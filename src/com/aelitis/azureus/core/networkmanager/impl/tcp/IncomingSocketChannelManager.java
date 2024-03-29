/*
 * Created on Jan 18, 2005
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

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.config.ParameterListener;
import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.logging.LogAlert;
import org.gudy.azureus2.core3.logging.LogEvent;
import org.gudy.azureus2.core3.logging.LogIDs;
import org.gudy.azureus2.core3.logging.Logger;
import org.gudy.azureus2.core3.util.*;

import com.aelitis.azureus.core.networkmanager.ConnectionEndpoint;
import com.aelitis.azureus.core.networkmanager.ProtocolEndpoint;
import com.aelitis.azureus.core.networkmanager.ProtocolEndpointFactory;
import com.aelitis.azureus.core.networkmanager.Transport;
import com.aelitis.azureus.core.networkmanager.VirtualServerChannelSelector;
import com.aelitis.azureus.core.networkmanager.VirtualServerChannelSelectorFactory;
import com.aelitis.azureus.core.networkmanager.admin.NetworkAdmin;
import com.aelitis.azureus.core.networkmanager.admin.NetworkAdminPropertyChangeListener;
import com.aelitis.azureus.core.networkmanager.impl.IncomingConnectionManager;
import com.aelitis.azureus.core.networkmanager.impl.ProtocolDecoder;
import com.aelitis.azureus.core.networkmanager.impl.TransportCryptoManager;
import com.aelitis.azureus.core.networkmanager.impl.TransportHelperFilter;
import com.aelitis.azureus.core.proxy.AEProxyAddressMapper;
import com.aelitis.azureus.core.proxy.AEProxyFactory;


/**
 * Accepts new incoming socket connections and manages routing of them
 * to registered handlers.
 */
public class IncomingSocketChannelManager {
	
	private static final LogIDs LOGID = LogIDs.NWMAN;

	private final String	portConfigKey;
	private final String	portEnableConfigKey;

	private int tcpListenPort;

	private int soRcvbufSize = COConfigurationManager.getIntParameter("network.tcp.socket.SO_RCVBUF");

	private InetAddress[] 	defaultBindAddresses = NetworkAdmin.getSingleton().getMultiHomedServiceBindAddresses(true);
	private InetAddress 	explicitBindAddress;
	private boolean		explicitBindAddressSet;

	private VirtualServerChannelSelector[] serverSelectors = new VirtualServerChannelSelector[0];
	private int listenFailCounts[] = new int[0];

	final IncomingConnectionManager	incomingManager = IncomingConnectionManager.getSingleton();

	protected final AEMonitor	thisMon	= new AEMonitor("IncomingSocketChannelManager");

	private long	lastNonLocalConnectionTime;

	private final AEProxyAddressMapper proxyAddressMapper = AEProxyFactory.getAddressMapper();

