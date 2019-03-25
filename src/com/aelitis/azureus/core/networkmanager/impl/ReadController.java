/*
 * Created on Oct 16, 2004
 * Created by Alon Rohter
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
 *
 */

package com.aelitis.azureus.core.networkmanager.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.config.ParameterListener;
import org.gudy.azureus2.core3.util.*;

import com.aelitis.azureus.core.networkmanager.EventWaiter;
import com.aelitis.azureus.core.stats.AzureusCoreStats;
import com.aelitis.azureus.core.stats.AzureusCoreStatsProvider;



/**
 * Processes reads of read-entities and handles the read selector.
 */
public class ReadController implements AzureusCoreStatsProvider {

	private static int 		IDLE_SLEEP_TIME		= 50;
	private static boolean	AGGRESIVE_READ		= false;

	static {
		COConfigurationManager.addAndFireParameterListeners(
			new String[]{
				"network.control.read.idle.time",
				"network.control.read.aggressive",
			},
			new ParameterListener() {
				public void parameterChanged(String name) {
					IDLE_SLEEP_TIME 	= COConfigurationManager.getIntParameter("network.control.read.idle.time");
					AGGRESIVE_READ		= COConfigurationManager.getBooleanParameter("network.control.read.aggressive");
				}
			}
		);
	}

	private volatile ArrayList<RateControlledEntity> normalPriorityEntities = new ArrayList<RateControlledEntity>();	//copied-on-write
	private volatile ArrayList<RateControlledEntity> highPriorityEntities 	= new ArrayList<RateControlledEntity>();	//copied-on-write
	private final AEMonitor entitiesMon = new AEMonitor("ReadController:EM");
	private int nextNormalPosition = 0;
	private int nextHighPosition = 0;

	private long	loopCount;
	private long	waitCount;
	private long	nonProgressCount;
	private long	progressCount;

	private long	entityCheckCount;
	private long	lastEntityCheckCount;

	private final EventWaiter 	readWaiter = new EventWaiter();

	private int			entityCount;

	public ReadController() {

		//start read handler processing
		Thread readProcessorThread = new AEThread("ReadController:ReadProcessor") {
			public void runSupport() {
				readProcessorLoop();
			}
		};
		readProcessorThread.setDaemon(true);
		readProcessorThread.setPriority(Thread.MAX_PRIORITY - 1);
		readProcessorThread.start();

		Set<String>	types = new HashSet<>();

		types.add(AzureusCoreStats.ST_NET_READ_CONTROL_LOOP_COUNT);
		types.add(AzureusCoreStats.ST_NET_READ_CONTROL_NP_COUNT);
		types.add(AzureusCoreStats.ST_NET_READ_CONTROL_P_COUNT);
		types.add(AzureusCoreStats.ST_NET_READ_CONTROL_WAIT_COUNT);
		types.add(AzureusCoreStats.ST_NET_READ_CONTROL_ENTITY_COUNT);
		types.add(AzureusCoreStats.ST_NET_READ_CONTROL_CON_COUNT);
		types.add(AzureusCoreStats.ST_NET_READ_CONTROL_READY_CON_COUNT);

		AzureusCoreStats.registerProvider(
			types,
			this);

		AEDiagnostics.addEvidenceGenerator(
			new AEDiagnosticsEvidenceGenerator() {
				public void generate(IndentWriter writer) {
					writer.println("Read Controller");
					try {
						writer.indent();
						ArrayList<RateControlledEntity> ref = normalPriorityEntities;
						writer.println("normal - " + ref.size());
						for (int i=0;i<ref.size();i++) {
							RateControlledEntity entity = ref.get(i);
							writer.println( entity.getString());
						}
						ref = highPriorityEntities;
						writer.println("priority - " + ref.size());
						for (int i=0;i<ref.size();i++) {
							RateControlledEntity entity = ref.get(i);
							writer.println(entity.getString());
						}
					} finally {
						writer.exdent();
					}
				}
			});
	}

