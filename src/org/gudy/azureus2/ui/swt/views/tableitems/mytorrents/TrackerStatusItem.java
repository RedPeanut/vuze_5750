/*
 * File    : TrackerStatusItem.java
 * Created : 24 nov. 2003
 * By      : Olivier
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

package org.gudy.azureus2.ui.swt.views.tableitems.mytorrents;

import org.gudy.azureus2.core3.download.DownloadManager;
import org.gudy.azureus2.core3.tracker.client.TRTrackerAnnouncerResponse;
import org.gudy.azureus2.core3.tracker.client.TRTrackerScraperResponse;
import org.gudy.azureus2.ui.swt.views.table.CoreTableColumnSWT;

import org.gudy.azureus2.plugins.download.Download;
import org.gudy.azureus2.plugins.ui.tables.*;

/**
 * @author Olivier
 *
 */
public class TrackerStatusItem extends CoreTableColumnSWT implements
		TableCellAddedListener, TableCellToolTipListener
{
	public static final Class DATASOURCE_TYPE = Download.class;

	public static final String COLUMN_ID = "tracker";

	public TrackerStatusItem(String sTableID) {
		super(DATASOURCE_TYPE, COLUMN_ID, ALIGN_LEAD, 90, sTableID);
		setRefreshInterval(15); // Slow update while no responses from tracker
	}

	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_TRACKER,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_INTERMEDIATE);
	}

	public void cellAdded(TableCell cell) {
		new Cell(cell);
	}

	private static class Cell extends AbstractTrackerCell {
		public Cell(TableCell cell) {
			super(cell);
		}

		public void refresh(TableCell cell) {
			super.refresh(cell);

			DownloadManager dm = (DownloadManager) cell.getDataSource();
			String status = dm == null ? "" : dm.getTrackerStatus();

			// status sometimes contains multiline text (e.g. HTML) on failure
			// - trim to end of first line break if present (see bug 1337563)

			int nl_pos = status.indexOf('\n');
			if (nl_pos >= 0)
				status = status.substring(0, nl_pos);

	    if (cell.setText(status) || !cell.isValid()) {
	    	TrackerCellUtils.updateColor(cell, dm, true);
	    }
		}

		public void scrapeResult(TRTrackerScraperResponse response) {
			checkScrapeResult(response);
		}

		public void announceResult(TRTrackerAnnouncerResponse response) {
			cell.invalidate();
		}
	}

	public void cellHover(TableCell cell) {
		DownloadManager dm = (DownloadManager) cell.getDataSource();
		cell.setToolTip(TrackerCellUtils.getTooltipText(cell, dm, true));
	}

	public void cellHoverComplete(TableCell cell) {
		cell.setToolTip(null);
	}
}
