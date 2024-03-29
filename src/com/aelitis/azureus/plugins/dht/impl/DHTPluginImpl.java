/*
 * Created on 24-Jan-2005
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

package com.aelitis.azureus.plugins.dht.impl;


import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.gudy.azureus2.core3.util.AENetworkClassifier;
import org.gudy.azureus2.core3.util.Constants;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.FileUtil;
import org.gudy.azureus2.core3.util.HashWrapper;
import org.gudy.azureus2.core3.util.SystemTime;
import org.gudy.azureus2.plugins.PluginConfig;
import org.gudy.azureus2.plugins.PluginEvent;
import org.gudy.azureus2.plugins.PluginInterface;
import org.gudy.azureus2.plugins.download.Download;
import org.gudy.azureus2.plugins.logging.LoggerChannel;
import org.gudy.azureus2.plugins.peers.Peer;
import org.gudy.azureus2.plugins.peers.PeerManager;
import org.gudy.azureus2.plugins.ui.config.ActionParameter;
import org.gudy.azureus2.plugins.ui.config.BooleanParameter;
import org.gudy.azureus2.plugins.utils.UTTimerEvent;
import org.gudy.azureus2.plugins.utils.UTTimerEventPerformer;

import com.aelitis.azureus.core.dht.DHT;
import com.aelitis.azureus.core.dht.DHTFactory;
import com.aelitis.azureus.core.dht.DHTLogger;
import com.aelitis.azureus.core.dht.DHTOperationListener;
import com.aelitis.azureus.core.dht.DHTStorageKeyStats;
import com.aelitis.azureus.core.dht.control.DHTControlStats;
import com.aelitis.azureus.core.dht.db.DHTDB;
import com.aelitis.azureus.core.dht.db.DHTDBStats;
import com.aelitis.azureus.core.dht.db.DHTDBValue;
import com.aelitis.azureus.core.dht.nat.DHTNATPuncher;
import com.aelitis.azureus.core.dht.nat.DHTNATPuncherAdapter;
import com.aelitis.azureus.core.dht.router.DHTRouterStats;
import com.aelitis.azureus.core.dht.transport.DHTTransportContact;
import com.aelitis.azureus.core.dht.transport.DHTTransportException;
import com.aelitis.azureus.core.dht.transport.DHTTransportFactory;
import com.aelitis.azureus.core.dht.transport.DHTTransportListener;
import com.aelitis.azureus.core.dht.transport.DHTTransportProgressListener;
import com.aelitis.azureus.core.dht.transport.DHTTransportStats;
import com.aelitis.azureus.core.dht.transport.DHTTransportTransferHandler;
import com.aelitis.azureus.core.dht.transport.DHTTransportValue;
import com.aelitis.azureus.core.dht.transport.udp.DHTTransportUDP;
import com.aelitis.azureus.core.util.DNSUtils;
import com.aelitis.azureus.core.versioncheck.VersionCheckClient;
import com.aelitis.azureus.plugins.dht.DHTPlugin;
import com.aelitis.azureus.plugins.dht.DHTPluginContact;
import com.aelitis.azureus.plugins.dht.DHTPluginInterface.DHTInterface;

import hello.util.Log;

import com.aelitis.azureus.plugins.dht.DHTPluginKeyStats;
import com.aelitis.azureus.plugins.dht.DHTPluginOperationListener;
import com.aelitis.azureus.plugins.dht.DHTPluginProgressListener;
import com.aelitis.azureus.plugins.dht.DHTPluginTransferHandler;
import com.aelitis.azureus.plugins.dht.DHTPluginValue;


/**
 * @author parg
 *
 */

public class DHTPluginImpl implements DHTInterface {

	private static String TAG = DHTPluginImpl.class.getSimpleName();
	
	private static final String	SEED_ADDRESS_V4	= Constants.DHT_SEED_ADDRESS_V4;
	private static final String	SEED_ADDRESS_V6	= Constants.DHT_SEED_ADDRESS_V6;
	private static final int	SEED_PORT		= 6881;

	private static final long	MIN_ROOT_SEED_IMPORT_PERIOD	= 8*60*60*1000;


	private PluginInterface		pluginInterface;

	private int					status;
	private String				statusText;

