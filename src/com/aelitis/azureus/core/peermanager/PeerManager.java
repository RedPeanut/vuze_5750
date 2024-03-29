/*
 * Created on Jan 20, 2005
 * Created by Alon Rohter
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

package com.aelitis.azureus.core.peermanager;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.*;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.logging.*;
import org.gudy.azureus2.core3.peer.PEPeer;
import org.gudy.azureus2.core3.peer.PEPeerListener;
import org.gudy.azureus2.core3.peer.PEPeerSource;
import org.gudy.azureus2.core3.peer.impl.*;
import org.gudy.azureus2.core3.peer.util.PeerIdentityManager;
import org.gudy.azureus2.core3.torrent.TOTorrentFile;
import org.gudy.azureus2.core3.util.AEGenericCallback;
import org.gudy.azureus2.core3.util.AEMonitor;
import org.gudy.azureus2.core3.util.AENetworkClassifier;
import org.gudy.azureus2.core3.util.AERunStateHandler;
import org.gudy.azureus2.core3.util.AEThread2;
import org.gudy.azureus2.core3.util.AddressUtils;
import org.gudy.azureus2.core3.util.ByteFormatter;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.HashWrapper;
import org.gudy.azureus2.core3.util.SystemTime;

import com.aelitis.azureus.core.networkmanager.*;
import com.aelitis.azureus.core.networkmanager.impl.IncomingConnectionManager;
import com.aelitis.azureus.core.networkmanager.impl.TransportHelper;
import com.aelitis.azureus.core.peermanager.messaging.*;
import com.aelitis.azureus.core.peermanager.messaging.bittorrent.*;
import com.aelitis.azureus.core.peermanager.piecepicker.util.BitFlags;
import com.aelitis.azureus.core.stats.AzureusCoreStats;
import com.aelitis.azureus.core.stats.AzureusCoreStatsProvider;
import com.aelitis.azureus.core.util.bloom.BloomFilter;
import com.aelitis.azureus.core.util.bloom.BloomFilterFactory;

/**
 *
 */
public class PeerManager implements AzureusCoreStatsProvider {
	
	private static final LogIDs LOGID = LogIDs.PEER;

	private static final PeerManager instance = new PeerManager();

	private static final int	PENDING_TIMEOUT	= 10*1000;

	private static final AEMonitor	timer_mon = new AEMonitor("PeerManager:timeouts");
	private static AEThread2	timer_thread;
	static final Set	timer_targets = new HashSet();

	protected static void registerForTimeouts(PeerManagerRegistrationImpl reg) {
		try {
			timer_mon.enter();
			timer_targets.add(reg);
			if (timer_thread == null) {
				timer_thread =
					new AEThread2("PeerManager:timeouts", true) {
					public void run() {
						int	idle_time	= 0;
						while (true) {
							try {
								Thread.sleep(PENDING_TIMEOUT / 2);
							} catch (Throwable e) {
							}
							try {
								timer_mon.enter();
								if (timer_targets.size() == 0) {
									idle_time += PENDING_TIMEOUT / 2;
									if (idle_time >= 30*1000) {
										timer_thread = null;
										break;
									}
								} else {
									idle_time = 0;
									Iterator	it = timer_targets.iterator();
									while (it.hasNext()) {
										PeerManagerRegistrationImpl	registration = (PeerManagerRegistrationImpl)it.next();
										if (!registration.timeoutCheck()) {
											it.remove();
										}
									}
								}
							} finally {
								timer_mon.exit();
							}
						}
					}
				};
				timer_thread.start();
			}
		} finally {
			timer_mon.exit();
		}
	}

	/**
	 * Get the singleton instance of the peer manager.
	 * @return the peer manager
	 */
	public static PeerManager getSingleton() {  return instance;  }

	private final Map<HashWrapper,List<PeerManagerRegistrationImpl>> registered_legacy_managers 	= new HashMap<HashWrapper,List<PeerManagerRegistrationImpl>>();
	private final Map<String,PeerManagerRegistrationImpl>			 registered_links				= new HashMap<String,PeerManagerRegistrationImpl>();

	private final ByteBuffer legacyHandshakeHeader;

