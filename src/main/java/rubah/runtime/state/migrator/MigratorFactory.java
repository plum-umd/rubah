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

import java.util.concurrent.ConcurrentHashMap;

import rubah.runtime.state.migrator.MigratorSubFactory.Migrator;


public class MigratorFactory {
	private MigratorSubFactory[] factories;

	public MigratorFactory(MigratorSubFactory ... factories) {
		this.factories = factories;
	}

	private ConcurrentHashMap<Class<?>, MigratorSubFactory> map = new ConcurrentHashMap<>();

	public Migrator getMigrator(Class<?> c) {
		MigratorSubFactory subFactory = map.get(c);

		if (subFactory == null)
			subFactory = this.getFactory(c);

		return subFactory.buildMigrator();
	}

	private MigratorSubFactory getFactory(Class<?> c) {
		for (MigratorSubFactory subFactory : this.factories) {
			if (subFactory.canMigrate(c)) {
				map.put(c, subFactory);
				return subFactory;
			}
		}

		throw new Error("Class " + c + " does not have any suitable migrator");
	}

	public long countMigrated() {
		long ret = 0;

		for (MigratorSubFactory factory : this.factories)
			ret += factory.countMigrated();

		return ret;
	}
}
