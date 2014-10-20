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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import rubah.RubahThread;
import rubah.runtime.Version;
import rubah.runtime.VersionManager;
import rubah.runtime.classloader.RubahClassloader;
import rubah.runtime.state.UpdateState.StoppedThread;
import rubah.runtime.state.migrator.MigratorSubFactory;
import rubah.runtime.state.migrator.StaticFieldsMigratorFactory;
import rubah.runtime.state.migrator.MigratorSubFactory.Migrator;
import rubah.runtime.state.strategy.MigrationStrategy;

public class MigratingProgramState extends RubahState {
	private final static int SAMPLE_TIME_MS = 1000;
	private final static String PRINT_CONVERTED_FILE = "printConversionsToFile";
	private final static File conversionsFile;
	private Set<Class<?>> migratedClasses = Collections.newSetFromMap(new ConcurrentHashMap<Class<?>, Boolean>());

	static {
		String fileName = System.getProperty(PRINT_CONVERTED_FILE);
		if (fileName != null) {
			conversionsFile = new File(fileName);
		} else {
			conversionsFile = null;
		}
	}

	private boolean stop = false;
	private Thread printingThread = new Thread(){
				@Override
				public void run() {
					try {
						BufferedWriter writer = new BufferedWriter(new FileWriter(conversionsFile));
						long conversions = strategy.countMigrated();

						while (true) {
							long time = System.currentTimeMillis();
							try {
								Thread.sleep(SAMPLE_TIME_MS);
							} catch (InterruptedException e) {
								continue;
							}
							time = System.currentTimeMillis() - time;

							long newConversions = strategy.countMigrated();
							int n = Math.max((int) Math.round(Math.floor(time/SAMPLE_TIME_MS)), 1);

							long convs = (newConversions-conversions)/n;
							for (int i = 0 ; i < n ; i++) {
								writer.write(""+convs+"\n");
							}

							writer.flush();

							conversions = newConversions;
							synchronized (this) {
								if (stop)
									break;
							}
						}

						writer.close();
					} catch (IOException e) {
						throw new Error(e);
					}
				}
			};

	private Map<RubahThread, RubahThread> redirectedThreads = new HashMap<>();

	protected MigrationStrategy strategy;

	public MigratingProgramState(UpdateState state) {
		super(state);
	}

	@Override
	public void doStart() {
		this.strategy = this.state.getOptions().getMigrationStrategy();
		this.strategy.setState(this);
		this.state.setLazyStrategy(this.strategy);

		if (conversionsFile != null) {
			this.printingThread.start();
		}

		System.out.println("Traversing the heap using strategy " + this.strategy.getDescription() + "...");

		long time = System.currentTimeMillis();
		LinkedList<Class<?>> loadedClasses = new LinkedList<>();
		synchronized (RubahClassloader.class) {
			loadedClasses = new LinkedList<Class<?>>(RubahClassloader.getLoadedClasses());
		}

		// Start traversing the heap
		try {
			this.migrateStaticFields(loadedClasses);

			// Threads
			for (StoppedThread stoppedThread : this.state.getStopped()) {
				Runnable r = (Runnable)this.strategy.migrate(stoppedThread.rubahThread.getTarget());
				stoppedThread.rubahThread.setTarget(r);
			}

			for (Entry<RubahThread, RubahThread> entry : this.redirectedThreads.entrySet()) {
				entry.getKey().setTarget(entry.getValue());
			}
		} catch (IllegalArgumentException e) {
			throw new Error(e);
		} catch (Error e) {
			e.printStackTrace();
			throw e;
		}

		this.strategy.waitForFinish();

		time = System.currentTimeMillis() - time;
		System.out.println(
				"Converted " + this.strategy.countMigrated() + " objects in " + time + "ms");
		if (this.shouldStopPrintingThread())
			this.stopPrintingThread();
	}

	private void migrateStaticFields(Collection<Class<?>> classes) {
		Version v1 = VersionManager.getInstance().getLatestVersion();
		MigratorSubFactory staticMigratorFactory = new StaticFieldsMigratorFactory(this.strategy, v1);

		for (Class<?> c : classes) {

			if (this.migratedClasses.contains(c))
				continue;

			this.migratedClasses.add(c);

			if (!staticMigratorFactory.canMigrate(c))
				continue;

			Migrator migrator = staticMigratorFactory.buildMigrator();
			Object newC = migrator.migrate(c);
			migrator.followReferences(newC);
		}
	}

	@Override
	public boolean isUpdating() {
		return true;
	}

	@Override
	public boolean isUpdateRequested() {
		return false;
	}

	@Override
	public void redirectThread(RubahThread t0, RubahThread t1) {
		this.redirectedThreads.put(t0, t1);
	}

	public UpdateState getState() {
		return this.state;
	}

	protected final void stopPrintingThread() {
		if (conversionsFile == null)
			return;

		synchronized (this.printingThread) {
			this.stop = true;
		}
	}

	protected boolean shouldStopPrintingThread() {
		return true;
	}

	@Override
	public void ensureStaticFieldsMigrated(Class<?> c) {
		this.migrateStaticFields(Arrays.asList(new Class<?>[]{ c }));
	}

}
