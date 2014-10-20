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

import rubah.framework.Clazz;
import rubah.framework.Method;
import rubah.update.change.ChangeSet;
import rubah.update.change.ChangeType;

public class MethodSignatureChangeDetector implements ChangeDetector<Method> {

	public void detectChanges(Method m0, Method m1, ChangeSet changeSet) {

		boolean changed = false;

		// Modifiers
		changed = changed || (m0.getAccess() != m1.getAccess());

		// Return type
		changed = changed || (!m0.getRetType().getFqn().equals(m1.getRetType().getFqn()));

		// Arguments number/type
		// TODO: arguments and return type after type erasure
		if (m0.getArgTypes().size() != m1.getArgTypes().size()) {
			changed = true;
		} else {
			for (int i = 0 ; i < m0.getArgTypes().size() ; i++) {
				Clazz arg0 = m0.getArgTypes().get(i);
				Clazz arg1 = m1.getArgTypes().get(i);
				changed = changed || (!arg0.getFqn().equals(arg1.getFqn()));
			}
		}

		if (changed) {
			changeSet.add(ChangeType.METHOD_SIGNATURE_CHANGE);
		}
	}

}
