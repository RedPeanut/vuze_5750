/*
 * File    : PRUDPPacket.java
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

import org.gudy.azureus2.core3.util.AEMonitor;
import org.gudy.azureus2.core3.util.RandomUtils;

public abstract class PRUDPPacket {
	
	public static final int	MAX_PACKET_SIZE			= 8192;
	public static final int DEFAULT_UDP_TIMEOUT		= 30000;

	private static int			nextId 		= RandomUtils.nextInt();
	private static AEMonitor	classMon	= new AEMonitor("PRUDPPacket");

	private	InetSocketAddress	address;

	private int		type;
	private int		transactionId;

	private PRUDPPacket	previousPacket;

	private int			serialisedSize;

	protected PRUDPPacket(
		int		_type,
		int		_transaction_id) {
		type			= _type;
		transactionId	= _transaction_id;
	}

	protected PRUDPPacket(int _type) {
		type = _type;
		try {
			classMon.enter();
			transactionId	= nextId++;
		} finally {
			classMon.exit();
		}
	}

	public void setSerialisedSize(int len) {
		serialisedSize = len;
	}

	public int getSerialisedSize() {
		return (serialisedSize);
	}

	public boolean hasContinuation() {
		return (false);
	}

	public void setPreviousPacket(
		PRUDPPacket	p) {
		previousPacket = p;
	}

	public PRUDPPacket getPreviousPacket() {
		return (previousPacket);
	}

	public void setAddress(InetSocketAddress _address) {
		address	= _address;
	}

	public InetSocketAddress getAddress() {
		return (address);
	}

	public int getAction() {
		return (type);
	}

	public int getTransactionId() {
		return (transactionId);
	}

	//public abstract String deserialise();
	public abstract void serialise(DataOutputStream	os) throws IOException;

	public String getString() {
		return ("type=" + type + ",addr=" + address);
	}
}
