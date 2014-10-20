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
import java.util.Random;
import java.util.UUID;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import rubah.framework.Clazz;
import rubah.framework.Field;
import rubah.framework.Method;
import rubah.framework.Namespace;
import rubah.framework.Type;

public class ReplaceOriginalNamesByUnique extends ClassVisitor implements Opcodes {
	protected HashMap<Object, String> namesMap;
	protected HashMap<String, Object> objectsMap;
	private Clazz thisClass;
	private Namespace namespace;

	public ReplaceOriginalNamesByUnique(HashMap<Object, String> namesMap,
			HashMap<String, Object> objectsMap, Namespace namespace, ClassVisitor visitor) {
		super(ASM5, visitor);
		this.namesMap = namesMap;
		this.objectsMap = objectsMap;
		this.namespace = namespace;
	}

	protected String rename(String name, Object obj) {
		name = this.namesMap.get(obj);

		if (name != null) {
			return name;
		}

		name = generateUniqueName();

		this.namesMap.put(obj, name);
		this.objectsMap.put(name, obj);

		return name;
	}

	private static Random r = new Random();

	public static String generateUniqueName() {
		return new UUID(r.nextLong(), r.nextLong()).toString();
	}


	@Override
	public void visit(int version, int access, String name, String signature,
			String superName, String[] interfaces) {
		this.thisClass = this.namespace.getClass(Type.getObjectType(name));

		name = this.rename(name, this.thisClass);

		super.visit(
				version,
				access,
				name,
				signature,
				superName,
				interfaces);
	}

	@Override
	public FieldVisitor visitField(int access, String name, String desc,
			String signature, Object value) {

		Field field = new Field(access, name, this.namespace.getClass(Type.getType(desc)), value != null);
		name = this.rename(name, field);

		return super.visitField(access, name, desc, signature, value);
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String desc,
			String signature, String[] exceptions) {

		name = this.rename(name, new Method(access, name, desc, this.namespace));

		return new MethodBodyReplacer(
				super.visitMethod(access, name, desc, signature, exceptions));
	}

	private class MethodBodyReplacer extends MethodVisitor {

		public MethodBodyReplacer(MethodVisitor mv) {
			super(ASM5, mv);
		}

		@Override
		public void visitMethodInsn(int opcode, String owner, String name,
				String desc, boolean itf) {

			name = ReplaceOriginalNamesByUnique.this.rename(
					name, new Method(0, name, desc, ReplaceOriginalNamesByUnique.this.namespace));

			super.visitMethodInsn(opcode, owner, name, desc, itf);
		}
	}
}
