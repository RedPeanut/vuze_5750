/*
 * Created on Feb 27, 2004
 * Created by Alon Rohter
 * Copyright (C) 2004, 2005, 2006 Alon Rohter, All Rights Reserved.
 *
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
 */
package org.gudy.azureus2.core3.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Properties;

import org.gudy.azureus2.core3.internat.LocaleUtil;
import org.gudy.azureus2.core3.logging.LogEvent;
import org.gudy.azureus2.core3.logging.LogIDs;
import org.gudy.azureus2.core3.logging.Logger;
import org.gudy.azureus2.platform.PlatformManager;
import org.gudy.azureus2.platform.PlatformManagerFactory;

import hello.util.Log;

/**
 * Utility class to manage system-dependant information.
 */
public class SystemProperties {
	
	private static final String TAG = SystemProperties.class.getSimpleName();
	
	private static final LogIDs LOGID = LogIDs.CORE;

	// note this is also used in the restart code....
	public static final String SYS_PROP_CONFIG_OVERRIDE = "azureus.config.path";
	
	/**
	 * Path separator charactor.
	 */
	public static final String SEP = System.getProperty("file.separator");

	public static final String	AZ_APP_ID		= "az";
	public static String APPLICATION_NAME 		= "Azureus";
	private static String APPLICATION_ID 		= AZ_APP_ID;
	private static String APPLICATION_VERSION	= Constants.AZUREUS_VERSION;

	// TODO: fix for non-SWT entry points one day
	private static 		String APPLICATION_ENTRY_POINT 	= "org.gudy.azureus2.ui.swt.Main";

	private static final 	String WIN_DEFAULT = "Application Data";
	private static final 	String OSX_DEFAULT = "Library" + SEP + "Application Support";


	private static final boolean PORTABLE = System.getProperty("azureus.portable.root", "").length() > 0;

	private static String userPath;
	private static String appPath;

	public static void determineApplicationName() {
		String explicit_name = System.getProperty("azureus.app.name", null);

		if (explicit_name != null) {

			explicit_name = explicit_name.trim();

			if (explicit_name.length() > 0) {

				setApplicationName(explicit_name);

				return;
			}
		}

			// try and infer the application name. this is only required on OSX as the app name
			// is a component of the "application path" used to find plugins etc.

		if (Constants.isOSX && !System.getProperty("azureus.infer.app.name", "true").equals("false")) {

			// Could use this, but determineApplicationName is called from
			// COConfigrationManager.preInitialize() and we probably don't want
			// to fire up PlatformManager just yet.
			//String	app = PlatformManagerFactory.getPlatformManager().getApplicationCommandLine();

			String classpath = System.getProperty("exe4j.moduleName", null);
			if (classpath == null || !classpath.contains(".app")) {
			/* example class path

			 /Applications/Utilities/Azureus.app/Contents/Resources/
			Java/swt.jar:/Applications/Utilities/Azureus.app/Contents/Resources/
			Java/swt-pi.jar:/Applications/Utilities/Azureus.app/Contents/Resources/
			Java/Azureus2.jar:/System/Library/Java
			*/

				classpath = System.getProperty("java.class.path");

			}

			if (classpath == null) {

					// System.out here as very early init!

				System.out.println("SystemProperties: determineApplicationName - class path is null");

			} else {

				int	dot_pos = classpath.indexOf(".app");

				if (dot_pos == -1) {

					// When running in eclipse, there is no .app, so use
					// "Working Directory" aka "user.dir" as the app name
					// if there's a Azureus2.jar in there
					// ie. Working Directory = /Users/Shared/Library/Application Support/Vuze
					File fileUserDir = new File(System.getProperty("user.dir", ""));
					if (new File(fileUserDir, "Azureus2.jar").exists()) {
						setApplicationName(fileUserDir.getName());
						return;
					}

					// probably console UI
					// System.out.println("SystemProperties: determineApplicationName -	can't determine application name from " + classpath);

				} else {

					int	start_pos = dot_pos;

					while (start_pos >= 0 && classpath.charAt(start_pos) != '/') {

						start_pos--;
					}

					String	app_name = classpath.substring(start_pos+1, dot_pos);

					setApplicationName(app_name);
				}
			}
		}
	}

	public static void setApplicationName(String name) {
		if (name != null && name.trim().length() > 0) {
			name = name.trim();
			if (userPath != null) {
				if (!name.equals( APPLICATION_NAME)) {
					System.out.println("**** SystemProperties::setApplicationName called too late! ****");
				}
			}
			APPLICATION_NAME			= name;
		}
	}

	public static void setApplicationIdentifier(
		String		application_id) {
		if (application_id != null && application_id.trim().length() > 0) {
			APPLICATION_ID			= application_id.trim();
		}
	}

