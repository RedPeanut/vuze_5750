/*
 * Created on 12 Jun 2006
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

package com.aelitis.net.natpmp.upnp;

import com.aelitis.net.natpmp.NatPMPDevice;
import com.aelitis.net.natpmp.upnp.impl.NatPMPUPnPImpl;
import com.aelitis.net.upnp.UPnP;
import com.aelitis.net.upnp.UPnPException;

public class
NatPMPUPnPFactory
{
	public static NatPMPUPnP
	create(
		UPnP			upnp,
		NatPMPDevice	nat_pmp_device )

		throws UPnPException
	{
		return (new NatPMPUPnPImpl( upnp, nat_pmp_device));
	}
}
