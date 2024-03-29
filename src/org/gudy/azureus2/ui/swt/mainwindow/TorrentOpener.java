/*
 * Created on 3 mai 2004
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
package org.gudy.azureus2.ui.swt.mainwindow;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;
import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.disk.DiskManagerFileInfo;
import org.gudy.azureus2.core3.disk.DiskManagerFileInfoSet;
import org.gudy.azureus2.core3.download.DownloadManager;
import org.gudy.azureus2.core3.download.DownloadManagerInitialisationAdapter;
import org.gudy.azureus2.core3.download.DownloadManagerState;
import org.gudy.azureus2.core3.global.GlobalManager;
import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.logging.LogAlert;
import org.gudy.azureus2.core3.logging.Logger;
import org.gudy.azureus2.core3.torrent.TOTorrent;
import org.gudy.azureus2.core3.torrent.TOTorrentException;
import org.gudy.azureus2.core3.torrent.impl.TorrentOpenFileOptions;
import org.gudy.azureus2.core3.torrent.impl.TorrentOpenOptions;
import org.gudy.azureus2.core3.torrentdownloader.TorrentDownloaderCallBackInterface;
import org.gudy.azureus2.core3.util.AERunnable;
import org.gudy.azureus2.core3.util.AEThread2;
import org.gudy.azureus2.core3.util.Constants;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.DisplayFormatters;
import org.gudy.azureus2.core3.util.RandomUtils;
import org.gudy.azureus2.core3.util.TorrentUtils;
import org.gudy.azureus2.core3.util.UrlUtils;
import org.gudy.azureus2.plugins.ui.UIManager;
import org.gudy.azureus2.plugins.ui.UIManagerEvent;
import org.gudy.azureus2.plugins.utils.StaticUtilities;
import org.gudy.azureus2.plugins.utils.subscriptions.SubscriptionManager;
import org.gudy.azureus2.pluginsimpl.local.PluginInitializer;
import org.gudy.azureus2.pluginsimpl.local.utils.xml.rss.RSSUtils;
import org.gudy.azureus2.ui.swt.FileDownloadWindow;
import org.gudy.azureus2.ui.swt.TorrentUtil;
import org.gudy.azureus2.ui.swt.URLTransfer;
import org.gudy.azureus2.ui.swt.Utils;
import org.gudy.azureus2.ui.swt.shells.CoreWaiterSWT;

import com.aelitis.azureus.core.AzureusCore;
import com.aelitis.azureus.core.AzureusCoreFactory;
import com.aelitis.azureus.core.AzureusCoreRunningListener;
import com.aelitis.azureus.core.tag.Tag;
import com.aelitis.azureus.core.vuzefile.VuzeFile;
import com.aelitis.azureus.core.vuzefile.VuzeFileComponent;
import com.aelitis.azureus.core.vuzefile.VuzeFileHandler;
import com.aelitis.azureus.ui.UIFunctions;
import com.aelitis.azureus.ui.UIFunctionsManager;
import com.aelitis.azureus.ui.swt.UIFunctionsManagerSWT;
import com.aelitis.azureus.ui.swt.UIFunctionsSWT;

import hello.util.Log;

/**
 * Bunch of Torrent Opening functions.
 *
 * @author Olivier Chalouhi
 * @author TuxPaper (openTorrentWindow)
 *
 * @todo move public, UI stuff to to {@link UIFunctionsSWT}
 */
public class TorrentOpener {

	private static String TAG = TorrentOpener.class.getSimpleName();

	/**
	 * Open a torrent.  Possibly display a window if the user config says so
	 *
	 * @param torrentFile Torrent to open (file, url, etc)
	 * @note PLUGINS USE THIS FUNCTION!
	 */
	public static void openTorrent(final String torrentFile) {
		openTorrent( torrentFile, new HashMap<String, Object>());
	}

	public static void openTorrent(final String torrentFile, final Map<String,Object> options) {
		AzureusCoreFactory.addCoreRunningListener(new AzureusCoreRunningListener() {
			public void azureusCoreRunning(AzureusCore core) {
    		UIFunctionsSWT uif = UIFunctionsManagerSWT.getUIFunctionsSWT();
    		if (uif != null) {
    			uif.openTorrentOpenOptions(null, null, new String[] { torrentFile }, options);
    		}
			}
		});
	}

