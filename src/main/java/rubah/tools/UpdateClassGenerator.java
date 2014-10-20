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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.apache.commons.io.IOUtils;
import org.javatuples.Pair;
import org.objectweb.asm.Opcodes;

import rubah.bytecode.transformers.AddHashCodeField;
import rubah.framework.Clazz;
import rubah.framework.Field;
import rubah.framework.Namespace;
import rubah.framework.Type;
import rubah.runtime.Version;
import rubah.update.change.Change;
import rubah.update.change.ChangeSet;
import rubah.update.change.ChangeType;
import rubah.update.change.ClassChange;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.converters.BooleanConverter;
import com.beust.jcommander.converters.FileConverter;

import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapper;
import freemarker.template.Template;
import freemarker.template.TemplateException;


public class UpdateClassGenerator implements Opcodes {

	public static final String V0_PREFFIX = "v0";
	public static final String V1_PREFFIX = "v1";
	public static final String METHOD_NAME = "convert";
	public static final String METHOD_NAME_STATIC = "convertStatic";
	public static final String COPY_METHOD_NAME_STATIC = "copyFields";

	private static class ArgParser {
		@Parameter(
				converter=FileConverter.class,
				description="Previous version descriptor",
				names={"-v0"},
				required=true)
		private File v0Descriptor;

		@Parameter(
				converter=FileConverter.class,
				description="New version descriptor",
				names={"-v1"},
				required=true)
		private File v1Descriptor;

		@Parameter(
				converter=FileConverter.class,
				description="File where to write the update class Java source",
				names={"-u", "--update-class"},
				required=true)
		private File outJavaFile;

		@Parameter(
				description="Package of the generated Java source file",
				names={"-p", "--package"})
		private String outJavaPackage;

		@Parameter(
				converter=FileConverter.class,
				description="Jar file where to writhe the conversion classes",
				names={"-o","--out"},
				required=true)
		private File outJar;

		@Parameter(
				converter=BooleanConverter.class,
				description="Flag that makes this tool generate conversion code for unchanged classes",
				names={"-a","--all"})
		private boolean allClasses = false;
	}

	private final static Map<Type, String> primitiveNullValues;

	static {
		primitiveNullValues = new HashMap<Type, String>();
		primitiveNullValues.put(Type.BOOLEAN_TYPE, "false");
		primitiveNullValues.put(Type.BYTE_TYPE, "(byte) 0");
		primitiveNullValues.put(Type.CHAR_TYPE, "\0");
		primitiveNullValues.put(Type.DOUBLE_TYPE, "0.0");
		primitiveNullValues.put(Type.FLOAT_TYPE, "0");
		primitiveNullValues.put(Type.INT_TYPE, "0");
		primitiveNullValues.put(Type.LONG_TYPE, "0L");
		primitiveNullValues.put(Type.SHORT_TYPE, "0");
	}

	private boolean allClasses;
	private Namespace defaultNamespace = new Namespace();
	private Version v0, v1;
	private Map<Clazz, ClassChange> changes;
	private Map<Namespace, String> preffixes =  new HashMap<Namespace, String>();

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


		UpdateClassGenerator ucg =
				new UpdateClassGenerator(parser.v0Descriptor, parser.v1Descriptor, parser.allClasses);

		ucg.generateConversionJar(parser.outJar);

