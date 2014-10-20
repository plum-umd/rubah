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
package rubah.framework;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Clazz implements Comparable<Clazz> {
	private String fqn;
	private Clazz parent;
	private Set<Field> fields = new HashSet<Field>();
	private Set<Clazz> interfaces = new HashSet<Clazz>();
	private Map<Method, Method> methods = new HashMap<Method, Method>();
	private Type type;
	private boolean iface = false;
	private Namespace namespace;

	/*default*/ Clazz(Type type, Namespace namespace) {
		this.fqn = type.getClassName();
		this.type = type;
		this.namespace = namespace;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Clazz) {
			Clazz c = (Clazz) obj;
			return this.type.equals(c.type) && this.namespace.equals(c.namespace);
		}
		return false;
	}

	@Override
	public int hashCode() {
		return this.type.hashCode() ^ this.namespace.hashCode();
	}

	public String getFqn() {
		return this.fqn;
	}

	public Clazz getParent() {
		return this.parent;
	}

	public void setParent(Clazz parent) {
		this.parent = parent;
	}

	public Set<Method> getMethods() {
		return this.methods.keySet();
	}

	public Method findMethod(Method m) {
		Method ret = this.methods.get(m);

		if (ret != null)
			return ret;

		if (this.parent != null)
			return this.parent.findMethod(m);

		return null;
	}

	public void addMethod(Method m) {
		this.methods.put(m, m);
	}

	public Set<Field> getFields() {
		return this.fields;
	}

	@Override
	public String toString() {
		StringBuffer ret = new StringBuffer();

		ret.append(this.fqn);
		if (this.parent != null) {
			ret.append(" extends ");
			ret.append(this.parent.fqn);
		}

		return ret.toString();
	}

	public int compareTo(Clazz o) {
		return this.fqn.compareTo(o.fqn);
	}

	public Type getASMType() {
		return this.type;
	}

	public boolean isArray() {
		return this.type.getSort() == org.objectweb.asm.Type.ARRAY;
	}

	public Clazz getArrayType() {
		if (this.isArray()) {
			return this.namespace.getClass(this.type.getElementType());
		}

		return this;
	}

	public int getDimensions() {
		if (!this.isArray()) {
			return 0;
		}

		return this.type.getDimensions();
	}

	public Class<?> asClass(ClassLoader loader) throws ClassNotFoundException {
		return Class.forName(this.fqn, true, loader);
	}

	public void setInterface(boolean isInterface) {
		this.iface = isInterface;
	}

	public boolean isInterface() {
		return this.iface;
	}

	public Set<Clazz> getInterfaces() {
		return this.interfaces;
	}

	public Namespace getNamespace() {
		return this.namespace;
	}
}
