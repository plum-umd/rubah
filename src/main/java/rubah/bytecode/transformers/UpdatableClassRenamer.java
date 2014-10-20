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
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureWriter;

import rubah.framework.Clazz;
import rubah.framework.Type;
import rubah.runtime.Version;

public class UpdatableClassRenamer extends ClassVisitor implements Opcodes {
	private static final String NAME_VERSION_SEPARATOR = "__";

	private HashMap<String, Object> objectsMap;

	private Version version;

	public UpdatableClassRenamer(
			Version version,
			HashMap<String, Object> objectsMap,
			ClassVisitor cv) {
		super(ASM5, cv);
		this.version = version;
		this.objectsMap = objectsMap;
	}

	public static String rename(String className, int version) {
		return className + NAME_VERSION_SEPARATOR + version;
	}

	@Override
	public void visit(int version, int access, String name, String signature,
			String superName, String[] interfaces) {

		Clazz c = (Clazz) this.objectsMap.get(name);

		if (c != null) {
			String realName = c.getASMType().getInternalName();
			name = (realName != null ? realName : name);
		}

		if (interfaces != null) {
			for (int i = 0; i < interfaces.length; i++) {
				interfaces[i] = this.version.renameInternalIfUpdatable(interfaces[i]);
			}
		}

		// Make access public so that fields can be set from Rubah
		access &= ~(ACC_PROTECTED & ACC_PRIVATE);
		access |= ACC_PUBLIC;

		access &= ~ACC_FINAL;
		super.visit(
				version,
				access,
				this.version.renameInternalIfUpdatable(name),
				signature,
				(superName != null ? this.version.renameInternalIfUpdatable(superName) : null),
				interfaces);
	}

	@Override
	public FieldVisitor visitField(int access, String name, String desc,
			String signature, Object value) {

		if (signature != null) {
			SignatureTypeRenamer renamer = new SignatureTypeRenamer();
			new SignatureReader(signature).accept(renamer);
			signature = renamer.toString();
		}

		return super.visitField(
				access,
				name,
				this.version.renameDescIfUpdatable(desc),
				signature,
				value);
	}

	@Override
	public void visitOuterClass(String owner, String name, String desc) {
		super.visitOuterClass(
				this.version.renameInternalIfUpdatable(owner),
				name,
				desc);
	}


	@Override
	public void visitInnerClass(String name, String outerName, String innerName,
			int access) {
		super.visitInnerClass(
				this.version.renameInternalIfUpdatable(name),
				(outerName != null) ? this.version.renameInternalIfUpdatable(outerName) : null,
				(innerName != null) ? this.version.renameInternalIfUpdatable(innerName) : null,
				access);
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String desc,
			String signature, String[] exceptions) {
		access &= ~ACC_FINAL;

		if (signature != null) {
			SignatureTypeRenamer renamer = new SignatureTypeRenamer();
			new SignatureReader(signature).accept(renamer);
			signature = renamer.toString();
		}

		if (exceptions != null) {
			for (int i = 0; i < exceptions.length; i++) {
				exceptions[i] = this.version.renameInternalIfUpdatable(exceptions[i]);
			}
		}

		return new MethodTypeRenamer(
				super.visitMethod(
						access,
						name,
						this.version.renameMethodDescIfUpdatable(desc),
						signature,
						exceptions));
	}


	private class SignatureTypeRenamer extends SignatureWriter {
		@Override
		public void visitClassType(String name) {
			name = UpdatableClassRenamer.this.version.renameInternalIfUpdatable(name);
			super.visitClassType(name);
		}
	}

	private class MethodTypeRenamer extends MethodVisitor {
		public MethodTypeRenamer(MethodVisitor mv) {
			super(ASM4, mv);
		}

		@Override
		public void visitTypeInsn(int opcode, String type) {
			super.visitTypeInsn(
					opcode,
					UpdatableClassRenamer.this.version.renameInternalIfUpdatable(type));
		}

		@Override
		public void visitFieldInsn(int opcode, String owner, String name,
				String desc) {
			super.visitFieldInsn(
					opcode,
					UpdatableClassRenamer.this.version.renameInternalIfUpdatable(owner),
					name,
					UpdatableClassRenamer.this.version.renameDescIfUpdatable(desc));
		}

		@Override
		public void visitMethodInsn(int opcode, String owner, String name,
				String desc, boolean itf) {
			super.visitMethodInsn(
					opcode,
					UpdatableClassRenamer.this.version.renameInternalIfUpdatable(owner),
					name,
					UpdatableClassRenamer.this.version.renameMethodDescIfUpdatable(desc),
					itf);
		}

		@Override
		public void visitLdcInsn(Object cst) {
			if (cst instanceof org.objectweb.asm.Type) {
				Type type = new Type((org.objectweb.asm.Type) cst);
				if (type.getSort() == org.objectweb.asm.Type.OBJECT || type.getSort() == org.objectweb.asm.Type.ARRAY) {
					cst = UpdatableClassRenamer.this.version.renameIfUpdatable(type).getASMType();
				}
			}
			super.visitLdcInsn(cst);
		}

		@Override
		public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
			super.visitTryCatchBlock(
					start,
					end,
					handler,
					(type == null ? null : version.renameInternalIfUpdatable(type)));
		}

	}
}
