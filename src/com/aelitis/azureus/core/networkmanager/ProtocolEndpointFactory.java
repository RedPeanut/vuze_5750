/*
 * Created on Oct 14, 2010
 * Created by Paul Gardner
 *
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
 */


package com.aelitis.azureus.core.networkmanager;

import java.net.InetSocketAddress;
import java.util.*;

import com.aelitis.azureus.core.networkmanager.impl.tcp.ProtocolEndpointTCP;
import com.aelitis.azureus.core.networkmanager.impl.udp.ProtocolEndpointUDP;

public class
ProtocolEndpointFactory
{
	private static ProtocolEndpointHandler tcpHandler = null;
	private static ProtocolEndpointHandler udpHandler = null;

	private static final Map<Integer,ProtocolEndpointHandler>	otherHandlers = new HashMap<Integer, ProtocolEndpointHandler>();

	static{
		ProtocolEndpointTCP.register();
		ProtocolEndpointUDP.register();
	}

	public static void
	registerHandler(
		ProtocolEndpointHandler		handler) {
		int	type = handler.getType();

		if (type == ProtocolEndpoint.PROTOCOL_TCP) {

			tcpHandler = handler;

		} else if (type == ProtocolEndpoint.PROTOCOL_UDP) {

			udpHandler = handler;

		} else {

			otherHandlers.put(type, handler);
		}
	}

	public static boolean
	isHandlerRegistered(
		int		type) {
		if (type == ProtocolEndpoint.PROTOCOL_TCP || type == ProtocolEndpoint.PROTOCOL_UDP) {

			return (true);

		} else {

			return (otherHandlers.containsKey( type));
		}
	}

	public static ProtocolEndpoint
	createEndpoint(
		int						type,
		InetSocketAddress		target) {
		switch(type) {
			case ProtocolEndpoint.PROTOCOL_TCP:{
				return (tcpHandler.create( target));
			}
			case ProtocolEndpoint.PROTOCOL_UDP:{
				return (udpHandler.create( target));
			}
			default:{
				ProtocolEndpointHandler handler = otherHandlers.get(type);
				if (handler != null) {
					return (handler.create( target));
				}
				return (null);
			}
		}
	}

	public static ProtocolEndpoint createEndpoint(
		int						type,
		ConnectionEndpoint		connectionEndpoint,
		InetSocketAddress		target) {
		switch(type) {
			case ProtocolEndpoint.PROTOCOL_TCP:{
				return (tcpHandler.create(connectionEndpoint, target));
			}
			case ProtocolEndpoint.PROTOCOL_UDP:{
				return (udpHandler.create(connectionEndpoint, target));
			}
			default:{
				ProtocolEndpointHandler handler = otherHandlers.get(type);
				if (handler != null) {
					return (handler.create(connectionEndpoint, target));
				}
				return (null);
			}
		}
	}
}
