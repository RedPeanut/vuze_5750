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

import java.io.UnsupportedEncodingException;
import java.util.Arrays;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.util.ByteFormatter;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.DisplayFormatters;
import org.gudy.azureus2.core3.util.TimeFormatter;
import org.gudy.azureus2.plugins.PluginInterface;
import org.gudy.azureus2.ui.swt.Messages;
import org.gudy.azureus2.ui.swt.Utils;
import org.gudy.azureus2.ui.swt.components.Legend;
import org.gudy.azureus2.ui.swt.components.graphics.PingGraphic;
import org.gudy.azureus2.ui.swt.components.graphics.SpeedGraphic;
import org.gudy.azureus2.ui.swt.mainwindow.Colors;
import org.gudy.azureus2.ui.swt.plugins.UISWTView;
import org.gudy.azureus2.ui.swt.plugins.UISWTViewEvent;
import org.gudy.azureus2.ui.swt.plugins.UISWTViewEventListener;

import com.aelitis.azureus.core.AzureusCore;
import com.aelitis.azureus.core.AzureusCoreFactory;
import com.aelitis.azureus.core.AzureusCoreRunningListener;
import com.aelitis.azureus.core.dht.DHT;
import com.aelitis.azureus.core.dht.DHTStorageAdapter;
import com.aelitis.azureus.core.dht.control.DHTControlActivity;
import com.aelitis.azureus.core.dht.control.DHTControlListener;
import com.aelitis.azureus.core.dht.control.DHTControlStats;
import com.aelitis.azureus.core.dht.db.DHTDBStats;
import com.aelitis.azureus.core.dht.nat.DHTNATPuncher;
import com.aelitis.azureus.core.dht.router.DHTRouterStats;
import com.aelitis.azureus.core.dht.transport.DHTTransport;
import com.aelitis.azureus.core.dht.transport.DHTTransportFullStats;
import com.aelitis.azureus.core.dht.transport.DHTTransportStats;
import com.aelitis.azureus.plugins.dht.DHTPlugin;

import hello.util.Log;
import hello.util.SingleCounter0;

/**
 *
 */
public class DHTView implements UISWTViewEventListener {

	private static String TAG = DHTView.class.getSimpleName();

	public static final int DHT_TYPE_MAIN = DHT.NW_MAIN;
	public static final int DHT_TYPE_CVS = DHT.NW_CVS;
	public static final int DHT_TYPE_MAIN_V6 = DHT.NW_MAIN_V6;
	public static final String MSGID_PREFIX = "DHTView";

	public static Color[] rttColours = new Color[] { Colors.grey, Colors.fadedGreen,Colors.fadedRed };

	private boolean autoDht;
	DHT dht;
	Composite panel;

	String	strYes;
	String	strNo;

	Label lblUpTime,lblNumberOfUsers;
	Label lblNodes,lblLeaves;
	Label lblContacts,lblReplacements,lblLive,lblUnknown,lblDying;
	Label lblSkew, lblRendezvous, lblReachable;
	Label lblKeys,lblValues,lblSize;
	Label lblLocal,lblDirect,lblIndirect;
	Label lblDivFreq,lblDivSize;

	Label lblReceivedPackets,lblReceivedBytes;
	Label lblSentPackets,lblSentBytes;

	Label lblPings[] = new Label[4];
	Label lblFindNodes[] = new Label[4];
	Label lblFindValues[] = new Label[4];
	Label lblStores[] = new Label[4];
	Label lblData[] = new Label[4];

	Canvas	in,out,rtt;
	SpeedGraphic inGraph, outGraph;
	PingGraphic rttGraph;

	boolean activityChanged;
	DHTControlListener controlListener;
	Table activityTable;
	DHTControlActivity[] activities;

	private int dhtType;
	protected AzureusCore core;

	public DHTView() {
		this(true);
	}

	public DHTView(boolean _auto_dht) {
		autoDht = _auto_dht;
		inGraph = SpeedGraphic.getInstance();
		outGraph = SpeedGraphic.getInstance();
		rttGraph = PingGraphic.getInstance();

		rttGraph.setColors(rttColours);
		rttGraph.setExternalAverage(true);
	}

