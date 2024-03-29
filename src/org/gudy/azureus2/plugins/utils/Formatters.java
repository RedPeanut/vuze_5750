/*
 * File    : Formatter.java
 * Created : 30-Mar-2004
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

package org.gudy.azureus2.plugins.utils;

import java.io.IOException;
import java.util.Comparator;
import java.util.Map;

import org.gudy.azureus2.core3.util.Constants;

/**
 * @author parg
 *
 */
public interface Formatters {
	
	public String BYTE_ENCODING = Constants.BYTE_ENCODING;
	public String TEXT_ENCODING = Constants.DEFAULT_ENCODING;

	public String formatByteCountToKiBEtc(long bytes);
	public String formatByteCountToKiBEtcPerSec(long bytes);
	public String formatPercentFromThousands(long thousands);
	public String formatByteArray(byte[] data, boolean noSpaces);
	public String encodeBytesToString(byte[] bytes);
	public byte[] decodeBytesFromString(String str);
	public String formatDate(long millis);

	/**
	 * @since 3.0.5.3
	 */
	public String formatTimeOnly(long millis);

	/**
	 * @since 3.0.5.3
	 */
	public String formatTimeOnly(long millis, boolean include_secs);

	/**
	 * @since 3.0.5.3
	 */
	public String formatDateOnly(long millis);
	public String formatTimeFromSeconds(long seconds);

	/**
	 * Format seconds remaining into an ETA value.
	 * @param seconds
	 * @return
	 * @since 2.4.0.3
	 */
	public String formatETAFromSeconds(long seconds);

	public byte[] bEncode(Map map) throws IOException;
	public Map bDecode(byte[] data) throws IOException;
	public String base32Encode(byte[] data);
	public byte[] base32Decode(String data);
	public Comparator getAlphanumericComparator(boolean ignore_case);

}
