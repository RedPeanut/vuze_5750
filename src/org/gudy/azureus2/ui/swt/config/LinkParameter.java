/*
 * Created on 17-Jun-2004
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

package org.gudy.azureus2.ui.swt.config;



 /** @author parg
 *
 */

import org.eclipse.swt.*;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.widgets.*;
import org.gudy.azureus2.ui.swt.Messages;
import org.gudy.azureus2.ui.swt.Utils;
import org.gudy.azureus2.ui.swt.mainwindow.Colors;


public class
LinkParameter
	extends Parameter
{
	Label		link_label;

  public
  LinkParameter(
  	Composite composite,
	final String name_resource)
  {
  	super(name_resource);
    link_label = new Label(composite, SWT.NULL);
    Messages.setLanguageText(link_label, name_resource);
    link_label.setCursor(link_label.getDisplay().getSystemCursor(SWT.CURSOR_HAND));
    link_label.setForeground(Colors.blue);
    link_label.addMouseListener(new MouseAdapter() {
      public void mouseDoubleClick(MouseEvent arg0) {
        fire();
      }
      public void mouseUp(MouseEvent arg0) {
        fire();
      }
    });
  }


  protected void
  fire()
  {
	  for (int i=0;i<change_listeners.size();i++) {

		((ParameterChangeListener)change_listeners.get(i)).parameterChanged(this,false);
	}
  }

  public void setLayoutData(Object layoutData) {
  	Utils.adjustPXForDPI(layoutData);
	  link_label.setLayoutData(layoutData);
  }

  public Control getControl()
  {
	 return link_label;
  }

  public void setValue(Object value) {
  }
}