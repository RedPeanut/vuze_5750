/*
 * Created on Aug 28, 2010
 * Created by Paul Gardner
 *
 * Copyright 2010 Vuze, Inc.  All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details (see the LICENSE file).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */



package com.aelitis.azureus.core.networkmanager.impl.utp;

import java.nio.ByteBuffer;
import java.util.List;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.logging.LogEvent;
import org.gudy.azureus2.core3.logging.LogIDs;
import org.gudy.azureus2.core3.logging.Logger;
import org.gudy.azureus2.core3.peer.PEPeer;
import org.gudy.azureus2.core3.peer.impl.PEPeerControl;
import org.gudy.azureus2.core3.util.AEGenericCallback;
import org.gudy.azureus2.core3.util.AERunnable;
import org.gudy.azureus2.core3.util.AsyncDispatcher;
import org.gudy.azureus2.core3.util.Debug;

import com.aelitis.azureus.core.networkmanager.NetworkConnection;
import com.aelitis.azureus.core.networkmanager.NetworkManager;
import com.aelitis.azureus.core.networkmanager.Transport.ConnectListener;
import com.aelitis.azureus.core.networkmanager.TransportEndpoint;
import com.aelitis.azureus.core.networkmanager.impl.ProtocolDecoder;
import com.aelitis.azureus.core.networkmanager.impl.TransportCryptoManager;
import com.aelitis.azureus.core.networkmanager.impl.TransportHelperFilter;
import com.aelitis.azureus.core.networkmanager.impl.TransportHelperFilterTransparent;
import com.aelitis.azureus.core.networkmanager.impl.TransportImpl;

import hello.util.Log;
import hello.util.SingleCounter0;


public class UTPTransport extends TransportImpl {
	
	private static String TAG = UTPTransport.class.getSimpleName();
	
	private static final LogIDs LOGID = LogIDs.NET;

	private static AsyncDispatcher	dispatcher = new AsyncDispatcher("utp:condisp");

	private UTPConnectionManager	manager;
	private ProtocolEndpointUTP		endpoint;
	private boolean					connect_with_crypto;
	private boolean					fallback_allowed;
	private byte[][]				shared_secrets;

	private int						fallback_count;

	private int transport_mode = TRANSPORT_MODE_NORMAL;

	private boolean				connected;
	private boolean 			cpPending;
	private ByteBuffer			cpInitialData;
	private ConnectListener		cpListener;

	private volatile boolean	closed;

	protected UTPTransport(
		UTPConnectionManager	_manager,
		ProtocolEndpointUTP		_endpoint,
		boolean 				_use_crypto,
		boolean 				_allow_fallback,
		byte[][] 				_shared_secrets )
	{
		manager					= _manager;
		endpoint				= _endpoint;
		connect_with_crypto 	= _use_crypto;
		shared_secrets			= _shared_secrets;
		fallback_allowed  		= _allow_fallback;
	}

	protected UTPTransport(
		UTPConnectionManager	_manager,
		ProtocolEndpointUTP		_endpoint,
		TransportHelperFilter	_filter ) {
		manager			= _manager;
		endpoint		= _endpoint;

		setFilter(_filter);
	}

	public boolean
	isTCP() {
		return (false);
	}

	public String getProtocol() {
		return ("uTP");
	}

	public TransportEndpoint
	getTransportEndpoint() {
		return (new TransportEndpointUTP( endpoint));
	}

	public int getMssSize() {
	  return ( UTPNetworkManager.getUdpMssSize());
	}

	public String
	getDescription() {
		return ( endpoint.getAddress().toString());
	}

	public void
	setTransportMode(
		int mode ) {
		transport_mode	= mode;
	}

	public int
	getTransportMode() {
		return (transport_mode);
	}

