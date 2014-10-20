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
package rubah.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashSet;
import java.util.Set;

public class RubahIO {

	private static class ThreadInterruptibleStatus {
		boolean interruptible;
		Thread t;
	}

	private static Set<ThreadInterruptibleStatus> threads = new HashSet<>();

	private static ThreadLocal<ThreadInterruptibleStatus> interruptibleStatus = new ThreadLocal<ThreadInterruptibleStatus>() {
		@Override
		protected ThreadInterruptibleStatus initialValue() {
			ThreadInterruptibleStatus ret = new ThreadInterruptibleStatus();
			ret.interruptible = false;
			ret.t = Thread.currentThread();

			synchronized (threads) {
				threads.add(ret);
			}

			return ret;
		}
	};

	public static void registerBlockingIO() {
		ThreadInterruptibleStatus status = interruptibleStatus.get();

		synchronized (status) {
			status.interruptible = true;
		}
	}

	public static void deregisterBlockingIO() {
		ThreadInterruptibleStatus status = interruptibleStatus.get();

		synchronized (status) {
			status.interruptible = false;
			Thread.interrupted();
		}
	}

	public static void interruptThreads() {
		synchronized (threads) {
			for (ThreadInterruptibleStatus status : threads) {
				synchronized (status) {
					if (status.interruptible) {
						System.out.println("Interrupting thread " + status.t);
						status.t.interrupt();
					}
					status.interruptible = false;
				}
			}
		}
	}

	/**
	 *
	 * @param channel
	 * @return A socket channel for an accepted connection, or null if the operation was interrupted due to an update
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public static SocketChannel accept(Selector selector, ServerSocketChannel channel) throws IOException, InterruptedException {
		SocketChannel ret = new AcceptOperation(selector, channel).performOperation();
		ret.configureBlocking(false);
		return ret;
	}

	public static SocketChannel accept(Selector selector, ServerSocketChannel channel, long timeout) throws IOException, InterruptedException {
		SocketChannel ret = new AcceptOperation(selector, channel, timeout).performOperation();
		ret.configureBlocking(false);
		return ret;
	}

	/**
	 *
	 * @param channel
	 * @param buffer
	 * @return The number of bytes written to the socket from the buffer, or null if the operation was interrupted due to an update
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public static Integer write(Selector selector, SocketChannel channel, ByteBuffer buffer)
			throws IOException, InterruptedException {
		return new WriteOperation(selector, channel, buffer).performOperation();
	}

	/**
	 *
	 * @param channel
	 * @param buffer
	 * @return The number of bytes read from the socket to the buffer, or null if the operation was interrupted due to an update
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public static Integer read(Selector selector, SocketChannel channel, ByteBuffer buffer)
			throws IOException, InterruptedException {
		return new ReadOperation(selector, channel, buffer).performOperation();
	}


}
