/*
 * Created on 07-Nov-2004
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

package org.gudy.azureus2.core3.torrent.impl;

/**
 * @author parg
 *
 */

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;

import org.gudy.azureus2.core3.torrent.*;
import org.gudy.azureus2.core3.util.AETemporaryFileHandler;
import org.gudy.azureus2.core3.util.BDecoder;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.FileUtil;

public class TOTorrentCreatorImpl implements TOTorrentCreator {
	private final File				torrentBase;
	private URL						announceUrl;
	private boolean					addOtherHashes;
	private long					pieceLength;
	private long 					pieceMinSize;
	private long 					pieceMaxSize;
	private long 					pieceNumLower;
	private long 					pieceNumUpper;

	private boolean					isDesc;

	private final Map<String,File>	linkageMap		= new HashMap<String, File>();
	private File					descriptorDir;

	private TOTorrentCreateImpl		torrent;

	private final List<TOTorrentProgressListener>	listeners = new ArrayList<TOTorrentProgressListener>();

	public TOTorrentCreatorImpl(File _torrent_base) {
		torrentBase = _torrent_base;
	}

	public TOTorrentCreatorImpl(
		File			_torrentBase,
		URL				_announceUrl,
		boolean			_addOtherHashes,
		long			_pieceLength )

		throws TOTorrentException
	{
		torrentBase 	= _torrentBase;
		announceUrl		= _announceUrl;
		addOtherHashes	= _addOtherHashes;
		pieceLength		= _pieceLength;
	}

	public TOTorrentCreatorImpl(
		File						_torrent_base,
		URL							_announce_url,
		boolean						_add_other_hashes,
		long						_piece_min_size,
		long						_piece_max_size,
		long						_piece_num_lower,
		long						_piece_num_upper )
		throws TOTorrentException
	{
		torrentBase 	= _torrent_base;
		announceUrl		= _announce_url;
		addOtherHashes	= _add_other_hashes;
		pieceMinSize	= _piece_min_size;
		pieceMaxSize	= _piece_max_size;
		pieceNumLower	= _piece_num_lower;
		pieceNumUpper	= _piece_num_upper;
	}

	public void setFileIsLayoutDescriptor(boolean b) {
		isDesc = b;
	}

	public TOTorrent create() throws TOTorrentException {
		
		try {
			if (announceUrl == null) {
				throw (new TOTorrentException("Skeleton creator", TOTorrentException.RT_WRITE_FAILS));
			}
			
			File	baseToUse;
			if (isDesc) {
				baseToUse = createLayoutMap();
			} else {
				baseToUse = torrentBase;
			}
			if (pieceLength > 0) {
				torrent =
					new TOTorrentCreateImpl(
							linkageMap,
							baseToUse,
							announceUrl,
							addOtherHashes,
							pieceLength);
			} else {
				torrent =
					new TOTorrentCreateImpl(
							linkageMap,
							baseToUse,
							announceUrl,
							addOtherHashes,
							pieceMinSize,
							pieceMaxSize,
							pieceNumLower,
							pieceNumUpper);
			}
			for (TOTorrentProgressListener l: listeners) {
				torrent.addListener(l);
			}
			torrent.create();
			return (torrent);
		} finally {
			if (isDesc) {
				destroyLayoutMap();
			}
		}
	}

	private List<DescEntry> readDescriptor()
		throws TOTorrentException
	{
		try {
			int		topFiles		= 0;
			int		topEntries		= 0;
			String 	topComponent 	= null;
			Map	map = BDecoder.decode(FileUtil.readFileAsByteArray(torrentBase));
			List<Map>	fileMap = (List<Map>)map.get("file_map");
			if (fileMap == null) {
				throw (new TOTorrentException("Invalid descriptor file", TOTorrentException.RT_READ_FAILS));
			}
			List<DescEntry>	descEntries = new ArrayList<DescEntry>();
			BDecoder.decodeStrings(fileMap);
			for (Map m: fileMap) {
				List<String>	logicalPath 	= (List<String>)m.get("logical_path");
				String			target			= (String)m.get("target");
				if (logicalPath == null || target == null) {
					throw (new TOTorrentException("Invalid descriptor file: entry=" + m, TOTorrentException.RT_READ_FAILS));
				}
				if (logicalPath.size() == 0) {
					throw (new TOTorrentException("Logical path must have at least one entry: " + m, TOTorrentException.RT_READ_FAILS));
				}
				for (int i=0;i<logicalPath.size();i++) {
					logicalPath.set( i, FileUtil.convertOSSpecificChars( logicalPath.get(i), i < logicalPath.size()-1));
				}
				File	tf = new File(target);
				if (!tf.exists()) {
					throw (new TOTorrentException("Invalid descriptor file: file '" + tf + "' not found" + m, TOTorrentException.RT_READ_FAILS));
				} else {
					String str = logicalPath.get(0);
					if (logicalPath.size() == 1) {
						topEntries++;
					}
					if (topComponent != null && !topComponent.equals( str)) {
						throw (new TOTorrentException("Invalid descriptor file: multiple top level elements specified", TOTorrentException.RT_READ_FAILS));
					}
					topComponent = str;
				}
				descEntries.add(new DescEntry(logicalPath, tf));
			}
			if (topEntries > 1) {
				throw (new TOTorrentException("Invalid descriptor file: exactly one top level entry required", TOTorrentException.RT_READ_FAILS));
			}
			if (descEntries.isEmpty()) {
				throw (new TOTorrentException("Invalid descriptor file: no mapping entries found", TOTorrentException.RT_READ_FAILS));
			}
			return (descEntries);
		} catch (IOException e) {
			throw (new TOTorrentException("Invalid descriptor file: " + Debug.getNestedExceptionMessage( e ), TOTorrentException.RT_READ_FAILS));
		}
	}

