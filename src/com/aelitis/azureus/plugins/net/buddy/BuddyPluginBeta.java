/*
 * Created on Oct 13, 2014
 * Created by Paul Gardner
 *
 * Copyright 2014 Azureus Software, Inc.  All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 */


package com.aelitis.azureus.plugins.net.buddy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.net.InetSocketAddress;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.util.AEDiagnostics;
import org.gudy.azureus2.core3.util.AEDiagnosticsEvidenceGenerator;
import org.gudy.azureus2.core3.util.AENetworkClassifier;
import org.gudy.azureus2.core3.util.AERunnable;
import org.gudy.azureus2.core3.util.AESemaphore;
import org.gudy.azureus2.core3.util.AsyncDispatcher;
import org.gudy.azureus2.core3.util.BDecoder;
import org.gudy.azureus2.core3.util.BEncoder;
import org.gudy.azureus2.core3.util.Base32;
import org.gudy.azureus2.core3.util.ByteArrayHashMap;
import org.gudy.azureus2.core3.util.ByteFormatter;
import org.gudy.azureus2.core3.util.Constants;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.DisplayFormatters;
import org.gudy.azureus2.core3.util.FileUtil;
import org.gudy.azureus2.core3.util.IndentWriter;
import org.gudy.azureus2.core3.util.SimpleTimer;
import org.gudy.azureus2.core3.util.SystemTime;
import org.gudy.azureus2.core3.util.TimeFormatter;
import org.gudy.azureus2.core3.util.TimerEvent;
import org.gudy.azureus2.core3.util.TimerEventPerformer;
import org.gudy.azureus2.core3.util.TimerEventPeriodic;
import org.gudy.azureus2.core3.util.UrlUtils;
import org.gudy.azureus2.core3.xml.util.XUXmlWriter;
import org.gudy.azureus2.plugins.PluginEvent;
import org.gudy.azureus2.plugins.PluginEventListener;
import org.gudy.azureus2.plugins.PluginInterface;
import org.gudy.azureus2.plugins.download.Download;
import org.gudy.azureus2.plugins.ipc.IPCException;
import org.gudy.azureus2.plugins.sharing.ShareManager;
import org.gudy.azureus2.plugins.sharing.ShareManagerListener;
import org.gudy.azureus2.plugins.sharing.ShareResource;
import org.gudy.azureus2.plugins.sharing.ShareResourceDir;
import org.gudy.azureus2.plugins.sharing.ShareResourceFile;
import org.gudy.azureus2.plugins.torrent.Torrent;
import org.gudy.azureus2.plugins.ui.config.BooleanParameter;
import org.gudy.azureus2.pluginsimpl.local.PluginCoreUtils;

import com.aelitis.azureus.core.proxy.impl.AEPluginProxyHandler;
import com.aelitis.azureus.core.tag.Tag;
import com.aelitis.azureus.core.tag.TagManagerFactory;
import com.aelitis.azureus.core.tag.TagType;
import com.aelitis.azureus.core.util.AZ3Functions;
import com.aelitis.azureus.core.util.CopyOnWriteList;
import com.aelitis.azureus.plugins.I2PHelpers;

