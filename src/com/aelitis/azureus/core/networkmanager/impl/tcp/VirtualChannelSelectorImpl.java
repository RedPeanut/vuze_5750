/*
 * Created on Jul 28, 2004
 * Created by Alon Rohter
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
package com.aelitis.azureus.core.networkmanager.impl.tcp;


import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.gudy.azureus2.core3.logging.LogAlert;
import org.gudy.azureus2.core3.logging.LogEvent;
import org.gudy.azureus2.core3.logging.LogIDs;
import org.gudy.azureus2.core3.logging.Logger;
import org.gudy.azureus2.core3.util.AEDiagnostics;
import org.gudy.azureus2.core3.util.AEMonitor;
import org.gudy.azureus2.core3.util.AESemaphore;
import org.gudy.azureus2.core3.util.AEThread2;
import org.gudy.azureus2.core3.util.Constants;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.SimpleTimer;
import org.gudy.azureus2.core3.util.SystemTime;
import org.gudy.azureus2.core3.util.TimerEvent;
import org.gudy.azureus2.core3.util.TimerEventPerformer;

import com.aelitis.azureus.core.networkmanager.VirtualChannelSelector;

import hello.util.Log;
import hello.util.SingleCounter0;


/**
 * Provides a simplified and safe (selectable-channel) socket single-op selector.
 */
public class VirtualChannelSelectorImpl {

	private static String TAG = VirtualChannelSelectorImpl.class.getSimpleName();
	
	private static final LogIDs LOGID = LogIDs.NWMAN;
	private static final boolean MAYBE_BROKEN_SELECT;

	static {
		// freebsd 7.x and diablo 1.6 no works as selector returns none ready even though
		// there's a bunch readable
		// Seems to not just be diablo java, but general 7.1 problem
		String jvm_name = System.getProperty("java.vm.name", "");
		boolean is_diablo = jvm_name.startsWith("Diablo");
		boolean is_freebsd_7_or_higher = false;
		
		// hack for 10.6 - will switch to not doing System.setProperty("java.nio.preferSelect", "true"); later
		try {
			// unfortunately the package maintainer has set os.name to Linux for FreeBSD...
			if (Constants.isFreeBSD || Constants.isLinux) {
				String os_type = System.getenv("OSTYPE");
				if (os_type != null && os_type.equals("FreeBSD")) {
					String os_version = System.getProperty("os.version", "");
					String	digits = "";
					for ( int i=0;i<os_version.length();i++) {
						char c = os_version.charAt(i);
						if (Character.isDigit(c)) {
							digits += c;
						} else {
							break;
						}
					}
					if (digits.length() > 0) {
						is_freebsd_7_or_higher = Integer.parseInt(digits) >= 7;
					}
				}
			}
		} catch (Throwable e) {
			e.printStackTrace();
		}
		MAYBE_BROKEN_SELECT = is_freebsd_7_or_higher || is_diablo || Constants.isOSX_10_6_OrHigher;
		if (MAYBE_BROKEN_SELECT) {
			System.out.println("Enabling broken select detection: diablo=" + is_diablo + ", freebsd 7+=" + is_freebsd_7_or_higher + ", osx 10.6+=" + Constants.isOSX_10_6_OrHigher);
		}
	}

	private static final int SELECTOR_TIMEOUT	= 15*1000;

	static final AESemaphore getSelectorAllowed = new AESemaphore("getSelectorAllowed", 1);

	private static class SelectorTimeoutException
		extends IOException {
		private SelectorTimeoutException() {
			super("Selector allocation timeout");
		}
	}