	private void init(AzureusCore core) {

		try {
			PluginInterface dhtPI = core.getPluginManager().getPluginInterfaceByClass(DHTPlugin.class);

			if (dhtPI == null)
				return;

			DHT[] dhts = ((DHTPlugin) dhtPI.getPlugin()).getDHTs();
			for (int i = 0; i < dhts.length; i++) {
				if (dhts[i].getTransport().getNetwork() == dhtType) {
					dht = dhts[i];
					break;
				}
			}

			if (dht == null)
				return;

			controlListener = new DHTControlListener() {
				public void activityChanged(DHTControlActivity activity, int type) {
					activityChanged = true;
				}
			};
			dht.getControl().addListener(controlListener);

		} catch (Exception e) {
			Debug.printStackTrace(e);
		}
	}

	public void setDHT(DHT _dht) {

		if (dht == null) {
			dht	= _dht;

			controlListener = new DHTControlListener() {
				public void activityChanged(DHTControlActivity activity,int type) {
					activityChanged = true;
				}
			};
			dht.getControl().addListener(controlListener);

		} else if (dht == _dht) {

		} else {
			Debug.out("Not Supported ");
		}
	}

	public void initialize(Composite composite) {
		
		//Log.d(TAG, "initialize() is called...");
		//new Throwable().printStackTrace();
		
		if (autoDht) {
			AzureusCoreFactory.addCoreRunningListener(new AzureusCoreRunningListener() {
				public void azureusCoreRunning(AzureusCore core) {
					DHTView.this.core = core;
					init(core);
				}
			});
		}

		panel = new Composite(composite,SWT.NULL);
		GridLayout layout = new GridLayout();
		layout.numColumns = 2;
		panel.setLayout(layout);

		strYes	= MessageText.getString("Button.yes").replaceAll("&", "");
		strNo	= MessageText.getString("Button.no").replaceAll("&", "");

		initialiseGeneralGroup();
		initialiseDBGroup();

		initialiseTransportDetailsGroup();
		initialiseOperationDetailsGroup();

		initialiseActivityGroup();
	}

	private void initialiseGeneralGroup() {
		Group gGeneral = new Group(panel,SWT.NONE);
		Messages.setLanguageText(gGeneral, "DHTView.general.title");

		GridData data = new GridData();
		data.verticalAlignment = SWT.BEGINNING;
		data.widthHint = 350;
		Utils.setLayoutData(gGeneral, data);

		GridLayout layout = new GridLayout();
		layout.numColumns = 6;
		gGeneral.setLayout(layout);

		// row1
		Label label = new Label(gGeneral,SWT.NONE);
		Messages.setLanguageText(label,"DHTView.general.uptime");

		lblUpTime = new Label(gGeneral,SWT.NONE);
		Utils.setLayoutData(lblUpTime, new GridData(SWT.FILL,SWT.TOP,true,false));

		label = new Label(gGeneral,SWT.NONE);
		Messages.setLanguageText(label,"DHTView.general.users");

		lblNumberOfUsers = new Label(gGeneral,SWT.NONE);
		Utils.setLayoutData(lblNumberOfUsers, new GridData(SWT.FILL,SWT.TOP,true,false));

		label = new Label(gGeneral,SWT.NONE);
		Messages.setLanguageText(label,"DHTView.general.reachable");

		lblReachable = new Label(gGeneral,SWT.NONE);
		Utils.setLayoutData(lblReachable, new GridData(SWT.FILL,SWT.TOP,true,false));

		// row 2
		label = new Label(gGeneral,SWT.NONE);
		Messages.setLanguageText(label,"DHTView.general.nodes");

		lblNodes = new Label(gGeneral,SWT.NONE);
		Utils.setLayoutData(lblNodes, new GridData(SWT.FILL,SWT.TOP,true,false));

		label = new Label(gGeneral,SWT.NONE);
		Messages.setLanguageText(label,"DHTView.general.leaves");

		lblLeaves = new Label(gGeneral,SWT.NONE);
		Utils.setLayoutData(lblLeaves, new GridData(SWT.FILL,SWT.TOP,true,false));

		label = new Label(gGeneral,SWT.NONE);
		Messages.setLanguageText(label,"DHTView.general.rendezvous");

		lblRendezvous = new Label(gGeneral,SWT.NONE);
		Utils.setLayoutData(lblRendezvous, new GridData(SWT.FILL,SWT.TOP,true,false));

		// row 3
		label = new Label(gGeneral,SWT.NONE);
		Messages.setLanguageText(label,"DHTView.general.contacts");

		lblContacts = new Label(gGeneral,SWT.NONE);
		Utils.setLayoutData(lblContacts, new GridData(SWT.FILL,SWT.TOP,true,false));

		label = new Label(gGeneral,SWT.NONE);
		Messages.setLanguageText(label,"DHTView.general.replacements");

		lblReplacements = new Label(gGeneral,SWT.NONE);
		Utils.setLayoutData(lblReplacements, new GridData(SWT.FILL,SWT.TOP,true,false));

		label = new Label(gGeneral,SWT.NONE);
		Messages.setLanguageText(label,"DHTView.general.live");

		lblLive= new Label(gGeneral,SWT.NONE);
		Utils.setLayoutData(lblLive, new GridData(SWT.FILL,SWT.TOP,true,false));

		// row 4
		label = new Label(gGeneral,SWT.NONE);
		Messages.setLanguageText(label,"DHTView.general.skew");

		lblSkew= new Label(gGeneral,SWT.NONE);
		Utils.setLayoutData(lblSkew, new GridData(SWT.FILL,SWT.TOP,true,false));

		label = new Label(gGeneral,SWT.NONE);
		Messages.setLanguageText(label,"DHTView.general.unknown");

		lblUnknown = new Label(gGeneral,SWT.NONE);
		Utils.setLayoutData(lblUnknown, new GridData(SWT.FILL,SWT.TOP,true,false));

		label = new Label(gGeneral,SWT.NONE);
		Messages.setLanguageText(label,"DHTView.general.dying");

		lblDying = new Label(gGeneral,SWT.NONE);
		Utils.setLayoutData(lblDying, new GridData(SWT.FILL,SWT.TOP,true,false));
	}

