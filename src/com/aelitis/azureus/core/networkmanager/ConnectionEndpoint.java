/*
 * Created on 16 Jun 2006
 * Created by Paul Gardner
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
 *
 */

package com.aelitis.azureus.core.networkmanager;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.SimpleTimer;
import org.gudy.azureus2.core3.util.SystemTime;
import org.gudy.azureus2.core3.util.TimerEvent;
import org.gudy.azureus2.core3.util.TimerEventPerformer;

import com.aelitis.azureus.core.networkmanager.Transport.ConnectListener;


public class ConnectionEndpoint {
	
	private final InetSocketAddress	notionalAddress;
	private ProtocolEndpoint[]	protocols;

	private Map<String,Object>	properties;

	public ConnectionEndpoint(InetSocketAddress _notionalAddress) {
		notionalAddress	= _notionalAddress;
	}

	public void addProperties(Map<String,Object> p) {
		synchronized(this) {
			if (properties == null) {
				properties = new HashMap<String,Object>(p);
			} else {
				properties.putAll(p);
			}
		}
	}

	public Object getProperty(String name) {
		synchronized(this) {
			if (properties != null) {
				return (properties.get(name));
			}
		}
		return (null);
	}

	public InetSocketAddress getNotionalAddress() {
		return (notionalAddress);
	}

	public ProtocolEndpoint[] getProtocols() {
		if (protocols == null) {
			return (new ProtocolEndpoint[0]);
		}
		return (protocols);
	}

	public void addProtocol(ProtocolEndpoint ep) {
		if (protocols == null) {
			protocols = new ProtocolEndpoint[]{ ep };
		} else {
			for (int i=0;i<protocols.length;i++) {
				if (protocols[i] == ep) {
					return;
				}
			}
			ProtocolEndpoint[] newEp = new ProtocolEndpoint[protocols.length + 1];
			System.arraycopy(protocols, 0, newEp, 0, protocols.length);
			newEp[protocols.length] = ep;
			protocols = newEp;
		}
		ep.setConnectionEndpoint(this);
	}

	public ConnectionEndpoint getLANAdjustedEndpoint() {
		ConnectionEndpoint result = new ConnectionEndpoint(notionalAddress);
		for (int i=0;i<protocols.length;i++) {
			ProtocolEndpoint ep = protocols[i];
			InetSocketAddress address = ep.getAdjustedAddress(true);
			ProtocolEndpointFactory.createEndpoint(ep.getType(), result, address);
		}
		return (result);
	}