	private static Selector getSelector()
		throws IOException {
		
		// we only want to allow one thread actively attempting to get a selector otherwise they'll just stack up
		if (!getSelectorAllowed.reserve(SELECTOR_TIMEOUT)) {
			Debug.out("Selector timeout (existing incomplete)");
			throw (new SelectorTimeoutException());
		}
		
		// some AV products/VPNs in a bad state can block selector opening - prevent this from blocking everything
		final Object[]	result = { null };
		final AESemaphore sem = new AESemaphore("getSelector");
		synchronized(VirtualChannelSelectorImpl.class) {
			try {
				final TimerEvent event =
					SimpleTimer.addEvent(
							"getSelector",
							SystemTime.getOffsetTime(SELECTOR_TIMEOUT),
							new TimerEventPerformer() {
								public void perform(TimerEvent event) {
									synchronized(VirtualChannelSelectorImpl.class) {
										if (result[0] == null) {
											Debug.out("Selector timeout");
											result[0] = new SelectorTimeoutException();
											sem.release();
										}
									}
								}
							});
				
				new AEThread2("getSelector") {
					public void run() {
						try {
							Selector sel = Selector.open();
							synchronized(VirtualChannelSelectorImpl.class) {
								if (result[0] == null) {
									result[0] = sel;
									return;
								}
							}
							sel.close();
						} catch (Throwable e) {
							synchronized(VirtualChannelSelectorImpl.class) {
								if (result[0] == null) {
									if (e instanceof IOException) {
										result[0]	= e;
									} else {
										result[0] = new IOException(Debug.getNestedExceptionMessage( e));
									}
								}
							}
						} finally {
							getSelectorAllowed.release();
							sem.release();
							event.cancel();
						}
					}
				}.start();
			} catch (Throwable e) {
				getSelectorAllowed.release();
				throw (new IOException(Debug.getNestedExceptionMessage(e)));
			}
		}
		sem.reserve();
		
		//System.out.println("getSelector-> " + result[0]);
		if (result[0] instanceof IOException) {
			throw ((IOException)result[0]);
		}
		return ((Selector)result[0]);
	}

	private boolean selectIsBroken;
	private int		selectLooksBrokenCount;
	private boolean	logged_broken_select;


	/*
	static boolean	rm_trace 	= false;
	static boolean	rm_test_fix = false;

	static{

		COConfigurationManager.addAndFireParameterListeners(
				new String[]{ "user.rm.trace", "user.rm.testfix" },
				new ParameterListener() {
					public void parameterChanged(
						String parameterName) {
						rm_trace 	= COConfigurationManager.getBooleanParameter("user.rm.trace", false);
						rm_test_fix = COConfigurationManager.getBooleanParameter("user.rm.testfix", false);
					}
				});
	}

	private long rm_flag_last_log;
	private Map	rm_listener_map = new HashMap();
	*/

	protected Selector selector;
	private final SelectorGuard selectorGuard;

	private int	consecSelectFails;
	private long consecSelectFailsStart;

	private final LinkedList<Object> 	registerCancelList 	= new LinkedList<Object>();
	private final AEMonitor 			registerCancelListMon	= new AEMonitor("VirtualChannelSelector:RCL");

	private final HashMap<AbstractSelectableChannel, Boolean> pausedStates = new HashMap<AbstractSelectableChannel, Boolean>();

	private final int 		interestOp;
	private final boolean	pauseAfterSelect;

	protected final VirtualChannelSelector parent;


	//private int[] select_counts = new int[ 50 ];
	//private int round = 0;

	private volatile boolean	destroyed;

	private boolean	randomiseKeys;

	private int		nextSelectLoopPos = 0;

	private static final int WRITE_SELECTOR_DEBUG_CHECK_PERIOD	= 10000;
	private static final int WRITE_SELECTOR_DEBUG_MAX_TIME		= 20000;

	private long lastWriteSelectDebug;
	private long lastSelectDebug;

	private long last_reopen_attempt = SystemTime.getMonotonousTime();

	public VirtualChannelSelectorImpl(VirtualChannelSelector _parent, 
			int _interest_op, 
			boolean _pause_after_select, 
			boolean _randomise_keys) {
		
		this.parent = _parent;
		interestOp = _interest_op;

		pauseAfterSelect	= _pause_after_select;
		randomiseKeys		= _randomise_keys;

		String type;
		switch(interestOp) {
			case VirtualChannelSelector.OP_CONNECT:
				type = "OP_CONNECT";	
				break;
			case VirtualChannelSelector.OP_READ:
				type = "OP_READ";	
				break;
			default:
				type = "OP_WRITE";	
				break;
		}


		selectorGuard = new SelectorGuard(type, new SelectorGuard.GuardListener() {
			public boolean safeModeSelectEnabled() {
				return parent.isSafeSelectionModeEnabled();
			}
	
			public void spinDetected() {
				closeExistingSelector();
				try {Thread.sleep(1000);} catch (Throwable x) {x.printStackTrace();}
				parent.enableSafeSelectionMode();
			}
	
			public void failureDetected() {
				try {Thread.sleep(1000);} catch (Throwable x) {x.printStackTrace();}
				closeExistingSelector();
				try {Thread.sleep(1000);} catch (Throwable x) {x.printStackTrace();}
				selector = openNewSelector();
			}
		});

		selector = openNewSelector();
	}



