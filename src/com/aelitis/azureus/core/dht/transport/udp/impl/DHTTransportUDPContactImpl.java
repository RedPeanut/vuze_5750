/*
 * Created on 21-Jan-2005
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

package com.aelitis.azureus.core.dht.transport.udp.impl;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

import org.gudy.azureus2.core3.util.AERunStateHandler;
import org.gudy.azureus2.core3.util.AESemaphore;

import com.aelitis.azureus.core.dht.DHT;
import com.aelitis.azureus.core.dht.impl.DHTLog;
import com.aelitis.azureus.core.dht.netcoords.DHTNetworkPosition;
import com.aelitis.azureus.core.dht.netcoords.DHTNetworkPositionManager;
import com.aelitis.azureus.core.dht.netcoords.vivaldi.ver1.VivaldiPositionProvider;
import com.aelitis.azureus.core.dht.transport.DHTTransport;
import com.aelitis.azureus.core.dht.transport.DHTTransportContact;
import com.aelitis.azureus.core.dht.transport.DHTTransportException;
import com.aelitis.azureus.core.dht.transport.DHTTransportFullStats;
import com.aelitis.azureus.core.dht.transport.DHTTransportReplyHandler;
import com.aelitis.azureus.core.dht.transport.DHTTransportReplyHandlerAdapter;
import com.aelitis.azureus.core.dht.transport.DHTTransportValue;
import com.aelitis.azureus.core.dht.transport.udp.DHTTransportUDP;
import com.aelitis.azureus.core.dht.transport.udp.DHTTransportUDPContact;
import com.aelitis.net.udp.uc.PRUDPPacketHandler;

import hello.util.Log;
import hello.util.Util;


/**
 * @author parg
 *
 */

public class DHTTransportUDPContactImpl implements DHTTransportUDPContact {
	
	private static String TAG = DHTTransportUDPContactImpl.class.getSimpleName();
	
	public static final int NODE_STATUS_UNKNOWN		= 0xffffffff;
	public static final int NODE_STATUS_ROUTABLE	= 0x00000001;

	static {
		AERunStateHandler.addListener(
			new AERunStateHandler.RunStateChangeListener() {
				private VivaldiPositionProvider provider = null;

				public void runStateChanged(
					long run_state) {
					synchronized(this) {
						if (AERunStateHandler.isDHTSleeping()) {
							if (provider != null) {
								DHTNetworkPositionManager.unregisterProvider(provider);
								provider = null;
							}
						} else {
							if (provider == null) {
								provider = new VivaldiPositionProvider();
								DHTNetworkPositionManager.registerProvider(provider);
							}
						}
					}
				}
			},
			true);
	}

	final DHTTransportUDPImpl		transport;
	private InetSocketAddress		externalAddress;
	private InetSocketAddress		transportAddress;

	private byte[]				id;
	private byte				protocolVersion;
	private int					instanceId;
	private final long			skew;
	private byte				genericFlags;

	private int					randomId;
	private int					nodeStatus	= NODE_STATUS_UNKNOWN;

	private DHTNetworkPosition[]		network_positions;

	protected DHTTransportUDPContactImpl(
		boolean					_isLocal,
		DHTTransportUDPImpl		_transport,
		InetSocketAddress		_transportAddress,
		InetSocketAddress		_externalAddress,
		byte					_protocolVersion,
		int						_instanceId,
		long					_skew,
		byte					_genericFlags)
		throws DHTTransportException {
		
		transport			= _transport;
		transportAddress	= _transportAddress;
		externalAddress		= _externalAddress;
		protocolVersion		= _protocolVersion;
		if (transportAddress.equals(externalAddress)) {
			externalAddress	= transportAddress;
		}
		instanceId		=	_instanceId;
		skew			= 	_skew;
		genericFlags	= 	_genericFlags;
		
		if (	transportAddress == externalAddress ||
				transportAddress.getAddress().equals(externalAddress.getAddress())
		) {
			id = DHTUDPUtils.getNodeID(externalAddress, protocolVersion);
		}
		createNetworkPositions(_isLocal);
	}

	public DHTTransport getTransport() {
		return (transport);
	}

	public byte getProtocolVersion() {
		return (protocolVersion);
	}

	protected void setProtocolVersion(
		byte		v) {
		protocolVersion 	= v;
	}

	public long getClockSkew() {
		return (skew);
	}

	public int getRandomIDType() {
		return (RANDOM_ID_TYPE1);
	}

	public void setRandomID(int		_random_id) {
		randomId	= _random_id;
	}

	public int getRandomID() {
		return (randomId);
	}

	public void setRandomID2(byte[]		id) {
	}

	public byte[] getRandomID2() {
		return (null);
	}

	protected int getNodeStatus() {
		return (nodeStatus);
	}

	protected void setNodeStatus(
		int		ns) {
		nodeStatus	= ns;
	}

	public boolean isValid() {
		return ( 	addressMatchesID() &&
					!transport.invalidExternalAddress( externalAddress.getAddress()));
	}

	public boolean isSleeping() {
		return ((genericFlags & DHTTransportUDP.GF_DHT_SLEEPING ) != 0);
	}

	protected void setGenericFlags(byte		flags) {
		genericFlags = flags;
	}

	protected boolean addressMatchesID() {
		return (id != null);
	}

	public InetSocketAddress getTransportAddress() {
		return (transportAddress);
	}

	public void setTransportAddress(InetSocketAddress address) {
		transportAddress = address;
	}

	public InetSocketAddress getExternalAddress() {
		return (externalAddress);
	}

