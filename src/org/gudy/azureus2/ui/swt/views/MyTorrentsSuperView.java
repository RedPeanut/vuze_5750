/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.	See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA	02111-1307, USA.
 */
package org.gudy.azureus2.ui.swt.views;

import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.download.DownloadManager;
import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.util.AEDiagnosticsEvidenceGenerator;
import org.gudy.azureus2.core3.util.AERunnable;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.IndentWriter;
import org.gudy.azureus2.plugins.ui.UIPluginViewToolBarListener;
import org.gudy.azureus2.plugins.ui.tables.TableManager;
import org.gudy.azureus2.ui.swt.DelayedListenerMultiCombiner;
import org.gudy.azureus2.ui.swt.Messages;
import org.gudy.azureus2.ui.swt.Utils;
import org.gudy.azureus2.ui.swt.plugins.UISWTInstance;
import org.gudy.azureus2.ui.swt.plugins.UISWTView;
import org.gudy.azureus2.ui.swt.plugins.UISWTViewEvent;
import org.gudy.azureus2.ui.swt.pluginsimpl.UISWTViewCoreEventListener;
import org.gudy.azureus2.ui.swt.pluginsimpl.UISWTViewImpl;
import org.gudy.azureus2.ui.swt.views.table.TableViewSWT;
import org.gudy.azureus2.ui.swt.views.table.impl.TableViewSWT_TabsCommon;
import org.gudy.azureus2.ui.swt.views.table.utils.TableColumnCreator;

import com.aelitis.azureus.core.AzureusCore;
import com.aelitis.azureus.core.AzureusCoreFactory;
import com.aelitis.azureus.core.AzureusCoreRunningListener;
import com.aelitis.azureus.ui.common.ToolBarItem;
import com.aelitis.azureus.ui.common.table.TableColumnCore;
import com.aelitis.azureus.ui.common.table.TableView;
import com.aelitis.azureus.ui.common.table.impl.TableColumnManager;
import com.aelitis.azureus.ui.selectedcontent.ISelectedContent;
import com.aelitis.azureus.ui.selectedcontent.SelectedContentListener;
import com.aelitis.azureus.ui.selectedcontent.SelectedContentManager;
import com.aelitis.azureus.ui.swt.mdi.MdiEntrySWT;
import com.aelitis.azureus.util.MapUtils;

/**
 * Wraps a "Incomplete" torrent list and a "Complete" torrent list into
 * one view
 */
