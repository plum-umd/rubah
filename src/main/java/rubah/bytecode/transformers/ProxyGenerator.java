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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.LocalVariablesSorter;

import rubah.Rubah;
import rubah.bytecode.RubahProxy;
import rubah.framework.Clazz;
import rubah.framework.Method;
import rubah.framework.Type;
import rubah.runtime.Version;
import rubah.runtime.VersionManager;
import rubah.update.UpdateClass;

public class ProxyGenerator implements Opcodes {

	private static final String PROXY_SUFFIX = "__PROXY";
	public static final String PROXIED_OBJ_NAME = "proxied";
	public static final Type PROXIED_OBJ_TYPE = Type.getType(Object.class);
	public static final String BASE_OBJ_NAME = "base";
	public static final Type BASE_OBJ_TYPE = Type.getType(Object.class);
	public static final String OFFSET_NAME = "offset";
	public static final Type OFFSET_TYPE = Type.INT_TYPE;
	public static final String SCALE_NAME = "scale";
	public static final Type SCALE_TYPE = Type.INT_TYPE;
	private static final int CLASS_ACCESS = ACC_PUBLIC | ACC_FINAL;
	private static final int METHOD_ACCESS = ACC_PUBLIC | ACC_SYNTHETIC | ACC_FINAL;
	private static final Type RUBAH_TYPE = Type.getType(Rubah.class);
	private static final Type RUBAH_PROXY_TYPE = Type.getType(RubahProxy.class);
	private static final Type OBJECT_TYPE = Type.getType(Object.class);

	protected Clazz thisClass;
	protected Version version;

	public ProxyGenerator(Clazz c, Version v) {
		this.thisClass = c;
		this.version = v;
	}

	public static String generateProxyName(String className) {
		return className + PROXY_SUFFIX;
	}

	public static String getOriginalName(String className) {
		return className.substring(0, className.length() - PROXY_SUFFIX.length());
	}

	public static boolean isProxyName(String className) {
		return className.endsWith(PROXY_SUFFIX);
	}

	public byte[] generateProxy() {
		return this.generateProxy(generateProxyName(this.thisClass.getFqn()));
	}

//	private Map<Pair<Clazz, Method>, Integer> computeInheritedOverloads(Map<Pair<Clazz, Method>, Integer> map) {
//
//		LinkedList<Clazz> inheritancePath = new LinkedList<Clazz>();
//
//		for (Clazz c = this.thisClass ; c != null ; c = c.getParent())
//			inheritancePath.addFirst(c);
//
//		Map<Pair<Clazz, Method>, Integer> ret = new HashMap<Pair<Clazz,Method>, Integer>();
//		HashSet<Pair<Method, Integer>> foundOverloads = new HashSet<Pair<Method,Integer>>();
//
//		for (Clazz c : inheritancePath) {
//			for (Method m : c.getMethods()) {
//				Integer overload = map.get(new Pair<Clazz, Method>(c, m));
//				if (overload != null)
//					foundOverloads.add(new Pair<Method, Integer>(m, overload));
//			}
//
//			for (Pair<Method, Integer> overload : foundOverloads)
//				ret.put(new Pair<Clazz, Method>(c, overload.getValue0()), overload.getValue1());
//		}
//
//		return ret;
//	}

	public byte[] generateProxy(String name) {
		final String proxyName = name.replace('.', '/');

		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);

		HashMap<Object, String> namesMap = new HashMap<Object, String>();
		HashMap<String, Object> objectsMap = new HashMap<String, Object>();

//		Map<Pair<Clazz, Method>, Integer> overloads = this.computeInheritedOverloads(this.version.getOverloads());

		ClassVisitor visitor = writer;

		visitor = new ReplaceUniqueByOriginalNames(namesMap, objectsMap, this.version.getNamespace(), visitor);

