/*
 * Created on May 29, 2006 4:23:01 PM
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
package com.aelitis.azureus.ui.skin;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.gudy.azureus2.core3.internat.IntegratedResourceBundle;
import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.logging.LogIDs;
import org.gudy.azureus2.core3.util.Constants;
import org.gudy.azureus2.core3.util.Debug;

import hello.util.Log;

/**
 * Implementation of SkinProperties using a IntegratedResourceBundle loaded from
 * hard coded paths.
 * <P>
 * Three level lookup of keys:
 * <li>(plugin) skin property file
 * <li>defaults property file
 * <li>Azureus MessageText class
 * <br>
 * Additionally, checks each for platform specific keys.
 * <p><br>
 * Values containing "{*}" are replaced with a lookup of *
 *
 * @author TuxPaper
 * @created May 29, 2006
 *
 */
public class SkinPropertiesImpl
	implements SkinProperties
{
	private static final String TAG = SkinPropertiesImpl.class.getSimpleName();
	
	private static final LogIDs LOGID = LogIDs.UI3;
	public static final String PATH_SKIN_DEFS = "com/aelitis/azureus/ui/skin/";
	private static final String FILE_SKIN_DEFS = "skin3.properties";
	private static final Pattern PAT_PARAM_ALPHA = Pattern.compile("\\{([^0-9].+?)\\}");
	private static final Pattern PAT_PARAM_NUM = Pattern.compile("\\{([0-9]+?)\\}");
	private IntegratedResourceBundle rb;
	private final ClassLoader classLoader;
	private int emHeightPX = 15;

	public SkinPropertiesImpl() {
		this(SkinPropertiesImpl.class.getClassLoader(), PATH_SKIN_DEFS,
				FILE_SKIN_DEFS);
	}

	public SkinPropertiesImpl(ClassLoader classLoader, String skinPath,
			String mainSkinFile) {
		this.classLoader = classLoader;
		skinPath = skinPath.replaceAll("/", ".");
		if (!skinPath.endsWith(".")) {
			skinPath += ".";
		}
		if (mainSkinFile.endsWith(".properties")) {
			mainSkinFile = mainSkinFile.substring(0, mainSkinFile.length() - 11);
		}
		//Log.d(TAG, "skinPath + mainSkinFile = " + skinPath + mainSkinFile);
		ResourceBundle bundle = ResourceBundle.getBundle(skinPath + mainSkinFile,
				Locale.getDefault(), classLoader);
		rb = new IntegratedResourceBundle(bundle, Collections.EMPTY_MAP, 1200);
		rb.setUseNullList(true);

		String sFiles = rb.getString("skin.include", null);
		if (sFiles != null) {
			String[] sFilesArray = sFiles.split(",");
			for (int i = 0; i < sFilesArray.length; i++) {
				String sFile = (sFilesArray[i].startsWith("/")
						? sFilesArray[i].substring(1) : skinPath + sFilesArray[i]);
				sFile = sFile.replaceAll("/", ".");
				try {
					ResourceBundle subBundle = ResourceBundle.getBundle(sFile,
							Locale.getDefault(), classLoader);
					rb.addResourceMessages(subBundle);
				} catch (Throwable t) {
					Debug.out("Err loading skin include: " + sFile, t);
				}
			}
		}
	}

	public void addResourceBundle(ResourceBundle subBundle, String skinPath) {
		addResourceBundle(subBundle, skinPath, classLoader);
	}

	public void addResourceBundle(ResourceBundle subBundle, String skinPath, ClassLoader loader) {
		try {
			clearCache();
			rb.addResourceMessages(subBundle);

			try {
				String sFiles = subBundle.getString("skin.include");

				if (sFiles != null && skinPath != null) {

					String[] sFilesArray = Constants.PAT_SPLIT_COMMA.split(sFiles);
					for (int i = 0; i < sFilesArray.length; i++) {
						String sFile = (sFilesArray[i].startsWith("/")
								? sFilesArray[i].substring(1) : skinPath + sFilesArray[i]);
						sFile = sFile.replaceAll("/", ".");
						try {
							ResourceBundle incBundle = ResourceBundle.getBundle(sFile,
									Locale.getDefault(), loader);
							rb.addResourceMessages(incBundle);
						} catch (Throwable t) {
							Debug.out("Err loading skin include: " + sFile, t);
						}
					}
				}
			} catch (MissingResourceException e) {
				// get this if skin.include not defined, which is entirely possible
			}
		} catch (Throwable t) {
  		Debug.out("Err loading skin include: " + subBundle, t);
  	}
	}

	//	public Properties getProperties() {
	//		return properties;
	//	}

	public void addProperty(String name, String value) {
		rb.addString(name, value);
	}

	public boolean hasKey(String name) {
		if (name == null) {
			return false;
		}

		String osName = null;
		if (Constants.isWindows) {
			osName = name + "._windows";
		} else if (Constants.isOSX) {
			osName = name + "._mac";
		} else if (Constants.isUnix) {
			osName = name + "._unix";
		} else if (Constants.isFreeBSD) {
			osName = name + "._freebsd";
		} else if (Constants.isLinux) {
			osName = name + "._linux";
		} else if (Constants.isSolaris) {
			osName = name + "._solaris";
		}

		boolean contains = false;
		if (osName != null) {
			// can't use containsKey on IntegratedResourceBundle :(
			contains = rb.getString(osName, null) != null;
		}

		if (!contains) {
			contains = rb.getString(name, null) != null;
		}
		return contains;
	}


	public String getReferenceID(String name) {
		String value = getValue(name, null, false);
		if (value == null || value.length() < 2) {
			return null;
		}
		if (value.charAt(0) == '{' && value.charAt(value.length() - 1) == '}') {
			return value.substring(1, value.length() - 1);
		}
		return null;
	}

	protected String getValue(String name, String[] params) {
		return getValue(name, params, true);
	}

	private String getValue(String name, String[] params, boolean expandReferences) {
		String value = null;
		String osName = null;

		if (name == null) {
			return null;
		}

		if (Constants.isWindows) {
			osName = name + "._windows";
		} else if (Constants.isOSX) {
			osName = name + "._mac";
		} else if (Constants.isUnix) {
			osName = name + "._unix";
		} else if (Constants.isFreeBSD) {
			osName = name + "._freebsd";
		} else if (Constants.isLinux) {
			osName = name + "._linux";
		} else if (Constants.isSolaris) {
			osName = name + "._solaris";
		}

		if (osName != null) {
			value = rb.getString(osName, null);
		}

		if (value == null) {
			value = rb.getString(name, null);
		}

		if (expandReferences && value != null && value.indexOf('}') > 0) {
			Matcher matcher;

			if (params != null) {
				matcher = PAT_PARAM_NUM.matcher(value);
				while (matcher.find()) {
					String key = matcher.group(1);
					try {
						int i = Integer.parseInt(key);

						if (i < params.length) {
							value = value.replaceAll("\\Q{" + key + "}\\E", params[i]);
						} else {
							value = value.replaceAll("\\Q{" + key + "}\\E", "");
						}
					} catch (Exception e) {
					}
				}
			}

			matcher = PAT_PARAM_ALPHA.matcher(value);
			while (matcher.find()) {
				String key = matcher.group(1);
				String text = getValue(key, params);
				if (text == null) {
					text = MessageText.getString(key);
				}
				value = value.replaceAll("\\Q{" + key + "}\\E", text);
			}
		}

		return value;
	}

	public int getIntValue(String name, int def) {
		String value = getValue(name, null);
		if (value == null) {
			return def;
		}

		int result = def;
		try {
			if (value.endsWith("rem")) {
				float em = Float.parseFloat(value.substring(0, value.length() - 3));

				result = (int) (emHeightPX * em);
			} else {
				result = Integer.parseInt(value);
			}
		} catch (NumberFormatException e) {
			// ignore error.. it might be valid to store a non-numeric..
			//e.printStackTrace();
		}
		return result;
	}

	public int[] getColorValue(String name) {
		int[] colors = new int[4];
		String value = getValue(name, null);

		if (value == null || value.length() == 0 || value.startsWith("COLOR_")) {
			colors[0] = colors[1] = colors[2] = -1;
			return colors;
		}

		try {
			if (value.charAt(0) == '#') {
				// hex color string
				long l = Long.parseLong(value.substring(1), 16);
				if (value.length() == 9) {
					colors = new int[] {
						(int) ((l >> 24) & 255),
						(int) ((l >> 16) & 255),
						(int) ((l >> 8) & 255),
						(int) (l & 255)
					};
				} else {
  				colors[0] = (int) ((l >> 16) & 255);
  				colors[1] = (int) ((l >> 8) & 255);
  				colors[2] = (int) (l & 255);
  				colors[3] = 255;
				}
			} else if (value.contains(",")) {
				StringTokenizer st = new StringTokenizer(value, ",");
				colors[0] = Integer.parseInt(st.nextToken());
				colors[1] = Integer.parseInt(st.nextToken());
				colors[2] = Integer.parseInt(st.nextToken());
				colors[3] = st.hasMoreTokens() ? Integer.parseInt(st.nextToken()) : 255;
			} else {
				colors[0] = colors[1] = colors[2] = -1;
			}
		} catch (Exception e) {
			//e.printStackTrace();
			colors[0] = colors[1] = colors[2] = -1;
		}

		return colors;
	}

	public String getStringValue(String name) {
		return getStringValue(name, (String[]) null);
	}

	public String getStringValue(String name, String def) {
		return getStringValue(name, (String[]) null, def);
	}

	public String[] getStringArray(String name) {
		return getStringArray(name, (String[]) null);
	}

	public String[] getStringArray(String name, String[] params) {
		String s = getValue(name, params);
		if (s == null) {
			return null;
		}

		String[] values = Constants.PAT_SPLIT_COMMAWORDS.split(s);
		if (values == null) {
			return new String[] {
				s
			};
		}

		return values;
	}

	public String getStringValue(String name, String[] params) {
		return getValue(name, params);
	}

	public String getStringValue(String name, String[] params, String def) {
		String s = getValue(name, params);
		return (s == null) ? def : s;
	}

	// @see com.aelitis.azureus.ui.skin.SkinProperties#getBooleanValue(java.lang.String, boolean)
	public boolean getBooleanValue(String name, boolean def) {
		String s = getStringValue(name, (String) null);
		if (s == null) {
			return def;
		}
		return s.toLowerCase().equals("true") || s.equals("1");
	}

	public void clearCache() {
		rb.clearUsedMessagesMap(1);
	}

	public ClassLoader getClassLoader() {
		return classLoader;
	}

	protected void setEmHeightPX(int fontHeightInPX) {
		this.emHeightPX = fontHeightInPX;
	}

	public int getEmHeightPX() {
		return emHeightPX;
	}
}
