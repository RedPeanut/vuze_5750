/*
 * Created on 21 Jun 2006
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

package com.aelitis.azureus.core.networkmanager.impl.tcp;


import java.net.InetAddress;
import java.nio.channels.CancelledKeyException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.config.ParameterListener;
import org.gudy.azureus2.core3.util.AEThread2;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.SystemTime;

import com.aelitis.azureus.core.networkmanager.VirtualChannelSelector;
import com.aelitis.azureus.core.stats.AzureusCoreStats;
import com.aelitis.azureus.core.stats.AzureusCoreStatsProvider;

public class TCPNetworkManager {
	
	private static int WRITE_SELECT_LOOP_TIME 		= 25;
	private static int WRITE_SELECT_MIN_LOOP_TIME 	= 0;
	private static int READ_SELECT_LOOP_TIME		= 25;
	private static int READ_SELECT_MIN_LOOP_TIME	= 0;

	protected static int tcpMssSize;

	private static final TCPNetworkManager instance = new TCPNetworkManager();

	public static TCPNetworkManager getSingleton() { return (instance); }

	public static boolean TCP_INCOMING_ENABLED;
	public static boolean TCP_OUTGOING_ENABLED;

	static {
		COConfigurationManager.addAndFireParameterListeners(
				new String[]{
					"TCP.Listen.Port.Enable",
					"network.tcp.connect.outbound.enable"
				},
				new ParameterListener() {
					public void parameterChanged(
						String name) {
						TCP_INCOMING_ENABLED = TCP_OUTGOING_ENABLED = COConfigurationManager.getBooleanParameter("TCP.Listen.Port.Enable");
						if (TCP_OUTGOING_ENABLED) {
							TCP_OUTGOING_ENABLED = COConfigurationManager.getBooleanParameter("network.tcp.connect.outbound.enable");
						}
					}
				});

		COConfigurationManager.addAndFireParameterListeners(
				new String[]{
					"network.tcp.read.select.time",
					"network.tcp.read.select.min.time",
					"network.tcp.write.select.time",
					"network.tcp.write.select.min.time",
					},
				new ParameterListener() {
					public void parameterChanged(String name) {
						WRITE_SELECT_LOOP_TIME 		= COConfigurationManager.getIntParameter("network.tcp.write.select.time");
						WRITE_SELECT_MIN_LOOP_TIME 	= COConfigurationManager.getIntParameter("network.tcp.write.select.min.time");

						READ_SELECT_LOOP_TIME 		= COConfigurationManager.getIntParameter("network.tcp.read.select.time");
						READ_SELECT_MIN_LOOP_TIME 	= COConfigurationManager.getIntParameter("network.tcp.read.select.min.time");
					}
				}
		);
	}

	/**
	 * Get the configured TCP MSS (Maximum Segment Size) unit, i.e. the max (preferred) packet payload size.
	 * NOTE: MSS is MTU-40bytes for TCPIP headers, usually 1460 (1500-40) for standard ethernet
	 * connections, or 1452 (1492-40) for PPPOE connections.
	 * @return mss size in bytes
	 */
	public static int getTcpMssSize() {  return tcpMssSize;  }

	public static void refreshRates(int min_rate) {
		tcpMssSize = COConfigurationManager.getIntParameter("network.tcp.mtu.size") - 40;
	    if (tcpMssSize > min_rate)  tcpMssSize = min_rate - 1;
	    if (tcpMssSize < 512)  tcpMssSize = 512;
	}

	private final VirtualChannelSelector readSelector =
			new VirtualChannelSelector("TCP network manager", VirtualChannelSelector.OP_READ, true);
	private final VirtualChannelSelector writeSelector =
			new VirtualChannelSelector("TCP network manager", VirtualChannelSelector.OP_WRITE, true);

	private final TCPConnectionManager connectDisconnectManager = new TCPConnectionManager();

	private final IncomingSocketChannelManager incomingSocketchannelManager =
		new IncomingSocketChannelManager("TCP.Listen.Port", "TCP.Listen.Port.Enable");

	private long	readSelectCount;
	private long	writeSelectCount;

	protected TCPNetworkManager() {
		Set<String>	types = new HashSet<>();
		types.add(AzureusCoreStats.ST_NET_TCP_SELECT_READ_COUNT);
		types.add(AzureusCoreStats.ST_NET_TCP_SELECT_WRITE_COUNT);

		AzureusCoreStats.registerProvider(
			types,
			new AzureusCoreStatsProvider() {
				public void updateStats(Set types, Map values) {
					if (types.contains(AzureusCoreStats.ST_NET_TCP_SELECT_READ_COUNT)) {
						values.put(AzureusCoreStats.ST_NET_TCP_SELECT_READ_COUNT, new Long(readSelectCount));
					}
					if (types.contains(AzureusCoreStats.ST_NET_TCP_SELECT_WRITE_COUNT)) {
						values.put(AzureusCoreStats.ST_NET_TCP_SELECT_WRITE_COUNT, new Long(writeSelectCount));
					}
				}
			});

		//start read selector processing
		AEThread2 readSelectorThread =
	    	new AEThread2("ReadController:ReadSelector", true) {
		    	public void run() {
		    		while (true) {
		    			try {
		    				if (READ_SELECT_MIN_LOOP_TIME > 0) {
		    					long start = SystemTime.getHighPrecisionCounter();
		    					readSelector.select(READ_SELECT_LOOP_TIME);
		    					long duration = SystemTime.getHighPrecisionCounter() - start;
		    					duration = duration/1000000;
		    					long sleep = READ_SELECT_MIN_LOOP_TIME - duration;
		    					if (sleep > 0) {
		    						try {
		    							Thread.sleep(sleep);
		    						} catch (Throwable e) {
		    						}
		    					}
		    				} else {
			    				readSelector.select(READ_SELECT_LOOP_TIME);
		    				}
			    			readSelectCount++;
		    			} catch (Throwable t) {
	    					// filter out the boring ones
		    				if (!(t instanceof CancelledKeyException)) {
		    					Debug.out("readSelectorLoop() EXCEPTION: ", t);
		    				}
		    			}
		    		}
		    	}
	    	};

	    readSelectorThread.setPriority(Thread.MAX_PRIORITY - 2);
	    readSelectorThread.start();

    	//start write selector processing
	    AEThread2 writeSelectorThread =
	    	new AEThread2("WriteController:WriteSelector", true) {
		    	public void run() {
		    	    while (true) {
		    	    	try {
		    	    		if (WRITE_SELECT_MIN_LOOP_TIME > 0) {
		    					long	start = SystemTime.getHighPrecisionCounter();
		    					writeSelector.select(WRITE_SELECT_LOOP_TIME);
		    					long duration = SystemTime.getHighPrecisionCounter() - start;
		    					duration = duration/1000000;
		    					long	sleep = WRITE_SELECT_MIN_LOOP_TIME - duration;
		    					if (sleep > 0) {
		    						try {
		    							Thread.sleep(sleep);
		    						} catch (Throwable e) {
		    						}
		    					}
		    	    		} else {
		    	    			writeSelector.select(WRITE_SELECT_LOOP_TIME);
		    	    			writeSelectCount++;
		    	    		}
		    	    	} catch (Throwable t) {
		    	    		Debug.out("writeSelectorLoop() EXCEPTION: ", t);
		    	    	}
		  		    }
		    	}
	    	};

	    writeSelectorThread.setPriority(Thread.MAX_PRIORITY - 2);
	    writeSelectorThread.start();
	}

	public void setExplicitBindAddress(
			InetAddress	address) {
		incomingSocketchannelManager.setExplicitBindAddress(address);
	}

	public void clearExplicitBindAddress() {
		incomingSocketchannelManager.clearExplicitBindAddress();
	}

	public boolean isEffectiveBindAddress(InetAddress address) {
		return (incomingSocketchannelManager.isEffectiveBindAddress( address));
	}

	/**
	 * Get the socket channel connect / disconnect manager.
	 * @return connect manager
	 */
	public TCPConnectionManager getConnectDisconnectManager() {
		return connectDisconnectManager;
	}

	/**
	 * Get the virtual selector used for socket channel read readiness.
	 * @return read readiness selector
	 */
	public VirtualChannelSelector getReadSelector() {  return readSelector;  }

	/**
	 * Get the virtual selector used for socket channel write readiness.
	 * @return write readiness selector
	 */
	public VirtualChannelSelector getWriteSelector() {  return writeSelector;  }

	public boolean isTCPListenerEnabled() {
		return ( incomingSocketchannelManager.isEnabled());
	}

	/**
	 * Get port that the TCP server socket is listening for incoming connections on.
	 * @return port number
	 */
	public int getTCPListeningPortNumber() {
		return ( incomingSocketchannelManager.getTCPListeningPortNumber());
	}

	public long getLastIncomingNonLocalConnectionTime() {
		return ( incomingSocketchannelManager.getLastNonLocalConnectionTime());
	}
}
