/*
 * File    : GlobalManagerStatsImpl.java
 * Created : 21-Oct-2003
 * By      : stuff
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

package org.gudy.azureus2.core3.global.impl;

/**
 * @author parg
 *
 */

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.global.*;
import org.gudy.azureus2.core3.util.*;
import org.gudy.azureus2.core3.util.SimpleTimer.TimerTickReceiver;

import com.aelitis.azureus.core.util.GeneralUtils;
import com.aelitis.azureus.core.util.average.MovingImmediateAverage;


public class
GlobalManagerStatsImpl
	implements GlobalManagerStats, TimerTickReceiver
{
	private final GlobalManagerImpl		manager;

	private long smooth_last_sent;
	private long smooth_last_received;

	private int current_smoothing_window 	= GeneralUtils.getSmoothUpdateWindow();
	private int current_smoothing_interval 	= GeneralUtils.getSmoothUpdateInterval();

	private MovingImmediateAverage smoothed_receive_rate 	= GeneralUtils.getSmoothAverage();
	private MovingImmediateAverage smoothed_send_rate 		= GeneralUtils.getSmoothAverage();


	private long total_data_bytes_received;
    private long total_protocol_bytes_received;

	private long totalDiscarded;

    private long total_data_bytes_sent;
    private long total_protocol_bytes_sent;

    private int	data_send_speed_at_close;

	private final Average data_receive_speed = Average.getInstance(1000, 10);  //average over 10s, update every 1000ms
    private final Average protocol_receive_speed = Average.getInstance(1000, 10);  //average over 10s, update every 1000ms
	private final Average data_receive_speed_no_lan = Average.getInstance(1000, 10);  //average over 10s, update every 1000ms
    private final Average protocol_receive_speed_no_lan = Average.getInstance(1000, 10);  //average over 10s, update every 1000ms

	private final Average data_send_speed = Average.getInstance(1000, 10);  //average over 10s, update every 1000ms
    private final Average protocol_send_speed = Average.getInstance(1000, 10);  //average over 10s, update every 1000ms
	private final Average data_send_speed_no_lan = Average.getInstance(1000, 10);  //average over 10s, update every 1000ms
    private final Average protocol_send_speed_no_lan = Average.getInstance(1000, 10);  //average over 10s, update every 1000ms


	protected GlobalManagerStatsImpl(
		GlobalManagerImpl	_manager) {
		manager = _manager;

		load();

		SimpleTimer.addTickReceiver(this);
	}

	protected void load() {
		data_send_speed_at_close	= COConfigurationManager.getIntParameter("globalmanager.stats.send.speed.at.close", 0);
	}

	protected void save() {
		COConfigurationManager.setParameter("globalmanager.stats.send.speed.at.close", getDataSendRate());
	}

	public int getDataSendRateAtClose() {
		return (data_send_speed_at_close);
	}

  			// update methods

	public void discarded(int length) {
		this.totalDiscarded += length;
	}

	public void dataBytesReceived(int length,boolean LAN) {
		total_data_bytes_received += length;
		if (!LAN) {
			data_receive_speed_no_lan.addValue(length);
		}
		data_receive_speed.addValue(length);
	}


	public void protocolBytesReceived(int length, boolean LAN) {
		total_protocol_bytes_received += length;
		if (!LAN) {
			protocol_receive_speed_no_lan.addValue(length);
		}
		protocol_receive_speed.addValue(length);
	}

	public void dataBytesSent(int length, boolean LAN) {
		total_data_bytes_sent += length;
		if (!LAN) {
			data_send_speed_no_lan.addValue(length);
		}
		data_send_speed.addValue(length);
	}

	public void protocolBytesSent(int length, boolean LAN) {
		total_protocol_bytes_sent += length;
		if (!LAN) {
			protocol_send_speed_no_lan.addValue(length);
		}
		protocol_send_speed.addValue(length);
	}

	public int getDataReceiveRate() {
		return (int)data_receive_speed.getAverage();
	}
	public int getDataReceiveRateNoLAN() {
		return (int)data_receive_speed_no_lan.getAverage();
	}
	public int getDataReceiveRateNoLAN(int average_period) {
		return (int)(average_period<=0?data_receive_speed_no_lan.getAverage():data_receive_speed_no_lan.getAverage(average_period));
	}
	public int getProtocolReceiveRate() {
		return (int)protocol_receive_speed.getAverage();
	}
	public int getProtocolReceiveRateNoLAN() {
		return (int)protocol_receive_speed_no_lan.getAverage();
	}
	public int getProtocolReceiveRateNoLAN(int average_period) {
		return (int)(average_period<=0?protocol_receive_speed_no_lan.getAverage():protocol_receive_speed_no_lan.getAverage(average_period));
	}

	public int getDataAndProtocolReceiveRate() {
		return ((int)( protocol_receive_speed.getAverage() + data_receive_speed.getAverage()));
	}

	public int getDataSendRate() {
		return (int)data_send_speed.getAverage();
	}
	public int getDataSendRateNoLAN() {
		return (int)data_send_speed_no_lan.getAverage();
	}
	public int getDataSendRateNoLAN(int average_period) {
		return (int)(average_period<=0?data_send_speed_no_lan.getAverage():data_send_speed_no_lan.getAverage(average_period));
	}

	public int getProtocolSendRate() {
		return (int)protocol_send_speed.getAverage();
	}
	public int getProtocolSendRateNoLAN() {
		return (int)protocol_send_speed_no_lan.getAverage();
	}
	public int getProtocolSendRateNoLAN(int average_period) {
		return (int)(average_period<=0?protocol_send_speed_no_lan.getAverage():protocol_send_speed_no_lan.getAverage(average_period));
	}

	public int getDataAndProtocolSendRate() {
		return ((int)( protocol_send_speed.getAverage() + data_send_speed.getAverage()));
	}

    public long getTotalDataBytesSent() {
    	return total_data_bytes_sent;
    }

    public long getTotalProtocolBytesSent() {
    	return total_protocol_bytes_sent;
    }


    public long getTotalDataBytesReceived() {
    	return total_data_bytes_received;
    }

    public long getTotalProtocolBytesReceived() {
    	return total_protocol_bytes_received;
    }


    public long getTotalDiscardedRaw() {
    	return totalDiscarded;
    }

    public long getTotalSwarmsPeerRate(boolean downloading, boolean seeding )
    {
    	return ( manager.getTotalSwarmsPeerRate(downloading,seeding));
    }

	public void tick(
		long		mono_now,
		int			tick_count) {
		if (tick_count % current_smoothing_interval == 0) {

			int	current_window = GeneralUtils.getSmoothUpdateWindow();

			if (current_smoothing_window != current_window) {

				current_smoothing_window 	= current_window;
				current_smoothing_interval	= GeneralUtils.getSmoothUpdateInterval();
				smoothed_receive_rate 		= GeneralUtils.getSmoothAverage();
				smoothed_send_rate 			= GeneralUtils.getSmoothAverage();
			}

			long	up 		= total_data_bytes_sent + total_protocol_bytes_sent;
			long	down 	= total_data_bytes_received + total_protocol_bytes_received;

			smoothed_send_rate.update(up - smooth_last_sent);
			smoothed_receive_rate.update(down - smooth_last_received);

			smooth_last_sent 		= up;
			smooth_last_received 	= down;
		}
	}

	public long getSmoothedSendRate() {
		return ((long)(smoothed_send_rate.getAverage()/current_smoothing_interval));
	}

	public long getSmoothedReceiveRate() {
		return ((long)(smoothed_receive_rate.getAverage()/current_smoothing_interval));
	}
}