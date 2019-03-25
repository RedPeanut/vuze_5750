/*
 * Created on 22 Jun 2006
 * Created by Paul Gardner
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 */

package com.aelitis.azureus.core.networkmanager.impl;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.gudy.azureus2.core3.util.AEDiagnostics;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.SystemTime;
import org.gudy.azureus2.core3.util.TimeFormatter;

import com.aelitis.azureus.core.networkmanager.EventWaiter;
import com.aelitis.azureus.core.networkmanager.NetworkConnection;
import com.aelitis.azureus.core.networkmanager.Transport;
import com.aelitis.azureus.core.networkmanager.TransportStartpoint;

public abstract class TransportImpl implements Transport {
	private TransportHelperFilter filter;

	private static final TransportStats stats = AEDiagnostics.TRACE_TCP_TRANSPORT_STATS ? new TransportStats() : null;

	private ByteBuffer data_already_read = null;

	private volatile EventWaiter readWaiter;
	private volatile EventWaiter writeWaiter;
	private volatile boolean isReadyForWrite = false;
	private volatile boolean isReadyForRead = false;
	private Throwable writeSelectFailure = null;
	private Throwable readSelectFailure = null;

	private long	lastReadyForRead = SystemTime.getSteppedMonotonousTime();

	private boolean	trace;

	protected TransportImpl() {
	}

	public TransportStartpoint getTransportStartpoint() {
		return (null);
	}

	public void setFilter(TransportHelperFilter _filter) {
		filter	= _filter;

		if (trace && _filter != null) {

			_filter.setTrace(true);
		}
	}

	public TransportHelperFilter
	getFilter() {
		return (filter);
	}

	public void setAlreadyRead(
		ByteBuffer bytes_already_read) {
		if (bytes_already_read != null && bytes_already_read.hasRemaining()) {

			if (data_already_read != null) {
				ByteBuffer new_bb = ByteBuffer.allocate(data_already_read.remaining() + bytes_already_read.remaining());
				new_bb.put(bytes_already_read);
				new_bb.put(data_already_read);
				new_bb.position(0);
				data_already_read = new_bb;
			}
			else {
				data_already_read	= bytes_already_read;
			}
			isReadyForRead = true;
		}
	}


	public String getEncryption(boolean verbose) {
		return ( filter==null?"":filter.getName(verbose));
	}

	public String getProtocol() {
			// default impl = extract from encryption

		String s = getEncryption(false);

		int pos = s.indexOf('(');

		if (pos != -1) {

			s = s.substring(pos+1);

			pos = s.indexOf(')');

			if (pos > 0) {

				return (s.substring( 0, pos));
			}
		}

		return ("");
	}

	public boolean isEncrypted() {
		return ( filter==null?false:filter.isEncrypted());
	}

	public boolean isSOCKS() {
		return (false);
	}

	  /**
	   * Is the transport ready to write,
	   * i.e. will a write request result in >0 bytes written.
	   * @return true if the transport is write ready, false if not yet ready
	   */

	public boolean isReadyForWrite(
		EventWaiter waiter ) {
		if (waiter != null) {

			writeWaiter = waiter;
		}

		return isReadyForWrite;
	}

	protected boolean readyForWrite(boolean	ready) {
		
		if (trace) {
			TimeFormatter.milliTrace("trans: readyForWrite -> " + ready);
		}
		
		if (ready) {
		  	boolean	progress = !isReadyForWrite;
			isReadyForWrite = true;
			EventWaiter ww = writeWaiter;
			if (ww != null) {
				ww.eventOccurred();
			}
			return progress;
		} else {
			isReadyForWrite = false;
			return (false);
		}
	}

	protected void writeFailed(
		Throwable	msg) {
		msg.fillInStackTrace();

		writeSelectFailure = msg;

		isReadyForWrite = true;  //set to true so that the next write attempt will throw an exception
	}

	  /**
	   * Is the transport ready to read,
	   * i.e. will a read request result in >0 bytes read.
	   * @return 0 if the transport is read ready, millis since last ready or -1 if never ready
	   */

	public long isReadyForRead(
		EventWaiter waiter ) {
		if (waiter != null) {
			readWaiter = waiter;
		}

		boolean ready = isReadyForRead ||
						data_already_read != null ||
						( filter != null && filter.hasBufferedRead());

		long	now = SystemTime.getSteppedMonotonousTime();

		if (ready) {

			lastReadyForRead = now;

			return (0);
		}

		long	diff = now - lastReadyForRead + 1;	// make sure > 0

		return (diff);
	}

	protected boolean readyForRead(boolean ready) {
		if (ready) {
		   	boolean	progress = !isReadyForRead;
			isReadyForRead = true;
			EventWaiter rw = readWaiter;
			if (rw != null) {
				rw.eventOccurred();
			}
			return progress;
		} else {
			isReadyForRead = false;
			return (false);
		}
	}