	public void connectOutbound(
		final ByteBuffer		initialData,
		final ConnectListener 	listener,
		final int				priority ) {
		if (!UTPNetworkManager.UTP_OUTGOING_ENABLED) {
			listener.connectFailure(new Throwable( "Outgoing uTP connections disabled"));
			return;
		}
		
		if (closed) {
			listener.connectFailure(new Throwable( "Connection already closed"));
			return;
		}
		
		if(getFilter() != null) {
			listener.connectFailure(new Throwable( "Already connected"));
			return;
		}
		
		if (COConfigurationManager.getBooleanParameter( "Proxy.Data.Enable")) {
			listener.connectFailure(new Throwable( "uTP proxy connection not supported"));
			return;
		}
		
		int time = listener.connectAttemptStarted(-1);
		if (time != -1) {
			Debug.out("uTP connect time override not supported");
		}
		
		UTPTransportHelper helper = null;
		try{
			helper = new UTPTransportHelper(manager, endpoint.getAddress(), this);
			final UTPTransportHelper f_helper = helper;
		  	if (connect_with_crypto) {
		    	TransportCryptoManager.getSingleton().manageCrypto(
		    		helper,
		    		shared_secrets,
		    		false,
		    		initialData,
		    		new TransportCryptoManager.HandshakeListener() {
		    			public void handshakeSuccess(
		    				ProtocolDecoder decoder,
		    				ByteBuffer 		remaining_initial_data )
		    			{
			    			TransportHelperFilter filter = decoder.getFilter();
			    			setFilter(filter);
			    			connectedOutbound(remaining_initial_data, listener);
		    			}
		    			
		    			public void handshakeFailure(Throwable failure_msg ) {
		    				if ( 	fallback_allowed &&
		    						NetworkManager.OUTGOING_HANDSHAKE_FALLBACK_ALLOWED &&
		    						!closed  ) {
		    					if (Logger.isEnabled()) {
		    						Logger.log(new LogEvent(LOGID, "crypto handshake failure [" +failure_msg.getMessage()+ "], attempting non-crypto fallback." ));
		    					}
		    					fallback_count++;
		    					connect_with_crypto = false;
		    					close(f_helper, "Handshake failure and retry");
		    					closed = false;
		    					if (initialData != null) {
		    						initialData.position(0);
		    					}
		    					connectOutbound(initialData, listener, priority);
		    				} else {
		    					close(f_helper, "Handshake failure");
		    					listener.connectFailure(failure_msg);
		    				}
		    			}
		    			
		    			public void gotSecret(		    				byte[]				session_secret )		    			{
		    			}
		    			
		    			public int getMaximumPlainHeaderLength() {
		    				throw (new RuntimeException());	// this is outgoing
		    			}
		    			
		    			public int matchPlainHeader(ByteBuffer buffer) {
		    				throw (new RuntimeException());	// this is outgoing
		    			}
		    		});
	  		} else {
		  		setFilter(new TransportHelperFilterTransparent( helper, false));
	  			// wait until we are actually connected before reporting this
		  		boolean	alreadyConnected = true;
		  		synchronized(this) {
		  			alreadyConnected = connected;
		  			if (!alreadyConnected) {
		  				cpPending		= true;
		  				cpInitialData	= initialData;
		  				cpListener		= listener;
		  			}
		  		}
		  		if (alreadyConnected) {
		  			connectedOutbound(initialData, listener);
		  		}
		  	}
		} catch (Throwable e) {
			Debug.printStackTrace(e);
			if (helper != null) {
				helper.close(Debug.getNestedExceptionMessage( e));
			}
			listener.connectFailure(e);
		}
	}

	protected void connected() {
		
		/*if (Once.getInstance().getAndIncreaseCount() < 1) {
			Log.d(TAG, "connected() is called...");
			new Throwable().printStackTrace();
		}*/
		
		final ByteBuffer		initialData;
		final ConnectListener	listener;
		synchronized(this) {
			connected = true;
			if (cpPending) {
				initialData		= cpInitialData;
				listener		= cpListener;
				cpPending 		= false;
				cpInitialData 	= null;
				cpListener		= null;
			} else {
				return;
			}
		}
		
		// need to get off of this thread due to deadlock potential
		dispatcher.dispatch(
			new AERunnable() {
				public void runSupport() {
					connectedOutbound(initialData, listener);
				}
			}
		);
	}

	protected void closed() {
		final ConnectListener	listener;
		synchronized(this) {
			if (cpPending) {
				cpPending = false;
				listener = cpListener;
				cpListener = null;
			} else {
				return;
			}
		}
		
		if (listener != null) {
			// need to get off of this thread due to deadlock potential
			dispatcher.dispatch(
				new AERunnable() {
					public void runSupport() {
						listener.connectFailure(new Throwable("Connection closed"));
					}
				}
			);
		}
	}

	protected void connectedOutbound(
		ByteBuffer			remainingInitialData,
		ConnectListener		listener) {
		
		TransportHelperFilter	filter = getFilter();
		if (Logger.isEnabled()) {
			Logger.log(new LogEvent(LOGID, "Outgoing uTP stream to " + endpoint.getAddress() + " established, type = " + (filter==null?"<unknown>":filter.getName(false))));
		}
		
		if (closed) {
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

	private void close(
		UTPTransportHelper		helper,
		String					reason) {
		helper.close(reason);
		close(reason);
	}

	public void close(String	reason) {
		closed	= true;

		readyForRead(false);
		readyForWrite(false);
		TransportHelperFilter	filter = getFilter();

		if (filter != null) {

			filter.getHelper().close(reason);

			setFilter(null);
		}

		closed();
	}

	public boolean isClosed() {
		return (closed);
	}

	public void bindConnection(NetworkConnection connection) {

		if (manager.preferUTP()) {
			final Object[] existing = { null };
			existing[0] =
				connection.setUserData(
				"RoutedCallback",
				new AEGenericCallback() {
					public Object invoke(Object arg) {
						try {
							PEPeerControl control = (PEPeerControl)arg;
							List<PEPeer> peers = control.getPeers( endpoint.getAddress().getAddress().getHostAddress());
							for (PEPeer peer: peers) {
								if ( 	!peer.isIncoming() &&
										peer.getTCPListenPort() == endpoint.getAddress().getPort()) {
									manager.log( "Overriding existing connection to " + endpoint.getAddress());
									control.removePeer(peer, "Replacing outgoing with incoming uTP connection");
								}
							}
							return (null);
						} finally {
							if (existing[0] instanceof AEGenericCallback) {
								((AEGenericCallback)existing[0]).invoke(arg);
							}
						}
					}
				});
		}
	}
}