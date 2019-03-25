/*
 * Created on Sep 13, 2004
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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnresolvedAddressException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.config.ParameterListener;
import org.gudy.azureus2.core3.logging.LogAlert;
import org.gudy.azureus2.core3.logging.LogEvent;
import org.gudy.azureus2.core3.logging.LogIDs;
import org.gudy.azureus2.core3.logging.Logger;
import org.gudy.azureus2.core3.util.AEMonitor;
import org.gudy.azureus2.core3.util.AEThread2;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.RandomUtils;
import org.gudy.azureus2.core3.util.SystemTime;

import com.aelitis.azureus.core.networkmanager.ProtocolEndpoint;
import com.aelitis.azureus.core.networkmanager.VirtualChannelSelector;
import com.aelitis.azureus.core.networkmanager.admin.NetworkAdmin;
import com.aelitis.azureus.core.proxy.AEProxyFactory;
import com.aelitis.azureus.core.stats.AzureusCoreStats;
import com.aelitis.azureus.core.stats.AzureusCoreStatsProvider;

import hello.util.Log;
import hello.util.SingleCounter0;




/**
 * Manages new connection establishment and ended connection termination.
 */
public class TCPConnectionManager {
	
	private String TAG = TCPConnectionManager.class.getSimpleName();
	
	private static final LogIDs LOGID = LogIDs.NWMAN;

	private static int CONNECT_SELECT_LOOP_TIME			= 100;
	private static int CONNECT_SELECT_LOOP_MIN_TIME		= 0;

	private static int MIN_SIMULTANIOUS_CONNECT_ATTEMPTS = 3;
	public static int MAX_SIMULTANIOUS_CONNECT_ATTEMPTS;

	private static int maxOutboundConnections;

	static {
		MAX_SIMULTANIOUS_CONNECT_ATTEMPTS = COConfigurationManager.getIntParameter("network.max.simultaneous.connect.attempts");

		if (MAX_SIMULTANIOUS_CONNECT_ATTEMPTS < 1) { //should never happen, but hey
	 	 MAX_SIMULTANIOUS_CONNECT_ATTEMPTS = 1;
	 	 COConfigurationManager.setParameter("network.max.simultaneous.connect.attempts", 1);
		}

		MIN_SIMULTANIOUS_CONNECT_ATTEMPTS = MAX_SIMULTANIOUS_CONNECT_ATTEMPTS - 2;

		if (MIN_SIMULTANIOUS_CONNECT_ATTEMPTS < 1) {
			MIN_SIMULTANIOUS_CONNECT_ATTEMPTS = 1;
		}

		COConfigurationManager.addParameterListener("network.max.simultaneous.connect.attempts", new ParameterListener() {
			public void parameterChanged(String parameterName) {
				MAX_SIMULTANIOUS_CONNECT_ATTEMPTS = COConfigurationManager.getIntParameter("network.max.simultaneous.connect.attempts");
				MIN_SIMULTANIOUS_CONNECT_ATTEMPTS = MAX_SIMULTANIOUS_CONNECT_ATTEMPTS - 2;
				if (MIN_SIMULTANIOUS_CONNECT_ATTEMPTS < 1) {
					MIN_SIMULTANIOUS_CONNECT_ATTEMPTS = 1;
				}
			}
		});

	COConfigurationManager.addAndFireParameterListeners(
			new String[]{
					"network.tcp.max.connections.outstanding",
			},
			new ParameterListener() {
				public void parameterChanged(
					String name) {
					maxOutboundConnections = COConfigurationManager.getIntParameter("network.tcp.max.connections.outstanding");
				}
			});

	COConfigurationManager.addAndFireParameterListeners(
			new String[]{
				"network.tcp.connect.select.time",
				"network.tcp.connect.select.min.time",
			},
			new ParameterListener() {
				public void parameterChanged(
					String name) {
					CONNECT_SELECT_LOOP_TIME 		= COConfigurationManager.getIntParameter("network.tcp.connect.select.time");
					CONNECT_SELECT_LOOP_MIN_TIME 	= COConfigurationManager.getIntParameter("network.tcp.connect.select.min.time");
				}
			});
	}

	private int rcvSize;
	private int sndSize;
	private String ipTos;
	private int localBindPort;

