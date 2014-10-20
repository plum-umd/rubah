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

import java.util.Set;

public class ArrayStrategy implements MappingStrategy {
	public static final int SMALL_ARRAY_SIZE = 100;
	private MappingStrategy delegate;
	private Set<String> updatedClassNames;

	public ArrayStrategy(MappingStrategy delegate) {
		this.delegate = delegate;
	}

	@Override
	public Object get(Object pre) {
		Class<?> c = pre.getClass();

		if (!c.isArray())
			return this.delegate.get(pre);

		Class<?> comp = c.getComponentType();

		if (comp.isPrimitive())
			return pre;

		if (this.updatedClassNames.contains(comp.getName()))
			return this.delegate.get(pre);

		Object[] obj = (Object[]) pre;

		if (obj.length == 0) {
			return pre;
		}

		if (obj.length >= SMALL_ARRAY_SIZE)
			return this.delegate.get(pre);

//		Object first = obj[0];
//		if (first == null)
//			return this.delegate.get(pre);
//
//		Object ret = this.delegate.get(first);
//
//		return (ret == null ? null : pre);
		return null;
	}

	@Override
	public Object put(Object pre, Object post) {
		Class<?> c = pre.getClass();

		if (!c.isArray()) {
			return this.delegate.put(pre, post);
		}

		Class<?> comp = c.getComponentType();

		if (comp.isPrimitive())
			return post;

		if (this.updatedClassNames.contains(comp.getName())) {
			return this.delegate.put(pre, post);
		}

		Object[] obj = (Object[]) pre;

		if (obj.length >= SMALL_ARRAY_SIZE) {
			return this.delegate.put(pre, post);
		}

		// Do not register small arrays, they just get re-scanned next time they are found
		// That might add overhead, but saves memory
		// (Non-aliased small arrays are very, very common)
		return post;
	}

	@Override
	public int countMapped() {
		return this.delegate.countMapped();
	}

	@Override
	public void setUpdatedClassNames(Set<String> updatedClassNames) {
		this.updatedClassNames = updatedClassNames;
	}
}
