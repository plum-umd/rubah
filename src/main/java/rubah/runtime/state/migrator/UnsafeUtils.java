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

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import rubah.Rubah;
import rubah.bytecode.transformers.AddForwardField;
import rubah.bytecode.transformers.ProxyGenerator;
import rubah.org.apache.commons.collections.map.HashedMap;
import rubah.runtime.Version;
import sun.misc.Unsafe;

public class UnsafeUtils {
	private static final String UNSAFE_FIELD_NAME = "theUnsafe";
	private static final long KLASS_OFFSET = 8L;
	private static final long HASHCODE_OFFSET = 1L;
	private static final UnsafeUtils instance = new UnsafeUtils();

	private HashedMap classOffsetsMap = new HashedMap();

	private Map<Class<?>, ProxyOffsets> proxyOffsetsMap = new HashMap<>();
	private static final Unsafe unsafe;

	static {
		Class<?> unsafeClass = Unsafe.class;
		Field unsafeField;
		try {
			unsafeField = unsafeClass.getDeclaredField(UNSAFE_FIELD_NAME);
			boolean accessible = unsafeField.isAccessible();
			unsafeField.setAccessible(true);
			unsafe = (Unsafe) unsafeField.get(null);
			unsafeField.setAccessible(accessible);
		} catch (NoSuchFieldException e) {
			throw new Error(e);
		} catch (SecurityException e) {
			throw new Error(e);
		} catch (IllegalArgumentException e) {
			throw new Error(e);
		} catch (IllegalAccessException e) {
			throw new Error(e);
		}
	}


	public static UnsafeUtils getInstance() {
		return instance;
	}

	public static Unsafe getUnsafe() {
		return unsafe;
	}

	private UnsafeUtils() {
		// Empty
	}

	public int getHashCode(Object obj) {
		return unsafe.getInt(obj, HASHCODE_OFFSET);
	}

	public void setHashCode(Object obj, int hashCode) {
		unsafe.putInt(obj, HASHCODE_OFFSET, hashCode);
	}

	public void setHashCode(Object src, Object dest) {
		// HashCodes are lazily initalized by the JVM
		// This line ensures the object has an hashCode
		System.identityHashCode(dest);
		// Otherwise, the JVM overwrites whatever the following line writes
		unsafe.putInt(dest, HASHCODE_OFFSET, System.identityHashCode(src));
	}

	public void setOffsets(Collection<Class<?>> classes, Version v1) {

		for (Class<?> c : classes) {
			setOffsets(c);

			String originalName = v1.getOriginalName(c.getName());

			if (originalName == null)
				continue;

			String nextVersionName = v1.getUpdatableName(originalName);

			if (nextVersionName.equals(c.getName()))
				continue;

			try {
				setOffsets(Class.forName(nextVersionName, false, Rubah.getLoader()));
			} catch (ClassNotFoundException e) {
				throw new Error("Should never happen");
			}

		}
	}

	public synchronized ClassOffsets setOffsets(Class<?> c) {
		if (c.isInterface())
			return new ClassOffsets();

		ClassOffsets classOffsets = (ClassOffsets) classOffsetsMap.get(c);

		if (classOffsets != null)
			return classOffsets;
		else
			classOffsets = new ClassOffsets();

		Class<?> parent = c.getSuperclass();
		if (parent != null)
			classOffsets.offsets.addAll(setOffsets(parent).offsets);

		boolean foundStaticBase = false;

		for (Field f : c.getDeclaredFields()) {
			if (Modifier.isStatic(f.getModifiers()) && !foundStaticBase) {
				classOffsets.staticBase = unsafe.staticFieldBase(f);
				foundStaticBase = true;
			}

			if (f.getType().isPrimitive() || (f.getType().isArray() && f.getType().getComponentType().isPrimitive()))
				continue;

			if (f.getName().equals(AddForwardField.FIELD_NAME))
				continue;

			if (Modifier.isStatic(f.getModifiers())) {
				classOffsets.staticOffsets.add(unsafe.staticFieldOffset(f));
			} else {
				classOffsets.offsets.add(unsafe.objectFieldOffset(f));
			}
		}

		classOffsetsMap.put(c, classOffsets);

		return classOffsets;
	}

