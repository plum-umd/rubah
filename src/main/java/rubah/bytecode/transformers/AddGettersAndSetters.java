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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import rubah.Rubah;
import rubah.framework.Clazz;
import rubah.framework.Field;
import rubah.framework.Method;
import rubah.framework.Namespace;
import rubah.framework.Type;
import rubah.runtime.Version;
import sun.misc.Unsafe;

public class AddGettersAndSetters extends RubahTransformer {
	public static final String GETTER_PREFFIX = "$GET$";
	public static final String SETTER_PREFFIX = "$SET$";
	private static final int ACCESS = ACC_PUBLIC | ACC_SYNTHETIC;
	private static final Type UNSAFE_TYPE = Type.getType(Unsafe.class);
	private static final Type STRING_TYPE = Type.getType(String.class);
	private static final Type CLASS_TYPE = Type.getType(Class.class);
	private static final Type FIELD_TYPE = Type.getType(java.lang.reflect.Field.class);
	private static final Type OBJECT_TYPE = Type.getType(Object.class);
	private static final Map<Integer, Invocation> putInvocationMap;

	private static class Invocation {
		final String desc;
		final Type 	 type;

		public Invocation(String desc, Type type) {
			this.desc = desc;
			this.type = type;
		}
	}

	static {
		putInvocationMap = new HashMap<>();
		putInvocationMap.put(org.objectweb.asm.Type.BOOLEAN,
				new Invocation("putBoolean", Type.BOOLEAN_TYPE));
		putInvocationMap.put(org.objectweb.asm.Type.BYTE,
				new Invocation("putByte", Type.BYTE_TYPE));
		putInvocationMap.put(org.objectweb.asm.Type.CHAR,
				new Invocation("putChar", Type.CHAR_TYPE));
		putInvocationMap.put(org.objectweb.asm.Type.DOUBLE,
				new Invocation("putDouble", Type.DOUBLE_TYPE));
		putInvocationMap.put(org.objectweb.asm.Type.FLOAT,
				new Invocation("putFloat", Type.FLOAT_TYPE));
		putInvocationMap.put(org.objectweb.asm.Type.INT,
				new Invocation("putInt", Type.INT_TYPE));
		putInvocationMap.put(org.objectweb.asm.Type.LONG,
				new Invocation("putLong", Type.LONG_TYPE));
		putInvocationMap.put(org.objectweb.asm.Type.SHORT,
				new Invocation("putShort", Type.SHORT_TYPE));
		putInvocationMap.put(org.objectweb.asm.Type.BOOLEAN,
				new Invocation("putBoolean", Type.BOOLEAN_TYPE));
	}

	private HashSet<Field> fields = new HashSet<Field>();
	private HashSet<String> foundGettersSetters = new HashSet<String>();

	private boolean generateInheritedFields = false;

	public static String generateGetterName(Type owner, String fieldName) {
		return generateGetterName(null, owner, fieldName);
	}

	public static String generateGetterName(Version version, Type owner, String fieldName) {
		if (version != null) {
			String name = version.getOriginalName(owner.getClassName());

			if (name != null) {
				owner = Type.getObjectType(name.replace('.', '/'));
				Clazz ownerClass = version.getNamespace().getClass(owner);
				out: while (ownerClass != null) {
					for (Field f : ownerClass.getFields()) {
						if (f.getName().equals(fieldName)) {
							owner = ownerClass.getASMType();
							break out;
						}
					}

					ownerClass = ownerClass.getParent();
				}
			}
		}

		return GETTER_PREFFIX + owner.getClassName().replaceAll("[^a-zA-Z0-9]", "\\$") + "$" + fieldName;
	}

	public static String generateSetterName(Type owner, String fieldName) {
		return generateSetterName(null, owner, fieldName);
	}

	public static String generateSetterName(Version version, Type owner, String fieldName) {
		if (version != null) {
			String name = version.getOriginalName(owner.getClassName());

			if (name != null) {
				owner = Type.getObjectType(name.replace('.', '/'));
				Clazz ownerClass = version.getNamespace().getClass(owner);
				out: while (ownerClass != null) {
					for (Field f : ownerClass.getFields()) {
						if (f.getName().equals(fieldName)) {
							owner = ownerClass.getASMType();
							break out;
						}
					}

					ownerClass = ownerClass.getParent();
				}
			}
		}

		return SETTER_PREFFIX + owner.getClassName().replaceAll("[^a-zA-Z0-9]", "\\$") + "$" + fieldName;
	}

	public AddGettersAndSetters(Namespace namespace, ClassVisitor visitor) {
		super(null, namespace, visitor);
	}

	public AddGettersAndSetters(HashMap<String, Object> objectsMap, Namespace namespace,
			ClassVisitor visitor) {
		super(objectsMap, namespace, visitor);
	}