	protected Selector openNewSelector() {
		Selector sel = null;
		final int MAX_TRIES = 10;
		try {
			sel = getSelector();
			AEDiagnostics.logWithStack("seltrace", "Selector created for '" + parent.getName() + "'," + selectorGuard.getType());
		} catch (Throwable t) {
			Debug.out("ERROR: caught exception on Selector.open()", t);
			try {Thread.sleep(3000);} catch (Throwable x) {x.printStackTrace();}
			int failCount = (t instanceof SelectorTimeoutException?1000:1);
			while (failCount < MAX_TRIES) {
				try {
					sel = getSelector();
					AEDiagnostics.logWithStack("seltrace", "Selector created for '" + parent.getName() + "'," + selectorGuard.getType());
					break;
				} catch (Throwable f) {
					Debug.out(f);
					if (f instanceof SelectorTimeoutException) {
						failCount = 1000;
					} else {
						failCount++;
					}
					if (failCount < MAX_TRIES) {
						try {Thread.sleep(3000);} catch ( Throwable x) {x.printStackTrace();}
					} else {
						break;
					}
				}
			}
			
			if (failCount < MAX_TRIES) { //success !
				Debug.out("NOTICE: socket Selector successfully opened after " +failCount+ " failures.");
			} else { //failure
				Logger.log(new LogAlert(LogAlert.REPEATABLE, LogAlert.AT_ERROR,
							"ERROR: socket Selector.open() failed " + (failCount==1000?"due to timeout": (MAX_TRIES + " times in a row")) + ", aborting."
									+ "\nAzureus / Java is likely being firewalled!"));
			}
		}
		return sel;
	}

	public void setRandomiseKeys(boolean r) {
		randomiseKeys = r;
	}

	public void pauseSelects(AbstractSelectableChannel channel) {
		//System.out.println("pauseSelects: " + channel + " - " + Debug.getCompressedStackTrace());
		if (channel == null) {
			return;
		}
		SelectionKey key = channel.keyFor(selector);
		if (key != null && key.isValid()) {
			key.interestOps(key.interestOps() & ~interestOp);
		} else {	//channel not (yet?) registered
			if (channel.isOpen()) {	//only bother if channel has not already been closed
				try {
					registerCancelListMon.enter();
					pausedStates.put(channel, Boolean.TRUE);	//ensure the op is paused upon reg select-time reg
				} finally {
					registerCancelListMon.exit();
				}
			}
		}
	}


	public void resumeSelects(AbstractSelectableChannel channel) {
		//System.out.println("resumeSelects: " + channel + " - " + Debug.getCompressedStackTrace());
		if (channel == null) {
			Debug.printStackTrace(new Exception("resumeSelects():: channel == null" ));
			return;
		}
		SelectionKey key = channel.keyFor(selector);
		if (key != null && key.isValid()) {
			// if we're resuming a non-interested key then reset the metrics
	
			if ((key.interestOps() & interestOp ) == 0) {
		 		 RegistrationData data = (RegistrationData)key.attachment();
	
		 		 data.lastSelectSuccessTime 	= SystemTime.getCurrentTime();
		 		 data.nonProgressCount		= 0;
			}
			key.interestOps(key.interestOps() | interestOp);
		} else {	
			//channel not (yet?) registered
			try {	
				registerCancelListMon.enter();
				pausedStates.remove(channel);	//check if the channel's op has been already paused before select-time reg
			} finally {	
				registerCancelListMon.exit();	
			}
		}
		//try {
		//	selector.wakeup();
		//}
		//catch (Throwable t) {	Debug.out("selector.wakeup():: caught exception: ", t);	 }
	}

	public void cancel(AbstractSelectableChannel channel) {
		//System.out.println("cancel: " + channel + " - " + Debug.getCompressedStackTrace());
		if (destroyed) {
			// don't worry too much about cancels
		}
		if (channel == null) {
			Debug.out("Attempt to cancel selects for null channel");
			return;
		}
		try {
			registerCancelListMon.enter();
				// ensure that there's only one operation outstanding for a given channel
				// at any one time (the latest operation requested )
			for (Iterator<Object> it = registerCancelList.iterator();it.hasNext();) {
				Object	obj = it.next();
				if (	channel == obj ||
						(	obj instanceof RegistrationData &&
									((RegistrationData)obj).channel == channel )) {
							// remove existing cancel or register
					it.remove();
					break;
				}
			}
			pauseSelects((AbstractSelectableChannel)channel);
				registerCancelList.add(channel);
		} finally {
			registerCancelListMon.exit();
		}
	}


