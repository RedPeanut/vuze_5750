/*
 * Created on 11-Jan-2005
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

package com.aelitis.azureus.core.dht.router.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.gudy.azureus2.core3.util.SystemTime;

import com.aelitis.azureus.core.dht.impl.DHTLog;
import com.aelitis.azureus.core.dht.router.DHTRouterContact;
import com.aelitis.azureus.core.dht.router.DHTRouterContactAttachment;

import hello.util.Log;

/**
 * @author parg
 *
 */

public class DHTRouterNodeImpl {
	
	private static final String TAG = DHTRouterNodeImpl.class.getSimpleName();
	
	private final DHTRouterImpl	router;
	private final int			depth;
	private final boolean		containsRouterNodeId;

	private List<DHTRouterContactImpl>	buckets;
	private List<DHTRouterContactImpl>	replacements;

	private DHTRouterNodeImpl	left;
	private DHTRouterNodeImpl	right;

	private long	lastLookupTime;

	protected DHTRouterNodeImpl(
		DHTRouterImpl					_router,
		int								_depth,
		boolean							_containsRouterNodeId,
		List<DHTRouterContactImpl>		_buckets) {
		router					= _router;
		depth					= _depth;
		containsRouterNodeId	= _containsRouterNodeId;
		buckets					= _buckets;
	}

	protected int getDepth() {
		return (depth);
	}

	protected boolean containsRouterNodeID() {
		return (containsRouterNodeId);
	}

	protected DHTRouterNodeImpl getLeft() {
		return (left);
	}

	protected DHTRouterNodeImpl getRight() {
		return (right);
	}

	protected void split(
		DHTRouterNodeImpl	newLeft,
		DHTRouterNodeImpl	newRight) {
		buckets	= null;
		if (replacements != null) {
			// we can get this when coming out of sleep mode
			// Debug.out("DHTRouterNode: inconsistenct - splitting a node with replacements");
			for (DHTRouterContactImpl rep: replacements) {
				router.notifyRemoved(rep);
			}
			replacements = null;
		}
		left	= newLeft;
		right	= newRight;
	}

	protected List<DHTRouterContactImpl> getBuckets() {
		return (buckets);
	}

	protected List<DHTRouterContactImpl> getReplacements() {
		return (replacements);
	}

	protected void addNode(DHTRouterContactImpl	node) {
		// MGP: notify that node added to bucket
		node.setBucketEntry();
		router.notifyAdded(node);
		buckets.add(node);
		requestNodeAdd(node, false);
	}

	protected DHTRouterContact addReplacement(
		DHTRouterContactImpl	replacement,
		int						maxRepPerNode) {

		if (maxRepPerNode == 0) {
			return (null);
		}

		// we ping the oldest bucket entry only if we "improve" matters in the replacement
		boolean	tryPing	= false;
		if (replacements == null) {
			tryPing	= true;
			replacements = new ArrayList<DHTRouterContactImpl>();
		} else {
			if (replacements.size() == maxRepPerNode) {
				// if this replacement is known to be alive, replace any existing
				// replacements that haven't been known to be alive
				if (replacement.hasBeenAlive()) {
					for (int i=0;i<replacements.size();i++) {
						DHTRouterContactImpl	r = (DHTRouterContactImpl)replacements.get(i);
						if (!r.hasBeenAlive()) {
							tryPing	= true;
							// MGP: kicking out a replacement that was never alive
							router.notifyRemoved(r);
							replacements.remove(i);
							break;
						}
					}

					// no unknown existing replacements but this is "newer" than the existing
					// ones so replace the oldest one
					if (replacements.size() == maxRepPerNode) {
						DHTRouterContactImpl removed = (DHTRouterContactImpl) replacements.remove(0);
						// MGP: kicking out the oldest replacement
						router.notifyRemoved(removed);
					}
				} else {
					// replace old unknown ones with newer unknown ones
					for (int i=0;i<replacements.size();i++) {
						DHTRouterContactImpl	r = (DHTRouterContactImpl)replacements.get(i);
						if (!r.hasBeenAlive()) {
							// MGP: kicking out an older replacement that was never alive
							router.notifyRemoved(r);
							replacements.remove(i);
							break;
						}
					}
				}
			} else {
				tryPing	= true;
			}
		}
		if (replacements.size() == maxRepPerNode) {
			// no room, drop the contact
			return (null);
		}
		// MGP: notify observers contact added as a replacement
		replacement.setReplacement();
		router.notifyAdded(replacement);
		replacements.add(replacement);
		if (tryPing) {
			for (int i=0;i<buckets.size();i++) {
				DHTRouterContactImpl	c = (DHTRouterContactImpl)buckets.get(i);
				// don't ping ourselves or someone already being pinged
				if (!( router.isID(c.getID()) || c.getPingOutstanding())) {
					c.setPingOutstanding(true);
					router.requestPing(c);
					break;
				}
			}
		}
		return (replacement);
	}

