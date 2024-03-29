/*
 * Created on 24.07.2003
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
package org.gudy.azureus2.core3.internat;

import java.io.File;
import java.io.FilenameFilter;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.logging.LogAlert;
import org.gudy.azureus2.core3.logging.Logger;
import org.gudy.azureus2.core3.util.Constants;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.FileUtil;
import org.gudy.azureus2.core3.util.SystemProperties;

import hello.util.Log;
import hello.util.SingleCounter0;


/**
 * @author Arbeiten
 *
 * @author CrazyAlchemist Added keyExistsForDefaultLocale
 */
@SuppressWarnings("restriction")
public class MessageText {

	private static String TAG = MessageText.class.getSimpleName();
	
	public static final Locale LOCALE_ENGLISH = Constants.LOCALE_ENGLISH;
	public static final Locale LOCALE_DEFAULT = new Locale("", ""); // == english
	private static Locale LOCALE_CURRENT = LOCALE_DEFAULT;
	private static final String BUNDLE_NAME;
	private static final Map<String,String>	DEFAULT_EXPANSIONS = new HashMap<String, String>();

	static {
		BUNDLE_NAME = System.getProperty("az.factory.internat.bundle", "org.gudy.azureus2.internat.MessagesBundle");
		updateProductName();
	}

	private static final Map pluginLocalizationPaths = new HashMap();
	private static final Collection pluginResourceBundles = new ArrayList();
	private static IntegratedResourceBundle RESOURCE_BUNDLE;
	private static Set platformSpecificKeys	= new HashSet();
	private static final Pattern PAT_PARAM_ALPHA = Pattern.compile("\\{([^0-9].+?)\\}");

	private static int bundleFailCount	= 0;

	private static final List listeners = new ArrayList();

	// preload default language w/o plugins
	static {
		setResourceBundle(new IntegratedResourceBundle(
				getResourceBundle(BUNDLE_NAME, 
						LOCALE_DEFAULT, 
						MessageText.class.getClassLoader()
				),
				pluginLocalizationPaths,
				null,
				4000, 
				true
			)
		);
	}

	// grab a reference to the default bundle

	private static IntegratedResourceBundle DEFAULT_BUNDLE = RESOURCE_BUNDLE;

	public static void updateProductName() {
		DEFAULT_EXPANSIONS.put("base.product.name", 		Constants.APP_NAME);
		DEFAULT_EXPANSIONS.put("base.plus.product.name", 	Constants.APP_PLUS_NAME);
	}

	public static void loadBundle() {
		loadBundle(false);
	}

	public static void loadBundle(boolean forceReload) {
		Locale	old_locale = getCurrentLocale();

		String savedLocaleString = COConfigurationManager.getStringParameter("locale");

		Locale savedLocale;
		String[] savedLocaleStrings = savedLocaleString.split("_", 3);
		if (savedLocaleStrings.length > 0 && savedLocaleStrings[0].length() == 2) {
			if (savedLocaleStrings.length == 3) {
				savedLocale = new Locale(savedLocaleStrings[0], savedLocaleStrings[1],
						savedLocaleStrings[2]);
			} else if (savedLocaleStrings.length == 2
					&& savedLocaleStrings[1].length() == 2) {
				savedLocale = new Locale(savedLocaleStrings[0], savedLocaleStrings[1]);
			} else {
				savedLocale = new Locale(savedLocaleStrings[0]);
			}
		} else {
			if (savedLocaleStrings.length == 3 && savedLocaleStrings[0].length() == 0
					&& savedLocaleStrings[2].length() > 0) {
				savedLocale = new Locale(savedLocaleStrings[0], savedLocaleStrings[1],
						savedLocaleStrings[2]);
			} else {
				savedLocale = Locale.getDefault();
			}
		}
		MessageText.changeLocale(savedLocale,forceReload);

		COConfigurationManager
				.setParameter("locale.set.complete.count", COConfigurationManager
						.getIntParameter("locale.set.complete.count") + 1);

		Locale	new_locale = getCurrentLocale();

		if (!old_locale.equals(new_locale) || forceReload) {
			for (int i=0;i<listeners.size();i++) {
				try {
					((MessageTextListener)listeners.get(i)).localeChanged( old_locale, new_locale);
				} catch (Throwable e) {
					Debug.printStackTrace(e);
				}
			}
		}
	}

	public static void addListener(MessageTextListener listener) {
		listeners.add(listener);
	}
	