	public void register(
		AbstractSelectableChannel 								channel,
		VirtualChannelSelector.VirtualAbstractSelectorListener 	listener,
		Object 													attachment) {
		
		if (destroyed) {
 			Debug.out("register called after selector destroyed");
		}
		if (channel == null) {
			Debug.out("Attempt to register selects for null channel");
			return;
		}
		
		try {
			registerCancelListMon.enter();
			// ensure that there's only one operation outstanding for a given channel
			// at any one time (the latest operation requested )
			for (Iterator<Object> it = registerCancelList.iterator();it.hasNext();) {
				Object	obj = it.next();
				if (channel == obj ||
						(	obj instanceof RegistrationData &&
							((RegistrationData)obj).channel == channel )
				) {
					it.remove();
					break;
				}
			}
			pausedStates.remove(channel);
			registerCancelList.add(new RegistrationData(channel, listener, attachment));
		} finally {
			registerCancelListMon.exit();
		}
	}


	public int select(long timeout) {
		
		long selectStartTime = SystemTime.getCurrentTime();
		if (selector == null) {
			long mono_now = SystemTime.getMonotonousTime();
			if ((mono_now - last_reopen_attempt > 60*1000) && !destroyed) {
				last_reopen_attempt = mono_now;
				selector = openNewSelector();
			}
			if (selector == null) {
				Debug.out("VirtualChannelSelector.select() op called with null selector");
				try {Thread.sleep(3000);} catch (Throwable x) {x.printStackTrace();}
				return 0;
			}
		}
		
		if (!selector.isOpen()) {
			Debug.out("VirtualChannelSelector.select() op called with closed selector");
			try {Thread.sleep(3000);} catch (Throwable x) {x.printStackTrace();}
			return 0;
		}
		
		// store these when they occur so they can be raised *outside* of the monitor to avoid
		// potential deadlocks
		RegistrationData	selectFailData	= null;
		Throwable 			selectFailExcep	= null;
		
		//process cancellations
		try {
			registerCancelListMon.enter();
			
			// don't use an iterator here as it is possible that error notifications to listeners
			// can result in the addition of a cancel request.
			// Note that this can only happen for registrations, and this *should* only result in
			// possibly a cancel being added (i.e. not a further registration), hence this can't
			// loop. Also note the approach of removing the entry before processing. This is so
			// that the logic used when adding a cancel (the removal of any matching entries) does
			// not cause the entry we're processing to be removed
			while (registerCancelList.size() > 0) {
				Object	obj = registerCancelList.remove(0);
				if (obj instanceof AbstractSelectableChannel) {
			 		// process cancellation
					AbstractSelectableChannel canceledChannel = (AbstractSelectableChannel)obj;
					try {
						SelectionKey key = canceledChannel.keyFor(selector);
						if (key != null) {
							key.cancel();	//cancel the key, since already registered
						}
					} catch (Throwable e) {
						Debug.printStackTrace(e);
					}
				} else {
					//process new registrations
					RegistrationData data = (RegistrationData)obj;
					try {
						if (data == null) {
							throw (new Exception("data == null"));
						} else if (data.channel == null) {
							throw (new Exception("data.channel == null"));
						}
						
						if (data.channel.isOpen()) {
							// see if already registered
							SelectionKey key = data.channel.keyFor(selector);
							if (key != null && key.isValid()) {	//already registered
								key.attach(data);
								key.interestOps(key.interestOps() | interestOp);	//ensure op is enabled
							} else {
								/*if (
										(interestOp & (SelectionKey.OP_READ | SelectionKey.OP_WRITE)) > 0
										&& Once.getInstance().getAndIncreaseCount() < 1
								) {
									Log.d(TAG, "select() is called..");
									Log.d(TAG, "register...");
									new Throwable().printStackTrace();
								}*/
								data.channel.register(selector, interestOp, data);
							}
							
							//check if op has been paused before registration moment
							Object paused = pausedStates.get(data.channel);
							if (paused != null) {
								pauseSelects(data.channel);	//pause it
							}
						} else{
							selectFailData	= data;
							selectFailExcep	= new Throwable("select registration: channel is closed");
						}
					} catch (Throwable t) {
						Debug.printStackTrace(t);
					 		selectFailData	= data;
					 		selectFailExcep	= t;
					}
				}
			}
			pausedStates.clear();	//reset after every registration round
		} finally {
			registerCancelListMon.exit();
		}
		
		if (selectFailData != null) {
			try {
				parent.selectFailure(
					selectFailData.listener,
					selectFailData.channel,
					selectFailData.attachment,
					selectFailExcep);
			} catch (Throwable e) {
				Debug.printStackTrace(e);
			}
		}
		
		//do the actual select
		int count = 0;
		selectorGuard.markPreSelectTime();
		try {
			count = selector.select(timeout);
			consecSelectFails = 0;
		} catch (Throwable t) {
	 		long	now = SystemTime.getMonotonousTime();
			consecSelectFails++;
			if (consecSelectFails == 1) {
				consecSelectFailsStart = now;
			}
			if (consecSelectFails > 20 && consecSelectFailsStart - now > 16*1000) {
				consecSelectFails = 0;
				Debug.out("Consecutive fail exceeded (" + consecSelectFails + ") - recreating selector");
				closeExistingSelector();
				try {	Thread.sleep(1000);	} catch ( Throwable x) {x.printStackTrace();}
				selector = openNewSelector();
				return (0);
			}
			if (now - lastSelectDebug > 5000) {
				lastSelectDebug = now;
				String msg = t.getMessage();
				if (msg == null || !msg.equalsIgnoreCase("bad file descriptor")) {
					Debug.out("Caught exception on selector.select() op: " +msg, t);
				}
			}
			try {	Thread.sleep(timeout);	} catch (Throwable e) { e.printStackTrace(); }
		}
		
		// do this after the select so that any pending cancels (prior to destroy) are processed
		// by the selector before we kill it
		if (destroyed) {
			closeExistingSelector();
			return (0);
		}
		
		if (	MAYBE_BROKEN_SELECT &&
				!selectIsBroken &&
				(interestOp == VirtualChannelSelector.OP_READ || interestOp == VirtualChannelSelector.OP_WRITE)) {
			if (selector.selectedKeys().size() == 0) {
				Set<SelectionKey> keys = selector.keys();
				for (SelectionKey key: keys) {
					if ((key.readyOps() & interestOp ) != 0) {
						selectLooksBrokenCount++;
						break;
					}
				}
				if (selectLooksBrokenCount >= 5) {
					selectIsBroken = true;
					if (!logged_broken_select) {
						logged_broken_select = true;
						//We always get this on OSX
						//Debug.outNoStack("Select operation looks broken, trying workaround");
					}
				}
			} else {
				selectLooksBrokenCount = 0;
			}
		}
		
		/*
		if (interestOp == VirtualChannelSelector.OP_READ) {	//TODO
			select_counts[round] = count;
			round++;
			if (round == select_counts.length) {
				StringBuffer buf = new StringBuffer(select_counts.length * 3);
				buf.append("select_counts=");
				for (int i=0; i < select_counts.length; i++) {
					buf.append(select_counts[i]);
					buf.append(' ');
				}
				//System.out.println(buf.toString());
				round = 0;
			}
		}
		*/
		
		selectorGuard.verifySelectorIntegrity(count, SystemTime.TIME_GRANULARITY_MILLIS /2);
		if (!selector.isOpen())	return count;
		
		int	progressMadeKeyCount	= 0;
		int	totalKeyCount			= 0;
		long	now = SystemTime.getCurrentTime();
		
		//notification of ready keys via listener callback
		// debug handling for channels stuck pending write select for long periods
		Set<SelectionKey>	nonSelectedKeys = null;
		if (interestOp == VirtualChannelSelector.OP_WRITE) {
			if (	now < lastWriteSelectDebug ||
				now - lastWriteSelectDebug > WRITE_SELECTOR_DEBUG_CHECK_PERIOD) {
				lastWriteSelectDebug = now;
				nonSelectedKeys = new HashSet<SelectionKey>(selector.keys());
			}
		}
		
		List<SelectionKey> readyKeys;
		if (MAYBE_BROKEN_SELECT && selectIsBroken) {
			Set<SelectionKey> all_keys = selector.keys();
			readyKeys = new ArrayList<SelectionKey>();
			for (SelectionKey key: all_keys) {
				if ((key.readyOps() & interestOp ) != 0) {
					readyKeys.add(key);
				}
			}
		} else {
			Set<SelectionKey> selected = selector.selectedKeys();
			if (selected.size() == 0) {
				readyKeys = Collections.emptyList();
			} else {
				readyKeys = new ArrayList<SelectionKey>(selected);
			}
		}
		
		boolean	randy = randomiseKeys;
		if (randy) {
			Collections.shuffle(readyKeys);
		}
		
		Set<SelectionKey>	selectedKeys = selector.selectedKeys();
		final int	readyKeySize	= readyKeys.size();;
		final int	startPos 		= nextSelectLoopPos++;
		final int	endPos			= startPos + readyKeySize;
		for (int i=startPos; i<endPos; i++) {
			SelectionKey key = readyKeys.get(i % readyKeySize);
			totalKeyCount++;
			selectedKeys.remove(key);
			RegistrationData data = (RegistrationData)key.attachment();
			if (nonSelectedKeys != null) {
				nonSelectedKeys.remove(key);
			}
			data.lastSelectSuccessTime = now;
			// int	rm_type;
			if (key.isValid()) {
				
				//it must have been paused between select and notification
				if ((key.interestOps() & interestOp) == 0) {
					// rm_type = 2;
				} else {
					if (pauseAfterSelect) {
						try {
							key.interestOps(key.interestOps() & ~interestOp);
						} catch (CancelledKeyException e) {
						}
					}
					
					//Log.d(TAG, "select() is called...");
					//Log.d(TAG, "how many times here is called?");
					
					boolean	progressIndicator = parent.selectSuccess(data.listener, data.channel, data.attachment);
					if (progressIndicator) {
						// rm_type = 0;
						progressMadeKeyCount++;
						data.nonProgressCount = 0;
					} else {
						// rm_type = 1;
						data.nonProgressCount++;
						boolean	loopback_connection = false;
						if (interestOp != VirtualChannelSelector.OP_ACCEPT) {
							SocketChannel sc = (SocketChannel)data.channel;
							Socket socket = sc.socket();
							InetAddress address = socket.getInetAddress();
							if (address != null) {
								loopback_connection = address.isLoopbackAddress();
							}
						}
						
						// we get no progress triggers when looping back for transcoding due to
						// high CPU usage of xcode process - remove debug spew and be more tolerant
						if (loopback_connection) {
							if (data.nonProgressCount == 10000) {
								Debug.out("No progress for " + data.nonProgressCount + ", closing connection");
								try {
									data.channel.close();
								} catch (Throwable e) {
									e.printStackTrace();
								}
							}
						} else {
							if (	data.nonProgressCount == 10 ||
									data.nonProgressCount %100 == 0 && data.nonProgressCount > 0) {
								boolean do_log = true;
								// seems we can get write-non-progress occurring under 'normal' circumstances so
								// back off the early logging in this case
								if (data.nonProgressCount == 10 && interestOp == VirtualChannelSelector.OP_WRITE) {
									do_log = false;
								}
								
								if (do_log) {
									Debug.out(
										"VirtualChannelSelector: No progress for op " + interestOp +
										": listener = " + data.listener.getClass() +
										", count = " + data.nonProgressCount +
										", socket: open = " + data.channel.isOpen() +
										(interestOp==VirtualChannelSelector.OP_ACCEPT?"":
											(", connected = " + ((SocketChannel)data.channel).isConnected())));
								}
								if (data.nonProgressCount == 1000) {
									Debug.out("No progress for " + data.nonProgressCount + ", closing connection");
									try {
										data.channel.close();
									} catch (Throwable e) {
										e.printStackTrace();
									}
								}
							}
						}
					}
				}
			} else {
				// rm_type = 3;
				key.cancel();
				parent.selectFailure(data.listener, data.channel, data.attachment, new Throwable("key is invalid" ));
				// can get this if socket has been closed between select and here
			}
			/*
			if (rm_trace) {
				Object	rm_key = data.listener.getClass();
				int[]	rm_count = (int[])rm_listener_map.get(rm_key);
				if (rm_count == null) {
					rm_count = new int[]{0,0,0,0};
					rm_listener_map.put(rm_key, rm_count);
				}
				rm_count[rm_type]++;
			}
			*/
		}
		
		if (nonSelectedKeys != null) {
			for (Iterator<SelectionKey> i = nonSelectedKeys.iterator(); i.hasNext();) {
				SelectionKey key = i.next();
				RegistrationData data = (RegistrationData)key.attachment();
				try {
					if ((key.interestOps() & interestOp) == 0) {
						continue;
					}
				} catch (CancelledKeyException e) {
					// get quite a few of these exceptions, ignore the key
					continue;
				}
				long	stall_time = now - data.lastSelectSuccessTime;
				if (stall_time < 0) {
					data.lastSelectSuccessTime	= now;
				} else {
					if (stall_time > WRITE_SELECTOR_DEBUG_MAX_TIME) {
						Logger.log(
						new LogEvent(LOGID,LogEvent.LT_WARNING,"Write select for " + key.channel() + " stalled for " + stall_time ));
							// hack - trigger a dummy write select to see if things are still OK
						if (key.isValid()) {
							if (pauseAfterSelect) {
								key.interestOps(key.interestOps() & ~interestOp);
							}
							if (parent.selectSuccess( data.listener, data.channel, data.attachment)) {
								data.nonProgressCount = 0;
							}
						} else {
							key.cancel();
							parent.selectFailure(data.listener, data.channel, data.attachment, new Throwable("key is invalid" ));
						}
					}
				}
			}
		}

		// if any of the ready keys hasn't made any progress then enforce minimum sleep period to avoid
		// spinning
		if (totalKeyCount == 0 || progressMadeKeyCount != totalKeyCount) {
			long time_diff = SystemTime.getCurrentTime() - selectStartTime;
			if (time_diff < timeout && time_diff >= 0) {	//ensure that it always takes at least 'timeout' time to complete the select op
				try {	Thread.sleep(timeout - time_diff);	} catch (Throwable e) { e.printStackTrace(); }
			}
		} else {
			/*
			if (rm_test_fix) {
				long time_diff = SystemTime.getCurrentTime() - select_start_time;
				if (time_diff < 10 && time_diff >= 0) {
					try {	Thread.sleep(10 - time_diff);	} catch (Throwable e) { e.printStackTrace(); }
				}
			}
			*/
		}
		/*
		if (rm_trace) {
			if (select_start_time - rm_flag_last_log > 10000) {
				rm_flag_last_log	= select_start_time;
				Iterator it = rm_listener_map.entrySet().iterator();
				String	str = "";
				while (it.hasNext()) {
					Map.Entry	entry = (Map.Entry)it.next();
					Class	cla = (Class)entry.getKey();
					String	name = cla.getName();
					int		pos = name.lastIndexOf('.');
					name = name.substring(pos+1);
					int[]	counts = (int[])entry.getValue();
					str += (str.length()==0?"":",")+ name + ":" + counts[0]+"/"+counts[1]+"/"+counts[2]+"/"+counts[3];
				}
		 			Debug.outNoStack("RM trace: " + hashCode() + ": op=" + INTEREST_OP + "-" + str);
			}
		}
		*/
		return count;
	}

