/*
 * File    : PRUDPPacketReceiverImpl.java
 * Created : 20-Jan-2004
 * By      : parg
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details (see the LICENSE file).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.aelitis.net.udp.uc.impl;

/**
 * @author parg
 *
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.BindException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.PasswordAuthentication;
import java.net.SocketTimeoutException;
import java.nio.channels.UnsupportedAddressTypeException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.config.ParameterListener;
import org.gudy.azureus2.core3.logging.LogAlert;
import org.gudy.azureus2.core3.logging.LogEvent;
import org.gudy.azureus2.core3.logging.LogIDs;
import org.gudy.azureus2.core3.logging.Logger;
import org.gudy.azureus2.core3.util.AEMonitor;
import org.gudy.azureus2.core3.util.AEMonitor2;
import org.gudy.azureus2.core3.util.AESemaphore;
import org.gudy.azureus2.core3.util.AEThread;
import org.gudy.azureus2.core3.util.AEThread2;
import org.gudy.azureus2.core3.util.Average;
import org.gudy.azureus2.core3.util.Constants;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.LightHashMap;
import org.gudy.azureus2.core3.util.SHA1Hasher;
import org.gudy.azureus2.core3.util.SimpleTimer;
import org.gudy.azureus2.core3.util.SystemTime;
import org.gudy.azureus2.core3.util.TimerEvent;
import org.gudy.azureus2.core3.util.TimerEventPerformer;
import org.gudy.azureus2.core3.util.TimerEventPeriodic;
import org.gudy.bouncycastle.util.encoders.Base64;

import com.aelitis.azureus.core.networkmanager.admin.NetworkAdmin;
import com.aelitis.azureus.core.networkmanager.admin.NetworkAdminPropertyChangeListener;
import com.aelitis.azureus.core.util.AEPriorityMixin;
import com.aelitis.azureus.core.util.CopyOnWriteList;
import com.aelitis.net.udp.uc.PRUDPPacket;
import com.aelitis.net.udp.uc.PRUDPPacketHandler;
import com.aelitis.net.udp.uc.PRUDPPacketHandlerException;
import com.aelitis.net.udp.uc.PRUDPPacketHandlerStats;
import com.aelitis.net.udp.uc.PRUDPPacketReceiver;
import com.aelitis.net.udp.uc.PRUDPPacketReply;
import com.aelitis.net.udp.uc.PRUDPPacketRequest;
import com.aelitis.net.udp.uc.PRUDPPrimordialHandler;
import com.aelitis.net.udp.uc.PRUDPRequestHandler;

import hello.util.Log;
import hello.util.SingleCounter0;
import hello.util.Util;

public class PRUDPPacketHandlerImpl implements PRUDPPacketHandler {
	
	private static String TAG = PRUDPPacketHandlerImpl.class.getSimpleName();
	
	private static boolean LOG_SEND_RECV = false;
	private static boolean BLOCK_SEND = false;
	
	private static final LogIDs LOGID = LogIDs.NET;

	private boolean		TRACE_REQUESTS	= false;

	private static int 	MAX_PACKET_SIZE;

	static {
		COConfigurationManager.addAndFireParameterListener(
			"network.udp.mtu.size",
			new ParameterListener() {
				public void parameterChanged(String parameterName) {
					//Log.d(TAG, "parameterChanged() is called...");
					//new Throwable().printStackTrace();
					//Log.d(TAG, "MAX_PACKET_SIZE = " + MAX_PACKET_SIZE);
					MAX_PACKET_SIZE = COConfigurationManager.getIntParameter(parameterName);
				}
			});
	}

	private static final long	MAX_SEND_QUEUE_DATA_SIZE	= 2*1024*1024;
	private static final long	MAX_RECV_QUEUE_DATA_SIZE	= 1*1024*1024;

	private static boolean	useSocks;

	static {
		COConfigurationManager.addAndFireParameterListeners(
			new String[] {
				"Enable.Proxy",
				"Enable.SOCKS",
			},
			new ParameterListener() {
				public void parameterChanged(String parameter_name) {
					boolean	enable_proxy 	= COConfigurationManager.getBooleanParameter("Enable.Proxy");
				    boolean enable_socks	= COConfigurationManager.getBooleanParameter("Enable.SOCKS");

				    useSocks = enable_proxy && enable_socks;
				}
			});
	}


	private int				port;
	private DatagramSocket	socket;

	private CopyOnWriteList<PRUDPPrimordialHandler>	primordialHandlers = new CopyOnWriteList<PRUDPPrimordialHandler>();
	private PRUDPRequestHandler						requestHandler;

	private PRUDPPacketHandlerStatsImpl	stats = new PRUDPPacketHandlerStatsImpl(this);


	private Map<Integer, PRUDPPacketHandlerRequestImpl> requests = new LightHashMap<>();
	private AEMonitor2	requestsMonitor = new AEMonitor2("PRUDPPH:req");


	private AEMonitor2		sendQueueMonitor = new AEMonitor2("PRUDPPH:sd");
	private long			sendQueueDataSize;
	private final List[]	sendQueues = new List[]{ new LinkedList(),new LinkedList(),new LinkedList()};
	private AESemaphore		sendQueueSemaphore = new AESemaphore("PRUDPPH:sq");
	private AEThread		sendThread;

	private AEMonitor		recvQueueMonitor = new AEMonitor("PRUDPPH:rq");
	private long			recvQueueDataSize;
	private List<Object[]>	recvQueue = new ArrayList<>();
	private AESemaphore		recvQueueSemaphore = new AESemaphore("PRUDPPH:rq");
	private AEThread		recvThread;

	private int			sendDelay				= 0;
	private int			receiveDelay			= 0;
	private int			queuedRequestTimeout	= 0;

	private long		totalRequestsReceived;
	private long		totalRequestsProcessed;
	private long		totalReplies;
	private long		lastErrorReport;
	private Average		requestReceiveAverage = Average.getInstance(1000, 10);

	private AEMonitor	bindAddressMon	= new AEMonitor("PRUDPPH:bind");

	private InetAddress				defaultBindIp;
	private InetAddress				explicitBindIp;

	private volatile InetAddress	currentBindIp;
	private volatile InetAddress	targetBindIp;

	private volatile boolean		failed;
	private volatile boolean		destroyed;
	private AESemaphore destroySemaphore = new AESemaphore("PRUDPPacketHandler:destroy");

	private Throwable 	initError;

	private PRUDPPacketHandlerImpl altProtocolDelegate;

	private final PacketTransformer	packetTransformer;

	protected PRUDPPacketHandlerImpl(
		int					_port,
		InetAddress			_bindIp,
		PacketTransformer	_packetTransformer) {
		
		//int count = SingleCounter0.getInstance().getAndIncreaseCount();
		//Log.d(TAG, String.format("how many times is this called... #%d", count));
		//Log.d(TAG, "and where -->");
		//new Throwable().printStackTrace();
		/*Log.d(TAG, "<init>() is called...");
		new Throwable().printStackTrace();*/

		port				= _port;
		explicitBindIp		= _bindIp;
		packetTransformer	= _packetTransformer;
		defaultBindIp		= NetworkAdmin.getSingleton().getSingleHomedServiceBindAddress();
		
		//Log.d(TAG, "port = " + port);
		//Log.d(TAG, "explicitBindIp = " + explicitBindIp);
		//Log.d(TAG, "defaultBindIp = " + defaultBindIp);
		
		calcBind();
		
		final AESemaphore initSemaphore = new AESemaphore("PRUDPPacketHandler:init");
		new AEThread2("PRUDPPacketReciever:" + port, true) {
			public void run() {
				receiveLoop(initSemaphore);
			}
		}.start();

		final TimerEventPeriodic[]	f_ev = {null};
		TimerEventPeriodic ev =
			SimpleTimer.addPeriodicEvent(
				"PRUDP:timeouts",
				5000,
				new TimerEventPerformer() {
					public void perform(TimerEvent	event) {
						if (destroyed && f_ev[0] != null) {
							f_ev[0].cancel();
						}
						checkTimeouts();
					}
				}
			);
		f_ev[0] = ev;
		initSemaphore.reserve();
	}

	public boolean hasPrimordialHandler() {
		synchronized(primordialHandlers) {
			return (primordialHandlers.size() > 0);
		}
	}

	public void addPrimordialHandler(PRUDPPrimordialHandler	handler) {
		
		synchronized(primordialHandlers) {
			if (primordialHandlers.contains(handler)) {
				Debug.out("Primordial handler already added!");
				return;
			}
			
			int	priority;
			if (handler instanceof AEPriorityMixin) {
				priority = ((AEPriorityMixin)handler).getPriority();
			} else {
				priority = AEPriorityMixin.PRIORITY_NORMAL;
			}
			
			List<PRUDPPrimordialHandler> existing = primordialHandlers.getList();
			int	insertAt = -1;
			for (int i=0;i<existing.size();i++) {
				PRUDPPrimordialHandler e = existing.get(i);
				int	existing_priority;
				if (e instanceof AEPriorityMixin) {
					existing_priority = ((AEPriorityMixin)e).getPriority();
				} else {
					existing_priority = AEPriorityMixin.PRIORITY_NORMAL;
				}
				if (existing_priority < priority) {
					insertAt = i;
					break;
				}
			}
			
			if (insertAt >= 0) {
				primordialHandlers.add(insertAt, handler);
			} else {
				primordialHandlers.add(handler);
			}
		}
		// if we have an altProtocolDelegate then this shares the list of handlers so no need to add
	}

	public void removePrimordialHandler(PRUDPPrimordialHandler	handler) {
		synchronized(primordialHandlers) {
			if (!primordialHandlers.contains( handler)) {
				Debug.out("Primordial handler not found!");
				return;
			}
			primordialHandlers.remove(handler);
		}
		// if we have an altProtocolDelegate then this shares the list of handlers so no need to remove
	}

	public void setRequestHandler(PRUDPRequestHandler _requestHandler) {
		
		
		
		if (requestHandler != null) {
			if (_requestHandler != null) {
				// if we need to support this then the handler will have to be associated
				// with a message type map, or we chain together and give each handler
				// a bite at processing the message
				throw (new RuntimeException("Multiple handlers per endpoint not supported"));
			}
		}
		requestHandler = _requestHandler;
		PRUDPPacketHandlerImpl delegate = altProtocolDelegate;
		if (delegate != null) {
			delegate.setRequestHandler(_requestHandler);
		}
	}

	public PRUDPRequestHandler getRequestHandler() {
		return (requestHandler);
	}

	public int getPort() {
		if (port == 0 && socket != null) {
			return (socket.getLocalPort());
		}
		return (port);
	}

	public InetAddress getBindIP() {
		return (currentBindIp);
	}

	protected void setDefaultBindAddress(InetAddress address) {
		try {
			bindAddressMon.enter();
			defaultBindIp = address;
			calcBind();
		} finally {
			bindAddressMon.exit();
		}
	}

	public void setExplicitBindAddress(InetAddress address) {
		
		try {
			bindAddressMon.enter();
			explicitBindIp	= address;
			calcBind();
		} finally {
			bindAddressMon.exit();
		}
		int	loops = 0;
		while (currentBindIp != targetBindIp && !(failed || destroyed)) {
			if (loops >= 100) {
				Debug.out("Giving up on wait for bind ip change to take effect");
				break;
			}
			try {
				Thread.sleep(50);
				loops++;
			} catch (Throwable e) {
				break;
			}
		}
	}

	protected void calcBind() {
		if (explicitBindIp != null) {

			if (altProtocolDelegate != null) {
				altProtocolDelegate.destroy();
				altProtocolDelegate = null;
			}

			targetBindIp = explicitBindIp;

		} else {

			InetAddress altAddress = null;
			NetworkAdmin adm = NetworkAdmin.getSingleton();
			try {
				if (defaultBindIp instanceof Inet6Address && !defaultBindIp.isAnyLocalAddress() && adm.hasIPV4Potential())
					altAddress = adm.getSingleHomedServiceBindAddress(NetworkAdmin.IP_PROTOCOL_VERSION_REQUIRE_V4);
				else if (defaultBindIp instanceof Inet4Address && adm.hasIPV6Potential())
					altAddress = adm.getSingleHomedServiceBindAddress(NetworkAdmin.IP_PROTOCOL_VERSION_REQUIRE_V6);
			} catch (UnsupportedAddressTypeException e) {
			}

			if (altProtocolDelegate != null && !altProtocolDelegate.explicitBindIp.equals(altAddress)) {
				altProtocolDelegate.destroy();
				altProtocolDelegate = null;
			}

			if (altAddress != null && altProtocolDelegate == null) {
				altProtocolDelegate = new PRUDPPacketHandlerImpl(port,altAddress,packetTransformer);
				altProtocolDelegate.stats = stats;
				altProtocolDelegate.primordialHandlers = primordialHandlers;
				altProtocolDelegate.requestHandler = requestHandler;
			}
			targetBindIp = defaultBindIp;
		}
	}

	protected void receiveLoop(AESemaphore initSem) {
		
		long lastSocketCloseTime = 0;
		
		NetworkAdminPropertyChangeListener propListener =
			new NetworkAdminPropertyChangeListener() {
				public void propertyChanged(String property) {
					if (property == NetworkAdmin.PR_DEFAULT_BIND_ADDRESS) {
						setDefaultBindAddress(NetworkAdmin.getSingleton().getSingleHomedServiceBindAddress());
					}
				}
			};
		NetworkAdmin.getSingleton().addPropertyChangeListener(propListener);
		try {
			// outter loop picks up bind-ip changes
			while (!(failed || destroyed)) {
				if (socket != null) {
					try {
						Log.d(TAG, "closing socket...");
						socket.close();
					} catch (Throwable e) {
						Debug.printStackTrace(e);
					}
				}
				
				InetSocketAddress	address		= null;
				DatagramSocket		newSocket	= null;
				try {
					//Log.d(TAG, "targetBindIp = " + targetBindIp);
					Log.d(TAG, "make new socket...");
					if (targetBindIp == null) {
						address = new InetSocketAddress("127.0.0.1", port);
						newSocket = new DatagramSocket(port);
					} else {
						address = new InetSocketAddress(targetBindIp, port);
						newSocket = new DatagramSocket(address);
					}
				} catch (BindException e) {
					// some firewalls (e.g. Comodo) seem to close sockets on us and then not release them quickly so we come through here and get
					// an 'address already in use' failure
					boolean	rebindWorked = false;
					int	delay = 25;
					for (int i=0;i<16 && !(failed || destroyed);i++) {
						try {
							Thread.sleep(delay);
							delay = delay * 2;
							if (delay > 1000) {
								delay = 1000;
							}
							if (targetBindIp == null) {
								address = new InetSocketAddress("127.0.0.1", port);
								newSocket = new DatagramSocket(port);
							} else {
								address = new InetSocketAddress(targetBindIp, port);
								newSocket = new DatagramSocket(address);
							}
							if (Logger.isEnabled())
								Logger.log(new LogEvent(LOGID,"PRUDPPacketReceiver: rebind to " + targetBindIp + " worked (tries=" + (i+1) + ") after getting " + Debug.getNestedExceptionMessage(e)));
							rebindWorked = true;
							break;
						} catch (Throwable f) {
						}
					}
					
					if (!rebindWorked) {
						if (Logger.isEnabled())
							Logger.log(new LogEvent(LOGID,"PRUDPPacketReceiver: bind failed with " + Debug.getNestedExceptionMessage(e)));

						// one off attempt to recover by selecting an explicit one.
						// on  Vista (at least) we sometimes fail with wildcard but succeeed
						// with explicit (see http://forum.vuze.com/thread.jspa?threadID=77574&tstart=0)
						if (targetBindIp.isAnyLocalAddress()) {
							InetAddress guess = NetworkAdmin.getSingleton().guessRoutableBindAddress();
							if (guess != null) {
								if (Logger.isEnabled())
									Logger.log(new LogEvent(LOGID,"PRUDPPacketReceiver: retrying with bind IP guess of " + guess ));
								try {
									InetSocketAddress guess_address = new InetSocketAddress(guess, port);
									newSocket = new DatagramSocket(guess_address);
									targetBindIp 	= guess;
									address			= guess_address;
									if (Logger.isEnabled())
										Logger.log(new LogEvent(LOGID,"PRUDPPacketReceiver: Switched to explicit bind ip " + targetBindIp + " after initial bind failure with wildcard (" + e.getMessage() + ")" ));
								} catch (Throwable f) {
									throw (e);
								}
							} else {
								throw (e);
							}
						} else {
							throw (e);
						}
					}
				}
				
				newSocket.setReuseAddress(true);
				// short timeout on receive so that we can interrupt a receive fairly quickly
				newSocket.setSoTimeout(1000);
				// only make the socket public once fully configured
				socket			= newSocket;
				currentBindIp	= targetBindIp;
				initSem.release();
				if (Logger.isEnabled())
					Logger.log(new LogEvent(LOGID,
							"PRUDPPacketReceiver: receiver established on port " + port + (currentBindIp==null?"":(", bound to " + currentBindIp ))));
				
				byte[] buffer = null;
				long successfulAccepts 	= 0;
				long failedAccepts		= 0;
				
				while (!(failed || destroyed)) {
					
					//int count = SingleCounter0.getInstance().getAndIncreaseCount();
					//Log.d(TAG, String.format("how many times is this called... #%d", count));
					
					if (currentBindIp != targetBindIp)
						break;
					
					try {
						if (buffer == null) {
							buffer = new byte[MAX_PACKET_SIZE];
						}
						
						DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address);
						receiveFromSocket(packet);
						if (packet.getLength() > MAX_PACKET_SIZE) {
							if (MAX_PACKET_SIZE < PRUDPPacket.MAX_PACKET_SIZE) {
								Debug.out("UDP Packet truncated: received length=" + packet.getLength() + ", current max=" + MAX_PACKET_SIZE);
								MAX_PACKET_SIZE = Math.min(packet.getLength() + 256, PRUDPPacket.MAX_PACKET_SIZE);
								buffer = null;
								continue;
							}
						}
						
						long receiveTime = SystemTime.getCurrentTime();
						successfulAccepts++;
						failedAccepts = 0;
						for (PRUDPPrimordialHandler h: primordialHandlers) {
							if (h.packetReceived(packet)) {
								// primordial handlers get their own buffer as we can't guarantee
								// that they don't need to hang onto the data
								buffer = null;
								stats.primordialPacketReceived(packet.getLength());
								break;
							}
						}
						
						if (buffer != null) {
							process(packet, receiveTime);
						}
						
					} catch (SocketTimeoutException e) {
						//Log.d(TAG, "ste is occured...");
					} catch (Throwable e) {
						// on vista we get periodic socket closures
						String	message = e.getMessage();
						if (	socket.isClosed() ||
								( message != null &&
									message.toLowerCase().contains("socket closed"))) {
							long	now = SystemTime.getCurrentTime();
							// can't guarantee there aren't situations where we get into a screaming
							// closed loop so guard against this somewhat
							if (now - lastSocketCloseTime < 500) {
								Thread.sleep(250);
							}
							lastSocketCloseTime = now;
							if (Logger.isEnabled())
								Logger.log(new LogEvent(LOGID,
										"PRUDPPacketReceiver: recycled UDP port " + port + " after close: ok=" + successfulAccepts ));
							break;
						}
						failedAccepts++;
						if (Logger.isEnabled())
							Logger.log(new LogEvent(LOGID,
									"PRUDPPacketReceiver: receive failed on port " + port + ": ok=" + successfulAccepts + ", fails=" + failedAccepts, e));

						if ((failedAccepts > 100 && successfulAccepts == 0 ) || failedAccepts > 1000) {
							Logger.logTextResource(new LogAlert(LogAlert.UNREPEATABLE,
									LogAlert.AT_ERROR, "Network.alert.acceptfail"), new String[] {
									"" + port, "UDP" });
							// break, sometimes get a screaming loop. e.g.
							/*
							[2:01:55]  DEBUG::Tue Dec 07 02:01:55 EST 2004
							[2:01:55]    java.net.SocketException: Socket operation on nonsocket: timeout in datagram socket peek
							[2:01:55]  	at java.net.PlainDatagramSocketImpl.peekData(Native Method)
							[2:01:55]  	at java.net.DatagramSocket.receive(Unknown Source)
							[2:01:55]  	at org.gudy.azureus2.core3.tracker.server.impl.udp.TRTrackerServerUDP.recvLoop(TRTrackerServerUDP.java:118)
							[2:01:55]  	at org.gudy.azureus2.core3.tracker.server.impl.udp.TRTrackerServerUDP$1.runSupport(TRTrackerServerUDP.java:90)
							[2:01:55]  	at org.gudy.azureus2.core3.util.AEThread.run(AEThread.java:45)
							*/
							initError	= e;
							failed	= true;
						}
					}
				}
			}
		} catch (Throwable e) {
			initError	= e;
			if (!(e instanceof BindException && Constants.isWindowsVistaOrHigher)) {
				Logger.logTextResource(new LogAlert(LogAlert.UNREPEATABLE,
						LogAlert.AT_ERROR, "Tracker.alert.listenfail"), new String[] { "UDP:"
						+ port });
			}
			Logger.log(new LogEvent(LOGID, "PRUDPPacketReceiver: "
					+ "DatagramSocket bind failed on port " + port, e));
		} finally {
			initSem.release();
			destroySemaphore.releaseForever();
			if (socket != null) {
				try {
					//Log.d(TAG, "closing socket...");
					socket.close();
				} catch (Throwable e) {
					Debug.printStackTrace(e);
				}
			}
			// make sure we destroy the delegate too if something happend
			PRUDPPacketHandlerImpl delegate = altProtocolDelegate;
			if (delegate != null) {
				delegate.destroy();
			}
			NetworkAdmin.getSingleton().removePropertyChangeListener(propListener);
		}
	}

	protected void checkTimeouts() {
		long now = SystemTime.getCurrentTime();
		List<PRUDPPacketHandlerRequestImpl> timedOut = new ArrayList<>();
		try {
			requestsMonitor.enter();
			Iterator<PRUDPPacketHandlerRequestImpl> it = requests.values().iterator();
			while (it.hasNext()) {
				PRUDPPacketHandlerRequestImpl request = (PRUDPPacketHandlerRequestImpl)it.next();
				long	sentTime = request.getSendTime();
				if (	sentTime != 0 &&
						now - sentTime >= request.getTimeout()) {
					it.remove();
					stats.requestTimedOut();
					timedOut.add(request);
				}
			}
		} finally {
			requestsMonitor.exit();
		}
		
		for (int i=0;i<timedOut.size();i++) {
			PRUDPPacketHandlerRequestImpl request = (PRUDPPacketHandlerRequestImpl)timedOut.get(i);
			if (TRACE_REQUESTS) {
				if (Logger.isEnabled())
					Logger.log(new LogEvent(LOGID, LogEvent.LT_ERROR,
							"PRUDPPacketHandler: request timeout"));
			}
			// don't change the text of this message, it's used elsewhere
			try {
				request.setException(new PRUDPPacketHandlerException("timed out"));
			} catch (Throwable e) {
				Debug.printStackTrace(e);
			}
		}
	}

	protected void process(DatagramPacket dgPacket, long receiveTime) {
		
		try {
			// HACK alert. Due to the form of the tracker UDP protocol (no common
			// header for requests and replies) we enforce a rule. All connection ids
			// must have their MSB set. As requests always start with the action, which
			// always has the MSB clear, we can use this to differentiate.
			byte[]	packetData	= dgPacket.getData();
			int		packetLen	= dgPacket.getLength();
			// System.out.println("received:" + packet_len);
			PRUDPPacket packet;
			boolean	requestPacket;
			stats.packetReceived(packetLen);
			InetSocketAddress originator = (InetSocketAddress)dgPacket.getSocketAddress();
			
			if (LOG_SEND_RECV) Log.d(TAG, String.format("recv[%s] = %s", originator, Util.toHexString(dgPacket.getData())));
			//if (DBG) Log.d(TAG, "originator = " + originator);
			
			if ((packetData[0]&0x80) == 0) {
				requestPacket = false;
				packet = PRUDPPacketReply.deserialiseReply(
					this, // PRUDPPacketHadnler 
					originator, // InetSocketAddress
					new DataInputStream(new ByteArrayInputStream(packetData, 0, packetLen)) // DataInputStream
				);
			} else {
				requestPacket = true;
				PRUDPPacketRequest request = PRUDPPacketRequest.deserialiseRequest(
						this,
						new DataInputStream(new ByteArrayInputStream(packetData, 0, packetLen)));
				request.setReceiveTime(receiveTime);
				packet = request;
			}
			packet.setSerialisedSize(packetLen);
			packet.setAddress(originator);
			if (requestPacket) {
				totalRequestsReceived++;
				// System.out.println("Incoming from " + dg_packet.getAddress());
				if (TRACE_REQUESTS) {
					Logger.log(new LogEvent(LOGID,
							"PRUDPPacketHandler: request packet received: "
									+ packet.getString()));
				}
				
				if (receiveDelay > 0) {
					// we take the processing offline so that these incoming requests don't
					// interfere with replies to outgoing requests
					try {
						recvQueueMonitor.enter();
						if (recvQueueDataSize > MAX_RECV_QUEUE_DATA_SIZE) {
							long	now = SystemTime.getCurrentTime();
							if (now - lastErrorReport > 30000) {
								lastErrorReport	= now;
								Debug.out("Receive queue size limit exceeded (" +
											MAX_RECV_QUEUE_DATA_SIZE + "), dropping request packet [" +
											totalRequestsReceived + "/" + totalRequestsProcessed + ":" + totalReplies + "]");
							}
						} else if (receiveDelay * recvQueue.size() > queuedRequestTimeout) {
							
							// by the time this request gets processed it'll have timed out
							// in the caller anyway, so discard it
							long now = SystemTime.getCurrentTime();
							if (now - lastErrorReport > 30000) {
								lastErrorReport	= now;
								Debug.out("Receive queue entry limit exceeded (" +
											recvQueue.size() + "), dropping request packet [" +
											totalRequestsReceived + "/" + totalRequestsProcessed + ":" + totalReplies + "]");
							}
						} else {
							recvQueue.add(new Object[]{ packet, new Integer(dgPacket.getLength()) });
							recvQueueDataSize	+= dgPacket.getLength();
							recvQueueSemaphore.release();
							if (recvThread == null) {
								recvThread =
									new AEThread("PRUDPPacketHandler:receiver") {
										public void runSupport() {
											while (true) {
												try {
													recvQueueSemaphore.reserve();
													Object[]	data;
													try {
														recvQueueMonitor.enter();
														data = (Object[])recvQueue.remove(0);
														totalRequestsProcessed++;
														recvQueueDataSize -= ((Integer)data[1]).intValue();
														requestReceiveAverage.addValue(1);
													} finally {
														recvQueueMonitor.exit();
													}
													
													PRUDPPacketRequest	p = (PRUDPPacketRequest)data[0];
													PRUDPRequestHandler	handler = requestHandler;
													if (handler != null) {
														handler.process(p);
														if (receiveDelay > 0) {
															int 	maxReqPerSec = 1000/receiveDelay;
															long	requestPerSec = requestReceiveAverage.getAverage();
															//System.out.println(requestPerSec + "/" + maxReqPerSec + " - " + recvQueueDataSize);
															if (requestPerSec > maxReqPerSec) {
																Thread.sleep(receiveDelay);
															} else {
																long delay = (receiveDelay * requestPerSec) / maxReqPerSec;
																if (delay >= 5) {
																	Thread.sleep(delay);
																}
															}
														}
													}
												} catch (Throwable e) {
													Debug.printStackTrace(e);
												}
											}
										}
									};
								recvThread.setDaemon(true);
								recvThread.start();
							}
						}
					} finally {
						recvQueueMonitor.exit();
					}
				} else {
					PRUDPRequestHandler	handler = requestHandler;
					if (handler != null) {
						handler.process((PRUDPPacketRequest)packet);
					}
				}
			} else {
				totalReplies++;
				if (TRACE_REQUESTS) {
					Logger.log(new LogEvent(LOGID,
							"PRUDPPacketHandler: reply packet received: "
									+ packet.getString()));
				}
				PRUDPPacketHandlerRequestImpl request;
				try {
					requestsMonitor.enter();
					if (packet.hasContinuation()) {
						// don't remove the request if there are more replies to come
						request = (PRUDPPacketHandlerRequestImpl)requests.get(new Integer(packet.getTransactionId()));
					} else {
						request = (PRUDPPacketHandlerRequestImpl)requests.remove(new Integer(packet.getTransactionId()));
					}
				} finally {
					requestsMonitor.exit();
				}
				
				if (request == null) {
					if (TRACE_REQUESTS) {
						Logger.log(new LogEvent(LOGID, LogEvent.LT_ERROR,
								"PRUDPPacketReceiver: unmatched reply received, discarding:"
										+ packet.getString()));
					}
				} else {
					request.setReply(packet, (InetSocketAddress)dgPacket.getSocketAddress(), receiveTime);
				}
			}
		} catch (Throwable e) {
			// if someone's sending us junk we just log and continue
			if (e instanceof IOException) {
				// generally uninteresting
				//e.printStackTrace();
			} else {
				Logger.log(new LogEvent(LOGID, "", e));
			}
		}
	}

	public PRUDPPacket sendAndReceive(
		PRUDPPacket				requestPacket,
		InetSocketAddress		destinationAddress)
		throws PRUDPPacketHandlerException {
		return (sendAndReceive(null,requestPacket, destinationAddress));
	}

	public PRUDPPacket sendAndReceive(
		PasswordAuthentication	auth,
		PRUDPPacket				requestPacket,
		InetSocketAddress		destinationAddress)
		throws PRUDPPacketHandlerException {
		return (sendAndReceive(auth, requestPacket, destinationAddress, PRUDPPacket.DEFAULT_UDP_TIMEOUT));
	}

	public PRUDPPacket sendAndReceive(
		PasswordAuthentication	auth,
		PRUDPPacket				requestPacket,
		InetSocketAddress		destinationAddress,
		long					timeout)
		throws PRUDPPacketHandlerException {
		PRUDPPacketHandlerRequestImpl request =
			sendAndReceive(auth, requestPacket, destinationAddress, null, timeout, PRUDPPacketHandler.PRIORITY_MEDIUM);
		return (request.getReply());
	}

	public PRUDPPacket sendAndReceive(
		PasswordAuthentication	auth,
		PRUDPPacket				requestPacket,
		InetSocketAddress		destinationAddress,
		long					timeout,
		int						priority)
		throws PRUDPPacketHandlerException {
		PRUDPPacketHandlerRequestImpl request =
			sendAndReceive(auth, requestPacket, destinationAddress, null, timeout, priority);
		return (request.getReply());
	}

	public void sendAndReceive(
		PRUDPPacket					requestPacket,
		InetSocketAddress			destinationAddress,
		PRUDPPacketReceiver			receiver,
		long						timeout,
		int							priority)
		throws PRUDPPacketHandlerException {
		sendAndReceive(null, requestPacket, destinationAddress, receiver, timeout, priority);
	}

	public PRUDPPacketHandlerRequestImpl sendAndReceive(
		PasswordAuthentication		auth,
		PRUDPPacket					requestPacket,
		InetSocketAddress			destinationAddress,
		PRUDPPacketReceiver			receiver,
		long						timeout,
		int							priority)
		throws PRUDPPacketHandlerException {
		
		/*if (SingleCounter0.getInstance().getAndIncreaseCount() <= 5)
			new Throwable().printStackTrace();*/
		
		/*
		if (socket == null || socket.isClosed()) {
			//Log.d(TAG, "sendAndReceive() is called...");
			Log.d(TAG, "[sendAndReceive()] socket == null || socket.isClosed()");
			//new Throwable().printStackTrace();
		}
		//*/
		
		if (BLOCK_SEND) return null;
		
		if (socket == null) {
			if (initError != null) {
				throw (new PRUDPPacketHandlerException("Transport unavailable", initError));
			}
			throw (new PRUDPPacketHandlerException("Transport unavailable"));
		}
		
		//Log.d(TAG, "destinationAddress = " + destinationAddress);
		final InetSocketAddress f_destinationAddress = destinationAddress;
		
		checkTargetAddress(destinationAddress);
		PRUDPPacketHandlerImpl delegate = altProtocolDelegate;
		if (delegate != null && destinationAddress.getAddress().getClass().isInstance(delegate.explicitBindIp)) {
			return delegate.sendAndReceive(auth, requestPacket, destinationAddress, receiver, timeout, priority);
		}
		
		try {
			MyByteArrayOutputStream	baos = new MyByteArrayOutputStream(MAX_PACKET_SIZE);
			DataOutputStream os = new DataOutputStream(baos);
			requestPacket.serialise(os);
			//Log.d(TAG, "requestPacket.getString() = " + requestPacket.getString());
			
			byte[]	_buffer = baos.getBuffer();
			int		_length	= baos.size();
			requestPacket.setSerialisedSize(_length);
			if (auth != null) {
				//<parg_home> so <new_packet> = <old_packet> + <user_padded_to_8_bytes> + <hash>
				//<parg_home> where <hash> = first 8 bytes of sha1(<old_packet> + <user_padded_to_8> + sha1(pass))
				//<XTF> Yes
				SHA1Hasher hasher = new SHA1Hasher();
				String	userName 	= auth.getUserName();
				String	password	= new String(auth.getPassword());
				byte[]	sha1Password;
				if (userName.equals("<internal>")) {
					sha1Password = Base64.decode(password);
				} else {
					sha1Password = hasher.calculateHash(password.getBytes());
				}
				byte[]	userBytes = new byte[8];
				Arrays.fill( userBytes, (byte)0);
				for (int i=0;i<userBytes.length&&i<userName.length();i++) {
					userBytes[i] = (byte)userName.charAt(i);
				}
				hasher = new SHA1Hasher();
				hasher.update(_buffer, 0, _length);
				hasher.update(userBytes);
				hasher.update(sha1Password);
				byte[]	overallHash = hasher.getDigest();
				//System.out.println("PRUDPHandler - auth = " + auth.getUserName() + "/" + new String(auth.getPassword()));
				baos.write(userBytes);
				baos.write(overallHash, 0, 8);
				_buffer = baos.getBuffer();
				_length	= baos.size();
			}
			
			DatagramPacket dgPacket = new DatagramPacket(_buffer, _length, destinationAddress);
			PRUDPPacketHandlerRequestImpl request = new PRUDPPacketHandlerRequestImpl(receiver, timeout);
			try {
				requestsMonitor.enter();
				requests.put(new Integer(requestPacket.getTransactionId()), request);
			} finally {
				requestsMonitor.exit();
			}
			
			try {
				//System.out.println("Outgoing to " + dgPacket.getAddress());
				//Log.d(TAG, "sendDelay = " + sendDelay);
				if (sendDelay > 0 && priority != PRUDPPacketHandler.PRIORITY_IMMEDIATE) {
					try {
						sendQueueMonitor.enter();
						if (sendQueueDataSize > MAX_SEND_QUEUE_DATA_SIZE) {
							request.sent();
							// synchronous write holding lock to block senders
							sendToSocket(dgPacket);
							stats.packetSent(_length);
							if (TRACE_REQUESTS) {
								Logger.log(new LogEvent(LOGID,
										"PRUDPPacketHandler: request packet sent to "
												+ destinationAddress + ": "
												+ requestPacket.getString()));
							}
							Thread.sleep(sendDelay * 4);
						} else {
							sendQueueDataSize	+= dgPacket.getLength();
							sendQueues[priority].add(new Object[]{ dgPacket, request });
							if (TRACE_REQUESTS) {
								String	str = "";
								for (int i=0;i<sendQueues.length;i++) {
									str += (i==0?"":",") + sendQueues[i].size();
								}
								System.out.println("send queue sizes: " + str);
							}
							sendQueueSemaphore.release();
							if (sendThread == null) {
								sendThread =
									new AEThread("PRUDPPacketHandler:sender") {
									
										public void runSupport() {
											
											int[] consecutiveSends = new int[sendQueues.length];
											while (true) {
												try {
													sendQueueSemaphore.reserve();
													Object[]	data;
													int			selectedPriority	= 0;
													try {
														sendQueueMonitor.enter();
														
														// invariant: at least one queue must have an entry
														for (int i=0;i<sendQueues.length;i++) {
															List	queue = sendQueues[i];
															int	queueSize = queue.size();
															if (queueSize > 0) {
																selectedPriority	= i;
																if (consecutiveSends[i] >= 4 ||
																		(i < sendQueues.length - 1 &&
																			sendQueues[i+1].size() - queueSize > 500)
																) {
																	// too many consecutive or too imbalanced, see if there are
																	// lower priority queues with entries
																	consecutiveSends[i]	= 0;
																} else {
																	consecutiveSends[i]++;
																	break;
																}
															} else {
																consecutiveSends[i]	= 0;
															}
														}
														data = (Object[])sendQueues[selectedPriority].remove(0);
														DatagramPacket p = (DatagramPacket)data[0];
														// mark as sent before sending in case send fails
														// and we then rely on timeout to pick this up
														sendQueueDataSize -= p.getLength();
													} finally {
														sendQueueMonitor.exit();
													}
													
													DatagramPacket					p	= (DatagramPacket)data[0];
													PRUDPPacketHandlerRequestImpl	r	= (PRUDPPacketHandlerRequestImpl)data[1];
													r.sent();
													sendToSocket(p);
													
													if (LOG_SEND_RECV) Log.d(TAG, String.format("send[%s] = %s", f_destinationAddress, Util.toHexString(p.getData())));
													
													stats.packetSent(p.getLength());
													if (TRACE_REQUESTS) {
														Logger.log(new LogEvent(LOGID,
															"PRUDPPacketHandler: request packet sent to "
																	+ p.getAddress()));
													}
													long delay = sendDelay;
													if (selectedPriority == PRIORITY_HIGH) {
														delay	= delay/2;
													}
													Thread.sleep(delay);
												} catch (Throwable e) {
													// get occasional send fails, not very interesting
													Logger.log(
														new LogEvent(
															LOGID,
															LogEvent.LT_WARNING,
															"PRUDPPacketHandler: send failed: " + Debug.getNestedExceptionMessage(e)));
												}
											}
										}
									};
									sendThread.setDaemon(true);
									sendThread.start();
							}
						}
					} finally {
						sendQueueMonitor.exit();
					}
				} else {
					request.sent();
					if (dgPacket == null) {
						throw new NullPointerException("dg_packet is null");
					}
					sendToSocket(dgPacket);
					// System.out.println("sent:" + buffer.length);
					stats.packetSent(_length);
					if (TRACE_REQUESTS) {
						Logger.log(new LogEvent(LOGID, "PRUDPPacketHandler: "
								+ "request packet sent to " + destinationAddress + ": "
								+ requestPacket.getString()));
					}
				}
				// if the send is ok then the request will be removed from the queue
				// either when a reply comes back or when it gets timed-out
				return (request);
			} catch (Throwable e) {
				// never got sent, remove it immediately
				try {
					requestsMonitor.enter();
					requests.remove(new Integer(requestPacket.getTransactionId()));
				} finally {
					requestsMonitor.exit();
				}
				throw (e);
			}
		} catch (Throwable e) {
			// AMC: I've seen this in debug logs - just wonder where it's
			// coming from.
			if (e instanceof NullPointerException) {
				Debug.out(e);
			}
			String msg = Debug.getNestedExceptionMessage(e);
			Logger.log(new LogEvent(LOGID,LogEvent.LT_ERROR,
					"PRUDPPacketHandler: sendAndReceive to " + destinationAddress + " failed: " + msg ));
			if (msg.contains("Invalid data length")) {
				Debug.out("packet=" + requestPacket.getString() + ",auth=" + auth);
				Debug.out(e);
			}
			throw (new PRUDPPacketHandlerException("PRUDPPacketHandler:sendAndReceive failed", e));
		}
	}

	public void send(
		PRUDPPacket				requestPacket,
		InetSocketAddress		destinationAddress)
		throws PRUDPPacketHandlerException {
		
		//Log.d(TAG, "send() is called...");
		if (BLOCK_SEND) return;
		
		if (socket == null || socket.isClosed()) {
			//Log.d(TAG, "[send()] socket == null || socket.isClosed()");
			if (initError != null) {
				throw (new PRUDPPacketHandlerException("Transport unavailable", initError));
			}
			throw (new PRUDPPacketHandlerException("Transport unavailable"));
		}
		
		checkTargetAddress(destinationAddress);
		PRUDPPacketHandlerImpl delegate = altProtocolDelegate;
		if (delegate != null && destinationAddress.getAddress().getClass().isInstance(delegate.explicitBindIp)) {
			delegate.send(requestPacket, destinationAddress);
			return;
		}
		
		try {
			MyByteArrayOutputStream	baos = new MyByteArrayOutputStream(MAX_PACKET_SIZE);
			DataOutputStream os = new DataOutputStream(baos);
			requestPacket.serialise(os);
			byte[]	_buffer = baos.getBuffer();
			int		_length	= baos.size();
			requestPacket.setSerialisedSize(_length);
			DatagramPacket dgPacket = new DatagramPacket(_buffer, _length, destinationAddress);
			// System.out.println("Outgoing to " + dg_packet.getAddress());
			if (TRACE_REQUESTS) {
				Logger.log(new LogEvent(LOGID,
						"PRUDPPacketHandler: reply packet sent: "
								+ requestPacket.getString()));
			}
			sendToSocket(dgPacket);
			stats.packetSent(_length);
			// this is a reply to a request, no time delays considered here
		} catch (Throwable e) {
			if (e instanceof NoRouteToHostException) {
			} else {
				e.printStackTrace();
			}
			Logger.log(new LogEvent(LOGID, LogEvent.LT_ERROR, "PRUDPPacketHandler: send to " + destinationAddress + " failed: " + Debug.getNestedExceptionMessage(e)));
			throw (new PRUDPPacketHandlerException("PRUDPPacketHandler:send failed", e));
		}
	}

	protected void checkTargetAddress(InetSocketAddress	address)
		throws PRUDPPacketHandlerException {
		if (address.getPort() == 0) {
			throw (new PRUDPPacketHandlerException("Invalid port - 0"));
		}
		if (address.getAddress() == null) {
			throw (new PRUDPPacketHandlerException("Unresolved host '" + address.getHostName() + "'"));
		}
	}

	public void	setDelays(
		int		_send_delay,
		int		_receive_delay,
		int		_queued_request_timeout) {
		sendDelay				= _send_delay;
		receiveDelay			= _receive_delay;
			// trim a bit off this limit to include processing time
		queuedRequestTimeout	= _queued_request_timeout-5000;
		if (queuedRequestTimeout < 5000) {
			queuedRequestTimeout = 5000;
		}
		PRUDPPacketHandlerImpl delegate = altProtocolDelegate;
		if (delegate != null) {
			delegate.setDelays(_send_delay, _receive_delay, _queued_request_timeout);
		}
	}

	public long getSendQueueLength() {
		int	res = 0;
		for (int i=0;i<sendQueues.length;i++) {
			res += sendQueues[i].size();
		}
		PRUDPPacketHandlerImpl delegate = altProtocolDelegate;
		if (delegate != null) {
			res += delegate.getSendQueueLength();
		}
		return (res);
	}

	public long getReceiveQueueLength() {
		long size = recvQueue.size();
		PRUDPPacketHandlerImpl delegate = altProtocolDelegate;
		if (delegate != null) {
			size += delegate.getReceiveQueueLength();
		}
		return size;
	}

	public void primordialSend(
		byte[]				buffer,
		InetSocketAddress	target)
		throws PRUDPPacketHandlerException
	{
		
		//Log.d(TAG, "primordialSend() is called...");
		
		if (socket == null || socket.isClosed()) {
			if (initError != null) {
				throw (new PRUDPPacketHandlerException("Transport unavailable", initError));
			}
			throw (new PRUDPPacketHandlerException("Transport unavailable"));
		}
		checkTargetAddress(target);
		PRUDPPacketHandlerImpl delegate = altProtocolDelegate;
		if (	delegate != null &&
				target.getAddress().getClass().isInstance(delegate.explicitBindIp)) {
			delegate.primordialSend(buffer, target);
			return;
		}
		
		try {
			DatagramPacket dg_packet = new DatagramPacket(buffer, buffer.length, target);
			// System.out.println("Outgoing to " + dg_packet.getAddress());
			if (TRACE_REQUESTS) {
				Logger.log(new LogEvent(LOGID,
						"PRUDPPacketHandler: reply packet sent: " + buffer.length + " to " + target ));
			}
			sendToSocket(dg_packet);
			stats.primordialPacketSent(buffer.length);
		} catch (Throwable e) {
			throw (new PRUDPPacketHandlerException(e.getMessage()));
		}
	}

	private void sendToSocket(DatagramPacket p) throws IOException {
		if (packetTransformer != null) {
			packetTransformer.transformSend(p);
		}
		socket.send(p);
	}

	private void receiveFromSocket(DatagramPacket p) 
		throws IOException  {
		socket.receive(p);
		if (packetTransformer != null) {
			//Log.d(TAG, "packetTransformer != null");
			packetTransformer.transformReceive(p);
		}
	}

	public PRUDPPacketHandlerStats getStats() {
		return (stats);
	}

	public void destroy() {
		destroyed	= true;
		PRUDPPacketHandlerImpl delegate = altProtocolDelegate;
		if (delegate != null) {
			delegate.destroy();
		}
		destroySemaphore.reserve();
	}

	public PRUDPPacketHandler openSession(InetSocketAddress target)
			throws PRUDPPacketHandlerException  {
		if (useSocks) {
			return (new PRUDPPacketHandlerSocks( target));
		} else {
			return (this);
		}
	}

	public void closeSession() throws PRUDPPacketHandlerException {
	}

	private static class MyByteArrayOutputStream extends ByteArrayOutputStream {
		
		private MyByteArrayOutputStream(int	size) {
			super(size);
		}

		private byte[] getBuffer() {
			return (buf);
		}
	}

	protected interface	PacketTransformer {
		public void transformSend(DatagramPacket packet);
		public void transformReceive(DatagramPacket packet);
	}
}
