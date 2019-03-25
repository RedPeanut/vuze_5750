/*
 * Created on Apr 22, 2005
 * Created by Alon Rohter
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
import java.util.*;

import org.gudy.azureus2.core3.util.AEDiagnostics;
import org.gudy.azureus2.core3.util.AEMonitor;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.SystemTime;

import com.aelitis.azureus.core.networkmanager.*;


/**
 *
 */

public class MultiPeerDownloader2 implements RateControlledEntity {

	private static final int MOVE_TO_IDLE_TIME	= 500;

	private static final Object	ADD_ACTION 		= new Object();
	private static final Object	REMOVE_ACTION 	= new Object();

	private volatile List<NetworkConnectionBase> connectionsCow = new ArrayList<>();  //copied-on-write
	private final AEMonitor connectionsMon = new AEMonitor("MultiPeerDownloader");

	private final RateHandler mainHandler;

	private List<Object[]>			pendingActions;
	private final ConnectionList	activeConnections 	= new ConnectionList();
	private final ConnectionList	idleConnections 	= new ConnectionList();

	private long			lastIdleCheck;

	private volatile EventWaiter		waiter;

	/**
	 * Create new downloader using the given "global" rate handler to limit all peers managed by this downloader.
	 * @param main_handler
	 */
	public MultiPeerDownloader2(RateHandler _mainHandler) {
		mainHandler = _mainHandler;
	}

	public RateHandler getRateHandler() {
		return (mainHandler);
	}

	/**
	 * Add the given connection to the downloader.
	 * @param connection to add
	 */
	public void addPeerConnection(NetworkConnectionBase connection) {
		EventWaiter waiterToKick = null;
		try {
			connectionsMon.enter();
			//copy-on-write
			int cowSize = connectionsCow.size();
			if (cowSize == 0) {
				waiterToKick = waiter;
				if (waiterToKick != null) {
					waiter = null;
				}
			}
			List<NetworkConnectionBase> connNew = new ArrayList<>(cowSize + 1);
			connNew.addAll(connectionsCow);
			connNew.add(connection);
			connectionsCow = connNew;
			if (pendingActions == null) {
				pendingActions = new ArrayList<>();
			}
			pendingActions.add(new Object[]{ ADD_ACTION, connection });
		} finally {
			connectionsMon.exit();
		}
		if (waiterToKick != null) {
			waiterToKick.eventOccurred();
		}
	}


	/**
	 * Remove the given connection from the downloader.
	 * @param connection to remove
	 * @return true if the connection was found and removed, false if not removed
	 */
	public boolean removePeerConnection(NetworkConnectionBase connection) {
		try {
			connectionsMon.enter();
			//copy-on-write
			ArrayList conn_new = new ArrayList(connectionsCow);
			boolean removed = conn_new.remove(connection);
			if (!removed) return false;
			connectionsCow = conn_new;
			if (pendingActions == null) {
				pendingActions = new ArrayList();
			}
			pendingActions.add(new Object[]{ REMOVE_ACTION, connection });
			return true;
		} finally {
			connectionsMon.exit();
		}
	}

	public boolean canProcess(EventWaiter waiter) {
		int[] allowed = mainHandler.getCurrentNumBytesAllowed();
		if (allowed[0] < 1) { // Not yet fully supporting free-protocol for downloading && allowed[1] == 0) {
			return false;
		}
		return true;
	}

	public long getBytesReadyToWrite() {
		return (0);
	}

	public int getConnectionCount(EventWaiter _waiter) {
		int result = connectionsCow.size();
		if (result == 0) {
			waiter = _waiter;
		}
		return (result);
	}

	public int getReadyConnectionCount(EventWaiter	waiter) {
		int	res = 0;
		for (Iterator it=connectionsCow.iterator();it.hasNext();) {
			NetworkConnectionBase connection = (NetworkConnectionBase)it.next();
			if (connection.getTransportBase().isReadyForRead(waiter) == 0) {
				res++;
			}
		}
		return (res);
	}

