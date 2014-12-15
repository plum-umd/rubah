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
package rubah.tools;

import java.io.IOException;

import rubah.framework.Clazz;
import rubah.framework.Method;
import rubah.framework.Namespace;

public class AnalysisPrinter extends ReadTool {
	public static final String TOOL_NAME = "printer";

	@Override
	public void processJar() throws IOException {

		Namespace namespace = UpdatableJarAnalyzer.readFile(this.inFile, new Namespace());

		for (Clazz c : namespace.getDefinedClasses()){
			System.out.println(c);
			for (Method m : c.getMethods()) {
				System.out.println(m.getName() + " - " + m.getBodyMD5());
			}
		}
	}

}
