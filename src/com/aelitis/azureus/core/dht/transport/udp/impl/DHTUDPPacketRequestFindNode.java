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

public class DHTUDPPacketRequestFindNode extends DHTUDPPacketRequest {
	
	private byte[]		id;

	private int			nodeStatus;
	private int			estimatedDhtSize;

	public DHTUDPPacketRequestFindNode(
		DHTTransportUDPImpl				_transport,
		long							_connectionId,
		DHTTransportUDPContactImpl		_localContact,
		DHTTransportUDPContactImpl		_remoteContact) {
		super(_transport, DHTUDPPacketHelper.ACT_REQUEST_FIND_NODE, _connectionId, _localContact, _remoteContact);
	}

	protected DHTUDPPacketRequestFindNode(
		DHTUDPPacketNetworkHandler		networkHandler,
		DataInputStream					is,
		long							connectionId,
		int								transactionId)
		throws IOException
	{
		super(networkHandler, is, DHTUDPPacketHelper.ACT_REQUEST_FIND_NODE, connectionId, transactionId);

		id = DHTUDPUtils.deserialiseByteArray(is, 64);

		if (getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_MORE_NODE_STATUS) {
			nodeStatus 			= is.readInt();
			estimatedDhtSize 	= is.readInt();
		}

		super.postDeserialise(is);
	}

	public void serialise(DataOutputStream os)
		throws IOException {
		super.serialise(os);
		DHTUDPUtils.serialiseByteArray(os, id, 64);
		if (getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_MORE_NODE_STATUS) {
			 os.writeInt(nodeStatus);
			 os.writeInt(estimatedDhtSize);
		}
		super.postSerialise(os);
	}

	protected void setID(byte[] _id) {
		id	= _id;
	}

	protected byte[] getID() {
		return (id);
	}

	protected void setNodeStatus(int ns) {
		nodeStatus	= ns;
	}

	protected int getNodeStatus() {
		return (nodeStatus);
	}

	protected void setEstimatedDHTSize(int s) {
		estimatedDhtSize = s;
	}

	protected int getEstimatedDHTSize() {
		return (estimatedDhtSize);
	}

	public String getString() {
		return (super.getString());
	}
}