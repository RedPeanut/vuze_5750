/*
 * Created on 12-Jan-2005
 * Created by Paul Gardner
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 */

package com.aelitis.azureus.core.dht.control.impl;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import org.gudy.azureus2.core3.util.AEMonitor;
import org.gudy.azureus2.core3.util.AESemaphore;
import org.gudy.azureus2.core3.util.AEThread2;
import org.gudy.azureus2.core3.util.AddressUtils;
import org.gudy.azureus2.core3.util.ByteArrayHashMap;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.HashWrapper;
import org.gudy.azureus2.core3.util.LightHashMap;
import org.gudy.azureus2.core3.util.ListenerManager;
import org.gudy.azureus2.core3.util.ListenerManagerDispatcher;
import org.gudy.azureus2.core3.util.RandomUtils;
import org.gudy.azureus2.core3.util.SHA1Simple;
import org.gudy.azureus2.core3.util.SimpleTimer;
import org.gudy.azureus2.core3.util.SystemTime;
import org.gudy.azureus2.core3.util.ThreadPool;
import org.gudy.azureus2.core3.util.ThreadPoolTask;
import org.gudy.azureus2.core3.util.TimerEvent;
import org.gudy.azureus2.core3.util.TimerEventPerformer;
import org.gudy.bouncycastle.crypto.CipherParameters;
import org.gudy.bouncycastle.crypto.engines.RC4Engine;
import org.gudy.bouncycastle.crypto.params.KeyParameter;

import com.aelitis.azureus.core.dht.DHT;
import com.aelitis.azureus.core.dht.DHTLogger;
import com.aelitis.azureus.core.dht.DHTOperationAdapter;
import com.aelitis.azureus.core.dht.DHTOperationListener;
import com.aelitis.azureus.core.dht.DHTStorageAdapter;
import com.aelitis.azureus.core.dht.DHTStorageBlock;
import com.aelitis.azureus.core.dht.control.DHTControl;
import com.aelitis.azureus.core.dht.control.DHTControlActivity;
import com.aelitis.azureus.core.dht.control.DHTControlActivity.ActivityNode;
import com.aelitis.azureus.core.dht.control.DHTControlAdapter;
import com.aelitis.azureus.core.dht.control.DHTControlContact;
import com.aelitis.azureus.core.dht.control.DHTControlListener;
import com.aelitis.azureus.core.dht.control.DHTControlStats;
import com.aelitis.azureus.core.dht.db.DHTDB;
import com.aelitis.azureus.core.dht.db.DHTDBFactory;
import com.aelitis.azureus.core.dht.db.DHTDBLookupResult;
import com.aelitis.azureus.core.dht.db.DHTDBValue;
import com.aelitis.azureus.core.dht.impl.DHTLog;
import com.aelitis.azureus.core.dht.netcoords.DHTNetworkPosition;
import com.aelitis.azureus.core.dht.netcoords.DHTNetworkPositionManager;
import com.aelitis.azureus.core.dht.router.DHTRouter;
import com.aelitis.azureus.core.dht.router.DHTRouterAdapter;
import com.aelitis.azureus.core.dht.router.DHTRouterContact;
import com.aelitis.azureus.core.dht.router.DHTRouterFactory;
import com.aelitis.azureus.core.dht.router.DHTRouterStats;
import com.aelitis.azureus.core.dht.transport.DHTTransport;
import com.aelitis.azureus.core.dht.transport.DHTTransportContact;
import com.aelitis.azureus.core.dht.transport.DHTTransportException;
import com.aelitis.azureus.core.dht.transport.DHTTransportFindValueReply;
import com.aelitis.azureus.core.dht.transport.DHTTransportFullStats;
import com.aelitis.azureus.core.dht.transport.DHTTransportListener;
import com.aelitis.azureus.core.dht.transport.DHTTransportQueryStoreReply;
import com.aelitis.azureus.core.dht.transport.DHTTransportReplyHandler;
import com.aelitis.azureus.core.dht.transport.DHTTransportReplyHandlerAdapter;
import com.aelitis.azureus.core.dht.transport.DHTTransportRequestHandler;
import com.aelitis.azureus.core.dht.transport.DHTTransportStoreReply;
import com.aelitis.azureus.core.dht.transport.DHTTransportValue;
import com.aelitis.azureus.core.dht.transport.udp.DHTTransportUDP;

import hello.util.Log;

/**
 * @author parg
 *
 */

public class DHTControlImpl implements DHTControl, DHTTransportRequestHandler {

	private String TAG = DHTControlImpl.class.getSimpleName();
	
	private static final boolean DISABLE_REPLICATE_ON_JOIN	= true;

	@SuppressWarnings("CanBeFinal")		// accessed from plugin

	public  static int EXTERNAL_LOOKUP_CONCURRENCY					= 16;

	private static final int EXTERNAL_PUT_CONCURRENCY				= 8;
	private static final int EXTERNAL_SLEEPING_PUT_CONCURRENCY		= 4;

	private static final int RANDOM_QUERY_PERIOD			= 5*60*1000;

	private static final int INTEGRATION_TIME_MAX			= 15*1000;


	final DHTControlAdapter		adapter;
	private final DHTTransport	transport;
	DHTTransportContact			localContact;

	private DHTRouter			router;

	final DHTDB					database;

	private final DHTControlStatsImpl	stats;

	final DHTLogger	logger;

	private final int	nodeIdByteCount;
	final int			searchConcurrency;
	private final int	lookupConcurrency;
	private final int	cacheAtClosestN;
	final int			K;
	private final int	B;
	private final int	maxRepPerNode;

	private final boolean	encodeKeys;
	private final boolean	enableRandomPoking;

	private long		routerStartTime;
	private int			routerCount;

	final ThreadPool internalLookupPool;
	final ThreadPool externalLookupPool;
	final ThreadPool internalPutPool;
	private final ThreadPool externalPutPool;

	private final Map<HashWrapper, Object[]> importedState = new HashMap<>();

	private volatile boolean	seeded;

	private long		lastLookup;


	final ListenerManager<DHTControlListener> listeners = ListenerManager.createAsyncManager(
			"DHTControl:listenDispatcher",
			new ListenerManagerDispatcher<DHTControlListener>() {
				public void dispatch(
					DHTControlListener	listener,
					int					type,
					Object				value) {
					listener.activityChanged((DHTControlActivity)value, type);
				}
			}
	);

	final List		activities		= new ArrayList();
	final AEMonitor	activityMonitor	= new AEMonitor("DHTControl:activities");

	protected final AEMonitor	estimateMon		= new AEMonitor("DHTControl:estimate");
	private long		lastDhtEstimateTime;
	private long		localDhtEstimate;
	private long		combinedDhtEstimate;
	private int			combinedDhtEstimateMag;

	private static final int	LOCAL_ESTIMATE_HISTORY	= 32;

	private final Map	localEstimateValues =
		new LinkedHashMap(LOCAL_ESTIMATE_HISTORY, 0.75f, true) {
			protected boolean  removeEldestEntry(Map.Entry eldest)  {
				return (size() > LOCAL_ESTIMATE_HISTORY);
			}
		};

	private static final int	REMOTE_ESTIMATE_HISTORY	= 128;

	private final List	remoteEstimateValues = new LinkedList();

	protected final AEMonitor	spoofMon		= new AEMonitor("DHTControl:spoof");

	//private Cipher 			spoof_cipher;
	//private SecretKey		spoof_key;
	MessageDigest	spoofDigest;
	byte[]			spoofKey;

	private static final int	SPOOF_GEN_HISTORY_SIZE	= 256;

	private final Map<InetAddress,Integer>	spoofGenHistory =
		new LinkedHashMap<InetAddress,Integer>(SPOOF_GEN_HISTORY_SIZE, 0.75f, true) {
			protected boolean  removeEldestEntry(Map.Entry<InetAddress,Integer> eldest) {
				return (size() > SPOOF_GEN_HISTORY_SIZE);
			}
		};

	private final Map<HashWrapper,byte[]> spoofGenHistory2 =
			new LinkedHashMap<HashWrapper,byte[]>(SPOOF_GEN_HISTORY_SIZE,0.75f,true) {
				protected boolean  removeEldestEntry(Map.Entry<HashWrapper,byte[]> eldest) {
					return (size() > SPOOF_GEN_HISTORY_SIZE);
				}
			};

	private final static int SPOOF_ID2_SIZE	= 8;

	private long			lastNodeAddCheck;
	private byte[]			nodeAddCheckUninterestingLimit;

	private long			rbsTime;
	private byte[]			rbsId	= {};

	private boolean			sleeping;
	private boolean			suspended;

	private volatile boolean	destroyed;

	public DHTControlImpl(
		DHTControlAdapter	_adapter,
		DHTTransport		_transport,
		int					_K,
		int					_B,
		int					_maxRepPerNode,
		int					_searchConcurrency,
		int					_lookupConcurrency,
		int					_originalRepublishInterval,
		int					_cacheRepublishInterval,
		int					_cacheAtClosestN,
		boolean				_encodeKeys,
		boolean				_enableRandomPoking,
		DHTLogger 			_logger
	) {
		
		adapter		= _adapter;
		transport	= _transport;
		logger		= _logger;

		K							= _K;
		B							= _B;
		maxRepPerNode				= _maxRepPerNode;
		searchConcurrency			= _searchConcurrency;
		lookupConcurrency			= _lookupConcurrency;
		cacheAtClosestN				= _cacheAtClosestN;
		encodeKeys					= _encodeKeys;
		enableRandomPoking			= _enableRandomPoking;

		//Log.d(TAG, ">>> K = " + _K);
		Log.d(TAG, ">>> lookupConcurrency = " + lookupConcurrency);
		new Throwable().printStackTrace();
				
		// set this so we don't do initial calculation until reasonably populated
		lastDhtEstimateTime	= SystemTime.getCurrentTime();

		database = DHTDBFactory.create(
				adapter.getStorageAdapter(),
				_originalRepublishInterval,
				_cacheRepublishInterval,
				transport.getProtocolVersion(),
				logger
		);
		
		internalLookupPool = new ThreadPool("DHTControl:internallookups", lookupConcurrency);
		internalPutPool = new ThreadPool("DHTControl:internalputs", lookupConcurrency);

		// external pools queue when full (as opposed to blocking)
		externalLookupPool = new ThreadPool("DHTControl:externallookups", EXTERNAL_LOOKUP_CONCURRENCY, true);
		externalPutPool = new ThreadPool("DHTControl:puts", EXTERNAL_PUT_CONCURRENCY, true);

		createRouter(transport.getLocalContact());

		//Log.d(TAG, ">>> 1");
		print_contactsToQuery();
		
		nodeIdByteCount = router.getID().length;
		stats = new DHTControlStatsImpl(this);

		// don't bother computing anti-spoof stuff if we don't support value storage

		if (transport.supportsStorage()) {

			try {
				/*
				spoof_cipher = Cipher.getInstance("DESede/ECB/PKCS5Padding");
				KeyGenerator keyGen = KeyGenerator.getInstance("DESede");
				spoof_key = keyGen.generateKey();
				*/

				spoofDigest 	= MessageDigest.getInstance("MD5");
				spoofKey		= new byte[16];
				RandomUtils.nextSecureBytes(spoofKey);
			} catch (Throwable e) {
				Debug.printStackTrace(e);
				logger.log(e);
			}
		}

		transport.setRequestHandler(this);

		transport.addListener(
				
			new DHTTransportListener() {
				
				public void localContactChanged(DHTTransportContact	newLocalContact) {

					logger.log("Transport ID changed, recreating router");

					List	oldContacts = router.findBestContacts(0);
					byte[]	oldRouterId = router.getID();

					createRouter(newLocalContact);

					// sort for closeness to new router id
					Set	sortedContacts = new SortedTransportContactSet(router.getID(), true).getSet();

					for (int i = 0; i < oldContacts.size(); i++) {

						DHTRouterContact contact = (DHTRouterContact) oldContacts.get(i);

						if (!Arrays.equals(oldRouterId, contact.getID())) {
							if (contact.isAlive()) {
								DHTTransportContact	t_contact = ((DHTControlContact)contact.getAttachment()).getTransportContact();
								sortedContacts.add(t_contact);
							}
						}
					}

					// fill up with non-alive ones to lower limit in case this is a start-of-day
					// router change and we only have imported contacts in limbo state

					for (int i=0;sortedContacts.size() < 32 && i<oldContacts.size();i++) {

						DHTRouterContact contact = (DHTRouterContact)oldContacts.get(i);

						if (!Arrays.equals( oldRouterId, contact.getID())) {
							if (!contact.isAlive()) {
								DHTTransportContact	t_contact = ((DHTControlContact)contact.getAttachment()).getTransportContact();
								sortedContacts.add(t_contact);
							}
						}
					}

					Iterator it = sortedContacts.iterator();

					int	added = 0;

					// don't add them all otherwise we can skew the smallest-subtree. better
					// to seed with some close ones and then let the normal seeding process
					// populate it correctly
					while (it.hasNext() && added < 128) {
						DHTTransportContact	contact = (DHTTransportContact)it.next();
						router.contactAlive(contact.getID(), new DHTControlContactImpl( contact));
						added++;
					}
					seed(false);
				}

				public void resetNetworkPositions() {
					List<DHTRouterContact> contacts = router.getAllContacts();
					for (int i = 0; i < contacts.size(); i++) {
						DHTRouterContact rc = contacts.get(i);
						if (!router.isID( rc.getID())) {
							((DHTControlContact) rc.getAttachment()).getTransportContact().createNetworkPositions(false);
						}
					}
				}

				public void currentAddress(String address) {}
				public void reachabilityChanged(boolean	reacheable) {}

			}
		);
		
		/*Log.d(TAG, ">>> 2");
		print_contactsToQuery();*/
	}

	@Override
	public void print_contactsToQuery() {
		byte[] routerNodeId = localContact.getID();
		Set<DHTTransportContact> contactsToQuery = getClosestContactsSet(routerNodeId, K, false);
		Log.d(TAG, "contactsToQuery.size() = " + contactsToQuery.size());
		Iterator<DHTTransportContact> it = contactsToQuery.iterator();
		for (int i=0;it.hasNext();i++) {
			DHTTransportContact contact = it.next();
			Log.d(TAG, String.format("contact[%d].getAddress() = %s", i, contact.getAddress()));
		}
	}
	
	@Override
	public void print_closestContactsSetSize() {
		byte[] routerNodeId = localContact.getID();
		Set<DHTTransportContact> closestContactsSet = getClosestContactsSet(routerNodeId, K, false);
		Log.d(TAG, "contactsToQuery.size() = " + closestContactsSet.size());
	}
	
	public DHTControlImpl(
		DHTControlAdapter	_adapter,
		DHTTransport		_transport,
		DHTRouter			_router,
		DHTDB				_database,
		int					_K,
		int					_B,
		int					_maxRepPerNode,
		int					_searchConcurrency,
		int					_lookupConcurrency,
		int					_original_republish_interval,
		int					_cache_republish_interval,
		int					_cacheAtClosestN,
		boolean				_encodeKeys,
		boolean				_enableRandomPoking,
		DHTLogger 			_logger) {
		
		//Log.d(TAG, ">>> K = " + _K);
		
		adapter		= _adapter;
		transport	= _transport;
		logger		= _logger;

		K						= _K;
		B						= _B;
		maxRepPerNode			= _maxRepPerNode;
		searchConcurrency		= _searchConcurrency;
		lookupConcurrency		= _lookupConcurrency;
		cacheAtClosestN			= _cacheAtClosestN;
		encodeKeys				= _encodeKeys;
		enableRandomPoking		= _enableRandomPoking;

		// set this so we don't do initial calculation until reasonably populated
		lastDhtEstimateTime	= SystemTime.getCurrentTime();

		database = _database;

		internalLookupPool = new ThreadPool("DHTControl:internallookups", lookupConcurrency);
		internalPutPool = new ThreadPool("DHTControl:internalputs", lookupConcurrency);

		// external pools queue when full (as opposed to blocking)
		externalLookupPool = new ThreadPool("DHTControl:externallookups", EXTERNAL_LOOKUP_CONCURRENCY, true);
		externalPutPool = new ThreadPool("DHTControl:puts", EXTERNAL_PUT_CONCURRENCY, true);

		router			= _router;
		routerStartTime	= SystemTime.getCurrentTime();

		localContact = transport.getLocalContact();
		database.setControl(this);

		nodeIdByteCount	= router.getID().length;
		stats = new DHTControlStatsImpl(this);

		// don't bother computing anti-spoof stuff if we don't support value storage

		if (transport.supportsStorage()) {

			try {
				/*
				spoof_cipher = Cipher.getInstance("DESede/ECB/PKCS5Padding");
				KeyGenerator keyGen = KeyGenerator.getInstance("DESede");
				spoof_key = keyGen.generateKey();
				*/

				spoofDigest 	= MessageDigest.getInstance("MD5");
				spoofKey		= new byte[16];
				RandomUtils.nextSecureBytes(spoofKey);
			} catch (Throwable e) {
				Debug.printStackTrace(e);
				logger.log(e);
			}
		}

		transport.setRequestHandler(this);
	}

