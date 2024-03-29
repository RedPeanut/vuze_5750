/*
 * Created on Oct 5, 2009
 * Created by Paul Gardner
 *
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
 */


package com.aelitis.azureus.core.pairing.impl;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.logging.LogAlert;
import org.gudy.azureus2.core3.logging.Logger;
import org.gudy.azureus2.core3.util.AEDiagnostics;
import org.gudy.azureus2.core3.util.AEDiagnosticsEvidenceGenerator;
import org.gudy.azureus2.core3.util.AERunnable;
import org.gudy.azureus2.core3.util.AESemaphore;
import org.gudy.azureus2.core3.util.AEThread2;
import org.gudy.azureus2.core3.util.AEVerifier;
import org.gudy.azureus2.core3.util.AsyncDispatcher;
import org.gudy.azureus2.core3.util.BDecoder;
import org.gudy.azureus2.core3.util.BEncoder;
import org.gudy.azureus2.core3.util.Base32;
import org.gudy.azureus2.core3.util.Constants;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.DelayedEvent;
import org.gudy.azureus2.core3.util.FileUtil;
import org.gudy.azureus2.core3.util.IndentWriter;
import org.gudy.azureus2.core3.util.SimpleTimer;
import org.gudy.azureus2.core3.util.SystemProperties;
import org.gudy.azureus2.core3.util.SystemTime;
import org.gudy.azureus2.core3.util.TimerEvent;
import org.gudy.azureus2.core3.util.TimerEventPerformer;
import org.gudy.azureus2.core3.util.TimerEventPeriodic;
import org.gudy.azureus2.core3.util.UrlUtils;
import org.gudy.azureus2.plugins.PluginInterface;
import org.gudy.azureus2.plugins.clientid.ClientIDException;
import org.gudy.azureus2.plugins.clientid.ClientIDGenerator;
import org.gudy.azureus2.plugins.tracker.web.TrackerWebPageRequest;
import org.gudy.azureus2.plugins.tracker.web.TrackerWebPageResponse;
import org.gudy.azureus2.plugins.ui.UIManager;
import org.gudy.azureus2.plugins.ui.UIManagerEvent;
import org.gudy.azureus2.plugins.ui.config.ActionParameter;
import org.gudy.azureus2.plugins.ui.config.BooleanParameter;
import org.gudy.azureus2.plugins.ui.config.ConfigSection;
import org.gudy.azureus2.plugins.ui.config.HyperlinkParameter;
import org.gudy.azureus2.plugins.ui.config.InfoParameter;
import org.gudy.azureus2.plugins.ui.config.LabelParameter;
import org.gudy.azureus2.plugins.ui.config.Parameter;
import org.gudy.azureus2.plugins.ui.config.ParameterListener;
import org.gudy.azureus2.plugins.ui.config.StringParameter;
import org.gudy.azureus2.plugins.ui.model.BasicPluginConfigModel;
import org.gudy.azureus2.plugins.utils.DelayedTask;
import org.gudy.azureus2.plugins.utils.StaticUtilities;
import org.gudy.azureus2.pluginsimpl.local.PluginInitializer;
import org.gudy.azureus2.pluginsimpl.local.clientid.ClientIDManagerImpl;
import org.gudy.azureus2.pluginsimpl.local.utils.resourcedownloader.ResourceDownloaderFactoryImpl;

import com.aelitis.azureus.core.AzureusCore;
import com.aelitis.azureus.core.AzureusCoreFactory;
import com.aelitis.azureus.core.AzureusCoreRunningListener;
import com.aelitis.azureus.core.networkmanager.admin.NetworkAdmin;
import com.aelitis.azureus.core.networkmanager.admin.NetworkAdminHTTPProxy;
import com.aelitis.azureus.core.networkmanager.admin.NetworkAdminNetworkInterface;
import com.aelitis.azureus.core.networkmanager.admin.NetworkAdminNetworkInterfaceAddress;
import com.aelitis.azureus.core.networkmanager.admin.NetworkAdminSocksProxy;
import com.aelitis.azureus.core.pairing.*;
import com.aelitis.azureus.core.security.CryptoManager;
import com.aelitis.azureus.core.security.CryptoManagerFactory;
import com.aelitis.azureus.core.util.CopyOnWriteList;
import com.aelitis.azureus.plugins.upnp.UPnPPlugin;
import com.aelitis.azureus.plugins.upnp.UPnPPluginService;
import com.aelitis.azureus.util.JSONUtils;
import com.aelitis.net.upnp.UPnPRootDevice;