	private void initialiseDBGroup() {
		Group gDB = new Group(panel,SWT.NONE);
		Messages.setLanguageText(gDB,"DHTView.db.title");

		GridData data = new GridData(GridData.FILL_HORIZONTAL);
		data.verticalAlignment = SWT.FILL;
		Utils.setLayoutData(gDB, data);

		GridLayout layout = new GridLayout();
		layout.numColumns = 6;
		layout.makeColumnsEqualWidth = true;
		gDB.setLayout(layout);

		Label label = new Label(gDB,SWT.NONE);
		Messages.setLanguageText(label,"DHTView.db.keys");

		lblKeys = new Label(gDB,SWT.NONE);
		Utils.setLayoutData(lblKeys, new GridData(SWT.FILL,SWT.TOP,true,false));

		label = new Label(gDB,SWT.NONE);
		Messages.setLanguageText(label,"DHTView.db.values");

		lblValues = new Label(gDB,SWT.NONE);
		Utils.setLayoutData(lblValues, new GridData(SWT.FILL,SWT.TOP,true,false));

		label = new Label(gDB,SWT.NONE);
		Messages.setLanguageText(label,"TableColumn.header.size");

		lblSize = new Label(gDB,SWT.NONE);
		Utils.setLayoutData(lblSize, new GridData(SWT.FILL,SWT.TOP,true,false));

		label = new Label(gDB,SWT.NONE);
		Messages.setLanguageText(label,"DHTView.db.local");

		lblLocal = new Label(gDB,SWT.NONE);
		Utils.setLayoutData(lblLocal, new GridData(SWT.FILL,SWT.TOP,true,false));

		label = new Label(gDB,SWT.NONE);
		Messages.setLanguageText(label,"DHTView.db.direct");

		lblDirect = new Label(gDB,SWT.NONE);
		Utils.setLayoutData(lblDirect, new GridData(SWT.FILL,SWT.TOP,true,false));

		label = new Label(gDB,SWT.NONE);
		Messages.setLanguageText(label,"DHTView.db.indirect");

		lblIndirect = new Label(gDB,SWT.NONE);
		Utils.setLayoutData(lblIndirect, new GridData(SWT.FILL,SWT.TOP,true,false));


		label = new Label(gDB,SWT.NONE);
		Messages.setLanguageText(label,"DHTView.db.divfreq");

		lblDivFreq = new Label(gDB,SWT.NONE);
		Utils.setLayoutData(lblDivFreq, new GridData(SWT.FILL,SWT.TOP,true,false));

		label = new Label(gDB,SWT.NONE);
		Messages.setLanguageText(label,"DHTView.db.divsize");

		lblDivSize = new Label(gDB,SWT.NONE);
		Utils.setLayoutData(lblDivSize, new GridData(SWT.FILL,SWT.TOP,true,false));
	}

