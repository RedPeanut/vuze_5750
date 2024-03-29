/**
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

package com.aelitis.azureus.ui.swt.shells.opentorrent;

import java.io.*;
import java.net.URL;

import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.*;
import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.config.StringIterator;
import org.gudy.azureus2.core3.config.StringList;
import org.gudy.azureus2.core3.config.impl.ConfigurationDefaults;
import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.torrent.impl.TorrentOpenOptions;
import org.gudy.azureus2.core3.torrentdownloader.TorrentDownloader;
import org.gudy.azureus2.core3.torrentdownloader.TorrentDownloaderCallBackInterface;
import org.gudy.azureus2.core3.util.*;
import org.gudy.azureus2.plugins.ui.UIManager;
import org.gudy.azureus2.plugins.ui.UIManagerEvent;
import org.gudy.azureus2.plugins.utils.StaticUtilities;
import org.gudy.azureus2.plugins.utils.subscriptions.SubscriptionManager;
import org.gudy.azureus2.pluginsimpl.local.PluginInitializer;
import org.gudy.azureus2.pluginsimpl.local.utils.xml.rss.RSSUtils;
import org.gudy.azureus2.ui.swt.*;
import org.gudy.azureus2.ui.swt.mainwindow.Colors;
import org.gudy.azureus2.ui.swt.mainwindow.TorrentOpener;

import com.aelitis.azureus.core.AzureusCore;
import com.aelitis.azureus.core.AzureusCoreFactory;
import com.aelitis.azureus.ui.UIFunctions;
import com.aelitis.azureus.ui.UIFunctionsManager;
import com.aelitis.azureus.ui.common.updater.UIUpdatable;
import com.aelitis.azureus.ui.swt.shells.main.UIFunctionsImpl;
import com.aelitis.azureus.ui.swt.skin.*;
import com.aelitis.azureus.ui.swt.uiupdater.UIUpdaterSWT;
import com.aelitis.azureus.ui.swt.views.skin.SkinnedDialog;
import com.aelitis.azureus.ui.swt.views.skin.SkinnedDialog.SkinnedDialogClosedListener;
import com.aelitis.azureus.ui.swt.views.skin.StandardButtonsArea;

public class OpenTorrentWindow
	implements TorrentDownloaderCallBackInterface, UIUpdatable
{

	protected static String CONFIG_REFERRER_DEFAULT = "openUrl.referrer.default";
	private Shell shellForChildren;
	private Shell parent;
	private SkinnedDialog dlg;
	private StandardButtonsArea buttonsArea;
	private Button btnPasteOpen;
	private SWTSkinObjectTextbox soTextArea;
	private SWTSkinObject soReferArea;
	private Combo referrerCombo;
	private String lastReferrer;
	private StringList referrers;
	//private TorrentOpenOptions torrentOptions;
	private SWTSkinObjectCheckbox soShowAdvanced;

	public OpenTorrentWindow(Shell parent) {
		this.parent = parent;

		Utils.execSWTThread(new AERunnable() {
			public void runSupport() {
				swt_createWindow();
			}
		});
	}

	private void swt_createWindow() {
		dlg = new SkinnedDialog("skin3_dlg_opentorrent", "shell", SWT.RESIZE
				| SWT.DIALOG_TRIM);

		shellForChildren = dlg.getShell();
		SWTSkin skin = dlg.getSkin();
		SWTSkinObject soTopBar = skin.getSkinObject("add-buttons");
		if (soTopBar instanceof SWTSkinObjectContainer) {
			swt_addButtons(((SWTSkinObjectContainer) soTopBar).getComposite());
		}

		soTextArea = (SWTSkinObjectTextbox) skin.getSkinObject("text-area");
		Text tb = ((Text) soTextArea.getControl());
		tb.setFocus();
		tb.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				int userMode = COConfigurationManager.getIntParameter("User Mode");
				if (userMode > 0) {
					if (soReferArea != null) {
						String text = ((Text) e.widget).getText();
						boolean hasURL = UrlUtils.parseTextForURL(text, false, true) != null;
						soReferArea.setVisible(hasURL);
					}
				}
			}
		});

		SWTSkinObject so;

		so = skin.getSkinObject("show-advanced");
		if (so instanceof SWTSkinObjectCheckbox) {
			soShowAdvanced = (SWTSkinObjectCheckbox) so;
			soShowAdvanced.setChecked(COConfigurationManager.getBooleanParameter(ConfigurationDefaults.CFG_TORRENTADD_OPENOPTIONS));
		}

		soReferArea = skin.getSkinObject("refer-area");

		lastReferrer = COConfigurationManager.getStringParameter(
				CONFIG_REFERRER_DEFAULT, "");

		so = skin.getSkinObject("refer-combo");
		if (so instanceof SWTSkinObjectContainer) {
			referrerCombo = new Combo(((SWTSkinObjectContainer) so).getComposite(),
					SWT.BORDER);
			referrerCombo.setLayoutData(Utils.getFilledFormData());
			referrers = COConfigurationManager.getStringListParameter("url_open_referrers");
			StringIterator iter = referrers.iterator();
			while (iter.hasNext()) {
				referrerCombo.add(iter.next());
			}

			if (lastReferrer != null) {
				referrerCombo.setText(lastReferrer);
			}

		}

		SWTSkinObject soButtonArea = skin.getSkinObject("button-area");
		if (soButtonArea instanceof SWTSkinObjectContainer) {
			buttonsArea = new StandardButtonsArea() {
				protected void clicked(int intValue) {
					String referrer = null;
					if (referrerCombo != null) {
						referrer = referrerCombo.getText().trim();
					}

					if (dlg != null) {
						dlg.close();
					}
					if (intValue == SWT.OK && soTextArea != null
							&& soTextArea.getText().length() > 0) {
						openTorrent(soTextArea.getText(), referrer);
					}
				}
			};
			buttonsArea.setButtonIDs(new String[] {
				MessageText.getString("Button.ok"),
				MessageText.getString("Button.cancel")
			});
			buttonsArea.setButtonVals(new Integer[] {
				SWT.OK,
				SWT.CANCEL
			});
			buttonsArea.swt_createButtons(((SWTSkinObjectContainer) soButtonArea).getComposite());
		}

		UIUpdaterSWT.getInstance().addUpdater(this);

		/*
		 * The bring-to-front logic for torrent addition is controlled by other parts of the code so we don't
		 * want the dlg to override this behaviour (main example here is torrents passed from, say, a browser,
		 * and the user has disabled the 'show vuze on external torrent add' feature)
		 */

		dlg.open("otw", false);

		dlg.addCloseListener(new SkinnedDialogClosedListener() {
			public void skinDialogClosed(SkinnedDialog dialog) {
				dispose();
			}
		});
	}

	protected void openTorrent(String text, String newReferrer) {
		if (newReferrer != null && newReferrer.length() > 0) {

			if (!referrers.contains(newReferrer)) {
				referrers.add(newReferrer);
				COConfigurationManager.setParameter("url_open_referrers", referrers);
				COConfigurationManager.save();
			}

			COConfigurationManager.setParameter(CONFIG_REFERRER_DEFAULT, newReferrer);
			COConfigurationManager.save();
		}
		final String[] splitters = {
			"\r\n",
			"\n",
			"\r",
			"\t"
		};

		String lines[] = null;

		for (int i = 0; i < splitters.length; i++) {
			if (text.contains(splitters[i])) {
				lines = text.split(splitters[i]);
				break;
			}
		}

		if (lines == null) {
			lines = new String[] {
				text
			};
		}

		TorrentOpener.openTorrentsFromStrings(new TorrentOpenOptions(), parent, null, lines, newReferrer,
				this, false);
	}

	protected void dispose() {
		UIUpdaterSWT.getInstance().removeUpdater(this);
	}

	private void swt_addButtons(Composite parent) {
		Composite cButtons = new Composite(parent, SWT.NONE);
		RowLayout rLayout = new RowLayout(SWT.HORIZONTAL);
		rLayout.marginBottom = 0;
		rLayout.marginLeft = 0;
		rLayout.marginRight = 0;
		rLayout.marginTop = 0;
		cButtons.setLayout(rLayout);
		cButtons.setLayoutData(Utils.getFilledFormData());

		// Buttons for tableTorrents
		Button browseTorrent = new Button(cButtons, SWT.PUSH);
		Messages.setLanguageText(browseTorrent, "OpenTorrentWindow.addFiles");
		browseTorrent.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event event) {
				FileDialog fDialog = new FileDialog(shellForChildren, SWT.OPEN
						| SWT.MULTI);
				fDialog.setFilterExtensions(new String[] {
					"*.torrent",
					"*.tor",
					Constants.FILE_WILDCARD
				});
				fDialog.setFilterNames(new String[] {
					"*.torrent",
					"*.tor",
					Constants.FILE_WILDCARD
				});
				fDialog.setFilterPath(TorrentOpener.getFilterPathTorrent());
				fDialog.setText(MessageText.getString("MainWindow.dialog.choose.file"));
				String fileName = TorrentOpener.setFilterPathTorrent(fDialog.open());
				if (fileName != null) {
					addTorrentsToWindow(fDialog.getFilterPath(), fDialog.getFileNames());
				}
			}
		});

		Button browseFolder = new Button(cButtons, SWT.PUSH);
		Messages.setLanguageText(browseFolder, "OpenTorrentWindow.addFiles.Folder");
		browseFolder.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event event) {
				DirectoryDialog fDialog = new DirectoryDialog(shellForChildren,
						SWT.NULL);
				fDialog.setFilterPath(TorrentOpener.getFilterPathTorrent());
				fDialog.setMessage(MessageText.getString("MainWindow.dialog.choose.folder"));
				String path = TorrentOpener.setFilterPathTorrent(fDialog.open());
				if (path != null) {
					addTorrentsToWindow(path, null);
				}
			}
		});

		btnPasteOpen = new Button(cButtons, SWT.PUSH);
		Messages.setLanguageText(btnPasteOpen,
				"OpenTorrentWindow.addFiles.Clipboard");
		btnPasteOpen.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event event) {
				Clipboard clipboard = new Clipboard(shellForChildren.getDisplay());

				String sClipText = (String) clipboard.getContents(TextTransfer.getInstance());
				if (sClipText != null) {
					addTorrentsFromTextList(sClipText.trim(), false);
				}
			}
		});

		btnPasteOpen.setVisible(false);
	}

	private String ensureTrailingSeparator(String sPath) {
		if (sPath == null || sPath.length() == 0 || sPath.endsWith(File.separator))
			return sPath;
		return sPath + File.separator;
	}

	private int addTorrentsToWindow(String sTorrentFilePath,
			String[] sTorrentFilenames) {
		String text = soTextArea.getText();

		sTorrentFilePath = ensureTrailingSeparator(sTorrentFilePath);

		// Process Directory
		if (sTorrentFilePath != null && sTorrentFilenames == null) {
			File dir = new File(sTorrentFilePath);
			if (!dir.isDirectory())
				return 0;

			final File[] files = dir.listFiles(new FileFilter() {
				public boolean accept(File arg0) {
					if (FileUtil.getCanonicalFileName(arg0.getName()).endsWith(".torrent"))
						return true;
					if (FileUtil.getCanonicalFileName(arg0.getName()).endsWith(".tor"))
						return true;
					return false;
				}
			});

			if (files.length == 0)
				return 0;

			sTorrentFilenames = new String[files.length];
			for (int i = 0; i < files.length; i++)
				sTorrentFilenames[i] = files[i].getName();
		}

		int numAdded = 0;

		if (sTorrentFilenames != null) {
			for (int i = 0; i < sTorrentFilenames.length; i++) {
				if (sTorrentFilenames[i] == null || sTorrentFilenames[i].length() == 0)
					continue;

				// Process File
				String sFileName = ((sTorrentFilePath == null) ? "" : sTorrentFilePath)
						+ sTorrentFilenames[i];

				File file = new File(sFileName);

				try {
					if (UrlUtils.isURL(sFileName)
							|| (file.exists() && TorrentUtils.isTorrentFile(sFileName))) {
						if (text.length() > 0) {
							text += "\n";
						}
						text += sFileName;
						numAdded++;
					}
				} catch (FileNotFoundException e) {
				} catch (IOException e) {
				}
			}

			if (numAdded > 0) {
				soTextArea.setText(text);
			}
		}

		return numAdded;
	}

	/**
	 * Add Torrent(s) to Window using a text list of files/urls/torrents
	 *
	 * @param sClipText Text to parse
	 * @param bVerifyOnly Only check if there's potential torrents in the text,
	 *                     do not try to add the torrents.
	 *
	 * @return Number of torrents added or found.  When bVerifyOnly, this number
	 *          may not be exact.
	 */
	private int addTorrentsFromTextList(String sClipText, boolean bVerifyOnly) {
		String[] lines = null;
		int iNumFound = 0;
		// # of consecutive non torrent lines
		int iNoTorrentLines = 0;
		// no use checking the whole clipboard (which may be megabytes)
		final int MAX_CONSECUTIVE_NONTORRENT_LINES = 100;

		final String[] splitters = {
			"\r\n",
			"\n",
			"\r",
			"\t"
		};

		for (int i = 0; i < splitters.length; i++)
			if (sClipText.contains(splitters[i])) {
				lines = sClipText.split(splitters[i]);
				break;
			}

		if (lines == null)
			lines = new String[] {
				sClipText
			};

		// Check if URL, 20 byte hash, Dir, or file
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i].trim();
			if (line.startsWith("\"") && line.endsWith("\"")) {
				if (line.length() < 3) {
					line = "";
				} else {
					line = line.substring(1, line.length() - 2);
				}
			}

			boolean ok;

			if (line.length()==0) {
				ok = false;
			} else if (UrlUtils.isURL(line)) {
				ok = true;
			} else {
				File file = new File(line);

				if (!file.exists()) {
					ok = false;
				} else if (file.isDirectory()) {
					if (bVerifyOnly) {
						// XXX Could do a file count here, but the number found is not
						//     expected to be an exact number anyway, since we aren't
						//     event verifying if they are torrents.
						ok = true;
					} else {
						iNumFound += addTorrentsToWindow(lines[i], null);
						ok = false;
					}
				} else {
					ok = true;
				}
			}

			if (!ok) {
				iNoTorrentLines++;
				lines[i] = null;
				if (iNoTorrentLines > MAX_CONSECUTIVE_NONTORRENT_LINES)
					break;
			} else {
				iNumFound++;
				iNoTorrentLines = 0;
			}
		}

		if (bVerifyOnly) {
			return iNumFound;
		}

		return addTorrentsToWindow(null, lines);
	}

	public static void main(String[] args) {
		AzureusCore core = AzureusCoreFactory.create();
		core.start();

		UIConfigDefaultsSWT.initialize();

		//		try {
		//			SWTThread.createInstance(null);
		//		} catch (SWTThreadAlreadyInstanciatedException e) {
		//			e.printStackTrace();
		//		}
		Display display = Display.getDefault();

		Colors.getInstance();

		COConfigurationManager.setParameter("User Mode", 2);

		UIFunctionsImpl uiFunctions = new UIFunctionsImpl(null);
		UIFunctionsManager.setUIFunctions(uiFunctions);

		//		invoke(null, core.getGlobalManager());
		OpenTorrentWindow window = new OpenTorrentWindow(null);
		while (!window.isDisposed()) {
			if (!display.readAndDispatch())
				display.sleep();
		}

		core.stop();
	}

	private boolean isDisposed() {
		if (dlg == null) {
			return false;
		}
		return dlg.isDisposed();
	}

	public void TorrentDownloaderEvent(int state, TorrentDownloader inf) {

		// This method is run even if the window is closed.

		// The default is to delete file on cancel
		// We set this flag to false if we detected the file was not a torrent
		if (!inf.getDeleteFileOnCancel() &&
				(	state == TorrentDownloader.STATE_CANCELLED ||
					state == TorrentDownloader.STATE_ERROR ||
					state == TorrentDownloader.STATE_DUPLICATE ||
					state == TorrentDownloader.STATE_FINISHED)) {

			File file = inf.getFile();

			// we already know it isn't a torrent.. we are just using the call
			// to popup the message

			boolean	done = false;

			if (RSSUtils.isRSSFeed( file)) {

				try {
					URL url = new URL(inf.getURL());

					UIManager ui_manager = StaticUtilities.getUIManager(10*1000);

					if (ui_manager != null) {

						String details = MessageText.getString(
								"subscription.request.add.message",
								new String[]{ inf.getURL() });

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
			}

			if (!done) {
				TorrentUtil.isFileTorrent(file, inf.getURL(), true);
			}

			if (file.exists()) {
				file.delete();
			}

			return;
		}

		if (state == TorrentDownloader.STATE_INIT) {
		} else if (state == TorrentDownloader.STATE_FINISHED) {
			File file = inf.getFile();
			TorrentOpenOptions torrentOptions = new TorrentOpenOptions();
			if (!TorrentOpener.mergeFileIntoTorrentInfo(file.getAbsolutePath(),
					inf.getURL(), torrentOptions)) {
				if (file.exists())
					file.delete();
			} else {
				UIFunctions uif = UIFunctionsManager.getUIFunctions();
				boolean b = uif.addTorrentWithOptions(false, torrentOptions);
				if (!b && file.exists()) {
					file.delete();
				}
			}
		} else if (state == TorrentDownloader.STATE_CANCELLED
				|| state == TorrentDownloader.STATE_ERROR
				|| state == TorrentDownloader.STATE_DUPLICATE) {

		} else if (state == TorrentDownloader.STATE_DOWNLOADING) {
			int count = inf.getLastReadCount();
			int numRead = inf.getTotalRead();

				// some weird logic here that seems to want to bail early on a download if it doesn't look like it is a torrent (bnencode always starts with 'd'
				// and using 'delete file on cancel' as some crazy marker to control this...

				// PARG - added '<' to prevent early abandoning of RSS feed content

			if (!inf.getDeleteFileOnCancel() && numRead >= 16384) {
				inf.cancel();
			} else if (numRead == count && count > 0) {
				final byte[] bytes = inf.getLastReadBytes();
				if (bytes[0] != 'd' && bytes[0] != '<') {
					inf.setDeleteFileOnCancel(false);
				}
			}
		} else {
			return;
		}
	}

	public void updateUI() {
		boolean bTorrentInClipboard = false;

		Clipboard clipboard = new Clipboard(Display.getDefault());

		String sClipText = (String) clipboard.getContents(TextTransfer.getInstance());
		if (sClipText != null)
			bTorrentInClipboard = addTorrentsFromTextList(sClipText, true) > 0;

		if (btnPasteOpen != null && !btnPasteOpen.isDisposed()
				&& btnPasteOpen.isVisible() != bTorrentInClipboard) {
			btnPasteOpen.setVisible(bTorrentInClipboard);
			if (bTorrentInClipboard) {
				btnPasteOpen.setToolTipText(sClipText);
			}
		}

		clipboard.dispose();
	}

	public String getUpdateUIName() {
		return null;
	}
}
