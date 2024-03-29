/*
 * Created on Jan 21, 2016
 * Created by Paul Gardner
 *
 * Copyright 2016 Azureus Software, Inc.  All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 */


package com.aelitis.azureus.plugins.net.buddy;

import org.gudy.azureus2.core3.util.Debug;

public class
BuddyPluginUI
{
	private static Class<?> impl_class;

	static{
		try {
			impl_class = BuddyPluginUI.class.getClassLoader().loadClass(  "com.aelitis.azureus.plugins.net.buddy.swt.SBC_ChatOverview");

		} catch (Throwable e) {
		}
	}

	public static void
	preInitialize() {
		if (impl_class != null) {

			try {
				impl_class.getMethod("preInitialize" ).invoke( null);

			} catch (Throwable e) {

				Debug.out(e);
			}
		}
	}

	public static boolean
	openChat(String network,
		String key) {
		if (impl_class != null) {

			try {
				impl_class.getMethod("openChat", String.class, String.class ).invoke( null, network, key);

				return (true);

			} catch (Throwable e) {

				Debug.out(e);
			}
		} else {

			Debug.out("Not supported");
		}

		return (false);
	}
}
