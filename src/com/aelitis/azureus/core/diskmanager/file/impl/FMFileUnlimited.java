/*
 * File    : FMFileUnlimited.java
 * Created : 12-Feb-2004
 * By      : parg
 *
 * Azureus - a Java Bittorrent client
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

package com.aelitis.azureus.core.diskmanager.file.impl;

/**
 * @author parg
 *
 */

import java.io.File;

import org.gudy.azureus2.core3.util.DirectByteBuffer;

import com.aelitis.azureus.core.diskmanager.file.*;

public class
FMFileUnlimited
	extends FMFileImpl
{
	protected FMFileUnlimited(
		FMFileOwner			_owner,
		FMFileManagerImpl	_manager,
		File				_file,
		int					_type )

		throws FMFileManagerException
	{
		super(_owner, _manager, _file, _type);
	}

	protected FMFileUnlimited(
		FMFileUnlimited	basis )

		throws FMFileManagerException
	{
		super(basis);
	}

	public FMFile
	createClone()

		throws FMFileManagerException
	{
		return (new FMFileUnlimited( this));
	}

	public void setAccessMode(
		int		mode )

		throws FMFileManagerException
	{
		try {
			this_mon.enter();

			if (mode == getAccessMode() && isOpen()) {

				return;
			}

			setAccessModeSupport(mode);

			if (isOpen()) {

				closeSupport(false);
			}

			openSupport("FMFileUnlimited:setAccessMode");

		} finally {

			this_mon.exit();
		}
	}

	public long getLength()

		throws FMFileManagerException
	{
		try {
			this_mon.enter();

			ensureOpen("FMFileUnlimited:getLength");

			return ( getLengthSupport());

		} finally {

			this_mon.exit();
		}
	}

	public void setLength(
		long		length )

		throws FMFileManagerException
	{
		try {
			this_mon.enter();

			ensureOpen("FMFileUnlimited:setLength");

			setLengthSupport(length);

		} finally {

			this_mon.exit();
		}
	}

	public void setPieceComplete(
		int					piece_number,
		DirectByteBuffer	piece_data )

		throws FMFileManagerException
	{
		try {
			this_mon.enter();

			if (isPieceCompleteProcessingNeeded( piece_number)) {

				ensureOpen("FMFileUnlimited:setPieceComplete");

				boolean	switched_mode = false;

				if (getAccessMode() != FM_WRITE) {

					setAccessMode(FM_WRITE);

					switched_mode = true;

						// switching mode closes the file...

					ensureOpen("FMFileUnlimited:setPieceComplete2");
				}

				try {

					setPieceCompleteSupport(piece_number, piece_data);

				} finally {

					if (switched_mode) {

						setAccessMode(FM_READ);
					}
				}
			}
		} finally {

			this_mon.exit();
		}
	}

	public void read(
		DirectByteBuffer	buffer,
		long				offset )

		throws FMFileManagerException
	{
		try {
			this_mon.enter();

			ensureOpen("FMFileUnlimited:read");

			readSupport(buffer, offset);

		} finally {

			this_mon.exit();
		}
	}

	public void read(
		DirectByteBuffer[]	buffers,
		long				offset )

		throws FMFileManagerException
	{
		try {
			this_mon.enter();

			ensureOpen("FMFileUnlimited:read");

			readSupport(buffers, offset);

		} finally {

			this_mon.exit();
		}
	}


	public void write(
		DirectByteBuffer	buffer,
		long		position )

		throws FMFileManagerException
	{
		try {
			this_mon.enter();

			ensureOpen("FMFileUnlimited:write");

			writeSupport(buffer, position);

		} finally {

			this_mon.exit();
		}
	}

	public void write(
		DirectByteBuffer[]	buffers,
		long				position )

		throws FMFileManagerException
	{
		try {
			this_mon.enter();

			ensureOpen("FMFileUnlimited:write");

			writeSupport(buffers, position);

		} finally {

			this_mon.exit();
		}
	}

	public void close()

		throws FMFileManagerException
	{
		try {
			this_mon.enter();

			closeSupport(true);

		} finally {

			this_mon.exit();
		}
	}
}
