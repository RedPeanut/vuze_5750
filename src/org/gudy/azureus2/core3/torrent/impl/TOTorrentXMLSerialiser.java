/*
 * File    : TOTorrentXMLSerialiser.java
 * Created : 13-Oct-2003
 * By      : stuff
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

/**
 * @author parg
 *
 */

import java.io.*;
import java.net.*;
import java.util.*;

import org.gudy.azureus2.core3.torrent.*;
import org.gudy.azureus2.core3.xml.util.*;

public class
TOTorrentXMLSerialiser
	extends XUXmlWriter
{
	protected final TOTorrentImpl		torrent;

	protected TOTorrentXMLSerialiser(
		TOTorrentImpl		_torrent) {
		torrent = _torrent;
	}

	protected void serialiseToFile(
		File		file )

		throws TOTorrentException
	{
		resetIndent();

		try {

			setOutputStream(new FileOutputStream( file));

			writeRoot();

		} catch (IOException e) {

			throw (new TOTorrentException("TOTorrentXMLSerialiser: file write fails: " + e.toString(),
											TOTorrentException.RT_WRITE_FAILS ));

		} finally {

			try {

				closeOutputStream();

			} catch (Throwable e) {

				throw (new TOTorrentException("TOTorrentXMLSerialiser: file close fails: " + e.toString(),
												TOTorrentException.RT_WRITE_FAILS ));
			}
		}
	}

	protected void writeRoot()

		throws TOTorrentException
	{
		writeLineRaw("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
		writeLineRaw("<tor:TORRENT");
		writeLineRaw("\txmlns:tor=\"http://azureus.sourceforge.net/files\"");
		writeLineRaw("\txmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"");
		writeLineRaw("\txsi:schemaLocation=\"http://azureus.sourceforge.net/files http://azureus.sourceforge.net/files/torrent.xsd\">");

		try {
			indent();

			writeTag("ANNOUNCE_URL",  torrent.getAnnounceURL().toString());

			TOTorrentAnnounceURLSet[] sets = torrent.getAnnounceURLGroup().getAnnounceURLSets();

			if (sets.length > 0) {

				writeLineRaw("<ANNOUNCE_LIST>");

				try {
					indent();

					for (int i=0;i<sets.length;i++) {

						TOTorrentAnnounceURLSet	set = sets[i];

						URL[]	urls = set.getAnnounceURLs();

						writeLineRaw("<ANNOUNCE_ENTRY>");

						try {
							indent();

							for (int j=0;j<urls.length;j++) {

								writeTag("ANNOUNCE_URL",  urls[j].toString());
							}
						} finally {

							exdent();
						}

						writeLineRaw("</ANNOUNCE_ENTRY>");
					}
				} finally {
					exdent();
				}

				writeLineRaw("</ANNOUNCE_LIST>");
			}

			byte[] comment = torrent.getComment();

			if (comment != null) {

				writeLocalisableTag("COMMENT", comment);
			}

			long creation_date = torrent.getCreationDate();

			if (creation_date != 0) {

				writeTag("CREATION_DATE", creation_date);
			}

			byte[]	created_by = torrent.getCreatedBy();

			if (created_by != null) {

				writeLocalisableTag("CREATED_BY", created_by);
			}

			writeTag("TORRENT_HASH", torrent.getHash());

			byte[]	hash_override = torrent.getHashOverride();

			if (hash_override != null) {

				writeTag("TORRENT_HASH_OVERRIDE", hash_override);
			}

			writeInfo();

			Map additional_properties = torrent.getAdditionalProperties();

			Iterator it = additional_properties.keySet().iterator();

			while (it.hasNext()) {

				String	key = (String)it.next();

				writeGenericMapEntry(key, additional_properties.get( key));
			}

		} finally {

			exdent();
		}
		writeLineRaw("</tor:TORRENT>");
	}

	protected void writeInfo()

		throws TOTorrentException
	{
		writeLineRaw("<INFO>");

		try {
			indent();

			writeLocalisableTag("NAME", torrent.getName());

			writeTag("PIECE_LENGTH", torrent.getPieceLength());

			TOTorrentFileImpl[] files = (TOTorrentFileImpl[])torrent.getFiles();

			if (torrent.isSimpleTorrent()) {

				writeTag("LENGTH", files[0].getLength());

			} else {

				writeLineRaw("<FILES>");

				try {
					indent();

					for (int i=0;i<files.length;i++) {

						writeLineRaw("<FILE>");

						try {

							indent();

							TOTorrentFileImpl	file	= files[i];

							writeTag("LENGTH", file.getLength());

							writeLineRaw("<PATH>");

							try {

								indent();

								byte[][]	path_comps = file.getPathComponents();

								for (int j=0;j<path_comps.length;j++) {

									writeLocalisableTag("COMPONENT", path_comps[j]);
								}

							} finally {

								exdent();
							}

							writeLineRaw("</PATH>");

							Map additional_properties = file.getAdditionalProperties();

							if (additional_properties != null) {

								Iterator prop_it = additional_properties.keySet().iterator();

								while (prop_it.hasNext()) {

									String	key = (String)prop_it.next();

									writeGenericMapEntry(key, additional_properties.get( key));
								}
							}
						} finally {

							exdent();
						}

						writeLineRaw("</FILE>");
					}
				} finally {

					exdent();
				}

				writeLineRaw("</FILES>");
			}

			writeLineRaw("<PIECES>");

			try {
				indent();

				byte[][]	pieces = torrent.getPieces();

				for (int i=0;i<pieces.length;i++) {

					writeGeneric(pieces[i]);
				}
			} finally {
				exdent();
			}

			writeLineRaw("</PIECES>");

			Map additional_properties = torrent.getAdditionalInfoProperties();

			Iterator it = additional_properties.keySet().iterator();

			while (it.hasNext()) {

				String	key = (String)it.next();

				writeGenericMapEntry(key, additional_properties.get( key));
			}


		} finally {
			exdent();
		}

		writeLineRaw("</INFO>");
	}
}