	public static void addAndFireListener(MessageTextListener listener) {
		listeners.add(listener);
		listener.localeChanged(getCurrentLocale(), getCurrentLocale());
	}

	public static void removeListener(MessageTextListener listener) {
		listeners.remove(listener);
	}

	static ResourceBundle getResourceBundle(
			String		name,
			Locale		loc,
			ClassLoader	cl) {
		
		try {
			return (ResourceBundle.getBundle(name, loc, cl));
		} catch (Throwable e) {
			bundleFailCount++;
			if (bundleFailCount == 1) {
				e.printStackTrace();
				Logger.log(new LogAlert(LogAlert.REPEATABLE, LogAlert.AT_ERROR,
						"Failed to load resource bundle. One possible cause is "
								+ "that you have installed " + Constants.APP_NAME + " into a directory "
								+ "with a '!' in it. If so, please remove the '!'."));
			}
			return (new ResourceBundle() {
				public Locale getLocale() {
					return (LOCALE_DEFAULT);
				}

				protected Object handleGetObject(String key) {
					return (null);
				}

				public Enumeration getKeys() {
					return (new Vector().elements());
				}
			});
		}
	}

	private static void setResourceBundle(IntegratedResourceBundle bundle) {

		RESOURCE_BUNDLE = bundle;
		Iterator keys = RESOURCE_BUNDLE.getKeysLight();
		String uiSuffix = getUISuffix();
		String platformSuffix = getPlatformSuffix();
		Set platformKeys = new HashSet();
		while (keys.hasNext()) {
			String key = (String) keys.next();
			if (key.endsWith(platformSuffix))
				platformKeys.add(key);
			else if (key.endsWith(uiSuffix)) {
				RESOURCE_BUNDLE.addString(
						key.substring(0, key.length() - uiSuffix.length()),
						RESOURCE_BUNDLE.getString(key)
				);
			}
		}
		platformSpecificKeys = platformKeys;
	}


	public static boolean keyExists(String key) {
		try {
			getResourceBundleString(key);
			return true;
		} catch (MissingResourceException e) {
			return false;
		}
	}

	public static boolean keyExistsForDefaultLocale(final String key) {
		try {
			DEFAULT_BUNDLE.getString(key);
			return true;
		} catch (MissingResourceException e) {
			return false;
		}
	}


	/**
	 * @param key
	 * @return
	 */
	public static String getString(
	String key,
	String sDefault) {
		if (key == null)
			return "";
		String	target_key = key + getPlatformSuffix();
		if (!platformSpecificKeys.contains( target_key)) {
			target_key	= key;
		}
		try {
			return getResourceBundleString(target_key);
		} catch (MissingResourceException e) {
			return getPlatformNeutralString(key, sDefault);
		}
	}

	public static String getString(String key) {
		if (key == null)
			return "";
		
		String	targetKey = key + getPlatformSuffix();
		if (!platformSpecificKeys.contains(targetKey)) {
			targetKey	= key;
		}
		
		try {
			return getResourceBundleString(targetKey);
		} catch (MissingResourceException e) {
			return getPlatformNeutralString(key);
		}
	}

	public static String getPlatformNeutralString(String key) {
		try {
			return getResourceBundleString(key);
		} catch (MissingResourceException e) {
			// we support the usage of non-resource strings as long as they are wrapped in !
			if (key.startsWith("!") && key.endsWith("!")) {
				return (key.substring(1,key.length()-1));
			}
			return '!' + key + '!';
		}
	}

	public static String getPlatformNeutralString(String key, String sDefault) {
		try {
			return getResourceBundleString(key);
		} catch (MissingResourceException e) {
			if (key.startsWith("!") && key.endsWith("!")) {
					return (key.substring(1,key.length()-1));
				}
			return sDefault;
		}
	}

	private static String getResourceBundleString(String key) {
		
		if (key == null)
			return "";
		
		String value = RESOURCE_BUNDLE.getString(key);
		return expandValue(value);
	}

	public static String expandValue(String value) {
		// Replace {*} with a lookup of *
		if (value != null && value.indexOf('}') > 0) {
			Matcher matcher = PAT_PARAM_ALPHA.matcher(value);
			while (matcher.find()) {
				String key = matcher.group(1);
				try {
					String text = DEFAULT_EXPANSIONS.get(key);

					if (text == null) {
						text = getResourceBundleString(key);
					}

					if (text != null) {
						value = value.replaceAll("\\Q{" + key + "}\\E", text);
					}
				} catch (MissingResourceException e) {
					// ignore error
				}
			}
		}
		return value;
	}

