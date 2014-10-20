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
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import rubah.framework.Clazz;
import rubah.framework.Field;
import rubah.framework.Method;
import rubah.framework.Namespace;
import rubah.runtime.Version;
import rubah.update.change.Change;
import rubah.update.change.ChangeSet;
import rubah.update.change.ChangeType;
import rubah.update.change.ClassChange;
import rubah.update.change.detector.ChangeDetector;
import rubah.update.change.detector.ClassHierarchyChangeDetector;
import rubah.update.change.detector.FieldTypeChangeDetector;
import rubah.update.change.detector.MethodBodyChangeDetector;
import rubah.update.change.detector.MethodSignatureChangeDetector;
import rubah.update.change.mapper.ClassNameMapper;
import rubah.update.change.mapper.FieldNameMapper;
import rubah.update.change.mapper.Mapper;
import rubah.update.change.mapper.MethodNameAndSignatureMapper;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.converters.BooleanConverter;
import com.beust.jcommander.converters.FileConverter;


public class Comparator {

	@Parameter(
			converter=FileConverter.class,
			description="Previous version descriptor",
			names={"-v0","--descriptor"},
			required=true)
	private File previousVersionDescriptor;


	@Parameter(
			converter=FileConverter.class,
			description="Current version descriptor",
			names={"-v1","--jar"},
			required=true)
	private File currentVersionDescriptor;

	@Parameter(
			converter=BooleanConverter.class,
			description="Print NO_CHANGE",
			names={"-n","--no-changes"})
	private boolean printNoChanges = false;

	private Version v0, v1;

	//TODO can this constructor be private?
	public Comparator() {
		// Empty
	}

	public Comparator(Version v0, Version v1) {
		this.v0 = v0;
		this.v1 = v1;
	}

	public static void main(String[] args) throws IOException {

		Comparator c = new Comparator();

		JCommander argParser = new JCommander(c);
		try {
			argParser.parse(args);
		} catch (ParameterException e) {
			System.out.println(e.getMessage());
			argParser.usage();
			System.exit(1);
		}

		Namespace defaultNameSpace = new Namespace();
		c.v0 = new Version( 0,
				UpdatableJarAnalyzer.readFile(c.previousVersionDescriptor, defaultNameSpace),
				null);

		c.v1 = new Version(1,
				UpdatableJarAnalyzer.readFile(c.currentVersionDescriptor, defaultNameSpace),
				c.v0);

		c.compare();
	}

	private void compare() throws IOException {

		// Create a descriptor by comparing classes
		for (Entry<Clazz, ClassChange> entry : this.computeChanges(false).entrySet()) {
			if (!this.printNoChanges && entry.getValue().getChangeSet().isEmpty()) {
				continue;
			}
			System.out.println(entry.getKey() + "\t" + entry.getValue().getChangeSet());
			for (Entry<Field, Change<Field>> field : entry.getValue().getFieldChanges().entrySet()) {
				if (!this.printNoChanges && field.getValue().getChangeSet().isEmpty()) {
					continue;
				}
				System.out.println("\t" + field.getKey() + "\t" + field.getValue().getChangeSet());
			}
			for (Entry<Method, Change<Method>> method : entry.getValue().getMethodChanges().entrySet()) {
				if (!this.printNoChanges && method.getValue().getChangeSet().isEmpty()) {
					continue;
				}
				System.out.println("\t" + method.getKey() + "\t" + method.getValue().getChangeSet());
			}
		}
	}

