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

import hello.util.Log;
import hello.util.SingleCounter1;
import hello.util.Util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.util.AEMonitor;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.SimpleTimer;
import org.gudy.azureus2.core3.util.SystemTime;
import org.gudy.azureus2.core3.util.TimerEvent;
import org.gudy.azureus2.core3.util.TimerEventPerformer;
import org.gudy.azureus2.core3.util.TimerEventPeriodic;

import com.aelitis.azureus.core.dht.DHTLogger;
import com.aelitis.azureus.core.dht.impl.DHTLog;
import com.aelitis.azureus.core.dht.router.DHTRouter;
import com.aelitis.azureus.core.dht.router.DHTRouterAdapter;
import com.aelitis.azureus.core.dht.router.DHTRouterContact;
import com.aelitis.azureus.core.dht.router.DHTRouterContactAttachment;
import com.aelitis.azureus.core.dht.router.DHTRouterObserver;
import com.aelitis.azureus.core.dht.router.DHTRouterStats;
import com.aelitis.azureus.core.util.CopyOnWriteList;
import com.aelitis.azureus.core.util.bloom.BloomFilter;
import com.aelitis.azureus.core.util.bloom.BloomFilterFactory;

/**
 * @author parg
 *
 */

public class DHTRouterImpl implements DHTRouter {
	
	private String TAG = DHTRouterImpl.class.getSimpleName();
	
	private static final int	SMALLEST_SUBTREE_MAX_EXCESS	= 10*1024;

	private boolean		isBootstrapProxy;

	private int		K;
	private int		B;
	private int		maxRepPerNode;

	private DHTLogger		logger;

	private int		smallestSubtreeMax;

	private DHTRouterAdapter		adapter;

	private DHTRouterContactImpl	localContact;
	private byte[]					routerNodeId;

	private DHTRouterNodeImpl		root;
	private DHTRouterNodeImpl		smallestSubtree;

	private int						consecutiveDead;

	private static long				randomSeed	= SystemTime.getCurrentTime();
	private Random					random;

	private List<DHTRouterContactImpl>	outstandingPings	= new ArrayList<DHTRouterContactImpl>();
	private List<DHTRouterContactImpl>	outstandingAdds		= new ArrayList<DHTRouterContactImpl>();

	private final DHTRouterStatsImpl	stats		= new DHTRouterStatsImpl(this);

	private final AEMonitor			monitor			= new AEMonitor("DHTRouter");
	private static final AEMonitor	classMonitor	= new AEMonitor("DHTRouter:class");

	private final CopyOnWriteList<DHTRouterObserver>	observers = new CopyOnWriteList<DHTRouterObserver>();

	private boolean	sleeping;
	private boolean	suspended;

	private final BloomFilter recentContactBloom =
		BloomFilterFactory.createRotating(
			BloomFilterFactory.createAddOnly(10*1024),
			2);

	private TimerEventPeriodic	timerEvent;

	private volatile int seedInTicks;

	private static final int	TICK_PERIOD 		= 10*1000;
	private static final int	SEED_DELAY_PERIOD	= 60*1000;
	private static final int	SEED_DELAY_TICKS	= SEED_DELAY_PERIOD/TICK_PERIOD;


	public DHTRouterImpl(
		int							_K,
		int							_B,
		int							_maxRepPerNode,
		byte[]						_routerNodeId,
		DHTRouterContactAttachment	_attachment,
		DHTLogger					_logger) {

		Log.d(TAG, "<init>() is called...");
		//Log.d(TAG, "_routerNodeId = " + Util.toHexString(_routerNodeId));
		new Throwable().printStackTrace();
		
		try {
			// only needed for in-process multi-router testing :P
			classMonitor.enter();
			random = new Random(randomSeed++);
		} finally {
			classMonitor.exit();
		}

		isBootstrapProxy	= COConfigurationManager.getBooleanParameter("dht.bootstrap.is.proxy", false);
		K					= _K;
		B					= _B;
		maxRepPerNode	= _maxRepPerNode;
		logger				= _logger;

		smallestSubtreeMax	= 1;
		for (int i=0;i<B;i++) {
			smallestSubtreeMax *= 2;
		}

		smallestSubtreeMax += SMALLEST_SUBTREE_MAX_EXCESS;
		routerNodeId = _routerNodeId;
		List<DHTRouterContactImpl> buckets = new ArrayList<>();
		localContact = new DHTRouterContactImpl(routerNodeId, _attachment, true);
		buckets.add(localContact);
		root = new DHTRouterNodeImpl(this, 0, true, buckets);
		
		//print();
		
		timerEvent = SimpleTimer.addPeriodicEvent(
			"DHTRouter:pinger",
			TICK_PERIOD,
			new TimerEventPerformer() {
				public void perform(TimerEvent event) {
					
					if (suspended)
						return;
					
					pingeroonies();
					if (seedInTicks > 0) {
						seedInTicks--;
						if (seedInTicks == 0) {
							seedSupport();
						}
					}
				}
			}
		);
	}

