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

import org.apache.commons.io.IOUtils;


public class V0V0UpdateClass implements UpdateClass {
	private static final long serialVersionUID = -5277525188797321572L;

	private static abstract class DummyUpdateClass { }

	@Override
	public byte[] getBytes() {
		try {
			return IOUtils.toByteArray(
					V0V0UpdateClass.class.getResourceAsStream(
							"/" + DummyUpdateClass.class.getName().replace('.', '/') + ".class"));
		} catch (IOException e) {
			throw new Error(e);
		}
	}
}
