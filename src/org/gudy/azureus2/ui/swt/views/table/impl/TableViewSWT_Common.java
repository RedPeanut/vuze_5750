/*
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

package org.gudy.azureus2.ui.swt.views.table.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.*;
import org.gudy.azureus2.core3.util.*;
import org.gudy.azureus2.plugins.download.Download;
import org.gudy.azureus2.plugins.ui.menus.MenuManager;
import org.gudy.azureus2.plugins.ui.tables.*;
import org.gudy.azureus2.pluginsimpl.local.ui.menus.MenuItemImpl;
import org.gudy.azureus2.ui.common.util.MenuItemManager;
import org.gudy.azureus2.ui.swt.MenuBuildUtils;
import org.gudy.azureus2.ui.swt.Messages;
import org.gudy.azureus2.ui.swt.SimpleTextEntryWindow;
import org.gudy.azureus2.ui.swt.Utils;
import org.gudy.azureus2.ui.swt.mainwindow.Colors;
import org.gudy.azureus2.ui.swt.views.columnsetup.TableColumnSetupWindow;
import org.gudy.azureus2.ui.swt.views.table.*;
import org.gudy.azureus2.ui.swt.views.table.utils.TableContextMenuManager;

import com.aelitis.azureus.ui.common.table.*;
import com.aelitis.azureus.ui.common.table.impl.TableColumnManager;

public class TableViewSWT_Common
	implements MouseListener, MouseMoveListener, SelectionListener, KeyListener
{

	TableViewSWT<?> tv;
	private long lCancelSelectionTriggeredOn = -1;
	private long lastSelectionTriggeredOn = -1;

	private static final int ASYOUTYPE_MODE_FIND = 0;
	private static final int ASYOUTYPE_MODE_FILTER = 1;
	private static final int ASYOUTYPE_MODE = ASYOUTYPE_MODE_FILTER;
	private static final int ASYOUTYPE_UPDATEDELAY = 300;

	private static final Color COLOR_FILTER_REGEX	= Colors.fadedYellow;
	private static Font FONT_NO_REGEX;
	private static Font FONT_REGEX;
	private static Font FONT_REGEX_ERROR;

	private List<TableViewSWTMenuFillListener> listenersMenuFill = new ArrayList<TableViewSWTMenuFillListener>(
			1);

	private List<KeyListener> listenersKey = new ArrayList<KeyListener>(1);

	private ArrayList<TableRowMouseListener> rowMouseListeners;

  private static AEMonitor mon_RowMouseListener = new AEMonitor("rml");

	private static AEMonitor mon_RowPaintListener = new AEMonitor("rpl");

	public int xAdj = 0;
	public int yAdj = 0;

	private ArrayList<TableRowSWTPaintListener> rowPaintListeners;


	public TableViewSWT_Common(TableViewSWT<?> tv) {
		this.tv = tv;
	}

	long lastMouseDblClkEventTime = 0;
	public void mouseDoubleClick(MouseEvent e) {
		long time = e.time & 0xFFFFFFFFL;
		long diff = time - lastMouseDblClkEventTime;
		// We fake a double click on MouseUp.. this traps 2 double clicks
		// in quick succession and ignores the 2nd.
		if (diff <= e.display.getDoubleClickTime() && diff >= 0) {
			return;
		}
		lastMouseDblClkEventTime = time;

		TableColumnCore tc = tv.getTableColumnByOffset(e.x);
		TableCellCore cell = tv.getTableCell(e.x, e.y);
		if (cell != null && tc != null) {
			TableCellMouseEvent event = createMouseEvent(cell, e,
					TableCellMouseEvent.EVENT_MOUSEDOUBLECLICK, false);
			if (event != null) {
				tc.invokeCellMouseListeners(event);
				cell.invokeMouseListeners(event);
				if (event.skipCoreFunctionality) {
					lCancelSelectionTriggeredOn = System.currentTimeMillis();
				}
			}
		}
	}

	long lastMouseUpEventTime = 0;
	Point lastMouseUpPos = new Point(0, 0);
	boolean mouseDown = false;
	TableRowSWT mouseDownOnRow = null;
	public void mouseUp(MouseEvent e) {
		// SWT OSX Bug: two mouseup events when app not in focus and user
		// clicks on the table.  Only one mousedown, so track that and ignore
		if (!mouseDown) {
			return;
		}
		mouseDown = false;

		TableColumnCore tc = tv.getTableColumnByOffset(e.x);
		TableCellCore cell = tv.getTableCell(e.x, e.y);
		//TableRowCore row = tv.getTableRow(e.x, e.y, true);
		mouseUp(mouseDownOnRow, cell, e.button, e.stateMask);

		if (e.button == 1) {
			long time = e.time & 0xFFFFFFFFL;
			long diff = time - lastMouseUpEventTime;
			if (diff <= e.display.getDoubleClickTime() && diff >= 0
					&& lastMouseUpPos.x == e.x && lastMouseUpPos.y == e.y) {
				// Fake double click because Cocoa SWT 3650 doesn't always trigger
				// DefaultSelection listener on a Tree on dblclick (works find in Table)
				runDefaultAction(e.stateMask);
				return;
			}
			lastMouseUpEventTime = time;
			lastMouseUpPos = new Point(e.x, e.y);
		}

		if (cell != null && tc != null) {
			TableCellMouseEvent event = createMouseEvent(cell, e,
					TableCellMouseEvent.EVENT_MOUSEUP, false);
			if (event != null) {
				tc.invokeCellMouseListeners(event);
				cell.invokeMouseListeners(event);
				if (event.skipCoreFunctionality) {
					lCancelSelectionTriggeredOn = System.currentTimeMillis();
				}
			}
		}
	}

	TableRowCore lastClickRow;

	public void mouseDown(MouseEvent e) {
		mouseDown = true;
		// we need to fill the selected row indexes here because the
		// dragstart event can occur before the SWT.SELECTION event and
		// our drag code needs to know the selected rows..
		TableRowSWT row = mouseDownOnRow = tv.getTableRow(e.x, e.y, false);
		TableCellCore cell = tv.getTableCell(e.x, e.y);
		TableColumnCore tc = cell == null ? null : cell.getTableColumnCore();

		mouseDown(row, cell, e.button, e.stateMask);

		if (row == null) {
			tv.setSelectedRows(new TableRowCore[0]);
		}

		tv.editCell(null, -1); // clear out current cell editor

		if (cell != null && tc != null) {
			TableCellMouseEvent event = createMouseEvent(cell, e,
					TableCellMouseEvent.EVENT_MOUSEDOWN, false);
			if (event != null) {
				tc.invokeCellMouseListeners(event);
				cell.invokeMouseListeners(event);
				tv.invokeRowMouseListener(event);
				if (event.skipCoreFunctionality) {
					lCancelSelectionTriggeredOn = System.currentTimeMillis();
				}
			}
			if (tc.hasInplaceEditorListener() && e.button == 1
					&& lastClickRow == cell.getTableRowCore()) {
				tv.editCell(tv.getTableColumnByOffset(e.x), cell.getTableRowCore().getIndex());
			}
			if (e.button == 1) {
				lastClickRow = cell.getTableRowCore();
			}
		} else if (row != null) {
			TableRowMouseEvent event = createMouseEvent(row, e,
					TableCellMouseEvent.EVENT_MOUSEDOWN, false);
			if (event != null) {
				tv.invokeRowMouseListener(event);
			}
		}
	}

	public void mouseDown(TableRowSWT row, TableCellCore cell, int button,
			int stateMask) {
	}

	public void mouseUp(TableRowCore row, TableCellCore cell, int button,
			int stateMask) {
	}

	private TableCellMouseEvent createMouseEvent(TableCellCore cell, MouseEvent e,
			int type, boolean allowOOB) {
		TableCellMouseEvent event = new TableCellMouseEvent();
		event.cell = cell;
		if (cell != null) {
			event.row = cell.getTableRow();
		}
		event.eventType = type;
		event.button = e.button;
		// TODO: Change to not use SWT masks
		event.keyboardState = e.stateMask;
		event.skipCoreFunctionality = false;
		if (cell instanceof TableCellSWT) {
			Rectangle r = ((TableCellSWT) cell).getBounds();
			if (r == null) {
				return event;
			}
			event.x = e.x - r.x - xAdj;
			if (!allowOOB && event.x < 0) {
				return null;
			}
			event.y = e.y - r.y - yAdj;
			if (!allowOOB && event.y < 0) {
				return null;
			}
		}

		return event;
	}

	private TableRowMouseEvent createMouseEvent(TableRowSWT row, MouseEvent e,
			int type, boolean allowOOB) {
		TableCellMouseEvent event = new TableCellMouseEvent();
		event.row = row;
		event.eventType = type;
		event.button = e.button;
		// TODO: Change to not use SWT masks
		event.keyboardState = e.stateMask;
		event.skipCoreFunctionality = false;
		if (row != null) {
			Rectangle r = row.getBounds();
			event.x = e.x - r.x - xAdj;
			if (!allowOOB && event.x < 0) {
				return null;
			}
			event.y = e.y - r.y - yAdj;
			if (!allowOOB && event.y < 0) {
				return null;
			}
		}

		return event;
	}



	TableCellCore lastCell = null;

	int lastCursorID = 0;

	public void mouseMove(MouseEvent e) {
		lCancelSelectionTriggeredOn = -1;
		if (tv.isDragging()) {
			return;
		}
		try {
			TableCellCore cell = tv.getTableCell(e.x, e.y);

			if (cell != lastCell) {
  			if (lastCell != null && !lastCell.isDisposed()) {
  				TableCellMouseEvent event = createMouseEvent(lastCell, e,
  						TableCellMouseEvent.EVENT_MOUSEEXIT, true);
  				if (event != null) {
  					((TableCellSWT) lastCell).setMouseOver(false);
  					TableColumnCore tc = ((TableColumnCore) lastCell.getTableColumn());
  					if (tc != null) {
  						tc.invokeCellMouseListeners(event);
  					}
  					lastCell.invokeMouseListeners(event);
  				}
  			}

  			if (cell != null && !cell.isDisposed()) {
  				TableCellMouseEvent event = createMouseEvent(cell, e,
  						TableCellMouseEvent.EVENT_MOUSEENTER, false);
  				if (event != null) {
  					((TableCellSWT) cell).setMouseOver(true);
  					TableColumnCore tc = ((TableColumnCore) cell.getTableColumn());
  					if (tc != null) {
  						tc.invokeCellMouseListeners(event);
  					}
  					cell.invokeMouseListeners(event);
  				}
  			}
			}

			int iCursorID = 0;
			if (cell == null) {
				lastCell = null;
			} else if (cell == lastCell) {
				iCursorID = lastCursorID;
			} else {
				iCursorID = cell.getCursorID();
				lastCell = cell;
			}

			if (iCursorID < 0) {
				iCursorID = 0;
			}

			if (iCursorID != lastCursorID) {
				lastCursorID = iCursorID;

				if (iCursorID >= 0) {
					tv.getComposite().setCursor(tv.getComposite().getDisplay().getSystemCursor(iCursorID));
				} else {
					tv.getComposite().setCursor(null);
				}
			}

			if (cell != null) {
				TableCellMouseEvent event = createMouseEvent(cell, e,
						TableCellMouseEvent.EVENT_MOUSEMOVE, false);
				if (event != null) {
					TableColumnCore tc = ((TableColumnCore) cell.getTableColumn());
					if (tc.hasCellMouseMoveListener()) {
						((TableColumnCore) cell.getTableColumn()).invokeCellMouseListeners(event);
					}
					cell.invokeMouseListeners(event);

					// listener might have changed it

					iCursorID = cell.getCursorID();
					if (iCursorID != lastCursorID) {
						lastCursorID = iCursorID;

						if (iCursorID >= 0) {
							tv.getComposite().setCursor(tv.getComposite().getDisplay().getSystemCursor(iCursorID));
						} else {
							tv.getComposite().setCursor(null);
						}
					}
				}
			}
		} catch (Exception ex) {
			Debug.out(ex);
		}
	}

	public void widgetSelected(SelectionEvent e) {
	}

	public void widgetDefaultSelected(SelectionEvent e) {
		if (lCancelSelectionTriggeredOn > 0
				&& System.currentTimeMillis() - lCancelSelectionTriggeredOn < 200) {
			e.doit = false;
		} else {
			runDefaultAction(e.stateMask);
		}
	}

	public void keyPressed(KeyEvent event) {
		// Note: Both table key presses and txtFilter keypresses go through this
		//       method.

		TableViewSWTFilter<?> filter = tv.getSWTFilter();
		if (event.widget != null && filter != null && event.widget == filter.widget) {
			if (event.character == SWT.DEL || event.character == SWT.BS) {
				handleSearchKeyPress(event);
				return;
			}
		}

		KeyListener[] listeners = tv.getKeyListeners();
		for (KeyListener l : listeners) {
			l.keyPressed(event);
			if (!event.doit) {
				lCancelSelectionTriggeredOn = SystemTime.getCurrentTime();
				return;
			}
		}

		if (event.keyCode == SWT.F5) {
			if ((event.stateMask & SWT.SHIFT) != 0) {
				tv.runForSelectedRows(new TableGroupRowRunner() {
					public void run(TableRowCore row) {
						row.invalidate();
						row.refresh(true);
					}
				});
			} else if ((event.stateMask & SWT.CONTROL) != 0) {
				tv.runForAllRows(new TableGroupRowRunner() {
					public void run(TableRowCore row) {
						row.invalidate();
						row.refresh(true);
					}
				});
			} else {
				tv.sortColumn(true);
			}
			event.doit = false;
			return;
		}

		int key = event.character;
		if (key <= 26 && key > 0) {
			key += 'a' - 1;
		}

		if (event.stateMask == SWT.MOD1) {
			switch (key) {
				case 'a': { // CTRL+A select all Torrents
					if (filter == null || event.widget != filter.widget) {
						if (!tv.isSingleSelection()) {
							tv.selectAll();
							event.doit = false;
						}
					} else {
						filter.widget.selectAll();
						event.doit = false;
					}
				}
				break;

				case '+': {
					if (Constants.isUnix) {
						tv.expandColumns();
						event.doit = false;
					}
					break;
				}
				case 'f': // CTRL+F Find/Filter
					tv.openFilterDialog();
					event.doit = false;
					break;
				case 'x': { // CTRL+X: RegEx search switch
					if (filter != null && event.widget == filter.widget) {
						filter.regex = !filter.regex;
						filter.widget.setBackground(filter.regex?COLOR_FILTER_REGEX:null);
						validateFilterRegex();
						tv.refilter();
						return;
					}
				}
					break;
				case 'g':
					System.out.println("force sort");
					tv.resetLastSortedOn();
					tv.sortColumn(true);
					break;
			}

		}

		if (event.stateMask == 0) {
			if (filter != null && filter.widget == event.widget) {
				if (event.keyCode == SWT.ARROW_DOWN) {
					tv.setFocus();
					event.doit = false;
				} else if (event.character == 13) {
					tv.refilter();
				}
			}
		}

		if (!event.doit) {
			return;
		}

		handleSearchKeyPress(event);
	}

	private void handleSearchKeyPress(KeyEvent e) {
		TableViewSWTFilter<?> filter = tv.getSWTFilter();
		if (filter == null || e.widget == filter.widget) {
			return;
		}

		String newText = null;

		// normal character: jump to next item with a name beginning with this character
		if (ASYOUTYPE_MODE == ASYOUTYPE_MODE_FIND) {
			if (System.currentTimeMillis() - filter.lastFilterTime > 3000)
				newText = "";
		}

		if (e.keyCode == SWT.BS) {
			if (e.stateMask == SWT.CONTROL) {
				newText = "";
			} else if (filter.nextText.length() > 0) {
				newText = filter.nextText.substring(0, filter.nextText.length() - 1);
			}
		} else if ((e.stateMask & ~SWT.SHIFT) == 0 && e.character > 32 && e.character != SWT.DEL) {
			newText = filter.nextText + String.valueOf(e.character);
		}

		if (newText == null) {
			return;
		}

		if (ASYOUTYPE_MODE == ASYOUTYPE_MODE_FILTER) {
			if (filter != null && filter.widget != null && !filter.widget.isDisposed()) {
				filter.widget.setFocus();
			}
			tv.setFilterText(newText);
//		} else {
//			TableCellCore[] cells = getColumnCells("name");
//
//			//System.out.println(sLastSearch);
//
//			Arrays.sort(cells, TableCellImpl.TEXT_COMPARATOR);
//			int index = Arrays.binarySearch(cells, filter.text,
//					TableCellImpl.TEXT_COMPARATOR);
//			if (index < 0) {
//
//				int iEarliest = -1;
//				String s = filter.regex ? filter.text : "\\Q" + filter.text + "\\E";
//				Pattern pattern = Pattern.compile(s, Pattern.CASE_INSENSITIVE);
//				for (int i = 0; i < cells.length; i++) {
//					Matcher m = pattern.matcher(cells[i].getText());
//					if (m.find() && (m.start() < iEarliest || iEarliest == -1)) {
//						iEarliest = m.start();
//						index = i;
//					}
//				}
//
//				if (index < 0)
//					// Insertion Point (best guess)
//					index = -1 * index - 1;
//			}
//
//			if (index >= 0) {
//				if (index >= cells.length)
//					index = cells.length - 1;
//				TableRowCore row = cells[index].getTableRowCore();
//				int iTableIndex = row.getIndex();
//				if (iTableIndex >= 0) {
//					setSelectedRows(new TableRowCore[] {
//						row
//					});
//				}
//			}
//			filter.lastFilterTime = System.currentTimeMillis();
		}
		e.doit = false;
	}

	private void validateFilterRegex() {
		TableViewSWTFilter<?> filter = tv.getSWTFilter();
		if (filter.regex) {
			if (FONT_NO_REGEX == null) {
				FONT_NO_REGEX = filter.widget.getFont();
				FontData[] fd = FONT_NO_REGEX.getFontData();
				for (int i = 0; i < fd.length; i++) {
					fd[i].setStyle(SWT.BOLD);
				}
				FONT_REGEX = new Font(filter.widget.getDisplay(), fd);
				for (int i = 0; i < fd.length; i++) {
					fd[i].setStyle(SWT.ITALIC);
				}
				FONT_REGEX_ERROR = new Font(filter.widget.getDisplay(), fd);
			}
			try {
				Pattern.compile(filter.nextText, Pattern.CASE_INSENSITIVE);
				filter.widget.setBackground(COLOR_FILTER_REGEX);
				filter.widget.setFont(FONT_REGEX);

				Messages.setLanguageTooltip(filter.widget,
						"MyTorrentsView.filter.tooltip");
			} catch (Exception e) {
				filter.widget.setBackground(Colors.colorErrorBG);
				filter.widget.setToolTipText(e.getMessage());
				filter.widget.setFont(FONT_REGEX_ERROR);
			}
		} else {
			filter.widget.setBackground(null);
			Messages.setLanguageTooltip(filter.widget,
					"MyTorrentsView.filter.tooltip");
			if (FONT_NO_REGEX != null) {
				filter.widget.setFont(FONT_NO_REGEX);
			}
		}
	}

	public void setFilterText(String s) {
		TableViewSWTFilter<?> filter = tv.getSWTFilter();
		if (filter == null) {
			return;
		}
		filter.nextText = s;
		if (filter != null && filter.widget != null && !filter.widget.isDisposed()) {
			if (!filter.nextText.equals(filter.widget.getText())) {
				filter.widget.setText(filter.nextText);
				filter.widget.setSelection(filter.nextText.length());
			}

			validateFilterRegex();
		}

		if (filter.eventUpdate != null) {
			filter.eventUpdate.cancel();
		}
		filter.eventUpdate = SimpleTimer.addEvent("SearchUpdate",
				SystemTime.getOffsetTime(ASYOUTYPE_UPDATEDELAY),
				new TimerEventPerformer() {
					public void perform(TimerEvent event) {
						TableViewSWTFilter<?> filter = tv.getSWTFilter();

						if (filter == null) {
							return;
						}
						if (filter.eventUpdate == null || filter.eventUpdate.isCancelled()) {
							filter.eventUpdate = null;
							return;
						}
						filter.eventUpdate = null;
						if (filter.nextText != null && !filter.nextText.equals(filter.text)) {
							filter.text = filter.nextText;
							filter.checker.filterSet(filter.text);
							tv.refilter();
						}
					}
				});
	}

	public void runDefaultAction(int stateMask) {
		// Don't allow mutliple run defaults in quick succession
		if (lastSelectionTriggeredOn > 0
				&& System.currentTimeMillis() - lastSelectionTriggeredOn < 200) {
			return;
		}

		// plugin may have cancelled the default action
		if (System.currentTimeMillis() - lCancelSelectionTriggeredOn > 200) {
			lastSelectionTriggeredOn = System.currentTimeMillis();
			TableRowCore[] selectedRows = tv.getSelectedRows();
			tv.triggerDefaultSelectedListeners(selectedRows, stateMask);
		}
	}

	public void keyReleased(KeyEvent event) {
		KeyListener[] listeners = tv.getKeyListeners();
		for (KeyListener l : listeners) {
			l.keyReleased(event);
			if (!event.doit) {
				return;
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.gudy.azureus2.ui.swt.views.table.TableViewSWT#addKeyListener(org.eclipse.swt.events.KeyListener)
	 */
	public void addKeyListener(KeyListener listener) {
		if (listenersKey.contains(listener)) {
			return;
		}

		listenersKey.add(listener);
	}

	/* (non-Javadoc)
	 * @see org.gudy.azureus2.ui.swt.views.table.TableViewSWT#removeKeyListener(org.eclipse.swt.events.KeyListener)
	 */
	public void removeKeyListener(KeyListener listener) {
		listenersKey.remove(listener);
	}

	public KeyListener[] getKeyListeners() {
		return listenersKey.toArray(new KeyListener[0]);
	}

	public void addRowMouseListener(TableRowMouseListener listener) {
		try {
			mon_RowMouseListener.enter();

			if (rowMouseListeners == null)
				rowMouseListeners = new ArrayList<TableRowMouseListener>(1);

			rowMouseListeners.add(listener);

		} finally {
			mon_RowMouseListener.exit();
		}
	}

	public void removeRowMouseListener(TableRowMouseListener listener) {
		try {
			mon_RowMouseListener.enter();

			if (rowMouseListeners == null)
				return;

			rowMouseListeners.remove(listener);

		} finally {
			mon_RowMouseListener.exit();
		}
	}

	/* (non-Javadoc)
	 * @see org.gudy.azureus2.ui.swt.views.table.TableViewSWT#invokeRowMouseListener(org.gudy.azureus2.plugins.ui.tables.TableRowMouseEvent)
	 */
	public void invokeRowMouseListener(TableRowMouseEvent event) {
		if (rowMouseListeners == null) {
			return;
		}
		ArrayList<TableRowMouseListener> listeners = new ArrayList<TableRowMouseListener>(
				rowMouseListeners);

		for (int i = 0; i < listeners.size(); i++) {
			try {
				TableRowMouseListener l = (listeners.get(i));

				l.rowMouseTrigger(event);

			} catch (Throwable e) {
				Debug.printStackTrace(e);
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.gudy.azureus2.ui.swt.views.table.TableViewSWT#addRowPaintListener(org.gudy.azureus2.ui.swt.views.table.TableRowSWTPaintListener)
	 */
	public void addRowPaintListener(TableRowSWTPaintListener listener) {
		try {
			mon_RowPaintListener.enter();

			if (rowPaintListeners == null)
				rowPaintListeners = new ArrayList<TableRowSWTPaintListener>(1);

			rowPaintListeners.add(listener);

		} finally {
			mon_RowPaintListener.exit();
		}
	}

	/* (non-Javadoc)
	 * @see org.gudy.azureus2.ui.swt.views.table.TableViewSWT#removeRowPaintListener(org.gudy.azureus2.ui.swt.views.table.TableRowSWTPaintListener)
	 */
	public void removeRowPaintListener(TableRowSWTPaintListener listener) {
		try {
			mon_RowPaintListener.enter();

			if (rowPaintListeners == null)
				return;

			rowPaintListeners.remove(listener);

		} finally {
			mon_RowPaintListener.exit();
		}
	}

	public void invokePaintListeners(GC gc, TableRowCore row,
			TableColumnCore column, Rectangle cellArea) {

		if (rowPaintListeners == null) {
			return;
		}
		ArrayList<TableRowSWTPaintListener> listeners = new ArrayList<TableRowSWTPaintListener>(
				rowPaintListeners);

		for (int i = 0; i < listeners.size(); i++) {
			try {
				TableRowSWTPaintListener l = (listeners.get(i));

				l.rowPaint(gc, row, column, cellArea);

			} catch (Throwable e) {
				Debug.printStackTrace(e);
			}
		}
	}

	/** Fill the Context Menu with items.  Called when menu is about to be shown.
	 *
	 * By default, a "Edit Columns" menu and a Column specific menu is set up.
	 *
	 * @param menu Menu to fill
	 * @param tcColumn
	 */
	public void fillMenu(final Menu menu, final TableColumnCore column) {
		String columnName = column == null ? null : column.getName();

		Object[] listeners = listenersMenuFill.toArray();
		for (int i = 0; i < listeners.length; i++) {
			TableViewSWTMenuFillListener l = (TableViewSWTMenuFillListener) listeners[i];
			l.fillMenu(columnName, menu);
		}

		boolean hasLevel1 = false;
		boolean hasLevel2 = false;
		// quick hack so we don't show plugin menus on selections of subitems
		TableRowCore[] selectedRows = tv.getSelectedRows();
		for (TableRowCore row : selectedRows) {
			if (row.getParentRowCore() != null) {
				hasLevel2 = true;
			} else {
				hasLevel1 = true;
			}
		}

		String tableID = tv.getTableID();
		String sMenuID = hasLevel1 ? tableID : TableManager.TABLE_TORRENT_FILES;

		// We'll add download-context specific menu items - if the table is download specific.
		// We need a better way to determine this...
		boolean isDownloadContext;
		org.gudy.azureus2.plugins.ui.menus.MenuItem[] menu_items = null;
		if (Download.class.isAssignableFrom( tv.getDataSourceType()) && !hasLevel2) {
			menu_items = MenuItemManager.getInstance().getAllAsArray(
					MenuManager.MENU_DOWNLOAD_CONTEXT);
			isDownloadContext = true;
		} else {
			menu_items = MenuItemManager.getInstance().getAllAsArray((String) null);
			isDownloadContext = false;
		}

		if (columnName == null) {
			MenuItem itemChangeTable = new MenuItem(menu, SWT.PUSH);
			Messages.setLanguageText(itemChangeTable,
					"MyTorrentsView.menu.editTableColumns");
			Utils.setMenuItemImage(itemChangeTable, "columns");

			itemChangeTable.addListener(SWT.Selection, new Listener() {
				public void handleEvent(Event e) {
					showColumnEditor();
				}
			});

		} else {

 		MenuItem item = new MenuItem(menu, SWT.PUSH);
 		Messages.setLanguageText(item, "MyTorrentsView.menu.thisColumn.toClipboard");
 		item.addListener(SWT.Selection, new Listener() {
 			public void handleEvent(Event e) {
 				String sToClipboard = "";
 				if (column == null) {
 					return;
 				}
 				String columnName = column.getName();
 				if (columnName == null) {
 					return;
 				}
 				TableRowCore[] rows = tv.getSelectedRows();
 				for (TableRowCore row : rows) {
 					if (row != rows[0]) {
 						sToClipboard += "\n";
 					}
 					TableCellCore cell = row.getTableCellCore(columnName);
 					if (cell != null) {
 						sToClipboard += cell.getClipboardText();
 					}
 				}
 				if (sToClipboard.length() == 0) {
 					return;
 				}
 				new Clipboard(Display.getDefault()).setContents(new Object[] {
 					sToClipboard
 				}, new Transfer[] {
 					TextTransfer.getInstance()
 				});
 			}
 		});
		}


		// Add Plugin Context menus..
		boolean enable_items = selectedRows.length > 0;

		TableContextMenuItem[] items = TableContextMenuManager.getInstance().getAllAsArray(
				sMenuID);

		if (items.length > 0 || menu_items.length > 0) {
			new org.eclipse.swt.widgets.MenuItem(menu, SWT.SEPARATOR);

			// Add download context menu items.
			if (menu_items != null) {
				// getSelectedDataSources(false) returns us plugin items.
				Object[] target;
				if (isDownloadContext) {
					Object[] dataSources = tv.getSelectedDataSources(false);
					target = new Download[dataSources.length];
					System.arraycopy(dataSources, 0, target, 0, target.length);
				} else {
					target = selectedRows;
				}
				MenuBuildUtils.addPluginMenuItems(menu_items, menu, true, true,
						new MenuBuildUtils.MenuItemPluginMenuControllerImpl(target));
			}

			if (items.length > 0) {
				MenuBuildUtils.addPluginMenuItems(items, menu, true, enable_items,
						new MenuBuildUtils.PluginMenuController() {
							public Listener makeSelectionListener(
									final org.gudy.azureus2.plugins.ui.menus.MenuItem plugin_menu_item) {
								return new TableSelectedRowsListener(tv, false) {
									public boolean run(TableRowCore[] rows) {
										if (rows.length != 0) {
											((MenuItemImpl) plugin_menu_item).invokeListenersMulti(rows);
										}
										return true;
									}
								};
							}

							public void notifyFillListeners(
									org.gudy.azureus2.plugins.ui.menus.MenuItem menu_item) {
								((MenuItemImpl)menu_item).invokeMenuWillBeShownListeners(tv.getSelectedRows());
							}

							// @see org.gudy.azureus2.ui.swt.MenuBuildUtils.PluginMenuController#buildSubmenu(org.gudy.azureus2.plugins.ui.menus.MenuItem)
							public void buildSubmenu(
									org.gudy.azureus2.plugins.ui.menus.MenuItem parent) {
								org.gudy.azureus2.plugins.ui.menus.MenuBuilder submenuBuilder = ((MenuItemImpl) parent).getSubmenuBuilder();
								if (submenuBuilder != null) {
									try {
										parent.removeAllChildItems();
										submenuBuilder.buildSubmenu(parent, tv.getSelectedRows());
									} catch (Throwable t) {
										Debug.out(t);
									}
								}
							}
						});
			}
		}

		if (hasLevel1) {
			// Add Plugin Context menus..
			if (column != null) {
				TableContextMenuItem[] columnItems = column.getContextMenuItems(TableColumnCore.MENU_STYLE_COLUMN_DATA);
				if (columnItems.length > 0) {
					new MenuItem(menu, SWT.SEPARATOR);

					MenuBuildUtils.addPluginMenuItems(
							columnItems,
							menu,
							true,
							true,
							new MenuBuildUtils.MenuItemPluginMenuControllerImpl(
									tv.getSelectedDataSources(true)));

				}
			}

			if (tv.getSWTFilter() != null) {
				final MenuItem itemFilter = new MenuItem(menu, SWT.PUSH);
				Messages.setLanguageText(itemFilter, "MyTorrentsView.menu.filter");
				itemFilter.addListener(SWT.Selection, new Listener() {
					public void handleEvent(Event event) {
						tv.openFilterDialog();
					}
				});
			}
		}
	}

	public void showColumnEditor() {
		TableRowCore focusedRow = tv.getFocusedRow();
		if (focusedRow == null || focusedRow.isRowDisposed()) {
			focusedRow = tv.getRow(0);
		}
		String tableID = tv.getTableID();
		new TableColumnSetupWindow(tv.getDataSourceType(), tableID, focusedRow,
				TableStructureEventDispatcher.getInstance(tableID)).open();
	}


	/**
	 * SubMenu for column specific tasks.
	 *
	 * @param iColumn Column # that tasks apply to.
	 */
	public void fillColumnMenu(final Menu menu, final TableColumnCore column,
			boolean isBlankArea) {
		String tableID = tv.getTableID();
		int	hiddenColumnCount = 0;

		if (!isBlankArea) {
			TableColumnManager tcm = TableColumnManager.getInstance();
			TableColumnCore[] allTableColumns = tcm.getAllTableColumnCoreAsArray(
					tv.getDataSourceType(), tableID);

			Arrays.sort(allTableColumns,
					TableColumnManager.getTableColumnOrderComparator());

			for (final TableColumnCore tc : allTableColumns) {
				boolean visible = tc.isVisible();
				if (!visible) {
					TableColumnInfo columnInfo = tcm.getColumnInfo(
							tv.getDataSourceType(), tableID, tc.getName());
					if (columnInfo.getProficiency() != TableColumnInfo.PROFICIENCY_BEGINNER) {
						hiddenColumnCount++;
						continue;
					}
				}
				MenuItem menuItem = new MenuItem(menu, SWT.CHECK);
				Messages.setLanguageText(menuItem, tc.getTitleLanguageKey());
				if (visible) {
					menuItem.setSelection(true);
				}
				menuItem.addListener(SWT.Selection, new Listener() {
					public void handleEvent(Event e) {
						tc.setVisible(!tc.isVisible());
						TableColumnManager tcm = TableColumnManager.getInstance();
						String tableID = tv.getTableID();
						tcm.saveTableColumns(tv.getDataSourceType(), tableID);
						if (tv instanceof TableStructureModificationListener) {
							((TableStructureModificationListener) tv).tableStructureChanged(
									true, null);
						}
					}
				});
			}
		}

		if (hiddenColumnCount > 0) {

			MenuItem itemMoreHidden = new MenuItem(menu, SWT.PUSH);
			Messages.setLanguageText(itemMoreHidden,
					"MyTorrentsView.menu.moreColHidden", new String[]{ String.valueOf(hiddenColumnCount)});

			itemMoreHidden.addListener(SWT.Selection, new Listener() {
				public void handleEvent(Event e) {
					showColumnEditor();
				}
			});
		}

		if (menu.getItemCount() > 0) {
			new MenuItem(menu, SWT.SEPARATOR);
		}

		if (column != null) {
			final MenuItem renameColumn = new MenuItem(menu, SWT.PUSH);
			Messages.setLanguageText(renameColumn,
					"MyTorrentsView.menu.renameColumn");

			renameColumn.addListener(SWT.Selection, new Listener() {
				public void handleEvent(Event e) {
					SimpleTextEntryWindow entryWindow = new SimpleTextEntryWindow(
							"ColumnRenameWindow.title", "ColumnRenameWindow.message");

					String	existing_name = column.getNameOverride();
					if (existing_name == null) {
						existing_name = "";
					}
					entryWindow.setPreenteredText(existing_name, false);
					entryWindow.selectPreenteredText(true);

					entryWindow.prompt();

					if (entryWindow.hasSubmittedInput()) {

						String name = entryWindow.getSubmittedInput().trim();

						if (name.length() == 0) {
							name = null;
						}
						column.setNameOverride(name);
						TableColumnManager tcm = TableColumnManager.getInstance();
						String tableID = tv.getTableID();
						tcm.saveTableColumns(tv.getDataSourceType(), tableID);
						TableStructureEventDispatcher.getInstance(tableID).tableStructureChanged(true, null);
					}
				}
			});
		}
		final MenuItem itemResetColumns = new MenuItem(menu, SWT.PUSH);
		Messages.setLanguageText(itemResetColumns, "table.columns.reset");
		itemResetColumns.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event e) {
				String tableID = tv.getTableID();
				TableColumnManager tcm = TableColumnManager.getInstance();
				tcm.resetColumns(tv.getDataSourceType(), tableID);
			}
		});

		final MenuItem itemChangeTable = new MenuItem(menu, SWT.PUSH);
		Messages.setLanguageText(itemChangeTable,
				"MyTorrentsView.menu.editTableColumns");
		Utils.setMenuItemImage(itemChangeTable, "columns");

		itemChangeTable.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event e) {
				showColumnEditor();
			}
		});

		menu.setData("column", column);

		if (column == null) {
			return;
		}

		String sColumnName = column.getName();
		if (sColumnName != null) {
			Object[] listeners = listenersMenuFill.toArray();
			for (int i = 0; i < listeners.length; i++) {
				TableViewSWTMenuFillListener l = (TableViewSWTMenuFillListener) listeners[i];
				l.addThisColumnSubMenu(sColumnName, menu);
			}
		}

		final MenuItem at_item = new MenuItem(menu, SWT.CHECK);
		Messages.setLanguageText(at_item,
				"MyTorrentsView.menu.thisColumn.autoTooltip");
		at_item.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event e) {
				TableColumnCore tcc = (TableColumnCore) menu.getData("column");
				tcc.setAutoTooltip(at_item.getSelection());
				tcc.invalidateCells();
			}
		});
		at_item.setSelection(column.doesAutoTooltip());


		// Add Plugin Context menus..
		TableContextMenuItem[] items = column.getContextMenuItems(TableColumnCore.MENU_STYLE_HEADER);
		if (items.length > 0) {
			new MenuItem(menu, SWT.SEPARATOR);

			MenuBuildUtils.addPluginMenuItems(items, menu, true, true,
					new MenuBuildUtils.MenuItemPluginMenuControllerImpl(
							tv.getSelectedDataSources(true)));

		}
	}

	public void addMenuFillListener(TableViewSWTMenuFillListener l) {
		listenersMenuFill.add(l);
	}

}