	protected void notifyAdded(DHTRouterContact contact) {
		for (Iterator<DHTRouterObserver> i = observers.iterator(); i.hasNext();) {
			DHTRouterObserver rto = i.next();
			try {
				rto.added(contact);
			} catch (Throwable e) {
				Debug.printStackTrace(e);
			}
		}
	}

	protected void notifyRemoved(DHTRouterContact contact) {
		for (Iterator<DHTRouterObserver> i = observers.iterator(); i.hasNext();) {
			DHTRouterObserver rto = i.next();
			try {
				rto.removed(contact);
			} catch (Throwable e) {
				Debug.printStackTrace(e);
			}
		}
	}

	protected void notifyLocationChanged(DHTRouterContact contact) {
		for (Iterator<DHTRouterObserver> i = observers.iterator(); i.hasNext();) {
			DHTRouterObserver rto = i.next();
			try {
				rto.locationChanged(contact);
			} catch (Throwable e) {
				Debug.printStackTrace(e);
			}
		}
	}

	protected void notifyNowAlive(DHTRouterContact contact) {
		for (Iterator<DHTRouterObserver> i = observers.iterator(); i.hasNext();) {
			DHTRouterObserver rto = i.next();
			try {
				rto.nowAlive(contact);
			} catch (Throwable e) {
				Debug.printStackTrace(e);
			}
		}
	}

	protected void notifyNowFailing(DHTRouterContact contact) {
		for (Iterator<DHTRouterObserver> i = observers.iterator(); i.hasNext();) {
			DHTRouterObserver rto = i.next();
			try {
				rto.nowFailing(contact);
			} catch (Throwable e) {
				Debug.printStackTrace(e);
			}
		}
	}

	protected void notifyDead() {
		for (Iterator<DHTRouterObserver> i = observers.iterator(); i.hasNext();) {
			DHTRouterObserver rto = i.next();
			try {
				rto.destroyed(this);
			} catch (Throwable e) {
				Debug.printStackTrace(e);
			}
		}
	}

	public boolean addObserver(DHTRouterObserver rto) {
		if ((rto != null) && !observers.contains(rto)) {
			observers.add(rto);
			return true;
		}
		return false;
	}

	public boolean containsObserver(DHTRouterObserver rto) {
		return ((rto != null) && observers.contains(rto));
	}

	public boolean removeObserver(DHTRouterObserver rto) {
		return ((rto != null) && observers.remove(rto));
	}

	public DHTRouterStats getStats() {
		return (stats);
	}

	public int getK() {
		return (K);
	}


	public byte[] getID() {
		return (routerNodeId);
	}

	public boolean isID(byte[] id) {
		return (Arrays.equals( id, routerNodeId));
	}

	public DHTRouterContact getLocalContact() {
		return (localContact);
	}

	public void setAdapter(
		DHTRouterAdapter	_adapter) {
		adapter	= _adapter;
	}

	public void setSleeping(
		boolean	_sleeping) {
		sleeping = _sleeping;
	}

	public void setSuspended(
		boolean			_suspended) {
		suspended = _suspended;
		if (!suspended) {
			seedInTicks = 1;
		}
	}

	public void contactKnown(
		byte[]						nodeId,
		DHTRouterContactAttachment	attachment,
		boolean						force) {
		
		// especially for small DHTs we don't want to prevent a contact from being re-added as long as they've been away for
		// a bit
		if (SystemTime.getMonotonousTime() - recentContactBloom.getStartTimeMono() > 10*60*1000) {
			recentContactBloom.clear();
		}
		
		if (recentContactBloom.contains(nodeId)) {
			if (!force)
				return;
		}
		
		recentContactBloom.add(nodeId);
		addContact(nodeId, attachment, false);
	}

	public void contactAlive(
		byte[]						nodeId,
		DHTRouterContactAttachment	attachment) {
		addContact(nodeId, attachment, true);
	}