	protected void createRouter(DHTTransportContact _localContact) {
		
		/*Log.d(TAG, "createRouter() is called...");
		Throwable t = new Throwable();
		t.printStackTrace();*/
		
		routerStartTime	= SystemTime.getCurrentTime();
		routerCount++;
		localContact = _localContact;
		if (router != null) {
			router.destroy();
		}
		router = DHTRouterFactory.create(
					K, B, maxRepPerNode,
					localContact.getID(),
					new DHTControlContactImpl(localContact),
					logger);
		router.setSleeping(sleeping);
		if (suspended) {
			router.setSuspended(true);
		}
		
		router.setAdapter(
			new DHTRouterAdapter() {
				
				public void requestPing(DHTRouterContact contact) {
					//Log.d(TAG, "requestPing() is called...");
					DHTControlImpl.this.requestPing(contact);
					//requestPing(contact);
				}
				
				public void requestLookup(
					byte[]		id,
					String		description) {
					
					//Log.d(TAG, "requestLookup() is called...");
					
					lookup(internalLookupPool,
							false,				
							id,					
							description,		
							(byte)0,			// short flags
							false,				// (boolean) true: value search, false: node search
							5*60*1000,			// long timeout: upper bound on this refresh/seeding operation
							searchConcurrency,	
							1,					
							router.getK()/2,	// int searchAccuracy: (parg - removed this; re-added the /2 on May 2014 to see how it performs) decrease search accuracy for refreshes
							new LookupResultHandler(new DHTOperationAdapter()) {
								public void diversify(DHTTransportContact cause, byte diversificationType) {
								}
								
								public void closest(List res) {
								}
							});
				}
				
				public void requestAdd(DHTRouterContact contact) {
					/*Log.d(TAG, "requestAdd() is called...");
					Throwable t = new Throwable();
					t.printStackTrace();*/
					
					nodeAddedToRouter(contact);
				}
				
			});
		
		database.setControl(this);
	}

	public long getRouterUptime() {
		long	now = SystemTime.getCurrentTime();
		if (now < routerStartTime) {
			routerStartTime	= now;
		}
		return ( now - routerStartTime);
	}

	public int getRouterCount() {
		return (routerCount);
	}

	public void	setSleeping(boolean	asleep) {
		if (asleep != sleeping) {
			logger.log("Sleep mode changed to " + asleep);
		}
		sleeping	= asleep;
		DHTRouter current_router = router;
		if (current_router != null) {
			current_router.setSleeping(asleep);
		}
		transport.setGenericFlag(DHTTransport.GF_DHT_SLEEPING, asleep);
		if (asleep) {
			externalPutPool.setMaxThreads(EXTERNAL_SLEEPING_PUT_CONCURRENCY);
		} else {
			externalPutPool.setMaxThreads(EXTERNAL_PUT_CONCURRENCY);
		}
		database.setSleeping(asleep);
	}

	public void	setSuspended(boolean susp) {
		suspended	= susp;
		if (susp) {
			transport.setSuspended(true);
			DHTRouter current_router = router;
			if (current_router != null) {
				current_router.setSuspended(true);
			}
			database.setSuspended(true);
		} else {
			database.setSuspended(false);
			DHTRouter current_router = router;
			if (current_router != null) {
				current_router.setSuspended(false);
			}
			transport.setSuspended(false);
		}
	}

	public DHTControlStats getStats() {
		return (stats);
	}

	public DHTTransport	getTransport() {
		return (transport);
	}

	public DHTRouter getRouter() {
		return (router);
	}

	public DHTDB getDataBase() {
		return (database);
	}

	public void contactImported(
			DHTTransportContact	contact,
			boolean				isBootstrap) {
		router.contactKnown(
				contact.getID(), 
				new DHTControlContactImpl(contact), 
				isBootstrap);
	}

	public void contactRemoved(DHTTransportContact	contact) {
		// obviously we don't want to remove ourselves
		if (!router.isID( contact.getID())) {
			router.contactDead(contact.getID(), true);
		}
	}

	public void	exportState(
		DataOutputStream	daos,
		int					max ) throws IOException
	{
		/*
		 * We need to be a bit smart about exporting state to deal with the situation where a
		 * DHT is started (with good import state) and then stopped before the goodness of the
		 * state can be re-established. So we remember what we imported and take account of this
		 * on a re-export
		 */
		// get all the contacts
		List	contacts = router.findBestContacts(0);
		// give priority to any that were alive before and are alive now
		List	to_save 	= new ArrayList();
		List	reserves	= new ArrayList();
		//System.out.println("Exporting");
		for (int i=0;i<contacts.size();i++) {
			DHTRouterContact	contact = (DHTRouterContact)contacts.get(i);
			Object[]	imported = (Object[])importedState.get(new HashWrapper( contact.getID()));
			if (imported != null) {
				if (contact.isAlive()) {
					// definitely want to keep this one
					to_save.add(contact);
				} else if (!contact.isFailing()) {
					// dunno if its still good or not, however its got to be better than any
					// new ones that we didn't import who aren't known to be alive
					reserves.add(contact);
				}
			}
		}
		//System.out.println("    initial to_save = " + to_save.size() + ", reserves = " + reserves.size());
		// now pull out any live ones
		for (int i=0;i<contacts.size();i++) {
			DHTRouterContact	contact = (DHTRouterContact)contacts.get(i);
			if (contact.isAlive() && !to_save.contains( contact)) {
				to_save.add(contact);
			}
		}
		//System.out.println("    after adding live ones = " + to_save.size());
		// now add any reserve ones
		for (int i=0;i<reserves.size();i++) {
			DHTRouterContact	contact = (DHTRouterContact)reserves.get(i);
			if (!to_save.contains( contact)) {
				to_save.add(contact);
			}
		}
		//System.out.println("    after adding reserves = " + to_save.size());
		// now add in the rest!
		for (int i=0;i<contacts.size();i++) {
			DHTRouterContact	contact = (DHTRouterContact)contacts.get(i);
			if (!to_save.contains(contact)) {
				to_save.add(contact);
			}
		}
		// and finally remove the invalid ones
		Iterator	it = to_save.iterator();
		while (it.hasNext()) {
			DHTRouterContact	contact	= (DHTRouterContact)it.next();
			DHTTransportContact	t_contact = ((DHTControlContact)contact.getAttachment()).getTransportContact();
			if (!t_contact.isValid()) {
				it.remove();
			}
		}
		//System.out.println("    finally = " + to_save.size());
		int	num_to_write = Math.min( max, to_save.size());
		daos.writeInt(num_to_write);
		for (int i=0;i<num_to_write;i++) {
			DHTRouterContact	contact = (DHTRouterContact)to_save.get(i);
			//System.out.println("export:" + contact.getString());
			daos.writeLong( contact.getTimeAlive());
			DHTTransportContact	t_contact = ((DHTControlContact)contact.getAttachment()).getTransportContact();
			try {
				t_contact.exportContact(daos);
			} catch (DHTTransportException e) {
				// shouldn't fail as for a contact to make it to the router
				// it should be valid...
				Debug.printStackTrace(e);
				throw (new IOException( e.getMessage()));
			}
		}
		daos.flush();
	}

	public void importState(DataInputStream dais) throws IOException {
		int	num = dais.readInt();
		for (int i=0;i<num;i++) {
			try {
				long timeAlive = dais.readLong();
				DHTTransportContact	contact = transport.importContact(dais, false);
				importedState.put(new HashWrapper(contact.getID()), new Object[]{ new Long(timeAlive), contact });
			} catch (DHTTransportException e) {
				Debug.printStackTrace(e);
			}
		}
	}

	public void seed(final boolean fullWait) {
		
		final AESemaphore sem = new AESemaphore("DHTControl:seed");
		lookup(internalLookupPool, 
				false,
				router.getID(), 		// byte[] lookupId
				"Seeding DHT", 			// String description
				(byte)0, 				// short flags
				false, 					// (boolean) true: value search, false: node search
				0,						//
				searchConcurrency*4,	// int concurrency
				1,						//
				router.getK(),			// int searchAccuracy
				new LookupResultHandler(new DHTOperationAdapter()) {
			
					public void diversify(
						DHTTransportContact	cause,
						byte diversificationType) {}
					
					public void closest(List res) {
						if (!fullWait) {
							sem.release();
						}
						seeded = true;
						try {
							router.seed();
						} finally {
							if (fullWait) {
								sem.release();
							}
						}
					}
				});
		
		// we always wait at least a minimum amount of time before returning
		long	start = SystemTime.getCurrentTime();
		sem.reserve(INTEGRATION_TIME_MAX);
		long	now = SystemTime.getCurrentTime();
		if (now < start) {
			start	= now;
		}
		
		long	remaining = INTEGRATION_TIME_MAX - (now - start);
		if (remaining > 500 && !fullWait) {
			logger.log("Initial integration completed, waiting " + remaining + " ms for second phase to start");
			try {
				Thread.sleep(remaining);
			} catch (Throwable e) {
				Debug.out(e);
			}
		}
	}

	public boolean isSeeded() {
		return (seeded);
	}

	public void setSeeded() {
		// manually set as seeded
		seeded = true;
		router.seed();
	}

	protected void poke() {
		if (!enableRandomPoking) {

			return;
		}

		long	now = SystemTime.getCurrentTime();

		if (	now < lastLookup ||
				now - lastLookup > RANDOM_QUERY_PERIOD) {

			lastLookup	= now;

				// we don't want this to be blocking as it'll stuff the stats

			externalLookupPool.run(
				new DhtTask(externalLookupPool) {
					private byte[]	target = {};

					public void runSupport() {
						target = router.refreshRandom();
					}

					protected void cancel() {}
					
					public byte[] getTarget() {
						return (target);
					}

					@Override
					public DHTControlActivity.ActivityState getCurrentState() {
						return (null);
					}

					public String getDescription() {
						return ("Random Query");
					}
				});
		}
	}

	public void put(
		byte[]					_unencoded_key,
		String					_description,
		byte[]					_value,
		short					_flags,
		byte					_life_hours,
		byte					_replication_control,
		boolean					_high_priority,
		DHTOperationListener	_listener) {
		
		// public entry point for explicit publishes
		if (_value.length == 0) {
			// zero length denotes value removal
			throw (new RuntimeException("zero length values not supported"));
		}

		byte[] encodedKey = encodeKey(_unencoded_key);

		if (DHTLog.isOn()) {
			DHTLog.log("put for " + DHTLog.getString( encodedKey));
		}

		DHTDBValue value = database.store(new HashWrapper(encodedKey), _value, _flags, _life_hours, _replication_control);

		put(
			externalPutPool,
			_high_priority,
			encodedKey,
			_description,
			value,
			_flags,
			0,
			true,
			new HashSet(),
			1,
			_listener instanceof DHTOperationListenerDemuxer ?
					(DHTOperationListenerDemuxer)_listener :
					new DHTOperationListenerDemuxer(_listener)
		);
	}

	public void putEncodedKey(
		byte[]				encoded_key,
		String				description,
		DHTTransportValue	value,
		long				timeout,
		boolean				original_mappings) {
		put( 	internalPutPool,
				false,
				encoded_key,
				description,
				value,
				(byte)0,
				timeout,
				original_mappings,
				new HashSet(),
				1,
				new DHTOperationListenerDemuxer(new DHTOperationAdapter()));
	}


	protected void put(
		ThreadPool					thread_pool,
		boolean						high_priority,
		byte[]						initial_encoded_key,
		String						description,
		DHTTransportValue			value,
		short						flags,
		long						timeout,
		boolean						original_mappings,
		Set							things_written,
		int							put_level,
		DHTOperationListenerDemuxer	listener) {
		
		put(thread_pool,
			high_priority,
			initial_encoded_key,
			description,
			new DHTTransportValue[]{ value },
			flags,
			timeout,
			original_mappings,
			things_written,
			put_level,
			listener);
	}

	protected void put(
		final ThreadPool					threadPool,
		final boolean						highPriority,
		final byte[]						initialEncodedKey,
		final String						description,
		final DHTTransportValue[]			values,
		final short							flags,
		final long							timeout,
		final boolean						original_mappings,
		final Set							things_written,
		final int							put_level,
		final DHTOperationListenerDemuxer	listener) {
		
		// get the initial starting point for the put - may have previously been diversified
		byte[][] encoded_keys =
			adapter.diversify(
					description,
					null,
					true,
					true,
					initialEncodedKey,
					DHT.DT_NONE,
					original_mappings,
					getMaxDivDepth());
		if (encoded_keys.length == 0) {
			// over-diversified
			listener.diversified("Over-diversification of [" + description + "]");
			listener.complete(false);
			return;
		}
		
		// may be > 1 if diversification is replicating (for load balancing)
		for (int i=0;i<encoded_keys.length;i++) {
			final byte[]	encodedKey	= encoded_keys[i];
			HashWrapper	hw = new HashWrapper(encodedKey);
			synchronized(things_written) {
				if (things_written.contains( hw)) {
					// System.out.println("put: skipping key as already written");
					continue;
				}
				things_written.add(hw);
			}
			
			final String thisDescription =
				Arrays.equals(encodedKey, initialEncodedKey) ?
						description :
						("Diversification of [" + description + "]");
			
			lookup(
					threadPool,
					highPriority,
					encodedKey,
					thisDescription,
					(short)(flags | DHT.FLAG_LOOKUP_FOR_STORE),
					false, 				// (boolean) true: value search, false: node search
					timeout,
					searchConcurrency,
					1,
					router.getK(),
					new LookupResultHandler(listener) {
				
						public void diversify(
							DHTTransportContact	cause,
							byte				diversification_type) {
							Debug.out("Shouldn't get a diversify on a lookup-node");
						}
						
						public void closest(List _closest) {
							put(
								threadPool,
								highPriority,
								new byte[][]{ encodedKey },
								"Store of [" + thisDescription + "]",
								new DHTTransportValue[][]{ values },
								flags,
								_closest,
								timeout,
								listener,
								true,
								things_written,
								put_level,
								false
							);
						}
					});
		}
	}

	public void putDirectEncodedKeys(
		byte[][]				encoded_keys,
		String					description,
		DHTTransportValue[][]	value_sets,
		List					contacts) {
			// we don't consider diversification for direct puts (these are for republishing
			// of cached mappings and we maintain these as normal - its up to the original
			// publisher to diversify as required)

		put( 	internalPutPool,
				false,
				encoded_keys,
				description,
				value_sets,
				(byte)0,
				contacts,
				0,
				new DHTOperationListenerDemuxer(new DHTOperationAdapter()),
				false,
				new HashSet(),
				1,
				false);
	}

