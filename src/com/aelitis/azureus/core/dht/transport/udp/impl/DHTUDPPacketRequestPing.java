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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import com.aelitis.azureus.core.dht.transport.udp.DHTTransportUDP;
import com.aelitis.azureus.core.dht.transport.udp.impl.packethandler.DHTUDPPacketNetworkHandler;


/**
 * @author parg
 *
 */

public class DHTUDPPacketRequestPing extends DHTUDPPacketRequest {
	
	private static final int[] 	EMPTY_INTS = {};

	private int[]	altNetworks				= EMPTY_INTS;
	private int[]	altNetworkCounts		= EMPTY_INTS;

	public DHTUDPPacketRequestPing(
		DHTTransportUDPImpl				_transport,
		long							_connection_id,
		DHTTransportUDPContactImpl		_local_contact,
		DHTTransportUDPContactImpl		_remote_contact) {
		super(_transport, DHTUDPPacketHelper.ACT_REQUEST_PING, _connection_id, _local_contact, _remote_contact);
	}

	protected DHTUDPPacketRequestPing(
		DHTUDPPacketNetworkHandler		networkHandler,
		DataInputStream					is,
		long							conId,
		int								transId)
		throws IOException
	{
		super(networkHandler, is, DHTUDPPacketHelper.ACT_REQUEST_PING, conId, transId);
		if (getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_ALT_CONTACTS) {
			DHTUDPUtils.deserialiseAltContactRequest(this, is);
		}
		super.postDeserialise(is);
	}

	public void serialise(DataOutputStream	os)
		throws IOException
	{
		super.serialise(os);
		if (getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_ALT_CONTACTS) {
			DHTUDPUtils.serialiseAltContactRequest(this, os);
		}
		super.postSerialise(os);
	}

	protected void setAltContactRequest(
		int[]	networks,
		int[]	counts) {
		altNetworks			= networks;
		altNetworkCounts	= counts;
	}

	protected int[] getAltNetworks() {
		return (altNetworks);
	}

	protected int[] getAltNetworkCounts() {
		return (altNetworkCounts);
	}

	public String getString() {
		return (super.getString());
	}
}