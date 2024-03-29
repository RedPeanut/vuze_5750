/*
 * Created on 2 juil. 2003
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
package org.gudy.azureus2.ui.swt.views;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.download.DownloadManager;
import org.gudy.azureus2.core3.download.DownloadManagerPeerListener;
import org.gudy.azureus2.core3.global.GlobalManager;
import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.ipfilter.IpFilter;
import org.gudy.azureus2.core3.ipfilter.IpFilterManagerFactory;
import org.gudy.azureus2.core3.peer.PEPeer;
import org.gudy.azureus2.core3.peer.PEPeerManager;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.HashWrapper;
import org.gudy.azureus2.core3.util.TorrentUtils;
import org.gudy.azureus2.plugins.peers.Peer;
import org.gudy.azureus2.plugins.ui.UIInputReceiver;
import org.gudy.azureus2.plugins.ui.UIInputReceiverListener;
import org.gudy.azureus2.plugins.ui.tables.TableManager;
import org.gudy.azureus2.ui.swt.Messages;
import org.gudy.azureus2.ui.swt.SimpleTextEntryWindow;
import org.gudy.azureus2.ui.swt.Utils;
import org.gudy.azureus2.ui.swt.plugins.UISWTInstance;
import org.gudy.azureus2.ui.swt.plugins.UISWTViewEvent;
import org.gudy.azureus2.ui.swt.pluginsimpl.UISWTViewCoreEventListener;
import org.gudy.azureus2.ui.swt.pluginsimpl.UISWTViewCoreEventListenerEx;
import org.gudy.azureus2.ui.swt.pluginsimpl.UISWTViewEventImpl;
import org.gudy.azureus2.ui.swt.views.peer.PeerFilesView;
import org.gudy.azureus2.ui.swt.views.peer.PeerInfoView;
import org.gudy.azureus2.ui.swt.views.peer.RemotePieceDistributionView;
import org.gudy.azureus2.ui.swt.views.table.TableViewSWT;
import org.gudy.azureus2.ui.swt.views.table.TableViewSWTMenuFillListener;
import org.gudy.azureus2.ui.swt.views.table.impl.TableViewFactory;
import org.gudy.azureus2.ui.swt.views.table.impl.TableViewTab;
import org.gudy.azureus2.ui.swt.views.tableitems.peers.ASItem;
import org.gudy.azureus2.ui.swt.views.tableitems.peers.ChokedItem;
import org.gudy.azureus2.ui.swt.views.tableitems.peers.ChokingItem;
import org.gudy.azureus2.ui.swt.views.tableitems.peers.ClientIdentificationItem;
import org.gudy.azureus2.ui.swt.views.tableitems.peers.ClientItem;
import org.gudy.azureus2.ui.swt.views.tableitems.peers.ColumnPeerNetwork;
import org.gudy.azureus2.ui.swt.views.tableitems.peers.ConnectedTimeItem;
import org.gudy.azureus2.ui.swt.views.tableitems.peers.DLedFromOthersItem;
import org.gudy.azureus2.ui.swt.views.tableitems.peers.DiscardedItem;
import org.gudy.azureus2.ui.swt.views.tableitems.peers.DownItem;
import org.gudy.azureus2.ui.swt.views.tableitems.peers.DownSpeedItem;
import org.gudy.azureus2.ui.swt.views.tableitems.peers.DownSpeedLimitItem;
import org.gudy.azureus2.ui.swt.views.tableitems.peers.EncryptionItem;
import org.gudy.azureus2.ui.swt.views.tableitems.peers.GainItem;
import org.gudy.azureus2.ui.swt.views.tableitems.peers.HandshakeReservedBytesItem;
import org.gudy.azureus2.ui.swt.views.tableitems.peers.HostNameItem;
import org.gudy.azureus2.ui.swt.views.tableitems.peers.IncomingRequestCountItem;
import org.gudy.azureus2.ui.swt.views.tableitems.peers.IndexItem;
import org.gudy.azureus2.ui.swt.views.tableitems.peers.InterestedItem;
import org.gudy.azureus2.ui.swt.views.tableitems.peers.InterestingItem;
import org.gudy.azureus2.ui.swt.views.tableitems.peers.IpItem;
import org.gudy.azureus2.ui.swt.views.tableitems.peers.LANItem;
import org.gudy.azureus2.ui.swt.views.tableitems.peers.LatencyItem;
import org.gudy.azureus2.ui.swt.views.tableitems.peers.MessagingItem;
import org.gudy.azureus2.ui.swt.views.tableitems.peers.OptimisticUnchokeItem;
import org.gudy.azureus2.ui.swt.views.tableitems.peers.OutgoingRequestCountItem;
import org.gudy.azureus2.ui.swt.views.tableitems.peers.PeerByteIDItem;
import org.gudy.azureus2.ui.swt.views.tableitems.peers.PeerIDItem;
import org.gudy.azureus2.ui.swt.views.tableitems.peers.PeerSourceItem;
import org.gudy.azureus2.ui.swt.views.tableitems.peers.PercentItem;
import org.gudy.azureus2.ui.swt.views.tableitems.peers.PieceItem;
import org.gudy.azureus2.ui.swt.views.tableitems.peers.PiecesItem;
import org.gudy.azureus2.ui.swt.views.tableitems.peers.PortItem;
import org.gudy.azureus2.ui.swt.views.tableitems.peers.ProtocolItem;
import org.gudy.azureus2.ui.swt.views.tableitems.peers.SnubbedItem;
import org.gudy.azureus2.ui.swt.views.tableitems.peers.StatUpItem;
import org.gudy.azureus2.ui.swt.views.tableitems.peers.StateItem;
import org.gudy.azureus2.ui.swt.views.tableitems.peers.TimeToSendPieceItem;
import org.gudy.azureus2.ui.swt.views.tableitems.peers.TimeUntilCompleteItem;
import org.gudy.azureus2.ui.swt.views.tableitems.peers.TotalDownSpeedItem;
import org.gudy.azureus2.ui.swt.views.tableitems.peers.TypeItem;
import org.gudy.azureus2.ui.swt.views.tableitems.peers.UniquePieceItem;
import org.gudy.azureus2.ui.swt.views.tableitems.peers.UpDownRatioItem;
import org.gudy.azureus2.ui.swt.views.tableitems.peers.UpItem;
import org.gudy.azureus2.ui.swt.views.tableitems.peers.UpRatioItem;
import org.gudy.azureus2.ui.swt.views.tableitems.peers.UpSpeedItem;
import org.gudy.azureus2.ui.swt.views.tableitems.peers.UpSpeedLimitItem;

import com.aelitis.azureus.core.AzureusCoreFactory;
import com.aelitis.azureus.core.networkmanager.NetworkManager;
import com.aelitis.azureus.core.util.IdentityHashSet;
import com.aelitis.azureus.ui.UIFunctions;
import com.aelitis.azureus.ui.UIFunctionsManager;
import com.aelitis.azureus.ui.common.table.TableColumnCore;
import com.aelitis.azureus.ui.common.table.TableDataSourceChangedListener;
import com.aelitis.azureus.ui.common.table.TableLifeCycleListener;
import com.aelitis.azureus.ui.common.table.TableRowCore;
import com.aelitis.azureus.ui.common.table.TableView;
import com.aelitis.azureus.ui.common.table.impl.TableColumnManager;
import com.aelitis.azureus.ui.mdi.MultipleDocumentInterface;
import com.aelitis.azureus.ui.selectedcontent.SelectedContent;
import com.aelitis.azureus.ui.selectedcontent.SelectedContentManager;
import com.aelitis.azureus.ui.swt.UIFunctionsManagerSWT;
import com.aelitis.azureus.ui.swt.UIFunctionsSWT;

import hello.util.Log;

/**
 * @author Olivier
 * @author TuxPaper
 *         2004/Apr/20: Use TableRowImpl instead of PeerRow
 *         2004/Apr/20: Remove need for tableItemToObject
 *         2004/Apr/21: extends TableView instead of IAbstractView
 * @author MjrTom
 *			2005/Oct/08: Add PieceItem
 */

