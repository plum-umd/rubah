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
import java.util.Set;

import org.cliffc.high_scale_lib.Counter;

import rubah.bytecode.transformers.AddForwardField;
import rubah.runtime.state.migrator.UnsafeUtils;

public class ForwardFieldStrategy implements MappingStrategy {
//	private transient Object visitedMarker = new Object();

//	private transient Map<Object, Object> forwardOffsets = new ConcurrentHashMap<Object, Object>();
	private static final long INFO_OFFSET;

	static {
		long val;
		try {
			val = UnsafeUtils.getUnsafe().objectFieldOffset(Class.class.getDeclaredField(AddForwardField.CLASS_INFO_FIELD_NAME));
		} catch (NoSuchFieldException | SecurityException e) {
			val = 0;
			// Ignore, this class is loaded in a different context probably
		}
		INFO_OFFSET = val;
	}
	private transient Counter cnt = new Counter();

	private MappingStrategy delegate;

	public ForwardFieldStrategy(MappingStrategy delegate) {
		this.delegate = delegate;
	}

	private Long setForwardOffset(Class<?> c) {
		long offset;
		try {
			if (c.isArray())
				offset = -1;
			else
				offset = MigrationStrategy.unsafe.objectFieldOffset(c.getField(AddForwardField.FIELD_NAME));
		} catch (SecurityException e) {
			throw new Error(e);
		} catch (NoSuchFieldException e) {
			offset = -1;
		}
//		this.forwardOffsets.put(c, offset);
		MigrationStrategy.unsafe.putObject(c, INFO_OFFSET, new Long(offset));

		return offset;
	}

	@Override
	public Object get(Object pre) {
		Class<?> c = pre.getClass();

		Long offset = (Long) MigrationStrategy.unsafe.getObject(c, INFO_OFFSET);

		if (offset == null) {
			offset = setForwardOffset(c);
		}

		if (offset < 0)
			return this.delegate.get(pre);

		Object ret = MigrationStrategy.unsafe.getObject(pre, offset);

//		return (ret == visitedMarker ? pre : ret);
		return ret;
	}

	@Override
	public Object put(Object pre, Object post) {
		Class<?> c = pre.getClass();

		Long offset = (Long) MigrationStrategy.unsafe.getObject(c, INFO_OFFSET);

		if (offset == null)
			offset = setForwardOffset(c);

		if (offset < 0) {
			return this.delegate.put(pre, post);
		}

//		if (pre == post)
//			post = visitedMarker;

		boolean success = MigrationStrategy.unsafe.compareAndSwapObject(pre, offset, null, post);

		if (success) {
//			this.cnt.increment();
			return post;
		} else {
			return MigrationStrategy.unsafe.getObject(pre, offset);
		}
	}

	@Override
	public int countMapped() {
		return this.cnt.intValue() + this.delegate.countMapped();
	}

	private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
		in.defaultReadObject();
//		this.forwardOffsets = new ConcurrentHashMap<>();
		this.cnt = new Counter();
//		this.visitedMarker = new Object();
	}

	@Override
	public void setUpdatedClassNames(Set<String> updatedClasses) {
		// Empty
	}
}
