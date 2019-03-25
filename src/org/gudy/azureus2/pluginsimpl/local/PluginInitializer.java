/*
 * File	: PluginInitializer.java
 * Created : 2 nov. 2003 18:59:17
 * By		: Olivier
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.	See the
 * GNU General Public License for more details (see the LICENSE file).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA	02111-1307	USA
 */

package org.gudy.azureus2.pluginsimpl.local;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.util.*;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.download.DownloadManager;
import org.gudy.azureus2.core3.global.GlobalManager;
import org.gudy.azureus2.core3.global.GlobalManagerListener;
import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.logging.*;
import org.gudy.azureus2.core3.security.SESecurityManager;
import org.gudy.azureus2.core3.util.*;
import org.gudy.azureus2.platform.PlatformManagerFactory;
import org.gudy.azureus2.plugins.*;
import org.gudy.azureus2.plugins.platform.PlatformManagerException;
import org.gudy.azureus2.pluginsimpl.local.launch.PluginLauncherImpl;
import org.gudy.azureus2.pluginsimpl.local.ui.UIManagerImpl;
import org.gudy.azureus2.pluginsimpl.local.update.UpdateManagerImpl;
import org.gudy.azureus2.pluginsimpl.local.utils.UtilitiesImpl;
import org.gudy.azureus2.pluginsimpl.local.utils.UtilitiesImpl.runnableWithException;
import org.gudy.azureus2.update.UpdaterUpdateChecker;
import org.gudy.azureus2.update.UpdaterUtils;


import com.aelitis.azureus.core.*;
import com.aelitis.azureus.core.versioncheck.VersionCheckClient;

import hello.util.Log;
import hello.util.SingleCounter0;



/**
 * @author Olivier
 *
 */
