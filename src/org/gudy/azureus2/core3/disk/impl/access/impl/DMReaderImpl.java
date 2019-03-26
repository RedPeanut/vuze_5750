/*
 * Created on 31-Jul-2004
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

package org.gudy.azureus2.core3.disk.impl.access.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.gudy.azureus2.core3.disk.DiskManagerReadRequest;
import org.gudy.azureus2.core3.disk.DiskManagerReadRequestListener;
import org.gudy.azureus2.core3.disk.impl.DiskManagerHelper;
import org.gudy.azureus2.core3.disk.impl.access.DMReader;
import org.gudy.azureus2.core3.disk.impl.piecemapper.DMPieceList;
import org.gudy.azureus2.core3.disk.impl.piecemapper.DMPieceMapEntry;
import org.gudy.azureus2.core3.logging.LogEvent;
import org.gudy.azureus2.core3.logging.LogIDs;
import org.gudy.azureus2.core3.logging.Logger;
import org.gudy.azureus2.core3.util.AEMonitor;
import org.gudy.azureus2.core3.util.AESemaphore;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.DirectByteBuffer;
import org.gudy.azureus2.core3.util.DirectByteBufferPool;
import org.gudy.azureus2.core3.util.SystemTime;

import com.aelitis.azureus.core.diskmanager.access.DiskAccessController;
import com.aelitis.azureus.core.diskmanager.access.DiskAccessRequest;
import com.aelitis.azureus.core.diskmanager.access.DiskAccessRequestListener;
import com.aelitis.azureus.core.diskmanager.cache.CacheFile;

import hello.util.Log;
import hello.util.SingleCounter9;

/**
 * @author parg
 *
 */
public class DMReaderImpl implements DMReader {
	
	private static String TAG = DMReaderImpl.class.getSimpleName();
	
	private static final LogIDs LOGID = LogIDs.DISK;

	final DiskManagerHelper		diskManager;
	final DiskAccessController	diskAccess;

	private int						asyncReads;
	final Set						readRequests		= new HashSet();
	final AESemaphore				asyncReadSem = new AESemaphore("DMReader:asyncReads");

	private boolean					started;
	private boolean					stopped;

	private long					totalReadOps;
	private long					totalReadBytes;

	protected final AEMonitor	thisMon	= new AEMonitor("DMReader");

	public DMReaderImpl(DiskManagerHelper _diskManager) {
		diskManager		= _diskManager;
		diskAccess		= diskManager.getDiskAccessController();
	}

	public void start() {
		try {
			thisMon.enter();
			if (started) {
				throw (new RuntimeException("can't start twice"));
			}
			if (stopped) {
				throw (new RuntimeException("already been stopped"));
			}
			started	= true;
		} finally {
			thisMon.exit();
		}
	}

	public void stop() {
		int	read_wait;
		try {
			thisMon.enter();
			if (stopped || !started) {
				return;
			}
			stopped	= true;
			read_wait	= asyncReads;
		} finally {
			thisMon.exit();
		}
		long	log_time 		= SystemTime.getCurrentTime();
		for (int i=0;i<read_wait;i++) {
			long	now = SystemTime.getCurrentTime();
			if (now < log_time) {
				log_time = now;
			} else {
				if (now - log_time > 1000) {
					log_time	= now;
					if (Logger.isEnabled()) {
						Logger.log(new LogEvent(diskManager, LOGID, "Waiting for reads to complete - " + (read_wait-i) + " remaining" ));
					}
				}
			}
			asyncReadSem.reserve();
		}
	}

	public DiskManagerReadRequest createReadRequest(
		int pieceNumber,
		int offset,
		int length) {
		return (new DiskManagerReadRequestImpl(pieceNumber, offset, length));
	}

	public boolean hasOutstandingReadRequestForPiece(int pieceNumber) {
		try {
			thisMon.enter();
			Iterator	it = readRequests.iterator();
			while (it.hasNext()) {
				DiskManagerReadRequest	request = (DiskManagerReadRequest)((Object[])it.next())[0];
				if (request.getPieceNumber() == pieceNumber) {
					return (true);
				}
			}
			return (false);
		} finally {
			thisMon.exit();
		}
	}

	public long[] getStats() {
		return (new long[]{ totalReadOps, totalReadBytes });
	}

	// returns null if the read can't be performed

	public DirectByteBuffer readBlock(
		int pieceNumber,
		int offset,
		int length) {
		DiskManagerReadRequest	request = createReadRequest(pieceNumber, offset, length);
		final AESemaphore	sem = new AESemaphore("DMReader:readBlock");
		final DirectByteBuffer[]	result = {null};
		readBlock(
			request,
			new DiskManagerReadRequestListener() {
				  public void readCompleted(
				  		DiskManagerReadRequest 	request,
						DirectByteBuffer 		data) {
					  result[0]	= data;
					  sem.release();
				  }
				  
				  public void readFailed(
				  		DiskManagerReadRequest 	request,
						Throwable		 		cause) {
					  sem.release();
				  }
				  
				  public int getPriority() {
					  return (-1);
				  }
				  
				  public void requestExecuted(long bytes) {
				  }
			}
		);
		sem.reserve();
		return (result[0]);
	}