	// all incoming node actions come through either contactDead or addContact
	// A side effect of processing
	// the node is that either a ping can be requested (if a replacement node
	// is available and the router wants to check the liveness of an existing node)
	// or a new node can be added (either directly to a node or indirectly via
	// a replacement becoming "live"
	// To avoid requesting these actions while synchronised these are recorded
	// in lists and then kicked off separately here


	public DHTRouterContact
	contactDead(
		byte[]						node_id,
		boolean						force) {
		if (suspended) {
			return (null);
		}
		if (Arrays.equals( routerNodeId, node_id)) {
				// we should never become dead ourselves as this screws up things like
				// checking that stored values are close enough to the K livest nodes (as if we are
				// dead we don't return ourselves and it all goes doo daa )
			Debug.out("DHTRouter: contactDead called on router node!");
			return (localContact);
		}
		try {
			try {
				monitor.enter();
				consecutiveDead++;
				/*
				if (consecutive_dead != 0 && consecutive_dead % 10 == 0) {
					System.out.println("consecutive_dead: " + consecutive_dead);
				}
				*/
				Object[]	res = findContactSupport(node_id);
				DHTRouterNodeImpl		node	= (DHTRouterNodeImpl)res[0];
				DHTRouterContactImpl	contact = (DHTRouterContactImpl)res[1];
				if (contact != null) {
					// some protection against network drop outs - start ignoring dead
					// notifications if we're getting significant continous fails
					if (consecutiveDead < 100 || force) {
						contactDeadSupport(node, contact, force);
					}
				}
				return (contact);
			} finally {
				monitor.exit();
			}
		} finally {
			dispatchPings();
			dispatchNodeAdds();
		}
	}

	private void contactDeadSupport(
		DHTRouterNodeImpl		node,
		DHTRouterContactImpl	contact,
		boolean					force) {
		// bootstrap proxy has no network so we can't detect liveness of contacts. simply allow replacement of bucket
		// entries when possible to rotate somewhat
		if (isBootstrapProxy) {
			List<DHTRouterContactImpl> replacements = node.getReplacements();
			if (replacements == null || replacements.size() == 0) {
				return;
			}
		}
		node.dead(contact, force);
	}

	public void contactRemoved(byte[] node_id) {

	}

	public void	addContact(
		byte[]						nodeId,
		DHTRouterContactAttachment	attachment,
		boolean						knownToBeAlive) {
		
		if (attachment.isSleeping()) {
			// sleeping nodes are removed from the router as they're not generally
			// available for doing stuff
			if (Arrays.equals(routerNodeId, nodeId))
				return;
			
			try {
				monitor.enter();
				Object[]	res = findContactSupport(nodeId);
				DHTRouterNodeImpl		node	= (DHTRouterNodeImpl)res[0];
				DHTRouterContactImpl	contact = (DHTRouterContactImpl)res[1];
				if (contact != null) {
					contactDeadSupport(node, contact, true);
				}
			} finally {
				monitor.exit();
			}
			return;
		}
		
		try {
			try {
				monitor.enter();
				if (knownToBeAlive) {
					consecutiveDead	= 0;
				}
				addContactSupport(nodeId, attachment, knownToBeAlive);
			} finally {
				monitor.exit();
			}
		} finally {
			dispatchPings();
			dispatchNodeAdds();
		}
	}

