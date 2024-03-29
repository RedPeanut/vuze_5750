/*
 * File		: ProgressPanel.java
 * Created : 7 oct. 2003 13:01:42
 * By			: Olivier
 *
 * Azureus - a Java Bittorrent client
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

package org.gudy.azureus2.ui.swt.maketorrent;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.download.DownloadManager;
import org.gudy.azureus2.core3.download.DownloadManagerInitialisationAdapter;
import org.gudy.azureus2.core3.internat.LocaleTorrentUtil;
import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.logging.LogAlert;
import org.gudy.azureus2.core3.logging.Logger;
import org.gudy.azureus2.core3.peer.PEPeerManager;
import org.gudy.azureus2.core3.torrent.*;
import org.gudy.azureus2.core3.tracker.host.TRHostException;
import org.gudy.azureus2.core3.util.*;
import org.gudy.azureus2.ui.swt.Messages;
import org.gudy.azureus2.ui.swt.shells.CoreWaiterSWT;
import org.gudy.azureus2.ui.swt.shells.CoreWaiterSWT.TriggerInThread;
import org.gudy.azureus2.ui.swt.views.utils.ManagerUtils;
import org.gudy.azureus2.ui.swt.wizard.AbstractWizardPanel;
import org.gudy.azureus2.ui.swt.wizard.IWizardPanel;

import com.aelitis.azureus.core.AzureusCore;
import com.aelitis.azureus.core.AzureusCoreRunningListener;
import com.aelitis.azureus.core.tag.Tag;
import com.aelitis.azureus.core.tag.TagManager;
import com.aelitis.azureus.core.tag.TagManagerFactory;
import com.aelitis.azureus.core.tag.TagType;

/**
 * @author Olivier
 *
 */
public class ProgressPanel extends AbstractWizardPanel<NewTorrentWizard> implements TOTorrentProgressListener {

	Text tasks;
	ProgressBar progress;
	Display display;
	Button showTorrentFile;

	public ProgressPanel(NewTorrentWizard wizard, IWizardPanel<NewTorrentWizard> _previousPanel) {
		super(wizard, _previousPanel);
	}
	
	/* (non-Javadoc)
	 * @see org.gudy.azureus2.ui.swt.maketorrent.IWizardPanel#show()
	 */
	public void show() {
		display = wizard.getDisplay();
		wizard.setTitle(MessageText.getString("wizard.progresstitle"));
		wizard.setCurrentInfo("");
		wizard.setPreviousEnabled(false);
		Composite rootPanel = wizard.getPanel();
		GridLayout layout = new GridLayout();
		layout.numColumns = 1;
		rootPanel.setLayout(layout);

		Composite panel = new Composite(rootPanel, SWT.NULL);
		GridData gridData = new GridData(GridData.VERTICAL_ALIGN_CENTER | GridData.FILL_HORIZONTAL);
		panel.setLayoutData(gridData);
		layout = new GridLayout();
		layout.numColumns = 2;
		panel.setLayout(layout);

		tasks = new Text(panel, SWT.BORDER | SWT.MULTI | SWT.READ_ONLY);
		tasks.setBackground(display.getSystemColor(SWT.COLOR_WHITE));
		gridData = new GridData(GridData.FILL_BOTH);
		gridData.heightHint = 120;
		gridData.horizontalSpan = 2;
		tasks.setLayoutData(gridData);

		progress = new ProgressBar(panel, SWT.NULL);
		progress.setMinimum(0);
		progress.setMaximum(0);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.horizontalSpan = 2;
		progress.setLayoutData(gridData);

		Label label = new Label( panel, SWT.NULL);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		label.setLayoutData(gridData);
		Composite Browsepanel = new Composite(panel, SWT.NULL);
		layout = new GridLayout();
		layout.numColumns = 2;
		Browsepanel.setLayout(layout);

		label = new Label(Browsepanel, SWT.NULL);
		Messages.setLanguageText(label, "wizard.newtorrent.showtorrent");
	
		showTorrentFile = new Button(Browsepanel, SWT.PUSH);
	
	 	Messages.setLanguageText(showTorrentFile, "MyTorrentsView.menu.explore");
	
	 	showTorrentFile.addSelectionListener(
	 		new SelectionAdapter() {
				public void widgetSelected(SelectionEvent e) {
					ManagerUtils.open(new File(wizard.savePath));
				}
	 		});
	
	 	showTorrentFile.setEnabled(false);
	}

	/* (non-Javadoc)
	 * @see org.gudy.azureus2.ui.swt.maketorrent.IWizardPanel#finish()
	 */
	public void finish() {
		Thread t = new AEThread("Torrent Maker") {
			public void runSupport() {
				makeTorrent();
			}
		};
		t.setPriority(Thread.MIN_PRIORITY);
		t.setDaemon(true);
		t.start();
	}

