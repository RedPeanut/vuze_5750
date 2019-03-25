/*
 * Created on Jan 24, 2005
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
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import org.gudy.azureus2.core3.util.*;
import com.aelitis.azureus.core.networkmanager.Transport;
import com.aelitis.azureus.core.peermanager.messaging.*;
/**
 *
 */
public class BTMessageDecoder implements MessageStreamDecoder {
	
	private static final int MIN_MESSAGE_LENGTH = 1;	//for type id
	//private static final int MAX_MESSAGE_LENGTH = 16*1024+128;	//should never be > 16KB+9B, as we never request chunks > 16KB - update, some LT extensions can be bigger
	private static final int MAX_MESSAGE_LENGTH = 128*1024;	// 17/5/2013: parg: got a huge torrent with so many pieces the bitfield exceeds the above limit...
	private static final int HANDSHAKE_FAKE_LENGTH = 323119476;	//(byte)19 + "Bit" readInt() value of header
	private static final byte SS = DirectByteBuffer.SS_MSG;
	private DirectByteBuffer payloadBuffer = null;
	private final DirectByteBuffer lengthBuffer = DirectByteBufferPool.getBuffer(DirectByteBuffer.AL_MSG, 4);
	private final ByteBuffer[] decodeArray = new ByteBuffer[] { null, lengthBuffer.getBuffer(SS) };
	private boolean readingLengthMode = true;
	private boolean readingHandshakeMessage = false;
	private int messageLength;
	private int pre_read_start_buffer;
	private int preReadStartPosition;
	private boolean last_received_was_keepalive = false;
	private volatile boolean destroyed = false;
	private volatile boolean isPaused = false;
	private final ArrayList messagesLastRead = new ArrayList();
	private int protocol_bytes_last_read = 0;
	private int data_bytes_last_read = 0;
	private int percentComplete = -1;
	
	public BTMessageDecoder() {
		/* nothing */
	}
	