	private DHTRouterContact addContactSupport(
		byte[]						nodeId,
		DHTRouterContactAttachment	attachment,
		boolean						knownToBeAlive) {
		
		/*Log.d(TAG, "addContactSupport() is called...");
		Throwable t = new Throwable();
		t.printStackTrace();*/
		
		if (Arrays.equals(routerNodeId, nodeId)) {
			// as we have reduced node id space the chance of us sharing a node id is higher. Easiest way to handle this is
			// just to bail out here
			return (localContact);
		}
		
		DHTRouterNodeImpl currentNode = root;
		boolean	partOfSmallestSubtree = false;
		for (int i=0;i<nodeId.length;i++) {
			byte	b = nodeId[i];
			int	j = 7;
			while (j >= 0) {
				if (currentNode == smallestSubtree) {
					partOfSmallestSubtree	= true;
				}
				boolean	bit = ((b>>j)&0x01)==1?true:false;
				DHTRouterNodeImpl	nextNode;
				if (bit) {
					nextNode = currentNode.getLeft();
				} else {
					nextNode = currentNode.getRight();
				}
				
				if (nextNode == null) {
					DHTRouterContact	existingContact = currentNode.updateExistingNode(nodeId, attachment, knownToBeAlive);
					if (existingContact != null) {
						return (existingContact);
					}
					List<DHTRouterContactImpl> buckets = currentNode.getBuckets();
					int	bucketsSize = buckets.size();
					if (sleeping && bucketsSize >= K/4 && !currentNode.containsRouterNodeID()) {
						// keep non-important buckets less full when sleeping
						DHTRouterContactImpl new_contact = new DHTRouterContactImpl(nodeId, attachment, knownToBeAlive);
						return (currentNode.addReplacement(new_contact, 1));
					} else if (bucketsSize == K) {
						// split if either
						// 1) this list contains router_node_id or
						// 2) depth % B is not 0
						// 3) this is part of the smallest subtree
						boolean	contains_router_node_id = currentNode.containsRouterNodeID();
						int		depth					= currentNode.getDepth();
						boolean	too_deep_to_split = depth % B == 0;	// note this will be true for 0 but other
																	// conditions will allow the split
						if (	contains_router_node_id ||
								(!too_deep_to_split)	||
								partOfSmallestSubtree) {
							
							// the smallest-subtree bit is to ensure that we remember all of
							// our closest neighbours as ultimately they are the ones responsible
							// for returning our identity to queries (due to binary choppery in
							// general the query will home in on our neighbours before
							// hitting us. It is therefore important that we keep ourselves live
							// in their tree by refreshing. If we blindly chopped at K entries
							// (down to B levels) then a highly unbalanced tree would result in
							// us dropping some of them and therefore not refreshing them and
							// therefore dropping out of their trees. There are also other benefits
							// of maintaining this tree regarding stored value refresh
							// Note that it is rare for such an unbalanced tree.
							// However, a possible DOS here would be for a rogue node to
							// deliberately try and create such a tree with a large number
							// of entries.
							if (	partOfSmallestSubtree &&
									too_deep_to_split &&
									(!contains_router_node_id) &&
									getContactCount(smallestSubtree ) > smallestSubtreeMax) {
								Debug.out("DHTRouter: smallest subtree max size violation");
								return (null);
							}
							// split!!!!
							List<DHTRouterContactImpl> leftBuckets = new ArrayList<>();
							List<DHTRouterContactImpl> rightBuckets = new ArrayList<>();
							for (int k=0;k<buckets.size();k++) {
								DHTRouterContactImpl contact = (DHTRouterContactImpl)buckets.get(k);
								byte[]	bucket_id = contact.getID();
								if (((bucket_id[depth/8]>>(7-(depth%8)))&0x01 ) == 0) {
									rightBuckets.add(contact);
								} else {
									leftBuckets.add(contact);
								}
							}
							boolean	rightContainsRid = false;
							boolean leftContainsRid = false;
							if (contains_router_node_id) {
								rightContainsRid =
										((routerNodeId[depth/8]>>(7-(depth%8)))&0x01 ) == 0;
								leftContainsRid	= !rightContainsRid;
							}
							DHTRouterNodeImpl	newLeft 	= new DHTRouterNodeImpl(this, depth+1, leftContainsRid, leftBuckets);
							DHTRouterNodeImpl	newRight 	= new DHTRouterNodeImpl(this, depth+1, rightContainsRid, rightBuckets);
							currentNode.split(newLeft, newRight);
							if (rightContainsRid) {
								// we've created a new smallest subtree
								// TODO: tidy up old smallest subtree - remember to factor in B...
								smallestSubtree = newLeft;
							} else if (leftContainsRid) {
								// TODO: tidy up old smallest subtree - remember to factor in B...
								smallestSubtree = newRight;
							}
							// not complete, retry addition
						} else {
							// split not appropriate, add as a replacemnet
							DHTRouterContactImpl newContact = new DHTRouterContactImpl(nodeId, attachment, knownToBeAlive);
							return (currentNode.addReplacement(newContact, sleeping?1:maxRepPerNode));
						}
					} else {
						// bucket space free, just add it
						DHTRouterContactImpl newContact = new DHTRouterContactImpl(nodeId, attachment, knownToBeAlive);
						currentNode.addNode(newContact);	// complete - added to bucket
						return (newContact);
					}
				} else {
					currentNode = nextNode;
					j--;
				}
			}
		}
		Debug.out("DHTRouter inconsistency");
		return (null);
	}

