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

import java.util.*;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.config.ParameterListener;
import org.gudy.azureus2.core3.disk.*;
import org.gudy.azureus2.core3.disk.impl.DiskManagerFileInfoImpl;
import org.gudy.azureus2.core3.disk.impl.DiskManagerHelper;
import org.gudy.azureus2.core3.disk.impl.DiskManagerRecheckInstance;
import org.gudy.azureus2.core3.disk.impl.access.DMChecker;
import org.gudy.azureus2.core3.disk.impl.piecemapper.DMPieceList;
import org.gudy.azureus2.core3.disk.impl.piecemapper.DMPieceMapEntry;
import org.gudy.azureus2.core3.logging.*;
import org.gudy.azureus2.core3.util.*;

import com.aelitis.azureus.core.diskmanager.cache.CacheFile;

/**
 * @author parg
 *
 */

public class
DMCheckerImpl
	implements DMChecker
{
	protected static final LogIDs LOGID = LogIDs.DISK;

	private static boolean	flushPieces;
	private static boolean	checking_read_priority;

	static final AEMonitor		classMon	= new AEMonitor("DMChecker:class");
	static final List				asyncCheckQueue		= new ArrayList();
	static final AESemaphore		asyncCheckQueueSem 	= new AESemaphore("DMChecker::asyncCheck");

	private static final boolean	fullyAsync = COConfigurationManager.getBooleanParameter("diskmanager.perf.checking.fully.async");

	static{
		if (fullyAsync) {

			new AEThread2("DMCheckerImpl:asyncCheckScheduler", true) {
				public void run() {
					while (true) {

						asyncCheckQueueSem.reserve();

						Object[]	entry;

						try {
							classMon.enter();

							entry = (Object[])asyncCheckQueue.remove(0);

							int	queue_size = asyncCheckQueue.size();

							if (queue_size % 100 == 0 && queue_size > 0) {

								System.out.println("async check queue size=" + asyncCheckQueue.size());
							}

						} finally {

							classMon.exit();
						}

						((DMCheckerImpl)entry[0]).enqueueCheckRequest(
							(DiskManagerCheckRequest)entry[1],
							(DiskManagerCheckRequestListener)entry[2],
							flushPieces);
					}
				}
			}.start();
		}
	}

    static{

    	 ParameterListener param_listener = new ParameterListener() {
    	    public void parameterChanged(
				String  str )
    	    {
    	   	    flushPieces				= COConfigurationManager.getBooleanParameter("diskmanager.perf.cache.flushpieces");
       	   	  	checking_read_priority		= COConfigurationManager.getBooleanParameter("diskmanager.perf.checking.read.priority");
     	    }
    	 };

 		COConfigurationManager.addAndFireParameterListeners(
 			new String[]{
 				"diskmanager.perf.cache.flushpieces",
 				"diskmanager.perf.checking.read.priority" },
 				param_listener);
    }

	protected final DiskManagerHelper		diskManager;

	protected int			asyncChecks;
	protected final AESemaphore	asyncCheckSem 	= new AESemaphore("DMChecker::asyncCheck");

	protected int			asyncReads;
	protected final AESemaphore	async_read_sem 		= new AESemaphore("DMChecker::asyncRead");

	private boolean	started;

	protected volatile boolean	stopped;

	private volatile boolean	complete_recheck_in_progress;
	private volatile int		complete_recheck_progress;

	private boolean				checking_enabled		= true;

	protected final AEMonitor	thisMon	= new AEMonitor("DMChecker");

	public DMCheckerImpl(
		DiskManagerHelper	_disk_manager) {
		diskManager	= _disk_manager;
	}

	public void start() {
		try {
			thisMon.enter();
			if (started) {
				throw (new RuntimeException("DMChecker: start while started"));
			}
			if (stopped) {
				throw (new RuntimeException("DMChecker: start after stopped"));
			}
			started	= true;
		} finally {
			thisMon.exit();
		}
	}

	public void stop() {
		int	check_wait;
		int	read_wait;
		try {
			thisMon.enter();
			if (stopped || !started) {
				return;
			}
				// when we exit here we guarantee that all file usage operations have completed
				// i.e. writes and checks (checks being doubly async)
			stopped	= true;
			read_wait	= asyncReads;
			check_wait	= asyncChecks;
		} finally {
			thisMon.exit();
		}
		long	log_time 		= SystemTime.getCurrentTime();
			// wait for reads
		for (int i=0;i<read_wait;i++) {
			long	now = SystemTime.getCurrentTime();
			if (now < log_time) {
				log_time = now;
			} else {
				if (now - log_time > 1000) {
					log_time	= now;
					if (Logger.isEnabled()) {
						Logger.log(new LogEvent(diskManager, LOGID, "Waiting for check-reads to complete - " + (read_wait-i) + " remaining" ));
					}
				}
			}
			async_read_sem.reserve();
		}
		log_time 		= SystemTime.getCurrentTime();
			// wait for checks
		for (int i=0;i<check_wait;i++) {
			long	now = SystemTime.getCurrentTime();
			if (now < log_time) {
				log_time = now;
			} else {
				if (now - log_time > 1000) {
					log_time	= now;
					if (Logger.isEnabled()) {
						Logger.log(new LogEvent(diskManager, LOGID, "Waiting for checks to complete - " + (read_wait-i) + " remaining" ));
					}
				}
			}
			asyncCheckSem.reserve();
		}
	}

	public int getCompleteRecheckStatus() {
	   if (complete_recheck_in_progress) {

		   return (complete_recheck_progress);

	   } else {

		   return (-1);
	   }
	}

	public void setCheckingEnabled(
		boolean		enabled) {
		checking_enabled = enabled;
	}

	public DiskManagerCheckRequest
	createCheckRequest(
		int 	pieceNumber,
		Object	user_data) {
		return (new DiskManagerCheckRequestImpl( pieceNumber, user_data));
	}

	public void enqueueCompleteRecheckRequest(
		final DiskManagerCheckRequest			request,
		final DiskManagerCheckRequestListener 	listener) {
		if (!checking_enabled) {
			listener.checkCompleted(request, true);
			return;
		}
		complete_recheck_progress		= 0;
		complete_recheck_in_progress	= true;
	 	new AEThread2("DMChecker::completeRecheck", true) {
		  		public void run()
		  		{
		  			DiskManagerRecheckInstance	recheck_inst = diskManager.getRecheckScheduler().register(diskManager, true);
		  			try {
		  				final AESemaphore	sem = new AESemaphore("DMChecker::completeRecheck");
		  				int	checks_submitted	= 0;
			            final AESemaphore	 run_sem = new AESemaphore("DMChecker::completeRecheck:runsem", 2);
			            int nbPieces = diskManager.getNbPieces();
		  				for (int i=0; i < nbPieces; i++) {
		  					complete_recheck_progress = 1000*i / nbPieces;
		  					DiskManagerPiece	dm_piece = diskManager.getPiece(i);
	  							// only recheck the piece if it happens to be done (a complete dnd file that's
	  							// been set back to dnd for example) or the piece is part of a non-dnd file
		  					if (dm_piece.isDone() || !dm_piece.isSkipped()) {
			  					run_sem.reserve();
				  				while (!stopped) {
					  				if (recheck_inst.getPermission()) {
					  					break;
					  				}
					  			}
			  					if (stopped) {
			  						break;
			  					}
			  					final DiskManagerCheckRequest this_request = createCheckRequest( i, request.getUserData());
			  					enqueueCheckRequest(
			  						this_request,
			  	       				new DiskManagerCheckRequestListener() {
					  	       			public void
					  	       			checkCompleted(
					  	       				DiskManagerCheckRequest 	request,
					  	       				boolean						passed )
					  	       			{
					  	       				try {
					  	       					listener.checkCompleted(request, passed);
					  	       				} catch (Throwable e) {
					  	       					Debug.printStackTrace(e);
					  	       				} finally {
					  	       					complete();
					  	       				}
					  	       			}
					  	       			public void
					  	       			checkCancelled(
					  	       				DiskManagerCheckRequest		request )
					  	       			{
					  	       				try {
					  	       					listener.checkCancelled(request);
					  	       				} catch (Throwable e) {
					  	       					Debug.printStackTrace(e);
					  	       				} finally {
					  	       					complete();
					  	       				}
					  	       			}
					  	       			public void
					  	       			checkFailed(
					  	       				DiskManagerCheckRequest 	request,
					  	       				Throwable		 			cause )
					  	       			{
					  	       				try {
					  	       					listener.checkFailed(request, cause);
					  	       				} catch (Throwable e) {
					  	       					Debug.printStackTrace(e);
					  	       				} finally {
					  	       					complete();
					  	       				}			  	       			}
					  	       			protected void
					  	       			complete()
					  	       			{
			  	       						run_sem.release();
			  	       						sem.release();
				  	       				}
									},
									false);
			  					checks_submitted++;
		  					}
		  				}
		  					// wait for all to complete
		  				for (int i=0;i<checks_submitted;i++) {
		  					sem.reserve();
		  				}
		  	       } finally {
		  	       		complete_recheck_in_progress	= false;
		  	       		recheck_inst.unregister();
		  	       }
		        }
		 	}.start();
	}

	public void enqueueCheckRequest(
		DiskManagerCheckRequest				request,
		DiskManagerCheckRequestListener 	listener) {
		if (fullyAsync) {
			// if the disk controller read-queue is full then normal the read-request allocation
			// will block. This option forces the check request to be scheduled off the caller's
			// thread
			try {
				classMon.enter();
				asyncCheckQueue.add(new Object[]{ this, request, listener });
				if (asyncCheckQueue.size() % 100 == 0) {
					System.out.println("async check queue size=" + asyncCheckQueue.size());
				}
			} finally {
				classMon.exit();
			}
			asyncCheckQueueSem.release();
		} else {
			enqueueCheckRequest(request, listener, flushPieces);
		}
	}

	public boolean hasOutstandingCheckRequestForPiece(
		int		piece_number) {
		if (fullyAsync) {
			try {
				classMon.enter();
				for (int i=0;i<asyncCheckQueue.size();i++) {
					Object[]	entry = (Object[])asyncCheckQueue.get(i);
					if (entry[0] == this) {
						DiskManagerCheckRequest request = (DiskManagerCheckRequest)entry[1];
						if (request.getPieceNumber() == piece_number) {
							return (true);
						}
					}
				}
			} finally {
				classMon.exit();
			}
		}
		return (false);
	}

	protected void enqueueCheckRequest(
		final DiskManagerCheckRequest			request,
		final DiskManagerCheckRequestListener 	listener,
		boolean									readFlush ) {
		
		// everything comes through here - the interceptor listener maintains the piece state and
		// does logging
		request.requestStarts();
		enqueueCheckRequestSupport(
				request,
				new DiskManagerCheckRequestListener() {
					public void checkCompleted(
						DiskManagerCheckRequest 	request,
						boolean						passed) {
						request.requestEnds(true);
						try {
							int	piece_number	= request.getPieceNumber();
							DiskManagerPiece	piece = diskManager.getPiece(request.getPieceNumber());
							piece.setDone(passed);
							if (passed) {
								DMPieceList	pieceList = diskManager.getPieceList(piece_number);
								for (int i = 0; i < pieceList.size(); i++) {
									DMPieceMapEntry pieceEntry = pieceList.get(i);
									pieceEntry.getFile().dataChecked( pieceEntry.getOffset(), pieceEntry.getLength());
								}
							}
						} finally {
							listener.checkCompleted(request, passed);
							if (Logger.isEnabled()) {
								if (passed) {
									Logger.log(new LogEvent(diskManager, LOGID, LogEvent.LT_INFORMATION,
												"Piece " + request.getPieceNumber() + " passed hash check."));
								} else {
									Logger.log(new LogEvent(diskManager, LOGID, LogEvent.LT_WARNING,
												"Piece " + request.getPieceNumber() + " failed hash check."));
								}
							}
						}
					}
					
					public void checkCancelled(DiskManagerCheckRequest request) {
						request.requestEnds(false);
						// don't explicitly mark a piece as failed if we get a cancellation as the
						// existing state will suffice. Either we're rechecking because it is bad
						// already (in which case it won't be done, or we're doing a recheck-on-complete
						// in which case the state is ok and musn't be flipped to bad
						listener.checkCancelled(request);
						if (Logger.isEnabled()) {
							Logger.log(new LogEvent(diskManager, LOGID, LogEvent.LT_WARNING,
											"Piece " + request.getPieceNumber() + " hash check cancelled."));
						}
					}
					
					public void checkFailed(
						DiskManagerCheckRequest 	request,
						Throwable		 			cause) {
						request.requestEnds(false);
						try {
							diskManager.getPiece(request.getPieceNumber()).setDone(false);
						} finally {
							listener.checkFailed(request, cause);
							if (Logger.isEnabled()) {
								Logger.log(new LogEvent(diskManager, LOGID, LogEvent.LT_WARNING,
												"Piece " + request.getPieceNumber() + " failed hash check - " + Debug.getNestedExceptionMessage(cause)));
							}
						}
					}
				}, readFlush);
	}


	protected void enqueueCheckRequestSupport(
		final DiskManagerCheckRequest			request,
		final DiskManagerCheckRequestListener	listener,
		boolean									readFlush) {
		
		if (!checking_enabled) {
			listener.checkCompleted(request, true);
			return;
		}
		
		final int	pieceNumber	= request.getPieceNumber();
		try {
			final byte[]	requiredHash = diskManager.getPieceHash(pieceNumber);
			
			// quick check that the files that make up this piece are at least big enough
			// to warrant reading the data to check
			// also, if the piece is entirely compact then we can immediately
			// fail as we don't actually have any data for the piece (or can assume we don't)
			// we relax this a bit to catch pieces that are part of compact files with less than
			// three pieces as it is possible that these were once complete and have all their bits
			// living in retained compact areas
			final DMPieceList pieceList = diskManager.getPieceList(pieceNumber);
			try {
				// there are other comments in the code about the existence of 0 length piece lists
				// just in case these still occur for who knows what reason ensure that a 0 length list
				// causes the code to carry on and do the check (i.e. it is no worse that before this
				// optimisation was added...)
				boolean	allCompact = pieceList.size() > 0;
				for (int i = 0; i < pieceList.size(); i++) {
					DMPieceMapEntry piece_entry = pieceList.get(i);
					DiskManagerFileInfoImpl	file_info = piece_entry.getFile();
					CacheFile	cache_file = file_info.getCacheFile();
					if (cache_file.compareLength( piece_entry.getOffset()) < 0) {
						listener.checkCompleted(request, false);
						return;
					}
					if (allCompact) {
						int st = cache_file.getStorageType();
						if ((st != CacheFile.CT_COMPACT && st != CacheFile.CT_PIECE_REORDER_COMPACT ) || file_info.getNbPieces() <= 2) {
							allCompact = false;
						}
					}
				}
				if (allCompact) {
					// System.out.println("Piece " + pieceNumber + " is all compact, failing hash check");
					listener.checkCompleted(request, false);
					return;
				}
			} catch (Throwable e) {
				// we can fail here if the disk manager has been stopped as the cache file length access may be being
				// performed on a "closed" (i.e. un-owned) file
				listener.checkCancelled(request);
				return;
			}
			int thisPieceLength = diskManager.getPieceLength(pieceNumber);
			DiskManagerReadRequest readRequest = diskManager.createReadRequest(pieceNumber, 0, thisPieceLength);
		   	try {
		   		thisMon.enter();
				if (stopped) {
					listener.checkCancelled(request);
					return;
				}
				asyncReads++;
		   	} finally {
		   		thisMon.exit();
		   	}
		   	readRequest.setFlush(readFlush);
		   	readRequest.setUseCache(!request.isAdHoc());
			diskManager.enqueueReadRequest(
				readRequest,
				new DiskManagerReadRequestListener() {
					public void readCompleted(
						DiskManagerReadRequest 	readRequest,
						DirectByteBuffer 		buffer) {
						complete();
					   	try {
					   		thisMon.enter();
							if (stopped) {
								buffer.returnToPool();
								listener.checkCancelled(request);
								return;
							}
							asyncChecks++;
					   	} finally {
					   		thisMon.exit();
					   	}
					   	if (buffer.getFlag( DirectByteBuffer.FL_CONTAINS_TRANSIENT_DATA)) {
					   		try {
					   			buffer.returnToPool();
					   			listener.checkCompleted(request, false);
					   		} finally {
					   			try {
    								thisMon.enter();
    								asyncChecks--;
    								if (stopped) {
    									asyncCheckSem.release();
    								}
    							} finally {
    								thisMon.exit();
    							}
					   		}
					   	} else {
							try {
						    	final	DirectByteBuffer	f_buffer	= buffer;
							   	ConcurrentHasher.getSingleton().addRequest(
					    			buffer.getBuffer(DirectByteBuffer.SS_DW),
									new ConcurrentHasherRequestListener() {
					    				public void complete(ConcurrentHasherRequest hashRequest) {
					    					int	asyncResult	= 3; // cancelled
					    					try {
												byte[] actualHash = hashRequest.getResult();
												if (actualHash != null) {
													request.setHash(actualHash);
				    								asyncResult = 1; // success
				    								for (int i = 0; i < actualHash.length; i++) {
				    									if (actualHash[i] != requiredHash[i]) {
				    										asyncResult = 2; // failed;
				    										break;
				    									}
				    								}
												}
					    					} finally {
					    						try {
					    							if (asyncResult == 1) {
					    								try {
					    									for (int i = 0; i < pieceList.size(); i++) {
					    										DMPieceMapEntry pieceEntry = pieceList.get(i);
					    										DiskManagerFileInfoImpl	fileInfo = pieceEntry.getFile();
				    											// edge case here for skipped zero length files that have been deleted
					    										if (fileInfo.getLength() > 0 || !fileInfo.isSkipped()) {
					    											CacheFile	cacheFile = fileInfo.getCacheFile();
					    											cacheFile.setPieceComplete(pieceNumber, f_buffer);
					    										}
					    									}
					    								} catch (Throwable e) {
					    									f_buffer.returnToPool();
					    									Debug.out(e);
					    									listener.checkFailed(request, e);
					    									return;
					    								}
					    							}
						    						f_buffer.returnToPool();
						    						if (asyncResult == 1) {
						    							listener.checkCompleted(request, true);
						    						} else if (asyncResult == 2) {
						    							listener.checkCompleted(request, false);
						    						} else {
						    							listener.checkCancelled(request);
						    						}
					    						} finally {
					    							try {
					    								thisMon.enter();
					    								asyncChecks--;
					    								if (stopped) {
					    									asyncCheckSem.release();
					    								}
					    							} finally {
					    								thisMon.exit();
					    							}
					    						}
					    					}
					    				}
									},
									request.isLowPriority()
								);

							} catch (Throwable e) {
								Debug.printStackTrace(e);
	    						buffer.returnToPool();
	    						listener.checkFailed(request, e);
							}
					   	}
					}
					
					public void readFailed(
						DiskManagerReadRequest 	readRequest,
						Throwable		 		cause) {
						complete();
						listener.checkFailed(request, cause);
					}
					
					public int getPriority() {
						return (checking_read_priority?0:-1);
					}
					
					public void requestExecuted(long bytes) {
					}
					
					protected void complete() {
						try {
							thisMon.enter();
							asyncReads--;
							if (stopped) {
								async_read_sem.release();
							}
						} finally {
							thisMon.exit();
						}
					}
				});
		} catch (Throwable e) {
			diskManager.setFailed("Piece check error - " + Debug.getNestedExceptionMessage(e));
			Debug.printStackTrace(e);
			listener.checkFailed(request, e);
		}
	}
}
