/*
 * File		: PluginManager.java
 * Created : 14-Dec-2003
 * By			: parg
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

package org.gudy.azureus2.plugins;

import java.util.List;
import java.util.Properties;

import org.gudy.azureus2.pluginsimpl.local.*;
import org.gudy.azureus2.plugins.installer.*;

/**
 * This class allows Azureus to be started as an embedded component and also allows plugins to
 * be dynamically registered
 * @author parg
 */


public abstract class PluginManager {
	
	/**
	 * No user interface
	 *
	 * @since 2.0.8.0
	 */
	public static final int	UI_NONE		= 0;
	
	/**
	 * SWT user inferface
	 *
	 * @since 2.0.6.0
	 */
	public static final int	UI_SWT		= 1;


	/**
	 * Property Key: Allow multiple instances.
	 * Normally Azureus will only permit a single instance to run per machine.
	 * Values for this key are: "true" or "false"
	 *
	 * @since 2.0.7.0
	 */
	public static final String	PR_MULTI_INSTANCE	= "MULTI_INSTANCE";

	/**
	 * Where the azureus config (i.e. per-user type) state is stored
	 * String value
	 * @since 4.9.0.1
	 */

	public static final String	PR_USER_DIRECTORY	= "USER_DIR";

	/**
	 * Where azureus is 'installed'. For embedded use you probably want to set config and app
	 * dir to a shared per-user location
	 * String value
	 * @since 4.9.0.1
	 */

	public static final String	PR_APP_DIRECTORY	= "APP_DIR";

	/**
	 * Parent folder that contains the downloads directory
	 * String value
	 * @since 4.9.0.1
	 */

	public static final String	PR_DOC_DIRECTORY	= "DOC_DIR";

	/**
	 * Set this to "true" (String) if you want to disable any native platform support
	 * String value
	 * @since 4.9.0.1
	 */

	public static final String	PR_DISABLE_NATIVE_SUPPORT	= "DISABLE_NATIVE";


	public static PluginManagerDefaults getDefaults() {
		return (PluginManagerDefaultsImpl.getSingleton());
	}

	/**
	 * Runs Azureus
	 * @param ui_type Type of user interface to provide.	See UI_* Constants
	 * @param properties A list of properties to pass Azureus.	See PR_* constants.
	 *
	 * @since 2.0.6.0
	 */
	public static PluginManager startAzureus(
		int			ui_type,
		Properties	properties) {
		return (PluginManagerImpl.startAzureus( ui_type, properties));
	}

	/**
	 * Shuts down Azureus
	 * @throws PluginException
	 *
	 * @since 2.0.8.0
	 */
	public static void stopAzureus()

		throws PluginException
	{
		PluginManagerImpl.stopAzureus();
	}

	/**
	 * restarts azureus and performs any Update actions defined via the plugin "update"
	 * interface. Currently only works for SWT UIs.
	 * @throws PluginException
	 *
	 * @since 2.1.0.0
	 */

	public static void restartAzureus()

		throws PluginException
	{
		PluginManagerImpl.restartAzureus();
	}

	/**
	 * Programatic plugin registration interface
	 * @param plugin_class	this must implement Plugin
	 *
	 * @since 2.0.6.0
	 */

	public static void registerPlugin(
		Class		plugin_class) {
		PluginManagerImpl.registerPlugin(plugin_class);
	}

	public static void registerPlugin(
		Plugin		plugin,
		String		id) {
		PluginManagerImpl.registerPlugin(plugin, id, plugin.getClass().getName());
	}

	public static void registerPlugin(
		Plugin		plugin,
		String		id,
		String		config_key) {
		PluginManagerImpl.registerPlugin(plugin, id, config_key);
	}

	/**
	 * Returns the plugin interface with a given id, or <tt>null</tt> if not found.
	 *
	 * @param id
	 * @param operational If <tt>true</tt>, only return a PluginInterface if the plugin
	 *	 is operational (i.e. is running).
		 * @since 3.1.1.1
	 */
	public abstract PluginInterface getPluginInterfaceByID(String id, boolean operational);

	/**
	 * Returns the plugin interface with a given class name, or <tt>null</tt> if not found.
	 *
	 * @param class_name
	 * @param operational If <tt>true</tt>, only return a PluginInterface if the plugin
	 *	 is operational (i.e. is running).
		 * @since 3.1.1.1
	 */
	public abstract PluginInterface getPluginInterfaceByClass(String class_name, boolean operational);

	/**
	 * Returns the plugin interface with a given class, or <tt>null</tt> if not found.
	 *
	 * @param class_object
	 * @param operational If <tt>true</tt>, only return a PluginInterface if the plugin
	 *	 is operational (i.e. is running).
		 * @since 3.1.1.1
	 */
	public abstract PluginInterface getPluginInterfaceByClass(Class class_object, boolean operational);

	/**
	 * Gets the current set of registered plugins. During initialisation this will probably give partial
	 * results as plugin initialisation is non-deterministic.
	 * @return
	 *
	 * @since 2.1.0.0
	 */

	public abstract PluginInterface[]
	getPluginInterfaces();


	/**
	 * returns the default plugin interface that can be used to access plugin functionality without an
	 * explicit plugin
	 * @return	null if unavailable
	 */

	public abstract PluginInterface
	getDefaultPluginInterface();

	/**
	 * Gets the current set of registered plugins. During initialisation this will probably give partial
	 * results as plugin initialisation is non-deterministic.
	 * @return
	 *
	 * @since 2.1.0.0
	 */

	public abstract PluginInterface[]
	getPlugins();

	public abstract PluginInterface[]
	getPlugins(
		boolean	expect_partial_result);

	public abstract void
	firePluginEvent(
		int		event_type);

	public abstract PluginInstaller
	getPluginInstaller();

	public final void refreshPluginList() {
		refreshPluginList(true);
	}

	/**
	 * @since 3.1.1.1
	 */
	public abstract void refreshPluginList(boolean initialise);

	public abstract boolean
	isSilentRestartEnabled();

	public abstract boolean
	isInitialized();

	public static final String	CA_QUIT_VUZE	= "QuitVuze";
	public static final String	CA_SLEEP		= "Sleep";
	public static final String	CA_HIBERNATE	= "Hibernate";
	public static final String	CA_SHUTDOWN		= "Shutdown";

	/**
	 * @since 5701
	 * @param action one of the above CA_
	 * @throws PluginException
	 */

	public abstract void
	executeCloseAction(
		String		action )

		throws PluginException;

	/**
	 * returns the plugin interface with a given id, or null if not found
	 * @param id
	 * @return
	 *
	 * @since 2.1.0.0
	 */

	public abstract PluginInterface
	getPluginInterfaceByID(
		String		id);


	/**
	 *
	 * @since 2.1.0.0
	 */

	public abstract PluginInterface
	getPluginInterfaceByClass(
		String		class_name );

	public abstract PluginInterface
	getPluginInterfaceByClass(
		Class		c);

	/**
	 * *since 5201
	 */

	public abstract List<PluginInterface>
	getPluginsWithMethod(
		String		name,
		Class<?>[]	parameters);
}
