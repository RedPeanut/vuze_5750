/*
 * Created on Sep 13, 2004
 * Created by Olivier Chalouhi
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
package org.gudy.azureus2.ui.swt.views.stats;


import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.config.ParameterListener;
import org.gudy.azureus2.core3.download.DownloadManager;
import org.gudy.azureus2.core3.download.impl.DownloadManagerRateController;
import org.gudy.azureus2.core3.global.GlobalManager;
import org.gudy.azureus2.core3.global.GlobalManagerStats;
import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.peer.PEPeer;
import org.gudy.azureus2.core3.peer.PEPeerManager;
import org.gudy.azureus2.core3.peer.PEPeerStats;
import org.gudy.azureus2.core3.stats.transfer.OverallStats;
import org.gudy.azureus2.core3.stats.transfer.StatsFactory;
import org.gudy.azureus2.core3.util.AERunnable;
import org.gudy.azureus2.core3.util.Constants;
import org.gudy.azureus2.core3.util.DisplayFormatters;
import org.gudy.azureus2.core3.util.SystemTime;
import org.gudy.azureus2.pluginsimpl.local.PluginCoreUtils;
import org.gudy.azureus2.ui.swt.Messages;
import org.gudy.azureus2.ui.swt.TextViewerWindow;
import org.gudy.azureus2.ui.swt.Utils;
import org.gudy.azureus2.ui.swt.components.BufferedLabel;
import org.gudy.azureus2.ui.swt.components.Legend;
import org.gudy.azureus2.ui.swt.components.graphics.PingGraphic;
import org.gudy.azureus2.ui.swt.components.graphics.Plot3D;
import org.gudy.azureus2.ui.swt.components.graphics.Scale;
import org.gudy.azureus2.ui.swt.components.graphics.SpeedGraphic;
import org.gudy.azureus2.ui.swt.components.graphics.ValueFormater;
import org.gudy.azureus2.ui.swt.mainwindow.Colors;
import org.gudy.azureus2.ui.swt.plugins.UISWTView;
import org.gudy.azureus2.ui.swt.plugins.UISWTViewEvent;
import org.gudy.azureus2.ui.swt.pluginsimpl.UISWTViewCoreEventListener;

import com.aelitis.azureus.core.AzureusCore;
import com.aelitis.azureus.core.AzureusCoreRunningListener;
import com.aelitis.azureus.core.AzureusCoreFactory;
import com.aelitis.azureus.core.networkmanager.NetworkConnection;
import com.aelitis.azureus.core.networkmanager.Transport;
import com.aelitis.azureus.core.networkmanager.TransportStartpoint;
import com.aelitis.azureus.core.networkmanager.admin.NetworkAdmin;
import com.aelitis.azureus.core.proxy.AEProxySelector;
import com.aelitis.azureus.core.proxy.AEProxySelectorFactory;
import com.aelitis.azureus.core.speedmanager.SpeedManager;
import com.aelitis.azureus.core.speedmanager.SpeedManagerLimitEstimate;
import com.aelitis.azureus.core.speedmanager.SpeedManagerPingMapper;
import com.aelitis.azureus.core.speedmanager.SpeedManagerPingSource;
import com.aelitis.azureus.core.speedmanager.SpeedManagerPingZone;
import com.aelitis.azureus.ui.UIFunctions;
import com.aelitis.azureus.ui.UIFunctionsManager;
import com.aelitis.azureus.ui.mdi.MultipleDocumentInterface;
import com.aelitis.net.udp.uc.PRUDPPacketHandler;
import com.aelitis.net.udp.uc.PRUDPPacketHandlerFactory;

/**
 *
 */
