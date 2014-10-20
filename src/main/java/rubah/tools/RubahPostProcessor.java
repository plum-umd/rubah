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
package rubah.tools;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import rubah.Rubah;
import rubah.framework.Type;
import rubah.tools.RubahTool.Parameters;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.converters.FileConverter;

public class RubahPostProcessor extends ReadWriteTool implements Opcodes {
	public static final String TOOL_NAME = "postprocessor";
	private static final String TO_PACKAGE = Rubah.class.getPackage().getName();
	private static final String WHERE_PACKAGE = Rubah.class.getPackage().getName();
	private static final Set<String> FROM_PACKAGES;

	static {
		FROM_PACKAGES = new HashSet<String>();
		addParents(FROM_PACKAGES, HashSet.class, HashSet.class.getPackage());
		addParents(FROM_PACKAGES, HashMap.class, HashSet.class.getPackage());

		System.out.println(FROM_PACKAGES);
	}

	private static void addParents(Set<String> set, Class<?> start, Package limit) {

		Class<?> parent = start;

		do {
			set.add(parent.getName());
			for (Class<?> inner : parent.getDeclaredClasses()) {
				addParents(set, inner, limit);
			}
		}
		while ((parent = parent.getSuperclass()).getPackage().equals(limit));
	}

	public static class PostProcessorParameters extends RubahTool.Parameters {
		@Parameter(
				converter=FileConverter.class,
				description="Bootstrap jar",
				required=true,
				names={"-b","--bootstrap"})
		protected File bootstrapJar;
	}

	@Override
	public void processJar() throws IOException {
		this.outFile = File.createTempFile("rubahtmp", ".jar");
		super.processJar();

		this.inFile.delete();
		this.outFile.renameTo(this.inFile);
	}

	@Override
	protected Parameters getParameters() {
		this.parameters = new PostProcessorParameters();
		return this.parameters;
	}

	@Override
	protected void endProcess() throws IOException {
		ReadTool read = new ReadTool(((PostProcessorParameters)this.parameters).bootstrapJar) {
			@Override
			protected void foundClassFile(String name, InputStream inputStream) throws IOException {
				Type t = Type.getObjectType(name.replaceAll("\\.class$", ""));

				if (FROM_PACKAGES.contains(t.getClassName())) {
					ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
					new ClassReader(inputStream).accept(new PackageRewriterClassVisitor(writer), 0);

					addFileToOutJar(registerType(t).getInternalName() + ".class", writer.toByteArray());
				}
			}
		};

		read.processJar();
	}

	private Type registerType(Type type) {

		if (type.isArray()) {
			if (FROM_PACKAGES.contains(type.getClassName())) {
				return registerType(type.getElementType()).createArrayType(type.getDimensions());
			}
		}

		if (FROM_PACKAGES.contains(type.getClassName())) {
			return Type.getObjectType(TO_PACKAGE.replace('.', '/') + "/" + type.getInternalName());
		}

		return type;
	}

	private String registerInternal(String internalName) {
		return registerType(Type.getObjectType(internalName)).getInternalName();
	}

	private String registerMethod(String methodDesc) {
		Type ret = this.registerType(Type.getReturnType(methodDesc));

		Type args[] = Type.getArgumentTypes(methodDesc);
		for (int i = 0; i < args.length; i++) {
			args[i] = registerType(args[i]);
		}

		return Type.getMethodDescriptor(ret, args);

	}

	@Override
	protected void foundClassFile(String name, InputStream inputStream) throws IOException {

		if (!name.replace('/', '.').startsWith(WHERE_PACKAGE)) {
			super.foundClassFile(name, inputStream);
			return;
		}

		ClassReader reader = new ClassReader(inputStream);
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		ClassVisitor visitor = writer;

		visitor = new PackageRewriterClassVisitor(visitor);

		reader.accept(visitor, 0);
		this.addFileToOutJar(name, writer.toByteArray());
	}

	private class PackageRewriterClassVisitor extends ClassVisitor {


		public PackageRewriterClassVisitor(ClassVisitor cv) {
			super(ASM5, cv);
		}

		@Override
		public void visit(int version, int access, String name,
				String signature, String superName, String[] interfaces) {

			name = registerInternal (name);
			superName = registerInternal(superName);
			if (interfaces != null) {
				for (int i = 0; i < interfaces.length; i++)
					interfaces[i] = registerInternal(interfaces[i]);
			}

			super.visit(version, access, name, signature, superName, interfaces);
		}

		@Override
		public FieldVisitor visitField(int access, String name,
				String desc, String signature, Object value) {

			desc = registerType(Type.getType(desc)).getDescriptor();

			return super.visitField(access, name, desc, signature, value);
		}

		@Override
		public MethodVisitor visitMethod(int access, String name,
				String desc, String signature, String[] exceptions) {

			String newDesc = registerMethod(desc);
			desc = newDesc;
			if (exceptions != null)
				for (int i = 0; i < exceptions.length; i++)
					exceptions[i] = registerInternal(exceptions[i]);

			MethodVisitor ret = super.visitMethod(access, name, desc, signature, exceptions);


			ret = new MethodVisitor(ASM5, ret) {
				@Override
				public void visitTypeInsn(int opcode, String type) {
					type = registerInternal(type);
					super.visitTypeInsn(opcode, type);
				}

				@Override
				public void visitFieldInsn(int opcode, String owner,
						String name, String desc) {
					owner = registerInternal(owner);
					desc = registerType(Type.getType(desc)).getDescriptor();
					super.visitFieldInsn(opcode, owner, name, desc);
				}

				@Override
				public void visitMethodInsn(int opcode, String owner,
						String name, String desc, boolean itf) {

					owner = registerInternal(owner);
					desc = registerMethod(desc);

					super.visitMethodInsn(opcode, owner, name, desc, itf);
				}

				@Override
				public void visitTryCatchBlock(Label start, Label end,
						Label handler, String type) {
					if (type != null)
						type = registerInternal(type);
					super.visitTryCatchBlock(start, end, handler, type);
				}
			};

			return ret;
		}
	}
}
