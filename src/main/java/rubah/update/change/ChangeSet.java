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

public class ChangeSet {
	private final static ChangeSet SUPPORTED_JVM_CHANGES =
			new ChangeSet(ChangeType.METHOD_BODY_CHANGE);

	private int set = 0;

	private ChangeSet(int set) {
		this.set = set;
	}

	public ChangeSet(ChangeType ... changes) {
		for (ChangeType change : changes) {
			this.add(change);
		}
	}

	public ChangeSet add(ChangeType change) {
		this.set |= change.getMask();
		return this;
	}

	public boolean hasChange(ChangeType change) {
		if (change == ChangeType.NO_CHANGE) {
			return this.set == 0;
		}

		return (this.set & change.getMask()) != 0;
	}

	public ChangeSet intersect(ChangeSet other) {
		return new ChangeSet(this.set & other.set);
	}

	public boolean isEmpty() {
		return this.set == 0;
	}

	public ChangeSet merge(ChangeSet otherSet) {
		return new ChangeSet(this.set | otherSet.set);
	}

	@Override
	public String toString() {
		StringBuffer buf = new StringBuffer();

		if (this.set == 0) {
			buf.append(ChangeType.NO_CHANGE);
		} else {
			ChangeType[] values = ChangeType.values();
			for (int i = 1 ; i < values.length ; i++) {
				if (this.hasChange(values[i])) {
					buf.append(values[i] + " ");
				}
			}
		}

		return buf.toString();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof ChangeSet) {
			return this.set == ((ChangeSet)obj).set;
		}
		return false;
	}

	@Override
	public int hashCode() {
		return new Integer(this.set).hashCode();
	}

	public boolean isJVMSupported() {
		return ((SUPPORTED_JVM_CHANGES.set | this.set) == SUPPORTED_JVM_CHANGES.set);
	}
}
