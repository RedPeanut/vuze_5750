/*
 * Created on Dec 17, 2013
 * Created by Paul Gardner
 *
 * Copyright 2013 Azureus Software, Inc.  All rights reserved.
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
 */


package com.aelitis.azureus.core.proxy.impl;

import java.lang.ref.WeakReference;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketAddress;
import java.net.URL;
import java.util.*;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.config.ParameterListener;
import org.gudy.azureus2.core3.util.AENetworkClassifier;
import org.gudy.azureus2.core3.util.AESemaphore;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.SystemTime;
import org.gudy.azureus2.core3.util.TorrentUtils;
import org.gudy.azureus2.core3.util.UrlUtils;
import org.gudy.azureus2.plugins.PluginAdapter;
import org.gudy.azureus2.plugins.PluginEvent;
import org.gudy.azureus2.plugins.PluginEventListener;
import org.gudy.azureus2.plugins.PluginInterface;
import org.gudy.azureus2.plugins.ipc.IPCInterface;
import org.gudy.azureus2.pluginsimpl.local.PluginInitializer;

import com.aelitis.azureus.core.AzureusCore;
import com.aelitis.azureus.core.AzureusCoreFactory;
import com.aelitis.azureus.core.proxy.AEProxySelectorFactory;
import com.aelitis.azureus.core.proxy.AEProxyFactory.PluginHTTPProxy;
import com.aelitis.azureus.core.proxy.AEProxyFactory.PluginProxy;
import com.aelitis.azureus.core.util.CopyOnWriteList;
import com.aelitis.azureus.core.util.CopyOnWriteSet;
import com.aelitis.azureus.plugins.dht.DHTPluginInterface;

