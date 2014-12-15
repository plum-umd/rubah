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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import rubah.bytecode.transformers.ClassNameGatherer;
import rubah.bytecode.transformers.DecreaseClassMethodsProtection;
import rubah.bytecode.transformers.UpdatableClassInfoGatherer;
import rubah.framework.Clazz;
import rubah.framework.DelegatingNamespace;
import rubah.framework.Field;
import rubah.framework.Method;
import rubah.framework.Namespace;
import rubah.framework.Type;
import rubah.runtime.Version;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.converters.FileConverter;

public class UpdatableJarAnalyzer extends ReadWriteTool implements Opcodes {
	private static class ArgParser {
		@Parameter(
				converter=FileConverter.class,
				description="Input jar file",
				names={"-i","--in-jar"},
				required=true)
		private File injar;

		@Parameter(
				converter=FileConverter.class,
				description="Output file",
				required=true,
				names={"-o","--out"})
		private File outFile;

		@Parameter(
				description="Updatable packages (empty for all classes in jar)",
				required=false,
				variableArity=true,
				names={"-p","--packages"})
		private List<String> packages = new LinkedList<String>();
	}

	private List<String> packages;

	private Namespace namespace = new Namespace();
	private ClassVisitor visitor;

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

		UpdatableJarAnalyzer analyzer = new UpdatableJarAnalyzer();
		analyzer.inFile = parser.injar;
		analyzer.outFile = parser.outFile;
		analyzer.packages = new LinkedList<String>();

		for (String pack : parser.packages) {
			analyzer.packages.add(pack.replace('.', File.separatorChar));
		}

		analyzer.processJar();
	}


	@Override
	protected void foundClassFile(String name, InputStream inputStream)
			throws IOException {

		boolean process = true;

		if (!this.packages.isEmpty()) {
			process = false;

			for (String pack : this.packages) {
				if (name.startsWith(pack)) {
					process = true;
					break;
				}
			}
		}

		if (!process) {
			return;
		}

		new ClassReader(inputStream).accept(this.visitor, 0);
	}

	@Override
	public void processJar() throws IOException {
		ClassNameGatherer nameGatherer = new ClassNameGatherer();
		this.visitor = nameGatherer;
		super.processJar();

		Namespace newNamespace =
				new DelegatingNamespace(this.namespace, nameGatherer.getClassNames());

		Version version = new Version(newNamespace);

		UpdatableClassInfoGatherer infoGatherer = new UpdatableClassInfoGatherer(version);

		this.visitor = new DecreaseClassMethodsProtection(infoGatherer);
		super.processJar();

		this.writeOutFile(newNamespace);
	}

	private void writeOutFile(Namespace namespace) throws IOException {
		ObjectOutputStream outStream =
				new ObjectOutputStream(new FileOutputStream(this.outFile));

		AnalysisData data = new AnalysisData();
		for (Clazz c : namespace.getDefinedClasses())
			data.updatableClasses.add(new ClassData(c));

		outStream.writeObject(data);

		outStream.close();
	}

	public static Namespace readFile(File inJar, Namespace namespace) throws FileNotFoundException, IOException {
		return readFile(IOUtils.toByteArray(new FileInputStream(inJar)), namespace);
	}

	public static Namespace readFile(byte[] updateDescriptor, Namespace namespace)
			throws IOException {
		ObjectInputStream outStream =
				new ObjectInputStream(new ByteArrayInputStream(updateDescriptor));

		Set<Clazz> classes = new HashSet<Clazz>();
		AnalysisData data = null;
		try {
			data = (AnalysisData) outStream.readObject();
		} catch (ClassNotFoundException e) {
			throw new Error(e);
		} finally {
			outStream.close();
		}

		HashSet<String> classNames = new HashSet<String>();

		for (ClassData cd : data.updatableClasses)
			classNames.add(Type.getType(cd.fqn).getClassName());

		Namespace newNamespace =
				new DelegatingNamespace(namespace, classNames);


		for (ClassData cd : data.updatableClasses)
			classes.add(cd.toClass(newNamespace));

		return newNamespace;
	}

	public static class ClassData implements Serializable {
		private static final long serialVersionUID = 1722351260872124919L;

		private String			fqn;
		private String 			parentFqn;
		private boolean			isInterface;
		private FieldData[] 	fields;
		private MethodData[]	methods;
		private String[]		ifaces;

		public ClassData(Clazz c) {
			this.fqn = c.getASMType().getDescriptor();
			this.isInterface = c.isInterface();
			if (c.getParent() != null) {
				this.parentFqn = c.getParent().getASMType().getDescriptor();
			}

			this.ifaces = new String[c.getInterfaces().size()];
			int i = 0;
			for (Clazz iface : c.getInterfaces())
				this.ifaces[i++] = iface.getASMType().getDescriptor();

			this.fields = new FieldData[c.getFields().size()];
			i = 0;
			for (Field f : c.getFields())
				this.fields[i++] = new FieldData(f);

			this.methods = new MethodData[c.getMethods().size()];
			i = 0;
			for (Method m : c.getMethods())
				methods[i++] = new MethodData(m);
		}

		public Clazz toClass(Namespace namespace) {
			Clazz ret = namespace.getClass(Type.getType(this.fqn), true);
			ret.setInterface(this.isInterface);
			if (this.parentFqn != null) {
				ret.setParent(namespace.getClass(Type.getType(this.parentFqn)));
			}
			for (String iface : this.ifaces) {
				ret.getInterfaces().add(namespace.getClass(Type.getType(iface)));
			}
			for (FieldData fd : this.fields) {
				Field f = new Field(
						fd.access,
						fd.name,
						namespace.getClass(Type.getType(fd.desc)),
						fd.constant);
				ret.getFields().add(f);
			}
			for (MethodData md : this.methods) {
				List<Clazz> args = new LinkedList<Clazz>();
				for (String argFqn : md.argsDesc) {
					args.add(namespace.getClass(Type.getType(argFqn)));
				}

				Method m = new Method(
						md.access,
						md.name,
						namespace.getClass(Type.getType(md.retDesc)),
						args);

				m.setBodyMD5(md.bodyMD5);

				ret.addMethod(m);
			}

			return ret;
		}
	}

	public static class FieldData implements Serializable {
		private static final long serialVersionUID = 6269593623533725416L;

		private int 	access;
		private String 	name;
		private String 	desc;
		private boolean constant;

		public FieldData(Field f) {
			this.access 	= f.getAccess();
			this.name		= f.getName();
			this.desc		= f.getType().getASMType().getDescriptor();
			this.constant	= f.isConstant();
		}
	}

	public static class MethodData implements Serializable {
		private static final long serialVersionUID = 3802620430701092039L;

		private int 		access;
		private String		name;
		private	String		retDesc;
		private	String[]	argsDesc;
		private String		bodyMD5;

		public MethodData(Method m) {
			String[] args = new String[m.getArgTypes().size()];

			int i = 0;
			for (Clazz arg : m.getArgTypes())
				args[i++] = arg.getASMType().getDescriptor();

			this.access		= m.getAccess();
			this.name		= m.getName();
			this.retDesc	= m.getRetType().getASMType().getDescriptor();
			this.argsDesc	= args;
			this.bodyMD5	= m.getBodyMD5();
		}
	}

	private static class AnalysisData implements Serializable {
		private static final long serialVersionUID = -8021445031150193622L;

		private Set<ClassData> updatableClasses = new HashSet<ClassData>();
	}
}
