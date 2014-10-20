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

import java.io.IOException;

import rubah.Rubah;

public class JarUpdateClass implements UpdateClass {
	private static final long serialVersionUID = -9066140634022342455L;
	private String name;

	public JarUpdateClass(String name) {
		this.name = name;
	}

	@Override
	public byte[] getBytes() {
		try {
			return Rubah.getLoader().getResourceByName(this.name.replace('.', '/') + ".class");
		} catch (IOException e) {
			throw new Error("Update class \"" + this.name + "\" not found");
		}
	}

}
