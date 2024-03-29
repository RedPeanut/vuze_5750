/*
 * Created on 29 nov. 2004
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details (see the LICENSE file).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.gudy.azureus2.ui.swt.pluginsinstaller;

import java.util.*;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import org.gudy.azureus2.core3.html.HTMLUtils;
import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.util.AERunnable;
import org.gudy.azureus2.core3.util.AEThread2;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.plugins.installer.InstallablePlugin;
import org.gudy.azureus2.plugins.installer.StandardPlugin;
import org.gudy.azureus2.ui.swt.Messages;
import org.gudy.azureus2.ui.swt.Utils;
import org.gudy.azureus2.ui.swt.components.LinkArea;
import org.gudy.azureus2.ui.swt.shells.CoreWaiterSWT;
import org.gudy.azureus2.ui.swt.shells.CoreWaiterSWT.TriggerInThread;
import org.gudy.azureus2.ui.swt.wizard.AbstractWizardPanel;
import org.gudy.azureus2.ui.swt.wizard.IWizardPanel;

import com.aelitis.azureus.core.AzureusCore;
import com.aelitis.azureus.core.AzureusCoreRunningListener;


/**
 * @author Olivier Chalouhi
 *
 */
public class IPWListPanel extends AbstractWizardPanel<InstallPluginWizard> {

  Table pluginList;

  LinkArea	link_area;

  public
  IPWListPanel(
	InstallPluginWizard 					wizard,
	IWizardPanel<InstallPluginWizard> 		previous )
  {
	super(wizard, previous);
  }


  public void
  show()
  {
    wizard.setTitle(MessageText.getString("installPluginsWizard.list.title"));
    wizard.setErrorMessage("");

	Composite rootPanel = wizard.getPanel();
	GridLayout layout = new GridLayout();
	layout.numColumns = 1;
	rootPanel.setLayout(layout);

	Composite panel = new Composite(rootPanel, SWT.NULL);
	GridData gridData = new GridData(GridData.VERTICAL_ALIGN_CENTER | GridData.FILL_HORIZONTAL);
	Utils.setLayoutData(panel, gridData);
	layout = new GridLayout();
	layout.numColumns = 1;
	panel.setLayout(layout);

	final Label lblStatus = new Label(panel,SWT.NULL);
	GridData data = new GridData(GridData.FILL_HORIZONTAL);
	Utils.setLayoutData(lblStatus, data);
	Messages.setLanguageText(lblStatus,"installPluginsWizard.list.loading");

	pluginList = new Table(panel,SWT.BORDER | SWT.V_SCROLL | SWT.CHECK | SWT.FULL_SELECTION | SWT.SINGLE);
	pluginList.setHeaderVisible(true);
	data = new GridData(GridData.FILL_HORIZONTAL);
	data.heightHint = 120;
	Utils.setLayoutData(pluginList, data);


	TableColumn tcName = new TableColumn(pluginList,SWT.LEFT);
	Messages.setLanguageText(tcName,"installPluginsWizard.list.name");
	tcName.setWidth(Utils.adjustPXForDPI(200));

	TableColumn tcVersion = new TableColumn(pluginList,SWT.LEFT);
	Messages.setLanguageText(tcVersion,"installPluginsWizard.list.version");
	tcVersion.setWidth(Utils.adjustPXForDPI(150));


	Label lblDescription = new Label(panel,SWT.NULL);
	Messages.setLanguageText(lblDescription,"installPluginsWizard.list.description");

	link_area = new LinkArea(panel);

	data = new GridData(GridData.FILL_HORIZONTAL);
	data.heightHint = 100;
	link_area.getComponent().setLayoutData(data);

	CoreWaiterSWT.waitForCore(TriggerInThread.NEW_THREAD, new AzureusCoreRunningListener() {
		public void azureusCoreRunning(AzureusCore core) {
	    final StandardPlugin plugins[];
	    try {
	      plugins = wizard.getStandardPlugins(core);

	      Arrays.sort(
	      	plugins,
		  	new Comparator<StandardPlugin>() {
	      		public int compare(
					StandardPlugin o1,
					StandardPlugin o2)
	      		{
	      			return ( o1.getName().compareToIgnoreCase( o2.getName()));
	      		}
			});

	    } catch (final Exception e) {

	    	Debug.printStackTrace(e);
		    wizard.getDisplay().asyncExec(new AERunnable() {
			      public void runSupport() {
			      	link_area.addLine( Debug.getNestedExceptionMessage(e));
			      }
		    });

	    	return;
	    }

	    wizard.getDisplay().asyncExec(new AERunnable() {
	      public void runSupport() {

	        lblStatus.setText( ((InstallPluginWizard)wizard).getListTitleText());

	        List<InstallablePlugin>	selected_plugins = wizard.getPluginList();

	        for (int i = 0 ; i < plugins.length ; i++) {
	          StandardPlugin plugin = plugins[i];
	          if (plugin.getAlreadyInstalledPlugin() == null) {
	            if (pluginList == null || pluginList.isDisposed())
	              return;
	            TableItem item = new TableItem(pluginList,SWT.NULL);
	            item.setData(plugin);
	            item.setText(0,plugin.getName());
	            boolean	selected = false;
	            for (int j=0;j<selected_plugins.size();j++) {
	            	if (selected_plugins.get(j).getId() == plugin.getId()) {
	            		selected = true;
	            	}
	            }
	            item.setChecked(selected);
	            item.setText(1,plugin.getVersion());
	          }
	        }

	        	// if there's only one entry then we might as well pull it in (this is really to
	        	// support explicit install directions in the wizard as opposed to selection from
	        	// the SF list )

	        if (plugins.length == 1 && pluginList.getItemCount() > 0) {

	        	pluginList.select(0);

	        	loadPluginDetails( pluginList.getItem(0));
	        }
	      }
	    });
	  }
	});


	pluginList.addListener(SWT.Selection,new Listener() {
	  public void handleEvent(Event e) {
	    if (pluginList.getSelectionCount() > 0) {
	    	loadPluginDetails( pluginList.getSelection()[0]);

	    }
	    updateList();
	  }
	});
  }

