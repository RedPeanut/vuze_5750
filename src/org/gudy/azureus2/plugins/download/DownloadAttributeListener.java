/*
 * Created on 8 Nov 2007
 * Created by Allan Crooks
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
package org.gudy.azureus2.plugins.download;

import org.gudy.azureus2.plugins.torrent.TorrentAttribute;

/**
 * Event listener interface to be notified when particular attributes are handled.
 *
 * @sionce 3.0.3.5
 * @author Allan Crooks
 */
public interface DownloadAttributeListener {
	public int WRITTEN = DownloadPropertyEvent.PT_TORRENT_ATTRIBUTE_WRITTEN;
	public int WILL_BE_READ = DownloadPropertyEvent.PT_TORRENT_ATTRIBUTE_WILL_BE_READ;

	/**
	 * This method will be called when an attribute event occurs.
	 *
	 * @param download The download object involved.
	 * @param attribute The attribute involved.
	 * @param event_type Either <tt>WRITTEN</tt> or <tt>WILL_BE_READ</tt>.
	 */
	public void attributeEventOccurred(Download download, TorrentAttribute attribute, int event_type);
}
