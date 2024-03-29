/*
 * Created on May 29, 2014
 * Created by Paul Gardner
 * 
 * Copyright 2014 Azureus Software, Inc.  All rights reserved.
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License only.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 */


package lbms.plugins.mldht.azureus;

import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.util.*;

import org.gudy.azureus2.core3.util.BEncoder;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.SystemTime;

import com.aelitis.azureus.core.dht.transport.DHTTransportAlternativeContact;
import com.aelitis.azureus.core.dht.transport.DHTTransportAlternativeNetwork;
import com.aelitis.azureus.core.dht.transport.udp.impl.DHTUDPUtils;

public class AlternativeContactHandler {

	private DHTTransportAlternativeNetworkImpl	ipv4Net = new DHTTransportAlternativeNetworkImpl(DHTTransportAlternativeNetwork.AT_MLDHT_IPV4);
	private DHTTransportAlternativeNetworkImpl	ipv6Net = new DHTTransportAlternativeNetworkImpl(DHTTransportAlternativeNetwork.AT_MLDHT_IPV6);

	protected AlternativeContactHandler() {
		DHTUDPUtils.registerAlternativeNetwork(ipv4Net);
		DHTUDPUtils.registerAlternativeNetwork(ipv6Net);
	}

	protected void nodeAlive(InetSocketAddress address) {
		if (address.getAddress() instanceof Inet4Address) {
			ipv4Net.addAddress(address);
		} else {
			ipv6Net.addAddress(address);
		}
	}

	protected void destroy() {
		DHTUDPUtils.unregisterAlternativeNetwork(ipv4Net);
		DHTUDPUtils.unregisterAlternativeNetwork(ipv6Net);
	}

	private static class DHTTransportAlternativeNetworkImpl implements DHTTransportAlternativeNetwork {
		
		private static final int ADDRESS_HISTORY_MAX	= 32;
		private int	network;
		private LinkedList<Object[]>	address_history = new LinkedList<Object[]>();

		private DHTTransportAlternativeNetworkImpl(int net) {
			network	= net;
		}
		
		public int getNetworkType() {
			return (network);
		}
		
		private void addAddress(InetSocketAddress address) {
			synchronized(address_history) {
				address_history.addFirst(new Object[]{  address, new Long(SystemTime.getMonotonousTime())});
				if (address_history.size() > ADDRESS_HISTORY_MAX) {
					address_history.removeLast();
				}
			}
		}

		public List<DHTTransportAlternativeContact> getContacts(int max) {
			List<DHTTransportAlternativeContact> result = new ArrayList<DHTTransportAlternativeContact>(max);
			synchronized(address_history) {
				for (Object[] entry: address_history) {
					result.add(new DHTTransportAlternativeContactImpl((InetSocketAddress)entry[0],(Long)entry[1]));
					if (result.size() == max) {
						break;
					}
				}
			}
			return(result);
		}
		
		private class DHTTransportAlternativeContactImpl
			implements DHTTransportAlternativeContact {
			
			private final InetSocketAddress		address;
			private final int	 				seenSecs;
			private final int	 				id;

			private DHTTransportAlternativeContactImpl(InetSocketAddress _address, long seen) {
				address	= _address;
				seenSecs = (int)(seen/1000);
				int	_id;
				try {
					_id = Arrays.hashCode(BEncoder.encode(getProperties()));
				} catch (Throwable e) {
					Debug.out(e);
					_id = 0;
				}
				id	= _id;
			}

			public int getNetworkType() {
				return (network);
			}

			public int getVersion() {
				return (1);
			}

			public int getID() {
				return (id);
			}

			public int getLastAlive() {
				return (seenSecs);
			}

			public int getAge() {
				return (((int) (SystemTime.getMonotonousTime() / 1000)) - seenSecs);
			}

			public Map<String, Object> getProperties() {
				Map<String,Object>	properties = new HashMap<String, Object>();
				try {
					properties.put("a", address.getAddress().getAddress());
					properties.put("p", new Long(address.getPort()));
				} catch (Throwable e) {
					Debug.out(e);
				}
				return(properties);
			}
		}
	}
}
