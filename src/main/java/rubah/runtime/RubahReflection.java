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
package rubah.runtime;

import rubah.Rubah;
import rubah.bytecode.RubahProxy;



public abstract class RubahReflection {
	public static Class<?> forName(String name) throws ClassNotFoundException {
		// This will fail if called from version n after version n+1 installed
		// TODO Add extra argument, version#, computed when class is loaded and
		// the correct version# is known

		Version version = VersionManager.getInstance().getRunningVersion();
		String ret = version.getUpdatableName(name);

		ret = (ret == null ? name : ret);

		return Class.forName(ret, true, Rubah.getLoader());
	}

	public static void wait(Object obj) throws java.lang.InterruptedException {
		if (obj instanceof RubahProxy)
			obj = Rubah.getConverted(obj);

		obj.wait();
	}

    public static void wait(Object obj, long timeout) throws java.lang.InterruptedException {
		if (obj instanceof RubahProxy)
			obj = Rubah.getConverted(obj);

		obj.wait(timeout);
	}

	public static void wait(Object obj, long timeout, int nanos) throws java.lang.InterruptedException {
		if (obj instanceof RubahProxy)
			obj = Rubah.getConverted(obj);

		obj.wait(timeout, nanos);
	}

	public static void notify(Object obj) throws java.lang.InterruptedException {
		if (obj instanceof RubahProxy)
			obj = Rubah.getConverted(obj);

		obj.notify();
	}

	public static void notifyAll(Object obj) throws java.lang.InterruptedException {
		if (obj instanceof RubahProxy)
			obj = Rubah.getConverted(obj);

		obj.notifyAll();
	}
}
