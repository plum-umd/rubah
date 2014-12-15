/*******************************************************************************
 *  	Copyright 2014,
 *  		Luis Pina <luis@luispina.me>,
 *  		Michael Hicks <mwh@cs.umd.edu>
 *
 *  	This file is part of Rubah.
 *
 *     Rubah is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Rubah is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Rubah.  If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package rubah.tools;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import org.apache.commons.io.output.NullOutputStream;

import rubah.Rubah;
import rubah.runtime.RubahRuntime;
import rubah.runtime.VersionManager;
import rubah.runtime.state.Installer;
import rubah.runtime.state.ObservedNotUpdating.Observer;
import rubah.runtime.state.ObservedStoppingThreads;
import rubah.runtime.state.Options;
import rubah.tools.updater.ParsingArguments;
import rubah.tools.updater.UpdateState;
import rubah.update.FileUpdateClass;
import rubah.update.JarUpdateClass;
import rubah.update.UpdateClass;


public class Updater {
	private static final String LISTENER_THREAD_NAME = "rubahUpdateThread";
	private static final String DISABLE_OUT = "quietOut";
	public static final PrintStream out;
	private static final String PORT_PROP = "updatePort";
	public static final int port;

	static {
		if (System.getProperty(DISABLE_OUT) != null) {
			out = new PrintStream(new NullOutputStream());
		} else {
			out = System.out;
		}
		port = Integer.parseInt(System.getProperty(PORT_PROP, "55416"));
	}

	public static void main(String[] args) {
		UpdateState state = new UpdateState();
		state.setCommandLine(args);

		ParsingArguments parser = new ParsingArguments();
		parser.execute(state);

		UpdateClass updateClass;

		if (state.getUpdateClassName() != null)
			updateClass = new JarUpdateClass(state.getUpdateClassName());
		else if (state.getUpdateClassFile() != null)
			updateClass = new FileUpdateClass(state.getUpdateClassFile());
		else
			updateClass = null;

		Options options;
		Updater.Type type = null;

		if (state.isV0V0()) {
			type = Type.v0v0;
			options = new Options()
			.setUpdatePackage(state.getUpdatePackage())
			.setUpdateClass(updateClass)
			.setStopAndGo(state.isStopAndGo())
			.setMigrationStrategy(state.getMigrationStrategy())
			.setLazy(state.isLazy());
		} else {
			type = Type.v0v1;
			options = new Options()
			.setUpdatePackage(state.getUpdatePackage())
			.setUpdateDescriptor(state.getDescriptor())
			.setJar(state.getNewJar())
			.setUpdateClass(updateClass)
			.setStopAndGo(state.isStopAndGo())
			.setMigrationStrategy(state.getMigrationStrategy())
			.setLazy(state.isLazy());
		}

		try (Socket clientSocket = new Socket(InetAddress.getLocalHost(), Updater.port)) {
	        // Create the input & output streams to the server
	        ObjectOutputStream outToServer = new ObjectOutputStream(clientSocket.getOutputStream());
	        outToServer.writeObject(type);
	        outToServer.writeObject(options);

	        clientSocket.close();

	    } catch (Exception e) {
	        System.err.println("Client Error: " + e.getMessage());
	        System.err.println("Localized: " + e.getLocalizedMessage());
	        System.err.println("Stack Trace: " + e.getStackTrace());
	    }
	}

	public enum Type { v0v0, v0v1 }


	private static class ListenerThread extends Thread {

		public ListenerThread(String name) {
			super(name);
			this.setDaemon(true);
		}

		@Override
		public void run() {
			try (ServerSocket welcomeSocket = new ServerSocket(port)) {
				while (true) {
					// Create the Client Socket
					try (Socket clientSocket = welcomeSocket.accept()) {
						out.println("Updater connected...");
						ObjectInputStream inFromClient = new ObjectInputStream(clientSocket.getInputStream());

						Type type = (Type) inFromClient.readObject();
						Options options = (Options) inFromClient.readObject();
						RemoteObserver observer = new RemoteObserver(type, options);

						try {
							RubahRuntime.observeState(observer);
							synchronized (observer) {
								while (!observer.updated) {
									try {
										observer.wait();
										// The application thread has installed the update
										// The updater thread can now finish the update
										((ObservedStoppingThreads)RubahRuntime.getState()).restart();
									} catch (InterruptedException e) {
										continue;
									}
								}
							}
						} catch (Throwable e) {
							System.out.println(e);
							e.printStackTrace();
							continue;
						}
					} catch (ClassNotFoundException e) {
						System.out.println(e);
						e.printStackTrace();
						continue;
					}
				}
			} catch (IOException e) {
				throw new Error(e);
			}
		}

		private static class RemoteObserver implements Observer {
			private final Type type;
			private final Options options;
			private boolean updated = false;

			private Installer installer = new Installer() {
				@Override
				public void installVersion() throws IOException {
					switch (type) {
					case v0v0:
						VersionManager.getInstance().installV0V0(options);
						break;
					case v0v1:
						VersionManager.getInstance().installVersion(options);
						break;
					default:
						throw new Error("Unknown update type");
					}
				}
			};

			public RemoteObserver(Type type, Options options) {
				this.type = type;
				this.options = options;
			}

			@Override
			public void update(String updatePoint) {
				synchronized (this) {
					if (!this.updated) {
						// Use this application thread to install the update
						// This is unlike the normal case, where the updater thread installs the update
						// But this behavior ensures that the update is installed at this exact update point
						// Note that this thread is holding the write lock on RubahRuntime at this point,
						// effectively locking out all other threads that are trying to reach an update point
						this.updated = true;
						Rubah.installNewVersion(this.options, this.installer);
						try {
							// Reach the update point after installing the update
							// This throws an exception that gets propagated to application code
							RubahRuntime.update(updatePoint);
						} finally {
							// The update exception gets "caught" here before being propagated up
							// The updater thread will now wake and finish the update
							this.notifyAll();
						}
					}
				}
			}
		}
	}

	public static void listen() {
		new ListenerThread(LISTENER_THREAD_NAME).start();
	}
}