	public void putDirectEncodedKeys(
		byte[][]				encoded_keys,
		String					description,
		DHTTransportValue[][]	value_sets,
		DHTTransportContact		contact,
		DHTOperationListener	listener) {
			// we don't consider diversification for direct puts (these are for republishing
			// of cached mappings and we maintain these as normal - its up to the original
			// publisher to diversify as required)

		List<DHTTransportContact> contacts = new ArrayList<DHTTransportContact>(1);

		contacts.add(contact);

		put( 	internalPutPool,
				false,
				encoded_keys,
				description,
				value_sets,
				(byte)0,
				contacts,
				0,
				new DHTOperationListenerDemuxer(listener),
				false,
				new HashSet(),
				1,
				false);
	}

	public byte[]
	getObfuscatedKey(
		byte[]		plain_key) {
		int	length = plain_key.length;

		byte[]	obs_key = new byte[ length ];

		System.arraycopy(plain_key, 0, obs_key, 0, 5);

			// ensure plain key and obfuscated one differ at subsequent bytes to prevent potential
			// clashes with code that uses 'n' byte prefix (e.g. DB survey code)

		for (int i=6;i<length;i++) {

			if (plain_key[i] == 0) {

				obs_key[i] = 1;
			}
		}

			// finally copy over last two bytes for code that uses challenge-response on this
			// (survey code)

		obs_key[length-2] = plain_key[length-2];
		obs_key[length-1] = plain_key[length-1];

		return (obs_key);
	}

	protected byte[]
	getObfuscatedValue(
		byte[]		plain_key) {
        RC4Engine	engine = new RC4Engine();

		CipherParameters	params = new KeyParameter(new SHA1Simple().calculateHash( plain_key));

		engine.init(true, params);

		byte[]	temp = new byte[1024];

		engine.processBytes(temp, 0, 1024, temp, 0);

		final byte[] obs_value = new byte[ plain_key.length ];

		engine.processBytes(plain_key, 0, plain_key.length, obs_value, 0);

		return (obs_value);
	}

	protected DHTTransportValue
	getObfuscatedValue(
		final DHTTransportValue		basis,
		byte[]						plain_key) {
		final byte[] obs_value = getObfuscatedValue(plain_key);

		return (
			new DHTTransportValue() {
				public boolean isLocal() {
					return ( basis.isLocal());
				}

				public long getCreationTime() {
					return ( basis.getCreationTime());
				}

				public byte[]
				getValue() {
					return (obs_value);
				}

				public int getVersion() {
					return ( basis.getVersion());
				}

				public DHTTransportContact
				getOriginator() {
					return ( basis.getOriginator());
				}

				public int getFlags() {
					return ( basis.getFlags());
				}

				public int getLifeTimeHours() {
					return ( basis.getLifeTimeHours());
				}

				public byte
				getReplicationControl() {
					return ( basis.getReplicationControl());
				}

				public byte
				getReplicationFactor() {
					return ( basis.getReplicationFactor());
				}

				public byte
				getReplicationFrequencyHours() {
					return ( basis.getReplicationFrequencyHours());
				}

				public String getString() {
					return ("obs: " + basis.getString());
				}
			});
	}

	protected void put(
		final ThreadPool					threadPool,
		final boolean						highPriority,
		byte[][]							initialEncodedKeys,
		final String						description,
		final DHTTransportValue[][]			initialValueSets,
		final short							flags,
		final List							contacts,
		final long							timeout,
		final DHTOperationListenerDemuxer	listener,
		final boolean						considerDiversification,
		final Set							thingsWritten,
		final int							putLevel,
		final boolean						immediate) {
		
		int maxDepth = getMaxDivDepth();
		if (putLevel > maxDepth) {
			Debug.out("Put level exceeded, terminating diversification (level=" + putLevel + ",max=" + maxDepth + ")");
			listener.incrementCompletes();
			listener.complete(false);
			return;
		}
		
		boolean[]	ok = new boolean[initialEncodedKeys.length];
		int	failed = 0;
		for (int i=0;i<initialEncodedKeys.length;i++) {
			if (! (ok[i] = !database.isKeyBlocked(initialEncodedKeys[i]))) {
				failed++;
			}
		}
		
		// if all failed then nothing to do
		if (failed == ok.length) {
			listener.incrementCompletes();
			listener.complete(false);
			return;
		}
		
		final byte[][] 				encoded_keys 	= failed==0?initialEncodedKeys:new byte[ok.length-failed][];
		final DHTTransportValue[][] value_sets 		= failed==0?initialValueSets:new DHTTransportValue[ok.length-failed][];
		if (failed > 0) {
			int	pos = 0;
			for (int i=0;i<ok.length;i++) {
				if (ok[i]) {
					encoded_keys[ pos ] = initialEncodedKeys[i];
					value_sets[ pos ] 	= initialValueSets[i];
					pos++;
				}
			}
		}
		
		final byte[][] 				obs_keys;
		final DHTTransportValue[][]	obs_vals;
		if ((flags & DHT.FLAG_OBFUSCATE_LOOKUP) != 0) {
			if (encoded_keys.length != 1) {
				Debug.out("inconsistent - expected one key");
			}
			if (value_sets[0].length != 1) {
				Debug.out("inconsistent - expected one value");
			}
			obs_keys	= new byte[1][];
			obs_vals	= new DHTTransportValue[1][1];
			obs_keys[0] 	= getObfuscatedKey(encoded_keys[0]);
			obs_vals[0][0]	= getObfuscatedValue(value_sets[0][0], encoded_keys[0]);
		} else {
			obs_keys	= null;
			obs_vals	= null;
		}
		
		// only diversify on one hit as we're storing at closest 'n' so we only need to
		// do it once for each key
		final boolean[]	diversified = new boolean[encoded_keys.length];
		int	skipped = 0;
		for (int i=0;i<contacts.size();i++) {
			final DHTTransportContact	contact = (DHTTransportContact)contacts.get(i);
			if (router.isID( contact.getID())) {
				// don't send to ourselves!
				skipped++;
			} else {
				boolean skip_this = false;
				synchronized(thingsWritten) {
					if (thingsWritten.contains(contact)) {
						// if we've come back to an already hit contact due to a diversification loop
						// then ignore it
						// Debug.out("Put: contact encountered for a second time, ignoring");
						skipped++;
						skip_this	= true;
					} else {
						thingsWritten.add(contact);
					}
				}
				
				if (!skip_this) {
					try {
						for (int j=0;j<value_sets.length;j++) {
							for (int k=0;k<value_sets[j].length;k++) {
								listener.wrote(contact, value_sets[j][k]);
							}
						}
						
						// each store is going to report its complete event
						listener.incrementCompletes();
						contact.sendStore(
							new DHTTransportReplyHandlerAdapter() {
								
								public void storeReply(
									DHTTransportContact _contact,
									byte[]				_diversifications) {
									boolean	complete_is_async = false;
									try {
										if (DHTLog.isOn()) {
											DHTLog.log("Store OK " + DHTLog.getString(_contact));
										}
										router.contactAlive( _contact.getID(), new DHTControlContactImpl(_contact));
										// can be null for old protocol versions
										boolean div_done = false;
										if (considerDiversification && _diversifications != null) {
											for (int j=0;j<_diversifications.length;j++) {
												if (_diversifications[j] != DHT.DT_NONE && !diversified[j]) {
													div_done = true;
													diversified[j]	= true;
													byte[][] diversified_keys =
														adapter.diversify(description, _contact, true, false, encoded_keys[j], _diversifications[j], false, getMaxDivDepth());

													logDiversification(_contact, encoded_keys, diversified_keys);
													for (int k=0;k<diversified_keys.length;k++) {
														put(	threadPool,
																highPriority,
																diversified_keys[k],
																"Diversification of [" + description + "]",
																value_sets[j],
																flags,
																timeout,
																false,
																thingsWritten,
																putLevel + 1,
																listener);
													}
												}
											}
										}
										
										if (!div_done) {
											if (obs_keys != null) {
												contact.sendStore(
													new DHTTransportReplyHandlerAdapter() {
														public void storeReply(
															DHTTransportContact _contact,
															byte[]				_diversifications) {
															if (DHTLog.isOn()) {
																DHTLog.log("Obs store OK " + DHTLog.getString( _contact));
															}
															listener.complete(false);
														}
														
														public void failed(
															DHTTransportContact 	_contact,
															Throwable 				_error) {
															if (DHTLog.isOn()) {
																DHTLog.log("Obs store failed " + DHTLog.getString( _contact) + " -> failed: " + _error.getMessage());
															}
															listener.complete(true);
														}
													},
													obs_keys,
													obs_vals,
													immediate);
												complete_is_async = true;
											}
										}
									} finally {
										if (!complete_is_async) {
											listener.complete(false);
										}
									}
								}
								
								public void failed(
									DHTTransportContact 	_contact,
									Throwable 				_error) {
									try {
										if (DHTLog.isOn()) {
											DHTLog.log("Store failed " + DHTLog.getString( _contact) + " -> failed: " + _error.getMessage());
										}
										router.contactDead(_contact.getID(), false);
									} finally {
										listener.complete(true);
									}
								}
								
								public void keyBlockRequest(
									DHTTransportContact		contact,
									byte[]					request,
									byte[]					key_signature) {
									DHTStorageBlock	key_block = database.keyBlockRequest(null, request, key_signature);
									if (key_block != null) {
										// remove this key for any subsequent publishes. Quickest hack
										// is to change it into a random key value - this will be rejected
										// by the recipient as not being close enough anyway
										for (int i=0;i<encoded_keys.length;i++) {
											if (Arrays.equals( encoded_keys[i], key_block.getKey())) {
												byte[]	dummy = new byte[encoded_keys[i].length];
												RandomUtils.nextBytes(dummy);
												encoded_keys[i] = dummy;
											}
										}
									}
								}
							},
							encoded_keys,
							value_sets,
							immediate);
					} catch (Throwable e) {
						Debug.printStackTrace(e);
					}
				}
			}
		}
		if (skipped == contacts.size()) {
			listener.incrementCompletes();
			listener.complete(false);
		}
	}

	protected int getMaxDivDepth() {
		if (combinedDhtEstimate == 0) {

			getEstimatedDHTSize();
		}

		int max = Math.max(2, combinedDhtEstimateMag);

		// System.out.println("net:" + transport.getNetwork() + " - max_div_depth=" + max);

		return (max);
	}

	protected void logDiversification(
		final DHTTransportContact		contact,
		final byte[][]					keys,
		final byte[][]					div) {
		/*
		System.out.println("Div check starts for " + contact.getString());

		String	keys_str = "";

		for (int i=0;i<keys.length;i++) {

			keys_str += (i==0?"":",") + ByteFormatter.encodeString(keys[i]);
		}

		String	div_str = "";

		for (int i=0;i<div.length;i++) {

			div_str += (i==0?"":",") + ByteFormatter.encodeString(div[i]);
		}

		System.out.println("    " + keys_str + " -> " + div_str);

		new AEThread2("sdsd", true) {
			public void run() {
				DHTTransportFullStats stats = contact.getStats();

				System.out.println( contact.getString() + "-> " +(stats==null?"<null>":stats.getString()));
			}
		}.start();
		*/
	}

	public DHTTransportValue
	getLocalValue(
		byte[]		unencoded_key) {
		final byte[]	encoded_key = encodeKey(unencoded_key);

		if (DHTLog.isOn()) {
			DHTLog.log("getLocalValue for " + DHTLog.getString( encoded_key));
		}

		DHTDBValue	res = database.get(new HashWrapper( encoded_key));

		if (res == null) {

			return (null);
		}

		return (res);
	}

	public List<DHTTransportValue>
	getStoredValues(
		byte[]		unencoded_key) {
		final byte[]	encoded_key = encodeKey(unencoded_key);

		if (DHTLog.isOn()) {
			DHTLog.log("getStoredValues for " + DHTLog.getString( encoded_key));
		}

		List<DHTDBValue>	res = database.getAllValues(new HashWrapper( encoded_key));

		if (res == null) {

			return (null);
		}

		ArrayList<DHTTransportValue>	temp = new ArrayList<DHTTransportValue>( res.size());

		temp.addAll(res);

		return (temp);
	}

	public void get(
		byte[]						unencodedKey,
		String						description,
		short						flags,
		int							maxValues,
		long						timeout,
		boolean						exhaustive,
		boolean						highPriority,
		final DHTOperationListener	getListener) {
		final byte[]	encodedKey = encodeKey(unencodedKey);

		if (DHTLog.isOn()) {
			DHTLog.log("get for " + DHTLog.getString(encodedKey));
		}

		final DhtTaskSet[] taskSet = { null };

		DHTOperationListenerDemuxer demuxer =
			new DHTOperationListenerDemuxer(
				new DHTOperationListener() {
					public void searching(
						DHTTransportContact	contact,
						int					level,
						int					active_searches) {
						getListener.searching(contact, level, active_searches);
					}

					public boolean diversified(String desc) {
						return (getListener.diversified(desc));
					}

					public void found(
						DHTTransportContact	contact,
						boolean				isClosest) {
						getListener.found(contact,isClosest);
					}

					public void read(
						DHTTransportContact	contact,
						DHTTransportValue	value) {
						getListener.read(contact, value);
					}

					public void wrote(
						DHTTransportContact	contact,
						DHTTransportValue	value) {
						getListener.wrote(contact, value);
					}

					public void complete(boolean timeout) {
						try {
							getListener.complete(timeout);
						} catch (Throwable e) {
							Debug.out(e);
						} finally {
							if (taskSet[0] != null) {
								taskSet[0].cancel();
							}
						}
					}
				});


		taskSet[0] = getSupport(encodedKey, description, flags, maxValues, timeout, exhaustive, highPriority, demuxer);
	}

	public boolean isDiversified(byte[] unencoded_key) {
		final byte[]	encoded_key = encodeKey(unencoded_key);
		return (adapter.isDiversified(encoded_key));
	}

	public boolean lookup(
   		byte[]							unencodedKey,
   		String							description,
   		long							timeout,
   		final DHTOperationListener		lookupListener) {
		return (lookupEncoded(encodeKey(unencodedKey), description, timeout, false, lookupListener));
	}

	public boolean lookupEncoded(
   		byte[]							encodedKey,
   		String							description,
   		long							timeout,
   		boolean							highPriority,
   		final DHTOperationListener		lookupListener) {
		
		if (DHTLog.isOn()) {
			DHTLog.log("lookup for " + DHTLog.getString(encodedKey));
		}

		final AESemaphore sem = new AESemaphore("DHTControl:lookup");

		final boolean[] diversified = { false };

		DHTOperationListener delegate =
			new DHTOperationListener() {
			
				public void searching(
					DHTTransportContact	contact,
					int					level,
					int					active_searches) {
					lookupListener.searching(contact, level, active_searches);
				}

				public void found(DHTTransportContact contact, boolean is_closest) {
				}

				public boolean diversified(String desc) {
					return (lookupListener.diversified(desc));
				}

				public void read(DHTTransportContact contact, DHTTransportValue value) {
				}

				public void wrote(DHTTransportContact contact, DHTTransportValue value) {
				}

				public void complete(boolean timeout) {
					lookupListener.complete(timeout);
					sem.release();
				}
			};

		lookup(
				externalLookupPool,
				highPriority,
				encodedKey,
				description,
				(byte)0,			// 
				false,				// (boolean) true: value search, false: node search
				timeout,
				searchConcurrency,
				1,					// 
				router.getK(),		// searchAccuracy
				new LookupResultHandler(delegate) {
		
					public void diversify(
						DHTTransportContact	cause,
						byte				diversification_type) {
						diversified("Diversification of [lookup]");
						diversified[0] = true;
					}

					public void closest(List closest) {
						for (int i=0;i<closest.size();i++) {
							lookupListener.found((DHTTransportContact)closest.get(i),true);
						}
					}
				}
		);

		sem.reserve();

		return (diversified[0]);
	}