	/**
	 * Create manager and begin accepting and routing new connections.
	 */
	public IncomingSocketChannelManager(String _port_config_key, String _port_enable_config_key) {

		portConfigKey 		= _port_config_key;
		portEnableConfigKey	= _port_enable_config_key;
	
		tcpListenPort = COConfigurationManager.getIntParameter(portConfigKey);

		//allow dynamic port number changes
		COConfigurationManager.addParameterListener( portConfigKey, new ParameterListener() {
			public void parameterChanged(String parameterName) {
				int port = COConfigurationManager.getIntParameter(portConfigKey);
				if (port != tcpListenPort) {
					tcpListenPort = port;
					restart();
				}
			}
		});

		COConfigurationManager.addParameterListener( portEnableConfigKey, new ParameterListener() {
				public void parameterChanged(String parameterName) {
					restart();
				}
			});

		//allow dynamic receive buffer size changes
		COConfigurationManager.addParameterListener("network.tcp.socket.SO_RCVBUF", new ParameterListener() {
			public void parameterChanged(String parameterName) {
				int size = COConfigurationManager.getIntParameter("network.tcp.socket.SO_RCVBUF");
				if (size != soRcvbufSize) {
					soRcvbufSize = size;
					restart();
				}
			}
		});

		//allow dynamic bind address changes
		NetworkAdmin.getSingleton().addPropertyChangeListener(
			new NetworkAdminPropertyChangeListener() {
				public void propertyChanged(String		property ) {
					if (property == NetworkAdmin.PR_DEFAULT_BIND_ADDRESS) {

							InetAddress[] addresses = NetworkAdmin.getSingleton().getMultiHomedServiceBindAddresses(true);

							if (!Arrays.equals(addresses, defaultBindAddresses)) {

								defaultBindAddresses = addresses;

								restart();
							}
					}
				}
			});


		//start processing
		start();


		//run a daemon thread to poll listen port for connectivity
		//it seems that sometimes under OSX that listen server sockets sometimes stop accepting incoming connections for some unknown reason
		//this checker tests to make sure the listen socket is still accepting connections, and if not, recreates the socket

		SimpleTimer.addPeriodicEvent("IncomingSocketChannelManager:concheck", 60 * 1000, new TimerEventPerformer() {
			public void perform(
				TimerEvent ev ) {
				COConfigurationManager.setParameter("network.tcp.port." + tcpListenPort + ".last.nonlocal.incoming", lastNonLocalConnectionTime);

				for (int i = 0; i < serverSelectors.length; i++) {
					VirtualServerChannelSelector server_selector = serverSelectors[i];

					if (server_selector != null && server_selector.isRunning()) { //ensure it's actually running
						long accept_idle = SystemTime.getCurrentTime() - server_selector.getTimeOfLastAccept();
						if (accept_idle > 10 * 60 * 1000) { //the socket server hasn't accepted any new connections in the last 10min
							//so manually test the listen port for connectivity
							InetAddress inet_address = server_selector.getBoundToAddress();
							try {
								if (inet_address == null)
									inet_address = InetAddress.getByName("127.0.0.1"); //failback
								Socket sock = new Socket(inet_address, tcpListenPort, inet_address, 0);
								sock.close();
								listenFailCounts[i] = 0;
							} catch (Throwable t) {
								//ok, let's try again without the explicit local bind
								try {
									Socket sock = new Socket(InetAddress.getByName("127.0.0.1"), tcpListenPort);
									sock.close();
									listenFailCounts[i] = 0;
								} catch (Throwable x) {
									listenFailCounts[i]++;
									Debug.out(new Date() + ": listen port on [" + inet_address + ": " + tcpListenPort + "] seems CLOSED [" + listenFailCounts[i] + "x]");
									if (listenFailCounts[i] > 4) {
										String error = t.getMessage() == null ? "<null>" : t.getMessage();
										String msg = "Listen server socket on [" + inet_address + ": " + tcpListenPort + "] does not appear to be accepting inbound connections.\n[" + error + "]\nAuto-repairing listen service....\n";
										Logger.log(new LogAlert(LogAlert.UNREPEATABLE, LogAlert.AT_WARNING, msg));
										restart();
										listenFailCounts[i] = 0;
									}
								}
							}
						} else
						{ //it's recently accepted an inbound connection
							listenFailCounts[i] = 0;
						}
					}
				}
			}
		});
	}

	public boolean isEnabled() {
		return (COConfigurationManager.getBooleanParameter(portEnableConfigKey));
	}

	/**
	 * Get port that the TCP server socket is listening for incoming connections
	 * on.
	 *
	 * @return port number
	 */
	public int getTCPListeningPortNumber() {	return tcpListenPort;	}

	public void setExplicitBindAddress(InetAddress	address) {
		explicitBindAddress 	= address;
		explicitBindAddressSet	= true;

		restart();
	}

	public void clearExplicitBindAddress() {
		explicitBindAddress		= null;
		explicitBindAddressSet	= false;

		restart();
	}

	protected InetAddress[] getEffectiveBindAddresses() {
		if (explicitBindAddressSet) {
			return (new InetAddress[] {explicitBindAddress});
		} else {
			return (defaultBindAddresses);
		}
	}

