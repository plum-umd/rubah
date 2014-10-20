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

import java.lang.reflect.Modifier;
import java.util.HashMap;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import rubah.framework.Clazz;
import rubah.framework.Namespace;
import rubah.framework.Type;

public class RubahTransformer extends ClassVisitor implements Opcodes {

	protected Clazz thisClass;
	protected HashMap<String, Object> objectsMap;
	protected Namespace namespace;
	protected boolean isInterface;

	public RubahTransformer(HashMap<String, Object> objectsMap, Namespace namespace) {
		super(ASM5);
		this.objectsMap = objectsMap;
		this.namespace = namespace;
	}

	public RubahTransformer(HashMap<String, Object> objectsMap, Namespace namespace, ClassVisitor cv) {
		super(ASM5, cv);
		this.objectsMap = objectsMap;
		this.namespace = namespace;
	}

	@Override
	public void visit(int version, int access, String name, String signature,
			String superName, String[] interfaces) {
		this.visit(version, access, name, signature, superName, interfaces, true);
	}
	public void visit(int version, int access, String name, String signature,
			String superName, String[] interfaces, boolean callNextVisitor) {
		if (this.objectsMap != null) {
			this.thisClass = (Clazz) this.objectsMap.get(name);
		} else {
			this.thisClass = this.namespace.getClass(Type.getObjectType(name));
		}

		this.isInterface = Modifier.isInterface(access);

		if (callNextVisitor)
			super.visit(version, access, name, signature, superName, interfaces);
	}
}
