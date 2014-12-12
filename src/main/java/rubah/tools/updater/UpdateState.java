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

import java.io.File;

import rubah.runtime.state.strategy.MigrationStrategy;

public class UpdateState {

	private String[] commandLine;

	private int port;
	private boolean v0v0;
	private File descriptor;
	private File updateClassFile;
	private String updateClassName;
	private File newJar;

	private boolean stopAndGo;

	private boolean lazy;

	private MigrationStrategy migrationStrategy;
	private File updatePackage;

	public int getPort() {
		return this.port;
	}
	public void setPort(int port) {
		this.port = port;
	}
	public File getDescriptor() {
		return this.descriptor;
	}
	public void setDescriptor(File descriptor) {
		this.descriptor = descriptor;
	}
	public String[] getCommandLine() {
		return this.commandLine;
	}
	public void setCommandLine(String[] commandLine) {
		this.commandLine = commandLine;
	}
	public File getUpdateClassFile() {
		return this.updateClassFile;
	}
	public void setUpdateClassFile(File updateClassFile) {
		this.updateClassFile = updateClassFile;
	}
	public String getUpdateClassName() {
		return this.updateClassName;
	}
	public void setUpdateClassName(String updateClassName) {
		this.updateClassName = updateClassName;
	}
	public File getNewJar() {
		return this.newJar;
	}
	public void setNewJar(File newJar) {
		this.newJar = newJar;
	}
	public void setV0V0(boolean v0v0) {
		this.v0v0 = v0v0;
	}
	public boolean isV0V0() {
		return this.v0v0;
	}
	public boolean isStopAndGo() {
		return stopAndGo;
	}
	public void setStopAndGo(boolean stopAndGo) {
		this.stopAndGo = stopAndGo;
	}
	public MigrationStrategy getMigrationStrategy() {
		return migrationStrategy;
	}
	public void setMigrationStrategy(MigrationStrategy migrationStrategy) {
		this.migrationStrategy = migrationStrategy;
	}
	public void setLazy(boolean lazy) {
		this.lazy = lazy;
	}
	public boolean isLazy() {
		return lazy;
	}
	public void setUpdatePackage(File updatePackage) {
		this.updatePackage = updatePackage;
	}
	public File getUpdatePackage() {
		return this.updatePackage;
	}
}
