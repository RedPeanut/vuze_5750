/*
 * Created on May 8, 2004
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

package com.aelitis.azureus.core.networkmanager.impl.tcp;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.logging.*;
import org.gudy.azureus2.core3.util.*;

import com.aelitis.azureus.core.networkmanager.*;
import com.aelitis.azureus.core.networkmanager.impl.ProtocolDecoder;
import com.aelitis.azureus.core.networkmanager.impl.TransportHelperFilter;
import com.aelitis.azureus.core.networkmanager.impl.TransportCryptoManager;
import com.aelitis.azureus.core.networkmanager.impl.TransportHelper;
import com.aelitis.azureus.core.networkmanager.impl.TransportImpl;
import com.aelitis.azureus.core.proxy.AEProxyFactory;
import com.aelitis.azureus.core.proxy.AEProxyFactory.PluginProxy;

import hello.util.Log;



/**
 * Represents a peer TCP transport connection (eg. a network socket).
 */
public class TCPTransportImpl extends TransportImpl implements Transport {
	
	private static String TAG = TCPTransportImpl.class.getSimpleName();
	
	private static final LogIDs LOGID = LogIDs.NET;

	private final ProtocolEndpointTCP protocolEndpoint;

	private TCPConnectionManager.ConnectListener connectRequestKey = null;
	private String description = "<disconnected>";
	private final boolean isInboundConnection;

	private int transportMode = TRANSPORT_MODE_NORMAL;

	public volatile boolean hasBeenClosed = false;

	private boolean 		connectWithCrypto;
	private byte[][]		sharedSecrets;
	private int				fallbackCount;
	private final boolean	fallbackAllowed;

	private boolean					isSocks;
	private volatile PluginProxy	pluginProxy;

	/**
	 * Constructor for disconnected (outbound) transport.
	 */
	public TCPTransportImpl(
		ProtocolEndpointTCP endpoint,
		boolean useCrypto,
		boolean allowFallback,
		byte[][] _sharedSecrets) {
		
		protocolEndpoint = endpoint;
		isInboundConnection = false;
		connectWithCrypto = useCrypto;
		sharedSecrets		= _sharedSecrets;
		fallbackAllowed	= allowFallback;
	}


	/**
	 * Constructor for connected (inbound) transport.
	 * @param channel connection
	 * @param already_read bytes from the channel
	 */
	public TCPTransportImpl(
		ProtocolEndpointTCP 	endpoint,
		TransportHelperFilter	filter) {
		protocolEndpoint = endpoint;
	
		setFilter(filter);

		isInboundConnection = true;
		connectWithCrypto = false;	//inbound connections will automatically be using crypto if necessary
		fallbackAllowed = false;

		InetSocketAddress address = endpoint.getAddress();
		description = (isInboundConnection ? "R" : "L" ) + ": " + AddressUtils.getHostNameNoResolve(address) + ": " + address.getPort();
	}

	/**
	 * Get the socket channel used by the transport.
	 * @return the socket channel
	 */
	public SocketChannel getSocketChannel() {
		TransportHelperFilter filter = getFilter();
		if (filter == null) {
			return null;
		}

		TCPTransportHelper helper = (TCPTransportHelper)filter.getHelper();
		if (helper == null) {
			return null;
		}

		return helper.getSocketChannel();
	}

	public TransportEndpointTCP getTransportEndpoint() {
		return (new TransportEndpointTCP(protocolEndpoint, getSocketChannel()));
	}

	public TransportStartpoint getTransportStartpoint() {
		return (new TransportStartpointTCP(getTransportEndpoint()));
	}

	public int getMssSize() {
		return (TCPNetworkManager.getTcpMssSize());
	}

	public boolean isTCP() {
		return (true);
	}

	public boolean isSOCKS() {
		return (isSocks);
	}

	public String getProtocol() {
		if (isSocks) {
			return ("TCP (SOCKS)");
		} else {
			return "TCP";
		}
	}

	/**
	 * Get a textual description for this transport.
	 * @return description
	 */
	public String getDescription() { return description; }