	private ActionParameter		reseedParam;
	private BooleanParameter	warn_user_param;

	private DHT					dht;
	private int					port;
	private byte				protocolVersion;
	private int					network;
	private boolean				v6;
	private DHTTransportUDP		transport;

	private DHTPluginStorageManager storageManager;

	private long				lastRootSeedImportTime;

	private LoggerChannel		log;
	private DHTLogger			dhtLog;

	private int					statsTicks;

	public DHTPluginImpl(
		PluginInterface			_pluginInterface,
		DHTNATPuncherAdapter	_natAdapter,
		DHTPluginImplAdapter	_adapter,
		byte					_protocolVersion,
		int						_network,
		boolean					_v6,
		String					_ip,
		int						_port,
		ActionParameter			_reseed,
		BooleanParameter		_warnUserParam,
		boolean					_logging,
		LoggerChannel			_log,
		DHTLogger				_dhtLog) {

		pluginInterface		= _pluginInterface;
		protocolVersion		= _protocolVersion;
		network				= _network;
		v6					= _v6;
		port				= _port;
		reseedParam			= _reseed;
		warn_user_param		= _warnUserParam;
		log					= _log;
		dhtLog				= _dhtLog;

		final DHTPluginImplAdapter adapter = _adapter;

		try {
			storageManager = new DHTPluginStorageManager(network, dhtLog, getDataDir(_network));

			final PluginConfig conf = pluginInterface.getPluginconfig();

			int	sendDelay = conf.getPluginIntParameter("dht.senddelay", 25);
			int	recvDelay	= conf.getPluginIntParameter("dht.recvdelay", 10);

			boolean	bootstrap	= conf.getPluginBooleanParameter("dht.bootstrapnode", false);

			// start off optimistic with reachable = true
			boolean	initialReachable = conf.getPluginBooleanParameter("dht.reachable." + network, true);

			transport = DHTTransportFactory.createUDP(
						_protocolVersion,
						_network,
						_v6,
						_ip,
						storageManager.getMostRecentAddress(),
						_port,
						3,
						1,
						10000, 	// udp timeout - tried less but a significant number of
								// premature timeouts occurred
						sendDelay, recvDelay,
						bootstrap,
						initialReachable,
						dhtLog);

			transport.addListener(
				new DHTTransportListener() {
					public void localContactChanged(DHTTransportContact	localContact) {
						storageManager.localContactChanged(localContact);
						if (adapter != null) {
							adapter.localContactChanged( getLocalAddress());
						}
					}

					public void resetNetworkPositions() {}

					public void currentAddress(String address) {
						storageManager.recordCurrentAddress(address);
					}

					public void reachabilityChanged(boolean	reacheable) {}
				});

			Properties	props = new Properties();

			/*
			System.out.println("FRIGGED REFRESH PERIOD");
			props.put(DHT.PR_CACHE_REPUBLISH_INTERVAL, new Integer( 5*60*1000));
			*/
			if (_network == DHT.NW_CVS) {
					// reduce network usage
				//System.out.println("CVS DHT cache republish interval modified");
				props.put(DHT.PR_CACHE_REPUBLISH_INTERVAL, new Integer(1*60*60*1000));
			}

			dht = DHTFactory.create(
						transport,
						props,
						storageManager,
						_natAdapter,
						dhtLog);

			//Log.d(TAG, ">>> 1");
			//dht.getControl().print_contactsToQuery();
			
			pluginInterface.firePluginEvent(
				new PluginEvent() {
					public int getType() {
						return (DHTPlugin.EVENT_DHT_AVAILABLE);
					}

					public Object getValue() {
						return (dht);
					}
				});

			dht.setLogging(_logging);
			
			//Log.d(TAG, ">>> 2");
			//dht.getControl().print_contactsToQuery();
			
			DHTTransportContact rootSeed = importRootSeed();
			Log.d(TAG, ">>> 3");
			dht.getControl().print_contactsToQuery();
			
			storageManager.importContacts(dht);
			//Log.d(TAG, ">>> 4");
			//dht.getControl().print_contactsToQuery();
			
			pluginInterface.getUtilities().createTimer("DHTExport", true).addPeriodicEvent(
					2*60*1000,
					new UTTimerEventPerformer() {
						private int tick_count = 0;

						public void perform(UTTimerEvent event) {
							tick_count++;
							if (tick_count == 1 || tick_count % 5 == 0) {
								checkForReSeed(false);
								storageManager.exportContacts(dht);
							}
						}
					});

			integrateDHT(true, rootSeed);
			status = DHTPlugin.STATUS_RUNNING;
			statusText = "Running";
		} catch (Throwable e) {
			Debug.printStackTrace(e);
			log.log("DHT integrtion fails", e);
			statusText = "DHT Integration fails: " + Debug.getNestedExceptionMessage(e);
			status	= DHTPlugin.STATUS_FAILED;
		}
	}

