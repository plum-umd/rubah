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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ThreadPoolStrategy extends ExecutorStrategy {
	private transient LinkedBlockingQueue<Runnable> queue;

	public ThreadPoolStrategy(MappingStrategy mapping, int nThreads) {
		super(mapping, nThreads);
	}

	@Override
	protected ExecutorService getExecutor() {
		this.queue = new LinkedBlockingQueue<Runnable>();
		return new ThreadPoolExecutor(
				this.nThreads,
				this.nThreads,
				0L, TimeUnit.MILLISECONDS, this.queue);
	}

	@Override
	public String toString() {
		return "" + this.queue.size();
	}
}