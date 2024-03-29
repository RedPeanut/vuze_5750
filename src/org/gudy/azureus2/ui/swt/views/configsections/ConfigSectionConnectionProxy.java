/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
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

package org.gudy.azureus2.ui.swt.views.configsections;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.util.AESemaphore;
import org.gudy.azureus2.core3.util.AEThread2;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.plugins.ui.config.ConfigSection;
import org.gudy.azureus2.ui.swt.Messages;
import org.gudy.azureus2.ui.swt.TextViewerWindow;
import org.gudy.azureus2.ui.swt.Utils;
import org.gudy.azureus2.ui.swt.config.*;
import org.gudy.azureus2.ui.swt.plugins.UISWTConfigSection;

import com.aelitis.azureus.core.networkmanager.admin.NetworkAdmin;
import com.aelitis.azureus.core.networkmanager.admin.NetworkAdminSocksProxy;

public class ConfigSectionConnectionProxy implements UISWTConfigSection {

	private final static String CFG_PREFIX = "ConfigView.section.proxy.";

	private final static int REQUIRED_MODE = 2;

	public int maxUserMode() {
		return REQUIRED_MODE;
	}


	public String configSectionGetParentSection() {
		return ConfigSection.SECTION_CONNECTION;
	}

	public String configSectionGetName() {
		return "proxy";
	}

	public void configSectionSave() {
	}

	public void configSectionDelete() {
	}

