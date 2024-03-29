/*
 * Created on 10 Jul 2006
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

package com.aelitis.azureus.core.nat;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

import org.gudy.azureus2.core3.util.AERunnable;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.ThreadPool;
import org.gudy.azureus2.plugins.PluginInterface;
import org.gudy.azureus2.pluginsimpl.local.PluginCoreUtils;

import com.aelitis.azureus.core.AzureusCore;
import com.aelitis.azureus.core.dht.DHT;
import com.aelitis.azureus.core.dht.nat.DHTNATPuncher;
import com.aelitis.azureus.core.dht.nat.DHTNATPuncherAdapter;
import com.aelitis.azureus.core.dht.transport.DHTTransportContact;
import com.aelitis.azureus.plugins.dht.DHTPlugin;

public class
NATTraverser
	implements DHTNATPuncherAdapter
{
	public static final int	TRAVERSE_REASON_PEER_DATA			= 1;
	public static final int	TRAVERSE_REASON_GENERIC_MESSAGING	= 2;
	public static final int	TRAVERSE_REASON_PAIR_TUNNEL			= 3;

	private static final int	MAX_QUEUE_SIZE	= 128;

	private final AzureusCore		core;
	private DHTNATPuncher	puncher;

	private final ThreadPool	threadPool = new ThreadPool("NATTraverser", 16, true);

	private final Map	handlers = new HashMap();

	public NATTraverser(
		AzureusCore		_core) {
		core	= _core;
	}

	public void registerHandler(
		NATTraversalHandler		handler) {
		synchronized(handlers) {

			handlers.put(new Integer(handler.getType()), handler);
		}
	}

	public NATTraversal attemptTraversal(
		final NATTraversalHandler		handler,
		final InetSocketAddress			target,
		final Map						request,
		boolean							sync,
		final NATTraversalObserver		listener) {
		
		final NATTraversal traversal =
			new NATTraversal() {
				private boolean cancelled;
				public void cancel() {
					cancelled	= true;
				}
				public boolean isCancelled() {
					return (cancelled);
				}
			};
			
		if (sync) {
			syncTraverse(handler, target, request, listener);
		} else {
			if (threadPool.getQueueSize() >= MAX_QUEUE_SIZE) {
				Debug.out("NATTraversal queue full");
				listener.failed(NATTraversalObserver.FT_QUEUE_FULL);
			} else {
				threadPool.run(
					new AERunnable() {
						public void runSupport() {
							if (traversal.isCancelled()) {
								listener.failed(NATTraversalObserver.FT_CANCELLED);
							} else {
								syncTraverse(handler, target, request, listener);
							}
						}
					});
			}
		}
		return (traversal);
	}

	protected void syncTraverse(
		NATTraversalHandler		handler,
		InetSocketAddress		target,
		Map						request,
		NATTraversalObserver	listener) {
		try {
			int	type = handler.getType();
			synchronized(this) {
				if (puncher == null) {
					if (!PluginCoreUtils.isInitialisationComplete()) {
						listener.failed(new Exception("NAT traversal failed, initialisation not complete"));
						return;
					}
					PluginInterface dht_pi =
						core.getPluginManager().getPluginInterfaceByClass(DHTPlugin.class);
					if (dht_pi != null) {
						DHTPlugin dht_plugin = (DHTPlugin)dht_pi.getPlugin();
						if (dht_plugin.isEnabled()) {
							DHT dht = dht_plugin.getDHT(DHT.NW_MAIN);
							if (dht == null) {
								dht = dht_plugin.getDHT(DHT.NW_CVS);
							}
							if (dht != null) {
								puncher = dht.getNATPuncher();
							}
						}
					}
				}
				if (puncher == null) {
					listener.disabled();
					return;
				}
			}
			if (request == null) {
				request = new HashMap();
			}
			request.put("_travreas", new Long( type));
			InetSocketAddress[]	target_a = { target };
			DHTTransportContact[]	rendezvous_used = {null};
			Map	reply = puncher.punch(handler.getName(), target_a, rendezvous_used, request);
			if (reply == null) {
				if (rendezvous_used[0] == null) {
					listener.failed(NATTraversalObserver.FT_NO_RENDEZVOUS);
				} else {
					listener.failed(new Exception("NAT traversal failed"));
				}
			} else {
				listener.succeeded(rendezvous_used[0].getAddress(), target_a[0], reply);
			}
		} catch (Throwable e) {
			listener.failed(e);
		}
	}

	public Map
	sendMessage(
		NATTraversalHandler		handler,
		InetSocketAddress		rendezvous,
		InetSocketAddress		target,
		Map						message )

		throws NATTraversalException
	{
		if (puncher == null) {

			throw (new NATTraversalException("Puncher unavailable"));
		}

		message.put("_travreas", new Long( handler.getType()));

		Map	reply = puncher.sendMessage(rendezvous, target, message);

		if (reply == null) {

			throw (new NATTraversalException("Send message failed"));
		}

		return (reply);
	}

	public Map
	getClientData(
		InetSocketAddress	originator,
		Map					originator_data) {
		Long	type = (Long)originator_data.get("_travreas");

		if (type != null) {

			NATTraversalHandler	handler;

			synchronized(handlers) {

				handler = (NATTraversalHandler)handlers.get(new Integer( type.intValue()));
			}


			if (handler != null) {

				return (handler.process( originator, originator_data));
			}
		}

		return (null);
	}
}
