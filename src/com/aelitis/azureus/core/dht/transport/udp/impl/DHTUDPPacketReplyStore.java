/*
 * File    : PRUDPPacketReplyConnect.java
 * Created : 20-Jan-2004
 * By      : parg
 *
 * Azureus - a Java Bittorrent client
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details (see the LICENSE file).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.aelitis.azureus.core.dht.transport.udp.impl;

/**
 * @author parg
 *
 */

import java.io.*;
import java.net.InetSocketAddress;

import com.aelitis.azureus.core.dht.transport.DHTTransportContact;
import com.aelitis.azureus.core.dht.transport.udp.DHTTransportUDP;
import com.aelitis.azureus.core.dht.transport.udp.impl.packethandler.DHTUDPPacketNetworkHandler;

public class
DHTUDPPacketReplyStore
	extends DHTUDPPacketReply
{
	private byte[]	diversify;

	public DHTUDPPacketReplyStore(
		DHTTransportUDPImpl			transport,
		DHTUDPPacketRequestStore	request,
		DHTTransportContact			local_contact,
		DHTTransportContact			remote_contact) {
		super(transport, DHTUDPPacketHelper.ACT_REPLY_STORE, request, local_contact, remote_contact);
	}

	protected DHTUDPPacketReplyStore(
		DHTUDPPacketNetworkHandler		network_handler,
		InetSocketAddress				originator,
		DataInputStream					is,
		int								trans_id )

		throws IOException
	{
		super(network_handler, originator, is, DHTUDPPacketHelper.ACT_REPLY_STORE, trans_id);

		if (getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_DIV_AND_CONT) {

			diversify = DHTUDPUtils.deserialiseByteArray(is, DHTUDPPacketRequestStore.MAX_KEYS_PER_PACKET);
		}
	}

	public void serialise(
		DataOutputStream	os )

		throws IOException
	{
		super.serialise(os);

		if (getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_DIV_AND_CONT) {

			DHTUDPUtils.serialiseByteArray(os, diversify, DHTUDPPacketRequestStore.MAX_KEYS_PER_PACKET);
		}
	}

	public void setDiversificationTypes(
		byte[]		_diversify) {
		diversify	= _diversify;
	}

	public byte[]
	getDiversificationTypes() {
		return (diversify);
	}
}
