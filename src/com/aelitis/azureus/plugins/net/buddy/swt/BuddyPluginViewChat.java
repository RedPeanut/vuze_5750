/*
 * Created on Apr 26, 2008
 * Created by Paul Gardner
 *
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


package com.aelitis.azureus.plugins.net.buddy.swt;

import java.util.*;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.plugins.utils.LocaleUtilities;
import org.gudy.azureus2.ui.swt.Messages;
import org.gudy.azureus2.ui.swt.Utils;
import org.gudy.azureus2.ui.swt.components.shell.ShellFactory;
import org.gudy.azureus2.ui.swt.mainwindow.Colors;

import com.aelitis.azureus.plugins.net.buddy.BuddyPlugin;
import com.aelitis.azureus.plugins.net.buddy.BuddyPluginAZ2;
import com.aelitis.azureus.plugins.net.buddy.BuddyPluginAZ2ChatListener;
import com.aelitis.azureus.plugins.net.buddy.BuddyPluginBuddy;

public class
BuddyPluginViewChat
	implements BuddyPluginAZ2ChatListener
{
	private BuddyPlugin						plugin;
	private BuddyPluginAZ2.chatInstance		chat;

	private LocaleUtilities		lu;

	private Shell 		shell;
	private StyledText 	log;
	private Table		buddy_table;

	private List		participants = new ArrayList();

	protected
	BuddyPluginViewChat(
		BuddyPlugin						_plugin,
		Display 						_display,
		BuddyPluginAZ2.chatInstance		_chat) {
		plugin	= _plugin;
		chat	= _chat;

		lu		= plugin.getPluginInterface().getUtilities().getLocaleUtilities();

		shell = ShellFactory.createMainShell(SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MIN | SWT.MAX);

		shell.addDisposeListener(
			new DisposeListener() {
				public void widgetDisposed(
					DisposeEvent arg0) {
					closed();
				}
			});

		shell.setText(lu.getLocalisedMessageText("azbuddy.chat.title"));

		Utils.setShellIcon(shell);

		GridLayout layout = new GridLayout();
		layout.numColumns = 2;
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		shell.setLayout(layout);
		GridData grid_data = new GridData(GridData.FILL_BOTH);
		Utils.setLayoutData(shell, grid_data);


		log = new StyledText(shell,SWT.READ_ONLY | SWT.V_SCROLL | SWT.BORDER | SWT.WRAP | SWT.NO_FOCUS);
		grid_data = new GridData(GridData.FILL_BOTH);
		grid_data.horizontalSpan = 1;
		grid_data.horizontalIndent = 4;
		grid_data.widthHint = 300;
		grid_data.heightHint = 400;
		Utils.setLayoutData(log, grid_data);
		log.setIndent(4);

		log.setEditable(false);

		Composite rhs = new Composite(shell, SWT.NONE);
		layout = new GridLayout();
		layout.numColumns = 1;
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		rhs.setLayout(layout);
		grid_data = new GridData(GridData.FILL_BOTH);
		grid_data.widthHint = 150;
		Utils.setLayoutData(rhs, grid_data);

			// table

		buddy_table = new Table(rhs, SWT.MULTI | SWT.BORDER | SWT.FULL_SELECTION | SWT.VIRTUAL);

		String[] headers = {
				"azbuddy.ui.table.name" };

		int[] sizes = { 150 };

		int[] aligns = { SWT.LEFT };

		for (int i = 0; i < headers.length; i++) {

			TableColumn tc = new TableColumn(buddy_table, aligns[i]);

			tc.setWidth(Utils.adjustPXForDPI(sizes[i]));

			Messages.setLanguageText(tc, headers[i]);
		}

	    buddy_table.setHeaderVisible(true);

	    grid_data = new GridData(GridData.FILL_BOTH);
	    grid_data.heightHint = buddy_table.getHeaderHeight() * 3;
		Utils.setLayoutData(buddy_table, grid_data);


		buddy_table.addListener(
			SWT.SetData,
			new Listener() {
				public void handleEvent(
					Event event) {
					TableItem item = (TableItem)event.item;

					int index = buddy_table.indexOf(item);

					if (index < 0 || index >= participants.size()) {

						return;
					}

					BuddyPluginAZ2.chatParticipant	participant = (BuddyPluginAZ2.chatParticipant)participants.get(index);

					BuddyPluginBuddy buddy = participant.getBuddy();

					if (buddy == null) {

						item.setForeground(0, Colors.red);

					} else if (buddy.isOnline( false)) {

						item.setForeground(0, Colors.black);

					} else {

						item.setForeground(0, Colors.grey);
					}

					item.setText(0, participant.getName());
				}
			});




			// Text

		final Text text = new Text( shell, SWT.MULTI | SWT.V_SCROLL | SWT.WRAP | SWT.BORDER);
		grid_data = new GridData(GridData.FILL_HORIZONTAL);
		grid_data.horizontalSpan = 2;
		grid_data.heightHint = 50;
		Utils.setLayoutData(text, grid_data);

		text.addKeyListener(
			new KeyListener() {
				public void keyPressed(
					KeyEvent e) {
					if (e.keyCode == SWT.CR) {

						e.doit = false;

						sendMessage( text.getText());

						text.setText("");
					}
				}

				public void keyReleased(
					KeyEvent e) {
				}
			});

		text.setFocus();

		shell.addListener(
			SWT.Traverse,
			new Listener() {
				public void handleEvent(
					Event e) {
					if (e.character == SWT.ESC) {

						close();
				}
			}
		});

		BuddyPluginAZ2.chatParticipant[] existing_participants = chat.getParticipants();

		synchronized(participants) {

			participants.addAll(Arrays.asList( existing_participants));
		}

		updateTable(false);

		BuddyPluginAZ2.chatMessage[] history = chat.getHistory();

		for (int i=0;i<history.length;i++) {

			logChatMessage( history[i].getNickName(), Colors.blue, history[i].getMessage());
		}

		chat.addListener(this);

	    shell.pack();
	    Utils.createURLDropTarget(shell, text);
	    Utils.centreWindow(shell);
	    shell.open();
	}

	protected void
	updateTable(
		boolean	async) {
		if (async) {

			if (!buddy_table.isDisposed()) {

				buddy_table.getDisplay().asyncExec(
						new Runnable() {
							public void
							run() {
								if (buddy_table.isDisposed()) {

									return;
								}

								updateTable(false);
							}
						});
			}
		} else {

			buddy_table.setItemCount( participants.size());
			buddy_table.clearAll();
			buddy_table.redraw();
		}
	}

	protected void
	close() {
		shell.dispose();
	}

	protected void
	closed() {
		chat.removeListener(this);

		chat.destroy();
	}

	public void participantAdded(
		BuddyPluginAZ2.chatParticipant		participant) {
		synchronized(participants) {

			participants.add(participant);
		}

		updateTable(true);
	}

	public void participantChanged(
		BuddyPluginAZ2.chatParticipant		participant) {
		updateTable(true);
	}

	public void participantRemoved(
		BuddyPluginAZ2.chatParticipant		participant) {
		synchronized(participants) {

			participants.remove(participant);
		}

		updateTable(true);
	}

	protected void
	sendMessage(String		text) {
		Map	msg = new HashMap();

		try {
			msg.put("line", text.getBytes("UTF-8"));

		} catch (Throwable e) {

			msg.put("line", text.getBytes());
		}

		logChatMessage(plugin.getNickname(), Colors.green, msg);

		chat.sendMessage(msg);
	}

	public void messageReceived(
		final BuddyPluginAZ2.chatParticipant	participant,
		final Map								msg) {
		if (!log.isDisposed()) {

			log.getDisplay().asyncExec(
					new Runnable() {
						public void
						run() {
							if (log.isDisposed()) {

								return;
							}

							try {
								logChatMessage(participant.getName(), Colors.blue, msg);

							} catch (Throwable e) {

								Debug.printStackTrace(e);
							}
						}
					});
		}
	}

	protected void
	logChatMessage(String		buddy_name,
		Color 		colour,
		Map			map) {
		byte[]	line = (byte[])map.get("line");

		String msg;

		try {
			msg = new String(line, "UTF-8");

		} catch (Throwable e) {

			msg = new String(line);
		}

		if (buddy_name.length() > 32) {

			buddy_name = buddy_name.substring(0,16) + "...";
		}

		int	start = log.getText().length();

		if (msg.startsWith("/me")) {

			msg = msg.substring(3).trim();

			String	me = "* " + buddy_name + " " + msg;

			log.append(me);

			if (colour != Colors.black) {

				StyleRange styleRange = new StyleRange();
				styleRange.start = start;
				styleRange.length = me.length();
				styleRange.foreground = colour;
				log.setStyleRange(styleRange);
			}

			log.append("\n");

		} else {
			String says = lu.getLocalisedMessageText("azbuddy.chat.says", new String[]{ buddy_name }) + "\n";

			log.append(says);

			if (colour != Colors.black) {

				StyleRange styleRange = new StyleRange();
				styleRange.start = start;
				styleRange.length = says.length();
				styleRange.foreground = colour;
				log.setStyleRange(styleRange);
			}

			log.append(msg + "\n");
		}

		log.setSelection( log.getText().length());
	}
}
