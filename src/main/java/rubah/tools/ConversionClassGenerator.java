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
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.apache.commons.io.IOUtils;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import rubah.framework.Clazz;
import rubah.framework.Field;
import rubah.framework.Method;
import rubah.framework.Namespace;
import rubah.framework.Type;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.converters.FileConverter;


public class ConversionClassGenerator implements Opcodes {
	private static final int CLASS_VERSION = Opcodes.V1_5;
	private static final int CLASS_ACCESS = ACC_PUBLIC;
	private static final int FIELD_ACCESS = ACC_PUBLIC;
	private static final int FIELD_STATIC_ACCESS = ACC_STATIC | ACC_PUBLIC;
	private static final int CONVERT_METHOD_ACCESS = ACC_PUBLIC | ACC_ABSTRACT;
	private static final int CONVERT_ARRAY_METHOD_ACCESS = ACC_PUBLIC | ACC_STATIC | ACC_NATIVE;
	private static final int COPY_METHOD_ACCESS_STATIC = ACC_PUBLIC | ACC_STATIC | ACC_NATIVE;

	private static class ArgParser {
		@Parameter(
				converter=FileConverter.class,
				description="Previous version descriptor",
				names={"-d","--descriptor"},
				required=true)
		private File versionDescriptor;

		@Parameter(
				description="Package preffix",
				names={"-p","--preffix"},
				required=true)
		private String preffix;

		@Parameter(
				converter=FileConverter.class,
				description="Out jar file",
				names={"-o","--out"},
				required=true)
		private File outJar;
	}

	private Namespace namespace;
	private String preffix;
	private String newPreffix = "";

	public static void main(String[] args) throws IOException {

		ArgParser parser = new ArgParser();

		JCommander argParser = new JCommander(parser);
		try {
			argParser.parse(args);
		} catch (ParameterException e) {
			System.out.println(e.getMessage());
			argParser.usage();
			System.exit(1);
		}

		ConversionClassGenerator c =
				new ConversionClassGenerator(
						UpdatableJarAnalyzer.readFile(parser.versionDescriptor, new Namespace()),
						parser.preffix
						);

		c.generateConversionClasses(parser.outJar);
	}

	public ConversionClassGenerator(Namespace namespace, String preffix) {
		this.namespace = namespace;
		this.preffix = preffix;
	}

	public ConversionClassGenerator(Namespace namespace, String preffix, String newPreffix) {
		this.namespace = namespace;
		this.preffix = preffix;
		this.newPreffix = newPreffix;
	}

	public void generateConversionClasses(File outJar) throws IOException {

		FileOutputStream fos = new FileOutputStream(outJar);
		JarOutputStream jos = new JarOutputStream(fos, new Manifest());

		for (Clazz cl : this.namespace.getDefinedClasses()) {
			this.addConversionClassesToJar(jos, cl);
		}

		jos.close();
		fos.close();

	}

	public void addConversionClassesToJar(JarOutputStream jos, Clazz cl) throws IOException {
		jos.putNextEntry(new JarEntry(this.getType(cl).getInternalName() + ".class"));
		IOUtils.write(this.getClassBytes(cl), jos);
	}

	private byte[] getClassBytes(Clazz cl) {
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);

		String[] interfaces = new String[cl.getInterfaces().size()];

		{
			int i = 0;
			for (Clazz iface : cl.getInterfaces()) {
				interfaces[i++] = this.getType(iface).getInternalName();
			}
		}

		writer.visit(
				CLASS_VERSION,
				CLASS_ACCESS | (cl.isInterface() ? Modifier.INTERFACE : 0),
				this.getType(cl).getInternalName(),
				null,
				this.getType(cl.getParent()).getInternalName(),
				interfaces);

		for (Field f : cl.getFields()) {
			writer.visitField(
					Modifier.isStatic(f.getAccess()) ? FIELD_STATIC_ACCESS : FIELD_ACCESS,
					f.getName(),
					this.getType(f.getType()).getDescriptor(),
					null,
					null);
		}

		for (Method m : cl.getMethods()) {

			Type ret = this.getType(m.getRetType());
			Type[] args = new Type[m.getArgTypes().size()];

			{
				int i = 0;
				for (Clazz arg : m.getArgTypes()) {
					args[i++] = this.getType(arg);
				}
			}

			writer.visitMethod(
					(Modifier.isStatic(m.getAccess()) ? FIELD_STATIC_ACCESS : FIELD_ACCESS) | ACC_ABSTRACT,
					m.getName(),
					Type.getMethodDescriptor(ret, args),
					null,
					null);
		}

		writer.visitMethod(
				CONVERT_METHOD_ACCESS,
				UpdateClassGenerator.METHOD_NAME,
				Type.getMethodDescriptor(this.getType(cl, this.newPreffix)),
				null,
				null);

		writer.visitMethod(
				CONVERT_METHOD_ACCESS,
				UpdateClassGenerator.METHOD_NAME,
				Type.getMethodDescriptor(
						this.getType(cl, this.newPreffix),
						this.getType(cl, this.newPreffix)),
				null,
				null);

		if (this.newPreffix.equals("")) {
			writer.visitMethod(
					COPY_METHOD_ACCESS_STATIC,
					UpdateClassGenerator.COPY_METHOD_NAME_STATIC,
					Type.getMethodDescriptor(Type.VOID_TYPE),
					null,
					null);
		}

		Clazz arrayCl = this.namespace.getClass(cl.getASMType(), 1);

		writer.visitMethod(
				CONVERT_ARRAY_METHOD_ACCESS,
				UpdateClassGenerator.METHOD_NAME,
				Type.getMethodDescriptor(
						this.getType(arrayCl, this.newPreffix),
						this.getType(arrayCl)),
				null,
				null);

		writer.visitEnd();

		return writer.toByteArray();
	}

	private Type getType(Clazz cl) {
		return this.getType(cl, this.preffix);
	}

	private Type getType(Clazz cl, String preffix) {
		Type ret = cl.getASMType();

		if (cl.getNamespace().equals(this.namespace)) {
			if (cl.isArray()) {
				StringBuffer typeDesc = new StringBuffer();

				for (int i = 0 ; i < ret.getDimensions() ; i++) {
					typeDesc.append('[');
				}

				typeDesc.append("L");
				typeDesc.append(preffix.equals("") ? "" : preffix + "/");
				typeDesc.append(ret.getElementType().getInternalName());
				typeDesc.append(";");
				return Type.getType(typeDesc.toString());
			} else {
				return Type.getObjectType((preffix.equals("") ? "" : preffix + "/") + ret.getInternalName());
			}
		} else {
			return ret;
		}
	}
}
