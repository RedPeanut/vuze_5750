/*
 * Created by Olivier Chalouhi
 * Modified Apr 13, 2004 by Alon Rohter
 * Heavily modified Sep 2005 by Joseph Bridgewater
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
 */

package org.gudy.azureus2.core3.peer.impl.control;


import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.*;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.config.ParameterListener;
import org.gudy.azureus2.core3.disk.*;
import org.gudy.azureus2.core3.disk.DiskManager.GettingThere;
import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.ipfilter.*;
import org.gudy.azureus2.core3.logging.*;
import org.gudy.azureus2.core3.peer.*;
import org.gudy.azureus2.core3.peer.impl.*;
import org.gudy.azureus2.core3.peer.util.PeerIdentityDataID;
import org.gudy.azureus2.core3.peer.util.PeerIdentityManager;
import org.gudy.azureus2.core3.peer.util.PeerUtils;
import org.gudy.azureus2.core3.torrent.TOTorrentException;
import org.gudy.azureus2.core3.tracker.client.TRTrackerAnnouncer;
import org.gudy.azureus2.core3.tracker.client.TRTrackerAnnouncerResponse;
import org.gudy.azureus2.core3.tracker.client.TRTrackerAnnouncerResponsePeer;
import org.gudy.azureus2.core3.tracker.client.TRTrackerScraperResponse;
import org.gudy.azureus2.core3.util.*;
import org.gudy.azureus2.plugins.download.DownloadAnnounceResultPeer;
import org.gudy.azureus2.plugins.network.Connection;
import org.gudy.azureus2.plugins.network.OutgoingMessageQueue;
import org.gudy.azureus2.plugins.peers.Peer;
import org.gudy.azureus2.plugins.peers.PeerDescriptor;

import com.aelitis.azureus.core.networkmanager.LimitedRateGroup;
import com.aelitis.azureus.core.networkmanager.admin.NetworkAdmin;
import com.aelitis.azureus.core.networkmanager.impl.tcp.TCPConnectionManager;
import com.aelitis.azureus.core.networkmanager.impl.tcp.TCPNetworkManager;
import com.aelitis.azureus.core.networkmanager.impl.udp.UDPNetworkManager;
import com.aelitis.azureus.core.peermanager.PeerManagerRegistration;
import com.aelitis.azureus.core.peermanager.control.PeerControlInstance;
import com.aelitis.azureus.core.peermanager.control.PeerControlScheduler;
import com.aelitis.azureus.core.peermanager.control.PeerControlSchedulerFactory;
import com.aelitis.azureus.core.peermanager.nat.PeerNATInitiator;
import com.aelitis.azureus.core.peermanager.nat.PeerNATTraversalAdapter;
import com.aelitis.azureus.core.peermanager.nat.PeerNATTraverser;
import com.aelitis.azureus.core.peermanager.peerdb.*;
import com.aelitis.azureus.core.peermanager.piecepicker.PiecePicker;
import com.aelitis.azureus.core.peermanager.piecepicker.PiecePickerFactory;
import com.aelitis.azureus.core.peermanager.unchoker.Unchoker;
import com.aelitis.azureus.core.peermanager.unchoker.UnchokerFactory;
import com.aelitis.azureus.core.peermanager.unchoker.UnchokerUtil;
import com.aelitis.azureus.core.peermanager.uploadslots.UploadHelper;
import com.aelitis.azureus.core.peermanager.uploadslots.UploadSlotManager;
import com.aelitis.azureus.core.tracker.TrackerPeerSource;
import com.aelitis.azureus.core.tracker.TrackerPeerSourceAdapter;
import com.aelitis.azureus.core.util.FeatureAvailability;
import com.aelitis.azureus.core.util.bloom.BloomFilter;
import com.aelitis.azureus.core.util.bloom.BloomFilterFactory;

import hello.util.Log;
import hello.util.SingleCounter0;

/**
 * manages all peer transports for a torrent
 *
 * @author MjrTom
 *			2005/Oct/08: Numerous changes for new piece-picking. Also
 *						a few optimizations and multi-thread cleanups
 *			2006/Jan/02: refactoring piece picking related code
 */

