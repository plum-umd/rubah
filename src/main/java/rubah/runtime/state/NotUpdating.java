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
import java.util.HashMap;

import rubah.RubahThread;

public class NotUpdating extends RubahState {

	public NotUpdating() {
		super(new UpdateState());
		this.state.setRunning(new HashMap<RubahThread, RubahThread>());
	}

	public NotUpdating(UpdateState state) {
		super(state);
	}

	@Override
	public RubahState start() {
		if (state.getUpdateTime() != 0) {
			long updateTime = System.currentTimeMillis() - state.getUpdateTime();
			System.out.println("Total update time: " + updateTime + "ms");
		}
		this.doClear();
		return null;
	}

	protected void doClear() {
		this.state.clear(false);
	}

	@Override
	public void doStart() {
		throw new Error("Should not be invoked");
	}
	
	@Override
	public void update(String updatePoint) {
		/* Empty */
	}

	@Override
	public RubahState installUpdate(Installer installer, Options updateOptions) {
		this.state.setInstaller(installer);
		this.state.setOptions(updateOptions);

		States.setStates(this.state);

		if (!updateOptions.isStopAndGo()) {

			// Install new version
			try {
				installer.installVersion();
			} catch (IOException e) {
				throw new Error(e);
			}
		}

		return this.state.getStates().moveToNextState();
	}

	@Override
	public boolean isUpdating() {
		return false;
	}

	@Override
	public boolean isUpdateRequested() {
		return false;
	}

	@Override
	public RubahState observeState(UpdateState.Observer observer) {
		this.state.setObserver(observer);
		return new ObservedNotUpdating(this.state);
	}
}
