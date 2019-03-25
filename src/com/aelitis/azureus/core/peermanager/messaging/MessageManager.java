/*
 * Created on Jan 8, 2005
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
package com.aelitis.azureus.core.peermanager.messaging;
import java.util.*;

import org.gudy.azureus2.core3.util.*;

import com.aelitis.azureus.core.peermanager.messaging.azureus.AZMessageFactory;
import com.aelitis.azureus.core.peermanager.messaging.bittorrent.BTMessageFactory;
import com.aelitis.azureus.core.peermanager.messaging.bittorrent.ltep.LTMessageFactory;

import hello.util.Log;


/**
 *
 */
public class MessageManager {
	
	private static String TAG = MessageManager.class.getSimpleName();
	
	private static final MessageManager instance = new MessageManager();
	private final ByteArrayHashMap 	messageMap 	= new ByteArrayHashMap();
	private final List				messages		= new ArrayList();
	protected final AEMonitor	thisMon = new AEMonitor("MessageManager");
	
	private MessageManager() {
		/*nothing*/
	}

	public static MessageManager getSingleton() {	return instance;	}

	/**
	 * Perform manager initialization.
	 */
	public void initialize() {
		AZMessageFactory.init();	//register AZ message types
		BTMessageFactory.init();	//register BT message types
		LTMessageFactory.init();	//register LT message types
	}


	/**
	 * Register the given message type with the manager for processing.
	 * @param message instance to use for decoding
	 * @throws MessageException if this message type has already been registered
	 */
	public void registerMessageType(Message message) throws MessageException {
		try {
			thisMon.enter();
			byte[]	idBytes = message.getIDBytes();
			if (messageMap.containsKey(idBytes)) {
				throw new MessageException("message type [" +message.getID()+ "] already registered!");
			}
			messageMap.put(idBytes, message);
			messages.add(message);
		} finally {
			thisMon.exit();
		}
	}

	/**
	 * Remove registration of given message type from manager.
	 * @param message type to remove
	 */
	public void deregisterMessageType(Message message) {
		try {	
			thisMon.enter();
			messageMap.remove(message.getIDBytes());
			messages.remove(message);
		} finally{	
			thisMon.exit();	
		}
	}

	/**
	 * Construct a new message instance from the given message information.
	 * @param id of message
	 * @param messageData payload
	 * @return decoded/deserialized message
	 * @throws MessageException if message creation failed
	 */
	public Message createMessage(byte[] idBytes, DirectByteBuffer messageData, byte version) 
			throws MessageException {
		
		/*Log.d(TAG, "createMessage() is called...");
		Log.d(TAG, "idBytes = " + new String(idBytes));
		
		Throwable t = new Throwable();
		t.printStackTrace();*/
		
		Message message = (Message)messageMap.get(idBytes);
		if (message == null) {
			throw new MessageException("message id[" + new String(idBytes) + "] not registered");
		}
		return message.deserialize(messageData, version);
	}

	/**
	 * Lookup a registered message type via id and version.
	 * @param id to look for
	 * @return the default registered message instance if found, otherwise returns null if this message type is not registered
	 */
	public Message lookupMessage(String id) {
		return (Message)messageMap.get(id.getBytes());
	}

	public Message lookupMessage(byte[] id_bytes) {
		return (Message)messageMap.get(id_bytes);
	}

	/**
	 * Get a list of the registered messages.
	 * @return messages
	 */
	public Message[] getRegisteredMessages() {
		return (Message[])messages.toArray(new Message[messages.size()]);
	}

}
