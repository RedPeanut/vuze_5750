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

import org.gudy.azureus2.plugins.ui.tables.*;
import org.gudy.azureus2.ui.swt.views.table.CoreTableColumnSWT;

import org.gudy.azureus2.core3.disk.DiskManagerFileInfo;
import org.gudy.azureus2.core3.download.DownloadManager;
import org.gudy.azureus2.core3.download.DownloadManagerState;

public class FileExtensionItem
       extends CoreTableColumnSWT
       implements TableCellRefreshListener
{

  /** Default Constructor */
  public FileExtensionItem() {
    super("fileext", ALIGN_LEAD, POSITION_INVISIBLE, 50, TableManager.TABLE_TORRENT_FILES);
    setMinWidthAuto(true);
  }

	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_CONTENT,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_INTERMEDIATE);
	}

  public void refresh(TableCell cell) {
    DiskManagerFileInfo fileInfo = (DiskManagerFileInfo)cell.getDataSource();
    cell.setText(determineFileExt(fileInfo));
  }

  private static String determineFileExt(DiskManagerFileInfo fileInfo) {
	String name = (fileInfo == null) ? "" : fileInfo.getFile(true).getName();

	DownloadManager dm = fileInfo==null?null:fileInfo.getDownloadManager();

	String incomp_suffix = dm==null?null:dm.getDownloadState().getAttribute(DownloadManagerState.AT_INCOMP_FILE_SUFFIX);

	if (incomp_suffix != null && name.endsWith( incomp_suffix)) {

		name = name.substring( 0, name.length() - incomp_suffix.length());
	}

	int dot_position = name.lastIndexOf(".");
	if (dot_position == -1) {return "";}


	return name.substring(dot_position+1);
  }
}
