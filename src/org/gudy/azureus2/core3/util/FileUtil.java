 /*
 * Created on Oct 10, 2003
 * Modified Apr 14, 2004 by Alon Rohter
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.	See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA	02111-1307, USA.
 *
 */

package org.gudy.azureus2.core3.util;

import java.io.*;
import java.lang.reflect.Method;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import org.gudy.azureus2.core3.config.COConfigurationListener;
import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.logging.LogEvent;
import org.gudy.azureus2.core3.logging.LogIDs;
import org.gudy.azureus2.core3.logging.Logger;
import org.gudy.azureus2.platform.PlatformManager;
import org.gudy.azureus2.platform.PlatformManagerCapabilities;
import org.gudy.azureus2.platform.PlatformManagerFactory;
import org.gudy.azureus2.plugins.platform.PlatformManagerException;

import com.aelitis.azureus.core.AzureusCore;
import com.aelitis.azureus.core.AzureusCoreFactory;
import com.aelitis.azureus.core.AzureusCoreOperation;
import com.aelitis.azureus.core.AzureusCoreOperationTask;

/**
 * File utility class.
 */
public class FileUtil {
	private static final LogIDs LOGID = LogIDs.CORE;
	public static final String DIR_SEP = System.getProperty("file.separator");


	private static final int	RESERVED_FILE_HANDLE_COUNT	= 4;
	private static boolean		first_reservation		= true;
	private static boolean	is_my_lock_file			= false;
	private static final List		reserved_file_handles 	= new ArrayList();
	private static final AEMonitor	class_mon				= new AEMonitor("FileUtil:class");

	private static Method reflectOnUsableSpace;

	private static char[]	char_conversion_mapping = null;


	static {

		try
		{
			reflectOnUsableSpace = File.class.getMethod("getUsableSpace", (Class[])null);
		} catch (Throwable e)
		{
			reflectOnUsableSpace = null;
		}
	}

	public static boolean isAncestorOf(File parent, File child) {
		parent = canonise(parent);
		child = canonise(child);
		if (parent.equals(child)) {return true;}
		String parent_s = parent.getPath();
		String child_s = child.getPath();
		if (parent_s.charAt(parent_s.length()-1) != File.separatorChar) {
			parent_s += File.separatorChar;
		}
		return child_s.startsWith(parent_s);
	}

	public static File canonise(File file) {
		try {return file.getCanonicalFile();}
		catch (IOException ioe) {return file;}
	}

	public static String getCanonicalFileName(String filename) {
		// Sometimes Windows use filename in 8.3 form and cannot
		// match .torrent extension. To solve this, canonical path
		// is used to get back the long form

		String canonicalFileName = filename;
		try {
			canonicalFileName = new File(filename).getCanonicalPath();
		}
		catch (IOException ignore) {}
		return canonicalFileName;
	}


	public static File getUserFile(String filename) {
		return new File(SystemProperties.getUserPath(), filename);
	}

	/**
	 * Get a file relative to this program's install directory.
	 * <p>
	 * On Windows, this is usually %Program Files%\[AppName]\[filename]
	 * <br>
	 * On *nix, this is usually the [Launch Dir]/[filename]
	 * <br>
	 * On Mac, this is "/Users/Shared/Library/Application Support/[AppName]/[filename]"
	 * On legacy (unsigned) Mac, it's usually "[AppName].app/Contents"
	 */
	public static File getApplicationFile(String filename) {
		String path = SystemProperties.getApplicationPath();
		if (Constants.isOSX && !new File(path, "Azureus2.jar").exists()) {
			// Legacy Mac, we stored things inside the .app, which caused
			// signature breakage on change.
			path = path + SystemProperties.getApplicationName() + ".app/Contents/";
		}
		return new File(path, filename);
	}



	/**
	 * Deletes the given dir and all files/dirs underneath
	 */
	public static boolean recursiveDelete(File f) {
		String defSaveDir = COConfigurationManager.getStringParameter("Default save path");
		String moveToDir = COConfigurationManager.getStringParameter("Completed Files Directory", "");

		try {
			moveToDir = new File(moveToDir).getCanonicalPath();
		} catch (Throwable e) {
		}
		try {
			defSaveDir = new File(defSaveDir).getCanonicalPath();
		} catch (Throwable e) {
		}

		try {

			if (f.getCanonicalPath().equals(moveToDir)) {
				System.out.println("FileUtil::recursiveDelete:: not allowed to delete the MoveTo dir !");
				return (false);
			}
			if (f.getCanonicalPath().equals(defSaveDir)) {
				System.out.println("FileUtil::recursiveDelete:: not allowed to delete the default data dir !");
				return (false);
			}

			if (f.isDirectory()) {
				File[] files = f.listFiles();
				for (int i = 0; i < files.length; i++) {
					if (!recursiveDelete(files[i])) {

						return (false);
					}
				}
				if (!f.delete()) {

					return (false);
				}
			}
			else {
				if (!f.delete()) {

					return (false);
				}
			}
		} catch (Exception ignore) {/*ignore*/}

		return (true);
	}

	public static boolean recursiveDeleteNoCheck(File f) {
		try {
			if (f.isDirectory()) {
				File[] files = f.listFiles();
				for (int i = 0; i < files.length; i++) {
					if (!recursiveDeleteNoCheck(files[i])) {

						return (false);
					}
				}
				if (!f.delete()) {

					return (false);
				}
			}
			else {
				if (!f.delete()) {

					return (false);
				}
			}
		} catch (Exception ignore) {/*ignore*/}

		return (true);
	}

	public static long
	getFileOrDirectorySize(
		File		file )
	{
		if (file.isFile()) {

			return ( file.length());

		} else {

			long	res = 0;

			File[] files = file.listFiles();

			if (files != null) {

				for (int i=0;i<files.length;i++) {

					res += getFileOrDirectorySize(files[i]);
				}
			}

			return (res);
		}
	}

