/*
 * Created on 23 Jun 2006
 * Created by Paul Gardner
 * Copyright (C) 2006 Aelitis, All Rights Reserved.
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
 * AELITIS, SAS au capital de 46,603.30 euros
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 *
 */

package com.aelitis.azureus.core.networkmanager.impl.utp;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.util.AESemaphore;
import org.gudy.azureus2.core3.util.AEThread2;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.SystemTime;

import com.aelitis.azureus.core.networkmanager.impl.TransportHelper;

import hello.util.Log;
import hello.util.SingleCounter0;

public class UTPSelector {
	
	private static String TAG = UTPSelector.class.getSimpleName();
	
	private static final int POLL_FREQUENCY	= COConfigurationManager.getIntParameter("network.utp.poll.time", 50);

	private AEThread2	thread;

	private List<Object[]>	readySet	= new LinkedList<Object[]>();
	private AESemaphore		readySem	= new AESemaphore("UTPSelector");

	private volatile boolean destroyed;

	protected UTPSelector(final UTPConnectionManager manager) {
		
		thread =
			new AEThread2("UTPSelector", true) {
				public void run() {
					boolean	quit		= false;
					long	last_poll	= 0;
					int		lastConnectionCount = 0;
					while (!quit) {
						if (destroyed) {
							// one last dispatch cycle
							quit	= true;
						}
						long	now = SystemTime.getMonotonousTime();
						if (now - last_poll >= POLL_FREQUENCY) {
							lastConnectionCount = manager.poll(readySem, now);
							last_poll	= now;
						}
						if (readySem.getValue() == 0) {
							manager.inputIdle();
						}
						if (readySem.reserve(lastConnectionCount==0?1000:(POLL_FREQUENCY/2))) {
							Object[]	entry;
							synchronized(readySet) {
								if (readySet.size() == 0) {
									continue;
								}
								entry = readySet.remove(0);
							}

							TransportHelper	transport 	= (TransportHelper)entry[0];
							TransportHelper.selectListener	listener = (TransportHelper.selectListener)entry[1];
							if (listener == null) {
								Debug.out("Null listener");
							} else {
								Object attachment = entry[2];
								try {
									if (entry.length == 3) {
										listener.selectSuccess(transport, attachment);
									} else {
										listener.selectFailure(transport, attachment, (Throwable)entry[3]);
									}
								} catch (Throwable e) {
									Debug.printStackTrace(e);
								}
							}
						}
					}
				}
			};
		thread.setPriority(Thread.MAX_PRIORITY-1);
		thread.start();
	}

	protected void destroy() {
		synchronized(readySet) {
			destroyed	= true;
		}
	}

	protected void ready(
		TransportHelper						transport,
		TransportHelper.selectListener		listener,
		Object								attachment) {
		
		/*if (Once.getInstance().getAndIncreaseCount() < 1) {
			Log.d(TAG, "ready() is called...");
			new Throwable().printStackTrace();
		}*/
		
		boolean	removed = false;
		synchronized(readySet) {
			if (destroyed) {
				Debug.out("Selector has been destroyed");
				throw (new RuntimeException( "Selector has been destroyed"));
			}
			Iterator<Object[]>	it = readySet.iterator();
			while( it.hasNext()) {
				Object[]	entry = (Object[])it.next();
				if (entry[1] == listener) {
					it.remove();
					removed	= true;
					break;
				}
			}
			readySet.add(new Object[]{ transport, listener, attachment });
		}
		if (!removed) {
			readySem.release();
		}
	}

	protected void ready(
		TransportHelper						transport,
		TransportHelper.selectListener		listener,
		Object								attachment,
		Throwable							error) {
		
		/*if (Once.getInstance().getAndIncreaseCount() < 1) {
			Log.d(TAG, "ready() is called...");
			new Throwable().printStackTrace();
		}*/
		
		boolean	removed = false;
		synchronized(readySet) {
			if (destroyed) {
				Debug.out("Selector has been destroyed");
				throw (new RuntimeException( "Selector has been destroyed"));
			}
			Iterator	it = readySet.iterator();
			while(it.hasNext()) {
				Object[]	entry = (Object[])it.next();
				if (entry[1] == listener) {
					it.remove();
					removed	= true;
					break;
				}
			}
			readySet.add(new Object[]{ transport, listener, attachment, error });
		}
		if (!removed) {
			readySem.release();
		}
	}

	protected void	cancel(
		TransportHelper						transport,
		TransportHelper.selectListener		listener) {
		synchronized(readySet) {
			Iterator	it = readySet.iterator();
			while(it.hasNext()) {
				Object[]	entry = (Object[])it.next();
				if (entry[0] == transport && entry[1] == listener) {
					it.remove();
					break;
				}
			}
		}
	}
}