	protected DhtTaskSet getSupport(
		final byte[]						initial_encoded_key,
		final String						description,
		final short							flags,
		final int							maxValues,
		final long							timeout,
		final boolean						exhaustive,
		final boolean						highPriority,
		final DHTOperationListenerDemuxer	getListener) {
		final DhtTaskSet result = new DhtTaskSet();
		// get the initial starting point for the get - may have previously been diversified
		byte[][]	encoded_keys = adapter.diversify(description, null, false, true, initial_encoded_key, DHT.DT_NONE, exhaustive, getMaxDivDepth());
		if  (encoded_keys.length == 0) {
			// over-diversified
			getListener.diversified("Over-diversification of [" + description + "]");
			getListener.complete(false);
			return (result);
		}
		
		for (int i=0;i<encoded_keys.length;i++) {
			final boolean[]	diversified = { false };
			final byte[]	encodedKey	= encoded_keys[i];
			boolean	div = !Arrays.equals(encodedKey, initial_encoded_key);
			final String	thisDescription =
				div?("Diversification of [" + description + "]" ):description;
			if (div) {
				if (!getListener.diversified(thisDescription)) {
					continue;
				}
			}
			boolean	isStatsQuery = (flags & DHT.FLAG_STATS ) != 0;
			result.add(
				lookup(
					externalLookupPool,
					highPriority,		// 
					encodedKey,			// 
					thisDescription,	// 
					flags,				// 
					true,				// (boolean) true: value search, false: node search
					timeout,
					isStatsQuery?searchConcurrency*2:searchConcurrency,
					maxValues,
					router.getK(),
					new LookupResultHandler(getListener) {
					
						private final List	found_values = new ArrayList();
						
						public void diversify(
							DHTTransportContact	cause,
							byte				diversificationType) {
							boolean okToDiv = diversified("Diversification of [" + thisDescription + "]");
							// we only want to follow one diversification
							if (okToDiv && !diversified[0]) {
								diversified[0] = true;
								int	rem = maxValues==0?0:( maxValues - found_values.size());
								if (maxValues == 0 || rem > 0) {
									byte[][]	diversified_keys = adapter.diversify( description, cause, false, false, encodedKey, diversificationType, exhaustive, getMaxDivDepth());
									if (diversified_keys.length > 0) {
										// should return a max of 1 (0 if diversification refused)
										// however, could change one day to search > 1
										for (int j=0;j<diversified_keys.length;j++) {
											if (!result.isCancelled()) {
												result.add(
													getSupport(diversified_keys[j], "Diversification of [" + thisDescription + "]", flags, rem,  timeout, exhaustive, highPriority, getListener));
											}
										}
									}
								}
							}
						}
						
						public void read(
							DHTTransportContact	contact,
							DHTTransportValue	value) {
							found_values.add(value);
							super.read(contact, value);
						}
						
						public void closest(List	closest) {
							/* we don't use teh cache-at-closest kad feature
							if (found_values.size() > 0) {
								DHTTransportValue[]	values = new DHTTransportValue[found_values.size()];
								found_values.toArray(values);
									// cache the values at the 'n' closest seen locations
								for (int k=0;k<Math.min(cache_at_closest_n,closest.size());k++) {
									DHTTransportContact	contact = (DHTTransportContact)(DHTTransportContact)closest.get(k);
									for (int j=0;j<values.length;j++) {
										wrote(contact, values[j]);
									}
									contact.sendStore(
											new DHTTransportReplyHandlerAdapter() {
												public void storeReply(
													DHTTransportContact _contact,
													byte[]				_diversifications) {
														// don't consider diversification for cache stores as we're not that
														// bothered
													DHTLog.log("Cache store OK " + DHTLog.getString( _contact));
													router.contactAlive( _contact.getID(), new DHTControlContactImpl(_contact));
												}
												
												public void failed(
													DHTTransportContact 	_contact,
													Throwable 				_error) {
													DHTLog.log("Cache store failed " + DHTLog.getString( _contact) + " -> failed: " + _error.getMessage());
													router.contactDead(_contact.getID(), false);
												}
											},
											new byte[][]{ encoded_key },
											new DHTTransportValue[][]{ values });
								}
							}
							*/
						}
					}
				)
			);
		}
		return (result);
	}

	public byte[]
	remove(
		byte[]					unencoded_key,
		String					description,
		DHTOperationListener	listener) {
		final byte[]	encoded_key = encodeKey(unencoded_key);
		if (DHTLog.isOn()) {
			DHTLog.log("remove for " + DHTLog.getString( encoded_key));
		}
		DHTDBValue	res = database.remove(localContact, new HashWrapper( encoded_key));
		if (res == null) {
				// not found locally, nothing to do
			return (null);
		} else {
				// we remove a key by pushing it back out again with zero length value
			put( 	externalPutPool,
					false,
					encoded_key,
					description,
					res,
					(byte)res.getFlags(),
					0,
					true,
					new HashSet(),
					1,
					new DHTOperationListenerDemuxer(listener));
			return (res.getValue());
		}
	}

	public byte[] remove(
		DHTTransportContact[]	contacts,
		byte[]					unencoded_key,
		String					description,
		DHTOperationListener	listener) {
		final byte[]	encoded_key = encodeKey(unencoded_key);
		if (DHTLog.isOn()) {
			DHTLog.log("remove for " + DHTLog.getString( encoded_key));
		}
		DHTDBValue	res = database.remove(localContact, new HashWrapper( encoded_key));
		if (res == null) {
				// not found locally, nothing to do
			return (null);
		} else {
			List	contacts_l = new ArrayList(contacts.length);
			Collections.addAll(contacts_l, contacts);
			put( 	externalPutPool,
					true,
					new byte[][]{ encoded_key },
					"Store of [" + description + "]",
					new DHTTransportValue[][]{{ res }},
					(byte)res.getFlags(),
					contacts_l,
					0,
					new DHTOperationListenerDemuxer(listener),
					true,
					new HashSet(),
					1,
					true);
			return (res.getValue());
		}
	}