@SuppressWarnings("serial")
public class PEPeerControlImpl 
	extends LogRelation
	implements PEPeerControl, 
		DiskManagerWriteRequestListener, 
		PeerControlInstance, 
		PeerNATInitiator,
		DiskManagerCheckRequestListener, 
		IPFilterListener {
	
	private static String TAG = PEPeerControlImpl.class.getSimpleName();
	
	private static final LogIDs LOGID = LogIDs.PEER;

	private static final boolean TEST_PERIODIC_SEEDING_SCAN_FAIL_HANDLING	= false;

	static{
		if (TEST_PERIODIC_SEEDING_SCAN_FAIL_HANDLING) {
			Debug.out("**** test periodic scan failure enabled ****");
		}
	}

	private static final int WARNINGS_LIMIT = 2;

	private static final int	CHECK_REASON_DOWNLOADED		= 1;
	private static final int	CHECK_REASON_COMPLETE		= 2;
	private static final int	CHECK_REASON_SCAN			= 3;
	private static final int	CHECK_REASON_SEEDING_CHECK		= 4;
	private static final int	CHECK_REASON_BAD_PIECE_CHECK	= 5;

	private static final int	SEED_CHECK_WAIT_MARKER	= 65526;

	// config
	private static boolean 	disconnectSeedsWhenSeeding;
	private static boolean 	enableSeedingPieceRechecks;
	private static int		stalledPieceTimeout;
	private static boolean 	fastUnchokeNewPeers;
    private static float	banPeerDiscardRatio;
    private static int		banPeerDiscardMinKb;
    private static boolean	udpFallbackForFailedConnection;
    private static boolean	udpFallbackForDroppedConnection;
    private static boolean	udpProbeEnabled;
    private static boolean	hideAPiece;
    private static boolean	preferUdpDefault;

	static {
		COConfigurationManager.addAndFireParameterListeners(
			new String[]{
				"Disconnect Seed",
				"Seeding Piece Check Recheck Enable",
				"peercontrol.stalled.piece.write.timeout",
				"Peer.Fast.Initial.Unchoke.Enabled",
   				"Ip Filter Ban Discard Ratio",
   				"Ip Filter Ban Discard Min KB",
   				"peercontrol.udp.fallback.connect.fail",
   				"peercontrol.udp.fallback.connect.drop",
   				"peercontrol.udp.probe.enable",
  				"peercontrol.hide.piece",
  				"peercontrol.hide.piece.ds",
   				"peercontrol.prefer.udp",
			},
			new ParameterListener() {
				public void parameterChanged(String name) {
					disconnectSeedsWhenSeeding 		= COConfigurationManager.getBooleanParameter("Disconnect Seed");
					enableSeedingPieceRechecks 		= COConfigurationManager.getBooleanParameter("Seeding Piece Check Recheck Enable");
					stalledPieceTimeout				= COConfigurationManager.getIntParameter("peercontrol.stalled.piece.write.timeout", 60*1000);
					fastUnchokeNewPeers 			= COConfigurationManager.getBooleanParameter("Peer.Fast.Initial.Unchoke.Enabled");
					banPeerDiscardRatio				= COConfigurationManager.getFloatParameter("Ip Filter Ban Discard Ratio");
					banPeerDiscardMinKb				= COConfigurationManager.getIntParameter("Ip Filter Ban Discard Min KB");
					udpFallbackForFailedConnection	= COConfigurationManager.getBooleanParameter("peercontrol.udp.fallback.connect.fail");
					udpFallbackForDroppedConnection	= COConfigurationManager.getBooleanParameter("peercontrol.udp.fallback.connect.drop");
					udpProbeEnabled					= COConfigurationManager.getBooleanParameter("peercontrol.udp.probe.enable");
					hideAPiece						= COConfigurationManager.getBooleanParameter("peercontrol.hide.piece");
					boolean hide_a_piece_ds			= COConfigurationManager.getBooleanParameter("peercontrol.hide.piece.ds");
					if (hideAPiece && !hide_a_piece_ds) {
						disconnectSeedsWhenSeeding = false;
					}
					preferUdpDefault						= COConfigurationManager.getBooleanParameter("peercontrol.prefer.udp");
				}
			});
	}

	private static final IpFilter ipFilter = IpFilterManagerFactory.getSingleton().getIPFilter();

	private volatile boolean	isRunning 		= false;
	private volatile boolean	isDestroyed 	= false;

	private volatile ArrayList<PEPeerTransport>	peerTransportsCow = new ArrayList<PEPeerTransport>();	// Copy on write!
	private final AEMonitor     peerTransportsMon	= new AEMonitor("PEPeerControl:PT");

	protected final PEPeerManagerAdapter	adapter;
	private final DiskManager           diskMgr;
	private final DiskManagerPiece[]    dmPieces;

	private final boolean	isPrivateTorrent;

	private PEPeerManager.StatsReceiver	statsReceiver;

	private final PiecePicker	piecePicker;
	private long				lastNeededUndonePieceChange;

	/** literally seeding as in 100% torrent complete */
	private boolean 			seedingMode;
	private boolean				restartInitiated;

	private final int			_nbPieces;		// how many pieces in the torrent
	private final PEPieceImpl[]	pePieces;		// pieces that are currently in progress
	private int					nbPiecesActive;	// how many pieces are currently in progress

	private int				nbPeersSnubbed;

	private PeerIdentityDataID			_hash;
	private final byte[]        		_myPeerId;
	private PEPeerManagerStatsImpl      _stats;

	//private final TRTrackerAnnouncer _tracker;
	//private int _maxUploads;
	private int		statsTickCount;
	private int		_seeds, _peers, _remotesTCPNoLan, _remotesUDPNoLan, _remotesUTPNoLan;
	private int 	_tcpPendingConnections, _tcpConnectingConnections;
	private long	lastRemoteTime;
	private long	_timeStarted;
	private long	_timeStarted_mono;
	private long	_timeStartedSeeding 		= -1;
	private long	_timeStartedSeeding_mono 	= -1;
	private long	_timeFinished;
	private Average	_averageReceptionSpeed;

	private long mainloopLoopCount;

	private static final int MAINLOOP_ONE_SECOND_INTERVAL = 1000 / PeerControlScheduler.SCHEDULE_PERIOD_MILLIS;
	private static final int MAINLOOP_FIVE_SECOND_INTERVAL = MAINLOOP_ONE_SECOND_INTERVAL * 5;
	private static final int MAINLOOP_TEN_SECOND_INTERVAL = MAINLOOP_ONE_SECOND_INTERVAL * 10;
	private static final int MAINLOOP_TWENTY_SECOND_INTERVAL = MAINLOOP_ONE_SECOND_INTERVAL * 20;
	private static final int MAINLOOP_THIRTY_SECOND_INTERVAL = MAINLOOP_ONE_SECOND_INTERVAL * 30;
	private static final int MAINLOOP_SIXTY_SECOND_INTERVAL = MAINLOOP_ONE_SECOND_INTERVAL * 60;
	private static final int MAINLOOP_TEN_MINUTE_INTERVAL = MAINLOOP_SIXTY_SECOND_INTERVAL * 10;

	private volatile ArrayList<PEPeerManagerListener> peerManagerListenersCow = new ArrayList<PEPeerManagerListener>();  //copy on write

	private final List<Object[]>	pieceCheckResultList	= new ArrayList<Object[]>();
	private final AEMonitor			pieceCheckResultListMon	= new AEMonitor("PEPeerControl:PCRL");

	private boolean 			superSeedMode;
	private int 				superSeedModeCurrentPiece;
	private int 				superSeedModeNumberOfAnnounces;
	private SuperSeedPiece[]	superSeedPieces;

	private int					hiddenPiece;
	private final AEMonitor     thisMon = new AEMonitor("PEPeerControl");
	private long				ipFilterLastUpdateTime;
	private Map<Object,Object>	userData;
	private Unchoker			unchoker;
	private List<Object[]>		externalRateLimitersCow;

	private int	bytesQueuedForUpload;
	private int	connectionsWithQueuedData;
	private int	connectionsWithQueuedDataBlocked;
	private int	connectionsUnchoked;

	private List<PEPeerTransport> sweepList = Collections.emptyList();
	private int nextPEXSweepIndex = 0;

	private final UploadHelper uploadHelper = new UploadHelper() {
		public int getPriority() {
			return UploadHelper.PRIORITY_NORMAL;  //TODO also must call UploadSlotManager.getSingleton().updateHelper(upload_helper); on priority change
		}

		public ArrayList<PEPeer> getAllPeers() {
			return ((ArrayList)peerTransportsCow);
		}

		public boolean isSeeding() {
			return seedingMode;
		}
	};



	private final PeerDatabase	peerDatabase = PeerDatabaseFactory.createPeerDatabase();

	private int				badPieceReported	= -1;

	private int				nextRescanPiece		= -1;
	private long			rescanPieceTime		= -1;

	private long			lastEta;
	private long			lastEtaSmoothed;
	private long			lastEtaCalculation;

	private static final int MAX_UDP_CONNECTIONS		= 16;

	private static final int PENDING_NAT_TRAVERSAL_MAX	= 32;
	private static final int MAX_UDP_TRAVERSAL_COUNT	= 3;

	private static final String	PEER_NAT_TRAVERSE_DONE_KEY	= PEPeerControlImpl.class.getName() + "::nat_trav_done";

	private final Map<String,PEPeerTransport>	pendingNatTraversals =
		new LinkedHashMap<String,PEPeerTransport>(PENDING_NAT_TRAVERSAL_MAX,0.75f,true) {
		protected boolean removeEldestEntry(
			Map.Entry<String,PEPeerTransport> eldest) {
			return size() > PENDING_NAT_TRAVERSAL_MAX;
		}
	};

	private int udpTraversalCount;

	private static final int UDP_RECONNECT_MAX			= 16;

	private final Map<String,PEPeerTransport>	udpReconnects =
		new LinkedHashMap<String,PEPeerTransport>(UDP_RECONNECT_MAX,0.75f,true) {
		protected boolean removeEldestEntry(
			Map.Entry<String,PEPeerTransport> eldest) {
			return size() > UDP_RECONNECT_MAX;
		}
	};

	private static final int UDP_RECONNECT_MIN_MILLIS	= 10*1000;
	private long	lastUdpReconnect;

	private boolean	preferUdp;

	private static final int		PREFER_UDP_BLOOM_SIZE	= 10000;
	private volatile BloomFilter	preferUdpBloom;

	private final LimitedRateGroup upload_limited_rate_group = new LimitedRateGroup() {
		public String getName() {
			return ("per_dl_up: " + getDisplayName());
		}
		public int getRateLimitBytesPerSecond() {
			return adapter.getUploadRateLimitBytesPerSecond();
		}
		public boolean isDisabled() {
			return (adapter.getUploadRateLimitBytesPerSecond() == -1);
		}
		public void updateBytesUsed(
				int	used) {
		}
	};

	private final LimitedRateGroup download_limited_rate_group = new LimitedRateGroup() {
		public String getName() {
			return ("per_dl_down: " + getDisplayName());
		}
		public int getRateLimitBytesPerSecond() {
			return adapter.getDownloadRateLimitBytesPerSecond();
		}
		public boolean isDisabled() {
			return (adapter.getDownloadRateLimitBytesPerSecond() == -1);
		}
		public void updateBytesUsed(
				int	used) {
		}
	};

	private final int	partitionId;

	private final boolean	isMetadataDownload;
	private int				metadata_infodict_size;

	private GettingThere	finishInProgress;

	private long			last_seed_disconnect_time;

	private final BloomFilter		naughty_fast_extension_bloom =
			BloomFilterFactory.createRotating(
				BloomFilterFactory.createAddRemove4Bit(2000), 2);

	public PEPeerControlImpl(
		byte[]					_peer_id,
		PEPeerManagerAdapter 	_adapter,
		DiskManager 			_diskManager,
		int						_partition_id) {
		
		_myPeerId		= _peer_id;
		adapter 		= _adapter;
		diskMgr 		= _diskManager;
		partitionId	= _partition_id;
		boolean is_private = false;
		try {
			is_private = diskMgr.getTorrent().getPrivate();
		} catch (Throwable e) {
			Debug.out(e);
		}
		isPrivateTorrent = is_private;
		isMetadataDownload	= adapter.isMetadataDownload();
		if (!isMetadataDownload) {
			metadata_infodict_size	= adapter.getTorrentInfoDictSize();
		}
		_nbPieces = diskMgr.getNbPieces();
		dmPieces = diskMgr.getPieces();
		pePieces = new PEPieceImpl[_nbPieces];
		hiddenPiece = hideAPiece?((int)(Math.abs(adapter.getRandomSeed())%_nbPieces)):-1;
		/*
		if (hidden_piece >= 0) {
			System.out.println("Hidden piece for " + getDisplayName() + " = " + hidden_piece);
		}
		*/
		piecePicker = PiecePickerFactory.create(this);
		ipFilter.addListener(this);
	}

	public void start() {
		//This torrent Hash
		try {
			_hash = PeerIdentityManager.createDataID(diskMgr.getTorrent().getHash());
		} catch (TOTorrentException e) {
			// this should never happen
			Debug.printStackTrace(e);
			_hash = PeerIdentityManager.createDataID(new byte[20]);
		}
		// the recovered active pieces
		for (int i = 0; i < _nbPieces; i++) {
			final DiskManagerPiece dmPiece = dmPieces[i];
			if (!dmPiece.isDone() && dmPiece.getNbWritten() > 0) {
				addPiece(new PEPieceImpl(this, dmPiece, 0), i, true, null);
			}
		}
		//The peer connections
		peerTransportsCow = new ArrayList();
		//BtManager is threaded, this variable represents the
		// current loop iteration. It's used by some components only called
		// at some specific times.
		mainloopLoopCount = 0;
		//The current tracker state
		//this could be start or update
		_averageReceptionSpeed = Average.getInstance(1000, 30);
		// the stats
		_stats =new PEPeerManagerStatsImpl(this);
		superSeedMode = (COConfigurationManager.getBooleanParameter("Use Super Seeding") && this.getRemaining() == 0);
		superSeedModeCurrentPiece = 0;
		if (superSeedMode) {
			initialiseSuperSeedMode();
		}
		// initial check on finished state - future checks are driven by piece check results
		// Moved out of mainLoop() so that it runs immediately, possibly changing
		// the state to seeding.
		checkFinished(true);
		UploadSlotManager.getSingleton().registerHelper(uploadHelper);
		lastNeededUndonePieceChange =Long.MIN_VALUE;
		_timeStarted 		= SystemTime.getCurrentTime();
		_timeStarted_mono 	= SystemTime.getMonotonousTime();
		isRunning = true;
		// activate after marked as running as we may synchronously add connections here due to pending activations
		PeerManagerRegistration reg = adapter.getPeerManagerRegistration();
		if (reg != null) {
			reg.activate(this);
		}
		PeerNATTraverser.getSingleton().register(this);
		PeerControlSchedulerFactory.getSingleton(partitionId).register(this);
	}

	public void stopAll() {
		isRunning =false;
		UploadSlotManager.getSingleton().deregisterHelper(uploadHelper);
		PeerControlSchedulerFactory.getSingleton(partitionId).unregister(this);
		PeerNATTraverser.getSingleton().unregister(this);
		// remove legacy controller activation
		PeerManagerRegistration reg = adapter.getPeerManagerRegistration();
		if (reg != null) {
			reg.deactivate();
		}
		closeAndRemoveAllPeers("download stopped", false);
		// clear pieces
		for (int i =0; i <_nbPieces; i++) {
			if (pePieces[i] !=null)
				removePiece(pePieces[i], i);
		}
		// 5. Remove listeners
		ipFilter.removeListener(this);
		piecePicker.destroy();
		final ArrayList<PEPeerManagerListener> peer_manager_listeners = peerManagerListenersCow;
		for (int i=0; i < peer_manager_listeners.size(); i++) {
			((PEPeerManagerListener)peer_manager_listeners.get(i)).destroyed();
		}
		sweepList = Collections.emptyList();
		pendingNatTraversals.clear();
		udpReconnects.clear();
		isDestroyed = true;
	}

	public int getPartitionID() {
		return (partitionId);
	}

	public boolean isDestroyed() {
		return (isDestroyed);
	}

	public DiskManager getDiskManager() {  return diskMgr;   }
	public PiecePicker getPiecePicker() {
		return piecePicker;
	}

	public PEPeerManagerAdapter	getAdapter() { return (adapter); }

	public String getDisplayName() { return ( adapter.getDisplayName()); }

	public String getName() {
		return ( getDisplayName());
	}

	public void schedule() {
		
		if (finishInProgress != null) {
			// System.out.println("Finish in prog");
			if (finishInProgress.hasGotThere()) {
				finishInProgress = null;
				// System.out.println("Finished");
			} else {
				return;
			}
		}
		
		try {
			// first off update the stats so they can be used by subsequent steps
			updateStats();
			updateTrackerAnnounceInterval();
			doConnectionChecks();
			processPieceChecks();
			if (finishInProgress != null) {
				// get off the scheduler thread while potentially long running operations complete
				return;
			}
			
			// note that seeding_mode -> torrent totally downloaded, not just non-dnd files
			// complete, so there is no change of a new piece appearing done by a means such as
			// background periodic file rescans
			if (!seedingMode) {
				checkCompletedPieces(); //check to see if we've completed anything else
			}
			checkBadPieces();
			checkInterested();      // see if need to recheck Interested on all peers
			piecePicker.updateAvailability();
			checkCompletionState();	// pick up changes in completion caused by dnd file changes
			if (finishInProgress != null) {
				// get off the scheduler thread while potentially long running operations complete
				return;
			}
			
			checkSeeds();
			if (!seedingMode) {
				// if we're not finished
				checkRequests();
				piecePicker.allocateRequests();
				checkRescan();
				checkSpeedAndReserved();
				check99PercentBug();
			}

			updatePeersInSuperSeedMode();
			doUnchokes();
		} catch (Throwable e) {
			Debug.printStackTrace(e);
		}
		mainloopLoopCount++;
	}



	/**
	 * A private method that does analysis of the result sent by the tracker.
	 * It will mainly open new connections with peers provided
	 * and set the timeToWait variable according to the tracker response.
	 * @param tracker_response
	 */

	private void analyseTrackerResponse(
			TRTrackerAnnouncerResponse	tracker_response) {
		// tracker_response.print();
		final TRTrackerAnnouncerResponsePeer[]	peers = tracker_response.getPeers();

		if (peers != null) {
			addPeersFromTracker( tracker_response.getPeers());
		}

		final Map extensions = tracker_response.getExtensions();

		if (extensions != null) {
			addExtendedPeersFromTracker(extensions);
		}
	}

	public void processTrackerResponse(
			TRTrackerAnnouncerResponse	response) {
		// only process new peers if we're still running
		if (isRunning) {
			analyseTrackerResponse(response);
		}
	}

	private void addExtendedPeersFromTracker(
			Map		extensions) {
		final Map	protocols = (Map)extensions.get("protocols");

		if (protocols != null) {

			System.out.println("PEPeerControl: tracker response contained protocol extensions");

			final Iterator protocol_it = protocols.keySet().iterator();

			while (protocol_it.hasNext()) {

				final String	protocol_name = (String)protocol_it.next();

				final Map	protocol = (Map)protocols.get(protocol_name);

				final List	transports = PEPeerTransportFactory.createExtendedTransports(this, protocol_name, protocol);

				for (int i=0;i<transports.size();i++) {

					final PEPeer	transport = (PEPeer)transports.get(i);

					addPeer(transport);
				}
			}
		}
	}

	public List<PEPeer>
	getPeers() {
		return ((List)peerTransportsCow);
	}

	public List<PEPeer>
	getPeers(
		String	address) {
		List<PEPeer>	result = new ArrayList<PEPeer>();

		Iterator<PEPeerTransport>	it = peerTransportsCow.iterator();

		if (address.contains(":")) {

				// straight forward string matching can fail due to use of :: compression

			try {
				byte[] address_bytes = InetAddress.getByName(address).getAddress();

				while (it.hasNext()) {

					PEPeerTransport	peer = it.next();

					String peer_address = peer.getIp();

					if (peer_address.contains(":")) {

						byte[] peer_bytes = (byte[])peer.getUserData("ipv6.bytes");

						if (peer_bytes == null) {

							peer_bytes = InetAddress.getByName(peer_address).getAddress();

							peer.setUserData("ipv6.bytes", peer_bytes);
						}

						if (Arrays.equals( address_bytes, peer_bytes)) {

							result.add(peer);
						}
					}
				}

				return (result);

			} catch (Throwable e) {

				// weird, just carry on
			}
		}

		while (it.hasNext()) {

			PEPeerTransport	peer = it.next();

			if (peer.getIp().equals( address)) {

				result.add(peer);
			}
		}

		return (result);
	}

	public int getPendingPeerCount() {
		return ( peerDatabase.getDiscoveredPeerCount());
	}

	public PeerDescriptor[]
  	getPendingPeers()
  	{
  		return ((PeerDescriptor[])peerDatabase.getDiscoveredPeers());
  	}

	public PeerDescriptor[]
	getPendingPeers(
		String	address) {
		return ((PeerDescriptor[])peerDatabase.getDiscoveredPeers(address));
	}

	public void addPeer(
		PEPeer		_transport) {
		if (!( _transport instanceof PEPeerTransport)) {

			throw (new RuntimeException("invalid class"));
		}

		final PEPeerTransport	transport = (PEPeerTransport)_transport;

		if (!ipFilter.isInRange(transport.getIp(), getDisplayName(), getTorrentHash())) {

			final ArrayList<PEPeerTransport> peer_transports = peerTransportsCow;

			if (!peer_transports.contains(transport)) {

				addToPeerTransports(transport);

				transport.start();

			} else {
				Debug.out("addPeer():: peer_transports.contains(transport): SHOULD NEVER HAPPEN !");
				transport.closeConnection("already connected");
			}
		} else {

			transport.closeConnection("IP address blocked by filters");
		}
	}

	protected byte[]
	getTorrentHash() {
		try {
			return ( diskMgr.getTorrent().getHash());

		} catch (Throwable e) {

			return (null);
		}
	}
	public void removePeer(
			PEPeer	_transport) {
		removePeer(_transport, "remove peer");
	}

	public void removePeer(
			PEPeer	_transport,
			String	reason) {
		if (!( _transport instanceof PEPeerTransport)) {

			throw (new RuntimeException("invalid class"));
		}

		PEPeerTransport	transport = (PEPeerTransport)_transport;

		closeAndRemovePeer(transport, reason, true);
	}

	private void closeAndRemovePeer(PEPeerTransport peer, String reason, boolean log_if_not_found) {
		boolean removed =false;

		// copy-on-write semantics
		try {
			peerTransportsMon.enter();

			if (peerTransportsCow.contains( peer)) {

				final ArrayList new_peer_transports = new ArrayList(peerTransportsCow);

				new_peer_transports.remove(peer);
				peerTransportsCow =new_peer_transports;
				removed =true;
			}
		}
		finally{
			peerTransportsMon.exit();
		}

		if (removed) {
			peer.closeConnection(reason);
			peerRemoved(peer);  	//notify listeners
		}
		else {
			if (log_if_not_found) {
				// we know this happens due to timing issues... Debug.out("closeAndRemovePeer(): peer not removed");
			}
		}
	}

	private void closeAndRemoveAllPeers(String reason, boolean reconnect) {
		List<PEPeerTransport> peer_transports;

		try {
			peerTransportsMon.enter();

			peer_transports = peerTransportsCow;

			peerTransportsCow = new ArrayList<PEPeerTransport>(0);
		}
		finally{
			peerTransportsMon.exit();
		}

		for (int i=0; i < peer_transports.size(); i++) {
			final PEPeerTransport peer = peer_transports.get(i);

			try {

				peer.closeConnection(reason);

			} catch (Throwable e) {

				// if something goes wrong with the close process (there's a bug in there somewhere whereby
				// we occasionally get NPEs then we want to make sure we carry on and close the rest

				Debug.printStackTrace(e);
			}

			try {
				peerRemoved(peer);  //notify listeners

			} catch (Throwable e) {

				Debug.printStackTrace(e);
			}
		}

		if (reconnect) {
			for (int i=0; i < peer_transports.size(); i++) {
				final PEPeerTransport peer = peer_transports.get(i);

				PEPeerTransport	reconnected_peer = peer.reconnect(false, false);
			}
		}
	}




	public void addPeer(
		String 		ip_address,
		int			tcp_port,
		int			udp_port,
		boolean 	use_crypto,
		Map			user_data) {
		final byte type = use_crypto ? PeerItemFactory.HANDSHAKE_TYPE_CRYPTO : PeerItemFactory.HANDSHAKE_TYPE_PLAIN;
		final PeerItem peer_item = PeerItemFactory.createPeerItem(ip_address, tcp_port, PeerItem.convertSourceID( PEPeerSource.PS_PLUGIN ), type, udp_port, PeerItemFactory.CRYPTO_LEVEL_1, 0);

		byte	crypto_level = PeerItemFactory.CRYPTO_LEVEL_1;

		if (!isAlreadyConnected( peer_item )) {

			String fail_reason;

			boolean	tcp_ok = TCPNetworkManager.TCP_OUTGOING_ENABLED && tcp_port > 0;
			boolean udp_ok = UDPNetworkManager.UDP_OUTGOING_ENABLED && udp_port > 0;

			if (	tcp_ok && !(( preferUdp || preferUdpDefault) && udp_ok)) {

				fail_reason = makeNewOutgoingConnection(PEPeerSource.PS_PLUGIN, ip_address, tcp_port, udp_port, true, use_crypto, crypto_level, user_data);  //directly inject the the imported peer

			} else if (udp_ok) {

				fail_reason = makeNewOutgoingConnection(PEPeerSource.PS_PLUGIN, ip_address, tcp_port, udp_port, false, use_crypto, crypto_level, user_data);  //directly inject the the imported peer

			} else {

				fail_reason = "No usable protocol";
			}

			if (fail_reason != null )  Debug.out("Injected peer " + ip_address + ":" + tcp_port + " was not added - " + fail_reason);
		}
	}

	public void peerDiscovered(
		String		peerSource,
		String 		ipAddress,
		int			tcpPort,
		int			udpPort,
		boolean 	useCrypto) {
		if (peerDatabase != null) {
			final ArrayList<PEPeerTransport> peerTransports = peerTransportsCow;
			for (int x=0; x < peerTransports.size(); x++) {
				PEPeer transport = peerTransports.get(x);
				// allow loopback connects for co-located proxy-based connections and testing
				if (ipAddress.equals( transport.getIp())) {
					boolean sameAllowed = COConfigurationManager.getBooleanParameter("Allow Same IP Peers") ||
					transport.getIp().equals("127.0.0.1");
					if (!sameAllowed || tcpPort == transport.getPort()) {
						return;
					}
				}
			}
			byte type = useCrypto ? PeerItemFactory.HANDSHAKE_TYPE_CRYPTO : PeerItemFactory.HANDSHAKE_TYPE_PLAIN;
			PeerItem item = PeerItemFactory.createPeerItem(
					ipAddress,
					tcpPort,
					PeerItem.convertSourceID(peerSource),
					type,
					udpPort,
					PeerItemFactory.CRYPTO_LEVEL_1,
					0);
			peerDiscovered(null, item);
			peerDatabase.addDiscoveredPeer(item);
		}
	}

	private void addPeersFromTracker(TRTrackerAnnouncerResponsePeer[] peers) {
		for (int i = 0; i < peers.length; i++) {
			final TRTrackerAnnouncerResponsePeer peer = peers[i];
			final List<PEPeerTransport> peerTransports = peerTransportsCow;
			boolean alreadyConnected = false;
			for (int x=0; x < peerTransports.size(); x++) {
				final PEPeerTransport transport = peerTransports.get(x);
				// allow loopback connects for co-located proxy-based connections and testing
				if (peer.getAddress().equals( transport.getIp())) {
					final boolean same_allowed = COConfigurationManager.getBooleanParameter("Allow Same IP Peers") ||
					transport.getIp().equals("127.0.0.1");
					if (!same_allowed || peer.getPort() == transport.getPort()) {
						alreadyConnected = true;
						break;
					}
				}
			}
			if (alreadyConnected)  continue;
			if (peerDatabase != null) {
				byte type = peer.getProtocol() == DownloadAnnounceResultPeer.PROTOCOL_CRYPT ? PeerItemFactory.HANDSHAKE_TYPE_CRYPTO : PeerItemFactory.HANDSHAKE_TYPE_PLAIN;
				byte cryptoLevel = peer.getAZVersion() < TRTrackerAnnouncer.AZ_TRACKER_VERSION_3?PeerItemFactory.CRYPTO_LEVEL_1:PeerItemFactory.CRYPTO_LEVEL_2;
				PeerItem item = PeerItemFactory.createPeerItem(
						peer.getAddress(),
						peer.getPort(),
						PeerItem.convertSourceID(peer.getSource()),
						type,
						peer.getUDPPort(),
						cryptoLevel,
						peer.getUploadSpeed());
				peerDiscovered(null, item);
				peerDatabase.addDiscoveredPeer(item);
			}
			int	httpPort = peer.getHTTPPort();
			if (httpPort != 0 && !seedingMode) {
				adapter.addHTTPSeed(peer.getAddress(), httpPort);
			}
		}
	}


	/**
	 * Request a new outgoing peer connection.
	 * @param address ip of remote peer
	 * @param port remote peer listen port
	 * @return null if the connection was added to the transport list, reason if rejected
	 */
	private String makeNewOutgoingConnection(
			String	peerSource,
			String 	address,
			int 	tcpPort,
			int		udpPort,
			boolean	useTcp,
			boolean requireCrypto,
			byte	cryptoLevel,
			Map		userData) {
		
		//make sure this connection isn't filtered
		if (ipFilter.isInRange(address, getDisplayName(), getTorrentHash())) {
			return ("IPFilter block");
		}
		
		String netCat = AENetworkClassifier.categoriseAddress(address);
		if (!adapter.isNetworkEnabled(netCat)) {
			return ("Network '" + netCat + "' is not enabled");
		}
		if (!adapter.isPeerSourceEnabled(peerSource)) {
			return ("Peer source '" + peerSource + "' is not enabled");
		}
		boolean	isPriorityConnection = false;
		if (userData != null) {
			Boolean pc = (Boolean)userData.get(Peer.PR_PRIORITY_CONNECTION);
			if (pc != null && pc.booleanValue()) {
				isPriorityConnection = true;
			}
		}
		
		//make sure we need a new connection
		boolean max_reached = getMaxNewConnectionsAllowed(netCat) == 0;	// -1 -> unlimited
		if (max_reached) {
			if (	peerSource != PEPeerSource.PS_PLUGIN ||
					!doOptimisticDisconnect(
							AddressUtils.isLANLocalAddress(address) != AddressUtils.LAN_LOCAL_NO,
							isPriorityConnection,
							netCat )) {
				return ("Too many connections");
			}
		}
		
		//make sure not already connected to the same IP address; allow loopback connects for co-located proxy-based connections and testing
		final boolean same_allowed = COConfigurationManager.getBooleanParameter("Allow Same IP Peers" ) || address.equals("127.0.0.1");
		if (!same_allowed && PeerIdentityManager.containsIPAddress(_hash, address)) {
			return ("Already connected to IP");
		}
		if (PeerUtils.ignorePeerPort( tcpPort )) {
			if (Logger.isEnabled())
				Logger.log(new LogEvent(diskMgr.getTorrent(), LOGID,
						"Skipping connect with " + address + ":" + tcpPort
						+ " as peer port is in ignore list."));
			return ("TCP port '" + tcpPort + "' is in ignore list");
		}

		//start the connection
		PEPeerTransport real = PEPeerTransportFactory.createTransport(this,
				peerSource,
				address,
				tcpPort,
				udpPort, 
				useTcp, 
				requireCrypto, 
				cryptoLevel, 
				userData);
		addToPeerTransports(real);
		return null;
	}


	/**
	 * A private method that checks if PEPieces being downloaded are finished
	 * If all blocks from a PEPiece are written to disk, this method will
	 * queue the piece for hash check.
	 * Elsewhere, if it passes sha-1 check, it will be marked as downloaded,
	 * otherwise, it will unmark it as fully downloaded, so blocks can be retreived again.
	 */
	private void checkCompletedPieces() {
		if ((mainloopLoopCount %MAINLOOP_ONE_SECOND_INTERVAL) !=0)
			return;

		//for every piece
		for (int i = 0; i <_nbPieces; i++) {
			final DiskManagerPiece dmPiece =dmPieces[i];
			//if piece is completly written, not already checking, and not Done
			if (dmPiece.isNeedsCheck()) {
				//check the piece from the disk
				dmPiece.setChecking();

				DiskManagerCheckRequest req =
					diskMgr.createCheckRequest(
							i, new Integer(CHECK_REASON_DOWNLOADED));

				req.setAdHoc(false);

				diskMgr.enqueueCheckRequest( req, this);
			}
		}
	}

	/** Checks given piece to see if it's active but empty, and if so deactivates it.
	 * @param pieceNumber to check
	 * @return true if the piece was removed and is no longer active (pePiece ==null)
	 */
	private boolean checkEmptyPiece(
		final int pieceNumber) {
        if (piecePicker.isInEndGameMode()) {

			return false;   // be sure to not remove pieces in EGM
        }

		final PEPiece pePiece =pePieces[pieceNumber];
		final DiskManagerPiece dmPiece =dmPieces[pieceNumber];

		if (pePiece == null || pePiece.isRequested())
			return false;

		if (dmPiece.getNbWritten() >0 ||pePiece.getNbUnrequested() < pePiece.getNbBlocks() ||pePiece.getReservedBy() !=null)
			return false;

			// reset in case dmpiece is in some skanky state

		pePiece.reset();

		removePiece(pePiece, pieceNumber);
		return true;
	}

	/**
	 * Check if a piece's Speed is too fast for it to be getting new data
	 * and if a reserved pieced failed to get data within 120 seconds
	 */
	private void checkSpeedAndReserved() {
		// only check every 5 seconds
		if (mainloopLoopCount % MAINLOOP_FIVE_SECOND_INTERVAL != 0)
			return;

		final int				nbPieces	=_nbPieces;
		final PEPieceImpl[] pieces =pePieces;
		//for every piece
		for (int i =0; i <nbPieces; i++) {
			// placed before null-check in case it really removes a piece
			checkEmptyPiece(i);


			final PEPieceImpl pePiece =pieces[i];
			// these checks are only against pieces being downloaded
			// yet needing requests still/again
			if (pePiece !=null) {
				final long timeSinceActivity =pePiece.getTimeSinceLastActivity()/1000;

				int pieceSpeed =pePiece.getSpeed();
				// block write speed slower than piece speed
				if (pieceSpeed > 0 && timeSinceActivity*pieceSpeed*0.25 > DiskManager.BLOCK_SIZE/1024) {
					if (pePiece.getNbUnrequested() > 2)
						pePiece.setSpeed(pieceSpeed-1);
					else
						pePiece.setSpeed(0);
				}


				if (timeSinceActivity > 120) {
					pePiece.setSpeed(0);
					// has reserved piece gone stagnant?
					final String reservingPeer =pePiece.getReservedBy();
					if (reservingPeer !=null) {
						final PEPeerTransport pt = getTransportFromAddress(reservingPeer);
						// Peer is too slow; Ban them and unallocate the piece
						// but, banning is no good for peer types that get pieces reserved
						// to them for other reasons, such as special seed peers
						if (needsMD5CheckOnCompletion(i))
							badPeerDetected(reservingPeer, i);
						else if (pt != null)
							closeAndRemovePeer(pt, "Reserved piece data timeout; 120 seconds", true);

						pePiece.setReservedBy(null);
					}

					if (!piecePicker.isInEndGameMode()) {
						pePiece.checkRequests();
					}

					checkEmptyPiece(i);
				}

			}
		}
	}

	private void check99PercentBug() {
		// there's a bug whereby pieces are left downloaded but never written. might have been fixed by
		// changes to the "write result" logic, however as a stop gap I'm adding code to scan for such
		// stuck pieces and reset them

		if (mainloopLoopCount % MAINLOOP_SIXTY_SECOND_INTERVAL == 0) {

			long	now = SystemTime.getCurrentTime();

			for ( int i=0;i<pePieces.length;i++) {

				PEPiece	pe_piece = pePieces[ i ];

				if (pe_piece != null) {

					DiskManagerPiece	dm_piece = dmPieces[i];

					if (!dm_piece.isDone()) {

						if (pe_piece.isDownloaded()) {

							if (now - pe_piece.getLastDownloadTime(now) > stalledPieceTimeout) {

								// people with *very* slow disk writes can trigger this (I've been talking to a user
								// with a SAN that has .5 second write latencies when checking a file at the same time
								// this means that when dowloading > 32K/sec things start backing up). Eventually the
								// write controller will start blocking the network thread to prevent unlimited
								// queueing but until that time we need to handle this situation slightly better)

								// if there are any outstanding requests for this piece then leave it alone

								if (!( diskMgr.hasOutstandingWriteRequestForPiece( i) ||
										diskMgr.hasOutstandingReadRequestForPiece(i) ||
										diskMgr.hasOutstandingCheckRequestForPiece(i ))) {

									Debug.out("Fully downloaded piece stalled pending write, resetting p_piece " + i);

									pe_piece.reset();
								}
							}
						}
					}
				}

			}

			if (hiddenPiece >= 0) {

				int	hp_avail = piecePicker.getAvailability(hiddenPiece);

				if (hp_avail < (dmPieces[hiddenPiece].isDone()?2:1)) {

					int[] avails = piecePicker.getAvailability();

					int	num = 0;

					for ( int i=0;i<avails.length;i++) {

						if (avails[i] > 0 && !dmPieces[i].isDone() && pePieces[i] == null) {

							num++;
						}
					}

					if (num > 0) {

						num = RandomUtils.nextInt(num);

						int	backup = -1;

						for ( int i=0;i<avails.length;i++) {

							if (avails[i] > 0 && !dmPieces[i].isDone() && pePieces[i] == null) {

								if (backup == -1) {

									backup = i;
								}

								if (num == 0) {

									hiddenPiece = i;

									backup = -1;

									break;
								}

								num--;
							}
						}

						if (backup != -1) {

							hiddenPiece = backup;
						}
					}
				}
			}
		}
	}

	private void checkInterested() {
		if ((mainloopLoopCount %MAINLOOP_ONE_SECOND_INTERVAL) != 0) {
			return;
		}

		if (lastNeededUndonePieceChange >=piecePicker.getNeededUndonePieceChange())
			return;

		lastNeededUndonePieceChange =piecePicker.getNeededUndonePieceChange();

		final List<PEPeerTransport> peer_transports = peerTransportsCow;
		int cntPeersSnubbed =0;	// recount # snubbed peers while we're at it
		for (int i =0; i <peer_transports.size(); i++) {
			final PEPeerTransport peer =peer_transports.get(i);
			peer.checkInterested();
			if (peer.isSnubbed())
				cntPeersSnubbed++;
		}
		setNbPeersSnubbed(cntPeersSnubbed);
	}


	/**
	 * Private method to process the results given by DiskManager's
	 * piece checking thread via asyncPieceChecked(..)
	 */
	private void processPieceChecks() {
		if (pieceCheckResultList.size() > 0) {
			final List pieces;
			// process complete piece results
			try {
				pieceCheckResultListMon.enter();
				pieces = new ArrayList(pieceCheckResultList);
				pieceCheckResultList.clear();
			} finally {
				pieceCheckResultListMon.exit();
			}
			final Iterator it = pieces.iterator();
			while (it.hasNext()) {
				final Object[]	data = (Object[])it.next();
				//bah
				processPieceCheckResult((DiskManagerCheckRequest)data[0],((Integer)data[1]).intValue());
			}
		}
	}

	private void checkBadPieces() {
		if (mainloopLoopCount % MAINLOOP_SIXTY_SECOND_INTERVAL == 0) {

			if (badPieceReported != -1) {

				DiskManagerCheckRequest	req =
					diskMgr.createCheckRequest(
						badPieceReported,
						new Integer(CHECK_REASON_BAD_PIECE_CHECK));

				req.setLowPriority(true);

			   	if (Logger.isEnabled()) {

					Logger.log(
							new LogEvent(
								diskMgr.getTorrent(), LOGID,
								"Rescanning reported-bad piece " + badPieceReported ));

			   	}

				badPieceReported	= -1;

				try {
					diskMgr.enqueueCheckRequest(req, this);

				} catch (Throwable e) {


					Debug.printStackTrace(e);
				}
			}
		}
	}

	private void checkRescan() {
		if (rescanPieceTime == 0) {

			// pending a piece completion

			return;
		}

		if (nextRescanPiece == -1) {

			if (mainloopLoopCount % MAINLOOP_FIVE_SECOND_INTERVAL == 0) {

				if (adapter.isPeriodicRescanEnabled()) {

					nextRescanPiece	= 0;
				}
			}
		} else {

			if (mainloopLoopCount % MAINLOOP_TEN_MINUTE_INTERVAL == 0) {

				if (!adapter.isPeriodicRescanEnabled()) {

					nextRescanPiece	= -1;
				}
			}
		}

		if (nextRescanPiece == -1) {

			return;
		}

		// delay as required

		final long	now = SystemTime.getCurrentTime();

		if (rescanPieceTime > now) {

			rescanPieceTime	= now;
		}

		// 250K/sec limit

		final long	piece_size = diskMgr.getPieceLength();

		final long	millis_per_piece = piece_size / 250;

		if (now - rescanPieceTime < millis_per_piece) {

			return;
		}

		while (nextRescanPiece != -1) {

			int	this_piece = nextRescanPiece;

			nextRescanPiece++;

			if (nextRescanPiece == _nbPieces) {

				nextRescanPiece	= -1;
			}

				// this functionality is to pick up pieces that have been downloaded OUTSIDE of
				// Azureus - e.g. when two torrents are sharing a single file. Hence the check on
				// the piece NOT being done

			if (pePieces[this_piece] == null && !dmPieces[this_piece].isDone() && dmPieces[this_piece].isNeeded()) {

				DiskManagerCheckRequest	req =
					diskMgr.createCheckRequest(
							this_piece,
							new Integer(CHECK_REASON_SCAN));

				req.setLowPriority(true);

				if (Logger.isEnabled()) {

					Logger.log(
							new LogEvent(
									diskMgr.getTorrent(), LOGID,
									"Rescanning piece " + this_piece ));

				}

				rescanPieceTime	= 0;	// mark as check piece in process

				try {
					diskMgr.enqueueCheckRequest(req, this);

				} catch (Throwable e) {

					rescanPieceTime	= now;

					Debug.printStackTrace(e);
				}

				break;
			}
		}
	}

	public void badPieceReported(
		PEPeerTransport		originator,
		int					piece_number) {
		Debug.outNoStack( getDisplayName() + ": bad piece #" + piece_number + " reported by " + originator.getIp());

		if (piece_number < 0 || piece_number >= _nbPieces) {

			return;
		}

		badPieceReported = piece_number;
	}

	private static final int FE_EVENT_LIMIT	= 5;	// don't make > 15 without changing bloom!S

		/*
		 * We keep track of both peer connection events and attempts to re-download the same fast piece
		 * for a given peer to prevent an attack whereby a peer connects and repeatedly downloads the same
		 * fast piece, or alternatively connects, downloads some fast pieces, disconnects, then does so
		 * again.
		 */

	public boolean isFastExtensionPermitted(
		PEPeerTransport		originator) {
		try {
			byte[] key = originator.getIp().getBytes(Constants.BYTE_ENCODING);

			synchronized(naughty_fast_extension_bloom) {

				int events = naughty_fast_extension_bloom.add(key);

				if (events < FE_EVENT_LIMIT) {

					return (true);
				}

				Logger.log(new LogEvent(diskMgr.getTorrent(), LOGID,
						"Fast extension disabled for " + originator.getIp() + " due to repeat connections" ));
			}

		} catch (Throwable e) {
		}

		return (false);
	}

	public void reportBadFastExtensionUse(
		PEPeerTransport		originator) {
		try {
			byte[] key = originator.getIp().getBytes(Constants.BYTE_ENCODING);

			synchronized(naughty_fast_extension_bloom) {

				if (naughty_fast_extension_bloom.add(key) == FE_EVENT_LIMIT) {

					Logger.log(new LogEvent(diskMgr.getTorrent(), LOGID,
							"Fast extension disabled for " + originator.getIp() + " due to repeat requests for the same pieces" ));
				}
			}
		} catch (Throwable e) {
		}
	}

	public void setStatsReceiver(
		PEPeerManager.StatsReceiver	receiver) {
		statsReceiver = receiver;
	}

	public void statsRequest(
		PEPeerTransport 	originator,
		Map 				request) {
		Map		reply = new HashMap();

		adapter.statsRequest(originator, request, reply);

		if (reply.size() > 0) {

			originator.sendStatsReply(reply);
		}
	}

	public void statsReply(
		PEPeerTransport 	originator,
		Map 				reply) {
		PEPeerManager.StatsReceiver receiver = statsReceiver;

		if (receiver != null) {

			receiver.receiveStats(originator, reply);
		}
	}

	/**
	 * This method checks if the downloading process is finished.
	 *
	 */

	private void checkFinished(
		final boolean start_of_day) {
		final boolean all_pieces_done =diskMgr.getRemainingExcludingDND() ==0;

		if (all_pieces_done) {

			seedingMode	= true;

			preferUdpBloom = null;

			piecePicker.clearEndGameChunks();

			if (!start_of_day)
				adapter.setStateFinishing();

			_timeFinished = SystemTime.getCurrentTime();
			final List<PEPeerTransport> peer_transports = peerTransportsCow;

			//remove previous snubbing
			for (int i =0; i <peer_transports.size(); i++) {
				final PEPeerTransport pc = peer_transports.get(i);
				pc.setSnubbed(false);
			}
			setNbPeersSnubbed(0);

			final boolean checkPieces =COConfigurationManager.getBooleanParameter("Check Pieces on Completion");

			//re-check all pieces to make sure they are not corrupt, but only if we weren't already complete
			if (checkPieces &&!start_of_day) {
				final DiskManagerCheckRequest req =diskMgr.createCheckRequest(-1, new Integer(CHECK_REASON_COMPLETE));
				diskMgr.enqueueCompleteRecheckRequest(req, this);
			}

			_timeStartedSeeding 		= SystemTime.getCurrentTime();
			_timeStartedSeeding_mono 	= SystemTime.getMonotonousTime();

			try {
				diskMgr.saveResumeData(false);

			} catch (Throwable e) {
				Debug.out("Failed to save resume data", e);
			}

			adapter.setStateSeeding(start_of_day);

			final AESemaphore waiting_it = new AESemaphore("PEC:DE");

			new AEThread2("PEC:DE") {
				public void run() {
					try {
						diskMgr.downloadEnded(
							new DiskManager.OperationStatus() {
								public void gonnaTakeAWhile(
									GettingThere gt ) {
									boolean	async_set = false;

									synchronized(PEPeerControlImpl.this) {

										if (finishInProgress == null) {

											finishInProgress = gt;

											async_set = true;
										}
									}

									if (async_set) {

										waiting_it.release();
									}
								}
							});
					} finally {

						waiting_it.release();
					}
				}
			}.start();

			waiting_it.reserve();

		} else {

			seedingMode = false;
		}
	}

	protected void checkCompletionState() {
		if (mainloopLoopCount % MAINLOOP_ONE_SECOND_INTERVAL != 0) {

			return;
		}

		boolean dm_done = diskMgr.getRemainingExcludingDND() == 0;

		if (seedingMode) {

			if (!dm_done) {

				seedingMode = false;

				_timeStartedSeeding 		= -1;
				_timeStartedSeeding_mono 	= -1;
				_timeFinished				= 0;

				Logger.log(
						new LogEvent(	diskMgr.getTorrent(), LOGID,
								"Turning off seeding mode for PEPeerManager"));
			}

		} else {

			if (dm_done) {

				checkFinished(false);

				if (seedingMode) {

					Logger.log(
							new LogEvent(	diskMgr.getTorrent(), LOGID,
									"Turning on seeding mode for PEPeerManager"));

				}
			}
		}
	}

	/**
	 * This method will locate expired requests on peers, will cancel them,
	 * and mark the peer as snubbed if we haven't received usefull data from
	 * them within the last 60 seconds
	 */
	private void checkRequests() {
		// to be honest I don't see why this can't be 5 seconds, but I'm trying 1 second
		// now as the existing 0.1 second is crazy given we're checking for events that occur
		// at 60+ second intervals

		if (mainloopLoopCount % MAINLOOP_ONE_SECOND_INTERVAL != 0) {

			return;
		}

		final long now =SystemTime.getCurrentTime();

		//for every connection
		final List<PEPeerTransport> peer_transports = peerTransportsCow;
		for (int i =peer_transports.size() -1; i >=0 ; i--) {
			final PEPeerTransport pc = peer_transports.get(i);
			if (pc.getPeerState() ==PEPeer.TRANSFERING) {
				final List expired = pc.getExpiredRequests();
				if (expired !=null &&expired.size() >0) {   // now we know there's a request that's > 60 seconds old
					final boolean isSeed =pc.isSeed();
					// snub peers that haven't sent any good data for a minute
					final long timeSinceGoodData =pc.getTimeSinceGoodDataReceived();
					if (timeSinceGoodData <0 ||timeSinceGoodData >60 *1000)
						pc.setSnubbed(true);

					//Only cancel first request if more than 2 mins have passed
					DiskManagerReadRequest request =(DiskManagerReadRequest) expired.get(0);

					final long timeSinceData =pc.getTimeSinceLastDataMessageReceived();
					final boolean noData =(timeSinceData <0) ||timeSinceData >(1000 *(isSeed ?120 :60));
					final long timeSinceOldestRequest = now - request.getTimeCreated(now);


					//for every expired request
					for (int j = (timeSinceOldestRequest >120 *1000 && noData)  ? 0 : 1; j < expired.size(); j++) {
						//get the request object
						request =(DiskManagerReadRequest) expired.get(j);
						//Only cancel first request if more than 2 mins have passed
						pc.sendCancel(request);				//cancel the request object
						//get the piece number
						final int pieceNumber = request.getPieceNumber();
						PEPiece	pe_piece = pePieces[pieceNumber];
						//unmark the request on the block
						if (pe_piece != null)
							pe_piece.clearRequested(request.getOffset() /DiskManager.BLOCK_SIZE);
						// remove piece if empty so peers can choose something else, except in end game
						if (!piecePicker.isInEndGameMode())
							checkEmptyPiece(pieceNumber);
					}
				}
			}
		}
	}

	private void updateTrackerAnnounceInterval() {
		if (mainloopLoopCount % MAINLOOP_FIVE_SECOND_INTERVAL != 0) {
			return;
		}

		final int WANT_LIMIT = 100;

		int[] _num_wanted = getMaxNewConnectionsAllowed();

		int num_wanted;

		if (_num_wanted[0] < 0) {

			num_wanted = WANT_LIMIT;	// unlimited

		} else {

			num_wanted = _num_wanted[0] + _num_wanted[1];

			if (num_wanted > WANT_LIMIT) {

				num_wanted = WANT_LIMIT;
			}
		}

		final boolean has_remote = adapter.isNATHealthy();

		if (has_remote) {
			//is not firewalled, so can accept incoming connections,
			//which means no need to continually keep asking the tracker for peers
			num_wanted = (int)(num_wanted / 1.5);
		}


		int current_connection_count = PeerIdentityManager.getIdentityCount(_hash);

		final TRTrackerScraperResponse tsr = adapter.getTrackerScrapeResponse();

		if (tsr != null && tsr.isValid()) {  //we've got valid scrape info
			final int num_seeds = tsr.getSeeds();
			final int num_peers = tsr.getPeers();

			final int swarm_size;

			if (seedingMode) {
				//Only use peer count when seeding, as other seeds are unconnectable.
				//Since trackers return peers randomly (some of which will be seeds),
				//backoff by the seed2peer ratio since we're given only that many peers
				//on average each announce.
				final float ratio = (float)num_peers / (num_seeds + num_peers);
				swarm_size = (int)(num_peers * ratio);
			}
			else {
				swarm_size = num_peers + num_seeds;
			}

			if (swarm_size < num_wanted) {  //lower limit to swarm size if necessary
				num_wanted = swarm_size;
			}
		}

		if (num_wanted < 1) {  //we dont need any more connections
			adapter.setTrackerRefreshDelayOverrides(100);  //use normal announce interval
			return;
		}

		if (current_connection_count == 0)  current_connection_count = 1;  //fudge it :)

		final int current_percent = (current_connection_count * 100) / (current_connection_count + num_wanted);

		adapter.setTrackerRefreshDelayOverrides(current_percent);  //set dynamic interval override
	}

	public boolean hasDownloadablePiece() {
		return (piecePicker.hasDownloadablePiece());
	}

	public int getBytesQueuedForUpload() {
		return (bytesQueuedForUpload);
	}

	public int getNbPeersWithUploadQueued() {
		return (connectionsWithQueuedData);
	}

	public int getNbPeersWithUploadBlocked() {
		return (connectionsWithQueuedDataBlocked);
	}

	public int getNbPeersUnchoked() {
		return (connectionsUnchoked);
	}

	public int[] getAvailability() {
		return piecePicker.getAvailability();
	}

	//this only gets called when the My Torrents view is displayed
	public float getMinAvailability() {
		return piecePicker.getMinAvailability();
	}

	public float getMinAvailability(int file_index) {
		return piecePicker.getMinAvailability(file_index);
	}

	public long getBytesUnavailable() {
		return piecePicker.getBytesUnavailable();
	}

	public float getAvgAvail() {
		return piecePicker.getAvgAvail();
	}

	public long getAvailWentBadTime() {
		long went_bad = piecePicker.getAvailWentBadTime();

				// there's a chance a seed connects and then disconnects (when we're seeding) quickly
				// enough for the piece picker not to notice...

		if (piecePicker.getMinAvailability() < 1.0 && last_seed_disconnect_time > went_bad - 5000) {

			went_bad = last_seed_disconnect_time;
		}

		return (went_bad);
	}

	public void addPeerTransport(PEPeerTransport transport) {
    if (!ipFilter.isInRange(transport.getIp(), getDisplayName(), getTorrentHash())) {
			final ArrayList peer_transports = peerTransportsCow;

			if (!peer_transports.contains(transport)) {
				addToPeerTransports(transport);
			}
			else{
				Debug.out("addPeerTransport():: peer_transports.contains(transport): SHOULD NEVER HAPPEN !");
				transport.closeConnection("already connected");
			}
		}
		else {
			transport.closeConnection("IP address blocked by filters");
		}
	}


	/**
	 * Do all peer choke/unchoke processing.
	 */
	private void doUnchokes() {

		// logic below is either 1 second or 10 secondly, bail out early id neither

		if (!UploadSlotManager.AUTO_SLOT_ENABLE) {		 //manual per-torrent unchoke slot mode

			if (mainloopLoopCount % MAINLOOP_ONE_SECOND_INTERVAL != 0) {
				return;
			}

			final int max_to_unchoke = adapter.getMaxUploads();  //how many simultaneous uploads we should consider
			final ArrayList peer_transports = peerTransportsCow;

			//determine proper unchoker
			if (seedingMode) {
				if (unchoker == null || !(unchoker.isSeedingUnchoker())) {
					unchoker = UnchokerFactory.getSingleton().getUnchoker(true);
				}
			}
			else {
				if (unchoker == null || unchoker.isSeedingUnchoker()) {
					unchoker = UnchokerFactory.getSingleton().getUnchoker(false);
				}
			}

				//do main choke/unchoke update every 10 secs

			if (mainloopLoopCount % MAINLOOP_TEN_SECOND_INTERVAL == 0) {

				final boolean refresh = mainloopLoopCount % MAINLOOP_THIRTY_SECOND_INTERVAL == 0;

				boolean	do_high_latency_peers =  mainloopLoopCount % MAINLOOP_TWENTY_SECOND_INTERVAL == 0 ;

				if (do_high_latency_peers) {

					boolean ok = false;

					for (String net: AENetworkClassifier.AT_NON_PUBLIC) {

						if (adapter.isNetworkEnabled( net)) {

							ok = true;

							break;
						}
					}

					if (!ok) {

						do_high_latency_peers = false;
					}
				}

				unchoker.calculateUnchokes(max_to_unchoke, peer_transports, refresh, adapter.hasPriorityConnection(), do_high_latency_peers);

				ArrayList	chokes 		= unchoker.getChokes();
				ArrayList	unchokes	= unchoker.getUnchokes();

				addFastUnchokes(unchokes);

				UnchokerUtil.performChokes(chokes, unchokes);

			} else if (mainloopLoopCount % MAINLOOP_ONE_SECOND_INTERVAL == 0) {  //do quick unchoke check every 1 sec

				ArrayList unchokes = unchoker.getImmediateUnchokes(max_to_unchoke, peer_transports);

				addFastUnchokes(unchokes);

				UnchokerUtil.performChokes(null, unchokes);
			}
		}
	}

	private void addFastUnchokes(
		ArrayList	peers_to_unchoke) {
		for ( Iterator<PEPeerTransport> it=peerTransportsCow.iterator();it.hasNext();) {

			PEPeerTransport peer = it.next();

			if (	peer.getConnectionState() != PEPeerTransport.CONNECTION_FULLY_ESTABLISHED ||
					!UnchokerUtil.isUnchokable(peer, true) ||
					peers_to_unchoke.contains(peer)) {

				continue;
			}

			if (peer.isLANLocal()) {

				peers_to_unchoke.add(peer);

			} else if (	fastUnchokeNewPeers &&
						peer.getData("fast_unchoke_done" ) == null) {

				peer.setData("fast_unchoke_done", "");

				peers_to_unchoke.add(peer);
			}
		}
	}

	//	send the have requests out
	private void sendHave(int pieceNumber) {
		//fo
		final List<PEPeerTransport> peerTransports = peerTransportsCow;

		for (int i = 0; i < peerTransports.size(); i++) {
			//get a peer connection
			final PEPeerTransport pc = peerTransports.get(i);
			//send the have message
			pc.sendHave(pieceNumber);
		}
	}

	// Method that checks if we are connected to another seed, and if so, disconnect from him.
	private void checkSeeds() {
		//proceed on mainloop 1 second intervals if we're a seed and we want to force disconnects
		if ((mainloopLoopCount % MAINLOOP_ONE_SECOND_INTERVAL) != 0)
			return;

		if (!disconnectSeedsWhenSeeding) {
			return;
		}

		List<PEPeerTransport> to_close = null;

		final List<PEPeerTransport> peer_transports = peerTransportsCow;
		for (int i = 0; i < peer_transports.size(); i++) {
			final PEPeerTransport pc = peer_transports.get(i);

			if (pc != null && pc.getPeerState() == PEPeer.TRANSFERING && ((isSeeding() && pc.isSeed()) || pc.isRelativeSeed())) {
				if (to_close == null)  to_close = new ArrayList();
				to_close.add(pc);
			}
		}

		if (to_close != null) {
			for (int i=0; i < to_close.size(); i++) {
				closeAndRemovePeer(to_close.get(i), "disconnect other seed when seeding", false);
			}
		}
	}




	private void updateStats() {

		if ((mainloopLoopCount %MAINLOOP_ONE_SECOND_INTERVAL) != 0) {
			return;
		}

		statsTickCount++;

		//calculate seeds vs peers
		final ArrayList<PEPeerTransport> peer_transports = peerTransportsCow;

		int	new_pending_tcp_connections 	= 0;
		int new_connecting_tcp_connections	= 0;

		int	new_seeds = 0;
		int new_peers = 0;
		int new_tcp_incoming 	= 0;
		int new_udp_incoming  	= 0;
		int new_utp_incoming  	= 0;

		int	bytes_queued 	= 0;
		int	con_queued		= 0;
		int con_blocked		= 0;
		int con_unchoked	= 0;

		for ( Iterator<PEPeerTransport> it=peer_transports.iterator();it.hasNext();) {

			final PEPeerTransport pc = it.next();

			if (pc.getPeerState() == PEPeer.TRANSFERING) {

				if (!pc.isChokedByMe()) {

					con_unchoked++;
				}

				Connection connection = pc.getPluginConnection();

				if (connection != null) {

					OutgoingMessageQueue mq = connection.getOutgoingMessageQueue();

					int q = mq.getDataQueuedBytes() + mq.getProtocolQueuedBytes();

					bytes_queued += q;

					if (q > 0) {

						con_queued++;

						if (mq.isBlocked()) {

							con_blocked++;
						}
					}
				}

				if (pc.isSeed())
					new_seeds++;
				else
					new_peers++;

				if (pc.isIncoming() && !pc.isLANLocal()) {

					if (pc.isTCP()) {

						new_tcp_incoming++;

					} else {

						String protocol = pc.getProtocol();

						if (protocol.equals("UDP")) {

							new_udp_incoming++;

						} else {

							new_utp_incoming++;
						}
					}
				}
			} else {
				if (pc.isTCP()) {

					int c_state = pc.getConnectionState();

					if (c_state == PEPeerTransport.CONNECTION_PENDING) {

						new_pending_tcp_connections++;

					} else if (c_state == PEPeerTransport.CONNECTION_CONNECTING) {

						new_connecting_tcp_connections++;
					}
				}
			}
		}

		_seeds = new_seeds;
		_peers = new_peers;
		_remotesTCPNoLan = new_tcp_incoming;
		_remotesUDPNoLan = new_udp_incoming;
		_remotesUTPNoLan = new_utp_incoming;
		_tcpPendingConnections = new_pending_tcp_connections;
		_tcpConnectingConnections = new_connecting_tcp_connections;

		bytesQueuedForUpload 				= bytes_queued;
		connectionsWithQueuedData			= con_queued;
		connectionsWithQueuedDataBlocked	= con_blocked;
		connectionsUnchoked					= con_unchoked;

		_stats.update(statsTickCount);
	}
	/**
	 * The way to unmark a request as being downloaded, or also
	 * called by Peer connections objects when connection is closed or choked
	 * @param request a DiskManagerReadRequest holding details of what was canceled
	 */
	public void requestCanceled(DiskManagerReadRequest request) {
		final int pieceNumber =request.getPieceNumber();  //get the piece number
		PEPiece pe_piece = pePieces[pieceNumber];
		if (pe_piece != null) {
			pe_piece.clearRequested(request.getOffset() /DiskManager.BLOCK_SIZE);
		}
	}


	public PEPeerControl
	getControl() {
		return (this);
	}

	public byte[][]
	getSecrets(
		int	crypto_level) {
		return (adapter.getSecrets( crypto_level));
	}

//	get the hash value
	public byte[] getHash() {
		return _hash.getDataID();
	}

	public PeerIdentityDataID
	getPeerIdentityDataID() {
		return (_hash);
	}

//	get the peer id value
	public byte[] getPeerId() {
		return _myPeerId;
	}

//	get the remaining percentage
	public long getRemaining() {
		return diskMgr.getRemaining();
	}


	public void discarded(PEPeer peer, int length) {
		if (length > 0) {
			_stats.discarded(peer, length);

				// discards are more likely during end-game-mode

			if (banPeerDiscardRatio > 0 && !( piecePicker.isInEndGameMode() || piecePicker.hasEndGameModeBeenAbandoned())) {

				long	received 	= peer.getStats().getTotalDataBytesReceived();
				long	discarded	= peer.getStats().getTotalBytesDiscarded();

				long	non_discarded = received - discarded;

				if (non_discarded < 0) {

					non_discarded = 0;
				}

				if (discarded >= banPeerDiscardMinKb * 1024L) {

					if (	non_discarded == 0 ||
							((float)discarded) / non_discarded >= banPeerDiscardRatio) {

						badPeerDetected(peer.getIp(), -1);
					}
				}
			}
		}
	}

	public void dataBytesReceived(PEPeer peer, int length) {
		if (length > 0) {
			_stats.dataBytesReceived(peer,length);

			_averageReceptionSpeed.addValue(length);
		}
	}


	public void protocolBytesReceived(PEPeer peer,  int length) {
		if (length > 0) {
			_stats.protocolBytesReceived(peer,length);
		}
	}

	public void dataBytesSent(PEPeer peer, int length) {
		if (length > 0) {
			_stats.dataBytesSent(peer, length);
		}
	}


	public void protocolBytesSent(PEPeer peer, int length) {
		if (length > 0) {
			_stats.protocolBytesSent(peer,length);
		}
	}

	/** DiskManagerWriteRequestListener message
	 * @see org.gudy.azureus2.core3.disk.DiskManagerWriteRequestListener
	 */
	public void writeCompleted(DiskManagerWriteRequest request) {
		final int pieceNumber =request.getPieceNumber();

		DiskManagerPiece	dm_piece = dmPieces[pieceNumber];

		if (!dm_piece.isDone()) {

			final PEPiece pePiece =pePieces[pieceNumber];

			if (pePiece != null) {

				Object user_data = request.getUserData();

				String key;

				if (user_data instanceof String) {

					key = (String)user_data;

				} else if (user_data instanceof PEPeer) {

					key = ((PEPeer)user_data).getIp();

				} else {

					key = "<none>";
				}

				pePiece.setWritten(key, request.getOffset() /DiskManager.BLOCK_SIZE);

			} else {

				// this is a way of fixing a 99.9% bug where a dmpiece is left in a
				// fully downloaded state with the underlying pe_piece null. Possible explanation is
				// that a slow peer sends an entire piece at around the time a pe_piece gets reset
				// due to inactivity.

				// we also get here when recovering data that has come in late after the piece has
				// been abandoned

				dm_piece.setWritten(request.getOffset() /DiskManager.BLOCK_SIZE);
			}
		}
	}

	public void writeFailed(
			DiskManagerWriteRequest 	request,
			Throwable		 			cause) {
		// if the write has failed then the download will have been stopped so there is no need to try
		// and reset the piece
	}


	/** This method will queue up a dism manager write request for the block if the block is not already written.
	 * It will send out cancels for the block to all peer either if in end-game mode, or per cancel param
	 * @param pieceNumber to potentialy write to
	 * @param offset within piece to queue write for
	 * @param data to be writen
	 * @param sender peer that sent this data
	 * @param cancel if cancels definatly need to be sent to all peers for this request
	 */
	public void writeBlock(int pieceNumber, int offset, DirectByteBuffer data, Object sender, boolean cancel) {
		final int blockNumber =offset /DiskManager.BLOCK_SIZE;
		final DiskManagerPiece dmPiece =dmPieces[pieceNumber];
		if (dmPiece.isWritten(blockNumber)) {
			data.returnToPool();
			return;
		}

		PEPiece	pe_piece = pePieces[ pieceNumber ];

		if (pe_piece != null) {

			pe_piece.setDownloaded(offset);
		}

		final DiskManagerWriteRequest request =diskMgr.createWriteRequest(pieceNumber, offset, data, sender);
		diskMgr.enqueueWriteRequest(request, this);
		// In case we are in endGame mode, remove the block from the chunk list
		if (piecePicker.isInEndGameMode())
			piecePicker.removeFromEndGameModeChunks(pieceNumber, offset);
		if (cancel ||piecePicker.isInEndGameMode()) {   // cancel any matching outstanding download requests
			//For all connections cancel the request
			final List<PEPeerTransport> peer_transports = peerTransportsCow;
			for (int i=0;i<peer_transports.size();i++) {
				final PEPeerTransport connection = peer_transports.get(i);
				final DiskManagerReadRequest dmr =diskMgr.createReadRequest(pieceNumber, offset, dmPiece.getBlockSize(blockNumber));
				connection.sendCancel(dmr);
			}
		}
	}


//	/**
//	* This method is only called when a block is received after the initial request expired,
//	* but the data has not yet been fulfilled by any other peer, so we use the block data anyway
//	* instead of throwing it away, and cancel any outstanding requests for that block that might have
//	* been sent after initial expiry.
//	*/
//	public void writeBlockAndCancelOutstanding(int pieceNumber, int offset, DirectByteBuffer data,PEPeer sender) {
//	final int blockNumber =offset /DiskManager.BLOCK_SIZE;
//	final DiskManagerPiece dmPiece =dm_pieces[pieceNumber];
//	if (dmPiece.isWritten(blockNumber))
//	{
//	data.returnToPool();
//	return;
//	}
//	DiskManagerWriteRequest request =disk_mgr.createWriteRequest(pieceNumber, offset, data, sender);
//	disk_mgr.enqueueWriteRequest(request, this);

//	// cancel any matching outstanding download requests
//	List peer_transports =peer_transports_cow;
//	for (int i =0; i <peer_transports.size(); i++)
//	{
//	PEPeerTransport connection =(PEPeerTransport) peer_transports.get(i);
//	DiskManagerReadRequest dmr =disk_mgr.createReadRequest(pieceNumber, offset, dmPiece.getBlockSize(blockNumber));
//	connection.sendCancel(dmr);
//	}
//	}


	public boolean isWritten(int piece_number, int offset) {
		return dmPieces[piece_number].isWritten(offset /DiskManager.BLOCK_SIZE);
	}

	public boolean validateReadRequest(
			PEPeerTransport	originator,
			int				pieceNumber,
			int 			offset,
			int 			length) {
		if (diskMgr.checkBlockConsistencyForRead(originator.getClient() + ": " + originator.getIp(), true, pieceNumber, offset, length)) {

			if (enableSeedingPieceRechecks && isSeeding()) {

				DiskManagerPiece	dm_piece = dmPieces[pieceNumber];

				int	read_count = dm_piece.getReadCount()&0xffff;

				if (read_count < SEED_CHECK_WAIT_MARKER - 1) {

					read_count++;

					dm_piece.setReadCount((short)read_count);
				}
			}

			return (true);
		} else {

			return (false);
		}
	}

	public boolean validateHintRequest(
			PEPeerTransport	originator,
			int				pieceNumber,
			int 			offset,
			int 			length) {
		return (diskMgr.checkBlockConsistencyForHint(originator.getClient() + ": " + originator.getIp(),pieceNumber, offset, length));
	}

	public boolean validatePieceReply(
		PEPeerTransport		originator,
		int 				pieceNumber,
		int 				offset,
		DirectByteBuffer 	data) {
		return diskMgr.checkBlockConsistencyForWrite(originator.getClient() + ": " + originator.getIp(),pieceNumber, offset, data);
	}

	public int getAvailability(int pieceNumber) {
		return piecePicker.getAvailability(pieceNumber);
	}

	public void havePiece(int pieceNumber, int pieceLength, PEPeer pcOrigin) {
		piecePicker.addHavePiece(pcOrigin, pieceNumber);
		_stats.haveNewPiece(pieceLength);

		if (superSeedMode) {
			superSeedPieces[pieceNumber].peerHasPiece(pcOrigin);
			if (pieceNumber == pcOrigin.getUniqueAnnounce()) {
				pcOrigin.setUniqueAnnounce(-1);
				superSeedModeNumberOfAnnounces--;
			}
		}
		int availability =piecePicker.getAvailability(pieceNumber) -1;
		if (availability < 4) {
			if (dmPieces[pieceNumber].isDone())
				availability--;
			if (availability <= 0)
				return;
			//for all peers

			final List<PEPeerTransport> peer_transports = peerTransportsCow;

			for (int i = peer_transports.size() - 1; i >= 0; i--) {
				final PEPeerTransport pc = peer_transports.get(i);
				if (pc !=pcOrigin &&pc.getPeerState() ==PEPeer.TRANSFERING &&pc.isPieceAvailable(pieceNumber))
					((PEPeerStatsImpl)pc.getStats()).statisticalSentPiece(pieceLength / availability);
			}
		}
	}

	public int getPieceLength(int pieceNumber) {

		return diskMgr.getPieceLength(pieceNumber);

	}

	public int getNbPeers() {
		return _peers;
	}

	public int getNbSeeds() {
		return _seeds;
	}

	public int getNbRemoteTCPConnections() {
		return _remotesTCPNoLan;
	}

	public int getNbRemoteUDPConnections() {
		return _remotesUDPNoLan;
	}
	public int getNbRemoteUTPConnections() {
		return _remotesUTPNoLan;
	}

	public long getLastRemoteConnectionTime() {
		return (lastRemoteTime);
	}

	public PEPeerManagerStats getStats() {
		return _stats;
	}

	public int getNbPeersStalledPendingLoad() {
		int	res = 0;

		Iterator<PEPeerTransport> it = peerTransportsCow.iterator();

		while (it.hasNext()) {

			PEPeerTransport transport = it.next();

			if (transport.isStalledPendingLoad()) {

				res ++;
			}
		}

		return (res);
	}

	/**
	 * Returns the ETA time in seconds.
	 *   0 = download is complete.
	 * < 0 = download is complete and it took -xxx time to complete.
	 * Constants.CRAPPY_INFINITE_AS_LONG = incomplete and 0 average speed
	 *
	 * @note eta will be zero for incomplete torrent in the following case:<br>
	 * * Torrent has DND files
	 * * non-DND files are all downloaded, however, the last piece is incomplete.
	 *   The blocks for the file are downloaded, but the piece is not hash checked,
	 *   and therefore potentially incomplete.
	 */
	public long getETA(boolean smoothed) {
		final long	now = SystemTime.getCurrentTime();

		if (now < lastEtaCalculation || now - lastEtaCalculation > 900) {

			long dataRemaining = diskMgr.getRemainingExcludingDND();

			if (dataRemaining > 0) {

				int writtenNotChecked = 0;

				for (int i = 0; i < _nbPieces; i++) {
					if (dmPieces[i].isInteresting()) {
						writtenNotChecked +=dmPieces[i].getNbWritten() *DiskManager.BLOCK_SIZE;
					}
				}

				dataRemaining = dataRemaining - writtenNotChecked;

				if  (dataRemaining < 0) {
					dataRemaining	= 0;
				}
			}

			long	jagged_result;
			long	smooth_result;

			if (dataRemaining == 0) {
				final long timeElapsed = (_timeFinished - _timeStarted)/1000;
				//if time was spent downloading....return the time as negative
				if (timeElapsed > 1) {
					jagged_result = timeElapsed * -1;
				} else {
					jagged_result = 0;
				}
				smooth_result = jagged_result;
			} else {

				{
					final long averageSpeed = _averageReceptionSpeed.getAverage();
					long lETA = (averageSpeed == 0) ? Constants.CRAPPY_INFINITE_AS_LONG : dataRemaining / averageSpeed;
					// stop the flickering of ETA from "Finished" to "x seconds" when we are
					// just about complete, but the data rate is jumpy.
					if (lETA == 0)
						lETA = 1;
					jagged_result = lETA;
				}
				{
					final long averageSpeed = _stats.getSmoothedDataReceiveRate();
					long lETA = (averageSpeed == 0) ? Constants.CRAPPY_INFINITE_AS_LONG : dataRemaining / averageSpeed;
					// stop the flickering of ETA from "Finished" to "x seconds" when we are
					// just about complete, but the data rate is jumpy.
					if (lETA == 0)
						lETA = 1;
					smooth_result = lETA;
				}
			}

			lastEta				= jagged_result;
			lastEtaSmoothed		= smooth_result;
			lastEtaCalculation	= now;
		}

		return (smoothed?lastEtaSmoothed:lastEta);
	}

	public boolean isRTA() {
		return (piecePicker.getRTAProviders().size() > 0);
	}

	private void addToPeerTransports(PEPeerTransport peer) {
		boolean added = false;
		List limiters;
		try {
			peerTransportsMon.enter();
			// if it is already disconnected (synchronous failure during connect
			// for example) don't add it
			if (peer.getPeerState() == PEPeer.DISCONNECTED) {
				return;
			}
			if (peerTransportsCow.contains(peer)) {
				Debug.out("Transport added twice");
				return;  //we do not want to close it
			}
			if (isRunning) {
				//copy-on-write semantics
				final ArrayList newPeerTransports = new ArrayList(peerTransportsCow.size() +1);
				newPeerTransports.addAll(peerTransportsCow);
				newPeerTransports.add(peer);
				peerTransportsCow = newPeerTransports;
				added = true;
			}
			limiters = externalRateLimitersCow;
		} finally{
			peerTransportsMon.exit();
		}
		
		if (added) {
			boolean incoming = peer.isIncoming();
			_stats.haveNewConnection(incoming);
			if (incoming) {
				long connectTime = SystemTime.getCurrentTime();
				if (connectTime > lastRemoteTime) {
					lastRemoteTime = connectTime;
				}
			}
			if (limiters != null) {
				for (int i=0;i<limiters.size();i++) {
					Object[]	entry = (Object[])limiters.get(i);
					peer.addRateLimiter((LimitedRateGroup)entry[0],((Boolean)entry[1]).booleanValue());
				}
			}
			peerAdded(peer);
		} else {
			peer.closeConnection("PeerTransport added when manager not running");
		}
	}

	public void addRateLimiter(
		LimitedRateGroup	group,
		boolean				upload) {
		List<PEPeerTransport>	transports;
		try {
			peerTransportsMon.enter();
			ArrayList<Object[]>	new_limiters = new ArrayList<Object[]>( externalRateLimitersCow==null?1:externalRateLimitersCow.size()+1);
			if (externalRateLimitersCow != null) {
				new_limiters.addAll(externalRateLimitersCow);
			}
			new_limiters.add(new Object[]{ group, Boolean.valueOf(upload)});
			externalRateLimitersCow = new_limiters;
			transports = peerTransportsCow;
		} finally {
			peerTransportsMon.exit();
		}
		for (int i=0;i<transports.size();i++) {
			transports.get(i).addRateLimiter(group, upload);
		}
	}

	public void removeRateLimiter(
		LimitedRateGroup	group,
		boolean				upload) {
		List<PEPeerTransport>	transports;

		try {
			peerTransportsMon.enter();

			if (externalRateLimitersCow != null) {

				ArrayList	new_limiters = new ArrayList( externalRateLimitersCow.size()-1);

				for (int i=0;i<externalRateLimitersCow.size();i++) {

					Object[]	entry = (Object[])externalRateLimitersCow.get(i);

					if (entry[0] != group) {

						new_limiters.add(entry);
					}
				}

				if (new_limiters.size() == 0) {

					externalRateLimitersCow = null;

				} else {

					externalRateLimitersCow = new_limiters;
				}
			}

			transports = peerTransportsCow;

		} finally {

			peerTransportsMon.exit();
		}

		for (int i=0;i<transports.size();i++) {

			transports.get(i).removeRateLimiter(group, upload);
		}
	}

	public int getUploadRateLimitBytesPerSecond() {
		return ( adapter.getUploadRateLimitBytesPerSecond());
	}

	public int getDownloadRateLimitBytesPerSecond() {
		return ( adapter.getDownloadRateLimitBytesPerSecond());
	}

		//	the peer calls this method itself in closeConnection() to notify this manager

	public void peerConnectionClosed(
		PEPeerTransport 	peer,
		boolean 			connect_failed,
		boolean				network_failed ) {
		boolean	connection_found = false;

		boolean tcpReconnect = false;
		boolean ipv6reconnect = false;

		try {
			peerTransportsMon.enter();

			int udpPort = peer.getUDPListenPort();

			boolean canTryUDP = UDPNetworkManager.UDP_OUTGOING_ENABLED && peer.getUDPListenPort() > 0;
			boolean canTryIpv6 = NetworkAdmin.getSingleton().hasIPV6Potential(true) && peer.getAlternativeIPv6() != null;

			if (isRunning) {

				PeerItem peer_item = peer.getPeerItemIdentity();
				PeerItem self_item = peerDatabase.getSelfPeer();


				if (self_item == null || !self_item.equals( peer_item)) {

					String	ip = peer.getIp();
					boolean wasIPv6;
					if (peer.getNetwork() == AENetworkClassifier.AT_PUBLIC) {
						try {

							wasIPv6 = AddressUtils.getByName(ip) instanceof Inet6Address;
						} catch (UnknownHostException e) {
							wasIPv6 = false;
							// something is fishy about the old address, don't try to reconnect with v6
							canTryIpv6 = false;
						}
					} else {
						wasIPv6 	= false;
						canTryIpv6 	= false;
					}

					//System.out.println("netfail="+network_failed+", connfail="+connect_failed+", can6="+canTryIpv6+", was6="+wasIPv6);

					String	key = ip + ":" + udpPort;

					if (peer.isTCP()) {

						String net = AENetworkClassifier.categoriseAddress(ip);

						if (connect_failed) {

								// TCP connect failure, try UDP later if necessary

							if (canTryUDP && udpFallbackForFailedConnection) {

								pendingNatTraversals.put(key, peer);
							} else if (canTryIpv6 && !wasIPv6) {
								tcpReconnect = true;
								ipv6reconnect = true;
							}
						} else if (	canTryUDP &&
									udpFallbackForDroppedConnection &&
									network_failed &&
									seedingMode &&
									peer.isInterested() &&
									!peer.isSeed() && !peer.isRelativeSeed() &&
									peer.getStats().getEstimatedSecondsToCompletion() > 60 &&
									FeatureAvailability.isUDPPeerReconnectEnabled()) {

							if (Logger.isEnabled()) {
								Logger.log(new LogEvent(peer, LOGID, LogEvent.LT_WARNING, "Unexpected stream closure detected, attempting recovery"));
							}

								// System.out.println("Premature close of stream: " + getDisplayName() + "/" + peer.getIp());

							udpReconnects.put(key, peer);

						} else if (	network_failed &&
									peer.isSafeForReconnect() &&
									!(seedingMode && (peer.isSeed() || peer.isRelativeSeed() || peer.getStats().getEstimatedSecondsToCompletion() < 60)) &&
									getMaxConnections(net) > 0 &&
									(getMaxNewConnectionsAllowed( net ) < 0 || getMaxNewConnectionsAllowed( net ) > getMaxConnections( net ) / 3 )&&
									FeatureAvailability.isGeneralPeerReconnectEnabled()) {

							tcpReconnect = true;
						}
					} else if (connect_failed) {

							// UDP connect failure

						if (udpFallbackForFailedConnection) {

							if (peer.getData(PEER_NAT_TRAVERSE_DONE_KEY) == null) {

								// System.out.println("Direct reconnect failed, attempting NAT traversal");

								pendingNatTraversals.put(key, peer);
							}
						}
					}
				}
			}

			if (peerTransportsCow.contains( peer)) {
				final ArrayList new_peer_transports = new ArrayList(peerTransportsCow);
				new_peer_transports.remove(peer);
				peerTransportsCow = new_peer_transports;
				connection_found  = true;
			}
		}
		finally{
			peerTransportsMon.exit();
		}

		if (connection_found) {
			if (peer.getPeerState() != PEPeer.DISCONNECTED) {
				System.out.println("peer.getPeerState() != PEPeer.DISCONNECTED: " +peer.getPeerState());
			}

			peerRemoved(peer);  //notify listeners
		}

		if (tcpReconnect)
			peer.reconnect(false, ipv6reconnect);
	}




	public void peerAdded(
			PEPeer pc) {
		adapter.addPeer(pc);  //async downloadmanager notification

		//sync peermanager notification
		final ArrayList peer_manager_listeners = peerManagerListenersCow;

		for (int i=0; i < peer_manager_listeners.size(); i++) {
			((PEPeerManagerListener)peer_manager_listeners.get(i)).peerAdded(this, pc);
		}
	}


	public void peerRemoved(
			PEPeer pc) {
		if (	isRunning &&
				!seedingMode &&
				(preferUdp || preferUdpDefault)) {

			int udp = pc.getUDPListenPort();

			if (udp != 0 && udp == pc.getTCPListenPort()) {

				BloomFilter filter = preferUdpBloom;

				if (filter == null) {

					filter = preferUdpBloom = BloomFilterFactory.createAddOnly(PREFER_UDP_BLOOM_SIZE);
				}

				if (filter.getEntryCount() < PREFER_UDP_BLOOM_SIZE / 10) {

					filter.add( pc.getIp().getBytes());
				}
			}
		}

		final int piece = pc.getUniqueAnnounce();
		if (piece != -1 && superSeedMode) {
			superSeedModeNumberOfAnnounces--;
			superSeedPieces[piece].peerLeft();
		}

		int[]	reserved_pieces = pc.getReservedPieceNumbers();

		if (reserved_pieces != null) {

			for (int reserved_piece: reserved_pieces) {

				PEPiece	pe_piece = pePieces[reserved_piece];

				if (pe_piece != null) {

					String	reserved_by = pe_piece.getReservedBy();

					if (reserved_by != null && reserved_by.equals( pc.getIp())) {

						pe_piece.setReservedBy(null);
					}
				}
			}
		}

		if (pc.isSeed()) {

			last_seed_disconnect_time = SystemTime.getCurrentTime();
		}

		adapter.removePeer(pc);  //async downloadmanager notification

		//sync peermanager notification
		final ArrayList peer_manager_listeners = peerManagerListenersCow;

		for (int i=0; i < peer_manager_listeners.size(); i++) {
			((PEPeerManagerListener)peer_manager_listeners.get(i)).peerRemoved(this, pc);
		}
	}

	/** Don't pass a null to this method. All activations of pieces must go through here.
	 * @param piece PEPiece invoked; notifications of it's invocation need to be done
	 * @param pieceNumber of the PEPiece
	 */
	public void addPiece(final PEPiece piece, final int pieceNumber, PEPeer for_peer) {
		addPiece(piece, pieceNumber, false, for_peer);
	}

	protected void addPiece(final PEPiece piece, final int pieceNumber, final boolean forceAdd, PEPeer forPeer) {
		if (piece == null || pePieces[pieceNumber] != null) {
			Debug.out("piece state inconsistent");
		}
		pePieces[pieceNumber] = (PEPieceImpl)piece;
		nbPiecesActive++;
		if (isRunning || forceAdd) {
			// deal with possible piece addition by scheduler loop after closdown started
			adapter.addPiece(piece);
		}
		final ArrayList peerManagerListeners = peerManagerListenersCow;
		for (int i=0; i < peerManagerListeners.size(); i++) {
			try {
				((PEPeerManagerListener)peerManagerListeners.get(i)).pieceAdded(this, piece, forPeer);
			} catch (Throwable e) {
				Debug.printStackTrace(e);
			}
		}
	}

	/** Sends messages to listeners that the piece is no longer active.  All closing
	 * out (deactivation) of pieces must go through here. The piece will be null upon return.
	 * @param pePiece PEPiece to remove
	 * @param pieceNumber int
	 */
	public void removePiece(PEPiece pePiece, int pieceNumber) {
		if (pePiece != null) {
			adapter.removePiece(pePiece);
		} else {
			pePiece = pePieces[pieceNumber];
		}

			// only decrement num-active if this piece was active (see comment below as to why this might no be the case)

		if (pePieces[pieceNumber] != null) {
			pePieces[pieceNumber] = null;
			nbPiecesActive--;
		}

		if (pePiece == null) {
			// we can get here without the piece actually being active when we have a very slow peer that is sent a request for the last
			// block of a piece, doesn't reply, the request gets cancelled, (and piece marked as inactive) and then it sends the block
			// and our 'recover block as useful' logic kicks in, writes the block, completes the piece, triggers a piece check and here we are

			return;
		}

		final ArrayList peer_manager_listeners = peerManagerListenersCow;

		for (int i=0; i < peer_manager_listeners.size(); i++) {
			try {
				((PEPeerManagerListener)peer_manager_listeners.get(i)).pieceRemoved(this, pePiece);

			} catch (Throwable e) {

				Debug.printStackTrace(e);
			}
		}
	}

	public int getNbActivePieces() {
		return nbPiecesActive;
	}

	public String getElapsedTime() {
		return TimeFormatter.format((SystemTime.getCurrentTime() - _timeStarted) / 1000);
	}

//	Returns time started in ms
	public long getTimeStarted( boolean mono) {
		return mono?_timeStarted_mono:_timeStarted;
	}

	public long getTimeStartedSeeding( boolean mono) {
		return mono?_timeStartedSeeding_mono:_timeStartedSeeding;
	}

	private byte[] computeMd5Hash(DirectByteBuffer buffer) {
		BrokenMd5Hasher md5 	= new BrokenMd5Hasher();

		md5.reset();
		final int position = buffer.position(DirectByteBuffer.SS_DW);
		md5.update(buffer.getBuffer(DirectByteBuffer.SS_DW));
		buffer.position(DirectByteBuffer.SS_DW, position);
		ByteBuffer md5Result	= ByteBuffer.allocate(16);
		md5Result.position(0);
		md5.finalDigest(md5Result);

		final byte[] result =new byte[16];
		md5Result.position(0);
		for (int i =0; i <result.length; i++) {
			result[i] = md5Result.get();
		}

		return result;
	}

	private void MD5CheckPiece(PEPiece piece, boolean correct) {
		final String[] writers =piece.getWriters();
		int offset = 0;
		for (int i =0; i <writers.length; i++) {
			final int length =piece.getBlockSize(i);
			final String peer =writers[i];
			if (peer !=null) {
				DirectByteBuffer buffer =diskMgr.readBlock(piece.getPieceNumber(), offset, length);

				if (buffer !=null) {
					final byte[] hash =computeMd5Hash(buffer);
					buffer.returnToPool();
					buffer = null;
					piece.addWrite(i,peer,hash,correct);
				}
			}
			offset += length;
		}
	}

	public void checkCompleted(DiskManagerCheckRequest request, boolean passed) {
		if (TEST_PERIODIC_SEEDING_SCAN_FAIL_HANDLING && ((Integer) request.getUserData()).intValue() == CHECK_REASON_SEEDING_CHECK) {
			passed = false;
		}
		
		try {
			pieceCheckResultListMon.enter();
			pieceCheckResultList.add(new Object[]{request, new Integer(passed?1:0)});
		} finally {
			pieceCheckResultListMon.exit();
		}
	}

	public void checkCancelled(DiskManagerCheckRequest request) {
		try {
			pieceCheckResultListMon.enter();
			pieceCheckResultList.add(new Object[]{request, new Integer(2)});
		} finally {
			pieceCheckResultListMon.exit();
		}
	}

	public void checkFailed(DiskManagerCheckRequest request, Throwable cause) {
		try {
			pieceCheckResultListMon.enter();
			pieceCheckResultList.add(new Object[]{request, new Integer(0)});
		} finally {
			pieceCheckResultListMon.exit();
		}
	}

	public boolean needsMD5CheckOnCompletion(int pieceNumber) {
		final PEPieceImpl piece = pePieces[pieceNumber];
		if (piece == null)
			return false;
		return piece.getPieceWrites().size() > 0;
	}

	private void processPieceCheckResult(DiskManagerCheckRequest request, int outcome) {
		final int checkType = ((Integer) request.getUserData()).intValue();
		try {
			final int pieceNumber = request.getPieceNumber();
			// System.out.println("processPieceCheckResult(" + _finished + "/" + recheck_on_completion + "):" + pieceNumber +
			// "/" + piece + " - " + result);
			// passed = 1, failed = 0, cancelled = 2
			if (checkType == CHECK_REASON_COMPLETE) {
				// this is a recheck, so don't send HAVE msgs
				if (outcome == 0) {
					// piece failed; restart the download afresh
					Debug.out(getDisplayName() + ": Piece #" +pieceNumber +" failed final re-check. Re-downloading...");
					if (!restartInitiated) {
						restartInitiated	= true;
						adapter.restartDownload(true);
					}
				}
				return;
			} else if (checkType == CHECK_REASON_SEEDING_CHECK || checkType == CHECK_REASON_BAD_PIECE_CHECK) {
				if (outcome == 0) {
					if (checkType == CHECK_REASON_SEEDING_CHECK) {
					Debug.out(getDisplayName() + "Piece #" +pieceNumber +" failed recheck while seeding. Re-downloading...");
					} else {
						Debug.out(getDisplayName() + "Piece #" +pieceNumber +" failed recheck after being reported as bad. Re-downloading...");
					}
					Logger.log(new LogAlert(this, LogAlert.REPEATABLE, LogAlert.AT_ERROR,
							"Download '" + getDisplayName() + "': piece " + pieceNumber
									+ " has been corrupted, re-downloading"));
					if (!restartInitiated) {
						restartInitiated = true;
						adapter.restartDownload(true);
					}
				}
				return;
			}
			// piece can be null when running a recheck on completion
			// actually, give the above code I don't think this is true anymore...
			final PEPieceImpl pePiece = pePieces[pieceNumber];
			if (outcome == 1 || isMetadataDownload) {
				//  the piece has been written correctly
				try {
					if (pePiece !=null) {
						if (needsMD5CheckOnCompletion(pieceNumber)) {
							MD5CheckPiece(pePiece, true);
						}
						
						final List list = pePiece.getPieceWrites();
						if (list.size() > 0) {
							//For each Block
							for (int i =0; i <pePiece.getNbBlocks(); i++) {
								//System.out.println("Processing block " + i);
								//Find out the correct hash
								final List listPerBlock =pePiece.getPieceWrites(i);
								byte[] correctHash = null;
								//PEPeer correctSender = null;
								Iterator iterPerBlock = listPerBlock.iterator();
								while (iterPerBlock.hasNext()) {
									final PEPieceWriteImpl write = (PEPieceWriteImpl) iterPerBlock.next();
									if (write.isCorrect()) {
										correctHash = write.getHash();
										//correctSender = write.getSender();
									}
								}
								//System.out.println("Correct Hash " + correctHash);
								//If it's found
								if (correctHash !=null) {
									iterPerBlock = listPerBlock.iterator();
									while (iterPerBlock.hasNext()) {
										final PEPieceWriteImpl write = (PEPieceWriteImpl) iterPerBlock.next();
										if (!Arrays.equals(write.getHash(), correctHash)) {
											//Bad peer found here
											badPeerDetected(write.getSender(),pieceNumber);
										}
									}
								}
							}
						}
					}
				} finally {
					// regardless of any possible failure above, tidy up correctly
					removePiece(pePiece, pieceNumber);
					// send all clients a have message
					sendHave(pieceNumber);  //XXX: if Done isn't set yet, might refuse to send this piece
				}
			} else if (outcome == 0) {
				//    the piece is corrupt
				Iterator<PEPeerManagerListener> it = peerManagerListenersCow.iterator();
				while (it.hasNext()) {
					try {
						it.next().pieceCorrupted(this, pieceNumber);
					} catch (Throwable e) {
						Debug.printStackTrace(e);
					}
				}
				if (pePiece != null) {
					try {
						MD5CheckPiece(pePiece, false);
						final String[] writers =pePiece.getWriters();
						final List uniqueWriters =new ArrayList();
						final int[] writesPerWriter =new int[writers.length];
						for (int i =0; i <writers.length; i++) {
							final String writer =writers[i];
							if (writer !=null) {
								int writerId = uniqueWriters.indexOf(writer);
								if (writerId ==-1) {
									uniqueWriters.add(writer);
									writerId = uniqueWriters.size() - 1;
								}
								writesPerWriter[writerId]++;
							}
						}
						
						final int nbWriters =uniqueWriters.size();
						if (nbWriters ==1) {
							//Very simple case, only 1 peer contributed for that piece,
							//so, let's mark it as a bad peer
							String	bad_ip = (String)uniqueWriters.get(0);
							PEPeerTransport bad_peer = getTransportFromAddress(bad_ip);
							if (bad_peer != null) {
								bad_peer.sendBadPiece(pieceNumber);
							}
							badPeerDetected( bad_ip, pieceNumber);
							//and let's reset the whole piece
							pePiece.reset();
						} else if (nbWriters > 1) {
							int maxWrites = 0;
							String bestWriter = null;
							PEPeerTransport	bestWriterTransport = null;
							for (int i =0; i < uniqueWriters.size(); i++) {
								final int writes = writesPerWriter[i];
									if (writes > maxWrites) {
									final String writer = (String) uniqueWriters.get(i);
										PEPeerTransport pt = getTransportFromAddress(writer);
										if (	pt !=null &&
												pt.getReservedPieceNumbers() == null &&
												!ipFilter.isInRange(writer, getDisplayName(),getTorrentHash())) {
										bestWriter = writer;
										maxWrites = writes;
											bestWriterTransport	= pt;
									}
								}
							}
							if (bestWriter !=null) {
								pePiece.setReservedBy(bestWriter);
								bestWriterTransport.addReservedPieceNumber(pePiece.getPieceNumber());
								pePiece.setRequestable();
								for (int i =0; i <pePiece.getNbBlocks(); i++) {
									// If the block was contributed by someone else
									if (writers[i] == null ||!writers[i].equals(bestWriter)) {
										pePiece.reDownloadBlock(i);
									}
								}
							} else {
								//In all cases, reset the piece
								pePiece.reset();
							}
						} else {
							//In all cases, reset the piece
							pePiece.reset();
						}
	
						//if we are in end-game mode, we need to re-add all the piece chunks
						//to the list of chunks needing to be downloaded
						piecePicker.addEndGameChunks(pePiece);
						_stats.hashFailed(pePiece.getLength());
					} catch (Throwable e) {
						Debug.printStackTrace(e);
						// anything craps out in the above code, ensure we tidy up
						pePiece.reset();
					}
				} else {
					// no active piece for some reason, clear down DM piece anyway
					// one reason for getting here is that blocks are being injected directly from another
					// download with the same file (well, turns out it isn't the same file if getting hash fails);
					// Debug.out(getDisplayName() + ": Piece #" +pieceNumber +" failed check and no active piece, resetting...");
					dmPieces[pieceNumber].reset();
				}
			} else {
				// cancelled, download stopped
			}
		} finally {
			if (checkType == CHECK_REASON_SCAN) {
				rescanPieceTime	= SystemTime.getCurrentTime();
			}
			if (!seedingMode) {
				checkFinished(false);
			}
		}
	}


	private void badPeerDetected(String ip,  int pieceNumber) {
		boolean hashFail = pieceNumber >= 0;
		// note that peer can be NULL but things still work in the main
		PEPeerTransport peer = getTransportFromAddress(ip);
		if (hashFail && peer != null) {
			Iterator<PEPeerManagerListener> it = peerManagerListenersCow.iterator();
			while (it.hasNext()) {
				try {
					it.next().peerSentBadData(this, peer, pieceNumber);
				} catch (Throwable e) {
					Debug.printStackTrace(e);
				}
			}
		}
		// Debug.out("Bad Peer Detected: " + peerIP + " [" + peer.getClient() + "]");
		IpFilterManager filter_manager =IpFilterManagerFactory.getSingleton();
		
		//Ban fist to avoid a fast reco of the bad peer
		int nbWarnings = filter_manager.getBadIps().addWarningForIp(ip);
		boolean	disconnecPpeer = false;
		
		// no need to reset the bad chunk count as the peer is going to be disconnected and
		// if it comes back it'll start afresh
		// warning limit only applies to hash-fails, discards cause immediate action
		if (nbWarnings > WARNINGS_LIMIT) {
			if (COConfigurationManager.getBooleanParameter("Ip Filter Enable Banning")) {
				// if a block-ban occurred, check other connections
				if (ipFilter.ban(ip, getDisplayName(), false )) {
					checkForBannedConnections();
				}
				// Trace the ban
				if (Logger.isEnabled()) {
					Logger.log(new LogEvent(peer, LOGID, LogEvent.LT_ERROR, ip +" : has been banned and won't be able "
						+"to connect until you restart azureus"));
				}
				disconnecPpeer = true;
			}
		} else if (!hashFail) {
			// for failures due to excessive discard we boot the peer anyway
			disconnecPpeer	= true;
		}
		if (disconnecPpeer) {
			if (peer != null) {
				final int ps =peer.getPeerState();
				// might have been through here very recently and already started closing
				// the peer (due to multiple bad blocks being found from same peer when checking piece)
				if (!(ps ==PEPeer.CLOSING ||ps ==PEPeer.DISCONNECTED)) {
					//	Close connection
					closeAndRemovePeer(peer, "has sent too many " + (hashFail?"bad pieces":"discarded blocks") + ", " +WARNINGS_LIMIT +" max.", true);
				}
			}
		}
	}

	public PEPiece[] getPieces() {
		return pePieces;
	}

	public PEPiece getPiece(int pieceNumber) {
		return pePieces[pieceNumber];
	}

	public PEPeerStats createPeerStats(PEPeer owner) {
		return (new PEPeerStatsImpl(owner));
	}


	public DiskManagerReadRequest createDiskManagerRequest(
			int pieceNumber,
			int offset,
			int length) {
		return (diskMgr.createReadRequest(pieceNumber, offset, length));
	}

	public boolean requestExists(
			String			peerIp,
			int				pieceNumber,
			int				offset,
			int				length) {
		List<PEPeerTransport> peerTransports = peerTransportsCow;
		DiskManagerReadRequest	request = null;
		for (int i=0; i < peerTransports.size(); i++) {
			PEPeerTransport conn = peerTransports.get(i);
			if (conn.getIp().equals(peerIp)) {
				if (request == null) {
					request = createDiskManagerRequest(pieceNumber, offset, length);
				}
				if (conn.getRequestIndex(request) != -1) {
					return (true);
				}
			}
		}
		return (false);
	}

	public boolean seedPieceRecheck() {
		if (!( enableSeedingPieceRechecks || isSeeding())) {
			return (false);
		}
		int	max_reads 		= 0;
		int	max_reads_index = 0;
		if (TEST_PERIODIC_SEEDING_SCAN_FAIL_HANDLING) {
			max_reads_index = (int)(Math.random()*dmPieces.length);;
			max_reads = dmPieces[ max_reads_index ].getNbBlocks()*3;
		} else {
			for (int i=0;i<dmPieces.length;i++) {
				// skip dnd pieces
				DiskManagerPiece	dm_piece = dmPieces[i];
				if (!dm_piece.isDone()) {
					continue;
				}
				int	num = dm_piece.getReadCount()&0xffff;
				if (num > SEED_CHECK_WAIT_MARKER) {
					// recently been checked, skip for a while
					num--;
					if (num == SEED_CHECK_WAIT_MARKER) {
						num = 0;
					}
					dm_piece.setReadCount((short)num);
				} else {
					if (num > max_reads) {
						max_reads 		= num;
						max_reads_index = i;
					}
				}
			}
		}
		if (max_reads > 0) {
			DiskManagerPiece	dm_piece = dmPieces[ max_reads_index ];
			// if the piece has been read 3 times (well, assuming each block is read once,
			// which is obviously wrong, but...)
			if (max_reads >= dm_piece.getNbBlocks() * 3) {
				DiskManagerCheckRequest	req = diskMgr.createCheckRequest(max_reads_index, new Integer( CHECK_REASON_SEEDING_CHECK ));
				req.setAdHoc(true);
				req.setLowPriority(true);
				if (Logger.isEnabled())
					Logger.log(new LogEvent(diskMgr.getTorrent(), LOGID,
							"Rechecking piece " + max_reads_index + " while seeding as most active"));
				diskMgr.enqueueCheckRequest(req, this);
				dm_piece.setReadCount((short)65535);
				// clear out existing, non delayed pieces so we start counting piece activity
				// again
				for (int i=0;i<dmPieces.length;i++) {
					if (i != max_reads_index) {
						int	num = dmPieces[i].getReadCount()&0xffff;
						if (num < SEED_CHECK_WAIT_MARKER) {
							dmPieces[i].setReadCount((short)0);
						}
					}
				}
				return (true);
			}
		}
		return (false);
	}

	public void addListener(PEPeerManagerListener l) {
		try {
			thisMon.enter();
			//copy on write
			final ArrayList peer_manager_listeners = new ArrayList(peerManagerListenersCow.size() + 1);
			peer_manager_listeners.addAll(peerManagerListenersCow);
			peer_manager_listeners.add(l);
			peerManagerListenersCow = peer_manager_listeners;
		} finally {
			thisMon.exit();
		}
	}

	public void removeListener(PEPeerManagerListener l) {
		try {
			thisMon.enter();

			//copy on write
			final ArrayList peer_manager_listeners = new ArrayList(peerManagerListenersCow);
			peer_manager_listeners.remove(l);
			peerManagerListenersCow = peer_manager_listeners;
		} finally {
			thisMon.exit();
		}
	}

	private void checkForBannedConnections() {
		if (ipFilter.isEnabled()) {  //if ipfiltering is enabled, remove any existing filtered connections
			List<PEPeerTransport> to_close = null;

			final List<PEPeerTransport>	peer_transports = peerTransportsCow;

			String name 	= getDisplayName();
			byte[]	hash	= getTorrentHash();

			for (int i=0; i < peer_transports.size(); i++) {
				final PEPeerTransport conn = peer_transports.get(i);

				if (ipFilter.isInRange( conn.getIp(), name, hash)) {
					if (to_close == null)  to_close = new ArrayList();
					to_close.add(conn);
				}
			}

			if (to_close != null) {
				for (int i=0; i < to_close.size(); i++) {
					closeAndRemovePeer(to_close.get(i), "IPFilter banned IP address", true);
				}
			}
		}
	}

	public boolean isSeeding() {
		return (seedingMode);
	}

	public boolean isMetadataDownload() {
		return (isMetadataDownload);
	}

	public int getTorrentInfoDictSize() {
		return (metadata_infodict_size);
	}

	public void setTorrentInfoDictSize(
		int		size) {
		metadata_infodict_size	= size;
	}

	public boolean isInEndGameMode() {
		return piecePicker.isInEndGameMode();
	}

	public boolean isSuperSeedMode() {
		return superSeedMode;
	}

	public boolean canToggleSuperSeedMode() {
		if (superSeedMode) {

			return (true);
		}

		return (superSeedPieces == null && this.getRemaining() ==0);
	}

	public void setSuperSeedMode(
			boolean _superSeedMode) {
		if (_superSeedMode == superSeedMode) {

			return;
		}

		boolean	kick_peers = false;

		if (_superSeedMode) {

			if (superSeedPieces == null && this.getRemaining() ==0) {

				superSeedMode = true;

				initialiseSuperSeedMode();

				kick_peers	= true;
			}
		} else {

			superSeedMode = false;

			kick_peers	= true;
		}

		if (kick_peers) {

			// turning on/off super-seeding, gotta kick all connected peers so they get the
			// "right" bitfield

			List<PEPeerTransport> peer_transports = peerTransportsCow;

			for (int i=0; i < peer_transports.size(); i++) {

				PEPeerTransport conn = peer_transports.get(i);

				closeAndRemovePeer(conn, "Turning on super-seeding", false);
			}
		}
	}

	private void initialiseSuperSeedMode() {
		superSeedPieces = new SuperSeedPiece[_nbPieces];
		for (int i = 0 ; i < _nbPieces ; i++) {
			superSeedPieces[i] = new SuperSeedPiece(this,i);
		}
	}

	private void updatePeersInSuperSeedMode() {
		if (!superSeedMode) {
			return;
		}

		//Refresh the update time in case this is needed
		for (int i = 0 ; i < superSeedPieces.length ; i++) {
			superSeedPieces[i].updateTime();
		}

		//Use the same number of announces than unchoke
		int nbUnchoke = adapter.getMaxUploads();
		if (superSeedModeNumberOfAnnounces >= 2 * nbUnchoke)
			return;


		//Find an available Peer
		PEPeerTransport selectedPeer = null;
		List<SuperSeedPeer> sortedPeers = null;

		final List<PEPeerTransport>	peer_transports = peerTransportsCow;

		sortedPeers = new ArrayList<SuperSeedPeer>(peer_transports.size());
		Iterator<PEPeerTransport> iter1 = peer_transports.iterator();
		while (iter1.hasNext()) {
			sortedPeers.add(new SuperSeedPeer(iter1.next()));
		}

		Collections.sort(sortedPeers);
		Iterator<SuperSeedPeer> iter2 = sortedPeers.iterator();
		while (iter2.hasNext()) {
			final PEPeerTransport peer = ((SuperSeedPeer)iter2.next()).peer;
			if ((peer.getUniqueAnnounce() == -1) && (peer.getPeerState() == PEPeer.TRANSFERING)) {
				selectedPeer = peer;
				break;
			}
		}

		if (selectedPeer == null ||selectedPeer.getPeerState() >=PEPeer.CLOSING)
			return;

		if (selectedPeer.getUploadHint() == 0) {
			//Set to infinite
			selectedPeer.setUploadHint(Constants.CRAPPY_INFINITY_AS_INT);
		}

		//Find a piece
		boolean found = false;
		SuperSeedPiece piece = null;
		boolean loopdone = false;  // add loop status

		while (!found) {
			piece = superSeedPieces[superSeedModeCurrentPiece];
			if (piece.getLevel() > 0) {
				piece = null;
				superSeedModeCurrentPiece++;
				if (superSeedModeCurrentPiece >= _nbPieces) {
					superSeedModeCurrentPiece = 0;

					if (loopdone) {  // if already been here, has been full loop through pieces, quit
						//quit superseed mode
						superSeedMode = false;
						closeAndRemoveAllPeers("quiting SuperSeed mode", true);
						return;
					}
					else {
						// loopdone==false --> first time here --> go through the pieces
						// for a second time to check if reserved pieces have got freed due to peers leaving
						loopdone = true;
					}
				}
			} else {
				found = true;
			}
		}

		if (piece == null) {
			return;
		}

		//If this peer already has this piece, return (shouldn't happen)
		if (selectedPeer.isPieceAvailable(piece.getPieceNumber())) {
			return;
		}

		selectedPeer.setUniqueAnnounce(piece.getPieceNumber());
		superSeedModeNumberOfAnnounces++;
		piece.pieceRevealedToPeer();
		selectedPeer.sendHave(piece.getPieceNumber());
	}

	public void updateSuperSeedPiece(PEPeer peer,int pieceNumber) {
		// currently this gets only called from bitfield scan function in PEPeerTransportProtocol
		if (!superSeedMode)
			return;
		superSeedPieces[pieceNumber].peerHasPiece(null);
		if (peer.getUniqueAnnounce() == pieceNumber) {
			peer.setUniqueAnnounce(-1);
			superSeedModeNumberOfAnnounces--;
		}
	}

	public boolean isPrivateTorrent() {
		return (isPrivateTorrent);
	}

	public int getExtendedMessagingMode() {
		return ( adapter.getExtendedMessagingMode());
	}

	public boolean isPeerExchangeEnabled() {
		return ( adapter.isPeerExchangeEnabled());
	}

	public LimitedRateGroup getUploadLimitedRateGroup() {  return upload_limited_rate_group;  }

	public LimitedRateGroup getDownloadLimitedRateGroup() {  return download_limited_rate_group;  }


	/** To retreive arbitrary objects against this object. */
	public Object getData(
			String key) {
		try {
			thisMon.enter();

			if (userData == null) return null;

			return userData.get(key);

		} finally {

			thisMon.exit();
		}
	}

	/** To store arbitrary objects against a control. */

	public void setData(
			String key,
			Object value) {
		try {
			thisMon.enter();

			if (userData == null) {
				userData = new HashMap();
			}
			if (value == null) {
				if (userData.containsKey(key))
					userData.remove(key);
			} else {
				userData.put(key, value);
			}
		} finally {
			thisMon.exit();
		}
	}

	public int getConnectTimeout(
		int		ct_def) {
		if (ct_def <= 0) {

			return (ct_def);
		}

		if (seedingMode) {

				// seeding mode connections are already de-prioritised so nothing to do

			return (ct_def);
		}

		int max_sim_con = TCPConnectionManager.MAX_SIMULTANIOUS_CONNECT_ATTEMPTS;

			// high, let's not mess with things

		if (max_sim_con >= 50) {

			return (ct_def);
		}

			// we have somewhat limited outbound connection limits, see if it makes sense to
			// reduce the connect timeout to prevent connection stall due to a bunch getting
			// stuck 'connecting' for a long time and stalling us

		int	connected			= _seeds + _peers;
		int	connecting			= _tcpConnectingConnections;
		int queued				= _tcpPendingConnections;

		int	not_yet_connected 	= peerDatabase.getDiscoveredPeerCount();

		int	max = getMaxConnections("");

		int	potential = connecting + queued + not_yet_connected;

		/*
		System.out.println(
				"connected=" + connected +
				", queued=" + queued +
				", connecting=" + connecting +
				", queued=" + queued +
				", not_yet=" + not_yet_connected +
				", max=" + max);
		*/

			// not many peers -> don't amend

		int	lower_limit = max/4;

		if (potential <= lower_limit || max == lower_limit) {

			return (ct_def);
		}

			// if we got lots of potential, use minimum delay

		final int MIN_CT = 7500;

		if (potential >= max) {

			return (MIN_CT);
		}

			// scale between MIN and ct_def

		int pos 	= potential - lower_limit;
		int	scale 	= max - lower_limit;

		int res =  MIN_CT + (ct_def - MIN_CT )*(scale - pos)/scale;

		// System.out.println("scaled->" + res);

		return (res);
	}

	private void doConnectionChecks() {
		
		// if mixed networks then we have potentially two connections limits
		// 1) general peer one  - e.g. 100
		// 2) general+reserved slots for non-public net  - e.g. 103
		// so we get to schedule 3 'extra' non-public connections
		
		//every 1 second
		if (mainloopLoopCount % MAINLOOP_ONE_SECOND_INTERVAL == 0) {
			final List<PEPeerTransport> peerTransports = peerTransportsCow;
			int numWaitingEstablishments = 0;
			int udpConnections = 0;
			for (int i=0; i < peerTransports.size(); i++) {
				final PEPeerTransport transport = peerTransports.get(i);
				//update waiting count
				final int state = transport.getConnectionState();
				if (state == PEPeerTransport.CONNECTION_PENDING || state == PEPeerTransport.CONNECTION_CONNECTING) {
					numWaitingEstablishments++;
				}
				if (!transport.isTCP()) {
					udpConnections++;
				}
			}
			
			int[]	allowedSeedsInfo = getMaxSeedConnections();
			int		baseAllowedSeeds = allowedSeedsInfo[0];
			// base_allowed_seeds == 0 -> no limit
			if (baseAllowedSeeds > 0) {
				int	extraSeeds = allowedSeedsInfo[1];
				int	toDisconnect = _seeds - baseAllowedSeeds;
				if (toDisconnect > 0) {
					// seeds are limited by people trying to get a reasonable upload by connecting
					// to leechers where possible. disconnect seeds from end of list to prevent
					// cycling of seeds
					Set<PEPeerTransport> toRetain = new HashSet<PEPeerTransport>();
					if (extraSeeds > 0) {
						// we can have up to extra_seeds additional non-public seeds on top of base
						for (PEPeerTransport transport: peerTransports) {
							if (transport.isSeed() && transport.getNetwork() != AENetworkClassifier.AT_PUBLIC) {
								toRetain.add(transport);
								if (toRetain.size() == extraSeeds) {
									break;
								}
							}
						}
						toDisconnect -= toRetain.size();
					}
					for (int i=peerTransports.size()-1; i >= 0 && toDisconnect > 0; i--) {
						final PEPeerTransport transport = peerTransports.get(i);
						if (transport.isSeed()) {
							if (!toRetain.contains( transport)) {
								closeAndRemovePeer(transport, "Too many seeds", false);
								toDisconnect--;
							}
						}
					}
				}
			}
			int[] allowedInfo = getMaxNewConnectionsAllowed();
			int	allowedBase = allowedInfo[0];
			
			// allowed_base < 0 -> unlimited
			if (allowedBase < 0 || allowedBase > 1000) {
				allowedBase = 1000;  //ensure a very upper limit so it doesnt get out of control when using PEX
				allowedInfo[0] = allowedBase;
			}
			
			if (adapter.isNATHealthy()) {  //if unfirewalled, leave slots avail for remote connections
				int free = getMaxConnections()[0] / 20;  //leave 5%
				allowedBase = allowedBase - free;
				allowedInfo[0] = allowedBase;
			}
			
			for (int i=0;i<allowedInfo.length;i++) {
				int	allowed = allowedInfo[i];
				if (allowed > 0) {
					//try and connect only as many as necessary
					final int wanted = TCPConnectionManager.MAX_SIMULTANIOUS_CONNECT_ATTEMPTS - numWaitingEstablishments;
					if (wanted > allowed) {
						numWaitingEstablishments += wanted - allowed;
					}
					int	remaining = allowed;
					int	tcpRemaining = TCPNetworkManager.getSingleton().getConnectDisconnectManager().getMaxOutboundPermitted();
					int	udpRemaining = UDPNetworkManager.getSingleton().getConnectionManager().getMaxOutboundPermitted();
					
					while (numWaitingEstablishments < TCPConnectionManager.MAX_SIMULTANIOUS_CONNECT_ATTEMPTS && (tcpRemaining > 0 || udpRemaining > 0)) {
						
						if (!isRunning)  break;
						final PeerItem item = peerDatabase.getNextOptimisticConnectPeer(i == 1);
						if (item == null || !isRunning)  break;
						final PeerItem self = peerDatabase.getSelfPeer();
						if (self != null && self.equals( item )) {
							continue;
						}
						
						if (!isAlreadyConnected(item)) {
							final String source = PeerItem.convertSourceString(item.getSource());
							final boolean useCrypto = item.getHandshakeType() == PeerItemFactory.HANDSHAKE_TYPE_CRYPTO;
							int	tcpPort = item.getTCPPort();
							int	udpPort = item.getUDPPort();
							
							/*if (SingleCounter.getInstance().getAndIncreaseCount() < 1) {
								Log.d(TAG, "tcpPort = " + tcpPort);
								Log.d(TAG, "udpPort = " + udpPort);
							}*/
							
							if (udpPort == 0 && udpProbeEnabled) {
								// for probing we assume udp port same as tcp
								udpPort = tcpPort;
							}
							
							boolean	preferUdpOverall = preferUdp || preferUdpDefault;
							if (preferUdpOverall && udpPort == 0) {
								// see if we have previous record of this address as udp connectable
								byte[]	address = item.getIP().getBytes();
								BloomFilter	bloom = preferUdpBloom;
								if (bloom != null && bloom.contains(address)) {
									udpPort = tcpPort;
								}
							}
							boolean	tcpOk = TCPNetworkManager.TCP_OUTGOING_ENABLED && tcpPort > 0 && tcpRemaining > 0;
							boolean udpOk = UDPNetworkManager.UDP_OUTGOING_ENABLED && udpPort > 0 && udpRemaining > 0;
							if (tcpOk && !(preferUdpOverall && udpOk)) {
								if (makeNewOutgoingConnection(source, item.getAddressString(), tcpPort, udpPort, true, useCrypto, item.getCryptoLevel(), null) == null) {
									tcpRemaining--;
									numWaitingEstablishments++;
									remaining--;
								}
							} else if (udpOk) {
								if (makeNewOutgoingConnection(source, item.getAddressString(), tcpPort, udpPort, false, useCrypto, item.getCryptoLevel(), null) == null) {
									udpRemaining--;
									numWaitingEstablishments++;
									remaining--;
								}
							}
						}
					}
					if (i == 0) {
						if (	UDPNetworkManager.UDP_OUTGOING_ENABLED &&
								remaining > 0 &&
								udpRemaining > 0 &&
								udpConnections < MAX_UDP_CONNECTIONS) {
							doUDPConnectionChecks(remaining);
						}
					}
				}
			}
		}
		
		//every 5 seconds
		if (mainloopLoopCount % MAINLOOP_FIVE_SECOND_INTERVAL == 0) {
			final List<PEPeerTransport> peer_transports = peerTransportsCow;
			for (int i=0; i < peer_transports.size(); i++) {
				final PEPeerTransport transport = peer_transports.get(i);
				//check for timeouts
				if (transport.doTimeoutChecks())  continue;
				//keep-alive check
				transport.doKeepAliveCheck();
				//speed tuning check
				transport.doPerformanceTuningCheck();
			}
		}
		// every 10 seconds check for connected + banned peers
		if (mainloopLoopCount % MAINLOOP_TEN_SECOND_INTERVAL == 0) {
			final long	last_update = ipFilter.getLastUpdateTime();
			if (last_update != ipFilterLastUpdateTime) {
				ipFilterLastUpdateTime	= last_update;
				checkForBannedConnections();
			}
		}
		//every 30 seconds
		if (mainloopLoopCount % MAINLOOP_THIRTY_SECOND_INTERVAL == 0) {
			//if we're at our connection limit, time out the least-useful
			//one so we can establish a possibly-better new connection
			optimisticDisconnectCount = 0;
			int[] allowed = getMaxNewConnectionsAllowed();
			if (allowed[0] + allowed[1] == 0) {  //we've reached limit
				doOptimisticDisconnect(false, false, "");
			}
		}

		//sweep over all peers in a 60 second timespan
		float percentage = ((mainloopLoopCount % MAINLOOP_SIXTY_SECOND_INTERVAL) + 1F) / (1F *MAINLOOP_SIXTY_SECOND_INTERVAL);
		int goal;
		if (mainloopLoopCount % MAINLOOP_SIXTY_SECOND_INTERVAL == 0) {
			goal = 0;
			sweepList = peerTransportsCow;
		} else
			goal = (int)Math.floor(percentage * sweepList.size());
		for ( int i=nextPEXSweepIndex; i < goal && i < sweepList.size(); i++) {
			//System.out.println(mainloop_loop_count+" %:"+percentage+" start:"+nextPEXSweepIndex+" current:"+i+" <"+goal+"/"+sweepList.size());
			final PEPeerTransport peer = sweepList.get(i);
			peer.updatePeerExchange();
		}
		nextPEXSweepIndex = goal;
		
		// kick duplicate outbound connections - some users experience increasing numbers of connections to the same IP address sitting there in a 'connecting' state
		// not sure of the exact cause unfortunately so adding this as a stop gap measure (parg, 2015/01/11)
		// I have a suspicion this is caused by an outbound connection failing (possibly with TCP+uTP dual connects) and not resulting in overall disconnect
		// as the user has a bunch of 'bind' exceptions in their log
		if (mainloopLoopCount % MAINLOOP_SIXTY_SECOND_INTERVAL == 0) {
			List<PEPeerTransport> peer_transports = peerTransportsCow;
			if (peer_transports.size() > 1) {
				Map<String,List<PEPeerTransport>>	peer_map = new HashMap<String, List<PEPeerTransport>>();
				for (PEPeerTransport peer: peer_transports) {
					if (peer.isIncoming()) {
						continue;
					}
					if (	peer.getPeerState() == PEPeer.CONNECTING &&
							peer.getConnectionState() == PEPeerTransport.CONNECTION_CONNECTING &&
							peer.getLastMessageSentTime() != 0) {
						String key = peer.getIp() + ":" + peer.getPort();
						List<PEPeerTransport> list = peer_map.get(key);
						if (list == null) {
							list = new ArrayList<PEPeerTransport>(1);
							peer_map.put(key, list);
						}
						list.add(peer);
					}
				}
				for ( List<PEPeerTransport> list: peer_map.values()) {
					if (list.size() >= 2) {
						long			newest_time = Long.MIN_VALUE;
						PEPeerTransport	newest_peer	= null;
						for (PEPeerTransport peer: list) {
							long	last_sent = peer.getLastMessageSentTime();
							if (last_sent > newest_time) {
								newest_time = last_sent;
								newest_peer	= peer;
							}
						}
						for (PEPeerTransport peer: list) {
							if (peer != newest_peer) {
								if (	peer.getPeerState() == PEPeer.CONNECTING &&
										peer.getConnectionState() == PEPeerTransport.CONNECTION_CONNECTING) {
									closeAndRemovePeer(peer, "Removing old duplicate connection", false);
								}
							}
						}
					}
				}
			}
		}
	}

	private void doUDPConnectionChecks(int number) {
		List<PEPeerTransport>	newConnections = null;
		try {
			peerTransportsMon.enter();
			long	now = SystemTime.getCurrentTime();
			if (udpReconnects.size() > 0 && now - lastUdpReconnect >= UDP_RECONNECT_MIN_MILLIS) {
				lastUdpReconnect = now;
				Iterator<PEPeerTransport> it = udpReconnects.values().iterator();
				PEPeerTransport	peer = it.next();
				it.remove();
				if (Logger.isEnabled()) {
					Logger.log(new LogEvent(this, LOGID, LogEvent.LT_INFORMATION, "Reconnecting to previous failed peer " + peer.getPeerItemIdentity().getAddressString()));
				}
				if (newConnections == null) {
					newConnections = new ArrayList<PEPeerTransport>();
				}
				newConnections.add(peer);
				number--;
				if (number <= 0) {
					return;
				}
			}
			if (pendingNatTraversals.size() == 0) {
				return;
			}
			int max = MAX_UDP_TRAVERSAL_COUNT;
			// bigger the swarm, less chance of doing it
			if (seedingMode) {
				if (_peers > 8) {
					max = 0;
				} else {
					max = 1;
				}
			} else if (_seeds > 8) {
				max = 0;
			} else if (_seeds > 4) {
				max = 1;
			}
			int	avail = max - udpTraversalCount;
			int	to_do = Math.min(number, avail);
			Iterator<PEPeerTransport>	it = pendingNatTraversals.values().iterator();
			while (to_do > 0 && it.hasNext()) {
				final PEPeerTransport	peer = it.next();
				it.remove();
				String peer_ip = peer.getPeerItemIdentity().getAddressString();
				if (AENetworkClassifier.categoriseAddress(peer_ip) != AENetworkClassifier.AT_PUBLIC) {
					continue;
				}
				to_do--;
				PeerNATTraverser.getSingleton().create(
						this,
						new InetSocketAddress(peer_ip, peer.getPeerItemIdentity().getUDPPort()),
						new PeerNATTraversalAdapter() {
							private boolean	done;
							public void success(
									InetSocketAddress	target) {
								complete();
								PEPeerTransport newTransport = peer.reconnect(true, false);
								if (newTransport != null) {
									newTransport.setData(PEER_NAT_TRAVERSE_DONE_KEY, "");
								}
							}
							
							public void failed() {
								complete();
							}
							
							protected void complete() {
								try {
									peerTransportsMon.enter();
									if (!done) {
										done = true;
										udpTraversalCount--;
									}
								} finally {
									peerTransportsMon.exit();
								}
							}
						});
				udpTraversalCount++;
			}
		} finally {
			peerTransportsMon.exit();
			if (newConnections != null) {
				for (int i=0;i<newConnections.size();i++) {
					PEPeerTransport	peer_item = newConnections.get(i);
						// don't call when holding monitor - deadlock potential
					peer_item.reconnect(true, false);
				}
			}
		}
	}

	// counter is reset every 30s by doConnectionChecks()
	private int optimisticDisconnectCount = 0;

	public boolean doOptimisticDisconnect(
		boolean		pending_lan_local_peer,
		boolean 	force,
		String 		network )	// on behalf of a particular peer OR "" for general
	{
		// if it isn't for non-pub network then try to maintain the extra connection slots, if any, allocated
		// to non-pub

		final int	non_pub_extra;

		if (network != AENetworkClassifier.AT_I2P) {

			int[] max_con = getMaxConnections();

			non_pub_extra = max_con[1];

		} else {

			non_pub_extra = 0;
		}

		final List<PEPeerTransport> peer_transports = peerTransportsCow;

		PEPeerTransport max_transport 			= null;
		PEPeerTransport max_seed_transport		= null;
		PEPeerTransport max_non_lan_transport 	= null;

		PEPeerTransport max_pub_transport 			= null;
		PEPeerTransport max_pub_seed_transport		= null;
		PEPeerTransport max_pub_non_lan_transport 	= null;

		long max_time = 0;
		long max_seed_time 		= 0;
		long max_non_lan_time	= 0;
		long max_pub_time		= 0;
		long max_pub_seed_time 		= 0;
		long max_pub_non_lan_time	= 0;

		int	non_pub_found = 0;

		List<Long> activeConnectionTimes = new ArrayList<Long>(peer_transports.size());

		int	lan_peer_count	= 0;

		for (int i=0; i < peer_transports.size(); i++) {

			final PEPeerTransport peer = peer_transports.get(i);

			if (peer.getConnectionState() == PEPeerTransport.CONNECTION_FULLY_ESTABLISHED) {

				final long timeSinceConnection =peer.getTimeSinceConnectionEstablished();
				final long timeSinceSentData =peer.getTimeSinceLastDataMessageSent();

				activeConnectionTimes.add(timeSinceConnection);

				long peerTestTime = 0;
				if (seedingMode) {
					if (timeSinceSentData != -1)
						peerTestTime = timeSinceSentData;  //ensure we've sent them at least one data message to qualify for drop
				} else {
					final long timeSinceGoodData =peer.getTimeSinceGoodDataReceived();


					if (timeSinceGoodData == -1)
						peerTestTime +=timeSinceConnection;   //never received
					else
						peerTestTime +=timeSinceGoodData;

					// try to drop unInteresting in favor of Interesting connections
					if (!peer.isInteresting()) {
						if (!peer.isInterested())   // if mutually unInterested, really try to drop the connection
							peerTestTime +=timeSinceConnection +timeSinceSentData;   // if we never sent, it will subtract 1, which is good
						else
							peerTestTime +=(timeSinceConnection -timeSinceSentData); // try to give interested peers a chance to get data

						peerTestTime *=2;
					}

					peerTestTime +=peer.getSnubbedTime();
				}

				if (!peer.isIncoming()) {
					peerTestTime = peerTestTime * 2;   //prefer to drop a local connection, to make room for more remotes
				}

				boolean	count_pubs = non_pub_extra > 0 && peer.getNetwork() == AENetworkClassifier.AT_PUBLIC;

				if (peer.isLANLocal()) {

					lan_peer_count++;

				} else {

					if (peerTestTime > max_non_lan_time) {

						max_non_lan_time 		= peerTestTime;
						max_non_lan_transport 	= peer;
					}

					if (count_pubs) {

						if (peerTestTime > max_pub_non_lan_time) {

							max_pub_non_lan_time 		= peerTestTime;
							max_pub_non_lan_transport 	= peer;
						}
					}
				}

					// anti-leech checks

				if (!seedingMode) {

					// remove long-term snubbed peers with higher probability
					peerTestTime += peer.getSnubbedTime();
					if (peer.getSnubbedTime() > 2*60) {peerTestTime*=1.5;}

					PEPeerStats pestats = peer.getStats();
					// everybody has deserverd a chance of half an MB transferred data
					if (pestats.getTotalDataBytesReceived()+pestats.getTotalDataBytesSent() > 1024*512) {
						boolean goodPeer = true;

						// we don't like snubbed peers with a negative gain
						if (peer.isSnubbed() && pestats.getTotalDataBytesReceived() < pestats.getTotalDataBytesSent()) {
							peerTestTime *= 1.5;
							goodPeer = false;
						}
						// we don't like peers with a very bad ratio (10:1)
						if (pestats.getTotalDataBytesSent() > pestats.getTotalDataBytesReceived() * 10) {
							peerTestTime *= 2;
							goodPeer = false;
						}
						// modify based on discarded : overall downloaded ratio
						if (pestats.getTotalDataBytesReceived() > 0 && pestats.getTotalBytesDiscarded() > 0) {
							peerTestTime = (long)(peerTestTime *( 1.0+((double)pestats.getTotalBytesDiscarded()/(double)pestats.getTotalDataBytesReceived())));
						}

						// prefer peers that do some work, let the churn happen with peers that did nothing
						if (goodPeer)
							peerTestTime *= 0.7;
					}
				}


				if (peerTestTime > max_time) {

					max_time 		= peerTestTime;
					max_transport 	= peer;
				}

				if (count_pubs) {

					if (peerTestTime > max_pub_time) {

						max_pub_time 		= peerTestTime;
						max_pub_transport 	= peer;
					}
				} else {

					non_pub_found++;
				}

				if (peer.isSeed() || peer.isRelativeSeed()) {

					if (peerTestTime > max_seed_time) {

						max_seed_time 		= peerTestTime;
						max_seed_transport 	= peer;
					}

					if (count_pubs) {

						if (peerTestTime > max_pub_seed_time) {

							max_pub_seed_time 		= peerTestTime;
							max_pub_seed_transport 	= peer;
						}
					}
				}
			}
		}

		if (non_pub_extra > 0) {

			if (non_pub_found <= non_pub_extra) {

					// don't kick a non-pub peer

				if (max_transport != null && max_transport.getNetwork() != AENetworkClassifier.AT_PUBLIC) {

					max_time		= max_pub_time;
					max_transport 	= max_pub_transport;
				}

				if (max_seed_transport != null && max_seed_transport.getNetwork() != AENetworkClassifier.AT_PUBLIC) {

					max_seed_time		= max_pub_seed_time;
					max_seed_transport	= max_pub_seed_transport;
				}

				if (max_non_lan_transport != null && max_non_lan_transport.getNetwork() != AENetworkClassifier.AT_PUBLIC) {

					max_non_lan_time		= max_pub_non_lan_time;
					max_non_lan_transport 	= max_pub_non_lan_transport;
				}
			}
		}

		long medianConnectionTime;

		if (activeConnectionTimes.size() > 0) {
			Collections.sort(activeConnectionTimes);
			medianConnectionTime = activeConnectionTimes.get(activeConnectionTimes.size()/2);
		} else {
			medianConnectionTime = 0;
		}

		int max_con = getMaxConnections(network);

		// allow 1 disconnect every 30s per 30 peers; 2 at least every 30s
		int maxOptimistics = max_con==0?8:Math.max(max_con/30,2);

		// avoid unnecessary churn, e.g.
		if (!pending_lan_local_peer && !force && optimisticDisconnectCount >= maxOptimistics && medianConnectionTime < 5*60*1000)
			return false;


		// don't boot lan peers if we can help it (unless we have a few of them)

		if (max_transport != null) {

			final int LAN_PEER_MAX	= 4;

			if (max_transport.isLANLocal() && lan_peer_count < LAN_PEER_MAX && max_non_lan_transport != null) {

				// override lan local max with non-lan local max

				max_transport	= max_non_lan_transport;
				max_time		= max_non_lan_time;
			}

			// if we have a seed limit, kick seeds in preference to non-seeds

			if (getMaxSeedConnections( network ) > 0 && max_seed_transport != null && max_time > 5*60*1000) {
				closeAndRemovePeer(max_seed_transport, "timed out by doOptimisticDisconnect()", true);
				optimisticDisconnectCount++;
				return true;
			}

			if (max_transport != null && max_time > 5 *60*1000) {  //ensure a 5 min minimum test time
				closeAndRemovePeer(max_transport, "timed out by doOptimisticDisconnect()", true);
				optimisticDisconnectCount++;
				return true;
			}

			// kick worst peers to accomodate lan peer

			if (pending_lan_local_peer && lan_peer_count < LAN_PEER_MAX) {
				closeAndRemovePeer(max_transport, "making space for LAN peer in doOptimisticDisconnect()", true);
				optimisticDisconnectCount++;
				return true;
			}

			if (force) {

				closeAndRemovePeer(max_transport, "force removal of worst peer in doOptimisticDisconnect()", true);

				return true;
			}
		} else if (force) {

			if (peer_transports.size() > 0) {

				PEPeerTransport pt = peer_transports.get(new Random().nextInt( peer_transports.size()));

				closeAndRemovePeer(pt, "force removal of random peer in doOptimisticDisconnect()", true);

				return true;
			}
		}

		return false;
	}


	public PeerExchangerItem createPeerExchangeConnection(final PEPeerTransport base_peer) {
		if (base_peer.getTCPListenPort() > 0) {  //only accept peers whose remote port is known
			final PeerItem peer =
				PeerItemFactory.createPeerItem( base_peer.getIp(),
						base_peer.getTCPListenPort(),
						PeerItemFactory.PEER_SOURCE_PEER_EXCHANGE,
						base_peer.getPeerItemIdentity().getHandshakeType(),
						base_peer.getUDPListenPort(),
						PeerItemFactory.CRYPTO_LEVEL_1,
						0);

			return peerDatabase.registerPeerConnection( peer, new PeerExchangerItem.Helper() {
				public boolean isSeed() {
					return base_peer.isSeed();
				}
			});
		}

		return null;
	}



	private boolean isAlreadyConnected(PeerItem peer_id) {
		final List<PEPeerTransport> peer_transports = peerTransportsCow;
		for (int i=0; i < peer_transports.size(); i++) {
			final PEPeerTransport peer = peer_transports.get(i);
			if (peer.getPeerItemIdentity().equals( peer_id ))  return true;
		}
		return false;
	}


	public void peerVerifiedAsSelf(PEPeerTransport self) {
		if (self.getTCPListenPort() > 0) {  //only accept self if remote port is known
			final PeerItem peer = PeerItemFactory.createPeerItem( self.getIp(), self.getTCPListenPort(),
					PeerItem.convertSourceID(self.getPeerSource() ), self.getPeerItemIdentity().getHandshakeType(), self.getUDPListenPort(),PeerItemFactory.CRYPTO_LEVEL_CURRENT, 0);
			peerDatabase.setSelfPeer(peer);
		}
	}

	public void IPFilterEnabledChanged(
		boolean			is_enabled) {
		if (is_enabled) {

			checkForBannedConnections();
		}
	}

	public boolean canIPBeBanned(
			String ip ) {
		return true;
	}

	public boolean canIPBeBlocked(
		String	ip,
		byte[]	torrent_hash) {
		return true;
	}

	public void IPBlockedListChanged(IpFilter filter) {
		Iterator<PEPeerTransport>	it = peerTransportsCow.iterator();

		String	name 	= getDisplayName();
		byte[]	hash	= getTorrentHash();

		while (it.hasNext()) {
			try {
  			PEPeerTransport	peer = it.next();

  			if (filter.isInRange(peer.getIp(), name, hash )) {
  				peer.closeConnection("IP address blocked by filters");
  			}
			} catch (Exception e) {
			}
		}
	}

	public void IPBanned(BannedIp ip) {
		for (int i =0; i <_nbPieces; i++) {
			if (pePieces[i] !=null)
				pePieces[i].reDownloadBlocks(ip.getIp());
		}
	}

	public long getHiddenBytes() {
		if (hiddenPiece < 0) {

			return (0);
		}

		return ( dmPieces[hiddenPiece].getLength());
	}

	public int getHiddenPiece() {
		return (hiddenPiece);
	}

	public int getUploadPriority() {
		return ( adapter.getUploadPriority());
	}

	public int getAverageCompletionInThousandNotation() {
		final ArrayList peer_transports = peerTransportsCow;

		if (peer_transports !=null) {
			final long total =diskMgr.getTotalLength();

			final int my_completion = total == 0
			? 1000
					: (int) ((1000 * (total - diskMgr.getRemainingExcludingDND())) / total);

			int sum = my_completion == 1000 ? 0 : my_completion;  //add in our own percentage if not seeding
			int num = my_completion == 1000 ? 0 : 1;

			for (int i =0; i <peer_transports.size(); i++) {
				final PEPeer peer =(PEPeer) peer_transports.get(i);

				if (peer.getPeerState() ==PEPeer.TRANSFERING &&!peer.isSeed()) {
					num++;
					sum += peer.getPercentDoneInThousandNotation();
				}
			}

			return num > 0 ? sum / num : 0;
		}

		return -1;
	}

	public int[]
	getMaxConnections() {
		return ( adapter.getMaxConnections());
	}

	private int getMaxConnections(
		String		net) {
		int[]	data = getMaxConnections();

		int	result = data[0];

			// 0 = unlimited

		if (result > 0) {

			if (net != AENetworkClassifier.AT_PUBLIC) {

				result += data[1];
			}
		}

		return (result);
	}

	public int[]
	getMaxSeedConnections() {
		return ( adapter.getMaxSeedConnections());
	}

	private int getMaxSeedConnections(
		String		net) {
		int[]	data = getMaxSeedConnections();

		int	result = data[0];

			// 0 = unlimited

		if (result > 0) {

			if (net != AENetworkClassifier.AT_PUBLIC) {

				result += data[1];
			}
		}

		return (result);
	}

	/**
	 * returns the allowed connections for the given network
	 * -1 -> unlimited
	 */

	public int getMaxNewConnectionsAllowed(
		String		network) {
		int[]	max_con = getMaxConnections();

		int	dl_max = max_con[0];

		if (network != AENetworkClassifier.AT_PUBLIC) {

			dl_max += max_con[1];
		}

		int	allowed_peers = PeerUtils.numNewConnectionsAllowed(getPeerIdentityDataID(), dl_max);

		return (allowed_peers);
	}

	/**
	 * returns number of whatever peers to connect and then extra ones that must be non-pub if available
	 * @return -1 -> unlimited
	 */

	private int[]
	getMaxNewConnectionsAllowed() {
		int[]	max_con = getMaxConnections();

		int	dl_max 	= max_con[0];
		int	extra	= max_con[1];

		int	allowed_peers = PeerUtils.numNewConnectionsAllowed(getPeerIdentityDataID(), dl_max + extra);

			// allowed_peers == -1 -> unlimited

		if (allowed_peers >= 0) {

			allowed_peers -= extra;

			if (allowed_peers < 0) {

				extra += allowed_peers;

				if (extra < 0) {

					extra = 0;
				}

				allowed_peers = 0;
			}
		}

		return (new int[]{ allowed_peers, extra });
	}

	public int getSchedulePriority() {
		return isSeeding() ? Integer.MAX_VALUE : adapter.getPosition();
	}

	public boolean hasPotentialConnections() {
		return (pendingNatTraversals.size() + peerDatabase.getDiscoveredPeerCount() > 0);
	}

	public String getRelationText() {
		return ( adapter.getLogRelation().getRelationText());
	}

	public Object[]
	              getQueryableInterfaces() {
		return ( adapter.getLogRelation().getQueryableInterfaces());
	}



	public PEPeerTransport getTransportFromIdentity(byte[] peer_id) {
		final List<PEPeerTransport>	peer_transports = peerTransportsCow;
		for (int i=0; i < peer_transports.size(); i++) {
			final PEPeerTransport conn = peer_transports.get(i);
			if (Arrays.equals( peer_id, conn.getId() ))   return conn;
		}
		return null;
	}

	/* peer item is not reliable for general use
	public PEPeerTransport getTransportFromPeerItem(PeerItem peerItem) {
		ArrayList peer_transports =peer_transports_cow;
		for (int i =0; i <peer_transports.size(); i++) {
			PEPeerTransport pt =(PEPeerTransport) peer_transports.get(i);
			if (pt.getPeerItemIdentity().equals(peerItem))
				return pt;
		}
		return null;
	}
	*/

	public PEPeerTransport getTransportFromAddress(String peer) {
		final List<PEPeerTransport> peerTransports = peerTransportsCow;
		for (int i =0; i <peerTransports.size(); i++) {
			final PEPeerTransport pt = peerTransports.get(i);
			if (peer.equals(pt.getIp()))
				return pt;
		}
		return null;
	}

	// Snubbed peers accounting
	public void incNbPeersSnubbed() {
		nbPeersSnubbed++;
	}

	public void decNbPeersSnubbed() {
		nbPeersSnubbed--;
	}

	public void setNbPeersSnubbed(int n) {
		nbPeersSnubbed =n;
	}

	public int getNbPeersSnubbed() {
		return nbPeersSnubbed;
	}

	public boolean getPreferUDP() {
		return (preferUdp);
	}

	public void setPreferUDP(
		boolean	prefer) {
		 preferUdp = prefer;
	}

	public boolean isPeerSourceEnabled(
		String peer_source ) {
		return (adapter.isPeerSourceEnabled( peer_source));
	}

	public boolean isNetworkEnabled(
		String net ) {
		return (adapter.isNetworkEnabled( net));
	}

	public void peerDiscovered(
		PEPeerTransport		finder,
		PeerItem			pi) {
		final ArrayList peer_manager_listeners = peerManagerListenersCow;

		for (int i=0; i < peer_manager_listeners.size(); i++) {
			try {
				((PEPeerManagerListener)peer_manager_listeners.get(i)).peerDiscovered(this, pi, finder);

			} catch (Throwable e) {

				Debug.printStackTrace(e);
			}
		}
	}

	public TrackerPeerSource
	getTrackerPeerSource() {
		return (
			new TrackerPeerSourceAdapter() {
				public int getType() {
					return (TP_PEX);
				}

				public int getStatus() {
					return (isPeerExchangeEnabled()?ST_ONLINE:ST_DISABLED);
				}

				public String getName() {
					return (
						MessageText.getString("tps.pex.details",
							new String[]{
								String.valueOf( peerTransportsCow.size()),
								String.valueOf( peerDatabase.getExchangedPeerCount()),
								String.valueOf( peerDatabase.getDiscoveredPeerCount())}));
				}

				public int getPeers() {
					return ( isPeerExchangeEnabled()?peerDatabase.getExchangedPeersUsed():-1);
				}
			});
	}

	public void generateEvidence(
			IndentWriter		writer) {
		writer.println("PeerManager: seeding=" + seedingMode);

		writer.println(
				"    udp_fb=" + pendingNatTraversals.size() +
				",udp_tc=" + udpTraversalCount +
				",pd=[" + peerDatabase.getString() + "]");

		String	pending_udp = "";

		try {
			peerTransportsMon.enter();

			Iterator<PEPeerTransport>	it = pendingNatTraversals.values().iterator();

			while (it.hasNext()) {

				PEPeerTransport peer = it.next();

				pending_udp += (pending_udp.length()==0?"":",") + peer.getPeerItemIdentity().getAddressString() + ":" + peer.getPeerItemIdentity().getUDPPort();
			}
		} finally {

			peerTransportsMon.exit();
		}

		if (pending_udp.length() > 0) {

			writer.println("    pending_udp=" + pending_udp);
		}

		List	traversals = PeerNATTraverser.getSingleton().getTraversals(this);

		String	active_udp = "";

		Iterator it1 = traversals.iterator();

		while (it1.hasNext()) {

			InetSocketAddress ad = (InetSocketAddress)it1.next();

			active_udp += (active_udp.length()==0?"":",") + AddressUtils.getHostAddress(ad) + ":" + ad.getPort();
		}

		if (active_udp.length() > 0) {

			writer.println("    active_udp=" + active_udp);
		}

		if (!seedingMode) {

			writer.println("  Active Pieces");

			int	num_active = 0;

			try {
				writer.indent();

				String	str	= "";
				int		num	= 0;

				for (int i=0;i<pePieces.length;i++) {

					PEPiece	piece = pePieces[i];

					if (piece != null) {

						num_active++;

						str += (str.length()==0?"":",") + "#" + i + " " + dmPieces[i].getString() + ": " + piece.getString();

						num++;

						if (num == 20) {

							writer.println(str);
							str = "";
							num	= 0;
						}
					}
				}

				if (num > 0) {
					writer.println(str);
				}

			} finally {

				writer.exdent();
			}

			if (num_active == 0) {

				writer.println("  Inactive Pieces (excluding done/skipped)");

				try {
					writer.indent();

					String	str	= "";
					int		num	= 0;

					for (int i=0;i<dmPieces.length;i++) {

						DiskManagerPiece	dm_piece = dmPieces[i];

						if (dm_piece.isInteresting()) {

							str += (str.length()==0?"":",") + "#" + i + " " + dmPieces[i].getString();

							num++;

							if (num == 20) {

								writer.println(str);
								str = "";
								num	= 0;
							}
						}
					}

					if (num > 0) {

						writer.println(str);
					}

				} finally {

					writer.exdent();
				}
			}

			piecePicker.generateEvidence(writer);
		}

		try {
			peerTransportsMon.enter();

			writer.println("Peers: total = " + peerTransportsCow.size());

			writer.indent();

			try {
				writer.indent();

				Iterator<PEPeerTransport> it2 = peerTransportsCow.iterator();

				while (it2.hasNext()) {

					PEPeerTransport	peer = it2.next();

					peer.generateEvidence(writer);
				}
			} finally {

				writer.exdent();
			}
		} finally {

			peerTransportsMon.exit();

			writer.exdent();
		}

		diskMgr.generateEvidence(writer);
	}
}
