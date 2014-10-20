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
import org.objectweb.asm.MethodVisitor;

import rubah.framework.Field;
import rubah.framework.Method;
import rubah.framework.Namespace;
import rubah.framework.Type;

public class AddHashCodeMethod extends RubahTransformer {

	public static final String HASHCODE_METHOD_NAME = "hashCode";
	public static final String HASHCODE_METHOD_DESC = Type.getMethodDescriptor(Type.INT_TYPE);

	private boolean isClassInteresting;
	private boolean foundHashCode = false;

	public AddHashCodeMethod(Namespace namespace, ClassVisitor visitor) {
		super(null, namespace, visitor);
	}

	public AddHashCodeMethod(HashMap<String, Object> objectsMap, Namespace namespace, ClassVisitor visitor) {
		super(objectsMap, namespace, visitor);
	}

	@Override
	public void visit(int version, int access, String name, String signature,
			String superName, String[] interfaces) {
		super.visit(version, access, name, signature, superName, interfaces);

//		this.isClassInteresting =
//				!this.thisClass.isInterface() &&
//				this.thisClass.getNamespace().equals(this.namespace) &&
//				this.thisClass.getParent() != null &&
//				!this.thisClass.getParent().getNamespace().equals(this.namespace);

		this.isClassInteresting =
				this.thisClass.getFields().contains(
						new Field(
								AddHashCodeField.FIELD_MODIFIERS,
								AddHashCodeField.FIELD_NAME,
								this.namespace.getClass(Type.INT_TYPE),
								false));
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String desc,
			String signature, String[] exceptions) {
		Method m = (this.objectsMap != null ? (Method) this.objectsMap.get(name) : null);

		if (m != null && m.getName().equals(HASHCODE_METHOD_NAME) && desc.equals(HASHCODE_METHOD_DESC)) {
			this.foundHashCode = true;
		}

		if (name.equals(HASHCODE_METHOD_NAME) && this.thisClass.getASMType().equals(Type.getType(Enum.class)))
			access &= ~ACC_FINAL;

		return super.visitMethod(access, name, desc, signature, exceptions);
	}

	@Override
	public void visitEnd() {
		if (this.isClassInteresting && !this.foundHashCode) {
			this.generateHashCodeMethod();
		}

		super.visitEnd();
	}

	private void generateHashCodeMethod() {
		MethodVisitor mv = this.visitMethod(
				ACC_PUBLIC,
				HASHCODE_METHOD_NAME,
				HASHCODE_METHOD_DESC,
				null,
				null);
		mv.visitCode();
		mv.visitVarInsn(ALOAD, 0);
		mv.visitFieldInsn(
				GETFIELD,
				this.thisClass.getASMType().getInternalName(),
				AddHashCodeField.FIELD_NAME,
				Type.INT_TYPE.getDescriptor());
		mv.visitInsn(IRETURN);
		mv.visitMaxs(1, 1);
		mv.visitEnd();
	}
}