	private void initialiseTransportDetailsGroup() {
		Group gTransport = new Group(panel,SWT.NONE);
		Messages.setLanguageText(gTransport,"DHTView.transport.title");

		GridData data = new GridData(GridData.FILL_VERTICAL);
		data.widthHint = 350;
		data.verticalSpan = 2;
		Utils.setLayoutData(gTransport, data);

		GridLayout layout = new GridLayout();
		layout.numColumns = 3;
		layout.makeColumnsEqualWidth = true;
		gTransport.setLayout(layout);

		Label label = new Label(gTransport,SWT.NONE);

		label = new Label(gTransport,SWT.NONE);
		Messages.setLanguageText(label,"DHTView.transport.packets");
		Utils.setLayoutData(label, new GridData(SWT.FILL,SWT.TOP,true,false));

		label = new Label(gTransport,SWT.NONE);
		Messages.setLanguageText(label,"DHTView.transport.bytes");
		Utils.setLayoutData(label, new GridData(SWT.FILL,SWT.TOP,true,false));

		label = new Label(gTransport,SWT.NONE);
		Messages.setLanguageText(label,"DHTView.transport.received");

		lblReceivedPackets = new Label(gTransport,SWT.NONE);
		Utils.setLayoutData(lblReceivedPackets, new GridData(SWT.FILL,SWT.TOP,true,false));

		lblReceivedBytes = new Label(gTransport,SWT.NONE);
		Utils.setLayoutData(lblReceivedBytes, new GridData(SWT.FILL,SWT.TOP,true,false));

		label = new Label(gTransport,SWT.NONE);
		Messages.setLanguageText(label,"DHTView.transport.sent");

		lblSentPackets = new Label(gTransport,SWT.NONE);
		Utils.setLayoutData(lblSentPackets, new GridData(SWT.FILL,SWT.TOP,true,false));

		lblSentBytes = new Label(gTransport,SWT.NONE);
		Utils.setLayoutData(lblSentBytes, new GridData(SWT.FILL,SWT.TOP,true,false));

		label = new Label(gTransport,SWT.NONE);
		Messages.setLanguageText(label,"DHTView.transport.in");
		data = new GridData();
		data.horizontalSpan = 3;
		Utils.setLayoutData(label, data);

		in = new Canvas(gTransport,SWT.NO_BACKGROUND);
		data = new GridData(GridData.FILL_BOTH);
		data.horizontalSpan = 3;
		Utils.setLayoutData(in, data);
		inGraph.initialize(in);

		label = new Label(gTransport,SWT.NONE);
		Messages.setLanguageText(label,"DHTView.transport.out");
		data = new GridData();
		data.horizontalSpan = 3;
		Utils.setLayoutData(label, data);

		out = new Canvas(gTransport,SWT.NO_BACKGROUND);
		data = new GridData(GridData.FILL_BOTH);
		data.horizontalSpan = 3;
		Utils.setLayoutData(out, data);
		outGraph.initialize(out);

		label = new Label(gTransport,SWT.NONE);
		Messages.setLanguageText(label,"DHTView.transport.rtt");
		data = new GridData();
		data.horizontalSpan = 3;
		Utils.setLayoutData(label, data);

		rtt = new Canvas(gTransport,SWT.NO_BACKGROUND);
		data = new GridData(GridData.FILL_BOTH);
		data.horizontalSpan = 3;
		Utils.setLayoutData(rtt, data);
		rttGraph.initialize(rtt);

		data = new GridData(GridData.FILL_HORIZONTAL);
		data.horizontalSpan = 3;

		Legend.createLegendComposite(
				gTransport,
				rttColours,
				new String[] {
					"DHTView.rtt.legend.average",
					"DHTView.rtt.legend.best",
					"DHTView.rtt.legend.worst" },
				data);
	}

