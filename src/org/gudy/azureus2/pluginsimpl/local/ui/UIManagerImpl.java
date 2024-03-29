/*
 * Created on 19-Apr-2004
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

package org.gudy.azureus2.pluginsimpl.local.ui;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.util.AEMonitor;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.plugins.PluginInterface;
import org.gudy.azureus2.plugins.logging.LoggerChannel;
import org.gudy.azureus2.plugins.torrent.Torrent;
import org.gudy.azureus2.plugins.ui.*;
import org.gudy.azureus2.plugins.ui.config.ConfigSection;
import org.gudy.azureus2.plugins.ui.menus.MenuManager;
import org.gudy.azureus2.plugins.ui.model.BasicPluginConfigModel;
import org.gudy.azureus2.plugins.ui.model.BasicPluginViewModel;
import org.gudy.azureus2.plugins.ui.model.PluginConfigModel;
import org.gudy.azureus2.plugins.ui.tables.TableManager;
import org.gudy.azureus2.pluginsimpl.local.ui.menus.MenuManagerImpl;
import org.gudy.azureus2.pluginsimpl.local.ui.model.BasicPluginConfigModelImpl;
import org.gudy.azureus2.pluginsimpl.local.ui.model.BasicPluginViewModelImpl;
import org.gudy.azureus2.pluginsimpl.local.ui.tables.TableManagerImpl;
import org.gudy.azureus2.ui.common.UIInstanceBase;

import com.aelitis.azureus.core.util.CopyOnWriteList;
import com.aelitis.azureus.ui.IUIIntializer;
import com.aelitis.azureus.ui.selectedcontent.ISelectedContent;
import com.aelitis.azureus.ui.selectedcontent.SelectedContentListener;
import com.aelitis.azureus.ui.selectedcontent.SelectedContentManager;




/**
 * @author parg
 *
 */