	protected DHTRouterContactImpl updateExistingNode(
		byte[]						nodeId,
		DHTRouterContactAttachment	attachment,
		boolean						knownToBeAlive) {
		for (int k=0;k<buckets.size();k++) {
			DHTRouterContactImpl	contact = (DHTRouterContactImpl)buckets.get(k);
			if (Arrays.equals(nodeId, contact.getID())) {
				if (knownToBeAlive) {
					// MGP: will update observers of updated status in this method
					alive(contact);
				}
				// might be the same node but back after a restart. we need to
				// treat this differently as we need to kick off the "store"
				// events as required.
				int	newId	= attachment.getInstanceID();
				
				// if the new-id is zero this represents us hearing about a contact
				// indirectly (imported or returned as a query). In this case we
				// don't use this information as an indication of the target's
				// instance identity because it isn't!
				if (newId != 0) {
					int	oldId = contact.getAttachment().getInstanceID();
					if (oldId != newId) {
						DHTLog.log("Instance ID changed for " +
									DHTLog.getString( contact.getID())+
									": old = " + oldId + ", new = " + newId);
						contact.setAttachment(attachment);
						// if the instance id was 0, this means that it was unknown
						// (e.g. contact imported). We still need to go ahead and treat
						// as a new node
						requestNodeAdd(contact, oldId != 0);
					}
				}
				return (contact);
			}
		}
		
		// check replacements as well
		if (replacements != null) {
			for (int k=0;k<replacements.size();k++) {
				DHTRouterContactImpl contact = (DHTRouterContactImpl)replacements.get(k);
				if (Arrays.equals(nodeId, contact.getID())) {
					if (knownToBeAlive) {
						// MGP: will update observers of updated status in this method
						alive(contact);
					}
					return (contact);
				}
			}
		}
		return (null);
	}

	protected void alive(DHTRouterContactImpl contact) {
		// DHTLog.log(DHTLog.getString( contact.getID()) + ": alive");
		contact.setPingOutstanding(false);
		// record whether was alive
		boolean wasAlive = contact.isAlive();
		if (buckets.remove(contact)) {
			contact.setAlive();
			if (!wasAlive) {
				// MGP: notify observers that now alive
				router.notifyNowAlive(contact);
			}
			// MGP: simply reinserting, so do not notify observers that added to bucket
			buckets.add(contact);
		} else if (replacements.remove( contact)) {
			long lastTime = contact.getFirstFailOrLastAliveTime();
			contact.setAlive();
			if (!wasAlive) {
				// MGP: notify observers that now alive
				router.notifyNowAlive(contact);
			}
			
			// this is a good time to probe the contacts as we know a
			// replacement is alive and therefore in a position to replace a
			// dead bucket entry. Only do this if we haven't heard from this contact
			// recently
			if (contact.getLastAliveTime() - lastTime > 30000) {
				for (int i=0;i<buckets.size();i++) {
					DHTRouterContactImpl	c = (DHTRouterContactImpl)buckets.get(i);
					// don't ping ourselves or someone already being pinged
					if (!( router.isID(c.getID()) || c.getPingOutstanding())) {
						c.setPingOutstanding(true);
						router.requestPing(c);
						break;
					}
				}
			}
			// MGP: simply reinserting, so do not notify observers that added to replacments
			replacements.add(contact);
		}
	}

