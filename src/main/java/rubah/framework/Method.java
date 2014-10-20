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

import java.util.LinkedList;
import java.util.List;

public class Method {

	private int access;
	private String name;
	private Clazz retType;
	private List<Clazz> argTypes;
	private String bodyMD5;

	public Method(int access, String name, String desc, Namespace namespace) {
		this.access = access;
		this.name = name;
		this.retType = namespace.getClass(Type.getReturnType(desc));

		this.argTypes = new LinkedList<Clazz>();
		for (Type arg : Type.getArgumentTypes(desc)) {
			this.argTypes.add(namespace.getClass(arg));
		}
	}

	public Method(int access, String name, Clazz retType, List<Clazz> argTypes) {
		this.access = access;
		this.name = name;
		this.retType = retType;
		this.argTypes = argTypes;
	}

	@Override
	public String toString() {
		StringBuffer ret = new StringBuffer();

		ret.append(this.retType);
		ret.append(" ");
		ret.append(this.name);
		ret.append("(");
		ret.append(this.argTypes);
		ret.append(")");

		return ret.toString();
	}

	@Override
	public boolean equals(Object obj) {

		if (obj instanceof Method) {
			Method m = (Method) obj;
			return this.name.equals(m.name) &&
					this.retType.equals(m.retType) &&
					this.argTypes.equals(m.argTypes);
		}

		return false;
	}

	@Override
	public int hashCode() {
		return this.name.hashCode() ^ this.retType.hashCode() ^ this.argTypes.hashCode();
	}

	public int getAccess() {
		return this.access;
	}

	public String getName() {
		return this.name;
	}

	public Clazz getRetType() {
		return this.retType;
	}

	public List<Clazz> getArgTypes() {
		return this.argTypes;
	}

	public String getASMDesc() {

		Type[] args = new Type[this.argTypes.size()];

		int i = 0;
		for (Clazz arg : this.argTypes) {
			args[i++] = arg.getASMType();
		}

		return Type.getMethodDescriptor(this.retType.getASMType(), args);
	}

	public String getBodyMD5() {
		return this.bodyMD5;
	}

	public void setBodyMD5(String bodyMD5) {
		this.bodyMD5 = bodyMD5;
	}

}