	public String getName() {
		return (DHTLog.getString2(id));
	}

	public byte[] getBloomKey() {
		return (getAddress().getAddress().getAddress());
	}

	public InetSocketAddress getAddress() {
		return ( getExternalAddress());
	}

	public int getMaxFailForLiveCount() {
		return (transport.getMaxFailForLiveCount());
	}

	public int getMaxFailForUnknownCount() {
		return (transport.getMaxFailForUnknownCount());
	}

	public int getInstanceID() {
		return (instanceId);
	}

	protected void setInstanceIDAndVersion(
		int		_instance_id,
		byte	_protocol_version) {
		instanceId	= _instance_id;
			// target supports a higher version than we thought, update
		if (_protocol_version > protocolVersion) {
			protocolVersion = _protocol_version;
		}
	}

	public boolean isAlive(long timeout) {
		final AESemaphore sem = new AESemaphore("DHTTransportContact:alive");
		final boolean[]	alive = { false };
		try {
			sendPing(
				new DHTTransportReplyHandlerAdapter() {
					public void pingReply(
						DHTTransportContact contact) {
						alive[0]	= true;
						sem.release();
					}
					
					public void failed(
						DHTTransportContact 	contact,
						Throwable 				cause) {
						sem.release();
					}
				});
			sem.reserve(timeout);
			return (alive[0]);
		} catch (Throwable e) {
			return (false);
		}
	}

	public void	isAlive(DHTTransportReplyHandler handler, long timeout) {
		transport.sendPing(this, handler, timeout, PRUDPPacketHandler.PRIORITY_IMMEDIATE);
	}

	public void sendPing(DHTTransportReplyHandler handler) {
		/*Log.d(TAG, "sendPing() is called...");
		Throwable t = new Throwable();
		t.printStackTrace();*/
		transport.sendPing(this, handler);
	}

	public void	sendImmediatePing(
		DHTTransportReplyHandler	handler,
		long						timeout) {
		transport.sendImmediatePing(this, handler, timeout);
	}

	public void	sendStats(DHTTransportReplyHandler	handler) {
		transport.sendStats(this, handler);
	}

	public void	sendStore(DHTTransportReplyHandler	handler,
		byte[][]					keys,
		DHTTransportValue[][]		value_sets,
		boolean						immediate) {
		transport.sendStore(
				this, handler, keys, value_sets,
				immediate?PRUDPPacketHandler.PRIORITY_IMMEDIATE:PRUDPPacketHandler.PRIORITY_LOW);
	}

	public void	sendQueryStore(
		DHTTransportReplyHandler 	handler,
		int							header_length,
		List<Object[]>			 	key_details ) {
		transport.sendQueryStore( this, handler, header_length, key_details);
	}

	public void	sendFindNode(
			DHTTransportReplyHandler handler,
			byte[] nid,
			short flags) {
		//Log.d(TAG, "sendFindNode() is called...");
		//Log.d(TAG, "nid = " + Util.toHexString(nid));
		//Throwable t = new Throwable();
		//t.printStackTrace();
		transport.sendFindNode(this, handler, nid);
	}

	public void	sendFindValue(
		DHTTransportReplyHandler	handler,
		byte[]						key,
		int							max_values,
		short						flags) {
		transport.sendFindValue(this, handler, key, max_values, flags);
	}

	public void	sendKeyBlock(
		final DHTTransportReplyHandler	handler,
		final byte[]					request,
		final byte[]					signature) {
		
		// gotta do anti-spoof
		sendFindNode(
			new DHTTransportReplyHandlerAdapter() {
				public void findNodeReply(
					DHTTransportContact 	contact,
					DHTTransportContact[]	contacts) {
					transport.sendKeyBlockRequest(DHTTransportUDPContactImpl.this, handler, request, signature);
				}
				
				public void	failed(
					DHTTransportContact 	_contact,
					Throwable				_error) {
					handler.failed(_contact, _error);
				}
			},
			new byte[0],
			DHT.FLAG_NONE);

	}

	public DHTTransportFullStats getStats() {
		return (transport.getFullStats( this));
	}

	public byte[] getID() {
		if (id == null) {
			throw (new RuntimeException("Invalid contact"));
		}
		return (id);
	}

	public void exportContact(DataOutputStream os)
		throws IOException, DHTTransportException {
		transport.exportContact(this, os);
	}

	public Map<String, Object> exportContactToMap() {
		return (transport.exportContactToMap( this));
	}

	public void remove() {
		transport.removeContact(this);
	}

	protected void setNetworkPositions(DHTNetworkPosition[] positions) {
  		network_positions	= positions;
  	}

	public void	createNetworkPositions(boolean is_local) {
		network_positions	= DHTNetworkPositionManager.createPositions(id==null?DHTUDPUtils.getBogusNodeID():id, is_local);
	}

	public DHTNetworkPosition[] getNetworkPositions() {
		return (network_positions);
	}

  	public DHTNetworkPosition getNetworkPosition(byte position_type) {
  		for (int i=0;i<network_positions.length;i++) {
  			if (network_positions[i].getPositionType() == position_type) {
  				return (network_positions[i]);
  			}
  		}
  		return (null);
  	}

	public String getString() {
		if (transportAddress.equals( externalAddress)) {
			return ( DHTLog.getString2(id) + "["+transportAddress.toString()+",V" + getProtocolVersion() +"]");
		}
		return ( DHTLog.getString2(id) + "[tran="+transportAddress.toString()+",ext="+externalAddress+",V" + getProtocolVersion() +"]");
	}
}
