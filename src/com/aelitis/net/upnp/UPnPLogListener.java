/*
 * Created on 14-Jun-2004
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

package com.aelitis.net.upnp;

/**
 * @author parg
 *
 */

public interface
UPnPLogListener
{
	public static final int	TYPE_ALWAYS					= 1;
	public static final int	TYPE_ONCE_PER_SESSION		= 2;
	public static final int	TYPE_ONCE_EVER				= 3;

	public void log(
		String		str);

	public void logAlert(
		String		str,
		boolean		error,
		int			type);
}