	public ConnectionAttempt connectOutbound(
		final boolean			connectWithCrypto,
		final boolean 			allowFallback,
		final byte[][]			sharedSecrets,
		final ByteBuffer		initialData,
		final int				priority,
		final ConnectListener 	listener) {
		
		if (protocols.length == 1) {
			ProtocolEndpoint protocol = protocols[0];
			final Transport transport = protocol.connectOutbound(connectWithCrypto, allowFallback, sharedSecrets, initialData, priority, listener);
			return (
				new ConnectionAttempt() {
					public void abandon() {
						if (transport != null) {
							transport.close("Connection attempt abandoned");
						}
					}
				});
		} else {
			final boolean[] connected = { false };
			final boolean[] abandoned = { false };
			final List<Transport> transports = new ArrayList<Transport>(protocols.length);
			final ConnectListener listenerDelegate =
				new ConnectListener() {
					//private long		start_time;
					private int			timeout = Integer.MIN_VALUE;
					private int			failCount;
					
					public int connectAttemptStarted(int defaultConnectTimeout) {
						synchronized(connected) {
							if (timeout == Integer.MIN_VALUE) {
								//start_time = SystemTime.getCurrentTime();
								timeout = listener.connectAttemptStarted(defaultConnectTimeout);
							}
							return (timeout);
						}
					}
					
					public void connectSuccess(
						Transport	transport,
						ByteBuffer 	remainingInitialData)
					{
						boolean	disconnect;
						synchronized(connected) {
							disconnect = abandoned[0];
							if (!disconnect) {
								if (!connected[0]) {
									connected[0] = true;
									//System.out.println("Connect took " + (SystemTime.getCurrentTime() - start_time) + " for " + transport.getDescription());
								} else {
									disconnect = true;
								}
							}
						}
						if (disconnect) {
							transport.close("Transparent not required");
						} else {
							listener.connectSuccess(transport, remainingInitialData);
						}
					}
					
					public void connectFailure(Throwable failureMsg) {
						boolean	inform;
						synchronized(connected) {
							failCount++;
							inform = failCount == protocols.length;
						}
						if (inform) {
							listener.connectFailure(failureMsg);
						}
					}
					
					public Object getConnectionProperty(
						String property_name) {
						return (listener.getConnectionProperty( property_name));
					}
				};
				
			boolean	ok = true;
			if (protocols.length != 2) {
				ok = false;
			} else {
				ProtocolEndpoint	p1 = protocols[0];
				ProtocolEndpoint 	p2 = protocols[1];
				if (p1.getType() == ProtocolEndpoint.PROTOCOL_TCP && p2.getType() == ProtocolEndpoint.PROTOCOL_UTP) {
				} else if (p1.getType() == ProtocolEndpoint.PROTOCOL_UTP && p2.getType() == ProtocolEndpoint.PROTOCOL_TCP) {
					ProtocolEndpoint temp = p1;
					p1 = p2;
					p2 = temp;
				} else {
					ok = false;
				}
				
				if (ok) {
					// p1 is TCP, p2 is uTP 
					final ByteBuffer initialDataCopy;
					if (initialData != null) {
						initialDataCopy = initialData.duplicate();
					} else {
						initialDataCopy = null;
					}
					Transport transport =
						p2.connectOutbound(
							connectWithCrypto,
							allowFallback,
							sharedSecrets,
							initialData,
							priority,
							new ConnectListenerEx(listenerDelegate));
					transports.add(transport);
					final ProtocolEndpoint tcp_ep = p1;
					SimpleTimer.addEvent(
						"delay:tcp:connect",
						SystemTime.getCurrentTime() + 750,
						false,
						new TimerEventPerformer() {
							public void perform(TimerEvent event) {
								synchronized(connected) {
									if (connected[0] || abandoned[0]) {
										return;
									}
								}
								Transport transport =
									tcp_ep.connectOutbound(
										connectWithCrypto,
										allowFallback,
										sharedSecrets,
										initialDataCopy,
										priority,
										new ConnectListenerEx(listenerDelegate));
								
								synchronized(connected) {
									if (abandoned[0]) {
										transport.close("Connection attempt abandoned");
									} else {
										transports.add(transport);
									}
								}							
							}
						});
				}
			}
			
			if (!ok) {
				Debug.out("No supportified!");
				listener.connectFailure(new Exception("Not Supported"));
			}
			
			return (
				new ConnectionAttempt() {
					public void abandon() {
						List<Transport> to_kill;
						synchronized(connected) {
							abandoned[0] = true;
							to_kill = new ArrayList<Transport>(transports);
						}
						for (Transport transport: to_kill) {
							transport.close("Connection attempt abandoned");
						}
					}
				}
			);
		}
	}

	public String getDescription() {
		String	str = "[";
		for (int i=0;i<protocols.length;i++) {
			str += (i==0?"":",") + protocols[i].getDescription();
		}
		return (str + "]");
	}

	private static class ConnectListenerEx
		implements ConnectListener
	{
		private final ConnectListener		listener;
		private boolean	ok;
		private boolean	failed;
		
		private ConnectListenerEx(ConnectListener _listener) {
			listener = _listener;
		}
		
		public int connectAttemptStarted(int defaultConnectTimeout) {
			return (listener.connectAttemptStarted(defaultConnectTimeout));
		}
		
		public void connectSuccess(
			Transport		transport,
			ByteBuffer 		remaining_initial_data) {
			
			synchronized(this) {
				if (ok || failed) {
					if (ok) {
						Debug.out("Double doo doo");
					}
					return;
				}
				ok = true;
			}
			listener.connectSuccess(transport, remaining_initial_data);
		}
		
		public void connectFailure(Throwable failureMsg) {
			synchronized(this) {
				if (ok || failed) {
					return;
				}
				failed = true;
			}
			listener.connectFailure(failureMsg);
		}
		
		public Object getConnectionProperty(String property_name) {
			return (listener.getConnectionProperty( property_name));
		}
	}
}
