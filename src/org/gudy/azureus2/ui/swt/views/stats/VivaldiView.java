/*
 * Created on 22 juin 2005
 * Created by Olivier Chalouhi
 *
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.	See the
 * GNU General Public License for more details (see the LICENSE file).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA	02111-1307	USA
 */
package org.gudy.azureus2.ui.swt.views.stats;

import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;

import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.plugins.PluginInterface;
import org.gudy.azureus2.ui.swt.Messages;
import org.gudy.azureus2.ui.swt.plugins.UISWTView;
import org.gudy.azureus2.ui.swt.plugins.UISWTViewEvent;
import org.gudy.azureus2.ui.swt.plugins.UISWTViewEventListener;

import com.aelitis.azureus.core.AzureusCore;
import com.aelitis.azureus.core.AzureusCoreFactory;
import com.aelitis.azureus.core.AzureusCoreRunningListener;
import com.aelitis.azureus.core.dht.DHT;
import com.aelitis.azureus.core.dht.control.DHTControlContact;
import com.aelitis.azureus.plugins.dht.DHTPlugin;

public class VivaldiView implements UISWTViewEventListener {
	
	public static final String MSGID_PREFIX = "VivaldiView";
	
	public static final int DHT_TYPE_MAIN		= DHT.NW_MAIN;
	public static final int DHT_TYPE_CVS		= DHT.NW_CVS;
	public static final int DHT_TYPE_MAIN_V6	= DHT.NW_MAIN_V6;

	DHT dht;
	Composite panel;
	VivaldiPanel drawPanel;
	private final boolean autoAlpha;
	private int dhtType;
	private AzureusCore core;
	private UISWTView swtView;


	public VivaldiView(boolean autoAlpha) {
		this.autoAlpha = autoAlpha;
	}

	public VivaldiView() {
		this(false);
	}

	private void init(AzureusCore core) {
		try {
			PluginInterface dhtPI = core.getPluginManager().getPluginInterfaceByClass(DHTPlugin.class);
			if (dhtPI == null) {
				return;
			}
			DHT[] dhts = ((DHTPlugin)dhtPI.getPlugin()).getDHTs();
			for (int i=0;i<dhts.length;i++) {
				if (dhts[i].getTransport().getNetwork() == dhtType) {
					dht = dhts[i];
					break;
				}
			}
			
			if (dht == null)
				return;
			
		} catch (Exception e) {
			Debug.printStackTrace(e);
		}
	}

	private void initialize(Composite composite) {
		AzureusCoreFactory.addCoreRunningListener(new AzureusCoreRunningListener() {
			public void azureusCoreRunning(AzureusCore core) {
				VivaldiView.this.core = core;
				init(core);
			}
		});

		panel = new Composite(composite,SWT.NULL);
		panel.setLayout(new FillLayout());
		drawPanel = new VivaldiPanel(panel);
		drawPanel.setAutoAlpha(autoAlpha);
	}

	private Composite getComposite() {
		return panel;
	}

	private void refresh() {
		if (dht == null) {
			if (core != null) {
				// keep trying until dht is avail
				init(core);
			} else {
				return;
			}
		}

		if (dht != null) {
			List<DHTControlContact> l = dht.getControl().getContacts();
			drawPanel.refreshContacts(l,dht.getControl().getTransport().getLocalContact());
		}
	}

	private String getTitleID() {
		if (dhtType == DHT_TYPE_MAIN) {
			return ("VivaldiView.title.full");
		} else if (dhtType == DHT_TYPE_CVS) {
			return ("VivaldiView.title.fullcvs");
		} else {
			return ("VivaldiView.title.full_v6");
		}
	}

	private void delete() {
		if (drawPanel != null) {
			drawPanel.delete();
		}
	}

	public boolean eventOccurred(UISWTViewEvent event) {
		switch (event.getType()) {
			case UISWTViewEvent.TYPE_CREATE:
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
				refresh();
				break;
		}

	return true;
	}
}