public class
UIManagerImpl
	implements UIManager
{
	protected static AEMonitor	class_mon = new AEMonitor("UIManager:class");

	protected static boolean				initialisation_complete;

	protected static CopyOnWriteList<Object[]>					ui_listeners		= new CopyOnWriteList<Object[]>();
	protected static CopyOnWriteList<UIManagerEventListener>	ui_event_listeners	= new CopyOnWriteList<UIManagerEventListener>();

	protected static List<UIInstanceFactory>		ui_factories		= new ArrayList<UIInstanceFactory>();
	protected static List<UIManagerEventAdapter>	ui_event_history	= new ArrayList<UIManagerEventAdapter>();
	protected static List<BasicPluginConfigModel>	configModels 		= new ArrayList<BasicPluginConfigModel>();


	protected PluginInterface		pi;

	protected TableManager			table_manager;
	protected MenuManager           menu_manager;

	private static ArrayList<UIDataSourceListener> listDSListeners;

	public UIManagerImpl(
		PluginInterface		_pi) {
		pi		=_pi;

		table_manager	= new TableManagerImpl(this);
		menu_manager	= new MenuManagerImpl(this);
	}

	public PluginInterface
	getPluginInterface() {
		return (pi);
	}

	public BasicPluginViewModel
	createBasicPluginViewModel(
		String			name) {
		final BasicPluginViewModel	model = new BasicPluginViewModelImpl(this, name);

		fireEvent(pi, UIManagerEvent.ET_PLUGIN_VIEW_MODEL_CREATED, model);

		return (model);
	}

	public void destroy(
		final BasicPluginViewModel		model) {
		fireEvent(pi, UIManagerEvent.ET_PLUGIN_VIEW_MODEL_DESTROYED, model);
	}

	public BasicPluginConfigModel
	createBasicPluginConfigModel(
		String		section_name) {
		return (createBasicPluginConfigModel( ConfigSection.SECTION_PLUGINS, section_name));
	}


	public BasicPluginConfigModel createBasicPluginConfigModel(
		String		parentSection,
		String		sectionName) {
		
		final BasicPluginConfigModel model = new BasicPluginConfigModelImpl(this, parentSection, sectionName);
		try {
  			class_mon.enter();
			configModels.add(model);
		} finally {
			class_mon.exit();
		}
		fireEvent(pi, UIManagerEvent.ET_PLUGIN_CONFIG_MODEL_CREATED, model);
		return (model);
	}

	public void destroy(final BasicPluginConfigModel model) {
		try {
  			class_mon.enter();
			configModels.remove(model);
		} finally {
			class_mon.exit();
		}
		fireEvent(pi, UIManagerEvent.ET_PLUGIN_CONFIG_MODEL_DESTROYED, model);
	}

	public PluginConfigModel[] getPluginConfigModels() {
		try {
  			class_mon.enter();
			return (PluginConfigModel[]) configModels.toArray(new PluginConfigModel[0]);
		} finally {
			class_mon.exit();
		}
	}

	public void copyToClipBoard(final String		data )
		throws UIException {
		boolean ok = fireEvent(pi, UIManagerEvent.ET_COPY_TO_CLIPBOARD, data);
		if (!ok) {
			throw (new UIException("Failed to deliver request to UI"));
		}
	}

	public void openURL(final URL url)
		throws UIException {
		boolean ok = fireEvent(pi, UIManagerEvent.ET_OPEN_URL, url);
		if (!ok) {
			throw (new UIException("Failed to deliver request to UI"));
		}
	}

  public TableManager getTableManager() {
    return (table_manager);
  }

  public MenuManager getMenuManager() {
	  return menu_manager;
  }

  	public static void
  	initialisationComplete()
  	{
  		SelectedContentManager.addCurrentlySelectedContentListener(new SelectedContentListener() {
  			public void currentlySelectedContentChanged(
  					ISelectedContent[] currentContent, String viewID) {
  				triggerDataSourceListeners(SelectedContentManager.convertSelectedContentToObject(null));
  			}
  		});

  		List<Object[]> to_fire = new ArrayList<Object[]>();

  		try {
  			class_mon.enter();

  			initialisation_complete	= true;

			for (int j=0;j<ui_factories.size();j++) {

				UIInstanceFactory	factory = (UIInstanceFactory)ui_factories.get(j);

  				Iterator<Object[]> it = ui_listeners.iterator();

  				while (it.hasNext()) {

 					Object[]	entry = it.next();

 					List<UIInstanceFactory> fired = (List<UIInstanceFactory>)entry[2];

 					if (!fired.contains( factory)) {

 						fired.add(factory);

 						to_fire.add(new Object[]{ entry[0], factory.getInstance((PluginInterface)entry[1])});
 					}
				}
			}
  		} finally {

  			class_mon.exit();
  		}

  		for (Object[] entry: to_fire) {

			try {
				((UIManagerListener)entry[0]).UIAttached((UIInstance)entry[1]);

			} catch (Throwable e) {

				Debug.printStackTrace(e);
			}
  		}


		for (Object[] entry : to_fire) {

			try {
				if (entry[0] instanceof UIManagerListener2) {
					((UIManagerListener2) entry[0]).UIAttachedComplete((UIInstance) entry[1]);
				}

			} catch (Throwable e) {

				Debug.printStackTrace(e);
			}
		}
	}

  	public void attachUI(UIInstanceFactory factory) throws UIException {
  		attachUI(factory, null);
  	}

	public void attachUI(
		UIInstanceFactory		factory,
		IUIIntializer 			init) {
 		List<Object[]> to_fire = new ArrayList<Object[]>();

		try {
  			class_mon.enter();

  			ui_factories.add(factory);

  			if (initialisation_complete) {

  				Iterator<Object[]> it = ui_listeners.iterator();

  				while (it.hasNext()) {

  					Object[]	entry = (Object[])it.next();

					List<UIInstanceFactory> fired = (List<UIInstanceFactory>)entry[2];

					fired.add(factory);

					to_fire.add(new Object[]{ entry[0], entry[1], factory.getInstance((PluginInterface)entry[1])});
  				}
  			}
  		} finally {

  			class_mon.exit();
  		}

		for (Object[] entry : to_fire) {

			PluginInterface pi = (PluginInterface) entry[1];

			String name = pi.getPluginName();

			if (init != null) {

				init.reportCurrentTask(MessageText.getString("splash.plugin.UIinit",
						new String[] {
							name
						}));

				init.increaseProgress();
			}

			try {
				((UIManagerListener) entry[0]).UIAttached((UIInstance) entry[2]);

			} catch (Throwable e) {

				Debug.printStackTrace(e);
			}
		}

		for (Object[] entry : to_fire) {

			try {
				if (entry[0] instanceof UIManagerListener2) {
					((UIManagerListener2) entry[0]).UIAttachedComplete((UIInstance) entry[2]);
				}

			} catch (Throwable e) {

				Debug.printStackTrace(e);
			}
		}
	}

	public void detachUI(
		UIInstanceFactory		factory )

		throws UIException
	{
		factory.detach();

 		List<Object[]> to_fire = new ArrayList<Object[]>();

		try {
  			class_mon.enter();

  			ui_factories.remove(factory);

  			if (initialisation_complete) {

  				Iterator<Object[]> it = ui_listeners.iterator();

  				while (it.hasNext()) {

 					Object[]	entry = (Object[])it.next();

					List<UIInstanceFactory> fired = (List<UIInstanceFactory>)entry[2];

					fired.remove(factory);

 					to_fire.add(new Object[]{ entry[0], factory.getInstance((PluginInterface)entry[1])});
  				}
  			}
  		} finally {

  			class_mon.exit();
  		}

  		for (Object[] entry: to_fire) {

			try {
				((UIManagerListener)entry[0]).UIDetached((UIInstance)entry[1]);

			} catch (Throwable e) {

				Debug.printStackTrace(e);
			}
  		}
	}

  	public void
  	addUIListener(
  		UIManagerListener listener )
  	{
 		List<UIInstance> to_fire = new ArrayList<UIInstance>();

		try {
  			class_mon.enter();

  			List<UIInstanceFactory> fired = new ArrayList<UIInstanceFactory>();

  			ui_listeners.add(new Object[]{ listener, pi, fired });

 			if (initialisation_complete) {

  				for (int i=0;i<ui_factories.size();i++) {

  					UIInstanceFactory	factory = (UIInstanceFactory)ui_factories.get(i);

  					to_fire.add(factory.getInstance( pi));
  				}
  			}
  		} finally {

  			class_mon.exit();
  		}

  		for (UIInstance instance: to_fire) {

			try {
				listener.UIAttached(instance);

			} catch (Throwable e) {

				Debug.printStackTrace(e);
			}
  		}

		if (listener instanceof UIManagerListener2) {
			for (UIInstance instance : to_fire) {

				try {
					((UIManagerListener2) listener).UIAttachedComplete(instance);

				} catch (Throwable e) {

					Debug.printStackTrace(e);
				}
			}
		}

	}

 	public void
  	removeUIListener(
  		UIManagerListener listener )
 	{
		try {
  			class_mon.enter();

  			Iterator<Object[]>	it = ui_listeners.iterator();

  			while (it.hasNext()) {

				Object[]	entry = it.next();

				if (entry[0] == listener) {

					it.remove();
				}
  			}

  		} finally {

  			class_mon.exit();
  		}
 	}

  	public void
  	addUIEventListener(
  		UIManagerEventListener listener )
  	{
  		List<UIManagerEventAdapter>	ui_event_history_copy;

		try {
  			class_mon.enter();

  			ui_event_listeners.add(listener);

  			ui_event_history_copy = new ArrayList<UIManagerEventAdapter>(ui_event_history);

  		} finally {

  			class_mon.exit();
  		}

  		for (int i=0;i<ui_event_history_copy.size();i++) {

  			try {
  				listener.eventOccurred((UIManagerEvent)ui_event_history_copy.get(i));

  			} catch (Throwable e) {

  				Debug.printStackTrace(e);
  			}
  		}
  	}

 	public void
  	removeUIEventListener(
  		UIManagerEventListener listener )
 	{
		try {
  			class_mon.enter();

  			ui_event_listeners.remove(listener);

  		} finally {

  			class_mon.exit();
  		}
 	}

 	public boolean hasUIInstances() {return !ui_factories.isEmpty();}

 	public UIInstance[] getUIInstances() {
 		try {
  			class_mon.enter();
  			ArrayList<UIInstance> result = new ArrayList<UIInstance>(ui_factories.size());
  			for (int i=0;i<ui_factories.size();i++) {
  				UIInstanceFactory instance = ui_factories.get(i);
  				result.add(instance.getInstance(pi));
  			}
  			return result.toArray(new UIInstance[result.size()]);
 		}
 		finally {
 			class_mon.exit();
 		}
 	}

	public static boolean fireEvent(
 		PluginInterface	pi,
 		int				type,
 		Object			data)
 	{
		return (fireEvent(new UIManagerEventAdapter(pi, type, data)));
 	}

 	public static boolean fireEvent(UIManagerEventAdapter event) {
 		boolean	delivered	= false;
		Iterator<UIManagerEventListener> event_it = ui_event_listeners.iterator();
		while (event_it.hasNext()) {
 			try {
 				if (event_it.next().eventOccurred( event)) {
 					delivered = true;
 					break;
 				}
 			} catch (Throwable e) {
 				e.printStackTrace();
 			}
 		}
 		int	type = event.getType();
		// some events need to be replayed when new UIs attach
 		if (	type == UIManagerEvent.ET_PLUGIN_VIEW_MODEL_CREATED ||
 				type == UIManagerEvent.ET_PLUGIN_CONFIG_MODEL_CREATED ||
 				type == UIManagerEvent.ET_ADD_TABLE_CONTEXT_MENU_ITEM ||
 				type == UIManagerEvent.ET_ADD_MENU_ITEM ||
 				type == UIManagerEvent.ET_REMOVE_TABLE_CONTEXT_MENU_ITEM ||
 				type == UIManagerEvent.ET_REMOVE_MENU_ITEM) {
 			delivered = true;
 			try {
 	  			class_mon.enter();
 	  			ui_event_history.add(event);
 			} finally {
 				class_mon.exit();
 			}
 		} else if (	type == UIManagerEvent.ET_PLUGIN_VIEW_MODEL_DESTROYED ||
 					type == UIManagerEvent.ET_PLUGIN_CONFIG_MODEL_DESTROYED) {
 				// remove any corresponding history events for creation of these entities
 			delivered = true;
 			try {
 	  			class_mon.enter();
	 			Iterator<UIManagerEventAdapter> 	history_it = ui_event_history.iterator();
	 			while (history_it.hasNext()) {
	 				UIManagerEvent	e = history_it.next();
	 				int	e_type = e.getType();
	 				if (	e_type == UIManagerEvent.ET_PLUGIN_VIEW_MODEL_CREATED ||
	 		 				e_type == UIManagerEvent.ET_PLUGIN_CONFIG_MODEL_CREATED) {
	 					if (e.getData() == event.getData()) {
	 						history_it.remove();
	 						break;
	 					}
	 				}
	 			}
	 		} finally {
	 			class_mon.exit();
	 		}
 		}
 		return (delivered);
 	}

	public void showTextMessage(
		final String		title_resource,
		final String		message_resource,
		final String		contents) {
		fireEvent( pi, UIManagerEvent.ET_SHOW_TEXT_MESSAGE, new String[]{ title_resource, message_resource, contents });
	}

	public long showMessageBox(
		String		title_resource,
		String		message_resource,
		long		message_map) {
		return (showMessageBox( title_resource, message_resource, message_map, new Object[0]));
	}

	public long showMessageBox(
		String		title_resource,
		String		message_resource,
		long		message_map,
		Object[]	params) {
		Object[]	all_params = new Object[3+params.length];

		all_params[0]	= title_resource;
		all_params[1]	= message_resource;
		all_params[2]	= new Long(message_map);

		System.arraycopy(params, 0, all_params, 3, params.length);

		UIManagerEventAdapter event =
			new UIManagerEventAdapter(
					pi,
					UIManagerEvent.ET_SHOW_MSG_BOX,
					all_params);

		if (!fireEvent(event)) {

			return (UIManagerEvent.MT_NONE);
		}

		return (((Long)event.getResult()).longValue());
	}

	public long showMessageBox(
		String					title_resource,
		String					message_resource,
		long					message_map,
		Map<String,Object>		params) {
		Object[]	all_params = new Object[4];

		all_params[0]	= title_resource;
		all_params[1]	= message_resource;
		all_params[2]	= new Long(message_map);
		all_params[3]	= params;

		UIManagerEventAdapter event =
			new UIManagerEventAdapter(
					pi,
					UIManagerEvent.ET_SHOW_MSG_BOX,
					all_params);

		if (!fireEvent(event)) {

			return (UIManagerEvent.MT_NONE);
		}

		return (((Long)event.getResult()).longValue());
	}

	public void openTorrent(
		Torrent torrent) {
		fireEvent(pi, UIManagerEvent.ET_OPEN_TORRENT_VIA_TORRENT, torrent);
	}

 	public void openFile(File file) {fireEvent(pi,UIManagerEvent.ET_FILE_OPEN, file);}
 	public void showFile(File file) {fireEvent(pi,UIManagerEvent.ET_FILE_SHOW, file);}

	public boolean showConfigSection(String sectionID) {
		UIManagerEventAdapter event = new UIManagerEventAdapter(
				pi, UIManagerEvent.ET_SHOW_CONFIG_SECTION, sectionID);
		if (!fireEvent(event))
			return false;

		if (event.getResult() instanceof Boolean)
			return false;

		return ((Boolean)event.getResult()).booleanValue();
	}

 	public UIInputReceiver getInputReceiver() {
 		UIInstance[] instances = this.getUIInstances();
 		UIInputReceiver r = null;
 		for (int i=0; i<instances.length; i++) {
 			r = instances[i].getInputReceiver();
 			if (r != null) {return r;}
 		}
 		return null;
 	}

 	public UIMessage createMessage() {
 		UIInstance[] instances = this.getUIInstances();
 		UIMessage r = null;
 		for (int i=0; i<instances.length; i++) {
 			r = instances[i].createMessage();
 			if (r != null) {return r;}
 		}
 		return null;
 	}

	public BasicPluginViewModel createLoggingViewModel(
			LoggerChannel channel, boolean use_plugin_name
	) {
		String log_view_name = (use_plugin_name) ? pi.getPluginName() : channel.getName();
		BasicPluginViewModel model = createBasicPluginViewModel(log_view_name);
		model.getActivity().setVisible(false);
		model.getProgress().setVisible(false);
		model.getStatus().setVisible(false);
		model.attachLoggerChannel(channel);
		return model;
	}

	public void setEverythingHidden(
		boolean hidden) {
		fireEvent(pi, UIManagerEvent.ET_HIDE_ALL, hidden);
	}

	public static void unload(
		PluginInterface	pi) {
		try {
  			class_mon.enter();

			Iterator<Object[]> it = ui_listeners.iterator();

			while (it.hasNext()) {

				Object[]	entry = it.next();

				if (pi == (PluginInterface)entry[1]) {

					it.remove();
				}
			}

			Iterator<UIManagerEventAdapter> ev_it = ui_event_history.iterator();

			while (ev_it.hasNext()) {

				UIManagerEventAdapter event = ev_it.next();

				if (event.getPluginInterface() == pi) {

					ev_it.remove();
				}
			}

			for (UIInstanceFactory uif : ui_factories) {
				UIInstance instance = uif.getInstance(pi);
				if (instance instanceof UIInstanceBase) {
					UIInstanceBase instanceBase = (UIInstanceBase) instance;
					instanceBase.unload(pi);
				}
			}
  	} finally {

  		class_mon.exit();
  	}
	}

	public void addDataSourceListener(UIDataSourceListener l, boolean triggerNow) {
		class_mon.enter();
		try {
			if (listDSListeners == null) {
				listDSListeners = new ArrayList<UIDataSourceListener>();
			}
			listDSListeners.add(l);
		} finally {
			class_mon.exit();
		}
		if (triggerNow) {
			try {
				l.dataSourceChanged(SelectedContentManager.convertSelectedContentToObject(null));
			} catch (Throwable t) {
				Debug.out(t);
			}
		}
	}

	public void removeDataSourceListener(UIDataSourceListener l) {
		class_mon.enter();
		try {
			if (listDSListeners == null) {
				return;
			}
			listDSListeners.remove(l);
		} finally {
			class_mon.exit();
		}
	}

	// @see org.gudy.azureus2.plugins.ui.UIManager#getDataSource()
	public Object getDataSource() {
		return SelectedContentManager.convertSelectedContentToObject(null);
	}

	private static void triggerDataSourceListeners(Object ds) {
		UIDataSourceListener[] listeners;
		class_mon.enter();
		try {
			if (listDSListeners == null) {
				return;
			}
			listeners = listDSListeners.toArray(new UIDataSourceListener[0]);
		} finally {
			class_mon.exit();
		}
		for (UIDataSourceListener l : listeners) {
			try {
				l.dataSourceChanged(ds);
			} catch (Throwable t) {
				Debug.out(t);
			}
		}
	}
}