	public Map<Clazz, ClassChange> computeChanges(boolean v0v0) {
		Map<Clazz, ClassChange> ret = new HashMap<Clazz, ClassChange>();

		for (Clazz c1 : this.v1.getNamespace().getDefinedClasses()) {

			ChangeSet classChangeSet = new ChangeSet();
			Clazz c0 = this.mapClass(c1);

			if (v0v0) {
				ret.put(c1, ClassChange.v0v0(c0));
				continue;
			}

			if (c0 == null) {
				ret.put(c1, ClassChange.newClass());
				continue;
			}

			for (ChangeDetector<Clazz> detector : this.getClassChangeDetectors()) {
				detector.detectChanges(c0, c1, classChangeSet);
			}

			HashMap<Method, Change<Method>> methodChanges =
					new HashMap<Method, Change<Method>>();

			for (Method m1 : c1.getMethods()) {
				Method m0 = this.mapMethod(c0, m1);

				ChangeSet methodChangeSet = new ChangeSet();

				if (m0 == null) {
					methodChangeSet.add(ChangeType.NEW_METHOD);
					methodChanges.put(m1, new Change<Method>(methodChangeSet));
					continue;
				}

				for (ChangeDetector<Method> detector : this.getMethodChangeDetectors()) {
					detector.detectChanges(m0, m1, methodChangeSet);
				}

				methodChanges.put(m1, new Change<Method>(methodChangeSet, m0));
			}

			HashMap<Field, Change<Field>> fieldChanges =
					new HashMap<Field, Change<Field>>();

			for (Field f1 : c1.getFields()) {
				Field f0 = this.mapField(c0, f1);

				ChangeSet fieldChangeSet = new ChangeSet();

				if (f0 == null) {
					int access = f1.getAccess();
					if (Modifier.isStatic(access) && Modifier.isFinal(access)) {
						fieldChangeSet.add(ChangeType.NEW_CONSTANT);
					} else {
						fieldChangeSet.add(ChangeType.NEW_FIELD);
					}
					fieldChanges.put(f1, new Change<Field>(fieldChangeSet));
					continue;
				}

				for (ChangeDetector<Field> detector : this.getFieldChangeDetectors()) {
					detector.detectChanges(f0, f1, fieldChangeSet);
				}

				fieldChanges.put(f1, new Change<Field>(fieldChangeSet, f0));
			}

			ret.put( c1, new ClassChange(classChangeSet, c0, fieldChanges, methodChanges));
		}

		return ret;
	}

	private List<ChangeDetector<Field>> getFieldChangeDetectors() {
		LinkedList<ChangeDetector<Field>> ret = new LinkedList<ChangeDetector<Field>>();

		ret.add(new FieldTypeChangeDetector());

		return ret;
	}

	private List<ChangeDetector<Method>> getMethodChangeDetectors() {
		LinkedList<ChangeDetector<Method>> ret = new LinkedList<ChangeDetector<Method>>();

		ret.add(new MethodSignatureChangeDetector());
		ret.add(new MethodBodyChangeDetector());

		return ret;
	}

	private List<ChangeDetector<Clazz>> getClassChangeDetectors() {
		LinkedList<ChangeDetector<Clazz>> ret = new LinkedList<ChangeDetector<Clazz>>();

		ret.add(new ClassHierarchyChangeDetector());

		return ret;
	}

	private Field mapField(Clazz c0, Field f1) {
		for (Mapper<Field> mapper : this.getFieldMappers(c0)) {
			Field f0 = mapper.map(f1);
			if (f0 != null) {
				return f0;
			}
		}

		return null;
	}

	private Method mapMethod(Clazz c0, Method m1) {
		for (Mapper<Method> mapper : this.getMethodMappers(c0)) {
			Method m0 = mapper.map(m1);
			if (m0 != null) {
				return m0;
			}
		}

		return null;
	}

	private Clazz mapClass(Clazz c1) {
		for (Mapper<Clazz> mapper : this.getClassMappers()) {
			Clazz c0 = mapper.map(c1);
			if (c0 != null) {
				return c0;
			}
		}

		return null;
	}

	private List<Mapper<Field>> getFieldMappers(Clazz c0) {
		LinkedList <Mapper<Field>> ret = new LinkedList<Mapper<Field>>();

		ret.add(new FieldNameMapper(c0));

		return ret;
	}

	private List<Mapper<Method>> getMethodMappers(Clazz c0) {
		LinkedList <Mapper<Method>> ret = new LinkedList<Mapper<Method>>();

		ret.add(new MethodNameAndSignatureMapper(c0));

		return ret;
	}

	private List<Mapper<Clazz>> getClassMappers() {
		LinkedList <Mapper<Clazz>> ret = new LinkedList<Mapper<Clazz>>();

		if (this.v0 != null) {
			ret.add(new ClassNameMapper(this.v0.getNamespace()));
		}

		return ret;
	}
}
