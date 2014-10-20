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
import java.util.LinkedList;
import java.util.Set;

import org.objectweb.asm.Opcodes;

import rubah.Rubah;
import rubah.framework.Clazz;
import rubah.runtime.Version;
import rubah.runtime.VersionManager;
import rubah.runtime.classloader.RubahClassloader;
import rubah.runtime.state.migrator.UnsafeUtils;

public class ComputingUpdateMetadata extends RubahState implements Opcodes {
	private static final String UPDATE_PACKAGE_DUMP_FILE_PROPERTY = "dumpUpdatePackage";
	private static final File UPDATE_PACKAGE_DUMP_FILE;

	static {
		String fileName = System.getProperty(UPDATE_PACKAGE_DUMP_FILE_PROPERTY);
		if (fileName != null)
			UPDATE_PACKAGE_DUMP_FILE = new File(fileName);
		else
			UPDATE_PACKAGE_DUMP_FILE = null;
	}

	public ComputingUpdateMetadata(UpdateState state) {
		super(state);
	}

	@Override
	public void doStart() {
		long time = System.currentTimeMillis();
		Version v1 = VersionManager.getInstance().getLatestVersion();

		LinkedList<Class<?>> loadedClasses = null;
		synchronized (RubahClassloader.class) {
			loadedClasses = new LinkedList<Class<?>>(RubahClassloader.getLoadedClasses());
		}

//		Map<String, ClassRedefinition> redefinitions = null;
//		File updatePackage = this.state.getOptions().getUpdatePackage();
//		ObjectInputStream ois = null;
//
//		try {
//			if (updatePackage != null) {
//				ois = new ObjectInputStream(new FileInputStream(updatePackage));
//				redefinitions = (Map<String, ClassRedefinition>) ois.readObject();
//			}
//		} catch (FileNotFoundException e) {
//			throw new Error(e);
//		} catch (IOException e) {
//			throw new Error(e);
//		} catch (ClassNotFoundException e) {
//			throw new Error(e);
//		} finally {
//			try {
//				if (ois != null)
//					ois.close();
//			} catch (IOException e) {
//				// Don't care
//			}
//		}

//		if (redefinitions == null) {
//			ObjectOutputStream oos = null;
//			try {
//				redefinitions = computeBytecode(v1, loadedClasses);
//
//				if (UPDATE_PACKAGE_DUMP_FILE != null) {
//					oos = new ObjectOutputStream(new FileOutputStream(UPDATE_PACKAGE_DUMP_FILE));
//					oos.writeObject(redefinitions);
//				}
//
//			} catch (IOException e) {
//				throw new Error(e);
//			} finally {
//				if (oos != null)
//					try {
//						oos.close();
//					} catch (IOException e) {
//						// Don't care
//					}
//			}
//		}
//
//		state.setRedefinitions(redefinitions);

		for (Class<?> c0 : loadedClasses) {
			String originalName = v1.getOriginalName(c0.getName());

			if (originalName == null)
				continue;

			Class<?> c1;
			try {
				c1 = Class.forName(v1.getUpdatableName(originalName), false, Rubah.getLoader());
			} catch (ClassNotFoundException e) {
				throw new Error(e);
			}

			UnsafeUtils.getInstance().setOffsets(c0);
			UnsafeUtils.getInstance().setOffsets(c1);
		}

		time = System.currentTimeMillis() - time;
		System.out.println("Time spent computing update metadata " + time + "ms");
	}

	@Override
	public void update(String updatePoint) {
		/* Empty */
	}

	@Override
	public boolean isUpdating() {
		return false;
	}

	@Override
	public boolean isUpdateRequested() {
		return false;
	}

