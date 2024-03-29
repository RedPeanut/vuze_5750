/*
 * Created : 2004/May/26
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

package org.gudy.azureus2.pluginsimpl.local.disk;

import java.io.File;

import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.plugins.disk.DiskManagerChannel;
import org.gudy.azureus2.plugins.disk.DiskManagerFileInfo;
import org.gudy.azureus2.plugins.disk.DiskManagerListener;
import org.gudy.azureus2.plugins.disk.DiskManagerRandomReadRequest;
import org.gudy.azureus2.plugins.download.Download;
import org.gudy.azureus2.plugins.download.DownloadException;
import org.gudy.azureus2.pluginsimpl.local.download.DownloadImpl;
import org.gudy.azureus2.pluginsimpl.local.download.DownloadManagerImpl;


/**
 * @author TuxPaper
 *
 */

public class
DiskManagerFileInfoImpl
	implements DiskManagerFileInfo
{
	protected DownloadImpl										download;
	protected org.gudy.azureus2.core3.disk.DiskManagerFileInfo 	core;

	public DiskManagerFileInfoImpl(
		DownloadImpl										_download,
		org.gudy.azureus2.core3.disk.DiskManagerFileInfo 	coreFileInfo) {
	  core 		= coreFileInfo;
	  download	= _download;
	}

	public void setPriority(boolean b) {
	  core.setPriority(b?1:0);
	}

	public void setSkipped(boolean b) {
	  core.setSkipped(b);
	}

	public int getNumericPriorty() {
		return ( core.getPriority());
	}

	public int getNumericPriority() {
		return ( core.getPriority());
	}

	public void setNumericPriority(
		int priority) {
		core.setPriority(priority);
	}

	public void setDeleted(boolean b) {
		int st = core.getStorageType();

		int	target_st;

		if (b) {

			if (st == org.gudy.azureus2.core3.disk.DiskManagerFileInfo.ST_LINEAR) {

				target_st = org.gudy.azureus2.core3.disk.DiskManagerFileInfo.ST_COMPACT;

			} else if (st == org.gudy.azureus2.core3.disk.DiskManagerFileInfo.ST_REORDER) {

				target_st = org.gudy.azureus2.core3.disk.DiskManagerFileInfo.ST_REORDER_COMPACT;

			} else {

				return;
			}

		} else {

			if (st == org.gudy.azureus2.core3.disk.DiskManagerFileInfo.ST_COMPACT) {

				target_st = org.gudy.azureus2.core3.disk.DiskManagerFileInfo.ST_LINEAR;

			} else if (st == org.gudy.azureus2.core3.disk.DiskManagerFileInfo.ST_REORDER_COMPACT) {

				target_st = org.gudy.azureus2.core3.disk.DiskManagerFileInfo.ST_REORDER;

			} else {

				return;
			}
		}

		core.setStorageType(target_st);

	}

	public boolean isDeleted() {
		int st = core.getStorageType();

		return (st ==  org.gudy.azureus2.core3.disk.DiskManagerFileInfo.ST_COMPACT || st == org.gudy.azureus2.core3.disk.DiskManagerFileInfo.ST_REORDER_COMPACT);
	}

	public void setLink(
		File	link_destination) {
		core.setLink(link_destination);
	}

	public File
	getLink() {
		return ( core.getLink());
	}
	 	// get methods

	public int getAccessMode() {
	  return core.getAccessMode();
	}

	public long getDownloaded() {
	  return core.getDownloaded();
	}

	public long getLength() {
		  return core.getLength();
		}
	public File getFile() {
	  return core.getFile(false);
	}

	public File
	getFile(
		boolean	follow_link) {
		return (core.getFile( follow_link));
	}

	public int getFirstPieceNumber() {
	  return core.getFirstPieceNumber();
	}

	public long getPieceSize() {
		try {
			return getDownload().getTorrent().getPieceSize();
		} catch (Throwable e) {

			Debug.printStackTrace(e);

			return (0);
		}
	}
	public int getNumPieces() {
	  return core.getNbPieces();
	}

	public boolean isPriority() {
	  return core.getPriority() != 0;
	}

	public boolean isSkipped() {
	  return core.isSkipped();
	}

	public int getIndex() {
		return ( core.getIndex());
	}

	public byte[] getDownloadHash()
		throws DownloadException
	{
		return ( getDownload().getTorrent().getHash());
	}

	public Download getDownload()
         throws DownloadException
    {
		if (download != null) {

			return (download);
		}

			// not sure why this code is here as we already have the download - leaving in for the moment just in case...

		return DownloadManagerImpl.getDownloadStatic( core.getDownloadManager());
    }

	public DiskManagerChannel
	createChannel()
	 	throws DownloadException
	{
		return (new DiskManagerChannelImpl( download, this));
	}

	public DiskManagerRandomReadRequest
	createRandomReadRequest(
		long						file_offset,
		long						length,
		boolean						reverse_order,
		DiskManagerListener			listener )

		throws DownloadException
	{
		return (DiskManagerRandomReadController.createRequest( download, this, file_offset, length, reverse_order, listener));
	}


	// not visible to plugin interface
	public org.gudy.azureus2.core3.disk.DiskManagerFileInfo
	getCore() {
		return (core);
	}
}
