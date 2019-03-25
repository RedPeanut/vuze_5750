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

public class DHTUDPPacketReplyFindNode
	extends DHTUDPPacketReply
{
	private DHTTransportContact[]	contacts;
	private int						randomId;
	private int						nodeStatus	= DHTTransportUDPContactImpl.NODE_STATUS_UNKNOWN;
	private int						estimatedDhtSize;

	public DHTUDPPacketReplyFindNode(
		DHTTransportUDPImpl				transport,
		DHTUDPPacketRequestFindNode		request,
		DHTTransportContact				localContact,
		DHTTransportContact				remoteContact) {
		super(transport, DHTUDPPacketHelper.ACT_REPLY_FIND_NODE, request, localContact, remoteContact);
	}

	protected DHTUDPPacketReplyFindNode(
		DHTUDPPacketNetworkHandler		networkHandler,
		InetSocketAddress				originator,
		DataInputStream					is,
		int								transId)
		throws IOException
	{
		super(networkHandler, originator, is, DHTUDPPacketHelper.ACT_REPLY_FIND_NODE, transId);

		if (getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_ANTI_SPOOF) {
			randomId = is.readInt();
		}

		if (getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_XFER_STATUS) {
			nodeStatus = is.readInt();
		}

		if (getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_SIZE_ESTIMATE) {
			estimatedDhtSize = is.readInt();
		}

		if (getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_VIVALDI) {
			DHTUDPUtils.deserialiseVivaldi(this, is);
		}

		contacts = DHTUDPUtils.deserialiseContacts(getTransport(), is);
	}

	public void serialise(DataOutputStream os)
		throws IOException {
		super.serialise(os);
		if (getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_ANTI_SPOOF) {
			os.writeInt(randomId);
		}
		if (getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_XFER_STATUS) {
			 os.writeInt(nodeStatus);
		}
		if (getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_SIZE_ESTIMATE) {
			 os.writeInt(estimatedDhtSize);
		}
		if (getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_VIVALDI) {
			DHTUDPUtils.serialiseVivaldi(this, os);
		}
		DHTUDPUtils.serialiseContacts(os, contacts);
	}

	protected void setContacts(DHTTransportContact[] _contacts) {
		contacts = _contacts;
	}

	protected void setRandomID(int _random_id) {
		randomId = _random_id;
	}

	protected int getRandomID() {
		return (randomId);
	}

	protected void setNodeStatus(
		int		ns) {
		nodeStatus	= ns;
	}

	protected int getNodeStatus() {
		return (nodeStatus);
	}

	protected void setEstimatedDHTSize(
		int	s) {
		estimatedDhtSize	= s;
	}

	protected int getEstimatedDHTSize() {
		return (estimatedDhtSize);
	}

	protected DHTTransportContact[]
	getContacts() {
		return (contacts);
	}

	public String getString() {
		return (super.getString() + ",contacts=" + (contacts==null?"null":(""+contacts.length)));
	}
}
