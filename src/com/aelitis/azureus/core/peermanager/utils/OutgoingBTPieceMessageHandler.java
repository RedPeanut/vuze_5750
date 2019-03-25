/*
 * Created on Jul 19, 2004
 * Created by Alon Rohter
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.	See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA	02111-1307, USA.
 *
 */

package com.aelitis.azureus.core.peermanager.utils;

import java.util.*;

import org.gudy.azureus2.core3.disk.*;
import org.gudy.azureus2.core3.peer.PEPeer;
import org.gudy.azureus2.core3.util.*;

import com.aelitis.azureus.core.networkmanager.OutgoingMessageQueue;
import com.aelitis.azureus.core.peermanager.messaging.*;
import com.aelitis.azureus.core.peermanager.messaging.bittorrent.*;


/**
 * Front-end manager for handling requested outgoing bittorrent Piece messages.
 * Peers often make piece requests in batch, with multiple requests always
 * outstanding, all of which won't necessarily be honored (i.e. we choke them),
 * so we don't want to waste time reading in the piece data from disk ahead
 * of time for all the requests. Thus, we only want to perform read-aheads for a
 * small subset of the requested data at any given time, which is what this handler
 * does, before passing the messages onto the outgoing message queue for transmission.
 */
public class OutgoingBTPieceMessageHandler {
	private final PEPeer					peer;
	private final OutgoingMessageQueue 	outgoingMessageQueue;
	private 		byte					pieceVersion;

	private final LinkedList<DiskManagerReadRequest>			requests 			= new LinkedList<DiskManagerReadRequest>();
	private final ArrayList<DiskManagerReadRequest>			loadingMessages 	= new ArrayList<DiskManagerReadRequest>();
	private final HashMap<BTPiece,DiskManagerReadRequest> 	queuedMessages 	= new HashMap<BTPiece, DiskManagerReadRequest>();

	private final AEMonitor	lockMon	= new AEMonitor("OutgoingBTPieceMessageHandler:lock");
	private boolean destroyed = false;
	private int requestReadAhead = 2;

	final OutgoingBTPieceMessageHandlerAdapter	adapter;

	/**
	 * Create a new handler for outbound piece messages,
	 * reading piece data from the given disk manager
	 * and transmitting the messages out the given message queue.
	 * @param disk_manager
	 * @param outgoing_message_q
	 */
	public
	OutgoingBTPieceMessageHandler(
	PEPeer									_peer,
	OutgoingMessageQueue 					_outgoing_message_q,
	OutgoingBTPieceMessageHandlerAdapter	_adapter,
	byte									_piece_version )
	{
	peer					= _peer;
		outgoingMessageQueue 	= _outgoing_message_q;
		adapter					= _adapter;
		pieceVersion			= _piece_version;

		outgoingMessageQueue.registerQueueListener(sentMessageListener);
	}

	public void
	setPieceVersion(
	byte	version )
	{
		pieceVersion = version;
	}



	private final DiskManagerReadRequestListener readReqListener = new DiskManagerReadRequestListener() {
		public void readCompleted(DiskManagerReadRequest request, DirectByteBuffer data) {
			try {
				lockMon.enter();

				if (!loadingMessages.contains(request) || destroyed) { //was canceled
					data.returnToPool();
					return;
				}
				loadingMessages.remove(request);

				BTPiece msg = new BTPiece(request.getPieceNumber(), request.getOffset(), data, pieceVersion);
				queuedMessages.put(msg, request);

				outgoingMessageQueue.addMessage(msg, true);
			} finally{
				lockMon.exit();
			}

			outgoingMessageQueue.doListenerNotifications();
		}

		public void
		readFailed(
			DiskManagerReadRequest 	request,
			Throwable		 		cause )
		{
				try {
						lockMon.enter();

						if (!loadingMessages.contains( request ) || destroyed) { //was canceled
							return;
						}
						loadingMessages.remove(request);
				} finally {
						lockMon.exit();
				}

				peer.sendRejectRequest(request);
		}

		public int
		getPriority()
		{
			return (-1);
		}
	public void requestExecuted(long bytes) {
		adapter.diskRequestCompleted(bytes);
	}
	};


