/*
 * Created on Jul 29, 2004
 * Created by Alon Rohter
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.	See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA	02111-1307, USA.
 *
 */

package com.aelitis.azureus.core.networkmanager;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.config.ParameterListener;
import org.gudy.azureus2.core3.download.DownloadManager;
import org.gudy.azureus2.core3.global.GlobalManagerListener;
import org.gudy.azureus2.core3.util.AddressUtils;
import org.gudy.azureus2.core3.util.Debug;

import com.aelitis.azureus.core.AzureusCore;
import com.aelitis.azureus.core.networkmanager.impl.IncomingConnectionManager;
import com.aelitis.azureus.core.networkmanager.impl.RateControlledEntity;
import com.aelitis.azureus.core.networkmanager.impl.ReadController;
import com.aelitis.azureus.core.networkmanager.impl.TransferProcessor;
import com.aelitis.azureus.core.networkmanager.impl.TransportHelper;
import com.aelitis.azureus.core.networkmanager.impl.WriteController;
import com.aelitis.azureus.core.networkmanager.impl.http.HTTPNetworkManager;
import com.aelitis.azureus.core.networkmanager.impl.tcp.TCPNetworkManager;
import com.aelitis.azureus.core.networkmanager.impl.udp.UDPNetworkManager;
import com.aelitis.azureus.core.peermanager.messaging.MessageStreamDecoder;
import com.aelitis.azureus.core.peermanager.messaging.MessageStreamEncoder;
import com.aelitis.azureus.core.peermanager.messaging.MessageStreamFactory;
import com.aelitis.azureus.core.util.FeatureAvailability;

import hello.util.Log;



/**
 *
 */
public class NetworkManager {

	private static final String TAG = NetworkManager.class.getSimpleName();
	
	public static final int UNLIMITED_RATE = 1024 * 1024 * 100; //100 mbyte/s

	private static final NetworkManager instance = new NetworkManager();

	private static int maxDownloadRateBps;
	private static int external_max_download_rate_bps;

	private static int max_upload_rate_bps_normal;
	private static int max_upload_rate_bps_seeding_only;
	private static int maxUploadRateBps;

	private static boolean lanRateEnabled;
	private static int maxLanUploadRateBps;
	private static int maxLanDownloadRateBps;

	private static boolean seeding_only_mode_allowed;
	private static boolean seeding_only_mode = false;

	public static boolean 	REQUIRE_CRYPTO_HANDSHAKE;
	public static boolean 	INCOMING_HANDSHAKE_FALLBACK_ALLOWED;
	public static boolean 	OUTGOING_HANDSHAKE_FALLBACK_ALLOWED;
	public static boolean 	INCOMING_CRYPTO_ALLOWED;

	private static boolean	USE_REQUEST_LIMITING;


