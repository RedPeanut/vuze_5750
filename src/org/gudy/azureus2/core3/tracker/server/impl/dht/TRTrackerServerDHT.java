/*
 * Created on 13-Feb-2005
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

package org.gudy.azureus2.core3.tracker.server.impl.dht;

import java.net.InetAddress;

import org.gudy.azureus2.core3.tracker.server.TRTrackerServerRequestListener;
import org.gudy.azureus2.core3.tracker.server.impl.TRTrackerServerImpl;

/**
 * @author parg
 *
 */

public class
TRTrackerServerDHT
	extends TRTrackerServerImpl
{
	public TRTrackerServerDHT(
		String		_name,
		boolean		_start_up_ready) {
		super(_name, _start_up_ready);
	}

	public String getHost() {
		return ("dht");
	}

	public int getPort() {
		return (-1);
	}

	public boolean isSSL() {
		return (false);
	}

	public InetAddress
	getBindIP() {
		return (null);
	}

	public void addRequestListener(
		TRTrackerServerRequestListener	l) {
	}

	public void removeRequestListener(
		TRTrackerServerRequestListener	l) {
	}

	protected void closeSupport() {
		destroySupport();
	}
}
