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
package rubah.update.change.detector;

import rubah.framework.Field;
import rubah.update.change.ChangeSet;
import rubah.update.change.ChangeType;

import org.objectweb.asm.Opcodes;

public class FieldTypeChangeDetector implements ChangeDetector<Field>, Opcodes {

	public void detectChanges(Field f0, Field f1, ChangeSet changeSet) {

		boolean changed = false;

		int ignoreAccessMask = ~ (ACC_PUBLIC | ACC_PROTECTED | ACC_PRIVATE);

		int access0 = f0.getAccess() & ignoreAccessMask;
		int access1 = f1.getAccess() & ignoreAccessMask;

		// Modifiers
		changed = changed || (access0 != access1);

		// Type
		changed = changed || (!f0.getType().getFqn().equals(f1.getType().getFqn()));

		if (changed) {
			changeSet.add(ChangeType.FIELD_TYPE_CHANGE);
		}
	}

}
