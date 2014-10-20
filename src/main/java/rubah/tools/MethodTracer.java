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
package rubah.tools;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.AdviceAdapter;

public class MethodTracer extends ReadWriteTool implements Opcodes {
	public static final String TOOL_NAME = "tracer";
	private String className;
	private String methodName;

	@Override
	protected void foundClassFile(String name, InputStream inputStream) throws IOException {
		ClassReader reader = new ClassReader(inputStream);
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		ClassVisitor visitor = writer;

		visitor = new MethodTracerClassVisitor(writer);

		reader.accept(visitor, ClassReader.EXPAND_FRAMES);
		this.addFileToOutJar(name, writer.toByteArray());
	}

	private class MethodTracerClassVisitor extends ClassVisitor {

		public MethodTracerClassVisitor(ClassVisitor cv) {
			super(ASM5, cv);
		}

		@Override
		public void visit(int version, int access, String name, String signature,
				String superName, String[] interfaces) {
			MethodTracer.this.className = name;
			super.visit(version, access, name, signature, superName, interfaces);
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String desc,
				String signature, String[] exceptions) {
			MethodTracer.this.methodName = name + desc;
			MethodVisitor ret =
					super.visitMethod(access, name, desc, signature, exceptions);

			if (Modifier.isAbstract(access)) {
				return ret;
			}

			return new MethodTracerMethodVisitor(access, name, desc, ret);
		}
	}

	private class MethodTracerMethodVisitor extends AdviceAdapter {
		private Label startFinally = new Label();

		public MethodTracerMethodVisitor(int access, String name, String desc, MethodVisitor mv) {
			super(ASM5, mv, access, name, desc);
		}

		@Override
		public void visitCode() {
			super.visitCode();
			this.mv.visitLabel(this.startFinally);
			this.mv.visitLdcInsn(MethodTracer.this.className);
			this.mv.visitLdcInsn(MethodTracer.this.methodName);
			this.mv.visitMethodInsn(INVOKESTATIC, "rubah/tools/MethodTracer", "print", "(Ljava/lang/String;Ljava/lang/String;)V", false);
		}

		@Override
		public void visitMaxs(int maxStack, int maxLocals) {
			Label endFinally = new Label();
			this.visitTryCatchBlock(this.startFinally, endFinally, endFinally, null);
			this.mv.visitLabel(endFinally);
			this.onFinally(ATHROW);
			this.visitInsn(ATHROW);
			super.visitMaxs(maxStack, maxLocals);
		}

		@Override
		protected void onMethodExit(int opcode) {
			if (opcode != ATHROW) {
				this.onFinally(opcode);
			}
		}

		private void onFinally(int opcode) {
			this.mv.visitFieldInsn(GETSTATIC, "rubah/tools/MethodTracer", "level", "I");
			this.mv.visitInsn(ICONST_1);
			this.mv.visitInsn(ISUB);
			this.mv.visitFieldInsn(PUTSTATIC, "rubah/tools/MethodTracer", "level", "I");
		}


	}

	public static int level = 0;

	public static void print(String className, String methodName) {
		level++;
		StringBuffer buf = new StringBuffer();

		for (int i = 0 ; i < level ; i++) {
			buf.append("|-");
		}

		buf.append(className);
		buf.append(" - ");
		buf.append(methodName);

		System.out.println(buf.toString());
	}
}