	static {
		COConfigurationManager.addAndFireParameterListeners(
			new String[]{ "network.transport.encrypted.require",
				"network.transport.encrypted.fallback.incoming",
				"network.transport.encrypted.fallback.outgoing",
				"network.transport.encrypted.allow.incoming",
				"LAN Speed Enabled",
				"Max Upload Speed KBs",
				"Max LAN Upload Speed KBs",
				"Max Upload Speed Seeding KBs",
				"enable.seedingonly.upload.rate",
				"Max Download Speed KBs",
				"Max LAN Download Speed KBs",
				"network.tcp.mtu.size",
				"network.udp.mtu.size",
				"Use Request Limiting"},

			new ParameterListener()	{
				public void	parameterChanged(String ignore) {
					REQUIRE_CRYPTO_HANDSHAKE				= COConfigurationManager.getBooleanParameter("network.transport.encrypted.require");
					INCOMING_HANDSHAKE_FALLBACK_ALLOWED	= COConfigurationManager.getBooleanParameter("network.transport.encrypted.fallback.incoming");
		 			OUTGOING_HANDSHAKE_FALLBACK_ALLOWED	= COConfigurationManager.getBooleanParameter("network.transport.encrypted.fallback.outgoing");
		 			INCOMING_CRYPTO_ALLOWED				= COConfigurationManager.getBooleanParameter("network.transport.encrypted.allow.incoming");

					USE_REQUEST_LIMITING					= COConfigurationManager.getBooleanParameter("Use Request Limiting");

					max_upload_rate_bps_normal = COConfigurationManager.getIntParameter("Max Upload Speed KBs") * 1024;
					if (max_upload_rate_bps_normal < 1024)	max_upload_rate_bps_normal = UNLIMITED_RATE;
					if (max_upload_rate_bps_normal > UNLIMITED_RATE)	max_upload_rate_bps_normal = UNLIMITED_RATE;

					maxLanUploadRateBps = COConfigurationManager.getIntParameter("Max LAN Upload Speed KBs") * 1024;
					if (maxLanUploadRateBps < 1024)	maxLanUploadRateBps = UNLIMITED_RATE;
					if (maxLanUploadRateBps > UNLIMITED_RATE)	maxLanUploadRateBps = UNLIMITED_RATE;


					max_upload_rate_bps_seeding_only = COConfigurationManager.getIntParameter("Max Upload Speed Seeding KBs") * 1024;
					if (max_upload_rate_bps_seeding_only < 1024)	max_upload_rate_bps_seeding_only = UNLIMITED_RATE;
					if (max_upload_rate_bps_seeding_only > UNLIMITED_RATE)	max_upload_rate_bps_seeding_only = UNLIMITED_RATE;

					seeding_only_mode_allowed = COConfigurationManager.getBooleanParameter("enable.seedingonly.upload.rate");

					external_max_download_rate_bps = maxDownloadRateBps = (int)(COConfigurationManager.getIntParameter("Max Download Speed KBs") * 1024); // leave 5KiB/s room for the request limiting
					if (maxDownloadRateBps < 1024 || maxDownloadRateBps > UNLIMITED_RATE)
						maxDownloadRateBps = UNLIMITED_RATE;
					else if (USE_REQUEST_LIMITING && FeatureAvailability.isRequestLimitingEnabled())
						maxDownloadRateBps += Math.max(maxDownloadRateBps * 0.1, 5*1024);

					lanRateEnabled = COConfigurationManager.getBooleanParameter("LAN Speed Enabled");
					maxLanDownloadRateBps = COConfigurationManager.getIntParameter("Max LAN Download Speed KBs") * 1024;
					if (maxLanDownloadRateBps < 1024)	maxLanDownloadRateBps = UNLIMITED_RATE;
					if (maxLanDownloadRateBps > UNLIMITED_RATE)	maxLanDownloadRateBps = UNLIMITED_RATE;

					refreshRates();
				}
			}
		);
	}

	private final List<WriteController> writeControllers;
	private final List<ReadController> 	readControllers;

	{
		int	numRead = COConfigurationManager.getIntParameter("network.control.read.processor.count");
		readControllers = new ArrayList<ReadController>(numRead);
		for (int i=0;i<numRead;i++) {
			readControllers.add(new ReadController());
		}
		int	numWrite = COConfigurationManager.getIntParameter("network.control.write.processor.count");
		writeControllers = new ArrayList<WriteController>(numWrite);
		for (int i=0;i<numWrite;i++) {
			writeControllers.add(new WriteController());
		}
		
		//Log.d(TAG, "numRead = " + numRead);
		//Log.d(TAG, "numWrite = " + numWrite);
		
	}

	private final TransferProcessor uploadProcessor =
		new TransferProcessor(
			TransferProcessor.TYPE_UPLOAD,
			new LimitedRateGroup() {
				
				public String getName() {
					return ("global_up");
				}
				
				public int getRateLimitBytesPerSecond() {
					return maxUploadRateBps;
				}
				
				public boolean isDisabled() {
					return (maxUploadRateBps == -1);
				}
				
				public void updateBytesUsed(int	used) {
				}
			},
			writeControllers.size() > 1
		);

