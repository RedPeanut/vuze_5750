/**
 * Created on Jan 3, 2009
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

package org.gudy.azureus2.ui.swt.views.columnsetup;

import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.*;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.util.AERunnable;
import org.gudy.azureus2.core3.util.Constants;
import org.gudy.azureus2.plugins.ui.tables.TableColumn;
import org.gudy.azureus2.plugins.ui.tables.TableColumnInfo;
import org.gudy.azureus2.plugins.ui.tables.TableRow;
import org.gudy.azureus2.ui.swt.Messages;
import org.gudy.azureus2.ui.swt.Utils;
import org.gudy.azureus2.ui.swt.components.BubbleTextBox;
import org.gudy.azureus2.ui.swt.components.shell.ShellFactory;
import org.gudy.azureus2.ui.swt.shells.GCStringPrinter;
import org.gudy.azureus2.ui.swt.views.table.TableRowSWT;
import org.gudy.azureus2.ui.swt.views.table.TableViewSWT;
import org.gudy.azureus2.ui.swt.views.table.impl.TableViewFactory;

import com.aelitis.azureus.core.util.RegExUtil;
import com.aelitis.azureus.ui.common.table.*;
import com.aelitis.azureus.ui.common.table.impl.TableColumnManager;
import com.aelitis.azureus.ui.common.updater.UIUpdatable;
import com.aelitis.azureus.ui.swt.imageloader.ImageLoader;
import com.aelitis.azureus.ui.swt.uiupdater.UIUpdaterSWT;

/**
 * @author TuxPaper
 * @created Jan 3, 2009
 *
 */