	{
		COConfigurationManager.addAndFireParameterListeners(
			new String[]{
				"network.tcp.socket.SO_RCVBUF",
				"network.tcp.socket.SO_SNDBUF",
				"network.tcp.socket.IPDiffServ",
				"network.bind.local.port"
			},
			new ParameterListener() {
				public void parameterChanged(String name) {
					rcvSize = COConfigurationManager.getIntParameter("network.tcp.socket.SO_RCVBUF");
					sndSize = COConfigurationManager.getIntParameter("network.tcp.socket.SO_SNDBUF");
					ipTos = COConfigurationManager.getStringParameter("network.tcp.socket.IPDiffServ");
					localBindPort = COConfigurationManager.getIntParameter("network.bind.local.port");
				}
			});
	}

	private static final int CONNECT_ATTEMPT_TIMEOUT = 15*1000;	// parg: reduced from 30 sec as almost never see worthwhile connections take longer that this
	private static final int CONNECT_ATTEMPT_STALL_TIME = 3*1000;	//3sec
	private static final boolean SHOW_CONNECT_STATS = false;

	private final VirtualChannelSelector connectSelector = 
			new VirtualChannelSelector("Connect/Disconnect Manager", VirtualChannelSelector.OP_CONNECT, true);

	private long connectionRequestIdNext;

	private final Set<ConnectionRequest> newRequests =
		new TreeSet<ConnectionRequest>(
			new Comparator<ConnectionRequest>() {
				public int compare(
					ConnectionRequest r1,
					ConnectionRequest r2) {
					if (r1 == r2) {
						return (0);
					}
					int	res = r1.getPriority() - r2.getPriority();
					if (res == 0) {
						// check for duplicates
						// erm, no, think of the socks data proxy connections luke
						/*
						InetSocketAddress a1 = r1.address;
						InetSocketAddress a2 = r2.address;
						if (a1.getPort() == a2.getPort()) {
							if (Arrays.equals(a1.getAddress().getAddress(), a2.getAddress().getAddress())) {
								return (0);
							}
						}
						*/
						res = r1.getRandom() - r2.getRandom();
						if (res == 0) {
							long l = r1.getID() - r2.getID();
							if (l < 0) {
								res = -1;
							} else if (l > 0) {
								res = 1;
							} else {
								Debug.out("arghhh, borkage");
							}
						}
					}
					return (res);
				}
			});
	
	private final List<ConnectListener> 		canceledRequests 	= new ArrayList<ConnectListener>();
	private final AEMonitor	newCanceledMon	= new AEMonitor("ConnectDisconnectManager:NCM");
	private final Map<ConnectionRequest,Object> pendingAttempts 	= new HashMap<ConnectionRequest, Object>();
	private final LinkedList<SocketChannel> 	pendingCloses 		= new LinkedList<SocketChannel>();
	private final Map<SocketChannel,Long>		delayedCloses		= new HashMap<SocketChannel, Long>();
	private final AEMonitor	pendingClosesMon = new AEMonitor("ConnectDisconnectManager:PC");
	private boolean maxConnExceededLogged;