	public static void setApplicationEntryPoint(
		String		entry_point) {
		if (entry_point != null && entry_point.trim().length() > 0) {

			APPLICATION_ENTRY_POINT	= entry_point.trim();
		}
	}

	public static String getApplicationName() {
		return (APPLICATION_NAME);
	}

	public static void setApplicationVersion(
		String	v) {
		APPLICATION_VERSION = v;
	}

	public static String getApplicationVersion() {
		return (APPLICATION_VERSION);
	}

	public static String getApplicationIdentifier() {
		return (APPLICATION_ID);
	}

	public static String getApplicationEntryPoint() {
		return (APPLICATION_ENTRY_POINT);
	}

		/**
		 * This is used by third-party apps that want explicit control over the user-path
		 * @param _path
		 */

	public static void setUserPath(
		String		_path) {
		userPath	= _path;
	}

	/**
	 * Returns the full path to the user's home azureus directory.
	 * Under unix, this is usually ~/.azureus/
	 * Under Windows, this is usually .../Documents and Settings/username/Application Data/Azureus/
	 * Under OSX, this is usually /Users/username/Library/Application Support/Azureus/
	 */
	public static String getUserPath() {
		
		//Log.d(TAG, ">>> 1");
		
		if (userPath != null) {
			return userPath;
		}
		
		//Log.d(TAG, ">>> 2");
		
		// WATCH OUT!!!! possible recursion here if logging is changed so that it messes with
		// config initialisation - that's why we don't assign the user_path variable until it
		// is complete - an earlier bug resulted in us half-assigning it and using it due to
		// recursion. At least with this approach we'll get (worst case) stack overflow if
		// a similar change is made, and we'll spot it!!!!
		// Super Override -- no AZ_DIR or xxx_DEFAULT added at all.
		String tempUserPath = System.getProperty(SYS_PROP_CONFIG_OVERRIDE);
		try {
			if (tempUserPath != null) {
				
				//Log.d(TAG, ">>> 3");
				
				if (!tempUserPath.endsWith(SEP)) {
					tempUserPath += SEP;
				}
				File dir = new File(tempUserPath);
				if (!dir.exists()) {
					FileUtil.mkdirs(dir);
				}
				if (Logger.isEnabled())
					Logger.log(new LogEvent(LOGID,
							"SystemProperties::getUserPath(Custom): user_path = "
									+ tempUserPath));
				return tempUserPath;
			}
			
			// No override, get it from platform manager
			try {
				PlatformManager platformManager = PlatformManagerFactory.getPlatformManager();
				File loc = platformManager.getLocation(PlatformManager.LOC_USER_DATA);
				if (loc != null) {
					//Log.d(TAG, ">>> 4");
					tempUserPath = loc.getPath() + SEP;
					if (Logger.isEnabled()) {
						Logger.log(new LogEvent(LOGID,
								"SystemProperties::getUserPath: user_path = " + tempUserPath));
					}
				}
			} catch (Throwable e) {
				if (Logger.isEnabled()) {
					Logger.log(new LogEvent(LOGID,
							"Unable to retrieve user config path from "
									+ "the platform manager. "
									+ "Make sure aereg.dll is present."));
				}
			}
			
			// If platform failed, try some hackery
			if (tempUserPath == null) {
				//Log.d(TAG, ">>> 5");
				String userhome = System.getProperty("user.home");
				if (Constants.isWindows) {
					tempUserPath = getEnvironmentalVariable("APPDATA");
					if (tempUserPath != null && tempUserPath.length() > 0) {
						if (Logger.isEnabled())
							Logger.log(new LogEvent(LOGID,
									"Using user config path from APPDATA env var instead: "
											+ tempUserPath));
					} else {
						tempUserPath = userhome + SEP + WIN_DEFAULT;
						if (Logger.isEnabled())
							Logger.log(new LogEvent(LOGID,
									"Using user config path from java user.home var instead: "
											+ tempUserPath));
					}
					tempUserPath = tempUserPath + SEP + APPLICATION_NAME + SEP;
					if (Logger.isEnabled())
						Logger.log(new LogEvent(LOGID,
								"SystemProperties::getUserPath(Win): user_path = "
										+ tempUserPath));
				} else if (Constants.isOSX) {
					tempUserPath = userhome + SEP + OSX_DEFAULT + SEP
							+ APPLICATION_NAME + SEP;
					if (Logger.isEnabled())
						Logger.log(new LogEvent(LOGID,
								"SystemProperties::getUserPath(Mac): user_path = "
										+ tempUserPath));
				} else {
					// unix type
					tempUserPath = userhome + SEP + "."
							+ APPLICATION_NAME.toLowerCase() + SEP;
					if (Logger.isEnabled())
						Logger.log(new LogEvent(LOGID,
								"SystemProperties::getUserPath(Unix): user_path = "
										+ tempUserPath));
				}
			}
			//if the directory doesn't already exist, create it
			File dir = new File(tempUserPath);
			if (!dir.exists()) {
				FileUtil.mkdirs(dir);
			}
			return tempUserPath;
		} finally {
			userPath = tempUserPath;
		}
	}