public class
PairingManagerImpl
	implements PairingManager, AEDiagnosticsEvidenceGenerator
{
	private static final boolean DEBUG	= false;

	private static final String	SERVICE_URL;

	static{
		String url = System.getProperty("az.pairing.url", "");

		if (url.length() == 0) {

			SERVICE_URL = Constants.PAIRING_URL;

		} else {

			SERVICE_URL = url;
		}
	}

	private static final PairingManagerImpl	singleton = new PairingManagerImpl();

	public static PairingManager
	getSingleton() {
		return (singleton);
	}

	private static final int	GLOBAL_UPDATE_PERIOD	= 60*1000;
	private static final int	CD_REFRESH_PERIOD		= 23*60*60*1000;
	private static final int	CD_REFRESH_TICKS		= CD_REFRESH_PERIOD / GLOBAL_UPDATE_PERIOD;

	private static final int	CONNECT_TEST_PERIOD_MILLIS	= 30*60*1000;

	private AzureusCore	azureus_core;

	final BooleanParameter 	param_enable;


	private final InfoParameter		param_ac_info;
	private final InfoParameter		param_status_info;
	private final InfoParameter		param_last_error;
	private final HyperlinkParameter	param_view;

	final BooleanParameter 	param_srp_enable;
	private final LabelParameter		param_srp_state;

	private final BooleanParameter 	param_e_enable;
	private final StringParameter		param_public_ipv4;
	private final StringParameter		param_public_ipv6;
	private final StringParameter		param_host;

	private final BooleanParameter 	param_net_enable;

	private final StringParameter		param_local_ipv4;
	private final StringParameter		param_local_ipv6;

	private final BooleanParameter 	param_icon_enable;

	private final Map<String,PairedServiceImpl>		services = new HashMap<String, PairedServiceImpl>();

	private final AESemaphore	init_sem = new AESemaphore("PM:init");

	private TimerEventPeriodic	global_update_event;

	private InetAddress		current_v4;
	private InetAddress		current_v6;

	private String			local_v4	= "";
	private String			local_v6	= "";

	private PairingManagerTunnelHandler	tunnel_handler;

	private boolean	update_outstanding;
	private boolean	updates_enabled;

	private static final int MIN_UPDATE_PERIOD_DEFAULT	= 10*1000;
	private static final int MAX_UPDATE_PERIOD_DEFAULT	= 60*60*1000;

	private int min_update_period	= MIN_UPDATE_PERIOD_DEFAULT;
	private int max_update_period	= MAX_UPDATE_PERIOD_DEFAULT;


	private final AsyncDispatcher	dispatcher = new AsyncDispatcher();

	private boolean			must_update_once;
	private boolean			update_in_progress;
	private TimerEvent		deferred_update_event;
	private long			last_update_time		= -1;
	private int				consec_update_fails;

	private long			qr_version = COConfigurationManager.getLongParameter("pairing.qr.ver", 0);

	private String			last_message;

	final Map<String,Object[]>	local_address_checks = new HashMap<String, Object[]>();

	private final CopyOnWriteList<PairingManagerListener>	listeners = new CopyOnWriteList<PairingManagerListener>();

	private UIAdapter		ui;

	private int	tests_in_progress = 0;

	protected PairingManagerImpl() {
		AEDiagnostics.addEvidenceGenerator(this);

		try {
			ui = (UIAdapter)Class.forName("com.aelitis.azureus.core.pairing.impl.swt.PMSWTImpl").newInstance();

		} catch (Throwable e) {

		}

		must_update_once = COConfigurationManager.getBooleanParameter("pairing.updateoutstanding");

		PluginInterface default_pi = PluginInitializer.getDefaultInterface();

		final UIManager	ui_manager = default_pi.getUIManager();

		BasicPluginConfigModel configModel = ui_manager.createBasicPluginConfigModel(
				ConfigSection.SECTION_CONNECTION, CONFIG_SECTION_ID);

		configModel.addHyperlinkParameter2("ConfigView.label.please.visit.here", MessageText.getString("ConfigView.section.connection.pairing.url"));

		param_enable = configModel.addBooleanParameter2("pairing.enable", "pairing.enable", false);

		String	access_code = readAccessCode();

		param_ac_info = configModel.addInfoParameter2("pairing.accesscode", access_code);

		param_status_info 	= configModel.addInfoParameter2("pairing.status.info", "");

		param_last_error	= configModel.addInfoParameter2("pairing.last.error", "");

		param_view = configModel.addHyperlinkParameter2("pairing.view.registered", SERVICE_URL + "/web/view?ac=" + access_code);

		if (access_code.length() == 0) {

			param_view.setEnabled(false);
		}

		COConfigurationManager.registerExportedParameter("pairing.enable", param_enable.getConfigKeyName());
		COConfigurationManager.registerExportedParameter("pairing.access_code", param_ac_info.getConfigKeyName());

		final ActionParameter ap = configModel.addActionParameter2("pairing.ac.getnew", "pairing.ac.getnew.create");

		ap.addListener(
			new ParameterListener() {
				public void parameterChanged(
					Parameter 	param ) {
					try {
						ap.setEnabled(false);

						allocateAccessCode(false);

						SimpleTimer.addEvent(
							"PM:enabler",
							SystemTime.getOffsetTime(30*1000),
							new TimerEventPerformer() {
								public void perform(
									TimerEvent event ) {
									ap.setEnabled(true);
								}
							});

					} catch (Throwable e) {

						ap.setEnabled(true);

						String details = MessageText.getString(
								"pairing.alloc.fail",
								new String[]{ Debug.getNestedExceptionMessage(e)});

						ui_manager.showMessageBox(
								"pairing.op.fail",
								"!" + details + "!",
								UIManagerEvent.MT_OK);
					}
				}
			});

			// srp

		LabelParameter	param_srp_info = configModel.addLabelParameter2("pairing.srp.info");

		HyperlinkParameter param_srp_link = configModel.addHyperlinkParameter2("label.more.info.here", MessageText.getString("ConfigView.section.connection.pairing.srp.url"));

		param_srp_enable 	= configModel.addBooleanParameter2("pairing.srp.enable", "pairing.srp.enable", false);

		COConfigurationManager.registerExportedParameter("pairing.srp_enable", param_srp_enable.getConfigKeyName());

		param_srp_state 	= configModel.addLabelParameter2("");

		updateSRPState();

		final ActionParameter param_srp_set = configModel.addActionParameter2("pairing.srp.setpw", "pairing.srp.setpw.doit");

		param_srp_set.addListener(
			new ParameterListener() {
				public void parameterChanged(
					Parameter 	param ) {
					param_srp_set.setEnabled(false);

					new AEThread2("getpw") {
						public void run() {
							try {
								if (ui != null) {

									char[] password = ui.getSRPPassword();

									if (password != null) {

										tunnel_handler.setSRPPassword(password);
									}
								} else {

									Debug.out("No UI available");
								}
							} finally {

								param_srp_set.setEnabled(true);
							}
						}
					}.start();
				}
			});

		param_srp_enable.addListener(
				new ParameterListener() {
					public void parameterChanged(
						Parameter 	param ) {
						boolean active = param_srp_enable.getValue();

						tunnel_handler.setActive(active);

						updateSRPState();
					}
				});

		param_srp_enable.addEnabledOnSelection(param_srp_state);
		param_srp_enable.addEnabledOnSelection(param_srp_set);

		configModel.createGroup(
				"pairing.group.srp",
				new Parameter[]{
					param_srp_info,
					param_srp_link,
					param_srp_enable,
					param_srp_state,
					param_srp_set,
				});

			// optional

		param_net_enable = configModel.addBooleanParameter2("pairing.nets.enable", "pairing.nets.enable", false);

		configModel.createGroup(
				"pairing.group.optional",
				new Parameter[]{
						param_net_enable });

			// explicit

		LabelParameter	param_e_info = configModel.addLabelParameter2("pairing.explicit.info");

		param_e_enable = configModel.addBooleanParameter2("pairing.explicit.enable", "pairing.explicit.enable", false);

		param_public_ipv4	= configModel.addStringParameter2("pairing.ipv4", "pairing.ipv4", "");
		param_public_ipv6	= configModel.addStringParameter2("pairing.ipv6", "pairing.ipv6", "");
		param_host			= configModel.addStringParameter2("pairing.host", "pairing.host", "");

		LabelParameter spacer = configModel.addLabelParameter2("blank.resource");

		param_local_ipv4	= configModel.addStringParameter2("pairing.local.ipv4", "pairing.local.ipv4", "");
		param_local_ipv6	= configModel.addStringParameter2("pairing.local.ipv6", "pairing.local.ipv6", "");


		param_public_ipv4.setGenerateIntermediateEvents(false);
		param_public_ipv6.setGenerateIntermediateEvents(false);
		param_host.setGenerateIntermediateEvents(false);

		ParameterListener change_listener =
			new ParameterListener() {
				public void parameterChanged(
					Parameter param) {
					updateNeeded();

					if (param == param_enable) {

						fireChanged();
					}
				}
			};

		param_enable.addListener(change_listener);
		param_e_enable.addListener(	change_listener);
		param_public_ipv4.addListener(	change_listener);
		param_public_ipv6.addListener(	change_listener);
		param_local_ipv4.addListener(	change_listener);
		param_local_ipv6.addListener(	change_listener);
		param_host.addListener(	change_listener);
		param_net_enable.addListener(	change_listener);

		param_e_enable.addEnabledOnSelection(param_public_ipv4);
		param_e_enable.addEnabledOnSelection(param_public_ipv6);
		param_e_enable.addEnabledOnSelection(param_local_ipv4);
		param_e_enable.addEnabledOnSelection(param_local_ipv6);
		param_e_enable.addEnabledOnSelection(param_host);

		configModel.createGroup(
			"pairing.group.explicit",
			new Parameter[]{
				param_e_info,
				param_e_enable,
				param_public_ipv4,
				param_public_ipv6,
				param_host,
				spacer,
				param_local_ipv4,
				param_local_ipv6,
			});

		param_icon_enable = configModel.addBooleanParameter2("pairing.config.icon.show", "pairing.config.icon.show", true);

		AzureusCoreFactory.addCoreRunningListener(
			new AzureusCoreRunningListener() {
				public void azureusCoreRunning(
					AzureusCore core) {
					initialise(core);
				}
			});
	}


	protected void initialise(
		AzureusCore		_core) {
		synchronized(this) {

			azureus_core	= _core;
		}

		try {
			tunnel_handler = new PairingManagerTunnelHandler(this, azureus_core);

			PluginInterface default_pi = PluginInitializer.getDefaultInterface();

			DelayedTask dt = default_pi.getUtilities().createDelayedTask(
				new Runnable() {
					public void run() {
						new DelayedEvent(
							"PM:delayinit",
							10*1000,
							new AERunnable() {
								public void runSupport() {
									enableUpdates();
								}
							});
					}
				});

			dt.queue();

			if (ui != null) {

				try {
					ui.initialise(default_pi, param_icon_enable);

				} catch (Throwable e) {
					// ignore swt errors console UI users get here
				}
			}
		} finally {

			init_sem.releaseForever();

			updateSRPState();
		}
	}

	protected void waitForInitialisation()

		throws PairingException
	{
		if (!init_sem.reserve( 30*1000)) {

			throw (new PairingException("Timeout waiting for initialisation"));
		}
	}

	public boolean isEnabled() {
		return ( param_enable.getValue());
	}

	public void setEnabled(
		boolean	enabled) {
		param_enable.setValue(enabled);
	}

	public boolean isSRPEnabled() {
		return ( param_srp_enable.getValue());
	}

	public void setSRPEnabled(
		boolean	enabled) {
		param_srp_enable.setValue(enabled);
	}

	public void setGroup(
		String group ) {
		COConfigurationManager.setParameter("pairing.groupcode", group);

		updateNeeded();
	}

	public String getGroup() {
		return (COConfigurationManager.getStringParameter("pairing.groupcode", null));
	}

	public List<PairedNode>
	listGroup()

		throws PairingException
	{
		try {
			URL url = new URL( SERVICE_URL + "/remote/listGroup?gc=" + getGroup());

			InputStream is =  new ResourceDownloaderFactoryImpl().create(url).download();

			Map json = JSONUtils.decodeJSON(new String( FileUtil.readInputStreamAsByteArray( is ), "UTF-8"));

			List<Map>	list = (List<Map>)json.get("result");

			List<PairedNode>	result = new ArrayList<PairedNode>();

			String my_ac = peekAccessCode();

			if (list != null) {

				for (Map m: list) {

					PairedNodeImpl node = new PairedNodeImpl(m);

					if (my_ac == null || !my_ac.equals( node.getAccessCode())) {

						result.add(node);
					}
				}
			}

			return (result);

		} catch (Throwable e) {

			throw (new PairingException("Failed to list group", e));
		}
	}

	public List<PairedService>
	lookupServices(
		String		access_code )

		throws PairingException
	{
		try {
			URL url = new URL(SERVICE_URL + "/remote/listBindings?ac=" + access_code + "&jsoncallback=");

			InputStream is =  new ResourceDownloaderFactoryImpl().create(url).download();

			String reply = new String(FileUtil.readInputStreamAsByteArray( is ), "UTF-8");

				// hack to remove callback

			reply = reply.substring(1, reply.length()-1);

			Map json = JSONUtils.decodeJSON(reply);

			Map error = (Map)json.get("error");

			if (error != null) {

				throw (new PairingException((String)error.get("msg")));
			}

			List<Map>	list = (List<Map>)json.get("result");

			List<PairedService>	result = new ArrayList<PairedService>();

			if (list != null) {

				for (Map m: list) {

					result.add(new PairedService2Impl((String) m.get("sid"), m));
				}
			}

			return (result);

		} catch (Throwable e) {

			throw (new PairingException("Failed to lookup services", e));
		}
	}

	protected void setStatus(
		String		str) {
		String last_status = param_status_info.getValue();

		if (!last_status.equals( str)) {

			param_status_info.setValue(str);

			fireChanged();
		}
	}

	public String getStatus() {
		return ( param_status_info.getValue());
	}

	public String getSRPStatus() {
		if (!isSRPEnabled()) {

			return ("Not enabled");

		} else if (tunnel_handler == null) {

			return ("Initialising");
		} else {

			return ( tunnel_handler.getStatus());
		}
	}

	protected void setLastServerError(
		String	error) {
		String last_error = param_last_error.getValue();

		if (error == null) {

			error = "";
		}

		if (!last_error.equals( error)) {

			param_last_error.setValue(error);

			if (error.contains("generate a new one")) {

				Logger.log(
					new LogAlert(
						true,
						LogAlert.AT_WARNING,
						"The pairing access code is invalid.\n\nCreate a new one via Tools->Options->Connection->Pairing or disable the pairing feature." ));
			}

			fireChanged();
		}
	}

	public String getLastServerError() {
		String last_error = param_last_error.getValue();

		if (last_error.length() == 0) {

			last_error = null;
		}

		return (last_error);
	}

	public boolean hasActionOutstanding() {
		synchronized(this) {

			if (!isEnabled()) {

				return (false);
			}

			return (!updates_enabled || update_outstanding || deferred_update_event != null || update_in_progress);
		}
	}

	protected String readAccessCode() {
		return (COConfigurationManager.getStringParameter("pairing.accesscode", ""));
	}

	protected void writeAccessCode(
		String		ac) {
		COConfigurationManager.setParameter("pairing.accesscode", ac);

			// try not to loose this!

		COConfigurationManager.save();

		param_ac_info.setValue(ac);

		param_view.setHyperlink(SERVICE_URL + "/web/view?ac=" + ac);

		param_view.setEnabled(ac.length() > 0);
	}

	private File
	receiveQR(
		String					ac,
		Map<String,Object>		response) {
		try {
			byte[]	bytes 	= (byte[])response.get("qr_b");

			if (bytes == null) {

				return (null);
			}

			long	ver		= (Long)response.get("qr_v");

			File cache_dir = new File(SystemProperties.getUserPath(), "cache");

			File qr_file = new File(cache_dir, "qr_" + ac + "_" + ver + ".png");

			if (FileUtil.writeBytesAsFile2( qr_file.getAbsolutePath(), bytes)) {

				return (qr_file);
			}

			return (null);

		} catch (Throwable e) {

			Debug.out(e);

			return (null);
		}
	}

	public File
	getQRCode() {
			//check current qr version against cached file if it exists

		String existing = readAccessCode();

		if (existing == null) {

			return (null);
		}

		if (qr_version > 0) {

			File cache_dir = new File(SystemProperties.getUserPath(), "cache");

			File qr_file = new File(cache_dir, "qr_" + existing + "_" + qr_version + ".png");

			if (qr_file.exists()) {

				return (qr_file);
			}
		}

		Map<String,Object>	request = new HashMap<String, Object>();

		request.put("ac", existing);

		try {

			Map<String,Object> response = sendRequest("get_qr", request);

			return (receiveQR( existing, response));

		} catch (Throwable e) {

			Debug.out(e);

			return (null);
		}
	}

	protected String allocateAccessCode(
		boolean		updating )

		throws PairingException
	{
		Map<String,Object>	request = new HashMap<String, Object>();

		String existing = readAccessCode();

		request.put("ac", existing);
		request.put("qr", 1L);

		Map<String,Object> response = sendRequest("allocate", request);

		try {
			String code = getString(response, "ac");

			receiveQR(code, response);

			writeAccessCode(code);

			if (!updating) {

				updateNeeded();
			}

			fireChanged();

			return (code);

		} catch (Throwable e) {

			throw (new PairingException("allocation failed", e));
		}
	}

	public String peekAccessCode() {
		return ( readAccessCode());
	}

	public String getAccessCode()

		throws PairingException
	{
		waitForInitialisation();

		String ac = readAccessCode();

		if (ac == null || ac.length() == 0) {

			ac = allocateAccessCode(false);
		}

		return (ac);
	}

	public void getAccessCode(
		final PairingManagerListener 	listener )

		throws PairingException
	{
		new AEThread2("PM:gac", true) {
			public void run() {
				try {
					getAccessCode();

				} catch (Throwable e) {

				} finally {

					listener.somethingChanged(PairingManagerImpl.this);
				}
			}
		}.start();
	}

	public String getReplacementAccessCode()

		throws PairingException
	{
		waitForInitialisation();

		String new_code = allocateAccessCode(false);

		return (new_code);
	}

	public PairedService
	addService(
		String							sid,
		PairedServiceRequestHandler		handler) {
		synchronized(this) {

			PairedServiceImpl	result = services.get(sid);

			if (result == null) {

				if (DEBUG) {
					System.out.println("PS: added " + sid);
				}

				result = new PairedServiceImpl(sid, handler);

				services.put(sid, result);

			} else {
				result.setHandler(handler);
			}

			return (result);
		}
	}

	public PairedServiceImpl
	getService(
		String		sid) {
		synchronized(this) {

			PairedServiceImpl	result = services.get(sid);

			return (result);
		}
	}

	protected void remove(
		PairedServiceImpl	service) {
		synchronized(this) {

			String sid = service.getSID();

			if (services.remove(sid) != null) {

				if (DEBUG) {
					System.out.println("PS: removed " + sid);
				}
			}
		}

		updateNeeded();
	}

	protected void sync(
		PairedServiceImpl	service) {
		updateNeeded();
	}

	protected InetAddress
	updateAddress(
		InetAddress		current,
		InetAddress		latest,
		boolean			v6) {
		if (v6) {

			if (latest instanceof Inet4Address) {

				return (current);
			}
		} else {

			if (latest instanceof Inet6Address) {

				return (current);
			}
		}

		if (current == latest) {

			return (current);
		}

		if (current == null || latest == null) {

			return (latest);
		}

		if (!current.equals( latest)) {

			return (latest);
		}

		return (current);
	}

	protected void updateGlobals(
		boolean	is_updating ) {
		final long now = SystemTime.getMonotonousTime();

		NetworkAdmin network_admin = NetworkAdmin.getSingleton();

		InetAddress latest_v4 = azureus_core.getInstanceManager().getMyInstance().getExternalAddress();

		synchronized(this) {

			InetAddress temp_v4 = updateAddress(current_v4, latest_v4, false);

			InetAddress latest_v6 = network_admin.getDefaultPublicAddressV6();

			InetAddress temp_v6 = updateAddress(current_v6, latest_v6, true);

			final TreeSet<String>	latest_v4_locals = new TreeSet<String>();
			final TreeSet<String>	latest_v6_locals = new TreeSet<String>();

			NetworkAdminNetworkInterface[] interfaces = network_admin.getInterfaces();

			List<Runnable>	to_do = new ArrayList<Runnable>();

			Set<String> existing_checked;

			synchronized(local_address_checks) {

				existing_checked = new HashSet<String>( local_address_checks.keySet());
			}

			for (NetworkAdminNetworkInterface intf: interfaces) {

				NetworkAdminNetworkInterfaceAddress[] addresses = intf.getAddresses();

				for (NetworkAdminNetworkInterfaceAddress address: addresses) {

					final InetAddress ia = address.getAddress();

					if (ia.isLoopbackAddress()) {

						continue;
					}

					if (ia.isLinkLocalAddress() || ia.isSiteLocalAddress()) {

						final String a_str = ia.getHostAddress();

						existing_checked.remove(a_str);

						Object[] check;

						synchronized(local_address_checks) {

							check = local_address_checks.get(a_str);
						}

						boolean run_check = check == null || now - ((Long)check[0]) > CONNECT_TEST_PERIOD_MILLIS;

						if (run_check) {

							to_do.add(
								new Runnable() {
									public void run() {
										Socket socket = new Socket();

										String	result = a_str;

										try {
											socket.bind(new InetSocketAddress( ia, 0));

											socket.connect(new InetSocketAddress("www.google.com", 80 ), 10*1000);

											result += "*";

										} catch (Throwable e) {

										} finally {
											try {
												socket.close();
											} catch (Throwable e) {
											}

										}

										synchronized(local_address_checks) {

											local_address_checks.put( a_str, new Object[]{ new Long(now), result });

											if (ia instanceof Inet4Address) {

												latest_v4_locals.add(result);

											} else {

												latest_v6_locals.add(result);
											}
										}
									}
								});

						} else {

							if (ia instanceof Inet4Address) {

								latest_v4_locals.add((String)check[1]);

							} else {

								latest_v6_locals.add((String)check[1]);
							}
						}
					}
				}
			}

			if (to_do.size() > 0) {

				final AESemaphore	sem = new AESemaphore("PM:check");

				for (final Runnable r: to_do) {

					new AEThread2("PM:check:", true) {
						public void run() {
							try {
								r.run();

							} finally {

								sem.release();
							}
						}
					}.start();
				}

				for (int i=0;i<to_do.size();i++) {

					sem.reserve();
				}
			}

			synchronized(local_address_checks) {

				for (String excess: existing_checked) {

					local_address_checks.remove(excess);
				}
			}

			String v4_locals_str = getString(latest_v4_locals);
			String v6_locals_str = getString(latest_v6_locals);


			if (	temp_v4 != current_v4 ||
					temp_v6 != current_v6 ||
					!v4_locals_str.equals(local_v4) ||
					!v6_locals_str.equals(local_v6)) {

				current_v4	= temp_v4;
				current_v6	= temp_v6;
				local_v4	= v4_locals_str;
				local_v6	= v6_locals_str;

				if (!is_updating) {

					updateNeeded();
				}
			}

		}
	}

	protected String getString(
		Set<String>	set) {
		String	str = "";

		for (String s: set) {

			str += (str.length()==0?"":",") + s;
		}

		return (str);
	}

	protected void enableUpdates() {
		synchronized(this) {

			updates_enabled = true;

			if (update_outstanding) {

				update_outstanding = false;

				updateNeeded();
			}
		}
	}

	protected void updateNeeded() {
		if (DEBUG) {
			System.out.println("PS: updateNeeded");
		}

		synchronized(this) {

			if (updates_enabled) {

				dispatcher.dispatch(
					new AERunnable() {
						public void runSupport() {
							doUpdate();
						}
					});


			} else {

				setStatus(MessageText.getString("pairing.status.initialising"));

				update_outstanding	= true;
			}
		}
	}

	protected void doUpdate() {
		long	now = SystemTime.getMonotonousTime();

		synchronized(this) {

			if (deferred_update_event != null) {

				return;
			}

			long	time_since_last_update = now - last_update_time;

			if (last_update_time > 0 &&  time_since_last_update < min_update_period) {

				deferUpdate( min_update_period - time_since_last_update);

				return;
			}

			update_in_progress = true;
		}

		try {
			Map<String,Object>	payload = new HashMap<String, Object>();

			boolean	is_enabled = param_enable.getValue();

			boolean	has_services = false;

			synchronized(this) {

				List<Map<String,String>>	list =  new ArrayList<Map<String,String>>();

				payload.put("s", list);

				if (services.size() > 0 && is_enabled) {

					if (global_update_event == null) {

						global_update_event =
							SimpleTimer.addPeriodicEvent(
							"PM:updater",
							GLOBAL_UPDATE_PERIOD,
							new TimerEventPerformer() {
								private int	tick_count;

								public void perform(
									TimerEvent event ) {
									tick_count++;

									updateGlobals(false);

									if (tick_count % CD_REFRESH_TICKS == 0) {

										updateNeeded();
									}
								}
							});

						updateGlobals(true);
					}

					boolean	enable_nets = param_net_enable.getValue();

					for ( PairedServiceImpl service: services.values()) {

						list.add(service.toMap( enable_nets));
					}

					has_services = list.size() > 0;

				} else {

						// when we get to zero services we want to push through the
						// last update to remove cd

					if (global_update_event == null) {

						if (consec_update_fails == 0 && !must_update_once) {

							update_in_progress = false;

							setStatus(MessageText.getString( is_enabled?"pairing.status.noservices":"pairing.status.disabled"));

							return;
						}
					} else {

						global_update_event.cancel();

						global_update_event = null;
					}
				}

				last_update_time = now;
			}

				// we need a valid access code here!

			String ac = readAccessCode();

			if (ac.length() == 0) {

				ac = allocateAccessCode(true);
			}

			payload.put("ac", ac);

			String	gc = getGroup();

			if (gc != null && gc.length() > 0) {

				payload.put("gc", gc);
			}

			if (is_enabled && has_services && param_srp_enable.getValue()) {

				tunnel_handler.setActive(true);

				tunnel_handler.updateRegistrationData(payload);

			} else {

				tunnel_handler.setActive(false);
			}

			synchronized(this) {

				if (current_v4 != null) {

					payload.put("c_v4", current_v4.getHostAddress());
				}

				if (current_v6 != null) {

					payload.put("c_v6", current_v6.getHostAddress());
				}

				if (local_v4.length() > 0) {

					payload.put("l_v4", local_v4);
				}

				if (local_v6.length() > 0) {

					payload.put("l_v6", local_v6);
				}

				if (param_e_enable.getValue()) {

					String host = param_host.getValue().trim();

					if (host.length() > 0) {

						payload.put("e_h", host);
					}

					String v4 = param_public_ipv4.getValue().trim();

					if (v4.length() > 0) {

						payload.put("e_v4", v4);
					}

					String v6 = param_public_ipv6.getValue().trim();

					if (v6.length() > 0) {

						payload.put("e_v6", v6);
					}

					String l_v4 = param_local_ipv4.getValue().trim();

					if (l_v4.length() > 0) {

						payload.put("e_l_v4", l_v4);
					}

					String l_v6 = param_local_ipv6.getValue().trim();

					if (l_v6.length() > 0) {

						payload.put("e_l_v6", l_v6);
					}
				}

					// grab some UPnP info for diagnostics

				try {
				    PluginInterface pi_upnp = azureus_core.getPluginManager().getPluginInterfaceByClass(UPnPPlugin.class);

				    if (pi_upnp != null) {

				        UPnPPlugin upnp = (UPnPPlugin)pi_upnp.getPlugin();

				        if (upnp.isEnabled()) {

				        	List<Map<String,String>>	upnp_list = new ArrayList<Map<String,String>>();

				        	payload.put("upnp", upnp_list);

				        	UPnPPluginService[] services = upnp.getServices();

				        	Set<UPnPRootDevice> devices = new HashSet<UPnPRootDevice>();

				        	for (UPnPPluginService service: services) {

				        			// some users get silly numbers of services :(

				        		if (upnp_list.size() > 10) {

				        			break;
				        		}

				        		UPnPRootDevice root_device = service.getService().getGenericService().getDevice().getRootDevice();

				        		if (!devices.contains( root_device)) {

				        			devices.add(root_device);

					        		Map<String,String>	map = new HashMap<String, String>();

					        		upnp_list.add(map);

					        		map.put("i", root_device.getInfo());
				        		}
				        	}
				        }
				    }
				} catch (Throwable e) {
				}

				try {
					NetworkAdmin admin = NetworkAdmin.getSingleton();

					NetworkAdminHTTPProxy http_proxy = admin.getHTTPProxy();

					if (http_proxy != null) {

						payload.put("hp", http_proxy.getName());
					}

					NetworkAdminSocksProxy[] socks_proxies = admin.getSocksProxies();

					if (socks_proxies.length > 0) {

						payload.put("sp", socks_proxies[0].getName());
					}
				} catch (Throwable e) {
				}

				payload.put("_enabled", is_enabled?1L:0L);
			}

			if (DEBUG) {
				System.out.println("PS: doUpdate: " + payload);
			}

			sendRequest("update", payload);

			synchronized(this) {

				consec_update_fails	= 0;

				must_update_once = false;

				if (deferred_update_event == null) {

					COConfigurationManager.setParameter("pairing.updateoutstanding", false);
				}

				update_in_progress = false;

				if (global_update_event == null) {

					setStatus(MessageText.getString( is_enabled?"pairing.status.noservices":"pairing.status.disabled"));

				} else {

					setStatus(
						MessageText.getString(
							"pairing.status.registered",
							new String[]{ new SimpleDateFormat().format(new Date(SystemTime.getCurrentTime()))}));
				}
			}
		} catch (Throwable e) {

			synchronized(this) {

				try {
					consec_update_fails++;

					long back_off = min_update_period;

					for (int i=0;i<consec_update_fails;i++) {

						back_off *= 2;

						if (back_off > max_update_period) {

							back_off = max_update_period;

							break;
						}
					}

					deferUpdate(back_off);

				} finally {

					update_in_progress = false;
				}
			}
		} finally {

			synchronized(this) {

				if (update_in_progress) {

					Debug.out("Something didn't clear update_in_progress!!!!");

					update_in_progress = false;
				}
			}
		}
	}

	protected void deferUpdate(
		long	millis) {
		millis += 5000;

		long target = SystemTime.getOffsetTime(millis);

		deferred_update_event =
			SimpleTimer.addEvent(
				"PM:defer",
				target,
				new TimerEventPerformer() {
					public void perform(
						TimerEvent event) {
						synchronized(PairingManagerImpl.this) {

							deferred_update_event = null;
						}

						COConfigurationManager.setParameter("pairing.updateoutstanding", false);

						updateNeeded();
					}
				});

		setStatus(
				MessageText.getString(
					"pairing.status.pending",
					new String[]{ new SimpleDateFormat().format(new Date(target))}));

		COConfigurationManager.setParameter("pairing.updateoutstanding", true);
	}


	private Map<String, Object>
	sendRequest(
		String 				command,
		Map<String, Object> payload )

		throws PairingException
	{
		try {
			Map<String, Object> request = new HashMap<String, Object>();

			CryptoManager cman = CryptoManagerFactory.getSingleton();

			String azid = Base32.encode( cman.getSecureID());

			payload.put("_azid", azid);

			try {
				String pk = Base32.encode(cman.getECCHandler().getPublicKey("pairing"));

				payload.put("_pk", pk);

			} catch (Throwable e) {
			}

			request.put("req", payload);

			String request_str = Base32.encode(BEncoder.encode( request));

			String	sig = null;

			try {
				sig = Base32.encode(cman.getECCHandler().sign( request_str.getBytes("UTF-8" ), "pairing"));

			} catch (Throwable e) {
			}

			String other_params =
				"&ver=" + UrlUtils.encode(Constants.AZUREUS_VERSION) +
				"&app=" + UrlUtils.encode( SystemProperties.getApplicationName()) +
				"&locale=" + UrlUtils.encode( MessageText.getCurrentLocale().toString());

			if (sig != null) {

				other_params += "&sig=" + sig;
			}

			URL target = new URL(SERVICE_URL + "/client/" + command + "?request=" + request_str + other_params);

			Properties	http_properties = new Properties();

			http_properties.put(ClientIDGenerator.PR_URL, target);

			try {
				ClientIDManagerImpl.getSingleton().generateHTTPProperties(null, http_properties);

			} catch (ClientIDException e) {

				throw (new IOException( e.getMessage()));
			}

			target = (URL)http_properties.get(ClientIDGenerator.PR_URL);

			HttpURLConnection connection = (HttpURLConnection)target.openConnection();

			connection.setConnectTimeout(30*1000);

			InputStream is = connection.getInputStream();

			Map<String,Object> response = (Map<String,Object>)BDecoder.decode(new BufferedInputStream( is));

			synchronized(this) {

				Long	min_retry = (Long)response.get("min_secs");

				if (min_retry != null) {

					min_update_period	= min_retry.intValue()*1000;
				}

				Long	max_retry = (Long)response.get("max_secs");

				if (max_retry != null) {

					max_update_period	= max_retry.intValue()*1000;
				}
			}

			final String message = getString(response, "message");

			if (message != null) {

				if (last_message == null || !last_message.equals( message)) {

					last_message = message;

					try {
						byte[] message_sig = (byte[])response.get("message_sig");

						AEVerifier.verifyData(message, message_sig);

						new AEThread2("PairMsg", true) {
							public void run() {
								UIManager ui_manager = StaticUtilities.getUIManager(120*1000);

								if (ui_manager != null) {

									ui_manager.showMessageBox(
											"pairing.server.warning.title",
											"!" + message + "!",
											UIManagerEvent.MT_OK);
								}
							}
						}.start();

					} catch (Throwable e) {
					}
				}
			}

			String error = getString(response, "error");

			if (error != null) {

				throw (new PairingException( error));
			}

			setLastServerError(null);

			Map<String,Object> reply = (Map<String,Object>)response.get("rep");

			Long qr_v = (Long)reply.get("qr_v");

			if (qr_v != null) {

				if (qr_version != qr_v.longValue()) {

					qr_version = qr_v;

					COConfigurationManager.setParameter("pairing.qr.ver", qr_version);
				}
			}
			return (reply);

		} catch (Throwable e) {

			setLastServerError(Debug.getNestedExceptionMessage( e));

			if (e instanceof PairingException) {

				throw ((PairingException)e);
			}

			throw (new PairingException("invocation failed", e));
		}
	}


	public PairingTest
	testService(
		String 					sid,
		PairingTestListener 	listener )

		throws PairingException
	{
		return (new TestServiceImpl( sid, listener));
	}

	protected void updateSRPState() {
		String	text;

		if (param_srp_enable.getValue()) {

			if (tunnel_handler == null) {

				text = MessageText.getString("pairing.status.initialising") + "...";

			} else {

				text = tunnel_handler.getStatus();
			}
		} else {

			text = MessageText.getString("MyTorrentsView.menu.setSpeed.disabled");
		}

		param_srp_state.setLabelText( MessageText.getString("pairing.srp.state", new String[]{ text }));
	}

	public void setSRPPassword(
		char[]		password) {
		init_sem.reserve();

		tunnel_handler.setSRPPassword(password);
	}

	public boolean handleLocalTunnel(
		TrackerWebPageRequest		request,
		TrackerWebPageResponse		response )

		throws IOException
	{
		init_sem.reserve();

		return (tunnel_handler.handleLocalTunnel( request, response));
	}

	public void recordRequest(
		String		name,
		String		ip,
		boolean		good) {
		synchronized(this) {

				// don't record any incoming stuff during a test as we don't want to
				// show this as a significant event

			if (tests_in_progress > 0) {

				return;
			}
		}

		if (ui != null) {

			try {
				ui.recordRequest(name, ip, good);

			} catch (Throwable e) {
				// ignore swt errors console UI users get here
			}
		}
	}

	protected void fireChanged() {
		for (PairingManagerListener l: listeners) {

			try {
				l.somethingChanged(this);

			} catch (Throwable e) {

				Debug.out(e);
			}
		}
	}

	public void addListener(
		PairingManagerListener		l) {
		listeners.add(l);
	}

	public void removeListener(
		PairingManagerListener		l) {
		listeners.remove(l);
	}

	protected String getString(
		Map<String,Object>	map,
		String				name )

		throws IOException
	{
		byte[]	bytes = (byte[])map.get(name);

		if (bytes == null) {

			return (null);
		}

		return (new String( bytes, "UTF-8"));
	}

	public void generate(
		IndentWriter writer) {
		writer.println("Pairing Manager");

		try {
			writer.indent();

			if (tunnel_handler != null) {

				tunnel_handler.generateEvidence(writer);
			}
		} finally {

			writer.exdent();
		}
	}

	protected class
	TestServiceImpl
		implements PairingTest
	{
		final private String					sid;
		final private PairingTestListener		listener;

		private volatile int		outcome = OT_PENDING;
		private volatile String		error_message;

		private volatile boolean	cancelled;

		protected TestServiceImpl(
			String 					_sid,
			PairingTestListener 	_listener) {
			sid			= _sid;
			listener	= _listener;

			new AEThread2("PM:test") {
				public void run() {
					try {
						String	access_code		= null;
						long	sid_wait_start	= -1;

						while (true) {

							if (!isEnabled()) {

								throw (new Exception("Pairing is disabled"));
							}

							access_code = peekAccessCode();

							if (access_code != null) {

								if (!hasActionOutstanding()) {

									if (getService(sid) != null) {

										break;

									} else {

										long	now = SystemTime.getMonotonousTime();

										if (sid_wait_start == -1) {

											sid_wait_start = now;

										} else {

											if (now - sid_wait_start > 5000) {

												break;
											}
										}
									}
								}
							}

							Thread.sleep(500);

							if (cancelled) {

								outcome = OT_CANCELLED;

								return;
							}
						}

						PairedService service = getService(sid);

						if (service == null) {

							throw (new Exception("Service not found"));
						}

						listener.testStarted(TestServiceImpl.this);

						String other_params =
							"&ver=" + UrlUtils.encode(Constants.AZUREUS_VERSION) +
							"&app=" + UrlUtils.encode( SystemProperties.getApplicationName()) +
							"&locale=" + UrlUtils.encode( MessageText.getCurrentLocale().toString());

						URL target = new URL(SERVICE_URL + "/web/test?sid=" + sid + "&ac=" + access_code + "&format=bencode" + other_params);

						HttpURLConnection connection = (HttpURLConnection)target.openConnection();

						connection.setConnectTimeout(10*1000);

						try {
							synchronized(PairingManagerImpl.this) {

								tests_in_progress++;
							}

							InputStream is = connection.getInputStream();

							Map<String,Object> response = (Map<String,Object>)BDecoder.decode(new BufferedInputStream( is));

							response = BDecoder.decodeStrings(response);

							Long code = (Long)response.get("code");

							if (code == null) {

								throw (new Exception("Code missing from reply"));
							}

							error_message = (String)response.get("msg");

							if (code == 1) {

								outcome = OT_SUCCESS;

							} else if (code == 2) {

								outcome = OT_SERVER_OVERLOADED;

							} else if (code == 3) {

								outcome = OT_SERVER_FAILED;

							} else if (code == 4) {

								outcome = OT_FAILED;

								error_message = "Connect timeout";

							} else if (code == 5) {

								outcome = OT_FAILED;

							} else {

								outcome = OT_SERVER_FAILED;;

								error_message = "Unknown response code " + code;
							}
						} catch (SocketTimeoutException e) {

							outcome = OT_SERVER_UNAVAILABLE;

							error_message = "Connect timeout";

						}
					} catch (Throwable e) {

						outcome = OT_SERVER_UNAVAILABLE;

						error_message = Debug.getNestedExceptionMessage(e);

					} finally {

						try {
							listener.testComplete(TestServiceImpl.this);

						} finally {

							synchronized(PairingManagerImpl.this) {

								tests_in_progress--;
							}
						}
					}
				}
			}.start();
		}

		public int getOutcome() {
			return (outcome);
		}

		public String getErrorMessage() {
			return (error_message);
		}

		public void cancel() {
			cancelled	= true;
		}
	}

	protected class
	PairedServiceImpl
		implements PairedService, PairingConnectionData
	{
		private final String				sid;
		private final Map<String,String>	attributes	= new HashMap<String, String>();

		private	PairedServiceRequestHandler		request_handler;

		protected PairedServiceImpl(
			String							_sid,
			PairedServiceRequestHandler		_request_handler) {
			sid				= _sid;
			request_handler	= _request_handler;
		}

		public String getSID() {
			return (sid);
		}

		protected void setHandler(
			PairedServiceRequestHandler		_h) {
			request_handler	= _h;
		}

		protected PairedServiceRequestHandler
		getHandler() {
			return (request_handler);
		}

		public PairingConnectionData
		getConnectionData() {
			return (this);
		}

		public void remove() {
			PairingManagerImpl.this.remove(this);
		}

		public void setAttribute(
			String		name,
			String		value) {
			synchronized(this) {

				if (DEBUG) {
					System.out.println("PS: " + sid + ": " + name + " -> " + value);
				}

				if  (value == null) {

					attributes.remove(name);

				} else {

					attributes.put(name, value);
				}
			}
		}

		public String getAttribute(
			String		name) {
			synchronized(this) {

				return (attributes.get( name));
			}
		}

		public void sync() {
			PairingManagerImpl.this.sync(this);
		}

		protected Map<String,String>
		toMap(
			boolean		enable_nets) {
			Map<String,String> result = new HashMap<String, String>();

			result.put("sid", sid);

			synchronized(this) {

				result.putAll(attributes);
			}

			if (!enable_nets) {

				result.remove(PairingConnectionData.ATTR_I2P);

				result.remove(PairingConnectionData.ATTR_TOR);
			}

			return (result);
		}
	}

	private class
	PairedNodeImpl
		implements PairedNode
	{
		private final Map		map;

		protected PairedNodeImpl(
			Map		_map) {
			map	= _map;
		}

		public String getAccessCode() {
			return ((String)map.get("ac"));
		}

		public List<InetAddress>
		getAddresses() {
			Set<InetAddress> addresses = new HashSet<InetAddress>();

			addAddress(addresses, "c_v4");
			addAddress(addresses, "c_v6");
			addAddress(addresses, "l_v4");
			addAddress(addresses, "l_v6");
			addAddress(addresses, "e_v4");
			addAddress(addresses, "e_v6");
			addAddress(addresses, "e_l_v4");
			addAddress(addresses, "e_l_v6");
			addAddress(addresses, "e_h");

			return (new ArrayList<InetAddress>( addresses));
		}

		private void addAddress(
			Set<InetAddress>	addresses,
			String				key) {
			String str = (String)map.get(key);

			if (str != null) {

				String[] bits = str.split(",");

				for (String bit: bits) {

					bit = bit.trim();

					if (bit.length() == 0) {

						continue;
					}


					if (bit.endsWith("*")) {

						bit = bit.substring(0, bit.length()-1);
					}

					try {
						addresses.add(InetAddress.getByName( bit));

					} catch (Throwable e) {
					}
				}
			}
		}

		public List<PairedService>
		getServices() {
			Map<String,Map> smap = (Map)map.get("services");

			List<PairedService>	services = new ArrayList<PairedService>();

			for ( Map.Entry<String,Map> entry: smap.entrySet()) {

				services.add(new PairedService2Impl(entry.getKey(), entry.getValue()));
			}

			return (services);
		}
	}

	private static class
	PairedService2Impl
		implements PairedService
	{
		private final String		sid;
		private final Map			map;

		protected PairedService2Impl(
			String		_sid,
			Map			_map) {
			sid		= _sid;
			map		= _map;
		}

		public String getSID() {
			return (sid);
		}

		public PairingConnectionData
		getConnectionData() {
			return (new PairingConnectionData2( map));
		}

		public void remove() {
			throw (new RuntimeException("Not supported"));
		}
	}

	private static class
	PairingConnectionData2
		implements PairingConnectionData
	{
		private final Map		map;

		protected PairingConnectionData2(
			Map		_map) {
			map		= _map;
		}

		public void setAttribute(
			String		name,
			String		value) {
			throw (new RuntimeException("Not supported"));
		}

		public String getAttribute(
			String		name) {
			return ((String)map.get( name));
		}

		public void sync() {
			throw (new RuntimeException("Not supported"));
		}
	}

	public interface
	UIAdapter
	{
		public void initialise(
			PluginInterface			pi,
			BooleanParameter		icon_enable);

		public void recordRequest(
			final String		name,
			final String		ip,
			final boolean		good);

		public char[]
		getSRPPassword();
	}
}
