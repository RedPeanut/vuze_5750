/*
 * Created on 18-Apr-2004
 * Created by Paul Gardner
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

package org.gudy.azureus2.platform;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.gudy.azureus2.core3.util.AEMonitor;
import org.gudy.azureus2.core3.util.Constants;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.plugins.platform.PlatformManagerException;

import hello.util.Log;

/**
 * @author parg
 *
 */
public class PlatformManagerFactory {
	
	private static String TAG = PlatformManagerFactory.class.getSimpleName();
	
	protected static PlatformManager	platformManager;
	protected static AEMonitor			class_mon	= new AEMonitor("PlatformManagerFactory");

	public static PlatformManager getPlatformManager() {
		try {
			boolean forceDummy = System.getProperty("azureus.platform.manager.disable", "false" ).equals("true");
			class_mon.enter();
			if (platformManager == null && !forceDummy) {
				try {
					String cla = System.getProperty("az.factory.platformmanager.impl", "");
					//Log.d(TAG, ">>> cla = " + cla);
					if (cla.length() == 0) {
						int platformType = getPlatformType();
						switch (platformType) {
							case PlatformManager.PT_WINDOWS:
								cla = "org.gudy.azureus2.platform.win32.PlatformManagerImpl";
								break;
							case PlatformManager.PT_MACOSX:
								cla = "org.gudy.azureus2.platform.macosx.PlatformManagerImpl";
								break;
							case PlatformManager.PT_UNIX:
								cla = "org.gudy.azureus2.platform.unix.PlatformManagerImpl";
								break;
							default:
								cla = "org.gudy.azureus2.platform.dummy.PlatformManagerImpl";
								break;
						}
					}
					
					Class<?> platformManagerClass = Class.forName(cla);
					try {
						Method methGetSingleton = platformManagerClass.getMethod("getSingleton");
						platformManager = (PlatformManager) methGetSingleton.invoke(null);
					} catch (NoSuchMethodException e) {
					} catch (SecurityException e) {
					} catch (IllegalAccessException e) {
					} catch (IllegalArgumentException e) {
					} catch (InvocationTargetException e) {
					}
					
					if (platformManager == null) {
						platformManager = (PlatformManager)Class.forName(cla).newInstance();
					}
				} catch (Throwable e) {
					// exception will already have been logged
					if (!(e instanceof PlatformManagerException)) {
						Debug.printStackTrace(e);
					}
				}
			}
			if (platformManager == null) {
				platformManager = org.gudy.azureus2.platform.dummy.PlatformManagerImpl.getSingleton();
			}
			return (platformManager);
		} finally {
			class_mon.exit();
		}
	}

	public static int getPlatformType() {
		if (Constants.isWindows) {
			return (PlatformManager.PT_WINDOWS);
		} else if (Constants.isOSX) {
			return (PlatformManager.PT_MACOSX);
		} else if (Constants.isUnix) {
			return (PlatformManager.PT_UNIX);
		} else {
			return (PlatformManager.PT_OTHER);
		}
	}
}
