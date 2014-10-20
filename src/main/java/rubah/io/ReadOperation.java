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
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;


public class ReadOperation extends NonBlockingOperation<SocketChannel, Integer> {
	private ByteBuffer buffer;

	public ReadOperation(Selector selector, SocketChannel channel, ByteBuffer buffer) throws IOException {
		super(selector, channel, SelectionKey.OP_READ);
		this.buffer = buffer;
	}

	@Override
	protected boolean testSelectionKey(SelectionKey key) {
		return key.isReadable();
	}

	@Override
	protected Integer doOperation(SocketChannel channel, int selected) throws IOException {
		return channel.read(this.buffer);
	}
}