	private void initialiseOperationDetailsGroup() {
		Group gOperations = new Group(panel,SWT.NONE);
		Messages.setLanguageText(gOperations,"DHTView.operations.title");
		Utils.setLayoutData(gOperations, new GridData(SWT.FILL,SWT.BEGINNING,true,false));

		GridLayout layout = new GridLayout();
		layout.numColumns = 5;
		layout.makeColumnsEqualWidth = true;
		gOperations.setLayout(layout);


		Label label = new Label(gOperations,SWT.NONE);

		label = new Label(gOperations,SWT.NONE);
		Messages.setLanguageText(label,"DHTView.operations.sent");
		Utils.setLayoutData(label, new GridData(SWT.FILL,SWT.TOP,true,false));

		label = new Label(gOperations,SWT.NONE);
		Messages.setLanguageText(label,"DHTView.operations.ok");
		Utils.setLayoutData(label, new GridData(SWT.FILL,SWT.TOP,true,false));

		label = new Label(gOperations,SWT.NONE);
		Messages.setLanguageText(label,"DHTView.operations.failed");
		Utils.setLayoutData(label, new GridData(SWT.FILL,SWT.TOP,true,false));

		label = new Label(gOperations,SWT.NONE);
		Messages.setLanguageText(label,"DHTView.operations.received");
		Utils.setLayoutData(label, new GridData(SWT.FILL,SWT.TOP,true,false));


		label = new Label(gOperations,SWT.NONE);
		Messages.setLanguageText(label,"DHTView.operations.ping");

		for (int i = 0 ; i < 4 ; i++) {
			lblPings[i] = new Label(gOperations,SWT.NONE);
			Utils.setLayoutData(lblPings[i], new GridData(SWT.FILL,SWT.TOP,true,false));
		}


		label = new Label(gOperations,SWT.NONE);
		Messages.setLanguageText(label,"DHTView.operations.findNode");

		for (int i = 0 ; i < 4 ; i++) {
			lblFindNodes[i] = new Label(gOperations,SWT.NONE);
			Utils.setLayoutData(lblFindNodes[i], new GridData(SWT.FILL,SWT.TOP,true,false));
		}


		label = new Label(gOperations,SWT.NONE);
		Messages.setLanguageText(label,"DHTView.operations.findValue");

		for (int i = 0 ; i < 4 ; i++) {
			lblFindValues[i] = new Label(gOperations,SWT.NONE);
			Utils.setLayoutData(lblFindValues[i], new GridData(SWT.FILL,SWT.TOP,true,false));
		}


		label = new Label(gOperations,SWT.NONE);
		Messages.setLanguageText(label,"DHTView.operations.store");

		for (int i = 0 ; i < 4 ; i++) {
			lblStores[i] = new Label(gOperations,SWT.NONE);
			Utils.setLayoutData(lblStores[i], new GridData(SWT.FILL,SWT.TOP,true,false));
		}

		label = new Label(gOperations,SWT.NONE);
		Messages.setLanguageText(label,"DHTView.operations.data");

		for (int i = 0 ; i < 4 ; i++) {
			lblData[i] = new Label(gOperations,SWT.NONE);
			Utils.setLayoutData(lblData[i], new GridData(SWT.FILL,SWT.TOP,true,false));
		}
	}

	private void initialiseActivityGroup() {
		Group gActivity = new Group(panel,SWT.NONE);
		Messages.setLanguageText(gActivity,"DHTView.activity.title");
		Utils.setLayoutData(gActivity, new GridData(SWT.FILL,SWT.FILL,true,true));
		gActivity.setLayout(new GridLayout());

		activityTable = new Table(gActivity,SWT.VIRTUAL | SWT.BORDER | SWT.FULL_SELECTION | SWT.SINGLE);
		Utils.setLayoutData(activityTable, new GridData(GridData.FILL_BOTH));

		final TableColumn colStatus =	new TableColumn(activityTable,SWT.LEFT);
		Messages.setLanguageText(colStatus,"DHTView.activity.status");
		colStatus.setWidth(Utils.adjustPXForDPI(80));

		final TableColumn colType =	new TableColumn(activityTable,SWT.LEFT);
		Messages.setLanguageText(colType,"DHTView.activity.type");
		colType.setWidth(Utils.adjustPXForDPI(80));

		final TableColumn colName =	new TableColumn(activityTable,SWT.LEFT);
		Messages.setLanguageText(colName,"DHTView.activity.target");
		colName.setWidth(Utils.adjustPXForDPI(80));

		final TableColumn colDetails =	new TableColumn(activityTable,SWT.LEFT);
		Messages.setLanguageText(colDetails,"DHTView.activity.details");
		colDetails.setWidth(Utils.adjustPXForDPI(300));
		colDetails.setResizable(false);


		activityTable.setHeaderVisible(true);
		Listener computeLastRowWidthListener = new Listener() {
			// inUse flag to prevent a SWT stack overflow.	For some reason
			// the setWidth call was triggering a resize.
			boolean inUse = false;
			public void handleEvent(Event event) {
				if (inUse) {
					return;
				}

				inUse = true;
			 	try {
					if (activityTable == null || activityTable.isDisposed()) return;
					int totalWidth = activityTable.getClientArea().width;
					int remainingWidth = totalWidth
																 - colStatus.getWidth()
																 - colType.getWidth()
																 - colName.getWidth();
					if (remainingWidth > 0)
						colDetails.setWidth(remainingWidth);

			 	} finally {
					inUse = false;
				}
			}
		};
		activityTable.addListener(SWT.Resize, computeLastRowWidthListener);
		colStatus.addListener(SWT.Resize,computeLastRowWidthListener);
		colType.addListener(SWT.Resize,computeLastRowWidthListener);
		colName.addListener(SWT.Resize,computeLastRowWidthListener);

		activityTable.addListener(SWT.SetData, new Listener() {
			public void handleEvent(Event event) {
				TableItem item = (TableItem) event.item;
				int index = activityTable.indexOf (item);
				item.setText (0, MessageText.getString("DHTView.activity.status." + activities[index].isQueued()));
				item.setText (1, MessageText.getString("DHTView.activity.type." + activities[index].getType()));
				item.setText (2, ByteFormatter.nicePrint(activities[index].getTarget()));
				item.setText (3, activities[index].getDescription());
			}
		});

	}