		visitor = new UpdatableClassRenamer(this.version, objectsMap, visitor);
		visitor = this.getMethodsTransformer(proxyName, this.thisClass, visitor);
		// The placement of this transformer is important
		// because convert methods require special treatment
		// as they operate in between versions
		if (this.version.getPrevious() != null) {
			UpdateClass updateClass = VersionManager.getInstance().getUpdateClass(this.version);
			visitor = new ProcessUpdateClass(
					objectsMap,
					this.version,
					updateClass,
					visitor);
			visitor = new ClassVisitor(ASM5, visitor) {
				@Override
				public void visitEnd() {

					Type t = thisClass.getASMType();
					String updatableName = version.getUpdatableName(t.getClassName());

					if (updatableName != null) {
						Type t1 = Type.getObjectType(updatableName.replace('.', '/'));

						String prevName = version.getPrevious().getUpdatableName(t.getClassName());

						MethodVisitor mv = this.cv.visitMethod(
								ACC_PUBLIC,
								"convert",
								Type.getMethodDescriptor(Type.VOID_TYPE, OBJECT_TYPE, OBJECT_TYPE),
								null,
								null);

						if (prevName != null) {
							Type t0 = Type.getObjectType(version.getPrevious().getUpdatableName(t.getClassName()).replace('.', '/'));

							mv.visitVarInsn(ALOAD, 1);
							mv.visitTypeInsn(CHECKCAST, t0.getInternalName());
							mv.visitVarInsn(ALOAD, 2);
							mv.visitTypeInsn(CHECKCAST, t1.getInternalName());

							mv.visitMethodInsn(
									INVOKESTATIC,
									proxyName,
									ProcessUpdateClass.METHOD_NAME_STATIC,
									Type.getMethodDescriptor(Type.VOID_TYPE, t0, t1),
									false);

							mv.visitInsn(RETURN);

						} else {
							// Class was introduced this version
							mv.visitTypeInsn(NEW, "java/lang/Error");
							mv.visitInsn(DUP);
							mv.visitLdcInsn("Should not execute, converting a class that did not exist in the previous version");
							mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Error", "<init>", "(Ljava/lang/String;)V", false);
							mv.visitInsn(ATHROW);
						}

						mv.visitMaxs(0, 0);
						mv.visitEnd();
					}

					this.cv.visitEnd();
				}
			};
		}
//		visitor = new RenameOverloads(objectsMap, overloads, this.version.getNamespace(), visitor);
//		visitor = new TypeEraser(objectsMap, this.version, visitor);
		visitor = new AddGettersAndSetters(objectsMap, this.version.getNamespace(), visitor).setGenerateInheritedFields(true);
		visitor = new ReplaceOriginalNamesByUnique(namesMap, objectsMap, this.version.getNamespace(), visitor);

		visitor = new OverrideInheritedMethods(this.thisClass, visitor);

		visitor.visit(
				V1_5,
				CLASS_ACCESS,
				this.thisClass.getASMType().getInternalName(),
				null,
				null,
				null);

