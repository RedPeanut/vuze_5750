/*
 * Created on Apr 16, 2004 Created by Alon Rohter
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version. This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details. You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software Foundation, Inc.,
 * 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */
package org.gudy.azureus2.core3.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import hello.util.Log;

/**
 * Utility class to retrieve current system time, and catch clock backward time
 * changes.
 */
public class SystemTime {
	
	private static String TAG = SystemTime.class.getSimpleName();
	
	public static final long	TIME_GRANULARITY_MILLIS	= 25;	//internal update time ms

	private static final int	STEPS_PER_SECOND	= (int) (1000 / TIME_GRANULARITY_MILLIS);

	private static SystemTimeProvider	instance;

	// can't do that without some safeguarding code.
	// monotime does guarantee that time neither goes backwards nor performs leaps into the future.
	// the HPC doesn't jump backward but can jump forward in time
	private static final boolean		SOD_IT_LETS_USE_HPC = false;//	= Constants.isCVSVersion();

	private static volatile List<TickConsumer>			systemTimeConsumers		= new ArrayList<TickConsumer>();
	private static volatile List<TickConsumer>			monotoneTimeConsumers	= new ArrayList<TickConsumer>();
	private static volatile List<ChangeListener>		clockChangeList			= new ArrayList<ChangeListener>();
	//private static long					hpcBaseTime;
	//private static long					hpcLastTime;
	//private static boolean				noHcpLogged;

	static {
		try {
			if (System.getProperty("azureus.time.use.raw.provider", "0").equals("1")) {
				Log.d(TAG, "Using Raw Provider...");
				System.out.println("Warning: Using Raw Provider");
				instance = new RawProvider();
			} else {
				Log.d(TAG, "Using Stepped Provider");
				instance = new SteppedProvider();
			}
		} catch (Throwable e) {
			// might be in applet...
			instance = new SteppedProvider();
		}
	}

	public static void useRawProvider() {
		if (!(instance instanceof RawProvider)) {
			Debug.out("Whoa, someone already created a non-raw provider!");

			instance = new RawProvider();
		}
	}

	protected interface SystemTimeProvider {
		public long getTime();

		public long getMonoTime();

		public long getSteppedMonoTime();
	}

	private static class SteppedProvider implements SystemTimeProvider {
		private static final long	HPC_START = getHighPrecisionCounter()/1000000L;

		private final Thread		updater;
		private volatile long		steppedTime;
		private volatile long		currentTimeOffset = System.currentTimeMillis();
		private final AtomicLong 	lastApproximateTime = new AtomicLong();
		//private volatile long		lastApproximateTime;
		private volatile int		accessCount;
		private volatile int		sliceAccessCount;
		private volatile int		accessAveragePerSlice;
		private volatile int		driftAdjustedGranularity;

		private volatile long		steppedMonoTime;

