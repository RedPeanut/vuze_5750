/*
 * Created on 14 avr. 2005
 * Created by Olivier Chalouhi
 *
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
package org.gudy.azureus2.ui.swt.nat;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.ipchecker.natchecker.NatChecker;
import org.gudy.azureus2.core3.util.AERunnable;
import org.gudy.azureus2.core3.util.AEThread;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.ui.swt.Messages;
import org.gudy.azureus2.ui.swt.Utils;
import org.gudy.azureus2.ui.swt.components.shell.ShellFactory;
import org.gudy.azureus2.ui.swt.shells.CoreWaiterSWT;
import org.gudy.azureus2.ui.swt.shells.CoreWaiterSWT.TriggerInThread;

import com.aelitis.azureus.core.AzureusCore;
import com.aelitis.azureus.core.AzureusCoreRunningListener;
import com.aelitis.azureus.core.AzureusCoreFactory;
import com.aelitis.azureus.core.networkmanager.admin.NetworkAdmin;
import com.aelitis.azureus.core.networkmanager.admin.NetworkAdminProgressListener;
import com.aelitis.azureus.core.networkmanager.admin.NetworkAdminProtocol;

public class NatTestWindow {

  Display display;

  Button bTestTCP,bTestUDP,bApply,bCancel;
  StyledText textResults;

  int serverTCPListenPort;
  int serverUDPListenPort;

  public class CheckerTCP extends AEThread {

    private int TCPListenPort;

    public CheckerTCP(int tcp_listen_port) {
      super("NAT Checker TCP");
      this.TCPListenPort = tcp_listen_port;
    }

    public void
    runSupport()
    {
    	try {
	          printMessage(MessageText.getString("configureWizard.nat.testing") + " TCP " + TCPListenPort + " ... ");
	          NatChecker checker = new NatChecker(AzureusCoreFactory.getSingleton(), NetworkAdmin.getSingleton().getMultiHomedServiceBindAddresses(true)[0], TCPListenPort, false);
	          switch (checker.getResult()) {
	          case NatChecker.NAT_OK :
	            printMessage("\n" + MessageText.getString("configureWizard.nat.ok") + "\n" + checker.getAdditionalInfo());
	            break;
	          case NatChecker.NAT_KO :
	            printMessage("\n" + MessageText.getString("configureWizard.nat.ko") + " - " + checker.getAdditionalInfo()+".\n");
	            break;
	          default :
	            printMessage("\n" + MessageText.getString("configureWizard.nat.unable") + ". \n(" + checker.getAdditionalInfo()+").\n");
	            break;
	          }
    	} finally {
          if (display.isDisposed()) {return;}
          display.asyncExec(new AERunnable()  {
            public void runSupport() {
              if (bTestTCP != null && ! bTestTCP.isDisposed())
            	  bTestTCP.setEnabled(true);
			if (bTestUDP != null && ! bTestUDP.isDisposed())
				bTestUDP.setEnabled(true);
			if (bApply != null && ! bApply.isDisposed())
				bApply.setEnabled(true);
            }
          });
    	}
    }
  }

  public class CheckerUDP extends AEThread {

	    private AzureusCore	core;
	    private int			udp_port;

	    public CheckerUDP(AzureusCore _core, int _udp_port) {
	      super("NAT Checker UDP");
	      core 		= _core;
	      udp_port	= _udp_port;
	    }

	    public void
	    runSupport()
	    {
	    	try {
		    	final NetworkAdmin	admin = NetworkAdmin.getSingleton();

				NetworkAdminProtocol[] inbound_protocols = admin.getInboundProtocols(core);

				NetworkAdminProtocol selected = null;

				for (NetworkAdminProtocol p: inbound_protocols) {

					if (p.getType() == NetworkAdminProtocol.PT_UDP && p.getPort() == udp_port) {

						selected = p;

						break;
					}
				}

				if (selected == null) {

					selected = admin.createInboundProtocol(core, NetworkAdminProtocol.PT_UDP, udp_port);
				}

		        if (selected == null) {

		        	printMessage("\n" + MessageText.getString("configureWizard.nat.ko") + ". \n( No UDP protocols enabled).\n");

		        } else {

		        	printMessage(MessageText.getString("configureWizard.nat.testing") + " UDP " + udp_port + " ... ");

		        	try {
		        		selected.test(
		        				null,
		        				true,
		        				new NetworkAdminProgressListener()
		        				{
		        					public void
		        					reportProgress(
		        							String task )
		        					{
		        						printMessage("\n    " + task);
		        					}
		        				});

		        		printMessage("\n" + MessageText.getString("configureWizard.nat.ok"));

		        	} catch (Throwable e) {

		        		printMessage("\n" + MessageText.getString("configureWizard.nat.ko") + ". " + Debug.getNestedExceptionMessage(e)+".\n");
		        	}
		        }

	    	} finally {
	    		if (display.isDisposed()) {return;}
	    		display.asyncExec(new AERunnable()  {
	    			public void runSupport() {
	    				if (bTestTCP != null && ! bTestTCP.isDisposed())
	    					bTestTCP.setEnabled(true);
	    				if (bTestUDP != null && ! bTestUDP.isDisposed())
	    					bTestUDP.setEnabled(true);
	    				if (bApply != null && ! bApply.isDisposed())
	    					bApply.setEnabled(true);
	    			}
	    		});
	    	}
	    }
  }


  public NatTestWindow() {
	serverTCPListenPort = COConfigurationManager.getIntParameter("TCP.Listen.Port");
	serverUDPListenPort = COConfigurationManager.getIntParameter("UDP.Listen.Port");

    final Shell shell = ShellFactory.createMainShell(SWT.BORDER | SWT.TITLE | SWT.CLOSE);
    shell.setText(MessageText.getString("configureWizard.nat.title"));
    Utils.setShellIcon(shell);

    display = shell.getDisplay();

    GridLayout layout = new GridLayout();
    layout.numColumns = 1;
    shell.setLayout(layout);

    Composite panel = new Composite(shell, SWT.NULL);
    GridData gridData = new GridData(GridData.VERTICAL_ALIGN_CENTER | GridData.FILL_HORIZONTAL);
    Utils.setLayoutData(panel, gridData);
    layout = new GridLayout();
    layout.numColumns = 3;
    panel.setLayout(layout);

    Label label = new Label(panel, SWT.WRAP);
    gridData = new GridData();
    gridData.horizontalSpan = 3;
    gridData.widthHint = 400;
    Utils.setLayoutData(label, gridData);
    Messages.setLanguageText(label, "configureWizard.nat.message");

    label = new Label(panel, SWT.NULL);
    label = new Label(panel, SWT.NULL);
    label = new Label(panel, SWT.NULL);
    label = new Label(panel, SWT.NULL);

    	// TCP

    Messages.setLanguageText(label, "configureWizard.nat.server.tcp_listen_port");

    final Text textServerTCPListen = new Text(panel, SWT.BORDER);
    gridData = new GridData();
    gridData.grabExcessHorizontalSpace = true;
    gridData.horizontalAlignment = SWT.FILL;
    Utils.setLayoutData(textServerTCPListen, gridData);
    textServerTCPListen.setText("" + serverTCPListenPort);
    textServerTCPListen.addListener(SWT.Verify, new Listener() {
      public void handleEvent(Event e) {
        String text = e.text;
        char[] chars = new char[text.length()];
        text.getChars(0, chars.length, chars, 0);
        for (int i = 0; i < chars.length; i++) {
          if (!('0' <= chars[i] && chars[i] <= '9')) {
            e.doit = false;
            return;
          }
        }
      }
    });

    textServerTCPListen.addListener(SWT.Modify, new Listener() {
      public void handleEvent(Event e) {
    	  try {
    		  final int TCPListenPort = Integer.parseInt(textServerTCPListen.getText());
    		  serverTCPListenPort = TCPListenPort;
    	  } catch (Throwable f) {
    	  }
      }
    });

    bTestTCP = new Button(panel, SWT.PUSH);
    Messages.setLanguageText(bTestTCP, "configureWizard.nat.test");
    gridData = new GridData();
    gridData.widthHint = 70;
    Utils.setLayoutData(bTestTCP, gridData);

    label = new Label(panel, SWT.NULL);

    	// UDP

    Messages.setLanguageText(label, "configureWizard.nat.server.udp_listen_port");

    final Text textServerUDPListen = new Text(panel, SWT.BORDER);
    gridData = new GridData();
    gridData.grabExcessHorizontalSpace = true;
    gridData.horizontalAlignment = SWT.FILL;
    Utils.setLayoutData(textServerUDPListen, gridData);
    textServerUDPListen.setText("" + serverUDPListenPort);
    textServerUDPListen.addListener(SWT.Verify, new Listener() {
      public void handleEvent(Event e) {
        String text = e.text;
        char[] chars = new char[text.length()];
        text.getChars(0, chars.length, chars, 0);
        for (int i = 0; i < chars.length; i++) {
          if (!('0' <= chars[i] && chars[i] <= '9')) {
            e.doit = false;
            return;
          }
        }
      }
    });

    textServerUDPListen.addListener(SWT.Modify, new Listener() {
      public void handleEvent(Event e) {
    	  try {
    		  final int UDPListenPort = Integer.parseInt(textServerUDPListen.getText());
    		  serverUDPListenPort =UDPListenPort;
    	  } catch (Throwable f) {
    	  }
      }
    });

    bTestUDP = new Button(panel, SWT.PUSH);
    Messages.setLanguageText(bTestUDP, "configureWizard.nat.test");
    gridData = new GridData();
    gridData.widthHint = 70;
    Utils.setLayoutData(bTestUDP, gridData);

    	// results

    textResults = new StyledText(panel, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.WRAP);
    gridData = new GridData();
    gridData.widthHint = 400;
    gridData.heightHint = 100;
    gridData.grabExcessVerticalSpace = true;
    gridData.verticalAlignment = SWT.FILL;
    gridData.horizontalSpan = 3;
    Utils.setLayoutData(textResults, gridData);
    textResults.setBackground(panel.getDisplay().getSystemColor(SWT.COLOR_WHITE));

    bTestTCP.addListener(SWT.Selection, new Listener() {
      public void handleEvent(Event event) {
      	bTestUDP.setEnabled(false);
    	bTestTCP.setEnabled(false);
    	bApply.setEnabled(false);
        textResults.setText("");

        CoreWaiterSWT.waitForCore(TriggerInThread.ANY_THREAD,
						new AzureusCoreRunningListener() {
							public void azureusCoreRunning(AzureusCore core) {
								CheckerTCP checker = new CheckerTCP(serverTCPListenPort);
								checker.start();
					}
				});
      }
    });


    bTestUDP.addListener(SWT.Selection, new Listener() {
        public void handleEvent(Event event) {
          bTestUDP.setEnabled(false);
          bTestTCP.setEnabled(false);
          bApply.setEnabled(false);
          textResults.setText("");

          CoreWaiterSWT.waitForCore(TriggerInThread.ANY_THREAD,
  						new AzureusCoreRunningListener() {
  							public void azureusCoreRunning(AzureusCore core) {
  								CheckerUDP checker = new CheckerUDP(core,serverUDPListenPort);
  								checker.start();
  					}
  				});
        }
      });

    bApply = new Button(panel,SWT.PUSH);
    bApply.setText(MessageText.getString("Button.apply"));
    gridData = new GridData();
    gridData.widthHint = 70;
    gridData.grabExcessHorizontalSpace = true;
    gridData.horizontalAlignment = SWT.RIGHT;
    gridData.horizontalSpan = 2;
    Utils.setLayoutData(bApply, gridData);


    bApply.addListener(SWT.Selection, new Listener() {
      public void handleEvent(Event event) {
	   	int	old_tcp 	= COConfigurationManager.getIntParameter("TCP.Listen.Port");
	   	int	old_udp 	= COConfigurationManager.getIntParameter("UDP.Listen.Port");
	   	int	old_udp2 	= COConfigurationManager.getIntParameter("UDP.NonData.Listen.Port");

	   	if (old_tcp != serverTCPListenPort) {
	   		COConfigurationManager.setParameter("TCP.Listen.Port",serverTCPListenPort);
	   	}

        if (old_udp != serverUDPListenPort) {
        	COConfigurationManager.setParameter("UDP.Listen.Port",serverUDPListenPort);

	        if (old_udp == old_udp2) {
	        	COConfigurationManager.setParameter("UDP.NonData.Listen.Port",serverUDPListenPort);
	        }
        }

        COConfigurationManager.save();

        shell.close();
      }
    });

    bCancel = new Button(panel,SWT.PUSH);
    bCancel.setText(MessageText.getString("Button.cancel"));
    gridData = new GridData();
    gridData.widthHint = 70;
    Utils.setLayoutData(bCancel, gridData);
    bCancel.addListener(SWT.Selection, new Listener() {
      public void handleEvent(Event event) {
        shell.close();
      }
    });

	shell.setDefaultButton(bApply);

	shell.addListener(SWT.Traverse, new Listener() {
		public void handleEvent(Event e) {
			if (e.character == SWT.ESC) {
				shell.close();
			}
		}
	});

    shell.pack();
    Utils.centreWindow(shell);
    shell.open();
  }

  public void printMessage(final String message) {
    if (display == null || display.isDisposed())
      return;
    display.asyncExec(new AERunnable() {
      public void runSupport() {
        if (textResults == null || textResults.isDisposed())
          return;
        textResults.append(message);
      }
    });
  }
}