public class
AEPluginProxyHandler
{
	private static final CopyOnWriteList<PluginInterface>		plugins = new CopyOnWriteList<PluginInterface>();

	private static final int			plugin_init_max_wait	= 30*1000;
	private static final AESemaphore 	plugin_init_complete 	= new AESemaphore("init:waiter");

	private static boolean	enable_plugin_proxies_with_socks;

	static{
		try {
			COConfigurationManager.addAndFireParameterListener(
				"Proxy.SOCKS.disable.plugin.proxies",
				new ParameterListener() {
					public void parameterChanged(String parameterName) {
						enable_plugin_proxies_with_socks = !COConfigurationManager.getBooleanParameter(parameterName);
					}
				});

			AzureusCore core = AzureusCoreFactory.getSingleton();

			PluginInterface default_pi = core.getPluginManager().getDefaultPluginInterface();

			default_pi.addEventListener(
					new PluginEventListener() {
						public void handleEvent(
							PluginEvent ev) {
							int	type = ev.getType();

							if (type == PluginEvent.PEV_PLUGIN_OPERATIONAL) {

								pluginAdded((PluginInterface)ev.getValue());
							}
							if (type == PluginEvent.PEV_PLUGIN_NOT_OPERATIONAL) {

								pluginRemoved((PluginInterface)ev.getValue());
							}
						}
					});

				PluginInterface[] plugins = default_pi.getPluginManager().getPlugins(true);

				for (PluginInterface pi: plugins) {

					if (pi.getPluginState().isOperational()) {

						pluginAdded(pi);
					}
				}

				default_pi.addListener(
					new PluginAdapter() {
						public void initializationComplete() {
							plugin_init_complete.releaseForever();
						}
					});

		} catch (Throwable e) {

			e.printStackTrace();
		}
	}

	private static void
	pluginAdded(
		PluginInterface pi) {
		String pid = pi.getPluginID();

		if (pid.equals("aznettor") || pid.equals("azneti2phelper")) {

			plugins.add(pi);
		}
	}

	private static void
	pluginRemoved(
		PluginInterface pi) {
		String pid = pi.getPluginID();

		if (pid.equals("aznettor") || pid.equals("azneti2phelper")) {

			plugins.remove(pi);
		}
	}

	private static boolean
	waitForPlugins(
		int		max_wait) {
		if (PluginInitializer.isInitThread()) {

			Debug.out("Hmm, rework this");
		}

		return (plugin_init_complete.reserve( max_wait));
	}

	private static final Map<Proxy,WeakReference<PluginProxyImpl>>	proxy_map 	= new IdentityHashMap<Proxy,WeakReference<PluginProxyImpl>>();
	private static final CopyOnWriteSet<SocketAddress>				proxy_list	= new CopyOnWriteSet<SocketAddress>(false);

	public static boolean
	hasPluginProxyForNetwork(String		network,
		boolean		supports_data) {
		long start = SystemTime.getMonotonousTime();

		while (true) {

			long	rem = plugin_init_max_wait - (SystemTime.getMonotonousTime() - start);

			if (rem <= 0) {

				return (false);
			}

			boolean wait_complete = waitForPlugins(Math.min( (int)rem, 1000));

			boolean result = getPluginProxyForNetwork(network, supports_data) != null;

			if (result || wait_complete) {

				return (result);
			}
		}
	}

	private static PluginInterface
	getPluginProxyForNetwork(String		network,
		boolean		supports_data) {
		for (PluginInterface pi: plugins) {

			String pid = pi.getPluginID();

			if (pid.equals("aznettor") && network == AENetworkClassifier.AT_TOR) {

				if (!supports_data) {

					return (pi);
				}
			}

			if (pid.equals("azneti2phelper") && network == AENetworkClassifier.AT_I2P) {

				return (pi);
			}
		}

		return (null);
	}

	public static boolean
	hasPluginProxy() {
		waitForPlugins(plugin_init_max_wait);

		for (PluginInterface pi: plugins) {

			try {
				IPCInterface ipc = pi.getIPC();

				if (ipc.canInvoke("testHTTPPseudoProxy", new Object[]{ TorrentUtils.getDecentralisedEmptyURL() })) {

					return (true);
				}
			} catch (Throwable e) {
			}
		}

		return (false);
	}

	private static boolean
	isEnabled() {
		Proxy system_proxy = AEProxySelectorFactory.getSelector().getActiveProxy();

		if (system_proxy == null || system_proxy.equals( Proxy.NO_PROXY)) {

			return (true);

		} else {

			return (enable_plugin_proxies_with_socks);
		}
	}

		/**
		 * This method should NOT BE CALLED as it is in the .impl package - unfortunately the featman plugin calls it - will be removed
		 * when aefeatman 1.3.2 is released
		 * @param reason
		 * @param target
		 * @deprecated
		 * @return
		 */

	public static PluginProxyImpl
	getPluginProxy(String	reason,
		URL		target) {
		return (getPluginProxy( reason, target, null, false));
	}

	public static PluginProxyImpl
	getPluginProxy(String					reason,
		URL						target,
		Map<String,Object>		properties,
		boolean					can_wait) {
		if (isEnabled()) {

			String url_protocol = target.getProtocol().toLowerCase();

			if (url_protocol.startsWith("http") || url_protocol.equals("ftp")) {

				if (can_wait) {

					waitForPlugins(0);
				}

				if (properties == null) {

					properties = new HashMap<String, Object>();
				}

				for (PluginInterface pi: plugins) {

					try {
						IPCInterface ipc = pi.getIPC();

						Object[] proxy_details;

						if (ipc.canInvoke("getProxy", new Object[]{ reason, target, properties })) {

							proxy_details = (Object[])ipc.invoke("getProxy", new Object[]{ reason, target, properties });

						} else {

							proxy_details = (Object[])ipc.invoke("getProxy", new Object[]{ reason, target });
						}

						if (proxy_details != null) {

							if (proxy_details.length == 2) {

									// support old plugins

								proxy_details = new Object[]{ proxy_details[0], proxy_details[1], target.getHost()};
							}

							return (new PluginProxyImpl( target.toExternalForm(), reason, ipc, properties, proxy_details));
						}
					} catch (Throwable e) {
					}
				}
			}
		}

		return (null);
	}

	public static PluginProxyImpl
	getPluginProxy(String					reason,
		String					host,
		int						port,
		Map<String,Object>		properties) {
		if (isEnabled()) {

			if (properties == null) {

				properties = new HashMap<String, Object>();
			}

			for (PluginInterface pi: plugins) {

				try {
					IPCInterface ipc = pi.getIPC();

					Object[] proxy_details;

					if (ipc.canInvoke("getProxy", new Object[]{ reason, host, port, properties })) {

						proxy_details = (Object[])ipc.invoke("getProxy", new Object[]{ reason, host, port, properties });

					} else {

						proxy_details = (Object[])ipc.invoke("getProxy", new Object[]{ reason, host, port });
					}

					if (proxy_details != null) {

						return (new PluginProxyImpl( host + ":" + port, reason, ipc, properties, proxy_details));
					}
				} catch (Throwable e) {
				}
			}
		}

		return (null);
	}

	public static PluginProxy
	getPluginProxy(
		Proxy		proxy) {
		if (proxy != null) {

			synchronized(proxy_map) {

				WeakReference<PluginProxyImpl>	ref = proxy_map.get(proxy);

				if (ref != null) {

					return ( ref.get());
				}
			}
		}

		return (null);
	}

	public static boolean
	isPluginProxy(
		SocketAddress		address) {
		return (proxy_list.contains( address));
	}

	public static Boolean
	testPluginHTTPProxy(
		URL			url,
		boolean		can_wait) {
		if (isEnabled()) {

			String url_protocol = url.getProtocol().toLowerCase();

			if (url_protocol.startsWith("http")) {

				if (can_wait) {

					waitForPlugins(0);
				}

				for (PluginInterface pi: plugins) {

					try {
						IPCInterface ipc = pi.getIPC();

						return ((Boolean)ipc.invoke("testHTTPPseudoProxy", new Object[]{ url }));

					} catch (Throwable e) {
					}
				}
			} else {

				Debug.out("Unsupported protocol: " + url_protocol);
			}
		}

		return (null);
	}

	public static PluginHTTPProxyImpl
	getPluginHTTPProxy(String		reason,
		URL			url,
		boolean		can_wait) {
		if (isEnabled()) {

			String url_protocol = url.getProtocol().toLowerCase();

			if (url_protocol.startsWith("http")) {

				if (can_wait) {

					waitForPlugins(0);
				}

				for (PluginInterface pi: plugins) {

					try {
						IPCInterface ipc = pi.getIPC();

						Proxy proxy = (Proxy)ipc.invoke("createHTTPPseudoProxy", new Object[]{ reason, url });

						if (proxy != null) {

							return (new PluginHTTPProxyImpl( reason, ipc, proxy));
						}
					} catch (Throwable e) {
					}
				}
			} else {

				Debug.out("Unsupported protocol: " + url_protocol);
			}
		}

		return (null);
	}

	public static List<PluginInterface>
	getPluginHTTPProxyProviders(
		boolean		can_wait) {
		if (can_wait) {

			waitForPlugins(0);
		}

		List<PluginInterface> pis =
			AzureusCoreFactory.getSingleton().getPluginManager().getPluginsWithMethod(
				"createHTTPPseudoProxy",
				new Class[]{ String.class, URL.class });

		return (pis);
	}

	public static Map<String,Object>
	getPluginServerProxy(String					reason,
		String					network,
		String					server_uid,
		Map<String,Object>		options) {
		waitForPlugins(plugin_init_max_wait);

		PluginInterface pi = getPluginProxyForNetwork(network, false);

		if (pi == null) {

			return (null);
		}

		options = new HashMap<String,Object>(options);

		options.put("id", server_uid);

		try {
			IPCInterface ipc = pi.getIPC();

			Map<String,Object> reply = (Map<String,Object>)ipc.invoke("getProxyServer", new Object[]{ reason, options });

			return (reply);

		} catch (Throwable e) {

		}

		return (null);
	}

	public static DHTPluginInterface
	getPluginDHTProxy(String					reason,
		String					network,
		Map<String,Object>		options) {
		waitForPlugins(plugin_init_max_wait);

		PluginInterface pi = getPluginProxyForNetwork(network, false);

		if (pi == null) {

			return (null);
		}

		try {
			IPCInterface ipc = pi.getIPC();

			DHTPluginInterface reply = (DHTPluginInterface)ipc.invoke("getProxyDHT", new Object[]{ reason, options });

			return (reply);

		} catch (Throwable e) {

		}

		return (null);
	}

	private static class
	PluginProxyImpl
		implements PluginProxy
	{
		private final long					create_time = SystemTime.getMonotonousTime();

		private final String				target;
		private final String				reason;

		private final IPCInterface			ipc;
		private final Map<String,Object>	proxy_options;
		private final Object[]				proxy_details;

		private final List<PluginProxyImpl>	children = new ArrayList<AEPluginProxyHandler.PluginProxyImpl>();

		private
		PluginProxyImpl(String				_target,
			String				_reason,
			IPCInterface		_ipc,
			Map<String,Object>	_proxy_options,
			Object[]			_proxy_details) {
			target				= _target;
			reason				= _reason;
			ipc					= _ipc;
			proxy_options		= _proxy_options;
			proxy_details		= _proxy_details;

			WeakReference<PluginProxyImpl>	my_ref = new WeakReference<PluginProxyImpl>(this);

			List<PluginProxyImpl>	removed = new ArrayList<PluginProxyImpl>();

			synchronized(proxy_map) {

				Proxy proxy = getProxy();

				SocketAddress address = proxy.address();

				if (!proxy_list.contains( address)) {

					proxy_list.add(address);
				}

				proxy_map.put(proxy, my_ref);

				if (proxy_map.size() > 1024) {

					long	now = SystemTime.getMonotonousTime();

					Iterator<WeakReference<PluginProxyImpl>>	it = proxy_map.values().iterator();

					while (it.hasNext()) {

						WeakReference<PluginProxyImpl> ref = it.next();

						PluginProxyImpl	pp = ref.get();

						if (pp == null) {

							it.remove();

						} else {

							if (now - pp.create_time > 5*60*1000) {

								removed.add(pp);

								it.remove();
							}
						}
					}
				}
			}

			for (PluginProxyImpl pp: removed) {

				pp.setOK(false);
			}
		}

		public String
		getTarget() {
			return (target);
		}

		public PluginProxy
		getChildProxy(String		child_reason,
			URL 		url) {
			PluginProxyImpl	child = getPluginProxy(reason + " - " + child_reason, url, proxy_options, false);

			if (child != null) {

				synchronized(children) {

					children.add(child);
				}
			}

			return (child);
		}

		public Proxy
		getProxy() {
			return ((Proxy)proxy_details[0]);
		}

			// URL methods

		public URL
		getURL() {
			return ((URL)proxy_details[1]);
		}

		public String
		getURLHostRewrite() {
			return ((String)proxy_details[2]);
		}

			// host:port methods

		public String
		getHost() {
			return ((String)proxy_details[1]);
		}

		public int
		getPort() {
			return ((Integer)proxy_details[2]);
		}

		public void
		setOK(
			boolean	good) {
			try {
				ipc.invoke("setProxyStatus", new Object[]{ proxy_details[0], good });

			} catch (Throwable e) {
			}

			List<PluginProxyImpl> kids;

			synchronized(children) {

				kids = new ArrayList<PluginProxyImpl>(children);

				children.clear();
			}

			for (PluginProxyImpl child: kids) {

				child.setOK(good);
			}

			synchronized(proxy_map) {

				proxy_map.remove( getProxy());
			}
		}
	}

	private static class
	PluginHTTPProxyImpl
		implements PluginHTTPProxy
	{
		private final String			reason;
		private final IPCInterface	ipc;
		private final Proxy			proxy;

		private
		PluginHTTPProxyImpl(String				_reason,
			IPCInterface		_ipc,
			Proxy				_proxy) {
			reason				= _reason;
			ipc					= _ipc;
			proxy				= _proxy;
		}

		public Proxy
		getProxy() {
			return (proxy);
		}

		public String
		proxifyURL(String url) {
			try {
				URL _url = new URL(url);

				InetSocketAddress pa = (InetSocketAddress)proxy.address();

				_url = UrlUtils.setHost( _url, pa.getAddress().getHostAddress());
				_url = UrlUtils.setPort( _url, pa.getPort());

				url = _url.toExternalForm();

				url += (url.indexOf('?')==-1?"?":"&") + "_azpproxy=1";

				return (url);

			} catch (Throwable e) {

				Debug.out("Failed to proxify URL: " + url, e);

				return (url);
			}
		}

		public void destroy() {
			try {

				ipc.invoke("destroyHTTPPseudoProxy", new Object[]{ proxy });

			} catch (Throwable e) {

				Debug.out(e);
			}
		}
	}
}