  protected static void
  openTorrentsForTracking(
    final String path,
    final String fileNames[] )
  {
  	CoreWaiterSWT.waitForCoreRunning(new AzureusCoreRunningListener() {
			public void azureusCoreRunning(final AzureusCore core) {
				final Display display = SWTThread.getInstance().getDisplay();
		  	if (display == null || display.isDisposed() || core == null)
		  		return;

				new AEThread2("TorrentOpener") {
					public void run() {

						for (int i = 0; i < fileNames.length; i++) {

							try {
								TOTorrent t = TorrentUtils.readFromFile(new File(path,
										fileNames[i]), true);

								core.getTrackerHost().hostTorrent(t, true, true);

							} catch (Throwable e) {
								Logger.log(new LogAlert(LogAlert.UNREPEATABLE,
										"Torrent open fails for '" + path + File.separator
												+ fileNames[i] + "'", e));
							}
						}
					}
				}.start();
			}
		});
  }

  public static void
  openTorrentTrackingOnly()
  {
		Utils.execSWTThread(new AERunnable() {
			public void runSupport() {
				final Shell shell = Utils.findAnyShell();
		  	if (shell == null)
		  		return;

				FileDialog fDialog = new FileDialog(shell, SWT.OPEN | SWT.MULTI);
				fDialog.setFilterPath(getFilterPathTorrent());
				fDialog
						.setFilterExtensions(new String[] { "*.torrent", "*.tor", Constants.FILE_WILDCARD });
				fDialog.setFilterNames(new String[] { "*.torrent", "*.tor", Constants.FILE_WILDCARD });
				fDialog.setText(MessageText.getString("MainWindow.dialog.choose.file"));
				String path = setFilterPathTorrent(fDialog.open());
				if (path == null)
					return;

				TorrentOpener.openTorrentsForTracking(path, fDialog.getFileNames());
			}
		});
  }

  public static void openTorrentSimple() {
		Utils.execSWTThread(new AERunnable() {
			public void runSupport() {
				final Shell shell = Utils.findAnyShell();
				if (shell == null)
					return;

				FileDialog fDialog = new FileDialog(shell, SWT.OPEN | SWT.MULTI);
				fDialog.setFilterPath(getFilterPathTorrent());
				fDialog.setFilterExtensions(new String[] {
						"*.torrent",
						"*.tor",
						Constants.FILE_WILDCARD });
				fDialog.setFilterNames(new String[] {
						"*.torrent",
						"*.tor",
						Constants.FILE_WILDCARD });
				fDialog.setText(MessageText.getString("MainWindow.dialog.choose.file"));
				String path = setFilterPathTorrent(fDialog.open());
				if (path == null)
					return;

				UIFunctionsManagerSWT.getUIFunctionsSWT().openTorrentOpenOptions(shell,
						path, fDialog.getFileNames(), false, false);
			}
		});
	}

  public static void openDroppedTorrents(DropTargetEvent event, boolean deprecated_sharing_param) {
	  	Object data = event.data;

	  	if (data == null) {
			return;
	  	}

		// prevent attempt to handle drop of URLs that refer to content as torrents
		// I'd prefer to disable the drop altogether but can't find a way to get the
		// drop data before the drop actually occurs

		if (data instanceof String) {
			if (((String)data).contains("azcdid=" + RandomUtils.INSTANCE_ID)) {
				event.detail 	= DND.DROP_NONE;
				return;
			}
		} else if (data instanceof URLTransfer.URLType) {

			String link = ((URLTransfer.URLType)data).linkURL;

			if (link != null && link.contains("azcdid=" + RandomUtils.INSTANCE_ID)) {
				event.detail 	= DND.DROP_NONE;
				return;
			}
		}

		if (event.data instanceof String[] || event.data instanceof String) {
			final String[] sourceNames = (event.data instanceof String[])
					? (String[]) event.data : new String[] { (String) event.data };

			if (event.detail == DND.DROP_NONE)
				return;

			for (int i = 0; (i < sourceNames.length); i++) {
				final File source = new File(sourceNames[i]);
				String sURL = UrlUtils.parseTextForURL(sourceNames[i], true);

				if (sURL != null && !source.exists()) {
					UIFunctionsSWT uif = UIFunctionsManagerSWT.getUIFunctionsSWT();
					if (uif != null) {
						uif.openTorrentOpenOptions(null, null, new String[] { sURL },
								false, false);
					}
				} else if (source.isFile()) {

						// go async as vuze file handling can require UI access which then blocks
						// if this is happening during init

					new AEThread2("asyncOpen", true) {
						public void run() {
							String filename = source.getAbsolutePath();

							VuzeFileHandler vfh = VuzeFileHandler.getSingleton();

							if (vfh.loadAndHandleVuzeFile(filename, VuzeFileComponent.COMP_TYPE_NONE) != null) {

								return;
							}


							UIFunctionsSWT uif = UIFunctionsManagerSWT.getUIFunctionsSWT();
							if (uif != null) {
								uif.openTorrentOpenOptions(null, null, new String[] { filename },
										false, false);
							}

						}
					}.start();

				} else if (source.isDirectory()) {

					String dir_name = source.getAbsolutePath();

					UIFunctionsSWT uif = UIFunctionsManagerSWT.getUIFunctionsSWT();
					if (uif != null) {
						uif.openTorrentOpenOptions(null, dir_name, null,
								false, false);
					}
				}
			}
		} else if (event.data instanceof URLTransfer.URLType) {
			UIFunctionsSWT uif = UIFunctionsManagerSWT.getUIFunctionsSWT();
			if (uif != null) {
				uif.openTorrentOpenOptions(null, null, new String[] {
					((URLTransfer.URLType) event.data).linkURL
				}, false, false);
			}
		}
	}


