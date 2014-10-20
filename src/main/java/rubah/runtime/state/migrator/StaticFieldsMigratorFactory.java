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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import rubah.Rubah;
import rubah.bytecode.transformers.AddTraverseMethod;
import rubah.bytecode.transformers.ProcessUpdateClass;
import rubah.bytecode.transformers.ProxyGenerator;
import rubah.framework.Clazz;
import rubah.framework.Type;
import rubah.runtime.Version;
import rubah.runtime.state.migrator.UnsafeUtils.ClassOffsets;
import rubah.runtime.state.strategy.MigrationStrategy;

public class StaticFieldsMigratorFactory extends MigratorSubFactory {
	private Version v1;

	public StaticFieldsMigratorFactory(MigrationStrategy strategy, Version v1) {
		super(strategy);
		this.v1 = v1;
	}

	@Override
	public boolean canMigrate(Class<?> c) {
		if (c.isInterface() || !AddTraverseMethod.isAllowed(c.getName()))
			return false;

		if (c.getClassLoader() != Rubah.getLoader()) {
			return false;
		}

		if (!Rubah.getLoader().isResolved(c.getName())) {
			return false;
		}

		//TODO this method is a mess, rewrite it carefully
		String originalName = this.v1.getPrevious().getOriginalName(
				c.getName());
		if (originalName == null) {
			// Class belongs the new version, doesn't need to be
			// converted
			return false;
		}

		if (this.v1.getPrevious().getPrevious() != null
				&& this.v1.getPrevious().getPrevious()
						.getOriginalName(c.getName()) != null) {
			// Class was redefined earlier than last version
			return false;
		}

		originalName = v1.getOriginalName(c.getName());
		if (originalName != null) {
			// Class is updatable
			Clazz c1 = v1.getNamespace().getClass(
					Type.getObjectType(originalName.replace('.', '/')));
			Clazz c0 = v1.getUpdate().getV0(c1);

			if (c0 == null) {
				// Class was introduced in this version
				return false;
			}
		}

		return true;
	}

	@Override
	public Migrator buildMigrator() {
		return new StaticFieldsMigrator();
	}

	private class StaticFieldsMigrator extends Migrator {
		private boolean traverse = true;

		@Override
		protected Object doMigrate(Object pre) {
			Class<?> c = (Class<?>)pre;
			String originalName = v1.getOriginalName(c.getName());
			Clazz c1 = v1.getNamespace().getClass(
					Type.getObjectType(originalName.replace('.', '/')));
			Clazz c0 = v1.getUpdate().getV0(c1);
			Method conversionMethod;

			try {
				// Map the old class to a new class
				Class<?> conversionOwner;
				Class<?> post = Class.forName(
						v1.getUpdatableName(originalName), true,
						Rubah.getLoader());

				if (!post.isEnum()) {
//				if (v1.getUpdate().isConverted(c0)) {
//					if (v1.getUpdate().isUpdated(c0)) {
						// Run the class conversion method for the static
						// fields
						conversionOwner =
								Class.forName(
										ProxyGenerator.generateProxyName(post.getName()),
										true,
										Rubah.getLoader());
						conversionMethod = conversionOwner.getMethod(
								ProcessUpdateClass.METHOD_NAME_STATIC, c);
//					} else {
//						conversionOwner = Class.forName(
//								PureConversionClassLoader.PURE_CONVERSION_PREFFIX
//								+ v1.getNumber(), true, Rubah.getLoader());
//						// Run the class conversion method for the static fields
//						conversionMethod = conversionOwner.getMethod(
//								ProcessUpdateClass.METHOD_NAME_STATIC, c);
//						conversionOwner.getMethod(
//								ProcessUpdateClass.METHOD_NAME_STATIC, c);
//					}
					if (Rubah.getLoader().isResolved(c.getName())) {
						// Migrate static fields if resolved
						conversionMethod.invoke(null, new Object[] { null });
					}
				}

				return post;

			} catch (ClassNotFoundException e) {
				throw new Error(e);
			} catch (NoSuchMethodException e) {
				throw new Error(e);
			} catch (SecurityException e) {
				throw new Error(e);
			} catch (IllegalAccessException e) {
				throw new Error(e);
			} catch (IllegalArgumentException e) {
				throw new Error(e);
			} catch (InvocationTargetException e) {
				throw new Error(e);
			}
		}

		@Override
		public void followReferences(Object post) {
			if (!this.traverse)
				return;

			Class<?> c = (Class<?>) post;

			if (!AddTraverseMethod.isAllowed(c.getName()))
				return;

			ClassOffsets offsets = UnsafeUtils.getInstance().getOffsets(c);
			Object staticBase = offsets.getStaticBase();

			for (long offset : offsets.getStaticOffsets())
				strategy.migrate(staticBase, offset, staticBase, offset);
		}
	}

}
