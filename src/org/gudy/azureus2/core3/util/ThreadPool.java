/*
 * File    : ThreadPool.java
 * Created : 21-Nov-2003
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

package org.gudy.azureus2.core3.util;

/**
 * @author parg
 *
 */

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.config.ParameterListener;


public class ThreadPool {
	private static final boolean	NAME_THREADS = Constants.IS_CVS_VERSION && System.getProperty("az.thread.pool.naming.enable", "true" ).equals("true");

	private static final boolean	LOG_WARNINGS	= false;
	private static final int		WARN_TIME		= 10000;

	static final List		busyPools			= new ArrayList();
	private static boolean	busy_pool_timer_set	= false;

	private static boolean	debug_thread_pool;
	private static boolean	debug_thread_pool_log_on;

	static {
		if (System.getProperty("transitory.startup", "0").equals("0")) {
			AEDiagnostics.addEvidenceGenerator(
				new AEDiagnosticsEvidenceGenerator() {
					public void generate(IndentWriter writer) {
						writer.println("Thread Pools");
						try {
							writer.indent();
							List	pools;
							synchronized(busyPools) {
								pools	= new ArrayList(busyPools);
							}
							for (int i=0;i<pools.size();i++) {
								((ThreadPool)pools.get(i)).generateEvidence(writer);
							}
						} finally {
							writer.exdent();
						}
					}
				}
			);
		}
	}

	static final ThreadLocal		tls	=
		new ThreadLocal() {
			public Object initialValue() {
				return (null);
			}
		};

	protected static void checkAllTimeouts() {
		List	pools;
		// copy the busy pools to avoid potential deadlock due to synchronization
		// nestings
		synchronized(busyPools) {
			pools = new ArrayList(busyPools);
		}
		for (int i=0;i<pools.size();i++) {
			((ThreadPool)pools.get(i)).checkTimeouts();
		}
	}


	final String		name;
	private final int	maxSize;
	private int			threadNameIndex	= 1;

	private long		executionLimit;

	final List				busy;
	private final boolean	queueWhenFull;
	final List<Runnable>	taskQueue	= new ArrayList<>();

	final AESemaphore		threadSemaphore;
	private int				reservedTarget;
	private int				reservedActual;

	private int				threadPriority	= Thread.NORM_PRIORITY;
	private boolean			warnWhenFull;

	private long			taskTotal;
	private long			taskTotalLast;
	private final Average	taskAverage	= Average.getInstance(WARN_TIME, 120);

	private boolean			logCpu	= AEThread2.TRACE_TIMES;

	public ThreadPool(
		String	_name,
		int		_maxSize) {
		this(_name, _maxSize, false);
	}

	public ThreadPool(
		String	_name,
		int		_maxSize,
		boolean	_queueWhenFull) {
		name			= _name;
		maxSize			= _maxSize;
		queueWhenFull	= _queueWhenFull;

		threadSemaphore = new AESemaphore("ThreadPool::" + name, _maxSize);

		busy		= new ArrayList(_maxSize);
	}

	private void generateEvidence(IndentWriter writer) {
		writer.println(name + ": max=" + maxSize +",qwf=" + queueWhenFull + ",queue=" + taskQueue.size() + ",busy=" + busy.size() + ",total=" + taskTotal + ":" + DisplayFormatters.formatDecimal(taskAverage.getDoubleAverage(),2) + "/sec");
	}

	public void setWarnWhenFull() {
		warnWhenFull	= true;
	}

	public void setLogCPU() {
		logCpu	= true;
	}

	public int getMaxThreads() {
		return (maxSize);
	}

	public void setThreadPriority(
		int	_priority) {
		threadPriority	= _priority;
	}

	public void setExecutionLimit(
		long		millis) {
		synchronized(this) {
			executionLimit	= millis;
		}
	}

	public ThreadPoolWorker run(AERunnable runnable) {
		return (run(runnable, false, false));
	}

