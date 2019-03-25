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
import com.aelitis.azureus.core.dht.transport.DHTTransportContact;
import com.aelitis.azureus.core.dht.transport.udp.DHTTransportUDP;
import com.aelitis.azureus.core.dht.transport.udp.impl.packethandler.DHTUDPPacketNetworkHandler;
import com.aelitis.azureus.core.dht.netcoords.*;
import com.aelitis.net.udp.uc.PRUDPPacketReply;

/**
 * @author parg
 *
 */

public class DHTUDPPacketReply
	extends 	PRUDPPacketReply
	implements 	DHTUDPPacket
{
	public static final int	DHT_HEADER_SIZE	=
		PRUDPPacketReply.PR_HEADER_SIZE +
		8 +		// con id
		1 +		// ver
		1 +		// net
		4 +		// instance
		1 + 	// flags
		1 +		// flags2
		2;		// proc time

	private DHTTransportUDPImpl 	transport;

	private long	connectionId;
	private byte	protocolVersion;
	private byte	vendorId	= DHTTransportUDP.VENDOR_ID_NONE;
	private int		network;
	private int		targetInstanceId;
	private byte	flags;
	private byte	flags2;

	private long	skew;

	private DHTNetworkPosition[]	network_positions;

	private short	processingTime;

	private long	requestReceiveTime;

	public DHTUDPPacketReply(
		DHTTransportUDPImpl	_transport,
		int					_type,
		DHTUDPPacketRequest	_request,
		DHTTransportContact	_local_contact,
		DHTTransportContact	_remote_contact) {
		
		super(_type, _request.getTransactionId());
		
		transport			= _transport;
		connectionId		= _request.getConnectionId();
		protocolVersion		= _remote_contact.getProtocolVersion();
		//System.out.println("reply to " + _remote_contact.getAddress() + ", proto=" + protocol_version);
		
		// the target might be at a higher protocol version that us, so trim back if necessary
		// as we obviously can't talk a higher version than what we are!
		if (protocolVersion > _transport.getProtocolVersion()) {
			protocolVersion = _transport.getProtocolVersion();
		}
		targetInstanceId	= _local_contact.getInstanceID();
		skew				= _remote_contact.getClockSkew();
		flags				= transport.getGenericFlags();
		flags2				= transport.getGenericFlags2();
		requestReceiveTime	= _request.getReceiveTime();
	}

	protected DHTUDPPacketReply(
		DHTUDPPacketNetworkHandler		networkHandler,
		InetSocketAddress				originator,
		DataInputStream					is,
		int								type,
		int								transId)
		throws IOException
	{
		super(type, transId);
		setAddress(originator);
		connectionId 	= is.readLong();
		protocolVersion	= is.readByte();
		//System.out.println("reply prot=" + protocol_version);
		if (protocolVersion >= DHTTransportUDP.PROTOCOL_VERSION_VENDOR_ID) {
			vendorId = is.readByte();
		}
		if (protocolVersion >= DHTTransportUDP.PROTOCOL_VERSION_NETWORKS) {
			network = is.readInt();
		}
		if (protocolVersion < ( network == DHT.NW_CVS?DHTTransportUDP.PROTOCOL_VERSION_MIN_CVS:DHTTransportUDP.PROTOCOL_VERSION_MIN)) {
			throw (DHTUDPUtils.INVALID_PROTOCOL_VERSION_EXCEPTION);
		}
		
		// we can only get the correct transport after decoding the network...
		transport			= networkHandler.getTransport(this);
		targetInstanceId	= is.readInt();
		if (protocolVersion >= DHTTransportUDP.PROTOCOL_VERSION_PACKET_FLAGS) {
			flags = is.readByte();
		}
		if (protocolVersion >= DHTTransportUDP.PROTOCOL_VERSION_PACKET_FLAGS2) {
			flags2 = is.readByte();
		}
		if (protocolVersion >= DHTTransportUDP.PROTOCOL_VERSION_PROC_TIME) {
			processingTime = is.readShort();
		}
	}

	public DHTTransportUDPImpl getTransport() {
		return (transport);
	}

	protected int getTargetInstanceID() {
		return (targetInstanceId);
	}

	public long getConnectionId() {
		return (connectionId);
	}

	protected long getClockSkew() {
		return (skew);
	}

	public byte
	getProtocolVersion() {
		return (protocolVersion);
	}

	protected byte
	getVendorID() {
		return (vendorId);
	}

	public int getNetwork() {
		return (network);
	}

	public byte
	getGenericFlags() {
		return (flags);
	}

	public byte
	getGenericFlags2() {
		return (flags2);
	}

	public void setNetwork(
		int		_network) {
		network	= _network;
	}

	protected DHTNetworkPosition[]
	getNetworkPositions() {
		return (network_positions);
	}

	protected void setNetworkPositions(
		DHTNetworkPosition[] _network_positions) {
		network_positions = _network_positions;
	}

	public void serialise(DataOutputStream os)
		throws IOException
	{
		super.serialise(os);
		// add to this and you need to adjust HEADER_SIZE above
		os.writeLong(connectionId);
		os.writeByte(protocolVersion);
		if (protocolVersion >= DHTTransportUDP.PROTOCOL_VERSION_VENDOR_ID) {
			os.writeByte(DHTTransportUDP.VENDOR_ID_ME);
		}
		if (protocolVersion >= DHTTransportUDP.PROTOCOL_VERSION_NETWORKS) {
			os.writeInt(network);
		}
		os.writeInt(targetInstanceId);
		if (protocolVersion >= DHTTransportUDP.PROTOCOL_VERSION_PACKET_FLAGS) {
			os.writeByte(flags);
		}
		if (protocolVersion >= DHTTransportUDP.PROTOCOL_VERSION_PACKET_FLAGS2) {
			os.writeByte(flags2);
		}
		if (protocolVersion >= DHTTransportUDP.PROTOCOL_VERSION_PROC_TIME) {
			if (requestReceiveTime == 0) {
				os.writeShort(0);
			} else {
				short processing_time = (short)(SystemTime.getCurrentTime() - requestReceiveTime);
				if (processing_time <= 0) {
					processing_time = 1;	// min value
				}
				os.writeShort(processing_time);
			}
		}
	}

	public long getProcessingTime() {
		return (processingTime & 0x0000ffff);
	}

	public String getString() {
		return (super.getString() + ",[con="+connectionId+",prot=" + protocolVersion + ",ven=" + vendorId + ",net="+network + ",fl=" + flags + "/" + flags2 + "]");
	}
}