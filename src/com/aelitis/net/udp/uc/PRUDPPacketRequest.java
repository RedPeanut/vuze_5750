/*
 * File    : PRUDPPacketRequest.java
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

package com.aelitis.net.udp.uc;

/**
 * @author parg
 *
 */

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.gudy.azureus2.core3.util.AEMonitor;
import org.gudy.azureus2.core3.util.Debug;

public abstract class PRUDPPacketRequest extends PRUDPPacket {
	
	public static final int	PR_HEADER_SIZE	= 16; // 8+4+4

	private static AEMonitor	classMonitor = new AEMonitor("PRUDPPacketRequest:class");

	private static Map<Integer, PRUDPPacketRequestDecoder> packetDecoders = new HashMap<>();

	private long	connectionId;
	private long	receiveTime;

	public static void registerDecoders(Map<Integer, PRUDPPacketRequestDecoder> _decoders) {
		try {
			classMonitor.enter();
			Map<Integer, PRUDPPacketRequestDecoder> newDecoders = new HashMap<>(packetDecoders);
			Iterator<Integer> it = _decoders.keySet().iterator();
			while (it.hasNext()) {
				Integer action = it.next();
				if (packetDecoders.containsKey(action)) {
					Debug.out("Duplicate codec! " + action);
				}
			}
			newDecoders.putAll(_decoders);
			packetDecoders	= newDecoders;
		} finally {
			classMonitor.exit();
		}
	}

	public PRUDPPacketRequest(
		int		_action,
		long	_con_id) {
		super(_action);
		connectionId	= _con_id;
	}

	protected PRUDPPacketRequest(
		int		_action,
		long	_con_id,
		int		_trans_id) {
		super(_action, _trans_id);
		connectionId	= _con_id;
	}

	public long getConnectionId() {
		return (connectionId);
	}

	public long getReceiveTime() {
		return (receiveTime);
	}

	public void setReceiveTime(long _rt) {
		receiveTime = _rt;
	}

	public void serialise(DataOutputStream os)
		throws IOException
	{
		// add to this and you need to adjust HEADER_SIZE above
		os.writeLong(connectionId);
		os.writeInt(getAction());
		os.writeInt(getTransactionId());
	}

	public void deserialise(byte[] data, int len) {
		
	}
	
	public static PRUDPPacketRequest deserialiseRequest(
		PRUDPPacketHandler	handler,
		DataInputStream		is)
		throws IOException
	{
		long	connectionId 	= is.readLong();
		int		action			= is.readInt();
		int		transactionId	= is.readInt();
		PRUDPPacketRequestDecoder decoder = (PRUDPPacketRequestDecoder)packetDecoders.get(new Integer(action));
		if (decoder == null) {
			throw (new IOException("No decoder registered for action '" + action + "'"));
		}
		return (decoder.decode(handler, is, connectionId, action, transactionId));
	}

	public String getString() {
		return (super.getString() + ":request[con=" + connectionId + ",trans=" + getTransactionId() + "]");
	}
}
