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
package rubah.update.change;

import java.util.HashMap;
import java.util.Map;

import rubah.framework.Clazz;
import rubah.framework.Field;
import rubah.framework.Method;

public class ClassChange extends Change<Clazz> {

	public static ClassChange newClass() {
		return new ClassChange(
				new ChangeSet().add(ChangeType.NEW_CLASS),
				null,
				new HashMap<Field, Change<Field>>(),
				new HashMap<Method, Change<Method>>());
	}

	public static ClassChange v0v0(Clazz c0) {
		return new ClassChange(
				new ChangeSet().add(ChangeType.V0V0),
				c0,
				new HashMap<Field, Change<Field>>(),
				new HashMap<Method, Change<Method>>());
	}

	private Map<Field, Change<Field>> fieldChanges;
	private Map<Method, Change<Method>> methodChanges;

	public ClassChange(
			ChangeSet changeSet,
			Clazz original,
			Map<Field, Change<Field>> fieldChanges,
			Map<Method, Change<Method>> methodChanges) {
		super(changeSet, original);
		this.fieldChanges = fieldChanges;
		this.methodChanges = methodChanges;

		for (Change<Method> m : this.methodChanges.values()) {
			this.changeSet = this.changeSet.merge(m.changeSet);
		}

		for (Change<Field> f : this.fieldChanges.values()) {
			this.changeSet = this.changeSet.merge(f.changeSet);
		}
	}

	public Map<Field, Change<Field>> getFieldChanges() {
		return this.fieldChanges;
	}

	public Map<Method, Change<Method>> getMethodChanges() {
		return this.methodChanges;
	}

}
