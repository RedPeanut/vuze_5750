/**
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

package com.aelitis.azureus.core.peermanager.control.impl;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.config.ParameterListener;
import org.gudy.azureus2.core3.disk.DiskManager;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.SystemTime;

import com.aelitis.azureus.core.peermanager.control.SpeedTokenDispenser;
import com.aelitis.azureus.core.util.FeatureAvailability;

public class
SpeedTokenDispenserPrioritised
	implements SpeedTokenDispenser
{
	// crude TBF implementation
	private int		rateKiB;
	{
		COConfigurationManager.addAndFireParameterListeners(new String[] { "Max Download Speed KBs", "Use Request Limiting" }, new ParameterListener() {
			public void parameterChanged(String parameterName) {
				rateKiB = COConfigurationManager.getIntParameter("Max Download Speed KBs");
				if (!COConfigurationManager.getBooleanParameter("Use Request Limiting") || !FeatureAvailability.isRequestLimitingEnabled())
					rateKiB = 0;

					// sanity check
				if (rateKiB < 0) {
					rateKiB = 0;
				}

				threshold = Math.max(BUCKET_THRESHOLD_FACTOR*rateKiB, BUCKET_THRESHOLD_LOWER_BOUND);
				lastTime = currentTime - 1; // shortest possible delta
				refill(); // cap buffer to threshold in case something accumulated
			}
		});
	}
	private long	threshold;
	private long	bucket		= 0;
	private long	lastTime	= SystemTime.getCurrentTime();
	private long	currentTime;

	public void update(long newTime) {
		currentTime = newTime;
	}

	// allow at least 2 outstanding requests
	private static final int	BUCKET_THRESHOLD_LOWER_BOUND	= 2 * DiskManager.BLOCK_SIZE;
	// time (in seconds) at max speed until the buffer is empty: too low = latency issues; too high = overshooting for too long
	private static final int	BUCKET_RESPONSE_TIME			= 1;
	// n KiB buffer per 1KiB/s speed, that should be roughly n seconds max response time
	private static final int	BUCKET_THRESHOLD_FACTOR			= 1024 * BUCKET_RESPONSE_TIME;

	public void refill() {
		if (lastTime == currentTime || rateKiB == 0)
			return;

		if (lastTime > currentTime) {
			lastTime = currentTime;
			return;
		}

		if (bucket < 0) {
			Debug.out("Bucket is more than empty! - " + bucket);
			bucket = 0;
		}
		long delta = currentTime - lastTime;
		lastTime = currentTime;
		// upcast to long since we might exceed int-max when rate and delta are
		// large enough; then downcast again...
		long tickDelta = ( rateKiB * 1024L * delta) / 1000;
		//System.out.println("threshold:" + threshold + " update: " + bucket + " time delta:" + delta);
		bucket += tickDelta;
		if (bucket > threshold)
			bucket = threshold;
	}

	public int dispense(int numberOfChunks, int chunkSize) {
		if (rateKiB == 0)
			return numberOfChunks;
		if (chunkSize > bucket)
			return 0;
		if (chunkSize * numberOfChunks <= bucket) {
			bucket -= chunkSize * numberOfChunks;
			return numberOfChunks;
		}
		int availableChunks = (int)(bucket / chunkSize);
		bucket -= chunkSize * availableChunks;
		return availableChunks;
	}

	public void returnUnusedChunks(int unused, int chunkSize) {
		bucket += unused * chunkSize;
	}

	public int peek(int chunkSize) {
		if (rateKiB != 0)
			return (int)(bucket / chunkSize);
		else
			return Integer.MAX_VALUE;
	}
}
