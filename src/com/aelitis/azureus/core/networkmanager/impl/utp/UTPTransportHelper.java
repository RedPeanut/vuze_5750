/*
 * Created on Aug 28, 2010
 * Created by Paul Gardner
 *
 * Copyright 2010 Vuze, Inc.  All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details (see the LICENSE file).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */



package com.aelitis.azureus.core.networkmanager.impl.utp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.gudy.azureus2.core3.logging.LogIDs;
import org.gudy.azureus2.core3.util.Debug;

import com.aelitis.azureus.core.networkmanager.impl.TransportHelper;
import com.aelitis.azureus.core.networkmanager.impl.TransportHelper.selectListener;



public class UTPTransportHelper
	implements TransportHelper {
	
	private static final LogIDs LOGID = LogIDs.NET;

	public static final int READ_TIMEOUT		= 30*1000;
	public static final int CONNECT_TIMEOUT		= 20*1000;

	private UTPConnectionManager	manager;
	private UTPSelector				selector;

	private InetSocketAddress		address;
	private UTPTransport			transport;

	private boolean					incoming;

	private UTPConnection			connection;

	private selectListener		readListener;
	private Object				readAttachment;
	private boolean 			read_selects_paused;

	private selectListener		writeListener;
	private Object				writeAttachment;
	private boolean 			writeSelectsPaused	= true;	// default is paused

	private boolean				connected;
	private boolean				closed;
	private IOException			failed;

	private ByteBuffer[]		pendingPartialWrites;

	private Map<Object,Object>	user_data;

	public UTPTransportHelper(
		UTPConnectionManager	_manager,
		InetSocketAddress		_address,
		UTPTransport			_transport)

		throws IOException
	{
		manager		= _manager;
		address 	= _address;
		transport	= _transport;

		incoming	= false;

		connection 	= manager.connect(address, this);
		selector	= connection.getSelector();
	}

	public UTPTransportHelper(
		UTPConnectionManager	_manager,
		InetSocketAddress		_address,
		UTPConnection			_connection) {
		
		// incoming
		manager		= _manager;
		address 	= _address;
		connection 	= _connection;

		connected	= true;
		incoming	= true;

		selector	= connection.getSelector();
	}

	protected void setTransport(UTPTransport _transport) {
		transport	= _transport;
	}

	protected UTPTransport getTransport() {
		return (transport);
	}

	protected int getMss() {
		if (transport == null) {
			return (UTPNetworkManager.getUdpMssSize());
		}
		return (transport.getMssSize());
	}

	public boolean minimiseOverheads() {
		return (UTPNetworkManager.MINIMISE_OVERHEADS);
	}

	public int getConnectTimeout() {
		return (CONNECT_TIMEOUT);
	}

	public int getReadTimeout() {
		return (READ_TIMEOUT);
	}

	public InetSocketAddress
	getAddress() {
		return (address);
	}

	public String getName(boolean verbose) {
		return ("uTP");
	}

	public boolean isIncoming() {
		return (incoming);
	}

	protected UTPConnection getConnection() {
		return (connection);
	}

	protected void setConnected() {
		synchronized(this) {
			if (connected) {
				return;
			}
			connected = true;
		}
		transport.connected();
	}

	public boolean delayWrite(ByteBuffer buffer) {
		if (pendingPartialWrites == null) {
			pendingPartialWrites = new ByteBuffer[]{ buffer };
			return (true);
		}
		return (false);
	}

	public boolean hasDelayedWrite() {
		return (pendingPartialWrites != null);
	}

	public int write(
		ByteBuffer 	buffer,
		boolean		partial_write )
		throws IOException
	{
		synchronized(this) {
			if (failed != null) {
				throw (failed);
			}
			if (closed) {
				throw (new IOException("Transport closed"));
			}
		}
		int	buffer_rem = buffer.remaining();
		if (partial_write && buffer_rem < UTPConnectionManager.MIN_WRITE_PAYLOAD) {
			if (pendingPartialWrites == null) {
				pendingPartialWrites = new ByteBuffer[1];
				ByteBuffer	copy = ByteBuffer.allocate(buffer_rem);
				copy.put(buffer);
				copy.position(0);
				pendingPartialWrites[0] = copy;
				return (buffer_rem);
			} else {
				int	queued = 0;
				for (int i=0;i<pendingPartialWrites.length;i++) {
					queued += pendingPartialWrites[i].remaining();
				}
				if (queued + buffer_rem <= UTPConnectionManager.MAX_BUFFERED_PAYLOAD) {
					ByteBuffer[] new_ppw = new ByteBuffer[ pendingPartialWrites.length+1 ];
					for (int i=0;i<pendingPartialWrites.length;i++) {
						new_ppw[i] = pendingPartialWrites[i];
					}
					ByteBuffer	copy = ByteBuffer.allocate(buffer_rem);
					copy.put(buffer);
					copy.position(0);
					new_ppw[pendingPartialWrites.length] = copy;
					pendingPartialWrites = new_ppw;
					return (buffer_rem);
				}
			}
		}
		if (pendingPartialWrites != null) {
			int	ppw_len = pendingPartialWrites.length;
			int	ppw_rem	= 0;
			ByteBuffer[]	buffers2 = new ByteBuffer[ppw_len+1];
			for (int i=0;i<ppw_len;i++) {
				buffers2[i] = pendingPartialWrites[i];
				ppw_rem += buffers2[i].remaining();
			}
			buffers2[ppw_len] = buffer;
			try {
				int written = connection.write(buffers2, 0, buffers2.length);
				if (written >= ppw_rem) {
					return (written - ppw_rem);
				} else {
					return (0);
				}
			} finally {
				ppw_rem	= 0;
				for (int i=0;i<ppw_len;i++) {
					ppw_rem += buffers2[i].remaining();
				}
				if (ppw_rem == 0) {
					pendingPartialWrites = null;
				}
			}
		} else {
			return (connection.write(new ByteBuffer[]{ buffer }, 0, 1));
		}
	}

	public long write(
		ByteBuffer[] 	buffers,
		int 			arrayOffset,
		int 			length)
		throws IOException
	{
		synchronized(this) {
			if (failed != null) {
				throw (failed);
			}
			if (closed) {
				throw (new IOException("Transport closed"));
			}
		}
		
		if (pendingPartialWrites != null) {
			int	ppw_len = pendingPartialWrites.length;
			int	ppw_rem	= 0;
			ByteBuffer[]	buffers2 = new ByteBuffer[length+ppw_len];
			for (int i=0;i<ppw_len;i++) {
				buffers2[i] = pendingPartialWrites[i];
				ppw_rem += buffers2[i].remaining();
			}
			int	pos = ppw_len;
			for (int i=arrayOffset;i<arrayOffset+length;i++) {
				buffers2[pos++] = buffers[i];
			}
			
			try {
				int written = connection.write(buffers2, 0, buffers2.length);
				if (written >= ppw_rem) {
					return (written - ppw_rem);
				} else {
					return (0);
				}
			} finally {
				ppw_rem	= 0;
				for (int i=0;i<ppw_len;i++) {
					ppw_rem += buffers2[i].remaining();
				}
				if (ppw_rem == 0) {
					pendingPartialWrites = null;
				}
			}
		} else {
			return (connection.write(buffers, arrayOffset, length));
		}
	}

	public int read(ByteBuffer buffer )
		throws IOException
	{
		synchronized(this) {

			if (failed != null) {

				throw (failed);
			}

			if (closed) {

				throw (new IOException("Transport closed"));
			}
		}

		return (connection.read(buffer));
	}

	public long read(
		ByteBuffer[] 	buffers,
		int 			array_offset,
		int 			length )
		throws IOException
	{
		synchronized(this) {
			if (failed != null) {
				throw (failed);
			}
			if (closed) {
				throw (new IOException("Transport closed"));
			}
		}
		long	total = 0;
		for (int i=array_offset;i<array_offset+length;i++) {
			ByteBuffer	buffer = buffers[i];
			int	max = buffer.remaining();
			int	read = connection.read(buffer);
			total += read;
			if (read < max) {
				break;
			}
		}
		//System.out.println("total = " + total);
		return (total);
	}

	protected void canRead() {
		fireReadSelect();
	}

	protected void canWrite() {
		fireWriteSelect();
	}

	public synchronized void pauseReadSelects() {
		if (readListener != null) {
			selector.cancel(this, readListener);
		}
		read_selects_paused	= true;
	}

	public synchronized void pauseWriteSelects() {
		if (writeListener != null) {
			selector.cancel(this, writeListener);
		}
		writeSelectsPaused = true;
	}

	public synchronized void resumeReadSelects() {
		read_selects_paused = false;
		fireReadSelect();
	}

	public synchronized void resumeWriteSelects() {
		writeSelectsPaused = false;
		fireWriteSelect();
	}

	public void registerForReadSelects(
		selectListener	listener,
		Object			attachment ) {
		synchronized(this) {
			readListener		= listener;
			readAttachment		= attachment;
		}
		resumeReadSelects();
	}

	public void registerForWriteSelects(
		selectListener	listener,
		Object			attachment) {
		synchronized(this) {
		  	writeListener		= listener;
			writeAttachment	= attachment;
		}
		resumeWriteSelects();
	}

	public synchronized void cancelReadSelects() {
		selector.cancel(this, readListener);

		read_selects_paused	= true;
	  	readListener		= null;
		readAttachment		= null;
	}

	public synchronized void cancelWriteSelects() {
	   	selector.cancel(this, writeListener);

		writeSelectsPaused	= true;
	 	writeListener			= null;
		writeAttachment		= null;
	}

	protected void fireReadSelect() {
	 	synchronized(this) {
	   		if (readListener != null && !read_selects_paused) {
	   			if (failed != null) {
	   	 			selector.ready(this, readListener, readAttachment, failed);
	   			} else if (closed) {
	   	   			selector.ready(this, readListener, readAttachment, new Throwable("Transport closed"));
	   			} else if (connection.canRead()) {
	   	 			selector.ready(this, readListener, readAttachment);
	   			}
	   		}
	 	}
	}

	protected void fireWriteSelect() {
	  	synchronized(this) {
	   		if (writeListener != null && !writeSelectsPaused) {
	   			if (failed != null) {
	   				writeSelectsPaused	= true;
	   	 			selector.ready(this, writeListener, writeAttachment, failed);
	   			}else if (closed) {
	   				writeSelectsPaused	= true;
	   	   			selector.ready(this, writeListener, writeAttachment, new Throwable("Transport closed"));
	   			}else if (connection.canWrite()) {
	   				writeSelectsPaused	= true;
	   	 			selector.ready(this, writeListener, writeAttachment);
	   			}
	   		}
		}
	}

	public void failed(Throwable	reason) {
		close(Debug.getNestedExceptionMessage(reason));
	}

	public boolean isClosed() {
		synchronized(this) {
			return (closed);
		}
	}

	public void close(String	reason ) {
		synchronized(this) {
	   		closed	= true;
			fireReadSelect();
	  		fireWriteSelect();
	  	}
		if (transport != null) {
			transport.closed();
		}
		if (connection != null) {
			connection.closeSupport(reason);
		}
	}

	protected void poll() {
	   	synchronized(this) {
	   		fireReadSelect();
	   		fireWriteSelect();
	   	}
	}

	public synchronized void setUserData(
		Object	key,
		Object	data) {
		if (user_data == null) {
			user_data = new HashMap<Object, Object>();
		}
		user_data.put(key, data);
	}

	public synchronized Object getUserData(Object	key) {
		if (user_data == null) {
			return (null);
		}
		return (user_data.get(key));
	}

	public void setTrace(boolean	on) {
	}

	public void setScatteringMode(
		long forBytes) {
	}
}