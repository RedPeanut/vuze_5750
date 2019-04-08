/*
 * Created on 12-Jan-2005
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

package com.aelitis.azureus.core.dht.impl;

import java.io.*;
import java.util.List;
import java.util.Properties;

import org.gudy.azureus2.core3.util.AERunStateHandler;
import org.gudy.azureus2.core3.util.Debug;

import com.aelitis.azureus.core.dht.DHT;
import com.aelitis.azureus.core.dht.DHTListener;
import com.aelitis.azureus.core.dht.DHTLogger;
import com.aelitis.azureus.core.dht.DHTOperationListener;
import com.aelitis.azureus.core.dht.DHTStorageAdapter;
import com.aelitis.azureus.core.dht.control.*;
import com.aelitis.azureus.core.dht.db.DHTDB;
import com.aelitis.azureus.core.dht.nat.DHTNATPuncher;
import com.aelitis.azureus.core.dht.nat.DHTNATPuncherAdapter;
import com.aelitis.azureus.core.dht.nat.DHTNATPuncherFactory;
import com.aelitis.azureus.core.dht.netcoords.DHTNetworkPositionManager;
import com.aelitis.azureus.core.dht.router.DHTRouter;
import com.aelitis.azureus.core.dht.speed.DHTSpeedTester;
import com.aelitis.azureus.core.dht.speed.DHTSpeedTesterFactory;
import com.aelitis.azureus.core.dht.transport.*;
import com.aelitis.azureus.core.util.CopyOnWriteList;

import hello.util.Log;

/**
 * @author parg
 *
 */

