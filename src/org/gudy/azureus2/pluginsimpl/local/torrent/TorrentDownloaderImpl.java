/*
 * File    : TorrentDownloaderImpl.java
 * Created : 28-Feb-2004
 * By      : parg
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

package org.gudy.azureus2.pluginsimpl.local.torrent;

/**
 * @author parg
 *
 */

import java.net.*;
import java.util.Locale;
import java.io.*;

import org.gudy.azureus2.plugins.torrent.*;
import org.gudy.azureus2.plugins.utils.resourcedownloader.*;
import org.gudy.azureus2.pluginsimpl.local.utils.resourcedownloader.ResourceDownloaderFactoryImpl;
import org.gudy.azureus2.core3.logging.*;
import org.gudy.azureus2.core3.torrent.*;
import org.gudy.azureus2.core3.util.*;

public class
TorrentDownloaderImpl
	implements TorrentDownloader
{
	private static final LogIDs LOGID = LogIDs.PLUGIN;
	protected TorrentManagerImpl		manager;
	protected URL						url;
	protected ResourceDownloader		_downloader;

	protected boolean					encoding_requested;
	protected String					requested_encoding;
	protected boolean					set_encoding;

	protected TorrentDownloaderImpl(
		TorrentManagerImpl		_manager,
		URL						_url) {
		manager		= _manager;
		url			= _url;

		_downloader = ResourceDownloaderFactoryImpl.getSingleton().create(url);
	}

	protected TorrentDownloaderImpl(
		TorrentManagerImpl	_manager,
		URL					_url,
		String				_user_name,
		String				_password) {
		manager	= _manager;
		url		= _url;

			// assumption here is that if we have a user name and password supplied
			// then user-interaction is NOT required. Thus we set the default encoding
			// to ensure that in the unlikely event of the torrent having multiple
			// encodings the SWT UI isn't kicked off to get an encoding when the user
			// is absent.

		set_encoding	= true;

		_downloader = ResourceDownloaderFactoryImpl.getSingleton().create(url, _user_name, _password);

		_downloader.addListener(new ResourceDownloaderAdapter() {
			public void reportActivity(ResourceDownloader downloader, String activity) {
				if (Logger.isEnabled())
					Logger.log(new LogEvent(LOGID, "TorrentDownloader:" + activity));
			}
		});

	}

	public Torrent
	download()

		throws TorrentException
	{
		try {
			return (downloadSupport( _downloader));

		} catch (TorrentException e) {

			if (url.getProtocol().toLowerCase(Locale.US).startsWith("http")) {

				ResourceDownloader rd = _downloader.getClone();

					// try with referer

				UrlUtils.setBrowserHeaders( rd, url.toExternalForm());

				return (downloadSupport( rd));

			} else {

				throw (e);
			}
		}
	}

	private Torrent
	downloadSupport(
		ResourceDownloader downloader )

		throws TorrentException
	{
		InputStream	is = null;

		try {
			is = downloader.download();

			TOTorrent	torrent = TOTorrentFactory.deserialiseFromBEncodedInputStream(is);

			if (encoding_requested) {

				manager.tryToSetTorrentEncoding(torrent, requested_encoding);

			} else {

				if (set_encoding) {

					manager.tryToSetDefaultTorrentEncoding(torrent);
				}
			}

			return (new TorrentImpl(torrent));

		} catch (TorrentException e) {

			throw (e);

		} catch (ResourceDownloaderException e) {

			throw (new TorrentException( e));

		} catch (Throwable e) {

			throw (new TorrentException("TorrentDownloader: download fails", e));

		} finally {

			if (is != null) {

				try {

					is.close();

				} catch (IOException e) {

					Debug.printStackTrace(e);
				}
			}
		}
	}

	public Torrent
	download(
		String	encoding )

		throws TorrentException
	{
		encoding_requested	= true;
		requested_encoding	= encoding;

		return ( download());
	}

	public void setRequestProperty(String key, Object value)
			throws TorrentException {
		if (_downloader != null) {
			try {
				_downloader.setProperty(key, value);
			} catch (ResourceDownloaderException e) {
				throw new TorrentException(e);
			}
		}
	}

	public Object getRequestProperty(String key)
		throws TorrentException {
		if (_downloader != null) {
			try {
				return _downloader.getProperty(key);
			} catch (ResourceDownloaderException e) {
				throw new TorrentException(e);
			}
		}
		return null;
	}

}
