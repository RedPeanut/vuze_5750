/*
 * File    : UpSpeedItem.java
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

import org.eclipse.swt.graphics.Color;

import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.DisplayFormatters;
import org.gudy.azureus2.core3.util.StringInterner;
import org.gudy.azureus2.ui.swt.Utils;
import org.gudy.azureus2.ui.swt.mainwindow.Colors;
import org.gudy.azureus2.ui.swt.views.table.CoreTableColumnSWT;

import com.aelitis.azureus.plugins.startstoprules.defaultplugin.DefaultRankCalculator;
import com.aelitis.azureus.plugins.startstoprules.defaultplugin.StartStopRulesDefaultPlugin;

import org.gudy.azureus2.plugins.download.Download;
import org.gudy.azureus2.plugins.download.DownloadTypeComplete;
import org.gudy.azureus2.plugins.ui.tables.*;


/** Upload Speed column
 *
 * @author Olivier
 * @author TuxPaper (2004/Apr/17: modified to TableCellAdapter)
 */
public class UpSpeedItem
       extends CoreTableColumnSWT
       implements TableCellAddedListener
{
	public static final Class DATASOURCE_TYPE = Download.class;

  public static final String COLUMN_ID = "upspeed";

  /*
   * Default Constructor
   *
   * @param sTableID The ID of the table the cell belongs to
   */
  public UpSpeedItem(String sTableID) {
    super(DATASOURCE_TYPE, COLUMN_ID, ALIGN_TRAIL, 70, sTableID);
		setType(TableColumn.TYPE_TEXT);
    setRefreshInterval(INTERVAL_LIVE);
    setUseCoreDataSource(false);
    setMinWidthAuto(true);
  }

  public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_SHARING,
			CAT_BYTES
		});
		Class dsType = info.getColumn().getForDataSourceType();
		if (DownloadTypeComplete.class == dsType) {
			info.addCategories(new String[] {
				CAT_ESSENTIAL
			});
			info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
		}
	}

  public void cellAdded(TableCell cell) {
    cell.addRefreshListener(new RefreshListener());
  }

  private static class RefreshListener implements TableCellRefreshListener {
    private int iLastState;
    private int loop = 0;

    public void refresh(TableCell cell) {
      Download dm = (Download)cell.getDataSource();
      long value;
      int iState;
      if (dm == null) {
        iState = -1;
        value = 0;
      } else {
        iState = dm.getState();
        value = dm.getStats().getUploadAverage();
      }

      boolean bChangeColor = (++loop % 10) == 0;

      if (cell.setSortValue(value) || !cell.isValid() || (iState != iLastState)) {
				cell.setText(value == 0
						? ""
						: StringInterner.intern(DisplayFormatters.formatByteCountToKiBEtcPerSec(value)));
      	bChangeColor = true;
      }

      if (bChangeColor && dm != null) {
        changeColor(cell, dm, iState);
        loop = 0;
      }
    }

    private void changeColor(TableCell cell, Download dl, int iState) {
      try {
      	DefaultRankCalculator calc = StartStopRulesDefaultPlugin.getRankCalculator(dl);

        Color newFG = null;
        if (calc != null && dl.getState() == Download.ST_SEEDING
						&& !calc.getActivelySeeding())
					newFG = Colors.colorWarning;

        cell.setForeground(Utils.colorToIntArray(newFG));

        iLastState = iState;
      } catch (Exception e) {
      	Debug.printStackTrace(e);
      }
    }
  }
}
