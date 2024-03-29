/*
 * Created on May 6, 2008
 * Created by Paul Gardner
 *
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
 */

package com.aelitis.azureus.core.metasearch.impl;

import java.util.regex.Pattern;

public class FieldRemapping {

	private String  match_str;

	private Pattern match;
	private String replace;

	public FieldRemapping(String match, String replace) {

		this.match_str 	= match;
		this.match 		= Pattern.compile(match);
		this.replace 	=replace;
	}

	public String getMatchString() {
		return (match_str);
	}

	public Pattern getMatch() {
		return match;
	}

	public String getReplacement() {
		return replace;
	}



}
