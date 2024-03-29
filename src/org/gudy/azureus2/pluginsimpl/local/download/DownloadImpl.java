/*
 * File    : DownloadImpl.java
 * Created : 06-Jan-2004
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

package org.gudy.azureus2.pluginsimpl.local.download;

/**
 * @author parg
 *
 */

import java.io.File;
import java.net.URL;
import java.util.*;

import org.gudy.azureus2.core3.category.Category;
import org.gudy.azureus2.core3.category.CategoryManager;
import org.gudy.azureus2.core3.download.*;
import org.gudy.azureus2.core3.download.DownloadManager;
import org.gudy.azureus2.core3.download.DownloadManagerListener;
import org.gudy.azureus2.core3.download.impl.DownloadManagerMoveHandler;
import org.gudy.azureus2.core3.global.GlobalManager;
import org.gudy.azureus2.core3.global.GlobalManagerDownloadRemovalVetoException;
import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.logging.LogRelation;
import org.gudy.azureus2.core3.peer.PEPeer;
import org.gudy.azureus2.core3.peer.PEPeerManager;
import org.gudy.azureus2.core3.peer.PEPeerSource;
import org.gudy.azureus2.core3.torrent.TOTorrent;
import org.gudy.azureus2.core3.tracker.client.TRTrackerAnnouncer;
import org.gudy.azureus2.core3.tracker.client.TRTrackerAnnouncerResponse;
import org.gudy.azureus2.core3.tracker.client.TRTrackerScraperResponse;
import org.gudy.azureus2.core3.util.AEMonitor;
import org.gudy.azureus2.core3.util.BEncoder;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.SystemTime;
import org.gudy.azureus2.plugins.ddb.DistributedDatabase;
import org.gudy.azureus2.plugins.disk.DiskManager;
import org.gudy.azureus2.plugins.disk.DiskManagerFileInfo;
import org.gudy.azureus2.plugins.download.*;
import org.gudy.azureus2.plugins.download.savelocation.SaveLocationChange;
import org.gudy.azureus2.plugins.network.RateLimiter;
import org.gudy.azureus2.plugins.peers.PeerManager;
import org.gudy.azureus2.plugins.tag.Tag;
import org.gudy.azureus2.plugins.torrent.Torrent;
import org.gudy.azureus2.plugins.torrent.TorrentAttribute;
import org.gudy.azureus2.pluginsimpl.local.ddb.DDBaseImpl;
import org.gudy.azureus2.pluginsimpl.local.deprecate.PluginDeprecation;
import org.gudy.azureus2.pluginsimpl.local.disk.DiskManagerFileInfoImpl;
import org.gudy.azureus2.pluginsimpl.local.peers.PeerManagerImpl;
import org.gudy.azureus2.pluginsimpl.local.torrent.TorrentImpl;
import org.gudy.azureus2.pluginsimpl.local.torrent.TorrentManagerImpl;
import org.gudy.azureus2.pluginsimpl.local.utils.UtilitiesImpl;

import com.aelitis.azureus.core.peermanager.messaging.bittorrent.BTHandshake;
import com.aelitis.azureus.core.tag.TagManagerFactory;
import com.aelitis.azureus.core.tracker.TrackerPeerSource;
import com.aelitis.azureus.core.tracker.TrackerPeerSourceAdapter;
import com.aelitis.azureus.core.util.CopyOnWriteList;
import com.aelitis.azureus.core.util.CopyOnWriteMap;

