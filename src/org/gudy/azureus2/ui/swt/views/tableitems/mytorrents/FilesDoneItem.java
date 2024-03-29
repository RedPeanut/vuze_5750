/*
 * File    : SavePathItem.java
 * Created : 01 febv. 2004
 * By      : TuxPaper
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

import org.gudy.azureus2.core3.disk.DiskManagerFileInfo;
import org.gudy.azureus2.core3.download.DownloadManager;
import org.gudy.azureus2.core3.util.StringInterner;

import org.gudy.azureus2.plugins.download.Download;
import org.gudy.azureus2.plugins.ui.tables.TableCell;
import org.gudy.azureus2.plugins.ui.tables.TableCellRefreshListener;
import org.gudy.azureus2.plugins.ui.tables.TableColumnInfo;
import org.gudy.azureus2.ui.swt.views.table.CoreTableColumnSWT;


public class FilesDoneItem
       extends CoreTableColumnSWT
       implements TableCellRefreshListener
{
	public static final Class DATASOURCE_TYPE = Download.class;

  public static final String COLUMN_ID = "filesdone";

	public FilesDoneItem(String sTableID) {
	  super(DATASOURCE_TYPE, COLUMN_ID, ALIGN_CENTER, 50, sTableID);
	  setRefreshInterval(5);
    setMinWidthAuto(true);
  }

	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_CONTENT,
			CAT_PROGRESS
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_INTERMEDIATE);
	}

  public void refresh(TableCell cell) {
    DownloadManager dm = (DownloadManager)cell.getDataSource();

    String	text = "";

    if (dm != null) {
    	int	complete 			= 0;
    	int	skipped				= 0;
    	int	skipped_complete	= 0;

    	DiskManagerFileInfo[]	files = dm.getDiskManagerFileInfo();

    	int	total	= files.length;

    	for (int i=0;i<files.length;i++) {
    		DiskManagerFileInfo	file = files[i];

    		if (file.getLength() == file.getDownloaded()) {
    			complete++;
    			if (file.isSkipped()) {
    				skipped++;
    				skipped_complete++;
    			}
    		} else if (file.isSkipped()) {
    			skipped++;
    		}
    	}

    	if (skipped == 0) {
    		text = StringInterner.intern(complete + "/" + total);
    	} else {
    		text = (complete-skipped_complete) + "(" + complete + ")/" + (total-skipped) + "(" + total + ")";
    	}
    }

    cell.setText(text);
  }
}
