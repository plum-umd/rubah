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

import rubah.RubahException;


public class ObservedNotUpdating extends NotUpdating {

	private Observer observer;

	public ObservedNotUpdating(UpdateState state, Observer observer) {
		super(state);
		this.observer = observer;
	}

	@Override
	public RubahState start() {
		return null;
	}

	@Override
	protected void doClear() {
		this.state.clear(false);
	}

	@Override
	public void doStart() {
		throw new Error("Should not be invoked");
	}

	@Override
	public void update(String updatePoint) {
		try {
			this.observer.update(updatePoint);
		} catch (RubahException e) {
			// Rubah is not observed after an update
			// To make it observable after the update, do not erase this line!
			// Instead, in class rubah.runtime.state.States, make the update return to a ObservedNotUpdating after the update
			// It is important that the threads restart with the flag observed set to false, so that they don't try to grab the write lock
			this.state.setObserved(false);
			throw e;
		}
	}

	public interface Observer {
		public void update(String updatePoint);
	}
}
