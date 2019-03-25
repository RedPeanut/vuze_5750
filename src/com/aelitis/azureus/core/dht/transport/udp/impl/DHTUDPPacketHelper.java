/*
 * Created on 21-Jan-2005
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

package com.aelitis.azureus.core.dht.transport.udp.impl;

import java.io.*;
import java.net.*;
import java.util.*;


import com.aelitis.azureus.core.dht.transport.udp.impl.packethandler.DHTUDPPacketNetworkHandler;
import com.aelitis.net.udp.uc.PRUDPPacketHandler;
import com.aelitis.net.udp.uc.PRUDPPacketReply;
import com.aelitis.net.udp.uc.PRUDPPacketReplyDecoder;
import com.aelitis.net.udp.uc.PRUDPPacketRequest;
import com.aelitis.net.udp.uc.PRUDPPacketRequestDecoder;


/**
 * @author parg
 *
 */

public class DHTUDPPacketHelper {
	
	public static final int		PACKET_MAX_BYTES		= 1400;

	// these actions have to co-exist with the tracker ones when the connection
	// is shared, hence 1024

	public static final int ACT_REQUEST_PING		= 0x400;
	public static final int ACT_REPLY_PING			= 0x401;
	public static final int ACT_REQUEST_STORE		= 0x402;
	public static final int ACT_REPLY_STORE			= 0x403;
	public static final int ACT_REQUEST_FIND_NODE	= 0x404;
	public static final int ACT_REPLY_FIND_NODE		= 0x405;
	public static final int ACT_REQUEST_FIND_VALUE	= 0x406;
	public static final int ACT_REPLY_FIND_VALUE	= 0x407;
	
	public static final int ACT_REPLY_ERROR			= 0x408;
	public static final int ACT_REPLY_STATS			= 0x409;
	public static final int ACT_REQUEST_STATS		= 0x40A;
	
	public static final int ACT_DATA				= 0x40B;
	
	public static final int ACT_REQUEST_KEY_BLOCK	= 0x40C;
	public static final int ACT_REPLY_KEY_BLOCK		= 0x40D;
	
	public static final int ACT_REQUEST_QUERY_STORE	= 0x40E;
	public static final int ACT_REPLY_QUERY_STORE	= 0x40F;

	private static boolean	registered				= false;

