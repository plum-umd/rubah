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
import java.net.SocketTimeoutException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.Iterator;

import rubah.Rubah;

public abstract class NonBlockingOperation<T extends AbstractSelectableChannel, E> {
	private final T channel;
	private Selector selector;
	private long timeout = -1L;

	public NonBlockingOperation(Selector selector, T channel) {
		this.channel = channel;
		this.selector = selector;
	}

	public NonBlockingOperation(Selector selector, T channel, int selectionKeys) throws IOException {
		this.channel = channel;
		this.selector = selector;
		this.channel.register(selector, selectionKeys);
	}

	public NonBlockingOperation(Selector selector, T channel, int selectionKeys, long timeout) throws IOException {
		this(selector, channel, selectionKeys);
		this.timeout = timeout;
	}

	/**
	 *
	 * @return The result of the operation, or null if the operation was interrupted due to an update.
	 * @throws IOException
	 */
	public E performOperation() throws IOException, InterruptedException {

		while (true) {
			Rubah.registerBlockingIO();
			int selected = (this.timeout < 0 ? selector.select() : selector.select(timeout));
			// MARK
			Rubah.deregisterBlockingIO();
			// Thread may be interrupted at MARK,
			// so this line clears the interrupted state.
			// This way the next IO call does not close the socket.
//		Thread.interrupted();
			Iterator<SelectionKey> it = this.selector.selectedKeys().iterator();
			while (it.hasNext()) {
				SelectionKey selKey = it.next();
				it.remove();

				if (selKey.isValid() && this.testSelectionKey(selKey)) {
					E ret = this.doOperation(this.channel, selected);
					if (ret == null)
						continue;
					return ret;
				}
			}
			// No selection key was set, this operation was interrupted
			return this.noOperation();
		}
	}

	protected E noOperation() throws InterruptedException, SocketTimeoutException {
		if (Rubah.isUpdateRequested())
			throw new InterruptedException();
		throw new SocketTimeoutException();
	}

	protected abstract boolean testSelectionKey(SelectionKey key);

	protected abstract E doOperation(T channel, int selected) throws IOException;
}