	public List<DHTRouterContact> findClosestContacts(
		byte[]		nodeId,
		int			numToReturn,
		boolean		liveOnly) {
		
		// find the num_to_return-ish closest nodes - consider all buckets, not just the closest
		try {
			monitor.enter();
			List<DHTRouterContact> res = new ArrayList<>();
			
			/*boolean contains = false;
			Throwable t = new Throwable();
			StackTraceElement[] ste = t.getStackTrace();
			for (int i = 0; i < ste.length; i++) {
				if (ste[i].toString().contains("startLookup")) {
					contains = true;
					break;
				}
			}*/
			
			//if (contains) Log.d(TAG, "-------------------------");
			findClosestContacts(nodeId, numToReturn, 0, root, liveOnly, res);
			//if (contains) Log.d(TAG, "-------------------------");
			
			return (res);
		} finally {
			monitor.exit();
		}
	}

	protected void findClosestContacts(
		byte[]					nodeId,
		int						numToReturn,
		int						depth,
		DHTRouterNodeImpl		currentNode,
		boolean					liveOnly,
		List<DHTRouterContact>	res) {

		//Log.d(TAG, "findClosestContacts() is called...");
		//Log.d(TAG, "node_id = " + Util.toHexString(node_id));
		//Throwable t = new Throwable();
		//t.printStackTrace();
		
		//if (SingleCounter0.getInstance().getAndIncreaseCount() <= 10) {
			
			//Throwable t = new Throwable();
			//t.printStackTrace();
			
			/*StackTraceElement[] ste = t.getStackTrace();
			for (int i = 0; i < ste.length; i++) {
				Log.d(TAG, String.format("ste[%d].getClassName() = %s", i, ste[i].getClassName()));
				//Log.d(TAG, String.format("ste[%d].getFileName() = %s", i, ste[i].getFileName()));
			}*/
		//}
		
		boolean contains = false;
		Throwable t = new Throwable();
		StackTraceElement[] ste = t.getStackTrace();
		for (int i = 0; i < ste.length; i++) {
			if (ste[i].toString().contains("startLookup")) {
				contains = true;
				break;
			}
			//Log.d(TAG, String.format("ste[%d].getClassName() = %s", i, ste[i].getClassName()));
			//Log.d(TAG, String.format("ste[%d].getFileName() = %s", i, ste[i].getFileName()));
		}
		
		List<DHTRouterContactImpl> buckets = currentNode.getBuckets();
		
		if (contains && SingleCounter1.getInstance().getAndIncreaseCount() <= 2) {
			String indent = "";
			for (int i = 0; i < depth; i++)
				indent += "  ";
			if (buckets != null)
				Log.d(TAG, String.format("%s%d%s", indent, buckets.size(), buckets));
			else
				Log.d(TAG, String.format("%snull", indent));
		}
		
		if (buckets != null) {
			// add everything from the buckets - caller will sort and select
			// the best ones as required
			for (int i=0;i<buckets.size();i++) {
				DHTRouterContactImpl contact = (DHTRouterContactImpl)buckets.get(i);
				// use !failing at the moment to include unknown ones
				if (! (liveOnly && contact.isFailing())) {
					res.add(contact);
				}
			}
		} else {
			boolean bit = ((nodeId[depth/8]>>(7-(depth%8)))&0x01) == 1;
			DHTRouterNodeImpl bestNode;
			DHTRouterNodeImpl worseNode;
			if (bit) {
				bestNode = currentNode.getLeft();
				worseNode = currentNode.getRight();
			} else {
				bestNode = currentNode.getRight();
				worseNode = currentNode.getLeft();
			}
			findClosestContacts(nodeId, numToReturn, depth+1, bestNode, liveOnly, res);
			if (res.size() < numToReturn) {
				findClosestContacts(nodeId, numToReturn, depth+1, worseNode, liveOnly, res);
			}
		}
	}

	public DHTRouterContact findContact(byte[] nodeId) {
		Object[] res = findContactSupport(nodeId);
		return ((DHTRouterContact) res[1]);
	}

	protected DHTRouterNodeImpl findNode(byte[] nodeId) {
		Object[] res = findContactSupport(nodeId);
		return ((DHTRouterNodeImpl) res[0]);
	}

	protected Object[] findContactSupport(byte[] nodeId) {
		
		/*Log.d(TAG, "findContactSupport() is called...");
		Throwable t = new Throwable();
		t.printStackTrace();*/
		
		try {
			monitor.enter();
			DHTRouterNodeImpl currentNode = root;
			for (int i=0;i<nodeId.length;i++) {
				if (currentNode.getBuckets() != null) {
					break;
				}
				byte b = nodeId[i];
				int	j = 7;
				while (j >= 0) {
					boolean	bit = ((b>>j)&0x01)==1?true:false;
					if (currentNode.getBuckets() != null) {
						break;
					}
					if (bit) {
						currentNode = currentNode.getLeft();
					} else {
						currentNode = currentNode.getRight();
					}
					j--;
				}
			}
			
			List buckets = currentNode.getBuckets();
			for (int k=0;k<buckets.size();k++) {
				DHTRouterContactImpl contact = (DHTRouterContactImpl)buckets.get(k);
				if (Arrays.equals(nodeId, contact.getID())) {
					return (new Object[]{ currentNode, contact });
				}
			}
			return (new Object[]{ currentNode, null });
		} finally {
			monitor.exit();
		}
	}

