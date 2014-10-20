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

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import rubah.Rubah;
import rubah.framework.Clazz;
import rubah.framework.Field;
import rubah.framework.Type;
import rubah.runtime.RubahRuntime;
import rubah.runtime.Version;
import rubah.runtime.state.migrator.UnsafeUtils;
import rubah.tools.UpdateClassGenerator;
import rubah.update.UpdateClass;
import rubah.update.change.Change;

public class ProcessUpdateClass extends RubahTransformer {
	public static final int METHOD_ACCESS = ACC_PUBLIC | ACC_STATIC;
	public static final int METHOD_ACCESS_STATIC = ACC_PUBLIC | ACC_STATIC;
	public static final String METHOD_NAME = "convert";
	public static final String METHOD_NAME_STATIC = "convert$static";
	private static final Type OBJECT_TYPE = Type.getType(Object.class);
	private static final Type UNSAFE_UTILS_TYPE = Type.getType(UnsafeUtils.class);
	private static final Type UNSAFE_TYPE = Type.getType(sun.misc.Unsafe.class);
	private static final Type CLASS_OFFSETS_TYPE = Type.getType(UnsafeUtils.ClassOffsets.class);
	private static final Type CLASS_TYPE = Type.getType(Class.class);

	protected Version version;
	private Type oldDummyType, newDummyType;
	private Map<String, MethodNode> methodNodeIndex;

	public ProcessUpdateClass(HashMap<String, Object> objectsMap, Version v1, UpdateClass updateClass, ClassVisitor cv) {
		super(objectsMap, v1.getNamespace(), cv);
		this.version = v1;

		if (updateClass == null) {
			this.methodNodeIndex = new HashMap<String, MethodNode>();
			return;
		}

		this.methodNodeIndex = getMethodNodeIndex(updateClass.getBytes());
	}

	public static Map<String, MethodNode> getMethodNodeIndex(byte[] bytecode) {
		Map<String, MethodNode> methodNodeIndex = new HashMap<String, MethodNode>();
		ClassNode classNode = new ClassNode();
		new ClassReader(bytecode).accept(classNode, 0);
		for (Object obj : classNode.methods) {
			MethodNode mn = (MethodNode) obj;
			methodNodeIndex.put(mn.name + mn.desc, mn);
		}

		return methodNodeIndex;
	}

	private static void getUnsafe(MethodVisitor mv) {
		mv.visitMethodInsn(
				INVOKESTATIC,
				UNSAFE_UTILS_TYPE.getInternalName(),
				"getUnsafe",
				Type.getMethodDescriptor(UNSAFE_TYPE),
				false);
	}

	private static void getUnsafeUtils(MethodVisitor mv) {
		mv.visitMethodInsn(
				INVOKESTATIC,
				UNSAFE_UTILS_TYPE.getInternalName(),
				"getInstance",
				Type.getMethodDescriptor(UNSAFE_UTILS_TYPE),
				false);
	}

	private static void getClassOffsets(MethodVisitor mv) {
		mv.visitMethodInsn(
				INVOKEVIRTUAL,
				UNSAFE_UTILS_TYPE.getInternalName(),
				"getOffsets",
				Type.getMethodDescriptor(CLASS_OFFSETS_TYPE, CLASS_TYPE),
				false);
	}

	private static void getStaticBase(MethodVisitor mv) {
		mv.visitMethodInsn(
				INVOKEVIRTUAL,
				CLASS_OFFSETS_TYPE.getInternalName(),
				"getStaticBase",
				Type.getMethodDescriptor(OBJECT_TYPE),
				false);
	}

	@Override
	public void visit(int version, int access, String name, String signature,
			String superName, String[] interfaces) {
		super.visit(version, access, name, signature, superName, interfaces);

		String oldTypeName =
				UpdateClassGenerator.V0_PREFFIX + "." + this.thisClass.getFqn();
		this.oldDummyType = Type.getObjectType(oldTypeName.replace('.', '/'));
		String newTypeName =
				UpdateClassGenerator.V1_PREFFIX + "." + this.thisClass.getFqn();
		this.newDummyType = Type.getObjectType(newTypeName.replace('.', '/'));
	}

