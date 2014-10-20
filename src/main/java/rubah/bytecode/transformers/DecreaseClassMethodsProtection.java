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

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

import rubah.tools.BootstrapJarProcessor;

public class DecreaseClassMethodsProtection extends ClassVisitor {
	private String className;

	public DecreaseClassMethodsProtection(ClassVisitor cv) {
		super(BootstrapJarProcessor.ASM5, cv);
	}

	@Override
	public void visit(int version, int access, String name,
			String signature, String superName, String[] interfaces) {
		// Make access public so that fields can be set from Rubah
		access &= ~(BootstrapJarProcessor.ACC_PROTECTED & BootstrapJarProcessor.ACC_PRIVATE);
		access |= BootstrapJarProcessor.ACC_PUBLIC;

		if (AddTraverseMethod.isAllowed(name.replace('/', '.')))
			access &= ~BootstrapJarProcessor.ACC_FINAL;

		className = name;

		super.visit(version, access, name, signature, superName, interfaces);
	}

	@Override
	public MethodVisitor visitMethod(int access, String name,
			String desc, String signature, String[] exceptions) {
		if (!className.startsWith("java/lang") || className.equals(Enum.class.getName().replace('.', '/')))
			access &= ~BootstrapJarProcessor.ACC_FINAL;

		if (!Modifier.isPrivate(access) || Modifier.isStatic(access)) {
			access &= ~BootstrapJarProcessor.ACC_PRIVATE;
			access &= ~BootstrapJarProcessor.ACC_PROTECTED;
			access |= BootstrapJarProcessor.ACC_PUBLIC;
		}

		return super.visitMethod(access, name, desc, signature, exceptions);
	}

}