  public static String getFilterPathData() {
    String before = COConfigurationManager.getStringParameter("previous.filter.dir.data");
    if (before != null && before.length() > 0) {
      return before;
    }
    String def;
		try {
			def = COConfigurationManager.getDirectoryParameter("Default save path");
	    return def;
		} catch (IOException e) {
			return "";
		}
  }

  public static String getFilterPathTorrent() {
    String before = COConfigurationManager.getStringParameter("previous.filter.dir.torrent");
    if (before != null && before.length() > 0) {
      return before;
    }
    return COConfigurationManager.getStringParameter("General_sDefaultTorrent_Directory");
  }

  public static String setFilterPathData(String path) {
    if (path != null && path.length() > 0) {
      File test = new File(path);
      if (!test.isDirectory()) test = test.getParentFile();
      String now = "";
      if (test != null) now = test.getAbsolutePath();
      String before = COConfigurationManager.getStringParameter("previous.filter.dir.data");
      if (before == null || before.length() == 0 || !before.equals( now )) {
        COConfigurationManager.setParameter("previous.filter.dir.data", now);
        COConfigurationManager.save();
      }
    }
    return path;
  }

  public static String setFilterPathTorrent(String path) {
    if (path != null && path.length() > 0) {
      File test = new File(path);
      if (!test.isDirectory()) test = test.getParentFile();
      String now = "";
      if (test != null) now = test.getAbsolutePath();
      String before = COConfigurationManager.getStringParameter("previous.filter.dir.torrent");
      if (before == null || before.length() == 0 || !before.equals( now )) {
        COConfigurationManager.setParameter("previous.filter.dir.torrent", now);
        COConfigurationManager.save();
      }
      return now;
    }
    return path;
  }


	public static boolean doesDropHaveTorrents(DropTargetEvent event) {
		boolean isTorrent = false;
		if (event.data == null && event.currentDataType != null) {
			FileTransfer ft = FileTransfer.getInstance();
			if (ft.isSupportedType(event.currentDataType)) {
				Object data = ft.nativeToJava(event.currentDataType);
				String[] fileList = (data instanceof String) ? new String[] {
					(String) data
				} : (String[]) data;
				if (fileList != null) {
					for (String file : fileList) {
						if (file.endsWith(".torrent") || file.endsWith(".vuze")) {
							isTorrent = true;
							break;
						}
					}
				}
			} else {
				Object object = URLTransfer.getInstance().nativeToJava(
						event.currentDataType);
				if (object instanceof URLTransfer.URLType) {

					URLTransfer.URLType xfer = (URLTransfer.URLType)object;

					String link = xfer.linkURL;

					if (link == null || !link.contains("azcdid=" + RandomUtils.INSTANCE_ID)) {

						isTorrent = true;
					}
				}
			}
		} else if (event.data instanceof String && ((String)event.data).contains("azcdid=" + RandomUtils.INSTANCE_ID)) {

			// not a torrent

		} else if (event.data instanceof String[] || event.data instanceof String) {
			final String[] sourceNames = (event.data instanceof String[])
					? (String[]) event.data : new String[] {
						(String) event.data
					};
			for (String name : sourceNames) {
				String sURL = UrlUtils.parseTextForURL(name, true);
				if (sURL != null) {
					isTorrent = true;
					break;
				}
			}
		} else if (event.data instanceof URLTransfer.URLType) {

			URLTransfer.URLType xfer = (URLTransfer.URLType)event.data;

			String link = xfer.linkURL;

			if (link == null || !link.contains("azcdid=" + RandomUtils.INSTANCE_ID)) {

				isTorrent = true;
			}
		}
		return isTorrent;
	}