	public boolean isEffectiveBindAddress(InetAddress	address ) {
		InetAddress[]	effective = getEffectiveBindAddresses();
		return Arrays.asList(effective).contains(address);
	}


	private final class	TcpSelectListener implements VirtualServerChannelSelector.SelectListener {
		
		public void newConnectionAccepted(final ServerSocketChannel server, final SocketChannel channel) {

			InetAddress remote_ia = channel.socket().getInetAddress();
			if (!( remote_ia.isLoopbackAddress() || remote_ia.isLinkLocalAddress() || remote_ia.isSiteLocalAddress())) {
				lastNonLocalConnectionTime = SystemTime.getCurrentTime();
			}

			//check for encrypted transport
			final TCPTransportHelper	helper = new TCPTransportHelper(channel);

			TransportCryptoManager.getSingleton().manageCrypto(
					helper, 
					null, 
					true, 
					null, 
					
					new TransportCryptoManager.HandshakeListener() {
				
						public void handshakeSuccess(ProtocolDecoder decoder, ByteBuffer remaining_initial_data) {
							process(server.socket().getLocalPort(), decoder.getFilter());
						}
						
						public void handshakeFailure(Throwable failure_msg ) {
							
							if (Logger.isEnabled()) 	
								Logger.log(new LogEvent(LOGID, "incoming crypto handshake failure: " + Debug.getNestedExceptionMessage(failure_msg)));
							/*
							// we can have problems with sockets stuck in a TIME_WAIT state if we just
							// close an incoming channel - to clear things down properly the client needs
							// to initiate the close. So what we do is send some random bytes to the client
							// under the assumption this will cause them to close, and we delay our socket close
							// for 10 seconds to give them a chance to do so.
							try {
								Random	random = new Random();
								byte[]	random_bytes = new byte[68+random.nextInt(128-68)];
								random.nextBytes(random_bytes);
								channel.write(ByteBuffer.wrap( random_bytes));
							} catch (Throwable e) {
								// ignore anything here
							}
							NetworkManager.getSingleton().closeSocketChannel(channel, 10*1000);
							*/
							helper.close("Handshake failure: " + Debug.getNestedExceptionMessage( failure_msg));
						}
						
						public void gotSecret(byte[] sessionSecret) {
						}
						
						public int getMaximumPlainHeaderLength() {
							return (incomingManager.getMaxMinMatchBufferSize());
						}
						
						public int matchPlainHeader(ByteBuffer buffer) {
							Object[]	match_data = incomingManager.checkForMatch(helper, server.socket().getLocalPort(), buffer, true);
							if (match_data == null) {
								return (TransportCryptoManager.HandshakeListener.MATCH_NONE);
							} else {
								IncomingConnectionManager.MatchListener match = (IncomingConnectionManager.MatchListener)match_data[0];
								if (match.autoCryptoFallback()) {
									return (TransportCryptoManager.HandshakeListener.MATCH_CRYPTO_AUTO_FALLBACK);
								} else {
									return (TransportCryptoManager.HandshakeListener.MATCH_CRYPTO_NO_AUTO_FALLBACK);
								}
							}
						}
					}
			);
		}
	}


	private final VirtualServerChannelSelector.SelectListener selectListener = new TcpSelectListener();


