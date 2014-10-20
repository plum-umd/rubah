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
package rubah.runtime.state;

import java.io.Serializable;

public class ClassRedefinition implements Serializable {
	private final byte[] scaffoldedVersion;
	private final byte[] newVersion;
	private final boolean isFailfast;
	private final boolean hasChanged;

	public ClassRedefinition(byte[] scaffoldedVersion, byte[] newVersion, boolean isFailfast,
			boolean hasChanged) {
		this.scaffoldedVersion = scaffoldedVersion;
		this.newVersion = newVersion;
		this.isFailfast = isFailfast;
		this.hasChanged = hasChanged;
	}

	public byte[] getScaffoldedVersion() {
		return scaffoldedVersion;
	}

	public byte[] getNewVersion() {
		return newVersion;
	}

	public boolean isFailfast() {
		return isFailfast;
	}

	public boolean isHasChanged() {
		return hasChanged;
	}
}