	private final OutgoingMessageQueue.MessageQueueListener sentMessageListener = new OutgoingMessageQueue.MessageQueueListener() {
		
		public boolean messageAdded(Message message) {
			return true;
		}

		public void messageSent(Message message) {
			
			if (message.getID().equals(BTMessage.ID_BT_PIECE)) {
				try {
					lockMon.enter();
					// due to timing issues we can get in here with a message already removed
					queuedMessages.remove(message);
				} finally {
					lockMon.exit();
				}
				
				/*
				if (peer.getIp().equals("64.71.5.2")) {
					outgoing_message_queue.setTrace(true);
					// BTPiece p = (BTPiece)message;
					// TimeFormatter.milliTrace("obt sent: " + p.getPieceNumber() + "/" + p.getPieceOffset());
				}
		 		*/
				doReadAheadLoads();
			}
		}
		public void messageQueued(Message message) {/*nothing*/}
		public void messageRemoved(Message message) {/*nothing*/}
		public void protocolBytesSent(int byte_count) {/*ignore*/}
		public void dataBytesSent(int byte_count) {/*ignore*/}
		public void flush() {}
	};



	/**
	 * Register a new piece data request.
	 * @param piece_number
	 * @param piece_offset
	 * @param length
	 */
	public boolean addPieceRequest(int piece_number, int piece_offset, int length) {
		if (destroyed )	return ( false);

		DiskManagerReadRequest dmr = peer.getManager().getDiskManager().createReadRequest(piece_number, piece_offset, length);

		try {
			lockMon.enter();

			requests.addLast(dmr);

		} finally {
			lockMon.exit();
		}

		doReadAheadLoads();

		return (true);
	}


	/**
	 * Remove an outstanding piece data request.
	 * @param piece_number
	 * @param piece_offset
	 * @param length
	 */
	public void removePieceRequest(int piece_number, int piece_offset, int length) {
		if (destroyed)	return;

		DiskManagerReadRequest dmr = peer.getManager().getDiskManager().createReadRequest(piece_number, piece_offset, length);

		boolean	inform_rejected = false;

		try {
			lockMon.enter();

			if (requests.contains( dmr )) {
				requests.remove(dmr);
				inform_rejected = true;
				return;
			}

			if (loadingMessages.contains( dmr )) {
				loadingMessages.remove(dmr);
				inform_rejected = true;
				return;
			}


			for (Iterator i = queuedMessages.entrySet().iterator(); i.hasNext();) {
				Map.Entry entry = (Map.Entry)i.next();
				if (entry.getValue().equals( dmr )) {	//it's already been queued
					BTPiece msg = (BTPiece)entry.getKey();
					if (outgoingMessageQueue.removeMessage( msg, true )) {
					inform_rejected = true;
						i.remove();
					}
					break;	//do manual listener notify
				}
			}
		} finally {

			lockMon.exit();

			if (inform_rejected) {

	 			peer.sendRejectRequest(dmr);
			}
		}

		outgoingMessageQueue.doListenerNotifications();
	}



	/**
	 * Remove all outstanding piece data requests.
	 */
	public void removeAllPieceRequests() {
		if (destroyed)	return;

		List<DiskManagerReadRequest> removed = new ArrayList<DiskManagerReadRequest>();

		try {
			lockMon.enter();

			// removed this trace as Alon can't remember why the trace is here anyway and as far as I can
			// see there's nothing to stop a piece being delivered to transport and removed from
			// the message queue before we're notified of this and thus it is entirely possible that
			// our view of queued messages is lagging.
			// String before_trace = outgoing_message_queue.getQueueTrace();
			/*
			int num_queued = queued_messages.size();
			int num_removed = 0;

			for (Iterator i = queued_messages.keySet().iterator(); i.hasNext();) {
				BTPiece msg = (BTPiece)i.next();
				if (outgoing_message_queue.removeMessage( msg, true )) {
					i.remove();
					num_removed++;
				}
			}

			if (num_removed < num_queued -2) {
				Debug.out("num_removed[" +num_removed+ "] < num_queued[" +num_queued+ "]:\nBEFORE:\n" +before_trace+ "\nAFTER:\n" +outgoing_message_queue.getQueueTrace());
			}
			*/


			for (Iterator<BTPiece> i = queuedMessages.keySet().iterator(); i.hasNext();) {
					BTPiece msg = i.next();
					if (outgoingMessageQueue.removeMessage(msg, true)) {
						removed.add(queuedMessages.get( msg));
					}
			}

			queuedMessages.clear();	// this replaces stuff above

			removed.addAll(requests);

			requests.clear();

			removed.addAll(loadingMessages);

			loadingMessages.clear();
		}
		finally{
			lockMon.exit();
		}

		for (DiskManagerReadRequest request: removed) {

			 peer.sendRejectRequest(request);
		}

		outgoingMessageQueue.doListenerNotifications();
	}



