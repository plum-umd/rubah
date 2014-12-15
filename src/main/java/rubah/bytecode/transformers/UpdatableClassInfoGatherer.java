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
package rubah.bytecode.transformers;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import rubah.framework.Clazz;
import rubah.framework.Namespace;
import rubah.runtime.Version;

public class UpdatableClassInfoGatherer extends BasicClassInfoGatherer {
	public static HashMap<Clazz, List<Clazz>> computeSubClasses(Namespace namespace) {
		HashMap<Clazz, Set<Clazz>> tmp = new HashMap<Clazz, Set<Clazz>>();

		for (Clazz c : namespace.getDefinedClasses()) {
			tmp.put(c, new HashSet<Clazz>());
		}

		for (Clazz c : namespace.getDefinedClasses()) {
			if (c.getParent().getNamespace().equals(namespace)) {
				tmp.get(c.getParent()).add(c);
			}
		}


		HashMap<Clazz, List<Clazz>> ret = new HashMap<Clazz, List<Clazz>>();

		for (Entry<Clazz, Set<Clazz>> entry : tmp.entrySet()) {
			LinkedList<Clazz> lst = new  LinkedList<>(entry.getValue());

			Collections.sort(lst, new Comparator<Clazz>() {
				@Override
				public int compare(Clazz o1, Clazz o2) {
					return o1.getFqn().compareTo(o2.getFqn());
				}
			});

			ret.put(entry.getKey(), lst);
		}

		return ret;
	}

	public static Map<Clazz, Set<Clazz>> computeInterfaces(Namespace namespace) {
		HashMap<Clazz, Set<Clazz>> ret = new HashMap<Clazz, Set<Clazz>>();

		for (Clazz c : namespace.getDefinedClasses()) {
			ret.put(c, new HashSet<Clazz>());
		}

		for (Clazz c : namespace.getDefinedClasses()) {
			for (Clazz iface : c.getInterfaces()) {
				if (iface.getNamespace().equals(namespace)) {
					ret.get(iface).add(c);
				}
			}
		}

		return ret;
	}

	public UpdatableClassInfoGatherer(Version version) {
		super(version.getNamespace());
	}

	@Override
	public void visit(int version, int access, String name, String signature,
			String superName, String[] interfaces) {
		super.visit(version, access, name, signature, superName, interfaces);
	}
}