	/**
	 * @param torrentOptions
	 * @return
	 * @since 5.0.0.1
	 *
	 * @TODO: Remove SWT UI parts (use UIFunctions) and move out of SWT tree
	 */
	public static final boolean addTorrent(final TorrentOpenOptions torrentOptions) {

		try {
			if (torrentOptions.getTorrent() == null) {
				return false;
			}

			final DownloadManagerInitialisationAdapter dmia = new DownloadManagerInitialisationAdapter() {

				public int getActions() {
					return (ACT_ASSIGNS_TAGS);
				}

				public void initialised(
					DownloadManager dm,
					boolean for_seeding) {

					DiskManagerFileInfoSet fileInfoSet = dm.getDiskManagerFileInfoSet();
					DiskManagerFileInfo[] fileInfos = fileInfoSet.getFiles();

					boolean reorder_mode = COConfigurationManager.getBooleanParameter("Enable reorder storage mode");
					int reorder_mode_min_mb = COConfigurationManager.getIntParameter("Reorder storage mode min MB");

					try {
						dm.getDownloadState().suppressStateSave(true);

						boolean[] toSkip = new boolean[fileInfos.length];
						boolean[] toCompact = new boolean[fileInfos.length];
						boolean[] toReorderCompact = new boolean[fileInfos.length];

						int[] priorities = null;

						int comp_num = 0;
						int reorderCompNum = 0;

						final TorrentOpenFileOptions[] files = torrentOptions.getFiles();

						for (int iIndex = 0; iIndex < fileInfos.length; iIndex++) {
							DiskManagerFileInfo fileInfo = fileInfos[iIndex];
							if (iIndex >= 0 && iIndex < files.length
									&& files[iIndex].lSize == fileInfo.getLength()) {
								// Always pull destination file from fileInfo and not from
								// TorrentFileInfo because the destination may have changed
								// by magic code elsewhere
								File fDest = fileInfo.getFile(true);
								if (files[iIndex].isLinked()) {

									fDest = files[iIndex].getDestFileFullName();

									// Can't use fileInfo.setLink(fDest) as it renames
									// the existing file if there is one

									dm.getDownloadState().setFileLink(iIndex,
											fileInfo.getFile(false), fDest);
								}

								if (files[iIndex].isToDownload()) {

									int	priority = files[iIndex].getPriority();
									if (priority != 0) {
										if (priorities == null) {
											priorities = new int[fileInfos.length];
										}
										priorities[iIndex] = priority;
									}
								} else {
									toSkip[iIndex] = true;

									if (!fDest.exists()) {

										if (reorder_mode
												&& (fileInfo.getLength() / (1024 * 1024)) >= reorder_mode_min_mb) {

											toReorderCompact[iIndex] = true;
											reorderCompNum++;
										} else {
											toCompact[iIndex] = true;
											comp_num++;
										}
									}
								}
							}
						}

						// rename display name based on whether the user has manually renamed either the only file in
						// a single file torrent or the top-level-folder in a multi-file torrent

						if (files.length == 1) {
							TorrentOpenFileOptions file = files[0];
							if (file.isManualRename()) {
								String fileRename = file.getDestFileName();
								if (fileRename != null && fileRename.length() > 0) {
									dm.getDownloadState().setDisplayName(fileRename);
								}
							}
						} else {
							String folderRename = torrentOptions.getManualRename();
							if (folderRename != null &&
									folderRename.length() > 0) {
								dm.getDownloadState().setDisplayName(folderRename);
							}
						}

						if (comp_num > 0) {
							fileInfoSet.setStorageTypes(toCompact, DiskManagerFileInfo.ST_COMPACT);
						}

						if (reorderCompNum > 0) {
							fileInfoSet.setStorageTypes(toReorderCompact, DiskManagerFileInfo.ST_REORDER_COMPACT);
						}

						fileInfoSet.setSkipped(toSkip, true);

						if (priorities != null) {
							fileInfoSet.setPriority(priorities);
						}

						int	maxUp = torrentOptions.getMaxUploadSpeed();
						int kInB = DisplayFormatters.getKinB();

						if (maxUp > 0) {
							dm.getStats().setUploadRateLimitBytesPerSecond(maxUp*kInB);
						}

						int	maxDown = torrentOptions.getMaxDownloadSpeed();
						if (maxDown > 0) {
							dm.getStats().setDownloadRateLimitBytesPerSecond(maxDown*kInB);
						}

						DownloadManagerState dmState = dm.getDownloadState();

						if (torrentOptions.disableIPFilter) {
							dmState.setFlag(DownloadManagerState.FLAG_DISABLE_IP_FILTER, true);
						}

						if (torrentOptions.peerSource != null) {
							for (String peerSource : torrentOptions.peerSource.keySet()) {
								boolean enable = torrentOptions.peerSource.get(peerSource);
								dmState.setPeerSourceEnabled(peerSource, enable);
							}
						}

						Map<String,Boolean> enabledNetworks = torrentOptions.getEnabledNetworks();

						if (enabledNetworks != null) {

							if (!dmState.getFlag( DownloadManagerState.FLAG_INITIAL_NETWORKS_SET)) {

								for (String net : enabledNetworks.keySet()) {
									boolean enable = enabledNetworks.get(net);
									dmState.setNetworkEnabled(net, enable);
								}
							}
						}

						List<Tag> initialTags = torrentOptions.getInitialTags();

						for (Tag t: initialTags) {
							t.addTaggable(dm);
						}

						List<List<String>> trackers = torrentOptions.getTrackers(true);

						if (trackers != null) {

							TOTorrent torrent = dm.getTorrent();
							TorrentUtils.listToAnnounceGroups(trackers, torrent);

							try {
								TorrentUtils.writeToFile(torrent);
							} catch (Throwable e) {
								Debug.printStackTrace(e);
							}
						}
					} finally {
						dm.getDownloadState().suppressStateSave(false);
					}
				}
			};

			AzureusCoreFactory.addCoreRunningListener(new AzureusCoreRunningListener() {

				//private String TAG = AzureusCoreRunningListener.class.getSimpleName();

				public void azureusCoreRunning(AzureusCore core) {

					Log.d(TAG, "azureusCoreRunning() is called...");

					TOTorrent torrent = torrentOptions.getTorrent();
					byte[] hash = null;
					try {
						hash = torrent.getHash();
					} catch (TOTorrentException e) {
					}

					int startMode = torrentOptions.getStartMode();
					if (startMode == TorrentOpenOptions.STARTMODE_QUEUED) Log.d(TAG, "TorrentOpenOptions.STARTMODE_QUEUED");
					if (startMode == TorrentOpenOptions.STARTMODE_STOPPED) Log.d(TAG, "TorrentOpenOptions.STARTMODE_STOPPED");
					if (startMode == TorrentOpenOptions.STARTMODE_FORCESTARTED) Log.d(TAG, "TorrentOpenOptions.STARTMODE_FORCESTARTED");
					if (startMode == TorrentOpenOptions.STARTMODE_SEEDING) Log.d(TAG, "TorrentOpenOptions.STARTMODE_SEEDING");

					/*public final static int STARTMODE_QUEUED = 0;
					public final static int STARTMODE_STOPPED = 1;
					public final static int STARTMODE_FORCESTARTED = 2;
					public final static int STARTMODE_SEEDING = 3;*/

					int iStartState = (torrentOptions.getStartMode() == TorrentOpenOptions.STARTMODE_STOPPED)
							? DownloadManager.STATE_STOPPED : DownloadManager.STATE_QUEUED;

					GlobalManager gm = core.getGlobalManager();

					DownloadManager dm = gm.addDownloadManager(torrentOptions.sFileName,
							hash, torrentOptions.getParentDir(), torrentOptions.getSubDir(),
							iStartState, true,
							torrentOptions.getStartMode() == TorrentOpenOptions.STARTMODE_SEEDING, dmia);

					// If dm is null, most likely there was an error printed.. let's hope
					// the user was notified and skip the error quietly.
					// We don't have to worry about deleting the file (info.bDelete..)
					// since gm.addDown.. will handle it.
					if (dm == null) {
						return;
					}

					if (torrentOptions.iQueueLocation == TorrentOpenOptions.QUEUELOCATION_TOP) {
						gm.moveTop(new DownloadManager[] { dm });
					}

					if (torrentOptions.getStartMode() == TorrentOpenOptions.STARTMODE_FORCESTARTED) {
						dm.setForceStart(true);
					}

				}
			});

		} catch (Exception e) {
			UIFunctions uif = UIFunctionsManager.getUIFunctions();
			if (uif != null) {
				uif.showErrorMessage("OpenTorrentWindow.mb.openError",
						Debug.getStackTrace(e), new String[] {
							torrentOptions.sOriginatingLocation,
							e.getMessage()
						});
			}
			return false;
		}
		return true;
	}

