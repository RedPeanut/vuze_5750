/*
 * Created on 8 juil. 2003
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.	See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA	02111-1307, USA.
 *
 */
package org.gudy.azureus2.ui.swt;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.*;

import com.aelitis.azureus.core.*;
import com.aelitis.azureus.core.impl.AzureusCoreSingleInstanceClient;
import com.aelitis.azureus.ui.UIFunctions;
import com.aelitis.azureus.ui.UIFunctionsManager;
import com.aelitis.azureus.ui.swt.UIFunctionsSWT;

import org.gudy.azureus2.core3.util.*;
import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.logging.*;
import org.gudy.azureus2.plugins.update.UpdateInstaller;
import org.gudy.azureus2.plugins.update.UpdateManager;
import org.gudy.azureus2.ui.swt.mainwindow.TorrentOpener;
import org.gudy.azureus2.ui.swt.sharing.ShareUtils;

/**
 * @author Olivier
 *
 */
public class StartServer {
	
	private static final LogIDs LOGID = LogIDs.GUI;

	private ServerSocket socket;
	private int state;

	private boolean bContinue;
	public static final int STATE_FAULTY = 0;
	public static final int STATE_LISTENING = 1;

	protected List		queued_torrents = new ArrayList();
	protected boolean		core_started	= false;
	protected AEMonitor	this_mon		= new AEMonitor("StartServer");

	public StartServer() {
		try {
			// DON'T USE LOGGER HERE DUE TO COMMENTS BELOW - IF AZ ALREADY RUNNING THEN THE SERVERSOCKET
			// CALL WILL THROW AN EXCEPTION
			socket = new ServerSocket(Constants.INSTANCE_PORT, 50, InetAddress.getByName("127.0.0.1")); //NOLAR: only bind to localhost
			state = STATE_LISTENING;
			if (Logger.isEnabled())
				Logger.log(new LogEvent(LOGID, "StartServer: listening on "
						+ "127.0.0.1:"+Constants.INSTANCE_PORT+" for passed torrent info"));
		} catch (Throwable t) {
			// DON'T USE LOGGER here as we DON't want to initialise all the logger stuff
			// and in particular AEDiagnostics config dirty stuff!!!!
			state = STATE_FAULTY;
			String reason = t.getMessage() == null ? "<>" : t.getMessage();
			System.out.println("StartServer ERROR: unable" + " to bind to 127.0.0.1:"+Constants.INSTANCE_PORT+" listening"
							+ " for passed torrent info: " + reason);
		}
	}

	public void
	pollForConnections(
 	 	final AzureusCore		azureus_core )

	{
				azureus_core.addLifecycleListener(
					new AzureusCoreLifecycleAdapter() {
					public void componentCreated(
						AzureusCore 			core,
						AzureusCoreComponent	component) {
						if (component instanceof UIFunctionsSWT) {

							openQueuedTorrents();
						}
						}
				});

				if (socket != null) {

				Thread t =
					new AEThread("Start Server") {
						public void
						runSupport() {
							pollForConnectionsSupport(azureus_core);
						}
				};

			t.setDaemon(true);

			t.start();
				}
	}

	private void pollForConnectionsSupport(final AzureusCore core) {
		bContinue = true;
		while (bContinue) {
			BufferedReader br = null;
			try {
				Socket sck = socket.accept();

				AzureusCoreSingleInstanceClient.sendReply(sck);

				String address = sck.getInetAddress().getHostAddress();
				if (address.equals("localhost") || address.equals("127.0.0.1")) {
					br = new BufferedReader(new InputStreamReader(sck.getInputStream(),Constants.DEFAULT_ENCODING));
					String line = br.readLine();
					//System.out.println("received : " + line);

					if (Logger.isEnabled())
						Logger.log(new LogEvent(LOGID, "Main::startServer: received '"
								+ line + "'"));

					if (line != null) {
						String [] args = parseArgs(line);
						if (args != null && args.length > 0) {
							String debug_str = args[0];
							for (int i=1; i<args.length; i++) {
								debug_str += " ; " + args[i];
							}
							Logger.log(new LogEvent(LOGID, "Main::startServer: decoded to '" + debug_str + "'"));
							processArgs(core,args);
						}
					}
				}

				sck.close();

			}
			catch (Exception e) {
				if (!(e instanceof SocketException))
					Debug.printStackTrace(e);
				//bContinue = false;
			} finally {
				try {
					if (br != null)
						br.close();
				} catch (Exception e) { /*ignore */
				}
			}
		}
	}

	private static String[] parseArgs(String line) {
		if (!line.startsWith(AzureusCoreSingleInstanceClient.ACCESS_STRING + ";")) {return null;}

		// I'm sure there's a lovely regex which could do this, but I can't be bothered to figure
		// it out.
		ArrayList parts = new ArrayList();
		StringBuilder buf = new StringBuilder();
		boolean escape_mode = false;
		char c;
		for (int i=AzureusCoreSingleInstanceClient.ACCESS_STRING.length() + 1; i<line.length(); i++) {
			c = line.charAt(i);
			if (escape_mode) {buf.append(c); escape_mode = false;}
			else if (c == '&') {escape_mode = true;}
			else if (c == ';') {parts.add(buf.toString()); buf.setLength(0);}
			else {buf.append(c);}
		}
		if (buf.length() > 0) {parts.add(buf.toString());}
		return (String[])parts.toArray(new String[parts.size()]);

	}

