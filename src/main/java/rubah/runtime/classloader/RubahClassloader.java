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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.commons.io.IOUtils;
import org.objectweb.asm.Opcodes;

import rubah.Rubah;
import rubah.bytecode.transformers.AddHashCodeField;
import rubah.bytecode.transformers.AddTraverseMethod;
import rubah.bytecode.transformers.ProxyGenerator;
import rubah.framework.Clazz;
import rubah.framework.Type;
import rubah.runtime.Version;
import rubah.runtime.VersionManager;
import rubah.runtime.state.migrator.UnsafeUtils;
import rubah.update.UpdateClass;
import sun.misc.Unsafe;


public class RubahClassloader extends ClassLoader implements Opcodes {
	// Package names of classes that might have interesting state before Rubah class gets loaded
	private static final Set<String> INTERESTING_PACKAGE_NAMES =
			new HashSet<String>(Arrays.asList(new String[]{
					HashSet.class.getPackage().getName(),
					ConcurrentHashMap.class.getPackage().getName(),
					"sun.nio.ch",
			}));

	private static final Unsafe unsafe = UnsafeUtils.getUnsafe();
	private static final Class<?> classClass = Class.class;
	private static final long hashCodeFieldOffset = getClassHashCodeOffset();

	private static long getClassHashCodeOffset() {
		Field hashCodeField;
		try {
			hashCodeField = classClass.getDeclaredField(AddHashCodeField.FIELD_NAME);
		} catch (NoSuchFieldException e) {
			// No $hashCode, continue
			return Unsafe.INVALID_FIELD_OFFSET;
		} catch (SecurityException e) {
			// No $hashCode, continue
			return Unsafe.INVALID_FIELD_OFFSET;
		}
		return unsafe.objectFieldOffset(hashCodeField);
	}

	private Map<String, Boolean> resolved = new HashMap<>();

	private static LinkedList<Class<?>> loadedClasses = new LinkedList<Class<?>>();
	public static LinkedList<Class<?>> getLoadedClasses() {
		return loadedClasses;
	}

	private static HashSet<String> loadedClassNames = new HashSet<String>();
	public static HashSet<String> getLoadedClassNames() {
		return loadedClassNames;
	}

	private static HashSet<Class<?>> redefinableClasses = new HashSet<Class<?>>();
	public static HashSet<Class<?>> getRedefinableClasses() {

		while(true) {
			try {
				return new HashSet<Class<?>>(redefinableClasses);
			} catch(ConcurrentModificationException e) {
				continue;
			}
		}
	}

	private static void setHashCode(Class<?> c) {
		setHashCode(c, System.identityHashCode(c));
	}

	private static void setHashCode(Class<?> c, int hashCode) {
		unsafe.putInt(c, hashCodeFieldOffset, hashCode);
	}

	public RubahClassloader(ClassLoader parent) {
		super(parent);
	}

