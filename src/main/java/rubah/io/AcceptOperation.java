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
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class AcceptOperation extends NonBlockingOperation<ServerSocketChannel, SocketChannel> {

	public AcceptOperation(Selector selector, ServerSocketChannel channel) throws IOException {
		super(selector, channel, SelectionKey.OP_ACCEPT);
	}

	public AcceptOperation(Selector selector, ServerSocketChannel channel, long timeout) throws IOException {
		super(selector, channel, SelectionKey.OP_ACCEPT, timeout);
	}

	@Override
	protected boolean testSelectionKey(SelectionKey key) {
		return key.isAcceptable();
	}

	@Override
	protected SocketChannel doOperation(ServerSocketChannel channel, int selected) throws IOException {
		try {
			return channel.accept();
		} catch (Throwable e) {
			e.printStackTrace();
			throw new Error(e);
		}
	}
}