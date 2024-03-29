/*
 * Created on 1 Nov 2006
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


package com.aelitis.azureus.core.networkmanager.admin.impl;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;

import org.gudy.azureus2.core3.util.BDecoder;
import org.gudy.azureus2.core3.util.BEncoder;

import com.aelitis.net.udp.uc.PRUDPPacketRequest;

public class
NetworkAdminNATUDPRequest
	extends PRUDPPacketRequest
{
	private Map	payload;

	public NetworkAdminNATUDPRequest(
		long		connection_id) {
		super(NetworkAdminNATUDPCodecs.ACT_NAT_REQUEST, connection_id);
	}

	protected NetworkAdminNATUDPRequest(
		DataInputStream		is,
		long				connection_id ,
		int					trans_id )

		throws IOException
	{
		super(NetworkAdminNATUDPCodecs.ACT_NAT_REQUEST, connection_id, trans_id);

		short	len = is.readShort();

		if (len <= 0) {

			throw (new IOException("invalid length"));
		}

		byte[]	bytes = new byte[len];

		is.read(bytes);

		payload = BDecoder.decode(bytes);
	}

	public void serialise(
		DataOutputStream	os )

		throws IOException
	{
		super.serialise(os);

		byte[]	bytes = BEncoder.encode(payload);

		os.writeShort((short)bytes.length);

		os.write(bytes);
	}

	public Map
	getPayload() {
		return (payload);
	}

	public void setPayload(
		Map		_payload) {
		payload	= _payload;
	}

	public String getString() {
		return ( super.getString());
	}
}
