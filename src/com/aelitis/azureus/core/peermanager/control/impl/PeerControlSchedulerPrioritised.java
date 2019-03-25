/*
 * Created on Oct 23, 2007
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


package com.aelitis.azureus.core.peermanager.control.impl;

import java.util.*;

import org.gudy.azureus2.core3.util.AEMonitor;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.SystemTime;

import com.aelitis.azureus.core.peermanager.control.PeerControlInstance;
import com.aelitis.azureus.core.peermanager.control.SpeedTokenDispenser;
import com.aelitis.azureus.core.stats.AzureusCoreStatsProvider;

import hello.util.Log;
import hello.util.SingleCounter0;

public class PeerControlSchedulerPrioritised
	extends PeerControlSchedulerImpl
	implements AzureusCoreStatsProvider {

	private static String TAG = PeerControlSchedulerPrioritised.class.getSimpleName();
	
	private Map	instanceMap = new HashMap();
	final List	pendingRegistrations = new ArrayList();
	private volatile boolean	registrationsChanged;
	private volatile long		latestTime;
	protected final AEMonitor	thisMon = new AEMonitor("PeerControlSchedulerPrioritised");

	private final SpeedTokenDispenserPrioritised tokenDispenser = new SpeedTokenDispenserPrioritised();

	protected void schedule() {
		latestTime	= SystemTime.getMonotonousTime();
		SystemTime.registerMonotonousConsumer(
			new SystemTime.TickConsumer() {
				public void consume(long	time) {
					synchronized(PeerControlSchedulerPrioritised.this) {
						latestTime	= time;
						if (instanceMap.size() > 0 || pendingRegistrations.size() > 0) {
							PeerControlSchedulerPrioritised.this.notify();
						}
					}
				}
			});

		ArrayList	instances = new ArrayList();
		long	latestTimeUsed	= 0;
		int scheduledNext = 0;
		long	currentScheduleStart = latestTime;
		long 	lastStatsTime	= latestTime;
		while (true) {
			if (registrationsChanged) {
				try {
					thisMon.enter();
					Iterator	it = instances.iterator();
					while (it.hasNext()) {
						if (((InstanceWrapper)it.next()).isUnregistered()) {
							it.remove();
						}
					}
					for (int i=0;i<pendingRegistrations.size();i++)
						instances.add(pendingRegistrations.get(i));
					pendingRegistrations.clear();
					// order instances by their priority (lowest number first)
					Collections.sort(instances);
					if (instances.size() > 0) {
						for (int i=0;i<instances.size();i++)
							((InstanceWrapper)instances.get(i)).setScheduleOffset((SCHEDULE_PERIOD_MILLIS * i) / instances.size());
					}
					scheduledNext = 0;
					currentScheduleStart = latestTime;
					registrationsChanged = false;
				} finally {
					thisMon.exit();
				}
			}
			tokenDispenser.update(latestTime);
			for (int i = scheduledNext; i < instances.size(); i++) {
				InstanceWrapper inst = (InstanceWrapper) instances.get(i);
				if (currentScheduleStart + inst.getScheduleOffset() > latestTimeUsed)
					break; // too early for next task, continue waiting
				if (i == 0 || !useWeights)
					tokenDispenser.refill();
				// System.out.println("scheduling "+i+" time:"+latest_time);
				inst.schedule();
				scheduleCount++;
				scheduledNext++;
				if (scheduledNext >= instances.size()) {
					scheduledNext = 0;
					// try to run every task every SCHEDULE_PERIOD_MILLIS on average
					currentScheduleStart += SCHEDULE_PERIOD_MILLIS;
					// if tasks hog too much time then delay to prevent massive
					// catch-up-hammering
					if (latestTimeUsed - currentScheduleStart > SCHEDULE_PERIOD_MAX_CATCHUP )
						currentScheduleStart = latestTimeUsed + SCHEDULE_PERIOD_MILLIS;
				}
			}

			/*
			for (Iterator it=instances.iterator();it.hasNext();) {
				instanceWrapper	inst = (instanceWrapper)it.next();
				long	target = inst.getNextTick();
				long	diff = target - latest_time_used;
				if (diff <= 0 || diff > SCHEDULE_PERIOD_MILLIS) {
					inst.schedule();
					long new_target = target + SCHEDULE_PERIOD_MILLIS;
					diff = new_target - latest_time_used;
					if (diff <= 0 || diff > SCHEDULE_PERIOD_MILLIS)
						new_target = latest_time_used + SCHEDULE_PERIOD_MILLIS;
					inst.setNextTick(new_target);
				}
			}*/
			synchronized(this) {
				if (latestTime == latestTimeUsed) {
					wait_count++;
					try {
						long wait_start = SystemTime.getHighPrecisionCounter();
						wait(5000);
						long wait_time 	= SystemTime.getHighPrecisionCounter() - wait_start;
						total_wait_time += wait_time;
					} catch (Throwable e) {
						Debug.printStackTrace(e);
					}
				} else {
					yield_count++;
					Thread.yield();
				}
				latestTimeUsed	= latestTime;
			}
			long	statsDiff =  latestTimeUsed - lastStatsTime;
			if (statsDiff > 10000) {
				// System.out.println("stats: time = " + stats_diff + ", ticks = " + tick_count + ", inst = " + instances.size());
				lastStatsTime	= latestTimeUsed;
			}
		}
	}

	public void register(PeerControlInstance instance) {
		
		/*if (Once.getInstance().getAndIncreaseCount() < 1) {
			Log.d(TAG, "register() is called...");
			new Throwable().printStackTrace();
		}*/
		
		InstanceWrapper wrapper = new InstanceWrapper(instance);
		try {
			thisMon.enter();
			Map	new_map = new HashMap(instanceMap);
			new_map.put(instance, wrapper);
			instanceMap = new_map;
			pendingRegistrations.add(wrapper);
			registrationsChanged = true;
		} finally {
			thisMon.exit();
		}
	}

	public void unregister(
		PeerControlInstance	instance) {
		try {
			thisMon.enter();
			Map	new_map = new HashMap(instanceMap);
			InstanceWrapper wrapper = (InstanceWrapper)new_map.remove(instance);
			if (wrapper == null) {
				Debug.out("instance wrapper not found");
				return;
			}
			wrapper.unregister();
			instanceMap = new_map;
			registrationsChanged = true;
		} finally {
			thisMon.exit();
		}
	}

	public SpeedTokenDispenser getSpeedTokenDispenser() {
		return (tokenDispenser);
	}

	public void updateScheduleOrdering() {
		registrationsChanged = true;
	}

	protected static class InstanceWrapper implements Comparable {
		private final PeerControlInstance	instance;
		private boolean						unregistered;
		private long						offset;

		protected InstanceWrapper(PeerControlInstance	_instance) {
			instance = _instance;
		}

		protected void unregister() {
			unregistered	= true;
		}

		protected boolean isUnregistered() {
			return (unregistered);
		}

		protected void setScheduleOffset(long	t) {
			offset	= t;
		}

		protected long getScheduleOffset() {
			return (offset);
		}

		protected PeerControlInstance getInstance() {
			return (instance);
		}

		protected void schedule() {
			try {
				instance.schedule();
			} catch (Throwable e) {
				Debug.printStackTrace(e);
			}
		}

		public int compareTo(Object o) {
			return instance.getSchedulePriority()-((InstanceWrapper)o).instance.getSchedulePriority();
		}
	}
}