public class TransferStatsView
	implements UISWTViewCoreEventListener
{
	public static final String MSGID_PREFIX = "TransferStatsView";

	private static final int MAX_DISPLAYED_PING_MILLIS		= 1199;	// prevents us hitting 1200 and resulting in graph expanding to 1400
	private static final int MAX_DISPLAYED_PING_MILLIS_DISP	= 1200;	// tidy display

	private GlobalManager		globalManager;
	private GlobalManagerStats 	stats;
	private SpeedManager 		speedManager;

	private OverallStats totalStats;

	private Composite mainPanel;

	private Composite blahPanel;
	private BufferedLabel asn,estUpCap,estDownCap;
	private BufferedLabel uploadBiaser;
	private BufferedLabel currentIP;

	private Composite 		connectionPanel;
	private BufferedLabel	uploadLabel, connectionLabel;
	private SpeedGraphic	uploadGraphic;
	private SpeedGraphic	connectionGraphic;

	private TabFolder 			conFolder;
	private long				last_route_update;
	private Composite 			route_comp;
	private BufferedLabel[][]	route_labels 	= new BufferedLabel[0][0];
	private Map<String,Long>	route_last_seen	= new HashMap<String, Long>();

	private Composite generalPanel;
	private BufferedLabel totalLabel;
	private BufferedLabel nowUp, nowDown, sessionDown, sessionUp, sessionRatio, sessionTime, totalDown, totalUp, total_ratio, totalTime;

	private Label socksState;
	private BufferedLabel socksCurrent, socksFails;
	private Label socksMore;

	private Group autoSpeedPanel;
	private StackLayout autoSpeedPanelLayout;
	private Composite autoSpeedInfoPanel;
	private Composite autoSpeedDisabledPanel;
	private PingGraphic pingGraph;

	private plotView[]	plotViews;
	private zoneView[] 	zoneViews;

	private limitToTextHelper	limit_to_text = new limitToTextHelper();

	private final DecimalFormat formatter = new DecimalFormat("##.#");

	private boolean	initialised;

	private UISWTView swtView;


	public TransferStatsView() {
		AzureusCoreFactory.addCoreRunningListener(new AzureusCoreRunningListener() {
			public void azureusCoreRunning(AzureusCore core) {
				globalManager = core.getGlobalManager();
				stats = globalManager.getStats();
				speedManager = core.getSpeedManager();
				totalStats = StatsFactory.getStats();
			}
		});
		pingGraph = PingGraphic.getInstance();
	}

	private void initialize(Composite composite) {

		mainPanel = new Composite(composite,SWT.NULL);
		GridLayout mainLayout = new GridLayout();
		mainPanel.setLayout(mainLayout);

		AzureusCoreFactory.addCoreRunningListener(new AzureusCoreRunningListener() {
			public void azureusCoreRunning(AzureusCore core) {
				Utils.execSWTThread(new AERunnable() {
					public void runSupport() {
						if (mainPanel == null || mainPanel.isDisposed()) {
							return;
						}
						createGeneralPanel();
						createConnectionPanel();
						createCapacityPanel();
						createAutoSpeedPanel();

						initialised	= true;
					}
				});
			}
		});
	}

	private void createGeneralPanel() {
		generalPanel = new Composite(mainPanel, SWT.NULL);
		GridLayout outerLayout = new GridLayout();
		outerLayout.numColumns = 2;
		generalPanel.setLayout(outerLayout);
		GridData gridData = new GridData(GridData.FILL_HORIZONTAL);
		Utils.setLayoutData(generalPanel, gridData);
	
		Composite generalStatsPanel = new Composite(generalPanel,SWT.BORDER);
		GridData generalStatsPanelGridData = new GridData(GridData.FILL_HORIZONTAL);
		generalStatsPanelGridData.grabExcessHorizontalSpace = true;
		Utils.setLayoutData(generalStatsPanel, generalStatsPanelGridData);

		GridLayout panelLayout = new GridLayout();
		panelLayout.numColumns = 5;
		panelLayout.makeColumnsEqualWidth = true;
		generalStatsPanel.setLayout(panelLayout);

		Label lbl = new Label(generalStatsPanel,SWT.NULL);

		lbl = new Label(generalStatsPanel,SWT.NULL);
		Messages.setLanguageText(lbl,"SpeedView.stats.downloaded");

		lbl = new Label(generalStatsPanel,SWT.NULL);
		Messages.setLanguageText(lbl,"SpeedView.stats.uploaded");

		lbl = new Label(generalStatsPanel,SWT.NULL);
		Messages.setLanguageText(lbl,"SpeedView.stats.ratio");

		lbl = new Label(generalStatsPanel,SWT.NULL);
		Messages.setLanguageText(lbl,"SpeedView.stats.uptime");

		lbl = new Label(generalStatsPanel,SWT.NULL);
		lbl = new Label(generalStatsPanel,SWT.NULL);
		lbl = new Label(generalStatsPanel,SWT.NULL);
		lbl = new Label(generalStatsPanel,SWT.NULL);
		lbl = new Label(generalStatsPanel,SWT.NULL);

		/////// NOW /////////
		Label nowLabel = new Label(generalStatsPanel,SWT.NULL);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		Utils.setLayoutData(nowLabel, gridData);
		Messages.setLanguageText(nowLabel,"SpeedView.stats.now");

		nowDown = new BufferedLabel(generalStatsPanel,SWT.DOUBLE_BUFFERED);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		Utils.setLayoutData(nowDown, gridData);

		nowUp = new BufferedLabel(generalStatsPanel,SWT.DOUBLE_BUFFERED);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		Utils.setLayoutData(nowUp, gridData);

		lbl = new Label(generalStatsPanel,SWT.NULL);
		lbl = new Label(generalStatsPanel,SWT.NULL);

		//////// SESSION ////////
		Label sessionLabel = new Label(generalStatsPanel,SWT.NULL);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		Utils.setLayoutData(sessionLabel, gridData);

		Messages.setLanguageText(sessionLabel,"SpeedView.stats.session");
		sessionDown = new BufferedLabel(generalStatsPanel,SWT.DOUBLE_BUFFERED);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		Utils.setLayoutData(sessionDown, gridData);

		sessionUp = new BufferedLabel(generalStatsPanel,SWT.DOUBLE_BUFFERED);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		Utils.setLayoutData(sessionUp, gridData);

		sessionRatio = new BufferedLabel(generalStatsPanel,SWT.DOUBLE_BUFFERED);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		Utils.setLayoutData(sessionRatio, gridData);

		sessionTime = new BufferedLabel(generalStatsPanel,SWT.DOUBLE_BUFFERED);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		Utils.setLayoutData(sessionTime, gridData);

		///////// TOTAL ///////////
		totalLabel = new BufferedLabel(generalStatsPanel,SWT.DOUBLE_BUFFERED);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		Utils.setLayoutData(totalLabel, gridData);
		Messages.setLanguageText(totalLabel.getWidget(),"SpeedView.stats.total");

		totalDown = new BufferedLabel(generalStatsPanel,SWT.DOUBLE_BUFFERED);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		Utils.setLayoutData(totalDown, gridData);

		totalUp = new BufferedLabel(generalStatsPanel,SWT.DOUBLE_BUFFERED);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		Utils.setLayoutData(totalUp, gridData);

		total_ratio = new BufferedLabel(generalStatsPanel,SWT.DOUBLE_BUFFERED);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		Utils.setLayoutData(total_ratio, gridData);

		totalTime = new BufferedLabel(generalStatsPanel,SWT.DOUBLE_BUFFERED);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		Utils.setLayoutData(totalTime, gridData);

		for (Object obj: new Object[]{ nowLabel, sessionLabel, totalLabel }) {
			Control control;
			if (obj instanceof BufferedLabel) {
				control = ((BufferedLabel)obj).getControl();
			} else {
				control = (Label)obj;
			}
			final Menu menu = new Menu(control.getShell(), SWT.POP_UP);
			control.setMenu(menu);
			MenuItem	 item = new MenuItem(menu,SWT.NONE);
			Messages.setLanguageText(item, "MainWindow.menu.view.configuration");
			item.addSelectionListener(
				new SelectionAdapter() {
					public void widgetSelected(
						SelectionEvent e) {
						UIFunctions uif = UIFunctionsManager.getUIFunctions();
						if (uif != null) {
							uif.getMDI().showEntryByID(
									MultipleDocumentInterface.SIDEBAR_SECTION_CONFIG, "Stats");
						}
					}
				});
		}

		// SOCKS area
		Composite generalSocksPanel = new Composite(generalPanel,SWT.BORDER);
		GridData generalSocksData = new GridData();
		Utils.setLayoutData(generalSocksPanel, generalSocksData);

		GridLayout socksLayout = new GridLayout();
		socksLayout.numColumns = 2;
		generalSocksPanel.setLayout(socksLayout);

		lbl = new Label(generalSocksPanel,SWT.NULL);
		Messages.setLanguageText(lbl,"label.socks");

		lbl = new Label(generalSocksPanel,SWT.NULL);

			// proxy state

		lbl = new Label(generalSocksPanel,SWT.NULL);
		lbl.setText(MessageText.getString("label.proxy" ) + ":");

		socksState =	new Label(generalSocksPanel,SWT.NULL);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.widthHint = 120;
		Utils.setLayoutData(socksState, gridData);

			// current details

		lbl = new Label(generalSocksPanel,SWT.NULL);
		lbl.setText(MessageText.getString("PeersView.state" ) + ":");

		socksCurrent =	new BufferedLabel(generalSocksPanel,SWT.DOUBLE_BUFFERED);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		Utils.setLayoutData(socksCurrent, gridData);

			// fail details

		lbl = new Label(generalSocksPanel,SWT.NULL);
		lbl.setText(MessageText.getString("label.fails" ) + ":");

		socksFails =	new BufferedLabel(generalSocksPanel,SWT.DOUBLE_BUFFERED);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		Utils.setLayoutData(socksFails, gridData);

			// more info

		lbl = new Label(generalSocksPanel,SWT.NULL);

		gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.horizontalAlignment = GridData.END;
		socksMore	=	new Label(generalSocksPanel, SWT.NULL);
		socksMore.setText(MessageText.getString("label.more") + "...");
		Utils.setLayoutData(socksMore,	gridData);
		socksMore.setCursor(socksMore.getDisplay().getSystemCursor(SWT.CURSOR_HAND));
		socksMore.setForeground(Colors.blue);
		socksMore.addMouseListener(new MouseAdapter() {
			public void mouseDoubleClick(MouseEvent arg0) {
				showSOCKSInfo();
			}
			public void mouseUp(MouseEvent arg0) {
				showSOCKSInfo();
			}
		});

			// got a rare layout bug that results in the generalStatsPanel not showing the bottom row correctly until the panel
			// is resized - attempt to fix by sizing based on the socks panel which seems to consistently layout OK

		Point socks_size = generalSocksPanel.computeSize(SWT.DEFAULT, SWT.DEFAULT);
		Rectangle trim = generalSocksPanel.computeTrim(0, 0, socks_size.x, socks_size.y);
		generalStatsPanelGridData.heightHint = socks_size.y - (trim.height - socks_size.y);
	}

	private void showSOCKSInfo() {
		AEProxySelector proxy_selector = AEProxySelectorFactory.getSelector();

		String	info = proxy_selector.getInfo();

		TextViewerWindow viewer = new TextViewerWindow(
			MessageText.getString("proxy.info.title"),
			null,
			info, false );

	}

	private void createCapacityPanel() {
		blahPanel = new Composite(mainPanel,SWT.NONE);
		GridData blahPanelData = new GridData(GridData.FILL_HORIZONTAL);
		Utils.setLayoutData(blahPanel, blahPanelData);

		GridLayout panelLayout = new GridLayout();
		panelLayout.numColumns = 8;
		blahPanel.setLayout(panelLayout);


		Label label;
		GridData gridData;

		label = new Label(blahPanel,SWT.NONE);
		Messages.setLanguageText(label,"SpeedView.stats.asn");
		asn = new BufferedLabel(blahPanel,SWT.NONE);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		Utils.setLayoutData(asn, gridData);

		label = new Label(blahPanel,SWT.NONE);
		Messages.setLanguageText(label,"label.current_ip");
		currentIP = new BufferedLabel(blahPanel,SWT.NONE);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		Utils.setLayoutData(currentIP, gridData);

		label = new Label(blahPanel,SWT.NONE);
		Messages.setLanguageText(label,"SpeedView.stats.estupcap");
		estUpCap = new BufferedLabel(blahPanel,SWT.NONE);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		Utils.setLayoutData(estUpCap, gridData);

		label = new Label(blahPanel,SWT.NONE);
		Messages.setLanguageText(label,"SpeedView.stats.estdowncap");
		estDownCap = new BufferedLabel(blahPanel,SWT.NONE);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		Utils.setLayoutData(estDownCap, gridData);

		label = new Label(blahPanel,SWT.NONE);
		Messages.setLanguageText(label,"SpeedView.stats.upbias");
		uploadBiaser = new BufferedLabel(blahPanel,SWT.NONE);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.horizontalSpan = 7;
		Utils.setLayoutData(uploadBiaser, gridData);
	}

	private void createConnectionPanel() {
		connectionPanel = new Composite(mainPanel,SWT.NONE);
		GridData gridData = new GridData(GridData.FILL_HORIZONTAL);
		Utils.setLayoutData(connectionPanel, gridData);

		GridLayout panelLayout = new GridLayout();
		panelLayout.numColumns = 2;
		panelLayout.makeColumnsEqualWidth = true;
		connectionPanel.setLayout(panelLayout);

		Composite conn_area = new Composite(connectionPanel, SWT.NULL);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		Utils.setLayoutData(conn_area, gridData);

		panelLayout = new GridLayout();
		panelLayout.numColumns = 2;
		conn_area.setLayout(panelLayout);

		Label label = new Label(conn_area, SWT.NULL);
		Messages.setLanguageText(label, "SpeedView.stats.con");

		connectionLabel = new BufferedLabel(conn_area, SWT.DOUBLE_BUFFERED);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		Utils.setLayoutData(connectionLabel, gridData);

		Composite upload_area = new Composite(connectionPanel, SWT.NULL);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		Utils.setLayoutData(upload_area, gridData);

		panelLayout = new GridLayout();
		panelLayout.numColumns = 2;
		upload_area.setLayout(panelLayout);

		label = new Label(upload_area, SWT.NULL);
		Messages.setLanguageText(label, "SpeedView.stats.upload");

		uploadLabel = new BufferedLabel(upload_area, SWT.DOUBLE_BUFFERED);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		Utils.setLayoutData(uploadLabel, gridData);


		// connections
		conFolder = new TabFolder(connectionPanel, SWT.LEFT);
		gridData = new GridData(GridData.FILL_BOTH);
		gridData.horizontalSpan = 1;
		Utils.setLayoutData(conFolder, gridData);
		conFolder.setBackground(Colors.background);

		// connection counts
		TabItem connItem = new TabItem(conFolder, SWT.NULL);

		connItem.setText(MessageText.getString("label.connections"));

		Canvas connectionCanvas = new Canvas(conFolder,SWT.NO_BACKGROUND);
		connItem.setControl(connectionCanvas);
		gridData = new GridData(GridData.FILL_BOTH);
		gridData.heightHint = 200;
		Utils.setLayoutData(connectionCanvas, gridData);
		connectionGraphic =
			SpeedGraphic.getInstance(
				new Scale(false),
				new ValueFormater() {
					public String format(int value) {
						 return (String.valueOf(value));
					}
				}
			);

		connectionGraphic.initialize(connectionCanvas);
		Color[] colors = connectionGraphic.colors;

		connectionGraphic.setLineColors(colors);

		// route info
		TabItem routeInfoTab = new TabItem(conFolder, SWT.NULL);

		routeInfoTab.setText(MessageText.getString("label.routing"));

		Composite route_tab_comp = new Composite(conFolder, SWT.NULL);
		Utils.setLayoutData(route_tab_comp, new GridData(SWT.FILL, SWT.FILL, true, true));
		GridLayout routeTabLayout = new GridLayout();
		routeTabLayout.numColumns = 1;
		route_tab_comp.setLayout(routeTabLayout);

		routeInfoTab.setControl(route_tab_comp);

		ScrolledComposite sc = new ScrolledComposite(route_tab_comp, SWT.V_SCROLL);
		Utils.setLayoutData(sc, new GridData(SWT.FILL, SWT.FILL, true, true));

		route_comp = new Composite(sc, SWT.NULL);

		Utils.setLayoutData(route_comp, new GridData(SWT.FILL, SWT.FILL, true, true));
		GridLayout routeLayout = new GridLayout();
		routeLayout.numColumns = 3;
		//routeLayout.makeColumnsEqualWidth = true;
		route_comp.setLayout(routeLayout);

		sc.setContent(route_comp);

		buildRouteComponent(5);


		// upload queued
		Canvas uploadCanvas = new Canvas(connectionPanel,SWT.NO_BACKGROUND);
		gridData = new GridData(GridData.FILL_BOTH);
		gridData.heightHint = 200;
		Utils.setLayoutData(uploadCanvas, gridData);
		uploadGraphic =
			SpeedGraphic.getInstance(
			new ValueFormater() {
					public String format(int value) {
							 return DisplayFormatters.formatByteCountToKiBEtc(value);
					}
			});

		uploadGraphic.initialize(uploadCanvas);

	}

	private void buildRouteComponent(int rows) {
		boolean	changed = false;
		if (rows <= route_labels.length) {
			for ( int i=rows;i<route_labels.length;i++) {
				for ( int j=0;j<3;j++) {
					route_labels[i][j].setText("");
				}
			}
		} else {
			Control[] labels = route_comp.getChildren();
			for (int i = 0; i < labels.length; i++) {
				labels[i].dispose();
			}
			Label h1 = new Label(route_comp, SWT.NULL);
			Utils.setLayoutData(h1,	new GridData(GridData.FILL_HORIZONTAL ));
			h1.setText(MessageText.getString("label.route"));
			Label h2 = new Label(route_comp, SWT.NULL);
			Utils.setLayoutData(h2,	new GridData(GridData.FILL_HORIZONTAL ));
			h2.setText(MessageText.getString("tps.type.incoming"));
			Label h3 = new Label(route_comp, SWT.NULL);
			Utils.setLayoutData(h3,	new GridData(GridData.FILL_HORIZONTAL ));
			h3.setText(MessageText.getString("label.outgoing"));
			new Label(route_comp, SWT.NULL);
			new Label(route_comp, SWT.NULL);
			new Label(route_comp, SWT.NULL);
			route_labels = new BufferedLabel[rows][3];
			for (int i=0;i<rows;i++) {
				for ( int j=0;j<3;j++) {
					BufferedLabel l = new BufferedLabel(route_comp, SWT.DOUBLE_BUFFERED);
					GridData gridData = new GridData(GridData.FILL_HORIZONTAL);
					Utils.setLayoutData(l,	gridData);
					route_labels[i][j] = l;
				}
			}
			changed = true;
		}
		Point size = route_comp.computeSize(route_comp.getParent().getSize().x, SWT.DEFAULT);
		changed = changed || !route_comp.getSize().equals(size);
		route_comp.setSize(size);
		if (!changed) {
				// sometimes things get layouted when not visibel and things don't work proper when visibilized ;(
				// look for something zero height that shouldn't be
			for ( int i=0;i<route_labels.length;i++) {
				for (int j=0;j<3;j++) {
					BufferedLabel lab = route_labels[i][j];
					if (lab.getControl().getSize().y == 0 &&	lab.getText().length() > 0) {
						changed = true;
					}
				}
			}
		}
		if (changed) {
			route_comp.getParent().layout(true, true);
		}
		route_comp.update();
	}

	private void createAutoSpeedPanel() {
		autoSpeedPanel = new Group(mainPanel,SWT.NONE);
		GridData generalPanelData = new GridData(GridData.FILL_BOTH);
		Utils.setLayoutData(autoSpeedPanel, generalPanelData);
		Messages.setLanguageText(autoSpeedPanel,"SpeedView.stats.autospeed", new String[]{ String.valueOf(MAX_DISPLAYED_PING_MILLIS_DISP)});


		autoSpeedPanelLayout = new StackLayout();
		autoSpeedPanel.setLayout(autoSpeedPanelLayout);

		autoSpeedInfoPanel = new Composite(autoSpeedPanel,SWT.NULL);
		Utils.setLayoutData(autoSpeedInfoPanel, new GridData(GridData.FILL_BOTH));
		GridLayout layout = new GridLayout();
		layout.numColumns = 8;
		layout.makeColumnsEqualWidth = true;
		autoSpeedInfoPanel.setLayout(layout);

		Canvas pingCanvas = new Canvas(autoSpeedInfoPanel,SWT.NO_BACKGROUND);
		GridData gridData = new GridData(GridData.FILL_BOTH);
		gridData.horizontalSpan = 4;
		Utils.setLayoutData(pingCanvas, gridData);

		pingGraph.initialize(pingCanvas);

		TabFolder folder = new TabFolder(autoSpeedInfoPanel, SWT.LEFT);
		gridData = new GridData(GridData.FILL_BOTH);
		gridData.horizontalSpan = 4;
		Utils.setLayoutData(folder, gridData);
		folder.setBackground(Colors.background);

		ValueFormater speed_formatter =
			new ValueFormater() {
				public String format(
					int value) {
					return (DisplayFormatters.formatByteCountToKiBEtc( value));
				}
			};

		ValueFormater time_formatter =
			new ValueFormater() {
				public String format(
					int value) {
					return (value + " ms");
				}
			};

		ValueFormater[] formatters = new ValueFormater[]{ speed_formatter, speed_formatter, time_formatter };

		String[] labels = new String[]{ "up", "down", "ping" };

		SpeedManagerPingMapper[] mappers = speedManager.getMappers();

		plotViews	= new plotView[mappers.length];
		zoneViews	= new zoneView[mappers.length];

		for (int i=0;i<mappers.length;i++) {

			SpeedManagerPingMapper mapper = mappers[i];

			TabItem plot_item = new TabItem(folder, SWT.NULL);

			plot_item.setText("Plot " + mapper.getName());

			Canvas plotCanvas = new Canvas(folder,SWT.NO_BACKGROUND);
			gridData = new GridData(GridData.FILL_BOTH);
			Utils.setLayoutData(plotCanvas, gridData);

			plotViews[i] = new plotView(mapper, plotCanvas, labels, formatters);

			plot_item.setControl(plotCanvas);

			TabItem zones_item = new TabItem(folder, SWT.NULL);
			zones_item.setText("Zones " + mapper.getName());

			Canvas zoneCanvas = new Canvas(folder,SWT.NO_BACKGROUND);
			gridData = new GridData(GridData.FILL_BOTH);
			Utils.setLayoutData(zoneCanvas, gridData);

			zoneViews[i] = new zoneView(mapper, zoneCanvas, labels, formatters);

			zones_item.setControl(zoneCanvas);
		}

		autoSpeedDisabledPanel = new Composite(autoSpeedPanel,SWT.NULL);
		autoSpeedDisabledPanel.setLayout(new GridLayout());
		Label disabled = new Label(autoSpeedDisabledPanel,SWT.NULL);
		disabled.setEnabled(false);
		Messages.setLanguageText(disabled,"SpeedView.stats.autospeed.disabled");
		Utils.setLayoutData(disabled, new GridData(GridData.HORIZONTAL_ALIGN_CENTER | GridData.FILL_HORIZONTAL));

		autoSpeedPanelLayout.topControl = speedManager.isAvailable() ? autoSpeedInfoPanel : autoSpeedDisabledPanel;

		gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.horizontalSpan = 8;

	Legend.createLegendComposite(
			autoSpeedInfoPanel,
				PingGraphic.defaultColors,
				new String[]{
							"TransferStatsView.legend.pingaverage",
							"TransferStatsView.legend.ping1",
							"TransferStatsView.legend.ping2",
						"TransferStatsView.legend.ping3" },
				gridData);
	}

	private void delete() {
		Utils.disposeComposite(generalPanel);
		Utils.disposeComposite(blahPanel);

		if (uploadGraphic != null) {
			uploadGraphic.dispose();
		}

		if (connectionGraphic != null) {
			connectionGraphic.dispose();
		}

		if (pingGraph != null) {
			pingGraph.dispose();
		}

		if (plotViews != null) {
			for (int i = 0; i < plotViews.length; i++) {

				plotViews[i].dispose();
			}
		}

		if (zoneViews != null) {
			for (int i = 0; i < zoneViews.length; i++) {

				zoneViews[i].dispose();
			}
		}
	}

	private Composite getComposite() {
		return mainPanel;
	}



	private void refresh() {

		if (!initialised) {
			return;
		}
		refreshGeneral();

		refreshCapacityPanel();

		refreshConnectionPanel();

		refreshPingPanel();
	}

	private void refreshGeneral() {
	if (stats == null) {
		return;
	}

		int now_prot_down_rate = stats.getProtocolReceiveRate();
		int now_prot_up_rate = stats.getProtocolSendRate();

		int now_total_down_rate = stats.getDataReceiveRate() + now_prot_down_rate;
		int now_total_up_rate = stats.getDataSendRate() + now_prot_up_rate;

		float now_perc_down = (float)(now_prot_down_rate *100) / (now_total_down_rate==0?1:now_total_down_rate);
		float now_perc_up = (float)(now_prot_up_rate *100) / (now_total_up_rate==0?1:now_total_up_rate);

		nowDown.setText(DisplayFormatters.formatByteCountToKiBEtcPerSec(now_total_down_rate) +
										"	(" + DisplayFormatters.formatByteCountToKiBEtcPerSec(now_prot_down_rate) +
										", " +formatter.format(now_perc_down )+ "%)");

		nowUp.setText(DisplayFormatters.formatByteCountToKiBEtcPerSec(now_total_up_rate) +
									"	(" + DisplayFormatters.formatByteCountToKiBEtcPerSec(now_prot_up_rate) +
									", " +formatter.format(now_perc_up )+ "%)");

		///////////////////////////////////////////////////////////////////////

		long session_prot_received = stats.getTotalProtocolBytesReceived();
		long session_prot_sent = stats.getTotalProtocolBytesSent();

		long session_total_received = stats.getTotalDataBytesReceived() + session_prot_received;
		long session_total_sent = stats.getTotalDataBytesSent() + session_prot_sent;

		float session_perc_received = (float)(session_prot_received *100) / (session_total_received==0?1:session_total_received);
		float session_perc_sent = (float)(session_prot_sent *100) / (session_total_sent==0?1:session_total_sent);

		sessionDown.setText(DisplayFormatters.formatByteCountToKiBEtc(session_total_received) +
												"	(" + DisplayFormatters.formatByteCountToKiBEtc(session_prot_received) +
												", " +formatter.format(session_perc_received )+ "%)");

		sessionUp.setText(DisplayFormatters.formatByteCountToKiBEtc(session_total_sent) +
											"	(" + DisplayFormatters.formatByteCountToKiBEtc(session_prot_sent) +
											", " +formatter.format(session_perc_sent )+ "%)");

		////////////////////////////////////////////////////////////////////////

		if (totalStats != null) {
			long mark = totalStats.getMarkTime();
			if (mark > 0) {
				Messages.setLanguageText(totalLabel.getWidget(),"SpeedView.stats.total.since", new String[]{ new SimpleDateFormat().format(new Date( mark)) });
			} else {
				Messages.setLanguageText(totalLabel.getWidget(),"SpeedView.stats.total");
			}

			long dl_bytes = totalStats.getDownloadedBytes(true);
			long ul_bytes = totalStats.getUploadedBytes(true);

			totalDown.setText(DisplayFormatters.formatByteCountToKiBEtc(dl_bytes));
			totalUp.setText(DisplayFormatters.formatByteCountToKiBEtc(ul_bytes));

			long session_up_time 	= totalStats.getSessionUpTime();
			long total_up_time 	= totalStats.getTotalUpTime(true);

			sessionTime.setText(session_up_time==0?"":DisplayFormatters.formatETA( session_up_time));
			totalTime.setText(total_up_time==0?"":DisplayFormatters.formatETA( total_up_time));


			long t_ratio_raw = (1000* ul_bytes / (dl_bytes==0?1:dl_bytes));
			long s_ratio_raw = (1000* session_total_sent / (session_total_received==0?1:session_total_received));

			String t_ratio = "";
			String s_ratio = "";

			String partial = String.valueOf(t_ratio_raw % 1000);
			while (partial.length() < 3) {
				partial = "0" + partial;
			}
			t_ratio = (t_ratio_raw / 1000) + "." + partial;

			partial = String.valueOf(s_ratio_raw % 1000);
			while (partial.length() < 3) {
				partial = "0" + partial;
			}
			s_ratio = (s_ratio_raw / 1000) + "." + partial;


			total_ratio.setText(t_ratio);
			sessionRatio.setText(s_ratio);
		}

		AEProxySelector proxy_selector = AEProxySelectorFactory.getSelector();

		Proxy proxy = proxy_selector.getActiveProxy();

		socksMore.setEnabled(proxy != null);

		if (Constants.isOSX) {

			socksMore.setForeground(proxy==null?Colors.light_grey:Colors.blue);
		}

		socksState.setText(proxy==null?MessageText.getString("label.inactive"): ((InetSocketAddress)proxy.address()).getHostName());

		if (proxy == null) {

			socksCurrent.setText("");

			socksFails.setText("");

		} else {
			long	last_con 	= proxy_selector.getLastConnectionTime();
			long	last_fail 	= proxy_selector.getLastFailTime();
			int		total_cons	= proxy_selector.getConnectionCount();
			int		total_fails	= proxy_selector.getFailCount();

			long	now = SystemTime.getMonotonousTime();

			long	con_ago		= now - last_con;
			long	fail_ago 	= now - last_fail;

			String	state_str;

			if (last_fail < 0) {

				state_str = "PeerManager.status.ok";

			} else {

				if (fail_ago > 60*1000) {

					if (con_ago < fail_ago) {

						state_str = "PeerManager.status.ok";

					} else {

						state_str = "SpeedView.stats.unknown";
					}
				} else {

					state_str = "ManagerItem.error";
				}
			}

			socksCurrent.setText(MessageText.getString( state_str ) + ", con=" + total_cons);

			long	fail_ago_secs = fail_ago/1000;

			if (fail_ago_secs == 0) {

				fail_ago_secs = 1;
			}

			socksFails.setText(last_fail<0?"":(DisplayFormatters.formatETA( fail_ago_secs, false ) + " " + MessageText.getString("label.ago" ) + ", tot=" + total_fails));
		}
	}

	private void refreshCapacityPanel() {
		if (speedManager == null) {
			return;
		}

		asn.setText(speedManager.getASN());

		estUpCap.setText(limit_to_text.getLimitText(speedManager.getEstimatedUploadCapacityBytesPerSec()));

		estDownCap.setText(limit_to_text.getLimitText(speedManager.getEstimatedDownloadCapacityBytesPerSec()));

		uploadBiaser.setText( DownloadManagerRateController.getString());

		InetAddress current_ip = NetworkAdmin.getSingleton().getDefaultPublicAddress();

		currentIP.setText(current_ip==null?"":current_ip.getHostAddress());
	}

	private void refreshConnectionPanel() {
		
		if (globalManager == null)
			return;
		
		int	totalConnections	= 0;
		int	totalConQueued		= 0;
		int	totalConBlocked		= 0;
		int	totalConUnchoked	= 0;
		int	totalDataQueued		= 0;
		int	totalIn 			= 0;
		
		List<DownloadManager> dms = globalManager.getDownloadManagers();
		for (DownloadManager dm: dms) {
			PEPeerManager pm = dm.getPeerManager();
			if (pm != null) {
				totalDataQueued += pm.getBytesQueuedForUpload();
				totalConnections += pm.getNbPeers() + pm.getNbSeeds();
				totalConQueued 	+= pm.getNbPeersWithUploadQueued();
				totalConBlocked	+= pm.getNbPeersWithUploadBlocked();
				totalConUnchoked += pm.getNbPeersUnchoked();
				totalIn += pm.getNbRemoteTCPConnections() + pm.getNbRemoteUDPConnections() + pm.getNbRemoteUTPConnections();
			}
		}
		connectionLabel.setText(
			MessageText.getString(
					"SpeedView.stats.con_details",
					new String[]{
							String.valueOf(totalConnections) + "[" +MessageText.getString("label.in").toLowerCase() + ":" + totalIn + "]",
							String.valueOf(totalConUnchoked), String.valueOf(totalConQueued), String.valueOf(totalConBlocked) }));
		connectionGraphic.addIntsValue(new int[]{ totalConnections, totalConUnchoked, totalConQueued, totalConBlocked });
		uploadLabel.setText(
			MessageText.getString(
					"SpeedView.stats.upload_details",
					new String[]{ DisplayFormatters.formatByteCountToKiBEtc(totalDataQueued)}));
		uploadGraphic.addIntValue(totalDataQueued);
		uploadGraphic.refresh(false);
		connectionGraphic.refresh(false);
		if (conFolder.getSelectionIndex() == 1) {
			long	now = SystemTime.getMonotonousTime();
			if (now - last_route_update >= 2*1000) {
				last_route_update = now;
				NetworkAdmin na = NetworkAdmin.getSingleton();
				Map<InetAddress,String>		ip_to_name_map		= new HashMap<InetAddress,String>();
				Map<String,RouteInfo>			name_to_route_map 	= new HashMap<String,RouteInfo>();
				RouteInfo udp_info 		= null;
				RouteInfo unknown_info 	= null;
				List<PRUDPPacketHandler> udpHandlers = PRUDPPacketHandlerFactory.getHandlers();
				InetAddress	udp_bind_ip = null;
				for (PRUDPPacketHandler handler: udpHandlers) {
					if (handler.hasPrimordialHandler()) {
						udp_bind_ip = handler.getBindIP();
						if (udp_bind_ip != null) {
							if (udp_bind_ip.isAnyLocalAddress()) {
								udp_bind_ip = null;
							}
						}
					}
				}
				for (DownloadManager dm: dms) {
					PEPeerManager pm = dm.getPeerManager();
					if (pm != null) {
						List<PEPeer> peers = pm.getPeers();
						for (PEPeer p: peers) {
							NetworkConnection nc = PluginCoreUtils.unwrap( p.getPluginConnection());
							boolean done = false;
							if (nc != null) {
								Transport transport = nc.getTransport();
								if (transport != null) {
									if (transport.isTCP()) {
										TransportStartpoint start = transport.getTransportStartpoint();
										if (start != null) {
											InetSocketAddress socket_address = start.getProtocolStartpoint().getAddress();
											if (socket_address != null) {
												InetAddress	address = socket_address.getAddress();
												String name;
												if (address.isAnyLocalAddress()) {
													name = "* (TCP)";
												} else {
													name = ip_to_name_map.get(address);
												}
												if (name == null) {
													name = na.classifyRoute( address);
													ip_to_name_map.put(address, name);
												}
												if (transport.isSOCKS()) {
													name += " (SOCKS)";
												}
												RouteInfo	info = name_to_route_map.get(name);
												if (info == null) {
													info = new RouteInfo(name);
													name_to_route_map.put(name, info);
													route_last_seen.put(name, now);
												}
												info.update(p);
												done = true;
											}
										}
									} else {
										if (udp_bind_ip != null) {
											RouteInfo	info;
											String name = ip_to_name_map.get(udp_bind_ip);
											if (name == null) {
												name = na.classifyRoute( udp_bind_ip);
												ip_to_name_map.put(udp_bind_ip, name);
												info = name_to_route_map.get(name);
												route_last_seen.put(name, now);
												if (info == null) {
													info = new RouteInfo(name);
													name_to_route_map.put(name, info);
												}
											} else {
												info = name_to_route_map.get(name);
											}
											info.update(p);
											done = true;
										} else {
											if (udp_info == null) {
												udp_info 		= new RouteInfo("* (UDP)");
												name_to_route_map.put(udp_info.getName(), udp_info);
												route_last_seen.put(udp_info.getName(), now);
											}
											udp_info.update(p);
											done = true;
										}
									}
								}
							}
							if (!done) {
								if (unknown_info == null) {
									unknown_info 		= new RouteInfo("Pending");
									name_to_route_map.put(unknown_info.getName(), unknown_info);
									route_last_seen.put(unknown_info.getName(), now);
								}
								unknown_info.update(p);
							}
						}
					}
				}
				List<RouteInfo>	rows = new ArrayList<RouteInfo>();
				Iterator<Map.Entry<String,Long>> it = route_last_seen.entrySet().iterator();
				while (it.hasNext()) {
					Map.Entry<String,Long> entry = it.next();
					long	when = entry.getValue();
					if (now - when > 60*1000) {
						it.remove();
					} else if (when != now) {
						rows.add(new RouteInfo( entry.getKey()));
					}
				}
				rows.addAll(name_to_route_map.values());
				Collections.sort(
					 rows,
					 new Comparator<RouteInfo>()
					 {
						public int compare(
							RouteInfo o1,
							RouteInfo o2) {
							String	n1 = o1.getName();
							String	n2 = o2.getName();
								// wildcard and pending to the bottom
							if (n1.startsWith("*") || n1.equals("Pending")) {
								n1 = "zzzz" + n1;
							}
							if (n2.startsWith("*") || n2.equals("Pending")) {
								n2 = "zzzz" + n2;
							}
							return ( n1.compareTo(n2));
						}
					 });
				buildRouteComponent( rows.size());
				for (int i=0;i<rows.size();i++) {
					RouteInfo	info = rows.get(i);
					route_labels[i][0].setText( info.getName());
					route_labels[i][1].setText( info.getIncomingString());
					route_labels[i][2].setText( info.getOutgoingString());
				}
				buildRouteComponent( rows.size());
			}
		}
	}

	private static class
	RouteInfo
	{
		private String			name;
		private RouteInfoRecord	incoming = new RouteInfoRecord();
		private RouteInfoRecord	outgoing = new RouteInfoRecord();

		private RouteInfo(
		String		_name ) {
			name	= _name;
		}

		private String getName() {
			return (name);
		}

		private String getIncomingString() {
			return ( incoming.getString());
		}

		private String getOutgoingString() {
			return ( outgoing.getString());
		}

		private void update(
			PEPeer	peer ) {
			RouteInfoRecord record;

			if (peer.isIncoming()) {

				record = incoming;

			} else {

				record = outgoing;
			}

			record.update(peer);
		}
	}

	private static class
	RouteInfoRecord
	{
		private int	peer_count;
		private int	up_rate;
		private int	down_rate;

		private void update(
			PEPeer	peer ) {
			peer_count++;

			PEPeerStats stats = peer.getStats();

			up_rate 	+= stats.getDataSendRate() + stats.getProtocolSendRate();
			down_rate += stats.getDataReceiveRate() + stats.getProtocolReceiveRate();
		}

		private String getString() {
			if (peer_count == 0) {

				return ("0");
			}

			return ( peer_count + ": up=" +
					DisplayFormatters.formatByteCountToKiBEtcPerSec(up_rate) + ", down=" +
					DisplayFormatters.formatByteCountToKiBEtcPerSec(down_rate));
		}
	}

	private void refreshPingPanel() {
		if (speedManager == null) {
			return;
		}
		if (speedManager.isAvailable()) {// && speedManager.isEnabled()) {
			autoSpeedPanelLayout.topControl = autoSpeedInfoPanel;
			autoSpeedPanel.layout();

			pingGraph.refresh();
			for (int i=0;i<plotViews.length;i++) {

				plotViews[i].refresh();
			}

			for (int i=0;i<zoneViews.length;i++) {

				zoneViews[i].refresh();
			}

		} else {
			autoSpeedPanelLayout.topControl = autoSpeedDisabledPanel;
			autoSpeedPanel.layout();
		}
	}

	/* (non-Javadoc)
	 * @see org.gudy.azureus2.ui.swt.views.stats.PeriodicViewUpdate#periodicUpdate()
	 */
	public void periodicUpdate() {
		if (speedManager == null) {
			return;
		}
		if (speedManager.isAvailable()) {// && speedManager.isEnabled()) {
			SpeedManagerPingSource sources[] = speedManager.getPingSources();
			if (sources.length > 0) {
				int[] pings = new int[sources.length];
				for (int i = 0 ; i < sources.length ; i++) {

					SpeedManagerPingSource source = sources[i];

					if (source != null) {

						int	ping = source.getPingTime();

						ping = Math.min(ping, MAX_DISPLAYED_PING_MILLIS);

						pings[i] = ping;
					}
				}
				pingGraph.addIntsValue(pings);

				if (plotViews != null) {
					for (plotView view: plotViews) {
						if (view != null) {
							view.update();
						}
					}
				}

				if (zoneViews != null) {
					for (zoneView view: zoneViews) {
						if (view != null) {
							view.update();
						}
					}
				}
			}
		}
	}


	protected String getMapperTitle(
		SpeedManagerPingMapper mapper ) {
		if (mapper.isActive()) {

			SpeedManagerLimitEstimate up_1 		= mapper.getEstimatedUploadLimit(false);
			SpeedManagerLimitEstimate up_2 		= mapper.getEstimatedUploadLimit(true);
			SpeedManagerLimitEstimate down_1 	= mapper.getEstimatedDownloadLimit(false);
			SpeedManagerLimitEstimate down_2 	= mapper.getEstimatedDownloadLimit(true);

			return ("ul=" + DisplayFormatters.formatByteCountToKiBEtc(up_1.getBytesPerSec()) + ":" + DisplayFormatters.formatByteCountToKiBEtc(up_2.getBytesPerSec())+
					",dl=" + DisplayFormatters.formatByteCountToKiBEtc(down_1.getBytesPerSec()) + ":" + DisplayFormatters.formatByteCountToKiBEtc(down_2.getBytesPerSec()) +
					",mr=" + DisplayFormatters.formatDecimal( mapper.getCurrentMetricRating(),2));
		}

		return ("");
	}

	class
	plotView
	{
		private SpeedManagerPingMapper	mapper;
		private Plot3D 					plotGraph;

		protected
		plotView(
			SpeedManagerPingMapper	_mapper,
			Canvas					_canvas,
			String[]					_labels,
			ValueFormater[]			_formatters ) {
			mapper	= _mapper;

			plotGraph = new Plot3D(_labels, _formatters);

			plotGraph.setMaxZ(MAX_DISPLAYED_PING_MILLIS);

			plotGraph.initialize(_canvas);
		}

		protected void update() {
			int[][]	history = mapper.getHistory();

			plotGraph.update(history);

			plotGraph.setTitle(getMapperTitle( mapper));
		}

		protected void refresh() {
			plotGraph.refresh(false);
		}

		protected void dispose() {
			plotGraph.dispose();
		}
	}

	class
	zoneView
		implements ParameterListener
	{
		private SpeedManagerPingMapper	mapper;

		private SpeedManagerPingZone[] zones = new SpeedManagerPingZone[0];

		private Canvas			canvas;

		private ValueFormater[] formatters;

		private String[] labels;

		private String	title = "";

		private int 	refresh_count;
		private int 	graphicsUpdate;
		private Point old_size;

		protected Image buffer_image;

		protected
		zoneView(
			SpeedManagerPingMapper		_mapper,
			Canvas 						_canvas,
			String[]						_labels,
			ValueFormater[]				_formatters ) {
			mapper		= _mapper;
			canvas		= _canvas;
			labels		= _labels;
			formatters	= _formatters;

			COConfigurationManager.addAndFireParameterListener("Graphics Update", this);
		}

		public void parameterChanged(
			String name ) {
			graphicsUpdate = COConfigurationManager.getIntParameter(name);

		}

		protected void update() {
			zones	= mapper.getZones();

			title = getMapperTitle(mapper);
		}

		private void refresh() {
			if (canvas.isDisposed()) {

				return;
			}

			Rectangle bounds = canvas.getClientArea();

			if (bounds.height < 30 || bounds.width	< 100 || bounds.width > 2000 || bounds.height > 2000) {

				return;
			}

			boolean size_changed = (old_size == null || old_size.x != bounds.width || old_size.y != bounds.height);

			old_size = new Point(bounds.width,bounds.height);

			refresh_count++;

			if (refresh_count > graphicsUpdate) {

				refresh_count = 0;
			}

			if (refresh_count == 0 || size_changed) {

				if (buffer_image != null && ! buffer_image.isDisposed()) {

					buffer_image.dispose();
				}

				buffer_image = draw(bounds);
			}

			if (buffer_image != null) {

				GC gc = new GC(canvas);

				gc.drawImage(buffer_image, bounds.x, bounds.y);

				gc.dispose();
			}
		}

		private Image
		draw(
		Rectangle	bounds ) {
			final int	PAD_TOP		= 10;
			final int	PAD_BOTTOM	= 10;
			final int	PAD_RIGHT	= 10;
			final int	PAD_LEFT	= 10;

			int usable_width 	= bounds.width - PAD_LEFT - PAD_RIGHT;
			int usable_height	= bounds.height - PAD_TOP - PAD_BOTTOM;

			Image image = new Image(canvas.getDisplay(), bounds);

			GC gc = new GC(image);

			try {
				gc.setAntialias(SWT.ON);
			} catch (Exception e) {
				// Ignore ERROR_NO_GRAPHICS_LIBRARY error or any others
			}

			int font_height 	= gc.getFontMetrics().getHeight();
			int char_width 	= gc.getFontMetrics().getAverageCharWidth();


			Color[] colours = plotViews[0].plotGraph.getColours();

			int	max_x 		= 0;
			int	max_y 		= 0;

			if (zones.length > 0) {

				int	max_metric	= 0;

				for (int i=0;i<zones.length;i++) {

					SpeedManagerPingZone zone = zones[i];

					int	metric 	= zone.getMetric();

					if (metric > 0) {

						max_metric = Math.max(max_metric, metric);

						max_x = Math.max( max_x, zone.getUploadEndBytesPerSec());
						max_y = Math.max( max_y, zone.getDownloadEndBytesPerSec());
					}
				}

				if (max_x > 0 && max_y > 0) {

					double x_ratio = (double)usable_width/max_x;
					double y_ratio = (double)usable_height/max_y;

					List<Object[]>	texts = new ArrayList<Object[]>();

					for (int i=0;i<zones.length;i++) {

						SpeedManagerPingZone zone = zones[i];

						int	metric 	= zone.getMetric();
						int	x1		= zone.getUploadStartBytesPerSec();
						int	y1 		= zone.getDownloadStartBytesPerSec();
						int	x2 		= zone.getUploadEndBytesPerSec();
						int	y2		= zone.getDownloadEndBytesPerSec();

						if (metric > 0) {

							int	colour_index = (int)((float)metric*colours.length/max_metric);

							if (colour_index >= colours.length) {

								colour_index = colours.length-1;
							}

							gc.setBackground(colours[colour_index]);

							int	x 		= PAD_LEFT + (int)(x1*x_ratio);
							int	y 		= PAD_TOP	+ (int)(y1*y_ratio);
							int	width 	= (int)Math.ceil((x2-x1+1)*x_ratio);
							int	height	= (int)Math.ceil((y2-y1+1)*y_ratio);

							int	y_draw = usable_height + PAD_TOP + PAD_TOP - y - height;

							gc.fillRectangle(x, y_draw, width, height);

							int	text_metric = zone.getMetric();

							String text = String.valueOf(metric);

							int	text_width = text.length()*char_width + 4;

							if (width >= text_width && height >= font_height) {


								Rectangle text_rect =
								new Rectangle(
										x + ((width-text_width)/2),
										y_draw + ((height-font_height)/2),
										text_width, font_height);

									// check for overlap with existing and delete older

								Iterator<Object[]> it = texts.iterator();

								while (it.hasNext()) {

									Object[]	old = it.next();

									Rectangle old_coords = (Rectangle)old[1];

									if (old_coords.intersects( text_rect)) {

										it.remove();
									}
								}

								texts.add(new Object[]{ new Integer( text_metric), text_rect });
							}
						}
					}

						// only do the last 100 texts as things get a little cluttered

					int	text_num = texts.size();

					for (int i=(text_num>100?(text_num-100):0);i<text_num;i++) {

						Object[]	entry = texts.get(i);

						String	str = String.valueOf(entry[0]);

						Rectangle	rect = (Rectangle)entry[1];

						gc.drawText(str, rect.x, rect.y, SWT.DRAW_TRANSPARENT);
					}
				}
			}

				// x axis

			int x_axis_left_x = PAD_LEFT;
			int x_axis_left_y = usable_height + PAD_TOP;

			int x_axis_right_x = PAD_LEFT + usable_width;
			int x_axis_right_y = x_axis_left_y;


			gc.drawLine(x_axis_left_x, x_axis_left_y, x_axis_right_x, x_axis_right_y);
			gc.drawLine(usable_width, x_axis_right_y - 4, x_axis_right_x, x_axis_right_y);
			gc.drawLine(usable_width, x_axis_right_y + 4, x_axis_right_x, x_axis_right_y);

			for (int i=1;i<10;i++) {

				int	x = x_axis_left_x + (x_axis_right_x - x_axis_left_x)*i/10;

				gc.drawLine(x, x_axis_left_y, x, x_axis_left_y+4);
			}

			SpeedManagerLimitEstimate le = mapper.getEstimatedUploadLimit(false);

			if (le != null) {

				gc.setForeground(Colors.grey);

				int[][] segs = le.getSegments();

				if (segs.length > 0) {

					int	max_metric 	= 0;
					int	max_pos		= 0;

					for (int i=0;i<segs.length;i++) {

						int[]	seg = segs[i];

						max_metric 	= Math.max(max_metric, seg[0]);
						max_pos 		= Math.max(max_pos, seg[2]);
					}

					double	metric_ratio 	= max_metric==0?1:((float)50/max_metric);
					double	pos_ratio 		= max_pos==0?1:((float)usable_width/max_pos);

					int	prev_x	= 1;
					int	prev_y	= 1;

					for (int i=0;i<segs.length;i++) {

						int[]	seg = segs[i];

						int	next_x 	= (int)((seg[1] + (seg[2]-seg[1])/2)*pos_ratio) + 1;
						int	next_y	= (int)((seg[0])*metric_ratio) + 1;

						gc.drawLine(
								x_axis_left_x + prev_x,
								x_axis_left_y - prev_y,
								x_axis_left_x + next_x,
								x_axis_left_y - next_y);

						prev_x = next_x;
						prev_y = next_y;
					}
				}

				gc.setForeground(Colors.black);
			}

			SpeedManagerLimitEstimate[] bad_up = mapper.getBadUploadHistory();

			if (bad_up.length > 0) {

				gc.setLineWidth(3);

				gc.setForeground(Colors.red);

				for (int i=0;i<bad_up.length;i++) {

					int speed = bad_up[i].getBytesPerSec();

					int	x = max_x == 0?0:(speed * usable_width / max_x);

					gc.drawLine(
							x_axis_left_x + x,
							x_axis_left_y - 0,
							x_axis_left_x + x,
							x_axis_left_y - 10);

				}

				gc.setForeground(Colors.black);

				gc.setLineWidth(1);
			}

			String x_text = labels[0] + " - " + formatters[0].format(max_x+1);

			gc.drawText( 	x_text,
							x_axis_right_x - 20 - x_text.length()*char_width,
							x_axis_right_y - font_height - 2,
							SWT.DRAW_TRANSPARENT);

				// y axis

			int y_axis_bottom_x = PAD_LEFT;
			int y_axis_bottom_y = usable_height + PAD_TOP;

			int y_axis_top_x 	= PAD_LEFT;
			int y_axis_top_y 	= PAD_TOP;

			gc.drawLine(y_axis_bottom_x, y_axis_bottom_y, y_axis_top_x, y_axis_top_y);

			gc.drawLine(y_axis_top_x-4, y_axis_top_y+PAD_TOP,	y_axis_top_x, y_axis_top_y);
			gc.drawLine(y_axis_top_x+4, y_axis_top_y+PAD_TOP,	y_axis_top_x, y_axis_top_y);

			for (int i=1;i<10;i++) {

				int	y = y_axis_bottom_y + (y_axis_top_y - y_axis_bottom_y)*i/10;

				gc.drawLine(y_axis_bottom_x, y, y_axis_bottom_x-4, y);
			}

			le = mapper.getEstimatedDownloadLimit(false);

			if (le != null) {

				gc.setForeground(Colors.grey);

				int[][] segs = le.getSegments();

				if (segs.length > 0) {

					int	max_metric 	= 0;
					int	max_pos		= 0;

					for (int i=0;i<segs.length;i++) {

						int[]	seg = segs[i];

						max_metric 	= Math.max(max_metric, seg[0]);
						max_pos 		= Math.max(max_pos, seg[2]);
					}

					double	metric_ratio 	= max_metric==0?1:((float)50/max_metric);
					double	pos_ratio 		= max_pos==0?1:((float)usable_height/max_pos);

					int	prev_x	= 1;
					int	prev_y	= 1;

					for (int i=0;i<segs.length;i++) {

						int[]	seg = segs[i];

						int	next_x	= (int)((seg[0])*metric_ratio) + 1;
						int	next_y 	= (int)((seg[1] + (seg[2]-seg[1])/2)*pos_ratio) + 1;

						gc.drawLine(
							y_axis_bottom_x + prev_x,
							y_axis_bottom_y - prev_y,
							y_axis_bottom_x + next_x,
							y_axis_bottom_y - next_y);

						prev_x = next_x;
						prev_y = next_y;
					}
				}

				gc.setForeground(Colors.black);
			}

			SpeedManagerLimitEstimate[] bad_down = mapper.getBadDownloadHistory();

			if (bad_down.length > 0) {

				gc.setForeground(Colors.red);

				gc.setLineWidth(3);

				for (int i=0;i<bad_down.length;i++) {

					int speed = bad_down[i].getBytesPerSec();

					int	y = max_y==0?0:(speed * usable_height / max_y);

					gc.drawLine(
								y_axis_bottom_x + 0,
								y_axis_bottom_y - y,
							y_axis_bottom_x + 10,
							y_axis_bottom_y - y);
				}

				gc.setForeground(Colors.black);

				gc.setLineWidth(1);
			}

			String	y_text = labels[1] + " - " + formatters[1].format(max_y+1);

			gc.drawText(y_text, y_axis_top_x+4, y_axis_top_y + 2, SWT.DRAW_TRANSPARENT);

			gc.drawText(title, ( bounds.width - title.length()*char_width )/2, 1, SWT.DRAW_TRANSPARENT);

			gc.dispose();

			return (image);
		}

		protected void dispose() {
			if (buffer_image != null && ! buffer_image.isDisposed()) {

				buffer_image.dispose();
			}

			COConfigurationManager.removeParameterListener("Graphics Update",this);
		}
	}

	public static class limitToTextHelper {
		
		String	msg_text_unknown;
		String	msg_text_estimate;
		String	msg_text_choke_estimate;
		String	msg_text_measured_min;
		String	msg_text_measured;
		String	msg_text_manual;
		String	msg_unlimited;
		String[]	setable_types;
		
		public limitToTextHelper() {
			msg_text_unknown			= MessageText.getString("SpeedView.stats.unknown");
			msg_text_estimate			= MessageText.getString("SpeedView.stats.estimate");
			msg_text_choke_estimate	= MessageText.getString("SpeedView.stats.estimatechoke");
			msg_text_measured			= MessageText.getString("SpeedView.stats.measured");
			msg_text_measured_min		= MessageText.getString("SpeedView.stats.measuredmin");
			msg_text_manual			= MessageText.getString("SpeedView.stats.manual");
			msg_unlimited			= MessageText.getString("ConfigView.unlimited");
			setable_types =	new String[]{ "", msg_text_estimate, msg_text_measured, msg_text_manual };
		}
		
		public String[] getSettableTypes() {
			return (setable_types);
		}
		
		public String getSettableType(SpeedManagerLimitEstimate limit) {
			float type = limit.getEstimateType();
			String	text;
			if (type == SpeedManagerLimitEstimate.TYPE_UNKNOWN) {
				text = "";
			} else if (type == SpeedManagerLimitEstimate.TYPE_MANUAL) {
				text = msg_text_manual;
			} else if (type == SpeedManagerLimitEstimate.TYPE_MEASURED) {
				text = msg_text_measured;
			} else if (type == SpeedManagerLimitEstimate.TYPE_MEASURED_MIN) {
				text = msg_text_measured;
			} else if (type == SpeedManagerLimitEstimate.TYPE_CHOKE_ESTIMATED) {
				text = msg_text_estimate;
			} else {
				text = msg_text_estimate;
			}
			return (text);			
		}
		
		public String typeToText(float type) {
			String	text;
			if (type == SpeedManagerLimitEstimate.TYPE_UNKNOWN) {
				text = msg_text_unknown;
			} else if (type == SpeedManagerLimitEstimate.TYPE_MANUAL) {
				text = msg_text_manual;
			} else if (type == SpeedManagerLimitEstimate.TYPE_MEASURED) {
				text = msg_text_measured;
			} else if (type == SpeedManagerLimitEstimate.TYPE_MEASURED_MIN) {
				text = msg_text_measured_min;
			} else if (type == SpeedManagerLimitEstimate.TYPE_CHOKE_ESTIMATED) {
				text = msg_text_choke_estimate;
			} else {
				text = msg_text_estimate;
			}
			return (text);
		}
		
		public float textToType(String text) {
			if (text.equals(msg_text_estimate)) {
				return (SpeedManagerLimitEstimate.TYPE_ESTIMATED);
			} else if (text.equals(msg_text_choke_estimate)) {
				return (SpeedManagerLimitEstimate.TYPE_CHOKE_ESTIMATED);
			} else if (text.equals(msg_text_measured)) {
				return (SpeedManagerLimitEstimate.TYPE_MEASURED);
			} else if (text.equals(msg_text_manual)) {
				return (SpeedManagerLimitEstimate.TYPE_MANUAL);
			} else {
				return (SpeedManagerLimitEstimate.TYPE_UNKNOWN);
			}
		}
		
		public String getLimitText(SpeedManagerLimitEstimate limit) {
			float type = limit.getEstimateType();
			String	text = typeToText(type);
			int	l = limit.getBytesPerSec();
			if (l == 0) {
				return (msg_unlimited + " (" + text + ")");
			} else {
				return (DisplayFormatters.formatByteCountToKiBEtcPerSec( l) + " (" + text + ")");
			}
		}
		
		public String getUnlimited() {
			return (msg_unlimited);
		}
	}

	public boolean eventOccurred(UISWTViewEvent event) {
		switch (event.getType()) {
			case UISWTViewEvent.TYPE_CREATE:
				swtView = (UISWTView)event.getData();
				swtView.setTitle(MessageText.getString(MSGID_PREFIX + ".title.full"));
				break;

			case UISWTViewEvent.TYPE_DESTROY:
				delete();
				break;

			case UISWTViewEvent.TYPE_INITIALIZE:
				initialize((Composite)event.getData());
				break;

			case UISWTViewEvent.TYPE_LANGUAGEUPDATE:
				Messages.updateLanguageForControl(getComposite());
				break;

			case UISWTViewEvent.TYPE_DATASOURCE_CHANGED:
				break;

			case UISWTViewEvent.TYPE_FOCUSGAINED:
				// weird layout issue with general panel - switch away from view and then back leaves bottom line not rendering - this fixes it
				if (generalPanel != null) {
					generalPanel.layout(true, true);
				}
				break;

			case UISWTViewEvent.TYPE_REFRESH:
				refresh();
				break;

			case StatsView.EVENT_PERIODIC_UPDATE:
				periodicUpdate();
				break;
		}

		return true;
	}
}

