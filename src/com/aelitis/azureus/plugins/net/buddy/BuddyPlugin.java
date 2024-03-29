/*
 * Created on Mar 19, 2008
 * Created by Paul Gardner
 *
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


package com.aelitis.azureus.plugins.net.buddy;

import java.io.*;
import java.net.*;
import java.util.*;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.download.DownloadManager;
import org.gudy.azureus2.core3.download.DownloadManagerState;
import org.gudy.azureus2.core3.util.*;
import org.gudy.azureus2.core3.util.protocol.azplug.AZPluginConnection;
import org.gudy.azureus2.core3.xml.util.XUXmlWriter;
import org.gudy.azureus2.plugins.*;
import org.gudy.azureus2.plugins.ddb.*;
import org.gudy.azureus2.plugins.disk.DiskManagerFileInfo;
import org.gudy.azureus2.plugins.download.Download;
import org.gudy.azureus2.plugins.download.DownloadException;
import org.gudy.azureus2.plugins.download.DownloadScrapeResult;
import org.gudy.azureus2.plugins.ipc.IPCException;
import org.gudy.azureus2.plugins.logging.LoggerChannel;
import org.gudy.azureus2.plugins.messaging.MessageException;
import org.gudy.azureus2.plugins.messaging.MessageManager;
import org.gudy.azureus2.plugins.messaging.generic.GenericMessageConnection;
import org.gudy.azureus2.plugins.messaging.generic.GenericMessageHandler;
import org.gudy.azureus2.plugins.messaging.generic.GenericMessageRegistration;
import org.gudy.azureus2.plugins.network.ConnectionManager;
import org.gudy.azureus2.plugins.network.RateLimiter;
import org.gudy.azureus2.plugins.torrent.Torrent;
import org.gudy.azureus2.plugins.torrent.TorrentAttribute;
import org.gudy.azureus2.plugins.ui.UIInstance;
import org.gudy.azureus2.plugins.ui.UIManagerListener;
import org.gudy.azureus2.plugins.ui.config.*;
import org.gudy.azureus2.plugins.ui.menus.MenuItem;
import org.gudy.azureus2.plugins.ui.menus.MenuItemFillListener;
import org.gudy.azureus2.plugins.ui.menus.MenuItemListener;
import org.gudy.azureus2.plugins.ui.model.BasicPluginConfigModel;
import org.gudy.azureus2.plugins.ui.tables.TableContextMenuItem;
import org.gudy.azureus2.plugins.ui.tables.TableManager;
import org.gudy.azureus2.plugins.ui.tables.TableRow;
import org.gudy.azureus2.plugins.utils.*;
import org.gudy.azureus2.plugins.utils.security.SEPublicKey;
import org.gudy.azureus2.plugins.utils.security.SEPublicKeyLocator;
import org.gudy.azureus2.plugins.utils.security.SESecurityManager;
import org.gudy.azureus2.pluginsimpl.local.PluginCoreUtils;

import com.aelitis.azureus.core.AzureusCoreFactory;
import com.aelitis.azureus.core.security.*;
import com.aelitis.azureus.core.tag.Tag;
import com.aelitis.azureus.core.tag.TagManagerFactory;
import com.aelitis.azureus.core.tag.TagType;
import com.aelitis.azureus.core.util.CopyOnWriteList;
import com.aelitis.azureus.core.util.bloom.BloomFilter;
import com.aelitis.azureus.core.util.bloom.BloomFilterFactory;
import com.aelitis.azureus.core.vuzefile.VuzeFileHandler;
import com.aelitis.azureus.plugins.magnet.MagnetPlugin;
import com.aelitis.azureus.plugins.magnet.MagnetPluginProgressListener;
import com.aelitis.azureus.plugins.net.buddy.tracker.BuddyPluginTracker;

public class
BuddyPlugin
	implements Plugin
{
	public static final boolean SUPPORT_ONLINE_STATUS		= true;

	public static final int VERSION_INITIAL	= 1;
	public static final int VERSION_CHAT	= 2;
	public static final int VERSION_CURRENT	= VERSION_CHAT;


	public static final int MT_V3_CHAT		= 1;

	private static final int FEED_UPDATE_MIN_MILLIS	= 6*60*60*1000;

	public static final int MAX_MESSAGE_SIZE	= 4*1024*1024;

	public static final int	SUBSYSTEM_INTERNAL	= 0;
	public static final int	SUBSYSTEM_AZ2		= 1;
	public static final int	SUBSYSTEM_AZ3		= 2;

	protected static final int	SUBSYSTEM_MSG_TYPE_BASE	= 1024;

	public static final int STATUS_ONLINE			= 0;
	public static final int STATUS_AWAY				= 1;
	public static final int STATUS_NOT_AVAILABLE	= 2;
	public static final int STATUS_BUSY				= 3;
	public static final int STATUS_APPEAR_OFFLINE	= 4;

	public static final String[] STATUS_VALUES 	= { "0", "1", "2", "3", "4" };

	public static final String[] STATUS_KEYS = {
		"os_online", "os_away", "os_not_avail", "os_busy", "os_offline"
	};

	public static final String[] STATUS_STRINGS = new String[ STATUS_KEYS.length ];

	protected static final int RT_INTERNAL_REQUEST_PING		= 1;
	protected static final int RT_INTERNAL_REPLY_PING		= 2;
	protected static final int RT_INTERNAL_REQUEST_CLOSE	= 3;
	protected static final int RT_INTERNAL_REPLY_CLOSE		= 4;
	protected static final int RT_INTERNAL_FRAGMENT			= 5;

	protected static final boolean TRACE = false;

	private static final String VIEW_ID = "azbuddy";

	private static final int	INIT_UNKNOWN		= 0;
	private static final int	INIT_OK				= 1;
	private static final int	INIT_BAD			= 2;

	private static final int	MAX_UNAUTH_BUDDIES	= 16;

	public static final int	TIMER_PERIOD	= 10*1000;

	private static final int	BUDDY_STATUS_CHECK_PERIOD_MIN	= 3*60*1000;
	private static final int	BUDDY_STATUS_CHECK_PERIOD_INC	= 1*60*1000;

	protected static final int	STATUS_REPUBLISH_PERIOD		= 10*60*1000;
	private static final int	STATUS_REPUBLISH_TICKS		= STATUS_REPUBLISH_PERIOD/TIMER_PERIOD;

	private static final int	CHECK_YGM_PERIOD			= 5*60*1000;
	private static final int	CHECK_YGM_TICKS				= CHECK_YGM_PERIOD/TIMER_PERIOD;
	private static final int	YGM_BLOOM_LIFE_PERIOD		= 60*60*1000;
	private static final int	YGM_BLOOM_LIFE_TICKS		= YGM_BLOOM_LIFE_PERIOD/TIMER_PERIOD;

	private static final int	SAVE_CONFIG_PERIOD			= 60*1000;
	private static final int	SAVE_CONFIG_TICKS			= SAVE_CONFIG_PERIOD/TIMER_PERIOD;

	public static final int		PERSISTENT_MSG_RETRY_PERIOD		= 5*60*1000;
	private static final int	PERSISTENT_MSG_CHECK_PERIOD		= 60*1000;
	private static final int	PERSISTENT_MSG_CHECK_TICKS		= PERSISTENT_MSG_CHECK_PERIOD/TIMER_PERIOD;

	private static final int	UNAUTH_BLOOM_RECREATE		= 120*1000;
	private static final int	UNAUTH_BLOOM_CHUNK			= 1000;

	private static final int	BLOOM_CHECK_PERIOD			= UNAUTH_BLOOM_RECREATE/2;
	private static final int	BLOOM_CHECK_TICKS			= BLOOM_CHECK_PERIOD/TIMER_PERIOD;

	public static final int STREAM_CRYPTO 	= MessageManager.STREAM_ENCRYPTION_RC4_REQUIRED;
	public static final int BLOCK_CRYPTO	= SESecurityManager.BLOCK_ENCRYPTION_AES;

	//public static final int STREAM_CRYPTO 	= MessageManager.STREAM_ENCRYPTION_NONE;
	//public static final int BLOCK_CRYPTO	= SESecurityManager.BLOCK_ENCRYPTION_NONE;

	private volatile int	 initialisation_state = INIT_UNKNOWN;

	private PluginInterface	plugin_interface;

	private LoggerChannel	logger;

	private BooleanParameter 		classic_enabled_param;
	private StringParameter 		nick_name_param;
	private StringListParameter 	online_status_param;
	private BooleanParameter 		enable_chat_notifications;
	private StringParameter 		cat_pub;


	private BooleanParameter 		beta_enabled_param;


	private boolean			ready_to_publish;
	private publishDetails	current_publish		= new publishDetails();
	private publishDetails	latest_publish		= current_publish;
	private long			last_publish_start;
	private TimerEvent		republish_delay_event;

	private BloomFilter		unauth_bloom;
	private long			unauth_bloom_create_time;
	private BloomFilter	ygm_unauth_bloom;


	private AsyncDispatcher	publish_dispatcher = new AsyncDispatcher();

	private	DistributedDatabase 	ddb;

	private CryptoHandler ecc_handler = CryptoManagerFactory.getSingleton().getECCHandler();

	private List<BuddyPluginBuddy>	buddies 	= new ArrayList<BuddyPluginBuddy>();

	private List<BuddyPluginBuddy>	connected_at_close;

	private Map<String,BuddyPluginBuddy>		buddies_map	= new HashMap<String,BuddyPluginBuddy>();

	private CopyOnWriteList<BuddyPluginListener>				listeners 			= new CopyOnWriteList<BuddyPluginListener>();
	private CopyOnWriteList<BuddyPluginBuddyRequestListener>	request_listeners	= new CopyOnWriteList<BuddyPluginBuddyRequestListener>();

	private SESecurityManager	sec_man;

	private GenericMessageRegistration	msg_registration;

	private RateLimiter	inbound_limiter;

	private RateLimiter	outbound_limiter;

	private boolean		config_dirty;

	private Random	random = RandomUtils.SECURE_RANDOM;

	private BuddyPluginAZ2		az2_handler;

	private List<DistributedDatabaseContact>	publish_write_contacts = new ArrayList<DistributedDatabaseContact>();

	private int		status_seq;

	{
		while (status_seq == 0) {

			status_seq = random.nextInt();
		}
	}

	private Set<BuddyPluginBuddy>			pd_preinit		= new HashSet<BuddyPluginBuddy>();

	private List<BuddyPluginBuddy>			pd_queue 		= new ArrayList<BuddyPluginBuddy>();
	private AESemaphore						pd_queue_sem	= new AESemaphore("BuddyPlugin:persistDispatch");
	private AEThread2						pd_thread;

	private boolean		bogus_ygm_written;

	private BuddyPluginTracker	buddy_tracker;

	private TorrentAttribute	ta_category;

	private Set<String>	public_tags_or_categories = new HashSet<String>();


	private BuddyPluginBeta		beta_plugin;

	private BuddyPluginViewInterface	swt_ui;

	public static void
	load(
		PluginInterface		plugin_interface) {
		String name =
			plugin_interface.getUtilities().getLocaleUtilities().getLocalisedMessageText("Views.plugins." + VIEW_ID + ".title");

		plugin_interface.getPluginProperties().setProperty("plugin.version", 	"1.0");
		plugin_interface.getPluginProperties().setProperty("plugin.name", 		name);
	}

	public void initialize(
		final PluginInterface		_plugin_interface) {
		plugin_interface	= _plugin_interface;


		ta_category		= plugin_interface.getTorrentManager().getAttribute(TorrentAttribute.TA_CATEGORY);

		az2_handler = new BuddyPluginAZ2(this);

		sec_man = plugin_interface.getUtilities().getSecurityManager();

		logger = plugin_interface.getLogger().getChannel("Friends");

		logger.setDiagnostic();

		final LocaleUtilities lu = plugin_interface.getUtilities().getLocaleUtilities();

		lu.addListener(
			new LocaleListener() {
				public void localeChanged(
					Locale		l) {
					updateLocale(lu);
				}
			});

		updateLocale(lu);

		BasicPluginConfigModel config = plugin_interface.getUIManager().createBasicPluginConfigModel("Views.plugins." + VIEW_ID + ".title");

			// enabled

		classic_enabled_param = config.addBooleanParameter2("azbuddy.enabled", "azbuddy.enabled", false);

			// nickname

		nick_name_param = config.addStringParameter2("azbuddy.nickname", "azbuddy.nickname", "");

		nick_name_param.setGenerateIntermediateEvents(false);

		nick_name_param.addListener(
				new ParameterListener() {
					public void
					parameterChanged(
						Parameter	param) {
						updateNickName( nick_name_param.getValue());
					}
				});

			// online status

		String[]	os_values 	= STATUS_VALUES;
		String[]	os_labels	= STATUS_STRINGS;

		online_status_param = config.addStringListParameter2(
				"azbuddy.online_status", "azbuddy.online_status",
				os_values,
				os_labels,
				os_values[0]);

		online_status_param.addListener(
				new ParameterListener() {
					public void
					parameterChanged(
						Parameter	param) {
						 int status = Integer.parseInt( online_status_param.getValue());

						 updateOnlineStatus(status);
					}
				});

		online_status_param.setVisible(SUPPORT_ONLINE_STATUS); // If we add this then use proper message texts in the STATUS_STRINGS

			// protocol speed

		final IntParameter	protocol_speed = config.addIntParameter2("azbuddy.protocolspeed", "azbuddy.protocolspeed", 32);

		protocol_speed.setMinimumRequiredUserMode(Parameter.MODE_ADVANCED);

		ConnectionManager cman = plugin_interface.getConnectionManager();

		int inbound_limit = protocol_speed.getValue()*1024;

		inbound_limiter 	= cman.createRateLimiter("buddy_up", inbound_limit);
		outbound_limiter 	= cman.createRateLimiter("buddy_down", 0);

		protocol_speed.addListener(
				new ParameterListener() {
					public void
					parameterChanged(
						Parameter	param) {
						inbound_limiter.setRateLimitBytesPerSecond(protocol_speed.getValue()*1024);
					}
				});

			// chat notifications

		enable_chat_notifications = config.addBooleanParameter2("azbuddy.enable_chat_notif", "azbuddy.enable_chat_notif", true);

			// default published tags or cats

		cat_pub = config.addStringParameter2("azbuddy.enable_cat_pub", "azbuddy.enable_cat_pub", "");

		cat_pub.setGenerateIntermediateEvents(false);

		setPublicTagsOrCategories(cat_pub.getValue(), false);

		final BooleanParameter tracker_enable = config.addBooleanParameter2("azbuddy.tracker.enabled", "azbuddy.tracker.enabled", true);

		cat_pub.addListener(
			new ParameterListener() {
				public void parameterChanged(
					Parameter 	param) {
					setPublicTagsOrCategories( cat_pub.getValue(), false);
				}
			});

		config.createGroup(
			"label.classic",
			new Parameter[]{
					classic_enabled_param, nick_name_param, online_status_param,
					protocol_speed, enable_chat_notifications, cat_pub, tracker_enable
			});

			// decentralised stuff


		beta_enabled_param = config.addBooleanParameter2("azbuddy.dchat.decentralized.enabled", "azbuddy.dchat.decentralized.enabled", true);


		config.createGroup(
				"azbuddy.dchat.decentralized",
				new Parameter[]{
						beta_enabled_param,
				});



			// config end

		beta_plugin = new BuddyPluginBeta(plugin_interface, this, beta_enabled_param);

		final TableContextMenuItem menu_item_itorrents =
			plugin_interface.getUIManager().getTableManager().addContextMenuItem(TableManager.TABLE_MYTORRENTS_INCOMPLETE, "azbuddy.contextmenu");
		final TableContextMenuItem menu_item_ctorrents 	=
			plugin_interface.getUIManager().getTableManager().addContextMenuItem(TableManager.TABLE_MYTORRENTS_COMPLETE, "azbuddy.contextmenu");

		menu_item_itorrents.setStyle(TableContextMenuItem.STYLE_MENU);
		menu_item_ctorrents.setStyle(TableContextMenuItem.STYLE_MENU);

		MenuItemFillListener	menu_fill_listener =
			new MenuItemFillListener() {
				public void
				menuWillBeShown(
					MenuItem	menu,
					Object		_target) {
					menu.removeAllChildItems();

					if (!( isClassicEnabled() && isAvailable())) {

						menu.setEnabled(false);

						return;
					}

					final List<Torrent>	torrents = new ArrayList<Torrent>();

					if (_target instanceof TableRow) {

						addDownload(torrents, (TableRow)_target);

					} else {

						TableRow[] rows = (TableRow[])_target;

						for (TableRow row: rows) {

							addDownload(torrents, row);
						}
					}

					if (torrents.size() == 0) {

						menu.setEnabled(false);

					} else {

						List<BuddyPluginBuddy> buddies = getBuddies();

						boolean	incomplete = ((TableContextMenuItem)menu).getTableID() == TableManager.TABLE_MYTORRENTS_INCOMPLETE;

						TableContextMenuItem parent = incomplete?menu_item_itorrents:menu_item_ctorrents;

						for (int i=0;i<buddies.size();i++) {

							final BuddyPluginBuddy	buddy = (BuddyPluginBuddy)buddies.get(i);

							if (buddy.isOnline( true)) {

								TableContextMenuItem item =
									plugin_interface.getUIManager().getTableManager().addContextMenuItem(
										parent,
										"!" + buddy.getName() + "!");

								item.addMultiListener(
									new MenuItemListener() {
										public void selected(
											MenuItem 	menu,
											Object 		target) {
											for (Torrent torrent: torrents) {

												az2_handler.sendAZ2Torrent(torrent, buddy);
											}
										}
									});
							}
						}

						menu.setEnabled(true);
					}
				}

				protected void
				addDownload(
					List<Torrent>		torrents,
					TableRow			row) {
					Object obj = row.getDataSource();

					Download	download;

					if (obj instanceof Download) {

						download = (Download)obj;

					} else {

						DiskManagerFileInfo file = (DiskManagerFileInfo)obj;

						try {
							download	= file.getDownload();

						} catch (DownloadException e) {

							Debug.printStackTrace(e);

							return;
						}
					}

					Torrent torrent = download.getTorrent();

					if (torrent != null && !TorrentUtils.isReallyPrivate( PluginCoreUtils.unwrap( torrent))) {

						torrents.add(torrent);
					}
				}
			};

		menu_item_itorrents.addFillListener(menu_fill_listener);
		menu_item_ctorrents.addFillListener(menu_fill_listener);

		buddy_tracker = new BuddyPluginTracker(this, tracker_enable);

		plugin_interface.getUIManager().addUIListener(
			new UIManagerListener() {
				public void
				UIAttached(
					final UIInstance		instance) {
					if (instance.getUIType() == UIInstance.UIT_SWT) {

						try {
							swt_ui = (BuddyPluginViewInterface)Class.forName("com.aelitis.azureus.plugins.net.buddy.swt.BuddyPluginView").getConstructor(
								new Class[]{ BuddyPlugin.class, UIInstance.class, String.class } ).newInstance(
									new Object[]{ BuddyPlugin.this, instance, VIEW_ID });

							// new BuddyPluginView(BuddyPlugin.this, swt_ui, VIEW_ID);

						} catch (Throwable e) {
							e.printStackTrace();
						}
					}

					setupDisablePrompt(instance);
				}

				public void
				UIDetached(
					UIInstance		instance) {
				}
			});

		ParameterListener enabled_listener =
			new ParameterListener() {
				public void
				parameterChanged(
					Parameter	param) {
					boolean classic_enabled = classic_enabled_param.getValue();

					nick_name_param.setEnabled(classic_enabled);
					online_status_param.setEnabled(classic_enabled);
					protocol_speed.setEnabled(classic_enabled);
					enable_chat_notifications.setEnabled(classic_enabled);
					cat_pub.setEnabled(classic_enabled);
					tracker_enable.setEnabled(classic_enabled);

						// only toggle overall state on a real change

					if (param != null) {

						setClassicEnabledInternal(classic_enabled);
						fireEnabledStateChanged();
					}

					boolean beta_enabled = beta_enabled_param.getValue();
				}
			};

		enabled_listener.parameterChanged(null);

		classic_enabled_param.addListener(enabled_listener);
		beta_enabled_param.addListener(enabled_listener);

		loadConfig();

		registerMessageHandler();

		plugin_interface.addListener(
			new PluginListener() {
				public void
				initializationComplete() {
					final DelayedTask dt = plugin_interface.getUtilities().createDelayedTask(new Runnable() {
							public void run() {
								new AEThread2("BuddyPlugin:init", true) {
									public void
									run() {
										startup();

										beta_plugin.startup();
									}
								}.start();
							}
						});

					dt.queue();
				}

				public void
				closedownInitiated() {
					saveConfig(true);

					closedown();

					beta_plugin.closedown();
				}

				public void
				closedownComplete() {
				}
			});
	}

	protected void
	updateLocale(
		LocaleUtilities	lu) {
		for ( int i=0;i<STATUS_STRINGS.length;i++) {

			STATUS_STRINGS[i] = lu.getLocalisedMessageText("azbuddy." + STATUS_KEYS[i]);
		}

		if (online_status_param != null) {

			online_status_param.setLabels(STATUS_STRINGS);
		}
	}

	/**
	 *
	 *
	 * @since 3.0.5.3
	 */
	protected void
	setupDisablePrompt(
			final UIInstance ui) {
		if (plugin_interface == null) {
			return;
		}

		String enabledConfigID = "PluginInfo." + plugin_interface.getPluginID()
				+ ".enabled";
		COConfigurationManager.addParameterListener(enabledConfigID,
				new org.gudy.azureus2.core3.config.ParameterListener() {
					public void parameterChanged(String parameterName) {
						fireEnabledStateChanged();
					}
				});
	}

	public void showConfig() {
		plugin_interface.getUIManager().showConfigSection("Views.plugins." + VIEW_ID + ".title");
	}

	protected void
	startup() {
		try {
			ddb = plugin_interface.getDistributedDatabase();

			if (!ddb.isAvailable()) {

				throw (new Exception("DDB Unavailable"));
			}
				// pick up initial values before enabling

			ddb.addListener(
				new DistributedDatabaseListener() {
					public void event(
						DistributedDatabaseEvent event) {
						if (event.getType() == DistributedDatabaseEvent.ET_LOCAL_CONTACT_CHANGED) {

							updateIP();
						}
					}
				});

			updateIP();

			updateNickName( nick_name_param.getValue());

			updateOnlineStatus( Integer.parseInt( online_status_param.getValue()));

			COConfigurationManager.addAndFireParameterListeners(
					new String[]{
						"TCP.Listen.Port",
						"TCP.Listen.Port.Enable",
						"UDP.Listen.Port",
						"UDP.Listen.Port.Enable" },
					new org.gudy.azureus2.core3.config.ParameterListener() {
						public void parameterChanged(String parameterName) {
							updateListenPorts();
						}
					});

			CryptoManagerFactory.getSingleton().addKeyListener(
				new CryptoManagerKeyListener() {
					public void keyChanged(
						CryptoHandler handler) {
						updateKey();
					}

					public void
					keyLockStatusChanged(
						CryptoHandler		handler) {
						boolean unlocked = handler.isUnlocked();

						if (unlocked) {

							if (latest_publish.isEnabled()) {

								updatePublish(latest_publish);
							}
						} else {

							new AEThread2("BuddyPlugin:disc", true) {
								public void
								run() {
									List buddies = getAllBuddies();

									for (int i=0;i<buddies.size();i++) {

										((BuddyPluginBuddy)buddies.get(i)).disconnect();
									}
								}
							}.start();
						}
					}
				});

			ready_to_publish	= true;

			setClassicEnabledInternal( classic_enabled_param.getValue());

			checkBuddiesAndRepublish();

			fireClassicInitialised(true);

				// try to re-establish connection to previously connectd buddies

			List<BuddyPluginBuddy> buddies = getBuddies();

			for (BuddyPluginBuddy buddy: buddies) {

				if (buddy.getIP() != null && !buddy.isConnected()) {

					log("Attempting reconnect to " + buddy.getString());

					buddy.sendKeepAlive();
				}
			}

		} catch (Throwable e) {

			log("Initialisation failed", e);

			fireClassicInitialised(false);
		}
	}

	public boolean isClassicEnabled() {
		if (classic_enabled_param == null) {return false;}

		return ( classic_enabled_param.getValue());
	}

	public void setClassicEnabled(
		boolean		enabled) {
		if (classic_enabled_param == null) {return;}
		classic_enabled_param.setValue(enabled);
	}

	protected void
	setClassicEnabledInternal(
		boolean		_enabled) {
		synchronized(this) {

			if (latest_publish.isEnabled() != _enabled) {

				publishDetails new_publish = latest_publish.getCopy();

				new_publish.setEnabled(_enabled);

				updatePublish(new_publish);
			}
		}
	}

	public boolean isBetaEnabled() {
		if (beta_enabled_param == null) {

			return (false);
		}

		return ( beta_enabled_param.getValue());
	}

	public BuddyPluginBeta getBeta() {
		return (beta_plugin);
	}

	public BuddyPluginTracker getTracker() {
		return (buddy_tracker);
	}

	public String getNickname() {
		return (  nick_name_param.getValue());
	}

	public void setNickname(String	str) {
		nick_name_param.setValue(str);
	}

	public void setOnlineStatus(
		int		status) {
		online_status_param.setValue("" + status);
	}

	public int getOnlineStatus() {
		return ( latest_publish.getOnlineStatus());
	}

	public BooleanParameter getEnableChatNotificationsParameter() {
		return (enable_chat_notifications);
	}

	protected String
	normaliseCat(String		str) {
		if (str == null) {

			return (null);

		} else if (str.toLowerCase().equals("all")) {

			return ("All");

		} else {

			return (str);
		}
	}

	protected void
	normaliseCats(
		Set<String>	cats) {
		if (cats != null) {

			boolean	all_found = false;

			Iterator<String> it = cats.iterator();

			while (it.hasNext()) {

				if (it.next().toLowerCase().equals("all")) {

					it.remove();

					all_found = true;
				}
			}

			if (all_found) {

				cats.add("All");
			}
		}
	}

	public boolean isPublicTagOrCategory(String	cat) {
		cat = normaliseCat(cat);

		return (public_tags_or_categories.contains( cat));
	}

	public void addPublicTagOrCategory(String	cat) {
		cat = normaliseCat(cat);

		Set<String> new_cats = new HashSet(public_tags_or_categories);

		if (new_cats.add( cat)) {

			setPublicTagsOrCategories(new_cats, true);
		}
	}

	public void removePublicTagOrCategory(String	cat) {
		cat = normaliseCat(cat);

		Set<String> new_cats = new HashSet(public_tags_or_categories);

		if (new_cats.remove( cat)) {

			setPublicTagsOrCategories(new_cats, true);
		}
	}

	protected void
	setPublicTagsOrCategories(String	str,
		boolean	persist) {
		Set<String>	new_pub_cats = new HashSet<String>();

		String[]	bits = str.split(",");

		for (String s: bits) {

			s = s.trim();

			if (bits.length > 0) {

				new_pub_cats.add(normaliseCat( s));
			}
		}

		setPublicTagsOrCategories(new_pub_cats, persist);
	}

	protected void
	setPublicTagsOrCategories(
		Set<String>	new_pub_tags_or_cats,
		boolean		persist) {
		if (!public_tags_or_categories.equals(new_pub_tags_or_cats)) {

			Set<String> removed = new HashSet<String>(public_tags_or_categories);

			removed.removeAll(new_pub_tags_or_cats);

			public_tags_or_categories = new_pub_tags_or_cats;

			if (persist) {

				String cat_str = "";

				for (String s: public_tags_or_categories) {

					cat_str += (cat_str.length()==0?"":",") + s;
				}

				cat_pub.setValue(cat_str);
			}

			List<BuddyPluginBuddy> buds = getBuddies();

			for (BuddyPluginBuddy b: buds) {

				Set<String> local = b.getLocalAuthorisedRSSTagsOrCategories();

				if (local != null || new_pub_tags_or_cats.size() > 0) {

					if (local == null) {

						local = new HashSet<String>();

					} else {

							// gotta clone else we're messing with stuff that ain't ours

						local = new HashSet<String>(local);
					}

					local.addAll(new_pub_tags_or_cats);

					local.removeAll(removed);

					b.setLocalAuthorisedRSSTagsOrCategories(local);
				}
			}
		}
	}

	protected void
	registerMessageHandler() {
		try {
			addRequestListener(
				new BuddyPluginBuddyRequestListener() {
					public Map
					requestReceived(
						BuddyPluginBuddy	from_buddy,
						int					subsystem,
						Map					request )

						throws BuddyPluginException
					{
						if (subsystem == SUBSYSTEM_INTERNAL) {

							if (!from_buddy.isAuthorised()) {

								throw (new BuddyPluginException("Unauthorised"));
							}

							return (processInternalRequest( from_buddy, request));
						}

						return (null);
					}

					public void
					pendingMessages(
						BuddyPluginBuddy[]	from_buddies) {
					}
				});

			msg_registration =
				plugin_interface.getMessageManager().registerGenericMessageType(
					"AZBUDDY", "Buddy message handler",
					STREAM_CRYPTO,
					new GenericMessageHandler() {
						public boolean
						accept(
							GenericMessageConnection	connection )

							throws MessageException
						{
							if (!isClassicEnabled()) {

								return (false);
							}

							final String originator = connection.getEndpoint().getNotionalAddress().getAddress().getHostAddress();

							if (TRACE) {
								System.out.println("accept " + originator);
							}

							try {
								String reason = "Friend: Incoming connection establishment (" + originator + ")";

								addRateLimiters(connection);

								connection =
									sec_man.getSTSConnection(
											connection,
											sec_man.getPublicKey(SEPublicKey.KEY_TYPE_ECC_192, reason),
											new SEPublicKeyLocator() {
												public boolean
												accept(
													Object		context,
													SEPublicKey	other_key) {
													String	other_key_str = Base32.encode( other_key.encodeRawPublicKey());

													if (TRACE) {
														System.out.println("Incoming: acceptKey - " + other_key_str);
													}

													try {
														synchronized(BuddyPlugin.this) {

															int	unauth_count = 0;

															for (int i=0;i<buddies.size();i++) {

																BuddyPluginBuddy	buddy = (BuddyPluginBuddy)buddies.get(i);

																if (buddy.getPublicKey().equals( other_key_str)) {

																		// don't accept a second or subsequent connection for unauth buddies
																		// as they have a single chance to be processed

																	if (!buddy.isAuthorised()) {

																		log("Incoming connection from " + originator + " failed as for unauthorised buddy");

																		return (false);
																	}

																	buddy.incomingConnection((GenericMessageConnection)context);

																	return (true);
																}

																if (!buddy.isAuthorised()) {

																	unauth_count++;
																}
															}

																// no existing authorised buddy

															if (unauth_count < MAX_UNAUTH_BUDDIES) {

																if (tooManyUnauthConnections( originator)) {

																	log("Too many recent unauthorised connections from " + originator);

																	return (false);
																}

																BuddyPluginBuddy buddy = addBuddy(other_key_str, SUBSYSTEM_AZ2, false);

																if (buddy != null) {

																	buddy.incomingConnection((GenericMessageConnection)context);

																	return (true);

																} else {

																	return (false);
																}
															}
														}

														log("Incoming connection from " + originator + " failed due to pk mismatch");

														return (false);

													} catch (Throwable e) {

														log("Incomming connection from " + originator + " failed", e);

														return (false);
													}
												}
											},
											reason,
											BLOCK_CRYPTO);

							} catch (Throwable e) {

								connection.close();

								log("Incoming connection from " + originator + " failed", e);
							}

							return (true);
						}
					});

		} catch (Throwable e) {

			log("Failed to register message listener", e);
		}
	}

	protected void
	addRateLimiters(
		GenericMessageConnection	connection) {
		connection.addInboundRateLimiter(inbound_limiter);
		connection.addOutboundRateLimiter(outbound_limiter);
	}

	protected boolean
	tooManyUnauthConnections(String	originator) {
		synchronized(this) {

			if (unauth_bloom == null) {

				unauth_bloom = BloomFilterFactory.createAddRemove4Bit(UNAUTH_BLOOM_CHUNK);

				unauth_bloom_create_time	= SystemTime.getCurrentTime();
			}

			int	hit_count = unauth_bloom.add( originator.getBytes());

			if (hit_count >= 8) {

				Debug.out("Too many recent unauthorised connection attempts from " + originator);

				return (true);
			}

			return (false);
		}
	}

	protected void
	checkUnauthBloom() {
		synchronized(this) {

			if (unauth_bloom != null) {

				long	now = SystemTime.getCurrentTime();

				if (now < unauth_bloom_create_time) {

					unauth_bloom_create_time = now;

				} else if (now - unauth_bloom_create_time > UNAUTH_BLOOM_RECREATE) {

					unauth_bloom = null;
				}
			}
		}
	}

	protected void
	checkMaxMessageSize(
		int		size )

		throws BuddyPluginException
	{
		if (size > MAX_MESSAGE_SIZE) {

			throw (new BuddyPluginException("Message is too large to send, limit is " + DisplayFormatters.formatByteCountToKiBEtc( MAX_MESSAGE_SIZE)));
		}
	}

	protected void
	checkPersistentDispatch() {
		List	buddies_copy;

		synchronized(this) {

			buddies_copy = new ArrayList(buddies);
		}

		for (int i=0;i<buddies_copy.size();i++) {

			BuddyPluginBuddy	buddy = (BuddyPluginBuddy)buddies_copy.get(i);

			buddy.checkPersistentDispatch();
		}
	}

	protected void
	persistentDispatchInit() {
		Iterator it = pd_preinit.iterator();

		while (it.hasNext()) {

			persistentDispatchPending((BuddyPluginBuddy)it.next());
		}

		pd_preinit = null;
	}

	protected void
	persistentDispatchPending(
		BuddyPluginBuddy	buddy) {
		synchronized(pd_queue) {

			if (initialisation_state == INIT_UNKNOWN) {

				pd_preinit.add(buddy);

				return;
			}

			if (!pd_queue.contains( buddy)) {

				pd_queue.add(buddy);

				pd_queue_sem.release();

				if (pd_thread == null) {

					pd_thread =
						new AEThread2("BuddyPlugin:persistDispatch", true) {
							public void
							run() {
								while (true) {

									if (!pd_queue_sem.reserve( 30*1000)) {

										synchronized(pd_queue) {

											if (pd_queue.isEmpty()) {

												pd_thread	= null;

												break;
											}
										}
									} else {

										BuddyPluginBuddy	buddy;

										synchronized(pd_queue) {

											buddy = (BuddyPluginBuddy)pd_queue.remove(0);
										}

										buddy.persistentDispatch();
									}
								}
							}
						};

					pd_thread.start();
				}
			}
		}
	}

	protected Map
	processInternalRequest(
		BuddyPluginBuddy	from_buddy,
		Map					request )

		throws BuddyPluginException
	{
		int	type = ((Long)request.get("type")).intValue();

		if (type == RT_INTERNAL_REQUEST_PING) {

			Map	reply = new HashMap();

			reply.put("type", new Long( RT_INTERNAL_REPLY_PING));

			return (reply);

		} else if (type == RT_INTERNAL_REQUEST_CLOSE) {

			from_buddy.receivedCloseRequest(request);

			Map	reply = new HashMap();

			reply.put("type", new Long( RT_INTERNAL_REPLY_CLOSE));

			return (reply);

		} else {

			throw (new BuddyPluginException("Unrecognised request type " + type));
		}
	}

	protected void
	updateListenPorts() {
		synchronized(this) {

			int	tcp_port = COConfigurationManager.getIntParameter("TCP.Listen.Port");
			boolean	tcp_enabled = COConfigurationManager.getBooleanParameter("TCP.Listen.Port.Enable");
			int	udp_port = COConfigurationManager.getIntParameter("UDP.Listen.Port");
			boolean	udp_enabled = COConfigurationManager.getBooleanParameter("UDP.Listen.Port.Enable");

			if (!tcp_enabled) {

				tcp_port = 0;
			}

			if (!udp_enabled) {

				udp_port = 0;
			}

			if (	latest_publish.getTCPPort() != tcp_port ||
					latest_publish.getUDPPort() != udp_port) {

				publishDetails new_publish = latest_publish.getCopy();

				new_publish.setTCPPort(tcp_port);
				new_publish.setUDPPort(udp_port);

				updatePublish(new_publish);
			}
		}
	}

	protected void
	updateIP() {
		if (ddb == null || !ddb.isAvailable()) {

			return;
		}

		synchronized(this) {

			InetAddress public_ip = ddb.getLocalContact().getAddress().getAddress();

			if (	latest_publish.getIP() == null ||
					!latest_publish.getIP().equals(public_ip)) {

				publishDetails new_publish = latest_publish.getCopy();

				new_publish.setIP(public_ip);

				updatePublish(new_publish);
			}
		}
	}

	protected void
	updateNickName(String		new_nick) {
		new_nick = new_nick.trim();

		if (new_nick.length() == 0) {

			new_nick = null;
		}

		synchronized(this) {

			String	old_nick = latest_publish.getNickName();

			if (!stringsEqual(new_nick, old_nick)) {

				publishDetails new_publish = latest_publish.getCopy();

				new_publish.setNickName(new_nick);

				updatePublish(new_publish);
			}
		}
	}

	protected void
	updateOnlineStatus(
		int		new_status) {
		boolean	changed;

		synchronized(this) {

			int	old_status = latest_publish.getOnlineStatus();

			changed = old_status != new_status;

			if (changed) {

				publishDetails new_publish = latest_publish.getCopy();

				new_publish.setOnlineStatus(new_status);

				updatePublish(new_publish);
			}
		}

		if (changed) {

			List	buddies = getAllBuddies();

			for (int i=0;i<buddies.size();i++) {

				BuddyPluginBuddy	buddy = (BuddyPluginBuddy)buddies.get(i);

				if (buddy.isConnected()) {

					buddy.sendKeepAlive();
				}
			}
		}
	}

	public String getOnlineStatus(
		int		status) {
		if (status >= STATUS_STRINGS.length || status < 0) {

			status = 0;
		}

		return (STATUS_STRINGS[status]);
	}

	protected boolean
	stringsEqual(String	s1,
		String	s2) {
		if (s1 == null && s2 == null) {

			return (true);
		}

		if (s1 == null || s2 == null) {

			return (false);
		}

		return (s1.equals( s2));
	}

	protected void
	updateKey() {
		synchronized(this) {

			publishDetails new_publish = latest_publish.getCopy();

			new_publish.setPublicKey(null);

			updatePublish(new_publish);
		}
	}

	protected void
	updatePublish(
		final publishDetails	details) {
		latest_publish = details;

		if (ddb == null || !ready_to_publish) {

			return;
		}

		publish_dispatcher.dispatch(
			new AERunnable() {
				public void
				runSupport() {
						// only execute the most recent publish

					if (publish_dispatcher.getQueueSize() > 0) {

						return;
					}

					updatePublishSupport(details);
				}
			});
	}

	protected void
	updatePublishSupport(
		publishDetails	details) {
		byte[]	key_to_remove = null;

		publishDetails	existing_details;

		boolean	log_this;

		synchronized(this) {

			log_this = !current_publish.getString().equals( details.getString());

			existing_details = current_publish;

			if (!details.isEnabled()) {

				if (current_publish.isPublished()) {

					key_to_remove	= current_publish.getPublicKey();
				}
			} else {

				if (details.getPublicKey() == null) {

					try {
						details.setPublicKey(ecc_handler.getPublicKey("Creating online status key"));

					} catch (Throwable e) {

						log("Failed to publish details", e);

						return;
					}
				}

				if (current_publish.isPublished()) {

					byte[]	existing_key = current_publish.getPublicKey();

					if (!Arrays.equals( existing_key, details.getPublicKey())) {

						key_to_remove = existing_key;
					}
				}
			}

			current_publish = details;
		}

		if (key_to_remove != null) {

			log("Removing old status publish: " + existing_details.getString());

			try {
				ddb.delete(
					new DistributedDatabaseListener() {
						public void
						event(
							DistributedDatabaseEvent		event) {
						}
					},
					getStatusKey(key_to_remove, "Friend status de-registration for old key"));

			} catch (Throwable e) {

				log("Failed to remove existing publish", e);
			}
		}

		if (details.isEnabled()) {

				// ensure we have a sensible ip

			InetAddress ip = details.getIP();

			if (ip.isLoopbackAddress() || ip.isLinkLocalAddress() || ip.isSiteLocalAddress()) {

				log("Can't publish as ip address is invalid: " + details.getString());

				return;
			}

			details.setPublished(true);

			Map	payload = new HashMap();

			if (details.getTCPPort() > 0) {

				payload.put("t", new Long(  details.getTCPPort()));
			}

			if (details.getUDPPort() > 0) {

				payload.put("u", new Long( details.getUDPPort()));
			}

			payload.put("i", ip.getAddress());

			String	nick = details.getNickName();

			if (nick != null) {

				if (nick.length() > 32) {

					nick = nick.substring(0, 32);
				}

				payload.put("n", nick);
			}

			payload.put("o", new Long( details.getOnlineStatus()));

			int	next_seq = ++status_seq;

			if (next_seq == 0) {

				next_seq = ++status_seq;
			}

			details.setSequence(next_seq);

			payload.put("s", new Long( next_seq));

			payload.put("v", new Long( VERSION_CURRENT));

			boolean	failed_to_get_key = true;

			try {
				byte[] data = BEncoder.encode(payload);

				DistributedDatabaseKey	key = getStatusKey(details.getPublicKey(), "My buddy status registration " + payload);

				byte[] signature = ecc_handler.sign(data, "Friend online status");

				failed_to_get_key = false;

				byte[]	signed_payload = new byte[ 1 + signature.length + data.length ];

				signed_payload[0] = (byte)signature.length;

				System.arraycopy(signature, 0, signed_payload, 1, signature.length);
				System.arraycopy(data, 0, signed_payload, 1 + signature.length, data.length);

				DistributedDatabaseValue	value = ddb.createValue(signed_payload);

				final AESemaphore	sem = new AESemaphore("BuddyPlugin:reg");

				if (log_this) {

					logMessage("Publishing status starts: " + details.getString());
				}

				last_publish_start = SystemTime.getMonotonousTime();

				ddb.write(
					new DistributedDatabaseListener() {
						private List<DistributedDatabaseContact>	write_contacts = new ArrayList<DistributedDatabaseContact>();

						public void
						event(
							DistributedDatabaseEvent		event) {
							int	type = event.getType();

							if (type == DistributedDatabaseEvent.ET_VALUE_WRITTEN) {

								write_contacts.add( event.getContact());

							} else if (	type == DistributedDatabaseEvent.ET_OPERATION_TIMEOUT ||
										type == DistributedDatabaseEvent.ET_OPERATION_COMPLETE) {

								synchronized(publish_write_contacts) {

									publish_write_contacts.clear();

									publish_write_contacts.addAll(write_contacts);
								}

								sem.release();
							}
						}
					},
					key,
					value);

				sem.reserve();

				if (log_this) {

					logMessage("My status publish complete");
				}
			} catch (Throwable e) {

				logMessage("Failed to publish online status", e);

				if (failed_to_get_key) {

					synchronized(this) {

						if (republish_delay_event != null) {

							return;
						}

						if (	last_publish_start == 0 ||
								SystemTime.getMonotonousTime() - last_publish_start > STATUS_REPUBLISH_PERIOD) {

							log("Rescheduling publish as failed to get key");

							republish_delay_event = SimpleTimer.addEvent(
								"BuddyPlugin:republish",
								SystemTime.getCurrentTime() + 60*1000,
								new TimerEventPerformer() {
									public void perform(
										TimerEvent event) {
										synchronized(BuddyPlugin.this) {

											republish_delay_event = null;
										}

										if (	last_publish_start == 0 ||
												SystemTime.getMonotonousTime() - last_publish_start > STATUS_REPUBLISH_PERIOD) {

											if (latest_publish.isEnabled()) {

												updatePublish(latest_publish);
											}
										}
									}
								});

						}
					}
				}
			}
		}
	}

	protected int
	getCurrentStatusSeq() {
		synchronized(this) {

			return ( current_publish.getSequence());
		}
	}

	protected void
	closedown() {
		logMessage("Closing down");

		List<BuddyPluginBuddy>	buddies = getAllBuddies();

		synchronized(this) {

			connected_at_close = new ArrayList<BuddyPluginBuddy>();

			for (BuddyPluginBuddy buddy: buddies) {

				if (buddy.isConnected()) {

					connected_at_close.add(buddy);
				}
			}
		}

		if (ddb != null) {

			boolean	restarting = AzureusCoreFactory.isCoreAvailable() ? AzureusCoreFactory.getSingleton().isRestarting() : false;

			logMessage("   closing buddy connections");

			for (int i=0;i<buddies.size();i++) {

				((BuddyPluginBuddy)buddies.get(i)).sendCloseRequest(restarting);
			}

			if (!restarting) {

				logMessage("   updating online status");

				List	contacts = new ArrayList();

				synchronized(publish_write_contacts) {

					contacts.addAll(publish_write_contacts);
				}

				byte[] key_to_remove;

				synchronized(this) {

					key_to_remove	= current_publish.getPublicKey();
				}

				if (contacts.size() == 0 || key_to_remove == null) {

					return;
				}

				DistributedDatabaseContact[] contact_a = new DistributedDatabaseContact[contacts.size()];

				contacts.toArray(contact_a);

				try {
					ddb.delete(
						new DistributedDatabaseListener() {
							public void
							event(
								DistributedDatabaseEvent		event) {
								if (event.getType() == DistributedDatabaseEvent.ET_VALUE_DELETED) {

									// System.out.println("Deleted status from " + event.getContact().getName());
								}
							}
						},
						getStatusKey(key_to_remove, "Friend status de-registration for closedown"),
						contact_a);

				} catch (Throwable e) {

					log("Failed to remove existing publish", e);
				}
			}
		}
	}

	protected DistributedDatabaseKey
	getStatusKey(
		byte[]	public_key,
		String	reason )

		throws Exception
	{
		byte[]	key_prefix = "azbuddy:status".getBytes();

		byte[]	key_bytes = new byte[ key_prefix.length + public_key.length ];

		System.arraycopy(key_prefix, 0, key_bytes, 0, key_prefix.length);
		System.arraycopy(public_key, 0, key_bytes, key_prefix.length, public_key.length);

		DistributedDatabaseKey key = ddb.createKey(key_bytes, reason);

		return (key);
	}

	protected DistributedDatabaseKey
	getYGMKey(
		byte[]	public_key,
		String	reason )

		throws Exception
	{
		byte[]	key_prefix = "azbuddy:ygm".getBytes();

		byte[]	key_bytes = new byte[ key_prefix.length + public_key.length ];

		System.arraycopy(key_prefix, 0, key_bytes, 0, key_prefix.length);
		System.arraycopy(public_key, 0, key_bytes, key_prefix.length, public_key.length);

		DistributedDatabaseKey key = ddb.createKey(key_bytes, reason);

		return (key);
	}

	protected void
	setConfigDirty() {
		synchronized(this) {

			config_dirty = true;
		}
	}

	protected void
	loadConfig() {
		long	now = SystemTime.getCurrentTime();

		synchronized(this) {

			Map map = readConfig();

			List	buddies_config = (List)map.get("friends");

			if (buddies_config != null) {

				if (buddies_config.size() == 0) {

					deleteConfig();

				} else {
					for (int i=0;i<buddies_config.size();i++) {

						Object o = buddies_config.get(i);

						if (o instanceof Map) {

							Map	details = (Map)o;

							Long	l_ct = (Long)details.get("ct");

							long	created_time = l_ct==null?now:l_ct.longValue();

							if (created_time > now) {

								created_time = now;
							}

							String	key = new String((byte[])details.get("pk"));

							List	recent_ygm = (List)details.get("ygm");

							String	nick = decodeString((byte[])details.get("n"));

							Long	l_seq = (Long)details.get("ls");

							int	last_seq = l_seq==null?0:l_seq.intValue();

							Long	l_lo = (Long)details.get("lo");

							long	last_time_online = l_lo==null?0:l_lo.longValue();

							if (last_time_online > now) {

								last_time_online = now;
							}

							Long l_subsystem = (Long)details.get("ss");

							int	subsystem = l_subsystem==null?SUBSYSTEM_AZ2:l_subsystem.intValue();

							if (subsystem == SUBSYSTEM_AZ3) {
								continue;
							}

							Long l_ver = (Long)details.get("v");

							int	ver = l_ver==null?VERSION_INITIAL:l_ver.intValue();

							String	loc_cat = decodeString((byte[])details.get("lc"));
							String	rem_cat = decodeString((byte[])details.get("rc"));

							BuddyPluginBuddy buddy = new BuddyPluginBuddy(this, created_time, subsystem, true, key, nick, ver, loc_cat, rem_cat, last_seq, last_time_online, recent_ygm);

							byte[]	ip_bytes = (byte[])details.get("ip");

							if (ip_bytes != null) {

								try {
									InetAddress ip = InetAddress.getByAddress(ip_bytes);

									int	tcp_port = ((Long)details.get("tcp")).intValue();
									int	udp_port = ((Long)details.get("udp")).intValue();

									buddy.setCachedStatus(ip, tcp_port, udp_port);

								} catch (Throwable e) {
								}
							}

							logMessage("Loaded buddy " + buddy.getString());

							buddies.add(buddy);

							buddies_map.put(key, buddy);
						}
					}
				}
			}

			int	num_buddies = buddies.size();

			for (BuddyPluginBuddy b: buddies) {

				b.setInitialStatus(now, num_buddies);
			}
		}
	}

	protected String
	decodeString(
		byte[]		bytes) {
		if (bytes == null) {

			return (null);
		}

		try {
			return (new String( bytes, "UTF8"));

		} catch (Throwable e) {

			return (null);
		}
	}

	protected void
	saveConfig() {
		saveConfig(false);
	}

	protected void
	saveConfig(
		boolean	force) {
		synchronized(this) {

			if (config_dirty || force) {

				List buddies_config = new ArrayList();

				for (int i=0;i<buddies.size();i++) {

					BuddyPluginBuddy buddy = (BuddyPluginBuddy)buddies.get(i);

					if (!buddy.isAuthorised()) {

						continue;
					}

					Map	map = new HashMap();

					map.put("ct", new Long( buddy.getCreatedTime()));

					map.put("pk", buddy.getPublicKey());

					List	ygm = buddy.getYGMMarkers();

					if (ygm != null) {

						map.put("ygm", ygm);
					}

					String	nick = buddy.getNickName();

					if (nick != null) {

						map.put("n", nick);
					}

					map.put("ls", new Long( buddy.getLastStatusSeq()));

					map.put("lo", new Long( buddy.getLastTimeOnline()));

					map.put("ss", new Long( buddy.getSubsystem()));

					map.put("v", new Long( buddy.getVersion()));

					if (buddy.getLocalAuthorisedRSSTagsOrCategoriesAsString() != null) {
						map.put("lc", buddy.getLocalAuthorisedRSSTagsOrCategoriesAsString());
					}

					if (buddy.getRemoteAuthorisedRSSTagsOrCategoriesAsString() != null) {
						map.put("rc", buddy.getRemoteAuthorisedRSSTagsOrCategoriesAsString());
					}

					boolean connected =
						buddy.isConnected() ||
						(connected_at_close != null && connected_at_close.contains( buddy));

					if (connected) {

						InetAddress	ip 			= buddy.getIP();
						int			tcp_port	= buddy.getTCPPort();
						int			udp_port	= buddy.getUDPPort();

						if (ip != null) {

							map.put("ip", ip.getAddress());
							map.put("tcp", new Long( tcp_port));
							map.put("udp", new Long( udp_port));
						}
					}

					buddies_config.add(map);
				}

				Map	map = new HashMap();

				if (buddies_config.size() > 0) {

					map.put("friends", buddies_config);

					writeConfig(map);

				} else {

					deleteConfig();
				}

				config_dirty = false;
			}
		}
	}

	public BuddyPluginBuddy addBuddy(String		key,
		int			subsystem )

	{
		return (addBuddy( key, subsystem, true));
	}

	protected BuddyPluginBuddy
	addBuddy(String		key,
		int			subsystem,
		boolean		authorised) {
		if (key.length() == 0 || !verifyPublicKey( key)) {

			return (null);
		}

		BuddyPluginBuddy	buddy_to_return = null;

			// buddy may be already present as unauthorised in which case we pick it up
			// and authorise it and send the added event (we don't fire added events for
			// unauthorised buddies)

		synchronized(this) {

			for (int i=0;i<buddies.size();i++) {

				BuddyPluginBuddy buddy = (BuddyPluginBuddy)buddies.get(i);

				if (buddy.getPublicKey().equals( key)) {

					if (buddy.getSubsystem() != subsystem) {

						log("Buddy " + buddy.getString() + ": subsystem changed from " + buddy.getSubsystem() + " to " + subsystem);

						buddy.setSubsystem(subsystem);

						saveConfig(true);
					}

					if (authorised && !buddy.isAuthorised()) {

						log("Buddy " + buddy.getString() + ": no authorised");

						buddy.setAuthorised(true);

						buddy_to_return	= buddy;

					} else {

						return (buddy);
					}
				}
			}

			if (buddy_to_return == null) {

				buddy_to_return =
					new BuddyPluginBuddy(this, SystemTime.getCurrentTime(), subsystem, authorised, key, null, VERSION_CURRENT, null, null, 0, 0, null);

				buddies.add(buddy_to_return);

				buddies_map.put(key, buddy_to_return);

				if (!authorised) {

					log("Added unauthorised buddy: " + buddy_to_return.getString());
				}
			}

			if (buddy_to_return.isAuthorised()) {

				logMessage("Added buddy " + buddy_to_return.getString());

				saveConfig(true);
			}
		}

		fireAdded(buddy_to_return);

		return (buddy_to_return);
	}

	protected void
	removeBuddy(
		BuddyPluginBuddy 	buddy) {
		synchronized(this) {

			if (!buddies.remove( buddy)) {

				return;
			}

			buddies_map.remove( buddy.getPublicKey());

			logMessage("Removed friend " + buddy.getString());

			saveConfig(true);
		}

		buddy.destroy();

		fireRemoved(buddy);
	}

	protected Map
	readConfig() {
		File	config_file = new File(plugin_interface.getUtilities().getAzureusUserDir(), "friends.config");

		return (readConfigFile( config_file));
	}

	protected void
	writeConfig(
		Map		map) {
		File	config_file = new File(plugin_interface.getUtilities().getAzureusUserDir(), "friends.config");

		writeConfigFile(config_file, map);
	}

	protected void
	deleteConfig() {
		Utilities utils = plugin_interface.getUtilities();

		File	config_file = new File(utils.getAzureusUserDir(), "friends.config");

		utils.deleteResilientBEncodedFile(
				config_file.getParentFile(), config_file.getName(), true);

	}

	protected Map
	readConfigFile(
		File		name) {
		Utilities utils = plugin_interface.getUtilities();

		Map map = utils.readResilientBEncodedFile(
						name.getParentFile(), name.getName(), true);

		if (map == null) {

			map = new HashMap();
		}

		return (map);
	}

	protected boolean
	writeConfigFile(
		File		name,
		Map			data) {
		Utilities utils = plugin_interface.getUtilities();

		utils.writeResilientBEncodedFile(
			name.getParentFile(), name.getName(), data, true);

		return ( name.exists());
	}

	protected File
	getBuddyConfigDir() {
		return (new File( plugin_interface.getUtilities().getAzureusUserDir(), "friends"));
	}

	public BuddyPluginAZ2 getAZ2Handler() {
		return (az2_handler);
	}

	public String getPublicKey() {
		try {
			return (Base32.encode(ecc_handler.getPublicKey("Friend get key")));

		} catch (Throwable e) {

			logMessage("Failed to access public key", e);

			return (null);
		}
	}

	public boolean verifyPublicKey(String		key) {
		return (ecc_handler.verifyPublicKey( Base32.decode( key)));
	}

	protected void
	checkBuddiesAndRepublish() {
		updateBuddys();

		plugin_interface.getUtilities().createTimer("Buddy checker").addPeriodicEvent(
			TIMER_PERIOD,
			new UTTimerEventPerformer() {
				int	tick_count;

				public void perform(
					UTTimerEvent event) {
					tick_count++;

					if (!isClassicEnabled()) {

						return;
					}

					updateBuddys();

					if (tick_count % STATUS_REPUBLISH_TICKS == 0) {

						if (latest_publish.isEnabled()) {

							updatePublish(latest_publish);
						}
					}

					if (tick_count % CHECK_YGM_TICKS == 0) {

						checkMessagePending(tick_count);
					}

					if (tick_count % BLOOM_CHECK_TICKS == 0) {

						checkUnauthBloom();
					}

					if (tick_count % SAVE_CONFIG_TICKS == 0) {

						saveConfig();
					}

					if (tick_count % PERSISTENT_MSG_CHECK_TICKS == 0) {

						checkPersistentDispatch();
					}

					if (buddy_tracker != null) {

						buddy_tracker.tick(tick_count);
					}
				}
			});
	}

	protected void
	updateBuddys() {
		List	buddies_copy;

		synchronized(this) {

			buddies_copy = new ArrayList(buddies);
		}

		long	now = SystemTime.getCurrentTime();

		Random random = new Random();

		for (int i=0;i<buddies_copy.size();i++) {

			BuddyPluginBuddy	buddy = (BuddyPluginBuddy)buddies_copy.get(i);

			long	last_check = buddy.getLastStatusCheckTime();

			buddy.checkTimeouts();

			int	period = BUDDY_STATUS_CHECK_PERIOD_MIN + BUDDY_STATUS_CHECK_PERIOD_INC*buddies_copy.size()/5;

				// randomise a bit

			period += random.nextInt(2*60*1000);

				// last check may be in the future as we defer checks for seemingly inactive buddies

			if (now - last_check > period) {

				if (!buddy.statusCheckActive()) {

					if (buddy.isAuthorised()) {

						updateBuddyStatus(buddy);
					}
				}
			}
		}

			// trim any non-authorised buddies that have gone idle

		synchronized(this) {

			for (int i=0;i<buddies_copy.size();i++) {

				BuddyPluginBuddy	buddy = (BuddyPluginBuddy)buddies_copy.get(i);

				if (buddy.isIdle() && !buddy.isAuthorised()) {

					removeBuddy(buddy);
				}
			}
		}
	}

	protected void
	updateBuddyStatus(
		final BuddyPluginBuddy	buddy) {
		if (!buddy.statusCheckStarts()) {

			return;
		}

		log("Updating buddy status: " + buddy.getString());

		try {
			final byte[]	public_key = buddy.getRawPublicKey();

			DistributedDatabaseKey	key =
				getStatusKey( public_key, "Friend status check for " + buddy.getName());

			ddb.read(
				new DistributedDatabaseListener() {
					private long	latest_time;
					private Map		status;

					public void
					event(
						DistributedDatabaseEvent		event) {
						int	type = event.getType();

						if (type == DistributedDatabaseEvent.ET_VALUE_READ) {

							try {
								DistributedDatabaseValue value = event.getValue();

								long time = value.getCreationTime();

								if (time > latest_time) {

									byte[] signed_stuff = (byte[])value.getValue(byte[].class);

									Map	new_status = verifyAndExtract(signed_stuff, public_key);

									if (new_status != null) {

										status = new_status;

										latest_time = time;
									}
								}
							} catch (Throwable e) {

								log("Read failed", e);
							}
						} else if (	type == DistributedDatabaseEvent.ET_OPERATION_TIMEOUT ||
									type == DistributedDatabaseEvent.ET_OPERATION_COMPLETE) {

							if (status == null) {

								buddy.statusCheckFailed();

							} else {

								try {
									Long	l_tcp_port = (Long)status.get("t");
									Long	l_udp_port = (Long)status.get("u");

									int	tcp_port = l_tcp_port==null?0:l_tcp_port.intValue();
									int udp_port = l_udp_port==null?0:l_udp_port.intValue();

									InetAddress ip = InetAddress.getByAddress((byte[])status.get("i"));

									String	nick = decodeString((byte[])status.get("n"));

									Long	l_seq = (Long)status.get("s");

									int		seq = l_seq==null?0:l_seq.intValue();

									Long	l_os = (Long)status.get("o");

									int		os = l_os==null?BuddyPlugin.STATUS_ONLINE:l_os.intValue();

									Long	l_ver = (Long)status.get("v");

									int		ver = l_ver==null?VERSION_INITIAL:l_ver.intValue();

									buddy.statusCheckComplete(latest_time, ip, tcp_port, udp_port, nick, os, seq, ver);

								} catch (Throwable e) {

									buddy.statusCheckFailed();

									log("Status decode failed", e);
								}
							}
						}
					}
				},
				key,
				120*1000);

		} catch (Throwable e) {

			buddy.statusCheckFailed();

			log("Friend status update failed: " + buddy.getString(), e);
		}
	}

	protected Map
	verifyAndExtract(
		byte[]		signed_stuff,
		byte[]		public_key )

		throws BuddyPluginException
	{
		int	signature_length = ((int)signed_stuff[0])&0xff;

		byte[]	signature 	= new byte[ signature_length ];
		byte[]	data		= new byte[ signed_stuff.length - 1 - signature_length];

		System.arraycopy(signed_stuff, 1, signature, 0, signature_length);
		System.arraycopy(signed_stuff, 1 + signature_length, data, 0, data.length);

		try {
			if (ecc_handler.verify( public_key, data, signature)) {

				return (BDecoder.decode( data));

			} else {

				logMessage("Signature verification failed");

				return (null);
			}
		} catch (Throwable e) {

			rethrow ("Verification failed", e);

			return (null);
		}
	}

	protected byte[]
	signAndInsert(
		Map		plain_stuff,
		String	reason )

		throws BuddyPluginException
	{
		try {
			byte[] data = BEncoder.encode(plain_stuff);

			byte[] signature = ecc_handler.sign(data, reason);

			byte[]	signed_payload = new byte[ 1 + signature.length + data.length ];

			signed_payload[0] = (byte)signature.length;

			System.arraycopy(signature, 0, signed_payload, 1, signature.length);
			System.arraycopy(data, 0, signed_payload, 1 + signature.length, data.length);

			return (signed_payload);

		} catch (Throwable e) {

			rethrow ("Signing failed", e);

			return (null);
		}
	}

	public boolean verify(String				pk,
		byte[]				payload,
		byte[]				signature )

		throws BuddyPluginException
	{
		return (verify( Base32.decode( pk ), payload, signature));
	}

	protected boolean
	verify(
		BuddyPluginBuddy	buddy,
		byte[]				payload,
		byte[]				signature )

		throws BuddyPluginException
	{
		return (verify( buddy.getRawPublicKey(), payload, signature));
	}

	protected boolean
	verify(
		byte[]				pk,
		byte[]				payload,
		byte[]				signature )

		throws BuddyPluginException
	{
		try {

			return (ecc_handler.verify( pk, payload, signature));

		} catch (Throwable e) {

			rethrow ("Verification failed", e);

			return (false);
		}
	}

	public byte[]
   	sign(
   		byte[]		payload )

	   	throws BuddyPluginException
	{
		try {

			return (ecc_handler.sign( payload, "Friend message signing"));

		} catch (Throwable e) {

			rethrow ("Signing failed", e);

			return (null);
		}
	}

	protected cryptoResult
	encrypt(
		BuddyPluginBuddy	buddy,
		byte[]				payload )

		throws BuddyPluginException
	{
		return encrypt(buddy.getPublicKey(), payload, buddy.getName());
	}

	public cryptoResult encrypt(String				pk,
		byte[]				payload,
		String				forWho )

		throws BuddyPluginException
	{

		try {
			byte[]	hash = new byte[20];

			random.nextBytes(hash);

			Map	content = new HashMap();

			content.put("h", hash);
			content.put("p", payload);

			final byte[] encrypted = ecc_handler.encrypt(Base32.decode(pk), BEncoder.encode( content), "Encrypting message for " + forWho);

			final byte[] sha1_hash = new SHA1Simple().calculateHash(hash);

			return (
				new cryptoResult() {
					public byte[]
		    		getChallenge() {
						return (sha1_hash);
					}

		    		public byte[]
		    		getPayload()
		    		{
		    			return (encrypted);
		    		}
				});

		} catch (Throwable e) {

			rethrow ("Encryption failed", e);

			return (null);
		}
	}

	protected cryptoResult
	decrypt(
		BuddyPluginBuddy	buddy,
		byte[]				content,
		String forName)

		throws BuddyPluginException
	{

		try {
			final byte[] decrypted = ecc_handler.decrypt( buddy.getRawPublicKey(), content, "Decrypting message for " + buddy.getName());

			final Map	map = BDecoder.decode(decrypted);

			return (
				new cryptoResult() {
					public byte[]
		    		getChallenge() {
						return ((byte[])map.get("h"));
					}

		    		public byte[]
		    		getPayload()
		    		{
		    			return ((byte[])map.get("p"));
		    		}
				});

		} catch (Throwable e) {

			rethrow ("Decryption failed", e);

			return (null);
		}
	}

	public cryptoResult decrypt(String				public_key,
		byte[]				content )

		throws BuddyPluginException
	{

		try {
			final byte[] decrypted = ecc_handler.decrypt( Base32.decode(public_key), content, "Decrypting message for " + public_key);

			final Map	map = BDecoder.decode(decrypted);

			return (
				new cryptoResult() {
					public byte[]
		    		getChallenge() {
						return ((byte[])map.get("h"));
					}

		    		public byte[]
		    		getPayload()
		    		{
		    			return ((byte[])map.get("p"));
		    		}
				});

		} catch (Throwable e) {

			rethrow ("Decryption failed", e);

			return (null);
		}
	}

	protected void
	setMessagePending(
		BuddyPluginBuddy			buddy,
		final operationListener		listener )

		throws BuddyPluginException
	{
		try {
			checkAvailable();

			final String	reason = "Friend YGM write for " + buddy.getName();

			Map	payload = new HashMap();

			payload.put("r", new Long( random.nextLong()));

			byte[] signed_payload = signAndInsert( payload, reason);

			Map	envelope = new HashMap();

			envelope.put("pk", ecc_handler.getPublicKey( reason));
			envelope.put("ss", signed_payload);

			DistributedDatabaseValue	value = ddb.createValue(BEncoder.encode( envelope));

			logMessage(reason + " starts: " + payload);

			DistributedDatabaseKey	key = getYGMKey(buddy.getRawPublicKey(), reason);

			ddb.write(
				new DistributedDatabaseListener() {
					public void
					event(
						DistributedDatabaseEvent		event) {
						int	type = event.getType();

						if (	type == DistributedDatabaseEvent.ET_OPERATION_TIMEOUT ||
								type == DistributedDatabaseEvent.ET_OPERATION_COMPLETE) {

							logMessage(reason + " complete");

							listener.complete();
						}
					}
				},
				key,
				value);

		} catch (Throwable e) {

			try {
				rethrow ("Failed to publish YGM", e);

			} finally {

				listener.complete();
			}
		}
	}

	public void checkMessagePending(
		int	tick_count) {
		log("Checking YGM");

		if (tick_count % YGM_BLOOM_LIFE_TICKS == 0) {

			synchronized(this) {

				ygm_unauth_bloom = null;
			}
		}

		try {
			String	reason = "Friend YGM check";

			byte[] public_key = ecc_handler.getPublicKey(reason);

			DistributedDatabaseKey	key = getYGMKey(public_key, reason);

			ddb.read(
				new DistributedDatabaseListener() {
					private List		new_ygm_buddies = new ArrayList();
					private boolean	 	unauth_permitted = false;;

					public void
					event(
						DistributedDatabaseEvent		event) {
						int	type = event.getType();

						if (type == DistributedDatabaseEvent.ET_VALUE_READ) {

							try {
								DistributedDatabaseValue value = event.getValue();

								byte[]	envelope = (byte[])value.getValue(byte[].class);

								Map	map = BDecoder.decode(envelope);

								byte[]	pk = (byte[])map.get("pk");

								if (pk == null) {

									return;
								}

								String	pk_str = Base32.encode(pk);

								BuddyPluginBuddy buddy = getBuddyFromPublicKey(pk_str);

								if (buddy == null || !buddy.isAuthorised()) {

									if (buddy == null) {

										log("YGM entry from unknown friend '" + pk_str + "' - ignoring");

									} else {

										log("YGM entry from unauthorised friend '" + pk_str + "' - ignoring");
									}

									byte[] address = event.getContact().getAddress().getAddress().getAddress();

									synchronized(BuddyPlugin.this) {

										if (ygm_unauth_bloom == null) {

											ygm_unauth_bloom = BloomFilterFactory.createAddOnly(512);
										}

										if (!ygm_unauth_bloom.contains( address)) {

											ygm_unauth_bloom.add(address);

											unauth_permitted = true;
										}
									}
								} else {

									byte[]	signed_stuff = (byte[])map.get("ss");

									Map	payload = verifyAndExtract(signed_stuff, pk);

									if (payload != null) {

										long	rand = ((Long)payload.get("r")).longValue();

										if (buddy.addYGMMarker( rand)) {

											new_ygm_buddies.add(buddy);
										}
									}
								}
							} catch (Throwable e) {

								log("Read failed", e);
							}
						} else if (	type == DistributedDatabaseEvent.ET_OPERATION_TIMEOUT ||
									type == DistributedDatabaseEvent.ET_OPERATION_COMPLETE) {

							if (new_ygm_buddies.size() > 0 || unauth_permitted) {

								BuddyPluginBuddy[] b = new BuddyPluginBuddy[new_ygm_buddies.size()];

								new_ygm_buddies.toArray(b);

								fireYGM(b);
							}
						}
					}
				},
				key,
				120*1000,
				DistributedDatabase.OP_EXHAUSTIVE_READ);

			boolean	write_bogus_ygm = false;

			synchronized(this) {

				if (!bogus_ygm_written) {

					bogus_ygm_written = write_bogus_ygm = true;
				}
			}

			if (write_bogus_ygm) {

				final String	reason2 = "Friend YGM write for myself";

				Map	envelope = new HashMap();

				DistributedDatabaseValue	value = ddb.createValue(BEncoder.encode( envelope));

				logMessage(reason2 + " starts");

				ddb.write(
					new DistributedDatabaseListener() {
						public void
						event(
							DistributedDatabaseEvent		event) {
							int	type = event.getType();

							if (type == DistributedDatabaseEvent.ET_OPERATION_COMPLETE) {

								logMessage(reason2 + " complete");
							}
						}
					},
					key,
					value);
			}

		} catch (Throwable e) {

			logMessage("YGM check failed", e);
		}
	}

	public BuddyPluginBuddy getBuddyFromPublicKey(String		key) {
		synchronized(this) {

			return ((BuddyPluginBuddy)buddies_map.get(key));
		}
	}

	public PluginInterface getPluginInterface() {
		return (plugin_interface);
	}

	protected SESecurityManager
	getSecurityManager() {
		return (sec_man);
	}

	protected GenericMessageRegistration
	getMessageRegistration() {
		return (msg_registration);
	}

		/**
		 * Returns authorised buddies only
		 */

	public List<BuddyPluginBuddy>
	getBuddies() {
		synchronized(this) {

			List<BuddyPluginBuddy>	result = new ArrayList<BuddyPluginBuddy>();

			for (int i=0;i<buddies.size();i++) {

				BuddyPluginBuddy	buddy = (BuddyPluginBuddy)buddies.get(i);

				if (buddy.isAuthorised()) {

					result.add(buddy);
				}
			}

			return (result);
		}
	}

	protected List<BuddyPluginBuddy>
	getAllBuddies() {
		synchronized(this) {

			return (new ArrayList<BuddyPluginBuddy>( buddies));
		}
	}

	public boolean isAvailable() {
		try {
			checkAvailable();

			return (true);

		} catch (Throwable e) {

			return (false);
		}
	}

	protected void
	checkAvailable()

		throws BuddyPluginException
	{
		if (initialisation_state == INIT_UNKNOWN) {

			throw (new BuddyPluginException("Plugin not yet initialised"));

		} else if (initialisation_state == INIT_BAD) {

			throw (new BuddyPluginException("Plugin unavailable"));

		}
	}


	protected void
	fireClassicInitialised(
		boolean		ok) {
		if (ok) {

			initialisation_state = INIT_OK;

		} else {

			initialisation_state = INIT_BAD;
		}

		persistentDispatchInit();

		if (ok) {

			buddy_tracker.initialise();
		}

		List	 listeners_ref = listeners.getList();

		for (int i=0;i<listeners_ref.size();i++) {

			try {
				((BuddyPluginListener)listeners_ref.get(i)).initialised(ok);

			} catch (Throwable e) {

				Debug.printStackTrace(e);
			}
		}
	}

	public void addListener(
		BuddyPluginListener	listener) {
		if (listeners.contains(listener)) {
			return;
		}

		listeners.add(listener);

		if (initialisation_state != INIT_UNKNOWN) {

			listener.initialised(initialisation_state == INIT_OK);
		}
	}

	public void removeListener(
		BuddyPluginListener	listener) {
		listeners.remove(listener);
	}

	protected Map
	requestReceived(
		BuddyPluginBuddy		from_buddy,
		int						subsystem,
		Map						content )

		throws BuddyPluginException
	{
		List	 listeners_ref = request_listeners.getList();

		for (int i=0;i<listeners_ref.size();i++) {

			try {
				Map reply = ((BuddyPluginBuddyRequestListener)listeners_ref.get(i)).requestReceived(from_buddy, subsystem, content);

				if (reply != null) {

					return (reply);
				}
			} catch (BuddyPluginException e) {

				throw (e);

			} catch (Throwable e) {

				Debug.printStackTrace(e);

				throw (new BuddyPluginException("Request processing failed", e));
			}
		}

		return (null);
	}

	protected void
   	fireAdded(
   		BuddyPluginBuddy		buddy )
   	{
		if (buddy.isAuthorised()) {

			buddy.setLocalAuthorisedRSSTagsOrCategories(public_tags_or_categories);

	   		List	 listeners_ref = listeners.getList();

	   		for (int i=0;i<listeners_ref.size();i++) {

	   			try {
	   				((BuddyPluginListener)listeners_ref.get(i)).buddyAdded(buddy);

	   			} catch (Throwable e) {

	   				Debug.printStackTrace(e);
	   			}
	   		}
		}
   	}

	protected void
   	fireRemoved(
   		BuddyPluginBuddy		buddy )
   	{
		if (buddy.isAuthorised()) {

	   		List	 listeners_ref = listeners.getList();

	   		for (int i=0;i<listeners_ref.size();i++) {

	   			try {
	   				((BuddyPluginListener)listeners_ref.get(i)).buddyRemoved(buddy);

	   			} catch (Throwable e) {

	   				Debug.printStackTrace(e);
	   			}
	   		}
		}
   	}

	protected void
   	fireDetailsChanged(
   		BuddyPluginBuddy		buddy )
   	{
		if (buddy.isAuthorised()) {

	   		List	 listeners_ref = listeners.getList();

	   		for (int i=0;i<listeners_ref.size();i++) {

	   			try {
	   				((BuddyPluginListener)listeners_ref.get(i)).buddyChanged(buddy);

	   			} catch (Throwable e) {

	   				Debug.printStackTrace(e);
	   			}
	   		}
		}
   	}

	protected void
   	fireYGM(
   		BuddyPluginBuddy[]		from_buddies )
   	{
   		List	 listeners_ref = request_listeners.getList();

   		for (int i=0;i<listeners_ref.size();i++) {

   			try {
   				((BuddyPluginBuddyRequestListener)listeners_ref.get(i)).pendingMessages(from_buddies);

   			} catch (Throwable e) {

   				Debug.printStackTrace(e);
   			}
   		}
   	}

	protected void
 	fireEnabledStateChanged()
 	{
		final boolean enabled = !plugin_interface.getPluginState().isDisabled() && isClassicEnabled();

 		List	 listeners_ref = listeners.getList();

 		for (int i=0;i<listeners_ref.size();i++) {

 			try {
 				((BuddyPluginListener)listeners_ref.get(i)).enabledStateChanged(enabled);

 			} catch (Throwable e) {

 				Debug.printStackTrace(e);
 			}
 		}
 	}

	protected void
 	fireUpdated()
 	{
 		for (BuddyPluginListener listener: listeners) {

 			try {
 				listener.updated();

 			} catch (Throwable e) {

 				Debug.printStackTrace(e);
 			}
 		}
 	}

	protected BuddyPluginViewInterface
	getSWTUI() {
		return (swt_ui);
	}

	protected void
	rethrow (String		reason,
		Throwable	e )

		throws BuddyPluginException
	{
		logMessage(reason, e);

		if (e instanceof CryptoManagerPasswordException) {


			throw (new BuddyPluginPasswordException(((CryptoManagerPasswordException)e).wasIncorrect(), reason, e));

		} else {

			throw (new BuddyPluginException( reason, e));
		}
	}

	public InputStream handleURLProtocol(
		AZPluginConnection			connection,
		String						arg_str )

		throws IPCException
	{
		if (arg_str.toLowerCase(Locale.US).startsWith("chat:")) {

				//azplug:?id=azbuddy&arg=chat%3Aanon%3Fmonkey%2520magic

			if (!beta_enabled_param.getValue()) {

				throw (new IPCException("Decentralized chat not enabled"));
			}

			try {
				InputStream result = beta_plugin.handleURI(arg_str, false);

				if (result != null) {

					return (result);
				}

					// return an empty .vuze file to keep things happy...

				return (new ByteArrayInputStream( VuzeFileHandler.getSingleton().create().exportToBytes()));

			} catch (Throwable e) {

				throw (new IPCException( e));
			}

		} else {

			String[]	args = arg_str.split("&");

			String		pk 				= null;
			String		category_or_tag	= "All";
			byte[]		hash			= null;

			for (String arg: args) {

				String[]	bits = arg.split("=");

				String	lhs = bits[0];
				String	rhs	= UrlUtils.decode(bits[1]);

				if (lhs.equals("pk")) {

					pk		= rhs;

				} else if (lhs.equals("cat")) {

					category_or_tag = rhs;

				} else if (lhs.equals("hash")) {

					hash	= Base32.decode(rhs);
				}
			}

			if (pk == null) {

				throw (new IPCException("Public key missing from '" + arg_str + "'"));
			}

			BuddyPluginBuddy	buddy	= getBuddyFromPublicKey(pk);

			if (buddy == null) {

				throw (new IPCException("Buddy with public key '" + pk + "' not found"));
			}

			if (hash == null) {

				return (handleUPRSS( connection, buddy, category_or_tag));

			} else {

				return (handleUPTorrent( connection, buddy, category_or_tag, hash));
			}
		}
	}

	public InputStream handleUPRSS(
		final AZPluginConnection	connection,
		BuddyPluginBuddy			buddy,
		String						tag_or_category )

		throws IPCException
	{
		if (!buddy.isOnline( true)) {

			throw (new IPCException("Buddy isn't online"));
		}

		Map<String,Object>	msg = new HashMap<String, Object>();

		final String if_mod 	= connection.getRequestProperty("If-Modified-Since");

		try {
			msg.put("cat", tag_or_category.getBytes("UTF-8"));

			if (if_mod != null) {

				msg.put("if_mod", if_mod);
			}

			// String etag		= connection.getRequestProperty("If-None-Match");

		} catch (Throwable e) {

			Debug.out(e);
		}

		final Object[] 		result 		= { null };
		final AESemaphore	result_sem 	= new AESemaphore("BuddyPlugin:rss");

		final String	etag = buddy.getPublicKey() + "-" + tag_or_category;

		az2_handler.sendAZ2RSSMessage(
			buddy,
			msg,
			new BuddyPluginAZ2TrackerListener() {
				public Map
				messageReceived(
					BuddyPluginBuddy	buddy,
					Map					message) {
					try {
						byte[] bytes = (byte[])message.get("rss");

						ByteArrayInputStream bais = new ByteArrayInputStream(bytes);

						result[0] = bais;

						connection.setHeaderField("ETag", etag);

						byte[] b_last_mod = (byte[])message.get("last_mod");

						if (b_last_mod != null) {

							String	last_mod = new String(b_last_mod, "UTF-8");

							connection.setHeaderField("Last-Modified", last_mod);

							if (if_mod != null && if_mod.equals(last_mod) && bytes.length == 0) {

								connection.setResponse(HttpURLConnection.HTTP_NOT_MODIFIED, "Not Modified");
							}
						}

						result_sem.release();

					} catch (Throwable e) {

						messageFailed(buddy, e);
					}

					return (null);
				}

				public void
				messageFailed(
					BuddyPluginBuddy	buddy,
					Throwable			cause) {
					result[0] = new IPCException("Read failed", cause);

					result_sem.release();
				}
			});

		result_sem.reserve(60*1000);

		if (result[0] == null) {

			throw (new IPCException("Timeout"));

		} else if (result[0] instanceof InputStream) {

			return ((InputStream)result[0]);

		} else {

			throw ((IPCException)result[0]);
		}
	}

	public InputStream handleUPTorrent(
		final AZPluginConnection	connection,
		final BuddyPluginBuddy		buddy,
		String						tag_or_category,
		final byte[]				hash )

		throws IPCException
	{
		final long timeout = 120*1000;

		final Object[] 		result 		= { null };
		final AESemaphore	result_sem 	= new AESemaphore("BuddyPlugin:upt");

		log("Attempting to download torrent for " + Base32.encode( hash));

			// first try and get torrent direct from the buddy

		if (buddy.isOnline( true)) {

			try {

				Map<String,Object>	msg = new HashMap<String, Object>();

				try {
					msg.put("cat", tag_or_category.getBytes("UTF-8"));

					msg.put("hash", hash);

				} catch (Throwable e) {

					Debug.out(e);
				}

				az2_handler.sendAZ2RSSMessage(
					buddy,
					msg,
					new BuddyPluginAZ2TrackerListener() {
						private boolean	result_set;

						public Map
						messageReceived(
							BuddyPluginBuddy	buddy,
							Map					message) {
							try {
								byte[] bytes = (byte[])message.get("torrent");

								log("    torrent downloaded from buddy");

								setResult(bytes);

							} catch (Throwable e) {

								messageFailed(buddy, e);
							}

							return (null);
						}

						public void
						messageFailed(
							BuddyPluginBuddy	buddy,
							Throwable			cause) {
							setResult(new IPCException("Read failed", cause));
						}

						protected void
						setResult(
							Object		obj) {
							synchronized(result) {

								if (result_set) {

									return;
								}

								result_set = true;

								if (!( result[0] instanceof byte[])) {

									result[0] = obj;
								}

								result_sem.release();
							}
						}
					});
			} catch (Throwable e) {

				result[0] = new IPCException("Buddy torrent get failed", e);

				result_sem.release();
			}
		} else {

			result[0] = new IPCException("Buddy is offline");

			result_sem.release();
		}

			// second try and get via magnet

		final MagnetPlugin	magnet_plugin = getMagnetPlugin();

		if (magnet_plugin == null) {

			synchronized(result) {

				if (result[0] == null) {

					result[0] = new IPCException("Magnet plugin unavailable");
				}
			}

			result_sem.release();

		} else {

			new AEThread2("BuddyPlugin:mag", true) {
				private boolean result_set;

				public void
				run() {
					try {

						if (buddy.isOnline( true)) {

							Thread.sleep(10*1000);
						}

						synchronized(result) {

							if (result[0] instanceof byte[]) {

								setResult(null);

								return;
							}
						}

						byte[] torrent_data = magnet_plugin.download(
								!logger.isEnabled() ? null : new MagnetPluginProgressListener() {
								public void
								reportSize(
									long	size) {
								}

								public void
								reportActivity(String	str) {
									log("    MagnetDownload: " + str);
								}

								public void
								reportCompleteness(
									int		percent) {
								}

								public void
								reportContributor(
									InetSocketAddress	address) {
								}

								public boolean
								verbose() {
									return (false);
								}

								public boolean
								cancelled() {
									return (false);
								}
							},
							hash,
							"",
							new InetSocketAddress[0],
							timeout,
							MagnetPlugin.FL_NONE);

						if (torrent_data == null) {

							setResult(new IPCException("Magnet timeout"));

						} else {

							log("    torrent downloaded from magnet");

							setResult(torrent_data);
						}
					} catch (Throwable e) {

						setResult(new IPCException("Magnet get failed", e));
					}
				}

				protected void
				setResult(
					Object		obj) {
					synchronized(result) {

						if (result_set) {

							return;
						}

						result_set = true;

						if (obj != null) {

							if (	result[0] == null ||
									(obj instanceof byte[] && !( result[0] instanceof byte[]))) {

								result[0] = obj;
							}
						}

						result_sem.release();
					}
				}
			}.start();
		}

		long	start = SystemTime.getMonotonousTime();

		if (result_sem.reserve(timeout)) {

			if (!( result[0] instanceof byte[])) {

				long	rem = timeout - (SystemTime.getMonotonousTime() - start);

				if (rem > 0) {

					result_sem.reserve(rem);
				}
			}
		}

		if (result[0] == null) {

			log("    torrent download timeout");

			throw (new IPCException("Timeout"));

		} else if (result[0] instanceof byte[]) {

			return (new ByteArrayInputStream((byte[])result[0]));

		} else {

			IPCException error = (IPCException)result[0];

			log("    torrent downloaded failed: " + Debug.getNestedExceptionMessage( error));

			throw (error);
		}
	}

	protected MagnetPlugin
	getMagnetPlugin() {
		PluginInterface  pi  = AzureusCoreFactory.getSingleton().getPluginManager().getPluginInterfaceByClass(MagnetPlugin.class);

		if (pi == null) {

			return (null);
		}

		return ((MagnetPlugin)pi.getPlugin());
	}

	public feedDetails getRSS(
		BuddyPluginBuddy		buddy,
		String					tag_or_category,
		String					if_mod )

		throws BuddyPluginException
	{
		if (!buddy.isLocalRSSTagOrCategoryAuthorised( tag_or_category)) {

			throw (new BuddyPluginException("Unauthorised tag/category '" + tag_or_category + "'"));
		}

		buddy.localRSSTagOrCategoryRead(tag_or_category);

		Download[] downloads = plugin_interface.getDownloadManager().getDownloads();

		List<Download>	selected_dls = new ArrayList<Download>();

		long	fingerprint	= 0;

		for (int i=0;i<downloads.length;i++) {

			Download download = downloads[i];

			Torrent torrent = download.getTorrent();

			if (torrent == null) {

				continue;
			}

			boolean	match = tag_or_category.equalsIgnoreCase("all");

			if (!match) {

				String dl_cat = download.getAttribute(ta_category);

				match = dl_cat != null && dl_cat.equals(tag_or_category);
			}

			if (!match) {

				try {
					List<Tag> tags = TagManagerFactory.getTagManager().getTagsForTaggable(TagType.TT_DOWNLOAD_MANUAL, PluginCoreUtils.unwrap( download));

					for (Tag tag: tags) {

						if (tag.getTagName(true).equals( tag_or_category)) {

							match = true;

							break;
						}
					}
				} catch (Throwable e) {
				}
			}

			if (match) {

				if (!TorrentUtils.isReallyPrivate( PluginCoreUtils.unwrap( torrent))) {

					selected_dls.add(download);

					byte[] hash = torrent.getHash();

					int	num = (hash[0]<<24)&0xff000000 | (hash[1] << 16)&0x00ff0000 | (hash[2] << 8)&0x0000ff00 | hash[3]&0x000000ff;

					fingerprint += num;
				}
			}
		}

		PluginConfig pc = plugin_interface.getPluginconfig();

		String	feed_finger_key = "feed_finger.category." + tag_or_category;
		String	feed_date_key 	= "feed_date.category." + tag_or_category;

		long	existing_fingerprint 	= pc.getPluginLongParameter(feed_finger_key, 0);
		long	feed_date 				= pc.getPluginLongParameter(feed_date_key, 0);

		long	now = SystemTime.getCurrentTime();

		if (existing_fingerprint == fingerprint) {

				// update every now and then to pick up new peer/seed values

			if (selected_dls.size() > 0) {

				if (	now < feed_date ||
						now - feed_date > FEED_UPDATE_MIN_MILLIS) {

					feed_date = now;

					pc.setPluginParameter(feed_date_key, feed_date);
				}
			}
		} else {

			pc.setPluginParameter(feed_finger_key, fingerprint);


				// ensure feed date goes up

			if (now <= feed_date) {

				feed_date++;

			} else {

				feed_date = now;
			}

			pc.setPluginParameter(feed_date_key, feed_date);
		}

		String last_modified = TimeFormatter.getHTTPDate(feed_date);

		if (if_mod != null && if_mod.equals( last_modified)) {

			return (new feedDetails(new byte[0], last_modified));
		}

		ByteArrayOutputStream	os = new ByteArrayOutputStream();

		try {
			PrintWriter pw = new PrintWriter(new OutputStreamWriter(os, "UTF-8"));

			pw.println("<?xml version=\"1.0\" encoding=\"utf-8\"?>");

			pw.println("<rss version=\"2.0\" xmlns:vuze=\"http://www.vuze.com\">");

			pw.println("<channel>");

			pw.println("<title>" + escape( tag_or_category ) + "</title>");

			Collections.sort(
				selected_dls,
				new Comparator<Download>() {
					public int
					compare(
						Download d1,
						Download d2) {
						long	added1 = getAddedTime(d1)/1000;
						long	added2 = getAddedTime(d2)/1000;

						return ((int)(added2 - added1 ));
					}
				});


			pw.println(	"<pubDate>" + last_modified + "</pubDate>");

			for (int i=0;i<selected_dls.size();i++) {

				Download download = (Download)selected_dls.get(i);

				DownloadManager	core_download = PluginCoreUtils.unwrap(download);

				Torrent torrent = download.getTorrent();

				String	hash_str = Base32.encode( torrent.getHash());

				pw.println("<item>");

				pw.println("<title>" + escape( download.getName()) + "</title>");

				pw.println("<guid>" + hash_str + "</guid>");

				long added = core_download.getDownloadState().getLongParameter(DownloadManagerState.PARAM_DOWNLOAD_ADDED_TIME);

				pw.println(	"<pubDate>" + TimeFormatter.getHTTPDate(added ) + "</pubDate>");

				pw.println(	"<vuze:size>" + torrent.getSize()+ "</vuze:size>");
				pw.println(	"<vuze:assethash>" + hash_str + "</vuze:assethash>");

				String url = "azplug:?id=azbuddy&name=Friends&arg=";

				String arg = "pk=" + getPublicKey() + "&cat=" + tag_or_category + "&hash=" + Base32.encode(torrent.getHash());

				url += URLEncoder.encode(arg, "UTF-8");

				pw.println("<vuze:downloadurl>" + escape( url ) + "</vuze:downloadurl>");

				DownloadScrapeResult scrape = download.getLastScrapeResult();

				if (scrape != null && scrape.getResponseType() == DownloadScrapeResult.RT_SUCCESS) {

					pw.println(	"<vuze:seeds>" + scrape.getSeedCount() + "</vuze:seeds>");
					pw.println(	"<vuze:peers>" + scrape.getNonSeedCount() + "</vuze:peers>");
				}

				pw.println("</item>");
			}

			pw.println("</channel>");

			pw.println("</rss>");

			pw.flush();

			return (new feedDetails( os.toByteArray(), last_modified));

		} catch (IOException e) {

			throw (new BuddyPluginException("", e));
		}
	}

	public byte[]
	getRSSTorrent(
		BuddyPluginBuddy		buddy,
		String					category,
		byte[]					hash )

		throws BuddyPluginException
	{
		if (!buddy.isLocalRSSTagOrCategoryAuthorised( category)) {

			throw (new BuddyPluginException("Unauthorised category '" + category + "'"));
		}

		try {
			Download download = plugin_interface.getDownloadManager().getDownload(hash);

			if (download != null) {

				Torrent	torrent = download.getTorrent();

				if (torrent != null) {

					String dl_cat = download.getAttribute(ta_category);

					if (	category.equalsIgnoreCase("all") ||
							(dl_cat != null && dl_cat.equals( category))) {

						if (!TorrentUtils.isReallyPrivate( PluginCoreUtils.unwrap( torrent))) {

							torrent = torrent.removeAdditionalProperties();

							return ( torrent.writeToBEncodedData());
						}
					}
				}
			}
		} catch (Throwable e) {

			throw (new BuddyPluginException("getTorrent failed", e));
		}

		throw (new BuddyPluginException("Not found"));
	}

	protected long
	getAddedTime(
		Download	download) {
		DownloadManager	core_download = PluginCoreUtils.unwrap(download);

		return ( core_download.getDownloadState().getLongParameter(DownloadManagerState.PARAM_DOWNLOAD_ADDED_TIME));
	}

	protected String
	escape(String	str) {
		return ( XUXmlWriter.escapeXML(str));
	}

	public void addRequestListener(
		BuddyPluginBuddyRequestListener	listener) {
		request_listeners.add(listener);
	}

	public void removeRequestListener(
		BuddyPluginBuddyRequestListener	listener) {
		request_listeners.remove(listener);
	}

	public void logMessage(String		str,
		Throwable	e) {
		logMessage(str + ": " + Debug.getNestedExceptionMessage(e), true);
	}

	public void logMessage(String		str) {
		logMessage(str, false);
	}

	public void logMessage(String		str,
		boolean		is_error) {
		log(str);

		Iterator it = listeners.iterator();

		while (it.hasNext()) {

			try {
				((BuddyPluginListener)it.next()).messageLogged(str, is_error);

			} catch (Throwable e) {

				Debug.printStackTrace(e);
			}
		}
	}

	public void log(String		str) {
		logger.log(str);
	}

	public void log(String		str,
		Throwable	e) {
		logger.log(str + ": " + Debug.getNestedExceptionMessageAndStack( e));
	}

	private static class
	publishDetails
		implements Cloneable
	{
		private byte[]			public_key;
		private InetAddress		ip;
		private int				tcp_port;
		private int				udp_port;
		private String			nick_name;
		private int				online_status		= STATUS_ONLINE;

		private boolean			enabled;
		private boolean			published;

		private int				sequence;

		protected publishDetails
		getCopy() {
			try {
				publishDetails copy = (publishDetails)clone();

				copy.published = false;

				return (copy);

			} catch (Throwable e) {

				return ( null);
			}
		}

		protected boolean
		isPublished() {
			return (published);
		}

		protected void
		setPublished(
			boolean		b) {
			published	= b;
		}

		protected boolean
		isEnabled() {
			return (enabled);
		}

		protected void
		setEnabled(
			boolean	_enabled) {
			enabled	= _enabled;
		}

		protected void
		setSequence(
			int		seq) {
			sequence = seq;
		}

		protected int
		getSequence() {
			return (sequence);
		}

		protected byte[]
		getPublicKey() {
			return (public_key);
		}

		protected void
		setPublicKey(
			byte[]		k) {
			public_key	= k;
		}

		protected InetAddress
		getIP() {
			return (ip);
		}

		protected void
		setIP(
			InetAddress	_ip) {
			ip	= _ip;
		}

		protected int
		getTCPPort() {
			return (tcp_port);
		}

		protected void
		setTCPPort(
			int		_port) {
			tcp_port = _port;
		}

		protected int
		getUDPPort() {
			return (udp_port);
		}

		protected void
		setUDPPort(
			int		_port) {
			udp_port = _port;
		}

		protected String
		getNickName() {
			return (nick_name);
		}

		protected void
		setNickName(String		 n) {
			nick_name	= n;
		}

		protected int
		getOnlineStatus() {
			return (online_status);
		}

		protected void
		setOnlineStatus(
			int		_status) {
			online_status = _status;
		}

		protected String
		getString() {
			return ("enabled=" + enabled + ",ip=" + ip + ",tcp=" + tcp_port + ",udp=" + udp_port + ",stat=" + online_status + ",key=" + (public_key==null?"<none>":Base32.encode( public_key)));
		}
	}

	protected interface
	operationListener
	{
		public void
		complete();
	}

	public interface cryptoResult
	{
		public byte[]
		getChallenge();

		public byte[]
		getPayload();
	}

	protected static class
	feedDetails
	{
		private byte[]		contents;
		private String		last_modified;

		protected
		feedDetails(
			byte[]		_contents,
			String		_last_modified) {
			contents		= _contents;
			last_modified	= _last_modified;
		}

		protected byte[]
		getContent() {
			return (contents);
		}

		protected String
		getLastModified() {
			return (last_modified);
		}
	}
}
