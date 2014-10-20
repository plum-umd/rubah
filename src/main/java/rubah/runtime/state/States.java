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

import java.util.Arrays;
import java.util.LinkedList;

public class States {
	private LinkedList<RubahState> states;

	private States(RubahState ... states) {
		this.states = new LinkedList<RubahState>(Arrays.asList(states));
	}

	public RubahState getNextState() {
		return states.getFirst();
	}

	public RubahState moveToNextState() {
		return this.states.pop();
	}

	public static void setStates(UpdateState state) {
		States states = null;

		if (state.getOptions().isStopAndGo()) {
			states = new States(
				new StoppingThreads(state),
				new MigratingControlFlow(state),
				new NotUpdating(state)
				);
		} else if(state.getOptions().isFullyLazy() || state.getOptions().isLazy()) {
			states = new States(
				new InstallingNewVersion(state),
				new ComputingUpdateMetadata(state),
				new StoppingThreads(state),
				new LazyMigratingProgramState(state),
				new LazyMigratingControlFlow(state),
				new LazyNotUpdating(state)
				);
		} else {
			states = new States(
				new InstallingNewVersion(state),
				new ComputingUpdateMetadata(state),
				new StoppingThreads(state),
				new MigratingProgramState(state),
				new MigratingControlFlow(state),
				new NotUpdating(state)
				);
		}

		state.setStates(states);
	}
}
