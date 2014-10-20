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

import java.util.HashSet;
import java.util.Set;

import rubah.framework.Clazz;
import rubah.update.change.ChangeSet;
import rubah.update.change.ChangeType;

public class ClassHierarchyChangeDetector implements ChangeDetector<Clazz> {

	public void detectChanges(Clazz c0, Clazz c1, ChangeSet changeSet) {

		// Super class
		if (!c0.getParent().getFqn().equals(c1.getParent().getFqn())) {
			changeSet.add(ChangeType.HIERARCHY_CHANGE);
			return;
		}

		// Interfaces
		//TODO Compare all the interfaces (including inherited) instead of just the declared interfaces

		if (c0.getInterfaces().size() != c1.getInterfaces().size()) {
			changeSet.add(ChangeType.HIERARCHY_CHANGE);
			return;
		}

		Set<String> ifaceNames = new HashSet<String>();

		for (Clazz iface : c0.getInterfaces()) {
			ifaceNames.add(iface.getFqn());
		}

		for (Clazz iface : c1.getInterfaces()) {
			if (!ifaceNames.contains(iface.getFqn())) {
				changeSet.add(ChangeType.HIERARCHY_CHANGE);
				return;
			}
		}
	}

}
