/*
 * Created on Dec 1, 2008
 * Created by Paul Gardner
 *
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.	See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA	02111-1307, USA.
 */


package com.aelitis.azureus.core.networkmanager.impl;

import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.SystemTime;

import com.aelitis.azureus.core.networkmanager.NetworkManager;

public class ByteBucketMT implements ByteBucket {
	
	private int rate;
	private int burst_rate;
	private volatile long availBytes;
	private volatile long prev_update_time;

	private volatile boolean frozen;

	/**
	 * Create a new byte-bucket with the given byte fill (guaranteed) rate.
	 * Burst rate is set to default 1.2X of given fill rate.
	 * @param rate_bytes_per_sec fill rate
	 */
	public ByteBucketMT(int rate_bytes_per_sec) {
		this(rate_bytes_per_sec, rate_bytes_per_sec + (rate_bytes_per_sec/5));
	}

	/**
	 * Create a new byte-bucket with the given byte fill (guaranteed) rate
	 * and the given burst rate.
	 * @param rate_bytes_per_sec fill rate
	 * @param burst_rate max rate
	 */
	private ByteBucketMT(int rate_bytes_per_sec, int burst_rate) {
		this.rate = rate_bytes_per_sec;
		this.burst_rate = burst_rate;
		availBytes = 0; //start bucket empty
		prev_update_time = SystemTime.getMonotonousTime();
		ensureByteBucketMinBurstRate();
	}


	/**
	 * Get the number of bytes currently available for use.
	 * @return number of free bytes
	 */
	public int getAvailableByteCount() {
		if (availBytes < NetworkManager.UNLIMITED_RATE) {
			updateAvailByteCount();
		}
		int res = (int)availBytes;
		if (res < 0) {
			res = 0;
		}
		return (res);
	}


	/**
	 * Update the bucket with the number of bytes just used.
	 * @param bytes_used
	 */
	public void setBytesUsed(int bytes_used) {
		if (availBytes >= NetworkManager.UNLIMITED_RATE) {
			return;
		}

		availBytes -= bytes_used;
		//if (avail_bytes < 0) Debug.out("avail_bytes < 0: " + avail_bytes);
	}


	/**
	 * Get the configured fill rate.
	 * @return guaranteed rate in bytes per sec
	 */
	public int getRate() {	return rate;	}


	/**
	 * Get the configured burst rate.
	 * @return burst rate in bytes per sec
	 */
	public int getBurstRate() {	return burst_rate;	}


	/**
	 * Set the current fill/guaranteed rate, with a burst rate of 1.2X the given rate.
	 * @param rate_bytes_per_sec
	 */
	public void setRate(int rate_bytes_per_sec) {
		setRate( rate_bytes_per_sec, rate_bytes_per_sec + (rate_bytes_per_sec/5));
	}

	public void
	setFrozen(
		boolean	f )
	{
		frozen = f;
	}

	/**
	 * Set the current fill/guaranteed rate, along with the burst rate.
	 * @param rate_bytes_per_sec
	 * @param burst_rate
	 */
	public void setRate(int rate_bytes_per_sec, int burst_rate) {
		if (rate_bytes_per_sec < 0) {
			Debug.out("rate_bytes_per_sec [" +rate_bytes_per_sec+ "] < 0");
			rate_bytes_per_sec = 0;
		}
		if (burst_rate < rate_bytes_per_sec) {
			Debug.out("burst_rate [" +burst_rate+ "] < rate_bytes_per_sec [" +rate_bytes_per_sec+ "]");
			burst_rate = rate_bytes_per_sec;
		}
		this.rate = rate_bytes_per_sec;
		this.burst_rate = burst_rate;
		if (availBytes > burst_rate) {
			availBytes = burst_rate;
		}
		ensureByteBucketMinBurstRate();
	}


	private void updateAvailByteCount() {
		if (frozen) {
			return;
		}
		synchronized(this) {
			final long now =SystemTime.getMonotonousTime();
			if (prev_update_time <now) {
				availBytes +=((now -prev_update_time) * rate) / 1000;
				prev_update_time =now;
				if (availBytes > burst_rate) availBytes = burst_rate;
				else if (availBytes < 0) {
					//Debug.out("ERROR: avail_bytes < 0: " + avail_bytes);
				}
			}
		}
	}


	/**
	 * Make sure the bucket's burst rate is at least MSS-sized,
	 * otherwise it will never allow a full packet's worth of data.
	 */
	private void ensureByteBucketMinBurstRate() {
		int mss = NetworkManager.getMinMssSize();
		if (burst_rate < mss) {	//oops, this won't ever allow a full packet
			burst_rate = mss;	//so increase the max byte size
		}
	}

}