	private final TransferProcessor downloadProcessor =
		new TransferProcessor(
			TransferProcessor.TYPE_DOWNLOAD,
			new LimitedRateGroup() {
				public String getName() {
					return ("global_down");
				}
				
				public int getRateLimitBytesPerSecond() {
					return maxDownloadRateBps;
				}
				
				public boolean isDisabled() {
					return (maxDownloadRateBps == -1);
				}
				
				public void updateBytesUsed(int used) {
				}
			},
			readControllers.size() > 1
		);


	private final TransferProcessor lanUploadProcessor =
		new TransferProcessor(
			TransferProcessor.TYPE_UPLOAD,
			new LimitedRateGroup() {
				public String getName() {
					return ("global_lan_up");
				}
				public int getRateLimitBytesPerSecond() {
					return maxLanUploadRateBps;
				}
				public boolean isDisabled() {
					return (maxLanUploadRateBps == -1);
				}
				public void updateBytesUsed(int	used) {
				}
			},
			writeControllers.size() > 1);

	private final TransferProcessor lanDownloadProcessor =
		new TransferProcessor(
			TransferProcessor.TYPE_DOWNLOAD,
			new LimitedRateGroup() {
				public String getName() {
					return ("global_lan_down");
				}
				public int getRateLimitBytesPerSecond() {
					return maxLanDownloadRateBps;
				}
				public boolean isDisabled() {
					return (maxLanDownloadRateBps == -1);
				}
				public void updateBytesUsed(
						int	used) {
				}
			},
			readControllers.size() > 1);

	public static boolean isLANRateEnabled() {
		return (lanRateEnabled);
	}

	private NetworkManager() {
	}

	public static int getMinMssSize() {	return Math.min(TCPNetworkManager.getTcpMssSize(), UDPNetworkManager.getUdpMssSize()); }


	private static void refreshRates() {
		if (isSeedingOnlyUploadRate()) {
			maxUploadRateBps = max_upload_rate_bps_seeding_only;
		} else {
			maxUploadRateBps = max_upload_rate_bps_normal;
		}

		if (maxUploadRateBps < 1024) {
			Debug.out("max_upload_rate_bps < 1024=" +maxUploadRateBps);
		}

		//ensure that mss isn't greater than up/down rate limits
		int	minRate = Math.min(
			maxUploadRateBps,
			Math.min(
				maxDownloadRateBps,
				Math.min(
					maxLanUploadRateBps, 
					maxLanDownloadRateBps
				)
			)
		);

		TCPNetworkManager.refreshRates(minRate);
		UDPNetworkManager.refreshRates(minRate);
	}


	public static boolean isSeedingOnlyUploadRate() {
		return seeding_only_mode_allowed && seeding_only_mode;
	}

	public static int getMaxUploadRateBPSNormal() {
		if (max_upload_rate_bps_normal == UNLIMITED_RATE)	return 0;
		return max_upload_rate_bps_normal;
	}

	public static int getMaxUploadRateBPSSeedingOnly() {
		if (max_upload_rate_bps_seeding_only == UNLIMITED_RATE)	return 0;
		return max_upload_rate_bps_seeding_only;
	}

	/**
	 * This method is for display purposes only, the internal rate limiting is 10% higher than returned by this method!
	 */
	public static int getMaxDownloadRateBPS() {
		if (maxDownloadRateBps == UNLIMITED_RATE)	return 0;
		return external_max_download_rate_bps;
	}

	public static final int CRYPTO_OVERRIDE_NONE			= 0;
	public static final int CRYPTO_OVERRIDE_REQUIRED		= 1;
	public static final int CRYPTO_OVERRIDE_NOT_REQUIRED	= 2;


	public static boolean getCryptoRequired(int	override_level) {
		if (override_level == CRYPTO_OVERRIDE_NONE) {
			return (REQUIRE_CRYPTO_HANDSHAKE);
		} else if (override_level == CRYPTO_OVERRIDE_REQUIRED) {
			return (true);
		} else {
			return (false);
		}
	}