	/**
	 * The lookup method returns up to K closest nodes to the target
	 * @param lookupId
	 * @return
	 */
	protected DhtTask lookup(
		final ThreadPool 			threadPool,
		boolean 					highPriority,
		final byte[] 				_lookupId,
		final String 				description,
		final short					flags,
		final boolean 				valueSearch,
		final long 					timeout,
		final int 					concurrency,
		final int 					maxValues,
		final int 					searchAccuracy,
		final LookupResultHandler	handler) {
		
		/*int count = SingleCounter2.getInstance().getAndIncreaseCount();
		Log.d(TAG, String.format("how many times lookup() is called... #%d", count));
		Log.d(TAG, "_lookupId = " + Util.toHexString(_lookupId));*/
		
		//new Throwable().printStackTrace();
		
		/*if ( 
				   (flags & DHT.FLAG_LOOKUP_FOR_STORE) == 1
				|| valueSearch
		)
			new Throwable().printStackTrace();*/
		
		/*int count = SingleCounter0.getInstance().getAndIncreaseCount();
		Log.d(TAG, String.format("lookup() is called... #%d", count));
		Log.d(TAG, "_lookupId = " + Util.toHexString(_lookupId));
		Log.d(TAG, "valueSearch = " + valueSearch);
		if (count <= 2)
			new Throwable().printStackTrace();*/
		
		final byte[] lookupId;
		final byte[] obsValue;

		if ((flags & DHT.FLAG_OBFUSCATE_LOOKUP) != 0) {
			lookupId = getObfuscatedKey(_lookupId);
			obsValue = getObfuscatedValue(_lookupId);
		} else {
			lookupId = _lookupId;
			obsValue = null;
		}

		DhtTask	task = new DhtTask(threadPool) {

			private String TAG = "DHTControlImpl.DhtTask";
		
			boolean timeoutOccurred = false;

			// keep querying successively closer nodes until we have got responses from the K
			// closest nodes that we've seen. We might get a bunch of closer nodes that then
			// fail to respond, which means we have reconsider further away nodes.
			// we keep a list of nodes that we have queried to avoid re-querying them.
			// we keep a list of nodes discovered that we have yet to query.
			// we have a parallel search limit of A. For each A we effectively loop grabbing
			// the currently closest unqueried node, querying it and adding the results to the
			// yet-to-query-set (unless already queried)
			// we terminate when we have received responses from the K closest nodes we know
			// about (excluding failed ones)
			// Note that we never widen the root of our search beyond the initial K closest
			// that we know about - this could be relaxed
			// contacts remaining to query
			// closest at front

			Set<DHTTransportContact> contactsToQuery;
			AEMonitor contactsToQueryMon;
			Map<DHTTransportContact, Object[]> levelMap;

			// record the set of contacts we've queried to avoid re-queries
			ByteArrayHashMap<DHTTransportContact> contactsQueried;
			
			// record the set of contacts that we've had a reply from
			// furthest away at front
			Set<DHTTransportContact> okContacts;
			
			// this handles the search concurrency
			int idleSearches;
			int activeSearches;
			int valuesFound;
			int valueReplies;
			Set valuesFoundSet;
			boolean keyBlocked;
			long start;

			TimerEvent timeoutEvent;

			private int runningState = 1; // -1 terminated, 0 waiting, 1 running
			private int freeTasksCount = concurrency;

			private boolean	cancelled;

			// start the lookup
			@Override
			public void	runSupport() {
				startLookup();
			}

			private void startLookup() {
				contactsToQueryMon = new AEMonitor("DHTControl:ctq");
				
				//if (SingleCounter9.getInstance().getAndIncreaseCount() == 1)
					//new Throwable().printStackTrace();
				
				/*int count = SingleCounter0.getInstance().getAndIncreaseCount();
				if (count == 1)
					new Throwable().printStackTrace();
				Log.d(TAG, "count = " + count);
				Log.d(TAG, "startLookup() is called...");
				Log.d(TAG, "lookupId = " + Util.toHexString(lookupId));*/
				//Log.d(TAG, "K = " + K);
				contactsToQuery = getClosestContactsSet(lookupId, K, false);
				
				//Log.d(TAG, "contactsToQuery.size() = " + contactsToQuery.size());
				
				Iterator<DHTTransportContact> it;
				
				/*
				it = contactsToQuery.iterator();
				for (int i=0;it.hasNext();i++) {
					DHTTransportContact contact = it.next();
					Log.d(TAG, String.format("contact[%d].getAddress() = %s", i, contact.getAddress()));
				}*/
				
				levelMap = new LightHashMap<DHTTransportContact,Object[]>();

				// record the set of contacts we've queried to avoid re-queries
				contactsQueried = new ByteArrayHashMap<DHTTransportContact>();

				// record the set of contacts that we've had a reply from
				// furthest away at front
				okContacts = new SortedTransportContactSet(lookupId, false).getSet();
				
				// this handles the search concurrency
				valuesFoundSet = new HashSet();
				start = SystemTime.getMonotonousTime();

				lastLookup = SystemTime.getCurrentTime();
				handler.incrementCompletes();

				/*Iterator<DHTTransportContact>*/ it = contactsToQuery.iterator();
				while (it.hasNext()) {
					DHTTransportContact contact = (DHTTransportContact) it.next();
					handler.found(contact,false);
					levelMap.put(contact, new Object[]{ new Integer(0), null });
				}

				if (DHTLog.isOn()) {
					DHTLog.log("lookup for " + DHTLog.getString(lookupId));
				}

				if (valueSearch && database.isKeyBlocked(lookupId)) {
					DHTLog.log("lookup: terminates - key blocked");
					// bail out and pretend everything worked with zero results
					terminateLookup(false);
					return;
				}

				if (timeout > 0) {
					timeoutEvent = SimpleTimer.addEvent("DHT lookup timeout", SystemTime.getCurrentTime()+timeout, new TimerEventPerformer() {
						public void perform(TimerEvent event) {
							if (DHTLog.isOn()) {
								DHTLog.log("lookup: terminates - timeout");
							}
							//System.out.println("timeout");
							timeoutOccurred = true;
							terminateLookup(false);
						}
					});
				}
				lookupSteps();
			}

			private void terminateLookup(boolean error) {
				if (timeoutEvent != null)
					timeoutEvent.cancel();
				
				synchronized (this) {
					if (runningState == -1)
						return;
					runningState = -1;
				}
				
				try {
					if (!error) {
						// maybe unterminated searches still going on so protect ourselves
						// against concurrent modification of result set
						List closestRes = null;
						try {
							contactsToQueryMon.enter();
							if (DHTLog.isOn()) {
								DHTLog.log("lookup complete for " + DHTLog.getString(lookupId));
								DHTLog.log("    queried = " + DHTLog.getString(contactsQueried));
								DHTLog.log("    to query = " + DHTLog.getString(contactsToQuery));
								DHTLog.log("    ok = " + DHTLog.getString(okContacts));
							}
							closestRes = new ArrayList(okContacts);
							// we need to reverse the list as currently closest is at the end
							Collections.reverse(closestRes);
							if (timeout <= 0 && !valueSearch)
								// we can use the results of this to estimate the DHT size
								estimateDHTSize(lookupId, contactsQueried.values(), searchAccuracy);
						} finally {
							contactsToQueryMon.exit();
						}
						handler.closest(closestRes);
					}
					handler.complete(timeoutOccurred);
				} finally {
					releaseToPool();
				}
			}

			private synchronized boolean reserve() {
				if (freeTasksCount <= 0 || runningState == -1) {
					//System.out.println("reserve-exit");
					if (runningState == 1)
						runningState = 0;
					return false;
				}

				freeTasksCount--;
				return true;
			}

			private synchronized void release() {
				
				/*Log.d(TAG, "release() is called...");
				Throwable t = new Throwable();
				t.printStackTrace();*/
				
				freeTasksCount++;
				if (runningState == 0) {
					//System.out.println("release-start");
					runningState = 1;
					new AEThread2("DHT lookup runner",true) {
						public void run() {
							threadPool.registerThreadAsChild(worker);
							lookupSteps();
							threadPool.deregisterThreadAsChild(worker);
						}
					}.start();
				}
			}

			protected synchronized void cancel() {
				if (runningState != -1) {
					// System.out.println("Task cancelled");
				}
				cancelled = true;
			}

			// individual lookup steps
			private void lookupSteps() {
				try {
					boolean terminate = false;

					while (!cancelled) {
						if (timeout > 0) {
							long now = SystemTime.getMonotonousTime();

							long remaining = timeout - (now - start);
							if (remaining <= 0) {
								if (DHTLog.isOn()) {
									DHTLog.log("lookup: terminates - timeout");
								}

								timeoutOccurred = true;
								terminate = true;
								break;
							}

							if (!reserve())
								break; // temporary stop, will be revived by release() or until a timeout occurs


						} else if (!reserve())
							break; // temporary stop, will be revived by release()*/

						try {
							
							contactsToQueryMon.enter();
							
							// for stats queries the values returned are unique to target so don't assume 2 replies sufficient
							if (valuesFound >= maxValues || ((flags & DHT.FLAG_STATS) == 0 &&  valueReplies >= 2)) {
								// all hits should have the same values anyway...
								terminate = true;
								break;
							}

							// if we've received a key block then easiest way to terminate the query is to
							// dump any outstanding targets
							if (keyBlocked)
								contactsToQuery.clear();

							//Log.d(TAG, "contactsToQuery.size() = " + contactsToQuery.size());
							
							// if nothing pending then we need to wait for the results of a previous
							// search to arrive. Of course, if there are no searches active then
							// we've run out of things to do
							if (contactsToQuery.size() == 0) {
								if (activeSearches == 0) {
									if (DHTLog.isOn()) {
										DHTLog.log("lookup: terminates - no contacts left to query");
									}
									terminate = true;
									break;
								}
								idleSearches++;
								continue;
							}

							// select the next contact to search
							DHTTransportContact closest = (DHTTransportContact) contactsToQuery.iterator().next();

							// if the next closest is further away than the furthest successful hit so
							// far and we have K hits, we're done
							if (okContacts.size() == searchAccuracy) {
								DHTTransportContact furthestOk = (DHTTransportContact) okContacts.iterator().next();
								int distance = computeAndCompareDistances(furthestOk.getID(), closest.getID(), lookupId);
								if (distance <= 0) {
									if (DHTLog.isOn()) {
										DHTLog.log("lookup: terminates - we've searched the closest " + searchAccuracy + " contacts");
									}
									//Log.d(TAG, "lookup: terminates - we've searched the closest " + searchAccuracy + " contacts");
									terminate = true;
									break;
								}
							}

							// we optimise the first few entries based on their Vivaldi distance. Only a few
							// however as we don't want to start too far away from the target.
							if (contactsQueried.size() < concurrency) {
								DHTNetworkPosition[] locNps = localContact.getNetworkPositions();
								DHTTransportContact vp_closest = null;
								Iterator vp_it = contactsToQuery.iterator();
								int vp_countLimit = (concurrency * 2) - contactsQueried.size();
								int vp_count = 0;
								float bestDist = Float.MAX_VALUE;
								while (vp_it.hasNext() && vp_count < vp_countLimit) {
									vp_count++;
									DHTTransportContact entry = (DHTTransportContact) vp_it.next();
									DHTNetworkPosition[] remNps = entry.getNetworkPositions();

									float dist = DHTNetworkPositionManager.estimateRTT(locNps, remNps);
									if ((!Float.isNaN(dist)) && dist < bestDist) {
										bestDist = dist;
										vp_closest = entry;
										// System.out.println(start + ": lookup for " + DHTLog.getString2( lookup_id) + ": vp override (dist = " + dist + ")");
									}

									if (vp_closest != null) // override ID closest with VP closes
										closest = vp_closest;
								}
							}

							final DHTTransportContact f_closest = closest;
							
							/*Log.d(TAG, "_lookupId = " + Util.toHexString(_lookupId));
							Log.d(TAG, "closest.getID() = " + Util.toHexString(closest.getID()));*/
														
							contactsToQuery.remove(closest);
							contactsQueried.put(closest.getID(), closest);
							// never search ourselves!
							if (router.isID(closest.getID())) {
								release();
								continue;
							}

							final int searchLevel = ((Integer) levelMap.get(closest)[0]).intValue();
							activeSearches++;
							handler.searching(closest, searchLevel, activeSearches);

							DHTTransportReplyHandlerAdapter replyHandler = new DHTTransportReplyHandlerAdapter() {
								
								private boolean	valueReplyReceived = false;

								public void findNodeReply(
										DHTTransportContact targetContact,
										DHTTransportContact[] replyContacts) {
									
									/*Log.d(TAG, "targetContact.getAddress() = " + targetContact.getAddress());
									for (int i=0;i<replyContacts.length;i++) {
										Log.d(TAG, String.format("replyContacts[%d].getAddress() = %s", i, replyContacts[i].getAddress()));
									}*/
									//Log.d(TAG, "replyContacts.getAddress() = " + replyContacts.getAddress());
									/*Throwable t = new Throwable();
									t.printStackTrace();*/
									
									try {
										if (DHTLog.isOn()) {
											DHTLog.log("findNodeReply: " + DHTLog.getString(replyContacts));
										}

										router.contactAlive(targetContact.getID(), new DHTControlContactImpl(targetContact));
										for (int i = 0; i < replyContacts.length; i++) {
											DHTTransportContact contact = replyContacts[i];
											// ignore responses that are ourselves
											if (compareDistances(router.getID(), contact.getID()) == 0)
												continue;
											
											// dunno if its alive or not, however record its existance
											router.contactKnown(contact.getID(), new DHTControlContactImpl(contact), false);
										}
										
										try {
											contactsToQueryMon.enter();
											okContacts.add(targetContact);
											if (okContacts.size() > searchAccuracy) {
												// delete the furthest away
												Iterator<DHTTransportContact> ok_it = okContacts.iterator();
												ok_it.next();
												ok_it.remove();
											}
											
											for (int i = 0; i < replyContacts.length; i++) {
												DHTTransportContact contact = replyContacts[i];
												// ignore responses that are ourselves
												if (compareDistances(router.getID(), contact.getID()) == 0)
													continue;

												if (contactsQueried.get(contact.getID()) == null && (!contactsToQuery.contains(contact))) {
													if (DHTLog.isOn()) {
														DHTLog.log("    new contact for query: " + DHTLog.getString(contact));
													}

													contactsToQuery.add(contact);
													handler.found(contact,false);
													levelMap.put(contact, new Object[]{ new Integer(searchLevel + 1), targetContact });
													if (idleSearches > 0) {
														idleSearches--;
														release();
													}
												} else {
													// DHTLog.log("    already queried: " + DHTLog.getString( contact));
												}
											}
										} finally {
											contactsToQueryMon.exit();
										}
									} finally {
										try {
											contactsToQueryMon.enter();
											activeSearches--;
										} finally {
											contactsToQueryMon.exit();
										}
										release();
									}
								}

								public void findValueReply(
									DHTTransportContact 	contact,
									DHTTransportValue[] 	values,
									byte 					diversificationType, // hack - this is set to 99 when recursing here during obfuscated lookup
									boolean 				moreToCome) {

									if (DHTLog.isOn()) {
										DHTLog.log("findValueReply: " + DHTLog.getString(values) + ",mtc=" + moreToCome + ", dt=" + diversificationType);
									}

									boolean	obsRecurse = false;

									if (diversificationType == 99) {
										obsRecurse = true;
										diversificationType = DHT.DT_NONE;
									}

									try {
										if (!keyBlocked && diversificationType != DHT.DT_NONE) {
											// diversification instruction
											if ((flags & DHT.FLAG_STATS) == 0) {
												// ignore for stats queries as we're after the
												// target key's stats, not the diversification
												// thereof
												handler.diversify(contact, diversificationType);
											}
										}

										valueReplyReceived = true;
										router.contactAlive(contact.getID(), new DHTControlContactImpl(contact));
										int newValues = 0;
										if (!keyBlocked) {
											for (int i = 0; i < values.length; i++) {
												DHTTransportValue value = values[i];
												DHTTransportContact originator = value.getOriginator();
												// can't just use originator id as this value can be DOSed (see DB code)
												byte[] originatorId = originator.getID();
												byte[] valueBytes = value.getValue();
												byte[] valueId = new byte[originatorId.length + valueBytes.length];
												System.arraycopy(originatorId, 0, valueId, 0, originatorId.length);
												System.arraycopy(valueBytes, 0, valueId, originatorId.length, valueBytes.length);
												HashWrapper x = new HashWrapper(valueId);

												if (!valuesFoundSet.contains(x)) {

													if (obsValue != null && !obsRecurse) {

														// we have read the marker value, now issue a direct read with the
														// real key

														if (Arrays.equals(obsValue, valueBytes)) {

															moreToCome = true;

															final DHTTransportReplyHandlerAdapter f_outer = this;

															f_closest.sendFindValue(
																new DHTTransportReplyHandlerAdapter() {
																	
																	public void findValueReply(
																		DHTTransportContact 	contact,
																		DHTTransportValue[] 	values,
																		byte 					diversification_type,
																		boolean 				moreToCome) {
																		if (diversification_type == DHT.DT_NONE) {
																			f_outer.findValueReply(contact, values, (byte)99, false);
																		}
																	}

																	public void failed(
																		DHTTransportContact 	contact,
																		Throwable 				error) {
																		f_outer.failed(contact, error);
																	}
																},
																_lookupId, 1, flags);

															break;
														}
													} else {
														newValues++;
														valuesFoundSet.add(x);
														handler.read(contact, values[i]);
													}
												}
											}
										}

										try {
											contactsToQueryMon.enter();
											if (!moreToCome)
												valueReplies++;
											valuesFound += newValues;
										} finally {
											contactsToQueryMon.exit();
										}

									} finally {
										if (!moreToCome) {
											try {
												contactsToQueryMon.enter();
												activeSearches--;
											} finally {
												contactsToQueryMon.exit();
											}
											release();
										}
									}
								}

								public void findValueReply(DHTTransportContact contact, DHTTransportContact[] contacts) {
									findNodeReply(contact, contacts);
								}

								public void failed(DHTTransportContact targetContact, Throwable error) {
									try {
										// if at least one reply has been received then we
										// don't treat subsequent failure as indication of
										// a contact failure (just packet loss)
										if (!valueReplyReceived) {
											if (DHTLog.isOn()) {
												DHTLog.log("findNode/findValue " + DHTLog.getString(targetContact) + " -> failed: " + error.getMessage());
											}

											router.contactDead(targetContact.getID(), false);
										}
									} finally {
										try {
											contactsToQueryMon.enter();
											activeSearches--;
										} finally {
											contactsToQueryMon.exit();
										}
										release();
									}
								}

								public void keyBlockRequest(DHTTransportContact contact, byte[] request, byte[] keySignature) {
									// we don't want to kill the contact due to this so indicate that
									// it is ok by setting the flag
									if (database.keyBlockRequest(null, request, keySignature) != null)
										keyBlocked = true;
								}
							};
							
							router.recordLookup(lookupId);
							if (valueSearch) {
								int rem = maxValues - valuesFound;
								if (rem <= 0) {
									Debug.out("eh?");
									rem = 1;
								}
								closest.sendFindValue(replyHandler, lookupId, rem, flags);
							} else {
								//Log.d(TAG, "closest.getAddress() = " + closest.getAddress());
								closest.sendFindNode(replyHandler, lookupId, flags);
							}
						} finally {
							contactsToQueryMon.exit();
						}
					}

					if (terminate) {
						terminateLookup(false);
					} else if (cancelled) {
						terminateLookup(true);
					}
				} catch (Throwable e) {
					Debug.printStackTrace(e);
					terminateLookup(true);
				}
			}

			public byte[] getTarget() {
				return (lookupId);
			}

			public String getDescription() {
				return (description);
			}

			public DHTControlActivity.ActivityState getCurrentState() {

				DHTTransportContact	local = localContact;
				ANImpl root_node = new ANImpl(local);
				String result_str;

				if (timeoutOccurred) {
					result_str = "Timeout";
				} else {
					long elapsed = SystemTime.getMonotonousTime() - start;
					String	elapsed_str = elapsed < 1000 ? (elapsed + "ms") : ((elapsed / 1000) + "s");

					if (valueSearch) {
						result_str = valuesFound + " hits, time=" + elapsed_str;
					} else {
						result_str = "time=" + elapsed_str;
					}
				}

				ASImpl result = new ASImpl(root_node, result_str);

				// can be null if 'added' listener callback runs before class init complete...
				if (contactsToQueryMon != null) {
					contactsToQueryMon.enter();
					
					try {
						
						if (contactsQueried != null && levelMap != null) {
							
							List<Map.Entry<DHTTransportContact, Object[]>> lm_entries = new ArrayList<Map.Entry<DHTTransportContact,Object[]>>( levelMap.entrySet());
							Collections.sort(
								lm_entries,
								new Comparator<Map.Entry<DHTTransportContact, Object[]>>() {
									public int compare(
											Entry<DHTTransportContact, Object[]> o1,
											Entry<DHTTransportContact, Object[]> o2) {
										int	l1 = (Integer)o1.getValue()[0];
										int	l2 = (Integer)o2.getValue()[0];

										return (l1 - l2);
									}
								});

							Set<DHTTransportContact>	qd = new HashSet<DHTTransportContact>( contactsQueried.values());
							Map<DHTTransportContact,ANImpl>	node_map = new HashMap<DHTTransportContact, ANImpl>();
							node_map.put(local, root_node);

							for (Map.Entry<DHTTransportContact, Object[]> lme: lm_entries) {

								DHTTransportContact contact = lme.getKey();
								if (!qd.contains(contact)) {
									continue;
								}

								Object[] entry = lme.getValue();

								DHTTransportContact parent 	= (DHTTransportContact)entry[1];

								if (parent == null) {
									parent = local;
								}

								ANImpl pNode = node_map.get(parent);

								if (pNode == null) {
									Debug.out("eh");
								} else {
									ANImpl new_node = new ANImpl(contact);
									node_map.put(contact, new_node);
									pNode.add(new_node);
								}
							}
						}

					} finally {
						contactsToQueryMon.exit();
					}
				}
				return (result);
			}
		};

		threadPool.run(task, highPriority, true);

		return (task);
	}

	private static class ASImpl implements DHTControlActivity.ActivityState {

		private final ANImpl	root;
		private int				depth 	= -1;
		private final String			result;

		private ASImpl(
			ANImpl	_root,
			String	_result) {
			root 	= _root;
			result	= _result;
		}

		public ActivityNode
		getRootNode() {
			return (root);
		}

		public String getResult() {
			return (result);
		}

		public int getDepth() {
			if (depth == -1) {

				depth = root.getMaxDepth() - 1;

				if (depth == 0) {

					depth = 1;
				}
			}

			return (depth);
		}

		public String getString() {
			return ( root.getString());
		}
	}

	private static class
	ANImpl
		implements DHTControlActivity.ActivityNode
	{
		private final DHTTransportContact		contact;
		private final List<ActivityNode>		kids = new ArrayList<ActivityNode>();

		private ANImpl(
			DHTTransportContact	_contact) {
			contact = _contact;
		}

		public DHTTransportContact
		getContact() {
			return (contact);
		}

		public List<ActivityNode>
		getChildren() {
			return (kids);
		}

		private int getMaxDepth() {
			int	max = 0;

			for (ActivityNode n: kids) {

				max = Math.max( max, ((ANImpl)n).getMaxDepth());
			}

			return (max + 1);
		}

		private void add(
			ActivityNode	n) {
			kids.add(n);
		}
		private String getString() {
			String str = "";

			if (kids.size() > 0) {

				for (ActivityNode k: kids) {

					ANImpl a = (ANImpl)k;

					str += (str.length()==0?"":",") + a.getString();
				}

				str = "["  + str + "]";
			}

			return (AddressUtils.getHostAddress( contact.getAddress()) + " - " + str);
		}
	}

	// Request methods
	public void	pingRequest(DHTTransportContact originatingContact) {
		if (DHTLog.isOn()) {
			DHTLog.log("pingRequest from " + DHTLog.getString(originatingContact.getID()));
		}
		router.contactAlive( originatingContact.getID(), new DHTControlContactImpl(originatingContact));
	}

	public void	keyBlockRequest(
		DHTTransportContact originating_contact,
		byte[]				request,
		byte[]				sig) {
		if (DHTLog.isOn()) {
			DHTLog.log("keyBlockRequest from " + DHTLog.getString( originating_contact.getID()));
		}
		router.contactAlive( originating_contact.getID(), new DHTControlContactImpl(originating_contact));
		database.keyBlockRequest(originating_contact, request, sig);
	}

