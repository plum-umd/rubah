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
package rubah;

import rubah.runtime.RubahRuntime;


public class RubahThread extends Thread {
	private Runnable target;
	private boolean restart;

	public RubahThread() {
		this.target = this;
	}

	public RubahThread(RubahThread target) {
		super(target);
		this.target = target;
	}

	public RubahThread(Runnable r) {
		super(r);
		this.target = r;
	}

	@Override
	public void run() {
		boolean registered = false;
		while (!registered) {
			try {
				Rubah.registerRunningThread(this);
				registered = true;
				if (this.target instanceof RubahThread)
					((RubahThread)this.target).rubahRun();
				else
					this.target.run();
			} catch (RubahException e) {
				RubahRuntime.deregisterRunningThread(this, e);
				registered = false;
				restart = false;
				while (!restart) {
					synchronized (this) {
						try {
							this.wait();
						} catch (java.lang.InterruptedException e1) {
							continue;
						}
					}
				}
				continue;
			} catch (Throwable e) {
				e.printStackTrace();
				throw new Error(e);
			} finally {
				if (registered) {
					RubahRuntime.deregisterRunningThread(this);
				}
			}
		}

	}

	protected void rubahRun() {
		super.run();
	}

	@Override
	public String toString() {
		return "" + System.identityHashCode(this) + "(" + System.identityHashCode(this.target) + ")";
	}

	public Runnable getTarget() {
		return this.target;
	}

	public void setTarget(Runnable t) {
		this.target = t;
	}

	public void restart() {
		synchronized (this) {
			this.restart = true;
			this.notifyAll();
		}
	}
}