public class MyTorrentsSuperView
	implements UISWTViewCoreEventListener,
	AEDiagnosticsEvidenceGenerator, UIPluginViewToolBarListener
{
	private static int SASH_WIDTH = 5;

	private MyTorrentsView torrentview;
	private MyTorrentsView seedingview;
	private Composite form;
	private MyTorrentsView lastSelectedView;

	private Composite child1;
	private Composite child2;
	private final Text txtFilter;
	private final Composite cCats;
	private Object ds;
	private UISWTView swtView;
	private MyTorrentsView viewWhenDeactivated;

	public MyTorrentsSuperView(Text txtFilter, Composite cCats) {
		this.txtFilter = txtFilter;
		this.cCats = cCats;
		AzureusCoreFactory.addCoreRunningListener(new AzureusCoreRunningListener() {
			public void azureusCoreRunning(AzureusCore core) {
				Utils.execSWTThread(new AERunnable() {
					public void runSupport() {
						TableColumnManager tcManager = TableColumnManager.getInstance();
						tcManager.addColumns(getCompleteColumns());
						tcManager.addColumns(getIncompleteColumns());
					}
				});
			}
		});
	}


	public Composite getComposite() {
		return form;
	}

	public void initialize(final Composite parent) {
		if (form != null) {
			return;
		}

		form = new Composite(parent, SWT.NONE);
		FormLayout flayout = new FormLayout();
		flayout.marginHeight = 0;
		flayout.marginWidth = 0;
		form.setLayout(flayout);
		GridData gridData;
		gridData = new GridData(GridData.FILL_BOTH);
		form.setLayoutData(gridData);


		GridLayout layout;


		child1 = new Composite(form,SWT.NONE);
		layout = new GridLayout();
		layout.numColumns = 1;
		layout.horizontalSpacing = 0;
		layout.verticalSpacing = 0;
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		child1.setLayout(layout);

		final Sash sash = Utils.createSash(form, SASH_WIDTH);

		child2 = new Composite(form,SWT.NULL);
		layout = new GridLayout();
		layout.numColumns = 1;
		layout.horizontalSpacing = 0;
		layout.verticalSpacing = 0;
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		child2.setLayout(layout);

		FormData formData;

		// More precision, times by 100
		int weight = (int) (COConfigurationManager.getFloatParameter("MyTorrents.SplitAt"));
		if (weight > 10000) {
			weight = 10000;
		} else if (weight < 100) {
			weight *= 100;
		}
		// Min/max of 5%/95%
		if (weight < 500) {
			weight = 500;
		} else if (weight > 9000) {
			weight = 9000;
		}
		double pct = (float)weight / 10000;
		sash.setData("PCT", new Double(pct));

		// FormData for table child1
		formData = new FormData();
		formData.left = new FormAttachment(0, 0);
		formData.right = new FormAttachment(100, 0);
		formData.top = new FormAttachment(0, 0);
		formData.bottom = new FormAttachment((int) (pct * 100), 0);
		child1.setLayoutData(formData);
		final FormData child1Data = formData;

		// sash
		formData = new FormData();
		formData.left = new FormAttachment(0, 0);
		formData.right = new FormAttachment(100, 0);
		formData.top = new FormAttachment(child1);
		formData.height = SASH_WIDTH;
		sash.setLayoutData(formData);

		// child2
		formData = new FormData();
		formData.left = new FormAttachment(0, 0);
		formData.right = new FormAttachment(100, 0);
		formData.bottom = new FormAttachment(100, 0);
		formData.top = new FormAttachment(sash);

		child2.setLayoutData(formData);


		// Listeners to size the folder
		sash.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				final boolean FASTDRAG = true;

				if (FASTDRAG && e.detail == SWT.DRAG)
					return;

				child1Data.height = e.y + e.height - SASH_WIDTH;
				form.layout();

				Double l = new Double((double) child1.getBounds().height
						/ form.getBounds().height);
				sash.setData("PCT", l);
				if (e.detail != SWT.DRAG) {
					int i = (int) (l.doubleValue() * 10000);
					COConfigurationManager.setParameter("MyTorrents.SplitAt", i);
				}
			}
		});

		form.addListener(SWT.Resize, new DelayedListenerMultiCombiner() {
			public void handleDelayedEvent(Event e) {
				if (sash.isDisposed()) {
					return;
				}
				Double l = (Double) sash.getData("PCT");
				if (l == null) {
					return;
				}
				int newHeight = (int) (form.getBounds().height * l.doubleValue());
				if (child1Data.height != newHeight || child1Data.bottom != null) {
					child1Data.bottom = null;
					child1Data.height = newHeight;
					form.layout();
				}
			}
		});

		AzureusCoreFactory.addCoreRunningListener(new AzureusCoreRunningListener() {
			public void azureusCoreRunning(final AzureusCore core) {
				Utils.execSWTThread(new AERunnable() {
					public void runSupport() {
						initializeWithCore(core, parent);
					}

				});
			}
		});

	}

	private void initializeWithCore(AzureusCore core, Composite parent) {
		torrentview = createTorrentView(core,
				TableManager.TABLE_MYTORRENTS_INCOMPLETE, false, getIncompleteColumns(),
				child1);

		seedingview = createTorrentView(core,
				TableManager.TABLE_MYTORRENTS_COMPLETE, true, getCompleteColumns(),
				child2);


		torrentview.getComposite().addListener(SWT.FocusIn, new Listener() {
			public void handleEvent(Event event) {
				seedingview.getTableView().getTabsCommon().setTvOverride(torrentview.getTableView());
			}
		});

		seedingview.getComposite().addListener(SWT.FocusIn, new Listener() {
			public void handleEvent(Event event) {
				seedingview.getTableView().getTabsCommon().setTvOverride(null);
			}
		});


			// delegate selections from the incomplete view to the sub-tabs owned by the seeding view

		SelectedContentManager.addCurrentlySelectedContentListener(
			new SelectedContentListener()
			{
				public void
				currentlySelectedContentChanged(
					ISelectedContent[] 		currentContent,
					String 					viewId )
				{
					if (form.isDisposed() || torrentview == null || seedingview == null) {

						SelectedContentManager.removeCurrentlySelectedContentListener(this);

					} else {

						TableView<?> selected_tv = SelectedContentManager.getCurrentlySelectedTableView();

						TableViewSWT<?> incomp_tv 	= torrentview.getTableView();
			 				TableViewSWT<?> comp_tv 	= seedingview.getTableView();

						if (incomp_tv != null && comp_tv != null && ( selected_tv == incomp_tv || selected_tv == comp_tv)) {

							TableViewSWT_TabsCommon tabs = comp_tv.getTabsCommon();

							if (tabs != null) {

								tabs.triggerTabViewsDataSourceChanged(selected_tv);
							}
						}
					}
				}
			});

		initializeDone();
	}

	public void initializeDone() {
	}


	public void updateLanguage() {
		// no super call, the views will do their own

		if (getComposite() == null || getComposite().isDisposed())
			return;

		if (seedingview != null) {
			seedingview.updateLanguage();
		}
		if (torrentview != null) {
			torrentview.updateLanguage();
		}
	}

	public String getFullTitle() {
		return MessageText.getString("MyTorrentsView.mytorrents");
	}

	// XXX: Is there an easier way to find out what has the focus?
	private MyTorrentsView getCurrentView() {
		// wrap in a try, since the controls may be disposed
		try {
			if (torrentview != null && torrentview.isTableFocus()) {
				lastSelectedView = torrentview;
			} else if (seedingview != null && seedingview.isTableFocus()) {
				lastSelectedView = seedingview;
			}
		} catch (Exception ignore) {/*ignore*/}

		return lastSelectedView;
	}

	private UIPluginViewToolBarListener getActiveToolbarListener() {
		MyTorrentsView[] viewsToCheck = {
			getCurrentView(),
			torrentview,
			seedingview
		};
		for (int i = 0; i < viewsToCheck.length; i++) {
			MyTorrentsView view = viewsToCheck[i];
			if (view != null) {
				MdiEntrySWT activeSubView = view.getTableView().getTabsCommon().getActiveSubView();
				if (activeSubView != null) {
					UIPluginViewToolBarListener toolBarListener = activeSubView.getToolBarListener();
					if (toolBarListener != null) {
						return toolBarListener;
					}
				}
				if (i == 0 && view.isTableFocus()) {
					return view;
				}
			}
		}

		return null;
	}

	/* (non-Javadoc)
	 * @see com.aelitis.azureus.ui.common.ToolBarEnabler2#refreshToolBarItems(java.util.Map)
	 */
	public void refreshToolBarItems(Map<String, Long> list) {
		UIPluginViewToolBarListener currentView = getActiveToolbarListener();
		if (currentView != null) {
			currentView.refreshToolBarItems(list);
		}
	}

	/* (non-Javadoc)
	 * @see com.aelitis.azureus.ui.common.ToolBarActivation#toolBarItemActivated(com.aelitis.azureus.ui.common.ToolBarItem, long)
	 */
	public boolean toolBarItemActivated(ToolBarItem item, long activationType, Object datasource) {
		UIPluginViewToolBarListener currentView = getActiveToolbarListener();
		if (currentView != null) {
			if (currentView.toolBarItemActivated(item, activationType, datasource)) {
				return true;
			}
		}
		MyTorrentsView currentView2 = getCurrentView();
		if (currentView2 != currentView && currentView2 != null) {
			if (currentView2.toolBarItemActivated(item, activationType, datasource)) {
				return true;
			}
		}
		return false;
	}

	public DownloadManager[] getSelectedDownloads() {
		MyTorrentsView currentView = getCurrentView();
		if (currentView == null) {return null;}
		return currentView.getSelectedDownloads();
	}

	public void
	generate(
	IndentWriter	writer )
	{

		try {
			writer.indent();

			writer.println("Downloading");

			writer.indent();

			torrentview.generate(writer);

		} finally {

			writer.exdent();

			writer.exdent();
		}

		try {
			writer.indent();

			writer.println("Seeding");

			writer.indent();

			seedingview.generate(writer);

		} finally {

			writer.exdent();

			writer.exdent();
		}
	}

	private Image obfusticatedImage(Image image) {
		if (torrentview != null) {
			torrentview.obfusticatedImage(image);
		}
		if (seedingview != null) {
			seedingview.obfusticatedImage(image);
		}
		return image;
	}

	public Menu getPrivateMenu() {
		return null;
	}

	public void viewActivated() {
		SelectedContentManager.clearCurrentlySelectedContent();

		if (viewWhenDeactivated != null) {
			viewWhenDeactivated.getComposite().setFocus();
			viewWhenDeactivated.updateSelectedContent(true);
		} else {
			MyTorrentsView currentView = getCurrentView();
			if (currentView != null) {
				currentView.updateSelectedContent();
			}
		}
	}

	public void viewDeactivated() {
		viewWhenDeactivated = getCurrentView();
		/*
		MyTorrentsView currentView = getCurrentView();
		if (currentView == null) {return;}
		String ID = currentView.getShortTitle();
		if (currentView instanceof MyTorrentsView) {
			ID = ((MyTorrentsView)currentView).getTableView().getTableID();
		}

		TableView tv = null;
		if (currentView instanceof MyTorrentsView) {
			tv = ((MyTorrentsView) currentView).getTableView();
		}
		//SelectedContentManager.clearCurrentlySelectedContent();
		SelectedContentManager.changeCurrentlySelectedContent(ID, null, tv);
		*/
	}

	/**
	 * Returns the set of columns for the incomplete torrents view
	 * Subclasses my override to return a different set of columns
	 * @return
	 */
	protected TableColumnCore[] getIncompleteColumns() {
		return TableColumnCreator.createIncompleteDM(TableManager.TABLE_MYTORRENTS_INCOMPLETE);
	}

	/**
	 * Returns the set of columns for the completed torrents view
	 * Subclasses my override to return a different set of columns
	 * @return
	 */
	protected TableColumnCore[] getCompleteColumns() {
		return TableColumnCreator.createCompleteDM(TableManager.TABLE_MYTORRENTS_COMPLETE);
	}


	/**
	 * Returns an instance of <code>MyTorrentsView</code>
	 * Subclasses my override to return a different instance of MyTorrentsView
	 * @param _azureus_core
	 * @param isSeedingView
	 * @param columns
	 * @param child1
	 * @return
	 */
	protected MyTorrentsView
	createTorrentView(
		AzureusCore 		_azureus_core,
		String 				tableID,
		boolean 			isSeedingView,
		TableColumnCore[] 	columns,
		Composite 			c ) {
		MyTorrentsView view = new MyTorrentsView(_azureus_core, tableID,
				isSeedingView, columns, txtFilter, cCats, isSeedingView);

		try {
			UISWTViewImpl swtView = new UISWTViewImpl(tableID, UISWTInstance.VIEW_MAIN, false);
			swtView.setDatasource(ds);
			swtView.setEventListener(view, true);
			swtView.setDelayInitializeToFirstActivate(false);

			swtView.initialize(c);
		} catch (Exception e) {
			Debug.out(e);
		}

		/*
		c.addListener(SWT.Activate, new Listener() {
			public void handleEvent(Event event) {
				viewActivated();
			}
		});
		c.addListener(SWT.Deactivate, new Listener() {
			public void handleEvent(Event event) {
				viewDeactivated();
			}
		});
		*/
		c.layout();
		return view;
	}


	public MyTorrentsView getTorrentview() {
		return torrentview;
	}


	public MyTorrentsView getSeedingview() {
		return seedingview;
	}

	public void dataSourceChanged(Object newDataSource) {
		ds = newDataSource;
	}

	public boolean eventOccurred(UISWTViewEvent event) {
		switch (event.getType()) {
			case UISWTViewEvent.TYPE_CREATE:
				swtView = (UISWTView) event.getData();
				swtView.setToolBarListener(this);
				swtView.setTitle(getFullTitle());
				break;

			case UISWTViewEvent.TYPE_DESTROY:
				break;

			case UISWTViewEvent.TYPE_INITIALIZE:
				initialize((Composite) event.getData());
				return true;

			case UISWTViewEvent.TYPE_LANGUAGEUPDATE:
				swtView.setTitle(getFullTitle());
				Messages.updateLanguageForControl(getComposite());
				break;

			case UISWTViewEvent.TYPE_DATASOURCE_CHANGED:
				dataSourceChanged(event.getData());
				break;

			case UISWTViewEvent.TYPE_REFRESH:
				break;

			case UISWTViewEvent.TYPE_OBFUSCATE:
				Object data = event.getData();
				if (data instanceof Map) {
					obfusticatedImage((Image) MapUtils.getMapObject((Map) data, "image",
							null, Image.class));
				}
				break;
		}

		if (seedingview != null) {
			try {
				seedingview.getSWTView().triggerEvent(event.getType(), event.getData());
			} catch (Exception e) {
				Debug.out(e);
			}
		}

		if (torrentview != null) {
			try {
				torrentview.getSWTView().triggerEvent(event.getType(), event.getData());
			} catch (Exception e) {
				Debug.out(e);
			}
		}

		// both subviews will get focusgained, resulting in the last one grabbing
		// "focus".	We restore last used focus, but only after the subviews are
		// done being greedy
		switch (event.getType()) {
			case UISWTViewEvent.TYPE_FOCUSGAINED:
				viewActivated();
				break;

			case UISWTViewEvent.TYPE_FOCUSLOST:
				viewDeactivated();
				break;
		}

		return true;
	}

	public UISWTView getSWTView() {
		return swtView;
	}
}