	/**
	 * Request the transport connection be established.
	 * NOTE: Will automatically connect via configured proxy if necessary.
	 * @param address remote peer address to connect to
	 * @param listener establishment failure/success listener
	 */
	public void connectOutbound(final ByteBuffer initialData,
			final ConnectListener listener,
			final int priority) {
		
		if (!TCPNetworkManager.TCP_OUTGOING_ENABLED) {
			listener.connectFailure(new Throwable("Outbound TCP connections disabled"));
			return;
		}
		
		if (hasBeenClosed) {
			listener.connectFailure(new Throwable("Connection already closed"));
			return;
		}
		
		if (getFilter() != null) {	//already connected
			Debug.out("socket_channel != null");
			listener.connectSuccess(this, initialData);
			return;
		}
		
		final InetSocketAddress	address = protocolEndpoint.getAddress();
		if (!address.equals(ProxyLoginHandler.DEFAULT_SOCKS_SERVER_ADDRESS)) {

			// see if a plugin can handle this connection
			if (address.isUnresolved()) {
				String host = address.getHostName();
				if (AENetworkClassifier.categoriseAddress(host) != AENetworkClassifier.AT_PUBLIC) {
					Map<String,Object>	opts = new HashMap<String,Object>();
					Object peer_nets = listener.getConnectionProperty(AEProxyFactory.PO_PEER_NETWORKS);
					if (peer_nets != null) {
						opts.put(AEProxyFactory.PO_PEER_NETWORKS, peer_nets);
					}
					PluginProxy pp = pluginProxy;
					pluginProxy = null;
					if (pp != null) {
						// most likely crypto fallback connection so don't assume it is a bad
						// outcome
						pp.setOK(true);
					}
					pluginProxy = AEProxyFactory.getPluginProxy("outbound connection", host, address.getPort(), opts);
				}
			}
			if (pluginProxy == null) {
			 	isSocks = COConfigurationManager.getBooleanParameter("Proxy.Data.Enable");
			}
		}
		
		final TCPTransportImpl transportInstance = this;

		TCPConnectionManager.ConnectListener connectListener = new TCPConnectionManager.ConnectListener() {
			
			public int connectAttemptStarted(int defaultConnectTimeout) {
				return (listener.connectAttemptStarted(defaultConnectTimeout));
			}
			
			public void connectSuccess(final SocketChannel channel) {
				if (channel == null) {
					String msg = "connectSuccess:: given channel == null";
					Debug.out(msg);
					setConnectResult(false);
					listener.connectFailure(new Exception( msg ));
					return;
				}
				
				if (hasBeenClosed) { //closed between select ops
					TCPNetworkManager.getSingleton().getConnectDisconnectManager().closeConnection(channel);	//just close it
					setConnectResult(false);
					listener.connectFailure(new Throwable("Connection has been closed"));
					return;
				}
				
				connectRequestKey = null;
				description = (isInboundConnection ? "R" : "L") + ": " + channel.socket().getInetAddress().getHostAddress() + ": " + channel.socket().getPort();
				PluginProxy pp = pluginProxy;
				
				if (isSocks) {	//proxy server connection established, login
					
					//Log.d(TAG, ">>> 1");
					
					if (Logger.isEnabled())
						Logger.log(new LogEvent(LOGID,"Socket connection established to proxy server [" +description+ "], login initiated..."));
					
					// set up a transparent filter for socks negotiation
					setFilter(TCPTransportHelperFilterFactory.createTransparentFilter(channel));
					new ProxyLoginHandler( transportInstance, address, new ProxyLoginHandler.ProxyListener() {
						public void connectSuccess() {
							if (Logger.isEnabled())
								Logger.log(new LogEvent(LOGID, "Proxy [" +description+ "] login successful." ));
							handleCrypto(address, channel, initialData, priority, listener);
						}
						public void connectFailure(Throwable failure_msg) {
							close("Proxy login failed");
							listener.connectFailure(failure_msg);
						}
					});
				} else if (pp != null) {
					
					//Log.d(TAG, ">>> 2");
					
				 	if (Logger.isEnabled()) {
				 		Logger.log(new LogEvent(LOGID,"Socket connection established via plugin proxy [" +description+ "], login initiated..."));
				 	}
				 	
			 		// set up a transparent filter for socks negotiation
					setFilter(TCPTransportHelperFilterFactory.createTransparentFilter( channel));
					String pp_host = pp.getHost();
					InetSocketAddress ia_address;
					if (AENetworkClassifier.categoriseAddress(pp_host) == AENetworkClassifier.AT_PUBLIC) {
						ia_address = new InetSocketAddress(pp.getHost(), pp.getPort());
					} else {
						ia_address = InetSocketAddress.createUnresolved( pp_host, pp.getPort());
					}
					new ProxyLoginHandler(
						transportInstance,
						ia_address,
						new ProxyLoginHandler.ProxyListener() {
							public void connectSuccess() {
								if (Logger.isEnabled()) {
									Logger.log(new LogEvent(LOGID, "Proxy [" +description+ "] login successful." ));
								}
								setConnectResult(true);
								handleCrypto(address, channel, initialData, priority, listener);
							}
							
							public void connectFailure(
								Throwable failure_msg) {
								setConnectResult(false);
								close("Proxy login failed");
								listener.connectFailure(failure_msg);
							}
						},
						"V4a", "", "");
				} else {
					
					//Log.d(TAG, ">>> 3");
					
					//direct connection established, notify
					handleCrypto(address, channel, initialData, priority, listener);
				}
			}
			
			public void connectFailure(Throwable failure_msg) {
				connectRequestKey = null;
				setConnectResult(false);
				listener.connectFailure(failure_msg);
			}
		};
		
		connectRequestKey = connectListener;
		InetSocketAddress toConnect;
		PluginProxy pp = pluginProxy;
		if (isSocks) {
			toConnect = ProxyLoginHandler.getProxyAddress(address);
		} else if (pp != null) {
			toConnect = (InetSocketAddress)pp.getProxy().address();
		} else {
			toConnect = address;
		}
		TCPNetworkManager.getSingleton().getConnectDisconnectManager().requestNewConnection(toConnect, connectListener, priority);
	}


