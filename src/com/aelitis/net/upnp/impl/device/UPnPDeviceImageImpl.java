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

package com.aelitis.net.upnp.impl.device;

import com.aelitis.net.upnp.UPnPDeviceImage;

public class UPnPDeviceImageImpl
	implements UPnPDeviceImage
{
	public int width;

	public int height;

	public String location;

	public String mime;

	public UPnPDeviceImageImpl(int width, int height, String location, String mime) {
		this.width = width;
		this.height = height;
		this.location = location;
		this.mime = mime;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public String getLocation() {
		return location;
	}

	public String getMime() {
		return mime;
	}
}