	/**
	 * Returns the full path to the directory where Azureus is installed
	 * and running from.
	 */
	public static String getApplicationPath() {
		if (appPath != null) {

			return (appPath);
		}

		String temp_app_path = System.getProperty("azureus.install.path", System.getProperty("user.dir"));

		if (!temp_app_path.endsWith(SEP)) {

			temp_app_path += SEP;
		}

		if (Constants.isOSX) {
			// Java7 appaends .app to user.dir
			String appName = SystemProperties.getApplicationName() + ".app/";
			if (temp_app_path.endsWith(appName)) {
				temp_app_path = temp_app_path.substring(0, temp_app_path.length() - appName.length());
			}
		}

		appPath = temp_app_path;

		return (appPath);
	}


	/**
	 * Returns whether or not this running instance was started via
	 * Java's Web Start system.
	 */
	public static boolean isJavaWebStartInstance() {
		try {
			String java_ws_prop = System.getProperty("azureus.javaws");
			return (java_ws_prop != null && java_ws_prop.equals("true" ));
		}
		catch (Throwable e) {
			//we can get here if running in an applet, as we have no access to system props
			return false;
		}
	}



	/**
	 * Will attempt to retrieve an OS-specific environmental var.
	 */

	public static String getEnvironmentalVariable(final String _var) {
		// this approach doesn't work at all on Windows 95/98/ME - it just hangs
		// so get the hell outta here!
		if (Constants.isWindows9598ME) {
			return ("");
		}
		// getenv reinstated in 1.5 - try using it
		String	res = System.getenv(_var);
		if (res != null) {
			return (res);
		}
		
		Properties envVars = new Properties();
		BufferedReader br = null;
		try {
		 	Process p = null;
				Runtime r = Runtime.getRuntime();
			if (Constants.isWindows) {
				p = r.exec(new String[]{ "cmd.exe", "/c", "set" });
			} else { //we assume unix
				p = r.exec("env");
			}
			String system_encoding = LocaleUtil.getSingleton().getSystemEncoding();
			if (Logger.isEnabled())
				Logger.log(new LogEvent(LOGID,
						"SystemProperties::getEnvironmentalVariable - " + _var
								+ ", system encoding = " + system_encoding));
			br = new BufferedReader(new InputStreamReader(p.getInputStream(), system_encoding), 8192);
			String line;
			while ((line = br.readLine()) != null) {
				int idx = line.indexOf('=');
				if (idx >= 0) {
					String key = line.substring(0, idx);
					String value = line.substring(idx+1);
					envVars.setProperty(key, value);
				}
			}
			br.close();
		} catch (Throwable t) {
			if (br != null) try { br.close(); } catch (Exception ingore) {}
		}
		return envVars.getProperty(_var, "");
	}

	public static String getDocPath() {
		String explicitDir = System.getProperty("azureus.doc.path", null);

		if (explicitDir != null) {
			File temp = new File(explicitDir);
			if (!temp.exists()) {
				if (!temp.mkdirs()) {
					System.err.println("Failed to create document dir: " + temp);
				}
			} else if (!(temp.isDirectory() && temp.canWrite())) {
				System.err.println("Document dir is not a directory or not writable: " + temp);
			}
			return ( temp.getAbsolutePath());
		}
		if (PORTABLE) {
			return (getUserPath());
		}

		File fDocPath = null;
		try {
			PlatformManager platformManager = PlatformManagerFactory.getPlatformManager();

			fDocPath = platformManager.getLocation(PlatformManager.LOC_DOCUMENTS);
		} catch (Throwable e) {
		}
		if (fDocPath == null) {
			System.err.println("This is BAD - fix me!");
			//new Throwable().printStackTrace();
			// should never happen.. but if we are missing a dll..
			fDocPath = new File(getUserPath(), "Documents");
		}

		return fDocPath.getAbsolutePath();
	}

	public static String getAzureusJarPath() {
		String	str = getApplicationPath();
		if (Constants.isOSX && !new File(str, "Azureus2.jar").exists()) {
			str += SystemProperties.getApplicationName() + ".app/Contents/Resources/Java/";
		}
		return (str + "Azureus2.jar");
	}
}