	protected void handleCrypto(
		final InetSocketAddress 	address,
		final SocketChannel 		channel,
		final ByteBuffer 			initialData,
		final int	 				priority,
		final ConnectListener 		listener) {
		
		//Log.d(TAG, ">>> connectWithCrypto = " + connectWithCrypto);
		
		if (connectWithCrypto) {
			
			//attempt encrypted transport
			final TransportHelper	helper = new TCPTransportHelper(channel);
			TransportCryptoManager.getSingleton().manageCrypto(
					helper, 
					sharedSecrets, 
					false, initialData, 
					new TransportCryptoManager.HandshakeListener() {
						public void handshakeSuccess(ProtocolDecoder decoder, ByteBuffer remainingInitialData) {
							//System.out.println(description+ " | crypto handshake success [" +_filter.getName()+ "]");
							TransportHelperFilter filter = decoder.getFilter();
							setFilter(filter);
							if (Logger.isEnabled()) {
									Logger.log(new LogEvent(LOGID, "Outgoing TCP stream to " + channel.socket().getRemoteSocketAddress() + " established, type = " + filter.getName(false)));
							}
		
							connectedOutbound(remainingInitialData, listener);
				 		}
		
						public void handshakeFailure(Throwable failure_msg) {
							if (fallbackAllowed && NetworkManager.OUTGOING_HANDSHAKE_FALLBACK_ALLOWED && !hasBeenClosed) {
								if (Logger.isEnabled() ) Logger.log(new LogEvent(LOGID, description+ " | crypto handshake failure [" +failure_msg.getMessage()+ "], attempting non-crypto fallback."));
								connectWithCrypto = false;
								fallbackCount++;
						 		close(helper, "Handshake failure and retry");
								hasBeenClosed = false;
								if (initialData != null) {
		
									initialData.position(0);
								}
								connectOutbound(initialData, listener, priority);
							} else {
								close(helper, "Handshake failure");
								listener.connectFailure(failure_msg);
							}
						}
		
						public void gotSecret(byte[] session_secret) {
						}
		
						public int getMaximumPlainHeaderLength() {
							throw (new RuntimeException());	// this is outgoing
						}
		
						public int matchPlainHeader(ByteBuffer buffer) {
							throw (new RuntimeException());	// this is outgoing
						}
					}
			);
		} else {	//no crypto
			
			//if (fallback_count > 0) {
			//	System.out.println(channel.socket()+ " | non-crypto fallback successful!");
			//}
			setFilter(TCPTransportHelperFilterFactory.createTransparentFilter(channel));

			if (Logger.isEnabled()) {
				Logger.log(new LogEvent(LOGID, "Outgoing TCP stream to " + channel.socket().getRemoteSocketAddress() + " established, type = " + getFilter().getName(false) + ", fallback = " + (fallbackCount==0?"no":"yes" )));
			}
	
			connectedOutbound(initialData, listener);
		}
	}