	public void updateStats(int sampleStatsTicks) {
		statsTicks++;
		if (transport != null) {
			PluginConfig conf = pluginInterface.getPluginconfig();
			boolean current_reachable = transport.isReachable();
			if (current_reachable != conf.getPluginBooleanParameter("dht.reachable." + network, true)) {
					// reachability has changed
				conf.setPluginParameter("dht.reachable." + network, current_reachable);
				if (!current_reachable) {
					String msg = "If you have a router/firewall, please check that you have port " + port +
									" UDP open.\nDecentralised tracking requires this." ;
					int	warned_port = pluginInterface.getPluginconfig().getPluginIntParameter("udp_warned_port", 0);
					if (warned_port == port || !warn_user_param.getValue()) {
						log.log(msg);
					} else {
						pluginInterface.getPluginconfig().setPluginParameter("udp_warned_port", port);
						log.logAlert(LoggerChannel.LT_WARNING, msg);
					}
				} else {
					log.log("Reachability changed for the better");
				}
			}
			if (statsTicks % sampleStatsTicks == 0) {
				logStats();
			}
		}
	}

	public int getStatus() {
		return (status);
	}

	public String getStatusText() {
		return (statusText);
	}

	public boolean isReachable() {
		return (transport.isReachable());
	}

	public void setLogging(boolean l) {
		dht.setLogging(l);
	}

	public void tick() {
	}

	public int getPort() {
		return (port);
	}

	public void setPort(int newPort) {
		port = newPort;
		try {
			transport.setPort(port);
		} catch (Throwable e) {
			log.log(e);
		}
	}

	public long getClockSkew() {
		return (transport.getStats().getSkewAverage());
	}

	public void logStats() {
		DHTDBStats			d_stats	= dht.getDataBase().getStats();
		DHTControlStats		c_stats = dht.getControl().getStats();
		DHTRouterStats		r_stats = dht.getRouter().getStats();
		DHTTransportStats 	t_stats = transport.getStats();

		long[]	rs = r_stats.getStats();

		log.log("DHT:ip=" + transport.getLocalContact().getAddress() +
					",net=" + transport.getNetwork() +
					",prot=V" + transport.getProtocolVersion()+
					",reach=" + transport.isReachable()+
					",flags=" + Integer.toString((int)(transport.getGenericFlags()&0xff), 16 ));

		log.log( 	"Router" +
					":nodes=" + rs[DHTRouterStats.ST_NODES] +
					",leaves=" + rs[DHTRouterStats.ST_LEAVES] +
					",contacts=" + rs[DHTRouterStats.ST_CONTACTS] +
					",replacement=" + rs[DHTRouterStats.ST_REPLACEMENTS] +
					",live=" + rs[DHTRouterStats.ST_CONTACTS_LIVE] +
					",unknown=" + rs[DHTRouterStats.ST_CONTACTS_UNKNOWN] +
					",failing=" + rs[DHTRouterStats.ST_CONTACTS_DEAD]);

		log.log( 	"Transport" +
					":" + t_stats.getString());

		int[]	dbv_details = d_stats.getValueDetails();

		log.log(    "Control:dht=" + c_stats.getEstimatedDHTSize() +
				   	", Database:keys=" + d_stats.getKeyCount() +
				   	",vals=" + dbv_details[DHTDBStats.VD_VALUE_COUNT]+
				   	",loc=" + dbv_details[DHTDBStats.VD_LOCAL_SIZE]+
				   	",dir=" + dbv_details[DHTDBStats.VD_DIRECT_SIZE]+
				   	",ind=" + dbv_details[DHTDBStats.VD_INDIRECT_SIZE]+
				   	",div_f=" + dbv_details[DHTDBStats.VD_DIV_FREQ]+
				   	",div_s=" + dbv_details[DHTDBStats.VD_DIV_SIZE]);

		DHTNATPuncher np = dht.getNATPuncher();

		if (np != null) {
			log.log("NAT: " + np.getStats());
		}
	}

