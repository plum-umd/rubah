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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.ow2.util.base64.Base64;

import rubah.framework.Clazz;
import rubah.framework.Field;
import rubah.framework.Method;
import rubah.framework.Namespace;
import rubah.framework.Type;

public class BasicClassInfoGatherer extends RubahTransformer {

	public BasicClassInfoGatherer(Namespace namespace,ClassVisitor visitor) {
		super(null, namespace, visitor);
	}

	public BasicClassInfoGatherer(Namespace namespace) {
		super(null, namespace);
	}

	@Override
	public void visit(int version, int access, String name, String signature,
			String superName, String[] interfaces) {
		super.visit(version, access, name, signature, superName, interfaces, false);

		if (superName != null) {
			this.thisClass.setParent(this.namespace.getClass(Type.getObjectType(superName)));
		}

		if (interfaces != null) {
			for (String ifaceName : interfaces) {
				this.thisClass.getInterfaces().add(this.namespace.getClass(Type.getObjectType(ifaceName)));
			}
		}

		this.thisClass.setInterface(Modifier.isInterface(access));

		if (this.cv != null)
			this.cv.visit(version, access, name, signature, superName, interfaces);
	}

	@Override
	public FieldVisitor visitField(int access, String name, String desc,
			String signature, Object value) {

		this.thisClass.getFields().add(
				new Field(access, name, this.namespace.getClass(Type.getType(desc)), value != null));

		return super.visitField(access, name, desc, signature, value);
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String desc,
			String signature, String[] exceptions) {

		Clazz retType = this.namespace.getClass(Type.getReturnType(desc));
		List<Clazz> argTypes = new ArrayList<Clazz>();

		for (Type argType : Type.getArgumentTypes(desc)) {
			argTypes.add(this.namespace.getClass(argType));
		}

		Method m = new Method(access, name, retType, argTypes);

		this.thisClass.addMethod(m);

		MethodVisitor ret =
				super.visitMethod(access, name, desc, signature, exceptions);

		if (!Modifier.isAbstract(access)) {
			ret = new BodyMD5Computer(ret, m);
		}

		return ret;
	}

	private class BodyMD5Computer extends MethodVisitor {
		private Method method;
		private MessageDigest digest;
		private HashMap<Label, Integer> labels =
				new HashMap<Label, Integer>();
		private int lastFoundLabel = 0;

		public BodyMD5Computer(MethodVisitor mv, Method method) {
			super(ASM5, mv);
			this.method = method;

			try {
				this.digest = MessageDigest.getInstance("MD5");
			} catch (NoSuchAlgorithmException e) {
				throw new Error(e);
			}
		}

		private void update(int i) {
			this.digest.update((byte)(i >>> 24));
			this.digest.update((byte)(i >>> 16));
			this.digest.update((byte)(i >>> 8));
			this.digest.update((byte) i);
		}

		private void update(String s) {
			if (s != null) {
				this.digest.update(s.getBytes());
			}
		}

		private void update(Label ... labels) {
			if (labels == null) {
				return;
			}

			for(Label label : labels) {
				Integer labelIdx = this.labels.get(label);

				if (labelIdx == null) {
					labelIdx = this.lastFoundLabel++;
					this.labels.put(label, labelIdx);
				}

				this.update(labelIdx);
			}
		}

		@Override
		public void visitEnd() {
			this.method.setBodyMD5(new String(Base64.encode(this.digest.digest())));
			super.visitEnd();
		}

		@Override
		public void visitInsn(int opcode) {
			this.update(opcode);
			super.visitInsn(opcode);
		}

		@Override
		public void visitIntInsn(int opcode, int operand) {
			this.update(opcode);
			this.update(operand);
			super.visitIntInsn(opcode, operand);
		}

		@Override
		public void visitVarInsn(int opcode, int var) {
			this.update(opcode);
			this.update(var);
			super.visitVarInsn(opcode, var);
		}

		@Override
		public void visitTypeInsn(int opcode, String type) {
			this.update(opcode);
			this.update(type);
			super.visitTypeInsn(opcode, type);
		}

		@Override
		public void visitFieldInsn(int opcode, String owner, String name,
				String desc) {
			this.update(opcode);
			this.update(owner);
			this.update(name);
			super.visitFieldInsn(opcode, owner, name, desc);
		}

		@Override
		public void visitMethodInsn(int opcode, String owner, String name,
				String desc, boolean itf) {
			this.update(opcode);
			this.update(owner);
			this.update(name);
			this.update(desc);
			super.visitMethodInsn(opcode, owner, name, desc, itf);
		}

		@Override
		public void visitLabel(Label label) {
			this.update(label);
			super.visitLabel(label);
		}

		@Override
		public void visitJumpInsn(int opcode, Label label) {
			this.update(opcode);
			this.update(label);
			super.visitJumpInsn(opcode, label);
		}

		@Override
		public void visitLdcInsn(Object cst) {
			this.update(LDC);
			this.update(cst.hashCode());
			super.visitLdcInsn(cst);
		}

		@Override
		public void visitIincInsn(int var, int increment) {
			this.update(IINC);
			this.update(var);
			this.update(increment);
			super.visitIincInsn(var, increment);
		}

		@Override
		public void visitTableSwitchInsn(int min, int max, Label dflt,
				Label... labels) {
			this.update(TABLESWITCH);
			this.update(min);
			this.update(max);
			this.update(dflt);
			this.update(labels);
			super.visitTableSwitchInsn(min, max, dflt, labels);
		}

		@Override
		public void visitMultiANewArrayInsn(String desc, int dims) {
			this.update(MULTIANEWARRAY);
			this.update(desc);
			this.update(dims);
			super.visitMultiANewArrayInsn(desc, dims);
		}

		@Override
		public void visitTryCatchBlock(Label start, Label end, Label handler,
				String type) {
			this.update(start);
			this.update(end);
			this.update(handler);
			this.update(type);
			super.visitTryCatchBlock(start, end, handler, type);
		}
	}
}
