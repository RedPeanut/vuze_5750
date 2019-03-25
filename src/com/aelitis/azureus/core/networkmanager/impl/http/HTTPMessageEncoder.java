/*
 * Created on 2 Oct 2006
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
package com.aelitis.azureus.core.networkmanager.impl.http;
import com.aelitis.azureus.core.networkmanager.RawMessage;
import com.aelitis.azureus.core.peermanager.messaging.Message;
import com.aelitis.azureus.core.peermanager.messaging.MessageStreamEncoder;
import com.aelitis.azureus.core.peermanager.messaging.bittorrent.BTMessage;

import hello.util.Log;

public class HTTPMessageEncoder implements MessageStreamEncoder {
	
	private static String TAG = HTTPMessageEncoder.class.getSimpleName();
	
	private HTTPNetworkConnection	httpConnection;
	public void setConnection(HTTPNetworkConnection _http_connection) {
		httpConnection	= _http_connection;
	}
	
	public RawMessage[] encodeMessage(Message message) {
		Log.d(TAG, "encodeMessage() is called...");
		String id = message.getID();
		// System.out.println("encodeMessage: " + message.getID());
		RawMessage	raw_message = null;
		if (id.equals(BTMessage.ID_BT_HANDSHAKE)) {
			raw_message = httpConnection.encodeHandShake(message);
		} else if (id.equals(BTMessage.ID_BT_CHOKE)) {
			raw_message = httpConnection.encodeChoke();
		} else if (id.equals(BTMessage.ID_BT_UNCHOKE)) {
			raw_message = httpConnection.encodeUnchoke();
		} else if (id.equals(BTMessage.ID_BT_BITFIELD)) {
			raw_message = httpConnection.encodeBitField();
		} else if (id.equals(BTMessage.ID_BT_PIECE)) {
			return (httpConnection.encodePiece(message));
		} else if (id.equals(HTTPMessage.MSG_ID)) {
			raw_message = ((HTTPMessage)message).encode(message);
		}
		if (raw_message == null) {
			raw_message = httpConnection.getEmptyRawMessage(message);
		}
		return (new RawMessage[]{ raw_message });
	}
}
