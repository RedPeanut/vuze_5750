/*
 * Created on 6 Feb 2007
 * Created by Allan Crooks
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
package org.gudy.azureus2.pluginsimpl.local.ui.menus;

import java.util.Iterator;

import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.util.Debug;

import com.aelitis.azureus.core.util.CopyOnWriteList;

import hello.util.Log;
import hello.util.SingleCounter0;

import org.gudy.azureus2.plugins.PluginInterface;
import org.gudy.azureus2.plugins.ui.Graphic;
import org.gudy.azureus2.plugins.ui.UIManagerEvent;
import org.gudy.azureus2.plugins.ui.menus.*;
import org.gudy.azureus2.pluginsimpl.local.PluginInitializer;
import org.gudy.azureus2.pluginsimpl.local.ui.UIManagerImpl;

/**
 * amc1: This class was largely derived from TableContextMenuImpl.
 */
public class MenuItemImpl implements MenuItem {
	
	private static String TAG = MenuItemImpl.class.getSimpleName();
	
	private PluginInterface pi;
	private String sMenuID;
	private String sName;
	private int style = STYLE_PUSH;
	private boolean enabled = true;
	private Object data;
	private Graphic graphic;
	private CopyOnWriteList listeners = new CopyOnWriteList(1);
	private CopyOnWriteList mListeners = new CopyOnWriteList(1);
	private CopyOnWriteList fillListeners = new CopyOnWriteList(1);
	private CopyOnWriteList children = new CopyOnWriteList();
	private MenuItemImpl parent = null;
	private String displayText = null;
	private boolean visible = true;
	private MenuContextImpl menuContext = null;
	private MenuBuilder builder;

	public MenuItemImpl(PluginInterface _pi, String menuID, String key) {
		pi = _pi;
		if (pi == null) {
			pi = PluginInitializer.getDefaultInterface();
		}
		sMenuID = menuID;
		sName = key;
	}

	public MenuItemImpl(MenuItemImpl ti, String key) {
		pi = ti.pi;
		this.parent = ti;
		this.parent.addChildMenuItem(this);
		this.sMenuID = this.parent.getMenuID();
		this.sName = key;
	}

	public String getResourceKey() {
		return sName;
	}

	public String getMenuID() {
		return sMenuID;
	}

	public int getStyle() {
		return (style);
	}

	public void setStyle(int _style) {
		if (this.style == MenuItem.STYLE_MENU && _style != MenuItem.STYLE_MENU) {
			throw new RuntimeException(
					"cannot revert menu style MenuItem object to another style");
		}
		style = _style;
	}

	public Object getData() {
		return (data);
	}

	public void setData(Object _data) {
		data = _data;
	}

	public boolean isEnabled() {
		return (enabled);
	}

	public void setEnabled(boolean _enabled) {
		enabled = _enabled;
	}

	public void setGraphic(Graphic _graphic) {
		graphic = _graphic;
	}

	public Graphic getGraphic() {
		return (graphic);
	}

	public void invokeMenuWillBeShownListeners(Object target) {
		for (Iterator iter = fillListeners.iterator(); iter.hasNext();) {
			try {
				MenuItemFillListener l = (MenuItemFillListener) iter.next();
				l.menuWillBeShown(this, target);
			} catch (Throwable e) {
				Debug.printStackTrace(e);
			}
		}
	}

	public void addFillListener(MenuItemFillListener listener) {
		fillListeners.add(listener);
	}

	public void removeFillListener(MenuItemFillListener listener) {
		fillListeners.remove(listener);
	}

	// Currently used by TableView (and other places).
	public void invokeListenersMulti(Object[] rows) {
		// We invoke the multi listeners first...
		invokeListenersOnList(this.mListeners, rows);
		if (rows == null || rows.length == 0) {
			invokeListenersSingle(null);
			return;
		}
		for (int i = 0; i < rows.length; i++) {
			invokeListenersSingle(rows[i]);
		}
	}

	  public void addMultiListener(MenuItemListener l) {
		  mListeners.add(l);
	  }