	protected void dead(
		DHTRouterContactImpl	contact,
		boolean					force) {
		// DHTLog.log(DHTLog.getString( contact.getID()) + ": dead");
		contact.setPingOutstanding(false);
		// record whether was failing
		boolean was_failing = contact.isFailing();
		if (contact.setFailed() || force) {
				// check the contact is still present
			if (buckets.remove( contact)) {
				if (!was_failing) {
					// MGP: first notify observers that now failing
					router.notifyNowFailing(contact);
				}
				// MGP: notify that removed from bucket
				router.notifyRemoved(contact);
				if (replacements != null && replacements.size() > 0) {
						// take most recent alive one and add to buckets
					boolean	replaced	= false;
					for (int i=replacements.size()-1;i>=0;i--) {
						DHTRouterContactImpl	rep = (DHTRouterContactImpl)replacements.get(i);
						if (rep.hasBeenAlive()) {
							DHTLog.log( DHTLog.getString( contact.getID()) + ": using live replacement " + DHTLog.getString(rep.getID()));
							// MGP: notify that a replacement was promoted to the bucket
							rep.setBucketEntry();
							router.notifyLocationChanged(rep);
							replacements.remove(rep);
							buckets.add(rep);
							replaced	= true;
							requestNodeAdd(rep, false);
							break;
						}
					}
						// non alive - just take most recently added
					if (!replaced) {
						DHTRouterContactImpl	rep = (DHTRouterContactImpl)replacements.remove(replacements.size() - 1);
						DHTLog.log( DHTLog.getString( contact.getID()) + ": using unknown replacement " + DHTLog.getString(rep.getID()));
						// MGP: notify that a replacement was promoted to the bucket
						rep.setBucketEntry();
						router.notifyLocationChanged(rep);
						buckets.add(rep);
							// add-node logic will ping the node if its not known to
							// be alive
						requestNodeAdd(rep, false);
					}
				}
			} else {
				if (!was_failing) {
					// MGP: first notify observers that now failing
					router.notifyNowFailing(contact);
				}
				// MGP: notify that removed from replacement list
				router.notifyRemoved(contact);
				replacements.remove(contact);
			}
		}
	}

	protected void requestNodeAdd(
		DHTRouterContactImpl	contact,
		boolean					definiteChange) {
		// DOS problem here - if a node deliberately flicked between
		// instance IDs we'll get into an update frenzy.
		long	now = SystemTime.getCurrentTime();
		if (now - contact.getLastAddedTime() > 10000) {
			contact.setLastAddedTime(now);
			router.requestNodeAdd(contact);
		} else {
			// only produce a warning if this is a definite change from one id to
			// another (as opposed to a change from "unknown" to another)
			if (definiteChange) {
				router.log("requestNodeAdd for " + contact.getString() + " denied as too soon after previous ");
			}
		}
	}

	protected long getTimeSinceLastLookup() {
		long now = SystemTime.getCurrentTime();
		if (now < lastLookupTime) {
			// clock changed, don't know so make as large as possible
			return (Long.MAX_VALUE);
		}
		return (now - lastLookupTime);
	}

	protected void setLastLookupTime() {
		lastLookupTime = SystemTime.getCurrentTime();
	}

	public void	print(
		String	indent,
		String	prefix) {
		
		String log;
		if (left == null) {
			log = indent + prefix +
					": buckets = " + buckets.size() + contactsToString(buckets) +
					", replacements = " + (replacements == null ? "null" : (replacements.size() + contactsToString(replacements))) +
					(containsRouterNodeId ? " *" : " ") +
					(this == router.getSmallestSubtree() ? "SST" : "") +
					" tsll=" + getTimeSinceLastLookup();
			Log.d(TAG, log);
			router.log(log);
		} else {
			log = indent + prefix + ":" + (containsRouterNodeId?" *":" ") +
					(this==router.getSmallestSubtree()?"SST":"");
			Log.d(TAG, log);
			router.log(log);
			left.print(indent + "  ", prefix + "1");
			right.print(indent + "  ", prefix + "0");
		}
	}

	protected String contactsToString(List contacts) {
		StringBuilder sb = new StringBuilder(contacts.size()*64);
		sb.append("{");
		for (int i=0;i<contacts.size();i++) {
			if (i > 0) {
				sb.append(", ");
			}
			((DHTRouterContactImpl)contacts.get(i)).getString(sb);
		}
		sb.append("}");
		return (sb.toString());
	}
}
