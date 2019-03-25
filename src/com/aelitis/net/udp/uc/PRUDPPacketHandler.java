/*
 * File    : PRUDPPacketReceiver.java
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

import java.net.*;

public interface PRUDPPacketHandler {
	
	public static final int	PRIORITY_LOW		= 2;
	public static final int	PRIORITY_MEDIUM		= 1;
	public static final int	PRIORITY_HIGH		= 0;

	public static final int	PRIORITY_IMMEDIATE	= 99;

	/**
	 * Asynchronous send and receive
	 * @param requestPacket
	 * @param destinationAddress
	 * @param receiver
	 * @throws PRUDPPacketHandlerException
	 */
	public void sendAndReceive(
		PRUDPPacket					requestPacket,
		InetSocketAddress			destinationAddress,
		PRUDPPacketReceiver			receiver,
		long						timeout,
		int							priority)
		throws PRUDPPacketHandlerException;

	/**
	 * Synchronous send and receive
	 * @param auth
	 * @param requestPacket
	 * @param destinationAddress
	 * @return
	 * @throws PRUDPPacketHandlerException
	 */
	public PRUDPPacket sendAndReceive(
		PasswordAuthentication		auth,
		PRUDPPacket					requestPacket,
		InetSocketAddress			destinationAddress )
		throws PRUDPPacketHandlerException;

	public PRUDPPacket sendAndReceive(
		PasswordAuthentication		auth,
		PRUDPPacket					requestPacket,
		InetSocketAddress			destinationAddress,
		long						timeoutMillis )
		throws PRUDPPacketHandlerException;

	public PRUDPPacket sendAndReceive(
		PasswordAuthentication		auth,
		PRUDPPacket					requestPacket,
		InetSocketAddress			destinationAddress,
		long						timeoutMillis,
		int							priority )
		throws PRUDPPacketHandlerException;

	/**
	 * Send only
	 * @param requestPacket
	 * @param destinationAddress
	 * @throws PRUDPPacketHandlerException
	 */
	public void send(
		PRUDPPacket					requestPacket,
		InetSocketAddress			destinationAddress)
		throws PRUDPPacketHandlerException;

	public PRUDPRequestHandler getRequestHandler();

	public void setRequestHandler(PRUDPRequestHandler	request_handler);

	public void primordialSend(
		byte[]				data,
		InetSocketAddress	target )
		throws PRUDPPacketHandlerException;

	public boolean hasPrimordialHandler();

	public void addPrimordialHandler(PRUDPPrimordialHandler	handler);

	public void removePrimordialHandler(PRUDPPrimordialHandler	handler);

	public int getPort();

	public InetAddress getBindIP();

	public void setDelays(
		int		send_delay,
		int		receive_delay,
		int		queued_request_timeout);

	public void setExplicitBindAddress(InetAddress	address);

	public PRUDPPacketHandlerStats getStats();

	public PRUDPPacketHandler openSession(InetSocketAddress	target)
		throws PRUDPPacketHandlerException;

	public void closeSession()
		throws PRUDPPacketHandlerException;

	public void destroy();
}
