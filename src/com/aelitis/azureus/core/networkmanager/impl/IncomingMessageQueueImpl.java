/*
 * Created on Oct 17, 2004
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

package com.aelitis.azureus.core.networkmanager.impl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import org.gudy.azureus2.core3.util.AEMonitor;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.DirectByteBuffer;

import com.aelitis.azureus.core.networkmanager.IncomingMessageQueue;
import com.aelitis.azureus.core.networkmanager.NetworkConnection;
import com.aelitis.azureus.core.peermanager.messaging.Message;
import com.aelitis.azureus.core.peermanager.messaging.MessageStreamDecoder;

import hello.util.Log;



/**
 * Inbound peer message queue.
 */
public class IncomingMessageQueueImpl implements IncomingMessageQueue {

	private static String TAG = IncomingMessageQueueImpl.class.getSimpleName();
	
	private volatile ArrayList<MessageQueueListener> listeners = new ArrayList<MessageQueueListener>();	//copy-on-write
	private final AEMonitor listeners_mon = new AEMonitor("IncomingMessageQueue:listeners");

	private MessageStreamDecoder streamDecoder;
	private final NetworkConnection connection;


	/**
	 * Create a new incoming message queue.
	 * @param streamDecoder default message stream decoder
	 * @param connection owner to read from
	 */
	public IncomingMessageQueueImpl(
			MessageStreamDecoder streamDecoder,
			NetworkConnection connection) {
		if (streamDecoder == null) {
			throw new NullPointerException("stream_decoder is null");
		}
		this.connection = connection;
		this.streamDecoder = streamDecoder;
	}


	/**
	 * Set the message stream decoder that will be used to decode incoming messages.
	 * @param newStreamDecoder to use
	 */
	public void setDecoder(MessageStreamDecoder newStreamDecoder) {
		
		/*Log.d(TAG, "setDecoder() is called...");
		Throwable t = new Throwable();
		t.printStackTrace();*/
		
		ByteBuffer alreadyRead = streamDecoder.destroy();
		connection.getTransport().setAlreadyRead(alreadyRead);
		streamDecoder = newStreamDecoder;
		streamDecoder.resumeDecoding();
	}

	public MessageStreamDecoder getDecoder() {
		return (streamDecoder);
	}

	/**
	 * Get the percentage of the current message that has already been received.
	 * @return percentage complete (0-99), or -1 if no message is currently being received
	 */
	public int getPercentDoneOfCurrentMessage() {
		return streamDecoder.getPercentDoneOfCurrentMessage();
	}



	/**
	 * Receive (read) message(s) data from the underlying transport.
	 * @param maxBytes to read
	 * @return number of bytes received
	 * @throws IOException on receive error
	 */
	public int[] receiveFromTransport(int maxBytes, boolean protocolIsFree) throws IOException {
		
		/*Log.d(TAG, "receiveFromTransport() is called...");
		Throwable t = new Throwable();
		t.printStackTrace();*/
		
		if (maxBytes < 1) {
			// Not yet fully supporting free-protocol for downloading
			if (!protocolIsFree) {
				Debug.out("max_bytes < 1: " +maxBytes);
			}
			return new int[2];
		}
		if (listeners.isEmpty()) {
			Debug.out("no queue listeners registered!");
			throw new IOException("no queue listeners registered!");
		}
		
		int bytesRead;
		try {
			//perform decode op
			bytesRead = streamDecoder.performStreamDecode(connection.getTransport(), maxBytes);
		} catch (RuntimeException e) {
			Debug.out("Stream decode for " + connection.getString() + " failed: " + Debug.getNestedExceptionMessageAndStack(e));
			throw (e);
		}
		
		//check if anything was decoded and notify listeners if so
		Message[] messages = streamDecoder.removeDecodedMessages();
		if (messages != null) {
			for (int i=0; i < messages.length; i++) {
				Message msg = messages[i];
				if (msg == null) {
					System.out.println("received msg == null [messages.length=" +messages.length+ ", #" +i+ "]: " +connection.getTransport().getDescription());
					continue;
				}
				ArrayList listeners_ref = listeners;	//copy-on-write
				boolean handled = false;
				for (int x=0; x < listeners_ref.size(); x++) {
					MessageQueueListener mql = (MessageQueueListener)listeners_ref.get(x);
					if (mql.messageReceived(msg)) {
						handled = true;
					}
				}
				if (!handled) {
					if (listeners_ref.size() > 0) {
						System.out.println("no registered listeners [out of " +listeners_ref.size()+ "] handled decoded message [" +msg.getDescription()+ "]");
					}
					DirectByteBuffer[] buffs = msg.getData();
					for (int x=0; x < buffs.length; x++) {
						buffs[x].returnToPool();
					}
				}
			}
		}
		
		int protocolRead = streamDecoder.getProtocolBytesDecoded();
		if (protocolRead > 0) {
			ArrayList listeners_ref = listeners;	//copy-on-write
			for (int i=0; i < listeners_ref.size(); i++) {
				MessageQueueListener mql = (MessageQueueListener)listeners_ref.get(i);
				mql.protocolBytesReceived(protocolRead);
			}
		}
		
		int dataRead = streamDecoder.getDataBytesDecoded();
		if (dataRead > 0) {
			ArrayList listeners_ref = listeners;	//copy-on-write
			for (int i=0; i < listeners_ref.size(); i++) {
				MessageQueueListener mql = (MessageQueueListener)listeners_ref.get(i);
				mql.dataBytesReceived(dataRead);
			}
		}
		
		// ideally bytes_read = data_read + protocol_read. in case it isn't then we want to
		// return bytes_read = d + p with bias to p
		dataRead = bytesRead - protocolRead;
		if (dataRead < 0) {
			protocolRead 	= bytesRead;
			dataRead		= 0;
		}
		return (new int[]{ dataRead, protocolRead });
	}

