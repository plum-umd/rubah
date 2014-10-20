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
package rubah.runtime.state.migrator;

import rubah.runtime.state.strategy.MigrationStrategy;


public abstract class MigratorSubFactory {
	protected MigrationStrategy strategy;

	public MigratorSubFactory(MigrationStrategy strategy) {
		this.strategy = strategy;
	}

	public abstract boolean canMigrate(Class<?> c);

	public abstract Migrator buildMigrator();

	public long countMigrated() {
		return 0L;
	}

	public abstract class Migrator {
		public Object migrate(Object obj) {
			Object ret = this.doMigrate(obj);

			return ret;
		}

		protected abstract Object doMigrate(Object pre);

		public abstract void followReferences(Object post);

		public Object registerMapping(Object pre, Object post) {
			return strategy.getMapping().put(pre, post);
		}

		protected void follow(Object ref) {
			strategy.migrate(ref);
		}
	}
}