	protected static void
	recursiveEmptyDirDelete(
		File	f,
	Set		ignore_set,
	boolean	log_warnings )
	{
		 try {
			String defSaveDir 	= COConfigurationManager.getStringParameter("Default save path");
			String moveToDir 		= COConfigurationManager.getStringParameter("Completed Files Directory", "");

			if (defSaveDir.trim().length() > 0) {

				defSaveDir = new File(defSaveDir).getCanonicalPath();
			}

			if (moveToDir.trim().length() > 0) {

				moveToDir = new File(moveToDir).getCanonicalPath();
			}

			if (f.isDirectory()) {

				File[] files = f.listFiles();

				if (files == null) {

					if (log_warnings) {
						Debug.out("Empty folder delete:	failed to list contents of directory " + f);
					}

						return;
				}

				for (int i = 0; i < files.length; i++) {

					File	x = files[i];

					if (x.isDirectory()) {

						recursiveEmptyDirDelete(files[i],ignore_set,log_warnings);

					} else {

						if (ignore_set.contains( x.getName().toLowerCase())) {

							if (!x.delete()) {

								if (log_warnings) {
									Debug.out("Empty folder delete: failed to delete file " + x);
								}
							}
						}
					}
				}

				if (f.getCanonicalPath().equals(moveToDir)) {

					if (log_warnings) {
						Debug.out("Empty folder delete:	not allowed to delete the MoveTo dir !");
					}

					return;
				}

				if (f.getCanonicalPath().equals(defSaveDir)) {

					if (log_warnings) {
						Debug.out("Empty folder delete:	not allowed to delete the default data dir !");
					}

					return;
				}

				File[] files_inside = f.listFiles();
				if (files_inside.length == 0) {

					if (!f.delete()) {

						if (log_warnings) {
							Debug.out("Empty folder delete:	failed to delete directory " + f);
						}
					}
				} else {
					if (log_warnings) {
						Debug.out("Empty folder delete:	" + files_inside.length + " file(s)/folder(s) still in \"" + f + "\" - first listed item is \"" + files_inside[0].getName() + "\". Not removing.");
					}
				}
			}

		} catch (Exception e) { Debug.out(e.toString()); }
	}

	public static String
	convertOSSpecificChars(
		String		file_name_in,
		boolean		is_folder )
	{
		char[] mapping;
		synchronized(FileUtil.class) {
			if (char_conversion_mapping == null) {
				COConfigurationManager.addAndFireListener(
					 new COConfigurationListener() {
						 public void configurationSaved()
						 {
							 synchronized(FileUtil.class) {
								 String map = COConfigurationManager.getStringParameter("File.Character.Conversions");
								 String[] bits = map.split(",");
								 List<Character> chars = new ArrayList<Character>();
								 for (String bit: bits) {
									 bit = bit.trim();
									 if (bit.length()==3) {
										 char from	= bit.charAt(0);
										 char to	= bit.charAt(2);
										 chars.add(from);
										 chars.add(to);
									 }
								 }
								 char[] new_map = new char[chars.size()];
								 for ( int i=0;i<new_map.length;i++) {
									 new_map[i] = chars.get(i);
								 }
								 char_conversion_mapping = new_map;
							 }
						 }
					});
			}
			mapping = char_conversion_mapping;
		}
			// this rule originally from DiskManager
		char[]	chars = file_name_in.toCharArray();
	if (mapping.length == 2) {
			// default case
			char from 	= mapping[0];
			char to		= mapping[1];
			for (int i=0;i<chars.length;i++) {
				if (chars[i] == from) {
					chars[i] = to;
				}
			}
		} else if (mapping.length > 0) {
			for (int i=0;i<chars.length;i++) {
				char c = chars[i];
			for (int j=0;j<mapping.length;j+=2) {
					if (c == mapping[j]) {
						chars[i] = mapping[j+1];
					}
				}
			}
		}
		if (!Constants.isOSX) {
			if (Constants.isWindows) {
					//	this rule originally from DiskManager
			// The definitive list of characters permitted for Windows is defined here:
			// http://support.microsoft.com/kb/q120138/
				String not_allowed = "\\/:?*<>|";
				for (int i=0;i<chars.length;i++) {
					if (not_allowed.indexOf(chars[i]) != -1) {
							chars[i] = '_';
						}
					}
				// windows doesn't like trailing dots and whitespaces in folders, replace them
				if (is_folder) {
					for (int i = chars.length-1;i >= 0 && (chars[i] == '.' || chars[i] == ' ');chars[i] = '_',i--);
				}
			}
				// '/' is valid in mac file names, replace with space
				// so it seems are cr/lf
		for (int i=0;i<chars.length;i++) {
			char	c = chars[i];
			if (c == '/' || c == '\r' || c == '\n') {
				chars[i] = ' ';
			}
		}
		}
		String	file_name_out = new String(chars);
	try {
			// mac file names can end in space - fix this up by getting
			// the canonical form which removes this on Windows
			// however, for soem reason getCanonicalFile can generate high CPU usage on some user's systems
			// in	java.io.Win32FileSystem.canonicalize
			// so changing this to only be used on non-windows
		if (Constants.isWindows) {
			while (file_name_out.endsWith(" ")) {
				file_name_out = file_name_out.substring(0,file_name_out.length()-1);
			}
		} else {
			String str = new File(file_name_out).getCanonicalFile().toString();
			int	p = str.lastIndexOf(File.separator);
			file_name_out = str.substring(p+1);
		}
	} catch (Throwable e) {
		// ho hum, carry on, it'll fail later
		//e.printStackTrace();
	}
	//System.out.println("convertOSSpecificChars: " + file_name_in + " ->" + file_name_out);
	return (file_name_out);
	}

	public static void	writeResilientConfigFile(String filename, Map data) {
		File parentDir = new File(SystemProperties.getUserPath());
		boolean useBackups = COConfigurationManager.getBooleanParameter("Use Config File Backups");
		writeResilientFile(parentDir, filename, data, useBackups);
	}

	public static void writeResilientFile(File file, Map data) {
		writeResilientFile(file.getParentFile(), file.getName(), data, false);
	}

	public static boolean writeResilientFileWithResult(
		File		parentDir,
		String		filename,
		Map			data) {
		return (writeResilientFile( parentDir, filename, data));
	}

	public static void writeResilientFile(
		File		parentDir,
		String		filename,
		Map			data,
		boolean		useBackup) {
		writeResilientFile(parentDir, filename, data, useBackup, true);
	}

	public static void writeResilientFile(
		File		parentDir,
		String		filename,
		Map			data,
		boolean		useBackup,
		boolean		copyToBackup)
	{
		if (useBackup) {
			File originator = new File(parentDir, filename);
			if (originator.exists()) {
				backupFile(originator, copyToBackup);
			}
		}
		writeResilientFile(parentDir, filename, data);
	}

	// synchronise it to prevent concurrent attempts to write the same file