	public static void generateConversionMethod(Version v1, Clazz c1, ConvertMethodGenerator[] generators) {
		Clazz c0 = v1.getUpdate().getV0(c1);

		for (ConvertMethodGenerator generator : generators) {
			MethodVisitor mv = generator.getMethodVisitor();

			if (mv == null) {
				continue;
			}

			mv.visitCode();

			if (c1.getParent().getNamespace().equals(c1.getNamespace())) {
				// Convert parent, if updatable
				generator.callSuper(mv, c1.getParent());
			} else {
				// Copy all fields from parent, if non-updatable
				generator.copyAllFields(mv, c1.getParent());
			}

			HashSet<Field> interestingFields = new HashSet<Field>();
			for (Field f : c1.getFields()) {
				Change<Field> change =
						v1.getUpdate().getChanges(c0).getFieldChanges().get(f);
				if (generator.isFieldInteresting(f, c1) && (change == null || change.getChangeSet().isEmpty())) {
					interestingFields.add(f);
					// Copy unchanged fields
					generator.convertField(mv, f);
				}
			}

			MethodNode mn = generator.getMethodNode();

			if (mn != null) {
				// Add custom conversion code to end of conversion method
				mn.instructions.resetLabels();
				mn.accept(new ProcessUpdateMethod(v1, generator, interestingFields, mv));
				continue;
			}

			mv.visitInsn(RETURN);
			mv.visitEnd();
			mv.visitMaxs(0, 0);
		}
	}

	protected boolean isClassInteresting() {
		if (this.isInterface || !this.thisClass.getNamespace().equals(this.namespace)) {
			return false;
		}

		Clazz c0 = this.version.getUpdate().getV0(this.thisClass);

		if (c0 == null) {// || UpdateManager.getInstance().isFirstVersion(this.version.getUpdatableName(c0.getFqn()))) {
			return false;
		}

		return true;
	}

	@Override
	public void visitEnd() {
		if (this.isClassInteresting()) {
			generateConversionMethod(
					this.version,
					this.thisClass,
					this.getGenerators());
		}

		super.visitEnd();
	}

	protected ConvertMethodGenerator[] getGenerators() {
		return new ConvertMethodGenerator[]{
				new NormalConvertMethodGenerator(
						this.oldDummyType,
						this.newDummyType,
						this.version,
						this,
						this.methodNodeIndex),
				new StaticConvertMethodGenerator(
						this.thisClass,
						this.oldDummyType,
						this.newDummyType,
						this.version,
						this,
						this.methodNodeIndex)
		};
	}

	private interface Callback{
		String getClassName(String className);
	}

	private static Type getUpdatableType(Type t, final Version version) {
		return getRealType(t, new Callback() {
			@Override
			public String getClassName(String className) {
				if (className.startsWith(UpdateClassGenerator.V0_PREFFIX)) {
					className = className.replaceFirst(UpdateClassGenerator.V0_PREFFIX + "\\.", "");

					if (version.getPrevious() != null) {
						className = version.getPrevious().getUpdatableName(className);
					} else {
						className = version.getUpdatableName(className);
					}
				} else if (className.startsWith(UpdateClassGenerator.V1_PREFFIX)) {
					className = className.replaceFirst(UpdateClassGenerator.V1_PREFFIX + "\\.", "");
					className = version.getUpdatableName(className);
				}
				return className;
			}
		});
	}

	private static Type getRealType(Type t, Callback c) {

		 Type innerT = (t.isArray() ? t.getElementType() : t);

		if (innerT.isPrimitive()) {
			return t;
		}

		String className = c.getClassName(innerT.getClassName());
		if (className == null) {
			return null;
		}

		className = className.replace('.', '/');

		if (t.isArray()) {
			return Type.getObjectType(className).createArrayType(t.getDimensions());
		}

		return Type.getObjectType(className);
	}

	private static final class UnsafeInfo {
		private final String getterName;
		private final String getterSignature;
		private final String setterName;
		private final String setterSignature;

		public UnsafeInfo(String getterName, String getterSignature,
				String setterName, String setterSignature) {
			this.getterName = getterName;
			this.getterSignature = getterSignature;
			this.setterName = setterName;
			this.setterSignature = setterSignature;
		}
	}

	private static final Map<Type, UnsafeInfo> unsafeInfoMap;

