/*
 * Created on 20 mai 2004
 * Created by Olivier Chalouhi
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details (see the LICENSE file).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.gudy.azureus2.ui.swt.updater2;

import java.util.Map;

import org.eclipse.swt.SWT;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.logging.*;
import org.gudy.azureus2.core3.util.AEVerifier;
import org.gudy.azureus2.core3.util.Constants;

import com.aelitis.azureus.core.versioncheck.VersionCheckClient;

import org.gudy.azureus2.plugins.update.UpdateChecker;


/**
 * @author Olivier Chalouhi
 *
 */
public class SWTVersionGetter {
	private static final LogIDs LOGID = LogIDs.GUI;

  private String platform;
  private int currentVersion;
  private int latestVersion;
  private UpdateChecker	checker;

  private String[] mirrors;

	private String infoURL;

  public
  SWTVersionGetter(
  		UpdateChecker	_checker )
  {
    this.platform 		= COConfigurationManager.getStringParameter("ConfigView.section.style.swt.library.selection");
    if (this.platform == null || this.platform.length() == 0) {
    	this.platform = SWT.getPlatform();
    }
    this.currentVersion = SWT.getVersion();


    this.latestVersion = 0;
    checker	= _checker;
  }

  public boolean needsUpdate() {
    try {
      downloadLatestVersion();

      String msg = "SWT: current version = " + currentVersion + ", latest version = " + latestVersion;

      checker.reportProgress(msg);

      if (Logger.isEnabled())
				Logger.log(new LogEvent(LOGID, msg));

      return latestVersion > currentVersion;
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
  }

  private void downloadLatestVersion() {
  	if (Logger.isEnabled())
			Logger.log(new LogEvent(LOGID, "Requesting latest SWT version "
					+ "and url from version check client."));

    Map reply = VersionCheckClient.getSingleton().getVersionCheckInfo(VersionCheckClient.REASON_CHECK_SWT);

    String msg = "SWT version check received:";

    boolean	done = false;

    if (Constants.isOSX_10_5_OrHigher) {

    	String target_lib = COConfigurationManager.getStringParameter("ConfigView.section.style.swt.library.selection");

    	String current_lib = SWT.getPlatform();

    	if (target_lib.equalsIgnoreCase( current_lib)) {

    	    byte[] version_bytes = (byte[])reply.get("swt_version_" + target_lib);

    	    if (version_bytes != null) {

    	    	latestVersion = Integer.parseInt(new String( version_bytes ));

    	    	msg += " version=" + latestVersion;

        		byte[] url_bytes = (byte[])reply.get("swt_url_" + target_lib);

        		if (url_bytes != null) {

           			mirrors = new String[] { new String(url_bytes) };

        			msg += " url=" + mirrors[0];
        		}

        		done = true;
    	    }
    	} else {

    		byte[] url_bytes = (byte[])reply.get("swt_url_" + target_lib);

    		if (url_bytes != null) {

    			msg += " (platform switch from " + current_lib + " to " + target_lib + ")";

    			mirrors = new String[] { new String(url_bytes) };

    			msg += " url=" + mirrors[0];

    			latestVersion = Integer.MAX_VALUE;

    			done = true;
    		}
    	}
    }

    if (!done) {

	    byte[] version_bytes = (byte[])reply.get("swt_version");
	    if (version_bytes != null) {
	      latestVersion = Integer.parseInt(new String( version_bytes ));
	      msg += " version=" + latestVersion;
	    }

	    byte[] url_bytes = (byte[])reply.get("swt_url");
	    if (url_bytes != null) {
	      mirrors = new String[] { new String(url_bytes) };
	      msg += " url=" + mirrors[0];
	    }
    }

    byte[] info_bytes = (byte[])reply.get("swt_info_url");

    if (info_bytes != null) {

    	byte[] sig = (byte[])reply.get("swt_info_sig");

		if (sig == null) {

			Logger.log(new LogEvent( LogIDs.LOGGER, "swt info signature check failed - missing signature"));

		} else {

		  	try {
	    		infoURL = new String(info_bytes);

	    		try {
					AEVerifier.verifyData(infoURL, sig);

				} catch (Throwable e) {

					Logger.log(new LogEvent( LogIDs.LOGGER, "swt info signature check failed", e ));

					infoURL = null;
				}
	    	} catch (Exception e) {

	    		Logger.log(new LogEvent(LOGID, "swt info_url", e));
	    	}
		}
    }

    if (Logger.isEnabled())
			Logger.log(new LogEvent(LOGID, msg));
  }




  /**
   * @return Returns the latestVersion.
   */
  public int getLatestVersion() {
    return latestVersion;
  }

  public int getCurrentVersion() {
	    return currentVersion;
  }
  /**
   * @return Returns the platform.
   */
  public String getPlatform() {
    return platform;
  }

  /**
   * @return Returns the mirrors.
   */
  public String[] getMirrors() {
    return mirrors;
  }

	public String getInfoURL() {
		return infoURL;
	}
}
