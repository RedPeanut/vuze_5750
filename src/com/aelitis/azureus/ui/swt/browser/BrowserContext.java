/*
 * Created on Jul 19, 2006 10:16:26 PM
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
package com.aelitis.azureus.ui.swt.browser;

import java.net.*;
import java.util.*;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.TitleListener;
import org.eclipse.swt.browser.TitleEvent;
import org.eclipse.swt.browser.ProgressListener;
import org.eclipse.swt.browser.ProgressEvent;
import org.eclipse.swt.browser.LocationListener;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.browser.OpenWindowListener;
import org.eclipse.swt.browser.CloseWindowListener;
import org.eclipse.swt.browser.WindowEvent;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.*;
import org.gudy.azureus2.core3.util.*;
import org.gudy.azureus2.plugins.utils.StaticUtilities;
import org.gudy.azureus2.plugins.utils.resourcedownloader.ResourceDownloader;
import org.gudy.azureus2.pluginsimpl.local.PluginInitializer;
import org.gudy.azureus2.ui.swt.BrowserWrapper;
import org.gudy.azureus2.ui.swt.Utils;
import org.gudy.azureus2.ui.swt.components.shell.ShellFactory;
import org.gudy.azureus2.ui.swt.mainwindow.TorrentOpener;
import org.gudy.azureus2.ui.swt.shells.MessageBoxShell;

import com.aelitis.azureus.core.messenger.ClientMessageContextImpl;
import com.aelitis.azureus.core.messenger.browser.listeners.BrowserMessageListener;
import com.aelitis.azureus.core.messenger.config.PlatformConfigMessenger;
import com.aelitis.azureus.core.vuzefile.VuzeFile;
import com.aelitis.azureus.core.vuzefile.VuzeFileHandler;
import com.aelitis.azureus.ui.swt.browser.msg.MessageDispatcherSWT;
import com.aelitis.azureus.util.ConstantsVuze;
import com.aelitis.azureus.util.JSONUtils;
import com.aelitis.azureus.util.UrlFilter;

/**
 * Manages the context for a single SWT {@link Browser} component,
 * including listeners and messages.
 *
 * @author dharkness
 * @created Jul 19, 2006
 */
