/*
 * Created on Dec 7, 2016
 * Created by Paul Gardner
 *
 * Copyright 2016 Azureus Software, Inc.  All rights reserved.
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
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 */


package com.aelitis.azureus.ui.swt.search;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Text;
import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.util.AENetworkClassifier;
import org.gudy.azureus2.core3.util.AERunnable;
import org.gudy.azureus2.core3.util.AEThread2;
import org.gudy.azureus2.core3.util.Base32;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.DisplayFormatters;
import org.gudy.azureus2.core3.util.FrequencyLimitedDispatcher;
import org.gudy.azureus2.core3.util.SystemTime;
import org.gudy.azureus2.core3.util.UrlUtils;
import org.gudy.azureus2.plugins.ui.tables.TableColumn;
import org.gudy.azureus2.plugins.ui.tables.TableColumnCreationListener;
import org.gudy.azureus2.ui.swt.Messages;
import org.gudy.azureus2.ui.swt.Utils;
import org.gudy.azureus2.ui.swt.mainwindow.ClipboardCopy;
import org.gudy.azureus2.ui.swt.mainwindow.Colors;
import org.gudy.azureus2.ui.swt.views.table.TableViewSWT;
import org.gudy.azureus2.ui.swt.views.table.TableViewSWTMenuFillListener;
import org.gudy.azureus2.ui.swt.views.table.impl.TableViewFactory;

import com.aelitis.azureus.core.AzureusCore;
import com.aelitis.azureus.core.AzureusCoreFactory;
import com.aelitis.azureus.core.AzureusCoreRunningListener;
import com.aelitis.azureus.core.metasearch.Engine;
import com.aelitis.azureus.core.metasearch.MetaSearchListener;
import com.aelitis.azureus.core.metasearch.MetaSearchManager;
import com.aelitis.azureus.core.metasearch.MetaSearchManagerFactory;
import com.aelitis.azureus.core.metasearch.Result;
import com.aelitis.azureus.core.metasearch.ResultListener;
import com.aelitis.azureus.core.metasearch.SearchParameter;
import com.aelitis.azureus.core.util.CopyOnWriteSet;
import com.aelitis.azureus.core.util.GeneralUtils;
import com.aelitis.azureus.ui.UIFunctions;
import com.aelitis.azureus.ui.UIFunctionsManager;
import com.aelitis.azureus.ui.common.table.TableColumnCore;
import com.aelitis.azureus.ui.common.table.TableLifeCycleListener;
import com.aelitis.azureus.ui.common.table.TableRowCore;
import com.aelitis.azureus.ui.common.table.TableSelectionListener;
import com.aelitis.azureus.ui.common.table.TableViewFilterCheck;
import com.aelitis.azureus.ui.common.table.impl.TableColumnManager;
import com.aelitis.azureus.ui.selectedcontent.DownloadUrlInfo;
import com.aelitis.azureus.ui.selectedcontent.ISelectedContent;
import com.aelitis.azureus.ui.selectedcontent.SelectedContent;
import com.aelitis.azureus.ui.selectedcontent.SelectedContentManager;
import com.aelitis.azureus.ui.swt.columns.search.ColumnSearchResultSite;
import com.aelitis.azureus.ui.swt.columns.searchsubs.*;
import com.aelitis.azureus.ui.swt.imageloader.ImageLoader;
import com.aelitis.azureus.ui.swt.imageloader.ImageLoader.ImageDownloaderListener;
import com.aelitis.azureus.ui.swt.search.SearchResultsTabArea.SearchQuery;
import com.aelitis.azureus.ui.swt.skin.SWTSkinObject;
import com.aelitis.azureus.ui.swt.skin.SWTSkinObjectContainer;
import com.aelitis.azureus.ui.swt.skin.SWTSkinObjectText;
import com.aelitis.azureus.ui.swt.skin.SWTSkinObjectTextbox;
import com.aelitis.azureus.ui.swt.skin.SWTSkinObjectToggle;
import com.aelitis.azureus.ui.swt.skin.SWTSkinToggleListener;
import com.aelitis.azureus.ui.swt.utils.SearchSubsUtils;

