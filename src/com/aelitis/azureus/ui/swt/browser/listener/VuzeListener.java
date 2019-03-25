/*
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details (see the LICENSE file).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.aelitis.azureus.ui.swt.browser.listener;

import java.util.Map;

import org.gudy.azureus2.core3.util.Base32;
import org.gudy.azureus2.core3.util.SystemTime;
import org.gudy.azureus2.ui.swt.speedtest.SpeedTestSelector;

import com.aelitis.azureus.core.messenger.browser.BrowserMessage;
import com.aelitis.azureus.core.messenger.browser.listeners.AbstractBrowserMessageListener;
import com.aelitis.azureus.core.vuzefile.VuzeFile;
import com.aelitis.azureus.core.vuzefile.VuzeFileHandler;
import com.aelitis.azureus.ui.swt.feature.FeatureManagerUI;
import com.aelitis.azureus.util.FeatureUtils;
import com.aelitis.azureus.util.MapUtils;


public class VuzeListener
	extends AbstractBrowserMessageListener
{
	public static final String DEFAULT_LISTENER_ID = "vuze";

	public static final String OP_LOAD_VUZE_FILE = "load-vuze-file";

	public static final String OP_INSTALL_TRIAL = "install-trial";

	public static final String OP_GET_MODE = "get-mode";

	public static final String OP_GET_REMAINING = "get-plus-remaining";

	public static final String OP_RUN_SPEED_TEST = "run-speed-test";

	public VuzeListener() {
		super(DEFAULT_LISTENER_ID);
	}

	public void handleMessage(
		BrowserMessage message) {
		String opid = message.getOperationId();

		if (OP_LOAD_VUZE_FILE.equals(opid)) {

			Map decodedMap = message.getDecodedMap();

			String content = MapUtils.getMapString(decodedMap, "content", null);

			if (content == null) {

				throw new IllegalArgumentException("content missing");

			} else {

				byte[] bytes = Base32.decode(content);

				VuzeFileHandler vfh = VuzeFileHandler.getSingleton();

				VuzeFile vf = vfh.loadVuzeFile(bytes);

				if (vf == null) {

					throw new IllegalArgumentException("content invalid");

				} else {

					vfh.handleFiles(new VuzeFile[]{ vf }, 0);
				}
			}
		} else if (OP_INSTALL_TRIAL.equals(opid)) {
			FeatureManagerUI.createTrial();

		} else if (OP_RUN_SPEED_TEST.equals(opid)) {

			SpeedTestSelector.runMLABTest( null);

		} else if (OP_GET_MODE.equals(opid)) {
			Map decodedMap = message.getDecodedMap();

			String callback = MapUtils.getMapString(decodedMap, "callback", null);

			if (callback != null) {

				context.executeInBrowser(callback + "('" + FeatureUtils.getPlusMode() + "')");

			} else {

				message.debug("bad or no callback param");
			}
		} else if (OP_GET_REMAINING.equals(opid)) {
			Map decodedMap = message.getDecodedMap();

			String callback = MapUtils.getMapString(decodedMap, "callback", null);

			if (callback != null) {

				FeatureUtils.licenceDetails fd = FeatureUtils.getPlusFeatureDetails();
				if (fd == null || fd.getExpiryTimeStamp() == 0) {
					context.executeInBrowser(callback + "()");
				} else {
					long ms1 = fd.getExpiryTimeStamp() - SystemTime.getCurrentTime();
					long ms2 = fd.getExpiryDisplayTimeStamp() - SystemTime.getCurrentTime();
					context.executeInBrowser(callback + "(" + ms1 + "," + ms2 + ")");
				}

			} else {

				message.debug("bad or no callback param");
			}
		} else {

			throw new IllegalArgumentException("Unknown operation: " + opid);
		}
	}
}
