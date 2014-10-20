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

import java.util.HashMap;
import java.util.Set;

import org.cliffc.high_scale_lib.Counter;

import rubah.Rubah;
import rubah.bytecode.RubahProxy;
import rubah.bytecode.transformers.ProxyGenerator;
import rubah.framework.Clazz;
import rubah.framework.Type;
import rubah.runtime.Version;
import rubah.runtime.state.strategy.MigrationStrategy;

public class UpdatableObjectMigratorFactory extends DefaultObjectMigratorFactory {
	private Version v1;
	private Counter migrated = new Counter();
	private Set<String> outdatedClasses;
	private UpdatableObjectMigrator migrator = new UpdatableObjectMigrator();

	private HashMap<Class<?>, ConversionInfo> conversionInfo = new HashMap<>();

	public UpdatableObjectMigratorFactory(
			Set<String> outdatedClasses,
			Version v1,
			MigrationStrategy strategy) {
		super(strategy);
		this.v1 = v1;
		this.outdatedClasses = outdatedClasses;
	}

	@Override
	public boolean canMigrate(Class<?> preClass) {
		return this.outdatedClasses.contains(preClass.getName());
	}

	@Override
	public long countMigrated() {
		return migrated.estimate_get();
	}

	@Override
	public Migrator buildMigrator() {
		return this.migrator;
	}

	private ConversionInfo getConversionInfo(Class<?> c) throws ClassNotFoundException, NoSuchMethodException, SecurityException {

		ConversionInfo ret = this.conversionInfo.get(c);

		if (ret == null) {
			ret = new ConversionInfo();

			Version v0 = v1.getPrevious();
			String originalName = v0.getOriginalName(c.getName());
			Clazz c0 = v0.getNamespace().getClass(Type.getObjectType(originalName.replace('.', '/')));
			Clazz c1 = v1.getUpdate().getV1(c0);
			ret.postClass = Class.forName(v1.getUpdatableName(c1.getFqn()), true, Rubah.getLoader());

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
//				ret.conversionMethod =
//						conversionClass.getMethod(
//								ProcessUpdateClass.METHOD_NAME,
//								c,
//								ret.postClass);
			} else {
				throw new Error("Pure conversion classes not implemented");
//				Class<?> conversionClass =
//						Class.forName(
//								PureConversionClassLoader.PURE_CONVERSION_PREFFIX + v1.getNumber(),
//								true,
//								Rubah.getLoader());
//				ret.conversionMethod =
//						conversionClass.getMethod(
//								ProcessUpdateClass.METHOD_NAME,
//								c,
//								c);
			}

			this.conversionInfo.put(c, ret);
		}

		return ret;
	}

	protected class UpdatableObjectMigrator extends DefaultObjectMigrator {
		@Override
		protected Object doMigrate(Object pre) {
			Object post;

			try {
				ConversionInfo info = getConversionInfo(pre.getClass());

				// Create the respective object in the new version
				post = sun.misc.Unsafe.getUnsafe().allocateInstance(info.postClass);
//				post = pre;

				// Get the conversion method
//				Method conversionMethod = info.conversionMethod;

				// Set the hash code
				UnsafeUtils.getInstance().setHashCode(pre, post);

				// Run the conversion method
				info.conversionMethodHolder.convert(pre, post);
//				this.traverse = (Boolean) conversionMethod.invoke(null, pre, post);
			} catch (ClassNotFoundException e) {
				throw new Error(e);
			} catch (InstantiationException e) {
				throw new Error(e);
			} catch (IllegalArgumentException e) {
				throw new Error(e);
			} catch (SecurityException e) {
				throw new Error(e);
//			} catch (IllegalAccessException e) {
//				throw new Error(e);
//			} catch (InvocationTargetException e) {
//				throw new Error(e);
			} catch (NoSuchMethodException e) {
				throw new Error(e);
			}

//			migrated.increment();

			return post;
		}
//
//		@Override
//		public void followReferences(Object post) {
//			if (this.traverse)
//				super.followReferences(post);
//		}
	}

	private static class ConversionInfo {
		Class<?> postClass;
		RubahProxy conversionMethodHolder;
	}
}
