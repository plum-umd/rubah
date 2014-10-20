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

import rubah.Rubah;
import rubah.runtime.Version;
import rubah.runtime.VersionManager;
import rubah.runtime.state.strategy.MigrationStrategy;

public class ReflectionMigratorFactory extends DefaultObjectMigratorFactory {
	private final ReflectionMigrator theMigrator = new ReflectionMigrator();
	private Version version = VersionManager.getInstance().getRunningVersion();

	public ReflectionMigratorFactory(MigrationStrategy strategy) {
		super(strategy);
	}

	@Override
	public boolean canMigrate(Class<?> preClass) {
		return Class.class.isAssignableFrom(preClass);
	}

	@Override
	public Migrator buildMigrator() {
		return this.theMigrator;
	}

	private class ReflectionMigrator extends DefaultObjectMigrator {
		@Override
		public Object doMigrate(Object obj) {

			Class<?> c = (Class<?>)obj;
			Class<?> ret = c;

			String realName = version.getOriginalName(c.getName());

			if (realName != null) {
				try {
					ret = Class.forName(
							version.getUpdatableName(realName),
							false,
							Rubah.getLoader());
				} catch (ClassNotFoundException e) {
					throw new Error(e);
				} catch (SecurityException e) {
					throw new Error(e);
				} catch (IllegalArgumentException e) {
					throw new Error(e);
				}
			}

			return ret;
		}

		@Override
		public void followReferences(Object post) {
			// No need to follow references for Class objects, nothing interesting there
		}
	}
}
