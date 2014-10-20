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
import java.util.concurrent.ForkJoinPool;

public class ForkJoinStrategy extends ExecutorStrategy {

	public ForkJoinStrategy(MappingStrategy mapping, int nThreads) {
		super(mapping, nThreads);
	}

	@Override
	protected ExecutorService getExecutor() {
		return new ForkJoinPool(this.nThreads);
	}

	@Override
	public String toString() {
		ForkJoinPool pool = (ForkJoinPool)this.executor;
		return pool.getQueuedTaskCount()+"\t"+pool.getStealCount();
	}
}