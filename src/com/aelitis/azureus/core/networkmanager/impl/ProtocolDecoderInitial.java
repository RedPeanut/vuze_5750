/*
 * Created on 18-Jan-2006
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

package com.aelitis.azureus.core.networkmanager.impl;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import org.gudy.azureus2.core3.logging.LogEvent;
import org.gudy.azureus2.core3.logging.LogIDs;
import org.gudy.azureus2.core3.logging.Logger;
import org.gudy.azureus2.core3.util.AENetworkClassifier;
import org.gudy.azureus2.core3.util.AddressUtils;
import org.gudy.azureus2.core3.util.SystemTime;

import com.aelitis.azureus.core.networkmanager.NetworkManager;

public class ProtocolDecoderInitial
	extends ProtocolDecoder
{
	private static final LogIDs LOGID = LogIDs.NWMAN;

	final ProtocolDecoderAdapter	adapter;
	private TransportHelperFilter	filter;
	final TransportHelper	transport;

	private final byte[][]	shared_secrets;
	final ByteBuffer	initialData;
	private ByteBuffer	decodeBuffer;
	private int			decodeRead;

	private long	startTime	= SystemTime.getCurrentTime();

	private ProtocolDecoderPHE	phe_decoder;

	private long	lastReadTime		= 0;

	private boolean processingComplete;

	public ProtocolDecoderInitial(
		TransportHelper				_transport,
		byte[][]					_sharedSecrets,
		boolean						_outgoing,
		ByteBuffer					_initialData,
		ProtocolDecoderAdapter		_adapter)
		throws IOException
	{
		super(true);
		transport		= _transport;
		shared_secrets	= _sharedSecrets;
		initialData		= _initialData;
		adapter			= _adapter;
		final TransportHelperFilterTransparent transparentFilter = new TransportHelperFilterTransparent(transport, false);
		filter	= transparentFilter;
		if (_outgoing) {  
			//we assume that for outgoing connections, if we are here, we want to use crypto
			if (ProtocolDecoderPHE.isCryptoOK()) {
				decodePHE(null);
			} else {
				throw (new IOException("Crypto required but unavailable"));
			}
		} else {
			decodeBuffer = ByteBuffer.allocate(adapter.getMaximumPlainHeaderLength());
			transport.registerForReadSelects(
				new TransportHelper.selectListener() {
				   	public boolean selectSuccess(
						TransportHelper	helper,
						Object 			attachment)
				   	{
						try {
							int	len = helper.read(decodeBuffer);
							if (len < 0) {
								failed(new IOException("end of stream on socket read: in=" + decodeBuffer.position()));
							} else if (len == 0) {
								return (false);
							}
							lastReadTime = SystemTime.getCurrentTime();
							decodeRead += len;
							int	match =  adapter.matchPlainHeader(decodeBuffer);
							if (match != ProtocolDecoderAdapter.MATCH_NONE) {
								helper.cancelReadSelects();
								if (NetworkManager.REQUIRE_CRYPTO_HANDSHAKE && match == ProtocolDecoderAdapter.MATCH_CRYPTO_NO_AUTO_FALLBACK) {
									InetSocketAddress isa = transport.getAddress();
									if (NetworkManager.INCOMING_HANDSHAKE_FALLBACK_ALLOWED) {
										if (Logger.isEnabled()) {
											Logger.log(new LogEvent(LOGID, "Incoming connection ["+ isa + "] is not encrypted but has been accepted as fallback is enabled" ));
										}
									} else if (AddressUtils.isLANLocalAddress( AddressUtils.getHostAddress( isa )) == AddressUtils.LAN_LOCAL_YES) {
										if (Logger.isEnabled()) {
											Logger.log(new LogEvent(LOGID, "Incoming connection ["+ isa + "] is not encrypted but has been accepted as lan-local" ));
										}
									} else if (AENetworkClassifier.categoriseAddress(isa) != AENetworkClassifier.AT_PUBLIC) {
										if (Logger.isEnabled()) {
											Logger.log(new LogEvent(LOGID, "Incoming connection ["+ isa + "] is not encrypted but has been accepted as not a public network" ));
										}
									} else {
										throw (new IOException("Crypto required but incoming connection has none"));
									}
								}
								decodeBuffer.flip();
								transparentFilter.insertRead(decodeBuffer);
								complete(initialData);
							} else {
								if (!decodeBuffer.hasRemaining()) {
									helper.cancelReadSelects();
									if (NetworkManager.INCOMING_CRYPTO_ALLOWED) {
										decodeBuffer.flip();
										decodePHE(decodeBuffer);
									} else {
										if (Logger.isEnabled())
											Logger.log(new LogEvent(LOGID, "Incoming connection ["+ transport.getAddress() + "] encrypted but rejected as not permitted" ));
										throw (new IOException("Incoming crypto connection not permitted"));
									}
								}
							}
							return (true);
						} catch (Throwable e) {
							selectFailure(helper, attachment, e);
							return (false);
						}
				   	}
				   	
					public void selectFailure(
						TransportHelper	helper,
						Object 			attachment,
						Throwable 		msg) {
						helper.cancelReadSelects();
						failed(msg);
					}
				},
				this);
		}
	}

	protected void decodePHE(ByteBuffer	buffer)
			throws IOException {
		
		ProtocolDecoderAdapter	pheAdapter =
			new ProtocolDecoderAdapter() {
				public void decodeComplete(
					ProtocolDecoder	decoder,
					ByteBuffer		remaining_initial_data ) {
					filter = decoder.getFilter();
					complete(remaining_initial_data);
				}

				public void decodeFailed(
					ProtocolDecoder	decoder,
					Throwable			cause) {
					failed(cause);
				}

				public void gotSecret(
					byte[]				session_secret) {
					adapter.gotSecret(session_secret);
				}

				public int getMaximumPlainHeaderLength() {
					throw (new RuntimeException());
				}

				public int matchPlainHeader(
					ByteBuffer			buffer) {
					throw (new RuntimeException());
				}
			};

		phe_decoder = new ProtocolDecoderPHE(transport, shared_secrets, buffer, initialData, pheAdapter);
	}

	public boolean isComplete(
		long	now) {
		if (transport == null) {
				// can happen during initialisation
			return (false);
		}
		if (!processingComplete) {
			if (startTime > now) {
				startTime	= now;
			}
			if (lastReadTime > now) {
				lastReadTime	= now;
			}
			if (phe_decoder != null) {
				lastReadTime	= phe_decoder.getLastReadTime();
			}
			long	timeout;
			long	time;
			if (lastReadTime == 0) {
				timeout = transport.getConnectTimeout();
				time	= startTime;
			} else {
				timeout = transport.getReadTimeout();
				time	= lastReadTime;
			}
			if (now - time > timeout) {
				try {
					transport.cancelReadSelects();
					transport.cancelWriteSelects();
				} catch (Throwable e) {
				}
				String	phe_str = "";
				if (phe_decoder != null) {
					phe_str = ", crypto: " + phe_decoder.getString();
				}
			   	if (Logger.isEnabled()) {
					Logger.log(new LogEvent(LOGID, "Connection ["
							+ transport.getAddress() + "] forcibly timed out after "
							+ timeout/1000 + "sec due to socket inactivity"));
			   	}
				failed(new Throwable("Protocol decode aborted: timed out after " + timeout/1000+ "sec: " + decodeRead + " bytes read" + phe_str));
			}
		}
		return (processingComplete);
	}

	public TransportHelperFilter getFilter() {
		return (filter);
	}

	protected void complete(ByteBuffer remainingInitialData) {
		if (!processingComplete) {
			processingComplete	= true;
			adapter.decodeComplete(this, remainingInitialData);
		}
	}

	protected void failed(Throwable	reason) {
		if (!processingComplete) {
			processingComplete	= true;
			adapter.decodeFailed(this, reason);
		}
	}
}