public class BrowserContext
	extends ClientMessageContextImpl
	implements DisposeListener
{
	private static final String CONTEXT_KEY = "BrowserContext";

	private static final String KEY_ENABLE_MENU = "browser.menu.enable";

	private BrowserWrapper browser;

	private Display display;

	private boolean pageLoading = false;

	private long pageLoadingStart = 0;

	private long pageLoadingEnd = 0;

	private String lastValidURL = null;

	private final boolean forceVisibleAfterLoad;

	private TimerEventPeriodic checkURLEvent;

	private Control widgetWaitIndicator;

	private MessageDispatcherSWT messageDispatcherSWT;

	protected boolean wiggleBrowser = Utils.isCarbon;

	private torrentURLHandler		torrentURLHandler;

	private List loadingListeners = Collections.EMPTY_LIST;

	private long pageLoadTime;

	private long contentNetworkID = ConstantsVuze.DEFAULT_CONTENT_NETWORK_ID;

	private AEMonitor mon_listJS = new AEMonitor("listJS");

	private List<String> listJS = new ArrayList<String>(1);

	private boolean allowPopups = true;

	private volatile boolean 	autoReloadPending = false;
	private String[]			lastRetryData;

	/**
	 * Creates a context and registers the given browser.
	 *
	 * @param id unique identifier of this context
	 * @param browser the browser to be registered
	 */
	public BrowserContext(
		String 			_id,
		BrowserWrapper 	_browser,
		Control 		_widgetWaitingIndicator,
		boolean 		_forceVisibleAfterLoad ) {
		super(_id, null);

		browser 				= _browser;
		forceVisibleAfterLoad 	= _forceVisibleAfterLoad;
		widgetWaitIndicator 	= _widgetWaitingIndicator;

		// System.out.println("Registered browser context: id=" + getID());

		messageDispatcherSWT = new MessageDispatcherSWT(this);

		setMessageDispatcher(messageDispatcherSWT);

		final TimerEventPerformer showBrowersPerformer = new TimerEventPerformer() {
			public void perform(TimerEvent event) {
				if (browser != null && !browser.isDisposed()) {
					Utils.execSWTThread(new AERunnable() {
						public void runSupport() {
							if (forceVisibleAfterLoad && browser != null
									&& !browser.isDisposed() && !browser.isVisible()) {
								browser.setVisible(true);
							}
						}
					});
				}
			}
		};

		final TimerEventPerformer hideIndicatorPerformer = new TimerEventPerformer() {
			public void perform(TimerEvent event) {
				setPageLoading(false, browser.getUrl());
				if (widgetWaitIndicator != null && !widgetWaitIndicator.isDisposed()) {
					Utils.execSWTThread(new AERunnable() {
						public void runSupport() {
							if (widgetWaitIndicator != null
									&& !widgetWaitIndicator.isDisposed()) {
								widgetWaitIndicator.setVisible(false);
							}
						}
					});
				}
			}
		};

		final TimerEventPerformer checkURLEventPerformer = new TimerEventPerformer() {
			public void perform(TimerEvent event) {
				if (browser != null && !browser.isDisposed()) {
					Utils.execSWTThreadLater(0, new AERunnable() {
						public void runSupport() {
							if (browser != null && !browser.isDisposed()) {
								browser.execute("try { "
										+ "tuxLocString = document.location.toString();"
										+ "if (tuxLocString.indexOf('res://') == 0) {"
										+ "  document.title = 'err: ' + tuxLocString;"
										+ "} else {"
										+ "  tuxTitleString = document.title.toString();"
										+ "  if (tuxTitleString.indexOf('408 ') == 0 || tuxTitleString.indexOf('503 ') == 0 || tuxTitleString.indexOf('500 ') == 0) "
										+ "  { document.title = 'err: ' + tuxTitleString; } " + "}"
										+ "} catch (e) { }");
							}
						}
					});
				}
			}
		};

		if (forceVisibleAfterLoad) {

			browser.setVisible(false);
		}

		setPageLoading(false, browser.getUrl());

		if (widgetWaitIndicator != null && !widgetWaitIndicator.isDisposed()) {
			widgetWaitIndicator.setVisible(false);
		}

		browser.addTitleListener(new TitleListener() {
			public void changed(TitleEvent event) {

				/*
				 * The browser might have been disposed already by the time this method is called
				 */
				if (browser == null || browser.isDisposed() || browser.getShell().isDisposed()) {
					return;
				}

				if (!browser.isVisible()) {
					SimpleTimer.addEvent("Show Browser",
							System.currentTimeMillis() + 700, showBrowersPerformer);
				}
				if (event.title.startsWith("err: ")) {
					fillWithRetry(event.title, "err in title");
				}
			}
		});

		browser.addProgressListener(new ProgressListener() {
			public void changed(ProgressEvent event) {
				//int pct = event.total == 0 ? 0 : 100 * event.current / event.total;
				//System.out.println(pct + "%/" + event.current + "/" + event.total);
			}

			public void completed(ProgressEvent event) {
				/*
				 * The browser might have been disposed already by the time this method is called
				 */
				if (browser == null || browser.isDisposed() || browser.getShell().isDisposed()) {
					return;
				}

				checkURLEventPerformer.perform(null);
				if (forceVisibleAfterLoad && !browser.isVisible()) {
					browser.setVisible(true);
				}

				browser.execute("try { if (azureusClientWelcome) { azureusClientWelcome('"
						+ ConstantsVuze.AZID
						+ "',"
						+ "{ 'azv':'"
						+ org.gudy.azureus2.core3.util.Constants.AZUREUS_VERSION
						+ "', 'browser-id':'" + getID() + "' }" + ");} } catch (e) { }");

				if (org.gudy.azureus2.core3.util.Constants.isCVSVersion()
						|| System.getProperty("debug.https", null) != null) {
					if (browser.getUrl().indexOf("https") == 0) {
						browser.execute("try { o = document.getElementsByTagName('body'); if (o) o[0].style.borderTop = '2px dotted #3b3b3b'; } catch (e) {}");
					}
				}

				if (wiggleBrowser) {
					Shell shell = browser.getShell();
					Point size = shell.getSize();
					size.x -= 1;
					size.y -= 1;
					shell.setSize(size);
					size.x += 1;
					size.y += 1;
					shell.setSize(size);
				}
			}
		});

		checkURLEvent = SimpleTimer.addPeriodicEvent("checkURL", 10000,
				checkURLEventPerformer);


		browser.addOpenWindowListener(new OpenWindowListener() {
			public void open(WindowEvent event) {
				if (browser == null || browser.isDisposed() || browser.getShell().isDisposed()) {
					return;
				}
				event.required = true;

				if (browser.getUrl().contains("js.debug=1")) {
					final Shell shell = ShellFactory.createMainShell(SWT.SHELL_TRIM);
					shell.setLayout(new FillLayout());
					shell.setSize(920, 500);
					BrowserWrapper subBrowser = BrowserWrapper.createBrowser(shell,
							Utils.getInitialBrowserStyle(SWT.NONE));
					subBrowser.addCloseWindowListener(new CloseWindowListener() {
						public void close(WindowEvent event) {
							shell.dispose();
						}
					});
					shell.open();
					subBrowser.setBrowser(event);

				} else {

					final BrowserWrapper subBrowser = BrowserWrapper.createBrowser(browser.getControl(),
							Utils.getInitialBrowserStyle(SWT.NONE));
					subBrowser.addLocationListener(new LocationListener() {
						public void changed(LocationEvent arg0) {
							// TODO Auto-generated method stub

						}
						public void changing(LocationEvent event) {
							event.doit = false;
							boolean doLinkExternally = PlatformConfigMessenger.areLinksExternal(browser.getUrl());
							if (doLinkExternally) {
								Utils.launch(event.location);
							} else if (allowPopups()
									&& !UrlFilter.getInstance().urlIsBlocked(event.location)
									&& (event.location.startsWith("http://") || event.location.startsWith("https://"))) {
								debug("open sub browser: " + event.location);
								Utils.launch(event.location);
							} else {
								debug("blocked open sub browser: " + event.location);
							}
								// parg 2012/10/2 increase delay in case causing crashes

							Utils.execSWTThreadLater(1000, new AERunnable() {
								public void runSupport() {
									subBrowser.dispose();
								}
							});
						}
					});
					subBrowser.setBrowser(event);
				}
			}
		});

		final BrowserWrapper bw = browser;

		browser.addLocationListener(new LocationListener() {
			private TimerEvent timerevent;

			public void changed(LocationEvent event) {
				if (browser.isDisposed() || browser.getShell().isDisposed()) {
					return;
				}
				debug("browser.changed " + event.location);
				if (timerevent != null) {
					timerevent.cancel();
				}
				checkURLEventPerformer.perform(null);
				setPageLoading(false, event.top ? event.location : null);
				if (widgetWaitIndicator != null && !widgetWaitIndicator.isDisposed()) {
					widgetWaitIndicator.setVisible(false);
				}

				// event.top is only filled on changed event (not changing!)
				if (!event.top) {
					return;
				}
				String location = event.location.toLowerCase();
				boolean isWebURL = location.startsWith("http://")
						|| location.startsWith("https://");
				if (!isWebURL) {
					if (event.location.startsWith("res://")) {
						fillWithRetry(event.location, "top changed");
						return;
					}
					// we don't get a changed state on non URLs (mailto, javascript, etc)
				}

				if (UrlFilter.getInstance().isWhitelisted(event.location)) {
					lastValidURL = event.location;
				}

				//System.out.println("cd" + event.location);
			}

			public void changing(LocationEvent event) {
				// event.top is always false.  changed event has it set though..
				debug("browser.changing " + event.location + " from "
						+ browser.getUrl() + ";" + event.top);
				/*
				 * The browser might have been disposed already by the time this method is called
				 */
				if (browser.isDisposed() || browser.getShell().isDisposed()) {
					return;
				}

				String event_location = event.location;

				//Utils.openMessageBox(Utils.findAnyShell(), SWT.OK, "Location Changing", "Navigating to " + event_location);

				if (event_location.startsWith("javascript")
						&& event_location.indexOf("back()") > 0) {
					if (browser.isBackEnabled()) {
						browser.back();
					} else if (lastValidURL != null) {
						fillWithRetry(event_location, "back");
					}
					return;
				}


				String lowerLocation = event_location.toLowerCase();
				boolean isOurURI = lowerLocation.startsWith("magnet:")
						|| lowerLocation.startsWith("vuze:")
						|| lowerLocation.startsWith("bc:")
						|| lowerLocation.startsWith("bctp:")
						|| lowerLocation.startsWith("dht:");

				if (isOurURI) {
					event.doit = false;
					TorrentOpener.openTorrent(event_location);
					return;
				}

				boolean isWebURL = lowerLocation.startsWith("http://")
						|| lowerLocation.startsWith("https://");
				if (!isWebURL) {
					// we don't get a changed state on non URLs (mailto, javascript, etc)
					return;
				}

				boolean blocked = UrlFilter.getInstance().urlIsBlocked(event_location);

				if (!allowPopups()) {
					if (blocked) {
						return;
					}

					String curURL = browser.getUrl().toLowerCase();

					boolean isPageLoadingOrRecent = isPageLoading()
							|| (pageLoadingEnd > 0 && pageLoadingEnd + 500 > SystemTime.getCurrentTime())
							|| event_location.contains(".admonkey.");

					boolean wasSearch = curURL.startsWith(
							"http://www.google.com/#q")
							|| curURL.startsWith("http://www.google.com/search")
							|| PlatformConfigMessenger.areLinksExternal(curURL);
					boolean isSearch = event_location.startsWith("http://www.google.com/#q")
							|| (event_location.startsWith("http://www.google.com/search"))
							|| PlatformConfigMessenger.areLinksExternal(event_location);

					if (wasSearch
							&& !isSearch
							&& !curURL.equalsIgnoreCase(event_location)
							&& !event_location.equals("about:blank")
							&& !isPageLoadingOrRecent) {
  					event.doit = false;
						String[] contentTypes = getContentTypes(event_location, bw.getUrl());

						boolean isTorrent = false;
						for (String s : contentTypes) {

							if (s != null) {

								if (s.contains("torrent")) {

									isTorrent = true;
								}
							}
						}

						if (!isTorrent || !openTorrent( bw, event)) {
							Utils.launch(event.location);
						}
  					return;
  				}

				}

				if (blocked) {
					event.doit = false;
					new MessageBoxShell(SWT.OK, "URL blocked", "Tried to open "
							+ event_location + " but it's blocked").open(null);
					browser.back();
				} else {
					if (UrlFilter.getInstance().isWhitelisted(event_location)) {
						lastValidURL = event_location;
					}
					setPageLoading(true, event.location);
					if (event.top) {
						if (widgetWaitIndicator != null && !widgetWaitIndicator.isDisposed()) {
							widgetWaitIndicator.setVisible(true);
						}

						// Backup in case changed(..) is never called
						timerevent = SimpleTimer.addEvent("Hide Indicator",
								System.currentTimeMillis() + 20000, hideIndicatorPerformer);
					} else {
						boolean isTorrent 	= false;
						boolean isVuzeFile	= false;

						//Try to catch .torrent files
						// URLs ending in "?torrent" on Amazon S3's Simple Storage Service
						// return an auto-generated a torrent based on the url, but only on
						// GET.  HEAD will fail, so we have to trap and assume
						if (event_location.endsWith(".torrent") || event_location.endsWith("?torrent")) {
							isTorrent = true;
						} else {
							//If it's not obviously a web page

							boolean	can_rpc = UrlFilter.getInstance().urlCanRPC(event_location);

							boolean	test_for_torrent 	= !can_rpc &&
								!event_location.contains(".htm");
							boolean	test_for_vuze		= can_rpc &&  (event_location.endsWith(".xml" ) || event_location.endsWith(".vuze"));

							if (test_for_torrent || test_for_vuze) {

								String[] contentTypes = getContentTypes(event_location, bw.getUrl());

								for (String s : contentTypes) {

									if (s != null) {

										if (test_for_torrent && s.contains("torrent")) {

											isTorrent = true;
										}

										if (test_for_vuze && s.contains("vuze")) {

											isVuzeFile = true;
										}
									}
								}


								//System.out.println("Test for t/v: " + event_location + " -> " + isTorrent + "/" + isVuzeFile);
							}
						}

						if (isTorrent) {

							openTorrent(bw,event);

						} else if (isVuzeFile) {

							event.doit = false;
							setPageLoading(false, event.location);

							try {
								String referer_str = null;

								try {
									referer_str = new URL(bw.getUrl()).toExternalForm();

								} catch (Throwable e) {
								}

								Map headers = UrlUtils.getBrowserHeaders(referer_str);

								String cookies = (String) bw.getData("current-cookies");

								if (cookies != null) {

									headers.put("Cookie", cookies);
								}

								ResourceDownloader rd = StaticUtilities.getResourceDownloaderFactory().create(new URL( event_location));

								VuzeFileHandler vfh = VuzeFileHandler.getSingleton();

								VuzeFile vf = vfh.loadVuzeFile( rd.download());

								if (vf == null) {

									event.doit = true;
									setPageLoading(true, event.location);

								} else {

									vfh.handleFiles(new VuzeFile[]{ vf }, 0);
								}
							} catch (Throwable e) {

								e.printStackTrace();
							}
						}
					}
				}
			}
		});

		browser.setData(CONTEXT_KEY, this);
		browser.addDisposeListener(this);

		// enable right-click context menu only if system property is set
		final boolean enableMenu = System.getProperty(KEY_ENABLE_MENU, "0").equals(
				"1");
		browser.addListener(SWT.MenuDetect, new Listener() {
			// @see org.eclipse.swt.widgets.Listener#handleEvent(org.eclipse.swt.widgets.Event)
			public void handleEvent(Event event) {
				event.doit = enableMenu;
			}
		});

		messageDispatcherSWT.registerBrowser(browser);
		this.display = browser.getDisplay();
	}

	protected boolean openTorrent(BrowserWrapper browser, LocationEvent event) {
		event.doit = false;
		setPageLoading(false, event.location);

		try {
			String referer_str = null;

			try {
				referer_str = new URL(browser.getUrl()).toExternalForm();

			} catch (Throwable e) {
			}

			final Map headers = UrlUtils.getBrowserHeaders(referer_str);


			String cookies = (String)browser.getData("current-cookies");

			if (cookies != null) {

				headers.put("Cookie", cookies);
			}

			final String	url = event.location;

			if (torrentURLHandler != null) {

				try {
					torrentURLHandler.handleTorrentURL(url);

				} catch (Throwable e) {

					Debug.printStackTrace(e);
				}
			}

			Utils.getOffOfSWTThread(new AERunnable() {

				public void runSupport() {
					try {
						PluginInitializer.getDefaultInterface().getDownloadManager().addDownload(
								new URL(url), headers);
					} catch (Exception e) {
						Debug.out(e);
					}
				}
			});

			return true;
		} catch (Throwable e) {
			Debug.out(e);
			return false;
		}
	}

	protected String[] getContentTypes(String event_location, String _referer) {
		try {
			//See what the content type is
			URL url = new URL(event_location);
			URLConnection conn = url.openConnection();

			// we're only trying to get the content type so just use head

			((HttpURLConnection) conn).setRequestMethod("HEAD");

			String referer_str = null;

			try {
				URL referer = new URL(_referer);

				if (referer != null) {

					referer_str = referer.toExternalForm();

				}
			} catch (Throwable e) {
			}


			UrlUtils.setBrowserHeaders(conn, referer_str);

			UrlUtils.connectWithTimeouts(conn, 1500, 5000);

			String contentType = conn.getContentType();
			String contentDisposition = conn.getHeaderField("Content-Disposition");


			// There's a bug in the ":3" server where a HEAD followed by a GET on the
			// same content results in a corrupt GET reply
			String server = conn.getHeaderField("Server");
			if ("application/x-bittorrent".equals(contentType) && ":3".equals(server)) {
				Thread.sleep(6000);
			}

			return new String[] {
				contentType,
				contentDisposition
			};
		} catch (Throwable e) {
		}

		return new String[0];
	}

	/**
	 * @param b
	 * @param url
	 *
	 * @since 3.1.1.1
	 */
	protected void setPageLoading(boolean b, String url) {
		//System.out.println("SPL: " + b + ";" + url + ";" + Debug.getCompressedStackTrace());
		// we may get multiple "load done"s (from each frame) which we don't
		// want to skip
		if (b && pageLoading) {
			return;
		}
		mon_listJS.enter();
		try {
  		pageLoading = b;
  		if (pageLoading) {
  			pageLoadingStart = SystemTime.getCurrentTime();
  			pageLoadTime = -1;
  		} else if (pageLoadingStart > 0 && url != null) {
  			pageLoadingEnd = SystemTime.getCurrentTime();
  			pageLoadTime = pageLoadingEnd - pageLoadingStart;
  			executeInBrowser("clientSetLoadTime(" + pageLoadTime + ");");

  			pageLoadingStart = 0;
  		}
  		if (!pageLoading && listJS.size() > 0) {
  			debug(listJS.size() + " javascripts queued.  Executing now..");
  			for (String js : listJS) {
					executeInBrowser(js);
				}
  			listJS.clear();
  		}
		} finally {
			mon_listJS.exit();
		}

		Object[] listeners = loadingListeners.toArray();
		for (int i = 0; i < listeners.length; i++) {
			loadingListener l = (loadingListener) listeners[i];
			l.browserLoadingChanged(b, url);
		}
	}

	public void setTorrentURLHandler(
		torrentURLHandler handler) {
		torrentURLHandler = handler;
	}

	public void setAutoReloadPending(
		final boolean	is_pending,
		final boolean	aborted) {
		autoReloadPending = is_pending;

		Utils.execSWTThread(
			new Runnable() {
				public void run() {
					if (!is_pending) {

						if (aborted) {

							if (lastRetryData != null) {

								if (!browser.isDisposed()) {

									fillWithRetry(lastRetryData[0], lastRetryData[1]);
								}
							}
						}
					}
				}
			});
	}

	public void fillWithRetry(String s, String s2) {

		Color bg = browser.getDisplay().getSystemColor(SWT.COLOR_WIDGET_BACKGROUND);
		Color fg = browser.getDisplay().getSystemColor(SWT.COLOR_WIDGET_FOREGROUND);

		if (autoReloadPending) {

			lastRetryData = new String[]{ s, s2 };

			browser.setText("<html><body style='overflow:auto; font-family: verdana; font-size: 10pt' bgcolor=#"
					+ Utils.toColorHexString(bg)
					+ " text=#" + Utils.toColorHexString(fg) + ">"
					+ "<br>Please wait while Vuze attempts to load the page (this can take a moment or two initially) ...<br>"
					+ "</body></html>");
		} else {

			browser.setText("<html><body style='overflow:auto; font-family: verdana; font-size: 10pt' bgcolor=#"
					+ Utils.toColorHexString(bg)
					+ " text=#" + Utils.toColorHexString(fg) + ">"
					+ "<br>Sorry, there was a problem loading this page.<br> "
					+ "Please check if your internet connection is working and click <a href='"
					+ lastValidURL
					+ "' style=\"color: rgb(100, 155, 255); \">retry</a> to continue."
					+ "<div style='word-wrap: break-word'><font size=1 color=#"
					+ Utils.toColorHexString(bg)
					+ ">"
					+ s + "<br><br>" + s2
					+ "</font></div>" + "</body></html>");
		}
	}

	private void deregisterBrowser() {
		if (browser == null) {
			throw new IllegalStateException("Context " + getID()
					+ " doesn't have a registered browser");
		}

		// System.out.println("Unregistered browser context: id=" + getID());

		if (!browser.isDisposed()) {
  		browser.setData(CONTEXT_KEY, null);
  		browser.removeDisposeListener(this);
  		messageDispatcherSWT.deregisterBrowser(browser);
		}
		browser = null;

		if (checkURLEvent != null && !checkURLEvent.isCancelled()) {
			checkURLEvent.cancel();
			checkURLEvent = null;
		}
	}

	/**
	 * Accesses the context associated with the given browser.
	 *
	 * @param browser holds the context in its application data map
	 * @return the browser's context or <code>null</code> if there is none
	 */
	/*
	public static BrowserContext getContext(Browser browser) {
		Object data = browser.getData(CONTEXT_KEY);
		if (data != null && !(data instanceof BrowserContext)) {
			Debug.out("Data in Browser with key " + CONTEXT_KEY
					+ " is not a BrowserContext");
			return null;
		}

		return (BrowserContext) data;
	}
	*/

	public void addMessageListener(BrowserMessageListener listener) {
		messageDispatcherSWT.addListener(listener);
	}

	public Object getBrowserData(String key) {
		return browser.getData(key);
	}

	public void setBrowserData(String key, Object value) {
		browser.setData(key, value);
	}

	public boolean sendBrowserMessage(String key, String op) {
		return sendBrowserMessage(key, op, (Map) null);
	}

	public boolean sendBrowserMessage(String key, String op, Map params) {
		StringBuilder msg = new StringBuilder();
		msg.append("az.msg.dispatch('").append(key).append("', '").append(op).append(
				"'");
		if (params != null) {
			msg.append(", ").append(JSONUtils.encodeToJSON(params));
		}
		msg.append(")");

		return executeInBrowser(msg.toString());
	}

	public boolean sendBrowserMessage(String key, String op, Collection params) {
		StringBuilder msg = new StringBuilder();
		msg.append("az.msg.dispatch('").append(key).append("', '").append(op).append(
				"'");
		if (params != null) {
			msg.append(", ").append(JSONUtils.encodeToJSON(params));
		}
		msg.append(")");

		return executeInBrowser(msg.toString());
	}

	protected boolean maySend(String key, String op, Map params) {
		return !pageLoading;
	}

	public boolean executeInBrowser(final String javascript) {
		mon_listJS.enter();
		try {
			if (!mayExecute(javascript)) {
				listJS.add(javascript);
				return false;
			}
		} finally {
			mon_listJS.exit();
		}

		if (display == null || display.isDisposed()) {
			debug("CANNOT: browser.execute(" + getShortJavascript(javascript) + ")");
			return false;
		}

		// swallow errors silently
		final String reallyExecute = "try { " + javascript + " } catch (e) { }";
		Utils.execSWTThread(new AERunnable() {
			public void runSupport() {
				if (browser == null || browser.isDisposed()) {
					debug("CANNOT: browser.execute(" + getShortJavascript(javascript)
							+ " )");
				} else if (!browser.execute(reallyExecute)) {
					debug("FAILED: browser.execute(" + getShortJavascript(javascript)
							+ " )");
				} else {
					debug("SUCCESS: browser.execute(" + getShortJavascript(javascript)
							+ " )");
				}
			}
		});

		return true;
	}

	protected boolean mayExecute(String javascript) {
		return !pageLoading;
	}

	public void widgetDisposed(DisposeEvent event) {
		if (event.widget == browser.getControl()) {
			deregisterBrowser();
		}
	}

	private String getShortJavascript(String javascript) {
		if (javascript.length() < (256 + 3 + 256)) {
			return javascript;
		}
		StringBuilder result = new StringBuilder();
		result.append(javascript.substring(0, 256));
		result.append("...");
		result.append(javascript.substring(javascript.length() - 256));
		return result.toString();
	}

	public void setWiggleBrowser(boolean wiggleBrowser) {
		this.wiggleBrowser = wiggleBrowser;
	}

	public boolean isPageLoading() {
		return pageLoading;
	}


	public void addListener(loadingListener l) {
		if (loadingListeners == Collections.EMPTY_LIST) {
			loadingListeners = new ArrayList(1);
		}
		loadingListeners.add(l);
	}

	public static interface loadingListener {
		public void browserLoadingChanged(boolean loading, String url);
	}

	public long getContentNetworkID() {
		return contentNetworkID;
	}

	public void setContentNetworkID(long contentNetworkID) {
		this.contentNetworkID = contentNetworkID;
	}

	public void setAllowPopups(boolean allowPopups) {
		this.allowPopups = allowPopups;
	}

	public boolean allowPopups() {
		return allowPopups;
	}
}