	public DHTTransportStoreReply storeRequest(
		DHTTransportContact 	originating_contact,
		byte[][]				keys,
		DHTTransportValue[][]	value_sets) {
		
		byte[] originator_id = originating_contact.getID();
		router.contactAlive( originator_id, new DHTControlContactImpl(originating_contact));
		if (DHTLog.isOn()) {
			DHTLog.log("storeRequest from " + DHTLog.getString( originating_contact )+ ", keys = " + keys.length);
		}
		
		byte[]	diverse_res = new byte[keys.length];
		Arrays.fill(diverse_res, DHT.DT_NONE);
		if (keys.length != value_sets.length) {
			Debug.out("DHTControl:storeRequest - invalid request received from " + originating_contact.getString() + ", keys and values length mismatch");
			return (new DHTTransportStoreReplyImpl(diverse_res));
		}
		DHTStorageBlock	blocked_details	= null;
		// System.out.println("storeRequest: received " + originating_contact.getRandomID() + " from " + originating_contact.getAddress());
		//System.out.println("store request: keys=" + keys.length);
		if (keys.length > 0) {
			boolean	cache_forward = false;
			for (DHTTransportValue[] values: value_sets) {
				for (DHTTransportValue value: values) {
					if (!Arrays.equals( originator_id, value.getOriginator().getID())) {
						cache_forward	= true;
						break;
					}
				}
				if (cache_forward) {
					break;
				}
			}
			// don't start accepting cache forwards until we have a good idea of our
			// acceptable key space
			if (cache_forward && !isSeeded()) {
				//System.out.println("not seeded");
				if (DHTLog.isOn()) {
					DHTLog.log("Not storing keys as not yet seeded");
				}
			} else if (!verifyContact( originating_contact, !cache_forward)) {
				//System.out.println("verification fail");
				logger.log("Verification of contact '" + originating_contact.getName() + "' failed for store operation");
			} else {
				// get the closest contacts to me
				byte[]	my_id	= localContact.getID();
				int	c_factor = router.getK();
				DHTStorageAdapter sad = adapter.getStorageAdapter();
				if (sad != null && sad.getNetwork() != DHT.NW_CVS) {
					c_factor += (c_factor/2);
				}
				boolean store_it = true;
				if (cache_forward) {
					long	now = SystemTime.getMonotonousTime();
					if (now - rbsTime < 10*1000 && Arrays.equals( originator_id, rbsId)) {
						// System.out.println("contact too far away - repeat");
						store_it = false;
					} else {
						// make sure the originator is in our group
						List<DHTTransportContact>closest_contacts = getClosestContactsList(my_id, c_factor, true);
						DHTTransportContact	furthest = closest_contacts.get( closest_contacts.size()-1);
						if (computeAndCompareDistances(furthest.getID(), originator_id, my_id) < 0) {
							rbsId 		= originator_id;
							rbsTime	= now;
							// System.out.println("contact too far away");
							if (DHTLog.isOn()) {
								DHTLog.log("Not storing keys as cache forward and sender too far away");
							}
							store_it	= false;
						}
					}
				}
				if (store_it) {
					for (int i=0;i<keys.length;i++) {
						byte[]			key = keys[i];
						HashWrapper		hw_key		= new HashWrapper(key);
						DHTTransportValue[]	values 	= value_sets[i];
						if (DHTLog.isOn()) {
							DHTLog.log("    key=" + DHTLog.getString(key) + ", value=" + DHTLog.getString(values));
						}
						// make sure the key isn't too far away from us
						if (	!( 	database.hasKey( hw_key) ||
									isIDInClosestContacts(my_id, key, c_factor, true))) {
							// System.out.println("key too far away");
							if (DHTLog.isOn()) {
								DHTLog.log("Not storing keys as cache forward and sender too far away");
							}
						} else {
							diverse_res[i] = database.store(originating_contact, hw_key, values);
							if (blocked_details == null) {
								blocked_details = database.getKeyBlockDetails(key);
							}
						}
					}
				}
			}
		}
		
		// fortunately we can get away with this as diversifications are only taken note of by initial, single value stores
		// and not by the multi-value cache forwards...
		if (blocked_details == null) {
			return (new DHTTransportStoreReplyImpl(diverse_res));
		} else {
			return (new DHTTransportStoreReplyImpl(blocked_details.getRequest(), blocked_details.getCertificate()));
		}
	}

	public DHTTransportQueryStoreReply
	queryStoreRequest(
		DHTTransportContact 		originating_contact,
		int							header_len,
		List<Object[]>				keys) {
		router.contactAlive( originating_contact.getID(), new DHTControlContactImpl(originating_contact));

		if (DHTLog.isOn()) {

			DHTLog.log("queryStoreRequest from " + DHTLog.getString( originating_contact)+ ", header_len=" + header_len + ", keys=" + keys.size());
		}

		if (originating_contact.getRandomIDType() == DHTTransportContact.RANDOM_ID_TYPE1) {

			int	rand = generateSpoofID(originating_contact);

			originating_contact.setRandomID(rand);

		} else {

			byte[]	rand = generateSpoofID2(originating_contact);

			originating_contact.setRandomID2(rand);
		}

		return (database.queryStore( originating_contact, header_len, keys));
	}

	public DHTTransportContact[]
	findNodeRequest(
   		DHTTransportContact originating_contact,
   		byte[]				id) {
		return (findNodeRequest( originating_contact, id, false));
	}

	private DHTTransportContact[]
	findNodeRequest(
		DHTTransportContact originating_contact,
		byte[]				id,
		boolean				already_logged) {
		if (!already_logged && DHTLog.isOn()) {
			DHTLog.log("findNodeRequest from " + DHTLog.getString( originating_contact.getID()));
		}

		router.contactAlive( originating_contact.getID(), new DHTControlContactImpl(originating_contact));

		List	l;

		if (id.length == router.getID().length) {

			l = getClosestKContactsList(id, true);	// parg: switched to live-only to reduce client lookup steps 2013/02/06

		} else {

				// this helps both protect against idiot queries and also saved bytes when we use findNode
				// to just get a random ID prior to cache-forwards

			l = new ArrayList();
		}

		final DHTTransportContact[]	res = new DHTTransportContact[l.size()];

		l.toArray(res);

		if (originating_contact.getRandomIDType() == DHTTransportContact.RANDOM_ID_TYPE1) {

			int	rand = generateSpoofID(originating_contact);

			originating_contact.setRandomID(rand);

		} else {

			byte[]	rand = generateSpoofID2(originating_contact);

			originating_contact.setRandomID2(rand);
		}

		return (res);
	}

	public DHTTransportFindValueReply
	findValueRequest(
		DHTTransportContact originating_contact,
		byte[]				key,
		int					max_values,
		short				flags) {
		if (DHTLog.isOn()) {
			DHTLog.log("findValueRequest from " + DHTLog.getString( originating_contact.getID()));
		}

		DHTDBLookupResult	result	= database.get(originating_contact, new HashWrapper( key ), max_values, flags, true);

		if (result != null) {

			if (originating_contact.getRandomIDType() == DHTTransportContact.RANDOM_ID_TYPE2) {

				byte[]	rand = generateSpoofID2(originating_contact);

				originating_contact.setRandomID2(rand);
			}

			router.contactAlive( originating_contact.getID(), new DHTControlContactImpl(originating_contact));

			DHTStorageBlock	block_details = database.getKeyBlockDetails(key);

			if (block_details == null) {

				return (new DHTTransportFindValueReplyImpl( result.getDiversificationType(), result.getValues()));

			} else {

				return (new DHTTransportFindValueReplyImpl( block_details.getRequest(), block_details.getCertificate()));

			}
		} else {

			return (new DHTTransportFindValueReplyImpl( findNodeRequest( originating_contact, key, true)));
		}
	}

	public DHTTransportFullStats
	statsRequest(
		DHTTransportContact	contact) {
		return (stats);
	}

	protected void requestPing(DHTRouterContact contact) {
		((DHTControlContact)contact.getAttachment()).getTransportContact().sendPing(
			new DHTTransportReplyHandlerAdapter() {
				public void pingReply(DHTTransportContact _contact) {
					if (DHTLog.isOn()) {
						DHTLog.log("ping OK " + DHTLog.getString( _contact));
					}
					router.contactAlive(_contact.getID(), new DHTControlContactImpl(_contact));
				}
				
				public void failed(DHTTransportContact _contact, Throwable _error) {
					if (DHTLog.isOn()) {
						DHTLog.log("ping " + DHTLog.getString(_contact) + " -> failed: " + _error.getMessage());
					}
					router.contactDead(_contact.getID(), false);
				}
			}
		);
	}