	/**
	 * Note that you have to ensure that a select operation is performed on the normal select
	 * loop *after* destroying the selector to actually cause the destroy to occur
	 */
	public void destroy() {
		destroyed	= true;
	}

	protected void closeExistingSelector() {
		for (Iterator<SelectionKey> i = selector.keys().iterator(); i.hasNext();) {
		SelectionKey key = i.next();
		RegistrationData data = (RegistrationData)key.attachment();
		parent.selectFailure(data.listener, data.channel, data.attachment, new Throwable("selector destroyed" ));
		}

		try {
		selector.close();

		AEDiagnostics.log("seltrace", "Selector destroyed for '" + parent.getName() + "'," + selectorGuard.getType());
		}
		catch (Throwable t) { t.printStackTrace(); }
	}


	private static class RegistrationData {
		protected final AbstractSelectableChannel channel;
		protected final VirtualChannelSelector.VirtualAbstractSelectorListener listener;
		protected final Object attachment;

		protected int 	nonProgressCount;
		protected long	lastSelectSuccessTime;

		private RegistrationData(AbstractSelectableChannel _channel, VirtualChannelSelector.VirtualAbstractSelectorListener _listener, Object _attachment) {
			channel 		= _channel;
			listener		= _listener;
			attachment 		= _attachment;

			lastSelectSuccessTime	= SystemTime.getCurrentTime();
		}
	}

}