	private static boolean writeResilientFile(
		File		parentDir,
		String		filename,
		Map			data) {
		
		try {
			class_mon.enter();
			try {
				getReservedFileHandles();
				File temp = new File(parentDir, filename + ".saving");
				BufferedOutputStream baos = null;
				try {
					byte[] encodedData = BEncoder.encode(data);
					FileOutputStream tempOS = new FileOutputStream(temp, false);
					baos = new BufferedOutputStream(tempOS, 8192);
					baos.write(encodedData);
					baos.flush();
					// thinking about removing this - just do so for CVS for the moment
					if (!Constants.isCVSVersion()) {
						tempOS.getFD().sync();
					}
					baos.close();
					baos = null;
					//only use newly saved file if it got this far, i.e. it saved successfully
					if (temp.length() > 1L) {
						File file = new File(parentDir, filename);
						if (file.exists()) {
							if (!file.delete()) {
								Debug.out("Save of '" + filename + "' fails - couldn't delete " + file.getAbsolutePath());
							}
						}
						if (file.exists()) {
							Debug.out(file + " still exists after delete attempt");
						}
						if (temp.renameTo(file)) {
							return (true);
						}
						// rename failed, sleep a little and try again
						Thread.sleep(50);
						if (temp.renameTo(file)) {
							//System.err.println("2nd attempt of rename succeeded for " + temp.getAbsolutePath() + " to " + file.getAbsolutePath());
							return true;
						}
						Debug.out("Save of '" + filename + "' fails - couldn't rename " + temp.getAbsolutePath() + " to " + file.getAbsolutePath());
					}
					return (false);
				} catch (Throwable e) {
					Debug.out("Save of '" + filename + "' fails", e);
					return (false);
				} finally {
					try {
						if (baos != null) {
							baos.close();
						}
					} catch (Exception e) {
						Debug.out("Save of '" + filename + "' fails", e);
						return (false);
					}
				}
			} finally {
				releaseReservedFileHandles();
			}
		} finally {
			class_mon.exit();
		}
	}

		public static boolean
		resilientConfigFileExists(
			String		name )
		{
 		File parent_dir = new File(SystemProperties.getUserPath());

 		boolean use_backups = COConfigurationManager.getBooleanParameter("Use Config File Backups");

 		return (new File( parent_dir, name).exists() ||
 				(use_backups && new File( parent_dir, name + ".bak").exists()));
		}

	/**
	 *
 	 * @return Map read from config file, or empty HashMap if error
	 */
	public static Map
	readResilientConfigFile(
		String		file_name) {
 		File parent_dir = new File(SystemProperties.getUserPath());

 		boolean use_backups = COConfigurationManager.getBooleanParameter("Use Config File Backups");

 		return (readResilientFile( parent_dir, file_name, use_backups));
	}

	/**
	 *
 	 * @return Map read from config file, or empty HashMap if error
	 */
	public static Map readResilientConfigFile(
		String		filename,
		boolean		useBackups) {
 		File dir = new File(SystemProperties.getUserPath());
 		if (!useBackups) {
			// override if a backup file exists. This is needed to cope with backups
			// of the main config file itself as when bootstrapping we can't get the
			// "use backups"
 			if (new File(dir, filename + ".bak").exists()) {
 				useBackups = true;
 			}
 		}
 		return (readResilientFile(dir, filename, useBackups));
	}

	/**
	 *
 	 * @return Map read from config file, or empty HashMap if error
	 */
	public static Map readResilientFile(File file) {
		return (readResilientFile(file.getParentFile(), file.getName(), false, true));
	}

	/**
	 *
 	 * @return Map read from config file, or empty HashMap if error
	 */
 	public static Map readResilientFile(
		File		parentDir,
		String		filename,
		boolean		useBackup) {
 		return readResilientFile(parentDir, filename, useBackup, true);
 	}

 	/**
 	 *
 	 * @param parentDir
 	 * @param filename
 	 * @param useBackup
 	 * @param internKeys
 	 *
 	 * @return Map read from config file, or empty HashMap if error
 	 */
 	public static Map readResilientFile(
		File		parentDir,
		String		filename,
		boolean		useBackup,
		boolean		internKeys)
 	{
		File backupFile = new File(parentDir, filename + ".bak");
 		if (useBackup) {
 			useBackup = backupFile.exists();
 		}
		// if we've got a backup, don't attempt recovery here as the .bak file may be
		// fully OK
 		Map	res = readResilientFileSupport(parentDir, filename, !useBackup, internKeys);
 		if (res == null && useBackup) {
			// try backup without recovery
 			res = readResilientFileSupport(parentDir, filename + ".bak", false, internKeys);
			if (res != null) {
				Debug.out("Backup file '" + backupFile + "' has been used for recovery purposes");
				// rewrite the good data, don't use backups here as we want to
				// leave the original backup in place for the moment
				writeResilientFile(parentDir, filename, res, false);
			} else {
 				// neither main nor backup file ok, retry main file with recovery
				res = readResilientFileSupport(parentDir, filename, true, true);
			}
 		}
 		if (res == null) {
 			res = new HashMap();
 		}
 		return (res);
 	}

	// synchronised against writes to make sure we get a consistent view

		private static Map readResilientFileSupport(
		File		parentDir,
		String		filename,
		boolean		attemptRecovery,
		boolean		internKeys) {
			try {
				class_mon.enter();
				try {
					getReservedFileHandles();
					Map	res = null;
					try {
						res = readResilientFile(filename, parentDir, filename, 0, false, internKeys);
					} catch (Throwable e) {
						// ignore, it'll be rethrown if we can't recover below
					}
					if (res == null && attemptRecovery) {
						res = readResilientFile(filename, parentDir, filename, 0, true, internKeys);
						if (res != null) {
							Debug.out("File '" + filename + "' has been partially recovered, information may have been lost!");
						}
					}
					return (res);
				} catch (Throwable e) {
					Debug.printStackTrace(e);
					return (null);
				} finally {
					releaseReservedFileHandles();
				}
			} finally {
				class_mon.exit();
			}
		}