public class PluginInitializer
	implements GlobalManagerListener, AEDiagnosticsEvidenceGenerator {

	private static String TAG = PluginInitializer.class.getSimpleName();
	
	public static final boolean DISABLE_PLUGIN_VERIFICATION = false;

	private static final LogIDs LOGID = LogIDs.CORE;
	public static final String	INTERNAL_PLUGIN_ID = "<internal>";

	// class name, plugin id, plugin key (key used for config props so if you change
	// it you'll need to migrate the config)
	// "id" is used when checking for updates

	// IF YOU ADD TO THE BUILTIN PLUGINS, AMEND PluginManagerDefault appropriately!!!!
		// Plugin ID constant
		// class
		// plugin id
		// plugin key for prefixing config data
		// report if not present
	// force re-enable if disabled by config

	private String[][] builtinPlugins = {
		{	 
			PluginManagerDefaults.PID_START_STOP_RULES,
			"com.aelitis.azureus.plugins.startstoprules.defaultplugin.StartStopRulesDefaultPlugin",
			"azbpstartstoprules",
			"",
			"true",
			"true"
		},
		{
			PluginManagerDefaults.PID_REMOVE_RULES,
			"com.aelitis.azureus.plugins.removerules.DownloadRemoveRulesPlugin",
			"azbpremovalrules",
			"",
			"true",
			"false"
		},
		{	 
			PluginManagerDefaults.PID_SHARE_HOSTER,
			"com.aelitis.azureus.plugins.sharing.hoster.ShareHosterPlugin",
			"azbpsharehoster",
			"ShareHoster",
			"true",
			"false"
		},
		{	 
			PluginManagerDefaults.PID_PLUGIN_UPDATE_CHECKER,
			"org.gudy.azureus2.pluginsimpl.update.PluginUpdatePlugin",
			"azbppluginupdate",
			"PluginUpdate",
			"true",
			"true"
		},
		{	 
			PluginManagerDefaults.PID_UPNP,
			"com.aelitis.azureus.plugins.upnp.UPnPPlugin",
			"azbpupnp",
			"UPnP",
			"true",
			"false"
		},
		{	 
			PluginManagerDefaults.PID_DHT,
			"com.aelitis.azureus.plugins.dht.DHTPlugin",
			"azbpdht",
			"DHT",
			"true",
			"false"
		},
		{
			PluginManagerDefaults.PID_DHT_TRACKER,
			"com.aelitis.azureus.plugins.tracker.dht.DHTTrackerPlugin",
			"azbpdhdtracker",
			"DHT Tracker",
			"true",
			"false"
		},
		{
			PluginManagerDefaults.PID_MAGNET,
			"com.aelitis.azureus.plugins.magnet.MagnetPlugin",
			"azbpmagnet",
			"Magnet URI Handler",
			"true",
			"false"
		},
		{
			PluginManagerDefaults.PID_CORE_UPDATE_CHECKER,
			"org.gudy.azureus2.update.CoreUpdateChecker",
			"azbpcoreupdater",
			"CoreUpdater",
			"true",
			"true"
		},
		{
			PluginManagerDefaults.PID_CORE_PATCH_CHECKER,
			"org.gudy.azureus2.update.CorePatchChecker",
			"azbpcorepatcher",
			"CorePatcher",
			"true",
			"true"
		},
 		{	 
			PluginManagerDefaults.PID_PLATFORM_CHECKER,
			"org.gudy.azureus2.platform.PlatformManagerPluginDelegate",
			"azplatform2",
			"azplatform2",
			"true",
			"false"
		},
	 		//{	 PluginManagerDefaults.PID_JPC,
			//	"com.aelitis.azureus.plugins.jpc.JPCPlugin",
			//	"azjpc",
			//	"azjpc",
			//	"false" },
 		{	 PluginManagerDefaults.PID_EXTERNAL_SEED,
			"com.aelitis.azureus.plugins.extseed.ExternalSeedPlugin",
			"azextseed",
			"azextseed",
 				"true",
 				"false"},
 		{	 PluginManagerDefaults.PID_LOCAL_TRACKER,
 				"com.aelitis.azureus.plugins.tracker.local.LocalTrackerPlugin",
 				"azlocaltracker",
 				"azlocaltracker",
			"true",
			"false"},
		{
			PluginManagerDefaults.PID_NET_STATUS,
 			"com.aelitis.azureus.plugins.net.netstatus.NetStatusPlugin",
 			"aznetstat",
 			"aznetstat",
			"true",
			"false"
		},
		{	 
			PluginManagerDefaults.PID_BUDDY,
			"com.aelitis.azureus.plugins.net.buddy.BuddyPlugin",
			"azbuddy",
			"azbuddy",
			"true",
			"false"
		},
		{
			PluginManagerDefaults.PID_RSS,
			"com.aelitis.azureus.core.rssgen.RSSGeneratorPlugin",
			"azintrss",
			"azintrss",
			"true",
			"false"
		},
		/* disable until we can get some tracker admins to work on this
	 	{
 			PluginManagerDefaults.PID_TRACKER_PEER_AUTH,
			"com.aelitis.azureus.plugins.tracker.peerauth.TrackerPeerAuthPlugin",
			"aztrackerpeerauth",
			"aztrackerpeerauth",
			"true",
			"false"
		},
		*/
		};

	static VerifiedPluginHolder verifiedPluginHolder;

	static {
		synchronized(PluginInitializer.class) {
			verifiedPluginHolder = new VerifiedPluginHolder();
		}
	}

	// these can be removed one day
	private static String[][]default_version_details = {
		{ 
			"org.cneclipse.multiport.MultiPortPlugin",
			"multi-ports",
			"Mutli-Port Trackers",
			"1.0" 
		},
	};


	private static PluginInitializer	singleton;
	private static AEMonitor			classMon	= new AEMonitor("PluginInitializer");

	private static List		registrationQueue 	= new ArrayList();
	private static List		initThreads = new ArrayList(1);

	private static AsyncDispatcher		asyncDispatcher = new AsyncDispatcher();
	private static List<PluginEvent>	pluginEventHistory = new ArrayList<PluginEvent>();

	private AzureusCore		azureusCore;

	private PluginInterfaceImpl	defaulPlugin;
	private PluginManager		pluginManager;
	private ClassLoader			rootClassLoader	= getClass().getClassLoader();
	private List				loadedPIList		= new ArrayList();
	private static boolean		loadingBuiltin;

	private List<Plugin>				s_plugins			= new ArrayList<Plugin>();
	private List<PluginInterfaceImpl>	s_pluginInterfaces	= new ArrayList<PluginInterfaceImpl>();

	private boolean				initialisationComplete;
	private volatile boolean	pluginsInitialised;

	private Set<String>	vcDisabledPlugins = VersionCheckClient.getSingleton().getDisabledPluginIDs();

	public static PluginInitializer getSingleton(AzureusCore azureusCore) {
		try {
			classMon.enter();
			if (singleton == null) {
				singleton = new PluginInitializer(azureusCore);
			}
			return (singleton);
		} finally {
			classMon.exit();
		}
	}

	private static PluginInitializer peekSingleton() {
		try {
			classMon.enter();
			return (singleton);
		} finally {
			classMon.exit();
		}
	}

	protected static void queueRegistration(Class	_class) {
		try {
			classMon.enter();
		 	if (singleton == null) {
				registrationQueue.add(_class);
			} else {
				try {
					singleton.initializePluginFromClass( _class, INTERNAL_PLUGIN_ID, _class.getName(), false, false, true);
			} catch (PluginException e) {
				}
			}
		} finally {
			classMon.exit();
		}
	}

	protected static void queueRegistration(
		Plugin		plugin,
		String		id,
		String		configKey) {
		try {
			classMon.enter();
		 	if (singleton == null) {
				registrationQueue.add(new Object[] { plugin, id, configKey });
			} else {
				try {
					singleton.initializePluginFromInstance(plugin, id, configKey);
				} catch (Throwable e) {
					Debug.out(e);
				}
			}
		} finally {
			classMon.exit();
		}
	}

	protected static boolean isLoadingBuiltin() {
		return (loadingBuiltin);
	}

	public static void checkAzureusVersion(
			String name,
			Properties props,
			boolean alert_on_fail)
		throws PluginException {

		String required_version = (String)props.get("plugin.azureus.min_version");
		if (required_version == null) {return;}
		if (Constants.compareVersions(Constants.AZUREUS_VERSION, required_version) < 0) {
			String plugin_name_bit = name.length() > 0 ? (name+" "):"";
			String msg = "Plugin " + plugin_name_bit + "requires " + Constants.APP_NAME + " version " + required_version + " or higher";
			if (alert_on_fail) {
				Logger.log(new LogAlert(LogAlert.REPEATABLE, LogAlert.AT_ERROR, msg));
			}
			throw new PluginException(msg);
		}
	}

	public static void checkJDKVersion(
	String		name,
	Properties	props,
	boolean		alert_on_fail )

		throws PluginException
	{
		String	required_jdk = (String)props.get("plugin.jdk.min_version");
		if (required_jdk != null) {
			String	actual_jdk = Constants.JAVA_VERSION;
			required_jdk 	= normaliseJDK(required_jdk);
			actual_jdk	= normaliseJDK(actual_jdk);
			if (required_jdk.length() == 0 || actual_jdk.length() == 0) {
				return;
			}
			if (Constants.compareVersions(actual_jdk, required_jdk) < 0) {
				String	msg =	"Plugin " + (name.length()>0?(name+" "):"" ) + "requires Java version " + required_jdk + " or higher";
				if (alert_on_fail) {
					Logger.log(new LogAlert(LogAlert.REPEATABLE, LogAlert.AT_ERROR, msg));
				}
				throw (new PluginException( msg));
			}
		}
	}

	protected static String normaliseJDK(String	jdk) {
		try {
			String	str = "";
			// take leading digit+. portion only
			for (int i=0;i<jdk.length();i++) {
				char c = jdk.charAt(i);
				if (c == '.' || Character.isDigit( c)) {
					str += c;
				} else {
					break;
				}
			}
				// convert 5|6|... to 1.5|1.6 etc
			if (Integer.parseInt("" + str.charAt(0)) > 1) {
				str = "1." + str;
			}
			return (str);
		} catch (Throwable e) {
			return ("");
		}
	}

	protected PluginInitializer(AzureusCore _azureusCore) {
		azureusCore	= _azureusCore;
		AEDiagnostics.addEvidenceGenerator(this);
		azureusCore.addLifecycleListener(
			new AzureusCoreLifecycleAdapter() {
				public void componentCreated(
					AzureusCore					core,
					AzureusCoreComponent		comp ) {
					if (comp instanceof GlobalManager) {
						GlobalManager	gm	= (GlobalManager)comp;
						gm.addListener(PluginInitializer.this);
					}
				}
			});
		UpdateManagerImpl.getSingleton(azureusCore);	// initialise the update manager
		pluginManager = PluginManagerImpl.getSingleton(this);
		String	dynamic_plugins = System.getProperty("azureus.dynamic.plugins", null);
		if (dynamic_plugins != null) {
			String[]	classes = dynamic_plugins.split(";");
			for (String c: classes) {
				try {
					queueRegistration(Class.forName( c));
				} catch (Throwable e) {
					Debug.out("Registration of dynamic plugin '" + c + "' failed", e);
				}
			}
		}
		UpdaterUtils.checkBootstrapPlugins();
	}

	protected void fireCreated(PluginInterfaceImpl pi) {
		azureusCore.triggerLifeCycleComponentCreated(pi);
	}

	protected void fireOperational(PluginInterfaceImpl pi, boolean op) {
		fireEventSupport(op?PluginEvent.PEV_PLUGIN_OPERATIONAL:PluginEvent.PEV_PLUGIN_NOT_OPERATIONAL, pi);
	}

	public static void addInitThread() {
		synchronized(initThreads) {
			if (initThreads.contains( Thread.currentThread())) {
				Debug.out("Already added");
			}
			initThreads.add( Thread.currentThread());
		}
	}

	public static void removeInitThread() {
		synchronized(initThreads) {
			initThreads.remove( Thread.currentThread());
		}
	}

	public static boolean isInitThread() {
		synchronized(initThreads) {
			return initThreads.contains(Thread.currentThread());
		}
	}

	protected boolean isInitialisationThread() {
		return (isInitThread());
	}

	public List loadPlugins(
		AzureusCore		core,
		boolean bSkipAlreadyLoaded,
		boolean loadExternalPlugins,
		boolean loading_for_startup,
		boolean initialise_plugins) {
		
		if (bSkipAlreadyLoaded) {
			// discard any failed ones
			List pis;
			synchronized(s_pluginInterfaces) {
				pis = new ArrayList(s_pluginInterfaces);
			}
			for (int i=0;i<pis.size();i++) {
				PluginInterfaceImpl pi = (PluginInterfaceImpl)pis.get(i);
					Plugin p = pi.getPlugin();
					if (p instanceof FailedPlugin) {
						unloadPlugin(pi);
					}
				}
		}
		
		List pluginLoaded = new ArrayList();
		PluginManagerImpl.setStartDetails(core);
		getRootClassLoader();
		
		// first do explicit plugins
		File userDir = FileUtil.getUserFile("plugins");
		File appDir = FileUtil.getApplicationFile("plugins");
		
		Log.d(TAG, "userDir = " + userDir);
		Log.d(TAG, "appDir = " + appDir);
		
		int	userPlugins	= 0;
		int appPlugins = 0;
		if (userDir.exists() && userDir.isDirectory()) {
			userPlugins = userDir.listFiles().length;
		}
		if (appDir.exists() && appDir.isDirectory()) {
			appPlugins = appDir.listFiles().length;
		}
		
		// user ones first so they override app ones if present
		if (loadExternalPlugins) {
			pluginLoaded.addAll(loadPluginsFromDir(userDir, 0, userPlugins
					+ appPlugins, bSkipAlreadyLoaded, loading_for_startup, initialise_plugins));
			if (!userDir.equals(appDir)) {
				pluginLoaded.addAll(loadPluginsFromDir(appDir, userPlugins,
						userPlugins + appPlugins, bSkipAlreadyLoaded, loading_for_startup, initialise_plugins));
			}
		} else {
			if (Logger.isEnabled()) {
				Logger.log(new LogEvent(LOGID, "Loading of external plugins skipped"));
			}
		}
		
		if (Logger.isEnabled())
			Logger.log(new LogEvent(LOGID, "Loading built-in plugins"));
			PluginManagerDefaults	def = PluginManager.getDefaults();
			for (int i=0;i<builtinPlugins.length;i++) {
				if (def.isDefaultPluginEnabled( builtinPlugins[i][0])) {
					try {
						loadingBuiltin	= true;
						// lazyness here, for builtin we use static load method with default plugin interface
						// if we need to improve on this then we'll have to move to a system more akin to
						// the dir-loaded plugins
						Class	cla = rootClassLoader.loadClass( builtinPlugins[i][1]);
							Method	load_method = cla.getMethod("load", new Class[]{ PluginInterface.class });
							load_method.invoke( null, new Object[]{ getDefaultInterfaceSupport() });
					Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING,
							"Built-in plugin '" + builtinPlugins[i][0] + "' ok"));
					} catch (NoSuchMethodException e) {
					} catch (Throwable e) {
						if (builtinPlugins[i][4].equalsIgnoreCase("true")) {
							Debug.printStackTrace(e);
							Logger.log(new LogAlert(LogAlert.UNREPEATABLE,
									"Load of built in plugin '" + builtinPlugins[i][2] + "' fails", e));
						}
					} finally {
						loadingBuiltin = false;
					}
				} else {
					if (Logger.isEnabled())
						Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING,
								"Built-in plugin '" + builtinPlugins[i][2] + "' is disabled"));
				}
			}
			
 		if (Logger.isEnabled())
			Logger.log(new LogEvent(LOGID, "Loading dynamically registered plugins"));
		for (int i=0;i<registrationQueue.size();i++) {
			Object	entry = registrationQueue.get(i);
			Class	cla;
			String	id;
			if (entry instanceof Class) {
					cla = (Class)entry;
					id	= cla.getName();
			} else {
				Object[]	x = (Object[])entry;
				Plugin	plugin = (Plugin)x[0];
				cla	= plugin.getClass();
				id	= (String)x[1];
			}
			try {
				// lazyness here, for dynamic we use static load method with default plugin interface
				// if we need to improve on this then we'll have to move to a system more akin to
				// the dir-loaded plugins
				Method	loadMethod = cla.getMethod("load", new Class[]{ PluginInterface.class });
					loadMethod.invoke( null, new Object[]{ getDefaultInterfaceSupport() });
			} catch (NoSuchMethodException e) {
			} catch (Throwable e) {
				Debug.printStackTrace(e);
				Logger.log(new LogAlert(LogAlert.UNREPEATABLE,
						"Load of dynamic plugin '" + id + "' fails", e));
			}
		}
		return pluginLoaded;
	}

	private void getRootClassLoader() {
		// first do explicit plugins
		File	user_dir = FileUtil.getUserFile("shared");
		getRootClassLoader(user_dir);
		File	app_dir	 = FileUtil.getApplicationFile("shared");
		if (!user_dir.equals( app_dir)) {
			getRootClassLoader(app_dir);
		}
	}

 	private void getRootClassLoader(File dir) {
 		dir = new File(dir, "lib");
 		if (dir.exists() && dir.isDirectory()) {
 			File[]	files = dir.listFiles();
 			if (files != null) {
 				files = PluginLauncherImpl.getHighestJarVersions(files, new String[]{ null }, new String[]{ null }, false);
 				for (int i=0;i<files.length;i++) {
 				 	if (Logger.isEnabled())
 						Logger.log(new LogEvent(LOGID, "Share class loader extended by " + files[i].toString()));
 				 	rootClassLoader =
 				 		PluginLauncherImpl.addFileToClassPath(
 				 				PluginInitializer.class.getClassLoader(),
 				 				rootClassLoader, files[i]);
 				}
 			}
 		}
	}

	private List loadPluginsFromDir(
		File	pluginDirectory,
		int		pluginOffset,
		int		pluginTotal,
		boolean bSkipAlreadyLoaded,
		boolean loadingForStartup,
		boolean initialise) {
		
		/*Log.d(TAG, "loadPluginsFromDir() is called...");
		new Throwable().printStackTrace();*/
		
		List dirLoadedPIs = new ArrayList();
		if (Logger.isEnabled())
			Logger.log(new LogEvent(LOGID, "Plugin Directory is " + pluginDirectory));
		if (!pluginDirectory.exists()) {
			FileUtil.mkdirs(pluginDirectory);
		}
		if (pluginDirectory.isDirectory()) {
			File[] pluginsDirectory = pluginDirectory.listFiles();
			for (int i = 0 ; i < pluginsDirectory.length ; i++) {
				if (pluginsDirectory[i].getName().equals("CVS" )) {
					if (Logger.isEnabled())
						Logger.log(new LogEvent(LOGID, "Skipping plugin "
								+ pluginsDirectory[i].getName()));
					continue;
				}
		
				if (Logger.isEnabled())
						Logger.log(new LogEvent(LOGID, "Loading plugin "
								+ pluginsDirectory[i].getName()));
				try {
					List loadedPIs = loadPluginFromDir(pluginsDirectory[i], bSkipAlreadyLoaded, loadingForStartup, initialise);
					// save details for later initialisation
					loadedPIList.add(loadedPIs);
					dirLoadedPIs.addAll(loadedPIs);
				} catch (PluginException e) {
					// already handled
				}
			}
		}
		return dirLoadedPIs;
	}

	private List loadPluginFromDir(
			File directory,
			boolean bSkipAlreadyLoaded,
			boolean loadingForStartup,
			boolean initialise) // initialise setting is used if loading_for_startup isnt
		throws PluginException
	{
		List	loadedPlugins = new ArrayList();
		ClassLoader pluginClassLoader = rootClassLoader;
		if (!directory.isDirectory()) {
			return (loadedPlugins);
		}
		String pluginName = directory.getName();
		File[] pluginContents = directory.listFiles();
		if (pluginContents == null || pluginContents.length == 0) {
			return (loadedPlugins);
		}
		
		// first sanity check - dir must include either a plugin.properties or
		// at least one .jar file
		boolean	looksLikePlugin	= false;
		for (int i=0;i<pluginContents.length;i++) {
			String	name = pluginContents[i].getName().toLowerCase();
			if (name.endsWith(".jar") || name.equals("plugin.properties")) {
				looksLikePlugin = true;
				break;
			}
		}
		
		if (!looksLikePlugin) {
			if (Logger.isEnabled())
				Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING,
						"Plugin directory '" + directory + "' has no plugin.properties "
						+ "or .jar files, skipping"));
			return (loadedPlugins);
		}
		
		// take only the highest version numbers of jars that look versioned
		String[]	pluginVersion = {null};
		String[]	pluginId = {null};
		pluginContents	= PluginLauncherImpl.getHighestJarVersions(pluginContents, pluginVersion, pluginId, true);
		for (int i = 0 ; i < pluginContents.length ; i++) {
			File	jarFile = pluginContents[i];
			// migration hack for i18nAZ_1.0.jar
			if (pluginContents.length > 1) {
				String	name = jarFile.getName();
				if (name.startsWith("i18nPlugin_")) {
					// non-versioned version still there, rename it
					if (Logger.isEnabled())
						Logger.log(new LogEvent(LOGID, "renaming '" + name
								+ "' to conform with versioning system"));
					jarFile.renameTo(new File(jarFile.getParent(), "i18nAZ_0.1.jar	"));
					continue;
				}
			}
			pluginClassLoader = PluginLauncherImpl.addFileToClassPath(rootClassLoader, pluginClassLoader, jarFile);
		}
		String pluginClassString = null;
		try {
			Properties props = new Properties();
			File propertiesFile = new File(directory.toString() + File.separator + "plugin.properties");
			try {
				// if properties file exists on its own then override any properties file
				// potentially held within a jar
				if (propertiesFile.exists()) {
					FileInputStream	fis = null;
					try {
						fis = new FileInputStream(propertiesFile);
						props.load(fis);
					} finally {
						if (fis != null) {
							fis.close();
						}
					}
				} else {
					if (pluginClassLoader instanceof URLClassLoader) {
						URLClassLoader	current = (URLClassLoader)pluginClassLoader;
						URL url = current.findResource("plugin.properties");
						if (url != null) {
							URLConnection connection = url.openConnection();
							InputStream is = connection.getInputStream();
							props.load(is);
						} else {
							throw (new Exception("failed to load plugin.properties from jars"));
						}
					} else {
						throw (new Exception("failed to load plugin.properties from dir or jars"));
					}
				}
			} catch (Throwable e) {
				Debug.printStackTrace(e);
				String	msg =	"Can't read 'plugin.properties' for plugin '" + pluginName + "': file may be missing";
				Logger.log(new LogAlert(LogAlert.UNREPEATABLE, LogAlert.AT_ERROR, msg));
				System.out.println(msg);
				throw (new PluginException( msg, e));
			}
			checkJDKVersion(pluginName, props, true);
			checkAzureusVersion(pluginName, props, true);
			pluginClassString = (String)props.get("plugin.class");
			if (pluginClassString == null) {
				pluginClassString = (String)props.get("plugin.classes");
				if (pluginClassString == null) {
					// set so we don't bork later will npe
					pluginClassString = "";
				}
			}
			String	pluginNameString = (String)props.get("plugin.name");
			if (pluginNameString == null) {
				pluginNameString = (String)props.get("plugin.names");
			}
			int	pos1 = 0;
			int	pos2 = 0;
			while (true) {
				int	p1 = pluginClassString.indexOf(";", pos1);
				String	pluginClass;
				if (p1 == -1) {
					pluginClass = pluginClassString.substring(pos1).trim();
				} else {
					pluginClass	= pluginClassString.substring(pos1,p1).trim();
					pos1 = p1+1;
				}
				PluginInterfaceImpl existingPI = getPluginFromClass(pluginClass);
				if (existingPI != null) {
					if (bSkipAlreadyLoaded) {
						break;
					}
					// allow user dir entries to override app dir entries without warning
					File	thisParent 	= directory.getParentFile();
					File	existingParent = null;
					if (existingPI.getInitializerKey() instanceof File) {
						existingParent	= ((File)existingPI.getInitializerKey()).getParentFile();
					}
					if (	thisParent.equals( FileUtil.getApplicationFile("plugins")) &&
							existingParent	!= null &&
							existingParent.equals(FileUtil.getUserFile("plugins"))) {
						// skip this overridden plugin
						if (Logger.isEnabled())
							Logger.log(new LogEvent(LOGID, "Plugin '" + pluginNameString
									+ "/" + pluginClass
									+ ": shared version overridden by user-specific one"));
						return (new ArrayList());
					} else {
						Logger.log(new LogAlert(LogAlert.UNREPEATABLE, LogAlert.AT_WARNING,
								"Error loading '" + pluginNameString + "', plugin class '"
								+ pluginClass + "' is already loaded"));
					}
				} else {
					String	plugin_name = null;
					if (pluginNameString != null) {
						int	p2 = pluginNameString.indexOf(";", pos2);

						if (p2 == -1) {
							plugin_name = pluginNameString.substring(pos2).trim();
						} else {
							plugin_name	= pluginNameString.substring(pos2,p2).trim();
							pos2 = p2+1;
						}
					}
					Properties newProps = (Properties)props.clone();
					for (int j=0;j<default_version_details.length;j++) {
						if (pluginClass.equals(default_version_details[j][0])) {
							if (newProps.get("plugin.id") == null) {
								newProps.put("plugin.id", default_version_details[j][1]);
							}
							if (plugin_name == null) {
								plugin_name	= default_version_details[j][2];
							}
							if (newProps.get("plugin.version") == null) {
								// no explicit version. If we've derived one then use that, otherwise defaults
								if (pluginVersion[0] != null) {
									newProps.put("plugin.version", pluginVersion[0]);
								} else {
									newProps.put("plugin.version", default_version_details[j][3]);
								}
							}
						}
					}
					newProps.put("plugin.class", pluginClass);
					if (plugin_name != null) {
						newProps.put("plugin.name", plugin_name);
					}
					// System.out.println("loading plugin '" + plugin_class + "' using cl " + classLoader);
					// if the plugin load fails we still need to generate a plugin entry
					// as this drives the upgrade process

					Throwable	load_failure	= null;
					String pid = pluginId[0]==null?directory.getName():pluginId[0];
					List<File>	verified_files = null;
					Plugin plugin = null;
					if (vcDisabledPlugins.contains ( pid)) {
						log("Plugin '" + pid + "' has been administratively disabled");
					} else {
						try {
							String cl_key = "plugin.cl.ext." + pid;
							String str = COConfigurationManager.getStringParameter(cl_key, null);
							if (str != null && str.length() > 0) {
								COConfigurationManager.removeParameter(cl_key);
								pluginClassLoader = PluginLauncherImpl.extendClassLoader(rootClassLoader, pluginClassLoader, new URL( str));
							}
						} catch (Throwable e) {
						}
						if (pid.endsWith("_v")) {
							verified_files = new ArrayList<File>();
							// re-verify jar files
							log("Re-verifying " + pid);
							for ( int i = 0 ; i < pluginContents.length ; i++) {
								File	jarFile = pluginContents[i];
								if (jarFile.getName().endsWith(".jar")) {
									try {
										log("	verifying " + jarFile);
										AEVerifier.verifyData(jarFile);
										verified_files.add(jarFile);
										log("	OK");
									} catch (Throwable e) {
										String	msg = "Error loading plugin '" + pluginName + "' / '" + pluginClassString + "'";
										Logger.log(new LogAlert(LogAlert.UNREPEATABLE, msg, e));
										plugin = new FailedPlugin(plugin_name,directory.getAbsolutePath());
									}
								}
							}
						}
						if (plugin == null) {
							plugin = PluginLauncherImpl.getPreloadedPlugin(pluginClass);
							if (plugin == null) {
								try {
									try {
										Class<Plugin> c = (Class<Plugin>)PlatformManagerFactory.getPlatformManager().loadClass(pluginClassLoader, pluginClass);

										//Class c = plugin_class_loader.loadClass(plugin_class);
										plugin	= c.newInstance();
										try {
											// kick off any pre-inits
											if (pluginClassLoader instanceof URLClassLoader) {
												URL[] urls = ((URLClassLoader)pluginClassLoader).getURLs();
												for (URL u: urls) {
													String path = u.getPath();
													if (path.endsWith(".jar")) {
														int	s1 = path.lastIndexOf('/');
														int	s2 = path.lastIndexOf('\\');
														path = path.substring(Math.max( s1, s2)+1);
														s2 = path.indexOf('_');
														if (s2 > 0) {
															path = path.substring(0, s2);
															path = path.replaceAll("-", "");
															String cl = "plugin.preinit." + pid + ".PI" + path;
															try {
																Class pic = pluginClassLoader.loadClass(cl);
																if (pic != null) {
																	pic.newInstance();
																}
															} catch (Throwable e) {
															}
														}
													}
												}
											}
										} catch (Throwable e) {
										}
									} catch (PlatformManagerException e) {
										throw ( e.getCause());
									}
								} catch (java.lang.UnsupportedClassVersionError e) {
									plugin = new FailedPlugin(plugin_name,directory.getAbsolutePath());
									// shorten stack trace
									load_failure	= new UnsupportedClassVersionError(e.getMessage());
								} catch (Throwable e) {
									if (	e instanceof ClassNotFoundException &&
											props.getProperty("plugin.install_if_missing", "no" ).equalsIgnoreCase("yes")) {
										// don't report the failure
									} else {
										load_failure	= e;
									}
									plugin = new FailedPlugin(plugin_name,directory.getAbsolutePath());
								}
							} else {
								pluginClassLoader = plugin.getClass().getClassLoader();
							}
						}
						MessageText.integratePluginMessages((String)props.get("plugin.langfile"),pluginClassLoader);
						PluginInterfaceImpl pluginInterface =
							new PluginInterfaceImpl(
									plugin,
									this,
									directory,
									pluginClassLoader,
									verified_files,
									directory.getName(),	// key for config values
									newProps,
									directory.getAbsolutePath(),
									pid,
									pluginVersion[0]);
						boolean bEnabled = (loadingForStartup) ? pluginInterface.getPluginState().isLoadedAtStartup() : initialise;
						pluginInterface.getPluginState().setDisabled(!bEnabled);
						try {
							Method	load_method = plugin.getClass().getMethod("load", new Class[]{ PluginInterface.class });
							load_method.invoke( plugin, new Object[]{ pluginInterface });
						} catch (NoSuchMethodException e) {
						} catch (Throwable e) {
							load_failure	= e;
						}
						loadedPlugins.add(pluginInterface);
						if (load_failure != null) {
							pluginInterface.setAsFailed();
								// don't complain about our internal one
							if (!pid.equals(UpdaterUpdateChecker.getPluginID())) {
								String msg =
									MessageText.getString("plugin.init.load.failed",
									new String[]{
											plugin_name==null?pluginName:plugin_name,
											directory.getAbsolutePath()
										});
								LogAlert la;
								if (load_failure instanceof UnsupportedClassVersionError) {
									la = new LogAlert(LogAlert.UNREPEATABLE, LogAlert.AT_ERROR, msg + ".\n\n" + MessageText.getString("plugin.install.class_version_error"));
								} else if (load_failure instanceof ClassNotFoundException) {
									la = new LogAlert(LogAlert.UNREPEATABLE, LogAlert.AT_ERROR, msg + ".\n\n" + MessageText.getString("plugin.init.load.failed.classmissing") + "\n\n", load_failure);
								} else {
									la = new LogAlert(LogAlert.UNREPEATABLE, msg, load_failure);
								}
								Logger.log(la);
								System.out.println( msg + ": " + load_failure);
							}
						}
					}
				}
				if (p1 == -1) {
					break;
				}
			}
			return (loadedPlugins);
		} catch (Throwable e) {
			if (e instanceof PluginException) {
				throw ((PluginException)e);
			}
			Debug.printStackTrace(e);
			String	msg = "Error loading plugin '" + pluginName + "' / '" + pluginClassString + "'";
			Logger.log(new LogAlert(LogAlert.UNREPEATABLE, msg, e));
			System.out.println( msg + ": " + e);
			throw (new PluginException( msg, e));
		}
	}

	private void log(String str) {
		if (Logger.isEnabled()) {
			Logger.log(new LogEvent(LOGID, str ));
		}
	}

	public void initialisePlugins() {
		try {
			addInitThread();
			final LinkedList<Runnable> initQueue = new LinkedList<Runnable>();
			for (int i = 0; i < loadedPIList.size(); i++) {
				final int idx = i;
				initQueue.add(new Runnable() {
					public void run() {
						try {
							List l = (List) loadedPIList.get(idx);
							if (l.size() > 0) {
								PluginInterfaceImpl pluginInterface = (PluginInterfaceImpl) l.get(0);
								if (Logger.isEnabled())
									Logger.log(new LogEvent(LOGID, "Initializing plugin '"
											+ pluginInterface.getPluginName() + "'"));
								initialisePlugin(l);
								if (Logger.isEnabled())
									Logger.log(new LogEvent(LOGID, "Initialization of plugin '"
											+ pluginInterface.getPluginName() + "' complete"));
							}
						} catch (Throwable e) {
							// already handled
						}
						// some plugins try and steal the logger stdout redirects.
						// re-establish them if needed
						Logger.doRedirects();
					}
				});
			}

			// now do built in ones
			initQueue.add(new Runnable() {
				public void run() {
					if (Logger.isEnabled())
						Logger.log(new LogEvent(LOGID, "Initializing built-in plugins"));
				}
			});

			final PluginManagerDefaults def = PluginManager.getDefaults();
			for (int i = 0; i < builtinPlugins.length; i++) {
				final int idx = i;
				initQueue.add(new Runnable() {
					public void run() {
						if (def.isDefaultPluginEnabled(builtinPlugins[idx][0])) {
							String id = builtinPlugins[idx][2];
							String key = builtinPlugins[idx][3];
							try {
								Class cls = rootClassLoader.loadClass(
										builtinPlugins[idx][1]);
								if (Logger.isEnabled())
									Logger.log(new LogEvent(LOGID, "Initializing built-in plugin '"
											+ builtinPlugins[idx][2] + "'" ));
								initializePluginFromClass(cls, id, key, "true".equals(builtinPlugins[idx][5]), true, true);
								if (Logger.isEnabled())
								Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING,
										"Initialization of built in plugin '" + builtinPlugins[idx][2] + "' complete"));
							} catch (Throwable e) {
								try {
									// replace it with a "broken" plugin instance
									initializePluginFromClass(FailedPlugin.class, id, key, false, false, true);
								} catch (Throwable f) {
								}
								if (builtinPlugins[idx][4].equalsIgnoreCase("true")) {
									Debug.printStackTrace(e);
									Logger.log(new LogAlert(LogAlert.UNREPEATABLE,
											"Initialization of built in plugin '" + builtinPlugins[idx][2]
													+ "' fails", e));
								}
							}
						} else {
							if (Logger.isEnabled())
								Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING,
										"Built-in plugin '" + builtinPlugins[idx][2] + "' is disabled"));
						}
					}
				});
			}
			initQueue.add(new Runnable() {
				public void run() {
					if (Logger.isEnabled())
						Logger.log(new LogEvent(LOGID,
								"Initializing dynamically registered plugins"));
				}
			});
			for (int i = 0; i < registrationQueue.size(); i++) {
				final int idx = i;
				initQueue.add(new Runnable() {
					public void run() {
						try {
							Object entry = registrationQueue.get(idx);
							if (entry instanceof Class) {
								Class cla = (Class) entry;
								singleton.initializePluginFromClass(cla, INTERNAL_PLUGIN_ID, cla
										.getName(), false, true, true);
							} else {
								Object[] x = (Object[]) entry;
								Plugin plugin = (Plugin) x[0];
								singleton.initializePluginFromInstance(plugin, (String) x[1], (String)x[2]);
							}
						} catch (PluginException e) {
						}
					}
				});
			}
			
			AEThread2 secondaryInitializer =
				new AEThread2("2nd PluginInitializer Thread",true) {
					public void run() {
						try {
							addInitThread();
							while (true) {
								Runnable toRun;
								synchronized (initQueue) {
									if (initQueue.isEmpty()) {
										break;
									}
									toRun = (Runnable)initQueue.remove(0);
								}
								try {
									toRun.run();
								} catch (Throwable e) {
									Debug.out(e);
								}
							}
						} finally {
							removeInitThread();
						}
					}
				};
			secondaryInitializer.start();
			while (true) {
				Runnable toRun;
				synchronized(initQueue) {
					if (initQueue.isEmpty()) {
						break;
					}
					toRun = (Runnable)initQueue.remove(0);
				}
				try {
					toRun.run();
				} catch (Throwable e) {
					Debug.out(e);
				}
			}
			secondaryInitializer.join();
			registrationQueue.clear();
			pluginsInitialised = true;
			fireEvent(PluginEvent.PEV_ALL_PLUGINS_INITIALISED);
		} finally {
			removeInitThread();
		}
	}

	protected void checkPluginsInitialised() {
		if (!pluginsInitialised) {
			Debug.out("Wait until plugin initialisation is complete until doing this!");
		}
	}

	protected boolean isInitialized() {
		return (pluginsInitialised);
	}

	private void initialisePlugin(List l) throws PluginException {
		PluginException	last_load_failure = null;
		for (int i=0;i<l.size();i++) {
			final PluginInterfaceImpl	pluginInterface = (PluginInterfaceImpl)l.get(i);
			if (pluginInterface.getPluginState().isDisabled()) {
				synchronized(s_pluginInterfaces) {
					s_pluginInterfaces.add(pluginInterface);
				}
				continue;
			}
			if (pluginInterface.getPluginState().isOperational()) {
				continue;
			}
			
			Throwable		loadFailure = null;
			final Plugin	plugin = pluginInterface.getPlugin();
			try {
				UtilitiesImpl.callWithPluginThreadContext(
					pluginInterface,
					new runnableWithException<PluginException>() {
						public void run() throws PluginException {
							fireCreated(pluginInterface);
							plugin.initialize(pluginInterface);
							if (!(plugin instanceof FailedPlugin)) {
								pluginInterface.getPluginStateImpl().setOperational(true, false);
							}
						}
					});
			} catch (Throwable e) {
				loadFailure	= e;
			}
			synchronized(s_pluginInterfaces) {
				s_plugins.add(plugin);
				s_pluginInterfaces.add(pluginInterface);
			}
			if (loadFailure != null) {
				Debug.printStackTrace(loadFailure);
				String	msg = "Error initializing plugin '" + pluginInterface.getPluginName() + "'";
				Logger.log(new LogAlert(LogAlert.UNREPEATABLE, msg, loadFailure));
				System.out.println( msg + " : " + loadFailure);
				last_load_failure = new PluginException(msg, loadFailure);
			}
		}
		if (last_load_failure != null) {
			throw (last_load_failure);
		}
	}

	protected void initializePluginFromClass(
		final Class 	pluginClass,
		final String	pluginId,
		String			pluginConfigKey,
		boolean 		forceEnabled,
		boolean 		loading_for_startup,
		boolean 		initialise) throws PluginException {

		if (pluginClass != FailedPlugin.class && getPluginFromClass(pluginClass) != null) {
			Logger.log(new LogAlert(LogAlert.UNREPEATABLE, LogAlert.AT_WARNING,
					"Error loading '" + pluginId + "', plugin class '"
							+ pluginClass.getName() + "' is already loaded"));
			return;
		}

		try {
			final Plugin plugin = (Plugin) pluginClass.newInstance();
			String	pluginName;

			if (pluginConfigKey.length() == 0) {
				pluginName = pluginClass.getName();
				int	pos = pluginName.lastIndexOf(".");

				if (pos != -1) {
					pluginName = pluginName.substring(pos+1);
				}
			} else {
				pluginName = pluginConfigKey;
			}

			Properties properties = new Properties();
			
			// default plugin name
			properties.put("plugin.name", pluginName);

			final PluginInterfaceImpl pluginInterface =
				new PluginInterfaceImpl(
						plugin,
						this,
						pluginClass,
						pluginClass.getClassLoader(),
						null,
						pluginConfigKey,
						properties,
						"",
						pluginId,
						null);

			boolean bEnabled = (loading_for_startup) ? pluginInterface.getPluginState().isLoadedAtStartup() : initialise;

			/**
			 * For some plugins, override any config setting which disables the plugin.
			 */
			if (forceEnabled && !bEnabled) {
				pluginInterface.getPluginState().setLoadedAtStartup(true);
				bEnabled = true;
				Logger.log(new LogAlert(false, LogAlert.AT_WARNING, MessageText.getString(
						"plugins.init.force_enabled", new String[] {pluginId}
				)));
			}

			pluginInterface.getPluginState().setDisabled(!bEnabled);

			final boolean f_enabled = bEnabled;

			UtilitiesImpl.callWithPluginThreadContext(
				pluginInterface,
				new runnableWithException<PluginException>() {
					public void run() throws PluginException {
						try {
							Method	loadMethod = pluginClass.getMethod("load", new Class[]{ PluginInterface.class });
							loadMethod.invoke(plugin, new Object[]{ pluginInterface });
						} catch (NoSuchMethodException e) {
						} catch (Throwable e) {
							Debug.printStackTrace(e);
							Logger.log(new LogAlert(LogAlert.UNREPEATABLE,
									"Load of built in plugin '" + pluginId + "' fails", e));
						}

						if (f_enabled) {
							fireCreated(pluginInterface);
							plugin.initialize(pluginInterface);

							if (!(plugin instanceof FailedPlugin)) {
								pluginInterface.getPluginStateImpl().setOperational(true, false);
							}
						}
					}
				});

			synchronized(s_pluginInterfaces) {
				s_plugins.add(plugin);
				s_pluginInterfaces.add(pluginInterface);
			}
		} catch (Throwable e) {
			Debug.printStackTrace(e);
			String	msg = "Error loading internal plugin '" + pluginClass.getName() + "'";
			Logger.log(new LogAlert(LogAlert.UNREPEATABLE, msg, e));
			System.out.println(msg + " : " + e);
			throw (new PluginException( msg, e));
		}
	}

	protected void initializePluginFromInstance(
		final Plugin	plugin,
		String			pluginId,
		String			pluginConfigKey)
		throws PluginException {
		
		try {
			final PluginInterfaceImpl plugin_interface =
				new PluginInterfaceImpl(
						plugin,
						this,
						plugin.getClass(),
						plugin.getClass().getClassLoader(),
						null,
						pluginConfigKey,
						new Properties(),
						"",
						pluginId,
						null);

		UtilitiesImpl.callWithPluginThreadContext(
			plugin_interface,
			new UtilitiesImpl.runnableWithException<PluginException>() {
				public void run() throws PluginException {
					fireCreated(plugin_interface);
						plugin.initialize(plugin_interface);
						if (!(plugin instanceof FailedPlugin)) {
							plugin_interface.getPluginStateImpl().setOperational(true, false);
						}
				}
			});
			
			synchronized(s_pluginInterfaces) {
				s_plugins.add(plugin);
				s_pluginInterfaces.add(plugin_interface);
			}
		} catch (Throwable e) {
			Debug.printStackTrace(e);
			String	msg = "Error loading internal plugin '" + plugin.getClass().getName() + "'";
			Logger.log(new LogAlert(LogAlert.UNREPEATABLE, msg, e));
			System.out.println(msg + " : " + e);
			throw (new PluginException( msg, e));
		}
	}

	protected void unloadPlugin(PluginInterfaceImpl		pi) {
		synchronized(s_pluginInterfaces) {
			s_plugins.remove( pi.getPlugin());
			s_pluginInterfaces.remove(pi);
		}
		pi.unloadSupport();
		for (int i=0;i<loadedPIList.size();i++) {
			List	l = (List)loadedPIList.get(i);
			if (l.remove(pi)) {
				if (l.size() == 0) {
					loadedPIList.remove(i);
				}
				break;
			}
		}
		verifiedPluginHolder.removeValue(pi);
	}

	protected void reloadPlugin(PluginInterfaceImpl pi) throws PluginException {
		reloadPlugin(pi, false, true);
	}

	protected void reloadPlugin(
			PluginInterfaceImpl pi,
			boolean loading_for_startup,
			boolean initialise) throws PluginException {
		unloadPlugin(pi);
		Object key 			= pi.getInitializerKey();
		String config_key	= pi.getPluginConfigKey();
		if (key instanceof File) {
			List	pis = loadPluginFromDir( (File)key, false, loading_for_startup, initialise);
			initialisePlugin(pis);
		} else {
			initializePluginFromClass((Class) key, pi.getPluginID(), config_key, false, loading_for_startup, initialise);
		}
	}

	protected AzureusCore getAzureusCore() {
		return (azureusCore);
	}

	protected GlobalManager getGlobalManager() {
		return (azureusCore.getGlobalManager());
	}

	public static PluginInterface getDefaultInterface() {
		if (singleton == null) {
			throw new AzureusCoreException(
					"PluginInitializer not instantiated by AzureusCore.create yet");
		}
		return (singleton.getDefaultInterfaceSupport());
	}

	protected PluginInterface getDefaultInterfaceSupport() {

		synchronized(s_pluginInterfaces) {
		if (defaulPlugin == null) {

				try {
					defaulPlugin =
						new PluginInterfaceImpl(
								new Plugin() {
									public void initialize(PluginInterface pi) {}
							},
							this,
							getClass(),
							getClass().getClassLoader(),
							null,
							"default",
							new Properties(),
							null,
							INTERNAL_PLUGIN_ID,
							null);

				} catch (Throwable e) {
					Debug.out(e);
				}
			}
		}

		return (defaulPlugin);
	}

	public void downloadManagerAdded(DownloadManager dm) {
	}

	public void downloadManagerRemoved(DownloadManager dm) {
	}

	public void destroyInitiated() {
		List plugin_interfaces;
		synchronized(s_pluginInterfaces) {
			plugin_interfaces = new ArrayList(s_pluginInterfaces);
		}
		for (int i=0;i<plugin_interfaces.size();i++) {
			((PluginInterfaceImpl)plugin_interfaces.get(i)).closedownInitiated();
		}
		if (defaulPlugin != null) {
			defaulPlugin.closedownInitiated();
		}
	}

	public void destroyed() {
		List plugin_interfaces;
		synchronized(s_pluginInterfaces) {
			plugin_interfaces = new ArrayList(s_pluginInterfaces);
		}
		for (int i=0;i<plugin_interfaces.size();i++) {
			((PluginInterfaceImpl)plugin_interfaces.get(i)).closedownComplete();
		}
		if (defaulPlugin != null) {
			defaulPlugin.closedownComplete();
		}
	}


	public void seedingStatusChanged(boolean seeding_only_mode, boolean b) {
		/*nothing*/
	}

	protected void runPEVTask(AERunnable run) {
		asyncDispatcher.dispatch(run);
	}


	protected List<PluginEvent> getPEVHistory() {
		return (pluginEventHistory);
	}

	protected void fireEventSupport(
		final int		type,
		final Object	value ) {
		asyncDispatcher.dispatch(
		 new AERunnable() {
			 public void runSupport() {
					PluginEvent	ev =
						new PluginEvent() {
							public int getType() {
								return (type);
							}
							public Object getValue() {
								return (value);
							}
						};
					if (	type == PluginEvent.PEV_CONFIGURATION_WIZARD_STARTS ||
							type == PluginEvent.PEV_CONFIGURATION_WIZARD_COMPLETES ||
							type == PluginEvent.PEV_INITIAL_SHARING_COMPLETE ||
							type == PluginEvent.PEV_INITIALISATION_UI_COMPLETES ||
							type == PluginEvent.PEV_ALL_PLUGINS_INITIALISED) {
						pluginEventHistory.add(ev);
						if (pluginEventHistory.size() > 1024) {
							Debug.out("Plugin event history too large!!!!");
							pluginEventHistory.remove(0);
						}
					}
					List pluginInterfaces;
					synchronized(s_pluginInterfaces) {
						pluginInterfaces = new ArrayList(s_pluginInterfaces);
					}
					for (int i=0;i<pluginInterfaces.size();i++) {
						try {
							((PluginInterfaceImpl)pluginInterfaces.get(i)).firePluginEventSupport(ev);
						} catch (Throwable e) {
							Debug.printStackTrace(e);
						}
					}
			 	if (defaulPlugin != null) {
						defaulPlugin.firePluginEventSupport(ev);
					}
				}
		 });
	}

	private void waitForEvents() {
		if (asyncDispatcher.isDispatchThread()) {
			Debug.out("Deadlock - recode this monkey boy");
		} else {
			final AESemaphore sem = new AESemaphore("waiter");
			asyncDispatcher.dispatch(
				new AERunnable() {
					public void runSupport() {
						sem.release();
					}
				});
			if (!sem.reserve( 10*1000)) {
				Debug.out("Timeout waiting for event dispatch");
			}
		}
	}

	public static void fireEvent(int type) {
		singleton.fireEventSupport(type, null);
	}

	public static void fireEvent(int type, Object value) {
		singleton.fireEventSupport(type, value);
	}

	public static void waitForPluginEvents() {
		singleton.waitForEvents();
	}

	public void initialisationComplete() {
		initialisationComplete	= true;
		UIManagerImpl.initialisationComplete();
		List pluginInterfaces;
		synchronized(s_pluginInterfaces) {
			pluginInterfaces = new ArrayList(s_pluginInterfaces);
		}
		for (int i=0;i<pluginInterfaces.size();i++) {
			((PluginInterfaceImpl)pluginInterfaces.get(i)).initialisationComplete();
		}
		
		// keep this last as there are things out there that rely on the init complete of the
		// default interface meaning that everything else is complete and informed complete
		if (defaulPlugin != null) {
			defaulPlugin.initialisationComplete();
		}
	}

	protected boolean isInitialisationComplete() {
		return (initialisationComplete);
	}


	public static List<PluginInterfaceImpl> getPluginInterfaces() {
		return singleton.getPluginInterfacesSupport(false);
	}

	private List<PluginInterfaceImpl> getPluginInterfacesSupport(boolean expect_partial_result) {
		if (!expect_partial_result) {
			checkPluginsInitialised();
		}
		synchronized(s_pluginInterfaces) {
			return (new ArrayList<PluginInterfaceImpl>( s_pluginInterfaces));
		}
	}

	public PluginInterface[] getPlugins() {
		return (getPlugins( false));
	}

	public PluginInterface[] getPlugins(boolean	expect_partial_result ) {
		List	pis = getPluginInterfacesSupport(expect_partial_result);
		PluginInterface[]	res = new 	PluginInterface[pis.size()];
		pis.toArray(res);
		return (res);
	}

	protected PluginManager getPluginManager() {
		return (pluginManager);
	}

	protected PluginInterfaceImpl getPluginFromClass(Class	cla ) {
		return (getPluginFromClass(cla.getName()));
	}

	protected PluginInterfaceImpl getPluginFromClass(String	className) {
		List pluginInterfaces;
		synchronized(s_pluginInterfaces) {
			pluginInterfaces = new ArrayList(s_pluginInterfaces);
		}
		for (int i=0;i<pluginInterfaces.size();i++) {
			PluginInterfaceImpl	pi = (PluginInterfaceImpl)pluginInterfaces.get(i);
			if (pi.getPlugin().getClass().getName().equals(className)) {
				return (pi);
			}
		}
		
		// fall back to the loaded but not-yet-initialised list
		for (int i=0;i<loadedPIList.size();i++) {
			List	l = (List)loadedPIList.get(i);
			for (int j=0;j<l.size();j++) {
				PluginInterfaceImpl	pi = (PluginInterfaceImpl)l.get(j);
				if (pi.getPlugin().getClass().getName().equals(className)) {
						return (pi);
					}
			}
		}
		return (null);
	}


	public void generate(
		IndentWriter		writer) {
		writer.println("Plugins");

		try {
			writer.indent();

			List plugin_interfaces;

			synchronized(s_pluginInterfaces) {

				plugin_interfaces = new ArrayList(s_pluginInterfaces);
			}

		 	for (int i=0;i<plugin_interfaces.size();i++) {

					PluginInterfaceImpl	pi = (PluginInterfaceImpl)plugin_interfaces.get(i);

					pi.generateEvidence(writer);
		 	}

		} finally {

			writer.exdent();
		}
	}

	protected static void
	setVerified(
		PluginInterfaceImpl		pi,
		Plugin					plugin,
		boolean					v,
		boolean					bad )

		throws PluginException
	{
		Object[] existing = (Object[])verifiedPluginHolder.setValue( pi, new Object[]{ plugin, v });

		if (existing != null && ( existing[0] != plugin || (Boolean)existing[1] != v)) {

			throw (new PluginException("Verified status change not permitted"));
		}

				if (bad && !DISABLE_PLUGIN_VERIFICATION) {

			throw (new RuntimeException("Plugin verification failed"));
		}
	}

	public static boolean
	isVerified(
		PluginInterface		pi,
		Plugin				plugin) {
		if (!( pi instanceof PluginInterfaceImpl)) {

			return (false);
		}

		VerifiedPluginHolder holder = verifiedPluginHolder;

		if (holder.getClass() != VerifiedPluginHolder.class) {

			Debug.out("class mismatch");

			return (false);
		}

		if (DISABLE_PLUGIN_VERIFICATION) {

			Debug.out(" **************************** VERIFICATION DISABLED ******************");

			return (true);
		}

		Object[] ver = (Object[])verifiedPluginHolder.getValue(pi);

		return (ver != null && ver[0] == plugin && (Boolean)ver[1]);
	}

	public static boolean
	isCoreOrVerifiedPlugin() {
		Class<?>[] stack = SESecurityManager.getClassContext();

		ClassLoader core = PluginInitializer.class.getClassLoader();

		PluginInitializer singleton = peekSingleton();

		PluginInterface[] pis = singleton==null?new PluginInterface[0]:singleton.getPlugins();

		Set<ClassLoader>	ok_loaders = new HashSet<ClassLoader>();

		ok_loaders.add(core);

		for (Class<?> c: stack) {

			ClassLoader cl = c.getClassLoader();

			if (cl != null && !ok_loaders.contains( cl)) {

				boolean ok = false;

				for (PluginInterface pi: pis) {

					Plugin plugin = pi.getPlugin();

					if (plugin.getClass().getClassLoader() == cl) {

						if (isVerified( pi, plugin)) {

							ok_loaders.add(cl);

							ok = true;

							break;
						}
					}
				}

				if (!ok) {

					Debug.out("Class " + c.getCanonicalName() + " with loader " + cl + " isn't trusted");

					return (false);
				}
			}
		}

		return (true);
	}

	private static final class
	VerifiedPluginHolder
	{
		private static final Object	NULL_VALUE = new Object();

		private volatile boolean initialised;

		private AESemaphore	request_sem = new AESemaphore("ValueHolder");

		private List<Object[]>	request_queue = new ArrayList<Object[]>();

		private VerifiedPluginHolder() {
			Class[] context = SESecurityManager.getClassContext();

			if (context.length == 0) {

				return;
			}

			if (context[2] != PluginInitializer.class) {

				Debug.out("Illegal operation");

				return;
			}

			AEThread2 t =
				new AEThread2("PluginVerifier") {
					public void run() {
						Map<Object,Object> values = new IdentityHashMap<Object,Object>();

						while (true) {

							request_sem.reserve();

							Object[] req;

							synchronized(request_queue) {

								req = request_queue.remove(0);
							}

							if (req[1] == null) {

								req[1] = values.get(req[0]);

							} else {

								Object existing = values.get(req[0]);

								if (req[1] == NULL_VALUE) {

									req[1] = existing;

									values.remove(req[0]);

								} else {
									if (existing != null) {

										req[1] = existing;

									} else {

										values.put(req[0], req[1]);
									}
								}
							}

							((AESemaphore)req[2]).release();
						}
					}
				};

			t.start();

			initialised = true;
		}

		public Object removeValue(
			Object	key) {
			if (!initialised) {

				return (null);
			}

			AESemaphore sem = new AESemaphore("ValueHolder:remove");

			Object[] request = new Object[]{ key, NULL_VALUE, sem };

			synchronized(request_queue) {

				request_queue.add(request);
			}

			request_sem.release();

			sem.reserve();

			return (request[1]);
		}

		public Object setValue(
			Object	key,
			Object	value) {
			if (!initialised) {

				return (null);
			}

			AESemaphore sem = new AESemaphore("ValueHolder:set");

			Object[] request = new Object[]{ key, value, sem };

			synchronized(request_queue) {

				request_queue.add(request);
			}

			request_sem.release();

			sem.reserve();

			return (request[1]);
		}

		public Object getValue(
			Object	key) {
			if (!initialised) {

				return (null);
			}

			AESemaphore sem = new AESemaphore("ValueHolder:get");

			Object[] request = new Object[]{ key, null, sem };

			synchronized(request_queue) {

				request_queue.add(request);
			}

			request_sem.release();

			sem.reserve();

			return (request[1]);
		}
	}
}
