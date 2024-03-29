/*
 * Created on 31-Jan-2005
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

package com.aelitis.azureus.plugins.tracker.dht;


import java.net.InetSocketAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.WeakHashMap;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.peer.PEPeerManager;
import org.gudy.azureus2.core3.peer.PEPeerSource;
import org.gudy.azureus2.core3.tracker.protocol.PRHelpers;
import org.gudy.azureus2.core3.util.AEMonitor;
import org.gudy.azureus2.core3.util.AENetworkClassifier;
import org.gudy.azureus2.core3.util.AESemaphore;
import org.gudy.azureus2.core3.util.AEThread2;
import org.gudy.azureus2.core3.util.ByteFormatter;
import org.gudy.azureus2.core3.util.Constants;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.SystemTime;
import org.gudy.azureus2.core3.util.TimeFormatter;
import org.gudy.azureus2.core3.util.TorrentUtils;
import org.gudy.azureus2.plugins.Plugin;
import org.gudy.azureus2.plugins.PluginInterface;
import org.gudy.azureus2.plugins.PluginListener;
import org.gudy.azureus2.plugins.download.Download;
import org.gudy.azureus2.plugins.download.DownloadAnnounceResult;
import org.gudy.azureus2.plugins.download.DownloadAnnounceResultPeer;
import org.gudy.azureus2.plugins.download.DownloadAttributeListener;
import org.gudy.azureus2.plugins.download.DownloadListener;
import org.gudy.azureus2.plugins.download.DownloadManagerListener;
import org.gudy.azureus2.plugins.download.DownloadScrapeResult;
import org.gudy.azureus2.plugins.download.DownloadTrackerListener;
import org.gudy.azureus2.plugins.logging.LoggerChannel;
import org.gudy.azureus2.plugins.logging.LoggerChannelListener;
import org.gudy.azureus2.plugins.peers.Peer;
import org.gudy.azureus2.plugins.peers.PeerManager;
import org.gudy.azureus2.plugins.torrent.Torrent;
import org.gudy.azureus2.plugins.torrent.TorrentAttribute;
import org.gudy.azureus2.plugins.ui.UIManager;
import org.gudy.azureus2.plugins.ui.config.BooleanParameter;
import org.gudy.azureus2.plugins.ui.config.ConfigSection;
import org.gudy.azureus2.plugins.ui.config.IntParameter;
import org.gudy.azureus2.plugins.ui.config.Parameter;
import org.gudy.azureus2.plugins.ui.config.ParameterListener;
import org.gudy.azureus2.plugins.ui.model.BasicPluginConfigModel;
import org.gudy.azureus2.plugins.ui.model.BasicPluginViewModel;
import org.gudy.azureus2.plugins.utils.DelayedTask;
import org.gudy.azureus2.plugins.utils.UTTimerEvent;
import org.gudy.azureus2.plugins.utils.UTTimerEventPerformer;
import org.gudy.azureus2.pluginsimpl.local.PluginCoreUtils;

import com.aelitis.azureus.core.networkmanager.NetworkManager;
import com.aelitis.azureus.core.tracker.TrackerPeerSource;
import com.aelitis.azureus.core.tracker.TrackerPeerSourceAdapter;
import com.aelitis.azureus.plugins.I2PHelpers;
import com.aelitis.azureus.plugins.dht.DHTPlugin;
import com.aelitis.azureus.plugins.dht.DHTPluginContact;
import com.aelitis.azureus.plugins.dht.DHTPluginOperationListener;
import com.aelitis.azureus.plugins.dht.DHTPluginValue;

import hello.util.Log;

/**
 * @author parg
 *
 */

