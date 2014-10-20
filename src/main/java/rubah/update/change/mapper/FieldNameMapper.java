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
import rubah.framework.Field;

public class FieldNameMapper implements Mapper<Field> {

	private Map<String, Field> fieldMap = new HashMap<String, Field>();

	public FieldNameMapper(Clazz c0) {
		for (Field f : c0.getFields()) {
			this.fieldMap.put(f.getName(), f);
		}
	}

	public Field map(Field f) {
		return this.fieldMap.get(f.getName());
	}
}
