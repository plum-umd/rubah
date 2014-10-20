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

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class EagerLazy extends ThreadPoolStrategy {
	private ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
	private boolean shutdown = false;


	public EagerLazy(MappingStrategy mapping, int threads) {
		super(mapping, threads);
	}

	@Override
	public void waitForFinish() {
		super.waitForFinish();
		this.readWriteLock.writeLock().lock();
		try {
			this.shutdown = true;
		} finally {
			this.readWriteLock.writeLock().unlock();
		}
	}

	@Override
	public Object migrate(Object obj) {
		this.readWriteLock.readLock().lock();
		try {
			if (this.shutdown) {
				// Behave like FullyLazy
				return this.baseMigrate(obj);
			}
			else
				return super.migrate(obj);
		} finally {
			this.readWriteLock.readLock().unlock();
		}
	}

	@Override
	public void migrate(Object fromBase, long fromOffset, Object toBase, long toOffset) {
		this.readWriteLock.readLock().lock();
		try {
			if (this.shutdown) {
				// Behave like FullyLazy
				return;
			}
			else
				super.migrate(fromBase, fromOffset, toBase, toOffset);
		} finally {
			this.readWriteLock.readLock().unlock();
		}
		return;
	}
}