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
import java.io.OutputStream;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

import rubah.Rubah;

public class UpdatableOutputStream extends OutputStream {
	private static final int DEFAULT_BUFFER_SIZE = 16 * 1024;
	private ByteBuffer buf;
	private Selector selector;
	private SocketChannel channel;

	public UpdatableOutputStream(SocketChannel channel) throws IOException {
		this(channel, DEFAULT_BUFFER_SIZE);
	}

	public UpdatableOutputStream(SocketChannel channel, int bufferSize) throws IOException {
		this.buf = ByteBuffer.allocate(bufferSize);
		this.buf.clear();
		this.selector = Selector.open();
		this.channel = channel;
		this.channel.register(this.selector, SelectionKey.OP_WRITE);
	}

	@Override
	public void write(int b) throws IOException {
		try {
			this.buf.put((byte) b);
		} catch (BufferOverflowException e) {
			this.flush();
			this.buf.put((byte) b);
		}
	}

	@Override
	public void write(byte[] bytes, int off, int len)
			throws IOException {

		for (int n = this.buf.capacity(); len > n ; off += n, len -= n) {
			this.write(bytes, off, n);
		}

		try {
			this.buf.put(bytes, off, len);
		} catch (BufferOverflowException e) {
			this.flush();
			this.buf.put(bytes, off, len);
		}
	}

	@Override
	public void flush() throws IOException {
		this.buf.flip();
		while (this.buf.hasRemaining()) {
			Integer ret;
			try {
				ret	= Rubah.write(this.selector, this.channel, this.buf);
			} catch (rubah.io.InterruptedException e) {
				System.out.println("Thread "+Thread.currentThread()+" interrupted while writing, retrying...");
				continue; // Interrupted while flushing, retry
			}
			if (ret == -1) {
				System.out.println("Thread "+Thread.currentThread()+" attempted to write to closed stream...");
				break; // End of stream occurred
			}
			else {
				continue; // Kernel buffer full, retry
			}
		}
		this.buf.clear();
	}

}