		visitor.visitEnd();
		return writer.toByteArray();
	}

	protected ClassVisitor getMethodsTransformer(String proxyName, Clazz thisClass2, ClassVisitor visitor) {
		return new TransformProxyMethods(proxyName, this.thisClass, visitor);
	}

	protected static class TransformProxyMethods extends ClassVisitor {

		protected String proxyName;
		protected Clazz owner;

		public TransformProxyMethods(String proxyName, Clazz owner, ClassVisitor cv) {
			super(ASM5, cv);
			this.proxyName = proxyName;
			this.owner = owner;
		}

		@Override
		public void visit(int version, int access, String name,
				String signature, String superName, String[] interfaces) {
			this.cv.visit(
					V1_5,
					CLASS_ACCESS,
					proxyName,
					null,
					owner.getASMType().getInternalName(),
					new String[]{ RUBAH_PROXY_TYPE.getInternalName() });
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String desc,
				String signature, String[] exceptions) {

			if (name.equals(ProcessUpdateClass.METHOD_NAME) || name.equals(ProcessUpdateClass.METHOD_NAME_STATIC))
				return this.cv.visitMethod(access, name, desc, signature, exceptions);

			MethodVisitor ret = new MethodVisitor(ASM5) { };

			if (Modifier.isStatic(access))
				return ret;

			LocalVariablesSorter mv = new LocalVariablesSorter(
					access,
					desc,
					this.cv.visitMethod(METHOD_ACCESS, name, desc, signature, exceptions));

			String ownerName = this.owner.getASMType().getInternalName();

			int newObjectVar = mv.newLocal(OBJECT_TYPE.getASMType());

			mv.visitCode();
			mv.visitVarInsn(ALOAD, 0);
			mv.visitMethodInsn(
					INVOKESTATIC,
					RUBAH_TYPE.getInternalName(),
					"getConverted",
					Type.getMethodDescriptor(OBJECT_TYPE, OBJECT_TYPE),
					false);
			mv.visitTypeInsn(CHECKCAST, ownerName);

			int arg = 1;

			for (Type t : Type.getArgumentTypes(desc)) {
				mv.visitVarInsn(t.getOpcode(ILOAD), arg);
				arg += t.getSize();
			}

			mv.visitMethodInsn(
					INVOKEVIRTUAL,
					ownerName,
					name,
					desc,
					false);
			mv.visitInsn(Type.getReturnType(desc).getOpcode(IRETURN));

			mv.visitMaxs(0, 0);
			mv.visitEnd();

			return ret;
		}

//		@Override
//		public void visitEnd() {
//			// Add extra proxy fields
//			this.cv.visitField(
//					FIELD_ACCESS,
//					BASE_OBJ_NAME,
//					BASE_OBJ_TYPE.getDescriptor(),
//					null,
//					null);
//			this.cv.visitField(
//					FIELD_ACCESS,
//					OFFSET_NAME,
//					OFFSET_TYPE.getDescriptor(),
//					null,
//					null);
//			this.cv.visitField(
//					FIELD_ACCESS,
//					PROXIED_OBJ_NAME,
//					PROXIED_OBJ_TYPE.getDescriptor(),
//					null,
//					null);
//			this.cv.visitField(
//					FIELD_ACCESS,
//					SCALE_NAME,
//					SCALE_TYPE.getDescriptor(),
//					null,
//					null);
//
//			super.visitEnd();
//		}
	}

	private static class OverrideInheritedMethods extends ClassVisitor {

		private static HashSet<String> BLACKLIST = new HashSet<String>(
				Arrays.asList(new String[]{
						"finalize",
						"clone",
						"<init>"
				}));

		private static int DISALLOWED_MASK = Modifier.FINAL | Modifier.PRIVATE | Modifier.STATIC;

		private Clazz thisClass;

		public OverrideInheritedMethods(Clazz thisClass, ClassVisitor cv) {
			super(ASM5, cv);
			this.thisClass = thisClass;
		}

		@Override
		public void visitEnd() {
			HashSet<Method> overrides = new HashSet<Method>();
			Clazz parent = this.thisClass;

			while (parent != null) {
				for (Method m : parent.getMethods()) {
					if (!BLACKLIST.contains(m.getName())
							&& ((m.getAccess() & DISALLOWED_MASK) == 0)
							&& !overrides.contains(m)) {
						this.override(m, parent);
						overrides.add(m);
					}
				}

				parent = parent.getParent();
			}

			super.visitEnd();
		}

		private void override(Method m, Clazz owner) {
			MethodVisitor mv = this.visitMethod(
					m.getAccess(),
					m.getName(),
					m.getASMDesc(),
					null,
					null);

			mv.visitVarInsn(ALOAD, 0);

			int var = 1;

			for (Clazz arg : m.getArgTypes()) {
				mv.visitVarInsn(arg.getASMType().getOpcode(ILOAD), var);
				var += arg.getASMType().getSize();
			}

			mv.visitMethodInsn(
					INVOKESPECIAL,
					owner.getASMType().getInternalName(),
					m.getName(),
					m.getASMDesc(),
					false);

			mv.visitInsn(m.getRetType().getASMType().getOpcode(IRETURN));
			mv.visitMaxs(0, 0);
			mv.visitEnd();
		}
	}
}
