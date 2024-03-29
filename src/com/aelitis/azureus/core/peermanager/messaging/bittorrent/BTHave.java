/*
 * Created on Apr 30, 2004
 * Created by Alon Rohter
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
package com.aelitis.azureus.core.peermanager.messaging.bittorrent;
import org.gudy.azureus2.core3.util.*;

import com.aelitis.azureus.core.peermanager.messaging.Message;
import com.aelitis.azureus.core.peermanager.messaging.MessageException;

import hello.util.Log;
/**
 * BitTorrent have message.
 */
public class BTHave implements BTMessage {
	
	private static String TAG = BTHave.class.getSimpleName();
	
	private final byte version;
	private DirectByteBuffer buffer = null;
	private String description = null;
	private final int piece_number;
	public BTHave(int piece_number, byte version) {
		this.piece_number = piece_number;
		this.version = version;
	}
	public int getPieceNumber() {	return piece_number;	}
	public String getID() {	return BTMessage.ID_BT_HAVE;	}
	public byte[] getIDBytes() {	return BTMessage.ID_BT_HAVE_BYTES;	}
	public String getFeatureID() {	return BTMessage.BT_FEATURE_ID;	}
	public int getFeatureSubID() {	return BTMessage.SUBID_BT_HAVE;	}
	public int getType() {	return Message.TYPE_PROTOCOL_PAYLOAD;	}
	public byte getVersion() { return version; };
	public String getDescription() {
		if (description == null) {
			description = BTMessage.ID_BT_HAVE + " piece #" + piece_number;
		}
		return description;
	}
	
	public DirectByteBuffer[] getData() {
		
		/*Log.d(TAG, "getData() is called...");
		Throwable t = new Throwable();
		t.printStackTrace();*/
		
		if (buffer == null) {
			buffer = DirectByteBufferPool.getBuffer(DirectByteBuffer.AL_MSG_BT_HAVE, 4);
			buffer.putInt(DirectByteBuffer.SS_MSG, piece_number);
			buffer.flip(DirectByteBuffer.SS_MSG);
		}
		return new DirectByteBuffer[]{ buffer };
	}
	
	public Message deserialize(DirectByteBuffer data, byte version) throws MessageException {
		if (data == null) {
			throw new MessageException("[" +getID() + "] decode error: data == null");
		}
		if (data.remaining(DirectByteBuffer.SS_MSG) != 4) {
			throw new MessageException("[" +getID() + "] decode error: payload.remaining[" +data.remaining( DirectByteBuffer.SS_MSG )+ "] != 4");
		}
		int number = data.getInt(DirectByteBuffer.SS_MSG);
		if (number < 0) {
			throw new MessageException("[" +getID() + "] decode error: number < 0");
		}
		data.returnToPool();
		return new BTHave(number, version);
	}
	
	public void destroy() {
		if (buffer != null)	buffer.returnToPool();
	}
}
