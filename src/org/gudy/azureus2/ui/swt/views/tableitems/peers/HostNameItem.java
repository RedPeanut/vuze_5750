/*
 * Created on 27-May-2004
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

package org.gudy.azureus2.ui.swt.views.tableitems.peers;

import org.gudy.azureus2.core3.peer.PEPeer;

import org.gudy.azureus2.plugins.ui.tables.TableCell;
import org.gudy.azureus2.plugins.ui.tables.TableCellRefreshListener;
import org.gudy.azureus2.plugins.ui.tables.TableColumnInfo;
import org.gudy.azureus2.ui.swt.views.table.CoreTableColumnSWT;

/**
 * @author parg
 *
 */
public class HostNameItem extends CoreTableColumnSWT implements TableCellRefreshListener {

	public static final String COLUMN_ID = "host";

	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_PEER_IDENTIFICATION,
		});
	}

	/** Default Constructor */
	public HostNameItem(String table_id) {
		super(COLUMN_ID, POSITION_INVISIBLE, 100, table_id);
		setRefreshInterval(INTERVAL_LIVE);
		setObfustication(true);
	}

	public void refresh(TableCell cell) {
		PEPeer peer = (PEPeer) cell.getDataSource();
		String addr = peer == null ? "" : peer.getIPHostName();
		if (cell.setText(addr) && !addr.equals(peer==null?"":peer.getIp())) {
			String[] l = addr.split("\\.");
			StringBuilder buf = new StringBuilder();
			for (int i = l.length-1; i >= 0 ; i--) {
				buf.append(l[i]);
				buf.append('.');
			}
			cell.setSortValue(buf.toString());
		}
	}
}
