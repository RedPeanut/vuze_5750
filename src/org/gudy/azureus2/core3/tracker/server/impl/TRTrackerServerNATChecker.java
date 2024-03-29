/*
 * Created on 29-Jul-2004
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

package org.gudy.azureus2.core3.tracker.server.impl;

/**
 * @author parg
 *
 */

import java.util.*;
import java.net.*;

import org.gudy.azureus2.core3.tracker.server.*;

import org.gudy.azureus2.core3.config.*;
import org.gudy.azureus2.core3.util.*;
import org.gudy.azureus2.core3.logging.*;

import com.aelitis.azureus.core.proxy.AEProxyFactory;

public class TRTrackerServerNATChecker {
	
	private static final LogIDs LOGID = LogIDs.TRACKER;
	
	protected static final TRTrackerServerNATChecker singleton	= new TRTrackerServerNATChecker();

	protected static final int THREAD_POOL_SIZE		= 32;
	protected static final int CHECK_QUEUE_LIMIT	= 2048;

	protected static int checkTimeout		= TRTrackerServer.DEFAULT_NAT_CHECK_SECS*1000;

	protected static TRTrackerServerNATChecker getSingleton() {
		return (singleton);
	}

	protected boolean		enabled;
	protected ThreadPool	threadPool;

	protected final List<ThreadPoolTask>	checkQ		= new ArrayList<>();
	protected final AESemaphore				checkQSem	= new AESemaphore("TracerServerNATChecker");
	protected final AEMonitor				checkQMon	= new AEMonitor("TRTrackerServerNATChecker:Q");

	protected final AEMonitor 	thisMon 		= new AEMonitor("TRTrackerServerNATChecker");

	protected TRTrackerServerNATChecker() {
		final String	enableParam 	= "Tracker NAT Check Enable";
		final String	timeoutParam	= "Tracker NAT Check Timeout";

		final String[]	params = { enableParam, timeoutParam };

		for (int i=0;i<params.length;i++) {
			COConfigurationManager.addParameterListener(
				params[i],
				new ParameterListener() {
					public void parameterChanged(
						String parameter_name) {
						checkConfig(enableParam, timeoutParam);
					}
				});
		}

		checkConfig(enableParam, timeoutParam);
	}

	protected boolean isEnabled() {
		return (enabled);
	}

	protected void checkConfig(
		String	enableParam,
		String	timeoutParam) {
		try {
			thisMon.enter();
			enabled = COConfigurationManager.getBooleanParameter(enableParam);
			checkTimeout = COConfigurationManager.getIntParameter(timeoutParam) * 1000;
			if (checkTimeout < 1000) {
				Debug.out("NAT check timeout too small - " + checkTimeout);
				checkTimeout	= 1000;
			}
			
			if (threadPool == null) {
				threadPool	= new ThreadPool("Tracker NAT Checker", THREAD_POOL_SIZE);
				threadPool.setExecutionLimit(checkTimeout);
				Thread	dispatcherThread =
					new AEThread("Tracker NAT Checker Dispatcher") {
						public void runSupport() {
							while (true) {
								checkQSem.reserve();
								ThreadPoolTask	task;
								try {
									checkQMon.enter();
									task = (ThreadPoolTask)checkQ.remove(0);
								} finally {
									checkQMon.exit();
								}
								
								try {
									threadPool.run(task);
								} catch (Throwable e) {
									Debug.printStackTrace(e);
								}
							}
						}
					};
				dispatcherThread.setDaemon(true);
				dispatcherThread.start();
			} else {
				threadPool.setExecutionLimit(checkTimeout);
			}
		} finally {
			thisMon.exit();
		}
	}

	protected boolean addNATCheckRequest(
		final String								host,
		final int									port,
		final TRTrackerServerNatCheckerListener		listener) {
		if ((!enabled) || threadPool == null) {
			return (false);
		}
		try {
			checkQMon.enter();
			if (checkQ.size() > CHECK_QUEUE_LIMIT) {
				if (Logger.isEnabled())
					Logger.log(new LogEvent(LOGID, LogEvent.LT_WARNING,
							"NAT Check queue size too large, check for '" + host + ":" + port
									+ "' skipped"));
				//Debug.out("NAT Check queue size too large, check skipped");
				listener.NATCheckComplete(true);
			} else {
				checkQ.add(
					new ThreadPoolTask() {
						protected	Socket	socket;
						public void runSupport() {
							boolean	ok = false;
							try {
								InetSocketAddress address =
									new InetSocketAddress(AEProxyFactory.getAddressMapper().internalise(host), port);
								socket = new Socket();
								socket.connect(address, checkTimeout);
								ok	= true;
								socket.close();
								socket	= null;
							} catch (Throwable e) {
							} finally {
								listener.NATCheckComplete(ok);
								if (socket != null) {
									try {
										socket.close();
									} catch (Throwable e) {
									}
								}
							}
						}
						
						public void interruptTask() {
							if (socket != null) {
								try {
									socket.close();
								} catch (Throwable e) {
								}
							}
						}
					});
				checkQSem.release();
			}
		} finally {
			checkQMon.exit();
		}
		return (true);
	}
}