public class DownloadImpl
	extends LogRelation
	implements 	Download, DownloadManagerListener,
				DownloadManagerTrackerListener,
				DownloadManagerStateListener, DownloadManagerActivationListener,
				DownloadManagerStateAttributeListener
{
	private final DownloadManagerImpl		manager;
	private final DownloadManager			downloadManager;
	private final DownloadStatsImpl			downloadStats;

	private int			latest_state		= ST_STOPPED;
	private boolean 	latest_forcedStart;

	private DownloadAnnounceResultImpl		last_announce_result 	= new DownloadAnnounceResultImpl(this,null);
	private DownloadScrapeResultImpl		last_scrape_result		= new DownloadScrapeResultImpl(this, null);
	private AggregateScrapeResult			last_aggregate_scrape	= new AggregateScrapeResult(this);

    private TorrentImpl torrent = null;

	private List<DownloadListener> listeners = new ArrayList<>();
	private AEMonitor	listeners_mon			= new AEMonitor("Download:L");
	private List		property_listeners		= new ArrayList();
	private List		tracker_listeners		= new ArrayList();
	private AEMonitor	tracker_listeners_mon	= new AEMonitor("Download:TL");
	private List		removal_listeners 		= new ArrayList();
	private AEMonitor	removal_listeners_mon	= new AEMonitor("Download:RL");
	private Map			peerListeners			= new HashMap();
	private AEMonitor	peerListenersMon		= new AEMonitor("Download:PL");

	private CopyOnWriteList completion_listeners     = new CopyOnWriteList();

	private CopyOnWriteMap read_attribute_listeners_map_cow  = new CopyOnWriteMap();
	private CopyOnWriteMap write_attribute_listeners_map_cow = new CopyOnWriteMap();

	private CopyOnWriteList	activation_listeners = new CopyOnWriteList();
	private DownloadActivationEvent	activationState;

	private Map<String,int[]>	announceResponseMap;

	protected DownloadImpl(
		DownloadManagerImpl	_manager,
		DownloadManager		_dm) {
		
		manager				= _manager;
		downloadManager	= _dm;
		downloadStats		= new DownloadStatsImpl(downloadManager);

		activationState =
			new DownloadActivationEvent() {
				public Download
				getDownload() {
					return (DownloadImpl.this);
				}

				public int getActivationCount() {
					return ( downloadManager.getActivationCount());
				}
			};

		downloadManager.addListener(this);

		latest_forcedStart = downloadManager.isForceStart();
	}

	// Not available to plugins
	public DownloadManager
	getDownload() {
		return (downloadManager);
	}

	public int getState() {
		return (convertState( downloadManager.getState()));
	}

	public int getSubState() {
		int	state = getState();

		if (state == ST_STOPPING) {

			int	substate = downloadManager.getSubState();

			if (substate == DownloadManager.STATE_QUEUED) {

				return (ST_QUEUED);

			} else if (substate == DownloadManager.STATE_STOPPED) {

				return (ST_STOPPED);

			} else if (substate == DownloadManager.STATE_ERROR) {

				return (ST_ERROR);
			}
		}

		return (state);
	}

	protected int convertState(int dmState) {
		// dm states: waiting -> initialising -> initialized ->
		//		disk states: allocating -> checking -> ready ->
		// dm states: downloading -> finishing -> seeding -> stopping -> stopped
		// "initialize" call takes from waiting -> initialising -> waiting (no port) or initialized (ok)
		// if initialized then disk manager runs through to ready
		// "startdownload" takes ready -> dl etc.
		// "stopIt" takes to stopped which is equiv to ready
		int	ourState;
		switch(dmState) {
			case DownloadManager.STATE_WAITING:
			{
				ourState	= ST_WAITING;
				break;
			}
			case DownloadManager.STATE_INITIALIZING:
			case DownloadManager.STATE_INITIALIZED:
			case DownloadManager.STATE_ALLOCATING:
			case DownloadManager.STATE_CHECKING:
			{
				ourState	= ST_PREPARING;
				break;
			}
			case DownloadManager.STATE_READY:
			{
				ourState	= ST_READY;
				break;
			}
			case DownloadManager.STATE_DOWNLOADING:
			case DownloadManager.STATE_FINISHING:		// finishing download - transit to seeding
			{
				ourState	= ST_DOWNLOADING;
				break;
			}
			case DownloadManager.STATE_SEEDING:
			{
				ourState	= ST_SEEDING;
				break;
			}
			case DownloadManager.STATE_STOPPING:
			{
				ourState	= ST_STOPPING;
				break;
			}
			case DownloadManager.STATE_STOPPED:
			{
				ourState	= ST_STOPPED;
				break;
			}
			case DownloadManager.STATE_QUEUED:
			{
				ourState	= ST_QUEUED;
				break;
			}
			case DownloadManager.STATE_ERROR:
			{
				ourState	= ST_ERROR;
				break;
			}
			default:
			{
				ourState	= ST_ERROR;
			}
		}
		return (ourState);
	}

	public String getErrorStateDetails() {
		return ( downloadManager.getErrorDetails());
	}

	public long getFlags() {
		return ( downloadManager.getDownloadState().getFlags());
	}

	public boolean getFlag(
		long		flag) {
		return (downloadManager.getDownloadState().getFlag( flag));
	}

	public void setFlag(long flag, boolean set) {
		downloadManager.getDownloadState().setFlag(flag, set);
	}

	public int getIndex() {
		GlobalManager globalManager = downloadManager.getGlobalManager();
		return globalManager.getIndexOf(downloadManager);
	}

    public Torrent
    getTorrent()
    {
    	if (this.torrent != null) {return this.torrent;}

        TOTorrent torrent = downloadManager.getTorrent();
        if (torrent == null) {return null;}
        this.torrent = new TorrentImpl(torrent);
        return this.torrent;
    }

	public void initialize() throws DownloadException {
		int	state = downloadManager.getState();
		if (state == DownloadManager.STATE_WAITING) {
			downloadManager.initialize();
		} else {
			throw (new DownloadException("Download::initialize: download not waiting (state=" + state + ")"));
		}
	}

	public void start() throws DownloadException {
		int	state = downloadManager.getState();
		if (state == DownloadManager.STATE_READY) {
			downloadManager.startDownload();
		} else {
			throw (new DownloadException("Download::start: download not ready (state=" + state + ")"));
		}
	}

	public void restart() throws DownloadException {
		int	state = downloadManager.getState();
		if (	state == DownloadManager.STATE_STOPPED ||
				state == DownloadManager.STATE_QUEUED) {
			downloadManager.setStateWaiting();
		} else {
			throw (new DownloadException("Download::restart: download already running (state=" + state + ")"));
		}
	}

	public void stop()

		throws DownloadException
	{
		if (downloadManager.getState() != DownloadManager.STATE_STOPPED) {

			downloadManager.stopIt(DownloadManager.STATE_STOPPED, false, false);

		} else {

			throw (new DownloadException("Download::stop: download already stopped"));
		}
	}

	public void stopAndQueue()

		throws DownloadException
	{
		if (downloadManager.getState() != DownloadManager.STATE_QUEUED) {

			downloadManager.stopIt(DownloadManager.STATE_QUEUED, false, false);

		} else {

			throw (new DownloadException("Download::stopAndQueue: download already queued"));
		}
	}

	public void recheckData()

		throws DownloadException
	{
		if (!downloadManager.canForceRecheck()) {

			throw (new DownloadException("Download::recheckData: download must be stopped, queued or in error state"));
		}

		downloadManager.forceRecheck();
	}

	public boolean isStartStopLocked() {
		return (downloadManager.getState() == DownloadManager.STATE_STOPPED);
	}

	public boolean isForceStart() {
		return downloadManager.isForceStart();
	}

	public void setForceStart(boolean forceStart) {
		downloadManager.setForceStart(forceStart);
	}

	public boolean isPaused() {
		return ( downloadManager.isPaused());
	}

	public void pause() {
		downloadManager.pause();
	}

	public void resume() {
		downloadManager.resume();
	}

	public int getPosition() {
		return downloadManager.getPosition();
	}

	public long getCreationTime() {
		return ( downloadManager.getCreationTime());
	}

	public void setPosition(int newPosition) {
		downloadManager.setPosition(newPosition);
	}

	public void moveUp() {
		downloadManager.getGlobalManager().moveUp(downloadManager);
	}

	public void moveDown() {
		downloadManager.getGlobalManager().moveDown(downloadManager);
	}

	public void moveTo(
		int	pos) {
		downloadManager.getGlobalManager().moveTo(downloadManager, pos);
	}

	public String getName() {
		return downloadManager.getDisplayName();
	}

  public String getTorrentFileName() {
    return downloadManager.getTorrentFileName();
  }

  public String getCategoryName() {
    Category category = downloadManager.getDownloadState().getCategory();
    if (category == null)
      category = CategoryManager.getCategory(Category.TYPE_UNCATEGORIZED);

    if (category == null)
      return null;
    return category.getName();
  }

  public List<Tag> getTags() {
	  return (new ArrayList<Tag>( TagManagerFactory.getTagManager().getTagsForTaggable( downloadManager)));
  }

  public String
  getAttribute(
  	TorrentAttribute		attribute )
  {
  	String	name = convertAttribute(attribute);

  	if (name != null) {

  		return (downloadManager.getDownloadState().getAttribute( name));
  	}

  	return (null);
  }

  public String[]
  getListAttribute(
  	TorrentAttribute		attribute )
  {
	  	String	name = convertAttribute(attribute);

	  	if (name != null) {

	  		return (downloadManager.getDownloadState().getListAttribute( name));
	  	}

	  	return (null);
  }

  public void
  setListAttribute(
	TorrentAttribute attribute,
	String[] value)
  {
	  String name = convertAttribute(attribute);

	  if (name != null) {
		  downloadManager.getDownloadState().setListAttribute(name, value);
	  }
  }

  public void
  setMapAttribute(
	TorrentAttribute		attribute,
	Map						value )
  {
	  	String	name = convertAttribute(attribute);

	  	if (name != null) {

	  			// gotta clone before updating in case user has read values and then just
	  			// updated them - setter code optimises out sets of the same values...

			downloadManager.getDownloadState().setMapAttribute(name, BEncoder.cloneMap( value));
	  	}
  }

  public Map
  getMapAttribute(
	TorrentAttribute		attribute )
  {
	  	String	name = convertAttribute(attribute);

	  	if (name != null) {

	  		return (downloadManager.getDownloadState().getMapAttribute( name));
	  	}

	  	return (null);
  }

  public void
  setAttribute(
  	TorrentAttribute		attribute,
	String					value )
  {
 	String	name = convertAttribute(attribute);

  	if (name != null) {

  		downloadManager.getDownloadState().setAttribute(name, value);
  	}
  }

  public boolean hasAttribute(TorrentAttribute attribute) {
	  String name = convertAttribute(attribute);
	  if (name == null) {return false;}
	  return downloadManager.getDownloadState().hasAttribute(name);
  }

  public boolean getBooleanAttribute(TorrentAttribute attribute) {
	  String name = convertAttribute(attribute);
	  if (name == null) {return false;} // Default value
	  return downloadManager.getDownloadState().getBooleanAttribute(name);
  }

  public void setBooleanAttribute(TorrentAttribute attribute, boolean value) {
	  String name = convertAttribute(attribute);
	  if (name != null) {
		  downloadManager.getDownloadState().setBooleanAttribute(name, value);
	  }
  }

  public int getIntAttribute(TorrentAttribute attribute) {
	  String name = convertAttribute(attribute);
	  if (name == null) {return 0;} // Default value
	  return downloadManager.getDownloadState().getIntAttribute(name);
  }

  public void setIntAttribute(TorrentAttribute attribute, int value) {
	  String name = convertAttribute(attribute);
	  if (name != null) {
		  downloadManager.getDownloadState().setIntAttribute(name, value);
	  }
  }

  public long getLongAttribute(TorrentAttribute attribute) {
	  String name = convertAttribute(attribute);
	  if (name == null) {return 0L;} // Default value
	  return downloadManager.getDownloadState().getLongAttribute(name);
  }

  public void setLongAttribute(TorrentAttribute attribute, long value) {
	  String name = convertAttribute(attribute);
	  if (name != null) {
		  downloadManager.getDownloadState().setLongAttribute(name, value);
	  }
  }

  protected String
  convertAttribute(
  	TorrentAttribute		attribute )
  {
 	if (attribute.getName() == TorrentAttribute.TA_CATEGORY) {

  		return (DownloadManagerState.AT_CATEGORY);

 	} else if (attribute.getName() == TorrentAttribute.TA_NETWORKS) {

		return (DownloadManagerState.AT_NETWORKS);

 	} else if (attribute.getName() == TorrentAttribute.TA_TRACKER_CLIENT_EXTENSIONS) {

		return (DownloadManagerState.AT_TRACKER_CLIENT_EXTENSIONS);

	} else if (attribute.getName() == TorrentAttribute.TA_PEER_SOURCES) {

		return (DownloadManagerState.AT_PEER_SOURCES);

	} else if (attribute.getName() == TorrentAttribute.TA_DISPLAY_NAME) {

		return (DownloadManagerState.AT_DISPLAY_NAME);

	} else if (attribute.getName() == TorrentAttribute.TA_USER_COMMENT) {

		return (DownloadManagerState.AT_USER_COMMENT);

	} else if (attribute.getName() == TorrentAttribute.TA_RELATIVE_SAVE_PATH) {

		return (DownloadManagerState.AT_RELATIVE_SAVE_PATH);

	} else if (attribute.getName() == TorrentAttribute.TA_SHARE_PROPERTIES) {

			// this is a share-level attribute only, not propagated to individual downloads

		return (null);

	} else if (attribute.getName().startsWith("Plugin.")) {

		return ( attribute.getName());

  	} else {

  		Debug.out("Can't convert attribute '" + attribute.getName() + "'");

  		return (null);
  	}
  }

  protected TorrentAttribute
  convertAttribute(
  	String			name )
  {
 	if (name.equals( DownloadManagerState.AT_CATEGORY)) {

  		return (TorrentManagerImpl.getSingleton().getAttribute( TorrentAttribute.TA_CATEGORY));

	} else if (name.equals( DownloadManagerState.AT_NETWORKS)) {

	  	return (TorrentManagerImpl.getSingleton().getAttribute( TorrentAttribute.TA_NETWORKS));

	} else if (name.equals( DownloadManagerState.AT_PEER_SOURCES)) {

		return (TorrentManagerImpl.getSingleton().getAttribute( TorrentAttribute.TA_PEER_SOURCES));

	} else if (name.equals( DownloadManagerState.AT_TRACKER_CLIENT_EXTENSIONS)) {

		return (TorrentManagerImpl.getSingleton().getAttribute( TorrentAttribute.TA_TRACKER_CLIENT_EXTENSIONS));

	} else if (name.equals ( DownloadManagerState.AT_DISPLAY_NAME)) {

		return (TorrentManagerImpl.getSingleton().getAttribute( TorrentAttribute.TA_DISPLAY_NAME));

	} else if (name.equals ( DownloadManagerState.AT_USER_COMMENT)) {

		return (TorrentManagerImpl.getSingleton().getAttribute( TorrentAttribute.TA_USER_COMMENT));

	} else if (name.equals ( DownloadManagerState.AT_RELATIVE_SAVE_PATH)) {

		return (TorrentManagerImpl.getSingleton().getAttribute( TorrentAttribute.TA_RELATIVE_SAVE_PATH));

	} else if (name.startsWith("Plugin.")) {

		return (TorrentManagerImpl.getSingleton().getAttribute( name));

  	} else {

  		return (null);
  	}
  }

  public void setCategory(String sName) {
    Category category = CategoryManager.getCategory(sName);
    if (category == null)
      category = CategoryManager.createCategory(sName);
    downloadManager.getDownloadState().setCategory(category);
  }

  public boolean isPersistent() {
    return downloadManager.isPersistent();
  }

	public void remove()

		throws DownloadException, DownloadRemovalVetoException
	{
		remove(false, false);
	}

	public void remove(
		boolean	delete_torrent,
		boolean	delete_data )

		throws DownloadException, DownloadRemovalVetoException
	{
		int	dl_state = downloadManager.getState();

		if (	dl_state == DownloadManager.STATE_STOPPED 	||
				dl_state == DownloadManager.STATE_ERROR 	||
				dl_state == DownloadManager.STATE_QUEUED) {

			GlobalManager globalManager = downloadManager.getGlobalManager();

			try {

				globalManager.removeDownloadManager(downloadManager, delete_torrent, delete_data);

			} catch (GlobalManagerDownloadRemovalVetoException e) {

				throw (new DownloadRemovalVetoException( e.getMessage()));
			}

		} else {

			throw (new DownloadRemovalVetoException( MessageText.getString("plugin.download.remove.veto.notstopped")));
		}
	}

	public boolean canBeRemoved()

		throws DownloadRemovalVetoException
	{
		int	dl_state = downloadManager.getState();

		if (	dl_state == DownloadManager.STATE_STOPPED 	||
				dl_state == DownloadManager.STATE_ERROR 	||
				dl_state == DownloadManager.STATE_QUEUED) {

			GlobalManager globalManager = downloadManager.getGlobalManager();

			try {
				globalManager.canDownloadManagerBeRemoved(downloadManager, false, false);

			} catch (GlobalManagerDownloadRemovalVetoException e) {

				throw (new DownloadRemovalVetoException( e.getMessage(),e.isSilent()));
			}

		} else {

			throw (new DownloadRemovalVetoException( MessageText.getString("plugin.download.remove.veto.notstopped")));
		}

		return (true);
	}

	public DownloadStats
	getStats() {
		return (downloadStats);
	}

 	public boolean isComplete()
 	{
 		return downloadManager.isDownloadComplete(false);
 	}

 	public boolean isComplete(boolean bIncludeDND) {
 		return downloadManager.isDownloadComplete(bIncludeDND);
 	}

 	public boolean
 	isChecking()
 	{
 		return (downloadStats.getCheckingDoneInThousandNotation() != -1);
 	}

 	public boolean
 	isMoving()
 	{
 		org.gudy.azureus2.core3.disk.DiskManager dm = downloadManager.getDiskManager();

 		if (dm != null) {

 			return (dm.getMoveProgress() != -1);
 		}

 		return (false);
 	}

	protected void isRemovable()
		throws DownloadRemovalVetoException
	{
			// no sync required, see update code

		for (int i=0;i<removal_listeners.size();i++) {

			try {
				((DownloadWillBeRemovedListener)removal_listeners.get(i)).downloadWillBeRemoved(this);

			} catch (DownloadRemovalVetoException e) {

				throw (e);

			} catch (Throwable e) {

				Debug.printStackTrace(e);
			}
		}
	}

	protected void destroy() {
		downloadManager.removeListener(this);
	}


	// DownloadManagerListener methods
	public void stateChanged(
		DownloadManager manager,
		int				state) {
		int	prev_state 	= latest_state;

		latest_state	= convertState(state);

		// System.out.println("Plug: dl = " + getName() + ", prev = " + prev_state + ", curr = " + latest_state + ", signalled state = " + state);
		boolean currForcedStart = isForceStart();

		// Copy reference in case any attempts to remove or add listeners are tried.
		List<DownloadListener> listenersToUse = listeners;

		if (prev_state != latest_state || latest_forcedStart != currForcedStart) {

			latest_forcedStart = currForcedStart;

			for (int i=0;i<listenersToUse.size();i++) {

				try {
					long startTime = SystemTime.getCurrentTime();
					DownloadListener listener = (DownloadListener) listenersToUse.get(i);

					listener.stateChanged(this, prev_state, latest_state);

					long diff = SystemTime.getCurrentTime() - startTime;
					if (diff > 1000) {
						System.out.println("Plugin should move long processes (" + diff
								+ "ms) off of Download's stateChanged listener trigger. "
								+ listener);
					}

				} catch (Throwable e) {
					Debug.printStackTrace(e);
				}
			}
		}
	}

	public void downloadComplete(DownloadManager manager) {
		if (this.completion_listeners.isEmpty()) {return;}
		Iterator itr = this.completion_listeners.iterator();
		DownloadCompletionListener dcl;
		while (itr.hasNext()) {
			dcl = (DownloadCompletionListener)itr.next();
			long startTime = SystemTime.getCurrentTime();
			try {dcl.onCompletion(this);}
			catch (Throwable t) {Debug.printStackTrace(t);}
			long diff = SystemTime.getCurrentTime() - startTime;
			if (diff > 1000) {
				System.out.println("Plugin should move long processes (" + diff + "ms) off of Download's onCompletion listener trigger. " + dcl);
			}
		}
	}

	public void completionChanged(
		DownloadManager 	manager,
		boolean 			bCompleted) {
	}

	public void filePriorityChanged(DownloadManager download, org.gudy.azureus2.core3.disk.DiskManagerFileInfo file) {
	}

  public void
  positionChanged(
  	DownloadManager download,
    int oldPosition,
	int newPosition)
  {
	for (int i = 0; i < listeners.size(); i++) {
		try {
			long startTime = SystemTime.getCurrentTime();
			DownloadListener listener = (DownloadListener)listeners.get(i);

			listener.positionChanged(this, oldPosition, newPosition);

			long diff = SystemTime.getCurrentTime() - startTime;
			if (diff > 1000) {
				System.out.println("Plugin should move long processes (" + diff
						+ "ms) off of Download's positionChanged listener trigger. "
						+ listener);
			}
		} catch (Throwable e) {
			Debug.printStackTrace(e);
		}
	}
  }

	public void addListener(DownloadListener l) {
		try {
			listeners_mon.enter();
			List<DownloadListener> new_listeners = new ArrayList<>(listeners);
			new_listeners.add(l);
			listeners	= new_listeners;
		} finally {
			listeners_mon.exit();
		}
	}


	public void removeListener(
		DownloadListener	l) {
		try {
			listeners_mon.enter();

			List	new_listeners	= new ArrayList(listeners);

			new_listeners.remove(l);

			listeners	= new_listeners;
		} finally {

			listeners_mon.exit();
		}
	}

	public void addAttributeListener(DownloadAttributeListener listener, TorrentAttribute attr, int event_type) {
		String attribute = convertAttribute(attr);
		if (attribute == null) {return;}

		CopyOnWriteMap attr_map = this.getAttributeMapForType(event_type);
		CopyOnWriteList listener_list = (CopyOnWriteList)attr_map.get(attribute);
		boolean add_self = false;

		if (listener_list == null) {
			listener_list = new CopyOnWriteList();
			attr_map.put(attribute, listener_list);
		}
		add_self = listener_list.isEmpty();

		listener_list.add(listener);
		if (add_self) {
			downloadManager.getDownloadState().addListener(this, attribute, event_type);
		}
	}

	public void removeAttributeListener(DownloadAttributeListener listener, TorrentAttribute attr, int event_type) {
		String attribute = convertAttribute(attr);
		if (attribute == null) {return;}

		CopyOnWriteMap attr_map = this.getAttributeMapForType(event_type);
		CopyOnWriteList listener_list = (CopyOnWriteList)attr_map.get(attribute);
		boolean remove_self = false;

		if (listener_list != null) {
			listener_list.remove(listener);
			remove_self = listener_list.isEmpty();
		}

		if (remove_self) {
			downloadManager.getDownloadState().removeListener(this, attribute, event_type);
		}

	}

	public DownloadAnnounceResult
	getLastAnnounceResult() {
		TRTrackerAnnouncer tc = downloadManager.getTrackerClient();

		if (tc != null) {

			last_announce_result.setContent( tc.getLastResponse());
		}

		return (last_announce_result);
	}

	public DownloadScrapeResult
	getLastScrapeResult() {
		TRTrackerScraperResponse response = downloadManager.getTrackerScrapeResponse();

		if (response != null) {

				// don't notify plugins of intermediate (initializing, scraping) states as they would be picked up as errors

			if (response.getStatus() == TRTrackerScraperResponse.ST_ERROR || response.getStatus() == TRTrackerScraperResponse.ST_ONLINE) {

				last_scrape_result.setContent(response);
			}
		}

		return (last_scrape_result);
	}

	public DownloadScrapeResult
	getAggregatedScrapeResult() {
		DownloadScrapeResult	result = getAggregatedScrapeResultSupport();

		if (result != null) {

			String cache = downloadManager.getDownloadState().getAttribute(DownloadManagerState.AT_AGGREGATE_SCRAPE_CACHE);

			boolean	do_update = true;

			long	mins = SystemTime.getCurrentTime()/(1000*60);

			if (cache != null) {

				String[]	bits = cache.split(",");

				if (bits.length == 3) {

					long	updated_mins	= 0;

					try {
						updated_mins = Long.parseLong(bits[0]);

					} catch (Throwable e) {

					}

					if (mins - updated_mins < 15) {


						do_update = false;
					}
				}
			}

			if (do_update) {

				String str = mins + "," + result.getSeedCount() + "," + result.getNonSeedCount();

				downloadManager.getDownloadState().setAttribute(DownloadManagerState.AT_AGGREGATE_SCRAPE_CACHE, str);
			}
		}

		return (result);
	}

	private DownloadScrapeResult
	getAggregatedScrapeResultSupport() {
		List<TRTrackerScraperResponse> responses = downloadManager.getGoodTrackerScrapeResponses();

		int	best_peers 	= -1;
		int best_seeds	= -1;
		int	best_time	= -1;

		TRTrackerScraperResponse	best_resp	= null;

		if (responses != null) {

			for (TRTrackerScraperResponse response: responses) {

				int	peers = response.getPeers();
				int seeds = response.getSeeds();

				if (	peers > best_peers ||
						(peers == best_peers && seeds > best_seeds)) {

					best_peers	= peers;
					best_seeds	= seeds;

					best_resp = response;
				}
			}
		}

			// if no good real tracker responses then use less reliable DHT ones

		if (best_peers == -1) {

			try {
				TrackerPeerSource our_dht = null;

				List<TrackerPeerSource> peer_sources = downloadManager.getTrackerPeerSources();

				for (TrackerPeerSource ps: peer_sources) {

					if (ps.getType() == TrackerPeerSource.TP_DHT) {

						our_dht = ps;

						break;
					}
				}

				peerListenersMon.enter();

				if (announceResponseMap != null) {

					int	total_seeds = 0;
					int total_peers	= 0;
					int	latest_time	= 0;

					int	num = 0;

					if (our_dht != null && our_dht.getStatus() == TrackerPeerSource.ST_ONLINE) {

						total_seeds = our_dht.getSeedCount();
						total_peers	= our_dht.getLeecherCount();
						latest_time = our_dht.getLastUpdate();

						num = 1;
					}

					for ( int[] entry: announceResponseMap.values()) {

						num++;

						int	seeds 	= entry[0];
						int	peers 	= entry[1];
						int time	= entry[3];

						total_seeds += seeds;
						total_peers += peers;

						if (time > latest_time) {

							latest_time	= time;
						}
					}

					if (total_peers >= 0) {

						best_peers	= Math.max(1, total_peers / num);
						best_seeds	= total_seeds / num;

						if (total_seeds > 0 && best_seeds == 0) {

							best_seeds = 1;
						}
						best_time	= latest_time;
						best_resp	= null;
					}
				}

			} finally {

				peerListenersMon.exit();
			}
		}

		if (best_peers >= 0) {

			// System.out.println(download_manager.getDisplayName() + ": " + best_peers + "/" + best_seeds + "/" + best_resp);

			last_aggregate_scrape.update(best_resp, best_seeds, best_peers, best_time);

			return (last_aggregate_scrape);

		} else {

			return ( getLastScrapeResult());
		}
	}

	public void scrapeResult(
		TRTrackerScraperResponse	response) {
		// don't notify plugins of intermediate (initializing, scraping) states as they would be picked up as errors
		if (response.getStatus() != TRTrackerScraperResponse.ST_ERROR && response.getStatus() != TRTrackerScraperResponse.ST_ONLINE)
			return;

		last_scrape_result.setContent(response);

		for (int i=0;i<tracker_listeners.size();i++) {

			try {
				((DownloadTrackerListener)tracker_listeners.get(i)).scrapeResult(last_scrape_result);

			} catch (Throwable e) {

				Debug.printStackTrace(e);
			}
		}
	}

	// Used by DownloadEventNotifierImpl.
	void announceTrackerResultsToListener(DownloadTrackerListener l) {
		l.announceResult(last_announce_result);
		l.scrapeResult(last_scrape_result);
	}

	public void announceResult(
		TRTrackerAnnouncerResponse			response) {
		last_announce_result.setContent(response);

		List	tracker_listeners_ref = tracker_listeners;

		for (int i=0;i<tracker_listeners_ref.size();i++) {

			try {
				((DownloadTrackerListener)tracker_listeners_ref.get(i)).announceResult(last_announce_result);

			} catch (Throwable e) {

				Debug.printStackTrace(e);
			}
		}
	}

	public TrackerPeerSource
	getTrackerPeerSource() {
		return (
			new TrackerPeerSourceAdapter() {
				private long	fixup;
				private int		state;
				private String 	details = "";
				private int		seeds;
				private int		leechers;
				private int		peers;

				private void fixup() {
					long	now = SystemTime.getCurrentTime();

					if (now - fixup > 1000) {

						if (!downloadManager.getDownloadState().isPeerSourceEnabled( PEPeerSource.PS_PLUGIN)) {

							state = ST_DISABLED;

						} else {

							int s = getState();

							if (s == ST_DOWNLOADING || s == ST_SEEDING) {

								state = ST_ONLINE;

							} else {

								state = ST_STOPPED;
							}
						}

						if (state == ST_ONLINE) {

							try {
								peerListenersMon.enter();

								int	s	= 0;
								int	l	= 0;
								int p 	= 0;

								String	str = "";

								if (announceResponseMap != null) {

									for (Map.Entry<String, int[]> entry: announceResponseMap.entrySet()) {

										String 	cn 		= entry.getKey();
										int[]	data 	= entry.getValue();

										str += (str.length()==0?"":", ") + cn;

										str += " " + data[0] + "/" + data[1] + "/" + data[2];

										s += data[0];
										l += data[1];
										p += data[2];
									}
								}

								details 	= str;

								if (str.length() == 0) {

									seeds		= -1;
									leechers	= -1;
									peers		= -1;

								} else {

									seeds		= s;
									leechers	= l;
									peers		= p;
								}

							} finally {

								peerListenersMon.exit();
							}
						} else {

							details = "";
						}

						fixup = now;
					}
				}

				public int getType() {
					return (TP_PLUGIN);
				}

				public int getStatus() {
					fixup();

					return (state);
				}

				public String getName() {
					fixup();

					if (state == ST_ONLINE) {

						return (details);
					}

					return ("");
				}

				public int getSeedCount() {
					fixup();

					if (state == ST_ONLINE) {

						return (seeds);
					}

					return (-1);
				}

				public int getLeecherCount() {
					fixup();

					if (state == ST_ONLINE) {

						return (leechers);
					}

					return (-1);
				}

				public int getPeers() {
					fixup();

					if (state == ST_ONLINE) {

						return (peers);
					}

					return (-1);
				}
			});
	}

	private String getTrackingName(Object obj) {
		String name = obj.getClass().getName();
		int pos = name.lastIndexOf('.');
		name = name.substring(pos + 1);
		pos = name.indexOf('$');
		if (pos != -1) {
			name = name.substring(0, pos);
		}
		// hack alert - could use classloader to find plugin I guess
		pos = name.indexOf("DHTTrackerPlugin");
		if (pos == 0) {
			// built in
			name = null;
		} else if (pos > 0) {
			name = name.substring(0, pos);
		} else if (name.equals("DHTAnnounceResult")) {
			name = "mlDHT";
		}
		return (name);
	}

	public void setAnnounceResult(DownloadAnnounceResult	result) {
		
		String className = getTrackingName(result);
		if (className != null) {
			int	seeds 		= result.getSeedCount();
			int	leechers 	= result.getNonSeedCount();
			DownloadAnnounceResultPeer[] peers = result.getPeers();
			int	peerCount = peers==null?0:peers.length;
			try {
				peerListenersMon.enter();
				if (announceResponseMap == null) {
					announceResponseMap = new HashMap<String, int[]>();
				} else {
					if (announceResponseMap.size() > 32) {
						Debug.out("eh?");
						announceResponseMap.clear();
					}
				}
				int[]	data = (int[])announceResponseMap.get(className);
				if (data == null) {
					data = new int[4];
					announceResponseMap.put(className, data);
				}
				data[0]	= seeds;
				data[1]	= leechers;
				data[2]	= peerCount;
				data[3] = (int)(SystemTime.getCurrentTime()/1000);
			} finally {
				peerListenersMon.exit();
			}
		}
		downloadManager.setAnnounceResult(result);
	}

	public void setScrapeResult(
		DownloadScrapeResult	result) {
		String class_name = getTrackingName(result);

		if (class_name != null) {

			int	seeds 		= result.getSeedCount();
			int	leechers 	= result.getNonSeedCount();

			try {
				peerListenersMon.enter();

				if (announceResponseMap == null) {

					announceResponseMap = new HashMap<String, int[]>();

				} else {

					if (announceResponseMap.size() > 32) {

						Debug.out("eh?");

						announceResponseMap.clear();
					}
				}

				int[]	data = (int[])announceResponseMap.get(class_name);

				if (data == null) {

					data = new int[4];

					announceResponseMap.put(class_name, data);
				}

				data[0]	= seeds;
				data[1]	= leechers;
				data[3] = (int)(SystemTime.getCurrentTime()/1000);
			} finally {

				peerListenersMon.exit();
			}
		}

		downloadManager.setScrapeResult(result);
	}

	public void stateChanged(
		DownloadManagerState			state,
		DownloadManagerStateEvent		event) {
		final int type = event.getType();

		if (	type == DownloadManagerStateEvent.ET_ATTRIBUTE_WRITTEN ||
				type == DownloadManagerStateEvent.ET_ATTRIBUTE_WILL_BE_READ 	) {

			String	name = (String)event.getData();

			List	property_listeners_ref = property_listeners;

			final TorrentAttribute	attr = convertAttribute(name);

			if (attr != null) {

				for (int i=0;i<property_listeners_ref.size();i++) {

					try {
						((DownloadPropertyListener)property_listeners_ref.get(i)).propertyChanged(
								this,
								new DownloadPropertyEvent() {
									public int getType() {
										return ( type==DownloadManagerStateEvent.ET_ATTRIBUTE_WRITTEN
													?DownloadPropertyEvent.PT_TORRENT_ATTRIBUTE_WRITTEN
													:DownloadPropertyEvent.PT_TORRENT_ATTRIBUTE_WILL_BE_READ	);
									}

									public Object getData() {
										return (attr);
									}
								});

					} catch (Throwable e) {

						Debug.printStackTrace(e);
					}
				}
			}
		}
	}

	public void addPropertyListener(
		DownloadPropertyListener	l) {

		// Compatibility for the autostop plugin.
		if ("com.aimedia.stopseeding.core.RatioWatcher".equals(l.getClass().getName())) {

			// Looking at the source code, this method doesn't actually appear to do anything,
			// so we can avoid doing anything for now.
			return;
			/*
			if (property_to_attribute_map == null) {property_to_attribute_map = new HashMap(1);}
			DownloadAttributeListener dal = new PropertyListenerBridge(l);
			property_to_attribute_map.put(l, dal);
			this.addAttributeListener(dal, attr, event_type);
			*/
		}

		PluginDeprecation.call("property listener", l);
		try {
			tracker_listeners_mon.enter();

			List	new_property_listeners = new ArrayList(property_listeners);

			new_property_listeners.add(l);

			property_listeners	= new_property_listeners;

			if (property_listeners.size() == 1) {

				downloadManager.getDownloadState().addListener(this);
			}
		} finally {

			tracker_listeners_mon.exit();
		}
	}

	public void removePropertyListener(
		DownloadPropertyListener	l) {

		// Compatibility for the autostop plugin.
		if ("com.aimedia.stopseeding.core.RatioWatcher".equals(l.getClass().getName())) {

			// Looking at the source code, this method doesn't actually appear to do anything,
			// so we can avoid doing anything for now.
			return;
		}

		try {
			tracker_listeners_mon.enter();

			List	new_property_listeners	= new ArrayList(property_listeners);

			new_property_listeners.remove(l);

			property_listeners	= new_property_listeners;

			if (property_listeners.size() == 0) {

				downloadManager.getDownloadState().removeListener(this);
			}
		} finally {

			tracker_listeners_mon.exit();
		}
	}

	public void torrentChanged() {
		TRTrackerAnnouncer	client = downloadManager.getTrackerClient();

		if (client != null) {

			client.resetTrackerUrl(true);
		}
	}

	public void addTrackerListener(
		DownloadTrackerListener	l) {
		addTrackerListener(l, true);
	}

	public void addTrackerListener(
		DownloadTrackerListener	l,
		boolean immediateTrigger) {
		try {
			tracker_listeners_mon.enter();

			List	new_tracker_listeners = new ArrayList(tracker_listeners);

			new_tracker_listeners.add(l);

			tracker_listeners	= new_tracker_listeners;

			if (tracker_listeners.size() == 1) {

				downloadManager.addTrackerListener(this);
			}
		} finally {

			tracker_listeners_mon.exit();
		}

		if (immediateTrigger) {this.announceTrackerResultsToListener(l);}
	}

	public void removeTrackerListener(
		DownloadTrackerListener	l) {
		try {
			tracker_listeners_mon.enter();

			List	new_tracker_listeners	= new ArrayList(tracker_listeners);

			new_tracker_listeners.remove(l);

			tracker_listeners	= new_tracker_listeners;

			if (tracker_listeners.size() == 0) {

				downloadManager.removeTrackerListener(this);
			}
		} finally {

			tracker_listeners_mon.exit();
		}
	}

	public void addDownloadWillBeRemovedListener(
		DownloadWillBeRemovedListener	l) {
		try {
			removal_listeners_mon.enter();

			List	new_removal_listeners	= new ArrayList(removal_listeners);

			new_removal_listeners.add(l);

			removal_listeners	= new_removal_listeners;

		} finally {

			removal_listeners_mon.exit();
		}
	}

	public void removeDownloadWillBeRemovedListener(
		DownloadWillBeRemovedListener	l ) {
		try {
			removal_listeners_mon.enter();

			List	new_removal_listeners	= new ArrayList(removal_listeners);

			new_removal_listeners.remove(l);

			removal_listeners	= new_removal_listeners;

		} finally {

			removal_listeners_mon.exit();
		}
	}

	public void addPeerListener(final DownloadPeerListener listener) {
		
		DownloadManagerPeerListener delegate =
			new DownloadManagerPeerListener() {
				public void peerManagerAdded(PEPeerManager manager) {
					PeerManager pm = PeerManagerImpl.getPeerManager(manager);
					listener.peerManagerAdded(DownloadImpl.this, pm);
				}
				
				public void peerManagerRemoved(PEPeerManager manager) {
					PeerManager pm = PeerManagerImpl.getPeerManager(manager);
					listener.peerManagerRemoved(DownloadImpl.this, pm);
				}
				
				public void peerManagerWillBeAdded(PEPeerManager manager) {
				}
				
				public void peerAdded(PEPeer peer) {
				}
				
				public void peerRemoved(PEPeer peer) {
				}
			};
		try {
			peerListenersMon.enter();
			peerListeners.put(listener, delegate);
		} finally {
			peerListenersMon.exit();
		}
		downloadManager.addPeerListener(delegate);
	}


	public void removePeerListener(
		DownloadPeerListener	listener) {
		DownloadManagerPeerListener delegate;

		try {
			peerListenersMon.enter();

			delegate = (DownloadManagerPeerListener)peerListeners.remove(listener);

		} finally {

			peerListenersMon.exit();
		}

		if (delegate == null) {

			// sometimes we end up with double removal so don't bother spewing about this
			// Debug.out("Listener not found for removal");

		} else {

			downloadManager.removePeerListener(delegate);
		}
	}

	public boolean activateRequest(
		final int		count) {
		DownloadActivationEvent event =
			new DownloadActivationEvent() {
			public Download
			getDownload() {
				return (DownloadImpl.this);
			}

			public int getActivationCount() {
				return (count);
			}
		};

		for (Iterator it=activation_listeners.iterator();it.hasNext();) {

			try {
				DownloadActivationListener	listener = (DownloadActivationListener)it.next();

				if (listener.activationRequested( event)) {

					return (true);
				}
			} catch (Throwable e) {

				Debug.printStackTrace(e);
			}
		}

		return (false);
	}

	public DownloadActivationEvent
	getActivationState() {
		return (activationState);
	}

	public void addActivationListener(
		DownloadActivationListener		l) {
		try {
			peerListenersMon.enter();

			activation_listeners.add(l);

			if (activation_listeners.size() == 1) {

				downloadManager.addActivationListener(this);
			}
		} finally {

			peerListenersMon.exit();
		}
	}

	public void removeActivationListener(
		DownloadActivationListener		l) {
		try {
			peerListenersMon.enter();

			activation_listeners.remove(l);

			if (activation_listeners.size() == 0) {

				downloadManager.removeActivationListener(this);
			}
		} finally {

			peerListenersMon.exit();
		}
	}

	public void	addCompletionListener(DownloadCompletionListener l) {
		try {
			listeners_mon.enter();
			this.completion_listeners.add(l);
		}
		finally{
			listeners_mon.exit();
		}
	}

	public void	removeCompletionListener(DownloadCompletionListener l) {
		try {
			listeners_mon.enter();
			this.completion_listeners.remove(l);
		}
		finally{
			listeners_mon.exit();
		}
	}

 	public PeerManager
	getPeerManager()
 	{
 		PEPeerManager	pm = downloadManager.getPeerManager();

 		if (pm == null) {

 			return (null);
 		}

 		return ( PeerManagerImpl.getPeerManager( pm));
 	}

	public DiskManager
	getDiskManager() {
		PeerManager	pm = getPeerManager();

		if (pm != null) {

			return ( pm.getDiskManager());
		}

		return (null);
	}

	public int getDiskManagerFileCount() {
		return downloadManager.getNumFileInfos();
	}

	public DiskManagerFileInfo getDiskManagerFileInfo(int index) {
		org.gudy.azureus2.core3.disk.DiskManagerFileInfo[] info = downloadManager.getDiskManagerFileInfo();

		if (info == null) {
			return null;
		}
		if (index < 0 || index >= info.length) {
			return null;
		}

		return new DiskManagerFileInfoImpl(this, info[index]);
	}

	public DiskManagerFileInfo
	getPrimaryFile() {
		org.gudy.azureus2.core3.disk.DiskManagerFileInfo primaryFile = downloadManager.getDownloadState().getPrimaryFile();

		if (primaryFile == null) {
			return null;
		}
		return new DiskManagerFileInfoImpl(this, primaryFile);
	}

	public DiskManagerFileInfo[]
	getDiskManagerFileInfo() {
		org.gudy.azureus2.core3.disk.DiskManagerFileInfo[] info = downloadManager.getDiskManagerFileInfo();

		if (info == null) {

			return (new DiskManagerFileInfo[0]);
		}

		DiskManagerFileInfo[]	res = new DiskManagerFileInfo[info.length];

		for (int i=0;i<res.length;i++) {

			res[i] = new DiskManagerFileInfoImpl(this, info[i]);
		}

		return (res);
	}

 	public void setMaximumDownloadKBPerSecond(
		int		kb )
 	{
         if (kb==-1) {
            Debug.out("setMaximiumDownloadKBPerSecond got value (-1) ZERO_DOWNLOAD. (-1)"+
                "does not work through this method, use getDownloadRateLimitBytesPerSecond() instead.");
         }//if

         downloadManager.getStats().setDownloadRateLimitBytesPerSecond(kb < 0 ? 0 : kb*1024);
 	}

	public int getMaximumDownloadKBPerSecond() {
		int bps = downloadManager.getStats().getDownloadRateLimitBytesPerSecond();
		return bps <= 0 ? bps : (bps < 1024 ? 1 : bps / 1024);
	}

  	public int getUploadRateLimitBytesPerSecond() {
      return downloadManager.getStats().getUploadRateLimitBytesPerSecond();
  	}

  	public void setUploadRateLimitBytesPerSecond(int max_rate_bps) {
      downloadManager.getStats().setUploadRateLimitBytesPerSecond(max_rate_bps);
  	}

  	public int getDownloadRateLimitBytesPerSecond() {
  		return downloadManager.getStats().getDownloadRateLimitBytesPerSecond();
  	}

  	public void setDownloadRateLimitBytesPerSecond(int max_rate_bps) {
  		downloadManager.getStats().setDownloadRateLimitBytesPerSecond(max_rate_bps);
  	}

	public void addRateLimiter(
		RateLimiter		limiter,
		boolean			is_upload) {
		downloadManager.addRateLimiter(UtilitiesImpl.wrapLimiter( limiter, false ), is_upload);
	}

	public void removeRateLimiter(
		RateLimiter		limiter,
		boolean			is_upload) {
		downloadManager.removeRateLimiter(UtilitiesImpl.wrapLimiter( limiter, false ), is_upload);
	}

  public int getSeedingRank() {
    return downloadManager.getSeedingRank();
  }

	public void setSeedingRank(int rank) {
		downloadManager.setSeedingRank(rank);
	}

	public String getSavePath()
 	{
		return ( downloadManager.getSaveLocation().toString());
 	}

	public void
  	moveDataFiles(
  		File	new_parent_dir )

  		throws DownloadException
  	{
 		try {
 			downloadManager.moveDataFiles(new_parent_dir);

 		} catch (DownloadManagerException e) {

 			throw (new DownloadException("move operation failed", e));
 		}
  	}

	public void moveDataFiles(File new_parent_dir, String new_name)

  		throws DownloadException
  	{
 		try {
 			downloadManager.moveDataFiles(new_parent_dir, new_name);

 		} catch (DownloadManagerException e) {

 			throw (new DownloadException("move / rename operation failed", e));
 		}
  	}

	public void renameDownload(String new_name) throws DownloadException {
		try {downloadManager.renameDownload(new_name);}
		catch (DownloadManagerException e) {
			throw new DownloadException("rename operation failed", e);
		}
	}

  	public void
  	moveTorrentFile(
  		File	new_parent_dir )

  		throws DownloadException
 	{
		try {
 			downloadManager.moveTorrentFile(new_parent_dir);

 		} catch (DownloadManagerException e) {

 			throw (new DownloadException("move operation failed", e));
 		}
 	}

  	/**
  	 * @deprecated
  	 */
  	public File[] calculateDefaultPaths(boolean for_moving) {
  	  SaveLocationChange slc = this.calculateDefaultDownloadLocation();
	  if (slc == null) {return null;}
	  return new File[] {slc.download_location, slc.torrent_location};
  	}

  	public boolean isInDefaultSaveDir() {
  		return downloadManager.isInDefaultSaveDir();
  	}

 	public void requestTrackerAnnounce()
 	{
 		downloadManager.requestTrackerAnnounce(false);
 	}

	public void requestTrackerAnnounce(
		boolean		immediate) {
		downloadManager.requestTrackerAnnounce(immediate);
	}

	public void requestTrackerScrape(
		boolean		immediate) {
		downloadManager.requestTrackerScrape(immediate);
	}

  public byte[] getDownloadPeerId() {
    TRTrackerAnnouncer announcer = downloadManager.getTrackerClient();
    if (announcer == null) return null;
    return announcer.getPeerId();
  }


  public boolean isMessagingEnabled() {  return downloadManager.getExtendedMessagingMode() == BTHandshake.AZ_RESERVED_MODE;  }

  public void setMessagingEnabled(boolean enabled) {
	  throw new RuntimeException("setMessagingEnabled is in the process of being removed - if you are seeing this error, let the Azureus developers know that you need this method to stay!");
    //download_manager.setAZMessagingEnabled(enabled);
  }


 	// Deprecated methods

  public int getPriority() {
    return 0;
  }

  public boolean isPriorityLocked() {
    return false;
  }

  public void setPriority(int priority) {
  }

  public boolean isRemoved() {
	return ( downloadManager.isDestroyed());
  }
  // Pass LogRelation off to core objects

	/* (non-Javadoc)
	 * @see org.gudy.azureus2.core3.logging.LogRelation#getLogRelationText()
	 */
	public String getRelationText() {
		return propogatedRelationText(downloadManager);
	}

	/* (non-Javadoc)
	 * @see org.gudy.azureus2.core3.logging.LogRelation#getQueryableInterfaces()
	 */
	public Object[] getQueryableInterfaces() {
		return new Object[] { downloadManager };
	}

	private CopyOnWriteMap getAttributeMapForType(int event_type) {
		return event_type == DownloadPropertyEvent.PT_TORRENT_ATTRIBUTE_WILL_BE_READ ? read_attribute_listeners_map_cow : write_attribute_listeners_map_cow;
	}

	public boolean canMoveDataFiles() {
		return downloadManager.canMoveDataFiles();
	}

	public void attributeEventOccurred(DownloadManager download, String attribute, int event_type) {
		CopyOnWriteMap attr_listener_map = getAttributeMapForType(event_type);

		TorrentAttribute attr = convertAttribute(attribute);
		if (attr == null) {return;}

		List listeners = null;
		listeners = ((CopyOnWriteList)attr_listener_map.get(attribute)).getList();

		if (listeners == null) {return;}

		for (int i=0; i<listeners.size(); i++) {
			DownloadAttributeListener dal = (DownloadAttributeListener)listeners.get(i);
			try {dal.attributeEventOccurred(this, attr, event_type);}
			catch (Throwable t) {Debug.printStackTrace(t);}
		}
	}

	public SaveLocationChange calculateDefaultDownloadLocation() {
		return DownloadManagerMoveHandler.recalculatePath(this.downloadManager);
	}

	 public Object getUserData(Object key) {
		 return ( downloadManager.getUserData(key));
	 }

	 public void setUserData(Object key, Object data) {
		 downloadManager.setUserData(key, data);
	 }

	 public void startDownload(boolean force) {
		if (force) {
			this.setForceStart(true);
			return;
		}
		this.setForceStart(false);

		int state = this.getState();
		if (state == DownloadManager.STATE_STOPPED ||	state == DownloadManager.STATE_QUEUED) {
			downloadManager.setStateWaiting();
		}

	 }

	 public void stopDownload() {
		 if (downloadManager.getState() == DownloadManager.STATE_STOPPED) {return;}
		 downloadManager.stopIt(DownloadManager.STATE_STOPPED, false, false);
	 }

	 	// stub stuff

	 public boolean
	 isStub()
	 {
		 return (false);
	 }

	 public boolean
	 canStubbify()
	 {
		 return (manager.canStubbify( this));
	 }

	 public DownloadStub
	 stubbify()

	 	throws DownloadException, DownloadRemovalVetoException
	 {
		return (manager.stubbify( this));
	 }

	 public Download
	 destubbify()

	 	throws DownloadException
	 {
		 return (this);
	 }

	 public List<DistributedDatabase>
	 getDistributedDatabases()
	 {
		 return (DDBaseImpl.getDDBs( this));
	 }

	 public byte[]
	 getTorrentHash()
	 {
		 Torrent t = getTorrent();

		 if (t == null) {

			 return (null);
		 }

		 return ( t.getHash());
	 }


	 public long
	 getTorrentSize()
	 {
		 Torrent t = getTorrent();

		 if (t == null) {

			 return (0);
		 }

		 return ( t.getSize());
	 }

	 public DownloadStubFile[]
	 getStubFiles()
	 {
		 DiskManagerFileInfo[] dm_files = getDiskManagerFileInfo();

		 DownloadStubFile[] files = new DownloadStubFile[dm_files.length];

		 for ( int i=0;i<files.length;i++) {

			 final DiskManagerFileInfo dm_file = dm_files[i];

			 files[i] =
				new	DownloadStubFile()
			 	{
					public File
					getFile() {
						return (dm_file.getFile( true));
					}

					public long getLength() {
						if (dm_file.getDownloaded() == dm_file.getLength() && !dm_file.isSkipped()) {

							return ( dm_file.getLength());

						} else {

							return ( -dm_file.getLength());
						}
					}
			 	};
		 }

		 return (files);
	 }


	 public void changeLocation(SaveLocationChange slc) throws DownloadException {

		 // No change in the file.
		 boolean has_change = slc.hasDownloadChange() || slc.hasTorrentChange();
		 if (!has_change) {return;}

		 // Test that one of the locations is actually different.
		 has_change = slc.isDifferentDownloadLocation(new File(this.getSavePath()));
		 if (!has_change) {
			 has_change = slc.isDifferentTorrentLocation(new File(this.getTorrentFileName()));
		 }

		 if (!has_change) {return;}

		 boolean try_to_resume = !this.isPaused();
		 try {
			 try {
				 if (slc.hasDownloadChange()) {downloadManager.moveDataFiles(slc.download_location, slc.download_name);}
				 if (slc.hasTorrentChange()) {downloadManager.moveTorrentFile(slc.torrent_location, slc.torrent_name);}
			 }
			 catch (DownloadManagerException e) {
				 throw new DownloadException(e.getMessage(), e);
			 }
		 }
		 finally {
			 if (try_to_resume) {this.resume();}
		 }

	 }

	 private static class PropertyListenerBridge implements DownloadAttributeListener {
		 private DownloadPropertyListener l;
		 public PropertyListenerBridge(DownloadPropertyListener l) {this.l = l;}
		 public void attributeEventOccurred(Download d, final TorrentAttribute attr, final int event_type) {
			 l.propertyChanged(d, new DownloadPropertyEvent() {
				 public int getType() {return event_type;}
				 public Object getData() {return attr;}
			 });
		 }
	 }

	 private static class
	 AggregateScrapeResult
	 	implements DownloadScrapeResult
	 {
		private Download	dl;

		private TRTrackerScraperResponse		response;

		private int		seeds;
		private int		leechers;

		private int		time_secs;

		private AggregateScrapeResult(
			Download		_dl) {
			dl	= _dl;
		}

		private void update(
			TRTrackerScraperResponse		_response,
			int								_seeds,
			int								_peers,
			int								_time_secs) {
			response			= _response;
			seeds				= _seeds;
			leechers			= _peers;
			time_secs			= _time_secs;
		}

		public Download
		getDownload() {
			return (dl);
		}

		public int getResponseType() {
			return (RT_SUCCESS);
		}

		public int getSeedCount() {
			return (seeds);
		}

		public int getNonSeedCount() {
			return (leechers);
		}

		public long getScrapeStartTime() {
			TRTrackerScraperResponse r = response;

			if (r != null) {

				return ( r.getScrapeStartTime());
			}

			if (time_secs <= 0) {

				return (-1);

			} else {

				return (time_secs * 1000L);
			}
		}

		public void setNextScrapeStartTime(
			long nextScrapeStartTime) {
			Debug.out("Not Supported");
		}

		public long getNextScrapeStartTime() {
			TRTrackerScraperResponse r = response;

			return ( r == null?-1:r.getScrapeStartTime());
		}

		public String getStatus() {
			return ("Aggregate Scrape");
		}

		public URL
		getURL() {
			TRTrackerScraperResponse r = response;

			return ( r == null?null:r.getURL());
		}
	 }
}