  	protected void
  	loadPluginDetails(
  		final TableItem	selected_item )
  	{
  		link_area.reset();

	    link_area.addLine( MessageText.getString("installPluginsWizard.details.loading"));

	      final StandardPlugin plugin = (StandardPlugin) selected_item.getData();


	      AEThread2 detailsLoader = new AEThread2("Detail Loader") {
	        public void run() {
	         final String description = HTMLUtils.convertListToString(HTMLUtils.convertHTMLToText(plugin.getDescription(),""));
	         wizard.getDisplay().asyncExec(new AERunnable() {
			      public void runSupport() {
			        if (pluginList == null || pluginList.isDisposed() || pluginList.getSelectionCount() ==0)
			          return;
			        if (pluginList.getSelection()[0] != selected_item)
			          return;

			        link_area.reset();

			        link_area.setRelativeURLBase( plugin.getRelativeURLBase());

			        link_area.addLine(description);
			      }
		      	});
	        }
	      };

	      detailsLoader.start();
  	}

	public boolean isNextEnabled() {
		return (((InstallPluginWizard)wizard).getPluginList().size() > 0);
	}

  public IWizardPanel<InstallPluginWizard> getNextPanel() {
    return new IPWInstallModePanel(wizard,this);
  }

  public void updateList() {
    ArrayList<InstallablePlugin> list = new ArrayList<InstallablePlugin>();
    TableItem[] items = pluginList.getItems();
    for (int i = 0 ; i < items.length ; i++) {
      if (items[i].getChecked()) {
        list.add((InstallablePlugin)items[i].getData());
      }
    }
    wizard.setPluginList(list);
    wizard.setNextEnabled(isNextEnabled());

  }
}