	public Composite configSectionCreate(final Composite parent) {
		GridData gridData;
		GridLayout layout;

		Composite cSection = new Composite(parent, SWT.NULL);

		gridData = new GridData(GridData.VERTICAL_ALIGN_FILL
				| GridData.HORIZONTAL_ALIGN_FILL);
		Utils.setLayoutData(cSection, gridData);
		layout = new GridLayout();
		layout.numColumns = 2;
		cSection.setLayout(layout);

		int userMode = COConfigurationManager.getIntParameter("User Mode");
		if (userMode < REQUIRED_MODE) {
			Label label = new Label(cSection, SWT.WRAP);
			gridData = new GridData();
			gridData.horizontalSpan = 2;
			Utils.setLayoutData(label, gridData);

			final String[] modeKeys = { "ConfigView.section.mode.beginner",
					"ConfigView.section.mode.intermediate",
					"ConfigView.section.mode.advanced" };

			String param1, param2;
			if (REQUIRED_MODE < modeKeys.length)
				param1 = MessageText.getString(modeKeys[REQUIRED_MODE]);
			else
				param1 = String.valueOf(REQUIRED_MODE);

			if (userMode < modeKeys.length)
				param2 = MessageText.getString(modeKeys[userMode]);
			else
				param2 = String.valueOf(userMode);

			label.setText(MessageText.getString("ConfigView.notAvailableForMode",
					new String[] { param1, param2 } ));

			return cSection;
		}

		//////////////////////  PROXY GROUP /////////////////

		Group gProxyTracker = new Group(cSection, SWT.NULL);
		Messages.setLanguageText(gProxyTracker, CFG_PREFIX + "group.tracker");
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.horizontalSpan = 2;
		Utils.setLayoutData(gProxyTracker, gridData);
		layout = new GridLayout();
		layout.numColumns = 2;
		gProxyTracker.setLayout(layout);

		final BooleanParameter enableProxy = new BooleanParameter(gProxyTracker,
				"Enable.Proxy", CFG_PREFIX + "enable_proxy");
		gridData = new GridData();
		gridData.horizontalSpan = 2;
		enableProxy.setLayoutData(gridData);

		final BooleanParameter enableSocks = new BooleanParameter(gProxyTracker,
				"Enable.SOCKS", CFG_PREFIX + "enable_socks");
		gridData = new GridData();
		gridData.horizontalSpan = 2;
		enableSocks.setLayoutData(gridData);

		Label lHost = new Label(gProxyTracker, SWT.NULL);
		Messages.setLanguageText(lHost, CFG_PREFIX + "host");
		final StringParameter pHost = new StringParameter(gProxyTracker, "Proxy.Host", "", false);
		gridData = new GridData();
		gridData.widthHint = 105;
		pHost.setLayoutData(gridData);

		Label lPort = new Label(gProxyTracker, SWT.NULL);
		Messages.setLanguageText(lPort, CFG_PREFIX + "port");
		final StringParameter pPort = new StringParameter(gProxyTracker, "Proxy.Port", "", false);
		gridData = new GridData();
		gridData.widthHint = 40;
		pPort.setLayoutData(gridData);

		Label lUser = new Label(gProxyTracker, SWT.NULL);
		Messages.setLanguageText(lUser, CFG_PREFIX + "username");
		final StringParameter pUser = new StringParameter(gProxyTracker, "Proxy.Username", false);
		gridData = new GridData();
		gridData.widthHint = 105;
		pUser.setLayoutData(gridData);

		Label lPass = new Label(gProxyTracker, SWT.NULL);
		Messages.setLanguageText(lPass, CFG_PREFIX + "password");
		final StringParameter pPass = new StringParameter(gProxyTracker, "Proxy.Password", "", false);
		gridData = new GridData();
		gridData.widthHint = 105;
		pPass.setLayoutData(gridData);

		final BooleanParameter trackerDNSKill = new BooleanParameter(gProxyTracker,
				"Proxy.SOCKS.Tracker.DNS.Disable", CFG_PREFIX + "no.local.dns");
		gridData = new GridData();
		gridData.horizontalSpan = 2;
		trackerDNSKill.setLayoutData(gridData);

		final NetworkAdminSocksProxy[]	test_proxy = { null };

		final Button test_socks = new Button(gProxyTracker, SWT.PUSH);
		Messages.setLanguageText(test_socks, CFG_PREFIX	+ "testsocks");

		test_socks.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event event) {

				final NetworkAdminSocksProxy target;

				synchronized(test_proxy) {

					target = test_proxy[0];
				}

				if (target != null) {

					final TextViewerWindow viewer = new TextViewerWindow(
							MessageText.getString(CFG_PREFIX	+ "testsocks.title"),
							null,
							"Testing SOCKS connection to " + target.getHost() + ":" + target.getPort(), false );

					final AESemaphore	test_done = new AESemaphore("");

					new AEThread2("SOCKS test") {
						public void run() {
							try {
								String[] vers = target.getVersionsSupported();

								String ver = "";

								for (String v: vers) {

									ver += (ver.length()==0?"":", ") + v;
								}

								appendText(viewer, "\r\nConnection OK - supported version(s): " + ver);


							} catch (Throwable e) {

								appendText(viewer, "\r\n" + Debug.getNestedExceptionMessage( e));

							} finally {

								test_done.release();
							}
						}
					}.start();

					new AEThread2("SOCKS test dotter") {
						public void run() {
							while (!test_done.reserveIfAvailable()) {

								appendText(viewer, ".");

								try {
									Thread.sleep(500);

								} catch (Throwable e) {

									break;
								}
							}
						}
					}.start();
				}
			}

