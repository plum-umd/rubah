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

import rubah.framework.Method;
import rubah.framework.Namespace;
import rubah.framework.Type;
import rubah.runtime.RubahReflection;

public class ReflectionRewritter extends RubahTransformer {
	protected final HashMap<MethodInvocation, MethodInvocation> translations = new HashMap<>();

	public ReflectionRewritter(HashMap<String, Object> objectsMap, Namespace namespace, ClassVisitor cv) {
		super(objectsMap, namespace, cv);
		this.setTranslations();
	}

	protected void setTranslations() {
		Type reflectionType = Type.getType(RubahReflection.class);
		Type classType = Type.getType(Class.class);
		Type stringType = Type.getType(String.class);
		Type objectType = Type.getType(Object.class);

		this.translations.put(
				new MethodInvocation(classType, "forName", classType, stringType),
				new MethodInvocation(reflectionType, "forName", classType, stringType));

		this.translations.put(
				new MethodInvocation(objectType, "wait", Type.VOID_TYPE),
				new MethodInvocation(reflectionType, "wait", Type.VOID_TYPE, objectType).setOpcode(INVOKESTATIC));

		this.translations.put(
				new MethodInvocation(objectType, "wait", Type.VOID_TYPE, Type.LONG_TYPE),
				new MethodInvocation(reflectionType, "wait", Type.VOID_TYPE, objectType, Type.LONG_TYPE).setOpcode(INVOKESTATIC));

		this.translations.put(
				new MethodInvocation(objectType, "wait", Type.VOID_TYPE, Type.LONG_TYPE, Type.INT_TYPE),
				new MethodInvocation(reflectionType, "wait", Type.VOID_TYPE, objectType, Type.LONG_TYPE, Type.INT_TYPE).setOpcode(INVOKESTATIC));

		this.translations.put(
				new MethodInvocation(objectType, "notify", Type.VOID_TYPE),
				new MethodInvocation(reflectionType, "notify", Type.VOID_TYPE, objectType).setOpcode(INVOKESTATIC));

		this.translations.put(
				new MethodInvocation(objectType, "notifyAll", Type.VOID_TYPE),
				new MethodInvocation(reflectionType, "notifyAll", Type.VOID_TYPE, objectType).setOpcode(INVOKESTATIC));
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String desc,
			String signature, String[] exceptions) {
		return new MethodReflectionRewritter(
				super.visitMethod(access, name, desc, signature, exceptions));
	}

	protected static class MethodInvocation {
		Integer opcode;
		Type owner;
		String name;
		String desc;

		public MethodInvocation(Type owner, String name, String desc) {
			this.owner = owner;
			this.name = name;
			this.desc = desc;
		}

		public MethodInvocation(Type owner, String name, Type retType, Type ... argTypes) {
			this.owner = owner;
			this.name = name;
			this.desc = Type.getMethodDescriptor(retType, argTypes);
		}

		public MethodInvocation setOpcode(int opcode) {
			this.opcode = opcode;
			return this;
		}

		@Override
		public int hashCode() {
			return this.owner.hashCode() ^ this.name.hashCode() ^ this.desc.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof MethodInvocation) {
				MethodInvocation other = (MethodInvocation) obj;
				return this.owner.equals(other.owner) && this.name.equals(other.name) && this.desc.equals(other.desc);
			}

			return false;
		}

		@Override
		public String toString() {
			return "MethodInvocation [owner=" + this.owner + ", name=" + this.name + ", desc=" + this.desc + "]";
		}

	}

	private class MethodReflectionRewritter extends MethodVisitor {

		public MethodReflectionRewritter(MethodVisitor mv) {
			super(ASM5, mv);
		}

		@Override
		public void visitMethodInsn(int opcode, String owner, String name,
				String desc, boolean itf) {

			Method m = null;

			if (objectsMap != null)
				m =(Method) ReflectionRewritter.this.objectsMap.get(name);

			String realName = name;
			if (m != null) {
				realName = m.getName();
			}

			MethodInvocation invocation =
					new MethodInvocation(Type.getObjectType(owner), realName, desc);

			invocation = translations.get(invocation);

			if (invocation != null) {
				owner = invocation.owner.getInternalName();
				name = invocation.name;
				desc = invocation.desc;
				if (invocation.opcode != null)
					opcode = invocation.opcode;
			}

			super.visitMethodInsn(opcode, owner, name, desc, itf);
		}


	}
}
