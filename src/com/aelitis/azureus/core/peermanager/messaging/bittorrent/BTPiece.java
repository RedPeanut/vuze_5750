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

/**
 * BitTorrent piece message.
 */
public class BTPiece implements BTMessage {
	
	private final byte version;
	private final DirectByteBuffer[] buffer = new DirectByteBuffer[2];
	private String description;
	private final int pieceNumber;
	private final int pieceOffset;
	private final int pieceLength;
	
	public BTPiece(int pieceNumber, int pieceOffset, DirectByteBuffer data, byte version) {
		this.pieceNumber = pieceNumber;
		this.pieceOffset = pieceOffset;
		this.pieceLength = data == null ? 0 : data.remaining(DirectByteBuffer.SS_MSG);
		buffer[1] = data;
		this.version = version;
	}
	
	public int getPieceNumber() {	return pieceNumber;	}
	public int getPieceOffset() {	return pieceOffset;	}
	public DirectByteBuffer getPieceData() {	return buffer[1];	}
	public String getID() {	return BTMessage.ID_BT_PIECE;	}
	public byte[] getIDBytes() {	return BTMessage.ID_BT_PIECE_BYTES;	}
	public String getFeatureID() {	return BTMessage.BT_FEATURE_ID;	}
	public int getFeatureSubID() {	return BTMessage.SUBID_BT_PIECE;	}
	public int getType() {	return Message.TYPE_DATA_PAYLOAD;	}
	public byte getVersion() { return version; };
	public String getDescription() {
		if (description == null) {
			description = BTMessage.ID_BT_PIECE + " data for piece #" + pieceNumber + ":" + pieceOffset + "->" + (pieceOffset + pieceLength -1);
		}
		return description;
	}
	
	public DirectByteBuffer[] getData() {
		if (buffer[0] == null) {
			buffer[0] = DirectByteBufferPool.getBuffer(DirectByteBuffer.AL_MSG_BT_PIECE, 8);
			buffer[0].putInt(DirectByteBuffer.SS_MSG, pieceNumber);
			buffer[0].putInt(DirectByteBuffer.SS_MSG, pieceOffset);
			buffer[0].flip(DirectByteBuffer.SS_MSG);
		}
		return buffer;
	}
	
	public Message deserialize(DirectByteBuffer data, byte version) throws MessageException {
		if (data == null) {
			throw new MessageException("[" +getID() + "] decode error: data == null");
		}
		if (data.remaining(DirectByteBuffer.SS_MSG) < 8) {
			throw new MessageException("[" +getID()+ "] decode error: payload.remaining[" +data.remaining(DirectByteBuffer.SS_MSG )+ "] < 8");
		}
		int number = data.getInt(DirectByteBuffer.SS_MSG);
		if (number < 0) {
			throw new MessageException("[" +getID() +"] decode error: number < 0");
		}
		int offset = data.getInt(DirectByteBuffer.SS_MSG);
		if (offset < 0) {
			throw new MessageException("[" +getID() + "] decode error: offset < 0");
		}
		return new BTPiece(number, offset, data, version);
	}
	
	public void destroy() {
		if (buffer[0] != null) buffer[0].returnToPool();
		if (buffer[1] != null) buffer[1].returnToPool();
	}
}
