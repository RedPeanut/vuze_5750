/*
 * Created on 15-Nov-2004
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

package org.gudy.azureus2.core3.download.impl;

import java.io.*;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.gudy.azureus2.core3.category.Category;
import org.gudy.azureus2.core3.category.CategoryManager;
import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.config.ParameterListener;
import org.gudy.azureus2.core3.disk.DiskManagerFactory;
import org.gudy.azureus2.core3.disk.DiskManagerFileInfo;
import org.gudy.azureus2.core3.download.*;
import org.gudy.azureus2.core3.ipfilter.IpFilterManagerFactory;
import org.gudy.azureus2.core3.logging.LogEvent;
import org.gudy.azureus2.core3.logging.LogIDs;
import org.gudy.azureus2.core3.logging.LogRelation;
import org.gudy.azureus2.core3.logging.Logger;
import org.gudy.azureus2.core3.peer.PEPeerSource;
import org.gudy.azureus2.core3.torrent.*;
import org.gudy.azureus2.core3.tracker.client.TRTrackerAnnouncer;
import org.gudy.azureus2.core3.util.*;

import com.aelitis.azureus.core.AzureusCoreFactory;
import com.aelitis.azureus.core.util.CopyOnWriteList;
import com.aelitis.azureus.core.util.CopyOnWriteMap;
import com.aelitis.azureus.core.util.LinkFileMap;

import hello.util.Log;
import hello.util.SingleCounter0;

/**
 * @author parg
 * Overall aim of this is to stop updating the torrent file itself and update something
 * Azureus owns. To this end a file based on torrent hash is created in user-dir/active
 * It is actually just a copy of the torrent file
 */