	public void readBlock(
		final DiskManagerReadRequest			request,
		final DiskManagerReadRequestListener	_listener) {
		
		/*if (SingleCounter9.getInstance().getAndIncreaseCount() == 1) {
			Log.d(TAG, "readBlock() is called...");
			new Throwable().printStackTrace();
		}*/
		
		request.requestStarts();
		final DiskManagerReadRequestListener listener =
			new DiskManagerReadRequestListener() {
				public void readCompleted(
						DiskManagerReadRequest 	request,
						DirectByteBuffer 		data) {
					
					/*if (SingleCounter9.getInstance().getAndIncreaseCount() == 2) {
						Log.d(TAG, "readCompleted() is called...");
						new Throwable().printStackTrace();
					}*/
					
					request.requestEnds(true);
					_listener.readCompleted(request, data);
				}
				public void readFailed(
						DiskManagerReadRequest 	request,
						Throwable		 		cause) {
					request.requestEnds(false);
					_listener.readFailed(request, cause);
				}
				public int getPriority() {
					return ( _listener.getPriority());
				}
				public void requestExecuted(long bytes) {
					_listener.requestExecuted(bytes);
				}
			};
		DirectByteBuffer buffer	= null;
		try {
			int	length		= request.getLength();
			buffer = DirectByteBufferPool.getBuffer(DirectByteBuffer.AL_DM_READ,length);
			if (buffer == null) { // Fix for bug #804874
				Debug.out("DiskManager::readBlock:: ByteBufferPool returned null buffer");
				listener.readFailed(request, new Exception("Out of memory"));
				return;
			}
			int	pieceNumber	= request.getPieceNumber();
			int	offset		= request.getOffset();
			DMPieceList pieceList = diskManager.getPieceList(pieceNumber);
			// temporary fix for bug 784306
			if (pieceList.size() == 0) {
				Debug.out("no pieceList entries for " + pieceNumber);
				listener.readCompleted(request, buffer);
				return;
			}
			long previousFilesLength = 0;
			int currentFile = 0;
			long fileOffset = pieceList.get(0).getOffset();
			while (currentFile < pieceList.size() && pieceList.getCumulativeLengthToPiece(currentFile) < offset) {
				previousFilesLength = pieceList.getCumulativeLengthToPiece(currentFile);
				currentFile++;
				fileOffset = 0;
			}
			// update the offset (we're in the middle of a file)
			fileOffset += offset - previousFilesLength;
			List	chunks = new ArrayList();
			int	bufferPosition = 0;
			while (bufferPosition < length && currentFile < pieceList.size()) {
				DMPieceMapEntry map_entry = pieceList.get(currentFile);
				int	lengthAvailable = map_entry.getLength() - (int)( fileOffset - map_entry.getOffset());
				//explicitly limit the read size to the proper length, rather than relying on the underlying file being correctly-sized
				//see long DMWriterAndCheckerImpl::checkPiece note
				int entry_read_limit = bufferPosition + lengthAvailable;
				// now bring down to the required read length if this is shorter than this
				// chunk of data
				entry_read_limit = Math.min(length, entry_read_limit);
				// this chunk denotes a read up to buffer offset "entry_read_limit"
				chunks.add(new Object[]{ map_entry.getFile().getCacheFile(), new Long(fileOffset), new Integer( entry_read_limit)});
				bufferPosition = entry_read_limit;
				currentFile++;
				fileOffset = 0;
			}
			if (chunks.size() == 0) {
				Debug.out("no chunk reads for " + pieceNumber);
				listener.readCompleted(request, buffer);
				return;
			}
			
			// this is where we go async and need to start counting requests for the sake
			// of shutting down tidily
			// have to wrap the request as we can validly have >1 for same piece/offset/length and
			// the request type itself overrides object equiv based on this...
			final Object[] request_wrapper = { request };
			DiskManagerReadRequestListener	l =
				new DiskManagerReadRequestListener() {
					public void readCompleted(
							DiskManagerReadRequest 	request,
							DirectByteBuffer 		data) {
						complete();
						listener.readCompleted(request, data);
					}
					public void readFailed(
							DiskManagerReadRequest 	request,
							Throwable		 		cause) {
						complete();
						listener.readFailed(request, cause);
					}
					public int getPriority() {
						return ( _listener.getPriority());
					}
					public void requestExecuted(long bytes) {
						_listener.requestExecuted(bytes);
					}
					protected void complete() {
						try {
							thisMon.enter();
							asyncReads--;
							if (!readRequests.remove( request_wrapper)) {
								Debug.out("request not found");
							}
							if (stopped) {
								asyncReadSem.release();
							}
						} finally {
							thisMon.exit();
						}
					}
				};
			try {
				thisMon.enter();
				if (stopped) {
					buffer.returnToPool();
					listener.readFailed(request, new Exception("Disk reader has been stopped"));
					return;
				}
				asyncReads++;
				readRequests.add(request_wrapper);
			} finally {
				thisMon.exit();
			}
			new requestDispatcher(request, l, buffer, chunks);
		} catch (Throwable e) {
			if (buffer != null) {
				buffer.returnToPool();
			}
			diskManager.setFailed("Disk read error - " + Debug.getNestedExceptionMessage(e));
			Debug.printStackTrace(e);
			listener.readFailed(request, e);
		}
	}

