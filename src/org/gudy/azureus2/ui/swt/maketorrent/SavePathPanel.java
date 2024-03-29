/*
 * File		: SavePathPanel.java
 * Created : 30 sept. 2003 17:06:45
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
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.torrent.TOTorrentFactory;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.DisplayFormatters;
import org.gudy.azureus2.ui.swt.Messages;
import org.gudy.azureus2.ui.swt.Utils;
import org.gudy.azureus2.ui.swt.wizard.AbstractWizardPanel;
import org.gudy.azureus2.ui.swt.wizard.IWizardPanel;

/**
 * @author Olivier
 *
 */
public class SavePathPanel extends AbstractWizardPanel<NewTorrentWizard> {

	protected long	fileSize;
	protected long	pieceSize;
	protected long	pieceCount;

	public SavePathPanel(NewTorrentWizard wizard,AbstractWizardPanel<NewTorrentWizard> _previousPanel) {
		super(wizard, _previousPanel);
	}

	/* (non-Javadoc)
	 * @see org.gudy.azureus2.ui.swt.maketorrent.IWizardPanel#show()
	 */
	public void show() {
		
		
		
		try {
			if (wizard.create_mode == NewTorrentWizard.MODE_BYO) {
				fileSize = TOTorrentFactory.getTorrentDataSizeFromFileOrDir(wizard.byo_desc_file, true);
			} else {
				fileSize = TOTorrentFactory.getTorrentDataSizeFromFileOrDir(new File( wizard.create_mode==NewTorrentWizard.MODE_DIRECTORY? wizard.directoryPath: wizard.singlePath), false);
			}
			pieceSize = TOTorrentFactory.getComputedPieceSize(fileSize);

			pieceCount = TOTorrentFactory.getPieceCount(fileSize, pieceSize);
		} catch (Throwable e) {
			Debug.printStackTrace(e);
		}
		wizard.setTitle(MessageText.getString("wizard.torrentFile"));
		wizard.setCurrentInfo(MessageText.getString("wizard.choosetorrent"));
		Composite panel = wizard.getPanel();
		GridLayout layout = new GridLayout();
		layout.numColumns = 3;
		panel.setLayout(layout);
		Label label;/* = new Label(panel,SWT.NULL);
		Messages.setLanguageText(label,"wizard.file");*/
		final Text file = new Text(panel,SWT.BORDER);

		file.addModifyListener(new ModifyListener() {
			/* (non-Javadoc)
			 * @see org.eclipse.swt.events.ModifyListener#modifyText(org.eclipse.swt.events.ModifyEvent)
			 */
			public void modifyText(ModifyEvent arg0) {
				String fName = file.getText();
				wizard.savePath = fName;
				String error = "";
				if (! fName.equals("")) {
					File f = new File(file.getText());
					if (f.isDirectory() || ( f.getParentFile() != null && !f.getParentFile().canWrite())) {
						error = MessageText.getString("wizard.invalidfile");
					} else {
						String	parent = f.getParent();
						if (parent != null) {
							wizard.setDefaultSaveDir(parent);
						}
					}
				}
				wizard.setErrorMessage(error);
				wizard.setFinishEnabled(!wizard.savePath.equals("") && error.equals(""));
			}
		});
		String	default_save = wizard.getDefaultSaveDir();
			// if we have a default save dir then use this as the basis for save location
		String	target_file;
		if (wizard.create_mode == NewTorrentWizard.MODE_BYO) {
			target_file = "";
			if (wizard.byo_map != null) {
				java.util.List list = (java.util.List) wizard.byo_map.get("file_map");
				if (list != null) {
					Map map = (Map) list.get(0);
					if (map != null) {
						java.util.List path = (java.util.List) map.get("logical_path");
						if (path != null) {
							target_file = new File(
									COConfigurationManager.getStringParameter("General_sDefaultTorrent_Directory"),
									(String) path.get(0) + ".torrent").getAbsolutePath();
						}
					}
				}
			}
		} else if (wizard.create_mode == NewTorrentWizard.MODE_DIRECTORY) {
			target_file = wizard.directoryPath + ".torrent";
		} else {
			target_file = wizard.singlePath + ".torrent";
		}

		if (default_save.length() > 0 && target_file.length() > 0) {
			File temp = new File(target_file);
			String	existing_parent = temp.getParent();
			if (existing_parent != null) {
				target_file	= new File( default_save, temp.getName()).toString();
			}
		}

		wizard.savePath = target_file;

		file.setText( wizard.savePath);
		GridData gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.horizontalSpan = 2;
		Utils.setLayoutData(file, gridData);
		Button browse = new Button(panel,SWT.PUSH);
		browse.addListener(SWT.Selection,new Listener() {
			/* (non-Javadoc)
			 * @see org.eclipse.swt.widgets.Listener#handleEvent(org.eclipse.swt.widgets.Event)
			 */
			public void handleEvent(Event arg0) {
				FileDialog fd = new FileDialog(wizard.getWizardWindow(),SWT.SAVE);
				final String path = wizard.savePath;
				if (wizard.getErrorMessage().equals("") && !path.equals("")) {
						File fsPath = new File(path);
						if (!path.endsWith(File.separator)) {
								fd.setFilterPath(fsPath.getParent());
								fd.setFileName(fsPath.getName());
						}
						else {
								fd.setFileName(path);
						}
				}
				String f = fd.open();
				if (f != null) {
						file.setText(f);
						File	ff = new File(f);
						String	parent = ff.getParent();
						if (parent != null) {
								wizard.setDefaultSaveDir(parent);
						}
					}
			}
		});
		Messages.setLanguageText(browse,"wizard.browse");

		// ----------------------

		label = new Label(panel, SWT.SEPARATOR | SWT.HORIZONTAL);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.horizontalSpan = 3;
		Utils.setLayoutData(label, gridData);

		Composite gFileStuff = new Composite(panel, SWT.NULL);
		gridData = new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL);
		gridData.horizontalSpan = 3;
		Utils.setLayoutData(gFileStuff, gridData);
		layout = new GridLayout();
		layout.numColumns = 4;
		gFileStuff.setLayout(layout);

