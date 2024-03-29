/*
 * Created on Oct 7, 2014
 * Created by Paul Gardner
 *
 * Copyright 2014 Azureus Software, Inc.  All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 */


package com.aelitis.azureus.plugins.dht;

public class
DHTPluginOperationAdapter
	implements DHTPluginOperationListener
{
	public void starts(
		byte[]				key) {
	}

	public boolean diversified() {
		return (true);
	}

	public void valueRead(
		DHTPluginContact	originator,
		DHTPluginValue		value) {
	}

	public void valueWritten(
		DHTPluginContact	target,
		DHTPluginValue		value) {
	}

	public void complete(
		byte[]				key,
		boolean				timeout_occurred) {
	}
}
