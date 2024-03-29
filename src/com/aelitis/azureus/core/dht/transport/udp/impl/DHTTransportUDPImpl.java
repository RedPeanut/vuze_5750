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

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.ipfilter.IpFilter;
import org.gudy.azureus2.core3.ipfilter.IpFilterManagerFactory;
import org.gudy.azureus2.core3.util.AEMonitor;
import org.gudy.azureus2.core3.util.AERunnable;
import org.gudy.azureus2.core3.util.AESemaphore;
import org.gudy.azureus2.core3.util.AEThread2;
import org.gudy.azureus2.core3.util.Average;
import org.gudy.azureus2.core3.util.Constants;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.DelayedEvent;
import org.gudy.azureus2.core3.util.RandomUtils;
import org.gudy.azureus2.core3.util.SimpleTimer;
import org.gudy.azureus2.core3.util.SystemTime;
import org.gudy.azureus2.core3.util.TimerEvent;
import org.gudy.azureus2.core3.util.TimerEventPerformer;

import com.aelitis.azureus.core.dht.DHT;
import com.aelitis.azureus.core.dht.DHTLogger;
import com.aelitis.azureus.core.dht.netcoords.DHTNetworkPosition;
import com.aelitis.azureus.core.dht.netcoords.DHTNetworkPositionManager;
import com.aelitis.azureus.core.dht.netcoords.DHTNetworkPositionProvider;
import com.aelitis.azureus.core.dht.netcoords.DHTNetworkPositionProviderListener;
import com.aelitis.azureus.core.dht.transport.DHTTransportAlternativeContact;
import com.aelitis.azureus.core.dht.transport.DHTTransportAlternativeNetwork;
import com.aelitis.azureus.core.dht.transport.DHTTransportContact;
import com.aelitis.azureus.core.dht.transport.DHTTransportException;
import com.aelitis.azureus.core.dht.transport.DHTTransportFindValueReply;
import com.aelitis.azureus.core.dht.transport.DHTTransportFullStats;
import com.aelitis.azureus.core.dht.transport.DHTTransportListener;
import com.aelitis.azureus.core.dht.transport.DHTTransportProgressListener;
import com.aelitis.azureus.core.dht.transport.DHTTransportQueryStoreReply;
import com.aelitis.azureus.core.dht.transport.DHTTransportReplyHandler;
import com.aelitis.azureus.core.dht.transport.DHTTransportReplyHandlerAdapter;
import com.aelitis.azureus.core.dht.transport.DHTTransportRequestHandler;
import com.aelitis.azureus.core.dht.transport.DHTTransportStats;
import com.aelitis.azureus.core.dht.transport.DHTTransportStoreReply;
import com.aelitis.azureus.core.dht.transport.DHTTransportTransferHandler;
import com.aelitis.azureus.core.dht.transport.DHTTransportValue;
import com.aelitis.azureus.core.dht.transport.udp.DHTTransportUDP;
import com.aelitis.azureus.core.dht.transport.udp.DHTTransportUDPContact;
import com.aelitis.azureus.core.dht.transport.udp.impl.packethandler.DHTUDPPacketHandler;
import com.aelitis.azureus.core.dht.transport.udp.impl.packethandler.DHTUDPPacketHandlerException;
import com.aelitis.azureus.core.dht.transport.udp.impl.packethandler.DHTUDPPacketHandlerFactory;
import com.aelitis.azureus.core.dht.transport.udp.impl.packethandler.DHTUDPPacketHandlerStub;
import com.aelitis.azureus.core.dht.transport.udp.impl.packethandler.DHTUDPPacketReceiver;
import com.aelitis.azureus.core.dht.transport.udp.impl.packethandler.DHTUDPRequestHandler;
import com.aelitis.azureus.core.dht.transport.util.DHTTransferHandler;
import com.aelitis.azureus.core.dht.transport.util.DHTTransferHandler.Packet;
import com.aelitis.azureus.core.dht.transport.util.DHTTransportRequestCounter;
import com.aelitis.azureus.core.util.average.AverageFactory;
import com.aelitis.azureus.core.util.average.MovingImmediateAverage;
import com.aelitis.azureus.core.util.bloom.BloomFilter;
import com.aelitis.azureus.core.util.bloom.BloomFilterFactory;
import com.aelitis.azureus.core.versioncheck.VersionCheckClient;
import com.aelitis.net.udp.uc.PRUDPPacketHandler;

import hello.util.Log;
import hello.util.SingleCounter2;

/**
 * @author parg
 *
 */

