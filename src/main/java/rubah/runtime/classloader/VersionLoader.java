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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import org.apache.commons.io.IOUtils;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import rubah.bytecode.transformers.DummifyStaticInitTransformer;
import rubah.bytecode.transformers.ReflectionRewritter;
import rubah.runtime.Version;

public class VersionLoader extends DefaultClassLoader {
	protected Version version;
	private JarFile versionJar;

	public VersionLoader(Version version, File versionJar, TransformerFactory factory) {
		super(version.getNamespace(), factory);
		this.version = version;
		this.factory = factory;
		try {
			if (versionJar != null) {
				this.versionJar = new JarFile(versionJar);
			}
		} catch (IOException e) {
			throw new Error(e);
		}
	}

	@Override
	protected byte[] getOriginalClassBytes(String className) throws IOException {
		String resourceName = className.replace('.', '/') + ".class";

		if (this.versionJar == null) {
			return super.getOriginalClassBytes(className);
		}

		return this.getResource(resourceName);
	}

	@Override
	public byte[] getResource(String resourceName) throws IOException {
		if (this.versionJar == null) {
			return super.getResource(resourceName);
		}

		JarFile jarFile = this.versionJar;

		ZipEntry ze = jarFile.getEntry(resourceName);
		if (ze == null)
			throw new FileNotFoundException(resourceName);
		return IOUtils.toByteArray(jarFile.getInputStream(ze));
	}

	@Override
	protected ClassVisitor addTransformers(
			ClassVisitor visitor, HashMap<String, Object> objectsMap) {

		visitor = this.factory.getUpdatableClassRenamer(
						objectsMap,
						this.version,
						this.namespace,
						visitor);
		visitor = new DummifyStaticInitTransformer(
				objectsMap,
				this.version,
				visitor);
		visitor = new ReflectionRewritter(
				objectsMap,
				this.namespace,
				visitor);
		return super.addTransformers(visitor, objectsMap);
	}

	@Override
	protected void analyzeClass(byte[] classBytes) {
		// Empty, updatable classes were already analyzed
	}

	@Override
	protected ClassWriter getClassWriter(int flags) {
		return new RubahClassWriter(flags, this.version, this.namespace);
	}

	@Override
	protected String getOriginalClassName(String className) {
		return this.version.getOriginalName(className);
	}


	public JarFile getVersionJar() {
		return this.versionJar;
	}
}
