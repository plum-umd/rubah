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


public class Field {

	private int access;
	private String name;
	private Clazz type;
	private boolean constant;

	public Field(int access, String name, Clazz type, boolean constant) {
		this.access = access;
		this.name = name;
		this.type = type;
		this.constant = constant;
	}

	@Override
	public String toString() {
		StringBuffer ret = new StringBuffer();

		ret.append(this.type);
		ret.append(" ");
		ret.append(this.name);

		return ret.toString();
	}

	@Override
	public boolean equals(Object obj) {

		if (obj instanceof Field) {
			Field f = (Field) obj;
			return this.name.equals(f.name) &&
					this.type.equals(f.type);
		}

		return false;
	}

	@Override
	public int hashCode() {
		return this.name.hashCode() ^ this.type.hashCode();
	}

	public int getAccess() {
		return this.access;
	}

	public String getName() {
		return this.name;
	}

	public Clazz getType() {
		return this.type;
	}

	public boolean isConstant() {
		return constant;
	}
}
