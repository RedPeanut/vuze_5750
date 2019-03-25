/*
 * Created on 18-Sep-2004
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

package org.gudy.azureus2.core3.util;

/**
 * @author parg
 *
 */

import java.util.*;

import hello.util.Log;

public class AEMonitor extends AEMonSem {
	
	private static String TAG = AEMonitor.class.getSimpleName();
	
	private int dontWait		= 1;
	private int nests			= 0;
	private int totalReserve	= 0;
	private int totalRelease	= 1;

	protected Thread owner;
	protected Thread lastWaiter;

	public AEMonitor(String _name) {
		super(_name, true);
	}

	public void enter() {
		enter(null);
	}

	public void enter(String from) {
		
		if (DEBUG) {
			debugEntry();
		}
		
		Thread currentThread = Thread.currentThread();
		synchronized(this) {
			entryCount++;
			//Log.d(TAG, "(owner == currentThread) = " + (owner == currentThread));
			if (owner == currentThread) {
				//Log.d(TAG, "owner == currentThread");
				nests++;
			} else {
				//Log.d(TAG, "owner != currentThread");
				//Log.d(TAG, "(dontWait == 0) = " + (dontWait == 0));
				if (dontWait == 0) {
					try {
						waiting++;
						lastWaiter	= currentThread;
						if (waiting > 1) {
							// System.out.println("AEMonitor: " + name + " contended");
						}
						
						// we can get spurious wakeups (see Object javadoc) so we need to guard against
						// their possibility
						int	spuriousCount = 0;
						while (true) {
							//Log.d(TAG, "from = " + from);
							//Log.d(TAG, "wait() is called...");
							wait(0);
							//Log.d(TAG, "notified...");
							if (totalReserve == totalRelease) {
								spuriousCount++;
								if (spuriousCount > 1024) {
									waiting--;
									Debug.out("AEMonitor: spurious wakeup limit exceeded");
									throw (new Throwable("die die die"));
								} else {
									// Debug.out("AEMonitor: spurious wakeup, ignoring");
								}
							} else {
								break;
							}
						}
						totalReserve++;
					} catch (Throwable e) {
						// we know here that someone's got a finally clause to do the
						// balanced 'exit'. hence we should make it look as if we own it...
						waiting--;
						owner = currentThread;
						Debug.out("**** monitor interrupted ****");
						throw (new RuntimeException("AEMonitor:interrupted"));
					} finally {
						lastWaiter = null;
					}
				} else {
					totalReserve++;
					dontWait--;
				}
				owner = currentThread;
			}
		}
	}

	/*
	 * Try and obtain it
	 * @return true if got monitor, false otherwise
	 */
	public boolean enter(int maxMillis) {
		
		if (DEBUG) {
			debugEntry();
		}
		
		Thread current_thread = Thread.currentThread();
		synchronized(this) {
			entryCount++;
			if (owner == current_thread) {
				nests++;
			} else {
				if (dontWait == 0) {
					try {
						waiting++;
						lastWaiter	= current_thread;
						wait(maxMillis);
						if (totalReserve == totalRelease) {
							// failed to obtain it, so we need to mark ourselves as no
							// longer waiting
							waiting--;
							return (false);
						}
						totalReserve++;
					} catch (Throwable e) {
						// we know here that someone's got a finally clause to do the
						// balanced 'exit'. hence we should make it look as if we own it...
						waiting--;
						owner	= current_thread;
						Debug.out("**** monitor interrupted ****");
						throw (new RuntimeException("AEMonitor:interrupted"));
					} finally {
						lastWaiter = null;
					}
				} else {
					totalReserve++;
					dontWait--;
				}
				owner = current_thread;
			}
		}
		return (true);
	}

	public void exit() {
		try {
			synchronized(this) {
				if (nests > 0) {
					if (DEBUG) {
						if (owner != Thread.currentThread()) {
							Debug.out("nested exit but current thread not owner");
						}
					}
					nests--;
				} else {
					owner = null;
					totalRelease++;
					if (waiting != 0) {
						waiting--;
						notify();
					} else {
						dontWait++;
						if (dontWait > 1) {
							Debug.out("**** AEMonitor '" + name + "': multiple exit detected");
						}
					}
				}
			}
		} finally {
			if (DEBUG) {
				debugExit();
			}
		}
	}

	public boolean isHeld() {
		synchronized(this) {
			return (owner == Thread.currentThread());
		}
	}

	public boolean hasWaiters() {
		synchronized(this) {
			return (waiting > 0);
		}
	}

	public static Map getSynchronisedMap(Map m) {
		return (Collections.synchronizedMap(m));
	}
}