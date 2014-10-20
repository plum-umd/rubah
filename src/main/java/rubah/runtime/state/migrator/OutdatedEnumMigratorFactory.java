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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import rubah.Rubah;
import rubah.framework.Clazz;
import rubah.framework.Type;
import rubah.runtime.Version;
import rubah.runtime.state.strategy.MigrationStrategy;

public class OutdatedEnumMigratorFactory extends DefaultObjectMigratorFactory {
	private Version v1;
	private Set<String> outdatedClasses;
	private UpdatableObjectMigrator migrator = new UpdatableObjectMigrator();

	private ConcurrentHashMap<Class<?>, ConversionInfo> conversionInfo = new ConcurrentHashMap<>();

	public OutdatedEnumMigratorFactory(
			Set<String> redefinedClasses,
			Version v1,
			MigrationStrategy strategy) {
		super(strategy);
		this.v1 = v1;
		this.outdatedClasses = redefinedClasses;
	}

	@Override
	public boolean canMigrate(Class<?> preClass) {
		return preClass.isEnum() && this.outdatedClasses.contains(preClass.getName());
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
			ret.updatedEnum = Class.forName(v1.getUpdatableName(c1.getFqn()), true, Rubah.getLoader());
			this.conversionInfo.put(c, ret);
		}

		return ret;
	}

	protected class UpdatableObjectMigrator extends DefaultObjectMigrator {
		@Override
		protected Object doMigrate(Object pre) {
			Object ret = null;
			try {
				ConversionInfo info = getConversionInfo(pre.getClass());
				
				for (Object obj : info.updatedEnum.getEnumConstants()) {
					if (((Enum)obj).name().equals((((Enum)pre).name()))) {
						ret = obj;
						break;
					}
				}
			} catch (ClassNotFoundException e) {
				throw new Error(e);
			} catch (IllegalArgumentException e) {
				throw new Error(e);
			} catch (SecurityException e) {
				throw new Error(e);
			} catch (NoSuchMethodException e) {
				throw new Error(e);
			}

			return ret;
		}
	}

	private static class ConversionInfo {
		Class<?> updatedEnum;
	}
}