	public TCPConnectionManager() {
		
		/*
		Log.d(TAG, "ctor() is called...");
		new Throwable().printStackTrace();
		//*/
		
		Set<String>	types = new HashSet<String>();
		types.add(AzureusCoreStats.ST_NET_TCP_OUT_CONNECT_QUEUE_LENGTH);
		types.add(AzureusCoreStats.ST_NET_TCP_OUT_CANCEL_QUEUE_LENGTH);
		types.add(AzureusCoreStats.ST_NET_TCP_OUT_CLOSE_QUEUE_LENGTH);
		types.add(AzureusCoreStats.ST_NET_TCP_OUT_PENDING_QUEUE_LENGTH);
		AzureusCoreStats.registerProvider(
				types,
				new AzureusCoreStatsProvider() {
					public void updateStats(
						Set<String>				types,
						Map<String,Object>		values) {
						if (types.contains(AzureusCoreStats.ST_NET_TCP_OUT_CONNECT_QUEUE_LENGTH)) {
							values.put(AzureusCoreStats.ST_NET_TCP_OUT_CONNECT_QUEUE_LENGTH, new Long(newRequests.size()));
						}
						if (types.contains(AzureusCoreStats.ST_NET_TCP_OUT_CANCEL_QUEUE_LENGTH)) {
							values.put(AzureusCoreStats.ST_NET_TCP_OUT_CANCEL_QUEUE_LENGTH, new Long(canceledRequests.size()));
						}
						if (types.contains(AzureusCoreStats.ST_NET_TCP_OUT_CLOSE_QUEUE_LENGTH)) {
							values.put(AzureusCoreStats.ST_NET_TCP_OUT_CLOSE_QUEUE_LENGTH, new Long(pendingCloses.size()));
						}
						if (types.contains(AzureusCoreStats.ST_NET_TCP_OUT_PENDING_QUEUE_LENGTH)) {
							values.put(AzureusCoreStats.ST_NET_TCP_OUT_PENDING_QUEUE_LENGTH, new Long(pendingAttempts.size()));
						}
					}
				});
		
		new AEThread2("ConnectDisconnectManager", true) {
			public void run() {
				while (true) {
					addNewOutboundRequests();
					runSelect();
					doClosings();
				}
			}
		}.start();
	}

	public int getMaxOutboundPermitted() {
		return (Math.max(maxOutboundConnections - newRequests.size(), 0));
	}

	private void addNewOutboundRequests() {
		while (pendingAttempts.size() < MIN_SIMULTANIOUS_CONNECT_ATTEMPTS) {
			ConnectionRequest cr = null;
			try {
				newCanceledMon.enter();
				if (newRequests.isEmpty()) break;
				Iterator<ConnectionRequest> it = newRequests.iterator();
				cr = it.next();
				it.remove();
			} finally {
				newCanceledMon.exit();
			}
			if (cr != null) {
				addNewRequest(cr);
			}
		}
	}