	public ClassOffsets getOffsets(Class<?> c) {
		ClassOffsets ret = (ClassOffsets) this.classOffsetsMap.get(c);

		if (ret == null)
			return setOffsets(c);

		return ret;
	}

	public static class ClassOffsets {
		private Object staticBase;
		private LinkedList<Long> offsets = new LinkedList<Long>();
		private LinkedList<Long> staticOffsets = new LinkedList<Long>();

		public Object getStaticBase() {
			return staticBase;
		}
		public LinkedList<Long> getOffsets() {
			return offsets;
		}
		public LinkedList<Long> getStaticOffsets() {
			return staticOffsets;
		}
	}

	private HashMap<Class<?>, Object> classToKlass = new HashMap<>();

	public void registerClassToKlass(Class<?> c) {
		if (classToKlass.containsKey(c))
			return;

		Object obj;
		try {
			obj = unsafe.allocateInstance(c);
		} catch (InstantiationException e) {
			throw new Error(e);
		}

		int klass = unsafe.getInt(obj, KLASS_OFFSET);
		this.classToKlass.put(c, klass);
	}

	public int getKlass(Object target) {
		return unsafe.getInt(target, KLASS_OFFSET);
	}

	public Object getClassToken (Class<?> c) {
		if (c.isArray())
			return Array.newInstance(c.getComponentType(), 0);
		else
			try {
				return unsafe.allocateInstance(c);
			} catch (InstantiationException e) {
				throw new Error(e);
			}
	}

	public void changeClass(Object target, Object fromClassToken, Object toClassToken) {
		int oldKlass = unsafe.getInt(fromClassToken, KLASS_OFFSET);
		int newKlass = unsafe.getInt(toClassToken, KLASS_OFFSET);
		unsafe.compareAndSwapInt(target, KLASS_OFFSET, oldKlass, newKlass);
//		unsafe.putInt(target, KLASS_OFFSET, newKlass);
	}

	public void changeClass(Object target, Object classToken) {
		int newKlass = unsafe.getInt(classToken, KLASS_OFFSET);
		unsafe.putInt(target, KLASS_OFFSET, newKlass);
	}

	public void changeClass(Object target, Class<?> newClass) {
		try {

			Object obj = this.classToKlass.get(newClass);

			if (obj == null) {

				if (newClass.isArray())
					obj = Array.newInstance(newClass.getComponentType(), 0);
				else
					obj = unsafe.allocateInstance(newClass);

				this.classToKlass.put(newClass, obj);
			}

			int newKlass = unsafe.getInt(obj, KLASS_OFFSET);
			unsafe.putInt(target, KLASS_OFFSET, newKlass);
		} catch (InstantiationException e) {
			throw new Error(e);
		}
	}

	public ProxyOffsets getProxyOffsets(Class<?> proxyClass) {

		ProxyOffsets ret = this.proxyOffsetsMap.get(proxyClass);

		if (ret == null) {
			try {
				Field baseField = proxyClass.getDeclaredField(ProxyGenerator.BASE_OBJ_NAME);
				Field scaleField = proxyClass.getDeclaredField(ProxyGenerator.SCALE_NAME);
				Field proxiedField = proxyClass.getDeclaredField(ProxyGenerator.PROXIED_OBJ_NAME);
				Field offsetField = proxyClass.getDeclaredField(ProxyGenerator.OFFSET_NAME);

				ret = new ProxyOffsets();

				ret.baseOffset = (short) unsafe.objectFieldOffset(baseField);
				ret.scaleOffset = (short) unsafe.objectFieldOffset(scaleField);
				ret.proxiedOffset = (short) unsafe.objectFieldOffset(proxiedField);
				ret.offsetOffset = (short) unsafe.objectFieldOffset(offsetField);

				this.proxyOffsetsMap.put(proxyClass, ret);
			} catch (NoSuchFieldException e) {
				throw new Error(e);
			} catch (SecurityException e) {
				throw new Error(e);
			}
		}

		return ret;

	}

	public static class ProxyOffsets {
		public short baseOffset;
		public short scaleOffset;
		public short proxiedOffset;
		public short offsetOffset;
	}

	public static void write(Object owner, long val, long offset) {
		unsafe.putLong(owner, offset, val);
	}

	public static void write(Object owner, double val, long offset) {
		unsafe.putDouble(owner, offset, val);
	}
}
