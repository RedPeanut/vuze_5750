/*
 * Created on 9 juil. 2003
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

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.*;
import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.util.AERunnable;
import org.gudy.azureus2.ui.swt.Utils;

/**
 * @author Olivier
 *
 */
public class StringAreaParameter extends Parameter{

  private String name;
  private Text inputField;
  private String defaultValue;

  public StringAreaParameter(Composite composite,final String name) {
    this(composite, name, COConfigurationManager.getStringParameter(name));
  }

  public StringAreaParameter(Composite composite,final String name, String defaultValue) {
  	super(name);
    this.name = name;
    this.defaultValue = defaultValue;
    inputField = new Text(composite, SWT.BORDER | SWT.WRAP | SWT.MULTI | SWT.V_SCROLL) {
  		// I know what I'm doing. Maybe ;)
  		public void checkSubclass() {
  		}

    	// @see org.eclipse.swt.widgets.Text#computeSize(int, int, boolean)
    	public Point computeSize(int wHint, int hHint, boolean changed) {
    		// Text widget, at least on Windows, forces the preferred width
    		// to the width of the text inside of it
    		// Fix this by forcing to LayoutData's minWidth
    		if (hHint==0 && !isVisible()) {

    			return (new Point( 0, 0));
    		}
    		Point pt = super.computeSize(wHint, hHint, changed);

    		if (wHint == SWT.DEFAULT) {
      		Object ld = getLayoutData();
      		if (ld instanceof GridData) {
      			if (((GridData)ld).grabExcessHorizontalSpace) {
      				pt.x = 10;
      			}
      		}
    		}


    		return pt;
    	}
    };

    String value = COConfigurationManager.getStringParameter(name, defaultValue);
    inputField.setText(value);
    inputField.addListener(SWT.Verify, new Listener() {
        public void handleEvent(Event e) {
          e.doit = COConfigurationManager.verifyParameter(name, e.text);
        }
    });

    inputField.addListener(SWT.FocusOut, new Listener() {
        public void handleEvent(Event event) {
        	checkValue();
        }
    });

    inputField.addKeyListener(
			new KeyAdapter() {
				public void keyPressed(
					KeyEvent event ) {
					int key = event.character;

					if (key <= 26 && key > 0) {

						key += 'a' - 1;
					}

					if (key == 'a' && event.stateMask == SWT.MOD1) {

						event.doit = false;

						inputField.selectAll();
					}
				}
			});
  }

  public int
  getPreferredHeight(
	int	line_count )
  {
	  return (inputField.getLineHeight() * line_count);
  }

  protected void
  checkValue()
  {
	  String	old_value = COConfigurationManager.getStringParameter(name, defaultValue);
	  String	new_value = inputField.getText();

	  if (!old_value.equals(new_value)) {
	      COConfigurationManager.setParameter(name,new_value);

	      if (change_listeners != null) {
	        for (int i=0;i<change_listeners.size();i++) {
	          ((ParameterChangeListener)change_listeners.get(i)).parameterChanged(StringAreaParameter.this,false);
	        }
	      }
	  }
  }

  public void setLayoutData(Object layoutData) {
  	Utils.adjustPXForDPI(layoutData);
    inputField.setLayoutData(layoutData);
  }

  public void setValue(final String value) {
		Utils.execSWTThread(new AERunnable() {
			public void runSupport() {
				if (inputField == null || inputField.isDisposed()
						|| inputField.getText().equals(value)) {
					return;
				}
				inputField.setText(value);
			}
		});

		if (!COConfigurationManager.getStringParameter(name).equals(value)) {
			COConfigurationManager.setParameter(name, value);
		}
	}

  public String getValue() {
    return inputField.getText();
  }

  /* (non-Javadoc)
   * @see org.gudy.azureus2.ui.swt.IParameter#getControl()
   */
  public Control getControl() {
    return inputField;
  }

  public void setValue(Object value) {
  	if (value instanceof String) {
  		setValue((String)value);
  	}
  }

  public Object getValueObject() {
  	return COConfigurationManager.getStringParameter(name);
  }
}
