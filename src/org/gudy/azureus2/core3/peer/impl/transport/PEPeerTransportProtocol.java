/*
 * File	: PEPeerTransportProtocol.java
 * Created : 22-Oct-2003
 * By		: stuff
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
package org.gudy.azureus2.core3.peer.impl.transport;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.config.ParameterListener;
import org.gudy.azureus2.core3.disk.DiskManager;
import org.gudy.azureus2.core3.disk.DiskManagerPiece;
import org.gudy.azureus2.core3.disk.DiskManagerReadRequest;
import org.gudy.azureus2.core3.logging.LogAlert;
import org.gudy.azureus2.core3.logging.LogEvent;
import org.gudy.azureus2.core3.logging.LogIDs;
import org.gudy.azureus2.core3.logging.LogRelation;
import org.gudy.azureus2.core3.logging.Logger;
import org.gudy.azureus2.core3.peer.PEPeer;
import org.gudy.azureus2.core3.peer.PEPeerListener;
import org.gudy.azureus2.core3.peer.PEPeerManager;
import org.gudy.azureus2.core3.peer.PEPeerSource;
import org.gudy.azureus2.core3.peer.PEPeerStats;
import org.gudy.azureus2.core3.peer.impl.PEPeerControl;
import org.gudy.azureus2.core3.peer.impl.PEPeerTransport;
import org.gudy.azureus2.core3.peer.impl.PEPeerTransportFactory;
import org.gudy.azureus2.core3.peer.util.PeerIdentityDataID;
import org.gudy.azureus2.core3.peer.util.PeerIdentityManager;
import org.gudy.azureus2.core3.peer.util.PeerUtils;
import org.gudy.azureus2.core3.util.AEMonitor;
import org.gudy.azureus2.core3.util.AENetworkClassifier;
import org.gudy.azureus2.core3.util.AERunStateHandler;
import org.gudy.azureus2.core3.util.AddressUtils;
import org.gudy.azureus2.core3.util.ByteFormatter;
import org.gudy.azureus2.core3.util.Constants;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.DirectByteBuffer;
import org.gudy.azureus2.core3.util.DirectByteBufferPool;
import org.gudy.azureus2.core3.util.HashWrapper;
import org.gudy.azureus2.core3.util.IPToHostNameResolver;
import org.gudy.azureus2.core3.util.IPToHostNameResolverListener;
import org.gudy.azureus2.core3.util.IPToHostNameResolverRequest;
import org.gudy.azureus2.core3.util.IndentWriter;
import org.gudy.azureus2.core3.util.LightHashMap;
import org.gudy.azureus2.core3.util.RandomUtils;
import org.gudy.azureus2.core3.util.SHA1Hasher;
import org.gudy.azureus2.core3.util.SHA1Simple;
import org.gudy.azureus2.core3.util.SimpleTimer;
import org.gudy.azureus2.core3.util.StringInterner;
import org.gudy.azureus2.core3.util.SystemTime;
import org.gudy.azureus2.core3.util.TimerEvent;
import org.gudy.azureus2.core3.util.TimerEventPerformer;
import org.gudy.azureus2.plugins.clientid.ClientIDGenerator;
import org.gudy.azureus2.plugins.dht.mainline.MainlineDHTProvider;
import org.gudy.azureus2.plugins.network.Connection;
import org.gudy.azureus2.plugins.peers.Peer;
import org.gudy.azureus2.pluginsimpl.local.clientid.ClientIDManagerImpl;
import org.gudy.azureus2.pluginsimpl.local.network.ConnectionImpl;

import com.aelitis.azureus.core.impl.AzureusCoreImpl;
import com.aelitis.azureus.core.networkmanager.ConnectionEndpoint;
import com.aelitis.azureus.core.networkmanager.IncomingMessageQueue;
import com.aelitis.azureus.core.networkmanager.LimitedRateGroup;
import com.aelitis.azureus.core.networkmanager.NetworkConnection;
import com.aelitis.azureus.core.networkmanager.NetworkManager;
import com.aelitis.azureus.core.networkmanager.OutgoingMessageQueue;
import com.aelitis.azureus.core.networkmanager.ProtocolEndpoint;
import com.aelitis.azureus.core.networkmanager.ProtocolEndpointFactory;
import com.aelitis.azureus.core.networkmanager.Transport;
import com.aelitis.azureus.core.networkmanager.admin.NetworkAdmin;
import com.aelitis.azureus.core.networkmanager.impl.tcp.TCPNetworkManager;
import com.aelitis.azureus.core.networkmanager.impl.udp.UDPNetworkManager;
import com.aelitis.azureus.core.peermanager.messaging.Message;
import com.aelitis.azureus.core.peermanager.messaging.MessageManager;
import com.aelitis.azureus.core.peermanager.messaging.MessageStreamEncoder;
import com.aelitis.azureus.core.peermanager.messaging.azureus.AZBadPiece;
import com.aelitis.azureus.core.peermanager.messaging.azureus.AZHandshake;
import com.aelitis.azureus.core.peermanager.messaging.azureus.AZHave;
import com.aelitis.azureus.core.peermanager.messaging.azureus.AZMessage;
import com.aelitis.azureus.core.peermanager.messaging.azureus.AZMessageDecoder;
import com.aelitis.azureus.core.peermanager.messaging.azureus.AZMessageEncoder;
import com.aelitis.azureus.core.peermanager.messaging.azureus.AZMetaData;
import com.aelitis.azureus.core.peermanager.messaging.azureus.AZPeerExchange;
import com.aelitis.azureus.core.peermanager.messaging.azureus.AZRequestHint;
import com.aelitis.azureus.core.peermanager.messaging.azureus.AZStatReply;
import com.aelitis.azureus.core.peermanager.messaging.azureus.AZStatRequest;
import com.aelitis.azureus.core.peermanager.messaging.azureus.AZStylePeerExchange;
import com.aelitis.azureus.core.peermanager.messaging.azureus.AZUTMetaData;
import com.aelitis.azureus.core.peermanager.messaging.bittorrent.BTAllowedFast;
import com.aelitis.azureus.core.peermanager.messaging.bittorrent.BTBitfield;
import com.aelitis.azureus.core.peermanager.messaging.bittorrent.BTCancel;
import com.aelitis.azureus.core.peermanager.messaging.bittorrent.BTChoke;
import com.aelitis.azureus.core.peermanager.messaging.bittorrent.BTDHTPort;
import com.aelitis.azureus.core.peermanager.messaging.bittorrent.BTHandshake;
import com.aelitis.azureus.core.peermanager.messaging.bittorrent.BTHave;
import com.aelitis.azureus.core.peermanager.messaging.bittorrent.BTHaveAll;
import com.aelitis.azureus.core.peermanager.messaging.bittorrent.BTHaveNone;
import com.aelitis.azureus.core.peermanager.messaging.bittorrent.BTInterested;
import com.aelitis.azureus.core.peermanager.messaging.bittorrent.BTKeepAlive;
import com.aelitis.azureus.core.peermanager.messaging.bittorrent.BTMessage;
import com.aelitis.azureus.core.peermanager.messaging.bittorrent.BTMessageDecoder;
import com.aelitis.azureus.core.peermanager.messaging.bittorrent.BTMessageEncoder;
import com.aelitis.azureus.core.peermanager.messaging.bittorrent.BTMessageFactory;
import com.aelitis.azureus.core.peermanager.messaging.bittorrent.BTPiece;
import com.aelitis.azureus.core.peermanager.messaging.bittorrent.BTRawMessage;
import com.aelitis.azureus.core.peermanager.messaging.bittorrent.BTRejectRequest;
import com.aelitis.azureus.core.peermanager.messaging.bittorrent.BTRequest;
import com.aelitis.azureus.core.peermanager.messaging.bittorrent.BTSuggestPiece;
import com.aelitis.azureus.core.peermanager.messaging.bittorrent.BTUnchoke;
import com.aelitis.azureus.core.peermanager.messaging.bittorrent.BTUninterested;
import com.aelitis.azureus.core.peermanager.messaging.bittorrent.ltep.LTHandshake;
import com.aelitis.azureus.core.peermanager.messaging.bittorrent.ltep.LTMessage;
import com.aelitis.azureus.core.peermanager.messaging.bittorrent.ltep.LTMessageDecoder;
import com.aelitis.azureus.core.peermanager.messaging.bittorrent.ltep.LTMessageEncoder;
import com.aelitis.azureus.core.peermanager.messaging.bittorrent.ltep.UTMetaData;
import com.aelitis.azureus.core.peermanager.messaging.bittorrent.ltep.UTPeerExchange;
import com.aelitis.azureus.core.peermanager.messaging.bittorrent.ltep.UTUploadOnly;
import com.aelitis.azureus.core.peermanager.peerdb.PeerExchangerItem;
import com.aelitis.azureus.core.peermanager.peerdb.PeerItem;
import com.aelitis.azureus.core.peermanager.peerdb.PeerItemFactory;
import com.aelitis.azureus.core.peermanager.piecepicker.PiecePicker;
import com.aelitis.azureus.core.peermanager.piecepicker.util.BitFlags;
import com.aelitis.azureus.core.peermanager.utils.AZPeerIdentityManager;
import com.aelitis.azureus.core.peermanager.utils.ClientIdentifier;
import com.aelitis.azureus.core.peermanager.utils.OutgoingBTHaveMessageAggregator;
import com.aelitis.azureus.core.peermanager.utils.OutgoingBTPieceMessageHandler;
import com.aelitis.azureus.core.peermanager.utils.OutgoingBTPieceMessageHandlerAdapter;
import com.aelitis.azureus.core.peermanager.utils.PeerClassifier;
import com.aelitis.azureus.core.peermanager.utils.PeerMessageLimiter;
import com.aelitis.azureus.core.proxy.AEProxyAddressMapper;
import com.aelitis.azureus.core.proxy.AEProxyFactory;
import com.aelitis.azureus.core.tag.TaggableResolver;

import hello.util.Log;
import hello.util.SingleCounter0;

public class PEPeerTransportProtocol
	extends LogRelation
	implements PEPeerTransport
{
	
	private static String TAG = PEPeerTransportProtocol.class.getSimpleName();
	
	protected final static LogIDs LOGID = LogIDs.PEER;
	private volatile int	_lastPiece =-1;		//last piece that was requested from this peer (mostly to try to request from same one)
	protected final PEPeerControl 	manager;
	protected final DiskManager		diskManager;
	protected final PiecePicker		piecePicker;
	protected final int		nbPieces;
	private final String			peerSource;
	private byte[] peerId;
	private final String ip;
	private final String network;
	protected String ip_resolved;
	private IPToHostNameResolverRequest	ip_resolver_request;
	private final int port;
	private PeerItem peerItemIdentity;
	private int tcp_listen_port = 0;
	private int udpListenPort = 0;
	private int udpNonDataPort = 0;
	// if the peer announces an ipv6 address
	private InetAddress alternativeAddress;
	private byte	crypto_level;
	protected PEPeerStats peerStats;
	private final ArrayList<DiskManagerReadRequest> requested = new ArrayList<DiskManagerReadRequest>();
	private final AEMonitor	requested_mon = new AEMonitor("PEPeerTransportProtocol:Req");
	private Map data;
	private long lastNeededUndonePieceChange;
	private boolean really_choked_by_other_peer = true;
	private boolean effectively_choked_by_other_peer = true;
	private long	effectively_unchoked_time = -1;
	protected boolean choking_other_peer = true;
	private boolean interested_in_other_peer = false;
	private boolean otherPeerInterestedInMe = false;
	private long snubbed =0;
	/** lazy allocation; null until needed */
	private volatile BitFlags	peerHavePieces =null;
	private volatile boolean	availabilityAdded =false;
	private volatile boolean	received_bitfield;
	private int[]	piece_priority_offsets;
	private boolean handshakeSent;
	private boolean seeding = false;
	private static final byte RELATIVE_SEEDING_NONE = 0x00;
	// indicates that the peer told us that he's uploading only
	private static final byte RELATIVE_SEEDING_UPLOAD_ONLY_INDICATED = 0x01;
	// peer is not useful to us
	private static final byte RELATIVE_SEEDING_UPLOAD_ONLY_SEED = 0x02;
	private byte relativeSeeding = RELATIVE_SEEDING_NONE;
	private final boolean incoming;
	protected volatile boolean closing = false;
	private volatile int currentPeerState;
	final private NetworkConnection connection;
	private OutgoingBTPieceMessageHandler outgoingPieceMessageHandler;
	private OutgoingBTHaveMessageAggregator outgoingHaveMessageAggregator;
	private Connection	pluginConnection;
	private boolean identityAdded = false;	//needed so we don't remove id's in closeAll() on duplicate connection attempts
	protected int connectionState = PEPeerTransport.CONNECTION_PENDING;
	private String client = ""; // Client name to show to user.
	private String clientPeerId = ""; // Client name derived from the peer ID.
	private String client_handshake = ""; // Client name derived from the handshake.
	private String client_handshake_version = ""; // Client version derived from the handshake.
	//When superSeeding, number of unique piece announced
	private int uniquePiece = -1;
	//When downloading a piece in exclusivity mode the piece number being downloaded
	private int[] reserved_pieces = null;
	//Spread time (0 secs , fake default)
	private int spreadTimeHint = 0 * 1000;
	private long last_message_sent_time = 0;
	private long lastMessageReceivedTime = 0;
	private long lastDataMessageReceivedTime = -1;
	private long lastGoodDataTime =-1;			// time data written to disk was recieved
	private long last_data_message_sent_time = -1;
	private long connectionEstablishedTime = 0;
	private int consecutive_no_request_count;
	private int messagingMode = MESSAGING_BT_ONLY;
	private Message[] supported_messages = null;
	private byte		otherPeerBitfieldVersion			= BTMessageFactory.MESSAGE_VERSION_INITIAL;
	private byte		otherPeerCancelVersion				= BTMessageFactory.MESSAGE_VERSION_INITIAL;
	private byte		other_peer_choke_version			= BTMessageFactory.MESSAGE_VERSION_INITIAL;
	private byte		otherPeerHandshakeVersion			= BTMessageFactory.MESSAGE_VERSION_INITIAL;
	private byte		other_peer_bt_have_version			= BTMessageFactory.MESSAGE_VERSION_INITIAL;
	private byte		other_peer_az_have_version			= BTMessageFactory.MESSAGE_VERSION_INITIAL;
	private byte		other_peer_interested_version		= BTMessageFactory.MESSAGE_VERSION_INITIAL;
	private byte		other_peer_keep_alive_version		= BTMessageFactory.MESSAGE_VERSION_INITIAL;
	private byte		other_peer_pex_version				= BTMessageFactory.MESSAGE_VERSION_INITIAL;
	private byte		other_peer_piece_version			= BTMessageFactory.MESSAGE_VERSION_INITIAL;
	private byte		other_peer_unchoke_version			= BTMessageFactory.MESSAGE_VERSION_INITIAL;
	private byte		other_peer_uninterested_version		= BTMessageFactory.MESSAGE_VERSION_INITIAL;
	private byte		other_peer_request_version			= BTMessageFactory.MESSAGE_VERSION_INITIAL;
	private byte		other_peer_suggest_piece_version	= BTMessageFactory.MESSAGE_VERSION_INITIAL;
	private byte		other_peer_have_all_version			= BTMessageFactory.MESSAGE_VERSION_INITIAL;
	private byte		other_peer_have_none_version		= BTMessageFactory.MESSAGE_VERSION_INITIAL;
	private byte		other_peer_reject_request_version	= BTMessageFactory.MESSAGE_VERSION_INITIAL;
	private byte		other_peer_allowed_fast_version		= BTMessageFactory.MESSAGE_VERSION_INITIAL;
	private final byte	other_peer_bt_lt_ext_version		= BTMessageFactory.MESSAGE_VERSION_INITIAL;
	private byte		other_peer_az_request_hint_version	= BTMessageFactory.MESSAGE_VERSION_INITIAL;
	private byte		other_peer_az_bad_piece_version		= BTMessageFactory.MESSAGE_VERSION_INITIAL;
	private byte		other_peer_az_stats_request_version	= BTMessageFactory.MESSAGE_VERSION_INITIAL;
	private byte		other_peer_az_stats_reply_version	= BTMessageFactory.MESSAGE_VERSION_INITIAL;
	private byte		other_peer_az_metadata_version		= BTMessageFactory.MESSAGE_VERSION_INITIAL;
	private static final boolean DEBUG_FAST = false;
	private boolean ut_pex_enabled 			= false;
	private boolean fast_extension_enabled 	= false;
	private boolean ml_dht_enabled 			= false;
	private static final int	ALLOWED_FAST_PIECE_OFFERED_NUM		= 10;
	private static final int	ALLOWED_FAST_OTHER_PEER_PIECE_MAX	= 10;
	private static final Object	KEY_ALLOWED_FAST_RECEIVED 	= new Object();
	private static final Object	KEY_ALLOWED_FAST_SENT 		= new Object();
	private final AEMonitor closingMon	= new AEMonitor("PEPeerTransportProtocol:closing");
	private final AEMonitor generalMon		= new AEMonitor("PEPeerTransportProtocol:data");
	private byte[] handshake_reserved_bytes = null;
	private LinkedHashMap recentOutgoingRequests;
	private AEMonitor	recentOutgoingRequestsMon;
	private boolean has_received_initial_pex = false;
	private static final boolean SHOW_DISCARD_RATE_STATS;
	static {
		final String	prop = System.getProperty("show.discard.rate.stats");
		SHOW_DISCARD_RATE_STATS = prop != null && prop.equals("1");
	}

	private static int requestsDiscarded = 0;
	private static int requestsDiscardedEndgame = 0;
	private static int requestsRecovered = 0;
	private static int requestsCompleted = 0;
	private static final int REQUEST_HINT_MAX_LIFE	= PiecePicker.REQUEST_HINT_MAX_LIFE + 30*1000;
	private int[]	requestHint;
	private List peerListenersCow;
	private final AEMonitor	peerListenersMon = new AEMonitor("PEPeerTransportProtocol:PL");

	//certain Optimum Online networks block peer seeding via "complete" bitfield message filtering
	//lazy mode makes sure we never send a complete (seed) bitfield
	protected static boolean ENABLE_LAZY_BITFIELD;
	private boolean priorityConnection;
	private int uploadPriorityAuto;
	
	private static final class DisconnectedTransportQueue extends LinkedHashMap {
		
		public DisconnectedTransportQueue() {
			super(20,0.75F);
		}

		private static final long MAX_CACHE_AGE = 2*60*1000;
		// remove all elements older than 2 minutes until we hit the 20 again
		private void performCleaning() {
			if (size() > 20) {
				Iterator it = values().iterator();
				long now = SystemTime.getMonotonousTime();
				while (it.hasNext() && size() > 20) {
					QueueEntry eldest = (QueueEntry)it.next();
					if (now - eldest.addTime > MAX_CACHE_AGE) {
						it.remove();
					} else {
						break;
					}
				}
			}
		}

		private static final class QueueEntry {
			public QueueEntry(PEPeerTransportProtocol trans) {
				transport = trans;
			}
			final PEPeerTransportProtocol transport;
			final long addTime = SystemTime.getMonotonousTime();
		}
		
		// hardcap at 100
		protected boolean removeEldestEntry(Map.Entry eldest) {
			return size() > 100;
		}
		
		synchronized public Object put(HashWrapper key, PEPeerTransportProtocol value) {
			performCleaning();
			return super.put(key, new QueueEntry(value));
		}
		
		synchronized public PEPeerTransportProtocol remove(HashWrapper key) {
			performCleaning();
			QueueEntry entry = (QueueEntry)super.remove(key);
			if (entry != null)
				return entry.transport;
			else
				return null;
		}
	}

	private static final DisconnectedTransportQueue recentlyDisconnected = new DisconnectedTransportQueue();
	private static boolean 	fast_unchoke_new_peers;
	private static final Random rnd = RandomUtils.SECURE_RANDOM;
	private static final byte[] sessionSecret;
	private static boolean enable_upload_bias;
	static {
		rnd.setSeed(SystemTime.getHighPrecisionCounter());
		sessionSecret = new byte[20];
		rnd.nextBytes(sessionSecret);
		COConfigurationManager.addAndFireParameterListeners(
				new String[]{
					"Use Lazy Bitfield",
					"Peer.Fast.Initial.Unchoke.Enabled",
					"Bias Upload Enable" },
				new ParameterListener() {
					public final void parameterChanged(String ignore) {
						final String	prop = System.getProperty("azureus.lazy.bitfield");
						ENABLE_LAZY_BITFIELD = prop != null && prop.equals("1");
						ENABLE_LAZY_BITFIELD |= COConfigurationManager.getBooleanParameter("Use Lazy Bitfield");
						fast_unchoke_new_peers 		= COConfigurationManager.getBooleanParameter("Peer.Fast.Initial.Unchoke.Enabled");
						enable_upload_bias 			= COConfigurationManager.getBooleanParameter("Bias Upload Enable");
					}
				});
	}

	// reconnect stuff
	private HashWrapper peerSessionID;
	private HashWrapper mySessionID;
	// allow reconnect if we've sent or recieved at least 1 piece over the current connection
	private boolean allowReconnect;

	private Set<Object>		uploadDisabledSet;
	private Set<Object>		downloadDisabledSet;
	private boolean			isUploadDisabled;
	private boolean			isDownloadDisabled;
	private boolean isOptimisticUnchoke = false;
	private PeerExchangerItem peerExchangeItem = null;
	private boolean peerExchangeSupported = false;
	protected PeerMessageLimiter messageLimiter;
	private boolean requestHintSupported;
	private boolean badPieceSupported;
	private boolean statsRequestSupported;
	private boolean statsReplySupported;
	private boolean azMetadataSupported;
	private boolean haveAggregationDisabled;
	private volatile boolean	manualLazyBitfieldControl;
	private volatile int[]		manualLazyHaves;
	private final boolean isMetadataDownload;
	private long requestLatency;
	
	//INCOMING
	public PEPeerTransportProtocol(
		PEPeerControl 		_manager,
		String 				_peerSource,
		NetworkConnection	_connection,
		Map					_initialUserData) {
		
		/*Log.d(TAG, "ctor1() is called...");
		Throwable t = new Throwable();
		t.printStackTrace();*/
		
		manager		= _manager;
		peerSource	= _peerSource;
		connection 	= _connection;
		data		= _initialUserData;
		incoming = true;
		isMetadataDownload = manager.isMetadataDownload();
		diskManager = manager.getDiskManager();
		piecePicker = manager.getPiecePicker();
		nbPieces = diskManager.getNbPieces();
		InetSocketAddress notionalAddress = _connection.getEndpoint().getNotionalAddress();
		ip	= AddressUtils.getHostAddress(notionalAddress);
		network	= AENetworkClassifier.categoriseAddress(ip);
		port	= notionalAddress.getPort();
		peerItemIdentity = PeerItemFactory.createPeerItem(
				ip, port, 
				PeerItem.convertSourceID(_peerSource),
				PeerItemFactory.HANDSHAKE_TYPE_PLAIN,
				0,
				PeerItemFactory.CRYPTO_LEVEL_1,
				0);	//this will be recreated upon az handshake decode
		pluginConnection = new ConnectionImpl(connection, incoming);
		peerStats = manager.createPeerStats(this);
		changePeerState(PEPeer.CONNECTING);
	}

	public void start() {
		
		// split out connection initiation from constructor so we can get access to the peer transport
		// before message processing starts
		if (incoming) {
			//"fake" a connect request to register our listener
			connection.connect(
				ProtocolEndpoint.CONNECT_PRIORITY_MEDIUM,
				new NetworkConnection.ConnectionListener() {
					
					public final int connectStarted(int ct) {
						connectionState = PEPeerTransport.CONNECTION_CONNECTING;
						return (ct);
					}
					
					public final void connectSuccess(ByteBuffer remaining_initial_data) {	//will be called immediately
						if (Logger.isEnabled())
							Logger.log(new LogEvent(PEPeerTransportProtocol.this, LOGID,
							"In: Established incoming connection"));
						generateSessionId();
						initializeConnection();
						/*
						 * Waiting until we've received the initiating-end's full handshake, before sending back our own,
						 * really should be the "proper" behavior.	However, classic BT trackers running NAT checking will
						 * only send the first 48 bytes (up to infohash) of the peer handshake, skipping peerid, which means
						 * we'll never get their complete handshake, and thus never reply, which causes the NAT check to fail.
						 * So, we need to send our handshake earlier, after we've verified the infohash.
						 * NOTE:
						 * This code makes the assumption that the inbound infohash has already been validated,
						 * as we don't check their handshake fully before sending our own.
						 */
						sendBTHandshake();
					}
					
					public final void connectFailure(
						Throwable failure_msg ) {	//should never happen
						Debug.out("ERROR: incoming connect failure: ", failure_msg);
						closeConnectionInternally("ERROR: incoming connect failure [" + PEPeerTransportProtocol.this + "] : " + failure_msg.getMessage(), true, true);
					}
					
					public final void exceptionThrown(Throwable error ) {
						if (error.getMessage() == null) {
							Debug.out(error);
						}
						closeConnectionInternally("connection exception: " + error.getMessage(), false, true);
					}
					
					public Object getConnectionProperty(
						String property_name) {
						return (null);
					}
					
					public String getDescription() {
						return (getString());
					}
				});
		} else {
			// not pulled out startup from outbound connections yet...
		}
	}
	
	//OUTGOING
	public PEPeerTransportProtocol(
			PEPeerControl 	_manager,
			String 			_peerSource,
			String 			_ip,
			int 			_tcpPort,
			int 			_udpPort,
			boolean			_useTcp,
			boolean 		_requireCryptoHandshake,
			byte			_cryptoLevel,
			Map				_initialUserData) {
		
		/*Log.d(TAG, "ctor2() is called..");
		Throwable t = new Throwable();
		t.printStackTrace();*/
		
		manager = _manager;
		isMetadataDownload = manager.isMetadataDownload();
		diskManager = manager.getDiskManager();
		piecePicker = manager.getPiecePicker();
		nbPieces = diskManager.getNbPieces();
		lastNeededUndonePieceChange = Long.MIN_VALUE;
		peerSource		= _peerSource;
		ip				= _ip;
		port			= _tcpPort;
		tcp_listen_port = _tcpPort;
		udpListenPort	= _udpPort;
		crypto_level	= _cryptoLevel;
		data			= _initialUserData;
		network	= AENetworkClassifier.categoriseAddress(ip);
		if (data != null) {
			Boolean pc = (Boolean)data.get(Peer.PR_PRIORITY_CONNECTION);
			if (pc != null && pc.booleanValue()) {
				setPriorityConnection(true);
			}
		}
		udpNonDataPort = UDPNetworkManager.getSingleton().getUDPNonDataListeningPortNumber();
		peerItemIdentity = PeerItemFactory.createPeerItem(ip, tcp_listen_port, PeerItem.convertSourceID( _peerSource ), PeerItemFactory.HANDSHAKE_TYPE_PLAIN, _udpPort, crypto_level, 0);	//this will be recreated upon az handshake decode
		incoming = false;
		peerStats = manager.createPeerStats(this);
		if (port < 0 || port > 65535) {
			closeConnectionInternally("given remote port is invalid: " + port);
			connection = null;
			return;
		}
		
		// either peer specific or global pref plus optional per-download level
		boolean useCrypto =
				_requireCryptoHandshake ||
				NetworkManager.getCryptoRequired(manager.getAdapter().getCryptoLevel());
		boolean	lanLocal = isLANLocal();
		boolean publicNet = peerItemIdentity.getNetwork() == AENetworkClassifier.AT_PUBLIC;
		if (lanLocal || !publicNet) {
			useCrypto = false; // dont bother with PHE for lan peers
								// or non-public networks. one reason for this is that it doesn't work with i2psnark for some reason
								// (actually the reason is that it blocks peers that send a bad message (ConnectionAcceptor:MAX_BAD) which
								// causes the subsequent plain connection to be dropped)
								// AND it causes a raw-message to be submitted to the outgoing message queue which
								// then bypasses i2phelp plugin handshake modifications...
								// note also that incoming non-public connections also ignore crypto settings
		}
		
		InetSocketAddress	endpointAddress;
		ProtocolEndpoint	pe1;
		ProtocolEndpoint	pe2 = null;
		if (_useTcp) {
			boolean utpAvailable = ProtocolEndpointFactory.isHandlerRegistered(ProtocolEndpoint.PROTOCOL_UTP);
			boolean socks_active = NetworkAdmin.getSingleton().isSocksActive();
			if (publicNet) {
				endpointAddress = new InetSocketAddress(ip, tcp_listen_port);
			} else {
				endpointAddress = InetSocketAddress.createUnresolved(ip, tcp_listen_port);
			}
			if (lanLocal || !utpAvailable || !publicNet) {
				pe1 = ProtocolEndpointFactory.createEndpoint(ProtocolEndpoint.PROTOCOL_TCP, endpointAddress);
			} else if (AERunStateHandler.isUDPNetworkOnly() && !socks_active) {
				pe1 = ProtocolEndpointFactory.createEndpoint(ProtocolEndpoint.PROTOCOL_UTP, endpointAddress);
			} else {
				pe1 = ProtocolEndpointFactory.createEndpoint(ProtocolEndpoint.PROTOCOL_TCP, endpointAddress);
				if (!socks_active) {
					if (RandomUtils.nextInt(2) == 1) {
						pe2 = ProtocolEndpointFactory.createEndpoint(ProtocolEndpoint.PROTOCOL_UTP, endpointAddress);
					}
				}
			}
		} else {
			if (publicNet) {
				endpointAddress = new InetSocketAddress(ip, udpListenPort);
			} else {
				endpointAddress = InetSocketAddress.createUnresolved(ip, udpListenPort);
			}
			pe1 = ProtocolEndpointFactory.createEndpoint(ProtocolEndpoint.PROTOCOL_UDP, endpointAddress);
		}
		ConnectionEndpoint connectionEndpoint = new ConnectionEndpoint(endpointAddress);
		connectionEndpoint.addProtocol(pe1);
		if (pe2 != null) {
			connectionEndpoint.addProtocol(pe2);
		}
		connection =
			NetworkManager.getSingleton().createConnection(
					connectionEndpoint,
					new BTMessageEncoder(),
					new BTMessageDecoder(),
					useCrypto,
					!_requireCryptoHandshake,
					manager.getSecrets(_cryptoLevel));
		pluginConnection = new ConnectionImpl(connection, incoming);
		changePeerState(PEPeer.CONNECTING);
		ByteBuffer	initialOutboundData = null;
		if (useCrypto) {
			BTHandshake handshake = new BTHandshake(
					manager.getHash(),
					manager.getPeerId(),
					manager.getExtendedMessagingMode(), otherPeerHandshakeVersion);
			if (Logger.isEnabled())
				Logger.log(new LogEvent(this, LOGID,
						"Sending encrypted handshake with reserved bytes: " +
							ByteFormatter.nicePrint(handshake.getReserved(), false)));
			DirectByteBuffer[] ddbs = handshake.getRawData();
			int	handshakeLen = 0;
			for (int i=0;i<ddbs.length;i++) {
				handshakeLen += ddbs[i].remaining(DirectByteBuffer.SS_PEER);
			}
			initialOutboundData = ByteBuffer.allocate(handshakeLen);
			for (int i=0;i<ddbs.length;i++) {
				DirectByteBuffer	ddb = ddbs[i];
				initialOutboundData.put(ddb.getBuffer( DirectByteBuffer.SS_PEER));
				ddb.returnToPool();
			}
			initialOutboundData.flip();
			handshakeSent = true;
		}
		
		int	priority;
		if (manager.isSeeding()) {
			priority = ProtocolEndpoint.CONNECT_PRIORITY_LOW;
		} else if (manager.isRTA()) {
			if (PeerClassifier.isAzureusIP(ip)) {
				priority = ProtocolEndpoint.CONNECT_PRIORITY_SUPER_HIGHEST;
			} else {
				priority = ProtocolEndpoint.CONNECT_PRIORITY_HIGHEST;
			}
		} else if (PeerClassifier.isAzureusIP(ip)) {
			priority = ProtocolEndpoint.CONNECT_PRIORITY_HIGHEST;
		} else {
			priority = ProtocolEndpoint.CONNECT_PRIORITY_MEDIUM;
		}
		
		// give plugin peers some priority as they're being injected for a good reason
		if (peerSource == PEPeerSource.PS_PLUGIN) {
			if (priority > ProtocolEndpoint.CONNECT_PRIORITY_HIGH) {
				priority = ProtocolEndpoint.CONNECT_PRIORITY_HIGH;
			}
		}
		
		connection.connect(
				initialOutboundData,
				priority,
				new NetworkConnection.ConnectionListener() {
					private boolean	connectOk;
					public final int connectStarted(int defaultConnectTimeout) {
						connectionState = PEPeerTransport.CONNECTION_CONNECTING;
						if (defaultConnectTimeout <= 0) {
							return (defaultConnectTimeout);
						}
						return (manager.getConnectTimeout(defaultConnectTimeout));
					}
					
					public final void connectSuccess(ByteBuffer remainingInitialData) {
						connectOk = true;
						if (closing) {
							//Debug.out("PEPeerTransportProtocol::connectSuccess() called when closing.");
							return;
						}
						generateSessionId();
						if (Logger.isEnabled())
							Logger.log(new LogEvent(PEPeerTransportProtocol.this, LOGID,
									"Out: Established outgoing connection"));
						initializeConnection();
						if (remainingInitialData != null && remainingInitialData.remaining() > 0) {
							// queue as a *raw* message as already encoded
							connection.getOutgoingMessageQueue().addMessage(
								new BTRawMessage(new DirectByteBuffer(remainingInitialData)), false);
						}
						sendBTHandshake();
					}
					
					public final void connectFailure(Throwable failure_msg) {
						closeConnectionInternally("failed to establish outgoing connection: " + failure_msg.getMessage(), true, true);
					}
					
					public final void exceptionThrown(Throwable error) {
						if (error.getMessage() == null) {
							Debug.out("error.getMessage() == null", error);
						}
						closeConnectionInternally("connection exception: " + error.getMessage(), !connectOk, true);
					}
					
					public Object getConnectionProperty(
						String property_name) {
						if (property_name == AEProxyFactory.PO_PEER_NETWORKS) {
							return ( manager.getAdapter().getEnabledNetworks());
						}
						return (null);
					}
					
					public String getDescription() {
						return (getString());
					}
				});
		if (Logger.isEnabled())
			Logger.log(new LogEvent(this, LOGID,
			"Out: Creating outgoing connection"));
	}


	protected void initializeConnection() {
		
		if (closing)	
			return;
		
		recentOutgoingRequests = new LinkedHashMap(16, .75F, true) {
			public final boolean removeEldestEntry(Map.Entry eldest) {
				return size() > 16;
			}
		};
		recentOutgoingRequestsMon	= new AEMonitor("PEPeerTransportProtocol:ROR");
		messageLimiter = new PeerMessageLimiter();
		/*
		//link in outgoing piece handler
		outgoing_piece_message_handler =
			new OutgoingBTPieceMessageHandler(
					this,
					connection.getOutgoingMessageQueue(),
					new OutgoingBTPieceMessageHandlerAdapter() {
						public void diskRequestCompleted(
								long bytes) {
							peer_stats.diskReadComplete(bytes);
						}
				},
				other_peer_piece_version);*/
		//link in outgoing have message aggregator
		outgoingHaveMessageAggregator = new OutgoingBTHaveMessageAggregator(connection.getOutgoingMessageQueue(), other_peer_bt_have_version, other_peer_az_have_version);
		connectionEstablishedTime = SystemTime.getCurrentTime();
		connectionState = PEPeerTransport.CONNECTION_WAITING_FOR_HANDSHAKE;
		changePeerState(PEPeer.HANDSHAKING);
		registerForMessageHandling();
	}

	public String getPeerSource() {
		return (peerSource);
	}
	
	/**
	 * Close the peer connection from within the PEPeerTransport object.
	 * @param reason
	 */
	protected void closeConnectionInternally(String reason, boolean connect_failed, boolean network_failure) {
		performClose(reason, connect_failed, false, network_failure);
	}

	protected void closeConnectionInternally(String reason) {
		performClose(reason, false, false, false);
	}

	/**
	 * Close the peer connection from the PEPeerControl manager side.
	 * NOTE: This method assumes PEPeerControl already knows about the close.
	 * This method is inteded to be only invoked by select administrative methods.
	 * You probably should not invoke this directly.
	 */
	public void closeConnection(String reason) {
		performClose(reason, false, true, false);
	}

	private void performClose(String reason, boolean connect_failed, boolean externally_closed, boolean network_failure) {
		try {
			closingMon.enter();
			if (closing)
				return;
			closing = true;
			// immediatly lose interest in peer
			interested_in_other_peer =false;
			lastNeededUndonePieceChange =Long.MAX_VALUE;
			if (isSnubbed())
				manager.decNbPeersSnubbed();
			if (identityAdded) {	//remove identity
				if (peerId != null)
					PeerIdentityManager.removeIdentity( manager.getPeerIdentityDataID(), peerId, getPort());
				else
					Debug.out("PeerIdentity added but peer_id == null !!!");
				identityAdded	= false;
			}
			changePeerState(PEPeer.CLOSING);
		} finally {
			closingMon.exit();
		}
		//cancel any pending requests (on the manager side)
		cancelRequests();
		if (outgoingHaveMessageAggregator != null) {
			outgoingHaveMessageAggregator.destroy();
		}
		if (peerExchangeItem != null) {
			peerExchangeItem.destroy();
		}
		if (outgoingPieceMessageHandler != null) {
			outgoingPieceMessageHandler.destroy();
		}
		if (connection != null) {	//can be null if close is called within ::<init>::, like when the given port is invalid
			connection.close(reason);
		}
		if (ip_resolver_request != null) {
			ip_resolver_request.cancel();
		}
		removeAvailability();
		changePeerState(PEPeer.DISCONNECTED);
		if (Logger.isEnabled())
			Logger.log(new LogEvent(this, LOGID, "Peer connection closed: " + reason));
		if (!externally_closed) {	//if closed internally, notify manager, otherwise we assume it already knows
			manager.peerConnectionClosed(this, connect_failed, network_failure);
		}
		setPriorityConnection(false);
		/*
		 *	all managed references should have been removed by now
		 *	add to recently disconnected list and null some stuff to make the object lighter
		 */
		outgoingHaveMessageAggregator = null;
		peerExchangeItem = null;
		outgoingPieceMessageHandler = null;
		pluginConnection = null;
		// only save stats if it's worth doing so; ignore rapid connect-disconnects
		if (peerStats.getTotalDataBytesReceived() > 0 || peerStats.getTotalDataBytesSent() > 0 || SystemTime.getCurrentTime() - connectionEstablishedTime > 30 * 1000)
			recentlyDisconnected.put(mySessionID, this);
	}

	public boolean isClosed() {
		return (closing);
	}

	public PEPeerTransport reconnect(boolean tryUDP, boolean tryIPv6) {
		boolean use_tcp = isTCP() && !(tryUDP && getUDPListenPort() > 0);
		if ((use_tcp && getTCPListenPort() > 0) || (!use_tcp && getUDPListenPort() > 0)) {
			boolean use_crypto = getPeerItemIdentity().getHandshakeType() == PeerItemFactory.HANDSHAKE_TYPE_CRYPTO;
			PEPeerTransport new_conn =
				PEPeerTransportFactory.createTransport(
						manager,
						getPeerSource(),
						tryIPv6 && alternativeAddress != null ? alternativeAddress.getHostAddress() : getIp(),
						getTCPListenPort(),
						getUDPListenPort(),
						use_tcp,
						use_crypto,
						crypto_level,
						null);
			// log to both relations
			Logger.log(new LogEvent(new Object[] {this, new_conn},LOGID,"attempting to reconnect, creating new connection"));
			if (new_conn instanceof PEPeerTransportProtocol) {
				PEPeerTransportProtocol pt = (PEPeerTransportProtocol) new_conn;
				pt.checkForReconnect(mySessionID);
				// carry over the alt address in case the reconnect fails and we try again with ipv6
				pt.alternativeAddress = alternativeAddress;
			}
			manager.addPeer(new_conn);
			return (new_conn);
		} else
		{
			return (null);
		}
	}
	/* (non-Javadoc)
	 * @see org.gudy.azureus2.core3.peer.impl.PEPeerTransport#isSafeForReconnect()
	 */
	public boolean isSafeForReconnect() {
		return allowReconnect;
	}

	private void checkForReconnect(HashWrapper oldID) {
		PEPeerTransportProtocol oldTransport = recentlyDisconnected.remove(oldID);
		if (oldTransport != null) {
			Logger.log(new LogEvent(this,LOGID,LogAlert.AT_INFORMATION,"reassociating stats from "+oldTransport+" with this connection"));
			peerSessionID = oldTransport.peerSessionID;
			peerStats = oldTransport.peerStats;
			peerStats.setPeer(this);
			setSnubbed(oldTransport.isSnubbed());
			snubbed = oldTransport.snubbed;
			lastGoodDataTime = oldTransport.lastGoodDataTime;
		}
	}

	private void generateSessionId() {
		SHA1Hasher sha1 = new SHA1Hasher();
		sha1.update(sessionSecret);
		sha1.update(manager.getHash());
		sha1.update(getIp().getBytes());
		mySessionID = sha1.getHash();
		checkForReconnect(mySessionID);
	}

	private void addAvailability() {
		if (!availabilityAdded &&!closing &&peerHavePieces !=null &&currentPeerState ==PEPeer.TRANSFERING) {
			final List peer_listeners_ref =peerListenersCow;
			if (peer_listeners_ref !=null) {
				for (int i =0; i <peer_listeners_ref.size(); i++) {
					final PEPeerListener peerListener =(PEPeerListener) peer_listeners_ref.get(i);
					peerListener.addAvailability(this, peerHavePieces);
				}
				availabilityAdded =true;
			}
		}
	}

	private void removeAvailability() {
		if (availabilityAdded &&peerHavePieces !=null) {
			final List peer_listeners_ref =peerListenersCow;
			if (peer_listeners_ref !=null) {
				for (int i =0; i <peer_listeners_ref.size(); i++) {
					final PEPeerListener peerListener =(PEPeerListener) peer_listeners_ref.get(i);
					peerListener.removeAvailability(this, peerHavePieces);
				}
			}
			availabilityAdded =false;
		}
		peerHavePieces =null;
	}

	protected void sendBTHandshake() {
		if (!handshakeSent) {
			int msgMode = manager.getExtendedMessagingMode();
			Boolean disableAz = (Boolean)connection.getEndpoint().getProperty(AEProxyAddressMapper.MAP_PROPERTY_DISABLE_AZ_MESSAGING);
			if (disableAz != null && disableAz) {
				if (msgMode == BTHandshake.AZ_RESERVED_MODE) {
					msgMode = BTHandshake.LT_RESERVED_MODE;
				}
			}
			BTHandshake handshake =	new BTHandshake(manager.getHash(),
					manager.getPeerId(), msgMode, otherPeerHandshakeVersion);
			if (Logger.isEnabled())
				Logger.log(new LogEvent(this, LOGID,
						"Sending handshake with reserved bytes: " +
							ByteFormatter.nicePrint(handshake.getReserved(), false)));
			connection.getOutgoingMessageQueue().addMessage(handshake, false);
		}
	}

	private void sendLTHandshake() {
		String client_name = (String)ClientIDManagerImpl.getSingleton().getProperty(manager.getHash(), ClientIDGenerator.PR_CLIENT_NAME);
		int localTcpPort = TCPNetworkManager.getSingleton().getTCPListeningPortNumber();
		String tcpPortOverride = COConfigurationManager.getStringParameter("TCP.Listen.Port.Override");
		try {
			localTcpPort = Integer.parseInt(tcpPortOverride);
		} catch (NumberFormatException e)	{} // ignore as invalid input
		
		boolean require_crypto = NetworkManager.getCryptoRequired( manager.getAdapter().getCryptoLevel());
		Map data_dict = new HashMap();
		data_dict.put("v", client_name);
		data_dict.put("p", new Integer(localTcpPort));
		data_dict.put("e", new Long(require_crypto ? 1L : 0L));
		boolean upload_only =
			manager.isSeeding() &&
			!( ENABLE_LAZY_BITFIELD || manualLazyBitfieldControl || manager.isSuperSeedMode());
		// maintain this for any kinds of compatability
		data_dict.put("upload_only", new Long(upload_only? 1L : 0L));
		int metainfo_size;
		if (manager.isPrivateTorrent()) {
			metainfo_size = 0;
		} else {
			metainfo_size = isMetadataDownload?0:manager.getTorrentInfoDictSize();
		}
		if (metainfo_size > 0) {
			data_dict.put("metadata_size", new Integer(metainfo_size));
		}
		NetworkAdmin na = NetworkAdmin.getSingleton();
		if (peerItemIdentity.getNetwork() == AENetworkClassifier.AT_PUBLIC && !na.isSocksActive()) {
			// don't send public address in handshake
			InetAddress defaultV6 = na.hasIPV6Potential(true) ? na.getDefaultPublicAddressV6() : null;
			if (defaultV6 != null) {
				data_dict.put("ipv6",defaultV6.getAddress());
			}
		}
		LTHandshake lt_handshake = new LTHandshake(data_dict, other_peer_bt_lt_ext_version);
		lt_handshake.addDefaultExtensionMappings(true, isMetadataDownload || metainfo_size > 0, true);
		connection.getOutgoingMessageQueue().addMessage(lt_handshake, false);
	}

	private void sendAZHandshake() {
		final Message[] avail_msgs = MessageManager.getSingleton().getRegisteredMessages();
		final String[] avail_ids = new String[avail_msgs.length];
		final byte[] avail_vers = new byte[avail_msgs.length];
		for (int i = 0; i < avail_msgs.length; i++) {
			avail_ids[i] = avail_msgs[i].getID();
			avail_vers[i] = avail_msgs[i].getVersion();
		}
		int local_tcp_port = TCPNetworkManager.getSingleton().getTCPListeningPortNumber();
		int local_udp_port = UDPNetworkManager.getSingleton().getUDPListeningPortNumber();
		int local_udp2_port = UDPNetworkManager.getSingleton().getUDPNonDataListeningPortNumber();
		String tcpPortOverride = COConfigurationManager.getStringParameter("TCP.Listen.Port.Override");
		try {
			local_tcp_port = Integer.parseInt(tcpPortOverride);
		} catch (NumberFormatException e) {} // ignore as invalid input
		boolean require_crypto = NetworkManager.getCryptoRequired(manager.getAdapter().getCryptoLevel());
		/*
		 * we always send the Az-handshake immediately after the BT-handshake, before decoding the
		 * other side's Az-handshake, thus there should be no peerSessionID unless this is a
		 * reconnect
		 */
		if (peerSessionID != null)
			Logger.log(new LogEvent(this, LOGID, LogEvent.LT_INFORMATION,"notifying peer of reconnect attempt"));
		InetAddress defaultV6 = null;
		NetworkAdmin na = NetworkAdmin.getSingleton();
		if (peerItemIdentity.getNetwork() == AENetworkClassifier.AT_PUBLIC && !na.isSocksActive()) {
				// don't send public address in handshake
			defaultV6 = na.hasIPV6Potential(true) ? na.getDefaultPublicAddressV6() : null;
		}
		String peer_name = Constants.AZUREUS_PROTOCOL_NAME;
		if (	peerId[0] == '-' &&
				peerId[1] == 'A' &&
				peerId[2] == 'Z' &&
				peerId[7] == '-') {
			try {
				int version = Integer.parseInt(new String( peerId, 3, 4));
				if (version < 4813) {
					peer_name = Constants.AZUREUS_PROTOCOL_NAME_PRE_4813;
				}
			} catch (Throwable e) {
			}
		}
		AZHandshake az_handshake = new AZHandshake(
				AZPeerIdentityManager.getAZPeerIdentity(),
				mySessionID,
				peerSessionID,
				peer_name,
				Constants.AZUREUS_VERSION,
				local_tcp_port,
				local_udp_port,
				local_udp2_port,
				defaultV6,
				manager.isPrivateTorrent()?0:( isMetadataDownload?0:manager.getTorrentInfoDictSize()),
				avail_ids,
				avail_vers,
				require_crypto ? AZHandshake.HANDSHAKE_TYPE_CRYPTO : AZHandshake.HANDSHAKE_TYPE_PLAIN,
				otherPeerHandshakeVersion,
				manager.isSeeding() && ! (ENABLE_LAZY_BITFIELD || manualLazyBitfieldControl));
		connection.getOutgoingMessageQueue().addMessage(az_handshake, false);
	}

	public int getPeerState() {	return currentPeerState;	}

	public boolean isDownloadPossible() {
		if (!closing &&!effectively_choked_by_other_peer) {
			if (lastNeededUndonePieceChange <piecePicker.getNeededUndonePieceChange()) {
				checkInterested();
				lastNeededUndonePieceChange =piecePicker.getNeededUndonePieceChange();
			}
			if (interested_in_other_peer &&currentPeerState ==PEPeer.TRANSFERING)
				return true;
		}
		return false;
	}

	public int getPercentDoneInThousandNotation() {
		long total_done = getBytesDownloaded();
		return (int)((total_done *1000) /diskManager.getTotalLength());
	}

	public boolean transferAvailable() {
		return (!effectively_choked_by_other_peer && interested_in_other_peer);
	}

	private void printRequestStats() {
		if (SHOW_DISCARD_RATE_STATS) {
			final float discard_perc = (requestsDiscarded * 100F) / ((requestsCompleted + requestsRecovered + requestsDiscarded) * 1F);
			final float discard_perc_end = (requestsDiscardedEndgame * 100F) / ((requestsCompleted + requestsRecovered + requestsDiscardedEndgame) * 1F);
			final float recover_perc = (requestsRecovered * 100F) / ((requestsRecovered + requestsDiscarded) * 1F);
			System.out.println("c="+requestsCompleted+ " d="+requestsDiscarded+ " de="+requestsDiscardedEndgame+ " r="+requestsRecovered+ " dp="+discard_perc+	"% dpe="+discard_perc_end+ "% rp="+recover_perc+ "%");
		}
	}


	/**
	 * Checks if this peer is a seed or not by trivially checking if
	 * thier Have bitflags exisits and shows a number of bits set equal
	 * to the torrent # of pieces (and the torrent # of pieces is >0)
	 */
	private void checkSeed() {
			// seed implicitly means *something* to send (right?)
		if (peerHavePieces !=null && nbPieces >0) {
			setSeed((peerHavePieces.nbSet ==nbPieces));
		} else {
			setSeed(false);
		}
		if (manager.isSeeding() && isSeed()) {
			// peer is a real seed so it's also a relative seed
			relativeSeeding |= RELATIVE_SEEDING_UPLOAD_ONLY_SEED;
		} else if (manager.isSeeding() && (relativeSeeding & RELATIVE_SEEDING_UPLOAD_ONLY_INDICATED) != 0) {
			// peer indicated upload-only, we're seeding so he's a relative seed
			relativeSeeding |= RELATIVE_SEEDING_UPLOAD_ONLY_SEED;
		} else if (peerHavePieces !=null && nbPieces > 0) {
			int piecesDone = manager.getPiecePicker().getNbPiecesDone();
			DiskManagerPiece[] dmPieces = diskManager.getPieces();
			boolean couldBeSeed = true;
			if (!manager.isSeeding() &&	(relativeSeeding & RELATIVE_SEEDING_UPLOAD_ONLY_INDICATED) != 0) {
				/*
				 * peer indicated upload-only, check if we can use any of the data, otherwise flag
				 * as relative seed. Useful to disconnect not-useful pseudo-seeds during downloading
				 */
				for (int i = peerHavePieces.start;i <= peerHavePieces.end;i++) {
						// relative seed if peer doesn't have the piece, we already have it or we don't need it
					couldBeSeed &= !peerHavePieces.flags[i] || dmPieces[i].isDone() || !dmPieces[i].isNeeded();
					if (!couldBeSeed) {
						break;
					}
				}
			} else if (manager.isSeeding() && piecesDone <= peerHavePieces.nbSet) {
					// we're seeding, check if peer has all the data we have (and more), flag as relative seed if so
				for (int i = peerHavePieces.start;i <= peerHavePieces.end;i++) {
						// relative seed if we don't have the piece or we have it and the peer has it too
					couldBeSeed &= !(dmPieces[i].isDone()) || peerHavePieces.flags[i];
					if (!couldBeSeed) {
						break;
					}
				}
			} else {
				couldBeSeed = false;
			}
			if (couldBeSeed) {
				relativeSeeding |= RELATIVE_SEEDING_UPLOAD_ONLY_SEED;
			} else {
				relativeSeeding &= ~RELATIVE_SEEDING_UPLOAD_ONLY_SEED;
			}
		} else{
			relativeSeeding &= ~RELATIVE_SEEDING_UPLOAD_ONLY_SEED;
		}
	}

	public DiskManagerReadRequest request(
		final int 		pieceNumber,
		final int 		pieceOffset,
		final int 		pieceLength,
		final boolean 	returnDuplicates) {
		
		/*
		if (Once.getInstance().getAndIncreaseCount() < 1) {
			Log.d(TAG, "request() is called...");
			new Throwable().printStackTrace();
		}
		//*/
		
		final DiskManagerReadRequest request = manager.createDiskManagerRequest(pieceNumber, pieceOffset, pieceLength);
		if (currentPeerState != TRANSFERING) {
			manager.requestCanceled(request);
			return null;
		}
		boolean added =false;
		try {
			requested_mon.enter();
			if (!requested.contains(request)) {
				if (requested.size() == 0) {
					request.setLatencyTest();
				}
				requested.add(request);
				added = true;
			}
		} finally {
			requested_mon.exit();
		}
		
		if (added) {
			if (isMetadataDownload) {
				if (azMetadataSupported) {
					connection.getOutgoingMessageQueue().addMessage(new AZMetaData(pieceNumber, other_peer_request_version ), false);
				} else {
					connection.getOutgoingMessageQueue().addMessage(new UTMetaData(pieceNumber, other_peer_request_version ), false);
				}
			} else {
				connection.getOutgoingMessageQueue().addMessage(new BTRequest(pieceNumber, pieceOffset, pieceLength, other_peer_request_version ), false);
			}
			_lastPiece = pieceNumber;
			if (DEBUG_FAST) {
				if (really_choked_by_other_peer) {
					System.out.println("Sending allow-fast request for " + pieceNumber + "/" + pieceOffset + "/" + pieceLength + " to " + getIp());
				}
			}
			try {
				recentOutgoingRequestsMon.enter();
				recentOutgoingRequests.put(request, null);
			} finally {
				recentOutgoingRequestsMon.exit();
			}
			return (request);
		} else {
			if (returnDuplicates) {
				return (request);
			} else {
				return (null);
			}
		}
	}

	public int getRequestIndex(
			DiskManagerReadRequest request) {
		try {
			requested_mon.enter();
			return (requested.indexOf( request));
		} finally {
			requested_mon.exit();
		}
	}

	public void sendCancel(DiskManagerReadRequest request) {
		if (currentPeerState != TRANSFERING) return;
		if (hasBeenRequested(request)) {
			removeRequest(request);
		connection.getOutgoingMessageQueue().addMessage(new BTCancel( request.getPieceNumber(), request.getOffset(), request.getLength(), otherPeerCancelVersion ), false);
		}
	}

	private void sendHaveNone() {
		connection.getOutgoingMessageQueue().addMessage(new BTHaveNone(other_peer_have_none_version), false);
	}

	public void sendHave(int pieceNumber) {
		if (currentPeerState != TRANSFERING || pieceNumber == manager.getHiddenPiece()) return;
		//only force if the other peer doesn't have this piece and is not yet interested or we;ve disabled
		// aggregation
		final boolean force =!otherPeerInterestedInMe && peerHavePieces !=null && !peerHavePieces.flags[pieceNumber];
		outgoingHaveMessageAggregator.queueHaveMessage(pieceNumber, force || haveAggregationDisabled);
		checkInterested();
	}

	public void sendChoke() {
		if (currentPeerState != TRANSFERING) return;
		//System.out.println("["+(System.currentTimeMillis()/1000)+"] " +connection + " choked");
		connection.getOutgoingMessageQueue().addMessage(new BTChoke(other_peer_choke_version), false);
		choking_other_peer = true;
		isOptimisticUnchoke = false;
		destroyPieceMessageHandler();
	}

	public void sendUnChoke() {
		if (currentPeerState != TRANSFERING) return;
		//System.out.println("["+(System.currentTimeMillis()/1000)+"] " +connection + " unchoked");
		createPieceMessageHandler();
		choking_other_peer = false;	// set this first as with pseudo peers we can effectively synchronously act
		
		// on the unchoke advice and we don't want that borking with choked still set
		connection.getOutgoingMessageQueue().addMessage(new BTUnchoke(other_peer_unchoke_version), false);
	}

	private void createPieceMessageHandler() {
		if (outgoingPieceMessageHandler == null) {
			outgoingPieceMessageHandler = new OutgoingBTPieceMessageHandler(
				this,
				connection.getOutgoingMessageQueue(),
				new OutgoingBTPieceMessageHandlerAdapter() {
					public void diskRequestCompleted(long bytes) {
						peerStats.diskReadComplete(bytes);
					}
			},
			other_peer_piece_version);
		}
	}

	private void destroyPieceMessageHandler() {
		if (outgoingPieceMessageHandler != null) {
			outgoingPieceMessageHandler.removeAllPieceRequests();
			outgoingPieceMessageHandler.destroy();
			outgoingPieceMessageHandler = null;
		}
	}

	private void sendKeepAlive() {
		if (currentPeerState != TRANSFERING) return;
		if (outgoingHaveMessageAggregator.hasPending()) {
			outgoingHaveMessageAggregator.forceSendOfPending();
		} else {
			connection.getOutgoingMessageQueue().addMessage(new BTKeepAlive(other_peer_keep_alive_version), false);
		}
	}

	private void sendMainlineDHTPort() {
		if (!this.ml_dht_enabled) {return;}
		MainlineDHTProvider provider = getDHTProvider();
		if (provider == null) {return;}
		Message message = new BTDHTPort(provider.getDHTPort());
		connection.getOutgoingMessageQueue().addMessage(message, false);
	}

	/**
	 * Global checkInterested method.
	 * Early-out scan of pieces to determine if the peer is interesting or not.
	 * They're interesting if they have a piece that we Need and isn't Done
	 */
	public void checkInterested() {
		if (closing || peerHavePieces ==null || peerHavePieces.nbSet == 0) {
			return;
		}
		boolean is_interesting = false;
		if (!isDownloadDisabled) {
			if (piecePicker.hasDownloadablePiece()) {
					// there is a piece worth being interested in
				if (!isSeed() && !isRelativeSeed()) {
						// check individually if don't have all
					for (int i = peerHavePieces.start; i <= peerHavePieces.end; i++) {
						if (peerHavePieces.flags[i] && diskManager.isInteresting(i)) {
							is_interesting = true;
							break;
						}
					}
				} else {
					is_interesting = true;
				}
			}
		}
		if (is_interesting && !interested_in_other_peer) {
			connection.getOutgoingMessageQueue().addMessage(new BTInterested(other_peer_interested_version), false);
		} else if (!is_interesting && interested_in_other_peer) {
			connection.getOutgoingMessageQueue().addMessage(new BTUninterested(other_peer_uninterested_version), false);
		}
		interested_in_other_peer = is_interesting || isMetadataDownload;
	}
	/** @deprecated no longer used by CVS code
	 * Checks if a particular piece makes us interested in the peer
	 * @param pieceNumber the piece number that has been received
	 */
	/*
	private void checkInterested(int pieceNumber) {
		if (closing)
			return;
		// Do we need this piece and it's not Done?
		if (!interested_in_other_peer &&diskManager.isInteresting(pieceNumber)) {
		connection.getOutgoingMessageQueue().addMessage(new BTInterested(), false);
			interested_in_other_peer =true;
	}
	}
	 */


	/**
	 * Private method to send the bitfield.
	 */
	private void sendBitField() {
		if (closing) {
			return;
		}
			//In case we're in super seed mode, we don't send our bitfield
		if (manager.isSuperSeedMode()) {
				// trying this to see if it fixes issue with libtorrent (v0.15.9.0)
			sendHaveNone();
			return;
		}
		if (isMetadataDownload) {
			return;
		}
		//create bitfield
		final DirectByteBuffer buffer =DirectByteBufferPool.getBuffer(DirectByteBuffer.AL_MSG, (nbPieces +7) /8);
		final DiskManagerPiece[] pieces =diskManager.getPieces();
		int num_pieces = pieces.length;
		HashSet	lazies 		= null;
		int[]	lazy_haves	= null;
		if (ENABLE_LAZY_BITFIELD || manualLazyBitfieldControl) {
			int bits_in_first_byte = Math.min(num_pieces, 8);
			int last_byte_start_bit = (num_pieces / 8) * 8;
			int bits_in_last_byte = num_pieces - last_byte_start_bit;
			if (bits_in_last_byte == 0) {
				bits_in_last_byte = 8;
				last_byte_start_bit -= 8;
			}
			lazies = new HashSet();
			// one bit from first byte
			int first_byte_entry = rnd.nextInt(bits_in_first_byte);
			if (pieces[first_byte_entry].isDone()) {
				lazies.add(new MutableInteger(first_byte_entry));
			}
			// one bit from last byte
			int last_byte_entry = last_byte_start_bit + rnd.nextInt(bits_in_last_byte);
			if (pieces[last_byte_entry].isDone()) {
				lazies.add(new MutableInteger(last_byte_entry));
			}
			// random others missing
			int other_lazies = rnd.nextInt(16) + 4;
			for (int i = 0; i < other_lazies; i++) {
				int random_entry = rnd.nextInt(num_pieces);
				if (pieces[random_entry].isDone()) {
					lazies.add(new MutableInteger(random_entry));
				}
			}
			int num_lazy = lazies.size();
			if (num_lazy == 0) {
				lazies = null;
			} else
			{
				lazy_haves = new int[num_lazy];
				Iterator it = lazies.iterator();
				for (int i = 0; i < num_lazy; i++) {
					int lazy_have = ((MutableInteger) it.next()).getValue();
					lazy_haves[i] = lazy_have;
				}
				if (num_lazy > 1) {
					for (int i = 0; i < num_lazy; i++) {
						int swap = rnd.nextInt(num_lazy);
						if (swap != i) {
							int temp = lazy_haves[swap];
							lazy_haves[swap] = lazy_haves[i];
							lazy_haves[i] = temp;
						}
					}
				}
			}
		}
		int bToSend = 0;
		int i = 0;
		MutableInteger	mi = new MutableInteger(0);
		int	hidden_piece = manager.getHiddenPiece();
		for (; i <num_pieces; i++) {
			if ((i %8) ==0) {
				bToSend =0;
			}
			bToSend = bToSend << 1;
			if (pieces[i].isDone() && i != hidden_piece) {
				if (lazies != null) {
					mi.setValue(i);
					if (lazies.contains( mi)) {
						// System.out.println("LazySet: " + getIp() + " -> " + i);
					} else {
						bToSend += 1;
					}
				} else {
					bToSend += 1;
				}
			}
			if ((i %8) ==7) {
				buffer.put(DirectByteBuffer.SS_BT, (byte) bToSend);
		}
		}
		if ((i %8) !=0) {
			bToSend = bToSend << (8 - (i % 8));
			buffer.put(DirectByteBuffer.SS_BT, (byte)bToSend);
		}
		buffer.flip(DirectByteBuffer.SS_BT);
		connection.getOutgoingMessageQueue().addMessage(new BTBitfield(buffer, otherPeerBitfieldVersion ), false);
		if (lazy_haves != null) {
			if (manualLazyBitfieldControl) {
				manualLazyHaves = lazy_haves;
			} else {
				sendLazyHaves(lazy_haves, false);
			}
		}
	}

	protected void sendLazyHaves(
		final int[]	lazy_haves,
		boolean		immediate) {
		if (immediate) {
			if (currentPeerState == TRANSFERING) {
				for (int lazy_have: lazy_haves) {
					connection.getOutgoingMessageQueue().addMessage(new BTHave(lazy_have, other_peer_bt_have_version), false);
				}
			}
		} else {
			SimpleTimer.addEvent(
				"LazyHaveSender",
				SystemTime.getCurrentTime() + 1000 + rnd.nextInt(2000),
				new TimerEventPerformer() {
					int	next_have	= 0;
					public void perform(TimerEvent event) {
						if (currentPeerState == TRANSFERING) {
							int lazy_have = lazy_haves[next_have++];
							connection.getOutgoingMessageQueue().addMessage(new BTHave(lazy_have, other_peer_bt_have_version), false);
							if (next_have < lazy_haves.length && currentPeerState == TRANSFERING) {
								SimpleTimer.addEvent("LazyHaveSender", SystemTime.getCurrentTime() + rnd.nextInt(2000), this);
							}
						}
					}
				});
		}
	}

	public byte[] getId() {	return peerId;	}

	public String getIp() {	return ip;	}

	public InetAddress getAlternativeIPv6() { return alternativeAddress; }

	public int getPort() {	return port;	}

	public int getTCPListenPort() {	return tcp_listen_port;	}

	public int getUDPListenPort() {	return udpListenPort;	}

	public int getUDPNonDataListenPort() { return (udpNonDataPort); }

	public String getClient() {	return client;	}

	public boolean isIncoming() {	return incoming;	}

	public boolean isOptimisticUnchoke() {	return isOptimisticUnchoke && !isChokedByMe();	}

	public void setOptimisticUnchoke(boolean is_optimistic) {	isOptimisticUnchoke = is_optimistic;	}

	public PEPeerControl getControl() {	return manager;	}

	public PEPeerManager getManager() {	return manager;	}

	public PEPeerStats getStats() {	return peerStats;	}

	public int[] getPriorityOffsets() {
		return (piece_priority_offsets);
	}

	public boolean requestAllocationStarts(int[]	base_priorities) {
		return (false);
	}

	public void requestAllocationComplete() {
	}
	/**
	 * @return null if no bitfield has been recieved yet
	 * else returns BitFlags indicating what pieces the peer has
	 */
	public BitFlags getAvailable() {
		return peerHavePieces;
	}

	public boolean isPieceAvailable(int pieceNumber) {
		if (peerHavePieces !=null)
			return peerHavePieces.flags[pieceNumber];
		return false;
	}

	public boolean isChokingMe() {	return effectively_choked_by_other_peer;	}

	public boolean isUnchokeOverride() {return really_choked_by_other_peer && !effectively_choked_by_other_peer; }

	public boolean isChokedByMe() {	return choking_other_peer;	}
	/**
	 * @return true if the peer is interesting to us
	 */
	public boolean isInteresting() {	return interested_in_other_peer;	}
	/**
	 * @return true if the peer is interested in what we're offering
	 */
	public boolean isInterested() {	return otherPeerInterestedInMe;	}

	public boolean isSeed() {	return seeding;	}

	public boolean isRelativeSeed() { return (relativeSeeding & RELATIVE_SEEDING_UPLOAD_ONLY_SEED) != 0; }

	private void setSeed(boolean	s) {
		if (seeding != s) {
			seeding	= s;
			PeerExchangerItem pex_item = peerExchangeItem;
			if (pex_item != null && s) {
				pex_item.seedStatusChanged();
			}
		}
	}

	public boolean isSnubbed() {	return snubbed !=0;	}

	public long getSnubbedTime() {
		if (snubbed ==0)
			return 0;
		final long now =SystemTime.getCurrentTime();
		if (now <snubbed)
			snubbed =now -26; // odds are ...
		return now -snubbed;
	}

	public void setSnubbed(boolean b) {
		if (!closing) {
			final long now =SystemTime.getCurrentTime();
			if (!b) {
				if (snubbed !=0) {
					snubbed =0;
					manager.decNbPeersSnubbed();
				}
			} else if (snubbed ==0) {
				snubbed =now;
				manager.incNbPeersSnubbed();
			}
		}
	}

	public void setUploadHint(int spreadTime) {	spreadTimeHint = spreadTime;	}

	public int getUploadHint() {	return spreadTimeHint;	}

	public void setUniqueAnnounce(int _uniquePiece) {	uniquePiece = _uniquePiece;	}

	public int getUniqueAnnounce() {	return uniquePiece;	}

	public Object getData(String key) {
		return (getUserData( key));
	}

	public void setData(String key, Object value) {
		setUserData(key, value);
	}
	/** To retreive arbitrary objects against a peer. */
	public Object getUserData (Object key) {
		try {
			generalMon.enter();
			if (data == null) return null;
			return data.get(key);
		} finally {
			generalMon.exit();
		}
	}
	/** To store arbitrary objects against a peer. */
	public void setUserData (Object key, Object value) {
		try {
			generalMon.enter();
			if (data == null) {
				data = new LightHashMap();
			}
			if (value == null) {
				if (data.containsKey(key)) {
					data.remove(key);
					if (data.size()==0) {
						data = null;
					}
				}
			} else {
				data.put(key, value);
			}
		} finally {
			generalMon.exit();
		}
	}

	public String getIPHostName() {
		if (ip_resolved == null) {
			ip_resolved = ip;
			ip_resolver_request = IPToHostNameResolver.addResolverRequest(
					ip_resolved,
					new IPToHostNameResolverListener() {
						public final void
						IPResolutionComplete(
								String		res,
								boolean		ok) {
							ip_resolved	= res;
						}
					});
		}
		return (ip_resolved);
	}

	private void cancelRequests() {
		if (!closing) {	 //cancel any unsent requests in the queue
			final Message[] type ={new BTRequest(-1, -1, -1, other_peer_request_version)};
			connection.getOutgoingMessageQueue().removeMessagesOfType(type, false);
		}
		if (requested !=null &&requested.size() >0) {
			try {
				requested_mon.enter();
				if (!closing) {	 // may have unchoked us, gotten a request, then choked without filling it - snub them
					// if they actually have data coming in, they'll be unsnubbed as soon as it writes
					final long timeSinceGoodData =getTimeSinceGoodDataReceived();
					if (timeSinceGoodData ==-1 ||timeSinceGoodData >60 *1000)
						setSnubbed(true);
				}
				for (int i = requested.size() - 1; i >= 0; i--) {
					final DiskManagerReadRequest request =(DiskManagerReadRequest) requested.remove(i);
					manager.requestCanceled(request);
				}
			} finally {
				requested_mon.exit();
			}
		}
	}

	public int getMaxNbRequests() {
		return (-1);
	}

	public int getNbRequests() {
		return requested.size();
	}
	/**
	 *
	 * @return	may be null for performance purposes
	 */
	public List getExpiredRequests() {
		List result = null;
		// this is frequently called, hence we operate without a monitor and
		// take the hit of possible exceptions due to concurrent list
		// modification (only out-of-bounds can occur)
		try {
			for (int i =requested.size() -1; i >=0; i--) {
				final DiskManagerReadRequest request = (DiskManagerReadRequest) requested.get(i);
				if (request.isExpired()) {
					if (result == null) {
						result = new ArrayList();
					}
					result.add(request);
				}
			}
			return (result);
		} catch (Throwable e) {
			return result;
		}
	}

	private DiskManagerReadRequest lookupRequest(
		int		piece_number,
		int		piece_offset,
		int		length) {
		try {
			requested_mon.enter();
			for (DiskManagerReadRequest r: requested) {
				if (r.getPieceNumber() == piece_number &&
						r.getOffset()	== piece_offset &&
						r.getLength() == length) {
					return (r);
				}
			}
			return (null);
		} finally {
			requested_mon.exit();
		}
	}

	private boolean	hasBeenRequested(DiskManagerReadRequest request) {
		try {	
			requested_mon.enter();
			return requested.contains(request);
		} finally {	
			requested_mon.exit();
		}
	}

	protected void removeRequest(DiskManagerReadRequest	request) {
		try {
			requested_mon.enter();
			requested.remove(request);
		} finally {
			requested_mon.exit();
		}
		final BTRequest msg = new BTRequest(request.getPieceNumber(), request.getOffset(), request.getLength(), other_peer_request_version);
		connection.getOutgoingMessageQueue().removeMessage(msg, false);
		msg.destroy();
	}

	private void resetRequestsTime(final long now) {
		try {
			requested_mon.enter();
			final int requestedSize =requested.size();
			for (int i =0; i <requestedSize; i++) {
				final DiskManagerReadRequest request =(DiskManagerReadRequest) requested.get(i);
				if (request != null)
					request.resetTime(now);
			}
		} finally {
			requested_mon.exit();
		}
	}

	public String toString() {
		// parg: removed this as we want to see the peer's IP/host, not (e.g.) a SOCKS server address if in use
		//if (connection != null && connection.isConnected()) {
		//	return connection + (isTCP()?" [":"(UDP) [") + client+ "]";
		//}
		return (isIncoming() ? "R: " : "L: ")+ ip + ":" + port + (isTCP()?" [":"(UDP) [") + client+ "]";
	}

	public String getString() {
		return (toString());
	}

	public void doKeepAliveCheck() {
		final long now =SystemTime.getCurrentTime();
		final long wait_time =now -last_message_sent_time;
		if (last_message_sent_time == 0 || wait_time < 0) {
			last_message_sent_time =now; //don't send if brand new connection
			return;
		}
		if (wait_time > 2*60*1000) {	//2min keep-alive timer
			sendKeepAlive();
			last_message_sent_time =now;	//not quite true, but we don't want to queue multiple keep-alives before the first is actually sent
		}
	}

	public void updateAutoUploadPriority(
		Object		key,
		boolean		inc) {
		try {
			generalMon.enter();
				boolean	key_exists = getUserData(key) != null;
				if (inc && !key_exists) {
					uploadPriorityAuto++;
					setUserData(key, "");
				} else if (!inc && key_exists) {
					uploadPriorityAuto--;
					setUserData(key, null);
				}
		} finally {
			generalMon.exit();
		}
	}

	public boolean doTimeoutChecks() {
		//Timeouts for states PEPeerTransport.CONNECTION_PENDING and
		//PEPeerTransport.CONNECTION_CONNECTING are handled by the ConnectDisconnectManager
		//so we don't need to deal with them here.
		if (connection != null) {
			connection.getOutgoingMessageQueue().setPriorityBoost(
				uploadPriorityAuto > 0 ||
				manager.getUploadPriority() > 0 ||
				( enable_upload_bias && !manager.isSeeding()));
		}
		if (fast_extension_enabled) {
			checkAllowedFast();
		}
		final long now =SystemTime.getCurrentTime();
		//make sure we time out stalled connections
		if (connectionState == PEPeerTransport.CONNECTION_FULLY_ESTABLISHED) {
			if (lastMessageReceivedTime >now)
				lastMessageReceivedTime =now;
			if (lastDataMessageReceivedTime >now)
				lastDataMessageReceivedTime =now;
			if (now -lastMessageReceivedTime >5*60*1000
					&&now -lastDataMessageReceivedTime >5*60*1000) { //5min timeout
				// assume this is due to a network failure
				// e.g. something that didn't close the TCP socket properly
				// will attempt reconnect
				closeConnectionInternally("timed out while waiting for messages", false, true);
				return true;
			}
		}
		//ensure we dont get stuck in the handshaking phases
		else if (connectionState == PEPeerTransport.CONNECTION_WAITING_FOR_HANDSHAKE) {
			if (connectionEstablishedTime >now)
				connectionEstablishedTime =now;
			else if (now - connectionEstablishedTime > 3*60*1000) { //3min timeout
				closeConnectionInternally("timed out while waiting for handshake");
				return true;
			}
		}
		return false;
	}

	public void doPerformanceTuningCheck() {
		Transport	transport = connection.getTransport();
		if (transport != null && peerStats != null && outgoingPieceMessageHandler != null) {
			//send speed -based tuning
			final long send_rate = peerStats.getDataSendRate() + peerStats.getProtocolSendRate();
			if (send_rate >= 3125000) {	// 25 Mbit/s
				transport.setTransportMode(Transport.TRANSPORT_MODE_TURBO);
				outgoingPieceMessageHandler.setRequestReadAhead(256);
			}
			else if (send_rate >= 1250000) {	// 10 Mbit/s
				transport.setTransportMode(Transport.TRANSPORT_MODE_TURBO);
				outgoingPieceMessageHandler.setRequestReadAhead(128);
			}
			else if (send_rate >= 125000) {	// 1 Mbit/s
				if (transport.getTransportMode() < Transport.TRANSPORT_MODE_FAST) {
					transport.setTransportMode(Transport.TRANSPORT_MODE_FAST);
				}
				outgoingPieceMessageHandler.setRequestReadAhead(32);
			}
			else if (send_rate >= 62500) {	// 500 Kbit/s
				outgoingPieceMessageHandler.setRequestReadAhead(16);
			}
			else if (send_rate >= 31250) {	// 250 Kbit/s
				outgoingPieceMessageHandler.setRequestReadAhead(8);
			}
			else if (send_rate >= 12500) {	// 100 Kbit/s
				outgoingPieceMessageHandler.setRequestReadAhead(4);
			}
			else {
				outgoingPieceMessageHandler.setRequestReadAhead(2);
			}

			//receive speed -based tuning
			final long receive_rate = peerStats.getDataReceiveRate() + peerStats.getProtocolReceiveRate();
			if (receive_rate >= 1250000) {	// 10 Mbit/s
				transport.setTransportMode(Transport.TRANSPORT_MODE_TURBO);
			}
			else if (receive_rate >= 125000) {	// 1 Mbit/s
				if (transport.getTransportMode() < Transport.TRANSPORT_MODE_FAST) {
					transport.setTransportMode(Transport.TRANSPORT_MODE_FAST);
				}
			}
		}
	}


	public int getConnectionState() {	return connectionState;	}


	public long getTimeSinceLastDataMessageReceived() {
		if (lastDataMessageReceivedTime == -1) {	//never received
			return -1;
		}
		final long now =SystemTime.getCurrentTime();
		if (lastDataMessageReceivedTime >now)
			lastDataMessageReceivedTime =now;	 //time went backwards
		return now -lastDataMessageReceivedTime;
	}

	public long getTimeSinceGoodDataReceived() {
		if (lastGoodDataTime ==-1)
			return -1;	// never received
		final long now =SystemTime.getCurrentTime();
		if (lastGoodDataTime >now)
			lastGoodDataTime =now;	 //time went backwards
		return now -lastGoodDataTime;
	}

	public long getTimeSinceLastDataMessageSent() {
		if (last_data_message_sent_time == -1) {	//never sent
			return -1;
		}
		final long now =SystemTime.getCurrentTime();
		if (last_data_message_sent_time >now)
			last_data_message_sent_time =now;	 //time went backwards
		return now -last_data_message_sent_time;
	}


	public long getTimeSinceConnectionEstablished() {
		if (connectionEstablishedTime == 0) {	//fudge it while the transport is being connected
			return 0;
		}
		final long now =SystemTime.getCurrentTime();
		if (connectionEstablishedTime >now)
			connectionEstablishedTime =now;
		return now -connectionEstablishedTime;
	}

	public int getConsecutiveNoRequestCount() {
		return (consecutive_no_request_count);
	}

	public void setConsecutiveNoRequestCount(int num) {
		consecutive_no_request_count	= num;
	}

	protected void decodeBTHandshake(BTHandshake handshake) {
		
		/*Log.d(TAG, "decodeBTHandshake() is called...");
		Throwable t = new Throwable();
		t.printStackTrace();*/
		
		if (Logger.isEnabled())
			Logger.log(new LogEvent(this, LOGID,
					"Received handshake with reserved bytes: " +
						ByteFormatter.nicePrint(handshake.getReserved(), false)));
		PeerIdentityDataID	my_peer_data_id = manager.getPeerIdentityDataID();
		if (getConnectionState() == CONNECTION_FULLY_ESTABLISHED) {
			handshake.destroy();
			closeConnectionInternally("peer sent another handshake after the initial connect");
		}
		if (!Arrays.equals( manager.getHash(), handshake.getDataHash() )) {
			closeConnectionInternally("handshake has wrong infohash");
			handshake.destroy();
			return;
		}
		peerId = handshake.getPeerId();
		// Decode a client identification string from the given peerID
		this.clientPeerId = this.client = StringInterner.intern(PeerClassifier.getClientDescription(peerId));
		//make sure the client type is not banned
		if (!PeerClassifier.isClientTypeAllowed(client)) {
			closeConnectionInternally(client+ " client type not allowed to connect, banned");
			handshake.destroy();
			return;
		}
		
		//make sure we are not connected to ourselves
		if (Arrays.equals(manager.getPeerId(), peerId)) {
			manager.peerVerifiedAsSelf(this);	//make sure we dont do it again
			closeConnectionInternally("given peer id matches myself");
			handshake.destroy();
			return;
		}
		
		//make sure we are not already connected to this peer
		boolean sameIdentity = PeerIdentityManager.containsIdentity(my_peer_data_id, peerId, getPort());
		boolean sameIP = false;
		//allow loopback connects for co-located proxy-based connections and testing
		boolean same_allowed = COConfigurationManager.getBooleanParameter("Allow Same IP Peers" ) || ip.equals("127.0.0.1");
		if (!same_allowed) {
			if (PeerIdentityManager.containsIPAddress(my_peer_data_id, ip)) {
				sameIP = true;
			}
		}
		if (sameIdentity) {
			boolean close = true;
			if (connection.isLANLocal()) {	 //this new connection is lan-local
				PEPeerTransport existing = manager.getTransportFromIdentity(peerId);
				if (existing != null) {
					String	existing_ip = existing.getIp();
					// normally we don't allow a lan-local to replace a lan-local connection. There is
					// however one exception - where the existing connection comes from the gateway address
					// and therefore actually denotes an effectively non-lan-local connection. Unfortunately
					// we don't have a good way of finding the default gateway, so just go for ending in .1
					if (	!existing.isLANLocal() ||
							(existing_ip.endsWith(".1" ) && !existing_ip.equals( ip))) {	//so drop the existing connection if it is an external (non lan-local) one
						Debug.outNoStack("Dropping existing non-lanlocal peer connection [" +existing+ "] in favour of [" + this + "]");
						manager.removePeer(existing);
						close = false;
					}
				}
			}
			if (close) {
				if (Constants.IS_CVS_VERSION) {
					try {
						List<PEPeer> peers = manager.getPeers();
						String dup_str = "?";
						boolean	dup_ip = false;
						for (PEPeer p: peers) {
							if (p == this) {
								continue;
							}
							byte[] id = p.getId();
							if (Arrays.equals( id, peerId)) {
								dup_ip	= p.getIp().equals( getIp());
								dup_str = p.getClient() + "/" + p.getClientNameFromExtensionHandshake() + "/" + p.getIp() + "/" + p.getPort();
								break;
							}
						}
						String my_str = getClient() + "/" + getIp() + "/" + getPort();
						if (!dup_ip) {
							Debug.outNoStack(
								"Duplicate peer id detected: id=" + ByteFormatter.encodeString(peerId ) + ": this=" + my_str + ",other=" + dup_str);
						}
					} catch (Throwable e) {
					}
				}
				closeConnectionInternally("peer matches already-connected peer id");
				handshake.destroy();
				return;
			}
		}
		if (sameIP) {
			closeConnectionInternally("peer matches already-connected IP address, duplicate connections not allowed");
			handshake.destroy();
			return;
		}
		//make sure we haven't reached our connection limit
		boolean max_reached	= manager.getMaxNewConnectionsAllowed(network) == 0;
		if (	
				max_reached &&
				!manager.doOptimisticDisconnect(isLANLocal(), isPriorityConnection(), network)
		) {
			int[] _con_max = manager.getMaxConnections();
			int con_max = _con_max[0] + _con_max[1];
			final String msg = "too many existing peer connections [p" +
				PeerIdentityManager.getIdentityCount(my_peer_data_id)
				+"/g" +PeerIdentityManager.getTotalIdentityCount()
				+", pmx" +PeerUtils.MAX_CONNECTIONS_PER_TORRENT+ "/gmx"
				+PeerUtils.MAX_CONNECTIONS_TOTAL+"/dmx" + con_max + "]";
			//System.out.println(msg);
			closeConnectionInternally(msg);
			handshake.destroy();
			return;
		}
		try {
			closingMon.enter();
			if (closing) {
				final String msg = "connection already closing";
				closeConnectionInternally(msg);
				handshake.destroy();
				return;
			}
			if (!PeerIdentityManager.addIdentity(my_peer_data_id, peerId, getPort(), ip)) {
				closeConnectionInternally("peer matches already-connected peer id");
				handshake.destroy();
				return;
			}
			identityAdded = true;
		} finally {
			closingMon.exit();
		}
		if (Logger.isEnabled())
			Logger.log(new LogEvent(this, LOGID, "In: has sent their handshake"));
		// Let's store the reserved bits somewhere so they can be examined later (externally).
		handshake_reserved_bytes = handshake.getReserved();
		/*
		 * Waiting until we've received the initiating-end's full handshake, before sending back our own,
		 * really should be the "proper" behavior.	However, classic BT trackers running NAT checking will
		 * only send the first 48 bytes (up to infohash) of the peer handshake, skipping peerid, which means
		 * we'll never get their complete handshake, and thus never reply, which causes the NAT check to fail.
		 * So, we need to send our handshake earlier, after we've verified the infohash.
		 *
			if (incoming) {	//wait until we've received their handshake before sending ours
			sendBTHandshake();
			}
		 */
		this.ml_dht_enabled = (handshake_reserved_bytes[7] & 0x01 ) == 1;
		
		// disable fast if we have per-torrent upload limit as it is non-trivial to enforce for choked fast-start
		// transfers as peer is in the multi-peer upload group (as choked) and in this mode the limit isn't
		// enforced (see http://forum.vuze.com/thread.jspa?threadID=105262)
		fast_extension_enabled =
			BTHandshake.FAST_EXTENSION_ENABLED &&
			manager.getUploadRateLimitBytesPerSecond() == 0 &&
			(handshake_reserved_bytes[7] & 0x04) != 0;
		messagingMode = decideExtensionProtocol(handshake);
		//extended protocol processing
		if (messagingMode == MESSAGING_AZMP) {
			/**
			 * We log when a non-Azureus client claims to support extended messaging...
			 * Obviously other Azureus clients do, so there's no point logging about them!
			 */
			if (Logger.isEnabled() && !client.contains("Azureus")) {
				Logger.log(new LogEvent(this, LOGID, "Handshake claims extended AZ "
						+ "messaging support... enabling AZ mode."));
			}
			// Ignore the handshake setting - wait for the AZHandshake to indicate
			// support instead.
			this.ml_dht_enabled = false;
			Transport transport = connection.getTransport();
			int padding_mode;
			if (transport.isEncrypted()) {
				if (transport.isTCP()) {
					padding_mode = AZMessageEncoder.PADDING_MODE_NORMAL;
				} else {
					padding_mode = AZMessageEncoder.PADDING_MODE_MINIMAL;
				}
			} else {
				padding_mode = AZMessageEncoder.PADDING_MODE_NONE;
			}
			connection.getIncomingMessageQueue().setDecoder(new AZMessageDecoder());
			connection.getOutgoingMessageQueue().setEncoder(new AZMessageEncoder(padding_mode));
			// We will wait until we get the Az handshake before considering the connection
			// initialised.
			this.sendAZHandshake();
			handshake.destroy();
		} else if (messagingMode == MESSAGING_LTEP) {
			if (Logger.isEnabled()) {
				Logger.log(new LogEvent(this, LOGID, "Enabling LT extension protocol support..."));
			}
			connection.getIncomingMessageQueue().setDecoder(new LTMessageDecoder());
			connection.getOutgoingMessageQueue().setEncoder(new LTMessageEncoder(this));
			generateSessionId();
			/**
			 * We don't need to wait for the LT handshake, nor do we require it, nor
			 * does it matter if the LT handshake comes later, nor does it matter if
			 * it we receive it repeatedly. So there - we can initialise the connection
			 * right now. :P
			 */
				// UNLESS (PARG, 2014/9/11) this is a metadata download in which case we MUST
				// receive the LT handshake (with the metadata size in it) before transitioning
				// to active...
			if (!isMetadataDownload) {
				this.initPostConnection(handshake);
			}
			this.sendLTHandshake();
		}
		else {
			this.client = ClientIdentifier.identifyBTOnly(this.clientPeerId, this.handshake_reserved_bytes);
			connection.getIncomingMessageQueue().getDecoder().resumeDecoding();
			this.initPostConnection(handshake);
		}
	}

	private int decideExtensionProtocol(BTHandshake handshake) {
		int messaging_mode = manager.getExtendedMessagingMode();
			// regardless of what they support, if we're just plain bt then so be it
		if (messaging_mode == BTHandshake.BT_RESERVED_MODE) {
			return (MESSAGING_BT_ONLY);
		}
		boolean supports_ltep = (handshake.getReserved()[5] & 16) == 16;
		if (messaging_mode == BTHandshake.LT_RESERVED_MODE) {
			return (supports_ltep?MESSAGING_LTEP:MESSAGING_BT_ONLY);
		}
		boolean supports_azmp = (handshake.getReserved()[0] & 128) == 128;
		if (!supports_azmp) {
			if (supports_ltep) {
				return (MESSAGING_LTEP); // LTEP is supported.
			}
			return (MESSAGING_BT_ONLY); // LTEP isn't supported.
		}
		if (!supports_ltep) {
				// 	Check if the client is misbehaving...
			if (client.contains("Plus!")) {

				if (Logger.isEnabled())
					Logger.log(new LogEvent(this, LOGID, "Handshake mistakingly indicates"
							+ " extended AZ messaging support...ignoring."));
				return MESSAGING_BT_ONLY;
			}
			return (MESSAGING_AZMP);
		}

		boolean enp_major_bit = (handshake.getReserved()[5] & 2) == 2;
		boolean enp_minor_bit = (handshake.getReserved()[5] & 1) == 1;
		// Only enable one of the blocks below.
		String their_ext_preference = ((enp_major_bit == enp_minor_bit) ? "Force " : "Prefer ") + ((enp_major_bit) ? "AZMP" : "LTEP");
		// Force AZMP block.
		String our_ext_preference = "Force AZMP";
		boolean use_azmp = enp_major_bit || enp_minor_bit; // Anything other than Force LTEP, then we force AZMP to be used.
		boolean we_decide = use_azmp;
		// Prefer AZMP block (untested).
		/*
		String our_ext_preference = "Prefer AZMP";
		boolean use_azmp = enp_major_bit; // Requires other client to prefer or force AZMP.
		boolean we_decide = use_azmp && !enp_minor_bit; // We decide only if we are using AZMP and the other client didn't force it.
		*/
		// Prefer LTEP block (untested).
		/*
		String our_ext_preference = "Prefer LTEP";
		boolean use_azmp = enp_major_bit && enp_minor_bit; // Only use it Force AZMP is enabled.
		boolean we_decide = enp_minor_bit && !use_azmp; // We decide only if we are using LTEP and the other client didn't force it.
		*/
		if (Logger.isEnabled()) {
			String msg = "Peer supports both AZMP and LTEP: ";
			msg += "\"" + our_ext_preference + "\"" + (we_decide ? ">" : "<") + ((our_ext_preference.equals(their_ext_preference)) ? "= " : " ");
			msg += "\"" + their_ext_preference + "\" - using " + (use_azmp ? "AZMP" : "LTEP");
			Logger.log(new LogEvent(this, LOGID, msg));
		}
		return (use_azmp?MESSAGING_AZMP:MESSAGING_LTEP);
	}

	protected void decodeLTHandshake(LTHandshake handshake) {
		String lt_handshake_name = handshake.getClientName();
		if (lt_handshake_name != null) {
			this.client_handshake = StringInterner.intern(lt_handshake_name);
			this.client = StringInterner.intern(ClientIdentifier.identifyLTEP(this.clientPeerId, this.client_handshake, this.peerId));
		}
		if (handshake.getTCPListeningPort() > 0 ) {
			// Only use crypto if it was specifically requested. Not sure what the default
			// should be if they haven't indicated...
			Boolean crypto_requested = handshake.isCryptoRequested();
			byte handshake_type = (crypto_requested != null && crypto_requested.booleanValue()) ? PeerItemFactory.HANDSHAKE_TYPE_CRYPTO : PeerItemFactory.HANDSHAKE_TYPE_PLAIN;
			tcp_listen_port = handshake.getTCPListeningPort();
			peerItemIdentity = PeerItemFactory.createPeerItem(
				ip, tcp_listen_port,
				PeerItem.convertSourceID(peerSource),
				handshake_type,
				udpListenPort, // probably none
				crypto_level,
				0
			 );
		}
		if (handshake.isUploadOnly()) {
			relativeSeeding |= RELATIVE_SEEDING_UPLOAD_ONLY_INDICATED;
			checkSeed();
		}
		if (AddressUtils.isGlobalAddressV6(handshake.getIPv6()))
			alternativeAddress = handshake.getIPv6();
		LTMessageEncoder encoder = (LTMessageEncoder)connection.getOutgoingMessageQueue().getEncoder();
		encoder.updateSupportedExtensions(handshake.getExtensionMapping());
		this.ut_pex_enabled = encoder.supportsUTPEX();
		if (isMetadataDownload) {
			if (encoder.supportsUTMetaData()) {
				int	mds = handshake.getMetadataSize();
				if (mds > 0) {
					spoofMDAvailability(mds);
				}
			}
			if (currentPeerState != PEPeer.TRANSFERING) {
					// this was deferred in the BT handshake decode for metadata downloads...
				initPostConnection(null);
			}
		}
		/**
		 * Grr... this is one thing which I'm sure I had figured out much better than it is here...
		 * Basically, we "initialise" the connection at the BT handshake stage, because the LT handshake
		 * is mandatory or required to come first (unlike the AZ one).
		 *
		 * But when we receive an LT handshake, we have to "initialise" it like we did previously, because
		 * we may have to set the internals up to indicate if PEX is supported.
		 *
		 * I'm not entirely sure this method is meant to be called more than once, and I'm less convinced
		 * that it's safe to do it repeatedly over the lifetime of a properly-initialised, actually-doing-stuff
		 * connection... but I'll worry about that later.
		 */
		this.doPostHandshakeProcessing();
		handshake.destroy();
	}

	protected void decodeAZHandshake(AZHandshake handshake) {
		if (getConnectionState() == CONNECTION_FULLY_ESTABLISHED) {
			handshake.destroy();
			closeConnectionInternally("peer sent another az-handshake after the intial connect");
		}
		this.client_handshake = StringInterner.intern(handshake.getClient());
		this.client_handshake_version = StringInterner.intern(handshake.getClientVersion());
		this.client = StringInterner.intern(ClientIdentifier.identifyAZMP(this.clientPeerId, client_handshake, client_handshake_version, this.peerId));
		if (handshake.getTCPListenPort() > 0) { // use the ports given in handshake
			tcp_listen_port = handshake.getTCPListenPort();
			udpListenPort = handshake.getUDPListenPort();
			udpNonDataPort = handshake.getUDPNonDataListenPort();
			final byte type = handshake.getHandshakeType() == AZHandshake.HANDSHAKE_TYPE_CRYPTO ? PeerItemFactory.HANDSHAKE_TYPE_CRYPTO : PeerItemFactory.HANDSHAKE_TYPE_PLAIN;
			// remake the id using the peer's remote listen port instead of
			// their random local port
			peerItemIdentity = PeerItemFactory.createPeerItem(ip, tcp_listen_port, PeerItem.convertSourceID(peerSource), type, udpListenPort, crypto_level, 0);
		}
		if (AddressUtils.isGlobalAddressV6(handshake.getIPv6()))
			alternativeAddress = handshake.getIPv6();

		if (handshake.getReconnectSessionID() != null) {
			if (Logger.isEnabled()) {
				Logger.log(new LogEvent(this, LOGID, LogEvent.LT_INFORMATION, "received reconnect request ID: "+handshake.getReconnectSessionID().toBase32String()));
			}
			checkForReconnect(handshake.getReconnectSessionID());
		}
		if (handshake.getRemoteSessionID() != null)
			peerSessionID = handshake.getRemoteSessionID();
		if (handshake.isUploadOnly()) {
			relativeSeeding |= RELATIVE_SEEDING_UPLOAD_ONLY_INDICATED;
			checkSeed();
		}

		String[] supported_message_ids = handshake.getMessageIDs();
		byte[] supported_message_versions = handshake.getMessageVersions();
		//find mutually available message types
		final ArrayList messages = new ArrayList();
		for (int i = 0; i < handshake.getMessageIDs().length; i++) {
			Message msg = MessageManager.getSingleton().lookupMessage(supported_message_ids[i]);
			if (msg != null) { // mutual support!
				messages.add(msg);
				String id = msg.getID();
				byte supported_version = supported_message_versions[i];
				// we can use == safely
				if (id == BTMessage.ID_BT_BITFIELD)
					otherPeerBitfieldVersion = supported_version;
				else if (id == BTMessage.ID_BT_CANCEL)
					otherPeerCancelVersion = supported_version;
				else if (id == BTMessage.ID_BT_CHOKE)
					other_peer_choke_version = supported_version;
				else if (id == BTMessage.ID_BT_HANDSHAKE)
					otherPeerHandshakeVersion = supported_version;
				else if (id == BTMessage.ID_BT_HAVE)
					other_peer_bt_have_version = supported_version;
				else if (id == BTMessage.ID_BT_INTERESTED)
					other_peer_interested_version = supported_version;
				else if (id == BTMessage.ID_BT_KEEP_ALIVE)
					other_peer_keep_alive_version = supported_version;
				else if (id == BTMessage.ID_BT_PIECE)
					other_peer_piece_version = supported_version;
				else if (id == BTMessage.ID_BT_UNCHOKE)
					other_peer_unchoke_version = supported_version;
				else if (id == BTMessage.ID_BT_UNINTERESTED)
					other_peer_uninterested_version = supported_version;
				else if (id == BTMessage.ID_BT_REQUEST)
					other_peer_request_version = supported_version;
				else if (id == BTMessage.ID_BT_SUGGEST_PIECE)
					other_peer_suggest_piece_version = supported_version;
				else if (id == BTMessage.ID_BT_HAVE_ALL)
					other_peer_have_all_version = supported_version;
				else if (id == BTMessage.ID_BT_HAVE_NONE)
					other_peer_have_none_version = supported_version;
				else if (id == BTMessage.ID_BT_REJECT_REQUEST)
					other_peer_reject_request_version = supported_version;
				else if (id == BTMessage.ID_BT_ALLOWED_FAST)
					other_peer_allowed_fast_version = supported_version;
				else if (id == AZMessage.ID_AZ_PEER_EXCHANGE)
					other_peer_pex_version = supported_version;
				else if (id == AZMessage.ID_AZ_REQUEST_HINT)
					other_peer_az_request_hint_version = supported_version;
				else if (id == AZMessage.ID_AZ_HAVE)
					other_peer_az_have_version = supported_version;
				else if (id == AZMessage.ID_AZ_BAD_PIECE)
					other_peer_az_bad_piece_version = supported_version;
				else if (id == AZMessage.ID_AZ_STAT_REQUEST)
					other_peer_az_stats_request_version = supported_version;
				else if (id == AZMessage.ID_AZ_STAT_REPLY)
					other_peer_az_stats_reply_version = supported_version;
				else if (id == AZMessage.ID_AZ_METADATA)
					other_peer_az_metadata_version = supported_version;
				else if (id == BTMessage.ID_BT_DHT_PORT)
					this.ml_dht_enabled = true;
				else
				{
					// we expect unmatched ones here at the moment as we're not
					// dealing with them yet or they don't make sense.
					// for example AZVER
				}
			}
		}
		if (isMetadataDownload) {
			int	 mds = handshake.getMetadataSize();
			if (mds > 0) {
					manager.setTorrentInfoDictSize(mds);
			}
		}
		supported_messages = (Message[]) messages.toArray(new Message[messages.size()]);
		if (outgoingPieceMessageHandler != null) {
			outgoingPieceMessageHandler.setPieceVersion(other_peer_piece_version);
		}
		if (outgoingHaveMessageAggregator != null) {
			outgoingHaveMessageAggregator.setHaveVersion(other_peer_bt_have_version, other_peer_az_have_version);
		}
		this.initPostConnection(handshake);
	}

		private void spoofMDAvailability(
			int		mds ) {
			int	md_pieces = (mds + 16*1024 - 1)/(16*1024);
			manager.setTorrentInfoDictSize(mds);
			BitFlags tempHavePieces = new BitFlags(nbPieces);
			for ( int i=0;i<md_pieces;i++) {
				tempHavePieces.set(i);
			}
			peerHavePieces = tempHavePieces;
			addAvailability();
			really_choked_by_other_peer = false;
			calculatePiecePriorities();
		}

		private void initPostConnection(Message handshake) {
		changePeerState(PEPeer.TRANSFERING);
		connectionState = PEPeerTransport.CONNECTION_FULLY_ESTABLISHED;
		sendBitField();
		if (handshake != null) {
			handshake.destroy();
		}
		addAvailability();
		sendMainlineDHTPort();
		if (isMetadataDownload) {
			interested_in_other_peer = true;
		}
		}

	protected void decodeHaveAll(
		BTHaveAll have_all) {
		have_all.destroy();
		received_bitfield = true;
		if (isMetadataDownload) {
			return;
		}
		try {
			closingMon.enter();
			if (!closing) {
				final BitFlags tempHavePieces;
				if (peerHavePieces ==null) {
					tempHavePieces = new BitFlags(nbPieces);
				} else {
					tempHavePieces = peerHavePieces;
					removeAvailability();
				}
				tempHavePieces.setAll();
				for (int i=0; i<nbPieces; i++) {
					manager.updateSuperSeedPiece(this, i);
				}
				peerHavePieces = tempHavePieces;
				addAvailability();
				checkSeed();
				checkInterested();
			}
		} finally {
			closingMon.exit();
		}
	}

	protected void decodeHaveNone(
		BTHaveNone have_none) {
		have_none.destroy();
		received_bitfield = true;
		if (isMetadataDownload) {
			return;
		}
		try {
			closingMon.enter();
			if (!closing) {
				final BitFlags tempHavePieces;
				if (peerHavePieces ==null) {
					tempHavePieces = new BitFlags(nbPieces);
				} else {
					tempHavePieces = peerHavePieces;
					removeAvailability();
				}
				tempHavePieces.clear();
				peerHavePieces = tempHavePieces;
					//some of these kinda pointless but whatever
				addAvailability();
				checkSeed();
				checkInterested();
				checkFast(tempHavePieces);
			}
		} finally {
			closingMon.exit();
		}
	}

	protected void decodeBitfield(BTBitfield bitfield) {
		received_bitfield = true;
		if (isMetadataDownload) {
			bitfield.destroy();
			return;
		}
		final DirectByteBuffer field =bitfield.getBitfield();
		final byte[] dataf =new byte[(nbPieces +7) /8];
		if (field.remaining( DirectByteBuffer.SS_PEER ) < dataf.length) {
			final String error = toString() + " has sent invalid Bitfield: too short [" +field.remaining(DirectByteBuffer.SS_PEER)+ "<" +dataf.length+ "]";
			Debug.out(error);
			if (Logger.isEnabled())
				Logger.log(new LogEvent(this, LOGID, LogEvent.LT_ERROR, error ));
			bitfield.destroy();
			return;
		}
		field.get(DirectByteBuffer.SS_PEER, dataf);
		try {
			closingMon.enter();
			if (closing)
				bitfield.destroy();
			else
			{
				final BitFlags tempHavePieces;
				if (peerHavePieces ==null) {
					tempHavePieces =new BitFlags(nbPieces);
				} else
				{
					tempHavePieces =peerHavePieces;
					removeAvailability();
				}
				for (int i =0; i <nbPieces; i++) {
					final int index =i /8;
					final int bit =7 -(i %8);
					final byte bData =dataf[index];
					final byte b =(byte) (bData >>bit);
					if ((b &0x01) ==1) {
						tempHavePieces.set(i);
						manager.updateSuperSeedPiece(this,i);
					}
				}
				bitfield.destroy();
				peerHavePieces =tempHavePieces;
				addAvailability();
				checkSeed();
				checkInterested();
				checkFast(tempHavePieces);
			}
		}
		finally{
			closingMon.exit();
		}
	}

	public void setSuspendedLazyBitFieldEnabled(
		boolean	enable) {
		manualLazyBitfieldControl	= enable;
		if (!enable) {
			int[] pending = manualLazyHaves;
			manualLazyHaves = null;
			if (pending != null) {
				sendLazyHaves(pending, true);
			}
		}
	}

	protected void decodeMainlineDHTPort(BTDHTPort port) {
		int i_port = port.getDHTPort();
		port.destroy();
		if (!this.ml_dht_enabled) {return;}
		MainlineDHTProvider provider = getDHTProvider();
		if (provider == null) {return;}
		if (network == AENetworkClassifier.AT_PUBLIC) {
			try {provider.notifyOfIncomingPort(getIp(), i_port);}
			catch (Throwable t) {Debug.printStackTrace(t);}
		}
	}

	protected void decodeChoke(BTChoke choke) {
		choke.destroy();
		if (isMetadataDownload) {
			return;
		}
		if (!really_choked_by_other_peer) {
			really_choked_by_other_peer = true;
			calculatePiecePriorities();
			cancelRequests();
		}
	}

	protected void decodeUnchoke(BTUnchoke unchoke) {
		unchoke.destroy();
		if (isMetadataDownload) {
			return;
		}
		if (really_choked_by_other_peer) {
			really_choked_by_other_peer = false;
			calculatePiecePriorities();
		}
	}

	protected void decodeInterested(
		BTInterested interested ) {
		interested.destroy();
			// Don't allow known seeds to be interested in us
		otherPeerInterestedInMe = !(isSeed() || isRelativeSeed());
		if (	otherPeerInterestedInMe &&
				!isUploadDisabled &&
				fast_unchoke_new_peers &&
				isChokedByMe() &&
				getData("fast_unchoke_done" ) == null) {
			setData("fast_unchoke_done", "");
			sendUnChoke();
		}
	}

	protected void decodeUninterested(BTUninterested uninterested) {
		uninterested.destroy();
		otherPeerInterestedInMe = false;
		//force send any pending haves in case one of them would make the other peer interested again
		if (outgoingHaveMessageAggregator != null) {
			outgoingHaveMessageAggregator.forceSendOfPending();
		}
	}


	protected void decodeHave(BTHave have) {
		final int pieceNumber =have.getPieceNumber();
		have.destroy();
		if (isMetadataDownload) {
			return;
		}
		if ((pieceNumber >=nbPieces) ||(pieceNumber <0)) {
			closeConnectionInternally("invalid pieceNumber: " +pieceNumber);
			return;
		}
		if (closing)
			return;
		if (peerHavePieces ==null)
			peerHavePieces =new BitFlags(nbPieces);
		if (!peerHavePieces.flags[pieceNumber]) {
			if (!interested_in_other_peer &&diskManager.isInteresting(pieceNumber)&&!isDownloadDisabled) {
				connection.getOutgoingMessageQueue().addMessage(new BTInterested(other_peer_interested_version), false);
				interested_in_other_peer =true;
			}
			peerHavePieces.set(pieceNumber);
			final int pieceLength =manager.getPieceLength(pieceNumber);
			manager.havePiece(pieceNumber, pieceLength, this);
			checkSeed(); // maybe a seed using lazy bitfield, or suddenly became a seed;
			if (otherPeerInterestedInMe) {
				if (isSeed() || isRelativeSeed()) {
					otherPeerInterestedInMe = false; // never consider seeds interested
				}
			}
			peerStats.hasNewPiece(pieceLength);
		}
	}

	protected void decodeAZHave(AZHave have) {
		final int[] pieceNumbers = have.getPieceNumbers();
		have.destroy();
		if (closing) {
			return;
		}
		if (peerHavePieces == null) {
			peerHavePieces = new BitFlags(nbPieces);
		}
		boolean	sendInterested = false;
		boolean	new_have		= false;
		for (int i=0;i<pieceNumbers.length;i++) {
			int pieceNumber = pieceNumbers[i];
			if ((pieceNumber >= nbPieces) ||(pieceNumber <0)) {
				closeConnectionInternally("invalid pieceNumber: " +pieceNumber);
				return;
			}
			if (!peerHavePieces.flags[pieceNumber]) {
				new_have	= true;
				if (	!(sendInterested || interested_in_other_peer || isDownloadDisabled) &&
						diskManager.isInteresting(pieceNumber)) {
					sendInterested = true;
				}
				peerHavePieces.set(pieceNumber);
				final int pieceLength =manager.getPieceLength(pieceNumber);
				manager.havePiece(pieceNumber, pieceLength, this);
				peerStats.hasNewPiece(pieceLength);
			}
		}
		
		if (new_have) {
			checkSeed(); // maybe a seed using lazy bitfield, or suddenly became a seed;
			if (otherPeerInterestedInMe) {
				if (isSeed() || isRelativeSeed()) {
					otherPeerInterestedInMe = false; // never consider seeds interested
				}
			}
		}
		
		if (sendInterested) {
			connection.getOutgoingMessageQueue().addMessage(new BTInterested(other_peer_interested_version), false);
			interested_in_other_peer = true;
		}
	}

	protected long getBytesDownloaded() {
		if (peerHavePieces ==null ||peerHavePieces.flags.length ==0)
			return 0;
		final long	total_done;
		if (peerHavePieces.flags[nbPieces-1]) {
			total_done = ((long)(peerHavePieces.nbSet -1) *diskManager.getPieceLength()) +diskManager.getPieceLength(nbPieces -1);
		} else {
			total_done =(long)peerHavePieces.nbSet *diskManager.getPieceLength();
		}
		return ( Math.min( total_done, diskManager.getTotalLength()));
	}

	public long getBytesRemaining() {
		return ( diskManager.getTotalLength() - getBytesDownloaded());
	}

	public void sendBadPiece(int piece_number) {
			if (badPieceSupported) {
				AZBadPiece	bp = new AZBadPiece(piece_number, other_peer_az_bad_piece_version);
				connection.getOutgoingMessageQueue().addMessage(bp, false);
		}
	}

	protected void decodeAZBadPiece(AZBadPiece bad_piece ) {
		final int piece_number = bad_piece.getPieceNumber();
		bad_piece.destroy();
		manager.badPieceReported(this, piece_number);
	}

	public void sendStatsRequest(
			Map		request ) {
			if (statsRequestSupported) {
				AZStatRequest	sr = new AZStatRequest(request, other_peer_az_stats_request_version);
				connection.getOutgoingMessageQueue().addMessage(sr, false);
		}
	}

	protected void decodeAZStatsRequest(
		AZStatRequest request ) {
		Map req = request.getRequest();
		request.destroy();
		manager.statsRequest(this, req);
	}

	public void sendStatsReply(
			Map		reply ) {
			if (statsReplySupported) {
				AZStatReply	sr = new AZStatReply(reply, other_peer_az_stats_reply_version);
				connection.getOutgoingMessageQueue().addMessage(sr, false);
		}
	}

	protected void decodeAZStatsReply(
		AZStatReply reply ) {
		Map rep = reply.getReply();
		reply.destroy();
		manager.statsReply(this, rep);
	}

	protected void decodeRequest(BTRequest request) {
		final int number = request.getPieceNumber();
		final int offset = request.getPieceOffset();
		final int length = request.getLength();
		request.destroy();
		if (!manager.validateReadRequest( this, number, offset, length )) {
			closeConnectionInternally("request for piece #" + number + ":" + offset + "->" + (offset + length -1) + " is an invalid request");
			return;
		}
		if (manager.getHiddenPiece() == number) {
			closeConnectionInternally("request for piece #" + number + " is invalid as piece is hidden");
			return;
		}
		boolean	request_ok = !isUploadDisabled;
		if (request_ok) {
			if (choking_other_peer) {
				try {
					generalMon.enter();
					int[][] pieces = (int[][])getUserData(KEY_ALLOWED_FAST_SENT);
					if (pieces != null) {
						for (int i=0;i<pieces.length;i++) {
							if (pieces[i][0] == number) {
								if (pieces[i][1] >= length) {
									 pieces[i][1] -= length;
									 if (DEBUG_FAST) {
										 System.out.println("Permitting fast-allowed request for " + number + "/" + offset + "/" + length + " to " + getIp());
									 }
									 request_ok = true;
									 createPieceMessageHandler();
									 break;
								} else {
									manager.reportBadFastExtensionUse(this);
								}
							}
						}
					}
				} finally {
					generalMon.exit();
				}
			} else {
				request_ok = true;
			}
		}
		if (request_ok) {
			if (outgoingPieceMessageHandler == null || !outgoingPieceMessageHandler.addPieceRequest( number, offset, length)) {
				sendRejectRequest(number, offset, length);
			}
			allowReconnect = true;
		} else {
			if (Logger.isEnabled())
				Logger.log(new LogEvent(this, LOGID, "decodeRequest(): peer request for piece #"
						+ number + ":" + offset + "->" + (offset + length -1)
						+ " ignored as peer is currently choked."));
			sendRejectRequest(number, offset, length);
		}
	}

	public void sendRejectRequest(
		DiskManagerReadRequest	request) {
		sendRejectRequest( request.getPieceNumber(), request.getOffset(), request.getLength());
	}

	private void sendRejectRequest(
		int		number,
		int		offset,
		int		length) {
		if (fast_extension_enabled && !closing) {
			// System.out.println("Sending reject request " + number + "/" + offset + "/" + length + " to " + getIp());
			BTRejectRequest	reject = new BTRejectRequest(number, offset, length, other_peer_reject_request_version);
				connection.getOutgoingMessageQueue().addMessage(reject, false);
		}
	}

	protected void decodePiece(BTPiece piece) {
		
		final int pieceNumber = piece.getPieceNumber();
		final int offset = piece.getPieceOffset();
		final DirectByteBuffer payload = piece.getPieceData();
		final int length = payload.remaining(DirectByteBuffer.SS_PEER);
		/*
		if (AEDiagnostics.CHECK_DUMMY_FILE_DATA) {
			int pos = payload.position(DirectByteBuffer.SS_PEER);
			long	off = ((long)number) * getControl().getPieceLength(0) + offset;
			for (int i=0;i<length;i++) {
			byte	v = payload.get(DirectByteBuffer.SS_PEER);
			if ((byte)off != v) {
				System.out.println("piece: read is bad at " + off + ": expected = " + (byte)off + ", actual = " + v);
				break;
			}
			off++;
			}
			payload.position(DirectByteBuffer.SS_PEER, pos);
		}
		 */
		final Object error_msg =
			new Object() {
			public final String
			toString() {
				return ("decodePiece(): Peer has sent piece #" + pieceNumber + ":" + offset + "->"	+ (offset + length -1) + ", ");
			}
		};
		if (!manager.validatePieceReply( this, pieceNumber, offset, payload )) {
			peerStats.bytesDiscarded(length);
			manager.discarded(this, length);
			requestsDiscarded++;
			printRequestStats();
			piece.destroy();
			if (Logger.isEnabled())
				Logger.log(new LogEvent(this, LOGID, LogEvent.LT_ERROR,
						error_msg
						+"but piece block discarded as invalid."));
			return;
		}
		if (DEBUG_FAST) {
			if (fast_extension_enabled && really_choked_by_other_peer) {
				System.out.println("Received allow-fast piece for " + pieceNumber + "/" + offset + "/" + length + " from " + getIp());
			}
		}
		final DiskManagerReadRequest existing_request = lookupRequest(pieceNumber, offset, length);
		boolean piece_error = true;
		if (existing_request != null) {	//from active request
			if (existing_request.isLatencyTest()) {
				long latency = SystemTime.getMonotonousTime() - existing_request.getTimeSent();
				if (latency > 0) {
					requestLatency = latency;
				}
			}
			removeRequest(existing_request);
			final long now =SystemTime.getCurrentTime();
			resetRequestsTime(now);
			if (manager.isWritten( pieceNumber, offset )) {	//oops, looks like this block has already been written
				peerStats.bytesDiscarded(length);
				manager.discarded(this, length);
				if (manager.isInEndGameMode()) {	//we're probably in end-game mode then
					if (lastGoodDataTime !=-1 &&now -lastGoodDataTime <=60 *1000)
						setSnubbed(false);
					lastGoodDataTime =now;
					requestsDiscardedEndgame++;
					if (Logger.isEnabled())
						Logger.log(new LogEvent(this, LogIDs.PIECES, LogEvent.LT_INFORMATION,
								error_msg
								+"but piece block ignored as already written in end-game mode."));
				}
				else {
					// if they're not snubbed, then most likely this peer got a re-request after some other peer
					// snubbed themselves, and the slow peer finially finished the piece, but before this peer did
					// so give credit to this peer anyway for having delivered a block at this time
					if (!isSnubbed())
						lastGoodDataTime =now;
					if (Logger.isEnabled())
						Logger.log(new LogEvent(this, LogIDs.PIECES, LogEvent.LT_WARNING,
								error_msg
								+"but piece block discarded as already written."));
					requestsDiscarded++;
				}
				printRequestStats();
			}
			else {	//successfully received block!
				manager.writeBlock( pieceNumber, offset, payload, this, false);
				if (lastGoodDataTime !=-1 &&now -lastGoodDataTime <=60 *1000)
					setSnubbed(false);
				lastGoodDataTime =now;
				requestsCompleted++;
				piece_error = false;	//dont destroy message, as we've passed the payload on to the disk manager for writing
			}
		}
		else {	//initial request may have already expired, but check if we can use the data anyway
			if (!manager.isWritten( pieceNumber, offset )) {
				final boolean ever_requested;
				DiskManagerReadRequest request = manager.createDiskManagerRequest(pieceNumber, offset, length);
				try {	recentOutgoingRequestsMon.enter();
				ever_requested = recentOutgoingRequests.containsKey(request);
				}
				finally{	recentOutgoingRequestsMon.exit();	}
				if (ever_requested) { //security-measure: we dont want to be accepting any ol' random block
					manager.writeBlock( pieceNumber, offset, payload, this, true);
					final long now =SystemTime.getCurrentTime();
					if (lastGoodDataTime !=-1 &&now -lastGoodDataTime <=60 *1000)
						setSnubbed(false);
					resetRequestsTime(now);
					lastGoodDataTime =now;
					requestsRecovered++;
					printRequestStats();
					piece_error = false;	//dont destroy message, as we've passed the payload on to the disk manager for writing
					if (Logger.isEnabled())
						Logger.log(new LogEvent(this, LogIDs.PIECES, LogEvent.LT_INFORMATION,
								error_msg
								+"expired piece block data recovered as useful."));
				}
				else {
					System.out.println("[" +client+ "]" +error_msg + "but expired piece block discarded as never requested.");
					peerStats.bytesDiscarded(length);
					manager.discarded(this, length);
					requestsDiscarded++;
					printRequestStats();
					if (Logger.isEnabled())
						Logger.log(new LogEvent(this, LogIDs.PIECES, LogEvent.LT_ERROR,
								error_msg
								+"but expired piece block discarded as never requested."));
				}
			}
			else {
				peerStats.bytesDiscarded(length);
				manager.discarded(this, length);
				requestsDiscarded++;
				printRequestStats();
				if (Logger.isEnabled())
					Logger.log(new LogEvent(this, LogIDs.PIECES, LogEvent.LT_WARNING,
							error_msg
							+"but expired piece block discarded as already written."));
			}
		}
		if (piece_error)
			piece.destroy();
		else
			allowReconnect = true;
	}

	protected void decodeCancel(
		BTCancel cancel ) {
		int number = cancel.getPieceNumber();
		int offset = cancel.getPieceOffset();
		int length = cancel.getLength();
		cancel.destroy();
		if (outgoingPieceMessageHandler != null)
			outgoingPieceMessageHandler.removePieceRequest(number, offset, length);
	}

	protected void decodeRejectRequest(BTRejectRequest reject) {
		
		int number = reject.getPieceNumber();
		int offset = reject.getPieceOffset();
		int length = reject.getLength();
		reject.destroy();
		final DiskManagerReadRequest request = manager.createDiskManagerRequest(number, offset, length);
		if (hasBeenRequested( request)) {
			removeRequest(request);
			manager.requestCanceled(request);
			// if fast-allowed block rejected, remove from set so we don't re-request it
			try {
				generalMon.enter();
				List<Integer> pieces = (List<Integer>)getUserData(KEY_ALLOWED_FAST_RECEIVED);
				if (pieces != null) {
					pieces.remove(new Integer( number));
					if (pieces.size() == 0) {
						setUserData(KEY_ALLOWED_FAST_RECEIVED, null);
					}
				}
				int[]	priorities = piece_priority_offsets;
				if (priorities != null) {
					priorities[number] = Integer.MIN_VALUE;
				}
				calculatePiecePriorities();
			} finally {
				generalMon.exit();
			}
		}
	}

	private void decodeAllowedFast(
		BTAllowedFast	allowed ) {
		int	piece = allowed.getPieceNumber();
		allowed.destroy();
		if (piecePicker.getNbPiecesDone() > ALLOWED_FAST_OTHER_PEER_PIECE_MAX) {
				// we have too many pieces already, ignore
			return;
		}
		if (DEBUG_FAST) {
			System.out.println("Received allow-fast " + piece + " from " + getIp());
		}
		try {
			generalMon.enter();
			List<Integer> pieces = (List<Integer>)getUserData(KEY_ALLOWED_FAST_RECEIVED);
			if (pieces == null) {
				pieces = new ArrayList<Integer>(ALLOWED_FAST_OTHER_PEER_PIECE_MAX);
				setUserData(KEY_ALLOWED_FAST_RECEIVED, pieces);
			}
			if (pieces.size() < ALLOWED_FAST_OTHER_PEER_PIECE_MAX * 2) {
				Integer i = new Integer(piece);
				if (!pieces.contains(i) && i >=0 && i < nbPieces) {
					pieces.add(i);
					calculatePiecePriorities();
				}
			}
		} finally {
			generalMon.exit();
		}
	}

	private void sendAllowFast(
		int		number) {
		if (fast_extension_enabled) {
			if (DEBUG_FAST) {
				System.out.println("Sending allow-fast " + number + " to " + getIp());
			}
			BTAllowedFast	af = new BTAllowedFast(number, other_peer_allowed_fast_version);
				connection.getOutgoingMessageQueue().addMessage(af, false);
		}
	}

	protected void calculatePiecePriorities() {
		try {
			generalMon.enter();
			if (really_choked_by_other_peer) {
				List<Integer> pieces = (List<Integer>)getUserData(KEY_ALLOWED_FAST_RECEIVED);
				if (pieces == null) {
					effectively_choked_by_other_peer = true;
					piece_priority_offsets	= null;
				} else {
					int[]	priorities = piece_priority_offsets;
					if (priorities == null) {
						priorities = new int[nbPieces];
						Arrays.fill(priorities, Integer.MIN_VALUE);
					}
					for (int i: pieces) {
						priorities[i] = 0;
					}
					piece_priority_offsets = priorities;
					if (effectively_choked_by_other_peer) {
						effectively_choked_by_other_peer = false;
						effectively_unchoked_time	= SystemTime.getMonotonousTime();
					}
				}
			} else {
				if (effectively_choked_by_other_peer) {
					effectively_choked_by_other_peer = false;
					effectively_unchoked_time	= SystemTime.getMonotonousTime();
				}
				piece_priority_offsets = null;
			}
		} finally {
			generalMon.exit();
		}
	}

	protected void checkFast(
		BitFlags	flags) {
		if (	fast_extension_enabled &&
				!isUploadDisabled &&
				!(isSeed() || isRelativeSeed()) &&
				PeerClassifier.fullySupportsFE(clientPeerId)) {
				// if already has enough pieces then bail
			if (flags.nbSet >= ALLOWED_FAST_OTHER_PEER_PIECE_MAX) {
				return;
			}
			if (!manager.isFastExtensionPermitted( this)) {
				return;
			}
			int[][] pieces;
			try {
				generalMon.enter();
				pieces = (int[][])getUserData(KEY_ALLOWED_FAST_SENT);
				if (pieces == null) {
					List<Integer> l_pieces = generateFastSet(ALLOWED_FAST_PIECE_OFFERED_NUM);
					pieces = new int[l_pieces.size()][2];
					int	piece_size = diskManager.getPieceLength();
					for (int i=0;i<l_pieces.size(); i++) {
						int	piece_number = l_pieces.get(i);
						pieces[i] = new int[]{ piece_number, piece_size*2 };
					}
					setUserData(KEY_ALLOWED_FAST_SENT, pieces);
				}
			} finally {
				generalMon.exit();
			}
			for ( int i=0;i<pieces.length;i++) {
				int	piece_number = pieces[i][0];
				if (!flags.flags[ piece_number ]) {
					sendAllowFast(piece_number);
				}
			}
		}
	}

	private void checkAllowedFast() {
		try {
			generalMon.enter();
			if (piecePicker.getNbPiecesDone() > ALLOWED_FAST_OTHER_PEER_PIECE_MAX) {
				List<Integer> pieces = (List<Integer>)getUserData(KEY_ALLOWED_FAST_RECEIVED);
				if (pieces != null) {
					if (DEBUG_FAST) {
						System.out.println("Clearing down fast received for " + getIp());
					}
					setUserData(KEY_ALLOWED_FAST_RECEIVED, null);
					calculatePiecePriorities();
				}
			}
			BitFlags flags = peerHavePieces;
			if (flags != null && flags.nbSet >= ALLOWED_FAST_OTHER_PEER_PIECE_MAX) {
				int[][] pieces = (int[][])getUserData(KEY_ALLOWED_FAST_SENT);
				if (pieces != null) {
					if (DEBUG_FAST) {
						System.out.println("Clearing down fast sent for " + getIp());
					}
					setUserData(KEY_ALLOWED_FAST_SENT, null);
				}
			}
		} finally {
			generalMon.exit();
		}
	}

	private void registerForMessageHandling() {
		//INCOMING MESSAGES
		connection.getIncomingMessageQueue().registerQueueListener(
			new IncomingMessageQueue.MessageQueueListener() {
				public final boolean messageReceived(Message message) {
					
					/*Log.d(TAG, "messageReceived() is called...");
					Throwable t = new Throwable();
					t.printStackTrace();*/
					
					if (Logger.isEnabled())
						Logger.log(new LogEvent(PEPeerTransportProtocol.this, LogIDs.NET,
								"Received [" + message.getDescription() + "] message"));
					final long now = SystemTime.getCurrentTime();
					lastMessageReceivedTime =now;
					if (message.getType() == Message.TYPE_DATA_PAYLOAD) {
						lastDataMessageReceivedTime =now;
					}
					String	messageId = message.getID();
					if (messageId.equals(BTMessage.ID_BT_PIECE)) {
						decodePiece((BTPiece)message);
						return true;
					}
					if (closing) {
						message.destroy();
						return true;
					}
					if (messageId.equals(BTMessage.ID_BT_KEEP_ALIVE)) {
						message.destroy();
						//make sure they're not spamming us
						if (!messageLimiter.countIncomingMessage(message.getID(), 6, 60*1000 )) {	//allow max 6 keep-alives per 60sec
							System.out.println(manager.getDisplayName() + ": Incoming keep-alive message flood detected, dropping spamming peer connection." +PEPeerTransportProtocol.this);
							closeConnectionInternally("Incoming keep-alive message flood detected, dropping spamming peer connection.");
						}
						return true;
					}
					if (messageId.equals(BTMessage.ID_BT_HANDSHAKE)) {
						decodeBTHandshake((BTHandshake)message);
						return true;
					}
					if (messageId.equals(AZMessage.ID_AZ_HANDSHAKE)) {
						decodeAZHandshake((AZHandshake)message);
						return true;
					}
					if (messageId.equals(LTMessage.ID_LT_HANDSHAKE)) {
						decodeLTHandshake((LTHandshake)message);
						return true;
					}
					if (messageId.equals(BTMessage.ID_BT_BITFIELD)) {
						decodeBitfield((BTBitfield)message);
						return true;
					}
					if (messageId.equals(BTMessage.ID_BT_CHOKE)) {
						decodeChoke((BTChoke)message);
						if (choking_other_peer) {
							connection.enableEnhancedMessageProcessing(false, manager.getPartitionID());	//downgrade back to normal handler
						}
						return true;
					}
					if (messageId.equals(BTMessage.ID_BT_UNCHOKE)) {
						decodeUnchoke((BTUnchoke)message);
						connection.enableEnhancedMessageProcessing(true, manager.getPartitionID());	//make sure we use a fast handler for the resulting download
						return true;
					}
					if (messageId.equals(BTMessage.ID_BT_INTERESTED)) {
						decodeInterested((BTInterested)message);
						return true;
					}
					if (messageId.equals(BTMessage.ID_BT_UNINTERESTED)) {
						decodeUninterested((BTUninterested)message);
						return true;
					}
					if (messageId.equals(BTMessage.ID_BT_HAVE)) {
						decodeHave((BTHave)message);
						return true;
					}
					if (messageId.equals(BTMessage.ID_BT_REQUEST)) {
						decodeRequest((BTRequest)message);
						return true;
					}
					if (messageId.equals(BTMessage.ID_BT_CANCEL)) {
						decodeCancel((BTCancel)message);
						return true;
					}
					if (messageId.equals(BTMessage.ID_BT_SUGGEST_PIECE)) {
						decodeSuggestPiece((BTSuggestPiece)message);
						return true;
					}
					if (messageId.equals(BTMessage.ID_BT_HAVE_ALL)) {
						decodeHaveAll((BTHaveAll)message);
						return true;
					}
					if (messageId.equals(BTMessage.ID_BT_HAVE_NONE)) {
						decodeHaveNone((BTHaveNone)message);
						return true;
					}
					if (messageId.equals(BTMessage.ID_BT_REJECT_REQUEST)) {
						decodeRejectRequest((BTRejectRequest)message);
						return true;
					}
					if (messageId.equals(BTMessage.ID_BT_ALLOWED_FAST)) {
						decodeAllowedFast((BTAllowedFast)message);
						return true;
					}
					if (messageId.equals(BTMessage.ID_BT_DHT_PORT)) {
						decodeMainlineDHTPort((BTDHTPort)message);
						return true;
					}
					if (messageId.equals(AZMessage.ID_AZ_PEER_EXCHANGE)) {
						decodePeerExchange((AZPeerExchange)message);
						return true;
					}
					if (messageId.equals(LTMessage.ID_UT_PEX)) {
						decodePeerExchange((UTPeerExchange)message);
						return true;
					}
						// generic handling of az-style PEX (e.g. used by non-public net)
					if (message instanceof AZStylePeerExchange) {
						decodePeerExchange((AZStylePeerExchange)message);
						return true;
					}
					if (messageId.equals(AZMessage.ID_AZ_REQUEST_HINT)) {
						decodeAZRequestHint((AZRequestHint)message);
						return true;
					}
					if (messageId.equals(AZMessage.ID_AZ_HAVE)) {
						decodeAZHave((AZHave)message);
						return true;
					}
					if (messageId.equals(AZMessage.ID_AZ_BAD_PIECE)) {
						decodeAZBadPiece((AZBadPiece)message);
						return true;
					}
					if (messageId.equals(AZMessage.ID_AZ_STAT_REQUEST)) {
						decodeAZStatsRequest((AZStatRequest)message);
						return true;
					}
					if (messageId.equals(AZMessage.ID_AZ_STAT_REPLY)) {
						decodeAZStatsReply((AZStatReply)message);
						return true;
					}
					if (messageId.equals(LTMessage.ID_UT_METADATA)) {
						decodeMetaData((UTMetaData)message);
						return true;
					}
					if (messageId.equals(AZMessage.ID_AZ_METADATA)) {
						decodeMetaData((AZMetaData)message);
						return true;
					}
					if (messageId.equals(LTMessage.ID_UT_UPLOAD_ONLY)) {
						decodeUploadOnly((UTUploadOnly)message);
						return true;
					}
					return false;
				}

				public final void protocolBytesReceived(int byte_count) {
					//update stats
					peerStats.protocolBytesReceived(byte_count);
					manager.protocolBytesReceived(PEPeerTransportProtocol.this, byte_count);
				}

				public final void dataBytesReceived(int byte_count) {
					// Observe that the peer is sending data so that if theyre so slow that the whole
					// data block times out, we don't think theyre not sending anything at all
					lastDataMessageReceivedTime =SystemTime.getCurrentTime();
					//update stats
					peerStats.dataBytesReceived(byte_count);
					manager.dataBytesReceived(PEPeerTransportProtocol.this, byte_count);
				}

				public boolean isPriority() {
					return (false);
				}
		});
		
		//OUTGOING MESSAGES
		connection.getOutgoingMessageQueue().registerQueueListener( 
			new OutgoingMessageQueue.MessageQueueListener() {
				
				public final boolean messageAdded(Message message) { return true; }
				public final void messageRemoved(Message message) { /*ignore*/ }
				
				public final void messageQueued(Message message) { /* ignore */ }
	
				public final void messageSent(Message message) {
					//update keep-alive info
					final long now =SystemTime.getCurrentTime();
					last_message_sent_time =now;
					if (message.getType() == Message.TYPE_DATA_PAYLOAD) {
						last_data_message_sent_time =now;
					}
					String messageId = message.getID();
					if (messageId.equals( BTMessage.ID_BT_UNCHOKE )) { // is about to send piece data
						connection.enableEnhancedMessageProcessing(true, manager.getPartitionID());	//so make sure we use a fast handler
					} else if (messageId.equals( BTMessage.ID_BT_CHOKE )) { // is done sending piece data
						if (effectively_choked_by_other_peer) {
							connection.enableEnhancedMessageProcessing(false, manager.getPartitionID());	//so downgrade back to normal handler
						}
					} else if (messageId.equals(BTMessage.ID_BT_REQUEST)) {
						BTRequest request = (BTRequest)message;
						DiskManagerReadRequest dm_request = lookupRequest( request.getPieceNumber(), request.getPieceOffset(), request.getLength());
						if (dm_request != null) {
							dm_request.setTimeSent( SystemTime.getMonotonousTime());
						}
					}
					
					if (Logger.isEnabled())
						Logger.log(new LogEvent(PEPeerTransportProtocol.this, LogIDs.NET,
								"Sent [" + message.getDescription() + "] message"));
				}
	
				public final void protocolBytesSent(int byte_count) {
					//update stats
					peerStats.protocolBytesSent(byte_count);
					manager.protocolBytesSent(PEPeerTransportProtocol.this, byte_count);
				}
	
				public final void dataBytesSent(int byte_count) {
					//update stats
					peerStats.dataBytesSent(byte_count);
					manager.dataBytesSent(PEPeerTransportProtocol.this, byte_count);
				}
				 public void flush() {}
			});
		//start message processing
		connection.addRateLimiter(manager.getUploadLimitedRateGroup(), true);
		connection.addRateLimiter(manager.getDownloadLimitedRateGroup(), false);
		connection.startMessageProcessing();
	}

	public void	addRateLimiter(
		LimitedRateGroup	limiter,
		boolean				upload) {
		connection.addRateLimiter(limiter, upload);
	}

	public LimitedRateGroup[] getRateLimiters(boolean upload) {
		return (connection.getRateLimiters( upload));
	}

	public void removeRateLimiter(
		LimitedRateGroup	limiter,
		boolean				upload) {
		connection.removeRateLimiter(limiter, upload);
	}

	public void setUploadDisabled(
		Object		key,
		boolean		disabled) {
		synchronized(this) {
			if (uploadDisabledSet == null) {
				if (disabled) {
					uploadDisabledSet = new HashSet<Object>();
					uploadDisabledSet.add(key);
				} else {
					Debug.out("derp");
				}
			} else {
				if (disabled) {
					if (!uploadDisabledSet.add( key)) {
						Debug.out("derp");
					}
				} else {
					if (!uploadDisabledSet.remove( key)) {
						Debug.out("derp");
					}
					if (uploadDisabledSet.size() == 0) {
						uploadDisabledSet = null;
					}
				}
			}
			isUploadDisabled = uploadDisabledSet != null;
			//System.out.println("setUploadDisabled " + getIp() + " -> " + (upload_disabled_set==null?0:upload_disabled_set.size()));
		}
	}

	public void setDownloadDisabled(
		Object		key,
		boolean		disabled) {
		boolean	check = false;
		synchronized(this) {
			if (downloadDisabledSet == null) {
				if (disabled) {
					downloadDisabledSet = new HashSet<Object>();
					downloadDisabledSet.add(key);
				} else {
					Debug.out("derp");
				}
			} else {
				if (disabled) {
					if (!downloadDisabledSet.add( key)) {
						Debug.out("derp");
					}
				} else {
					if (!downloadDisabledSet.remove( key)) {
						Debug.out("derp");
					}
					if (downloadDisabledSet.size() == 0) {
						downloadDisabledSet = null;
					}
				}
			}
			boolean	old = isDownloadDisabled;
			isDownloadDisabled = downloadDisabledSet != null;
			check = old != isDownloadDisabled;
			//System.out.println("setDownloadDisabled " + getIp() + " -> " + (download_disabled_set==null?0:download_disabled_set.size()));
		}
		if (check) {
			checkInterested();
		}
	}

	public boolean isUploadDisabled() {
		return (isUploadDisabled);
	}

	public boolean isUploadDisabled(Object		key) {
		synchronized(this) {
			if (uploadDisabledSet == null) {
				return (false);
			}
			return (uploadDisabledSet.contains( key));
		}
	}

	public boolean isDownloadDisabled() {
		return (isDownloadDisabled);
	}

	public boolean isDownloadDisabled(Object		key) {
		synchronized(this) {
			if (downloadDisabledSet == null) {
				return (false);
			}
			return (downloadDisabledSet.contains( key));
		}
	}

	public Connection getPluginConnection() {
		return pluginConnection;
	}

	public Message[] getSupportedMessages() {
		return supported_messages;
	}

	public boolean supportsMessaging() {
		return supported_messages != null;
	}

	public int getMessagingMode() {
		return messagingMode;
	}

	public byte[] getHandshakeReservedBytes() {
		return this.handshake_reserved_bytes;
	}

	public void setHaveAggregationEnabled(boolean		enabled) {
		haveAggregationDisabled	= !enabled;
	}

	public boolean hasReceivedBitField() {
		return (received_bitfield);
	}

	public long getUnchokedForMillis() {
		long	time = effectively_unchoked_time;
		if (effectively_choked_by_other_peer || time < 0) {
			return (-1);
		}
		return (SystemTime.getMonotonousTime() - time);
	}

	public long getLatency() {
		return (requestLatency);
	}

	public String getEncryption() {
		Transport	transport = connection.getTransport();
		if (transport == null) {
			return ("");
		}
		return (transport.getEncryption( false));
	}

	public String getProtocol() {
		Transport	transport = connection.getTransport();
		String 	result;
		if (transport == null) {
			result	= "";
		} else {
			result = transport.getProtocol();
		}
		return (result);
	}

	public String getProtocolQualifier() {
		return ((String)connection.getEndpoint().getProperty(AEProxyAddressMapper.MAP_PROPERTY_PROTOCOL_QUALIFIER));
	}

	public void addListener(PEPeerListener listener ) {
		try {
			peerListenersMon.enter();
			if (peerListenersCow == null) {
				peerListenersCow = new ArrayList();
			}
			final List	new_listeners = new ArrayList(peerListenersCow);
			new_listeners.add(listener);
			peerListenersCow	= new_listeners;
		} finally {
			peerListenersMon.exit();
		}
	}

	public void removeListener(
			PEPeerListener listener ) {
		try {
			peerListenersMon.enter();
			if (peerListenersCow != null) {
				List	new_listeners = new ArrayList(peerListenersCow);
				new_listeners.remove(listener);
				if (new_listeners.isEmpty()) {
					new_listeners	= null;
				}
				peerListenersCow	= new_listeners;
			}
		} finally {
			peerListenersMon.exit();
		}
	}

	private void changePeerState(int new_state) {
		currentPeerState = new_state;
		if (currentPeerState == PEPeer.TRANSFERING) {	 //YUCK!
			doPostHandshakeProcessing();
		}
		final List	peer_listeners_ref = peerListenersCow;
		if (peer_listeners_ref != null) {
			for (int i=0; i < peer_listeners_ref.size(); i++) {
				final PEPeerListener l = (PEPeerListener)peer_listeners_ref.get(i);
				l.stateChanged(this, currentPeerState);
			}
		}
	}


	/**
	 * this method might be called repeatedly since LTEP allows headers to be sent more than once
	 * make sure that multiple invokations do not have unintended consequences
	 */
	private void doPostHandshakeProcessing() {
		//peer exchange registration
		if (manager.isPeerExchangeEnabled()) {
			//try and register all connections for their peer exchange info
			PeerExchangerItem pex_item = peerExchangeItem;
			if (pex_item == null && canBePeerExchanged()) {
				pex_item = peerExchangeItem = manager.createPeerExchangeConnection(this);
			}
			if (pex_item != null) {
					//check for peer exchange support
				if (ut_pex_enabled || peerSupportsMessageType(AZMessage.ID_AZ_PEER_EXCHANGE)) {
					peerExchangeSupported = true;
					pex_item.enableStateMaintenance();
				} else {
					MessageStreamEncoder encoder = connection.getOutgoingMessageQueue().getEncoder();
					if (	encoder instanceof LTMessageEncoder &&
							((LTMessageEncoder)encoder).hasCustomExtensionHandler(LTMessageEncoder.CET_PEX)) {
						peerExchangeSupported = true;
						pex_item.enableStateMaintenance();
					} else {
							//no need to maintain internal states as we wont be sending/receiving peer exchange messages
						pex_item.disableStateMaintenance();
					}
				}
			}
		}
		requestHintSupported 	= peerSupportsMessageType(AZMessage.ID_AZ_REQUEST_HINT);
		badPieceSupported 	= peerSupportsMessageType(AZMessage.ID_AZ_BAD_PIECE);
		statsRequestSupported = peerSupportsMessageType(AZMessage.ID_AZ_STAT_REQUEST);
		statsReplySupported 	= peerSupportsMessageType(AZMessage.ID_AZ_STAT_REPLY);
		azMetadataSupported 	= peerSupportsMessageType(AZMessage.ID_AZ_METADATA);
		if (isMetadataDownload) {
			if (azMetadataSupported) {
				int	mds = manager.getTorrentInfoDictSize();
				if (mds > 0) {
					spoofMDAvailability(mds);
				}
			}
		}
	}

	private boolean canBePeerExchanged() {
		if (clientPeerId != null) {
				// disable the exchange of location targetted peers
			boolean ok = !clientPeerId.startsWith(PeerClassifier.CACHE_LOGIC);
			// System.out.println("canPEX: " + client_peer_id + " -> " + ok);
			return (ok);
		} else {
			Debug.out("No client peer id!");
			return (false);
		}
	}

	private boolean peerSupportsMessageType(String message_id) {
		if (supported_messages != null) {
			for (int i=0; i < supported_messages.length; i++) {
				if (supported_messages[i].getID().equals( message_id ))	return true;
			}
		}
		return false;
	}

	public void updatePeerExchange() {
		if (currentPeerState != TRANSFERING) return;
		if (!peerExchangeSupported)	return;
		PeerExchangerItem pex_item = peerExchangeItem;
		if (pex_item != null && manager.isPeerExchangeEnabled()) {
			if (peerItemIdentity.getNetwork() == AENetworkClassifier.AT_PUBLIC) {
				final PeerItem[] adds = pex_item.getNewlyAddedPeerConnections(AENetworkClassifier.AT_PUBLIC);
				final PeerItem[] drops = pex_item.getNewlyDroppedPeerConnections(AENetworkClassifier.AT_PUBLIC);
				if ((adds != null && adds.length > 0) || (drops != null && drops.length > 0)) {
					if (ut_pex_enabled) {
						connection.getOutgoingMessageQueue().addMessage(new UTPeerExchange(adds, drops, null, (byte)0), false);
					} else {
						connection.getOutgoingMessageQueue().addMessage(new AZPeerExchange( manager.getHash(), adds, drops, other_peer_pex_version ), false);
					}
				}
			} else {
				MessageStreamEncoder encoder = connection.getOutgoingMessageQueue().getEncoder();
				if (encoder instanceof LTMessageEncoder) {
					((LTMessageEncoder)encoder).handleCustomExtension( LTMessageEncoder.CET_PEX, new Object[]{ pex_item });
				}
			}
		}
	}

	protected void decodePeerExchange(AZStylePeerExchange exchange) {
		// if we're seeding ignore µT-PEXed seeds, Az won't send them in the first place
		PeerItem[] added = exchange instanceof UTPeerExchange ? ((UTPeerExchange)exchange).getAddedPeers(!(manager.isSeeding() || Constants.DOWNLOAD_SOURCES_PRETEND_COMPLETE)) : exchange.getAddedPeers();
		PeerItem[] dropped = exchange.getDroppedPeers();
		int	max_added	= exchange.getMaxAllowedPeersPerVolley(!has_received_initial_pex, true);
		int max_dropped = exchange.getMaxAllowedPeersPerVolley(!has_received_initial_pex, false);
		exchange.destroy();
		//make sure they're not spamming us
		if (!messageLimiter.countIncomingMessage( exchange.getID(), 7, 120*1000 )) {	//allow max 7 PEX per 2min	//TODO reduce max after 2308 release?
			System.out.println(manager.getDisplayName() + ": Incoming PEX message flood detected, dropping spamming peer connection." +PEPeerTransportProtocol.this);
			closeConnectionInternally("Incoming PEX message flood detected, dropping spamming peer connection.");
			return;
		}
		if (	(added != null	 && added.length	> max_added) ||
				(dropped != null && dropped.length > max_dropped)) {
			// log these too-large messages and ignore them - if the swarm really is this large then
			// we'll discover the peers soon anyway
			if (Logger.isEnabled()) {
				Logger.log(
					new LogEvent(this, LOGID,
					"Invalid PEX message received: too large, ignoring this exchange. (added=" + (added==null?0:added.length) + ",dropped=" + (dropped==null?0:dropped.length) +")" ));
			}
			added 	= null;
			dropped	= null;
		}
		has_received_initial_pex = true;
		PeerExchangerItem pex_item = peerExchangeItem;
		if (peerExchangeSupported && pex_item != null && manager.isPeerExchangeEnabled()) {
			if (added != null) {
				for (int i=0; i < added.length; i++) {
					PeerItem pi = added[i];
					manager.peerDiscovered(this, pi);
					pex_item.addConnectedPeer(pi);
				}
			}
			if (dropped != null) {
				for (int i=0; i < dropped.length; i++) {
					pex_item.dropConnectedPeer(dropped[i]);
				}
			}
		}
		else {
			if (Logger.isEnabled())
				Logger.log(new LogEvent(this, LOGID,
						"Peer Exchange disabled for this download, "
						+ "dropping received exchange message"));
		}
	}

	protected void decodeMetaData(
		AZUTMetaData metadata ) {
		try {
			final int BLOCK_SIZE = 16*1024;
			int	type = metadata.getMessageType();
			if (type == UTMetaData.MSG_TYPE_REQUEST) {
				if (!manager.isPrivateTorrent()) {
					int	piece = metadata.getPiece();
					int total_size = manager.getTorrentInfoDictSize();
					byte[] data = total_size<=0?null:manager.getAdapter().getTorrentInfoDict(this);
					UTMetaData	reply ;
					int	offset = piece*BLOCK_SIZE;
					if (isMetadataDownload || data == null || offset >= data.length) {
						reply = new UTMetaData(piece, null, 0, other_peer_bt_lt_ext_version);
					} else {
						int	to_send = Math.min(data.length - offset, BLOCK_SIZE);
						// System.out.println("Sending ut_metadata: " + offset + "/" + to_send);
						reply = new UTMetaData(piece, ByteBuffer.wrap( data, offset, to_send ), total_size, other_peer_bt_lt_ext_version);
					}
					connection.getOutgoingMessageQueue().addMessage(reply, false);
				}
			} else if (type == UTMetaData.MSG_TYPE_DATA) {
				int	piece_number 		= metadata.getPiece();
				DirectByteBuffer data 	= metadata.getMetadata();
				int	data_size 	= data.remaining(DirectByteBuffer.SS_PEER);
				int total_size	= manager.getTorrentInfoDictSize();
				int	piece_count 	= (total_size+BLOCK_SIZE-1)/BLOCK_SIZE;
				int	last_piece_size	= total_size%BLOCK_SIZE;
				if (last_piece_size == 0) {
					last_piece_size = BLOCK_SIZE;
				}
				boolean	good = false;
				if (piece_number < piece_count) {
					int	expected_size = piece_number==piece_count-1?last_piece_size:BLOCK_SIZE;
					if (data_size == expected_size) {
						DiskManagerReadRequest request = manager.createDiskManagerRequest(piece_number, 0, BLOCK_SIZE);
						if (hasBeenRequested( request)) {
							good = true;
							metadata.setMetadata(null);
							removeRequest(request);
							long now = SystemTime.getCurrentTime();
							resetRequestsTime(now);
							manager.writeBlock( piece_number, 0, data, this, false);
							if (lastGoodDataTime !=-1 && now -lastGoodDataTime <=60 *1000) {
								setSnubbed(false);
							}
							lastGoodDataTime = now;
							requestsCompleted++;
						}
					}
				}
				if (! good) {
					peerStats.bytesDiscarded(data_size);
					manager.discarded(this, data_size);
					requestsDiscarded++;
					printRequestStats();
					if (Logger.isEnabled())
						Logger.log(new LogEvent(this, LOGID, LogEvent.LT_ERROR,
								"metadata piece discarded as invalid."));
				}
			} else {
				int	piece = metadata.getPiece();
				final DiskManagerReadRequest request = manager.createDiskManagerRequest(piece, 0, 16*1024);
				if (hasBeenRequested( request)) {
					removeRequest(request);
					manager.requestCanceled(request);
				}
			}
		} finally {
			metadata.destroy();
		}
	}

	protected void decodeUploadOnly(
		UTUploadOnly message ) {
		try {
			boolean ulo = message.isUploadOnly();
			if (ulo) {
				relativeSeeding |= RELATIVE_SEEDING_UPLOAD_ONLY_INDICATED;
			} else {
				relativeSeeding &= ~RELATIVE_SEEDING_UPLOAD_ONLY_INDICATED;
			}
		} finally {
			message.destroy();
		}
	}

	public boolean sendRequestHint(
			int		piece_number,
			int		offset,
			int		length,
			int		life) {
		if (requestHintSupported) {
			AZRequestHint	rh = new AZRequestHint(piece_number, offset, length, life, other_peer_az_request_hint_version);
			connection.getOutgoingMessageQueue().addMessage(rh, false);
			return (true);
		} else {
			return (false);
		}
	}

	protected void decodeSuggestPiece(
		BTSuggestPiece	hint) {
		int	piece_number 	= hint.getPieceNumber();
		int	offset			= 0;
		int	length			= manager.getPieceLength(piece_number);
		hint.destroy();
		if (manager.validateHintRequest( this, piece_number, offset, length)) {
			if (requestHint == null) {
				requestHint = new int[]{ piece_number, offset, length };
			}
		}
	}

	protected void decodeAZRequestHint(
		AZRequestHint	hint) {
		int	piece_number 	= hint.getPieceNumber();
		int	offset			= hint.getOffset();
		int	length			= hint.getLength();
		int	life			= hint.getLife();
		hint.destroy();
		if (life > REQUEST_HINT_MAX_LIFE) {
			life = REQUEST_HINT_MAX_LIFE;
		}
		if (manager.validateHintRequest( this, piece_number, offset, length)) {
			if (requestHint == null) {
				// we ignore life time currently as once hinted we don't accept another hint
				// until that one is satisfied. This is to prevent too many pieces starting
				requestHint = new int[]{ piece_number, offset, length };
			}
		}
	}

	public int[]
				 getRequestHint() {
		return (requestHint);
	}

	public void clearRequestHint() {
		requestHint = null;
	}

	public PeerItem getPeerItemIdentity() {	return peerItemIdentity;	}

	public int[]
	 	getReservedPieceNumbers()
	 	{
	 		return (reserved_pieces);
	 	}
 	public void
 	addReservedPieceNumber(int piece_number)
 	{
 		int[]	existing = reserved_pieces;
 		if (existing == null) {
 			reserved_pieces = new int[]{ piece_number };
 		} else {
 			int[] updated = new int[existing.length+1];
 			System.arraycopy(existing, 0, updated, 0, existing.length);
 			updated[existing.length] = piece_number;
 			reserved_pieces = updated;
 		}
 	}
 	public void
 	removeReservedPieceNumber(int piece_number)
 	{
 		int[]	existing = reserved_pieces;
 		if (existing != null) {
 			if (existing.length == 1) {
 				if (existing[0] == piece_number) {
 					reserved_pieces = null;
 				}
 			} else {
 				int[] updated = new int[existing.length-1];
 				int		pos 	= 0;
 				boolean	found 	= false;
 				for (int i=0;i<existing.length;i++) {
 					int	pn = existing[i];
 					if (found || pn != piece_number) {
 						if (pos == updated.length) {
 							return;
 						}
 						updated[pos++] = pn;
 					} else {
 						found = true;
 					}
 				}
 				reserved_pieces = updated;
 			}
 		}
 	}

	public int getIncomingRequestCount() {
		if (outgoingPieceMessageHandler == null) {
			return (0);
		}
		return outgoingPieceMessageHandler.getRequestCount();
	}

	public int getOutgoingRequestCount() {
		return ( getNbRequests());
	}

	public int getOutboundDataQueueSize() {
		return ( connection.getOutgoingMessageQueue().getTotalSize());
	}

	public boolean isStalledPendingLoad() {
		if (outgoingPieceMessageHandler == null) {
			return (false);
		}
		return outgoingPieceMessageHandler.isStalledPendingLoad();
	}

	public int[] getIncomingRequestedPieceNumbers() {
		if (outgoingPieceMessageHandler == null) {
			return (new int[0]);
		}
		return outgoingPieceMessageHandler.getRequestedPieceNumbers();
	}

	public int[] getOutgoingRequestedPieceNumbers() {
		try {
			requested_mon.enter();
			/** Cheap hack to reduce (but not remove all) the # of duplicate entries */
			int iLastNumber = -1;
			// allocate max size needed (we'll shrink it later)
			final int[] pieceNumbers = new int[requested.size()];
			int pos = 0;
			for (int i = 0; i < requested.size(); i++) {
				DiskManagerReadRequest request = null;
				try {
					request = (DiskManagerReadRequest) requested.get(i);
				} catch (Exception e) {
					Debug.printStackTrace(e);
				}
				if (request != null && iLastNumber != request.getPieceNumber()) {
					iLastNumber = request.getPieceNumber();
					pieceNumbers[pos++] = iLastNumber;
				}
			}
			final int[] trimmed = new int[pos];
			System.arraycopy(pieceNumbers, 0, trimmed, 0, pos);
			return trimmed;
		} finally {
			requested_mon.exit();
		}
	}

	public int getPercentDoneOfCurrentIncomingRequest() {
		return (connection.getIncomingMessageQueue().getPercentDoneOfCurrentMessage());
	}

	public int getPercentDoneOfCurrentOutgoingRequest() {
		return (connection.getOutgoingMessageQueue().getPercentDoneOfCurrentMessage());
	}

	public long getLastMessageSentTime() {
		return (last_message_sent_time);
	}
	/* (non-Javadoc)
	 * @see org.gudy.azureus2.core3.logging.LogRelation#getLogRelationText()
	 */
	public String getRelationText() {
		String text = "";
		if (manager instanceof LogRelation)
			text = ((LogRelation)manager).getRelationText() + "; ";
		text += "Peer: " + toString();
		return text;
	}

	/* (non-Javadoc)
	 * @see org.gudy.azureus2.core3.logging.LogRelation#queryForClass(java.lang.Class)
	 */
	public Object[] getQueryableInterfaces() {
		return new Object[] { manager };
	}

	public int getLastPiece() {
		return _lastPiece;
	}

	public void setLastPiece(int pieceNumber) {
		_lastPiece =pieceNumber;
	}

	public boolean isLANLocal() {
		if (connection == null )	return ( AddressUtils.isLANLocalAddress( ip ) == AddressUtils.LAN_LOCAL_YES);
		return connection.isLANLocal();
	}

	public boolean isTCP() {
		if (connection == null) {
			return (false);
		}
		
		ProtocolEndpoint[] protocols = connection.getEndpoint().getProtocols();
		// not very good here - for most purposes we lump uTP with TCP as 'UDP' originally
		// meant the Vuze UDP protocol. The reconnect handling, for example, requires uTP to
		// appear to be TCP so for the moment lets make it so
		return (protocols[0].getType() != ProtocolEndpoint.PROTOCOL_UDP);
	}

	public String getNetwork() {
		return (network);
	}

	public void setUploadRateLimitBytesPerSecond(
		int 	bytes) {
		if (bytes == -1) {
			if (!isUploadDisabled( PEPeerTransport.class)) {
				setUploadDisabled(PEPeerTransport.class, true);
				connection.setUploadLimit(0);
			}
		} else {
			if (isUploadDisabled) {
				if (isUploadDisabled( PEPeerTransport.class)) {
					setUploadDisabled(PEPeerTransport.class, false);
				}
			}
			connection.setUploadLimit(bytes);
		}
	}

	public int getUploadRateLimitBytesPerSecond() {
		if (isUploadDisabled) {
			if (isUploadDisabled( PEPeerTransport.class)) {
				return (-1);
			}
		}
		return connection.getUploadLimit();
	}

	public void setDownloadRateLimitBytesPerSecond(int 	bytes) {
		if (bytes == -1) {
			if (!isDownloadDisabled( PEPeerTransport.class)) {
				setDownloadDisabled(PEPeerTransport.class, true);
				connection.setDownloadLimit(0);
			}
		} else {
			if (isDownloadDisabled) {
				if (isDownloadDisabled( PEPeerTransport.class)) {
					setDownloadDisabled(PEPeerTransport.class, false);
				}
			}
			connection.setDownloadLimit(bytes);
		}
	}

	public int getDownloadRateLimitBytesPerSecond() {
		if (isDownloadDisabled) {
			if (isDownloadDisabled( PEPeerTransport.class)) {
				return (-1);
			}
		}
		return connection.getDownloadLimit();
	}

	public String getClientNameFromPeerID() {return this.clientPeerId;}

	public String getClientNameFromExtensionHandshake() {
		if (!this.client_handshake.equals("") && !this.client_handshake_version.equals("")) {
			return this.client_handshake + " " + this.client_handshake_version;
		}
		return this.client_handshake;
	}

	private static MainlineDHTProvider getDHTProvider() {
		return AzureusCoreImpl.getSingleton().getGlobalManager().getMainlineDHTProvider();
	}

	public void setPriorityConnection(
		boolean		is_priority) {
		synchronized(this) {
			if (priorityConnection == is_priority) {
				return;
			}
			priorityConnection = is_priority;
		}
		manager.getAdapter().priorityConnectionChanged(is_priority);
	}

	public boolean isPriorityConnection() {
		synchronized(this) {
			return (priorityConnection);
		}
	}

	protected List<Integer>
	generateFastSet(
		byte[]		hash,
		String		ip,
		int			num_pieces,
		int			num_required) {
		List<Integer>	res = new ArrayList<Integer>();
		try {
			if (network == AENetworkClassifier.AT_PUBLIC) {
				byte[]	address = InetAddress.getByName(ip).getAddress();
					// no IPv6 support yet
				if (address.length == 4) {
					byte[]	bytes = new byte[24];
					System.arraycopy(address, 0, bytes, 0, 3);
					System.arraycopy(hash, 0, bytes, 4, 20);
					num_required = Math.min(num_required, num_pieces);
					while (res.size() < num_required) {
						bytes = new SHA1Simple().calculateHash(bytes);
						int	pos = 0;
						while (pos < 20 && res.size() < num_required) {
							long	index = (bytes[pos++] << 24 )&0xff000000L |
											(bytes[pos++] << 16 )&0x00ff0000L |
											(bytes[pos++] << 8	)&0x0000ff00L |
											bytes[pos++]&0x000000ffL;
							Integer i = new Integer((int)(index%num_pieces));
							if (!res.contains(i)) {
								res.add(i);
							}
						}
					}
				}
			}
		} catch (Throwable e) {
			Debug.out("Fast set generation failed", e);
		}
		return (res);
	}

	protected List<Integer>
	generateFastSet(
		int		num) {
		return (generateFastSet( manager.getHash(), getIp(), nbPieces, num));
	}

	public int getTaggableType() {return TT_PEER;}
	 	public String getTaggableID() { return (null); }

	public TaggableResolver	getTaggableResolver() { return (null); }

	public void generateEvidence(IndentWriter	writer) {
		writer.println(
				"ip=" + getIp() + ",in=" + isIncoming() + ",port=" + getPort() + ",cli=" + client + ",tcp=" + getTCPListenPort() + ",udp=" + getUDPListenPort() +
				",oudp=" + getUDPNonDataListenPort() + ",prot=" + getProtocol() + ",p_state=" + getPeerState() + ",c_state=" + getConnectionState() + ",seed=" + isSeed() + ",partialSeed=" + isRelativeSeed() + ",pex=" + peerExchangeSupported + ",closing=" + closing);
		writer.println("	choked=" + effectively_choked_by_other_peer + "/" + really_choked_by_other_peer + ",choking=" + choking_other_peer + ",is_opt=" + isOptimisticUnchoke);
		writer.println("	interested=" + interested_in_other_peer + ",interesting=" + otherPeerInterestedInMe + ",snubbed=" + snubbed);
		writer.println("	lp=" + _lastPiece + ",up=" + uniquePiece + ",rp=" + reserved_pieces);
		writer.println(
				"	last_sent=" + last_message_sent_time + "/" + last_data_message_sent_time +
				",last_recv=" + lastMessageReceivedTime + "/" + lastDataMessageReceivedTime + "/" + lastGoodDataTime);
		writer.println("	conn_at=" + connectionEstablishedTime + ",cons_no_reqs=" + consecutive_no_request_count +
				",discard=" + requestsDiscarded + "/" + requestsDiscardedEndgame + ",recov=" + requestsRecovered + ",comp=" + requestsCompleted + ",curr=" + requested.size());
	}

	protected static class MutableInteger {
		private int	value;
		protected MutableInteger(int		v) {
			value = v;
		}

		protected void setValue(int	v) {
			value = v;
		}

		protected int getValue() {
			return (value);
		}

		public int hashCode() {
			return value;
		}

		public boolean equals(Object obj) {
			if (obj instanceof MutableInteger) {
				return value == ((MutableInteger)obj).value;
			}
			return false;
		}
	}
}
