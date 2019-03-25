/*
 * Created on 31-Jan-2005
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

package com.aelitis.azureus.core.dht.control.impl;

import org.gudy.azureus2.core3.util.*;

import com.aelitis.azureus.core.dht.control.DHTControlStats;
import com.aelitis.azureus.core.dht.db.DHTDBStats;
import com.aelitis.azureus.core.dht.router.*;
import com.aelitis.azureus.core.dht.transport.*;

/**
 * @author parg
 *
 */

public class DHTControlStatsImpl
	implements DHTTransportFullStats, DHTControlStats
{
	private static final int	UPDATE_INTERVAL	= 10*1000;
	private static final int	UPDATE_PERIOD	= 120;

	private final DHTControlImpl		control;


	private final Average	packetsInAverage 	= Average.getInstance(UPDATE_INTERVAL, UPDATE_PERIOD);
	private final Average	packetsOutAverage 	= Average.getInstance(UPDATE_INTERVAL, UPDATE_PERIOD);
	private final Average	bytesInAverage 		= Average.getInstance(UPDATE_INTERVAL, UPDATE_PERIOD);
	private final Average	bytesOutAverage 	= Average.getInstance(UPDATE_INTERVAL, UPDATE_PERIOD);

	private DHTTransportStats	transportSnapshot;
	private long[]				routerSnapshot;
	private int[]				valueDetailsSnapshot;

	protected DHTControlStatsImpl(DHTControlImpl _control) {
		control	= _control;
		transportSnapshot	= control.getTransport().getStats().snapshot();
		routerSnapshot		= control.getRouter().getStats().getStats();
		SimpleTimer.addPeriodicEvent(
			"DHTCS:update",
			UPDATE_INTERVAL,
			new TimerEventPerformer() {
				public void perform(TimerEvent event) {
					update();
					control.poke();
				}
			});
	}

	protected void update() {
		DHTTransport transport 	= control.getTransport();
		DHTTransportStats t_stats = transport.getStats().snapshot();
		packetsInAverage.addValue(
				t_stats.getPacketsReceived() - transportSnapshot.getPacketsReceived());
		packetsOutAverage.addValue(
				t_stats.getPacketsSent() - transportSnapshot.getPacketsSent());
		bytesInAverage.addValue(
				t_stats.getBytesReceived() - transportSnapshot.getBytesReceived());
		bytesOutAverage.addValue(
				t_stats.getBytesSent() - transportSnapshot.getBytesSent());
		transportSnapshot	= t_stats;
		routerSnapshot	= control.getRouter().getStats().getStats();
		valueDetailsSnapshot = null;
	}

	public long getTotalBytesReceived() {
		return (transportSnapshot.getBytesReceived());
	}

	public long getTotalBytesSent() {
		return (transportSnapshot.getBytesSent());
	}

	public long getTotalPacketsReceived() {
		return (transportSnapshot.getPacketsReceived());
	}

	public long getTotalPacketsSent() {
		return (transportSnapshot.getPacketsSent());
	}

	public long getTotalPingsReceived() {
		return (transportSnapshot.getPings()[DHTTransportStats.STAT_RECEIVED]);
	}
	
	public long getTotalFindNodesReceived() {
		return (transportSnapshot.getFindNodes()[DHTTransportStats.STAT_RECEIVED]);
	}
	
	public long getTotalFindValuesReceived() {
		return (transportSnapshot.getFindValues()[DHTTransportStats.STAT_RECEIVED]);
	}
	
	public long getTotalStoresReceived() {
		return (transportSnapshot.getStores()[DHTTransportStats.STAT_RECEIVED]);
	}
	
	public long getTotalKeyBlocksReceived() {
		return (transportSnapshot.getKeyBlocks()[DHTTransportStats.STAT_RECEIVED]);
	}

	// averages
	public long getAverageBytesReceived() {
		return ( bytesInAverage.getAverage());
	}

	public long getAverageBytesSent() {
		return ( bytesOutAverage.getAverage());
	}

	public long getAveragePacketsReceived() {
		return (packetsInAverage.getAverage());
	}

	public long getAveragePacketsSent() {
		return ( packetsOutAverage.getAverage());
	}

	public long getIncomingRequests() {
		return ( transportSnapshot.getIncomingRequests());
	}
		// DB

	protected int[]
	getValueDetails() {
		int[] vd = valueDetailsSnapshot;

		if (vd == null) {

			vd = control.getDataBase().getStats().getValueDetails();

			valueDetailsSnapshot = vd;
		}

		return (vd);
	}

	public long getDBValuesStored() {
		int[]	vd = getValueDetails();

		return ( vd[ DHTDBStats.VD_VALUE_COUNT ]);
	}

	public long getDBKeyCount() {
		return ( control.getDataBase().getStats().getKeyCount());
	}

	public long getDBValueCount() {
		return ( control.getDataBase().getStats().getValueCount());
	}

	public long getDBKeysBlocked() {
		return ( control.getDataBase().getStats().getKeyBlockCount());
	}

	public long getDBKeyDivSizeCount() {
		int[]	vd = getValueDetails();

		return ( vd[ DHTDBStats.VD_DIV_SIZE ]);
	}

	public long getDBKeyDivFreqCount() {
		int[]	vd = getValueDetails();

		return ( vd[ DHTDBStats.VD_DIV_FREQ ]);
	}

	public long getDBStoreSize() {
		return ( control.getDataBase().getStats().getSize());
	}

		// Router

	public long getRouterNodes() {
		return ( routerSnapshot[DHTRouterStats.ST_NODES]);
	}

	public long getRouterLeaves() {
		return ( routerSnapshot[DHTRouterStats.ST_LEAVES]);
	}

	public long getRouterContacts() {
		return ( routerSnapshot[DHTRouterStats.ST_CONTACTS]);
	}

	public long getRouterUptime() {
		return ( control.getRouterUptime());
	}

	public int getRouterCount() {
		return ( control.getRouterCount());
	}

	public String getVersion() {
		return (Constants.AZUREUS_VERSION);
	}

	public long getEstimatedDHTSize() {
		return (control.getEstimatedDHTSize());
	}

	public String getString() {
		return (	"transport:" +
				getTotalBytesReceived() + "," +
				getTotalBytesSent() + "," +
				getTotalPacketsReceived() + "," +
				getTotalPacketsSent() + "," +
				getTotalPingsReceived() + "," +
				getTotalFindNodesReceived() + "," +
				getTotalFindValuesReceived() + "," +
				getTotalStoresReceived() + "," +
				getTotalKeyBlocksReceived() + "," +
				getAverageBytesReceived() + "," +
				getAverageBytesSent() + "," +
				getAveragePacketsReceived() + "," +
				getAveragePacketsSent() + "," +
				getIncomingRequests() +
				",router:" +
				getRouterNodes() + "," +
				getRouterLeaves() + "," +
				getRouterContacts() +
				",database:" +
				getDBKeyCount() + ","+
				getDBValueCount() + ","+
				getDBValuesStored() + ","+
				getDBStoreSize() + ","+
				getDBKeyDivFreqCount() + ","+
				getDBKeyDivSizeCount() + ","+
				getDBKeysBlocked()+
				",version:" + getVersion()+","+
				getRouterUptime() + ","+
				getRouterCount());
	}
}
