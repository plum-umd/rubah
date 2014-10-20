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

import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import rubah.RubahThread;
import rubah.org.apache.commons.collections.map.HashedMap;
import rubah.runtime.state.strategy.MigrationStrategy;

public class UpdateState {
	private Map<RubahThread, RubahThread> running;
	private Installer installer;
	private Map<String, ClassRedefinition> redefinitions;
	private Set<StoppedThread> stopped = new HashSet<StoppedThread>();
	private Set<StoppedThreadPool> stoppedThreadPool = new HashSet<StoppedThreadPool>();

	private Options options;
	private States states;
	private MigrationStrategy strategy;
	private long updateTime;
	private HashedMap offsets =  new HashedMap();
	private HashedMap staticOffsets = new HashedMap();
	private HashedMap staticBases = new HashedMap();


	public Map<RubahThread, RubahThread> getRunning() {
		return running;
	}
	public void setRunning(Map<RubahThread, RubahThread> running) {
		this.running = running;
	}
	public Installer getInstaller() {
		return installer;
	}
	public void setInstaller(Installer installer) {
		this.installer = installer;
	}
	public Map<String, ClassRedefinition> getRedefinitions() {
		return redefinitions;
	}
	public void setRedefinitions(Map<String, ClassRedefinition> redefinitions) {
		this.redefinitions = redefinitions;
	}
	public Set<StoppedThread> getStopped() {
		return stopped;
	}
	public void setStopped(Set<StoppedThread> stopped) {
		this.stopped = stopped;
	}
	public Options getOptions() {
		return options;
	}
	public void setOptions(Options options) {
		this.options = options;
	}
	public UpdateState clear(boolean isLazy) {
		this.installer = null;
		this.redefinitions = new HashMap<String, ClassRedefinition>();
		this.stopped.clear();
		this.options = new Options();
		this.updateTime = 0;
		this.offsets =  new HashedMap();
		this.staticOffsets = new HashedMap();
		this.staticBases = new HashedMap();
		if (!isLazy)
			this.strategy = null;
		return this;
	}
	public void printRunningThreads() {
		String runningThreads;
		while (true) {
			try {
				runningThreads = this.running.toString();
				break;
			} catch (ConcurrentModificationException e) {
				continue;
			}
		}

		System.out.println(runningThreads);
	}
	public States getStates() {
		return this.states;
	}
	public void setStates(States states) {
		this.states = states;
	}
	public void setLazyStrategy(MigrationStrategy strategy) {
		this.strategy = strategy;
	}
	public MigrationStrategy getLazyStrategy() {
		return this.strategy;
	}
	public long getUpdateTime() {
		return updateTime;
	}
	public void setUpdateTime(long updateTime) {
		this.updateTime = updateTime;
	}
	public HashedMap getOffsets() {
		return this.offsets;
	}
	public HashedMap getStaticOffsets() {
		return this.staticOffsets;
	}
	public HashedMap getStaticBases() {
		return this.staticBases;
	}

	public static class StoppedThreadPool {
		public final AtomicInteger count = new AtomicInteger();
		public final String threadName;

		public StoppedThreadPool(String threadName) {
			this.threadName = threadName;
		}
	}

	public static class StoppedThread {
		public final RubahThread rubahThread;
		public final Thread thread;
		public final String updatePoint;
		public final String threadName;

		public StoppedThread(Thread thread, RubahThread rubahThread, String updatePoint,
				String threadName) {
			this.rubahThread = rubahThread;
			this.thread = thread;
			this.updatePoint = updatePoint;
			this.threadName = threadName;
		}
	}

	public Set<StoppedThreadPool> getStoppedThreadPool() {
		return stoppedThreadPool;
	}
}