	public void initialize(AzureusCore core) {
		HTTPNetworkManager.getSingleton();

		core.getGlobalManager().addListener( 
			new GlobalManagerListener() {
				public void downloadManagerAdded(DownloadManager dm) {}
				public void downloadManagerRemoved(DownloadManager dm) {}
				public void destroyInitiated() {}
				public void destroyed() {}
	
				public void seedingStatusChanged(boolean seeding_only, boolean b) {
					seeding_only_mode = seeding_only;
					refreshRates();
				}
			}
		);
	}

	/**
	 * Get the singleton instance of the network manager.
	 * @return the network manager
	 */
	public static NetworkManager getSingleton() { return instance; }

	/**
	 * Create a new unconnected remote network connection (for outbound-initiated connections).
	 * @param remote_address to connect to
	 * @param encoder default message stream encoder to use for the outgoing queue
	 * @param decoder default message stream decoder to use for the incoming queue
	 * @return a new connection
	 */
	public NetworkConnection createConnection(ConnectionEndpoint target, MessageStreamEncoder encoder, MessageStreamDecoder decoder, boolean connect_with_crypto, boolean allow_fallback, byte[][] shared_secrets) {
		return NetworkConnectionFactory.create(target, encoder, decoder, connect_with_crypto, allow_fallback, shared_secrets);
	}

	/**
	 * Request the acceptance and routing of new incoming connections that match the given initial byte sequence.
	 * @param matcher initial byte sequence used for routing
	 * @param listener for handling new inbound connections
	 * @param factory to use for creating default stream encoder/decoders
	 */
	public void requestIncomingConnectionRouting(
		ByteMatcher 				matcher,
		final RoutingListener 		listener,
		final MessageStreamFactory 	factory) {
		IncomingConnectionManager.getSingleton().registerMatchBytes(
			matcher,
			new IncomingConnectionManager.MatchListener() {
				public boolean autoCryptoFallback() {
					return (listener.autoCryptoFallback());
				}
				
				public void connectionMatched(Transport	transport, Object routingData) {
					listener.connectionRouted(NetworkConnectionFactory.create(transport, factory.createEncoder(), factory.createDecoder()), routingData);
				}
			}
		);
	}

	public NetworkConnection bindTransport(
		Transport				transport,
		MessageStreamEncoder	encoder,
		MessageStreamDecoder	decoder) {
		return (NetworkConnectionFactory.create(transport, encoder, decoder));
	}


	/**
	 * Cancel a request for inbound connection routing.
	 * @param matcher byte sequence originally used to register
	 */
	public void cancelIncomingConnectionRouting(ByteMatcher matcher) {
		IncomingConnectionManager.getSingleton().deregisterMatchBytes(matcher);
	}


	/**
	 * Add an upload entity for write processing.
	 * @param entity to add
	 */
	public void addWriteEntity(RateControlledEntity entity, int partition_id) {
		if (writeControllers.size() == 1 || partition_id < 0) {
			writeControllers.get(0).addWriteEntity(entity);
		} else {
			WriteController controller = writeControllers.get((partition_id%(writeControllers.size()-1))+1);
			controller.addWriteEntity(entity);
		}
	}


	/**
	 * Remove an upload entity from write processing.
	 * @param entity to remove
	 */
	public void removeWriteEntity(RateControlledEntity entity) {
		if (writeControllers.size() == 1) {
			writeControllers.get(0).removeWriteEntity(entity);
		} else {
			for (WriteController write_controller: writeControllers) {
				write_controller.removeWriteEntity(entity);
			}
		}
	}


	/**
	 * Add a download entity for read processing.
	 * @param entity to add
	 */
	public void addReadEntity(RateControlledEntity entity, int partition_id) {
		if (readControllers.size() == 1 || partition_id < 0) {
			readControllers.get(0).addReadEntity(entity);
		} else {
			ReadController controller = readControllers.get((partition_id%(readControllers.size()-1))+1);
			controller.addReadEntity(entity);
		}
	}


