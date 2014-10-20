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
package rubah.update;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import rubah.bytecode.transformers.UpdatableClassInfoGatherer;
import rubah.framework.Clazz;
import rubah.framework.Field;
import rubah.framework.Method;
import rubah.framework.Namespace;
import rubah.framework.Type;
import rubah.runtime.Version;
import rubah.tools.Comparator;
import rubah.tools.UpdateClassGenerator;
import rubah.update.change.Change;
import rubah.update.change.ChangeSet;
import rubah.update.change.ChangeType;
import rubah.update.change.ClassChange;

public class ProgramUpdate {

	private Map<Clazz, ClassUpdate> v0Updates = new HashMap<Clazz, ClassUpdate>();
	private Map<Clazz, ClassUpdate> v1Updates = new HashMap<Clazz, ClassUpdate>();
	private Set<Clazz> redefined = new HashSet<Clazz>();
	private Set<Clazz> converted = new HashSet<Clazz>();
	private Set<Clazz> updated = new HashSet<Clazz>();
	private Version v1;
	private boolean lazy = false;
	private HashSet<Clazz> border = new HashSet<Clazz>();

	public ProgramUpdate() {
		// Empty constructor
	}

	public static interface Callback {
		public void foundUpdatableClass(Clazz c1);
	}

	public ProgramUpdate(
			Callback c,
			UpdateClass updateClass,
			final Version version,
			boolean v0v0) {

		this.v1 = version;

		Map<Clazz, ClassChange> changes =
				new Comparator(version.getPrevious(), version).computeChanges(v0v0);

		for (Entry<Clazz, ClassChange> entry : changes.entrySet()) {
			Clazz c1 = entry.getKey();
			Clazz c0 = entry.getValue().getOriginal();

			this.addClassUpdate(new ClassUpdate(c0, c1, entry.getValue()));

			c.foundUpdatableClass(c1);
		}

		// Propagate updates to subclasses
		if (version.getPrevious() != null) {
			this.propagateSubclasses(c, version.getPrevious().getNamespace(), changes);
		}

		// Find classes that are converted even though not updated
		// TODO: move this up, passing it to the comparator to map v0 to v1 names according to conversion method arguments
		if (updateClass != null) {
			this.findConverted(updateClass, version.getPrevious().getNamespace());
		}

		this.computeNonUpdatableBorder();
	}

	private void computeNonUpdatableBorder() {
		for (Clazz c : this.updated) {
			Clazz parent = c.getParent();

			while (parent != null) {
				this.border.add(parent);
				parent = parent.getParent();
			}
		}
	}

	private void findConverted(UpdateClass updateClass, final Namespace n0) {
		new ClassReader(updateClass.getBytes()).accept(
				new ClassVisitor(Opcodes.ASM5) {
					@Override
					public MethodVisitor visitMethod(int access, String name,
							String desc, String signature, String[] exceptions) {

						Clazz c0;

						if (name.equals(UpdateClassGenerator.METHOD_NAME)) {
							String c0Name = Type.getArgumentTypes(desc)[0].getClassName();
							c0Name = c0Name.replaceFirst(UpdateClassGenerator.V0_PREFFIX + "\\.", "");
							c0 = n0.getClass(Type.getObjectType(c0Name.replace('.', '/')));

							String c1Name = Type.getArgumentTypes(desc)[1].getClassName();
							c1Name = c1Name.replaceFirst(UpdateClassGenerator.V1_PREFFIX + "\\.", "");

							ProgramUpdate.this.converted.add(c0);
						} else if (name.equals(UpdateClassGenerator.METHOD_NAME_STATIC)) {
							String c1Name = Type.getArgumentTypes(desc)[0].getClassName();
							c1Name = c1Name.replaceFirst(UpdateClassGenerator.V1_PREFFIX + "\\.", "");
							c0 = n0.getClass(Type.getObjectType(c1Name.replace('.', '/')));

							ProgramUpdate.this.converted.add(c0);
						}

						return null;
					}
				},
				ClassReader.SKIP_CODE);
	}

