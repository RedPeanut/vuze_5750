/*
 * Created on 25 May 2008
 * Created by Allan Crooks
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
package org.gudy.azureus2.plugins.ui.config;

/**
 * Any class implementing this interface represent an object which is to be used
 * to generate a {@link UIParameter} object. Any plugin wishing to implement this
 * interface shouldn't implement it directly - it will need to implement a
 * sub-interface which defines methods directly related to the user interface
 * you want to integrate with.
 *
 * <p><b>Note:</b> This interface is intended to be implemented by plugins.</p>
 *
 * @author Allan Crooks
 * @since 3.0.5.3
 */
public interface UIParameterContext {

}