	/**
	 *
	 * @param runnable
	 * @param highPriority
	 *            inserts at front if tasks queueing
	 */
	public ThreadPoolWorker run(AERunnable runnable, 
			boolean highPriority, 
			boolean manualRelease) {
		
		if (manualRelease && !(runnable instanceof ThreadPoolTask))
			throw new IllegalArgumentException("manual release only allowed for ThreadPoolTasks");
		else if (manualRelease)
			((ThreadPoolTask)runnable).setManualRelease();
		
		// System.out.println("Thread pool:" + name + " - sem = " + thread_sem.getValue() + ", queue = " + task_queue.size());
		
		// not queueing, grab synchronous sem here
		if (!queueWhenFull) {
			if (!threadSemaphore.reserveIfAvailable()) {
				// defend against recursive entry when in queuing mode (yes, it happens)
				ThreadPoolWorker recursiveWorker = (ThreadPoolWorker)tls.get();
				if (recursiveWorker == null || recursiveWorker.getOwner() != this) {
					// do a blocking reserve here, not recursive
					checkWarning();
					threadSemaphore.reserve();
				} else {
					// run immediately
					if (runnable instanceof ThreadPoolTask) {
						ThreadPoolTask task = (ThreadPoolTask)runnable;
						task.worker = recursiveWorker;
						try {
							task.taskStarted();
							runIt(runnable);
							task.join();
						} finally {
							task.taskCompleted();
						}
					} else {
						runIt(runnable);
					}
					return (recursiveWorker);
				}
			}
		}
		
		ThreadPoolWorker allocatedWorker;
		synchronized(this) {
			if (highPriority)
				taskQueue.add(0, runnable);
			else
				taskQueue.add(runnable);
			
			// reserve if available is non-blocking
			if (queueWhenFull && !threadSemaphore.reserveIfAvailable()) {
				allocatedWorker	= null;
				checkWarning();
			} else {
				allocatedWorker = new ThreadPoolWorker();
			}
		}
		return (allocatedWorker);
	}

	protected void runIt(AERunnable	runnable) {
		if (logCpu) {
			long	startCpu = logCpu?AEJavaManagement.getThreadCPUTime():0;
			long	startTime	= SystemTime.getHighPrecisionCounter();
			runnable.run();
			if (startCpu > 0) {
				long endCpu = logCpu?AEJavaManagement.getThreadCPUTime():0;
				long diffCpu = (endCpu - startCpu) / 1000000;
				long endTime = SystemTime.getHighPrecisionCounter();
				long diffMillis = (endTime - startTime) / 1000000;
				if (diffCpu > 10 || diffMillis > 10) {
					System.out.println(TimeFormatter.milliStamp() + ": Thread: " + Thread.currentThread().getName() + ": " + runnable + " -> " + diffCpu + "/" + diffMillis);
				}
			}
		} else {
			runnable.run();
		}
	}

	protected void checkWarning() {
		if (warnWhenFull) {
			String task_names = "";
			try {
				synchronized (ThreadPool.this) {
					for (int i = 0; i < busy.size(); i++) {
						ThreadPoolWorker x = (ThreadPoolWorker) busy.get(i);
						AERunnable r = x.runnable;
						if (r != null) {
							String name;
							if (r instanceof ThreadPoolTask)
								name = ((ThreadPoolTask) r).getName();
							else
								name = r.getClass().getName();
							task_names += (task_names.length() == 0 ? "" : ",") + name;
						}
					}
				}
			} catch (Throwable e) {}
			Debug.out("Thread pool '" + getName() + "' is full (busy=" + task_names + ")");
			warnWhenFull = false;
		}
	}

	public AERunnable[] getQueuedTasks() {
		synchronized (this) {
			AERunnable[] res = new AERunnable[taskQueue.size()];
			taskQueue.toArray(res);
			return (res);
		}
	}

	public int getQueueSize() {
		synchronized (this) {
			return taskQueue.size();
		}
	}

	public boolean isQueued(AERunnable task) {
		synchronized (this) {
			return taskQueue.contains(task);
		}
	}

	public AERunnable[] getRunningTasks() {
		List runnables = new ArrayList();
		synchronized(this) {
			Iterator	it = busy.iterator();
			while (it.hasNext()) {
				ThreadPoolWorker	worker = (ThreadPoolWorker)it.next();
				AERunnable	runnable = worker.getRunnable();
				if (runnable != null) {
					runnables.add(runnable);
				}
			}
		}
		AERunnable[] res = new AERunnable[runnables.size()];
		runnables.toArray(res);
		return (res);
	}

