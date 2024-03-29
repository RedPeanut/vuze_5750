/**
 * Created on Mar 12, 2009
 *
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
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
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.aelitis.azureus.core.messenger.config;

import java.util.*;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.util.*;
import org.gudy.azureus2.plugins.PluginInterface;
import org.gudy.azureus2.plugins.PluginManager;

import com.aelitis.azureus.core.AzureusCoreFactory;
import com.aelitis.azureus.core.devices.*;
import com.aelitis.azureus.core.messenger.PlatformMessage;
import com.aelitis.azureus.core.messenger.PlatformMessenger;

/**
 * @author TuxPaper
 * @created Mar 12, 2009
 *
 */
public class PlatformDevicesMessenger
{
	public static final String CFG_SEND_QOS = "devices.sendQOS";

	public static final String LISTENER_ID = "devices";

	private static final String OP_QOS_TURN_ON = "qos-turn-on";

	private static final String OP_QOS_FOUND_DEVICE = "qos-found-device";

	private static final String OP_REPORT_DEVICES = "report-devices";

	public static void qosTurnOn(boolean withITunes, boolean bugFix) {
		if (!COConfigurationManager.getBooleanParameter(CFG_SEND_QOS, false)) {
			return;
		}

		PlatformMessage message = new PlatformMessage("AZMSG", LISTENER_ID,
				OP_QOS_TURN_ON, new Object[] {
					"itunes",
					Boolean.valueOf(withITunes),
					"os-name",
					Constants.OSName + (bugFix ? ":BF" : "")
				}, 5000);
		message.setSendAZID(false);
		PlatformMessenger.queueMessage(message, null);
	}

	public static void qosFoundDevice(final Device device) {
		if (device == null
				|| !COConfigurationManager.getBooleanParameter(CFG_SEND_QOS, false)) {
			return;
		}

		if ("ms_wmp.generic".equals(device.getClassification())) {
			return;
		}

		SimpleTimer.addEvent("qosFoundDevice", SystemTime.getOffsetTime(1000), new TimerEventPerformer() {
			public void perform(TimerEvent event) {
				_qosFoundDevice(device);
			}
		});
	}

	private static void _qosFoundDevice(Device device) {
		if (device == null
				|| !COConfigurationManager.getBooleanParameter(CFG_SEND_QOS, false)) {
			return;
		}

		HashMap<String, Object> map = new HashMap<String, Object>();

		addPluginVersionsToMap(map);

		map.put("device-name", getDeviceName(device));
		map.put("device-type", new Integer(device.getType()));
		if (device instanceof DeviceMediaRenderer) {
			DeviceMediaRenderer renderer = (DeviceMediaRenderer) device;
			map.put("renderer-species",
					Integer.valueOf(renderer.getRendererSpecies()));
		}

		PlatformMessage message = new PlatformMessage("AZMSG", LISTENER_ID,
				OP_QOS_FOUND_DEVICE, map, 5000);
		message.setSendAZID(false);
		PlatformMessenger.queueMessage(message, null);
	}

	private static void addPluginVersionsToMap(Map map) {
		if (AzureusCoreFactory.isCoreRunning()) {
  		PluginManager pm = AzureusCoreFactory.getSingleton().getPluginManager();
  		PluginInterface pi;
  		pi = pm.getPluginInterfaceByID("vuzexcode");
  		if (pi != null) {
  			map.put("xcode-plugin-version", pi.getPluginVersion());
  		}
  		pi = pm.getPluginInterfaceByID("azitunes");
  		if (pi != null) {
  			map.put("itunes-plugin-version", pi.getPluginVersion());
  		}
		}
		map.put("os-name", Constants.OSName);
	}

	private static String getDeviceName(Device device) {
		return device.getClassification();
		/*
		String name = device.getName();
		String classification = device.getClassification();
		StringBuffer deviceName = new StringBuffer();
		if (device.isGenericUSB()) {
			deviceName.append("{g}");
		}
		if (device.isHidden()) {
			deviceName.append("{h}");
		}
		deviceName.append(name);
		if (!name.equals(classification)) {
			deviceName.append('/');
			deviceName.append(classification);
		}
		return deviceName.toString();
		*/
	}
}