	protected File getDataDir(int network) {
		File dir = new File(pluginInterface.getUtilities().getAzureusUserDir(), "dht");
		if (network != 0) {
			dir = new File(dir, "net" + network);
		}
		FileUtil.mkdirs(dir);
		return (dir);
	}

	public void integrateDHT(boolean first,
		DHTTransportContact	removeAfterwards) {
		
		try {
			reseedParam.setEnabled(false);
			log.log("DHT " + (first?"":"re-") + "integration starts");
			long start = SystemTime.getCurrentTime();
			dht.integrate(false);
			if (removeAfterwards != null) {
				log.log("Removing seed " + removeAfterwards.getString());
				removeAfterwards.remove();
			}
			long end = SystemTime.getCurrentTime();
			log.log("DHT " + (first?"":"re-") + "integration complete: elapsed = " + (end-start));
			dht.print(false);
		} finally {
			reseedParam.setEnabled(true);
		}
	}

	public void checkForReSeed(
		boolean	force) {
		int	seed_limit = 32;
		try {
			long[]	router_stats = dht.getRouter().getStats().getStats();
			if (router_stats[ DHTRouterStats.ST_CONTACTS_LIVE] < seed_limit || force) {
				if (force) {
					log.log("Reseeding");
				} else {
					log.log("Less than 32 live contacts, reseeding");
				}
				int	peers_imported	= 0;
					// only try boostrapping off connected peers on the main network as it is unlikely
					// any of them are running CVS and hence the boostrap will fail
				if (network == DHT.NW_MAIN || network == DHT.NW_MAIN_V6) {
						// first look for peers to directly import
					Download[]	downloads = pluginInterface.getDownloadManager().getDownloads();
outer:
					for (int i=0;i<downloads.length;i++) {
						Download	download = downloads[i];
						PeerManager pm = download.getPeerManager();
						if (pm == null) {
							continue;
						}
						Peer[] 	peers = pm.getPeers();
						for (int j=0;j<peers.length;j++) {
							Peer	p = peers[j];
							int	peer_udp_port = p.getUDPNonDataListenPort();
							if (peer_udp_port != 0) {
								boolean is_v6 = p.getIp().contains(":");
								if (is_v6 == v6) {
									String ip =  p.getIp();
									if (AENetworkClassifier.categoriseAddress(ip) == AENetworkClassifier.AT_PUBLIC) {
										if (importSeed(ip, peer_udp_port) != null) {
											peers_imported++;
											if (peers_imported > seed_limit) {
												break outer;
											}
										}
									}
								}
							}
						}
					}
					if (peers_imported < 16) {
						List<InetSocketAddress> list = VersionCheckClient.getSingleton().getDHTBootstrap(network == DHT.NW_MAIN);
						for (InetSocketAddress address: list) {
							if (importSeed(address) != null) {
								peers_imported++;
								if (peers_imported > seed_limit) {
									break;
								}
							}
						}
					}
				}
				DHTTransportContact	root_to_remove = null;
				if (peers_imported == 0) {
					root_to_remove = importRootSeed();
					if (root_to_remove != null) {
						peers_imported++;
					}
				}
				if (peers_imported > 0) {
					integrateDHT(false, root_to_remove);
				} else {
					log.log("No valid peers found to reseed from");
				}
			}
		} catch (Throwable e) {
			log.log(e);
		}
	}

	protected DHTTransportContact importRootSeed() {
		try {
			long now = SystemTime.getCurrentTime();
			if (now - lastRootSeedImportTime > MIN_ROOT_SEED_IMPORT_PERIOD) {
				lastRootSeedImportTime = now;
				return (importSeed(getSeedAddress()));
			} else {
				log.log("    root seed imported too recently, ignoring");
			}
		} catch (Throwable e) {
			log.log(e);
		}
		return (null);
	}