	static {
		unsafeInfoMap = new HashMap<>();

		for (Type t : Type.primitives) {
			String name;

			switch (t.getSort()) {
			case org.objectweb.asm.Type.BOOLEAN:
				name = "Boolean";
				break;
			case org.objectweb.asm.Type.BYTE:
				name = "Byte";
				break;
			case org.objectweb.asm.Type.CHAR:
				name = "Char";
				break;
			case org.objectweb.asm.Type.DOUBLE:
				name = "Double";
				break;
			case org.objectweb.asm.Type.FLOAT:
				name = "Float";
				break;
			case org.objectweb.asm.Type.INT:
				name = "Int";
				break;
			case org.objectweb.asm.Type.LONG:
				name = "Long";
				break;
			case org.objectweb.asm.Type.SHORT:
				name = "Short";
				break;
			default:
				throw new Error("Should not reach here");
			}

			unsafeInfoMap.put(t, new UnsafeInfo(
					"get" + name,
					Type.getMethodDescriptor(t, OBJECT_TYPE, Type.LONG_TYPE),
					"put" + name,
					Type.getMethodDescriptor(Type.VOID_TYPE, OBJECT_TYPE, Type.LONG_TYPE, t)));
		}
	}

	private static void getFieldUsingUnsafe(MethodVisitor mv, Field f) {
		if (f.getType().getASMType().isPrimitive()) {
			UnsafeInfo info = unsafeInfoMap.get(f.getType().getASMType());
			mv.visitMethodInsn(INVOKEVIRTUAL, "sun/misc/Unsafe", info.getterName, info.getterSignature, false);
		} else {
			mv.visitMethodInsn(INVOKEVIRTUAL, "sun/misc/Unsafe", "getObject", "(Ljava/lang/Object;J)Ljava/lang/Object;", false);
		}
	}

	private static void putFieldUsingUnsafe(MethodVisitor mv, Field f) {
		if (f.getType().getASMType().isPrimitive()) {
			UnsafeInfo info = unsafeInfoMap.get(f.getType().getASMType());
			mv.visitMethodInsn(INVOKEVIRTUAL, "sun/misc/Unsafe", info.setterName, info.setterSignature, false);
		} else {
			mv.visitMethodInsn(INVOKEVIRTUAL, "sun/misc/Unsafe", "putObject", "(Ljava/lang/Object;JLjava/lang/Object;)V", false);
		}
	}

	private static long getFieldOffset(Type t, Field f) {
		try {
			Class<?> c = Class.forName(t.getClassName(), false, Rubah.getLoader());

			while (true) {
				try {
					if (c == null)
						return -1;

					if (Modifier.isStatic(f.getAccess()))
						return UnsafeUtils.getUnsafe().staticFieldOffset(c.getDeclaredField(f.getName()));
					else
						return UnsafeUtils.getUnsafe().objectFieldOffset(c.getDeclaredField(f.getName()));
				} catch (NoSuchFieldException e) {
					// Keep going
					c = c.getSuperclass();
				}
			}
		} catch (ReflectiveOperationException | SecurityException e) {
			throw new Error(e);
		}
	}

	public static interface ConvertMethodGenerator {
		public boolean isFieldInteresting(Field f, Clazz owner);

		public void callSuper(MethodVisitor mv, Clazz parent);

		public void copyAllFields(MethodVisitor mv, Clazz parent);

		public MethodNode getMethodNode();

		public MethodVisitor getMethodVisitor();

		public void convertField(MethodVisitor mv, Field f);
	}

	public static class NormalConvertMethodGenerator implements ConvertMethodGenerator {
		protected Type t0, t1;
		private Version version;
		private ClassVisitor cv;
		private Map<String, MethodNode> methodNodeIndex;

		public NormalConvertMethodGenerator(Type t0, Type t1, Version version, ClassVisitor cv, Map<String, MethodNode> methodNodeIndex) {
			this.t0 = t0;
			this.t1 = t1;
			this.version = version;
			this.cv = cv;
			this.methodNodeIndex = methodNodeIndex;
		}

		@Override
		public boolean isFieldInteresting(Field f, Clazz owner) {
			// Copy values from all non-static fields
			return !Modifier.isStatic(f.getAccess()) && !f.getName().equals(AddForwardField.FIELD_NAME);
		}

		@Override
		public MethodVisitor getMethodVisitor() {
			if (this.version.getPrevious() == null) {
				return null;
			}

			Type t0 = getUpdatableType(this.t0, this.version);

			if (t0 == null) {
				// This class was introduced in this update
				return null;
			}

			return this.cv.visitMethod(
				METHOD_ACCESS,
				METHOD_NAME_STATIC,
				Type.getMethodDescriptor(
						Type.VOID_TYPE,
						t0,
						getUpdatableType(this.t1, this.version)),
				null,
				null);
		}

