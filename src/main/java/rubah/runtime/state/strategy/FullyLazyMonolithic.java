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

import java.io.FileDescriptor;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.cliffc.high_scale_lib.Counter;

import rubah.Rubah;
import rubah.bytecode.RubahProxy;
import rubah.bytecode.transformers.AddForwardField;
import rubah.bytecode.transformers.AddTraverseMethod;
import rubah.bytecode.transformers.ProxyGenerator;
import rubah.framework.Clazz;
import rubah.framework.Type;
import rubah.runtime.Version;
import rubah.runtime.VersionManager;
import rubah.runtime.state.MigratingProgramState;
import rubah.runtime.state.migrator.MigratorSubFactory;
import rubah.runtime.state.migrator.MigratorSubFactory.Migrator;
import rubah.runtime.state.migrator.StaticFieldsMigratorFactory;
import rubah.runtime.state.migrator.UnsafeUtils;

@SuppressWarnings("restriction")
public class FullyLazyMonolithic implements MigrationStrategy {
	private static final long INFO_OFFSET;
	public static final int SMALL_ARRAY_SIZE = 1025;
	private transient Counter counter;
	public static final HashSet<String> BLACK_LIST = new HashSet<String>(Arrays.asList(new String[]{
			FileDescriptor.class.getName()
	}));
	private transient Version v1;
	private transient rubah.runtime.state.ConcurrentHashMap<Object, Object> map;
	private Set<String> transformedClasses, outdatedClasses;

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

	@Override
	public void waitForFinish() {
		return;
	}

	@Override
	public String getDescription() {
		return "Fully lazy ugly";
	}

	@Override
	public long countMigrated() {
		return this.counter.estimate_get();
	}

	@Override
	public MappingStrategy getMapping() {
		throw new Error("Should not be invoked");
	}

	@Override
	public MigrationStrategy setState(MigratingProgramState state) {
		this.counter = new Counter();
		this.transformedClasses = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
		this.outdatedClasses = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

		Version v1 = VersionManager.getInstance().getLatestVersion();
		Version v0 = v1.getPrevious();

		if (v0 != null) {
			for (Clazz c0 : v0.getNamespace().getDefinedClasses()) {
				this.outdatedClasses.add(v0.getUpdatableName(c0.getFqn()));
				if (v1.getUpdate().isConverted(c0)) {
					this.transformedClasses.add(v0.getUpdatableName(c0.getFqn()));
				}
			}
		}

		this.v1 = v1;
		this.map = new rubah.runtime.state.ConcurrentHashMap<>();

		return this;
	}

	@Override
	public void migrateStaticFields(Collection<Class<?>> classes) {
		Version v1 = VersionManager.getInstance().getLatestVersion();
		MigratorSubFactory staticMigratorFactory = new StaticFieldsMigratorFactory(this, v1);

		for (Class<?> c : classes) {
			if (!staticMigratorFactory.canMigrate(c))
				continue;

			Migrator migrator = staticMigratorFactory.buildMigrator();
			Object newC = migrator.migrate(c);
			migrator.followReferences(newC);
		}
	}

	@Override
	public void migrate(Object fromBase, long fromOffset, Object toBase, long toOffset) {
		// Only invoked from the static migration, to traverse the static fields
		this.traverse(fromBase, fromOffset, toBase, toOffset);
	}