	public DHTTransportContact importSeed(String ip, int port) {
		try {
			return (transport.importContact(checkResolve(new InetSocketAddress(ip, port)), protocolVersion, true));
		} catch (Throwable e) {
			log.log(e);
			return (null);
		}
	}

	protected DHTTransportContact importSeed(InetAddress ia, int port) {
		try {
			return (transport.importContact(new InetSocketAddress(ia, port), protocolVersion, true));
		} catch (Throwable e) {
			log.log(e);
			return (null);
		}
	}

	protected DHTTransportContact importSeed(InetSocketAddress ia) {
		try {
			return (transport.importContact(ia, protocolVersion, true));
		} catch (Throwable e) {
			log.log(e);
			return (null);
		}
	}

	protected InetSocketAddress	getSeedAddress() {
		return (checkResolve(new InetSocketAddress(v6 ? SEED_ADDRESS_V6 : SEED_ADDRESS_V4, SEED_PORT)));
	}

	private InetSocketAddress checkResolve(InetSocketAddress isa) {
		if (v6) {
			if (isa.isUnresolved()) {
				try {
					DNSUtils.DNSUtilsIntf dns_utils = DNSUtils.getSingleton();
					if (dns_utils != null) {
						String host = dns_utils.getIPV6ByName(isa.getHostName()).getHostAddress();
						isa = new InetSocketAddress(host, isa.getPort());
					}
				} catch (Throwable e) {
				}
			}
		}
		return (isa);
	}

	public boolean isDiversified(
		byte[]		key) {
		return (dht.isDiversified( key));
	}

	public void put(
		byte[]						key,
		String						description,
		byte[]						value,
		byte						flags,
		DHTPluginOperationListener	listener) {
		put(key, description, value, flags, true, listener);
	}

	public void	put(
		final byte[]						key,
		final String						description,
		final byte[]						value,
		final byte							flags,
		final boolean						high_priority,
		final DHTPluginOperationListener	listener) {
		
		dht.put(key,
				description,
				value,
				flags,
				high_priority,
				new DHTOperationListener() {
			
					private boolean started;
					
					public void searching(
						DHTTransportContact	contact,
						int					level,
						int					active_searches) {
						if (listener != null) {
							synchronized(this) {
								if (started) {
									return;
								}
								started = true;
							}
							listener.starts(key);
						}
					}
					
					public boolean diversified(String desc) {
						return (true);
					}
					
					public void found(
						DHTTransportContact	contact,
						boolean				is_closest) {
					}
					
					public void read(
						DHTTransportContact	_contact,
						DHTTransportValue	_value) {
						Debug.out("read operation not supported for puts");
					}
					
					public void wrote(
						DHTTransportContact	_contact,
						DHTTransportValue	_value) {
						// log.log("Put: wrote " + _value.getString() + " to " + _contact.getString());
						if (listener != null) {
							listener.valueWritten(new DHTPluginContactImpl(DHTPluginImpl.this, _contact ), mapValue( _value));
						}
					}
					
					public void complete(boolean timeout) {
						// log.log("Put: complete, timeout = " + timeout);
						if (listener != null) {
							listener.complete(key, timeout);
						}
					}
				}
		);
	}

	public DHTPluginValue getLocalValue(byte[] key) {
		final DHTTransportValue	val = dht.getLocalValue(key);
		if (val == null) {
			return (null);
		}
		return (mapValue(val));
	}

	public List<DHTPluginValue> getValues() {
		DHTDB	db = dht.getDataBase();
		Iterator<HashWrapper>	keys = db.getKeys();
		List<DHTPluginValue>	vals = new ArrayList<DHTPluginValue>();
		while (keys.hasNext()) {
			DHTDBValue val = db.getAnyValue( keys.next());
			if (val != null) {
				vals.add(mapValue( val));
			}
		}
		return (vals);
	}

