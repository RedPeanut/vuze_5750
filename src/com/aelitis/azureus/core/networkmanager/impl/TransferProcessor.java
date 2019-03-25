/*
 * Created on Oct 7, 2004
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

import java.util.*;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.config.ParameterListener;
import org.gudy.azureus2.core3.util.AEMonitor;
import org.gudy.azureus2.core3.util.Debug;

import com.aelitis.azureus.core.networkmanager.LimitedRateGroup;
import com.aelitis.azureus.core.networkmanager.NetworkConnectionBase;
import com.aelitis.azureus.core.networkmanager.NetworkManager;
import com.aelitis.azureus.core.networkmanager.RateHandler;




/**
 *
 */
public class TransferProcessor {
	
	private static final boolean RATE_LIMIT_LAN_TOO	= false;

	private static boolean	RATE_LIMIT_UP_INCLUDES_PROTOCOL 	= false;
	private static boolean	RATE_LIMIT_DOWN_INCLUDES_PROTOCOL 	= false;

	static {
		if (RATE_LIMIT_LAN_TOO) {
			System.err.println("**** TransferProcessor: RATE_LIMIT_LAN_TOO enabled ****");
		}
		
		COConfigurationManager.addAndFireParameterListeners(
				new String[]{
					"Up Rate Limits Include Protocol",
					"Down Rate Limits Include Protocol"
				},
				new ParameterListener() {
					public void parameterChanged(String parameterName) {
						RATE_LIMIT_UP_INCLUDES_PROTOCOL = COConfigurationManager.getBooleanParameter("Up Rate Limits Include Protocol");
						RATE_LIMIT_DOWN_INCLUDES_PROTOCOL = COConfigurationManager.getBooleanParameter("Down Rate Limits Include Protocol");
					}
				}
		);
	}

	public static final int TYPE_UPLOAD	 = 0;
	public static final int TYPE_DOWNLOAD = 1;

	private final int processorType;
	private final LimitedRateGroup maxRate;

	private final RateHandler	mainRateHandler;
	private final ByteBucket 	mainBucket;
	private final EntityHandler mainController;

	private final HashMap<LimitedRateGroup,GroupData> 				groupBuckets 	= new HashMap<LimitedRateGroup,GroupData>();
	private final HashMap<NetworkConnectionBase,ConnectionData> 	connections 	= new HashMap<NetworkConnectionBase,ConnectionData>();

	private final AEMonitor connectionsMon;

	private final boolean	multiThreaded;

	/**
	 * Create new transfer processor for the given read/write type, limited to the given max rate.
	 * @param processorType read or write processor
	 * @param maxRateLimit to use
	 */
	public TransferProcessor(
			final int _processorType, 
			LimitedRateGroup maxRateLimit, 
			boolean multiThreaded) {
		this.processorType = _processorType;
		this.maxRate 		= maxRateLimit;
		this.multiThreaded	= multiThreaded;

		connectionsMon = new AEMonitor("TransferProcessor:" +processorType);

		mainBucket = createBucket(maxRate.getRateLimitBytesPerSecond());

		mainRateHandler =
			new RateHandler() {
				final int pt = _processorType;
				public int[] getCurrentNumBytesAllowed() {
					if (mainBucket.getRate() != maxRate.getRateLimitBytesPerSecond()) { //sync rate
						mainBucket.setRate(maxRate.getRateLimitBytesPerSecond());
					}
					int special;
					if (pt == TYPE_UPLOAD) {
						if (RATE_LIMIT_UP_INCLUDES_PROTOCOL) {
							special = 0;
						} else {
							special = Integer.MAX_VALUE;
						}
					} else {
		 				if (RATE_LIMIT_DOWN_INCLUDES_PROTOCOL) {
							special = 0;
						} else {
							special = Integer.MAX_VALUE;
						}
					}
					return (new int[]{ mainBucket.getAvailableByteCount(), special });
				}
				
				public void bytesProcessed(int dataBytes, int protocolBytes) {
					//System.out.println((pt == TYPE_UPLOAD?"Up":"Down") + ": " + data_bytes + "/" + protocol_bytes);
					int num_bytes_written;
					if (pt == TYPE_UPLOAD) {
						num_bytes_written = RATE_LIMIT_UP_INCLUDES_PROTOCOL?dataBytes + protocolBytes:dataBytes;
					} else {
		 				num_bytes_written = RATE_LIMIT_DOWN_INCLUDES_PROTOCOL?dataBytes + protocolBytes:dataBytes;
					}
					mainBucket.setBytesUsed(num_bytes_written);
					maxRate.updateBytesUsed(num_bytes_written);
				}
			};

		mainController = new EntityHandler(processorType, mainRateHandler);
	}