	public void updateStats(Set types, Map values) {
		if (types.contains(AzureusCoreStats.ST_NET_READ_CONTROL_LOOP_COUNT)) {
			values.put(AzureusCoreStats.ST_NET_READ_CONTROL_LOOP_COUNT, new Long( loopCount ));
		}
		if (types.contains(AzureusCoreStats.ST_NET_READ_CONTROL_NP_COUNT)) {
			values.put(AzureusCoreStats.ST_NET_READ_CONTROL_NP_COUNT, new Long( nonProgressCount ));
		}
		if (types.contains(AzureusCoreStats.ST_NET_READ_CONTROL_P_COUNT)) {
			values.put(AzureusCoreStats.ST_NET_READ_CONTROL_P_COUNT, new Long( progressCount ));
		}
		if (types.contains(AzureusCoreStats.ST_NET_READ_CONTROL_WAIT_COUNT)) {
			values.put(AzureusCoreStats.ST_NET_READ_CONTROL_WAIT_COUNT, new Long( waitCount ));
		}
		if (types.contains(AzureusCoreStats.ST_NET_READ_CONTROL_ENTITY_COUNT)) {
			values.put( AzureusCoreStats.ST_NET_READ_CONTROL_ENTITY_COUNT, new Long( highPriorityEntities.size() + normalPriorityEntities.size()));
		}
		if (	
			types.contains(AzureusCoreStats.ST_NET_READ_CONTROL_CON_COUNT) ||
			types.contains(AzureusCoreStats.ST_NET_READ_CONTROL_READY_CON_COUNT)
		) {
			int	ready_connections	= 0;
			int	connections			= 0;
			ArrayList[] refs = { normalPriorityEntities, highPriorityEntities };
			for (int i=0;i<refs.length;i++) {
				ArrayList	ref = refs[i];
				for (int j=0;j<ref.size();j++) {
						RateControlledEntity entity = (RateControlledEntity)ref.get(j);
						connections 		+= entity.getConnectionCount(readWaiter);
						ready_connections += entity.getReadyConnectionCount(readWaiter);
				}
			}
			values.put(AzureusCoreStats.ST_NET_READ_CONTROL_CON_COUNT, new Long( connections));
			values.put(AzureusCoreStats.ST_NET_READ_CONTROL_READY_CON_COUNT, new Long( ready_connections));
		}
	}




	private void readProcessorLoop() {
		boolean checkHighFirst = true;

		while (true) {
			loopCount++;
			try {
				if (checkHighFirst) {
					checkHighFirst = false;
					if (!doHighPriorityRead()) {
						if (!doNormalPriorityRead()) {
							if (readWaiter.waitForEvent(hasConnections()?IDLE_SLEEP_TIME:1000)) {
								waitCount++;
							}
						}
					}
				} else {
					checkHighFirst = true;
					if (!doNormalPriorityRead()) {
						if (!doHighPriorityRead()) {
							if (readWaiter.waitForEvent(hasConnections()?IDLE_SLEEP_TIME:1000)) {
								waitCount++;
							}
						}
					}
				}
			} catch (Throwable t) {
				Debug.out("readProcessorLoop() EXCEPTION: ", t);
			}
		}
	}

	private boolean hasConnections() {
		if (entityCount == 0) {
			return (false);
		}
		List<RateControlledEntity> ref = highPriorityEntities;
		for (RateControlledEntity e: ref) {
			if (e.getConnectionCount(readWaiter) > 0) {
				return (true);
			}
		}
		ref = normalPriorityEntities;
		for (RateControlledEntity e: ref) {
			if (e.getConnectionCount(readWaiter) > 0) {
				return (true);
			}
		}
		return (false);
	}

	private boolean doNormalPriorityRead() {
		return (doRead(getNextReadyNormalPriorityEntity()));
	}

	private boolean doHighPriorityRead() {
		return (doRead(getNextReadyHighPriorityEntity()));
	}

