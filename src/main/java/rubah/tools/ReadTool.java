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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

/*default*/ abstract class ReadTool {
	private static final String CLASS_FILE_REGEX = ".*\\.class$";
	private static final Pattern PATTERN = Pattern.compile(CLASS_FILE_REGEX);

	protected File inFile;
	protected RubahTool.Parameters parameters;

	public ReadTool() { /* Empty */ }

	public ReadTool (File inJar) {
		this.inFile = inJar;
	}

	protected void foundResource(String name, InputStream inputStream)
			throws IOException { /* Empty*/ }

	protected void foundClassFile(String name, InputStream inputStream)
			throws IOException { /* Empty*/ }

	public void processJar() throws IOException {

		if (this.inFile == null)
			this.inFile = this.parameters.injar;

		JarFile jar;
		jar = new JarFile(this.inFile);

		HashSet<String> foundFiles = new HashSet<>();

		try {
			Enumeration<JarEntry> contents = jar.entries();

			while (contents.hasMoreElements()) {
				JarEntry entry = contents.nextElement();

				if (foundFiles.contains(entry.getName()))
					continue;

				foundFiles.add(entry.getName());

				if (PATTERN.matcher(entry.getName()).matches()) {
					this.foundClassFile(
							entry.getName(),
							jar.getInputStream(entry));
				} else {
					this.foundResource(
							entry.getName(),
							jar.getInputStream(entry));
				}
			}
		} finally {
			jar.close();
		}
	}

	protected RubahTool.Parameters getParameters() {
		this.parameters = new RubahTool.Parameters();
		return this.parameters;
	}
}
