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

package org.gudy.azureus2.ui.swt.views.tableitems.files;

import org.gudy.azureus2.core3.disk.DiskManagerFileInfo;
import org.gudy.azureus2.core3.util.DisplayFormatters;
import org.gudy.azureus2.plugins.ui.tables.*;
import org.gudy.azureus2.ui.swt.views.table.CoreTableColumnSWT;

/**
 *
 * @author TuxPaper
 * @since 2.0.8.5
 */
public class PercentItem
	extends CoreTableColumnSWT
	implements TableCellRefreshListener
{
	/** Default Constructor */
	public PercentItem() {
		super("%", ALIGN_TRAIL, POSITION_LAST, 60, TableManager.TABLE_TORRENT_FILES);
		setRefreshInterval(INTERVAL_LIVE);
		setMinWidthAuto(true);
	}

	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_PROGRESS,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
	}

	public void refresh(TableCell cell) {

		DiskManagerFileInfo fileInfo = (DiskManagerFileInfo) cell.getDataSource();

		long percent = 0;

		if (fileInfo != null) {
			long bytesDownloaded = fileInfo.getDownloaded();

			if (bytesDownloaded < 0) {

				percent = -1; // unknown skeleton value

			} else {
				long length = fileInfo.getLength();

				if (length != 0) {

					percent = (1000 * bytesDownloaded) / length;

				} else {

					percent = 1000;
				}
			}

		} else {

			percent = -1; // unknown skeleton value
		}

		if (!cell.setSortValue(percent) && cell.isValid()) {

			return;
		}

		cell.setText(percent < 0 ? ""
				: DisplayFormatters.formatPercentFromThousands((int) percent));
	}
}