	public int getRunningCount() {
  		int	res = 0;
  		synchronized(this) {
  			Iterator	it = busy.iterator();
  			while (it.hasNext()) {
  				ThreadPoolWorker	worker = (ThreadPoolWorker)it.next();
  				AERunnable	runnable = worker.getRunnable();
  				if (runnable != null) {
  					res++;
  				}
  			}
  		}
  		return (res);
  	}

	public boolean isFull() {
		return (threadSemaphore.getValue() == 0);
	}

	public void setMaxThreads(int max) {
		if (max > maxSize) {
			Debug.out("should support this sometime...");
			return;
		}
		setReservedThreadCount(maxSize - max);
	}

	public void setReservedThreadCount(int res) {
		
		synchronized(this) {
			if (res < 0) {
				res = 0;
			} else if (res > maxSize) {
				res = maxSize;
			}
			int	 diff =  res - reservedActual;
			while (diff < 0) {
				threadSemaphore.release();
				reservedActual--;
				diff++;
			}
			while (diff > 0) {
				if (threadSemaphore.reserveIfAvailable()) {
					reservedActual++;
					diff--;
				} else {
					break;
				}
			}
			reservedTarget = res;
		}
	}

	protected void checkTimeouts() {
		synchronized(this) {
			long	diff = taskTotal - taskTotalLast;
			taskAverage.addValue(diff);
			taskTotalLast = taskTotal;
			if (debug_thread_pool_log_on) {
				System.out.println("ThreadPool '" + getName() + "'/" + threadNameIndex + ": max=" + maxSize + ",sem=[" + threadSemaphore.getString() + "],busy=" + busy.size() + ",queue=" + taskQueue.size());
			}
			long	now = SystemTime.getMonotonousTime();
			for (int i=0;i<busy.size();i++) {
				ThreadPoolWorker	x = (ThreadPoolWorker)busy.get(i);
				long	elapsed = now - x.runStartTime;
				if (elapsed > ( (long)WARN_TIME * (x.warnCount+1))) {
					x.warnCount++;
					if (LOG_WARNINGS) {
						DebugLight.out(x.getWorkerName() + ": running, elapsed = " + elapsed + ", state = " + x.state);
					}
					if (executionLimit > 0 && elapsed > executionLimit) {
						if (LOG_WARNINGS) {
							DebugLight.out(x.getWorkerName() + ": interrupting");
						}
						AERunnable r = x.runnable;
						if (r != null) {
							try {
								if (r instanceof ThreadPoolTask) {
									((ThreadPoolTask)r).interruptTask();
								} else {
									x.interrupt();
								}
							} catch (Throwable e) {
								DebugLight.printStackTrace(e);
							}
						}
					}
				}
			}
		}
	}

	public String getName() {
		return (name);
	}

	void releaseManual(ThreadPoolTask toRelease) {
		if (!toRelease.canManualRelease()) {
			throw new IllegalStateException("task not manually releasable");
		}
		synchronized(this) {
			long elapsed = SystemTime.getMonotonousTime() - toRelease.worker.runStartTime;
			if (elapsed > WARN_TIME && LOG_WARNINGS)
				DebugLight.out(toRelease.worker.getWorkerName() + ": terminated, elapsed = " + elapsed + ", state = " + toRelease.worker.state);
			if (!busy.remove(toRelease.worker)) {
				throw new IllegalStateException("task already released");
			}
			// if debug is on we leave the pool registered so that we
			// can trace on the timeout events
			if (busy.size() == 0 && !debug_thread_pool) {
				synchronized (busyPools) {
					busyPools.remove(this);
				}
			}
			if (busy.size() == 0) {
				if (reservedTarget > reservedActual) {
					reservedActual++;
				} else {
					threadSemaphore.release();
				}
			} else {
				new ThreadPoolWorker();
			}
		}
	}

	public void registerThreadAsChild(ThreadPoolWorker parent) {
		if (tls.get() == null || tls.get() == parent)
			tls.set(parent);
		else
			throw new IllegalStateException("another parent is already set for this thread");
	}