		private SteppedProvider() {
			// System.out.println("SystemTime: using stepped time provider");

			steppedTime = 0;

			updater = new Thread("SystemTime") {
				public void run() {
					long adjustedTimeOffset = currentTimeOffset;
					// these averages rely on monotone time, thus won't be affected by system time changes
					final Average access_average = Average.getInstance(1000, 10);
					final Average drift_average = Average.getInstance(1000, 10);
					long lastOffset = adjustedTimeOffset;
					long lastSecond = -1000;
					int tick_count = 0;
					while (true) {
						final long rawTime = System.currentTimeMillis();
						/*
						 * keep the monotone time in sync with the raw system
						 * time, for this we need to know the offset of the
						 * current time to the system time
						 */
						long newMonotoneTime = rawTime - adjustedTimeOffset;
						long delta = newMonotoneTime - steppedTime;
						/*
						 * unless the system time jumps, then we just guess the
						 * time that has passed and adjust the update, so that
						 * the next round can be in sync with the system time
						 * again
						 */
						if (delta < 0 || delta > 1000) {
							/*
							 * jump occured, update monotone time offset, but
							 * not the current time one, that only happens every
							 * second
							 */
							steppedTime += TIME_GRANULARITY_MILLIS;
							adjustedTimeOffset = rawTime - steppedTime;
						} else
						{ // time is good, keep it
							steppedTime = newMonotoneTime;
						}
						tick_count++;

						long change;

						if (tick_count == STEPS_PER_SECOND) {

							change = adjustedTimeOffset - lastOffset;

							if (change != 0) {

								Iterator<ChangeListener> it = clockChangeList.iterator();
								//Debug.outNoStack("Clock change of " + change + " ms detected, raw=" + rawTime);
								while (it.hasNext()) {

									try {
										it.next().clockChangeDetected(rawTime, change);

									} catch (Throwable e) {

										Debug.out(e);
									}
								}
								lastOffset = adjustedTimeOffset;

								currentTimeOffset = adjustedTimeOffset;
							}
							// averaging magic to estimate the amount of time that passes between each getTime invocation
							long drift = steppedTime - lastSecond - 1000;
							lastSecond = steppedTime;
							drift_average.addValue(drift);
							driftAdjustedGranularity = (int) (TIME_GRANULARITY_MILLIS + (drift_average.getAverage() / STEPS_PER_SECOND));
							access_average.addValue(accessCount);
							accessAveragePerSlice = (int) (access_average.getAverage() / STEPS_PER_SECOND);
							//System.out.println("access count = " + access_count + ", average = " + access_average.getAverage() + ", per slice = " + access_average_per_slice + ", drift = " + drift +", average = " + drift_average.getAverage() + ", dag =" + drift_adjusted_granularity);
							accessCount = 0;
							tick_count = 0;
						} else {
							change = 0;
						}

						sliceAccessCount = 0;

						steppedMonoTime = steppedTime;

						long adjustedTime = steppedTime + currentTimeOffset;

						if (change != 0) {
							Iterator<ChangeListener> it = clockChangeList.iterator();
							//Debug.outNoStack("Clock change of " + change + " ms completed, curr=" + adjustedTime);
							while (it.hasNext()) {

								try {
									it.next().clockChangeCompleted(adjustedTime, change);
								} catch (Throwable e) {
									Debug.out(e);
								}
							}
						}

						// copy reference since we use unsynced COW semantics
						List<TickConsumer> consumersRef = monotoneTimeConsumers;
						for (int i = 0; i < consumersRef.size(); i++) {
							TickConsumer cons = consumersRef.get(i);
							try {
								cons.consume(steppedTime);
							} catch (Throwable e) {
								Debug.printStackTrace(e);
							}
						}

						/*
						 * notify consumers with the external offset, internal
						 * offset is only meant for updates
						 */
						consumersRef = systemTimeConsumers;

						for (int i = 0; i < consumersRef.size(); i++) {
							TickConsumer cons = consumersRef.get(i);
							try {
								cons.consume(adjustedTime);
							} catch (Throwable e) {
								Debug.printStackTrace(e);
							}
						}

						try {
							Thread.sleep(TIME_GRANULARITY_MILLIS);
						} catch (Exception e) {
							Debug.printStackTrace(e);
						}
					}
				}
			};
			updater.setDaemon(true);
			// we don't want this thread to lag much as it'll stuff up the upload/download rate mechanisms (for example)
			updater.setPriority(Thread.MAX_PRIORITY);
			updater.start();
		}

		public long getTime() {
			return getMonoTime() + currentTimeOffset;
		}

		public long getMonoTime() {
			if (SOD_IT_LETS_USE_HPC) {
				return ((getHighPrecisionCounter()/1000000) - HPC_START);
			} else {
				long adjusted_time;
				long averageSliceStep = accessAveragePerSlice;
				if (averageSliceStep > 0) {
					long sliceStep = (driftAdjustedGranularity * sliceAccessCount) / averageSliceStep;
					if (sliceStep >= driftAdjustedGranularity) {
						sliceStep = driftAdjustedGranularity - 1;
					}
					adjusted_time = sliceStep + steppedTime;
				} else
					adjusted_time = steppedTime;
				accessCount++;
				sliceAccessCount++;

				// make sure we don't go backwards and our reference value for going backwards doesn't go backwards either
				long approxBuffered = lastApproximateTime.get();
				if (adjusted_time < approxBuffered)
					adjusted_time = approxBuffered;
				else
					lastApproximateTime.compareAndSet(approxBuffered, adjusted_time);

				return adjusted_time;
			}
		}