	private void setTransportBuffersSize(int sizeInBytes) {
		if (getFilter() == null) {
			Debug.out("socket_channel == null");
			return;
		}

		try {
			SocketChannel	channel = getSocketChannel();

			channel.socket().setSendBufferSize(sizeInBytes);
			channel.socket().setReceiveBufferSize(sizeInBytes);

			int snd_real = channel.socket().getSendBufferSize();
			int rcv_real = channel.socket().getReceiveBufferSize();

			if (Logger.isEnabled())
				Logger.log(new LogEvent(LOGID, "Setting new transport [" + description
					+ "] buffer sizes: SND=" + sizeInBytes + " [" + snd_real
					+ "] , RCV=" + sizeInBytes + " [" + rcv_real + "]"));
		} catch (Throwable t) {
			Debug.out(t);
		}
	}


	/**
	 * Set the transport to the given speed mode.
	 * @param mode to change to
	 */
	public void setTransportMode(int mode) {
		if (mode == transportMode)	return;	//already in mode
		switch(mode) {
			case TRANSPORT_MODE_NORMAL:
				setTransportBuffersSize(8 * 1024);
				break;
			case TRANSPORT_MODE_FAST:
				setTransportBuffersSize(64 * 1024);
				break;
			case TRANSPORT_MODE_TURBO:
				setTransportBuffersSize(512 * 1024);
				break;
			default:
				Debug.out("invalid transport mode given: " +mode);
		}
		transportMode = mode;
	}

	protected void connectedOutbound(
		ByteBuffer			remainingInitialData,
		ConnectListener		listener) {
		if (hasBeenClosed) {
			TransportHelperFilter	filter = getFilter();
			if (filter != null) {
				filter.getHelper().close("Connection closed");
				setFilter(null);
			}
			listener.connectFailure(new Throwable("Connection closed"));
		} else {
			connectedOutbound();
			listener.connectSuccess(this, remainingInitialData);
		}
	}

	/**
	 * Get the transport's speed mode.
	 * @return current mode
	 */
	public int getTransportMode() {	return transportMode;	}

	protected void close(
		TransportHelper		helper,
		String				reason) {
		helper.close(reason);
		close(reason);
	}

	private void setConnectResult(boolean ok) {
		PluginProxy pp = pluginProxy;
		if (pp != null) {
			pluginProxy = null;
			pp.setOK(ok);
		}
	}

	/**
	 * Close the transport connection.
	 */
	public void close(String reason) {
		hasBeenClosed = true;
		setConnectResult(false);
		if (connectRequestKey != null) {
			TCPNetworkManager.getSingleton().getConnectDisconnectManager().cancelRequest(connectRequestKey);
		}
		readyForRead(false);
		readyForWrite(false);
		TransportHelperFilter filter = getFilter();
		if (filter != null) {
			filter.getHelper().close(reason);
			setFilter(null);
		}
		
		// we need to set it ready for reading so that the other scheduling thread wakes up
		// and discovers that this connection has been closed
		setReadyForRead();
	}
}