	public int doProcessing(
		EventWaiter waiter,
		int			maxBytes) {
		
		// Note - this is single threaded
		// System.out.println("MPD: do process - " + connections_cow.size() + "/" + active_connections.size() + "/" + idle_connections.size());
		int[] bytesAllowed = mainHandler.getCurrentNumBytesAllowed();
		int numBytesAllowed = bytesAllowed[0];
		boolean protocolIsFree = bytesAllowed[1] > 0;
		if (numBytesAllowed < 1) { // Not yet fully supporting free-protocol for downloading && !protocol_is_free) {
			return 0;
		}
		if (maxBytes > 0 && maxBytes < numBytesAllowed) {
			numBytesAllowed = maxBytes;
		}
		if (pendingActions != null) {
			try {
				connectionsMon.enter();
				for (int i=0;i<pendingActions.size();i++) {
					Object[] entry = (Object[])pendingActions.get(i);
					NetworkConnectionBase	connection = (NetworkConnectionBase)entry[1];
					if (entry[0] == ADD_ACTION) {
						activeConnections.add(connection);
					} else {
						activeConnections.remove(connection);
						idleConnections.remove(connection);
					}
				}
				pendingActions = null;
			} finally {
				connectionsMon.exit();
			}
		}
		
		long now = SystemTime.getSteppedMonotonousTime();
		if (now - lastIdleCheck > MOVE_TO_IDLE_TIME) {
			lastIdleCheck = now;
			// move any active ones off of the idle queue
			ConnectionEntry	entry = idleConnections.head();
			while (entry != null) {
				NetworkConnectionBase connection = entry.connection;
				ConnectionEntry next = entry.next;
				if (connection.getTransportBase().isReadyForRead(waiter) == 0) {
					// System.out.println("   moving to active " + connection.getString());
					idleConnections.remove(entry);
					activeConnections.addToStart(entry);
				}
				entry = next;
			}
		}
		
		// process the active set
		int numBytesRemaining = numBytesAllowed;
		int	dataBytesRead		= 0;
		int protocolBytesRead 	= 0;
		ConnectionEntry	entry = activeConnections.head();
		int	numEntries = activeConnections.size();
		for (int i=0; i<numEntries && entry != null && numBytesRemaining > 0;i++) {
			NetworkConnectionBase connection = entry.connection;
			ConnectionEntry next = entry.next;
			long	ready = connection.getTransportBase().isReadyForRead(waiter);
			// System.out.println("   " + connection.getString() + " - " + ready);
			if (ready == 0) {
				int	mss = connection.getMssSize();
				int allowed = numBytesRemaining > mss ? mss : numBytesRemaining;
				int bytesRead = 0;
				
				try {
					int[] read = connection.getIncomingMessageQueue().receiveFromTransport(allowed, protocolIsFree);
					dataBytesRead 		+= read[0];
					protocolBytesRead	+= read[1];
					bytesRead = read[0] + read[1];
				} catch (Throwable e) {
					
					if (AEDiagnostics.TRACE_CONNECTION_DROPS) {
						if (e.getMessage() == null) {
							Debug.out("null read exception message: ", e);
						} else {
							if (!e.getMessage().contains("end of stream on socket read") &&
								!e.getMessage().contains(
									"An existing connection was forcibly closed by the remote host") &&
								!e.getMessage().contains("Connection reset by peer") &&
								!e.getMessage().contains(
									"An established connection was aborted by the software in your host machine")) {
								System.out.println("MP: read exception [" +connection.getTransportBase().getDescription()+ "]: " +e.getMessage());
							}
						}
					}
					
					if (! (e instanceof IOException )) {
						// one day upgrade this exception to an IOException
						if (!Debug.getNestedExceptionMessage(e).contains("Incorrect mix")) {
							Debug.printStackTrace(e);
						}
					}
					connection.notifyOfException(e);
				}
				numBytesRemaining -= bytesRead;
				// System.out.println("   moving to end " + connection.getString());
				activeConnections.moveToEnd(entry);
			} else if (ready > MOVE_TO_IDLE_TIME) {
				// System.out.println("   moving to idle " + connection.getString());
				activeConnections.remove(entry);
				idleConnections.addToEnd(entry);
			}
			entry = next;
		}
		
		int totalBytesRead = numBytesAllowed - numBytesRemaining;
		if (totalBytesRead > 0) {
			mainHandler.bytesProcessed(dataBytesRead, protocolBytesRead);
			return totalBytesRead;
		}
		return 0;  //zero bytes read
	}


	public int getPriority() {  return RateControlledEntity.PRIORITY_HIGH;  }

	public boolean getPriorityBoost() { return false; }

	public String getString() {
		StringBuilder str = new StringBuilder();
		str.append("MPD (").append(connectionsCow.size()).append("/").append(activeConnections.size()).append("/")
			.append(idleConnections.size()).append(": ");
		int	num = 0;
		for (Iterator it=connectionsCow.iterator();it.hasNext();) {
			NetworkConnectionBase connection = (NetworkConnectionBase)it.next();
			if (num++ > 0) {
				str.append(",");
			}
			str.append( connection.getString());
		}
		return ( str.toString());
	}

	protected static class ConnectionList {
		private int				size;
		private ConnectionEntry	head;
		private ConnectionEntry	tail;
		
		protected ConnectionEntry add(NetworkConnectionBase		connection) {
			ConnectionEntry entry = new ConnectionEntry(connection);
			if (head == null) {
				head = tail = entry;
			} else {
				tail.next	= entry;
				entry.prev	= tail;
				tail = entry;
			}
			size++;
			return (entry);
		}
		
		protected void addToEnd(
			ConnectionEntry		entry) {
			entry.next = null;
			entry.prev = tail;
			if (tail == null) {
				head = tail = entry;
			} else {
				tail.next	= entry;
				tail 		= entry;
			}
			size++;
		}
		
		protected void addToStart(ConnectionEntry entry) {
			entry.next = head;
			entry.prev = null;
			if (head == null) {
				head = tail = entry;
			} else {
				head.prev	= entry;
				head 		= entry;
			}
			size++;
		}
		
		protected void moveToEnd(ConnectionEntry entry) {
			if (entry != tail) {
				ConnectionEntry prev 	= entry.prev;
				ConnectionEntry	next	= entry.next;
				if (prev == null) {
					head	= next;
				} else {
					prev.next = next;
				}
				next.prev = prev;
				entry.prev 	= tail;
				entry.next	= null;
				tail.next	= entry;
				tail		= entry;
			}
		}
		
		protected ConnectionEntry remove(NetworkConnectionBase connection) {
			ConnectionEntry	entry = head;
			while (entry != null) {
				if (entry.connection == connection) {
					remove(entry);
					return (entry);
				} else {
					entry = entry.next;
				}
			}
			return (null);
		}
		
		protected void remove(ConnectionEntry entry) {
			ConnectionEntry prev 	= entry.prev;
			ConnectionEntry	next	= entry.next;
			if (prev == null) {
				head	= next;
			} else {
				prev.next = next;
			}
			if (next == null) {
				tail	= prev;
			} else {
				next.prev = prev;
			}
			size--;
		}
		protected int size() {
			return (size);
		}
		protected ConnectionEntry head() {
			return (head);
		}
	}

	protected static class ConnectionEntry {
		private ConnectionEntry next;
		private ConnectionEntry prev;

		final NetworkConnectionBase	connection;

		protected ConnectionEntry(NetworkConnectionBase		_connection) {
			connection = _connection;
		}

	}
}