		ucg.generateUpdateClass(parser.outJavaFile, parser.outJavaPackage);

	}

	public UpdateClassGenerator(File v0Descriptor, File v1Descriptor, boolean allClasses) throws IOException {
		this.allClasses = allClasses;

		this.v0 = new Version(0, UpdatableJarAnalyzer.readFile(v0Descriptor, this.defaultNamespace), null);
		this.v1 = new Version(1, UpdatableJarAnalyzer.readFile(v1Descriptor, this.defaultNamespace), this.v0);

		this.preffixes.put(this.v0.getNamespace(), V0_PREFFIX);
		this.preffixes.put(this.v1.getNamespace(), V1_PREFFIX);

		this.changes = new Comparator(this.v0, this.v1).computeChanges(false);
	}

	public void generateUpdateClass(File outJavaFile, String outJavaPackage) throws IOException {

		String template =
				new String(IOUtils.toCharArray(UpdateClassGenerator.class.getResourceAsStream("/UpdateClass.template.java")));

		Configuration cfg = new Configuration();
		StringTemplateLoader loader = new StringTemplateLoader();
		loader.putTemplate("template", template);
		cfg.setTemplateLoader(loader);
		cfg.setObjectWrapper(new DefaultObjectWrapper());

		Template temp = cfg.getTemplate("template");

		FileWriter fw = new FileWriter(outJavaFile);
		BufferedWriter bw = new BufferedWriter(fw);

		try {
			temp.process(this.buildTemplateModel(outJavaPackage), bw);
		} catch (TemplateException e) {
			throw new Error(e);
		}

		bw.close();
		fw.close();
	}

	private Map<String, Object> buildTemplateModel(String outJavaPackage) {
		Map<String, Object> templateModel = new HashMap<String, Object>();

		templateModel.put("package", outJavaPackage);
		templateModel.put("namespace", this.defaultNamespace);
		templateModel.put("modifier", new Modifier());
		templateModel.put("helper", this);
		templateModel.put("convertName", METHOD_NAME);
		templateModel.put("convertStaticName", METHOD_NAME_STATIC);
		templateModel.put("copyStaticFieldsMethodName", COPY_METHOD_NAME_STATIC);

		HashSet<ChangesTemplateModel> detectedChanges = new HashSet<ChangesTemplateModel>();
		for (Entry<Clazz, ClassChange> change : this.changes.entrySet()) {

			if (change.getValue().getChangeSet().hasChange(ChangeType.NEW_CLASS)) {
				continue;
			}

			ChangesTemplateModel ctm = new ChangesTemplateModel(
					this.addPreffixToFQN(change.getValue().getOriginal()),
					this.addPreffixToFQN(change.getKey()));

			if (this.allClasses) {
				ctm.hasStaticChanges = true;
				ctm.hasNonStaticChanges = true;
			}

			for (Entry<Field, Change<Field>> fieldChange : change.getValue().getFieldChanges().entrySet()) {

				if (this.isHashCode(fieldChange)) {
					continue;
				}

				ChangeSet changeSet = fieldChange.getValue().getChangeSet();

				if (changeSet.hasChange(ChangeType.NEW_FIELD)) {
					if (Modifier.isStatic(fieldChange.getKey().getAccess())) {
						ctm.hasStaticChanges = true;
					} else {
						ctm.hasNonStaticChanges = true;
					}
					ctm.newFields.add(new Pair<Field, Change<Field>>(fieldChange.getKey(), fieldChange.getValue()));
				} else if (changeSet.hasChange(ChangeType.NEW_CONSTANT)) {
					ctm.hasStaticChanges = true;
					ctm.newConstants.add(new Pair<Field, Change<Field>>(fieldChange.getKey(), fieldChange.getValue()));
				} else if (changeSet.hasChange(ChangeType.FIELD_TYPE_CHANGE)) {
					if (Modifier.isStatic(fieldChange.getKey().getAccess())) {
						ctm.hasStaticChanges = true;
					} else {
						ctm.hasNonStaticChanges = true;
					}
					ctm.typeChangedFields.add(new Pair<Field, Change<Field>>(fieldChange.getKey(), fieldChange.getValue()));
				} else {
					ctm.unmodifiedFields.add(new Pair<Field, Change<Field>>(fieldChange.getKey(), fieldChange.getValue()));
				}
			}

			if (ctm.hasNonStaticChanges || ctm.hasStaticChanges) {
				detectedChanges.add(ctm);
			}
		}

		templateModel.put("classes", detectedChanges);

		return templateModel;
	}

	private boolean isHashCode(Entry<Field, Change<Field>> fieldChange) {
		return
				fieldChange.getKey().getName().equals(AddHashCodeField.FIELD_NAME) ||
				(
						fieldChange.getValue().getOriginal() != null &&
						fieldChange.getValue().getOriginal().getName().equals(AddHashCodeField.FIELD_NAME));
	}

	public String addPreffixToFQNIgnoreArrays(Clazz c) {

		if (c.isArray()) {
			c = c.getNamespace().getClass(c.getASMType().getElementType());
		}

		String preffix = this.preffixes.get(c.getNamespace());

		if (preffix != null) {
			return preffix + "." + c.getFqn();
		}

		return c.getFqn();
	}

	public String getNullValue(Clazz c) {
		if (c.getASMType().isPrimitive()) {
			return primitiveNullValues.get(c.getASMType());
		} else {
			return "null";
		}
	}


	private String addPreffixToFQN(Clazz c) {
		String preffix = this.preffixes.get(c.getNamespace());

		if (preffix != null) {
			return preffix + "." + c.getFqn();
		}

		return c.getFqn();
	}

	public void generateConversionJar(File outJar) throws IOException {

		FileOutputStream fos = new FileOutputStream(outJar);
		JarOutputStream jos = new JarOutputStream(fos, new Manifest());

		this.generateConversionClasses(jos, this.v0, this.preffixes.get(this.v1.getNamespace()));
		this.generateConversionClasses(jos, this.v1, "");

		jos.close();
		fos.close();
	}

	private void generateConversionClasses(JarOutputStream jos, Version v, String newPreffix) throws IOException {

		ConversionClassGenerator ccg =
				new ConversionClassGenerator(v.getNamespace(), this.preffixes.get(v.getNamespace()), newPreffix);

		for (Clazz cl : v.getNamespace().getDefinedClasses()) {
			ccg.addConversionClassesToJar(jos, cl);
		}
	}

	public static class SortedChanges {
	}

	public static class ChangesTemplateModel {
		private final String v0, v1;
		private boolean hasStaticChanges = false;
		private boolean hasNonStaticChanges = false;
		public final Set<Pair<Field, Change<Field>>> unmodifiedFields = new HashSet<Pair<Field,Change<Field>>>();
		public final Set<Pair<Field, Change<Field>>> newFields = new HashSet<Pair<Field,Change<Field>>>();
		public final Set<Pair<Field, Change<Field>>> newConstants = new HashSet<Pair<Field,Change<Field>>>();
		public final Set<Pair<Field, Change<Field>>> typeChangedFields = new HashSet<Pair<Field,Change<Field>>>();

		public ChangesTemplateModel(String v0, String v1) {
			this.v0 = v0;
			this.v1 = v1;
		}

		public String getV0() {
			return this.v0;
		}

		public String getV1() {
			return this.v1;
		}

		public boolean isHasStaticChanges() {
			return this.hasStaticChanges;
		}

		public void setHasStaticChanges(boolean hasStaticChanges) {
			this.hasStaticChanges = hasStaticChanges;
		}

		public boolean isHasNonStaticChanges() {
			return this.hasNonStaticChanges;
		}

		public void setHasNonStaticChanges(boolean hasNonStaticChanges) {
			this.hasNonStaticChanges = hasNonStaticChanges;
		}

		public Set<Pair<Field, Change<Field>>> getUnmodifiedFields() {
			return this.unmodifiedFields;
		}

		public Set<Pair<Field, Change<Field>>> getNewFields() {
			return this.newFields;
		}

		public Set<Pair<Field, Change<Field>>> getNewConstants() {
			return this.newConstants;
		}

		public Set<Pair<Field, Change<Field>>> getTypeChangedFields() {
			return this.typeChangedFields;
		}
	}
}
