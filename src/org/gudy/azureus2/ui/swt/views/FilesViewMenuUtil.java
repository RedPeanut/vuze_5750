/**
 * Created on May 12, 2010
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package org.gudy.azureus2.ui.swt.views;

import java.io.File;
import java.util.*;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.*;
import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.disk.DiskManagerFileInfo;
import org.gudy.azureus2.core3.download.DownloadManager;
import org.gudy.azureus2.core3.download.DownloadManagerState;
import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.util.AERunnable;
import org.gudy.azureus2.core3.util.AESemaphore;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.FileUtil;
import org.gudy.azureus2.plugins.sharing.ShareManager;
import org.gudy.azureus2.plugins.ui.UIInputReceiver;
import org.gudy.azureus2.plugins.ui.UIInputReceiverListener;
import org.gudy.azureus2.plugins.ui.menus.MenuManager;
import org.gudy.azureus2.pluginsimpl.local.PluginCoreUtils;
import org.gudy.azureus2.ui.common.util.MenuItemManager;
import org.gudy.azureus2.ui.swt.MenuBuildUtils;
import org.gudy.azureus2.ui.swt.Messages;
import org.gudy.azureus2.ui.swt.SimpleTextEntryWindow;
import org.gudy.azureus2.ui.swt.Utils;
import org.gudy.azureus2.ui.swt.mainwindow.ClipboardCopy;
import org.gudy.azureus2.ui.swt.mainwindow.MenuFactory;
import org.gudy.azureus2.ui.swt.sharing.ShareUtils;
import org.gudy.azureus2.ui.swt.shells.AdvRenameWindow;
import org.gudy.azureus2.ui.swt.shells.MessageBoxShell;
import org.gudy.azureus2.ui.swt.views.utils.ManagerUtils;

import com.aelitis.azureus.core.AzureusCoreOperation;
import com.aelitis.azureus.core.AzureusCoreOperationTask;
import com.aelitis.azureus.core.util.LinkFileMap;
import com.aelitis.azureus.ui.UserPrompterResultListener;
import com.aelitis.azureus.ui.common.table.TableRowCore;
import com.aelitis.azureus.ui.common.table.TableView;

/**
 * @author TuxPaper
 * @created May 12, 2010
 *
 */
public class FilesViewMenuUtil
{
	public static final Object PRIORITY_HIGH 			= new Object();
	public static final Object PRIORITY_NORMAL 			= new Object();
	public static final Object PRIORITY_LOW 			= new Object();
	public static final Object PRIORITY_NUMERIC 		= new Object();
	public static final Object PRIORITY_NUMERIC_AUTO 	= new Object();
	public static final Object PRIORITY_SKIPPED 		= new Object();
	public static final Object PRIORITY_DELETE 			= new Object();

