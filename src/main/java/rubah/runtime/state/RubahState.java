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

import java.io.IOException;

import rubah.RubahException;
import rubah.RubahThread;
import rubah.runtime.VersionManager;

public abstract class RubahState {
	protected UpdateState state;

	protected RubahState(UpdateState state) {
		this.state = state;
	}

	public RubahState start() {
		this.doStart();
		return this.state.getStates().moveToNextState();
	}

	protected abstract void doStart();

	public abstract boolean isUpdating();

	public abstract boolean isUpdateRequested();

	public void update(String updatePoint) {
		throw new Error("Input not expected");
	}

	public RubahState installUpdate(Installer installer, Options updateOptions) {
		throw new Error("Input not expected");
	}

	public void redirectThread(RubahThread t0, RubahThread t1) {
		throw new Error("Input not expected");
	}

	public Object getConverted(Object pre) {
		return this.state.getOptions().getMigrationStrategy().migrate(pre);
	}

	public void registerRunningThread(RubahThread t) {
//		System.out.println("Thread "+t+" is now running an instance of " + t.getTarget().getClass());
		this.state.getRunning().put(t, t);
//		System.out.println(this.running);
	}

	public void deregisterRunningThread(RubahThread t, RubahException e) {
//		System.out.println("Thread "+t+" has stopped running");
		this.state.getRunning().remove(t);
//		System.out.println(this.running);
	}

	public byte[] getClassBytes(String className) throws IOException {
		return VersionManager.getInstance().getClassBytes(className);
	}

	public void ensureStaticFieldsMigrated(Class<?> c) {
		throw new Error("Input not expected");
	}

	public RubahState observeState(Installer installer, Options updateOptions, UpdateState.Observer observer) {
		throw new Error("Input not expected");
	}
}
