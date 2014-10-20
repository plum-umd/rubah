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


public class FullyLazy extends SingleThreaded {

	public FullyLazy(MappingStrategy mapping) {
		super(mapping);
	}

	@Override
	protected void migrateStatic(Object base, long offset) {
		super.migrate(base, offset, base, offset);
	}

	@Override
	public void migrate(Object fromBase, long fromOffset, Object toBase, long toOffset) {
		Object fromObj = unsafe.getObject(fromBase, fromOffset);
		Object toObj = unsafe.getObject(toBase, toOffset);

		if (fromObj == null)
			return;

		Object ret = this.mapping.get(fromObj);

		if (ret == null)
			return;

		if (ret != fromObj)
			unsafe.compareAndSwapObject(toBase, toOffset, toObj, ret);
	}
}