	private void start() {
		try {
			thisMon.enter();

			if (tcpListenPort < 0 || tcpListenPort > 65535 || tcpListenPort == Constants.INSTANCE_PORT) {
				String msg = "Invalid incoming TCP listen port configured, " + tcpListenPort + ". Port reset to default. Please check your config!";
				Debug.out(msg);
				Logger.log(new LogAlert(LogAlert.UNREPEATABLE, LogAlert.AT_ERROR, msg));
				tcpListenPort = RandomUtils.generateRandomNetworkListenPort();
				COConfigurationManager.setParameter(portConfigKey, tcpListenPort);
			}

			if (COConfigurationManager.getBooleanParameter(portEnableConfigKey)) {
				lastNonLocalConnectionTime = COConfigurationManager.getLongParameter("network.tcp.port." + tcpListenPort + ".last.nonlocal.incoming", 0);

				if (lastNonLocalConnectionTime > SystemTime.getCurrentTime()) {
					lastNonLocalConnectionTime = SystemTime.getCurrentTime();
				}

				if (serverSelectors.length == 0) {
					InetSocketAddress address;
					InetAddress[] bindAddresses = getEffectiveBindAddresses();

					List tempSelectors = new ArrayList(bindAddresses.length);

					listenFailCounts = new int[bindAddresses.length];
					for (int i = 0; i < bindAddresses.length; i++) {
						InetAddress bindAddress = bindAddresses[i];
						if (!NetworkAdmin.getSingleton().hasIPV6Potential(true) && bindAddress instanceof Inet6Address)
							continue;
						if (bindAddress != null)
							address = new InetSocketAddress(bindAddress, tcpListenPort);
						else
							address = new InetSocketAddress(tcpListenPort);
						VirtualServerChannelSelector serverSelector;
						if (bindAddresses.length == 1)
							serverSelector = VirtualServerChannelSelectorFactory.createBlocking(address, soRcvbufSize, selectListener);
						else
							serverSelector = VirtualServerChannelSelectorFactory.createNonBlocking(address, soRcvbufSize, selectListener);
						serverSelector.start();
						tempSelectors.add(serverSelector);
					}

					if (tempSelectors.size() == 0)
						Logger.log(new LogAlert(true,LogAlert.AT_WARNING,MessageText.getString("network.bindError")));

					serverSelectors = (VirtualServerChannelSelector[])tempSelectors.toArray(new VirtualServerChannelSelector[tempSelectors.size()]);
				}
			} else {
				Logger.log(new LogEvent(LOGID, "Not starting TCP listener on port " + tcpListenPort + " as protocol disabled"));
			}
		} finally {
			thisMon.exit();
		}
	}


	protected void process(int local_port, TransportHelperFilter filter) {
		SocketChannel	channel = ((TCPTransportHelper)filter.getHelper()).getSocketChannel();
		Socket socket = channel.socket();
		//set advanced socket options
		try {
			int so_sndbuf_size = COConfigurationManager.getIntParameter("network.tcp.socket.SO_SNDBUF");
			if (so_sndbuf_size > 0 )	socket.setSendBufferSize( so_sndbuf_size);
			String ip_tos = COConfigurationManager.getStringParameter("network.tcp.socket.IPDiffServ");
			if (ip_tos.length() > 0 )	socket.setTrafficClass( Integer.decode( ip_tos ).intValue());
		} catch (Throwable t) {
			t.printStackTrace();
		}
		AEProxyAddressMapper.AppliedPortMapping applied_mapping = proxyAddressMapper.applyPortMapping( socket.getInetAddress(), socket.getPort());
		InetSocketAddress	tcp_address = applied_mapping.getAddress();
		ConnectionEndpoint	co_ep = new ConnectionEndpoint(tcp_address);
		Map<String,Object>	properties = applied_mapping.getProperties();
		if (properties != null) {
			co_ep.addProperties(properties);
		}
		ProtocolEndpointTCP	pe_tcp = (ProtocolEndpointTCP)ProtocolEndpointFactory.createEndpoint(ProtocolEndpoint.PROTOCOL_TCP, co_ep, tcp_address);
		Transport transport = new TCPTransportImpl(pe_tcp, filter);
		incomingManager.addConnection(local_port, filter, transport);
	}


	protected long getLastNonLocalConnectionTime() {
		return (lastNonLocalConnectionTime);
	}

	private void restart() {
		try {
			thisMon.enter();
			for (int i=0;i<serverSelectors.length;i++)
				serverSelectors[i].stop();
			serverSelectors = new VirtualServerChannelSelector[0];
		} finally {
			thisMon.exit();
		}
		try { Thread.sleep(1000);	} catch ( Throwable t) { t.printStackTrace();	}
		start();
	}



}