	private void addNewRequest(final ConnectionRequest request) {
		
		/*
		if (Once.getInstance().getAndIncreaseCount() < 1) {
			Log.d(TAG, "addNewRequest() is called...");
			Log.d(TAG, "request.address = " + request.address);
			new Throwable().printStackTrace();
		}
		//*/
		
		request.setConnectTimeout(request.listener.connectAttemptStarted(request.getConnectTimeout()));

		boolean	ipv6problem	= false;
		boolean	bindFailed	= false;
		try {
			request.channel = SocketChannel.open();
			InetAddress bindIP = null;
			try {
				//advanced socket options
				if (rcvSize > 0) {
					if (Logger.isEnabled())
						Logger.log(new LogEvent(LOGID, "Setting socket receive buffer size"
								+ " for outgoing connection [" + request.address + "] to: "
								+ rcvSize));
					request.channel.socket().setReceiveBufferSize(rcvSize);
				}
				
				if (sndSize > 0) {
					if (Logger.isEnabled())
						Logger.log(new LogEvent(LOGID, "Setting socket send buffer size "
								+ "for outgoing connection [" + request.address + "] to: "
								+ sndSize));
					request.channel.socket().setSendBufferSize(sndSize);
				}
				
				if (ipTos.length() > 0) {
					if (Logger.isEnabled())
						Logger.log(new LogEvent(LOGID, "Setting socket TOS field "
								+ "for outgoing connection [" + request.address + "] to: "
								+ ipTos));
					request.channel.socket().setTrafficClass(Integer.decode( ipTos ).intValue());
				}
				
				if (localBindPort > 0) {
					request.channel.socket().setReuseAddress(true);
				}
				
				try {
					bindIP = NetworkAdmin.getSingleton().getMultiHomedOutgoingRoundRobinBindAddress(request.address.getAddress());
					if (bindIP != null) {
						// ignore bind for plugin proxies as we connect directly to them - if they need to
						// enforce any bindings on delegated connections then that is their job to implement
						if (bindIP.isAnyLocalAddress() || !AEProxyFactory.isPluginProxy(request.address)) {
							if (Logger.isEnabled()) 	Logger.log(new LogEvent(LOGID, "Binding outgoing connection [" + request.address + "] to local IP address: " + bindIP+":"+localBindPort));
							request.channel.socket().bind(new InetSocketAddress(bindIP, localBindPort));
						}
					} else if (localBindPort > 0) {
						if (Logger.isEnabled()) Logger.log(new LogEvent(LOGID, "Binding outgoing connection [" + request.address + "] to local port #: " +localBindPort));
						request.channel.socket().bind(new InetSocketAddress(localBindPort));
					}
				} catch (SocketException e) {
					bindFailed = true;
					String msg = e.getMessage().toLowerCase();
					if (	
						(
							msg.contains("address family not supported by protocol family")
							|| msg.contains("protocol family unavailable")
						)
						&& !NetworkAdmin.getSingleton().hasIPV6Potential(true)) {
						ipv6problem = true;
					}
					throw e;
				}
			} catch (Throwable t) {
				if (bindFailed && NetworkAdmin.getSingleton().mustBind()) {
					// force binding is set but failed, must bail here - can happen during VPN disconnect for
					// example, before we switch to a localhost bind
					throw (t);
				} else if (!ipv6problem) {
					//dont pass the exception outwards, so we will continue processing connection without advanced options set
					String msg = "Error while processing advanced socket options (rcv=" + rcvSize + ", snd=" + sndSize + ", tos=" + ipTos + ", port=" + localBindPort + ", bind=" + bindIP + ")";
					//Debug.out(msg, t);
					Logger.log(new LogAlert(LogAlert.UNREPEATABLE, msg, t));
				} else {
					// can't support NIO + ipv6 on this system, pass on and don't raise an alert
					throw (t);
				}
			}
			
			request.channel.configureBlocking(false);
			request.connectStartTime = SystemTime.getMonotonousTime();
			if (request.channel.connect(request.address)) {	//already connected
				finishConnect(request);
			} else {
				//not yet connected, so register for connect selection
				try {
					newCanceledMon.enter();
					pendingAttempts.put(request, null);
				} finally {
					newCanceledMon.exit();
				}
				
				connectSelector.register(
						request.channel,
						new VirtualChannelSelector.VirtualSelectorListener() {
							
							public boolean selectSuccess(
								VirtualChannelSelector 	selector,
								SocketChannel 			sc,
								Object 					attachment) {
								try {
									newCanceledMon.enter();
									pendingAttempts.remove(request);
								} finally {
									newCanceledMon.exit();
								}
								finishConnect(request);
								return true;
							}
							
							public void selectFailure(
									VirtualChannelSelector 	selector,
									SocketChannel 			sc,
									Object 					attachment,
									Throwable 				msg ) {
								try {
									newCanceledMon.enter();
									pendingAttempts.remove(request);
								} finally {
									newCanceledMon.exit();
								}
								closeConnection(request.channel);
								request.listener.connectFailure(msg);
							}
						}
						, null
				);
			}
		} catch (Throwable t) {
			String full = request.address.toString();
			String hostname = request.address.getHostName();
			int port = request.address.getPort();
			boolean unresolved = request.address.isUnresolved();
			InetAddress	inet_address = request.address.getAddress();
			String full_sub = inet_address==null?request.address.toString():inet_address.toString();
			String host_address = inet_address==null?request.address.toString():inet_address.getHostAddress();
			String msg = "ConnectDisconnectManager::address exception: full="+full+ ", hostname="+hostname+ ", port="+port+ ", unresolved="+unresolved+ ", full_sub="+full_sub+ ", host_address="+host_address;
			if (request.channel != null) {
				String channel = request.channel.toString();
				Socket socket = request.channel.socket();
				String socket_string = socket.toString();
				InetAddress local_address = socket.getLocalAddress();
				String local_address_string = local_address == null ? "<null>" : local_address.toString();
				int local_port = socket.getLocalPort();
				SocketAddress ra = socket.getRemoteSocketAddress();
				String remote_address;
				if (ra != null)	remote_address = ra.toString();
				else remote_address = "<null>";
				int remote_port = socket.getPort();
				msg += "\n channel="+channel+ ", socket="+socket_string+ ", local_address="+local_address_string+ ", local_port="+local_port+ ", remote_address="+remote_address+ ", remote_port="+remote_port;
			}
			else {
				msg += "\n channel=<null>";
			}
			if (ipv6problem || t instanceof UnresolvedAddressException || t instanceof NoRouteToHostException) {
				Logger.log(new LogEvent(LOGID,LogEvent.LT_WARNING,msg));
			} else {
				Logger.log(new LogEvent(LOGID,LogEvent.LT_ERROR,msg,t));
			}
			if (request.channel != null) {
				closeConnection(request.channel);
			}
			request.listener.connectFailure(t);
		}
	}