	private void traverse(Object fromBase, long fromOffset, Object toBase, long toOffset) {
		Object fromObj = unsafe.getObject(fromBase, fromOffset);
		Object toObj = unsafe.getObject(toBase, toOffset);

		if (fromObj == null)
			return;

		Class<?> c = fromObj.getClass();

		ClassConversionInfo info = this.getConversionInfo(c);

		// Has this object been converted already?
		Object ret = this.getMappedObject(fromObj, info);

		if (ret != null) {
			unsafe.compareAndSwapObject(toBase, toOffset, toObj, ret);
			return;
		}

		// Updated classes were already visited, no need to visit them again or install proxies
		switch (info.traverseAction) {
		case INSTALL_FRONTIER:
			UnsafeUtils.getInstance().changeClass(fromObj, info.proxyClassToken);
			break;
		case MIGRATE:
			// Migrate directly
			ret = this.migrate(fromObj);
			unsafe.compareAndSwapObject(toBase, toOffset, toObj, ret);
			break;
		case INSTALL_PROXY: {
			Object proxy;

			try {
				proxy = unsafe.allocateInstance(info.postClass);
//				ClassConversionInfo objectInfo = this.getConversionInfo(pre.getClass());
				UnsafeUtils.getInstance().setHashCode(fromObj, proxy);
				info.conversionMethodHolder.convert(fromObj, proxy);
				this.counter.increment();
//				for (long offset : this.getConversionInfo(info.proxyClass).fieldOffsets)
//					this.traverse(ret, offset, ret, offset);
				UnsafeUtils.getInstance().changeClass(proxy, info.proxyClassToken);
			} catch (ReflectiveOperationException | IllegalArgumentException e) {
				throw new Error(e);
			}

//			ClassConversionInfo proxyInfo = this.getConversionInfo(info.proxyClass);
//			this.mapObject(proxy, fromObj, proxyInfo);

			// Map outdated to proxy
			// This is where the algorithm synchronizes
			// If we lose the race, there will be another proxy for this object
			// Therefore the assignment, so that we continue with the correct proxy
			proxy = this.mapObject(fromObj, proxy, info);

//			unsafe.putObject(proxy, proxyInfo.forwardFieldOffset, fromObj);
//			ret = this.mapObject(proxy, fromObj, proxyInfo);

//			unsafe.putObject(proxy, proxyInfo.proxyBaseOffset, toBase);
//			unsafe.putObject(proxy, proxyInfo.proxyProxiedOffset, fromObj);
//			unsafe.putInt(proxy, proxyInfo.proxyOffsetOffset, (int) toOffset);
//			unsafe.putInt(proxy, proxyInfo.proxyScaleOffset, -1);

			// Install proxy with CAS because program might write a new value to this field
			unsafe.compareAndSwapObject(toBase, toOffset, toObj, proxy);
		}
		break;
		case NONE:
			break;
		default:
			throw new Error("Code should not reach here");
		}
	}

	private Object getMappedObject(Object obj, ClassConversionInfo info) {

		Object ret;

		switch (info.mappingAction) {
		case NONE:
			return null;
		case SAME:
			ret = obj;
			break;
		case FORWARD:
			ret = unsafe.getObject(obj, info.forwardFieldOffset);
			break;
		case MAP:
			ret = this.map.get(obj);
			break;
		case ARRAY:
			if (Array.getLength(obj) > SMALL_ARRAY_SIZE) {
				ret = this.map.get(obj);
				break;
			}

			return null;
		default:
			throw new Error("Should not reach here");
		}

		if (ret != null && info.migrateAction == MigrateAction.REMOVE_PROXY) {
			UnsafeUtils.getInstance().changeClass(obj, info.proxyClassToken);
//		} else if (ret != null && info.migrateAction == MigrateAction.REMOVE_PROXY) {
//			if (ret == obj)
//				UnsafeUtils.getInstance().changeClass(obj, info.proxyClassToken);
//			else
//				throw new Error("Should never happen");
		}

		return ret;
	}

