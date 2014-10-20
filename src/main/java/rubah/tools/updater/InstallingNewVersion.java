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
package rubah.tools.updater;

import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;

import rubah.runtime.state.Options;
import rubah.tools.Updater;
import rubah.tools.Updater.Type;
import rubah.update.FileUpdateClass;
import rubah.update.JarUpdateClass;
import rubah.update.UpdateClass;

public class InstallingNewVersion extends Filter {

	@Override
	public void execute(final UpdateState state) {

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
			.setFullyLazy(state.isFullyLazy())
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
			.setFullyLazy(state.isFullyLazy())
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

}