	public void delete() {
		Utils.disposeComposite(panel);
		if (dht != null) {
			dht.getControl().removeListener(controlListener);
		}
		outGraph.dispose();
		inGraph.dispose();
		rttGraph.dispose();
	}

	private String getTitleID() {
		if (dhtType == DHT_TYPE_MAIN) {
			return ("DHTView.title.full");
		} else if (dhtType == DHT_TYPE_CVS) {
			return ("DHTView.title.fullcvs");
		} else {
			return ("DHTView.title.full_v6");
		}
	}

	private Composite getComposite() {
		return panel;
	}

	private void refresh() {
		// need to do these here otherwise they sit in an unpainted state
		inGraph.refresh(false);
		outGraph.refresh(false);
		rttGraph.refresh();

		if (dht == null) {
			if (core != null) {
				// keep trying until dht is avail
				init(core);
			}
			return;
		}

		refreshGeneral();
		refreshDB();
		refreshTransportDetails();
		refreshOperationDetails();
		refreshActivity();
	}

	private void refreshGeneral() {
		DHTControlStats controlStats = dht.getControl().getStats();
		DHTRouterStats routerStats = dht.getRouter().getStats();
		DHTTransport transport = dht.getTransport();
		DHTTransportStats transportStats = transport.getStats();
		lblUpTime.setText(TimeFormatter.format(controlStats.getRouterUptime() / 1000));
		lblNumberOfUsers.setText("" + controlStats.getEstimatedDHTSize());
		int percent = transportStats.getRouteablePercentage();
		lblReachable.setText(
				(transport.isReachable() ? strYes : strNo)
				+ (percent==-1 ? "" : (" " + percent + "%"))
		);

		DHTNATPuncher puncher = dht.getNATPuncher();

		String puncher_str;

		if (puncher == null) {
			puncher_str = "";
		} else {
			puncher_str = puncher.operational() ? strYes : strNo;
		}

		lblRendezvous.setText(transport.isReachable() ? "" : puncher_str);
		long[] stats = routerStats.getStats();
		lblNodes.setText("" + stats[DHTRouterStats.ST_NODES]);
		lblLeaves.setText("" + stats[DHTRouterStats.ST_LEAVES]);
		lblContacts.setText("" + stats[DHTRouterStats.ST_CONTACTS]);
		lblReplacements.setText("" + stats[DHTRouterStats.ST_REPLACEMENTS]);
		lblLive.setText("" + stats[DHTRouterStats.ST_CONTACTS_LIVE]);
		lblUnknown.setText("" + stats[DHTRouterStats.ST_CONTACTS_UNKNOWN]);
		lblDying.setText("" + stats[DHTRouterStats.ST_CONTACTS_DEAD]);

		long skew_average = transportStats.getSkewAverage();

		lblSkew.setText(skew_average == 0
				? "" : (skew_average<0?"-":"") + TimeFormatter.format100ths(Math.abs(skew_average))
		);
	}

	private int refreshIter = 0;
	private UISWTView swtView;

	private void refreshDB() {
		if (refreshIter == 0) {
		DHTDBStats dbStats = dht.getDataBase().getStats();
			lblKeys.setText("" + dbStats.getKeyCount() + " (" + dbStats.getLocalKeyCount() + ")");
			int[] stats = dbStats.getValueDetails();
			lblValues.setText("" + stats[DHTDBStats.VD_VALUE_COUNT]);
			lblSize.setText(DisplayFormatters.formatByteCountToKiBEtc(dbStats.getSize()));
			lblDirect.setText(DisplayFormatters.formatByteCountToKiBEtc( stats[DHTDBStats.VD_DIRECT_SIZE]));
			lblIndirect.setText(DisplayFormatters.formatByteCountToKiBEtc( stats[DHTDBStats.VD_INDIRECT_SIZE]));
			lblLocal.setText(DisplayFormatters.formatByteCountToKiBEtc( stats[DHTDBStats.VD_LOCAL_SIZE]));

			DHTStorageAdapter sa = dht.getStorageAdapter();

			String rem_freq;
			String rem_size;

			if (sa == null) {
				rem_freq = "-";
				rem_size = "-";
			} else {
				rem_freq = "" + sa.getRemoteFreqDivCount();
				rem_size = "" + sa.getRemoteSizeDivCount();
			}

			lblDivFreq.setText("" + stats[DHTDBStats.VD_DIV_FREQ] + " (" + rem_freq + ")");
			lblDivSize.setText("" + stats[DHTDBStats.VD_DIV_SIZE] + " (" + rem_size + ")");
		} else {
			refreshIter++;
			if (refreshIter == 100) refreshIter = 0;
		}

	}