	protected class requestDispatcher implements DiskAccessRequestListener {
		private final DiskManagerReadRequest	dm_request;
		final DiskManagerReadRequestListener	listener;
		private final DirectByteBuffer			buffer;
		private final List						chunks;

		private final int	buffer_length;

		private int	chunk_index;
		private int	chunk_limit;

		protected
		requestDispatcher(
			DiskManagerReadRequest			_request,
			DiskManagerReadRequestListener	_listener,
			DirectByteBuffer				_buffer,
			List							_chunks) {
			dm_request	= _request;
			listener	= _listener;
			buffer		= _buffer;
			chunks		= _chunks;

			/*
			String	str = "Read: " + dm_request.getPieceNumber()+"/"+dm_request.getOffset()+"/"+dm_request.getLength()+":";

			for (int i=0;i<chunks.size();i++) {

				Object[]	entry = (Object[])chunks.get(i);

				String	str2 = entry[0] + "/" + entry[1] +"/" + entry[2];

				str += (i==0?"":",") + str2;
			}

			System.out.println(str);
			*/

			buffer_length = buffer.limit(DirectByteBuffer.SS_DR);

			dispatch();
		}

		protected void dispatch() {
			try {
				if (chunk_index == chunks.size()) {

					buffer.limit(DirectByteBuffer.SS_DR, buffer_length);

					buffer.position( DirectByteBuffer.SS_DR, 0);

					listener.readCompleted(dm_request, buffer);

				} else {

					if (chunk_index == 1 && chunks.size() > 32) {

							// for large numbers of chunks drop the recursion approach and
							// do it linearly (but on the async thread)

						for (int i=1;i<chunks.size();i++) {

							final AESemaphore	sem 	= new AESemaphore("DMR:dispatch:asyncReq");
							final Throwable[]	error	= {null};

							doRequest(
								new DiskAccessRequestListener() {
									public void requestComplete(
										DiskAccessRequest	request) {
										sem.release();
									}

									public void requestCancelled(
										DiskAccessRequest	request) {
										Debug.out("shouldn't get here");
									}

									public void requestFailed(
										DiskAccessRequest	request,
										Throwable			cause) {
										error[0]	= cause;

										sem.release();
									}

									public int getPriority() {
										return ( listener.getPriority());
									}

									public void requestExecuted(long bytes) {
										if (bytes > 0) {

											totalReadBytes 	+= bytes;
											totalReadOps		++;
										}

										listener.requestExecuted(bytes);
									}
								});

							sem.reserve();

							if (error[0] != null) {

								throw (error[0]);
							}
						}

						buffer.limit(DirectByteBuffer.SS_DR, buffer_length);

						buffer.position( DirectByteBuffer.SS_DR, 0);

						listener.readCompleted(dm_request, buffer);
					} else {

						doRequest(this);
					}
				}
			} catch (Throwable e) {

				failed(e);
			}
		}

		protected void doRequest(
			DiskAccessRequestListener	l) {
			Object[]	stuff = (Object[])chunks.get(chunk_index++);

			if (chunk_index > 0) {

				buffer.position(DirectByteBuffer.SS_DR, chunk_limit);
			}

			chunk_limit = ((Integer)stuff[2]).intValue();

			buffer.limit(DirectByteBuffer.SS_DR, chunk_limit);

			short	cache_policy = dm_request.getUseCache()?CacheFile.CP_READ_CACHE:CacheFile.CP_NONE;

			if (dm_request.getFlush()) {

				cache_policy |= CacheFile.CP_FLUSH;
			}

			diskAccess.queueReadRequest(
				(CacheFile)stuff[0],
				((Long)stuff[1]).longValue(),
				buffer,
				cache_policy,
				l);
		}

		public void requestComplete(
			DiskAccessRequest	request) {
			dispatch();
		}

		public void requestCancelled(
			DiskAccessRequest	request) {
				// we never cancel so nothing to do here

			Debug.out("shouldn't get here");
		}

		public void requestFailed(
			DiskAccessRequest	request,
			Throwable			cause) {
			failed(cause);
		}

		public int getPriority() {
			return ( listener.getPriority());
		}

		public void requestExecuted(long bytes) {
			if (bytes > 0) {

				totalReadBytes 	+= bytes;
				totalReadOps		++;
			}

			listener.requestExecuted(bytes);
		}

		protected void failed(
			Throwable			cause) {
			buffer.returnToPool();

			diskManager.setFailed("Disk read error - " + Debug.getNestedExceptionMessage(cause));

			Debug.printStackTrace(cause);

			listener.readFailed(dm_request, cause);
		}
	}
}
