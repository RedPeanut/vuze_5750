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

package com.aelitis.azureus.ui.swt.shells.main;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.widgets.*;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.config.ParameterListener;
import org.gudy.azureus2.core3.config.impl.ConfigurationDefaults;
import org.gudy.azureus2.core3.util.Constants;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.SystemProperties;
import org.gudy.azureus2.plugins.ui.toolbar.UIToolBarActivationListener;
import org.gudy.azureus2.plugins.ui.toolbar.UIToolBarItem;
import org.gudy.azureus2.plugins.ui.toolbar.UIToolBarManager;
import org.gudy.azureus2.ui.swt.KeyBindings;
import org.gudy.azureus2.ui.swt.MenuBuildUtils;
import org.gudy.azureus2.ui.swt.Messages;
import org.gudy.azureus2.ui.swt.Utils;
import org.gudy.azureus2.ui.swt.mainwindow.*;
import org.gudy.azureus2.ui.swt.pluginsimpl.UIToolBarManagerImpl;

import com.aelitis.azureus.core.cnetwork.ContentNetwork;
import com.aelitis.azureus.core.util.FeatureAvailability;
import com.aelitis.azureus.ui.UIFunctionsManager;
import com.aelitis.azureus.ui.mdi.MultipleDocumentInterface;
import com.aelitis.azureus.ui.selectedcontent.SelectedContentManager;
import com.aelitis.azureus.ui.skin.SkinConstants;
import com.aelitis.azureus.ui.swt.feature.FeatureManagerUI;
import com.aelitis.azureus.ui.swt.skin.SWTSkin;
import com.aelitis.azureus.ui.swt.skin.SWTSkinFactory;
import com.aelitis.azureus.ui.swt.skin.SWTSkinObject;
import com.aelitis.azureus.ui.swt.skin.SWTSkinUtils;
import com.aelitis.azureus.ui.swt.views.skin.SBC_PlusFTUX;
import com.aelitis.azureus.ui.swt.views.skin.SkinViewManager;
import com.aelitis.azureus.ui.swt.views.skin.ToolBarView;
import com.aelitis.azureus.ui.swt.views.skin.sidebar.SideBar;
import com.aelitis.azureus.util.ConstantsVuze;
import com.aelitis.azureus.util.ContentNetworkUtils;

