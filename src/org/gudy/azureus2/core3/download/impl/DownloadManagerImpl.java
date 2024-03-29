/*
 * File		: DownloadManagerImpl.java
 * Created : 19-Oct-2003
 * By			: parg
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.	See the
 * GNU General Public License for more details (see the LICENSE file).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA	02111-1307	USA
 */

package org.gudy.azureus2.core3.download.impl;
/*
 * Created on 30 juin 2003
 *
 */

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.config.ParameterListener;
import org.gudy.azureus2.core3.config.impl.TransferSpeedValidator;
import org.gudy.azureus2.core3.disk.DiskManager;
import org.gudy.azureus2.core3.disk.DiskManagerFactory;
import org.gudy.azureus2.core3.disk.DiskManagerFileInfo;
import org.gudy.azureus2.core3.disk.DiskManagerFileInfoSet;
import org.gudy.azureus2.core3.disk.impl.DiskManagerImpl;
import org.gudy.azureus2.core3.disk.impl.DiskManagerUtil;
import org.gudy.azureus2.core3.download.DownloadManager;
import org.gudy.azureus2.core3.download.DownloadManagerActivationListener;
import org.gudy.azureus2.core3.download.DownloadManagerDiskListener;
import org.gudy.azureus2.core3.download.DownloadManagerException;
import org.gudy.azureus2.core3.download.DownloadManagerInitialisationAdapter;
import org.gudy.azureus2.core3.download.DownloadManagerListener;
import org.gudy.azureus2.core3.download.DownloadManagerPeerListener;
import org.gudy.azureus2.core3.download.DownloadManagerPieceListener;
import org.gudy.azureus2.core3.download.DownloadManagerState;
import org.gudy.azureus2.core3.download.DownloadManagerStateAttributeListener;
import org.gudy.azureus2.core3.download.DownloadManagerStateFactory;
import org.gudy.azureus2.core3.download.DownloadManagerStats;
import org.gudy.azureus2.core3.download.DownloadManagerTPSListener;
import org.gudy.azureus2.core3.download.DownloadManagerTrackerListener;
import org.gudy.azureus2.core3.download.ForceRecheckListener;
import org.gudy.azureus2.core3.global.GlobalManager;
import org.gudy.azureus2.core3.global.GlobalManagerEvent;
import org.gudy.azureus2.core3.global.GlobalManagerStats;
import org.gudy.azureus2.core3.internat.LocaleTorrentUtil;
import org.gudy.azureus2.core3.internat.LocaleUtilDecoder;
import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.logging.LogAlert;
import org.gudy.azureus2.core3.logging.LogEvent;
import org.gudy.azureus2.core3.logging.LogIDs;
import org.gudy.azureus2.core3.logging.LogRelation;
import org.gudy.azureus2.core3.logging.Logger;
import org.gudy.azureus2.core3.peer.PEPeer;
import org.gudy.azureus2.core3.peer.PEPeerManager;
import org.gudy.azureus2.core3.peer.PEPeerSource;
import org.gudy.azureus2.core3.peer.PEPiece;
import org.gudy.azureus2.core3.torrent.TOTorrent;
import org.gudy.azureus2.core3.torrent.TOTorrentAnnounceURLSet;
import org.gudy.azureus2.core3.torrent.TOTorrentException;
import org.gudy.azureus2.core3.torrent.TOTorrentListener;
import org.gudy.azureus2.core3.tracker.client.TRTrackerAnnouncer;
import org.gudy.azureus2.core3.tracker.client.TRTrackerAnnouncerException;
import org.gudy.azureus2.core3.tracker.client.TRTrackerAnnouncerFactory;
import org.gudy.azureus2.core3.tracker.client.TRTrackerAnnouncerListener;
import org.gudy.azureus2.core3.tracker.client.TRTrackerAnnouncerResponse;
import org.gudy.azureus2.core3.tracker.client.TRTrackerAnnouncerResponsePeer;
import org.gudy.azureus2.core3.tracker.client.TRTrackerScraper;
import org.gudy.azureus2.core3.tracker.client.TRTrackerScraperResponse;
import org.gudy.azureus2.core3.util.AEMonitor;
import org.gudy.azureus2.core3.util.AENetworkClassifier;
import org.gudy.azureus2.core3.util.AEThread2;
import org.gudy.azureus2.core3.util.Base32;
import org.gudy.azureus2.core3.util.ByteFormatter;
import org.gudy.azureus2.core3.util.Constants;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.DisplayFormatters;
import org.gudy.azureus2.core3.util.FileUtil;
import org.gudy.azureus2.core3.util.IndentWriter;
import org.gudy.azureus2.core3.util.LightHashMap;
import org.gudy.azureus2.core3.util.ListenerManager;
import org.gudy.azureus2.core3.util.ListenerManagerDispatcher;
import org.gudy.azureus2.core3.util.StringInterner;
import org.gudy.azureus2.core3.util.SystemTime;
import org.gudy.azureus2.core3.util.TorrentUtils;
import org.gudy.azureus2.plugins.PluginInterface;
import org.gudy.azureus2.plugins.clientid.ClientIDGenerator;
import org.gudy.azureus2.plugins.download.Download;
import org.gudy.azureus2.plugins.download.DownloadAnnounceResult;
import org.gudy.azureus2.plugins.download.DownloadScrapeResult;
import org.gudy.azureus2.plugins.download.savelocation.SaveLocationChange;
import org.gudy.azureus2.plugins.network.ConnectionManager;
import org.gudy.azureus2.pluginsimpl.local.PluginCoreUtils;
import org.gudy.azureus2.pluginsimpl.local.clientid.ClientIDManagerImpl;
import org.gudy.azureus2.pluginsimpl.local.download.DownloadImpl;

import com.aelitis.azureus.core.AzureusCoreFactory;
import com.aelitis.azureus.core.AzureusCoreOperation;
import com.aelitis.azureus.core.AzureusCoreOperationTask;
import com.aelitis.azureus.core.networkmanager.LimitedRateGroup;
import com.aelitis.azureus.core.networkmanager.NetworkManager;
import com.aelitis.azureus.core.peermanager.control.PeerControlSchedulerFactory;
import com.aelitis.azureus.core.tag.Taggable;
import com.aelitis.azureus.core.tag.TaggableResolver;
import com.aelitis.azureus.core.tracker.TrackerPeerSource;
import com.aelitis.azureus.core.tracker.TrackerPeerSourceAdapter;
import com.aelitis.azureus.core.util.CopyOnWriteList;
import com.aelitis.azureus.core.util.LinkFileMap;
import com.aelitis.azureus.core.util.PlatformTorrentUtils;
import com.aelitis.azureus.plugins.extseed.ExternalSeedPlugin;
import com.aelitis.azureus.plugins.tracker.dht.DHTTrackerPlugin;
import com.aelitis.azureus.plugins.tracker.local.LocalTrackerPlugin;

import hello.util.Log;

/**
 * @author Olivier
 *
 */