	/**
	 * Gets the localization key suffix for the running platform
	 * @return The suffix
	 * @see Constants
	 */
	private static String getPlatformSuffix() {
		if (Constants.isOSX)
				return "._mac";
		else if (Constants.isLinux)
			return "._linux";
		else if (Constants.isUnix)
			return "._unix";
		else if (Constants.isFreeBSD)
			return "._freebsd";
		else if (Constants.isSolaris)
				return "._solaris";
		 else if (Constants.isWindows)
			 return "._windows";
		 else
			 return "._unknown";
	}

	private static String getUISuffix() {
		return "az2".equalsIgnoreCase(COConfigurationManager.getStringParameter("ui"))
				? "._classic" : "._vuze";
	}

	/**
	 * Process a sequence of words, and translate the ones containing at least one '.', unless it's an ending dot.
	 * @param sentence
	 * @return the formated String in the current Locale
	 */
	public static String getStringForSentence(String sentence) {
		StringTokenizer st = new StringTokenizer(sentence , " ");
		StringBuilder result = new StringBuilder(sentence.length());
		String separator = "";
		while (st.hasMoreTokens()) {
			result.append(separator);
			separator = " ";

			String word = st.nextToken();
			int length = word.length();
			int position = word.lastIndexOf(".");
			if (position == -1 || (position+1) == length) {
				result.append(word);
			} else {
				//We have a key :
				String translated = getString(word);
				if (translated.equals("!" + word + "!")) {
					result.append(word);
				}
				else {
					result.append(translated);
				}
			}
		}
		return result.toString();
	}

	/**
	 * Expands a message text and replaces occurrences of %1 with first param, %2 with second...
	 * @param key
	 * @param params
	 * @return
	 */
	public static String getString(
			String		key,
		String[]	params ) {
		String	res = getString(key);

		if (params == null) {
			return res;
		}

		for (int i=0;i<params.length;i++) {

			String	from_str 	= "%" + (i+1);
			String	to_str		= params[i];

			res = replaceStrings(res, from_str, to_str);
		}

		return (res);
	}

	protected static String
	replaceStrings(
		String	str,
	String	f_s,
	String	t_s ) {
		int	pos = 0;

		String	res	= "";

		while (pos < str.length()) {

			int	p1 = str.indexOf(f_s, pos);

			if (p1 == -1) {

				res += str.substring(pos);

				break;
			}

			res += str.substring(pos, p1) + t_s;

			pos = p1+f_s.length();
		}

		return (res);
	}

	public static String getDefaultLocaleString(String key) {
		// TODO Auto-generated method stub
		try {
			return DEFAULT_BUNDLE.getString(key);
		} catch (MissingResourceException e) {
			// we support the usage of non-resource strings as long as they are wrapped in !
			if (key.startsWith("!") && key.endsWith("!")) {
				return (key.substring(1,key.length()-1));
			}
			return '!' + key + '!';
		}
	}

	public static Locale getCurrentLocale() {
		return LOCALE_DEFAULT.equals(LOCALE_CURRENT) ? LOCALE_ENGLISH : LOCALE_CURRENT;
	}

	public static boolean isCurrentLocale(Locale locale) {
		return LOCALE_ENGLISH.equals(locale) ? LOCALE_CURRENT.equals(LOCALE_DEFAULT) : LOCALE_CURRENT.equals(locale);
	}

