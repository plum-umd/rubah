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
package rubah.runtime.state;

import java.io.File;
import java.io.Serializable;

import rubah.runtime.state.strategy.IdentityMapStrategy;
import rubah.runtime.state.strategy.MigrationStrategy;
import rubah.runtime.state.strategy.SingleThreaded;
import rubah.update.UpdateClass;

public class Options implements Serializable {
	private File updateDescriptor;
	private UpdateClass updateClass;
	private File jar;
	private boolean stopAndGo = false;
	private boolean measureWorkingSet = false;
	private boolean traversal = false;
	private boolean lazy = false;
	private MigrationStrategy migrationStrategy = new SingleThreaded(new IdentityMapStrategy());
	private File updatePackage;

	public boolean isStopAndGo() {
		return stopAndGo;
	}

	public Options setStopAndGo(boolean stopAndGo) {
		this.stopAndGo = stopAndGo;
		return this;
	}

	public boolean isMeasureWorkingSet() {
		return measureWorkingSet;
	}

	public Options setMeasureWorkingSet(boolean measureWorkingSet) {
		this.measureWorkingSet = measureWorkingSet;
		return this;
	}

	public boolean isTraversal() {
		return traversal;
	}

	public Options setTraversal(boolean traversal) {
		this.traversal = traversal;
		return this;
	}

	public File getUpdateDescriptor() {
		return this.updateDescriptor;
	}

	public Options setUpdateDescriptor(File updateDescriptor) {
		this.updateDescriptor = updateDescriptor;
		return this;
	}

	public UpdateClass getUpdateClass() {
		return updateClass;
	}

	public Options setUpdateClass(UpdateClass updateClass) {
		this.updateClass = updateClass;
		return this;
	}

	public File getJar() {
		return jar;
	}

	public Options setJar(File jar) {
		this.jar= jar;
		return this;
	}

	public MigrationStrategy getMigrationStrategy() {
		return migrationStrategy;
	}

	public Options setMigrationStrategy(MigrationStrategy migrationStrategy) {
		this.migrationStrategy = migrationStrategy;
		return this;
	}

	public boolean isLazy() {
		return this.lazy;
	}

	public Options setLazy(boolean lazy) {
		this.lazy = lazy;
		return this;
	}

	public Options setUpdatePackage(File updatePackage) {
		this.updatePackage = updatePackage;
		return this;
	}

	public File getUpdatePackage() {
		return this.updatePackage;
	}
}
