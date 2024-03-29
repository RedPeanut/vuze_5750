/*
 * File    : CertificateTrustWindow.java
 * Created : 29-Dec-2003
 * By      : parg
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

package org.gudy.azureus2.ui.swt.auth;

/**
 * @author parg
 *
 */

import java.security.cert.X509Certificate;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.security.SECertificateListener;
import org.gudy.azureus2.core3.security.SESecurityManager;
import org.gudy.azureus2.core3.torrent.TOTorrent;
import org.gudy.azureus2.core3.util.AERunnable;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.TorrentUtils;
import org.gudy.azureus2.ui.swt.Messages;
import org.gudy.azureus2.ui.swt.Utils;
import org.gudy.azureus2.ui.swt.components.shell.ShellFactory;
import org.gudy.azureus2.ui.swt.mainwindow.SWTThread;


public class
CertificateTrustWindow
	implements SECertificateListener
{
	public CertificateTrustWindow() {
		SESecurityManager.addCertificateListener(this);
	}

	public boolean trustCertificate(
		final String			_resource,
		final X509Certificate	cert) {
		final Display	display = SWTThread.getInstance().getDisplay();

		if (display.isDisposed()) {

			return (false);
		}

		TOTorrent		torrent = TorrentUtils.getTLSTorrent();

		final String resource;

		if (torrent != null) {


			resource	= TorrentUtils.getLocalisedName(torrent) + "\n" + _resource;
		} else {

			resource	= _resource;
		}

		final trustDialog[]	dialog = new trustDialog[1];

		try {
			Utils.execSWTThread(new AERunnable() {
						public void runSupport() {
							dialog[0] = new trustDialog(display, resource, cert);
						}
					}, false);
		} catch (Throwable e) {

			Debug.printStackTrace(e);

			return (false);
		}

		return (dialog[0].getTrusted());
	}

	protected static class
	trustDialog
	{
		protected Shell			shell;

		protected boolean		trusted;

		protected
		trustDialog(
				Display				display,
				String				resource,
				X509Certificate		cert) {
			if (display.isDisposed()) {

				return;
			}

			shell =  ShellFactory.createMainShell(SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);

			Utils.setShellIcon(shell);
			shell.setText(MessageText.getString("security.certtruster.title"));

			GridLayout layout = new GridLayout();
			layout.numColumns = 3;

			shell.setLayout (layout);

			GridData gridData;

			// info

			Label info_label = new Label(shell,SWT.NULL);
			Messages.setLanguageText(info_label, "security.certtruster.intro");
			gridData = new GridData(GridData.FILL_BOTH);
			gridData.horizontalSpan = 3;
			Utils.setLayoutData(info_label, gridData);

			// resource

			Label resource_label = new Label(shell,SWT.NULL);
			Messages.setLanguageText(resource_label, "security.certtruster.resource");
			gridData = new GridData(GridData.FILL_BOTH);
			gridData.horizontalSpan = 1;
			Utils.setLayoutData(resource_label, gridData);

			Label resource_value = new Label(shell,SWT.WRAP);
			resource_value.setText(resource.replaceAll("&", "&&"));
			gridData = new GridData(GridData.FILL_BOTH);
			gridData.horizontalSpan = 2;
			Utils.setLayoutData(resource_value, gridData);

			// issued by

			Label issued_by_label = new Label(shell,SWT.NULL);
			Messages.setLanguageText(issued_by_label, "security.certtruster.issuedby");
			gridData = new GridData(GridData.FILL_BOTH);
			gridData.horizontalSpan = 1;
			Utils.setLayoutData(issued_by_label, gridData);

			Label issued_by_value = new Label(shell,SWT.NULL);
			issued_by_value.setText(extractCN(cert.getIssuerDN().getName()).replaceAll("&", "&&"));
			gridData = new GridData(GridData.FILL_BOTH);
			gridData.horizontalSpan = 2;
			Utils.setLayoutData(issued_by_value, gridData);

			// issued to

			Label issued_to_label = new Label(shell,SWT.NULL);
			Messages.setLanguageText(issued_to_label, "security.certtruster.issuedto");
			gridData = new GridData(GridData.FILL_BOTH);
			gridData.horizontalSpan = 1;
			Utils.setLayoutData(issued_to_label, gridData);

			Label issued_to_value = new Label(shell,SWT.NULL);
			issued_to_value.setText(extractCN(cert.getSubjectDN().getName()).replaceAll("&", "&&"));
			gridData = new GridData(GridData.FILL_BOTH);
			gridData.horizontalSpan = 2;
			Utils.setLayoutData(issued_to_value, gridData);

			// prompt

			Label prompt_label = new Label(shell,SWT.NULL);
			Messages.setLanguageText(prompt_label, "security.certtruster.prompt");
			gridData = new GridData(GridData.FILL_BOTH);
			gridData.horizontalSpan = 3;
			Utils.setLayoutData(prompt_label, gridData);

				// line

			Label labelSeparator = new Label(shell,SWT.SEPARATOR | SWT.HORIZONTAL);
			gridData = new GridData(GridData.FILL_HORIZONTAL);
			gridData.horizontalSpan = 3;
			Utils.setLayoutData(labelSeparator, gridData);

				// buttons

			new Label(shell,SWT.NULL);

			Composite comp = new Composite(shell,SWT.NULL);
			gridData = new GridData(GridData.FILL_HORIZONTAL | GridData.HORIZONTAL_ALIGN_END | GridData.HORIZONTAL_ALIGN_FILL);
			gridData.grabExcessHorizontalSpace = true;
			gridData.horizontalSpan = 2;
			Utils.setLayoutData(comp, gridData);
			GridLayout layoutButtons = new GridLayout();
			layoutButtons.numColumns = 2;
			comp.setLayout(layoutButtons);



			Button bYes = new Button(comp,SWT.PUSH);
			bYes.setText(MessageText.getString("security.certtruster.yes"));
			gridData = new GridData(GridData.FILL_HORIZONTAL | GridData.HORIZONTAL_ALIGN_END | GridData.HORIZONTAL_ALIGN_FILL);
			gridData.grabExcessHorizontalSpace = true;
			gridData.widthHint = 70;
			Utils.setLayoutData(bYes, gridData);
			bYes.addListener(SWT.Selection,new Listener() {
				public void handleEvent(Event e) {
					close(true);
				}
			});

			Button bNo = new Button(comp,SWT.PUSH);
			bNo.setText(MessageText.getString("security.certtruster.no"));
			gridData = new GridData(GridData.HORIZONTAL_ALIGN_END);
			gridData.grabExcessHorizontalSpace = false;
			gridData.widthHint = 70;
			Utils.setLayoutData(bNo, gridData);
			bNo.addListener(SWT.Selection,new Listener() {
				public void handleEvent(Event e) {
					close(false);
				}
			});

			shell.setDefaultButton(bYes);

			shell.addListener(SWT.Traverse, new Listener() {
				public void handleEvent(Event e) {
					if (e.character == SWT.ESC) {
						close(false);
					}
				}
			});


			shell.pack ();

			Utils.centreWindow(shell);

			shell.open ();

	    while (!shell.isDisposed()) {
	      if (!shell.getDisplay().readAndDispatch()) {
	      	shell.getDisplay().sleep();
	      }
	    }
		}

		protected void close(
			boolean		ok) {
			trusted = ok;

			shell.dispose();
		}

		protected String extractCN(
			String		dn) {
			int	p1 = dn.indexOf("CN=");

			if (p1 == -1) {
				return (dn);
			}

			int	p2 = dn.indexOf(",", p1);

			if (p2 == -1) {

				return ( dn.substring(p1+3).trim());
			}

			return ( dn.substring(p1+3,p2).trim());
		}

		public boolean getTrusted() {
			return (trusted);
		}
	}
}