	/**
	 * Creates a TorrentInfo from a file.  Prompts user if the file is invalid,
	 * torrent already exists
	 *
	 * @param sFileName
	 * @param sOriginatingLocation
	 * @return
	 * @since 5.0.0.1
	 */
	// TODO: i18n
	public static boolean mergeFileIntoTorrentInfo(String sFileName,
			final String sOriginatingLocation, TorrentOpenOptions torrentOptions) {
		TOTorrent torrent = null;
		File torrentFile;
		boolean bDeleteFileOnCancel = false;

		// Make a copy if user wants that.  We'll delete it when we cancel, if we
		// actually made a copy.
		try {
			if (sFileName.startsWith("file://localhost/")) {
				sFileName = UrlUtils.decode(sFileName.substring(16));
			}

			final File fOriginal = new File(sFileName);

			if (!fOriginal.isFile() || !fOriginal.exists()) {
				UIFunctionsManager.getUIFunctions().showErrorMessage(
						"OpenTorrentWindow.mb.openError", fOriginal.toString(),
						new String[] {
							UrlUtils.decode(sOriginatingLocation),
							"Not a File"
						});
				return false;
			}

			if (fOriginal.length() > TorrentUtils.MAX_TORRENT_FILE_SIZE) {
				UIFunctionsManager.getUIFunctions().showErrorMessage(
						"OpenTorrentWindow.mb.openError", fOriginal.toString(),
						new String[] {
							UrlUtils.decode(sOriginatingLocation),
							"Too large to be a torrent"
						});
				return false;
			}

			torrentFile = TorrentUtils.copyTorrentFileToSaveDir(fOriginal, true);
			bDeleteFileOnCancel = !fOriginal.equals(torrentFile);
			// TODO if the files are still equal, and it isn't in the save
			//       dir, we should copy it to a temp file in case something
			//       re-writes it.  No need to copy a torrent coming from the
			//       downloader though..
		} catch (IOException e) {
			// Use torrent in wherever it is and hope for the best
			// XXX Should error instead?
			Debug.out(e);
			torrentFile = new File(sFileName);
		}

		VuzeFileHandler vfh = VuzeFileHandler.getSingleton();
		VuzeFile vf = vfh.loadVuzeFile(torrentFile);
		System.out.println("vf = " + vf);
		if (vf != null) {
			vfh.handleFiles(new VuzeFile[] { vf }, VuzeFileComponent.COMP_TYPE_NONE);
			return false;
		}

		if (RSSUtils.isRSSFeed( torrentFile)) {

			boolean	done = false;

			try {
				URL url = new URL(sOriginatingLocation);

				UIManager ui_manager = StaticUtilities.getUIManager(10*1000);

				if (ui_manager != null) {

					String details = MessageText.getString(
							"subscription.request.add.message",
							new String[]{ sOriginatingLocation });

					long res = ui_manager.showMessageBox(
							"subscription.request.add.title",
							"!" + details + "!",
							UIManagerEvent.MT_YES | UIManagerEvent.MT_NO);

					if (res == UIManagerEvent.MT_YES) {

						SubscriptionManager sm = PluginInitializer.getDefaultInterface().getUtilities().getSubscriptionManager();

						sm.requestSubscription(url);

						done = true;
					}
				}
			} catch (Throwable e) {

				Debug.out(e);
			}


			if (done) {
				if (bDeleteFileOnCancel) {
					torrentFile.delete();
				}
				return false;
			}
		}
		// Do a quick check to see if it's a torrent
		if (!TorrentUtil.isFileTorrent(torrentFile, torrentFile.getName(), !torrentOptions.getHideErrors())) {
			if (bDeleteFileOnCancel) {
				torrentFile.delete();
			}
			return false;
		}

		// Load up the torrent, see it it's real
		try {
			torrent = TorrentUtils.readFromFile(torrentFile, false);
		} catch (final TOTorrentException e) {

			UIFunctionsManager.getUIFunctions().showErrorMessage(
					"OpenTorrentWindow.mb.openError",  Debug.getStackTrace(e),
					new String[] {
						sOriginatingLocation,
						e.getMessage()
					});

			if (bDeleteFileOnCancel)
				torrentFile.delete();

			return false;
		}

		torrentOptions.bDeleteFileOnCancel = bDeleteFileOnCancel;
		torrentOptions.sFileName = torrentFile.getAbsolutePath();
		torrentOptions.setTorrent(torrent);
		torrentOptions.sOriginatingLocation = sOriginatingLocation;

		return torrentOptions.getTorrent() != null;
	}