	private final AEMonitor	managers_mon = new AEMonitor("PeerManager:managers");

	private PeerManager() {
		
		legacyHandshakeHeader = ByteBuffer.allocate(20);
		legacyHandshakeHeader.put((byte)BTHandshake.PROTOCOL.length());
		legacyHandshakeHeader.put(BTHandshake.PROTOCOL.getBytes());
		legacyHandshakeHeader.flip();

		Set<String>	types = new HashSet<String>();

		types.add(AzureusCoreStats.ST_PEER_MANAGER_COUNT);
		types.add(AzureusCoreStats.ST_PEER_MANAGER_PEER_COUNT);
		types.add(AzureusCoreStats.ST_PEER_MANAGER_PEER_SNUBBED_COUNT);
		types.add(AzureusCoreStats.ST_PEER_MANAGER_PEER_STALLED_DISK_COUNT);

		AzureusCoreStats.registerProvider(types, this);

		init();
	}

	public void updateStats(
		Set		types,
		Map		values) {
		if (types.contains( AzureusCoreStats.ST_PEER_MANAGER_COUNT)) {
			values.put( AzureusCoreStats.ST_PEER_MANAGER_COUNT, new Long( registered_legacy_managers.size()));
		}
		if (	types.contains( AzureusCoreStats.ST_PEER_MANAGER_PEER_COUNT) ||
				types.contains(AzureusCoreStats.ST_PEER_MANAGER_PEER_SNUBBED_COUNT) ||
				types.contains(AzureusCoreStats.ST_PEER_MANAGER_PEER_STALLED_DISK_COUNT)) {
			long	total_peers 				= 0;
			long	total_snubbed_peers			= 0;
			long	total_stalled_pending_load	= 0;
			try {
				managers_mon.enter();
				Iterator<List<PeerManagerRegistrationImpl>>	it = registered_legacy_managers.values().iterator();
				while (it.hasNext()) {
					List<PeerManagerRegistrationImpl>	registrations = it.next();
					Iterator<PeerManagerRegistrationImpl>	it2 = registrations.iterator();
					while (it2.hasNext()) {
						PeerManagerRegistrationImpl reg = it2.next();
						PEPeerControl control = reg.getActiveControl();
						if (control != null) {
							total_peers 				+= control.getNbPeers();
							total_snubbed_peers			+= control.getNbPeersSnubbed();
							total_stalled_pending_load	+= control.getNbPeersStalledPendingLoad();
						}
					}
				}
			} finally {
				managers_mon.exit();
			}
			if (types.contains( AzureusCoreStats.ST_PEER_MANAGER_PEER_COUNT)) {
				values.put(AzureusCoreStats.ST_PEER_MANAGER_PEER_COUNT, new Long( total_peers));
			}
			if (types.contains( AzureusCoreStats.ST_PEER_MANAGER_PEER_SNUBBED_COUNT)) {
				values.put(AzureusCoreStats.ST_PEER_MANAGER_PEER_SNUBBED_COUNT, new Long( total_snubbed_peers));
			}
			if (types.contains( AzureusCoreStats.ST_PEER_MANAGER_PEER_STALLED_DISK_COUNT)) {
				values.put(AzureusCoreStats.ST_PEER_MANAGER_PEER_STALLED_DISK_COUNT, new Long( total_stalled_pending_load));
			}
		}
	}