	private void refreshTransportDetails() {
		DHTTransportStats	 transportStats = dht.getTransport().getStats();
		lblReceivedBytes.setText(DisplayFormatters.formatByteCountToKiBEtc(transportStats.getBytesReceived()));
		lblSentBytes.setText(DisplayFormatters.formatByteCountToKiBEtc(transportStats.getBytesSent()));
		lblReceivedPackets.setText("" + transportStats.getPacketsReceived());
		lblSentPackets.setText("" + transportStats.getPacketsSent());
	}

	private void refreshOperationDetails() {
		DHTTransportStats transportStats = dht.getTransport().getStats();
		long[] pings = transportStats.getPings();
		for (int i = 0 ; i < 4 ; i++) {
			lblPings[i].setText("" + pings[i]);
		}

		long[] findNodes = transportStats.getFindNodes();
		for (int i = 0 ; i < 4 ; i++) {
			lblFindNodes[i].setText("" + findNodes[i]);
		}

		long[] findValues = transportStats.getFindValues();
		for (int i = 0 ; i < 4 ; i++) {
			lblFindValues[i].setText("" + findValues[i]);
		}

		long[] stores 	= transportStats.getStores();
		long[] qstores 	= transportStats.getQueryStores();

		for (int i = 0 ; i < 4 ; i++) {
			lblStores[i].setText("" + stores[i] + " (" + qstores[i] + ")");
		}
		long[] data = transportStats.getData();
		for (int i = 0 ; i < 4 ; i++) {
			lblData[i].setText("" + data[i]);
		}
	}

	private void refreshActivity() {
		if (activityChanged) {
			activityChanged = false;
			activities = dht.getControl().getActivities();
			activityTable.setItemCount(activities.length);
			activityTable.clearAll();
			//Dunno if still needed?
			activityTable.redraw();
		}
	}

	public void periodicUpdate() {
		if (dht == null) return;
		DHTTransportFullStats fullStats = dht.getTransport().getLocalContact().getStats();
		if (fullStats != null) {
			inGraph.addIntValue((int)fullStats.getAverageBytesReceived());
			outGraph.addIntValue((int)fullStats.getAverageBytesSent());
		}
		DHTTransportStats stats = dht.getTransport().getStats();
		int[] rtts = stats.getRTTHistory().clone();
		Arrays.sort(rtts);
		int	rtt_total = 0;
		int	rtt_num		= 0;
		int	start = 0;
		for (int rtt: rtts) {
			if (rtt > 0) {
				rtt_total += rtt;
				rtt_num++;
			} else {
				start++;
			}
		}
		int	average = 0;
		int	best	= 0;
		int worst	= 0;

		if (rtt_num > 0) {
			average = rtt_total/rtt_num;
		}
		int chunk = rtt_num/3;
		int	max_best 	= start+chunk;
		int min_worst	= rtts.length-1-chunk;
		int	worst_total = 0;
		int	worst_num	= 0;
		int best_total	= 0;
		int	best_num	= 0;
		for ( int i=start;i<rtts.length;i++) {
			if (i < max_best) {
				best_total+=rtts[i];
				best_num++;
			} else if (i > min_worst) {
				worst_total+=rtts[i];
					worst_num++;
			}
		}
		if (best_num > 0) {
			best = best_total/best_num;
		}
		if (worst_num > 0) {
			worst = worst_total/worst_num;
		}
		rttGraph.addIntsValue(new int[]{ average,best,worst });
	}