	@Override
	public void visit(int version, int access, String name, String signature,
			String superName, String[] interfaces) {
		// Ensure that version >= 1.5
		switch (version) {
			case V1_1:
			case V1_2:
			case V1_3:
			case V1_4:
				version = V1_5;
				break;
		}
		super.visit(version, access, name, signature, superName, interfaces);
	}

	@Override
	public FieldVisitor visitField(int access, String name, String desc,
			String signature, Object value) {
		FieldVisitor ret = super.visitField(access, name, desc, signature, value);

		if (this.isInterface) {
			return ret;
		}

		Field field = null;
		if (this.objectsMap != null) {
			field = (Field) this.objectsMap.get(name);
		}

		if (field == null) {
			field = new Field(access, name, this.namespace.getClass(Type.getType(desc)), value != null);
		}
		this.fields.add(field);

		return ret;
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String desc,
			String signature, String[] exceptions) {

		Method m = (this.objectsMap != null ? ((Method)this.objectsMap.get(name)) : null);
		String realName = (m != null ? m.getName() : name);

		if (realName.startsWith(GETTER_PREFFIX) || realName.startsWith(SETTER_PREFFIX)) {
			this.foundGettersSetters.add(realName);
		}

		return super.visitMethod(access, name, desc, signature, exceptions);
	}

	@Override
	public void visitEnd() {

		if (this.thisClass.isInterface())
			return;

		this.generateFields(this.thisClass.getFields(), this.thisClass, true);

		Clazz c = this.thisClass.getParent();

		while (c != null) {
			this.generateFields(c.getFields(), this.thisClass, false);
			if (this.generateInheritedFields)
				this.generateFields(c.getFields(), c, false);
			c = c.getParent();
		}

		super.visitEnd();
	}

	private void generateFields(Set<Field> fields, Clazz owner, boolean generatePrivate) {
		for (Field field : fields) {

			if (field.getName().equals(AddForwardField.FIELD_NAME))
				continue;

			if (!generatePrivate && Modifier.isPrivate(field.getAccess()))
				continue;

			boolean isStatic = Modifier.isStatic(field.getAccess());
			boolean isFinal = Modifier.isFinal(field.getAccess());
			Clazz type = field.getType();
			this.generateGetter(isStatic, field.getName(), owner, type);
			if (isFinal)
				this.generateFinalSetter(isStatic, field.getName(), owner, type);
			else
				this.generateSetter(isStatic, field.getName(), owner, type);
		}
	}

	private void generateGetter(boolean isStatic, String name, Clazz owner, Clazz type) {

		String getterName = generateGetterName(owner.getASMType(), name);

		if (this.foundGettersSetters.contains(getterName))
			return;

		MethodVisitor mv = this.visitMethod(
				(isStatic ? (ACCESS | ACC_STATIC) : ACCESS),
				getterName,
				Type.getMethodDescriptor(type.getASMType()),
				null,
				null);
		mv.visitCode();
		if (isStatic) {
			mv.visitFieldInsn(
					GETSTATIC,
					owner.getASMType().getInternalName(),
					name,
					type.getASMType().getDescriptor());
		} else {
			mv.visitVarInsn(ALOAD, 0);
			mv.visitFieldInsn(
					GETFIELD,
					owner.getASMType().getInternalName(),
					name,
					type.getASMType().getDescriptor());
		}
		mv.visitInsn(type.getASMType().getOpcode(IRETURN));
		mv.visitMaxs(0, 0);
		mv.visitEnd();
	}

