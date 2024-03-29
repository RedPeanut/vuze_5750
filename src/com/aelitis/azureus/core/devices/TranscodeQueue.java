/*
 * Created on Feb 6, 2009
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */


package com.aelitis.azureus.core.devices;

import org.gudy.azureus2.plugins.disk.DiskManagerFileInfo;

public interface
TranscodeQueue
{
	public TranscodeJob add(
		TranscodeTarget			target,
		TranscodeProfile		profile,
		DiskManagerFileInfo		file,
		boolean					add_stopped )

		throws TranscodeException;

	public TranscodeJob add(
		TranscodeTarget			target,
		TranscodeProfile		profile,
		DiskManagerFileInfo		file,
		int						transcode_requirement,		// from target.TRANSCODE_<x>
		boolean					add_stopped )

		throws TranscodeException;

	public TranscodeJob[]
	getJobs();

	public void pause();

	public boolean isPaused();

	public void resume();

	public int getJobCount();

	public TranscodeJob getCurrentJob();

	public boolean isTranscoding();

	public long getMaxBytesPerSecond();

	public void setMaxBytesPerSecond(
		long		max);

	public void addListener(
		TranscodeQueueListener		listener);

	public void removeListener(
		TranscodeQueueListener		listener);

	public void addActionListener(
		TranscodeQueueActionListener		listener);

	public void removeActionListener(
		TranscodeQueueActionListener		listener);
}
