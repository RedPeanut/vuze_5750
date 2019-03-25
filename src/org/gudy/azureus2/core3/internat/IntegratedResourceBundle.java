/*
 * Created on 29.11.2003
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
package org.gudy.azureus2.core3.internat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.*;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.config.ParameterListener;
import org.gudy.azureus2.core3.util.AETemporaryFileHandler;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.LightHashMap;
import org.gudy.azureus2.core3.util.SimpleTimer;
import org.gudy.azureus2.core3.util.TimerEvent;
import org.gudy.azureus2.core3.util.TimerEventPerformer;
import org.gudy.azureus2.core3.util.TimerEventPeriodic;

/**
 * @author Rene Leonhardt
 */
public class IntegratedResourceBundle
	extends ResourceBundle {
	
	private static final boolean DEBUG = false;
	private static final Object	NULL_OBJECT = new Object();
	private static final Map	bundleMap = new WeakHashMap();
	private static TimerEventPeriodic	compactTimer;
	protected static boolean upperCaseEnabled;

	static {
		COConfigurationManager.addAndFireParameterListener(
			"label.lang.upper.case",
			new ParameterListener() {
				public void parameterChanged(
					String name) {
					upperCaseEnabled = COConfigurationManager.getBooleanParameter(name, false);
				}
			});
	}

	protected static void resetCompactTimer() {
		synchronized(bundleMap) {
			if (compactTimer == null && System.getProperty("transitory.startup", "0").equals("0")) {
				compactTimer = SimpleTimer.addPeriodicEvent(
					"IRB:compactor",
					60*1000,
					new TimerEventPerformer() {
						public void perform(
							TimerEvent event) {
							synchronized(bundleMap) {
								Iterator it = bundleMap.keySet().iterator();
								boolean	didSomething = false;
								while (it.hasNext()) {
									IntegratedResourceBundle	rb = (IntegratedResourceBundle)it.next();
									if (DEBUG) {
										System.out.println("Compact RB " + rb.getString());
									}
									if (rb.compact()) {
										didSomething	= true;
									}
									if (DEBUG) {
										System.out.println("        to " + rb.getString());
									}
								}
								if (!didSomething) {
									compactTimer.cancel();
									compactTimer	= null;
								}
							}
						}
					});
			}
		}
	}

	private final Locale	locale;
	private final boolean	isMessageBundle;

	private Map	messages;
	private Map	usedMessages;
	private List nullValues;

	private boolean		messagesDirty;
	private int			cleanCount	= 0;
	private boolean		oneOffDiscardDone;

	private File		scratchFileName;
	private InputStream	scratchFileIS;

	private final int initCapacity;

	private Map<String,String>	addedStrings;

	public IntegratedResourceBundle(
		ResourceBundle 		main,
		Map 				localizationPaths) {
		this(main, localizationPaths, null, 10);
	}

	public IntegratedResourceBundle(
		ResourceBundle 		main,
		Map 				localizationPaths,
		int					initCapacity) {
		this(main, localizationPaths, null, initCapacity);
	}

	public IntegratedResourceBundle(
		ResourceBundle 		main,
		Map 				localizationPaths,
		Collection 			resource_bundles,
		int					initCapacity) {
		this(main, localizationPaths, resource_bundles, initCapacity, false);
	}

	public IntegratedResourceBundle(
		ResourceBundle 		main,
		Map 				localizationPaths,
		Collection 			resource_bundles,
		int					initCapacity,
		boolean				isMessageBundle) {
		this.initCapacity 		= initCapacity;
		this.isMessageBundle	= isMessageBundle;
		messages = new LightHashMap(initCapacity);
		locale = main.getLocale();
		// use a somewhat decent initial capacity, proper calculation would require java 1.6
		addResourceMessages(main, isMessageBundle);
		synchronized (localizationPaths) {
			for (Iterator iter = localizationPaths.keySet().iterator(); iter.hasNext();) {
				String localizationPath = (String) iter.next();
				ClassLoader classLoader = (ClassLoader) localizationPaths.get(localizationPath);
				addPluginBundle(localizationPath, classLoader);
			}
		}
		if (resource_bundles != null) {
			synchronized (resource_bundles) {
				for (Iterator itr = resource_bundles.iterator(); itr.hasNext();) {
					addResourceMessages((ResourceBundle)itr.next());
				}
			}
		}
		usedMessages = new LightHashMap( messages.size());
		synchronized(bundleMap) {
			bundleMap.put(this, NULL_OBJECT);
			resetCompactTimer();
		}
		//System.out.println("IRB Size = " + messages.size() + "/cap=" + initCapacity + ";" + Debug.getCompressedStackTrace());
	}

	public Locale getLocale() {
		return locale;
	}

	private Map getMessages() {
		return (loadMessages());
	}

	public Enumeration getKeys() {
		new Exception("Don't call me, call getKeysLight").printStackTrace();
		Map m = loadMessages();
		return (new Vector( m.keySet()).elements());
	}

	protected Iterator
	getKeysLight() {
		Map m = new LightHashMap(loadMessages());

		return ( m.keySet().iterator());
	}

	/**
	 * Gets a string, using default if key doesn't exist.  Skips
	 * throwing MissingResourceException when key doesn't exist, which saves
	 * some CPU cycles
	 *
	 * @param key
	 * @param def
	 * @return
	 *
	 * @since 3.1.1.1
	 */
	public String getString(String key, String def) {
		String s = (String) handleGetObject(key);
		if (s == null) {
			if (parent != null) {
				s = parent.getString(key);
			}
			if (s == null) {
				return def;
			}
		}
		return s;
	}


	protected Object handleGetObject(String key) {
		Object	res;
		synchronized(bundleMap) {
			res = usedMessages.get(key);
		}
		Integer keyHash = null;
		if (nullValues != null) {
			keyHash = new Integer(key.hashCode());
  		int index = Collections.binarySearch(nullValues, keyHash);
  		if (index >= 0) {
  			return null;
  		}
		}
		if (res == NULL_OBJECT) {
			return (null);
		}
		if (res == null) {
			synchronized(bundleMap) {
				loadMessages();
				if (messages != null) {
					res = messages.get(key);
				}
				if (res == null && nullValues != null) {
		  		int index = Collections.binarySearch(nullValues, keyHash);
					if (index < 0) {
						index = -1 * index - 1; // best guess
					}
					if (index > nullValues.size()) {
						index = nullValues.size();
					}
					nullValues.add(index, keyHash);
				} else {
					usedMessages.put(key, res==null?NULL_OBJECT:res);
				}
				cleanCount	= 0;
				resetCompactTimer();
			}
		}
		return (res);
	}

	public void addPluginBundle(String localizationPath, ClassLoader classLoader) {
		ResourceBundle newResourceBundle = null;
		try {
			if (classLoader != null)
				newResourceBundle = ResourceBundle.getBundle(localizationPath, locale ,classLoader);
			else
				newResourceBundle = ResourceBundle.getBundle(localizationPath, locale,IntegratedResourceBundle.class.getClassLoader());
		} catch (Exception e) {
			//        System.out.println(localizationPath+": no resource bundle for " +
			// main.getLocale());
			try {
				if (classLoader != null)
					newResourceBundle = ResourceBundle.getBundle(localizationPath, MessageText.LOCALE_DEFAULT,classLoader);
				else
					newResourceBundle = ResourceBundle.getBundle(localizationPath, MessageText.LOCALE_DEFAULT,IntegratedResourceBundle.class.getClassLoader());
			} catch (Exception e2) {
				System.out.println(localizationPath + ": no default resource bundle");
				return;
			}
		}

		addResourceMessages(newResourceBundle, true);


	}

	public void addResourceMessages(ResourceBundle bundle) {
		addResourceMessages(bundle, false);
	}

	public void addResourceMessages(
		ResourceBundle 	bundle,
		boolean			areMessages) {
		boolean upper_case = upperCaseEnabled && (isMessageBundle || areMessages);
		synchronized (bundleMap) {
			loadMessages();
			if (bundle != null) {
				messagesDirty = true;
				if (bundle instanceof IntegratedResourceBundle) {
					Map<String,String> m = ((IntegratedResourceBundle)bundle).getMessages();
					if (upper_case) {
						for ( Map.Entry<String,String> entry: m.entrySet()) {
							String key = entry.getKey();
							messages.put(key, toUpperCase(entry.getValue()));
						}
					} else {
						messages.putAll(m);
					}
					if (usedMessages != null) {
						usedMessages.keySet().removeAll(m.keySet());
					}
					if (nullValues != null) {
						nullValues.removeAll(m.keySet());
					}
				} else {
					for (Enumeration enumeration = bundle.getKeys(); enumeration.hasMoreElements();) {
						String key = (String) enumeration.nextElement();
						if (upper_case) {
							messages.put(key, toUpperCase((String)bundle.getObject(key)));
						} else {
							messages.put(key, bundle.getObject(key));
						}
						if (usedMessages != null) {
							usedMessages.remove(key);
						}
						if (nullValues != null) {
							nullValues.remove(key);
						}
					}
				}
			}
		}
//		System.out.println("after addrb; IRB Size = " + messages.size() + "/cap=" + initCapacity + ";" + Debug.getCompressedStackTrace());
	}

	private String toUpperCase(
		String	str) {
		int	pos1 = str.indexOf('{');
		if (pos1 == -1) {
			return (str.toUpperCase( locale));
		}
		int	pos = 0;
		int	len = str.length();
		StringBuilder result = new StringBuilder(len);
		while (pos < len) {
			if (pos1 > pos) {
				result.append(str.substring( pos, pos1 ).toUpperCase( locale));
			}
			if (pos1 == len) {
				return ( result.toString());
			}
			int pos2 = str.indexOf('}', pos1);
			if (pos2 == -1) {
				result.append(str.substring( pos1 ).toUpperCase( locale));
				return ( result.toString());
			}
			pos2++;
			result.append(str.substring( pos1, pos2));
			pos = pos2;
			pos1 = str.indexOf('{', pos);
			if (pos1 == -1) {
				pos1 = len;
			}
		}
		return ( result.toString());
	}

	protected boolean compact() {
		// System.out.println("compact " + getString() + ": cc=" + clean_count);
		cleanCount++;
		if (cleanCount == 1) {
			return (true);
		}
		if (scratchFileIS == null || messagesDirty) {
			File tempFile = null;
			FileOutputStream	fos = null;
			// we have a previous cache, discard
			if (scratchFileIS != null) {
				// System.out.println("discard cache file " + scratch_file_name + " for " + this);
				try {
					scratchFileIS.close();
				} catch (Throwable e) {
					scratchFileName = null;
				} finally {
					scratchFileIS = null;
				}
			}
			try {
				Properties props = new Properties();
				props.putAll(messages);
				if (scratchFileName == null) {
					tempFile = AETemporaryFileHandler.createTempFile();
				} else {
					tempFile = scratchFileName;
				}
				fos = new FileOutputStream(tempFile);
				props.store(fos, "message cache");
				fos.close();
				fos = null;
				// System.out.println("wrote cache file " + temp_file + " for " + this);
				scratchFileName	= tempFile;
				scratchFileIS 	= new FileInputStream(tempFile);
				messagesDirty	= false;
			} catch (Throwable e) {
				if (fos != null) {
					try {
						fos.close();
					} catch (Throwable f) {
					}
				}
				if  (tempFile != null) {
					tempFile.delete();
				}
			}
		}
		if (scratchFileIS != null) {
			if (cleanCount >= 2) {
				/*
				if (messages != null) {
					System.out.println("messages discarded  " + scratch_file_name + " for " + this);
				}
				*/
				// throw away full message map after 2 ticks
				messages = null;
			}
			if (cleanCount == 5 && !oneOffDiscardDone) {
				// System.out.println("used discard " + scratch_file_name + " for " + this);
				oneOffDiscardDone = true;
				// one off discard of used_messages to clear out any that were
				// accessed once and never again
				usedMessages.clear();
			}
		}
		if (cleanCount > 5) {
			Map	compact_um = new LightHashMap(usedMessages.size() + 16);
			compact_um.putAll(usedMessages);
			usedMessages = compact_um;
			return (false);
		} else {
			return (true);
		}
	}

	protected Map loadMessages() {
		synchronized(bundleMap) {
			if (messages != null) {
				return (messages);
			}
			Map	result;
			if (scratchFileIS == null) {
				result = new LightHashMap();
			} else {
				// System.out.println("read cache file " + scratch_file_name + " for " + this);
				Properties p = new Properties();
				InputStream	fis = scratchFileIS;
				try {
					p.load(fis);
					fis.close();
					scratchFileIS = new FileInputStream(scratchFileName);
					messages = new LightHashMap();
					messages.putAll(p);
					result = messages;
				} catch (Throwable e) {
					if (fis != null) {
						try {
							fis.close();
						} catch (Throwable f) {
						}
					}
					Debug.out("Failed to load message bundle scratch file", e);
					scratchFileName.delete();
					scratchFileIS = null;
					result = new LightHashMap();
				}
			}
			if (addedStrings != null) {
				result.putAll(addedStrings);
			}
			return (result);
		}
	}

	protected String getString() {
		return ( locale + ": use=" + usedMessages.size() + ",map=" + (messages==null?"":String.valueOf(messages.size()))
				+ (nullValues == null ? "" : ",null=" + nullValues.size()) + ",added=" + (addedStrings==null?"":addedStrings.size()));
	}

	public void addString(String key, String value) {
		synchronized(bundleMap) {
			if (addedStrings == null) {
				addedStrings = new HashMap<String, String>();
			}
			addedStrings.put(key, value);
			if (messages != null) {
				messages.put(key, value);
			}
		}
	}

	public boolean getUseNullList() {
		return nullValues != null;
	}

	public void setUseNullList(boolean useNullList) {
		if (useNullList && nullValues == null) {
			nullValues = new ArrayList(0);
		} else if (!useNullList && nullValues != null) {
			nullValues = null;
		}
	}

	public void clearUsedMessagesMap(int initialCapacity) {
		usedMessages = new LightHashMap(initialCapacity);
		if (nullValues != null) {
			nullValues = new ArrayList(0);
		}
	}
}
