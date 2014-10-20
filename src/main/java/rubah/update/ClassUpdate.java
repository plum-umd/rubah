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

import rubah.framework.Clazz;
import rubah.update.change.ClassChange;

public class ClassUpdate {
	private Clazz v0, v1;
	private ClassChange classChanges;

	public ClassUpdate(Clazz v0, Clazz v1, ClassChange classChanges) {
		this.v0 = v0;
		this.v1 = v1;
		this.classChanges = classChanges;
	}

	public Clazz getV0() {
		return this.v0;
	}

	public Clazz getV1() {
		return this.v1;
	}

	public ClassChange getClassChanges() {
		return this.classChanges;
	}
}
