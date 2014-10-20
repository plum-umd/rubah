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
package rubah.runtime.classloader;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodNode;

import rubah.bytecode.transformers.ProcessUpdateClass;
import rubah.framework.Clazz;
import rubah.framework.Type;
import rubah.runtime.Version;
import rubah.tools.UpdateClassGenerator;
import rubah.update.ProgramUpdate;
import rubah.update.UpdateClass;


public class PureConversionClassLoader extends DefaultClassLoader implements Opcodes {
	public static final String PURE_CONVERSION_PREFFIX = "conversion.Conversion";
	private static final String CONSTRUCTOR_NAME = "<init>";
	private static final String OBJECT_INTERNAL_NAME =
			Type.getType(Object.class).getInternalName();

	private Version version;
	private Map<String, MethodNode> methodNodeIndex;

	public PureConversionClassLoader(Version version, UpdateClass updateClass) {
		super(version.getNamespace(), new TransformerFactory());
		this.version = version;
		this.methodNodeIndex = ProcessUpdateClass.getMethodNodeIndex(updateClass.getBytes());
	}

	@Override
	public byte[] getClass(String className) throws IOException {
		if (!className.equals(PURE_CONVERSION_PREFFIX + this.version.getNumber())) {
			return null;
		}

		return this.generatePureConversionClass(className);
	}

	private static boolean isPureConversion(Clazz c0, ProgramUpdate update) {
		return update.isConverted(c0) && 	// is converted
				!update.isUpdated(c0);				// but not updated
	}

	private static boolean isUpdatedWithUnchangedSuper(Clazz c0, ProgramUpdate update) {
		return update.isUpdated(c0) &&	// is updated
				c0.getParent().getNamespace().equals(c0.getNamespace()) && // parent is updatable
				!update.isUpdated(c0.getParent()); // but not updated
	}

	private byte[] generatePureConversionClass(String className) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(
				V1_5,
				ACC_PUBLIC,
				className.replace('.', '/'),
				null,
				OBJECT_INTERNAL_NAME, null);

		MethodVisitor mv = cw.visitMethod(
				ACC_PRIVATE,
				CONSTRUCTOR_NAME,
				Type.getMethodDescriptor(Type.VOID_TYPE),
				null,
				null);
		mv.visitCode();
		mv.visitVarInsn(ALOAD, 0);
		mv.visitMethodInsn(
				INVOKESPECIAL,
				OBJECT_INTERNAL_NAME,
				CONSTRUCTOR_NAME,
				Type.getMethodDescriptor(Type.VOID_TYPE),
				false);
		mv.visitInsn(RETURN);
		mv.visitMaxs(1, 1);
		mv.visitEnd();

		Set<Clazz> conversionMethodsToGenerate = new HashSet<Clazz>();

		ProgramUpdate update = this.version.getUpdate();
		for (Clazz c0 : this.version.getPrevious().getNamespace().getDefinedClasses()) {
			Clazz c1 = update.getV1(c0);

			if (c1 == null) {
				continue;
			}

			if (isPureConversion(c0, update)) {
				conversionMethodsToGenerate.add(c0);
			} else if (isUpdatedWithUnchangedSuper(c0, update)) {

				Clazz tmp = c0.getParent();

				while (tmp.getNamespace().equals(c0.getNamespace())) {
					conversionMethodsToGenerate.add(tmp);
					tmp = tmp.getParent();
				}

			}
		}

		for (Clazz c0 : conversionMethodsToGenerate) {
			Clazz c1 = update.getV1(c0);

			String oldTypeName =
					UpdateClassGenerator.V0_PREFFIX + "." + c0.getFqn();
			Type oldDummyType = Type.getObjectType(oldTypeName.replace('.', '/'));
			String newTypeName =
					UpdateClassGenerator.V1_PREFFIX + "." + c0.getFqn();
			Type newDummyType = Type.getObjectType(newTypeName.replace('.', '/'));

			ProcessUpdateClass.generateConversionMethod(
					this.version,
					c1,
					new ProcessUpdateClass.ConvertMethodGenerator[]{
							new ProcessUpdateClass.NormalConvertMethodGenerator(
									oldDummyType,
									newDummyType,
									this.version,
									cw,
									this.methodNodeIndex),
							new ProcessUpdateClass.StaticConvertMethodGenerator(
									c1,
									oldDummyType,
									newDummyType,
									this.version,
									cw,
									this.methodNodeIndex)});
		}

		cw.visitEnd();

		return cw.toByteArray();
	}

	@Override
	protected String getOriginalClassName(String className) {
		return className;
	}

	@Override
	protected void analyzeClass(byte[] classBytes) {
		// No need to analyze overload helper classes
	}

}
