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
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

import rubah.Rubah;

public class UpdatableInputStream extends InputStream {
	private static final int DEFAULT_BUFFER_SIZE = 16 * 1024;
	private ByteBuffer buf;
	private SocketChannel channel;
	private Selector selector;

	public UpdatableInputStream(SocketChannel channel) throws IOException {
		this(channel, DEFAULT_BUFFER_SIZE);
	}

	public UpdatableInputStream(SocketChannel channel, int bufferSize) throws IOException {
		this.buf = ByteBuffer.allocate(bufferSize);
		this.buf.flip();
		this.selector = Selector.open();
		this.channel = channel;
		this.channel.register(this.selector, SelectionKey.OP_READ);
	}

	private Integer blockingRead() throws IOException, rubah.io.InterruptedException {
		Integer ret = null;
		this.buf.clear();
		try {
			ret = Rubah.read(this.selector, this.channel, this.buf);
		} finally {
			this.buf.flip();
		}
		return ret;
	}

	@Override
	public int read() throws IOException {
		if (!this.buf.hasRemaining()) {
			while (true) {
				Integer ret;
				try {
					ret = this.blockingRead();
				} catch (rubah.io.InterruptedException e) {
					System.out.println("Thread "+Thread.currentThread()+" interrupted while reading, ignoring...");
					continue; // Interrupt, carry on reading
				}
				if (ret == -1) {
					System.out.println("Thread "+Thread.currentThread()+" attempted to read from closed stream...");
					return -1; // End of stream occurred
				}
				else {
					break; // Process the read value
				}
			}
		}

		return this.buf.get() & 0xFF;
	}

	@Override
	public int read(byte[] bytes, int off, int len)
			throws IOException {
		if (!this.buf.hasRemaining()) {
			while (true) {
				Integer ret ;
				try {
					ret = this.blockingRead();
				} catch (rubah.io.InterruptedException e) {
					continue; // Interrupt, carry on reading
				}
				if (ret == -1) {
					return -1; // End of stream occurred
				}
				else {
					break; // Process the read value
				}
			}
		}

		len = Math.min(len, this.buf.remaining());
		this.buf.get(bytes, off, len);
		return len;
	}

	/**
	 * Returns the next byte that read() will return and blocks until available data.
	 * @return
	 * @throws IOException
	 * @throws rubah.io.InterruptedException If read is interrupted by an update request
	 */
	public Integer peek() throws IOException, rubah.io.InterruptedException {
		if (!this.buf.hasRemaining()) {
			while (true) {
				Integer ret	= this.blockingRead();
				if (ret == -1) {
					return -1; // End of stream occurred
				}
				else {
					break; // Process the read value
				}
			}
		}
		this.buf.mark();
		int ret = this.buf.get() & 0xFF;
		this.buf.reset();
		return ret;
	}
}