	public void deregisterThreadAsChild(ThreadPoolWorker parent) {
		if (tls.get() == parent)
			tls.set(null);
		else
			throw new IllegalStateException("tls is not set to parent");
	}


	class ThreadPoolWorker extends AEThread2 {
		
		private final String		workerName;
		private volatile AERunnable	runnable;
		private long				runStartTime;
		private int					warnCount;
		private String				state = "<none>";

		protected ThreadPoolWorker() {
			super(NAME_THREADS?(name + " " + (threadNameIndex)):name,true);
			threadNameIndex++;
			setPriority(threadPriority);
			workerName = this.getName();
			start();
		}

		public void run() {
			tls.set(ThreadPoolWorker.this);

			boolean autoRelease = true;

			try {
				do {
					try {
						synchronized (ThreadPool.this) {
							if (taskQueue.size() > 0)
								runnable = (AERunnable) taskQueue.remove(0);
							else
								break;
						}

						synchronized (ThreadPool.this) {
							runStartTime = SystemTime.getMonotonousTime();
							warnCount = 0;
							busy.add(ThreadPoolWorker.this);
							taskTotal++;
							if (busy.size() == 1) {
								synchronized (busyPools) {
									if (!busyPools.contains(ThreadPool.this)) {
										busyPools.add(ThreadPool.this);
										if (!busy_pool_timer_set) {
											// we have to defer this action rather
											// than running as a static initialiser
											// due to the dependency between
											// ThreadPool, Timer and ThreadPool again
											COConfigurationManager.addAndFireParameterListeners(new String[] { "debug.threadpool.log.enable", "debug.threadpool.debug.trace" }, new ParameterListener() {
												public void parameterChanged(String name) {
													debug_thread_pool = COConfigurationManager.getBooleanParameter("debug.threadpool.log.enable", false);
													debug_thread_pool_log_on = COConfigurationManager.getBooleanParameter("debug.threadpool.debug.trace", false);
												}
											});
											busy_pool_timer_set = true;
											SimpleTimer.addPeriodicEvent("ThreadPool:timeout", WARN_TIME, new TimerEventPerformer() {
												public void perform(TimerEvent event) {
													checkAllTimeouts();
												}
											});
										}
									}
								}
							}
						}

						if (runnable instanceof ThreadPoolTask) {
							ThreadPoolTask tpt = (ThreadPoolTask) runnable;
							tpt.worker = this;
							String task_name = NAME_THREADS?tpt.getName():null;
							try {
								if (task_name != null)
									setName(workerName + "{" + task_name + "}");
								tpt.taskStarted();
								runIt(runnable);
							} finally {
								if (task_name != null)
									setName(workerName);

								if (tpt.isAutoReleaseAndAllowManual())
									tpt.taskCompleted();
								else {
									autoRelease = false;
									break;
								}

							}
						} else
							runIt(runnable);

					} catch (Throwable e) {
						DebugLight.printStackTrace(e);
					} finally {
						if (autoRelease) {
							synchronized (ThreadPool.this) {
								long elapsed = SystemTime.getMonotonousTime() - runStartTime;
								if (elapsed > WARN_TIME && LOG_WARNINGS)
									DebugLight.out(getWorkerName() + ": terminated, elapsed = " + elapsed + ", state = " + state);

								busy.remove(ThreadPoolWorker.this);

								// if debug is on we leave the pool registered so that we
								// can trace on the timeout events
								if (busy.size() == 0 && !debug_thread_pool)
									synchronized (busyPools) {
										busyPools.remove(ThreadPool.this);
									}
							}
						}
					}
				} while (runnable != null);
			} catch (Throwable e) {
				DebugLight.printStackTrace(e);
			} finally {
				if (autoRelease) {
					synchronized (ThreadPool.this) {
						if (reservedTarget > reservedActual) {
							reservedActual++;
						} else {
							threadSemaphore.release();
						}
					}
				}
				tls.set(null);
			}
		}

		public void setState(String _state) {
			//System.out.println("state = " + _state);
			state = _state;
		}

		public String getState() {
			return (state);
		}

		protected String getWorkerName() {
			return (workerName);
		}

		protected ThreadPool getOwner() {
			return (ThreadPool.this);
		}

		protected AERunnable getRunnable() {
			return (runnable);
		}
	}
}
