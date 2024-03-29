/*
 * Created on Mar 26, 2015
 * Created by Paul Gardner
 *
 * Copyright 2015 Azureus Software, Inc.  All rights reserved.
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


package org.gudy.azureus2.pluginsimpl.local.ui.config;

import java.util.*;

import org.gudy.azureus2.plugins.ui.config.ParameterGroup;
import org.gudy.azureus2.plugins.ui.config.ParameterTabFolder;

public class
ParameterTabFolderImpl
	extends ParameterImpl
	implements ParameterTabFolder

{
	private List<ParameterGroupImpl>	groups = new ArrayList<ParameterGroupImpl>();

	public
	ParameterTabFolderImpl() {
		super(null, "", "");
	}

	public void addTab(
		ParameterGroup		_group) {
		ParameterGroupImpl	group = (ParameterGroupImpl)_group;

		groups.add(group);

		group.setTabFolder(this);
	}

	public void removeTab(
		ParameterGroup		group) {

	}
}