	public static Locale[] getLocales(boolean sort) {
		String bundleFolder = BUNDLE_NAME.replace('.', '/');
		final String prefix = BUNDLE_NAME.substring(BUNDLE_NAME.lastIndexOf('.') + 1);
		final String extension = ".properties";

		String urlString = MessageText.class.getClassLoader().getResource(bundleFolder.concat(extension)).toExternalForm();
		//System.out.println("urlString: " + urlString);
		String[] bundles = null;

		if (urlString.startsWith("jar:file:")) {

			File jar = FileUtil.getJarFileFromURL(urlString);

			if (jar != null) {
				try {
					// System.out.println("jar: " + jar.getAbsolutePath());
					JarFile jarFile = new JarFile(jar);
					Enumeration entries = jarFile.entries();
					ArrayList list = new ArrayList(250);
					while (entries.hasMoreElements()) {
						JarEntry jarEntry = (JarEntry) entries.nextElement();
						if (jarEntry.getName().startsWith(bundleFolder)
								&& jarEntry.getName().endsWith(extension)) {
							// System.out.println("jarEntry: " + jarEntry.getName());
							list.add(jarEntry.getName().substring(
									bundleFolder.length() - prefix.length()));
							// "MessagesBundle_de_DE.properties"
						}
					}
					bundles = (String[]) list.toArray(new String[list.size()]);
				} catch (Exception e) {
					Debug.printStackTrace(e);
				}
			}
		} else {
			File bundleDirectory = new File(URI.create(urlString)).getParentFile();
			//			System.out.println("bundleDirectory: " +
			// bundleDirectory.getAbsolutePath());

			bundles = bundleDirectory.list(new FilenameFilter() {
				public boolean accept(File dir, String name) {
					return name.startsWith(prefix) && name.endsWith(extension);
				}
			});
		}

		HashSet bundleSet = new HashSet();

		// Add local first
		File localDir = new File(SystemProperties.getUserPath());
		String localBundles[] = localDir.list(new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return name.startsWith(prefix) && name.endsWith(extension);
			}
		});

			// can be null if user path is borked

		if (localBundles != null) {

			bundleSet.addAll(Arrays.asList(localBundles));
		}

		// Add AppDir 2nd
		File appDir = new File(SystemProperties.getApplicationPath());
		String appBundles[] = appDir.list(new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return name.startsWith(prefix) && name.endsWith(extension);
			}
		});

			// can be null if app path is borked

		if (appBundles != null) {

			bundleSet.addAll(Arrays.asList(appBundles));
		}
		// Any duplicates will be ignored
		bundleSet.addAll(Arrays.asList(bundles));

		List foundLocalesList = new ArrayList(bundleSet.size());

		foundLocalesList.add(LOCALE_ENGLISH);

		Iterator val = bundleSet.iterator();
		while (val.hasNext()) {
			String sBundle = (String)val.next();

			// System.out.println("ResourceBundle: " + bundles[i]);
			if (prefix.length() + 1 < sBundle.length() - extension.length()) {
				String locale = sBundle.substring(prefix.length() + 1, sBundle.length() - extension.length());
				//System.out.println("Locale: " + locale);
				String[] sLocalesSplit = locale.split("_", 3);
				if (sLocalesSplit.length > 0 && sLocalesSplit[0].length() == 2) {
					if (sLocalesSplit.length == 3) {
						foundLocalesList.add(new Locale(sLocalesSplit[0], sLocalesSplit[1], sLocalesSplit[2]));
					} else if (sLocalesSplit.length == 2 && sLocalesSplit[1].length() == 2) {
						foundLocalesList.add(new Locale(sLocalesSplit[0], sLocalesSplit[1]));
					} else {
						foundLocalesList.add(new Locale(sLocalesSplit[0]));
					}
				} else {
					if (sLocalesSplit.length == 3 &&
							sLocalesSplit[0].length() == 0 &&
							sLocalesSplit[2].length() > 0) {
						foundLocalesList.add(new Locale(sLocalesSplit[0], sLocalesSplit[1], sLocalesSplit[2]));
					}
				}
			 }
		}

		Locale[] foundLocales = new Locale[foundLocalesList.size()];

		foundLocalesList.toArray(foundLocales);

		if (sort) {
			try {
				Arrays.sort(foundLocales, new Comparator() {
					public final int compare (Object a, Object b) {
						return ((Locale)a).getDisplayName((Locale)a).compareToIgnoreCase(((Locale)b).getDisplayName((Locale)b));
					}
				});
			} catch (Throwable e) {
				// user has a problem whereby a null-pointer exception occurs when sorting the
				// list - I've done some fixes to the locale list construction but am
				// putting this in here just in case
				Debug.printStackTrace(e);
			}
		}
		return foundLocales;
	}

	public static boolean changeLocale(Locale newLocale) {
		return changeLocale(newLocale, false);
	}

	private static boolean changeLocale(Locale newLocale, boolean force) {
		// set locale for startup (will override immediately it on locale change anyway)
		Locale.setDefault(newLocale);

		if (!isCurrentLocale(newLocale) || force) {
			Locale.setDefault(LOCALE_DEFAULT);
			ResourceBundle newResourceBundle = null;
			String bundleFolder = BUNDLE_NAME.replace('.', '/');
			final String prefix = BUNDLE_NAME.substring(BUNDLE_NAME.lastIndexOf('.') + 1);
			final String extension = ".properties";

			if (newLocale.equals(LOCALE_ENGLISH))
				newLocale = LOCALE_DEFAULT;

			try {
				File userBundleFile = new File(SystemProperties.getUserPath());
				File appBundleFile = new File(SystemProperties.getApplicationPath());

				// Get the jarURL
				// XXX Is there a better way to get the JAR name?
				ClassLoader cl = MessageText.class.getClassLoader();

				URL u = cl.getResource(bundleFolder + extension);

				if (u == null) {

						// might be missing entirely

					return (false);
				}
				String sJar = u.toString();
				sJar = sJar.substring(0, sJar.length() - prefix.length() - extension.length());
				URL jarURL = new URL(sJar);

				// User dir overrides app dir which overrides jar file bundles
				URL[] urls = {userBundleFile.toURL(), appBundleFile.toURL(), jarURL};

				/* This is debugging code, use it when things go wrong :) The line number
				 * is approximate as the input stream is buffered by the reader...

				{
					LineNumberInputStream lnis	= null;
						try {
							ClassLoader fff = new URLClassLoader(urls);
							java.io.InputStream stream = fff.getResourceAsStream("MessagesBundle_th_TH.properties");
							lnis = new LineNumberInputStream(stream);
							new java.util.PropertyResourceBundle(lnis);
						} catch (Throwable e) {
							System.out.println( lnis.getLineNumber());
							e.printStackTrace();
						}
				}
				*/

				newResourceBundle = getResourceBundle("MessagesBundle", newLocale, new URLClassLoader(urls));
				
				// do more searches if getBundle failed, or if the language is not the
				// same and the user wanted a specific country
				if ((!newResourceBundle.getLocale().getLanguage().equals(newLocale.getLanguage()) &&
						 !newLocale.getCountry().equals(""))) {

					Locale foundLocale = newResourceBundle.getLocale();
					System.out.println("changeLocale: "+
														 (foundLocale.toString().equals("") ? "*Default Language*" : foundLocale.getDisplayLanguage()) +
														 " != "+newLocale.getDisplayName()+". Searching without country..");
					// try it without the country
					Locale localeJustLang = new Locale(newLocale.getLanguage());
					newResourceBundle = getResourceBundle("MessagesBundle", localeJustLang,
																												new URLClassLoader(urls));

					if (newResourceBundle == null ||
							!newResourceBundle.getLocale().getLanguage().equals(localeJustLang.getLanguage())) {
						// find first language we have in our list
						System.out.println("changeLocale: Searching for language " + newLocale.getDisplayLanguage() + " in *any* country..");
						Locale[] locales = getLocales(false);
						for (int i = 0; i < locales.length; i++) {
							if (locales[i].getLanguage().equals(newLocale.getLanguage())) {
								newResourceBundle = getResourceBundle("MessagesBundle", locales[i],
										new URLClassLoader(urls));
								break;
							}
						}
					}
				}
			} catch (MissingResourceException e) {
				System.out.println("changeLocale: no resource bundle for " + newLocale);
				Debug.printStackTrace(e);
				return false;
			} catch (Exception e) {
				Debug.printStackTrace(e);
			}

			if (newResourceBundle != null) {
				if (!newLocale.equals(LOCALE_DEFAULT) && !newResourceBundle.getLocale().equals(newLocale)) {
					String sNewLanguage = newResourceBundle.getLocale().getDisplayName();
					if (sNewLanguage == null || sNewLanguage.trim().equals(""))
						sNewLanguage = "English (default)";
					System.out.println("changeLocale: no message properties for Locale '" + newLocale.getDisplayName() + "' (" + newLocale + "), using '" + sNewLanguage + "'");
					if (newResourceBundle.getLocale().equals(RESOURCE_BUNDLE.getLocale())) {
						return false;
					}
				}
				newLocale = newResourceBundle.getLocale();
				Locale.setDefault(newLocale.equals(LOCALE_DEFAULT) ? LOCALE_ENGLISH : newLocale);
				LOCALE_CURRENT = newLocale;
				setResourceBundle(new IntegratedResourceBundle(newResourceBundle, pluginLocalizationPaths, null, 4000, true ));
				if (newLocale.equals(LOCALE_DEFAULT))
					DEFAULT_BUNDLE = RESOURCE_BUNDLE;
				return true;
			} else
				return false;
		}
		return false;
	}

	// TODO: This is slow. For every call, IntegratedResourceBundle creates
	//			 a hashtables and fills it with the old resourceBundle, then adds
	//			 the new one, and then puts it all back into a ListResourceBundle.
	//			 As we get more plugins, the time to add a new plugin's language file
	//			 increases dramatically (even if the language file only has 1 entry!)
	//			 Fix this by:
	//				 - Create only one IntegratedResourceBundle
	//				 - extending ResourceBundle
	//				 - override handleGetObject, store in hashtable
	//				 - function to add another ResourceBundle, adds to hashtable
	public static boolean integratePluginMessages(String localizationPath, ClassLoader classLoader) {
		boolean integratedSuccessfully = false;

			// allow replacement of localisation paths so that updates of unloadable plugins
			// replace messages

		if (localizationPath != null && localizationPath.length() != 0) {

			synchronized (pluginLocalizationPaths) {
				pluginLocalizationPaths.put(localizationPath, classLoader);
			}

			RESOURCE_BUNDLE.addPluginBundle(localizationPath, classLoader);
			setResourceBundle(RESOURCE_BUNDLE);

			integratedSuccessfully = true;
		}
		return integratedSuccessfully;
	}

	public static boolean integratePluginMessages(ResourceBundle bundle) {
		synchronized (pluginResourceBundles) {
			pluginResourceBundles.add(bundle);
		}

		RESOURCE_BUNDLE.addResourceMessages(bundle,true);
		setResourceBundle(RESOURCE_BUNDLE);

		return true;
	}

