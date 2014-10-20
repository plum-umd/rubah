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
package rubah.runtime.classloader;

import java.util.HashMap;

import org.objectweb.asm.ClassVisitor;

import rubah.bytecode.transformers.AddGettersAndSetters;
import rubah.bytecode.transformers.UpdatableClassRenamer;
import rubah.framework.Namespace;
import rubah.runtime.Version;

public class TransformerFactory {
	public ClassVisitor getUpdatableClassRenamer(HashMap<String, Object> objectsMap, Version version, Namespace namespace, ClassVisitor visitor) {
		return new UpdatableClassRenamer(version, objectsMap, visitor);
	}

	public ClassVisitor getAddGettersAndSetters(HashMap<String, Object> objectsMap, Namespace namespace, ClassVisitor visitor) {
		return new AddGettersAndSetters(objectsMap, namespace, visitor);
	}
}