	private static String getEventTypeString(int type) {
		
		if (type == UISWTViewEvent.TYPE_CREATE) return "UISWTViewEvent.TYPE_CREATE";
		if (type == UISWTViewEvent.TYPE_DATASOURCE_CHANGED) return "UISWTViewEvent.TYPE_DATASOURCE_CHANGED";
		if (type == UISWTViewEvent.TYPE_INITIALIZE) return "UISWTViewEvent.TYPE_INITIALIZE";
		if (type == UISWTViewEvent.TYPE_FOCUSGAINED) return "UISWTViewEvent.TYPE_FOCUSGAINED";
		if (type == UISWTViewEvent.TYPE_FOCUSLOST) return "UISWTViewEvent.TYPE_FOCUSLOST";
		if (type == UISWTViewEvent.TYPE_REFRESH) return "UISWTViewEvent.TYPE_REFRESH";
		if (type == UISWTViewEvent.TYPE_LANGUAGEUPDATE) return "UISWTViewEvent.TYPE_LANGUAGEUPDATE";
		if (type == UISWTViewEvent.TYPE_DESTROY) return "UISWTViewEvent.TYPE_DESTROY";
		if (type == UISWTViewEvent.TYPE_CLOSE) return "UISWTViewEvent.TYPE_CLOSE";
		if (type == UISWTViewEvent.TYPE_OBFUSCATE) return "UISWTViewEvent.TYPE_OBFUSCATE";
		
		if (type == StatsView.EVENT_PERIODIC_UPDATE) return "StatsView.EVENT_PERIODIC_UPDATE";
		
		//return String.valueOf(type);
		throw new RuntimeException("do not occur this...");
	}
	
	private static String getUtf8FromIso_8859_1(String original) {
		try {
			return new String(original.getBytes("iso-8859-1"), "utf-8");
		} catch (UnsupportedEncodingException e) {
			return "";
		}
	}
	
	private static String getString(String original, String charset) {
		try {
			return new String(original.getBytes(), charset);
		} catch (UnsupportedEncodingException e) {
			return "";
		}
	}
	
	private static String getString(byte[] bytes, String charset) {
		try {
			return new String(bytes, charset);
		} catch (UnsupportedEncodingException e) {
			return "";
		}
	}
	
	public boolean eventOccurred(UISWTViewEvent event) {
		
		/*Log.d(TAG, "eventOccurred() is called...");
		Log.d(TAG, "type = " + getEventTypeString(event.getType()));
		Throwable t = new Throwable();
		t.printStackTrace();*/
		
		switch (event.getType()) {
			case UISWTViewEvent.TYPE_CREATE:
				//Log.d(TAG, "UISWTViewEvent.TYPE_CREATE is occured...");
				/*Throwable t = new Throwable();
				t.printStackTrace();
				
				String title = MessageText.getString(getTitleID());
				//title = getUtf8FromIso_8859_1(title);
				//Log.d(TAG, "title = " + new String(title.getBytes(), "iso-8859-1"));
				String [] charset = {"utf-8","euc-kr","ksc5601","iso-8859-1","x-windows-949"};
				//for (int i = 0; i < charset.length; i++)
					//Log.d(TAG, getString(title, charset[i]));
				
				for (int i=0; i<charset.length; i++) {
					for (int j=0; j<charset.length; j++) {
						try {
							Log.d(TAG, getString(title.getBytes(charset[i]), charset[j]));
						} catch (UnsupportedEncodingException e) {
							e.printStackTrace();
						}
					}
				}*/
				
				swtView = (UISWTView)event.getData();
				swtView.setTitle(MessageText.getString(getTitleID()));
				break;

			case UISWTViewEvent.TYPE_DESTROY:
				delete();
				break;

			case UISWTViewEvent.TYPE_INITIALIZE:
				initialize((Composite)event.getData());
				break;

			case UISWTViewEvent.TYPE_LANGUAGEUPDATE:
				Messages.updateLanguageForControl(getComposite());
				if (swtView != null) {
					swtView.setTitle(MessageText.getString(getTitleID()));
				}
				break;

			case UISWTViewEvent.TYPE_DATASOURCE_CHANGED:
				if (event.getData() instanceof Number) {
					dhtType = ((Number) event.getData()).intValue();
					if (swtView != null) {
						swtView.setTitle(MessageText.getString(getTitleID()));
					}
				}
				break;

			case UISWTViewEvent.TYPE_FOCUSGAINED:
				break;

			case UISWTViewEvent.TYPE_REFRESH:
				/*if (SingleCounter0.getInstance().getAndIncreaseCount() == 1)
					new Throwable().printStackTrace();*/
				
				refresh();
				break;

			case StatsView.EVENT_PERIODIC_UPDATE:
				periodicUpdate();
				break;
		}

		return true;
	}
}


