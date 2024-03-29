/*
 * Created on 1 Nov 2006
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


package com.aelitis.azureus.core.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.*;
import java.util.*;

import org.gudy.azureus2.core3.util.AESemaphore;
import org.gudy.azureus2.core3.util.AEThread2;
import org.gudy.azureus2.core3.util.Constants;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.SystemTime;
import org.gudy.azureus2.pluginsimpl.local.utils.UtilitiesImpl.runnableWithException;

public class
NetUtils
{
	private static final int MIN_NI_CHECK_MILLIS 	= 30*1000;
	private static final int INC1_NI_CHECK_MILLIS 	= 2*60*1000;
	private static final int INC2_NI_CHECK_MILLIS 	= 15*60*1000;

	private static int	current_check_millis = MIN_NI_CHECK_MILLIS;

	private static long	last_ni_check	= -1;

	private static volatile List<NetworkInterface>		current_interfaces = new ArrayList<NetworkInterface>();

	private static boolean						first_check	= true;
	private static boolean						check_in_progress;

	static final AESemaphore					ni_sem = new AESemaphore("NetUtils:ni");

	private static final Map<Object,Object[]>			host_or_address_map 	= new HashMap<Object, Object[]>();

	private static final Object	RESULT_NULL = new Object();

	public static List<NetworkInterface>
	getNetworkInterfaces()

		throws SocketException
	{
		long	now = SystemTime.getMonotonousTime();

		boolean	do_check 	= false;
		boolean	is_first	= false;

		synchronized(NetUtils.class) {

			if (!check_in_progress) {

				if (last_ni_check < 0 || now - last_ni_check > current_check_millis) {

					do_check 			= true;
					check_in_progress	= true;

					if (first_check) {

						first_check = false;
						is_first	= true;
					}
				}
			}
		}

		if (do_check) {

			final runnableWithException<SocketException> do_it =
				new runnableWithException<SocketException>() {
					public void run()

						throws SocketException
					{
						List<NetworkInterface> result = new ArrayList<NetworkInterface>();

						try {
								// got some major CPU issues on some machines with crap loads of NIs

							long	start 	= SystemTime.getHighPrecisionCounter();

							Enumeration<NetworkInterface> nis = NetworkInterface_getNetworkInterfaces();

							long	elapsed_millis = (SystemTime.getHighPrecisionCounter() - start) / 1000000;

							long	old_period = current_check_millis;

							if (elapsed_millis > (Constants.isAndroid?5000:1000) && current_check_millis <  INC2_NI_CHECK_MILLIS) {

								current_check_millis = INC2_NI_CHECK_MILLIS;

							} else if (elapsed_millis > (Constants.isAndroid?1000:250) && current_check_millis < INC1_NI_CHECK_MILLIS) {

								current_check_millis = INC1_NI_CHECK_MILLIS;
							}

							if (old_period != current_check_millis) {

								Debug.out("Network interface enumeration took " + elapsed_millis + ": decreased refresh frequency to " + current_check_millis + "ms");
							}

							if (nis != null) {

								while (nis.hasMoreElements()) {

									result.add( nis.nextElement());
								}
							}

							// System.out.println("getNI: elapsed=" + elapsed_millis + ", result=" + result.size());

						} finally {

							synchronized(NetUtils.class) {

								check_in_progress	= false;
								current_interfaces 	= result;

								last_ni_check	= SystemTime.getMonotonousTime();
							}

							ni_sem.releaseForever();
						}
					}
				};

			if (is_first) {

				final AESemaphore do_it_sem = new AESemaphore("getNIs");

				final SocketException[]	error = { null };

				new AEThread2("getNIAsync") {
					public void run() {
						try {
							do_it.run();

						} catch (SocketException e) {

							error[0] = e;

						} finally {

							do_it_sem.release();
						}
					}
				}.start();

				if (!do_it_sem.reserve( 15*1000)) {

					Debug.out("Timeout obtaining network interfaces");

					ni_sem.releaseForever();

				} else {

					if (error[0] != null) {

						throw (error[0]);
					}
				}
			} else {

				do_it.run();
			}
		}

		ni_sem.reserve();

		return (current_interfaces);
	}

	public static InetAddress
	getLocalHost()

		throws UnknownHostException
	{
		try {
			return ( InetAddress.getLocalHost());

		} catch (Throwable e) {

				// sometimes get this when changing host name
				// return first non-loopback one

			try {
				List<NetworkInterface> 	nis = getNetworkInterfaces();

				for (NetworkInterface ni: nis) {

					Enumeration addresses = ni.getInetAddresses();

					while (addresses.hasMoreElements()) {

						InetAddress address = (InetAddress)addresses.nextElement();

						if (address.isLoopbackAddress() || address instanceof Inet6Address) {

							continue;
						}

						return (address);
					}
				}
			} catch (Throwable f) {
			}

			return (InetAddress.getByName("127.0.0.1"));
		}
	}

	public static NetworkInterface
	getByName(
		String name )

		throws SocketException
	{
		return (getBySupport( name));
	}

	public static NetworkInterface
	getByInetAddress(
		InetAddress addr )

		throws SocketException
	{
		return (getBySupport( addr));
	}

	private static NetworkInterface
	getBySupport(
		Object 	name_or_address )

		throws SocketException
	{
		Object[] entry;

		synchronized(host_or_address_map) {

			entry = host_or_address_map.get(name_or_address);

			if (entry != null) {

				synchronized(entry) {

					long	now = SystemTime.getMonotonousTime();

					Object result_or_error = entry[0];

					if (result_or_error != null) {

						if (((Long)entry[1]) > now) {

								// not expired

							if (result_or_error == RESULT_NULL) {

								return (null);

							} else if (result_or_error instanceof NetworkInterface) {

								return ((NetworkInterface)result_or_error);

							} else {

								throw ((SocketException)result_or_error);
							}
						}

						entry[0] = null;
					}
				}
			} else {

				entry = new Object[2];

				host_or_address_map.put(name_or_address, entry);
			}
		}

		synchronized(entry) {

				// if another thread has done a concurrent lookup then re-use result

			Object result_or_error = entry[0];

			if (result_or_error != null) {

				if (result_or_error == RESULT_NULL) {

					return (null);

				} else if (result_or_error instanceof NetworkInterface) {

					return ((NetworkInterface)result_or_error);

				} else {

					throw ((SocketException)result_or_error);
				}
			}

			long	start 	= SystemTime.getHighPrecisionCounter();

			Object			 	result 	= null;
			SocketException		error	= null;

			try {
				if (name_or_address instanceof String) {

					result = NetworkInterface.getByName((String)name_or_address);

				} else {

					result = NetworkInterface.getByInetAddress((InetAddress)name_or_address);

				}

				if (result == null) {

					result = RESULT_NULL;
				}
			} catch (SocketException e) {

				error = e;
			}

			long elapsed = (SystemTime.getHighPrecisionCounter() - start) / 1000000;

			entry[0] = result==null?error:result;

			long delay = 250*elapsed;

			if (delay > 5*60*1000) {

				delay = 5*60*1000;
			}

			entry[1] = SystemTime.getMonotonousTime() + delay;

			if (error != null) {

				throw (error);

			} else {

				if (result == RESULT_NULL) {

					return (null);

				} else {

					return ((NetworkInterface)result);
				}
			}
		}
	}


	/**
	 * Calls NetworkInterface.getNetworkInterface, tries to recover from
	 * SocketException on some Android devices
	 */
	private static Enumeration<NetworkInterface> NetworkInterface_getNetworkInterfaces()
			throws SocketException {
		SocketException se;
		try {
			return NetworkInterface.getNetworkInterfaces();
		} catch (SocketException e) {
			/*
			Found on Android API 22 (Sony Bravia Android TV):
			java.net.SocketException
			     at java.net.NetworkInterface.rethrowAsSocketException(NetworkInterface.java:248)
			     at java.net.NetworkInterface.readIntFile(NetworkInterface.java:243)
			     at java.net.NetworkInterface.getByNameInternal(NetworkInterface.java:121)
			     at java.net.NetworkInterface.getNetworkInterfacesList(NetworkInterface.java:309)
			     at java.net.NetworkInterface.getNetworkInterfaces(NetworkInterface.java:298)
			     at whatevercalled getNetworkInterfaces()
			 Caused by: java.io.FileNotFoundException: /sys/class/net/p2p1/ifindex: open failed: ENOENT (No such file or directory)
			     at libcore.io.IoBridge.open(IoBridge.java:456)
			     at libcore.io.IoUtils$FileReader.<init>(IoUtils.java:209)
			     at libcore.io.IoUtils.readFileAsString(IoUtils.java:116)
			     at java.net.NetworkInterface.readIntFile(NetworkInterface.java:236)
			 	... 18 more
			 Caused by: android.system.ErrnoException: open failed: ENOENT (No such file or directory)
			     at libcore.io.Posix.open(Native Method)
			     at libcore.io.BlockGuardOs.open(BlockGuardOs.java:186)
			     at libcore.io.IoBridge.open(IoBridge.java:442)
			 	... 21 more
			 	*/
			se = e;
		}

		// Java 7 has getByIndex
		try {
			Method mGetByIndex = NetworkInterface.class.getDeclaredMethod(
					"getByIndex", int.class);
			List<NetworkInterface> list = new ArrayList<NetworkInterface>();
			int i = 0;
			do {
				//NetworkInterface nif = NetworkInterface.getByIndex(i);
				NetworkInterface nif = null;
				try {
					nif = (NetworkInterface) mGetByIndex.invoke(null, i);
				} catch (IllegalAccessException e) {
					break;
				} catch (InvocationTargetException ignore) {
					// getByIndex throws SocketException
				}
				if (nif != null) {
					list.add(nif);
				} else if (i > 0) {
					break;
				}
				i++;
			} while (true);
			if (list.size() > 0) {
				return Collections.enumeration(list);
			}
		} catch (NoSuchMethodException ignore) {
		}

		// Worst case, try some common interface names
		List<NetworkInterface> list = new ArrayList<NetworkInterface>();
		final String[] commonNames = {
			"lo",
			"eth",
			"lan",
			"wlan",
			"en", // Some Android's Ethernet
			"p2p", // Android
			"net", // Windows, usually TAP
			"ppp" // Windows
		};
		for (String commonName : commonNames) {
			try {
				NetworkInterface nif = NetworkInterface.getByName(commonName);
				if (nif != null) {
					list.add(nif);
				}

				// Could interfaces skip numbers?  Oh well..
				int i = 0;
				while (true) {
					nif = NetworkInterface.getByName(commonName + i);
					if (nif != null) {
						list.add(nif);
					} else {
						break;
					}
					i++;
				}
			} catch (Throwable ignore) {
			}
		}
		if (list.size() > 0) {
			return Collections.enumeration(list);
		}

		throw se;
	}
}