	protected void init() {
		MessageManager.getSingleton().initialize();  //ensure it gets initialized

		NetworkManager.ByteMatcher matcher =
			new NetworkManager.ByteMatcher() {
				public int matchThisSizeOrBigger() {	return (48);}
				public int maxSize() {  return 48;  }
				public int minSize() { return 20; }
	
				public Object matches(
						TransportHelper		transport,
						ByteBuffer 			toCompare,
						int 				port) {
					InetSocketAddress	address = transport.getAddress();
					int oldLimit = toCompare.limit();
					int oldPosition = toCompare.position();
					toCompare.limit(oldPosition + 20);
					PeerManagerRegistrationImpl	routingData = null;
					if (toCompare.equals(legacyHandshakeHeader)) {  //compare header
						toCompare.limit(oldPosition + 48);
						toCompare.position(oldPosition + 28);
						byte[]	hash = new byte[toCompare.remaining()];
						toCompare.get(hash);
						try {
							managers_mon.enter();
							List<PeerManagerRegistrationImpl>	registrations = registered_legacy_managers.get(new HashWrapper( hash));
							if (registrations != null) {
								routingData = registrations.get(0);
							}
						} finally {
							managers_mon.exit();
						}
					}
					
					//restore buffer structure
					toCompare.limit(oldLimit);
					toCompare.position(oldPosition);
					if (routingData != null) {
						if (!routingData.isActive()) {
							if (routingData.isKnownSeed(address)) {
								String reason = "Activation request from " + address + " denied as known seed";
								if (Logger.isEnabled()) {
									Logger.log(new LogEvent(LOGID, reason));
								}
								transport.close(reason);
								routingData = null;
							} else {
								if (!routingData.getAdapter().activateRequest( address)) {
									String reason = "Activation request from " + address + " denied by rules";
									if (Logger.isEnabled()) {
										Logger.log(new LogEvent(LOGID, reason ));
									}
									transport.close(reason);
									routingData = null;
								}
							}
						}
					}
					return routingData;
				}
	
				public Object minMatches(
						TransportHelper		transport,
						ByteBuffer 			toCompare,
						int 				port) {
					boolean matches = false;
	
					int oldLimit = toCompare.limit();
					int oldPosition = toCompare.position();
	
					toCompare.limit(oldPosition + 20);
	
					if (toCompare.equals(legacyHandshakeHeader)) {
						matches = true;
					}
	
					//restore buffer structure
					toCompare.limit(oldLimit);
					toCompare.position(oldPosition);
	
					return matches?"":null;
				}
	
				public byte[][] getSharedSecrets() {
					return (null);	// registered manually above
				}
	
				public int getSpecificPort() {
					return (-1);
				}
			};

		// register for incoming connection routing
		NetworkManager.getSingleton().requestIncomingConnectionRouting(
			matcher,
			new NetworkManager.RoutingListener() {
				public void connectionRouted(
						NetworkConnection 	connection,
						Object 				routingData) {
					PeerManagerRegistrationImpl	registration = (PeerManagerRegistrationImpl)routingData;
					registration.route(connection, null);
				}

				public boolean autoCryptoFallback() {
					return (false);
				}
			},
			new MessageStreamFactory() {
				public MessageStreamEncoder createEncoder() {  return new BTMessageEncoder();  }
				public MessageStreamDecoder createDecoder() {  return new BTMessageDecoder();  }
			}
		);
	}

	public PeerManagerRegistration manualMatchHash(
			InetSocketAddress	address,
			byte[]				hash) {
		PeerManagerRegistrationImpl	routingData = null;
		try {
			managers_mon.enter();
			List<PeerManagerRegistrationImpl>	registrations = registered_legacy_managers.get(new HashWrapper( hash));
			if (registrations != null) {
				routingData = registrations.get(0);
			}
		} finally {
			managers_mon.exit();
		}
		if (routingData != null) {
			if (!routingData.isActive()) {
				if (routingData.isKnownSeed( address)) {
					if (Logger.isEnabled()) {
						Logger.log(new LogEvent(LOGID, "Activation request from " + address + " denied as known seed" ));
					}
					routingData = null;
				} else {
					if (!routingData.getAdapter().activateRequest( address)) {
						if (Logger.isEnabled()) {
							Logger.log(new LogEvent(LOGID, "Activation request from " + address + " denied by rules" ));
						}
						routingData = null;
					}
				}
			}
		}
		return routingData;
	}

	public PeerManagerRegistration manualMatchLink(
			InetSocketAddress	address,
			String				link) {
		byte[]	hash;
		try {
			managers_mon.enter();
			PeerManagerRegistrationImpl	registration = registered_links.get(link);
			if (registration == null) {
				return (null);
			}
			hash = registration.getHash();
		} finally {
			managers_mon.exit();
		}
		return (manualMatchHash( address, hash));
	}

