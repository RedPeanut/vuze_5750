/*
 * File    : PRUDPPacketReply.java
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

import java.io.*;
import java.net.InetSocketAddress;
import java.util.*;

import org.gudy.azureus2.core3.util.AEMonitor;
import org.gudy.azureus2.core3.util.Debug;

public abstract class PRUDPPacketReply extends PRUDPPacket {
	
	public static final int	PR_HEADER_SIZE	= 8;
	private static AEMonitor classMonitor = new AEMonitor("PRUDPPacketReply:class");
	private static Map<Integer, PRUDPPacketReplyDecoder> packetDecoders	= new HashMap<>();

	public static void registerDecoders(Map<Integer, PRUDPPacketReplyDecoder> _decoders) {
		try {
			classMonitor.enter();
			Map<Integer, PRUDPPacketReplyDecoder> newDecoders = new HashMap<>(packetDecoders);
			Iterator<Integer> it = _decoders.keySet().iterator();
			while (it.hasNext()) {
				Integer action = (Integer)it.next();
				if (packetDecoders.containsKey(action)) {
					Debug.out("Duplicate codec! " + action);
				}
			}
			newDecoders.putAll(_decoders);
			packetDecoders = newDecoders;
		} finally {
			classMonitor.exit();
		}
	}

	public PRUDPPacketReply(
		int		_action,
		int		_tran_id) {
		super(_action, _tran_id);
	}

	public void serialise(DataOutputStream os)
		throws IOException
	{
		// add to this and you need to adjust HEADER_SIZE above
		os.writeInt(getAction());
		os.writeInt(getTransactionId());
	}

	public static PRUDPPacketReply deserialiseReply(
		PRUDPPacketHandler	handler,
		InetSocketAddress	originator,
		DataInputStream		is)
		throws IOException
	{
		int action = is.readInt();
		PRUDPPacketReplyDecoder	decoder = (PRUDPPacketReplyDecoder)packetDecoders.get(new Integer(action));
		if (decoder == null) {
			throw (new IOException("No decoder registered for action '" + action + "'"));
		}
		int transactionId = is.readInt();
		return (decoder.decode(handler, originator, is, action, transactionId));
	}

	public String getString() {
		return (super.getString() + ":reply[trans=" + getTransactionId() + "]");
	}
}