	private void finishConnect(ConnectionRequest request) {
		try {
			if (request.channel.finishConnect()) {
				if (SHOW_CONNECT_STATS) {
					long queueWaitTime = request.connectStartTime - request.requestStartTime;
					long connectTime = SystemTime.getMonotonousTime() - request.connectStartTime;
					int numQueued = newRequests.size();
					int numConnecting = pendingAttempts.size();
					System.out.println("S: queueWaitTime="+queueWaitTime+
							", connectTime="+connectTime+
							", numQueued="+numQueued+
							", numConnecting="+numConnecting);
				}
				//ensure the request hasn't been canceled during the select op
				boolean canceled = false;
				try {
					newCanceledMon.enter();
					canceled = canceledRequests.contains(request.listener);
				} finally {
					newCanceledMon.exit();
				}
				if (canceled) {
					closeConnection(request.channel);
				} else {
					connectSelector.cancel(request.channel);
					request.listener.connectSuccess(request.channel);
				}
			} else {
				//should never happen
				Debug.out("finishConnect() failed");
				request.listener.connectFailure(new Throwable("finishConnect() failed" ));
				closeConnection(request.channel);
			}
		} catch (Throwable t) {
			if (SHOW_CONNECT_STATS) {
				long queue_wait_time = request.connectStartTime - request.requestStartTime;
				long connect_time = SystemTime.getMonotonousTime() - request.connectStartTime;
				int num_queued = newRequests.size();
				int num_connecting = pendingAttempts.size();
				System.out.println("F: queue_wait_time="+queue_wait_time+
						", connect_time="+connect_time+
						", num_queued="+num_queued+
						", num_connecting="+num_connecting);
			}
			request.listener.connectFailure(t);
			closeConnection(request.channel);
		}
	}

	private void runSelect() {
		//do cancellations
		try {
			newCanceledMon.enter();
			for (Iterator<ConnectListener> can_it = canceledRequests.iterator(); can_it.hasNext();) {
				ConnectListener key = can_it.next();
				for (Iterator<ConnectionRequest> pen_it = pendingAttempts.keySet().iterator(); pen_it.hasNext();) {
					ConnectionRequest request = pen_it.next();
					if (request.listener == key) {
						connectSelector.cancel(request.channel);
						closeConnection(request.channel);
						pen_it.remove();
						break;
					}
				}
			}
			canceledRequests.clear();
		} finally {
			newCanceledMon.exit();
		}
		
		//run select
		try {
			if (CONNECT_SELECT_LOOP_MIN_TIME > 0) {
				long	start = SystemTime.getHighPrecisionCounter();
				connectSelector.select(CONNECT_SELECT_LOOP_TIME);
				long duration = SystemTime.getHighPrecisionCounter() - start;
				duration = duration/1000000;
				long	sleep = CONNECT_SELECT_LOOP_MIN_TIME - duration;
				if (sleep > 0) {
					try {
						Thread.sleep(sleep);
					} catch (Throwable e) {
					}
				}
			} else {
				connectSelector.select(CONNECT_SELECT_LOOP_TIME);
			}
		} catch (Throwable t) {
			Debug.out("connnectSelectLoop() EXCEPTION: ", t);
		}
		
		//do connect attempt timeout checks
		int numStalledRequests = 0;
		final long now = SystemTime.getMonotonousTime();
		List<ConnectionRequest> timeouts = null;
		try {
			newCanceledMon.enter();
			for (Iterator<ConnectionRequest> i = pendingAttempts.keySet().iterator(); i.hasNext();) {
				final ConnectionRequest request = i.next();
				final long waiting_time =now -request.connectStartTime;
				if (waiting_time > request.connectTimeout) {
					i.remove();
					SocketChannel channel = request.channel;
					connectSelector.cancel(channel);
					closeConnection(channel);
					if (timeouts == null) {
						timeouts = new ArrayList<ConnectionRequest>();
					}
					timeouts.add(request);
				} else if (waiting_time >= CONNECT_ATTEMPT_STALL_TIME) {
					numStalledRequests++;
				} else if (waiting_time < 0) {	//time went backwards
					request.connectStartTime =now;
				}
			}
		} finally {
			newCanceledMon.exit();
		}
		
		if (timeouts != null) {
			for (ConnectionRequest request: timeouts) {
				InetSocketAddress	sock_address = request.address;
				 	InetAddress a = sock_address.getAddress();
				 	String	target;
				 	if (a != null) {
						target = a.getHostAddress() + ":" + sock_address.getPort();
					} else {
						target = sock_address.toString();
					}
					request.listener.connectFailure(new SocketTimeoutException("Connection attempt to " + target + " aborted: timed out after " + request.connectTimeout/1000+ "sec" ));
			}
		}
		
		//check if our connect queue is stalled, and expand if so
		if (pendingAttempts.size()  == numStalledRequests 
				&& pendingAttempts.size() < MAX_SIMULTANIOUS_CONNECT_ATTEMPTS) {
			ConnectionRequest cr =null;
			try {
				newCanceledMon.enter();
				if (!newRequests.isEmpty()) {
					Iterator<ConnectionRequest> it = newRequests.iterator();
					cr = it.next();
					it.remove();
				}
			} finally {
				newCanceledMon.exit();
			}
			if (cr != null) {
				addNewRequest(cr);
			}
		}
	}


