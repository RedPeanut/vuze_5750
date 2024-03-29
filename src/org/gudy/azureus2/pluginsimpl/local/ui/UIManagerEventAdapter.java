/*
 * Created on 10-Jan-2006
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

package org.gudy.azureus2.pluginsimpl.local.ui;

import org.gudy.azureus2.plugins.PluginInterface;
import org.gudy.azureus2.plugins.ui.UIManagerEvent;

public class UIManagerEventAdapter
	implements UIManagerEvent {
	
	private PluginInterface		pi;
	private int					type;
	private Object				data;
	private Object				result;

	public UIManagerEventAdapter(
		PluginInterface	_pi,
		int				_type,
		Object			_data) {
		pi			= _pi;
		type		= _type;
		data		= _data;
	}

		/**
		 * @return very occasionally this may be NULL
		 */

	protected PluginInterface
	getPluginInterface() {
		return (pi);
	}

	public int getType() {
		return (type);
	}

	public Object getData() {
		return (data);
	}

	public void setResult(
		Object	_result) {
		result	= _result;
	}

	public Object getResult() {
		return (result);
	}
}