public class TableColumnSetupWindow
	implements UIUpdatable
{
	private static final String TABLEID_AVAIL = "ColumnSetupAvail";

	private static final String TABLEID_CHOSEN = "ColumnSetupChosen";

	private static final boolean CAT_BUTTONS = true;

	private Shell shell;

	private TableViewSWT<TableColumn> tvAvail;

	private final String forTableID;

	private final Class<?> forDataSourceType;

	private Composite cTableAvail;

	private Composite cCategories;

	private TableViewSWT tvChosen;

	private Composite cTableChosen;

	private TableColumnCore[] columnsChosen;

	private final TableRow sampleRow;

	private DragSourceListener dragSourceListener;

	private final TableStructureModificationListener<?> listener;

	private TableColumnCore[] columnsOriginalOrder;

	protected boolean apply = false;

	private Button[] radProficiency = new Button[3];

	private Map<TableColumnCore, Boolean> mapNewVisibility = new HashMap<TableColumnCore, Boolean>();

	private ArrayList<TableColumnCore> listColumnsNoCat;

	private ArrayList<String> listCats;

	private Combo comboFilter;

	private Group cPickArea;

	protected boolean doReset;

	public TableColumnSetupWindow(final Class<?> forDataSourceType, String _tableID,
			TableRow sampleRow, TableStructureModificationListener<?> _listener) {
		this.sampleRow = sampleRow;
		this.listener = _listener;
		FormData fd;
		this.forDataSourceType = forDataSourceType;
		forTableID = _tableID;

		dragSourceListener = new DragSourceListener() {
			private TableColumnCore tableColumn;

			public void dragStart(DragSourceEvent event) {
				event.doit = true;

				if (!(event.widget instanceof DragSource)) {
					event.doit = false;
					return;
				}

				TableView<?> tv = (TableView<?>) ((DragSource) event.widget).getData("tv");
				// drag start happens a bit after the mouse moves, so the
				// cursor location isn't accurate
				//Point cursorLocation = event.display.getCursorLocation();
				//cursorLocation = tv.getTableComposite().toControl(cursorLocation);
				//TableRowCore row = tv.getRow(cursorLocation.x, cursorLocation.y);
				//System.out.println("" + event.x + ";" + event.y + "/" + cursorLocation);

				// event.x and y doesn't always return correct values!
				//TableRowCore row = tv.getRow(event.x, event.y);

				TableRowCore row = tv.getFocusedRow();
				if (row == null) {
					event.doit = false;
					return;
				}

				tableColumn = (TableColumnCore) row.getDataSource();


				if (event.image != null && !Constants.isLinux) {
					try {
  					GC gc = new GC(event.image);
  					try {
    					Rectangle bounds = event.image.getBounds();
    					gc.fillRectangle(bounds);
    					String title = MessageText.getString(
    							tableColumn.getTitleLanguageKey(), tableColumn.getName());
    					String s = title
    							+ " Column will be placed at the location you drop it, shifting other columns down";
    					GCStringPrinter sp = new GCStringPrinter(gc, s, bounds, false, false,
    							SWT.CENTER | SWT.WRAP);
    					sp.calculateMetrics();
    					if (sp.isCutoff()) {
    						GCStringPrinter.printString(gc, title, bounds, false, false,
    								SWT.CENTER | SWT.WRAP);
    					} else {
    						sp.printString();
    					}
  					} finally {
  						gc.dispose();
  					}
					} catch (Throwable t) {
						//ignore
					}
				}
			}

			public void dragSetData(DragSourceEvent event) {
				if (!(event.widget instanceof DragSource)) {
					return;
				}

				TableView<?> tv = (TableView<?>) ((DragSource) event.widget).getData("tv");
				event.data = "" + (tv == tvChosen ? "c" : "a");
			}

			public void dragFinished(DragSourceEvent event) {
			}
		};

		String tableName = MessageText.getString(_tableID + "View.header",
				(String) null);
		if (tableName == null) {
			tableName = MessageText.getString(_tableID + "View.title.full",
					(String) null);
			if (tableName == null) {
				tableName = _tableID;
			}
		}


		shell = ShellFactory.createShell(Utils.findAnyShell(), SWT.SHELL_TRIM);
		Utils.setShellIcon(shell);
		FormLayout formLayout = new FormLayout();
    shell.setText(MessageText.getString("ColumnSetup.title", new String[] {
			tableName
		}));
		shell.setLayout(formLayout);
		shell.setSize(780, 550);

		shell.addTraverseListener(new TraverseListener() {
			public void keyTraversed(TraverseEvent e) {
				if (e.detail == SWT.TRAVERSE_ESCAPE) {
					shell.dispose();
				}
			}
		});
		shell.addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent e) {
				close();
			}
		});

		Label topInfo = new Label(shell, SWT.WRAP);
		Messages.setLanguageText(topInfo, "ColumnSetup.explain");

		fd = Utils.getFilledFormData();
		fd.left.offset = 5;
		fd.top.offset = 5;
		fd.bottom = null;
		Utils.setLayoutData(topInfo, fd);

		Button btnOk = new Button(shell, SWT.PUSH);
		Messages.setLanguageText(btnOk, "Button.ok");
		btnOk.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				apply = true;
				shell.dispose();
			}
		});

		cPickArea = new Group(shell, SWT.NONE);
		cPickArea.setLayout(new FormLayout());


		final ExpandBar expandFilters = new ExpandBar(cPickArea, SWT.NONE);
		expandFilters.setSpacing(1);

		final Composite cFilterArea = new Composite(expandFilters, SWT.NONE);
		cFilterArea.setLayout(new FormLayout());

		final TableColumnManager tcm = TableColumnManager.getInstance();

		Group cResultArea = new Group(shell, SWT.NONE);
		Messages.setLanguageText(cResultArea, "ColumnSetup.chosencolumns");
		cResultArea.setLayout(new FormLayout());

		Composite cResultButtonArea = new Composite(cResultArea, SWT.NONE);
		cResultButtonArea.setLayout(new FormLayout());

		tvAvail = createTVAvail();

		cTableAvail = new Composite(cPickArea, SWT.NO_FOCUS);
		GridLayout gridLayout = new GridLayout();
		gridLayout.marginWidth = gridLayout.marginHeight = 0;
		cTableAvail.setLayout(gridLayout);

		BubbleTextBox bubbleTextBox = new BubbleTextBox(cTableAvail, SWT.BORDER | SWT.SEARCH | SWT.ICON_SEARCH | SWT.ICON_CANCEL | SWT.SINGLE);
		bubbleTextBox.getTextWidget().setMessage(MessageText.getString("column.setup.search"));
		GridData gd = new GridData(SWT.RIGHT,SWT.CENTER,true,false);
		bubbleTextBox.getParent().setLayoutData(gd);

		tvAvail.enableFilterCheck(
			bubbleTextBox.getTextWidget(),
			new TableViewFilterCheck<TableColumn>() {
				@Override
				public boolean filterCheck(
					TableColumn 	ds,
					String 			filter,
					boolean 		regex ) {
					TableColumnCore core = (TableColumnCore)ds;

					String raw_key 		= core.getTitleLanguageKey(false);
					String current_key 	= core.getTitleLanguageKey(true);

					String name1 = MessageText.getString(raw_key, core.getName());
					String name2 = null;

					if (!raw_key.equals( current_key)) {
						String rename = MessageText.getString(current_key, "");
						if (rename.length() > 0) {
							name2 = rename;
						}
					}
					String[] names = {
						name1,
						name2,
						MessageText.getString(core.getTitleLanguageKey() + ".info")
					};

					for (String name: names) {

						if (name == null) {

							continue;
						}

						String s = regex ? filter : "\\Q" + filter.replaceAll("[|;]", "\\\\E|\\\\Q") + "\\E";

						boolean	match_result = true;

						if (regex && s.startsWith("!")) {

							s = s.substring(1);

							match_result = false;
						}

						Pattern pattern = RegExUtil.getCachedPattern("tcs:search", s, Pattern.CASE_INSENSITIVE);

						if (pattern.matcher(name).find() == match_result) {

							return (true);
						}

					}

					return (false);
				}

				@Override
				public void filterSet(String filter) {
				}
			});

		tvAvail.initialize(cTableAvail);

		TableColumnCore[] datasources = tcm.getAllTableColumnCoreAsArray(
				forDataSourceType, forTableID);

		listColumnsNoCat = new ArrayList<TableColumnCore>(
				Arrays.asList(datasources));
		listCats = new ArrayList<String>();
		for (int i = 0; i < datasources.length; i++) {
			TableColumnCore column = datasources[i];
			TableColumnInfo info = tcm.getColumnInfo(forDataSourceType, forTableID,
					column.getName());
			if (info != null) {
				String[] categories = info.getCategories();
				if (categories != null && categories.length > 0) {
					for (int j = 0; j < categories.length; j++) {
						String cat = categories[j];
						if (!listCats.contains(cat)) {
							listCats.add(cat);
						}
					}
					listColumnsNoCat.remove(column);
				}
			}
		}

		Listener radListener = new Listener() {
			public void handleEvent(Event event) {
				fillAvail();
			}
		};


		Composite cProficiency = new Composite(cFilterArea, SWT.NONE);
		cProficiency.setBackgroundMode(SWT.INHERIT_FORCE);
		cProficiency.setLayout(new FormLayout());

		Label lblProficiency = new Label(cProficiency, SWT.NONE);
		Messages.setLanguageText(lblProficiency, "ColumnSetup.proficiency");

		radProficiency[0] = new Button(cProficiency, SWT.RADIO);
		Messages.setLanguageText(radProficiency[0], "ConfigView.section.mode.beginner");
		fd = new FormData();
		fd.left = new FormAttachment(lblProficiency, 5);
		radProficiency[0].setLayoutData(fd);
		radProficiency[0].addListener(SWT.Selection, radListener);

		radProficiency[1] = new Button(cProficiency, SWT.RADIO);
		Messages.setLanguageText(radProficiency[1], "ConfigView.section.mode.intermediate");
		fd = new FormData();
		fd.left = new FormAttachment(radProficiency[0], 5);
		radProficiency[1].setLayoutData(fd);
		radProficiency[1].addListener(SWT.Selection, radListener);

		radProficiency[2] = new Button(cProficiency, SWT.RADIO);
		Messages.setLanguageText(radProficiency[2], "ConfigView.section.mode.advanced");
		fd = new FormData();
		fd.left = new FormAttachment(radProficiency[1], 5);
		radProficiency[2].setLayoutData(fd);
		radProficiency[2].addListener(SWT.Selection, radListener);

		int userMode = COConfigurationManager.getIntParameter("User Mode");
		if (userMode < 0) {
			userMode = 0;
		} else if (userMode >= radProficiency.length) {
			userMode = radProficiency.length - 1;
		}
		radProficiency[userMode].setSelection(true);

		// >>>>>>>> Buttons

		Listener buttonListener = new Listener() {
			public void handleEvent(Event event) {
				Control[] children = cCategories.getChildren();
				for (int i = 0; i < children.length; i++) {
					Control child = children[i];
					if (child != event.widget && (child instanceof Button)) {
						Button btn = (Button) child;
						btn.setSelection(false);
					}
				}

				fillAvail();
			}
		};

		Label lblCat = new Label(cFilterArea, SWT.NONE);
		Messages.setLanguageText(lblCat, "ColumnSetup.categories");

		if (CAT_BUTTONS) {
			cCategories = new Composite(cFilterArea, SWT.NONE);
			Utils.setLayout(cCategories, new RowLayout());

  		Button button = new Button(cCategories, SWT.TOGGLE);
  		Messages.setLanguageText(button, "Categories.all");
  		button.addListener(SWT.Selection, buttonListener);
  		button.setSelection(true);

  		for (String cat : listCats) {
  			button = new Button(cCategories, SWT.TOGGLE);
  			button.setData("cat", cat);
  			if (MessageText.keyExists("ColumnCategory." + cat)) {
    			button.setText(MessageText.getString("ColumnCategory." + cat));
  			} else {
    			button.setText(cat);
  			}
  			button.addListener(SWT.Selection, buttonListener);
  		}

  		if (listColumnsNoCat.size() > 0) {
  			button = new Button(cCategories, SWT.TOGGLE);
  			if (MessageText.keyExists("ColumnCategory.uncat")) {
    			button.setText(MessageText.getString("ColumnCategory.uncat"));
  			} else {
    			button.setText("?");
  			}
  			button.setText("?");
  			button.setData("cat", "uncat");
  			button.addListener(SWT.Selection, buttonListener);
  		}
		} else {
			comboFilter = new Combo(cFilterArea, SWT.DROP_DOWN | SWT.READ_ONLY);
			comboFilter.addListener(SWT.Selection, radListener);

			listCats.add(0, "all");
			for (String cat : listCats) {
				comboFilter.add(cat);
			}
			comboFilter.select(0);
		}

		final ExpandItem expandItemFilters = new ExpandItem(expandFilters, SWT.NONE);
		expandItemFilters.setText(MessageText.getString("ColumnSetup.filters"));
		expandItemFilters.setControl(cFilterArea);
		expandFilters.addListener(SWT.Resize, new Listener() {
			public void handleEvent(Event event) {
				expandItemFilters.setHeight(cFilterArea.computeSize(
						expandFilters.getSize().x, SWT.DEFAULT).y + 3);
			}
		});

		expandFilters.addListener(SWT.Expand, new Listener() {
			public void handleEvent(Event event) {
				Utils.execSWTThreadLater(Constants.isLinux ? 250 : 0, new AERunnable() {
					public void runSupport() {
						shell.layout(true, true);
					}
				});
			}
		});
		expandFilters.addListener(SWT.Collapse, new Listener() {
			public void handleEvent(Event event) {
				Utils.execSWTThreadLater(Constants.isLinux ? 250 : 0, new AERunnable() {
					public void runSupport() {
						shell.layout(true, true);
					}
				});
			}
		});


		// <<<<<<< Buttons

		// >>>>>>> Chosen

		ImageLoader imageLoader = ImageLoader.getInstance();

		Button btnLeft = new Button(cResultButtonArea, SWT.PUSH);
		imageLoader.setButtonImage(btnLeft, "alignleft");
		btnLeft.addSelectionListener(new SelectionListener() {
			public void widgetSelected(SelectionEvent e) {
				alignChosen( TableColumnCore.ALIGN_LEAD);
			}

			public void widgetDefaultSelected(SelectionEvent e) {
			}
		});

		Button btnCentre = new Button(cResultButtonArea, SWT.PUSH);
		imageLoader.setButtonImage(btnCentre, "aligncentre");
		btnCentre.addSelectionListener(new SelectionListener() {
			public void widgetSelected(SelectionEvent e) {
				alignChosen(TableColumnCore.ALIGN_CENTER);
			}

			public void widgetDefaultSelected(SelectionEvent e) {
			}
		});

		Button btnRight = new Button(cResultButtonArea, SWT.PUSH);
		imageLoader.setButtonImage(btnRight, "alignright");
		btnRight.addSelectionListener(new SelectionListener() {
			public void widgetSelected(SelectionEvent e) {
				alignChosen(TableColumnCore.ALIGN_TRAIL);
			}

			public void widgetDefaultSelected(SelectionEvent e) {
			}
		});

		Button btnUp = new Button(cResultButtonArea, SWT.PUSH);
		imageLoader.setButtonImage(btnUp, "up");
		btnUp.addSelectionListener(new SelectionListener() {
			public void widgetSelected(SelectionEvent e) {
				moveChosenUp();
			}

			public void widgetDefaultSelected(SelectionEvent e) {
			}
		});

		Button btnDown = new Button(cResultButtonArea, SWT.PUSH);
		imageLoader.setButtonImage(btnDown, "down");
		btnDown.addSelectionListener(new SelectionListener() {
			public void widgetSelected(SelectionEvent e) {
				moveChosenDown();
			}

			public void widgetDefaultSelected(SelectionEvent e) {
			}
		});

		Button btnDel = new Button(cResultButtonArea, SWT.PUSH);
		imageLoader.setButtonImage(btnDel, "delete2");
		btnDel.addSelectionListener(new SelectionListener() {
			public void widgetSelected(SelectionEvent e) {
				removeSelectedChosen();
			}

			public void widgetDefaultSelected(SelectionEvent e) {
			}
		});

		tvChosen = createTVChosen();

		cTableChosen = new Composite(cResultArea, SWT.NONE);
		gridLayout = new GridLayout();
		gridLayout.marginWidth = gridLayout.marginHeight = 0;
		cTableChosen.setLayout(gridLayout);

		tvChosen.initialize(cTableChosen);

		columnsChosen = tcm.getAllTableColumnCoreAsArray(forDataSourceType,
				forTableID);
		Arrays.sort(columnsChosen,
				TableColumnManager.getTableColumnOrderComparator());
		columnsOriginalOrder = new TableColumnCore[columnsChosen.length];
		System.arraycopy(columnsChosen, 0, columnsOriginalOrder, 0,
				columnsChosen.length);
		int pos = 0;
		for (int i = 0; i < columnsChosen.length; i++) {
			boolean visible = columnsChosen[i].isVisible();
			mapNewVisibility.put(columnsChosen[i], Boolean.valueOf(visible));
			if (visible) {
				columnsChosen[i].setPositionNoShift(pos++);
				tvChosen.addDataSource(columnsChosen[i]);
			}
		}
		tvChosen.processDataSourceQueue();


		Button btnReset = null;
		String[] defaultColumnNames = tcm.getDefaultColumnNames(forTableID);
		if (defaultColumnNames != null) {
  		btnReset = new Button(cResultButtonArea, SWT.PUSH);
  		Messages.setLanguageText(btnReset, "Button.reset");
  		btnReset.addSelectionListener(new SelectionAdapter() {
  			public void widgetSelected(SelectionEvent e) {
  				String[] defaultColumnNames = tcm.getDefaultColumnNames(forTableID);
  				if (defaultColumnNames != null) {
  					List<TableColumnCore> defaultColumns = new ArrayList<TableColumnCore>();
  					for (String name : defaultColumnNames) {
  						TableColumnCore column = tcm.getTableColumnCore(forTableID, name);
  						if (column != null) {
  							defaultColumns.add(column);
  						}
						}
  					if (defaultColumns.size() > 0) {
  						for (TableColumnCore tc : mapNewVisibility.keySet()) {
								mapNewVisibility.put(tc, Boolean.FALSE);
							}
  						tvChosen.removeAllTableRows();
  						columnsChosen = defaultColumns.toArray(new TableColumnCore[0]);
  						for (int i = 0; i < columnsChosen.length; i++) {
  							mapNewVisibility.put(columnsChosen[i], Boolean.TRUE);
								columnsChosen[i].setPositionNoShift(i);
								tvChosen.addDataSource(columnsChosen[i]);
  						}
  						doReset = true;
  					}
  				}
  			}
  		});
		}

		final Button btnCancel = new Button(shell, SWT.PUSH);
		Messages.setLanguageText(btnCancel, "Button.cancel");
		btnCancel.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				shell.dispose();
			}
		});

		Button btnApply = new Button(cResultButtonArea, SWT.PUSH);
		Messages.setLanguageText(btnApply, "Button.apply");
		btnApply.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				apply();
				btnCancel.setEnabled(false);
			}
		});

		fd = new FormData();
		fd.left = new FormAttachment(0, 5);
		fd.right = new FormAttachment(100, -5);
		//fd.bottom = new FormAttachment(100, -5);
		//Utils.setLayoutData(lblChosenHeader, fd);

		fd = new FormData();
		fd.top = new FormAttachment(topInfo, 5);
		fd.right = new FormAttachment(100, -3);
		fd.bottom = new FormAttachment(btnOk, -5);
		fd.width = 210;
		Utils.setLayoutData(cResultArea, fd);

		fd = new FormData();
		fd.top = new FormAttachment(0, 3);
		fd.left = new FormAttachment(0, 3);
		fd.right = new FormAttachment(100, -3);
		fd.bottom = new FormAttachment(cResultButtonArea, -3);
		Utils.setLayoutData(cTableChosen, fd);

		fd = new FormData();
		fd.bottom = new FormAttachment(100, 0);
		fd.left = new FormAttachment(cTableChosen, 0, SWT.CENTER);
		Utils.setLayoutData(cResultButtonArea, fd);

			// align

		fd = new FormData();
		fd.top = new FormAttachment(0, 3);
		fd.left = new FormAttachment(0, 3);
		Utils.setLayoutData(btnLeft, fd);

		fd = new FormData();
		fd.left = new FormAttachment(btnLeft, 3);
		fd.top = new FormAttachment(btnLeft, 0, SWT.TOP);
		fd.bottom = new FormAttachment(btnLeft, 0, SWT.BOTTOM);
		Utils.setLayoutData(btnCentre, fd);

		fd = new FormData();
		fd.left = new FormAttachment(btnCentre, 3);
		fd.top = new FormAttachment(btnLeft, 0, SWT.TOP);
		fd.bottom = new FormAttachment(btnLeft, 0, SWT.BOTTOM);
		Utils.setLayoutData(btnRight, fd);

			// move

		fd = new FormData();
		fd.left = new FormAttachment(0, 3);
		fd.top = new FormAttachment(btnLeft, 2);
		Utils.setLayoutData(btnUp, fd);

		fd = new FormData();
		fd.left = new FormAttachment(btnUp, 3);
		fd.top = new FormAttachment(btnUp, 0, SWT.TOP);
		Utils.setLayoutData(btnDown, fd);

		fd = new FormData();
		fd.left = new FormAttachment(btnDown, 3);
		fd.top = new FormAttachment(btnUp, 0, SWT.TOP);
		Utils.setLayoutData(btnDel, fd);

		if (btnReset != null) {

	  		fd = new FormData();
	  		fd.right = new FormAttachment(btnApply, -3);
	  		fd.bottom = new FormAttachment(btnApply, 0, SWT.BOTTOM);
	  		Utils.setLayoutData(btnReset, fd);
		}

		fd = new FormData();
		fd.right = new FormAttachment(100, -3);
		fd.top = new FormAttachment(btnUp, 3, SWT.BOTTOM);
		//fd.width = 64;
		Utils.setLayoutData(btnApply, fd);

		fd = new FormData();
		fd.right = new FormAttachment(100, -8);
		fd.bottom = new FormAttachment(100, -3);
		//fd.width = 65;
		Utils.setLayoutData(btnCancel, fd);

		fd = new FormData();
		fd.right = new FormAttachment(btnCancel, -3);
		fd.bottom = new FormAttachment(btnCancel, 0, SWT.BOTTOM);
		//fd.width = 64;
		Utils.setLayoutData(btnOk, fd);

		// <<<<<<<<< Chosen

		fd = new FormData();
		fd.top = new FormAttachment(topInfo, 5);
		fd.left = new FormAttachment(0, 3);
		fd.right = new FormAttachment(cResultArea, -3);
		fd.bottom = new FormAttachment(100, -3);
		Utils.setLayoutData(cPickArea, fd);

		fd = new FormData();
		fd.bottom = new FormAttachment(100, 0);
		fd.left = new FormAttachment(0, 0);
		fd.right = new FormAttachment(100, 0);
		Utils.setLayoutData(expandFilters, fd);


		if (CAT_BUTTONS) {
			fd = new FormData();
			fd.bottom = new FormAttachment(cCategories, 0, SWT.CENTER);
			fd.left = new FormAttachment(0, 5);
			Utils.setLayoutData(lblCat, fd);

			fd = new FormData();
			//fd.top = new FormAttachment(0, 0);
			fd.bottom = new FormAttachment(radProficiency[0], 0, SWT.CENTER);
			fd.left = new FormAttachment(0, 0);
			Utils.setLayoutData(lblProficiency, fd);

  		fd = new FormData();
  		fd.top = new FormAttachment(cProficiency, 5);
  		fd.left = new FormAttachment(lblCat, 5);
  		fd.right = new FormAttachment(100, 0);
  		Utils.setLayoutData(cCategories, fd);
		} else {
			fd = new FormData();
			fd.top = new FormAttachment(comboFilter, -5);
			fd.right = new FormAttachment(comboFilter, 0, SWT.CENTER);
			Utils.setLayoutData(lblCat, fd);

  		fd = new FormData();
  		fd.top = new FormAttachment(cProficiency, 0, SWT.CENTER);
  		fd.right = new FormAttachment(100, 0);
  		Utils.setLayoutData(comboFilter, fd);
		}

		fd = new FormData();
		fd.top = new FormAttachment(0, 5);
		fd.left = new FormAttachment(0, 5);
		Utils.setLayoutData(cProficiency, fd);

		fd = new FormData();
		fd.top = new FormAttachment(0, 3);
		fd.left = new FormAttachment(0, 3);
		fd.right = new FormAttachment(100, -3);
		fd.bottom = new FormAttachment(expandFilters, -3);
		Utils.setLayoutData(cTableAvail, fd);


		//cTableAvail.setFocus();
		//tvAvail.getTableComposite().setFocus();

		shell.setTabList(new Control[] {
			cPickArea,
			cResultArea,
			btnOk,
			btnCancel,
		});

		cPickArea.setTabList(new Control[] {
			cTableAvail
		});

		fillAvail();

		UIUpdaterSWT.getInstance().addUpdater(this);
	}

	/**
	 *
	 *
	 * @since 4.0.0.5
	 */
	protected void fillAvail() {
		String selectedCat = null;
		if (CAT_BUTTONS) {
  		Control[] children = cCategories.getChildren();
  		for (int i = 0; i < children.length; i++) {
  			Control child = children[i];
  			if (child instanceof Button) {
  				Button btn = (Button) child;
  				if (btn.getSelection()) {
  					selectedCat = (String) btn.getData("cat");
  					break;
  				}
  			}
  		}
		} else {
			selectedCat = comboFilter.getItem(comboFilter.getSelectionIndex());
		}

		if (selectedCat != null && selectedCat.equals("all")) {
			selectedCat = null;
		}


		byte selectedProf = 0;
		for (byte i = 0; i < radProficiency.length; i++) {
			Button btn = radProficiency[i];
			if (btn.getSelection()) {
				selectedProf = i;
				break;
			}
		}

		String s;
		//= "Available " + radProficiency[selectedProf].getText() + " Columns";
		if (selectedCat != null) {
			s = MessageText.getString("ColumnSetup.availcolumns.filteredby", new String[] {
				radProficiency[selectedProf].getText(),
				selectedCat
			});
		} else {
			s = MessageText.getString("ColumnSetup.availcolumns", new String[] {
				radProficiency[selectedProf].getText(),
			});
		}
		cPickArea.setText(s);

		tvAvail.removeAllTableRows();

		final TableColumnManager tcm = TableColumnManager.getInstance();
		TableColumnCore[] datasources = tcm.getAllTableColumnCoreAsArray(
				forDataSourceType, forTableID);

		if (selectedCat == "uncat") {
			datasources = listColumnsNoCat.toArray(new TableColumnCore[listColumnsNoCat.size()]);
		}
		for (int i = 0; i < datasources.length; i++) {
			TableColumnCore column = datasources[i];
			TableColumnInfo info = tcm.getColumnInfo(forDataSourceType,
					forTableID, column.getName());
			String[] cats = info == null ? null : info.getCategories();
			if (cats == null) {
				if (selectedCat == null || selectedCat.equals("uncat")) {
					tvAvail.addDataSource(column);
				}
			} else {
  			for (int j = 0; j < cats.length; j++) {
  				String cat = cats[j];
  				if ((selectedCat == null || selectedCat.equalsIgnoreCase(cat))
  						&& info.getProficiency() <= selectedProf) {
  					tvAvail.addDataSource(column);
  					break;
  				}
  			}
			}
		}
		tvAvail.processDataSourceQueue();
	}

	/**
	 *
	 *
	 * @since 4.0.0.5
	 */
	protected void removeSelectedChosen() {
		Object[] datasources = tvChosen.getSelectedDataSources().toArray();
		for (int i = 0; i < datasources.length; i++) {
			TableColumnCore column = (TableColumnCore) datasources[i];
			mapNewVisibility.put(column, Boolean.FALSE);
		}
		tvChosen.removeDataSources(datasources);
		tvChosen.processDataSourceQueue();
		for (int i = 0; i < datasources.length; i++) {
			TableRowSWT row = (TableRowSWT) tvAvail.getRow((TableColumn)datasources[i]);
			if (row != null) {
				row.redraw();
			}
		}
	}

	/**
	 *
	 *
	 * @since 4.0.0.5
	 */
	protected void moveChosenDown() {
		TableRowCore[] selectedRows = tvChosen.getSelectedRows();
		TableRowCore[] rows = tvChosen.getRows();
		for (int i = selectedRows.length - 1; i >= 0; i--) {
			TableRowCore row = selectedRows[i];
			TableColumnCore column = (TableColumnCore) row.getDataSource();
			if (column != null) {
				int oldColumnPos = column.getPosition();
				int oldRowPos = row.getIndex();
				if (oldRowPos < rows.length - 1) {
					TableRowCore displacedRow = rows[oldRowPos + 1];
					((TableColumnCore) displacedRow.getDataSource()).setPositionNoShift(oldColumnPos);
					rows[oldRowPos + 1] = rows[oldRowPos];
					rows[oldRowPos] = displacedRow;
					column.setPositionNoShift(oldColumnPos + 1);
				}
			}
		}
		tvChosen.tableInvalidate();
		tvChosen.refreshTable(true);
	}

	/**
	 *
	 *
	 * @since 4.0.0.5
	 */
	protected void moveChosenUp() {
		TableRowCore[] selectedRows = tvChosen.getSelectedRows();
		TableRowCore[] rows = tvChosen.getRows();
		for (int i = 0; i < selectedRows.length; i++) {
			TableRowCore row = selectedRows[i];
			TableColumnCore column = (TableColumnCore) row.getDataSource();
			if (column != null) {
				int oldColumnPos = column.getPosition();
				int oldRowPos = row.getIndex();
				if (oldRowPos > 0) {
					TableRowCore displacedRow = rows[oldRowPos - 1];
					((TableColumnCore) displacedRow.getDataSource()).setPositionNoShift(oldColumnPos);
					rows[oldRowPos - 1] = rows[oldRowPos];
					rows[oldRowPos] = displacedRow;
					column.setPositionNoShift(oldColumnPos - 1);

					column.setAlignment(TableColumnCore.ALIGN_CENTER);
				}
			}
		}
		tvChosen.tableInvalidate();
		tvChosen.refreshTable(true);
	}

	protected void alignChosen(int align) {
		TableRowCore[] selectedRows = tvChosen.getSelectedRows();
		for (int i = 0; i < selectedRows.length; i++) {
			TableRowCore row = selectedRows[i];
			TableColumnCore column = (TableColumnCore) row.getDataSource();
			if (column != null) {
				column.setAlignment(align);
			}
		}
		tvChosen.tableInvalidate();
		tvChosen.refreshTable(true);
	}


	/**
	 *
	 *
	 * @since 4.0.0.5
	 */
	protected void apply() {
		TableColumnManager tcm = TableColumnManager.getInstance();
		if (doReset) {
			TableColumnCore[] allTableColumns = tcm.getAllTableColumnCoreAsArray(
					forDataSourceType, forTableID);
			if (allTableColumns != null) {
				for (TableColumnCore column : allTableColumns) {
					if (column != null) {
						column.reset();
					}
				}
			}
		}

		for (TableColumnCore tc : mapNewVisibility.keySet()) {
			boolean visible = mapNewVisibility.get(tc).booleanValue();
			tc.setVisible(visible);
		}

		tcm.saveTableColumns(forDataSourceType, forTableID);
		listener.tableStructureChanged(true, forDataSourceType);
	}

	/**
	 * @return
	 *
	 * @since 4.0.0.5
	 */
	private TableViewSWT<?> createTVChosen() {
		final TableColumnManager tcm = TableColumnManager.getInstance();
		TableColumnCore[] columnTVChosen = tcm.getAllTableColumnCoreAsArray(
				TableColumn.class, TABLEID_CHOSEN);
		for (int i = 0; i < columnTVChosen.length; i++) {
			TableColumnCore column = columnTVChosen[i];
			if (column.getName().equals(ColumnTC_ChosenColumn.COLUMN_ID)) {
				column.setVisible(true);
				column.setWidth(175);
				column.setSortAscending(true);
			} else {
				column.setVisible(false);
			}
		}

		final TableViewSWT<?> tvChosen = TableViewFactory.createTableViewSWT(
				TableColumn.class, TABLEID_CHOSEN, TABLEID_CHOSEN, columnTVChosen,
				ColumnTC_ChosenColumn.COLUMN_ID, SWT.FULL_SELECTION | SWT.VIRTUAL
						| SWT.MULTI);
		tvAvail.setParentDataSource(this);
		tvChosen.setMenuEnabled(false);
		tvChosen.setHeaderVisible(false);
		//tvChosen.setRowDefaultHeight(16);

		tvChosen.addLifeCycleListener(new TableLifeCycleListener() {
			private DragSource dragSource;
			private DropTarget dropTarget;

			public void tableViewInitialized() {
				dragSource = tvChosen.createDragSource(DND.DROP_MOVE | DND.DROP_COPY
						| DND.DROP_LINK);
				dragSource.setTransfer(new Transfer[] {
					TextTransfer.getInstance()
				});
				dragSource.setData("tv", tvChosen);
				dragSource.addDragListener(dragSourceListener);

				dropTarget = tvChosen.createDropTarget(DND.DROP_DEFAULT
						| DND.DROP_MOVE | DND.DROP_COPY | DND.DROP_LINK
						| DND.DROP_TARGET_MOVE);
				dropTarget.setTransfer(new Transfer[] {
					TextTransfer.getInstance()
				});
				dropTarget.addDropListener(new DropTargetListener() {

					public void dropAccept(DropTargetEvent event) {
						// TODO Auto-generated method stub

					}

					public void drop(DropTargetEvent event) {
						String id = (String) event.data;
						TableRowCore destRow = tvChosen.getRow(event);

						TableView<?> tv = id.equals("c") ? tvChosen : tvAvail;

						Object[] dataSources = tv.getSelectedDataSources().toArray();
						for (int i = 0; i < dataSources.length; i++) {
							TableColumnCore column = (TableColumnCore) dataSources[i];
							if (column != null) {
								chooseColumn(column, destRow, true);
								TableRowCore row = tvAvail.getRow(column);
								if (row != null) {
									row.redraw();
								}
							}
						}
					}

					public void dragOver(DropTargetEvent event) {
						// TODO Auto-generated method stub

					}

					public void dragOperationChanged(DropTargetEvent event) {
						// TODO Auto-generated method stub

					}

					public void dragLeave(DropTargetEvent event) {
						// TODO Auto-generated method stub

					}

					public void dragEnter(DropTargetEvent event) {
						// TODO Auto-generated method stub

					}
				});
			}

			public void tableViewDestroyed() {
				if (dragSource != null && !dragSource.isDisposed()) {
					dragSource.dispose();
				}
				if (dropTarget != null && !dropTarget.isDisposed()) {
					dropTarget.dispose();
				}
			}
		});

		tvChosen.addKeyListener(new KeyListener() {
			public void keyReleased(KeyEvent e) {
			}

			public void keyPressed(KeyEvent e) {
				if (e.stateMask == 0
						&& (e.keyCode == SWT.ARROW_LEFT || e.keyCode == SWT.DEL)) {
					removeSelectedChosen();
					e.doit = false;
				}

				if (e.stateMask == SWT.CONTROL) {
					if (e.keyCode == SWT.ARROW_UP) {
						moveChosenUp();
						e.doit = false;
					} else if (e.keyCode == SWT.ARROW_DOWN) {
						moveChosenDown();
						e.doit = false;
					}
				}
			}
		});
		return tvChosen;
	}

	/**
	 * @return
	 *
	 * @since 4.0.0.5
	 */
	private TableViewSWT<TableColumn> createTVAvail() {
		final TableColumnManager tcm = TableColumnManager.getInstance();
		Map<String, TableColumnCore> mapColumns = tcm.getTableColumnsAsMap(
				TableColumn.class, TABLEID_AVAIL);
		TableColumnCore[] columns;
		int[] widths = { 405, 105 };
		if (sampleRow == null) {
			columns = new TableColumnCore[] {
				mapColumns.get(ColumnTC_NameInfo.COLUMN_ID),
			};
			widths = new int[] { 510 };
		} else {
			columns = new TableColumnCore[] {
				mapColumns.get(ColumnTC_NameInfo.COLUMN_ID),
				mapColumns.get(ColumnTC_Sample.COLUMN_ID),
			};
		}
		for (int i = 0; i < columns.length; i++) {
			TableColumnCore column = columns[i];
			if (column != null) {
				column.setVisible(true);
				column.setPositionNoShift(i);
				column.setWidth(widths[i]);
			}
		}

		final TableViewSWT<TableColumn> tvAvail = TableViewFactory.createTableViewSWT(
				TableColumn.class, TABLEID_AVAIL, TABLEID_AVAIL, columns,
				ColumnTC_NameInfo.COLUMN_ID, SWT.FULL_SELECTION | SWT.VIRTUAL
						| SWT.SINGLE);
		tvAvail.setParentDataSource(this);
		tvAvail.setMenuEnabled(false);

		tvAvail.setRowDefaultHeightEM(5);

		tvAvail.addLifeCycleListener(new TableLifeCycleListener() {
			private DragSource dragSource;
			private DropTarget dropTarget;

			public void tableViewInitialized() {
				dragSource = tvAvail.createDragSource(DND.DROP_MOVE | DND.DROP_COPY
						| DND.DROP_LINK);
				dragSource.setTransfer(new Transfer[] {
					TextTransfer.getInstance()
				});
				dragSource.setData("tv", tvAvail);
				dragSource.addDragListener(dragSourceListener);


				dropTarget = tvAvail.createDropTarget(DND.DROP_DEFAULT
						| DND.DROP_MOVE | DND.DROP_COPY | DND.DROP_LINK
						| DND.DROP_TARGET_MOVE);
				dropTarget.setTransfer(new Transfer[] {
					TextTransfer.getInstance()
				});
				dropTarget.addDropListener(new DropTargetAdapter() {
					public void drop(DropTargetEvent event) {
						String id = (String) event.data;

						if (!id.equals("c")) {
							return;
						}

						removeSelectedChosen();
					}
				});

			}

			public void tableViewDestroyed() {
				if (dragSource != null && !dragSource.isDisposed()) {
					dragSource.dispose();
				}
				if (dropTarget != null && !dropTarget.isDisposed()) {
					dropTarget.dispose();
				}
			}
		});

		tvAvail.addSelectionListener(new TableSelectionAdapter() {
			public void defaultSelected(TableRowCore[] rows, int stateMask) {
				for (int i = 0; i < rows.length; i++) {
					TableRowCore row = rows[i];
					TableColumnCore column = (TableColumnCore) row.getDataSource();
					chooseColumn(column, null, false);
				}
			}
		}, false);

		tvAvail.addKeyListener(new KeyListener() {
			public void keyReleased(KeyEvent e) {
			}

			public void keyPressed(KeyEvent e) {
				if (e.stateMask == 0) {
					if (e.keyCode == SWT.ARROW_RIGHT) {
						TableRowCore[] selectedRows = tvAvail.getSelectedRows();
						for (int i = 0; i < selectedRows.length; i++) {
							TableRowCore row = selectedRows[i];
							TableColumnCore column = (TableColumnCore) row.getDataSource();
							chooseColumn(column, null, false);
							tvChosen.processDataSourceQueue();
							row.redraw();
						}
						e.doit = false;
					} else if (e.keyCode == SWT.ARROW_LEFT) {
						TableRowCore[] selectedRows = tvAvail.getSelectedRows();
						for (int i = 0; i < selectedRows.length; i++) {
							TableRowCore row = selectedRows[i];
							TableColumnCore column = (TableColumnCore) row.getDataSource();
							mapNewVisibility.put(column, Boolean.FALSE);
							tvChosen.removeDataSource(column);
							tvChosen.processDataSourceQueue();
							row.redraw();
						}
						e.doit = false;
					}
				}
			}
		});

		return tvAvail;
	}

	public void open() {
		shell.open();
	}

	// @see com.aelitis.azureus.ui.common.updater.UIUpdatable#getUpdateUIName()
	public String getUpdateUIName() {
		// TODO Auto-generated method stub
		return null;
	}

	// @see com.aelitis.azureus.ui.common.updater.UIUpdatable#updateUI()
	public void updateUI() {
		if (shell.isDisposed()) {
			UIUpdaterSWT.getInstance().removeUpdater(this);
			return;
		}
		if (tvAvail != null && !tvAvail.isDisposed()) {
			tvAvail.refreshTable(false);
		}
		if (tvChosen != null && !tvChosen.isDisposed()) {
			tvChosen.refreshTable(false);
		}
	}

	public TableRow getSampleRow() {
		return sampleRow;
	}

	public void chooseColumn(TableColumnCore column) {
		chooseColumn(column, null, false);
		TableRowCore row = tvAvail.getRow(column);
		if (row != null) {
			row.redraw();
		}
	}

	public boolean isColumnAdded(TableColumnCore column) {
		if (tvChosen == null) {
			return false;
		}
		TableRowCore row = tvChosen.getRow(column);
		return row != null;
	}

	/**
	 * @param column
	 *
	 * @since 4.0.0.5
	 */
	public void chooseColumn(final TableColumnCore column,
			TableRowCore placeAboveRow, boolean ignoreExisting) {
		TableRowCore row = tvChosen.getRow(column);

		if (row == null || ignoreExisting) {
			int newPosition = 0;

			row = placeAboveRow == null && !ignoreExisting ? tvChosen.getFocusedRow()
					: placeAboveRow;
			if (row == null || row.getDataSource() == null) {
				if (columnsChosen.length > 0) {
					newPosition = columnsChosen.length;
				}
			} else {
				newPosition = ((TableColumn) row.getDataSource()).getPosition();
			}

			int oldPosition = column.getPosition();
			final boolean shiftDir = oldPosition > newPosition
					||  !mapNewVisibility.get(column).booleanValue();
			column.setPositionNoShift(newPosition);
			mapNewVisibility.put(column, Boolean.TRUE);

				// seen one of these here:
				// java.lang.IllegalArgumentException: Comparison method violates its general contract!
				// so I guess getPosition can return different values :(
				// hack

			for (int i=0;i<10;i++) {
				try {
					Arrays.sort(columnsChosen, new Comparator() {
						public int compare(Object arg0, Object arg1) {
							if ((arg1 instanceof TableColumn) && (arg0 instanceof TableColumn)) {
								int iPositionA = ((TableColumn) arg0).getPosition();
								if (iPositionA < 0)
									iPositionA = 0xFFFF + iPositionA;
								int iPositionB = ((TableColumn) arg1).getPosition();
								if (iPositionB < 0)
									iPositionB = 0xFFFF + iPositionB;

								int i = iPositionA - iPositionB;
								if (i == 0) {
									if (column == arg0) {
										return shiftDir ? -1 : 1;
									}
									return shiftDir ? 1 : -1;
								}
								return i;
							}
							return 0;
						}
					});

					break;

				} catch (Throwable e) {
				}
			}

			int pos = 0;
			for (int i = 0; i < columnsChosen.length; i++) {
				if (mapNewVisibility.get(columnsChosen[i]).booleanValue()) {
					columnsChosen[i].setPositionNoShift(pos++);
				}
			}

			TableRowCore existingRow = tvChosen.getRow(column);
			if (existingRow == null) {
				tvChosen.addDataSource(column);
				tvChosen.processDataSourceQueue();
				tvChosen.addCountChangeListener(new TableCountChangeListener() {

					public void rowRemoved(TableRowCore row) {
					}

					public void rowAdded(final TableRowCore row) {
						Utils.execSWTThreadLater(500, new AERunnable() {
							public void runSupport() {
								tvChosen.setSelectedRows(new TableRowCore[] { row });
								tvChosen.showRow(row);
							}
						});
						tvChosen.removeCountChangeListener(this);
					}
				});
			}

			Arrays.sort(columnsChosen,
					TableColumnManager.getTableColumnOrderComparator());

			tvChosen.tableInvalidate();
			tvChosen.refreshTable(true);

		} else {
			row.setSelected(true);
		}
	}

	private void close() {
		if (apply) {
			apply();
		} else {
			for (int i = 0; i < columnsOriginalOrder.length; i++) {
				TableColumnCore column = columnsOriginalOrder[i];
				if (column != null) {
					column.setPositionNoShift(i);
				}
			}
		}
	}
}