public class DHTTrackerPlugin
	implements Plugin, DownloadListener, DownloadAttributeListener, DownloadTrackerListener {

	private static String TAG = DHTTrackerPlugin.class.getSimpleName();
	
	public static Object	DOWNLOAD_USER_DATA_I2P_SCRAPE_KEY	= new Object();

	private static final String	PLUGIN_NAME				= "Distributed Tracker";
	private static final String PLUGIN_CONFIGSECTION_ID = "plugins.dhttracker";
	private static final String PLUGIN_RESOURCE_ID		= "ConfigView.section.plugins.dhttracker";

	private static final int	ANNOUNCE_TIMEOUT			= 2*60*1000;
	private static final int	ANNOUNCE_DERIVED_TIMEOUT	= 60*1000;	// spend less time on these
	private static final int	SCRAPE_TIMEOUT				= 30*1000;

	private static final int	ANNOUNCE_MIN_DEFAULT		= 2*60*1000;
	private static final int	ANNOUNCE_MAX				= 60*60*1000;
	private static final int	ANNOUNCE_MAX_DERIVED_ONLY	= 30*60*1000;

	private static final int	INTERESTING_CHECK_PERIOD		= 4*60*60*1000;
	private static final int	INTERESTING_INIT_RAND_OURS		=    5*60*1000;
	private static final int	INTERESTING_INIT_MIN_OURS		=    2*60*1000;
	private static final int	INTERESTING_INIT_RAND_OTHERS	=   30*60*1000;
	private static final int	INTERESTING_INIT_MIN_OTHERS		=    5*60*1000;

	private static final int	INTERESTING_DHT_CHECK_PERIOD	= 1*60*60*1000;
	private static final int	INTERESTING_DHT_INIT_RAND		=    5*60*1000;
	private static final int	INTERESTING_DHT_INIT_MIN		=    2*60*1000;


	private static final int	INTERESTING_AVAIL_MAX		= 8;	// won't pub if more
	private static final int	INTERESTING_PUB_MAX_DEFAULT	= 30;	// limit on pubs

	private static final int	REG_TYPE_NONE			= 1;
	private static final int	REG_TYPE_FULL			= 2;
	private static final int	REG_TYPE_DERIVED		= 3;

	private static final int	LIMITED_TRACK_SIZE		= 16;

	private static final boolean	TRACK_NORMAL_DEFAULT	= true;
	private static final boolean	TRACK_LIMITED_DEFAULT	= true;

	private static final boolean	TEST_ALWAYS_TRACK		= false;

	public static final int	NUM_WANT			= 30;	// Limit to ensure replies fit in 1 packet

	private static final long	start_time = SystemTime.getCurrentTime();

	private static final Object	DL_DERIVED_METRIC_KEY		= new Object();
	private static final int	DL_DERIVED_MIN_TRACK		= 5;
	private static final int	DL_DERIVED_MAX_TRACK		= 20;
	private static final int	DIRECT_INJECT_PEER_MAX		= 5;

	//private static final boolean ADD_ASN_DERIVED_TARGET			= false;
	//private static final boolean ADD_NETPOS_DERIVED_TARGETS		= false;

	private static URL	DEFAULT_URL;

	static{
		try {
			DEFAULT_URL = new URL("dht:");
		} catch (Throwable e) {
			Debug.printStackTrace(e);
		}
	}

	private PluginInterface			pluginInterface;
	private BasicPluginViewModel 	model;
	private DHTPlugin				dht;

	private TorrentAttribute 	ta_networks;
	private TorrentAttribute 	ta_peer_sources;

	private Map<Download,Long>		interestingDownloads 	= new HashMap<Download,Long>();
	private int						interesting_published	= 0;
	private int						interesting_pub_max		= INTERESTING_PUB_MAX_DEFAULT;
	private Map<Download,int[]>		runningDownloads 		= new HashMap<Download,int[]>();
	private Map<Download,int[]>		run_data_cache	 		= new HashMap<Download,int[]>();
	private Map<Download,RegistrationDetails>	registered_downloads 	= new HashMap<Download,RegistrationDetails>();

	private Map<Download,Boolean>	limited_online_tracking	= new HashMap<Download,Boolean>();
	private Map<Download,Long>		queryMap			 	= new HashMap<Download,Long>();

	private Map<Download,Integer>	in_progress				= new HashMap<Download,Integer>();

	// external config to limit plugin op to pure decentralised only
	private boolean				track_only_decentralsed = COConfigurationManager.getBooleanParameter("dhtplugin.track.only.decentralised", false);

	private BooleanParameter	track_normal_when_offline;
	private BooleanParameter	track_limited_when_online;

	private long				current_announce_interval = ANNOUNCE_MIN_DEFAULT;

	private LoggerChannel		log;

	private Map<Download,int[]> scrape_injection_map = new WeakHashMap<Download,int[]>();

	private Random				random = new Random();
	private boolean				isRunning;

	private AEMonitor			thisMon	= new AEMonitor("DHTTrackerPlugin");

	//private DHTNetworkPosition[]	current_network_positions;
	//private long					last_net_pos_time;

	private AESemaphore			initialisedSem = new AESemaphore("DHTTrackerPlugin:init");
	private DHTTrackerPluginAlt	alt_lookup_handler;
	private boolean				disable_put;

	{
		COConfigurationManager.addAndFireParameterListeners(
			new String[]{
				"Enable.Proxy",
				"Enable.SOCKS",
			},
			new org.gudy.azureus2.core3.config.ParameterListener() {
				public void parameterChanged(String parameter_name) {
					boolean	enable_proxy 	= COConfigurationManager.getBooleanParameter("Enable.Proxy");
				    boolean enable_socks	= COConfigurationManager.getBooleanParameter("Enable.SOCKS");

				    disable_put = enable_proxy && enable_socks;
				}
			});
	}

	public static void load(PluginInterface pluginInterface) {
		pluginInterface.getPluginProperties().setProperty("plugin.version", 	"1.0");
		pluginInterface.getPluginProperties().setProperty("plugin.name", 		PLUGIN_NAME);
	}

	public void initialize(PluginInterface _plugin_interface) {

		pluginInterface	= _plugin_interface;

		log = pluginInterface.getLogger().getTimeStampedChannel(PLUGIN_NAME);

		ta_networks 	= pluginInterface.getTorrentManager().getAttribute(TorrentAttribute.TA_NETWORKS);
		ta_peer_sources = pluginInterface.getTorrentManager().getAttribute(TorrentAttribute.TA_PEER_SOURCES);

		UIManager uiManager = pluginInterface.getUIManager();

		model = uiManager.createBasicPluginViewModel(PLUGIN_RESOURCE_ID);
		model.setConfigSectionID(PLUGIN_CONFIGSECTION_ID);

		BasicPluginConfigModel config =
			uiManager.createBasicPluginConfigModel(ConfigSection.SECTION_PLUGINS, PLUGIN_CONFIGSECTION_ID);

		track_normal_when_offline = config.addBooleanParameter2("dhttracker.tracknormalwhenoffline", "dhttracker.tracknormalwhenoffline", TRACK_NORMAL_DEFAULT);
		track_limited_when_online = config.addBooleanParameter2("dhttracker.tracklimitedwhenonline", "dhttracker.tracklimitedwhenonline", TRACK_LIMITED_DEFAULT);
		track_limited_when_online.addListener(
			new ParameterListener() {
				public void parameterChanged(Parameter param) {
					configChanged();
				}
			});

		track_normal_when_offline.addListener(
			new ParameterListener() {
				public void parameterChanged(Parameter param) {
					track_limited_when_online.setEnabled( track_normal_when_offline.getValue());

					configChanged();
				}
			});

		if (!track_normal_when_offline.getValue()) {
			track_limited_when_online.setEnabled(false);
		}

		interesting_pub_max = pluginInterface.getPluginconfig().getPluginIntParameter("dhttracker.presencepubmax", INTERESTING_PUB_MAX_DEFAULT);

		if (!TRACK_NORMAL_DEFAULT) {
			// should be TRUE by default
			System.out.println("**** DHT Tracker default set for testing purposes ****");
		}

		BooleanParameter	enable_alt 	= config.addBooleanParameter2("dhttracker.enable_alt", "dhttracker.enable_alt", true);
		IntParameter 		alt_port 	= config.addIntParameter2("dhttracker.alt_port", "dhttracker.alt_port", 0, 0, 65535);
		enable_alt.addEnabledOnSelection(alt_port);
		config.createGroup("dhttracker.alt_group", new Parameter[]{ enable_alt,alt_port });

		if (enable_alt.getValue()) {
			alt_lookup_handler = new DHTTrackerPluginAlt( alt_port.getValue());
		}

		model.getActivity().setVisible(false);
		model.getProgress().setVisible(false);
		model.getLogArea().setMaximumSize(80000);

		log.addListener(
				new LoggerChannelListener() {
					public void messageLogged(
						int		type,
						String	message) {
						model.getLogArea().appendText( message+"\n");
					}

					public void messageLogged(
						String		str,
						Throwable	error) {
						model.getLogArea().appendText( error.toString()+"\n");
					}
				});

		model.getStatus().setText(MessageText.getString("ManagerItem.initializing"));

		log.log("Waiting for Distributed Database initialisation");

		pluginInterface.addListener(
			new PluginListener() {
				public void initializationComplete() {
					boolean	release_now = true;
					try {
						final PluginInterface dhtPluginInterface = pluginInterface.getPluginManager().getPluginInterfaceByClass(DHTPlugin.class);
						if (dhtPluginInterface != null) {
							dht = (DHTPlugin)dhtPluginInterface.getPlugin();
							final DelayedTask dt = pluginInterface.getUtilities().createDelayedTask(
									new Runnable() {
										public void  run() {
											AEThread2 t =
												new AEThread2("DHTTrackerPlugin:init", true) {
													public void run() {
														try {
															if (dht.isEnabled()) {
																log.log("DDB Available");
																model.getStatus().setText(MessageText.getString("DHTView.activity.status.false"));
																initialise();
															} else {
																log.log("DDB Disabled");
																model.getStatus().setText(MessageText.getString("dht.status.disabled"));
																notRunning();
															}
														} catch (Throwable e) {
															log.log("DDB Failed", e);
															model.getStatus().setText(MessageText.getString("DHTView.operations.failed"));
															notRunning();
														} finally {
															initialisedSem.releaseForever();
														}
													}
												};
												t.start();
										}
									});
							dt.queue();
							release_now = false;
						} else {
							log.log("DDB Plugin missing");
							model.getStatus().setText(MessageText.getString("DHTView.operations.failed" ));
							notRunning();
						}
					} finally {
						if (release_now) {
							initialisedSem.releaseForever();
						}
					}
				}
				public void closedownInitiated() {}
				public void closedownComplete() {}
			});
	}

	protected void notRunning() {
		pluginInterface.getDownloadManager().addListener(
				new DownloadManagerListener() {
					public void downloadAdded(final Download download) {
						addDownload(download);
					}

					public void downloadRemoved(Download download) {
						removeDownload(download);
					}
				}
		);
	}

	protected void initialise() {

		isRunning = true;

		pluginInterface.getDownloadManager().addListener(
			new DownloadManagerListener() {
				public void downloadAdded(Download download) {
					addDownload(download);
				}

				public void downloadRemoved(Download download) {
					removeDownload(download);
				}
			}
		);

		pluginInterface.getUtilities().createTimer("DHT Tracker", true).addPeriodicEvent(
			15000,
			
			new UTTimerEventPerformer() {

				private int	ticks;
				private String 	prev_alt_status = "";

				public void perform(UTTimerEvent event) {
					ticks++;

					processRegistrations(ticks%8==0); // 15초x8=2분

					if (ticks == 2 || ticks%4==0) {
						processNonRegistrations();
					}

					if (alt_lookup_handler != null) {

						if (ticks % 4 == 0) {

							String alt_status = alt_lookup_handler.getString();
							if (!alt_status.equals( prev_alt_status)) {
								log.log("Alternative stats: " + alt_status);
								prev_alt_status = alt_status;
							}
						}
					}
				}
			});
	}

	public void waitUntilInitialised() {
		initialisedSem.reserve();
	}

	public boolean isRunning() {
		return (isRunning);
	}

	public void addDownload(final Download download) {

		/*Log.d(TAG, "addDownload() is called...");
		Throwable t = new Throwable();
		t.printStackTrace();*/
		
		Torrent	torrent = download.getTorrent();
		boolean	is_decentralised = false;

		if (torrent != null) {
			is_decentralised = TorrentUtils.isDecentralised( torrent.getAnnounceURL());
		}

		// bail on our low noise ones, these don't require decentralised tracking unless that's what they are
		if (download.getFlag(Download.FLAG_LOW_NOISE) && !is_decentralised) {
			return;
		}

		if (track_only_decentralsed) {
			if (torrent != null) {
				if (!is_decentralised) {
					return;
				}
			}
		}

		if (isRunning) {

			String[] networks = download.getListAttribute(ta_networks);

			if (torrent != null && networks != null) {

				boolean	public_net = false;

				for (int i = 0; i < networks.length; i++) {
					if (networks[i].equalsIgnoreCase("Public")) {
						public_net	= true;
						break;
					}
				}

				if (public_net && !torrent.isPrivate()) {

					boolean	our_download = torrent.wasCreatedByUs();
					long	delay;

					if (our_download) {
						if (download.getCreationTime() > start_time) {
							delay = 0;
						} else {
							delay = pluginInterface.getUtilities().getCurrentSystemTime() +
									INTERESTING_INIT_MIN_OURS +
									random.nextInt(INTERESTING_INIT_RAND_OURS);
						}
					} else {

						int	min;
						int	rand;

						if (TorrentUtils.isDecentralised( torrent.getAnnounceURL())) {

							min		= INTERESTING_DHT_INIT_MIN;
							rand	= INTERESTING_DHT_INIT_RAND;

						} else {

							min		= INTERESTING_INIT_MIN_OTHERS;
							rand	= INTERESTING_INIT_RAND_OTHERS;
						}

						delay = pluginInterface.getUtilities().getCurrentSystemTime() +
									min + random.nextInt(rand);
					}

					try {
						thisMon.enter();
						interestingDownloads.put(download, new Long( delay));
					} finally {
						thisMon.exit();
					}
				}
			}

			download.addAttributeListener(DHTTrackerPlugin.this, ta_networks, DownloadAttributeListener.WRITTEN);
			download.addAttributeListener(DHTTrackerPlugin.this, ta_peer_sources, DownloadAttributeListener.WRITTEN);
			download.addTrackerListener(DHTTrackerPlugin.this);
			download.addListener(DHTTrackerPlugin.this);
			checkDownloadForRegistration(download, true);

		} else {

			if (torrent != null && torrent.isDecentralised()) {

				download.addListener(
					new DownloadListener() {
						public void stateChanged(
							final Download		download,
							int					old_state,
							int					new_state) {
							int	state = download.getState();

							if (	state == Download.ST_DOWNLOADING ||
									state == Download.ST_SEEDING) {

								download.setAnnounceResult(
									new DownloadAnnounceResult() {
										public Download getDownload() {
											return (download);
										}

										public int getResponseType() {
											return (DownloadAnnounceResult.RT_ERROR);
										}

										public int getReportedPeerCount() {
											return (0);
										}

										public int getSeedCount() {
											return (0);
										}

										public int getNonSeedCount() {
											return (0);
										}

										public String getError() {
											return ("Distributed Database Offline");
										}

										public URL getURL() {
											return ( download.getTorrent().getAnnounceURL());
										}

										public DownloadAnnounceResultPeer[] getPeers() {
											return (new DownloadAnnounceResultPeer[0]);
										}

										public long getTimeToWait() {
											return (0);
										}

										public Map getExtensions() {
											return (null);
										}
									});
							}
						}

						public void positionChanged(
							Download		download,
							int 			oldPosition,
							int 			newPosition) {

						}
					});


				download.setScrapeResult(
					new DownloadScrapeResult() {
						public Download getDownload() {
							return (download);
						}

						public int getResponseType() {
							return (RT_ERROR);
						}

						public int getSeedCount() {
							return (-1);
						}

						public int getNonSeedCount() {
							return (-1);
						}

						public long getScrapeStartTime() {
							return ( SystemTime.getCurrentTime());
						}

						public void setNextScrapeStartTime(
							long nextScrapeStartTime) {
						}

						public long getNextScrapeStartTime() {
							return (-1);
						}

						public String getStatus() {
							return ("Distributed Database Offline");
						}

						public URL getURL() {
							return ( download.getTorrent().getAnnounceURL());
						}
					});
			}
		}
	}

	public void removeDownload(
		Download	download) {
		if (isRunning) {
			download.removeTrackerListener(DHTTrackerPlugin.this);

			download.removeListener(DHTTrackerPlugin.this);

			try {
				thisMon.enter();

				interestingDownloads.remove(download);

				runningDownloads.remove(download);

				run_data_cache.remove(download);

				limited_online_tracking.remove(download);

			} finally {

				thisMon.exit();
			}
		} else {

		}
	}

	public void attributeEventOccurred(Download download, TorrentAttribute attr, int event_type) {
		checkDownloadForRegistration(download, false);
	}

	public void scrapeResult(
		DownloadScrapeResult	result) {
		checkDownloadForRegistration(result.getDownload(), false);
	}

	public void announceResult(
		DownloadAnnounceResult	result) {
		checkDownloadForRegistration(result.getDownload(), false);
	}


	protected void checkDownloadForRegistration(
		Download		download,
		boolean			first_time) {
		if (download == null) {

			return;
		}

		boolean	skip_log = false;

		int	state = download.getState();

		int	register_type	= REG_TYPE_NONE;

		String	register_reason;

		Random	random = new Random();
			/*
			 * Queued downloads are removed from the set to consider as we now have the "presence store"
			 * mechanism to ensure that there are a number of peers out there to provide torrent download
			 * if required. This has been done to avoid the large number of registrations that users with
			 * large numbers of queued torrents were getting.
			 */

		if (	state == Download.ST_DOWNLOADING 	||
				state == Download.ST_SEEDING 		||
				// state == Download.ST_QUEUED 		||
				download.isPaused()) {	// pause is a transitory state, don't dereg

			String[]	networks = download.getListAttribute(ta_networks);

			Torrent	torrent = download.getTorrent();

			if (torrent != null && networks != null) {

				boolean	public_net = false;

				for (int i=0;i<networks.length;i++) {

					if (networks[i].equalsIgnoreCase("Public")) {

						public_net	= true;

						break;
					}
				}

				if (public_net && !torrent.isPrivate()) {

					if (torrent.isDecentralised()) {

							// peer source not relevant for decentralised torrents

						register_type	= REG_TYPE_FULL;

						register_reason = "decentralised";

					} else {

						if (torrent.isDecentralisedBackupEnabled() || TEST_ALWAYS_TRACK) {

							String[]	sources = download.getListAttribute(ta_peer_sources);

							boolean	ok = false;

							if (sources != null) {

								for (int i=0;i<sources.length;i++) {

									if (sources[i].equalsIgnoreCase("DHT")) {

										ok	= true;

										break;
									}
								}
							}

							if (!( ok || TEST_ALWAYS_TRACK)) {

								register_reason = "decentralised peer source disabled";

							} else {
									// this will always be true since change to exclude queued...

								boolean	is_active =
											state == Download.ST_DOWNLOADING ||
											state == Download.ST_SEEDING ||
											download.isPaused();

								if (is_active) {

									register_type = REG_TYPE_DERIVED;
								}

								if (torrent.isDecentralisedBackupRequested() || TEST_ALWAYS_TRACK) {

									register_type	= REG_TYPE_FULL;

									register_reason = TEST_ALWAYS_TRACK?"testing always track":"torrent requests decentralised tracking";

								} else if (track_normal_when_offline.getValue()) {

										// only track if torrent's tracker is not available

									if (is_active) {

										DownloadAnnounceResult result = download.getLastAnnounceResult();

										if (	result == null ||
												result.getResponseType() == DownloadAnnounceResult.RT_ERROR ||
												TorrentUtils.isDecentralised(result.getURL())) {

											register_type	= REG_TYPE_FULL;

											register_reason = "tracker unavailable (announce)";

										} else {

											register_reason = "tracker available (announce: " + result.getURL() + ")";
										}
									} else {

										DownloadScrapeResult result = download.getLastScrapeResult();

										if (	result == null ||
												result.getResponseType() == DownloadScrapeResult.RT_ERROR ||
												TorrentUtils.isDecentralised(result.getURL())) {

											register_type	= REG_TYPE_FULL;

											register_reason = "tracker unavailable (scrape)";

										} else {

											register_reason = "tracker available (scrape: " + result.getURL() + ")";
										}
									}

									if (register_type != REG_TYPE_FULL && track_limited_when_online.getValue()) {

										Boolean	existing = (Boolean)limited_online_tracking.get(download);

										boolean	track_it = false;

										if (existing != null) {

											track_it = existing.booleanValue();

										} else {

											DownloadScrapeResult result = download.getLastScrapeResult();

											if (	result != null&&
													result.getResponseType() == DownloadScrapeResult.RT_SUCCESS) {

												int	seeds 		= result.getSeedCount();
												int leechers	= result.getNonSeedCount();

												int	swarm_size = seeds + leechers;

												if (swarm_size <= LIMITED_TRACK_SIZE) {

													track_it = true;

												} else {

													track_it = random.nextInt(swarm_size) < LIMITED_TRACK_SIZE;
												}

												if (track_it) {

													limited_online_tracking.put( download, Boolean.valueOf(track_it));
												}
											}
										}

										if (track_it) {

											register_type	= REG_TYPE_FULL;

											register_reason = "limited online tracking";
										}
									}
								} else {
									register_type	= REG_TYPE_FULL;

									register_reason = "peer source enabled";
								}
							}
						} else {

							register_reason = "decentralised backup disabled for the torrent";
						}
					}
				} else {

					register_reason = "not public";
				}
			} else {

				register_reason = "torrent is broken";
			}

			if (register_type == REG_TYPE_DERIVED) {

				if (register_reason.length() == 0) {

					register_reason = "derived";

				} else {

					register_reason = "derived (overriding ' " + register_reason + "')";
				}
			}
		} else if (	state == Download.ST_STOPPED ||
					state == Download.ST_ERROR) {

			register_reason	= "not running";

			skip_log	= true;

		} else if (	state == Download.ST_QUEUED) {

				// leave in whatever state it current is (reg or not reg) to avoid thrashing
				// registrations when seeding rules are start/queueing downloads

			register_reason	= "";

		} else {

			register_reason	= "";
		}

		if (register_reason.length() > 0) {

			try {
				thisMon.enter();

				int[] run_data = runningDownloads.get(download);

				if (register_type != REG_TYPE_NONE) {

					if (run_data == null) {

						log( download,	"Monitoring '" + download.getName() + "': " + register_reason);

						int[] cache = run_data_cache.remove(download);

						if (cache == null) {

							runningDownloads.put( download, new int[]{ register_type, 0, 0, 0, 0 });

						} else {

							cache[0] = register_type;

							runningDownloads.put(download, cache);
						}

						queryMap.put( download, new Long( SystemTime.getCurrentTime()));

					} else {

						Integer	existing_type = run_data[0];

						if (	existing_type.intValue() == REG_TYPE_DERIVED &&
								register_type == REG_TYPE_FULL) {

								// upgrade

							run_data[0] = register_type;
						}
					}
				} else {

					if (run_data  != null) {

						if (!skip_log) {

							log( download, "Not monitoring: "	+ register_reason);
						}

						runningDownloads.remove(download);

						run_data_cache.put(download, run_data);

							// add back to interesting downloads for monitoring

						interestingDownloads.put(
								download,
								new Long( 	pluginInterface.getUtilities().getCurrentSystemTime() +
											INTERESTING_INIT_MIN_OTHERS ));

					} else {

						if (first_time && !skip_log) {

							log( download, "Not monitoring: "	+ register_reason);
						}
					}
				}
			} finally {

				thisMon.exit();
			}
		}
	}

	protected void processRegistrations(boolean fullProcessing) {
		
		int	tcpPort = pluginInterface.getPluginconfig().getIntParameter("TCP.Listen.Port");
 		String portOverride = COConfigurationManager.getStringParameter("TCP.Listen.Port.Override");
  		if (!portOverride.equals("")) {
  			try {
  				tcpPort	= Integer.parseInt(portOverride);
  			} catch (Throwable e) {
  			}
  		}
  		
  		if (tcpPort == 0) {
  			log.log("TCP port=0, registration not performed");
  			return;
  		}
  		
	    String overrideIps = COConfigurationManager.getStringParameter("Override Ip", "");
	    String overrideIp	= null;
	  	if (overrideIps.length() > 0) {
			// gotta select an appropriate override based on network type
	  		StringTokenizer	tok = new StringTokenizer(overrideIps, ";");
	  		while (tok.hasMoreTokens()) {
	  			String	thisAddress = (String)tok.nextToken().trim();
	  			if (thisAddress.length() > 0) {
	  				String	cat = AENetworkClassifier.categoriseAddress(thisAddress);
	  				if (cat == AENetworkClassifier.AT_PUBLIC) {
	  					overrideIp	= thisAddress;
	  					break;
	  				}
	  			}
			}
		}
	  	
  	    if (overrideIp != null) {
    		try {
    			overrideIp = PRHelpers.DNSToIPAddress(overrideIp);
    		} catch (UnknownHostException e) {
    			log.log("    Can't resolve IP override '" + overrideIp + "'");
    			overrideIp	= null;
    		}
    	}
  	    
		ArrayList<Download>	rdls;
		try {
			thisMon.enter();
			rdls = new ArrayList<Download>(runningDownloads.keySet());
		} finally {
			thisMon.exit();
		}
		
		long now = SystemTime.getCurrentTime();

		if (fullProcessing) {
			Iterator<Download>	rds_it = rdls.iterator();
			List<Object[]> interesting = new ArrayList<Object[]>();
			while (rds_it.hasNext()) {
				Download dl = rds_it.next();
				int	regType = REG_TYPE_NONE;
				try {
					thisMon.enter();
					int[] run_data = runningDownloads.get(dl);
					if (run_data != null) {
						regType = run_data[0];
					}
				} finally {
					thisMon.exit();
				}
		  		if (regType == REG_TYPE_NONE) {
		  			continue;
		  		}
		  		long metric = getDerivedTrackMetric(dl);
		  		interesting.add(new Object[]{ dl, new Long( metric )});
			}
			
			Collections.sort(
				interesting,
				new Comparator<Object[]>() {
					public int  compare(Object[] entry1, Object[] entry2)  {
						long res = ((Long) entry2[1]).longValue() - ((Long) entry1[1]).longValue();
						if (res < 0)
							return (-1);
						else if (res > 0)
							return (1);
						else
							return (0);
					}
				});
			Iterator<Object[]> it = interesting.iterator();
			int	num = 0;
			while (it.hasNext()) {
				Object[] entry = it.next();
				Download	dl 		= (Download)entry[0];
				long		metric	= ((Long)entry[1]).longValue();
				num++;
				if (metric > 0) {
					if (num <= DL_DERIVED_MIN_TRACK) {
							// leave as is
					} else if (num <= DL_DERIVED_MAX_TRACK) {
							// scale metric between limits
						metric = (metric * ( DL_DERIVED_MAX_TRACK - num )) / ( DL_DERIVED_MAX_TRACK - DL_DERIVED_MIN_TRACK);
					} else {
						metric = 0;
					}
				}
				if (metric > 0) {
					dl.setUserData(DL_DERIVED_METRIC_KEY, new Long( metric));
				} else {
					dl.setUserData(DL_DERIVED_METRIC_KEY, null);
				}
			}
		}
		
		Iterator<Download>	rdls_it = rdls.iterator();
		// first off do any puts
		while (rdls_it.hasNext()) {
			Download	dl = rdls_it.next();
			int	reg_type = REG_TYPE_NONE;
			try {
				thisMon.enter();
				int[] run_data = runningDownloads.get(dl);
				if (run_data != null) {
					reg_type = run_data[0];
				}
			} finally {
				thisMon.exit();
			}
	  		if (reg_type == REG_TYPE_NONE) {
	  			continue;
	  		}
	  		
  			// format is [ip_override:]tcp_port[;CI...][;udp_port]
	  	    String	value_to_put = overrideIp==null?"":(overrideIp+":");
	  	    value_to_put += tcpPort;
	  	    String put_flags = ";";
	  	    if (NetworkManager.REQUIRE_CRYPTO_HANDSHAKE) {
	  	    	put_flags += "C";
	  	    }
	  	    String[] networks = dl.getListAttribute(ta_networks);
	  	    boolean	i2p = false;
	  	    if (networks != null) {
	  	    	for (String net: networks) {
	  	    		if (net == AENetworkClassifier.AT_I2P) {
	  	    			if (I2PHelpers.isI2PInstalled()) {
	  	    				put_flags += "I";
	  	    			}
	  	    			i2p = true;
	  	    			break;
	  	    		}
	  	    	}
	  	    }
	  	    if (put_flags.length() > 1) {
	  	    	value_to_put += put_flags;
	  	    }
			int	udp_port = pluginInterface.getPluginconfig().getIntParameter("UDP.Listen.Port");
			int	dht_port = dht.getLocalAddress().getAddress().getPort();
			if (udp_port != dht_port) {
				value_to_put += ";" + udp_port;
			}
			putDetails putDetails = new putDetails(value_to_put, overrideIp, tcpPort, udp_port, i2p);
			byte dhtFlags = isComplete(dl)?DHTPlugin.FLAG_SEEDING:DHTPlugin.FLAG_DOWNLOADING;
			RegistrationDetails	registration = (RegistrationDetails)registered_downloads.get(dl);
			boolean	doIt = false;
			if (registration == null) {
				log(dl, "Registering download as " + (dhtFlags == DHTPlugin.FLAG_SEEDING?"Seeding":"Downloading"));
				registration = new RegistrationDetails(dl, reg_type, putDetails, dhtFlags);
				registered_downloads.put(dl, registration);
				doIt = true;
			} else {
				boolean	targetsChanged = false;
				if (fullProcessing) {
					targetsChanged = registration.updateTargets(dl, reg_type);
				}
				if (targetsChanged ||
						registration.getFlags() != dhtFlags ||
						!registration.getPutDetails().sameAs(putDetails)) {
					log(dl, (registration == null ? "Registering" : "Re-registering") + " download as " + (dhtFlags == DHTPlugin.FLAG_SEEDING ? "Seeding" : "Downloading"));
					registration.update(putDetails, dhtFlags);
					doIt = true;
				}
			}
			
			if (doIt) {
				try {
					thisMon.enter();
					queryMap.put(dl, new Long(now));
				} finally {
					thisMon.exit();
				}
				trackerPut(dl, registration);
			}
		}
		
		// second any removals
		Iterator<Map.Entry<Download,RegistrationDetails>> rd_it = registered_downloads.entrySet().iterator();
		while (rd_it.hasNext()) {
			Map.Entry<Download,RegistrationDetails>	entry = rd_it.next();
			final Download	dl = entry.getKey();
			boolean	unregister;
			try {
				thisMon.enter();
				unregister = !runningDownloads.containsKey(dl);
			} finally {
				thisMon.exit();
			}
			if (unregister) {
				log(dl, "Unregistering download");
				rd_it.remove();
				try {
					thisMon.enter();
					queryMap.remove(dl);
				} finally {
					thisMon.exit();
				}
				trackerRemove(dl, entry.getValue());
			}
		}
		
		// lastly gets
		rdls_it = rdls.iterator();
		while (rdls_it.hasNext()) {
			final Download	dl = (Download)rdls_it.next();
			Long	next_time;
			try {
				thisMon.enter();
				next_time = (Long)queryMap.get(dl);
			} finally {
				thisMon.exit();
			}
			if (next_time != null && now >= next_time.longValue()) {
				int	reg_type = REG_TYPE_NONE;
				try {
					thisMon.enter();
					queryMap.remove(dl);
					int[] run_data = runningDownloads.get(dl);
					if (run_data != null) {
						reg_type = run_data[0];
					}
				} finally {
					thisMon.exit();
				}
				final long	start = SystemTime.getCurrentTime();
				// if we're already connected to > NUM_WANT peers then don't bother with the main announce
				PeerManager	pm = dl.getPeerManager();
				// don't query if this download already has an active DHT operation
				boolean	skip = isActive(dl) || reg_type == REG_TYPE_NONE;
				if (skip) {
					log(dl, "Deferring announce as activity outstanding");
				}
				RegistrationDetails	registration = (RegistrationDetails)registered_downloads.get(dl);
				if (registration == null) {
					Debug.out("Inconsistent, registration should be non-null");
					continue;
				}
				boolean	derived_only = false;
				if (pm != null && !skip) {
					int	con = pm.getStats().getConnectedLeechers() + pm.getStats().getConnectedSeeds();
					derived_only = con >= NUM_WANT;
				}
				
				if (!skip) {
					skip = trackerGet(dl, registration, derived_only) == 0;
				}
				
				// if we didn't kick off a get then we have to reschedule here as normally
				// the get operation will do the rescheduling when it receives a result
				if (skip) {
					try {
						thisMon.enter();
						if (runningDownloads.containsKey( dl)) {
							// use "min" here as we're just deferring it
							queryMap.put(dl, new Long(start + ANNOUNCE_MIN_DEFAULT));
						}
					} finally {
						thisMon.exit();
					}
				}
			}
		}
	}

	protected long getDerivedTrackMetric(
		Download		download) {
			// metric between -100 and + 100. Note that all -ve mean 'don't do it'
			// they're just indicating different reasons

		Torrent t = download.getTorrent();

		if (t == null) {

			return (-100);
		}

		if (t.getSize() < 10*1024*1024) {

			return (-99);
		}

		DownloadAnnounceResult announce = download.getLastAnnounceResult();

		if (	announce == null ||
				announce.getResponseType() != DownloadAnnounceResult.RT_SUCCESS) {

			return (-98);
		}

		DownloadScrapeResult scrape = download.getLastScrapeResult();

		if (	scrape == null ||
				scrape.getResponseType() != DownloadScrapeResult.RT_SUCCESS) {

			return (-97);
		}

		int leechers 	= scrape.getNonSeedCount();
		// int seeds		= scrape.getSeedCount();

		int	total = leechers;	// parg - changed to just use leecher count rather than seeds+leechers

		if (total >= 2000) {

			return (100);

		} else if (total <= 200) {

			return (0);

		} else {

			return (( total - 200 ) / 4);
		}
	}

	protected void trackerPut(
		final Download			download,
		RegistrationDetails		details) {
		final 	long	start = SystemTime.getCurrentTime();

		trackerTarget[] targets = details.getTargets(true);

		byte flags = details.getFlags();

		for (int i=0;i<targets.length;i++) {

			final trackerTarget target = targets[i];

			int	target_type = target.getType();

		 	    // don't let a put block an announce as we don't want to be waiting for
		  	    // this at start of day to get a torrent running

		  	    // increaseActive(dl);

			String	encoded = details.getPutDetails().getEncoded();

			byte[]	encoded_bytes = encoded.getBytes();

			DHTPluginValue existing = dht.getLocalValue( target.getHash());

			if (	existing != null &&
					existing.getFlags() == flags &&
					Arrays.equals(existing.getValue(), encoded_bytes)) {

					// already present, no point in updating

				continue;
			}

			if (disable_put) {

				if (target_type == REG_TYPE_FULL) {

					log( download, "Registration of '" + target.getDesc() + "' skipped as disabled due to use of SOCKS proxy");
				}
			} else if (download.getFlag( Download.FLAG_METADATA_DOWNLOAD)) {

				log( download, "Registration of '" + target.getDesc() + "' skipped as metadata download");

			} else if (target_type == REG_TYPE_DERIVED && dht.isSleeping()) {

				log( download, "Registration of '" + target.getDesc() + "' skipped as sleeping");

			} else {

				dht.put(
					target.getHash(),
					"Tracker reg of '" + download.getName() + "'" + target.getDesc() + " -> " + encoded,
					encoded_bytes,
					flags,
					false,
					new DHTPluginOperationListener() {
						public boolean diversified() {
							return (true);
						}

						public void starts(
							byte[] 				key ) {
						}

						public void valueRead(
							DHTPluginContact	originator,
							DHTPluginValue		value) {
						}

						public void valueWritten(
							DHTPluginContact	target,
							DHTPluginValue		value) {
						}

						public void complete(
							byte[]	key,
							boolean	timeout_occurred) {
							if (target.getType() == REG_TYPE_FULL) {

								log( 	download,
										"Registration of '" + target.getDesc() + "' completed (elapsed="	+ TimeFormatter.formatColonMillis((SystemTime.getCurrentTime() - start)) + ")");
							}

								// decreaseActive(dl);
						}
					});
			}
		}
	}

	protected int trackerGet(
		final Download				download,
		final RegistrationDetails	details,
		final boolean				derived_only) {
		
		final long		start = SystemTime.getCurrentTime();
		final Torrent	torrent = download.getTorrent();
		final URL		url_to_report = torrent.isDecentralised()?torrent.getAnnounceURL():DEFAULT_URL;
		trackerTarget[]	targets = details.getTargets(false);
		final long[]	max_retry = { 0 };

		final boolean do_alt =
			alt_lookup_handler != null &&
			(!(download.getFlag( Download.FLAG_LOW_NOISE) || download.getFlag( Download.FLAG_LIGHT_WEIGHT)));

		int	num_done = 0;

		for (int i=0;i<targets.length;i++) {
			final trackerTarget target = targets[i];
			int	target_type = target.getType();
			if (target_type == REG_TYPE_FULL && derived_only) {
				continue;
			} else if (target_type == REG_TYPE_DERIVED && dht.isSleeping()) {
				continue;
			}
			increaseActive(download);
			num_done++;
			final boolean isComplete = isComplete(download);

			dht.get(target.getHash(),
					"Tracker announce for '" + download.getName() + "'" + target.getDesc(),
					isComplete?DHTPlugin.FLAG_SEEDING:DHTPlugin.FLAG_DOWNLOADING,
					NUM_WANT,
					target_type==REG_TYPE_FULL?ANNOUNCE_TIMEOUT:ANNOUNCE_DERIVED_TIMEOUT,
					false, false,
					new DHTPluginOperationListener() {
						List<String>	addresses 	= new ArrayList<String>();
						List<Integer>	ports		= new ArrayList<Integer>();
						List<Integer>	udp_ports	= new ArrayList<Integer>();
						List<Boolean>	is_seeds	= new ArrayList<Boolean>();
						List<String>	flags		= new ArrayList<String>();

						int		seed_count;
						int		leecher_count;

						int		i2p_seed_count;
						int 	i2p_leecher_count;

						volatile boolean	complete;

						{
							if (do_alt) {
								alt_lookup_handler.get(
										target.getHash(),
										isComplete,
										new DHTTrackerPluginAlt.LookupListener() {
											public void foundPeer(
												InetSocketAddress	address) {
												alternativePeerRead(address);
											}

											public boolean isComplete() {
												return (complete && addresses.size() > 5);
											}

											public void completed() {
											}
										}
								);
							}
						}

						public boolean diversified() {
							return (true);
						}

						public void starts(byte[] key) {
						}

						private void alternativePeerRead(InetSocketAddress peer) {
							boolean	try_injection = false;
							
							synchronized(this) {
								if (complete) {
									try_injection = addresses.size() < 5;
								} else {
									try {
										addresses.add( peer.getAddress().getHostAddress());
										ports.add( peer.getPort());
										udp_ports.add(0);
										flags.add(null);
										
										is_seeds.add(false);
										leecher_count++;
									} catch (Throwable e) {
									}
								}
							}
							if (try_injection) {
								PeerManager pm = download.getPeerManager();
								if (pm != null) {
									pm.peerDiscovered(
										PEPeerSource.PS_DHT,
										peer.getAddress().getHostAddress(),
										peer.getPort(),
										0,
										NetworkManager.getCryptoRequired(NetworkManager.CRYPTO_OVERRIDE_NONE));
								}
							}
						}

						public void valueRead(
							DHTPluginContact	originator,
							DHTPluginValue		value) {
							synchronized(this) {
								if (complete) {
									return;
								}
								try {
									String[]	tokens = new String(value.getValue()).split(";");
									String	tcp_part = tokens[0].trim();
									int	sep = tcp_part.indexOf(':');
									String	ip_str		= null;
									String	tcp_port_str;
									if (sep == -1) {
										tcp_port_str = tcp_part;
									} else {
										ip_str 			= tcp_part.substring(0, sep);
										tcp_port_str	= tcp_part.substring(sep+1);
									}
									int	tcp_port = Integer.parseInt(tcp_port_str);
									if (tcp_port > 0 && tcp_port < 65536) {
										String	flag_str	= null;
										int		udp_port	= -1;
										boolean	has_i2p = false;
										try {
											for (int i=1;i<tokens.length;i++) {
												String	token = tokens[i].trim();
												if (token.length() > 0) {
													if (Character.isDigit( token.charAt( 0))) {
														udp_port = Integer.parseInt(token);
														if (udp_port <= 0 || udp_port >=65536) {
															udp_port = -1;
														}
													} else {
														flag_str = token;
														if (flag_str.contains("I")) {
															has_i2p = true;
														}
													}
												}
											}
										} catch (Throwable e) {
										}
										addresses.add(
												ip_str==null?originator.getAddress().getAddress().getHostAddress():ip_str);
										ports.add(new Integer( tcp_port));
										udp_ports.add(new Integer( udp_port==-1?originator.getAddress().getPort():udp_port));
										flags.add(flag_str);
										if ((value.getFlags() & DHTPlugin.FLAG_DOWNLOADING ) == 1) {
											leecher_count++;
											is_seeds.add(Boolean.FALSE);
											if (has_i2p) {
												i2p_leecher_count++;
											}
										} else {
											is_seeds.add(Boolean.TRUE);
											seed_count++;
											if (has_i2p) {
												i2p_seed_count++;
											}
										}
									}
								} catch (Throwable e) {
									// in case we get crap back (someone spamming the DHT) just
									// silently ignore
								}
							}
						}

						public void valueWritten(
							DHTPluginContact	target,
							DHTPluginValue		value) {
						}

						public void complete(
							byte[]	key,
							boolean	timeout_occurred) {
							synchronized(this) {
								if (complete) {
									return;
								}
								complete = true;
							}
							if (	target.getType() == REG_TYPE_FULL ||
									(	target.getType() == REG_TYPE_DERIVED &&
										seed_count + leecher_count > 1 )) {
								log( 	download,
										"Get of '" + target.getDesc() + "' completed (elapsed=" + TimeFormatter.formatColonMillis(SystemTime.getCurrentTime() - start)
												+ "), addresses=" + addresses.size() + ", seeds="
												+ seed_count + ", leechers=" + leecher_count);
							}
							decreaseActive(download);
							int	peers_found = addresses.size();
							List<DownloadAnnounceResultPeer>	peers_for_announce = new ArrayList<DownloadAnnounceResultPeer>();
							
							// scale min and max based on number of active torrents
							// we don't want more than a few announces a minute
							int	announce_per_min = 4;
							int	num_active = queryMap.size();
							int	announce_min = Math.max(ANNOUNCE_MIN_DEFAULT, ( num_active / announce_per_min )*60*1000);
							int	announce_max = derived_only?ANNOUNCE_MAX_DERIVED_ONLY:ANNOUNCE_MAX;
							announce_min = Math.min(announce_min, announce_max);
							current_announce_interval = announce_min;
							final long	retry = announce_min + peers_found*(long)(announce_max-announce_min)/NUM_WANT;
							int download_state = download.getState();
							boolean	we_are_seeding = download_state == Download.ST_SEEDING;
							
							try {
								thisMon.enter();
								int[] run_data = runningDownloads.get(download);

								if (run_data != null) {

									boolean full = target.getType() == REG_TYPE_FULL;

									int peer_count = we_are_seeding?leecher_count:(seed_count+leecher_count);

									run_data[1] = full?seed_count:Math.max( run_data[1], seed_count);
									run_data[2]	= full?leecher_count:Math.max( run_data[2], leecher_count);
									run_data[3] = full?peer_count:Math.max( run_data[3], peer_count);

									run_data[4] = (int)(SystemTime.getCurrentTime()/1000);

									long	absolute_retry = SystemTime.getCurrentTime() + retry;
									if (absolute_retry > max_retry[0]) {
										// only update next query time if none set yet
										// or we appear to have set the existing one. If we
										// don't do this then we'll overwrite any rescheduled
										// announces
										Long	existing = (Long)queryMap.get(download);
										if (	existing == null ||
												existing.longValue() == max_retry[0]) {
											max_retry[0] = absolute_retry;
											queryMap.put(download, new Long( absolute_retry));
										}
									}
								}
							} finally {
								thisMon.exit();
							}
							putDetails put_details = details.getPutDetails();
							String	ext_address = put_details.getIPOverride();
							if (ext_address == null) {
								ext_address = dht.getLocalAddress().getAddress().getAddress().getHostAddress();
							}
							if (put_details.hasI2P()) {
								if (we_are_seeding) {
									if (i2p_seed_count > 0) {
										i2p_seed_count--;
									}
								} else {
									if (i2p_leecher_count > 0) {
										i2p_leecher_count--;
									}
								}
							}
							if (i2p_seed_count + i2p_leecher_count > 0) {
								download.setUserData( DOWNLOAD_USER_DATA_I2P_SCRAPE_KEY, new int[]{ i2p_seed_count,  i2p_leecher_count });
							} else {
								download.setUserData(DOWNLOAD_USER_DATA_I2P_SCRAPE_KEY, null);
							}
							for (int i=0;i<addresses.size();i++) {
								// when we are seeding ignore seeds
								if (we_are_seeding && ((Boolean)is_seeds.get(i)).booleanValue()) {
									continue;
								}
								// remove ourselves
								String	ip = (String)addresses.get(i);
								if (ip.equals( ext_address)) {
									if (((Integer)ports.get(i)).intValue() == put_details.getTCPPort() &&
										 ((Integer)udp_ports.get(i)).intValue() == put_details.getUDPPort()) {
										continue;
									}
								}

								final int f_i = i;

								peers_for_announce.add(new DownloadAnnounceResultPeer() {
										public String getSource() {
											return (PEPeerSource.PS_DHT);
										}

										public String getAddress() {
											return ((String)addresses.get(f_i));
										}

										public int getPort() {
											return (((Integer)ports.get(f_i)).intValue());
										}

										public int getUDPPort() {
											return (((Integer)udp_ports.get(f_i)).intValue());
										}

										public byte[] getPeerID() {
											return (null);
										}

										public short getProtocol() {
											String	flag = (String)flags.get(f_i);
											short protocol = PROTOCOL_NORMAL;
											if (flag != null) {
												if (flag.contains("C")) {
													protocol = PROTOCOL_CRYPT;
												}
											}
											return (protocol);
										}
									});

							}

							if (target.getType() == REG_TYPE_DERIVED && peers_for_announce.size() > 0) {
								PeerManager pm = download.getPeerManager();
								if (pm != null) {
									// try some limited direct injection
									List<DownloadAnnounceResultPeer>	temp = new ArrayList<DownloadAnnounceResultPeer>(peers_for_announce);
									Random rand = new Random();
									for (int i=0;i<DIRECT_INJECT_PEER_MAX && temp.size() > 0; i++) {
										DownloadAnnounceResultPeer peer = temp.remove( rand.nextInt( temp.size()));
										log( download, "Injecting derived peer " + peer.getAddress() + " into " + download.getName());
										Map<Object,Object>	user_data = new HashMap<Object,Object>();
										user_data.put( Peer.PR_PRIORITY_CONNECTION, Boolean.TRUE);
										pm.addPeer(
												peer.getAddress(),
												peer.getPort(),
												peer.getUDPPort(),
												peer.getProtocol() == DownloadAnnounceResultPeer.PROTOCOL_CRYPT,
												user_data);
									}
								}
							}

							if (	download_state == Download.ST_DOWNLOADING ||
									download_state == Download.ST_SEEDING) {

								final DownloadAnnounceResultPeer[] peers = new DownloadAnnounceResultPeer[peers_for_announce.size()];
								peers_for_announce.toArray(peers);

								download.setAnnounceResult(
										new DownloadAnnounceResult() {
											public Download getDownload() {
												return (download);
											}

											public int getResponseType() {
												return (DownloadAnnounceResult.RT_SUCCESS);
											}

											public int getReportedPeerCount() {
												return ( peers.length);
											}

											public int getSeedCount() {
												return (seed_count);
											}

											public int getNonSeedCount() {
												return (leecher_count);
											}

											public String getError() {
												return (null);
											}

											public URL getURL() {
												return (url_to_report);
											}

											public DownloadAnnounceResultPeer[] getPeers() {
												return (peers);
											}

											public long getTimeToWait() {
												return (retry/1000);
											}

											public Map
											getExtensions() {
												return (null);
											}
										});
							}

							// only inject the scrape result if the torrent is decentralised. If we do this for
							// "normal" torrents then it can have unwanted side-effects, such as stopping the torrent
							// due to ignore rules if there are no downloaders in the DHT - bthub backup, for example,
							// isn't scrapable...

							// hmm, ok, try being a bit more relaxed about this, inject the scrape if
							// we have any peers.

							boolean	inject_scrape = leecher_count > 0;

							DownloadScrapeResult result = download.getLastScrapeResult();

							if (	result == null ||
									result.getResponseType() == DownloadScrapeResult.RT_ERROR) {

							} else {

								// if the currently reported values are the same as the
								// ones we previously injected then overwrite them
								// note that we can't test the URL to see if we originated
								// the scrape values as this gets replaced when a normal
								// scrape fails :(

								synchronized(scrape_injection_map) {

									int[]	prev = (int[])scrape_injection_map.get(download);

									if (	prev != null &&
											prev[0] == result.getSeedCount() &&
											prev[1] == result.getNonSeedCount()) {

										inject_scrape	= true;
									}
								}
							}

							if (torrent.isDecentralised() || inject_scrape) {
								// make sure that the injected scrape values are consistent
								// with our currently connected peers
								PeerManager	pm = download.getPeerManager();
								int	local_seeds 	= 0;
								int	local_leechers 	= 0;
								if (pm != null) {
									Peer[]	dl_peers = pm.getPeers();
									for (int i=0;i<dl_peers.length;i++) {
										Peer	dl_peer = dl_peers[i];
										if (dl_peer.getPercentDoneInThousandNotation() == 1000) {
											local_seeds++;
										} else {
											local_leechers++;
										}
									}
								}
								final int f_adj_seeds 		= Math.max(seed_count, local_seeds);
								final int f_adj_leechers	= Math.max(leecher_count, local_leechers);
								synchronized(scrape_injection_map) {
									scrape_injection_map.put( download, new int[]{ f_adj_seeds, f_adj_leechers });
								}
								try {
									thisMon.enter();
									int[] run_data = runningDownloads.get(download);
									if (run_data == null) {
										run_data = run_data_cache.get(download);
									}
									if (run_data != null) {
										run_data[1] = f_adj_seeds;
										run_data[2]	= f_adj_leechers;
										run_data[4] = (int)(SystemTime.getCurrentTime()/1000);
									}
								} finally {
									thisMon.exit();
								}

								download.setScrapeResult(
									new DownloadScrapeResult() {
										public Download getDownload() {
											return (download);
										}

										public int getResponseType() {
											return (RT_SUCCESS);
										}

										public int getSeedCount() {
											return (f_adj_seeds);
										}

										public int getNonSeedCount() {
											return (f_adj_leechers);
										}

										public long getScrapeStartTime() {
											return (start);
										}

										public void setNextScrapeStartTime(
											long nextScrapeStartTime) {

										}
										public long getNextScrapeStartTime() {
											return (SystemTime.getCurrentTime() + retry);
										}

										public String getStatus() {
											return ("OK");
										}

										public URL getURL() {
											return (url_to_report);
										}
									});
								}
						}
					});
		}

		return (num_done);
	}

	protected boolean isComplete(
		Download	download) {
		if (Constants.DOWNLOAD_SOURCES_PRETEND_COMPLETE) {

			return (true);
		}

		boolean	is_complete = download.isComplete();

		if (is_complete) {

			PeerManager pm = download.getPeerManager();

			if (pm != null) {

				PEPeerManager core_pm = PluginCoreUtils.unwrap(pm);

				if (core_pm != null && core_pm.getHiddenBytes() > 0) {

					is_complete = false;
				}
			}
		}

		return (is_complete);
	}

	protected void trackerRemove(
		final Download			download,
		RegistrationDetails		details) {
		if (disable_put) {

			return;
		}

		if (download.getFlag( Download.FLAG_METADATA_DOWNLOAD)) {

			return;
		}

		final 	long	start = SystemTime.getCurrentTime();

		trackerTarget[] targets = details.getTargets(true);

		for (int i=0;i<targets.length;i++) {

			final trackerTarget target = targets[i];

			if (dht.hasLocalKey( target.getHash())) {

				increaseActive(download);

				dht.remove(
						target.getHash(),
						"Tracker dereg of '" + download.getName() + "'" + target.getDesc(),
						new DHTPluginOperationListener() {
							public boolean diversified() {
								return (true);
							}

							public void starts(
								byte[] 				key ) {
							}

							public void valueRead(
								DHTPluginContact	originator,
								DHTPluginValue		value) {
							}

							public void valueWritten(
								DHTPluginContact	target,
								DHTPluginValue		value) {
							}

							public void complete(
								byte[]	key,
								boolean	timeout_occurred) {
								if (target.getType() == REG_TYPE_FULL) {

									log( 	download,
											"Unregistration of '" + target.getDesc() + "' completed (elapsed="
												+ TimeFormatter.formatColonMillis(SystemTime.getCurrentTime() - start) + ")");
								}

								decreaseActive(download);
							}
						});
			}
		}
	}

	protected void trackerRemove(
		final Download			download,
		final trackerTarget 	target) {
		if (disable_put) {

			return;
		}

		if (download.getFlag( Download.FLAG_METADATA_DOWNLOAD)) {

			return;
		}

		final 	long	start = SystemTime.getCurrentTime();

		if (dht.hasLocalKey( target.getHash())) {

			increaseActive(download);

			dht.remove(
					target.getHash(),
					"Tracker dereg of '" + download.getName() + "'" + target.getDesc(),
					new DHTPluginOperationListener() {
						public boolean diversified() {
							return (true);
						}

						public void starts(
							byte[] 				key ) {
						}

						public void valueRead(
							DHTPluginContact	originator,
							DHTPluginValue		value) {
						}

						public void valueWritten(
							DHTPluginContact	target,
							DHTPluginValue		value) {
						}

						public void complete(
							byte[]	key,
							boolean	timeout_occurred) {
							if (target.getType() == REG_TYPE_FULL) {

								log( 	download,
										"Unregistration of '" + target.getDesc() + "' completed (elapsed="
										+ TimeFormatter.formatColonMillis(SystemTime.getCurrentTime() - start) + ")");
							}

							decreaseActive(download);
						}
					});
		}
	}

	protected void processNonRegistrations() {
		Download	ready_download 				= null;
		long		ready_download_next_check	= -1;

		long	now = pluginInterface.getUtilities().getCurrentSystemTime();

			// unfortunately getting scrape results can acquire locks and there is a vague
			// possibility of deadlock here, so pre-fetch the scrape results

		List<Download>	to_scrape = new ArrayList<Download>();

		try {
			thisMon.enter();

			Iterator<Download>	it = interestingDownloads.keySet().iterator();

			while (it.hasNext() && ready_download == null) {

				Download	download = it.next();

				Torrent	torrent = download.getTorrent();

				if (torrent == null) {

					continue;
				}

				int[] run_data = runningDownloads.get(download);

				if (run_data == null || run_data[0] == REG_TYPE_DERIVED) {

						// looks like we'll need the scrape below

					to_scrape.add(download);
				}
			}
		} finally {

			thisMon.exit();
		}

		Map<Download,DownloadScrapeResult> scrapes = new HashMap<Download,DownloadScrapeResult>();

		for (int i=0;i<to_scrape.size();i++) {

			Download	download = (Download)to_scrape.get(i);

			scrapes.put( download, download.getLastScrapeResult());
		}

		try {
			thisMon.enter();

			Iterator<Download>	it = interestingDownloads.keySet().iterator();

			while (it.hasNext() && ready_download == null) {

				Download	download = it.next();

				Torrent	torrent = download.getTorrent();

				if (torrent == null) {

					continue;
				}

				int[] run_data = runningDownloads.get(download);

				if (run_data == null || run_data[0] == REG_TYPE_DERIVED) {

					boolean	force =  torrent.wasCreatedByUs();

					if (!force) {

						if (interesting_pub_max > 0 && interesting_published > interesting_pub_max) {

							continue;
						}

						DownloadScrapeResult	scrape = (DownloadScrapeResult)scrapes.get(download);

						if (scrape == null) {

								// catch it next time round

							continue;
						}

						if (scrape.getSeedCount() + scrape.getNonSeedCount() > NUM_WANT) {

							continue;
						}
					}

					long	target = ((Long)interestingDownloads.get(download)).longValue();

					long check_period = TorrentUtils.isDecentralised( torrent.getAnnounceURL())?INTERESTING_DHT_CHECK_PERIOD:INTERESTING_CHECK_PERIOD;

					if (target <= now) {

						ready_download				= download;
						ready_download_next_check 	= now + check_period;

						interestingDownloads.put(download, new Long( ready_download_next_check));

					} else if (target - now > check_period) {

						interestingDownloads.put( download, new Long( now + (target%check_period)));
					}
				}
			}

		} finally {

			thisMon.exit();
		}

		if (ready_download != null) {

			final Download	f_ready_download = ready_download;

			final Torrent torrent = ready_download.getTorrent();

			if (ready_download.getFlag( Download.FLAG_METADATA_DOWNLOAD)) {

				try {
					thisMon.enter();

					interestingDownloads.remove(f_ready_download);

				} finally {

					thisMon.exit();
				}

			} else if (dht.isDiversified( torrent.getHash())) {

				// System.out.println("presence query for " + f_ready_download.getName() + "-> diversified pre start");

				try {
					thisMon.enter();

					interestingDownloads.remove(f_ready_download);

				} finally {

					thisMon.exit();
				}
			} else {

				//System.out.println("presence query for " + ready_download.getName());

				final long start 		= now;
				final long f_next_check = ready_download_next_check;

				dht.get(	torrent.getHash(),
							"Presence query for '" + ready_download.getName() + "'",
							(byte)0,
							INTERESTING_AVAIL_MAX,
							ANNOUNCE_TIMEOUT,
							false, false,
							new DHTPluginOperationListener() {
								private boolean diversified;
								private int 	leechers = 0;
								private int 	seeds	 = 0;

								private int 	i2p_leechers = 0;
								private int 	i2p_seeds	 = 0;

								public boolean diversified() {
									diversified	= true;

									return (false);
								}

								public void starts(
									byte[] 				key ) {
								}

								public void valueRead(
									DHTPluginContact	originator,
									DHTPluginValue		value) {
									boolean is_leecher = (value.getFlags() & DHTPlugin.FLAG_DOWNLOADING) == 1;

									if (is_leecher) {

										leechers++;

									} else {

										seeds++;
									}

									try {
										String[]	tokens = new String(value.getValue()).split(";");

										for (int i=1;i<tokens.length;i++) {

											String	token = tokens[i].trim();

											if (token.length() > 0) {

												if (!Character.isDigit( token.charAt( 0))) {

													String flag_str = token;

													if (flag_str.contains("I")) {

														if (is_leecher) {

															i2p_leechers++;

														} else {

															i2p_seeds++;
														}
													}
												}
											}

										}
									} catch (Throwable e) {

									}
								}

								public void valueWritten(
									DHTPluginContact	target,
									DHTPluginValue		value) {
								}

								public void complete(
									byte[]	key,
									boolean	timeout_occurred) {
									// System.out.println("    presence query for " + f_ready_download.getName() + "->" + total + "/div = " + diversified);

									int	total = leechers + seeds;

									log( torrent,
											"Presence query: availability="+
											(total==INTERESTING_AVAIL_MAX?(INTERESTING_AVAIL_MAX+"+"):(total+"")) + ",div=" + diversified +
											" (elapsed=" + TimeFormatter.formatColonMillis(SystemTime.getCurrentTime() - start) + ")");

									if (diversified) {

										try {
											thisMon.enter();

											interestingDownloads.remove(f_ready_download);

										} finally {

											thisMon.exit();
										}

									} else if (total < INTERESTING_AVAIL_MAX) {

											// once we're registered we don't need to process this download any
											// more unless it goes active and then inactive again

										try {
											thisMon.enter();

											interestingDownloads.remove(f_ready_download);

										} finally {

											thisMon.exit();
										}

										interesting_published++;

										if (!disable_put) {

											dht.put(
												torrent.getHash(),
												"Presence store '" + f_ready_download.getName() + "'",
												"0".getBytes(),	// port 0, no connections
												(byte)0,
												new DHTPluginOperationListener() {
													public boolean diversified() {
														return (true);
													}

													public void starts(
														byte[] 				key ) {
													}

													public void valueRead(
														DHTPluginContact	originator,
														DHTPluginValue		value) {
													}

													public void valueWritten(
														DHTPluginContact	target,
														DHTPluginValue		value) {
													}

													public void complete(
														byte[]	key,
														boolean	timeout_occurred) {
													}
												});
										}
									}


									try {
										thisMon.enter();

										int[] run_data = runningDownloads.get(f_ready_download);

										if (run_data == null) {

											run_data = run_data_cache.get(f_ready_download);
										}

										if (run_data != null) {

											if (total < INTERESTING_AVAIL_MAX) {

												run_data[1] = seeds;
												run_data[2]	= leechers;
												run_data[3] = total;

											} else {

												run_data[1] = Math.max(run_data[1], seeds);
												run_data[2] = Math.max(run_data[2], leechers);
											}

											run_data[4] = (int)(SystemTime.getCurrentTime()/1000);
										}
									} finally {

										thisMon.exit();
									}

									if (i2p_seeds + i2p_leechers > 0) {

										int[] details = (int[])f_ready_download.getUserData(DOWNLOAD_USER_DATA_I2P_SCRAPE_KEY);

										if (details == null) {

											details = new int[]{ i2p_seeds, i2p_leechers };

											f_ready_download.setUserData(DOWNLOAD_USER_DATA_I2P_SCRAPE_KEY,details);

										} else {

											details[0] = Math.max(details[0], i2p_seeds);
											details[1] = Math.max(details[1], i2p_leechers);
										}
									}


									f_ready_download.setScrapeResult(
										new DownloadScrapeResult() {
											public Download
											getDownload() {
												return (null);
											}

											public int getResponseType() {
												return (RT_SUCCESS);
											}

											public int getSeedCount() {
												return (seeds);
											}

											public int getNonSeedCount() {
												return (leechers);
											}

											public long getScrapeStartTime() {
												return ( SystemTime.getCurrentTime());
											}

											public void setNextScrapeStartTime(
												long nextScrapeStartTime) {
											}

											public long getNextScrapeStartTime() {
												return (f_next_check);
											}

											public String getStatus() {
												return ("OK");
											}

											public URL
											getURL() {
												URL	url_to_report = torrent.isDecentralised()?torrent.getAnnounceURL():DEFAULT_URL;

												return (url_to_report);
											}
										});
								}
							});

			}
		}
	}

	public void stateChanged(
		Download		download,
		int				old_state,
		int				new_state) {
		int	state = download.getState();
		try {
			thisMon.enter();
			if (state == Download.ST_DOWNLOADING ||
					state == Download.ST_SEEDING ||
					state == Download.ST_QUEUED) {	// included queued here for the mo to avoid lots
													// of thrash for torrents that flip a lot
				if (runningDownloads.containsKey(download)) {
					// force requery
					queryMap.put(download, new Long( SystemTime.getCurrentTime()));
				}
			}
		} finally {
			thisMon.exit();
		}
		
		// don't do anything if paused as we want things to just continue as they are (we would force an announce here otherwise)
		if (!download.isPaused()) {
			checkDownloadForRegistration(download, false);
		}
	}

	public void announceAll() {
		log.log("Announce-all requested");
		
		Log.d(TAG, "announceAll() is called...");
		Throwable t = new Throwable();
		t.printStackTrace();
		
		Long now = new Long(SystemTime.getCurrentTime());
		try {
			thisMon.enter();
			Iterator<Map.Entry<Download,Long>> it = queryMap.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<Download,Long>	entry = it.next();
				entry.setValue(now);
			}
		} finally {
			thisMon.exit();
		}
	}

	private void announce(Download download) {
		log.log("Announce requested for " + download.getName());
		
		Log.d(TAG, "announce() is called...");
		Throwable t = new Throwable();
		t.printStackTrace();
		
		try {
			thisMon.enter();
			queryMap.put(download, SystemTime.getCurrentTime());
		} finally {
			thisMon.exit();
		}
	}

	public void positionChanged(
		Download		download,
		int 			oldPosition,
		int 			newPosition) {
	}

	protected void configChanged() {
		Download[] downloads = pluginInterface.getDownloadManager().getDownloads();
		for (int i=0;i<downloads.length;i++) {
			checkDownloadForRegistration(downloads[i], false);
		}
	}

	/**
	 * This is used by the dhtscraper plugin
	 */

	public DownloadScrapeResult scrape(byte[] hash) {
		final int[]	seeds 		= {0};
		final int[] leechers 	= {0};

		final AESemaphore	sem = new AESemaphore("DHTTrackerPlugin:scrape");

		dht.get(hash,
				"Scrape for " + ByteFormatter.encodeString(hash ).substring( 0, 16),
				DHTPlugin.FLAG_DOWNLOADING,
				NUM_WANT,
				SCRAPE_TIMEOUT,
				false, false,
				new DHTPluginOperationListener() {
					public boolean diversified() {
						return (true);
					}

					public void starts(
						byte[] 				key ) {
					}

					public void valueRead(
						DHTPluginContact	originator,
						DHTPluginValue		value) {
						if ((value.getFlags() & DHTPlugin.FLAG_DOWNLOADING ) == 1) {

							leechers[0]++;

						} else {

							seeds[0]++;
						}
					}

					public void valueWritten(
						DHTPluginContact	target,
						DHTPluginValue		value) {
					}

					public void complete(
						byte[]	key,
						boolean	timeout_occurred) {
						sem.release();
					}
				});

		sem.reserve();

		return (
				new DownloadScrapeResult() {
					public Download
					getDownload() {
						return (null);
					}

					public int getResponseType() {
						return (RT_SUCCESS);
					}

					public int getSeedCount() {
						return (seeds[0]);
					}

					public int getNonSeedCount() {
						return (leechers[0]);
					}

					public long getScrapeStartTime() {
						return (0);
					}

					public void setNextScrapeStartTime(
						long nextScrapeStartTime) {
					}

					public long getNextScrapeStartTime() {
						return (0);
					}

					public String getStatus() {
						return ("OK");
					}

					public URL
					getURL() {
						return (null);
					}
				});
	}

	protected void increaseActive(
		Download		dl) {
		try {
			thisMon.enter();

			Integer	active_i = (Integer)in_progress.get(dl);

			int	active = active_i==null?0:active_i.intValue();

			in_progress.put(dl, new Integer( active+1));

		} finally {

			thisMon.exit();
		}
	}

	protected void decreaseActive(Download dl) {
		try {
			thisMon.enter();
			Integer	active_i = (Integer)in_progress.get(dl);
			if (active_i == null) {
				Debug.out("active count inconsistent");
			} else {
				int	active = active_i.intValue()-1;
				if (active == 0) {
					in_progress.remove(dl);
				} else {
					in_progress.put(dl, new Integer( active));
				}
			}
		} finally {
			thisMon.exit();
		}
	}

	protected boolean isActive(
		Download		dl) {
		try {
			thisMon.enter();
			return (in_progress.get(dl) != null);
		} finally {
			thisMon.exit();
		}
	}

	protected class
	RegistrationDetails
	{
		//private static final int DERIVED_ACTIVE_MIN_MILLIS	= 2*60*60*1000;
		private putDetails			put_details;
		private byte				flags;
		private trackerTarget[]		put_targets;
		private List<trackerTarget>	not_put_targets;
		//private long			derived_active_start	= -1;
		//private long			previous_metric;
		protected RegistrationDetails(
			Download			_download,
			int					_reg_type,
			putDetails			_put_details,
			byte				_flags) {
			put_details		= _put_details;
			flags			= _flags;
			getTrackerTargets(_download, _reg_type);
		}
		protected void update(
			putDetails		_put_details,
			byte			_flags) {
			put_details	= _put_details;
			flags		= _flags;
		}
		protected boolean updateTargets(
			Download			_download,
			int					_reg_type) {
			trackerTarget[]	old_put_targets = put_targets;
			getTrackerTargets(_download, _reg_type);
				// first remove any redundant entries
			for (int i=0;i<old_put_targets.length;i++) {
				boolean	found = false;
				byte[]	old_hash = old_put_targets[i].getHash();
				for (int j=0;j<put_targets.length;j++) {
					if (Arrays.equals( put_targets[j].getHash(), old_hash)) {
						found	= true;
						break;
					}
				}
				if (!found) {
					trackerRemove(_download, old_put_targets[i]);
				}
			}
				// now look to see if we have any new stuff
			boolean	changed = false;
			for (int i=0;i<put_targets.length;i++) {
				byte[]	new_hash = put_targets[i].getHash();
				boolean	found = false;
				for (int j=0;j<old_put_targets.length;j++) {
					if (Arrays.equals( old_put_targets[j].getHash(), new_hash)) {
						found = true;
						break;
					}
				}
				if (!found) {
					changed = true;
				}
			}
			return (changed);
		}
		
		protected putDetails
		getPutDetails() {
			return (put_details);
		}
		
		protected byte
		getFlags() {
			return (flags);
		}
		
		protected trackerTarget[]
		getTargets(
			boolean		for_put) {
			if (for_put || not_put_targets == null) {
				return (put_targets);
			} else {
				List<trackerTarget>	result = new ArrayList<trackerTarget>(Arrays.asList( put_targets));
				for (int i=0;i<not_put_targets.size()&& i < 2; i++) {
					trackerTarget target = (trackerTarget)not_put_targets.remove(0);
					not_put_targets.add(target);
					// System.out.println("Mixing in " + target.getDesc());
					result.add(target);
				}
				return ( (trackerTarget[])result.toArray(new trackerTarget[result.size()]));
			}
		}
		protected void
    	getTrackerTargets(
    		Download		download,
    		int				type )
    	{
    		byte[]	torrent_hash = download.getTorrent().getHash();
    		List<trackerTarget>	result = new ArrayList<trackerTarget>();
    		if (type == REG_TYPE_FULL) {
    			result.add(new trackerTarget( torrent_hash, REG_TYPE_FULL, ""));
    		}
 /*
    		if (ADD_ASN_DERIVED_TARGET) {
	    	    NetworkAdminASN net_asn = NetworkAdmin.getSingleton().getCurrentASN();
	    	    String	as 	= net_asn.getAS();
	    	    String	asn = net_asn.getASName();
	    		if (as.length() > 0 && asn.length() > 0) {
	    			String	key = "azderived:asn:" + as;
	    			try {
	    				byte[] asn_bytes = key.getBytes("UTF-8");
	    				byte[] key_bytes = new byte[torrent_hash.length + asn_bytes.length];
	    				System.arraycopy(torrent_hash, 0, key_bytes, 0, torrent_hash.length);
	    				System.arraycopy(asn_bytes, 0, key_bytes, torrent_hash.length, asn_bytes.length);
	    				result.add(new trackerTarget( key_bytes, REG_TYPE_DERIVED, asn + "/" + as));
	    			} catch (Throwable e) {
	    				Debug.printStackTrace(e);
	    			}
	    		}
    		}
    		if (ADD_NETPOS_DERIVED_TARGETS) {
	    		long	now = SystemTime.getMonotonousTime();
	    		boolean	do_it;
	       		Long	metric = (Long)download.getUserData(DL_DERIVED_METRIC_KEY);
	       		boolean	do_it_now = metric != null;
	    		if (derived_active_start >= 0 && now - derived_active_start <= DERIVED_ACTIVE_MIN_MILLIS) {
	    			do_it = true;
	    			if (metric == null) {
	    				metric = new Long(previous_metric);
	    			}
	    		} else {
	    			if (do_it_now) {
	    				do_it = true;
	    			} else {
	    				derived_active_start = -1;
	    				do_it = false;
	    			}
	    		}
	    		boolean	newly_active = false;
	    		if (do_it_now) {
	    			newly_active = derived_active_start == -1;
	    			derived_active_start = now;
	    		}
	    		List<trackerTarget>	skipped_targets = null;
	    		if (do_it) {
	    			previous_metric = metric.longValue();
		    		try {
		    			DHTNetworkPosition[] positions = getNetworkPositions();
		    			for (int i=0;i<positions.length;i++) {
		    				DHTNetworkPosition pos = positions[i];
		    				if (pos.getPositionType() == DHTNetworkPosition.POSITION_TYPE_VIVALDI_V2) {
		    					if (pos.isValid()) {
		    						List<Object[]>	derived_results = getVivaldiTargets( torrent_hash, pos.getLocation());
		    		    			int	num_to_add = metric.intValue() * derived_results.size() / 100;
		    		    			// System.out.println(download.getName() + ": metric=" + metric + ", adding=" + num_to_add);
		    						for (int j=0;j<derived_results.size();j++) {
		    							Object[] entry = derived_results.get(j);
		    							// int	distance = ((Integer)entry[0]).intValue();
		    							trackerTarget	target= (trackerTarget)entry[1];
		    							if (j < num_to_add) {
		    								result.add(target);
		    							} else {
		    								if (skipped_targets == null) {
		    									skipped_targets = new ArrayList<trackerTarget>();
		    								}
		    								skipped_targets.add(target);
		    							}
		    						}
		    					}
		    				}
		    			}
		    		} catch (Throwable e) {
		    			Debug.printStackTrace(e);
		    		}
	    		}
	    		not_put_targets = skipped_targets;
    		}
    		*/
	    	put_targets 	= result.toArray(new trackerTarget[result.size()]);
    	}
	}

	/*
	private DHTNetworkPosition[]
	getNetworkPositions() {
		DHTNetworkPosition[] res = current_network_positions;

		long	now = SystemTime.getMonotonousTime();

		if (	res == null ||
				now - last_net_pos_time >  30*60*1000) {

			res = current_network_positions = DHTNetworkPositionManager.getLocalPositions();

			last_net_pos_time = now;
		}

		return (res);
	}
	*/

	private void log(
		Download		download,
		String			str) {
		log(download.getTorrent(), str);
	}

	private void log(
		Torrent			torrent,
		String			str) {
		log.log(torrent, LoggerChannel.LT_INFORMATION, str);
	}

	public TrackerPeerSource getTrackerPeerSource(final Download download) {
		return (
			new TrackerPeerSourceAdapter() {
				private long	last_fixup;
				private boolean	updating;
				private int		status		= ST_UNKNOWN;
				private long	next_time	= -1;
				private int[]	runData;
				
				private void fixup() {
					long now = SystemTime.getMonotonousTime();
					if (now - last_fixup > 5*1000) {
						try {
							thisMon.enter();
							updating 	= false;
							next_time	= -1;
							runData = runningDownloads.get(download);
							if (runData != null) {
								if (in_progress.containsKey( download)) {
									updating = true;
								}
								status = initialisedSem.isReleasedForever()?ST_ONLINE:ST_STOPPED;
								Long l_next_time = queryMap.get(download);
								if (l_next_time != null) {
									next_time = l_next_time.longValue();
								}
							} else if (interestingDownloads.containsKey( download)) {
								status = ST_STOPPED;
							} else {
								int dl_state = download.getState();
								if (	dl_state == Download.ST_DOWNLOADING ||
										dl_state == Download.ST_SEEDING ||
										dl_state == Download.ST_QUEUED) {
									status = ST_DISABLED;
								} else {
									status = ST_STOPPED;
								}
							}
							if (runData == null) {
								runData = run_data_cache.get(download);
							}
						} finally {
							thisMon.exit();
						}
						
						String[]	sources = download.getListAttribute(ta_peer_sources);
						boolean	ok = false;
						if (sources != null) {
							for (int i=0;i<sources.length;i++) {
								if (sources[i].equalsIgnoreCase("DHT")) {
									ok	= true;
									break;
								}
							}
						}
						
						if (!ok) {
							status = ST_DISABLED;
						}
						last_fixup = now;
					}
				}
				
				public int getType() {
					return (TP_DHT);
				}
				public String getName() {
					return ("DHT: " + model.getStatus().getText());
				}
				public int getStatus() {
					fixup();
					return (status);
				}
				public int getSeedCount() {
					fixup();
					if (runData == null) {
						return (-1);
					}
					return (runData[1]);
				}
				public int getLeecherCount() {
					fixup();
					if (runData == null) {
						return (-1);
					}
					return (runData[2]);
				}
				
				public int getPeers() {
					fixup();
					if (runData == null) {
						return (-1);
					}
					return (runData[3]);
				}
				
				public int getLastUpdate() {
					fixup();
					if (runData == null) {
						return (0);
					}
					return (runData[4]);
				}
				
				public int getSecondsToUpdate() {
					fixup();
					if (next_time < 0) {
						return (-1);
					}
					return ((int)((next_time - SystemTime.getCurrentTime())/1000));
				}
				
				public int getInterval() {
					fixup();
					if (runData == null) {
						return (-1);
					}
					return ((int)(current_announce_interval/1000));
				}
				
				public int getMinInterval() {
					fixup();
					if (runData == null) {
						return (-1);
					}
					return (ANNOUNCE_MIN_DEFAULT/1000);
				}
				
				public boolean isUpdating() {
					return (updating);
				}
				
				public boolean canManuallyUpdate() {
					fixup();
					return (runData != null);
				}
				
				public void manualUpdate() {
					announce(download);
				}
			});
	}


	public TrackerPeerSource[] getTrackerPeerSources(final Torrent torrent) {
		TrackerPeerSource vuze_dht =
			new TrackerPeerSourceAdapter() {
				private volatile boolean	query_done;
				private volatile int		status		= ST_INITIALISING;
				private volatile int		seeds 		= 0;
				private volatile int		leechers 	= 0;

				private void fixup() {
					if (initialisedSem.isReleasedForever()) {
						synchronized(this) {
							if (query_done) {
								return;
							}
							query_done = true;
							status = ST_UPDATING;
						}
						dht.get(	torrent.getHash(),
									"Availability lookup for '" + torrent.getName() + "'",
									DHTPlugin.FLAG_DOWNLOADING,
									NUM_WANT,
									ANNOUNCE_DERIVED_TIMEOUT,
									false, true,
									new DHTPluginOperationListener() {
										public void starts(
											byte[]				key) {
										}
										public boolean diversified() {
											return (true);
										}
										public void valueRead(
											DHTPluginContact	originator,
											DHTPluginValue		value) {
											if ((value.getFlags() & DHTPlugin.FLAG_DOWNLOADING ) == 1) {
												seeds++;
											} else {
												leechers++;
											}
										}
										public void valueWritten(
											DHTPluginContact	target,
											DHTPluginValue		value) {
										}
										public void complete(
											byte[]				key,
											boolean				timeout_occurred) {
											status		= ST_ONLINE;
										}
									});
					}
				}
				public int getType() {
					return (TP_DHT);
				}
				public String getName() {
					return ("Vuze DHT");
				}
				public int getStatus() {
					fixup();
					return (status);
				}
				public int getSeedCount() {
					fixup();
					int	result = seeds;
					if (result == 0 && status != ST_ONLINE) {
						return (-1);
					}
					return (result);
				}
				public int getLeecherCount() {
					fixup();
					int	result = leechers;
					if (result == 0 && status != ST_ONLINE) {
						return (-1);
					}
					return (result);
				}
				public int getPeers() {
					return (-1);
				}
				public boolean isUpdating() {
					return (status == ST_UPDATING);
				}
			};
		if (alt_lookup_handler != null) {
			TrackerPeerSource alt_dht =
					new TrackerPeerSourceAdapter() {
						private volatile int		status 	= ST_UPDATING;
						private volatile int		peers 	= 0;
						{
							alt_lookup_handler.get(
									torrent.getHash(),
									false,
									new DHTTrackerPluginAlt.LookupListener() {
										public void foundPeer(
											InetSocketAddress	address) {
											peers++;
										}
										public boolean isComplete() {
											return (false);
										}
										public void completed() {
											status = ST_ONLINE;
										}
									});
						}

						public int getType() {
							return (TP_DHT);
						}
						public String getName() {
							return ("Mainline DHT");
						}
						public int getStatus() {
							return (status);
						}
						public int getPeers() {
							int	result = peers;
							if (result == 0 && status != ST_ONLINE) {
								return (-1);
							}
							return (result);
						}
						public boolean isUpdating() {
							return (status == ST_UPDATING);
						}
					};
			return (new TrackerPeerSource[]{ vuze_dht, alt_dht });
		} else {
			return (new TrackerPeerSource[]{ vuze_dht });
		}
	}



	/*
	public static List<Object[]>
	getVivaldiTargets(
		byte[]					torrent_hash,
		double[]				loc) {
		List<Object[]>	derived_results = new ArrayList<Object[]>();

		String	loc_str = "";

		for (int j=0;j<loc.length;j++) {

			loc_str += (j==0?"":",") + loc[j];
		}

		TriangleSlicer slicer = new TriangleSlicer(25);

		double	t1_x = loc[0];
		double	t1_y = loc[1];
		double	t2_x = loc[2];
		double	t2_y = loc[3];

		int[] triangle1 = slicer.findVertices(t1_x, t1_y);

		int[] triangle2 = slicer.findVertices(t2_x, t2_y);


		for (int j=0;j<triangle1.length;j+=2) {

			int	t1_vx = triangle1[j];
			int t1_vy = triangle1[j+1];

			double	t1_distance = getDistance(t1_x, t1_y, t1_vx, t1_vy);

			for (int k=0;k<triangle2.length;k+=2) {

				int	t2_vx = triangle2[k];
				int t2_vy = triangle2[k+1];

				double	t2_distance = getDistance(t2_x, t2_y, t2_vx, t2_vy);

					// these distances are in different dimensions - make up a combined distance

				double distance = getDistance(t1_distance, 0, 0, t2_distance);


				String	key = "azderived:vivaldi:";

				String v_str = 	t1_vx + "." + t1_vy + "." + t2_vx + "." + t2_vy;

				key += v_str;

				try {
					byte[] v_bytes = key.getBytes("UTF-8");

					byte[] key_bytes = new byte[torrent_hash.length + v_bytes.length];

					System.arraycopy(torrent_hash, 0, key_bytes, 0, torrent_hash.length);

					System.arraycopy(v_bytes, 0, key_bytes, torrent_hash.length, v_bytes.length);

					derived_results.add(
						new Object[]{
							new Integer((int)distance),
							new trackerTarget(key_bytes, REG_TYPE_DERIVED, "Vivaldi: " + v_str) });


				} catch (Throwable e) {

					Debug.printStackTrace(e);
				}
			}
		}

		Collections.sort(
			derived_results,
			new Comparator<Object[]>() {
				public int compare(
					Object[] 	entry1,
					Object[] 	entry2 ) {
					int	d1 = ((Integer)entry1[0]).intValue();
					int	d2 = ((Integer)entry2[0]).intValue();

					return (d1 - d2);
				}
			});

		return (derived_results);
	}

	protected static double
	getDistance(
		double	x1,
		double	y1,
		double	x2,
		double	y2) {
		return (Math.sqrt((x2-x1)*(x2-x1) + (y2-y1)*(y2-y1)));
	}
	*/

	protected static class
	putDetails
	{
		private String	encoded;
		private String	ip_override;
		private int		tcp_port;
		private int		udp_port;
		private boolean	i2p;

		private
		putDetails(
			String	_encoded,
			String	_ip,
			int		_tcp_port,
			int		_udp_port,
			boolean	_i2p) {
			encoded			= _encoded;
			ip_override		= _ip;
			tcp_port		= _tcp_port;
			udp_port		= _udp_port;
			i2p				= _i2p;
		}

		protected String getEncoded() {
			return (encoded);
		}

		protected String getIPOverride() {
			return (ip_override);
		}

		protected int getTCPPort() {
			return (tcp_port);
		}

		protected int getUDPPort() {
			return (udp_port);
		}

		private boolean hasI2P() {
			return (i2p);
		}

		protected boolean sameAs(
			putDetails		other) {
			return ( getEncoded().equals( other.getEncoded()));
		}
	}

	public static class
	trackerTarget
	{
		private String		desc;
		private	byte[]		hash;
		private int			type;

		protected
		trackerTarget(
			byte[]			_hash,
			int				_type,
			String			_desc) {
			hash		= _hash;
			type		= _type;
			desc		= _desc;
		}

		public int getType() {
			return (type);
		}

		public byte[]
		getHash() {
			return (hash);
		}

		public String getDesc() {
			if (type != REG_TYPE_FULL) {

				return ("(" + desc + ")");
			}

			return ("");
		}
	}

	public static class
	TriangleSlicer
	{
		int width;

		private double w;
		private double w2;
		private double h;

		private double tan60;

		public TriangleSlicer(int width) {
			this.width = width;

			this.w = (float) width;
			this.w2 = w / 2;
			this.h = Math.cos(Math.PI / 6) * w;

			this.tan60 = Math.tan(Math.PI / 3);

		}

		/**
		 *
		 * @param x
		 * @param y
		 * @return an array of int values being x,y coordinate pairs
		 */
		public int[] findVertices(double x,double y) {

			int yN = (int) Math.floor((y / h));
			int xN = (int) Math.floor((x /w2));

			double v1x,v2x,v3x,v1y,v2y,v3y;

			//weither the triangle is like /\ (true) or \/ (false)
			boolean upTriangle;

			if ((xN+yN) % 2 == 0) {
				// we have a / separator in the "cell"
				if ((y-h*yN) > (x-w2*xN) * tan60) {
					//we're in the upper part
					upTriangle = false;
					v1x = w2 * (xN - 1);
					v1y = h * (yN + 1) ;
				} else {
					//we're in the lower part
					upTriangle = true;
					v1x = w2 * xN;
					v1y = h * yN;
				}
			} else {
				// We have a \ separator in the "cell"
				if ((y- h*yN) > (w2 - (x-w2*xN)) * tan60) {
					//we're in the upper part
					upTriangle = false;
					v1x = w2 * xN;
					v1y = h * (yN+1);
				} else {
					//we're in the lower part
					upTriangle = true;
					v1x = w2 * (xN - 1);
					v1y = h * yN;
				}
			}

			if (upTriangle) {
				v2x = v1x + w;
				v2y = v1y;

				v3x = v1x + w2;
				v3y = v1y + h;
			} else {
				v2x = v1x + w;
				v2y = v1y;

				v3x = v1x + w2;
				v3y = v1y - h;
			}

			int[] result = new int[6];

			result[0] = (int) v1x;
			result[1] = (int) v1y;

			result[2] = (int) v2x;
			result[3] = (int) v2y;

			result[4] = (int) v3x;
			result[5] = (int) v3y;

			return result;

		}
	}
}
