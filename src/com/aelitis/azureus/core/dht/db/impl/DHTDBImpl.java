/*
 * Created on 28-Jan-2005
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

package com.aelitis.azureus.core.dht.db.impl;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.*;

import org.gudy.azureus2.core3.ipfilter.IpFilter;
import org.gudy.azureus2.core3.ipfilter.IpFilterManagerFactory;
import org.gudy.azureus2.core3.util.AEMonitor;
import org.gudy.azureus2.core3.util.AESemaphore;
import org.gudy.azureus2.core3.util.AEThread2;
import org.gudy.azureus2.core3.util.AddressUtils;
import org.gudy.azureus2.core3.util.ByteArrayHashMap;
import org.gudy.azureus2.core3.util.ByteFormatter;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.HashWrapper;
import org.gudy.azureus2.core3.util.RandomUtils;
import org.gudy.azureus2.core3.util.SimpleTimer;
import org.gudy.azureus2.core3.util.SystemTime;
import org.gudy.azureus2.core3.util.TimerEvent;
import org.gudy.azureus2.core3.util.TimerEventPerformer;
import org.gudy.azureus2.core3.util.TimerEventPeriodic;

import com.aelitis.azureus.core.dht.DHT;
import com.aelitis.azureus.core.dht.DHTLogger;
import com.aelitis.azureus.core.dht.DHTOperationAdapter;
import com.aelitis.azureus.core.dht.DHTStorageAdapter;
import com.aelitis.azureus.core.dht.DHTStorageBlock;
import com.aelitis.azureus.core.dht.DHTStorageKey;
import com.aelitis.azureus.core.dht.DHTStorageKeyStats;
import com.aelitis.azureus.core.dht.db.*;
import com.aelitis.azureus.core.dht.impl.DHTLog;
import com.aelitis.azureus.core.dht.router.DHTRouter;
import com.aelitis.azureus.core.dht.control.DHTControl;
import com.aelitis.azureus.core.dht.transport.DHTTransportContact;
import com.aelitis.azureus.core.dht.transport.DHTTransportQueryStoreReply;
import com.aelitis.azureus.core.dht.transport.DHTTransportReplyHandlerAdapter;
import com.aelitis.azureus.core.dht.transport.DHTTransportValue;
import com.aelitis.azureus.core.dht.transport.udp.DHTTransportUDP;
import com.aelitis.azureus.core.util.FeatureAvailability;
import com.aelitis.azureus.core.util.bloom.BloomFilter;
import com.aelitis.azureus.core.util.bloom.BloomFilterFactory;

/**
 * @author parg
 *
 */

public class DHTDBImpl implements DHTDB, DHTDBStats {
	
	private static final int MAX_VALUE_LIFETIME	= 3*24*60*60*1000;


	private final int			originalRepublishInterval;

	// the grace period gives the originator time to republish their data as this could involve
	// some work on their behalf to find closest nodes etc. There's no real urgency here anyway

	public static final int			ORIGINAL_REPUBLISH_INTERVAL_GRACE	= 60*60*1000;

	private static final boolean	ENABLE_PRECIOUS_STUFF			= false;
	private static final int		PRECIOUS_CHECK_INTERVAL			= 2*60*60*1000;

	private final int			cacheRepublishInterval;

	private static final long		MIN_CACHE_EXPIRY_CHECK_INTERVAL		= 60*1000;
	private long		last_cache_expiry_check;

	private static final long	IP_BLOOM_FILTER_REBUILD_PERIOD		= 15*60*1000;
	private static final int	IP_COUNT_BLOOM_SIZE_INCREASE_CHUNK	= 1000;

	private BloomFilter	ip_count_bloom_filter = BloomFilterFactory.createAddRemove8Bit(IP_COUNT_BLOOM_SIZE_INCREASE_CHUNK);

	private static final int	VALUE_VERSION_CHUNK = 128;
	private int	next_value_version;
	private int next_value_version_left;


	protected static final int		QUERY_STORE_REQUEST_ENTRY_SIZE	= 6;
	protected static final int		QUERY_STORE_REPLY_ENTRY_SIZE	= 2;

	final Map<HashWrapper,DHTDBMapping> storedValues = new HashMap<HashWrapper,DHTDBMapping>();
	private final Map<DHTDBMapping.ShortHash,DHTDBMapping>	storedValuesPrefixMap	= new HashMap<DHTDBMapping.ShortHash,DHTDBMapping>();

	private DHTControl				control;
	private final DHTStorageAdapter	adapter;
	private DHTRouter				router;
	private DHTTransportContact		localContact;
	final DHTLogger					logger;

	private static final long	MAX_TOTAL_SIZE	= 4*1024*1024;

	private int		totalSize;
	private int		totalValues;
	private int		totalKeys;
	private int		totalLocalKeys;


	private boolean forceOriginalRepublish;

	private final IpFilter	ipFilter	= IpFilterManagerFactory.getSingleton().getIPFilter();

	final AEMonitor	thisMon	= new AEMonitor("DHTDB");

	private static final boolean	DEBUG_SURVEY		= false;
	private static final boolean	SURVEY_ONLY_RF_KEYS	= true;


	private static final int	SURVEY_PERIOD					= DEBUG_SURVEY?1*60*1000:15*60*1000;
	private static final int	SURVEY_STATE_INACT_TIMEOUT		= DEBUG_SURVEY?5*60*1000:60*60*1000;
	private static final int	SURVEY_STATE_MAX_LIFE_TIMEOUT	= 3*60*60*1000 + 30*60*1000;
	private static final int	SURVEY_STATE_MAX_LIFE_RAND		= 1*60*60*1000;

	private static final int	MAX_SURVEY_SIZE			= 100;
	private static final int	MAX_SURVEY_STATE_SIZE	= 150;

	private final boolean	surveyEnabled;

	private volatile boolean surveyInProgress;

	private final Map<HashWrapper,Long>	surveyMappingTimes = new HashMap<HashWrapper, Long>();

	private final Map<HashWrapper,SurveyContactState>	surveyState =
		new LinkedHashMap<HashWrapper,SurveyContactState>(MAX_SURVEY_STATE_SIZE,0.75f,true) {
			protected boolean removeEldestEntry(
		   		Map.Entry<HashWrapper,SurveyContactState> eldest) {
				return size() > MAX_SURVEY_STATE_SIZE;
			}
		};

	private TimerEventPeriodic			preciousTimer;
	private TimerEventPeriodic			originalRepublishTimer;
	private TimerEventPeriodic			cacheRepublishTimer;
	private final TimerEventPeriodic	bloomTimer;
	private TimerEventPeriodic			surveyTimer;


	private boolean	sleeping;
	private boolean	suspended;

	private volatile boolean	destroyed;

	public DHTDBImpl(
		DHTStorageAdapter	_adapter,
		int					_originalRepublishInterval,
		int					_cacheRepublishInterval,
		byte				_protocolVersion,
		DHTLogger			_logger) {
		
		adapter						= _adapter==null?null:new adapterFacade(_adapter);
		originalRepublishInterval	= _originalRepublishInterval;
		cacheRepublishInterval		= _cacheRepublishInterval;
		logger						= _logger;

		surveyEnabled =
			_protocolVersion >= DHTTransportUDP.PROTOCOL_VERSION_REPLICATION_CONTROL3 &&
			(	adapter == null ||
				adapter.getNetwork() == DHT.NW_CVS ||
				FeatureAvailability.isDHTRepV2Enabled()
			);

		if (ENABLE_PRECIOUS_STUFF) {
			preciousTimer = SimpleTimer.addPeriodicEvent(
				"DHTDB:precious",
				PRECIOUS_CHECK_INTERVAL/4,
				true, // absolute, we don't want effective time changes (computer suspend/resume) to shift these
				new TimerEventPerformer() {
					public void perform(TimerEvent event) {
						checkPreciousStuff();
					}
				});
		}

		if (originalRepublishInterval > 0) {

			originalRepublishTimer = SimpleTimer.addPeriodicEvent(
				"DHTDB:op",
				originalRepublishInterval,
				true, // absolute, we don't want effective time changes (computer suspend/resume) to shift these
				new TimerEventPerformer() {
					public void perform(TimerEvent event) {
						logger.log("Republish of original mappings starts");
						long start = SystemTime.getCurrentTime();
						int stats = republishOriginalMappings();
						long end = SystemTime.getCurrentTime();
						logger.log("Republish of original mappings completed in " + (end-start) + ": " +
									"values = " + stats);
					}
				});
		}

		if (cacheRepublishInterval > 0) {
			// random skew here so that cache refresh isn't very synchronised, as the optimisations
			// regarding non-republising benefit from this
			cacheRepublishTimer = SimpleTimer.addPeriodicEvent(
					"DHTDB:cp",
					cacheRepublishInterval + 10000 - RandomUtils.nextInt(20000),
					true,	// absolute, we don't want effective time changes (computer suspend/resume) to shift these
					new TimerEventPerformer() {
						public void perform(TimerEvent event) {
							logger.log("Republish of cached mappings starts");
							long	start 	= SystemTime.getCurrentTime();
							int[]	stats = republishCachedMappings();
							long	end 	= SystemTime.getCurrentTime();
							logger.log("Republish of cached mappings completed in " + (end-start) + ": " +
										"values = " + stats[0] + ", keys = " + stats[1] + ", ops = " + stats[2]);
							if (forceOriginalRepublish) {
								forceOriginalRepublish	= false;
								logger.log("Force republish of original mappings due to router change starts");
								start 	= SystemTime.getCurrentTime();
								int stats2 = republishOriginalMappings();
								end 	= SystemTime.getCurrentTime();
								logger.log("Force republish of original mappings due to router change completed in " + (end-start) + ": " +
											"values = " + stats2);
							}
						}
					});
		}


		bloomTimer = SimpleTimer.addPeriodicEvent(
				"DHTDB:bloom",
				IP_BLOOM_FILTER_REBUILD_PERIOD,
				new TimerEventPerformer() {
					public void perform(TimerEvent event) {
						try {
							thisMon.enter();
							rebuildIPBloomFilter(false);
						} finally {
							thisMon.exit();
						}
					}
				});

		if (surveyEnabled) {
			surveyTimer = SimpleTimer.addPeriodicEvent(
				"DHTDB:survey",
				SURVEY_PERIOD,
				true,
				new TimerEventPerformer() {
					public void perform(TimerEvent	event) {
						survey();
					}
				}
			);
		}
	}


	public void setControl(
		DHTControl		_control) {
		control			= _control;
			// trigger an "original value republish" if router has changed
		forceOriginalRepublish = router != null;
		router			= control.getRouter();
		localContact	= control.getTransport().getLocalContact();
			// our ID has changed - amend the originator of all our values
		try {
			thisMon.enter();
			surveyState.clear();
			Iterator<DHTDBMapping>	it = storedValues.values().iterator();
			while (it.hasNext()) {
				DHTDBMapping	mapping = it.next();
				mapping.updateLocalContact(localContact);
			}
		} finally {
			thisMon.exit();
		}
	}

