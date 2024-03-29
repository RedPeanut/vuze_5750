/*
 * File    : Timer.java
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

import java.lang.ref.WeakReference;
import java.util.*;

public class Timer
	extends 	AERunnable
	implements	SystemTime.ChangeListener {
	
	private static final boolean DEBUG_TIMERS = true;
	private static ArrayList<WeakReference<Timer>> timers = null;
	static final AEMonitor timersMon = new AEMonitor("timers list");

	private ThreadPool	threadPool;
	private Set<TimerEvent>	events = new TreeSet<TimerEvent>();

	private long	uniqueIdNext	= 0;

	private long				currentWhen;
	private volatile boolean	destroyed;
	private boolean				indestructable;

	private boolean		log;
	private int			maxEventsLogged;

	public Timer(String	name) {
		this(name, 1);
	}

	public Timer(String	name, int threadPoolSize) {
		this(name, threadPoolSize, Thread.NORM_PRIORITY);
	}

	public Timer(
		String	name,
		int		threadPoolSize,
		int		threadPriority) {
		
		if (DEBUG_TIMERS) {
			try {
				timersMon.enter();
				if (timers == null) {
					timers = new ArrayList<WeakReference<Timer>>();
					AEDiagnostics.addEvidenceGenerator(new evidenceGenerator());
				}
				timers.add(new WeakReference<Timer>(this));
			} finally {
				timersMon.exit();
			}
		}
		
		threadPool = new ThreadPool(name, threadPoolSize);
		SystemTime.registerClockChangeListener(this);
		
		Thread t = new Thread(this, "Timer:" + name);
		t.setDaemon(true);
		t.setPriority(threadPriority);
		t.start();
	}

	public void setIndestructable() {
		indestructable	= true;
	}

	public synchronized List<TimerEvent> getEvents() {
		return (new ArrayList<TimerEvent>(events));
	}
	
	public void setLogging(boolean _log) {
		log = _log;
	}

	public boolean getLogging() {
		return log;
	}

	public void setWarnWhenFull() {
		threadPool.setWarnWhenFull();
	}

	public void setLogCPU() {
		threadPool.setLogCPU();
	}

	public void runSupport() {
		while (true) {
			try {
				TimerEvent	eventToRun = null;
				synchronized(this) {
					if (destroyed) {
						break;
					}
					if (events.isEmpty()) {
						// System.out.println("waiting forever");
						try {
							currentWhen = Integer.MAX_VALUE;
							this.wait();
						} finally {
							currentWhen = 0;
						}
					} else {
						long now = SystemTime.getCurrentTime();
						TimerEvent nextEvent = (TimerEvent) events.iterator().next();
						long when = nextEvent.getWhen();
						long delay = when - now;
						if (delay > 0) {
							// System.out.println("waiting for " + delay);
							try {
								currentWhen = when;
								this.wait(delay);
							} finally {
								currentWhen = 0;
							}
						}
					}
					if (destroyed) {
						break;
					}
					if (events.isEmpty()) {
						continue;
					}
					
					long	now = SystemTime.getCurrentTime();
					Iterator<TimerEvent> it = events.iterator();
					TimerEvent	nextEvent = it.next();
					long rem = nextEvent.getWhen() - now;
					if (rem <= SystemTime.TIME_GRANULARITY_MILLIS) {
						eventToRun = nextEvent;
						it.remove();
						/*
						if (rem < -100) {
							System.out.println("Late scheduling [" + (-rem) + "] of " + event_to_run.getString());
						}
						*/
					}
					// System.out.println( getName() +": events=" + events.size() + ", to_run=" +  (event_to_run==null?"null":event_to_run.getString()));
				}
				if (eventToRun != null) {
					eventToRun.setHasRun();
					if (log) {
						System.out.println("running: " + eventToRun.getString());
					}
					threadPool.run(eventToRun.getRunnable());
				}
			} catch (Throwable e) {
				Debug.printStackTrace(e);
			}
		}
	}

	public void clockChangeDetected(
		long	current_time,
		long	offset) {
		if (Math.abs(offset) >= 60*1000) {
				// fix up the timers
			synchronized(this) {
				Iterator<TimerEvent>	it = events.iterator();
				List<TimerEvent>	updated_events = new ArrayList<TimerEvent>( events.size());
				while (it.hasNext()) {
					TimerEvent	event = (TimerEvent)it.next();
						// absolute events don't have their timings fiddled with
					if (!event.isAbsolute()) {
						long	old_when = event.getWhen();
						long	new_when = old_when + offset;
						TimerEventPerformer performer = event.getPerformer();
							// sanity check for periodic events
						if (performer instanceof TimerEventPeriodic) {
							TimerEventPeriodic	periodic_event = (TimerEventPeriodic)performer;
							long	freq = periodic_event.getFrequency();
							if (new_when > current_time + freq + 5000) {
								long	adjusted_when = current_time + freq;
								//Debug.outNoStack(periodic_event.getName() + ": clock change sanity check. Reduced schedule time from " + old_when + "/" + new_when + " to " +  adjusted_when);
								new_when = adjusted_when;
							}
						}
						// don't wrap around by accident although this really shouldn't happen
						if (old_when > 0 && new_when < 0 && offset > 0) {
							// Debug.out("Ignoring wrap around for " + event.getName());
						} else {
							// System.out.println("    adjusted: " + old_when + " -> " + new_when);
							event.setWhen(new_when);
						}
					}
					updated_events.add(event);
				}
					// resort - we have to use an alternative list of events as input because if we just throw the
					// treeset in the constructor optimises things under the assumption that the original set
					// was correctly sorted...
				events = new TreeSet<TimerEvent>(updated_events);
			}
		}
	}

	public void clockChangeCompleted(
		long	current_time,
		long	offset) {
		if (Math.abs(offset) >= 60*1000) {
				// there's a chance that between the change being notified and completed an event was scheduled
				// using an un-modified current time. Nothing can be done for non-periodic events but for periodic
				// ones we can santitize them to at least be within the periodic time period of the current time
				// important for when clock goes back but not forward obviously
			synchronized(this) {
				Iterator<TimerEvent>	it = events.iterator();
				boolean	updated = false;
				while (it.hasNext()) {
					TimerEvent	event = (TimerEvent)it.next();
						// absolute events don't have their timings fiddled with
					if (!event.isAbsolute()) {
						TimerEventPerformer performer = event.getPerformer();
						// sanity check for periodic events
						if (performer instanceof TimerEventPeriodic) {
							TimerEventPeriodic	periodic_event = (TimerEventPeriodic)performer;
							long	freq = periodic_event.getFrequency();
							long old_when = event.getWhen();
							if (old_when > current_time + freq + 5000) {
								long	adjusted_when = current_time + freq;
								//Debug.outNoStack(periodic_event.getName() + ": clock change sanity check. Reduced schedule time from " + old_when + " to " +  adjusted_when);
								event.setWhen(adjusted_when);
								updated = true;
							}
						}
					}
				}
				if (updated) {
					events = new TreeSet<TimerEvent>(new ArrayList<TimerEvent>( events));
				}
				// must have this notify here as the scheduling code uses the current time to calculate
				// how long to sleep for and this needs to be guaranteed to be using the correct (new) time
				notify();
			}
		}
	}

	public void adjustAllBy(long	offset) {
		// fix up the timers
		synchronized (this) {
			// as we're adjusting all events by the same amount the ordering remains valid
			Iterator<TimerEvent> it = events.iterator();
			boolean resort = false;
			while (it.hasNext()) {
				TimerEvent event = it.next();
				long old_when = event.getWhen();
				long new_when = old_when + offset;
					// don't wrap around by accident
				if (old_when > 0 && new_when < 0 && offset > 0) {
					// Debug.out("Ignoring wrap around for " + event.getName());
					resort = true;
				} else {
					// System.out.println("    adjusted: " + old_when + " -> " + new_when);
					event.setWhen(new_when);
				}
			}
			if (resort) {
				events = new TreeSet<TimerEvent>(new ArrayList<TimerEvent>( events));
			}
			notify();
		}
	}

	public synchronized TimerEvent addEvent(
		long				when,
		TimerEventPerformer	performer) {
		return (addEvent(SystemTime.getCurrentTime(), when, performer));
	}

	public synchronized TimerEvent addEvent(
		String				name,
		long				when,
		TimerEventPerformer	performer) {
		return (addEvent(name, SystemTime.getCurrentTime(), when, performer));
	}

	public synchronized TimerEvent addEvent(
		String				name,
		long				when,
		boolean				absolute,
		TimerEventPerformer	performer) {
		return (addEvent(name, SystemTime.getCurrentTime(), when, absolute, performer));
	}

	public synchronized TimerEvent addEvent(
		long				creationTime,
		long				when,
		TimerEventPerformer	performer) {
		return (addEvent(null, creationTime, when, performer));
	}

	public synchronized TimerEvent addEvent(
		long				creationTime,
		long				when,
		boolean				absolute,
		TimerEventPerformer	performer) {
		return (addEvent(null, creationTime, when, absolute, performer));
	}

	public synchronized TimerEvent addEvent(
		String				name,
		long				creationTime,
		long				when,
		TimerEventPerformer	performer) {
		return (addEvent(name, creationTime, when, false, performer));
	}

	public synchronized TimerEvent addEvent(
		String				name,
		long				creationTime,
		long				when,
		boolean				absolute,
		TimerEventPerformer	performer) {
		
		TimerEvent event = new TimerEvent(this, uniqueIdNext++, creationTime, when, absolute, performer);
		if (name != null) {
			event.setName(name);
		}
		events.add(event);
		if (log) {
			if (events.size() > maxEventsLogged) {
				maxEventsLogged = events.size();
				System.out.println("Timer '" + threadPool.getName() + "' - events = " + maxEventsLogged);
			}
		}
		// System.out.println("event added (" + when + ") - queue = " + events.size());
		if (currentWhen == Integer.MAX_VALUE || when < currentWhen) {
			notify();
		}
		return (event);
	}

	public synchronized TimerEventPeriodic addPeriodicEvent(
		long				frequency,
		TimerEventPerformer	performer) {
		return (addPeriodicEvent(null, frequency, performer));
	}

	public synchronized TimerEventPeriodic addPeriodicEvent(
		String				name,
		long				frequency,
		TimerEventPerformer	performer) {
		return (addPeriodicEvent(name, frequency, false, performer));
	}

	public synchronized TimerEventPeriodic addPeriodicEvent(
		String				name,
		long				frequency,
		boolean				absolute,
		TimerEventPerformer	performer) {
		TimerEventPeriodic periodicPerformer = new TimerEventPeriodic(this, frequency, absolute, performer);
		if (name != null) {
			periodicPerformer.setName(name);
		}
		if (log) {
			System.out.println("Timer '" + threadPool.getName() + "' - added " + periodicPerformer.getString());
		}
		return (periodicPerformer);
	}

	protected synchronized void cancelEvent(TimerEvent	event) {
		if (events.contains( event)) {
			events.remove(event);
			// System.out.println("event cancelled (" + event.getWhen() + ") - queue = " + events.size());
			notify();
		}
	}

	public synchronized void destroy() {
		if (indestructable) {
			Debug.out("Attempt to destroy indestructable timer '" + getName() + "'");
		} else {
			destroyed	= true;
			notify();
			SystemTime.unregisterClockChangeListener(this);
		}
		
		if (DEBUG_TIMERS) {
			try {
				timersMon.enter();
				// crappy
				for (Iterator iter = timers.iterator(); iter.hasNext();) {
					WeakReference timerRef = (WeakReference) iter.next();
					Object timer = timerRef.get();
					if (timer == null || timer == this) {
						iter.remove();
					}
				}
			} finally {
				timersMon.exit();
			}
		}
	}

	public String getName() {
		return (threadPool.getName());
	}

	public synchronized void dump() {
		System.out.println("Timer '" + threadPool.getName() + "': dump");
		Iterator	it = events.iterator();
		while (it.hasNext()) {
			TimerEvent	ev = (TimerEvent)it.next();
			System.out.println("\t" + ev.getString());
		}
	}

	private static class evidenceGenerator 
		implements AEDiagnosticsEvidenceGenerator {
		
		public void generate(IndentWriter writer) {
			if (!DEBUG_TIMERS) {
				return;
			}

			ArrayList lines = new ArrayList();
			int count = 0;
			try {
				try {
					timersMon.enter();
					// crappy
					for (Iterator iter = timers.iterator(); iter.hasNext();) {
						WeakReference timerRef = (WeakReference) iter.next();
						Timer timer = (Timer) timerRef.get();
						if (timer == null) {
							iter.remove();
						} else {
							count++;

							List	events = timer.getEvents();

							lines.add(timer.threadPool.getName() + ", "
									+ events.size() + " events:");

							Iterator it = events.iterator();
							while (it.hasNext()) {
								TimerEvent ev = (TimerEvent) it.next();
								lines.add("  " + ev.getString());
							}
						}
					}
				} finally {
					timersMon.exit();
				}

				writer.println("Timers: " + count + " (time=" + SystemTime.getCurrentTime() + "/" + SystemTime.getMonotonousTime() + ")");
				writer.indent();
				for (Iterator iter = lines.iterator(); iter.hasNext();) {
					String line = (String) iter.next();
					writer.println(line);
				}
				writer.exdent();
			} catch (Throwable e) {
				writer.println(e.toString());
			}
		}
	}
}
