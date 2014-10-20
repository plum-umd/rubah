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
package rubah.runtime;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.javatuples.Pair;

import rubah.bytecode.transformers.UpdatableClassRenamer;
import rubah.framework.Clazz;
import rubah.framework.Method;
import rubah.framework.Namespace;
import rubah.framework.Type;
import rubah.tools.UpdatableJarAnalyzer.VersionDescriptor;
import rubah.update.ProgramUpdate;
import rubah.update.UpdateClass;
import rubah.update.V0V0UpdateClass;

public class Version {
	private final int number;
	private final Map<Pair<Clazz, Method>, Integer> overloads;
	private Map<String, String> updatableToOriginalClassNames = new HashMap<String, String>();
	private Map<String, String> originalToUpdatableClassNames = new HashMap<String, String>();
	private ProgramUpdate update;
	private final Namespace namespace;

	private final Version previous;

	public Version(Namespace namespace) {
		this.namespace = namespace;

		this.previous = null;
		this.number = -1;
		this.overloads = new HashMap<Pair<Clazz,Method>, Integer>();
		this.update = new ProgramUpdate();
	}

	public Version(final int number, VersionDescriptor descriptor, Version previous) {
		this.previous = previous;
		this.number = number;
		this.namespace = descriptor.namespace;

		this.overloads =
				Collections.unmodifiableMap(descriptor.overloads);
	}

	public void computeTraversal() {
		this.computeProgramUpdate(new V0V0UpdateClass(), false, false);
	}

	public void computeV0V0Update(boolean isLazy) {
		this.computeProgramUpdate(null, true, isLazy);
	}

	public void computeProgramUpdate(UpdateClass updateClass, boolean isLazy) {
		this.computeProgramUpdate(updateClass, false, isLazy);
	}

	private void computeProgramUpdate(UpdateClass updateClass, boolean v0v0, boolean isLazy) {
		this.update = new ProgramUpdate(
						new ProgramUpdate.Callback() {
							public void foundUpdatableClass(Clazz c1) {
								Type newType =
										Type.getObjectType(UpdatableClassRenamer.rename(c1.getFqn(), Version.this.number).replace('.', '/'));

								Version.this.updatableToOriginalClassNames.put(newType.getClassName(), c1.getFqn());
								Version.this.originalToUpdatableClassNames.put(c1.getFqn(), newType.getClassName());
							}
						},
						updateClass,
						this,
						v0v0);

		this.update.setLazy(isLazy);
	}

	public int getNumber() {
		return this.number;
	}

	public Map<Pair<Clazz, Method>, Integer> getOverloads() {
		return this.overloads;
	}

	public String getUpdatableName(String originalName) {
		Version v = this;
		String ret = null;

		while (v != null && (ret = v.originalToUpdatableClassNames.get(originalName)) == null) {
			v = v.previous;
		}

		return ret;
	}

	public String getOriginalName(String updatableName) {
		Version v = this;
		String ret = null;

		while (v != null && (ret = v.updatableToOriginalClassNames.get(updatableName)) == null) {
			v = v.previous;
		}

		return ret;
	}

	public Method eraseUpdatableTypes(Method m) {
		Clazz retType = this.eraseUpdatableType(m.getRetType());
		List<Clazz> argTypes = new LinkedList<Clazz>();
		for (Clazz arg : m.getArgTypes()) {
			argTypes.add(this.eraseUpdatableType(arg));
		}

		return new Method(m.getAccess(), m.getName(), retType, argTypes);
	}

	public Clazz eraseUpdatableType(Clazz c) {
			if (c.getNamespace().equals(this.namespace)) {
				return this.namespace.getClass(Type.getType(Object.class), c.getDimensions());
			} else if (c.isArray() && c.getArrayType().getNamespace().equals(this.namespace)) {
				return this.namespace.getClass(Object.class);
			} else if (this.previous != null) {
				return this.previous.eraseUpdatableType(c);
			}

		return c;
	}

	public Namespace getNamespace() {
		return this.namespace;
	}

	public ProgramUpdate getUpdate() {
		return this.update;
	}

	public Version getPrevious() {
		return this.previous;
	}

	public Type renameIfUpdatable(Type type) {
	
		if (type.getSort() == org.objectweb.asm.Type.ARRAY) {
			Type renamed = this.renameIfUpdatable(type.getElementType());
	
			if (renamed != type.getElementType()) {
				return this.namespace.getClass(renamed, type.getDimensions()).getASMType();
			}
		} else {
			String newName = this.getUpdatableName(type.getClassName());
	
			if (newName != null) {
				type = Type.getObjectType(newName.replace('.', '/'));
			}
		}
	
		return type;
	}

	private Type[] renameIfUpdatable(Type[] types) {
		Type[] ret = new Type[types.length];
	
		int i = 0;
	
		for (Type t : types) {
			ret[i++] = this.renameIfUpdatable(t);
		}
	
		return ret;
	}

	public String renameInternalIfUpdatable(String internalName) {
		return this.renameIfUpdatable(Type.getObjectType(internalName)).getInternalName();
	}

	public String renameDescIfUpdatable(String desc) {
		return this.renameIfUpdatable(Type.getType(desc)).getDescriptor();
	}

	public String renameMethodDescIfUpdatable(String methodDesc) {
		Type retType = this.renameIfUpdatable(Type.getReturnType(methodDesc));
		Type[] argTypes = this.renameIfUpdatable(Type.getArgumentTypes(methodDesc));
	
		return Type.getMethodDescriptor(retType, argTypes);
	}

}
