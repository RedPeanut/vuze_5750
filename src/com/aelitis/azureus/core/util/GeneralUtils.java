/*
 * Created on Jun 9, 2008
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


package com.aelitis.azureus.core.util;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.config.ParameterListener;
import org.gudy.azureus2.core3.util.Constants;

import com.aelitis.azureus.core.util.average.AverageFactory;
import com.aelitis.azureus.core.util.average.MovingImmediateAverage;

public class
GeneralUtils
{
		/**
		 * string.replaceAll does \\ and $ expansion in replacement, this doesn't, in fact it
		 * doesn't do any pattern matching at all, it is a literal replacement
		 * @param str
		 * @param from_str		= NOTE, no regex support
		 * @param replacement
		 * @return
		 */

        // XXX: This doesn't appear to be used...
    public static String
	replaceAll(String	str,
		String	from_str,
		String	replacement) {
		StringBuffer	res = null;

		int	pos = 0;

		while (true) {

			int	p1 = str.indexOf(from_str, pos);

			if (p1 == -1) {

				if (res == null) {

					return (str);
				}

				res.append(str.substring( pos));

				return ( res.toString());

			} else {

				if (res == null) {

					res = new StringBuffer(str.length() * 2);
				}

				if (p1 > pos) {

					res.append(str.substring( pos, p1));
				}

				res.append(replacement);

				pos = p1 + from_str.length();
			}
		}
	}

	/**
	 * as above but does safe replacement of multiple strings (i.e. a match in the replacement
	 * of one string won't be substituted by another)
	 * @param str
	 * @param from_strs
	 * @param to_strs
	 * @return
	 */
	public static String
	replaceAll(String		str,
		String[]	from_strs,
		String[]	to_strs) {
		StringBuffer	res = null;

		int	pos = 0;

		while (true) {

			int	min_match_pos 	= Integer.MAX_VALUE;
			int	match_index		= -1;

			for (int i=0;i<from_strs.length;i++) {

				int	pt = str.indexOf(from_strs[i], pos);

				if (pt != -1) {

					if (pt < min_match_pos) {

						min_match_pos		= pt;
						match_index			= i;
					}
				}
			}

			if (match_index == -1) {

				if (res == null) {

					return (str);
				}

				res.append(str.substring( pos));

				return ( res.toString());

			} else {

				if (res == null) {

					res = new StringBuffer(str.length() * 2);
				}

				if (min_match_pos > pos) {

					res.append(str.substring( pos, min_match_pos));
				}

				res.append(to_strs[match_index]);

				pos = min_match_pos + from_strs[match_index].length();
			}
		}
	}

	private final static String REGEX_URLHTML = "<A HREF=\"(.+?)\">(.+?)</A>";
	public static String stripOutHyperlinks(String message) {
		return Pattern.compile(REGEX_URLHTML, Pattern.CASE_INSENSITIVE).matcher(
				message).replaceAll("$2");
	}

		/**
		 * splits space separated tokens respecting quotes (either " or ' )
		 * @param str
		 * @return
		 */

	public static String[]
	splitQuotedTokens(String		str) {
		List<String>	bits = new ArrayList<String>();

		char	quote 				= ' ';
		boolean	escape 				= false;
		boolean	bit_contains_quotes = false;

		String	bit = "";

		char[] chars = str.toCharArray();

		for (int i=0;i<chars.length;i++) {

			char c = chars[i];

			if (Character.isWhitespace(c)) {

				c = ' ';
			}

			if (escape) {

				bit += c;

				escape = false;

				continue;

			} else if (c == '\\') {

				escape = true;

				continue;
			}

			if (c == '"' || c == '\'' && ( i == 0 || chars[ i-1 ] != '\\')) {

				if (quote == ' ') {

					bit_contains_quotes = true;

					quote = c;

				} else if (quote == c) {

					quote = ' ';

				} else {

					bit += c;
				}
			} else {

				if (quote == ' ') {

					if (c == ' ') {

						if (bit.length() > 0 || bit_contains_quotes) {

							bit_contains_quotes = false;

							bits.add(bit);

							bit = "";
						}
					} else {

						bit += c;
					}
				} else {

					bit += c;
				}
			}
		}

		if (quote != ' ') {

			bit += quote;
		}

		if (bit.length() > 0 || bit_contains_quotes) {

			bits.add(bit);
		}

		return ( bits.toArray(new String[bits.size()]));
	}

	public static ProcessBuilder
	createProcessBuilder(
		File workingDir,
		String[] cmd,
		String[] extra_env)

		throws IOException
	{
		ProcessBuilder pb;

		Map<String, String> newEnv = new HashMap<String, String>();
		newEnv.putAll(System.getenv());
		newEnv.put("LANG", "C.UTF-8");
		if (extra_env != null && extra_env.length > 1) {
			for (int i = 1; i < extra_env.length; i += 2) {
				newEnv.put(extra_env[i - 1], extra_env[i]);
			}
		}

		if (Constants.isWindows) {
			String[] i18n = new String[cmd.length + 2];
			i18n[0] = "cmd";
			i18n[1] = "/C";
			i18n[2] = escapeDosCmd(cmd[0]);
			for (int counter = 1; counter < cmd.length; counter++) {
				if (cmd[counter].length() == 0) {
					i18n[counter + 2] = "";
				} else {
					String envName = "JENV_" + counter;
					i18n[counter + 2] = "%" + envName + "%";
					newEnv.put(envName, cmd[counter]);
				}
			}
			cmd = i18n;
		}

		pb = new ProcessBuilder(cmd);
		Map<String, String> env = pb.environment();
		env.putAll(newEnv);

		if (workingDir != null) {
			pb.directory(workingDir);
		}
		return pb;
	}

	private static String escapeDosCmd(String string) {
		String s = string.replaceAll("([&%^])", "^$1");
		s = s.replaceAll("'", "\"'\"");
		return s;
	}

	private static int SMOOTHING_UPDATE_WINDOW	 	= 60;
	private static int SMOOTHING_UPDATE_INTERVAL 	= 1;


	static{
		COConfigurationManager.addAndFireParameterListener(
			"Stats Smoothing Secs",
			new ParameterListener() {
				public void parameterChanged(String xxx) {
					SMOOTHING_UPDATE_WINDOW	= COConfigurationManager.getIntParameter("Stats Smoothing Secs");

					if (SMOOTHING_UPDATE_WINDOW < 30) {

						SMOOTHING_UPDATE_WINDOW = 30;

					} else if (SMOOTHING_UPDATE_WINDOW > 30*60) {

						SMOOTHING_UPDATE_WINDOW = 30*60;
					}

					SMOOTHING_UPDATE_INTERVAL = SMOOTHING_UPDATE_WINDOW/60;

					if (SMOOTHING_UPDATE_INTERVAL < 1) {

						SMOOTHING_UPDATE_INTERVAL = 1;

					} else if (SMOOTHING_UPDATE_INTERVAL > 20) {

						SMOOTHING_UPDATE_INTERVAL = 20;
					}
				}
			});
	}

	public static int
	getSmoothUpdateWindow() {
		return (SMOOTHING_UPDATE_WINDOW);
	}

	public static int
	getSmoothUpdateInterval() {
		return (SMOOTHING_UPDATE_INTERVAL);
	}

	public static MovingImmediateAverage
	getSmoothAverage() {
		return (AverageFactory.MovingImmediateAverage(SMOOTHING_UPDATE_WINDOW/SMOOTHING_UPDATE_INTERVAL));
	}

	public static String stringJoin(Collection list, String delim) {
		StringBuilder sb = new StringBuilder();
		for (Object s : list) {
			if (s == null) {
				continue;
			}
			if (sb.length() > 0) {
				sb.append(delim);
			}
			sb.append(s.toString());
		}
		return sb.toString();
	}
}
