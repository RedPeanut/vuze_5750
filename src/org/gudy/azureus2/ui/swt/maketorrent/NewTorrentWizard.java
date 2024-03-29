/*
 * File : Wizard.java Created : 12 oct. 2003 14:30:57 By : Olivier
 *
 * Azureus - a Java Bittorrent client
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details (see the LICENSE file).
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place, Suite 330, Boston, MA 02111-1307 USA
 */

package org.gudy.azureus2.ui.swt.maketorrent;

import java.io.File;
import java.util.*;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.*;
import org.eclipse.swt.widgets.*;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.torrent.TOTorrentCreator;
import org.gudy.azureus2.core3.util.TorrentUtils;
import org.gudy.azureus2.ui.swt.URLTransfer;
import org.gudy.azureus2.ui.swt.Utils;
import org.gudy.azureus2.ui.swt.wizard.AbstractWizardPanel;
import org.gudy.azureus2.ui.swt.wizard.IWizardPanel;
import org.gudy.azureus2.ui.swt.wizard.Wizard;

/**
 * @author Olivier
 *
 */
public class NewTorrentWizard extends Wizard {
	
	static final int	TT_LOCAL		= 1;
	static final int	TT_EXTERNAL		= 2;
	static final int	TT_DECENTRAL	= 3;

	static final String	TT_EXTERNAL_DEFAULT 	= "http://";
	static final String	TT_DECENTRAL_DEFAULT	= TorrentUtils.getDecentralisedEmptyURL().toString();

	private static String	defaultOpenDir 	= COConfigurationManager.getStringParameter("CreateTorrent.default.open", "");
	private static String	defaultSaveDir 	= COConfigurationManager.getStringParameter("CreateTorrent.default.save", "");
	private static String	comment 		= COConfigurationManager.getStringParameter("CreateTorrent.default.comment", "");
	private static int 		trackerType 	= COConfigurationManager.getIntParameter("CreateTorrent.default.trackertype", TT_LOCAL);

	static {
		// default the default to the "save torrents to" location
		if (defaultSaveDir.length() == 0) {
			defaultSaveDir = COConfigurationManager.getStringParameter("General_sDefaultTorrent_Directory", "");
		}
	}

	//false : singleMode, true: directory
	 protected static final int MODE_SINGLE_FILE	= 1;
	 protected static final int MODE_DIRECTORY		= 2;
	 protected static final int MODE_BYO			= 3;

	int create_mode = MODE_BYO;
	String singlePath = "";
	String directoryPath = "";
	String savePath = "";

	File	byo_desc_file;
	Map	byo_map;

	String trackerURL = TT_EXTERNAL_DEFAULT;

	boolean computedPieceSize = true;
	long	manualPieceSize;

	boolean 			useMultiTracker = false;
	boolean 			useWebSeed = false;

	private boolean 	addOtherHashes	= 	COConfigurationManager.getBooleanParameter("CreateTorrent.default.addhashes", false);

	String multiTrackerConfig = "";
	List trackers = new ArrayList();

	String webSeedConfig = "";
	Map	webseeds = new HashMap();

	boolean autoOpen 		= false;
	boolean autoHost 		= false;
	boolean forceStart		= false;
	String	initialTags		= COConfigurationManager.getStringParameter("CreateTorrent.default.initialTags", "");
	boolean superseed		= false;
	boolean permitDHT		= true;

	TOTorrentCreator creator = null;