	/**
	 * Remove a download entity from read processing.
	 * @param entity to remove
	 */
	public void removeReadEntity(RateControlledEntity entity) {
		if (readControllers.size() == 1) {
			readControllers.get(0).removeReadEntity(entity);
		} else {
			for (ReadController read_controller: readControllers) {
				read_controller.removeReadEntity(entity);
			}
		}
	}

	public Set<NetworkConnectionBase> getConnections() {
		Set<NetworkConnectionBase>	result = new HashSet<NetworkConnectionBase>();

		result.addAll(lanUploadProcessor.getConnections());
		result.addAll(lanDownloadProcessor.getConnections());
		result.addAll(uploadProcessor.getConnections());
		result.addAll(downloadProcessor.getConnections());

		return (result);
	}

	/**
	 * Register peer connection for network upload and download handling.
	 * NOTE: The given max rate limits are ignored until the connection is upgraded.
	 * NOTE: The given max rate limits are ignored for LANLocal connections.
	 * @param peerConnection to register for network transfer processing
	 * @param upload_group upload rate limit group
	 * @param download_group download rate limit group
	 */
	public void startTransferProcessing(NetworkConnectionBase peerConnection) {
		
		/*Log.d(TAG, "startTransferProcessing() is called...");
		Throwable t = new Throwable();
		t.printStackTrace();*/
		
		if (	
			lanRateEnabled &&
			(
				peerConnection.isLANLocal() ||
				AddressUtils.applyLANRateLimits(peerConnection.getEndpoint().getNotionalAddress())
			)
		) {
			lanUploadProcessor.registerPeerConnection(peerConnection, true);
			lanDownloadProcessor.registerPeerConnection(peerConnection, false);
		} else {
			uploadProcessor.registerPeerConnection(peerConnection, true);
			downloadProcessor.registerPeerConnection(peerConnection, false);
		}
	}


	/**
	 * Cancel network upload and download handling for the given connection.
	 * @param peer_connection to cancel
	 */
	public void stopTransferProcessing(NetworkConnectionBase peer_connection) {
		if (lanUploadProcessor.isRegistered( peer_connection)) {
			lanUploadProcessor.deregisterPeerConnection(peer_connection);
			lanDownloadProcessor.deregisterPeerConnection(peer_connection);
		} else {
			uploadProcessor.deregisterPeerConnection(peer_connection);
			downloadProcessor.deregisterPeerConnection(peer_connection);
		}
	}


	/**
	 * Upgrade the given connection to high-speed network transfer handling.
	 * @param peer_connection to upgrade
	 */
	public void upgradeTransferProcessing(NetworkConnectionBase peer_connection, int partition_id) {
		if (lanUploadProcessor.isRegistered( peer_connection)) {
			lanUploadProcessor.upgradePeerConnection(peer_connection, partition_id);
			lanDownloadProcessor.upgradePeerConnection(peer_connection, partition_id);
		} else {
			uploadProcessor.upgradePeerConnection(peer_connection, partition_id);
			downloadProcessor.upgradePeerConnection(peer_connection, partition_id);
		}
	}

	/**
	 * Downgrade the given connection back to a normal-speed network transfer handling.
	 * @param peer_connection to downgrade
	 */
	public void downgradeTransferProcessing(NetworkConnectionBase peer_connection) {
		if (lanUploadProcessor.isRegistered( peer_connection)) {
			lanUploadProcessor.downgradePeerConnection(peer_connection);
			lanDownloadProcessor.downgradePeerConnection(peer_connection);
		} else {
			uploadProcessor.downgradePeerConnection(peer_connection);
			downloadProcessor.downgradePeerConnection(peer_connection);
		}
	}

	public TransferProcessor getUploadProcessor() {
		return (uploadProcessor);
	}

