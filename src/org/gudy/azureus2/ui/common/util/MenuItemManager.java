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
package org.gudy.azureus2.ui.common.util;

import java.util.*;

import org.gudy.azureus2.core3.util.AEMonitor;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.plugins.ui.menus.MenuContext;
import org.gudy.azureus2.plugins.ui.menus.MenuItem;
import org.gudy.azureus2.plugins.ui.menus.MenuManager;
import org.gudy.azureus2.pluginsimpl.local.ui.menus.MenuContextImpl;

/**
 * Similar to TableContextMenuManager - this keeps references to created menu
 * items by plugins to be used by external code.
 *
 * @author amc1
 */
public class MenuItemManager {

	private static MenuItemManager instance;

	private static AEMonitor class_mon = new AEMonitor("MenuManager");

	/*
	 * Holds all the MenuItem objects.
	 *    key = MENU_* type (see MenuManager in the plugin API)
	 *    value = Map: key = menu key value = MenuItem object
	 * Update parg: actually we can have multiple view instances that individually add and remove
	 * their own sets of menus. Before this was updated to hold a list of MenuItems against a given
	 * resource/menu-id then when one view was deleted it removed the menus for all view instances
	 * and therefore remaining view instances lost their menus.
	 * The change to maintain a list isn't perfect either as individual MenuItems can have view instance
	 * specific data (e.g. set via setData) but by the time we get here we have no concept of view instance
	 * and therefore can't return/delete the correct one. However, this fix is at least better than
	 * losing menus completely...
	 */

	private Map<String, Map<String, List<MenuItem>>> itemsMap;

	private AEMonitor itemsMon = new AEMonitor("MenuManager:items");

	private ArrayList<MenuItemManagerListener> listeners = new ArrayList<MenuItemManagerListener>(0);

	private MenuItemManager() {
		itemsMap = new HashMap<String, Map<String, List<MenuItem>>>();
	}

	/**
	 * Return the static MenuManager instance
	 */
	public static MenuItemManager getInstance() {
		try {
			class_mon.enter();
			if (instance == null)
				instance = new MenuItemManager();
			return instance;
		} finally {
			class_mon.exit();
		}
	}

	public void addMenuItem(MenuItem item) {
		try {
			String name = item.getResourceKey();
			String sMenuID = item.getMenuID();
			try {
				itemsMon.enter();
				Map<String, List<MenuItem>> mTypes = itemsMap.get(sMenuID);
				if (mTypes == null) {
					// LinkedHashMap to preserve order
					mTypes = new LinkedHashMap<String, List<MenuItem>>();
					itemsMap.put(sMenuID, mTypes);
				}
				List<MenuItem> mis = mTypes.get(name);
				if (mis == null) {
					mis = new ArrayList<MenuItem>(1);
					mTypes.put(name, mis);
				}
				mis.add(item);
			} finally {
				itemsMon.exit();
			}
		} catch (Exception e) {
			System.out.println("Error while adding Menu Item");
			Debug.printStackTrace(e);
		}
	}

	public void removeAllMenuItems(String sMenuID) {
		try {
			itemsMon.enter();
			Map<String, List<MenuItem>> mTypes = itemsMap.get(sMenuID);
			if (mTypes == null) {
				return;
			}
				// remove one MenuItem for each resource-id in the assumption that each view instance
				// will have added one entry for each
			Iterator<Map.Entry<String, List<MenuItem>>> it = mTypes.entrySet().iterator();
			while (it.hasNext()) {
				List<MenuItem> mis = it.next().getValue();
				if (mis.size() > 0) {
					mis.remove(0);	// pick one at random, not great but there you go
				}
				if (mis.size() == 0) {
					it.remove();
				}
			}
			if (mTypes.isEmpty()) {
				itemsMap.remove(sMenuID);
			}
		} finally {
			itemsMon.exit();
		}
	}