	private void mapDirectory(
		int			prefix_length,
		File		target,
		File		temp )

		throws IOException
	{
		File[]	files = target.listFiles();
		for (File f: files) {
			String	file_name = f.getName();
			if (file_name.equals(".") || file_name.equals("..")) {
				continue;
			}
			File t = new File( temp, file_name);
			if (f.isDirectory()) {
				if (!t.isDirectory()) {
					t.mkdirs();
				}
				mapDirectory(prefix_length, f, t);
			} else {
				if (!t.exists()) {
					t.createNewFile();
				} else {
					throw (new IOException("Duplicate file: " + t));
				}
				linkageMap.put(t.getAbsolutePath().substring( prefix_length ), f);
			}
		}
	}

	private File createLayoutMap()

		throws TOTorrentException
	{
			// create a directory/file hierarchy that mirrors that prescribed by the descriptor
			// along with a linkage map to be applied during construction

		if (descriptorDir != null) {

			return (descriptorDir);
		}

		try {
			descriptorDir = AETemporaryFileHandler.createTempDir();

			File	top_level_file = null;

			List<DescEntry>	desc_entries	= readDescriptor();

			for (DescEntry entry: desc_entries) {

				List<String>	logical_path	= entry.getLogicalPath();
				File			target			= entry.getTarget();

				File temp = descriptorDir;

				int	prefix_length = descriptorDir.getAbsolutePath().length() + 1;

				for (int i=0;i<logical_path.size();i++) {

					temp = new File(temp, logical_path.get( i));

					if (top_level_file == null) {

						top_level_file = temp;
					}
				}

				if (target.isDirectory()) {

					if (!temp.isDirectory()) {

						if (!temp.mkdirs()) {

							throw (new TOTorrentException("Failed to create logical directory: " + temp, TOTorrentException.RT_WRITE_FAILS));
						}
					}

					mapDirectory(prefix_length, target, temp);

				} else {

					File p = temp.getParentFile();

					if (!p.isDirectory()) {

						if (!p.mkdirs()) {

							throw (new TOTorrentException("Failed to create logical directory: " + p, TOTorrentException.RT_WRITE_FAILS));
						}
					}

					if (temp.exists()) {

						throw (new TOTorrentException("Duplicate file: " + temp, TOTorrentException.RT_WRITE_FAILS));

					} else {

						temp.createNewFile();

						linkageMap.put(temp.getAbsolutePath().substring( prefix_length ), target);
					}
				}
			}

			return (top_level_file);

		} catch (TOTorrentException e) {

			throw (e);

		} catch (Throwable e) {

			throw (new TOTorrentException( Debug.getNestedExceptionMessage( e ), TOTorrentException.RT_WRITE_FAILS));
		}
	}

	private void destroyLayoutMap() {
		if (descriptorDir != null && descriptorDir.exists()) {

			if (!FileUtil.recursiveDelete( descriptorDir)) {

				Debug.out("Failed to delete descriptor directory '" + descriptorDir + "'");
			}
		}
	}

	public long getTorrentDataSizeFromFileOrDir()

		throws TOTorrentException
	{
		if (isDesc) {

			List<DescEntry>	desc_entries	= readDescriptor();

			long	result = 0;

			for (DescEntry entry: desc_entries) {

				result += getTorrentDataSizeFromFileOrDir( entry.getTarget());
			}

			return (result);

		} else {

			return (getTorrentDataSizeFromFileOrDir( torrentBase));
		}
	}

	private long getTorrentDataSizeFromFileOrDir(
		File				file) {
		String	name = file.getName();

		if (name.equals(".") || name.equals("..")) {

			return (0);
		}

		if (!file.exists()) {

			return (0);
		}

		if (file.isFile()) {

			return ( file.length());

		} else {

			File[]	dir_files = file.listFiles();

			long	length = 0;

			for (int i=0;i<dir_files.length;i++) {

				length += getTorrentDataSizeFromFileOrDir(dir_files[i]);
			}

			return (length);
		}
	}

	public void cancel() {
		if (torrent != null) {

			torrent.cancel();
		}
	}

	public void addListener(
		TOTorrentProgressListener	listener) {
		if (torrent == null) {

			listeners.add(listener);

		} else {

			torrent.addListener(listener);
		}
	}

	public void removeListener(
		TOTorrentProgressListener	listener) {
		if (torrent == null) {

			listeners.remove(listener);

		} else {

			torrent.removeListener(listener);
		}
	}

	private static class
	DescEntry {
		private final List<String>	logical_path;
		private final File			target;

		private DescEntry(
			List<String>		_l,
			File				_t) {
			logical_path	= _l;
			target			= _t;
		}

		private List<String>
		getLogicalPath() {
			return (logical_path);
		}

		private File
		getTarget() {
			return (target);
		}
	}
}
