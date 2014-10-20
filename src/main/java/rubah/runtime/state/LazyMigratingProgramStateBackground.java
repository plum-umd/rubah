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




public class LazyMigratingProgramStateBackground extends LazyMigratingProgramState {
	private boolean updating = true;

	public LazyMigratingProgramStateBackground(UpdateState state) {
		super(state);
	}

	@Override
	public void doStart() {
		if (updating) {
			super.doStart();
			updating = false;
			return;
		}
//		this.strategy = this.state.getOptions().getMigrationStrategy();
//		this.strategy.setState(this);
//		this.state.setLazyStrategy(this.strategy);

		long time = System.currentTimeMillis();
		System.out.println("Program stopped for " + (time - state.getUpdateTime()) + " ms");
		long objects = this.strategy.countMigrated();
		this.strategy.waitForFinish();
		this.stopPrintingThread();
		time = System.currentTimeMillis() - time;
		objects = this.strategy.countMigrated() - objects;
		System.out.println(
				"Converted " + objects + " objects in " + time + "ms");
	}
	
	@Override
	public void update(String updatePoint) {
		// Do nothing
	}

	@Override
	public boolean isUpdating() {
		return this.updating;
	}
}
