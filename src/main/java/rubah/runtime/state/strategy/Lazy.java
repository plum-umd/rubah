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


public class Lazy extends ThreadPoolStrategy {
	private boolean firstWaitForFinish = false;
	private ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
	private boolean shutdown = false;
	

	public Lazy(MappingStrategy mapping) {
		super(mapping, 1);
	}
	
	@Override
	public void waitForFinish() {
		if (!this.firstWaitForFinish) {
			this.firstWaitForFinish = true;
			return;
		}

		super.waitForFinish();
	}
	
	@Override
	public Object migrate(Object obj) {
		this.readWriteLock.readLock().lock();;
		try {
			if (this.shutdown)
				return obj;
			else
				return super.migrate(obj);
		} finally {
			this.readWriteLock.readLock().unlock();
		}
	}

	@Override
	protected void beforeShutdown() {
		this.readWriteLock.writeLock().lock();
		try {
			this.shutdown = true;
		} finally {
			this.readWriteLock.writeLock().unlock();
		}
	}
}