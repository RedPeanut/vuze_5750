/*
 * Created on 23-Dec-2005
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

package com.aelitis.azureus.plugins.tracker.local;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.util.AERunnable;
import org.gudy.azureus2.core3.util.AsyncDispatcher;
import org.gudy.azureus2.core3.util.SHA1Simple;
import org.gudy.azureus2.core3.util.SystemTime;
import org.gudy.azureus2.core3.util.TorrentUtils;
import org.gudy.azureus2.plugins.Plugin;
import org.gudy.azureus2.plugins.PluginConfigListener;
import org.gudy.azureus2.plugins.PluginInterface;
import org.gudy.azureus2.plugins.download.Download;
import org.gudy.azureus2.plugins.download.DownloadListener;
import org.gudy.azureus2.plugins.download.DownloadManagerListener;
import org.gudy.azureus2.plugins.logging.LoggerChannel;
import org.gudy.azureus2.plugins.logging.LoggerChannelListener;
import org.gudy.azureus2.plugins.peers.PeerManager;
import org.gudy.azureus2.plugins.torrent.Torrent;
import org.gudy.azureus2.plugins.torrent.TorrentAttribute;
import org.gudy.azureus2.plugins.ui.UIManager;
import org.gudy.azureus2.plugins.ui.config.BooleanParameter;
import org.gudy.azureus2.plugins.ui.config.ConfigSection;
import org.gudy.azureus2.plugins.ui.config.StringParameter;
import org.gudy.azureus2.plugins.ui.model.BasicPluginConfigModel;
import org.gudy.azureus2.plugins.ui.model.BasicPluginViewModel;
import org.gudy.azureus2.plugins.utils.DelayedTask;
import org.gudy.azureus2.plugins.utils.Monitor;
import org.gudy.azureus2.plugins.utils.UTTimerEvent;
import org.gudy.azureus2.plugins.utils.UTTimerEventPerformer;
import org.gudy.azureus2.pluginsimpl.local.PluginCoreUtils;

import com.aelitis.azureus.core.AzureusCoreFactory;
import com.aelitis.azureus.core.instancemanager.AZInstance;
import com.aelitis.azureus.core.instancemanager.AZInstanceManager;
import com.aelitis.azureus.core.instancemanager.AZInstanceManagerListener;
import com.aelitis.azureus.core.instancemanager.AZInstanceTracked;
import com.aelitis.azureus.core.tracker.TrackerPeerSource;
import com.aelitis.azureus.core.tracker.TrackerPeerSourceAdapter;

import hello.util.Log;

public class LocalTrackerPlugin
	implements Plugin, AZInstanceManagerListener, DownloadManagerListener, DownloadListener
{
	private static String TAG = LocalTrackerPlugin.class.getSimpleName();
	
	private static final String	PLUGIN_NAME	= "LAN Peer Finder";
	private static final String PLUGIN_CONFIGSECTION_ID = "Plugin.localtracker.name";

	private static final long	ANNOUNCE_PERIOD		= 5*60*1000;
	private static final long	RE_ANNOUNCE_PERIOD	= 1*60*1000;

	private PluginInterface		pluginInterface;
	private AZInstanceManager	instance_manager;
	private boolean				active;
	private TorrentAttribute 	ta_networks;
	private TorrentAttribute 	ta_peer_sources;

	private Map<Download,long[]>	downloads 	= new HashMap<Download, long[]>();

	private Map<String,Map<String,Long>>	track_times	= new HashMap<String, Map<String,Long>>();

	private String				last_autoadd	= "";
	private String				last_subnets	= "";

	private BooleanParameter	enabled;

	private long				plugin_start_time;

	private long current_time;

	private LoggerChannel 		log;
	private Monitor 			mon;

	private AsyncDispatcher	dispatcher = new AsyncDispatcher(30*1000);

	public static void load(PluginInterface pluginInterface) {
		pluginInterface.getPluginProperties().setProperty("plugin.version", 	"1.0");
		pluginInterface.getPluginProperties().setProperty("plugin.name", 		PLUGIN_NAME);
	}

	public void initialize(PluginInterface _pluginInterface) {
		
		Log.d(TAG, "initialize() is called...");
		
		pluginInterface = _pluginInterface;
		ta_networks 	= pluginInterface.getTorrentManager().getAttribute(TorrentAttribute.TA_NETWORKS);
		ta_peer_sources = pluginInterface.getTorrentManager().getAttribute(TorrentAttribute.TA_PEER_SOURCES);
		mon	= pluginInterface.getUtilities().getMonitor();
		log = pluginInterface.getLogger().getTimeStampedChannel(PLUGIN_NAME);
		UIManager	ui_manager = pluginInterface.getUIManager();
		BasicPluginConfigModel	config = ui_manager.createBasicPluginConfigModel(ConfigSection.SECTION_PLUGINS, PLUGIN_CONFIGSECTION_ID);
		config.addLabelParameter2("Plugin.localtracker.info");
		enabled = config.addBooleanParameter2("Plugin.localtracker.enable", "Plugin.localtracker.enable", true);
		config.addLabelParameter2("Plugin.localtracker.networks.info");
		final StringParameter subnets = config.addStringParameter2("Plugin.localtracker.networks", "Plugin.localtracker.networks", "");
		final BooleanParameter include_wellknown = config.addBooleanParameter2("Plugin.localtracker.wellknownlocals", "Plugin.localtracker.wellknownlocals", true);
		config.addLabelParameter2("Plugin.localtracker.autoadd.info");
		final StringParameter autoadd = config.addStringParameter2("Plugin.localtracker.autoadd", "Plugin.localtracker.autoadd", "");
		/*
		 * actually these parameters affect LAN detection as a whole, not just the local tracker,
		 * so leave them enabled...
		 *
		enabled.addEnabledOnSelection(lp1);
		enabled.addEnabledOnSelection(subnets);
		enabled.addEnabledOnSelection(lp2);
		enabled.addEnabledOnSelection(autoadd);
		*/
		final BasicPluginViewModel	view_model =
			pluginInterface.getUIManager().createBasicPluginViewModel("Plugin.localtracker.name");
		view_model.setConfigSectionID(PLUGIN_CONFIGSECTION_ID);
		view_model.getActivity().setVisible(false);
		view_model.getProgress().setVisible(false);
		log.addListener(
				new LoggerChannelListener() {
					public void messageLogged(
						int		type,
						String	content) {
						view_model.getLogArea().appendText(content + "\n");
					}
					public void messageLogged(
						String		str,
						Throwable	error) {
						if (str.length() > 0) {
							view_model.getLogArea().appendText(str + "\n");
						}
						StringWriter sw = new StringWriter();
						PrintWriter	pw = new PrintWriter(sw);
						error.printStackTrace(pw);
						pw.flush();
						view_model.getLogArea().appendText(sw.toString() + "\n");
					}
				});
		plugin_start_time = pluginInterface.getUtilities().getCurrentSystemTime();
		// Assume we have a core, since this is a plugin
		instance_manager	= AzureusCoreFactory.getSingleton().getInstanceManager();
		instance_manager.addListener(this);
		pluginInterface.getPluginconfig().addListener(
				new PluginConfigListener() {
					public void configSaved() {
						processSubNets(subnets.getValue(),include_wellknown.getValue());
						processAutoAdd( autoadd.getValue());
					}
				});
		processSubNets(subnets.getValue(), include_wellknown.getValue());
		processAutoAdd(autoadd.getValue());
		final DelayedTask dt = pluginInterface.getUtilities().createDelayedTask(new Runnable() {
				public void run() {
						pluginInterface.getDownloadManager().addListener(
								LocalTrackerPlugin.this);
				}
			});
		dt.queue();
	}

	public void instanceFound(
		AZInstance		instance) {
		if (!enabled.getValue()) {

			return;
		}

		log.log("Found: " + instance.getString());

		try {
			mon.enter();

			track_times.put( instance.getID(), new HashMap<String, Long>());

		} finally {

			mon.exit();
		}

		checkActivation();
	}

	protected void checkActivation() {
		try {
			mon.enter();

			if (active) {

				return;
			}

			active	= true;

			pluginInterface.getUtilities().createThread(
				"Tracker",
				new Runnable() {
					public void run() {
						track();
					}
				});

		} finally {

			mon.exit();
		}
	}

	public void instanceChanged(
		AZInstance		instance) {
		if (!enabled.getValue()) {

			return;
		}

		log.log("Changed: " + instance.getString());
	}

	public void instanceLost(
		AZInstance		instance) {
		try {
			mon.enter();

			track_times.remove( instance.getID());

		} finally {

			mon.exit();
		}

		if (!enabled.getValue()) {

			return;
		}

		log.log("Lost: " + instance.getString());
	}

	public void instanceTracked(
		AZInstanceTracked 		instance) {
		if (!enabled.getValue()) {

			return;
		}

		handleTrackResult(instance);
	}

	protected void track() {
		long	now = pluginInterface.getUtilities().getCurrentSystemTime();

		if (now - plugin_start_time < 60*1000) {

			try {
					// initial small delay to let things stabilise

				Thread.sleep(15*1000);

			} catch (Throwable e) {
			}
		}


		pluginInterface.getUtilities().createTimer("LanPeerFinder:Tracker", true).addPeriodicEvent(
				30*1000,
				new UTTimerEventPerformer() {

					public void perform(UTTimerEvent	event) {

						current_time = pluginInterface.getUtilities().getCurrentSystemTime();

						try {

							List<Download>	todo = new ArrayList<Download>();

							try {
								mon.enter();

								Iterator<Map.Entry<Download,long[]>>	it = downloads.entrySet().iterator();

								while (it.hasNext()) {

									Map.Entry<Download,long[]>	entry = it.next();

									Download	dl 		= entry.getKey();
									long		when	= entry.getValue()[0];

									if (when > current_time || current_time - when > ANNOUNCE_PERIOD) {

										todo.add(dl);
									}
								}

							} finally {

								mon.exit();
							}

							for (int i=0;i<todo.size();i++) {

								track(todo.get(i));
							}

						} catch (Throwable e) {

							log.log(e);
						}

					}

				});

	}



	protected void track(
		Download	download) {
		long	now = pluginInterface.getUtilities().getCurrentSystemTime();

		boolean	ok = false;

		try {
			mon.enter();

			long[]	data = downloads.get(download);

			if (data == null) {

				return;
			}

			long	last_track = data[0];

			if (last_track > now || now - last_track > RE_ANNOUNCE_PERIOD) {

				ok	= true;

				data[0] = now;
			}

		} finally {

			mon.exit();
		}

		if (ok) {

			trackSupport(download);
		}
	}

	protected void trackSupport(
		final Download	download) {
		if (!enabled.getValue()) {

			return;
		}

		int	state = download.getState();

		if (state == Download.ST_ERROR || state == Download.ST_STOPPED) {

			return;
		}

		String[]	sources = download.getListAttribute(ta_peer_sources);

		boolean	ok = false;

		if (sources != null) {

			for (int i=0;i<sources.length;i++) {

				if (sources[i].equalsIgnoreCase("Plugin")) {

					ok	= true;

					break;
				}
			}
		}

		if (!ok) {

			return;
		}

		if (download.getTorrent() == null) {

			return;
		}

		byte[] hash = new SHA1Simple().calculateHash(download.getTorrent().getHash());


		AZInstanceTracked[]	peers =
			instance_manager.track(
				hash,
				new AZInstanceTracked.TrackTarget() {
					public Object getTarget() {
						return (download);
					}

					public boolean isSeed() {
						return ( download.isComplete());
					}
				});

		int	total_seeds 	= 0;
		int	total_leechers	= 0;
		int	total_peers		= 0;

		for (int i=0;i<peers.length;i++) {

			int res = handleTrackResult(peers[i]);

			if (res == 1) {
				total_seeds++;
			} else if (res == 2) {
				total_leechers++;
			} else if (res == 3) {
				total_seeds++;
				total_peers++;
			} else if (res == 4) {
				total_leechers++;
				total_peers++;
			}
		}

		try {
			mon.enter();

			long[] data = downloads.get(download);

			if (data != null) {

				data[1] = total_seeds;
				data[2] = total_leechers;
				data[3] = total_peers;
			}
		} finally {

			mon.exit();
		}
	}

	protected void forceTrack(
		final Download	download) {
		try {
			mon.enter();

			long[] data = downloads.get(download);

			if (data == null) {

				data = new long[4];

				downloads.put(download, data);

			} else {

				data[0] = 0;
			}

			String	dl_key = pluginInterface.getUtilities().getFormatters().encodeBytesToString(download.getTorrent().getHash());

			Iterator<Map<String,Long>>	it = track_times.values().iterator();

			while (it.hasNext()) {

				it.next().remove(dl_key);
			}
		} finally {

			mon.exit();
		}

		dispatcher.dispatch(
			new AERunnable() {
				public void runSupport() {
					track(download);
				}
			});
	}

	protected int handleTrackResult(
		AZInstanceTracked		tracked_inst) {
		AZInstance	inst	= tracked_inst.getInstance();

		Download	download = (Download)tracked_inst.getTarget().getTarget();

		boolean	is_seed = tracked_inst.isSeed();

		long	now		= pluginInterface.getUtilities().getCurrentSystemTime();

		boolean	skip 	= false;

			// this code is here to deal with multiple interface machines that receive the result multiple times

		try {
			mon.enter();

			Map<String,Long>	map = track_times.get(inst.getID());

			if (map == null) {

				map	= new HashMap<String, Long>();

				track_times.put(inst.getID(), map);
			}

			String	dl_key = pluginInterface.getUtilities().getFormatters().encodeBytesToString(download.getTorrent().getHash());

			Long	last_track = map.get(dl_key);

			if (last_track != null) {

				long	lt = last_track.longValue();

				if (now - lt < 30*1000) {

					skip	= true;
				}
			}

			map.put( dl_key, new Long(now));

		} finally {

			mon.exit();
		}

		if (skip) {

			return (-1);
		}

		log.log("Tracked: " + inst.getString() + ": " + download.getName() + ", seed = " + is_seed);

		if (download.isComplete() && is_seed) {

			return (1);
		}

		PeerManager	peer_manager = download.getPeerManager();

		if (peer_manager != null) {

			String	peer_ip			= inst.getInternalAddress().getHostAddress();
			int		peer_tcp_port	= inst.getTCPListenPort();
			int		peer_udp_port	= inst.getUDPListenPort();

			log.log("    " + download.getName() + ": Injecting peer " + peer_ip + ":" + peer_tcp_port + "/" + peer_udp_port);

			peer_manager.addPeer(peer_ip, peer_tcp_port, peer_udp_port, false);
		}

		return (is_seed?3:2);
	}

	public void downloadAdded(
		Download	download) {
		try {
			mon.enter();

			Torrent	torrent = download.getTorrent();

			if (torrent == null) {

				return;
			}

			if (TorrentUtils.isReallyPrivate(PluginCoreUtils.unwrap( torrent))) {

				log.log("Not tracking " + download.getName() + ": torrent is private");

				return;
			}

			String[]	networks = download.getListAttribute(ta_networks);

			boolean	public_net = false;

			if (networks != null) {

				for (int i=0;i<networks.length;i++) {

					if (networks[i].equalsIgnoreCase("Public")) {

						public_net	= true;

						break;
					}
				}
			}

			if (!public_net) {

				log.log("Not tracking " + download.getName() + ": torrent has no public network");

				return;
			}

			if (enabled.getValue()) {

				log.log("Tracking " + download.getName());
			}

			long[] data = downloads.get(download);

			if (data == null) {

				data = new long[4];

				downloads.put(download, data);

			} else {

				data[0] = 0;
			}

			download.addListener(this);

		} finally {

			mon.exit();
		}
	}

	public void downloadRemoved(
		Download	download) {
		try {
			mon.enter();

			downloads.remove(download);

			download.removeListener(this);

		} finally {

			mon.exit();
		}
	}

	public TrackerPeerSource
	getTrackerPeerSource(
		final Download		download) {
		return (
			new TrackerPeerSourceAdapter() {
				private long[] 		_last_data;
				private boolean		enabled;
				private boolean		running;
				private long		fixup_time;

				private long[]
				fixup() {
					long now = SystemTime.getMonotonousTime();

					if (now - fixup_time > 1000) {

						try {
							mon.enter();

							_last_data = downloads.get(download);

						} finally {

							mon.exit();
						}

						enabled = LocalTrackerPlugin.this.enabled.getValue();

						if (enabled) {

							int ds = download.getState();

							running = ds == Download.ST_DOWNLOADING || ds == Download.ST_SEEDING;

						} else {

							running = false;
						}

						fixup_time = now;
					}

					return (_last_data);
				}

				public int getType() {
					return (TP_LAN);
				}

				public String getName() {
					return (MessageText.getString("tps.lan.details", new String[]{ String.valueOf( instance_manager.getOtherInstanceCount( false))}));
				}

				public int getStatus() {
					long[] last_data = fixup();

					if (last_data == null || !enabled) {

						return (ST_DISABLED);
					}

					if (running) {

						return (ST_ONLINE);
					}

					return (ST_STOPPED);
				}

				public int getSeedCount() {
					long[] last_data = fixup();

					if (last_data == null || !running) {

						return (-1);
					}

					return ((int)last_data[1]);
				}

				public int getLeecherCount() {
					long[] last_data = fixup();

					if (last_data == null || !running) {

						return (-1);
					}

					return ((int)last_data[2]);
				}

				public int getPeers() {
					long[] last_data = fixup();

					if (last_data == null || !running) {

						return (-1);
					}

					return ((int)last_data[3]);
				}

				public int getSecondsToUpdate() {
					long[] last_data = fixup();

					if (last_data == null || !running) {

						return (Integer.MIN_VALUE);
					}

					return ((int)((ANNOUNCE_PERIOD - ( SystemTime.getCurrentTime() - last_data[0] ))/1000));
				}

				public int getInterval() {
					if (running) {

						return ((int)(ANNOUNCE_PERIOD/1000));
					}

					return (-1);
				}

				public int getMinInterval() {
					if (running) {

						return ((int)(RE_ANNOUNCE_PERIOD/1000));
					}

					return (-1);
				}

				public boolean isUpdating() {
					int	su = getSecondsToUpdate();

					if (su == Integer.MIN_VALUE || su >= 0) {

						return (false);
					}

					return (true);
				}
			});
	}

	public void stateChanged(
		Download		download,
		int				old_state,
		int				new_state) {
		if (	new_state == Download.ST_DOWNLOADING ||
				new_state == Download.ST_SEEDING) {

			forceTrack(download);
		}
	}

	public void positionChanged(
		Download	download,
		int oldPosition,
		int newPosition) {
	}

	protected void processSubNets(
		String	subnets,
		boolean	include_well_known) {
		if (include_well_known != instance_manager.getIncludeWellKnownLANs()) {

			instance_manager.setIncludeWellKnownLANs(include_well_known);

			log.log("Include well known local networks set to " + include_well_known);
		}

		if (subnets.equals( last_subnets)) {

			return;
		}

		last_subnets = subnets;

		StringTokenizer	tok = new StringTokenizer( subnets, ";");

		while (tok.hasMoreTokens()) {

			String	net = tok.nextToken().trim();

			try {

				if (instance_manager.addLANSubnet( net)) {

					log.log("Added network '" + net + "'");
				}

			} catch (Throwable e) {

				log.log("Failed to add network '" + net + "'", e);
			}
		}
	}

	protected void processAutoAdd(
		String	autoadd) {
		if (autoadd.equals( last_autoadd)) {

			return;
		}

		last_autoadd = autoadd;

		StringTokenizer	tok = new StringTokenizer( autoadd, ";");

		while (tok.hasMoreTokens()) {

			String	peer = tok.nextToken();

			try {

				InetAddress p = InetAddress.getByName( peer.trim());

				if (instance_manager.addInstance( p)) {

					log.log("Added peer '" + peer + "'");
				}
			} catch (Throwable e) {

				log.log("Failed to decode peer '" + peer + "'", e);
			}
		}
	}
}