	public void setReadyForRead() {
		readyForRead(true);
	}

	protected void readFailed(Throwable msg) {
		msg.fillInStackTrace();	// msg picked up on another thread - make sure trace is available
		readSelectFailure = msg;
		isReadyForRead = true;  //set to true so that the next read attempt will throw an exception
	}

	/**
	 * Write data to the transport from the given buffers.
	 * NOTE: Works like GatheringByteChannel.
	 * @param buffers from which bytes are to be retrieved
	 * @param arrayOffset offset within the buffer array of the first buffer from which bytes are to be retrieved
	 * @param length maximum number of buffers to be accessed
	 * @return number of bytes written
	 * @throws IOException on write error
	 */
	public long write(
		ByteBuffer[] buffers,
		int arrayOffset,
		int length)
		throws IOException
	{
	  	if (writeSelectFailure != null) {
	  		throw new IOException("writeSelectFailure: " + writeSelectFailure.getMessage());
	  	}
	  	if (filter == null) return 0;
	  	long written = filter.write(buffers, arrayOffset, length);
	  	if (stats != null ) stats.bytesWritten((int)written);  //TODO
	  	if (written < 1) requestWriteSelect();
	  	return written;
	}

	/**
	 * Read data from the transport into the given buffers.
	 * NOTE: Works like ScatteringByteChannel.
	 * @param buffers into which bytes are to be placed
	 * @param array_offset offset within the buffer array of the first buffer into which bytes are to be placed
	 * @param length maximum number of buffers to be accessed
	 * @return number of bytes read
	 * @throws IOException on read error
	 */
	public long read(
		ByteBuffer[] buffers,
		int array_offset,
		int length )
		throws IOException
	{
		if (readSelectFailure != null) {
			throw new IOException("read_select_failure: " + readSelectFailure.getMessage());
		}
			//insert already-read data into the front of the stream
		if (data_already_read != null) {
			int inserted = 0;
			for (int i = array_offset; i < (array_offset + length); i++) {
				ByteBuffer bb = buffers[ i ];
				int orig_limit = data_already_read.limit();
				if (data_already_read.remaining() > bb.remaining()) {
					data_already_read.limit(data_already_read.position() + bb.remaining());
				}
				inserted += data_already_read.remaining();
				bb.put(data_already_read);
				data_already_read.limit(orig_limit);
				if (!data_already_read.hasRemaining()) {
					data_already_read = null;
					break;
				}
			}
			if (!buffers[ array_offset + length - 1 ].hasRemaining()) {  //the last buffer has nothing left to read into normally
				return inserted;  //so return right away, skipping socket read
			}
		}
		if (filter == null) {
			throw (new IOException("Transport not ready"));
		}
		long bytes_read = filter.read(buffers, array_offset, length);
		if (stats != null )  stats.bytesRead( (int)bytes_read);  //TODO
		if (bytes_read == 0) {
			requestReadSelect();
		}
		return bytes_read;
	}

	private void requestWriteSelect() {
		isReadyForWrite = false;

		if (filter != null) {

		   	filter.getHelper().resumeWriteSelects();
		}
	}


	private void requestReadSelect() {
		isReadyForRead = false;

		if (filter != null) {

			filter.getHelper().resumeReadSelects();
		}
	}

	public void connectedInbound() {
		registerSelectHandling();
	}

	public void connectedOutbound() {
		registerSelectHandling();
	}

	public void bindConnection(NetworkConnection	connection) {
	}

	public void unbindConnection(NetworkConnection	connection) {
	}

	private void registerSelectHandling() {
		
		TransportHelperFilter filter = getFilter();

		if (filter == null) {
			Debug.out("ERROR: registerSelectHandling():: filter == null");
			return;
		}

		TransportHelper	helper = filter.getHelper();

		//read selection
		helper.registerForReadSelects(
			new TransportHelper.selectListener() {
			   	public boolean selectSuccess(
					TransportHelper	helper,
					Object 			attachment) {
			   		return (readyForRead(true));
			   	}

				public void selectFailure(
					TransportHelper	helper,
					Object 			attachment,
					Throwable 		msg) {
					readFailed(msg);
				}
			},
			null);

		helper.registerForWriteSelects(
				new TransportHelper.selectListener() {
				   	public boolean selectSuccess(
						TransportHelper	helper,
						Object 			attachment) {
				   		return (readyForWrite(true));
				   	}

					public void selectFailure(
						TransportHelper	helper,
						Object 			attachment,
						Throwable 		msg) {
						writeFailed(msg);
					}
				},
				null);
	}

	public void setTrace(boolean	on) {
		trace	= on;
		TransportHelperFilter	filter = getFilter();
		if (filter != null) {
			filter.setTrace(on);
		}
	}
}
