/*
 * Created on Jan 19, 2007
 * Created by Paul Gardner
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.	See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA	02111-1307, USA.
 *
 */

package com.aelitis.azureus.core.peermanager.messaging.azureus;
import java.util.HashMap;
import java.util.Map;
import org.gudy.azureus2.core3.util.DirectByteBuffer;
import com.aelitis.azureus.core.peermanager.messaging.Message;
import com.aelitis.azureus.core.peermanager.messaging.MessageException;
import com.aelitis.azureus.core.peermanager.messaging.MessagingUtil;
public class
AZStatRequest
	implements AZMessage
{
	private final byte version;
	private DirectByteBuffer buffer = null;
	private final Map	request;
	public AZStatRequest(
		Map		_request,
		byte	_version) {
		request		= _request;
		version		= _version;
	}
	public String getID() {
		return (AZMessage.ID_AZ_STAT_REQUEST);
	}
	public byte[]
	getIDBytes() {
		return (AZMessage.ID_AZ_STAT_REQUEST_BYTES);
	}
	public String getFeatureID() {
		return (AZMessage.AZ_FEATURE_ID);
	}
	public int getFeatureSubID() {
		return (AZMessage.SUBID_ID_AZ_STAT_REQUEST);
	}
	public int getType() {
		return (Message.TYPE_PROTOCOL_PAYLOAD);
	}
	public byte getVersion() { return version; };
	public String getDescription() {
		return (getID() + ": " + request);
	}
	public Map
	getRequest() {
		return (request);
	}
	public DirectByteBuffer[]
	getData() {
		if (buffer == null) {
			Map	map = new HashMap();
			map.put("request", request);
			buffer = MessagingUtil.convertPayloadToBencodedByteStream(map, DirectByteBuffer.AL_MSG);
		}
		return new DirectByteBuffer[]{ buffer };
	}
	public Message
	deserialize(
		DirectByteBuffer 	data,
		byte				version )
		throws MessageException
	{
		Map payload = MessagingUtil.convertBencodedByteStreamToPayload(data, 1, getID());
		Map request	= (Map)payload.get("request");
		return (new AZStatRequest( request, version));
	}
	public void destroy() {
		if (buffer != null) {
			buffer.returnToPool();
		}
	}
}