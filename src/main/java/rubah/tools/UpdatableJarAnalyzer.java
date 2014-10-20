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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.javatuples.Pair;
import org.javatuples.Quartet;
import org.javatuples.Quintet;
import org.javatuples.Sextet;
import org.javatuples.Tuple;
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

		infoGatherer.computeOverloads();
		this.writeOutFile(
				new VersionDescriptor(
						newNamespace,
						infoGatherer.getOverloads()));
	}

	private void writeOutFile(VersionDescriptor descriptor) throws IOException {
		ObjectOutputStream outStream =
				new ObjectOutputStream(new FileOutputStream(this.outFile));

		AnalysisData data = new AnalysisData();
		for (Clazz c : descriptor.namespace.getDefinedClasses()) {
			data.updatableClasses.add(new ClassData(c));
		}

		for (Entry<Pair<Clazz, Method>, Integer> entry : descriptor.overloads.entrySet()) {
			data.overloads.put(
					new Pair<ClassData, MethodData>(
							new ClassData(entry.getKey().getValue0()),
							new MethodData(entry.getKey().getValue1())),
					entry.getValue());
		}

		outStream.writeObject(data);

		outStream.close();
	}

	public static class VersionDescriptor {
		public final Namespace namespace;
		public final Map<Pair<Clazz, Method>, Integer> overloads;

		public VersionDescriptor(Namespace namespace,
				Map<Pair<Clazz, Method>, Integer> overloads) {
			this.namespace = namespace;
			this.overloads = overloads;
		}
	}

	public static VersionDescriptor readFile(File inJar, Namespace namespace) throws FileNotFoundException, IOException {
		return readFile(IOUtils.toByteArray(new FileInputStream(inJar)), namespace);
	}

	public static VersionDescriptor readFile(byte[] updateDescriptor, Namespace namespace)
			throws IOException {
		ObjectInputStream outStream =
				new ObjectInputStream(new ByteArrayInputStream(updateDescriptor));

		Set<Clazz> classes = new HashSet<Clazz>();
		Map<Pair<Clazz, Method>, Integer> overloads =
				new HashMap<Pair<Clazz,Method>, Integer>();
		AnalysisData data = null;
		try {
			data = (AnalysisData) outStream.readObject();
		} catch (ClassNotFoundException e) {
			throw new Error(e);
		} finally {
			outStream.close();
		}

		HashSet<String> classNames = new HashSet<String>();

		for (ClassData cd : data.updatableClasses) {
			classNames.add(Type.getType(cd.tuple.getValue0()).getClassName());
		}

		Namespace newNamespace =
				new DelegatingNamespace(namespace, classNames);


		for (ClassData cd : data.updatableClasses) {
			Clazz c = cd.toClass(newNamespace);
			for (MethodData md : cd.tuple.getValue3()) {
				List<Clazz> args = new LinkedList<Clazz>();
				for (String argFqn : md.tuple.getValue3()) {
					args.add(newNamespace.getClass(Type.getType(argFqn)));
				}

				Method m = new Method(
						md.tuple.getValue0(),
						md.tuple.getValue1(),
						newNamespace.getClass(Type.getType(md.tuple.getValue2())), args);

				Integer overload =
						data.overloads.get(new Pair<ClassData, MethodData>(cd, md));

				if (overload != null) {
					overloads.put(new Pair<Clazz, Method>(c, m), overload);
				}
			}

			classes.add(c);
		}

		return new VersionDescriptor(newNamespace, overloads);
	}

	private static abstract class ElementData<T extends Tuple> implements Serializable {
		protected T tuple;

		@Override
		public int hashCode() {
			return this.tuple.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof ElementData) {
				return this.tuple.equals(((ElementData) obj).tuple);
			}
			return false;
		}

		@Override
		public String toString() {
			return this.tuple.toString();
		}
	}

	public static class ClassData
			extends ElementData<Sextet<String, String, Set<FieldData>, Set<MethodData>, Boolean, Set<String>>> {

		public ClassData(Clazz c) {
			String fqn = c.getASMType().getDescriptor();
			String parentFqn = null;
			if (c.getParent() != null) {
				parentFqn = c.getParent().getASMType().getDescriptor();
			}

			Set<String> interfaces = new HashSet<String>();
			for (Clazz iface : c.getInterfaces()) {
				interfaces.add(iface.getASMType().getDescriptor());
			}

			Set<FieldData> fields = new HashSet<FieldData>();
			for (Field f : c.getFields()) {
				fields.add(new FieldData(f));
			}

			Set<MethodData> methods = new HashSet<MethodData>();
			for (Method m : c.getMethods()) {
				methods.add(new MethodData(m));
			}

			this.tuple =
					new Sextet<String, String, Set<FieldData>, Set<MethodData>, Boolean, Set<String>>(
							fqn, parentFqn, fields, methods, c.isInterface(), interfaces);
		}

		public Clazz toClass(Namespace namespace) {
			Clazz ret = namespace.getClass(Type.getType(this.tuple.getValue0()), true);
			ret.setInterface(this.tuple.getValue4());
			if (this.tuple.getValue1() != null) {
				ret.setParent(namespace.getClass(Type.getType(this.tuple.getValue1())));
			}
			for (String iface : this.tuple.getValue5()) {
				ret.getInterfaces().add(namespace.getClass(Type.getType(iface)));
			}
			for (FieldData fd : this.tuple.getValue2()) {
				Field f = new Field(
						fd.tuple.getValue0(),
						fd.tuple.getValue1(),
						namespace.getClass(Type.getType(fd.tuple.getValue2())),
						fd.tuple.getValue3());
				ret.getFields().add(f);
			}
			for (MethodData md : this.tuple.getValue3()) {
				List<Clazz> args = new LinkedList<Clazz>();
				for (String argFqn : md.tuple.getValue3()) {
					args.add(namespace.getClass(Type.getType(argFqn)));
				}

				Method m = new Method(
						md.tuple.getValue0(),
						md.tuple.getValue1(),
						namespace.getClass(Type.getType(md.tuple.getValue2())), args);

				m.setBodyMD5(md.tuple.getValue4());

				ret.addMethod(m);
			}

			return ret;
		}
	}

	public static class FieldData extends ElementData<Quartet<Integer, String, String, Boolean>> {
		public FieldData(Field f) {
			this.tuple = new Quartet<Integer, String, String, Boolean>(
					f.getAccess(), f.getName(), f.getType().getASMType().getDescriptor(), f.isConstant());
		}
	}

	public static class MethodData extends ElementData<Quintet<Integer, String, String, List<String>, String>> {
		public MethodData(Method m) {
			List<String> args = new LinkedList<String>();

			for (Clazz arg : m.getArgTypes()) {
				args.add(arg.getASMType().getDescriptor());
			}

			this.tuple = new Quintet<Integer, String, String, List<String>, String>(
					m.getAccess(),
					m.getName(),
					m.getRetType().getASMType().getDescriptor(),
					args,
					m.getBodyMD5());
		}
	}

	private static class AnalysisData implements Serializable {
		private Set<ClassData> updatableClasses = new HashSet<ClassData>();
		private Map<Pair<ClassData, MethodData>, Integer> overloads =
				new HashMap<Pair<ClassData,MethodData>, Integer>();
	}
}
