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

package org.gudy.azureus2.ui.swt.views.table.painted;

import java.util.LinkedHashMap;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;

import org.gudy.azureus2.core3.config.ParameterListener;
import org.gudy.azureus2.core3.util.*;
import org.gudy.azureus2.plugins.ui.tables.TableCell;
import org.gudy.azureus2.plugins.ui.tables.TableColumn;
import org.gudy.azureus2.ui.swt.Utils;
import org.gudy.azureus2.ui.swt.mainwindow.Colors;
import org.gudy.azureus2.ui.swt.shells.GCStringPrinter;
import org.gudy.azureus2.ui.swt.views.table.TableCellSWT;
import org.gudy.azureus2.ui.swt.views.table.TableViewSWT;
import org.gudy.azureus2.ui.swt.views.table.impl.TableCellSWTBase;
import org.gudy.azureus2.ui.swt.views.table.impl.TableRowSWTBase;
import org.gudy.azureus2.ui.swt.views.table.utils.TableColumnSWTUtils;

import com.aelitis.azureus.ui.common.table.TableCellCore;
import com.aelitis.azureus.ui.common.table.TableColumnCore;
import com.aelitis.azureus.ui.common.table.TableRowCore;
import com.aelitis.azureus.ui.swt.utils.FontUtils;

