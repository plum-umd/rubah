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

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.javatuples.Pair;

import rubah.framework.Clazz;
import rubah.framework.Method;
import rubah.framework.Namespace;
import rubah.framework.Type;
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

	private Map<Pair<Clazz, Method>, Integer> overloads =
			new HashMap<Pair<Clazz,Method>, Integer>();
	private Version version;

	public UpdatableClassInfoGatherer(Version version) {
		super(version.getNamespace());
		this.version = version;
	}

	public Map<Pair<Clazz, Method>, Integer> getOverloads() {
		return this.overloads;
	}

	public void computeOverloads() {
		this.computeOverloads(computeSubClasses(this.namespace));
	}

	private void computeOverloads(HashMap<Clazz, List<Clazz>> subClasses) {
		Map<Class<?>, Set<Method>> cache = new HashMap<Class<?>, Set<Method>>();

		for (Clazz c : this.namespace.getDefinedClasses()) {
			if (!c.getParent().getNamespace().equals(this.namespace)) {
				Map<Method, List<Method>> map = new HashMap<Method, List<Method>>();

				try {
					for (Method m : this.getAllInheritedMethodsUsingReflection(Class.forName(c.getFqn(), false, UpdatableClassRenamer.class.getClassLoader()), cache)) {
						List<Method> list = new ArrayList<Method>();
						list.add(m);
						map.put(m, list);
					}
				} catch (ClassNotFoundException e) {
					throw new Error(e);
				}

				this.computeOverloadesRecur(map, c, subClasses);
			}
		}

		Iterator<Entry<Pair<Clazz, Method>, Integer>> iterator =
				this.getOverloads().entrySet().iterator();

		while (iterator.hasNext()) {
			Entry<Pair<Clazz, Method>, Integer> entry = iterator.next();

			if (entry.getValue() == 0) {
				iterator.remove();
			}
		}
	}

	private Set<Method> getAllInheritedMethodsUsingReflection(Class<?> c,
			Map<Class<?>, Set<Method>> cache) {

		Set<Method> ret = cache.get(c);

		if (ret != null) {
			return ret;
		} else {
			ret = new HashSet<Method>();
		}


		while (c != null) {

			for (Class<?> iface : c.getInterfaces()) {
				ret.addAll(this.getAllInheritedMethodsUsingReflection(iface, cache));
			}

			for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
				ret.add(
						new Method(
								m.getModifiers(),
								m.getName(),
								Type.getMethodDescriptor(m),
								this.namespace));
			}

			for (Constructor<?> m : c.getDeclaredConstructors()) {
				ret.add(
						new Method(
								m.getModifiers(),
								"<init>",
								Type.getMethodDescriptor(m),
								this.namespace));
			}

			c = c.getSuperclass();
		}

		cache.put(c, ret);
		return ret;
	}

	private void computeOverloadesRecur(
			Map<Method, List<Method>> map,
			Clazz c, HashMap<Clazz, List<Clazz>> subclasses) {

		List<Method> methodsAndConstructores = new LinkedList<Method>();
		methodsAndConstructores.addAll(c.getMethods());
		Collections.sort(methodsAndConstructores, new Comparator<Method>() {

			@Override
			public int compare(Method arg0, Method arg1) {
				int ret = arg0.getName().compareTo(arg1.getName());

				if (ret == 0) {
					ret = arg0.getASMDesc().compareTo(arg1.getASMDesc());
				}

				return ret;
			}
		});
		for(Method m : methodsAndConstructores) {
			Method afterM = this.version.eraseUpdatableTypes(m);
			List<Method> secondMap;
			if (map.containsKey(afterM)) {
				secondMap = map.get(afterM);
			}
			else {
				secondMap = new ArrayList<Method>();
				map.put(afterM, secondMap);
			}

			int idx = secondMap.indexOf(m);

			if (idx >= 0) {
				this.getOverloads().put(new Pair<Clazz, Method>(c, m), idx);
			}
			else {
				this.getOverloads().put(new Pair<Clazz, Method>(c, m), secondMap.size());
				secondMap.add(m);
			}
		}

		for(Clazz child : subclasses.get(c)) {
			this.computeOverloadesRecur(
					new HashMap<Method, List<Method>>(map), child, subclasses);
		}
	}

	@Override
	public void visit(int version, int access, String name, String signature,
			String superName, String[] interfaces) {
		super.visit(version, access, name, signature, superName, interfaces);
	}
}