		@Override
		public void convertField(MethodVisitor mv, Field f) {

			Type t0 = getUpdatableType(this.t0, this.version);
			Type t1 = getUpdatableType(this.t1, this.version);

			long offset0 = getFieldOffset(t0, f);
			long offset1 = getFieldOffset(t1, f);

			if (offset0 < 0 || offset1 < 0)
				// Synthetic field, ignore it
				return;

			getUnsafe(mv);
			mv.visitVarInsn(ALOAD, 1);
			mv.visitLdcInsn(offset1);
			getUnsafe(mv);
			mv.visitVarInsn(ALOAD, 0);
			mv.visitLdcInsn(offset0);
			getFieldUsingUnsafe(mv, f);
			putFieldUsingUnsafe(mv, f);


		}

		@Override
		public MethodNode getMethodNode() {
				return this.methodNodeIndex.get(
						UpdateClassGenerator.METHOD_NAME +
						Type.getMethodDescriptor(Type.VOID_TYPE, this.t0, this.t1));
		}

		@Override
		public void callSuper(MethodVisitor mv, Clazz parent) {

			String oldTypeName =
					UpdateClassGenerator.V0_PREFFIX + "." + parent.getFqn();
			Type oldDummyType = getUpdatableType(
					Type.getObjectType(oldTypeName.replace('.', '/')),
					this.version);

			if (oldDummyType == null) {
				// Super is a new class introduced in this update, skip super call
				return;
			}

			Type t1 = Type.getObjectType(this.version.getUpdatableName(parent.getFqn()).replace('.', '/'));
			Type t0 = Type.getObjectType(this.version.getPrevious().getUpdatableName(parent.getFqn()).replace('.', '/'));

//			String newTypeName =
//					UpdateClassGenerator.V1_PREFFIX + "." + parent.getFqn();
//			Type newDummyType = getUpdatableType(
//					Type.getObjectType(newTypeName.replace('.', '/')),
//					this.version);

			mv.visitVarInsn(ALOAD, 0); // o0
			mv.visitVarInsn(ALOAD, 1); // o1

			String parentMethodName;

//			if (oldDummyType.equals(newDummyType)) {
//				parentMethodName = (PureConversionClassLoader.PURE_CONVERSION_PREFFIX + this.version.getNumber()).replace('.', '/');
//			} else {
				parentMethodName = ProxyGenerator.generateProxyName(this.version.getUpdatableName(parent.getFqn())).replace('.', '/');
//			}

			mv.visitMethodInsn(
					INVOKESTATIC,
					parentMethodName,
					METHOD_NAME_STATIC,
					Type.getMethodDescriptor(Type.VOID_TYPE, t0, t1),
					false);
		}

		@Override
		public void copyAllFields(MethodVisitor mv, Clazz c) {

			for ( /* Empty */ ; c != null; c = c.getParent()) {
				for (Field f : c.getFields()) {
					if (this.isFieldInteresting(f, c)) {
						this.convertField(mv, f);
					}
				}
			}

		}
	}

	public static class StaticConvertMethodGenerator implements ConvertMethodGenerator {

		private Clazz ownerClass;
		private Type t0, t1;
		private Version version;
		private ClassVisitor cv;
		private Map<String, MethodNode> methodNodeIndex;

		public StaticConvertMethodGenerator(Clazz ownerClass,Type t0, Type t1, Version version, ClassVisitor cv, Map<String, MethodNode> methodNodeIndex) {
			this.ownerClass = ownerClass;
			this.t0 = t0;
			this.t1 = t1;
			this.version = version;
			this.cv = cv;
			this.methodNodeIndex = methodNodeIndex;
		}

		@Override
		public boolean isFieldInteresting(Field f,Clazz owner) {
			// Copy values from static fields defined in this class
			// BUG ALERT:
			// Copying values from static fields in the superclasses may overwrite
			// values already converted with old values, resulting in program bugs

			if (f.isConstant())
				return false;

			return Modifier.isStatic(f.getAccess()) && owner.equals(this.ownerClass);
		}

		@Override
		public MethodVisitor getMethodVisitor() {
			if (this.version.getPrevious() == null) {
				return null;
			}

			return this.cv.visitMethod(
					METHOD_ACCESS,
					METHOD_NAME_STATIC,
					Type.getMethodDescriptor(
							Type.VOID_TYPE,
							getUpdatableType(this.t0, this.version)),
							null,
							null);
		}