	public static void
	fillMenu(
		final TableView<?> 				tv,
		final Menu 						menu,
		final DownloadManager[] 		manager_list,
		final DiskManagerFileInfo[][] 	files_list) {
		Shell shell = menu.getShell();

		final List<DiskManagerFileInfo>	all_files = new ArrayList<DiskManagerFileInfo>();

		for (DiskManagerFileInfo[] files: files_list) {

			all_files.addAll(Arrays.asList( files));
		}

		boolean hasSelection = (all_files.size() > 0);

		final MenuItem itemOpen = new MenuItem(menu, SWT.PUSH);
		Messages.setLanguageText(itemOpen, "FilesView.menu.open");
		Utils.setMenuItemImage(itemOpen, "run");
		// Invoke open on enter, double click
		menu.setDefaultItem(itemOpen);

		// Explore  (Copied from MyTorrentsView)
		final boolean use_open_containing_folder = COConfigurationManager.getBooleanParameter("MyTorrentsView.menu.show_parent_folder_enabled");
		final MenuItem itemExplore = new MenuItem(menu, SWT.PUSH);
		Messages.setLanguageText(itemExplore, "MyTorrentsView.menu."
				+ (use_open_containing_folder ? "open_parent_folder" : "explore"));
		itemExplore.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event event) {
				for (int i = all_files.size() - 1; i >= 0; i--) {
					DiskManagerFileInfo info = (DiskManagerFileInfo) all_files.get(i);
					if (info != null) {
						ManagerUtils.open(info, use_open_containing_folder);
					}
				}
			}
		});
		itemExplore.setEnabled(hasSelection);

			// open in browser

		final Menu menuBrowse = new Menu(menu.getShell(),SWT.DROP_DOWN);
		final MenuItem itemBrowse = new MenuItem(menu, SWT.CASCADE);
		Messages.setLanguageText(itemBrowse, "MyTorrentsView.menu.browse");
		itemBrowse.setMenu(menuBrowse);


		final MenuItem itemBrowsePublic = new MenuItem(menuBrowse, SWT.PUSH);
		itemBrowsePublic.setText(MessageText.getString("label.public" ) + "...");
		itemBrowsePublic.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event event) {
				for (int i = all_files.size() - 1; i >= 0; i--) {
					DiskManagerFileInfo info = (DiskManagerFileInfo)all_files.get(i);
					if (info != null) {
						ManagerUtils.browse(info, false, true);
					}
				}
			}
		});

		final MenuItem itemBrowseAnon = new MenuItem(menuBrowse, SWT.PUSH);
		itemBrowseAnon.setText(MessageText.getString("label.anon" ) + "...");
		itemBrowseAnon.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event event) {
				for (int i = all_files.size() - 1; i >= 0; i--) {
					DiskManagerFileInfo info = all_files.get(i);
					if (info != null) {
						ManagerUtils.browse(info, true, true);
					}
				}
			}
		});

		new MenuItem(menuBrowse, SWT.SEPARATOR);

		final MenuItem itemBrowseURL = new MenuItem(menuBrowse, SWT.PUSH);
		Messages.setLanguageText(itemBrowseURL, "label.copy.url.to.clip");
		itemBrowseURL.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event event) {
				Utils.getOffOfSWTThread(
					new AERunnable() {
						@Override
						public void runSupport() {
							String url = ManagerUtils.browse(all_files.get(0), true, false);
							if (url != null) {
								ClipboardCopy.copyToClipBoard(url);
							}
						}
					});
			}});

		itemBrowseURL.setEnabled(all_files.size()==1);

		menuBrowse.setEnabled(hasSelection);

			// rename/retarget

		MenuItem itemRenameOrRetarget = null, itemRename = null, itemRetarget = null;

		// "Rename or Retarget" -- Opens up file chooser (can choose new dir and new name)
		itemRenameOrRetarget = new MenuItem(menu, SWT.PUSH);
		Messages.setLanguageText(itemRenameOrRetarget, "FilesView.menu.rename");
		itemRenameOrRetarget.setData("rename", Boolean.valueOf(true));
		itemRenameOrRetarget.setData("retarget", Boolean.valueOf(true));

		// "Quick Rename" -- opens up input box with name
		itemRename = new MenuItem(menu, SWT.PUSH);
		Messages.setLanguageText(itemRename, "FilesView.menu.rename_only");
		itemRename.setData("rename", Boolean.valueOf(true));
		itemRename.setData("retarget", Boolean.valueOf(false));

		// "Move Files" -- opens up directory chooser
		itemRetarget = new MenuItem(menu, SWT.PUSH);
		Messages.setLanguageText(itemRetarget, "FilesView.menu.retarget");
		itemRetarget.setData("rename", Boolean.valueOf(false));
		itemRetarget.setData("retarget", Boolean.valueOf(true));


		// revert

		final MenuItem itemRevertFiles = new MenuItem(menu, SWT.PUSH);
		Messages.setLanguageText(itemRevertFiles, "MyTorrentsView.menu.revertfiles");


		// locate files

		final MenuItem itemLocateFiles = new MenuItem(menu, SWT.PUSH);
		Messages.setLanguageText(itemLocateFiles, "MyTorrentsView.menu.locatefiles");

		// find more

		final MenuItem itemfindMore = new MenuItem(menu, SWT.PUSH);
		Messages.setLanguageText(itemfindMore, "MyTorrentsView.menu.findmorelikethis");

		// clear links
		MenuItem itemClearLinks = null;

		final int userMode = COConfigurationManager.getIntParameter("User Mode");

		if (userMode > 1) {

			itemClearLinks = new MenuItem(menu, SWT.PUSH);
			Messages.setLanguageText(itemClearLinks, "FilesView.menu.clear.links");

		}
			// quick view

		final MenuItem itemQuickView = new MenuItem(menu, SWT.CHECK);
		Messages.setLanguageText(itemQuickView, "MainWindow.menu.quick_view");

		itemQuickView.setEnabled( all_files.size()==1 && Utils.isQuickViewSupported(all_files.get(0)));
		itemQuickView.setSelection( all_files.size()==1 && Utils.isQuickViewActive(all_files.get(0)));

		itemQuickView.addListener(
				SWT.Selection,
				new Listener() {
					public void handleEvent(
						Event arg) {
						Utils.setQuickViewActive( all_files.get(0), itemQuickView.getSelection());
					}
				});

			// alerts

		if (manager_list.length == 1) {
				// lazy for the moment
			MenuFactory.addAlertsMenu( menu, manager_list[0], files_list[0]);
		}
			// personal share

		final MenuItem itemPersonalShare = new MenuItem(menu, SWT.PUSH);
		Messages.setLanguageText(itemPersonalShare, "MyTorrentsView.menu.create_personal_share");

			// priority

		final MenuItem itemPriority = new MenuItem(menu, SWT.CASCADE);
		Messages.setLanguageText(itemPriority, "FilesView.menu.setpriority");

		final Menu menuPriority = new Menu(shell, SWT.DROP_DOWN);
		itemPriority.setMenu(menuPriority);

		final MenuItem itemHigh = new MenuItem(menuPriority, SWT.CASCADE);
		itemHigh.setData("Priority", PRIORITY_HIGH);
		Messages.setLanguageText(itemHigh, "FilesView.menu.setpriority.high");

		final MenuItem itemNormal = new MenuItem(menuPriority, SWT.CASCADE);
		itemNormal.setData("Priority", PRIORITY_NORMAL);
		Messages.setLanguageText(itemNormal, "FilesView.menu.setpriority.normal");

		final MenuItem itemLow = new MenuItem(menuPriority, SWT.CASCADE);
		itemLow.setData("Priority", PRIORITY_LOW);
		Messages.setLanguageText(itemLow, "FileItem.low");

		final MenuItem itemNumeric = new MenuItem(menuPriority, SWT.CASCADE);
		itemNumeric.setData("Priority", PRIORITY_NUMERIC);
		Messages.setLanguageText(itemNumeric, "FilesView.menu.setpriority.numeric");

		final MenuItem itemNumericAuto = new MenuItem(menuPriority, SWT.CASCADE);
		itemNumericAuto.setData("Priority", PRIORITY_NUMERIC_AUTO);
		Messages.setLanguageText(itemNumericAuto, "FilesView.menu.setpriority.numeric.auto");


		final MenuItem itemSkipped = new MenuItem(menuPriority, SWT.CASCADE);
		itemSkipped.setData("Priority", PRIORITY_SKIPPED);
		Messages.setLanguageText(itemSkipped, "FilesView.menu.setpriority.skipped");

		final MenuItem itemDelete = new MenuItem(menuPriority, SWT.CASCADE);
		itemDelete.setData("Priority", PRIORITY_DELETE);
		Messages.setLanguageText(itemDelete, "wizard.multitracker.delete"); // lazy but we're near release

		new MenuItem(menu, SWT.SEPARATOR);

		if (!hasSelection) {
			itemOpen.setEnabled(false);
			itemPriority.setEnabled(false);
			itemRenameOrRetarget.setEnabled(false);
			itemRename.setEnabled(false);
			itemRetarget.setEnabled(false);
			itemLocateFiles.setEnabled(false);
			itemfindMore.setEnabled(false);
			if (itemClearLinks != null) {
				itemClearLinks.setEnabled(false);
			}
			itemPersonalShare.setEnabled(false);

			return;
		}

		boolean	all_persistent			= true;

		boolean open 					= true;
		boolean all_compact 			= true;
		boolean all_dnd_not_deleted 	= true;
		boolean all_high_pri 			= true;
		boolean all_normal_pri 			= true;
		boolean all_low_pri 			= true;
		boolean	all_complete			= true;

		boolean	any_relocated			= false;

		final List<DiskManagerFileInfo>		files_with_links = new ArrayList<DiskManagerFileInfo>();

		for ( int j=0;j<manager_list.length;j++) {

			DownloadManager	manager = manager_list[j];

			int dm_file_count = manager.getNumFileInfos();

			if (!manager.isPersistent()) {
				all_persistent = false;
			}
			DiskManagerFileInfo[] files = files_list[j];

			DownloadManagerState dm_state = manager.getDownloadState();

			int[] storage_types = manager.getStorageType(files);

			for (int i = 0; i < files.length; i++) {

				DiskManagerFileInfo file_info = files[i];

				if (open && file_info.getAccessMode() != DiskManagerFileInfo.READ) {

					open = false;
				}

				boolean isCompact = storage_types[i] == DiskManagerFileInfo.ST_COMPACT || storage_types[i] == DiskManagerFileInfo.ST_REORDER_COMPACT;
				if (all_compact && !isCompact) {
					all_compact = false;
				}

				if (all_dnd_not_deleted || all_high_pri || all_normal_pri || all_low_pri) {
					if (file_info.isSkipped()) {
						all_high_pri = all_normal_pri = all_low_pri = false;
						if (isCompact) {
							all_dnd_not_deleted = false;
						}
					} else {
						all_dnd_not_deleted = false;

						// Only do this check if we need to.
						if (all_high_pri || all_normal_pri || all_low_pri) {
							int file_pri = file_info.getPriority();
							if (file_pri == 0) {
								all_high_pri = all_low_pri = false;
							} else if (file_pri == 1) {
								all_normal_pri = all_low_pri = false;
							} else if (file_pri == -1) {
								all_normal_pri = all_high_pri = false;
							} else {
								all_low_pri = all_normal_pri = all_high_pri = false;
							}
						}
					}
				}

				File file_link 		= file_info.getFile(true);
				File file_nolink 	= file_info.getFile(false);

				if (	file_info.getDownloaded() != file_info.getLength() ||
						file_link.length() != file_info.getLength()) {

					all_complete = false;
				}

					// only support clearing links for multi-file torrents

				if (dm_file_count > 1) {


					if (!file_nolink.getAbsolutePath().equals( file_link.getAbsolutePath())) {

						files_with_links.add(file_info);
					}
				}

				File target = dm_state.getFileLink(file_info.getIndex(), file_nolink);

			   	if (target != null) {

			    	if (target != file_nolink) {

			    		if (!target.equals( file_nolink)) {

			    			any_relocated = true;
			    		}
			    	}
		    	}
			}
		}

		// we can only open files if they are read-only

		itemOpen.setEnabled(open);

		// can't rename files for non-persistent downloads (e.g. shares) as these
		// are managed "externally"

		itemRenameOrRetarget.setEnabled(all_persistent);
		itemRename.setEnabled(all_persistent);
		itemRetarget.setEnabled(all_persistent);

			// only enable for single files - people prolly don't expect a multi-selection to result
			// in multiple shares, rather they would expect one share with the files they selected
			// which we don't support

		itemPersonalShare.setEnabled(all_complete && all_files.size() == 1);

		itemSkipped.setEnabled(!all_dnd_not_deleted);

		itemHigh.setEnabled(!all_high_pri);

		itemNormal.setEnabled(!all_normal_pri);

		itemLow.setEnabled(!all_low_pri);

		itemDelete.setEnabled(!all_compact);

		itemOpen.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event event) {
				for (int i = 0; i < all_files.size(); i++) {
					DiskManagerFileInfo info = (DiskManagerFileInfo)all_files.get(i);
					if (info != null && info.getAccessMode() == DiskManagerFileInfo.READ) {
						Utils.launch(info);
					}
				}
			}
		});

		Listener rename_listener = new Listener() {
			public void handleEvent(Event event) {
				final boolean rename_it = ((Boolean) event.widget.getData("rename")).booleanValue();
				final boolean retarget_it = ((Boolean) event.widget.getData("retarget")).booleanValue();
				rename(tv, all_files.toArray(new Object[all_files.size()]), rename_it, retarget_it);
			}
		};

		itemRenameOrRetarget.addListener(SWT.Selection, rename_listener);
		itemRename.addListener(SWT.Selection, rename_listener);
		itemRetarget.addListener(SWT.Selection, rename_listener);


		itemLocateFiles.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event event) {
				ManagerUtils.locateFiles( manager_list, files_list, menu.getShell());
			}
		});

		itemLocateFiles.setEnabled(true);

		if (ManagerUtils.canFindMoreLikeThis()) {
			itemfindMore.addListener(SWT.Selection, new Listener() {
				public void handleEvent(Event event) {
					ManagerUtils.findMoreLikeThis( all_files.get(0), menu.getShell());
				}
			});

			itemfindMore.setEnabled(all_files.size() == 1);
		}

		itemRevertFiles.setEnabled(any_relocated);
		itemRevertFiles.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event event) {

				revertFiles(tv, all_files);
			}
		});

		if (itemClearLinks != null) {

			itemClearLinks.setEnabled(files_with_links.size() > 0);

			itemClearLinks.addListener(SWT.Selection, new Listener() {
				public void handleEvent(Event event) {

					for (DiskManagerFileInfo file: files_with_links) {

						file.setLink(null);
					}

					invalidateRows(tv, files_with_links);
				}
			});
		}

		itemPersonalShare.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event event) {
				Map<String,String>	properties = new HashMap<String, String>();

				properties.put(ShareManager.PR_PERSONAL, "true");

				for (int i = 0; i < all_files.size(); i++) {

					DiskManagerFileInfo file_info = all_files.get(i);

					File file = file_info.getFile(true);

					if (file.isFile()) {

						ShareUtils.shareFile(file.getAbsolutePath(), properties);

					} else if (file.isDirectory()) {

						ShareUtils.shareDir(file.getAbsolutePath(), properties);
					}
				}
			}
		});


		Listener priorityListener = new Listener() {
			public void handleEvent(Event event) {
				final Object priority = event.widget.getData("Priority");
				Utils.getOffOfSWTThread(new AERunnable() {
					public void runSupport() {
						changePriority(priority, all_files);
					}
				});
			}
		};

		itemNumeric.addListener(SWT.Selection, priorityListener);
		itemNumericAuto.addListener(SWT.Selection, priorityListener);
		itemHigh.addListener(SWT.Selection, priorityListener);
		itemNormal.addListener(SWT.Selection, priorityListener);
		itemLow.addListener(SWT.Selection, priorityListener);
		itemSkipped.addListener(SWT.Selection, priorityListener);
		itemDelete.addListener(SWT.Selection, priorityListener);

		org.gudy.azureus2.plugins.ui.menus.MenuItem[] menu_items = MenuItemManager.getInstance().getAllAsArray(
				MenuManager.MENU_FILE_CONTEXT);
		if (menu_items.length > 0) {
			// plugins take org.gudy.azureus2.plugins.disk.DiskManagerFileInfo
			org.gudy.azureus2.plugins.disk.DiskManagerFileInfo[] fileInfos = new org.gudy.azureus2.plugins.disk.DiskManagerFileInfo[all_files.size()];
			for (int i = 0; i < all_files.size(); i++) {
				fileInfos[i] = (org.gudy.azureus2.plugins.disk.DiskManagerFileInfo) PluginCoreUtils.convert(
						all_files.get(i), false);
			}
			MenuBuildUtils.addPluginMenuItems(menu_items, menu, false, true,
					new MenuBuildUtils.MenuItemPluginMenuControllerImpl(fileInfos));
		}
	}

	public static void
	rename(
		final TableView 	tv,
		final Object[] 		datasources,
		boolean 			rename_it,
		boolean 			retarget_it) {
		if (datasources.length == 0) {
			return;
		}

		String save_dir = null;
		if (!rename_it && retarget_it) {
			// better count (text based on rename/retarget)
			String s = MessageText.getString("label.num_selected", new String[] {
				Integer.toString(datasources.length)
			});
			save_dir = askForSaveDirectory((DiskManagerFileInfo) datasources[0], s);
			if (save_dir == null) {
				return;
			}
		}

		final List<DownloadManager> pausedDownloads = new ArrayList<DownloadManager>(0);

		final 	AESemaphore task_sem = new AESemaphore("tasksem");

		final List<DiskManagerFileInfo>	affected_files = new ArrayList<DiskManagerFileInfo>();

		try {
			for (int i = 0; i < datasources.length; i++) {
				if (datasources[i] instanceof DownloadManager) {
					AdvRenameWindow window = new AdvRenameWindow();
					window.open((DownloadManager) datasources[i]);
					continue;
				}
				if (!(datasources[i] instanceof DiskManagerFileInfo)) {
					continue;
				}
				final DiskManagerFileInfo fileInfo = (DiskManagerFileInfo) datasources[i];
				File existing_file = fileInfo.getFile(true);
				File f_target = null;
				if (rename_it && retarget_it) {
					String s_target = askForRetargetedFilename(fileInfo);
					if (s_target != null)
						f_target = new File(s_target);
				} else if (rename_it) {
					String s_target = askForRenameFilename(fileInfo);
					if (s_target != null)
						f_target = new File(existing_file.getParentFile(), s_target);
				} else {
					// Parent directory has changed.
					f_target = new File(save_dir, existing_file.getName());
				}

				// So are we doing a rename?
				// If the user has decided against it - abort the op.
				if (f_target == null) {
					return;
				}

				DownloadManager manager = fileInfo.getDownloadManager();
				if (!pausedDownloads.contains(manager)) {
					if (manager.pause()) {
						pausedDownloads.add(manager);
					}
				}

				boolean	dont_delete_existing = false;

				if (f_target.exists()) {

						// Nothing to do.

					if (f_target.equals(existing_file)) {

						continue;

					} else if (retarget_it) {

						// we're doing a re-target so we just need to update the file info to refer to the new existing file

						if (checkRetargetOK( fileInfo, f_target)) {

							dont_delete_existing = true;

						} else {

							continue;
						}
					} else if (existing_file.exists() && !askCanOverwrite(existing_file)) {

						// A rewrite will occur, so we need to ask the user's permission.

						continue;
					}

					// If we reach here, then it means we are doing a real move, but there is
					// no existing file.
				}

				final File ff_target = f_target;

				final boolean f_dont_delete_existing = dont_delete_existing;

				affected_files.add(fileInfo);

				Utils.getOffOfSWTThread(new AERunnable() {
					public void runSupport() {
						moveFile(
							fileInfo.getDownloadManager(),
							fileInfo,
							ff_target,
							f_dont_delete_existing,
							new Runnable() {
								public void
								run() {
									task_sem.release();
								}
							});
					}
				});
			}
		} finally {
			if (affected_files.size() > 0) {

				Utils.getOffOfSWTThread(new AERunnable() {
					public void runSupport() {
						for ( int i=0;i<affected_files.size();i++) {
							task_sem.reserve();
						}

						for (DownloadManager manager : pausedDownloads) {
							manager.resume();
						}

						invalidateRows(tv, affected_files);
					}
				});
			}
		}
	}

	private static void
	invalidateRows(
		TableView					tv,
		List<DiskManagerFileInfo>	files) {
		if (tv == null) {

			return;
		}

		Set<TableRowCore>	done = new HashSet<TableRowCore>();

		for (DiskManagerFileInfo file: files) {

			TableRowCore row =  tv.getRow(file);

			if (row == null) {

				row = tv.getRow( file.getDownloadManager());

				if (row != null) {

					TableRowCore[] subrows = row.getSubRowsWithNull();

					if (subrows != null) {

						for (TableRowCore sr: subrows) {

							if (sr.getDataSource(true) == file) {

								row = sr;

								break;
							}
						}
					}
				}
			}

			if (row != null && !done.contains( row)) {

				done.add(row);

				row.invalidate(true);
			}
		}
	}

	public static void changePriority(Object type, final List<DiskManagerFileInfo> file_list) {

		if (file_list == null || file_list.size() == 0) {
			return;
		}

		if (type == PRIORITY_NUMERIC) {
			changePriorityManual(file_list);
			return;
		} else if (type == PRIORITY_NUMERIC_AUTO) {
			changePriorityAuto(file_list);
			return;
		}

		Map<DownloadManager, ArrayList<DiskManagerFileInfo>> mapDMtoDMFI = new IdentityHashMap<DownloadManager, ArrayList<DiskManagerFileInfo>>();

		for (DiskManagerFileInfo file: file_list) {

			DownloadManager dm = file.getDownloadManager();
			ArrayList<DiskManagerFileInfo> listFileInfos = mapDMtoDMFI.get(dm);
			if (listFileInfos == null) {
				listFileInfos = new ArrayList<DiskManagerFileInfo>(1);
				mapDMtoDMFI.put(dm, listFileInfos);
			}
			listFileInfos.add(file);
		}
		boolean skipped = (type == PRIORITY_SKIPPED || type == PRIORITY_DELETE);
		boolean delete_action = (type == PRIORITY_DELETE);
		for (DownloadManager dm : mapDMtoDMFI.keySet()) {
			ArrayList<DiskManagerFileInfo> list = mapDMtoDMFI.get(dm);
			DiskManagerFileInfo[] fileInfos = list.toArray(new DiskManagerFileInfo[0]);

			if (type == PRIORITY_NORMAL) {

				dm.setFilePriorities(fileInfos, 0);

			} else if (type == PRIORITY_HIGH) {

				dm.setFilePriorities(fileInfos, 1);

			} else if (type == PRIORITY_LOW) {

				dm.setFilePriorities(fileInfos, -1);
			}

			boolean paused = setSkipped(dm, fileInfos, skipped, delete_action);

			if (paused) {

				dm.resume();
			}
		}
	}

	private static void changePriorityManual(final List<DiskManagerFileInfo> file_list) {

		SimpleTextEntryWindow entryWindow = new SimpleTextEntryWindow(
				"FilesView.dialog.priority.title",
				"FilesView.dialog.priority.text");
		entryWindow.prompt(new UIInputReceiverListener() {
			public void UIInputReceiverClosed(UIInputReceiver entryWindow) {
				if (!entryWindow.hasSubmittedInput()) {
					return;
				}
				String sReturn = entryWindow.getSubmittedInput();

				if (sReturn == null)
					return;

				int priority = 0;
				try {
					priority = Integer.valueOf(sReturn).intValue();
				} catch (NumberFormatException er) {

					Debug.out("Invalid priority: " + sReturn);

					new MessageBoxShell(SWT.ICON_ERROR | SWT.OK,
							MessageText.getString("FilePriority.invalid.title"),
							MessageText.getString("FilePriority.invalid.text", new String[]{ sReturn })).open(null);

					return;
				}

				Map<DownloadManager, ArrayList<DiskManagerFileInfo>> mapDMtoDMFI = new IdentityHashMap<DownloadManager, ArrayList<DiskManagerFileInfo>>();

				for (DiskManagerFileInfo file: file_list) {
					DownloadManager dm = file.getDownloadManager();
					ArrayList<DiskManagerFileInfo> listFileInfos = mapDMtoDMFI.get(dm);
					if (listFileInfos == null) {
						listFileInfos = new ArrayList<DiskManagerFileInfo>(1);
						mapDMtoDMFI.put(dm, listFileInfos);
					}
					listFileInfos.add(file);

					file.setPriority(priority);
				}

				for (DownloadManager dm : mapDMtoDMFI.keySet()) {
					ArrayList<DiskManagerFileInfo> list = mapDMtoDMFI.get(dm);
					DiskManagerFileInfo[] fileInfos = list.toArray(new DiskManagerFileInfo[0]);
					boolean paused = setSkipped(dm, fileInfos, false, false);

					if (paused) {

						dm.resume();
					}
				}
			}
		});
	}

	private static void
	changePriorityAuto(
		List<DiskManagerFileInfo> file_list) {
		int priority = 0;

		Map<DownloadManager, ArrayList<DiskManagerFileInfo>> mapDMtoDMFI = new IdentityHashMap<DownloadManager, ArrayList<DiskManagerFileInfo>>();

		for (DiskManagerFileInfo file: file_list) {
			DownloadManager dm = file.getDownloadManager();

			ArrayList<DiskManagerFileInfo> listFileInfos = mapDMtoDMFI.get(dm);
			if (listFileInfos == null) {
				listFileInfos = new ArrayList<DiskManagerFileInfo>(1);
				mapDMtoDMFI.put(dm, listFileInfos);
			}
			listFileInfos.add(file);

			file.setPriority(priority++);
		}

		for ( Map.Entry<DownloadManager,ArrayList<DiskManagerFileInfo>> entry: mapDMtoDMFI.entrySet()) {

			DiskManagerFileInfo[] all_files = entry.getKey().getDiskManagerFileInfoSet().getFiles();

			ArrayList<DiskManagerFileInfo>	files = entry.getValue();

			int	next_priority = 0;

			if (all_files.length != files.size()) {

				Set<Integer>	affected_indexes = new HashSet<Integer>();

				for (DiskManagerFileInfo file: files) {

					affected_indexes.add( file.getIndex());
				}

				for (DiskManagerFileInfo file: all_files) {

					if (!( affected_indexes.contains( file.getIndex()) || file.isSkipped())) {

						next_priority = Math.max( next_priority, file.getPriority()+1);
					}
				}
			}

			next_priority += files.size();

			for (DiskManagerFileInfo file: files) {

				file.setPriority(--next_priority);
			}
		}

		for (DownloadManager dm : mapDMtoDMFI.keySet()) {
			ArrayList<DiskManagerFileInfo> list = mapDMtoDMFI.get(dm);
			DiskManagerFileInfo[] fileInfos = list.toArray(new DiskManagerFileInfo[0]);
			boolean paused = setSkipped(dm, fileInfos, false, false);

			if (paused) {

				dm.resume();
			}
		}
	}

	private static String askForRenameFilename(DiskManagerFileInfo fileInfo) {
		SimpleTextEntryWindow dialog = new SimpleTextEntryWindow(
				"FilesView.rename.filename.title", "FilesView.rename.filename.text");
		String file_name = fileInfo.getFile(true).getName();
		dialog.setPreenteredText(file_name, false); // false -> it's not "suggested", it's a previous value

		int pos = file_name.lastIndexOf('.');

		if (pos > 0) {

			String suffix = fileInfo.getDownloadManager().getDownloadState().getAttribute(DownloadManagerState.AT_INCOMP_FILE_SUFFIX);

			if (suffix != null && file_name.substring(pos).equals( suffix)) {

				pos--;

				while (pos > 0 && file_name.charAt( pos ) != '.') {

					pos--;
				}
			}

			if (pos > 0) {

				dialog.selectPreenteredTextRange(new int[]{ 0, pos });
			}
		}

		dialog.allowEmptyInput(false);
		dialog.prompt();
		if (!dialog.hasSubmittedInput()) {
			return null;
		}
		return dialog.getSubmittedInput();
	}

	private static String askForRetargetedFilename(DiskManagerFileInfo fileInfo) {
		FileDialog fDialog = new FileDialog(Utils.findAnyShell(), SWT.SYSTEM_MODAL); // parg - removed SAVE option as this prevents the selection of existing read-only media when re-targetting	| SWT.SAVE);
		File existing_file = fileInfo.getFile(true);
		fDialog.setFilterPath(existing_file.getParent());
		fDialog.setFileName(existing_file.getName());
		fDialog.setText(MessageText.getString("FilesView.rename.choose.path"));
		return fDialog.open();
	}

	private static String askForSaveDirectory(DiskManagerFileInfo fileInfo, String message) {
		// On Windows, Directory dialog doesn't seem to get mouse scroll focus
		// unless we have a shell.
		Shell anyShell = Utils.findAnyShell();
		Shell shell = new Shell(anyShell.getDisplay(), SWT.NO_TRIM);
		shell.setSize(1, 1);
		shell.open();

		DirectoryDialog dDialog = new DirectoryDialog(shell,
				SWT.SYSTEM_MODAL | SWT.SAVE);
		File current_dir = fileInfo.getFile(true).getParentFile();
		if (!current_dir.isDirectory()) {
			current_dir = fileInfo.getDownloadManager().getSaveLocation();
		}
		dDialog.setFilterPath(current_dir.getPath());
		dDialog.setText(MessageText.getString("FilesView.rename.choose.path.dir"));
		if (message != null) {
			dDialog.setMessage(message);
		}
		String open = dDialog.open();
		shell.close();
		return open;
	}

	private static boolean askCanOverwrite(File file) {
		MessageBoxShell mb = new MessageBoxShell(SWT.OK | SWT.CANCEL,
				MessageText.getString("FilesView.rename.confirm.delete.title"),
				MessageText.getString("FilesView.rename.confirm.delete.text",
						new String[] {
							file.toString()
						}));
		mb.setDefaultButtonUsingStyle(SWT.OK);
		mb.setRememberOnlyIfButton(0);
		mb.setRemember("FilesView.messagebox.rename.id", true, null);
		mb.setLeftImage(SWT.ICON_WARNING);
		mb.open(null);
		return mb.waitUntilClosed() == SWT.OK;
	}

	private static boolean checkRetargetOK(DiskManagerFileInfo info, File target) {

		if (!target.exists()) {

			return (true);
		}

		if (info.getTorrentFile().getLength() == target.length()) {

			return (true);
		}

		MessageBoxShell mb = new MessageBoxShell(SWT.OK | SWT.CANCEL,
				MessageText.getString("FilesView.retarget.confirm.title"),
				MessageText.getString("FilesView.retarget.confirm.text"));

		mb.setDefaultButtonUsingStyle(SWT.OK);

		mb.setLeftImage(SWT.ICON_WARNING);

		mb.open(null);

		return mb.waitUntilClosed() == SWT.OK;
	}

	// same code is used in tableitems.files.NameItem
	private static void
	moveFile(
		final DownloadManager 			manager,
		final DiskManagerFileInfo 		fileInfo,
		final File 						target,
		boolean							dont_delete_existing,
		final Runnable					done) {

		// this behaviour should be put further down in the core but I'd rather not
		// do so close to release :(

		manager.setUserData("is_changing_links", true);

		if (dont_delete_existing) {
				// I don't link this one bit, but there's a lot of things I don't like and this isn't the worst
			manager.setUserData("set_link_dont_delete_existing", true);
		}

		try {

			FileUtil.runAsTask(new AzureusCoreOperationTask() {
				public void run(AzureusCoreOperation operation) {
					boolean went_async = false;

					try {
						// xxx download must be stoppped
						boolean ok = fileInfo.setLink(target);

						if (!ok) {

							new MessageBoxShell(
								SWT.ICON_ERROR | SWT.OK,
								MessageText.getString("FilesView.rename.failed.title"),
								MessageText.getString("FilesView.rename.failed.text")).open(
									new UserPrompterResultListener() {

										public void prompterClosed(int result) {
											if (done != null) {
												done.run();
											}
										}
									});

							went_async = true;
						}
					} finally {
						manager.setUserData("is_changing_links", false);
						manager.setUserData("set_link_dont_delete_existing", null);

						if (!went_async) {

							if (done != null) {
								done.run();
							}
						}
					}
				}
			});
		} catch (Throwable e) {
			manager.setUserData("is_changing_links", false);
			manager.setUserData("set_link_dont_delete_existing", null);

			if (done != null) {
				done.run();
			}
		}
	}

	// Returns true if it was paused here.
	private static boolean setSkipped(DownloadManager manager,
			DiskManagerFileInfo[] infos, boolean skipped, boolean delete_action) {
		// if we're not managing the download then don't do anything other than
		// change the file's priority

		if (!manager.isPersistent()) {
			for (int i = 0; i < infos.length; i++) {
				infos[i].setSkipped(skipped);
			}
			return false;
		}
		int[] existing_storage_types = manager.getStorageType(infos);
		int nbFiles = manager.getDiskManagerFileInfoSet().nbFiles();
		boolean[] setLinear = new boolean[nbFiles];
		boolean[] setCompact = new boolean[nbFiles];
		boolean[] setReorder = new boolean[nbFiles];
		boolean[] setReorderCompact = new boolean[nbFiles];
		int compactCount = 0;
		int linearCount = 0;
		int reorderCount = 0;
		int reorderCompactCount = 0;

		if (infos.length > 1) {

		}
		// This should hopefully reduce the number of "exists" checks.
		File save_location = manager.getAbsoluteSaveLocation();
		boolean root_exists = save_location.isDirectory()
				|| (infos.length <= 1 && save_location.exists());

		boolean type_has_been_changed = false;
		boolean requires_pausing = false;

		for (int i = 0; i < infos.length; i++) {
			int existing_storage_type = existing_storage_types[i];
			int compact_target;
			int non_compact_target;
			if (existing_storage_type == DiskManagerFileInfo.ST_COMPACT || existing_storage_type == DiskManagerFileInfo.ST_LINEAR) {
				compact_target 		= DiskManagerFileInfo.ST_COMPACT;
				non_compact_target	= DiskManagerFileInfo.ST_LINEAR;
			} else {
				compact_target 		= DiskManagerFileInfo.ST_REORDER_COMPACT;
				non_compact_target	= DiskManagerFileInfo.ST_REORDER;
			}
			int new_storage_type;
			if (skipped) {

				// Check to see if the file exists, but try to avoid doing an
				// actual disk check if possible.
				File existing_file = infos[i].getFile(true);

				// Avoid performing existing_file.exists if we know that it is meant
				// to reside in the default save location and that location does not
				// exist.
				boolean perform_check;
				if (root_exists) {
					perform_check = true;
				} else if (FileUtil.isAncestorOf(save_location, existing_file)) {
					perform_check = false;
				} else {
					perform_check = true;
				}

				if (perform_check && existing_file.exists()) {
					if (delete_action) {
						MessageBoxShell mb = new MessageBoxShell(SWT.OK | SWT.CANCEL,
								MessageText.getString("FilesView.rename.confirm.delete.title"),
								MessageText.getString("FilesView.rename.confirm.delete.text",
										new String[] {
											existing_file.toString()
										}));
						mb.setDefaultButtonUsingStyle(SWT.OK);
						mb.setRememberOnlyIfButton(0);
						mb.setRemember("FilesView.messagebox.delete.id", false, null);
						mb.setLeftImage(SWT.ICON_WARNING);
						mb.open(null);

						boolean wants_to_delete = mb.waitUntilClosed() == SWT.OK;

						if (wants_to_delete) {

							new_storage_type = compact_target;

						} else {

							new_storage_type = non_compact_target;
						}
					} else {

							// compact only currently supports first+last piece and therefore is not
							// good for handling partial DND files (as other partial pieces will be discarded....)

						new_storage_type = non_compact_target;
					}
				}
				// File does not exist.
				else {
					new_storage_type = compact_target;
				}
			} else {
				new_storage_type = non_compact_target;
			}

			boolean has_changed = existing_storage_type != new_storage_type;

			type_has_been_changed |= has_changed;

			if (has_changed) {

				requires_pausing |= (new_storage_type == DiskManagerFileInfo.ST_COMPACT || new_storage_type == DiskManagerFileInfo.ST_REORDER_COMPACT);

				if (new_storage_type == DiskManagerFileInfo.ST_COMPACT) {
					setCompact[infos[i].getIndex()] = true;
					compactCount++;
				} else if (new_storage_type == DiskManagerFileInfo.ST_LINEAR) {
					setLinear[infos[i].getIndex()] = true;
					linearCount++;
				} else if (new_storage_type == DiskManagerFileInfo.ST_REORDER) {
					setReorder[infos[i].getIndex()] = true;
					reorderCount++;
				} else if (new_storage_type == DiskManagerFileInfo.ST_REORDER_COMPACT) {
					setReorderCompact[infos[i].getIndex()] = true;
					reorderCompactCount++;
				}
			}
		}

		boolean ok = true;
		boolean paused = false;
		if (type_has_been_changed) {
			if (requires_pausing)
				paused = manager.pause();
			if (linearCount > 0)
				ok &= Arrays.equals(
						setLinear,
						manager.getDiskManagerFileInfoSet().setStorageTypes(setLinear,
								DiskManagerFileInfo.ST_LINEAR));
			if (compactCount > 0)
				ok &= Arrays.equals(
						setCompact,
						manager.getDiskManagerFileInfoSet().setStorageTypes(setCompact,
								DiskManagerFileInfo.ST_COMPACT));
			if (reorderCount > 0)
				ok &= Arrays.equals(
						setReorder,
						manager.getDiskManagerFileInfoSet().setStorageTypes(setReorder,
								DiskManagerFileInfo.ST_REORDER));
			if (reorderCompactCount > 0)
				ok &= Arrays.equals(
						setReorderCompact,
						manager.getDiskManagerFileInfoSet().setStorageTypes(setReorderCompact,
								DiskManagerFileInfo.ST_REORDER_COMPACT));
		}

		if (ok) {
			for (int i = 0; i < infos.length; i++) {
				infos[i].setSkipped(skipped);
			}
		}

		return paused;
	}

	public static void
	revertFiles(
		final TableView<?>		tv,
		DownloadManager[]		dms) {
		List<DiskManagerFileInfo>	files = new ArrayList<DiskManagerFileInfo>();

		for (DownloadManager dm: dms) {

			DiskManagerFileInfo[] dm_files = dm.getDiskManagerFileInfoSet().getFiles();;

			LinkFileMap links = dm.getDownloadState().getFileLinks();

			Iterator<LinkFileMap.Entry>	it = links.entryIterator();

			while (it.hasNext()) {

				LinkFileMap.Entry entry = it.next();

				if (entry.getToFile() != null) {

					files.add( dm_files[ entry.getIndex()]);
				}
			}
		}

		if (files.size() > 0) {

			revertFiles(tv, files);
		}
	}

	public static void
	revertFiles(
		final TableView<?>					tv,
		List<DiskManagerFileInfo>			files) {
		final List<DownloadManager>	paused = new ArrayList<DownloadManager>();

		final 	AESemaphore task_sem = new AESemaphore("tasksem");

		final List<DiskManagerFileInfo>	affected_files = new ArrayList<DiskManagerFileInfo>();

		try {
			for (int i = 0; i < files.size(); i++) {

				final DiskManagerFileInfo file_info = files.get(i);

				if (file_info == null) {
					continue;
				}


				final File file_nolink 	= file_info.getFile(false);

				final DownloadManager manager = file_info.getDownloadManager();

				File target = file_info.getDownloadManager().getDownloadState().getFileLink(file_info.getIndex(), file_nolink);

			   	if (target != null) {

			    	if (target != file_nolink) {

			    		if (!target.equals( file_nolink)) {

							if (!paused.contains( manager)) {

								if (manager.pause()) {

									paused.add(manager);
								}
							}

							affected_files.add(file_info);

							Utils.getOffOfSWTThread(new AERunnable() {
								public void runSupport() {
									moveFile(
										manager,
										file_info,
										file_nolink,
										true,
										new Runnable() {
											public void
											run() {
												task_sem.release();													}
										});
								}
							});
			    		}
			    	}
		    	}
			}
		} finally {
			if (affected_files.size() > 0) {

				Utils.getOffOfSWTThread(new AERunnable() {
					public void runSupport() {
						for ( int i=0;i<affected_files.size();i++) {
							task_sem.reserve();
						}
						for (DownloadManager manager : paused) {
							manager.resume();
						}

						invalidateRows(tv, affected_files);
					}
				});
			}
		}
	}
}