/**
 * Reverts Locale back to default, and removes the config settin.
 * Notifications of change should be done by the caller.
 */
	/*
	@SuppressWarnings("restriction")
	public static void revertToDefaultLocale() {
		// Aside from the last 2 lines, this is Sun's code that is run
		// at startup to determine the locale.	Too bad they didn't provide
		// a way to call this code explicitly..
		String language, region, country, variant;
		language = System.getProperty("user.language", "en");
		// for compatibility, check for old user.region property
		region = System.getProperty("user.region");
		if (region != null) {
				// region can be of form country, country_variant, or _variant
				int i = region.indexOf('_');
				if (i >= 0) {
						country = region.substring(0, i);
						variant = region.substring(i + 1);
				} else {
						country = region;
						variant = "";
				}
		} else {
				country = System.getProperty("user.country", "");
				variant = System.getProperty("user.variant", "");
		}
		changeLocale(new Locale(language, country, variant));
		COConfigurationManager.removeParameter("locale");
	}
	*/

	public static interface
	MessageTextListener
	{
		public void localeChanged(
		Locale	old_locale,
		Locale	new_locale);
	}

	/**
	 * Sometime a localization key has 2 different returned values: one for Classic UI and another for the Vuze UI
	 * This method will attempt to locate the given key (with the prefix v3. ) if applicable, if found
	 * then it will return the key with the prepended prefix.	Otherwise it will return the key as given
	 * @param localizationKey
	 * @return
	 */
	public static String resolveLocalizationKey(String localizationKey) {
		if (null == localizationKey) {
			return null;
		}

		if ("az3".equalsIgnoreCase(COConfigurationManager.getStringParameter("ui"))) {
			String v3Key = null;
			if (!localizationKey.startsWith("v3.")) {
				v3Key = "v3." + localizationKey;
			} else {
				v3Key = localizationKey;
			}

			if (MessageText.keyExists(v3Key)) {
				return v3Key;
			}
		}

		return localizationKey;
	}

	/**
	 * Sometime a accelerator key has 2 different returned values: one for Classic UI and another for the Vuze UI
	 * This method will attempt to locate the given key (with the prefix v3. ) if applicable, if found
	 * then it will return the key with the prepended prefix.	Otherwise it will return the key as given
	 * @param acceleratorKey
	 * @return
	 */
	public static String resolveAcceleratorKey(String acceleratorKey) {
		if (null == acceleratorKey) {
			return null;
		}

		if ("az3".equalsIgnoreCase(COConfigurationManager.getStringParameter("ui"))) {
			String v3Key = null;
			if (!acceleratorKey.startsWith("v3.")) {
				v3Key = "v3." + acceleratorKey;
			} else {
				v3Key = acceleratorKey;
			}

			if (MessageText.keyExists(v3Key + ".keybinding")) {
				return v3Key;
			}
		}

		return acceleratorKey;
	}
}
