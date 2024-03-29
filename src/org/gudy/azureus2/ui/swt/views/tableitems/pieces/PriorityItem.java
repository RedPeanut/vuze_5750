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

package org.gudy.azureus2.ui.swt.views.tableitems.pieces;

import org.gudy.azureus2.core3.peer.PEPiece;
import org.gudy.azureus2.plugins.ui.tables.*;
import org.gudy.azureus2.ui.swt.views.table.CoreTableColumnSWT;

/**
 *
 * @author MjrTom
 * @since 2.3.0.7
 */

public class PriorityItem
extends CoreTableColumnSWT
implements TableCellRefreshListener
{
	/** Default Constructor */
	public PriorityItem() {
		super("priority", ALIGN_TRAIL, POSITION_LAST, 80, TableManager.TABLE_TORRENT_PIECES);
		setRefreshInterval(INTERVAL_LIVE);
	}

	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_SETTINGS,
		});
	}

	public void refresh(TableCell cell) {
		int	value =0;
		final PEPiece piece = (PEPiece)cell.getDataSource();
		if (piece !=null) {
			value =piece.getResumePriority();

			if (!cell.setSortValue(value) &&cell.isValid()) {
				return;
			}
		}
		cell.setText(""+value);
	}
}
