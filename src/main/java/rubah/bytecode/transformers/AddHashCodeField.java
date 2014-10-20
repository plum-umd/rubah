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

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.commons.AdviceAdapter;

import rubah.framework.Field;
import rubah.framework.Namespace;
import rubah.framework.Type;

public class AddHashCodeField extends RubahTransformer {

	public static final int FIELD_MODIFIERS = ACC_PRIVATE | ACC_FINAL;
	public static final String FIELD_NAME = "$hashCode";
	public static final String FIELD_DESC = Type.INT_TYPE.getDescriptor();

	private boolean isClassInteresting;

	public AddHashCodeField(Namespace namespace, ClassVisitor cv) {
		super(null, namespace, cv);
	}

	@Override
	public void visit(int version, int access, String name, String signature,
			String superName, String[] interfaces) {
		super.visit(version, access, name, signature, superName, interfaces);

		this.isClassInteresting =
				this.thisClass.getFields().contains(
						new Field(
								FIELD_MODIFIERS,
								FIELD_NAME,
								this.namespace.getClass(Type.INT_TYPE),
								false));
	}

	@Override
	public FieldVisitor visitField(int access, String name, String desc,
			String signature, Object value) {

		Field f = (this.objectsMap != null ? ((Field)this.objectsMap.get(name)) : null);
		String realName = (f != null ? f.getName() : name);

		if (realName.equals(FIELD_NAME))
			this.isClassInteresting = false;

		return super.visitField(access, name, desc, signature, value);
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String desc,
			String signature, String[] exceptions) {
		MethodVisitor ret = super.visitMethod(access, name, desc, signature, exceptions);

		if (this.isClassInteresting && name.equals("<init>")) {
			return new SetHashCodeMethodVisitor(ret, access, name, desc);
		}

		return ret;
	}

	@Override
	public void visitEnd() {
		if (this.isClassInteresting) {
			this.visitField(FIELD_MODIFIERS, FIELD_NAME, FIELD_DESC, null, null);
		}

		super.visitEnd();
	}

	private class SetHashCodeMethodVisitor extends AdviceAdapter {

		protected SetHashCodeMethodVisitor(MethodVisitor mv, int access,
				String name, String desc) {
			super(ASM5, mv, access, name, desc);
		}

		@Override
		protected void onMethodEnter() {
			this.mv.visitVarInsn(ALOAD, 0);
			this.mv.visitVarInsn(ALOAD, 0);
			this.mv.visitMethodInsn(
					INVOKESPECIAL,
					"java/lang/Object",
					"hashCode",
					"()I",
					false);
			this.mv.visitFieldInsn(
					PUTFIELD,
					AddHashCodeField.this.thisClass.getASMType().getInternalName(),
					FIELD_NAME,
					FIELD_DESC);
		}

		@Override
		public void visitMaxs(int maxStack, int maxLocals) {
			super.visitMaxs(Math.max(2, maxStack), maxLocals);
		}
	}
}
