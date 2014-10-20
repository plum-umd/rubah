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
package rubah.update;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.apache.commons.io.IOUtils;

public class FileUpdateClass implements UpdateClass {
	private static final long serialVersionUID = 3936446203146229615L;
	private File file;

	public FileUpdateClass(File file) {
		this.file = file;
	}

	@Override
	public byte[] getBytes() {
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(this.file);
			return IOUtils.toByteArray(fis);
		} catch (IOException e) {
			throw new Error(e);
		} finally {
			if (fis != null) {
				try {
					fis.close();
				} catch (IOException e) {
					// Don't care
				}
			}
		}
	}

}