	/**
	 * Register peer connection for upload handling.
	 * NOTE: The given max rate limit is ignored until the connection is upgraded.
	 * @param connection to register
	 * @param group rate limit group
	 */
	public void registerPeerConnection(NetworkConnectionBase connection, boolean upload) {
		final ConnectionData connData = new ConnectionData();

		try {	
			connectionsMon.enter();

			LimitedRateGroup[]	groups = connection.getRateLimiters(upload);
			
			//do group registration
			GroupData[]	groupDatas = new GroupData[groups.length];

			for (int i=0;i<groups.length;i++) {
				LimitedRateGroup group = groups[i];
				// boolean log = group.getName().contains("parg");
				GroupData groupData = (GroupData)groupBuckets.get(group);
				if (groupData == null) {
					int limit = NetworkManagerUtilities.getGroupRateLimit(group);
					groupData = new GroupData(createBucket( limit ));
					groupBuckets.put(group, groupData);

					/*
					if (log) {
						System.out.println("Creating RL1: " + group.getName() + " -> " + group_data);
					}
					*/
				}
				groupData.group_size++;
				groupDatas[i] = groupData;

				/*
				if (log) {
					System.out.println("Applying RL1: " + group.getName() + " -> " + connection);
				}
				*/
			}
			connData.groups = groups;
			connData.groupDatas = groupDatas;
			connData.state = ConnectionData.STATE_NORMAL;

			connections.put(connection, connData);
		} finally {
			connectionsMon.exit();	
		}

		mainController.registerPeerConnection(connection);
	}

	public List<NetworkConnectionBase> getConnections()	{
		try {
			connectionsMon.enter();
			return (new ArrayList<NetworkConnectionBase>( connections.keySet()));
		} finally {
			connectionsMon.exit();
		}
	}

	public boolean isRegistered(NetworkConnectionBase connection) {
		try { connectionsMon.enter();
			return (connections.containsKey( connection));
		}
		finally{ connectionsMon.exit(); }
	}

	/**
	 * Cancel upload handling for the given peer connection.
	 * @param connection to cancel
	 */
	public void deregisterPeerConnection(NetworkConnectionBase connection) {
		try { 
			connectionsMon.enter();
			ConnectionData conn_data = (ConnectionData)connections.remove(connection);
			if (conn_data != null) {
			GroupData[]	group_datas = conn_data.groupDatas;
			//do groups de-registration
			for (int i=0;i<group_datas.length;i++) {
				GroupData	group_data = group_datas[i];
				if (group_data.group_size == 1) {	//last of the group
					groupBuckets.remove(conn_data.groups[i]); //so remove
				} else {
					group_data.group_size--;
				}
				}
			}
		}
		finally{ connectionsMon.exit(); }

		mainController.cancelPeerConnection(connection);
	}

	public void setRateLimiterFreezeState(
		boolean	frozen ) {
		mainBucket.setFrozen(frozen);
	}

	public void addRateLimiter(
	NetworkConnectionBase 	connection,
	LimitedRateGroup		group ) {
		try {
			connectionsMon.enter();

				ConnectionData conn_data = (ConnectionData)connections.get(connection);

				if (conn_data != null) {

				LimitedRateGroup[]	groups 		= conn_data.groups;

				for (int i=0;i<groups.length;i++) {

					if (groups[i] == group) {

						return;
					}
				}

				// boolean log = group.getName().contains("parg");

					GroupData group_data = (GroupData)groupBuckets.get(group);

					if (group_data == null) {

						int limit = NetworkManagerUtilities.getGroupRateLimit(group);

						group_data = new GroupData(createBucket( limit ));

						/*
						if (log) {
							System.out.println("Creating RL2: " + group.getName() + " -> " + group_data);
						}
					*/

						groupBuckets.put(group, group_data);
					}

					/*
					if (log) {
						System.out.println("Applying RL2: " + group.getName() + " -> " + connection);
					}
				*/

					group_data.group_size++;

				GroupData[]			group_datas = conn_data.groupDatas;

					int	len = groups.length;

					LimitedRateGroup[]	new_groups = new LimitedRateGroup[ len + 1 ];

					System.arraycopy(groups, 0, new_groups, 0, len);
					new_groups[len] = group;

					conn_data.groups 		= new_groups;

					GroupData[]	new_group_datas = new GroupData[ len + 1 ];

					System.arraycopy(group_datas, 0, new_group_datas, 0, len);
					new_group_datas[len] = group_data;

					conn_data.groupDatas = new_group_datas;
				}
		} finally {

			connectionsMon.exit();
		}
	}

