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
import org.gudy.azureus2.core3.util.ByteFormatter;
import org.gudy.azureus2.core3.util.DirectByteBuffer;
import org.gudy.azureus2.core3.util.DirectByteBufferPool;
import org.gudy.azureus2.core3.util.RandomUtils;

import com.aelitis.azureus.core.networkmanager.RawMessage;
import com.aelitis.azureus.core.peermanager.messaging.Message;
import com.aelitis.azureus.core.peermanager.messaging.MessageException;
import com.aelitis.azureus.core.peermanager.utils.PeerClassifier;

import hello.util.Log;
/**
 * BitTorrent handshake message.
 */
public class BTHandshake implements BTMessage, RawMessage {
	
	private static String TAG = BTHandshake.class.getSimpleName();
	
	public static final String PROTOCOL = "BitTorrent protocol";
	// No reserve bits set.
	private static final byte[] BT_RESERVED = new byte[]{0, 0, 0, 0, 0, 0, 0, 0 };
	private static final byte[] LT_RESERVED = new byte[]{0, 0, 0, 0, 0, (byte)16, 0, 0 };
	
	// Set first bit of first byte to indicate advanced AZ messaging support. (128)
	// Set fourth bit of fifth byte to indicate LT messaging support. (16)
	// Set seventh bit (2) and eight bit (1) to force AZMP over LTEP. [current behaviour]
	// Set seventh bit (2) only to prefer AZMP over LTEP.
	// Set eighth bit (1) only to prefer LTEP over AZMP.
	private static final byte[] AZ_RESERVED = new byte[]{(byte)128, 0, 0, 0, 0, (byte)19, 0, 0 };
	public static final int BT_RESERVED_MODE	= 0;
	public static final int LT_RESERVED_MODE	= 1;
	public static final int AZ_RESERVED_MODE	= 2;
	private static final byte[][] RESERVED = { BT_RESERVED, LT_RESERVED, AZ_RESERVED };
	public static void setMainlineDHTEnabled(boolean enabled) {
		if (enabled) {
			LT_RESERVED[7] = (byte)(LT_RESERVED[7] | 0x01);
			AZ_RESERVED[7] = (byte)(AZ_RESERVED[7] | 0x01);
		} else {
			LT_RESERVED[7] = (byte)(LT_RESERVED[7] & 0xFE);
			AZ_RESERVED[7] = (byte)(AZ_RESERVED[7] & 0xFE);
		}
	}
	
	public static final boolean FAST_EXTENSION_ENABLED = true;
	public static void setFastExtensionEnabled(boolean enabled) {
		if (enabled) {
			LT_RESERVED[7] = (byte)(LT_RESERVED[7] | 0x04);
			AZ_RESERVED[7] = (byte)(AZ_RESERVED[7] | 0x04);
		}
		else {
			LT_RESERVED[7] = (byte)(LT_RESERVED[7] & 0xF3);
			AZ_RESERVED[7] = (byte)(AZ_RESERVED[7] & 0xF3);
		}
	}
	
	static {
		setFastExtensionEnabled(FAST_EXTENSION_ENABLED);
	}
	
	private DirectByteBuffer buffer = null;
	private String description = null;
	private final byte[] reservedBytes;
	private final byte[] datahashBytes;
	private final byte[] peerIdBytes;
	private final byte version;
	private static byte[] duplicate(byte[] b) {
		byte[] r = new byte[b.length];
		System.arraycopy(b, 0, r, 0, b.length);
		return r;
	}
	
	/**
	 * Used for outgoing handshake message.
	 * @param dataHash
	 * @param peerId
	 * @param reservedMode
	 * @param version
	 */
	public BTHandshake(byte[] dataHash, byte[] peerId, int reservedMode, byte version) {
		this(duplicate(RESERVED[reservedMode]), dataHash, peerId, version);
	}
	
	private BTHandshake(byte[] reserved, byte[] dataHash, byte[] peerId, byte version) {
		this.reservedBytes = reserved;
		this.datahashBytes = dataHash;
		this.peerIdBytes = peerId;
		this.version = version;
	}
	