	private void generateSetter(boolean isStatic, String name, Clazz owner, Clazz type) {
		String setterName = generateSetterName(owner.getASMType(), name);

		if (this.foundGettersSetters.contains(setterName))
			return;

		MethodVisitor	mv = this.visitMethod(
				(isStatic ? (ACCESS | ACC_STATIC) : ACCESS),
				setterName,
				Type.getMethodDescriptor(Type.VOID_TYPE, type.getASMType()),
				null,
				null);
		mv.visitCode();

		int i = 0;
		if (!isStatic)
			mv.visitVarInsn(ALOAD, i++);

		mv.visitVarInsn(type.getASMType().getOpcode(ILOAD), i);

		mv.visitFieldInsn(
				(isStatic ? PUTSTATIC : PUTFIELD),
				owner.getASMType().getInternalName(),
				name,
				type.getASMType().getDescriptor());

		mv.visitInsn(RETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
	}

	/**
	 * Use unsafe to ensure ability to write to final fields
	 * @param isStatic
	 * @param name
	 * @param type
	 */
	private void generateFinalSetter(boolean isStatic, String name, Clazz owner, Clazz type) {
		String setterName = generateSetterName(owner.getASMType(), name);

		if (this.foundGettersSetters.contains(setterName))
			return;

		int fieldLocal = 1 + type.getASMType().getSize();
		int  unsafeLocal = fieldLocal + 1;

		MethodVisitor	mv = this.visitMethod(
				(isStatic ? (ACCESS | ACC_STATIC) : ACCESS),
				setterName,
				Type.getMethodDescriptor(Type.VOID_TYPE, type.getASMType()),
				null,
				null);
		mv.visitCode();
		Label l0 = new Label();
		Label l1 = new Label();
		Label l2 = new Label();
		mv.visitTryCatchBlock(l0, l1, l2, "java/lang/SecurityException");
		Label l3 = new Label();
		mv.visitTryCatchBlock(l0, l1, l3, "java/lang/NoSuchFieldException");
		mv.visitLabel(l0);
		mv.visitLdcInsn(owner.getASMType().getASMType());
		mv.visitLdcInsn(name);
		mv.visitMethodInsn(
				INVOKEVIRTUAL,
				CLASS_TYPE.getInternalName(),
				"getDeclaredField",
				Type.getMethodDescriptor(FIELD_TYPE, STRING_TYPE),
				false);
		mv.visitVarInsn(ASTORE, fieldLocal);
		mv.visitMethodInsn(
				INVOKESTATIC,
				Type.getType(Rubah.class).getInternalName(),
				"getUnsafe",
				Type.getMethodDescriptor(UNSAFE_TYPE),
				false);
		mv.visitVarInsn(ASTORE, unsafeLocal);
		mv.visitVarInsn(ALOAD, unsafeLocal);


		if (isStatic) {
			mv.visitVarInsn(ALOAD, unsafeLocal);
			mv.visitVarInsn(ALOAD, fieldLocal);
			mv.visitMethodInsn(
					INVOKEVIRTUAL,
					UNSAFE_TYPE.getInternalName(),
					"staticFieldBase",
					"(Ljava/lang/reflect/Field;)Ljava/lang/Object;",
					false);
			mv.visitVarInsn(ALOAD, unsafeLocal);
			mv.visitVarInsn(ALOAD, fieldLocal);
			mv.visitMethodInsn(
					INVOKEVIRTUAL,
					UNSAFE_TYPE.getInternalName(),
					"staticFieldOffset",
					Type.getMethodDescriptor(Type.LONG_TYPE, FIELD_TYPE),
					false);
			mv.visitVarInsn(type.getASMType().getOpcode(ILOAD), 0);
		} else {
			mv.visitVarInsn(ALOAD, 0);
			mv.visitVarInsn(ALOAD, unsafeLocal);
			mv.visitVarInsn(ALOAD, fieldLocal);
			mv.visitMethodInsn(
					INVOKEVIRTUAL,
					UNSAFE_TYPE.getInternalName(),
					"objectFieldOffset",
					Type.getMethodDescriptor(Type.LONG_TYPE, FIELD_TYPE),
					false);
			mv.visitVarInsn(type.getASMType().getOpcode(ILOAD), 1);
		}

		this.generatePutObjectInvocation(mv, type.getASMType());

		mv.visitLabel(l1);
		Label l6 = new Label();
		mv.visitJumpInsn(GOTO, l6);
		mv.visitLabel(l2);
		mv.visitVarInsn(ASTORE, 2);
		mv.visitTypeInsn(NEW, "java/lang/Error");
		mv.visitInsn(DUP);
		mv.visitVarInsn(ALOAD, 2);
		mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Error", "<init>", "(Ljava/lang/Throwable;)V",false);
		mv.visitInsn(ATHROW);
		mv.visitLabel(l3);
		mv.visitVarInsn(ASTORE, 2);
		mv.visitTypeInsn(NEW, "java/lang/Error");
		mv.visitInsn(DUP);
		mv.visitVarInsn(ALOAD, 2);
		mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Error", "<init>", "(Ljava/lang/Throwable;)V",false);
		mv.visitInsn(ATHROW);
		mv.visitLabel(l6);
		mv.visitInsn(RETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
	}

	private void generatePutObjectInvocation(MethodVisitor mv, Type type) {
		Invocation result = putInvocationMap.get(type.getSort());
		if (result == null)
			result = new Invocation("putObject", OBJECT_TYPE);

		mv.visitMethodInsn(
				INVOKEVIRTUAL,
				UNSAFE_TYPE.getInternalName(),
				result.desc,
				Type.getMethodDescriptor(
						Type.VOID_TYPE,
						OBJECT_TYPE,
						Type.LONG_TYPE,
						result.type),
				false);
	}

	public AddGettersAndSetters setGenerateInheritedFields(boolean generateInheritedFields) {
		this.generateInheritedFields = generateInheritedFields;
		return this;
	}
}