public class TableRowPainted
	extends TableRowSWTBase
{
	private static final boolean DEBUG_SUBS = false;

	private Point drawOffset = new Point(0, 0);

	private int numSubItems;

	private Object[] subDataSources;

	private TableRowPainted[] subRows;

	private Object subRows_sync;

	private int subRowsHeight;

	private TableCellCore cellSort;

	final static public Color[] alternatingColors = new Color[] {
		null,
		Colors.colorAltRow
	};

	static{
		Colors.getInstance().addColorsChangedListener(
			new ParameterListener() {

				public void parameterChanged(String parameterName) {
					alternatingColors[1] = Colors.colorAltRow;
				}
			});
	}

	private int height = 0;

	private boolean initializing = true;

	private Color colorFG = null;

	public TableRowPainted(TableRowCore parentRow, TableViewPainted tv,
			Object dataSource, boolean triggerHeightChange) {
		// in theory, TableRowPainted could have its own sync
		// but in practice, I end up calling code within the sync which inevitably
		// calls the TableView and causes locks.  So, use the TableView's sync!

		super(tv.getSyncObject(), parentRow, tv, dataSource);
		subRows_sync = tv.getSyncObject();

		TableColumnCore sortColumn = tv.getSortColumn();
		if (sortColumn != null
				&& (parentRow == null || sortColumn.handlesDataSourceType(getDataSource(
						false).getClass()))) {
			cellSort = new TableCellPainted(TableRowPainted.this, sortColumn,
					sortColumn.getPosition());
		}

		if (height == 0) {
			setHeight(tv.getRowDefaultHeight(), false);
		}
		initializing = false;
		if (triggerHeightChange) {
			heightChanged(0, height);
		}
	}

	private void buildCells() {
		//debug("buildCells " + Debug.getCompressedStackTrace());
		TableColumnCore[] visibleColumns = getView().getVisibleColumns();
		if (visibleColumns == null) {
			return;
		}
		synchronized (lock) {
			mTableCells = new LinkedHashMap<String, TableCellCore>(
					visibleColumns.length, 1);

			TableColumn currentSortColumn = null;
			if (cellSort != null) {
				currentSortColumn = cellSort.getTableColumn();
			}
			TableRowCore parentRow = getParentRowCore();
			// create all the cells for the column
			for (int i = 0; i < visibleColumns.length; i++) {
				if (visibleColumns[i] == null) {
					continue;
				}

				if (parentRow != null
						&& !visibleColumns[i].handlesDataSourceType(getDataSource(false).getClass())) {
					mTableCells.put(visibleColumns[i].getName(), null);
					continue;
				}

				//System.out.println(dataSource + ": " + tableColumns[i].getName() + ": " + tableColumns[i].getPosition());
				TableCellCore cell = (currentSortColumn != null && visibleColumns[i].equals(currentSortColumn))
						? cellSort : new TableCellPainted(TableRowPainted.this,
								visibleColumns[i], i);
				mTableCells.put(visibleColumns[i].getName(), cell);
				//if (i == 10) cell.bDebug = true;
			}
		}
	}

	private void destroyCells() {
		synchronized (lock) {
			if (mTableCells != null) {
				for (TableCellCore cell : mTableCells.values()) {
					if (cell != null && cell != cellSort) {
						if (!cell.isDisposed()) {
							cell.dispose();
						}
					}
				}
				mTableCells = null;
			}
		}
	}

	public TableViewPainted getViewPainted() {
		return (TableViewPainted) getView();
	}

	/**
	 * @param gc GC to draw to
	 * @param drawBounds Area that needs redrawing
	 * @param rowStartX where in the GC this row's x-axis starts
	 * @param rowStartY where in the GC this row's y-axis starts
	 * @param pos
	 */
	public void swt_paintGC(GC gc, Rectangle drawBounds, int rowStartX,
			int rowStartY, int pos, boolean isTableSelected, boolean isTableEnabled) {
		if (isRowDisposed() || gc == null || gc.isDisposed() || drawBounds == null) {
			return;
		}
		// done by caller
		//if (!drawBounds.intersects(rowStartX, rowStartY, 9999, getHeight())) {
		//	return;
		//}

		TableColumnCore[] visibleColumns = getView().getVisibleColumns();
		if (visibleColumns == null || visibleColumns.length == 0) {
			return;
		}

		boolean isSelected = isSelected();
		boolean isSelectedNotFocused = isSelected && !isTableSelected;

		Color origBG = gc.getBackground();
		Color origFG = gc.getForeground();

		Color fg = getForeground();
		Color shadowColor = null;

		Color altColor;
		Color bg;
		if (isTableEnabled) {
  		altColor = alternatingColors[pos >= 0 ? pos % 2 : 0];
  		if (altColor == null) {
  			altColor = gc.getDevice().getSystemColor(SWT.COLOR_LIST_BACKGROUND);
  		}
  		if (isSelected) {
  			Color color;
  			color = gc.getDevice().getSystemColor(SWT.COLOR_LIST_SELECTION);
  			gc.setBackground(color);
  		} else {
  			gc.setBackground(altColor);
  		}

  		bg = getBackground();
  		if (bg == null) {
  			bg = gc.getBackground();
  		} else {
  			gc.setBackground(bg);
  		}

  		if (isSelected) {
  			shadowColor = fg;
  			fg = gc.getDevice().getSystemColor(SWT.COLOR_LIST_SELECTION_TEXT);
  		} else {
  			if (fg == null) {
  				fg = gc.getDevice().getSystemColor(SWT.COLOR_LIST_FOREGROUND);
  			}
  		}
		} else {
			Device device = gc.getDevice();
			altColor = device.getSystemColor(SWT.COLOR_WIDGET_BACKGROUND);
			if (isSelected) {
				bg = device.getSystemColor(SWT.COLOR_WIDGET_LIGHT_SHADOW);
			} else {
				bg = altColor;
			}
			gc.setBackground(bg);

			fg = device.getSystemColor(SWT.COLOR_WIDGET_NORMAL_SHADOW);
		}
		gc.setForeground(fg);

		int rowAlpha = getAlpha();
		Font font = gc.getFont();
		Rectangle clipping = gc.getClipping();

		int x = rowStartX;
		//boolean paintedRow = false;
		synchronized (lock) {
			if (mTableCells == null) {
				// not sure if this is wise, but visibleRows seems to keep up to date.. so, it must be ok!
				setShown(true, true);
			}
			if (mTableCells != null) {
				for (TableColumn tc : visibleColumns) {
					TableCellCore cell = mTableCells.get(tc.getName());
					int w = tc.getWidth();
					if (!(cell instanceof TableCellPainted) || cell.isDisposed()) {
						gc.fillRectangle(x, rowStartY, w, getHeight());
						x += w;
						continue;
					}
					TableCellPainted cellSWT = (TableCellPainted) cell;
					Rectangle r = new Rectangle(x, rowStartY, w, getHeight());
					cellSWT.setBoundsRaw(r);
					if (drawBounds.intersects(r)) {
						//paintedRow = true;
						gc.setAlpha(255);
						if (isSelectedNotFocused) {
							gc.setBackground(altColor);
							gc.fillRectangle(r);
							gc.setAlpha(100);
							gc.setBackground(bg);
							gc.fillRectangle(r);
						} else {
							gc.setBackground(bg);
							gc.fillRectangle(r);
							if (isSelected) {
  							gc.setAlpha(80);
  							gc.setForeground(altColor);
  							gc.fillGradientRectangle(r.x, r.y, r.width, r.height, true);
  							gc.setForeground(fg);
							}
						}
						gc.setAlpha(rowAlpha);
						if (swt_paintCell(gc, cellSWT.getBounds(), cellSWT, shadowColor)) {
							// row color may have changed; this would update the color
							// for all new cells.  However, setting color triggers a
							// row redraw that will fix up the UI
							//Color fgNew = getForeground();
							//if (fgNew != null && fgNew != fg) {
							//	fg = fgNew;
							//}
							gc.setBackground(bg);
							gc.setForeground(fg);
							gc.setFont(font);
							Utils.setClipping(gc, clipping);
						}
						if (DEBUG_ROW_PAINT) {
							((TableCellSWTBase) cell).debug("painted "
									+ (cell.getVisuallyChangedSinceRefresh() ? "VC" : "!P")
									+ " @ " + r);
						}
					} else {
						if (DEBUG_ROW_PAINT) {
							((TableCellSWTBase) cell).debug("Skip paintItem; no intersects; r="
									+ r
									+ ";dB="
									+ drawBounds
									+ " from "
									+ Debug.getCompressedStackTrace(4));
						}
					}

					x += w;
				}
			}
			int w = drawBounds.width - x;
			if (w > 0) {
				Rectangle r = new Rectangle(x, rowStartY, w, getHeight());
				if (clipping.intersects(r)) {
  				gc.setAlpha(255);
  				if (isSelectedNotFocused) {
  					gc.setBackground(altColor);
  					gc.fillRectangle(r);
  					gc.setAlpha(100);
  					gc.setBackground(bg);
  					gc.fillRectangle(r);
  				} else {
  					gc.fillRectangle(r);
  					if (isSelected) {
  						gc.setAlpha(80);
  						gc.setForeground(altColor);
  						gc.fillGradientRectangle(r.x, r.y, r.width, r.height, true);
  						gc.setForeground(fg);
  					}
  				}
  				gc.setAlpha(rowAlpha);
				}
			}

		} //synchronized (lock)

		//if (paintedRow) {
		//	debug("Paint " + e.x + "x" + e.y + " " + e.width + "x" + e.height + ".." + e.count + ";clip=" + e.gc.getClipping() +";drawOffset=" + drawOffset + " via " + Debug.getCompressedStackTrace());
		//}

		if (isFocused()) {
			gc.setAlpha(40);
			gc.setForeground(origFG);
			gc.setLineDash(new int[] { 1, 2 });
			gc.drawRectangle(rowStartX, rowStartY,
					getViewPainted().getClientArea().width - 1, getHeight() - 1);
			gc.setLineStyle(SWT.LINE_SOLID);
		}

		gc.setAlpha(255);
		gc.setBackground(origBG);
		gc.setForeground(origFG);
	}

	private boolean swt_paintCell(GC gc, Rectangle cellBounds,
			TableCellSWTBase cell, Color shadowColor) {
		// Only called from swt_PaintGC, so we can assume GC, cell are valid
		if (cellBounds == null) {
			return false;
		}

		boolean gcChanged = false;
		try {

			gc.setTextAntialias(SWT.DEFAULT);

			TableViewSWT<?> view = (TableViewSWT<?>) getView();

			TableColumnCore column = (TableColumnCore) cell.getTableColumn();
			view.invokePaintListeners(gc, this, column, cellBounds);

			int fontStyle = getFontStyle();
			Font oldFont = null;
			if (fontStyle == SWT.BOLD) {
				oldFont = gc.getFont();
				gc.setFont(FontUtils.getAnyFontBold(gc));
				gcChanged = true;
			}

			if (!cell.isUpToDate()) {
				//System.out.println("R " + rowIndex + ":" + iColumnNo);
				cell.refresh(true, true);
				//return;
			}

			String text = cell.getText();

			Color fg = cell.getForegroundSWT();
			if (fg != null) {
				gcChanged = true;
				if (isSelected()) {
					shadowColor = fg;
				} else {
					gc.setForeground(fg);
				}
			}
			Color bg = cell.getBackgroundSWT();
			if (bg != null) {
				gcChanged = true;
				gc.setBackground(bg);
			}

			//if (cell.getTableColumn().getClass().getSimpleName().equals("ColumnUnopened")) {
			//	System.out.println("FOOO" + cell.needsPainting());
			//}
			if (cell.needsPainting()) {
				Image graphicSWT = cell.getGraphicSWT();
				if (graphicSWT != null && !graphicSWT.isDisposed()) {
					Rectangle imageBounds = graphicSWT.getBounds();
					Rectangle graphicBounds = new Rectangle(cellBounds.x, cellBounds.y,
							cellBounds.width, cellBounds.height);
					if (cell.getFillCell()) {
						if (!graphicBounds.isEmpty()) {
							gc.setAdvanced(true);
							//System.out.println(imageBounds + ";" + graphicBounds);
							gc.drawImage(graphicSWT, 0, 0, imageBounds.width,
									imageBounds.height, graphicBounds.x, graphicBounds.y,
									graphicBounds.width, graphicBounds.height);
						}
					} else {

						if (imageBounds.width < graphicBounds.width) {
							int alignment = column.getAlignment();
							if ((alignment & TableColumn.ALIGN_CENTER) > 0) {
								graphicBounds.x += (graphicBounds.width - imageBounds.width) / 2;
							} else if ((alignment & TableColumn.ALIGN_TRAIL) > 0) {
								graphicBounds.x = (graphicBounds.x + graphicBounds.width)
										- imageBounds.width;
							}
						}

						if (imageBounds.height < graphicBounds.height) {
							graphicBounds.y += (graphicBounds.height - imageBounds.height) / 2;
						}

						gc.drawImage(graphicSWT, graphicBounds.x, graphicBounds.y);
					}

				}
				cell.doPaint(gc);
				gcChanged = true;
			}
			if (text.length() > 0) {
				int ofsx = 0;
				Image image = cell.getIcon();
				Rectangle imageBounds = null;
				if (image != null && !image.isDisposed()) {
					imageBounds = image.getBounds();
					int ofs = imageBounds.width;
					ofsx += ofs;
					cellBounds.x += ofs;
					cellBounds.width -= ofs;
				}
				//System.out.println("PS " + getIndex() + ";" + cellBounds + ";" + cell.getText());
				int style = TableColumnSWTUtils.convertColumnAlignmentToSWT(column.getAlignment());
				if (cellBounds.height > 20) {
					style |= SWT.WRAP;
				}
				int textOpacity = cell.getTextAlpha();
				//gc.setFont(getRandomFont());
				//textOpacity = 130;
				if (textOpacity < 255) {
					//gc.setTextAntialias(SWT.ON);
					gc.setAlpha(textOpacity);
					gcChanged = true;
				} else if (textOpacity > 255) {
					gc.setFont(FontUtils.getAnyFontBold(gc));
					//gc.setTextAntialias(SWT.ON);
					//gc.setAlpha(textOpacity & 255);
					gcChanged = true;
				}
				// put some padding on text
				ofsx += 6;
				cellBounds.x += 3;
				cellBounds.width -= 6;
				cellBounds.y += 2;
				cellBounds.height -= 4;
				if (!cellBounds.isEmpty()) {
					GCStringPrinter sp = new GCStringPrinter(gc, text, cellBounds, true,
							cellBounds.height > 20, style);

					boolean fit;
					if (shadowColor != null) {
						Color oldFG = gc.getForeground();
						gc.setForeground(shadowColor);

						cellBounds.x += 1;
						cellBounds.y += 1;
						int alpha = gc.getAlpha();
						gc.setAlpha(0x40);
						sp.printString(gc, cellBounds, style);
						gc.setAlpha(alpha);
						gc.setForeground(oldFG);

						cellBounds.x -= 1;
						cellBounds.y -= 1;
						fit = sp.printString2(gc, cellBounds, style);
					} else {
						fit = sp.printString();
					}

					if (fit) {

						cell.setDefaultToolTip(null);
					} else {

						cell.setDefaultToolTip(text);
					}

					Point size = sp.getCalculatedSize();
					size.x += ofsx;

					TableColumn tableColumn = cell.getTableColumn();
					if (tableColumn != null && tableColumn.getPreferredWidth() < size.x) {
						tableColumn.setPreferredWidth(size.x);
					}

					if (imageBounds != null) {
						int drawToY = cellBounds.y + (cellBounds.height / 2)
								- (imageBounds.height / 2);

						boolean hack_adv = Constants.isWindows8OrHigher && gc.getAdvanced();

						if (hack_adv) {
								// problem with icon transparency on win8
							gc.setAdvanced(false);
						}
						if ((style & SWT.RIGHT) != 0) {
							int drawToX = cellBounds.x + cellBounds.width - size.x;
							gc.drawImage(image, drawToX, drawToY);
						} else {
							if (imageBounds.height > cellBounds.height) {
								float pct = cellBounds.height / (float) imageBounds.height;
  							gc.drawImage(image, 0, 0, imageBounds.width, imageBounds.height,
  									cellBounds.x - imageBounds.width - 3, cellBounds.y,
  									(int) (imageBounds.width * pct),
  									(int) (imageBounds.height * pct));
							} else {
  							gc.drawImage(image, cellBounds.x - imageBounds.width - 3, drawToY);
							}
						}
						if (hack_adv) {
							gc.setAdvanced(true);
						}
					}
				} else {
					cell.setDefaultToolTip(null);
				}
			}
			cell.clearVisuallyChangedSinceRefresh();

			if (oldFont != null) {
				gc.setFont(oldFont);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return gcChanged;
	}

	private Font getRandomFont() {
		FontData[] fontList = Display.getDefault().getFontList(null, (Math.random() > 0.5));
		FontData fontData = fontList[(int)(Math.random() * fontList.length)];
		fontData.setStyle((int)(Math.random() * 4));
		fontData.height = (float) (Math.random() * 50f);
		return new Font(Display.getDefault(), fontData);
	}

	@Override
	public List<TableCellCore> refresh(boolean bDoGraphics, boolean bVisible) {
		final List<TableCellCore> invalidCells = super.refresh(bDoGraphics,
				bVisible);
		//System.out.print(SystemTime.getCurrentTime() + "] InvalidCells: ");
		if (invalidCells.size() > 0) {
			//for (TableCellCore cell : invalidCells) {
			//	System.out.print(cell.getTableColumn().getName() + ", ");
			//}
			//System.out.println();
			Utils.execSWTThread(new AERunnable() {
				public void runSupport() {
					Composite composite = getViewPainted().getComposite();
					if (composite == null || composite.isDisposed() || !isVisible()) {
						return;
					}
					boolean allCells;
					synchronized(lock) {
						allCells = (mTableCells != null)&& invalidCells.size() == mTableCells.size();
					}

					if (allCells) {
						getViewPainted().swt_updateCanvasImage(getDrawBounds(), false);
					} else {
						Rectangle drawBounds = getDrawBounds();
						for (Object o : invalidCells) {
							if (o instanceof TableCellPainted) {
								TableCellPainted cell = (TableCellPainted) o;
								Rectangle bounds = cell.getBoundsRaw();
								if (bounds != null) {
									// cell's rawbounds yPos can get out of date
									// cell's rawbounds gets updated via swt_updateCanvasImage, via swt_paintGC
									bounds.y = drawBounds.y;
									bounds.height = drawBounds.height;
									cell.setBoundsRaw(bounds);
									getViewPainted().swt_updateCanvasImage(bounds, false);
								}
							}
						}
					}
				}
			});
			//} else {
			//System.out.println("NONE");
		}
		return invalidCells;
	}

	public void redraw(boolean doChildren) {
		redraw(doChildren, false);
	}

	public void redraw(boolean doChildren, boolean immediateRedraw) {
		if (isRowDisposed()) {
			return;
		}
		getViewPainted().redrawRow(this, immediateRedraw);

		if (!doChildren) {
			return;
		}
		synchronized (subRows_sync) {
			if (subRows != null) {
				for (TableRowPainted subrow : subRows) {
					subrow.redraw();
				}
			}
		}
	}

	protected void debug(String s) {
		AEDiagnosticsLogger diag_logger = AEDiagnostics.getLogger("table");
		String prefix = SystemTime.getCurrentTime() + ":" + getTableID() + ": r"
				+ getIndex();
		if (getParentRowCore() != null) {
			prefix += "of" + getParentRowCore().getIndex();
		}
		prefix += ": ";
		diag_logger.log(prefix + s);

		System.out.println(prefix + s);
	}

	/* (non-Javadoc)
	 * @see org.gudy.azureus2.ui.swt.views.table.impl.TableRowSWTBase#getBounds()
	 */
	@Override
	public Rectangle getBounds() {
		//TableViewPainted view = (TableViewPainted) getView();
		//Rectangle clientArea = view.getClientArea();
		return new Rectangle(0, drawOffset.y, 9990, getHeight());
	}

	public Rectangle getDrawBounds() {
		TableViewPainted view = (TableViewPainted) getView();
		Rectangle clientArea = view.getClientArea();
		int offsetX = TableViewPainted.DIRECT_DRAW ? -clientArea.x : 0;
		Rectangle bounds = new Rectangle(offsetX, drawOffset.y - clientArea.y, 9990,
				getHeight());
		return bounds;
	}

	public int getFullHeight() {
		int h = getHeight();
		if (numSubItems > 0 && isExpanded()) {
			h += subRowsHeight;
		}
		return h;
	}

	public Point getDrawOffset() {
		return drawOffset;
	}

	/* (non-Javadoc)
	 * @see org.gudy.azureus2.ui.swt.views.table.impl.TableRowSWTBase#heightChanged(int, int)
	 */
	public void heightChanged(int oldHeight, int newHeight) {
		getViewPainted().rowHeightChanged(this, oldHeight, newHeight);
		TableRowCore row = getParentRowCore();
		if (row instanceof TableRowPainted) {
			((TableRowPainted) row).subRowHeightChanged(this, oldHeight, newHeight);
		}
	}

	public void subRowHeightChanged(TableRowCore row, int oldHeight, int newHeight) {
		subRowsHeight += (newHeight - oldHeight);
	}

	public boolean setDrawOffset(Point drawOffset) {
		if (drawOffset.equals(this.drawOffset)) {
			return false;
		}
		this.drawOffset = drawOffset;

		return true;
	}

	@Override
	public void setWidgetSelected(boolean selected) {
		redraw(false, true);
	}

	@Override
	public boolean setShown(boolean b, boolean force) {
		if (b == wasShown && !force) {
			return false;
		}

		synchronized (lock) {
			if (b && mTableCells == null) {
				buildCells();
			}
		}

		boolean ret = super.setShown(b, force);

		if (b) {
			invalidate();
			redraw(false, false);
		}

		synchronized (lock) {
			if (!b && mTableCells != null) {
				destroyCells();
			}
		}

		return ret;
	}

	@Override
	public void delete() {
		super.delete();

		synchronized (lock) {
			if (cellSort != null && !cellSort.isDisposed()) {

				cellSort.dispose();

				cellSort = null;
			}
		}

		deleteExistingSubRows();
	}

	private void deleteExistingSubRows() {
		synchronized (subRows_sync) {
			if (subRows != null) {
				for (TableRowPainted subrow : subRows) {
					subrow.delete();
				}
			}
			subRows = null;
		}
	}

	public void setSubItemCount(int length) {
		numSubItems = length;
		if (isExpanded() && subDataSources.length == length) {
			if (DEBUG_SUBS) {
				debug("setSubItemCount to " + length);
			}

			deleteExistingSubRows();
			TableRowPainted[] newSubRows = new TableRowPainted[length];
			TableViewPainted tv = getViewPainted();
			int h = 0;
			for (int i = 0; i < newSubRows.length; i++) {
				newSubRows[i] = new TableRowPainted(this, tv, subDataSources[i], false);
				newSubRows[i].setTableItem(i, false);
				h += newSubRows[i].getHeight();
			}

			int oldHeight = getFullHeight();
			subRowsHeight = h;
			getViewPainted().rowHeightChanged(this, oldHeight, getFullHeight());
			getViewPainted().triggerListenerRowAdded(newSubRows);

			subRows = newSubRows;
		}
	}

	public int getSubItemCount() {
		return numSubItems;
	}

	/* (non-Javadoc)
	 * @see com.aelitis.azureus.ui.common.table.TableRowCore#linkSubItem(int)
	 */
	public TableRowCore linkSubItem(int indexOf) {
		// Not used by TableViewPainted
		return null;
	}

	public void setSubItems(Object[] datasources) {
		deleteExistingSubRows();
		synchronized (subRows_sync) {
			subDataSources = datasources;
			subRowsHeight = 0;
			setSubItemCount(datasources.length);
		}
	}

	public TableRowCore[] getSubRowsWithNull() {
		synchronized (subRows_sync) {
			return subRows == null ? new TableRowCore[0] : subRows;
		}
	}

	public void removeSubRow(Object datasource) {
		synchronized (subRows_sync) {

			for (int i = 0; i < subDataSources.length; i++) {
				Object ds = subDataSources[i];
				if (ds == datasource) { // use .equals instead?
					TableRowPainted rowToDel = subRows[i];
					TableRowPainted[] newSubRows = new TableRowPainted[subRows.length - 1];
					System.arraycopy(subRows, 0, newSubRows, 0, i);
					System.arraycopy(subRows, i + 1, newSubRows, i, subRows.length - i
							- 1);
					subRows = newSubRows;

					Object[] newDatasources = new Object[subRows.length];
					System.arraycopy(subDataSources, 0, newDatasources, 0, i);
					System.arraycopy(subDataSources, i + 1, newDatasources, i,
							subDataSources.length - i - 1);
					subDataSources = newDatasources;

					rowToDel.delete();

					setSubItemCount(subRows.length);

					break;
				}
			}
		}
	}

	@Override
	public void setExpanded(boolean b) {
		if (canExpand()) {
			int oldHeight = getFullHeight();
			super.setExpanded(b);
			synchronized (subRows_sync) {
				TableRowPainted[] newSubRows = null;
				if (b && (subRows == null || subRows.length != numSubItems)
						&& subDataSources != null && subDataSources.length == numSubItems) {
					if (DEBUG_SUBS) {
						debug("building subrows " + numSubItems);
					}

					deleteExistingSubRows();
					newSubRows = new TableRowPainted[numSubItems];
					TableViewPainted tv = getViewPainted();
					int h = 0;
					for (int i = 0; i < newSubRows.length; i++) {
						newSubRows[i] = new TableRowPainted(this, tv, subDataSources[i],
								false);
						newSubRows[i].setTableItem(i, false);
						h += newSubRows[i].getHeight();
					}

					subRowsHeight = h;

					subRows = newSubRows;
				}

				getViewPainted().rowHeightChanged(this, oldHeight, getFullHeight());

				if (newSubRows != null) {
					getViewPainted().triggerListenerRowAdded(newSubRows);
				}

			}
			if (isVisible()) {
				getViewPainted().visibleRowsChanged();
				getViewPainted().redrawTable();
			}
		}
	}

	public TableRowCore getSubRow(int pos) {
		synchronized (subRows_sync) {
			if (subRows == null) {
				return null;
			}
			if (pos >= 0 && pos < subRows.length) {
				return subRows[pos];
			}
			return null;
		}
	}

	/* (non-Javadoc)
	 * @see org.gudy.azureus2.ui.swt.views.table.impl.TableRowSWTBase#setForeground(org.eclipse.swt.graphics.Color)
	 */
	@Override
	public boolean setForeground(Color color) {
		if (isRowDisposed()) {
			return false;
		}
		if (color == colorFG || (color != null && color.equals(colorFG))
				|| (colorFG != null && colorFG.equals(color))) {
			return false;
		}

		colorFG = color;
		//delays redraw until after.  Could use execSWTThreadLater
		Utils.getOffOfSWTThread(new AERunnable() {
			public void runSupport() {
				redraw(false, false);
			}
		});

		return true;
	}

	@Override
	public boolean setIconSize(Point pt) {
		//TODO
		return false;
	}

	@Override
	public Color getForeground() {
		return colorFG;
	}

	@Override
	public Color getBackground() {
		return null;
	}

	@Override
	public void setBackgroundImage(Image image) {
		//TODO
	}

	/* (non-Javadoc)
	 * @see com.aelitis.azureus.ui.common.table.TableRowCore#getHeight()
	 */
	public int getHeight() {
		return height == 0 ? getView().getRowDefaultHeight() : height;
	}

	/* (non-Javadoc)
	 * @see com.aelitis.azureus.ui.common.table.TableRowCore#setHeight(int)
	 */
	public boolean setHeight(int newHeight) {
		TableRowCore parentRowCore = getParentRowCore();
		boolean trigger = parentRowCore == null || parentRowCore.isExpanded();

		return setHeight(newHeight, trigger);
	}

	public boolean setHeight(int newHeight, boolean trigger) {
		if (height == newHeight) {
			return false;
		}
		int oldHeight = height;
		height = newHeight;
		if (trigger && !initializing) {
			heightChanged(oldHeight, newHeight);
		}

		return true;
	}

	@Override
	public TableCellCore getTableCellCore(String name) {
		if (isRowDisposed()) {
			return null;
		}
		synchronized (lock) {
			if (mTableCells == null) {
				if (cellSort != null && !cellSort.isDisposed()
						&& cellSort.getTableColumn().getName().equals(name)) {
					return cellSort;
				} else {
					return null;
				}
			}
			return mTableCells.get(name);
		}
	}

	@Override
	public TableCellSWT getTableCellSWT(String name) {
		TableCellCore cell = getTableCellCore(name);
		return (cell instanceof TableCellSWT) ? (TableCellSWT) cell : null;
	}

	@Override
	public TableCell getTableCell(String field) {
		return getTableCellCore(field);
	}

	public TableCellCore getSortColumnCell(String hint) {
		synchronized (lock) {
			return cellSort;
		}
	}

	public void setSortColumn(String columnID) {
		synchronized (lock) {

			if (mTableCells == null) {
				if (cellSort != null && !cellSort.isDisposed()) {
					if (cellSort.getTableColumn().getName().equals(columnID)) {
						return;
					}
					cellSort.dispose();
					cellSort = null;
				}
				TableColumnCore sortColumn = (TableColumnCore) getView().getTableColumn(
						columnID);
				if (getParentRowCore() == null
						|| sortColumn.handlesDataSourceType(getDataSource(false).getClass())) {
					cellSort = new TableCellPainted(TableRowPainted.this, sortColumn,
							sortColumn.getPosition());
				} else {
					cellSort = null;
				}
			} else {
				cellSort = mTableCells.get(columnID);
			}
		}
	}
}
