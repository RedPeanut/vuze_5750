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

import java.util.Map;

public interface
AzureusContentDirectory
{
	public static final String	AT_BTIH			= "btih";			// byte[]
	public static final String	AT_FILE_INDEX	= "file_index";		// Integer

	public AzureusContent
	lookupContent(
		Map		attributes);

	public AzureusContentFile
	lookupContentFile(
		Map		attributes);

	public AzureusContentDownload
	lookupContentDownload(
		Map		attributes);

	public void addListener(
		AzureusContentDirectoryListener		listener);

	public void removeListener(
		AzureusContentDirectoryListener		listener);
}
