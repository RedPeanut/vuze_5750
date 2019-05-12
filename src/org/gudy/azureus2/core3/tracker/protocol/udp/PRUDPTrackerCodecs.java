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

package org.gudy.azureus2.core3.tracker.protocol.udp;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

import com.aelitis.net.udp.uc.PRUDPPacketHandler;
import com.aelitis.net.udp.uc.PRUDPPacketReply;
import com.aelitis.net.udp.uc.PRUDPPacketReplyDecoder;
import com.aelitis.net.udp.uc.PRUDPPacketRequest;
import com.aelitis.net.udp.uc.PRUDPPacketRequestDecoder;

import hello.util.Log;


/**
 * @author parg
 *
 */

public class PRUDPTrackerCodecs {
	
	private static String TAG = PRUDPTrackerCodecs.class.getSimpleName();
	
	private static boolean registered = false;

	public static void registerCodecs() {
		
		//Log.d(TAG, "registerCodecs() is called...");
		
		if (registered)
			return;

		registered	= true;

		PRUDPPacketReplyDecoder	replyDecoder =
			new PRUDPPacketReplyDecoder() {
			
				public PRUDPPacketReply decode(
					PRUDPPacketHandler	handler,
					InetSocketAddress	originator,
					DataInputStream		is,
					int					action,
					int					transactionId)
					throws IOException
				{
					switch(action) {
						case PRUDPPacketTracker.ACT_REPLY_CONNECT:
							return (new PRUDPPacketReplyConnect(is, transactionId));
						case PRUDPPacketTracker.ACT_REPLY_ANNOUNCE:
							if (PRUDPPacketTracker.VERSION == 1)
								return (new PRUDPPacketReplyAnnounce(is, transactionId));
							else
								return (new PRUDPPacketReplyAnnounce2(is, transactionId));
						case PRUDPPacketTracker.ACT_REPLY_SCRAPE:
							if (PRUDPPacketTracker.VERSION == 1)
								return (new PRUDPPacketReplyScrape(is, transactionId));
							else
								return (new PRUDPPacketReplyScrape2(is, transactionId));
						case PRUDPPacketTracker.ACT_REPLY_ERROR:
							return (new PRUDPPacketReplyError(is, transactionId));
						default:
							throw (new IOException("Unrecognised action '" + action + "'"));
					}
				}
			};

		Map	replyDecoders = new HashMap();

		replyDecoders.put(new Integer(PRUDPPacketTracker.ACT_REPLY_CONNECT), replyDecoder);
		replyDecoders.put(new Integer(PRUDPPacketTracker.ACT_REPLY_ANNOUNCE), replyDecoder);
		replyDecoders.put(new Integer(PRUDPPacketTracker.ACT_REPLY_SCRAPE), replyDecoder);
		replyDecoders.put(new Integer(PRUDPPacketTracker.ACT_REPLY_ERROR), replyDecoder);

		PRUDPPacketReply.registerDecoders(replyDecoders);

		PRUDPPacketRequestDecoder	request_decoder =
			new PRUDPPacketRequestDecoder() {
				public PRUDPPacketRequest
				decode(
					PRUDPPacketHandler	handler,
					DataInputStream		is,
					long				connection_id,
					int					action,
					int					transaction_id )
					throws IOException
				{
					switch(action) {
						case PRUDPPacketTracker.ACT_REQUEST_CONNECT:
							return (new PRUDPPacketRequestConnect(is, connection_id,transaction_id));
						case PRUDPPacketTracker.ACT_REQUEST_ANNOUNCE:
							if (PRUDPPacketTracker.VERSION == 1) {
								return (new PRUDPPacketRequestAnnounce(is, connection_id,transaction_id));
							} else {
								return (new PRUDPPacketRequestAnnounce2(is, connection_id,transaction_id));
							}
						case PRUDPPacketTracker.ACT_REQUEST_SCRAPE:
							return (new PRUDPPacketRequestScrape(is, connection_id,transaction_id));
						default:
							throw (new IOException("unsupported request type"));
					}
				}
			};

		Map	request_decoders = new HashMap();

		request_decoders.put(new Integer( PRUDPPacketTracker.ACT_REQUEST_CONNECT ), request_decoder);
		request_decoders.put(new Integer( PRUDPPacketTracker.ACT_REQUEST_ANNOUNCE ), request_decoder);
		request_decoders.put(new Integer( PRUDPPacketTracker.ACT_REQUEST_SCRAPE ), request_decoder);

		PRUDPPacketRequest.registerDecoders(request_decoders);

	}
}
