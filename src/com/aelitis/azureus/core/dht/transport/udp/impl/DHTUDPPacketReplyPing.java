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
import java.util.List;

import com.aelitis.azureus.core.dht.transport.DHTTransportAlternativeContact;
import com.aelitis.azureus.core.dht.transport.DHTTransportContact;
import com.aelitis.azureus.core.dht.transport.udp.DHTTransportUDP;
import com.aelitis.azureus.core.dht.transport.udp.impl.packethandler.DHTUDPPacketNetworkHandler;

public class DHTUDPPacketReplyPing
	extends DHTUDPPacketReply
{
	private static final DHTTransportAlternativeContact[] EMPTY_CONTACTS = {};

	private DHTTransportAlternativeContact[]	altContacts = EMPTY_CONTACTS;

	public DHTUDPPacketReplyPing(
		DHTTransportUDPImpl		transport,
		DHTUDPPacketRequestPing	request,
		DHTTransportContact		local_contact,
		DHTTransportContact		remote_contact) {
		super(transport, DHTUDPPacketHelper.ACT_REPLY_PING, request, local_contact, remote_contact);
	}

	protected DHTUDPPacketReplyPing(
		DHTUDPPacketNetworkHandler		network_handler,
		InetSocketAddress				originator,
		DataInputStream					is,
		int								transId)
		throws IOException
	{
		super(network_handler, originator, is, DHTUDPPacketHelper.ACT_REPLY_PING, transId);
		if (getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_VIVALDI) {
			DHTUDPUtils.deserialiseVivaldi(this, is);
		}
		if (getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_ALT_CONTACTS) {
			altContacts = DHTUDPUtils.deserialiseAltContacts(is);
		}
	}

	public void	serialise(DataOutputStream os)
		throws IOException
	{
		super.serialise(os);
		if (getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_VIVALDI) {
			DHTUDPUtils.serialiseVivaldi(this, os);
		}
		if (getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_ALT_CONTACTS) {
			DHTUDPUtils.serialiseAltContacts(os, altContacts);
		}
	}

	protected void setAltContacts(List<DHTTransportAlternativeContact> _contacts) {
		final int MAX_CONTACTS = 16;
		if (_contacts.size() < MAX_CONTACTS) {
			altContacts = _contacts.toArray(new DHTTransportAlternativeContact[ _contacts.size()]);
		} else {
			altContacts = new DHTTransportAlternativeContact[MAX_CONTACTS];
			for ( int i=0;i<altContacts.length;i++) {
				altContacts[i] = _contacts.get(i);
			}
		}
	}

	protected DHTTransportAlternativeContact[] getAltContacts() {
		return (altContacts);
	}

}
