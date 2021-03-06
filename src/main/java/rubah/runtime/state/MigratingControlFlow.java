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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import rubah.runtime.state.UpdateState.StoppedThread;

public class MigratingControlFlow extends RubahState {
	private Map<Thread, String> migrating = new HashMap<>();

	private Lock stateLock = new ReentrantLock();
	private Condition migratingChanged = this.stateLock.newCondition();
	private Condition allMigrated = this.stateLock.newCondition();
	private boolean updating = true;

	public MigratingControlFlow(UpdateState state) {
		super(state);
	}

	@Override
	public void doStart() {
		System.out.println("Restarting threads");
		this.stateLock.lock();
		long time = System.currentTimeMillis();
		int nThreads = this.state.getStopped().size();
		try {
			// Restart stopped threads on new threads
			for (StoppedThread stoppedThread : this.state.getStopped()) {
				this.startThread(stoppedThread);
			}

			// Wait for control flow migration to finish
			while(!this.migrating.isEmpty()) {
				try {
					this.migratingChanged.await();
				} catch (InterruptedException e) {
					continue;
				}
			}

			System.out.println("Control migration finished, notifying threads");
			// Program updated, notify all threads to proceed executing the new version
			this.state.getStopped().clear();
			this.updating = false;
			this.allMigrated.signalAll();
		} finally {
			this.stateLock.unlock();
			time = System.currentTimeMillis() - time;
			System.out.println("Restarted " + nThreads + " threads in " + time + "ms");
		}
	}

	protected void startThread(StoppedThread stoppedThread) {
		Thread t = stoppedThread.thread;
		this.migrating.put(t, stoppedThread.updatePoint);
		stoppedThread.rubahThread.restart();
	}

	@Override
	public void update(String updatePoint) {
		Thread thisThread = Thread.currentThread();
		System.out.println("Thread " + thisThread + " reached update point \"" + updatePoint + "\"");

		if (updatePoint.equals(this.migrating.get(thisThread))) {
			this.stateLock.lock();
			try {
				this.migrating.remove(thisThread);
				this.migratingChanged.signalAll();
				while (this.updating) {
					try {
						this.allMigrated.await();
					} catch (InterruptedException e) {
						continue;
					}
				}
			} finally {
				this.stateLock.unlock();
			}
		}
		System.out.println("Thread " + thisThread + " released");
	}

	@Override
	public boolean isUpdating() {
		return this.updating;
	}

	@Override
	public boolean isUpdateRequested() {
		return false;
	}
}