	public void removeRateLimiter(
	NetworkConnectionBase 	connection,
	LimitedRateGroup		group ) {
		 try {
			 connectionsMon.enter();

			 ConnectionData conn_data = (ConnectionData)connections.get(connection);

			 if (conn_data != null) {

				 LimitedRateGroup[]	groups 		= conn_data.groups;
				 GroupData[]			group_datas = conn_data.groupDatas;

				 int	len = groups.length;

				 if (len == 0) {

					 return;
				 }

				 LimitedRateGroup[]	new_groups 		= new LimitedRateGroup[ len - 1 ];
				 GroupData[]			new_group_datas = new GroupData[ len - 1 ];

				 int	pos = 0;

				 for (int i=0;i<groups.length;i++) {

					 if (groups[i] == group) {

						 GroupData	group_data = conn_data.groupDatas[i];

						 if (group_data.group_size == 1) {	//last of the group

							 groupBuckets.remove(conn_data.groups[i]); //so remove

						 } else {

							 group_data.group_size--;
						 }
					 } else {

						 if (pos == new_groups.length) {

							 return;
						 }

						 new_groups[pos]		= groups[i];
						 new_group_datas[pos]	= group_datas[i];

						 pos++;
					 }
				 }

				 conn_data.groups 		= new_groups;
				 conn_data.groupDatas 	= new_group_datas;
			 }
		 } finally {

			 connectionsMon.exit();
		 }
	}


	// private static long last_log = 0;