	private Object mapObject(Object pre, Object post, ClassConversionInfo info) {
		Object ret = post;

		switch (info.mappingAction) {
			case NONE:
			case SAME:
				return ret;
			case FORWARD:
				boolean success = unsafe.compareAndSwapObject(pre, info.forwardFieldOffset, null, post);
				if (!success) {
					// Someone did the same concurrently
					// Discard the object created and return the other one
					ret = unsafe.getObject(pre, info.forwardFieldOffset);
				}
				return ret;
			case MAP:
				ret = this.map.putIfAbsent(pre, post);
				return (ret == null) ? post : ret;
			case ARRAY:
				if (Array.getLength(post) > SMALL_ARRAY_SIZE) {
					ret = this.map.putIfAbsent(pre, post);
					if (ret == null) {
						// Map large arrays so that they don't get re-migrated
						this.map.put(post, post);
						ret = post;
					}
				}

				return ret;
			default:
				throw new Error("Should not reach here");
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public Object migrate(Object obj) {
		if (obj == null) {
			return null;
		}

		Class<?> c = obj.getClass();
		ClassConversionInfo info = this.getConversionInfo(c);

		// Has this object been converted already?
		Object ret = this.getMappedObject(obj, info);
		if (ret != null) {
			return ret;
		}

		switch (info.migrateAction) {
		case MIGRATE_OUTDATED_ARRAY:
			UnsafeUtils.getInstance().changeClass(obj, info.postClassToken);
		case MIGRATE_ARRAY:
		case MIGRATE_NEW_ARRAY: {
			int length = Array.getLength(obj);

//			boolean isLarge = (length > SMALL_ARRAY_SIZE);
//			boolean isLarge = false;

//			if (!isLarge) {
				if (info.migrateAction == MigrateAction.MIGRATE_ARRAY || info.migrateAction == MigrateAction.MIGRATE_OUTDATED_ARRAY) {
					// Install frontier objects in-place
					ret = obj;
				} else {
					// Create new array and install proxies
					ret = Array.newInstance(info.postClass.getComponentType(), length);
				}

				long index = info.baseArrayIndex;
				for (int i = 0; i < length; i++, index += info.scaleArray)
					this.traverse(obj, index, ret, index);
//			} else {
//				ClassConversionInfo postInfo;
//				ClassConversionInfo elementInfo;
//				if (info.migrateAction == MigrateAction.MIGRATE_NEW_ARRAY) {
//					elementInfo = this.getConversionInfo(info.postClass.getComponentType());
//					ret = Array.newInstance(info.postClass.getComponentType(), length);
//					postInfo = this.getConversionInfo(info.postClass);
//				} else {
//					elementInfo = this.getConversionInfo(c.getComponentType());
//					ret = Array.newInstance(c.getComponentType(), length);
//					postInfo = info;
//				}
//
//				ClassConversionInfo proxyInfo = this.getConversionInfo(elementInfo.proxyClass);
//				RubahProxy theProxy;
//				try {
//					theProxy = (RubahProxy) unsafe.allocateInstance(elementInfo.proxyClass);
//
//					unsafe.putObject(theProxy, proxyInfo.proxyBaseOffset, ret);
//					unsafe.putObject(theProxy, proxyInfo.proxyProxiedOffset, obj);
//					unsafe.putInt(theProxy, proxyInfo.proxyOffsetOffset, (int) postInfo.baseArrayIndex);
//					unsafe.putInt(theProxy, proxyInfo.proxyScaleOffset, (int) postInfo.scaleArray);
//				} catch (InstantiationException e) {
//					throw new Error(e);
//				}
//
//				long index = postInfo.baseArrayIndex;
//				for (int i = 0; i < length; i++, index += postInfo.scaleArray)
//					unsafe.putObject(ret, index, theProxy);
//			}

		}
		break;
		case MIGRATE_CLASS: {
			Class<?> cc = (Class<?>) obj;
			ret = cc;

			String realName = v1.getOriginalName(cc.getName());

			if (realName != null) {
				try {
					ret = Class.forName(
							v1.getUpdatableName(realName),
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
		}
		break;
		case MIGRATE_OBJECT: {
			Object post;
			try {
				post = sun.misc.Unsafe.getUnsafe().allocateInstance(info.postClass);
				ret = post;
				UnsafeUtils.getInstance().setHashCode(obj, ret);
				info.conversionMethodHolder.convert(obj, post);
				this.counter.increment();
				for (long offset : this.getConversionInfo(ret.getClass()).fieldOffsets)
					this.traverse(ret, offset, ret, offset);
			} catch (IllegalArgumentException | InstantiationException e) {
				throw new Error(e);
			}
			break;
		}
		case MIGRATE_REFERENCE: {
			ret = obj;
			if (obj.getClass().equals(SoftReference.class)) {
				SoftReference<?> ref = (SoftReference<?>) obj;
				ret = new SoftReference(this.migrate(ref.get()));
			} else if (obj.getClass().equals(WeakReference.class)) {
				WeakReference<?> ref = (WeakReference<?>) obj;

				ret = new WeakReference(this.migrate(ref.get()));
			} else if (obj.getClass().equals(AtomicReference.class)) {
				AtomicReference ref = (AtomicReference) obj;

				ret = new AtomicReference(this.migrate(ref.get()));
			}
			else {
				// TODO find a more general way to migrated references
				// Taking a chance here...
				// Follow reference here
				this.migrate(((Reference<?>) obj).get());
			}

			// Register in the mapping here
			// This is where the algorithm synchronizes
			// If we lose the race, there will be another reference for this object
			// Therefore the assignment, so that we continue with the correct reference
			ret = this.mapObject(obj, ret, info);
			// Resulting reference should not be further migrated
			this.mapObject(ret, ret, info);

		}
		break;
		case REMOVE_PROXY: {
			try {
				ret = obj;
//				Object pre = unsafe.getObject(obj, info.forwardFieldOffset);
//				if (pre == null) {
//					throw new Error("Should never happen");
//				} else if (pre == obj) {
//					// Already converted
//				} else {
//					ClassConversionInfo objectInfo = this.getConversionInfo(pre.getClass());
//					objectInfo.conversionMethod.invoke(null, pre, obj);
//					this.counter.increment();

				ClassConversionInfo proxiedInfo = this.getConversionInfo(info.proxyClass);
				for (long offset : proxiedInfo.fieldOffsets)
					this.traverse(ret, offset, ret, offset);
				UnsafeUtils.getInstance().changeClass(ret, info.proxyClassToken);
//				}
			} catch (IllegalArgumentException e) {
				throw new Error(e);
			}
			break;

//			Object proxied = unsafe.getObject(obj, info.proxyProxiedOffset);
//			Object base = unsafe.getObject(obj, info.proxyBaseOffset);
//			long offset = unsafe.getInt(obj, info.proxyOffsetOffset);
//
//			ret = this.migrate(proxied);
//
//			unsafe.compareAndSwapObject(base, offset, obj, ret);
		}
//		break;
		case SKIP:
			break;
		case MIGRATE_OUTDATED:
			UnsafeUtils.getInstance().changeClass(obj, info.postClassToken);
		case NONE:
			ret = obj;
			for (long offset : info.fieldOffsets)
				this.traverse(ret, offset, ret, offset);
			break;
		default:
			throw new Error("Should not reach here");
		}


		ret = this.mapObject(obj, ret, info);

		return ret;
	}

	private ClassConversionInfo getConversionInfo(Class<?> c) {
		ClassConversionInfo ret = (ClassConversionInfo) UnsafeUtils.getUnsafe().getObject(c, INFO_OFFSET);

		if (ret == null) {
			ret = this.buildConversionInfo(c);
			UnsafeUtils.getUnsafe().putObject(c, INFO_OFFSET, ret);
		}

		return ret;
	}

	private ClassConversionInfo buildConversionInfo(Class<?> c) {
		ClassConversionInfo ret;

		ret = new ClassConversionInfo();

		Class<?> comp = c;
		while (comp.isArray())
			comp = comp.getComponentType();

		try {
			if (RubahProxy.class.isAssignableFrom(c)) {
				ret.migrateAction = MigrateAction.REMOVE_PROXY;
				ret.traverseAction = TraverseAction.NONE;
//				ret.forwardFieldOffset = -1;
				ret.mappingAction = MappingAction.MAP;
				try {
					Field forwardField = c.getField(AddForwardField.FIELD_NAME);
					ret.forwardFieldOffset = unsafe.objectFieldOffset(forwardField);
					ret.mappingAction = MappingAction.FORWARD;
				} catch (NoSuchFieldException e) {
					// No $forward field
				}
				ret.proxyClass = Class.forName(ProxyGenerator.getOriginalName(c.getName()), false, Rubah.getLoader());
				ret.proxyClassToken = UnsafeUtils.getInstance().getClassToken(ret.proxyClass);
			} else if (c.isPrimitive()) {
				ret.migrateAction = MigrateAction.SKIP;
				ret.traverseAction = TraverseAction.NONE;
				ret.forwardFieldOffset = -1;
				ret.mappingAction = MappingAction.NONE;
			} else if (c.isArray() && comp.isPrimitive()) {
				ret.migrateAction = MigrateAction.SKIP;
				ret.traverseAction = TraverseAction.NONE;
				ret.forwardFieldOffset = -2;
				ret.mappingAction = MappingAction.SAME;
			} else if (!AddTraverseMethod.isAllowed(c.getName())) {
				ret.migrateAction = MigrateAction.SKIP;
				ret.traverseAction = TraverseAction.NONE;
				ret.forwardFieldOffset = -1;
				ret.mappingAction = MappingAction.NONE;
			} else if (c.equals(Object.class)) {
				ret.migrateAction = MigrateAction.NONE;
				ret.mappingAction = MappingAction.SAME;
				ret.traverseAction = TraverseAction.NONE;
			} else if (Class.class.isAssignableFrom(c)) {
				ret.migrateAction = MigrateAction.MIGRATE_CLASS;
				ret.mappingAction = MappingAction.MAP;
				ret.traverseAction = TraverseAction.MIGRATE;
			} else if (Reference.class.isAssignableFrom(c) || AtomicReference.class.isAssignableFrom(c)) {
				ret.migrateAction = MigrateAction.MIGRATE_REFERENCE;
				ret.traverseAction = TraverseAction.MIGRATE;
				ret.proxyClass = Class.forName(ProxyGenerator.generateProxyName(c.getName()), false, Rubah.getLoader());
				ret.forwardFieldOffset = -1;
				ret.mappingAction = MappingAction.MAP;
			} else if (this.transformedClasses.contains((c.isArray() ? c.getComponentType().getName() : c.getName()))) {
				if (c.isArray()) {
					ret.migrateAction = MigrateAction.MIGRATE_NEW_ARRAY;
					ret.mappingAction = MappingAction.MAP;
					ret.traverseAction = TraverseAction.MIGRATE;

					ret.baseArrayIndex = unsafe.arrayBaseOffset(c);
					ret.scaleArray = unsafe.arrayIndexScale(c);

					Version v0 = v1.getPrevious();
					String originalName = v0.getOriginalName(c.getComponentType().getName());
					Clazz c0 = v0.getNamespace().getClass(Type.getObjectType(originalName.replace('.', '/')));
					Clazz c1 = v1.getUpdate().getV1(c0);
					ret.postClass = Class.forName(v1.getUpdatableName(c1.getFqn()), true, Rubah.getLoader());
					ret.postClass = Array.newInstance(ret.postClass, 0).getClass();

					originalName = v1.getOriginalName(c.getComponentType().getName());
					originalName = v1.getUpdatableName(originalName);

					if (!AddTraverseMethod.isAllowed(originalName) ||
							BLACK_LIST.contains(originalName) ||
							ProxyGenerator.isProxyName(originalName)) {
						throw new Error("This should never happen");
						//							ret.migrateAction = MigrateAction.NONE;
						//							ret.mappingAction = MappingAction.SAME;
					}
				} else {
					LinkedList<Long> offsets = UnsafeUtils.getInstance().getOffsets(c).getOffsets();
					ret.fieldOffsets = new long[offsets.size()];
					int i = 0;
					for (Long offset : offsets) {
						ret.fieldOffsets[i++] = offset;
					}
					ret.mappingAction = MappingAction.MAP;
					try {
						Field forwardField = c.getField(AddForwardField.FIELD_NAME);
						ret.forwardFieldOffset = unsafe.objectFieldOffset(forwardField);
						ret.mappingAction = MappingAction.FORWARD;
						ret.migrateAction = MigrateAction.MIGRATE_OBJECT;
						ret.traverseAction = TraverseAction.INSTALL_PROXY;
					} catch (NoSuchFieldException e) {
						// No $forward field
						ret.migrateAction = MigrateAction.MIGRATE_OBJECT;
						ret.traverseAction = TraverseAction.MIGRATE;
					}
					Version v0 = v1.getPrevious();
					String originalName = v0.getOriginalName(c.getName());
					Clazz c0 = v0.getNamespace().getClass(Type.getObjectType(originalName.replace('.', '/')));
					Clazz c1 = v1.getUpdate().getV1(c0);
					ret.postClass = Class.forName(v1.getUpdatableName(c1.getFqn()), true, Rubah.getLoader());
					ret.proxyClass = Class.forName(ProxyGenerator.generateProxyName(ret.postClass.getName()), false, Rubah.getLoader());
					ret.proxyClassToken = UnsafeUtils.getInstance().getClassToken(ret.proxyClass);
					if (v1.getUpdate().isUpdated(c0)) {
						Class<?> conversionClass =
								Class.forName(
										ProxyGenerator.generateProxyName(ret.postClass.getName()),
										true,
										Rubah.getLoader());
						try {
							ret.conversionMethodHolder = (RubahProxy) UnsafeUtils.getUnsafe().allocateInstance(conversionClass);
						} catch (InstantiationException e) {
							throw new Error(e);
						}
					} else {
						throw new Error("Pure conversion not supported");
					}
				}
			} else {
				if (c.isArray()) {
					ret.mappingAction = MappingAction.ARRAY;
					ret.traverseAction = TraverseAction.MIGRATE;
					ret.baseArrayIndex = unsafe.arrayBaseOffset(c);
					ret.scaleArray = unsafe.arrayIndexScale(c);

					String originalName = c.getComponentType().getName();

					if (!AddTraverseMethod.isAllowed(originalName) ||
							BLACK_LIST.contains(originalName) ||
							ProxyGenerator.isProxyName(originalName)) {
						ret.migrateAction = MigrateAction.NONE;
						ret.mappingAction = MappingAction.SAME;
					} else if (outdatedClasses.contains(originalName)) {
						originalName = v1.getOriginalName(c.getComponentType().getName());
						String newUpdatableName = v1.getUpdatableName(originalName);
						Class<?> newClass = Class.forName(newUpdatableName, true, Rubah.getLoader());
						ret.migrateAction = MigrateAction.MIGRATE_OUTDATED_ARRAY;
						ret.postClassToken = UnsafeUtils.getInstance().getClassToken(Array.newInstance(newClass, 0).getClass());
					} else {
						ret.migrateAction = MigrateAction.MIGRATE_ARRAY;
					}

				} else {
					ret.proxyClass = Class.forName(ProxyGenerator.generateProxyName(c.getName()), false, Rubah.getLoader());
					if (outdatedClasses.contains(c.getName())) {
						String originalName = v1.getOriginalName(c.getName());
						String newUpdatableName = v1.getUpdatableName(originalName);
						Class<?> newClass = Class.forName(newUpdatableName, true, Rubah.getLoader());
						ret.postClassToken = UnsafeUtils.getInstance().getClassToken(newClass);
						ret.migrateAction = MigrateAction.MIGRATE_OUTDATED;
						ret.proxyClassToken = UnsafeUtils.getInstance().getClassToken(Class.forName(ProxyGenerator.generateProxyName(newClass.getName()), false, Rubah.getLoader()));
					} else {
						ret.migrateAction = MigrateAction.NONE;
						ret.proxyClassToken = UnsafeUtils.getInstance().getClassToken(Class.forName(ProxyGenerator.generateProxyName(c.getName()), false, Rubah.getLoader()));
					}
					ret.traverseAction = TraverseAction.INSTALL_FRONTIER;
					LinkedList<Long> offsets = UnsafeUtils.getInstance().getOffsets(c).getOffsets();
					ret.fieldOffsets = new long[offsets.size()];
					int i = 0;
					for (Long offset : offsets) {
						ret.fieldOffsets[i++] = offset;
					}
					ret.mappingAction = MappingAction.MAP;
					try {
						Field forwardField = c.getField(AddForwardField.FIELD_NAME);
						ret.forwardFieldOffset = unsafe.objectFieldOffset(forwardField);
						ret.mappingAction = MappingAction.FORWARD;
					} catch (NoSuchFieldException e) {
						// No $forward field
					}
				}
			}

		} catch (SecurityException | ReflectiveOperationException e) {
			throw new Error(e);
		}

		return ret;
	}

	private enum MigrateAction {
		REMOVE_PROXY,
		MIGRATE_OUTDATED,
		MIGRATE_OBJECT,
		MIGRATE_REFERENCE,
		MIGRATE_CLASS,
		MIGRATE_ARRAY,
		MIGRATE_OUTDATED_ARRAY,
		MIGRATE_NEW_ARRAY,
		SKIP,	// Skip the object, not even traversing it
		NONE	// Traverse the object but do not migrate it in any way
	}

	private enum TraverseAction {
		INSTALL_PROXY,
		INSTALL_FRONTIER,
		MIGRATE,
		NONE,
	}

	private enum MappingAction {
		FORWARD,
		MAP,
		ARRAY,
		SAME,
		NONE,
	}

	private static class ClassConversionInfo {
		long[] fieldOffsets = new long[0];
		long forwardFieldOffset;
		MigrateAction migrateAction;
		MappingAction mappingAction;
		Class<?> postClass;
		Object postClassToken;
		Class<?> proxyClass;
		Object proxyClassToken;
		RubahProxy conversionMethodHolder;
		long baseArrayIndex, scaleArray;
		TraverseAction traverseAction;
	}
}
