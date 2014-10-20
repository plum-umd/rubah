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

import rubah.bytecode.transformers.AddTraverseMethod;
import rubah.runtime.state.strategy.MigrationStrategy;

public class BlackListMigratorFactory extends MigratorSubFactory {
	private final BlackListMigrator theMigrator = new BlackListMigrator();

	public BlackListMigratorFactory(MigrationStrategy strategy) {
		super(strategy);
	}

	@Override
	public boolean canMigrate(Class<?> preClass) {
		if (preClass.isPrimitive())
			return true;
		if (preClass.isArray())
			return this.canMigrate(preClass.getComponentType());

		return !AddTraverseMethod.isAllowed(preClass.getName());
	}

	@Override
	public Migrator buildMigrator() {
		return this.theMigrator;
	}

	private class BlackListMigrator extends Migrator {
		@Override
		protected Object doMigrate(Object pre) {
			// Don't do anything
			return pre;
		}

		@Override
		public void followReferences(Object post) {
			// Don't do anything
		}

		@Override
		public Object registerMapping(Object pre, Object post) {
			// Don't register black-listed as visited
			// TODO: Probably we only need to do this for Rubah stuff
			return post;
		}
	}
}