	private static Map readResilientFile(
		String		originalFilename,
		File		parentDir,
		String		filename,
		int			failCount,
		boolean		recoveryMode,
		boolean		skipKeyIntern) {
		
		// logging in here is only done during "non-recovery" mode to prevent subsequent recovery
		// attempts logging everything a second time.
		// recovery-mode allows the decoding process to "succeed" with a partially recovered file
			boolean	usingBackup	= filename.endsWith(".saving");
			File file = new File(parentDir, filename);
			//make sure the file exists and isn't zero-length
			if ((!file.exists()) || file.length() <= 1L) {
				if (usingBackup) {
					if (!recoveryMode) {
						if (failCount == 1) {
							Debug.out("Load of '" + originalFilename + "' fails, no usable file or backup");
						} else {
							// drop this log, it doesn't really help to inform about the failure to
							// find a .saving file
							//if (Logger.isEnabled())
						//		Logger.log(new LogEvent(LOGID, LogEvent.LT_ERROR, "Load of '"
						//				+ file_name + "' fails, file not found"));
						}
					}
					return (null);
				}
				if (!recoveryMode) {
					// kinda confusing log this as we get it under "normal" circumstances (loading a config
					// file that doesn't exist legitimately, e.g. shares or bad-ips
//					if (Logger.isEnabled())
//						Logger.log(new LogEvent(LOGID, LogEvent.LT_ERROR, "Load of '"
//								+ file_name + "' failed, " + "file not found or 0-sized."));
				}
				return (readResilientFile(originalFilename, parentDir, filename + ".saving", 0, recoveryMode, true));
			}
			
			BufferedInputStream bin = null;
			try {
				int	retry_limit = 5;
				while (true) {
					try {
						bin = new BufferedInputStream(new FileInputStream(file), 16384);
						break;
					} catch (IOException e) {
						if (--retry_limit == 0) {
							throw (e);
						}
						if (Logger.isEnabled())
						Logger.log(new LogEvent(LOGID, "Failed to open '" + file.toString()	+ "', retrying", e));
						Thread.sleep(500);
					}
				}
				
				BDecoder decoder = new BDecoder();
				if (recoveryMode) {
					decoder.setRecoveryMode(true);
				}
				Map	res = decoder.decodeStream(bin, !skipKeyIntern);
				if (usingBackup && !recoveryMode) {
					Debug.out("Load of '" + originalFilename + "' had to revert to backup file");
				}
				return (res);
			} catch (Throwable e) {
				Debug.printStackTrace(e);
				try {
					if (bin != null) {
						bin.close();
						bin	= null;
					}
				} catch (Exception x) {
					Debug.printStackTrace(x);
				}
					// if we're not recovering then backup the file
				if (!recoveryMode) {
					// Occurs when file is there but b0rked
						// copy it in case it actually contains useful data, so it won't be overwritten next save
					File bad;
					int	bad_id = 0;
					while (true) {
						File	test = new File( parentDir, file.getName() + ".bad" + (bad_id==0?"":(""+bad_id)));
						if (!test.exists()) {
							bad	= test;
							break;
						}
						bad_id++;
					}
					if (Logger.isEnabled())
						Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING, "Read of '"
								+ originalFilename + "' failed, decoding error. " + "Renaming to "
								+ bad.getName()));
						// copy it so its left in place for possible recovery
					copyFile(file, bad);
				}
				if (usingBackup) {
					if (!recoveryMode) {
						Debug.out("Load of '" + originalFilename + "' fails, no usable file or backup");
					}
					return (null);
				}
				return (readResilientFile( originalFilename, parentDir, filename + ".saving", 1, recoveryMode, true));
			} finally {
				try {
					if (bin != null) {
						bin.close();
					}
				} catch (Exception e) {
					Debug.printStackTrace(e);
				}
			}
	}

	public static void deleteResilientFile(
		File		file) {
		file.delete();
		new File(file.getParentFile(), file.getName() + ".bak").delete();
	}

	public static void deleteResilientConfigFile(
		String		name) {
		File parent_dir = new File(SystemProperties.getUserPath());

		new File(parent_dir, name).delete();
		new File(parent_dir, name + ".bak").delete();
	}

	private static void getReservedFileHandles() {
		try {
			class_mon.enter();

			while (reserved_file_handles.size() > 0) {

				// System.out.println("releasing reserved file handle");

				InputStream	is = (InputStream)reserved_file_handles.remove(0);

				try {
					is.close();

				} catch (Throwable e) {

					Debug.printStackTrace(e);
				}
			}
		} finally {

			class_mon.exit();
		}
	}

	private static void releaseReservedFileHandles() {
		try {

			class_mon.enter();

			File	lock_file	= new File(SystemProperties.getUserPath() + ".lock");

			if (first_reservation) {

				first_reservation = false;

				lock_file.delete();

				is_my_lock_file = lock_file.createNewFile();

			} else {

				lock_file.createNewFile();
			}

			while (reserved_file_handles.size() < RESERVED_FILE_HANDLE_COUNT) {

				// System.out.println("getting reserved file handle");

				InputStream	is = new FileInputStream(lock_file);

				reserved_file_handles.add(is);
			}
		} catch (Throwable e) {

			Debug.printStackTrace(e);

		} finally {

			class_mon.exit();
		}
	}

	public static boolean
	isMyFileLock() {
		return (is_my_lock_file);
	}

		/**
		 * Backup the given file to filename.bak, removing the old .bak file if necessary.
		 * If _make_copy is true, the original file will copied to backup, rather than moved.
		 * @param _filename name of file to backup
		 * @param _make_copy copy instead of move
		 */
		public static void backupFile(final String _filename, final boolean _make_copy) {
			backupFile(new File( _filename ), _make_copy);
		}

		/**
		 * Backup the given file to filename.bak, removing the old .bak file if necessary.
		 * If _make_copy is true, the original file will copied to backup, rather than moved.
		 * @param _file file to backup
		 * @param _make_copy copy instead of move
		 */
		public static void backupFile(final File _file, final boolean _make_copy) {
			if (_file.length() > 0L) {
				File bakfile = new File(_file.getAbsolutePath() + ".bak");
				if (bakfile.exists()) bakfile.delete();
				if (_make_copy) {
					copyFile(_file, bakfile);
				}
				else {
					_file.renameTo(bakfile);
				}
			}
		}


		/**
		 * Copy the given source file to the given destination file.
		 * Returns file copy success or not.
		 * @param _source_name source file name
		 * @param _dest_name destination file name
		 * @return true if file copy successful, false if copy failed
		 */
		public static boolean copyFile(final String _source_name, final String _dest_name) {
			return copyFile(new File(_source_name), new File(_dest_name));
		}

		/**
		 * Copy the given source file to the given destination file.
		 * Returns file copy success or not.
		 * @param _source source file
		 * @param _dest destination file
		 * @return true if file copy successful, false if copy failed
		 */
		/*
		// FileChannel.transferTo() seems to fail under certain linux configurations.
		public static boolean copyFile(final File _source, final File _dest) {
			FileChannel source = null;
			FileChannel dest = null;
			try {
				if (_source.length() < 1L) {
					throw new IOException(_source.getAbsolutePath() + " does not exist or is 0-sized");
				}
				source = new FileInputStream(_source).getChannel();
				dest = new FileOutputStream(_dest).getChannel();

				source.transferTo(0, source.size(), dest);
				return true;
			}
			catch (Exception e) {
				Debug.out(e);
				return false;
			}
			finally {
				try {
					if (source != null) source.close();
					if (dest != null) dest.close();
				}
				catch (Exception ignore) {}
			}
		}
		*/

		public static boolean copyFile(final File _source, final File _dest) {
			try {
				copyFile(new FileInputStream( _source ), new FileOutputStream( _dest ));
				return true;
			}
			catch (Throwable e) {
				Debug.printStackTrace(e);
				return false;
			}
		}

		public static void copyFileWithException(final File _source, final File _dest) throws IOException{
				 copyFile(new FileInputStream( _source ), new FileOutputStream( _dest ));
		}

		public static boolean copyFile(final File _source, final OutputStream _dest, boolean closeInputStream) {
				try {
					copyFile(new FileInputStream( _source ), _dest, closeInputStream);
					return true;
				}
				catch (Throwable e) {
					Debug.printStackTrace(e);
					return false;
				}
			}

			/**
			 * copys the input stream to the file. always closes the input stream
			 * @param _source
			 * @param _dest
			 * @throws IOException
			 */

		public static void
		copyFile(
			final InputStream 	_source,
			final File 			_dest )

			throws IOException
		{
			FileOutputStream	dest = null;

			boolean	close_input = true;

			try {
				dest = new FileOutputStream(_dest);

					// copyFile will close from now on, we don't need to

				close_input = false;

				copyFile(_source, dest, true);

			} finally {

					try {
					if (close_input) {
						_source.close();
					}
				} catch (IOException e) {
				}

				if (dest != null) {

					dest.close();
				}
			}
		}

		public static void
		copyFile(
			final InputStream 	_source,
			final File 			_dest,
			boolean				_close_input_stream )

			throws IOException
		{
			FileOutputStream	dest = null;

			boolean	close_input = _close_input_stream;

			try {
				dest = new FileOutputStream(_dest);

				close_input = false;

				copyFile(_source, dest, close_input);

			} finally {

					try {
					if (close_input) {

						_source.close();
					}
				} catch (IOException e) {
				}

				if (dest != null) {

					dest.close();
				}
			}
		}

		public static void
		copyFile(
			InputStream	 is,
			OutputStream	os )

			throws IOException
		{
			copyFile(is,os,true);
		}

		public static void copyFile(
		InputStream		is,
		OutputStream	os,
		boolean 		closeInputStream )

		throws IOException
	{
			try {

				if (!(is instanceof BufferedInputStream)) {

					is = new BufferedInputStream(is,128*1024);
				}

				byte[]	buffer = new byte[128*1024];

				while (true) {

					int	len = is.read(buffer);

					if (len == -1) {

						break;
					}

					os.write(buffer, 0, len);
				}
			} finally {
				try {
					if (closeInputStream) {
						is.close();
					}
				} catch (IOException e) {

				}

				os.close();
			}
	}

		public static void
		copyFileOrDirectory(
			File	from_file_or_dir,
			File	to_parent_dir )

			throws IOException
		{
			if (!from_file_or_dir.exists()) {

				throw (new IOException("File '" + from_file_or_dir.toString() + "' doesn't exist"));
			}

			if (!to_parent_dir.exists()) {

				throw (new IOException("File '" + to_parent_dir.toString() + "' doesn't exist"));
			}

			if (!to_parent_dir.isDirectory()) {

				throw (new IOException("File '" + to_parent_dir.toString() + "' is not a directory"));
			}

			if (from_file_or_dir.isDirectory()) {

				File[]	files = from_file_or_dir.listFiles();

				File	new_parent = new File( to_parent_dir, from_file_or_dir.getName());

				FileUtil.mkdirs(new_parent);

				for (int i=0;i<files.length;i++) {

					File	from_file	= files[i];

					copyFileOrDirectory(from_file, new_parent);
				}
			} else {

				File target = new File( to_parent_dir, from_file_or_dir.getName());

				if (!copyFile(	from_file_or_dir, target)) {

					throw (new IOException("File copy from " + from_file_or_dir + " to " + target + " failed"));
				}
			}
		}

		/**
		 * Returns the file handle for the given filename or it's
		 * equivalent .bak backup file if the original doesn't exist
		 * or is 0-sized.	If neither the original nor the backup are
		 * available, a null handle is returned.
		 * @param _filename root name of file
		 * @return file if successful, null if failed
		 */
		public static File getFileOrBackup(final String _filename) {
			try {
				File file = new File(_filename);
				//make sure the file exists and isn't zero-length
				if (file.length() <= 1L) {
					//if so, try using the backup file
					File bakfile = new File(_filename + ".bak");
					if (bakfile.length() <= 1L) {
						return null;
					}
					else return bakfile;
				}
				else return file;
			}
			catch (Exception e) {
				Debug.out(e);
				return null;
			}
		}

		public static File
		getJarFileFromClass(
			Class		cla )
		{
			try {
				String str = cla.getName();

				str = str.replace('.', '/') + ".class";

					URL url = cla.getClassLoader().getResource(str);

					if (url != null) {

						String	url_str = url.toExternalForm();

						if (url_str.startsWith("jar:file:")) {

							File jar_file = FileUtil.getJarFileFromURL(url_str);

							if (jar_file != null && jar_file.exists()) {

								return (jar_file);
							}
						}
					}
			} catch (Throwable e) {

				Debug.printStackTrace(e);
			}

				return (null);
		}

		public static File
	getJarFileFromURL(
		String		url_str )
		{
			if (url_str.startsWith("jar:file:")) {

					// java web start returns a url like "jar:file:c:/sdsd" which then fails as the file
					// part doesn't start with a "/". Add it in!
				// here's an example
				// jar:file:C:/Documents%20and%20Settings/stuff/.javaws/cache/http/Dparg.homeip.net/P9090/DMazureus-jnlp/DMlib/XMAzureus2.jar1070487037531!/org/gudy/azureus2/internat/MessagesBundle.properties

					// also on Mac we don't get the spaces escaped

				url_str = url_str.replaceAll(" ", "%20");

					if (!url_str.startsWith("jar:file:/")) {


						url_str = "jar:file:/".concat(url_str.substring(9));
					}

					try {
							// 	you can see that the '!' must be present and that we can safely use the last occurrence of it

						int posPling = url_str.lastIndexOf('!');

						String jarName = url_str.substring(4, posPling);

							//				System.out.println("jarName: " + jarName);

						URI uri;

						try {
							uri = URI.create(jarName);

							if (!new File(uri).exists()) {

								throw (new FileNotFoundException());
							}
						} catch (Throwable e) {

							jarName = "file:/" + UrlUtils.encode(jarName.substring( 6));

							uri = URI.create(jarName);
						}

						File jar = new File(uri);

						return (jar);

					} catch (Throwable e) {

						Debug.printStackTrace(e);
					}
			}

			return (null);
		}

		public static boolean
	renameFile(
		File		from_file,
		File		to_file )
		{
				return renameFile(from_file, to_file, true);
			}


		public static boolean
		renameFile(
				File				from_file,
				File				to_file,
				boolean		 fail_on_existing_directory)
		{
			return renameFile(from_file, to_file, fail_on_existing_directory, null);
		}

		public static boolean renameFile(
			File				from_file,
			File				to_file,
			boolean		 fail_on_existing_directory,
			FileFilter	file_filter
	) {

			if (!from_file.exists()) {

				Debug.out("renameFile: source file '" + from_file + "' doesn't exist, failing");

				return (false);
			}

				/**
				 * If the destination exists, we only fail if requested.
				 */
				if (to_file.exists() && (fail_on_existing_directory || from_file.isFile() || to_file.isFile())) {

					Debug.out("renameFile: target file '" + to_file + "' already exists, failing");

						return (false);
				}
			File to_file_parent = to_file.getParentFile();
			if (!to_file_parent.exists()) {FileUtil.mkdirs(to_file_parent);}

			if (from_file.isDirectory()) {

				File[] files = null;
				if (file_filter != null) {files = from_file.listFiles(file_filter);}
				else {files = from_file.listFiles();}

				if (files == null) {

						// empty dir

					return (true);
				}

				int	last_ok = 0;

				if (!to_file.exists()) {to_file.mkdir();}

				for (int i=0;i<files.length;i++) {

					File	ff = files[i];
				File	tf = new File( to_file, ff.getName());

					try {
						if (renameFile( ff, tf, fail_on_existing_directory, file_filter)) {

							last_ok++;

						} else {

							break;
						}
					} catch (Throwable e) {

						Debug.out("renameFile: failed to rename file '" + ff.toString() + "' to '"
									+ tf.toString() + "'", e);

						break;
					}
				}

				if (last_ok == files.length) {

					File[]	remaining = from_file.listFiles();

					if (remaining != null && remaining.length > 0) {
						// This might be important or not. We'll make it a debug message if we had a filter,
						// or log it normally otherwise.
						if (file_filter == null) {
							Debug.out("renameFile: files remain in '" + from_file.toString()
									+ "', not deleting");
						}
						else {
							/* Should we log this? How should we log this? */
							return true;
						}

					} else {

						if (!from_file.delete()) {
							Debug.out("renameFile: failed to delete '" + from_file.toString() + "'");
						}
					}

					return (true);
				}

					// recover by moving files back

					for (int i=0;i<last_ok;i++) {

				File	ff = files[i];
				File	tf = new File( to_file, ff.getName());

					try {
						// null - We don't want to use the file filter, it only refers to source paths.
										if (!renameFile( tf, ff, false, null)) {
							Debug.out("renameFile: recovery - failed to move file '" + tf.toString()
										+ "' to '" + ff.toString() + "'");
						}
					} catch (Throwable e) {
						Debug.out("renameFile: recovery - failed to move file '" + tf.toString()
									+ "' to '" + ff.toString() + "'", e);

					}
					}

					return (false);

			} else {

				boolean	copy_and_delete = COConfigurationManager.getBooleanParameter("Copy And Delete Data Rather Than Move");

				if (copy_and_delete) {

						boolean	move_if_same_drive = COConfigurationManager.getBooleanParameter("Move If On Same Drive");

					if (move_if_same_drive) {

							// FileSystem class available from Java 7, boo, just do a hack for windowz

						if (Constants.isWindows) {

							try {
								String str1 = from_file.getCanonicalPath();
								String str2 = to_file.getCanonicalPath();

									char drive1 = ':';
									char drive2 = ' ';

								if (str1.length() > 2 && str1.charAt(1) == ':') {

									drive1 = Character.toLowerCase(str1.charAt( 0));
								}
									if (str2.length() > 2 && str2.charAt(1) == ':') {

									drive2 = Character.toLowerCase(str2.charAt( 0));
								}

									if (drive1 == drive2) {

										copy_and_delete = false;
									}
							} catch (Throwable e) {

							}
						}
					}
				}

			if (	(!copy_and_delete) &&
					from_file.renameTo(to_file)) {

				return (true);

			} else {
				boolean		success	= false;

					// can't rename across file systems under Linux - try copy+delete

				FileInputStream		fis = null;

				FileOutputStream	fos = null;

				try {
					fis = new FileInputStream(from_file);

					fos = new FileOutputStream(to_file);

					byte[]	buffer = new byte[65536];

					while (true) {

						int	len = fis.read(buffer);

						if (len <= 0) {

							break;
						}

						fos.write(buffer, 0, len);
					}

					fos.close();

					fos	= null;

					fis.close();

					fis = null;

					if (!from_file.delete()) {
						Debug.out("renameFile: failed to delete '"
										+ from_file.toString() + "'");

						throw (new Exception("Failed to delete '" + from_file.toString() + "'"));
					}

					success	= true;

					return (true);

				} catch (Throwable e) {

					Debug.out("renameFile: failed to rename '" + from_file.toString()
									+ "' to '" + to_file.toString() + "'", e);

					return (false);

				} finally {

					if (fis != null) {

						try {
							fis.close();

						} catch (Throwable e) {
						}
					}

					if (fos != null) {

						try {
							fos.close();

						} catch (Throwable e) {
						}
					}

						// if we've failed then tidy up any partial copy that has been performed

					if (!success) {

						if (to_file.exists()) {

							to_file.delete();
						}
					}
				}
			}
			}
		}

		public static boolean
		writeStringAsFile(
			File		file,
			String		text )
		{
			try {
				return (writeBytesAsFile2( file.getAbsolutePath(), text.getBytes("UTF-8")));

			} catch (Throwable e) {

				Debug.out(e);

				return (false);
			}
		}

		public static void
		writeBytesAsFile(
			String filename,
			byte[] file_data )
		{
				// pftt, this is used by emp so can't fix signature to make more useful

			writeBytesAsFile2(filename, file_data);
		}

		public static boolean
		writeBytesAsFile2(
			String filename,
			byte[] file_data )
		{
			try {
				File file = new File(filename);

				if (!file.getParentFile().exists()) {

					file.getParentFile().mkdirs();
				}

				FileOutputStream out = new FileOutputStream(file);

				try {
					out.write(file_data);

				} finally {

						out.close();
				}

				return (true);

			} catch (Throwable t) {

				Debug.out("writeBytesAsFile:: error: ", t);

				return (false);
			}
		}

	public static boolean
	deleteWithRecycle(
		File		file,
		boolean		force_no_recycle) {
		if (COConfigurationManager.getBooleanParameter("Move Deleted Data To Recycle Bin" ) && !force_no_recycle) {

			try {
					final PlatformManager	platform	= PlatformManagerFactory.getPlatformManager();

					if (platform.hasCapability(PlatformManagerCapabilities.RecoverableFileDelete)) {

						platform.performRecoverableFileDelete( file.getAbsolutePath());

						return (true);

					} else {

						return ( file.delete());
					}
			} catch (PlatformManagerException e) {

				return ( file.delete());
			}
		} else {

			return ( file.delete());
		}
	}

	public static String translateMoveFilePath(
		String old_root,
		String new_root,
		String file_to_move) {
			// we're trying to get the bit from the file_to_move beyond the old_root and append it to the new_root

		if (!file_to_move.startsWith(old_root)) {

			return null;
		}

		if (old_root.equals(new_root)) {

				// roots are the same -> nothings gonna change

			return (file_to_move);
		}

		if (new_root.equals( file_to_move)) {

				// new root already the same as the from file, nothing to change

			return (file_to_move);
		}

		String file_suffix = file_to_move.substring(old_root.length());

		if (file_suffix.startsWith(File.separator)) {

			file_suffix = file_suffix.substring(1);

		} else {
				// hack to deal with special known case of this
				// old_root:	c:\fred\jim.dat
				// new_root:	c:\temp\egor\grtaaaa
				// old_file:	c:\fred\jim.dat.az!

			if (new_root.endsWith( File.separator)) {

				Debug.out("Hmm, this is not going to work out well... " + old_root + ", " + new_root + ", " + file_to_move);

			} else {

					// deal with case where new root already has the right suffix

				if (new_root.endsWith( file_suffix)) {

					return (new_root);
				}

				return (new_root + file_suffix);
			}
		}

		if (new_root.endsWith(File.separator)) {

			new_root = new_root.substring(0, new_root.length()-1);
		}

		return new_root + File.separator + file_suffix;
	}

	public static void runAsTask(
		AzureusCoreOperationTask	task) {
		AzureusCore	core = AzureusCoreFactory.getSingleton();

		core.createOperation(AzureusCoreOperation.OP_FILE_MOVE, task);
	}

	/**
	 * Makes Directories as long as the directory isn't directly in Volumes (OSX)
	 * @param f
	 * @return
	 */
	public static boolean mkdirs(File f) {
		if (Constants.isOSX) {
			Pattern pat = Pattern.compile("^(/Volumes/[^/]+)");
			Matcher matcher = pat.matcher(f.getParent());
			if (matcher.find()) {
				String sVolume = matcher.group();
				File fVolume = new File(sVolume);
				if (!fVolume.isDirectory()) {
					Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING, sVolume
							+ " is not mounted or not available."));
					return false;
				}
			}
		}
		return f.mkdirs();
	}

	/**
	 * Gets the extension of a file name, ensuring we don't go into the path
	 *
	 * @param fName	File name
	 * @return extension, with the '.'
	 */
	public static String getExtension(String fName) {
		final int fileSepIndex = fName.lastIndexOf(File.separator);
		final int fileDotIndex = fName.lastIndexOf('.');
		if (fileSepIndex == fName.length() - 1 || fileDotIndex == -1
				|| fileSepIndex > fileDotIndex) {
			return "";
		}

		return fName.substring(fileDotIndex);
	}

	public static String readFileAsString(
		File	file,
		int		size_limit,
		String charset)

		throws IOException
	{
		FileInputStream fis = new FileInputStream(file);
		try {
			return readInputStreamAsString(fis, size_limit, charset);
		} finally {

			fis.close();
		}
	}

	public static String readFileAsString(
		File	file,
		int		size_limit )

		throws IOException
	{
		FileInputStream fis = new FileInputStream(file);
		try {
			return readInputStreamAsString(fis, size_limit);
		} finally {

			fis.close();
		}
	}

	public static String readGZippedFileAsString(
		File	file,
		int		size_limit )

		throws IOException
	{
		FileInputStream fis = new FileInputStream(file);

		try {
			GZIPInputStream zis = new GZIPInputStream(fis);

			return readInputStreamAsString(zis, size_limit);
		} finally {

			fis.close();
		}
	}
	public static String readInputStreamAsString(
		InputStream is,
		int		size_limit )

		throws IOException
	{
		return readInputStreamAsString(is, size_limit, "ISO-8859-1");
	}

	public static String readInputStreamAsString(
		InputStream 	is,
		int				size_limit,
		String 			charSet)

		throws IOException
	{
		StringBuilder result = new StringBuilder(1024);

		byte[] buffer = new byte[64*1024];

		while (true) {

			int len = is.read(buffer);

			if (len <= 0) {

				break;
			}

			result.append(new String(buffer, 0, len, charSet));

			if (size_limit >= 0 && result.length() > size_limit) {

				result.setLength(size_limit);

				break;
			}
		}

		return (result.toString());
	}

	public static String readInputStreamAsStringWithTruncation(
		InputStream 	is,
		int				size_limit )

		throws IOException
	{
		StringBuilder result = new StringBuilder(1024);

		byte[] buffer = new byte[64*1024];

		try {
			while (true) {

				int len = is.read(buffer);

				if (len <= 0) {

					break;
				}

				result.append(new String(buffer, 0, len, "ISO-8859-1"));

				if (size_limit >= 0 && result.length() > size_limit) {

					result.setLength(size_limit);

					break;
				}
			}
		} catch (SocketTimeoutException e) {
		}

		return (result.toString());
	}

	public static String readFileEndAsString(
		File	file,
		int		size_limit )

		throws IOException
	{
		return (readFileEndAsString( file, size_limit, "ISO-8859-1"));
	}

	public static String readFileEndAsString(
		File	file,
		int		size_limit,
		String	charset )

		throws IOException
	{
		FileInputStream	fis = new FileInputStream(file);

		try {
			if (file.length() > size_limit) {

					// doesn't really work with multi-byte chars but woreva

				fis.skip(file.length() - size_limit);
			}

			StringBuilder result = new StringBuilder(1024);

			byte[]	buffer = new byte[64*1024];

			while (true) {

				int	len = fis.read(buffer);

				if (len <= 0) {

					break;
				}

					// doesn't really work with multi-byte chars but woreva

				result.append(new String( buffer, 0, len, charset));

				if (result.length() > size_limit) {

					result.setLength(size_limit);

					break;
				}
			}

			return ( result.toString());

		} finally {

			fis.close();
		}
	}

	public static byte[]
	readInputStreamAsByteArray(
		InputStream		is )

			throws IOException
	{
		return (readInputStreamAsByteArray( is, Integer.MAX_VALUE));
	}

	public static byte[]
	readInputStreamAsByteArray(
		InputStream		is,
		int				size_limit )

		throws IOException
	{
		ByteArrayOutputStream	baos = new ByteArrayOutputStream(32*1024);

		byte[]	buffer = new byte[32*1024];

		while (true) {

			int	len = is.read(buffer);

			if (len <= 0) {

				break;
			}

			baos.write(buffer, 0, len);

			if (baos.size() > size_limit) {

				throw (new IOException("size limit exceeded"));
			}
		}

		return ( baos.toByteArray());
	}

	public static byte[] readFileAsByteArray(File file)
			throws IOException {
		ByteArrayOutputStream	baos = new ByteArrayOutputStream((int)file.length());
		byte[]	buffer = new byte[32*1024];
		InputStream is = new FileInputStream(file);
		try {
			while (true) {
				int	len = is.read(buffer);
				if (len <= 0) {
					break;
				}
				baos.write(buffer, 0, len);
			}
			return ( baos.toByteArray());
		} finally {
			is.close();
		}
	}

	public final static boolean getUsableSpaceSupported() {
		return reflectOnUsableSpace != null;
	}

	public final static long getUsableSpace(File f) {
		try {
			return ((Long)reflectOnUsableSpace.invoke(f)).longValue();

		} catch ( Throwable e) {

			return -1;
		}
	}

	public static boolean
	canReallyWriteToAppDirectory() {
		if (!FileUtil.getApplicationFile("bogus").getParentFile().canWrite()) {

			return (false);
		}

			// handle vista+ madness

		if (Constants.isWindowsVistaOrHigher) {

			try {
				File write_test = FileUtil.getApplicationFile("_az_.dll");

					// should fail if no perms, but sometimes it's created in
					// virtualstore (if ran from java(w).exe for example)

				FileOutputStream fos = new FileOutputStream(write_test);

				try {
					fos.write(32);

				} finally {

					fos.close();
				}

				write_test.delete();

					// look for a file to try and rename. Unfortunately someone renamed License.txt to GPL.txt and screwed this up in 3020...

				File rename_test = FileUtil.getApplicationFile("License.txt");

				if (!rename_test.exists()) {

					rename_test = FileUtil.getApplicationFile("GPL.txt");
				}

				if (!rename_test.exists()) {

					File[] files = write_test.getParentFile().listFiles();

					if (files != null) {

						for (File f: files) {

							String name = f.getName();

							if (name.endsWith(".txt") || name.endsWith(".log")) {

								rename_test = f;

								break;
							}
						}
					}
				}

				if (rename_test.exists()) {

					File target = new File(rename_test.getParentFile(), rename_test.getName() + ".bak");

					target.delete();

					rename_test.renameTo(target);

					if (rename_test.exists()) {

						return (false);
					}

					target.renameTo(rename_test);

				} else {

					Debug.out("Failed to find a suitable file for the rename test");

						// let's assume we can't to be on the safe side

					return (false);
				}
			} catch (Throwable e) {

				return (false);
			}
		}

		return (true);
	}

	public static boolean
	canWriteToDirectory(
		File		dir) {
			// (dir).canWrite() seems to return true for local file systems at least on windows regardless
			// of effective permissions :(

		if (!dir.isDirectory()) {

			return (false);
		}

		try {
			File temp = AETemporaryFileHandler.createTempFileInDir(dir);

			if (!temp.delete()) {

				temp.deleteOnExit();
			}

			return (true);

		} catch (Throwable e) {

			return (false);
		}
	}
		/**
		 * Gets the encoding that should be used when writing script files (currently only
		 * tested for windows as this is where an issue can arise...)
		 * We also only test based on the user-data directory name to see if an explicit
		 * encoding switch is requried...
		 * @return null - use default
		 */

	private static boolean 	sce_checked;
	private static String	script_encoding;

	public static String getScriptCharsetEncoding() {
		synchronized(FileUtil.class) {

			if (sce_checked) {

				return (script_encoding);
			}

			sce_checked = true;

			String	file_encoding 	= System.getProperty("file.encoding", null);
			String	jvm_encoding	= System.getProperty("sun.jnu.encoding", null);

			if (file_encoding == null || jvm_encoding == null || file_encoding.equals( jvm_encoding)) {

				return (null);
			}

			try {

				String	test_str = SystemProperties.getUserPath();

				if (!new String(test_str.getBytes( file_encoding ), file_encoding).equals( test_str)) {

					if (new String(test_str.getBytes( jvm_encoding ), jvm_encoding).equals( test_str)) {

						Debug.out("Script encoding determined to be " + jvm_encoding + " instead of " + file_encoding);

						script_encoding = jvm_encoding;
					}
				}
			} catch (Throwable e) {
			}

			return (script_encoding);
		}
	}

	public static InternedFile
	internFileComponents(
		File		file) {
		if (file == null) {

			return (null);
		}

		List<String>	comps = new ArrayList<String>(100);

		File comp = file;

		while (comp != null) {

			String name = comp.getName();

			if (name.length() > 0) {

				comps.add(StringInterner.intern( name));

			} else {

				String path = comp.getPath();

				if (path.length() > 0) {

					comps.add(StringInterner.intern( path));
				}
			}

			comp = comp.getParentFile();
		}

		InternedFile res = new InternedFile(comps.toArray(new String[comps.size()]));

		if (!res.getFile().equals( file)) {

			Debug.out("intern failed for " + file + " (" + res.getFile() + ")");
		}

		return (res);
	}

	public static class
	InternedFile
	{
		private final String[]	comps;

		private InternedFile(
			String[]	_comps) {
			comps	= _comps;
		}

		public File
		getFile() {
			if (comps.length == 0) {

				return (new File(""));

			} else if (comps.length == 1) {

				return (new File( comps[0]));

			} else {

				StringBuilder b = new StringBuilder(256);

				for (int i=comps.length-1;i>=0;i--) {

					if (b.length() > 0) {

						b.append(File.separatorChar);
					}

					b.append(comps[i]);
				}

				return (new File( b.toString()));
			}
		}

		@Override
		public boolean equals(
			Object	other) {
			if (other instanceof InternedFile) {

				InternedFile o = (InternedFile)other;

				if (comps.length != o.comps.length) {

					return (false);
				}

				for (int i=comps.length-1;i>= 0; i--) {

					if (!comps[i].equals( o.comps[i])) {

						return (false);
					}
				}

				return (true);

			} else if (other instanceof File) {

				return (getFile().equals( other));

			} else {

				return (false);
			}
		}

		@Override
		public int hashCode() {
			int	h = 0;

			for (String s: comps) {

				h += s.hashCode();
			}

			return (h);
		}
	}
}
