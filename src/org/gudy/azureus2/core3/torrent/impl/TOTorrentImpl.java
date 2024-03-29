/*
 * File    : TOTorrentImpl.java
 * Created : 5 Oct. 2003
 * By      : Parg
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

package org.gudy.azureus2.core3.torrent.impl;


import java.io.*;
import java.net.*;
import java.util.*;

import org.gudy.azureus2.core3.logging.LogRelation;
import org.gudy.azureus2.core3.torrent.*;
import org.gudy.azureus2.core3.util.*;

import com.aelitis.azureus.core.AzureusCoreFactory;

import hello.util.Log;
import hello.util.SingleCounter0;
import hello.util.Util;

public class TOTorrentImpl extends LogRelation implements TOTorrent {
	
	private static String TAG = TOTorrentImpl.class.getSimpleName();
	
	protected static final String TK_ANNOUNCE			= "announce";
	protected static final String TK_ANNOUNCE_LIST		= "announce-list";
	protected static final String TK_COMMENT			= "comment";
	protected static final String TK_CREATION_DATE		= "creation date";
	protected static final String TK_CREATED_BY			= "created by";

	protected static final String TK_INFO				= "info";
	protected static final String TK_NAME				= "name";
	protected static final String TK_LENGTH				= "length";
	protected static final String TK_PATH				= "path";
	protected static final String TK_FILES				= "files";
	protected static final String TK_PIECE_LENGTH		= "piece length";
	protected static final String TK_PIECES				= "pieces";

	protected static final String TK_PRIVATE			= "private";

	protected static final String TK_NAME_UTF8			= "name.utf-8";
	protected static final String TK_PATH_UTF8			= "path.utf-8";
	protected static final String TK_COMMENT_UTF8		= "comment.utf-8";

	protected static final String TK_WEBSEED_BT			= "httpseeds";
	protected static final String TK_WEBSEED_GR			= "url-list";

	protected static final String TK_HASH_OVERRIDE		= "hash-override";

	protected static final List	TK_ADDITIONAL_OK_ATTRS =
		Arrays.asList(new String[]{ TK_COMMENT_UTF8, AZUREUS_PROPERTIES, TK_WEBSEED_BT, TK_WEBSEED_GR });

	private byte[]		torrentName;
	private byte[]		torrentNameUtf8;

	private byte[]		comment;
	private URL			announceUrl;
	private final TOTorrentAnnounceURLGroupImpl	announceGroup = new TOTorrentAnnounceURLGroupImpl(this);

	private long		pieceLength;
	private byte[][]	pieces;
	private int			numberOfPieces;
	
	private byte[]		torrentHashOverride;

	private byte[]		torrentHash;
	private HashWrapper	torrentHashWrapper;
	
	private boolean				simpleTorrent;
	private TOTorrentFileImpl[]	files;

	private long				creationDate;
	private byte[]				createdBy;

	private Map					additionalProperties 		= new LightHashMap(4);
	private final Map			additionalInfoProperties	= new LightHashMap(4);

	private boolean				created;
	private boolean				serialising;

	private List<TOTorrentListener>	listeners;

	protected final AEMonitor thisMon 	= new AEMonitor("TOTorrent");

	/**
	 * Constructor for deserialisation
	 */
	protected TOTorrentImpl() {
	}

	/**
	 * Constructor for creation
	 */
	protected TOTorrentImpl(String _torrent_name, URL _announce_url, boolean _simple_torrent)
			throws TOTorrentException {
		created = true;
		try {
			torrentName = _torrent_name.getBytes(Constants.DEFAULT_ENCODING);
			torrentNameUtf8 = torrentName;
			setAnnounceURL(_announce_url);
			simpleTorrent = _simple_torrent;
		} catch (UnsupportedEncodingException e) {
			throw (new TOTorrentException("Unsupported encoding for '" + _torrent_name + "'",
					TOTorrentException.RT_UNSUPPORTED_ENCODING));
		}
	}

	public void serialiseToBEncodedFile(
		final File		output_file )

		throws TOTorrentException
	{
			// we have to defer marking as created until some kind of persisting occurs as we don't know that the info-hash is "permanent" until#
			// this point (external code can set info-hash internal properties between create + complete )

		if (created) {

			TorrentUtils.addCreatedTorrent(this);
		}

		byte[]	res = serialiseToByteArray();

        BufferedOutputStream bos = null;

		try {
			File parent = output_file.getParentFile();
			if (parent == null) {
				throw new TOTorrentException("Path '" + output_file + "' is invalid", TOTorrentException.RT_WRITE_FAILS);
			}

			// We would expect this to be normally true most of the time.
			if (!parent.isDirectory()) {

				// Try to create a directory.
				boolean dir_created = FileUtil.mkdirs(parent);

				// Something strange going on...
				if (!dir_created) {

					// Does it exist already?
					if (parent.exists()) {

						// And it really isn't a directory?
						if (!parent.isDirectory()) {

							// How strange.
							throw new TOTorrentException("Path '" + output_file + "' is invalid", TOTorrentException.RT_WRITE_FAILS);

						}

						// It is a directory which does exist. But we tested for that earlier. Perhaps it has been created in the
						// meantime.
						else {
							/* do nothing */
						}
					}

					// It doesn't exist, and we couldn't create it.
					else {
						throw new TOTorrentException("Failed to create directory '" + parent + "'", TOTorrentException.RT_WRITE_FAILS);
					}
				} // end if (!dir_created)

			} // end if (!parent.isDirectory)


			File temp = new File( parent, output_file.getName() + ".saving");

			if (temp.exists()) {

				if (!temp.delete()) {

					throw (new TOTorrentException("Insufficient permissions to delete '" + temp + "'", TOTorrentException.RT_WRITE_FAILS));
				}
			} else {

				boolean	ok = false;

				try {
					ok = temp.createNewFile();

				} catch (Throwable e) {
				}

				if (!ok) {

					throw (new TOTorrentException("Insufficient permissions to write '" + temp + "'", TOTorrentException.RT_WRITE_FAILS));

				}
			}

            FileOutputStream fos = new FileOutputStream(temp, false);

            bos = new BufferedOutputStream(fos, 8192);

            bos.write(res);

            bos.flush();

		  		// thinking about removing this - just do so for CVS for the moment

			if (!Constants.isCVSVersion()) {

				fos.getFD().sync();
			}

            bos.close();

            bos = null;

              //only use newly saved file if it got this far, i.e. it was written successfully

            if (temp.length() > 1L) {
            	output_file.delete(); // Will fail silently if it doesn't exist.
                temp.renameTo(output_file);
            }

		} catch (TOTorrentException e) {

			throw (e);

		} catch ( Throwable e) {

			throw (new TOTorrentException( 	"Failed to serialise torrent: " + Debug.getNestedExceptionMessage(e),
											TOTorrentException.RT_WRITE_FAILS ));

		} finally {

			if (bos != null) {

				try {
					bos.close();

				} catch (IOException e) {

					Debug.printStackTrace(e);
				}
			}
		}
	}

	protected byte[]
	serialiseToByteArray()

		throws TOTorrentException
	{
		if (created) {

			TorrentUtils.addCreatedTorrent(this);
		}

		Map	root = serialiseToMap();

		try {
			return (BEncoder.encode( root));

		} catch (IOException e) {

			throw ( 	new TOTorrentException(
							"Failed to serialise torrent: " + Debug.getNestedExceptionMessage(e),
							TOTorrentException.RT_WRITE_FAILS ));

		}
	}

	public Map serialiseToMap()
			throws TOTorrentException
	{
		// protect against recursion when getting the hash
		if (created && !serialising) {
			try {
				serialising	= true;	// not thread safe but we can live without the hassle of using TLS or whatever
				TorrentUtils.addCreatedTorrent(this);
			} finally {
				serialising = false;
			}
		}
		Map	root = new HashMap();
		// seen a NPE here, not sure of cause so handling null announce_url in case
		writeStringToMetaData( root, TK_ANNOUNCE, (announceUrl==null?TorrentUtils.getDecentralisedEmptyURL():announceUrl).toString());
		TOTorrentAnnounceURLSet[] sets = announceGroup.getAnnounceURLSets();
		if (sets.length > 0) {
			List	announce_list = new ArrayList();
			for (int i=0;i<sets.length;i++) {
				TOTorrentAnnounceURLSet	set = sets[i];
				URL[]	urls = set.getAnnounceURLs();
				if (urls.length == 0) {
					continue;
				}
				List sub_list = new ArrayList();
				announce_list.add(sub_list);
				for (int j=0;j<urls.length;j++) {
					sub_list.add( writeStringToMetaData( urls[j].toString()));
				}
			}
			if (announce_list.size() > 0) {
				root.put(TK_ANNOUNCE_LIST, announce_list);
			}
		}
		if (comment != null) {
			root.put(TK_COMMENT, comment);
		}
		if (creationDate != 0) {
			root.put(TK_CREATION_DATE, new Long(creationDate));
		}
		if (createdBy != null) {
			root.put(TK_CREATED_BY, createdBy);
		}
		Map info = new HashMap();
		root.put(TK_INFO, info);
		info.put(TK_PIECE_LENGTH, new Long(pieceLength));
		if (pieces == null) {
			throw (new TOTorrentException("Pieces is null", TOTorrentException.RT_WRITE_FAILS));
		}
		byte[]	flat_pieces = new byte[pieces.length*20];
		for (int i=0;i<pieces.length;i++) {
			System.arraycopy(pieces[i], 0, flat_pieces, i*20, 20);
		}
		info.put(TK_PIECES, flat_pieces);
		info.put(TK_NAME, torrentName);
		if (torrentNameUtf8 != null) {
			info.put(TK_NAME_UTF8, torrentNameUtf8);
		}
		if (torrentHashOverride != null) {
			info.put(TK_HASH_OVERRIDE, torrentHashOverride);
		}
		if (simpleTorrent) {
			TOTorrentFile	file = files[0];
			info.put( TK_LENGTH, new Long(file.getLength()));
		} else {
			List	meta_files = new ArrayList();
			info.put(TK_FILES, meta_files);
			for (int i=0;i<files.length;i++) {
				TOTorrentFileImpl	file	= files[i];
				Map	file_map = file.serializeToMap();
				meta_files.add(file_map);
			}
		}
		Iterator info_it = additionalInfoProperties.keySet().iterator();
		while (info_it.hasNext()) {
			String	key = (String)info_it.next();
			info.put(key, additionalInfoProperties.get( key));
		}
		Iterator it = additionalProperties.keySet().iterator();
		while (it.hasNext()) {
			String	key = (String)it.next();
			Object	value = additionalProperties.get(key);
			if (value != null) {
				root.put(key, value);
			}
		}
		return (root);
	}

	public void serialiseToXMLFile(
	  File		file )

	  throws TOTorrentException
	{
		if (created) {

			TorrentUtils.addCreatedTorrent(this);
		}

		TOTorrentXMLSerialiser	serialiser = new TOTorrentXMLSerialiser(this);

		serialiser.serialiseToFile(file);
	}

	public byte[]
	getName() {
		return (torrentName);
	}

	protected void setName(
		byte[]	_name) {
		torrentName	= _name;
	}

	public String getUTF8Name() {
		try {
			return torrentNameUtf8 == null ? null : new String(torrentNameUtf8,
					"utf8");
		} catch (UnsupportedEncodingException e) {
			return null;
		}
	}

	protected void setNameUTF8(
		byte[]	_name) {
		torrentNameUtf8	= _name;
	}

	public boolean isSimpleTorrent() {
		return (simpleTorrent);
	}

	public byte[]
	getComment() {
		return (comment);
	}

	protected void setComment(
		byte[]		_comment )

	{
		comment = _comment;
	}

	public void setComment(
		String	_comment) {
		try {

			byte[]	utf8_comment = _comment.getBytes(Constants.DEFAULT_ENCODING);

			setComment(utf8_comment);

			setAdditionalByteArrayProperty(TK_COMMENT_UTF8, utf8_comment);

		} catch (UnsupportedEncodingException e) {

			Debug.printStackTrace(e);

			comment = null;
		}
	}

	public URL
	getAnnounceURL() {
		return (announceUrl);
	}

	public boolean setAnnounceURL(
		URL		url) {
		URL newURL = anonymityTransform(url);
		String s0 = (newURL == null) ? "" : newURL.toString();
		String s1 = (announceUrl == null) ? "" : announceUrl.toString();
		if (s0.equals(s1))
			return false;

		if (newURL == null) {

				// anything's better than null...

			newURL = TorrentUtils.getDecentralisedEmptyURL();
		}

		announceUrl	= StringInterner.internURL(newURL);

		fireChanged(TOTorrentListener.CT_ANNOUNCE_URLS);

		return true;
	}

	public boolean isDecentralised() {
		return ( TorrentUtils.isDecentralised( getAnnounceURL()));
	}

	public long getCreationDate() {
		return (creationDate);
	}

	public void setCreationDate(
		long		_creation_date) {
			// supposed to be in seconds, not millis. Some torrents have millis so try and
			// fix it

		long	now_secs = SystemTime.getCurrentTime()/1000;

		if (_creation_date > now_secs + 100*365*24*60*60L) {

			_creation_date = _creation_date/1000;
		}

		creationDate 	= _creation_date;
	}

	public void setCreatedBy(
		byte[]		_created_by) {
		createdBy	= _created_by;
	}

	protected void setCreatedBy(
		String		_created_by) {
		try {

			setCreatedBy(_created_by.getBytes( Constants.DEFAULT_ENCODING));

		} catch (UnsupportedEncodingException e) {

			Debug.printStackTrace(e);

			createdBy = null;
		}
	}

	public byte[]
	getCreatedBy() {
		return (createdBy);
	}

	public boolean isCreated() {
		return (created);
	}

	public byte[] getHash() throws TOTorrentException {
		
		if (torrentHash == null) {
			Map root = serialiseToMap();
			Map info = (Map) root.get(TK_INFO);
			setHashFromInfo(info);
		}
		
		//if (SingleCounter0.getInstance().getAndIncreaseCount() == 1) {
		//Log.d(TAG, "getHash() is called...");
		//Log.d(TAG, "torrentHash = " + Util.toHexString(torrentHash));
		//}
		
		return (torrentHash);
	}

	public HashWrapper getHashWrapper() throws TOTorrentException {
		if (torrentHashWrapper == null) {
			getHash();
		}
		return (torrentHashWrapper);
	}

	public boolean hasSameHashAs(
		TOTorrent		other) {
		try {
			byte[]	other_hash = other.getHash();
			return (Arrays.equals( getHash(), other_hash));
		} catch (TOTorrentException e) {
			Debug.printStackTrace(e);
			return (false);
		}
	}

	protected void setHashFromInfo(Map info) throws TOTorrentException {
		try {
			if (torrentHashOverride == null) {
				SHA1Hasher s = new SHA1Hasher();
				torrentHash = s.calculateHash(BEncoder.encode(info));
			} else {
				torrentHash = torrentHashOverride;
			}
			torrentHashWrapper = new HashWrapper(torrentHash);
		} catch (Throwable e) {
			throw (new TOTorrentException(
					"Failed to calculate hash: " + Debug.getNestedExceptionMessage(e),
					TOTorrentException.RT_HASH_FAILS)
			);
		}
	}

	public void setHashOverride(byte[] hash) throws TOTorrentException {
		if (torrentHashOverride != null) {
			if (Arrays.equals(hash, torrentHashOverride)) {
				return;
			} else {
				throw (new TOTorrentException("Hash override can only be set once", TOTorrentException.RT_HASH_FAILS));
			}
		}
		/* support this for fixing borked torrents
		if (!TorrentUtils.isDecentralised( announce_url)) {
			throw (new TOTorrentException(
						"Hash override can only be set on decentralised torrents",
						TOTorrentException.RT_HASH_FAILS ));
		}
		*/
		torrentHashOverride = hash;
		torrentHash	= null;
		getHash();
	}

	protected byte[] getHashOverride() {
		return (torrentHashOverride);
	}

	public void setPrivate(
		boolean	_private_torrent )

		throws TOTorrentException
	{
		additionalInfoProperties.put( TK_PRIVATE, new Long(_private_torrent?1:0));

			// update torrent hash

		torrentHash	= null;

		getHash();
	}

	public boolean getPrivate() {
		Object o = additionalInfoProperties.get(TK_PRIVATE);

		if (o instanceof Long) {

			return (((Long)o).intValue() != 0);
		}

		return (false);
	}

	public TOTorrentAnnounceURLGroup
	getAnnounceURLGroup() {
		return (announceGroup);
	}

	protected void addTorrentAnnounceURLSet(
		URL[]		urls) {
		announceGroup.addSet(new TOTorrentAnnounceURLSetImpl( this, urls));
	}

	public long getSize() {
		long	res = 0;

		for (int i=0;i<files.length;i++) {

			res += files[i].getLength();
		}

		return (res);
	}

	public long getPieceLength() {
		return (pieceLength);
	}

	protected void setPieceLength(
		long	_length) {
		pieceLength	= _length;
	}

	public int getNumberOfPieces() {
			// to support buggy torrents with extraneous pieces (they seem to exist) we calculate
			// the required number of pieces rather than the using the actual. Note that we
			// can't adjust the pieces array itself as this results in incorrect torrent hashes
			// being derived later after a save + restore

		if (numberOfPieces == 0) {

			numberOfPieces = (int)((getSize() + (pieceLength-1)) / pieceLength);
		}

		return (numberOfPieces);
	}

	public byte[][]	getPieces() {
		return (pieces);
	}

	public void setPieces(byte[][]	_pieces) {
		pieces = _pieces;
	}

	public int getFileCount() {
		return (files.length);
	}

	public TOTorrentFile[]
	getFiles() {
		return (files);
	}

	protected void setFiles(
		TOTorrentFileImpl[]		_files) {
		files	= _files;
	}

	protected boolean getSimpleTorrent() {
		return (simpleTorrent);
	}

	protected void setSimpleTorrent(
		boolean	_simple_torrent) {
		simpleTorrent	= _simple_torrent;
	}

	protected Map
	getAdditionalProperties() {
		return (additionalProperties);
	}

	public void setAdditionalStringProperty(
		String		name,
		String		value) {
		try {

			setAdditionalByteArrayProperty(name, writeStringToMetaData( value));

		} catch (TOTorrentException e) {

				// hide encoding exceptions as default encoding must be available

			Debug.printStackTrace(e);
		}
	}

	public String getAdditionalStringProperty(
		String		name) {
		try {

			return ( readStringFromMetaData( getAdditionalByteArrayProperty(name)));

		} catch (TOTorrentException e) {

				// hide encoding exceptions as default encoding must be available

			Debug.printStackTrace(e);

			return (null);
		}
	}

	public void setAdditionalByteArrayProperty(
		String		name,
		byte[]		value) {
		additionalProperties.put(name, value);
	}

	public void setAdditionalProperty(
		String		name,
		Object		value) {
		if (value instanceof String) {

			setAdditionalStringProperty(name,(String)value);

		} else {

			additionalProperties.put(name, value);
		}
	}

	public byte[]
	getAdditionalByteArrayProperty(
		String		name) {
		Object	obj = additionalProperties.get(name);

		if (obj instanceof byte[]) {

			return ((byte[])obj);
		}

		return (null);
	}

	public void setAdditionalLongProperty(
		String		name,
		Long		value) {
		additionalProperties.put(name, value);
	}

	public Long
	getAdditionalLongProperty(
		String		name) {
		Object	obj = additionalProperties.get(name);

		if (obj instanceof Long) {

			return ((Long)obj);
		}

		return (null);
	}

	public void setAdditionalListProperty(
		String		name,
		List		value) {
		additionalProperties.put(name, value);
	}

	public List
	getAdditionalListProperty(
		String		name) {
		Object	obj = additionalProperties.get(name);

		if (obj instanceof List) {

			return ((List)obj);
		}

		return (null);
	}

	public void setAdditionalMapProperty(
		String		name,
		Map 		value) {
		additionalProperties.put(name, value);
	}

	public Map
	getAdditionalMapProperty(
		String		name) {
		Object	obj = additionalProperties.get(name);

		if (obj instanceof Map) {

			return ((Map)obj);
		}

		return (null);
	}

	public Object getAdditionalProperty(
		String		name) {
		return (additionalProperties.get(name));
	}

	public void removeAdditionalProperty(
		String name) {
		additionalProperties.remove(name);
	}

	public void removeAdditionalProperties() {
		Map	new_props = new HashMap();

		Iterator it = additionalProperties.keySet().iterator();

		while (it.hasNext()) {

			String	key = (String)it.next();

			if (TK_ADDITIONAL_OK_ATTRS.contains(key)) {

				new_props.put(key, additionalProperties.get( key));
			}
		}

		additionalProperties = new_props;
	}

	protected void addAdditionalProperty(
		String			name,
		Object			value) {
		additionalProperties.put(name, value);
	}

	protected void addAdditionalInfoProperty(
		String			name,
		Object			value) {
		additionalInfoProperties.put(name, value);
	}

	protected Map
	getAdditionalInfoProperties() {
		return (additionalInfoProperties);
	}

	protected String readStringFromMetaData(
		Map		meta_data,
		String	name )

		throws TOTorrentException
	{
		Object	obj = meta_data.get(name);

		if (obj instanceof byte[]) {

			return (readStringFromMetaData((byte[])obj));
		}

		return (null);
	}

	protected String readStringFromMetaData(
		byte[]		value )

		throws TOTorrentException
	{
		try {
			if (value == null) {

				return (null);
			}

			return (	new String(value, Constants.DEFAULT_ENCODING ));

		} catch (UnsupportedEncodingException e) {

			throw (new TOTorrentException( 	"Unsupported encoding for '" + value + "'",
											TOTorrentException.RT_UNSUPPORTED_ENCODING));
		}
	}

	protected void writeStringToMetaData(
		Map		meta_data,
		String	name,
		String	value )

		throws TOTorrentException
	{
		meta_data.put(name, writeStringToMetaData( value));
	}

	protected byte[]
	writeStringToMetaData(
		String		value )

		throws TOTorrentException
	{
		try {

			return (	value.getBytes(Constants.DEFAULT_ENCODING));

		} catch (UnsupportedEncodingException e) {

			throw (new TOTorrentException( 	"Unsupported encoding for '" + value + "'",
											TOTorrentException.RT_UNSUPPORTED_ENCODING));
		}
	}

	protected URL
	anonymityTransform(
		URL		url) {
		/*
		 * 	hmm, doing this is harder than it looks as we have issues hosting
		 *  (both starting tracker instances and also short-cut loopback for seeding
		 *  leave as is for the moment
		if (HostNameToIPResolver.isNonDNSName( url.getHost())) {

			// remove the port as it is uninteresting and could leak information about the
			// tracker

			String	url_string = url.toString();

			String	port_string = ":" + (url.getPort()==-1?url.getDefaultPort():url.getPort());

			int	port_pos = url_string.indexOf(":" + url.getPort());

			if (port_pos != -1) {

				try {

					return (new URL( url_string.substring(0,port_pos) + url_string.substring(port_pos+port_string.length())));

				} catch ( MalformedURLException e) {

					Debug.printStackTrace(e);
				}
			}
		}
		*/

		return (url);
	}

	public void print() {
		try {
			byte[]	hash = getHash();

			System.out.println("name = " + torrentName);
			System.out.println("announce url = " + announceUrl);
			System.out.println("announce group = " + announceGroup.getAnnounceURLSets().length);
			System.out.println("creation date = " + creationDate);
			System.out.println("creation by = " + createdBy);
			System.out.println("comment = " + comment);
			System.out.println("hash = " + ByteFormatter.nicePrint( hash));
			System.out.println("piece length = " + getPieceLength());
			System.out.println("pieces = " + getNumberOfPieces());

			Iterator info_it = additionalInfoProperties.keySet().iterator();

			while (info_it.hasNext()) {

				String	key = (String)info_it.next();
				Object	value = additionalInfoProperties.get(key);

				try {

					System.out.println("info prop '" + key + "' = '" +
										(value instanceof byte[]?new String((byte[])value, Constants.DEFAULT_ENCODING):value.toString()) + "'");
				} catch ( UnsupportedEncodingException e) {

					System.out.println("info prop '" + key + "' = unsupported encoding!!!!");
				}
			}

			Iterator it = additionalProperties.keySet().iterator();

			while (it.hasNext()) {

				String	key = (String)it.next();
				Object	value = additionalProperties.get(key);

				try {

					System.out.println("prop '" + key + "' = '" +
										(value instanceof byte[]?new String((byte[])value, Constants.DEFAULT_ENCODING):value.toString()) + "'");
				} catch ( UnsupportedEncodingException e) {

					System.out.println("prop '" + key + "' = unsupported encoding!!!!");
				}
			}

			if (pieces == null) {

				System.out.println("\tpieces = null");

			} else {
				for (int i=0;i<pieces.length;i++) {

					System.out.println("\t" + ByteFormatter.nicePrint(pieces[i]));
				}
			}

			for (int i=0;i<files.length;i++) {

				byte[][]path_comps = files[i].getPathComponents();

				String	path_str = "";

				for (int j=0;j<path_comps.length;j++) {

					try {

						path_str += (j==0?"":File.separator) + new String(path_comps[j], Constants.DEFAULT_ENCODING);

					} catch (UnsupportedEncodingException e) {

						System.out.println("file - unsupported encoding!!!!");
					}
				}

				System.out.println("\t" + path_str + " (" + files[i].getLength() + ")");
			}
		} catch (TOTorrentException e) {

			Debug.printStackTrace(e);
		}
	}

	protected void fireChanged(
		int	type) {
		List<TOTorrentListener> to_fire = null;

		try {
			thisMon.enter();

			if (listeners != null) {

				to_fire = new ArrayList<TOTorrentListener>(listeners);
			}
		} finally {

			thisMon.exit();
		}

		if (to_fire != null) {

			for (TOTorrentListener l: to_fire) {

				try {
					l.torrentChanged(this, type);

				} catch (Throwable e) {

					Debug.out(e);
				}
			}
		}
	}

 	public void addListener(
		TOTorrentListener		l) {
 		try {
			thisMon.enter();

			if (listeners == null) {

				listeners = new ArrayList<TOTorrentListener>();
			}

			listeners.add(l);

		} finally {

			thisMon.exit();
		}
	}

	public void removeListener(
		TOTorrentListener		l) {
 		try {
			thisMon.enter();

			if (listeners != null) {

				listeners.remove(l);

				if (listeners.size() == 0) {

					listeners = null;
				}
			}
		} finally {

			thisMon.exit();
		}
	}

	public AEMonitor
	getMonitor() {
		return (thisMon);
	}

	/* (non-Javadoc)
	 * @see org.gudy.azureus2.core3.logging.LogRelation#getLogRelationText()
	 */
	public String getRelationText() {
		return "Torrent: '" + new String(torrentName) + "'";
	}

	/* (non-Javadoc)
	 * @see org.gudy.azureus2.core3.logging.LogRelation#queryForClass(java.lang.Class)
	 */
	public Object[] getQueryableInterfaces() {
		// yuck
		try {
			return new Object[] { AzureusCoreFactory.getSingleton()
					.getGlobalManager().getDownloadManager(this) };
		} catch (Exception e) {
		}

		return null;
	}
}