	public NewTorrentWizard(Display display) {
		super("wizard.title");

		cancel.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event arg0) {
				if (creator != null)
					creator.cancel();
			}
		});

		trackers.add(new ArrayList());
		trackerURL = Utils.getLinkFromClipboard(display, false, false);
		ModePanel panel = new ModePanel(this, null);
		createDropTarget(getWizardWindow());
		this.setFirstPanel(panel);
	}

	protected int getTrackerType() {
		return (trackerType);
	}

	protected void setTrackerType(int type) {
		trackerType = type;
		COConfigurationManager.setParameter("CreateTorrent.default.trackertype", trackerType);
	}

	protected String getDefaultOpenDir() {
		return (defaultOpenDir);
	}

	protected void setDefaultOpenDir(String d) {
		defaultOpenDir = d;
		COConfigurationManager.setParameter("CreateTorrent.default.open", defaultOpenDir);
	}

	protected String getDefaultSaveDir() {
		return (defaultSaveDir);
	}

	protected void setDefaultSaveDir(String d) {
		defaultSaveDir = d;
		COConfigurationManager.setParameter("CreateTorrent.default.save", defaultSaveDir);
	}

	protected String getInitialTags(boolean save) {
		if (save) {
			COConfigurationManager.setParameter("CreateTorrent.default.initialTags", initialTags);
		}
		return (initialTags);
	}

	protected void setInitialTags(String tags) {
		initialTags = tags;
	}

	void setComment(String s) {
		comment = s;
		COConfigurationManager.setParameter("CreateTorrent.default.comment", comment);
	}

	String getComment() {
		return (comment);
	}
	
	private void createDropTarget(final Control control) {
		DropTarget dropTarget = new DropTarget(control, DND.DROP_DEFAULT | DND.DROP_MOVE | DND.DROP_COPY | DND.DROP_LINK);
		dropTarget.setTransfer(new Transfer[] { URLTransfer.getInstance(), FileTransfer.getInstance()});
		dropTarget.addDropListener(new DropTargetAdapter() {
			public void dragOver(DropTargetEvent event) {
				if (URLTransfer.getInstance().isSupportedType(event.currentDataType)) {
					event.detail = getCurrentPanel() instanceof ModePanel ? DND.DROP_LINK : DND.DROP_NONE;
				}
			}
			public void drop(DropTargetEvent event) {
				if (event.data instanceof String[]) {
					String[] sourceNames = (String[]) event.data;
					if (sourceNames == null )
						event.detail = DND.DROP_NONE;
					if (event.detail == DND.DROP_NONE)
						return;

					for (String droppedFileStr: sourceNames) {
						File droppedFile = new File(droppedFileStr);
						if (getCurrentPanel() instanceof ModePanel) {
						} else if (getCurrentPanel() instanceof DirectoryPanel) {
							if (droppedFile.isDirectory())
								((DirectoryPanel) getCurrentPanel()).setFilename(droppedFile.getAbsolutePath());
						} else if (getCurrentPanel() instanceof SingleFilePanel) {
							if (droppedFile.isFile())
								((SingleFilePanel) getCurrentPanel()).setFilename(droppedFile.getAbsolutePath());
						} else if (getCurrentPanel() instanceof BYOPanel) {
							((BYOPanel) getCurrentPanel()).addFilename(droppedFile);

							continue;
						}
						break;
					}
				} else if (getCurrentPanel() instanceof ModePanel) {
					trackerURL = ((URLTransfer.URLType)event.data).linkURL;
					((ModePanel) getCurrentPanel()).updateTrackerURL();
				}
			 }
		});
	}

	protected void setPieceSizeComputed() {
		computedPieceSize = true;
	}

	public boolean getPieceSizeComputed() {
		return (computedPieceSize);
	}

	protected void setPieceSizeManual(long _value) {
		computedPieceSize = false;
		manualPieceSize = _value;
	}

	protected long getPieceSizeManual() {
		return (manualPieceSize);
	}

	protected void setAddOtherHashes(boolean o) {
		addOtherHashes = o;
		COConfigurationManager.setParameter("CreateTorrent.default.addhashes", addOtherHashes);

	}

	protected boolean getPrivateTorrent() {
		return (COConfigurationManager.getBooleanParameter("CreateTorrent.default.privatetorrent", false));
	}

	protected void setPrivateTorrent(boolean privateTorrent) {
		COConfigurationManager.setParameter("CreateTorrent.default.privatetorrent", privateTorrent);
	}

	protected boolean getAddOtherHashes() {
		return (addOtherHashes);
	}

	protected IWizardPanel<NewTorrentWizard> getNextPanelForMode(AbstractWizardPanel<NewTorrentWizard> prev) {
		switch (create_mode) {
			case MODE_DIRECTORY:
				return new DirectoryPanel(this, prev);
			case MODE_SINGLE_FILE:
				return new SingleFilePanel(this, prev);
			default:
				return new BYOPanel(this, prev);
		}
	}
}