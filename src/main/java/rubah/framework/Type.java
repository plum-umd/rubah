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
package rubah.framework;

import java.lang.reflect.Constructor;
import java.util.HashSet;
import java.util.Set;

public class Type {

	public static final int OBJECT = org.objectweb.asm.Type.OBJECT;

	public static final Type VOID_TYPE =
			new Type(org.objectweb.asm.Type.VOID_TYPE);
	public static final Type INT_TYPE =
			new Type(org.objectweb.asm.Type.INT_TYPE);
	public static final Type LONG_TYPE =
			new Type(org.objectweb.asm.Type.LONG_TYPE);
	public static final Type CHAR_TYPE =
			new Type(org.objectweb.asm.Type.CHAR_TYPE);
	public static final Type BOOLEAN_TYPE =
			new Type(org.objectweb.asm.Type.BOOLEAN_TYPE);
	public static final Type BYTE_TYPE =
			new Type(org.objectweb.asm.Type.BYTE_TYPE);
	public static final Type DOUBLE_TYPE =
			new Type(org.objectweb.asm.Type.DOUBLE_TYPE);
	public static final Type FLOAT_TYPE =
			new Type(org.objectweb.asm.Type.FLOAT_TYPE);
	public static final Type SHORT_TYPE =
			new Type(org.objectweb.asm.Type.SHORT_TYPE);

	private final static Set<org.objectweb.asm.Type> primitiveTypes;
	public final static Type[] primitives;

	static {
		primitiveTypes = new HashSet<org.objectweb.asm.Type>();
		primitiveTypes.add(org.objectweb.asm.Type.BOOLEAN_TYPE);
		primitiveTypes.add(org.objectweb.asm.Type.BYTE_TYPE);
		primitiveTypes.add(org.objectweb.asm.Type.CHAR_TYPE);
		primitiveTypes.add(org.objectweb.asm.Type.DOUBLE_TYPE);
		primitiveTypes.add(org.objectweb.asm.Type.FLOAT_TYPE);
		primitiveTypes.add(org.objectweb.asm.Type.INT_TYPE);
		primitiveTypes.add(org.objectweb.asm.Type.LONG_TYPE);
		primitiveTypes.add(org.objectweb.asm.Type.SHORT_TYPE);
		primitiveTypes.add(org.objectweb.asm.Type.VOID_TYPE);
		
		primitives = new Type[] {
				BOOLEAN_TYPE,
				BYTE_TYPE,
				CHAR_TYPE,
				DOUBLE_TYPE,
				FLOAT_TYPE,
				INT_TYPE,
				LONG_TYPE,
				SHORT_TYPE,
		};
	}

	private org.objectweb.asm.Type type;

	public Type(org.objectweb.asm.Type type) {
		this.type = type;
	}

	public int getSort() {
		return this.type.getSort();
	}


	public int getDimensions() {
		return this.type.getDimensions();
	}

	public Type getElementType() {
		return new Type(this.type.getElementType());
	}


	public String getClassName() {
		return this.type.getClassName();
	}


	public String getInternalName() {
		return this.type.getInternalName();
	}

	private static org.objectweb.asm.Type[] convertArray(Type[] types) {
		org.objectweb.asm.Type[] ret = new org.objectweb.asm.Type[types.length];

		for (int i = 0; i < types.length; i++) {
			Type type = types[i];
			ret[i] = type.type;
		}

		return ret;
	}

	private static Type[] convertArray(org.objectweb.asm.Type[] types) {
		Type[] ret = new Type[types.length];

		for (int i = 0; i < types.length; i++) {
			org.objectweb.asm.Type type = types[i];
			ret[i] = new Type(type);
		}

		return ret;
	}

	public Type[] getArgumentTypes() {
		return convertArray(this.type.getArgumentTypes());
	}

	public Type getReturnType() {
		return new Type(this.type.getReturnType());
	}


	public int getArgumentsAndReturnSizes() {
		return this.type.getArgumentsAndReturnSizes();
	}


	public String getDescriptor() {
		return this.type.getDescriptor();
	}


	public int getSize() {
		return this.type.getSize();
	}


	public int getOpcode(int opcode) {
		return this.type.getOpcode(opcode);
	}


	@Override
	public boolean equals(Object o) {
		if (o instanceof Type) {
			Type t = (Type) o;
			return this.type.equals(t.type);
		} else if (o instanceof org.objectweb.asm.Type) {
			return this.type.equals(o);
		}
		return false;
	}


	@Override
	public int hashCode() {
		return this.type.hashCode();
	}


	@Override
	public String toString() {
		return this.type.toString();
	}

	public static Type getType(Class<?> c) {
		return new Type(org.objectweb.asm.Type.getType(c));
	}

	public static String getMethodDescriptor(Type returnType, Type ... args) {
		return org.objectweb.asm.Type.getMethodDescriptor(
				returnType.type,
				convertArray(args));
	}

	public static String getMethodDescriptor(java.lang.reflect.Method m) {
		return org.objectweb.asm.Type.getMethodDescriptor(m);
	}

	public static String getMethodDescriptor(Constructor<?> c) {
		return org.objectweb.asm.Type.getConstructorDescriptor(c);
	}

	public static Type getObjectType(String internalName) {
		return new Type(org.objectweb.asm.Type.getObjectType(internalName));
	}

	public static Type getType(String desc) {
		return new Type(org.objectweb.asm.Type.getType(desc));
	}

	public static Type getReturnType(String desc) {
		return new Type(org.objectweb.asm.Type.getReturnType(desc));
	}

	public static Type[] getArgumentTypes(String desc) {
		return convertArray(org.objectweb.asm.Type.getArgumentTypes(desc));
	}

	// Custom methods

	public boolean isPrimitive() {
		return primitiveTypes.contains(this.type);
	}

	public boolean isArray() {
		return this.type.getSort() == org.objectweb.asm.Type.ARRAY;
	}

	public org.objectweb.asm.Type getASMType() {
		return this.type;
	}

	public String getPackageName() {
		return this.type.getClassName().replaceAll("\\.[^.]*$", "");
	}

	public Type createArrayType(int dimensions) {
		String descriptor = this.getDescriptor();

		for (int i = 0 ; i < dimensions ; i++) {
			descriptor = "[" + descriptor;
		}

		return Type.getType(descriptor);
	}
}