public class DHTImpl
	implements DHT, AERunStateHandler.RunStateChangeListener {
	
	private static String TAG = DHTImpl.class.getSimpleName();
	
	final DHTStorageAdapter			storageA;
	private DHTNATPuncherAdapter	natAdapter;
	private final DHTControl		control;
	private DHTNATPuncher			natPuncher;
	private DHTSpeedTester			speedTester;
	private final Properties		properties;
	private final DHTLogger			logger;

	private final CopyOnWriteList<DHTListener>	listeners = new CopyOnWriteList<DHTListener>();

	private boolean	runstate_startup 	= true;
	private boolean	sleeping			= false;

	public DHTImpl(
		DHTTransport			_transport,
		Properties				_properties,
		DHTStorageAdapter		_storageAdapter,
		DHTNATPuncherAdapter	_natAdapter,
		DHTLogger				_logger) {
		
		Log.d(TAG, "when DHTImpl is created? and how?");
		
		properties		= _properties;
		storageA		= _storageAdapter;
		natAdapter		= _natAdapter;
		logger			= _logger;
		
		DHTNetworkPositionManager.initialise(storageA);
		DHTLog.setLogger(logger);
		
		int		K 		= getProp(PR_CONTACTS_PER_NODE, 			DHTControl.K_DEFAULT);
		int		B 		= getProp(PR_NODE_SPLIT_FACTOR, 			DHTControl.B_DEFAULT);
		int		maxR	= getProp(PR_MAX_REPLACEMENTS_PER_NODE, 	DHTControl.MAX_REP_PER_NODE_DEFAULT);
		int		sConc 	= getProp(PR_SEARCH_CONCURRENCY, 			DHTControl.SEARCH_CONCURRENCY_DEFAULT);
		int		lConc 	= getProp(PR_LOOKUP_CONCURRENCY, 			DHTControl.LOOKUP_CONCURRENCY_DEFAULT);
		int		oRep 	= getProp(PR_ORIGINAL_REPUBLISH_INTERVAL, 	DHTControl.ORIGINAL_REPUBLISH_INTERVAL_DEFAULT);
		int		cRep 	= getProp(PR_CACHE_REPUBLISH_INTERVAL, 		DHTControl.CACHE_REPUBLISH_INTERVAL_DEFAULT);
		int		cN 		= getProp(PR_CACHE_AT_CLOSEST_N, 			DHTControl.CACHE_AT_CLOSEST_N_DEFAULT);
		boolean	eC 		= getProp(PR_ENCODE_KEYS, 					DHTControl.ENCODE_KEYS_DEFAULT) == 1;
		boolean	rP 		= getProp(PR_ENABLE_RANDOM_LOOKUP, 			DHTControl.ENABLE_RANDOM_DEFAULT) == 1;
		
		control = DHTControlFactory.create(
				new DHTControlAdapter() {
					
					public DHTStorageAdapter getStorageAdapter() {
						return (storageA);
					}
					
					public boolean isDiversified(byte[] key) {
						if (storageA == null) {
							return (false);
						}
						return (storageA.isDiversified(key));
					}
					
					public byte[][] diversify(
						String				description,
						DHTTransportContact	cause,
						boolean				put_operation,
						boolean				existing,
						byte[]				key,
						byte				type,
						boolean				exhaustive,
						int					maxDepth) {
						boolean	valid;
						if (existing) {
							valid =	 	type == DHT.DT_FREQUENCY ||
										type == DHT.DT_SIZE ||
										type == DHT.DT_NONE;
						} else {
							valid = 	type == DHT.DT_FREQUENCY ||
										type == DHT.DT_SIZE;
						}
						if (storageA != null && valid) {
							if (existing) {
								return (storageA.getExistingDiversification( key, put_operation, exhaustive, maxDepth));
							} else {
								return (storageA.createNewDiversification( description, cause, key, put_operation, type, exhaustive, maxDepth));
							}
						} else {
							if (!valid) {
								Debug.out("Invalid diversification received: type = " + type);
							}
							if (existing) {
								return (new byte[][]{ key });
							} else {
								return (new byte[0][]);
							}
						}
					}
				},
				_transport,
				K, B, maxR,
				sConc, lConc,
				oRep, cRep, cN, eC, rP,
				logger
		);
		if (natAdapter != null) {
			natPuncher	= DHTNATPuncherFactory.create(natAdapter, this);
		}
		AERunStateHandler.addListener(this, true);
	}

	public DHTImpl(
			DHTTransport		_transport,
			DHTRouter			_router,
			DHTDB				_database,
			Properties			_properties,
			DHTStorageAdapter	_storageAdapter,
			DHTLogger			_logger) {
		
		properties		= _properties;
		storageA		= _storageAdapter;
		logger			= _logger;
		
		DHTNetworkPositionManager.initialise(storageA);
		DHTLog.setLogger(logger);
		
		int		K 		= getProp(PR_CONTACTS_PER_NODE, 			DHTControl.K_DEFAULT);
		int		B 		= getProp(PR_NODE_SPLIT_FACTOR, 			DHTControl.B_DEFAULT);
		int		maxR	= getProp(PR_MAX_REPLACEMENTS_PER_NODE, 	DHTControl.MAX_REP_PER_NODE_DEFAULT);
		int		sConc 	= getProp(PR_SEARCH_CONCURRENCY, 			DHTControl.SEARCH_CONCURRENCY_DEFAULT);
		int		lConc 	= getProp(PR_LOOKUP_CONCURRENCY, 			DHTControl.LOOKUP_CONCURRENCY_DEFAULT);
		int		oRep 	= getProp(PR_ORIGINAL_REPUBLISH_INTERVAL, 	DHTControl.ORIGINAL_REPUBLISH_INTERVAL_DEFAULT);
		int		cRep 	= getProp(PR_CACHE_REPUBLISH_INTERVAL, 		DHTControl.CACHE_REPUBLISH_INTERVAL_DEFAULT);
		int		cN 		= getProp(PR_CACHE_AT_CLOSEST_N, 			DHTControl.CACHE_AT_CLOSEST_N_DEFAULT);
		boolean	eC 		= getProp(PR_ENCODE_KEYS, 					DHTControl.ENCODE_KEYS_DEFAULT) == 1;
		boolean	rP 		= getProp(PR_ENABLE_RANDOM_LOOKUP, 			DHTControl.ENABLE_RANDOM_DEFAULT) == 1;
		
		control = DHTControlFactory.create(
				
				new DHTControlAdapter() {
					
					public DHTStorageAdapter getStorageAdapter() {
						return (storageA);
					}
					
					public boolean isDiversified(byte[] key) {
						if (storageA == null) {
							return (false);
						}
						return (storageA.isDiversified(key));
					}
					
					public byte[][] diversify(
							String				description,
							DHTTransportContact	cause,
							boolean				putOperation,
							boolean				existing,
							byte[]				key,
							byte				type,
							boolean				exhaustive,
							int					maxDepth) {
						
						boolean	valid;
						if (existing) {
							valid =	 	type == DHT.DT_FREQUENCY ||
										type == DHT.DT_SIZE ||
										type == DHT.DT_NONE;
						} else {
							valid = 	type == DHT.DT_FREQUENCY ||
										type == DHT.DT_SIZE;
						}
						if (storageA != null && valid) {
							if (existing) {
								return (storageA.getExistingDiversification( key, putOperation, exhaustive, maxDepth));
							} else {
								return (storageA.createNewDiversification( description, cause, key, putOperation, type, exhaustive, maxDepth));
							}
						} else {
							if (!valid) {
								Debug.out("Invalid diversification received: type = " + type);
							}
							if (existing) {
								return (new byte[][]{ key });
							} else {
								return (new byte[0][]);
							}
						}
					}
				},
				_transport,
				_router,
				_database,
				K, B, maxR,
				sConc, lConc,
				oRep, cRep, cN, eC, rP,
				logger);
	}

	public void runStateChanged(long run_state) {
		try {
			boolean	is_sleeping = AERunStateHandler.isDHTSleeping();
			if (sleeping != is_sleeping) {
				sleeping = is_sleeping;
				if (!runstate_startup) {
					System.out.println("DHT sleeping=" + sleeping);
				}
			}
			control.setSleeping(sleeping);
			DHTSpeedTester old_tester = null;
			DHTSpeedTester new_tester = null;
			synchronized(this) {
				if (sleeping) {
					if (speedTester != null) {
						old_tester = speedTester;
						speedTester = null;
					}
				} else {
					new_tester = speedTester = DHTSpeedTesterFactory.create(this);
				}
			}
			if (old_tester != null) {
				if (!runstate_startup) {
					System.out.println("    destroying speed tester");
				}
				old_tester.destroy();
			}
			if (new_tester != null) {
				if (!runstate_startup) {
					System.out.println("    creating speed tester");
				}
				for (DHTListener l: listeners) {
					try {
						l.speedTesterAvailable(new_tester);
					} catch (Throwable e) {
						Debug.out(e);
					}
				}
			}
		} finally {
			runstate_startup = false;
		}
	}

	public boolean isSleeping() {
		return (sleeping);
	}

	public void setSuspended(
		boolean	susp) {
		if (susp) {
			if (natPuncher != null) {
				natPuncher.setSuspended(true);
			}
			control.setSuspended(true);
		} else {
			control.setSuspended(false);
			if (natPuncher != null) {
				natPuncher.setSuspended(false);
			}
		}
	}

	protected int getProp(String name, int def) {
		Integer	x = (Integer)properties.get(name);
		if (x == null) {
			properties.put(name, new Integer(def));
			return (def);
		}
		return ( x.intValue());
	}

	public int getIntProperty(String name) {
		return (((Integer)properties.get(name)).intValue());
	}

	public boolean isDiversified(byte[] key) {
		return (control.isDiversified(key));
	}

	public void	put(
		byte[]					key,
		String					description,
		byte[]					value,
		short					flags,
		DHTOperationListener	listener) {
		control.put(key, description, value, flags, (byte)0, DHT.REP_FACT_DEFAULT, true, listener);
	}

	public void put(
		byte[]					key,
		String					description,
		byte[]					value,
		short					flags,
		boolean					high_priority,
		DHTOperationListener	listener) {
		control.put(key, description, value, flags, (byte)0, DHT.REP_FACT_DEFAULT, high_priority, listener);
	}

	public void put(
		byte[]					key,
		String					description,
		byte[]					value,
		short					flags,
		byte					life_hours,
		boolean					high_priority,
		DHTOperationListener	listener) {
		control.put(key, description, value, flags, life_hours, DHT.REP_FACT_DEFAULT, high_priority, listener);
	}

	public void put(
		byte[]					key,
		String					description,
		byte[]					value,
		short					flags,
		byte					life_hours,
		byte					replication_control,
		boolean					high_priority,
		DHTOperationListener	listener) {
		control.put(key, description, value, flags, life_hours, replication_control, high_priority, listener);
	}

	public DHTTransportValue getLocalValue(byte[] key) {
		return (control.getLocalValue(key));
	}

	public List<DHTTransportValue> getStoredValues(byte[] key) {
		return (control.getStoredValues(key));
	}

	public void get(
		byte[]					key,
		String					description,
		short					flags,
		int						max_values,
		long					timeout,
		boolean					exhaustive,
		boolean					high_priority,
		DHTOperationListener	listener) {
		control.get(key, description, flags, max_values, timeout, exhaustive, high_priority, listener);
	}

	public byte[] remove(
		byte[]					key,
		String					description,
		DHTOperationListener	listener) {
		return (control.remove( key, description, listener));
	}

	public byte[] remove(
		DHTTransportContact[]	contacts,
		byte[]					key,
		String					description,
		DHTOperationListener	listener) {
		return (control.remove( contacts, key, description, listener));
	}

	public DHTTransport getTransport() {
		return (control.getTransport());
	}

	public DHTRouter getRouter() {
		return (control.getRouter());
	}

	public DHTControl getControl() {
		return (control);
	}

	public DHTDB getDataBase() {
		return (control.getDataBase());
	}

	public DHTNATPuncher getNATPuncher() {
		return (natPuncher);
	}

	public DHTSpeedTester
	getSpeedTester() {
		return (speedTester);
	}

	public DHTStorageAdapter getStorageAdapter() {
		return (storageA);
	}

	public void	integrate(boolean fullWait) {
		control.seed(fullWait);
		if (natPuncher!= null) {
			natPuncher.start();
		}
	}

	public void	destroy() {
		if (natPuncher != null) {
			natPuncher.destroy();
		}
		DHTNetworkPositionManager.destroy(storageA);
		AERunStateHandler.removeListener(this);
		if (control != null) {
			control.destroy();
		}
		if (speedTester != null) {
			speedTester.destroy();
		}
	}

	public void exportState(DataOutputStream os, int max)
			throws IOException {
		control.exportState(os, max);
	}

	public void	importState(DataInputStream is) throws IOException {
		control.importState(is);
	}

	public void setLogging(boolean on) {
		DHTLog.setLogging(on);
	}

	public DHTLogger getLogger() {
		return (logger);
	}

	public void	print(boolean full) {
		control.print(full);
	}

	public void addListener(DHTListener listener) {
		listeners.add(listener);

		DHTSpeedTester st = speedTester;
		if (st != null) {
			listener.speedTesterAvailable(st);
		}
	}

	public void removeListener(DHTListener listener) {
		listeners.remove(listener);
	}
}
