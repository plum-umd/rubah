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
package rubah.runtime.state.strategy;

import java.io.Serializable;
import java.util.Collection;

import rubah.runtime.state.MigratingProgramState;
import sun.misc.Unsafe;

public interface MigrationStrategy extends Serializable {
	/*default*/ static final Unsafe unsafe =
			(MigrationStrategy.class.getClassLoader() == null ? Unsafe.getUnsafe() : null);

	public MigrationStrategy setState(MigratingProgramState state);

	public void migrate(Object fromBase, long fromOffset, Object toBase, long toOffset);

	public Object migrate(Object obj);

	public void migrateStaticFields(Collection<Class<?>> classes);

	public MappingStrategy getMapping();

	public void waitForFinish();

	public String getDescription();

	public long countMigrated();
}