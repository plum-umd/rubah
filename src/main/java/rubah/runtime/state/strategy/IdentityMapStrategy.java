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

import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Set;

import rubah.runtime.state.MigratingProgramState;

public class IdentityMapStrategy implements MappingStrategy {

	private transient IdentityHashMap<Object, Object> map = new IdentityHashMap<Object, Object>();

	@Override
	public Object get(Object pre) {
		return this.map.get(pre);
	}

	@Override
	public Object put(Object pre, Object post) {
		this.map.put(pre, post);
		return post;
	}

	public MappingStrategy setState(MigratingProgramState state) {

		this.map = new IdentityHashMap<Object, Object>();

		return this;
	}

	@Override
	public int countMapped() {
		return this.map.size();
	}

	private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
		in.defaultReadObject();
		this.map = new IdentityHashMap<Object, Object>();
	}

	@Override
	public void setUpdatedClassNames(Set<String> updatedClasses) {
		// Empty
	}
}
