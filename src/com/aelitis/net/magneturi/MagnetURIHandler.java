/*
 * Created on 03-Mar-2005
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

package com.aelitis.net.magneturi;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

import com.aelitis.net.magneturi.impl.MagnetURIHandlerImpl;

/**
 * @author parg
 *
 */

public abstract class
MagnetURIHandler
{
	public static MagnetURIHandler
	getSingleton() {
		return ( MagnetURIHandlerImpl.getSingleton());
	}

	public abstract int
	getPort();

	public abstract void
	process(
		String			get,
		InputStream		is,
		OutputStream	os )

		throws IOException;

	public abstract void
	addListener(
		MagnetURIHandlerListener l);

	public abstract void
	removeListener(
		MagnetURIHandlerListener l);

	public abstract void
	addInfo(
		String		name,
		int			info);

	public abstract URL
	registerResource(
		ResourceProvider		provider);

	public interface
	ResourceProvider
	{
		public String getUID();

		public String getFileType();

		public byte[]
		getData();
	}
}