		// file size

		label = new Label(gFileStuff, SWT.NULL);
		Messages.setLanguageText(label, "wizard.maketorrent.filesize");

		Label file_size_label = new Label(gFileStuff, SWT.NULL);
		file_size_label.setText( DisplayFormatters.formatByteCountToKiBEtc(fileSize));

		label = new Label(gFileStuff, SWT.NULL);
		label = new Label(gFileStuff, SWT.NULL);

		// piece count

		label = new Label(gFileStuff, SWT.NULL);
		Messages.setLanguageText(label, "wizard.maketorrent.piececount");

		final Label piece_count_label = new Label(gFileStuff, SWT.NULL);
		piece_count_label.setText(""+pieceCount);
		label = new Label(gFileStuff, SWT.NULL);
		label = new Label(gFileStuff, SWT.NULL);

 		// piece size

		label = new Label(gFileStuff, SWT.NULL);
		Messages.setLanguageText(label, "wizard.maketorrent.piecesize");

		final Label piece_size_label = new Label(gFileStuff, SWT.NULL);
		gridData = new GridData();
		gridData.widthHint = 75;
		Utils.setLayoutData(piece_size_label, gridData);
		piece_size_label.setText(DisplayFormatters.formatByteCountToKiBEtc( pieceSize));

		final Combo manual = new Combo(gFileStuff, SWT.SINGLE | SWT.READ_ONLY);
		final long[] sizes = TOTorrentFactory.STANDARD_PIECE_SIZES;

		manual.add(MessageText.getString("wizard.maketorrent.auto"));

		for (int i=0;i<sizes.length;i++) {
			manual.add(DisplayFormatters.formatByteCountToKiBEtc(sizes[i]));
		}

		manual.select(0);