	protected long getNodeCount() {
		return (getNodeCount(root));
	}

	protected long getNodeCount(DHTRouterNodeImpl node) {
		if (node.getBuckets() != null) {
			return (1);
		} else {
			return (1 + getNodeCount(node.getLeft())) + getNodeCount(node.getRight());
		}
	}

	protected long getContactCount() {
		return (getContactCount(root));
	}

	protected long getContactCount(DHTRouterNodeImpl node) {
		if (node.getBuckets() != null) {
			return (node.getBuckets().size());
		} else {
			return (getContactCount(node.getLeft())) + getContactCount(node.getRight());
		}
	}

	public List findBestContacts(int max) {
		Set<Object> set = new TreeSet<Object>(new Comparator<Object>() {
			public int compare(Object o1, Object o2) {
				DHTRouterContactImpl c1 = (DHTRouterContactImpl) o1;
				DHTRouterContactImpl c2 = (DHTRouterContactImpl) o2;
				return ((int) (c2.getTimeAlive() - c1.getTimeAlive()));
			}
		});
		try {
			monitor.enter();
			findAllContacts(set, root);
		} finally {
			monitor.exit();
		}
		List result = new ArrayList(max);
		Iterator it = set.iterator();
		while (it.hasNext() && (max <= 0 || result.size() < max)) {
			result.add(it.next());
		}
		return (result);
	}

	public List getAllContacts() {
		try {
			monitor.enter();
			List l = new ArrayList();
			findAllContacts(l, root);
			return (l);
		} finally {
			monitor.exit();
		}
	}

	protected void findAllContacts(Set set, DHTRouterNodeImpl node) {
		List buckets = node.getBuckets();
		if (buckets == null) {
			findAllContacts(set, node.getLeft());
			findAllContacts(set, node.getRight());
		} else {
			for (int i = 0; i < buckets.size(); i++) {
				DHTRouterContactImpl contact = (DHTRouterContactImpl) buckets.get(i);
				set.add(contact);
			}
		}
	}

	protected void findAllContacts(List list, DHTRouterNodeImpl node) {
		// Log.d(TAG, "findAllContacts() is called...");
		List buckets = node.getBuckets();
		if (buckets == null) {
			findAllContacts(list, node.getLeft());
			findAllContacts(list, node.getRight());
		} else {
			for (int i = 0; i < buckets.size(); i++) {
				DHTRouterContactImpl contact = (DHTRouterContactImpl) buckets.get(i);
				list.add(contact);
			}
		}
		// Log.d(TAG, "list.size() = " + list.size());
	}

	public void seed() {
		// defer this a while to see how much refreshing is done by the normal DHT traffic
		seedInTicks = SEED_DELAY_TICKS;
	}

	protected void seedSupport() {
		
		/*
		Throwable t = new Throwable();
		t.printStackTrace();*/
		
		/*int count = SingleCounter2.getInstance().getAndIncreaseCount();
		Log.d(TAG, String.format("seedSupport() is called... #%d", count));
		if (count <= 2)
			print();*/
		
		// this function called once
		Log.d(TAG, String.format("seedSupport() is called..."));
		
		//Log.d(TAG, ">>> before...");
		//print();
		
		// refresh all buckets apart from closest neighbour
		byte[]	path = new byte[routerNodeId.length];
		List	ids = new ArrayList();
		try {
			monitor.enter();
			refreshNodes(ids, root, path, true, SEED_DELAY_PERIOD * 2);
		} finally {
			monitor.exit();
		}
		
		//Log.d(TAG, ">>> after...");
		//print();
		
		Log.d(TAG, ">>> ids.size() = " + ids.size());
		for (int i=0;i<ids.size();i++) {
			// random id in the node's range.
			/*String id = Util.toHexString((byte[])ids.get(i));
			if (id.length() > 8)
				id = id.substring(0,8)+"...";
			Log.d(TAG, String.format("ids[%d]=%s",i,id));*/
			requestLookup((byte[])ids.get(i), "Seeding DHT");
		}
	}