public class MainMenu
	implements IMainMenu, IMenuConstants
{
	private final static String PREFIX_V2 = "MainWindow.menu";

	private final static String PREFIX_V3 = "v3.MainWindow.menu";

	private Menu menuBar;

	/**
	 * Creates the main menu on the supplied shell
	 *
	 * @param shell
	 */
	public MainMenu(SWTSkin skin, final Shell shell) {
		if (null == skin) {
			System.err.println("MainMenu: The parameter [SWTSkin skin] can not be null");
			return;
		}
		buildMenu(shell);
	}

	private void buildMenu(Shell parent) {
		//The Main Menu
		menuBar = new Menu(parent, SWT.BAR);
		parent.setMenuBar(menuBar);
		addFileMenu();
		//addViewMenu();
		addSimpleViewMenu();
		addCommunityMenu();
		addToolsMenu();
		/*
		 * The Torrents menu is a user-configured option
		 */
		if (COConfigurationManager.getBooleanParameter("show_torrents_menu")) {
			addTorrentMenu();
		}
		if (!Constants.isWindows) {
			addWindowMenu();
		}
		// ===== Debug menu (development only)====
		if (org.gudy.azureus2.core3.util.Constants.isCVSVersion()) {
			final Menu menuDebug = org.gudy.azureus2.ui.swt.mainwindow.DebugMenuHelper.createDebugMenuItem(menuBar);
			menuDebug.addMenuListener(new MenuListener() {
				public void menuShown(MenuEvent e) {
					MenuItem[] items = menuDebug.getItems();
					Utils.disposeSWTObjects(items);
					DebugMenuHelper.createDebugMenuItem(menuDebug);
					MenuFactory.addSeparatorMenuItem(menuDebug);
					MenuItem menuItem = new MenuItem(menuDebug, SWT.PUSH);
					menuItem.setText("Log Views");
					menuItem.setEnabled(false);
					PluginsMenuHelper.getInstance().buildPluginLogsMenu(menuDebug);
				}
				public void menuHidden(MenuEvent e) {
				}
			});
		}
		addV3HelpMenu();
		
		/*
		 * Enabled/disable menus based on what ui mode we're in; this method call controls
		 * which menus are enabled when we're in Vuze vs. Vuze Advanced
		 */
		MenuFactory.updateEnabledStates(menuBar);
	}

	/**
	 * Creates the File menu and all its children
	 */
	private void addFileMenu() {
		MenuItem fileItem = MenuFactory.createFileMenuItem(menuBar);
		final Menu fileMenu = fileItem.getMenu();
		buildFileMenu(fileMenu);

		fileMenu.addListener(SWT.Show, new Listener() {
			public void handleEvent(Event event) {
				MenuItem[] menuItems = fileMenu.getItems();
				for (int i = 0; i < menuItems.length; i++) {
					menuItems[i].dispose();
				}

				buildFileMenu(fileMenu);
			}
		});
	}

	/**
	 * Builds the File menu dynamically
	 * @param fileMenu
	 */
	private void buildFileMenu(Menu fileMenu) {

		MenuItem openMenuItem = MenuFactory.createOpenMenuItem(fileMenu);
		Menu openSubMenu = openMenuItem.getMenu();
		MenuFactory.addOpenTorrentMenuItem(openSubMenu);
		MenuFactory.addOpenURIMenuItem(openSubMenu);
		MenuFactory.addOpenTorrentForTrackingMenuItem(openSubMenu);
		MenuFactory.addOpenVuzeFileMenuItem(openSubMenu);

		int userMode = COConfigurationManager.getIntParameter("User Mode");

		if (userMode > 0) {
			Menu shareSubMenu = MenuFactory.createShareMenuItem(fileMenu).getMenu();
			MenuFactory.addShareFileMenuItem(shareSubMenu);
			MenuFactory.addShareFolderMenuItem(shareSubMenu);
			MenuFactory.addShareFolderContentMenuItem(shareSubMenu);
			MenuFactory.addShareFolderContentRecursiveMenuItem(shareSubMenu);
		}

		MenuFactory.addCreateMenuItem(fileMenu);

		if (FeatureManagerUI.enabled) {
			MenuFactory.addSeparatorMenuItem(fileMenu);
			
	  		MenuFactory.addMenuItem(fileMenu, "menu.plus", new Listener() {
	  			public void handleEvent(Event event) {
	  				SBC_PlusFTUX.setSourceRef("menu-file");
	
	  				MultipleDocumentInterface mdi = UIFunctionsManager.getUIFunctions().getMDI();
	  				mdi.showEntryByID(MultipleDocumentInterface.SIDEBAR_SECTION_PLUS);
	  			}
	  		});
		}

		MenuFactory.addSeparatorMenuItem(fileMenu);
		MenuFactory.addCloseWindowMenuItem(fileMenu);
		MenuFactory.addCloseDetailsMenuItem(fileMenu);
		MenuFactory.addCloseDownloadBarsToMenu(fileMenu);

		MenuFactory.addSeparatorMenuItem(fileMenu);
		MenuFactory.createTransfersMenuItem(fileMenu);

		/*
		 * No need for restart and exit on OS X since it's already handled on the application menu
		 */
		if (!Utils.isCarbon) {
			MenuFactory.addSeparatorMenuItem(fileMenu);
			MenuFactory.addRestartMenuItem(fileMenu);
		}
		if (!Constants.isOSX) {
			MenuFactory.addExitMenuItem(fileMenu);
		}
	}

	private void addSimpleViewMenu() {
		try {
			MenuItem viewItem = MenuFactory.createViewMenuItem(menuBar);
			final Menu viewMenu = viewItem.getMenu();

			viewMenu.addListener(SWT.Show, new Listener() {
				public void handleEvent(Event event) {
					Utils.disposeSWTObjects(viewMenu.getItems());
					buildSimpleViewMenu(viewMenu,-1);
				}
			});

				// hack to handle key binding before menu is actually created...

			final KeyBindings.KeyBindingInfo binding_info = KeyBindings.getKeyBindingInfo("v3.MainWindow.menu.view." + SkinConstants.VIEWID_PLUGINBAR);

			if (binding_info != null) {
				Display.getDefault().addFilter(SWT.KeyDown, new Listener() {
					public void handleEvent(Event event) {
						if (event.keyCode == binding_info.accelerator) {
							Utils.disposeSWTObjects(viewMenu.getItems());
							buildSimpleViewMenu(viewMenu, event.keyCode);
						}
					}
				});
			}
		} catch (Exception e) {
			Debug.out("Error creating View Menu", e);
		}
	}

	/**
	 * @param viewMenu
	 *
	 * @since 4.5.0.3
	 */
	private void buildSimpleViewMenu(final Menu viewMenu, int accelerator) {
		try {

			MenuFactory.addMenuItem(viewMenu, SWT.CHECK, PREFIX_V3 + ".view.sidebar",
					new Listener() {
						public void handleEvent(Event event) {
							SideBar sidebar = (SideBar) SkinViewManager.getByClass(SideBar.class);
							if (sidebar != null) {
								sidebar.flipSideBarVisibility();
							}
						}
					});

			if (COConfigurationManager.getIntParameter("User Mode") > 1) {

				SWTSkin skin = SWTSkinFactory.getInstance();

				SWTSkinObject plugin_bar = skin.getSkinObject(SkinConstants.VIEWID_PLUGINBAR);

				if (plugin_bar != null) {

					MenuItem mi =
						MainMenu.createViewMenuItem(skin, viewMenu,
							"v3.MainWindow.menu.view." + SkinConstants.VIEWID_PLUGINBAR,
							SkinConstants.VIEWID_PLUGINBAR + ".visible",
							SkinConstants.VIEWID_PLUGINBAR, true, -1);

					if (accelerator != -1 && mi.getAccelerator() == accelerator) {

						Listener[] listeners = mi.getListeners(SWT.Selection);

						for (Listener l: listeners) {

							try {
								l.handleEvent(null);

							} catch (Throwable e) {
							}
						}
					}
				}
			}

			/////////

			MenuItem itemStatusBar = MenuFactory.createTopLevelMenuItem(viewMenu,
					"v3.MainWindow.menu.view.statusbar");
			itemStatusBar.setText(itemStatusBar.getText());
			Menu menuStatusBar = itemStatusBar.getMenu();

			final String[] statusAreaLangs = {
				"ConfigView.section.style.status.show_sr",
				"ConfigView.section.style.status.show_nat",
				"ConfigView.section.style.status.show_ddb",
				"ConfigView.section.style.status.show_ipf",
			};
			final String[] statusAreaConfig = {
				"Status Area Show SR",
				"Status Area Show NAT",
				"Status Area Show DDB",
				"Status Area Show IPF",
			};

			for (int i = 0; i < statusAreaConfig.length; i++) {
				final String configID = statusAreaConfig[i];
				String langID = statusAreaLangs[i];

				final MenuItem item = new MenuItem(menuStatusBar, SWT.CHECK);
				Messages.setLanguageText(item, langID);
				item.addListener(SWT.Selection, new Listener() {
					public void handleEvent(Event event) {
						COConfigurationManager.setParameter(configID,
								!COConfigurationManager.getBooleanParameter(configID));
					}
				});
				menuStatusBar.addListener(SWT.Show, new Listener() {
					public void handleEvent(Event event) {
						item.setSelection(COConfigurationManager.getBooleanParameter(configID));
					}
				});
			}

			/////////

			if (Constants.isWindows) {
				MenuFactory.addSeparatorMenuItem(viewMenu);
			}

			boolean needsSep = false;
			boolean enabled = COConfigurationManager.getBooleanParameter("Beta Programme Enabled");
			if (enabled) {
				MenuFactory.addMenuItem(viewMenu, SWT.CHECK, PREFIX_V2 + ".view.beta",
						new Listener() {
							public void handleEvent(Event event) {
								MultipleDocumentInterface mdi = UIFunctionsManager.getUIFunctions().getMDI();
								if (mdi != null) {
									mdi.showEntryByID(MultipleDocumentInterface.SIDEBAR_SECTION_BETAPROGRAM);
								}
							}
				});
				needsSep = true;
			}

			if (Constants.isWindows && FeatureAvailability.isGamesEnabled()) {
  			MenuFactory.addMenuItem(viewMenu, PREFIX_V3 + ".games", new Listener() {
  				public void handleEvent(Event event) {
  					MultipleDocumentInterface mdi = UIFunctionsManager.getUIFunctions().getMDI();
  					mdi.showEntryByID(SideBar.SIDEBAR_SECTION_GAMES);
  				}
  			});
				needsSep = true;
			}


			if (needsSep) {
				MenuFactory.addSeparatorMenuItem(viewMenu);
			}

			needsSep = PluginsMenuHelper.getInstance().buildViewMenu(viewMenu, viewMenu.getShell());

			if (COConfigurationManager.getBooleanParameter("Library.EnableSimpleView")) {

				if (needsSep) {
					MenuFactory.addSeparatorMenuItem(viewMenu);
				}

					// Ubuntu Unity (14.04) with SWT 4508 crashes when global View menu triggered as it appears
					// that radio menu items aren't supported
					// https://bugs.eclipse.org/bugs/show_bug.cgi?id=419729#c9

				int simple_advanced_menu_type = Constants.isLinux?SWT.CHECK:SWT.RADIO;

				MenuFactory.addMenuItem(viewMenu, simple_advanced_menu_type, PREFIX_V3
						+ ".view.asSimpleList", new Listener() {
					public void handleEvent(Event event) {
						UIToolBarManager tb = UIToolBarManagerImpl.getInstance();
						if (tb != null) {
							UIToolBarItem item = tb.getToolBarItem("modeBig");
							if (item != null) {
								item.triggerToolBarItem(
										UIToolBarActivationListener.ACTIVATIONTYPE_NORMAL,
										SelectedContentManager.convertSelectedContentToObject(null));
							}
						}
					}
				});
				MenuFactory.addMenuItem(viewMenu, simple_advanced_menu_type, PREFIX_V3
						+ ".view.asAdvancedList", new Listener() {
					public void handleEvent(Event event) {
						UIToolBarManager tb = UIToolBarManagerImpl.getInstance();
						if (tb != null) {
							UIToolBarItem item = tb.getToolBarItem("modeSmall");
							if (item != null) {
								item.triggerToolBarItem(
										UIToolBarActivationListener.ACTIVATIONTYPE_NORMAL,
										SelectedContentManager.convertSelectedContentToObject(null));
							}
						}
					}
				});
			}

			viewMenu.addMenuListener(new MenuListener() {

				public void menuShown(MenuEvent e) {

					MenuItem sidebarMenuItem = MenuFactory.findMenuItem(viewMenu,
							PREFIX_V3 + ".view.sidebar");
					if (sidebarMenuItem != null) {
						MultipleDocumentInterface mdi = UIFunctionsManager.getUIFunctions().getMDI();
						if (mdi != null) {
							sidebarMenuItem.setSelection(mdi.isVisible());
						}
					}

					MenuItem itemShowText = MenuFactory.findMenuItem(viewMenu, PREFIX_V3
							+ ".view.toolbartext");
					if (itemShowText != null) {
						ToolBarView tb = (ToolBarView) SkinViewManager.getByClass(ToolBarView.class);
						if (tb != null) {
							itemShowText.setSelection(tb.getShowText());
						}
					}

					if (COConfigurationManager.getBooleanParameter("Library.EnableSimpleView")) {

						MenuItem itemShowAsSimple = MenuFactory.findMenuItem(viewMenu,
								PREFIX_V3 + ".view.asSimpleList");
						if (itemShowAsSimple != null) {
							UIToolBarManager tb = UIToolBarManagerImpl.getInstance();
							if (tb != null) {
								UIToolBarItem item = tb.getToolBarItem("modeBig");
								long state = item == null ? 0 : item.getState();
								itemShowAsSimple.setEnabled((state & UIToolBarItem.STATE_ENABLED) > 0);
								itemShowAsSimple.setSelection((state & UIToolBarItem.STATE_DOWN) > 0);
							}
						}
						MenuItem itemShowAsAdv = MenuFactory.findMenuItem(viewMenu, PREFIX_V3
								+ ".view.asAdvancedList");
						if (itemShowAsAdv != null) {
							UIToolBarManager tb = UIToolBarManagerImpl.getInstance();
							if (tb != null) {
								UIToolBarItem item = tb.getToolBarItem("modeSmall");
								long state = item == null ? 0 : item.getState();
								itemShowAsAdv.setEnabled((state & UIToolBarItem.STATE_ENABLED) > 0);
								itemShowAsAdv.setSelection((state & UIToolBarItem.STATE_DOWN) > 0);
							}
						}
					}
				}

				public void menuHidden(MenuEvent e) {
				}
			});
		} catch (Exception e) {
			Debug.out("Error creating View Menu", e);
		}
	}

	/**
	 * Creates the Tools menu and all its children
	 */
	private void addToolsMenu() {
		MenuItem toolsItem = MenuFactory.createToolsMenuItem(menuBar);
		Menu toolsMenu = toolsItem.getMenu();

		MenuFactory.addMyTrackerMenuItem(toolsMenu);
		MenuFactory.addMySharesMenuItem(toolsMenu);
		MenuFactory.addConsoleMenuItem(toolsMenu);
		MenuFactory.addStatisticsMenuItem(toolsMenu);
		MenuFactory.addSpeedLimitsToMenu(toolsMenu);

		MenuFactory.addTransferBarToMenu(toolsMenu);
		MenuFactory.addAllPeersMenuItem(toolsMenu);
		MenuFactory.addClientStatsMenuItem(toolsMenu);
		MenuFactory.addBlockedIPsMenuItem(toolsMenu);

		MenuFactory.addSeparatorMenuItem(toolsMenu);
		MenuFactory.createPluginsMenuItem(toolsMenu, true);

		MenuFactory.addPairingMenuItem(toolsMenu);

		MenuFactory.addOptionsMenuItem(toolsMenu);

	}

	/**
	 * Creates the Help menu and all its children
	 */
	private void addV3HelpMenu() {
		MenuItem helpItem = MenuFactory.createHelpMenuItem(menuBar);
		Menu helpMenu = helpItem.getMenu();

		if (!Constants.isOSX) {
			/*
			 * The 'About' menu is on the application menu on OSX
			 */
			MenuFactory.addAboutMenuItem(helpMenu);
			MenuFactory.addSeparatorMenuItem(helpMenu);
		}

		MenuFactory.addMenuItem(helpMenu, PREFIX_V3 + ".getting_started",
				new Listener() {
					public void handleEvent(Event event) {
						MultipleDocumentInterface mdi = UIFunctionsManager.getUIFunctions().getMDI();
						if (mdi != null) {
							mdi.showEntryByID(SideBar.SIDEBAR_SECTION_WELCOME);
						}
					}
				});

		MenuFactory.addHelpSupportMenuItem(
				helpMenu,
				ContentNetworkUtils.getUrl(
				ConstantsVuze.getDefaultContentNetwork(), ContentNetwork.SERVICE_SUPPORT));

		MenuFactory.addHealthMenuItem(helpMenu);

		MenuFactory.addReleaseNotesMenuItem(helpMenu);

		if (!SystemProperties.isJavaWebStartInstance()) {
			MenuFactory.addSeparatorMenuItem(helpMenu);
			MenuFactory.addCheckUpdateMenuItem(helpMenu);
			MenuFactory.addBetaMenuItem(helpMenu);
			MenuFactory.addVoteMenuItem(helpMenu);
		}

		if (FeatureManagerUI.enabled) {
  		MenuFactory.addMenuItem(helpMenu, "menu.register", new Listener() {
  			public void handleEvent(Event event) {
  				FeatureManagerUI.openLicenceEntryWindow(false, null);
  			}
  		});
		}

		MenuFactory.addDonationMenuItem(helpMenu);

		MenuFactory.addSeparatorMenuItem(helpMenu);
		MenuFactory.addConfigWizardMenuItem(helpMenu);
		MenuFactory.addNatTestMenuItem(helpMenu);
		MenuFactory.addNetStatusMenuItem(helpMenu);
		MenuFactory.addSpeedTestMenuItem(helpMenu);
		MenuFactory.addAdvancedHelpMenuItem(helpMenu);

		MenuFactory.addSeparatorMenuItem(helpMenu);
		MenuFactory.addDebugHelpMenuItem(helpMenu);

	}

	/**
	 * Creates the Window menu and all its children
	 */
	private void addWindowMenu() {
		MenuItem menu_window = MenuFactory.createWindowMenuItem(menuBar);
		Menu windowMenu = menu_window.getMenu();

		MenuFactory.addMinimizeWindowMenuItem(windowMenu);
		MenuFactory.addZoomWindowMenuItem(windowMenu);
		MenuFactory.addSeparatorMenuItem(windowMenu);
		MenuFactory.addBringAllToFrontMenuItem(windowMenu);

		MenuFactory.addSeparatorMenuItem(windowMenu);
		MenuFactory.appendWindowMenuItems(windowMenu);
	}

	/**
	 * Creates the Torrent menu and all its children
	 */
	private void addTorrentMenu() {
		MenuFactory.createTorrentMenuItem(menuBar);
	}

	public Menu getMenu(String id) {
		if (MENU_ID_MENU_BAR.equals(id)) {
			return menuBar;
		}
		return MenuFactory.findMenu(menuBar, id);
	}

	private void addCommunityMenu() {
		MenuItem item = MenuFactory.createTopLevelMenuItem(menuBar,	MENU_ID_COMMUNITY);

		final Menu communityMenu = item.getMenu();

		communityMenu.addListener(
			SWT.Show,
			new Listener() {
				public void handleEvent( Event event) {
					Utils.disposeSWTObjects( communityMenu.getItems());

					MenuFactory.addMenuItem(communityMenu, MENU_ID_COMMUNITY_FORUMS,
						new Listener() {
							public void handleEvent(Event e) {
								Utils.launch(ContentNetworkUtils.getUrl(
										ConstantsVuze.getDefaultContentNetwork(),
										ContentNetwork.SERVICE_FORUMS));
							}
						});

					MenuFactory.addMenuItem(communityMenu, MENU_ID_COMMUNITY_WIKI,
						new Listener() {
							public void handleEvent(Event e) {
								Utils.launch(ContentNetworkUtils.getUrl(
										ConstantsVuze.getDefaultContentNetwork(),
										ContentNetwork.SERVICE_WIKI));
							}
						});

					MenuBuildUtils.addChatMenu(communityMenu, MENU_ID_COMMUNITY_CHAT, "General: Help");

					MenuFactory.addMenuItem(communityMenu, MENU_ID_COMMUNITY_BLOG,
						new Listener() {
							public void handleEvent(Event e) {
								Utils.launch(ContentNetworkUtils.getUrl(
										ConstantsVuze.getDefaultContentNetwork(),
										ContentNetwork.SERVICE_BLOG));
							}
						});
				}
			});
	}

	//====================================

	/**
	 * @deprecated This method has been replaced with {@link #getMenu(String)};
	 * use {@link #getMenu(IMenuConstants.MENU_ID_MENU_BAR)} instead
	 * @return the menuBar
	 */
	public Menu getMenuBar() {
		return menuBar;
	}

	/**
	 * @param viewMenu
	 * @param string
	 * @param string2
	 */
	public static MenuItem createViewMenuItem(final SWTSkin skin, Menu viewMenu,
			final String textID, final String configID, final String viewID,
			final boolean fast, int menuIndex) {
		MenuItem item;

		if (!ConfigurationDefaults.getInstance().doesParameterDefaultExist(configID)) {
			COConfigurationManager.setBooleanDefault(configID, true);
		}

		item = MenuFactory.addMenuItem(viewMenu, SWT.CHECK, menuIndex, textID,
				new Listener() {
					public void handleEvent(Event event) {
						SWTSkinObject skinObject = skin.getSkinObject(viewID);
						if (skinObject != null) {
							boolean newVisibility = !skinObject.isVisible();

							SWTSkinUtils.setVisibility(skin, configID, viewID, newVisibility,
									true, fast);
						}
					}
				});
		SWTSkinUtils.setVisibility(skin, configID, viewID,
				COConfigurationManager.getBooleanParameter(configID), false, true);

		final MenuItem itemViewPluginBar = item;
		final ParameterListener listener = new ParameterListener() {
			public void parameterChanged(String parameterName) {
				itemViewPluginBar.setSelection(COConfigurationManager.getBooleanParameter(parameterName));
			}
		};

		COConfigurationManager.addAndFireParameterListener(configID, listener);
		item.addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent e) {
				COConfigurationManager.removeParameterListener(configID, listener);
			}
		});

		return item;
	}

	// backward compat..
	public static void setVisibility(SWTSkin skin, String configID,
			String viewID, boolean visible) {
		SWTSkinUtils.setVisibility(skin, configID, viewID, visible, true, false);
	}

	// backward compat..
	public static void setVisibility(SWTSkin skin, String configID,
			String viewID, boolean visible, boolean save) {
		SWTSkinUtils.setVisibility(skin, configID, viewID, visible, save, false);
	}

}