	  public void removeMultiListener(MenuItemListener l) {
		  mListeners.remove(l);
	  }

	private void invokeListenersSingle(Object o) {
		invokeListenersOnList(this.listeners, o);
	}

	public void addListener(MenuItemListener l) {
		listeners.add(l);
	}

	public void removeListener(MenuItemListener l) {
		listeners.remove(l);
	}

	public MenuItem getParent() {
		return this.parent;
	}

	public MenuItem[] getItems() {
		if (this.style != MenuItem.STYLE_MENU) {
			return null;
		}
		return (MenuItem[]) this.children.toArray(new MenuItem[this.children
				.size()]);
	}

	public MenuItem getItem(String key) {
		if (this.style != MenuItem.STYLE_MENU) {
			return null;
		}
		java.util.Iterator itr = this.children.iterator();
		MenuItem result = null;
		while (itr.hasNext()) {
			result = (MenuItem) itr.next();
			if (key.equals(result.getResourceKey())) {
				return result;
			}
		}
		return null;
	}

	private void addChildMenuItem(MenuItem child) {
		if (this.style != MenuItem.STYLE_MENU) {
			throw new RuntimeException("cannot add to non-container MenuItem");
		}
		this.children.add(child);
	}

	public String getText() {
		
		/*if (SingleCounter0.getInstance().getAndIncreaseCount() == 1) {
			Log.d(TAG, "this.displayText = " + this.displayText);
		}*/
		
		if (this.displayText == null) {
			return MessageText.getString(this.getResourceKey());
		}
		return this.displayText;
	}

	public void setText(String text) {
		this.displayText = text;
	}

	protected void invokeListenersOnList(CopyOnWriteList listeners_to_notify,
			Object target) {
		for (Iterator iter = listeners_to_notify.iterator(); iter.hasNext();) {
			try {
				MenuItemListener l = (MenuItemListener) iter.next();
				l.selected(this, target);
			} catch (Throwable e) {
				Debug.printStackTrace(e);
			}
		}
	}

	protected void removeWithEvents(int root_menu_event, int sub_menu_event) {
		removeAllChildItems();
		if (this.parent != null) {
			UIManagerImpl.fireEvent(pi, sub_menu_event, new Object[]{this.parent, this});
			parent.children.remove(this);
			this.parent = null;
		}
		else {
			UIManagerImpl.fireEvent(pi, root_menu_event, this);
		}
		this.data = null;
		this.graphic = null;
		this.listeners.clear();
		this.fillListeners.clear();
		this.mListeners.clear();

		if (this.menuContext != null) {menuContext.dirty();}
	}

	public void remove() {
		removeWithEvents(UIManagerEvent.ET_REMOVE_MENU_ITEM, UIManagerEvent.ET_REMOVE_SUBMENU_ITEM);
	}

	public void removeAllChildItems() {
		// This should make this.children be empty...
		MenuItem[] children = this.getItems();
		if (children != null) {
			for (int i=0; i<children.length; i++) {children[i].remove();}
		}
	}

	public boolean isVisible() {return visible;}
	public void setVisible(boolean visible) {this.visible = visible;}

	public boolean isSelected() {
		if (style != STYLE_CHECK && style != STYLE_RADIO) {
			throw new RuntimeException("Style is not STYLE_CHECK or STYLE_RADIO");
		}
		if (data == null) {
			throw new RuntimeException("Item is neither selected or deselected");
		}
		if (!(data instanceof Boolean)) {
			throw new RuntimeException("Invalid data assigned to menu item, should be boolean: " + data);
		}
		return ((Boolean)data).booleanValue();
	}

	public void setContext(MenuContextImpl context) {
		this.menuContext = context;
	}

	// @see org.gudy.azureus2.plugins.ui.menus.MenuItem#setSubmenuBuilder(org.gudy.azureus2.plugins.ui.menus.MenuBuilder)
	public void setSubmenuBuilder(MenuBuilder builder) {
		this.builder = builder;
	}

	public MenuBuilder getSubmenuBuilder() {
		return builder;
	}
}