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

public enum ChangeType {
	NO_CHANGE,
	DELETED_METHOD,
	NEW_METHOD,
	METHOD_SIGNATURE_CHANGE,
	METHOD_BODY_CHANGE,
	STATIC_INITIALIZER_CHANGE,
	DELETED_FIELD,
	NEW_FIELD,
	NEW_CONSTANT,
	FIELD_TYPE_CHANGE,
	DELETED_CLASS,
	NEW_CLASS,
	HIERARCHY_CHANGE,
	CHANGED_SUPERTYPE,
	V0V0;

	/*default*/ int getMask() {
		int n = this.ordinal();;
		return ((n == 0) ? 0 : 1 << n - 1);
	}
}
