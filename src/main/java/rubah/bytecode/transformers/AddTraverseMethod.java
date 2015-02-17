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

import java.lang.ref.Reference;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

import rubah.Rubah;
import rubah.framework.Namespace;
import rubah.framework.Type;
import rubah.runtime.Version;
import sun.misc.Unsafe;

public class AddTraverseMethod extends RubahTransformer {
	private static final String OBJECT_INTERNAL_NAME =
			Type.getType(Object.class).getInternalName();
	private static final int TRAVERSE_METHOD_ACC_FLAGS = ACC_PUBLIC | ACC_SYNTHETIC;
	protected static final String TRAVERSE_METHOD_DESC =
			Type.getMethodDescriptor(Type.VOID_TYPE);
	public static final String TRAVERSE_METHOD_NAME = "$traverse";
	private static final String REGISTER_METHOD_DESC =
			Type.getMethodDescriptor(
					Type.getType(Object.class),
					Type.getType(Object.class),
					Type.getType(java.lang.reflect.Field.class));
	protected static final String REGISTER_METHOD_SIMPLE_DESC =
			Type.getMethodDescriptor(
					Type.getType(Object.class),
					Type.getType(Object.class));
	protected static final String REGISTER_METHOD_NAME = "registerTraversed";
	protected static final String REGISTER_METHOD_OWNER_NAME =
			Type.getType(Rubah.class).getInternalName();
	private static final String[] blackListedPackages = {
		// Reflection and unsafe are used in static class initialization
		// Must be blacklisted to avoid class loading loops
		Method.class.getPackage().getName(),
		Unsafe.class.getName(),
		// NIO charsets do not hold any interesting reference to updatable state
		"java.nio.charset",
		// Throwable's backtrace is something handled by native code, don't touch
		Throwable.class.getName(),
		Rubah.class.getPackage().getName() + ".",
		ThreadLocal.class.getName(),
		Object.class.getPackage().getName(),
		"sun.security",
		"sun.nio.ch.SocketChannelImpl",
		"sun.nio.ch.SocketAdaptor",
		"com.sun.proxy",
	};

	public static boolean isAllowed(String fqn) {

		if (fqn.startsWith(Object.class.getName()) || fqn.startsWith(Class.class.getName()) || fqn.startsWith(Reference.class.getPackage().getName()))
			return true;

		for (String allowedPak : blackListedPackages) {
			if (fqn.startsWith(allowedPak)) {
				return false;
			}
		}

		return true;
	}

	private boolean hasParent;
	private String parentInternalName;
	protected Version version;

	public AddTraverseMethod(HashMap<String, Object> objectsMap, Namespace namespace, ClassVisitor cv) {
		super(objectsMap, namespace, cv);
	}

	public AddTraverseMethod(HashMap<String, Object> objectsMap, Version version, ClassVisitor cv) {
		super(objectsMap, version.getNamespace(), cv);
		this.version = version;
	}

	@Override
	public void visit(int version, int access, String name, String signature,
			String superName, String[] interfaces) {
		this.hasParent = !name.equals(OBJECT_INTERNAL_NAME);
		if (this.hasParent) {
			this.parentInternalName = superName;
		}
		super.visit(version, access, name, signature, superName, interfaces);
	}

	protected boolean isFieldInteresting(int access, Type fieldType) {
		return !Modifier.isStatic(access) &&
				!fieldType.isPrimitive() &&
				(!fieldType.isArray() || !fieldType.getElementType().isPrimitive());
	}

	protected String getTraverseMethodName() {
		return TRAVERSE_METHOD_NAME;
	}

	protected int getTraverseMethodAccess() {
		return TRAVERSE_METHOD_ACC_FLAGS;
	}

	protected void generateTraverseMethodPreamble(MethodVisitor mv) {
		if (this.hasParent) {
			if (!isAllowed(this.parentInternalName.replace('/', '.'))) {
				return;
			}

			mv.visitVarInsn(ALOAD, 0);
			mv.visitMethodInsn(
					INVOKESPECIAL,
					this.parentInternalName,
					TRAVERSE_METHOD_NAME,
					TRAVERSE_METHOD_DESC,
					false);
		}
	}

	protected void getFieldOwner(MethodVisitor mv) {
		mv.visitVarInsn(ALOAD, 0);
	}

}
