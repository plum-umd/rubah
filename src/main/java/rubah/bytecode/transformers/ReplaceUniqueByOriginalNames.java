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
package rubah.bytecode.transformers;

import java.util.HashMap;

import rubah.framework.Clazz;
import rubah.framework.Field;
import rubah.framework.Method;
import rubah.framework.Namespace;

import org.objectweb.asm.ClassVisitor;

public class ReplaceUniqueByOriginalNames extends ReplaceOriginalNamesByUnique {

	public ReplaceUniqueByOriginalNames(HashMap<Object, String> namesMap,
			HashMap<String, Object> objectsMap, Namespace namespace, ClassVisitor visitor) {
		super(namesMap, objectsMap, namespace, visitor);
	}

	@Override
	protected String rename(String name, Object obj) {
		obj = this.objectsMap.get(name);

		if (obj != null) {
			if (obj instanceof Field) {
				return ((Field)obj).getName();
			}
			if (obj instanceof Method) {
				return ((Method)obj).getName();
			}
			if (obj instanceof Clazz) {
				return ((Clazz)obj).getASMType().getInternalName();
			}
		}

		return name;
	}


}
