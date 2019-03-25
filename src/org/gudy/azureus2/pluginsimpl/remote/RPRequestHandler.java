/*
 * File	: RPRequestHandler.java
 * Created : 15-Mar-2004
 * By	  : parg
 *
 * Azureus - a Java Bittorrent client
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

package org.gudy.azureus2.pluginsimpl.remote;

/**
 * @author parg
 *
 */

import java.util.HashMap;
import java.util.Map;

import org.gudy.azureus2.plugins.PluginInterface;
import org.gudy.azureus2.plugins.ipfilter.IPFilter;
import org.gudy.azureus2.plugins.ipfilter.IPRange;
import org.gudy.azureus2.plugins.logging.LoggerChannel;
import org.gudy.azureus2.pluginsimpl.remote.rpexceptions.RPInternalProcessException;
import org.gudy.azureus2.pluginsimpl.remote.rpexceptions.RPNoObjectIDException;

import hello.util.Log;

public class RPRequestHandler {
	
	private static final String TAG = RPRequestHandler.class.getSimpleName();
	
	protected PluginInterface pluginInterface;

	protected Map replyCache = new HashMap();

	public RPRequestHandler(PluginInterface _pi) {
		this.pluginInterface = _pi;
	}

	public RPReply processRequest(RPRequest request) {
		return (processRequest(request, null));
	}

	/**
	 * We no longer allow null to be returned, you will have to return a new RPReply instance
	 * which contains null instead.
	 */
	public RPReply processRequest(RPRequest request, RPRequestAccessController accessController) {
		Long connection_id = new Long(request.getConnectionId());
		replyCache  cached_reply = connection_id.longValue()==0?null:(replyCache)replyCache.get(connection_id);
		if (cached_reply != null) {
			if (cached_reply.getId() == request.getRequestId()) {
				return ( cached_reply.getReply());
			}
		}
		RPReply reply = processRequestSupport(request, accessController);
		if (reply == null) {reply = new RPReply(null);}
		replyCache.put(connection_id, new replyCache( request.getRequestId(), reply));
		return (reply);
	}

	protected RPReply processRequestSupport(
		RPRequest				   request,
		RPRequestAccessController   access_controller) {
		
		Log.d(TAG, "processRequestSupport() is called...");
		
		try {
			RPObject object = request.getObject();
			String method = request.getMethod();
			Log.d(TAG, "method = " + method);
			if (object == null && method.equals("getSingleton")) {
				RPObject pi = request.createRemotePluginInterface(pluginInterface);
				RPReply reply = new RPReply(pi);
				return (reply);
			} else if (object == null && method.equals("getDownloads")) {
				RPPluginInterface pi = request.createRemotePluginInterface(pluginInterface);
					// short cut method for quick access to downloads
					// used by GTS
				RPObject dm = (RPObject)pi._process(new RPRequest(null, "getDownloadManager", null)).getResponse();
				RPReply rep = dm._process(new RPRequest(null, "getDownloads", null));
				rep.setProperty("azureus_name", pi.azureus_name);
				rep.setProperty("azureus_version", pi.azureus_version);
				return (rep);
			} else if (object == null) {
				throw new RPNoObjectIDException();
			} else {
				// System.out.println("Request: con = " + request.getConnectionId() + ", req = " + request.getRequestId() + ", client = " + request.getClientIP());
				object = RPObject._lookupLocal( object._getOID());
					// _setLocal synchronizes the RP objects with their underlying
					// plugin objects
				object._setLocal();
				if (method.equals("_refresh")) {
					RPReply reply = new RPReply(object);
					return (reply);
				} else {
					String  name = object._getName();
					if (access_controller != null) {
						access_controller.checkAccess(name, request);
					}
					RPReply reply = object._process(request);
					if (name.equals("IPFilter") &&
							method.equals("setInRangeAddressesAreAllowed[boolean]") &&
							request.getClientIP() != null) {
						String  client_ip   = request.getClientIP();
							// problem here, if someone changes the mode here they'll lose their
							// connection coz they'll be denied access :)
						boolean b = ((Boolean)request.getParams()[0]).booleanValue();
						LoggerChannel[] channels = pluginInterface.getLogger().getChannels();
						IPFilter filter = pluginInterface.getIPFilter();
						if (b) {
							if (filter.isInRange( client_ip)) {
									// we gotta add the client's address range
								for (int i=0;i<channels.length;i++) {
									channels[i].log(
											LoggerChannel.LT_INFORMATION,
										"Adding range for client '" + client_ip + "' as allow/deny flag changed to allow");
								}
								filter.createAndAddRange(
										"auto-added for remote interface",
										client_ip,
										client_ip,
										false);
								filter.save();
								pluginInterface.getPluginconfig().save();
							}
						} else {
							IPRange[]   ranges = filter.getRanges();
							for (int i=0;i<ranges.length;i++) {
								if (ranges[i].isInRange(client_ip)) {
									for (int j=0;j<channels.length;j++) {
										channels[j].log(
												LoggerChannel.LT_INFORMATION,
												"deleting range '" + ranges[i].getStartIP() + "-" + ranges[i].getEndIP() + "' for client '" + client_ip + "' as allow/deny flag changed to deny");
									}
									ranges[i].delete();
								}
							}
							filter.save();
							pluginInterface.getPluginconfig().save();
						}
					}
					return (reply);
				}
			}
		} catch (RPException e) {
			return (new RPReply( e));
		} catch (Exception e) {
			throw new RPInternalProcessException(e);
		}
	}

	protected static class replyCache {
		protected long	  id;
		protected RPReply   reply;

		protected replyCache(long _id, RPReply _reply) {
			id	  = _id;
			reply   = _reply;
		}

		protected long getId() {
			return (id);
		}

		protected RPReply getReply() {
			return (reply);
		}
	}

}