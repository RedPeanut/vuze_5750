/*
 * Created on 16-Jan-2005
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


/**
 * @author parg
 *
 */

import java.util.*;

import org.gudy.azureus2.core3.util.ByteArrayHashMap;
import org.gudy.azureus2.core3.util.ByteFormatter;
import org.gudy.azureus2.core3.util.HashWrapper;

import com.aelitis.azureus.core.dht.DHTLogger;
import com.aelitis.azureus.core.dht.transport.DHTTransportContact;
import com.aelitis.azureus.core.dht.transport.DHTTransportValue;

public class DHTLog {
	
	public static final boolean		GLOBAL_BLOOM_TRACE		= false;
	public static final boolean		LOCAL_BLOOM_TRACE		= false;
	public static final boolean		CONTACT_VERIFY_TRACE	= false;
	public static final boolean		TRACE_VERSIONS 			= false;

	static {
		if (GLOBAL_BLOOM_TRACE) {
			System.out.println("**** DHTLog: global bloom trace on ****");
		}
		if (LOCAL_BLOOM_TRACE) {
			System.out.println("**** DHTLog: local bloom trace on ****");
		}
		if (CONTACT_VERIFY_TRACE) {
			System.out.println("**** DHTLog: contact verify trace on ****");
		}
		if (TRACE_VERSIONS) {
			System.out.println("**** DHTTransportStats: tracing protocol versions ****");
		}
	}

	public static boolean		loggingOn	= false;
	private static DHTLogger	logger;

	protected static void setLogging(boolean on) {
		loggingOn = on;
	}

	public static boolean isOn() {
		return (loggingOn);
	}

	public static void log(String str) {
		if (loggingOn) {
			if (logger != null) {
				logger.log(str);
			} else {
				System.out.println(str);
			}
		}
	}

	public static void setLogger(DHTLogger l) {
		logger = l;
	}

	public static String getString(byte[] b) {
		if (loggingOn) {
			return (getString2(b));
		} else {
			return ("");
		}
	}

	public static String getString2(byte[] b) {
		String res = ByteFormatter.nicePrint(b);
		if (res.length() > 8) {
			res = res.substring(0,8)+"...";
		}
		return (res);
	}

	public static String getFullString(byte[] b) {
		return (ByteFormatter.nicePrint(b));
	}

	public static String getString(HashWrapper w) {
		if (loggingOn) {
			return (getString(w.getHash()));
		} else {
			return ("");
		}
	}

	public static String getString(DHTTransportContact[] contacts) {
		if (loggingOn) {
			StringBuilder sb = new StringBuilder(128);
			sb.append("{");
			for (int i = 0; i < contacts.length; i++) {
				if (i > 0) {
					sb.append(",");
				}
				sb.append(getString(contacts[i].getID()));
			}
			sb.append("}");
			return (sb.toString());
		} else {
			return ("");
		}
	}

	public static String getString(DHTTransportContact contact) {
		if (loggingOn) {
			return (contact.getString());
		} else {
			return ("");
		}
	}

	public static String getString(List l) {
		if (loggingOn) {
			StringBuilder sb = new StringBuilder(128);
			sb.append("{");
			for (int i = 0; i < l.size(); i++) {
				if (i > 0) {
					sb.append(",");
				}
				sb.append(getString((DHTTransportContact) l.get(i)));
			}
			sb.append("}");
			return (sb.toString());
		} else {
			return ("");
		}
	}

	public static String getString(
		Set			s) {
		if (loggingOn) {
			StringBuilder sb = new StringBuilder(128);
			sb.append("{");

			Iterator it = s.iterator();

			while (it.hasNext()) {

				if (sb.length() > 1) {
					sb.append(",");
				}
				sb.append( getString((DHTTransportContact)it.next()));
			}

			sb.append("}");

			return ( sb.toString());
		} else {
			return ("");
		}
	}

	public static String getString(
		Map			s) {
		if (loggingOn) {
			StringBuilder sb = new StringBuilder(128);
			sb.append("{");

			Iterator it = s.keySet().iterator();

			while (it.hasNext()) {

				if (sb.length() > 1) {
					sb.append(",");
				}
				sb.append( getString((HashWrapper)it.next()));
			}

			sb.append("}");

			return ( sb.toString());
		} else {
			return ("");
		}
	}

	public static String getString(
		ByteArrayHashMap<?>			s) {
		if (loggingOn) {
			StringBuilder sb = new StringBuilder(128);
			sb.append("{");

			List<byte[]> keys = s.keys();

			for (byte[] key: keys) {

				if (sb.length() > 1) {
					sb.append(",");
				}
				sb.append(getString( key));
			}

			sb.append("}");

			return ( sb.toString());
		} else {
			return ("");
		}
	}

	public static String getString(
		DHTTransportValue[]	values) {
		if (loggingOn) {

			if (values == null) {

				return ("<null>");
			}

			StringBuilder sb = new StringBuilder(256);

			for (int i=0;i<values.length;i++) {

				if (i > 0) {
					sb.append(",");
				}
				getString(sb, values[i]);
			}
			return ( sb.toString());
		} else {
			return ("");
		}
	}

	public static void getString(
		StringBuilder		sb,
		DHTTransportValue	value) {
		if (loggingOn) {

			if (value == null) {

				sb.append("<null>");

			} else {

				sb.append(getString( value.getValue()));
				sb.append(" <");
				sb.append(value.isLocal()?"loc":"rem");
				sb.append(",flag=");
				sb.append(Integer.toHexString(value.getFlags()));
				sb.append(",life=");
				sb.append(value.getLifeTimeHours());
				sb.append(",rep=");
				sb.append(Integer.toHexString(value.getReplicationControl()));
				sb.append(",orig=");
				sb.append(value.getOriginator().getExternalAddress());
				sb.append(">");
			}
		}
	}

	public static String getString(
		DHTTransportValue	value) {
		if (loggingOn) {

			if (value == null) {

				return ("<null>");
			}

			return (getString( value.getValue()) + " <" + (value.isLocal()?"loc":"rem" ) + ",flag=" + Integer.toHexString(value.getFlags()) + ",life=" + value.getLifeTimeHours() + ",rep=" + Integer.toHexString( value.getReplicationControl())+",orig=" + value.getOriginator().getExternalAddress() +">");
		} else {
			return ("");
		}
	}
}
