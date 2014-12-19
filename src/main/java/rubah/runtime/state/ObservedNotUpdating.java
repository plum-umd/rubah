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



public class ObservedNotUpdating extends StoppingThreads {

	private boolean startedUpdate = false;

	public ObservedNotUpdating(UpdateState state) {
		super(state);
	}

	@Override
	public RubahState start() {
		synchronized (this) {
			while (!this.startedUpdate) {
				try {
					this.wait();
				} catch(InterruptedException e) {
					continue;
				}
			}
		}

		super.doStart();
		return null;
	}

	@Override
	public void update(String updatePoint) {
		synchronized (this) {
			if (this.startedUpdate)
				super.update(updatePoint);
			else if (this.state.getObserver().update(updatePoint)) {
				this.startedUpdate = true;
				this.notifyAll();
				super.update(updatePoint);
			}
		}
	}

	@Override
	public RubahState installUpdate(Installer installer, Options updateOptions) {
		this.state.setInstaller(installer);
		this.state.setOptions(updateOptions);

		States.setObservedStates(this.state);

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
}