public class
DownloadManagerStateImpl
	implements DownloadManagerState, ParameterListener
{
	private static final int	VER_INCOMING_PEER_SOURCE	= 1;
	private static final int	VER_CURRENT					= VER_INCOMING_PEER_SOURCE;


	private static final LogIDs LOGID = LogIDs.DISK;
	private static final String			RESUME_KEY				= "resume";
	private static final String			TRACKER_CACHE_KEY		= "tracker_cache";
	private static final String			ATTRIBUTE_KEY			= "attributes";
	private static final String			AZUREUS_PROPERTIES_KEY	= "azureus_properties";

	private static final File			ACTIVE_DIR;

	public static boolean SUPPRESS_FIXUP_ERRORS = false;

	static{

		ACTIVE_DIR = FileUtil.getUserFile("active");

		if (!ACTIVE_DIR.exists()) {

			FileUtil.mkdirs(ACTIVE_DIR);
		}
	}

	private static final Random	random = RandomUtils.SECURE_RANDOM;

	private static final Map	default_parameters;
	private static final Map	default_attributes;

	static{
		default_parameters  = new HashMap();

		for (int i=0;i<PARAMETERS.length;i++) {

			default_parameters.put(PARAMETERS[i][0], PARAMETERS[i][1]);
		}

		default_attributes  = new HashMap();

		for (int i=0;i<ATTRIBUTE_DEFAULTS.length;i++) {

			default_attributes.put(ATTRIBUTE_DEFAULTS[i][0], ATTRIBUTE_DEFAULTS[i][1]);
		}

		// only add keys that will point to Map objects here!
		TorrentUtils.registerMapFluff(new String[] {TRACKER_CACHE_KEY,RESUME_KEY});
	}

	private static final AEMonitor	class_mon	= new AEMonitor("DownloadManagerState:class");

	static final Map<HashWrapper,DownloadManagerStateImpl>		state_map 					= new HashMap<HashWrapper,DownloadManagerStateImpl>();

	static{
		ParameterListener listener =
			new ParameterListener() {
				public void parameterChanged(
					String parameterName) {
					List<DownloadManagerStateImpl> states;

					synchronized(state_map) {

						states = new ArrayList<DownloadManagerStateImpl>( state_map.values());
					}

					for (DownloadManagerStateImpl state: states) {

						try {
							state.parameterChanged(parameterName);

						} catch (Throwable e) {

							Debug.out(e);
						}
					}
				}
			};

		COConfigurationManager.addParameterListener("Max.Peer.Connections.Per.Torrent.When.Seeding", listener);
		COConfigurationManager.addParameterListener("Max.Peer.Connections.Per.Torrent.When.Seeding.Enable", listener);
		COConfigurationManager.addParameterListener("Max.Peer.Connections.Per.Torrent", listener);
		COConfigurationManager.addParameterListener("Max Uploads", listener);
		COConfigurationManager.addParameterListener("Max Uploads Seeding", listener);
		COConfigurationManager.addParameterListener("Max Seeds Per Torrent", listener);
		COConfigurationManager.addParameterListener("enable.seedingonly.maxuploads", listener);
	}


	private static final Map					global_state_cache			= new HashMap();
	private static final ArrayList			global_state_cache_wrappers	= new ArrayList();

	private static final CopyOnWriteMap<String,CopyOnWriteList<DownloadManagerStateAttributeListener>> global_listeners_read_map_cow  = new CopyOnWriteMap<String,CopyOnWriteList<DownloadManagerStateAttributeListener>>();
	private static final CopyOnWriteMap<String,CopyOnWriteList<DownloadManagerStateAttributeListener>> global_listeners_write_map_cow = new CopyOnWriteMap<String,CopyOnWriteList<DownloadManagerStateAttributeListener>>();


	private DownloadManagerImpl			download_manager;

	private final TorrentUtils.ExtendedTorrent	torrent;

	private boolean						write_required;

	private Category 	category;

	private final CopyOnWriteList<DownloadManagerStateListener>		listeners_cow	= new CopyOnWriteList<DownloadManagerStateListener>();

	private final CopyOnWriteMap<String,CopyOnWriteList<DownloadManagerStateAttributeListener>> listeners_read_map_cow  = new CopyOnWriteMap<String,CopyOnWriteList<DownloadManagerStateAttributeListener>>();
	private final CopyOnWriteMap<String,CopyOnWriteList<DownloadManagerStateAttributeListener>> listeners_write_map_cow = new CopyOnWriteMap<String,CopyOnWriteList<DownloadManagerStateAttributeListener>>();


	private Map			parameters;
	private Map			attributes;

	private final AEMonitor	this_mon	= new AEMonitor("DownloadManagerState");

	private int supressWrites = 0;

	private static final ThreadLocal		tls_wbr	=
		new ThreadLocal() {
			public Object initialValue() {
				return (new ArrayList(1));
			}
		};

	private static DownloadManagerState
	getDownloadState(
		DownloadManagerImpl				download_manager,
		TOTorrent						original_torrent,
		TorrentUtils.ExtendedTorrent	target_torrent )

		throws TOTorrentException
	{
		byte[]	hash	= target_torrent.getHash();

		DownloadManagerStateImpl	res	= null;

		try {
			class_mon.enter();

			HashWrapper	hash_wrapper = new HashWrapper(hash);

			res = (DownloadManagerStateImpl)state_map.get(hash_wrapper);

			if (res == null) {

				res = new DownloadManagerStateImpl(download_manager, target_torrent);

				state_map.put(hash_wrapper, res);

			} else {

					// if original state was created without a download manager,
					// bind it to this one

				if (res.getDownloadManager() == null && download_manager != null) {

					res.setDownloadManager(download_manager);
				}

				if (original_torrent != null) {

					res.mergeTorrentDetails(original_torrent);
				}
			}
		} finally {

			class_mon.exit();
		}

		return (res);
	}


	public static DownloadManagerState
	getDownloadState(
		TOTorrent		original_torrent )

		throws TOTorrentException
	{
		byte[]	torrent_hash = original_torrent.getHash();

		// System.out.println("getDownloadState: hash = " + ByteFormatter.encodeString(torrent_hash));

		TorrentUtils.ExtendedTorrent saved_state	= null;

		File	saved_file = getStateFile(torrent_hash);

		if (saved_file.exists()) {

			try {
				saved_state = TorrentUtils.readDelegateFromFile(saved_file, false);

			} catch (Throwable e) {

				Debug.out("Failed to load download state for " + saved_file, e);
			}
		}

			// if saved state not found then recreate from original torrent

		if (saved_state == null) {

			TorrentUtils.copyToFile(original_torrent, saved_file);

			saved_state = TorrentUtils.readDelegateFromFile(saved_file, false);
		}

		return (getDownloadState( null, original_torrent, saved_state));
	}

	protected static DownloadManagerState getDownloadState(
		DownloadManagerImpl	download_manager,
		String				torrentFile,
		byte[]				torrentHash,
		boolean				inactive )

		throws TOTorrentException
	{
		boolean	discard_pieces = state_map.size() > 32;
		// System.out.println("getDownloadState: hash = " + (torrent_hash==null?"null":ByteFormatter.encodeString(torrent_hash) + ", file = " + torrent_file));
		TOTorrent						original_torrent	= null;
		TorrentUtils.ExtendedTorrent 	savedState			= null;
			// first, if we already have the hash then see if we can load the saved state
		if (torrentHash != null) {
			File	saved_file = getStateFile(torrentHash);
			if (saved_file.exists()) {
				try {
					Map	cached_state = (Map)global_state_cache.remove(new HashWrapper( torrentHash));
					if (cached_state != null) {
						CachedStateWrapper wrapper = new CachedStateWrapper(download_manager, torrentFile, torrentHash, cached_state, inactive);
						global_state_cache_wrappers.add(wrapper);
						savedState	= wrapper;
					} else {
						savedState = TorrentUtils.readDelegateFromFile(saved_file, discard_pieces);
					}
				} catch (Throwable e) {
					Debug.out("Failed to load download state for " + saved_file);
				}
			}
		}
			// if saved state not found then recreate from original torrent if required
		if (savedState == null) {
			original_torrent = TorrentUtils.readDelegateFromFile(new File(torrentFile), discard_pieces);
			torrentHash = original_torrent.getHash();
			File	saved_file = getStateFile(torrentHash);
			if (saved_file.exists()) {
				try {
					savedState = TorrentUtils.readDelegateFromFile(saved_file, discard_pieces);
				} catch (Throwable e) {
					Debug.out("Failed to load download state for " + saved_file);
				}
			}
			if (savedState == null) {
				// we must copy the torrent as we want one independent from the
				// original (someone might still have references to the original
				// and do stuff like write it somewhere else which would screw us
				// up)
				TorrentUtils.copyToFile(original_torrent, saved_file);
				savedState = TorrentUtils.readDelegateFromFile(saved_file, discard_pieces);
			}
		}
		DownloadManagerState res = getDownloadState(download_manager, original_torrent, savedState);
		if (inactive) {
			res.setActive(false);
		}
		return (res);
	}

	protected static File
	getStateFile(
		byte[]		torrent_hash) {
		return (new File( ACTIVE_DIR, ByteFormatter.encodeString( torrent_hash ) + ".dat"));
	}

	protected static File
	getGlobalStateFile() {
		return (new File( ACTIVE_DIR, "cache.dat"));
	}

	public static void loadGlobalStateCache() {
		File file = getGlobalStateFile();

		if (!file.canRead()) {

			return;
		}

		try {

			BufferedInputStream is = new BufferedInputStream(new GZIPInputStream(new FileInputStream( file)));

			try {

				Map	map = BDecoder.decode(is);

				List	cache = (List)map.get("state");

				if (cache != null) {

					for (int i=0;i<cache.size();i++) {

						Map	entry = (Map)cache.get(i);

						byte[]	hash = (byte[])entry.get("hash");

						if (hash != null) {

							global_state_cache.put(new HashWrapper( hash ), entry);
						}
					}
				}

				is.close();

			} catch ( IOException e) {

				Debug.printStackTrace(e);
			} finally {

				try {
					is.close();

				} catch (Throwable e) {
				}
			}
		} catch (Throwable e) {

			Debug.printStackTrace(e);
		}
	}

	public static void saveGlobalStateCache() {
		try {
			class_mon.enter();

			Map	map = new HashMap();

			List	cache = new ArrayList();

			map.put("state", cache);

			Iterator	it = state_map.values().iterator();

			while (it.hasNext()) {

				DownloadManagerState dms = (DownloadManagerState)it.next();

				DownloadManager dm = dms.getDownloadManager();

				if (dm != null && dm.isPersistent()) {

					try {
						Map	state = CachedStateWrapper.export(dms);

						cache.add(state);

					} catch (Throwable e) {

						Debug.printStackTrace(e);
					}
				}
			}

			GZIPOutputStream	os = new GZIPOutputStream(new FileOutputStream( getGlobalStateFile()));

			try {

				os.write(BEncoder.encode( map));

				os.close();

			} catch (IOException e) {

				Debug.printStackTrace(e);

				try {
					os.close();

				} catch (IOException f) {

				}
			}
		} catch (Throwable e) {

			Debug.printStackTrace(e);

		} finally {

			class_mon.exit();

		}
	}

	public static void discardGlobalStateCache() {
		getGlobalStateFile().delete();

		for ( int i=0;i<global_state_cache_wrappers.size();i++) {

			((CachedStateWrapper)global_state_cache_wrappers.get(i)).clearCache();
		}

		global_state_cache_wrappers.clear();
		global_state_cache_wrappers.trimToSize();
	}

	public static void importDownloadState(
		File		source_dir,
		byte[]		download_hash )

		throws DownloadManagerException
	{
		String	hash_str = ByteFormatter.encodeString(download_hash);

		String	state_file = hash_str + ".dat";

		File	target_state_file 	= new File(ACTIVE_DIR, state_file);
		File	source_state_file 	= new File(source_dir, state_file);

		if (!source_state_file.exists()) {

			throw (new DownloadManagerException("Source state file missing: " + source_state_file));
		}

		if (target_state_file.exists()) {

			target_state_file.delete();

			//throw (new DownloadManagerException("Target state file already exists: " + target_state_file));
		}

		if (!FileUtil.copyFile( source_state_file, target_state_file)) {

			throw (new DownloadManagerException("Failed to copy state file: " + source_state_file + " -> " + target_state_file));
		}

		File	source_state_dir = new File(source_dir, hash_str);

		if (source_state_dir.exists()) {

			try {
				FileUtil.copyFileOrDirectory(source_state_dir, ACTIVE_DIR);

			} catch (Throwable e) {

				target_state_file.delete();

				throw (new DownloadManagerException("Failed to copy state dir: " + source_dir + " -> " + ACTIVE_DIR, e));
			}
		}
	}

	public static void deleteDownloadState(
		byte[]		download_hash )

		throws DownloadManagerException
	{
		deleteDownloadState(ACTIVE_DIR, download_hash);
	}

	public static void deleteDownloadState(
		File		source_dir,
		byte[]		download_hash )

		throws DownloadManagerException
	{
		String	hash_str = ByteFormatter.encodeString(download_hash);

		String	state_file = hash_str + ".dat";

		File	target_state_file 	= new File(source_dir, state_file);

		if (target_state_file.exists()) {

			if (!target_state_file.delete()) {

				throw (new DownloadManagerException("Failed to delete state file: " + target_state_file));
			}
		}

		File	target_state_dir = new File(source_dir, hash_str);

		if (target_state_dir.exists()) {

			if (!FileUtil.recursiveDelete( target_state_dir)) {

				throw (new DownloadManagerException("Failed to delete state dir: " + target_state_dir));
			}
		}
	}

	protected DownloadManagerStateImpl(
		DownloadManagerImpl				_download_manager,
		TorrentUtils.ExtendedTorrent	_torrent) {
		download_manager	= _download_manager;
		torrent				= _torrent;

		attributes = torrent.getAdditionalMapProperty(ATTRIBUTE_KEY);

		if (attributes == null) {

			attributes	= new HashMap();
        }

        String cat_string = getStringAttribute(AT_CATEGORY);

        if (cat_string != null) {

        	Category cat = CategoryManager.getCategory(cat_string);

        	if (cat != null) {

        		setCategory(cat);
        	}
        }

        parameters	= getMapAttribute(AT_PARAMETERS);

        if (parameters == null) {

        	parameters	= new HashMap();
        }

        	// note that version will be -1 for the first time through this code

        int	version = getIntAttribute(AT_VERSION);

        if (version < VER_INCOMING_PEER_SOURCE) {

        		// migrate by adding incoming as enabled - only needed if we have any specified as other
        		// code takes care of the case where we have none

        	if (getPeerSources().length > 0) {

        		if (PEPeerSource.isPeerSourceEnabledByDefault( PEPeerSource.PS_INCOMING)) {

        			setPeerSourceEnabled(PEPeerSource.PS_INCOMING, true);
        		}
        	} else {

        			// set default for newly added torrent

				setPeerSources( PEPeerSource.getDefaultEnabledPeerSources());
        	}
        }

        long flags = getFlags();

        if ((flags & FLAG_DISABLE_IP_FILTER ) != 0) {

        	try {
        		IpFilterManagerFactory.getSingleton().getIPFilter().addExcludedHash( torrent.getHash());

        	} catch (Throwable e) {

        		Debug.out(e);
        	}
        }

        if (version < VER_CURRENT) {

        	setIntAttribute(AT_VERSION, VER_CURRENT);
        }
	}

	public void parameterChanged(
		String parameterName) {
			// get any listeners to pick up new values as their defaults are based on core params

		informWritten(AT_PARAMETERS);
	}

	public DownloadManager
	getDownloadManager() {
		return (download_manager);
	}

	protected void setDownloadManager(
		DownloadManagerImpl		dm) {
		download_manager	= dm;
	}

	public File
	getStateFile() {
		try {
			File	parent = new File( ACTIVE_DIR, ByteFormatter.encodeString( torrent.getHash()) + File.separatorChar);

			return ( StringInterner.internFile(parent));

		} catch (Throwable e) {

			Debug.printStackTrace(e);

			return (null);
		}
	}

	public void clearTrackerResponseCache() {
		setTrackerResponseCache(new HashMap());
	}

	public Map getTrackerResponseCache() {

		Map tracker_response_cache = null;

		tracker_response_cache = torrent.getAdditionalMapProperty(TRACKER_CACHE_KEY);

		if (tracker_response_cache == null)
			tracker_response_cache = new HashMap();

		return (tracker_response_cache);
	}

	public void setTrackerResponseCache(
		Map		value) {

		try {
			this_mon.enter();

			// System.out.println("setting download state/tracker cache for '" + new String(torrent.getName()));

			boolean	changed = !BEncoder.mapsAreIdentical(value, getTrackerResponseCache());

			if (changed) {

				write_required	= true;

				torrent.setAdditionalMapProperty(TRACKER_CACHE_KEY, value);
			}

		} finally {

			this_mon.exit();
		}
	}

	public Map
	getResumeData() {
		try {
			this_mon.enter();

			return ( torrent.getAdditionalMapProperty(RESUME_KEY));

		} finally {

			this_mon.exit();
		}
	}

	public void clearResumeData() {
		setResumeData(null);
	}

	public void setResumeData(
		Map	data) {
		try {
			this_mon.enter();

			// System.out.println("setting download state/resume data for '" + new String(torrent.getName()));

			if (data == null) {

				setLongAttribute(AT_RESUME_STATE, 1);

				torrent.removeAdditionalProperty(RESUME_KEY);

			} else {

				torrent.setAdditionalMapProperty(RESUME_KEY, data);

				boolean complete = DiskManagerFactory.isTorrentResumeDataComplete(this);

				setLongAttribute(AT_RESUME_STATE, complete?2:1);
			}

			write_required	= true;

		} finally {

			this_mon.exit();
		}

			// we need to ensure this is persisted now as it has implications regarding crash restarts etc

		save();
	}

	public boolean isResumeDataComplete() {
			// this is a cache of resume state to speed up startup

		long	state = getLongAttribute(AT_RESUME_STATE);

		if (state == 0) {

			// don't know

			boolean complete = DiskManagerFactory.isTorrentResumeDataComplete(this);

			setLongAttribute(AT_RESUME_STATE, complete?2:1);

			return (complete);

		} else {

			return (state == 2);
		}
	}

	public TOTorrent
	getTorrent() {
		return (torrent);
	}

	public void setActive(
		boolean		active) {
		torrent.setDiscardFluff(!active);
	}

	public void discardFluff() {
		torrent.setDiscardFluff(true);
	}

	public boolean exportState(
		File	target_dir) {
		try {
			this_mon.enter();

			save(true);

			byte[]	hash = torrent.getHash();

			String	hash_str = ByteFormatter.encodeString(hash);

			String	state_file = hash_str + ".dat";

			File	existing_state_file = new File(ACTIVE_DIR, state_file);
			File	target_state_file 	= new File(target_dir, state_file);

			if (!FileUtil.copyFile( existing_state_file, target_state_file)) {

				throw (new IOException("Failed to copy state file"));
			}

			File	existing_state_dir = new File(ACTIVE_DIR, hash_str);

			if (existing_state_dir.exists()) {

				FileUtil.copyFileOrDirectory(existing_state_dir, target_dir);
			}

			return (true);

		} catch (Throwable e) {

			Debug.out(e);

			return (false);

		} finally {

			this_mon.exit();
		}
	}

	public void suppressStateSave(boolean suppress) {
		if (suppress)
			supressWrites++;
		else if (supressWrites > 0)
			supressWrites--;
	}

	public void save() {
		save(false);
	}

	protected void save(boolean force) {
		if (supressWrites > 0 && !force) {

			return;
		}

 		boolean do_write;

		try {
			this_mon.enter();

			do_write = write_required;

			write_required = false;

		} finally {

			this_mon.exit();
		}

		if (do_write) {

			try {
				// System.out.println("writing download state for '" + new String(torrent.getName()));

				if (Logger.isEnabled())
					Logger.log(new LogEvent(torrent, LOGID, "Saving state for download '"
							+ TorrentUtils.getLocalisedName(torrent) + "'"));

				torrent.setAdditionalMapProperty(ATTRIBUTE_KEY, attributes);

				TorrentUtils.writeToFile(torrent, true);

			} catch (Throwable e) {
				Logger.log(new LogEvent(torrent, LOGID, "Saving state", e));
			}
		} else {

			// System.out.println("not writing download state for '" + new String(torrent.getName()));
		}
	}

	public void delete() {
		try {
			class_mon.enter();

			HashWrapper	wrapper = torrent.getHashWrapper();

			state_map.remove(wrapper);

	        TorrentUtils.delete(torrent);

			File	dir = new File( ACTIVE_DIR, ByteFormatter.encodeString( wrapper.getBytes()));

			if (dir.exists() && dir.isDirectory()) {

				FileUtil.recursiveDelete(dir);
			}
		} catch (Throwable e) {

	    	Debug.printStackTrace(e);

		} finally {

			class_mon.exit();
		}
	}

	protected void mergeTorrentDetails(
		TOTorrent	other_torrent) {
		try {
			boolean	write = TorrentUtils.mergeAnnounceURLs(other_torrent, torrent);

			// System.out.println("DownloadManagerState:mergeTorrentDetails -> " + write);

			if (write) {

				save();

				if (download_manager != null) {

					TRTrackerAnnouncer	client = download_manager.getTrackerClient();

					if (client != null) {

						// pick up any URL changes

						client.resetTrackerUrl(false);
					}
				}
			}
		} catch (Throwable e) {

			Debug.printStackTrace(e);
		}
	}

	public void setFlag(
		long		flag,
		boolean		set) {
		long	old_value = getLongAttribute(AT_FLAGS);

		long	new_value;

		if (set) {

			new_value = old_value | flag;

		} else {

			new_value = old_value & ~flag;
		}

		if (old_value != new_value) {

			setLongAttribute(AT_FLAGS, new_value);

			if ((old_value & FLAG_DISABLE_IP_FILTER ) != (new_value & FLAG_DISABLE_IP_FILTER)) {

	        	try {
	        		if ((new_value & FLAG_DISABLE_IP_FILTER ) != 0) {

	        			IpFilterManagerFactory.getSingleton().getIPFilter().addExcludedHash( torrent.getHash());

	        		} else {

	        			IpFilterManagerFactory.getSingleton().getIPFilter().removeExcludedHash( torrent.getHash());

	        		}
	        	} catch (Throwable e) {

	        		Debug.out(e);
	        	}
	        }
		}
	}

	public boolean getFlag(
		long	flag) {
		long	value = getLongAttribute(AT_FLAGS);

		return ((value & flag ) != 0);
	}

	public long getFlags() {
		return (getLongAttribute( AT_FLAGS));
	}

	public boolean parameterExists(String name) {
		return parameters.containsKey(name);
	}

	public void setParameterDefault(
		String	name) {
		try {
			this_mon.enter();

			Object	value = parameters.get(name);

			if (value == null) {

				return;
			}

				// gotta clone here otherwise we update the underlying  map and the setMapAttribute code
				// doesn't think it has changed

			parameters	= new LightHashMap(parameters);

			parameters.remove(name);

		} finally {

			this_mon.exit();
		}

		setMapAttribute(AT_PARAMETERS, parameters);
	}

	public long getLongParameter(
		String	name) {
		try {
			this_mon.enter();

			Object	value = parameters.get(name);

			if (value == null) {

				value = default_parameters.get(name);

				if (value == null) {

					Debug.out("Unknown parameter '" + name + "' - must be defined in DownloadManagerState");

					return (0);

				} else {

						// default overrides

						// **** note - if you add to these make sure you extend the parameter listeners
						// registered as well (see static initialiser at top)

					if (name == PARAM_MAX_UPLOADS_WHEN_SEEDING_ENABLED) {

						if (COConfigurationManager.getBooleanParameter("enable.seedingonly.maxuploads")) {

							value = Boolean.TRUE;
						}

					} else if (name == PARAM_MAX_UPLOADS_WHEN_SEEDING) {

						int	def = COConfigurationManager.getIntParameter("Max Uploads Seeding");

						value = new Integer(def);

					} else if (name == PARAM_MAX_UPLOADS) {

						int	def = COConfigurationManager.getIntParameter("Max Uploads");

						value = new Integer(def);

					} else if (name == PARAM_MAX_PEERS) {

						int	def = COConfigurationManager.getIntParameter("Max.Peer.Connections.Per.Torrent");

						value = new Integer(def);

					} else if (name == PARAM_MAX_PEERS_WHEN_SEEDING_ENABLED) {

						if (COConfigurationManager.getBooleanParameter("Max.Peer.Connections.Per.Torrent.When.Seeding.Enable")) {

							value = Boolean.TRUE;
						}

					} else if (name == PARAM_MAX_PEERS_WHEN_SEEDING) {

						int	def = COConfigurationManager.getIntParameter("Max.Peer.Connections.Per.Torrent.When.Seeding");

						value = new Integer(def);

					} else if (name == PARAM_MAX_SEEDS) {

						value = new Integer(COConfigurationManager.getIntParameter("Max Seeds Per Torrent"));

					} else if (name == PARAM_RANDOM_SEED) {

						long	rand = random.nextLong();

						setLongParameter(name, rand);

						value = new Long(rand);
					}
				}
			}

			if (value instanceof Boolean) {

				return (((Boolean)value).booleanValue()?1:0);

			} else if (value instanceof Integer) {

				return ( ((Integer)value).longValue());

			} else if (value instanceof Long) {

				return ( ((Long)value).longValue());
			}

			Debug.out("Invalid parameter value for '" + name + "' - " + value);

			return (0);

		} finally {

			this_mon.exit();
		}
	}

	public void setLongParameter(
		String		name,
		long		value) {
		Object	default_value = default_parameters.get(name);

		if (default_value == null) {

			Debug.out("Unknown parameter '" + name + "' - must be defined in DownloadManagerState");
		}

		try {
			this_mon.enter();

				// gotta clone here otherwise we update the underlying  map and the setMapAttribute code
				// doesn't think it has changed

			parameters	= new LightHashMap(parameters);

			parameters.put( name, new Long(value));

			setMapAttribute(AT_PARAMETERS, parameters);

		} finally {

			this_mon.exit();
		}
	}

	public int getIntParameter(
		String	name) {
		return ((int)getLongParameter( name));
	}

	public void setIntParameter(
		String	name,
		int		value) {
		setLongParameter(name, value);
	}

	public boolean getBooleanParameter(
		String	name) {
		return (getLongParameter( name ) != 0);
	}

	public void setBooleanParameter(
		String		name,
		boolean		value) {
		setLongParameter(name, value?1:0);
	}

	public void setAttribute(
		String		name,
		String		value) {

		if (name.equals( AT_CATEGORY)) {

			if (value == null) {

				setCategory(null);

			} else {
				Category	cat = CategoryManager.getCategory(value);

				if (cat == null) {

					cat = CategoryManager.createCategory(value);

				}

				setCategory(cat);
			}
			return;
		}

		if (name.equals(AT_RELATIVE_SAVE_PATH)) {
			if (value.length() > 0) {
				File relative_path_file = new File(value);
				relative_path_file = DownloadManagerDefaultPaths.normaliseRelativePath(relative_path_file);
				value = (relative_path_file == null) ? "" : relative_path_file.getPath();
			}
		}

		setStringAttribute(name, value);
	}

	public String getAttribute(
		String		name) {
		if (name.equals( AT_CATEGORY)) {

			Category	cat = getCategory();

			if (cat == null) {

				return (null);
			}

			if (cat == CategoryManager.getCategory( Category.TYPE_UNCATEGORIZED)) {

				return (null);
			}

			return ( cat.getName());

		} else {

			return (getStringAttribute( name));
		}
	}

	public Category
	getCategory() {
	    return category;
	}

	public void setCategory(
		Category 	cat ) {
		if (cat == category) {
			return;
		}

		if (cat != null && cat.getType() != Category.TYPE_USER) {

			cat = null;
			if (cat == category) {
				return;
			}
		}

		Category oldCategory = (category == null)?CategoryManager.getCategory(Category.TYPE_UNCATEGORIZED):category;

		category = cat;

		if (oldCategory != null) {

			oldCategory.removeManager(this);
  		}

		DownloadManager dm = getDownloadManager();

		if (dm != null && !dm.isDestroyed()) {

			if (category != null) {

				category.addManager(this);

			} else {

				CategoryManager.getCategory(Category.TYPE_UNCATEGORIZED).addManager(this);
			}
		}

		if (category != null) {

			setStringAttribute( AT_CATEGORY, category.getName());

		} else {

			setStringAttribute(AT_CATEGORY, null);
		}
	}

	public String getTrackerClientExtensions() {
		return (getStringAttribute( AT_TRACKER_CLIENT_EXTENSIONS));
	}

	public void setTrackerClientExtensions(
		String		value) {
		setStringAttribute(AT_TRACKER_CLIENT_EXTENSIONS, value);
	}

    public String getDisplayName() {
    	return this.getStringAttribute(AT_DISPLAY_NAME);
    }

    public void setDisplayName(String value) {
    	this.setStringAttribute(AT_DISPLAY_NAME, value);
    }

    public String getUserComment() {
    	return this.getStringAttribute(AT_USER_COMMENT);
    }

    public void setUserComment(String value) {
    	this.setStringAttribute(AT_USER_COMMENT, value);
    }

    public String getRelativeSavePath() {
    	return this.getStringAttribute(AT_RELATIVE_SAVE_PATH);
    }

	public DiskManagerFileInfo getPrimaryFile() {
		int primaryIndex = -1;
		DiskManagerFileInfo[] fileInfo = download_manager.getDiskManagerFileInfoSet().getFiles();
		if (hasAttribute(AT_PRIMARY_FILE_IDX)) {
			primaryIndex = getIntAttribute(AT_PRIMARY_FILE_IDX);
		}

		if (primaryIndex < 0 || primaryIndex >= fileInfo.length) {
			primaryIndex = -1;
			if (fileInfo.length > 0) {
				int idxBiggest = -1;
				long lBiggest = -1;
				int numChecked = 0;
				for (int i = 0; i < fileInfo.length && numChecked < 10; i++) {
					if (!fileInfo[i].isSkipped()) {
						numChecked++;
						if (fileInfo[i].getLength() > lBiggest) {
  						lBiggest = fileInfo[i].getLength();
  						idxBiggest = i;
						}
					}
				}
				if (idxBiggest >= 0) {
					primaryIndex = idxBiggest;
				}
			}
			if (primaryIndex >= 0) {
				setPrimaryFile(fileInfo[primaryIndex]);
			}
		}

		if (primaryIndex >= 0) {
			return fileInfo[primaryIndex];
		}
		return null;
	}

	/**
	 * @param primary
	 */
	public void setPrimaryFile(DiskManagerFileInfo dmfi) {
		setIntAttribute(AT_PRIMARY_FILE_IDX, dmfi.getIndex());
	}

	public String[]
	getNetworks() {
		List	values = getListAttributeSupport(AT_NETWORKS);

		List	res = new ArrayList();

			// map back to the constants to allow == comparisons

		for (int i=0;i<values.size();i++) {

			String	nw = (String)values.get(i);

			for (int j=0;j<AENetworkClassifier.AT_NETWORKS.length;j++) {

				String	nn = AENetworkClassifier.AT_NETWORKS[j];

				if (nn.equals( nw)) {

					res.add(nn);
				}
			}
		}

		String[]	x = new String[res.size()];

		res.toArray(x);

		return (x);
	}

	  public boolean isNetworkEnabled(
	      String network) {
	    List	values = getListAttributeSupport(AT_NETWORKS);
	    return values.contains(network);
	  }

	public void setNetworks(
		String[]		networks) {
		if (networks == null) {

			networks = new String[0];
		}

		List	l = new ArrayList();

		Collections.addAll(l, networks);

		setListAttribute(AT_NETWORKS, l);
	}

	  public void
	  setNetworkEnabled(
	      String network,
	      boolean enabled) {
	    List	values = getListAttributeSupport(AT_NETWORKS);
	    boolean alreadyEnabled = values.contains(network);
	    List	l = new ArrayList();

	    if (enabled && !alreadyEnabled) {
	      for (int i=0;i<values.size();i++) {
	        l.add(values.get(i));
	      }
	      l.add(network);
	      setListAttribute(AT_NETWORKS, l);
	    }
	    if (!enabled && alreadyEnabled) {
	      for (int i=0;i<values.size();i++) {
	        l.add(values.get(i));
	      }
	      l.remove(network);
	      setListAttribute(AT_NETWORKS, l);
	    }
	  }

		// peer sources

	public String[]
	getPeerSources() {
		List	values = getListAttributeSupport(AT_PEER_SOURCES);

		List	res = new ArrayList();

			// map back to the constants to allow == comparisons

		for (int i=0;i<values.size();i++) {

			String	ps = (String)values.get(i);

			for (int j=0;j<PEPeerSource.PS_SOURCES.length;j++) {

				String	x = PEPeerSource.PS_SOURCES[j];

				if (x.equals( ps)) {

					res.add(x);
				}
			}
		}

		String[]	x = new String[res.size()];

		res.toArray(x);

		return (x);
	}

	public boolean isPeerSourceEnabled(
		String peerSource ) {
		List	values = getListAttributeSupport(AT_PEER_SOURCES);

		return values.contains(peerSource);
	}

	public boolean isPeerSourcePermitted(
		String	peerSource) {
			// no DHT for private torrents or explicitly prevented

		if (peerSource.equals( PEPeerSource.PS_DHT)) {

			if (	TorrentUtils.getPrivate( torrent) ||
					!TorrentUtils.getDHTBackupEnabled(torrent)) {

				return (false);
			}
		}

			// no PEX for private torrents

		if (peerSource.equals( PEPeerSource.PS_OTHER_PEER)) {

			if (TorrentUtils.getPrivate( torrent)) {

				return (false);
			}
		}

		List	values = getListAttributeSupport(AT_PEER_SOURCES_DENIED);

		if (values != null) {

			if (values.contains( peerSource)) {

				return (false);
			}
		}

		return (true);
	}

	public void setPeerSourcePermitted(
		String	peerSource,
		boolean	enabled) {
		if (!getFlag( FLAG_ALLOW_PERMITTED_PEER_SOURCE_CHANGES)) {

			Logger.log(new LogEvent(torrent, LOGID, "Attempt to modify permitted peer sources denied as disabled '"
							+ TorrentUtils.getLocalisedName(torrent) + "'"));

			return;
		}

		if (!enabled) {

			setPeerSourceEnabled(peerSource, false);
		}

		List	values = getListAttributeSupport(AT_PEER_SOURCES_DENIED);

		if (values == null) {

			if (!enabled) {

				values = new ArrayList();

				values.add(peerSource);

				setListAttribute(AT_PEER_SOURCES_DENIED, values);
			}
		} else {

			if (enabled) {

				values.remove(peerSource);

			} else {

				if (!values.contains( peerSource)) {

					values.add(peerSource);
				}
			}

			setListAttribute(AT_PEER_SOURCES_DENIED, values);
		}
	}

	public void setPeerSources(
		String[]		ps) {
		if (ps == null) {

			ps = new String[0];
		}

		List	l = new ArrayList();

		for (int i=0;i<ps.length;i++) {

			String	p = ps[i];

			if (isPeerSourcePermitted(p)) {

				l.add( ps[i]);
			}
		}

		setListAttribute(AT_PEER_SOURCES, l);
	}

	  public void
	  setPeerSourceEnabled(
	      String source,
	      boolean enabled )
	  {
		  if (enabled && !isPeerSourcePermitted( source)) {

			  return;
		  }

		  List	values = getListAttributeSupport(AT_PEER_SOURCES);

		  boolean alreadyEnabled = values.contains(source);

		  List	l = new ArrayList();

		  if (enabled && !alreadyEnabled) {
		    for (int i=0;i<values.size();i++) {
		      l.add(values.get(i));
		    }
		    l.add(source);
		    setListAttribute(AT_PEER_SOURCES, l);
		  }
		  if (!enabled && alreadyEnabled) {
		    for (int i=0;i<values.size();i++) {
		      l.add(values.get(i));
		    }
		    l.remove(source);
		    setListAttribute(AT_PEER_SOURCES, l);
		  }
	  }


	  // links stuff

	private volatile WeakReference<LinkFileMap>				file_link_cache 	= null;

	public void setFileLink(
		int		source_index,
		File	link_source,
		File	link_destination) {
		LinkFileMap	links = getFileLinks();

		File	existing = (File)links.get( source_index, link_source);

		if (link_destination == null) {

			if (existing == null) {

				return;
			}
		} else if (existing != null && existing.equals( link_destination)) {

			return;
		}

		links.put(source_index, link_source, link_destination);

		List	list = new ArrayList();

		Iterator<LinkFileMap.Entry>	it = links.entryIterator();

		while (it.hasNext()) {

			LinkFileMap.Entry	entry = it.next();

			int		index	= entry.getIndex();
			File	source 	= entry.getFromFile();
			File	target 	= entry.getToFile();

			String	str = index + "\n" + source + "\n" + (target==null?"":target.toString());

			list.add(str);
		}

		//System.out.println("setFileLink: " + link_source + " -> " + link_destination);

		synchronized(this) {

			file_link_cache = new WeakReference<LinkFileMap>(links);
		}

		setListAttribute(AT_FILE_LINKS2, list);
	}

	public void setFileLinks(
		List<Integer>	source_indexes,
		List<File>		link_sources,
		List<File>		link_destinations) {
		LinkFileMap	links = getFileLinks();

		boolean changed = false;

		for ( int i=0;i<link_sources.size();i++) {

			int		source_index		= source_indexes.get(i);
			File	link_source 		= link_sources.get(i);
			File	link_destination 	= link_destinations.get(i);

			File	existing = links.get( source_index, link_source);

			if (link_destination == null) {

				if (existing == null) {

					continue;
				}
			} else if (existing != null && existing.equals( link_destination)) {

				continue;
			}

			links.put(source_index, link_source, link_destination);

			changed = true;
		}

		if (!changed) {

			return;
		}

		//System.out.println("setFileLinks: " + links.getString());

		List	list = new ArrayList();

		Iterator<LinkFileMap.Entry>	it = links.entryIterator();

		while (it.hasNext()) {

			LinkFileMap.Entry	entry = it.next();

			int		index	= entry.getIndex();
			File	source 	= entry.getFromFile();
			File	target 	= entry.getToFile();

			String	str = index + "\n" + source + "\n" + (target==null?"":target.toString());

			list.add(str);
		}

		synchronized(this) {

			file_link_cache = new WeakReference<LinkFileMap>(links);
		}

		setListAttribute(AT_FILE_LINKS2, list);
	}

	public void clearFileLinks() {
		LinkFileMap	links = getFileLinks();

		List	list = new ArrayList();

		Iterator<LinkFileMap.Entry>	it = links.entryIterator();

		boolean	changed = false;

		while (it.hasNext()) {

			LinkFileMap.Entry	entry = it.next();

			int		index	= entry.getIndex();
			File	source 	= entry.getFromFile();
			File	target 	= entry.getToFile();

			if (target != null) {

				changed = true;
			}

			String	str = index + "\n" + source + "\n";

			list.add(str);
		}

		if (changed) {

			synchronized(this) {

				file_link_cache = null;
			}

			setListAttribute(AT_FILE_LINKS2, list);
		}

		//System.out.println("clearFileLinks");
	}

	public File
	getFileLink(
		int		source_index,
		File	link_source) {
		LinkFileMap map = null;

		WeakReference<LinkFileMap> ref = file_link_cache;

		if (ref != null) {

			map = ref.get();
		}

		if (map == null) {

			map = getFileLinks();

			synchronized(this) {

				file_link_cache = new WeakReference<LinkFileMap>(map);
			}
		}

		File res = map.get(source_index, link_source);

		//System.out.println("getFileLink: " + link_source + " -> " + res);

		return (res);
	}

	public LinkFileMap
	getFileLinks() {
		LinkFileMap map = null;

		WeakReference<LinkFileMap> ref = file_link_cache;

		if (ref != null) {

			map = ref.get();
		}

		if (map == null) {

			map = getFileLinksSupport();

			synchronized(this) {

				file_link_cache = new WeakReference<LinkFileMap>(map);
			}
		}

		return (map);
	}

	private LinkFileMap
	getFileLinksSupport() {
		LinkFileMap	res = new LinkFileMap();

		List	new_values = getListAttributeSupport(AT_FILE_LINKS2);

		if (new_values.size() > 0) {

			for (int i=0;i<new_values.size();i++) {

				String	entry = (String)new_values.get(i);

				String[] bits = entry.split("\n");

				if (bits.length >= 2) {

					try {
						int		index 	= Integer.parseInt( bits[0].trim());
						File	source	= new File(bits[1].trim());
						File	target	= bits.length<3?null:new File(bits[2].trim());

						if (index >= 0) {

							res.put(index, source, target);

						} else {

								// can get here when partially resolved link state is saved and then re-read

							res.putMigration(source, target);
						}
					} catch (Throwable e) {

						Debug.out(e);
					}
				}
			}
		} else {

			List	old_values = getListAttributeSupport(AT_FILE_LINKS_DEPRECATED);

			for (int i=0;i<old_values.size();i++) {

				String	entry = (String)old_values.get(i);

				int	sep = entry.indexOf("\n");

				if (sep != -1) {

					File target = (sep == entry.length()-1)?null:new File(entry.substring( sep+1));

					res.putMigration(new File( entry.substring(0,sep)), target);
				}
			}
		}

		//System.out.println("getFileLinks: " + res.getString());

		return (res);
	}

	public boolean isOurContent() {
			// HACK!

		Map mapAttr = getMapAttribute("Plugin.azdirector.ContentMap");

		return mapAttr != null	&& mapAttr.containsKey("DIRECTOR PUBLISH");
	}

		// general stuff


	protected String getStringAttribute(
		String	attribute_name) {
		informWillRead(attribute_name);

		try {
			this_mon.enter();

			if (!(attributes.get( attribute_name) instanceof byte[])) {

				return (null);
			}

			byte[]	bytes = (byte[])attributes.get(attribute_name);

			if (bytes == null) {

				return (null);
			}

			try {
				return (new String( bytes, Constants.DEFAULT_ENCODING));

			} catch (UnsupportedEncodingException e) {

				Debug.printStackTrace(e);

				return (null);
			}
		} finally {

			this_mon.exit();
		}
	}

	protected void setStringAttribute(
		final String	attribute_name,
		final String	attribute_value) {
		boolean	changed	= false;

		try {
			this_mon.enter();

			if (attribute_value == null) {

				if (attributes.containsKey( attribute_name)) {

					attributes.remove(attribute_name);

					write_required = changed = true;
				}
			} else {

				try {
					byte[]	existing_bytes = (byte[])attributes.get(attribute_name);

					byte[]	new_bytes = attribute_value.getBytes(Constants.DEFAULT_ENCODING);

					if (	existing_bytes == null ||
							!Arrays.equals(existing_bytes, new_bytes)) {

						attributes.put(attribute_name, new_bytes);

						write_required = changed = true;
					}

				} catch (UnsupportedEncodingException e) {

					Debug.printStackTrace(e);
				}
			}
		} finally {

			this_mon.exit();
		}

		if (changed) {

			informWritten(attribute_name);
		}
	}

	public long getLongAttribute(
		String	attribute_name) {
		informWillRead(attribute_name);

		try {
			this_mon.enter();

			Long	l = (Long)attributes.get(attribute_name);

			if (l == null) {

				Object def = default_attributes.get(attribute_name);

				if (def != null) {

					if (def instanceof Long) {

						return (((Long)def).longValue());

					} else if (def instanceof Integer) {

						return (((Integer)def).longValue());

					} else {

						Debug.out("unknown default type " + def);
					}
				} else if (attribute_name == AT_FILES_EXPANDED) {

					boolean featured = TorrentUtils.isFeaturedContent(torrent);

					long res = featured?1:0;

					attributes.put(attribute_name, new Long( res));

					write_required	= true;

					return (res);
				}

				return (0);
			}

			return ( l.longValue());

		} finally {

			this_mon.exit();
		}
	}

	public void setLongAttribute(
		final String	attribute_name,
		final long		attribute_value) {
		boolean	changed	= false;

		try {
			this_mon.enter();

			Long	existing_value = (Long)attributes.get(attribute_name);

			if (	existing_value == null ||
					existing_value.longValue() != attribute_value) {

				attributes.put(attribute_name, new Long( attribute_value));

				write_required = changed = true;
			}
		} finally {

			this_mon.exit();
		}

		if (changed) {

			informWritten(attribute_name);
		}
	}

	public void setListAttribute(
		String		name,
		String[]	values) {
		List	list = values==null?null:Arrays.asList((Object[]) values.clone());
		/*
		if (list != null) {

			for (int i=0;i<values.length;i++) {

				list.add( values[i]);
			}
		}*/

		setListAttribute(name, list);
	}

	public String getListAttribute(String name, int idx) {
		if (name.equals(AT_NETWORKS) || name.equals(AT_PEER_SOURCES))
			throw new UnsupportedOperationException("not supported right now, implement it yourself :P");

		informWillRead(name);

		try {
			this_mon.enter();
			List values = (List) attributes.get(name);
			if (values == null || idx >= values.size() || idx < 0)
				return null;
			Object o = values.get(idx);
			if (o instanceof byte[]) {
				byte[] bytes = (byte[]) o;
				String s = null;
				try {
					s = StringInterner.intern(new String(bytes, Constants.DEFAULT_ENCODING));
				} catch (UnsupportedEncodingException e) {
					Debug.printStackTrace(e);
				}
				if (s != null)
					values.set(idx, s);
				return s;
			} else if (o instanceof String) {
				return (String) o;
			}
		} finally {
			this_mon.exit();
		}

		return null;
	}

	public String[]
	getListAttribute(
		String	attribute_name) {
		if (attribute_name == AT_NETWORKS) {

			return ( getNetworks());

		} else if (attribute_name == AT_PEER_SOURCES) {

			return ( getPeerSources());

		} else {

			List	l = getListAttributeSupport(attribute_name);

			if (l == null) {

				return (null);
			}

			String[]	res = new String[l.size()];

			try {
				res = (String[])l.toArray(res);
			} catch (ArrayStoreException e) {
				Debug.out("getListAttribute(" + attribute_name + ") - object isnt String - " + e);

				return (null);
			}


			return (res);
		}
	}

	protected List
	getListAttributeSupport(
		String	attribute_name) {
		informWillRead(attribute_name);

		try {
			this_mon.enter();

			List	values = (List)attributes.get(attribute_name);

			List	res = new ArrayList(values != null ? values.size() : 0);

			if (values != null) {

				for (int i=0;i<values.size();i++) {

					Object	o = values.get(i);

					if (o instanceof byte[]) {

						byte[]	bytes = (byte[])o;

						String s = null;

						try {

							s = StringInterner.intern(new String(bytes, Constants.DEFAULT_ENCODING));

						} catch (UnsupportedEncodingException e) {

							Debug.printStackTrace(e);
						}

						if (s != null) {
							res.add(s);
							values.set(i, s);
						}
					} else if (o instanceof String) {

						res.add(o);
					}
				}
			}

			return (res);

		} finally {

			this_mon.exit();
		}
	}

	protected void setListAttribute(
		final String	attribute_name,
		final List		attribute_value) {
		boolean	changed	= false;

		try {
			this_mon.enter();

			if (attribute_value == null) {

				if (attributes.containsKey( attribute_name)) {

					attributes.remove(attribute_name);

					write_required = changed = true;
				}
			} else {

				List old_value = getListAttributeSupport(attribute_name);

				if (old_value == null || old_value.size() != attribute_value.size()) {

					attributes.put(attribute_name, attribute_value);

					write_required = changed = true;

				} else {

					if (old_value == attribute_value) {

						Debug.out("setListAttribute: should clone?");
					}

					changed = !BEncoder.listsAreIdentical(old_value, attribute_value);

					if (changed) {

						write_required = true;

						attributes.put(attribute_name, attribute_value);
					}
				}
			}
		} finally {

			this_mon.exit();
		}

		if (changed) {

			informWritten(attribute_name);
		}
	}

	public Map
	getMapAttribute(
		String	attribute_name) {
		informWillRead(attribute_name);

		try {
			this_mon.enter();

			Map	value = (Map)attributes.get(attribute_name);

			return (value);

		} finally {

			this_mon.exit();
		}
	}

	public void setMapAttribute(
		final String	attribute_name,
		final Map		attribute_value) {
		setMapAttribute(attribute_name, attribute_value, false);
	}

	protected void setMapAttribute(
		final String	attribute_name,
		final Map		attribute_value,
		boolean			disable_change_notification) {
		boolean	changed	= false;

		try {
			this_mon.enter();

			if (attribute_value == null) {

				if (attributes.containsKey( attribute_name)) {

					attributes.remove(attribute_name);

					write_required = changed = true;
				}
			} else {

				Map old_value = getMapAttribute(attribute_name);

				if (old_value == null || old_value.size() != attribute_value.size()) {

					attributes.put(attribute_name, attribute_value);

					write_required = changed = true;

				} else {

					if (old_value == attribute_value) {

						Debug.out("setMapAttribute: should clone?");
					}

					changed = !BEncoder.mapsAreIdentical(old_value, attribute_value);

					if (changed) {

						write_required = true;

						attributes.put(attribute_name, attribute_value);
					}
				}
			}
		} finally {

			this_mon.exit();
		}

		if (changed && !disable_change_notification) {

			informWritten(attribute_name);
		}
	}

	public boolean hasAttribute(
		String name) {
		try {

			this_mon.enter();

			if (attributes == null) {return false;}

			return attributes.containsKey(name);

		} finally {

			this_mon.exit();
		}
	}

	// These methods just use long attributes to store data into.

	public void setIntAttribute(
		String 	name,
		int 	value) {
		setLongAttribute(name, value);
	}

	public int getIntAttribute(
		String name) {
		return (int)getLongAttribute(name);
	}

	public void setBooleanAttribute(
		String 		name,
		boolean 	value ) {
		setLongAttribute(name, (value ? 1 : 0));
	}

	public boolean getBooleanAttribute(
		String name ) {
		return getLongAttribute(name) != 0;
	}


	public static DownloadManagerState
	getDownloadState(
		DownloadManager	dm) {
		return (new nullState(dm));
	}

	protected void informWritten(
		final String		attribute_name) {
			// don't make any of this async as the link management code for cache files etc
			// relies on callbacks here being synchronous...

		for ( DownloadManagerStateListener l: listeners_cow.getList()) {

			try {
				l.stateChanged(
					this,
					new DownloadManagerStateEvent() {
						public int getType() {
							return (DownloadManagerStateEvent.ET_ATTRIBUTE_WRITTEN);
						}

						public Object getData() {
							return (attribute_name);
						}
					});
			} catch (Throwable e) {

				Debug.printStackTrace(e);
			}
		}

		CopyOnWriteList<DownloadManagerStateAttributeListener> write_listeners = global_listeners_write_map_cow.get(attribute_name);

		if (write_listeners != null) {

			for ( DownloadManagerStateAttributeListener l: write_listeners.getList()) {

				try {

					l.attributeEventOccurred(download_manager, attribute_name, DownloadManagerStateAttributeListener.WRITTEN);

				} catch (Throwable t) {

					Debug.printStackTrace(t);
				}
			}
		}

		write_listeners = listeners_write_map_cow.get(attribute_name);

		if (write_listeners != null) {

			for ( DownloadManagerStateAttributeListener l: write_listeners.getList()) {

				try {

					l.attributeEventOccurred(download_manager, attribute_name, DownloadManagerStateAttributeListener.WRITTEN);

				} catch (Throwable t) {

					Debug.printStackTrace(t);
				}
			}
		}
	}

	protected void informWillRead(
		final String		attribute_name) {
			// avoid potential recursion will a will-be-read causing a write that then
			// causes a further will-be-read...

		List	will_be_read_list = (List)tls_wbr.get();

		if (!will_be_read_list.contains( attribute_name)) {

			will_be_read_list.add(attribute_name);

			try {

				for (DownloadManagerStateListener l: listeners_cow.getList()) {

					try {
						l.stateChanged(
							this,
							new DownloadManagerStateEvent() {
								public int getType() {
									return (DownloadManagerStateEvent.ET_ATTRIBUTE_WILL_BE_READ);
								}

								public Object getData() {
									return (attribute_name);
								}
							});
					} catch (Throwable e) {

						Debug.printStackTrace(e);
					}
				}

				CopyOnWriteList<DownloadManagerStateAttributeListener> read_listeners = global_listeners_read_map_cow.get(attribute_name);

				if (read_listeners != null) {

					for ( DownloadManagerStateAttributeListener l: read_listeners.getList()) {

						try {
							l.attributeEventOccurred(download_manager, attribute_name, DownloadManagerStateAttributeListener.WILL_BE_READ);

						} catch (Throwable t) {

							Debug.printStackTrace(t);
						}
					}
				}

				read_listeners = listeners_read_map_cow.get(attribute_name);

				if (read_listeners != null) {

					for ( DownloadManagerStateAttributeListener l: read_listeners.getList()) {

						try {
							l.attributeEventOccurred(download_manager, attribute_name, DownloadManagerStateAttributeListener.WILL_BE_READ);

						} catch (Throwable t) {

							Debug.printStackTrace(t);
						}
					}
				}
			} finally {

				will_be_read_list.remove(attribute_name);
			}
		}
	}

	public void addListener(
		DownloadManagerStateListener	l) {
		listeners_cow.add(l);
	}

	public void removeListener(
		DownloadManagerStateListener	l) {
		listeners_cow.remove(l);
	}

	public void addListener(DownloadManagerStateAttributeListener l, String attribute, int event_type) {
		CopyOnWriteMap<String,CopyOnWriteList<DownloadManagerStateAttributeListener>> map_to_use = (event_type == DownloadManagerStateAttributeListener.WILL_BE_READ) ? this.listeners_read_map_cow : this.listeners_write_map_cow;
		CopyOnWriteList<DownloadManagerStateAttributeListener> lst = map_to_use.get(attribute);
		if (lst == null) {
			lst = new CopyOnWriteList<DownloadManagerStateAttributeListener>();
			map_to_use.put(attribute, lst);
		}
		lst.add(l);
	}

	public void removeListener(DownloadManagerStateAttributeListener l, String attribute, int event_type) {
		CopyOnWriteMap<String,CopyOnWriteList<DownloadManagerStateAttributeListener>> map_to_use = (event_type == DownloadManagerStateAttributeListener.WILL_BE_READ) ? this.listeners_read_map_cow : this.listeners_write_map_cow;
		CopyOnWriteList<DownloadManagerStateAttributeListener> lst = map_to_use.get(attribute);
		if (lst != null) {lst.remove(l);}
	}

	public static void addGlobalListener(
		DownloadManagerStateAttributeListener l, String attribute, int event_type) {
		CopyOnWriteMap<String,CopyOnWriteList<DownloadManagerStateAttributeListener>> map_to_use = (event_type == DownloadManagerStateAttributeListener.WILL_BE_READ) ? global_listeners_read_map_cow : global_listeners_write_map_cow;
		CopyOnWriteList<DownloadManagerStateAttributeListener> lst = map_to_use.get(attribute);
		if (lst == null) {
			lst = new CopyOnWriteList<DownloadManagerStateAttributeListener>();
			map_to_use.put(attribute, lst);
		}
		lst.add(l);
	}

	public static void removeGlobalListener(
		DownloadManagerStateAttributeListener l, String attribute, int event_type) {
		CopyOnWriteMap<String,CopyOnWriteList<DownloadManagerStateAttributeListener>> map_to_use = (event_type == DownloadManagerStateAttributeListener.WILL_BE_READ) ? global_listeners_read_map_cow : global_listeners_write_map_cow;
		CopyOnWriteList<DownloadManagerStateAttributeListener> lst = (CopyOnWriteList)map_to_use.get(attribute);
		if (lst != null) {lst.remove(l);}
	}

	public void generateEvidence(
		IndentWriter writer) {
		writer.println("DownloadManagerState");

		try {
			writer.indent();

			writer.println("parameters=" + parameters);
			writer.println("flags=" + getFlags());
			DiskManagerFileInfo primaryFile = getPrimaryFile();
			if (primaryFile != null) {
				writer.println("primary file=" + Debug.secretFileName(primaryFile.getFile(true).getAbsolutePath()));
			}

		} finally {

			writer.exdent();
		}
	}


	public void dump(
		IndentWriter writer) {
		writer.println("attributes: " + parameters);
	}

	protected static class
	nullState
		implements DownloadManagerState
	{

		protected final DownloadManager		download_manager;

		protected
		nullState(
			DownloadManager	_dm) {
			download_manager = _dm;
		}

		public TOTorrent
		getTorrent() {
			return (null);
		}

		public File
		getStateFile() {
			return (null);
		}

		public DownloadManager
		getDownloadManager() {
			return (download_manager);
		}

		public void clearResumeData() {
		}

		public Map
		getResumeData() {
			return (new HashMap());
		}

		public void setResumeData(
			Map	data) {
		}

		public boolean isResumeDataComplete() {
			return (false);
		}

		public void clearTrackerResponseCache() {
		}

		public Map
		getTrackerResponseCache() {
			return (new HashMap());
		}

		public void setTrackerResponseCache(
			Map		value) {
		}

		public void setFlag(
			long		flag,
			boolean		set) {
		}

		public boolean getFlag(
			long		flag) {
			return (false);
		}

		public long getFlags() {
			return 0;
		}

		public void setParameterDefault(
			String	name) {
		}

		public long getLongParameter(
			String	name) {
			return (0);
		}

		public void setLongParameter(
			String	name,
			long	value) {
		}

		public int getIntParameter(
			String	name) {
			return (0);
		}

		public void setIntParameter(
			String	name,
			int		value) {
		}

		public boolean getBooleanParameter(
			String	name) {
			return (false);
		}

		public void setBooleanParameter(
			String		name,
			boolean		value) {
		}

		public void setAttribute(
			String		name,
			String		value) {
		}

		public String getAttribute(
			String		name) {
			return (null);
		}

		public String getTrackerClientExtensions() {
			return (null);
		}

		public void setTrackerClientExtensions(
			String		value) {
		}

		public void setListAttribute(
			String		name,
			String[]	values) {
		}


		public String getListAttribute(String name, int idx) {
			return null;
		}

		public String[]
		getListAttribute(
			String	name) {
			return (null);
		}

		public void setMapAttribute(
			String		name,
			Map			value) {
		}

		public Map
		getMapAttribute(
			String		name) {
			return (null);
		}

		public boolean hasAttribute(String name) {return false;}
		public int getIntAttribute(String name) {return 0;}
		public long getLongAttribute(String name) {return 0L;}
		public boolean getBooleanAttribute(String name) {return false;}
		public void setIntAttribute(String name, int value) {}
		public void setLongAttribute(String name, long value) {}
		public void setBooleanAttribute(String name, boolean value) {}

		public Category
		getCategory() {
			return (null);
		}

		public void setCategory(
			Category cat) {
		}

		public String[]
		getNetworks() {
			return (new String[0]);
		}


	    public boolean isNetworkEnabled(String network) {
	      return false;
	    }

		public void setNetworks(
			String[]		networks) {
		}


	    public void setNetworkEnabled(
	        String network,
	        boolean enabled) {
	    }

		public String[]
		getPeerSources() {
			return (new String[0]);
		}
		public boolean isPeerSourcePermitted(
			String	peerSource) {
			return (false);
		}

		public void setPeerSourcePermitted(String peerSource, boolean permitted) {
		}

	    public boolean
	    isPeerSourceEnabled(
	        String peerSource) {
	      return false;
	    }

	    public void suppressStateSave(boolean suppress) {}

		public void setPeerSources(
			String[]		networks) {
		}


	    public void
	    setPeerSourceEnabled(
	        String source,
	        boolean enabled) {
	    }

	    public void setFileLink(
			int		source_index,
			File	link_source,
			File	link_destination )
	    {
	    }

	    public void setFileLinks(
			List<Integer>	source_indexes,
			List<File>		link_sources,
			List<File>		link_destinations )
	    {
	    }

		public void clearFileLinks() {
		}

		public File
		getFileLink(
			int		source_index,
			File	link_source) {
			return (null);
		}

		public LinkFileMap
		getFileLinks() {
			return (new LinkFileMap());
		}

		public void setActive(boolean active) {
		}

		public void discardFluff() {}

		public boolean exportState(
			File	target_dir) {
			return (false);
		}

		public void save() {
		}

		public void delete() {
		}

		public void addListener(
			DownloadManagerStateListener	l) {}

		public void removeListener(
			DownloadManagerStateListener	l) {}

		public void addListener(DownloadManagerStateAttributeListener l, String attribute, int event_type) {}
		public void removeListener(DownloadManagerStateAttributeListener l, String attribute, int event_type) {}

        public void setDisplayName(String name) {}
        public String getDisplayName() {return null;}

        public void setUserComment(String name) {}
        public String getUserComment() {return null;}

        public void setRelativeSavePath(String name) {}
        public String getRelativeSavePath() {return null;}

		public boolean parameterExists(String name) {
			// TODO Auto-generated method stub
			return false;
		}

		public void generateEvidence(
			IndentWriter writer) {
			writer.println("DownloadManagerState: broken torrent");
		}
		public void dump(
			IndentWriter writer) {
		}

		public boolean isOurContent() {
			// TODO Auto-generated method stub
			return false;
		}

		// @see org.gudy.azureus2.core3.download.DownloadManagerState#getPrimaryFile()
		public DiskManagerFileInfo getPrimaryFile() {
			// TODO Auto-generated method stub
			return null;
		}

		// @see org.gudy.azureus2.core3.download.DownloadManagerState#setPrimaryFile(org.gudy.azureus2.core3.disk.DiskManagerFileInfo)
		public void setPrimaryFile(DiskManagerFileInfo dmfi) {
			// TODO Auto-generated method stub

		}
	}

	protected static class CachedStateWrapper 
			extends LogRelation 
			implements TorrentUtils.ExtendedTorrent {
		
		private static String TAG = "DownloadManagerStateImpl.CachedStateWrapper";
		
		private final DownloadManagerImpl	downloadManager;

		private final String	torrentFile;
		private HashWrapper		torrentHashWrapper;
		private Map				cache;
		private Map				cacheAttributes;
		private Map				cacheAzp;

		private volatile TorrentUtils.ExtendedTorrent		delegate;
		private TOTorrentException							fixup_failure;

		private boolean		discard_pieces;
		private boolean		logged_failure;

		private Boolean		simple_torrent;
		private long		size;
		private int			file_count;

		private URL						announce_url;
		private cacheGroup				announce_group;

		private volatile boolean		discard_fluff;

		protected CachedStateWrapper(
			DownloadManagerImpl	_downloadManager,
			String				_torrentFile,
			byte[]				_torrentHash,
			Map					_cache,
			boolean				_force_piece_discard) {
			
			/*if (SingleCounter0.getInstance().getAndIncreaseCount() == 1) {
				Log.d(TAG, "<init>() is called...");
				new Throwable().printStackTrace();
			}*/
			
			downloadManager = _downloadManager;
			torrentFile = _torrentFile;
			torrentHashWrapper = new HashWrapper(_torrentHash);
			cache = _cache;
			cacheAttributes = (Map) cache.get("attributes");
			cacheAzp = (Map) cache.get("azp");
			if (_force_piece_discard) {
				discard_pieces = true;
			} else {
				Long l_fp = (Long) cache.get("dp");
				if (l_fp != null) {
					discard_pieces = l_fp.longValue() == 1;
				}
			}
			Long st = (Long) cache.get("simple");
			if (st != null) {
				simple_torrent = Boolean.valueOf(st.longValue() == 1);
			}
			Long fc = (Long) cache.get("fc");
			if (fc != null) {
				file_count = fc.intValue();
			}
			Long l_size = (Long) cache.get("size");
			if (l_size != null) {
				size = l_size.longValue();
			}
			byte[] au = (byte[]) cache.get("au");
			if (au != null) {
				try {
					announce_url = StringInterner.internURL(new URL((new String(au, "UTF-8"))));
				} catch (Throwable e) {
				}
			}
			List ag = (List) cache.get("ag");
			if (ag != null) {
				try {
					announce_group = importGroup(ag);
				} catch (Throwable e) {
				}
			}
		}

		protected static Map
		export(
			DownloadManagerState dms )

			throws TOTorrentException
		{
			Map	cache = new HashMap();

			TOTorrent	state = dms.getTorrent();

			cache.put("hash", state.getHash());
			cache.put("name", state.getName());
			cache.put("utf8name", state.getUTF8Name() == null ? "" : state.getUTF8Name());
			cache.put("comment", state.getComment());
			cache.put("createdby", state.getCreatedBy());
			cache.put("size", new Long( state.getSize()));

			cache.put("encoding", state.getAdditionalStringProperty("encoding"));
			cache.put("torrent filename", state.getAdditionalStringProperty("torrent filename"));

			cache.put("attributes", state.getAdditionalMapProperty( ATTRIBUTE_KEY));
			cache.put("azp", state.getAdditionalMapProperty( AZUREUS_PROPERTIES_KEY));

			try {
				cache.put("au", state.getAnnounceURL().toExternalForm());
				cache.put("ag", exportGroup(state.getAnnounceURLGroup()));

			} catch (Throwable e) {
			}

			boolean	discard_pieces = dms.isResumeDataComplete();

			TOTorrent	t = dms.getTorrent();

			if (t instanceof CachedStateWrapper) {

					// We don't want to force a fixup here when saving!

				CachedStateWrapper csw = (CachedStateWrapper)t;

				if (!discard_pieces) {

						// discard pieces if they are currently discarded

					discard_pieces = csw.peekPieces() == null;
				}

				Boolean	simple_torrent = csw.simple_torrent;

				if (simple_torrent != null) {

					cache.put("simple", new Long(simple_torrent.booleanValue()?1:0));

				} else {

					Debug.out("Failed to cache simple state");
				}

				int	fc = csw.file_count;

				if (fc > 0) {

					cache.put("fc", new Long( fc));
				}
			} else {
				if (t instanceof TorrentUtils.torrentDelegate) {

					// torrent is already 'fixed up' so no harm in directly grabbing stuff for export as already loaded

					cache.put("simple", new Long(t.isSimpleTorrent()?1:0));

					cache.put("fc", t.getFileCount());

				} else {

					Debug.out("Hmm, torrent isn't cache-state-wrapper, it is " + t);
				}
			}

			cache.put("dp", new Long( discard_pieces?1:0));

			return (cache);
		}

		protected static List
		exportGroup(
			TOTorrentAnnounceURLGroup		group) {
			TOTorrentAnnounceURLSet[]	sets = group.getAnnounceURLSets();

			List	result = new ArrayList();

			for (int i=0;i<sets.length;i++) {

				TOTorrentAnnounceURLSet	set = sets[i];

				URL[]	urls = set.getAnnounceURLs();

				if (urls.length > 0) {

					List	s = new ArrayList(urls.length);

					for (int j=0;j<urls.length;j++) {

						s.add( urls[j].toExternalForm());
					}

					result.add(s);
				}
			}

			return (result);
		}

		protected cacheGroup
		importGroup(
			List		l )

			throws Exception
		{
			return (new cacheGroup( l));
		}

		protected class
		cacheGroup
			implements TOTorrentAnnounceURLGroup
		{
			private TOTorrentAnnounceURLSet[]		sets;

			protected
			cacheGroup(
				List	group )

				throws Exception
			{
				sets = new TOTorrentAnnounceURLSet[ group.size() ];

				for (int i = 0; i < sets.length; i++) {

					List set = (List) group.get(i);

					URL[] urls = new URL[set.size()];

					for (int j = 0; j < urls.length; j++) {

						urls[j] = StringInterner.internURL(new URL(new String((byte[]) set.get(j), "UTF-8")));
					}

					sets[i] = new cacheSet(urls);
				}
			}

			public TOTorrentAnnounceURLSet[]
           	getAnnounceURLSets() {
				if (announce_group == null && fixup()) {

					return delegate.getAnnounceURLGroup().getAnnounceURLSets();
				}

				return (sets);
			}

			void fixGroup() {
				TOTorrentAnnounceURLSet[] realSets = delegate.getAnnounceURLGroup().getAnnounceURLSets();

				if (realSets.length != sets.length) {

					// not a major issue - since we now allow the cached groups and persisted ones to
					// drift (see DNS changes) the cached ones will be accurate and the persisted ones
					// will be re-adjusted should they become visible
					// Debug.out("Cached announce group state does not match real state");

				} else {

					for (int i=0;i<realSets.length;i++) {

						if (sets[i] instanceof cacheSet) {

							((cacheSet)sets[i]).delegateSet = realSets[i];
						}
					}

					sets = null;
				}
			}

           	public void
           	setAnnounceURLSets(
           		TOTorrentAnnounceURLSet[]	toSet )
           	{
     	   		if (fixup()) {

     	   			TOTorrentAnnounceURLSet[] modToSet = new TOTorrentAnnounceURLSet[toSet.length];

               		for (int i = 0; i < toSet.length; i++) {

						TOTorrentAnnounceURLSet set = toSet[i];

						if (set instanceof cacheSet) {

							modToSet[i] = ((cacheSet) set).delegateSet;
						}

						if (modToSet[i] == null) {

							modToSet[i] = set;
						}
					}

    				delegate.getAnnounceURLGroup().setAnnounceURLSets(modToSet);
    			}
           	}

           	public TOTorrentAnnounceURLSet
           	createAnnounceURLSet(
           		URL[]	urls )
           	{
           		if (fixup()) {

    				return (delegate.getAnnounceURLGroup().createAnnounceURLSet( urls));
    			}

           		return (null);
           	}

           	protected class
           	cacheSet
           		implements TOTorrentAnnounceURLSet
           	{
           		private URL[]					urls;
           		private TOTorrentAnnounceURLSet delegateSet;

				public cacheSet(URL[] urls) {
					this.urls = urls;
				}

           		public URL[]
           		getAnnounceURLs()
           		{
           			if (announce_group == null && fixup() && delegateSet != null) {

           				return delegateSet.getAnnounceURLs();
           			}

           			return (urls);
           		}

		    	public void
		    	setAnnounceURLs(
		    		URL[]		toSet )
		    	{
		    		if (fixup() && delegateSet != null) {

		    			delegateSet.setAnnounceURLs(toSet);

		    		} else {

		    			urls = toSet;
		    		}
		    	}
           	}
		}

		protected void clearCache() {
			cache	= null;
		}

		protected boolean fixup() {
			try {
				if (delegate == null) {

					synchronized(this) {

						if (delegate == null) {

							// System.out.println("Fixing up " + this);

							if (fixup_failure != null) {

								throw (fixup_failure);
							}

							delegate = loadRealState();

							if (discard_fluff) {

								delegate.setDiscardFluff(discard_fluff);
							}

							if (cache != null) {

								Debug.out("Cache miss forced fixup");
							}

							cache = null;

								// join cache view back up with real state to save memory as the one
								// we've just read is irrelevant due to the cache values being
								// used

							if (cacheAttributes != null) {

								delegate.setAdditionalMapProperty(ATTRIBUTE_KEY, cacheAttributes);

								cacheAttributes = null;
							}

							if (cacheAzp != null) {

								delegate.setAdditionalMapProperty(AZUREUS_PROPERTIES_KEY, cacheAzp);

								cacheAzp = null;
							}

							announce_url = null;

							if (announce_group != null) {

								announce_group.fixGroup();

								announce_group = null;
							}
						}
					}
				}

				return (true);

			} catch (TOTorrentException e) {

				fixup_failure	= e;

				if (downloadManager != null) {

					downloadManager.setTorrentInvalid(Debug.getNestedExceptionMessage( e));

				} else {

					if (!logged_failure) {

						logged_failure = true;

						Debug.out("Torrent can't be loaded: " + Debug.getNestedExceptionMessage( e));
					}
				}
			}

			return (false);
		}

		protected TorrentUtils.ExtendedTorrent
		loadRealState()

			throws TOTorrentException
		{
			// System.out.println("loadReal: " + torrent_file + " dp=" + discard_pieces + ": " + Debug.getCompressedStackTrace().substring(114));

			if (!SUPPRESS_FIXUP_ERRORS && Constants.isCVSVersion()) {

				if (Thread.currentThread().isDaemon()) {

					// Debug.outNoStack("Fixup on thread " + Thread.currentThread().getName() + ": " + Debug.getCompressedStackTrace());

				} else {

					Debug.outNoStack(Debug.getCompressedStackTrace(new Exception() {public String toString() { return ("Premature fixup?");}}, 2, 10, true), true);
				}
			}

			File	saved_file = getStateFile(torrentHashWrapper.getBytes());

			if (saved_file.exists()) {

				try {

					return (TorrentUtils.readDelegateFromFile( saved_file, discard_pieces));

				} catch (Throwable e) {

					Debug.out("Failed to load download state for " + saved_file);
				}
			}

				// try reading from original

			TOTorrent original_torrent = TorrentUtils.readFromFile(new File(torrentFile), true);

			torrentHashWrapper = original_torrent.getHashWrapper();

			saved_file = getStateFile( torrentHashWrapper.getBytes());

			if (saved_file.exists()) {

				try {
					return (TorrentUtils.readDelegateFromFile( saved_file, discard_pieces));

				} catch (Throwable e) {

					Debug.out("Failed to load download state for " + saved_file);
				}
			}

				// we must copy the torrent as we want one independent from the
				// original (someone might still have references to the original
				// and do stuff like write it somewhere else which would screw us
				// up)

			TorrentUtils.copyToFile(original_torrent, saved_file);

			return (TorrentUtils.readDelegateFromFile( saved_file, discard_pieces));
		}


		public byte[]
    	getName() {
			Map	c = cache;

			if (c != null) {

				byte[] name = (byte[])c.get("name");
				if (name != null) {
					return name;
				}
			}

	   		if (fixup()) {

				return ( delegate.getName());
			}

	   		// Does grabbing the nested exception message always give us something useful?
	   		// My experience is that we just get an empty string here...
	   		return (("Error - " + Debug.getNestedExceptionMessage(fixup_failure)).getBytes());
    	}

		public String getUTF8Name() {
			Map	c = cache;

			if (c != null) {

				byte[] name = (byte[])c.get("utf8name");
				if (name != null) {
					String utf8name;
					try {
						utf8name = new String(name, "utf8");
					} catch (UnsupportedEncodingException e) {
						return null;
					}
					if (utf8name.length() == 0) {
						return null;
					}
					return utf8name;
				}
			}

			if (fixup()) {
				return delegate.getUTF8Name();
			}
			return null;
		}

    	public boolean
    	isSimpleTorrent()
    	{
    		if (simple_torrent != null) {

    			return ( simple_torrent.booleanValue());
    		}

    		if (fixup()) {

    			boolean st = delegate.isSimpleTorrent();

    			simple_torrent = Boolean.valueOf(st);

    			return (st);
    		}

    		return (false);
    	}

    	public byte[]
    	getComment()
    	{
			Map	c = cache;

			if (c != null) {

				return ((byte[])c.get("comment"));
			}

	   		if (fixup()) {

				return ( delegate.getComment());
			}

	   		return (null);
    	}

    	public void
    	setComment(
    		String		comment )
       	{
	   		if (fixup()) {

				delegate.setComment(comment);
			}
    	}

    	public long
    	getCreationDate()
       	{
	   		if (fixup()) {

				return ( delegate.getCreationDate());
			}

	   		return (0);
    	}

    	public void
    	setCreationDate(
    		long		date )
       	{
	   		if (fixup()) {

				delegate.setCreationDate(date);
			}
    	}

    	public byte[]
    	getCreatedBy()
       	{
			Map	c = cache;

			if (c != null) {

				return ((byte[])c.get("createdby"));
			}

	   		if (fixup()) {

				return ( delegate.getCreatedBy());
			}

	   		return (null);
    	}

       	public void
    	setCreatedBy(
    		byte[]		cb )
       	{
	   		if (fixup()) {

				delegate.setCreatedBy(cb);
			}
    	}

    	public boolean
    	isCreated()
       	{
	   		if (fixup()) {

				return ( delegate.isCreated());
			}

	   		return (false);
    	}

    	public boolean
    	isDecentralised()
    	{
    		return ( TorrentUtils.isDecentralised( getAnnounceURL()));
    	}

    	public URL
    	getAnnounceURL()
       	{
    		if (announce_url != null) {

    			return (announce_url);
    		}

	   		if (fixup()) {

				return ( delegate.getAnnounceURL());
			}

	   		return (null);
    	}

    	public boolean
    	setAnnounceURL(
    		URL		url )
       	{
    		if (announce_url != null) {

    			if (announce_url.toExternalForm().equals( url.toExternalForm())) {

    				return (false);
    			}
    		}

	   		if (fixup()) {

				return (delegate.setAnnounceURL( url));

			} else {

				announce_url = url;
			}

	   		return (false);
    	}

    	public TOTorrentAnnounceURLGroup
    	getAnnounceURLGroup()
       	{
    		if (announce_group != null) {

    			return (announce_group);
    		}

	   		if (fixup()) {

				return ( delegate.getAnnounceURLGroup());
			}

	   		return (null);
    	}

    	public byte[][]
    	getPieces()

    		throws TOTorrentException
	   	{
	   		if (fixup()) {

				return ( delegate.getPieces());
			}

	   		throw (fixup_failure);
    	}


    	public void
    	setPieces(
    		byte[][]	pieces )

    		throws TOTorrentException
	   	{
	   		if (fixup()) {

				delegate.setPieces(pieces);

				return;
			}

	   		throw (fixup_failure);
    	}

    	public byte[][]
    	peekPieces()

    		throws TOTorrentException
    	{
    		if (fixup()) {

    			return ( delegate.peekPieces());
    		}

	   		throw (fixup_failure);
    	}

    	public void
    	setDiscardFluff(
    		boolean discard )
    	{
    		discard_fluff	= discard;

    		if (delegate != null) {

    			delegate.setDiscardFluff(discard_fluff);
    		}
     	}

    	public long
    	getPieceLength()
       	{
	   		if (fixup()) {

				return ( delegate.getPieceLength());
			}

	   		return (0);
       	}

		public int
    	getNumberOfPieces()
       	{
	   		if (fixup()) {

				return ( delegate.getNumberOfPieces());
			}

	   		return (0);
    	}

    	public long
    	getSize()
       	{
    		if (size > 0) {

    			return (size);
    		}

	   		if (fixup()) {

				size = delegate.getSize();

				return (size);
			}

	   		return (0);
    	}

       	public int
    	getFileCount()
       	{
       		if (file_count == 0) {

       			if (fixup()) {

       				file_count= delegate.getFileCount();
       			}
       		}

       		return (file_count);
       	}

    	public TOTorrentFile[]
    	getFiles()
       	{
	   		if (fixup()) {

				return ( delegate.getFiles());
			}

	   		return (new TOTorrentFile[0]);
    	}

		public byte[] getHash()
				throws TOTorrentException {
			// optimise this
			return (torrentHashWrapper.getBytes());
		}

    	public HashWrapper
    	getHashWrapper()

    		throws TOTorrentException
	   	{
    		return (torrentHashWrapper);
    	}

    	public void
    	setHashOverride(
    		byte[] hash )

    		throws TOTorrentException
    	{
    		throw (new TOTorrentException("Not supported", TOTorrentException.RT_HASH_FAILS));
    	}

    	public boolean
    	hasSameHashAs(
    		TOTorrent		other )
       	{
    		try {
    			byte[]	other_hash = other.getHash();

    			return (Arrays.equals( getHash(), other_hash));

    		} catch (TOTorrentException e) {

    			Debug.printStackTrace(e);

    			return (false);
    		}
       	}

    	public boolean
    	getPrivate()
       	{
	   		if (fixup()) {

				return ( delegate.getPrivate());
			}

	   		return (false);
    	}

    	public void
    	setPrivate(
    		boolean	_private )

    		throws TOTorrentException
    	   	{
    	   		if (fixup()) {

    				delegate.setPrivate(_private);
    			}
        	}

    	public void
    	setAdditionalStringProperty(
    		String		name,
    		String		value )
       	{
	   		if (fixup()) {

				delegate.setAdditionalStringProperty(name, value);
			}
    	}

    	public String
    	getAdditionalStringProperty(
    		String		name )
       	{
			Map	c = cache;

			if (c != null && ( name.equals("encoding") || name.equals("torrent filename"))) {

				byte[] res = (byte[])c.get(name);

				if (res == null) {

					return (null);
				}

				try {
					return (new String( res, "UTF8"));

				} catch (Throwable e) {

					Debug.printStackTrace(e);

					return (null);
				}
			}

	   		if (fixup()) {

				return (delegate.getAdditionalStringProperty( name));
			}

	   		return (null);
    	}

    	public void
    	setAdditionalByteArrayProperty(
    		String		name,
    		byte[]		value )
       	{
	   		if (fixup()) {

				delegate.setAdditionalByteArrayProperty(name, value);
			}
    	}

    	public byte[]
    	getAdditionalByteArrayProperty(
    		String		name )
       	{
	   		if (fixup()) {

				return (delegate.getAdditionalByteArrayProperty( name));
			}

	   		return (null);
    	}

    	public void
    	setAdditionalLongProperty(
    		String		name,
    		Long		value )
       	{
	   		if (fixup()) {

				delegate.setAdditionalLongProperty(name, value);
			}
    	}

    	public Long
    	getAdditionalLongProperty(
    		String		name )
       	{
	   		if (fixup()) {

				return (delegate.getAdditionalLongProperty( name));
			}

	   		return (null);
    	}


    	public void
    	setAdditionalListProperty(
    		String		name,
    		List		value )
       	{
	   		if (fixup()) {

				delegate.setAdditionalListProperty(name, value);
			}
    	}

    	public List
    	getAdditionalListProperty(
    		String		name )
       	{
	   		if (fixup()) {

				return (delegate.getAdditionalListProperty( name));
			}

	   		return (null);
    	}

    	public void
    	setAdditionalMapProperty(
    		String		name,
    		Map			value )
       	{
	   		if (fixup()) {

				delegate.setAdditionalMapProperty(name, value);
			}
    	}

    	public Map
    	getAdditionalMapProperty(
    		String		name )
       	{
			Map	c = cacheAttributes;

			if (c != null &&  name.equals("attributes")) {

				return (c);
			}

			c = cacheAzp;

			if (c != null &&  name.equals("azureus_properties")) {

				return (c);
			}

	   		if (fixup()) {

				return (delegate.getAdditionalMapProperty( name));
			}

	   		return (null);
    	}

    	public Object
    	getAdditionalProperty(
    		String		name )
       	{
	   		if (fixup()) {

				return (delegate.getAdditionalProperty( name));
			}

	   		return (null);
    	}

    	public void
    	setAdditionalProperty(
    		String		name,
    		Object		value )
       	{
	   		if (fixup()) {

				delegate.setAdditionalProperty(name, value);
			}
    	}

    	public void
    	removeAdditionalProperty(
    		String name )
       	{
	   		if (fixup()) {

				delegate.removeAdditionalProperty(name);
			}
    	}

    	public void
    	removeAdditionalProperties()
       	{
	   		if (fixup()) {

				delegate.removeAdditionalProperties();
			}
    	}

    	public void
    	serialiseToBEncodedFile(
    		File		file )

    		throws TOTorrentException
	   	{
	   		if (fixup()) {

				delegate.serialiseToBEncodedFile(file);

				return;
			}

	   		throw (fixup_failure);
    	}

    	public Map
    	serialiseToMap()

    		throws TOTorrentException
	   	{
	   		if (fixup()) {

				return ( delegate.serialiseToMap());
			}

	   		throw (fixup_failure);
    	}

       public void
       serialiseToXMLFile(
    	   File		file )

    	   throws TOTorrentException
   	   	{
   	   		if (fixup()) {

   				delegate.serialiseToXMLFile(file);

   				return;
   			}

   	   		throw (fixup_failure);
       	}

       public void
       addListener(
    	  TOTorrentListener		l )
       {
    	   if (fixup()) {

    		   delegate.addListener(l);
    	   }
       }

       public void
       removeListener(
    	  TOTorrentListener		l )
       {
    	   if (fixup()) {

    		   delegate.removeListener(l);
    	   }
       }

       public AEMonitor
       getMonitor()
      	{
	   		if (fixup()) {

				return ( delegate.getMonitor());
			}

	   		return (null);
      	}

       public void
       print()
      	{
	   		if (fixup()) {

				delegate.print();
			}
      	}

     	/* (non-Javadoc)
     	 * @see org.gudy.azureus2.core3.logging.LogRelation#getLogRelationText()
     	 */
     	public String getRelationText() {
     		return "Torrent: '" + new String(getName()) + "'";
     	}

     	/* (non-Javadoc)
     	 * @see org.gudy.azureus2.core3.logging.LogRelation#queryForClass(java.lang.Class)
     	 */
     	public Object[] getQueryableInterfaces() {
     		// yuck
     		try {
     			return new Object[] { AzureusCoreFactory.getSingleton()
     					.getGlobalManager().getDownloadManager(this) };
     		} catch (Exception e) {
     		}

     		return null;
     	}
	}
}