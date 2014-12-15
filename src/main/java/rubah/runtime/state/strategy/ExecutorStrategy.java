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

import java.util.LinkedList;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import rubah.runtime.state.MigratingProgramState;

/*default*/ abstract class ExecutorStrategy extends SingleThreaded {

		private static class QueuedConversion {
			final Object 	from;
			final long 		fromOffset;
			final Object	to;
			final long		toOffset;

			public QueuedConversion(Object from, long fromOffset, Object to,
					long toOffset) {
				this.from 		= from;
				this.fromOffset = fromOffset;
				this.to 		= to;
				this.toOffset 	= toOffset;
			}


		}

		private transient ConcurrentLinkedDeque<QueuedConversion> queued = new ConcurrentLinkedDeque<>();
		protected transient StrippedCounter inFlight;
		protected transient ExecutorService executor;
		protected final int nThreads;

		public ExecutorStrategy(MappingStrategy mapping, int nThreads) {
			super(mapping);
			this.nThreads = nThreads;
		}

		@Override
		public MigrationStrategy setState(MigratingProgramState state) {
			this.queued = new ConcurrentLinkedDeque<>();
			this.inFlight = new StrippedCounter(this);
			return super.setState(state);
		}

		protected abstract ExecutorService getExecutor();

		@Override
		protected void migrateStatic(Object base, long offset) {
			super.migrate(base, offset, base, offset);
		}

		@Override
		public void migrate(final Object fromBase, long fromOffset, final Object toBase, long toOffset) {

			if (this.executor == null) {
				this.queued.add(new QueuedConversion(fromBase, fromOffset, toBase, toOffset));
				return;
			}

			this.inFlight.increment();
			final short fromOff = (short) fromOffset;
			final short toOff = (short) toOffset;
			this.executor.execute(new Runnable() {
				@Override
				public void run() {
					try {
						ExecutorStrategy.super.doMigrate(
								fromBase,
								fromOff,
								unsafe.getObject(fromBase, (long) fromOff),
								toBase,
								toOff,
								unsafe.getObject(toBase, (long) toOff));
					} catch (Throwable e) {
						System.out.println(e);
						e.printStackTrace();
						throw new Error(e);
					}
					inFlight.decrement();
				}
			});
		}

		protected void beforeShutdown() {
			// Empty
		}

		@Override
		public void waitForFinish() {

			this.executor = this.getExecutor();

			while (!this.queued.isEmpty()) {
				QueuedConversion q = this.queued.poll();
				this.migrate(q.from, q.fromOffset, q.to, q.toOffset);
			}

			synchronized (this) {
				while (this.inFlight.get() != 0) {
					try {
						this.wait();
					} catch (InterruptedException e) {
						continue;
					}
				}
			}

			this.beforeShutdown();

			this.executor.shutdown();

			boolean finished = false;

			do {
				try {
					finished = this.executor.awaitTermination(100, TimeUnit.MILLISECONDS);
				} catch (InterruptedException e) {
					continue;
				}
			} while (!finished);
		}

		@Override
		public String getDescription() {
			return super.getDescription() + " - " + this.nThreads + " threads";
		}

		private static class StrippedCounter {
			private static final int MIN_THRESHOLD = 50;
			private Object notify;
			private LinkedList<Strip> strips = new LinkedList<Strip>();
			private ThreadLocal<Strip> counter = new ThreadLocal<Strip>() {
				@Override
				protected Strip initialValue() {
					Strip ret = new Strip();
					synchronized (strips) {
						strips.add(ret);
					}
					return ret;
				}
			};

			public StrippedCounter(Object notify) {
				this.notify = notify;
			}

			public void increment() {
				Strip s = this.counter.get();
				s.lock.lock();
				try {
					s.value++;
				} finally {
					s.lock.unlock();
				}
			}

			public void decrement() {
				Strip s = this.counter.get();
				int newValue;
				s.lock.lock();
				try {
					newValue = --(s.value);
				} finally {
					s.lock.unlock();
				}

				if (newValue <= 0) {
					if (this.redistribute() == 0) {
						synchronized (notify) {
							notify.notifyAll();
						}
					}
				}
			}

			private int redistribute() {
				int ret = 0;

				synchronized (strips) {
					for (Strip strip : strips) {
						strip.lock.lock();
					}

					for (Strip strip : strips) {
						ret += strip.value;
						strip.value = 0;
					}

					if (ret < MIN_THRESHOLD) {
						this.counter.get().value = ret;
					} else {
						int newVal = ret / strips.size();
						int lastVal = ret - (newVal * (strips.size() - 1));
						for (Strip strip : strips) {
							if (strip == strips.getLast())
								strip.value = lastVal;
							else
								strip.value = newVal;
						}
					}

					for (Strip strip : strips) {
						strip.lock.unlock();
					}
				}

				return ret;
			}

			public int get() {
				int ret = 0;

				synchronized (strips) {
					for (Strip strip : strips) {
						strip.lock.lock();
					}

					for (Strip strip : strips) {
						ret += strip.value;
						strip.lock.unlock();
					}
				}

				return ret;
			}

			private static class Strip {
				int value = 0;
				Lock lock = new ReentrantLock();
			}
		}
}
