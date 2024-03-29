/*
 * Created on 02-Dec-2005
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

package com.aelitis.azureus.core.diskmanager.access.impl;

import java.util.*;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.util.DirectByteBuffer;

import com.aelitis.azureus.core.diskmanager.access.DiskAccessController;
import com.aelitis.azureus.core.diskmanager.access.DiskAccessControllerStats;
import com.aelitis.azureus.core.diskmanager.access.DiskAccessRequest;
import com.aelitis.azureus.core.diskmanager.access.DiskAccessRequestListener;
import com.aelitis.azureus.core.diskmanager.cache.CacheFile;
import com.aelitis.azureus.core.stats.AzureusCoreStats;
import com.aelitis.azureus.core.stats.AzureusCoreStatsProvider;

public class DiskAccessControllerImpl
	implements DiskAccessController, AzureusCoreStatsProvider
{
	final DiskAccessControllerInstance	readDispatcher;
	final DiskAccessControllerInstance	writeDispatcher;

	public DiskAccessControllerImpl(
		String	_name,
		int		_max_read_threads,
		int		_max_read_mb,
		int 	_max_write_threads,
		int		_max_write_mb) {
		boolean	enable_read_aggregation 		= COConfigurationManager.getBooleanParameter("diskmanager.perf.read.aggregate.enable");
		int		read_aggregation_request_limit 	= COConfigurationManager.getIntParameter("diskmanager.perf.read.aggregate.request.limit", 4);
		int		read_aggregation_byte_limit 	= COConfigurationManager.getIntParameter("diskmanager.perf.read.aggregate.byte.limit", 64*1024);


		boolean	enable_write_aggregation 		= COConfigurationManager.getBooleanParameter("diskmanager.perf.write.aggregate.enable");
		int		write_aggregation_request_limit = COConfigurationManager.getIntParameter("diskmanager.perf.write.aggregate.request.limit", 8);
		int		write_aggregation_byte_limit 	= COConfigurationManager.getIntParameter("diskmanager.perf.write.aggregate.byte.limit", 128*1024);

		readDispatcher 	=
			new DiskAccessControllerInstance(
					_name + "/" + "read",
					enable_read_aggregation,
					read_aggregation_request_limit,
					read_aggregation_byte_limit,
					_max_read_threads,
					_max_read_mb);

		writeDispatcher 	=
			new DiskAccessControllerInstance(
					_name + "/" + "write",
					enable_write_aggregation,
					write_aggregation_request_limit,
					write_aggregation_byte_limit,
					_max_write_threads,
					_max_write_mb);

		Set	types = new HashSet();

		types.add(AzureusCoreStats.ST_DISK_READ_QUEUE_LENGTH);
		types.add(AzureusCoreStats.ST_DISK_READ_QUEUE_BYTES);
		types.add(AzureusCoreStats.ST_DISK_READ_REQUEST_COUNT);
		types.add(AzureusCoreStats.ST_DISK_READ_REQUEST_SINGLE);
		types.add(AzureusCoreStats.ST_DISK_READ_REQUEST_MULTIPLE);
		types.add(AzureusCoreStats.ST_DISK_READ_REQUEST_BLOCKS);
		types.add(AzureusCoreStats.ST_DISK_READ_BYTES_TOTAL);
		types.add(AzureusCoreStats.ST_DISK_READ_BYTES_SINGLE);
		types.add(AzureusCoreStats.ST_DISK_READ_BYTES_MULTIPLE);
		types.add(AzureusCoreStats.ST_DISK_READ_IO_TIME);
		types.add(AzureusCoreStats.ST_DISK_READ_IO_COUNT);

		types.add(AzureusCoreStats.ST_DISK_WRITE_QUEUE_LENGTH);
		types.add(AzureusCoreStats.ST_DISK_WRITE_QUEUE_BYTES);
		types.add(AzureusCoreStats.ST_DISK_WRITE_REQUEST_COUNT);
		types.add(AzureusCoreStats.ST_DISK_WRITE_REQUEST_BLOCKS);
		types.add(AzureusCoreStats.ST_DISK_WRITE_BYTES_TOTAL);
		types.add(AzureusCoreStats.ST_DISK_WRITE_BYTES_SINGLE);
		types.add(AzureusCoreStats.ST_DISK_WRITE_BYTES_MULTIPLE);
		types.add(AzureusCoreStats.ST_DISK_WRITE_IO_TIME);

		AzureusCoreStats.registerProvider(types, this);
	}

	public void updateStats(
		Set		types,
		Map		values) {
			//read

		if (types.contains( AzureusCoreStats.ST_DISK_READ_QUEUE_LENGTH)) {

			values.put( AzureusCoreStats.ST_DISK_READ_QUEUE_LENGTH, new Long( readDispatcher.getQueueSize()));
		}

		if (types.contains( AzureusCoreStats.ST_DISK_READ_QUEUE_BYTES)) {

			values.put( AzureusCoreStats.ST_DISK_READ_QUEUE_BYTES, new Long( readDispatcher.getQueuedBytes()));
		}

		if (types.contains( AzureusCoreStats.ST_DISK_READ_REQUEST_COUNT)) {

			values.put( AzureusCoreStats.ST_DISK_READ_REQUEST_COUNT, new Long( readDispatcher.getTotalRequests()));
		}

		if (types.contains( AzureusCoreStats.ST_DISK_READ_REQUEST_SINGLE)) {

			values.put( AzureusCoreStats.ST_DISK_READ_REQUEST_SINGLE, new Long( readDispatcher.getTotalSingleRequests()));
		}

		if (types.contains( AzureusCoreStats.ST_DISK_READ_REQUEST_MULTIPLE)) {

			values.put( AzureusCoreStats.ST_DISK_READ_REQUEST_MULTIPLE, new Long( readDispatcher.getTotalAggregatedRequests()));
		}

		if (types.contains( AzureusCoreStats.ST_DISK_READ_REQUEST_BLOCKS)) {

			values.put( AzureusCoreStats.ST_DISK_READ_REQUEST_BLOCKS, new Long( readDispatcher.getBlockCount()));
		}

		if (types.contains( AzureusCoreStats.ST_DISK_READ_BYTES_TOTAL)) {

			values.put( AzureusCoreStats.ST_DISK_READ_BYTES_TOTAL, new Long( readDispatcher.getTotalBytes()));
		}

		if (types.contains( AzureusCoreStats.ST_DISK_READ_BYTES_SINGLE)) {

			values.put( AzureusCoreStats.ST_DISK_READ_BYTES_SINGLE, new Long( readDispatcher.getTotalSingleBytes()));
		}

		if (types.contains( AzureusCoreStats.ST_DISK_READ_BYTES_MULTIPLE)) {

			values.put( AzureusCoreStats.ST_DISK_READ_BYTES_MULTIPLE, new Long( readDispatcher.getTotalAggregatedBytes()));
		}

		if (types.contains( AzureusCoreStats.ST_DISK_READ_IO_TIME)) {

			values.put( AzureusCoreStats.ST_DISK_READ_IO_TIME, new Long( readDispatcher.getIOTime()));
		}

		if (types.contains( AzureusCoreStats.ST_DISK_READ_IO_COUNT)) {

			values.put( AzureusCoreStats.ST_DISK_READ_IO_COUNT, new Long( readDispatcher.getIOCount()));
		}

			// write

		if (types.contains( AzureusCoreStats.ST_DISK_WRITE_QUEUE_LENGTH)) {

			values.put( AzureusCoreStats.ST_DISK_WRITE_QUEUE_LENGTH, new Long( writeDispatcher.getQueueSize()));
		}

		if (types.contains( AzureusCoreStats.ST_DISK_WRITE_QUEUE_BYTES)) {

			values.put( AzureusCoreStats.ST_DISK_WRITE_QUEUE_BYTES, new Long( writeDispatcher.getQueuedBytes()));
		}

		if (types.contains( AzureusCoreStats.ST_DISK_WRITE_REQUEST_COUNT)) {

			values.put( AzureusCoreStats.ST_DISK_WRITE_REQUEST_COUNT, new Long( writeDispatcher.getTotalRequests()));
		}

		if (types.contains( AzureusCoreStats.ST_DISK_WRITE_REQUEST_BLOCKS)) {

			values.put( AzureusCoreStats.ST_DISK_WRITE_REQUEST_BLOCKS, new Long( writeDispatcher.getBlockCount()));
		}

		if (types.contains( AzureusCoreStats.ST_DISK_WRITE_BYTES_TOTAL)) {

			values.put( AzureusCoreStats.ST_DISK_WRITE_BYTES_TOTAL, new Long( writeDispatcher.getTotalBytes()));
		}

		if (types.contains( AzureusCoreStats.ST_DISK_WRITE_BYTES_SINGLE)) {

			values.put( AzureusCoreStats.ST_DISK_WRITE_BYTES_SINGLE, new Long( writeDispatcher.getTotalSingleBytes()));
		}

		if (types.contains( AzureusCoreStats.ST_DISK_WRITE_BYTES_MULTIPLE)) {

			values.put( AzureusCoreStats.ST_DISK_WRITE_BYTES_MULTIPLE, new Long( writeDispatcher.getTotalAggregatedBytes()));
		}

		if (types.contains( AzureusCoreStats.ST_DISK_WRITE_IO_TIME)) {

			values.put( AzureusCoreStats.ST_DISK_WRITE_IO_TIME, new Long( writeDispatcher.getIOTime()));
		}

		if (types.contains( AzureusCoreStats.ST_DISK_WRITE_IO_COUNT)) {

			values.put( AzureusCoreStats.ST_DISK_WRITE_IO_COUNT, new Long( writeDispatcher.getIOCount()));
		}

	}

	public DiskAccessRequest
	queueReadRequest(
		CacheFile					file,
		long						offset,
		DirectByteBuffer			buffer,
		short						cache_policy,
		DiskAccessRequestListener	listener) {
		DiskAccessRequestImpl	request =
			new DiskAccessRequestImpl(
					file,
					offset,
					buffer,
					listener,
					DiskAccessRequestImpl.OP_READ,
					cache_policy);

		readDispatcher.queueRequest(request);

		return (request);
	}

	public DiskAccessRequest
	queueWriteRequest(
		CacheFile					file,
		long						offset,
		DirectByteBuffer			buffer,
		boolean						free_buffer,
		DiskAccessRequestListener	listener) {
		// System.out.println("write request: " + offset);

		DiskAccessRequestImpl	request =
			new DiskAccessRequestImpl(
					file,
					offset,
					buffer,
					listener,
					free_buffer?DiskAccessRequestImpl.OP_WRITE_AND_FREE:DiskAccessRequestImpl.OP_WRITE,
					CacheFile.CP_NONE);

		writeDispatcher.queueRequest(request);

		return (request);
	}

	public DiskAccessControllerStats
	getStats() {
		return (
			new DiskAccessControllerStats() {
				final long	read_total_req 		= readDispatcher.getTotalRequests();
				final long	read_total_bytes 	= readDispatcher.getTotalBytes();

				public long getTotalReadRequests() {
					return (read_total_req);
				}

				public long getTotalReadBytes() {
					return (read_total_bytes);
				}
			});
	}

	public String getString() {
		return ("read: " + readDispatcher.getString() + ", write: " + writeDispatcher.getString());
	}
}