	protected void refreshNodes(
		List				nodesToRefresh,
		DHTRouterNodeImpl	node,
		byte[]				path,
		boolean				seeding,
		long				maxPermittedIdle)	// 0 -> don't check
	{
		// when seeding we don't do the smallest subtree
		if (seeding && node == smallestSubtree) {
			return;
		}
		if (maxPermittedIdle != 0) {
			if (node.getTimeSinceLastLookup() <= maxPermittedIdle) {
				return;
			}
		}
		if (node.getBuckets() != null) {
			// and we also don't refresh the bucket containing the router id when seeding
			if (seeding && node.containsRouterNodeID()) {
				return;
			}
			refreshNode(nodesToRefresh, node, path);
		}
		
		// synchronous refresh may result in this bucket being split
		// so we retest here to refresh sub-buckets as required
		if (node.getBuckets() == null) {
			int depth = node.getDepth();
			byte mask = (byte) (0x01 << (7 - (depth % 8)));
			path[depth / 8] = (byte) (path[depth / 8] | mask);
			refreshNodes(nodesToRefresh, node.getLeft(), path, seeding, maxPermittedIdle);
			path[depth / 8] = (byte) (path[depth / 8] & ~mask);
			refreshNodes(nodesToRefresh, node.getRight(), path, seeding, maxPermittedIdle);
		}
	}

	protected void refreshNode(
			List nodesToRefresh,
			DHTRouterNodeImpl node,
			byte[] path) {
		// pick a random id in the node's range.
		byte[] id = new byte[routerNodeId.length];
		random.nextBytes(id);
		int depth = node.getDepth();
		for (int i = 0; i < depth; i++) {
			byte mask = (byte) (0x01 << (7 - (i % 8)));
			boolean bit = ((path[i / 8] >> (7 - (i % 8))) & 0x01) == 1;
			if (bit) {
				id[i / 8] = (byte) (id[i / 8] | mask);
			} else {
				id[i / 8] = (byte) (id[i / 8] & ~mask);
			}
		}
		nodesToRefresh.add(id);
	}

	protected DHTRouterNodeImpl getSmallestSubtree() {
		return (smallestSubtree);
	}

	public void recordLookup(byte[] nodeId) {
		findNode(nodeId).setLastLookupTime();
	}

	public void	refreshIdleLeaves(long	idle_max) {
		
		Log.d(TAG, "refreshIdleLeaves() is called...");
		new Throwable().printStackTrace();
		
		// while we are synchronously refreshing the smallest subtree the tree can mutate underneath us
		// as new contacts are discovered. We NEVER merge things back together
		byte[]	path = new byte[routerNodeId.length];
		List	ids = new ArrayList();
		try {
			monitor.enter();
			refreshNodes(ids, root, path, false, idle_max);
		} finally {
			monitor.exit();
		}
		for (int i=0;i<ids.size();i++) {
			requestLookup((byte[])ids.get(i), "Idle leaf refresh");
		}
	}

	public boolean requestPing(byte[] node_id) {
		Object[] res = findContactSupport(node_id);
		DHTRouterContactImpl contact = (DHTRouterContactImpl) res[1];
		if (contact != null) {
			adapter.requestPing(contact);
			return (true);
		}
		return (false);
	}

	protected void requestPing(DHTRouterContactImpl contact) {
		if (suspended) {
			return;
		}
		// make sure we don't do the ping when synchronised
		DHTLog.log("DHTRouter: requestPing:" + DHTLog.getString(contact.getID()));
		if (contact == localContact) {
			Debug.out("pinging local contact");
		}
		try {
			monitor.enter();
			if (!outstandingPings.contains(contact)) {
				outstandingPings.add(contact);
			}
		} finally {
			monitor.exit();
		}
	}

	protected void dispatchPings() {
		if (outstandingPings.size() == 0) {
			return;
		}
		List pings;
		try {
			monitor.enter();
			pings = outstandingPings;
			outstandingPings = new ArrayList();
		} finally {
			monitor.exit();
		}
		if (suspended) {
			return;
		}
		for (int i = 0; i < pings.size(); i++) {
			adapter.requestPing((DHTRouterContactImpl) pings.get(i));
		}
	}

