/*
 * File    : ETAItem.java
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


import org.gudy.azureus2.ui.swt.views.MyTorrentsView;
import org.gudy.azureus2.ui.swt.views.ViewUtils;
import org.gudy.azureus2.ui.swt.views.table.CoreTableColumnSWT;

import org.gudy.azureus2.core3.download.DownloadManager;
import org.gudy.azureus2.plugins.download.DownloadTypeIncomplete;

import org.gudy.azureus2.plugins.ui.tables.TableCell;
import org.gudy.azureus2.plugins.ui.tables.TableCellRefreshListener;
import org.gudy.azureus2.plugins.ui.tables.TableColumnInfo;

/**
 *
 * @author Olivier
 * @author TuxPaper (2004/Apr/17: modified to TableCellAdapter)
 */
public class ETAItem
       extends CoreTableColumnSWT
       implements TableCellRefreshListener
{
	public static final Class<?> DATASOURCE_TYPE = DownloadTypeIncomplete.class;

	public static final String COLUMN_ID = "eta";

	private ViewUtils.CustomDateFormat cdf;

	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] { CAT_ESSENTIAL });
		info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
	}

	/** Default Constructor */
	public ETAItem(String sTableID) {
		super(DATASOURCE_TYPE, COLUMN_ID, ALIGN_TRAIL, 60, sTableID);
		setRefreshInterval(INTERVAL_LIVE);

		cdf = ViewUtils.addCustomDateFormat(this);
	}

	public void refresh(TableCell cell) {
		DownloadManager dm = (DownloadManager)cell.getDataSource();
		long value = (dm == null) ? 0 : dm.getStats().getETA();
		Long sortVal = value < 0 ? null : value;

		if (!cell.setSortValue(sortVal) && cell.isValid()) {
			return;
		}

		cell.setText( ViewUtils.formatETA( value, MyTorrentsView.eta_absolute, cdf.getDateFormat()));
	}

	public void postConfigLoad() {
		super.postConfigLoad();

		cdf.update();
	}
}
