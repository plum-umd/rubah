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
import java.util.Set;

import rubah.Rubah;
import rubah.runtime.Version;
import rubah.runtime.state.strategy.MigrationStrategy;


public class ArrayMigratorFactory extends MigratorSubFactory {
	private Version v1;
	protected Set<String> transformedClasses, outdatedClasses;
	private ArrayMigrator theMigrator = new ArrayMigrator();

	public ArrayMigratorFactory(
			Set<String> transformedClasses,
			Set<String> outdatedClasses,
			Version v1,
			MigrationStrategy strategy) {
		super(strategy);
		this.transformedClasses = transformedClasses;
		this.outdatedClasses = outdatedClasses;
		this.v1 = v1;
	}

	@Override
	public boolean canMigrate(Class<?> c) {
		return c.isArray();
	}

	@Override
	public Migrator buildMigrator() {
		return this.theMigrator;
	}

	protected class ArrayMigrator extends Migrator {

		private Object migrateArray(Object pre, Object post) {
			if (pre.getClass().getComponentType().isPrimitive()) {
				return post;
			}

			Object[] newArray = (Object[]) post;
			Object[] array = (Object[]) pre;

//			Unsafe unsafe = Rubah.getUnsafe();
//			long base = unsafe.arrayBaseOffset(post.getClass());
//			long scale = unsafe.arrayIndexScale(post.getClass());

			for (int i = 0; i < newArray.length; i++)
				newArray[i] = strategy.migrate(array[i]);

//			for (int i = 0; i < newArray.length; i++) {
//				long index = base + (i * scale);
//				strategy.migrate(array, index, newArray, index);
//			}

//			long index = base;
//			for (int i = 0; i < newArray.length; i++, index += scale) {
//				Object obj = unsafe.getObject(array, index);
//				Object migrated = strategy.migrate(obj);
//				if (pre == post)
//					unsafe.compareAndSwapObject(newArray, index, obj, migrated);
//				else
//					unsafe.putObject(newArray, index, migrated);
//			}

			return post;
		}

		@Override
		protected Object doMigrate(Object obj) {
			Class<?> objClass = obj.getClass();
			Class<?> arrayType = objClass.getComponentType();


			if (!transformedClasses.contains(arrayType.getName()) && !outdatedClasses.contains(arrayType.getName())) {
				return this.migrateArray(obj, obj);
			}

			String originalName = v1.getOriginalName(arrayType.getName());

			if (originalName == null) {
				return this.migrateArray(obj, obj);
			}

			String newUpdatableName = v1.getUpdatableName(originalName);

			if (newUpdatableName.equals(arrayType.getName())) {
				return this.migrateArray(obj, obj);
			}

			Object[] array = (Object[]) obj;
			Object[] newArray;

			try {
				Class<?> newClass = Class.forName(newUpdatableName, true, Rubah.getLoader());

//				if (outdatedClasses.contains(arrayType.getName())) {
//					UnsafeUtils.getInstance().changeClass(obj, Array.newInstance(objClass, 0), Array.newInstance(newClass, 0));
//					newArray = array;
//				} else {
					newArray = (Object[]) Array.newInstance(newClass, Array.getLength(obj));
//				}

				return this.migrateArray(array, newArray);
			} catch (NegativeArraySizeException e) {
				throw new Error(e);
			} catch (IllegalArgumentException e) {
				throw new Error(e);
			} catch (ClassNotFoundException e) {
				throw new Error(e);
			}
		}

		@Override
		public void followReferences(Object post) {
//			if (pre.getClass().getComponentType().isPrimitive()) {
//				return;
//			}
//
//			Object[] newArray = (Object[]) post;
//			Object[] array = (Object[]) pre;
//			for (int i = 0; i < newArray.length; i++) {
//				newArray[i] = this.strategy.migrate(array[i]);
//			}
		}

	}
}