	public void removeMenuItem(MenuItem item) {
		try {
			itemsMon.enter();
			Map<String, List<MenuItem>> menu_item_map = itemsMap.get(item.getMenuID());
			if (menu_item_map != null) {
				List<MenuItem> mis = menu_item_map.get(item.getResourceKey());
				if (mis != null) {
					if (!mis.remove( item)) {
						if (mis.size() > 0) {
							mis.remove(0);
						}
					}
					if (mis.size() == 0) {
						 menu_item_map.remove(item.getResourceKey());
					}
				}
				if (menu_item_map.isEmpty()) {
					itemsMap.remove(item.getMenuID());
				}
			}
		} finally {
			itemsMon.exit();
		}
	}

	/**
	 * See {@link MenuManager} for MENU_ Constants.<BR>
	 * For {@link MenuContext}, use the hack {@link MenuContextImpl#context}
	 */
	public MenuItem[] getAllAsArray(String sMenuID) {
		if (sMenuID != null) {
			triggerMenuItemQuery(sMenuID);
		}
		try {
			itemsMon.enter();
			Map<String, List<MenuItem>> localMenuItemMap = itemsMap.get(sMenuID);
			Map<String, List<MenuItem>> globalMenuItemMap = itemsMap.get(null);

			if (localMenuItemMap == null && globalMenuItemMap == null) {
				return new MenuItem[0];
			}
			if (sMenuID == null) {localMenuItemMap = null;}
			ArrayList<MenuItem> l = new ArrayList<MenuItem>();
			if (localMenuItemMap != null) {
				for ( List<MenuItem> mis: localMenuItemMap.values()) {
					if (mis.size() > 0) {
						l.add(mis.get(0));
					}
				}
			}
			if (globalMenuItemMap != null) {
				for ( List<MenuItem> mis: globalMenuItemMap.values()) {
					if (mis.size() > 0) {
						l.add( mis.get(0));
					}
				}
			}
			return l.toArray(new MenuItem[l.size()]);
		} finally {
			itemsMon.exit();
		}
	}

	/**
	 * See {@link MenuManager} for MENU_ Constants.<BR>
	 * For {@link MenuContext}, use the hack {@link MenuContextImpl#context}
	 */
	public MenuItem[] getAllAsArray(String[] menu_ids) {
		ArrayList<MenuItem> l  = new ArrayList<MenuItem>();
		for (int i=0; i<menu_ids.length; i++) {
			if (menu_ids[i] != null) {
				triggerMenuItemQuery(menu_ids[i]);
			}
			extractMenuItems(menu_ids[i], l);
		}
		extractMenuItems(null, l);
		return l.toArray(new MenuItem[l.size()]);
	}

	private void extractMenuItems(String menu_id, ArrayList<MenuItem> l) {
		try {
			itemsMon.enter();

			Map<String, List<MenuItem>> menu_map = itemsMap.get(menu_id);

			if (menu_map != null) {

				for ( List<MenuItem> mis: menu_map.values()) {

					if (mis.size() > 0) {

						l.add( mis.get(0));
					}
				}
			}
		} finally {

			itemsMon.exit();
		}
	}

	public void addListener(MenuItemManagerListener l) {
		synchronized (listeners) {
			if (!listeners.contains(l)) {
				listeners.add(l);
			}
		}
	}

	public void removeListener(MenuItemManagerListener l) {
		synchronized (listeners) {
			listeners.remove(l);
		}
	}

	private void triggerMenuItemQuery(String id) {
		MenuItemManagerListener[] listenersArray;
		synchronized (listeners) {
			listenersArray = listeners.toArray(new MenuItemManagerListener[0]);
		}
		for (MenuItemManagerListener l : listenersArray) {
			try {
				l.queryForMenuItem(id);
			} catch (Exception e) {
				Debug.out(e);
			}
		}
	}

	public void triggerMenuItemCleanup(String id) {
		MenuItemManagerListener[] listenersArray;
		synchronized (listeners) {
			listenersArray = listeners.toArray(new MenuItemManagerListener[0]);
		}
		for (MenuItemManagerListener l : listenersArray) {
			try {
				l.cleanupMenuItem(id);
			} catch (Exception e) {
				Debug.out(e);
			}
		}
	}
}
