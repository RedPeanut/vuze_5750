/*
 * Created on 12-Jun-2005
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
package com.aelitis.azureus.core.dht.transport.udp.impl.packethandler;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;

import org.gudy.azureus2.core3.util.AddressUtils;
import org.gudy.azureus2.core3.util.Base32;
import org.gudy.azureus2.core3.util.Constants;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.SystemTime;
import org.gudy.azureus2.core3.util.UrlUtils;

import com.aelitis.azureus.core.dht.transport.udp.impl.DHTUDPPacketHelper;
import com.aelitis.azureus.core.dht.transport.udp.impl.DHTUDPPacketReply;
import com.aelitis.azureus.core.dht.transport.udp.impl.DHTUDPPacketRequest;
import com.aelitis.azureus.core.util.DNSUtils;
import com.aelitis.azureus.core.util.bloom.BloomFilter;
import com.aelitis.azureus.core.util.bloom.BloomFilterFactory;
import com.aelitis.net.udp.uc.PRUDPPacket;
import com.aelitis.net.udp.uc.PRUDPPacketHandler;
import com.aelitis.net.udp.uc.PRUDPPacketHandlerException;
import com.aelitis.net.udp.uc.PRUDPPacketHandlerRequest;
import com.aelitis.net.udp.uc.PRUDPPacketReceiver;
import com.aelitis.net.udp.uc.PRUDPPacketReply;

import hello.util.Log;
import hello.util.SingleCounter1;

public class DHTUDPPacketHandler implements DHTUDPPacketHandlerStub {
	
	private static String TAG = DHTUDPPacketHandler.class.getSimpleName();
	
	private final DHTUDPPacketHandlerFactory	factory;
	final int									network;
	private final PRUDPPacketHandler			packetHandler;
	private final DHTUDPRequestHandler			requestHandler;
	private final DHTUDPPacketHandlerStats		stats;
	private boolean								testNetworkAlive	= true;
	private static final int					BLOOM_FILTER_SIZE		= 10000;
	private static final int					BLOOM_ROTATION_PERIOD	= 3*60*1000;
	private BloomFilter							bloom1;
	private BloomFilter							bloom2;
	private long								last_bloom_rotation_time;
	private boolean 							destroyed;
	
	protected DHTUDPPacketHandler(
		DHTUDPPacketHandlerFactory	_factory,
		int							_network,
		PRUDPPacketHandler			_packetHandler,
		DHTUDPRequestHandler		_requestHandler) {
		
		factory			= _factory;
		network			= _network;
		packetHandler	= _packetHandler;
		requestHandler	= _requestHandler;
		bloom1 = BloomFilterFactory.createAddOnly(BLOOM_FILTER_SIZE);
		bloom2 = BloomFilterFactory.createAddOnly(BLOOM_FILTER_SIZE);
		stats = new DHTUDPPacketHandlerStats(packetHandler);
	}
	
	public boolean isDestroyed() {
		return (destroyed);
	}
	
	public void testNetworkAlive(boolean alive) {
		testNetworkAlive = alive;
	}
	
	public DHTUDPRequestHandler getRequestHandler() {
		return (requestHandler);
	}
	
	public PRUDPPacketHandler getPacketHandler() {
		return (packetHandler);
	}
	
	protected int getNetwork() {
		return (network);
	}
	
	protected void updateBloom(InetSocketAddress destination_address) {
		// allow unresolved through (e.g. ipv6 dht seed) as handled later
		if (!destination_address.isUnresolved()) {
		    long diff = SystemTime.getCurrentTime() - last_bloom_rotation_time;
		    if (diff < 0 || diff > BLOOM_ROTATION_PERIOD) {
		    	// System.out.println("bloom rotate: entries = " + bloom1.getEntryCount() + "/" + bloom2.getEntryCount());
		    	bloom1 = bloom2;
		    	bloom2 = BloomFilterFactory.createAddOnly(BLOOM_FILTER_SIZE);
		        last_bloom_rotation_time = SystemTime.getCurrentTime();
		    }
		    byte[]	address_bytes = destination_address.getAddress().getAddress();
		    bloom1.add(address_bytes);
		    bloom2.add(address_bytes);
		}
	}
	
	public void	sendAndReceive(
		DHTUDPPacketRequest					request,
		InetSocketAddress					destinationAddress,
		final DHTUDPPacketReceiver			receiver,
		long								timeout,
		int									priority)
		throws DHTUDPPacketHandlerException {
		
		//Log.d(TAG, "count = " + SingleCounter1.getInstance().getAndIncreaseCount());
		
		if (destroyed) {
			throw (new DHTUDPPacketHandlerException("packet handler is destroyed"));
		}
		
		// send and receive pair
		destinationAddress = AddressUtils.adjustDHTAddress(destinationAddress, true);
		
		try {
			request.setNetwork(network);
			if (testNetworkAlive) {
				if (destinationAddress.isUnresolved() 
						&& destinationAddress.getHostName().equals(Constants.DHT_SEED_ADDRESS_V6)) {
					tunnelIPv6SeedRequest(request, destinationAddress, receiver);
				} else {
					updateBloom(destinationAddress);
					packetHandler.sendAndReceive(
						request,
						destinationAddress,
						new PRUDPPacketReceiver() {
							
							private String TAG = "DHTUDPPacketHandler.PRUDPPacketReceiver";
							
							public void packetReceived(
								PRUDPPacketHandlerRequest	request,
								PRUDPPacket					packet,
								InetSocketAddress			fromAddress) {
								
								//Log.d(TAG, "packetReceived() is called...");
								/*Throwable t = new Throwable();
								t.printStackTrace();*/
								
								DHTUDPPacketReply	reply = (DHTUDPPacketReply)packet;
								stats.packetReceived(reply.getSerialisedSize());
								if (reply.getNetwork() == network) {
									receiver.packetReceived(reply, fromAddress, request.getElapsedTime());
								} else {
									Debug.out("Non-matching network reply received: expected=" + network + ", actual=" + reply.getNetwork());
									receiver.error(new DHTUDPPacketHandlerException(new Exception("Non-matching network reply received")));
								}
							}
							
							public void error(PRUDPPacketHandlerException e) {
								//Log.d(TAG, "error() is called...");
								/*Throwable t = new Throwable();
								t.printStackTrace();*/
								
								receiver.error(new DHTUDPPacketHandlerException( e));
							}
						},
						timeout,
						priority);
				}
			} else {
				receiver.error(new DHTUDPPacketHandlerException(new Exception("Test network disabled")));
			}
		} catch (PRUDPPacketHandlerException e) {
			throw (new DHTUDPPacketHandlerException(e));
		} finally {
			stats.packetSent(request.getSerialisedSize());
		}
	}
	
	public void send(
		DHTUDPPacketRequest			request,
		InetSocketAddress			destination_address )
		throws DHTUDPPacketHandlerException
	{
		if (destroyed) {
			throw (new DHTUDPPacketHandlerException("packet handler is destroyed"));
		}
		destination_address	= AddressUtils.adjustDHTAddress(destination_address, true);
		updateBloom(destination_address);
			// one way send (no matching reply expected )
		try {
			request.setNetwork(network);
			if (testNetworkAlive) {
				packetHandler.send(request, destination_address);
			}
		} catch (PRUDPPacketHandlerException e) {
			throw (new DHTUDPPacketHandlerException( e));
		} finally {
			stats.packetSent(request.getSerialisedSize());
		}
	}
	
	public void send(DHTUDPPacketReply reply,
		InetSocketAddress destinationAddress)
		throws DHTUDPPacketHandlerException {
		
		if (destroyed)
			throw (new DHTUDPPacketHandlerException("packet handler is destroyed"));
		
		destinationAddress	= AddressUtils.adjustDHTAddress(destinationAddress, true);
		
		// send reply to a request
		try {
			reply.setNetwork(network);
			// outgoing request
			if (testNetworkAlive) {
				packetHandler.send(reply, destinationAddress);
			}
		} catch (PRUDPPacketHandlerException e) {
			throw (new DHTUDPPacketHandlerException( e));
		} finally {
			stats.packetSent(reply.getSerialisedSize());
		}
	}
	
	protected void receive(DHTUDPPacketRequest request) {

		if (destroyed)
			return;

		// incoming request
		if (testNetworkAlive) {
			request.setAddress(AddressUtils.adjustDHTAddress(request.getAddress(), false));
			// an alien request is one that originates from a peer that we haven't recently
			// talked to
			byte[] bloomKey = request.getAddress().getAddress().getAddress();
			boolean alien = !bloom1.contains(bloomKey);
			if (alien) {
				// avoid counting consecutive requests from same contact more than once
				bloom1.add(bloomKey);
				bloom2.add(bloomKey);
			}
			stats.packetReceived(request.getSerialisedSize());
			requestHandler.process(request, alien);
		}
	}
	
	public void setDelays(
		int		send_delay,
		int		receive_delay,
		int		queued_request_timeout) {
		// TODO: hmm
		packetHandler.setDelays(send_delay, receive_delay, queued_request_timeout);
	}
	public void destroy() {
		factory.destroy(this);
		destroyed = true;
	}
	public DHTUDPPacketHandlerStats
	getStats() {
		return (stats);
	}

	private void tunnelIPv6SeedRequest(
		DHTUDPPacketRequest			request,
		InetSocketAddress			destination_address,
		DHTUDPPacketReceiver		receiver )
		throws DHTUDPPacketHandlerException
	{
		if (destroyed) {
			throw (new DHTUDPPacketHandlerException("packet handler is destroyed"));
		}
		if (request.getAction() != DHTUDPPacketHelper.ACT_REQUEST_FIND_NODE) {
			return;
		}
		try {
			long start = SystemTime.getMonotonousTime();
			ByteArrayOutputStream baos_req = new ByteArrayOutputStream();
			DataOutputStream dos = new DataOutputStream(baos_req);
			request.serialise(dos);
			dos.close();
			byte[] request_bytes = baos_req.toByteArray();
			String host = Constants.DHT_SEED_ADDRESS_V6_TUNNEL;
			DNSUtils.DNSUtilsIntf dns_utils = DNSUtils.getSingleton();
			if (dns_utils != null) {
				try {
					host = dns_utils.getIPV6ByName(host).getHostAddress();
					host = UrlUtils.convertIPV6Host(host);
				} catch (Throwable e) {
				}
			}
			URL url = new URL("http://" + host + "/dht?port=" + packetHandler.getPort() + "&request=" + Base32.encode( request_bytes));
			HttpURLConnection connection = (HttpURLConnection)url.openConnection();
			connection.setConnectTimeout(10*1000);
			connection.setReadTimeout(20*1000);
			InputStream is = connection.getInputStream();
			ByteArrayOutputStream baos_rep = new ByteArrayOutputStream(1000);
			byte[]	buffer = new byte[8*1024];
			while (true) {
				int len = is.read(buffer);
				if (len <= 0) {
					break;
				}
				baos_rep.write(buffer, 0, len);
			}
			byte[] reply_bytes = baos_rep.toByteArray();
			if (reply_bytes.length > 0) {
				DHTUDPPacketReply reply = (DHTUDPPacketReply)PRUDPPacketReply.deserialiseReply(
						packetHandler, destination_address,
						new DataInputStream(new ByteArrayInputStream(reply_bytes)));
				receiver.packetReceived(reply, destination_address, SystemTime.getMonotonousTime() - start);
			}
		} catch (Throwable e) {
			throw (new DHTUDPPacketHandlerException("Tunnel failed", e));
		}
	}
}