	public int performStreamDecode(Transport transport, int maxBytes) throws IOException {
		try {
			protocol_bytes_last_read = 0;
			data_bytes_last_read = 0;
			int bytesRemaining = maxBytes;
			while (bytesRemaining > 0) {
				if (destroyed) {
					// destruction currently isn't thread safe so one thread can destroy the decoder (e.g. when closing a connection)
					// while the read-controller is still actively processing the us
					//throw (new IOException("BTMessageDecoder already destroyed"));
					break;
				}
				if (isPaused) {
					break;
				}
				
				int bytesPossible = preReadProcess(bytesRemaining);
				if (bytesPossible < 1) {
					Debug.out("ERROR BT: bytes_possible < 1");
					break;
				}
				if (readingLengthMode) {
					transport.read(decodeArray, 1, 1);	//only read into length buffer
				} else {
					transport.read(decodeArray, 0, 2);	//read into payload buffer, and possibly next message length
				}
				
				int bytesRead = postReadProcess();
				bytesRemaining -= bytesRead;
				if (bytesRead < bytesPossible) {
					break;
				}
				if (readingLengthMode && last_received_was_keepalive) {
					//hack to stop a 0-byte-read after receiving a keep-alive message
					//otherwise we won't realize there's nothing left on the line until trying to read again
					last_received_was_keepalive = false;
					break;
				}
			}
			return maxBytes - bytesRemaining;
		} catch (NullPointerException e) {
				// due to lack of synchronization here the buffers can be nullified by a concurrent 'destroy'
				// turn this into something less scarey
			throw (new IOException("Decoder has most likely been destroyed"));
		}
	}
	public int getPercentDoneOfCurrentMessage() {
		return percentComplete;
	}
	public Message[] removeDecodedMessages() {
		if (messagesLastRead.isEmpty())	return null;
		Message[] msgs = (Message[])messagesLastRead.toArray(new Message[messagesLastRead.size()]);
		messagesLastRead.clear();
		return msgs;
	}
	public int getProtocolBytesDecoded() {	return protocol_bytes_last_read;	}
	public int getDataBytesDecoded() {	return data_bytes_last_read;	}
	public ByteBuffer destroy() {
	if (destroyed) {
		Debug.out("Trying to redestroy message decoder, stack trace follows: " + this);
		Debug.outStackTrace();
	}
		isPaused = true;
		destroyed = true;
			// there's a concurrency issue with the decoder whereby it can be destroyed while will being messed with. Don't
			// have the energy to look into it properly atm so just try to ensure that it doesn't bork too badly (parg: 29/04/2012)
			// only occasional but does have potential to generate direct buffer mem leak ;(
		int lbuff_read = 0;
		int pbuff_read = 0;
		lengthBuffer.limit(SS, 4);
		DirectByteBuffer plb = payloadBuffer;
		if (readingLengthMode) {
			lbuff_read = lengthBuffer.position(SS);
		}
		else { //reading payload
			lengthBuffer.position(SS, 4);
			lbuff_read = 4;
			pbuff_read = plb == null ? 0 : plb.position(SS);
		}
		ByteBuffer unused = ByteBuffer.allocate(lbuff_read + pbuff_read);	 //TODO convert to direct?
		lengthBuffer.flip(SS);
		unused.put(lengthBuffer.getBuffer( SS ));
		try {
			if (plb != null) {
				plb.flip(SS);
				unused.put(plb.getBuffer( SS )); // Got a buffer overflow exception here in the past - related to PEX?
			}
		} catch (RuntimeException e) {
			Debug.out("hit known threading issue");
		}
		unused.flip();
		lengthBuffer.returnToPool();
		if (plb != null) {
			plb.returnToPool();
			payloadBuffer = null;
		}
		try {
			for (int i=0; i < messagesLastRead.size(); i++) {
				Message msg = (Message)messagesLastRead.get(i);
				msg.destroy();
			}
		} catch (RuntimeException e) {
			// happens if messages modified by alt thread...
			Debug.out("hit known threading issue");
		}
		messagesLastRead.clear();
		return unused;
	}
	private int preReadProcess(int allowed) {
		if (allowed < 1) {
			Debug.out("allowed < 1");
		}
		decodeArray[0] = payloadBuffer == null ? null : payloadBuffer.getBuffer(SS);	//ensure the decode array has the latest payload pointer
		int bytesAvailable = 0;
		boolean shrinkRemainingBuffers = false;
		int start_buff = readingLengthMode ? 1 : 0;
		boolean marked = false;
		for (int i = start_buff; i < 2; i++) {	//set buffer limits according to bytes allowed
			ByteBuffer bb = decodeArray[i];
			if (bb == null) {
				Debug.out("preReadProcess:: bb["+i+"] == null, decoder destroyed=" +destroyed);
				throw (new RuntimeException("decoder destroyed"));
			}
			if (shrinkRemainingBuffers) {
				bb.limit(0);	//ensure no read into this next buffer is possible
			} else {
				int remaining = bb.remaining();
				if (remaining < 1)	continue;	//skip full buffer
				if (!marked) {
					pre_read_start_buffer = i;
					preReadStartPosition = bb.position();
					marked = true;
				}
				if (remaining > allowed) {	//read only part of this buffer
					bb.limit(bb.position() + allowed);	//limit current buffer
					bytesAvailable += bb.remaining();
					shrinkRemainingBuffers = true;	//shrink any tail buffers
				}
				else {	//full buffer is allowed to be read
					bytesAvailable += remaining;
					allowed -= remaining;	//count this buffer toward allowed and move on to the next
				}
			}
		}
		return bytesAvailable;
	}

