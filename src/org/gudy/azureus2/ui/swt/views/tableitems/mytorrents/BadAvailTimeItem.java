/*
 * Created on 05-May-2006
 * Created by Paul Gardner
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 */

package org.gudy.azureus2.ui.swt.views.tableitems.mytorrents;

import java.util.Locale;

import org.gudy.azureus2.core3.download.DownloadManager;
import org.gudy.azureus2.core3.download.DownloadManagerState;
import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.peer.PEPeerManager;
import org.gudy.azureus2.plugins.download.Download;
import org.gudy.azureus2.plugins.ui.tables.TableCell;
import org.gudy.azureus2.plugins.ui.tables.TableColumnInfo;
import org.gudy.azureus2.ui.swt.views.table.utils.TableColumnCreator;
import org.gudy.azureus2.ui.swt.views.tableitems.ColumnDateSizer;

public class BadAvailTimeItem
	extends ColumnDateSizer
{
	public static final Class DATASOURCE_TYPE = Download.class;

	public static final String COLUMN_ID = "bad_avail_time";
	private static String	now_string;

	static{

		MessageText.addAndFireListener(
			new MessageText.MessageTextListener() {
				public void localeChanged(
					Locale old_locale,
					Locale new_locale ) {
					now_string = MessageText.getString("SpeedView.stats.now");
				}
			});
	}

	public BadAvailTimeItem(String sTableID) {
		super(DATASOURCE_TYPE, COLUMN_ID, TableColumnCreator.DATE_COLUMN_WIDTH, sTableID);
		setRefreshInterval(INTERVAL_LIVE);
	}

	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_SWARM,
			CAT_TIME,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_ADVANCED);
	}

	public void refresh(TableCell cell, long timestamp) {
		DownloadManager dm = (DownloadManager) cell.getDataSource();
		long value = dm==null?-1:dm.getStats().getAvailWentBadTime();

		if (value == 0) {

				// zero means no recorded last bad availability time (bad=transition from >=1 -> < 1)

			PEPeerManager pm = dm.getPeerManager();

			if (pm  == null || pm.getMinAvailability() < 1.0) {

				long stopped = dm.getDownloadState().getLongAttribute(DownloadManagerState.AT_TIME_STOPPED);

				if (stopped > 0) {

					value = stopped;

				} else {

					value = -1;
				}

			} else {

				value = -2;
			}
		}

		if (value > 0) {
			super.refresh(cell, value);
		} else {
			String text;

			if (value == -2) {
				text = now_string;
			} else {
				text = "";
			}

			if (!cell.setSortValue(value) && cell.isValid()) {
				return;
			}

			cell.setText(text);
		}
	}
}
