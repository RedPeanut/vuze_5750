/*
 * File    : GenericParameter.java
 * Created : Nov 21, 2003
 * By      : epall
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

package org.gudy.azureus2.pluginsimpl.local.ui.config;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.pluginsimpl.local.PluginConfigImpl;
import org.gudy.azureus2.plugins.ui.config.StringParameter;

public class StringParameterImpl extends ParameterImpl implements StringParameter
{
	private String 	defaultValue;
	private int		line_count;

	public StringParameterImpl(PluginConfigImpl config,String key, String label, String defaultValue) {
		super(config,key, label);
		config.notifyParamExists(getKey());
		COConfigurationManager.setStringDefault(getKey(), defaultValue);
		this.defaultValue = defaultValue;
	}

	/**
	 * @return Returns the defaultValue.
	 */
	public String getDefaultValue() {
		return defaultValue;
	}

	public String getValue() {
		return ( config.getUnsafeStringParameter( getKey(), getDefaultValue()));
	}

	public void setValue(
		String	s) {
		config.setUnsafeStringParameter(getKey(), s);
	}

	public void setMultiLine(
		int	visible_line_count) {
		line_count = visible_line_count;
	}

	public int getMultiLine() {
		return (line_count);
	}
}
