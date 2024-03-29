/**
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

package com.aelitis.azureus.util;

import java.util.*;

import org.gudy.azureus2.core3.util.Base32;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.bouncycastle.util.encoders.Base64;

/**
 * @author TuxPaper
 * @created Jun 1, 2007
 *
 */
public class MapUtils
{
	public static int getMapInt(Map map, String key, int def) {
		if (map == null) {
			return def;
		}
		try {
			Number n = (Number) map.get(key);

			if (n == null) {

				return (def);
			}

			return n.intValue();
		} catch (Throwable e) {
			Debug.out(e);
			return def;
		}
	}

	public static long getMapLong(Map map, String key, long def) {
		if (map == null) {
			return def;
		}
		try {
			Number n = (Number) map.get(key);

			if (n == null) {

				return (def);
			}

			return n.longValue();
		} catch (Throwable e) {
			Debug.out(e);
			return def;
		}
	}

	public static String getMapString(Map map, String key, String def) {
		if (map == null) {
			return def;
		}
		try {
			Object o = map.get(key);
			if (o == null && !map.containsKey(key)) {
				return def;
			}
			// NOTE: The above returns def when map doesn't contain the key,
			//       which suggests below we would return the null when o is null.
			//       But we don't! And now, some callers rely on this :(

			if (o instanceof String) {
				return (String) o;
			}
			if (o instanceof byte[]) {
				return new String((byte[]) o, "utf-8");
			}
			return def;
		} catch (Throwable t) {
			Debug.out(t);
			return def;
		}
	}

	public static String[]
	getMapStringArray(
		Map			map,
		String		key,
		String[]	def) {
		Object o = map.get(key);
		if (!(o instanceof List)) {
			return def;
		}
		List list = (List) o;
		String[] result = new String[list.size()];
		for (int i=0;i<result.length;i++) {
			result[i] = getString( list.get(i));
		}
		return (result);
	}

	public static String getString(
		Object	obj) {
		if (obj instanceof String) {
			return ((String)obj);
		} else if (obj instanceof byte[]) {

			try {
				return new String((byte[])obj, "UTF-8");
			} catch (Throwable e) {

			}
		}
		return (null);
	}

	public static void setMapString(Map map, String key, String val) {
		if (map == null) {
			Debug.out("Map is null!");
			return;
		}
		try {
			if (val == null) {
				map.remove(key);
			} else {
				map.put(key, val.getBytes("utf-8"));
			}
		} catch (Throwable e) {
			Debug.out(e);
		}
	}

	public static byte[] getMapByteArray(Map map, String key, byte[] def) {
		if (map == null) {
			return def;
		}
		try {
			Object o = map.get(key);
			if (o instanceof byte[]) {
				return (byte[]) o;
			}

			String b64Key = key + ".B64";
			if (map.containsKey(b64Key)) {
				o = map.get(b64Key);
				if (o instanceof String) {
					return Base64.decode((String) o);
				}
			}

			String b32Key = key + ".B32";
			if (map.containsKey(b32Key)) {
				o = map.get(b32Key);
				if (o instanceof String) {
					return Base32.decode((String) o);
				}
			}

			return def;
		} catch (Throwable t) {
			Debug.out(t);
			return def;
		}
	}

	public static Object getMapObject(Map map, String key, Object def, Class cla) {
		if (map == null) {
			return def;
		}
		try {
			Object o = map.get(key);
			if (cla.isInstance(o)) {
				return o;
			} else {
				return def;
			}
		} catch (Throwable t) {
			Debug.out(t);
			return def;
		}
	}

	public static boolean getMapBoolean(Map map, String key, boolean def) {
		if (map == null) {
			return def;
		}
		try {
			Object o = map.get(key);
			if (o instanceof Boolean) {
				return ((Boolean) o).booleanValue();
			}

			if (o instanceof Long) {
				return ((Long) o).longValue() == 1;
			}

			return def;
		} catch (Throwable e) {
			Debug.out(e);
			return def;
		}
	}

	public static List getMapList(Map map, String key, List def) {
		if (map == null) {
			return def;
		}
		try {
			List list = (List) map.get(key);
			if (list == null && !map.containsKey(key)) {
				return def;
			}
			return list;
		} catch (Throwable t) {
			Debug.out(t);
			return def;
		}
	}

	public static Map getMapMap(Map map, String key, Map def) {
		if (map == null) {
			return def;
		}
		try {
			Map valMap = (Map) map.get(key);
			if (valMap == null && !map.containsKey(key)) {
				return def;
			}
			return valMap;
		} catch (Throwable t) {
			Debug.out(t);
			return def;
		}
	}

}
