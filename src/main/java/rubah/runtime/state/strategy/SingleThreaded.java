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
package rubah.runtime.state.strategy;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.javatuples.Pair;

import rubah.framework.Clazz;
import rubah.runtime.Version;
import rubah.runtime.VersionManager;
import rubah.runtime.state.MigratingProgramState;
import rubah.runtime.state.migrator.ArrayMigratorFactory;
import rubah.runtime.state.migrator.BlackListMigratorFactory;
import rubah.runtime.state.migrator.DefaultObjectMigratorFactory;
import rubah.runtime.state.migrator.MigratorFactory;
import rubah.runtime.state.migrator.MigratorSubFactory;
import rubah.runtime.state.migrator.OutdatedClassMigratorFactory;
import rubah.runtime.state.migrator.OutdatedEnumMigratorFactory;
import rubah.runtime.state.migrator.ReferenceMigratorFactory;
import rubah.runtime.state.migrator.ReflectionMigratorFactory;
import rubah.runtime.state.migrator.StaticFieldsMigratorFactory;
import rubah.runtime.state.migrator.UpdatableObjectMigratorFactory;
import rubah.runtime.state.migrator.MigratorSubFactory.Migrator;

public class SingleThreaded implements MigrationStrategy {

	protected transient MigratingProgramState state;
	protected MappingStrategy mapping;
	protected transient Set<String> outdatedClasses, transformedClasses;

	protected transient MigratorFactory migratorFactory;

	public SingleThreaded(MappingStrategy mapping) {
		this.mapping = mapping;
	}

	@Override
	public void migrate(Object fromBase, long fromOffset, Object toBase, long toOffset) {
		Object fromObj = unsafe.getObject(fromBase, fromOffset);
		Object toObj = unsafe.getObject(toBase, toOffset);

		if (fromObj == null)
			return;

		// Has this object been converted already?
		Object ret = this.mapping.get(fromObj);
		if (ret != null) {
			unsafe.compareAndSwapObject(toBase, toOffset, toObj, ret);
			return;
		}

		this.doMigrate(fromBase, fromOffset, fromObj, toBase, toOffset, toObj);
	}

	protected void doMigrate(Object fromBase, long fromOffset, Object fromObj, Object toBase, long toOffset, Object toObj) {
		Object ret = this.migrate(fromObj);
		if (fromObj != ret)
			if (!unsafe.compareAndSwapObject(toBase, toOffset, toObj, ret)) {
				toObj = unsafe.getObject(toBase, toOffset);
				ret = this.migrate(toObj);
				if (toObj != ret)
					throw new Error("CAS failed and object not migrated, should not happen...");
			}

	}

	@Override
	public Object migrate(Object obj) {
		return this.baseMigrate(obj);
	}

	protected final Object baseMigrate(Object obj) {
		if (obj == null) {
			return null;
		}

		// Has this object been converted already?
		Object ret = this.mapping.get(obj);
		if (ret != null) {
			return ret;
		}

		Class<?> c = obj.getClass();

		Migrator m = this.migratorFactory.getMigrator(c);
		ret = m.migrate(obj);
		Object post = m.registerMapping(obj, ret);
		if (post != ret)
			return post;

		m.followReferences(ret);

		return ret;
	}

	@Override
	public void waitForFinish() {
		return;
	}

	@Override
	public MappingStrategy getMapping() {
		return this.mapping;
	}

	@Override
	public MigrationStrategy setState(MigratingProgramState state) {
		this.state = state;
		this.setUpMigrators();
		return this;
	}

	@Override
	public String getDescription() {
		return this.getClass().getSimpleName() + " - " + this.mapping.getClass().getSimpleName();
	}

	private void setUpMigrators() {
		this.outdatedClasses = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
		this.transformedClasses = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

		Version v1 = VersionManager.getInstance().getLatestVersion();
		Version v0 = v1.getPrevious();

		if (v0 != null) {
			for (Clazz c0 : v0.getNamespace().getDefinedClasses()) {
				this.outdatedClasses.add(v0.getUpdatableName(c0.getFqn()));
				if (v1.getUpdate().isConverted(c0)) {
					this.transformedClasses.add(v0.getUpdatableName(c0.getFqn()));
				}
			}
		}

		this.migratorFactory = new MigratorFactory(this.getMigrators(v1));
		this.mapping.setUpdatedClassNames(this.outdatedClasses);
	}

	protected MigratorSubFactory[] getMigrators(Version v1) {
		return new MigratorSubFactory[]{
				new BlackListMigratorFactory(this),
				new ReflectionMigratorFactory(this),
				new ReferenceMigratorFactory(this),
				new ArrayMigratorFactory(transformedClasses, outdatedClasses, v1, this),
				new UpdatableObjectMigratorFactory(transformedClasses, v1, this),
				new OutdatedEnumMigratorFactory(outdatedClasses, v1, this),
				new OutdatedClassMigratorFactory(outdatedClasses, v1, this),
				new DefaultObjectMigratorFactory(this),
		};
	}

	@Override
	public void migrateStaticFields(Collection<Class<?>> classes) {
		Version v1 = VersionManager.getInstance().getLatestVersion();
		LinkedList<Pair<String, Long>> times = new LinkedList<Pair<String,Long>>();
		MigratorSubFactory staticMigratorFactory = new StaticFieldsMigratorFactory(this, v1);

		for (Class<?> c : classes) {

			long time = System.currentTimeMillis();

			if (!staticMigratorFactory.canMigrate(c))
				continue;

			Migrator migrator = staticMigratorFactory.buildMigrator();
			Object newC = migrator.migrate(c);
			migrator.followReferences(newC);
			times.add(new Pair<String, Long>(c.getName(), (System.currentTimeMillis() - time)));
		}

		Collections.sort(times, new Comparator<Pair<String, Long>>() {
			@Override
			public int compare(Pair<String, Long> o1, Pair<String, Long> o2) {
				return o2.getValue1().compareTo(o1.getValue1());
			}
		});


		for (Pair<String, Long> t : times.subList(0, Math.min(times.size(), 10)))
			System.out.println("\t" + t.getValue1() + "\t" + t.getValue0());
	}

	protected void migrateStatic(Object base, long offset) {
		this.migrate(base, offset, base, offset);
	}

	@Override
	public long countMigrated() {
		return this.migratorFactory.countMigrated();
	}
}
