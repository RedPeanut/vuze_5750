/*
 * Created on Dec 4, 2004
 * Created by Alon Rohter
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

import java.io.IOException;
import java.nio.channels.*;

import org.gudy.azureus2.core3.logging.*;
import org.gudy.azureus2.core3.util.*;

import com.aelitis.azureus.core.networkmanager.VirtualChannelSelector;




/**
 * Virtual server socket channel for listening and accepting incoming connections.
 */
public class
VirtualAcceptSelector
{
	private static final VirtualAcceptSelector	singleton = new VirtualAcceptSelector();

	public static VirtualAcceptSelector
	getSingleton() {
		return (singleton);
	}

	private static final LogIDs LOGID = LogIDs.NWMAN;

	private final VirtualChannelSelector acceptSelector =
						new VirtualChannelSelector("Accepter", VirtualChannelSelector.OP_ACCEPT, false);

	protected VirtualAcceptSelector() {
		AEThread selectThread = new AEThread("Accept Selector") {
			public void runSupport() {
				while (true) {
					try {
						acceptSelector.select(50);
					} catch (Throwable e) {
						Debug.printStackTrace(e);
					}
				}
			}
	    };
	    selectThread.setDaemon(true);
	    selectThread.start();
	}

	public void register(
		ServerSocketChannel			channel,
		final AcceptListener		listener) {
		acceptSelector.register(
			channel,
			new VirtualChannelSelector.VirtualAcceptSelectorListener() {
				public boolean selectSuccess(
					VirtualChannelSelector 	selector,
					ServerSocketChannel 	sc,
					Object 					attachment) {
					try {
						SocketChannel	newChannel = sc.accept();
						if (newChannel == null) {
							return (false);
						}
						
						try {
							newChannel.configureBlocking(false);
						} catch (IOException e) {
							newChannel.close();
							throw (e);
						}
						
						listener.newConnectionAccepted(newChannel);
						return (true);
					} catch (IOException e) {
						Debug.printStackTrace(e);
						return (true);
					}
				}

				public void selectFailure(
					VirtualChannelSelector 	selector,
					ServerSocketChannel 	sc,
					Object 					attachment,
					Throwable 				msg) {
					Debug.printStackTrace(msg);
				}
			},
			null);
	}

	public void cancel(
		ServerSocketChannel		channel) {
		acceptSelector.cancel(channel);
	}

	  /**
	   * Listener notified when a new incoming connection is accepted.
	   */

	public interface
	AcceptListener
	{
	    /**
	     * The given connection has just been accepted.
	     * @param channel new connection
	     */
		public void newConnectionAccepted(
			SocketChannel 	channel);
	}
}