	public void setRequestReadAhead(int num_to_read_ahead) {
		requestReadAhead = num_to_read_ahead;
	}



	public void destroy() {
		try {
			lockMon.enter();

			removeAllPieceRequests();

			queuedMessages.clear();

			destroyed = true;

			outgoingMessageQueue.cancelQueueListener(sentMessageListener);
		}
		finally{
			lockMon.exit();
		}
	}


	private void doReadAheadLoads() {
		List toSubmit = null;
		try {
			lockMon.enter();

			while (loadingMessages.size() + queuedMessages.size() < requestReadAhead && !requests.isEmpty() && !destroyed) {
				DiskManagerReadRequest dmr = (DiskManagerReadRequest)requests.removeFirst();
				loadingMessages.add(dmr);
				if (toSubmit == null) toSubmit = new ArrayList();
				toSubmit.add(dmr);
			}
		} finally {
			lockMon.exit();
		}

		/*
		if (peer.getIp().equals("64.71.5.2")) {
			TimeFormatter.milliTrace("obt read_ahead: -> " + (to_submit==null?0:to_submit.size()) +
					" [lo=" + loading_messages.size() + ",qm=" + queued_messages.size() + ",re=" + requests.size() + ",rl=" + request_read_ahead + "]");
		}
		*/

		if (toSubmit != null) {
			for (int i=0;i<toSubmit.size();i++) {
				peer.getManager().getAdapter().enqueueReadRequest(peer, (DiskManagerReadRequest)toSubmit.get(i), readReqListener);
			}
		}
	}

	/**
	 * Get a list of piece numbers being requested
	 *
	 * @return list of Long values
	 */
	public int[] getRequestedPieceNumbers() {
		if (destroyed)	return new int[0];

		/** Cheap hack to reduce (but not remove all) the # of duplicate entries */
		int iLastNumber = -1;
		int pos = 0;
		int[] pieceNumbers;

		try {
			lockMon.enter();

			// allocate max size needed (we'll shrink it later)
			pieceNumbers = new int[queuedMessages.size()	+ loadingMessages.size() + requests.size()];

			for (Iterator iter = queuedMessages.keySet().iterator(); iter.hasNext();) {
				BTPiece msg = (BTPiece) iter.next();
				if (iLastNumber != msg.getPieceNumber()) {
					iLastNumber = msg.getPieceNumber();
					pieceNumbers[pos++] = iLastNumber;
				}
			}

			for (Iterator iter = loadingMessages.iterator(); iter.hasNext();) {
				DiskManagerReadRequest dmr = (DiskManagerReadRequest) iter.next();
				if (iLastNumber != dmr.getPieceNumber()) {
					iLastNumber = dmr.getPieceNumber();
					pieceNumbers[pos++] = iLastNumber;
				}
			}

			for (Iterator iter = requests.iterator(); iter.hasNext();) {
				DiskManagerReadRequest dmr = (DiskManagerReadRequest) iter.next();
				if (iLastNumber != dmr.getPieceNumber()) {
					iLastNumber = dmr.getPieceNumber();
					pieceNumbers[pos++] = iLastNumber;
				}
			}

		} finally {
			lockMon.exit();
		}

		int[] trimmed = new int[pos];
		System.arraycopy(pieceNumbers, 0, trimmed, 0, pos);

		return trimmed;
	}

	public int getRequestCount() {
		return ( queuedMessages.size()	+ loadingMessages.size() + requests.size());
	}

	public boolean isStalledPendingLoad() {
		return (queuedMessages.size() == 0 && loadingMessages.size() > 0);
	}
}