	protected void processArgs(
		AzureusCore		core,
	 	String 			args[])
	{
		if (args.length < 1 || !args[0].equals("args")) {
			return;
		}
		boolean showMainWindow = args.length == 1 || COConfigurationManager.getBooleanParameter("Activate Window On External Download");
		boolean	open	= true;
		for (int i = 1; i < args.length; i++) {
			String	arg = args[i];
			if (i == 1) {
					if (arg.equalsIgnoreCase("--closedown") || arg.equalsIgnoreCase("--shutdown")) {
							// discard any pending updates as we need to shutdown immediately (this
							// is called from installer to close running instance)
						try {
							UpdateManager um = core.getPluginManager().getDefaultPluginInterface().getUpdateManager();
							UpdateInstaller[] installers = um.getInstallers();
							for (UpdateInstaller installer: installers) {
								installer.destroy();
							}
						} catch (Throwable e) {
						}
						UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
						if (uiFunctions != null) {
							uiFunctions.dispose(false, false);
						}
						return;
					} else if (arg.equalsIgnoreCase("--restart")) {
						UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
						if (uiFunctions != null) {
							uiFunctions.dispose(true, false);
						}
						return;
					} else if (arg.equalsIgnoreCase("--open")) {
						showMainWindow = true;
						continue;
					} else if (arg.equalsIgnoreCase("--share")) {
						showMainWindow = true;
						open	= false;
						continue;
					}
			}
				String file_name = arg;
				File file = new File(file_name);
				if (!file.exists() && !isURI( file_name)) {
					String	magnet_uri = UrlUtils.normaliseMagnetURI(file_name);
					if (magnet_uri != null) {
						file_name = magnet_uri;
					}
				}
				if (isURI( file_name)) {
					if (Logger.isEnabled())
						Logger.log(new LogEvent(LOGID, "StartServer: args[" + i
								+ "] handling as a URI: " + file_name));
				} else {
						try {

							if (!file.exists()) {
								throw (new Exception("File not found"));
							}
							file_name = file.getCanonicalPath();
							Logger.log(new LogEvent(LOGID, "StartServer: file = " + file_name));
						} catch (Throwable e) {
							Logger.log(new LogAlert(LogAlert.REPEATABLE, LogAlert.AT_ERROR,
									"Failed to access torrent file '" + file_name
									+ "'. Ensure sufficient temporary file space "
									+ "available (check browser cache usage)."));
						}
				}
				boolean	queued = false;
				try {
					this_mon.enter();
					if (!core_started) {
						queued_torrents.add(new Object[]{ file_name, Boolean.valueOf(open)});
						queued = true;
					}
				} finally {
					this_mon.exit();
				}
				if (!queued) {
					handleFile(file_name, open);
				}
			}
		if (showMainWindow) {
			showMainWindow();
		}
	}

	protected boolean isURI(String filename) {
		String filename_lower = filename.toLowerCase();
		return (
				filename_lower.startsWith("http:") ||
				filename_lower.startsWith("https:") ||
				filename_lower.startsWith("magnet:") ||
				filename_lower.startsWith("maggot:") ||
				filename_lower.startsWith("bc:") ||
				filename_lower.startsWith("bctp:") ||
				filename_lower.startsWith("dht:")
		);
	}

	protected void handleFile(String file_name, boolean open) {
		try {
			if (open) {
				TorrentOpener.openTorrent(file_name);
			} else {
				File	f = new File(file_name);
				if (f.isDirectory()) {
					ShareUtils.shareDir(file_name);
				} else {
			 		ShareUtils.shareFile(file_name);
				}
			}
		} catch (Throwable e) {
			Debug.printStackTrace(e);
		}
	}

	protected void openQueuedTorrents() {
		try {
			this_mon.enter();
			core_started	= true;
		} finally {
			this_mon.exit();
		}
		for (int i=0;i<queued_torrents.size();i++) {
			Object[]	entry = (Object[])queued_torrents.get(i);
			String	file_name 	= (String)entry[0];
			boolean	open		= ((Boolean)entry[1]).booleanValue();
			handleFile(file_name, open);
		}
	}

	protected void showMainWindow() {
		UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
		if (uiFunctions != null) {
			uiFunctions.bringToFront();
		}
	}

	public void stopIt() {
		bContinue = false;
		try {
			if (socket != null) {
				socket.close();
			}
		}
		catch (Throwable e) {/*ignore */}
	}
	/**
	 * @return
	 */
	public int getState() {
		return state;
	}

	// Test argument parsing code.
	public static void main(String [] args) {
		String[] input_tests = new String[] {
			"a;b;c",
			"test",
			AzureusCoreSingleInstanceClient.ACCESS_STRING + ";b;c;d", // Simple test
			AzureusCoreSingleInstanceClient.ACCESS_STRING + ";b;c&;d;e", // Less simple test
			AzureusCoreSingleInstanceClient.ACCESS_STRING + ";b;c&&;d;e", // Even less simple test
			AzureusCoreSingleInstanceClient.ACCESS_STRING + ";b;c&&&;d;e", // Awkward test
		};

		String[][] output_results = new String[][] {
				null,
				null,
				new String[] {"b", "c", "d"},
				new String[] {"b", "c;d", "e"},
				new String[] {"b", "c&", "d", "e"},
				new String[] {"b", "c&;d", "e"},
		};

		for (int i=0; i<input_tests.length; i++) {
			System.out.println("Testing: " + input_tests[i]);
			String[] result = parseArgs(input_tests[i]);
			if (result == output_results[i]) {continue;}
			if (Arrays.equals(result, output_results[i])) {continue;}
			System.out.println("TEST FAILED");
			System.out.println("	Expected: " + Arrays.asList(output_results[i]));
			System.out.println("	Decoded : " + Arrays.asList(result));
			System.exit(1);
		}

		System.out.println("Done.");
	}

}
