/*
 * Created on Dec 19, 2006
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


package com.aelitis.azureus.core.content;

import java.util.*;

public class
AzureusContentDirectoryManager
{
	private static final List	directories = new ArrayList();

	public static void registerDirectory(
		AzureusContentDirectory	directory) {
		synchronized(directories) {

			directories.add(directory);
		}
	}

	public static AzureusContentDirectory[]
	getDirectories() {
		synchronized(directories) {

			return ((AzureusContentDirectory[])directories.toArray(new AzureusContentDirectory[directories.size()]));
		}
	}
}