public class PeersView extends TableViewTab<PEPeer>
	implements DownloadManagerPeerListener, TableDataSourceChangedListener,
		TableLifeCycleListener, TableViewSWTMenuFillListener, UISWTViewCoreEventListenerEx {
	
	private static String TAG = PeersView.class.getSimpleName();
	
	static TableColumnCore[] getBasicColumnItems(String tableId) {
		return new TableColumnCore[] {
			new IpItem(tableId),
			new ClientItem(tableId),
			new TypeItem(tableId),
			new MessagingItem(tableId),
			new EncryptionItem(tableId),
			new ProtocolItem(tableId),
			new PiecesItem(tableId),
			new PercentItem(tableId),
			new DownSpeedItem(tableId),
			new UpSpeedItem(tableId),
			new PeerSourceItem(tableId),
			new HostNameItem(tableId),
			new PortItem(tableId),
			new InterestedItem(tableId),
			new ChokedItem(tableId),
			new DownItem(tableId),
			new InterestingItem(tableId),
			new ChokingItem(tableId),
			new OptimisticUnchokeItem(tableId),
			new UpItem(tableId),
			new UpDownRatioItem(tableId),
			new GainItem(tableId),
			new StatUpItem(tableId),
			new SnubbedItem(tableId),
			new TotalDownSpeedItem(tableId),
			new TimeUntilCompleteItem(tableId),
			new DiscardedItem(tableId),
			new UniquePieceItem(tableId),
			new TimeToSendPieceItem(tableId),
			new DLedFromOthersItem(tableId),
			new UpRatioItem(tableId),
			new StateItem(tableId),
			new ConnectedTimeItem(tableId),
			new LatencyItem(tableId),
			new PieceItem(tableId),
			new IncomingRequestCountItem(tableId),
			new OutgoingRequestCountItem(tableId),
			new UpSpeedLimitItem(tableId),
			new DownSpeedLimitItem(tableId),
			new LANItem(tableId),
			new PeerIDItem(tableId),
			new PeerByteIDItem(tableId),
			new HandshakeReservedBytesItem(tableId),
			new ClientIdentificationItem(tableId),
			new ASItem(tableId),
			new IndexItem(tableId),
			new ColumnPeerNetwork(tableId),
		};
	}

	private static final TableColumnCore[] basicItems = getBasicColumnItems(TableManager.TABLE_TORRENT_PEERS);

	static{
		TableColumnManager tcManager = TableColumnManager.getInstance();

		tcManager.setDefaultColumnNames(TableManager.TABLE_TORRENT_PEERS, basicItems);
	}

	public static final String MSGID_PREFIX = "PeersView";

	private DownloadManager manager;
	private TableViewSWT<PEPeer> tv;
	private Shell shell;

	private boolean enable_tabs = true;

	private static boolean registeredCoreSubViews = false;

	private boolean 	comp_focused;
	private Object 		focus_pending_ds;
	private PEPeer		select_peer_pending;


	/**
	 * Initialize
	 *
	 */
	public PeersView() {
		super(MSGID_PREFIX);
	}

	public boolean isCloneable() {
		return (true);
	}

	public UISWTViewCoreEventListener getClone() {
		return (new PeersView());
	}

	// @see org.gudy.azureus2.ui.swt.views.table.impl.TableViewTab#initYourTableView()
	public TableViewSWT<PEPeer> initYourTableView() {
		
		/*
		Log.d(TAG, "initYourTableView() is called...");
		Throwable t = new Throwable();
		t.printStackTrace();
		//*/
		
		tv = TableViewFactory.createTableViewSWT(Peer.class, TableManager.TABLE_TORRENT_PEERS,
				getPropertiesPrefix(), basicItems, "pieces", SWT.MULTI | SWT.FULL_SELECTION
						| SWT.VIRTUAL);
		tv.setRowDefaultHeightEM(1);
		tv.setEnableTabViews(enable_tabs,true,null);

		UIFunctionsSWT uiFunctions = UIFunctionsManagerSWT.getUIFunctionsSWT();
		if (uiFunctions != null) {
			UISWTInstance pluginUI = uiFunctions.getUISWTInstance();

			if (pluginUI != null && !registeredCoreSubViews) {

				pluginUI.addView(TableManager.TABLE_TORRENT_PEERS, "PeerInfoView",
						PeerInfoView.class, null);
				pluginUI.addView(TableManager.TABLE_TORRENT_PEERS,
						"RemotePieceDistributionView", RemotePieceDistributionView.class,
						null);
				pluginUI.addView(TableManager.TABLE_TORRENT_PEERS, "PeerFilesView",
						PeerFilesView.class, null);

				pluginUI.addView(TableManager.TABLE_TORRENT_PEERS, "LoggerView",
						LoggerView.class, true);

				registeredCoreSubViews = true;
			}
		}

		tv.addTableDataSourceChangedListener(this, true);
		tv.addLifeCycleListener(this);
		tv.addMenuFillListener(this);
		return tv;
	}

	  private void
	  setFocused(boolean foc)
	  {
		  if (foc) {

			  comp_focused = true;

			  dataSourceChanged(focus_pending_ds);

		  } else {

			  focus_pending_ds = manager;

			  dataSourceChanged(null);

			  comp_focused = false;
		  }
	  }

	public void tableDataSourceChanged(Object newDataSource) {
		if (!comp_focused) {
			focus_pending_ds = newDataSource;
			return;
		}
		DownloadManager newManager = ViewUtils.getDownloadManagerFromDataSource(newDataSource);
		if (newManager == manager) {
			tv.setEnabled(manager != null);
			return;
		}
		if (manager != null) {
			manager.removePeerListener(this);
		}
		manager = newManager;
		if (tv.isDisposed()) {
			return;
		}
		tv.removeAllTableRows();
		tv.setEnabled(manager != null);
		if (manager != null) {
			manager.addPeerListener(this, false);
			addExistingDatasources();
		}
	}


	// @see com.aelitis.azureus.ui.common.table.TableLifeCycleListener#tableViewInitialized()
	public void tableViewInitialized() {
		Log.d(TAG, "tableViewInitialized() is called...");
		shell = tv.getComposite().getShell();

		if (manager != null) {
			manager.removePeerListener(this);
			manager.addPeerListener(this, false);
		}
		addExistingDatasources();
	}

	public void tableViewDestroyed() {
	  	if (manager != null) {
	  		manager.removePeerListener(this);
	  	}
	
	  	select_peer_pending = null;
	}

	public void fillMenu(String sColumnName, Menu menu) {fillMenu(menu, tv, shell, manager);}

	public static void fillMenu(
		Menu				menu,
		PEPeer				peer,
		DownloadManager 	download_specific) {
		PEPeer[] peers = {peer};

		fillMenu(menu, peers, menu.getShell(), download_specific);
	}

	public static void fillMenu(
		final Menu menu,
		final TableView<PEPeer> tv,
		final Shell shell,
		DownloadManager download_specific ) {
		List<PEPeer>	o_peers = (List<PEPeer>)(Object)tv.getSelectedDataSources();

		PEPeer[]	peers	= o_peers.toArray(new PEPeer[o_peers.size()]);

		fillMenu(menu, peers, shell, download_specific);

	}

	private static void fillMenu(
		final Menu 				menu,
		final PEPeer[]			peers,
		final Shell 			shell,
		DownloadManager 		download_specific ) {
		boolean hasSelection = (peers.length > 0);

		boolean downSpeedDisabled	= false;
		boolean	downSpeedUnlimited	= false;
		long	totalDownSpeed		= 0;
		long	downSpeedSetMax		= 0;
		long	maxDown				= 0;
		boolean upSpeedDisabled		= false;
		boolean upSpeedUnlimited	= false;
		long	totalUpSpeed		= 0;
		long	upSpeedSetMax		= 0;
		long	maxUp				= 0;

		GlobalManager gm = AzureusCoreFactory.getSingleton().getGlobalManager();

		final IdentityHashSet<DownloadManager>	download_managers = new IdentityHashSet<DownloadManager>();

		if (hasSelection) {

			for (int i = 0; i < peers.length; i++) {
				PEPeer peer = peers[i];

				PEPeerManager m = peer.getManager();

				if (m != null) {
					if (gm != null) {

						DownloadManager dm = gm.getDownloadManager(new HashWrapper( m.getHash()));

						if (dm != null) {

							download_managers.add(dm);
						}
					}
				}

				try {
					int maxul = peer.getStats().getUploadRateLimitBytesPerSecond();

					maxUp += maxul * 4;

					if (maxul == 0) {
						upSpeedUnlimited = true;
					} else {
						if (maxul > upSpeedSetMax) {
							upSpeedSetMax	= maxul;
						}
					}
					if (maxul == -1) {
						maxul = 0;
						upSpeedDisabled = true;
					}
					totalUpSpeed += maxul;

					int maxdl = peer.getStats().getDownloadRateLimitBytesPerSecond();

					maxDown += maxdl * 4;

					if (maxdl == 0) {
						downSpeedUnlimited = true;
					} else {
						if (maxdl > downSpeedSetMax) {
							downSpeedSetMax	= maxdl;
						}
					}
					if (maxdl == -1) {
						maxdl = 0;
						downSpeedDisabled = true;
					}
					totalDownSpeed += maxdl;

				} catch (Exception ex) {
					Debug.printStackTrace(ex);
				}
			}
		}

		if (download_specific != null) {
			final MenuItem block_item = new MenuItem(menu, SWT.CHECK);
			PEPeer peer = peers.length==0?null:peers[0];

			if (peer == null || peer.getManager().getDiskManager().getRemainingExcludingDND() > 0) {
				// disallow peer upload blocking when downloading
				block_item.setSelection(false);
				block_item.setEnabled(false);
			} else {
				block_item.setEnabled(true);
				block_item.setSelection(peer.isSnubbed());
			}

			if (peer != null) {
  			final boolean newSnubbedValue = !peer.isSnubbed();

  			Messages.setLanguageText(block_item, "PeersView.menu.blockupload");
  			block_item.addListener(SWT.Selection, new PeersRunner(peers) {
  				public void run(PEPeer peer) {
  					peer.setSnubbed(newSnubbedValue);
  				}
  			});
			}
		} else {

			if (download_managers.size() > 0) {
				MenuItem itemDetails = new MenuItem(menu, SWT.PUSH);
				Messages.setLanguageText(itemDetails, "PeersView.menu.showdownload");
				Utils.setMenuItemImage(itemDetails, "details");
				itemDetails.addListener(SWT.Selection, new Listener() {
					public void handleEvent(Event event) {
						UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
						if (uiFunctions != null) {
							for (DownloadManager dm : download_managers) {
								uiFunctions.getMDI().showEntryByID(
										MultipleDocumentInterface.SIDEBAR_SECTION_TORRENT_DETAILS,
										dm);
							}
						}
					}
				});
				new MenuItem(menu, SWT.SEPARATOR);
			}
		}

		final MenuItem kick_item = new MenuItem(menu, SWT.PUSH);

		Messages.setLanguageText(kick_item, "PeersView.menu.kick");
		kick_item.addListener(SWT.Selection, new PeersRunner(peers) {
			public void run(PEPeer peer) {
				peer.getManager().removePeer(peer,"Peer kicked");
			}
		});

		final MenuItem ban_item = new MenuItem(menu, SWT.PUSH);

		Messages.setLanguageText(ban_item, "PeersView.menu.kickandban");
		ban_item.addListener(SWT.Selection, new PeersRunner(peers) {
			public void run(PEPeer peer) {
				String msg = MessageText.getString("PeersView.menu.kickandban.reason");
				IpFilterManagerFactory.getSingleton().getIPFilter().ban(peer.getIp(),
						msg, true);
				peer.getManager().removePeer(peer, "Peer kicked and banned");
			}
		});

		final MenuItem ban_for_item = new MenuItem(menu, SWT.PUSH);

		Messages.setLanguageText(ban_for_item, "PeersView.menu.kickandbanfor");
		ban_for_item.addListener(SWT.Selection, new PeersRunner(peers) {
			public boolean run(final PEPeer[] peers) {

				String text = MessageText.getString("dialog.ban.for.period.text");

				SimpleTextEntryWindow entryWindow = new SimpleTextEntryWindow(
						"dialog.ban.for.period.title", "!" + text + "!");

				int def = COConfigurationManager.getIntParameter(
						"ban.for.period.default", 60);

				entryWindow.setPreenteredText(String.valueOf(def), false);

				entryWindow.prompt(new UIInputReceiverListener() {
					public void UIInputReceiverClosed(UIInputReceiver entryWindow) {
						if (!entryWindow.hasSubmittedInput()) {
							return;
						}
						String sReturn = entryWindow.getSubmittedInput();
						if (sReturn == null) {
							return;
						}
						int mins = -1;
						try {
							mins = Integer.valueOf(sReturn).intValue();
						} catch (NumberFormatException er) {
							// Ignore
						}
						if (mins <= 0) {
							MessageBox mb = new MessageBox(Utils.findAnyShell(), SWT.ICON_ERROR
									| SWT.OK);
							mb.setText(MessageText.getString("MyTorrentsView.dialog.NumberError.title"));
							mb.setMessage(MessageText.getString("MyTorrentsView.dialog.NumberError.text"));
							mb.open();
							return;
						}
						COConfigurationManager.setParameter("ban.for.period.default", mins);
						IpFilter filter = IpFilterManagerFactory.getSingleton().getIPFilter();
						for (PEPeer peer: peers) {
							String msg = MessageText.getString("PeersView.menu.kickandbanfor.reason", new String[]{ String.valueOf(mins)});
							filter.ban(peer.getIp(), msg, true, mins);
							peer.getManager().removePeer(peer, "Peer kicked and banned");
						}
					}
				});

				return (true);
			}
		});

		// === advanced menu ===

		final MenuItem itemAdvanced = new MenuItem(menu, SWT.CASCADE);
		Messages.setLanguageText(itemAdvanced, "MyTorrentsView.menu.advancedmenu");
		itemAdvanced.setEnabled(hasSelection);

		final Menu menuAdvanced = new Menu(shell, SWT.DROP_DOWN);
		itemAdvanced.setMenu(menuAdvanced);

		// advanced > Download Speed Menu //

		Map<String,Object> menu_properties = new HashMap<String,Object>();
		menu_properties.put(ViewUtils.SM_PROP_PERMIT_UPLOAD_DISABLE, true);
		menu_properties.put(ViewUtils.SM_PROP_PERMIT_DOWNLOAD_DISABLE, true);

		ViewUtils.addSpeedMenu(
			shell,
			menuAdvanced, true, true,
			false,
			hasSelection,
			downSpeedDisabled,
			downSpeedUnlimited,
			totalDownSpeed,
			downSpeedSetMax,
			maxDown,
			upSpeedDisabled,
			upSpeedUnlimited,
			totalUpSpeed,
			upSpeedSetMax,
			maxUp,
			peers.length,
			menu_properties,
			new ViewUtils.SpeedAdapter() {
				public void setDownSpeed(
					int speed ) {
					if (peers.length > 0) {
						for (int i = 0; i < peers.length; i++) {
							try {
								PEPeer peer = (PEPeer)peers[i];
								peer.getStats().setDownloadRateLimitBytesPerSecond(speed);
							} catch (Exception e) {
								Debug.printStackTrace(e);
							}
						}
					}
				}

				public void setUpSpeed(
					int speed ) {

					if (peers.length > 0) {
						for (int i = 0; i < peers.length; i++) {
							try {
								PEPeer peer = (PEPeer)peers[i];
								peer.getStats().setUploadRateLimitBytesPerSecond(speed);
							} catch (Exception e) {
								Debug.printStackTrace(e);
							}
						}
					}
				}
			});

		addPeersMenu(download_specific, menu);

		new MenuItem (menu, SWT.SEPARATOR);
	}

	public void addThisColumnSubMenu(String columnName, Menu menuThisColumn) {
		if (addPeersMenu(manager, menuThisColumn)) {
			new MenuItem(menuThisColumn, SWT.SEPARATOR);
		}
	}

	private static boolean addPeersMenu(
	  	final DownloadManager 	man,
		Menu					menu) {
		
	  	if (man == null) {
	  		return (false);
	  	}
	  	
	  	PEPeerManager pm = man.getPeerManager();
	  	if (pm == null) {
	  		return (false);
	  	}
	  	
	  	if (TorrentUtils.isReallyPrivate(man.getTorrent())) {
	  		return (false);
	  	}
	  	
	  	new MenuItem(menu, SWT.SEPARATOR);
		MenuItem add_peers_item = new MenuItem(menu, SWT.PUSH);
		Messages.setLanguageText( add_peers_item, "menu.add.peers");

		add_peers_item.addListener(
			SWT.Selection,
			new Listener() {
				public void handleEvent(Event event) {
					SimpleTextEntryWindow entryWindow = new SimpleTextEntryWindow(
							"dialog.add.peers.title",
							"dialog.add.peers.msg");
					String def = COConfigurationManager.getStringParameter("add.peers.default", "");
					entryWindow.setPreenteredText(String.valueOf( def ), false);
					entryWindow.prompt(
						new UIInputReceiverListener() {
							public void UIInputReceiverClosed(UIInputReceiver entryWindow) {
								if (!entryWindow.hasSubmittedInput()) {
									return;
								}
								String sReturn = entryWindow.getSubmittedInput();
								if (sReturn == null) {
									return;
								}
								COConfigurationManager.setParameter("add.peers.default", sReturn);
							  	PEPeerManager pm = man.getPeerManager();
							  	if (pm == null) {
							  		return;
							  	}
							  	String[] bits = sReturn.split(",");
							  	for (String bit: bits) {
							  		bit = bit.trim();
							  		int	pos = bit.lastIndexOf(':');
							  		if (pos != -1) {
							  			String host = bit.substring(0, pos).trim();
							  			String port = bit.substring(pos+1).trim();
							  			try {
							  				int	i_port = Integer.parseInt(port);
							  				pm.addPeer(host, i_port, 0, NetworkManager.getCryptoRequired(NetworkManager.CRYPTO_OVERRIDE_NONE), null);
							  			} catch (Throwable e) {
							  			}
							  		} else {
						  				pm.addPeer(bit, 6881, 0, NetworkManager.getCryptoRequired( NetworkManager.CRYPTO_OVERRIDE_NONE ), null);
							  		}
							  	}
							}
						});
				}
			}
		);

		return (true);
	}

	/* DownloadManagerPeerListener implementation */
	public void peerAdded(PEPeer created) {
		//Log.d(TAG, "peerAdded() is called...");
		tv.addDataSource(created);
	}

	public void peerRemoved(PEPeer removed) {
		tv.removeDataSource(removed);
	}

	public void selectPeer(PEPeer peer) {
		showPeer(peer, 0);
	}

	private void showPeer(
		final PEPeer		peer,
		final int			attempt ) {
		
		if (attempt > 10 || tv == null) {
			return;
		}
		
		// need to insert an async here as if we are in the process of switching to this view the
		// selection sometimes get lost. grrr
		// also, due to the way things work, as the table is building it is possible to select the entry
		// only to have the selection get lost due to the table re-calculating stuff, so we keep trying for
		// a while until we get an affirmation that it really is visible
		Utils.execSWTThreadLater(
			attempt==0?1:10,
			new Runnable() {
				public void run() {
					TableRowCore row = tv.getRow(peer);
					if (row == null) {
						if (attempt == 0) {
							select_peer_pending = peer;
							return;
						}
					} else {
						tv.setSelectedRows(new TableRowCore[]{ row });
						tv.showRow(row);
						if (row.isVisible()) {
							return;
						}
					}
					showPeer(peer, attempt+1);
				}
			});
	}

	public void peerManagerWillBeAdded(PEPeerManager	peer_manager) {}
	public void peerManagerAdded(PEPeerManager manager) {	}
	public void peerManagerRemoved(PEPeerManager manager) {
		tv.removeAllTableRows();
	}

	/**
	 * Add datasources already in existance before we called addListener.
	 * Faster than allowing addListener to call us one datasource at a time.
	 */
	private void addExistingDatasources() {
		if (manager == null || tv.isDisposed()) {
			return;
		}
		PEPeer[] dataSources = manager.getCurrentPeers();
		if (dataSources != null && dataSources.length > 0) {
			tv.addDataSources(dataSources);
			tv.processDataSourceQueue();
		}
		if (select_peer_pending != null) {
			showPeer(select_peer_pending, 1);
			select_peer_pending = null;
		}
	}

	public boolean eventOccurred(UISWTViewEvent event) {
	    switch (event.getType()) {
	      case UISWTViewEvent.TYPE_CREATE:{
	    	  if (event instanceof UISWTViewEventImpl) {
	    		  String parent = ((UISWTViewEventImpl)event).getParentID();
	    		  enable_tabs = parent != null && parent.equals(UISWTInstance.VIEW_TORRENT_DETAILS);
	    	  }
	    	  break;
	      }
	      case UISWTViewEvent.TYPE_FOCUSGAINED:
	      	String id = "DMDetails_Peers";
	      	setFocused(true);	// do this here to pick up corrent manager before rest of code
	      	if (manager != null) {
	      		if (manager.getTorrent() != null) {
	  					id += "." + manager.getInternalName();
	      		} else {
	      			id += ":" + manager.getSize();
	      		}
						SelectedContentManager.changeCurrentlySelectedContent(id,
								new SelectedContent[] {
									new SelectedContent(manager)
						});
					} else {
						SelectedContentManager.changeCurrentlySelectedContent(id, null);
					}
		    break;
	      case UISWTViewEvent.TYPE_FOCUSLOST:
	    	  setFocused(false);
	    		SelectedContentManager.clearCurrentlySelectedContent();
	    	  break;
	    }
	    return (super.eventOccurred(event));
	}

	private static abstract class PeersRunner implements Listener {
		
		private PEPeer[] peers;
		
		private PeersRunner(PEPeer[] _peers) {
			peers = _peers;
		}
		
		public void handleEvent(Event e) {
			if (!run(peers)) {
				for (PEPeer peer: peers) {
					run(peer);
				}
			}
		}
		
		public void run(PEPeer peer) {
		}
		
		public boolean run(PEPeer[]	peers) {
			return (false);
		}
	}
}