	public DHTDBValue store(
		HashWrapper		key,
		byte[]			value,
		short			flags,
		byte			life_hours,
		byte			replication_control) {
		// local store
		if ((flags & DHT.FLAG_PUT_AND_FORGET ) == 0) {
			if ((flags & DHT.FLAG_OBFUSCATE_LOOKUP ) != 0) {
				Debug.out("Obfuscated puts without 'put-and-forget' are not supported as original-republishing of them is not implemented");
			}
			if (life_hours > 0) {
				if (life_hours*60*60*1000 < originalRepublishInterval) {
					Debug.out("Don't put persistent values with a lifetime less than republish period - lifetime over-ridden");
					life_hours = 0;
				}
			}
			try {
				thisMon.enter();
				totalLocalKeys++;
				// don't police max check for locally stored data
				// only that received
				DHTDBMapping	mapping = (DHTDBMapping)storedValues.get(key);
				if (mapping == null) {
					mapping = new DHTDBMapping(this, key, true);
					storedValues.put(key, mapping);
					addToPrefixMap(mapping);
				}
				DHTDBValueImpl res =
					new DHTDBValueImpl(
							SystemTime.getCurrentTime(),
							value,
							getNextValueVersion(),
							localContact,
							localContact,
							true,
							flags,
							life_hours,
							replication_control);
				mapping.add(res);
				return (res);
			} finally {
				thisMon.exit();
			}
		} else {
			DHTDBValueImpl res =
				new DHTDBValueImpl(
						SystemTime.getCurrentTime(),
						value,
						getNextValueVersion(),
						localContact,
						localContact,
						true,
						flags,
						life_hours,
						replication_control);
			return (res);
		}
	}

	/*
	private long store_ops;
	private long store_ops_bad1;
	private long store_ops_bad2;

	private void logStoreOps() {
		System.out.println("sops (" + control.getTransport().getNetwork() + ")=" + store_ops + ",bad1=" + store_ops_bad1 + ",bad2=" + store_ops_bad2);
	}
	*/

	public byte store(
		DHTTransportContact 	sender,
		HashWrapper				key,
		DHTTransportValue[]		values) {
		// allow 4 bytes per value entry to deal with overhead (prolly should be more but we're really
		// trying to deal with 0-length value stores)
		if (totalSize + (totalValues*4) > MAX_TOTAL_SIZE) {
			DHTLog.log("Not storing " + DHTLog.getString2(key.getHash()) + " as maximum storage limit exceeded");
			return (DHT.DT_SIZE);
		}
		// logStoreOps();
		try {
			thisMon.enter();
			if (sleeping || suspended) {
				return (DHT.DT_NONE);
			}
			checkCacheExpiration(false);
			DHTDBMapping	mapping = (DHTDBMapping)storedValues.get(key);
			if (mapping == null) {
				mapping = new DHTDBMapping(this, key, false);
				storedValues.put(key, mapping);
				addToPrefixMap(mapping);
			}
			// we carry on an update as its ok to replace existing entries
			// even if diversified
			for (int i=0;i<values.length;i++) {
				DHTTransportValue	value = values[i];
				DHTDBValueImpl mapping_value	= new DHTDBValueImpl(sender, value, false);
				mapping.add(mapping_value);
			}
			return (mapping.getDiversificationType());
		} finally {
			thisMon.exit();
		}
	}

	public DHTDBLookupResult
	get(
		DHTTransportContact		reader,
		HashWrapper				key,
		int						max_values,	// 0 -> all
		short					flags,
		boolean					external_request ) {
		try {
			thisMon.enter();

			checkCacheExpiration(false);

			final DHTDBMapping mapping = (DHTDBMapping)storedValues.get(key);

			if (mapping == null) {

				return (null);
			}

			if (external_request) {

				mapping.addHit();
			}

			final DHTDBValue[]	values = mapping.get(reader, max_values, flags);

			return (
				new DHTDBLookupResult() {
					public DHTDBValue[]
					getValues() {
						return (values);
					}

					public byte
					getDiversificationType() {
						return ( mapping.getDiversificationType());
					}
				});

		} finally {

			thisMon.exit();
		}
	}

	public DHTDBValue
	get(
		HashWrapper				key) {
			// local get

		try {
			thisMon.enter();

			DHTDBMapping mapping = (DHTDBMapping)storedValues.get(key);

			if (mapping != null) {

				return (mapping.get( localContact));
			}

			return (null);

		} finally {

			thisMon.exit();
		}
	}

	public DHTDBValue
	getAnyValue(
		HashWrapper				key) {
		try {
			thisMon.enter();

			DHTDBMapping mapping = (DHTDBMapping)storedValues.get(key);

			if (mapping != null) {

				return (mapping.getAnyValue( localContact));
			}

			return (null);

		} finally {

			thisMon.exit();
		}
	}

	public List<DHTDBValue>
	getAllValues(
		HashWrapper				key) {
		try {
			thisMon.enter();
			DHTDBMapping mapping = (DHTDBMapping)storedValues.get(key);
			List<DHTDBValue> result = new ArrayList<DHTDBValue>();
			if (mapping != null) {
				result.addAll(mapping.getAllValues( localContact));
			}
			return (result);
		} finally {
			thisMon.exit();
		}
	}

	public boolean hasKey(
		HashWrapper		key) {
		try {
			thisMon.enter();
			return (storedValues.containsKey( key));
		} finally {
			thisMon.exit();
		}
	}

	public DHTDBValue
	remove(
		DHTTransportContact 	originator,
		HashWrapper				key) {
			// local remove

		try {
			thisMon.enter();

			DHTDBMapping mapping = (DHTDBMapping)storedValues.get(key);

			if (mapping != null) {

				DHTDBValueImpl	res = mapping.remove(originator);

				if (res != null) {

					totalLocalKeys--;

					if (!mapping.getValues().hasNext()) {

						storedValues.remove(key);

						removeFromPrefixMap(mapping);

						mapping.destroy();
					}

					return ( res.getValueForDeletion( getNextValueVersion()));
				}

				return (null);
			}

			return (null);

		} finally {

			thisMon.exit();
		}
	}

	public DHTStorageBlock
	keyBlockRequest(
		DHTTransportContact		direct_sender,
		byte[]					request,
		byte[]					signature) {
		if (adapter == null) {

			return (null);
		}

			// for block requests sent to us (as opposed to being returned from other operations)
			// make sure that the key is close enough to us

		if (direct_sender != null) {

			byte[]	key = adapter.getKeyForKeyBlock(request);

			List<DHTTransportContact> closest_contacts = control.getClosestKContactsList(key, true);

			boolean	process_it	= false;

			for (int i=0;i<closest_contacts.size();i++) {

				if (router.isID(closest_contacts.get(i).getID())) {

					process_it	= true;

					break;
				}
			}

			if (!process_it) {

				DHTLog.log("Not processing key block for  " + DHTLog.getString2(key) + " as key too far away");

				return (null);
			}

			if (! control.verifyContact( direct_sender, true)) {

				DHTLog.log("Not processing key block for  " + DHTLog.getString2(key) + " as verification failed");

				return (null);
			}
		}

		return (adapter.keyBlockRequest( direct_sender, request, signature));
	}

	public DHTStorageBlock
	getKeyBlockDetails(
		byte[]		key) {
		if (adapter == null) {

			return (null);
		}

		return (adapter.getKeyBlockDetails( key));
	}

	public boolean isKeyBlocked(
		byte[]		key) {
		return (getKeyBlockDetails(key) != null);
	}

	public DHTStorageBlock[]
	getDirectKeyBlocks() {
		if (adapter == null) {

			return (new DHTStorageBlock[0]);
		}

		return ( adapter.getDirectKeyBlocks());
	}

	public boolean isEmpty() {
		return (totalKeys == 0);
	}

	public int getKeyCount() {
		return ((int)totalKeys);
	}

	public int getLocalKeyCount() {
		return (totalLocalKeys);
	}

	public int getValueCount() {
		return ((int)totalValues);
	}

	public int getSize() {
		return ((int)totalSize);
	}

	public int[] getValueDetails() {
		try {
			thisMon.enter();
			int[]	res = new int[6];
			Iterator<DHTDBMapping>	it = storedValues.values().iterator();
			while (it.hasNext()) {
				DHTDBMapping mapping = it.next();
				res[DHTDBStats.VD_VALUE_COUNT] += mapping.getValueCount();
				res[DHTDBStats.VD_LOCAL_SIZE] += mapping.getLocalSize();
				res[DHTDBStats.VD_DIRECT_SIZE] += mapping.getDirectSize();
				res[DHTDBStats.VD_INDIRECT_SIZE] += mapping.getIndirectSize();
				int	dt = mapping.getDiversificationType();
				if (dt == DHT.DT_FREQUENCY) {
					res[DHTDBStats.VD_DIV_FREQ]++;
				} else if (dt == DHT.DT_SIZE) {
					res[DHTDBStats.VD_DIV_SIZE]++;
					/*
					Iterator<DHTDBValueImpl> it2 = mapping.getIndirectValues();
					System.out.println("values=" + mapping.getValueCount());
					while (it2.hasNext()) {
						DHTDBValueImpl val = it2.next();
						System.out.println(new String( val.getValue()) + " - " + val.getOriginator().getAddress());
					}
					*/
				}
			}
			return (res);
		} finally {
			thisMon.exit();
		}
	}

	public int getKeyBlockCount() {
		if (adapter == null) {
			return (0);
		}
		return (adapter.getDirectKeyBlocks().length);
	}

	public Iterator<HashWrapper> getKeys() {
		try {
			thisMon.enter();
			return (new ArrayList<HashWrapper>(storedValues.keySet()).iterator());
		} finally {
			thisMon.exit();
		}
	}

