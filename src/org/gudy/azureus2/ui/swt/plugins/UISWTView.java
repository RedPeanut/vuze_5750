/*
 * File    : UISWTView.java
 * Created : Oct 14, 2005
 * By      : TuxPaper
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

package org.gudy.azureus2.ui.swt.plugins;

import org.gudy.azureus2.plugins.PluginInterface;
import org.gudy.azureus2.plugins.ui.UIPluginView;

/**
 * Commands and Information about a SWT View
 *
 * @author TuxPaper
 *
 * @see org.gudy.azureus2.ui.swt.plugins.UISWTViewEvent#getView()
 * @see org.gudy.azureus2.ui.swt.plugins.UISWTViewEventListener#eventOccurred(UISWTViewEvent)
 * @see org.gudy.azureus2.ui.swt.plugins.UISWTInstance#addView(String, String, UISWTViewEventListener)
 */
public interface UISWTView extends UIPluginView {
	/**
	 * For {@link #setControlType(int)}; When the event
	 * {@link UISWTViewEvent#TYPE_INITIALIZE} is triggered, getData() will
	 * return a {@link org.eclipse.swt.widgets.Composite} object.
	 *
	 * @since 2.3.0.6
	 */
	public static final int CONTROLTYPE_SWT = 0;

	/**
	 * For {@link #setControlType(int)}; When the event
	 * {@link UISWTViewEvent#TYPE_INITIALIZE} is triggered, getData() will
	 * return a {@link java.awt.Component} object.
	 *
	 * @since 2.3.0.6
	 */
	public static final int CONTROLTYPE_AWT = 1;

	/**
	 * Sets the type of control this view uses.  Set before view initialization.
	 * <p>
	 * The default value is {@link #CONTROLTYPE_SWT}
	 *
	 * @param iControlType
	 *
	 * @since 2.3.0.6
	 */
	public void setControlType(int iControlType);

	/**
	 *
	 * @return CONTROLTYPE_*
	 *
	 * @since 4.3.1.3
	 */
	int getControlType();

	/**
	 * Retrieve the data sources related to this view.
	 *
	 * @return Depending on the parent view you added your view to, the Object will be:<br>
	 *  {@link UISWTInstance#VIEW_MAIN}- null<br>
	 *  {@link UISWTInstance#VIEW_MYTORRENTS}- {@link org.gudy.azureus2.plugins.download.Download}<br>
	 *  {@link UISWTInstance#VIEW_TORRENT_PEERS}- {@link org.gudy.azureus2.plugins.peers.Peer}<br>
	 *  If created by {@link UISWTInstance#openMainView(String, UISWTViewEventListener, Object)},
	 *  value will be the value set.
	 *  <p>
	 *  May return null if no data source is selected, or while processing the
	 *  {@link UISWTViewEvent#TYPE_CREATE} event.
	 *
	 * @since 2.3.0.6
	 */
	// From UIPluginView, declared here only to change JavaDoc
	public Object getDataSource();

	/**
	 * Get the original datasource that was set to the view
	 *
	 * @since 5.5.0.0
	 */
	public Object getInitialDataSource();

	/**
	 * Get parent view, if one exists
	 *
	 * @since 5.5.0.0
	 */
	public UISWTView getParentView();

	/**
	 * Trigger an event for this view
	 *
	 * @param eventType  Event to trigger {@link UISWTViewEvent}}
	 * @param data data to send with trigger
	 *
	 * @since 2.3.0.6
	 */
	public void triggerEvent(int eventType, Object data);

	/**
	 * Override the default title with a new one.
	 *
	 * After setting this, you should use the
	 * {@link UISWTViewEvent#TYPE_LANGUAGEUPDATE} to update your title to the
	 * new language.
	 *
	 * @param title new Title
	 *
	 * @since 2.3.0.6
	 */
	public void setTitle(String title);

	/**
	 * Gets the plugin interface associated with this view, null if none defined
	 *
	 * @since 4.5.1.1
	 */
	public PluginInterface getPluginInterface();


	/**
	 * To save memory/CPU, views are sometimes destroyed on {@link UISWTViewEvent#TYPE_FOCUSLOST}
	 * <P>
	 * This allows overriding of the default behaviour
	 *
	 * @since 5.6.0.1
	 */
	public void setDestroyOnDeactivate(boolean b);

	/**
	 * Retrieve whether this view can be destroyed on  {@link UISWTViewEvent#TYPE_FOCUSLOST}
	 *
	 * @since 5.6.0.1
	 */
	boolean isDestroyOnDeactivate();

}
