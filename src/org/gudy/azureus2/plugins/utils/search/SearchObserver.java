/*
 * Created on Jun 20, 2008
 * Created by Paul Gardner
 *
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
 */


package org.gudy.azureus2.plugins.utils.search;

public interface
SearchObserver
{
	public static final int PR_MAX_RESULTS_WANTED	= 1;	// Long
	public static final int PR_SUPPORTS_DUPLICATES	= 2;	// Boolean

	public void resultReceived(
		SearchInstance		search,
		SearchResult		result);

	public void complete();

	public void cancelled();

	public Object getProperty(
		int		property);
}
