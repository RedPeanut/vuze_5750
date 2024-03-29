/*
 * Created on 1 Nov 2006
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


package com.aelitis.azureus.core.networkmanager.admin.impl;

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

public class NetworkAdminNATUDPCodecs {
	
	public static final int ACT_NAT_REQUEST	= 40;
	public static final int ACT_NAT_REPLY	= 41;

	private static boolean	registered	= false;

	static{
		registerCodecs();
	}

	public static void registerCodecs() {
		
		if (registered)
			return;
		
		registered	= true;
		PRUDPPacketReplyDecoder	reply_decoder =
			new PRUDPPacketReplyDecoder() {
			
				public PRUDPPacketReply decode(
					PRUDPPacketHandler	handler,
					InetSocketAddress	originator,
					DataInputStream		is,
					int					action,
					int					transaction_id )
					throws IOException
				{
					switch(action) {
						case ACT_NAT_REPLY:
							return (new NetworkAdminNATUDPReply(is, transaction_id));
						default:
							throw (new IOException("Unrecognised action '" + action + "'"));
					}
				}
			};
		Map	reply_decoders = new HashMap();
		reply_decoders.put(new Integer(ACT_NAT_REPLY), reply_decoder);
		PRUDPPacketReply.registerDecoders(reply_decoders);
		
		PRUDPPacketRequestDecoder requestDecoder =
			new PRUDPPacketRequestDecoder() {
				public PRUDPPacketRequest decode(
					PRUDPPacketHandler	handler,
					DataInputStream		is,
					long				connection_id,
					int					action,
					int					transaction_id )
					throws IOException
				{
					switch(action) {
						case ACT_NAT_REQUEST:
							return (new NetworkAdminNATUDPRequest(is, connection_id, transaction_id));
						default:
							throw (new IOException("unsupported request type"));
					}
				}
			};
		Map<Integer, PRUDPPacketRequestDecoder> requestDecoders = new HashMap<>();
		requestDecoders.put(new Integer(ACT_NAT_REQUEST), requestDecoder);
		PRUDPPacketRequest.registerDecoders(requestDecoders);
	}
}