		@Override
		public void convertField(MethodVisitor mv, Field f) {
//			if (!processingConversionMethod)
//				return;

			Type t0 = getUpdatableType(this.t0, this.version);
			Type t1 = getUpdatableType(this.t1, this.version);

			long offset0 = getFieldOffset(t0, f);
			long offset1 = getFieldOffset(t1, f);

			if (offset0 < 0 || offset1 < 0)
				// Synthetic field, ignore
				return;

			getUnsafe(mv);
			getUnsafeUtils(mv);
			mv.visitLdcInsn(t1.getASMType());
			getClassOffsets(mv);
			getStaticBase(mv);
			mv.visitLdcInsn(offset1);
			getUnsafe(mv);
			getUnsafeUtils(mv);
			mv.visitLdcInsn(t0.getASMType());
			getClassOffsets(mv);
			getStaticBase(mv);
			mv.visitLdcInsn(offset0);
			getFieldUsingUnsafe(mv, f);
			putFieldUsingUnsafe(mv, f);
		}

		@Override
		public MethodNode getMethodNode() {
			MethodNode ret = this.methodNodeIndex.get(
						UpdateClassGenerator.METHOD_NAME_STATIC +
						Type.getMethodDescriptor(
								Type.VOID_TYPE,
								this.t1));

			if (ret == null) {
				// Default behavior
				ret = new MethodNode(
						ASM5,
						ACC_PUBLIC,
						UpdateClassGenerator.METHOD_NAME_STATIC,
						Type.getMethodDescriptor(Type.VOID_TYPE, this.t1),
						null,
						null);

				ret.instructions.add(new MethodInsnNode(
						INVOKESTATIC,
						t1.getInternalName(),
						UpdateClassGenerator.COPY_METHOD_NAME_STATIC,
						Type.getMethodDescriptor(Type.VOID_TYPE),
						false));

				ret.instructions.add(new InsnNode(RETURN));
			}

			return ret;
		}

		@Override
		public void callSuper(MethodVisitor mv, Clazz parent) {
			// Empty, super was already loaded (and converted) when this code runs
		}

		@Override
		public void copyAllFields(MethodVisitor mv, Clazz parent) {
			// Empty, super was already loaded (and converted) when this code runs
		}
	}

	private static class ProcessUpdateMethod extends MethodVisitor {

			private Version version;
			private ConvertMethodGenerator generator;
			private Set<Field> interestingFields;

			public ProcessUpdateMethod(Version version, ConvertMethodGenerator generator, Set<Field> interestingFields, MethodVisitor mv) {
				super(ASM5, mv);
				this.version = version;
				this.generator = generator;
				this.interestingFields = interestingFields;
			}

			@Override
			public void visitVarInsn(int opcode, int var) {
				super.visitVarInsn(opcode, var - 1);
			}

			@Override
			public void visitIincInsn(int var, int increment) {
				super.visitIincInsn(var - 1, increment);
			}

			@Override
			public void visitInsn(int opcode) {
				if (opcode == ARRAYLENGTH) {
					mv.visitMethodInsn(INVOKESTATIC, "java/lang/reflect/Array", "getLength", "(Ljava/lang/Object;)I", false);
					return;
				}
				super.visitInsn(opcode);
			}

			@Override
			public void visitLocalVariable(String name, String desc,
					String signature, Label start, Label end, int index) {
				if (index == 0)
					// Skip this
					return;
				super.visitLocalVariable(name, desc, signature, start, end, index - 1);
			}

