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

import java.io.IOException;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import rubah.RubahException;
import rubah.RubahThread;
import rubah.runtime.classloader.RubahClassloader;
import rubah.runtime.state.Installer;
import rubah.runtime.state.NotUpdating;
import rubah.runtime.state.Options;
import rubah.runtime.state.RubahState;
import rubah.runtime.state.UpdateState;

public class RubahRuntime {
	private static RubahState state = new NotUpdating();
	private static RubahClassloader loader;
	private static ReadWriteLock lock = new ReentrantReadWriteLock();

	public static void changeState(RubahState newState) {
		if (newState != null) {
			lock.writeLock().lock();
			try {
				state = newState;
			} finally {
				lock.writeLock().unlock();
			}
			System.out.println("Changing state to " + newState);
			changeState(state.start());
		}
	}

	public static RubahState getState() {
		lock.readLock().lock();
		try {
			return state;
		} finally {
			lock.readLock().unlock();
		}
	}

	// Not synchronized but concurrent.
	// However, missing an update point is benign:
	// - Thread is running, it will reach the update point again in the future
	// - Thread is blocked, it was interrupted and that creates an happens-before
	// - Thread is running but will block immediately after:
	//  - To block, must acquire monitor, which creates an happens-before
	//  - Then, must query current state about a possible requested update
	//  - So, the blocking call fails and the thread reaches the next update point
	public static void update(String updatePoint) {
		lock.readLock().lock();
		try {
			state.update(updatePoint);
		} finally {
			lock.readLock().unlock();
		}
//		// This yield helps breaking tight loops calling update,
//		// which may never see the state changing due to it being cached
//		Thread.yield();
	}

	public static void installNewVersion(final Options options, final Installer installer) {
		changeState(state.installUpdate(installer, options));
	}

	public static void registerRunningThread(RubahThread t) {
		lock.readLock().lock();
		try {
			state.registerRunningThread(t);
		} finally {
			lock.readLock().unlock();
		}
	}

	public static void deregisterRunningThread(RubahThread t) {
		deregisterRunningThread(t, null);
	}

	public static void deregisterRunningThread(
			RubahThread t, RubahException e) {
		lock.readLock().lock();
		try {
			state.deregisterRunningThread(t, e);
		} finally {
			lock.readLock().unlock();
		}
	}

	public static boolean isUpdateRequested() {
		lock.readLock().lock();
		try {
			return state.isUpdateRequested();
		} finally {
			lock.readLock().unlock();
		}
	}

	public static boolean isUpdating() {
		lock.readLock().lock();
		try {
			return state.isUpdating();
		} finally {
			lock.readLock().unlock();
		}
	}

	public static void setRubahClassloader(RubahClassloader rubahloader) {
		loader = rubahloader;
	}

	public static RubahClassloader getLoader() {
		return loader ;
	}

	public static void redirectThreadAfterUpdate(RubahThread t0, RubahThread t1) {
		state.redirectThread(t0, t1);
	}

	public static void ensureStaticFieldsMigrated(Class<?> c) {
		state.ensureStaticFieldsMigrated(c);
	}

	public static Object getConverted(Object pre) {
		Object ret = state.getConverted(pre);
		return ret;
	}

	public static byte[] getClassBytes(String className) throws IOException {
		return state.getClassBytes(className);
	}

	public static void observeState(UpdateState.Observer observer) {
		changeState(state.observeState(observer));
	}
}
