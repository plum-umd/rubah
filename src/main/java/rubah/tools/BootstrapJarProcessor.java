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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashSet;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import rubah.bytecode.transformers.AddForwardField;
import rubah.bytecode.transformers.AddGettersAndSetters;
import rubah.bytecode.transformers.AddHashCodeField;
import rubah.bytecode.transformers.AddHashCodeMethod;
import rubah.bytecode.transformers.AddTraverseMethod;
import rubah.bytecode.transformers.BasicClassInfoGatherer;
import rubah.bytecode.transformers.DecreaseClassMethodsProtection;
import rubah.bytecode.transformers.ProxyGenerator;
import rubah.bytecode.transformers.RedirectFieldManipulation;
import rubah.bytecode.transformers.RubahTransformer;
import rubah.framework.Clazz;
import rubah.framework.DelegatingNamespace;
import rubah.framework.Field;
import rubah.framework.Namespace;
import rubah.framework.Type;
import rubah.runtime.Version;
import rubah.tools.UpdatableJarAnalyzer.ClassData;

public class BootstrapJarProcessor extends ReadWriteTool implements Opcodes {
	public static final String TOOL_NAME = "bootstraper";
	private static final String META_INFO_FILE = "metainfo.bin";

	private Namespace namespace = new Namespace();

	@SuppressWarnings("unchecked")
	public static void readBootstrapMetaInfo(Namespace namespace) throws IOException {
		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		ObjectInputStream inStream =
				new ObjectInputStream(cl.getResourceAsStream(META_INFO_FILE));

		HashSet<ClassData> bootstrapClasses;
		try {
			bootstrapClasses = (HashSet<ClassData>) inStream.readObject();
		} catch (ClassNotFoundException e) {
			throw new Error(e);
		}

		for (ClassData cd : bootstrapClasses) {
			cd.toClass(namespace);
		}
	}

	private State state;

	@Override
	public void processJar() throws IOException {
		this.state = new BuildingMetaData();
		super.processJar();
		this.state = new TransformingClasses();
		super.processJar();
	}

	@Override
	protected void foundClassFile(String name, InputStream inputStream) throws IOException {
		this.state.foundClassFile(name, inputStream);
	}

	@Override
	protected void endProcess() throws IOException {
		this.state.endProcess();
	}

	private static interface State {
		public void foundClassFile(String name, InputStream inputStream) throws IOException;

		public void endProcess() throws IOException;
	}

	private class BuildingMetaData implements State {
		@Override
		public void foundClassFile(String name, InputStream inputStream)
				throws IOException {
			ClassVisitor visitor = new BasicClassInfoGatherer(namespace);
			visitor = new DecreaseClassMethodsProtection(visitor);
			new ClassReader(inputStream).accept(visitor, ClassReader.SKIP_FRAMES);
		}

		@Override
		public void endProcess() throws IOException {
			// Empty
		}
	}

	private class TransformingClasses implements State {

		@Override
		public void foundClassFile(String name, InputStream inputStream)
				throws IOException {

			System.out.println(name);

			if (name.startsWith("sun/security"))
				return;

			ClassReader reader = new ClassReader(inputStream);
			ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			ClassVisitor visitor = writer;

			visitor = new AddHashCodeMethod(namespace, visitor);
			visitor = new AddForwardField(namespace, visitor);
			visitor = new AddGettersAndSetters(namespace, visitor);
			visitor = new AddHashCodeField(namespace, visitor);
	//		visitor = new AddStaticTraverseMethod(null, this.namespace, visitor);
	//		visitor = new AddTraverseMethod(null, this.namespace, visitor);

			visitor = new RubahTransformer(null, namespace, visitor){
				@Override
				public void visit(int version, int access, String name,
						String signature, String superName, String[] interfaces) {
					super.visit(version, access, name, signature, superName, interfaces, false);

					// Add $hashCode to java.lang.Class
					if (this.thisClass.getFqn().equals(Class.class.getName())) {
						this.thisClass.getFields().add(new Field(
								AddHashCodeField.FIELD_MODIFIERS,
								AddHashCodeField.FIELD_NAME,
								this.namespace.getClass(Type.INT_TYPE),
								false));
					}

					this.cv.visit(version, access, name, signature, superName, interfaces);
				}
			};
			visitor = new DecreaseClassMethodsProtection(visitor);
			visitor = new RedirectFieldManipulation(namespace, visitor);

			reader.accept(visitor, ClassReader.EXPAND_FRAMES);
			addFileToOutJar(name, writer.toByteArray());
		}

		@Override
		public void endProcess() throws IOException {
			ByteArrayOutputStream innerStream = new ByteArrayOutputStream();
			ObjectOutputStream outStream = new ObjectOutputStream(innerStream);

			HashSet<ClassData> bootstrapClasses = new HashSet<ClassData>();

			for (Clazz c : new HashSet<Clazz>(namespace.getAllClasses())) {
				if (c.getASMType().isPrimitive() || c.isArray())
					continue;

				bootstrapClasses.add(new ClassData(c));

				if (AddTraverseMethod.isAllowed(c.getFqn())) {
//					String frontierName = FrontierClassGenerator.generateFrontierName(c.getFqn());
//
//					// Generate frontier classes a-priori because namespaces are protected
//					System.out.println(frontierName);
//					addFileToOutJar(
//							frontierName.replace('.', '/') + ".class",
//							new FrontierClassGenerator(c, new Version(new DelegatingNamespace(namespace, new HashSet<String>()))).generateProxy());

					String proxyName = ProxyGenerator.generateProxyName(c.getFqn());

					Clazz p = namespace.getClass(Type.getObjectType(proxyName.replace(".", "/")));
					p.setParent(c);
					bootstrapClasses.add(new ClassData(p));

					System.out.println(proxyName);
					// Generate proxies a-priori because namespaces are protected
					addFileToOutJar(
							proxyName.replace('.', '/') + ".class",
							new ProxyGenerator(c, new Version(new DelegatingNamespace(namespace, new HashSet<String>()))).generateProxy());
				}
			}

			outStream.writeObject(bootstrapClasses);

			foundResource(META_INFO_FILE,
					new ByteArrayInputStream(innerStream.toByteArray()));
		}
	}


}