	private void constructBuffer() {
		
		/*Log.d(TAG, "constructBuffer() is called...");
		Throwable t = new Throwable();
		t.printStackTrace();*/
		
		buffer = DirectByteBufferPool.getBuffer(DirectByteBuffer.AL_MSG_BT_HAND, 68);
		buffer.put(DirectByteBuffer.SS_MSG, (byte)PROTOCOL.length());
		buffer.put(DirectByteBuffer.SS_MSG, PROTOCOL.getBytes());
		buffer.put(DirectByteBuffer.SS_MSG, reservedBytes);
		buffer.put(DirectByteBuffer.SS_MSG, datahashBytes);
		buffer.put(DirectByteBuffer.SS_MSG, peerIdBytes);
		buffer.flip(DirectByteBuffer.SS_MSG);
	}
	
	public byte[] getReserved() {	return reservedBytes;	}
	public byte[] getDataHash() {	return datahashBytes;	}
	public byte[] getPeerId() {	return peerIdBytes;	}

	// message
	public String getID() {	return BTMessage.ID_BT_HANDSHAKE;	}
	public byte[] getIDBytes() {	return BTMessage.ID_BT_HANDSHAKE_BYTES;	}
	public String getFeatureID() {	return BTMessage.BT_FEATURE_ID;	}
	public int getFeatureSubID() {	return BTMessage.SUBID_BT_HANDSHAKE;	}
	public int getType() {	return Message.TYPE_PROTOCOL_PAYLOAD;	}
	public byte getVersion() { return version; };
	
	public String getDescription() {
		if (description == null) {
			description = BTMessage.ID_BT_HANDSHAKE + " of dataID: " +ByteFormatter.nicePrint(datahashBytes, true ) + " peerID: " +PeerClassifier.getPrintablePeerID( peerIdBytes);
		}
		return description;
	}
	
	public DirectByteBuffer[] getData() {
		if (buffer == null) {
			constructBuffer();
		}
		return new DirectByteBuffer[]{ buffer };
	}
	
	public Message deserialize(DirectByteBuffer data, byte version) throws MessageException {
		
		/*Log.d(TAG, "deserialize() is called...");
		Throwable t = new Throwable();
		t.printStackTrace();*/
		
		if (data == null) {
			throw new MessageException("[" +getID() + "] decode error: data == null");
		}
		if (data.remaining(DirectByteBuffer.SS_MSG) != 68) {
			throw new MessageException("[" +getID() + "] decode error: payload.remaining[" +data.remaining( DirectByteBuffer.SS_MSG )+ "] != 68");
		}
		if (data.get(DirectByteBuffer.SS_MSG ) != (byte)PROTOCOL.length()) {
			throw new MessageException("[" +getID() + "] decode error: payload.get() != (byte)PROTOCOL.length()");
		}
		byte[] header = new byte[PROTOCOL.getBytes().length];
		data.get(DirectByteBuffer.SS_MSG, header);
		if (!PROTOCOL.equals(new String(header))) {
			throw new MessageException("[" +getID() + "] decode error: invalid protocol given: " + new String( header ));
		}
		byte[] reserved = new byte[8];
		data.get(DirectByteBuffer.SS_MSG, reserved);
		byte[] infohash = new byte[20];
		data.get(DirectByteBuffer.SS_MSG, infohash);
		byte[] peerid = new byte[20];
		data.get(DirectByteBuffer.SS_MSG, peerid);
		data.returnToPool();
		
		if (peerid[0] == (byte)0 && peerid[1] == (byte)0) {
			boolean ok = false;
			for (int i=2;i<20;i++) {
				if (peerid[i] != (byte)0) {
					ok = true;
					break;
				}
			}
			if (!ok) {
				byte[] x = ("-" + "#@" + "0000" + "-").getBytes();	// bad peer id decode
				RandomUtils.nextBytes(peerid);
				System.arraycopy(x, 0, peerid, 0, x.length);
			}
		}
		return new BTHandshake(reserved, infohash, peerid, version);
	}
	
	// raw message
	public DirectByteBuffer[] getRawData() {
		if (buffer == null) {
			constructBuffer();
		}
		return new DirectByteBuffer[]{ buffer };
	}
	
	public int getPriority() {	return RawMessage.PRIORITY_HIGH;	}
	public boolean isNoDelay() {	return true;	}
	public void setNoDelay() {}
	public Message[] messagesToRemove() {	return null;	}
	
	public void destroy() {
		if (buffer != null)	buffer.returnToPool();
	}
	
	public Message getBaseMessage() {	return this;	}
}