	private void doClosings() {
		try {
			pendingClosesMon.enter();
			long	now = SystemTime.getMonotonousTime();
			if (delayedCloses.size() > 0) {
				Iterator<Map.Entry<SocketChannel,Long>>	it = delayedCloses.entrySet().iterator();
				while (it.hasNext()) {
					Map.Entry<SocketChannel,Long>	entry = (Map.Entry<SocketChannel,Long>)it.next();
					long	wait = ((Long)entry.getValue()).longValue() - now;
					if (wait < 0 || wait > 60*1000) {
						pendingCloses.addLast( entry.getKey());
						it.remove();
					}
				}
			}
			while (!pendingCloses.isEmpty()) {
				SocketChannel channel = pendingCloses.removeFirst();
				if (channel != null) {
					connectSelector.cancel(channel);
					try {
						channel.close();
					} catch (Throwable t) {
						/*Debug.printStackTrace(t);*/
					}
				}
			}
		} finally {
			pendingClosesMon.exit();
		}
	}


	/**
	 * Request that a new connection be made out to the given address.
	 * @param address remote ip+port to connect to
	 * @param listener to receive notification of connect attempt success/failure
	 */
	public void requestNewConnection(InetSocketAddress address, ConnectListener listener, int priority) {
		requestNewConnection(address, listener, CONNECT_ATTEMPT_TIMEOUT, priority);
	}

	public void requestNewConnection(
		InetSocketAddress 	address,
		ConnectListener 	listener,
		int					connectTimeout,
		int 				priority) {
		
		/*if (SingleCounter.getInstance().getAndIncreaseCount() < 2) {
			Log.d(TAG, "requestNewConnection() is called...");
			Log.d(TAG, "address = " + address);
			new Throwable().printStackTrace();
		}*/
		
		if (address.getPort() == 0) {
			try {
				listener.connectFailure(new Exception("Invalid port, connection to " + address + " abandoned"));
			} catch (Throwable e) {
				Debug.out(e);
			}
			return;
		}
		
		List<ConnectionRequest>	kicked 		= null;
		boolean					duplicate	= false;
		try {
			newCanceledMon.enter();
			
			//insert at a random position because new connections are usually added in 50-peer
			//chunks, i.e. from a tracker announce reply, and we want to evenly distribute the
			//connect attempts if there are multiple torrents running
			ConnectionRequest cr = new ConnectionRequest(connectionRequestIdNext++, address, listener, connectTimeout, priority);
			
			// this comparison is via Comparator and will weed out same address being added > once
			if (newRequests.contains(cr)) {
				duplicate = true;
			} else {
				newRequests.add(cr);
				if (newRequests.size() >= maxOutboundConnections) {
					if (!maxConnExceededLogged) {
						maxConnExceededLogged = true;
						Debug.out("TCPConnectionManager: max outbound connection limit reached (" + maxOutboundConnections + ")");
					}
				}
				
				if (priority == ProtocolEndpoint.CONNECT_PRIORITY_HIGHEST) {
					for (Iterator<ConnectionRequest> pen_it = pendingAttempts.keySet().iterator(); pen_it.hasNext();) {
						ConnectionRequest request =(ConnectionRequest) pen_it.next();
						if (request.priority == ProtocolEndpoint.CONNECT_PRIORITY_LOW) {
							if (!canceledRequests.contains( request.listener)) {
								canceledRequests.add(request.listener);
								if (kicked == null) {
									kicked = new ArrayList<ConnectionRequest>();
								}
								kicked.add(request);
							}
						}
					}
				}
			}
		} finally {
			newCanceledMon.exit();
		}
		
		if (duplicate) {
			try {
				listener.connectFailure(new Exception("Connection request already queued for " + address));
			} catch (Throwable e) {
				Debug.out(e);
			}
		}
		
		if (kicked != null) {
			for (int i=0;i<kicked.size();i++) {
				try {
					((ConnectionRequest)kicked.get(i)).listener.connectFailure(
						 new Exception("Low priority connection request abandoned in favour of high priority"));
				} catch (Throwable e) {
					Debug.printStackTrace(e);
				}
			}
		}
	}