public class DHTTransportUDPImpl
	implements DHTTransportUDP, DHTUDPRequestHandler {
	
	private static String TAG = DHTTransportUDPImpl.class.getSimpleName();
	
	@SuppressWarnings("CanBeFinal")
	public static boolean TEST_EXTERNAL_IP	= false;

	public static final int		MIN_ADDRESS_CHANGE_PERIOD_INIT_DEFAULT	= 5*60*1000;
	public static final int		MIN_ADDRESS_CHANGE_PERIOD_NEXT_DEFAULT	= 10*60*1000;

	public static final int		STORE_TIMEOUT_MULTIPLIER = 2;


	private String				externalAddress;
	private int					min_address_change_period = MIN_ADDRESS_CHANGE_PERIOD_INIT_DEFAULT;

	private final byte			protocolVersion;
	private final int			network;
	private final boolean		v6;
	private final String		ipOverride;
	private int					port;
	private final int			maxFailsForLive;
	private final int			maxFailsForUnknown;
	private long				requestTimeout;
	private long				storeTimeout;
	private boolean				reachable;
	private boolean				reachable_accurate;
	private final int			dhtSendDelay;
	private final int			dhtReceiveDelay;

	final DHTLogger logger;
	private DHTUDPPacketHandler packetHandler;
	private DHTTransportRequestHandler requestHandler;
	private DHTTransportUDPContactImpl localContact;
	private long last_address_change;
	final List listeners = new ArrayList();
	private final IpFilter ipFilter = IpFilterManagerFactory.getSingleton().getIPFilter();

	private DHTTransportUDPStatsImpl stats;
	private boolean bootstrapNode = false;

	private byte generic_flags	= DHTTransportUDP.GF_NONE;
	private byte generic_flags2	= VersionCheckClient.getSingleton().getDHTFlags();

	private static final int CONTACT_HISTORY_MAX 		= 32;
	private static final int CONTACT_HISTORY_PING_SIZE	= 24;

	final Map<InetSocketAddress,DHTTransportContact> contactHistory =
		new LinkedHashMap<InetSocketAddress,DHTTransportContact>(CONTACT_HISTORY_MAX,0.75f,true) {
			protected boolean removeEldestEntry(Map.Entry<InetSocketAddress, DHTTransportContact> eldest) {
				return size() > CONTACT_HISTORY_MAX;
			}
		};

	private static final int ROUTABLE_CONTACT_HISTORY_MAX 		= 128;

	final Map<InetSocketAddress,DHTTransportContact>	routableContactHistory =
		new LinkedHashMap<InetSocketAddress,DHTTransportContact>(ROUTABLE_CONTACT_HISTORY_MAX,0.75f,true) {
			protected boolean removeEldestEntry(Map.Entry<InetSocketAddress,DHTTransportContact> eldest) {
				return size() > ROUTABLE_CONTACT_HISTORY_MAX;
			}
		};


	private long							otherRoutableTotal;
	private long							otherNonRoutableTotal;
	private final MovingImmediateAverage	routeablePercentageAverage = AverageFactory.MovingImmediateAverage(8);

	private static final int RECENT_REPORTS_HISTORY_MAX = 32;

	private final Map	recentReports =
		new LinkedHashMap(RECENT_REPORTS_HISTORY_MAX,0.75f,true) {
			protected boolean removeEldestEntry(Map.Entry eldest) {
				return size() > RECENT_REPORTS_HISTORY_MAX;
			}
		};


	private static final int	STATS_PERIOD		= 60*1000;
	private static final int 	STATS_DURATION_SECS	= 600;			// 10 minute average
	private static final long	STATS_INIT_PERIOD	= 15*60*1000;	// bit more than 10 mins to allow average to establish

	private long	statsStartTime	= SystemTime.getCurrentTime();
	private long	lastAlienCount;
	private long	lastAlienFvCount;

	private final Average	alienAverage 	= Average.getInstance(STATS_PERIOD,STATS_DURATION_SECS);
	private final Average	alienFvAverage 	= Average.getInstance(STATS_PERIOD,STATS_DURATION_SECS);

	private Random				random;

	private static final int	BAD_IP_BLOOM_FILTER_SIZE	= 32000;
	private BloomFilter			badIpBloomFilter;

	private static final AEMonitor	classMon	= new AEMonitor("DHTTransportUDP:class");

	final AEMonitor	thisMon = new AEMonitor("DHTTransportUDP");

	private boolean		initial_address_change_deferred;
	private boolean		address_changing;

	private final DHTTransferHandler xferHandler;

	public DHTTransportUDPImpl(
			byte			_protocolVersion,
			int				_network,
			boolean			_v6,
			String			_ip,
			String			_defaultIp,
			int				_port,
			int				_maxFailsForLive,
			int				_maxFailsForUnknown,
			long			_timeout,
			int				_dhtSendDelay,
			int				_dhtReceiveDelay,
			boolean			_bootstrapNode,
			boolean			_initialReachability,
			DHTLogger		_logger)
		throws DHTTransportException {
		
		/*int count = SingleCounter2.getInstance().getAndIncreaseCount();
		Log.d(TAG, String.format("how many times <init> is called... #%d", count));*/
		
		protocolVersion			= _protocolVersion;
		network					= _network;
		v6						= _v6;
		ipOverride				= _ip;
		port					= _port;
		maxFailsForLive			= _maxFailsForLive;
		maxFailsForUnknown		= _maxFailsForUnknown;
		requestTimeout			= _timeout;
		dhtSendDelay			= _dhtSendDelay;
		dhtReceiveDelay			= _dhtReceiveDelay;
		bootstrapNode			= _bootstrapNode;
		reachable				= _initialReachability;
		logger					= _logger;
		storeTimeout			= requestTimeout * STORE_TIMEOUT_MULTIPLIER;
		try {
			random = RandomUtils.SECURE_RANDOM;
		} catch (Throwable e) {
			random	= new Random();
			logger.log(e);
		}
		
		xferHandler =
			new DHTTransferHandler(
				new DHTTransferHandler.Adapter() {
					public void sendRequest(DHTTransportContact _contact, Packet packet) {
						DHTTransportUDPContactImpl contact = (DHTTransportUDPContactImpl)_contact;
						DHTUDPPacketData request =
							new DHTUDPPacketData(
								DHTTransportUDPImpl.this,
								packet.getConnectionId(),
								localContact,
								contact);
						
						request.setDetails(
							packet.getPacketType(),
							packet.getTransferKey(),
							packet.getRequestKey(),
							packet.getData(),
							packet.getStartPosition(),
							packet.getLength(),
							packet.getTotalLength());
						
						try {
							checkAddress(contact);
							stats.dataSent(request);
							packetHandler.send(
								request,
								contact.getTransportAddress());
						} catch (Throwable e) {
						}
					}
					
					public long getConnectionID() {
						return (DHTTransportUDPImpl.this.getConnectionID());
					}
				},
				DHTUDPPacketData.MAX_DATA_SIZE,
				1,
				logger);
		int lastPct = COConfigurationManager.getIntParameter("dht.udp.net" + network + ".routeable_pct", -1);
		if (lastPct > 0) {
			routeablePercentageAverage.update(lastPct);
		}
		DHTUDPUtils.registerTransport(this);
		createPacketHandler();
		SimpleTimer.addPeriodicEvent(
			"DHTUDP:stats",
			STATS_PERIOD,
			new TimerEventPerformer() {
				private int tickCount;
				public void perform(TimerEvent	event) {
					updateStats(tickCount++);
					checkAltContacts();
				}
			});
		String defaultIp = _defaultIp==null?(v6?"::1":"127.0.0.1"):_defaultIp;
		getExternalAddress(defaultIp, logger);
		InetSocketAddress address = new InetSocketAddress(externalAddress, port);
		DHTNetworkPositionManager.addProviderListener(
			new DHTNetworkPositionProviderListener() {
				public void providerAdded(DHTNetworkPositionProvider provider) {
					if (localContact != null) {
						localContact.createNetworkPositions(true);
						try {
							thisMon.enter();
							for (DHTTransportContact c: contactHistory.values()) {
								c.createNetworkPositions(false);
							}
							for (DHTTransportContact c: routableContactHistory.values()) {
								c.createNetworkPositions(false);
							}
						} finally {
							thisMon.exit();
						}
						for (int i=0;i<listeners.size();i++) {
							try {
								((DHTTransportListener)listeners.get(i)).resetNetworkPositions();
							} catch (Throwable e) {
								Debug.printStackTrace(e);
							}
						}
					}
				}

				public void providerRemoved(DHTNetworkPositionProvider provider) {
				}
				
			});
		logger.log("Initial external address: " + address);
		localContact = new DHTTransportUDPContactImpl(true, this, 
				address, // transport
				address, // external
				protocolVersion, random.nextInt(), 0, (byte)0);
	}

	protected void createPacketHandler() throws DHTTransportException {
		DHTUDPPacketHelper.registerCodecs();
		// DHTPRUDPPacket relies on the request-handler being an instanceof THIS so watch out
		// if you change it :)
		try {
			if (packetHandler != null && !packetHandler.isDestroyed()) {
				packetHandler.destroy();
			}
			packetHandler = DHTUDPPacketHandlerFactory.getHandler(this, this);
		} catch (Throwable e) {
			throw (new DHTTransportException("Failed to get packet handler", e));
		}
		
		// limit send and receive rates. Receive rate is lower as we want a stricter limit
		// on the max speed we generate packets than those we're willing to process.
		// logger.log("send delay = " + _dht_send_delay + ", recv = " + _dht_receive_delay);
		packetHandler.setDelays(dhtSendDelay, dhtReceiveDelay, (int)requestTimeout);
		statsStartTime = SystemTime.getCurrentTime();
		if (stats == null) {
			stats = new DHTTransportUDPStatsImpl(this, protocolVersion, packetHandler.getStats());
		} else {
			stats.setStats(packetHandler.getStats());
		}
	}

	public DHTUDPRequestHandler getRequestHandler() {
		return (this);
	}

	public DHTUDPPacketHandler getPacketHandler() {
		return (packetHandler);
	}

	public void	setSuspended(boolean susp) {
		if (susp) {
			if (packetHandler != null) {
				packetHandler.destroy();
			}
		} else {
			if (packetHandler == null || packetHandler.isDestroyed()) {
				try {
					createPacketHandler();
				} catch (Throwable e) {
					Debug.out(e);
				}
			}
		}
	}

	protected void updateStats(int tick_count) {
		// pick up latest value
		generic_flags2	= VersionCheckClient.getSingleton().getDHTFlags();
		long	alien_count	= 0;
		long[]	aliens = stats.getAliens();
		for (int i=0;i<aliens.length;i++) {
			alien_count	+= aliens[i];
		}
		long	alien_fv_count = aliens[ DHTTransportStats.AT_FIND_VALUE ];
		alienAverage.addValue( (alien_count-lastAlienCount)*STATS_PERIOD/1000);
		alienFvAverage.addValue( (alien_fv_count-lastAlienFvCount)*STATS_PERIOD/1000);
		lastAlienCount	= alien_count;
		lastAlienFvCount	= alien_fv_count;
		long now = SystemTime.getCurrentTime();
		if (now < 	statsStartTime) {
			statsStartTime	= now;
		} else {
			// only fiddle with the initial view of reachability when things have had
			// time to stabilise
			if (Constants.isCVSVersion()) {
				long fv_average 		= alienFvAverage.getAverage();
				long all_average 		= alienAverage.getAverage();
				logger.log("Aliens for net " + network + ": " + fv_average + "/" + all_average);
			}
			if (now - statsStartTime > STATS_INIT_PERIOD) {
				reachable_accurate	= true;
				boolean	old_reachable	= reachable;
				if (alienFvAverage.getAverage() > 1) {
					reachable	= true;
				} else if (alienAverage.getAverage() > 3) {
					reachable	= true;
				} else {
					reachable	= false;
				}
				if (old_reachable != reachable) {
					for (int i=0;i<listeners.size();i++) {
						try {
							((DHTTransportListener)listeners.get(i)).reachabilityChanged(reachable);
						} catch (Throwable e) {
							Debug.printStackTrace(e);
						}
					}
				}
			}
		}
		int	pct = getRouteablePercentage();
		if (pct > 0) {
			COConfigurationManager.setParameter("dht.udp.net" + network + ".routeable_pct", pct);
		}
		// System.out.println("routables=" + other_routable_total + ", non=" + other_non_routable_total);
		// System.out.println("net " + network + ": aliens = " + alien_average.getAverage() + ", alien fv = " + alien_fv_average.getAverage());
	}

	protected void recordSkew(
		InetSocketAddress	originator_address,
		long				skew
	) {
		if (stats != null) {
			stats.recordSkew(originator_address, skew);
		}
	}

	protected int getNodeStatus() {
		if (bootstrapNode) {
			// bootstrap node is special case and not generally routable
			return (0);
		}
		if (reachable_accurate) {
			int	status = reachable?DHTTransportUDPContactImpl.NODE_STATUS_ROUTABLE:0;
			return (status);
		} else {
			return (DHTTransportUDPContactImpl.NODE_STATUS_UNKNOWN);
		}
	}

	public boolean isReachable() {
		return (reachable);
	}

	public byte getProtocolVersion() {
		return (protocolVersion);
	}

	public byte
	getMinimumProtocolVersion() {
		return (getNetwork()==DHT.NW_CVS?DHTTransportUDP.PROTOCOL_VERSION_MIN_CVS:DHTTransportUDP.PROTOCOL_VERSION_MIN);
	}

	public int getPort() {
		return (port);
	}

	public void	setPort(int	newPort)
		throws DHTTransportException
	{
		if (newPort == port) {
			return;
		}
		port	= newPort;
		createPacketHandler();
		setLocalContact();
	}

	public long getTimeout() {
		return (requestTimeout);
	}

	public void setTimeout(
		long		timeout) {
		if (requestTimeout == timeout) {
			return;
		}
		requestTimeout = timeout;
		storeTimeout   = requestTimeout * STORE_TIMEOUT_MULTIPLIER;
		packetHandler.setDelays(dhtSendDelay, dhtReceiveDelay, (int)requestTimeout);
	}

	public int getNetwork() {
		return (network);
	}

	public byte
	getGenericFlags() {
		return (generic_flags);
	}

	public byte
	getGenericFlags2() {
		return (generic_flags2);
	}

	public void setGenericFlag(
		byte		flag,
		boolean		value) {
		synchronized(this) {
			if (value) {
				generic_flags |= flag;
			} else {
				generic_flags &= ~flag;
			}
		}
	}

	public boolean isIPV6() {
		return (v6);
	}

	public void testInstanceIDChange()

		throws DHTTransportException
	{
		localContact = new DHTTransportUDPContactImpl(true, this, localContact.getTransportAddress(), localContact.getExternalAddress(), protocolVersion, random.nextInt(), 0, (byte)0);
	}

	public void testTransportIDChange()

		throws DHTTransportException
	{
		if (externalAddress.equals("127.0.0.1")) {
			externalAddress = "192.168.0.2";
		} else {
			externalAddress = "127.0.0.1";
		}
		InetSocketAddress	address = new InetSocketAddress(externalAddress, port);
		localContact = new DHTTransportUDPContactImpl(true, this, address, address, protocolVersion, localContact.getInstanceID(), 0, (byte)0);
		for (int i=0;i<listeners.size();i++) {
			try {
				((DHTTransportListener)listeners.get(i)).localContactChanged(localContact);
			} catch (Throwable e) {
				Debug.printStackTrace(e);
			}
		}
	}

	public void testExternalAddressChange() {
		try {
			Iterator	it = contactHistory.values().iterator();
			DHTTransportUDPContactImpl c1 = (DHTTransportUDPContactImpl)it.next();
			DHTTransportUDPContactImpl c2 = (DHTTransportUDPContactImpl)it.next();
			externalAddressChange(c1, c2.getExternalAddress(), true);
			//externalAddressChange(c, new InetSocketAddress("192.168.0.7", 6881));
		} catch (Throwable e) {
			Debug.printStackTrace(e);
		}
	}

	public void testNetworkAlive(
		boolean		alive) {
		packetHandler.testNetworkAlive(alive);
	}

	protected void getExternalAddress(
		String				defaultAddress,
		final DHTLogger		log) {
		
		// class level synchronisation is for testing purposes when running multiple UDP instances
		// in the same VM
		try {
			classMon.enter();
			String newExternalAddress = null;
			try {
				log.log("Obtaining external address");
				if (TEST_EXTERNAL_IP) {
					newExternalAddress	= v6?"::1":"127.0.0.1";
					log.log("	External IP address obtained from test data: " + newExternalAddress);
				}
				
				if (ipOverride != null) {
					newExternalAddress	= ipOverride;
					log.log("	External IP address explicitly overridden: " + newExternalAddress);
				}
				
				if (newExternalAddress == null) {
					// First attempt is via other contacts we know about. Select three
					List	contacts;
					try {
						thisMon.enter();
						contacts = new ArrayList(contactHistory.values());
					} finally {
						thisMon.exit();
					}
					
					// randomly select a number of entries to ping until we
					// get three replies
					String	returnedAddress 	= null;
					int		returnedMatches	= 0;
					int		searchLim = Math.min(CONTACT_HISTORY_PING_SIZE, contacts.size());
					log.log("	Contacts to search = " + searchLim);
					for (int i=0;i<searchLim;i++) {
						DHTTransportUDPContactImpl	contact = (DHTTransportUDPContactImpl)contacts.remove(RandomUtils.nextInt(contacts.size()));
						InetSocketAddress a = askContactForExternalAddress(contact);
						if (a != null && a.getAddress() != null) {
							String	ip = a.getAddress().getHostAddress();
							if (returnedAddress == null) {
								returnedAddress = ip;
								log.log("	: contact " + contact.getString() + " reported external address as '" + ip + "'");
								returnedMatches++;
							} else if (returnedAddress.equals( ip)) {
								returnedMatches++;
								log.log("	: contact " + contact.getString() + " also reported external address as '" + ip + "'");
								if (returnedMatches == 3) {
									newExternalAddress	= returnedAddress;
									log.log("	External IP address obtained from contacts: "  + returnedAddress);
									break;
								}
							} else {
								log.log("	: contact " + contact.getString() + " reported external address as '" + ip + "', abandoning due to mismatch");
								// mismatch - give up
								break;
							}
						} else {
							log.log("	: contact " + contact.getString() + " didn't reply");
						}
					}
				}
				
				if (newExternalAddress == null) {
					InetAddress publicAddress = logger.getPluginInterface().getUtilities().getPublicAddress(v6);
					if (publicAddress != null) {
						newExternalAddress = publicAddress.getHostAddress();
						log.log("	External IP address obtained: " + newExternalAddress);
					}
				}
			} catch (Throwable e) {
				Debug.printStackTrace(e);
			}
			if (newExternalAddress == null) {
				newExternalAddress =	defaultAddress;
				log.log("	External IP address defaulted:  " + newExternalAddress);
			}
			if (externalAddress == null || !externalAddress.equals(newExternalAddress)) {
				informLocalAddress(newExternalAddress);
			}
			externalAddress = newExternalAddress;
		} finally {
			classMon.exit();
		}
	}

	protected void informLocalAddress(String	address) {
		for (int i=0;i<listeners.size();i++) {
			try {
				((DHTTransportListener)listeners.get(i)).currentAddress(address);
			} catch (Throwable e) {
				Debug.printStackTrace(e);
			}
		}
	}

	protected void externalAddressChange(
		final DHTTransportUDPContactImpl	reporter,
		final InetSocketAddress				new_address,
		boolean								force )

		throws DHTTransportException
	{
			/*
			 * A node has reported that our external address and the one he's seen a
			 * message coming from differ. Natural explanations are along the lines of
			 * 1) my address is dynamically allocated by my ISP and it has changed
			 * 2) I have multiple network interfaces
			 * 3) there's some kind of proxy going on
			 * 4) this is a DOS attempting to stuff me up
			 *
			 * We assume that our address won't change more frequently than once every
			 * 5 minutes
			 * We assume that if we can successfully obtain an external address by
			 * using the above explicit check then this is accurate
			 * Only in the case where the above check fails do we believe the address
			 * that we've been told about
			 */

		InetAddress	ia = new_address.getAddress();

		if (ia == null) {

			Debug.out("reported new external address '" + new_address + "' is unresolved");

			throw (new DHTTransportException("Address '" + new_address + "' is unresolved"));
		}

			// dump addresses incompatible with our protocol

		if (	(ia instanceof Inet4Address && v6) ||
				(ia instanceof Inet6Address && !v6)) {

				// reduce debug spam, just return

			// throw (new DHTTransportException("Address " + new_address + " is incompatible with protocol family for " + external_address));

			return;
		}

		final String	new_ip = ia.getHostAddress();

		if (new_ip.equals( externalAddress)) {

				// probably just be a second notification of an address change, return
				// "ok to retry" as it should now work

			return;
		}

		try {
			thisMon.enter();

			long	now = SystemTime.getCurrentTime();

			if (now - last_address_change < min_address_change_period) {

				return;
			}

			if (contactHistory.size() < CONTACT_HISTORY_MAX && !force) {

				if (!initial_address_change_deferred) {

					initial_address_change_deferred = true;

					logger.log("Node " + reporter.getString() + " has reported that the external IP address is '" + new_address + "': deferring new checks");

					new DelayedEvent(
						"DHTTransportUDP:delayAC",
						30*1000,
						new AERunnable() {
							public void runSupport() {
								try {
									externalAddressChange(reporter, new_address, true);

								} catch (Throwable e) {

								}
							}
						});
				}

				return;
			}

			logger.log("Node " + reporter.getString() + " has reported that the external IP address is '" + new_address + "'");

				// check for dodgy addresses that shouldn't appear as an external address!

			if (invalidExternalAddress( ia)) {

				logger.log("	 This is invalid as it is a private address.");

				return;
			}

				// another situation to ignore is where the reported address is the same as
				// the reporter (they must be seeing it via, say, socks connection on a local
				// interface

			if (reporter.getExternalAddress().getAddress().getHostAddress().equals(new_ip)) {

				logger.log("	 This is invalid as it is the same as the reporter's address.");

				return;
			}

			last_address_change	= now;

				// bump up min period for subsequent changes

			if (min_address_change_period == MIN_ADDRESS_CHANGE_PERIOD_INIT_DEFAULT) {

				min_address_change_period = MIN_ADDRESS_CHANGE_PERIOD_NEXT_DEFAULT;
			}
		} finally {

			thisMon.exit();
		}

		final String	old_external_address = externalAddress;

			// we need to perform this test on a separate thread otherwise we'll block in the UDP handling
			// code because we're already running on the "process" callback from the UDP handler
			// (the test attempts to ping contacts)


		new AEThread2("DHTTransportUDP:getAddress", true) {
			public void run() {
				try {
					thisMon.enter();

					if (address_changing) {

						return;
					}

					address_changing	= true;

				} finally {

					thisMon.exit();
				}

				try {
					getExternalAddress(new_ip, logger);

					if (old_external_address.equals( externalAddress)) {

							// address hasn't changed, notifier must be perceiving different address
							// due to proxy or something

						return;
					}

					setLocalContact();

				} finally {

					try {
						thisMon.enter();

						address_changing	= false;

					} finally {

						thisMon.exit();
					}
				}
			}
		}.start();
	}

	protected void contactAlive(
		DHTTransportUDPContactImpl	contact) {
		try {
			thisMon.enter();

			contactHistory.put(contact.getTransportAddress(), contact);

		} finally {

			thisMon.exit();
		}
	}

	public DHTTransportContact[]
   	getReachableContacts()
   	{
		try {
			thisMon.enter();

			Collection<DHTTransportContact> vals = routableContactHistory.values();

			DHTTransportContact[]	res = new DHTTransportContact[vals.size()];

			vals.toArray(res);

			return (res);

		} finally {

			thisMon.exit();
		}
   	}

	public DHTTransportContact[]
   	getRecentContacts()
   	{
		try {
			thisMon.enter();

			Collection<DHTTransportContact> vals = contactHistory.values();

			DHTTransportContact[]	res = new DHTTransportContact[vals.size()];

			vals.toArray(res);

			return (res);

		} finally {

			thisMon.exit();
		}
   	}

	protected void updateContactStatus(
		DHTTransportUDPContactImpl	contact,
		int							status,
		boolean						incoming) {
		try {
			thisMon.enter();
			contact.setNodeStatus(status);
			if (contact.getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_XFER_STATUS) {
				if (status != DHTTransportUDPContactImpl.NODE_STATUS_UNKNOWN) {
					boolean	other_routable = (status & DHTTransportUDPContactImpl.NODE_STATUS_ROUTABLE) != 0;
						// only maintain stats on incoming requests so we get a fair sample.
						// in general we'll only get replies from routable contacts so if we
						// take this into account then everything gets skewed
					if (other_routable) {
						if (incoming) {
							synchronized(routeablePercentageAverage) {
								otherRoutableTotal++;
							}
						}
						routableContactHistory.put(contact.getTransportAddress(), contact);
					} else {
						if (incoming) {
							synchronized(routeablePercentageAverage) {
								otherNonRoutableTotal++;
							}
						}
					}
				}
			}
		} finally {
			thisMon.exit();
		}
	}

	public int getRouteablePercentage() {
		synchronized(routeablePercentageAverage) {
			double	average = routeablePercentageAverage.getAverage();
			long	both_total = otherRoutableTotal + otherNonRoutableTotal;
			int	current_percent;
			if (both_total == 0) {
				current_percent = 0;
			} else {
				current_percent = (int)((otherRoutableTotal * 100 )/ both_total);
			}
			if (both_total >= 300) {
					// add current to average and reset
				if (current_percent > 0) {
					average = routeablePercentageAverage.update(current_percent);
					otherRoutableTotal = otherNonRoutableTotal = 0;
				}
			} else if (both_total >= 100) {
					// if we have enough samples and no existing average then use current
				if (average == 0) {
					average = current_percent;
				} else {
						// factor in current percantage
					int samples = routeablePercentageAverage.getSampleCount();
					if (samples > 0) {
						average = ((samples * average ) + current_percent ) / ( samples + 1);
					}
				}
			}
			int result = (int)average;
			if (result == 0) {
					// -1 indicates we have no usable value
				result = -1;
			}
			return (result);
		}
	}

	protected boolean invalidExternalAddress(InetAddress ia) {
		return (	
				ia.isLinkLocalAddress() ||
				ia.isLoopbackAddress() ||
				ia.isSiteLocalAddress()
		);
	}

	protected int getMaxFailForLiveCount() {
		return (maxFailsForLive);
	}

	protected int getMaxFailForUnknownCount() {
		return (maxFailsForUnknown);
	}

	public DHTTransportContact getLocalContact() {
		return (localContact);
	}

	protected void setLocalContact() {
		InetSocketAddress	s_address = new InetSocketAddress(externalAddress, port);
		try {
			localContact = new DHTTransportUDPContactImpl(true, DHTTransportUDPImpl.this, s_address, s_address, protocolVersion, random.nextInt(), 0, (byte)0);
			logger.log("External address changed: " + s_address);
			Debug.out("DHTTransport: address changed to " + s_address);
			for (int i=0;i<listeners.size();i++) {
				try {
					((DHTTransportListener)listeners.get(i)).localContactChanged(localContact);
				} catch (Throwable e) {
					Debug.printStackTrace(e);
				}
			}
		} catch (Throwable e) {
			Debug.printStackTrace(e);
		}
	}

	public DHTTransportContact importContact(
		DataInputStream		is,
		boolean				isBootstrap)
		throws IOException, DHTTransportException
	{
		DHTTransportUDPContactImpl contact = DHTUDPUtils.deserialiseContact(this, is);
		importContact(contact, isBootstrap);
		return (contact);
	}

	public DHTTransportUDPContact importContact(
			InetSocketAddress	_address,
			byte				_protocolVersion,
			boolean				isBootstrap)
		throws DHTTransportException {
		// instance id of 0 means "unknown"
		DHTTransportUDPContactImpl contact = new DHTTransportUDPContactImpl(false, this, _address, _address, _protocolVersion, 0, 0, (byte)0);
		importContact(contact, isBootstrap);
		return (contact);
	}

	protected void importContact(
		DHTTransportUDPContactImpl	contact,
		boolean						isBootstrap) {
		
		try {
			thisMon.enter();
			// consider newly imported contacts as potential contacts for IP address queries if we've
			// got space (in particular, at start of day we may be able to get an address off these if
			// they're still alive )
			if (contactHistory.size() < CONTACT_HISTORY_MAX) {
				contactHistory.put(contact.getTransportAddress(), contact);
			}
		} finally {
			thisMon.exit();
		}
		requestHandler.contactImported(contact, isBootstrap);
		//logger.log("Imported contact " + contact.getString());
	}

	public void exportContact(
		DHTTransportContact	contact,
		DataOutputStream	os )

		throws IOException, DHTTransportException
	{
		DHTUDPUtils.serialiseContact(os, contact);
	}

	public Map<String,Object>
	exportContactToMap(
		DHTTransportContact	contact) {
		Map<String,Object>		result = new HashMap<String, Object>();
		result.put("v",contact.getProtocolVersion());
		InetSocketAddress address = contact.getExternalAddress();
		result.put("p", address.getPort());
		InetAddress	ia = address.getAddress();
		if (ia == null) {

			result.put("h", address.getHostName());
		} else {
			result.put("a", ia.getAddress());
		}
		return (result);
	}

	public DHTTransportUDPContact
	importContact(
		Map<String,Object>		map) {
		int version = ((Number)map.get("v")).intValue();

		int port = ((Number)map.get("p")).intValue();

		byte[]	a = (byte[])map.get("a");

		InetSocketAddress address;

		try {
			if (a == null) {

				address = InetSocketAddress.createUnresolved(new String((byte[])map.get("h"), "UTF-8" ), port);
			} else {

				address = new InetSocketAddress(InetAddress.getByAddress( a ), port);
			}

			DHTTransportUDPContactImpl contact = new DHTTransportUDPContactImpl(false, this, address, address, (byte)version, 0, 0, (byte)0);

			importContact(contact, false);

			return (contact);

		} catch (Throwable e) {

			Debug.out(e);

			return (null);
		}
	}

	public void removeContact(
		DHTTransportContact	contact) {
		requestHandler.contactRemoved(contact);
	}

	public void setRequestHandler(DHTTransportRequestHandler _requestHandler) {
		requestHandler = new DHTTransportRequestCounter(_requestHandler, stats);
	}

	public DHTTransportStats getStats() {
		return (stats);
	}

	//protected HashMap	port_map = new HashMap();
	//protected long		last_portmap_dump	= SystemTime.getCurrentTime();

	protected void checkAddress(
		DHTTransportUDPContactImpl		contact )

		throws DHTUDPPacketHandlerException
	{
		/*
		int	port = contact.getExternalAddress().getPort();

		try {
			this_mon.enter();

			int	count;

			Integer i = (Integer)port_map.get(new Integer(port));

			if (i != null) {

				count 	= i.intValue() + 1;

			} else {

				count	= 1;
			}

			port_map.put(new Integer(port), new Integer(count));

			long	now = SystemTime.getCurrentTime();

			if (now - last_portmap_dump > 60000) {

				last_portmap_dump	= now;

				Iterator	it = port_map.keySet().iterator();

				Map	rev = new TreeMap();

				while (it.hasNext()) {

					Integer	key = (Integer)it.next();

					Integer	val = (Integer)port_map.get(key);

					rev.put(val, key);
				}

				it = rev.keySet().iterator();

				while (it.hasNext()) {

					Integer	val = (Integer)it.next();

					Integer	key = (Integer)rev.get(val);

					System.out.println("port:" + key + "->" + val);
				}
			}

		} finally {

			this_mon.exit();
		}
		*/

		if (ipFilter.isEnabled()) {

				// don't need to synchronize access to the bloom filter as it works fine
				// without protection (especially as its add only)

			InetAddress ia = contact.getTransportAddress().getAddress();

			if (ia != null) {

					// allow unresolved addresses through (e.g. ipv6 seed) as handled later

				byte[]	addr = ia.getAddress();

				if (badIpBloomFilter == null) {

					badIpBloomFilter = BloomFilterFactory.createAddOnly(BAD_IP_BLOOM_FILTER_SIZE);

				} else {

					if (badIpBloomFilter.contains( addr)) {

						throw (new DHTUDPPacketHandlerException("IPFilter check fails (repeat)"));
					}
				}

				if (ipFilter.isInRange(
						contact.getTransportAddress().getAddress(), "DHT", null,
						logger.isEnabled(DHTLogger.LT_IP_FILTER))) {

						// don't let an attacker deliberately fill up our filter so we start
						// rejecting valid addresses

					if (badIpBloomFilter.getEntryCount() >= BAD_IP_BLOOM_FILTER_SIZE/10) {

						badIpBloomFilter = BloomFilterFactory.createAddOnly(BAD_IP_BLOOM_FILTER_SIZE);
					}

					badIpBloomFilter.add(addr);

					throw (new DHTUDPPacketHandlerException("IPFilter check fails"));
				}
			}
		}
	}

	protected void sendPing(
		final DHTTransportUDPContactImpl	contact,
		final DHTTransportReplyHandler		handler,
		long								timeout,
		int									priority) {
		
		/*if (SingleCounter1.getInstance().getAndIncreaseCount() == 1) {
			Log.d(TAG, "sendPing() is called..."); 
			Throwable t = new Throwable();
			t.printStackTrace();
		}*/
		
		try {
			checkAddress(contact);
			final long connectionId = getConnectionID();
			final DHTUDPPacketRequestPing request =
				new DHTUDPPacketRequestPing(this, connectionId, localContact, contact);
			requestAltContacts(request);
			stats.pingSent(request);
			requestSendRequestProcessor(contact, request);
			packetHandler.sendAndReceive(
				request,
				contact.getTransportAddress(),
				new DHTUDPPacketReceiver() {
					
					public void packetReceived(
						DHTUDPPacketReply	packet,
						InetSocketAddress	fromAddress,
						long				elapsedTime) {
						try {
							if (packet.getConnectionId() != connectionId)
								throw (new Exception("connection id mismatch"));
							
							contact.setInstanceIDAndVersion(packet.getTargetInstanceID(), packet.getProtocolVersion());
							requestSendReplyProcessor(contact, handler, packet, elapsedTime);
							receiveAltContacts((DHTUDPPacketReplyPing)packet);
							stats.pingOK();
							long proc_time = packet.getProcessingTime();
							if (proc_time > 0) {
								elapsedTime -= proc_time;
								if (elapsedTime < 0) {
									elapsedTime = 0;
								}
							}
							handler.pingReply(contact, (int)elapsedTime);
						} catch (DHTUDPPacketHandlerException e) {
							error(e);
						} catch (Throwable e) {
							Debug.printStackTrace(e);
							error(new DHTUDPPacketHandlerException("ping failed", e));
						}
					}
					
					public void error(DHTUDPPacketHandlerException e) {
						stats.pingFailed();
						handler.failed(contact,e);
					}
				},
				timeout, priority);
		} catch (Throwable e) {
			stats.pingFailed();
			handler.failed(contact,e);
		}
	}

	protected void sendPing(
		final DHTTransportUDPContactImpl	contact,
		final DHTTransportReplyHandler		handler) {
		sendPing(contact, handler, requestTimeout, PRUDPPacketHandler.PRIORITY_MEDIUM);
	}

	protected void sendImmediatePing(
		final DHTTransportUDPContactImpl	contact,
		final DHTTransportReplyHandler		handler,
		long								timeout) {
		sendPing(contact, handler, timeout, PRUDPPacketHandler.PRIORITY_IMMEDIATE);
	}

	protected void sendKeyBlockRequest(
		final DHTTransportUDPContactImpl	contact,
		final DHTTransportReplyHandler		handler,
		byte[]								block_request,
		byte[]								block_signature) {
		try {
			checkAddress(contact);
			final long	connection_id = getConnectionID();
			final DHTUDPPacketRequestKeyBlock	request =
				new DHTUDPPacketRequestKeyBlock(this, connection_id, localContact, contact);
			request.setKeyBlockDetails(block_request, block_signature);
			stats.keyBlockSent(request);
			request.setRandomID(contact.getRandomID());
			requestSendRequestProcessor(contact, request);
			packetHandler.sendAndReceive(
				request,
				contact.getTransportAddress(),
				new DHTUDPPacketReceiver() {
					
					public void packetReceived(
						DHTUDPPacketReply	packet,
						InetSocketAddress	fromAddress,
						long				elapsedTime) {
						
						try {
							if (packet.getConnectionId() != connection_id)
								throw (new Exception("connection id mismatch"));
							
							contact.setInstanceIDAndVersion( packet.getTargetInstanceID(), packet.getProtocolVersion());
							requestSendReplyProcessor(contact, handler, packet, elapsedTime);
							stats.keyBlockOK();
							handler.keyBlockReply(contact);
						} catch (DHTUDPPacketHandlerException e) {
							error(e);
						} catch (Throwable e) {
							Debug.printStackTrace(e);
							error(new DHTUDPPacketHandlerException("send key block failed", e));
						}
					}
					
					public void error(
						DHTUDPPacketHandlerException	e) {
						stats.keyBlockFailed();
						handler.failed(contact,e);
					}
				},
				requestTimeout, PRUDPPacketHandler.PRIORITY_MEDIUM);
		} catch (Throwable e) {
			stats.keyBlockFailed();
			handler.failed(contact,e);
		}
	}

		// stats

	protected void sendStats(
		final DHTTransportUDPContactImpl	contact,
		final DHTTransportReplyHandler		handler) {
		try {
			checkAddress(contact);
			final long	connection_id = getConnectionID();
			final DHTUDPPacketRequestStats	request =
				new DHTUDPPacketRequestStats(this, connection_id, localContact, contact);
			// request.setStatsType(DHTUDPPacketRequestStats.STATS_TYPE_NP_VER2);
			stats.statsSent(request);
			requestSendRequestProcessor(contact, request);
			packetHandler.sendAndReceive(
				request,
				contact.getTransportAddress(),
				new DHTUDPPacketReceiver() {
					public void packetReceived(
						DHTUDPPacketReply	packet,
						InetSocketAddress	from_address,
						long				elapsed_time) {
						try {
							if (packet.getConnectionId() != connection_id) {
								throw (new Exception("connection id mismatch"));
							}
							contact.setInstanceIDAndVersion( packet.getTargetInstanceID(), packet.getProtocolVersion());
							requestSendReplyProcessor(contact, handler, packet, elapsed_time);
							DHTUDPPacketReplyStats	reply = (DHTUDPPacketReplyStats)packet;
							stats.statsOK();
							if (reply.getStatsType() == DHTUDPPacketRequestStats.STATS_TYPE_ORIGINAL) {
								handler.statsReply( contact, reply.getOriginalStats());
							} else {
								// currently no handler for new stats
								System.out.println("new stats reply:" + reply.getString());
							}
						} catch (DHTUDPPacketHandlerException e) {
							error(e);
						} catch (Throwable e) {
							Debug.printStackTrace(e);
							error(new DHTUDPPacketHandlerException("stats failed", e));
						}
					}
					public void error(
						DHTUDPPacketHandlerException	e) {
						stats.statsFailed();
						handler.failed(contact, e);
					}
				},
				requestTimeout, PRUDPPacketHandler.PRIORITY_LOW);
		} catch (Throwable e) {
			stats.statsFailed();
			handler.failed(contact, e);
		}
	}

	// PING for deducing external IP address
	protected InetSocketAddress askContactForExternalAddress(DHTTransportUDPContactImpl	contact) {
		try {
			checkAddress(contact);
			final long	connectionId = getConnectionID();
			final DHTUDPPacketRequestPing	request =
					new DHTUDPPacketRequestPing(this, connectionId, localContact, contact);
			stats.pingSent(request);
			final AESemaphore	sem = new AESemaphore("DHTTransUDP:extping");
			final InetSocketAddress[]	result = new InetSocketAddress[1];
			packetHandler.sendAndReceive(
				request,
				contact.getTransportAddress(),
				new DHTUDPPacketReceiver() {
					public void packetReceived(
							DHTUDPPacketReply	_packet,
							InetSocketAddress	fromAddress,
							long elapsedTime) {
						try {
							if (_packet instanceof DHTUDPPacketReplyPing) {
								// ping was OK so current address is OK
								result[0] = localContact.getExternalAddress();
							} else if (_packet instanceof DHTUDPPacketReplyError) {
								DHTUDPPacketReplyError packet = (DHTUDPPacketReplyError) _packet;
								if (packet.getErrorType() == DHTUDPPacketReplyError.ET_ORIGINATOR_ADDRESS_WRONG) {
									result[0] = packet.getOriginatingAddress();
								}
							}
						} finally {
							sem.release();
						}
					}

					public void error(DHTUDPPacketHandlerException e) {
						try {
							stats.pingFailed();
						} finally {
							sem.release();
						}
					}
				},
				5000, PRUDPPacketHandler.PRIORITY_HIGH);
			sem.reserve(5000);
			return (result[0]);
		} catch (Throwable e) {
			stats.pingFailed();
			return (null);
		}
	}

	// STORE
	public void sendStore(
		final DHTTransportUDPContactImpl	contact,
		final DHTTransportReplyHandler		handler,
		byte[][]							keys,
		DHTTransportValue[][]				valueSets,
		int									priority) {
		
		final long f_connectionId = getConnectionID();
		
		if (false) {
			int	total_values = 0;
			for (int i=0;i<keys.length;i++) {
				total_values += valueSets[i].length;
			}
			System.out.println("store: keys = " + keys.length +", values = " + total_values);
		}
		
		// only report to caller the outcome of the first packet
		int	packet_count	= 0;
		try {
			checkAddress(contact);
			int		current_key_index	= 0;
			int		current_value_index	= 0;
			while (current_key_index < keys.length) {
				packet_count++;
				int	space = DHTUDPPacketHelper.PACKET_MAX_BYTES - DHTUDPPacketRequest.DHT_HEADER_SIZE;
				List<byte[]> keyList = new ArrayList<>();
				List<List<DHTTransportValue>> valuesList = new ArrayList<>();
				keyList.add(keys[current_key_index]);
				space -= (keys[current_key_index].length + 1);	// 1 for length marker
				valuesList.add(new ArrayList<DHTTransportValue>());
				while (	space > 0 &&
						current_key_index < keys.length) {
					if (current_value_index == valueSets[current_key_index].length) {
						// all values from the current key have been processed
						current_key_index++;
						current_value_index	= 0;
						if (keyList.size() == DHTUDPPacketRequestStore.MAX_KEYS_PER_PACKET) {
							// no more keys allowed in this packet
							break;
						}
						if (current_key_index == keys.length) {
							// no more keys left, job done
							break;
						}
						keyList.add( keys[current_key_index]);
						space -= (keys[current_key_index].length + 1);	// 1 for length marker
						valuesList.add(new ArrayList<DHTTransportValue>());
					}
					DHTTransportValue value = valueSets[current_key_index][current_value_index];
					int	entry_size = DHTUDPUtils.DHTTRANSPORTVALUE_SIZE_WITHOUT_VALUE + value.getValue().length + 1;
					List<DHTTransportValue>	values = (List<DHTTransportValue>)valuesList.get(valuesList.size()-1);
					if (	space < entry_size ||
							values.size() == DHTUDPPacketRequestStore.MAX_VALUES_PER_KEY) {
							// no space left or we've used up our limit on the
							// number of values permitted per key
						break;
					}
					values.add(value);
					space -= entry_size;
					current_value_index++;
				}
				int	packet_entries = keyList.size();
				if (packet_entries > 0) {
						// if last entry has no values then ignore it
					if (((List)valuesList.get( packet_entries-1)).size() == 0) {
						packet_entries--;
					}
				}
				if (packet_entries == 0) {
					break;
				}
				byte[][]				packet_keys 		= new byte[packet_entries][];
				DHTTransportValue[][]	packet_value_sets 	= new DHTTransportValue[packet_entries][];
				//int	packet_value_count = 0;
				for (int i=0;i<packet_entries;i++) {
					packet_keys[i] = (byte[])keyList.get(i);
					List	values = (List)valuesList.get(i);
					packet_value_sets[i] = new DHTTransportValue[values.size()];
					for (int j=0;j<values.size();j++) {
						packet_value_sets[i][j] = (DHTTransportValue)values.get(j);
						//packet_value_count++;
					}
				}
				// System.out.println("	packet " + packet_count + ": keys = " + packet_entries + ", values = " + packet_value_count);

				final DHTUDPPacketRequestStore request =
					new DHTUDPPacketRequestStore(this, f_connectionId, localContact, contact);
				stats.storeSent(request);
				request.setRandomID( contact.getRandomID());
				request.setKeys(packet_keys);
				request.setValueSets(packet_value_sets);
				final int f_packet_count	= packet_count;
				requestSendRequestProcessor(contact, request);
				packetHandler.sendAndReceive(
					request,
					contact.getTransportAddress(),
					
					new DHTUDPPacketReceiver() {
						
						public void packetReceived(
							DHTUDPPacketReply	packet,
							InetSocketAddress	fromAddress,
							long				elapsedTime) {
							try {
								if (packet.getConnectionId() != f_connectionId)
									throw (new Exception("connection id mismatch: sender=" + fromAddress + ",packet=" + packet.getString()));
								
								contact.setInstanceIDAndVersion(packet.getTargetInstanceID(), packet.getProtocolVersion());
								requestSendReplyProcessor(contact, handler, packet, elapsedTime);
								DHTUDPPacketReplyStore reply = (DHTUDPPacketReplyStore)packet;
								stats.storeOK();
								if (f_packet_count == 1) {
									handler.storeReply( contact, reply.getDiversificationTypes());
								}
							} catch (DHTUDPPacketHandlerException e) {
								error(e);
							} catch (Throwable e) {
								Debug.printStackTrace(e);
								error(new DHTUDPPacketHandlerException("store failed", e));
							}
						}
						
						public void error(
							DHTUDPPacketHandlerException	e) {
							stats.storeFailed();
							if (f_packet_count == 1) {
								handler.failed(contact, e);
							}
						}
					},
					storeTimeout,
					priority);
			}
		} catch (Throwable e) {
			stats.storeFailed();
			if (packet_count <= 1) {
				handler.failed(contact, e);
			}
		}
	}

	// QUERY STORE

	public void sendQueryStore(
		final DHTTransportUDPContactImpl	contact,
		final DHTTransportReplyHandler 		handler,
		int									header_size,
		List<Object[]>						key_details) {
		try {
			checkAddress(contact);
			final long	connection_id = getConnectionID();
			Iterator<Object[]> it = key_details.iterator();
			byte[]				current_prefix			= null;
			Iterator<byte[]>	current_suffixes 		= null;
			List<DHTUDPPacketRequestQueryStorage> requests = new ArrayList<DHTUDPPacketRequestQueryStorage>();
outer:
			while (it.hasNext()) {
				int	space = DHTUDPPacketRequestQueryStorage.SPACE;
				DHTUDPPacketRequestQueryStorage	request =
					new DHTUDPPacketRequestQueryStorage(this, connection_id, localContact, contact);
				List<Object[]> packet_key_details = new ArrayList<Object[]>();
				while (space > 0 && it.hasNext()) {
					if (current_prefix == null) {
						Object[] entry = it.next();
						current_prefix = (byte[])entry[0];
						List<byte[]> l = (List<byte[]>)entry[1];
						current_suffixes = l.iterator();
					}
					if (current_suffixes.hasNext()) {
						int	min_space = header_size + 3;	// 1 byte prefix len, 2 byte num suffix
						if (space < min_space) {
							request.setDetails(header_size, packet_key_details);
							requests.add(request);
							continue outer ;
						}
						List<byte[]> s = new ArrayList<byte[]>();
						packet_key_details.add(new Object[]{ current_prefix, s });
						int	prefix_size = current_prefix.length;
						int	suffix_size = header_size - prefix_size;
						space -= (3 + prefix_size);
						while (space >= suffix_size && current_suffixes.hasNext()) {
							s.add( current_suffixes.next());
						}
					} else {
						current_prefix = null;
					}
				}
				if (!it.hasNext()) {
					request.setDetails(header_size, packet_key_details);
					requests.add(request);
				}
			}
			final Object[] replies = new Object[ requests.size() ];
			for (int i=0;i<requests.size();i++) {
				DHTUDPPacketRequestQueryStorage request = requests.get(i);
				final int f_i = i;
				stats.queryStoreSent(request);
				requestSendRequestProcessor(contact, request);
				packetHandler.sendAndReceive(
					request,
					contact.getTransportAddress(),
					new DHTUDPPacketReceiver() {
						public void packetReceived(
							DHTUDPPacketReply	packet,
							InetSocketAddress	from_address,
							long				elapsed_time) {
							
							try {
								if (packet.getConnectionId() != connection_id) {
									throw (new Exception("connection id mismatch"));
								}
								contact.setInstanceIDAndVersion( packet.getTargetInstanceID(), packet.getProtocolVersion());
								requestSendReplyProcessor(contact, handler, packet, elapsed_time);
								DHTUDPPacketReplyQueryStorage	reply = (DHTUDPPacketReplyQueryStorage)packet;
									// copy out the random id in preparation for a possible subsequent
									// store operation
								contact.setRandomID( reply.getRandomID());
								stats.queryStoreOK();
								synchronized(replies) {
									replies[f_i] = reply;
									checkComplete();
								}
							} catch (DHTUDPPacketHandlerException e) {
								error(e);
							} catch (Throwable e) {
								Debug.printStackTrace(e);
								error(new DHTUDPPacketHandlerException("queryStore failed", e));
							}
						}
						public void error(
							DHTUDPPacketHandlerException	e) {
							stats.queryStoreFailed();
							synchronized(replies) {
								replies[f_i] = e;
								checkComplete();
							}
						}
						protected void checkComplete() {
							DHTUDPPacketHandlerException last_error = null;
							for (int i=0;i<replies.length;i++) {
								Object o = replies[i];
								if (o == null) {
									return;
								}
								if (o instanceof DHTUDPPacketHandlerException) {
									last_error = (DHTUDPPacketHandlerException)o;
								}
							}
							if (last_error != null) {
								handler.failed(contact, last_error);
							} else {
								if (replies.length == 1) {
									handler.queryStoreReply( contact, ((DHTUDPPacketReplyQueryStorage)replies[0]).getResponse());
								} else {
									List<byte[]> response = new ArrayList<byte[]>();
									for (int i=0;i<replies.length;i++) {
										response.addAll(((DHTUDPPacketReplyQueryStorage)replies[0]).getResponse());
									}
									handler.queryStoreReply(contact, response);
								}
							}
						}
					},
					requestTimeout, PRUDPPacketHandler.PRIORITY_MEDIUM);
			}
		} catch (Throwable e) {
			stats.queryStoreFailed();
			handler.failed(contact, e);
		}
	}

	// FIND NODE
	public void	sendFindNode(
		final DHTTransportUDPContactImpl	contact,
		final DHTTransportReplyHandler		handler,
		byte[]								nid) {
		
		/*
		if (SingleCounter2.getInstance().getAndIncreaseCount() == 1) {
			Log.d(TAG, "sendFindNode() is called..."); 
			Log.d(TAG, "nid = " + Util.toHexString(nid));
			new Throwable().printStackTrace();
		}
		//*/
		
		try {
			checkAddress(contact);

			final long connectionId = getConnectionID();

			final DHTUDPPacketRequestFindNode request = new DHTUDPPacketRequestFindNode(this, connectionId, localContact, contact);

			stats.findNodeSent(request);

			request.setID(nid);
			request.setNodeStatus(getNodeStatus());
			request.setEstimatedDHTSize(requestHandler.getTransportEstimatedDHTSize());
			requestSendRequestProcessor(contact, request);

			//InetSocketAddress addr = contact.getTransportAddress();
			//Log.d(TAG, "addr = " + addr);
			
			packetHandler.sendAndReceive(
				request,
				contact.getTransportAddress(),
				new DHTUDPPacketReceiver() {
					public void packetReceived(
						DHTUDPPacketReply	packet,
						InetSocketAddress	fromAddress,
						long				elapsedTime) {
						
						try {
							if (packet.getConnectionId() != connectionId) {
								throw (new Exception("connection id mismatch"));
							}

							contact.setInstanceIDAndVersion( packet.getTargetInstanceID(), packet.getProtocolVersion());
							requestSendReplyProcessor(contact, handler, packet, elapsedTime);
							DHTUDPPacketReplyFindNode reply = (DHTUDPPacketReplyFindNode) packet;
							
							// copy out the random id in preparation for a possible subsequent
							// store operation
							
							contact.setRandomID(reply.getRandomID());
							updateContactStatus(contact, reply.getNodeStatus(), false);
							requestHandler.setTransportEstimatedDHTSize(reply.getEstimatedDHTSize());
							stats.findNodeOK();
							
							DHTTransportContact[] contacts = reply.getContacts();
							
							// scavenge any contacts here to help bootstrap process
							// when ip wrong and no import history
							
							try {
								thisMon.enter();

								for (int i=0; contactHistory.size() < CONTACT_HISTORY_MAX && i<contacts.length;i++) {
									DHTTransportUDPContact c = (DHTTransportUDPContact)contacts[i];
									contactHistory.put(c.getTransportAddress(), c);
								}
							} finally {
								thisMon.exit();
							}
							
							handler.findNodeReply(contact, contacts);
							
						} catch (DHTUDPPacketHandlerException e) {
							error(e);
						} catch (Throwable e) {
							Debug.printStackTrace(e);
							error(new DHTUDPPacketHandlerException("findNode failed", e));
						}
					}

					public void error(DHTUDPPacketHandlerException e) {
						stats.findNodeFailed();
						handler.failed(contact, e);
					}
				},
				requestTimeout, 
				PRUDPPacketHandler.PRIORITY_MEDIUM);

		} catch (Throwable e) {
			stats.findNodeFailed();
			handler.failed(contact, e);
		}
	}

	// FIND VALUE
	public void	sendFindValue(
		final DHTTransportUDPContactImpl	contact,
		final DHTTransportReplyHandler		handler,
		byte[]								key,
		int									max_values,
		short								flags) {
		
		/*if (SingleCounter3.getInstance().getAndIncreaseCount() == 1) {
			Log.d(TAG, "sendFindValue() is called..."); 
			new Throwable().printStackTrace();
		}*/
		
		try {
			checkAddress(contact);
			final long	connection_id = getConnectionID();
			final DHTUDPPacketRequestFindValue	request =
				new DHTUDPPacketRequestFindValue(this, connection_id, localContact, contact);
			stats.findValueSent(request);
			request.setID(key);
			request.setMaximumValues(max_values);
			request.setFlags((byte)flags);
			requestSendRequestProcessor(contact, request);
			
			packetHandler.sendAndReceive(
				request,
				contact.getTransportAddress(),
				new DHTUDPPacketReceiver() {
					
					public void packetReceived(
						DHTUDPPacketReply	packet,
						InetSocketAddress	fromAddress,
						long				elapsedTime) {
						
						try {
							if (packet.getConnectionId() != connection_id) {
								throw (new Exception("connection id mismatch"));
							}
							contact.setInstanceIDAndVersion(packet.getTargetInstanceID(), packet.getProtocolVersion());
							requestSendReplyProcessor(contact, handler, packet, elapsedTime);
							DHTUDPPacketReplyFindValue	reply = (DHTUDPPacketReplyFindValue)packet;
							stats.findValueOK();
							DHTTransportValue[]	res = reply.getValues();
							if (res != null) {
								boolean	continuation = reply.hasContinuation();
								handler.findValueReply(contact, res, reply.getDiversificationType(), continuation);
							} else {
								handler.findValueReply(contact, reply.getContacts());
							}
						} catch (DHTUDPPacketHandlerException e) {
							error(e);
						} catch (Throwable e) {
							Debug.printStackTrace(e);
							error(new DHTUDPPacketHandlerException("findValue failed", e));
						}
					}
					
					public void error(DHTUDPPacketHandlerException e) {
						stats.findValueFailed();
						handler.failed(contact, e);
					}
					
				},
				requestTimeout, PRUDPPacketHandler.PRIORITY_HIGH);
		} catch (Throwable e) {
			if (!(e instanceof DHTUDPPacketHandlerException)) {
				stats.findValueFailed();
				handler.failed(contact, e);
			}
		}
	}

	protected DHTTransportFullStats getFullStats(DHTTransportUDPContactImpl contact) {
		if (contact == localContact) {
			return (requestHandler.statsRequest(contact));
		}

		final DHTTransportFullStats[] res = { null };

		final AESemaphore sem = new AESemaphore("DHTTransportUDP:getFullStats");

		sendStats(contact, new DHTTransportReplyHandlerAdapter() {
			public void statsReply(DHTTransportContact _contact, DHTTransportFullStats _stats) {
				res[0] = _stats;
				sem.release();
			}

			public void failed(DHTTransportContact _contact, Throwable _error) {
				sem.release();
			}

		});

		sem.reserve();

		return (res[0]);
	}

	public void registerTransferHandler(
		byte[]						handler_key,
		DHTTransportTransferHandler	handler) {
		xferHandler.registerTransferHandler(handler_key, handler);
	}

	public void registerTransferHandler(
		byte[]						handler_key,
		DHTTransportTransferHandler	handler,
		Map<String,Object>			options) {
		xferHandler.registerTransferHandler(handler_key, handler, options);
	}

	public void unregisterTransferHandler(
		byte[]						handler_key,
		DHTTransportTransferHandler	handler) {
		xferHandler.unregisterTransferHandler(handler_key, handler);
	}

	public byte[] readTransfer(
		DHTTransportProgressListener	listener,
		DHTTransportContact				target,
		byte[]							handlerKey,
		byte[]							key,
		long							timeout )
		throws DHTTransportException {
		InetAddress ia = target.getAddress().getAddress();
		if (	(ia instanceof Inet4Address && v6) ||
				(ia instanceof Inet6Address && !v6)) {
			throw (new DHTTransportException("Incompatible address"));
		}
		return (xferHandler.readTransfer(listener, target, handlerKey, key, timeout));
	}

	public void writeTransfer(
		DHTTransportProgressListener	listener,
		DHTTransportContact				target,
		byte[]							handlerKey,
		byte[]							key,
		byte[]							data,
		long							timeout )
		throws DHTTransportException
	{
		InetAddress ia = target.getAddress().getAddress();

		if (	(ia instanceof Inet4Address && v6) ||
				(ia instanceof Inet6Address && !v6)) {

			throw (new DHTTransportException("Incompatible address"));
		}

		xferHandler.writeTransfer(listener, target, handlerKey, key, data, timeout);
	}

	public byte[] writeReadTransfer(
		DHTTransportProgressListener	listener,
		DHTTransportContact				target,
		byte[]							handlerKey,
		byte[]							data,
		long							timeout )

		throws DHTTransportException
	{
		InetAddress ia = target.getAddress().getAddress();

		if (	(ia instanceof Inet4Address && v6) ||
				(ia instanceof Inet6Address && !v6)) {

			throw (new DHTTransportException("Incompatible address"));
		}

		return (xferHandler.writeReadTransfer( listener, target, handlerKey, data, timeout));
	}

	protected void dataRequest(
		final DHTTransportUDPContactImpl	originator,
		final DHTUDPPacketData				req) {
		
		stats.dataReceived();

		xferHandler.receivePacket(
			originator,
			new DHTTransferHandler.Packet(
				req.getConnectionId(),
				req.getPacketType(),
				req.getTransferKey(),
				req.getRequestKey(),
				req.getData(),
				req.getStartPosition(),
				req.getLength(),
				req.getTotalLength()));
	}

	public void process(
		DHTUDPPacketRequest	request,
		boolean				alien) {
		process(packetHandler, request, alien);
	}

	public void	process(
		DHTUDPPacketHandlerStub		packetHandlerStub,
		DHTUDPPacketRequest			request,
		boolean						alien
	) {
		
		if (requestHandler == null) {
			logger.log("Ignoring packet as not yet ready to process");
			return;
		}
		
		//System.out.println("process() is called...");
		//new Throwable().printStackTrace();
		
		try {
			stats.incomingRequestReceived(request, alien);
			InetSocketAddress transportAddress = request.getAddress();
			DHTTransportUDPContactImpl originatingContact =
				new DHTTransportUDPContactImpl(
						false,
						this,
						transportAddress,
						request.getOriginatorAddress(),
						request.getOriginatorVersion(),
						request.getOriginatorInstanceID(),
						request.getClockSkew(),
						request.getGenericFlags());
			try {
				checkAddress(originatingContact);
			} catch (DHTUDPPacketHandlerException e) {
				return;
			}
			requestReceiveRequestProcessor(originatingContact, request);
			boolean	bad_originator = !originatingContact.addressMatchesID();
			
			// bootstrap node returns details regardless of whether the originator ID matches
			// as the details will help the sender discover their correct ID (hopefully)
			if (bad_originator && !bootstrapNode) {
				String	contact_string = originatingContact.getString();
				if (recentReports.get(contact_string) == null) {
					recentReports.put(contact_string, "");
					logger.log("Node " + contact_string + " has incorrect ID, reporting it to them");
				}
				DHTUDPPacketReplyError reply =
					new DHTUDPPacketReplyError(
							this,
							request,
							localContact,
							originatingContact);
				reply.setErrorType(DHTUDPPacketReplyError.ET_ORIGINATOR_ADDRESS_WRONG);
				reply.setOriginatingAddress(originatingContact.getTransportAddress());
				requestReceiveReplyProcessor(originatingContact, reply);
				packetHandlerStub.send(reply, request.getAddress());
			} else {
				if (bad_originator) {
					// we need to patch the originator up otherwise we'll be populating our
					// routing table with crap
					originatingContact =
						new DHTTransportUDPContactImpl(
								false,
								this,
								transportAddress,
								transportAddress, 		// set originator address to transport
								request.getOriginatorVersion(),
								request.getOriginatorInstanceID(),
								request.getClockSkew(),
								request.getGenericFlags());
				} else {
					contactAlive(originatingContact);
				}
				
				if (request instanceof DHTUDPPacketRequestPing) {
					if (!bootstrapNode) {
						requestHandler.pingRequest(originatingContact);
						DHTUDPPacketRequestPing ping = (DHTUDPPacketRequestPing)request;
						DHTUDPPacketReplyPing reply =
							new DHTUDPPacketReplyPing(
									this,
									ping,
									localContact,
									originatingContact);
						sendAltContacts(ping, reply);
						requestReceiveReplyProcessor(originatingContact, reply);
						packetHandlerStub.send(reply, request.getAddress());
					}
				} else if (request instanceof DHTUDPPacketRequestKeyBlock) {
					if (!bootstrapNode) {
						DHTUDPPacketRequestKeyBlock	kb_request = (DHTUDPPacketRequestKeyBlock)request;
						originatingContact.setRandomID( kb_request.getRandomID());
						requestHandler.keyBlockRequest(
								originatingContact,
								kb_request.getKeyBlockRequest(),
								kb_request.getKeyBlockSignature());
						DHTUDPPacketReplyKeyBlock reply =
							new DHTUDPPacketReplyKeyBlock(
									this,
									kb_request,
									localContact,
									originatingContact);
						requestReceiveReplyProcessor(originatingContact, reply);
						packetHandlerStub.send( reply, request.getAddress());
					}
				} else if (request instanceof DHTUDPPacketRequestStats) {
					DHTUDPPacketRequestStats statsRequest = (DHTUDPPacketRequestStats)request;
					DHTUDPPacketReplyStats	reply =
						new DHTUDPPacketReplyStats(
								this,
								statsRequest,
								localContact,
								originatingContact);
					int	type = statsRequest.getStatsType();
					if (type == DHTUDPPacketRequestStats.STATS_TYPE_ORIGINAL) {
						DHTTransportFullStats	full_stats = requestHandler.statsRequest(originatingContact);
						reply.setOriginalStats(full_stats);
					} else if (type == DHTUDPPacketRequestStats.STATS_TYPE_NP_VER2) {
						DHTNetworkPositionProvider prov = DHTNetworkPositionManager.getProvider(DHTNetworkPosition.POSITION_TYPE_VIVALDI_V2);
						byte[]	data = new byte[0];
						if (prov != null) {
							ByteArrayOutputStream	baos = new ByteArrayOutputStream();
							DataOutputStream	dos = new DataOutputStream(baos);
							prov.serialiseStats(dos);
							dos.flush();
							data = baos.toByteArray();
						}
						reply.setNewStats(data, DHTNetworkPosition.POSITION_TYPE_VIVALDI_V2);
					} else {
						throw (new IOException("Uknown stats type '" + type + "'"));
					}
					requestReceiveReplyProcessor(originatingContact, reply);
					packetHandlerStub.send(reply, request.getAddress());
				} else if (request instanceof DHTUDPPacketRequestStore) {
					if (!bootstrapNode) {
						DHTUDPPacketRequestStore	store_request = (DHTUDPPacketRequestStore)request;
						originatingContact.setRandomID( store_request.getRandomID());
						DHTTransportStoreReply	res =
							requestHandler.storeRequest(
								originatingContact,
								store_request.getKeys(),
								store_request.getValueSets());
						if (res.blocked()) {
							if (originatingContact.getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_BLOCK_KEYS) {
								DHTUDPPacketReplyError	reply =
									new DHTUDPPacketReplyError(
										this,
										request,
										localContact,
										originatingContact);
								reply.setErrorType(DHTUDPPacketReplyError.ET_KEY_BLOCKED);
								reply.setKeyBlockDetails(res.getBlockRequest(), res.getBlockSignature());
								requestReceiveReplyProcessor(originatingContact, reply);
								packetHandlerStub.send( reply, request.getAddress());
							} else {
								DHTUDPPacketReplyStore	reply =
									new DHTUDPPacketReplyStore(
											this,
											store_request,
											localContact,
											originatingContact);
								reply.setDiversificationTypes(new byte[store_request.getKeys().length]);
								requestReceiveReplyProcessor(originatingContact, reply);
								packetHandlerStub.send( reply, request.getAddress());
							}
						} else {
							DHTUDPPacketReplyStore	reply =
								new DHTUDPPacketReplyStore(
										this,
										store_request,
										localContact,
										originatingContact);
							reply.setDiversificationTypes( res.getDiversificationTypes());
							requestReceiveReplyProcessor(originatingContact, reply);
							packetHandlerStub.send( reply, request.getAddress());
						}
					}
				} else if (request instanceof DHTUDPPacketRequestQueryStorage) {
					DHTUDPPacketRequestQueryStorage	query_request = (DHTUDPPacketRequestQueryStorage)request;
					DHTTransportQueryStoreReply	res =
						requestHandler.queryStoreRequest(
									originatingContact,
									query_request.getHeaderLength(),
									query_request.getKeys());
					DHTUDPPacketReplyQueryStorage	reply =
						new DHTUDPPacketReplyQueryStorage(
								this,
								query_request,
								localContact,
								originatingContact);
					reply.setRandomID( originatingContact.getRandomID());
					reply.setResponse( res.getHeaderSize(), res.getEntries());
					requestReceiveReplyProcessor(originatingContact, reply);
					packetHandlerStub.send( reply, request.getAddress());
				} else if (request instanceof DHTUDPPacketRequestFindNode) {
					DHTUDPPacketRequestFindNode	findRequest = (DHTUDPPacketRequestFindNode)request;
					boolean	acceptable;
					// as a bootstrap node we only accept find-node requests for the originator's ID
					if (bootstrapNode) {
						// log(originating_contact);
						// let bad originators through to aid bootstrapping with bad IP
						acceptable = bad_originator || Arrays.equals(findRequest.getID(), originatingContact.getID());
					} else {
						acceptable	= true;
					}
					
					if (acceptable) {
						if (findRequest.getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_MORE_NODE_STATUS) {
							updateContactStatus(originatingContact, findRequest.getNodeStatus(), true);
							requestHandler.setTransportEstimatedDHTSize(findRequest.getEstimatedDHTSize());
						}
						DHTTransportContact[] res =
							requestHandler.findNodeRequest(
										originatingContact,
										findRequest.getID());
						DHTUDPPacketReplyFindNode	reply =
							new DHTUDPPacketReplyFindNode(
									this,
									findRequest,
									localContact,
									originatingContact);
						reply.setRandomID(originatingContact.getRandomID());
						reply.setNodeStatus(getNodeStatus());
						reply.setEstimatedDHTSize( requestHandler.getTransportEstimatedDHTSize());
						reply.setContacts(res);
						requestReceiveReplyProcessor(originatingContact, reply);
						packetHandlerStub.send(reply, request.getAddress());
					}
				} else if (request instanceof DHTUDPPacketRequestFindValue) {
					if (!bootstrapNode) {
						DHTUDPPacketRequestFindValue	findRequest = (DHTUDPPacketRequestFindValue)request;
						DHTTransportFindValueReply res =
							requestHandler.findValueRequest(
										originatingContact,
										findRequest.getID(),
										findRequest.getMaximumValues(),
										findRequest.getFlags());
						if (res.blocked()) {
							if (originatingContact.getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_BLOCK_KEYS) {
								DHTUDPPacketReplyError	reply =
									new DHTUDPPacketReplyError(
										this,
										request,
										localContact,
										originatingContact);
								reply.setErrorType(DHTUDPPacketReplyError.ET_KEY_BLOCKED);
								reply.setKeyBlockDetails(res.getBlockedKey(), res.getBlockedSignature());
								requestReceiveReplyProcessor(originatingContact, reply);
								packetHandlerStub.send( reply, request.getAddress());
							} else {
								DHTUDPPacketReplyFindValue	reply =
									new DHTUDPPacketReplyFindValue(
										this,
										findRequest,
										localContact,
										originatingContact);
								reply.setValues(new DHTTransportValue[0], DHT.DT_NONE, false);
								requestReceiveReplyProcessor(originatingContact, reply);
								packetHandlerStub.send( reply, request.getAddress());
							}
						} else {
							DHTUDPPacketReplyFindValue reply =
								new DHTUDPPacketReplyFindValue(
									this,
									findRequest,
									localContact,
									originatingContact);
							
							if (res.hit()) {
								DHTTransportValue[]	res_values = res.getValues();
								int		max_size = DHTUDPPacketHelper.PACKET_MAX_BYTES - DHTUDPPacketReplyFindValue.DHT_FIND_VALUE_HEADER_SIZE;
								List	values 		= new ArrayList();
								int		values_size	= 0;
								int	pos = 0;
								while (pos < res_values.length) {
									DHTTransportValue	v = res_values[pos];
									int	v_len = v.getValue().length + DHTUDPPacketReplyFindValue.DHT_FIND_VALUE_TV_HEADER_SIZE;
									if (	values_size > 0 && // if value too big, cram it in anyway
											values_size + v_len > max_size) {
										// won't fit, send what we've got
										DHTTransportValue[]	x = new DHTTransportValue[values.size()];
										values.toArray(x);
										reply.setValues(x, res.getDiversificationType(), true);	// continuation = true
										packetHandlerStub.send(reply, request.getAddress());
										values_size	= 0;
										values		= new ArrayList();
									} else {
										values.add(v);
										values_size	+= v_len;
										pos++;
									}
								}
								
								// send the remaining (possible zero length) non-continuation values
								DHTTransportValue[]	x = new DHTTransportValue[values.size()];
								values.toArray(x);
								reply.setValues(x, res.getDiversificationType(), false);
								requestReceiveReplyProcessor(originatingContact, reply);
								packetHandlerStub.send(reply, request.getAddress());
							} else {
								reply.setContacts(res.getContacts());
								requestReceiveReplyProcessor(originatingContact, reply);
								packetHandlerStub.send(reply, request.getAddress());
							}
						}
					}
				} else if (request instanceof DHTUDPPacketData) {
					if (!bootstrapNode) {
						dataRequest(originatingContact, (DHTUDPPacketData)request);
					}
				} else {
					Debug.out("Unexpected packet:" + request.toString());
				}
			}
		} catch (DHTUDPPacketHandlerException e) {
			// not interesting, send packet fail or something
		} catch (Throwable e) {
			Debug.printStackTrace(e);
		}
	}

	// the _state networks are populated via ping requests to other peers
	private final Map<Integer, DHTTransportAlternativeNetworkImpl>	altNetStates 		= new HashMap<Integer, DHTTransportAlternativeNetworkImpl>();
	
	// the _providers represent a local source of contacts that are used as a primary
	// source of contacts for replying to other peers ping requests
	private volatile Map<Integer, DHTTransportAlternativeNetwork>	altNetProviders	= new HashMap<Integer, DHTTransportAlternativeNetwork>();
	private final Object											altNetProvidersLock = new Object();
	{
		for (Integer net: DHTTransportAlternativeNetwork.AT_ALL) {
			altNetStates.put(net, new DHTTransportAlternativeNetworkImpl( net));
		}
	}

	public DHTTransportAlternativeNetwork getAlternativeNetwork(int network_type) {
		return (altNetStates.get(network_type));
	}

	public void registerAlternativeNetwork(
		DHTTransportAlternativeNetwork		network) {
		synchronized(altNetProvidersLock) {
			Map<Integer, DHTTransportAlternativeNetwork> new_providers = new HashMap<Integer, DHTTransportAlternativeNetwork>(altNetProviders);
			new_providers.put(network.getNetworkType(), network);
			altNetProviders = new_providers;
		}
	}

	public void unregisterAlternativeNetwork(
		DHTTransportAlternativeNetwork		network) {
		synchronized(altNetProvidersLock) {
			Map<Integer, DHTTransportAlternativeNetwork> new_providers = new HashMap<Integer, DHTTransportAlternativeNetwork>(altNetProviders);
			Iterator< Map.Entry<Integer, DHTTransportAlternativeNetwork>> it = new_providers.entrySet().iterator();
			while (it.hasNext()) {
				if (it.next().getValue() == network) {
					it.remove();
				}
			}
			altNetProviders = new_providers;
		}
	}

	private void checkAltContacts() {
		int	total_required = 0;
		for ( DHTTransportAlternativeNetworkImpl net: altNetStates.values()) {
			total_required += net.getRequiredContactCount();
		}
		if (total_required > 0) {
			List<DHTTransportContact> targets = new ArrayList<DHTTransportContact>(ROUTABLE_CONTACT_HISTORY_MAX);
			try {
				thisMon.enter();
				for ( DHTTransportContact contact: routableContactHistory.values()) {
					if (contact.getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_ALT_CONTACTS) {
						targets.add(contact);
					}
				}
			} finally {
				thisMon.exit();
			}
			if (targets.size() > 0) {
				targets.get(RandomUtils.nextInt( targets.size())).sendPing(
					new DHTTransportReplyHandlerAdapter() {
						public void pingReply(DHTTransportContact _contact) {}
						
						public void failed(
							DHTTransportContact 	_contact,
							Throwable				_error) {
						}
					});
			}
		}
	}

	private void sendAltContacts(
		DHTUDPPacketRequestPing		request,
		DHTUDPPacketReplyPing		reply) {
		if (request.getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_ALT_CONTACTS) {
			int[]	alt_nets 	= request.getAltNetworks();
			int[] 	counts 		= request.getAltNetworkCounts();
			if (alt_nets.length > 0) {
				List<DHTTransportAlternativeContact>	alt_contacts = new ArrayList<DHTTransportAlternativeContact>();
				Map<Integer, DHTTransportAlternativeNetwork> providers = altNetProviders;
				for ( int i=0; i<alt_nets.length;i++) {
					int	count = counts[i];
					if (count == 0) {
						continue;
					}
					int	net = alt_nets[i];
					DHTTransportAlternativeNetworkImpl local = altNetStates.get(net);
					if (local == null) {
						continue;
					}
					int wanted = local.getRequiredContactCount();
					if (wanted > 0) {
						DHTTransportAlternativeNetwork provider = providers.get(net);
						if (provider != null) {
							local.addContactsForSend(provider.getContacts( wanted));
						}
					}
						// need to limit response for large serialisations
					if (net == DHTTransportAlternativeNetwork.AT_I2P) {
						count = Math.min(2, count);
					}
					alt_contacts.addAll(local.getContacts( count, true));
				}
				if (alt_contacts.size() > 0) {
					reply.setAltContacts(alt_contacts);
				}
			}
		}
	}

	private void requestAltContacts(DHTUDPPacketRequestPing request) {
		if (request.getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_ALT_CONTACTS) {
				// see if we could do with any more alt contacts
			List<int[]> wanted = null;
			for ( DHTTransportAlternativeNetworkImpl net: altNetStates.values()) {
				int req = net.getRequiredContactCount();
				if (req > 0) {
					int net_type = net.getNetworkType();
					if (net_type == DHTTransportAlternativeNetwork.AT_I2P) {
						req = Math.min(2, req);
					}
					if (wanted == null) {
						wanted = new ArrayList<int[]>( altNetStates.size());
					}
					wanted.add(new int[]{ net_type, req });
				}
			}
			if (wanted != null) {
				int[] networks 	= new int[wanted.size()];
				int[] counts	= new int[networks.length];
				for (int i=0;i<networks.length;i++) {
					int[] 	entry = wanted.get(i);
					networks[i] = entry[0];
					counts[i]	= entry[1];
				}
				// doesn't matter how many we request in total, the reply will be
				// limited to whatever the max is
				request.setAltContactRequest(networks, counts);
			}
		}
	}

	private void receiveAltContacts(DHTUDPPacketReplyPing reply) {
		if (reply.getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_ALT_CONTACTS) {
			for ( DHTTransportAlternativeContact contact: reply.getAltContacts()) {
				DHTTransportAlternativeNetworkImpl net = altNetStates.get( contact.getNetworkType());
				if (net != null) {
					net.addContactFromReply(contact);
				}
			}
		}
	}

	protected void requestReceiveRequestProcessor(
		DHTTransportUDPContactImpl	contact,
		DHTUDPPacketRequest			request) {
		// called when request received
	}

	protected void requestReceiveReplyProcessor(
		DHTTransportUDPContactImpl	contact,
		DHTUDPPacketReply			reply) {
		
		// called before sending reply to request
		int	action = reply.getAction();
		
		if (	action == DHTUDPPacketHelper.ACT_REPLY_PING ||
				action == DHTUDPPacketHelper.ACT_REPLY_FIND_NODE ||
				action == DHTUDPPacketHelper.ACT_REPLY_FIND_VALUE) {
			reply.setNetworkPositions(localContact.getNetworkPositions());
		}
	}

	protected void requestSendRequestProcessor(
		DHTTransportUDPContactImpl	contact,
		DHTUDPPacketRequest 		request) {
		// called before sending request
	}

	/**
	 * Returns false if this isn't an error reply, true if it is and a retry can be
	 * performed, throws an exception otherwise
	 * @param reply
	 * @return
	 */
	protected void requestSendReplyProcessor(
		DHTTransportUDPContactImpl	remoteContact,
		DHTTransportReplyHandler	handler,
		DHTUDPPacketReply			reply,
		long						elapsedTime)
		throws DHTUDPPacketHandlerException
	{
		// called after receiving reply to request
		//System.out.println("request:" + contact.getAddress() + " = " + elapsedTime);
		DHTNetworkPosition[] remoteNps = reply.getNetworkPositions();
		if (remoteNps != null) {
			long	proc_time = reply.getProcessingTime();
			if (proc_time > 0) {
				//System.out.println(elapsedTime + "/" + procTime);
				long rtt = elapsedTime - proc_time;
				if (rtt < 0) {
					rtt = 0;
				}
				// save current position of target
				remoteContact.setNetworkPositions(remoteNps);
				// update local positions
				DHTNetworkPositionManager.update(localContact.getNetworkPositions(), remoteContact.getID(), remoteNps, (float)rtt);
			}
		}
		remoteContact.setGenericFlags(reply.getGenericFlags());
		if (reply.getAction() == DHTUDPPacketHelper.ACT_REPLY_ERROR) {
			DHTUDPPacketReplyError	error = (DHTUDPPacketReplyError)reply;
			switch(error.getErrorType()) {
				case DHTUDPPacketReplyError.ET_ORIGINATOR_ADDRESS_WRONG: {
					try {
						externalAddressChange(remoteContact, error.getOriginatingAddress(), false);
					} catch (DHTTransportException e) {
						Debug.printStackTrace(e);
					}
					throw (new DHTUDPPacketHandlerException("address changed notification"));
				}
				case DHTUDPPacketReplyError.ET_KEY_BLOCKED: {
					handler.keyBlockRequest( remoteContact, error.getKeyBlockRequest(), error.getKeyBlockSignature());
					contactAlive(remoteContact);
					throw (new DHTUDPPacketHandlerException("key blocked"));
				}
			}
			throw (new DHTUDPPacketHandlerException("unknown error type " + error.getErrorType()));
		} else {
			contactAlive(remoteContact);
		}
	}

	protected long getConnectionID() {
		// unfortunately, to reuse the UDP port with the tracker protocol we
		// have to distinguish our connection ids by setting the MSB. This allows
		// the decode to work as there is no common header format for the request
		// and reply packets

		// note that tracker usage of UDP via this handler is only for outbound
		// messages, hence for that use a request will never be received by the
		// handler
		return (0x8000000000000000L | random.nextLong());
	}

	public boolean supportsStorage() {
		return (!bootstrapNode);
	}

	public void addListener(DHTTransportListener l) {
		listeners.add(l);
		if (externalAddress != null) {
			l.currentAddress(externalAddress);
		}
	}

	public void removeListener(DHTTransportListener l) {
		listeners.remove(l);
	}

	/*
	private PrintWriter	contact_log;
	private int			contact_log_entries;
	private SimpleDateFormat	contact_log_format = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
	{
		contact_log_format.setTimeZone(TimeZone.getTimeZone("UTC"));
	}

	protected void log(
		DHTTransportUDPContactImpl		contact) {
		if (network == DHT.NW_MAIN) {
			synchronized(this) {
				try {
					if (contact_log == null) {
						contact_log = new PrintWriter(new FileWriter(new File( SystemProperties.getUserPath(), "contact_log")));
					}
					contact_log_entries++;
					InetSocketAddress address = contact.getAddress();
					contact_log.println( contact_log_format.format(new Date()) + ", " + address.getAddress().getHostAddress() + ", " + address.getPort());
					if (contact_log_entries % 1000 == 0) {
						System.out.println("contact-log: " + contact_log_entries);
						contact_log.flush();
					}
				} catch (Throwable e) {
					Debug.out(e);
				}
			}
		}
	}
	*/
}
