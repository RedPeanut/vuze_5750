/*
 * Created on 12-Jan-2005
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

import hello.util.Util;

import org.gudy.azureus2.core3.util.SystemTime;

import com.aelitis.azureus.core.dht.control.DHTControlContact;
import com.aelitis.azureus.core.dht.impl.DHTLog;
import com.aelitis.azureus.core.dht.router.DHTRouterContact;
import com.aelitis.azureus.core.dht.router.DHTRouterContactAttachment;
import com.aelitis.azureus.core.dht.transport.DHTTransportContact;
import com.aelitis.azureus.core.dht.transport.udp.impl.DHTTransportUDPContactImpl;

/**
 * @author parg
 *
 */

public class DHTRouterContactImpl implements DHTRouterContact {
	
	private final byte[]				nodeId;
	private DHTRouterContactAttachment	attachment;

	private boolean		hasBeenAlive;
	private boolean		pingOutstanding;
	private int			failCount;
	private long		firstAliveTime;
	private long		firstFailOrLastAliveTime;
	private long		lastAddedTime;

	private boolean		isBucketEntry;

	public String toString() {
		String nodeId = Util.toHexString(this.nodeId);
		if (nodeId.length() > 8)
			nodeId = nodeId.substring(0,8)+"...";
		String address = "";
		int nodeStatus = -1;
		if (attachment != null) {
			DHTTransportContact t_contact = ((DHTControlContact) this.attachment).getTransportContact();
			address = t_contact.getAddress().toString();
			if (t_contact instanceof DHTTransportUDPContactImpl)
				nodeStatus = ((DHTTransportUDPContactImpl) t_contact).getNodeStatus();
		}
		return String.format("nodeId= %s,addr=%s,sttus=%d", nodeId, address, nodeStatus);
	}
	
	protected DHTRouterContactImpl(
		byte[]							_node_id,
		DHTRouterContactAttachment		_attachment,
		boolean							_has_been_alive) {
		
		nodeId			= _node_id;
		attachment		= _attachment;
		hasBeenAlive	= _has_been_alive;
		if (attachment != null) {
			attachment.setRouterContact(this);
		}
		isBucketEntry = false;
	}

	public byte[] getID() {
		return (nodeId);
	}

	public DHTRouterContactAttachment getAttachment() {
		return (attachment);
	}

	protected void setAttachment(DHTRouterContactAttachment _attachment) {
		attachment = _attachment;
	}

	public void setAlive() {
		failCount					= 0;
		firstFailOrLastAliveTime	= SystemTime.getCurrentTime();
		hasBeenAlive				= true;
		if (firstAliveTime == 0) {
			firstAliveTime = firstFailOrLastAliveTime;
		}
	}

	public boolean hasBeenAlive() {
		return (hasBeenAlive);
	}

	public boolean isAlive() {
		return (hasBeenAlive && failCount == 0);
	}

	public boolean isFailing() {
		return (failCount > 0);
	}

	protected int getFailCount() {
		return (failCount);
	}

	public long getTimeAlive() {
		if (failCount > 0 || firstAliveTime == 0) {
			return (0);
		}
		return (SystemTime.getCurrentTime() - firstAliveTime);
	}

	protected boolean setFailed() {
		failCount++;
		if (failCount == 1) {
			firstFailOrLastAliveTime = SystemTime.getCurrentTime();
		}
		return ( hasFailed());
	}

	protected boolean hasFailed() {
		if (hasBeenAlive) {
			return ( failCount >= attachment.getMaxFailForLiveCount());
		} else {
			return ( failCount >= attachment.getMaxFailForUnknownCount());
		}
	}

	protected long getFirstFailTime() {
		return (failCount==0?0:firstFailOrLastAliveTime);
	}

	protected long getLastAliveTime() {
		return (failCount==0?firstFailOrLastAliveTime:0);
	}

	protected long getFirstFailOrLastAliveTime() {
		return (firstFailOrLastAliveTime);
	}

	protected long getFirstAliveTime() {
		return (firstAliveTime);
	}

	protected long getLastAddedTime() {
		return (lastAddedTime);
	}

	protected void setLastAddedTime(
		long	l) {
		lastAddedTime	= l;
	}

	protected void setPingOutstanding(
		boolean	b) {
		pingOutstanding = b;
	}

	protected boolean getPingOutstanding() {
		return (pingOutstanding);
	}

	public String getString() {
		return (DHTLog.getString2(nodeId) + "[hba=" + (hasBeenAlive?"Y":"N") +
				",bad=" + failCount +
				",OK=" + getTimeAlive() + "]");
	}

	protected void getString(StringBuilder sb) {
		sb.append(DHTLog.getString2(nodeId));
		sb.append("[hba=");
		sb.append(hasBeenAlive?"Y":"N");
		sb.append(",bad=");
		sb.append(failCount);
		sb.append(",OK=");
		sb.append(getTimeAlive());
		sb.append("]");
	}

	public boolean isBucketEntry() {
		return isBucketEntry;
	}

	public void setBucketEntry() {
		isBucketEntry = true;
	}

	public boolean isReplacement() {
		return !isBucketEntry;
	}

	public void setReplacement() {
		isBucketEntry = false;
	}
}
