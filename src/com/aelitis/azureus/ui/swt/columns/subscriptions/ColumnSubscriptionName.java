/**
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

package com.aelitis.azureus.ui.swt.columns.subscriptions;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;

import org.gudy.azureus2.ui.swt.shells.GCStringPrinter;
import org.gudy.azureus2.ui.swt.views.table.CoreTableColumnSWT;
import org.gudy.azureus2.ui.swt.views.table.TableCellSWT;
import org.gudy.azureus2.ui.swt.views.table.TableCellSWTPaintListener;

import com.aelitis.azureus.core.subs.Subscription;
import com.aelitis.azureus.ui.UIFunctionsManager;
import com.aelitis.azureus.ui.mdi.MultipleDocumentInterface;
import com.aelitis.azureus.ui.swt.imageloader.ImageLoader;

import org.gudy.azureus2.core3.util.ByteFormatter;
import org.gudy.azureus2.plugins.ui.tables.*;

/**
 * @author Olivier Chalouhi
 * @created Oct 7, 2008
 *
 */
public class ColumnSubscriptionName
	extends CoreTableColumnSWT
	implements TableCellRefreshListener, TableCellSWTPaintListener, TableCellMouseListener
{
	public static String COLUMN_ID = "name";


	int imageWidth = -1;
	int imageHeight = -1;

	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_ESSENTIAL,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
	}

	/** Default Constructor */
	public ColumnSubscriptionName(String sTableID) {
		super(COLUMN_ID, POSITION_LAST, 350, sTableID);
		setRefreshInterval(INTERVAL_LIVE);
		setMinWidth(300);
	}

	public void refresh(TableCell cell) {
		String name = null;
		Subscription sub = (Subscription) cell.getDataSource();
		if (sub != null) {
			name = sub.getName();
		}
		if (name == null) {
			name = "";
		}

		if (!cell.setSortValue(name) && cell.isValid()) {
			return;
		}
	}

	public void cellPaint(GC gc, TableCellSWT cell) {
		Rectangle bounds = cell.getBounds();

		ImageLoader imageLoader = ImageLoader.getInstance();
		Image viewImage = imageLoader.getImage("ic_view");
		if (imageWidth == -1 || imageHeight == -1) {
			imageWidth = viewImage.getBounds().width;
			imageHeight = viewImage.getBounds().height;
		}

		bounds.width -= (imageWidth + 5);

		GCStringPrinter.printString(gc, cell.getSortValue().toString(), bounds,true,false,SWT.LEFT);

		Subscription sub = (Subscription) cell.getDataSource();

		if (sub != null && !sub.isSearchTemplate()) {

			gc.drawImage(viewImage, bounds.x + bounds.width, bounds.y + bounds.height / 2 - imageHeight / 2);
		}

		imageLoader.releaseImage("ic_view");

			//gc.drawText(cell.getText(), bounds.x,bounds.y);
	}

	public void cellMouseTrigger(TableCellMouseEvent event) {

		if (event.eventType == TableCellMouseEvent.EVENT_MOUSEUP
				&& event.button == 1) {
			TableCell cell = event.cell;
			int cellWidth = cell.getWidth();
			if (event.x > cellWidth - imageWidth - 5 && event.x < cellWidth - 5) {
				Subscription sub = (Subscription) cell.getDataSource();
				if (sub != null && !sub.isSearchTemplate()) {
					String key = "Subscription_" + ByteFormatter.encodeString(sub.getPublicKey());
					MultipleDocumentInterface mdi = UIFunctionsManager.getUIFunctions().getMDI();
					if (mdi != null) {
						mdi.showEntryByID(key);
					}
				}
			}
		}

	}


}