	private void propagateSubclasses(Callback c, Namespace n0,
			Map<Clazz, ClassChange> changes) {
		Map<Clazz, List<Clazz>> subclasses =
				UpdatableClassInfoGatherer.computeSubClasses(n0);
		Map<Clazz, Set<Clazz>> interfaces =
				UpdatableClassInfoGatherer.computeInterfaces(n0);

		for (Entry<Clazz, ClassChange> entry : changes.entrySet()) {

			if (entry.getValue().getChangeSet().isJVMSupported()) {
				// No changes or class redefined
				continue;
			}

			Clazz c1 = entry.getKey();
			Clazz c0 = this.getV0(c1);

			if (c0 == null) {
				// New class
				continue;
			}

			Queue<Clazz> classesToProcess = new LinkedList<Clazz>();

			classesToProcess.addAll(subclasses.get(c0));
			classesToProcess.addAll(interfaces.get(c0));

			while (!classesToProcess.isEmpty()) {
				Clazz sub0 = classesToProcess.poll();
				Clazz sub1 = this.getV1(sub0);

				if (sub1 != null && !this.isUpdated(sub0)) {
					ClassChange classChange = new ClassChange(
							new ChangeSet(entry.getValue().getChangeSet().isJVMSupported() ? ChangeType.METHOD_BODY_CHANGE : ChangeType.CHANGED_SUPERTYPE),
							sub0,
							new HashMap<Field, Change<Field>>(),
							new HashMap<Method, Change<Method>>());

					c.foundUpdatableClass(sub1);
					this.addClassUpdate(new ClassUpdate(sub0, sub1, classChange));
				}

				classesToProcess.addAll(subclasses.get(sub0));
				classesToProcess.addAll(interfaces.get(sub0));
			}
		}
	}

	private void addClassUpdate(ClassUpdate update) {
		this.v0Updates.put(update.getV0(), update);
		this.v1Updates.put(update.getV1(), update);

		ChangeSet cs = update.getClassChanges().getChangeSet();

		if (cs.isEmpty()) {
			return;
		}

		Clazz c0 = update.getV0();

		if (c0 == null) {
			// New class, skip
			return;
		} else if (cs.isJVMSupported()) {
			// Change supported by JVM
			this.redefined.add(c0);
		} else {
			this.updated.add(c0);
		}
	}

	public Clazz getV1(Clazz c0) {

		if (c0.isArray()) {
			Clazz ret = this.getV1(c0.getArrayType());
			if (ret == null)
				return null;

			return this.v1.getNamespace().getClass(ret.getASMType(), ret.getDimensions());
		}

		ClassUpdate update = this.v0Updates.get(c0);

		if (update == null) {
			return null;
		}

		return update.getV1();
	}

	public Clazz getV0(Clazz c1) {

		if (c1.isArray()) {
			Clazz ret = this.getV0(c1.getArrayType());
			if (ret == null)
				return null;

			return this.v1.getPrevious().getNamespace().getClass(ret.getASMType(), c1.getDimensions());
		}

		ClassUpdate update = this.v1Updates.get(c1);

		if (update == null) {
			return null;
		}

		return update.getV0();
	}

	public boolean isConverted(Clazz c0) {
		return this.isUpdated(c0) || this.converted.contains(c0);
	}

	public boolean isRedefined(Clazz c0) {
		return this.redefined.contains(c0);
	}

	public boolean isUpdated(Clazz c0) {
		return this.updated.contains(c0);
	}

	public ClassChange getChanges(Clazz c0) {
		return this.v0Updates.get(c0).getClassChanges();
	}

	public boolean isLazy() {
		return lazy;
	}

	public void setLazy(boolean isLazy) {
		this.lazy = isLazy;
	}

	public HashSet<Clazz> getBorder() {
		return border;
	}
}