public class DownloadManagerImpl extends LogRelation
	implements DownloadManager, Taggable {

	private static String TAG = DownloadManagerImpl.class.getSimpleName();

	private final static long SCRAPE_DELAY_ERROR_TORRENTS = 1000 * 60 * 60 * 2;// 2 hrs
	private final static long SCRAPE_DELAY_STOPPED_TORRENTS = 1000 * 60 * 60;	// 1 hr

	private final static long SCRAPE_INITDELAY_ERROR_TORRENTS = 1000 * 60 * 10;
	private final static long SCRAPE_INITDELAY_STOPPED_TORRENTS = 1000 * 60 * 3;

	private static int upload_when_busy_min_secs;
	private static int max_connections_npp_extra;

	private static final ClientIDManagerImpl client_id_manager = ClientIDManagerImpl.getSingleton();

	static{
		COConfigurationManager.addAndFireParameterListeners(
			new String[]{
				"max.uploads.when.busy.inc.min.secs",
			},
			new ParameterListener() {
				public void parameterChanged(
					String name) {
					upload_when_busy_min_secs = COConfigurationManager.getIntParameter("max.uploads.when.busy.inc.min.secs");

					max_connections_npp_extra = COConfigurationManager.getIntParameter("Non-Public Peer Extra Connections Per Torrent");
				}
			});
	}

	private static final String CFG_MOVE_COMPLETED_TOP = "Newly Seeding Torrents Get First Priority";
	
	// DownloadManager listeners
	private static final int LDT_STATECHANGED			= 1;
	private static final int LDT_DOWNLOADCOMPLETE		= 2;
	private static final int LDT_COMPLETIONCHANGED 		= 3;
	private static final int LDT_POSITIONCHANGED 		= 4;
	private static final int LDT_FILEPRIORITYCHANGED 	= 5;

	private final AEMonitor	listenersMonitor = new AEMonitor("DM:DownloadManager:L");

	static final ListenerManager<DownloadManagerListener> listenersAggregator = 
			ListenerManager.createAsyncManager(
				"DM:ListenAggregatorDispatcher",
				new ListenerManagerDispatcher<DownloadManagerListener>() {
					public void dispatch(
						DownloadManagerListener listener,
						int type,
						Object _value) {
						
						Object[] value = (Object[])_value;
						DownloadManagerImpl	dm = (DownloadManagerImpl)value[0];
						if (type == LDT_STATECHANGED) {
							listener.stateChanged(dm, ((Integer)value[1]).intValue());
						} else if (type == LDT_DOWNLOADCOMPLETE) {
							listener.downloadComplete(dm);
						} else if (type == LDT_COMPLETIONCHANGED) {
							listener.completionChanged(dm, ((Boolean)value[1]).booleanValue());
						} else if (type == LDT_FILEPRIORITYCHANGED) {
							listener.filePriorityChanged(dm, (DiskManagerFileInfo)value[1]);
						} else if (type == LDT_POSITIONCHANGED) {
							listener.positionChanged(dm, ((Integer)value[1]).intValue(), ((Integer)value[2]).intValue());
						}
					}
				}
			);

	static final CopyOnWriteList<DownloadManagerListener> globalDMListeners = new CopyOnWriteList<DownloadManagerListener>();

		private static final DownloadManagerListener globalDMListener =
				
		new DownloadManagerListener() {

			public void stateChanged(DownloadManager manager, int state) {
				for (DownloadManagerListener listener: globalDMListeners) {
					try {
						listener.stateChanged(manager, state);
					} catch (Throwable e) {
						Debug.out(e);
					}
				}
			}

			public void positionChanged(DownloadManager download, int oldPosition, int newPosition) {
				for (DownloadManagerListener listener: globalDMListeners) {
					try {
						listener.positionChanged(download, oldPosition, newPosition);
					} catch (Throwable e) {
						Debug.out(e);
					}
				}
			}

			public void filePriorityChanged(DownloadManager download, DiskManagerFileInfo file) {
				for (DownloadManagerListener listener: globalDMListeners) {
					try {
						listener.filePriorityChanged(download, file);
					} catch (Throwable e) {
						Debug.out(e);
					}
				}
			}

			public void downloadComplete(DownloadManager manager) {
				for (DownloadManagerListener listener: globalDMListeners) {
					try {
						listener.downloadComplete(manager);
					} catch (Throwable e) {
						Debug.out(e);
					}
				}
			}

			public void completionChanged(DownloadManager manager, boolean bCompleted) {
				DownloadManagerState dms = manager.getDownloadState();
				long time = dms.getLongAttribute(DownloadManagerState.AT_COMPLETE_LAST_TIME);
				if (time == -1) {
					if (bCompleted) {
						dms.setLongAttribute( DownloadManagerState.AT_COMPLETE_LAST_TIME, SystemTime.getCurrentTime());
					}
				} else if (time > 0) {
					if (!bCompleted) {
						dms.setLongAttribute(DownloadManagerState.AT_COMPLETE_LAST_TIME, -1);
					}
				} else {
					if (bCompleted) {
						long completedOn = dms.getLongParameter(DownloadManagerState.PARAM_DOWNLOAD_COMPLETED_TIME);
						if (completedOn > 0) {
							dms.setLongAttribute(DownloadManagerState.AT_COMPLETE_LAST_TIME, completedOn);
						}
					} else {
						dms.setLongAttribute(DownloadManagerState.AT_COMPLETE_LAST_TIME, -1);
					}
				}
				for (DownloadManagerListener listener: globalDMListeners) {
					try {
						listener.completionChanged(manager, bCompleted);
					} catch (Throwable e) {
						Debug.out(e);
					}
				}
			}
			};

	public static void addGlobalDownloadListener(DownloadManagerListener listener) {
		globalDMListeners.add(listener);
	}

		public static void removeGlobalDownloadListener(DownloadManagerListener listener ) {
			globalDMListeners.remove(listener);
		}

	private final ListenerManager<DownloadManagerListener> listeners = ListenerManager.createManager(
		"DM:ListenDispatcher",
		new ListenerManagerDispatcher<DownloadManagerListener>() {
			public void dispatch(DownloadManagerListener listener, int type, Object value) {
				listenersAggregator.dispatch(listener, type, value);
			}
		}
	);

	{
		listeners.addListener(globalDMListener);
	}
	
	// TrackerListeners
	private static final int LDT_TL_ANNOUNCERESULT		= 1;
	private static final int LDT_TL_SCRAPERESULT		= 2;

	final ListenerManager<Object> trackerListeners = ListenerManager.createManager(
			"DM:TrackerListenDispatcher",
			new ListenerManagerDispatcher<Object>() {
				public void dispatch(
					Object _listener,
					int type,
					Object value) {
					
					DownloadManagerTrackerListener listener = (DownloadManagerTrackerListener)_listener;
					if (type == LDT_TL_ANNOUNCERESULT) {
						listener.announceResult((TRTrackerAnnouncerResponse)value);
					} else if (type == LDT_TL_SCRAPERESULT) {
						listener.scrapeResult((TRTrackerScraperResponse)value);
					}
				}
			});

	// PeerListeners
	private static final int LDT_PE_PEER_ADDED		= 1;
	private static final int LDT_PE_PEER_REMOVED	= 2;
	private static final int LDT_PE_PM_ADDED		= 5;
	private static final int LDT_PE_PM_REMOVED		= 6;

	// one static async manager for them all

	static final ListenerManager<DownloadManagerPeerListener> peerListenersAggregator =
		ListenerManager.createAsyncManager(
			"DM:PeerListenAggregatorDispatcher",
			new ListenerManagerDispatcher<DownloadManagerPeerListener>() {
				public void dispatch(DownloadManagerPeerListener listener, int type, Object value) {
					if (type == LDT_PE_PEER_ADDED) {
						listener.peerAdded((PEPeer)value);
					} else if (type == LDT_PE_PEER_REMOVED) {
						listener.peerRemoved((PEPeer)value);
					} else if (type == LDT_PE_PM_ADDED) {
						listener.peerManagerAdded((PEPeerManager)value);
					} else if (type == LDT_PE_PM_REMOVED) {
						listener.peerManagerRemoved((PEPeerManager)value);
					}
				}
			}
		);

	static final Object TPS_Key = new Object();
	public static volatile String	dnd_subfolder;

	static {
		COConfigurationManager.addAndFireParameterListeners(
			new String[]{ "Enable Subfolder for DND Files", "Subfolder for DND Files" },
			new ParameterListener() {
				public void parameterChanged(
					String parameterName) {
					boolean enable	= COConfigurationManager.getBooleanParameter("Enable Subfolder for DND Files");
					if (enable) {
						String folder = COConfigurationManager.getStringParameter("Subfolder for DND Files").trim();
						if (folder.length() > 0) {
							folder = FileUtil.convertOSSpecificChars(folder, true).trim();
						}
						if (folder.length() > 0) {
							dnd_subfolder = folder;
						} else {
							dnd_subfolder = null;
						}
					} else {
						dnd_subfolder = null;
					}
				}
			}
		);
	}




	private final ListenerManager<DownloadManagerPeerListener>	peerListeners 	= ListenerManager.createManager(
			"DM:PeerListenDispatcher",
			new ListenerManagerDispatcher<DownloadManagerPeerListener>() {
				public void dispatch(
					DownloadManagerPeerListener		listener,
					int			type,
					Object		value) {
					peerListenersAggregator.dispatch(listener, type, value);
				}
			});

	final AEMonitor	peerListenersMon	= new AEMonitor("DM:DownloadManager:PeerL");

	final Map<PEPeer,String>		current_peers 						= new IdentityHashMap<PEPeer, String>();
	private final Map<PEPeer,Long>	current_peers_unmatched_removal 	= new IdentityHashMap<PEPeer, Long>();

	// PieceListeners
	private static final int LDT_PE_PIECE_ADDED		= 3;
	private static final int LDT_PE_PIECE_REMOVED	= 4;

	// one static async manager for them all

	static final ListenerManager<Object> pieceListenersAggregator = ListenerManager.createAsyncManager(
		"DM:PieceListenAggregatorDispatcher",
		new ListenerManagerDispatcher<Object>() {
			public void dispatch(
				Object		_listener,
				int			type,
				Object		value) {
				DownloadManagerPieceListener listener = (DownloadManagerPieceListener)_listener;

				if (type == LDT_PE_PIECE_ADDED) {
					listener.pieceAdded((PEPiece)value);
				} else if (type == LDT_PE_PIECE_REMOVED) {
					listener.pieceRemoved((PEPiece)value);
				}
			}
		}
	);

	private final ListenerManager<Object>	pieceListeners = ListenerManager.createManager(
			"DM:PieceListenDispatcher",
			new ListenerManagerDispatcher<Object>() {
				public void dispatch(
					Object		listener,
					int			type,
					Object		value) {
					pieceListenersAggregator.dispatch(listener, type, value);
				}
			});

	private List<DownloadManagerTPSListener>	tps_listeners;
	private final AEMonitor	pieceListenersMon	= new AEMonitor("DM:DownloadManager:PeiceL");
	private final List	currentPieces	= new ArrayList();
	final DownloadManagerController	controller;
	private final DownloadManagerStatsImpl	stats;
	protected final AEMonitor thisMon = new AEMonitor("DM:DownloadManager");
	private final boolean		persistent;

	/**
	 * Pretend this download is complete while not running,
	 * even if it has no data.	When the torrent starts up, the real complete
	 * level will be checked (probably by DiskManager), and if the torrent
	 * actually does have missing data at that point, the download will be thrown
	 * into error state.
	 * <p>
	 * Only a forced-recheck should clear this flag.
	 * <p>
	 * Current Implementation:<br>
	 * - implies that the user completed the download at one point<br>
	 * - Checks if there's Data Missing when torrent is done (or torrent load)
	 */
	private boolean assumedComplete;

	/**
	 * forceStarted torrents can't/shouldn't be automatically stopped
	 */

	private int			last_informed_state	= STATE_START_OF_DAY;
	private boolean		latest_informed_force_start;

	private long		resumeTime;

	final GlobalManager globalManager;
	private String torrentFileName;

	private boolean	open_for_seeding;

	private String	displayName	= "";
	private String	internalName	= "";

	// for simple torrents this refers to the torrent file itself. For non-simple it refers to the
	// folder containing the torrent's files

	private File	torrent_save_location;

	// Position in Queue
	private int position = -1;

	private Object[]					read_torrent_state;
	private	DownloadManagerState		downloadManagerState;

	private TOTorrent		torrent;
	private String 			torrentComment;
	private String 			torrentCreatedBy;

	private TRTrackerAnnouncer 					trackerClient;
	private final TRTrackerAnnouncerListener	trackerClientListener =
		new TRTrackerAnnouncerListener() {
			public void receivedTrackerResponse(TRTrackerAnnouncerResponse response) {
				
				/*Log.d(TAG, "receivedTrackerResponse() is called...");
				Throwable t = new Throwable();
				t.printStackTrace();*/
				
				PEPeerManager pm = controller.getPeerManager();
				if (pm != null) {
					pm.processTrackerResponse(response);
				}
				trackerListeners.dispatch(LDT_TL_ANNOUNCERESULT, response);
			}
			
			public void urlChanged(
				final TRTrackerAnnouncer	announcer,
				final URL	 				old_url,
				URL							new_url,
				boolean 					explicit) {
				if (explicit) {
					// flush connected peers on explicit url change
					if (torrent.getPrivate()) {
						final List<PEPeer>	peers;
						try {
							peerListenersMon.enter();
							peers = new ArrayList<PEPeer>( current_peers.keySet());
						} finally {
							peerListenersMon.exit();
						}
						new AEThread2("DM:torrentChangeFlusher", true) {
							public void run() {
								for (int i=0;i<peers.size();i++) {
									PEPeer	peer = (PEPeer)peers.get(i);
									peer.getManager().removePeer(peer, "Private torrent: tracker changed");
								}
							}
						}.start();
					}
					requestTrackerAnnounce(true);
				}
			}
			
			public void urlRefresh() {
				requestTrackerAnnounce(true);
			}
		};

	// a second listener used to catch and propagate the "stopped" event

	private final TRTrackerAnnouncerListener		stopping_tracker_client_listener =
		new TRTrackerAnnouncerListener() {
			public void receivedTrackerResponse(
				TRTrackerAnnouncerResponse	response) {
				if (trackerClient == null)
					response.setPeers(new TRTrackerAnnouncerResponsePeer[0]);
				trackerListeners.dispatch(LDT_TL_ANNOUNCERESULT, response);
			}

			public void urlChanged(
				TRTrackerAnnouncer	announcer,
				URL 				old_url,
				URL					new_url,
				boolean 			explicit ) {
			}

			public void urlRefresh() {
			}
		};


	private final CopyOnWriteList	activation_listeners = new CopyOnWriteList();

	private final long						scrape_random_seed	= SystemTime.getCurrentTime();

	private volatile Map<Object,Object>		data;

	private boolean data_already_allocated = false;

	private long	creation_time	= SystemTime.getCurrentTime();

	private int iSeedingRank;

	private boolean	dl_identity_obtained;
	private byte[]	dl_identity;
		private int 	dl_identity_hashcode;

		private int		max_uploads	= DownloadManagerState.MIN_MAX_UPLOADS;
		private int		max_connections;
		private int		max_connections_when_seeding;
		private boolean	max_connections_when_seeding_enabled;
		private int		max_seed_connections;
		private int		max_uploads_when_seeding	= DownloadManagerState.MIN_MAX_UPLOADS;
		private boolean	max_uploads_when_seeding_enabled;

		private int		max_upload_when_busy_bps;
		private int		current_upload_when_busy_bps;
		private long	last_upload_when_busy_update;
		private long	last_upload_when_busy_dec_time;
		private int		upload_priority_manual;
		private int		upload_priority_auto;

		private int		crypto_level 	= NetworkManager.CRYPTO_OVERRIDE_NONE;
		private int		message_mode	= -1;

	// Only call this with STATE_QUEUED, STATE_WAITING, or STATE_STOPPED unless you know what you are doing

		private volatile boolean	removing;
	private volatile boolean	destroyed;

	public DownloadManagerImpl(
		GlobalManager 							_gm,
		byte[]									_torrent_hash,
		String 									_torrentFileName,
		String 									_torrent_save_dir,
		String									_torrent_save_file,
		int	 									_initialState,
		boolean									_persistent,
		boolean									_recovered,
		boolean									_open_for_seeding,
		boolean									_has_ever_been_started,
		List									_file_priorities,
		DownloadManagerInitialisationAdapter	_initialisation_adapter) {

		if (_initialState != STATE_WAITING
				&& _initialState != STATE_STOPPED
				&& _initialState != STATE_QUEUED) {
			Debug.out("DownloadManagerImpl: Illegal start state, " + _initialState);
		}

		persistent			= _persistent;
		globalManager 		= _gm;
		open_for_seeding	= _open_for_seeding;

		// TODO: move this to download state!

			if (_file_priorities != null) {
				setData("file_priorities", _file_priorities);
			}

		stats = new DownloadManagerStatsImpl(this);
		controller	= new DownloadManagerController(this);
		torrentFileName = _torrentFileName;

		while (_torrent_save_dir.endsWith(File.separator)) {
			_torrent_save_dir = _torrent_save_dir.substring(0, _torrent_save_dir.length()-1);
		}

		// readTorrent adjusts the save dir and file to be sensible values
		readTorrent(_torrent_save_dir, _torrent_save_file, _torrent_hash,
				persistent && !_recovered, _open_for_seeding, _has_ever_been_started,
				_initialState);

		if (torrent != null) {

			if (_open_for_seeding && !_recovered) {

				Map<Integer,File> linkage = TorrentUtils.getInitialLinkage(torrent);

				if (linkage.size() > 0) {

					DownloadManagerState dms = getDownloadState();
					DiskManagerFileInfo[] files = getDiskManagerFileInfoSet().getFiles();

					try {
						dms.suppressStateSave(true);
						for (Map.Entry<Integer,File> entry: linkage.entrySet()) {
							int	index 	= entry.getKey();
							File target = entry.getValue();
							dms.setFileLink(index, files[index].getFile( false ), target);
						}
					} finally {
						dms.suppressStateSave(false);
					}
				}
			}

			if (_initialisation_adapter != null) {
				try {
					_initialisation_adapter.initialised(this, open_for_seeding);
				} catch (Throwable e) {
					Debug.printStackTrace(e);
				}
			}
		}
	}

	public int getTaggableType() {
		return (TT_DOWNLOAD);
	}

	public TaggableResolver getTaggableResolver() {
		return (globalManager);
	}

	public String getTaggableID() {
		return ( dl_identity==null?null:Base32.encode(dl_identity));
	}

	private void readTorrent(
		String		torrent_save_dir,
		String		torrent_save_file,
		byte[]		torrentHash,		// can be null for initial torrents
		boolean		new_torrent,		// probably equivalend to (torrent_hash == null)????
		boolean		for_seeding,
		boolean		has_ever_been_started,
		int			initial_state) {

		try {
			displayName				= torrentFileName;	// default if things go wrong decoding it
			internalName				= "";
			torrentComment				= "";
			torrentCreatedBy			= "";

			try {

				// this is the first thing we do and most likely to go wrong - hence its
				// existence is used below to indicate success or not

				downloadManagerState	=
						DownloadManagerStateImpl.getDownloadState(
								this,
								torrentFileName,
								torrentHash,
								initial_state == DownloadManager.STATE_STOPPED ||
								initial_state == DownloadManager.STATE_QUEUED);

				readParameters();

				// establish any file links

				DownloadManagerStateAttributeListener attr_listener =
					new DownloadManagerStateAttributeListener() {
						private final ThreadLocal<Boolean>	links_changing =
							new ThreadLocal<Boolean>() {
								protected Boolean initialValue() {
									return Boolean.FALSE;
								}
							};

						public void attributeEventOccurred(DownloadManager dm, String attribute_name, int event_type)	{
							if (attribute_name.equals(DownloadManagerState.AT_FILE_LINKS2)) {

								if (links_changing.get()) {
									System.out.println("recursive!");
									return;
								}

								links_changing.set(true);

								try {
									setFileLinks();
								} finally {
									links_changing.set(false);
								}
							} else if (attribute_name.equals(DownloadManagerState.AT_PARAMETERS)) {
								readParameters();
							} else if (attribute_name.equals(DownloadManagerState.AT_NETWORKS)) {

								TRTrackerAnnouncer tc = trackerClient;

								if (tc != null) {

									tc.resetTrackerUrl(false);
								}
							}
						}
					};

				downloadManagerState.addListener(attr_listener, DownloadManagerState.AT_FILE_LINKS2, DownloadManagerStateAttributeListener.WRITTEN);
				downloadManagerState.addListener(attr_listener, DownloadManagerState.AT_PARAMETERS, DownloadManagerStateAttributeListener.WRITTEN);
				downloadManagerState.addListener(attr_listener, DownloadManagerState.AT_NETWORKS, DownloadManagerStateAttributeListener.WRITTEN);

				torrent	= downloadManagerState.getTorrent();

				setFileLinks();

					// We can't have the identity of this download changing as this will screw up
					// anyone who tries to maintain a unique set of downloads (e.g. the GlobalManager)
					//

				if (!dl_identity_obtained) {
					// flag set true below
					dl_identity			= torrentHash==null?torrent.getHash():torrentHash;
					this.dl_identity_hashcode = new String(dl_identity).hashCode();
				 }

				 if (!Arrays.equals( dl_identity, torrent.getHash())) {
					 torrent	= null;	// prevent this download from being used

				 	// set up some kinda default else things don't work wel...
					 torrent_save_location = new File(torrent_save_dir, torrentFileName);
					 throw (new NoStackException("Download identity changed - please remove and re-add the download"));
				 }

				 read_torrent_state	= null;	// no longer needed if we saved it

				 LocaleUtilDecoder	locale_decoder = LocaleTorrentUtil.getTorrentEncoding(torrent);

			 	// if its a simple torrent and an explicit save file wasn't supplied, use
				// the torrent name itself

				displayName = FileUtil.convertOSSpecificChars(TorrentUtils.getLocalisedName(torrent),false);

				byte[] hash = torrent.getHash();

				internalName = ByteFormatter.nicePrint( hash, true);

					// now we know if its a simple torrent or not we can make some choices about
					// the save dir and file. On initial entry the save_dir will have the user-selected
					// save location and the save_file will be null

				File	save_dir_file	= new File(torrent_save_dir);

				// System.out.println("before: " + torrent_save_dir + "/" + torrent_save_file);

					// if save file is non-null then things have already been sorted out

				if (torrent_save_file == null) {

					// make sure we're working off a canonical save dir if possible
					try {
						if (save_dir_file.exists()) {
							save_dir_file = save_dir_file.getCanonicalFile();
						}
					} catch (Throwable e) {
						Debug.printStackTrace(e);
					}

				 	if (torrent.isSimpleTorrent()) {

			 			// if target save location is a directory then we use that as the save
			 			// dir and use the torrent display name as the target. Otherwise we
			 			// use the file name

				 		if (save_dir_file.exists()) {

				 			if (save_dir_file.isDirectory()) {
				 				torrent_save_file	= displayName;
				 			} else {
				 				torrent_save_dir	= save_dir_file.getParent().toString();
				 				torrent_save_file	= save_dir_file.getName();
				 			}
				 		} else {

			 				// doesn't exist, assume it refers directly to the file
				 			if (save_dir_file.getParent() == null) {
				 				throw (new NoStackException("Data location '" + torrent_save_dir + "' is invalid"));
				 			}

			 				torrent_save_dir	= save_dir_file.getParent().toString();

			 				torrent_save_file	= save_dir_file.getName();
				 		}

				 	} else {

				 			// torrent is a folder. It is possible that the natural location
				 			// for the folder is X/Y and that in fact 'Y' already exists and
				 			// has been selected. If ths is the case the select X as the dir and Y
				 			// as the file name

				 		if (save_dir_file.exists()) {

				 			if (!save_dir_file.isDirectory()) {

				 				throw (new NoStackException("'" + torrent_save_dir + "' is not a directory"));
				 			}

				 			if (save_dir_file.getName().equals( displayName)) {
				 				torrent_save_dir	= save_dir_file.getParent().toString();
				 			}
				 		}

				 		torrent_save_file	= displayName;
				 	}
				 }

				 torrent_save_location = new File(torrent_save_dir, torrent_save_file);

				 	// final validity test must be based of potentially linked target location as file
				 	// may have been re-targetted


					// if this isn't a new torrent then we treat the absence of the enclosing folder
					// as a fatal error. This is in particular to solve a problem with the use of
				 	// externally mounted torrent data on OSX, whereby a re-start with the drive unmounted
				 	// results in the creation of a local diretory in /Volumes that subsequently stuffs
				 	// up recovery when the volume is mounted

				 	// changed this to only report the error on non-windows platforms

				if (!(new_torrent || Constants.isWindows)) {

						// another exception here - if the torrent has never been started then we can
						// fairly safely continue as its in a stopped state

					if (has_ever_been_started) {

						 File	linked_target = getSaveLocation();

						 if (!linked_target.exists()) {

							throw (new NoStackException(
									MessageText.getString("DownloadManager.error.datamissing")
											+ " " + Debug.secretFileName(linked_target.toString())));
						}
				 	}
				 }

		 		// propagate properties from torrent to download

				boolean	low_noise = TorrentUtils.getFlag(torrent, TorrentUtils.TORRENT_FLAG_LOW_NOISE);

				if (low_noise) {
					downloadManagerState.setFlag(DownloadManagerState.FLAG_LOW_NOISE, true);
				}

				boolean	metadata_dl = TorrentUtils.getFlag(torrent, TorrentUtils.TORRENT_FLAG_METADATA_TORRENT);

				if (metadata_dl) {
					downloadManagerState.setFlag(DownloadManagerState.FLAG_METADATA_DOWNLOAD, true);
				}

			 	// if this is a newly introduced torrent trash the tracker cache. We do this to
			 	// prevent, say, someone publishing a torrent with a load of invalid cache entries
			 	// in it and a bad tracker URL. This could be used as a DOS attack

				 if (new_torrent) {

					downloadManagerState.setLongParameter( DownloadManagerState.PARAM_DOWNLOAD_ADDED_TIME, SystemTime.getCurrentTime());

					Map peer_cache = TorrentUtils.getPeerCache(torrent);

					if (peer_cache != null) {

						try {
							downloadManagerState.setTrackerResponseCache(peer_cache);

						} catch (Throwable e) {

							Debug.out(e);

							downloadManagerState.setTrackerResponseCache(new HashMap());
						}
					} else {

						downloadManagerState.setTrackerResponseCache(new HashMap());
					}

				 		// also remove resume data incase someone's published a torrent with resume
				 		// data in it

				 	if (for_seeding) {

				 		DiskManagerFactory.setTorrentResumeDataNearlyComplete(downloadManagerState);

				 		// Prevent download being considered for on-completion moving - it's considered complete anyway.
				 		downloadManagerState.setFlag(DownloadManagerState.FLAG_MOVE_ON_COMPLETION_DONE, true);

				 	} else {

				 		downloadManagerState.clearResumeData();
				 	}

				 		// set up the dnd-subfolder status on addition

				 	if (persistent && !for_seeding && !torrent.isSimpleTorrent()) {

				 		String dnd_sf = dnd_subfolder;

				 		if (dnd_sf != null) {

				 			if (torrent.getFiles().length <= DownloadManagerStateFactory.MAX_FILES_FOR_INCOMPLETE_AND_DND_LINKAGE) {

				 				if (downloadManagerState.getAttribute(DownloadManagerState.AT_DND_SUBFOLDER) == null) {

				 					downloadManagerState.setAttribute(DownloadManagerState.AT_DND_SUBFOLDER, dnd_sf);
				 				}

								boolean	use_prefix = COConfigurationManager.getBooleanParameter("Use Incomplete File Prefix");

								if (use_prefix) {

					 				if (downloadManagerState.getAttribute(DownloadManagerState.AT_DND_PREFIX) == null) {

										String prefix = Base32.encode(hash ).substring( 0, 12 ).toLowerCase( Locale.US) + "_";

					 					downloadManagerState.setAttribute(DownloadManagerState.AT_DND_PREFIX, prefix);
					 				}
								}
										}
									}
								}
				 } else {

					 long	add_time = downloadManagerState.getLongParameter(DownloadManagerState.PARAM_DOWNLOAD_ADDED_TIME);

					 if (add_time == 0) {

						 // grab an initial value from torrent file - migration only

						 try {
							 add_time = new File(torrentFileName).lastModified();

						 } catch (Throwable e) {
						 }

						 if (add_time == 0) {

							 add_time = SystemTime.getCurrentTime();
						 }

						 downloadManagerState.setLongParameter(DownloadManagerState.PARAM_DOWNLOAD_ADDED_TIME, add_time);
					 }
				 }


				 //trackerUrl = torrent.getAnnounceURL().toString();

				torrentComment = StringInterner.intern(locale_decoder.decodeString(torrent.getComment()));

				if (torrentComment == null) {

					 torrentComment	= "";
				}

				torrentCreatedBy = locale_decoder.decodeString(torrent.getCreatedBy());

				if (torrentCreatedBy == null) {

					torrentCreatedBy	= "";
				}

				 	// only restore the tracker response cache for non-seeds

				 if (downloadManagerState.isResumeDataComplete() || for_seeding) {

					 	// actually, can't think of a good reason not to restore the
					 	// cache for seeds, after all if the tracker's down we still want
					 	// to connect to peers to upload to

						// download_manager_state.clearTrackerResponseCache();

						stats.setDownloadCompletedBytes(getSize());

						setAssumedComplete(true);

				 } else {

					 setAssumedComplete(false);
				}

				if (downloadManagerState.getDisplayName() == null) {

					String title = PlatformTorrentUtils.getContentTitle(torrent);

					if (title != null && title.length() > 0) {

						downloadManagerState.setDisplayName(title);
					}
				}
			} catch (TOTorrentException e) {

				//Debug.printStackTrace(e);

				setFailed(TorrentUtils.exceptionToText( e));

			} catch (UnsupportedEncodingException e) {

				Debug.printStackTrace(e);

				setFailed( MessageText.getString("DownloadManager.error.unsupportedencoding"));

			} catch (NoStackException e) {

				Debug.outNoStack( e.getMessage());

			} catch (Throwable e) {

				Debug.printStackTrace(e);

				setFailed(e);

			} finally {

				 dl_identity_obtained	= true;
			}

			if (downloadManagerState == null) {
				read_torrent_state =
					new Object[]{
						torrent_save_dir, torrent_save_file, torrentHash,
						Boolean.valueOf(new_torrent), Boolean.valueOf(for_seeding), Boolean.valueOf(has_ever_been_started),
						new Integer(initial_state)
					};

					// torrent's stuffed - create a dummy "null object" to simplify use
					// by other code

				downloadManagerState	= DownloadManagerStateImpl.getDownloadState(this);

					// make up something vaguely sensible for save location

				if (torrent_save_file == null) {

					torrent_save_location = new File(torrent_save_dir);

				} else {

					torrent_save_location = new File(torrent_save_dir, torrent_save_file);
				}

			} else {


					// make up something vaguely sensible for save location if we haven't got one

				if (torrent_save_file == null) {

					torrent_save_location = new File(torrent_save_dir);
				}

					// make sure we know what networks to use for this download

				if (torrent != null && !downloadManagerState.hasAttribute( DownloadManagerState.AT_NETWORKS)) {

					String[] networks = AENetworkClassifier.getNetworks(torrent, displayName);

					downloadManagerState.setNetworks(networks);
				}
			}
		} finally {

			if (torrent_save_location != null) {

				boolean	already_done = false;

				String cache = downloadManagerState.getAttribute(DownloadManagerState.AT_CANONICAL_SD_DMAP);

				if (cache != null) {

					String key = torrent_save_location.getAbsolutePath() + "\n";

					if (cache.startsWith( key)) {

						torrent_save_location = new File( cache.substring( key.length()));

						already_done = true;
					}
				}

				if (!already_done) {

					String key = torrent_save_location.getAbsolutePath() + "\n";

					try {
						torrent_save_location = torrent_save_location.getCanonicalFile();

					} catch (Throwable e) {

						torrent_save_location = torrent_save_location.getAbsoluteFile();
					}

					downloadManagerState.setAttribute(
						DownloadManagerState.AT_CANONICAL_SD_DMAP,
						key + torrent_save_location.getAbsolutePath());
				}
					// update cached stuff in case something changed

				getSaveLocation();
			}

				// must be after torrent read, so that any listeners have a TOTorrent
				// not that if things have failed above this method won't override a failed
				// state with the initial one

			controller.setInitialState(initial_state);
		}
	}

	protected void readTorrent() {
		if (read_torrent_state == null) {

			return;
		}

		readTorrent(
				(String)read_torrent_state[0],
				(String)read_torrent_state[1],
				(byte[])read_torrent_state[2],
				((Boolean)read_torrent_state[3]).booleanValue(),
				((Boolean)read_torrent_state[4]).booleanValue(),
				((Boolean)read_torrent_state[5]).booleanValue(),
				((Integer)read_torrent_state[6]).intValue());

	}

	protected void readParameters() {
		max_connections							= getDownloadState().getIntParameter(DownloadManagerState.PARAM_MAX_PEERS);
		max_connections_when_seeding_enabled	= getDownloadState().getBooleanParameter(DownloadManagerState.PARAM_MAX_PEERS_WHEN_SEEDING_ENABLED);
		max_connections_when_seeding			= getDownloadState().getIntParameter(DownloadManagerState.PARAM_MAX_PEERS_WHEN_SEEDING);
		max_seed_connections					= getDownloadState().getIntParameter(DownloadManagerState.PARAM_MAX_SEEDS);
		max_uploads						 		= getDownloadState().getIntParameter(DownloadManagerState.PARAM_MAX_UPLOADS);
		max_uploads_when_seeding_enabled 		= getDownloadState().getBooleanParameter(DownloadManagerState.PARAM_MAX_UPLOADS_WHEN_SEEDING_ENABLED);
		max_uploads_when_seeding 				= getDownloadState().getIntParameter(DownloadManagerState.PARAM_MAX_UPLOADS_WHEN_SEEDING);
		max_upload_when_busy_bps				= getDownloadState().getIntParameter(DownloadManagerState.PARAM_MAX_UPLOAD_WHEN_BUSY) * 1024;

		max_uploads = Math.max(max_uploads, DownloadManagerState.MIN_MAX_UPLOADS);
		max_uploads_when_seeding = Math.max(max_uploads_when_seeding, DownloadManagerState.MIN_MAX_UPLOADS);

		upload_priority_manual					= getDownloadState().getIntParameter(DownloadManagerState.PARAM_UPLOAD_PRIORITY);
	}

	protected int[]
	getMaxConnections(boolean mixed) {
		if (mixed && max_connections > 0) {

			return (new int[]{ max_connections, max_connections_npp_extra });
		}

		return (new int[]{ max_connections, 0 });
	}

	protected int[]
	getMaxConnectionsWhenSeeding(boolean mixed) {
		if (mixed && max_connections_when_seeding > 0) {

			return (new int[]{ max_connections_when_seeding, max_connections_npp_extra });
		}

		return (new int[]{ max_connections_when_seeding, 0 });
	}

	protected boolean isMaxConnectionsWhenSeedingEnabled() {
		return (max_connections_when_seeding_enabled);
	}

	protected int[]
	getMaxSeedConnections(boolean mixed) {
		if (mixed && max_seed_connections > 0) {

			return (new int[]{ max_seed_connections, max_connections_npp_extra });
		}

		return (new int[]{ max_seed_connections, 0 });
	}

	protected boolean isMaxUploadsWhenSeedingEnabled() {
		return (max_uploads_when_seeding_enabled);
	}

	protected int getMaxUploadsWhenSeeding() {
		return (max_uploads_when_seeding);
	}

	public void updateAutoUploadPriority(
		Object		key,
		boolean		inc) {
		try {
				peerListenersMon.enter();

				boolean	key_exists = getUserData(key) != null;

				if (inc && !key_exists) {

					upload_priority_auto++;

					setUserData(key, "");

				} else if (!inc && key_exists) {

					upload_priority_auto--;

					setUserData(key, null);
				}
		} finally {

			peerListenersMon.exit();
		}
	}

	protected int getEffectiveUploadPriority() {
		return (upload_priority_manual + upload_priority_auto);
	}

	public int getMaxUploads() {
		return (max_uploads);
	}

	public void setMaxUploads(
		int	max) {
		downloadManagerState.setIntParameter(DownloadManagerState.PARAM_MAX_UPLOADS, max);
	}

	public void setManualUploadPriority(
		int	priority) {
		downloadManagerState.setIntParameter(DownloadManagerState.PARAM_UPLOAD_PRIORITY, priority);
	}

	public int getEffectiveMaxUploads() {
		if (isMaxUploadsWhenSeedingEnabled() && getState() == DownloadManager.STATE_SEEDING) {

			return ( getMaxUploadsWhenSeeding());

		} else {

			return (max_uploads);
		}
	}

	public int getEffectiveUploadRateLimitBytesPerSecond() {
		int	local_max_bps	= stats.getUploadRateLimitBytesPerSecond();
		int	rate			= local_max_bps;

		if (max_upload_when_busy_bps != 0) {

			long	now = SystemTime.getCurrentTime();

			if (now < last_upload_when_busy_update || now - last_upload_when_busy_update > 5000) {

				last_upload_when_busy_update	= now;

					// might need to impose the limit

				String key = TransferSpeedValidator.getActiveUploadParameter(globalManager);

				int	global_limit_bps = COConfigurationManager.getIntParameter(key)*1024;

				if (global_limit_bps > 0 && max_upload_when_busy_bps < global_limit_bps) {

						// we have a global limit and a valid busy limit

					local_max_bps = local_max_bps==0?global_limit_bps:local_max_bps;

					GlobalManagerStats gm_stats = globalManager.getStats();

					int	actual = gm_stats.getDataSendRateNoLAN() + gm_stats.getProtocolSendRateNoLAN();

					int	move_by = (local_max_bps - max_upload_when_busy_bps) / 10;

					if (move_by < 1024) {

						move_by = 1024;
					}

					if (global_limit_bps - actual <= 2*1024) {

							// close enough to impose the busy limit downwards


						if (current_upload_when_busy_bps == 0) {

							current_upload_when_busy_bps = local_max_bps;
						}

						int	prev_upload_when_busy_bps = current_upload_when_busy_bps;

						current_upload_when_busy_bps -= move_by;

						if (current_upload_when_busy_bps < max_upload_when_busy_bps) {

							current_upload_when_busy_bps = max_upload_when_busy_bps;
						}

						if (current_upload_when_busy_bps < prev_upload_when_busy_bps) {

							last_upload_when_busy_dec_time = now;
						}
					} else {

							// not hitting limit, increase

						if (current_upload_when_busy_bps != 0) {

								// only try increment if sufficient time passed

							if (	upload_when_busy_min_secs == 0 ||
									now < last_upload_when_busy_dec_time ||
									now - last_upload_when_busy_dec_time >=	upload_when_busy_min_secs*1000L) {

								current_upload_when_busy_bps += move_by;

								if (current_upload_when_busy_bps >= local_max_bps) {

									current_upload_when_busy_bps	= 0;
								}
							}
						}
					}

					if (current_upload_when_busy_bps > 0) {

						rate = current_upload_when_busy_bps;
					}
				} else {

					current_upload_when_busy_bps = 0;
				}
			} else {

				if (current_upload_when_busy_bps > 0) {

					rate = current_upload_when_busy_bps;
				}
			}
		}

		return (rate);
	}

	protected void setFileLinks() {
			// invalidate the cache info in case its now wrong

		cached_save_location	= null;

		DiskManagerFactory.setFileLinks( this, downloadManagerState.getFileLinks());

		controller.fileInfoChanged();
	}

	protected void clearFileLinks() {
		downloadManagerState.clearFileLinks();
	}

	private void updateFileLinks(
		File old_save_path, File new_save_path) {
		try {old_save_path = old_save_path.getCanonicalFile();}
		catch (IOException ioe) {old_save_path = old_save_path.getAbsoluteFile();}
		try {new_save_path = new_save_path.getCanonicalFile();}
		catch (IOException ioe) {new_save_path = new_save_path.getAbsoluteFile();}

		String old_path = old_save_path.getPath();
		String new_path = new_save_path.getPath();

		//System.out.println("update_file_links: " + old_path +	" -> " + new_path);

		LinkFileMap links = downloadManagerState.getFileLinks();

		Iterator<LinkFileMap.Entry> it = links.entryIterator();

		List<Integer>	from_indexes 	= new ArrayList<Integer>();
		List<File>		from_links 		= new ArrayList<File>();
		List<File>		to_links		= new ArrayList<File>();

		while (it.hasNext()) {

			LinkFileMap.Entry entry = it.next();

			try {

				File	to			= entry.getToFile();

				if (to == null) {

						// represents a deleted link, nothing to update

					continue;
				}

				try {
					to = to.getCanonicalFile();
				} catch (Throwable e) {
				}

				int		file_index 	= entry.getIndex();
				File	from 		= entry.getFromFile();

				try {
					from = from.getCanonicalFile();
				} catch (Throwable e) {
				}

				String	from_s	= from.getAbsolutePath();
				String	to_s		= to.getAbsolutePath();

				updateFileLink(file_index, old_path, new_path, from_s, to_s, from_indexes, from_links, to_links);

			} catch (Exception e) {

				Debug.printStackTrace(e);
			}
		}

		if (from_links.size() > 0) {

			downloadManagerState.setFileLinks(from_indexes, from_links, to_links);
		}
	}

	// old_path -> Full location of old torrent (inclusive of save name)
	// from_loc -> Old unmodified location of file within torrent.
	// to_loc -> Old modified location of file (where the link points to).
	//
	// We have to update from_loc and to_loc.
	// We should always be modifying from_loc. Only modify to_loc if it sits within
	// the old path.

	private void updateFileLink(
		int				file_index,
		String 			old_path,
		String 			new_path,
		String 			from_loc,
		String 			to_loc,
		List<Integer>	from_indexes,
		List<File> 		from_links,
		List<File> 		to_links) {
		//System.out.println("ufl: " + file_index + "\n	" + old_path + " - " + new_path + "\n	" + from_loc + " - " + to_loc);

		if (torrent.isSimpleTorrent()) {

			if (!old_path.equals( from_loc)) {

				throw new RuntimeException("assert failure: old_path=" + old_path + ", from_loc=" + from_loc);
			}

			//System.out.println("	 adding " + old_path + " -> null");

			from_indexes.add(0);
			from_links.add(new File(old_path));
			to_links.add(null);

				// in general links on simple torrents aren't used, instead the download's save-path is switched to the
				// alternate location (only a single file after all this is simplest implementation). Unfortunately links can
				// actually still be set (e.g. to add an 'incomplete' suffix to a file) so we still need to support link-rewriting
				// properly

				// so the only valid linking on a simple torrent is to rename the file, not to point to a file in an
				// alternative directory. Thus fixing up the link becomes a case of taking the target file name from the old link
				// and making it the target file naem of the new link within the new path's parent folder


			String new_path_parent;
			int pos = new_path.lastIndexOf(File.separatorChar);
			if (pos != -1) {
				new_path_parent = new_path.substring(0, pos);
			} else {
				Debug.out("new_path " + new_path + " missing file separator, not good");

				new_path_parent = new_path;
			}

			String to_loc_name;
			pos = to_loc.lastIndexOf(File.separatorChar);
			if (pos != -1) {
				to_loc_name = to_loc.substring(pos+1);
			} else {
				Debug.out("to_loc " + to_loc + " missing file separator, not good");

				to_loc_name = to_loc;
			}

			String to_loc_to_use = new_path_parent + File.separatorChar + to_loc_name;

			//System.out.println("	 adding " + new_path + " -> " + to_loc_to_use);
			from_indexes.add(0);
			from_links.add(new File(new_path));
			to_links.add(new File(to_loc_to_use));

		} else {

			String from_loc_to_use = FileUtil.translateMoveFilePath(old_path, new_path, from_loc);

			if (from_loc_to_use == null) {

				return;
			}

			String to_loc_to_use = FileUtil.translateMoveFilePath(old_path, new_path, to_loc);

			if (to_loc_to_use == null) {

				to_loc_to_use = to_loc;
			}

				// delete old

			from_indexes.add(file_index);
			from_links.add(new File(from_loc));
			to_links.add(null);

				// add new

			from_indexes.add(file_index);
			from_links.add(new File(from_loc_to_use));
			to_links.add(new File(to_loc_to_use));
		}
	}

	/**
	 * @deprecated
	 */

	public boolean filesExist() {
		return (filesExist( true));
	}

	public boolean filesExist(
		boolean expected_to_be_allocated) {
		return (controller.filesExist( expected_to_be_allocated));
	}


	public boolean isPersistent() {
		return (persistent);
	}

	public String getDisplayName() {
		DownloadManagerState dms = this.getDownloadState();
		if (dms != null) {
			String result = dms.getDisplayName();
			if (result != null) {return result;}
		}
		return (displayName);
	}

 	public String getInternalName()
		{
 		return (internalName);
		}

	public String getErrorDetails() {
		return ( controller.getErrorDetail());
	}

	public int getErrorType() {
		return ( controller.getErrorType());
	}

	public long getSize() {
		if (torrent != null) {

			return torrent.getSize();
		}

		return 0;
	}

	protected void setFailed() {
		setFailed((String)null);
	}

	protected void setFailed(
		Throwable 	e) {
		setFailed( Debug.getNestedExceptionMessage(e));
	}

	protected void setFailed(
		String	str) {
		controller.setFailed(str);
	}

	protected void setTorrentInvalid(
		String	str) {
		setFailed(str);

		torrent	= null;
	}


	public void saveResumeData() {
		if (getState() == STATE_DOWNLOADING) {

			try {
				getDiskManager().saveResumeData(true);

			} catch (Exception e) {

				setFailed("Resume data save fails: " + Debug.getNestedExceptionMessage(e));
			}
		}

		// we don't want to update the torrent if we're seeding

		if (!assumedComplete) {

			downloadManagerState.save();
		}
	}

		public void
		saveDownload()
		{
			DiskManager disk_manager = controller.getDiskManager();

			if (disk_manager != null) {

				disk_manager.saveState();
			}

			downloadManagerState.save();
		}


	public void initialize() {
			// entry:	valid if waiting, stopped or queued
			// exit: error, ready or on the way to error
		if (torrent == null) {
				// have a go at re-reading the torrent in case its been recovered
			readTorrent();
		}
		if (torrent == null) {
			setFailed();
			return;
		}
		
		// If a torrent that is assumed complete, verify that it actually has the
		// files, before we create the diskManager, which will try to allocate
		// disk space.
		if (assumedComplete && !filesExist(true)) {
			// filesExist() has set state to error for us
			// If the user wants to re-download the missing files, they must
			// do a re-check, which will reset the flag.
			return;
		}
		downloadManagerState.setActive(true);
		
		try {
			try {
				thisMon.enter();
				if (trackerClient != null) {
					Debug.out("DownloadManager: initialize called with tracker client still available");
					trackerClient.destroy();
				}
				trackerClient =
					TRTrackerAnnouncerFactory.create(
						torrent,
						new TRTrackerAnnouncerFactory.DataProvider() {
							public String[] getNetworks() {
								return (downloadManagerState.getNetworks());
							}
						});
				trackerClient.setTrackerResponseCache(downloadManagerState.getTrackerResponseCache());
				trackerClient.addListener(trackerClientListener);
			} finally {
				thisMon.exit();
			}
			
					// we need to set the state to "initialized" before kicking off the disk manager
					// initialisation as it should only report its status while in the "initialized"
					// state (see getState for how this works...)
			try {
				controller.initializeDiskManager(open_for_seeding);
			} finally {
				// only supply this the very first time the torrent starts so the controller can check
				// that things are ok. Subsequent restarts are under user control
				open_for_seeding	= false;
			}
		} catch (TRTrackerAnnouncerException e) {
			setFailed(e);
		}
	}


	public void setStateWaiting() {
		checkResuming();

		controller.setStateWaiting();
	}

		public void
		setStateFinishing()
		{
			controller.setStateFinishing();
		}

		public void
		setStateQueued()
		{
			checkResuming();

			controller.setStateQueued();
		}

		public int
		getState()
		{
			return ( controller.getState());
		}

		public int
		getSubState()
		{
			return ( controller.getSubState());
		}

		public boolean
		canForceRecheck()
		{
		if (getTorrent() == null) {

					// broken torrent, can't force recheck

			return (false);
			}

			return ( controller.canForceRecheck());
		}

		public void
		forceRecheck()
		{
			controller.forceRecheck(null);
		}

		public void
		forceRecheck(final ForceRecheckListener l)
		{
			controller.forceRecheck(l);
		}

		public void
		setPieceCheckingEnabled(
			boolean enabled )
		{
			controller.setPieceCheckingEnabled(enabled);
		}

		public void
		resetFile(
			DiskManagerFileInfo		file )
		{
		int	state = getState();

			if (	state == DownloadManager.STATE_STOPPED ||
					state == DownloadManager.STATE_ERROR) {

				DiskManagerFactory.clearResumeData(this, file);

			} else {

				Debug.out("Download not stopped");
			}
		}

	public void	recheckFile(DiskManagerFileInfo file) {
		int	state = getState();

		if (state == DownloadManager.STATE_STOPPED ||
				state == DownloadManager.STATE_ERROR) {
			DiskManagerFactory.recheckFile(this, file);
		} else {
			Debug.out("Download not stopped");
		}
	}

	public void startDownload() {
		/*Log.d(TAG, "startDownload() is called...");
		new Throwable().printStackTrace();*/

		message_mode = -1;	// reset it here
		controller.startDownload(getTrackerClient());
	}

	public void stopIt(
		int 		state_after_stopping,
		boolean 	remove_torrent,
		boolean 	remove_data) {
		stopIt(state_after_stopping, remove_torrent, remove_data, false);
	}

	public void stopIt(
		int 		state_after_stopping,
		boolean 	remove_torrent,
		boolean		remove_data,
		boolean		for_removal) {
		if (for_removal) {

			removing = true;
		}

		try {
			boolean closing = state_after_stopping == DownloadManager.STATE_CLOSED;
			int curState = getState();
			boolean alreadyStopped = curState == STATE_STOPPED
					|| curState == STATE_STOPPING || curState == STATE_ERROR;
			boolean skipSetTimeStopped = alreadyStopped
					|| (closing && curState == STATE_QUEUED);

			if (!skipSetTimeStopped) {
				downloadManagerState.setLongAttribute(
						DownloadManagerState.AT_TIME_STOPPED, SystemTime.getCurrentTime());
			}

			controller.stopIt(state_after_stopping, remove_torrent, remove_data,for_removal);

		} finally {

			downloadManagerState.setActive(false);
		}
	}

	private void checkResuming() {
		globalManager.resumingDownload(this);
	}

	public boolean pause() {
		return (globalManager.pauseDownload( this));
	}

	public boolean pause(
		long	_resume_time) {
			// obviously we should manage the timer+resumption but it works as it is at the moment...

			// we're not yet in a stopped state so indicate this with a negative value - it'll be corrected when the download stops

		resumeTime	= -_resume_time;

		return (globalManager.pauseDownload( this));
	}

	public long getAutoResumeTime() {
		return (resumeTime);
	}
	public boolean isPaused() {
		return (globalManager.isPaused( this));
	}

	public void resume() {
		globalManager.resumeDownload(this);
	}

	public boolean getAssumedComplete() {
		return assumedComplete;
	}

	public boolean requestAssumedCompleteMode() {
		boolean bCompleteNoDND = controller.isDownloadComplete(false);

		setAssumedComplete(bCompleteNoDND);
		return bCompleteNoDND;
	}

	// Protected: Use requestAssumedCompleteMode outside of scope
	protected void setAssumedComplete(boolean _assumedComplete) {
		if (_assumedComplete) {
			long completedOn = downloadManagerState.getLongParameter(DownloadManagerState.PARAM_DOWNLOAD_COMPLETED_TIME);
			if (completedOn <= 0) {
				downloadManagerState.setLongParameter(
						DownloadManagerState.PARAM_DOWNLOAD_COMPLETED_TIME,
						SystemTime.getCurrentTime());
			}
		}

		if (assumedComplete == _assumedComplete) {
			return;
		}

		//Logger.log(new LogEvent(this, LogIDs.CORE, "setAssumedComplete("
		//		+ _assumedComplete + ") was " + assumedComplete));

		assumedComplete = _assumedComplete;

		if (!assumedComplete) {
			controller.setStateDownloading();
		}

		// NOTE: We don't set "stats.setDownloadCompleted(1000)" anymore because
		//			 we can be in seeding mode with an unfinished torrent

		if (position != -1) {
			// we are in a new list, move to the top of the list so that we continue
			// seeding.
			// -1 position means it hasn't been added to the global list.	We
			// shouldn't touch it, since it'll get a position once it's adding is
			// complete

			DownloadManager[] dms = { DownloadManagerImpl.this };

			// pretend we are at the bottom of the new list
			// so that move top will shift everything down one

			position = globalManager.getDownloadManagers().size() + 1;

			if (COConfigurationManager.getBooleanParameter(CFG_MOVE_COMPLETED_TOP)) {

				globalManager.moveTop(dms);

			} else {

				globalManager.moveEnd(dms);
			}

			// we left a gap in incomplete list, fixup

			globalManager.fixUpDownloadManagerPositions();
		}

		listeners.dispatch(LDT_COMPLETIONCHANGED, new Object[] {
				this,
			Boolean.valueOf(_assumedComplete)
		});
	}


	public int getNbSeeds() {
		PEPeerManager peerManager = controller.getPeerManager();
		if (peerManager != null) {
			return peerManager.getNbSeeds();
		}
		return 0;
	}

	public int getNbPeers() {
		PEPeerManager peerManager = controller.getPeerManager();
		if (peerManager != null) {
			return peerManager.getNbPeers();
		}
		return 0;
	}

	public String getTrackerStatus() {
		TRTrackerAnnouncer tc = getTrackerClient();
		if (tc != null) {
			return tc.getStatusString();
		}
		// no tracker, return scrape
		if (torrent != null) {
			TRTrackerScraperResponse response = getTrackerScrapeResponse();
			if (response != null) {
				return response.getStatusString();
			}
		}
		return "";
	}

	public TRTrackerAnnouncer getTrackerClient() {
		return (trackerClient);
	}

	public void setAnnounceResult(DownloadAnnounceResult result) {
		TRTrackerAnnouncer	cl = getTrackerClient();
		if (cl == null) {
			// this can happen due to timing issues - not work debug spew for
			// Debug.out("setAnnounceResult called when download not running");
			return;
		}
		cl.setAnnounceResult(result);
	}

	public void setScrapeResult(DownloadScrapeResult result) {
		if (torrent != null && result != null) {
			TRTrackerScraper	scraper = globalManager.getTrackerScraper();
			TRTrackerScraperResponse current_resp = getTrackerScrapeResponse();
			URL	target_url;
			if (current_resp != null) {
				target_url = current_resp.getURL();
			} else {
				target_url = torrent.getAnnounceURL();
			}
			scraper.setScrape(torrent, target_url, result);
		}
	}

	public int getNbPieces() {
		if (torrent == null) {
			return (0);
		}
		return ( torrent.getNumberOfPieces());
	}


	public int getTrackerTime() {
		TRTrackerAnnouncer tc = getTrackerClient();

		if (tc != null) {

			return ( tc.getTimeUntilNextUpdate());
		}

			// no tracker, return scrape

		if (torrent != null) {

			TRTrackerScraperResponse response = getTrackerScrapeResponse();

			if (response != null) {

				if (response.getStatus() == TRTrackerScraperResponse.ST_SCRAPING) {

					return (-1);
				}

				return (int)((response.getNextScrapeStartTime() - SystemTime.getCurrentTime()) / 1000);
			}
		}

		return (TRTrackerAnnouncer.REFRESH_MINIMUM_SECS);
	}


		public TOTorrent
		getTorrent()
		{
			return (torrent);
		}

 	private File	cached_save_location;
	private File	cached_save_location_result;

		public File
	getSaveLocation()
		{
				// this can be called quite often - cache results for perf reasons

			File	save_location	= torrent_save_location;

			if (save_location == cached_save_location) {

				return (cached_save_location_result);
			}

 		File	res;

 		if (torrent == null || torrent.isSimpleTorrent()) {

 			res = downloadManagerState.getFileLink(0, save_location);

 		} else {

 			res = save_location;
 		}

 		if (res == null || res.equals(save_location)) {

 			res	= save_location;
 		} else {

 			try {
				res = res.getCanonicalFile();

			} catch (Throwable e) {

				res = res.getAbsoluteFile();
			}
 		}

 		cached_save_location		= save_location;
 		cached_save_location_result	= res;

 		return (res);
 	}

		public File
		getAbsoluteSaveLocation()
		{
			return (torrent_save_location);
		}

	public void setTorrentSaveDir(String new_dir) {
		setTorrentSaveDir(new_dir, this.getAbsoluteSaveLocation().getName());
	}

	public void setTorrentSaveDir(String new_dir, String dl_name) {
		File old_location = torrent_save_location;
		File new_location = new File(new_dir, dl_name);

		if (new_location.equals(old_location)) {
			return;
		}

			// assumption here is that the caller really knows what they are doing. You can't
			// just change this willy nilly, it must be synchronised with reality. For example,
			// the disk-manager calls it after moving files on completing
			// The UI can call it as long as the torrent is stopped.
			// Calling it while a download is active will in general result in unpredictable behaviour!

		updateFileLinks( old_location, new_location);

		torrent_save_location = new_location;

		String key = torrent_save_location.getAbsolutePath() + "\n";

		try {
			torrent_save_location = torrent_save_location.getCanonicalFile();

		} catch (Throwable e) {

			torrent_save_location = torrent_save_location.getAbsoluteFile();
		}

		downloadManagerState.setAttribute(
			DownloadManagerState.AT_CANONICAL_SD_DMAP,
			key + torrent_save_location.getAbsolutePath());

		Logger.log(new LogEvent(this, LogIDs.CORE, "Torrent save directory changing from \"" + old_location.getPath() + "\" to \"" + new_location.getPath()));

		// Trying to fix a problem where downloads are being moved into the program
		// directory on my machine, and I don't know why...
		//Debug.out("Torrent save directory changing from \"" + old_location.getPath() + "\" to \"" + new_location.getPath());

		controller.fileInfoChanged();
	}

	public String getPieceLength() {
		if (torrent != null) {
			return ( DisplayFormatters.formatByteCountToKiBEtc(torrent.getPieceLength()));
		}

		return ("");
	}

	public String getTorrentFileName() {
		return torrentFileName;
	}

	public void setTorrentFileName(
		String string) {
		torrentFileName = string;
	}

		// this is called asynchronously when a response is received

 	public void
 	setTrackerScrapeResponse(
		TRTrackerScraperResponse	response )
 	{
				// this is a reasonable place to pick up the change in active url caused by this scrape
				// response and update the torrent's url accordingly

		Object[] res = getActiveScrapeResponse();

		URL	active_url = (URL)res[1];

		if (active_url != null && torrent != null) {

			torrent.setAnnounceURL(active_url);
		}

		if (response != null) {
			int state = getState();
			if (state == STATE_ERROR || state == STATE_STOPPED) {
				long minNextScrape;
				if (response.getStatus() == TRTrackerScraperResponse.ST_INITIALIZING) {
					minNextScrape = SystemTime.getCurrentTime()
							+ (state == STATE_ERROR ? SCRAPE_INITDELAY_ERROR_TORRENTS
									: SCRAPE_INITDELAY_STOPPED_TORRENTS);
				} else {
					minNextScrape = SystemTime.getCurrentTime()
							+ (state == STATE_ERROR ? SCRAPE_DELAY_ERROR_TORRENTS
									: SCRAPE_DELAY_STOPPED_TORRENTS);
				}
				if (response.getNextScrapeStartTime() < minNextScrape) {
					response.setNextScrapeStartTime(minNextScrape);
				}
			} else if (!response.isValid() && response.getStatus() == TRTrackerScraperResponse.ST_INITIALIZING) {
				long minNextScrape;
				// Spread the scrapes out a bit.	This is extremely helpfull on large
				// torrent lists, and trackers that do not support multi-scrapes.
				// For trackers that do support multi-scrapes, it will really delay
				// the scrape for all torrent in the tracker to the one that has
				// the lowest share ratio.
				int sr = getStats().getShareRatio();
				minNextScrape = SystemTime.getCurrentTime()
						+ ((sr > 10000 ? 10000 : sr + 1000) * 60);

				if (response.getNextScrapeStartTime() < minNextScrape) {
					response.setNextScrapeStartTime(minNextScrape);
				}
			}

			// Need to notify listeners, even if scrape result is not valid, in
			// case they parse invalid scrapes

			if (response.isValid() && response.getStatus() == TRTrackerScraperResponse.ST_ONLINE) {

				long cache = ((((long)response.getSeeds())&0x00ffffffL)<<32)|(((long)response.getPeers())&0x00ffffffL);

				downloadManagerState.setLongAttribute(DownloadManagerState.AT_SCRAPE_CACHE, cache);
			}

			trackerListeners.dispatch(LDT_TL_SCRAPERESULT, response);
		}
	}

	public TRTrackerScraperResponse
	getTrackerScrapeResponse() {
		Object[] res = getActiveScrapeResponse();

		return ((TRTrackerScraperResponse)res[0]);
	}

		/**
		 * Returns the "first" online scrape response found, and its active URL, otherwise one of the failing
		 * scrapes
		 * @return
		 */

	protected Object[]
	getActiveScrapeResponse() {
		TRTrackerScraperResponse 	response	= null;
			 	URL							active_url	= null;

		TRTrackerScraper	scraper = globalManager.getTrackerScraper();

		TRTrackerAnnouncer tc = getTrackerClient();

		if (tc != null) {

			response = scraper.scrape(tc);
		}

		if (response == null && torrent != null) {

				// torrent not running. For multi-tracker torrents we need to behave sensibly
						// here

			TRTrackerScraperResponse	non_null_response = null;

			TOTorrentAnnounceURLSet[]	sets;
			try {
				sets = torrent.getAnnounceURLGroup().getAnnounceURLSets();
			} catch (Exception e) {
				return (new Object[]{ scraper.scrape(torrent), active_url });
			}

			if (sets.length == 0) {

				response = scraper.scrape(torrent);

			} else {

				URL							backup_url 		= null;
				TRTrackerScraperResponse	backup_response = null;

					// we use a fixed seed so that subsequent scrapes will randomise
						// in the same order, as required by the spec. Note that if the
						// torrent's announce sets are edited this all works fine (if we
						// cached the randomised URL set this wouldn't work)

				Random	scrape_random = new Random(scrape_random_seed);

				for (int i=0;response==null && i<sets.length;i++) {

					TOTorrentAnnounceURLSet	set = sets[i];

					URL[]	urls = set.getAnnounceURLs();

					List	rand_urls = new ArrayList();

					for (int j=0;j<urls.length;j++) {

						URL url = urls[j];

						int pos = (int)(scrape_random.nextDouble() *	(rand_urls.size()+1));

						rand_urls.add(pos,url);
					}

					for (int j=0;response==null && j<rand_urls.size();j++) {

						URL url = (URL)rand_urls.get(j);

						response = scraper.scrape(torrent, url);

						if (response!= null) {

							int status = response.getStatus();

								// Exit if online

							if (status == TRTrackerScraperResponse.ST_ONLINE) {

								if (response.isDHTBackup()) {

										// we'll use this if we don't find anything better

									backup_url		= url;
									backup_response	= response;

									response = null;

									continue;
								} else {

									active_url	= url;

									break;
								}
							}

								// Scrape 1 at a time to save on outgoing connections

							if (	status == TRTrackerScraperResponse.ST_INITIALIZING ||
									status == TRTrackerScraperResponse.ST_SCRAPING) {

								break;
							}

								// treat bad scrapes as missing so we go on to
			 					// the next tracker

							if ((!response.isValid()) || status == TRTrackerScraperResponse.ST_ERROR) {

								if (non_null_response == null) {

									non_null_response	= response;
								}

								response	= null;
							}
						}
					}
				}

				if (response == null) {

					if (backup_response != null) {

						response 	= backup_response;
						active_url	= backup_url;

					} else {

						response = non_null_response;
					}
				}
			}
		}

		return (new Object[]{ response, active_url });
	}


	public List<TRTrackerScraperResponse>
	getGoodTrackerScrapeResponses() {
		List<TRTrackerScraperResponse> 	responses	= new ArrayList<TRTrackerScraperResponse>();

		if (torrent != null) {

			TRTrackerScraper	scraper = globalManager.getTrackerScraper();


			TOTorrentAnnounceURLSet[]	sets;

			try {
				sets = torrent.getAnnounceURLGroup().getAnnounceURLSets();

			} catch (Throwable e) {

				sets = new	TOTorrentAnnounceURLSet[0];
			}

			if (sets.length == 0) {

				TRTrackerScraperResponse response = scraper.peekScrape(torrent, null);

				if (response!= null) {

					int status = response.getStatus();

					if (status == TRTrackerScraperResponse.ST_ONLINE) {

						responses.add(response);
					}
				}
			} else {

				for (int i=0; i<sets.length;i++) {

					TOTorrentAnnounceURLSet	set = sets[i];

					URL[]	urls = set.getAnnounceURLs();

					for (URL url: urls) {

						TRTrackerScraperResponse response = scraper.peekScrape(torrent, url);

						if (response!= null) {

							int status = response.getStatus();

							if (status == TRTrackerScraperResponse.ST_ONLINE) {


								responses.add(response);
							}
						}
					}
				}
			}
		}

		return (responses);
	}


	public void requestTrackerAnnounce(
		boolean	force) {
		TRTrackerAnnouncer tc = getTrackerClient();

		if (tc != null)

			tc.update(force);
	}

	public void requestTrackerScrape(
		boolean	force) {
		if (torrent != null) {

			TRTrackerScraper	scraper = globalManager.getTrackerScraper();

			scraper.scrape(torrent, force);
		}
	}

	protected void setTrackerRefreshDelayOverrides(
		int	percent) {
		TRTrackerAnnouncer tc = getTrackerClient();

		if (tc != null) {

			tc.setRefreshDelayOverrides(percent);
		}
	}

	protected boolean activateRequest(
		int		count) {
			// activation request for a queued torrent

		for (Iterator it = activation_listeners.iterator();it.hasNext();) {

			DownloadManagerActivationListener	listener = (DownloadManagerActivationListener)it.next();

			try {

				if (listener.activateRequest( count)) {

					return (true);
				}
			} catch (Throwable e) {

				Debug.printStackTrace(e);
			}
		}

		return (false);
	}

	public int getActivationCount() {
		return ( controller.getActivationCount());
	}

	public String getTorrentComment() {
		return torrentComment;
	}

	public String getTorrentCreatedBy() {
		return torrentCreatedBy;
	}

	public long getTorrentCreationDate() {
		if (torrent==null) {
			return (0);
		}

		return ( torrent.getCreationDate());
	}


	public GlobalManager
	getGlobalManager() {
		return (globalManager);
	}

	public DiskManager
	getDiskManager() {
		return ( controller.getDiskManager());
	}

	public DiskManagerFileInfoSet getDiskManagerFileInfoSet() {
		return controller.getDiskManagerFileInfoSet();
	}

	/**
	 * @deprecated use getDiskManagerFileInfoSet() instead
	 */
	public DiskManagerFileInfo[]
	 	getDiskManagerFileInfo() {
		return ( controller.getDiskManagerFileInfo());
	}

	public int getNumFileInfos() {

		return torrent == null ? 0 : torrent.getFileCount();
	}

	public PEPeerManager
	getPeerManager() {
		return ( controller.getPeerManager());
	}

	public boolean isDownloadComplete(boolean bIncludeDND) {
		if (!bIncludeDND) {
			return assumedComplete;
		}

		return controller.isDownloadComplete(bIncludeDND);
	}

	public void addListener(
		DownloadManagerListener	listener) {
		addListener(listener, true);
	}

	public void addListener(
		DownloadManagerListener		listener,
		boolean 					triggerStateChange) {
		if (listener == null) {
			Debug.out("Warning: null listener");
			return;
		}
		try {
			listenersMonitor.enter();
			listeners.addListener(listener);
			if (triggerStateChange) {
				listeners.dispatch(listener, LDT_STATECHANGED, new Object[]{ this, new Integer( getState())});
			}
				// we DON'T dispatch a downloadComplete event here as this event is used to mark the
				// transition between downloading and seeding, NOT purely to inform of seeding status
		} catch (Throwable t) {
			Debug.out("adding listener", t);
		} finally {
			listenersMonitor.exit();
		}
	}

	public void removeListener(
		DownloadManagerListener	listener) {
		try {
			listenersMonitor.enter();
			listeners.removeListener(listener);
		} finally {
			listenersMonitor.exit();
		}
	}

	/**
	 * Doesn't not inform if state didn't change from last inform call
	 */
	protected void informStateChanged() {
		
		// whenever the state changes we'll get called
		try {
			listenersMonitor.enter();
			int		newState 		= controller.getState();
			boolean new_force_start	= controller.isForceStart();
			if (newState != last_informed_state ||
					new_force_start != latest_informed_force_start) {
				last_informed_state	= newState;
				latest_informed_force_start	= new_force_start;
				if (resumeTime < 0) {
					if (newState == DownloadManager.STATE_STOPPED) {
						resumeTime = -resumeTime;
					}
				} else {
					resumeTime = 0;
				}

				listeners.dispatch(LDT_STATECHANGED, new Object[]{ this, new Integer(newState)});
			}
		} finally {
			listenersMonitor.exit();
		}
	}

	protected void informDownloadEnded() {
		try {
			listenersMonitor.enter();

			listeners.dispatch( LDT_DOWNLOADCOMPLETE, new Object[]{ this });

		} finally {

			listenersMonitor.exit();
		}
	}

	 void informPrioritiesChange(
			List	files) {
			controller.filePrioritiesChanged(files);

			try {
				listenersMonitor.enter();

				for (int i=0;i<files.size();i++)
					listeners.dispatch( LDT_FILEPRIORITYCHANGED, new Object[]{ this, (DiskManagerFileInfo)files.get(i) });

			} finally {

				listenersMonitor.exit();
			}

			requestAssumedCompleteMode();
		}


	protected void informPriorityChange(
		DiskManagerFileInfo	file) {
		informPrioritiesChange(Collections.singletonList(file));
	}

	protected void informPositionChanged(
		int new_position) {
		try {
			listenersMonitor.enter();

			int	old_position = position;

			if (new_position != old_position) {

				position = new_position;

				listeners.dispatch(
					LDT_POSITIONCHANGED,
					new Object[]{ this, new Integer(old_position ), new Integer(new_position)});

				// an active torrent changed its position, scheduling needs to be updated
				if (getState() == DownloadManager.STATE_SEEDING || getState() == DownloadManager.STATE_DOWNLOADING)
					PeerControlSchedulerFactory.updateScheduleOrdering();
			}
		} finally {

			listenersMonitor.exit();
		}
	}

	public void	addPeerListener(DownloadManagerPeerListener	listener) {
		addPeerListener(listener, true);
	}

	public void addPeerListener(
			DownloadManagerPeerListener	listener,
			boolean bDispatchForExisting) {

		try {
			peerListenersMon.enter();
			peerListeners.addListener(listener);

			if (!bDispatchForExisting) {
				return; // finally will call
			}

			for ( PEPeer peer: current_peers.keySet()) {
				peerListeners.dispatch(listener, LDT_PE_PEER_ADDED, peer);
			}

			PEPeerManager	temp = controller.getPeerManager();

			if (temp != null) {
				peerListeners.dispatch(listener, LDT_PE_PM_ADDED, temp);
			}

		} finally {

			peerListenersMon.exit();
		}
	}

	public void removePeerListener(
		DownloadManagerPeerListener	listener) {
		peerListeners.removeListener(listener);
	}

	public void addPieceListener(
		DownloadManagerPieceListener	listener) {
		addPieceListener(listener, true);
	}

	public void addPieceListener(
		DownloadManagerPieceListener	listener,
		boolean 						bDispatchForExisting) {
		try {
			pieceListenersMon.enter();

			pieceListeners.addListener(listener);

			if (!bDispatchForExisting)
				return; // finally will call

			for (int i=0;i<currentPieces.size();i++) {

				pieceListeners.dispatch( listener, LDT_PE_PIECE_ADDED, currentPieces.get(i));
			}

		} finally {

			pieceListenersMon.exit();
		}
	}

	public void removePieceListener(
		DownloadManagerPieceListener	listener) {
		pieceListeners.removeListener(listener);
	}



	public void addPeer(PEPeer peer) {
		try {
			peerListenersMon.enter();
			if (current_peers_unmatched_removal.remove(peer) != null) {
				return;
			}
			current_peers.put(peer, "");
			peerListeners.dispatch(LDT_PE_PEER_ADDED, peer);
		} finally {
			peerListenersMon.exit();
		}
	}

	public void removePeer(
		PEPeer		peer) {
		try {
			peerListenersMon.enter();
			if (current_peers.remove(peer) == null) {
				long	now = SystemTime.getMonotonousTime();
				current_peers_unmatched_removal.put(peer, now);
				if (current_peers_unmatched_removal.size() > 100) {
					Iterator<Map.Entry<PEPeer, Long>> it = current_peers_unmatched_removal.entrySet().iterator();
					while (it.hasNext()) {
						if (now - it.next().getValue() > 10*1000) {
							Debug.out("Removing expired unmatched removal record");
							it.remove();
						}
					}
				}
			}
			peerListeners.dispatch(LDT_PE_PEER_REMOVED, peer);
		} finally {
			peerListenersMon.exit();
		}
			// if we're a seed and they're a seed then no point in keeping in the announce cache
			// if it happens to be there - avoid seed-seed connections in the future
		if ((peer.isSeed() || peer.isRelativeSeed()) && isDownloadComplete( false)) {
			TRTrackerAnnouncer	announcer = trackerClient;
			if (announcer != null) {
				announcer.removeFromTrackerResponseCache( peer.getIp(), peer.getTCPListenPort());
			}
		}
	}

	public PEPeer[]
	getCurrentPeers() {
		try {
			peerListenersMon.enter();
			return ( current_peers.keySet().toArray(new PEPeer[current_peers.size()]));
		} finally {
			peerListenersMon.exit();
		}
	}

	public void addPiece(PEPiece piece) {
		try {
			pieceListenersMon.enter();
			currentPieces.add(piece);
			pieceListeners.dispatch(LDT_PE_PIECE_ADDED, piece);
		} finally {
			pieceListenersMon.exit();
		}
	}

	public void removePiece(PEPiece piece) {
		try {
			pieceListenersMon.enter();
			currentPieces.remove(piece);
			pieceListeners.dispatch(LDT_PE_PIECE_REMOVED, piece);
		} finally {
			pieceListenersMon.exit();
		}
	}

	public PEPiece[] getCurrentPieces() {
		try {
			pieceListenersMon.enter();
			return (PEPiece[])currentPieces.toArray(new PEPiece[currentPieces.size()]);
		} finally {
			pieceListenersMon.exit();
		}
	}

	protected void informWillBeStarted(PEPeerManager pm) {
		// hack I'm afraid - sometimes we want synchronous notification of a peer manager's
		// creation before it actually starts
		List l = peerListeners.getListenersCopy();
		for (int i=0;i<l.size();i++) {
			try {
				((DownloadManagerPeerListener)l.get(i)).peerManagerWillBeAdded(pm);
			} catch (Throwable e) {
				Debug.printStackTrace(e);
			}
		}
	}

		protected void informStarted(PEPeerManager pm) {
		try {
			peerListenersMon.enter();
			peerListeners.dispatch(LDT_PE_PM_ADDED, pm);
		} finally {
			peerListenersMon.exit();
		}
		TRTrackerAnnouncer tc = getTrackerClient();
		if (tc != null) {
			tc.update(true);
		}
		}

		protected void
		informStopped(
		PEPeerManager	pm,
		boolean			for_queue )	// can be null if controller was already stopped....
		{
			if (pm != null) {

				try {
					peerListenersMon.enter();

					peerListeners.dispatch(LDT_PE_PM_REMOVED, pm);

				} finally {

					peerListenersMon.exit();
				}
			}

			try {
				thisMon.enter();

				if (trackerClient != null) {

				trackerClient.addListener(stopping_tracker_client_listener);

					trackerClient.removeListener(trackerClientListener);

 				downloadManagerState.setTrackerResponseCache(	trackerClient.getTrackerResponseCache());

 				// we have serialized what we need -> can destroy retained stuff now
 				trackerClient.getLastResponse().setPeers(new TRTrackerAnnouncerResponsePeer[0]);

 				// currently only report this for complete downloads...

 				trackerClient.stop(for_queue && isDownloadComplete( false));

					trackerClient.destroy();

					trackerClient = null;
				}
		} finally {

			thisMon.exit();
		}
		}

	public DownloadManagerStats
	getStats() {
		return (stats);
	}

	public boolean isForceStart() {
		return ( controller.isForceStart());
	}

	public void setForceStart(
		boolean forceStart) {
		if (forceStart) {

			checkResuming();
		}

		controller.setForceStart(forceStart);
	}

		/**
		 * Is called when a download is finished.
		 * Activates alerts for the user.
		 *
		 * @param never_downloaded true indicates that we never actually downloaded
		 *												 anything in this session, but we determined that
		 *												 the download is complete (usually via
		 *												 startDownload())
		 *
		 * @author Rene Leonhardt
		 */

	protected void downloadEnded(
		boolean	never_downloaded) {
			if (!never_downloaded) {

				if (!COConfigurationManager.getBooleanParameter("StartStopManager_bRetainForceStartWhenComplete")) {

					if (isForceStart()) {

							setForceStart(false);
					}
				}

				setAssumedComplete(true);

				informDownloadEnded();
			}

			TRTrackerAnnouncer	tc = trackerClient;

			if (tc != null) {

				DiskManager	dm = getDiskManager();

					// only report "complete" if we really are complete, not a dnd completion event

				if (dm != null && dm.getRemaining() == 0 && !COConfigurationManager.getBooleanParameter("peercontrol.hide.piece")) {

					tc.complete(never_downloaded);
				}
			}
	}


	public void addDiskListener(
		DownloadManagerDiskListener	listener) {
		controller.addDiskListener(listener);
	}

	public void removeDiskListener(
		DownloadManagerDiskListener	listener) {
		controller.removeDiskListener(listener);
	}

	public void
		addActivationListener(
			DownloadManagerActivationListener listener) {
		activation_listeners.add(listener);
	}

		public void
		removeActivationListener(
			DownloadManagerActivationListener listener )
		{
			activation_listeners.remove(listener);
		}

	public int getHealthStatus() {
		int	state = getState();

		PEPeerManager	peerManager	 = controller.getPeerManager();

		TRTrackerAnnouncer tc = getTrackerClient();

		if (tc != null && peerManager != null && (state == STATE_DOWNLOADING || state == STATE_SEEDING)) {

			int nbSeeds = getNbSeeds();
			int nbPeers = getNbPeers();
			int nbRemotes = peerManager.getNbRemoteTCPConnections() + peerManager.getNbRemoteUTPConnections();

			TRTrackerAnnouncerResponse	announce_response = tc.getLastResponse();

			int trackerStatus = announce_response.getStatus();

			boolean isSeed = (state == STATE_SEEDING);

			if ((nbSeeds + nbPeers) == 0) {

				if (isSeed) {

					return WEALTH_NO_TRACKER;	// not connected to any peer and seeding
				}

				return WEALTH_KO;				// not connected to any peer and downloading
			}

						// read the spec for this!!!!
						// no_tracker =
						//	1) if downloading -> no tracker
						//	2) if seeding -> no connections		(dealt with above)

			if (!isSeed) {

				if (	trackerStatus == TRTrackerAnnouncerResponse.ST_OFFLINE ||
						trackerStatus == TRTrackerAnnouncerResponse.ST_REPORTED_ERROR) {

					return WEALTH_NO_TRACKER;
				}
			}

			if (nbRemotes == 0) {

				TRTrackerScraperResponse scrape_response = getTrackerScrapeResponse();

				if (scrape_response != null && scrape_response.isValid()) {

						// if we're connected to everyone then report OK as we can't get
						// any incoming connections!

					if (	nbSeeds == scrape_response.getSeeds() &&
							nbPeers == scrape_response.getPeers()) {

						return WEALTH_OK;
					}
				}

				return WEALTH_NO_REMOTE;
			}

			return WEALTH_OK;

		} else if (state == STATE_ERROR) {
			return WEALTH_ERROR;
		} else {

			return WEALTH_STOPPED;
		}
	}

	public int getNATStatus() {
		int	state = getState();

		PEPeerManager	peerManager	 = controller.getPeerManager();

		TRTrackerAnnouncer tc = getTrackerClient();

		if (tc != null && peerManager != null && (state == STATE_DOWNLOADING || state == STATE_SEEDING)) {

			if (peerManager.getNbRemoteTCPConnections() > 0 || peerManager.getNbRemoteUTPConnections() > 0) {

				return (ConnectionManager.NAT_OK);
			}

			long	last_good_time = peerManager.getLastRemoteConnectionTime();

			if (last_good_time > 0) {

					// half an hour's grace

				if (SystemTime.getCurrentTime() - last_good_time < 30*60*1000) {

					return (ConnectionManager.NAT_OK);

				} else {

					return (ConnectionManager.NAT_PROBABLY_OK);
				}
			}

			TRTrackerAnnouncerResponse	announce_response = tc.getLastResponse();

			int trackerStatus = announce_response.getStatus();

			if (	trackerStatus == TRTrackerAnnouncerResponse.ST_OFFLINE ||
					trackerStatus == TRTrackerAnnouncerResponse.ST_REPORTED_ERROR) {

				return ConnectionManager.NAT_UNKNOWN;
			}

				// tracker's ok but no remotes - give it some time

			if (SystemTime.getCurrentTime() - peerManager.getTimeStarted(false) < 3*60*1000) {

				return ConnectionManager.NAT_UNKNOWN;
			}

			TRTrackerScraperResponse scrape_response = getTrackerScrapeResponse();

			if (scrape_response != null && scrape_response.isValid()) {

					// if we're connected to everyone then report OK as we can't get
					// any incoming connections!

				if (	peerManager.getNbSeeds() == scrape_response.getSeeds() &&
						peerManager.getNbPeers() == scrape_response.getPeers()) {

					return ConnectionManager.NAT_UNKNOWN;
				}

					// can't expect incoming if we're seeding and there are no peers

				if (state == STATE_SEEDING	&& scrape_response.getPeers() == 0) {

					return ConnectionManager.NAT_UNKNOWN;
				}
			} else {

					// no scrape and we're seeding - don't use this as sign of badness as
					// we can't determine

				if (state == STATE_SEEDING) {

					return ConnectionManager.NAT_UNKNOWN;
				}
			}

			return ConnectionManager.NAT_BAD;

		} else {

			return ConnectionManager.NAT_UNKNOWN;
		}
	}

	public int getPosition() {
		return position;
	}

	public void setPosition(
		int new_position ) {
		informPositionChanged(new_position);
	}

	public void addTrackerListener(DownloadManagerTrackerListener listener) {
		trackerListeners.addListener(listener);
	}

	public void removeTrackerListener(DownloadManagerTrackerListener listener) {
			trackerListeners.removeListener(listener);
	}

	protected void deleteDataFiles() {
		DownloadManagerState state = getDownloadState();

		DiskManagerFactory.deleteDataFiles(
			torrent,
			torrent_save_location.getParent(),
			torrent_save_location.getName(),
			(	state.getFlag( DownloadManagerState.FLAG_LOW_NOISE) ||
				state.getFlag(DownloadManagerState.FLAG_FORCE_DIRECT_DELETE)));

		// Attempted fix for bug 1572356 - apparently sometimes when we perform removal of a download's data files,
		// it still somehow gets processed by the move-on-removal rules. I'm making the assumption that this method
		// is only called when a download is about to be removed.

		state.setFlag(DownloadManagerState.FLAG_DISABLE_AUTO_FILE_MOVE, true);
	}

	protected void deletePartialDataFiles() {
		DiskManagerFileInfo[] files = getDiskManagerFileInfoSet().getFiles();

		String abs_root = torrent_save_location.getAbsolutePath();

		for (DiskManagerFileInfo file: files) {

			if (!file.isSkipped()) {

				continue;
			}

				// just to be safe...

			if (file.getDownloaded() == file.getLength()) {

				continue;
			}

				// user may have switched a partially completed file to DND for some reason - be safe
				// and only delete compact files

			int	storage_type = file.getStorageType() ;

			if (storage_type == DiskManagerFileInfo.ST_COMPACT || storage_type == DiskManagerFileInfo.ST_REORDER_COMPACT) {

				File f = file.getFile(true);

				if (f.exists()) {

					if (f.delete()) {

						File parent = f.getParentFile();

						while (parent != null) {

							if (parent.isDirectory() && parent.listFiles().length == 0) {

								if (parent.getAbsolutePath().startsWith( abs_root)) {

									if (!parent.delete()) {

										Debug.outNoStack("Failed to remove empty directory: " + parent);

										break;

									} else {

										parent = parent.getParentFile();
									}

								} else {

									break;
								}
							} else {

								break;
							}
						}
					} else {

						Debug.outNoStack("Failed to remove partial: " + f);
					}
				}
			}
		}
	}

	protected void deleteTorrentFile() {
		if (torrentFileName != null) {

			TorrentUtils.delete(new File(torrentFileName),getDownloadState().getFlag( DownloadManagerState.FLAG_LOW_NOISE));
		}
	}


	public DownloadManagerState
	getDownloadState() {
		return (downloadManagerState);
	}

	public Object getData (String key) { return (getUserData( key));}


	public void setData (String key, Object value) { setUserData(key, value); }

		/** To retreive arbitrary objects against a download. */

	public Object getUserData(
		Object key ) {
		Map<Object,Object> data_ref = data;

		if (data_ref == null) {

			return null;
		}

		return ( data_ref.get(key));
	}

		/** To store arbitrary objects against a download. */

	public void setUserData(
		Object 		key,
		Object 		value) {
		try {
				// copy-on-write

			peerListenersMon.enter();

			Map<Object,Object> data_ref = data;

			if (data_ref == null && value == null) {

				return;
			}

			if (value == null) {

					// removal, data_ref not null here

				if (data_ref.containsKey(key)) {

					if (data_ref.size() == 1) {

						data_ref = null;

					} else {

						data_ref = new LightHashMap<Object,Object>(data_ref);

						data_ref.remove(key);
					}
				} else {

					return;
				}
			} else {

				if (data_ref == null) {

					data_ref = new LightHashMap<Object,Object>();

				} else {

					data_ref = new LightHashMap<Object,Object>(data_ref);
				}

				data_ref.put(key, value);
			}

			data = data_ref;

		} finally {

			peerListenersMon.exit();
		}
	}


	public boolean
	isDataAlreadyAllocated()
	{
		return data_already_allocated;
	}

	public void
	setDataAlreadyAllocated(
		boolean already_allocated )
	{
		data_already_allocated = already_allocated;
	}

	public void setSeedingRank(int rank) {
		iSeedingRank = rank;
	}

	public int getSeedingRank() {
		return iSeedingRank;
	}

	public long
	getCreationTime()
	{
		return (creation_time);
	}

	public void
	setCreationTime(
		long		t )
	{
		creation_time	= t;
	}

	public String
	isSwarmMerging()
	{
	 return (globalManager.isSwarmMerging( this));
	}

	public int
	getExtendedMessagingMode()
	{
		if (message_mode == -1) {

			byte[] hash = null;

			if (torrent != null) {

				try {
					hash = torrent.getHash();

				} catch (Throwable e) {
				}
			}

			message_mode = (Integer)client_id_manager.getProperty(hash, ClientIDGenerator.PR_MESSAGING_MODE);
		}

		return (message_mode);
	}

	public void
	setCryptoLevel(
	int		level )
	{
		crypto_level = level;
	}

	public int
	getCryptoLevel()
	{
		return (crypto_level);
	}

	public void
	moveDataFiles(
	File	new_parent_dir )

		throws DownloadManagerException
	{
		moveDataFiles(new_parent_dir, null);
	}

	public void
	moveDataFilesLive(
	File new_parent_dir)

		throws DownloadManagerException
	{
		moveDataFiles(new_parent_dir, null, true);
	}

	public void renameDownload(String new_name) throws DownloadManagerException {
			this.moveDataFiles(null, new_name);
	}

	public void
	moveDataFiles(
	final File 		destination,
	final String		new_name)

		throws DownloadManagerException
	{
		moveDataFiles(destination, new_name, false);
	}

	public void
	moveDataFiles(
	final File 		destination,
	final String		new_name,
	final boolean	live )

		throws DownloadManagerException
	{
		if (destination == null && new_name == null) {
			throw new NullPointerException("destination and new name are both null");
		}

		if (!canMoveDataFiles()) {
			throw new DownloadManagerException("canMoveDataFiles is false!");
		}


		/**
		 * Test to see if the download is to be moved somewhere where it already
		 * exists. Perhaps you might think it is slightly unnecessary to check this,
		 * but I would prefer this test left in - we want to prevent pausing
		 * unnecessarily pausing a running torrent (it fires off listeners, which
		 * might try to move the download).
		 *
		 * This helps us avoid a situation with AzCatDest...
		 */
		SaveLocationChange slc = new SaveLocationChange();
		slc.download_location = destination;
		slc.download_name = new_name;

		File current_location = getSaveLocation();
		if (slc.normaliseDownloadLocation(current_location).equals(current_location)) {
			return;
		}

		try {
			FileUtil.runAsTask(
				new AzureusCoreOperationTask() {
					public void run(
						AzureusCoreOperation operation) {
						try {
							if (live) {

								moveDataFilesSupport0(destination, new_name);

							} else {

								moveDataFilesSupport(destination, new_name);
							}
						} catch (DownloadManagerException e) {

							throw (new RuntimeException( e));
						}
					}
				});
		} catch (RuntimeException e) {

			Throwable cause = e.getCause();

			if (cause instanceof DownloadManagerException) {

				throw ((DownloadManagerException)cause);
			}

			throw (e);
		}
	}

	private void
	moveDataFilesSupport(
	File 	new_parent_dir,
	String 	new_filename)

		throws DownloadManagerException
		{
		boolean is_paused = this.pause();
		try {moveDataFilesSupport0(new_parent_dir, new_filename);}
		finally {if (is_paused) {this.resume();}}
		}

	private void
	moveDataFilesSupport0(
	File 		new_parent_dir,
	String 		new_filename )

		throws DownloadManagerException
	{
		if (!canMoveDataFiles()) {
			throw new DownloadManagerException("canMoveDataFiles is false!");
		}

		if (new_filename != null) {new_filename = FileUtil.convertOSSpecificChars(new_filename,false);}

			// old file will be a "file" for simple torrents, a dir for non-simple

		File	old_file = getSaveLocation();

		try {
			old_file = old_file.getCanonicalFile();
			if (new_parent_dir != null) {new_parent_dir = new_parent_dir.getCanonicalFile();}

		} catch (Throwable e) {
			Debug.printStackTrace(e);
			throw (new DownloadManagerException("Failed to get canonical paths", e));
		}

		final File current_save_location = old_file;
		File new_save_location = new File(
				(new_parent_dir == null) ? old_file.getParentFile() : new_parent_dir,
				(new_filename == null) ? old_file.getName() : new_filename
	 );

		if (current_save_location.equals(new_save_location)) {
				// null operation
			return;
		}

		DiskManager	dm = getDiskManager();

		if (dm == null || dm.getFiles() == null) {

			if (!old_file.exists()) {

				// files not created yet

				FileUtil.mkdirs(new_save_location.getParentFile());

				setTorrentSaveDir(new_save_location.getParent().toString(), new_save_location.getName());

				return;
			}

			try {
				new_save_location	= new_save_location.getCanonicalFile();

			} catch (Throwable e) {

				Debug.printStackTrace(e);
			}

			if (old_file.equals(new_save_location)) {

				// nothing to do

			} else if (torrent.isSimpleTorrent()) {


				if (controller.getDiskManagerFileInfo()[0].setLinkAtomic(new_save_location)) {
					setTorrentSaveDir(new_save_location.getParentFile().toString(), new_save_location.getName());
				} else {throw new DownloadManagerException("rename operation failed");}

				/*
				// Have to keep the file name in sync if we're renaming.
				//if (controller.getDiskManagerFileInfo()[0].setLinkAtomic(new_save_location)) {
				if (FileUtil.renameFile( old_file, new_save_location)) {

					setTorrentSaveDir(new_save_location.getParentFile().toString(), new_save_location.getName());

				} else {

					throw (new DownloadManagerException("rename operation failed"));
				}
				//} else {throw new DownloadManagerException("rename operation failed");}
				*/
			} else {

				if (FileUtil.isAncestorOf(old_file, new_save_location)) {

								Logger.logTextResource(new LogAlert(this, LogAlert.REPEATABLE,
							LogAlert.AT_ERROR, "DiskManager.alert.movefilefails"),
							new String[] {old_file.toString(), "Target is sub-directory of files" });

								throw (new DownloadManagerException("rename operation failed"));
				}

				// The files we move must be limited to those mentioned in the torrent.
				final HashSet files_to_move = new HashSet();

							// Required for the adding of parent directories logic.
							files_to_move.add(null);
							DiskManagerFileInfo[] info_files = controller.getDiskManagerFileInfo();
							for (int i=0; i<info_files.length; i++) {
									File f = info_files[i].getFile(true);
									try {f = f.getCanonicalFile();}
									catch (IOException ioe) {f = f.getAbsoluteFile();}
									boolean added_entry = files_to_move.add(f);

									/**
									 * Start adding all the parent directories to the
									 * files_to_move list. Doesn't matter if we include
									 * files which are outside of the file path, the
									 * renameFile call won't try to move those directories
									 * anyway.
									 */
									while (added_entry) {
											f = f.getParentFile();
											added_entry = files_to_move.add(f);
									}
							}
				FileFilter ff = new FileFilter() {
					public boolean accept(File f) {return files_to_move.contains(f);}
				};

				if (FileUtil.renameFile( old_file, new_save_location, false, ff)) {

					setTorrentSaveDir(new_save_location.getParentFile().toString(), new_save_location.getName());

				} else {

					throw (new DownloadManagerException("rename operation failed"));
				}

				if ( current_save_location.isDirectory()) {

					TorrentUtils.recursiveEmptyDirDelete(current_save_location, false);
				}
			}
		} else {
			dm.moveDataFiles(new_save_location.getParentFile(), new_save_location.getName(), null);
		}
	}

	public void
	copyDataFiles(
		File	parent_dir )

		throws DownloadManagerException
	{
		if (parent_dir.exists()) {

			if (!parent_dir.isDirectory()) {

				throw (new DownloadManagerException("'" + parent_dir + "' is not a directory"));
			}
		} else {

			if (!parent_dir.mkdirs()) {

				throw (new DownloadManagerException("failed to create '" + parent_dir + "'"));
			}
		}

		DiskManagerFileInfo[] files = controller.getDiskManagerFileInfoSet().getFiles();

		if (torrent.isSimpleTorrent()) {

			File file_from = files[0].getFile(true);

			try {
				File file_to = new File( parent_dir, file_from.getName());

				if (file_to.exists()) {

					if (file_to.length() != file_from.length()) {

						throw (new Exception("target file '" + file_to + " already exists"));
					}
				} else {

					FileUtil.copyFileWithException(file_from, file_to);
				}
			} catch (Throwable e) {

				throw (new DownloadManagerException("copy of '" + file_from + "' failed", e));
			}
		} else {

			try {
				File sl_file = getSaveLocation();

				String save_location = sl_file.getCanonicalPath();

				if (!save_location.endsWith( File.separator)) {

					save_location += File.separator;
				}

				parent_dir = new File( parent_dir, sl_file.getName());

				if (!parent_dir.isDirectory()) {

					parent_dir.mkdirs();
				}

				for (DiskManagerFileInfo file: files) {

					if (!file.isSkipped() && file.getDownloaded() == file.getLength()) {

						File file_from = file.getFile(true);

						try {
							String file_path = file_from.getCanonicalPath();

							if (file_path.startsWith( save_location)) {

								File file_to = new File( parent_dir, file_path.substring( save_location.length()));

								if (file_to.exists()) {

									if (file_to.length() != file_from.length()) {

										throw (new Exception("target file '" + file_to + " already exists"));
									}
								} else {

									File parent = file_to.getParentFile();

									if (!parent.exists()) {

										if (!parent.mkdirs()) {

											throw (new Exception("Failed to make directory '" + parent + "'"));
										}
									}

									FileUtil.copyFileWithException(file_from, file_to);
								}
							}
						} catch (Throwable e) {

							throw (new DownloadManagerException("copy of '" + file_from + "' failed", e));
						}
					}
				}
			} catch (Throwable e) {

				throw (new DownloadManagerException("copy failed", e));
			}
		}
	}

	public void moveTorrentFile(File new_parent_dir) throws DownloadManagerException {
		this.moveTorrentFile(new_parent_dir, null);
	}

	public void moveTorrentFile(File new_parent_dir, String new_name) throws DownloadManagerException {
		SaveLocationChange slc = new SaveLocationChange();
		slc.torrent_location = new_parent_dir;
		slc.torrent_name = new_name;

		File torrent_file_now = new File(getTorrentFileName());
		if (!slc.isDifferentTorrentLocation(torrent_file_now)) {return;}

		boolean is_paused = this.pause();
		try {moveTorrentFile0(new_parent_dir, new_name);}
		finally {if (is_paused) {this.resume();}}
	}


	private void moveTorrentFile0(
	File	new_parent_dir,
	String	new_name)

	throws DownloadManagerException
	{

		if (!canMoveDataFiles()) {

			throw (new DownloadManagerException("Cannot move torrent file"));
		}

		setTorrentFile(new_parent_dir, new_name);
	}

	public void setTorrentFile(File new_parent_dir, String new_name) throws DownloadManagerException {

		File	old_file = new File(getTorrentFileName());

		if (!old_file.exists()) {
			Debug.out("torrent file doesn't exist!");
			return;
		}

		if (new_parent_dir == null) {new_parent_dir = old_file.getParentFile();}
		if (new_name == null) {new_name = old_file.getName();}
		File new_file = new File(new_parent_dir, new_name);

		try {
			old_file = old_file.getCanonicalFile();
			new_file = new_file.getCanonicalFile();

		} catch (Throwable e) {

			Debug.printStackTrace(e);

			throw (new DownloadManagerException("Failed to get canonical paths", e));
		}

		// Nothing to do.
		if (new_file.equals(old_file)) {return;}

		if (TorrentUtils.move(old_file, new_file)) {
			setTorrentFileName(new_file.toString());

		} else {
			throw (new DownloadManagerException("rename operation failed"));
		}
	}

	public boolean isInDefaultSaveDir() {
		return DownloadManagerDefaultPaths.isInDefaultDownloadDir(this);
	}

	public boolean
	seedPieceRecheck()
	{
		PEPeerManager pm = controller.getPeerManager();

		if (pm != null) {

			return ( pm.seedPieceRecheck());
		}

		return (false);
	}

	public void
	addRateLimiter(
		LimitedRateGroup	group,
		boolean			upload )
	{
		controller.addRateLimiter(group, upload);
	}

	public LimitedRateGroup[]
	getRateLimiters(
	boolean	upload )
	{
		return (controller.getRateLimiters( upload));
	}

	public void
	removeRateLimiter(
		LimitedRateGroup	group,
		boolean				upload )
	{
		controller.removeRateLimiter(group, upload);
	}

	public boolean
	isTrackerError()
	{
		TRTrackerAnnouncer announcer = getTrackerClient();

		if (announcer != null) {

			TRTrackerAnnouncerResponse resp = announcer.getLastResponse();

			if (resp != null) {

				if (resp.getStatus() == TRTrackerAnnouncerResponse.ST_REPORTED_ERROR) {

					return (true);
				}
			}
		} else {

			TRTrackerScraperResponse resp = getTrackerScrapeResponse();

			if (resp != null) {

				if (resp.getStatus() == TRTrackerScraperResponse.ST_ERROR) {

					return (true);
				}
			}
		}

		return (false);
	}

	public boolean
	isUnauthorisedOnTracker()
	{
		TRTrackerAnnouncer announcer = getTrackerClient();
		String	status_str = null;
		if (announcer != null) {
			TRTrackerAnnouncerResponse resp = announcer.getLastResponse();
			if (resp != null) {
				if (resp.getStatus() == TRTrackerAnnouncerResponse.ST_REPORTED_ERROR) {
					status_str = resp.getStatusString();
				}
			}
		} else {
			TRTrackerScraperResponse resp = getTrackerScrapeResponse();
			if (resp != null) {
				if (resp.getStatus() == TRTrackerScraperResponse.ST_ERROR) {
					status_str = resp.getStatusString();
				}
			}
		}
		if (status_str != null) {
			status_str = status_str.toLowerCase();
			if (	status_str.contains("not authorised") ||
					status_str.contains("not authorized")) {
				return (true);
			}
		}
		return (false);
	}

		public List<TrackerPeerSource> getTrackerPeerSources() {
			try {
				thisMon.enter();
				Object[] tps_data = (Object[])getUserData(TPS_Key);
				List<TrackerPeerSource>	tps;
				if (tps_data == null) {
					tps = new ArrayList<TrackerPeerSource>();
					TOTorrentListener tol =
						new TOTorrentListener() {
						public void torrentChanged(
							TOTorrent	torrent,
							int 		type ) {
							if (type == TOTorrentListener.CT_ANNOUNCE_URLS) {
								List<DownloadManagerTPSListener>	toInform = null;
						 		try {
										thisMon.enter();
										torrent.removeListener(this);
										setUserData(TPS_Key, null);
										if (tps_listeners != null) {
											toInform = new ArrayList<DownloadManagerTPSListener>(tps_listeners);
										}
									} finally {
										thisMon.exit();
									}
						 		
									if (toInform != null) {
										for (DownloadManagerTPSListener l: toInform) {
											try {
												l.trackerPeerSourcesChanged();
											} catch (Throwable e) {
												Debug.out(e);
											}
										}
									}
							}
						}
					};
					setUserData(TPS_Key, new Object[]{ tps, tol });
					Download plugin_download = PluginCoreUtils.wrap(this);
					if (isDestroyed() || plugin_download == null) {
						return (tps);
					}
					
					// tracker peer sources
					final TOTorrent t = getTorrent();
					if (t != null) {
						t.addListener(tol);
						TOTorrentAnnounceURLSet[] sets = t.getAnnounceURLGroup().getAnnounceURLSets();
						if (sets.length == 0) {
							sets = new TOTorrentAnnounceURLSet[]{ t.getAnnounceURLGroup().createAnnounceURLSet(new URL[]{ torrent.getAnnounceURL()})};
						}
						
					// source per set
					for (final TOTorrentAnnounceURLSet set: sets) {
						final URL[] urls = set.getAnnounceURLs();
						if (urls.length == 0 || TorrentUtils.isDecentralised( urls[0])) {
							continue;
						}
						tps.add(
							new TrackerPeerSource() {
								private TrackerPeerSource _delegate;
			 					private TRTrackerAnnouncer		ta;
									private long					ta_fixup;
									private long					last_scrape_fixup_time;
									private Object[]				last_scrape;
								private TrackerPeerSource
								fixup() {
									long	now = SystemTime.getMonotonousTime();
									if (now - ta_fixup > 1000) {
										TRTrackerAnnouncer current_ta = getTrackerClient();
										if (current_ta == ta) {
											if (current_ta != null && _delegate == null) {
												_delegate = current_ta.getTrackerPeerSource(set);
											}
										} else {
											if (current_ta == null) {
												_delegate = null;
											} else {
												_delegate = current_ta.getTrackerPeerSource(set);
											}
											ta = current_ta;
										}
										ta_fixup	= now;
									}
									return (_delegate);
								}
								protected Object[]
								getScrape() {
									long now = SystemTime.getMonotonousTime();
									if (now - last_scrape_fixup_time > 30*1000 || last_scrape == null) {
										TRTrackerScraper	scraper = globalManager.getTrackerScraper();
										int	max_peers 	= -1;
										int max_seeds 	= -1;
										int max_comp	= -1;
										int max_time 	= 0;
										int	min_scrape	= Integer.MAX_VALUE;
										String status_str = null;
										boolean	found_usable = false;
										for (URL u: urls) {
											TRTrackerScraperResponse resp = scraper.peekScrape(torrent, u);
											if (resp != null) {
												if (!resp.isDHTBackup()) {
													found_usable = true;
													int peers 	= resp.getPeers();
													int seeds 	= resp.getSeeds();
													int comp	= resp.getCompleted();
													if (peers > max_peers) {
														max_peers = peers;
													}
													if (seeds > max_seeds) {
														max_seeds = seeds;
													}
													if (comp > max_comp) {
														max_comp = comp;
													}
													if (resp.getStatus() != TRTrackerScraperResponse.ST_INITIALIZING) {
														status_str = resp.getStatusString();
														int	time	= resp.getScrapeTime();
														if (time > max_time) {
															max_time = time;
														}
														long next_scrape = resp.getNextScrapeStartTime();
														if (next_scrape > 0) {
															int	 ns = (int)(next_scrape/1000);
															if (ns < min_scrape) {
																min_scrape = ns;
															}
														}
													}
												}
											}
										}
											// don't overwrite an old status if this time around we haven't found anything usable
										if (found_usable || last_scrape == null) {
											last_scrape = new Object[]{ max_seeds, max_peers, max_time, min_scrape, max_comp, status_str };
										}
										last_scrape_fixup_time = now;
									}
									return (last_scrape);
								}
								public int getType() {
									return (TrackerPeerSource.TP_TRACKER);
								}
								public String getName() {
									TrackerPeerSource delegate = fixup();
									if (delegate == null) {
										return ( urls[0].toExternalForm());
									}
									return ( delegate.getName());
								}
								public int getStatus() {
									TrackerPeerSource delegate = fixup();
									if (delegate == null) {
										return (ST_STOPPED);
									}
									return ( delegate.getStatus());
								}
								public String getStatusString() {
									TrackerPeerSource delegate = fixup();
									if (delegate == null) {
										return ((String)getScrape()[5]);
									}
									return ( delegate.getStatusString());
								}
								public int getSeedCount() {
									TrackerPeerSource delegate = fixup();
									if (delegate == null) {
										return ((Integer)getScrape()[0]);
									}
									int seeds = delegate.getSeedCount();
									if (seeds < 0) {
										seeds = (Integer)getScrape()[0];
									}
									return (seeds);
								}
								public int getLeecherCount() {
									TrackerPeerSource delegate = fixup();
									if (delegate == null) {
										return ((Integer)getScrape()[1]);
									}
									int leechers = delegate.getLeecherCount();
									if (leechers < 0) {
										leechers = (Integer)getScrape()[1];
									}
									return (leechers);
								}
								public int getCompletedCount() {
									TrackerPeerSource delegate = fixup();
									if (delegate == null) {
										return ((Integer)getScrape()[4]);
									}
									int comp = delegate.getCompletedCount();
									if (comp < 0) {
										comp = (Integer)getScrape()[4];
									}
									return (comp);
								}
								public int getPeers() {
									TrackerPeerSource delegate = fixup();
									if (delegate == null) {
										return (-1);
									}
									return ( delegate.getPeers());
								}
								public int getInterval() {
									TrackerPeerSource delegate = fixup();
									if (delegate == null) {
										Object[] si = getScrape();
										int	last 	= (Integer)si[2];
										int next	= (Integer)si[3];
										if (last > 0 && next < Integer.MAX_VALUE && last < next) {
											return (next - last);
										}
										return (-1);
									}
									return ( delegate.getInterval());
								}
								public int getMinInterval() {
									TrackerPeerSource delegate = fixup();
									if (delegate == null) {
										return (-1);
									}
									return ( delegate.getMinInterval());
								}
								public boolean isUpdating() {
									TrackerPeerSource delegate = fixup();
									if (delegate == null) {
										return (false);
									}
									return ( delegate.isUpdating());
								}
								public int getLastUpdate() {
									TrackerPeerSource delegate = fixup();
									if (delegate == null) {
										return ((Integer)getScrape()[2]);
									}
									return ( delegate.getLastUpdate());
								}
								public int getSecondsToUpdate() {
									TrackerPeerSource delegate = fixup();
									if (delegate == null) {
										return (-1);
									}
									return ( delegate.getSecondsToUpdate());
								}
								public boolean canManuallyUpdate() {
									TrackerPeerSource delegate = fixup();
									if (delegate == null) {
										return (false);
									}
									return ( delegate.canManuallyUpdate());
								}
								public void manualUpdate() {
									TrackerPeerSource delegate = fixup();
									if (delegate != null) {
										delegate.manualUpdate();
									}
								}
								public boolean canDelete() {
									return (true);
								}
								public void delete() {
									List<List<String>> lists = TorrentUtils.announceGroupsToList(t);
									List<String>	rem = new ArrayList<String>();
									for (URL u: urls) {
										rem.add( u.toExternalForm());
									}
									lists = TorrentUtils.removeAnnounceURLs2(lists, rem, false);
									TorrentUtils.listToAnnounceGroups(lists, t);
								}
							});
					}
					
					// cache peer source
					tps.add(
							new TrackerPeerSourceAdapter() {
								private TrackerPeerSource _delegate;
			 					private TRTrackerAnnouncer		ta;
			 					private boolean					enabled;
									private long					ta_fixup;
								private TrackerPeerSource
								fixup() {
									long	now = SystemTime.getMonotonousTime();
									if (now - ta_fixup > 1000) {
										TRTrackerAnnouncer current_ta = getTrackerClient();
										if (current_ta == ta) {
											if (current_ta != null && _delegate == null) {
												_delegate = current_ta.getCacheTrackerPeerSource();
											}
										} else {
											if (current_ta == null) {
												_delegate = null;
											} else {
												_delegate = current_ta.getCacheTrackerPeerSource();
											}
											ta = current_ta;
										}
										enabled = controller.isPeerSourceEnabled(PEPeerSource.PS_BT_TRACKER);
										ta_fixup	= now;
									}
									return (_delegate);
								}
								public int getType() {
									return (TrackerPeerSource.TP_TRACKER);
								}
								public String getName() {
									TrackerPeerSource delegate = fixup();
									if (delegate == null) {
										return (MessageText.getString("tps.tracker.cache"));
									}
									return ( delegate.getName());
								}
								public int getStatus() {
									TrackerPeerSource delegate = fixup();
									if (!enabled) {
										return (ST_DISABLED);
									}
									if (delegate == null) {
										return (ST_STOPPED);
									}
									return (ST_ONLINE);
								}
								public int getPeers() {
									TrackerPeerSource delegate = fixup();
									if (delegate == null || !enabled) {
										return (-1);
									}
									return ( delegate.getPeers());
								}
							});
					}
						// http seeds
					try {
						ExternalSeedPlugin esp = DownloadManagerController.getExternalSeedPlugin();
						if (esp != null) {
						tps.add(esp.getTrackerPeerSource( plugin_download));
	 				}
					} catch (Throwable e) {
					}
					
					// dht
					try {
					PluginInterface dht_pi = AzureusCoreFactory.getSingleton().getPluginManager().getPluginInterfaceByClass(DHTTrackerPlugin.class);
							if (dht_pi != null) {
								tps.add(((DHTTrackerPlugin)dht_pi.getPlugin()).getTrackerPeerSource(plugin_download));
							}
				} catch (Throwable e) {
					}
					
				// LAN
				try {
					PluginInterface lt_pi = AzureusCoreFactory.getSingleton().getPluginManager().getPluginInterfaceByClass(LocalTrackerPlugin.class);
					if (lt_pi != null) {
								tps.add(((LocalTrackerPlugin)lt_pi.getPlugin()).getTrackerPeerSource(plugin_download));
					}
				} catch (Throwable e) {
				}
				
				// Plugin
				try {
					tps.add(((DownloadImpl)plugin_download).getTrackerPeerSource());
				} catch (Throwable e) {
				}
				
				// PEX...
				tps.add(
					new TrackerPeerSourceAdapter() {
						private PEPeerManager		_pm;
						private TrackerPeerSource	_delegate;
						private TrackerPeerSource
						fixup() {
							PEPeerManager pm = getPeerManager();
							if (pm == null) {
								_delegate 	= null;
								_pm			= null;
							} else if (pm != _pm) {
								_pm	= pm;
								_delegate = pm.getTrackerPeerSource();
							}
							return (_delegate);
						}
						public int getType() {
							return (TP_PEX);
						}
						public int getStatus() {
							TrackerPeerSource delegate = fixup();
							if (delegate == null) {
								return (ST_STOPPED);
							} else {
								return ( delegate.getStatus());
							}
						}
						public String getName() {
							TrackerPeerSource delegate = fixup();
							if (delegate == null) {
								return ("");
							} else {
								return ( delegate.getName());
							}
						}
						public int getPeers() {
							TrackerPeerSource delegate = fixup();
							if (delegate == null) {
								return (-1);
							} else {
								return ( delegate.getPeers());
							}
						}
					});
					// incoming
				tps.add(
						new TrackerPeerSourceAdapter() {
							private long				fixup_time;
							private PEPeerManager		_pm;
							private int					tcp;
							private int					udp;
							private int					utp;
							private int					total;
							private boolean				enabled;
							private PEPeerManager
							fixup() {
								long	now = SystemTime.getMonotonousTime();
								if (now - fixup_time > 1000) {
									PEPeerManager pm = _pm = getPeerManager();
									if (pm != null) {
										tcp 	= pm.getNbRemoteTCPConnections();
										udp		= pm.getNbRemoteUDPConnections();
										utp		= pm.getNbRemoteUTPConnections();
										total	= pm.getStats().getTotalIncomingConnections();
									}
									enabled = controller.isPeerSourceEnabled(PEPeerSource.PS_INCOMING);
									fixup_time = now;
								}
								return (_pm);
							}
							public int getType() {
								return (TP_INCOMING);
							}
							public int getStatus() {
								PEPeerManager delegate = fixup();
								if (delegate == null) {
									return (ST_STOPPED);
								} else if (!enabled) {
									return (ST_DISABLED);
								} else {
									return (ST_ONLINE);
								}
							}
							public String getName() {
								PEPeerManager delegate = fixup();
								if (delegate == null || !enabled) {
									return ("");
								} else {
									return (
										MessageText.getString(
											"tps.incoming.details",
											new String[]{ String.valueOf(tcp ), String.valueOf( udp + utp ), String.valueOf( total )}));
								}
							}
							public int getPeers() {
								PEPeerManager delegate = fixup();
								if (delegate == null || !enabled) {
									return (-1);
								} else {
									return (tcp + udp);
								}
							}
						});
 			} else {
					tps = (List<TrackerPeerSource>)tps_data[0];
				}
				return (tps);
			} finally {
				thisMon.exit();
			}
		}

		public void
		addTPSListener(
			DownloadManagerTPSListener		listener )
		{
			try {
				thisMon.enter();
				if (tps_listeners == null) {
					tps_listeners = new ArrayList<DownloadManagerTPSListener>(1);
				}
				tps_listeners.add(listener);
			} finally {
				thisMon.exit();
			}
		}

		public void
		removeTPSListener(
			DownloadManagerTPSListener		listener )
		{
			 	try {
				thisMon.enter();
				if (tps_listeners != null) {
					tps_listeners.remove(listener);
					if (tps_listeners.size() == 0) {
						tps_listeners = null;
							Object[] tps_data = (Object[])getUserData(TPS_Key);
							if (tps_data != null) {
			 				TOTorrent t = getTorrent();
			 				if (t != null) {
			 					t.removeListener((TOTorrentListener)tps_data[1]);
			 				}
								setUserData(TPS_Key, null);
							}
					}
				}
			} finally {
				thisMon.exit();
			}
		}

	private byte[]
	getIdentity()
	{
 		return (dl_identity);
	}

	 /** @retun true, if the other DownloadManager has the same hash
		* @see java.lang.Object#equals(java.lang.Object)
		*/
	 public boolean equals(Object obj)
	 {
 		// check for object equivalence first!
 	if (this == obj) {
 		return (true);
 	}
 	if (obj instanceof DownloadManagerImpl) {
 		DownloadManagerImpl other = (DownloadManagerImpl) obj;
 		byte[] id1 = getIdentity();
 		byte[] id2 = other.getIdentity();
 		if (id1 == null || id2 == null) {
 		return (false);	// broken torrents - treat as different so shown
 							// as broken
 		}
 		return (Arrays.equals( id1, id2));
 	}
 	return false;
	 }


	 public int hashCode() {
		 return dl_identity_hashcode;
	 }


	/* (non-Javadoc)
	 * @see org.gudy.azureus2.core3.logging.LogRelation#getLogRelationText()
	 */
	public String getRelationText() {
		return "TorrentDLM: '" + getDisplayName() + "'";
	}


	/* (non-Javadoc)
	 * @see org.gudy.azureus2.core3.logging.LogRelation#queryForClass(java.lang.Class)
	 */
	public Object[] getQueryableInterfaces() {
		return new Object[] { trackerClient };
	}

	public String toString() {
		String hash = "<unknown>";

		if (torrent != null) {
			try {
				hash = ByteFormatter.encodeString(torrent.getHash());

			} catch (Throwable e) {
			}
		}

		String status = DisplayFormatters.formatDownloadStatus(this);
		if (status.length() > 10) {
			status = status.substring(0, 10);
		}
		return "DownloadManagerImpl#" + getPosition()
				+ (getAssumedComplete() ? "s" : "d") + "@"
				+ Integer.toHexString(hashCode()) + "/"
				+ status + "/"
				+ hash;
	}

	protected static class
	NoStackException
		extends Exception
	{
		protected NoStackException(
			String	str) {
			super(str);
		}
	}

	public void generateEvidence(IndentWriter		writer) {
		writer.println(toString());
		PEPeerManager pm = getPeerManager();
		try {
			writer.indent();
			writer.println("Save Dir: "
					+ Debug.secretFileName(getSaveLocation().toString()));
			if (current_peers.size() > 0) {
				writer.println("# Peers: " + current_peers.size());
			}
			if (currentPieces.size() > 0) {
				writer.println("# Pieces: " + currentPieces.size());
			}
			writer.println("Listeners: DownloadManager=" + listeners.size() + "; Disk="
				+ controller.getDiskListenerCount() + "; Peer=" + peerListeners.size()
				+ "; Tracker=" + trackerListeners.size());
			writer.println("SR: " + iSeedingRank);

			String sFlags = "";
			if (open_for_seeding) {
				sFlags += "Opened for Seeding; ";
			}
			if (data_already_allocated) {
				sFlags += "Data Already Allocated; ";
			}
			if (assumedComplete) {
				sFlags += "onlySeeding; ";
			}
			if (persistent) {
				sFlags += "persistent; ";
			}
			if (sFlags.length() > 0) {
				writer.println("Flags: " + sFlags);
			}
			stats.generateEvidence(writer);
			downloadManagerState.generateEvidence(writer);
			if (pm != null) {
				pm.generateEvidence(writer);
			}
				// note, PeerManager generates DiskManager evidence
			controller.generateEvidence(writer);
			TRTrackerAnnouncer announcer = trackerClient;
			if (announcer != null) {
				announcer.generateEvidence(writer);
			}
			TRTrackerScraperResponse scrape = getTrackerScrapeResponse();
			if (scrape == null) {
				writer.println("Scrape: null");
			} else {
				writer.println("Scrape: " + scrape.getString());
			}
		} finally {
			writer.exdent();
		}
	}

	public void destroy(boolean	is_duplicate) {
		
		destroyed	= true;
		if (is_duplicate) {
				// minimal tear-down
			controller.destroy();
		} else {
			try {
			 	// Data files don't exist, so we just don't do anything.
					if (!getSaveLocation().exists()) {return;}
					DiskManager dm = this.getDiskManager();
					if (dm != null) {
						dm.downloadRemoved();
						return;
					}
					SaveLocationChange move_details;
					move_details = DownloadManagerMoveHandler.onRemoval(this);
					if (move_details == null) {
						return;
					}
					boolean can_move_torrent = move_details.hasTorrentChange();
					try {
						if (move_details.hasDownloadChange()) {
							this.moveDataFiles(move_details.download_location, move_details.download_name);
						}
					}
					catch (Exception e) {
						can_move_torrent = false;
						Logger.log(new LogAlert(this, true,
							"Problem moving files to removed download directory", e));
					}
					// This code will silently fail if the torrent file doesn't exist.
					if (can_move_torrent) {
							try {
							this.moveTorrentFile(move_details.torrent_location, move_details.torrent_name);
						}
						catch (Exception e) {
							Logger.log(new LogAlert(this, true,
									"Problem moving torrent to removed download directory", e));
						}
					}
			} finally {
				clearFileLinks();
				controller.destroy();
			}
		}
	}

	public boolean isDestroyed() {
		return (destroyed || removing);
	}

	public int[] getStorageType(DiskManagerFileInfo[] info) {
		String[] types = DiskManagerImpl.getStorageTypes(this);
		int[] result = new int[info.length];
		for (int i=0; i<info.length; i++) {
			result[i] = DiskManagerUtil.convertDMStorageTypeFromString( types[info[i].getIndex()]);
		}
		return result;
	}

	public boolean canMoveDataFiles() {
		if (!isPersistent()) {return false;}
		return true;
	}

	public void rename(String name) throws DownloadManagerException {
		boolean paused = this.pause();
		try {
			this.renameDownload(name);
			this.getDownloadState().setAttribute(DownloadManagerState.AT_DISPLAY_NAME, name);
			this.renameTorrentSafe(name);
		}
		finally {
			if (paused) {this.resume();}
		}
	}

	public void renameTorrent(String name) throws DownloadManagerException {
		this.moveTorrentFile(null, name);
	}

	public void renameTorrentSafe(String name) throws DownloadManagerException {
		String torrent_parent = new File(this.getTorrentFileName()).getParent();
		String torrent_name = name;

		File new_path = new File(torrent_parent, torrent_name + ".torrent");
		if (new_path.exists()) {new_path = null;}

		for (int i=1; i<10; i++) {
			if (new_path != null) {break;}
			new_path = new File(torrent_parent, torrent_name + "(" + i + ").torrent");
			if (new_path.exists()) {new_path = null;}
		}

		if (new_path == null) {
			throw new DownloadManagerException("cannot rename torrent file - file already exists");
		}

		this.renameTorrent(new_path.getName());
	}

	@Override
	public void requestAttention() {
		fireGlobalManagerEvent(GlobalManagerEvent.ET_REQUEST_ATTENTION);
	}

	public void fireGlobalManagerEvent(int eventType) {
		globalManager.fireGlobalManagerEvent(eventType, this);
	}

	/* (non-Javadoc)
	 * @see org.gudy.azureus2.core3.download.DownloadManager#setFilePriorities(org.gudy.azureus2.core3.disk.DiskManagerFileInfo[], int)
	 */
	public void setFilePriorities(DiskManagerFileInfo[] fileInfos, int priority) {
		// TODO: Insted of looping, which fires off a lot of events,
		//			 do it more directly, and fire needed events once, at end
		for (DiskManagerFileInfo fileInfo : fileInfos) {
			fileInfo.setPriority(priority);
		}
	}
}
