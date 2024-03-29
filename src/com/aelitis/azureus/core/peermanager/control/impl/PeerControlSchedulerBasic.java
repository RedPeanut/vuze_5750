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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.gudy.azureus2.core3.util.AEMonitor;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.SystemTime;

import com.aelitis.azureus.core.peermanager.control.PeerControlInstance;
import com.aelitis.azureus.core.peermanager.control.SpeedTokenDispenser;
import com.aelitis.azureus.core.stats.AzureusCoreStatsProvider;

import hello.util.Log;

public class PeerControlSchedulerBasic
	extends PeerControlSchedulerImpl
	implements AzureusCoreStatsProvider
{
	
	private static String TAG = PeerControlSchedulerBasic.class.getSimpleName();
	
	private final Random	random = new Random();
	private Map<PeerControlInstance,instanceWrapper>	instance_map = new HashMap();
	private final List<instanceWrapper>	pending_registrations = new ArrayList<instanceWrapper>();
	private volatile boolean	registrations_changed;
	protected final AEMonitor	this_mon = new AEMonitor("PeerControlSchedulerBasic");
	private final SpeedTokenDispenserBasic tokenDispenser = new SpeedTokenDispenserBasic();

	private long	latestTime;
	private long	lastLagLog;

	protected void schedule() {
		
		/*Log.d(TAG, "schedule() is called...");
		Throwable t = new Throwable();
		t.printStackTrace();*/
		
		SystemTime.registerMonotonousConsumer(
			new SystemTime.TickConsumer() {
				public void consume(long time) {
					synchronized(PeerControlSchedulerBasic.this) {
						PeerControlSchedulerBasic.this.notify();
					}
				}
			});

		List<instanceWrapper>	instances = new LinkedList<instanceWrapper>();
		long	tick_count		= 0;
		long 	last_stats_time	= SystemTime.getMonotonousTime();
		while (true) {
			if (registrations_changed) {
				try {
					this_mon.enter();
					Iterator<instanceWrapper>	it = instances.iterator();
					while (it.hasNext()) {
						if (it.next().isUnregistered()) {
							it.remove();
						}
					}
					for (int i=0;i<pending_registrations.size();i++) {
						instances.add( pending_registrations.get(i));
					}
					pending_registrations.clear();
					registrations_changed	= false;
				} finally {
					this_mon.exit();
				}
			}
			latestTime	= SystemTime.getMonotonousTime();
			long current_schedule_count = scheduleCount;
			for (instanceWrapper inst: instances) {
				long	target = inst.getNextTick();
				long	diff = latestTime - target;
				if (diff >= 0) {
					tick_count++;
					inst.schedule(latestTime);
					scheduleCount++;
					long new_target = target + SCHEDULE_PERIOD_MILLIS;
					if (new_target <= latestTime) {
						new_target = latestTime + (target % SCHEDULE_PERIOD_MILLIS);
					}
					inst.setNextTick(new_target);
				}
			}
			synchronized(this) {
				if (current_schedule_count == scheduleCount) {
					wait_count++;
					try {
						long wait_start = SystemTime.getHighPrecisionCounter();
						wait(SCHEDULE_PERIOD_MILLIS);
						long wait_time 	= SystemTime.getHighPrecisionCounter() - wait_start;
						total_wait_time += wait_time;
					} catch (Throwable e) {
						Debug.printStackTrace(e);
					}
				} else {
					yield_count++;
					Thread.yield();
				}
			}
			long	stats_diff =  latestTime - last_stats_time;
			if (stats_diff > 10000) {
				// System.out.println("stats: time = " + stats_diff + ", ticks = " + tick_count + ", inst = " + instances.size());
				last_stats_time	= latestTime;
				tick_count	= 0;
			}
		}
	}

	public void register(
		PeerControlInstance	instance) {
		instanceWrapper wrapper = new instanceWrapper(instance);
		wrapper.setNextTick(latestTime + random.nextInt( SCHEDULE_PERIOD_MILLIS));
		try {
			this_mon.enter();
			Map<PeerControlInstance,instanceWrapper> new_map = new HashMap<PeerControlInstance,instanceWrapper>(instance_map);
			new_map.put(instance, wrapper);
			instance_map = new_map;
			pending_registrations.add(wrapper);
			registrations_changed = true;
		} finally {
			this_mon.exit();
		}
	}

	public void unregister(PeerControlInstance instance) {
		try {
			this_mon.enter();
			Map<PeerControlInstance,instanceWrapper>	new_map = new HashMap<PeerControlInstance,instanceWrapper>(instance_map);
			instanceWrapper wrapper = new_map.remove(instance);
			if (wrapper == null) {
				Debug.out("instance wrapper not found");
				return;
			}
			wrapper.unregister();
			instance_map = new_map;
			registrations_changed = true;
		} finally {
			this_mon.exit();
		}
	}

	public SpeedTokenDispenser getSpeedTokenDispenser() {
		return (tokenDispenser);
	}

	public void updateScheduleOrdering() {
	}

	protected class instanceWrapper {
		private final PeerControlInstance	instance;
		private boolean						unregistered;
		private long						nextTick;
		private long						last_schedule;
		
		protected instanceWrapper(PeerControlInstance _instance) {
			instance = _instance;
		}
		
		protected void unregister() {
			unregistered = true;
		}
		
		protected boolean isUnregistered() {
			return (unregistered);
		}
		
		protected void setNextTick(long t) {
			nextTick = t;
		}
		
		protected long getNextTick() {
			return (nextTick);
		}
		
		protected String getName() {
			return (instance.getName());
		}
		
		protected void schedule(long mono_now) {
			if (mono_now < 100000) {
				Debug.out("eh?");
			}
			if (last_schedule > 0) {
				if (mono_now - last_schedule > 1000) {
					if (mono_now - lastLagLog > 1000) {
						lastLagLog = mono_now;
						System.out.println("Scheduling lagging: " + (mono_now - last_schedule) + " - instances=" + instance_map.size());
					}
				}
			}
			last_schedule = mono_now;
			try {
				instance.schedule();
			} catch (Throwable e) {
				Debug.printStackTrace(e);
			}
		}
	}
}
