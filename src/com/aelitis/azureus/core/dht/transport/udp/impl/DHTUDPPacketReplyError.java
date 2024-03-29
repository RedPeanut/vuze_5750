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

import org.gudy.azureus2.core3.util.Debug;

import com.aelitis.azureus.core.dht.transport.DHTTransportContact;
import com.aelitis.azureus.core.dht.transport.DHTTransportException;
import com.aelitis.azureus.core.dht.transport.udp.impl.packethandler.DHTUDPPacketNetworkHandler;

public class DHTUDPPacketReplyError
	extends DHTUDPPacketReply
{
	public static final int	ET_UNKNOWN						= 0;
	public static final int	ET_ORIGINATOR_ADDRESS_WRONG		= 1;
	public static final int	ET_KEY_BLOCKED					= 2;

	private int						error_type	= ET_UNKNOWN;

	private InetSocketAddress		originatorAddress;
	private byte[]					key_block_request;
	private byte[]					key_block_signature;

	public DHTUDPPacketReplyError(
		DHTTransportUDPImpl		transport,
		DHTUDPPacketRequest		request,
		DHTTransportContact		localContact,
		DHTTransportContact		remoteContact) {
		super(transport, DHTUDPPacketHelper.ACT_REPLY_ERROR, request, localContact, remoteContact);
	}

	protected DHTUDPPacketReplyError(
		DHTUDPPacketNetworkHandler		network_handler,
		InetSocketAddress				originator,
		DataInputStream					is,
		int								transId)
		throws IOException
	{
		super(network_handler, originator, is, DHTUDPPacketHelper.ACT_REPLY_ERROR, transId);
		error_type = is.readInt();
		if (error_type == ET_ORIGINATOR_ADDRESS_WRONG) {
			originatorAddress	= DHTUDPUtils.deserialiseAddress(is);
		} else if (error_type == ET_KEY_BLOCKED) {
			key_block_request	= DHTUDPUtils.deserialiseByteArray(is, 255);
			key_block_signature = DHTUDPUtils.deserialiseByteArray(is, 65535);
		}
	}

	protected void setErrorType(int error) {
		error_type	= error;
	}

	protected int getErrorType() {
		return (error_type);
	}

	protected void setOriginatingAddress(InetSocketAddress a) {
		originatorAddress = a;
	}

	protected InetSocketAddress getOriginatingAddress() {
		return (originatorAddress);
	}

	protected void setKeyBlockDetails(
		byte[]	kbr,
		byte[]	sig) {
		key_block_request	= kbr;
		key_block_signature = sig;
	}

	protected byte[] getKeyBlockRequest() {
		return (key_block_request);
	}

	protected byte[] getKeyBlockSignature() {
   		return (key_block_signature);
   	}

	public void serialise(DataOutputStream os)
		throws IOException
	{
		super.serialise(os);
		os.writeInt(error_type);
		if (error_type == ET_ORIGINATOR_ADDRESS_WRONG) {
			try {
				DHTUDPUtils.serialiseAddress(os, originatorAddress);
			} catch (DHTTransportException e) {
				Debug.printStackTrace(e);
				throw (new IOException( e.getMessage()));
			}
		} else if (error_type == ET_KEY_BLOCKED) {
			DHTUDPUtils.serialiseByteArray(os, key_block_request, 255);
			DHTUDPUtils.serialiseByteArray(os, key_block_signature, 65535);
		}
	}
}
