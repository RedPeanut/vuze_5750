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
public class AESemaphore extends AEMonSem {
	
	private int		dontWait	= 0;

	private int		totalReserve	= 0;
	private int		totalRelease	= 0;

	private boolean	releasedForever	= false;

	protected Thread	latestWaiter;

	public AESemaphore(String _name) {
		this(_name, 0);
	}

	public AESemaphore(
		String		_name,
		int			count) {
		super(_name, false);
		dontWait		= count;
		totalRelease	= count;
	}

	public void reserve() {
		if (!reserve(0)) {
			Debug.out("AESemaphore: reserve completed without acquire [" + getString() + "]");
		}
	}

	public boolean reserve(long	millis) {
		return (reserveSupport(millis, 1) == 1);
	}

	public boolean reserveIfAvailable() {
		synchronized(this) {
			if (releasedForever || dontWait > 0) {
				reserve();
				return (true);
			} else {
				return (false);
			}
		}
	}

	public int reserveSet(
		int		max_to_reserve,
		long	millis) {
		return (reserveSupport(millis, max_to_reserve));
	}

	public int reserveSet(int max_to_reserve) {
		return (reserveSupport(0, max_to_reserve));
	}

	protected int reserveSupport(
		long	millis,
		int		maxToReserve) {
		
		if (DEBUG) {
			super.debugEntry();
		}
		
		synchronized(this) {
			entryCount++;
			//System.out.println( name + "::reserve");
			if (releasedForever) {
				return (1);
			}
			if (dontWait == 0) {
				try {
					waiting++;
					latestWaiter	= Thread.currentThread();
					if (waiting > 1) {
						// System.out.println("AESemaphore: " + name + " contended");
					}
					
					if (millis == 0) {
						// we can get spurious wakeups (see Object javadoc) so we need to guard against
						// their possibility
						int	spurious_count	= 0;
						while (true) {
							wait();
							if (totalReserve == totalRelease) {
								spurious_count++;
								if (spurious_count > 1024) {
									Debug.out("AESemaphore: spurious wakeup limit exceeded");
									throw (new Throwable("die die die"));
								} else {
									// Debug.out("AESemaphore: spurious wakeup, ignoring");
								}
							} else {
								break;
							}
						}
					} else {
						// we don't hugely care about spurious wakeups here, it'll just appear
						// as a failed reservation a bit early
						wait(millis);
					}
					if (totalReserve == totalRelease) {
						// here we have timed out on the wait without acquiring
						waiting--;
						return (0);
					}
					totalReserve++;
					return (1);
				} catch (Throwable e) {
					waiting--;
					Debug.out("**** semaphore operation interrupted ****");
					throw (new RuntimeException("Semaphore: operation interrupted", e));
				} finally {
					latestWaiter = null;
				}
			} else {
				int	numToGet = maxToReserve>dontWait?dontWait:maxToReserve;
				dontWait -= numToGet;
				totalReserve += numToGet;
				return (numToGet);
			}
		}
	}

	public void	release() {
		try {
			synchronized(this) {
				//System.out.println( name + "::release");
				totalRelease++;
				if (waiting != 0) {
					waiting--;
					notify();
				} else {
					dontWait++;
				}
			}
		} finally {
			if (DEBUG) {
				debugExit();
			}
		}
	}

	public void releaseAllWaiters() {
		synchronized(this) {
			int	x	= waiting;
			for (int i=0;i<x;i++) {
				release();
			}
		}
	}

	public void releaseForever() {
		synchronized(this) {
			releaseAllWaiters();
			releasedForever	= true;
		}
	}

	public boolean isReleasedForever() {
		synchronized(this) {
			return (releasedForever);
		}
	}

	public int getValue() {
		synchronized(this) {
			return (dontWait - waiting);
		}
	}

	public String getString() {
		synchronized(this) {
			return ("value=" + dontWait + ",waiting=" + waiting + ",res=" + totalReserve + ",rel=" + totalRelease);
		}
	}
}
