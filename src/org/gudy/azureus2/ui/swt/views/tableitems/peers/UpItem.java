/*
 * File    : UpItem.java
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

package org.gudy.azureus2.ui.swt.views.tableitems.peers;

import org.gudy.azureus2.core3.util.DisplayFormatters;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.config.ParameterListener;
import org.gudy.azureus2.core3.peer.PEPeer;
import org.gudy.azureus2.plugins.ui.tables.*;
import org.gudy.azureus2.ui.swt.views.table.CoreTableColumnSWT;

/**
 *
 * @author Olivier
 * @author TuxPaper (2004/Apr/19: modified to TableCellAdapter)
 */
public class UpItem extends CoreTableColumnSWT implements TableCellRefreshListener
{
	protected static boolean separate_prot_data_stats;

	protected static boolean data_stats_only;

	static {
		COConfigurationManager.addAndFireParameterListeners(new String[] {
				"config.style.dataStatsOnly",
				"config.style.separateProtDataStats" }, new ParameterListener() {
			public void parameterChanged(String x) {
				separate_prot_data_stats = COConfigurationManager.getBooleanParameter("config.style.separateProtDataStats");
				data_stats_only = COConfigurationManager.getBooleanParameter("config.style.dataStatsOnly");
			}
		});
	}

	/** Default Constructor */
	public UpItem(String table_id) {
		super("upload", ALIGN_TRAIL, POSITION_INVISIBLE, 70, table_id);
		setRefreshInterval(INTERVAL_LIVE);
	}

	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_BYTES,
		});
	}

	public void refresh(TableCell cell) {
		PEPeer peer = (PEPeer) cell.getDataSource();
		long data_value = 0;
		long prot_value = 0;

		if (peer != null) {
			data_value = peer.getStats().getTotalDataBytesSent();
			prot_value = peer.getStats().getTotalProtocolBytesSent();
		}
		long sort_value;
		if (separate_prot_data_stats) {
			sort_value = (data_value << 24) + prot_value;
		} else if (data_stats_only) {
			sort_value = data_value;
		} else {
			sort_value = data_value + prot_value;
		}

		if (!cell.setSortValue(sort_value) && cell.isValid())
			return;

		cell.setText(DisplayFormatters.formatDataProtByteCountToKiBEtc(data_value,
				prot_value));
	}
}
