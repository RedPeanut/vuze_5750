/*
 * Created : 11 nov. 2004
 * By      : Alon Rohter
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
import org.gudy.azureus2.core3.peer.PEPeerManager;
import org.gudy.azureus2.core3.util.DisplayFormatters;

import org.gudy.azureus2.plugins.download.Download;
import org.gudy.azureus2.plugins.ui.tables.TableCell;
import org.gudy.azureus2.plugins.ui.tables.TableCellRefreshListener;
import org.gudy.azureus2.plugins.ui.tables.TableColumnInfo;
import org.gudy.azureus2.ui.swt.views.table.CoreTableColumnSWT;



public class SwarmAverageCompletion
       extends CoreTableColumnSWT
       implements TableCellRefreshListener
{
	public static final Class DATASOURCE_TYPE = Download.class;


  public static final String COLUMN_ID = "swarm_average_completion";

	public SwarmAverageCompletion(String sTableID) {
    super(DATASOURCE_TYPE, COLUMN_ID, ALIGN_TRAIL, 70, sTableID);
    setRefreshInterval(INTERVAL_GRAPHIC);
    setMinWidthAuto(true);
  }

	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_SWARM,
			CAT_PROGRESS
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_INTERMEDIATE);
	}

  public void refresh(TableCell cell) {
    int average = -1;

    DownloadManager dm = (DownloadManager)cell.getDataSource();

    if (dm != null) {
    	PEPeerManager pm = dm.getPeerManager();

    	if (pm != null) {
    		average = pm.getAverageCompletionInThousandNotation();
    	}
    }

    if (!cell.setSortValue( average ) && cell.isValid()) {
      return;
    }

    if (average < 0) {
      cell.setText("");
    }
    else {
      cell.setText(DisplayFormatters.formatPercentFromThousands( average ));
    }
  }

}