		public long getSteppedMonoTime() {
			if (SOD_IT_LETS_USE_HPC) {
				return (getHighPrecisionCounter()/1000000);
			} else {
				return (steppedMonoTime);
			}
		}
	}

	private static class RawProvider implements SystemTimeProvider {
		//private static final int	STEPS_PER_SECOND	= (int) (1000 / TIME_GRANULARITY_MILLIS);
		private final Thread		updater;

		private RawProvider() {
			System.out.println("SystemTime: using raw time provider");

			updater = new Thread("SystemTime") {
				long	last_time;

				public void run() {
					while (true) {
						long current_time = getTime();
						long change;

						if (last_time != 0) {

							long offset = current_time - last_time;

							if (offset < 0 || offset > 5000) {

								change = offset;

									// clock's changed

								Iterator<ChangeListener> it = clockChangeList.iterator();

								while (it.hasNext()) {

									try {
										it.next().clockChangeDetected(current_time, change);
									} catch (Throwable e) {

										Debug.out(e);
									}
								}
							} else {
								change = 0;
							}
						} else {
							change = 0;
						}

						last_time = current_time;

						if (change != 0) {
							Iterator<ChangeListener> it = clockChangeList.iterator();
							while (it.hasNext()) {

								try {
									it.next().clockChangeCompleted(current_time, change);

								} catch (Throwable e) {

									Debug.out(e);
								}
							}
						}

						List consumerList_ref = systemTimeConsumers;

						for (int i = 0; i < consumerList_ref.size(); i++) {
							TickConsumer cons = (TickConsumer) consumerList_ref.get(i);
							try {
								cons.consume(current_time);
							} catch (Throwable e) {
								Debug.printStackTrace(e);
							}
						}
						consumerList_ref = monotoneTimeConsumers;

						long	mono_time = getMonoTime();

						for (int i = 0; i < consumerList_ref.size(); i++) {
							TickConsumer cons = (TickConsumer) consumerList_ref.get(i);
							try {
								cons.consume(mono_time);
							} catch (Throwable e) {
								Debug.printStackTrace(e);
							}
						}

						try {
							Thread.sleep(TIME_GRANULARITY_MILLIS);
						} catch (Exception e) {
							Debug.printStackTrace(e);
						}
					}
				}
			};
			updater.setDaemon(true);
			// we don't want this thread to lag much as it'll stuff up the upload/download rate mechanisms (for example)
			updater.setPriority(Thread.MAX_PRIORITY);
			updater.start();
		}

		public long getTime() {
			return System.currentTimeMillis();
		}

		/**
		 * This implementation does not guarantee monotonous time increases with
		 * 100% accuracy as the adjustedTimeOffset is only adjusted every
		 * TIME_GRANULARITY_MILLIS
		 */
		public long getMonoTime() {
			return getHighPrecisionCounter()/1000000;
		}

		public long getSteppedMonoTime() {
			return getMonoTime();
		}
	}

	/**
	 * Note that this can this time can jump into the future or past due to
	 * clock adjustments use getMonotonousTime() if you need steady increases
	 *
	 * @return current system time in millisecond since epoch
	 */
	public static long getCurrentTime() {
		return (instance.getTime());
	}

	/**
	 * Time that is guaranteed to grow monotonously and also ignores larger
	 * jumps into the future which might be caused by adjusting the system clock<br>
	 * <br>
	 *
	 * <b>Do not mix times retrieved by this method with normal time!</b>
	 *
	 * @return the amount of real time passed since the program start in
	 *         milliseconds
	 */
	public static long getMonotonousTime() {
		return instance.getMonoTime();
	}

