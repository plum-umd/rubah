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
import org.objectweb.asm.MethodVisitor;

import rubah.framework.Namespace;

public class FailFastMethodTransformer extends RubahTransformer {

	public FailFastMethodTransformer(HashMap<String, Object> objectsMap, Namespace namespace, ClassVisitor cv) {
		super(objectsMap, namespace, cv);
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String desc,
			String signature, String[] exceptions) {

		if (Modifier.isAbstract(access)) {
			// Do not install fail fast code on abstract methods
			return super.visitMethod(access, name, desc, signature, exceptions);
		}

		MethodVisitor mv = this.cv.visitMethod(access, name, desc, signature, exceptions);

		mv.visitCode();
		mv.visitTypeInsn(NEW, "java/lang/Error");
		mv.visitInsn(DUP);
		mv.visitLdcInsn("Fail fast");
		mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Error", "<init>", "(Ljava/lang/String;)V", false);
		mv.visitInsn(ATHROW);
		mv.visitMaxs(3, 1);
		mv.visitEnd();

		return new MethodVisitor(ASM5) { };
	}
}