public class
BuddyPluginBeta
{
	public static final boolean DEBUG_ENABLED			= System.getProperty("az.chat.buddy.debug", "0" ).equals("1");
	public static final boolean BETA_CHAN_ENABLED		= System.getProperty("az.chat.buddy.beta.chan", "1" ).equals("1");

	public static final String	BETA_CHAT_KEY = 	"test:beta:chat";

	public static final int PRIVATE_CHAT_DISABLED			= 1;
	public static final int PRIVATE_CHAT_PINNED_ONLY		= 2;
	public static final int PRIVATE_CHAT_ENABLED			= 3;

	private static final String	FLAGS_MSG_STATUS_KEY		= "s";
	private static final int 	FLAGS_MSG_STATUS_CHAT_NONE	= 0;		// def
	private static final int 	FLAGS_MSG_STATUS_CHAT_QUIT	= 1;

	public static final String 	FLAGS_MSG_ORIGIN_KEY 		= "o";
	public static final int 	FLAGS_MSG_ORIGIN_USER 		= 0;		// def
	public static final int 	FLAGS_MSG_ORIGIN_RATINGS 	= 1;
	public static final int 	FLAGS_MSG_ORIGIN_SEED_REQ 	= 2;
	public static final int 	FLAGS_MSG_ORIGIN_SUBS	 	= 3;

	public static final String 	FLAGS_MSG_FLASH_OVERRIDE	= "f";
	public static final int		FLAGS_MSG_FLASH_NO 			= 0;		// def
	public static final int		FLAGS_MSG_FLASH_YES 		= 1;

	public static final String 	FLAGS_MSG_TYPE_KEY			= "t";
	public static final int 	FLAGS_MSG_TYPE_NORMAL		= 0;		// def
	public static final int 	FLAGS_MSG_TYPE_ME			= 1;


	private BuddyPlugin			plugin;
	private PluginInterface		plugin_interface;
	private BooleanParameter	enabled;

	private AsyncDispatcher		dispatcher = new AsyncDispatcher("BuddyPluginBeta");

	private Map<String,ChatInstance>		chat_instances_map 	= new HashMap<String, BuddyPluginBeta.ChatInstance>();
	private CopyOnWriteList<ChatInstance>	chat_instances_list	= new CopyOnWriteList<BuddyPluginBeta.ChatInstance>();

	private PluginInterface azmsgsync_pi;

	private TimerEventPeriodic		timer;

	private String					shared_public_nickname;
	private String					shared_anon_nickname;
	private int						max_chat_ui_lines;
	private int						max_chat_ui_kb;
	private boolean					standalone_windows;
	private boolean					windows_to_sidebar;
	private boolean					hide_ratings;

	private int						private_chat_state;
	private boolean					shared_anon_endpoint;
	private boolean					sound_enabled;
	private String					sound_file;

	private Map<String,Map<String,Object>>		opts_map;

	private CopyOnWriteList<FTUXStateChangeListener>		ftux_listeners = new CopyOnWriteList<FTUXStateChangeListener>();

	private boolean	ftux_accepted = false;

	private CopyOnWriteList<ChatManagerListener>		listeners = new CopyOnWriteList<ChatManagerListener>();

	private AtomicInteger		private_chat_id = new AtomicInteger();

	private AESemaphore	init_complete = new AESemaphore("bpb:init");

	protected
	BuddyPluginBeta(
		PluginInterface		_pi,
		BuddyPlugin			_plugin,

		BooleanParameter	_enabled) {
		plugin_interface 	= _pi;
		plugin				= _plugin;
		enabled				= _enabled;

		ftux_accepted 	= COConfigurationManager.getBooleanParameter("azbuddy.dchat.ftux.accepted", false);

		shared_public_nickname 	= COConfigurationManager.getStringParameter("azbuddy.chat.shared_nick", "");
		shared_anon_nickname 	= COConfigurationManager.getStringParameter("azbuddy.chat.shared_anon_nick", "");
		private_chat_state	 	= COConfigurationManager.getIntParameter("azbuddy.chat.private_chat_state", PRIVATE_CHAT_ENABLED);

		shared_anon_endpoint	= COConfigurationManager.getBooleanParameter("azbuddy.chat.share_i2p_endpoint", true);
		sound_enabled			= COConfigurationManager.getBooleanParameter("azbuddy.chat.notif.sound.enable", false);
		sound_file			 	= COConfigurationManager.getStringParameter("azbuddy.chat.notif.sound.file", "");

		opts_map				= COConfigurationManager.getMapParameter("azbuddy.dchat.optsmap", new HashMap<String,Map<String,Object>>());	// should migrate others to use this...


			// migration starts

		Map<String,Long> favourite_map	= COConfigurationManager.getMapParameter("azbuddy.dchat.favemap", new HashMap<String,Long>());

		if (favourite_map.size() > 0) {

			migrateBooleans(favourite_map, "fave");

			COConfigurationManager.removeParameter("azbuddy.dchat.favemap");
		}

		Map<String,Long> save_messages_map		= COConfigurationManager.getMapParameter("azbuddy.dchat.savemsgmap", new HashMap<String,Long>());

		if (save_messages_map.size() > 0) {

			migrateBooleans(save_messages_map, "save");

			COConfigurationManager.removeParameter("azbuddy.dchat.savemsgmap");
		}

		Map<String,Long> log_messages_map		= COConfigurationManager.getMapParameter("azbuddy.dchat.logmsgmap", new HashMap<String,Long>());

		if (log_messages_map.size() > 0) {

			migrateBooleans(log_messages_map, "log");

			COConfigurationManager.removeParameter("azbuddy.dchat.logmsgmap");
		}

		Map<String,byte[]> lmi_map				= COConfigurationManager.getMapParameter("azbuddy.dchat.lmimap", new HashMap<String,byte[]>());

		if (lmi_map.size() > 0) {

			migrateByteArrays(lmi_map, "lmi");

			COConfigurationManager.removeParameter("azbuddy.dchat.lmimap");
		}

		// migration ends

		max_chat_ui_lines		= COConfigurationManager.getIntParameter("azbuddy.dchat.ui.max.lines", 250);
		max_chat_ui_kb			= COConfigurationManager.getIntParameter("azbuddy.dchat.ui.max.char.kb", 10);
		standalone_windows		= COConfigurationManager.getBooleanParameter("azbuddy.dchat.ui.standalone.windows", false);
		windows_to_sidebar		= COConfigurationManager.getBooleanParameter("azbuddy.dchat.ui.windows.to.sidebar", false);
		hide_ratings			= COConfigurationManager.getBooleanParameter("azbuddy.dchat.ui.hide.ratings", false);

		SimpleTimer.addPeriodicEvent(
			"BPB:checkfave",
			30*1000,
			new TimerEventPerformer() {
				public void perform(
					TimerEvent event) {
					checkFavourites();
				}
			});

		AEDiagnostics.addEvidenceGenerator(
			new AEDiagnosticsEvidenceGenerator() {
				public void generate(
					IndentWriter writer) {
					writer.println("Chat (active=" + chat_instances_list.size() + ")");

					try {
						writer.indent();

						for (ChatInstance inst: chat_instances_list) {

							writer.println("users=" + inst.getEstimatedNodes() + ", msg=" + inst.getMessageCount( true) + ", status=" + inst.getStatus());
						}
					} finally {

						writer.exdent();
					}
				}
			});
	}

	public boolean
	isAvailable() {
		return (plugin_interface.getPluginManager().getPluginInterfaceByID("azmsgsync", true ) != null);
	}

	public boolean isInitialised() {
		return ( init_complete.isReleasedForever());
	}

	public int getMaxUILines() {
		return (max_chat_ui_lines);
	}

	public void setMaxUILines(
		int		num) {
		max_chat_ui_lines		= num;

		COConfigurationManager.setParameter("azbuddy.dchat.ui.max.lines", num);

		COConfigurationManager.setDirty();
	}

	public int getMaxUICharsKB() {
		return (max_chat_ui_kb);
	}

	public void setMaxUICharsKB(
		int		num) {
		max_chat_ui_kb			= num;

		COConfigurationManager.setParameter("azbuddy.dchat.ui.max.char.kb", num);

		COConfigurationManager.setDirty();
	}

	public boolean getStandAloneWindows() {
		return (standalone_windows);
	}

	public void setStandAloneWindows(
		boolean		b) {
		standalone_windows			= b;

		COConfigurationManager.setParameter("azbuddy.dchat.ui.standalone.windows", b);

		COConfigurationManager.setDirty();
	}

	public boolean getWindowsToSidebar() {
		return (windows_to_sidebar);
	}

	public void setWindowsToSidebar(
		boolean		b) {
		windows_to_sidebar			= b;

		COConfigurationManager.setParameter("azbuddy.dchat.ui.windows.to.sidebar", b);

		COConfigurationManager.setDirty();
	}


	public boolean getHideRatings() {
		return (hide_ratings);
	}

	public void setHideRatings(
		boolean		b) {
		hide_ratings			= b;

		COConfigurationManager.setParameter("azbuddy.dchat.ui.hide.ratings", b);

		COConfigurationManager.setDirty();
	}

	public boolean getFavourite(String		net,
		String		key) {
		return (getBooleanOption( net, key, "fave", false));
	}

	public void setFavourite(String		net,
		String		key,
		boolean		b) {
		setBooleanOption(net, key, "fave", b);

		checkFavourites();
	}

	public List<String[]>
	getFavourites() {
		synchronized(opts_map) {

			List<String[]>	result = new ArrayList<String[]>();

			for ( Map.Entry<String,Map<String,Object>> entry: opts_map.entrySet()) {

				String 				net_key = entry.getKey();
				Map<String,Object>	map	= entry.getValue();

				Long	value = (Long)map.get("fave");

				if (value != null && value == 1) {

					String[] bits = net_key.split(":", 2);

					String network 	= AENetworkClassifier.internalise(bits[0]);
					String key		= decodeKey(bits[1]);

					result.add(new String[]{ network, key });
				}
			}

			return (result);
		}
	}

	private void
	checkFavourites() {
		dispatcher.dispatch(
			new AERunnable() {
				@Override
				public void runSupport() {
					try {
						List<String[]>	faves = getFavourites();

						Set<String>	set = new HashSet<String>();

						for (String[] fave: faves) {

							String	net = fave[0];
							String	key	= fave[1];

							set.add(net + ":" + key);

							ChatInstance chat = peekChatInstance(net, key, false);

							if (chat == null || !chat.getKeepAlive()) {

									// get a reference to the chat

								try {
									chat = getChat(net, key);

									chat.setKeepAlive(true);

								} catch (Throwable e) {

								}
							}
						}

						for (ChatInstance chat: chat_instances_list) {

							if (chat.getKeepAlive()) {

								String	net = chat.getNetwork();
								String	key = chat.getKey();

								if (!set.contains( net + ":" + key)) {

									if (	net == AENetworkClassifier.AT_PUBLIC &&
											key.equals(BETA_CHAT_KEY)) {

										// leave

									} else {

											// release our reference

										chat.setKeepAlive(false);

										chat.destroy();
									}
								}
							}
						}
					} finally {

						init_complete.releaseForever();
					}
				}
			});
	}

/*
 * 			String chat_key_base = "azbuddy.chat." + getNetAndKey();

			String shared_key 	= chat_key_base + ".shared";
			String nick_key 	= chat_key_base + ".nick";

			is_shared_nick 	= COConfigurationManager.getBooleanParameter(shared_key, true);
			instance_nick 	= COConfigurationManager.getStringParameter(nick_key, "");
 */

		// nick

	public String getNick(String		net,
		String		key) {
			// migrate

		String old_key = "azbuddy.chat." + net + ": " + key + ".nick";

		if (COConfigurationManager.doesParameterNonDefaultExist( old_key)) {

			String temp = COConfigurationManager.getStringParameter(old_key, "");

			COConfigurationManager.removeParameter(old_key);

			if (temp.length() > 0) {

				setNick(net, key, temp);

				return (temp);
			}
		}

		String nick = getStringOption(net, key, "nick", "");

		return (nick);
	}

	public void setNick(String		net,
		String		key,
		String		nick) {
		setStringOption(net, key, "nick", nick);
	}

		// shared nick

	private boolean
	getSharedNick(String		net,
		String		key) {
		String old_key = "azbuddy.chat." + net + ": " + key + ".shared";

		if (COConfigurationManager.doesParameterNonDefaultExist( old_key)) {

			boolean temp = COConfigurationManager.getBooleanParameter(old_key, true);

			COConfigurationManager.removeParameter(old_key);

			if (!temp) {

				setSharedNick(net, key, false);
			}

			return (temp);
		}

		return (getBooleanOption( net, key, "sn", true));
	}

	private void
	setSharedNick(String		net,
		String		key,
		boolean		b) {
		setBooleanOption(net, key, "sn", b);
	}

		// save messages

	private boolean
	getSaveMessages(String		net,
		String		key) {
		return (getBooleanOption( net, key, "save", false));
	}

	private void
	setSaveMessages(String		net,
		String		key,
		boolean		b) {
		setBooleanOption(net, key, "save", b);
	}

		// log messages

	private boolean
	getLogMessages(String		net,
		String		key) {
		return (getBooleanOption( net, key, "log", false));
	}

	private void
	setLogMessages(String		net,
		String		key,
		boolean		b) {
		setBooleanOption(net, key, "log", b);

	}

		// auto-mute

	private boolean
	getAutoMute(String		net,
		String		key) {
		return (getBooleanOption( net, key, "automute", false));
	}

	private void
	setAutoMute(String		net,
		String		key,
		boolean		b) {
		setBooleanOption(net, key, "automute", b);
	}

		// disable new msg indications indications

	private boolean
	getDisableNewMsgIndications(String		net,
		String		key) {
		return (getBooleanOption( net, key, "disnot", false));
	}

	private void
	setDisableNewMsgIndications(String		net,
		String		key,
		boolean		b) {
		setBooleanOption(net, key, "disnot", b);
	}

		// notification posting

	private boolean
	getEnableNotificationsPost(String		net,
		String		key) {
		return (getBooleanOption( net, key, "notipost", false));
	}

	private void
	setEnableNotificationsPost(String		net,
		String		key,
		boolean		b) {
		setBooleanOption(net, key, "notipost", b);
	}

		// last message info

	public String getLastMessageInfo(String		net,
		String		key) {
		return (getStringOption( net, key, "lmi", null));
	}

	public void setLastMessageInfo(String		net,
		String		key,
		String		info) {
		setStringOption(net, key, "lmi", info);
	}

		// migration

	private void
	migrateBooleans(
		Map<String,Long>		map,
		String					name) {
		for ( Map.Entry<String, Long> entry: map.entrySet()) {

			String 	net_key = entry.getKey();
			Long	value	= entry.getValue();

			if (value == 1) {

				String[] bits = net_key.split(":", 2);

				String network 	= AENetworkClassifier.internalise(bits[0]);
				String key		= bits[1];

				setBooleanOption(network, key, name, true);
			}
		}
	}

	private void
	migrateByteArrays(
		Map<String,byte[]>		map,
		String					name) {
		for ( Map.Entry<String, byte[]> entry: map.entrySet()) {

			String 	net_key = entry.getKey();
			byte[]	value	= entry.getValue();

			String[] bits = net_key.split(":", 2);

			String network 	= AENetworkClassifier.internalise(bits[0]);
			String key		= bits[1];

			setByteArrayOption(network, key, name, value);
		}
	}

	private void
	setBooleanOption(String		net,
		String		key,
		String		name,
		boolean		value) {
		setGenericOption(net, key, name, value?1L:0L);
	}

	private boolean
	getBooleanOption(String		net,
		String		key,
		String		name,
		boolean		def) {
		Object	obj = getGenericOption(net, key, name);

		if (obj instanceof Number) {

			return (((Number)obj).intValue()!=0);
		}

		return (def);
	}

	private void
	setStringOption(String		net,
		String		key,
		String		name,
		String		value) {
		try {
			setByteArrayOption(net, key, name, value.getBytes("UTF-8"));

		} catch (Throwable e) {

		}
	}

	private String
	getStringOption(String		net,
		String		key,
		String		name,
		String		def) {
		byte[]	bytes = getByteArrayOption(net, key, name);

		if (bytes != null) {

			try {
				return (new String( bytes, "UTF-8"));

			} catch (Throwable e) {

			}
		}

		return (def);
	}

	private void
	setByteArrayOption(String		net,
		String		key,
		String		name,
		byte[]		value) {
		setGenericOption(net, key, name, value);
	}

	private byte[]
	getByteArrayOption(String		net,
		String		key,
		String		name) {
		Object	obj = getGenericOption(net, key, name);

		if (obj instanceof byte[]) {

			return ((byte[])obj);
		}

		return (null);
	}

	private String
	encodeKey(String	key) {
		try {
			return (Base32.encode( key.getBytes("UTF-8")));

		} catch (Throwable e) {

			Debug.out( e);

			return ("");
		}
	}

	private String
	decodeKey(String		key) {
		try {
			return (new String( Base32.decode( key ),"UTF-8"));

		} catch (Throwable e) {

			Debug.out( e);

			return ("");
		}
	}

	private Object
	getGenericOption(String		net,
		String		key,
		String		name) {
		String net_key = net + ":" + encodeKey(key);

		synchronized(opts_map) {

			Map<String,Object>	opts = (Map<String,Object>)opts_map.get(net_key);

			if (opts == null) {

				return (null);
			}

			return (opts.get( name));
		}
	}

	private void
	setGenericOption(String		net,
		String		key,
		String		name,
		Object		value) {
		String net_key = net + ":" + encodeKey(key);

		synchronized(opts_map) {

			try {
				Map<String,Object>	opts = (Map<String,Object>)opts_map.get(net_key);

				if (opts == null) {

					opts = new HashMap<String, Object>();

					opts_map.put(net_key, opts);
				}

				opts.put(name, value);

				COConfigurationManager.setParameter("azbuddy.dchat.optsmap", opts_map);

			} catch (Throwable e) {
			}
		}

		COConfigurationManager.setDirty();
	}

	private void
	removeAllOptions(String		net,
		String		key) {
		String net_key = net + ":" + encodeKey(key);

		synchronized(opts_map) {

			try {
				Map<String,Object>	opts = (Map<String,Object>)opts_map.remove(net_key);

				if (opts == null) {

					return;
				}

				COConfigurationManager.setParameter("azbuddy.dchat.optsmap", opts_map);

			} catch (Throwable e) {
			}
		}

		COConfigurationManager.setDirty();
	}

	public String getSharedPublicNickname() {
		return (shared_public_nickname);
	}

	public void setSharedPublicNickname(String		_nick) {
		if (!_nick.equals( shared_public_nickname)) {

			shared_public_nickname	= _nick;

			COConfigurationManager.setParameter("azbuddy.chat.shared_nick", _nick);

			COConfigurationManager.setDirty();

			allUpdated();
		}
	}

	public String getSharedAnonNickname() {
		return (shared_anon_nickname);
	}

	public void setSharedAnonNickname(String		_nick) {
		if (!_nick.equals( shared_anon_nickname)) {

			shared_anon_nickname	= _nick;

			COConfigurationManager.setParameter("azbuddy.chat.shared_anon_nick", _nick);

			COConfigurationManager.setDirty();

			allUpdated();
		}
	}

	public int getPrivateChatState() {
		return (private_chat_state);
	}

	public void setPrivateChatState(
		int		state) {
		if (state !=  private_chat_state) {

			private_chat_state	= state;

			COConfigurationManager.setParameter("azbuddy.chat.private_chat_state", state);

			COConfigurationManager.setDirty();

			plugin.fireUpdated();
		}
	}

	public boolean getSharedAnonEndpoint() {
		return (shared_anon_endpoint);
	}

	public void setSharedAnonEndpoint(
		boolean		b) {
		if (b !=  shared_anon_endpoint) {

			shared_anon_endpoint	= b;

			COConfigurationManager.setParameter("azbuddy.chat.share_i2p_endpoint", b);

			COConfigurationManager.setDirty();

			plugin.fireUpdated();
		}
	}

	public void setSoundEnabled(
		boolean		b) {
		if (b !=  sound_enabled) {

			sound_enabled	= b;

			COConfigurationManager.setParameter("azbuddy.chat.notif.sound.enable", b);

			COConfigurationManager.setDirty();

			plugin.fireUpdated();
		}
	}

	public boolean getSoundEnabled() {
		return (sound_enabled);
	}

	public String getSoundFile() {
		return (sound_file);
	}

	public void setSoundFile(String		_file) {
		if (!_file.equals( sound_file)) {

			sound_file	= _file;

			COConfigurationManager.setParameter("azbuddy.chat.notif.sound.file", _file);

			COConfigurationManager.setDirty();

			plugin.fireUpdated();
		}
	}

	private void
	allUpdated() {
		for (ChatInstance chat: chat_instances_list) {

			chat.updated();
		}

		plugin.fireUpdated();
	}

	protected void
	startup() {
		plugin_interface.addEventListener(
			new PluginEventListener() {
				public void handleEvent(
					PluginEvent ev) {
					int	type = ev.getType();

					if (type == PluginEvent.PEV_INITIAL_SHARING_COMPLETE) {

						try {
							ShareManager share_manager = plugin_interface.getShareManager();

							share_manager.addListener(
								new ShareManagerListener() {

									public void resourceModified(
										ShareResource old_resource,
										ShareResource new_resource) {
										checkTag(new_resource);
									}

									public void resourceDeleted(
										ShareResource resource) {
									}

									public void resourceAdded(
										ShareResource resource) {
										checkTag(resource);
									}

									public void reportProgress(
										int percent_complete) {
									}

									public void reportCurrentTask(String task_description) {				}
								});

							ShareResource[] existing = share_manager.getShares();

							for (ShareResource sr: existing) {

								checkTag(sr);
							}
						} catch (Throwable e) {

							Debug.out(e);
						}
					} else if (type == PluginEvent.PEV_PLUGIN_OPERATIONAL) {

						pluginAdded((PluginInterface)ev.getValue());

					} else  if (type == PluginEvent.PEV_PLUGIN_NOT_OPERATIONAL) {

						pluginRemoved((PluginInterface)ev.getValue());
					}
				}
			});

		PluginInterface[] plugins = plugin_interface.getPluginManager().getPlugins(true);

		for (PluginInterface pi: plugins) {

			if (pi.getPluginState().isOperational()) {

				pluginAdded(pi);
			}
		}
	}

	private void
	checkTag(
		ShareResource		resource) {
		Map<String,String>	properties = resource.getProperties();

		if (properties != null) {

			String ud = properties.get(ShareManager.PR_USER_DATA);

			if (ud != null && ud.equals("buddyplugin:share")) {

				try {

					Torrent torrent = null;

					if (resource instanceof ShareResourceFile) {

						torrent = ((ShareResourceFile)resource).getItem().getTorrent();

					} else if (resource instanceof ShareResourceDir) {

						torrent = ((ShareResourceDir)resource).getItem().getTorrent();
					}

					if (torrent != null) {

						Download download = plugin_interface.getPluginManager().getDefaultPluginInterface().getShortCuts().getDownload( torrent.getHash());

						if (download != null) {

							tagDownload(download);
						}
					}
				} catch (Throwable e) {

				}
			}
		}
	}

	public void tagDownload(
		Download	download) {
		try {
			TagType tt = TagManagerFactory.getTagManager().getTagType(TagType.TT_DOWNLOAD_MANUAL);

			Tag tag = tt.getTag("tag.azbuddy.dchat.shares", false);

			if (tag == null) {

				tag = tt.createTag("tag.azbuddy.dchat.shares", true);

				tag.setCanBePublic(false);

				tag.setPublic(false);
			}

			tag.addTaggable(PluginCoreUtils.unwrap( download));

		} catch (Throwable e) {

			Debug.out(e);
		}
	}

	protected void
	closedown() {

	}

	private void
	pluginAdded(
		final PluginInterface	pi) {
		if (pi.getPluginID().equals("azmsgsync")) {

			synchronized(chat_instances_map) {

				azmsgsync_pi = pi;

				Iterator<ChatInstance>	it = chat_instances_map.values().iterator();

				while (it.hasNext()) {

					ChatInstance inst = it.next();

					try {
						inst.bind(azmsgsync_pi, null);

					} catch (Throwable e) {

						Debug.out(e);

						it.remove();
					}
				}
			}

			dispatcher.dispatch(
				new AERunnable() {

					@Override
					public void runSupport() {
						try {
							if (Constants.isCVSVersion() && enabled.getValue()) {

								if (BETA_CHAN_ENABLED) {

									ChatInstance chat = getChat(AENetworkClassifier.AT_PUBLIC, BETA_CHAT_KEY);

									chat.setKeepAlive(true);
								}
							}
						} catch (Throwable e) {

							// Debug.out(e);
						}
					}
				});

		}
	}

	private void
	pluginRemoved(
		PluginInterface	pi) {
		if (pi.getPluginID().equals("azmsgsync")) {

			synchronized(chat_instances_map) {

				azmsgsync_pi = null;

				Iterator<ChatInstance>	it = chat_instances_map.values().iterator();

				while (it.hasNext()) {

					ChatInstance inst = it.next();

					inst.unbind();

					if (inst.isPrivateChat()) {

						it.remove();
					}
				}
			}
		}
	}

	public boolean isI2PAvailable() {
		return (AEPluginProxyHandler.hasPluginProxyForNetwork( AENetworkClassifier.AT_I2P, false));
	}

	public InputStream handleURI(String		url_str,
		boolean		open_only )

		throws Exception
	{

			// url_str will be something like chat:anon?Test%20Me[&a=b]...
			// should really be chat:anon?key=Test%20Me but we'll support the shorthand

			// azplug:?id=azbuddy&arg=chat%3A%3FTest%2520Me%26format%3Drss
			// azplug:?id=azbuddy&arg=chat%3A%3Fkey%3DTest%2520Me%26format%3Drss

		int	pos = url_str.indexOf('?');

		String protocol;
		String key 		= null;
		String format	= null;

		if (pos != -1) {

			protocol = url_str.substring(0, pos ).toLowerCase( Locale.US);

			String args = url_str.substring(pos+1);

			String[] bits = args.split("&");

			for (String bit: bits) {

				String[] temp = bit.split("=");

				if (temp.length == 1) {

					key = UrlUtils.decode(temp[0]);

				} else {

					String lhs = temp[0].toLowerCase(Locale.US);
					String rhs = UrlUtils.decode(temp[1]);

					if (lhs.equals("key")) {

						key = rhs;

					} else if (lhs.equals("format")) {

						format	= rhs;
					}
				}
			}

		} else {

			throw (new Exception("Malformed request"));
		}

		if (key == null) {

			throw (new Exception("Key missing"));
		}

		if (open_only) {

			format = null;
		}

		String network;

		if (protocol.startsWith("chat:anon")) {

			if (!isI2PAvailable()) {

				boolean[] result = { false };

				I2PHelpers.installI2PHelper(
						MessageText.getString("azbuddy.dchat.anon.requested"),
						"azbuddy.dchat.uri.based.i2p.install", result,
						new Runnable() {
							public void run() {
							}
						});

				throw (new Exception("I2P unavailable"));
			}

			network = AENetworkClassifier.AT_I2P;

		} else if (protocol.startsWith("chat")) {

			network = AENetworkClassifier.AT_PUBLIC;

		} else {

			throw (new Exception("Invalid protocol: " + protocol));
		}

		if (format == null || !format.equalsIgnoreCase("rss")) {

			BuddyPluginViewInterface ui = plugin.getSWTUI();

			if (ui == null) {

				throw (new Exception("UI unavailable"));
			}

			ChatInstance chat = getChat( network, key);

			ui.openChat(chat);

			return (null);

		} else {

			ChatInstance chat = peekChatInstance(network, key, true);

			if (chat == null) {

				throw (new Exception("Chat unavailable"));
			}

				// we need this chat to hang around

			if (!chat.isFavourite()) {

				chat.setFavourite(true);

				chat.setKeepAlive(true);
			}

			if (!chat.getSaveMessages()) {

				chat.setSaveMessages(true);
			}

			List<ChatMessage> messages = chat.getMessages();

			ByteArrayOutputStream baos = new ByteArrayOutputStream(10*1024);

			PrintWriter pw = new PrintWriter(new OutputStreamWriter( baos, "UTF-8"));

			pw.println("<?xml version=\"1.0\" encoding=\"utf-8\"?>");

			pw.println("<rss version=\"2.0\" xmlns:vuze=\"http://www.vuze.com\">");

			pw.println("<channel>");

			pw.println("<title>" + escape( chat.getName()) + "</title>");

			long	last_modified;

			if (messages.size() == 0) {

				last_modified = SystemTime.getCurrentTime();

			} else {

				last_modified = messages.get( messages.size()-1).getTimeStamp();
			}

			pw.println(	"<pubDate>" + TimeFormatter.getHTTPDate(last_modified ) + "</pubDate>");

			for (ChatMessage message: messages) {

				List<Map<String,Object>>	message_links = extractLinks( message.getMessage());

				if (message_links.size() == 0) {

					continue;
				}

				String item_date = TimeFormatter.getHTTPDate( message.getTimeStamp());

				for (Map<String,Object> message_link: message_links) {

					if (message_link.containsKey("magnet")) {

						Map<String,Object> magnet = message_link;

						String	hash 	= (String)magnet.get("hash");

						if (hash == null) {

							continue;
						}

						String	title 	= (String)magnet.get("title");

						if (title == null) {

							title = hash;
						}

						String	link	= (String)magnet.get("link");

						if (link == null) {

							link = (String)magnet.get("magnet");
						}

						pw.println("<item>");

						pw.println("<title>" + escape( title ) + "</title>");

						pw.println("<guid>" + hash + "</guid>");

						String	cdp	= (String)magnet.get("cdp");

						if (cdp != null) {

							pw.println("<link>" + escape( cdp ) + "</link>");
						}

						Long	size 		= (Long)magnet.get("size");
						Long	seeds 		= (Long)magnet.get("seeds");
						Long	leechers 	= (Long)magnet.get("leechers");
						Long	date	 	= (Long)magnet.get("date");

						String enclosure =
								"<enclosure " +
									"type=\"application/x-bittorrent\" " +
									"url=\"" + escape(link) + "\"";

						if (size != null) {

							enclosure += " length=\"" + size + "\"";
						}

						enclosure += " />";

						pw.println(enclosure);

						String date_str = (date==null||date<=0)?item_date:TimeFormatter.getHTTPDate(date);

						pw.println(	"<pubDate>" + date_str + "</pubDate>");


						if (size != null) {

							pw.println(	"<vuze:size>" + size + "</vuze:size>");
						}

						if (seeds != null) {

							pw.println(	"<vuze:seeds>" + seeds + "</vuze:seeds>");
						}

						if (leechers != null) {

							pw.println(	"<vuze:peers>" + leechers + "</vuze:peers>");
						}

						pw.println(	"<vuze:assethash>" + hash + "</vuze:assethash>");

						pw.println("<vuze:downloadurl>" + escape( link ) + "</vuze:downloadurl>");

						pw.println("</item>");

					} else {

						String	title 	= (String)message_link.get("title");
						String 	link	= (String)message_link.get("link");

						pw.println("<item>");

						pw.println("<title>" + escape( title ) + "</title>");

						pw.println("<guid>" + escape( link ) + "</guid>");

						pw.println("<link>" + escape( link ) + "</link>");

						pw.println(	"<pubDate>" + item_date + "</pubDate>");

						pw.println(	"<vuze:rank></vuze:rank>");

						String enclosure =
								"<enclosure " +
									"type=\"application/x-bittorrent\" " +
									"url=\"" + escape(link) + "\"";


						enclosure += " />";

						pw.println(enclosure);

						pw.println("</item>");

					}
				}
			}

			pw.println("</channel>");

			pw.println("</rss>");

			pw.flush();

			return (new ByteArrayInputStream( baos.toByteArray()));
		}
	}

	private List<Map<String,Object>>
	extractLinks(String		str) {
		List<Map<String,Object>> result = new ArrayList<Map<String,Object>>();

		int	len = str.length();

		String	lc_str = str.toLowerCase(Locale.US);

		int	pos = 0;

		while (pos < len) {

			pos = lc_str.indexOf("magnet:", pos);

			int	type = -1;

			if (pos != -1) {

				type = 0;

			} else {

				String[] protocols = { "azplug:", "chat:" };

				for (String p: protocols) {

					pos = lc_str.indexOf(p, pos);

					if (pos != -1) {

						type = 1;

						break;
					}
				}

				if (type == -1) {

					break;
				}
			}

			int	start = pos;

			while (pos < len) {

				char c = str.charAt(pos);

				if (Character.isWhitespace(c ) || ( c == '"' && start > 0 && lc_str.charAt( start-1) == '"')) {

					break;

				} else {

					pos++;
				}
			}

			String link = str.substring(start, pos);

			if (type == 0) {

				String magnet = link;

				int x = magnet.indexOf('?');

				if (x != -1) {

					Map<String,Object> map = new HashMap<String,Object>();

						// remove any trailing ui name hack

					int	p1 = magnet.lastIndexOf("[[");

					if (p1 != -1 && magnet.endsWith("]]")) {

						magnet = magnet.substring(0, p1);
					}

					map.put("magnet", magnet);

					List<String>	trackers = new ArrayList<String>();

					map.put("trackers", trackers);

					String[] bits = magnet.substring(x+1 ).split("&");

					for (String bit: bits) {

						String[] temp = bit.split("=");

						if (temp.length == 2) {

							try {

								String	lhs = temp[0].toLowerCase(Locale.US);
								String	rhs = UrlUtils.decode(temp[1]);

								if (lhs.equals("xt")) {

									String lc_rhs = rhs.toLowerCase(Locale.US);

									int p = lc_rhs.indexOf("btih:");

									if (p >= 0) {

										map.put("hash", lc_rhs.substring( p+5 ).toUpperCase( Locale.US));
									}

								} else if (lhs.equals("dn")) {

									map.put("title", rhs);

								} else if (lhs.equals("tr")) {

									trackers.add(rhs);

								} else if (lhs.equals("fl")) {

									map.put("link", rhs);

								} else if (lhs.equals("xl")) {

									long size = Long.parseLong(rhs);

									map.put("size", size);

								} else if (lhs.equals("_d")) {

									long date = Long.parseLong(rhs);

									map.put("date", date);

								} else if (lhs.equals("_s")) {

									long seeds = Long.parseLong(rhs);

									map.put("seeds", seeds);

								} else if (lhs.equals("_l")) {

									long leechers = Long.parseLong(rhs);

									map.put("leechers", leechers);

								} else if (lhs.equals("_c")) {

									map.put("cdp", rhs);
								}
							} catch (Throwable e) {

							}

						}
					}

					//System.out.println(magnet + " -> " + map);

					result.add(map);
				}
			} else {

				Map<String,Object> map = new HashMap<String,Object>();

					// remove any trailing ui name hack

				int	p1 = link.lastIndexOf("[[");

				if (p1 != -1 && link.endsWith("]]")) {

					String title = UrlUtils.decode(link.substring( p1+2, link.length() - 2));

					map.put("title", title);

					link = link.substring(0, p1);

					map.put("link", link);

					result.add(map);
				}
			}
		}

		return (result);
	}

	private String
	escape(String	str) {
		return ( XUXmlWriter.escapeXML(str));
	}

	public boolean getFTUXAccepted() {
		return (ftux_accepted);
	}

	public void setFTUXAccepted(
		boolean	accepted) {
		ftux_accepted = accepted;

		COConfigurationManager.setParameter("azbuddy.dchat.ftux.accepted", true);

		COConfigurationManager.save();

		for (FTUXStateChangeListener l: ftux_listeners) {

			l.stateChanged(accepted);
		}
	}

	public void addFTUXStateChangeListener(
		FTUXStateChangeListener		listener) {
		ftux_listeners.add(listener);

		listener.stateChanged(ftux_accepted);
	}

	public void removeFTUXStateChangeListener(
		FTUXStateChangeListener		listener) {
		ftux_listeners.remove(listener);
	}

	private void
	logMessage(
		ChatInstance		chat,
		ChatMessage			message) {
		File log_dir = AEDiagnostics.getLogDir();

		log_dir = new File(log_dir, "chat");

		if (!log_dir.exists()) {

			log_dir.mkdir();
		}

		File log_file = new File(log_dir, FileUtil.convertOSSpecificChars( chat.getName(), false ) + ".log");

		PrintWriter	pw = null;

		try {

			pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream( log_file, true ), "UTF-8"));

			SimpleDateFormat time_format 	= new SimpleDateFormat("yyyy/MM/dd HH:mm");

			String msg = "[" + time_format.format(new Date( message.getTimeStamp())) + "]";

			msg += " <" + message.getParticipant().getName(true) + "> " + message.getMessage();

			pw.println(msg);

		} catch (Throwable e) {

		} finally {

			if (pw != null) {

				pw.close();
			}
		}
	}

	public void getAndShowChat(String		network,
		String		key )

		throws Exception
	{
		BuddyPluginViewInterface ui = plugin.getSWTUI();

		if (ui == null) {

			throw (new Exception("UI unavailable"));
		}

		ChatInstance chat = getChat( network, key) ;

		ui.openChat(chat);
	}

	public void showChat(
		ChatInstance	inst )

		throws Exception
	{
		BuddyPluginViewInterface ui = plugin.getSWTUI();

		if (ui == null) {

			throw (new Exception("UI unavailable"));
		}

		ui.openChat(inst);
	}

	private String
	pkToString(
		byte[]		pk) {
		byte[] temp = new byte[3];

		if (pk != null) {

			System.arraycopy(pk, 8, temp, 0, 3);
		}

		return (ByteFormatter.encodeString( temp));
	}

	public ChatInstance importChat(String		import_data )

		throws Exception
	{
		if (azmsgsync_pi == null) {

			throw (new Exception("Plugin unavailable "));
		}

		Map<String,Object>		options = new HashMap<String, Object>();

		options.put("import_data", import_data.getBytes("UTF-8"));

		Map<String,Object> reply = (Map<String,Object>)azmsgsync_pi.getIPC().invoke("importMessageHandler", new Object[]{ options });

		String	key			= new String((byte[])reply.get("key" ), "UTF-8");
		String	network	 	= (String)reply.get("network");
		Object	handler 	= reply.get("handler");

		return (getChat( network, key, null, handler, false, null));
	}

	public ChatInstance getChat(
		Download		download) {
		String	key = BuddyPluginUtils.getChatKey(download);

		if (key != null) {

			String[] networks = PluginCoreUtils.unwrap(download).getDownloadState().getNetworks();

			boolean	has_i2p = false;

			for (String net: networks) {

				if (net == AENetworkClassifier.AT_PUBLIC) {

					try {
						ChatInstance inst = getChat(net, key);

						return (inst);

					} catch (Throwable e) {

					}
				} else if (net == AENetworkClassifier.AT_I2P) {

					has_i2p = true;
				}
			}

			if (has_i2p) {

				try {
					ChatInstance inst = getChat(AENetworkClassifier.AT_I2P, key);

					return (inst);

				} catch (Throwable e) {

				}
			}
		}

		return (null);
	}

	public ChatInstance getChat(String			network,
		String			key )

		throws Exception
	{
		return (getChat( network, key, null, null, false, null));
	}

	public ChatInstance getChat(String					network,
		String					key,
		Map<String,Object>		options )

		throws Exception
	{
		return (getChat( network, key, null, null, false, options));
	}

	public ChatInstance getChat(
		ChatParticipant		participant )

		throws Exception
	{
		String key = participant.getChat().getKey() + " - " + participant.getName() + " (outgoing)[" + private_chat_id.getAndIncrement() + "]";

		return (getChat( participant.getChat().getNetwork(), key, participant, null, true, null));
	}

	public ChatInstance getChat(
		ChatParticipant	parent_participant,
		Object			handler )

		throws Exception
	{
		String key = parent_participant.getChat().getKey() + " - " + parent_participant.getName() + " (incoming)[" + private_chat_id.getAndIncrement() + "]";

		return (getChat( parent_participant.getChat().getNetwork(), key, null, handler, true, null));
	}

	private ChatInstance
	getChat(String				network,
		String				key,
		ChatParticipant		private_target,
		Object				handler,
		boolean				is_private_chat,
		Map<String,Object>	options )

		throws Exception
	{
		if (!enabled.getValue()) {

			throw (new Exception("Plugin not enabled"));
		}

		String meta_key = network + ":" + key;

		ChatInstance 	result;

		ChatInstance	added = null;

		synchronized(chat_instances_map) {

			result = chat_instances_map.get(meta_key);

			if (result == null) {

				result = new ChatInstance(network, key, private_target, is_private_chat, options);

				chat_instances_map.put(meta_key, result);

				chat_instances_list.add(result);

				added = result;

				if (azmsgsync_pi != null) {

					try {
						result.bind(azmsgsync_pi, handler);

					} catch (Throwable e) {

						chat_instances_map.remove(meta_key);

						chat_instances_list.remove(result);

						added = null;

						result.destroy();

						if (e instanceof Exception) {

							throw ((Exception)e);
						}

						throw (new Exception( e));
					}
				}
			} else {

				result.addReference();
			}

			if (timer == null) {

				timer =
					SimpleTimer.addPeriodicEvent(
						"BPB:timer",
						2500,
						new TimerEventPerformer() {

							public void perform(
								TimerEvent event) {
								for (ChatInstance inst: chat_instances_list) {

									inst.update();
								}
							}
						});
			}
		}

		if (added != null) {

			for (ChatManagerListener l: BuddyPluginBeta.this.listeners) {

				try {
					l.chatAdded(added);

				} catch (Throwable e) {

					Debug.out(e);
				}
			}
		}

		return (result);
	}


	public ChatInstance peekChatInstance(String				network,
		String				key) {
		return (peekChatInstance( network, key, false));
	}

	public ChatInstance peekChatInstance(
		Download		download) {
		String	key = BuddyPluginUtils.getChatKey(download);

		if (key != null) {

			String[] networks = PluginCoreUtils.unwrap(download).getDownloadState().getNetworks();

			boolean	has_i2p = false;

			for (String net: networks) {

				if (net == AENetworkClassifier.AT_PUBLIC) {

					try {
						return (peekChatInstance( net, key));

					} catch (Throwable e) {

					}
				} else if (net == AENetworkClassifier.AT_I2P) {

					has_i2p = true;
				}
			}

			if (has_i2p) {

				try {
					return (peekChatInstance( AENetworkClassifier.AT_I2P, key));

				} catch (Throwable e) {

				}
			}
		}

		return (null);
	}

		/**
		 * returns existing chat if found without adding a reference to it. If create_if_missing supplied
		 * then this will create a new chat (and add a reference to it) so use this parameter with
		 * caution
		 */

	public ChatInstance peekChatInstance(String				network,
		String				key,
		boolean				create_if_missing) {
		String meta_key = network + ":" + key;

		synchronized(chat_instances_map) {

			ChatInstance inst = chat_instances_map.get(meta_key);

			if (inst == null && create_if_missing) {

				try {
					inst = getChat(network, key);

				} catch (Throwable e) {

				}
			}

			return (inst);
		}
	}

	private static final Object	DOWNLOAD_PEEK_CACHE_KEY = new Object();

	private static AsyncDispatcher dl_peek_dispatcher = new AsyncDispatcher("dl:peeker");

	public Map<String,Object>
	peekChat(
		final Download		download,
		boolean				async) {
		String	key = BuddyPluginUtils.getChatKey(download);

		if (key != null) {

			if (async) {

				synchronized(DOWNLOAD_PEEK_CACHE_KEY) {

					Map<String,Object> map = (Map<String,Object>)download.getUserData(DOWNLOAD_PEEK_CACHE_KEY);

					if (map != null) {

							// TODO: could timeout?

						return (map);
					}

					if (dl_peek_dispatcher.getQueueSize() > 200) {

							// we'll get back here sometime

						return (null);
					}

					map = new HashMap<String, Object>();

					download.setUserData(DOWNLOAD_PEEK_CACHE_KEY, map);

					dl_peek_dispatcher.dispatch(
						new AERunnable() {

							@Override
							public void runSupport() {
								try {
									Map<String,Object> map = peekChat(download, false);

									if (map != null && map.isEmpty()) {

										map = null;
									}

									if (map == null) {

											// nost likely things not initialised

										try {
											Thread.sleep(1000);

										} catch (Throwable e) {
										}
									} else {

										if (!map.containsKey("m")) {

											map.put("m", 0);
										}
									}

									synchronized(DOWNLOAD_PEEK_CACHE_KEY) {

										download.setUserData(DOWNLOAD_PEEK_CACHE_KEY, map);
									}
								} catch (Throwable e) {

								}
							}
						});
				}
			} else {

				String[] networks = PluginCoreUtils.unwrap(download).getDownloadState().getNetworks();

				boolean	has_i2p = false;

				for (String net: networks) {

					if (net == AENetworkClassifier.AT_PUBLIC) {

						try {
							return (peekChat( net, key));

						} catch (Throwable e) {

						}
					} else if (net == AENetworkClassifier.AT_I2P) {

						has_i2p = true;
					}
				}

				if (has_i2p) {

					try {
						return (peekChat( AENetworkClassifier.AT_I2P, key));

					} catch (Throwable e) {

					}
				}
			}
		}

		return (null);
	}

	public Map<String,Object>
	peekChat(String				network,
		String				key) {
		Map<String,Object>		reply = new HashMap<String, Object>();

		try {
			PluginInterface pi;

			synchronized(chat_instances_map) {

				pi = azmsgsync_pi;
			}

			if (pi != null) {

				Map<String,Object>		options = new HashMap<String, Object>();

				options.put("network", network);
				options.put("key", key.getBytes("UTF-8"));

				options.put("timeout", 60*1000);

				if (network != AENetworkClassifier.AT_PUBLIC) {

					options.put("server_id", getSharedAnonEndpoint()?"dchat_shared":"dchat");
				}

				reply = (Map<String,Object>)pi.getIPC().invoke("peekMessageHandler", new Object[]{ options });
			}

		} catch (Throwable e) {

			Debug.out(e);
		}

		return (reply);
	}

	public List<ChatInstance>
	getChats() {
		return ( chat_instances_list.getList());
	}

	private void
	addChatActivity(
		ChatInstance		inst,
		ChatMessage			message) {
		if (inst.getEnableNotificationsPost()) {

			if (message != null) {

				AZ3Functions.provider provider = AZ3Functions.getProvider();

				if (provider == null) {

					return;
				}

				BuddyPluginViewInterface ui = plugin.getSWTUI();

				String	str;

				if (ui != null) {

					str = ui.renderMessage(inst, message);

				} else {

					str = message.getMessage();
				}

				String chan_name = inst.getName(true);

				int pos = chan_name.lastIndexOf('[');

				if (pos != -1 && chan_name.endsWith("]")) {

					chan_name = chan_name.substring(0, pos);
				}

				str = chan_name + ": " + str;

				Map<String,String>	cb_data = new HashMap<String, String>();

				cb_data.put("allowReAdd", "true");
				cb_data.put("net", inst.getNetwork());
				cb_data.put("key", inst.getKey());

				provider.addLocalActivity(
					inst.getNetAndKey(),
					"image.sidebar.chat-overview",
					str,
					new String[]{ MessageText.getString("label.view")},
					ActivityCallback.class,
					cb_data);
			}
		}
	}

	public static class
	ActivityCallback
		implements AZ3Functions.provider.LocalActivityCallback
	{
		public void actionSelected(String action, Map<String, String> data) {
			String	net = data.get("net");
			String	key = data.get("key");

			if (net != null && key != null) {

				BuddyPluginUtils.createBetaChat(net, key, null);
			}
		}
	}

	public void addListener(
		ChatManagerListener		l,
		boolean					fire_for_existing) {
		listeners.add(l);

		if (fire_for_existing) {

			for (ChatInstance inst: chat_instances_list) {

				l.chatAdded(inst);
			}
		}
	}

	public void removeListener(
		ChatManagerListener		l) {
		listeners.remove(l);
	}

	private static Pattern auto_dup_pattern1 = Pattern.compile("File '(.*?)' is");
	private static Pattern auto_dup_pattern2 = Pattern.compile(":([a-zA-Z2-7]{32})", Pattern.CASE_INSENSITIVE);
	private static Pattern auto_dup_pattern3 = Pattern.compile("See (http://wiki.vuze.com/w/Swarm_Merging)");

	private static Pattern[] auto_dup_patterns = { auto_dup_pattern1, auto_dup_pattern2, auto_dup_pattern3 };

	public class ChatInstance
	{
		public static final String	OPT_INVISIBLE		= "invisible";		// Boolean

		private static final int	MSG_HISTORY_MAX	= 512;

		private final String		network;
		private final String		key;

		private boolean				is_private_chat;
		private boolean				is_invisible_chat;

		private final ChatParticipant		private_target;

		private Object		binding_lock = new Object();
		private AESemaphore	binding_sem;

		private volatile PluginInterface		msgsync_pi;
		private volatile Object					handler;

		private byte[]							my_public_key;
		private byte[]							managing_public_key;
		private boolean							read_only;
		private int								ipc_version;

		private InetSocketAddress				my_address;

		private Object	chat_lock = this;

		private AtomicInteger						message_uid_next = new AtomicInteger();

		private List<ChatMessage>					messages	= new ArrayList<ChatMessage>();
		private ByteArrayHashMap<String>			message_ids = new ByteArrayHashMap<String>();
		private int									messages_not_mine_count;

		private ByteArrayHashMap<ChatParticipant>	participants = new ByteArrayHashMap<ChatParticipant>();

		private Map<String,List<ChatParticipant>>	nick_clash_map = new HashMap<String, List<ChatParticipant>>();

		private CopyOnWriteList<ChatListener>		listeners = new CopyOnWriteList<ChatListener>();

		private Map<Object,Object>					user_data = new HashMap<Object, Object>();

		private LinkedHashMap<String,String>							auto_dup_set =
			new LinkedHashMap<String,String>(500,0.75f,true) {
				protected boolean
				removeEldestEntry(
					Map.Entry<String,String> eldest) {
					return size() > 500;
				}
			};

		private boolean		keep_alive;
		private boolean		have_interest;

		private Map<String,Object> 	status;

		private boolean		is_shared_nick;
		private String		instance_nick;

		private int			reference_count;

		private ChatMessage		last_message_requiring_attention;
		private boolean			message_outstanding;

		private boolean		is_favourite;
		private boolean		auto_notify;

		private boolean		save_messages;
		private boolean		log_messages;
		private boolean		auto_mute;
		private boolean 	enable_notification_posts;
		private boolean		disable_new_msg_indications;

		private boolean		destroyed;

		private
		ChatInstance(String				_network,
			String				_key,
			ChatParticipant		_private_target,
			boolean				_is_private_chat,
			Map<String,Object>	_options) {
			network 		= _network;
			key				= _key;

				// private chat args

			private_target	= _private_target;
			is_private_chat = _is_private_chat;

			is_shared_nick 	= getSharedNick(network, key);
			instance_nick 	= getNick(network, key);

			if (!is_private_chat) {

				is_favourite 				= getFavourite(network, key);
				save_messages 				= BuddyPluginBeta.this.getSaveMessages(network, key);
				log_messages 				= BuddyPluginBeta.this.getLogMessages(network, key);
				auto_mute 					= BuddyPluginBeta.this.getAutoMute(network, key);
				disable_new_msg_indications = BuddyPluginBeta.this.getDisableNewMsgIndications(network, key);
			}

			enable_notification_posts = BuddyPluginBeta.this.getEnableNotificationsPost(network, key);

			if (_options != null) {

				Boolean	invis = (Boolean)_options.get(OPT_INVISIBLE);

				if (invis != null && invis) {

					is_invisible_chat = true;
				}
			}

			addReference();
		}

		public ChatInstance
		getClone()

			throws Exception
		{
			if (is_private_chat) {

				addReference();

				return (this);

			} else {

					// can probably just do the above...

				return (BuddyPluginBeta.this.getChat( network, key));
			}
		}

		protected void
		addReference() {
			synchronized(chat_lock) {

				reference_count++;

				// Debug.out(getName() + ": added ref -> " + reference_count);
			}
		}

		public String
		getName() {
			return (getName( false));
		}

		public String
		getName(
			boolean	abbreviated) {
			String str = key;

			int pos = str.lastIndexOf('[');

			if (pos != -1 && str.endsWith("]")) {

				String temp = str.substring(pos+1, str.length()-1);

				if (temp.contains("pk=")) {

					str = str.substring(0, pos);

					if (temp.contains("ro=1")) {

						str += "[R]";
					} else {

						str += "[M]";
					}
				} else {

					str = str.substring(0, pos);
				}
			}

			if (abbreviated) {

				return ( MessageText.getString(
						network==AENetworkClassifier.AT_PUBLIC?"label.public.medium":"label.anon.medium") +
						" - '" + str + "'");

			} else {

				return (
					MessageText.getString(
						network==AENetworkClassifier.AT_PUBLIC?"label.public":"label.anon") +
						" - '" + str + "'");
			}
		}

		public String
		getShortName() {

			String	short_name = getName();

			if (short_name.length() > 60) {

				short_name = short_name.substring(0, 60) + "...";
			}

			return (short_name);
		}

		public String
		getNetwork() {
			return (network);
		}

		public String
		getKey() {
			return (key);
		}

		public boolean
		isFavourite() {
			return (is_favourite);
		}

		public void
		setAutoNotify(
			boolean		b) {
			auto_notify	= b;
		}

		public boolean
		isAutoNotify() {
			return (auto_notify);
		}

		public boolean
		isInteresting() {
			return (have_interest);
		}

		public void
		setInteresting(
			boolean	b) {
			have_interest = b;
		}

		public boolean
		isStatistics() {
			return (key.startsWith("Statistics:"));
		}

		public void
		setFavourite(
			boolean		b) {
			if (!is_private_chat) {

				if (b != is_favourite) {

					is_favourite = b;

					BuddyPluginBeta.this.setFavourite(network, key, b);
				}
			}
		}

		public boolean
		getSaveMessages() {
			return (save_messages);
		}

		public void
		setSaveMessages(
			boolean		b) {
			if (!is_private_chat) {

				if (b != save_messages) {

					save_messages = b;

					BuddyPluginBeta.this.setSaveMessages(network, key, b);

					Map<String,Object>	options = new HashMap<String, Object>();

					options.put("save_messages", b);

					try {
						updateOptions(options);

					} catch (Throwable e) {

						Debug.out(e);
					}
				}
			}
		}

		public boolean
		getLogMessages() {
			return (log_messages);
		}

		public void
		setLogMessages(
			boolean		b) {
			if (!is_private_chat) {

				if (b != log_messages) {

					log_messages = b;

					BuddyPluginBeta.this.setLogMessages(network, key, b);

				}
			}
		}

		public boolean
		getAutoMute() {
			return (auto_mute);
		}

		public void
		setAutoMute(
			boolean		b) {
			if (!is_private_chat) {

				if (b != auto_mute) {

					auto_mute = b;

					BuddyPluginBeta.this.setAutoMute(network, key, b);

				}
			}
		}

		public boolean
		getDisableNewMsgIndications() {
			return (disable_new_msg_indications);
		}

		public void
		setDisableNewMsgIndications(
			boolean		b) {
			if (!is_private_chat) {

				if (b != disable_new_msg_indications) {

					disable_new_msg_indications = b;

					BuddyPluginBeta.this.setDisableNewMsgIndications(network, key, b);
				}
			}
		}

		public boolean
		getEnableNotificationsPost() {
			return (enable_notification_posts);
		}

		public void
		setEnableNotificationsPost(
			boolean		b) {
			if (b != enable_notification_posts) {

				enable_notification_posts = b;

				BuddyPluginBeta.this.setEnableNotificationsPost(network, key, b);
			}
		}

		private void
		setSpammer(
			ChatParticipant		participant,
			boolean				is_spammer) {
			Map<String,Object>	options = new HashMap<String, Object>();

			options.put("pk", participant.getPublicKey());
			options.put("spammer", is_spammer);

			try {
				updateOptions(options);

			} catch (Throwable e) {

				Debug.out(e);
			}
		}

		public boolean
		isManaged() {
			return (managing_public_key != null);
		}

		public boolean
		amManager() {
			return (managing_public_key != null && Arrays.equals( my_public_key, managing_public_key));
		}

		public boolean
		isManagedFor(String		network,
			String		key) {
			if (getNetwork() != network) {

				return (false);
			}

			return (getKey().equals( key + "[pk=" + Base32.encode( getPublicKey()) + "]"));
		}

		public ChatInstance
		getManagedChannel()

			throws Exception
		{
			if (isManaged()) {

				throw (new Exception("Channel is already managed"));
			}

			String new_key = getKey() + "[pk=" + Base32.encode( getPublicKey()) + "]";

			ChatInstance inst = getChat(getNetwork(), new_key);

			return (inst);
		}

		public boolean
		isReadOnlyFor(String		network,
			String		key) {
			if (getNetwork() != network) {

				return (false);
			}

			return (getKey().equals( key + "[pk=" + Base32.encode( getPublicKey()) + "&ro=1]"));
		}

		public ChatInstance
		getReadOnlyChannel()

			throws Exception
		{
			if (isManaged()) {

				throw (new Exception("Channel is already managed"));
			}

			String new_key = getKey() + "[pk=" + Base32.encode( getPublicKey()) + "&ro=1]";

			ChatInstance inst = getChat(getNetwork(), new_key);

			return (inst);
		}

		public boolean
		isReadOnly() {
			return ( read_only && !amManager());
		}

		public String
		getURL() {
			if (network == AENetworkClassifier.AT_PUBLIC) {

				return ("chat:?" + UrlUtils.encode( key));

			} else {

				return ("chat:anon:?" + UrlUtils.encode( key));
			}
		}

		public byte[]
		getPublicKey() {
			return (my_public_key);
		}

		public boolean
		isInvisible() {
			return (is_invisible_chat);
		}

		public boolean
		isPrivateChat() {
			return (is_private_chat);
		}

		public boolean
		isAnonymous() {
			return (network != AENetworkClassifier.AT_PUBLIC);
		}

		public String
		getNetAndKey() {
			return (network + ": " + key);
		}

		public void
		setKeepAlive(
			boolean		b) {
			keep_alive	= b;
		}

		public boolean
		getKeepAlive() {
			return (keep_alive);
		}

		public String
		getDefaultNickname() {
			return ( pkToString( getPublicKey()));
		}

		public boolean
		isSharedNickname() {
			return (is_shared_nick);
		}

		public void
		setSharedNickname(
			boolean		_shared) {
			if (_shared != is_shared_nick) {

				is_shared_nick	= _shared;

				setSharedNick(network, key, _shared);

				updated();
			}
		}

		public String
		getInstanceNickname() {
			return (instance_nick);
		}

		public void
		setInstanceNickname(String		_nick) {
			if (!_nick.equals( instance_nick)) {

				instance_nick	= _nick;

				setNick(network, key, _nick);

				updated();
			}
		}

		public String
		getNickname(
			boolean	use_default) {
			String	nick;

			if (is_shared_nick) {

				nick = network == AENetworkClassifier.AT_PUBLIC?shared_public_nickname:shared_anon_nickname;

			} else {

				nick = instance_nick;
			}

			if (nick.length() == 0 && use_default) {

				return ( getDefaultNickname());
			}

			return (nick);
		}

		private Object
		getHandler() {
			return (handler);
		}

		private void
		bind(
			PluginInterface		_msgsync_pi,
			Object				_handler )

			throws Exception
		{
			boolean	inform_avail = false;

			synchronized(binding_lock) {

				binding_sem = new AESemaphore("bpb:bind");

				try {

					msgsync_pi = _msgsync_pi;

					if (_handler != null) {

						handler		= _handler;

						try {
							Map<String,Object>		options = new HashMap<String, Object>();

							options.put("handler", _handler);

							options.put("addlistener", this);

							Map<String,Object> reply = (Map<String,Object>)msgsync_pi.getIPC().invoke("updateMessageHandler", new Object[]{ options });

							my_public_key 		= (byte[])reply.get("pk");
							managing_public_key = (byte[])reply.get("mpk");
							Boolean ro 			= (Boolean)reply.get("ro");

							read_only = ro != null && ro;

							Number ipc_v = (Number)reply.get("ipc_version");

							ipc_version = ipc_v ==null?1:ipc_v.intValue();

							inform_avail = true;

						} catch (Throwable e) {

							throw (new Exception( e));
						}
					} else {

						try {
							Map<String,Object>		options = new HashMap<String, Object>();

							options.put("network", network);
							options.put("key", key.getBytes("UTF-8"));

							if (private_target != null) {

								options.put("parent_handler", private_target.getChat().getHandler());
								options.put("target_pk", private_target.getPublicKey());
								options.put("target_contact", private_target.getContact());
							}

							if (network != AENetworkClassifier.AT_PUBLIC) {

								options.put("server_id", getSharedAnonEndpoint()?"dchat_shared":"dchat");
							}

							options.put("listener", this);

							if (getSaveMessages()) {

								options.put("save_messages", true);
							}

							Map<String,Object> reply = (Map<String,Object>)msgsync_pi.getIPC().invoke("getMessageHandler", new Object[]{ options });

							handler = reply.get("handler");

							my_public_key = (byte[])reply.get("pk");
							managing_public_key = (byte[])reply.get("mpk");
							Boolean ro 			= (Boolean)reply.get("ro");

							read_only = ro != null && ro;

							Number ipc_v = (Number)reply.get("ipc_version");

							ipc_version = ipc_v ==null?1:ipc_v.intValue();

							inform_avail = true;

						} catch (Throwable e) {

							throw (new Exception( e));
						}
					}
				} finally {

					binding_sem.releaseForever();

					binding_sem = null;
				}
			}

			if (inform_avail) {

				for (ChatListener l: listeners) {

					try {
						l.stateChanged(true);

					} catch (Throwable e) {

						Debug.out(e);
					}
				}

				if (getKey().startsWith("General: ")) {

					sendLocalMessage("!*" + MessageText.getString("azbuddy.dchat.welcome.general" ) + "*!", null, ChatMessage.MT_INFO);
				}
			}
		}

		private void
		updateOptions(
			Map<String,Object>		options )

			throws Exception
		{
			if (handler == null || msgsync_pi == null) {

				Debug.out("No handler!");

			} else {

				options.put("handler", handler);

				msgsync_pi.getIPC().invoke("updateMessageHandler", new Object[]{ options });
			}
		}

		private void
		unbind() {
			for (ChatListener l: listeners) {

				try {
					l.stateChanged(false);

				} catch (Throwable e) {

					Debug.out(e);
				}
			}

			handler 	= null;
			msgsync_pi	= null;
		}

		public boolean
		isAvailable() {
			return (handler != null);
		}

		public ChatMessage[]
		getHistory() {
			synchronized(chat_lock) {

				return ( messages.toArray(new ChatMessage[ messages.size() ]));
			}
		}

		private void
		update() {
			PluginInterface		current_pi 			= msgsync_pi;
			Object 				current_handler 	= handler;

			if (current_handler != null && current_pi != null) {

				try {
					Map<String,Object>		options = new HashMap<String, Object>();

					options.put("handler", current_handler);

					status = (Map<String,Object>)current_pi.getIPC().invoke("getStatus", new Object[]{ options });

				} catch (Throwable e) {

					Debug.out(e);
				}
			}

			updated();
		}

		private void
		updated() {
			for (ChatListener l: listeners) {

				try {
					l.updated();

				} catch (Throwable e) {

					Debug.out(e);
				}
			}
		}

		public void
		handleDrop(String		str) {

		}

		public int
		getEstimatedNodes() {
			Map<String,Object> map = status;

			if (map == null) {

				return (-1);
			}

			return (((Number)map.get("node_est")).intValue());
		}

		public int
		getMessageCount(
			boolean	not_mine) {
			if (not_mine) {

				return (messages_not_mine_count);

			} else {

				return ( messages.size());
			}
		}

			/**
			 * -ve -> state unknown
			 * 0 - synced
			 * +ve - number of messages pending
			 * @return
			 */

		public int
		getIncomingSyncState() {
			Map<String,Object> map = status;

			if (map == null) {

				return (-3);
			}

			Number	in_pending = (Number)map.get("msg_in_pending");

			return ( in_pending==null?-2:in_pending.intValue());
		}

		/**
		 * -ve -> state unknown
		 * 0 - synced
		 * +ve - number of messages pending
		 * @return
		 */

		public int
		getOutgoingSyncState() {
			Map<String,Object> map = status;

			if (map == null) {

				return (-3);
			}

			Number	out_pending = (Number)map.get("msg_out_pending");

			return ( out_pending==null?-2:out_pending.intValue());
		}

		public String
		getStatus() {
			PluginInterface		current_pi 			= msgsync_pi;
			Object 				current_handler 	= handler;

			if (current_pi == null) {

				return (MessageText.getString("azbuddy.dchat.status.noplugin"));
			}

			if (current_handler == null) {

				return (MessageText.getString("azbuddy.dchat.status.nohandler"));
			}

			Map<String,Object> map = status;

			if (map == null) {

				return (MessageText.getString("azbuddy.dchat.status.notavail"));

			} else {
				int status 			= ((Number)map.get("status")).intValue();
				int dht_count 		= ((Number)map.get("dht_nodes")).intValue();

				int nodes_local 	= ((Number)map.get("nodes_local")).intValue();
				int nodes_live 		= ((Number)map.get("nodes_live")).intValue();
				int nodes_dying 	= ((Number)map.get("nodes_dying")).intValue();

				int req_in 			= ((Number)map.get("req_in")).intValue();
				double req_in_rate 	= ((Number)map.get("req_in_rate")).doubleValue();
				int req_out_ok 		= ((Number)map.get("req_out_ok")).intValue();
				int req_out_fail 	= ((Number)map.get("req_out_fail")).intValue();
				double req_out_rate = ((Number)map.get("req_out_rate")).doubleValue();

				if (status == 0 || status == 1) {

					String	arg1;
					String	arg2;

					if (isPrivateChat()) {

						arg1 = MessageText.getString("label.private.chat") + ": ";
						arg2 = "";
					} else {

						if (status == 0) {

							arg1 = MessageText.getString("pairing.status.initialising") + ": ";
							arg2 = "DHT=" + (dht_count<0?"...":String.valueOf(dht_count)) + ", ";

						} else if (status == 1) {

							arg1 = "";
							arg2 = "DHT=" + dht_count + ", ";

						} else {
							arg1 = "";
							arg2 = "";
						}
					}

					String arg3 = nodes_local+"/"+nodes_live+"/"+nodes_dying;
					String arg4 = DisplayFormatters.formatDecimal(req_out_rate,1) + "/" +  DisplayFormatters.formatDecimal(req_in_rate,1);

					String str =
						MessageText.getString(
							"azbuddy.dchat.node.status",
							new String[]{ arg1, arg2, arg3, arg4 });

					if (isReadOnly()) {

						str += ", R-";

					} else if (amManager()) {

						if (read_only) {

							str += ", R+";

						} else {

							str += ", M+";
						}
					} else if (isManaged()) {

						str += ", M-";
					}

					if (Constants.isCVSVersion()) {

						str += ", Refs=" + reference_count;
					}

					return (str);

				} else {

					return (MessageText.getString("azbuddy.dchat.status.destroyed"));
				}
			}
		}

		private TimerEvent	sort_event;
		private boolean		sort_force_changed;

		private void
		sortMessages(
			boolean		force_change) {
			synchronized(chat_lock) {

				if (force_change) {

					sort_force_changed = true;
				}

				if (sort_event != null) {

					return;
				}

				sort_event =
					SimpleTimer.addEvent(
						"msgsort",
						SystemTime.getOffsetTime(500),
						new TimerEventPerformer() {
							public void perform(
								TimerEvent event) {
								boolean	changed = false;

								synchronized(chat_lock) {

									sort_event = null;

									changed = sortMessagesSupport();

									if (sort_force_changed) {

										changed = true;

										sort_force_changed = false;
									}
								}

								if (changed) {

									for (ChatListener l: listeners) {

										l.messagesChanged();
									}
								}
							}
						});
			}
		}

		private boolean
		sortMessagesSupport() {
			int	num_messages = messages.size();

			ByteArrayHashMap<ChatMessage>	id_map 		= new ByteArrayHashMap<ChatMessage>(num_messages);
			Map<ChatMessage,ChatMessage>	prev_map 	= new HashMap<ChatMessage,ChatMessage>(num_messages);

			Map<ChatMessage,Object>	next_map 			= new HashMap<ChatMessage,Object>(num_messages);

				// build id map so we can lookup prev messages

			// System.out.println("Sorting messages");

			for (ChatMessage msg: messages) {

				// System.out.println("    " + msg.getString());

				byte[]	id = msg.getID();

				id_map.put(id, msg);
			}

				// build sets of prev/next links

			for (ChatMessage msg: messages) {

				byte[]	prev_id 	= msg.getPreviousID();

				if (prev_id != null) {

					ChatMessage prev_msg = id_map.get(prev_id);

					if (prev_msg != null) {

						msg.setPreviousID( prev_msg.getID());	// save some mem

						// ordering prev_msg::msg

						prev_map.put(msg, prev_msg);

						Object existing = next_map.get(prev_msg);

						if (existing == null) {

							next_map.put(prev_msg, msg);

						} else if (existing instanceof ChatMessage) {

							List<ChatMessage> list = new ArrayList<ChatMessage>();

							list.add((ChatMessage)existing);
							list.add(msg);

							next_map.put(prev_msg,  list);

						} else {

							((List<ChatMessage>)existing).add(msg);
						}
					}
				}
			}

				// a comparator to consistently order messages to ensure sorting is determinstic

			Comparator<ChatMessage> message_comparator =
					new Comparator<ChatMessage>() {
						public int
						compare(
							ChatMessage o1,
							ChatMessage o2) {
							return ( o1.getUID() - o2.getUID());
						}
					};

				// break any loops arbitrarily

			Set<ChatMessage>	linked_messages = new TreeSet<ChatMessage>(	message_comparator);

			linked_messages.addAll( prev_map.keySet());

			while (linked_messages.size() > 0) {

				ChatMessage start = linked_messages.iterator().next();

				linked_messages.remove(start);

				ChatMessage current = start;

				int	loops = 0;

				while (true) {

					loops++;

					if (loops > num_messages) {

						Debug.out("infinte loop");

						break;
					}

					ChatMessage prev_msg = prev_map.get(current);

					if (prev_msg == null) {

						break;

					} else {

						linked_messages.remove(prev_msg);

						if (prev_msg == start) {

								// loopage

							prev_map.put(current, null);
							next_map.put(prev_msg, null);

							Debug.out("Loopage");

							break;

						} else {

							current = prev_msg;
						}
					}
				}

			}
				// find the heads of the various trees

			Set<ChatMessage>		tree_heads = new TreeSet<ChatMessage>(message_comparator);

			for (ChatMessage msg: messages) {

				ChatMessage prev_msg = prev_map.get(msg);

				if (prev_msg != null) {

					int	 loops = 0;

					while (true) {

						loops++;

						if (loops > num_messages) {

							Debug.out("infinte loop");

							break;
						}

						ChatMessage prev_prev = prev_map.get(prev_msg);

						if (prev_prev == null) {

							tree_heads.add(prev_msg);

							break;

						} else {

							prev_msg = prev_prev;
						}
					}
				}
			}

			// System.out.println("Got trees: " + tree_heads.size());

			Set<ChatMessage>	remainder_set = new HashSet<BuddyPluginBeta.ChatMessage>(messages);

			List<ChatMessage> result = null;

			for (ChatMessage head: tree_heads) {

				List<ChatMessage>	chain = flattenTree(head, next_map, num_messages);

				remainder_set.removeAll(chain);

				if (result == null) {

					result = chain;

				} else {

					result = merge(result, chain);
				}
			}

			if (remainder_set.size() > 0) {

					// these are messages not part of any chain so sort based on time

				List<ChatMessage>	remainder = new ArrayList<ChatMessage>(remainder_set);

				Collections.sort(
						remainder,
						new Comparator<ChatMessage>() {
							public int
							compare(
								ChatMessage m1,
								ChatMessage m2) {
								long l = m1.getTimeStamp() - m2.getTimeStamp();

								if (l < 0) {
									return (-1);
								} else if (l > 0) {
									return (1);
								} else {
									return ( m1.getUID() - m2.getUID());
								}
							}
						});

				if (result == null) {

					result = remainder;

				} else {

					result = merge(result, remainder);
				}
			}

			if (result == null) {

				return (false);
			}

			boolean	changed = false;

			if (messages.size() != result.size()) {

				Debug.out("Inconsistent: " + messages.size() + "/" + result.size());

				changed = true;
			}

			Set<ChatParticipant>	participants = new HashSet<ChatParticipant>();

			for ( int i=0;i<result.size();i++) {

				ChatMessage msg = result.get(i);

				ChatParticipant p = msg.getParticipant();

				participants.add(p);

				if (!changed) {

					if (messages.get(i) != msg) {

						// System.out.println("changed at " + i + ": new = " + msg.getString() + ", old = " + messages.get(i).getString());
						changed = true;
					}
				}
			}

			if (changed) {

				messages = result;

				for (ChatParticipant p: participants) {

					p.resetMessages();
				}

				Set<ChatParticipant>	updated = new HashSet<ChatParticipant>();

				for (ChatMessage msg: messages) {

					ChatParticipant p = msg.getParticipant();

					if (p.replayMessage( msg)) {

						updated.add(p);
					}
				}

				for (ChatParticipant p: updated) {

					updated(p);
				}
			}

			return (changed);
		}

		private List<ChatMessage>
		flattenTree(
			ChatMessage					head,
			Map<ChatMessage,Object>		next_map,
			int							num_messages) {
			if (num_messages <= 0) {

					// fail safe in case for some reason we end up in a loop

				return (  new ArrayList<ChatMessage>());
			}

			List<ChatMessage> chain = new ArrayList<ChatMessage>(num_messages);

			ChatMessage msg = head;

			while (true) {

				chain.add(msg);

				num_messages--;

				Object entry = next_map.get(msg);

				if (entry instanceof ChatMessage) {

					msg = (ChatMessage)entry;

				} else if (entry instanceof List) {

					List<ChatMessage> list = (List<ChatMessage>)entry;

					List<ChatMessage> current = null;

					for (ChatMessage node: list) {

						List<ChatMessage> temp = flattenTree(node, next_map, num_messages);

						num_messages -= temp.size();

						if (current == null) {

							current = temp;

						} else {

							current = merge(current, temp);
						}
					}

					chain.addAll(current);

					break;

				} else {

					break;
				}
			}

			return (chain);
		}

		private List<ChatMessage>
		merge(
			List<ChatMessage>		list1,
			List<ChatMessage>		list2) {
			int	size1 = list1.size();
			int size2 = list2.size();

			List<ChatMessage>	result = new ArrayList<ChatMessage>(size1 + size2);

			int	pos1 = 0;
			int pos2 = 0;

			while (true) {

				if (pos1 == size1) {

					for ( int i=pos2;i<size2;i++) {

						result.add( list2.get(i));
					}

					break;

				} else if (pos2 == size2) {

					for ( int i=pos1;i<size1;i++) {

						result.add( list1.get(i));
					}

					break;

				} else {

					ChatMessage m1 = list1.get(pos1);
					ChatMessage m2 = list2.get(pos2);

					long t1 = m1.getTimeStamp();
					long t2 = m2.getTimeStamp();

					if (t1 < t2 || ( t1 == t2 && m1.getUID() < m2.getUID())) {

						result.add(m1);

						pos1++;

					} else {

						result.add(m2);

						pos2++;
					}
				}
			}

			return (result);
		}

		public void
		messageReceived(
			Map<String,Object>			message_map )

			throws IPCException
		{
			AESemaphore sem;

			synchronized(binding_lock) {

				sem = binding_sem;
			}

			if (sem != null) {

				sem.reserve();
			}

			ChatMessage msg = new ChatMessage(message_uid_next.incrementAndGet(), message_map);

			long sequence = msg.getSequence();

			ChatParticipant	new_participant = null;

			boolean	sort_outstanding = false;

			byte[]	prev_id 	= msg.getPreviousID();

			synchronized(chat_lock) {

				byte[] id = msg.getID();

				if (message_ids.containsKey( id)) {

						// duplicate, probably from plugin unload, reload and re-bind

					return;
				}

				message_ids.put(id, "");

					// best case is that message belongs at the end

				int old_msgs = messages.size();

				messages.add(msg);

				if (messages.size() > MSG_HISTORY_MAX) {

					ChatMessage removed = messages.remove(0);

					old_msgs--;

					message_ids.remove( removed.getID());

					ChatParticipant rem_part = removed.getParticipant();

					rem_part.removeMessage(removed);

					if (!rem_part.isMe()) {

						messages_not_mine_count--;
					}
				}

				int origin = msg.getFlagOrigin();

				if (origin != FLAGS_MSG_ORIGIN_USER) {

					String auto_msg = msg.getMessage();

					if (auto_msg.contains("File")) {

						auto_msg = auto_msg.replace('\\', '/');
					}

					outer:
					for (Pattern p: auto_dup_patterns) {

						Matcher m = p.matcher(auto_msg);

						while (m.find()) {

							String dup_key = m.group(1);

							if (auto_dup_set.containsKey( dup_key)) {

								msg.setDuplicate();

								break outer;
							}

							auto_dup_set.put(dup_key, "");
						}
					}
				}

				byte[] pk = msg.getPublicKey();

				ChatParticipant participant = participants.get(pk);

				if (participant == null) {

					new_participant = participant = new ChatParticipant(this, pk);

					participants.put(pk, participant);

					participant.addMessage(msg);

					if (auto_mute && !participant.isMe()) {

						participant.setIgnored(true);
					}

				} else {

					participant.addMessage(msg);
				}

				if (log_messages) {

					if (!msg.isIgnored()) {

						logMessage(this, msg);
					}
				}

				if (participant.isMe()) {

					InetSocketAddress address = msg.getAddress();

					if (address != null) {

						my_address = address;
					}

					if (msg.getFlagFlashOverride()) {

						if (getHideRatings() && msg.getFlagOrigin() == FLAGS_MSG_ORIGIN_RATINGS) {

						} else {

							last_message_requiring_attention = msg;
						}
					}
				} else {

					if (!msg.isIgnored()) {

						if (getHideRatings() && msg.getFlagOrigin() == FLAGS_MSG_ORIGIN_RATINGS) {

							// don't mark as requiring attention else icon will end up flashing with no visible message

						} else {

							last_message_requiring_attention = msg;
						}
					}

					messages_not_mine_count++;
				}

				if (sort_event != null) {

					sort_outstanding = true;

				} else {

					if (old_msgs == 0) {

					} else if (prev_id != null && Arrays.equals( prev_id, messages.get(old_msgs-1).getID())) {

						// in right place already by linkage

					} else if (msg.getMessageType() != ChatMessage.MT_NORMAL) {

						// info etc always go last

					} else {

						sortMessages(true);

						sort_outstanding = true;
					}
				}
			}

			if (new_participant != null) {

				for (ChatListener l: listeners) {

					l.participantAdded(new_participant);
				}
			}

			for (ChatListener l: listeners) {

				l.messageReceived(msg, sort_outstanding);
			}
		}

		public Map<String,Object>
		chatRequested(
			Map<String,Object>			message_map )

			throws IPCException
		{
			AESemaphore sem;

			synchronized(binding_lock) {

				sem = binding_sem;
			}

			if (sem != null) {

				sem.reserve();
			}

			if (isStatistics()) {

				throw (new IPCException("Private chat disabled for statistical channels"));
			}

			if (private_chat_state == PRIVATE_CHAT_DISABLED) {

				throw (new IPCException("Private chat disabled by recipient"));
			}

			try {
				Object	new_handler 	= message_map.get("handler");

				byte[]	remote_pk 		= (byte[])message_map.get("pk");

				ChatParticipant	participant;

				synchronized(chat_lock) {

					participant = participants.get(remote_pk);
				}

				if (participant == null) {

					throw (new IPCException("Private chat requires you send at least one message to the main chat first"));
				}

				if (private_chat_state == PRIVATE_CHAT_PINNED_ONLY && !participant.isPinned()) {

					throw (new IPCException("Recipient will only accept private chats from pinned participants"));
				}

				BuddyPluginViewInterface ui = plugin.getSWTUI();

				if (ui == null) {

					throw (new IPCException("Chat unavailable"));
				}

				ChatInstance inst = getChat(participant, new_handler);

				if (!isSharedNickname()) {

					inst.setSharedNickname(false);

					inst.setInstanceNickname( getInstanceNickname());
				}

				ui.openChat(inst);

				Map<String,Object>	reply = new HashMap<String, Object>();

				reply.put("nickname", participant.getName());

				return (reply);

			} catch (IPCException e) {

				throw (e);

			} catch (Throwable e) {

				throw (new IPCException( e));
			}
		}

		AsyncDispatcher	dispatcher = new AsyncDispatcher("sendAsync");

		public void sendMessage(
			final String					message,
			final Map<String,Object>		options) {
			sendMessage(message, null, options);
		}

		public void sendMessage(
			final String					message,
			final Map<String,Object>		flags,
			final Map<String,Object>		options) {
			dispatcher.dispatch(
				new AERunnable() {

					@Override
					public void runSupport() {
						sendMessageSupport(message, flags, options);
					}
				});
		}

		public void sendRawMessage(
			final byte[]					message,
			final Map<String,Object>		flags,
			final Map<String,Object>		options) {
			dispatcher.dispatch(
				new AERunnable() {

					@Override
					public void runSupport() {
						sendMessageSupport(message, flags, options);
					}
				});
		}

		public void sendLocalMessage(
			final String		message,
			final String[]		args,
			final int			message_type) {
			if (ipc_version < 2) {

				return;
			}

			dispatcher.dispatch(
				new AERunnable() {

					@Override
					public void runSupport() {
						Map<String,Object>		options = new HashMap<String, Object>();

						String raw_message;

						if (message.startsWith("!") && message.endsWith("!")) {

							raw_message = message.substring(1, message.length() - 1);

						} else {

							raw_message = MessageText.getString(message, args);
						}
						options.put("is_local", true);
						options.put("message", raw_message);
						options.put("message_type", message_type);

						sendMessageSupport("", null, options);
					}
				});
		}

		public void sendControlMessage(
			final String		cmd) {
			if (ipc_version < 3) {

				return;
			}

			dispatcher.dispatch(
				new AERunnable() {

					@Override
					public void runSupport() {
						Map<String,Object>		options = new HashMap<String, Object>();

						options.put("is_control", true);
						options.put("cmd", cmd);

						sendMessageSupport("", null, options);
					}
				});
		}

		private void
		sendMessageSupport(
			Object					o_message,
			Map<String,Object>		flags,
			Map<String,Object>		options) {
			if (handler == null || msgsync_pi == null) {

				Debug.out("No handler/plugin");

			} else {

				if (o_message instanceof String) {

					final String message = (String)o_message;

					if (message.equals("!dump!")) {

						synchronized(chat_lock) {

							for (ChatMessage msg: messages) {

								System.out.println( msg.getTimeStamp() + ": " + pkToString( msg.getID()) + ", " + pkToString( msg.getPreviousID()) + ", " + msg.getSequence() + " - " + msg.getMessage());
							}
						}
						return;

					} else if (message.equals("!sort!")) {

						sortMessages(false);

						return;

					} else if (message.equals("!flood!")) {

						if (DEBUG_ENABLED) {

							SimpleTimer.addPeriodicEvent(
								"flooder",
								1500,
								new TimerEventPerformer() {

									public void perform(TimerEvent event) {

										sendMessage("flood - " + SystemTime.getCurrentTime(), null);

									}
								});
						}

						return;


					} else if (message.equals("!ftux!")) {

						plugin.getBeta().setFTUXAccepted(false);

						return;
					}

					boolean	is_me_msg = false;

					if (message.startsWith("/")) {

						String[] bits = message.split("[\\s]+", 3);

						String command = bits[0].toLowerCase(Locale.US);

						boolean	ok 				= false;
						boolean	missing_params 	= false;

						try {
							if (command.equals("/help")) {

								String link = MessageText.getString("azbuddy.dchat.link.url");

								sendLocalMessage("label.see.x.for.help", new String[]{ link }, ChatMessage.MT_INFO);

								ok = true;

							} else if (command.equals("/join")) {

								if (bits.length > 1) {

									bits = message.split("[\\s]+", 2);

									String key = bits[1];

									if (key.startsWith("\"") && key.endsWith("\"")) {
										key = key.substring(1,key.length()-1);
									}

									getAndShowChat(getNetwork(), key);

									ok = true;

								} else {

									missing_params = true;
								}
							} else if (command.equals("/nick")) {

								if (bits.length > 1) {

									bits = message.split("[\\s]+", 2);

									setSharedNickname(false);

									setInstanceNickname( bits[1]);

									ok = true;

								} else {

									missing_params = true;
								}

							} else if (command.equals("/pjoin")) {

								if (bits.length > 1) {

									bits = message.split("[\\s]+", 2);

									String key = bits[1];

									if (key.startsWith("\"") && key.endsWith("\"")) {
										key = key.substring(1,key.length()-1);
									}

									getAndShowChat(AENetworkClassifier.AT_PUBLIC, key);

									ok = true;

								} else {

									missing_params = true;
								}
							} else if (command.equals("/ajoin")) {

								if (bits.length <= 1) {

									missing_params = true;

								} else if (!isI2PAvailable()) {

									throw (new Exception("I2P not available"));

								} else {

									bits = message.split("[\\s]+", 2);

									String key = bits[1];

									if (key.startsWith("\"") && key.endsWith("\"")) {
										key = key.substring(1,key.length()-1);
									}

									getAndShowChat(AENetworkClassifier.AT_I2P, key);

									ok = true;
								}
							} else if (command.equals("/msg") || command.equals("/query")) {

								if (bits.length > 1) {

									String nick = bits[1];

									String	pm = bits.length ==2?"":bits[2].trim();

									ChatParticipant p = getParticipant(nick);

									if (p == null) {

										throw (new Exception("Nick not found: " + nick));

									} else if (p.isMe()) {

										throw (new Exception("Can't chat to yourself"));
									}

									ChatInstance ci = p.createPrivateChat();

									if (pm.length() > 0) {

										ci.sendMessage( pm, new HashMap<String, Object>());
									}

									showChat(ci);

									ok = true;

								} else {

									missing_params = true;
								}
							} else if (command.equals("/me")) {

								if (bits.length > 1) {

									is_me_msg	= true;

									o_message = message.substring(3).trim();

									if (flags == null) {

										flags = new HashMap<String, Object>();
									}

									flags.put(FLAGS_MSG_TYPE_KEY, FLAGS_MSG_TYPE_ME);

									ok = true;

								} else {

									missing_params = true;
								}
							} else if (command.equals("/ignore")) {

								if (bits.length > 1) {

									String nick = bits[1];

									boolean	ignore = true;

									if (nick.equals("-r") && bits.length > 2) {

										nick = bits[2];

										ignore = false;
									}

									ChatParticipant p = getParticipant(nick);

									if (p == null) {

										throw (new Exception("Nick not found: " + nick));
									}

									p.setIgnored(ignore);

										// obviously the setter should do this but whatever for the mo

									updated(p);

									ok = true;

								} else {

									missing_params = true;
								}
							} else if (command.equals("/control")) {

								if (ipc_version >= 3) {

									String[] bits2 = message.split("[\\s]+", 2);

									if (bits2.length > 1) {

										sendControlMessage(bits2[1]);

										ok = true;

									} else {

										throw (new Exception("Invalid command: " + message));
									}
								}

							} else if (command.equals("/peek")) {

								if (bits.length > 1) {

									Map<String,Object> result = peekChat(getNetwork(), message.substring( 5).trim());

									sendLocalMessage("!" + result + "!", null, ChatMessage.MT_INFO);

									ok = true;

								} else {

									missing_params = true;
								}
							} else if (command.equals("/clone")) {

								getAndShowChat( getNetwork(), getKey());

								ok = true;
							}

							if (!ok) {

								if (missing_params) {

									throw (new Exception("Error: Insufficient parameters for '" + command + "'"));
								}

								throw (new Exception("Error: Unhandled command: " + message));
							}
						} catch (Throwable e) {

							sendLocalMessage("!" + Debug.getNestedExceptionMessage( e ) + "!", null, ChatMessage.MT_ERROR);
						}

						if (!is_me_msg) {

							return;
						}
					}
				}

				try {
					ChatMessage		prev_message 	= null;
					long			prev_sequence	= -1;

					synchronized(chat_lock) {

						int	pos = messages.size() - 1;

						int	 missing_seq = 0;

						while (pos >= 0) {

							ChatMessage m = messages.get(pos--);

							if (m.getMessageType() == ChatMessage.MT_NORMAL) {

								if (prev_message == null) {

									prev_message = m;
								}

								prev_sequence = m.getSequence();

								if (prev_sequence > 0) {

									break;

								} else {

									missing_seq++;
								}
							}
						}

						if (prev_message != null) {

							prev_sequence += missing_seq;
						}
					}

					if (options == null) {

						options = new HashMap<String, Object>();

					} else {

							// clone as we are updating

						options = new HashMap<String, Object>(options);
					}

					options.put("handler", handler);

					Map<String,Object>	payload = new HashMap<String, Object>();

					if (o_message instanceof String) {

						payload.put("msg", ((String)o_message).getBytes("UTF-8"));

					} else {

						payload.put("msg", (byte[])o_message);
					}

					payload.put("nick", getNickname( false ).getBytes("UTF-8"));

					if (prev_message != null) {

						payload.put("pre", prev_message.getID());
						payload.put("seq", prev_sequence + 1);
					}

					if (flags != null) {

						payload.put("f", flags);
					}

					options.put("content", BEncoder.encode( payload));

					Map<String,Object> reply = (Map<String,Object>)msgsync_pi.getIPC().invoke("sendMessage", new Object[]{ options });

						// once we participate in a chat then we want to keep it around to ensure
						// or at least try and ensure message delivery

					have_interest = true;

				} catch (Throwable e) {

					Debug.out(e);
				}
			}
		}

		public String
		export() {
			if (handler == null || msgsync_pi == null) {

				return ("");
			}
			try {
				Map<String,Object>		options = new HashMap<String, Object>();

				options.put("handler", handler);

				Map<String,Object> reply = (Map<String,Object>)msgsync_pi.getIPC().invoke("exportMessageHandler", new Object[]{ options });

				return ((String)reply.get("export_data"));

			} catch (Throwable e) {

				Debug.out(e);

				return ("");
			}
		}

		public List<ChatMessage>
		getMessages() {
			synchronized(chat_lock) {

				return (new ArrayList<ChatMessage>( messages));
			}
		}

		public boolean
		hasUnseenMessageWithNick() {
			List<ChatMessage> messages = getUnseenMessages();

			for (ChatMessage msg: messages) {

				if (msg.getNickLocations().length > 0) {

					return (true);
				}
			}

			return (false);
		}

		public List<ChatMessage>
		getUnseenMessages() {
			synchronized(chat_lock) {

				LinkedList<ChatMessage> result = new LinkedList<ChatMessage>();

				if (messages.size() > 0) {

					for (int loop=0;loop<2;loop++) {

						List<ChatMessage>	need_fixup = new ArrayList<ChatMessage>();

						for ( int i=messages.size()-1;i>=0;i--) {

							ChatMessage msg = messages.get(i);

							if (msg.isIgnored() || msg.getParticipant().isMe()) {

								continue;
							}

							int seen_state =  msg.getSeenState();

							if (seen_state == ChatMessage.SEEN_YES) {

								break;

							} else if (seen_state == ChatMessage.SEEN_UNKNOWN) {

								need_fixup.add(msg);

							} else {

								result.addFirst(msg);
							}
						}

						if (loop==0 && need_fixup.size() > 0) {

							fixupSeenState(need_fixup);

							result.clear();

						} else {

							if (need_fixup.size() > 0) {

								Debug.out("Hmm");
							}

							break;
						}
					}
				}

				return (result);
			}
		}

		public ChatParticipant[]
		getParticipants() {
			synchronized(chat_lock) {

				return ( participants.values().toArray(new ChatParticipant[ participants.size()]));
			}
		}

		public ChatParticipant
		getParticipant(String	nick) {
			synchronized(chat_lock) {

				for ( ChatParticipant cp: participants.values()) {

					if (cp.getName().equals( nick)) {

						return (cp);
					}
				}
			}

			return (null);
		}

		protected void
		updated(
			ChatParticipant		p) {
			for (ChatListener l: listeners) {

				l.participantChanged(p);
			}
		}

		private void
		registerNick(
			ChatParticipant		p,
			String				old_nick,
			String				new_nick) {
			synchronized(chat_lock) {

				if (old_nick != null) {

					List<ChatParticipant> list = nick_clash_map.get(old_nick);

					if (list != null && list.remove( p)) {

						if (list.size() == 0) {

							nick_clash_map.remove(old_nick);

						} else {

							if (list.size() == 1) {

								list.get(0).setNickClash(false);
							}
						}
					} else {

						Debug.out("inconsistent");
					}
				}

				List<ChatParticipant> list = nick_clash_map.get(new_nick);

				if (list == null) {

					list = new ArrayList<BuddyPluginBeta.ChatParticipant>();

					nick_clash_map.put(new_nick, list);
				}

				if (list.contains( p)) {

					Debug.out("inconsistent");

				} else {

					list.add(p);

					if (list.size() > 1) {

						p.setNickClash(true);

						if (list.size() == 2) {

							list.get(0).setNickClash(true);
						}
					} else {

						p.setNickClash(false);
					}
				}
			}
		}

		public ChatMessage
		getLastMessageRequiringAttention() {
			return (last_message_requiring_attention);
		}

		public void
		setUserData(
			Object		key,
			Object		value) {
			synchronized(user_data) {

				user_data.put(key, value);
			}
		}

		public Object
		getUserData(
			Object		key) {
			synchronized(user_data) {

				return (user_data.get( key));
			}
		}

		public boolean
		getMessageOutstanding() {
			synchronized(chat_lock) {

				return (message_outstanding);
			}
		}

		public void
		setMessageOutstanding(
			ChatMessage		message ) {
			boolean	outstanding = message != null;

			boolean changed = false;

			addChatActivity(this, message);

			synchronized(chat_lock) {

				if (message_outstanding == outstanding) {

					return;
				}

				message_outstanding = outstanding;

				changed = true;

				if (!outstanding) {

					if (messages.size() > 0) {

						ChatMessage	last_read_msg = messages.get(messages.size()-1);

						long last_read_time = last_read_msg.getTimeStamp();

						String last_info = (SystemTime.getCurrentTime()/1000) + "/" + (last_read_time/1000) + "/" + Base32.encode( last_read_msg.getID());

						BuddyPluginBeta.this.setLastMessageInfo(network, key, last_info);
					}
				}
			}

			if (changed) {

				updated();
			}
		}

		public boolean
		isOldOutstandingMessage(
			ChatMessage			msg) {
			synchronized(chat_lock) {

				String info = BuddyPluginBeta.this.getLastMessageInfo(network, key);

				if (info != null) {

					String[] bits = info.split("/");

					try {
						long	old_time_secs 	= Long.parseLong(bits[0]);
						long	old_msg_secs 	= Long.parseLong(bits[1]);
						byte[]	old_id			= Base32.decode(bits[2]);

						long	msg_secs	= msg.getTimeStamp()/1000;
						byte[]	id			= msg.getID();

						if (Arrays.equals( id, old_id)) {

							return (true);
						}

						long	old_cuttoff = old_time_secs - 5*60;

						if (old_msg_secs > old_cuttoff) {

							old_cuttoff = old_msg_secs;
						}

						if (msg_secs <= old_cuttoff) {

							return (true);
						}

						if (message_ids.containsKey(old_id) && message_ids.containsKey( id)) {

							int	msg_index 		= -1;
							int old_msg_index 	= -1;

							for ( int i=0;i<messages.size();i++) {

								ChatMessage m = messages.get(i);

								if (m == msg) {

									msg_index = i;

								} else if (Arrays.equals( m.getID(), old_id)) {

									old_msg_index = i;
								}
							}

							if (msg_index <= old_msg_index) {

								return (true);
							}
						}
					} catch (Throwable e) {

					}
				}
			}

			return (false);
		}

		public void
		fixupSeenState(
			List<ChatMessage>			msgs) {
			for (ChatMessage msg: msgs) {

				msg.setSeen(false);
			}

			synchronized(chat_lock) {

				String info = BuddyPluginBeta.this.getLastMessageInfo(network, key);

				if (info != null) {

					String[] bits = info.split("/");

					try {
						long	old_time_secs 	= Long.parseLong(bits[0]);
						long	old_msg_secs 	= Long.parseLong(bits[1]);
						byte[]	old_id			= Base32.decode(bits[2]);

						for (ChatMessage msg: msgs) {

							long	msg_secs	= msg.getTimeStamp()/1000;
							byte[]	id			= msg.getID();

							if (Arrays.equals( id, old_id)) {

								msg.setSeen(true);

							} else {

								long	old_cuttoff = old_time_secs - 5*60;

								if (old_msg_secs > old_cuttoff) {

									old_cuttoff = old_msg_secs;
								}

								if (msg_secs <= old_cuttoff) {

									msg.setSeen(true);
								}
							}
						}

						if (message_ids.containsKey( old_id)) {

							Map<ChatMessage,Integer>	msg_map = new HashMap<ChatMessage, Integer>();

							int old_msg_index 	= -1;

							for ( int i=0;i<messages.size();i++) {

								ChatMessage m = messages.get(i);

								msg_map.put(m, i);

								if (Arrays.equals( m.getID(), old_id)) {

									old_msg_index = i;
								}
							}

							for (ChatMessage msg: msgs) {

								Integer msg_index = msg_map.get(msg);

								if (msg_index != null && msg_index <= old_msg_index) {

									msg.setSeen(true);
								}
							}
						}
					} catch (Throwable e) {
					}
				}
			}
		}

		public InetSocketAddress
		getMyAddress() {
			return (my_address);
		}

		public void
		addListener(
			ChatListener		listener) {
			listeners.add(listener);
		}

		public void
		removeListener(
			ChatListener		listener) {
			listeners.remove(listener);
		}

		public void
		remove() {
			destroy(true);

			removeAllOptions(network, key);
		}

		public boolean
		isDestroyed() {
			return (destroyed);
		}

		public void
		destroy() {
			destroy(false);
		}

		private void
		destroy(
			boolean		force) {
			synchronized(chat_lock) {

				if (force) {

					reference_count	= 0;
					keep_alive		= false;
					have_interest	= false;

				} else {

					reference_count--;

					// Debug.out(getName() + ": removed ref -> " + reference_count);

					if (reference_count > 0) {

						return;
					}
				}
			}

			if (!( keep_alive || (have_interest && !is_private_chat))) {

				destroyed = true;

				if (handler != null) {

					if (is_private_chat) {

						Map<String,Object>		flags = new HashMap<String, Object>();

						flags.put(FLAGS_MSG_STATUS_KEY, FLAGS_MSG_STATUS_CHAT_QUIT);

						sendMessageSupport("", flags, new HashMap<String, Object>());
					}

					try {
						Map<String,Object>		options = new HashMap<String, Object>();

						options.put("handler", handler);

						Map<String,Object> reply = (Map<String,Object>)msgsync_pi.getIPC().invoke("removeMessageHandler", new Object[]{ options });

					} catch (Throwable e) {

						Debug.out(e);

					} finally {

						String meta_key = network + ":" + key;

						ChatInstance	removed = null;

						synchronized(chat_instances_map) {

							ChatInstance inst = chat_instances_map.remove(meta_key);

							if (inst != null) {

								removed = inst;

								chat_instances_list.remove(inst);
							}

							if (chat_instances_map.size() == 0) {

								if (timer != null) {

									timer.cancel();

									timer = null;
								}
							}
						}

						if (removed != null) {

							for (ChatManagerListener l: BuddyPluginBeta.this.listeners) {

								try {
									l.chatRemoved(removed);

								} catch (Throwable e) {

									Debug.out(e);;
								}
							}
						}
					}
				}
			}
		}
	}

	public class ChatParticipant
	{
		private final ChatInstance		chat;
		private final byte[]			pk;

		private String				nickname;
		private boolean				is_ignored;
		private boolean				is_spammer;
		private boolean				is_pinned;
		private boolean				nick_clash;

		private List<ChatMessage>	participant_messages	= new ArrayList<ChatMessage>();

		private Boolean				is_me;

		private
		ChatParticipant(
			ChatInstance		_chat,
			byte[]				_pk) {
			chat	= _chat;
			pk		= _pk;

			nickname = pkToString(pk);

			is_pinned = COConfigurationManager.getBooleanParameter(getPinKey(), false);

			chat.registerNick(this, null, nickname);
		}

		public ChatInstance
		getChat() {
			return (chat);
		}

		public byte[]
		getPublicKey() {
			return (pk);
		}

		public Map<String,Object>
		getContact() {
			synchronized(chat.chat_lock) {

				if (participant_messages.isEmpty()) {

					return (null);
				}

				return ( participant_messages.get( participant_messages.size()-1).getContact());
			}
		}

		public InetSocketAddress
		getAddress() {
			synchronized(chat.chat_lock) {

				if (participant_messages.isEmpty()) {

					return (null);
				}

				return ( participant_messages.get( participant_messages.size()-1).getAddress());
			}
		}

		public boolean
		isMe() {
			if (is_me != null) {

				return (is_me);
			}

			byte[] chat_key = chat.getPublicKey();

			if (chat_key != null) {

				is_me = Arrays.equals(pk, chat_key);
			}

			return (is_me==null?false:is_me);
		}

		public String
		getName() {
			return (getName( true));
		}

		public String
		getName(
			boolean	use_nick) {
			if (use_nick) {

				return (nickname);

			} else {

				return (pkToString( pk));
			}
		}

		public boolean
		hasNickname() {
			return (!nickname.equals( pkToString( pk)));
		}

		private void
		addMessage(
			ChatMessage		message) {
			participant_messages.add(message);

			message.setParticipant(this);

			message.setIgnored(is_ignored || is_spammer);

			String new_nickname = message.getNickName();

			if (!nickname.equals(new_nickname)) {

				chat.registerNick(this, nickname, new_nickname);

				message.setNickClash( isNickClash());

				nickname = new_nickname;

				chat.updated(this);

			} else {

				message.setNickClash( isNickClash());
			}
		}

		private boolean
		replayMessage(
			ChatMessage		message) {
			participant_messages.add(message);

			message.setIgnored(is_ignored || is_spammer);

			String new_nickname = message.getNickName();

			if (!nickname.equals(new_nickname)) {

				chat.registerNick(this, nickname, new_nickname);

				message.setNickClash( isNickClash());

				nickname = new_nickname;

				return (true);

			} else {

				message.setNickClash( isNickClash());

				return (false);
			}
		}

		private void
		removeMessage(
			ChatMessage		message) {
			participant_messages.remove(message);
		}

		private void
		resetMessages() {
			String new_nickname = pkToString(pk);

			if (!nickname.equals(new_nickname)) {

				chat.registerNick(this, nickname, new_nickname);

				nickname = new_nickname;
			}

			participant_messages.clear();
		}

		public List<ChatMessage>
		getMessages() {
			synchronized(chat.chat_lock) {

				return (new ArrayList<ChatMessage>( participant_messages));
			}
		}

		public boolean
		isIgnored() {
			return (is_ignored);
		}

		public void
		setIgnored(
			boolean		b) {
			if (b != is_ignored) {

				is_ignored = b;

				synchronized(chat.chat_lock) {

					for (ChatMessage message: participant_messages) {

						message.setIgnored( b || is_spammer);
					}
				}
			}
		}

		public boolean
		isSpammer() {
			return (is_spammer);
		}

		public boolean
		canSpammer() {
			return (participant_messages.size() >= 5 && !is_spammer);
		}

		public void
		setSpammer(
			boolean		b) {
			if (b != is_spammer) {

				is_spammer = b;

				chat.setSpammer(this, b);

				synchronized(chat.chat_lock) {

					for (ChatMessage message: participant_messages) {

						message.setIgnored(b || is_ignored);
					}
				}
			}
		}

		public boolean
		isPinned() {
			return (is_pinned);
		}

		private String
		getPinKey() {
			return ("azbuddy.chat.pinned." + ByteFormatter.encodeString( pk, 0, 16));
		}

		public void
		setPinned(
			boolean		b) {
			if (b != is_pinned) {

				is_pinned = b;

				String key = getPinKey();

				if (is_pinned) {

					COConfigurationManager.setParameter(key, true);

				} else {

					COConfigurationManager.removeParameter(key);
				}

				COConfigurationManager.setDirty();
			}
		}

		public boolean
		isNickClash() {
			return (nick_clash);
		}

		private void
		setNickClash(
			boolean	b) {
			nick_clash = b;
		}

		public ChatInstance
		createPrivateChat()

			throws Exception
		{
			ChatInstance inst = BuddyPluginBeta.this.getChat(this);

			ChatInstance	parent = getChat();

			if (!parent.isSharedNickname()) {

				inst.setSharedNickname(false);

				inst.setInstanceNickname( parent.getInstanceNickname());
			}

			return (inst);
		}
	}

	public class ChatMessage
	{
		public static final int MT_NORMAL	= 1;
		public static final int MT_INFO		= 2;
		public static final int MT_ERROR	= 3;

		protected static final int SEEN_UNKNOWN	= 0;
		protected static final int SEEN_YES		= 1;
		protected static final int SEEN_NO		= 2;

		private final int						uid;
		private final Map<String,Object>		map;

		private WeakReference<Map<String,Object>>	payload_ref;

		private final byte[]					message_id;
		private final long						timestamp;

		private ChatParticipant					participant;

		private byte[]							previous_id;
		private long							sequence;

		private boolean							is_ignored;
		private boolean							is_duplicate;
		private boolean							is_nick_clash;

		private int								seen_state = SEEN_UNKNOWN;
		private int[]							nick_locations;

		private
		ChatMessage(
			int						_uid,
			Map<String,Object>		_map) {
			uid		= _uid;
			map		= _map;

			message_id = (byte[])map.get("id");

			timestamp = SystemTime.getCurrentTime() - getAgeWhenReceived()*1000L;

			Map<String,Object> payload = getPayload();

			previous_id = (byte[])payload.get("pre");

			Number	l_seq = (Number)payload.get("seq");

			sequence = l_seq==null?0:l_seq.longValue();
		}

		protected int
		getUID() {
			return (uid);
		}

		private void
		setParticipant(
			ChatParticipant		p) {
			participant	= p;
		}

		public ChatParticipant
		getParticipant() {
			return (participant);
		}

		private void
		setNickClash(
			boolean	clash) {
			is_nick_clash = clash;
		}

		public boolean
		isNickClash() {
			return (is_nick_clash);
		}

		public void
		setSeen(
			boolean	is_seen) {
			seen_state = is_seen?SEEN_YES:SEEN_NO;
		}

		public int
		getSeenState() {
			return (seen_state);
		}

		public int[]
		getNickLocations() {
			synchronized(this) {

				if (nick_locations == null) {

					if (participant == null) {

						return (new int[0]);
					}

					String my_nick = participant.getChat().getNickname(true);

					int	nick_len = my_nick.length();

					List<Integer> hits = new ArrayList<Integer>();

					if (my_nick.length() > 0) {

						String text = getMessage();

						int	text_len = text.length();

						int	pos = 0;

						while (pos < text_len) {

							pos = text.indexOf(my_nick, pos);

							if (pos >= 0) {

								boolean	match = true;

								if (pos > 0) {

									if (Character.isLetterOrDigit( text.charAt( pos-1))) {

										match = false;
									}
								}

								int nick_end = pos + nick_len;

								if (nick_end < text_len) {

									if (Character.isLetterOrDigit( text.charAt(nick_end))) {

										match = false;
									}
								}

								if (match) {

									hits.add(pos);
								}

								pos += nick_len;

							} else {

								break;
							}
						}
					}

					if (hits.size() == 0) {

						nick_locations = new int[0];

					} else {

						nick_locations = new int[hits.size()+1];

						nick_locations[0] = nick_len;

						for (int i=0;i<hits.size();i++) {

							nick_locations[i+1] = hits.get(i);
						}
					}
				}

				return (nick_locations);
			}
		}

		private Map<String,Object>
		getPayload() {
			synchronized(this) {

				Map<String,Object> payload = null;

				if (payload_ref != null) {

					payload = payload_ref.get();

					if (payload != null) {

						return (payload);
					}
				}

				try {
					byte[] content_bytes = (byte[])map.get("content");

					if (content_bytes != null && content_bytes.length > 0) {

						payload = BDecoder.decode(content_bytes);
					}
				} catch ( Throwable e) {
				}

				if (payload == null) {

					payload = new HashMap<String, Object>();
				}

				payload_ref = new WeakReference<Map<String,Object>>(payload);

				return (payload);
			}
		}

		private int
		getMessageStatus() {
			Map<String,Object> payload = getPayload();

			if (payload != null) {

				Map<String,Object>	flags = (Map<String,Object>)payload.get("f");

				if (flags != null) {

					Number status = (Number)flags.get(FLAGS_MSG_STATUS_KEY);

					if (status != null) {

						return ( status.intValue());
					}
				}
			}

			return (FLAGS_MSG_STATUS_CHAT_NONE);
		}

		private boolean
		getFlagFlashOverride() {
			Map<String,Object> payload = getPayload();

			if (payload != null) {

				Map<String,Object>	flags = (Map<String,Object>)payload.get("f");

				if (flags != null) {

					Number override = (Number)flags.get(FLAGS_MSG_FLASH_OVERRIDE);

					if (override != null) {

						return (override.intValue() != 0);
					}
				}
			}

			return (false);
		}

		public int
		getFlagOrigin() {
			Map<String,Object> payload = getPayload();

			if (payload != null) {

				Map<String,Object>	flags = (Map<String,Object>)payload.get("f");

				if (flags != null) {

					Number origin = (Number)flags.get(FLAGS_MSG_ORIGIN_KEY);

					if (origin != null) {

						return ( origin.intValue());
					}
				}
			}

				// bah, merging message has been missing origin flag

			String msg_text = getMessage();

			if (msg_text.startsWith("See http://wiki.vuze.com/w/Swarm_Merging")) {

				return (FLAGS_MSG_ORIGIN_RATINGS);
			}

			return (FLAGS_MSG_ORIGIN_USER);
		}

		public int
		getFlagType() {
			Map<String,Object> payload = getPayload();

			if (payload != null) {

				Map<String,Object>	flags = (Map<String,Object>)payload.get("f");

				if (flags != null) {

					Number type = (Number)flags.get(FLAGS_MSG_TYPE_KEY);

					if (type != null) {

						return ( type.intValue());
					}
				}
			}

			return (FLAGS_MSG_TYPE_NORMAL);
		}

		public String
		getMessage() {
			try {
				String	report = (String)map.get("error");

				if (report != null) {

					if (report.length() > 2 && report.charAt(1) == ':') {

						return (report.substring( 2));
					}

					return (report);
				}

				if (getMessageStatus() == FLAGS_MSG_STATUS_CHAT_QUIT) {

					return (
						MessageText.getString(
							"azbuddy.dchat.hasquit",
							new String[]
							{ participant==null?"<unknown>":participant.getName()}));
				}

					// was just a string for a while...

				Map<String,Object> payload = getPayload();

				if (payload != null) {

					byte[] msg_bytes = (byte[])payload.get("msg");

					if (msg_bytes != null) {

						return (new String( msg_bytes, "UTF-8"));
					}
				}



				return (new String((byte[])map.get("content" ), "UTF-8"));

			} catch (Throwable e) {

				Debug.out(e);

				return ("");
			}
		}

		public byte[]
		getRawMessage() {
			try {
				String	report = (String)map.get("error");

				if (report != null) {

					return (null);
				}

				if (getMessageStatus() == FLAGS_MSG_STATUS_CHAT_QUIT) {

					return (null);
				}

					// was just a string for a while...

				Map<String,Object> payload = getPayload();

				if (payload != null) {

					byte[] msg_bytes = (byte[])payload.get("msg");

					if (msg_bytes != null) {

						return (msg_bytes);
					}
				}

				return ((byte[])map.get("content"));

			} catch (Throwable e) {

				Debug.out(e);

				return (null);
			}
		}

		public int
		getMessageType() {
			return (getMessageType( true));
		}

		private int
		getMessageType(
			boolean	treat_quit_as_info) {
			String	report = (String)map.get("error");

			if (report == null) {

				if (treat_quit_as_info && getMessageStatus() == FLAGS_MSG_STATUS_CHAT_QUIT) {

					return (MT_INFO);
				}

				return (MT_NORMAL);

			} else {

				if (report.length() < 2 || report.charAt(1) != ':') {

					return (MT_ERROR);
				}

				char type = report.charAt(0);

				if (type == 'i') {

					return (MT_INFO);
				} else {

					return (MT_ERROR);
				}
			}
		}

		public void
		setDuplicate() {
			is_duplicate	= true;
		}

		public boolean
		isIgnored() {
			return (is_duplicate || is_ignored);
		}

		public void
		setIgnored(
			boolean		b) {
			is_ignored = b;
		}

		public byte[]
		getID() {
			return (message_id);
		}

		public byte[]
		getPreviousID() {
			return (previous_id);
		}

		private void
		setPreviousID(
			byte[]		pid) {
			previous_id = pid;
		}

		public long
		getSequence() {
			return (sequence);
		}

		public byte[]
		getPublicKey() {
			return ((byte[])map.get("pk"));
		}

		public Map<String,Object>
		getContact() {
			return ((Map<String,Object>)map.get("contact"));
		}

		public InetSocketAddress
		getAddress() {
			return ((InetSocketAddress)map.get("address"));
		}

		private int
		getAgeWhenReceived() {
			return (((Number)map.get("age")).intValue());
		}

		public long
		getTimeStamp() {
			return (timestamp);
		}

		public String
		getNickName() {
				// always use payload if available (for real messages)

			Map<String,Object> payload = getPayload();

			if (payload != null) {

				byte[] nick = (byte[])payload.get("nick");

				if (nick != null) {

					try {
						String str = new String(nick, "UTF-8");

						if (str.length() > 0) {

							return (str);
						}
					} catch (Throwable e) {
					}
				}
			}

				// otherwise assume it is internally generated for non-normal messages

			if (getMessageType(false) != ChatMessage.MT_NORMAL) {

				String nick = participant.getChat().getNickname(false);

				if (nick.length() > 0) {

					return (nick);
				}
			}

				// default when no user specified one present

			return ( pkToString( getPublicKey()));
		}

		public String
		getString() {
			return ("a=" + new SimpleDateFormat("D HH:mm:ss" ).format( getTimeStamp()) + ", i=" + pkToString( message_id ) + ", p=" + pkToString( previous_id) + ": " + getMessage());
		}
	}

	public interface ChatManagerListener
	{
		public void
		chatAdded(
			ChatInstance	inst);

		public void
		chatRemoved(
			ChatInstance	inst);
	}

	public interface ChatListener
	{
		public void
		messageReceived(
			ChatMessage				message,
			boolean					sort_outstanding);

		public void
		messagesChanged();

		public void
		participantAdded(
			ChatParticipant			participant);

		public void
		participantChanged(
			ChatParticipant			participant);

		public void
		participantRemoved(
			ChatParticipant			participant);

		public void
		stateChanged(
			boolean					avail);

		public void
		updated();
	}

	public static class
	ChatAdapter
		implements ChatListener
	{
		public void updated() {
		}

		public void stateChanged(
			boolean avail) {
		}

		public void participantRemoved(
			ChatParticipant participant) {
		}

		public void participantChanged(
			ChatParticipant participant) {
		}

		public void participantAdded(
			ChatParticipant participant) {
		}

		public void messagesChanged() {
		}

		public void messageReceived(
			ChatMessage 	message,
			boolean			sort_outstanding) {
		}
	}

	public interface FTUXStateChangeListener
	{
		public void
		stateChanged(
			boolean	accepted);
	}
}