	protected static void registerCodecs() {
		
		if (registered)
			return;
		
		registered = true;

		PRUDPPacketRequestDecoder requestDecoder =
			new PRUDPPacketRequestDecoder() {
			
				public PRUDPPacketRequest decode(
					PRUDPPacketHandler	handler,
					DataInputStream		is,
					long				connectionId,
					int					action,
					int					transactionId)
					throws IOException
				{
					if (handler == null) {
						// most likely cause is DHT packet ending up on the UDP tracker as it'll get
						// router here but with a null-handler
						throw (new IOException("No handler available for DHT packet decode"));
					}
					
					DHTUDPPacketNetworkHandler networkHandler = (DHTUDPPacketNetworkHandler)handler.getRequestHandler();
					if (networkHandler == null) {
						// we an get this after a port change and the old port listener is still running (e.g.
						// its still doing UDP tracker)
						throw (new IOException("No network handler available for DHT packet decode"));
					}
					
					switch(action) {
						case ACT_REQUEST_PING:
							return (new DHTUDPPacketRequestPing(networkHandler, is, connectionId, transactionId));
						case ACT_REQUEST_STORE:
							return (new DHTUDPPacketRequestStore(networkHandler, is, connectionId, transactionId));
						case ACT_REQUEST_FIND_NODE:
							return (new DHTUDPPacketRequestFindNode(networkHandler, is, connectionId, transactionId));
						case ACT_REQUEST_FIND_VALUE:
							return (new DHTUDPPacketRequestFindValue(networkHandler, is, connectionId, transactionId));
						case ACT_REQUEST_STATS:
							return (new DHTUDPPacketRequestStats(networkHandler, is, connectionId, transactionId));
						case ACT_DATA:
							return (new DHTUDPPacketData(networkHandler, is, connectionId, transactionId));
						case ACT_REQUEST_KEY_BLOCK:
							return (new DHTUDPPacketRequestKeyBlock(networkHandler, is, connectionId, transactionId));
						case ACT_REQUEST_QUERY_STORE:
							return (new DHTUDPPacketRequestQueryStorage(networkHandler, is, connectionId, transactionId));
						default:
							throw new IOException("Unknown action type");
					}
				}
			};

		Map<Integer, PRUDPPacketRequestDecoder> requestDecoders = new HashMap<>();

		requestDecoders.put(new Integer(ACT_REQUEST_PING), requestDecoder);
		requestDecoders.put(new Integer(ACT_REQUEST_STORE), requestDecoder);
		requestDecoders.put(new Integer(ACT_REQUEST_FIND_NODE), requestDecoder);
		requestDecoders.put(new Integer(ACT_REQUEST_FIND_VALUE), requestDecoder);
		requestDecoders.put(new Integer(ACT_REQUEST_STATS), requestDecoder);
		requestDecoders.put(new Integer(ACT_DATA), requestDecoder);
		requestDecoders.put(new Integer(ACT_REQUEST_KEY_BLOCK), requestDecoder);
		requestDecoders.put(new Integer(ACT_REQUEST_QUERY_STORE), requestDecoder);

		PRUDPPacketRequest.registerDecoders(requestDecoders);

		PRUDPPacketReplyDecoder	replyDecoder =
			new PRUDPPacketReplyDecoder() {
			
				public PRUDPPacketReply decode(
					PRUDPPacketHandler	handler,
					InetSocketAddress	originator,
					DataInputStream		is,
					int					action,
					int					transactionId)
					throws IOException {
					
					if (handler == null) {
						// most likely cause is DHT packet ending up on the UDP tracker as it'll get
						// router here but with a null-handler
						throw (new IOException("No handler available for DHT packet decode"));
					}

					DHTUDPPacketNetworkHandler networkHandler = (DHTUDPPacketNetworkHandler)handler.getRequestHandler();

					if (networkHandler == null) {
						// we an get this after a port change and the old port listener is still running (e.g.
						// its still doing UDP tracker)
						throw (new IOException("No network handler available for DHT packet decode"));
					}

					switch(action) {
						case ACT_REPLY_PING:
							return (new DHTUDPPacketReplyPing(networkHandler, originator, is, transactionId));
						case ACT_REPLY_STORE:
							return (new DHTUDPPacketReplyStore(networkHandler, originator, is, transactionId));
						case ACT_REPLY_FIND_NODE:
							return (new DHTUDPPacketReplyFindNode(networkHandler, originator, is, transactionId));
						case ACT_REPLY_FIND_VALUE:
							return (new DHTUDPPacketReplyFindValue(networkHandler, originator, is, transactionId));
						case ACT_REPLY_ERROR:
							return (new DHTUDPPacketReplyError(networkHandler, originator, is, transactionId));
						case ACT_REPLY_STATS:
							return (new DHTUDPPacketReplyStats(networkHandler, originator, is, transactionId));
						case ACT_REPLY_KEY_BLOCK:
							return (new DHTUDPPacketReplyKeyBlock(networkHandler, originator, is, transactionId));
						case ACT_REPLY_QUERY_STORE:
							return (new DHTUDPPacketReplyQueryStorage(networkHandler, originator, is, transactionId));
						default:
							throw (new IOException("Unknown action type"));
					}
				}
			};

		Map<Integer, PRUDPPacketReplyDecoder> replyDecoders = new HashMap<>();

		replyDecoders.put(new Integer(ACT_REPLY_PING), replyDecoder);
		replyDecoders.put(new Integer(ACT_REPLY_STORE), replyDecoder);
		replyDecoders.put(new Integer(ACT_REPLY_FIND_NODE), replyDecoder);
		replyDecoders.put(new Integer(ACT_REPLY_FIND_VALUE), replyDecoder);
		replyDecoders.put(new Integer(ACT_REPLY_ERROR), replyDecoder);
		replyDecoders.put(new Integer(ACT_REPLY_STATS), replyDecoder);
		replyDecoders.put(new Integer(ACT_REPLY_KEY_BLOCK), replyDecoder);
		replyDecoders.put(new Integer(ACT_REPLY_QUERY_STORE), replyDecoder);

		PRUDPPacketReply.registerDecoders(replyDecoders);
	}
}
