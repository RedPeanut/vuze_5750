/*
 * File    : PRUDPPacketReceiverFactoryImpl.java
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

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.gudy.azureus2.core3.util.AEMonitor;
import org.gudy.azureus2.core3.util.Debug;

import com.aelitis.net.udp.uc.PRUDPPacketHandler;
import com.aelitis.net.udp.uc.PRUDPReleasablePacketHandler;
import com.aelitis.net.udp.uc.PRUDPRequestHandler;

import hello.util.Log;
import hello.util.Util;

public class PRUDPPacketHandlerFactoryImpl {
	
	private static String TAG = PRUDPPacketHandlerFactoryImpl.class.getSimpleName();
	
	private static Map<Integer, PRUDPPacketHandlerImpl> receiverMap = new HashMap<Integer,PRUDPPacketHandlerImpl>();

	private static AEMonitor	classMonitor		= new AEMonitor("PRUDPPHF");
	private static Map			releasableMap		= new HashMap();
	private static Set			nonReleasableSet	= new HashSet();

	public static List<PRUDPPacketHandler> getHandlers() {
		try {
			classMonitor.enter();
			return (new ArrayList<PRUDPPacketHandler>(receiverMap.values()));
		} finally {
			classMonitor.exit();
		}
	}

	public static PRUDPPacketHandler getHandler(
		int						port,
		InetAddress				bindIp,
		PRUDPRequestHandler		requestHandler) {
		
		final Integer f_port = new Integer(port);
		try {
			classMonitor.enter();
			nonReleasableSet.add(f_port);
			PRUDPPacketHandlerImpl receiver = receiverMap.get(f_port);
			if (receiver == null) {
				receiver = new PRUDPPacketHandlerImpl(port, bindIp, null);
				receiverMap.put(f_port, receiver);
				Log.d(TAG, String.format("receiverMap.put(%d) is called...", f_port));
				Util.printMap("", receiverMap, "");
			}
			
			// only set the incoming request handler if one has been specified. This is important when
			// the port is shared (e.g. default udp tracker and dht) and only one usage has need to handle
			// unsolicited inbound requests as we don't want the tracker null handler to erase the dht's
			// one
			if (requestHandler != null) {
				receiver.setRequestHandler(requestHandler);
			}
			
			return (receiver);
		} finally {
			classMonitor.exit();
		}
	}

	public static PRUDPReleasablePacketHandler getReleasableHandler(
		int						port,
		PRUDPRequestHandler		request_handler) {
		
		final Integer	f_port = new Integer(port);
		try {
			classMonitor.enter();
			PRUDPPacketHandlerImpl	receiver = (PRUDPPacketHandlerImpl)receiverMap.get(f_port);
			if (receiver == null) {
				receiver = new PRUDPPacketHandlerImpl(port, null, null);
				receiverMap.put(f_port, receiver);
				Log.d(TAG, "receiverMap.put() is called...");
				Util.printMap("", receiverMap, "");
			}
			
			// only set the incoming request handler if one has been specified. This is important when
			// the port is shared (e.g. default udp tracker and dht) and only one usage has need to handle
			// unsolicited inbound requests as we don't want the tracker null handler to erase the dht's
			// one
			if (request_handler != null) {
				receiver.setRequestHandler(request_handler);
			}
			
			final PRUDPPacketHandlerImpl f_receiver = receiver;
			final PRUDPReleasablePacketHandler rel =
				new PRUDPReleasablePacketHandler() {
				
					public PRUDPPacketHandler getHandler() {
						return (f_receiver);
					}
					
					public void release() {
						try {
							classMonitor.enter();
							List l = (List)releasableMap.get(f_port);
							if (l == null) {
								Debug.out("hmm");
							} else {
								if (!l.remove(this)) {
									Debug.out("hmm");
								} else {
									if (l.size() == 0) {
										if (!nonReleasableSet.contains(f_port)) {
											f_receiver.destroy();
											receiverMap.remove(f_port);
											Log.d(TAG, String.format("receiverMap.remove(%d) is called...", f_port));
											//Util.printMap("", receiverMap, "");
										}
										releasableMap.remove(f_port);
									}
								}
							}
						} finally {
							classMonitor.exit();
						}
					}
				};
			List l = (List)releasableMap.get(f_port);
			if (l == null) {
				l = new ArrayList();
				releasableMap.put(f_port, l);
			}
			l.add(rel);
			if (l.size() > 1024) {
				Debug.out("things going wrong here");
			}
			return (rel);
		} finally {
			classMonitor.exit();
		}
	}
}