		manual.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event e) {
				int	index = manual.getSelectionIndex();
				if (index == 0) {
					wizard.setPieceSizeComputed();
					pieceSize = TOTorrentFactory.getComputedPieceSize(fileSize);
		 		} else {
					pieceSize = sizes[index-1];
					wizard.setPieceSizeManual(pieceSize);
				}
				pieceCount = TOTorrentFactory.getPieceCount(fileSize, pieceSize);
				piece_size_label.setText( DisplayFormatters.formatByteCountToKiBEtc(pieceSize));
				piece_count_label.setText(""+pieceCount);
			}
		});

		label = new Label(gFileStuff, SWT.NULL);

		// ------------------------
		label = new Label(panel, SWT.SEPARATOR | SWT.HORIZONTAL);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.horizontalSpan = 3;
		Utils.setLayoutData(label, gridData);

		final Button bAutoOpen = new Button(panel,SWT.CHECK);
		Messages.setLanguageText(bAutoOpen,"wizard.maketorrents.autoopen");
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.horizontalSpan = 3;
		Utils.setLayoutData(bAutoOpen, gridData);

		final Button bforce = new Button(panel,SWT.CHECK);
		Messages.setLanguageText(bforce,"wizard.maketorrents.force");
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.horizontalSpan = 3;
		Utils.setLayoutData(bforce, gridData);

		final Button bSuperSeed = new Button(panel,SWT.CHECK);
		Messages.setLanguageText(bSuperSeed,"wizard.maketorrents.superseed");
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.horizontalSpan = 3;
		Utils.setLayoutData(bSuperSeed, gridData);

		final Button bAutoHost = new Button(panel,SWT.CHECK);
		Messages.setLanguageText(bAutoHost,"wizard.maketorrents.autohost");
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.horizontalSpan = 3;
		Utils.setLayoutData(bAutoHost, gridData);

		label = new Label(panel,SWT.NULL);
		Messages.setLanguageText(label,"wizard.maketorrents.init.tags");
		final Text tagArea = new Text(panel,SWT.BORDER);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.horizontalSpan = 2;
		Utils.setLayoutData(tagArea, gridData);

		bforce.setEnabled(false);
		tagArea.setEnabled(false);
		bSuperSeed.setEnabled(false);
		bAutoHost.setEnabled(false);

		bAutoOpen.addListener(SWT.Selection,new Listener() {
			public void handleEvent(Event event) {
				boolean autoOpen = wizard.autoOpen = bAutoOpen.getSelection();

				boolean enable = autoOpen && wizard.getTrackerType() != NewTorrentWizard.TT_EXTERNAL;

				bforce.setEnabled(autoOpen);
				tagArea.setEnabled(autoOpen);
				bSuperSeed.setEnabled(autoOpen);
				bAutoHost.setEnabled(enable);
			}
		});

		bforce.addListener(SWT.Selection,new Listener() {
			public void handleEvent(Event event) {
				wizard.forceStart = bforce.getSelection();
			}
		});

		tagArea.setText( wizard.getInitialTags(false));
		tagArea.addModifyListener(new ModifyListener() {
			public void modifyText( ModifyEvent arg0) {
				wizard.setInitialTags( tagArea.getText().trim());
			}
		});


		bSuperSeed.addListener(SWT.Selection,new Listener() {
			public void handleEvent(Event event) {
				wizard.superseed = bSuperSeed.getSelection();
			}
		});

		bAutoHost.addListener(SWT.Selection,new Listener() {
			public void handleEvent(Event event) {
				wizard.autoHost = bAutoHost.getSelection();
			}
		});

		final Button bPrivateTorrent = new Button(panel,SWT.CHECK);
		Messages.setLanguageText(bPrivateTorrent,"ConfigView.section.sharing.privatetorrent");
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.horizontalSpan = 3;
		Utils.setLayoutData(bPrivateTorrent, gridData);

		final Button bAllowDHT = new Button(panel,SWT.CHECK);
		Messages.setLanguageText(bAllowDHT,"ConfigView.section.sharing.permitdht");
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.horizontalSpan = 3;
		Utils.setLayoutData(bAllowDHT, gridData);
		bAllowDHT.setSelection(true);

		bAllowDHT.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event event) {
				wizard.permitDHT = bAllowDHT.getSelection();
			}
		});

		// terrible code, who wrote this?
		if (wizard.getTrackerType() == NewTorrentWizard.TT_DECENTRAL) {
			wizard.setPrivateTorrent(false);
		}

		boolean privateTorrent = wizard.getPrivateTorrent();

		bAllowDHT.setEnabled(!privateTorrent);
		if (privateTorrent) {
			bAllowDHT.setSelection(false);
			wizard.permitDHT = false;
		}

		bPrivateTorrent.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event event) {
				boolean privateTorrent = bPrivateTorrent.getSelection();

				wizard.setPrivateTorrent(privateTorrent);

				if (privateTorrent) {
					bAllowDHT.setSelection(false);
					wizard.permitDHT = false;
				}
				bAllowDHT.setEnabled(!privateTorrent);
			}
		});

		if (wizard.getTrackerType() == NewTorrentWizard.TT_DECENTRAL) {

			bAllowDHT.setEnabled(false);
			bPrivateTorrent.setEnabled(false);
		} else {
			bPrivateTorrent.setSelection(privateTorrent);
		}
	}

	public IWizardPanel<NewTorrentWizard> getFinishPanel() {
		return new ProgressPanel(wizard, this);
	}

	public boolean isFinishSelectionOK() {
		String savePath = wizard.savePath;
		File f = new File(savePath);
		if (f.isFile()) {
			MessageBox mb = new MessageBox(wizard.getWizardWindow(), SWT.ICON_QUESTION | SWT.YES | SWT.NO);
			mb.setText(MessageText.getString("exportTorrentWizard.process.outputfileexists.title"));
			mb.setMessage(MessageText.getString("exportTorrentWizard.process.outputfileexists.message"));
			int result = mb.open();
			if (result == SWT.NO) {
				return (false);
			}
		}
		return (true);
	}
}