	protected void nodeAddedToRouter(DHTRouterContact newContact) {
		
		if (DISABLE_REPLICATE_ON_JOIN) {
			if (!newContact.hasBeenAlive()) {
				requestPing(newContact);
			}
			return;
		}
		
		// ignore ourselves
		if (router.isID(newContact.getID()))
			return;
		
		// when a new node is added we must check to see if we need to transfer
		// any of our values to it.
		Map	keysToStore	= new HashMap();
		DHTStorageBlock[]	directKeyBlocks = database.getDirectKeyBlocks();
		if (database.isEmpty() && directKeyBlocks.length == 0) {
			// nothing to do, ping it if it isn't known to be alive
			if (!newContact.hasBeenAlive()) {
				requestPing(newContact);
			}
			return;
		}
		
		// see if we're one of the K closest to the new node
		// optimise to avoid calculating for things obviously too far away
		boolean	performClosenessCheck = true;
		byte[] routerId 	= router.getID();
		byte[] contactId	= newContact.getID();
		byte[] distance = computeDistance(routerId, contactId);
		long now = SystemTime.getCurrentTime();
		byte[] nacul = nodeAddCheckUninterestingLimit;
		
		// time limit to pick up router changes caused by contacts being deleted
		if (now - lastNodeAddCheck < 30*1000 && nacul != null) {
			int res = compareDistances(nacul, distance);
			/*
			System.out.println(
				"r=" + ByteFormatter.encodeString(router_id) +
				",c=" +	ByteFormatter.encodeString(contact_id) +
				",d=" + ByteFormatter.encodeString(distance) +
				",l=" + ByteFormatter.encodeString(nacul) +
				",r=" + res);
			*/
			if (res < 0) {
				performClosenessCheck = false;
			}
		} else {
			lastNodeAddCheck				= now;
			nodeAddCheckUninterestingLimit 	= nacul = null;
		}
		
		boolean	close	= false;
		if (performClosenessCheck) {
			List	closestContacts = getClosestKContactsList(newContact.getID(), false);
			for (int i=0;i<closestContacts.size();i++) {
				if (router.isID(((DHTTransportContact)closestContacts.get(i)).getID())) {
					close	= true;
					break;
				}
			}
			if (!close) {
				if (nacul == null) {
					nodeAddCheckUninterestingLimit = distance;
				} else {
					if (compareDistances(nacul, distance) > 0) {
						nodeAddCheckUninterestingLimit = distance;
					}
				}
			}
		}
		
		if (!close) {
			if (!newContact.hasBeenAlive()) {
				requestPing(newContact);
			}
			return;
		}
		// System.out.println("Node added to router: id=" + ByteFormatter.encodeString(contact_id));
		
		// ok, we're close enough to worry about transferring values
		Iterator	it = database.getKeys();
		while (it.hasNext()) {
			HashWrapper	key		= (HashWrapper)it.next();
			byte[]	encodedKey	= key.getHash();
			if (database.isKeyBlocked(encodedKey)) {
				continue;
			}
			DHTDBLookupResult	result = database.get(null, key, 0, (byte)0, false);
			if (result == null) {
				// deleted in the meantime
				continue;
			}
			
			// even if a result has been diversified we continue to maintain the base value set
			// until the original publisher picks up the diversification (next publish period) and
			// publishes to the correct place
			DHTDBValue[]	values = result.getValues();
			if (values.length == 0) {
				continue;
			}
			
			// we don't consider any cached further away than the initial location, for transfer
			// however, we *do* include ones we originate as, if we're the closest, we have to
			// take responsibility for xfer (as others won't)
			List		sorted_contacts	= getClosestKContactsList(encodedKey, false);
			// if we're closest to the key, or the new node is closest and
			// we're second closest, then we take responsibility for storing
			// the value
			boolean	store_it	= false;
			if (sorted_contacts.size() > 0) {
				DHTTransportContact	first = (DHTTransportContact)sorted_contacts.get(0);
				if (router.isID(first.getID())) {
					store_it = true;
				} else if (Arrays.equals(first.getID(), newContact.getID()) && sorted_contacts.size() > 1) {
					store_it = router.isID(((DHTTransportContact)sorted_contacts.get(1)).getID());
				}
			}
			if (store_it) {
				List	values_to_store = new ArrayList(values.length);
				Collections.addAll(values_to_store, values);
				keysToStore.put(key, values_to_store);
			}
		}
		
		final DHTTransportContact	t_contact = ((DHTControlContact)newContact.getAttachment()).getTransportContact();
		final boolean[]	anti_spoof_done	= { false };
		if (keysToStore.size() > 0) {
			it = keysToStore.entrySet().iterator();
			final byte[][]				keys 		= new byte[keysToStore.size()][];
			final DHTTransportValue[][]	value_sets 	= new DHTTransportValue[keys.length][];
			int		index = 0;
			while (it.hasNext()) {
				Map.Entry	entry = (Map.Entry)it.next();
				HashWrapper	key		= (HashWrapper)entry.getKey();
				List		values	= (List)entry.getValue();
				keys[index] 		= key.getHash();
				value_sets[index]	= new DHTTransportValue[values.size()];

				for (int i=0;i<values.size();i++) {
					value_sets[index][i] = ((DHTDBValue)values.get(i)).getValueForRelay(localContact);
				}
				index++;
			}
			
			// move to anti-spoof for cache forwards. we gotta do a findNode to update the
			// contact's latest random id
			t_contact.sendFindNode(
					new DHTTransportReplyHandlerAdapter() {
						
						public void findNodeReply(
							DHTTransportContact 	contact,
							DHTTransportContact[]	contacts) {
							// System.out.println("nodeAdded: pre-store findNode OK");
							anti_spoof_done[0]	= true;
							t_contact.sendStore(
								new DHTTransportReplyHandlerAdapter() {
									
									public void storeReply(
										DHTTransportContact _contact,
										byte[]				_diversifications) {
										// System.out.println("nodeAdded: store OK");
										
										// don't consider diversifications for node additions as they're not interested
										// in getting values from us, they need to get them from nodes 'near' to the
										// diversification targets or the originator
										if (DHTLog.isOn()) {
											DHTLog.log("add store ok");
										}
										router.contactAlive(_contact.getID(), new DHTControlContactImpl(_contact));
									}
									
									public void failed(
										DHTTransportContact 	_contact,
										Throwable				_error) {
										// System.out.println("nodeAdded: store Failed");
										if (DHTLog.isOn()) {
											DHTLog.log("add store failed " + DHTLog.getString( _contact) + " -> failed: " + _error.getMessage());
										}
										router.contactDead(_contact.getID(), false);
									}
									
									public void keyBlockRequest(
										DHTTransportContact		contact,
										byte[]					request,
										byte[]					signature) {
										database.keyBlockRequest(null, request, signature);
									}
								},
								keys,
								value_sets,
								false
							);
						}
						
						public void failed(
							DHTTransportContact 	_contact,
							Throwable				_error) {
							// System.out.println("nodeAdded: pre-store findNode Failed");
							if (DHTLog.isOn()) {
								DHTLog.log("pre-store findNode failed " + DHTLog.getString( _contact) + " -> failed: " + _error.getMessage());
							}
							router.contactDead( _contact.getID(), false);
						}
					},
					t_contact.getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_ANTI_SPOOF2?new byte[0]:new byte[20],
					DHT.FLAG_LOOKUP_FOR_STORE);
		} else {
			if (!newContact.hasBeenAlive()) {
				requestPing(newContact);
			}
		}
		
		// finally transfer any key-blocks
		if (t_contact.getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_BLOCK_KEYS) {
			for (int i=0;i<directKeyBlocks.length;i++) {
				final DHTStorageBlock	keyBlock = directKeyBlocks[i];
				List	contacts = getClosestKContactsList(keyBlock.getKey(), false);
				boolean	forward_it = false;
				
				// ensure that the key is close enough to us
				for (int j=0;j<contacts.size();j++) {
					final DHTTransportContact	contact = (DHTTransportContact)contacts.get(j);
					if (router.isID(contact.getID())) {
						forward_it	= true;
						break;
					}
				}
				
				if (!forward_it || keyBlock.hasBeenSentTo( t_contact)) {
					continue;
				}
				
				final Runnable task =
					new Runnable() {
						public void run() {
							t_contact.sendKeyBlock(
									
								new DHTTransportReplyHandlerAdapter() {
									
									public void keyBlockReply(DHTTransportContact _contact) {
										if (DHTLog.isOn()) {
											DHTLog.log("key block forward ok " + DHTLog.getString( _contact));
										}
										keyBlock.sentTo(_contact);
									}
									
									public void failed(
										DHTTransportContact 	_contact,
										Throwable				_error) {
										if (DHTLog.isOn()) {
											DHTLog.log("key block forward failed " + DHTLog.getString( _contact) + " -> failed: " + _error.getMessage());
										}
									}
								},
								keyBlock.getRequest(),
								keyBlock.getCertificate()
							);
						}
					};
				if (anti_spoof_done[0]) {
					task.run();
				} else {
					t_contact.sendFindNode(
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
								if (DHTLog.isOn()) {
									DHTLog.log("pre-kb findNode failed " + DHTLog.getString( _contact) + " -> failed: " + _error.getMessage());
								}
								router.contactDead(_contact.getID(), false);
							}
						},
						t_contact.getProtocolVersion() >= DHTTransportUDP.PROTOCOL_VERSION_ANTI_SPOOF2?new byte[0]:new byte[20],
						DHT.FLAG_LOOKUP_FOR_STORE
					);
				}
			}
		}
	}

	protected Set<DHTTransportContact> getClosestContactsSet(
		byte[]		id,
		int			numToReturn,
		boolean		liveOnly) {
		List<DHTRouterContact> l = router.findClosestContacts(id, numToReturn, liveOnly);
		Set<DHTTransportContact> sortedSet = new SortedTransportContactSet(id, true).getSet();
		
		// profilers says l.size() is taking CPU (!) so put it into a variable
		// this is safe since the list returned is created for us only
		long size = l.size();
		//Log.d(TAG, "size = " + size);
		for (int i=0;i<size;i++) {
			sortedSet.add(
					( 
							(DHTControlContact)(l.get(i)).getAttachment() 
					).getTransportContact()
			);
		}
		return (sortedSet);
	}

	public List<DHTTransportContact> getClosestKContactsList(
		byte[]		id,
		boolean		liveOnly) {
		return (getClosestContactsList(id, K, liveOnly));
	}

	public List<DHTTransportContact> getClosestContactsList(
		byte[]		id,
		int			numToReturn,
		boolean		liveOnly) {
		Set<DHTTransportContact> sortedSet = getClosestContactsSet(id, numToReturn, liveOnly);
		List<DHTTransportContact> res = new ArrayList<DHTTransportContact>(numToReturn);
		Iterator<DHTTransportContact> it = sortedSet.iterator();
		while (it.hasNext() && res.size() < numToReturn) {
			res.add(it.next());
		}
		return (res);
	}

	protected boolean isIDInClosestContacts(
		byte[]		testId,
		byte[]		targetId,
		int			numToConsider,
		boolean		liveOnly) {
		List<DHTRouterContact> l = router.findClosestContacts(targetId, numToConsider, liveOnly);
		boolean	found		= false;
		int		num_closer 	= 0;
		for (DHTRouterContact c: l) {
			byte[] c_id = c.getID();
			if (Arrays.equals(testId, c_id)) {
				found = true;
			} else {
				if (computeAndCompareDistances(c_id, testId, targetId) < 0) {
					num_closer++;
				}
			}
		}
		return (found && num_closer < numToConsider);
	}

	protected byte[] encodeKey(byte[] key) {
		if (encodeKeys) {
			byte[] temp = new SHA1Simple().calculateHash(key);
			byte[] result = new byte[nodeIdByteCount];
			System.arraycopy(temp, 0, result, 0, nodeIdByteCount);
			return (result);
		} else {
			if (key.length == nodeIdByteCount) {
				return (key);
			} else {
				byte[] result = new byte[nodeIdByteCount];
				System.arraycopy(key, 0, result, 0, Math.min(nodeIdByteCount, key.length));
				return (result);
			}
		}
	}

	public int computeAndCompareDistances(byte[] t1, byte[] t2, byte[] pivot) {
		return (computeAndCompareDistances2(t1, t2, pivot));
	}

	protected static int computeAndCompareDistances2(byte[] t1, byte[] t2, byte[] pivot) {

		for (int i = 0; i < t1.length; i++) {
			byte d1 = (byte) (t1[i] ^ pivot[i]);
			byte d2 = (byte) (t2[i] ^ pivot[i]);
			int diff = (d1 & 0xff) - (d2 & 0xff);
			if (diff != 0) {
				return (diff);
			}
		}
		return (0);
	}

	public byte[] computeDistance(byte[] n1, byte[] n2) {
		return (computeDistance2(n1, n2));
	}

	protected static byte[] computeDistance2(byte[] n1, byte[] n2) {
		byte[] res = new byte[n1.length];
		for (int i = 0; i < res.length; i++) {
			res[i] = (byte) (n1[i] ^ n2[i]);
		}
		return (res);
	}

	/**
	 * -ve -> n1 < n2
	 * @param n1
	 * @param n2
	 * @return
	 */
	public int compareDistances(byte[] n1, byte[] n2) {
		return (compareDistances2(n1, n2));
	}

	protected static int compareDistances2(byte[] n1, byte[] n2) {
		for (int i=0;i<n1.length;i++) {
			int diff = (n1[i]&0xff) - (n2[i]&0xff);
			if (diff != 0) {
				return (diff);
			}
		}
		return (0);
	}

	public void	addListener(DHTControlListener l) {
		try {
			activityMonitor.enter();
			listeners.addListener(l);
			for (int i=0;i<activities.size();i++) {
				listeners.dispatch( DHTControlListener.CT_ADDED, activities.get(i));
			}
		} finally {
			activityMonitor.exit();
		}
	}

	public void removeListener(DHTControlListener l) {
		listeners.removeListener(l);
	}

	public DHTControlActivity[]	getActivities() {
		List res;
		try {
			activityMonitor.enter();
			res = new ArrayList(activities);
		} finally {
			activityMonitor.exit();
		}
		DHTControlActivity[] x = new DHTControlActivity[res.size()];
		res.toArray(x);
		return (x);
	}

	public void	setTransportEstimatedDHTSize(int size) {
		if (size > 0) {
			try {
				estimateMon.enter();
				remoteEstimateValues.add(new Integer( size));
				if (remoteEstimateValues.size() > REMOTE_ESTIMATE_HISTORY) {
					remoteEstimateValues.remove(0);
				}
			} finally {
				estimateMon.exit();
			}
		}
		// System.out.println("estimated dht size: " + size);
	}

	public int getTransportEstimatedDHTSize() {
		return ((int)localDhtEstimate);
	}

	public int getEstimatedDHTSize() {
		// public method, trigger actual computation periodically
		long now = SystemTime.getCurrentTime();
		long diff = now - lastDhtEstimateTime;
		if (diff < 0 || diff > 60*1000) {
			estimateDHTSize(router.getID(), null, router.getK());
		}

		// with recent changes we pretty much have a router that only contains routeable contacts
		// therefore the apparent size of the DHT is less than real and we need to adjust by the
		// routeable percentage to get an accurate figure
		int	percent = transport.getStats().getRouteablePercentage();

		// current assumption is that around 50% are firewalled, so if less (at least during migration) assume unusable
		if (percent < 25) {
			return ((int)combinedDhtEstimate);
		}
		double mult = 100.0 / percent;
		return ((int)(mult * combinedDhtEstimate));
	}

	protected void estimateDHTSize(
		byte[]							id,
		List<DHTTransportContact>		contacts,
		int								contactsToUse) {

		// if called with contacts then this is in internal estimation based on lookup values
		long now = SystemTime.getCurrentTime();
		long diff = now - lastDhtEstimateTime;

		// 5 second limiter here
		if (diff < 0 || diff > 5*1000) {
			try {
				estimateMon.enter();
				lastDhtEstimateTime	= now;
				List	l;
				if (contacts == null) {
					l = getClosestKContactsList(id, false);
				} else {
					Set	sortedSet	= new SortedTransportContactSet(id, true).getSet();
					sortedSet.addAll(contacts);
					l = new ArrayList(sortedSet);
					if (l.size() > 0) {
						// algorithm works relative to a starting point in the ID space so we grab
						// the first here rather than using the initial lookup target
						id = ((DHTTransportContact)l.get(0)).getID();
					}
					/*
					String	str = "";
					for (int i=0;i<l.size();i++) {
						str += (i==0?"":",") + DHTLog.getString2( ((DHTTransportContact)l.get(i)).getID());
					}
					System.out.println("trace: " + str);
					*/
				}
				// can't estimate with less than 2
				if (l.size() > 2) {

					/*
					<Gudy> if you call N0 yourself, N1 the nearest peer, N2 the 2nd nearest peer ... Np the pth nearest peer that you know (for example, N1 .. N20)
					<Gudy> and if you call D1 the Kad distance between you and N1, D2 between you and N2 ...
					<Gudy> then you have to compute :
					<Gudy> Dc = sum(i * Di) / sum(i * i)
					<Gudy> and then :
					<Gudy> NbPeers = 2^160 / Dc
					*/
					BigInteger	sum1 = new BigInteger("0");
					BigInteger	sum2 = new BigInteger("0");
					// first entry should be us
					for (int i=1;i<Math.min(l.size(), contactsToUse);i++) {
						DHTTransportContact	node = (DHTTransportContact)l.get(i);
						byte[]	dist = computeDistance(id, node.getID());
						BigInteger b_dist = IDToBigInteger(dist);
						BigInteger	b_i = new BigInteger(""+i);
						sum1 = sum1.add(b_i.multiply(b_dist));
						sum2 = sum2.add(b_i.multiply(b_i));
					}
					byte[]	max = new byte[id.length+1];
					max[0] = 0x01;
					long thisEstimate;
					if (sum1.compareTo(new BigInteger("0")) == 0) {
						thisEstimate = 0;
					} else {
						thisEstimate = IDToBigInteger(max).multiply(sum2).divide(sum1).longValue();
					}
					// there's always us!!!!
					if (thisEstimate < 1) {
						thisEstimate	= 1;
					}
					localEstimateValues.put(new HashWrapper(id), new Long(thisEstimate));
					long	newEstimate	= 0;
					Iterator	it = localEstimateValues.values().iterator();
					String	sizes = "";
					while (it.hasNext()) {
						long	estimate = ((Long)it.next()).longValue();
						sizes += (sizes.length()==0?"":",") + estimate;
						newEstimate += estimate;
					}
					localDhtEstimate = newEstimate/localEstimateValues.size();
					// System.out.println("getEstimatedDHTSize: " + sizes + "->" + dht_estimate + " (id=" + DHTLog.getString2(id) + ",cont=" + (contacts==null?"null":(""+contacts.size())) + ",use=" + contacts_to_use);
				}
				List rems = new ArrayList(new TreeSet(remoteEstimateValues));
				// ignore largest and smallest few values
				long	remAverage = localDhtEstimate;
				int		remVals	= 1;
				for (int i=3;i<rems.size()-3;i++) {
					remAverage += ((Integer)rems.get(i)).intValue();
					remVals++;
				}
				long[] routerStats = router.getStats().getStats();
				long routerContacts = routerStats[DHTRouterStats.ST_CONTACTS] + routerStats[DHTRouterStats.ST_REPLACEMENTS];
				combinedDhtEstimate = Math.max(remAverage/remVals, routerContacts);
				long	test_val 	= 10;
				int		test_mag	= 1;
				while (test_val < combinedDhtEstimate) {
					test_val *= 10;
					test_mag++;
				}
				combinedDhtEstimateMag = test_mag+1;
				// System.out.println("estimateDHTSize: loc =" + local_dht_estimate + ", comb = " + combined_dht_estimate + " [" + remote_estimate_values.size() + "]");
			} finally {
				estimateMon.exit();
			}
		}
	}

	protected BigInteger IDToBigInteger(byte[] data) {
		StringBuilder	strKey = new StringBuilder(data.length*2);
		for (int i=0;i<data.length;i++) {
			String	hex = Integer.toHexString(data[i]&0xff);
			if (hex.length() < 2) {
				strKey.append("0");
			}
			strKey.append(hex);
		}
		BigInteger	res		= new BigInteger(strKey.toString(), 16);
		return (res);
	}

	private int generateSpoofID(
		DHTTransportContact	contact) {
		if (spoofDigest == null) {
			return (0);
		}
		InetAddress iad = contact.getAddress().getAddress();
		try {
			spoofMon.enter();
				// during cache forwarding we get a lot of consecutive requests from the
				// same contact so we can save CPU by caching the latest result and optimising for this
			Integer existing = spoofGenHistory.get(iad);
			if (existing != null) {
				//System.out.println("anti-spoof: cached " + existing + " for " + contact.getAddress() + " - total=" + spoof_gen_history.size());
				return (existing);
			}
			byte[]	address = iad.getAddress();
			//spoof_cipher.init(Cipher.ENCRYPT_MODE, spoof_key);
			//byte[]	data_out = spoof_cipher.doFinal(address);
			byte[]	data_in = spoofKey.clone();
			for ( int i=0;i<address.length;i++) {
				data_in[i] ^= address[i];
			}
			byte[] data_out = spoofDigest.digest(data_in);
			int	res =  	(data_out[0]<<24)&0xff000000 |
						(data_out[1] << 16)&0x00ff0000 |
						(data_out[2] << 8)&0x0000ff00 |
						data_out[3]&0x000000ff;
			//System.out.println("anti-spoof: generating " + res + " for " + contact.getAddress() + " - total=" + spoof_gen_history.size());
			spoofGenHistory.put(iad, res);
			return (res);
		} catch (Throwable e) {
			logger.log(e);
		} finally {
			spoofMon.exit();
		}
		return (0);
	}

	private byte[]
	generateSpoofID2(
		DHTTransportContact	contact) {
		if (spoofDigest == null) {
			return (new byte[ SPOOF_ID2_SIZE ]);
		}
		HashWrapper cid = new HashWrapper( contact.getID());
		try {
			spoofMon.enter();
				// during cache forwarding we get a lot of consecutive requests from the
				// same contact so we can save CPU by caching the latest result and optimising for this
			byte[] existing = spoofGenHistory2.get(cid);
			if (existing != null) {
				//System.out.println("anti-spoof: cached " + existing + " for " + contact.getAddress() + " - total=" + spoof_gen_history.size());
				return (existing);
			}
			// spoof_cipher.init(Cipher.ENCRYPT_MODE, spoof_key);
			// byte[]	data_out = spoof_cipher.doFinal( cid.getBytes());
			byte[] 	cid_bytes 	= cid.getBytes();
			byte[]	data_in 	= spoofKey.clone();
			int	byte_count = Math.min(cid_bytes.length, data_in.length);
			for ( int i=0;i<byte_count;i++) {
				data_in[i] ^= cid_bytes[i];
			}
			byte[] data_out = spoofDigest.digest(data_in);
			byte[] res = new byte[SPOOF_ID2_SIZE];
			System.arraycopy(data_out, 0, res, 0, SPOOF_ID2_SIZE);
			//System.out.println("anti-spoof: generating " + res + " for " + contact.getAddress() + " - total=" + spoof_gen_history.size());
			spoofGenHistory2.put(cid, res);
			return (res);
		} catch (Throwable e) {
			logger.log(e);
		} finally {
			spoofMon.exit();
		}
		return (new byte[ SPOOF_ID2_SIZE ]);
	}

	public boolean verifyContact(
		DHTTransportContact 	c,
		boolean					direct) {
		boolean	ok;
		if (c.getRandomIDType() == DHTTransportContact.RANDOM_ID_TYPE1) {
			ok = c.getRandomID() == generateSpoofID(c);
		} else {
			byte[]	r1 = c.getRandomID2();
			byte[]	r2 = generateSpoofID2(c);
			if (r1 == null && r2 == null) {
				ok = true;
			} else if (r1 == null || r2 == null) {
				ok = false;
			} else {
				ok = Arrays.equals(r1, r2);
			}
		}
		if (DHTLog.CONTACT_VERIFY_TRACE) {
			System.out.println("    net " + transport.getNetwork() +"," + (direct?"direct":"indirect") + " verify for " + c.getName() + " -> " + ok + ", version = " + c.getProtocolVersion());
		}
		return (ok);
	}

	public List	getContacts() {
		List contacts = router.getAllContacts();
		List res = new ArrayList(contacts.size());
		for (int i=0;i<contacts.size();i++) {
			DHTRouterContact rc = (DHTRouterContact)contacts.get(i);
			res.add(rc.getAttachment());
		}
		return (res);
	}

	public void pingAll() {
		
		Log.d(TAG, "pingAll() is called...");
		Throwable t = new Throwable();
		t.printStackTrace();
		
		List contacts = router.getAllContacts();
		final AESemaphore sem = new AESemaphore("pingAll", 32);
		final int[]	results = {0,0};
		for (int i=0;i<contacts.size();i++) {
			sem.reserve();
			DHTRouterContact rc = (DHTRouterContact)contacts.get(i);
			((DHTControlContact)rc.getAttachment()).getTransportContact().sendPing(
					new DHTTransportReplyHandlerAdapter() {
						
						public void pingReply(DHTTransportContact _contact) {
							results[0]++;
							print();
							sem.release();
						}
						
						public void failed(
							DHTTransportContact 	_contact,
							Throwable				_error) {
							results[1]++;
							print();
							sem.release();
						}
						
						protected void print() {
							System.out.println("ok=" + results[0] + ",bad=" + results[1]);
						}
					});
		}
	}

	public void destroy() {
		destroyed = true;
		if (router != null) {
			router.destroy();
		}
		if (database != null) {
			database.destroy();
		}
	}

	public void	print(boolean full) {
		DHTNetworkPosition[]	nps = transport.getLocalContact().getNetworkPositions();
		String	np_str = "";
		for (int j=0;j<nps.length;j++) {
			np_str += (j==0?"":",") + nps[j];
		}
		logger.log("DHT Details: external address=" + transport.getLocalContact().getAddress() +
						", network=" + transport.getNetwork() +
						", protocol=V" + transport.getProtocolVersion() +
						", nps=" + np_str + ", est_size=" + getTransportEstimatedDHTSize());
		//router.print();
		database.print(full);
		/*
		List	c = getContacts();
		for (int i=0;i<c.size();i++) {
			DHTControlContact	cc = (DHTControlContact)c.get(i);
			System.out.println("    " + cc.getTransportContact().getVivaldiPosition());
		}
		*/
	}

	protected static class SortedTransportContactSet {
		
		private final TreeSet<DHTTransportContact>	treeSet;

		final byte[]	pivot;
		final boolean	ascending;

		protected SortedTransportContactSet(
			byte[]		_pivot,
			boolean		_ascending) {
			
			pivot		= _pivot;
			ascending	= _ascending;

			treeSet = new TreeSet<DHTTransportContact>(
				new Comparator<DHTTransportContact>() {
					public int compare(
						DHTTransportContact	t1,
						DHTTransportContact	t2) {
						
						// this comparator ensures that the closest to the key
						// is first in the iterator traversal
						int	distance = computeAndCompareDistances2(t1.getID(), t2.getID(), pivot);
						if (ascending) {
							return (distance);
						} else {
							return (-distance);
						}
					}
				});
		}

		public Set<DHTTransportContact> getSet() {
			return (treeSet);
		}
	}

	protected static class DHTOperationListenerDemuxer
		implements DHTOperationListener {
		
		private final AEMonitor	this_mon = new AEMonitor("DHTOperationListenerDemuxer");

		private final DHTOperationListener	delegate;

		private boolean		complete_fired;
		private boolean		complete_included_ok;

		private int			complete_count	= 0;

		protected DHTOperationListenerDemuxer(DHTOperationListener	_delegate) {
			delegate = _delegate;
			if (delegate == null) {
				Debug.out("invalid: null delegate");
			}
		}

		public void incrementCompletes() {
			try {
				this_mon.enter();
				complete_count++;
			} finally {
				this_mon.exit();
			}
		}

		public void searching(
			DHTTransportContact	contact,
			int					level,
			int					active_searches) {
			delegate.searching(contact, level, active_searches);
		}

		public boolean diversified(String desc) {
			return (delegate.diversified( desc));
		}

		public void found(
			DHTTransportContact	contact,
			boolean				is_closest) {
			delegate.found(contact, is_closest);
		}

		public void read(
			DHTTransportContact	contact,
			DHTTransportValue	value) {
			delegate.read(contact, value);
		}

		public void wrote(
			DHTTransportContact	contact,
			DHTTransportValue	value) {
			delegate.wrote(contact, value);
		}

		public void complete(boolean timeout) {
			boolean	fire	= false;
			try {
				this_mon.enter();
				if (!timeout) {
					complete_included_ok	= true;
				}
				complete_count--;
				if (complete_count <= 0 && !complete_fired) {
					complete_fired	= true;
					fire			= true;
				}
			} finally {
				this_mon.exit();
			}
			if (fire) {
				delegate.complete(!complete_included_ok);
			}
		}
	}

	abstract static class LookupResultHandler extends DHTOperationListenerDemuxer {
		
		protected LookupResultHandler(DHTOperationListener delegate) {
			super(delegate);
		}
		
		public abstract void closest(List res);
		public abstract void diversify(DHTTransportContact cause, byte diversificationType);
		
	}


	protected static class DHTTransportFindValueReplyImpl
		implements DHTTransportFindValueReply
	{
		private byte					dt = DHT.DT_NONE;
		private DHTTransportValue[]		values;
		private DHTTransportContact[]	contacts;
		private byte[]					blocked_key;
		private byte[]					blocked_sig;

		protected DHTTransportFindValueReplyImpl(
			byte				_dt,
			DHTTransportValue[]	_values) {
			dt		= _dt;
			values	= _values;

			boolean	copied = false;

			for (int i=0;i<values.length;i++) {

				DHTTransportValue	value = values[i];

				if ((value.getFlags() & DHT.FLAG_ANON) != 0) {

					if (!copied) {

						values = new DHTTransportValue[ _values.length ];

						System.arraycopy(_values, 0, values, 0, values.length);

						copied = true;
					}

					values[i] = new anonValue(value);
				}
			}
		}

		protected DHTTransportFindValueReplyImpl(
			DHTTransportContact[]	_contacts) {
			contacts	= _contacts;
		}

		protected DHTTransportFindValueReplyImpl(
			byte[]		_blocked_key,
			byte[]		_blocked_sig) {
			blocked_key	= _blocked_key;
			blocked_sig	= _blocked_sig;
		}

		public byte
		getDiversificationType() {
			return (dt);
		}

		public boolean hit() {
			return (values != null);
		}

		public boolean blocked() {
			return (blocked_key != null);
		}

		public DHTTransportValue[]
		getValues() {
			return (values);
		}

		public DHTTransportContact[]
		getContacts() {
			return (contacts);
		}

		public byte[]
		getBlockedKey() {
			return (blocked_key);
		}

		public byte[]
		getBlockedSignature() {
			return (blocked_sig);
		}
	}

	protected static class
	anonValue
		implements DHTTransportValue
	{
		private final DHTTransportValue delegate;

		protected
		anonValue(
			DHTTransportValue		v) {
			delegate = v;
		}

		public boolean isLocal() {
			return ( delegate.isLocal());
		}

		public long getCreationTime() {
			return ( delegate.getCreationTime());
		}

		public byte[]
		getValue() {
			return ( delegate.getValue());
		}

		public int getVersion() {
			return ( delegate.getVersion());
		}

		public DHTTransportContact
		getOriginator() {
			return (new AnonContact( delegate.getOriginator()));
		}

		public int getFlags() {
			return ( delegate.getFlags());
		}

		public int getLifeTimeHours() {
			return ( delegate.getLifeTimeHours());
		}

		public byte
		getReplicationControl() {
			return ( delegate.getReplicationControl());
		}

		public byte
		getReplicationFactor() {
			return ( delegate.getReplicationFactor());
		}

		public byte
		getReplicationFrequencyHours() {
			return ( delegate.getReplicationFrequencyHours());
		}

		public String getString() {
			return ( delegate.getString());
		}
	}

	protected static class AnonContact implements DHTTransportContact {
		
		private static InetSocketAddress anonAddress;

		static {
			try {
				anonAddress = new InetSocketAddress(InetAddress.getByName("0.0.0.0"), 0);
			} catch (Throwable e) {
				Debug.printStackTrace(e);
			}
		}

		private final DHTTransportContact	delegate;

		protected AnonContact(DHTTransportContact c) {
			delegate = c;
		}

		public int getMaxFailForLiveCount() {
			return (delegate.getMaxFailForLiveCount());
		}

		public int getMaxFailForUnknownCount() {
			return (delegate.getMaxFailForUnknownCount());
		}

		public int getInstanceID() {
			return (delegate.getInstanceID());
		}

		public byte[] getID() {
			Debug.out("hmm");

			return (delegate.getID());
		}

		public byte getProtocolVersion() {
			return (delegate.getProtocolVersion());
		}

		public long getClockSkew() {
			return (delegate.getClockSkew());
		}

		public int getRandomIDType() {
			return (delegate.getRandomIDType());
		}

		public void setRandomID(int id) {
			delegate.setRandomID(id);
		}

		public int getRandomID() {
			return (delegate.getRandomID());
		}

		public void setRandomID2(byte[] id) {
			delegate.setRandomID2(id);
		}

		public byte[] getRandomID2() {
			return (delegate.getRandomID2());
		}

		public String getName() {
			return (delegate.getName());
		}

		public byte[] getBloomKey() {
			return (delegate.getBloomKey());
		}

		public InetSocketAddress getAddress() {
			return (anonAddress);
		}

		public InetSocketAddress getTransportAddress() {
			return (getAddress());
		}

		public InetSocketAddress getExternalAddress() {
			return (getAddress());
		}

		public Map<String, Object> exportContactToMap() {
			return (delegate.exportContactToMap());
		}

		public boolean isAlive(long timeout) {
			return (delegate.isAlive(timeout));
		}

		public void isAlive(DHTTransportReplyHandler handler, long timeout) {
			delegate.isAlive(handler, timeout);
		}

		public boolean isValid() {
			return (delegate.isValid());
		}

		public boolean isSleeping() {
			return (delegate.isSleeping());
		}

		public void sendPing(DHTTransportReplyHandler handler) {
			delegate.sendPing(handler);
		}

		public void sendImmediatePing(DHTTransportReplyHandler handler, long timeout) {
			delegate.sendImmediatePing(handler, timeout);
		}

		public void sendStats(DHTTransportReplyHandler handler) {
			delegate.sendStats(handler);
		}

		public void sendStore(
			DHTTransportReplyHandler	handler,
			byte[][]					keys,
			DHTTransportValue[][]		value_sets,
			boolean						immediate) {
			delegate.sendStore(handler, keys, value_sets, immediate);
		}

		public void sendQueryStore(
			DHTTransportReplyHandler 	handler,
			int							header_length,
			List<Object[]>			 	key_details ) {
			delegate.sendQueryStore(handler, header_length, key_details);
		}

		public void sendFindNode(
			DHTTransportReplyHandler	handler,
			byte[]						id,
			short						flags) {
			delegate.sendFindNode(handler, id, flags);
		}

		public void sendFindValue(
			DHTTransportReplyHandler	handler,
			byte[]						key,
			int							max_values,
			short						flags) {
			delegate.sendFindValue( handler, key, max_values, flags);
		}

		public void sendKeyBlock(
			DHTTransportReplyHandler	handler,
			byte[]						key_block_request,
			byte[]						key_block_signature) {
			delegate.sendKeyBlock(handler, key_block_request, key_block_signature);
		}

		public DHTTransportFullStats getStats() {
			return (delegate.getStats());
		}

		public void exportContact(DataOutputStream os)
				throws IOException, DHTTransportException {
			delegate.exportContact(os);
		}

		public void remove() {
			delegate.remove();
		}

		public void createNetworkPositions(boolean is_local) {
			delegate.createNetworkPositions(is_local);
		}

		public DHTNetworkPosition[] getNetworkPositions() {
			return (delegate.getNetworkPositions());
		}

		public DHTNetworkPosition getNetworkPosition(byte position_type) {
			return (delegate.getNetworkPosition(position_type));
		}

		public DHTTransport getTransport() {
			return (delegate.getTransport());
		}

		public String getString() {
			return (delegate.getString());
		}
	}

	protected static class DHTTransportStoreReplyImpl
		implements DHTTransportStoreReply
	{
		private byte[]	divs;
		private byte[]	blockRequest;
		private byte[]	blockSig;

		protected DHTTransportStoreReplyImpl(byte[] _divs) {
			divs = _divs;
		}

		protected DHTTransportStoreReplyImpl(
			byte[]		_bk,
			byte[]		_bs) {
			blockRequest	= _bk;
			blockSig		= _bs;
		}

		public byte[] getDiversificationTypes() {
			return (divs);
		}

		public boolean blocked() {
			return (blockRequest != null);
		}

		public byte[] getBlockRequest() {
			return (blockRequest);
		}

		public byte[] getBlockSignature() {
			return (blockSig);
		}
	}

	protected abstract class DhtTask extends ThreadPoolTask {
		private final ControlActivity activity;

		protected DhtTask(ThreadPool threadPool) {
			activity = new ControlActivity(threadPool, this);
			try {
				activityMonitor.enter();
				activities.add(activity);
				listeners.dispatch(DHTControlListener.CT_ADDED, activity);
				// System.out.println("activity added:" + activities.size());
			} finally {
				activityMonitor.exit();
			}
		}

		public void taskStarted() {
			listeners.dispatch(DHTControlListener.CT_CHANGED, activity);
			//System.out.println("activity changed:" + activities.size());
		}

		public void taskCompleted() {
			try {
				activityMonitor.enter();
				activities.remove(activity);
				listeners.dispatch(DHTControlListener.CT_REMOVED, activity);
				// System.out.println("activity removed:" + activities.size());
			} finally {
				activityMonitor.exit();
			}
		}

		public void interruptTask() {
		}

		protected abstract void cancel();
		public abstract byte[] getTarget();
		public abstract String getDescription();
		public abstract DHTControlActivity.ActivityState getCurrentState();
	}

	protected static class DhtTaskSet {
		private boolean cancelled;
		private Object	things;
		
		private void add(DhtTask task) {
			synchronized(this) {
				if (cancelled) {
					task.cancel();
					return;
				}
				addToThings(task);
			}
		}
		
		private void add(DhtTaskSet	task_set) {
			synchronized(this) {
				if (cancelled) {
					task_set.cancel();
					return;
				}
				addToThings(task_set);
			}
		}
		
		private void addToThings(Object	obj) {
			if (things == null) {
				things = obj;
			} else {
				if (things instanceof List) {
					((List)things).add(obj);
				} else {
					List	l = new ArrayList(2);
					l.add(things);
					l.add(obj);
					things = l;
				}
			}
		}
		
		private void cancel() {
			Object	toCancel;
			synchronized(this) {
				if (cancelled) {
					return;
				}
				cancelled = true;
				toCancel = things;
				things = null;
			}
			if (toCancel != null) {
				if (toCancel instanceof DhtTask) {
					((DhtTask)toCancel).cancel();
				} else if (toCancel instanceof DhtTaskSet) {
					((DhtTaskSet)toCancel).cancel();
				} else {
					List	l = (List)toCancel;
					for (int i=0;i<l.size();i++) {
						Object o = l.get(i);
						if (o instanceof DhtTask) {
							((DhtTask)o).cancel();
						} else {
							((DhtTaskSet)o).cancel();
						}
					}
				}
			}
		}
		
		private boolean isCancelled() {
			synchronized (this) {
				return (cancelled);
			}
		}
	}

	protected class ControlActivity implements DHTControlActivity {
		
		protected final ThreadPool	tp;
		protected final DhtTask		task;
		protected final int			type;

		protected ControlActivity(
			ThreadPool	_tp,
			DhtTask		_task) {
			tp		= _tp;
			task	= _task;
			if (_tp == internalLookupPool) {
				type	= DHTControlActivity.AT_INTERNAL_GET;
			} else if (_tp == externalLookupPool) {
				type	= DHTControlActivity.AT_EXTERNAL_GET;
			} else if (_tp == internalPutPool) {
				type	= DHTControlActivity.AT_INTERNAL_PUT;
			} else {
				type	= DHTControlActivity.AT_EXTERNAL_PUT;
			}
		}

		public byte[] getTarget() {
			return (task.getTarget());
		}

		public String getDescription() {
			return (task.getDescription());
		}

		public int getType() {
			return (type);
		}

		public boolean isQueued() {
			return (tp.isQueued(task));
		}

		public ActivityState getCurrentState() {
			return (task.getCurrentState());
		}

		public String getString() {
			return (type + ":" + DHTLog.getString( getTarget()) + "/" + getDescription() + ", q = " + isQueued());
		}
	}
}
