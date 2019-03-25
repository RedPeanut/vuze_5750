/*
 * Created on 12-Jun-2005
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

package com.aelitis.azureus.core.dht.transport.udp.impl.packethandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.gudy.azureus2.core3.util.AEMonitor;
import org.gudy.azureus2.core3.util.Debug;

import com.aelitis.azureus.core.dht.transport.udp.impl.DHTTransportUDPImpl;
import com.aelitis.azureus.core.dht.transport.udp.impl.DHTUDPPacketRequest;
import com.aelitis.net.udp.uc.PRUDPPacketHandler;
import com.aelitis.net.udp.uc.PRUDPPacketHandlerFactory;

import hello.util.Log;
import hello.util.Util;

public class DHTUDPPacketHandlerFactory {
	
	private static String TAG = DHTUDPPacketHandlerFactory.class.getSimpleName();
	
	private static final DHTUDPPacketHandlerFactory	singleton = new DHTUDPPacketHandlerFactory();
	private final Map<Integer, Object[]> portMap = new HashMap<>();
	protected final AEMonitor	thisMon = new AEMonitor("DHTUDPPacketHandlerFactory");

	public static DHTUDPPacketHandler getHandler(
		DHTTransportUDPImpl		transport,
		DHTUDPRequestHandler	requestHandler)
		throws DHTUDPPacketHandlerException {
		return (singleton.getHandlerSupport(transport, requestHandler));
	}

	protected DHTUDPPacketHandler getHandlerSupport(
		DHTTransportUDPImpl		transport,
		DHTUDPRequestHandler	requestHandler)
		throws DHTUDPPacketHandlerException {
		
		try {
			thisMon.enter();
			int	port	= transport.getPort();
			int	network = transport.getNetwork();
			Object[] portDetails = (Object[])portMap.get(new Integer(port));
			if (portDetails == null) {
				PRUDPPacketHandler packetHandler =
					PRUDPPacketHandlerFactory.getHandler(
							port,
							new DHTUDPPacketNetworkHandler(this, port));

				portDetails = new Object[]{ packetHandler, new HashMap<Integer, Object[]>()};
				portMap.put(new Integer(port), portDetails);
				Log.d(TAG, String.format("portMap.put(%d) is called...", port));
				Util.printMap("", portMap, "");
			}
			
			Map<Integer, Object[]> networkMap = (Map)portDetails[1];
			Object[] networkDetails = (Object[])networkMap.get(new Integer(network));
			if (networkDetails != null) {
				throw (new DHTUDPPacketHandlerException("Network already added"));
			}
			DHTUDPPacketHandler ph = new DHTUDPPacketHandler(this, network, (PRUDPPacketHandler)portDetails[0], requestHandler);
			networkMap.put(new Integer(network), new Object[]{ transport, ph });
			return (ph);
		} finally {
			thisMon.exit();
		}
	}

	protected void destroy(
		DHTUDPPacketHandler	handler) {
		PRUDPPacketHandler	packet_handler = handler.getPacketHandler();
		int	port 	= packet_handler.getPort();
		int	network = handler.getNetwork();
		try {
			thisMon.enter();
			Object[]	port_details = (Object[])portMap.get(new Integer( port));
			if (port_details == null) {
				return;
			}
			Map network_map = (Map)port_details[1];
			network_map.remove(new Integer( network));
			if (network_map.size() == 0) {
				portMap.remove(new Integer( port));
				try {
					packet_handler.setRequestHandler(null);
				} catch (Throwable e) {
					Debug.printStackTrace(e);
				}
			}
		} finally {
			thisMon.exit();
		}
	}

	protected void process(int port, DHTUDPPacketRequest request) {
		try {
			int network = request.getNetwork();
			/*
			if (network != 0) {
				System.out.println("process:" + network + ":" + request.getString()); 
			}
			*/
			Object[] portDetails = (Object[]) portMap.get(new Integer(port));
			if (portDetails == null) {
				throw (new IOException("Port '" + port + "' not registered"));
			}

			Map networkMap = (Map) portDetails[1];
			Object[] networkDetails = (Object[]) networkMap.get(new Integer(network));
			if (networkDetails == null) {
				throw (new IOException("Network '" + network + "' not registered"));
			}

			DHTUDPPacketHandler packetHandler = (DHTUDPPacketHandler) networkDetails[1];
			packetHandler.receive(request);
		} catch (IOException e) {
			Debug.printStackTrace(e);
		}
	}

	public DHTTransportUDPImpl getTransport(
		int		port,
		int		network)
		throws IOException
	{
		Object[]	port_details = (Object[])portMap.get(new Integer( port));
		if (port_details == null) {
			throw (new IOException("Port '" + port + "' not registered"));
		}
		Map network_map = (Map)port_details[1];
		Object[]	network_details = (Object[])network_map.get(new Integer( network));
		if (network_details == null) {
			throw (new IOException("Network '" + network + "' not registered"));
		}
		return ((DHTTransportUDPImpl)network_details[0]);
	}
}