	@Override
	protected synchronized Class<?> loadClass(String className, boolean resolve) throws ClassNotFoundException {
		byte[] classBytes = null;
		VersionManager updateManager = VersionManager.getInstance();

		Class<?> ret = findLoadedClass(className);

		if (ret != null) {
			// Do nothing
		} else if (className.startsWith("rubah.") ||
				className.startsWith("java") ||
//				className.startsWith("com.sun.jna") ||
				className.startsWith("org.xml.sax") ||
				className.startsWith("sun")) {
			ret = super.loadClass(className, resolve);
			resolve = false;
		} else if (className.startsWith(PureConversionClassLoader.PURE_CONVERSION_PREFFIX)) {
			try {
				Version version = updateManager.getRunningVersion();
				UpdateClass updateClass = updateManager.getUpdateClass(version);
				classBytes = new PureConversionClassLoader(version, updateClass).getClass(className);
				writeClassFile(className, classBytes);
				ret = this.defineClass(className, classBytes, 0, classBytes.length);
//				return ret;
			} catch (IOException e) {
				throw new Error(e);
			}
		} else if (ProxyGenerator.isProxyName(className)) {
			Version latest = updateManager.getLatestVersion();
			String proxiedName = ProxyGenerator.getOriginalName(className);
			String originalName = latest.getOriginalName(proxiedName);
			originalName = (originalName == null ? proxiedName : originalName);
			Clazz c = latest.getNamespace().getClass(Type.getObjectType(originalName.replace('.', '/')));
			classBytes = new ProxyGenerator(c, updateManager.getRunningVersion()).generateProxy(className);
			writeClassFile(className, classBytes);
			ret = this.defineClass(className, classBytes, 0, classBytes.length);
		} else {

			try {
				classBytes = Rubah.getClassBytes(className);
				writeClassFile(className, classBytes);
				ret = this.defineClass(className, classBytes, 0, classBytes.length);
				redefinableClasses.add(ret);
			} catch (IOException e) {
				throw new ClassNotFoundException();
			}

			Version latest = VersionManager.getInstance().getLatestVersion();
			Version prev = latest.getPrevious();

			if (prev != null) {
				String prevName = prev.getUpdatableName(latest.getOriginalName(className));
				Class<?> prevClass = this.findLoadedClass(prevName);

				if (prevClass != null) {
					setHashCode(ret, prevClass.hashCode());
				}
			}
		}

		if (AddTraverseMethod.isAllowed(className)) {
			loadedClasses.add(ret);
			loadedClassNames.add(ret.getName());
		}

		if (resolve)
			this.resolveClass(ret);

		if (ret.hashCode() == 0)
			setHashCode(ret);

		Boolean isResolved = this.resolved.get(ret.getName());
		if (isResolved == null)
			this.resolved.put(ret.getName(), false);

		return ret;
	}

	public boolean isResolved(String s) {
		Boolean ret = resolved.get(s);

		if (ret == null)
			return false;

		return ret;
	}

	private static void writeClassFile(String className, byte[] classBytes) {
		FileOutputStream fos = null;
		try {
			File f = new File("/tmp/rubah/"+className.replace('.', '/'));
			f.mkdirs();
			f = new File("/tmp/rubah/"+className.replace('.', '/')+".class");
			fos = new FileOutputStream(f);
			IOUtils.write(classBytes, fos);
		} catch (IOException e) {
			throw new Error(e);
		} finally {
			if (fos != null)
				try {
					fos.close();
				} catch (IOException e) {
					// Don't care
				}
		}

	}


	public byte[] getResourceByName(String name) throws IOException {

		VersionManager manager = VersionManager.getInstance();

		for (Version version : manager.getVersions()) {
			byte[] ret;
			try {
				ret = new VersionLoader(version, manager.getJarFile(version), new TransformerFactory()).getResource(name);
			} catch (IOException e) {
				continue;
			}

			if (ret != null) {
				return ret;
			}
		}

		throw new IOException();
	}

	@Override
	public InputStream getResourceAsStream(String name) {
		JarFile jarFile = null;
		try {
			VersionManager u = VersionManager.getInstance();
			Version v = u.getRunningVersion();
			File f = VersionManager.getInstance().getJarFile(v);
			jarFile = new JarFile(f);
			JarEntry entry = jarFile.getJarEntry(name);

			if (entry != null) {
				try {
					byte[] ret = IOUtils.toByteArray(jarFile.getInputStream(entry));
					return new ByteArrayInputStream(ret);
				} catch (IOException e) {
					throw new Error(e);
				}
			}

		} catch (IOException e) {
			throw new Error(e);
		} finally {
			if (jarFile != null)
				try {
					jarFile.close();
				} catch (IOException e) {
					// Don't care
				}
		}

		return super.getResourceAsStream(name);
	}

	public synchronized void registerResolved(Class<?> c) {
		this.resolved.put(c.getName(), true);
	}

}