	/**
	 * Adds torrents that are listed in torrents array.  torrent array can
	 * can contain urls or file names.  File names get pathPrefix appended.
	 * <P>
	 * will open url download dialog, or warning dialogs
	 *
	 * @since 5.0.0.1
	 */
	public static void openTorrentsFromStrings(TorrentOpenOptions optionsToClone,
			Shell parent, String pathPrefix, String[] torrents, String referrer,
			TorrentDownloaderCallBackInterface listener,
			boolean forceTorrentOptionsWindow) {

		// if no torrents, but pathPrefix is directory, collect all torrents in it
		if (torrents == null || torrents.length == 0) {
			if (pathPrefix == null) {
				return;
			}
			File path = new File(pathPrefix);
			if (!path.isDirectory()) {
				return;
			}

			List<String> newTorrents = new ArrayList<String>();
			File[] listFiles = path.listFiles();
			for (File file : listFiles) {
				try {
					if (file.isFile() && TorrentUtils.isTorrentFile(file.getAbsolutePath())) {
						newTorrents.add(file.getName());
					}
				} catch (FileNotFoundException e) {
				} catch (IOException e) {
				}
			}

			if (newTorrents.size() == 0) {
				return;
			}

			torrents = newTorrents.toArray(new String[0]);
		}

		// trim out any .vuze files
		final VuzeFileHandler vfh = VuzeFileHandler.getSingleton();
		List<VuzeFile> vuzeFiles = new ArrayList<VuzeFile>();

		for (String line : torrents) {
			line = line.trim();
			if (line.startsWith("\"") && line.endsWith("\"")) {
				if (line.length() < 3) {
					line = "";
				} else {
					line = line.substring(1, line.length() - 2);
				}
			}

			TorrentOpenOptions torrentOptions = optionsToClone == null
					? new TorrentOpenOptions() : new TorrentOpenOptions(optionsToClone);

			File file = pathPrefix == null ? new File(line) : new File(pathPrefix,
					line);
			if (file.exists()) {

				try {
					VuzeFile vf = vfh.loadVuzeFile(file);
					//System.out.println("vf = " + vf);
					if (vf != null) {
						vuzeFiles.add(vf);
						continue;
					}
				} catch (Throwable e) {
					Debug.printStackTrace(e);
				}

				UIFunctions uif = UIFunctionsManager.getUIFunctions();

				if (TorrentOpener.mergeFileIntoTorrentInfo(file.getAbsolutePath(),
						null, torrentOptions)) {
					uif.addTorrentWithOptions(forceTorrentOptionsWindow, torrentOptions);
				}
				continue;
			}

			final String url = UrlUtils.parseTextForURL(line, true);
			if (url != null) {

				// we used to load any URL, but that results in double loading..
				if (url.endsWith(".vuze")) {
					new AEThread2("VuzeLoader") {
						public void run() {
							try {
								VuzeFile vf = vfh.loadVuzeFile(url); // XXX This takes a while..
								if (vf != null) {
									vfh.handleFiles(new VuzeFile[] {
										vf
									}, VuzeFileComponent.COMP_TYPE_NONE);
								}
							} catch (Throwable e) {
								Debug.printStackTrace(e);
							}
						}
					}.start();

					continue;
				}

				new FileDownloadWindow(parent, url, referrer, null, torrentOptions,
						listener);
			}
		}

		if (vuzeFiles.size() > 0) {
			VuzeFile[] vfs = new VuzeFile[vuzeFiles.size()];
			vuzeFiles.toArray(vfs);
			vfh.handleFiles(vfs, VuzeFileComponent.COMP_TYPE_NONE);
		}

	}
}