	public List<DHTPluginValue> getValues(byte[] key) {
		List<DHTPluginValue>	vals = new ArrayList<DHTPluginValue>();
		if (dht != null) {
			try {
				List<DHTTransportValue> values = dht.getStoredValues(key);
				for (DHTTransportValue v: values) {
					vals.add(mapValue( v));
				}
				return (vals);
			} catch (Throwable e) {
				Debug.out(e);
			}
		}
		return (vals);
	}

	public void get(
		final byte[]						key,
		final String						description,
		final byte							flags,
		final int							maxValues,
		final long							timeout,
		final boolean						exhaustive,
		final boolean						highPriority,
		final DHTPluginOperationListener	listener) {
		
		dht.get( 	
				key, description, flags, maxValues, timeout, exhaustive, highPriority,
				new DHTOperationListener() {
		
					private boolean	started = false;

					public void searching(
						DHTTransportContact	contact,
						int					level,
						int					activeSearches) {
						
						if (listener != null) {
							synchronized(this) {
								if (started) {
									return;
								}
								started = true;
							}
							listener.starts(key);
						}
					}

					public boolean diversified(String desc) {
						if (listener != null) {
							return (listener.diversified());
						}
						return (true);
					}

					public void found(DHTTransportContact contact, boolean isClosest) {
					}

					public void read(
						final DHTTransportContact	contact,
						final DHTTransportValue		value) {
						// log.log("Get: read " + value.getString() + " from " + contact.getString() + ", originator = " + value.getOriginator().getString());
						if (listener != null) {
							listener.valueRead(new DHTPluginContactImpl( DHTPluginImpl.this, value.getOriginator()), mapValue( value));
						}
					}

					public void wrote(
						final DHTTransportContact	contact,
						final DHTTransportValue		value) {
						// log.log("Get: wrote " + value.getString() + " to " + contact.getString());
					}

					public void complete(boolean _timeout) {
						// log.log("Get: complete, timeout = " + _timeout);
						if (listener != null) {
							listener.complete(key, _timeout);
						}
					}
				}
		);
	}

	public void remove(
		final byte[]						key,
		final String						description,
		final DHTPluginOperationListener	listener) {
		dht.remove( 	key,
						description,
						new DHTOperationListener() {
							private boolean started;
							
							public void searching(
								DHTTransportContact	contact,
								int					level,
								int					active_searches) {
								if (listener != null) {
									synchronized(this) {
										if (started) {
											return;
										}
										started = true;
									}
									listener.starts(key);
								}
							}
							
							public void found(
								DHTTransportContact	contact,
								boolean				is_closest) {
							}
							
							public boolean diversified(String desc) {
								return (true);
							}
							
							public void read(
								DHTTransportContact	contact,
								DHTTransportValue	value) {
								// log.log("Remove: read " + value.getString() + " from " + contact.getString());
							}
							
							public void wrote(
								DHTTransportContact	contact,
								DHTTransportValue	value) {
								// log.log("Remove: wrote " + value.getString() + " to " + contact.getString());
								if (listener != null) {
									listener.valueWritten(new DHTPluginContactImpl( DHTPluginImpl.this, contact ), mapValue( value));
								}
							}
							
							public void complete(boolean timeout) {
								// log.log("Remove: complete, timeout = " + timeout);
								if (listener != null) {
									listener.complete(key, timeout);
								}
							}
						});
	}

	public void remove(
		final DHTPluginContact[]			targets,
		final byte[]						key,
		final String						description,
		final DHTPluginOperationListener	listener) {
		DHTTransportContact[]	t_contacts = new DHTTransportContact[ targets.length ];
		for (int i=0;i<targets.length;i++) {
			t_contacts[i] = ((DHTPluginContactImpl)targets[i]).getContact();
		}
		dht.remove( 	t_contacts,
						key,
						description,
						new DHTOperationListener() {
							private boolean started;
							public void searching(
								DHTTransportContact	contact,
								int					level,
								int					active_searches) {
								if (listener != null) {
									synchronized(this) {
										if (started) {
											return;
										}
										started = true;
									}
									listener.starts(key);
								}
							}
							public void found(
								DHTTransportContact	contact,
								boolean				is_closest) {
							}
							
							public boolean diversified(String desc) {
								return (true);
							}
							
							public void read(
								DHTTransportContact	contact,
								DHTTransportValue	value) {
								// log.log("Remove: read " + value.getString() + " from " + contact.getString());
							}
							
							public void wrote(
								DHTTransportContact	contact,
								DHTTransportValue	value) {
								// log.log("Remove: wrote " + value.getString() + " to " + contact.getString());
								if (listener != null) {
									listener.valueWritten(new DHTPluginContactImpl( DHTPluginImpl.this, contact ), mapValue( value));
								}
							}
							
							public void complete(boolean timeout) {
								// log.log("Remove: complete, timeout = " + timeout);
								if (listener != null) {
									listener.complete(key, timeout);
								}
							}
						});
	}

