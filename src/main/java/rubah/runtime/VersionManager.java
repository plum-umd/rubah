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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;

import rubah.framework.Clazz;
import rubah.framework.Namespace;
import rubah.runtime.classloader.FallbackLoader;
import rubah.runtime.classloader.TransformerFactory;
import rubah.runtime.classloader.VersionLoader;
import rubah.runtime.state.Options;
import rubah.tools.UpdatableJarAnalyzer;
import rubah.tools.UpdatableJarAnalyzer.VersionDescriptor;
import rubah.update.UpdateClass;
import rubah.update.V0V0UpdateClass;

public final class VersionManager {
	private static Namespace defaultNamespace = new Namespace();
	private static VersionManager instance;

	public static Namespace getDefaultNamespace() {
		return defaultNamespace;
	}

	public static VersionManager getInstance() {
		if (instance == null) {
			instance = new VersionManager();
		}
		return instance;
	}

	public static int runningVersionNumber;
	private Version runningVersion;
	private LinkedList<Version> versions = new LinkedList<Version>();
	private Map<Version, byte[]> descriptors = new HashMap<Version, byte[]>();
	private Map<Version, File> jarFiles = new HashMap<Version, File>();
	private Map<Version, UpdateClass> updateClasses = new HashMap<Version, UpdateClass>();

	private Set<String> currentVersionClassNames = new HashSet<>();
	private Set<String> outdatedClassNames = new HashSet<>();

	private VersionManager() { /* Empty */ }

	public void installV0V0(Options options) throws IOException {
		Version v0 = this.getLatestVersion();
		Version v1 = this.createNextVersion(this.descriptors.get(v0));

		// Compute program update
		v1.computeV0V0Update(options.isLazy());

		this.versions.addFirst(v1);
		this.descriptors.put(v1, this.descriptors.get(v0));
		this.jarFiles.put(v1, this.jarFiles.get(v0));
		this.updateClasses.put(v1, (options.getUpdateClass() == null ? new V0V0UpdateClass() : options.getUpdateClass()));

		this.outdatedClassNames.addAll(this.currentVersionClassNames);
		this.currentVersionClassNames = new HashSet<>();

		for (Clazz c1 : v1.getNamespace().getDefinedClasses()) {
			String updatableName = v1.getUpdatableName(c1.getFqn());
			this.currentVersionClassNames.add(updatableName);
		}
	}

	public void installVersion(Options options) throws IOException {
		byte[] descriptorBytes = IOUtils.toByteArray(new FileInputStream(options.getUpdateDescriptor()));
		Version v1 = this.createNextVersion(descriptorBytes);

		this.versions.addFirst(v1);
		this.descriptors.put(v1, descriptorBytes);
		this.jarFiles.put(v1, options.getJar());
		this.updateClasses.put(v1, options.getUpdateClass());

		// Compute program update
		v1.computeProgramUpdate(options.getUpdateClass(), options.isLazy());

		this.outdatedClassNames.addAll(this.currentVersionClassNames);
		this.currentVersionClassNames = new HashSet<>();

		for (Clazz c1 : v1.getNamespace().getDefinedClasses()) {
			String updatableName = v1.getUpdatableName(c1.getFqn());
			this.currentVersionClassNames.add(updatableName);
		}
	}

	private Version createNextVersion(byte[] descriptorBytes) throws IOException {
		VersionDescriptor descriptor =
				UpdatableJarAnalyzer.readFile(descriptorBytes, defaultNamespace);

		Version v1;
		int number = 0;
		if (!this.versions.isEmpty()) {
			number = this.versions.getFirst().getNumber() + 1;
			Version prevVersion = this.versions.getFirst();
//			v1 = new Version(number-1, descriptor, prevVersion);
			v1 = new Version(number, descriptor, prevVersion);
		} else {
			v1 = new Version(number, descriptor, null);
		}

		return v1;
	}

	public Version getLatestVersion() {
		return this.versions.getFirst();
	}

	public List<Version> getVersions() {
		return this.versions;
	}

	public byte[] getDescriptorBytes(Version v) {
		return this.descriptors.get(v);
	}

	public File getJarFile(Version v) {
		return this.jarFiles.get(v);
	}

	public byte[] getClassBytes(String className) throws IOException {
		return this.getClassBytes(className, this.runningVersion);
	}

	public byte[] getClassBytes(String className, TransformerFactory factory) throws IOException {
		return this.getClassBytes(className, this.runningVersion, factory);
	}

	public byte[] getClassBytes(String className, Version version) throws IOException {
		return this.getClassBytes(className, version, new TransformerFactory());
	}

	public byte[] getClassBytes(String className, Version version, TransformerFactory factory) throws IOException {
		byte[] ret;

		if (this.currentVersionClassNames.contains(className)) {
			// Up-to-date class
			VersionLoader loader = new VersionLoader(
					version,
					this.jarFiles.get(version),
					factory);
			ret = loader.getClass(className);
		} else if (this.outdatedClassNames.contains(className)) {
			// Outdated class
			for (Version v : this.versions) {
				String originalName = v.getOriginalName(className);

				if (originalName == null)
					continue;

				if (v.getUpdatableName(originalName).equals(className)) {
					VersionLoader loader = new VersionLoader(
							v,
							this.jarFiles.get(v),
							factory);
					ret = loader.getClass(className);
					return ret;
				}
			}
			throw new Error("Unknown outdated class (should never happen): " + className);
		} else {
			// Non-updatable class
			ret = new FallbackLoader(this.versions.getFirst(), factory).getClass(className);
		}

		return ret;
	}

	public UpdateClass getUpdateClass(Version version) {
		return this.updateClasses.get(version);
	}

	public void setRunningVersion() {
		this.runningVersion = this.versions.getFirst();
		runningVersionNumber = this.runningVersion.getNumber();
	}

	public Version getRunningVersion() {
		return this.runningVersion;
	}
}
