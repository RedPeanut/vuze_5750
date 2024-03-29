/*
 * Created on Dec 2, 2016
 * Created by Paul Gardner
 *
 * Copyright 2016 Azureus Software, Inc.  All rights reserved.
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
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 */


package com.aelitis.azureus.ui.swt.search;


import java.util.Date;

import org.eclipse.swt.graphics.Image;
import org.gudy.azureus2.core3.util.Base32;
import org.gudy.azureus2.core3.util.LightHashMap;

import com.aelitis.azureus.core.metasearch.Engine;
import com.aelitis.azureus.core.metasearch.Result;
import com.aelitis.azureus.ui.swt.utils.SearchSubsResultBase;

public class
SBC_SearchResult
	implements SearchSubsResultBase, SBC_SearchResultsView.ImageLoadListener
{
	private final SBC_SearchResultsView		view;

	private final Engine			engine;
	private final Result			result;

	private final int				content_type;
	private final String			seeds_peers;
	private final long				seeds_peers_sort;
	private final long				votes_comments_sort;
	private final String			votes_comments;

	private LightHashMap<Object,Object>	user_data;

	public
	SBC_SearchResult(
		SBC_SearchResultsView	_view,
		Engine					_engine,
		Result					_result) {
		view	= _view;
		engine	= _engine;
		result	= _result;

		String type = result.getContentType();

		if (type == null || type.length() == 0) {
			content_type = 0;
		} else {
			char c = type.charAt(0);

			if (c == 'v') {
				content_type = 1;
			} else if (c == 'a') {
				content_type = 2;
			} else if (c == 'g') {
				content_type = 3;
			} else {
				content_type = 0;
			}
		}

		int seeds 		= result.getNbSeeds();
		int leechers 	= result.getNbPeers();
		int	super_seeds	= result.getNbSuperSeeds();

		if (super_seeds > 0) {
			seeds += (super_seeds*10);
		}
		seeds_peers = (seeds<0?"--":String.valueOf(seeds)) + "/" + (leechers<0?"--":String.valueOf(leechers));

		if (seeds < 0) {
			seeds = 0;
		} else {
			seeds++;
		}

		if (leechers < 0) {
			leechers = 0;
		} else {
			leechers++;
		}

		seeds_peers_sort = ((seeds&0x7fffffff)<<32) | (leechers & 0xffffffff);

		long votes		= result.getVotes();
		long comments 	= result.getComments();

		if (votes < 0 && comments < 0) {

			votes_comments_sort = 0;
			votes_comments		= null;

		} else {

			votes_comments = (votes<0?"--":String.valueOf(votes)) + "/" + (comments<0?"--":String.valueOf(comments));

			if (votes < 0) {
				votes= 0;
			} else {
				votes++;
			}
			if (comments < 0) {
				comments= 0;
			} else {
				comments++;
			}

			votes_comments_sort = ((votes&0x7fffffff)<<32) | (comments & 0xffffffff);
		}
	}

	public Engine getEngine() {
		return (engine);
	}

	public final String
	getName() {
		return ( result.getName());
	}

	public byte[]
	getHash() {
		String base32_hash = result.getHash();

		if (base32_hash != null) {

			return (Base32.decode( base32_hash));
		}

		return (null);
	}

	public int getContentType() {
		return (content_type);
	}

	public long getSize() {
		return ( result.getSize());
	}

	public String getSeedsPeers() {
		return (seeds_peers);
	}

	public long getSeedsPeersSortValue() {
		return (seeds_peers_sort);
	}

	public String getVotesComments() {
		return (votes_comments);
	}

	public long getVotesCommentsSortValue() {
		return (votes_comments_sort);
	}

	public int getRank() {
		float rank = result.getRank();

		return ((int)( rank*100));
	}

	public String getTorrentLink() {
		String r = result.getTorrentLink();

		if (r == null) {

			r = result.getDownloadLink();
		}

		return (r);
	}

	public String getDetailsLink() {
		return (result.getCDPLink());
	}

	public String getCategory() {
		return (result.getCategory());
	}

	public long getTime() {
		Date date = result.getPublishedDate();

		if (date != null) {

			return ( date.getTime());
		}

		return (0);
	}

	public Image getIcon() {
		return (view.getIcon( engine, this));
	}

	public boolean getRead() {
		return (false);
	}

	public void setRead(
		boolean		read) {
	}

	public void imageLoaded(
		Image		image) {
		view.invalidate(this);
	}

	public void setUserData(
		Object	key,
		Object	data) {
		synchronized(this) {
			if (user_data == null) {
				user_data = new LightHashMap<Object,Object>();
			}
			user_data.put(key, data);
		}
	}

	public Object getUserData(
		Object	key) {
		synchronized(this) {
			if (user_data == null) {
				return (null);
			}
			return (user_data.get( key));
		}
	}
}