	public DHTPluginContact getLocalAddress() {
		return (new DHTPluginContactImpl(this, transport.getLocalContact()));
	}

	public DHTPluginContact importContact(Map<String, Object> map) {
		try {
			return (new DHTPluginContactImpl(this, transport.importContact(map)));
		} catch (DHTTransportException e) {
			Debug.printStackTrace(e);
			return (null);
		}
	}

	public DHTPluginContact importContact(InetSocketAddress address) {
		try {
			return (new DHTPluginContactImpl(this, transport.importContact(address, protocolVersion, false)));
		} catch (DHTTransportException e) {
			Debug.printStackTrace(e);
			return (null);
		}
	}

	public DHTPluginContact
	importContact(
		InetSocketAddress				address,
		byte							version) {
		try {
			return (new DHTPluginContactImpl( this, transport.importContact( address, version, false)));

		} catch (DHTTransportException	e) {

			Debug.printStackTrace(e);

			return (null);
		}
	}

		// direct read/write support

	private Map<DHTPluginTransferHandler,DHTTransportTransferHandler>	handler_map = new HashMap<DHTPluginTransferHandler, DHTTransportTransferHandler>();

	public void registerHandler(
		byte[]							handler_key,
		final DHTPluginTransferHandler	handler,
		Map<String,Object>				options) {
		DHTTransportTransferHandler h =
			new DHTTransportTransferHandler() {
				public String getName() {
					return ( handler.getName());
				}

				public byte[]
				handleRead(
					DHTTransportContact	originator,
					byte[]				key) {
					return (handler.handleRead(new DHTPluginContactImpl( DHTPluginImpl.this, originator ), key));
				}

				public byte[]
				handleWrite(
					DHTTransportContact	originator,
					byte[]				key,
					byte[]				value) {
					return (handler.handleWrite(new DHTPluginContactImpl( DHTPluginImpl.this, originator ), key, value));
				}
			};

		synchronized(handler_map) {

			if (handler_map.containsKey( handler)) {

				Debug.out("Warning: handler already exists");
			} else {

				handler_map.put(handler, h);
			}
		}

		dht.getTransport().registerTransferHandler(handler_key, h, options);
	}

	public void unregisterHandler(
		byte[]							handler_key,
		final DHTPluginTransferHandler	handler) {
		DHTTransportTransferHandler h;

		synchronized(handler_map) {

			h = handler_map.remove(handler);
		}

		if (h == null) {

			Debug.out("Mapping not found for handler");

		} else {

			try {
				getDHT().getTransport().unregisterTransferHandler(handler_key, h);

			} catch (Throwable e) {

				Debug.out(e);
			}
		}
	}

	public byte[]
	read(
		final DHTPluginProgressListener	listener,
		DHTPluginContact				target,
		byte[]							handler_key,
		byte[]							key,
		long							timeout) {
		try {
			return ( dht.getTransport().readTransfer(
					listener == null ? null :
						new DHTTransportProgressListener() {
							public void reportSize(
								long	size) {
								listener.reportSize(size);
							}

							public void reportActivity(
								String	str) {
								listener.reportActivity(str);
							}

							public void reportCompleteness(
								int		percent) {
								listener.reportCompleteness(percent);
							}
						},
						((DHTPluginContactImpl)target).getContact(),
						handler_key,
						key,
						timeout ));

		} catch (DHTTransportException e) {

			throw (new RuntimeException( e));
		}
	}

