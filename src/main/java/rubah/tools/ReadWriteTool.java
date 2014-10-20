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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.apache.commons.io.IOUtils;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.converters.FileConverter;

/*default*/ abstract class ReadWriteTool extends ReadTool {
	protected File outFile;
	private JarOutputStream outJarStream;
	
	public static class ReadWriteParameters extends RubahTool.Parameters {
		@Parameter(
				converter=FileConverter.class,
				description="Output file",
				required=true,
				names={"-o","--out"})
		protected File outJar;
	}

	protected void addFileToOutJar(String name, byte[] bytes) {
		JarEntry entry = new JarEntry(name);
		try {
			this.outJarStream.putNextEntry(entry);
			if (bytes != null) {
				this.outJarStream.write(bytes, 0, bytes.length);
			}
			this.outJarStream.closeEntry();
		} catch (IOException e) {
			throw new Error(e);
		}
	}

	@Override
	protected void foundResource(String name, InputStream inputStream) throws IOException {
		this.addFileToOutJar(name, IOUtils.toByteArray(inputStream));
	}

	@Override
	protected void foundClassFile(String name, InputStream inputStream) throws IOException {
		this.addFileToOutJar(name, IOUtils.toByteArray(inputStream));
	}


	@Override
	public void processJar() throws IOException {
		
		if (this.parameters instanceof ReadWriteParameters)
			this.outFile = ((ReadWriteParameters)this.parameters).outJar;
		
		this.outJarStream =
				new JarOutputStream(new FileOutputStream(this.outFile));

//		try {
			super.processJar();
			this.endProcess();
//		} catch (Throwable e) {
//			throw new Error(e);
//		} finally {
				this.outJarStream.close();
//		}
	}
	protected void endProcess() throws IOException { /* Empty */ }
	
	protected RubahTool.Parameters getParameters() {
		this.parameters = new ReadWriteParameters();
		return this.parameters;
	}

}