	private int postReadProcess() throws IOException {
		int protBytesRead = 0;
		int dataBytesRead = 0;
		if (!readingLengthMode && !destroyed) {	//reading payload data mode
			//ensure-restore proper buffer limits
			payloadBuffer.limit(SS, messageLength);
			lengthBuffer.limit(SS, 4);
			int read = payloadBuffer.position(SS) - preReadStartPosition;
			if (payloadBuffer.position( SS ) > 0) {	//need to have read the message id first byte
				if (BTMessageFactory.getMessageType( payloadBuffer) == Message.TYPE_DATA_PAYLOAD) {
					dataBytesRead += read;
				} else {
					protBytesRead += read;
				}
			}
			
			if (!payloadBuffer.hasRemaining(SS) && !isPaused) {	//full message received!
				payloadBuffer.position(SS, 0);
				DirectByteBuffer refBuffer = payloadBuffer;
				payloadBuffer = null;
				if (readingHandshakeMessage) {	//decode handshake
					readingHandshakeMessage = false;
					DirectByteBuffer handshakeData = DirectByteBufferPool.getBuffer(DirectByteBuffer.AL_MSG_BT_HAND, 68);
					handshakeData.putInt(SS, HANDSHAKE_FAKE_LENGTH);
					handshakeData.put(SS, refBuffer);
					handshakeData.flip(SS);
					refBuffer.returnToPool();
					try {
						Message handshake = MessageManager.getSingleton().createMessage(BTMessage.ID_BT_HANDSHAKE_BYTES, handshakeData, (byte)1);
						messagesLastRead.add(handshake);
					} catch (MessageException me) {
						handshakeData.returnToPool();
						throw new IOException("BT message decode failed: " + me.getMessage());
					}
					
					//we need to auto-pause decoding until we're told to start again externally,
					//as we don't want to accidentally read the next message on the stream if it's an AZ-format handshake
					pauseDecoding();
				} else {	//decode normal message
					try {
						messagesLastRead.add(createMessage(refBuffer));
					} catch (Throwable e) {
						refBuffer.returnToPoolIfNotFree();
						// maintain unexpected errors as such so they get logged later
						if (e instanceof RuntimeException) {
							throw ((RuntimeException)e);
						}
						throw new IOException("BT message decode failed: " +e.getMessage());
					}
				}
				readingLengthMode = true;	//see if we've already read the next message's length
				percentComplete = -1;	//reset receive percentage
			}
			else {	//only partial received so far
				percentComplete = (payloadBuffer.position(SS) * 100) / messageLength;	//compute receive percentage
			}
		}
		if (readingLengthMode && !destroyed) {
			lengthBuffer.limit(SS, 4);	//ensure proper buffer limit
			protBytesRead += (pre_read_start_buffer == 1) ? lengthBuffer.position(SS ) - preReadStartPosition : lengthBuffer.position( SS);
			if (!lengthBuffer.hasRemaining( SS )) {	//done reading the length
				readingLengthMode = false;
				lengthBuffer.position(SS, 0);
				messageLength = lengthBuffer.getInt(SS);
				lengthBuffer.position(SS, 0);	//reset it for next length read
				if (messageLength == HANDSHAKE_FAKE_LENGTH) {	//handshake message
					readingHandshakeMessage = true;
					messageLength = 64;	//restore 'real' length
					payloadBuffer = DirectByteBufferPool.getBuffer(DirectByteBuffer.AL_MSG_BT_HAND, messageLength);
				}
				else if (messageLength == 0) {	//keep-alive message
					readingLengthMode = true;
					last_received_was_keepalive = true;
					try {
						Message keep_alive = MessageManager.getSingleton().createMessage(BTMessage.ID_BT_KEEP_ALIVE_BYTES, null, (byte)1);
						messagesLastRead.add(keep_alive);
					}
					catch (MessageException me) {
						throw new IOException("BT message decode failed: " + me.getMessage());
					}
				}
				else if (messageLength < MIN_MESSAGE_LENGTH || messageLength > MAX_MESSAGE_LENGTH) {
					throw new IOException("Invalid message length given for BT message decode: " + messageLength);
				}
				else {	//normal message
					payloadBuffer = DirectByteBufferPool.getBuffer(DirectByteBuffer.AL_MSG_BT_PAYLOAD, messageLength);
				}
			}
		}
		protocol_bytes_last_read += protBytesRead;
		data_bytes_last_read += dataBytesRead;
		return protBytesRead + dataBytesRead;
	}
	public void pauseDecoding() {
		isPaused = true;
	}
	public void resumeDecoding() {
		isPaused = false;
	}
	// Overridden by LTMessageDecoder.
	protected Message createMessage(DirectByteBuffer ref_buff) throws MessageException {
		try { 
			return BTMessageFactory.createBTMessage(ref_buff);
		} catch (MessageException me) {
			/*if (identifier != null 
				&& me.getMessage() != null 
				&& me.getMessage().startsWith("Unknown BT message id")
			) {
				System.out.println(identifier + " " + me.getMessage());
			}*/
			throw me;
		}
	}
}
