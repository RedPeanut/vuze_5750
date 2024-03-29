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

import java.net.Socket;
import java.nio.channels.SocketChannel;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.config.impl.ConfigurationManager;
import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.util.Constants;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.platform.PlatformManager;
import org.gudy.azureus2.platform.PlatformManagerCapabilities;
import org.gudy.azureus2.platform.PlatformManagerFactory;
import org.gudy.azureus2.plugins.platform.PlatformManagerException;
import org.gudy.azureus2.plugins.ui.config.ConfigSection;
import org.gudy.azureus2.ui.swt.Messages;
import org.gudy.azureus2.ui.swt.Utils;
import org.gudy.azureus2.ui.swt.components.LinkLabel;
import org.gudy.azureus2.ui.swt.config.*;
import org.gudy.azureus2.ui.swt.mainwindow.Colors;
import org.gudy.azureus2.ui.swt.plugins.UISWTConfigSection;

import com.aelitis.azureus.core.networkmanager.admin.NetworkAdmin;

public class ConfigSectionConnectionAdvanced implements UISWTConfigSection {

	private final static String CFG_PREFIX = "ConfigView.section.connection.advanced.";

	private final static int REQUIRED_MODE = 2;

	public int maxUserMode() {
		return REQUIRED_MODE;
	}

	public String configSectionGetParentSection() {
		return ConfigSection.SECTION_CONNECTION;
	}

	public String configSectionGetName() {
		return "connection.advanced";
	}

	public void configSectionSave() {
	}

	public void configSectionDelete() {
	}

