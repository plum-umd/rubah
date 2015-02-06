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
package rubah.framework;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import rubah.bytecode.transformers.ReplaceOriginalNamesByUnique;

public class Namespace {
	protected Map<String, Clazz> definedClasses = new HashMap<String, Clazz>();
	private Set<Clazz> bootstrapClasses = new HashSet<>();

	private static String DUMMY_PREFIX = "#";

	public static Type generateDummyName() {
		return Type.getObjectType(DUMMY_PREFIX + ReplaceOriginalNamesByUnique.generateUniqueName());
	}

	public Clazz getClass(Class<?> c) {
		return this.getClass(Type.getType(c));
	}

	/**
	 * This method is safe to use with arrays
	 * @param type
	 * @return
	 */
	public Clazz getClass(Type type) {
		return this.getClass(type, false);
	}

	public Clazz getClass(Type type, boolean isBootstrap) {
		Clazz ret = this.definedClasses.get(type.getClassName());

		if (ret == null) {
			ret = new Clazz(type, this);
			if (type.isPrimitive() || !type.getInternalName().startsWith(DUMMY_PREFIX)) {
				this.definedClasses.put(type.getClassName(), ret);
			}
		}
		if (isBootstrap)
			this.bootstrapClasses.add(ret);

		return ret;
	}

	public Clazz getClass(Type type, int dimensions) {
		StringBuffer typeDesc = new StringBuffer();

		for (int i = 0 ; i < dimensions ; i++) {
			typeDesc.append('[');
		}

		typeDesc.append(type.getDescriptor());
		return this.getClass(Type.getType(typeDesc.toString()));
	}

	public Collection<Clazz> getAllClasses() {
		return this.definedClasses.values();
	}

	public Collection<Clazz> getDefinedClasses() {
		return this.definedClasses.values();
	}

	public boolean isBootstrap(Clazz c) {
		return this.bootstrapClasses.contains(c);
	}

	public boolean isBootstrap(String className) {
		Clazz c = this.definedClasses.get(className);

		if (c != null)
			return this.bootstrapClasses.contains(c);

		return false;
	}
}
