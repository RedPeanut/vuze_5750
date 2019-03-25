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

package com.aelitis.azureus.core.networkmanager.impl;


import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

import org.gudy.azureus2.core3.util.AddressUtils;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.LightHashMap;

import com.aelitis.azureus.core.networkmanager.*;
import com.aelitis.azureus.core.peermanager.messaging.MessageStreamDecoder;
import com.aelitis.azureus.core.peermanager.messaging.MessageStreamEncoder;

import hello.util.Log;
import hello.util.SingleCounter0;



/**
 *
 */

public class NetworkConnectionImpl
	extends NetworkConnectionHelper
	implements NetworkConnection
{
	private static String TAG = NetworkConnectionImpl.class.getSimpleName();
	
	private final ConnectionEndpoint	connectionEndpoint;
	private final boolean				isIncoming;

	private boolean connectWithCrypto;
	private boolean allowFallback;
	private byte[][] sharedSecrets;

	private ConnectionListener connectionListener;
	private boolean 	isConnected;
	private byte		isLanLocal	= AddressUtils.LAN_LOCAL_MAYBE;

	private final OutgoingMessageQueueImpl outgoingMessageQueue;
	private final IncomingMessageQueueImpl incomingMessageQueue;

	private Transport	transport;

	private volatile ConnectionAttempt	connectionAttempt;
	private volatile boolean			closed;

	private Map<Object,Object>			userData;

	/**
	 * Constructor for new OUTbound connection.
	 * The connection is not yet established upon instantiation; use connect() to do so.
	 * @param _remote_address to connect to
	 * @param encoder default message stream encoder to use for the outgoing queue
	 * @param decoder default message stream decoder to use for the incoming queue
	 */
	public NetworkConnectionImpl(
			ConnectionEndpoint _target, 
			MessageStreamEncoder encoder,
			MessageStreamDecoder decoder, 
			boolean _connectWithCrypto, 
			boolean _allowFallback,
			byte[][] _sharedSecrets) {
		
		/*
		if (Once.getInstance().getAndIncreaseCount() < 1) {
			Log.d(TAG, "OUTbound ctor()...");
			new Throwable().printStackTrace();
		}//*/
		
		connectionEndpoint	= _target;
		isIncoming			= false;
		connectWithCrypto	= _connectWithCrypto;
		allowFallback = _allowFallback;
		sharedSecrets = _sharedSecrets;

		isConnected = false;
		outgoingMessageQueue = new OutgoingMessageQueueImpl(encoder);
		incomingMessageQueue = new IncomingMessageQueueImpl(decoder, this);
	}


	/**
	 * Constructor for new INbound connection.
	 * The connection is assumed to be already established, by the given already-connected channel.
	 * @param _remote_channel connected by
	 * @param data_already_read bytestream already read during routing
	 * @param encoder default message stream encoder to use for the outgoing queue
	 * @param decoder default message stream decoder to use for the incoming queue
	 */
	public NetworkConnectionImpl(Transport _transport,
			MessageStreamEncoder encoder, 
			MessageStreamDecoder decoder) {
		
		/*
		if (Once.getInstance().getAndIncreaseCount() < 1) {
			Log.d(TAG, "INbound ctor()...");
			new Throwable().printStackTrace();
		}
		//*/
		
		transport = _transport;
		connectionEndpoint = transport.getTransportEndpoint().getProtocolEndpoint().getConnectionEndpoint();
		isIncoming		= true;
		isConnected 	= true;
		outgoingMessageQueue = new OutgoingMessageQueueImpl(encoder);
		outgoingMessageQueue.setTransport(transport);
		incomingMessageQueue = new IncomingMessageQueueImpl(decoder, this);

		transport.bindConnection(this);
	}


	public ConnectionEndpoint getEndpoint() {
		return (connectionEndpoint);
	}

	public boolean isIncoming() {
		return (isIncoming);
	}

	public void connect(int priority, ConnectionListener listener) {
		connect(null, priority, listener);
	}

	public void connect(ByteBuffer initialOutboundData, int priority, ConnectionListener listener) {
		this.connectionListener = listener;
		if (isConnected) {
			connectionListener.connectStarted(-1);
			connectionListener.connectSuccess(initialOutboundData);
			return;
		}
		if (connectionAttempt != null) {
			Debug.out("Connection attempt already active");
			listener.connectFailure(new Throwable("Connection attempt already active"));
			return;
		}
		
		connectionAttempt =
			connectionEndpoint.connectOutbound(
					connectWithCrypto,
					allowFallback,
					sharedSecrets,
					initialOutboundData,
					priority,
					new Transport.ConnectListener() {
						
						//int once = 0;
						
						public int connectAttemptStarted(int defaultConnectTimeout) {
							return (connectionListener.connectStarted(defaultConnectTimeout));
						}
						
						public void connectSuccess(Transport _transport,
								ByteBuffer remainingInitialData) {
							
							//if (once++ == 0) {
								//new Throwable().printStackTrace();
							//}
							
							isConnected = true;
							transport	= _transport;
							outgoingMessageQueue.setTransport(transport);
							transport.bindConnection(NetworkConnectionImpl.this);
							connectionListener.connectSuccess(remainingInitialData);
							connectionAttempt	= null;
						}
						
						public void connectFailure(Throwable failureMsg) {
							isConnected = false;
							connectionListener.connectFailure(failureMsg);
						}
						
						public Object getConnectionProperty(String propertyName) {
							return (connectionListener.getConnectionProperty(propertyName));
						}
					}
			);
		
		if (closed) {
			ConnectionAttempt ca = connectionAttempt;
			if (ca != null) {
				ca.abandon();
			}
		}
	}

	public Transport detachTransport() {
		Transport	t = transport;
		if (t != null) {
			t.unbindConnection(this);
		}
		transport = new bogusTransport(transport);
		close("detached transport");
		return (t);
	}

	public void close(String reason) {
		NetworkManager.getSingleton().stopTransferProcessing(this);
		closed	= true;
		if (connectionAttempt != null) {
			connectionAttempt.abandon();
		}
		if (transport != null) {
			transport.close("Tidy close" + ( reason==null||reason.length()==0?"":(": " + reason)));
		}
		incomingMessageQueue.destroy();
	 	outgoingMessageQueue.destroy();
		isConnected = false;
	}


	public void notifyOfException(Throwable error) {
		if (connectionListener != null) {
			connectionListener.exceptionThrown(error);
		} else {
			Debug.out("notifyOfException():: connection_listener == null for exception: " +error.getMessage());
		}
	}

	public OutgoingMessageQueue getOutgoingMessageQueue() { return outgoingMessageQueue; }
	public IncomingMessageQueue getIncomingMessageQueue() { return incomingMessageQueue; }

	public void startMessageProcessing() {
		NetworkManager.getSingleton().startTransferProcessing(this);
	}

	public void enableEnhancedMessageProcessing(boolean enable, int partition_id) {
		if (enable) {
			NetworkManager.getSingleton().upgradeTransferProcessing(this, partition_id);
		} else {
			NetworkManager.getSingleton().downgradeTransferProcessing(this);
		}
	}

	public Transport getTransport() { return transport; }
	public TransportBase getTransportBase() { return transport; }

	public int getMssSize() {
		if (transport == null) {
			return ( NetworkManager.getMinMssSize());
		} else {
			return ( transport.getMssSize());
		}
	}


	public Object setUserData(
		Object		key,
		Object		value ) {
	synchronized(this) {
		if (userData == null) {
			userData = new LightHashMap<Object, Object>();
		}
		return (userData.put( key, value));
	}
	}

	public Object getUserData(
		Object		key ) {
		synchronized(this) {
			if (userData == null) {
				return (null);
			}
			return (userData.get( key));
		}
	}

	public String toString() {
		return (transport==null?connectionEndpoint.getDescription():transport.getDescription());
	}


	public boolean isConnected() {
		return isConnected;
	}


	public boolean isLANLocal() {
		if (isLanLocal == AddressUtils.LAN_LOCAL_MAYBE) {
			isLanLocal = AddressUtils.isLANLocalAddress( connectionEndpoint.getNotionalAddress());
		}
		return (isLanLocal == AddressUtils.LAN_LOCAL_YES);
	}

	public String getString() {
		return ("tran=" + (transport==null?"null":transport.getDescription()+",w_ready=" + transport.isReadyForWrite(null)+",r_ready=" + transport.isReadyForRead( null))+ ",in=" + incomingMessageQueue.getPercentDoneOfCurrentMessage() +
				",out=" + (outgoingMessageQueue==null?0:outgoingMessageQueue.getTotalSize()) + ",owner=" + (connectionListener==null?"null":connectionListener.getDescription()));
	}

	protected static class bogusTransport
		implements Transport
	{
		private final Transport transport;

		protected bogusTransport(Transport	_transport) {
			transport = _transport;
		}

		public boolean isReadyForWrite(EventWaiter waiter) {
			return (false);
		}

		public long isReadyForRead(EventWaiter waiter) {
			return (Long.MAX_VALUE);
		}

		public boolean isTCP() {
			return (transport.isTCP());
		}

		public boolean isSOCKS()		{
			return ( transport.isSOCKS());
		}

		public String getDescription() {
			return ( transport.getDescription());
		}

		public int getMssSize() {
			return ( transport.getMssSize());
		}

		public void setAlreadyRead(ByteBuffer bytes_already_read) {
			Debug.out("Bogus Transport Operation");
		}

		public TransportEndpoint getTransportEndpoint() {
			return (transport.getTransportEndpoint());
		}

		public TransportStartpoint getTransportStartpoint() {
			return ( transport.getTransportStartpoint());
		}

		public boolean isEncrypted() {
			return ( transport.isEncrypted());
		}

		public String getEncryption( boolean verbose) {
			return (transport.getEncryption( verbose));
		}

		public String getProtocol() { return transport.getProtocol(); }

		public void setReadyForRead() {
			Debug.out("Bogus Transport Operation");
		}

		public long write(
			ByteBuffer[] buffers,
			int array_offset,
			int length )
			throws IOException
		{
			Debug.out("Bogus Transport Operation");

			throw (new IOException("Bogus transport!"));
		}

		public long read(ByteBuffer[] buffers, int array_offset, int length )
			throws IOException
		{
			Debug.out("Bogus Transport Operation");

			throw (new IOException("Bogus transport!"));
		}

		public void setTransportMode(int mode) {
			Debug.out("Bogus Transport Operation");
		}

		public int getTransportMode() {
			return ( transport.getTransportMode());
		}

		public void connectOutbound(
			ByteBuffer			initial_data,
			ConnectListener 	listener,
			int					priority) {
			Debug.out("Bogus Transport Operation");

			listener.connectFailure(new Throwable("Bogus Transport"));
		}

		public void connectedInbound() {
			Debug.out("Bogus Transport Operation");
		}

		public void close(String reason) {
			// we get here after detaching a transport and then closing the peer connection
		}

		public void bindConnection(NetworkConnection	connection) {
		}

		public void unbindConnection(NetworkConnection	connection) {
		}

		public void setTrace(boolean	on) {
		}
	}
}
