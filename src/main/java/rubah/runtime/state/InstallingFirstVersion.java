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
package rubah.runtime.state;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;

import rubah.Rubah;
import rubah.RubahException;
import rubah.RubahThread;
import rubah.runtime.Version;
import rubah.runtime.VersionManager;
import rubah.runtime.classloader.RubahClassloader;

public class InstallingFirstVersion extends InstallingNewVersion {
	private String originalClassName;
	private String[] args;

	public InstallingFirstVersion(final String className,
			final File updateDescriptor, final File jarFile,
			final String... args) {
		super(new UpdateState());
		this.state.setRunning(new HashMap<RubahThread, RubahThread>());
		this.originalClassName = className;
		this.args = args;
		try {
			VersionManager.getInstance().installVersion(
					new Options()
						.setUpdateDescriptor(updateDescriptor)
						.setJar(jarFile));
		} catch (IOException e) {
			throw new Error(e);
		}
	}

	@Override
	public RubahState start() {
		VersionManager.getInstance().setRunningVersion();
		// Use custom classloader to load class
		new RubahThread() {

			@Override
			public void rubahRun() {
				ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
				RubahClassloader loader = new RubahClassloader(contextClassLoader);
				Rubah.setRubahClassloader(loader);
				Thread.currentThread().setContextClassLoader(loader);

				Version version = VersionManager.getInstance()
						.getRunningVersion();

				String className = InstallingFirstVersion.this.originalClassName;
				className = version.getUpdatableName(className);

				if (className == null) {
					className = InstallingFirstVersion.this.originalClassName;
				}

				try {
					Class<?> mainClass = loader.loadClass(className);
					Method main = mainClass.getMethod("main",
							new Class[] { String[].class });
					main.invoke(null,
							new Object[] { InstallingFirstVersion.this.args });
				} catch (RubahException e) {
					throw e;
				} catch (IllegalArgumentException e) {
					throw new Error(e);
				} catch (SecurityException e) {
					throw new Error(e);
				} catch (IllegalAccessException e) {
					throw new Error(e);
				} catch (InvocationTargetException e) {
					throw new Error(e);
				} catch (NoSuchMethodException e) {
					throw new Error(e);
				} catch (ClassNotFoundException e) {
					throw new Error(e);
				}
			}
		}.start();

		return new NotUpdating(this.state);
	}
}
