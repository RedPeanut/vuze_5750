/**
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
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

package org.gudy.azureus2.ui.swt;

import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;

import org.gudy.azureus2.core3.util.AERunnable;

/**
 *
 * SWT {@link Listener} that invokes event handling off of the SWT Thread
 *
 * @author TuxPaper
 * @created Jan 20, 2015
 *
 */
public abstract class ListenerGetOffSWT
	implements Listener
{
	// @see org.eclipse.swt.widgets.Listener#handleEvent(org.eclipse.swt.widgets.Event)
	public void handleEvent(final Event event) {
		Utils.getOffOfSWTThread(new AERunnable() {
			public void runSupport() {
				handleEventOffSWT(event);
			}
		});
	}

	abstract void handleEventOffSWT(Event event);
}