	public Composite configSectionCreate(final Composite parent) {
		GridData gridData;

		Composite cSection = new Composite(parent, SWT.NULL);

		gridData = new GridData(GridData.HORIZONTAL_ALIGN_FILL + GridData.VERTICAL_ALIGN_FILL);
		Utils.setLayoutData(cSection, gridData);
		GridLayout advanced_layout = new GridLayout();
		cSection.setLayout(advanced_layout);

		int userMode = COConfigurationManager.getIntParameter("User Mode");
		if (userMode < REQUIRED_MODE) {
			Label label = new Label(cSection, SWT.WRAP);
			gridData = new GridData();
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

		new LinkLabel(cSection, gridData, CFG_PREFIX
				+ "info.link", MessageText.getString(CFG_PREFIX + "url"));

		///////////////////////   ADVANCED SOCKET SETTINGS GROUP //////////

		Group gSocket = new Group(cSection, SWT.NULL);
		Messages.setLanguageText(gSocket, CFG_PREFIX + "socket.group");
		gridData = new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.FILL_HORIZONTAL);
		Utils.setLayoutData(gSocket, gridData);
		GridLayout glayout = new GridLayout();
		glayout.numColumns = 3;
		gSocket.setLayout(glayout);

			// max simultaneous

		Label lmaxout = new Label(gSocket, SWT.NULL);
		Messages.setLanguageText(lmaxout, "ConfigView.section.connection.network.max.simultaneous.connect.attempts");
		gridData = new GridData();
		Utils.setLayoutData(lmaxout,  gridData);

		IntParameter max_connects = new IntParameter(gSocket,
				"network.max.simultaneous.connect.attempts", 1, 100);
		gridData = new GridData();
		gridData.horizontalSpan = 2;
		max_connects.setLayoutData(gridData);

			// // max pending

		Label lmaxpout = new Label(gSocket, SWT.NULL);
		Messages.setLanguageText(lmaxpout, "ConfigView.section.connection.network.max.outstanding.connect.attempts");
		gridData = new GridData();
		Utils.setLayoutData(lmaxpout,  gridData);

		IntParameter max_pending_connects = new IntParameter(gSocket,
				"network.tcp.max.connections.outstanding", 1, 65536);
		gridData = new GridData();
		gridData.horizontalSpan = 2;
		max_pending_connects.setLayoutData(gridData);



			// bind ip

		Label lbind = new Label(gSocket, SWT.NULL);
		Messages.setLanguageText(lbind, "ConfigView.label.bindip");
		gridData = new GridData();
		Utils.setLayoutData(lbind, gridData);

		StringParameter bindip = new StringParameter(gSocket, "Bind IP", "", false);
		gridData = new GridData();
		gridData.widthHint = 100;
		gridData.horizontalSpan = 2;
		bindip.setLayoutData(gridData);

		Text lbind2 = new Text(gSocket, SWT.READ_ONLY | SWT.MULTI);
		lbind2.setTabs(8);
		Messages.setLanguageText(
				lbind2,
				"ConfigView.label.bindip.details",
				new String[] { "\t" + NetworkAdmin.getSingleton().getNetworkInterfacesAsString().replaceAll("\\\n", "\n\t") });
		gridData = new GridData();
		gridData.horizontalSpan = 3;
		Utils.setLayoutData(lbind2, gridData);

		BooleanParameter check_bind = new BooleanParameter(gSocket, "Check Bind IP On Start","network.check.ipbinding");
		gridData = new GridData();
		gridData.horizontalSpan = 3;
		check_bind.setLayoutData(gridData);

		BooleanParameter force_bind = new BooleanParameter(gSocket, "Enforce Bind IP","network.enforce.ipbinding");
		gridData = new GridData();
		gridData.horizontalSpan = 3;
		force_bind.setLayoutData(gridData);

		BooleanParameter bind_icon = new BooleanParameter(gSocket, "Show IP Bindings Icon", "network.ipbinding.icon.show");
		gridData = new GridData();
		gridData.horizontalSpan = 3;
		bind_icon.setLayoutData(gridData);

		BooleanParameter vpn_guess_enable = new BooleanParameter(gSocket, "network.admin.maybe.vpn.enable", "network.admin.maybe.vpn.enable");
		gridData = new GridData();
		gridData.horizontalSpan = 3;
		vpn_guess_enable.setLayoutData(gridData);


		Label lpbind = new Label(gSocket, SWT.NULL);
		Messages.setLanguageText(lpbind, CFG_PREFIX + "bind_port");
		final IntParameter port_bind = new IntParameter(gSocket,
				"network.bind.local.port", 0, 65535);
		gridData = new GridData();
		gridData.horizontalSpan = 2;
		port_bind.setLayoutData(gridData);


		Label lmtu = new Label(gSocket, SWT.NULL);
		Messages.setLanguageText(lmtu, CFG_PREFIX + "mtu");
		final IntParameter mtu_size = new IntParameter(gSocket,"network.tcp.mtu.size");
		mtu_size.setMaximumValue(512 * 1024);
		gridData = new GridData();
		gridData.horizontalSpan = 2;
		mtu_size.setLayoutData(gridData);

		// sndbuf
		Label lsend = new Label(gSocket, SWT.NULL);
		Messages.setLanguageText(lsend, CFG_PREFIX + "SO_SNDBUF");
		final IntParameter SO_SNDBUF = new IntParameter(gSocket,	"network.tcp.socket.SO_SNDBUF");
		gridData = new GridData();
		SO_SNDBUF.setLayoutData(gridData);

		final Label lsendcurr =  new Label(gSocket, SWT.NULL);
		gridData = new GridData( GridData.FILL_HORIZONTAL);
		gridData.horizontalIndent = 10;
		Utils.setLayoutData(lsendcurr,  gridData);

		// rcvbuf
		Label lreceiv = new Label(gSocket, SWT.NULL);
		Messages.setLanguageText(lreceiv, CFG_PREFIX + "SO_RCVBUF");
		final IntParameter SO_RCVBUF = new IntParameter(gSocket,	"network.tcp.socket.SO_RCVBUF");
		gridData = new GridData();
		SO_RCVBUF.setLayoutData(gridData);

		final Label lreccurr =  new Label(gSocket, SWT.NULL);
		gridData = new GridData( GridData.FILL_HORIZONTAL);
		gridData.horizontalIndent = 10;
		Utils.setLayoutData(lreccurr,  gridData);

		final Runnable buffUpdater =
			new Runnable() {
				public void run() {
					SocketChannel	sc = null;
					int	sndVal = 0;
					int	recVal = 0;
					try {
						sc = SocketChannel.open();
						Socket socket = sc.socket();
						if (SO_SNDBUF.getValue() == 0) {
							sndVal = socket.getSendBufferSize();
						}
						if (SO_RCVBUF.getValue() == 0) {
							recVal = socket.getReceiveBufferSize();
						}
					} catch (Throwable e) {
					} finally {
						try {
							sc.close();
						} catch (Throwable e) {
						}
					}
					if (sndVal == 0) {
						lsendcurr.setText("");
					} else {
						Messages.setLanguageText( lsendcurr, "label.current.equals", new String[]{ String.valueOf(sndVal) });
					}
					if (recVal == 0) {
						lreccurr.setText("");
					} else {
						Messages.setLanguageText( lreccurr, "label.current.equals", new String[]{ String.valueOf(recVal) });
					}
				}
			};
		buffUpdater.run();

		ParameterChangeAdapter buffListener =
			new ParameterChangeAdapter() {
				public void parameterChanged(
					Parameter 	p,
					boolean 	caused_internally) {
					buffUpdater.run();
				}
			};

		SO_RCVBUF.addChangeListener(buffListener);
		SO_SNDBUF.addChangeListener(buffListener);


		Label ltos = new Label(gSocket, SWT.NULL);
		Messages.setLanguageText(ltos, CFG_PREFIX + "IPDiffServ");
		final StringParameter IPDiffServ = new StringParameter(gSocket,	"network.tcp.socket.IPDiffServ");
		gridData = new GridData();
		gridData.widthHint = 100;
		gridData.horizontalSpan = 2;
		IPDiffServ.setLayoutData(gridData);


		//do simple input verification, and registry key setting for TOS field
		IPDiffServ.addChangeListener(new ParameterChangeAdapter() {
			final Color obg = IPDiffServ.getControl().getBackground();
			final Color ofg = IPDiffServ.getControl().getForeground();
			public void parameterChanged(Parameter p, boolean caused_internally) {
				String raw = IPDiffServ.getValue();
				int value = -1;
				try {
					value = Integer.decode(raw).intValue();
				} catch (Throwable t) {
				}
				if (value < 0 || value > 255) { //invalid or no value entered
					ConfigurationManager.getInstance().removeParameter(	"network.tcp.socket.IPDiffServ");
					if (raw != null && raw.length() > 0) { //error state
						IPDiffServ.getControl().setBackground(Colors.red);
						IPDiffServ.getControl().setForeground(Colors.white);
					} else { //no value state
						IPDiffServ.getControl().setBackground(obg);
						IPDiffServ.getControl().setForeground(ofg);
					}
					enableTOSRegistrySetting(false); //disable registry setting if necessary
				} else { //passes test
					IPDiffServ.getControl().setBackground(obg);
					IPDiffServ.getControl().setForeground(ofg);
					enableTOSRegistrySetting(true); //enable registry setting if necessary
				}
			}
		});

		// read select
		Label lreadsel = new Label(gSocket, SWT.NULL);
		Messages.setLanguageText(lreadsel, CFG_PREFIX + "read_select", new String[]{ String.valueOf( COConfigurationManager.getDefault("network.tcp.read.select.time"))});
		final IntParameter read_select = new IntParameter(gSocket,	"network.tcp.read.select.time", 10, 250);
		gridData = new GridData();
		gridData.horizontalSpan = 2;
		read_select.setLayoutData(gridData);

		Label lreadselmin = new Label(gSocket, SWT.NULL);
		Messages.setLanguageText(lreadselmin, CFG_PREFIX + "read_select_min", new String[]{ String.valueOf( COConfigurationManager.getDefault("network.tcp.read.select.min.time"))});
		final IntParameter read_select_min = new IntParameter(gSocket,	"network.tcp.read.select.min.time", 0, 100);
		gridData = new GridData();
		gridData.horizontalSpan = 2;
		read_select_min.setLayoutData(gridData);

		// write select
		Label lwritesel = new Label(gSocket, SWT.NULL);
		Messages.setLanguageText(lwritesel, CFG_PREFIX + "write_select", new String[]{ String.valueOf( COConfigurationManager.getDefault("network.tcp.write.select.time"))});
		final IntParameter write_select = new IntParameter(gSocket,	"network.tcp.write.select.time", 10, 250);
		gridData = new GridData();
		gridData.horizontalSpan = 2;
		write_select.setLayoutData(gridData);

		Label lwriteselmin = new Label(gSocket, SWT.NULL);
		Messages.setLanguageText(lwriteselmin, CFG_PREFIX + "write_select_min", new String[]{ String.valueOf( COConfigurationManager.getDefault("network.tcp.write.select.min.time"))});
		final IntParameter write_select_min = new IntParameter(gSocket,	"network.tcp.write.select.min.time", 0, 100);
		gridData = new GridData();
		gridData.horizontalSpan = 2;
		write_select_min.setLayoutData(gridData);

		new BooleanParameter(cSection, "IPV6 Enable Support", "network.ipv6.enable.support");
		new BooleanParameter(cSection, "IPV6 Prefer Addresses", "network.ipv6.prefer.addresses");
		if (Constants.isWindowsVistaOrHigher && Constants.isJava7OrHigher) {
			new BooleanParameter(cSection, "IPV4 Prefer Stack", "network.ipv4.prefer.stack");
		}
		//////////////////////////////////////////////////////////////////////////
		return cSection;
	}

	private void enableTOSRegistrySetting(boolean enable) {
		PlatformManager mgr = PlatformManagerFactory.getPlatformManager();
		if (mgr.hasCapability(PlatformManagerCapabilities.SetTCPTOSEnabled)) {
			//see http://wiki.vuze.com/w/AdvancedNetworkSettings
			try {
				mgr.setTCPTOSEnabled(enable);
			} catch (PlatformManagerException pe) {
				Debug.printStackTrace(pe);
			}
		}
	}

}
