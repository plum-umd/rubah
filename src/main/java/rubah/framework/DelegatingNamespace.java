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

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class DelegatingNamespace extends Namespace {
	private Namespace delegateTo;

	public DelegatingNamespace(Namespace delegateTo, Set<String> classes) {
		this.delegateTo = delegateTo;

		for (String className : classes) {
			super.getClass(Type.getObjectType(className.replace('.', '/')));
		}

	}

	@Override
	public Clazz getClass(Type type) {

		Clazz ret;

		if (type.isArray()) {
			ret = this.definedClasses.get(type.getElementType().getClassName());

			if (ret != null) {
				return new Clazz(type, this);
			}
		} else {
			ret = this.definedClasses.get(type.getClassName());

			if (ret != null) {
				return ret;
			}
		}

		return this.delegateTo.getClass(type);
	}

	@Override
	public Collection<Clazz> getAllClasses() {
		HashSet<Clazz> ret = new HashSet<Clazz>();

		ret.addAll(this.getDefinedClasses());
		ret.addAll(this.delegateTo.getAllClasses());

		return ret;
	}

	@Override
	public boolean isBootstrap(Clazz c) {
		return this.delegateTo.isBootstrap(c);
	}
}
