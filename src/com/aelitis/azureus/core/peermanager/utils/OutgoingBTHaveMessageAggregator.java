/*
 * Created on Jul 18, 2004
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

import org.gudy.azureus2.core3.util.AEMonitor;

import com.aelitis.azureus.core.networkmanager.*;
import com.aelitis.azureus.core.peermanager.messaging.*;
import com.aelitis.azureus.core.peermanager.messaging.azureus.AZHave;
import com.aelitis.azureus.core.peermanager.messaging.azureus.AZMessage;
import com.aelitis.azureus.core.peermanager.messaging.bittorrent.*;


/**
 * Utility class to enable write aggregation of BT Have messages,
 * in order to save bandwidth by not wasting a whole network packet
 * on a single small 9-byte message, and instead pad them onto other
 * messages.
 */
public class OutgoingBTHaveMessageAggregator {

	private final ArrayList pendingHaves	= new ArrayList();
	private final AEMonitor	pendingHavesMon	= new AEMonitor("OutgoingBTHaveMessageAggregator:PH");

	private byte btHaveVersion;
	private byte azHaveVersion;

	private boolean destroyed = false;

	private final OutgoingMessageQueue outgoingMessageQ;

	private final OutgoingMessageQueue.MessageQueueListener addedMessageListener = new OutgoingMessageQueue.MessageQueueListener() {
		public boolean messageAdded(Message message) { return true; }
		public void messageQueued(Message message) {
			//if another message is going to be sent anyway, add our haves as well
			String messageId = message.getID();
			if (
				!(messageId.equals(BTMessage.ID_BT_HAVE) || messageId.equals(AZMessage.ID_AZ_HAVE))
			) {
				sendPendingHaves();
			}
		}
		public void messageRemoved(Message message) {/*nothing*/}
		public void messageSent(Message message) {/*nothing*/}
		public void protocolBytesSent(int byte_count) {/*ignore*/}
		public void dataBytesSent(int byte_count) {/*ignore*/}
		public void flush() {}
	};


	/**
	 * Create a new aggregator, which will send messages out the given queue.
	 * @param outgoingMessageQ
	 */
	public OutgoingBTHaveMessageAggregator(
		OutgoingMessageQueue 	outgoingMessageQ,
		byte 					_btHaveVersion,
		byte					_azHaveVersion)
	{
		this.outgoingMessageQ = outgoingMessageQ;
		btHaveVersion	= _btHaveVersion;
		azHaveVersion	= _azHaveVersion;

		outgoingMessageQ.registerQueueListener(addedMessageListener);
	}

	public void setHaveVersion(byte	bt_version, byte az_version) {
		btHaveVersion 	= bt_version;
		azHaveVersion	= az_version;
	}
	
	/**
	 * Queue a new have message for aggregated sending.
	 * @param pieceNumber of the have message
	 * @param force if true, send this and any other pending haves right away
	 */
	public void queueHaveMessage(int pieceNumber, boolean force) {
		
		if (destroyed) return;
		
		try {
			pendingHavesMon.enter();
			pendingHaves.add(new Integer(pieceNumber));
			if (force) {
				sendPendingHaves();
			} else {
				int pending_bytes = pendingHaves.size() * 9;
				if (pending_bytes >= outgoingMessageQ.getMssSize()) {
					//System.out.println("enough pending haves for a full packet!");
					//there's enough pending bytes to fill a packet payload
					sendPendingHaves();
				}
			}
		} finally {
			pendingHavesMon.exit();
		}
	}


	/**
	 * Destroy the aggregator, along with any pending messages.
	 */
	public void destroy() {
		try {
			pendingHavesMon.enter();

			pendingHaves.clear();
			destroyed = true;
		}
		finally{
			pendingHavesMon.exit();
		}
	}


	/**
	 * Force send of any aggregated/pending have messages.
	 */
	public void forceSendOfPending() {
		sendPendingHaves();
	}



	/**
	 * Are there Haves messages pending?
	 * @return true if there are any unsent haves, false otherwise
	 */
	public boolean hasPending() {	return !pendingHaves.isEmpty();	}


	private void sendPendingHaves() {
		
		if (destroyed) return;
		
		try {
			pendingHavesMon.enter();
			int	numHaves = pendingHaves.size();
			if (numHaves == 0) {
				return;
			}
				// single have -> use BT
			if (numHaves == 1 || azHaveVersion < BTMessageFactory.MESSAGE_VERSION_SUPPORTS_PADDING) {
				for (int i=0; i < numHaves; i++) {
					Integer pieceNum = (Integer)pendingHaves.get(i);
					outgoingMessageQ.addMessage(new BTHave(pieceNum.intValue(), btHaveVersion), true);
				}
			} else {
				int[] pieceNumbers = new int[numHaves];
				for (int i=0; i < numHaves; i++) {
					pieceNumbers[i] = ((Integer)pendingHaves.get(i)).intValue();
				}
				outgoingMessageQ.addMessage(new AZHave(pieceNumbers, azHaveVersion), true);
			}
			outgoingMessageQ.doListenerNotifications();
			pendingHaves.clear();
		} finally {
			pendingHavesMon.exit();
		}
	}

}
