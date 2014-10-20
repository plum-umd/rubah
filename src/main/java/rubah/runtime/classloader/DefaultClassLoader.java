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

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

import org.apache.commons.io.IOUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import rubah.bytecode.transformers.AddForwardField;
import rubah.bytecode.transformers.DecreaseClassMethodsProtection;
import rubah.bytecode.transformers.RedirectFieldManipulation;
import rubah.bytecode.transformers.ReplaceOriginalNamesByUnique;
import rubah.bytecode.transformers.ReplaceUniqueByOriginalNames;
import rubah.framework.Namespace;

public abstract class DefaultClassLoader implements Opcodes {
	protected Namespace namespace;
	protected TransformerFactory factory;

	public DefaultClassLoader(Namespace namespace, TransformerFactory factory) {
		this.namespace = namespace;
		this.factory = factory;
	}

	protected byte[] getOriginalClassBytes(String className) throws IOException {
		className = "/" + className.replace('.', '/') + ".class";

		InputStream stream = DefaultClassLoader.class.getResourceAsStream(className);
		if (stream == null) {
			throw new IOException(className);
		}

		byte[] ret = IOUtils.toByteArray(stream);

		// Analyze non-updatable class
		this.analyzeClass(ret);

		return ret;
	}

	public byte[] getResource(String resourceName) throws IOException {
		InputStream stream = DefaultClassLoader.class.getResourceAsStream(resourceName);
		if (stream == null) {
			throw new IOException(resourceName);
		}

		return IOUtils.toByteArray(stream);
	}

	public byte[] getClass(String className) throws IOException {

		className = this.getOriginalClassName(className);
		if (className == null) {
			return null;
		}

		byte[] classBytes = this.getOriginalClassBytes(className);
		ClassReader reader = new ClassReader(classBytes);
		ClassWriter writer = this.getClassWriter(ClassWriter.COMPUTE_MAXS);
		ClassVisitor visitor = writer;

		HashMap<Object, String> namesMap = new HashMap<Object, String>();
		HashMap<String, Object> objectsMap = new HashMap<String, Object>();

		visitor = this.addPostTransformer(visitor);

		visitor = new ReplaceUniqueByOriginalNames(namesMap, objectsMap, this.namespace, visitor);

		visitor = this.addTransformers(visitor, objectsMap);

		visitor = new ReplaceOriginalNamesByUnique(namesMap, objectsMap, this.namespace, visitor);

		visitor = this.addPreTransformer(visitor);

		reader.accept(visitor, ClassReader.SKIP_FRAMES);

		return writer.toByteArray();
	}

	protected ClassVisitor addPreTransformer(ClassVisitor visitor) {
		return visitor;
	}

	protected ClassVisitor addPostTransformer(ClassVisitor visitor) {
		return visitor;
	}

	protected ClassWriter getClassWriter(int flags) {
		return new ClassWriter(flags);
	}

	protected ClassVisitor addTransformers(
			ClassVisitor visitor,
			HashMap<String, Object> objectsMap) {

		visitor = new AddForwardField(objectsMap, this.namespace, visitor);
		visitor = this.factory.getAddGettersAndSetters(objectsMap, this.namespace, visitor);
		visitor = new RedirectFieldManipulation(objectsMap, this.namespace, visitor);
		visitor = new DecreaseClassMethodsProtection(visitor);

		return visitor;
	}

	protected abstract String getOriginalClassName(String className);

	protected abstract void analyzeClass(byte[] classBytes);
}
