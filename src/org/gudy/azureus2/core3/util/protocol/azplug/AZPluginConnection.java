 /*
 * Created on 06-Mar-2005
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

package org.gudy.azureus2.core3.util.protocol.azplug;

import java.util.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.UrlUtils;
import org.gudy.azureus2.plugins.PluginInterface;
import org.gudy.azureus2.plugins.ipc.IPCException;
import org.gudy.azureus2.plugins.ipc.IPCInterface;

import com.aelitis.azureus.core.AzureusCoreFactory;
import com.aelitis.azureus.core.util.AZ3Functions;
import com.aelitis.azureus.core.vuzefile.VuzeFileHandler;

/**
 * @author parg
 *
 */

public class
AZPluginConnection
	extends HttpURLConnection
{
	private int		response_code	= HTTP_OK;
	private String	response_msg	= "OK";

	private InputStream					input_stream;
	private final Map<String,List<String>> 	headers = new HashMap<String, List<String>>();

	protected AZPluginConnection(
		URL		_url) {
		super(_url);
	}

	public void connect()
		throws IOException

	{
		String url = getURL().toString();

		int	pos = url.indexOf("?");

		if (pos == -1) {

			throw (new IOException("Malformed URL - ? missing"));
		}

		url = url.substring(pos+1);

		String[]	bits = url.split("&");

		Map args = new HashMap();

		for (int i=0;i<bits.length;i++) {

			String	bit = bits[i];

			String[] x = bit.split("=");

			if (x.length == 2) {

				String	lhs = x[0];
				String	rhs = UrlUtils.decode(x[1]);

				args.put(lhs.toLowerCase(), rhs);
			}
		}

		String	plugin_id = (String)args.get("id");

		if (plugin_id == null) {

			throw (new IOException("Plugin id missing"));
		}

		String	plugin_name = (String)args.get("name");
		String	arg			= (String)args.get("arg");

		if (arg == null) {

			arg = "";
		}

		String plugin_str;

		if (plugin_name == null) {

			plugin_str = "with id '" + plugin_id + "'";

		} else {

			plugin_str = "'" + plugin_name + "' (id " + plugin_id + ")";
		}

		if (plugin_id.equals("subscription")) {

			AZ3Functions.provider az3 = AZ3Functions.getProvider();

			if (az3 == null) {

				throw (new IOException("Subscriptions are not available"));
			}

			try {
				az3.subscribeToSubscription(arg);

				input_stream = new ByteArrayInputStream( VuzeFileHandler.getSingleton().create().exportToBytes());

			} catch (Throwable e) {

				throw (new IOException("Subscription addition failed: " + Debug.getNestedExceptionMessage( e)));
			}
		} else {
			// AZPluginConnection is called via reflection
			// Let's just assume that the Core is avail..

			PluginInterface pi = AzureusCoreFactory.getSingleton().getPluginManager().getPluginInterfaceByID(plugin_id);

			if (pi == null) {

				throw (new IOException("Plugin " + plugin_str + " is required - go to 'Tools->Plugins->Installation Wizard' to install."));
			}

			IPCInterface ipc = pi.getIPC();

			try {
				if (ipc.canInvoke("handleURLProtocol", new Object[]{ this, arg })) {

					input_stream = (InputStream)ipc.invoke("handleURLProtocol", new Object[]{ this, arg });

				} else {

					input_stream = (InputStream)ipc.invoke("handleURLProtocol", new Object[]{ arg });
				}
			} catch (IPCException ipce) {

				Throwable e = ipce;

				if (e.getCause() != null) {

					e = e.getCause();
				}

				throw (new IOException("Communication error with plugin '" + plugin_str + "': " + Debug.getNestedExceptionMessage(e)));
			}
		}
	}

    public Map<String,List<String>>
    getHeaderFields()
    {
        return (headers);
    }

    public String
    getHeaderField(
    	String	name )
    {
    	List<String> values = headers.get(name);

    	if (values == null || values.size() == 0) {

    		return (null);
    	}

    	return (values.get( values.size()-1));
    }

    public void
    setHeaderField(
    	String	name,
    	String	value )
    {
       	List<String> values = headers.get(name);

       	if (values == null) {

       		values = new ArrayList<String>();

       		headers.put(name, values);
       	}

       	values.add(value);
    }

	public InputStream
	getInputStream()

		throws IOException
	{
		return (input_stream);
	}

	public void setResponse(
		int		_code,
		String	_msg) {
		response_code		= _code;
		response_msg		= _msg;
	}

	public int getResponseCode() {
		return (response_code);
	}

	public String getResponseMessage() {
		return (response_msg);
	}

	public boolean usingProxy() {
		return (false);
	}

	public void disconnect() {
	}
}
