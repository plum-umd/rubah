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

public class DefaultObjectMigratorFactory extends MigratorSubFactory {
	private DefaultObjectMigrator theMigrator = new DefaultObjectMigrator();

	public DefaultObjectMigratorFactory(MigrationStrategy strategy) {
		super(strategy);
	}

	@Override
	public boolean canMigrate(Class<?> preClass) {
		return true;
	}

	@Override
	public Migrator buildMigrator() {
		return this.theMigrator;
	}

	/*default*/ class DefaultObjectMigrator extends Migrator {
		@Override
		protected Object doMigrate(Object pre) {
			return pre;
		}

		@Override
		public void followReferences(Object post) {
			Class<?> c = post.getClass();

//			if (!AddTraverseMethod.isAllowed(c.getName()))
//				return;

			for (long offset : UnsafeUtils.getInstance().getOffsets(c).getOffsets())
				strategy.migrate(post, offset, post, offset);
		}
	}
}
