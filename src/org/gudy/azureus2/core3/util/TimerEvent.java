/*
 * File    : TimerEvent.java
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

public class TimerEvent
	extends		ThreadPoolTask
	implements 	Comparable<TimerEvent> {
	
	private String						name;

	private final Timer					timer;
	private final long					created;
	private long						when;
	private final TimerEventPerformer	performer;

	private final boolean	absolute;
	private boolean			cancelled;
	private boolean			hasRun;

	private long			uniqueId	= 1;

	protected TimerEvent(
		Timer					_timer,
		long					_uniqueId,
		long					_created,
		long					_when,
		boolean					_absolute,
		TimerEventPerformer		_performer) {
		
		timer		= _timer;
		uniqueId	= _uniqueId;
		when		= _when;
		absolute	= _absolute;
		performer	= _performer;
		created 	= _created;

		if (Constants.IS_CVS_VERSION) {
			// sanity check - seems we sometimes use 0 to denote 'now'
			if (when != 0 && when <= 7*24*60*60*1000L) {
				new Exception("You sure you want to schedule an event in the past? Time should be absolute!").printStackTrace();
			} else if (when > 3000L*365*24*60*60*1000) {
				new Exception("You sure you want to schedule an event so far in the future?! (" + when + ")").printStackTrace();
			}
		}
	}

	public void setName(String _name) {
		name = _name;
	}

	public String getName() {
		return (name);
	}

	public long getCreatedTime() {
		return (created);
	}

	public long getWhen() {
		return (when);
	}

	protected void setWhen(long newWhen) {
		when = newWhen;
	}

	protected AERunnable getRunnable() {
		return (this);
	}

	protected TimerEventPerformer getPerformer() {
		return (performer);
	}

	protected boolean isAbsolute() {
		return (absolute);
	}

	public void runSupport() {
		performer.perform(this);
	}

	public synchronized void cancel() {
		cancelled	= true;
		timer.cancelEvent(this);
	}

	public synchronized boolean isCancelled() {
		return (cancelled);
	}

	protected void setHasRun() {
		hasRun	= true;
	}

	public boolean hasRun() {
		return (hasRun);
	}

	protected long getUniqueId() {
		return (uniqueId);
	}

	public int compareTo(
		TimerEvent		other) {
		long	res =  when - other.when;
		if (res == 0) {
			res = uniqueId - other.uniqueId;
			if (res == 0) {
				return ( 0);
			}
		}
		return res < 0 ? -1 : 1;
	}

	public void interruptTask() {
	}

	public String getString() {
		if (performer instanceof TimerEventPeriodic) {
			TimerEventPeriodic	tep = (TimerEventPeriodic)performer;
			return ("when=" + getWhen() + ",run=" + hasRun() + ", can=" + isCancelled() + "/" + tep.isCancelled() + ",freq=" + tep.getFrequency() + ",target=" + tep.getPerformer()+ (name==null?"":",name=" + name));
		} else {
			return ("when=" + getWhen() + ",run=" + hasRun() + ", can=" + isCancelled() + ",target=" + getPerformer() + (name==null?"":",name=" + name));
		}
	}
}