	/**
	 * Close the given connection.
	 * @param channel to close
	 */
	public void closeConnection(SocketChannel channel) {
		closeConnection(channel, 0);
	}

	public void closeConnection(
		SocketChannel channel,
		int delay) {
		try {
			pendingClosesMon.enter();
			if (delay == 0) {
				if (!delayedCloses.containsKey(channel)) {
					if (!pendingCloses.contains(channel)) {
						pendingCloses.addLast(channel);
					}
				}
			} else {
				delayedCloses.put(channel, new Long(SystemTime.getMonotonousTime() + delay));
			}
		} finally {
			pendingClosesMon.exit();
		}
	}


	/**
	 * Cancel a pending new connection request.
	 * @param listener_key used in the initial connect request
	 */
	public void cancelRequest(ConnectListener listener_key) {
		try {
			newCanceledMon.enter();

			//check if we can cancel it right away
			for (Iterator<ConnectionRequest> i = newRequests.iterator(); i.hasNext();) {
				ConnectionRequest request = i.next();
				if (request.listener == listener_key) {
					i.remove();
					return;
				}
			}

			canceledRequests.add(listener_key); //else add for later removal during select
		}
		finally{
			newCanceledMon.exit();
		}
	}



	private static class ConnectionRequest {
		private final InetSocketAddress address;
		private final ConnectListener listener;
		private final long requestStartTime;
		private long connectStartTime;
		private int connectTimeout;
		private SocketChannel	channel;
		private final short		rand;
		private final int		priority;
		private final long		id;

		private ConnectionRequest(long _id, InetSocketAddress _address, ConnectListener _listener, int _connect_timeout, int _priority) {
			id	= _id;
			address = _address;
			listener = _listener;
			connectTimeout	= _connect_timeout;
			requestStartTime = SystemTime.getMonotonousTime();
			rand = (short)(RandomUtils.nextInt(Short.MAX_VALUE));
			priority = _priority;
		}

		private int getConnectTimeout() {
			return (connectTimeout);
		}

		private void setConnectTimeout(int _ct) {
			connectTimeout = _ct;
		}

		private long getID() {
			return (id);
		}

		private int getPriority() {
			return (priority);
		}

		private short getRandom() {
			return (rand);
		}
	}


///////////////////////////////////////////////////////////

	/**
	 * Listener for notification of connection establishment.
	 */
	public interface ConnectListener {
		/**
		 * The connection establishment process has started,
		 * i.e. the connection is actively being attempted.
		 * @return adjusted connect timeout
		 */
		public int connectAttemptStarted(int default_timeout);

		/**
		 * The connection attempt succeeded.
		 * @param channel connected socket channel
		 */
		public void connectSuccess(SocketChannel channel) ;


		/**
		 * The connection attempt failed.
		 * @param failure_msg failure reason
		 */
		public void connectFailure(Throwable failure_msg);
	}

/////////////////////////////////////////////////////////////

}