	private static void computeInterestingAndFailfastClasses(Set<String> interestingClassNames, Set<String> failfastClassNames, Version version) {
		for (Clazz c1 : version.getNamespace().getDefinedClasses()) {

			for (Version v = version; v != null ; v = v.getPrevious()) {
				Clazz c0 = v.getUpdate().getV0(c1);

				if (c0 == null) {
					// Class introduced in version v
					interestingClassNames.add(v.getUpdatableName(c1.getFqn()));
					break;
				}

				if (v == version && v.getUpdate().isUpdated(c0)) {
					failfastClassNames.add(v.getPrevious().getUpdatableName(c1.getFqn()));
					interestingClassNames.add(v.getUpdatableName(c1.getFqn()));
					break;
				}

				if (v.getPrevious() == null) {
					interestingClassNames.add(v.getUpdatableName(c1.getFqn()));
					break;
				}
			}
		}
	}

//	public Map<String, ClassRedefinition> computeBytecode(Version version, Collection<Class<?>> loadedClasses) throws IOException {
//		Set<String> interestingClassNames = new HashSet<String>();
//		Set<String> failfastClassNames = new HashSet<String>();
//
//		computeInterestingAndFailfastClasses(interestingClassNames, failfastClassNames, version);
//
//		Map<String, ClassRedefinition> redefinitions = new HashMap<String, ClassRedefinition>();
//
//		for (Class<?> loadedClass : loadedClasses) {
//			String className = loadedClass.getName();
//			byte[] scaffoldedBytecodes = null;
//			byte[] newBytecodes = null;
//			boolean classChanged = false;
//			boolean isFailfast = false;
//
//			if (!AddTraverseMethod.isAllowed(className) || loadedClass.equals(Object.class))
//				continue;
//
//			if (className.startsWith("org.apache.commons.io")) {
//				// Loaded by Rubah and bypassed bytecode processing, disregard
//				continue;
//			}
//
//			String originalName = version.getOriginalName(className);
//
//			if (originalName == null) {
//				// Non-updatable, update refs to old code
//
//				GetMostRecentUsedFactory f = this.getMostRecentUsedFactory();
//				scaffoldedBytecodes = UpdateManager.getInstance().getClassBytes(className, version, f);
//
//				classChanged = (f.mostRecentVersionUsed == UpdateManager.getInstance().getLatestVersion());
//				if (classChanged)
//					newBytecodes = UpdateManager.getInstance().getClassBytes(className, version);
//
//			} else if (failfastClassNames.contains(className)) {
//					// TODO: Compute failfast bytecode
//					classChanged = true;
//					isFailfast = true;
//			}
//
//			ClassRedefinition bytecode = new ClassRedefinition(scaffoldedBytecodes, newBytecodes, isFailfast, classChanged);
//			redefinitions.put(className, bytecode);
//		}
//
//		for (String className : interestingClassNames) {
//			GetMostRecentUsedFactory f = this.getMostRecentUsedFactory();
//			byte[] scaffoldedBytecodes = UpdateManager.getInstance().getClassBytes(className, version, f);
//			boolean classChanged = (f.mostRecentVersionUsed == UpdateManager.getInstance().getLatestVersion());
//			byte[] newBytecodes = UpdateManager.getInstance().getClassBytes(className, version);
//			ClassRedefinition bytecode = new ClassRedefinition(scaffoldedBytecodes, newBytecodes, false, classChanged);
//			redefinitions.put(className, bytecode);
//		}
//
//		return redefinitions;
//	}
//
//	protected GetMostRecentUsedFactory getMostRecentUsedFactory() {
//		return new GetMostRecentUsedFactory();
//	}
//
//	protected class GetMostRecentUsedFactory extends TransformerFactory {
//		protected Version mostRecentVersionUsed;
//
//		@Override
//		public ClassVisitor getUpdatableClassRenamer(
//				HashMap<String, Object> objectsMap,
//				Version version,
//				Namespace namespace,
//				ClassVisitor visitor) {
//			return new UpdatableClassRenamerVersionGetter(version, objectsMap, namespace, visitor);
//		}
//
//		protected void registerVersion(Type type) {
//			Version v = UpdateManager.getInstance().getIntroducedVersion(type.getClassName());
//
//			if (this.mostRecentVersionUsed == null || v.getNumber() > this.mostRecentVersionUsed.getNumber()) {
//				this.mostRecentVersionUsed = v;
//			}
//		}
//
//		protected class UpdatableClassRenamerVersionGetter extends UpdatableClassRenamer {
//			public UpdatableClassRenamerVersionGetter(Version version,
//					HashMap<String, Object> objectsMap, Namespace namespace,
//					ClassVisitor cv) {
//				super(version, objectsMap, namespace, cv);
//			}
//
//			@Override
//			protected Type renameIfUpdatable(Type type) {
//				Type ret = super.renameIfUpdatable(type);
//
//				if (ret != type)
//					registerVersion(ret);
//
//				return ret;
//			}
//		}
//	}
}