	public void addRateLimiter(
		NetworkConnectionBase 	peer_connection,
		LimitedRateGroup		group,
		boolean					upload) {
		if (upload) {
			if (lanUploadProcessor.isRegistered( peer_connection)) {
				lanUploadProcessor.addRateLimiter(peer_connection, group);
			} else {
				uploadProcessor.addRateLimiter(peer_connection, group);
			}
		} else {
			if (lanDownloadProcessor.isRegistered( peer_connection)) {
				lanDownloadProcessor.addRateLimiter(peer_connection, group);
			} else {
				downloadProcessor.addRateLimiter(peer_connection, group);
			}
		}
	}

	public void removeRateLimiter(
		NetworkConnectionBase 	peer_connection,
		LimitedRateGroup		group,
		boolean					upload) {
		if (upload) {
			if (lanUploadProcessor.isRegistered( peer_connection)) {
				lanUploadProcessor.removeRateLimiter(peer_connection, group);
			} else {
				uploadProcessor.removeRateLimiter(peer_connection, group);
			}
		} else {
			if (lanDownloadProcessor.isRegistered( peer_connection)) {
				lanDownloadProcessor.removeRateLimiter(peer_connection, group);
			} else {
				downloadProcessor.removeRateLimiter(peer_connection, group);
			}
		}
	}

	public RateHandler getRateHandler(
		boolean					upload,
		boolean					lan) {
		if (upload) {
			if (lan) {
				return (lanUploadProcessor.getRateHandler());
			} else {
				return (uploadProcessor.getRateHandler());
			}
		} else {
			if (lan) {
				return (lanDownloadProcessor.getRateHandler());
			} else {
				return (downloadProcessor.getRateHandler());
			}
		}
	}

	public RateHandler getRateHandler(
		NetworkConnectionBase 	peer_connection,
		boolean					upload) {
		if (upload) {
			if (lanUploadProcessor.isRegistered( peer_connection)) {
				return (lanUploadProcessor.getRateHandler( peer_connection));
			} else {
				return (uploadProcessor.getRateHandler( peer_connection));
			}
		} else {
			if (lanDownloadProcessor.isRegistered( peer_connection)) {
				return (lanDownloadProcessor.getRateHandler( peer_connection));
			} else {
				return (downloadProcessor.getRateHandler( peer_connection));
			}
		}
	}

	/**
	 * Byte stream match filter for routing.
	 */
	public interface ByteMatcher {

		/**
		 * Number of bytes of buffer at or beyond which the "match" method will be called to test for a match
		 * @return
		 */
		public int matchThisSizeOrBigger();

		/**
		 * Get the max number of bytes this matcher requires. If it fails with this (or more) bytes then
		 * the connection will be dropped
		 * @return size in bytes
		 */
		public int maxSize();

		/**
		 * Get the minimum number of bytes required to determine if this matcher applies
		 * @return
		 */
		public int minSize();

		/**
		 * Check byte stream for match.
		 * @param address the originator of the connection
		 * @param to_compare
		 * @return	return "routing data" in case of a match, null otherwise
		 */
		public Object matches(TransportHelper transport, ByteBuffer to_compare, int port);

		/**
		 * Check for a minimum match
		 * @param to_compare
		 * @return return "routing data" in case of a match, null otherwise
		 */
		public Object minMatches(TransportHelper transport, ByteBuffer to_compare, int port);

		public byte[][] getSharedSecrets();

		public int getSpecificPort();
	}



	/**
	 * Listener for routing events.
	 */
	public interface RoutingListener {

		/**
		 * Currently if message crypto is on and default fallback for incoming not
		 * enabled then we would bounce incoming messages from non-crypto transports
		 * For example, NAT check
		 * This method allows auto-fallback for such transports
		 * @return
		 */
		public boolean autoCryptoFallback();

		/**
		 * The given incoming connection has been accepted.
		 * @param connection accepted
		 */
		public void connectionRouted(NetworkConnection connection, Object routing_data);
	}

}