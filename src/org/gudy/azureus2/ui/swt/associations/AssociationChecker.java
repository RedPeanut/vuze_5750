/*
 * Created on 23-Apr-2004
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

package org.gudy.azureus2.ui.swt.associations;

/**
 * @author parg
 *
 */

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.util.AERunnable;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.platform.PlatformManager;
import org.gudy.azureus2.platform.PlatformManagerCapabilities;
import org.gudy.azureus2.platform.PlatformManagerFactory;
import org.gudy.azureus2.ui.swt.Messages;
import org.gudy.azureus2.ui.swt.Utils;
import org.gudy.azureus2.ui.swt.components.shell.ShellFactory;
import org.gudy.azureus2.ui.swt.mainwindow.SWTThread;

import org.gudy.azureus2.plugins.platform.PlatformManagerException;

public class
AssociationChecker
{
	public static void checkAssociations() {
		try {

		    PlatformManager	platform  = PlatformManagerFactory.getPlatformManager();

		    if (platform.hasCapability(PlatformManagerCapabilities.RegisterFileAssociations)) {

		    	if (COConfigurationManager.getBooleanParameter("config.interface.checkassoc")) {

		    		if (!platform.isApplicationRegistered()) {

		    			new AssociationChecker( platform);
		    		}
		    	}
		    }
		} catch (Throwable e) {

			// Debug.printStackTrace(e);
		}
	}

	protected PlatformManager	platform;
	protected Display			display;
	protected Shell				shell;


	protected AssociationChecker(
		final PlatformManager		_platform) {
		platform	= _platform;

		display = SWTThread.getInstance().getDisplay();

		if (display.isDisposed()) {

			return;
		}

		Utils.execSWTThread(
				new AERunnable() {
					public void runSupport() {
						check();
					}
				});
	}

	protected void check() {
		if (display.isDisposed())
			return;

 		shell = ShellFactory.createMainShell(SWT.DIALOG_TRIM);

 		Utils.setShellIcon(shell);
	 	shell.setText(MessageText.getString("dialog.associations.title"));

	 	GridLayout layout = new GridLayout();
	 	layout.numColumns = 3;

	 	shell.setLayout (layout);

	 	GridData gridData;

	 		// text

		Label user_label = new Label(shell,SWT.NULL);
		Messages.setLanguageText(user_label, "dialog.associations.prompt");
		gridData = new GridData(GridData.FILL_BOTH);
		gridData.horizontalSpan = 3;
		Utils.setLayoutData(user_label, gridData);


	    final Button checkBox = new Button(shell, SWT.CHECK);
	    checkBox.setSelection(true);
		gridData = new GridData(GridData.FILL_BOTH);
		gridData.horizontalSpan = 3;
		Utils.setLayoutData(checkBox, gridData);
		Messages.setLanguageText(checkBox, "dialog.associations.askagain");

		// line

		Label labelSeparator = new Label(shell,SWT.SEPARATOR | SWT.HORIZONTAL);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.horizontalSpan = 3;
		Utils.setLayoutData(labelSeparator, gridData);

			// buttons

		new Label(shell,SWT.NULL);

		Button bYes = new Button(shell,SWT.PUSH);
	 	bYes.setText(MessageText.getString("Button.yes"));
	 	gridData = new GridData(GridData.FILL_HORIZONTAL | GridData.HORIZONTAL_ALIGN_END | GridData.HORIZONTAL_ALIGN_FILL);
	 	gridData.grabExcessHorizontalSpace = true;
	 	gridData.widthHint = 70;
	 	Utils.setLayoutData(bYes, gridData);
	 	bYes.addListener(SWT.Selection,new Listener() {
	  		public void handleEvent(Event e) {
		 		close(true, checkBox.getSelection());
	   		}
		 });

	 	Button bNo = new Button(shell,SWT.PUSH);
	 	bNo.setText(MessageText.getString("Button.no"));
	 	gridData = new GridData(GridData.HORIZONTAL_ALIGN_END);
	 	gridData.grabExcessHorizontalSpace = false;
	 	gridData.widthHint = 70;
	 	Utils.setLayoutData(bNo, gridData);
	 	bNo.addListener(SWT.Selection,new Listener() {
	 		public void handleEvent(Event e) {
		 		close(false, checkBox.getSelection());
	   		}
	 	});

	 	bYes.setFocus();
		shell.setDefaultButton(bYes);

		shell.addListener(SWT.Traverse, new Listener() {
			public void handleEvent(Event e) {
				if (e.character == SWT.ESC) {
					close(false, true);
				}
			}
		});


	 	shell.pack ();

		Utils.centreWindow(shell);

		shell.open ();

		/*
		 * parg - removed this as causing UI hang when assoc checker and key-unlock dialogs
		 * open together
		while (!shell.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
		*/
	}

	protected void close(
		boolean		ok,
		boolean		check_on_startup )
 	{
    	if (check_on_startup != COConfigurationManager.getBooleanParameter("config.interface.checkassoc")) {

    		COConfigurationManager.setParameter("config.interface.checkassoc",check_on_startup);

    		COConfigurationManager.save();
    	}

 		if (ok) {

 			try {
 				platform.registerApplication();

 			} catch (PlatformManagerException e) {

 				Debug.printStackTrace(e);
 			}
 		}

 		shell.dispose();
 	}
}
