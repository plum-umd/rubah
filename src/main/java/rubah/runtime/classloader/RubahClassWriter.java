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
package rubah.runtime.classloader;

import java.util.LinkedList;

import org.objectweb.asm.ClassWriter;

import rubah.framework.Clazz;
import rubah.framework.Namespace;
import rubah.framework.Type;
import rubah.runtime.Version;

public class RubahClassWriter extends ClassWriter {
	private Version version;
	private Namespace namespace;

	public RubahClassWriter(int flags, Version version, Namespace namespace) {
		super(flags);
		this.version = version;
		this.namespace = namespace;
	}

	private Type getRealType(String typeName) {
		Type type = Type.getObjectType(typeName);
		typeName =
				this.version.getOriginalName(type.getClassName());
		if (typeName != null) {
			type = Type.getObjectType(typeName);
		}
		return type;
	}

	@Override
	protected String getCommonSuperClass(String typeName1, String typeName2) {

		Type type1 = this.getRealType(typeName1);
		Type type2 = this.getRealType(typeName2);

		LinkedList<Clazz> superClasses = new LinkedList<Clazz>();
		Clazz c = this.namespace.getClass(type1);
		while (c != null) {
			superClasses.add(c);
			c = c.getParent();
		}

		c = this.namespace.getClass(type2);
		while (c != null) {
			for (Clazz c2 : superClasses) {
				if (c.equals(c2)) {
					String ret =
							this.version.getOriginalName(c.getFqn());
					if (ret != null) {
						return ret.replace('.', '/');
					}
					return c.getASMType().getInternalName();
				}
			}
			c = c.getParent();
		}

		throw new Error("Should not reach this code: " + typeName1 + " - " + typeName2);
	}
}
