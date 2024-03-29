/*
 * Created on Sep 23, 2004
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

import org.gudy.azureus2.core3.util.AEMonitor;
import org.gudy.azureus2.core3.util.Debug;

import com.aelitis.azureus.core.networkmanager.*;


/**
 * Manages transfer entities on behalf of peer connections.
 * Each entity handler has a global pool which manages all
 * connections by default.	Connections can also be "upgraded"
 * to a higher connection control level, i.e. each connection
 * has its own specialized entity for performance purposes.
 */
public class EntityHandler {
	private final HashMap upgraded_connections = new HashMap();
	private final AEMonitor lock = new AEMonitor("EntityHandler");
	private final MultiPeerUploader globalUploader;
	private final MultiPeerDownloader2 globalDownloader;
	private boolean globalRegistered = false;
	private final int handlerType;


	/**
	 * Create a new entity handler using the given rate handler.
	 * @param type read or write type handler
	 * @param rateHandler global max rate handler
	 */
	public EntityHandler(int type, RateHandler rateHandler) {
		this.handlerType = type;
		if (handlerType == TransferProcessor.TYPE_UPLOAD) {
			globalUploader = new MultiPeerUploader(rateHandler);
			globalDownloader = null;
		} else {	//download type
			globalDownloader = new MultiPeerDownloader2(rateHandler);
			globalUploader = null;
		}
	}



	/**
	 * Register a peer connection for management by the handler.
	 * @param connection to add to the global pool
	 */
	public void registerPeerConnection(NetworkConnectionBase connection) {
		try {
			lock.enter();
			if (!globalRegistered) {
				if (handlerType == TransferProcessor.TYPE_UPLOAD) {
					NetworkManager.getSingleton().addWriteEntity(globalUploader, -1);	//register global upload entity
				} else {
					NetworkManager.getSingleton().addReadEntity(globalDownloader, -1);	//register global download entity
				}
				globalRegistered = true;
			}
		} finally { lock.exit(); }

		if (handlerType == TransferProcessor.TYPE_UPLOAD) {
			globalUploader.addPeerConnection(connection);
		} else {
			globalDownloader.addPeerConnection(connection);
		}
	}


	/**
	 * Remove a peer connection from the entity handler.
	 * @param connection to cancel
	 */
	public void cancelPeerConnection(NetworkConnectionBase connection) {
	if (handlerType == TransferProcessor.TYPE_UPLOAD) {
		if (!globalUploader.removePeerConnection( connection )) {	//if not found in the pool entity
		SinglePeerUploader upload_entity = (SinglePeerUploader)upgraded_connections.remove(connection);	//check for it in the upgraded list
		if (upload_entity != null) {
			NetworkManager.getSingleton().removeWriteEntity(upload_entity);	//cancel from write processing
		}
		}
	}
	else {
		if (!globalDownloader.removePeerConnection( connection )) {	//if not found in the pool entity
		SinglePeerDownloader download_entity = (SinglePeerDownloader)upgraded_connections.remove(connection);	//check for it in the upgraded list
		if (download_entity != null) {
			NetworkManager.getSingleton().removeReadEntity(download_entity);	//cancel from read processing
		}
		}
	}

	}


	/**
	 * Upgrade a peer connection from the general pool to its own high-speed entity.
	 * @param connection to upgrade from global management
	 * @param handler individual connection rate handler
	 */
	public void upgradePeerConnection(NetworkConnectionBase connection, RateHandler handler, int partition_id) {
		try {	
			lock.enter();
			if (handlerType == TransferProcessor.TYPE_UPLOAD) {
				SinglePeerUploader upload_entity = new SinglePeerUploader(connection, handler);
				if (!globalUploader.removePeerConnection( connection )) {	//remove it from the general upload pool
					Debug.out("upgradePeerConnection:: upload entity not found/removed !");
				}
				NetworkManager.getSingleton().addWriteEntity(upload_entity, partition_id);	//register it for write processing
				upgraded_connections.put(connection, upload_entity);	//add it to the upgraded list
			} else {
				SinglePeerDownloader download_entity = new SinglePeerDownloader(connection, handler);
				if (!globalDownloader.removePeerConnection( connection )) {	//remove it from the general upload pool
					Debug.out("upgradePeerConnection:: download entity not found/removed !");
				}
				NetworkManager.getSingleton().addReadEntity(download_entity, partition_id);	//register it for read processing
				upgraded_connections.put(connection, download_entity);	//add it to the upgraded list
			}
		} finally {	
			lock.exit();	
		}
	}

	/**
	 * Downgrade (return) a peer connection back into the general pool.
	 * @param connection to downgrade back into the global entity
	 */
	public void downgradePeerConnection(NetworkConnectionBase connection) {
	try {	lock.enter();
		if (handlerType == TransferProcessor.TYPE_UPLOAD) {
		SinglePeerUploader upload_entity = (SinglePeerUploader)upgraded_connections.remove(connection);	//remove from the upgraded list
		if (upload_entity != null) {
			NetworkManager.getSingleton().removeWriteEntity(upload_entity);	//cancel from write processing
		}
		else {
			Debug.out("upload_entity == null");
		}
		globalUploader.addPeerConnection(connection);	//move back to the general pool
		}
		else {
		SinglePeerDownloader download_entity = (SinglePeerDownloader)upgraded_connections.remove(connection);	//remove from the upgraded list
		if (download_entity != null) {
			NetworkManager.getSingleton().removeReadEntity(download_entity);	//cancel from read processing
		}
		else {
			Debug.out("download_entity == null");
		}
		globalDownloader.addPeerConnection(connection);	//move back to the general pool
		}
	}
	finally {	lock.exit();	}
	}

	public RateHandler
	getRateHandler(
	 NetworkConnectionBase		connection )
	{
		try {
			lock.enter();

			if (handlerType == TransferProcessor.TYPE_UPLOAD) {

				SinglePeerUploader upload_entity = (SinglePeerUploader)upgraded_connections.get(connection);

				if (upload_entity != null) {

					return ( upload_entity.getRateHandler());
				} else {

					return ( globalUploader.getRateHandler());
				}
			} else {

				SinglePeerDownloader download_entity = (SinglePeerDownloader)upgraded_connections.get(connection);

				if (download_entity != null) {

					return ( download_entity.getRateHandler());
				} else {

					return ( globalDownloader.getRateHandler());
				}
			}

		} finally {
			lock.exit();
		}
	}

	/**
	 * Is the general pool entity in need of a transfer op.
	 * NOTE: Because the general pool is backed by a MultiPeer entity,
	 * it requires at least MSS available bytes before it will/can perform
	 * a successful transfer.	This method allows higher-level bandwidth allocation to
	 * determine if it should reserve the necessary MSS bytes for the general pool's needs.
	 * @return true of it has data to transfer, false if not
	 */
	/*
	public boolean isGeneralPoolReserveNeeded() {
	if (handler_type == TransferProcessor.TYPE_UPLOAD) {
		return global_uploader.hasWriteDataAvailable();
	}
	return global_downloader.hasReadDataAvailable();
	}
	*/

}