	/**
	 * Notifty the queue (and its listeners) of a message received externally on the queue's behalf.
	 * @param message received externally
	 */
	public void notifyOfExternallyReceivedMessage(Message message) throws IOException{
		ArrayList listeners_ref = listeners;	//copy-on-write
		boolean handled = false;

		DirectByteBuffer[] dbbs = message.getData();
		int size = 0;
		for (int i=0; i < dbbs.length; i++) {
			size += dbbs[i].remaining(DirectByteBuffer.SS_NET);
		}


		for (int x=0; x < listeners_ref.size(); x++) {
			MessageQueueListener mql = (MessageQueueListener)listeners_ref.get(x);
			if (mql.messageReceived( message)) {
				handled = true;
			}

			if (message.getType() == Message.TYPE_DATA_PAYLOAD) {
				mql.dataBytesReceived(size);
			}
			else {
				mql.protocolBytesReceived(size);
			}
		}

		if (!handled) {
			if (listeners_ref.size() > 0) {
				System.out.println("no registered listeners [out of " +listeners_ref.size()+ "] handled decoded message [" +message.getDescription()+ "]");
			}

			DirectByteBuffer[] buffs = message.getData();
			for (int x=0; x < buffs.length; x++) {
				buffs[ x ].returnToPool();
			}
		}
	}



	/**
	 * Manually resume processing (reading) incoming messages.
	 * NOTE: Allows us to resume docoding externally, in case it was auto-paused internally.
	 */
	public void resumeQueueProcessing() {
		streamDecoder.resumeDecoding();
	}



	/**
	 * Add a listener to be notified of queue events.
	 * @param listener
	 */
	public void registerQueueListener(MessageQueueListener listener) {
		try {	listeners_mon.enter();
			//copy-on-write
			ArrayList<MessageQueueListener> new_list = new ArrayList<MessageQueueListener>(listeners.size() + 1);

			if (listener.isPriority()) {
				boolean	added = false;
				for (int i=0;i<listeners.size();i++) {
					MessageQueueListener existing = listeners.get(i);
					if (added || existing.isPriority()) {
		 			} else {
						new_list.add(listener);
						added = true;
					}
	 				new_list.add(existing);
				}
				if (!added) {
					new_list.add(listener);
				}
			} else {
				new_list.addAll(listeners);
				new_list.add(listener);
			}
			listeners = new_list;
		}
		finally{	listeners_mon.exit();	}
	}


	/**
	 * Cancel queue event notification listener.
	 * @param listener
	 */
	public void cancelQueueListener(MessageQueueListener listener) {
		try {	listeners_mon.enter();
			//copy-on-write
			ArrayList new_list = new ArrayList(listeners);
			new_list.remove(listener);
			listeners = new_list;
		}
		finally{	listeners_mon.exit();	}
	}




	/**
	 * Destroy this queue.
	 */
	public void destroy() {
		streamDecoder.destroy();
	}

}
