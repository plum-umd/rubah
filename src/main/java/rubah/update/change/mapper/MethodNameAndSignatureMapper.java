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
package rubah.update.change.mapper;

import java.util.HashMap;
import java.util.Map;

import rubah.framework.Clazz;
import rubah.framework.Method;

import org.javatuples.Pair;

public class MethodNameAndSignatureMapper implements Mapper<Method> {

	private Map<Pair<String, String>, Method> methodMap =
			new HashMap<Pair<String,String>, Method>();

	public MethodNameAndSignatureMapper(Clazz c0) {
		for (Method m : c0.getMethods()) {
			this.methodMap.put(getMethodPair(m), m);
		}
	}

	private static Pair<String, String> getMethodPair(Method m) {
		return new Pair<String, String>( m.getASMDesc(), m.getName());
	}

	public Method map(Method t) {
		return this.methodMap.get(getMethodPair(t));
	}
}
