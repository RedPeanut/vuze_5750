/*
 * Created on 17 Jul 2006
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

import java.util.LinkedList;

public class AsyncDispatcher {
	
	private final String			name;
	private AEThread2				thread;
	private int						priority	= Thread.NORM_PRIORITY;
	private AERunnable				queueHead;
	private LinkedList<AERunnable>	queueTail;
	final AESemaphore				queueSem 	= new AESemaphore("AsyncDispatcher");

	private int						numPriority;

	final int quiesce_after_millis;

	public AsyncDispatcher() {
		this("AsyncDispatcher: " + Debug.getLastCallerShort(), 10000);
	}

	public AsyncDispatcher(String name) {
		this(name, 10000);
	}

	public AsyncDispatcher(int		quiesce_after_millis) {
		this("AsyncDispatcher: " + Debug.getLastCallerShort(), quiesce_after_millis);
	}

	public AsyncDispatcher(
		String		_name,
		int			_quiesce_after_millis) {
		name					= _name;
		quiesce_after_millis	= _quiesce_after_millis;
	}

	public void dispatch(
		AERunnable	target) {
		dispatch(target, false);
	}

	public void dispatch(
		AERunnable	target,
		boolean		is_priority) {
		
		synchronized(this) {
			if (queueHead == null) {
				queueHead = target;
				if (is_priority) {
					numPriority++;
				}
			} else {
				if (queueTail == null) {
					queueTail = new LinkedList<AERunnable>();
				}
				if (is_priority) {
					if (numPriority == 0) {
						queueTail.add(0, queueHead);
						queueHead = target;
					} else {
						queueTail.add(numPriority-1, target);
					}
					numPriority++;
				} else {
					queueTail.add(target);
				}
			}
			if (thread == null) {
				thread =
					new AEThread2(name, true) {
						public void run() {
							while (true) {
								queueSem.reserve(quiesce_after_millis);
								AERunnable	toRun = null;
								synchronized(AsyncDispatcher.this) {
									if (queueHead == null) {
										queueTail = null;
										thread = null;
										break;
									}
									toRun = queueHead;
									if (queueTail != null && !queueTail.isEmpty()) {
										queueHead = queueTail.removeFirst();
									} else {
										queueHead = null;
									}
									if (numPriority > 0) {
										numPriority--;
									}
								}
								try {
									toRun.runSupport();
								} catch (Throwable e) {
									Debug.printStackTrace(e);
								}
							}
						}
					};
				thread.setPriority(priority);
				thread.start();
			}
		}
		queueSem.release();
	}

	public boolean isQuiescent() {
		synchronized(this) {
			return (thread == null);
		}
	}

	public int getQueueSize() {
		synchronized(this) {

			int	result = queueHead == null?0:1;

			if (queueTail != null) {

				result += queueTail.size();
			}

			return (result);
		}
	}

	public void setPriority(
		int		p) {
		priority = p;
	}

	public boolean isDispatchThread() {
		synchronized(this) {

			return ( thread != null && thread.isCurrentThread());
		}
	}
}
