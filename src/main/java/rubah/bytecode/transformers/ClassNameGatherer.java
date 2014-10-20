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

import java.util.HashSet;
import java.util.Set;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import rubah.ConversionClass;
import rubah.framework.Type;

public class ClassNameGatherer extends ClassVisitor implements Opcodes {

	private Set<String> classNames = new HashSet<String>();

	public ClassNameGatherer() {
		super(ASM5);
	}

	@Override
	public void visit(int version, int access, String name, String signature,
			String superName, String[] interfaces) {
		super.visit(version, access, name, signature, superName, interfaces);

		for (String iface : interfaces) {
			if (iface.equals(Type.getType(ConversionClass.class).getInternalName())) {
				return;
			}
		}

		this.classNames.add(Type.getObjectType(name).getClassName());
	}

	public Set<String> getClassNames() {
		return this.classNames;
	}
}