	/**
	 * Upgrade the given connection to a high-speed transfer handler.
	 * @param connection to upgrade
	 */
	public void upgradePeerConnection(
		final NetworkConnectionBase 	connection,
		int 							partition_id ) {
		ConnectionData connectionData = null;
		try {
			connectionsMon.enter();
			connectionData = (ConnectionData)connections.get(connection);
		} finally {
			connectionsMon.exit();
		}
		if (connectionData != null && connectionData.state == ConnectionData.STATE_NORMAL) {
			final ConnectionData f_connectionData = connectionData;
			mainController.upgradePeerConnection(
				connection,
				new RateHandler() {
			 		final int pt = processorType;
			 		
					public int[] getCurrentNumBytesAllowed() {
			 			int special;
						if (pt == TYPE_UPLOAD) {
							if (RATE_LIMIT_UP_INCLUDES_PROTOCOL) {
								special = 0;
							} else {
								special = Integer.MAX_VALUE;
							}
						} else {
			 				if (RATE_LIMIT_DOWN_INCLUDES_PROTOCOL) {
								special = 0;
							} else {
								special = Integer.MAX_VALUE;
							}
						}
						
						// sync global rate
						if (mainBucket.getRate() != maxRate.getRateLimitBytesPerSecond()) {
							mainBucket.setRate(maxRate.getRateLimitBytesPerSecond());
						}
						
						int allowed = mainBucket.getAvailableByteCount();
						
						// reserve bandwidth for the general pool
						allowed -= connection.getMssSize();
						if (allowed < 0) allowed = 0;
						
						// only apply group rates to non-lan local connections
						// ******* READ ME *******
						// If you ever come here looking for an explanation as to why on torrent startup some peers
						// appear to be ignoring rate limits for the first few pieces of a download
						// REMEMBER that fast-start extension pieces are downloaded while be peer is choking us and hence
						// in a non-upgraded state WHICH MEANS that rate limits are NOT APPLIED
						if (RATE_LIMIT_LAN_TOO || !(connection.isLANLocal() && NetworkManager.isLANRateEnabled())) {
							// sync group rates
							LimitedRateGroup[]	groups 		= f_connectionData.groups;
							GroupData[]			groupDatas = f_connectionData.groupDatas;
							if (groups.length != groupDatas.length) {
								// yeah, I know....
								try {
									connectionsMon.enter();
									groups 		= f_connectionData.groups;
									groupDatas	= f_connectionData.groupDatas;
								} finally {
									connectionsMon.exit();
								}
							}
							
							try {
								for (int i=0;i<groupDatas.length;i++) {
									//boolean log = group.getName().contains("parg");
									int group_rate = NetworkManagerUtilities.getGroupRateLimit( groups[i]);
									ByteBucket group_bucket = groupDatas[i].bucket;
									/*
									if (log) {
										long now = SystemTime.getCurrentTime();
										if (now - last_log > 500) {
											last_log = now;
											System.out.println("		" + group.getName() + " -> " + group_rate + "/" + group_bucket.getAvailableByteCount());
										}
									}
									 */
									if (group_bucket.getRate() != group_rate) {
										group_bucket.setRate(group_rate);
									}
									int 	group_allowed = group_bucket.getAvailableByteCount();
									if (group_allowed < allowed) {
										allowed = group_allowed;
									}
								}
							} catch (Throwable e) {
								// conn_data.group stuff is not synchronized for speed but can cause borkage if new
								// limiters added so trap here
								if (!(e instanceof IndexOutOfBoundsException)) {
									Debug.printStackTrace(e);
								}
							}
						}
						return (new int[]{ allowed, special });
					}
					
					public void bytesProcessed(
						int data_bytes,
						int protocol_bytes) {
						//System.out.println((pt == TYPE_UPLOAD?"Up":"Down") + ": " + data_bytes + "/" + protocol_bytes);
						int numBytesWritten;
						if (pt == TYPE_UPLOAD) {
							numBytesWritten = RATE_LIMIT_UP_INCLUDES_PROTOCOL?data_bytes + protocol_bytes:data_bytes;
						} else {
			 				numBytesWritten = RATE_LIMIT_DOWN_INCLUDES_PROTOCOL?data_bytes + protocol_bytes:data_bytes;
						}
						
						if (RATE_LIMIT_LAN_TOO || !( connection.isLANLocal() && NetworkManager.isLANRateEnabled())) {
							LimitedRateGroup[]	groups 		= f_connectionData.groups;
							GroupData[]			group_datas = f_connectionData.groupDatas;
							if (groups.length != group_datas.length) {
								// yeah, I know....
								try {
									connectionsMon.enter();
									groups 		= f_connectionData.groups;
									group_datas	= f_connectionData.groupDatas;
								} finally {
									connectionsMon.exit();
								}
							}
							for (int i=0;i<group_datas.length;i++) {
								group_datas[i].bucket.setBytesUsed(numBytesWritten);
								groups[i].updateBytesUsed(numBytesWritten);
							}
						}
						mainBucket.setBytesUsed(numBytesWritten);
					}
				},
				partition_id);
			f_connectionData.state = ConnectionData.STATE_UPGRADED;
		}
	}


	/**
	 * Downgrade the given connection back to a normal-speed transfer handler.
	 * @param connection to downgrade
	 */
	public void downgradePeerConnection(NetworkConnectionBase connection) {
		ConnectionData conn_data = null;

		try { connectionsMon.enter();
			conn_data = (ConnectionData)connections.get(connection);
		}
		finally{ connectionsMon.exit(); }

		if (conn_data != null && conn_data.state == ConnectionData.STATE_UPGRADED) {
			mainController.downgradePeerConnection(connection);
			conn_data.state = ConnectionData.STATE_NORMAL;
		}
	}

	public RateHandler
	getRateHandler() {
		return (mainRateHandler);
	}

	public RateHandler
	getRateHandler(
	NetworkConnectionBase	connection ) {
	return (mainController.getRateHandler( connection));
	}

	private ByteBucket createBucket(int	bytes_per_sec) {
		if (multiThreaded) {
			return (new ByteBucketMT( bytes_per_sec));
		} else {
			return (new ByteBucketST( bytes_per_sec));
		}
	}

	private static class ConnectionData {
		private static final int STATE_NORMAL	 = 0;
		private static final int STATE_UPGRADED = 1;

		private int state;
		private LimitedRateGroup[] groups;
		private GroupData[] groupDatas;
	}

	private static class GroupData {
		private final ByteBucket bucket;
		private int group_size = 0;

		private GroupData(ByteBucket bucket) {
			this.bucket = bucket;
		}
	}


}