	private boolean doRead(RateControlledEntity readyEntity) {
		if (readyEntity != null) {
			if (AGGRESIVE_READ) {
				// skip over failed readers to find a good one
				if (readyEntity.doProcessing(readWaiter, 0) > 0) {
					progressCount++;
					return (true);
				} else {
					nonProgressCount++;
					if (entityCheckCount - lastEntityCheckCount >= normalPriorityEntities.size() + highPriorityEntities.size()) {
						lastEntityCheckCount	= entityCheckCount;
						// force a wait
						if (readWaiter.waitForEvent(IDLE_SLEEP_TIME)) {
							waitCount++;
						}
						return (false);
					}
					return (true);
				}
			} else {
				return (readyEntity.doProcessing(readWaiter, 0) > 0);
			}
		}
		return false;
	}


	private RateControlledEntity getNextReadyNormalPriorityEntity() {
		ArrayList<RateControlledEntity> ref = normalPriorityEntities;

		int size = ref.size();
		int num_checked = 0;

		while (num_checked < size) {
			entityCheckCount++;
			nextNormalPosition = nextNormalPosition >= size ? 0 : nextNormalPosition;	//make circular
			RateControlledEntity entity = ref.get(nextNormalPosition);
			nextNormalPosition++;
			num_checked++;
			if (entity.canProcess(readWaiter)) {	//is ready
				return entity;
			}
		}

		return null;	//none found ready
	}


	private RateControlledEntity getNextReadyHighPriorityEntity() {
		ArrayList<RateControlledEntity> ref = highPriorityEntities;

		int size = ref.size();
		int numChecked = 0;

		while (numChecked < size) {
			entityCheckCount++;
			nextHighPosition = nextHighPosition >= size ? 0 : nextHighPosition; //make circular
			RateControlledEntity entity = ref.get(nextHighPosition);
			nextHighPosition++;
			numChecked++;
			if (entity.canProcess(readWaiter)) { //is ready
				return entity;
			}
		}

		return null;	//none found ready
	}



	/**
	 * Add the given entity to the controller for read processing.
	 * @param entity to process reads for
	 */
	public void addReadEntity(RateControlledEntity entity) {
		try {	
			entitiesMon.enter();
			if (entity.getPriority() == RateControlledEntity.PRIORITY_HIGH) {
				//copy-on-write
				ArrayList<RateControlledEntity> highNew = new ArrayList<RateControlledEntity>(highPriorityEntities.size() + 1);
				highNew.addAll(highPriorityEntities);
				highNew.add(entity);
				highPriorityEntities = highNew;
			} else {
				//copy-on-write
				ArrayList<RateControlledEntity> normNew = new ArrayList<RateControlledEntity>(normalPriorityEntities.size() + 1);
				normNew.addAll(normalPriorityEntities);
				normNew.add(entity);
				normalPriorityEntities = normNew;
			}

			entityCount = normalPriorityEntities.size() + highPriorityEntities.size();
		} finally {	entitiesMon.exit();	}

		readWaiter.eventOccurred();
	}


	/**
	 * Remove the given entity from the controller.
	 * @param entity to remove from read processing
	 */
	public void removeReadEntity(RateControlledEntity entity) {
		try {	
			entitiesMon.enter();
			if (entity.getPriority() == RateControlledEntity.PRIORITY_HIGH) {
				//copy-on-write
				ArrayList<RateControlledEntity> high_new = new ArrayList<RateControlledEntity>(highPriorityEntities);
				high_new.remove(entity);
				highPriorityEntities = high_new;
			}
			else {
				//copy-on-write
				ArrayList<RateControlledEntity> norm_new = new ArrayList<RateControlledEntity>(normalPriorityEntities);
				norm_new.remove(entity);
				normalPriorityEntities = norm_new;
			}

			entityCount = normalPriorityEntities.size() + highPriorityEntities.size();
		} finally {	
			entitiesMon.exit();	
		}
	}

	public int getEntityCount() {
		return (entityCount);
	}
}
