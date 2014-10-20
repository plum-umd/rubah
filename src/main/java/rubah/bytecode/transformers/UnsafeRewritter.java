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

import org.objectweb.asm.ClassVisitor;

import rubah.Rubah;
import rubah.framework.Namespace;
import rubah.framework.Type;
import sun.misc.Unsafe;

public class UnsafeRewritter extends ReflectionRewritter {

	protected UnsafeRewritter(
			HashMap<String, Object> objectsMap, Namespace namespace,
			ClassVisitor cv) {
		super(objectsMap, namespace, cv);
	}

	public UnsafeRewritter(Namespace namespace, ClassVisitor cv) {
		super(null, namespace, cv);
	}

	@Override
	protected void setTranslations() {
		Type rubahType = Type.getType(Rubah.class);
		Type objectType = Type.getType(Object.class);
		Type unsafeType = Type.getType(Unsafe.class);

		this.translations.put(
				new MethodInvocation(unsafeType, "getObject", objectType, objectType, Type.LONG_TYPE),
				new MethodInvocation(rubahType, "getObject", objectType, unsafeType, objectType, Type.LONG_TYPE).setOpcode(INVOKESTATIC));
		this.translations.put(
				new MethodInvocation(unsafeType, "getObjectVolatile", objectType, objectType, Type.LONG_TYPE),
				new MethodInvocation(rubahType, "getObjectVolatile", objectType, unsafeType, objectType, Type.LONG_TYPE).setOpcode(INVOKESTATIC));
	}

}