			private void appendText(
				final TextViewerWindow	viewer,
				final String			line) {
				Utils.execSWTThread(
					new Runnable() {
						public void run() {
							if (!viewer.isDisposed()) {

								viewer.append2(line);
							}
						}
					});
			}
		});

		Parameter[] socks_params = { enableProxy, enableSocks, pHost, pPort, pUser, pPass, trackerDNSKill };

		ParameterChangeAdapter socks_adapter =
			new ParameterChangeAdapter() {
				public void parameterChanged(
					Parameter	p,
					boolean		caused_internally) {
					if (test_socks.isDisposed()) {

						p.removeChangeListener(this);

					} else {
						if (!caused_internally) {

							boolean 	enabled =
								enableProxy.isSelected() &&
								enableSocks.isSelected() &&
								pHost.getValue().trim().length() > 0 &&
								pPort.getValue().trim().length() > 0;

							boolean 	socks_enabled =
										enableProxy.isSelected() &&
										enableSocks.isSelected();

							trackerDNSKill.setEnabled(socks_enabled)
							;
							if (enabled) {

								try {
									int port = Integer.parseInt(pPort.getValue());

									NetworkAdminSocksProxy nasp =
										NetworkAdmin.getSingleton().createSocksProxy(
											pHost.getValue(), port, pUser.getValue(),pPass.getValue());

									synchronized(test_proxy) {

										test_proxy[0] = nasp;
									}
								} catch (Throwable e) {

									enabled = false;
								}
							}

							if (!enabled) {

								synchronized(test_proxy) {

									test_proxy[0] = null;
								}
							}

							final boolean f_enabled = enabled;

							Utils.execSWTThread(
								new Runnable() {
									public void run() {
										if (!test_socks.isDisposed()) {

											test_socks.setEnabled(f_enabled);
										}
									}
								});

						}
					}
				}
			};

		for (Parameter p: socks_params) {

			p.addChangeListener(socks_adapter);

		}

		socks_adapter.parameterChanged(null, false);	// init settings

		////////////////////////////////////////////////

		Group gProxyPeer = new Group(cSection, SWT.NULL);
		Messages.setLanguageText(gProxyPeer, CFG_PREFIX + "group.peer");
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.horizontalSpan = 2;
		Utils.setLayoutData(gProxyPeer, gridData);
		layout = new GridLayout();
		layout.numColumns = 2;
		gProxyPeer.setLayout(layout);

		final BooleanParameter enableSocksPeer = new BooleanParameter(gProxyPeer,
				"Proxy.Data.Enable", CFG_PREFIX + "enable_socks.peer");
		gridData = new GridData();
		gridData.horizontalSpan = 2;
		enableSocksPeer.setLayoutData(gridData);

		final BooleanParameter socksPeerInform = new BooleanParameter(gProxyPeer,
				"Proxy.Data.SOCKS.inform", CFG_PREFIX + "peer.informtracker");
		gridData = new GridData();
		gridData.horizontalSpan = 2;
		socksPeerInform.setLayoutData(gridData);

		Label lSocksVersion = new Label(gProxyPeer, SWT.NULL);
		Messages.setLanguageText(lSocksVersion, CFG_PREFIX + "socks.version");
		String[] socks_types = { "V4", "V4a", "V5" };
		String dropLabels[] = new String[socks_types.length];
		String dropValues[] = new String[socks_types.length];
		for (int i = 0; i < socks_types.length; i++) {
			dropLabels[i] = socks_types[i];
			dropValues[i] = socks_types[i];
		}
		final StringListParameter socksType = new StringListParameter(gProxyPeer,
				"Proxy.Data.SOCKS.version", "V4", dropLabels, dropValues);

		final BooleanParameter sameConfig = new BooleanParameter(gProxyPeer,
				"Proxy.Data.Same", CFG_PREFIX + "peer.same");
		gridData = new GridData();
		gridData.horizontalSpan = 2;
		sameConfig.setLayoutData(gridData);

		Label lDataHost = new Label(gProxyPeer, SWT.NULL);
		Messages.setLanguageText(lDataHost, CFG_PREFIX + "host");
		StringParameter pDataHost = new StringParameter(gProxyPeer,
				"Proxy.Data.Host", "");
		gridData = new GridData();
		gridData.widthHint = 105;
		pDataHost.setLayoutData(gridData);

		Label lDataPort = new Label(gProxyPeer, SWT.NULL);
		Messages.setLanguageText(lDataPort, CFG_PREFIX + "port");
		StringParameter pDataPort = new StringParameter(gProxyPeer,
				"Proxy.Data.Port", "");
		gridData = new GridData();
		gridData.widthHint = 40;
		pDataPort.setLayoutData(gridData);

		Label lDataUser = new Label(gProxyPeer, SWT.NULL);
		Messages.setLanguageText(lDataUser, CFG_PREFIX + "username");
		StringParameter pDataUser = new StringParameter(gProxyPeer,
				"Proxy.Data.Username");
		gridData = new GridData();
		gridData.widthHint = 105;
		pDataUser.setLayoutData(gridData);

		Label lDataPass = new Label(gProxyPeer, SWT.NULL);
		Messages.setLanguageText(lDataPass, CFG_PREFIX + "password");
		StringParameter pDataPass = new StringParameter(gProxyPeer,
				"Proxy.Data.Password", "");
		gridData = new GridData();
		gridData.widthHint = 105;
		pDataPass.setLayoutData(gridData);

		final Control[] proxy_controls = new Control[] { enableSocks.getControl(),
				lHost, pHost.getControl(), lPort, pPort.getControl(), lUser,
				pUser.getControl(), lPass, pPass.getControl() };

		IAdditionalActionPerformer proxy_enabler = new GenericActionPerformer(
				new Control[] {}) {
			public void performAction() {
				for (int i = 0; i < proxy_controls.length; i++) {

					proxy_controls[i].setEnabled(enableProxy.isSelected());
				}
			}
		};

		final Control[] proxy_peer_controls = new Control[] { lDataHost,
				pDataHost.getControl(), lDataPort, pDataPort.getControl(), lDataUser,
				pDataUser.getControl(), lDataPass, pDataPass.getControl() };

		final Control[] proxy_peer_details = new Control[] {
				sameConfig.getControl(), socksPeerInform.getControl(),
				socksType.getControl(), lSocksVersion };

		IAdditionalActionPerformer proxy_peer_enabler = new GenericActionPerformer(
				new Control[] {}) {
			public void performAction() {
				for (int i = 0; i < proxy_peer_controls.length; i++) {

					proxy_peer_controls[i].setEnabled(enableSocksPeer.isSelected()
							&& !sameConfig.isSelected());
				}

				for (int i = 0; i < proxy_peer_details.length; i++) {

					proxy_peer_details[i].setEnabled(enableSocksPeer.isSelected());
				}
			}
		};

		enableSocks.setAdditionalActionPerformer(proxy_enabler);
		enableProxy.setAdditionalActionPerformer(proxy_enabler);
		enableSocksPeer.setAdditionalActionPerformer(proxy_peer_enabler);
		sameConfig.setAdditionalActionPerformer(proxy_peer_enabler);


			// dns info

		Label label = new Label(cSection, SWT.WRAP);
		Messages.setLanguageText(label, CFG_PREFIX + "dns.info");
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.horizontalSpan = 2;
		gridData.widthHint = 200;  // needed for wrap
		Utils.setLayoutData(label, gridData);

			// disable plugin proxies

		final BooleanParameter disablepps = new BooleanParameter(cSection,
				"Proxy.SOCKS.disable.plugin.proxies", CFG_PREFIX + "disable.plugin.proxies");
		gridData = new GridData();
		gridData.horizontalSpan = 2;
		disablepps.setLayoutData(gridData);


			// check on start

		final BooleanParameter checkOnStart = new BooleanParameter(cSection,
				"Proxy.Check.On.Start", CFG_PREFIX + "check.on.start");
		gridData = new GridData();
		gridData.horizontalSpan = 2;
		checkOnStart.setLayoutData(gridData);

			// icon

		final BooleanParameter showIcon = new BooleanParameter(cSection,
				"Proxy.SOCKS.ShowIcon", CFG_PREFIX + "show_icon");
		gridData = new GridData();
		gridData.horizontalSpan = 2;
		showIcon.setLayoutData(gridData);

		final BooleanParameter flagIncoming = new BooleanParameter(cSection,
				"Proxy.SOCKS.ShowIcon.FlagIncoming", CFG_PREFIX + "show_icon.flag.incoming");
		gridData = new GridData();
		gridData.horizontalIndent=50;
		gridData.horizontalSpan = 2;
		flagIncoming.setLayoutData(gridData);

		showIcon.setAdditionalActionPerformer(
			new ChangeSelectionActionPerformer(flagIncoming));

			// username info

		label = new Label(cSection, SWT.WRAP);
		gridData = new GridData();
		gridData.horizontalSpan = 2;
		Utils.setLayoutData(label, gridData);
		label.setText(MessageText.getString(CFG_PREFIX+"username.info" ));

		return cSection;

	}
}
