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
import java.net.InetSocketAddress;

import org.gudy.azureus2.core3.util.SystemTime;

import com.aelitis.azureus.core.dht.DHT;
import com.aelitis.azureus.core.dht.transport.DHTTransportException;
import com.aelitis.azureus.core.dht.transport.udp.DHTTransportUDP;
import com.aelitis.azureus.core.dht.transport.udp.impl.packethandler.DHTUDPPacketNetworkHandler;
import com.aelitis.net.udp.uc.PRUDPPacketRequest;

/**
 * @author parg
 *
 */

public class DHTUDPPacketRequest
	extends 	PRUDPPacketRequest
	implements 	DHTUDPPacket {
	
	public static final int	DHT_HEADER_SIZE	=
		PRUDPPacketRequest.PR_HEADER_SIZE +
		1 + 		// protocol version
		1 + 		// originator version
		4 + 		// network
		4 +			// instance id
		8 +			// time
		DHTUDPUtils.INETSOCKETADDRESS_IPV4_SIZE +
		1 +			// flags
		1;			// flags2

	private final DHTTransportUDPImpl	transport;

	private byte				protocolVersion;
	private byte				vendorId	= DHTTransportUDP.VENDOR_ID_NONE;
	private int					network;

	private byte				originatorVersion;
	private long				originatorTime;
	private InetSocketAddress	originatorAddress;
	private int					originatorInstanceId;
	private byte				flags;
	private byte				flags2;

	private long				skew;

	public DHTUDPPacketRequest(
		DHTTransportUDPImpl				_transport,
		int								_type,
		long							_connectionId,
		DHTTransportUDPContactImpl		_localContact,
		DHTTransportUDPContactImpl		_remoteContact) {
		
		super(_type, _connectionId);
		transport = _transport;
		
		// serialisation constructor
		protocolVersion = _remoteContact.getProtocolVersion();
		//System.out.println("request to " + _remote_contact.getAddress() + ", proto=" + protocol_version);
		
		// the target might be at a higher protocol version that us, so trim back if necessary
		// as we obviously can't talk a higher version than what we are!
		if (protocolVersion > _transport.getProtocolVersion())
			protocolVersion = _transport.getProtocolVersion();
		
		originatorAddress		= _localContact.getExternalAddress();
		originatorInstanceId	= _localContact.getInstanceID();
		originatorTime			= SystemTime.getCurrentTime();
		flags	= transport.getGenericFlags();
		flags2	= transport.getGenericFlags2();
	}

	protected DHTUDPPacketRequest(
		DHTUDPPacketNetworkHandler		networkHandler,
		DataInputStream					is,
		int								type,
		long							conId,
		int								transId)
		throws IOException
	{
		super(type, conId, transId);
		
		// deserialisation constructor
		protocolVersion	= is.readByte();
		//System.out.println("request received prot=" + protocol_version);
		if (protocolVersion >= DHTTransportUDP.PROTOCOL_VERSION_VENDOR_ID) {
			vendorId	= is.readByte();
		}
		if (protocolVersion >= DHTTransportUDP.PROTOCOL_VERSION_NETWORKS) {
			network	= is.readInt();
		}
		if (protocolVersion < (network == DHT.NW_CVS?DHTTransportUDP.PROTOCOL_VERSION_MIN_CVS:DHTTransportUDP.PROTOCOL_VERSION_MIN)) {
			throw (DHTUDPUtils.INVALID_PROTOCOL_VERSION_EXCEPTION);
		}
		
		// we can only get the correct transport after decoding the network...
		transport = networkHandler.getTransport(this);
		if (protocolVersion >= DHTTransportUDP.PROTOCOL_VERSION_FIX_ORIGINATOR) {
			originatorVersion = is.readByte();
		} else {
			// this should be set correctly in the post-deserialise code, however default
			// it for now
			originatorVersion = protocolVersion;
		}
		originatorAddress		= DHTUDPUtils.deserialiseAddress(is);
		originatorInstanceId	= is.readInt();
		originatorTime			= is.readLong();
		
		// We maintain a rough view of the clock diff between them and us,
		// times are then normalised appropriately.
		// If the skew is positive then this means our clock is ahead of their
		// clock. Thus any times they send us will need to have the skew added in
		// so that they're correct relative to us.
		// For example: X has clock = 01:00, they create a value that expires at
		// X+8 hours 09:00. They send X to us. Our clock is an hour ahead (skew=+1hr)
		// We receive it at 02:00 (our time) and therefore time it out an hour early.
		// We therefore need to adjust the creation time to be 02:00.
		// Likewise, when we return a time to a caller we need to adjust by - skew to
		// put the time into their frame of reference.
		skew = SystemTime.getCurrentTime() - originatorTime;
		transport.recordSkew(originatorAddress, skew);
		if (protocolVersion >= DHTTransportUDP.PROTOCOL_VERSION_PACKET_FLAGS) {
			flags	= is.readByte();
		}
		if (protocolVersion >= DHTTransportUDP.PROTOCOL_VERSION_PACKET_FLAGS2) {
			flags2	= is.readByte();
		}
	}

	protected void postDeserialise(DataInputStream is)
		throws IOException {
		
		if (protocolVersion < DHTTransportUDP.PROTOCOL_VERSION_FIX_ORIGINATOR) {
			if (is.available() > 0) {
				originatorVersion	= is.readByte();
			} else {
				originatorVersion = protocolVersion;
			}
			
			// if the originator is a higher version than us then we can't do anything sensible
			// working at their version (e.g. we can't reply to them using that version).
			// Therefore trim their perceived version back to something we can deal with
			if (originatorVersion > getTransport().getProtocolVersion())
				originatorVersion = getTransport().getProtocolVersion();
		}
	}

	public void	serialise(DataOutputStream os) throws IOException {
		super.serialise(os);
		// add to this and you need to amend HEADER_SIZE above
		os.writeByte(protocolVersion);
		if (protocolVersion >= DHTTransportUDP.PROTOCOL_VERSION_VENDOR_ID) {
			os.writeByte(DHTTransportUDP.VENDOR_ID_ME);
		}
		if (protocolVersion >= DHTTransportUDP.PROTOCOL_VERSION_NETWORKS) {
			os.writeInt(network);
		}
		if (protocolVersion >= DHTTransportUDP.PROTOCOL_VERSION_FIX_ORIGINATOR) {
			// originator version
			os.writeByte(getTransport().getProtocolVersion());
		}
		
		try {
			DHTUDPUtils.serialiseAddress(os, originatorAddress);
		} catch (DHTTransportException e) {
			throw (new IOException(e.getMessage()));
		}
		os.writeInt(originatorInstanceId);
		os.writeLong(originatorTime);
		if (protocolVersion >= DHTTransportUDP.PROTOCOL_VERSION_PACKET_FLAGS) {
			os.writeByte(flags);
		}
		if (protocolVersion >= DHTTransportUDP.PROTOCOL_VERSION_PACKET_FLAGS2) {
			os.writeByte(flags2);
		}
	}

	protected void postSerialise(DataOutputStream os) throws IOException {
		if (protocolVersion < DHTTransportUDP.PROTOCOL_VERSION_FIX_ORIGINATOR) {
			// originator version is at tail so it works with older versions
			os.writeByte(getTransport().getProtocolVersion());
		}
	}

	public DHTTransportUDPImpl getTransport() {
		return (transport);
	}

	protected long getClockSkew() {
		return (skew);
	}

	public byte getProtocolVersion() {
		return (protocolVersion);
	}

	protected byte getVendorID() {
		return (vendorId);
	}

	public int getNetwork() {
		return (network);
	}

	public void setNetwork(int _network) {
		network	= _network;
	}

	public byte getGenericFlags() {
		return (flags);
	}

	public byte getGenericFlags2() {
		return (flags2);
	}

	protected byte getOriginatorVersion() {
		return (originatorVersion);
	}

	protected InetSocketAddress getOriginatorAddress() {
		return (originatorAddress);
	}

	protected void setOriginatorAddress(InetSocketAddress address) {
		originatorAddress	= address;
	}

	protected int getOriginatorInstanceID() {
		return (originatorInstanceId);
	}

	public String getString() {
		return (super.getString() + ",[prot=" + protocolVersion + ",ven=" + vendorId + ",net="+network+",ov=" + originatorVersion + ",fl=" + flags + "/" + flags2 + "]");
	}
}
