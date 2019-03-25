/*
 * Created on Nov 9, 2007
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


public abstract class AEThread2 {
	
	public static final boolean TRACE_TIMES = false;

	private static final int MIN_RETAINED	= Math.max(Runtime.getRuntime().availableProcessors(),2);
	private static final int MAX_RETAINED	= Math.max(MIN_RETAINED*4, 16);

	private static final int THREAD_TIMEOUT_CHECK_PERIOD	= 10*1000;
	private static final int THREAD_TIMEOUT					= 60*1000;

	private static final LinkedList	daemonThreads = new LinkedList();

	private static final class JoinLock {
		volatile boolean released = false;
	}

	private static long	last_timeout_check;

	private static long	totalStarts;
	private static long	totalCreates;


	private ThreadWrapper	wrapper;

	private String				name;
	private final boolean		daemon;
	private int					priority	= Thread.NORM_PRIORITY;
	private volatile JoinLock	lock		= new JoinLock();

	public AEThread2(
		String		_name) {
		this(_name, true);
	}

	public AEThread2(
		String		_name,
		boolean		_daemon) {
		name		= _name;
		daemon		= _daemon;
	}

	/**
	 * multiple invocations of start() are possible, but discouraged if combined
	 * with other thread operations such as interrupt() or join()
	 */
	public void	start() {
		JoinLock currentLock = lock;
		JoinLock newLock;
		synchronized (currentLock) {
			// create new lock in case this is a restart, all old .join()s will be locked on the old thread and thus released by the old thread
			if (currentLock.released)
				newLock = lock = new JoinLock();
			else
				newLock = currentLock;
		}
		
		if (daemon) {
			synchronized(daemonThreads) {
				totalStarts++;
				if (daemonThreads.isEmpty()) {
					totalCreates++;
					wrapper = new ThreadWrapper(name, true);
				} else {
					wrapper = (ThreadWrapper)daemonThreads.removeLast();
					wrapper.setName(name);
				}
			}
		} else {
			wrapper = new ThreadWrapper(name, false);
		}
		if (priority != wrapper.getPriority()) {
			wrapper.setPriority(priority);
		}
		wrapper.currentLock = newLock;
		wrapper.start(this, name);
	}

	public void setPriority(int _priority) {
		priority	= _priority;

		if (wrapper != null) {
			wrapper.setPriority(priority);
		}
	}

	public void setName(String	s) {
		name	= s;
		if (wrapper != null) {
			wrapper.setName(name);
		}
	}

	public String getName() {
		return (name);
	}

	public void interrupt() {
		if (wrapper == null) {
			throw new IllegalStateException("Interrupted before started!");
		} else {
			wrapper.interrupt();
		}
	}

	public boolean isAlive() {
		return wrapper == null ? false : wrapper.isAlive();
	}

	public boolean isCurrentThread() {
		return ( wrapper == Thread.currentThread());
	}

	public String toString() {
		if (wrapper == null) {
			return (name + " [daemon=" + daemon + ",priority=" + priority + "]");
		} else {
			return ( wrapper.toString());
		}
	}

	public abstract void run();

	public static boolean isOurThread(Thread	thread) {
		return (AEThread.isOurThread( thread));
	}

	public static void setOurThread() {
		AEThread.setOurThread();
	}

	public static void setOurThread(
		Thread	thread) {
		AEThread.setOurThread(thread);
	}

	public static void setDebug(
		Object		debug) {
		Thread current = Thread.currentThread();
		if (current instanceof ThreadWrapper) {
			((ThreadWrapper)current).setDebug(debug);
		}
	}

	/**
	 * entry 0 is debug object, 1 is Long mono-time it was set
	 * @param t
	 * @return
	 */
	public static Object[] getDebug(Thread t) {
		if (t instanceof ThreadWrapper) {
			return (((ThreadWrapper)t).getDebug());
		}
		return (null);
	}

	protected static class ThreadWrapper extends Thread {
		private AESemaphore2	sem;
		private AEThread2		target;
		private JoinLock		currentLock;

		private long		last_active_time;

		private Object[]		debug;

		protected ThreadWrapper(
			String		name,
			boolean		daemon) {
			super(name);
			setDaemon(daemon);
		}

		public void run() {
			while (true) {
				synchronized(currentLock) {
					try {
						if (TRACE_TIMES) {
							long 	start_time 	= SystemTime.getHighPrecisionCounter();
							long	start_cpu 	= AEJavaManagement.getThreadCPUTime();
							try {
								target.run();
							} finally {
								long	time_diff 	= (SystemTime.getHighPrecisionCounter() - start_time)/1000000;
								long	cpu_diff	= (AEJavaManagement.getThreadCPUTime() - start_cpu) / 1000000;
								if (cpu_diff > 10 || time_diff > 10) {
									System.out.println(TimeFormatter.milliStamp() + ": Thread: " + target.getName() + ": " + cpu_diff + "/" + time_diff);
								}
							}
						} else {
							target.run();
						}
					} catch (Throwable e) {
						DebugLight.printStackTrace(e);
					} finally {
						target = null;
						debug	= null;
						currentLock.released = true;
						currentLock.notifyAll();
					}
				}
				
				if (isInterrupted() || !Thread.currentThread().isDaemon()) {
					break;
				} else {
					synchronized(daemonThreads) {
						last_active_time	= SystemTime.getCurrentTime();
						if (	last_active_time < last_timeout_check ||
								last_active_time - last_timeout_check > THREAD_TIMEOUT_CHECK_PERIOD) {
							last_timeout_check	= last_active_time;
							while (daemonThreads.size() > 0 && daemonThreads.size() > MIN_RETAINED) {
								ThreadWrapper thread = (ThreadWrapper)daemonThreads.getFirst();
								long	thread_time = thread.last_active_time;
								if (	last_active_time < thread_time ||
										last_active_time - thread_time > THREAD_TIMEOUT) {
									daemonThreads.removeFirst();
									thread.retire();
								} else {
									break;
								}
							}
						}
						if (daemonThreads.size() >= MAX_RETAINED) {
							return;
						}
						daemonThreads.addLast(this);
						setName("AEThread2:parked[" + daemonThreads.size() + "]");
						// System.out.println("AEThread2: queue=" + daemon_threads.size() + ",creates=" + total_creates + ",starts=" + total_starts);
					}
					sem.reserve();
					if (target == null) {
						break;
					}
				}
			}
		}

		protected void start(
			AEThread2	_target,
			String		_name) {
			target	= _target;
			setName(_name);
			if (sem == null) {
				 sem = new AESemaphore2("AEThread2");
				 super.start();
			} else {
				sem.release();
			}
		}

		protected void retire() {
			sem.release();
		}

		protected void setDebug(Object	d) {
			debug	= new Object[]{ d, SystemTime.getMonotonousTime() };
		}

		protected Object[] getDebug() {
			return (debug);
		}
	}

	public void join() {
		JoinLock currentLock = lock;
		// sync lock will be blocked by the thread
		synchronized(currentLock) {
			// wait in case the thread is not running yet
			while (!currentLock.released) {
				try {
					currentLock.wait();
				} catch (InterruptedException e) {
				}
			}
		}
	}
}
