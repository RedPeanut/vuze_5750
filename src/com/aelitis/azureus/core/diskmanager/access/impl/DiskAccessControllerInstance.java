/*
 * Created on 04-Dec-2005
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.torrent.TOTorrent;
import org.gudy.azureus2.core3.util.AESemaphore;
import org.gudy.azureus2.core3.util.AEThread2;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.DisplayFormatters;
import org.gudy.azureus2.core3.util.RandomUtils;
import org.gudy.azureus2.core3.util.SystemTime;

import com.aelitis.azureus.core.diskmanager.cache.CacheFile;

public class
DiskAccessControllerInstance
{
	private final int aggregation_request_limit;
	private final int aggregation_byte_limit;

	private final String		name;
	final boolean		enable_aggregation;

	final boolean		invert_threads	= !COConfigurationManager.getBooleanParameter("diskmanager.perf.queue.torrent.bias");

	final int	max_threads;
	private int	max_mb_queued;

	private final groupSemaphore	max_mb_sem;

	private long			request_bytes_queued;
	private long			requests_queued;

	private long			total_requests;
	private long			total_single_requests_made;
	private long			total_aggregated_requests_made;

	private long			total_bytes;
	private long			total_single_bytes;
	private long			total_aggregated_bytes;

	private long			io_time;
	private long			io_count;

	private final requestDispatcher[]	dispatchers;

	private long		last_check		= 0;

	private final Map			torrent_dispatcher_map	= new HashMap();

	private static final int REQUEST_NUM_LOG_CHUNK 		= 100;
	private static final int REQUEST_BYTE_LOG_CHUNK 	= 1024*1024;

	private int			next_request_num_log	= REQUEST_NUM_LOG_CHUNK;
	private long		next_request_byte_log	= REQUEST_BYTE_LOG_CHUNK;

	static final ThreadLocal		tls	=
		new ThreadLocal() {
			public Object initialValue() {
				return (null);
			}
		};

	public DiskAccessControllerInstance(
		String	_name,
		boolean	_enable_aggregation,
		int		_aggregation_request_limit,
		int		_aggregation_byte_limit,
		int		_max_threads,
		int		_max_mb) {
		name				= _name;

		enable_aggregation			= _enable_aggregation;
		aggregation_request_limit	= _aggregation_request_limit;
		aggregation_byte_limit		= _aggregation_byte_limit;

		max_mb_queued		= _max_mb;

		max_mb_sem 			= new groupSemaphore(max_mb_queued);
		max_threads			= _max_threads;

		dispatchers	= new requestDispatcher[invert_threads?1:max_threads];

		for (int i=0;i<dispatchers.length;i++) {
			dispatchers[i]	= new requestDispatcher(i);
		}
	}

	protected String getName() {
		return (name);
	}

	protected long getBlockCount() {
		return ( max_mb_sem.getBlockCount());
	}

	protected long getQueueSize() {
		return (requests_queued);
	}

	protected long getQueuedBytes() {
		return (request_bytes_queued);
	}

	protected long getTotalRequests() {
		return (total_requests);
	}

	protected long getTotalSingleRequests() {
		return (total_single_requests_made);
	}

	protected long getTotalAggregatedRequests() {
		return (total_aggregated_requests_made);
	}

	public long getTotalBytes() {
		return (total_bytes);
	}

	public long getTotalSingleBytes() {
		return (total_single_bytes);
	}

	public long getTotalAggregatedBytes() {
		return (total_aggregated_bytes);
	}

	public long getIOTime() {
		return (io_time);
	}

	public long getIOCount() {
		return (io_count);
	}

	protected void queueRequest(
		DiskAccessRequestImpl	request) {
		requestDispatcher	dispatcher;

		if (dispatchers.length == 1) {

			dispatcher = dispatchers[0];

		} else {

			synchronized(torrent_dispatcher_map) {

				long	now = System.currentTimeMillis();

				boolean	check = false;

				if (now - last_check > 60000 || now < last_check) {

					check		= true;
					last_check	= now;
				}

				if (check) {

					Iterator	it = torrent_dispatcher_map.values().iterator();

					while (it.hasNext()) {

						requestDispatcher	d = (requestDispatcher)it.next();

						long	last_active = d.getLastRequestTime();

						if (now - last_active > 60000) {

							it.remove();

						} else if (now < last_active) {

							d.setLastRequestTime(now);
						}
					}
				}

				TOTorrent	torrent = request.getFile().getTorrentFile().getTorrent();

				dispatcher = (requestDispatcher)torrent_dispatcher_map.get(torrent);

				if (dispatcher == null) {

					int	min_index 	= 0;
					int	min_size	= Integer.MAX_VALUE;

					for (int i=0;i<dispatchers.length;i++) {

						int	size = dispatchers[i].size();

						if (size == 0) {

							min_index = i;

							break;
						}

						if (size < min_size) {

							min_size 	= size;
							min_index	= i;
						}
					}

					dispatcher = dispatchers[min_index];

					torrent_dispatcher_map.put(torrent, dispatcher);
				}

				dispatcher.setLastRequestTime(now);
			}
		}

		dispatcher.queue(request);
	}

	protected void getSpaceAllowance(
		DiskAccessRequestImpl	request) {
		int	mb_diff;

		synchronized(torrent_dispatcher_map) {

			int	old_mb = (int)(request_bytes_queued/(1024*1024));

			request_bytes_queued += request.getSize();

			int	new_mb = (int)(request_bytes_queued/(1024*1024));

			mb_diff = new_mb - old_mb;

			if (mb_diff > max_mb_queued) {

					// if this request is bigger than the max allowed queueable then easiest
					// approach is to bump up the limit

				max_mb_sem.releaseGroup(mb_diff - max_mb_queued);

				max_mb_queued	= mb_diff;
			}

			requests_queued++;

			if (requests_queued >= next_request_num_log) {

				//System.out.println("DAC:" + name + ": requests = " + requests_queued);

				next_request_num_log += REQUEST_NUM_LOG_CHUNK;
			}

			if (request_bytes_queued >= next_request_byte_log) {

				//System.out.println("DAC:" + name + ": bytes = " + request_bytes_queued);

				next_request_byte_log += REQUEST_BYTE_LOG_CHUNK;
			}
		}

		if (mb_diff > 0) {

			max_mb_sem.reserveGroup(mb_diff);
		}
	}

	protected void releaseSpaceAllowance(
		DiskAccessRequestImpl	request) {
		int	mb_diff;

		synchronized(torrent_dispatcher_map) {

			int	old_mb = (int)(request_bytes_queued/(1024*1024));

			request_bytes_queued -= request.getSize();

			int	new_mb = (int)(request_bytes_queued/(1024*1024));

			mb_diff = old_mb - new_mb;

			requests_queued--;
		}

		if (mb_diff > 0) {

			max_mb_sem.releaseGroup(mb_diff);
		}
	}

	protected String getString() {
		return (
			name +
			",agg=" + enable_aggregation +
			",max_t=" + max_threads +
			",max_mb=" + max_mb_queued +
			",q_byte=" + DisplayFormatters.formatByteCountToKiBEtc(request_bytes_queued) +
			",q_req=" + requests_queued +
			",t_req=" + total_requests +
			",t_byte=" + DisplayFormatters.formatByteCountToKiBEtc(total_bytes) +
			",io=" + io_count);
	}

	protected class
	requestDispatcher
	{
		private final int			index;
		final AEThread2[]	threads		= new AEThread2[invert_threads?max_threads:1];
		private int			activeThreads;

		final LinkedList	requests 	= new LinkedList();

		final Map			request_map	= new HashMap();
		private long		last_request_map_tidy;

		final AESemaphore	requestSem		= new AESemaphore("DiskAccessControllerInstance:requestDispatcher:request");
		final AESemaphore	schedule_sem	= new AESemaphore("DiskAccessControllerInstance:requestDispatcher:schedule", 1);


		private long	lastRequestTime;

		protected
		requestDispatcher(
			int	_index) {
			index	= _index;
		}

		protected void queue(
			DiskAccessRequestImpl			request) {
			if (tls.get() != null) {
					// let recursive calls straight through
				synchronized(requests) {
						// stats not synced on the right object, but they're only stats...
					total_requests++;
					total_single_requests_made++;
					total_bytes	+= request.getSize();
					total_single_bytes += request.getSize();
				}
					// long	io_start = SystemTime.getHighPrecisionCounter();
				try {
					request.runRequest();
				} catch (Throwable e) {
						// actually, for recursive calls the time of this request will be
						// included in the timing of the call resulting in the recursion
						// long	io_end = SystemTime.getHighPrecisionCounter();
						// io_time += (io_end - io_start);
					io_count++;
					Debug.printStackTrace(e);
				}
			} else {
				getSpaceAllowance(request);
				synchronized(requests) {
					total_requests++;
					total_bytes	+= request.getSize();
					boolean	added = false;
					int	priority = request.getPriority();
					if (priority >= 0) {
						int	pos = 0;
						for (Iterator it = requests.iterator();it.hasNext();) {
							DiskAccessRequestImpl	r = (DiskAccessRequestImpl)it.next();
							if (r.getPriority() < priority) {
								requests.add(pos, request);
								added = true;
								break;
							}
							pos++;
						}
					}
					if (!added) {
						requests.add(request);
					}
					if (enable_aggregation) {
						Map	m = (Map)request_map.get( request.getFile());
						if (m == null) {
							m = new HashMap();
							request_map.put(request.getFile(), m);
						}
						m.put(new Long( request.getOffset()), request);
						long now = SystemTime.getCurrentTime();
						if (now < last_request_map_tidy || now - last_request_map_tidy > 30000) {
								// check for and discard manky old files from stopped/removed
								// downloads
							last_request_map_tidy = now;
							Iterator	it = request_map.entrySet().iterator();
							while (it.hasNext()) {
								Map.Entry	entry = (Map.Entry)it.next();
								if (((HashMap)entry.getValue()).size() == 0) {
									if (!((CacheFile)entry.getKey()).isOpen()) {
										it.remove();
									}
								}
							}
						}
					}
					// System.out.println("request queue: req = " + requests.size() + ", bytes = " + request_bytes_queued);
					requestSem.release();
					requestQueued();
				}
			}
		}

		protected long getLastRequestTime() {
			return (lastRequestTime);
		}

		protected void setLastRequestTime(long l) {
			lastRequestTime	= l;
		}

		protected int size() {
			return (requests.size());
		}

		protected void requestQueued() {
			// requests monitor held
			if (activeThreads < threads.length && (activeThreads == 0 || requests.size() > 32)) {
				for (int i=0;i<threads.length;i++) {
					if (threads[i] == null) {
						activeThreads++;
						final int threadIndex = i;
						threads[threadIndex] =
							new AEThread2("DiskAccessController:dispatch(" + getName() + ")[" + index + "/" + threadIndex + "]", true) {
								public void run() {
									tls.set(this);
									while (true) {
										DiskAccessRequestImpl	request		= null;
										List					aggregated 	= null;
										try {
											if (invert_threads) {
												schedule_sem.reserve();
											}
											if (requestSem.reserve( 30000)) {
												synchronized(requests) {
													request = (DiskAccessRequestImpl)requests.remove(0);
													if (enable_aggregation) {
														CacheFile	file = request.getFile();
														Map	file_map = (Map)request_map.get(file);
															// it is possible for the file_map to be null here due to
															// the fact that the entries can be zero sized even though
															// requests for the file are outstanding (as we key on non-unique
															// request.offset)
														if (file_map == null) {
															file_map = new HashMap();
														}
														file_map.remove(new Long( request.getOffset()));
														if (request.getPriority() < 0 && !request.isCancelled()) {
															DiskAccessRequestImpl	current = request;
															long	aggregated_bytes = 0;
															try {
																while (true) {
																	int	current_size = current.getSize();
																	long	end = current.getOffset() + current_size;
																		// doesn't matter if we remove from this and don't end up using it
																	DiskAccessRequestImpl next = (DiskAccessRequestImpl)file_map.remove(new Long( end));
																	if (	next == null || next.isCancelled() ||
																			!next.canBeAggregatedWith(request)) {
																		break;
																	}
																	requests.remove(next);
																	if (!requestSem.reserve( 30000)) {
																			// semaphore should already be > 0 as we've removed an element...
																		Debug.out("shouldn't happen");
																	}
																	if (aggregated == null) {
																		aggregated = new ArrayList(8);
																		aggregated.add(current);
																		aggregated_bytes += current_size;
																	}
																	aggregated.add(next);
																	aggregated_bytes += next.getSize();
																	if (aggregated.size() > aggregation_request_limit || aggregated_bytes >= aggregation_byte_limit) {
																		break;
																	}
																	current = next;
																}
															} finally {

																if (aggregated != null) {
																	total_aggregated_requests_made++;
																	/*
																	System.out.println(
																			"aggregated read: requests=" + aggregated.size() +
																			", size=" + aggregated_bytes +
																			", a_reqs=" + requests.size() +
																			", f_reqs=" + file_map.size());
																	*/
																} else {
																	total_single_requests_made++;
																}
															}
														}
													}
												}
											}
										} finally {
											if (invert_threads) {
												schedule_sem.release();
											}
										}
										try {
											long	io_start = SystemTime.getHighPrecisionCounter();
											if (aggregated != null) {
												DiskAccessRequestImpl[]	requests = (DiskAccessRequestImpl[])aggregated.toArray(new DiskAccessRequestImpl[ aggregated.size()]);
												try {
													DiskAccessRequestImpl.runAggregated(request, requests);
												} finally {
													long	io_end = SystemTime.getHighPrecisionCounter();
													io_time += (io_end - io_start);
													io_count++;
													for (int i=0;i<requests.length;i++) {
														DiskAccessRequestImpl	r = requests[i];
														total_aggregated_bytes += r.getSize();
														releaseSpaceAllowance(r);
													}
												}
											} else if (request != null) {
												try {
													request.runRequest();
												} finally {
													long	io_end = SystemTime.getHighPrecisionCounter();
													io_time += (io_end - io_start);
													io_count++;
													total_single_bytes += request.getSize();
													releaseSpaceAllowance(request);
												}
											} else {
												synchronized(requests) {
													if (requests.size() == 0) {
														threads[threadIndex] = null;
														activeThreads--;
														break;
													}
												}
											}
										} catch (Throwable e) {
											Debug.printStackTrace(e);
										}
									}
								}
							};
						threads[threadIndex].start();
						break;
					}
				}
			}
		}
	}

	protected static class groupSemaphore {
		private int value;
		private final List	waiters = new LinkedList();
		private long	blocks;
		
		protected groupSemaphore(int	_value) {
			value	= _value;
		}
		
		protected long getBlockCount() {
			return (blocks);
		}
		protected void reserveGroup(
			int	num) {
			mutableInteger	wait;
			synchronized(this) {
					// for fairness we only return immediately if we can and there are no waiters
				if (num <= value && waiters.size() == 0) {
					value -= num;
					return;
				} else {
					blocks++;
					wait = new mutableInteger(num - value);
					value	= 0;
					waiters.add(wait);
				}
			}
			wait.reserve();
		}
		protected void releaseGroup(
			int	num) {
			synchronized(this) {
				if (waiters.size() == 0) {
						// no waiters we just increment the value
					value += num;
				} else {
						// otherwise we share num out amongst the waiters in order
					while (waiters.size() > 0) {
						mutableInteger wait	= (mutableInteger)waiters.get(0);
						int	wait_num = wait.getValue();
						if (wait_num <= num) {
								// we've got enough now to release this waiter
							wait.release();
							waiters.remove(0);
							num -= wait_num;
						} else {
							wait.setValue(wait_num - num);
							num	= 0;
							break;
						}
					}
						// if we have any left over then save it
					value = num;
				}
			}
		}
		protected static class
		mutableInteger
		{
			private int		i;
			private boolean	released;
			protected
			mutableInteger(
				int	_i) {
				i	= _i;
			}
			protected int getValue() {
				return (i);
			}
			protected void setValue(
				int	_i) {
				i	= _i;
			}
			protected void release() {
				synchronized(this) {
					released	= true;
					notify();
				}
			}
			protected void reserve() {
				synchronized(this) {
					if (released) {
						return;
					}
					try {
						int	spurious_count = 0;
						while (true) {
							wait();
							if (released) {
								break;
							} else {
								spurious_count++;
								if (spurious_count > 1024) {
									Debug.out("DAC::mutableInteger: spurious wakeup limit exceeded");
									throw (new RuntimeException("die die die"));
								} else {
									Debug.out("DAC::mutableInteger: spurious wakeup, ignoring");
								}
							}
						}
					} catch (InterruptedException e) {
						throw (new RuntimeException("Semaphore: operation interrupted"));
					}
				}
			}
		}
	}

	public static void main(
		String[]	args) {
		final groupSemaphore	sem = new groupSemaphore(9);

		for (int i=0;i<10;i++) {

			new Thread() {
				public void run() {
					int	count = 0;

					while (true) {

						int	group =RandomUtils.generateRandomIntUpto(10);

						System.out.println(Thread.currentThread().getName() + " reserving " + group);

						sem.reserveGroup(group);

						try {
							Thread.sleep(5 + RandomUtils.generateRandomIntUpto(5));

						} catch (Throwable e) {
						}

						sem.releaseGroup(group);

						count++;

						if (count %100 == 0) {

							System.out.println(Thread.currentThread().getName() + ": " + count + " ops");
						}
					}
				}
			}.start();
		}
	}
}