	public void manualRoute(
		PeerManagerRegistration		_registration,
		NetworkConnection			_connection,
		PeerManagerRoutingListener	_listener) {
		PeerManagerRegistrationImpl	registration = (PeerManagerRegistrationImpl)_registration;
		registration.route(_connection, _listener);
	}

	public PeerManagerRegistration registerLegacyManager(
		HashWrapper						hash,
		PeerManagerRegistrationAdapter  adapter) {
		
		try {
			managers_mon.enter();
			// normally we only get a max of 1 of these. However, due to DownloadManager crazyness
			// we can get an extra one when adding a download that already exists...
			List<PeerManagerRegistrationImpl>	registrations = registered_legacy_managers.get(hash);
			byte[][]	secrets = adapter.getSecrets();
			if (registrations == null) {
				registrations = new ArrayList<PeerManagerRegistrationImpl>(1);
				registered_legacy_managers.put(hash, registrations);
				IncomingConnectionManager.getSingleton().addSharedSecrets(secrets);
			}
			PeerManagerRegistrationImpl	registration = new PeerManagerRegistrationImpl(hash, adapter);
			registrations.add(registration);
			return (registration);
		} finally {
			managers_mon.exit();
		}
	}


	private class PeerManagerRegistrationImpl
		implements PeerManagerRegistration {
		
		private final HashWrapper 				hash;
		final PeerManagerRegistrationAdapter	adapter;
		private PEPeerControl					download;
		private volatile PEPeerControl			activeControl;
		private List<Object[]>	pendingConnections;
		private BloomFilter		known_seeds;
		private Map<String,TOTorrentFile>		links;
		
		protected PeerManagerRegistrationImpl(
			HashWrapper						_hash,
			PeerManagerRegistrationAdapter	_adapter) {
			hash	= _hash;
			adapter	= _adapter;
		}
		
		protected PeerManagerRegistrationAdapter getAdapter() {
			return (adapter);
		}
		
		protected byte[] getHash() {
			return (hash.getBytes());
		}
		
		public TOTorrentFile getLink(String		target) {
			synchronized(this) {
				if (links == null) {
					return (null);
				}
				return (links.get(target));
			}
		}
		
		public void addLink(
			String			link,
			TOTorrentFile	target)
			throws Exception
		{
			try {
				managers_mon.enter();
				if (registered_links.get(link) != null) {
					throw (new Exception("Duplicate link '" + link + "'"));
				}
				registered_links.put(link, this);
				//System.out.println("Added link '" + link + "'");
			} finally {
				managers_mon.exit();
			}
			synchronized(this) {
				if (links == null) {
					links = new HashMap<String,TOTorrentFile>();
				}
				links.put(link, target);
			}
		}
		
		public void removeLink(
			String			link) {
			try {
				managers_mon.enter();
				registered_links.remove(link);
			} finally {
				managers_mon.exit();
			}
			synchronized(this) {
				if (links != null) {
					links.remove(link);
				}
			}
		}
		
		public boolean isActive() {
			return (activeControl != null);
		}
		
		public void activate(
			PEPeerControl	_active_control) {
			List<Object[]>	connections = null;
			try {
				managers_mon.enter();
				activeControl = _active_control;
				if (download != null) {
					Debug.out("Already activated");
				}
				download = _active_control;
				connections = pendingConnections;
				pendingConnections = null;
			} finally {
				managers_mon.exit();
			}
			if (connections != null) {
				for (int i=0;i<connections.size();i++) {
					Object[]	entry = connections.get(i);
					NetworkConnection	nc = (NetworkConnection)entry[0];
					PeerManagerRoutingListener	listener = (PeerManagerRoutingListener)entry[2];
					route(_active_control, nc, true, listener);
				}
			}
		}
		
		public void deactivate() {
			try {
				managers_mon.enter();
				if (download == null) {
					Debug.out("Already deactivated");
				} else {
					download	= null;
				}
				activeControl = null;
				if (pendingConnections != null) {
					for (int i=0;i<pendingConnections.size();i++) {
						Object[]	entry = pendingConnections.get(i);
						NetworkConnection	connection = (NetworkConnection)entry[0];
						if (Logger.isEnabled())
							Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING,
								"Incoming connection from [" + connection + "]"
								+ " closed due to deactivation" ));
						connection.close("deactivated");
					}
					pendingConnections = null;
				}
			} finally {
				managers_mon.exit();
			}
		}
		
		public void unregister() {
			try {
				managers_mon.enter();
				if (activeControl != null) {
					Debug.out("Not deactivated");
					deactivate();
				}
				List<PeerManagerRegistrationImpl>	registrations = registered_legacy_managers.get(hash);
				if (registrations == null) {
					Debug.out("manager already deregistered");
				} else {
					if (registrations.remove( this)) {
						if (registrations.size() == 0) {
							IncomingConnectionManager.getSingleton().removeSharedSecrets( adapter.getSecrets());
							registered_legacy_managers.remove(hash);
						}
					} else {
						Debug.out("manager already deregistered");
					}
				}
				synchronized(this) {
					if (links != null) {
						Iterator<String>	it = links.keySet().iterator();
						while (it.hasNext()) {
							registered_links.remove( it.next());
						}
					}
				}
			} finally {
				managers_mon.exit();
			}
		}
		
		protected boolean isKnownSeed(
			InetSocketAddress		address) {
			try {
				managers_mon.enter();
				if (known_seeds == null) {
					return (false);
				}
				return (known_seeds.contains( AddressUtils.getAddressBytes( address)));
			} finally {
				managers_mon.exit();
			}
		}
		
		protected void setKnownSeed(
				InetSocketAddress		address) {
			try {
				managers_mon.enter();
				if (known_seeds == null) {
					known_seeds = BloomFilterFactory.createAddOnly(1024);
				}
				// can't include port as it will be a randomly allocated one in general. two people behind the
				// same NAT will have to connect to each other using LAN peer finder
				known_seeds.add(AddressUtils.getAddressBytes( address));
			} finally {
				managers_mon.exit();
			}
		}
		
		protected PEPeerControl
		getActiveControl() {
			return (activeControl);
		}
		
		protected void route(
			NetworkConnection 			connection,
			PeerManagerRoutingListener	listener) {
			
			if (adapter.manualRoute( connection)) {
				return;
			}
			
			if (!adapter.isPeerSourceEnabled( PEPeerSource.PS_INCOMING)) {
				if (Logger.isEnabled())
					Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING,
							"Incoming connection from [" + connection
							+ "] to " + adapter.getDescription() + " dropped as peer source disabled" ));
				connection.close("peer source disabled");
				return;
			}
			
			PEPeerControl	control;
			boolean	register_for_timeouts = false;
			try {
				managers_mon.enter();
				control = activeControl;
				if (control == null) {
					// not yet activated, queue connection for use on activation
					if (pendingConnections != null && pendingConnections.size() > 10) {
						if (Logger.isEnabled())
							Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING,
									"Incoming connection from [" + connection
									+ "] to " + adapter.getDescription() + " dropped too many pending activations" ));
						connection.close("too many pending activations");
						return;
					} else {
						if (pendingConnections == null) {
							pendingConnections = new ArrayList<Object[]>();
						}
						pendingConnections.add(new Object[]{ connection, new Long( SystemTime.getCurrentTime()), listener });
						if (pendingConnections.size() == 1) {
							register_for_timeouts	= true;
						}
					}
				}
			} finally {
				managers_mon.exit();
			}
			// do this outside the monitor as the timeout code calls us back holding the timeout monitor
			// and we need to grab managers_mon inside this to run timeouts
			if (register_for_timeouts) {
				registerForTimeouts(this);
			}
			if (control != null) {
				route(control, connection, false, listener);
			}
		}
		
		protected boolean timeoutCheck() {
			try {
				managers_mon.enter();
				if (pendingConnections == null) {
					return (false);
				}
				Iterator<Object[]> it = pendingConnections.iterator();
				long	now = SystemTime.getCurrentTime();
				while (it.hasNext()) {
					Object[]	entry = it.next();
					long	start_time = ((Long)entry[1]).longValue();
					if (now < start_time) {
						entry[1] = new Long(now);
					} else if (now - start_time > PENDING_TIMEOUT) {
						it.remove();
						NetworkConnection	connection = (NetworkConnection)entry[0];
						if (Logger.isEnabled())
							Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING,
									"Incoming connection from [" + connection
									+ "] to " + adapter.getDescription() + " closed due to activation timeout" ));
						connection.close("activation timeout");
					}
				}
				if (pendingConnections.size() == 0) {
					pendingConnections = null;
				}
				return (pendingConnections != null);
			} finally {
				managers_mon.exit();
			}
		}
		
		protected void route(
				PEPeerControl				control,
				final NetworkConnection 	connection,
				boolean						is_activation,
				PeerManagerRoutingListener	listener) {
			// make sure not already connected to the same IP address; allow
			// loopback connects for co-located proxy-based connections and
			// testing
			Object callback = connection.getUserData("RoutedCallback");
			if (callback instanceof AEGenericCallback) {
				try {
					((AEGenericCallback)callback).invoke(control);
				} catch (Throwable e) {
					Debug.out(e);
				}
			}
			ConnectionEndpoint ep = connection.getEndpoint();

			InetSocketAddress is_address = ep.getNotionalAddress();
			String host_address = AddressUtils.getHostAddress(is_address);
			String net_cat = AENetworkClassifier.categoriseAddress(host_address);
			if (!control.isNetworkEnabled( net_cat)) {
				connection.close("Network '" + net_cat + "' is not enabled");
				return;
			}
			
			InetAddress address_mbn = is_address.getAddress();
			boolean same_allowed = COConfigurationManager.getBooleanParameter("Allow Same IP Peers") || ( address_mbn != null && address_mbn.isLoopbackAddress());
			if (!same_allowed && PeerIdentityManager.containsIPAddress( control.getPeerIdentityDataID(), host_address)) {
				if (Logger.isEnabled()) {
					Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING,
							"Incoming connection from [" + connection
							+ "] dropped as IP address already "
							+ "connected for ["
							+ control.getDisplayName() + "]"));
				}
				connection.close("already connected to peer");
				return;
			}
			
			if (AERunStateHandler.isUDPNetworkOnly()) {
				if (connection.getTransport().getTransportEndpoint().getProtocolEndpoint().getType() == ProtocolEndpoint.PROTOCOL_TCP) {
					if (!connection.isLANLocal()) {
						connection.close("limited network mode: tcp disabled");
						return;
					}
				}
			}
			
			if (Logger.isEnabled()) {
				Logger.log(new LogEvent(LOGID, "Incoming connection from ["
						+ connection + "] routed to legacy download ["
						+ control.getDisplayName() + "]"));
			}
			
			PEPeerTransport	pt = PEPeerTransportFactory.createTransport(control, PEPeerSource.PS_INCOMING, connection, null);
			if (listener != null) {
				boolean	ok = false;
				try {
					if (listener.routed( pt)) {
						ok	= true;
					}
				} catch (Throwable e) {
					Debug.printStackTrace(e);
				}
				if (!ok) {
					connection.close("routing denied");
					return;
				}
			}
			pt.start();
			if (is_activation) {
				pt.addListener(
					new PEPeerListener() {
						public void stateChanged(
								PEPeer 		peer,
								int 		new_state) {
							if (new_state == PEPeer.CLOSING) {
								if (peer.isSeed()) {
									InetSocketAddress	address = connection.getEndpoint().getNotionalAddress();
									setKnownSeed(address);
									// this is mainly to deal with seeds that incorrectly connect to us
									adapter.deactivateRequest(address);
								}
							}
						}
						public void sentBadChunk(PEPeer peer, int piece_num, int total_bad_chunks) {}
						public void addAvailability(final PEPeer peer, final BitFlags peerHavePieces) {}
						public void removeAvailability(final PEPeer peer, final BitFlags peerHavePieces) {}
					}
				);
			}
			control.addPeerTransport(pt);
		}
		
		public String getDescription() {
			PEPeerControl	control = activeControl;
			return ( ByteFormatter.encodeString( hash.getBytes()) + ", control=" + (control==null?null:control.getDisplayName()) + ": " + adapter.getDescription());
		}
	}
}
