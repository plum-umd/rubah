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

import rubah.Rubah;
import rubah.framework.Method;
import rubah.framework.Type;
import rubah.runtime.RubahRuntime;
import rubah.runtime.Version;
import rubah.runtime.classloader.RubahClassloader;

public class DummifyStaticInitTransformer extends RubahTransformer {
	private static final String ENUM_CLASS_NAME = Type.getType(Enum.class).getInternalName();
	private static final String RUBAH_RUNTIME_CLASS_NAME = Type.getType(RubahRuntime.class).getInternalName();
	private static final Type RUBAH_CLASSLOADER_TYPE = Type.getType(RubahClassloader.class);

	protected Version version;
	private boolean isEnum;
	private boolean foundClinit;

	public DummifyStaticInitTransformer(HashMap<String, Object> objectsMap, Version v1, ClassVisitor cv) {
		super(objectsMap, v1.getNamespace(), cv);
		this.version = v1;
	}

	@Override
	public void visit(int version, int access, String name, String signature,
			String superName, String[] interfaces) {
		super.visit(version, access, name, signature, superName, interfaces);
		this.isEnum = superName.equals(ENUM_CLASS_NAME);
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String desc,
			String signature, String[] exceptions) {

		MethodVisitor ret = super.visitMethod(access, name, desc, signature, exceptions);

		Method m = (Method) this.objectsMap.get(name);
		String methodName = (m == null ? name : m.getName());

		if (methodName.equals("<clinit>")) {
			this.foundClinit = true;
			ret = new RegisterResolvedTransformer(ret);
			if (this.version.getPrevious() != null && !this.isEnum && m != null) {
				String prevVersionName = this.version.getPrevious().getUpdatableName(this.thisClass.getFqn());

				if (prevVersionName != null && Rubah.getLoader().isResolved(prevVersionName)) {
					// Class already resolved, generate dummy static initializer
					this.generateDummyClinit(ret);
					return null;
				}
			}
		}


		return ret;
	}

	@Override
	public void visitEnd() {
		if (!foundClinit) {
			MethodVisitor mv = this.cv.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
			mv = new RegisterResolvedTransformer(mv);
			this.generateDummyClinit(mv);
		}
		this.cv.visitEnd();
	}

	private void generateDummyClinit(MethodVisitor mv) {
		mv.visitCode();
		mv.visitInsn(RETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
	}

	private class RegisterResolvedTransformer extends MethodVisitor {
		public RegisterResolvedTransformer(MethodVisitor mv) {
			super(ASM5, mv);
		}

		@Override
		public void visitCode() {
			this.mv.visitCode();
			this.mv.visitMethodInsn(
					INVOKESTATIC,
					RUBAH_RUNTIME_CLASS_NAME,
					"getLoader",
					Type.getMethodDescriptor(RUBAH_CLASSLOADER_TYPE),
					false);
			this.mv.visitLdcInsn(thisClass.getASMType().getASMType());
			this.mv.visitMethodInsn(
					INVOKEVIRTUAL,
					RUBAH_CLASSLOADER_TYPE.getInternalName(),
					"registerResolved",
					"(Ljava/lang/Class;)V", false);
		}
	}
}