			@Override
			public void visitFieldInsn(int opcode, String owner, String name,
					String desc) {

				Type ownerType = Type.getObjectType(owner);
				Type realOwnerType = getUpdatableType(ownerType, this.version);

				Version v = this.version;

				if (owner.startsWith(UpdateClassGenerator.V0_PREFFIX)) {
					v = v.getPrevious();
				}

				Type fieldType = Type.getType(desc);
				int access = (opcode == PUTSTATIC || opcode == GETSTATIC) ? Modifier.STATIC : 0;
				Field f = new Field(access, name, v.getNamespace().getClass(fieldType), false);
				long offset = getFieldOffset(realOwnerType, f);

				if (!ownerType.equals(realOwnerType)) {
					Type realFieldType;

					if (fieldType.isPrimitive())
						realFieldType = fieldType;
					else
						realFieldType = getUpdatableType(fieldType, this.version);

					switch (opcode) {
						case GETFIELD:
							getUnsafe(mv);
							mv.visitInsn(SWAP);
							mv.visitLdcInsn(offset);
							getFieldUsingUnsafe(mv, f);
							if (!realFieldType.isPrimitive())
								mv.visitTypeInsn(CHECKCAST, realFieldType.getInternalName());
							return;
						case PUTFIELD:
							if (f.getType().getASMType().getSize() == 2) {
								mv.visitLdcInsn(offset);
								mv.visitMethodInsn(
										INVOKESTATIC,
										Type.getType(UnsafeUtils.class).getInternalName(),
										"write",
										Type.getMethodDescriptor(Type.VOID_TYPE, OBJECT_TYPE, f.getType().getASMType(), Type.LONG_TYPE),
										false);
								return;
							}
							getUnsafe(mv);
							mv.visitInsn(DUP_X2);
							mv.visitInsn(POP);
							mv.visitLdcInsn(offset);
							mv.visitInsn(DUP2_X1);
							mv.visitInsn(POP2);
							putFieldUsingUnsafe(mv, f);
							return;
						case GETSTATIC:
							getUnsafe(mv);
							getUnsafeUtils(mv);
							mv.visitLdcInsn(realOwnerType.getASMType());
							getClassOffsets(mv);
							getStaticBase(mv);
							mv.visitLdcInsn(offset);
							getFieldUsingUnsafe(mv, f);
							if (!realFieldType.isPrimitive())
								mv.visitTypeInsn(CHECKCAST, realFieldType.getInternalName());
							return;
						case PUTSTATIC:
							getUnsafe(mv);
							mv.visitInsn(SWAP);
							getUnsafeUtils(mv);
							mv.visitLdcInsn(realOwnerType.getASMType());
							getClassOffsets(mv);
							getStaticBase(mv);
							mv.visitInsn(SWAP);
							mv.visitLdcInsn(offset);
							mv.visitInsn(DUP2_X1);
							mv.visitInsn(POP2);
							putFieldUsingUnsafe(mv, f);
							return;
						default:
							throw new Error("Unexpected bytecode");
					}
				}

				super.visitFieldInsn(opcode, realOwnerType.getInternalName(), name, fieldType.getDescriptor());
			}

			@Override
			public void visitMethodInsn(int opcode, String owner, String name,
					String desc, boolean itf) {

				Type ownerType = Type.getObjectType(owner);
				Type realOwnerType = getUpdatableType(ownerType, this.version);
				if (name.equals("convert") && !ownerType.equals(realOwnerType)) {
					// Dummy invocation, pop arguments and skip
					for (int i = 0 , n = Type.getArgumentTypes(desc).length ; i < n ; i++) {
						super.visitInsn(POP);
					}
					return;
				}

				if (name.equals(UpdateClassGenerator.COPY_METHOD_NAME_STATIC) && opcode == INVOKESTATIC) {
					// Copy all unchanged fields here
					for (Field f : this.interestingFields) {
							// Copy unchanged field
							generator.convertField(mv, f);
					}

					return;
				}

				if (opcode == INVOKESTATIC) {
					// Ensure static fields of class were already converted, if class was present in the prev version
					Type prevOwnerType = getUpdatableType(ownerType, version.getPrevious());

					mv.visitLdcInsn(prevOwnerType.getASMType());
					mv.visitMethodInsn(INVOKESTATIC, Type.getType(RubahRuntime.class).getInternalName(), "ensureStaticFieldsMigrated", "(Ljava/lang/Class;)V", false);
				}


				Type retType = Type.getReturnType(desc);
				Type[] argTypes = Type.getArgumentTypes(desc);

				for (int i = 0; i < argTypes.length; i++) {
					argTypes[i] = getUpdatableType(argTypes[i], this.version);
				}

				retType = getUpdatableType(retType, this.version);

				desc = Type.getMethodDescriptor(retType, argTypes);

				super.visitMethodInsn(opcode, realOwnerType.getInternalName(), name, desc, itf);

			}

			@Override
			public void visitCode() {
				// Already in the middle of a method at this point, skip
			}

			@Override
			public void visitTypeInsn(int opcode, String typeName) {
				Type type = Type.getObjectType(typeName);
				Type realType = getUpdatableType(type, this.version);

				super.visitTypeInsn(opcode, realType.getInternalName());
			}

			@Override
			public void visitLdcInsn(Object cst) {

				if (cst instanceof org.objectweb.asm.Type) {
					Type t = new Type((org.objectweb.asm.Type)cst);
					if (t.getSort() == Type.OBJECT)
						cst = getUpdatableType(t, this.version).getASMType();
				}

				super.visitLdcInsn(cst);
			}
		}
}