	public void write(
		final DHTPluginProgressListener	listener,
		DHTPluginContact				target,
		byte[]							handler_key,
		byte[]							key,
		byte[]							data,
		long							timeout) {
		try {
			dht.getTransport().writeTransfer(
					listener == null ? null :
					new DHTTransportProgressListener() {
						public void reportSize(
							long	size) {
							listener.reportSize(size);
						}

						public void reportActivity(
							String	str) {
							listener.reportActivity(str);
						}

						public void reportCompleteness(
							int		percent) {
							listener.reportCompleteness(percent);
						}
					},
					((DHTPluginContactImpl)target).getContact(),
					handler_key,
					key,
					data,
					timeout);

		} catch (DHTTransportException e) {

			throw (new RuntimeException( e));
		}
	}

	public byte[]
	call(
		final DHTPluginProgressListener	listener,
		DHTPluginContact				target,
		byte[]							handler_key,
		byte[]							data,
		long							timeout) {
		try {
			return (
				dht.getTransport().writeReadTransfer(
					listener == null ? null :
					new DHTTransportProgressListener() {
						public void reportSize(
							long	size) {
							listener.reportSize(size);
						}

						public void reportActivity(
							String	str) {
							listener.reportActivity(str);
						}

						public void reportCompleteness(
							int		percent) {
							listener.reportCompleteness(percent);
						}
					},
					((DHTPluginContactImpl)target).getContact(),
					handler_key,
					data,
					timeout ));

		} catch (DHTTransportException e) {

			throw (new RuntimeException( e));
		}
	}

	public DHT
	getDHT() {
		return (dht);
	}

	public void setSuspended(
		boolean	susp) {
		dht.setSuspended(susp);
	}

	public void closedownInitiated() {
		storageManager.exportContacts(dht);

		dht.destroy();
	}

	public boolean isRecentAddress(
		String		address) {
		return (storageManager.isRecentAddress( address));
	}

	protected DHTPluginValue
	mapValue(
		final DHTTransportValue	value) {
		if (value == null) {

			return (null);
		}

		return (new DHTPluginValueImpl(value));
	}


	public DHTPluginKeyStats
	decodeStats(
		DHTPluginValue	value) {
		if ((value.getFlags() & DHTPlugin.FLAG_STATS) == 0) {

			return (null);
		}

		try {
			DataInputStream	dis = new DataInputStream(new ByteArrayInputStream( value.getValue()));

			final DHTStorageKeyStats stats = storageManager.deserialiseStats(dis);

			return (
				new DHTPluginKeyStats() {
					public int getEntryCount() {
						return ( stats.getEntryCount());
					}

					public int getSize() {
						return ( stats.getSize());
					}

					public int getReadsPerMinute() {
						return ( stats.getReadsPerMinute());
					}

					public byte
					getDiversification() {
						return ( stats.getDiversification());
					}
				});

		} catch (Throwable e) {

			Debug.printStackTrace(e);

			return (null);
		}
	}

	public byte[]
	getID() {
		return ( dht.getRouter().getID());
	}

	public boolean isIPV6() {
		return ( dht.getTransport().isIPV6());
	}

	public int getNetwork() {
		return ( dht.getTransport().getNetwork());
	}

	public DHTPluginContact[] getReachableContacts() {
		DHTTransportContact[] contacts = dht.getTransport().getReachableContacts();
		DHTPluginContact[] result = new DHTPluginContact[contacts.length];
		for (int i=0;i<contacts.length;i++) {
			result[i] = new DHTPluginContactImpl(this, contacts[i]);
		}
		return (result);
	}

	public DHTPluginContact[] getRecentContacts() {
		DHTTransportContact[] contacts = dht.getTransport().getRecentContacts();
		DHTPluginContact[] result = new DHTPluginContact[contacts.length];
		for (int i=0;i<contacts.length;i++) {
			result[i] = new DHTPluginContactImpl(this, contacts[i]);
		}
		return (result);
	}

	public List<DHTPluginContact> getClosestContacts(byte[] toId, boolean liveOnly) {
		List<DHTTransportContact> contacts = dht.getControl().getClosestKContactsList(toId, liveOnly);
		List<DHTPluginContact> result = new ArrayList<DHTPluginContact>( contacts.size());
		for (DHTTransportContact contact: contacts) {
			result.add(new DHTPluginContactImpl( this, contact));
		}
		return (result);
	}
}