	public void makeTorrent() {
		int	tracker_type = wizard.getTrackerType();
		if (tracker_type == NewTorrentWizard.TT_EXTERNAL) {
			TrackersUtil.getInstance().addTracker(wizard.trackerURL);
		}
		File f;
		if (wizard.create_mode == NewTorrentWizard.MODE_DIRECTORY) {
			f = new File(wizard.directoryPath);
		} else if (wizard.create_mode == NewTorrentWizard.MODE_SINGLE_FILE) {
			f = new File(wizard.singlePath);
		} else {
			f = wizard.byo_desc_file;
		}
		
		try {
			URL url = new URL(wizard.trackerURL);
			final TOTorrent torrent;
			if (wizard.getPieceSizeComputed()) {
				wizard.creator =
					TOTorrentFactory.createFromFileOrDirWithComputedPieceLength(
								f, url, wizard.getAddOtherHashes());
				wizard.creator.addListener(this);
				wizard.creator.setFileIsLayoutDescriptor(wizard.create_mode == NewTorrentWizard.MODE_BYO);
				torrent = wizard.creator.create();
			} else {
				wizard.creator =
						TOTorrentFactory.createFromFileOrDirWithFixedPieceLength(
									f, url, wizard.getAddOtherHashes(), wizard.getPieceSizeManual());
				wizard.creator.addListener(this);
					wizard.creator.setFileIsLayoutDescriptor(wizard.create_mode == NewTorrentWizard.MODE_BYO);
					torrent = wizard.creator.create();
			}
			if (tracker_type == NewTorrentWizard.TT_DECENTRAL) {
				TorrentUtils.setDecentralised(torrent);
			}
			torrent.setComment(wizard.getComment());
			TorrentUtils.setDHTBackupEnabled(torrent, wizard.permitDHT);
			TorrentUtils.setPrivate( torrent, wizard.getPrivateTorrent());
			LocaleTorrentUtil.setDefaultTorrentEncoding(torrent);
			// mark this newly created torrent as complete to avoid rechecking on open
			final File saveDir;
			if (wizard.create_mode == NewTorrentWizard.MODE_DIRECTORY) {
				saveDir = f;
			} else if (wizard.create_mode == NewTorrentWizard.MODE_SINGLE_FILE) {
				saveDir = f.getParentFile();
			} else {
				String savePath = COConfigurationManager.getStringParameter("Default save path");
				File f_savePath = new File(savePath);
				if (!f_savePath.canWrite()) {
					throw (new Exception("Default save path is not configured: See Tools->Options->File"));
				}
				saveDir = f_savePath;
			}
			if (wizard.useMultiTracker) {
				this.reportCurrentTask(MessageText.getString("wizard.addingmt"));
				TorrentUtils.listToAnnounceGroups(wizard.trackers, torrent);
			}
			if (wizard.useWebSeed && wizard.webseeds.size() > 0) {
					this.reportCurrentTask(MessageText.getString("wizard.webseed.adding"));
					Map	ws = wizard.webseeds;
					List	getright = (List)ws.get("getright");
					if (getright.size() > 0) {
						for (int i=0;i<getright.size();i++) {
							reportCurrentTask("		GetRight: " + getright.get(i));
						}
						torrent.setAdditionalListProperty("url-list", new ArrayList( getright));
					}
					List	webseed = (List)ws.get("webseed");
					if (webseed.size() > 0) {
						for (int i=0;i<webseed.size();i++) {
							reportCurrentTask("		WebSeed: " + webseed.get(i));
						}
						torrent.setAdditionalListProperty("httpseeds", new ArrayList( webseed));
					}
			}
		// must do this last as it saves a copy of the torrent state for future opening...
		/*
		 * actually, don't need to do this as the "open-for-seeding" option used when adding the download
		 * does the job. Reason I stopped doing this is
		 * https://sourceforge.net/tracker/index.php?func=detail&aid=1721917&group_id=84122&atid=575154
		 *
		DownloadManagerState download_manager_state =
			DownloadManagerStateFactory.getDownloadState(torrent);
		TorrentUtils.setResumeDataCompletelyValid(download_manager_state);
		download_manager_state.save();
		 */
		this.reportCurrentTask(MessageText.getString("wizard.savingfile"));
		final File torrentFile = new File(wizard.savePath);
		torrent.serialiseToBEncodedFile(torrentFile);
		this.reportCurrentTask(MessageText.getString("wizard.filesaved"));
		wizard.switchToClose(new Runnable() {
			public void run() {
				showTorrentFile.setEnabled(true);
			}
		});
		
		if (wizard.autoOpen) {
			CoreWaiterSWT.waitForCore(TriggerInThread.NEW_THREAD,
				new AzureusCoreRunningListener() {
					public void azureusCoreRunning(AzureusCore core) {
						boolean startStopped = COConfigurationManager.getBooleanParameter("Default Start Torrents Stopped");
						byte[] hash = null;
						try {
							hash = torrent.getHash();
						} catch (TOTorrentException e) {
						}
						
						if (wizard.forceStart || wizard.superseed) {
							// superseeding can only be set for an active download...
							startStopped = false;
						}
						
						DownloadManagerInitialisationAdapter dmia;
						final String	initialTags = wizard.getInitialTags(true);
						if (initialTags.length() > 0) {
							dmia =
								new DownloadManagerInitialisationAdapter() {
								
									public int getActions() {
										return (ACT_ASSIGNS_TAGS);
									}
									
									public void initialised(
										DownloadManager 	dm,
										boolean 			forSeeding) {
										TagManager tm = TagManagerFactory.getTagManager();
										TagType tag_type = tm.getTagType(TagType.TT_DOWNLOAD_MANUAL);
										String[]	bits = initialTags.replace(';', ',' ).split(",");
										for (String tag: bits) {
											tag = tag.trim();
											if (tag.length() > 0) {
												try {
													Tag t = tag_type.getTag(tag,	true);
													if (t == null) {
														t = tag_type.createTag(tag, true);
													}
													t.addTaggable(dm);
												} catch (Throwable e) {
													Debug.out(e);
												}
											}
										}
									}
								};
						} else {
							dmia = null;
						}

						final DownloadManager dm = core.getGlobalManager().addDownloadManager(
								torrentFile.toString(),
								hash,
								saveDir.toString(),
								startStopped ? DownloadManager.STATE_STOPPED
										: DownloadManager.STATE_QUEUED, true, // persistent
								true, // for seeding
								dmia);
						if (!startStopped && dm != null) {
							// We want this to move to seeding ASAP, so move it to the top
							// of the download list, where it will do the quick check and
							// move to the seeding list
							// (the for seeding flag should really be smarter and verify
							//	it's a seeding torrent and set appropriately)
							dm.getGlobalManager().moveTop(new DownloadManager[] { dm });
						}
						
						if (wizard.autoHost
								&& wizard.getTrackerType() != NewTorrentWizard.TT_EXTERNAL) {
							try {
								core.getTrackerHost().hostTorrent(torrent, true, false);
							} catch (TRHostException e) {
								Logger.log(new LogAlert(LogAlert.REPEATABLE,
										"Host operation fails", e));
							}
						}
						if (dm != null) {
							if (wizard.forceStart) {
								dm.setForceStart(true);
							}
							if (wizard.superseed) {
								new AEThread2("startwait") {
									public void run() {
										long	start = SystemTime.getMonotonousTime();
										while (true) {
											if (dm.isDestroyed()) {
												break;
											}
											long elapsed = SystemTime.getMonotonousTime() - start;
											if (elapsed > 60*1000) {
												int state = dm.getState();
												if (	state == DownloadManager.STATE_ERROR ||
														state ==DownloadManager.STATE_STOPPED) {
													break;
												}
											}
											if (elapsed > 5*60*1000) {
												break;
											}
											PEPeerManager pm = dm.getPeerManager();
											if (pm != null) {
												pm.setSuperSeedMode(true);
												break;
											}
											try {
												Thread.sleep(1000);
											} catch (Throwable e) {
												break;
											}
										}
									}
								}.start();
							}
						}
					}
				});
			}
		}
		catch (Exception e) {
			if (e instanceof TOTorrentException) {
				TOTorrentException	te = (TOTorrentException)e;
				if (te.getReason() == TOTorrentException.RT_CANCELLED) {
						//expected failure, don't log exception
				} else {
					reportCurrentTask(MessageText.getString("wizard.operationfailed"));
						reportCurrentTask(TorrentUtils.exceptionToText( te));
				}
			} else {
				Debug.printStackTrace(e);
				reportCurrentTask(MessageText.getString("wizard.operationfailed"));
				reportCurrentTask(Debug.getStackTrace(e));
			}
			wizard.switchToClose();
		}
	}

	/* (non-Javadoc)
	 * @see org.gudy.azureus2.core3.torrent.TOTorrentProgressListener#reportCurrentTask(java.lang.String)
	 */
	public void reportCurrentTask(final String task_description) {
		if (display != null && !display.isDisposed()) {
			display.asyncExec(new AERunnable() {
				public void runSupport() {
					if (tasks != null && !tasks.isDisposed()) {
						tasks.append(task_description + Text.DELIMITER);
					}
				}
			});
		}
	}

	/* (non-Javadoc)
	 * @see org.gudy.azureus2.core3.torrent.TOTorrentProgressListener#reportProgress(int)
	 */
	public void reportProgress(final int percent_complete) {
		if (display != null && !display.isDisposed()) {
			display.asyncExec(new AERunnable() {
				public void runSupport() {
					if (progress != null && !progress.isDisposed()) {
						progress.setSelection(percent_complete);
					}

				}
			});
		}
	}

}
