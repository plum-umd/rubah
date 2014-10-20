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
package rubah;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import rubah.io.InterruptedException;
import rubah.io.RubahIO;
import rubah.runtime.RubahRuntime;
import rubah.runtime.VersionManager;
import rubah.runtime.state.InstallingFirstVersion;
import rubah.runtime.state.RubahState;
import rubah.tools.BootstrapJarProcessor;
import rubah.tools.Updater;

public final class Rubah extends RubahRuntime {
	private Rubah() {
		throw new Error("This class is not supposed to be instantiated");
	}

	public static void update(String updatePoint) {
		RubahRuntime.update(updatePoint);
	}

	public static boolean isUpdateRequested() {
		return RubahRuntime.isUpdateRequested();
	}

	public static boolean isUpdating() {
		return RubahRuntime.isUpdating();
	}

	public static void main(String[] args) {

		try {
			BootstrapJarProcessor.readBootstrapMetaInfo(VersionManager.getDefaultNamespace());
		} catch (IOException e) {
			throw new Error(e);
		}

		String[] progArgs = new String[args.length - 3];
		System.arraycopy(args, 3, progArgs, 0, progArgs.length);

		RubahState state = new InstallingFirstVersion(args[2], new File(args[1]), new File(args[0]), progArgs);

		changeState(state);

		// Listen for future updates
		Updater.listen();

		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

		while (true) {
			String line;
			try {
				line = reader.readLine();
			} catch (IOException e) {
				continue;
			}

			if (line.equals("update")) {
//				Rubah.installNewVersion(new File("h2.1.2.122.rubah"), new File("UpdateClass_1_2_121_to_1_2_122.class"), new File("h2-1.2.122.jar"));
//				Rubah.installNewVersion(new File("test-v1.rubah"), new JarUpdateClass("UpdateClassTest"), new File("rubah-test-v1.jar"));
//				String basedir = "/Users/lggp/repos/git/doutoramento/code/rubah/Rubah/run/h2/";
//				Rubah.installNewVersion(
//						new Options()
//							.setUpdateDescriptor(new File(basedir + "h2.1.2.122.desc"))
//							.setUpdateClass(new JarUpdateClass("UpdateClass_1_2_121_to_1_2_122"))
//							.setJar(new File(basedir + "h2-1.2.122.jar"))
//							.setStopAndGo(false));
//				Rubah.installV0V0(new Options().setUpdateClass(new JarUpdateClass("jake2.UpdateClass")));
//				Rubah.installV0V0(new Options().setMigrationStrategy(new FullyLazyWithProxies(new ArrayStrategy(new ForwardFieldStrategy(new ConcurrentMapStrategy())))).setFullyLazy(true));
//				Rubah.installV0V0(new Options().setMigrationStrategy(new SingleThreaded(new ArrayStrategy(new ForwardFieldStrategy(new ConcurrentMapStrategy())))));
//				String basedir = "/Users/lggp/repos/git/doutoramento/code/rubah/Rubah/run/test/";
//				Rubah.installNewVersion(
//						new Options()
//							.setUpdateDescriptor(new File(basedir + "test-v1.desc"))
//							.setUpdateClass(new JarUpdateClass("UpdateClassTest"))
//							.setJar(new File(basedir + "rubah-test-v1.jar"))
//							.setStopAndGo(false));
			}
			if (line.equals("quit")) {
				return;
			}
		}
	}

	public static void registerBlockingIO() {
		RubahIO.registerBlockingIO();
	}

	public static void deregisterBlockingIO() {
		RubahIO.deregisterBlockingIO();
	}

	/**
	 *
	 * @param channel
	 * @return A socket channel for an accepted connection, or null if the operation was interrupted due to an update
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public static SocketChannel accept(Selector selector, ServerSocketChannel channel) throws IOException, InterruptedException {
		return RubahIO.accept(selector, channel);
	}

	public static SocketChannel accept(Selector selector, ServerSocketChannel channel, long timeout) throws IOException, InterruptedException {
		return RubahIO.accept(selector, channel, timeout);
	}

	/**
	 *
	 * @param channel
	 * @param buffer
	 * @return The number of bytes written to the socket from the buffer, or null if the operation was interrupted due to an update
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public static Integer write(Selector selector, SocketChannel channel, ByteBuffer buffer)
			throws IOException, InterruptedException {
		return RubahIO.write(selector, channel, buffer);
	}

	/**
	 *
	 * @param channel
	 * @param buffer
	 * @return The number of bytes read from the socket to the buffer, or null if the operation was interrupted due to an update
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public static Integer read(Selector selector, SocketChannel channel, ByteBuffer buffer)
			throws IOException, InterruptedException {
		return RubahIO.read(selector, channel, buffer);
	}

	public static void redirectThreadAfterUpdate(RubahThread t0, RubahThread t1) {
		RubahRuntime.redirectThreadAfterUpdate(t0, t1);
	}

	public static void touch(Object obj) {
		// Dummy method, useful for creating a breakpoint when debugging updatable code
	}
}