	protected int republishOriginalMappings() {
		if (suspended) {
			logger.log("Original republish skipped as suspended");
			return (0);
		}
		int	valuesPublished	= 0;
		Map<HashWrapper,List<DHTDBValueImpl>>	republish = new HashMap<HashWrapper,List<DHTDBValueImpl>>();
		try {
			thisMon.enter();
			Iterator<Map.Entry<HashWrapper,DHTDBMapping>> it = storedValues.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<HashWrapper,DHTDBMapping>	entry = it.next();
				HashWrapper		key		= (HashWrapper)entry.getKey();
				DHTDBMapping	mapping	= (DHTDBMapping)entry.getValue();
				Iterator<DHTDBValueImpl>	it2 = mapping.getValues();
				List<DHTDBValueImpl>	values = new ArrayList<DHTDBValueImpl>();
				while (it2.hasNext()) {
					DHTDBValueImpl	value = it2.next();
					if (value != null && value.isLocal()) {
						// we're republising the data, reset the creation time
						value.setCreationTime();
						values.add(value);
					}
				}
				if (values.size() > 0) {
					republish.put(key, values);
				}
			}
		} finally {
			thisMon.exit();
		}
		Iterator<Map.Entry<HashWrapper,List<DHTDBValueImpl>>>	it = republish.entrySet().iterator();
		int keyTot	= republish.size();
		int	keyNum = 0;
		while (it.hasNext()) {
			keyNum++;
			Map.Entry<HashWrapper,List<DHTDBValueImpl>>	entry = it.next();
			HashWrapper			key		= (HashWrapper)entry.getKey();
			List<DHTDBValueImpl>		values	= entry.getValue();
				// no point in worry about multi-value puts here as it is extremely unlikely that
				// > 1 value will locally stored, or > 1 value will go to the same contact
			for (int i=0;i<values.size();i++) {
				valuesPublished++;
				control.putEncodedKey(key.getHash(), "Republish orig: " + keyNum + " of " + keyTot, values.get(i), 0, true);
			}
		}
		return (valuesPublished);
	}

	protected int[] republishCachedMappings() {
		if (suspended) {
			logger.log("Cache republish skipped as suspended");
			return (new int[]{ 0, 0, 0 });
		}
			// first refresh any leaves that have not performed at least one lookup in the
			// last period
		router.refreshIdleLeaves(cacheRepublishInterval);
		final Map<HashWrapper,List<DHTDBValueImpl>>	republish = new HashMap<HashWrapper,List<DHTDBValueImpl>>();
		List<DHTDBMapping>	republish_via_survey = new ArrayList<DHTDBMapping>();
		long	now = System.currentTimeMillis();
		try {
			thisMon.enter();
			checkCacheExpiration(true);
			Iterator<Map.Entry<HashWrapper,DHTDBMapping>>	it = storedValues.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<HashWrapper,DHTDBMapping>	entry = it.next();
				HashWrapper			key		= entry.getKey();
				DHTDBMapping		mapping	= entry.getValue();
				
				// assume that if we've diversified then the other k-1 locations are under similar
				// stress and will have done likewise - no point in republishing cache values to them
				// New nodes joining will have had stuff forwarded to them regardless of diversification
				// status
				if (mapping.getDiversificationType() != DHT.DT_NONE) {
					continue;
				}
				Iterator<DHTDBValueImpl>	it2 = mapping.getValues();
				boolean	all_rf_values = it2.hasNext();
				List<DHTDBValueImpl>	values = new ArrayList<DHTDBValueImpl>();
				while (it2.hasNext()) {
					DHTDBValueImpl	value = it2.next();
					if (value.isLocal()) {
						all_rf_values = false;
					} else {
						if (value.getReplicationFactor() == DHT.REP_FACT_DEFAULT) {
							all_rf_values = false;
						}
						
						// if this value was stored < period ago then we assume that it was
						// also stored to the other k-1 locations at the same time and therefore
						// we don't need to re-store it
						if (now < value.getStoreTime()) {
							// deal with clock changes
							value.setStoreTime(now);
						} else if (now - value.getStoreTime() <= cacheRepublishInterval) {
							// System.out.println("skipping store");
						} else {
							values.add(value);
						}
					}
				}
				if (all_rf_values) {
					// if surveying is disabled then we swallow values here to prevent them
					// from being replicated using the existing technique and muddying the waters
					values.clear();	// handled by the survey process
					republish_via_survey.add(mapping);
				}
				if (values.size() > 0) {
					republish.put(key, values);
				}
			}
		} finally {
			thisMon.exit();
		}
		if (republish_via_survey.size() > 0) {
			// we still check for being too far away here
			List<HashWrapper>	stop_caching = new ArrayList<HashWrapper>();
			for (DHTDBMapping mapping: republish_via_survey) {
				HashWrapper			key		= mapping.getKey();
				byte[]	lookup_id	= key.getHash();
				List<DHTTransportContact>	contacts = control.getClosestKContactsList(lookup_id, false);
				// if we are no longer one of the K closest contacts then we shouldn't
				// cache the value
				boolean	keep_caching	= false;
				for (int j=0;j<contacts.size();j++) {
					if (router.isID(((DHTTransportContact)contacts.get(j)).getID())) {
						keep_caching	= true;
						break;
					}
				}
				if (!keep_caching) {
					DHTLog.log("Dropping cache entry for " + DHTLog.getString( lookup_id ) + " as now too far away");
					stop_caching.add(key);
				}
			}
			if (stop_caching.size() > 0) {
				try {
					thisMon.enter();
					for (int i=0;i<stop_caching.size();i++) {
						DHTDBMapping	mapping = (DHTDBMapping)storedValues.remove( stop_caching.get(i));
						if (mapping != null) {
							removeFromPrefixMap(mapping);
							mapping.destroy();
						}
					}
				} finally {
					thisMon.exit();
				}
			}
		}
		final int[]	values_published	= {0};
		final int[]	keys_published		= {0};
		final int[]	republish_ops		= {0};
		final HashSet<DHTTransportContact>	anti_spoof_done	= new HashSet<DHTTransportContact>();
		if (republish.size() > 0) {
			// System.out.println("cache replublish");
			
			// The approach is to refresh all leaves in the smallest subtree, thus populating the tree with
			// sufficient information to directly know which nodes to republish the values
			// to.
			// However, I'm going to rely on the "refresh idle leaves" logic above
			// (that's required to keep the DHT alive in general) to ensure that all
			// k-buckets are reasonably up-to-date
			Iterator<Map.Entry<HashWrapper,List<DHTDBValueImpl>>>	it1 = republish.entrySet().iterator();
			List<HashWrapper>	stop_caching = new ArrayList<HashWrapper>();
				// build a map of contact -> list of keys to republish
			Map<HashWrapper,Object[]>	contact_map	= new HashMap<HashWrapper,Object[]>();
			while (it1.hasNext()) {
				Map.Entry<HashWrapper,List<DHTDBValueImpl>>	entry = it1.next();
				HashWrapper			key		= entry.getKey();
				byte[]	lookup_id	= key.getHash();
				
				// just use the closest contacts - if some have failed then they'll
				// get flushed out by this operation. Grabbing just the live ones
				// is a bad idea as failures may rack up against the live ones due
				// to network problems and kill them, leaving the dead ones!
				List<DHTTransportContact>	contacts = control.getClosestKContactsList(lookup_id, false);
				
				// if we are no longer one of the K closest contacts then we shouldn't
				// cache the value
				boolean	keep_caching	= false;
				for (int j=0;j<contacts.size();j++) {
					if (router.isID(((DHTTransportContact)contacts.get(j)).getID())) {
						keep_caching	= true;
						break;
					}
				}
				if (!keep_caching) {
					DHTLog.log("Dropping cache entry for " + DHTLog.getString( lookup_id ) + " as now too far away");
					stop_caching.add(key);
					// we carry on and do one last publish
				}
				for (int j=0;j<contacts.size();j++) {
					DHTTransportContact	contact = (DHTTransportContact)contacts.get(j);
					if (router.isID( contact.getID())) {
						continue;	// ignore ourselves
					}
					Object[]	data = (Object[])contact_map.get(new HashWrapper(contact.getID()));
					if (data == null) {
						data	= new Object[]{ contact, new ArrayList<HashWrapper>()};
						contact_map.put(new HashWrapper(contact.getID()), data);
					}
					((List<HashWrapper>)data[1]).add(key);
				}
			}
			Iterator<Object[]> it2 = contact_map.values().iterator();
			final int	con_tot 	= contact_map.size();
			int con_num 	= 0;
			while (it2.hasNext()) {
				con_num++;
				final int f_con_num = con_num;
				final Object[]	data = it2.next();
				final DHTTransportContact	contact = (DHTTransportContact)data[0];
				
				// move to anti-spoof on cache forwards - gotta do a find-node first
				// to get the random id
				final AESemaphore	sem = new AESemaphore("DHTDB:cacheForward");
				contact.sendFindNode(
						new DHTTransportReplyHandlerAdapter() {
							public void findNodeReply(
								DHTTransportContact 	_contact,
								DHTTransportContact[]	_contacts) {
								anti_spoof_done.add(_contact);
								try {
									// System.out.println("cacheForward: pre-store findNode OK");
									List<HashWrapper>				keys	= (List<HashWrapper>)data[1];
									byte[][]				store_keys 		= new byte[keys.size()][];
									DHTTransportValue[][]	store_values 	= new DHTTransportValue[store_keys.length][];
									keys_published[0] += store_keys.length;
									for (int i=0;i<store_keys.length;i++) {
										HashWrapper	wrapper = keys.get(i);
										store_keys[i] = wrapper.getHash();
										List<DHTDBValueImpl>		values	= republish.get(wrapper);
										store_values[i] = new DHTTransportValue[values.size()];
										values_published[0] += store_values[i].length;
										for (int j=0;j<values.size();j++) {
											DHTDBValueImpl	value	= values.get(j);
											// we reduce the cache distance by 1 here as it is incremented by the
											// recipients
											store_values[i][j] = value.getValueForRelay(localContact);
										}
									}
									List<DHTTransportContact>	contacts = new ArrayList<DHTTransportContact>();
									contacts.add(contact);
									republish_ops[0]++;
									control.putDirectEncodedKeys(
											store_keys,
											"Republish cache: " + f_con_num + " of " + con_tot,
											store_values,
											contacts);
								} finally {
									sem.release();
								}
							}
							public void failed(
								DHTTransportContact 	_contact,
								Throwable				_error) {
								try {
									// System.out.println("cacheForward: pre-store findNode Failed");
									DHTLog.log("cacheForward: pre-store findNode failed " + DHTLog.getString( _contact) + " -> failed: " + _error.getMessage());
									router.contactDead( _contact.getID(), false);
								} finally {
									sem.release();
								}
							}
						},
						contact.getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_ANTI_SPOOF2?new byte[0]:new byte[20],
						DHT.FLAG_LOOKUP_FOR_STORE);
				sem.reserve();
			}
			
			try {
				thisMon.enter();
				for (int i=0;i<stop_caching.size();i++) {
					DHTDBMapping	mapping = (DHTDBMapping)storedValues.remove( stop_caching.get(i));
					if (mapping != null) {
						removeFromPrefixMap(mapping);
						mapping.destroy();
					}
				}
			} finally {
				thisMon.exit();
			}
		}
		DHTStorageBlock[]	direct_key_blocks = getDirectKeyBlocks();
		if (direct_key_blocks.length > 0) {
			for (int i=0;i<direct_key_blocks.length;i++) {
				final DHTStorageBlock	key_block = direct_key_blocks[i];
				List	contacts = control.getClosestKContactsList(key_block.getKey(), false);
				boolean	forward_it = false;
				
				// ensure that the key is close enough to us
				for (int j=0;j<contacts.size();j++) {
					final DHTTransportContact	contact = (DHTTransportContact)contacts.get(j);
					if (router.isID( contact.getID())) {
						forward_it	= true;
						break;
					}
				}
				for (int j=0; forward_it && j<contacts.size();j++) {
					final DHTTransportContact	contact = (DHTTransportContact)contacts.get(j);
					if (key_block.hasBeenSentTo( contact)) {
						continue;
					}
					if (router.isID( contact.getID())) {
						continue;	// ignore ourselves
					}
					if (contact.getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_BLOCK_KEYS) {
						final Runnable task =
							new Runnable() {
								public void run() {
									contact.sendKeyBlock(
										new DHTTransportReplyHandlerAdapter() {
											public void keyBlockReply(
												DHTTransportContact 	_contact) {
												DHTLog.log("key block forward ok " + DHTLog.getString( _contact));
												key_block.sentTo(_contact);
											}
											public void failed(
												DHTTransportContact 	_contact,
												Throwable				_error) {
												DHTLog.log("key block forward failed " + DHTLog.getString( _contact) + " -> failed: " + _error.getMessage());
											}
										},
										key_block.getRequest(),
										key_block.getCertificate());
								}
							};
							if (anti_spoof_done.contains( contact)) {
								task.run();
							} else {
								contact.sendFindNode(
										new DHTTransportReplyHandlerAdapter() {
											public void findNodeReply(
												DHTTransportContact 	contact,
												DHTTransportContact[]	contacts) {
												task.run();
											}
											public void failed(
												DHTTransportContact 	_contact,
												Throwable				_error) {
												// System.out.println("nodeAdded: pre-store findNode Failed");
												DHTLog.log("pre-kb findNode failed " + DHTLog.getString( _contact) + " -> failed: " + _error.getMessage());
												router.contactDead( _contact.getID(), false);
											}
										},
										contact.getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_ANTI_SPOOF2?new byte[0]:new byte[20],
										DHT.FLAG_LOOKUP_FOR_STORE);
							}
					}
				}
			}
		}
		return (new int[]{ values_published[0], keys_published[0], republish_ops[0] });
	}

	protected void checkCacheExpiration(boolean force) {
		long	 now = SystemTime.getCurrentTime();
		if (!force) {
			long elapsed = now - last_cache_expiry_check;
			if (elapsed > 0 && elapsed < MIN_CACHE_EXPIRY_CHECK_INTERVAL) {
				return;
			}
		}
		try {
			thisMon.enter();
			last_cache_expiry_check	= now;
			Iterator<DHTDBMapping>	it = storedValues.values().iterator();
			while (it.hasNext()) {
				DHTDBMapping	mapping = it.next();
				if (mapping.getValueCount() == 0) {
					it.remove();
					removeFromPrefixMap(mapping);
					mapping.destroy();
				} else {
					Iterator<DHTDBValueImpl>	it2 = mapping.getValues();
					while (it2.hasNext()) {
						DHTDBValueImpl	value = it2.next();
						if (!value.isLocal()) {
								// distance 1 = initial store location. We use the initial creation date
								// when deciding whether or not to remove this, plus a bit, as the
								// original publisher is supposed to republish these
							int life_hours = value.getLifeTimeHours();
							int	max_age;
							if (life_hours < 1) {
								max_age = originalRepublishInterval;
							} else {
								max_age = life_hours * 60*60*1000;
								if (max_age > MAX_VALUE_LIFETIME) {
									max_age = MAX_VALUE_LIFETIME;
								}
							}
							int	grace;
							if ((value.getFlags() & DHT.FLAG_PUT_AND_FORGET ) != 0) {
								grace = 0;
							} else {
									// scale the grace period for short lifetimes
								grace = Math.min(ORIGINAL_REPUBLISH_INTERVAL_GRACE, max_age/4);
							}
							if (now > value.getCreationTime() + max_age + grace) {
								DHTLog.log("removing cache entry (" + value.getString() + ")");
								it2.remove();
							}
						}
					}
				}
			}
		} finally {
			thisMon.exit();
		}
	}

	protected void addToPrefixMap(DHTDBMapping mapping) {
		DHTDBMapping.ShortHash key = mapping.getShortKey();
		DHTDBMapping existing = storedValuesPrefixMap.get(key);
			// possible to have clashes, be consistent in which one we use to avoid
			// confusing other nodes
		if (existing != null) {
			byte[]	existing_full 	= existing.getKey().getBytes();
			byte[]	new_full		= mapping.getKey().getBytes();
			if (control.computeAndCompareDistances( existing_full, new_full, localContact.getID()) < 0) {
				return;
			}
		}
		storedValuesPrefixMap.put(key, mapping);
		if (storedValuesPrefixMap.size() > storedValues.size()) {
			Debug.out("inconsistent");
		}
	}

	protected void removeFromPrefixMap(
		DHTDBMapping		mapping) {
		DHTDBMapping.ShortHash key = mapping.getShortKey();
		DHTDBMapping existing = storedValuesPrefixMap.get(key);
		if (existing == mapping) {
			storedValuesPrefixMap.remove(key);
		}
	}

	protected void checkPreciousStuff() {
		long	 now = SystemTime.getCurrentTime();
		Map<HashWrapper,List<DHTDBValueImpl>>	republish = new HashMap<HashWrapper,List<DHTDBValueImpl>>();
		try {
			thisMon.enter();
			Iterator<Map.Entry<HashWrapper,DHTDBMapping>>	it = storedValues.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<HashWrapper,DHTDBMapping>	entry = it.next();
				HashWrapper		key		= entry.getKey();
				DHTDBMapping	mapping	= entry.getValue();
				Iterator<DHTDBValueImpl>	it2 = mapping.getValues();
				List<DHTDBValueImpl>	values = new ArrayList<DHTDBValueImpl>();
				while (it2.hasNext()) {
					DHTDBValueImpl	value = it2.next();
					if (value.isLocal()) {
						if ((value.getFlags() & DHT.FLAG_PRECIOUS ) != 0) {
							if (now - value.getCreationTime() > PRECIOUS_CHECK_INTERVAL) {
								value.setCreationTime();
								values.add(value);
							}
						}
					}
				}
				if (values.size() > 0) {
					republish.put(key, values);
				}
			}
		} finally {
			thisMon.exit();
		}
		Iterator<Map.Entry<HashWrapper,List<DHTDBValueImpl>>>	it = republish.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<HashWrapper,List<DHTDBValueImpl>>	entry = it.next();
			HashWrapper			key		= entry.getKey();
			List<DHTDBValueImpl>		values	= entry.getValue();
				// no point in worry about multi-value puts here as it is extremely unlikely that
				// > 1 value will locally stored, or > 1 value will go to the same contact
			for (int i=0;i<values.size();i++) {
				control.putEncodedKey(key.getHash(), "Precious republish", values.get(i), 0, true);
			}
		}
	}

	protected DHTTransportContact getLocalContact() {
		return (localContact);
	}

	protected DHTStorageAdapter getAdapter() {
		return (adapter);
	}

	protected void log(String str) {
		logger.log(str);
	}

	public DHTDBStats getStats() {
		return (this);
	}

	protected void survey() {
		if (surveyInProgress) {
			return;
		}
		if (DEBUG_SURVEY) {
			System.out.println("surveying");
		}
		checkCacheExpiration(false);
		final byte[]	my_id = router.getID();
		if (DEBUG_SURVEY) {
			System.out.println("    my_id=" + ByteFormatter.encodeString( my_id));
		}
		
		final ByteArrayHashMap<DHTTransportContact>	id_map = new ByteArrayHashMap<DHTTransportContact>();
		List<DHTTransportContact> all_contacts = control.getClosestContactsList(my_id, router.getK()*3, true);
		for (DHTTransportContact contact: all_contacts) {
			id_map.put(contact.getID(), contact);
		}
		
		byte[]	max_key 	= my_id;
		byte[]	max_dist	= null;
		final List<HashWrapper> applicable_keys = new ArrayList<HashWrapper>();
		try {
			thisMon.enter();
			long	now = SystemTime.getMonotonousTime();
			Iterator<SurveyContactState> s_it = surveyState.values().iterator();
			while (s_it.hasNext()) {
				if (s_it.next().timeout(now)) {
					s_it.remove();
				}
			}
			
			Iterator<DHTDBMapping>	it = storedValues.values().iterator();
			Set<HashWrapper>	existing_times = new HashSet<HashWrapper>( surveyMappingTimes.keySet());
			while (it.hasNext()) {
				DHTDBMapping	mapping = it.next();
				HashWrapper hw = mapping.getKey();
				if (existing_times.size() > 0) {
					existing_times.remove(hw);
				}
				if (!applyRF( mapping)) {
					continue;
				}
				applicable_keys.add(hw);
				byte[] key = hw.getBytes();
				/*
				List<DHTTransportContact>	contacts = control.getClosestKContactsList(key, true);
				for (DHTTransportContact c: contacts) {
					id_map.put(c.getID(), c);
				}
				*/
				byte[] distance = control.computeDistance(my_id, key);
				if (max_dist == null || control.compareDistances(distance, max_dist ) > 0) {
					max_dist	= distance;
					max_key 	= key;
				}
			}
				// remove dead mappings
			for (HashWrapper hw: existing_times) {
				surveyMappingTimes.remove(hw);
			}
			logger.log("Survey starts: state size=" + surveyState.size() + ", all keys=" + storedValues.size() + ", applicable keys=" + applicable_keys.size());
		} finally {
			thisMon.exit();
		}
		
		if (DEBUG_SURVEY) {
			System.out.println("    max_key=" + ByteFormatter.encodeString( max_key ) + ", dist=" + ByteFormatter.encodeString( max_dist) + ", initial_contacts=" + id_map.size());
		}
		
		if (max_key == my_id) {
			logger.log("Survey complete - no applicable values");
			return;
		}
		
		// obscure key so we don't leak any keys
		byte[]	obscured_key = control.getObfuscatedKey(max_key);
		final int[]	requery_count = { 0 };
		final boolean[]	processing = { false };
		try {
			surveyInProgress = true;
			control.lookupEncoded(
				obscured_key,
				"Neighbourhood survey: basic",
				0,
				true,
				new DHTOperationAdapter() {
					private final List<DHTTransportContact> contacts = new ArrayList<DHTTransportContact>();
					private boolean	survey_complete;
					
					public void found(DHTTransportContact	contact,
						boolean				is_closest) {
						if (is_closest) {
							synchronized(contacts) {
								if (!survey_complete) {
									contacts.add(contact);
								}
							}
						}
					}
					
					public void complete(boolean timeout) {
						boolean	requeried = false;
						try {
							int	hits	= 0;
							int	misses	= 0;
								// find the closest miss to us and recursively search
							byte[]	min_dist 	= null;
							byte[]	min_id		= null;
							synchronized(contacts) {
								for (DHTTransportContact c: contacts) {
									byte[]	id = c.getID();
									if (id_map.containsKey( id)) {
										hits++;
									} else {
										misses++;
										if (id_map.size() >= MAX_SURVEY_SIZE) {
											log("Max survery size exceeded");
											break;
										}
										id_map.put(id, c);
										byte[] distance = control.computeDistance(my_id, id);
										if (min_dist == null || control.compareDistances(distance, min_dist ) < 0) {
											min_dist	= distance;
											min_id		= id;
										}
									}
								}
								contacts.clear();
							}
							
							// if significant misses then re-query
							if (misses > 0 && misses*100/(hits+misses) >= 25 && id_map.size()< MAX_SURVEY_SIZE) {
								if (requery_count[0]++ < 5) {
									if (DEBUG_SURVEY) {
										System.out.println("requery at " + ByteFormatter.encodeString( min_id));
									}
									
									// don't need to obscure here as its a node-id
									control.lookupEncoded(
										min_id,
										"Neighbourhood survey: level=" + requery_count[0],
										0,
										true,
										this);
									requeried = true;
								} else {
									if (DEBUG_SURVEY) {
										System.out.println("requery limit exceeded");
									}
								}
							} else {
								if (DEBUG_SURVEY) {
									System.out.println("super-neighbourhood=" + id_map.size() + " (hits=" + hits + ", misses=" + misses + ", level=" + requery_count[0] + ")");
								}
							}
						} finally {
							if (!requeried) {
								synchronized(contacts) {
									survey_complete = true;
								}
								if (DEBUG_SURVEY) {
									System.out.println("survey complete: nodes=" + id_map.size());
								}
								processSurvey(my_id, applicable_keys, id_map);
								processing[0] = true;
							}
						}
					}
				});
		} catch (Throwable e) {
			if (!processing[0]) {
				logger.log("Survey complete - no applicable nodes");
				surveyInProgress = false;
			}
		}
	}

	protected void processSurvey(
		byte[]									survey_my_id,
		List<HashWrapper>						applicable_keys,
		ByteArrayHashMap<DHTTransportContact>	survey) {
		boolean went_async = false;
		try {
			byte[][]	node_ids = new byte[survey.size()][];
			int	pos = 0;
			for ( byte[] id: survey.keys()) {
				node_ids[pos++] = id;
			}
			ByteArrayHashMap<List<DHTDBMapping>>	value_map = new ByteArrayHashMap<List<DHTDBMapping>>();
			Map<DHTTransportContact,ByteArrayHashMap<List<DHTDBMapping>>> request_map = new HashMap<DHTTransportContact, ByteArrayHashMap<List<DHTDBMapping>>>();
			Map<DHTDBMapping,List<DHTTransportContact>>	mapping_to_node_map = new HashMap<DHTDBMapping, List<DHTTransportContact>>();
			int max_nodes = Math.min( node_ids.length, router.getK());
			try {
				thisMon.enter();
				Iterator<HashWrapper>	it = applicable_keys.iterator();
				int	value_count = 0;
				while (it.hasNext()) {
					DHTDBMapping	mapping = storedValues.get( it.next());
					if (mapping == null) {
						continue;
					}
					value_count++;
					final byte[] key = mapping.getKey().getBytes();
						// find closest nodes to this key in order to asses availability
					Arrays.sort(
						node_ids,
						new Comparator<byte[]>() {
							public int compare(
								byte[] o1,
								byte[] o2 ) {
								return (control.computeAndCompareDistances( o1, o2, key));
							}
						});
					boolean	found_myself = false;
					for (int i=0;i<max_nodes;i++) {
						byte[]	id = node_ids[i];
						if (Arrays.equals( survey_my_id, id)) {
							found_myself = true;
							break;
						}
					}
					// if we're not in the closest set to this key then ignore it
					if (!found_myself) {
						if (DEBUG_SURVEY) {
							System.out.println("we're not in closest set for " + ByteFormatter.encodeString( key ) + " - ignoring");
						}
						continue;
					}
					List<DHTTransportContact>	node_list = new ArrayList<DHTTransportContact>(max_nodes);
					mapping_to_node_map.put(mapping, node_list);
					for (int i=0;i<max_nodes;i++) {
						byte[]	id = node_ids[i];
						// remove ourselves from the equation here as we don't want to end
						// up querying ourselves and we account for the replica we have later
						// on
						if (Arrays.equals( survey_my_id, id)) {
							continue;
						}
						List<DHTDBMapping> list = value_map.get(id);
						if (list == null) {
							list = new ArrayList<DHTDBMapping>();
							value_map.put(id, list);
						}
						list.add(mapping);
						node_list.add(survey.get( id));
					}
				}
				if (DEBUG_SURVEY) {
					System.out.println("Total values: " + value_count);
				}
				
				// build a list of requests to send to nodes to check their replicas
				for (byte[] id: node_ids) {
					final int MAX_PREFIX_TEST = 3;
					List<DHTDBMapping> all_entries = value_map.remove(id);
					ByteArrayHashMap<List<DHTDBMapping>> prefix_map = new ByteArrayHashMap<List<DHTDBMapping>>();
					if (all_entries != null) {
						prefix_map.put(new byte[0], all_entries);
						for (int i=0;i<MAX_PREFIX_TEST;i++) {
							List<byte[]> prefixes = prefix_map.keys();
							for (byte[] prefix: prefixes) {
								if (prefix.length == i) {
									List<DHTDBMapping> list = prefix_map.get(prefix);
									if (list.size() < 2) {
										continue;
									}
									ByteArrayHashMap<List<DHTDBMapping>> temp_map = new ByteArrayHashMap<List<DHTDBMapping>>();
									for (DHTDBMapping mapping: list) {
										byte[] key = mapping.getKey().getBytes();
										byte[] sub_prefix = new byte[ i+1 ];
										System.arraycopy(key, 0, sub_prefix, 0, i+1);
										List<DHTDBMapping> entries = temp_map.get(sub_prefix);
										if (entries == null) {
											entries = new ArrayList<DHTDBMapping>();
											temp_map.put(sub_prefix, entries);
										}
										entries.add(mapping);
									}
									List<DHTDBMapping> new_list = new ArrayList<DHTDBMapping>( list.size());
									List<byte[]> temp_keys = temp_map.keys();
									for (byte[] k: temp_keys) {
										List<DHTDBMapping> entries = temp_map.get(k);
										int	num	= entries.size();
										// prefix spread over multiple entries so ignore and just count suffix cost
										int outer_cost 	= num * (QUERY_STORE_REQUEST_ENTRY_SIZE - i);
										// include new prefix, one byte prefix len, 2 bytes num-suffixes, then suffixes
										// yes, this code should be elsewhere, but whatever
										int inner_cost	= i+4 + num * (QUERY_STORE_REQUEST_ENTRY_SIZE - i - 1);
										if (inner_cost < outer_cost) {
											prefix_map.put(k, entries);
										} else {
											new_list.addAll(entries);
										}
									}
									if (new_list.size() == 0) {
										prefix_map.remove(prefix);
									} else {
										prefix_map.put(prefix, new_list);
									}
								}
							}
						}
						String str = "";
						int encoded_size = 1;	// header size
						List<byte[]> prefixes = prefix_map.keys();
						for (byte[] prefix: prefixes) {
							encoded_size += 3 + prefix.length;
							List<DHTDBMapping> entries = prefix_map.get(prefix);
							encoded_size += (QUERY_STORE_REQUEST_ENTRY_SIZE - prefix.length) * entries.size();
							str += (str.length()==0?"":", ")+ ByteFormatter.encodeString(prefix) + "->" + entries.size();
						}
						if (DEBUG_SURVEY) {
							System.out.println("node " + ByteFormatter.encodeString( id ) + " -> " + (all_entries==null?0:all_entries.size()) + ", encoded=" + encoded_size + ", prefix=" + str);
						}
						if (prefixes.size() > 0) {
							request_map.put(survey.get( id ), prefix_map);
						}
					}
				}
			} finally {
				thisMon.exit();
			}
			LinkedList<Map.Entry<DHTTransportContact,ByteArrayHashMap<List<DHTDBMapping>>>> to_do = new LinkedList<Map.Entry<DHTTransportContact,ByteArrayHashMap<List<DHTDBMapping>>>>( request_map.entrySet());
			Map<DHTTransportContact,Object[]>	replies = new HashMap<DHTTransportContact,Object[]>();
			for (int i=0;i<Math.min(3,to_do.size());i++) {
				went_async = true;
				doQuery(survey_my_id, request_map.size(), mapping_to_node_map, to_do, replies, null, null, null);
			}
		} finally {
			if (!went_async) {
				logger.log("Survey complete - no applicable queries");
				surveyInProgress = false;
			}
		}
	}

	protected boolean applyRF(
		DHTDBMapping	mapping) {
		if (mapping.getDiversificationType() != DHT.DT_NONE) {
			return (false);
		}
		if (SURVEY_ONLY_RF_KEYS) {
			Iterator<DHTDBValueImpl>	it2 = mapping.getValues();
			if (!it2.hasNext()) {
				return (false);
			}
			int	min_period = Integer.MAX_VALUE;
			long	min_create = Long.MAX_VALUE;
			while (it2.hasNext()) {
				DHTDBValueImpl value = it2.next();
				byte rep_fact = value.getReplicationFactor();
				if (rep_fact == DHT.REP_FACT_DEFAULT || rep_fact == 0) {
					return (false);
				}
				int hours = value.getReplicationFrequencyHours()&0xff;
				if (hours < min_period) {
					min_period = hours;
				}
				min_create = Math.min( min_create, value.getCreationTime());
			}
			if (min_period > 0) {
				HashWrapper hw = mapping.getKey();
				Long	next_time = surveyMappingTimes.get(hw);
				long now = SystemTime.getMonotonousTime();
				if (next_time != null && next_time > now) {
					return (false);
				}
				long	period		= min_period*60*60*1000;
				long	offset_time = (SystemTime.getCurrentTime() - min_create) % period;
				long	rand		= RandomUtils.nextInt(30*60*1000) - 15*60*1000;
				long	new_next_time = now - offset_time + period + rand;
				if (new_next_time < now + 30*60*1000) {
					new_next_time += period;
				}
				if (DEBUG_SURVEY) {
					System.out.println("allocated next time with value relative " + (new_next_time-now) + ": period=" + period + ", offset=" + offset_time + ", rand=" + rand);
				}
				surveyMappingTimes.put(hw, new_next_time);
				if (next_time == null) {
					return (false);
				}
			}
		}
		return (true);
	}

	protected void doQuery(
		final byte[]			survey_my_id,
		final int				total,
		final Map<DHTDBMapping,List<DHTTransportContact>>										mapping_to_node_map,
		final LinkedList<Map.Entry<DHTTransportContact,ByteArrayHashMap<List<DHTDBMapping>>>>	to_do,
		final Map<DHTTransportContact,Object[]>													replies,
		DHTTransportContact		done_contact,
		List<DHTDBMapping>		done_mappings,
		List<byte[]>			done_reply) {
		Map.Entry<DHTTransportContact,ByteArrayHashMap<List<DHTDBMapping>>>	entry;
		synchronized(to_do) {
			if (done_contact != null) {
				replies.put(done_contact, new Object[]{ done_mappings, done_reply });
			}
			if (to_do.size() == 0) {
				if (replies.size() == total) {
					queriesComplete(survey_my_id, mapping_to_node_map, replies);
				}
				return;
			}
			entry = to_do.removeFirst();
		}
		DHTTransportContact contact = entry.getKey();
		boolean	handled = false;
		try {
			if (contact.getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_REPLICATION_CONTROL3) {
				if (DEBUG_SURVEY) {
					System.out.println("Hitting " + contact.getString());
				}
				final List<DHTDBMapping>	mapping_list = new ArrayList<DHTDBMapping>();
				ByteArrayHashMap<List<DHTDBMapping>>	map = entry.getValue();
				List<byte[]> prefixes = map.keys();
				List<Object[]> encoded = new ArrayList<Object[]>(prefixes.size());
				try {
					thisMon.enter();
					SurveyContactState contact_state = surveyState.get(new HashWrapper( contact.getID()));
					for (byte[] prefix: prefixes) {
						int	prefix_len = prefix.length;
						int	suffix_len = QUERY_STORE_REQUEST_ENTRY_SIZE - prefix_len;
						List<DHTDBMapping> mappings = map.get(prefix);
						List<byte[]> l = new ArrayList<byte[]>( mappings.size());
						encoded.add(new Object[]{ prefix, l });
						
						// remove entries that we know the contact already has
						// and then add them back in in the query-reply. note that we
						// still need to hit the contact if we end up with no values to
						// query as need to ascertain liveness. We might want to wait until,
						// say, 2 subsequent fails before treating contact as dead
						for (DHTDBMapping m: mappings) {
							if (contact_state != null) {
								if (contact_state.testMapping(m)) {
									if (DEBUG_SURVEY) {
										System.out.println("    skipping " + ByteFormatter.encodeString( m.getKey().getBytes()) + " as contact already has");
									}
									continue;
								}
							}
							mapping_list.add(m);
							byte[]	k = m.getKey().getBytes();
							byte[]	suffix = new byte[ suffix_len ];
							System.arraycopy(k, prefix_len, suffix, 0, suffix_len);
							l.add(suffix);
						}
					}
					if (Arrays.equals( contact.getID(), survey_my_id)) {
						Debug.out("inconsistent - we shouldn't query ourselves!");
					}
					contact.sendQueryStore(
						new DHTTransportReplyHandlerAdapter() {
							public void queryStoreReply(
								DHTTransportContact contact,
								List<byte[]>		response) {
								try {
									if (DEBUG_SURVEY) {
										System.out.println("response " + response.size());
										for (int i=0;i<response.size();i++) {
											System.out.println("    " + ByteFormatter.encodeString( response.get(i)));
										}
									}
								} finally {
									doQuery(survey_my_id, total, mapping_to_node_map, to_do, replies, contact, mapping_list, response);
								}
							}
							public void failed(
								DHTTransportContact 	contact,
								Throwable				error) {
								try {
									if (DEBUG_SURVEY) {
										System.out.println("Failed: " + Debug.getNestedExceptionMessage( error));
									}
								} finally {
									doQuery(survey_my_id, total, mapping_to_node_map, to_do, replies, contact, mapping_list, null);
								}
							}
						}, QUERY_STORE_REQUEST_ENTRY_SIZE, encoded);
					handled = true;
				} finally {
					thisMon.exit();
				}
			} else {
				if (DEBUG_SURVEY) {
					System.out.println("Not hitting " + contact.getString());
				}
			}
		} finally {
			if (!handled) {
				final List<DHTDBMapping>	mapping_list = new ArrayList<DHTDBMapping>();
				ByteArrayHashMap<List<DHTDBMapping>>	map = entry.getValue();
				List<byte[]> prefixes = map.keys();
				for (byte[] prefix: prefixes) {
					mapping_list.addAll(map.get( prefix));
				}
				doQuery(survey_my_id, total, mapping_to_node_map, to_do, replies, contact, mapping_list, null);
			}
		}
	}

	protected void queriesComplete(
		byte[]											survey_my_id,
		Map<DHTDBMapping,List<DHTTransportContact>>		mapping_to_node_map,
		Map<DHTTransportContact,Object[]>				replies) {
		Map<SurveyContactState,List<DHTDBMapping>>	store_ops = new HashMap<SurveyContactState, List<DHTDBMapping>>();
		try {
			thisMon.enter();
			if (!Arrays.equals( survey_my_id, router.getID())) {
				logger.log("Survey abandoned - router changed");
				return;
			}
			if (DEBUG_SURVEY) {
				System.out.println("Queries complete (replies=" + replies.size() + ")");
			}
			Map<DHTDBMapping,int[]>	totals = new HashMap<DHTDBMapping, int[]>();
			for ( Map.Entry<DHTTransportContact,Object[]> entry: replies.entrySet()) {
				DHTTransportContact	contact = entry.getKey();
				HashWrapper hw = new HashWrapper( contact.getID());
				SurveyContactState	contact_state = surveyState.get(hw);
				if (contact_state != null) {
					contact_state.updateContactDetails(contact);
				} else {
					contact_state = new SurveyContactState(contact);
					surveyState.put(hw, contact_state);
				}
				contact_state.updateUseTime();
				Object[]			temp	= entry.getValue();
				List<DHTDBMapping>	mappings 	= (List<DHTDBMapping>)temp[0];
				List<byte[]>		reply		= (List<byte[]>)temp[1];
				if (reply == null) {
					contact_state.contactFailed();
				} else {
					contact_state.contactOK();
					if (mappings.size() != reply.size()) {
						Debug.out("Inconsistent: mappings=" + mappings.size() + ", reply=" + reply.size());
						continue;
					}
					Iterator<DHTDBMapping>	it1 = mappings.iterator();
					Iterator<byte[]>		it2 = reply.iterator();
					while (it1.hasNext()) {
						DHTDBMapping	mapping = it1.next();
						byte[]			rep		= it2.next();
						if (rep == null) {
							contact_state.removeMapping(mapping);
						} else {
								// must match against our short-key mapping for consistency
							DHTDBMapping mapping_to_check = storedValuesPrefixMap.get( mapping.getShortKey());
							if (mapping_to_check == null) {
								// deleted
							} else {
								byte[] k = mapping_to_check.getKey().getBytes();
								int	rep_len = rep.length;
								if (rep_len < 2 || rep_len >= k.length) {
									Debug.out("Invalid rep_len: " + rep_len);
									continue;
								}
								boolean	match = true;
								int	offset = k.length-rep_len;
								for (int i=0;i<rep_len;i++) {
									if (rep[i] != k[i+offset]) {
										match = false;
										break;
									}
								}
								if (match) {
									contact_state.addMapping(mapping);
								} else {
									contact_state.removeMapping(mapping);
								}
							}
						}
					}
					Set<DHTDBMapping> contact_mappings = contact_state.getMappings();
					for (DHTDBMapping m: contact_mappings) {
						int[] t = totals.get(m);
						if (t == null) {
							t = new int[]{ 2 };		// one for local node + 1 for them
							totals.put(m, t);
						} else {
							t[0]++;
						}
					}
				}
			}
			for (Map.Entry<DHTDBMapping,List<DHTTransportContact>> entry: mapping_to_node_map.entrySet()) {
				DHTDBMapping				mapping 	= entry.getKey();
				List<DHTTransportContact>	contacts 	= entry.getValue();
				int[]	t = totals.get(mapping);
				int	copies;
				if (t == null) {
					copies = 1;		// us!
				} else {
					copies = t[0];
				}
				Iterator<DHTDBValueImpl> values = mapping.getValues();
				if (values.hasNext()) {
					int	max_replication_factor = -1;
					while (values.hasNext()) {
						DHTDBValueImpl value = values.next();
						int	rf = value.getReplicationFactor();
						if (rf > max_replication_factor) {
							max_replication_factor = rf;
						}
					}
					if (max_replication_factor == 0) {
						continue;
					}
					if (max_replication_factor > router.getK()) {
						max_replication_factor = router.getK();
					}
					if (copies < max_replication_factor) {
						int	required = max_replication_factor - copies;
						List<SurveyContactState> potential_targets = new ArrayList<SurveyContactState>();
						List<byte[]>	addresses = new ArrayList<byte[]>( contacts.size());
						for (DHTTransportContact c: contacts) {
							if (c.getProtocolVersion() < DHTTransportUDP.PROTOCOL_VERSION_REPLICATION_CONTROL3) {
								continue;
							}
							addresses.add( AddressUtils.getAddressBytes( c.getAddress()));
							SurveyContactState	contact_state = surveyState.get(new HashWrapper( c.getID()));
							if (contact_state != null && !contact_state.testMapping( mapping)) {
								potential_targets.add(contact_state);
							}
						}
						Set<HashWrapper>	bad_addresses = new HashSet<HashWrapper>();
						for (byte[] a1: addresses) {
							for (byte[] a2: addresses) {
									// ignore ipv6 for the moment...
								if (a1 == a2 || a1.length != a2.length || a1.length != 4) {
									continue;
								}
									// ignore common /16 s
								if (a1[0] == a2[0] && a1[1] == a2[1]) {
									log("/16 match on " + ByteFormatter.encodeString( a1 ) + "/" + ByteFormatter.encodeString( a2));
									bad_addresses.add(new HashWrapper( a1));
									bad_addresses.add(new HashWrapper( a2));
								}
							}
						}
						final byte[] key = mapping.getKey().getBytes();
						Collections.sort(
							potential_targets,
							new Comparator<SurveyContactState>() {
								public int compare(
									SurveyContactState o1,
									SurveyContactState o2) {
									boolean o1_bad = o1.getConsecFails() >= 2;
									boolean o2_bad = o2.getConsecFails() >= 2;
									if (o1_bad == o2_bad) {
											// switch from age based to closest as per Roxana's advice
										if (false) {
											long res = o2.getCreationTime() - o1.getCreationTime();
											if (res < 0) {
												return (-1);
											} else if (res > 0) {
												return (1);
											} else {
												return (0);
											}
										} else {
											return (
												control.computeAndCompareDistances(
														o1.getContact().getID(),
														o2.getContact().getID(),
														key ));
										}
									} else {
										if (o1_bad) {
											return (1);
										} else {
											return (-1);
										}
									}
								}
							});
						int	avail = Math.min( required, potential_targets.size());
						for (int i=0;i<avail;i++) {
							SurveyContactState target = potential_targets.get(i);
							if (	bad_addresses.size() > 0 &&
									bad_addresses.contains(new HashWrapper( AddressUtils.getAddressBytes( target.getContact().getAddress())))) {
									// make it look like this target has the mapping as we don't want to store it there but we want to treat it as
									// if it has it, effectively reducing availability but not skewing storage in favour of potentially malicious nodes
								target.addMapping(mapping);
							} else {
								List<DHTDBMapping> m = store_ops.get(target);
								if (m == null) {
									m = new ArrayList<DHTDBMapping>();
									store_ops.put(target, m);
								}
								m.add(mapping);
							}
						}
					}
				}
			}
		} finally {
			thisMon.exit();
			surveyInProgress = false;
		}
		logger.log("Survey complete - " + store_ops.size() + " store ops");
		if (DEBUG_SURVEY) {
			System.out.println("Store ops: " + store_ops.size());
		}
		for ( Map.Entry<SurveyContactState,List<DHTDBMapping>> store_op: store_ops.entrySet()) {
			final SurveyContactState 	contact = store_op.getKey();
			final List<DHTDBMapping>	keys	= store_op.getValue();
			final byte[][]				store_keys 		= new byte[keys.size()][];
			final DHTTransportValue[][]	store_values 	= new DHTTransportValue[store_keys.length][];
			for (int i=0;i<store_keys.length;i++) {
				DHTDBMapping	mapping = keys.get(i);
				store_keys[i] = mapping.getKey().getBytes();
				List<DHTTransportValue> v = new ArrayList<DHTTransportValue>();
				Iterator<DHTDBValueImpl> it = mapping.getValues();
				while (it.hasNext()) {
					DHTDBValueImpl value = it.next();
					if (!value.isLocal()) {
						v.add( value.getValueForRelay(localContact));
					}
				}
				store_values[i] = v.toArray(new DHTTransportValue[v.size()]);
			}
			final DHTTransportContact d_contact = contact.getContact();
			final Runnable	store_exec =
				new Runnable() {
					public void run() {
						if (DEBUG_SURVEY) {
							System.out.println("Storing " + keys.size() + " on " + d_contact.getString() + " - rand=" + d_contact.getRandomID());
						}
						control.putDirectEncodedKeys(
								store_keys,
								"Replication forward",
								store_values,
								d_contact,
								new DHTOperationAdapter() {
									public void complete(boolean timeout) {
										try {
											thisMon.enter();
											if (timeout) {
												contact.contactFailed();
											} else {
												contact.contactOK();
												for (DHTDBMapping m: keys) {
													contact.addMapping(m);
												}
											}
										} finally {
											thisMon.exit();
										}
									}
								});
					}
				};
			if (d_contact.getRandomIDType() != DHTTransportContact.RANDOM_ID_TYPE1) {
				Debug.out("derp");
			}
			if (d_contact.getRandomID() == 0) {
				d_contact.sendFindNode(
					new DHTTransportReplyHandlerAdapter() {
						public void findNodeReply(
							DHTTransportContact 	_contact,
							DHTTransportContact[]	_contacts) {
							store_exec.run();
						}
						public void failed(
							DHTTransportContact 	_contact,
							Throwable				_error) {
							try {
								thisMon.enter();
								contact.contactFailed();
							} finally {
								thisMon.exit();
							}
						}
					},
					d_contact.getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_ANTI_SPOOF2?new byte[0]:new byte[20],
					DHT.FLAG_LOOKUP_FOR_STORE);
			} else {
				store_exec.run();
			}
		}
	}

	private void sleep() {
		Iterator<Map.Entry<HashWrapper,DHTDBMapping>>	it = storedValues.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<HashWrapper,DHTDBMapping>	entry = it.next();
			HashWrapper			key		= entry.getKey();
			DHTDBMapping		mapping	= entry.getValue();
			Iterator<DHTDBValueImpl>	it2 = mapping.getValues();
			boolean	all_remote = it2.hasNext();
			while (it2.hasNext()) {
				DHTDBValueImpl	value = it2.next();
				if (value.isLocal()) {
					all_remote = false;
					break;
				}
			}
			if (all_remote) {
				it.remove();
				removeFromPrefixMap(mapping);
				mapping.destroy();
			}
		}
	}

	public void setSleeping(
		boolean	asleep) {
		try {
			thisMon.enter();

			sleeping = asleep;

			if (asleep) {

				sleep();
			}
		} finally {

			thisMon.exit();
		}
	}

	public void setSuspended(
		boolean			susp) {
		boolean	waking_up;

		try {
			thisMon.enter();

			waking_up = suspended && !susp;

			suspended = susp;

			if (susp) {

				sleep();
			}

		} finally {

			thisMon.exit();
		}

		if (waking_up) {

			new AEThread2("DHTB:resume") {
				public void run() {
					try {
							// give things a chance to get running again

						Thread.sleep(15*1000);

					} catch (Throwable e) {
					}

					logger.log("Force republish of original mappings due to resume from suspend");

					long start 	= SystemTime.getMonotonousTime();

					int stats = republishOriginalMappings();

					long end 	= SystemTime.getMonotonousTime();

					logger.log("Force republish of original mappings due to resume from suspend completed in " + (end-start) + ": " +
								"values = " + stats);
				}
			}.start();
		}
	}

	public DHTTransportQueryStoreReply
	queryStore(
		DHTTransportContact 		originating_contact,
		int							header_len,
		List<Object[]>				keys) {
		final List<byte[]> reply = new ArrayList<byte[]>();

		try {
			thisMon.enter();

			SurveyContactState	existing_state = surveyState.get(new HashWrapper( originating_contact.getID()));

			if (existing_state != null) {

				existing_state.updateContactDetails(originating_contact);
			}

			for (Object[] entry: keys) {

				byte[]			prefix 		= (byte[])entry[0];
				List<byte[]>	suffixes 	= (List<byte[]>)entry[1];

				byte[]	header = new byte[header_len];

				int		prefix_len	= prefix.length;
				int		suffix_len	= header_len - prefix_len;

				System.arraycopy(prefix, 0, header, 0, prefix_len);

				for (byte[] suffix: suffixes) {

					System.arraycopy(suffix, 0, header, prefix_len, suffix_len);

					DHTDBMapping mapping = storedValuesPrefixMap.get(new DHTDBMapping.ShortHash( header));

					if (mapping == null) {

						reply.add(null);

					} else {

						if (existing_state != null) {

							existing_state.addMapping(mapping);
						}

						byte[] k = mapping.getKey().getBytes();

						byte[] r = new byte[QUERY_STORE_REPLY_ENTRY_SIZE];

						System.arraycopy(k, k.length-QUERY_STORE_REPLY_ENTRY_SIZE, r, 0, QUERY_STORE_REPLY_ENTRY_SIZE);

						reply.add(r);
					}
				}
			}

			return (
				new DHTTransportQueryStoreReply() {
					public int getHeaderSize() {
						return (QUERY_STORE_REPLY_ENTRY_SIZE);
					}

					public List<byte[]>
					getEntries() {
						return (reply);
					}
				});

		} finally {

			thisMon.exit();
		}
	}

	public void print(
		boolean	full) {
		Map<Integer,Object[]>	count = new TreeMap<Integer,Object[]>();

		try {
			thisMon.enter();

			logger.log("Stored keys = " + storedValues.size() + ", values = " + getValueDetails()[DHTDBStats.VD_VALUE_COUNT]);

			if (!full) {

				return;
			}

			Iterator<Map.Entry<HashWrapper,DHTDBMapping>>	it1 = storedValues.entrySet().iterator();

			// ByteArrayHashMap<Integer> blah = new ByteArrayHashMap<Integer>();

			while (it1.hasNext()) {

				Map.Entry<HashWrapper,DHTDBMapping>		entry = it1.next();

				HashWrapper		value_key	= entry.getKey();

				DHTDBMapping	mapping 	= entry.getValue();

				/*
				if (mapping.getIndirectSize() > 1000) {
					mapping.print();
				}
				*/

				DHTDBValue[]	values = mapping.get(null,0,(byte)0);

				for (int i=0;i<values.length;i++) {

					DHTDBValue	value = values[i];

					/*
					byte[] v = value.getValue();

					Integer y = blah.get(v);

					if (y == null) {
						blah.put(v, 1);
					} else {
						blah.put(v, y+1);
					}
					*/

					Integer key = new Integer( value.isLocal()?0:1);

					Object[]	data = (Object[])count.get(key);

					if (data == null) {

						data = new Object[2];

						data[0] = new Integer(1);

						data[1] = "";

						count.put(key, data);

					} else {

						data[0] = new Integer(((Integer)data[0]).intValue() + 1);
					}

					String	s = (String)data[1];

					s += (s.length()==0?"":", ") + "key=" + DHTLog.getString2(value_key.getHash()) + ",val=" + value.getString();

					data[1]	= s;
				}
			}

			/*
			long	total_dup = 0;

			for ( byte[] k: blah.keys()) {

				int c = blah.get(k);

				if (c > 1) {

					total_dup += (c * k.length);

					System.out.println("Dup: " + new String(k) + " -> " + c);
				}
			}

			System.out.println("Total dup: " + total_dup);
			*/

			Iterator<Integer> it2 = count.keySet().iterator();

			while (it2.hasNext()) {

				Integer	k = it2.next();

				Object[]	data = (Object[])count.get(k);

				logger.log("    " + k + " -> " + data[0] + " entries"); // ": " + data[1]);
			}

			Iterator<Map.Entry<HashWrapper,DHTDBMapping>> it3 = storedValues.entrySet().iterator();

			StringBuilder	sb = new StringBuilder(1024);

			int		str_entries	= 0;

			while (it3.hasNext()) {

				Map.Entry<HashWrapper,DHTDBMapping>		entry = it3.next();

				HashWrapper		value_key	= entry.getKey();

				DHTDBMapping	mapping 	= entry.getValue();

				if (str_entries == 16) {

					logger.log( sb.toString());

					sb = new StringBuilder(1024);

					sb.append("    ");

					str_entries	= 0;
				}

				str_entries++;

				if (str_entries > 1) {
					sb.append(", ");
				}
				sb.append( DHTLog.getString2(value_key.getHash()));
				sb.append(" -> ");
				sb.append( mapping.getValueCount());
				sb.append("/");
				sb.append( mapping.getHits());
				sb.append("[");
				sb.append( mapping.getLocalSize());
				sb.append(",");
				sb.append( mapping.getDirectSize());
				sb.append(",");
				sb.append( mapping.getIndirectSize());
				sb.append("]");;
			}

			if (str_entries > 0) {

				logger.log( sb.toString());
			}
		} finally {

			thisMon.exit();
		}
	}

	protected void banContact(
		final DHTTransportContact	contact,
		final String				reason) {
		// CVS DHT can be significantly smaller than mainline (e.g. 1000) so will trigger
		// un-necessary banning which then obviously affects the main DHTs. So we disable
		// banning for CVS

		// same is currently true of the IPv6 one :(

		final boolean ban_ip =
				control.getTransport().getNetwork() != DHT.NW_CVS &&
				!control.getTransport().isIPV6();

		new AEThread2("DHTDBImpl:delayed flood delete", true) {
			public void run() {
					// delete their data on a separate thread so as not to
					// interfere with the current action

				try {
					thisMon.enter();

					Iterator<DHTDBMapping>	it = storedValues.values().iterator();

					boolean	overall_deleted = false;

					HashWrapper value_id = new HashWrapper( contact.getID());

					while (it.hasNext()) {

						DHTDBMapping	mapping = it.next();

						boolean	deleted = false;

						if (mapping.removeDirectValue(value_id) != null) {

							deleted = true;
						}

						if (mapping.removeIndirectValue(value_id) != null) {

							deleted = true;
						}


						if (deleted && !ban_ip) {

								// if we're not banning then rebuild bloom to avoid us continually
								// going through this ban code

							mapping.rebuildIPBloomFilter(false);

							overall_deleted = true;
						}
					}

					if (overall_deleted && !ban_ip) {

						rebuildIPBloomFilter(false);
					}
				} finally {

					thisMon.exit();

				}
			}
		}.start();

		if (ban_ip) {

			logger.log("Banning " + contact.getString() + " due to store flooding (" + reason + ")");

			ipFilter.ban(
					AddressUtils.getHostAddress( contact.getAddress()),
					"DHT: Sender stored excessive entries at this node (" + reason + ")", false);
		}
	}

	protected void incrementValueAdds(
		DHTTransportContact	contact) {
			// assume a node stores 1000 values at 20 (K) locations -> 20,000 values
			// assume a DHT size of 100,000 nodes
			// that is, on average, 1 value per 5 nodes
			// assume NAT of up to 30 ports per address
			// this gives 6 values per address
			// with a factor of 10 error this is still only 60 per address

			// However, for CVS DHTs we can have sizes of 1000 or less.

		byte[] bloom_key = contact.getBloomKey();

		int	hit_count = ip_count_bloom_filter.add(bloom_key);

		if (DHTLog.GLOBAL_BLOOM_TRACE) {

			System.out.println("direct add from " + contact.getAddress() + ", hit count = " + hit_count);
		}

			// allow up to 10% bloom filter utilisation

		if (ip_count_bloom_filter.getSize() / ip_count_bloom_filter.getEntryCount() < 10) {

			rebuildIPBloomFilter(true);
		}

		if (hit_count > 64) {

				// obviously being spammed, drop all data originated by this IP and ban it

			banContact(contact, "global flood");
		}
	}

	protected void decrementValueAdds(
		DHTTransportContact	contact) {
		byte[] bloom_key = contact.getBloomKey();

		int	hit_count = ip_count_bloom_filter.remove(bloom_key);

		if (DHTLog.GLOBAL_BLOOM_TRACE) {

			System.out.println("direct remove from " + contact.getAddress() + ", hit count = " + hit_count);
		}
	}

	protected void rebuildIPBloomFilter(
		boolean	increase_size) {
		BloomFilter	new_filter;

		if (increase_size) {

			new_filter = BloomFilterFactory.createAddRemove8Bit(ip_count_bloom_filter.getSize() + IP_COUNT_BLOOM_SIZE_INCREASE_CHUNK);

		} else {

			new_filter = BloomFilterFactory.createAddRemove8Bit( ip_count_bloom_filter.getSize());

		}

		try {

			//Map		sender_map	= new HashMap();
			//List	senders		= new ArrayList();

			Iterator<DHTDBMapping>	it = storedValues.values().iterator();

			int	max_hits = 0;

			while (it.hasNext()) {

				DHTDBMapping	mapping = it.next();

				mapping.rebuildIPBloomFilter(false);

				Iterator<DHTDBValueImpl>	it2 = mapping.getDirectValues();

				while (it2.hasNext()) {

					DHTDBValueImpl	val = it2.next();

					if (!val.isLocal()) {

						// logger.log("    adding " + val.getOriginator().getAddress());

						byte[] bloom_key = val.getOriginator().getBloomKey();

						int	hits = new_filter.add(bloom_key);

						if (hits > max_hits) {

							max_hits = hits;
						}
					}
				}

					// survey our neighbourhood

				/*
				 * its is non-trivial to do anything about nodes that get "close" to us and then
				 * spam us with crap. Ultimately, of course, to take a key out you "just" create
				 * the 20 closest nodes to the key and then run nodes that swallow all registrations
				 * and return nothing.
				 * Protecting against one or two such nodes that flood crap requires crap to be
				 * identified. Tracing shows a large disparity between number of values registered
				 * per neighbour (factors of 100), so an approach based on number of registrations
				 * is non-trivial (assuming future scaling of the DHT, what do we consider crap?)
				 * A further approach would be to query the claimed originators of values (obviously
				 * a low bandwith approach, e.g. query 3 values from the contact with highest number
				 * of forwarded values). This requires originators to support long term knowledge of
				 * what they've published (we don't want to blacklist a neighbour because an originator
				 * has deleted a value/been restarted). We also then have to consider how to deal with
				 * non-responses to queries (assuming an affirmative Yes -> value has been forwarded
				 * correnctly, No -> probably crap). We can't treat non-replies as No. Thus a bad
				 * neighbour only has to forward crap with originators that aren't AZ nodes (very
				 * easy to do!) to break this aproach.
				 *
				 *
				it2 = mapping.getIndirectValues();

				while (it2.hasNext()) {

					DHTDBValueImpl	val = (DHTDBValueImpl)it2.next();

					DHTTransportContact sender = val.getSender();

					HashWrapper	hw = new HashWrapper( sender.getID());

					Integer	sender_count = (Integer)sender_map.get(hw);

					if (sender_count == null) {

						sender_count = new Integer(1);

						senders.add(sender);

					} else {

						sender_count = new Integer(sender_count.intValue() + 1);
					}

					sender_map.put(hw, sender_count);
				}
				*/
			}

			logger.log("Rebuilt global IP bloom filter, size=" + new_filter.getSize() + ", entries=" + new_filter.getEntryCount()+", max hits=" + max_hits);

			/*
			senders = control.sortContactsByDistance(senders);

			for (int i=0;i<senders.size();i++) {

				DHTTransportContact	sender = (DHTTransportContact)senders.get(i);

				System.out.println( i + ":" + sender.getString() + " -> " + sender_map.get(new HashWrapper(sender.getID())));
			}
			*/

		} finally {

			ip_count_bloom_filter	= new_filter;
		}
	}

	protected void reportSizes(
		String	op) {
		/*
		if (!this_mon.isHeld()) {

			Debug.out("Monitor not held");
		}

		int	actual_keys 	= stored_values.size();
		int	actual_values 	= 0;
		int actual_size		= 0;

		Iterator it = stored_values.values().iterator();

		while (it.hasNext()) {

			DHTDBMapping	mapping = (DHTDBMapping)it.next();

			int	reported_size = mapping.getLocalSize() + mapping.getDirectSize() + mapping.getIndirectSize();

			actual_values += mapping.getValueCount();

			Iterator	it2 = mapping.getValues();

			int	sz = 0;

			while (it2.hasNext()) {

				DHTDBValue	val = (DHTDBValue)it2.next();

				sz += val.getValue().length;
			}

			if (sz != reported_size) {

				Debug.out("Reported mapping size != actual: " + reported_size + "/" + sz);
			}

			actual_size += sz;
		}

		if (actual_keys != total_keys) {

			Debug.out("Actual keys != total: " + actual_keys + "/" + total_keys);
		}

		if (adapter.getKeyCount() != actual_keys) {

			Debug.out("SM keys != total: " + actual_keys + "/" + adapter.getKeyCount());
		}

		if (actual_values != total_values) {

			Debug.out("Actual values != total: " + actual_values + "/" + total_values);
		}

		if (actual_size != total_size) {

			Debug.out("Actual size != total: " + actual_size + "/" + total_size);
		}

		if (actual_values < actual_keys) {

			Debug.out("Actual values < actual keys: " + actual_values + "/" + actual_keys);
		}

		System.out.println("DHTDB: " + op + " - keys=" + total_keys + ", values=" + total_values + ", size=" + total_size);
		*/
	}

	protected int getNextValueVersion() {
		try {
			thisMon.enter();

			if (next_value_version_left == 0) {

				next_value_version_left = VALUE_VERSION_CHUNK;

				if (adapter == null) {

						// no persistent manager, just carry on incrementing

				} else {

					next_value_version = adapter.getNextValueVersions(VALUE_VERSION_CHUNK);
				}

				//System.out.println("next chunk:" + next_value_version);
			}

			next_value_version_left--;

			int	res = next_value_version++;

			//System.out.println("next value version = " + res);

			return (res);

		} finally {

			thisMon.exit();
		}
	}

	public void destroy() {
		destroyed	= true;

		if (preciousTimer != null) {

			preciousTimer.cancel();
		}
		if (originalRepublishTimer != null) {

			originalRepublishTimer.cancel();
		}
		if (cacheRepublishTimer != null) {

			cacheRepublishTimer.cancel();
		}
		if (bloomTimer != null) {

			bloomTimer.cancel();
		}
		if (surveyTimer != null) {

			surveyTimer.cancel();
		}
	}

	protected class
	adapterFacade
		implements DHTStorageAdapter
	{
		private final DHTStorageAdapter		delegate;

		protected
		adapterFacade(
			DHTStorageAdapter	_delegate) {
			delegate = _delegate;
		}

		public int getNetwork() {
			return ( delegate.getNetwork());
		}

		public DHTStorageKey
		keyCreated(
			HashWrapper		key,
			boolean			local) {
				// report *before* incrementing as this occurs before the key is locally added

			reportSizes("keyAdded");

			totalKeys++;

			return (delegate.keyCreated( key, local));
		}

		public void keyDeleted(
			DHTStorageKey	adapter_key) {
			totalKeys--;

			delegate.keyDeleted(adapter_key);

			reportSizes("keyDeleted");
		}

		public int getKeyCount() {
			return ( delegate.getKeyCount());
		}

		public void keyRead(
			DHTStorageKey			adapter_key,
			DHTTransportContact		contact) {
			reportSizes("keyRead");

			delegate.keyRead(adapter_key, contact);
		}

		public DHTStorageKeyStats
		deserialiseStats(
			DataInputStream			is )

			throws IOException
		{
			return (delegate.deserialiseStats( is));
		}

		public void valueAdded(
			DHTStorageKey		key,
			DHTTransportValue	value) {
			totalValues++;
			totalSize += value.getValue().length;

			reportSizes("valueAdded");

			if (!value.isLocal()) {

				DHTDBValueImpl	val = (DHTDBValueImpl)value;

				boolean	direct = Arrays.equals( value.getOriginator().getID(), val.getSender().getID());

				if (direct) {

					incrementValueAdds( value.getOriginator());
				}
			}

			delegate.valueAdded(key, value);
		}

		public void valueUpdated(
			DHTStorageKey		key,
			DHTTransportValue	old_value,
			DHTTransportValue	new_value) {
			totalSize += (new_value.getValue().length - old_value.getValue().length);

			reportSizes("valueUpdated");

			delegate.valueUpdated(key, old_value, new_value);
		}

		public void valueDeleted(
			DHTStorageKey		key,
			DHTTransportValue	value) {
			totalValues--;
			totalSize -= value.getValue().length;

			reportSizes("valueDeleted");

			if (!value.isLocal()) {

				DHTDBValueImpl	val = (DHTDBValueImpl)value;

				boolean	direct = Arrays.equals( value.getOriginator().getID(), val.getSender().getID());

				if (direct) {

					decrementValueAdds( value.getOriginator());
				}
			}

			delegate.valueDeleted(key, value);
		}

			// local lookup/put operations

		public boolean isDiversified(
			byte[]		key) {
			return (delegate.isDiversified( key));
		}

		public byte[][]
		getExistingDiversification(
			byte[]			key,
			boolean			put_operation,
			boolean			exhaustive_get,
			int				max_depth) {
			return (delegate.getExistingDiversification( key, put_operation, exhaustive_get, max_depth));
		}

		public byte[][]
		createNewDiversification(
			String				description,
			DHTTransportContact	cause,
			byte[]				key,
			boolean				put_operation,
			byte				diversification_type,
			boolean				exhaustive_get,
			int					max_depth) {
			return (delegate.createNewDiversification( description, cause, key, put_operation, diversification_type, exhaustive_get, max_depth));
		}

		public int getNextValueVersions(
			int		num) {
			return ( delegate.getNextValueVersions(num));
		}

		public DHTStorageBlock
		keyBlockRequest(
			DHTTransportContact		direct_sender,
			byte[]					request,
			byte[]					signature) {
			return (delegate.keyBlockRequest( direct_sender, request, signature));
		}

		public DHTStorageBlock
		getKeyBlockDetails(
			byte[]		key) {
			return ( delegate.getKeyBlockDetails(key));
		}

		public DHTStorageBlock[]
		getDirectKeyBlocks() {
			return ( delegate.getDirectKeyBlocks());
		}

		public byte[]
    	getKeyForKeyBlock(
    		byte[]	request) {
			return (delegate.getKeyForKeyBlock( request));
		}

		public void setStorageForKey(
			String	key,
			byte[]	data) {
			delegate.setStorageForKey(key, data);
		}

		public byte[]
		getStorageForKey(
			String	key) {
			return ( delegate.getStorageForKey(key));
		}

		public int getRemoteFreqDivCount() {
			return ( delegate.getRemoteFreqDivCount());
		}

		public int getRemoteSizeDivCount() {
			return ( delegate.getRemoteSizeDivCount());
		}
	}

	protected static class
	SurveyContactState
	{
		private DHTTransportContact		contact;

		private final long					creation_time	= SystemTime.getMonotonousTime();
		private final long					timeout			= creation_time + SURVEY_STATE_MAX_LIFE_TIMEOUT + RandomUtils.nextInt(SURVEY_STATE_MAX_LIFE_RAND);

		private long					last_used		= creation_time;

		private final Set<DHTDBMapping>		mappings = new HashSet<DHTDBMapping>();

		private int	consec_fails;

		protected SurveyContactState(
			DHTTransportContact		c) {
			contact = c;

			log("new");
		}

		protected boolean timeout(
			long	now) {
			 return (now - last_used > SURVEY_STATE_INACT_TIMEOUT || now > timeout);
		}

		protected DHTTransportContact
		getContact() {
			return (contact);
		}

		protected long getCreationTime() {
			return (creation_time);
		}

		protected void updateContactDetails(
			DHTTransportContact		c) {
			if (c.getInstanceID() != contact.getInstanceID()) {

				log("instance id changed");

				mappings.clear();
			}

			contact	= c;
		}

		protected void updateUseTime() {
			last_used = SystemTime.getMonotonousTime();
		}

		protected long getLastUseTime() {
			return (last_used);
		}

		protected void contactOK() {
			log("contact ok");

			consec_fails	= 0;
		}

		protected void contactFailed() {
			consec_fails++;

			log("failed, consec=" + consec_fails);

			if (consec_fails >= 2) {

				mappings.clear();
			}
		}

		protected int getConsecFails() {
			return (consec_fails);
		}

		protected boolean testMapping(
			DHTDBMapping	mapping) {
			return (mappings.contains( mapping));
		}

		protected Set<DHTDBMapping>
		getMappings() {
			return (mappings);
		}

		protected void addMapping(
			DHTDBMapping	mapping) {
			if (mappings.add( mapping)) {

				log("add mapping");
			}
		}

		protected void removeMapping(
			DHTDBMapping	mapping) {
			if (mappings.remove( mapping)) {

				log("remove mapping");
			}
		}

		protected void log(
			String	str) {
			if (DEBUG_SURVEY) {
				System.out.println("s_state: " + contact.getString() + ": " + str);
			}
		}
	}
}
