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

import rubah.framework.Clazz;
import rubah.framework.Field;
import rubah.framework.Namespace;
import rubah.framework.Type;

public class AddForwardField extends RubahTransformer {

	public static final int FIELD_MODIFIERS = ACC_PUBLIC;
	public static final String FIELD_NAME = "$forward";
	public static final String FIELD_DESC = Type.getType(Object.class).getDescriptor();
	public static final String CLASS_INFO_FIELD_NAME = "$info";
	public static final String CLASS_INFO_FIELD_DESC = Type.getType(Object.class).getDescriptor();

	private boolean addField = true;

	public AddForwardField(HashMap<String, Object> objectsMap, Namespace namespace, ClassVisitor cv) {
		super(objectsMap, namespace, cv);
	}

	public AddForwardField(Namespace namespace, ClassVisitor visitor) {
		super(null, namespace, visitor);
	}

	@Override
	public FieldVisitor visitField(int access, String name, String desc,
			String signature, Object value) {

		Field f = (this.objectsMap != null ? ((Field)this.objectsMap.get(name)) : null);
		String realName = (f != null ? f.getName() : name);

		if (realName.equals(FIELD_NAME))
			this.addField = false;

		return super.visitField(access, name, desc, signature, value);
	}

	@Override
	public void visitEnd() {
		if (
				// Do not add field to classes directly inside java.lang package
				// For some reason, doing so breaks the profiler support
				(this.thisClass.getFqn().matches("java.lang.[^\\.]*") &&
						// Still, add this field to java.lang.Class
						!this.thisClass.getFqn().equals(Class.class.getName())) ||
				// Do not add field to classes inside java.lang.ref package
				// The GC writes directly to fields without checking the offset
				// This field gets overwritten with stuff that's not an object
				// And crashes the JVM when the program tries to do anything to this field
				// (Except checking if it is null)
			this.thisClass.getFqn().matches("java.lang.ref.*")
				) {
			super.visitEnd();
			return;
		}

		Clazz parent = this.thisClass.getParent();

		if (this.addField &&
				!this.thisClass.isInterface() &&
				parent != null &&
				parent.getFqn().equals(Object.class.getName())) {
			this.visitField(FIELD_MODIFIERS, FIELD_NAME, FIELD_DESC, null, null);
		}

		if (this.thisClass.getFqn().equals(Class.class.getName())) {
			this.visitField(FIELD_MODIFIERS, CLASS_INFO_FIELD_NAME, CLASS_INFO_FIELD_DESC, null, null);
		}

		super.visitEnd();
	}
}