		/**
		 * Like getMonotonousTime but only updated at TIME_GRANULARITY_MILLIS intervals (not interpolated)
		 * As such it is likely to be cheaper to obtain
		 * @return
		 */

	public static long getSteppedMonotonousTime() {
		return instance.getSteppedMonoTime();
	}


	public static long getOffsetTime(long offsetMS) {
		return instance.getTime() + offsetMS;
	}

	public static void registerConsumer(TickConsumer c) {
		synchronized (instance) {
			List newList = new ArrayList(systemTimeConsumers);
			newList.add(c);
			systemTimeConsumers = newList;
		}
	}

	public static void unregisterConsumer(TickConsumer c) {
		synchronized (instance) {
			List new_list = new ArrayList(systemTimeConsumers);
			new_list.remove(c);
			systemTimeConsumers = new_list;
		}
	}

	public static void registerMonotonousConsumer(TickConsumer c) {
		synchronized (instance) {
			List new_list = new ArrayList(monotoneTimeConsumers);
			new_list.add(c);
			monotoneTimeConsumers = new_list;
		}
	}

	public static void unregisterMonotonousConsumer(TickConsumer c) {
		synchronized (instance) {
			List new_list = new ArrayList(monotoneTimeConsumers);
			new_list.remove(c);
			monotoneTimeConsumers = new_list;
		}
	}

	public static void registerClockChangeListener(ChangeListener c) {
		synchronized (instance) {
			List new_list = new ArrayList(clockChangeList);
			new_list.add(c);
			clockChangeList = new_list;
		}
	}

	public static void unregisterClockChangeListener(ChangeListener c) {
		synchronized (instance) {
			List new_list = new ArrayList(clockChangeList);
			new_list.remove(c);
			clockChangeList = new_list;
		}
	}

	public interface TickConsumer {
		public void consume(long current_time);
	}

	public interface ChangeListener {
		/**
		 * Called before the change becomes visible to getCurrentTime callers
		 * @param current_time
		 * @param change_millis
		 */
		public void clockChangeDetected(long current_time, long change_millis);
		/**
		 * Called after the change is visible to getCurrentTime callers
		 * @param current_time
		 * @param change_millis
		 */
		public void clockChangeCompleted(long current_time, long change_millis);
	}

	public static long getHighPrecisionCounter() {
		return (System.nanoTime());
	}

	public static void main(String[] args) {
		for (int i = 0; i < 1; i++) {
			//final int f_i = i;
			new Thread() {
				public void run() {
					/*
					 * Average access_average = Average.getInstance(1000, 10);
					 *
					 * long last = SystemTime.getCurrentTime();
					 *
					 * int count = 0;
					 *
					 * while (true) {
					 *
					 * long now = SystemTime.getCurrentTime();
					 *
					 * long diff = now - last;
					 *
					 * System.out.println("diff=" + diff);
					 *
					 * last = now;
					 *
					 * access_average.addValue(diff);
					 *
					 * count++;
					 *
					 * if (count == 33) {
					 *
					 * System.out.println("AVERAGE " + f_i + " = " +
					 * access_average.getAverage());
					 *
					 * count = 0; }
					 *
					 * try { Thread.sleep(3);
					 *
					 * } catch (Throwable e) { } }
					 */
					long cstart = SystemTime.getCurrentTime();
					long mstart = SystemTime.getMonotonousTime();
					System.out.println("alter system clock to see differences between monotonous and current time");
					long cLastRound = cstart;
					long mLastRound = mstart;
					while (true) {
						long mnow = SystemTime.getMonotonousTime();
						long cnow = SystemTime.getCurrentTime();
						//if (mLastRound > mnow)
						System.out.println("current: " + (cnow - cstart) + " monotonous:" + (mnow - mstart) + " delta current:" + (cnow - cLastRound) + " delta monotonous:" + (mnow - mLastRound));
						cLastRound = cnow;
						mLastRound = mnow;
						try {
							Thread.sleep(15);
						} catch (Throwable e) {}
					}
				}
			}.start();
		}
	}
}