	protected void pingeroonies() {
		try {
			monitor.enter();
			DHTRouterNodeImpl node = root;
			LinkedList<DHTRouterNodeImpl> stack = new LinkedList<>();
			while (true) {
				List<DHTRouterContactImpl> buckets = node.getBuckets();
				if (buckets == null) {
					if (random.nextBoolean()) {
						stack.add(node.getRight());
						node = node.getLeft();
					} else {
						stack.add(node.getLeft());
						node = node.getRight();
					}
				} else {
					int 					maxFails 		= 0;
					DHTRouterContactImpl	maxFailsContact	= null;
					for (int i=0;i<buckets.size();i++) {
						DHTRouterContactImpl contact = (DHTRouterContactImpl)buckets.get(i);
						if (!contact.getPingOutstanding()) {
							int	fails = contact.getFailCount();
							if (fails > maxFails) {
								maxFails		= fails;
								maxFailsContact	= contact;
							}
						}
					}
					
					if (maxFailsContact != null) {
						requestPing(maxFailsContact);
						return;
					}
					
					if (stack.size() == 0) {
						break;
					}
					node = (DHTRouterNodeImpl)stack.removeLast();
				}
			}
		} finally {
			monitor.exit();
			dispatchPings();
		}
	}

	protected void requestNodeAdd(DHTRouterContactImpl contact) {

		if (suspended)
			return;

		// make sure we don't do the addition when synchronised
		DHTLog.log("DHTRouter: requestNodeAdd:" + DHTLog.getString(contact.getID()));
		if (contact == localContact) {
			Debug.out("adding local contact");
		}

		try {
			monitor.enter();
			if (!outstandingAdds.contains( contact)) {
				outstandingAdds.add(contact);
			}
		} finally {
			monitor.exit();
		}
	}

	protected void dispatchNodeAdds() {
		if (outstandingAdds.size() == 0) {
			return;
		}
		List	adds;
		try {
			monitor.enter();
			adds	= outstandingAdds;
			outstandingAdds = new ArrayList();
		} finally {
			monitor.exit();
		}
		if (suspended) {
			return;
		}
		for (int i=0;i<adds.size();i++) {
			adapter.requestAdd((DHTRouterContactImpl)adds.get(i));
		}
	}

	public byte[] refreshRandom() {
		byte[]	id = new byte[routerNodeId.length];
		random.nextBytes(id);
		requestLookup(id, "Random Refresh");
		return (id);
	}

	protected void requestLookup(byte[] id, String description) {
		DHTLog.log("DHTRouter: requestLookup:" + DHTLog.getString(id));
		adapter.requestLookup(id, description);
	}

	protected void getStatsSupport(
		long[]				stats_array,
		DHTRouterNodeImpl	node) {
		
		stats_array[DHTRouterStats.ST_NODES]++;
		List	buckets = node.getBuckets();
		if (buckets == null) {
			getStatsSupport( stats_array, node.getLeft());
			getStatsSupport( stats_array, node.getRight());
		} else {
			stats_array[DHTRouterStats.ST_LEAVES]++;
			stats_array[DHTRouterStats.ST_CONTACTS] += buckets.size();
			for (int i=0;i<buckets.size();i++) {
				DHTRouterContactImpl	contact = (DHTRouterContactImpl)buckets.get(i);
				if (contact.getFirstFailTime() > 0) {
					stats_array[DHTRouterStats.ST_CONTACTS_DEAD]++;
				} else if (contact.hasBeenAlive()) {
					stats_array[DHTRouterStats.ST_CONTACTS_LIVE]++;
				} else {
					stats_array[DHTRouterStats.ST_CONTACTS_UNKNOWN]++;
				}
			}
			List	rep = node.getReplacements();
			if (rep != null) {
				stats_array[DHTRouterStats.ST_REPLACEMENTS] += rep.size();
			}
		}
	}

	protected long[] getStatsSupport() {
		 /* number of nodes
		 * number of leaves
		 * number of contacts
		 * number of replacements
		 * number of live contacts
		 * number of unknown contacts
		 * number of dying contacts
		 */
		try {
			monitor.enter();
			long[]	res = new long[7];
			getStatsSupport(res, root);
			return (res);
		} finally {
			monitor.exit();
		}
	}

	protected void log(String str) {
		logger.log(str);
	}

	public void print() {
		
		//new Throwable().printStackTrace();
		
		try {
			monitor.enter();
			log("DHT: " + DHTLog.getString2(routerNodeId) + ", node count=" + getNodeCount()+ ", contacts=" + getContactCount());
			Log.d(TAG, String.format("DHT: %s, node count = %d, contacts=%d", DHTLog.getString2(routerNodeId), getNodeCount(), getContactCount()));
			root.print("", "");
		} finally {
			monitor.exit();
		}
	}

	public void destroy() {
		timerEvent.cancel();

		notifyDead();
	}
}