public class
SBC_SearchResultsView
	implements SearchResultsTabAreaBase, TableViewFilterCheck<SBC_SearchResult>, MetaSearchListener
{
	public static final String TABLE_SR = "SearchResults";

	private static boolean columnsAdded = false;

	private static Image[]	vitality_images;
	private static Image	ok_image;
	private static Image	fail_image;
	private static Image	auth_image;

	private SearchResultsTabArea		parent;

	private TableViewSWT<SBC_SearchResult> tv_subs_results;

	private Composite			table_parent;


	private Text txtFilter;

	private final Object filter_lock = new Object();

	private Spinner spinMinSize;
	private Spinner spinMaxSize;
	private Text textWithKW;
	private Text textWithoutKW;

	private int minSize;
	private int maxSize;

	private String[]	with_keywords 		= {};
	private String[]	without_keywords 	= {};

	private FrequencyLimitedDispatcher	refilter_dispatcher =
			new FrequencyLimitedDispatcher(
				new AERunnable() {

					@Override
					public void runSupport() {
						refilter();
					}
				}, 250);

	private final CopyOnWriteSet<String>	deselected_engines = new CopyOnWriteSet<String>(false);

	private Composite engine_area;

	private List<SBC_SearchResult>	last_selected_content = new ArrayList<SBC_SearchResult>();

	private Object 			search_lock	= new Object();
	private SearchInstance	current_search;

	protected
	SBC_SearchResultsView(
		SearchResultsTabArea		_parent) {
		parent	= _parent;
	}

	private SWTSkinObject
	getSkinObject(String viewID) {
		return ( parent.getSkinObject(viewID));
	}

	public Object
	skinObjectInitialShow(
		SWTSkinObject skinObject, Object params) {
		AzureusCoreFactory.addCoreRunningListener(
			new AzureusCoreRunningListener() {
				public void azureusCoreRunning(
					AzureusCore core) {
					initColumns(core);
				}
			});

		SWTSkinObjectTextbox soFilterBox = (SWTSkinObjectTextbox) getSkinObject("filterbox");
		if (soFilterBox != null) {
			txtFilter = soFilterBox.getTextControl();
		}

		if (vitality_images == null) {

			ImageLoader loader = ImageLoader.getInstance();

			vitality_images = loader.getImages("image.sidebar.vitality.dots");

			ok_image 	= loader.getImage("tick_mark");
			fail_image 	= loader.getImage("progress_cancel");
			auth_image 	= loader.getImage("image.sidebar.vitality.auth");
		}

		final SWTSkinObject soFilterArea = getSkinObject("filterarea");
		if (soFilterArea != null) {

			SWTSkinObjectToggle soFilterButton = (SWTSkinObjectToggle) getSkinObject("filter-button");
			if (soFilterButton != null) {

				boolean toggled = COConfigurationManager.getBooleanParameter("Search View Filter Options Expanded", false);

				if (toggled) {

					soFilterButton.setToggled(true);

					soFilterArea.setVisible(true);
				}

				soFilterButton.addSelectionListener(new SWTSkinToggleListener() {
					public void toggleChanged(SWTSkinObjectToggle so, boolean toggled) {

						COConfigurationManager.setParameter("Search View Filter Options Expanded", toggled);

						soFilterArea.setVisible(toggled);
						Utils.relayout(soFilterArea.getControl().getParent());
					}
				});
			}

			Composite parent = (Composite) soFilterArea.getControl();

			Composite filter_area = new Composite(parent, SWT.NONE);
			FormData fd = Utils.getFilledFormData();
			filter_area.setLayoutData(fd);

			GridLayout layout = new GridLayout();
			layout.marginBottom = layout.marginTop = layout.marginLeft = layout.marginRight = 0;
			filter_area.setLayout(layout);

			Label label;
			int sepHeight = 20;

			Composite cRow = new Composite(filter_area, SWT.NONE);
			cRow.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

			RowLayout rowLayout = new RowLayout(SWT.HORIZONTAL);
			rowLayout.spacing = 5;
			rowLayout.marginBottom = rowLayout.marginTop = rowLayout.marginLeft = rowLayout.marginRight = 0;
			rowLayout.center = true;
			cRow.setLayout(rowLayout);


				// with/without keywords

			ImageLoader imageLoader = ImageLoader.getInstance();

			for ( int i=0;i<2;i++) {

				final boolean with = i == 0;

				if (!with) {

					label = new Label(cRow, SWT.VERTICAL | SWT.SEPARATOR);
					label.setLayoutData(new RowData(-1, sepHeight));
				}

				Composite cWithKW = new Composite(cRow, SWT.NONE);
				layout = new GridLayout(2, false);
				layout.marginWidth = 0;
				layout.marginBottom = layout.marginTop = layout.marginLeft = layout.marginRight = 0;
				cWithKW.setLayout(layout);
				//Label lblWithKW = new Label(cWithKW, SWT.NONE);
				//lblWithKW.setText(MessageText.getString(with?"SubscriptionResults.filter.with.words":"SubscriptionResults.filter.without.words"));
				Label lblWithKWImg = new Label(cWithKW, SWT.NONE);
				lblWithKWImg.setImage( imageLoader.getImage( with?"icon_filter_plus":"icon_filter_minus"));

				final Text textWidget = new Text(cWithKW, SWT.BORDER);
				if (with) {
					textWithKW = textWidget;
				} else {
					textWithoutKW = textWidget;
				}
				textWidget.setMessage(MessageText.getString(with?"SubscriptionResults.filter.with.words":"SubscriptionResults.filter.without.words"));
				GridData gd = new GridData();
				gd.widthHint = Utils.adjustPXForDPI(100);
				textWidget.setLayoutData(gd);
				textWidget.addModifyListener(
					new ModifyListener() {

						public void modifyText(ModifyEvent e) {
							String text = textWidget.getText().toLowerCase(Locale.US);
							String[] bits = text.split("\\s+");

							Set<String>	temp = new HashSet<String>();

							for (String bit: bits) {

								bit = bit.trim();
								if (bit.length() > 0) {
									temp.add(bit);
								}
							}

							String[] words = temp.toArray(new String[temp.size()]);
							synchronized(filter_lock) {
								if (with) {
									with_keywords = words;
								} else {
									without_keywords = words;
								}
							}
							refilter_dispatcher.dispatch();
						}
					});
			}


				// min size

			label = new Label(cRow, SWT.VERTICAL | SWT.SEPARATOR);
			label.setLayoutData(new RowData(-1, sepHeight));

			Composite cMinSize = new Composite(cRow, SWT.NONE);
			layout = new GridLayout(2, false);
			layout.marginWidth = 0;
			layout.marginBottom = layout.marginTop = layout.marginLeft = layout.marginRight = 0;
			cMinSize.setLayout(layout);
			Label lblMinSize = new Label(cMinSize, SWT.NONE);
			lblMinSize.setText(MessageText.getString("SubscriptionResults.filter.min_size"));
			spinMinSize = new Spinner(cMinSize, SWT.BORDER);
			spinMinSize.setMinimum(0);
			spinMinSize.setMaximum(100*1024*1024);	// 100 TB should do...
			spinMinSize.setSelection(minSize);
			spinMinSize.addListener(SWT.Selection, new Listener() {
				public void handleEvent(Event event) {
					minSize = ((Spinner) event.widget).getSelection();
					refilter();
				}
			});

			// max size

			label = new Label(cRow, SWT.VERTICAL | SWT.SEPARATOR);
			label.setLayoutData(new RowData(-1, sepHeight));

			Composite cMaxSize = new Composite(cRow, SWT.NONE);
			layout = new GridLayout(2, false);
			layout.marginWidth = 0;
			layout.marginBottom = layout.marginTop = layout.marginLeft = layout.marginRight = 0;
			cMaxSize.setLayout(layout);
			Label lblMaxSize = new Label(cMaxSize, SWT.NONE);
			lblMaxSize.setText(MessageText.getString("SubscriptionResults.filter.max_size"));
			spinMaxSize = new Spinner(cMaxSize, SWT.BORDER);
			spinMaxSize.setMinimum(0);
			spinMaxSize.setMaximum(100*1024*1024);	// 100 TB should do...
			spinMaxSize.setSelection(maxSize);
			spinMaxSize.addListener(SWT.Selection, new Listener() {
				public void handleEvent(Event event) {
					maxSize = ((Spinner) event.widget).getSelection();
					refilter();
				}
			});

			engine_area = new Composite(filter_area, SWT.NONE);
			engine_area.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

			buildEngineArea(null);

			parent.layout(true);
		}

		return null;
	}

	private void
	buildEngineArea(
		final SearchInstance		search) {
		if (engine_area.isDisposed()) {

			return;
		}

		final Engine[]	engines = search==null?new Engine[0]:search.getEngines();

		Utils.disposeComposite(engine_area, false);

		Arrays.sort(
			engines,
			new Comparator<Engine>() {
				public int compare(Engine o1, Engine o2) {
					return ( o1.getName().compareTo( o2.getName()));
				}
			});

		RowLayout rowLayout = new RowLayout(SWT.HORIZONTAL);
		rowLayout.spacing = 3;
		rowLayout.marginBottom = rowLayout.marginTop = rowLayout.marginLeft = rowLayout.marginRight = 0;
		rowLayout.pack = false;
		engine_area.setLayout(rowLayout);

		final Composite label_comp = new Composite(engine_area, SWT.NULL);

		GridLayout layout = new GridLayout();
		layout.marginBottom = layout.marginTop = layout.marginLeft = layout.marginRight = 1;
		label_comp.setLayout(layout);

		Label label = new Label(label_comp, SWT.NULL);
		Messages.setLanguageText(label, "label.show.results.from");
		GridData grid_data = new GridData(SWT.LEFT, SWT.CENTER, true, true);

		label.setLayoutData(grid_data);

		final List<Button>		buttons 		= new ArrayList<Button>();
		final List<Label>		result_counts	= new ArrayList<Label>();
		final List<ImageLabel>	indicators		= new ArrayList<ImageLabel>();

		label.addMouseListener(
			new MouseAdapter() {

				public void mouseDown(MouseEvent e) {

					deselected_engines.clear();

					for (Button b: buttons) {

						b.setSelection(true);
					}

					refilter();
				}
			});

		for (final Engine engine: engines) {

			final Composite engine_comp = new Composite(engine_area, SWT.NULL);

			layout = new GridLayout(3,false);
			layout.marginBottom = layout.marginTop = layout.marginLeft = layout.marginRight = 1;
			engine_comp.setLayout(layout);

			engine_comp.addPaintListener(
				new PaintListener() {

					public void paintControl(PaintEvent e) {
						GC gc = e.gc;
						gc.setForeground( Colors.grey);

						Point size = engine_comp.getSize();

						gc.drawRectangle(new Rectangle( 0,  0, size.x-1, size.y-1));
					}
				});

			final Button button = new Button(engine_comp, SWT.CHECK);

			button.setData(engine);

			buttons.add(button);

			button.setText( engine.getName());

			button.setSelection( !deselected_engines.contains( engine.getUID()));

			Image image =
				getIcon(
					engine,
					new ImageLoadListener() {

						public void imageLoaded(Image image) {
							button.setImage(image);
						}
					});

			if (image != null) {

				button.setImage(image);
			}

			button.addSelectionListener(
				new SelectionAdapter() {

					public void widgetSelected(SelectionEvent e) {

						String id = engine.getUID();

						if (button.getSelection()) {

							deselected_engines.remove(id);

						} else {

							deselected_engines.add(id);
						}

						refilter();
					}
				});

			Menu menu = new Menu(button);

			button.setMenu(menu);

			MenuItem mi = new MenuItem(menu, SWT.PUSH);

			mi.setText(MessageText.getString("label.this.site.only"));

			mi.addSelectionListener(
				new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {

						deselected_engines.clear();

						button.setSelection(true);

						for (Button b: buttons) {

							if (b != button) {

								b.setSelection(false);

								deselected_engines.add(((Engine)b.getData()).getUID());
							}
						}

						refilter();
					}
				});

			MenuItem miCreateSubscription = new MenuItem(menu, SWT.PUSH);
			Messages.setLanguageText(miCreateSubscription, "menu.search.create.subscription");
			miCreateSubscription.addSelectionListener(new SelectionListener() {

				public void widgetSelected(SelectionEvent e) {
					Map filterMap = buildFilterMap();
					SearchUtils.showCreateSubscriptionDialog(engine.getId(),
							current_search.sq.term, filterMap);
				}

				public void widgetDefaultSelected(SelectionEvent e) {
				}
			});

			SearchUtils.addMenus(menu, engine, true);

			Label results = new Label(engine_comp, SWT.NULL);

			GC temp = new GC(results);
			Point size = temp.textExtent("(888)");
			temp.dispose();

			GridData gd = new GridData();

			gd.widthHint = Utils.adjustPXForDPI(size.x);

			results.setLayoutData(gd);

			result_counts.add(results);

			ImageLabel indicator = new ImageLabel(engine_comp, vitality_images[0]);

			indicators.add(indicator);

			indicator.addMouseListener(
				new MouseAdapter() {

					@Override
					public void mouseDown(
						MouseEvent e) {
						deselected_engines.clear();

						boolean	only_me_selected = button.getSelection();

						if (only_me_selected) {

							for (Button b: buttons) {

								if (b != button) {

									if (b.getSelection()) {

										only_me_selected = false;
									}
								}
							}
						}

						if (only_me_selected) {

							button.setSelection(false);

							deselected_engines.add( engine.getUID());

							for (Button b: buttons) {

								if (b != button) {

									b.setSelection(true);
								}
							}
						} else {

							button.setSelection(true);

							for (Button b: buttons) {

								if (b != button) {

									b.setSelection(false);

									deselected_engines.add(((Engine)b.getData()).getUID());
								}
							}
						}

						refilter();
					}
				});
		}

		Composite cAddEdit = new Composite(engine_area, SWT.NONE);
		cAddEdit.setLayout(new GridLayout());
		Button btnAddEdit = new Button(cAddEdit, SWT.PUSH);
		btnAddEdit.setLayoutData(new GridData(SWT.CENTER,0, true, true));
		Messages.setLanguageText(btnAddEdit, "button.add.edit.search.templates");
		btnAddEdit.addSelectionListener(new SelectionListener() {
			public void widgetSelected(SelectionEvent e) {
				UIFunctions functions = UIFunctionsManager.getUIFunctions();
				if (functions != null) {
					functions.viewURL("/xsearch/addedit.php", null, "");
				}
			}

			public void widgetDefaultSelected(SelectionEvent e) {
			}
		});

		Composite cCreateTemplate = new Composite(engine_area, SWT.NONE);
		cCreateTemplate.setLayout(new GridLayout());
		Button btnCreateTemplate = new Button(cCreateTemplate, SWT.PUSH);
		btnCreateTemplate.setLayoutData(new GridData(SWT.CENTER,0, true, true));
		Messages.setLanguageText(btnCreateTemplate, "menu.search.create.subscription");
		btnCreateTemplate.addSelectionListener(new SelectionListener() {

			public void widgetSelected(SelectionEvent e) {
				Map filterMap = buildFilterMap();
				SearchUtils.showCreateSubscriptionDialog(-1,
						current_search.sq.term, buildFilterMap());
			}

			public void widgetDefaultSelected(SelectionEvent e) {
			}
		});



		if (engines.length > 0) {

			new AEThread2("updater") {

				int	ticks;
				int	image_index = 0;

				volatile boolean	running = true;


				@Override
				public void run() {

					while (running) {

						if (label_comp.isDisposed()) {

							return;
						}

						try {
							Thread.sleep(100);

						} catch (Throwable e) {
						}

						Utils.execSWTThread(
							new Runnable() {

								public void run() {
									if (label_comp.isDisposed()) {

										return;
									}

									ticks++;

									image_index++;

									if (image_index == vitality_images.length) {

										image_index = 0;
									}

									boolean	do_results = ticks%5 == 0;

									boolean all_done = do_results;

									for (int i=0;i<engines.length; i++) {

										Object[] status = search.getEngineStatus(engines[i]);

										int state = (Integer)status[0];

										ImageLabel indicator = indicators.get(i);

										if (state == 0) {

											indicator.setImage( vitality_images[ image_index ]);

										} else if (state == 1) {

											indicator.setImage(ok_image);

										} else if (state == 2) {

											indicator.setImage(fail_image);

											String msg = (String)status[2];

											if (msg != null) {

												indicator.setToolTipText(msg);
											}
										} else {

											indicator.setImage(auth_image);
										}

										if (do_results) {

											if (state == 0) {

												all_done = false;
											}

											String str = "(" + status[1] + ")";

											Label rc = result_counts.get(i);

											if (!str.equals( rc.getText())) {

												rc.setText(str);
											}
										}
									}

									if (all_done) {

										running = false;
									}
								}
							});
					}
				}
			}.start();
		}
		engine_area.layout(true);
	}

	protected Map buildFilterMap() {
		Map<String, Object> mapFilter = new HashMap<String, Object>();
		if (without_keywords != null && without_keywords.length > 0) {
			mapFilter.put("text_filter_out", GeneralUtils.stringJoin(Arrays.asList(without_keywords), " "));
		}
		if (with_keywords != null && with_keywords.length > 0) {
			mapFilter.put("text_filter", GeneralUtils.stringJoin(Arrays.asList(with_keywords), " "));
		}
		if (maxSize > 0) {
			mapFilter.put("max_size", maxSize * 1024 * 1024L);
		}
		if (minSize > 0) {
			mapFilter.put("min_size", minSize * 1024 * 1024L);
		}
		//mapFilter.put("category", "");
		return mapFilter;
	}

	private void
	resetFilters() {
		synchronized(filter_lock) {

			minSize		= 0;
			maxSize		= 0;

			with_keywords 		= new String[0];
			without_keywords 	= new String[0];

			deselected_engines.clear();
		}

		Utils.execSWTThread(
			new Runnable() {
				public void run() {
					if (spinMinSize != null && !spinMinSize.isDisposed()) {
						spinMinSize.setSelection(0);
					}
					if (spinMaxSize != null && !spinMaxSize.isDisposed()) {
						spinMaxSize.setSelection(0);
					}
					if (textWithKW != null && !textWithKW.isDisposed()) {
						textWithKW.setText("");
					}
					if (textWithoutKW != null && !textWithoutKW.isDisposed()) {
						textWithoutKW.setText("");
					}

					if (tv_subs_results != null) {
						tv_subs_results.setFilterText("");
					}
				}
			});
	}

	private void
	setSearchEngines(
		final SearchInstance		si) {
		Utils.execSWTThread(
			new Runnable() {
				public void run() {
					buildEngineArea(si);
				}
			});
	}

	private boolean
	isOurContent(
		SBC_SearchResult result) {
		long	size = result.getSize();

		long kInB = DisplayFormatters.getKinB();
		long mInB = kInB*kInB;

		boolean size_ok =

			(size==-1||(size >= mInB*minSize)) &&
			(size==-1||(maxSize ==0 || size <= mInB*maxSize));

		if (!size_ok) {

			return (false);
		}

		if (with_keywords.length > 0 || without_keywords.length > 0) {

			synchronized(filter_lock) {

				String name = result.getName().toLowerCase(Locale.US);

				for ( int i=0;i<with_keywords.length;i++) {

					if (!name.contains( with_keywords[i])) {

						return (false);
					}
				}

				for ( int i=0;i<without_keywords.length;i++) {

					if (name.contains( without_keywords[i])) {

						return (false);
					}
				}
			}
		}

		String engine_id = result.getEngine().getUID();

		if (deselected_engines.contains( engine_id)) {

			return (false);
		}

		return (true);
	}


	protected void refilter() {
		if (tv_subs_results != null) {
			tv_subs_results.refilter();
		}
	}


	private void
	initColumns(
		AzureusCore core) {
		synchronized(SBC_SearchResultsView.class) {

			if (columnsAdded) {

				return;
			}

			columnsAdded = true;
		}

		TableColumnManager tableManager = TableColumnManager.getInstance();

		tableManager.registerColumn(
			SBC_SearchResult.class,
			ColumnSearchSubResultType.COLUMN_ID,
				new TableColumnCreationListener() {

					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultType(column);
					}
				});
		tableManager.registerColumn(
			SBC_SearchResult.class,
			ColumnSearchSubResultName.COLUMN_ID,
				new TableColumnCreationListener() {

					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultName(column);
					}
				});

		tableManager.registerColumn(
			SBC_SearchResult.class,
			ColumnSearchSubResultActions.COLUMN_ID,
				new TableColumnCreationListener() {

					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultActions(column);
					}
				});

		tableManager.registerColumn(
			SBC_SearchResult.class,
			ColumnSearchSubResultSize.COLUMN_ID,
				new TableColumnCreationListener() {

					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultSize(column);
					}
				});

		tableManager.registerColumn(
			SBC_SearchResult.class,
			ColumnSearchSubResultSeedsPeers.COLUMN_ID,
				new TableColumnCreationListener() {

					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultSeedsPeers(column);
					}
				});

		tableManager.registerColumn(
			SBC_SearchResult.class,
			ColumnSearchSubResultRatings.COLUMN_ID,
				new TableColumnCreationListener() {

					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultRatings(column);
					}
				});

		tableManager.registerColumn(
			SBC_SearchResult.class,
			ColumnSearchSubResultAge.COLUMN_ID,
				new TableColumnCreationListener() {

					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultAge(column);
					}
				});

		tableManager.registerColumn(
			SBC_SearchResult.class,
			ColumnSearchSubResultRank.COLUMN_ID,
				new TableColumnCreationListener() {

					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultRank(column);
					}
				});

		tableManager.registerColumn(
			SBC_SearchResult.class,
			ColumnSearchSubResultCategory.COLUMN_ID,
				new TableColumnCreationListener() {

					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultCategory(column);
					}
				});

		tableManager.registerColumn(
			SBC_SearchResult.class,
			ColumnSearchResultSite.COLUMN_ID,
				new TableColumnCreationListener() {

					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchResultSite(column);
					}
				});

		tableManager.registerColumn(
			SBC_SearchResult.class,
			ColumnSearchSubResultHash.COLUMN_ID,
				new TableColumnCreationListener() {

					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultHash(column);
					}
				});

		tableManager.registerColumn(
			SBC_SearchResult.class,
			ColumnSearchSubResultExisting.COLUMN_ID,
				new TableColumnCreationListener() {

					public void tableColumnCreated(TableColumn column) {
						new ColumnSearchSubResultExisting(column);
					}
				});
	}

	public void showView() {
		SWTSkinObject so_list = getSkinObject("search-results-list");

		if (so_list != null) {

			MetaSearchManagerFactory.getSingleton().getMetaSearch().addListener(this);

			so_list.setVisible(true);

			initTable((Composite) so_list.getControl());
		}
	}

	public void refreshView() {
		if (tv_subs_results != null) {

			tv_subs_results.refreshTable(false);
		}
	}

	public void hideView() {
		synchronized(search_lock) {

			if (current_search != null) {

				current_search.cancel();

				current_search = null;
			}
		}

		MetaSearchManagerFactory.getSingleton().getMetaSearch().removeListener(this);

		Utils.disposeSWTObjects(new Object[] {
			table_parent,
		});
	}

	public void engineAdded(
		Engine		engine) {
		if (engine.isActive()) {

			autoSearchAgain();
		}
	}

	public void engineUpdated(
		Engine		engine) {
	}

	public void engineRemoved(
		Engine		engine) {
		SearchInstance si = current_search;

		if (si != null) {

			if (si.getEngineIndex(engine) >= 0) {

				autoSearchAgain();
			}
		}
	}

	public void engineStateChanged(
		Engine 		engine) {
		SearchInstance si = current_search;

		if (si != null) {

			if (si.getEngineIndex(engine) >= 0) {

				autoSearchAgain();
			}
		}
	}

	private void
	initTable(
		Composite control) {
		tv_subs_results = TableViewFactory.createTableViewSWT(
				SBC_SearchResult.class,
				TABLE_SR,
				TABLE_SR,
				new TableColumnCore[0],
				ColumnSearchSubResultName.COLUMN_ID,
				SWT.MULTI | SWT.FULL_SELECTION | SWT.VIRTUAL);

		TableColumnManager tableManager = TableColumnManager.getInstance();

		tableManager.setDefaultColumnNames( TABLE_SR,
				new String[] {
				ColumnSearchSubResultType.COLUMN_ID,
				ColumnSearchSubResultName.COLUMN_ID,
				ColumnSearchSubResultActions.COLUMN_ID,
				ColumnSearchSubResultSize.COLUMN_ID,
				ColumnSearchSubResultSeedsPeers.COLUMN_ID,
				ColumnSearchSubResultRatings.COLUMN_ID,
				ColumnSearchSubResultAge.COLUMN_ID,
				ColumnSearchSubResultRank.COLUMN_ID,
				ColumnSearchSubResultCategory.COLUMN_ID,
				ColumnSearchResultSite.COLUMN_ID,
			});

		tableManager.setDefaultSortColumnName(TABLE_SR, ColumnSearchSubResultRank.COLUMN_ID);


		if (txtFilter != null) {
			tv_subs_results.enableFilterCheck(txtFilter, this);
		}

		tv_subs_results.setRowDefaultHeight(COConfigurationManager.getIntParameter("Search Subs Row Height"));

		SWTSkinObject soSizeSlider = getSkinObject("table-size-slider");
		if (soSizeSlider instanceof SWTSkinObjectContainer) {
			SWTSkinObjectContainer so = (SWTSkinObjectContainer) soSizeSlider;
			if (!tv_subs_results.enableSizeSlider(so.getComposite(), 16, 100)) {
				so.setVisible(false);
			}
		}

		table_parent = new Composite(control, SWT.NONE);
		table_parent.setLayoutData(Utils.getFilledFormData());
		GridLayout layout = new GridLayout();
		layout.marginHeight = layout.marginWidth = layout.verticalSpacing = layout.horizontalSpacing = 0;
		table_parent.setLayout(layout);

		tv_subs_results.addSelectionListener(new TableSelectionListener() {

			public void selected(
				TableRowCore[] _rows) {
				updateSelectedContent();
			}

			public void mouseExit(TableRowCore row) {
			}

			public void mouseEnter(TableRowCore row) {
			}

			public void focusChanged(TableRowCore focus) {
				UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
				if (uiFunctions != null) {
					uiFunctions.refreshIconBar();
				}
			}

			public void deselected(TableRowCore[] rows) {
				updateSelectedContent();
			}

			public void defaultSelected(TableRowCore[] rows, int stateMask) {
			}

			private void
			updateSelectedContent() {
				TableRowCore[] rows = tv_subs_results.getSelectedRows();

				ArrayList<ISelectedContent>	valid = new ArrayList<ISelectedContent>();

				last_selected_content.clear();

				for (int i=0;i<rows.length;i++) {

					SBC_SearchResult rc = (SBC_SearchResult)rows[i].getDataSource();

					last_selected_content.add(rc);

					byte[] hash = rc.getHash();

					if (hash != null && hash.length > 0) {

						SelectedContent sc = new SelectedContent(Base32.encode(hash), rc.getName());

						sc.setDownloadInfo(new DownloadUrlInfo(	getDownloadURI(rc)));

						valid.add(sc);
					}
				}

				ISelectedContent[] sels = valid.toArray(new ISelectedContent[valid.size()]);

				SelectedContentManager.changeCurrentlySelectedContent("IconBarEnabler",
						sels, tv_subs_results);

				UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();

				if (uiFunctions != null) {

					uiFunctions.refreshIconBar();
				}
			}
		}, false);

		tv_subs_results.addLifeCycleListener(
			new TableLifeCycleListener() {
				public void tableViewInitialized() {
				}

				public void tableViewDestroyed() {
				}
			});


		tv_subs_results.addMenuFillListener(
			new TableViewSWTMenuFillListener() {
				public void fillMenu(String sColumnName, Menu menu) {
					Object[] _related_content = tv_subs_results.getSelectedDataSources().toArray();

					final SBC_SearchResult[] results = new SBC_SearchResult[_related_content.length];

					System.arraycopy(_related_content, 0, results, 0, results.length);

					MenuItem item = new MenuItem(menu, SWT.PUSH);
					item.setText(MessageText.getString("label.copy.url.to.clip"));
					item.addSelectionListener(new SelectionAdapter() {
						public void widgetSelected(SelectionEvent e) {

							StringBuffer buffer = new StringBuffer(1024);

							for (SBC_SearchResult result: results) {

								if (buffer.length() > 0) {
									buffer.append("\r\n");
								}

								buffer.append(getDownloadURI( result));
							}
							ClipboardCopy.copyToClipBoard( buffer.toString());
						};
					});

					item.setEnabled(results.length > 0);

					SearchSubsUtils.addMenu(results, menu);

					new MenuItem(menu, SWT.SEPARATOR);

					if (results.length == 1) {

						if (SearchSubsUtils.addMenu( results[0], menu)) {

							new MenuItem(menu, SWT.SEPARATOR);
						}
					}
				}

				public void addThisColumnSubMenu(String columnName, Menu menuThisColumn) {
				}
			});

		tv_subs_results.initialize(table_parent);

		control.layout(true);
	}

	protected void
	invalidate(
		SBC_SearchResult		result) {
		TableRowCore row = tv_subs_results.getRow(result);

		if (row != null) {

			row.invalidate(true);
		}
	}

	public boolean
	filterCheck(
		SBC_SearchResult 	ds,
		String 				filter,
		boolean 			regex) {
		if (!isOurContent(ds)) {

			return false;
		}

		return (SearchSubsUtils.filterCheck( ds, filter, regex));
	}

	public void filterSet(String filter) {
	}

	private void
	autoSearchAgain() {
		SearchInstance si = current_search;

		if (si != null) {

			anotherSearch( si.getSearchQuery());
		}
	}

	public void anotherSearch(
		SearchQuery	sq) {
		synchronized(search_lock) {

			if (current_search != null) {

				current_search.cancel();
			}

			resetFilters();

			current_search = new SearchInstance(sq);
		}
	}

	public String getDownloadURI(
		SBC_SearchResult	result) {
		String torrent_url = (String)result.getTorrentLink();

		if (torrent_url != null && torrent_url.length() > 0) {

			return (torrent_url);
		}

		String uri = UrlUtils.getMagnetURI( result.getHash(), result.getName(), new String[]{ AENetworkClassifier.AT_PUBLIC });

		return (uri);
	}

	private static ImageLoader	image_loader = new ImageLoader(null, null);

	private static Map<String,Object[]>	image_map = new HashMap<String,Object[]>();

	public Image getIcon(
		final SBC_SearchResult		result) {
		return (getIcon( result.getEngine(), result));
	}

	public Image getIcon(
		Engine					engine,
		ImageLoadListener		result) {
		String icon = engine.getIcon();

		Image img = null;

		if (icon != null) {

			Object[] x = image_map.get(icon);

			if (x == null) {

				Set<ImageLoadListener>	waiters = new HashSet<ImageLoadListener>();

				final Object[] f_x = new Object[]{ null, waiters, SystemTime.getMonotonousTime() };

				waiters.add(result);

				image_map.put(icon, f_x);

				image_loader.getUrlImage(
					icon,
					new Point(0, Utils.adjustPXForDPI(16)),
					new ImageDownloaderListener() {

						public void imageDownloaded(Image image, boolean returnedImmediately) {

							f_x[0]	= image;

							Set<ImageLoadListener> set = (Set<ImageLoadListener>)f_x[1];

							for (ImageLoadListener result: set) {

								result.imageLoaded(image);
							}

							f_x[1] = null;
						}
					});

				img = (Image)f_x[0];	// in case synchronously set

			} else {

				if (x[1] instanceof Set) {

					((Set<ImageLoadListener>)x[1]).add(result);

				} else {

					img = (Image)x[0];

					if (img == null) {

						if (SystemTime.getMonotonousTime() - (Long)x[2] > 120*1000) {

							image_map.remove(icon);
						}
					}
				}
			}
		}

		return (img);
	}

	public int getResultCount() {
		SearchInstance ci = current_search;

		if (ci == null) {

			return (-1);
		}

		return ( current_search.getResultCount());
	}

	public interface ImageLoadListener
	{
		public void
		imageLoaded(
			Image		image);
	}

	private class
	SearchInstance
		implements ResultListener
	{
		private final SearchQuery		sq;
		private final Engine[]			engines;
		private final Object[][]		engine_status;

		private boolean	_cancelled;

		private Set<Engine>	pending = new HashSet<Engine>();

		private AtomicInteger	result_count = new AtomicInteger();

		private
		SearchInstance(
			SearchQuery		_sq) {
			sq		= _sq;

			tv_subs_results.removeAllTableRows();

			SWTSkinObjectText title = (SWTSkinObjectText)parent.getSkinObject("title");

			if (title != null) {

				title.setText( MessageText.getString("search.results.view.title", new String[]{ sq.term }));
			}

			MetaSearchManager metaSearchManager = MetaSearchManagerFactory.getSingleton();

			List<SearchParameter>	sps = new ArrayList<SearchParameter>();

			sps.add(new SearchParameter("s", sq.term));

			SearchParameter[] parameters = sps.toArray(new SearchParameter[ sps.size()]);

			Map<String,String>	context = new HashMap<String, String>();

			context.put(Engine.SC_FORCE_FULL, "true");

			context.put(Engine.SC_BATCH_PERIOD, "250");

			context.put(Engine.SC_REMOVE_DUP_HASH, "true");

			String headers = null;	// use defaults

			parent.setBusy(true);

			synchronized(pending) {

				engines = metaSearchManager.getMetaSearch().search(this, parameters, headers, context, 500);

				engine_status 	= new Object[engines.length][];

				for ( int i=0;i<engine_status.length;i++) {

					engine_status[i] = new Object[]{ 0, 0, null };
				}

				setSearchEngines(this);

				if (engines.length == 0) {

					parent.setBusy(false);

				} else {

					pending.addAll(Arrays.asList( engines));
				}
			}
		}

		protected SearchQuery
		getSearchQuery() {
			return (sq);
		}

		protected Engine[]
		getEngines() {
			synchronized(pending) {

				return (engines);
			}
		}

		protected int
		getEngineIndex(
			Engine	e) {
			synchronized(pending) {

				for (int i=0;i<engines.length;i++) {
					if (engines[i] == e) {
						return (i);
					}
				}
				return (-1);
			}
		}

		protected Object[]
		getEngineStatus(
			Engine		engine) {
			int i = getEngineIndex(engine);

			if (i >= 0) {

				return (engine_status[i]);

			} else {

				return (null);
			}
		}

		protected void
		cancel() {
			_cancelled	= true;

			parent.setBusy(false);
		}

		private boolean
		isCancelled() {
			synchronized(search_lock) {

				return (_cancelled);
			}
		}

		public void contentReceived(
			Engine engine,
			String content) {
		}

		public void matchFound(
			Engine 		engine,
			String[] 	fields) {
		}

		public void engineFailed(
			Engine 		engine,
			Throwable 	e) {
			if (isCancelled()) {

				return;
			}

			engineDone(engine, 2, Debug.getNestedExceptionMessage( e));
		}

		public void engineRequiresLogin(
			Engine 		engine,
			Throwable 	e) {
			if (isCancelled()) {

				return;
			}

			engineDone(engine, 3, null);
		}

		public void resultsComplete(
			Engine engine) {
			if (isCancelled()) {

				return;
			}

			engineDone(engine, 1, null);
		}

		private void
		engineDone(
			Engine		engine,
			int			state,
			String		msg) {
			int	i = getEngineIndex(engine);

			if (i >= 0) {

				engine_status[i][0] = state;
				engine_status[i][2] = msg;
			}

			synchronized(pending) {

				pending.remove(engine);

				if (pending.isEmpty()) {

					parent.setBusy(false);
				}
			}
		}

		public void resultsReceived(
			Engine 		engine,
			Result[] 	results) {
			synchronized(search_lock) {

				if (isCancelled()) {

					return;
				}

				int	index = getEngineIndex(engine);

				if (index >= 0) {

					int count = (Integer)engine_status[index][1];

					engine_status[index][1] = count + results.length;
				}

				SBC_SearchResult[]	data_sources = new  SBC_SearchResult[ results.length ];

				for ( int i=0;i<results.length;i++) {

					data_sources[i] = new SBC_SearchResult(SBC_SearchResultsView.this, engine, results[i]);
				}

				tv_subs_results.addDataSources(data_sources);

				tv_subs_results.processDataSourceQueueSync();

				result_count.addAndGet(results.length);

				parent.resultsFound();
			}
		}

		protected int
		getResultCount() {
			return ( result_count.get());
		}
	}

	static class
	ImageLabel
		extends Canvas implements PaintListener
	{
		private Image		image;

		public
		ImageLabel(
			Composite 	parent,
			Image		_image) {
			super(parent, SWT.DOUBLE_BUFFERED);

			image	= _image;

			addPaintListener(this);
		}

		public void paintControl(
			PaintEvent e) {
			if (!image.isDisposed()) {

				Point size = getSize();

				Rectangle rect = image.getBounds();

				int x_offset = Math.max(0, ( size.x - rect.width )/2);
				int y_offset = Math.max(0, ( size.y - rect.height )/2);

				e.gc.drawImage(image, x_offset, y_offset);
			}
		}


		public Point
		computeSize(
			int 	wHint,
			int 	hHint,
			boolean changed) {
			if (image.isDisposed()) {
				return (new Point(0,0));
			}

			Rectangle rect = image.getBounds();

			return (new Point( rect.width, rect.height));
		}

		private void
		setImage(
			Image	_image) {
			if (_image == image) {

				return;
			}

			image	= _image;

			redraw();
		}
	}
}
