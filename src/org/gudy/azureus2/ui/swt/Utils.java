/*
 * File    : Utils.java
 * Created : 25 sept. 2003 16:15:07
 * By      : Olivier
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

package org.gudy.azureus2.ui.swt;

import java.io.*;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.dnd.*;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.*;
import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.config.ParameterListener;
import org.gudy.azureus2.core3.disk.DiskManagerFileInfo;
import org.gudy.azureus2.core3.download.DownloadManager;
import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.logging.LogEvent;
import org.gudy.azureus2.core3.logging.LogIDs;
import org.gudy.azureus2.core3.logging.Logger;
import org.gudy.azureus2.core3.util.*;
import org.gudy.azureus2.platform.PlatformManager;
import org.gudy.azureus2.platform.PlatformManagerCapabilities;
import org.gudy.azureus2.platform.PlatformManagerFactory;
import org.gudy.azureus2.plugins.PluginInterface;
import org.gudy.azureus2.plugins.PluginManager;
import org.gudy.azureus2.plugins.disk.DiskManagerEvent;
import org.gudy.azureus2.plugins.disk.DiskManagerListener;
import org.gudy.azureus2.plugins.platform.PlatformManagerException;
import org.gudy.azureus2.plugins.ui.Graphic;
import org.gudy.azureus2.plugins.utils.PooledByteBuffer;
import org.gudy.azureus2.pluginsimpl.local.PluginCoreUtils;
import org.gudy.azureus2.ui.swt.components.BufferedLabel;
import org.gudy.azureus2.ui.swt.mainwindow.Colors;
import org.gudy.azureus2.ui.swt.mainwindow.SWTThread;
import org.gudy.azureus2.ui.swt.mainwindow.TorrentOpener;
import org.gudy.azureus2.ui.swt.pluginsimpl.UISWTGraphicImpl;
import org.gudy.azureus2.ui.swt.shells.MessageBoxShell;

import com.aelitis.azureus.core.AzureusCoreFactory;
import com.aelitis.azureus.core.util.GeneralUtils;
import com.aelitis.azureus.core.util.LaunchManager;
import com.aelitis.azureus.plugins.I2PHelpers;
import com.aelitis.azureus.ui.UIFunctions;
import com.aelitis.azureus.ui.UIFunctionsManager;
import com.aelitis.azureus.ui.UIFunctionsUserPrompter;
import com.aelitis.azureus.ui.swt.UIFunctionsManagerSWT;
import com.aelitis.azureus.ui.swt.UIFunctionsSWT;
import com.aelitis.azureus.ui.swt.imageloader.ImageLoader;

/**
 * @author Olivier
 *
 */
public class Utils {
	
	private static final int DEFAULT_DPI = Constants.isOSX ? 72 : 96;
	public static final String GOOD_STRING = "(/|,jI~`gy";
	public static final boolean isGTK = SWT.getPlatform().equals("gtk");
	public static final boolean isGTK3 = Utils.isGTK
			&& System.getProperty("org.eclipse.swt.internal.gtk.version", "2").startsWith("3");
	public static final boolean isCarbon = SWT.getPlatform().equals("carbon");
	public static final boolean isCocoa = SWT.getPlatform().equals("cocoa");
	/** Some platforms expand the last column to fit the remaining width of
	 * the table.
	 */
	public static final boolean LAST_TABLECOLUMN_EXPANDS = isGTK;
	/** GTK already handles alternating background for tables */
	public static final boolean TABLE_GRIDLINE_IS_ALTERNATING_COLOR = isGTK || isCocoa;
	public static int BUTTON_MARGIN;
	public static int BUTTON_MINWIDTH = Constants.isOSX ? 90 : 70;
	/**
	 * Debug/Diagnose SWT exec calls.  Provides usefull information like how
	 * many we are queuing up, and how long each call takes.  Good to turn on
	 * occassionally to see if we coded something stupid.
	 */
	private static final boolean DEBUG_SWTEXEC = System.getProperty(
			"debug.swtexec", "0").equals("1");
	private static ArrayList<Runnable> queue;
	private static AEDiagnosticsLogger diagLogger;
	private static Image[] shellIcons = null;
	private static Image icon128;
	private final static String[] shellIconNames = {
		"azureus",
		"azureus32",
		"azureus64",
		"azureus128"
	};
	
	public static final Rectangle EMPTY_RECT = new Rectangle(0, 0, 0, 0);
	private static int userMode;
	private static boolean isAZ2;

	static {
		if (DEBUG_SWTEXEC) {
			System.out.println("==== debug.swtexec=1, performance may be affected ====");
			queue = new ArrayList<Runnable>();
			diagLogger = AEDiagnostics.getLogger("swt");
			diagLogger.log("\n\nSWT Logging Starts");

			AEDiagnostics.addEvidenceGenerator(new AEDiagnosticsEvidenceGenerator() {
				public void generate(IndentWriter writer) {
					writer.println("SWT Queue:");
					writer.indent();
					for (Runnable r : queue) {
						if (r == null) {
							writer.println("NULL");
						} else {
							writer.println(r.toString());
						}
					}
					writer.exdent();
				}
			});
		} else {
			queue = null;
			diagLogger = null;
		}

		COConfigurationManager.addAndFireParameterListener("User Mode", new ParameterListener() {
			public void parameterChanged(String parameterName) {
				userMode = COConfigurationManager.getIntParameter("User Mode");
			}
		});
		COConfigurationManager.addAndFireParameterListener("ui", new ParameterListener() {
			public void parameterChanged(String parameterName) {
				isAZ2 = "az2".equals(COConfigurationManager.getStringParameter("ui"));
			}
		});
		// no need to listen, changing param requires restart
	    boolean smallOSXControl = COConfigurationManager.getBooleanParameter("enable_small_osx_fonts");
	    BUTTON_MARGIN = Constants.isOSX ? (smallOSXControl ? 10 : 12) : 6;
	}

	private static Set<DiskManagerFileInfo>	quick_view_active = new HashSet<DiskManagerFileInfo>();
	private static TimerEventPeriodic		quick_view_event;

	private static Point dpi;

	public static void initialize(Display display) {
		getDPI();
		// cache now to prevent invalid-thread access under some conditions later
		// in particular during plugin init of a custome column
		/*	org.eclipse.swt.SWTException: Invalid thread access
		    at org.eclipse.swt.SWT.error(SWT.java:4457)
		    at org.eclipse.swt.SWT.error(SWT.java:4372)
		    at org.eclipse.swt.SWT.error(SWT.java:4343)
		    at org.eclipse.swt.widgets.Display.error(Display.java:1258)
		    at org.eclipse.swt.widgets.Display.checkDevice(Display.java:764)
		    at org.eclipse.swt.graphics.Device.getDPI(Device.java:466)
		    at org.gudy.azureus2.ui.swt.Utils.getDPI(Utils.java:3641)
		    at org.gudy.azureus2.ui.swt.Utils.adjustPXForDPI(Utils.java:3654)
		    at com.aelitis.azureus.ui.common.table.impl.TableColumnImpl.init(TableColumnImpl.java:184)
		 */
	}

	public static boolean isAZ2UI() {
		return isAZ2;
	}

	public static void disposeComposite(Composite composite, boolean disposeSelf) {
		
		if (composite == null || composite.isDisposed())
			return;
		
		Control[] controls = composite.getChildren();
		for (int i = 0; i < controls.length; i++) {
			Control control = controls[i];
			if (control != null && !control.isDisposed()) {
				if (control instanceof Composite) {
					disposeComposite((Composite) control, true);
				}
				try {
					control.dispose();
				} catch (SWTException e) {
					Debug.printStackTrace(e);
				}
			}
		}
		
		// It's possible that the composite was destroyed by the child
		if (!composite.isDisposed() && disposeSelf)
			try {
				composite.dispose();
			} catch (SWTException e) {
				Debug.printStackTrace(e);
			}
	}

	public static void disposeComposite(Composite composite) {
		disposeComposite(composite, true);
	}

	/**
	 * Dispose of a list of SWT objects
	 *
	 * @param disposeList
	 */
	public static void disposeSWTObjects(List disposeList) {
		disposeSWTObjects(disposeList.toArray());
		disposeList.clear();
	}

	public static void disposeSWTObjects(Object[] disposeList) {
		if (disposeList == null) {
			return;
		}
		for (int i = 0; i < disposeList.length; i++) {
			try {
  			Object o = disposeList[i];
  			if (o instanceof Widget && !((Widget) o).isDisposed())
  				((Widget) o).dispose();
  			else if ((o instanceof Resource) && !((Resource) o).isDisposed()) {
  				((Resource) o).dispose();
  			}
			} catch (Exception e) {
				Debug.out("Warning: Disposal failed "
						+ Debug.getCompressedStackTrace(e, 0, -1, true));
			}
		}
	}

	/**
	 * Initializes the URL dialog with http://
	 * If a valid link is found in the clipboard, it will be inserted
	 * and the size (and location) of the dialog is adjusted.
	 * @param shell to set the dialog location if needed
	 * @param url the URL text control
	 * @param accept_magnets
	 *
	 * @author Rene Leonhardt
	 */
	public static void setTextLinkFromClipboard(final Shell shell,
			final Text url, boolean accept_magnets, boolean default_magnet) {
		String link = getLinkFromClipboard(shell.getDisplay(), accept_magnets, default_magnet);
		if (link != null)
			url.setText(link);
	}

	/**
	 * <p>Gets an URL from the clipboard if a valid URL for downloading has been copied.</p>
	 * <p>The supported protocols currently are http, https, and magnet.</p>
	 * @param display
	 * @param accept_magnets
	 * @return first valid link from clipboard, else "http://" or "magnet:"
	 */
	public static String getLinkFromClipboard(Display display,
			boolean accept_magnets, boolean default_magnet) {
		final Clipboard cb = new Clipboard(display);
		final TextTransfer transfer = TextTransfer.getInstance();

		String data = (String) cb.getContents(transfer);

		String text = UrlUtils.parseTextForURL(data, accept_magnets);
		if (text == null) {
			return default_magnet?"magnet:":"http://";
		}

		return text;
	}

	public static void centreWindow(Shell shell) {
		centreWindow(shell, true);
	}

	public static void centreWindow(Shell shell, boolean shrink_if_needed) {
		Rectangle centerInArea; // area to center in
		Rectangle displayArea;
		try {
			displayArea = shell.getMonitor().getClientArea();
		} catch (NoSuchMethodError e) {
			displayArea = shell.getDisplay().getClientArea();
		}

		if (shell.getParent() != null) {
			centerInArea = shell.getParent().getBounds();
		} else {
			centerInArea = displayArea;
		}

		Rectangle shellRect = shell.getBounds();

		if (shrink_if_needed) {
			if (shellRect.height > displayArea.height) {
				shellRect.height = displayArea.height;
			}
			if (shellRect.width > displayArea.width - 50) {
				shellRect.width = displayArea.width;
			}
		}

		shellRect.x = centerInArea.x + (centerInArea.width - shellRect.width) / 2;
		shellRect.y = centerInArea.y + (centerInArea.height - shellRect.height) / 2;

		shell.setBounds(shellRect);
	}

	/**
	 * Centers a window relative to a control. That is to say, the window will be located at the center of the control.
	 * @param window
	 * @param control
	 */
	public static void centerWindowRelativeTo(final Shell window,
			final Control control) {
		if (control == null || control.isDisposed() || window == null || window.isDisposed()) {
			return;
		}
		final Rectangle bounds = control.getBounds();
		final Point shellSize = window.getSize();
		window.setLocation(bounds.x + (bounds.width / 2) - shellSize.x / 2,
				bounds.y + (bounds.height / 2) - shellSize.y / 2);
	}

	public static List<RGB>
	getCustomColors() {
        String custom_colours_str = COConfigurationManager.getStringParameter("color.parameter.custom.colors", "");

        String[] bits = custom_colours_str.split(";");

        List<RGB> custom_colours = new ArrayList<RGB>();

        for (String bit: bits) {

        	String[] x = bit.split(",");

        	if (x.length == 3) {

        		try {
        			custom_colours.add(new RGB( Integer.parseInt( x[0]),Integer.parseInt( x[1]),Integer.parseInt( x[2])));

        		} catch (Throwable f) {

        		}
        	}
        }

        return (custom_colours);
	}

	public static void updateCustomColors(
		RGB[]	new_cc) {
		if (new_cc != null) {

			String custom_colours_str = "";

			for (RGB colour: new_cc) {

				custom_colours_str += (custom_colours_str.isEmpty()?"":";") + colour.red + "," + colour.green + "," + colour.blue;
			}

			COConfigurationManager.setParameter("color.parameter.custom.colors", custom_colours_str);
		}
	}

	public static RGB
	showColorDialog(
		Composite	parent,
		RGB			existing) {
		Shell parent_shell = parent.getShell();

		return (showColorDialog( parent_shell, existing));
	}

	public static RGB
	showColorDialog(
		Shell		parent_shell,
		RGB			existing) {
		Shell centerShell = new Shell(parent_shell, SWT.NO_TRIM);

		try {
			Rectangle displayArea;

			try {
				displayArea = parent_shell.getMonitor().getClientArea();

			} catch (NoSuchMethodError e) {

				displayArea = parent_shell.getDisplay().getClientArea();
			}

				// no way to get actual dialog size - guess...

			final int x = displayArea.x + displayArea.width / 2 - 120;
			final int y = displayArea.y + displayArea.height / 2 - 170;

			centerShell.setLocation(x, y);

			ColorDialog cd = new ColorDialog(centerShell);

			cd.setRGB(existing);

			List<RGB> custom_colours = Utils.getCustomColors();

			if (existing != null) {

				custom_colours.remove(existing);
			}

			cd.setRGBs( custom_colours.toArray(new RGB[0]));

			RGB rgb = cd.open();

			if (rgb != null) {

				updateCustomColors( cd.getRGBs());
			}

			return (rgb);

		} finally {

			centerShell.dispose();
		}
	}

	public static void createTorrentDropTarget(Composite composite,
			boolean bAllowShareAdd) {
		try {
			createDropTarget(composite, bAllowShareAdd, null);
		} catch (Exception e) {
			Debug.out(e);
		}
	}

	/**
	 * @param control the control (usually a Shell) to add the DropTarget
	 * @param url the Text control where to set the link text
	 *
	 * @author Rene Leonhardt
	 */
	public static void createURLDropTarget(Composite composite, Text url) {
		try {
			createDropTarget(composite, false, url);
		} catch (Exception e) {
			Debug.out(e);
		}
	}

	private static void createDropTarget(Composite composite,
			final boolean bAllowShareAdd, final Text url,
			DropTargetListener dropTargetListener) {

		Transfer[] transferList = new Transfer[] {
			HTMLTransfer.getInstance(),
			URLTransfer.getInstance(),
			FileTransfer.getInstance(),
			TextTransfer.getInstance()
		};

		final DropTarget dropTarget = new DropTarget(composite, DND.DROP_DEFAULT
				| DND.DROP_MOVE | DND.DROP_COPY | DND.DROP_LINK | DND.DROP_TARGET_MOVE);
		dropTarget.setTransfer(transferList);
		dropTarget.addDropListener(dropTargetListener);
		// Note: DropTarget will dipose when the parent it's on diposes

		// On Windows, dropping on children moves up to parent
		// On OSX, each child needs it's own drop.
		if (Constants.isWindows)
			return;

		Control[] children = composite.getChildren();
		for (int i = 0; i < children.length; i++) {
			Control control = children[i];
			if (!control.isDisposed()) {
				if (control instanceof Composite) {
					createDropTarget((Composite) control, bAllowShareAdd, url,
							dropTargetListener);
				} else {
					final DropTarget dropTarget2 = new DropTarget(control,
							DND.DROP_DEFAULT | DND.DROP_MOVE | DND.DROP_COPY | DND.DROP_LINK
									| DND.DROP_TARGET_MOVE);
					dropTarget2.setTransfer(transferList);
					dropTarget2.addDropListener(dropTargetListener);
				}
			}
		}
	}

	private static void createDropTarget(Composite composite,
			boolean bAllowShareAdd, Text url) {

		URLDropTarget target = new URLDropTarget(url, bAllowShareAdd);
		createDropTarget(composite, bAllowShareAdd, url, target);
	}

	private static class URLDropTarget
		extends DropTargetAdapter
	{
		private final Text url;

		private final boolean bAllowShareAdd;

		public URLDropTarget(Text url, boolean bAllowShareAdd) {
			this.url = url;
			this.bAllowShareAdd = bAllowShareAdd;
		}

		public void dropAccept(DropTargetEvent event) {
			event.currentDataType = URLTransfer.pickBestType(event.dataTypes,
					event.currentDataType);
		}

		public void dragOver(DropTargetEvent event) {
			// skip setting detail if user is forcing a drop type (ex. via the
			// ctrl key), providing that the operation is valid
			if (event.detail != DND.DROP_DEFAULT
					&& ((event.operations & event.detail) > 0))
				return;

			if ((event.operations & DND.DROP_LINK) > 0)
				event.detail = DND.DROP_LINK;
			else if ((event.operations & DND.DROP_DEFAULT) > 0)
				event.detail = DND.DROP_DEFAULT;
			else if ((event.operations & DND.DROP_COPY) > 0)
				event.detail = DND.DROP_COPY;
		}

		public void drop(DropTargetEvent event) {
			if (url == null || url.isDisposed()) {
				TorrentOpener.openDroppedTorrents(event, bAllowShareAdd);
			} else {
				if (event.data instanceof URLTransfer.URLType) {
					if (((URLTransfer.URLType) event.data).linkURL != null)
						url.setText(((URLTransfer.URLType) event.data).linkURL);
				} else if (event.data instanceof String) {
					String sURL = UrlUtils.parseTextForURL((String) event.data, true);
					if (sURL != null) {
						url.setText(sURL);
					}
				}
			}
		}
	}

	public static void alternateRowBackground(TableItem item) {
		if (Utils.TABLE_GRIDLINE_IS_ALTERNATING_COLOR) {
			if (!item.getParent().getLinesVisible())
				item.getParent().setLinesVisible(true);
			return;
		}

		if (item == null || item.isDisposed())
			return;
		Color[] colors = {
			item.getDisplay().getSystemColor(SWT.COLOR_LIST_BACKGROUND),
			Colors.colorAltRow
		};
		Color newColor = colors[item.getParent().indexOf(item) % colors.length];
		if (!item.getBackground().equals(newColor)) {
			item.setBackground(newColor);
		}
	}

		// Yes, this is actually used by the RSSFeed plugin...
		// so don't make private until this is fixed

    public static void alternateTableBackground(Table table) {
		if (table == null || table.isDisposed())
			return;

		if (Utils.TABLE_GRIDLINE_IS_ALTERNATING_COLOR) {
			if (!table.getLinesVisible())
				table.setLinesVisible(true);
			return;
		}

		int iTopIndex = table.getTopIndex();
		if (iTopIndex < 0 || (iTopIndex == 0 && table.getItemCount() == 0)) {
			return;
		}
		int iBottomIndex = getTableBottomIndex(table, iTopIndex);

		Color[] colors = {
			table.getDisplay().getSystemColor(SWT.COLOR_LIST_BACKGROUND),
			Colors.colorAltRow
		};
		int iFixedIndex = iTopIndex;
		for (int i = iTopIndex; i <= iBottomIndex; i++) {
			TableItem row = table.getItem(i);
			// Rows can be disposed!
			if (!row.isDisposed()) {
				Color newColor = colors[iFixedIndex % colors.length];
				iFixedIndex++;
				if (!row.getBackground().equals(newColor)) {
					//        System.out.println("setting "+rows[i].getBackground() +" to " + newColor);
					row.setBackground(newColor);
				}
			}
		}
	}

	/**
	 * <p>
	 * Set a MenuItem's image with the given ImageRepository key. In compliance with platform
	 * human interface guidelines, the images are not set under Mac OS X.
	 * </p>
	 * @param item SWT MenuItem
	 * @param repoKey ImageRepository image key
	 * @see <a href="http://developer.apple.com/documentation/UserExperience/Conceptual/OSXHIGuidelines/XHIGMenus/chapter_7_section_3.html#//apple_ref/doc/uid/TP30000356/TPXREF116">Apple HIG</a>
	 */
  	public static void setMenuItemImage(final MenuItem item, final String repoKey) {
  		if (Constants.isOSX || repoKey == null) {
  			return;
  		}
  		ImageLoader imageLoader = ImageLoader.getInstance();
  		item.setImage(imageLoader.getImage(repoKey));
  		item.addDisposeListener(new DisposeListener() {
  			public void widgetDisposed(DisposeEvent e) {
  				ImageLoader imageLoader = ImageLoader.getInstance();
  				imageLoader.releaseImage(repoKey);
  			}
  		});
  	}

  	public static void setMenuItemImage(final org.gudy.azureus2.plugins.ui.menus.MenuItem item, final String repoKey) {
  		if (Constants.isOSX || repoKey == null) {
  			return;
  		}
  		if (Utils.isSWTThread()) {
	  		ImageLoader imageLoader = ImageLoader.getInstance();
	  		Graphic graphic = new UISWTGraphicImpl( imageLoader.getImage(repoKey));

	  		item.setGraphic(graphic);
  		} else {
  			execSWTThread(
  				new Runnable() {

					public void run() {
						ImageLoader imageLoader = ImageLoader.getInstance();
				  		Graphic graphic = new UISWTGraphicImpl( imageLoader.getImage(repoKey));

				  		item.setGraphic(graphic);
					}
				});
  		}
  	}

	public static void setMenuItemImage(CLabel item, final String repoKey) {
		if (Constants.isOSX || repoKey == null) {
			return;
		}
		ImageLoader imageLoader = ImageLoader.getInstance();
		item.setImage(imageLoader.getImage(repoKey));
		item.addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent e) {
				ImageLoader imageLoader = ImageLoader.getInstance();
				imageLoader.releaseImage(repoKey);
			}
		});
	}

	public static void setMenuItemImage(final MenuItem item, final Image image) {
		if (!Constants.isOSX)
			item.setImage(image);
	}

	/**
	 * Sets the shell's Icon(s) to the default Azureus icon.  OSX doesn't require
	 * an icon, so they are skipped
	 *
	 * @param shell
	 */
	public static void setShellIcon(Shell shell) {
		if (Constants.isOSX) {
			if (true) {
				return;
			}
			if (icon128 == null) {
  			ImageLoader imageLoader = ImageLoader.getInstance();
  			icon128 = imageLoader.getImage("azureus128");
  			if (Constants.isCVSVersion()) {
  				final int border = 9;
					Image image = Utils.createAlphaImage(shell.getDisplay(),
							128 + (border * 2), 128 + (border * 2));
					image = blitImage(shell.getDisplay(), icon128, null, image, new Point(border,
							border + 1));
					imageLoader.releaseImage("azureus128");
					icon128 = image;
//  				GC gc = new GC(icon128);
//  				gc.setTextAntialias(SWT.ON);
//  				gc.setForeground(shell.getDisplay().getSystemColor(SWT.COLOR_YELLOW));
//  				Font font = getFontWithHeight(gc.getFont(), gc, 20, SWT.BOLD);
//  				gc.setFont(font);
//					GCStringPrinter.printString(gc, Constants.AZUREUS_VERSION,
//							new Rectangle(0, 0, 128, 128), false, false, SWT.CENTER
//									| SWT.BOTTOM);
//  				gc.dispose();
//  				font.dispose();
  			}
			}
 			shell.setImage(icon128);
			return;
		}

		try {
			if (shellIcons == null) {

				ArrayList<Image> listShellIcons = new ArrayList<Image>(
						shellIconNames.length);

				ImageLoader imageLoader = ImageLoader.getInstance();
				for (int i = 0; i < shellIconNames.length; i++) {
					// never release images since they are always used and stored
					// in an array
					Image image = imageLoader.getImage(shellIconNames[i]);
					if (ImageLoader.isRealImage(image)) {
						listShellIcons.add(image);
					}
				}
				shellIcons = (Image[]) listShellIcons.toArray(new Image[listShellIcons.size()]);
			}

			shell.setImages(shellIcons);
		} catch (NoSuchMethodError e) {
			// SWT < 3.0
		}
	}

	public static Display getDisplay() {
		SWTThread swt = SWTThread.getInstance();

		Display display;
		if (swt == null) {
			try {
	  			display = Display.getDefault();
	  			if (display == null) {
	  				System.err.println("SWT Thread not started yet!");
	  				return null;
	  			}
			} catch (Throwable t) {
				// ignore
				return null;
			}
		} else {
			if (swt.isTerminated()) {
				return null;
			}
			display = swt.getDisplay();
		}

		if (display == null || display.isDisposed()) {
			return null;
		}
		return display;
	}

	/**
	 * Execute code in the Runnable object using SWT's thread.  If current
	 * thread it already SWT's thread, the code will run immediately.  If the
	 * current thread is not SWT's, code will be run either synchronously or
	 * asynchronously on SWT's thread at the next reasonable opportunity.
	 *
	 * This method does not catch any exceptions.
	 *
	 * @param code code to run
	 * @param async true if SWT asyncExec, false if SWT syncExec
	 * @return success
	 */
	public static boolean execSWTThread(final Runnable code, boolean async) {
		return execSWTThread(code, async ? -1 : -2);
	}

	/**
	 * Schedule execution of the code in the Runnable object using SWT's thread.
	 * Even if the current thread is the SWT Thread, the code will be scheduled.
	 * <p>
	 * Much like Display.asyncExec, except getting the display is handled for you,
	 * and provides the ability to diagnose and monitor scheduled code run.
	 *
	 * @param msLater time to wait before running code on SWT thread.  0 does not
	 *                mean immediate, but as soon as possible.
	 * @param code Code to run
	 * @return sucess
	 *
	 * @since 3.0.4.3
	 */
	public static boolean execSWTThreadLater(int msLater, final Runnable code) {
		return execSWTThread(code, msLater);
	}

	/**
	 *
	 * @param code
	 * @param msLater -2: sync<BR>
	 *                -1: sync if on SWT thread, async otherwise<BR>
	 *                 0: async<BR>
	 *                >0: timerExec
	 * @return
	 *
	 * @since 3.0.4.3
	 */

	public static boolean
	isSWTThread() {
		final Display display = getDisplay();
		if (display == null) {
			return false;
		}

		return (display.getThread() == Thread.currentThread());
	}

	private static boolean execSWTThread(final Runnable code, final int msLater) {
		final Display display = getDisplay();
		if (display == null || code == null) {
			return false;
		}

		boolean isSWTThread = display.getThread() == Thread.currentThread();
		if (msLater < 0 && isSWTThread) {
			if (queue == null) {
				code.run();
			} else {
				long lStartTimeRun = SystemTime.getCurrentTime();

				code.run();

				long wait = SystemTime.getCurrentTime() - lStartTimeRun;
				if (wait > 700) {
					diagLogger.log(SystemTime.getCurrentTime() + "] took " + wait
							+ "ms to run " + Debug.getCompressedStackTrace(-5));
				}
			}
		} else if (msLater >= -1) {
			try {
				if (queue == null) {
					if (msLater <= 0) {
						display.asyncExec(code);
					} else {
						if (isSWTThread) {
							display.timerExec(msLater, code);
						} else {
  						SimpleTimer.addEvent("execSWTThreadLater",
							SystemTime.getOffsetTime(msLater), new TimerEventPerformer() {
								public void perform(TimerEvent event) {
									if (!display.isDisposed()) {
										display.asyncExec(code);
									}
								}
							});
						}
					}
				} else {
					queue.add(code);

					diagLogger.log(SystemTime.getCurrentTime() + "] + Q. size= "
							+ queue.size() + ";in=" + msLater + "; add " + code + " via "
							+ Debug.getCompressedStackTrace(-5));
					final long lStart = SystemTime.getCurrentTime();

					final Display fDisplay = display;
					final AERunnable runnableWrapper = new AERunnable() {
						public void runSupport() {
							long wait = SystemTime.getCurrentTime() - lStart - msLater;
							if (wait > 700) {
								diagLogger.log(SystemTime.getCurrentTime() + "] took " + wait
										+ "ms before SWT ran async code " + code);
							}
							long lStartTimeRun = SystemTime.getCurrentTime();

							try {
								if (fDisplay.isDisposed()) {
									Debug.out("Display disposed while trying to execSWTThread "
											+ code);
									// run anayway, except trap SWT error
									try {
										code.run();
									} catch (SWTException e) {
										Debug.out("Error while execSWTThread w/disposed Display", e);
									}
								} else {
									code.run();
								}
							} finally {
								long runTIme = SystemTime.getCurrentTime() - lStartTimeRun;
								if (runTIme > 500) {
									diagLogger.log(SystemTime.getCurrentTime() + "] took "
											+ runTIme + "ms to run " + code);
								}

								queue.remove(code);

								if (runTIme > 10) {
									diagLogger.log(SystemTime.getCurrentTime()
											+ "] - Q. size=" + queue.size() + ";wait:" + wait
											+ "ms;run:" + runTIme + "ms " + code);
								} else {
									diagLogger.log(SystemTime.getCurrentTime()
											+ "] - Q. size=" + queue.size() + ";wait:" + wait
											+ "ms;run:" + runTIme + "ms");
								}
							}
						}
					};
					
					if (msLater <= 0) {
						display.asyncExec(runnableWrapper);
					} else {
						if (isSWTThread) {
							display.timerExec(msLater, runnableWrapper);
						} else {
							SimpleTimer.addEvent("execSWTThreadLater",
  								SystemTime.getOffsetTime(msLater), new TimerEventPerformer() {
  									public void perform(TimerEvent event) {
  										if (!display.isDisposed()) {
  											display.asyncExec(runnableWrapper);
  										}
  									}
  								}
							);
						}
					}
				}
			} catch (NullPointerException e) {
				// If the display is being disposed of, asyncExec may give a null
				// pointer error

				if (Constants.isCVSVersion()) {
					Debug.out(e);
				}

				return false;
			}
		} else {
			display.syncExec(code);
		}

		return true;
	}

	/**
	 * Execute code in the Runnable object using SWT's thread.  If current
	 * thread it already SWT's thread, the code will run immediately.  If the
	 * current thread is not SWT's, code will be run asynchronously on SWT's
	 * thread at the next reasonable opportunity.
	 *
	 * This method does not catch any exceptions.
	 *
	 * @param code code to run
	 * @return success
	 */
	public static boolean execSWTThread(Runnable code) {
		return execSWTThread(code, -1);
	}

	public static boolean isThisThreadSWT() {
		SWTThread swt = SWTThread.getInstance();

		if (swt == null) {
			//System.err.println("WARNING: SWT Thread not started yet");
		}

		Display display = (swt == null) ? Display.getCurrent() : swt.getDisplay();

		if (display == null) {
			return false;
		}

		// This will throw if we are disposed or on the wrong thread
		// Much better that display.getThread() as that one locks Device.class
		// and may end up causing sync lock when disposing
		try {
			display.getWarnings();
		} catch (SWTException e) {
			return false;
		}

		return (display.getThread() == Thread.currentThread());
	}

	/**
	 * Bottom Index may be negative. Returns bottom index even if invisible.
	 * <p>
	 * Used by rssfeed
	 */
	public static int getTableBottomIndex(Table table, int iTopIndex) {

		// Shortcut: if lastBottomIndex is present, assume it's accurate
		Object lastBottomIndex = table.getData("lastBottomIndex");
		if (lastBottomIndex instanceof Number) {
			return ((Number)lastBottomIndex).intValue();
		}

		int columnCount = table.getColumnCount();
		if (columnCount == 0) {
			return -1;
		}
		int xPos = table.getColumn(0).getWidth() - 1;
		if (columnCount > 1) {
			xPos += table.getColumn(1).getWidth();
		}

		Rectangle clientArea = table.getClientArea();
		TableItem bottomItem = table.getItem(new Point(xPos,
				clientArea.y + clientArea.height - 2));
		if (bottomItem != null) {
			return table.indexOf(bottomItem);
		}
		return table.getItemCount() - 1;
	}


	public static void launch(final DiskManagerFileInfo fileInfo) {
		LaunchManager	launch_manager = LaunchManager.getManager();

		LaunchManager.LaunchTarget target = launch_manager.createTarget(fileInfo);

		launch_manager.launchRequest(
			target,
			new LaunchManager.LaunchAction() {
				public void actionAllowed() {
					Utils.execSWTThread(
						new Runnable() {
							public void run() {
								launch(fileInfo.getFile(true).toString());
							}
						});
				}

				public void actionDenied(
					Throwable			reason) {
					Debug.out("Launch request denied", reason);
				}
			});

	}

	public static void launch(URL url) {
		launch( url.toExternalForm());
	}

	public static void launch(
		String sFile ) {
		launch(sFile, false);
	}

	private static Set<String>		pending_ext_urls 		= new HashSet<String>();
	private static AsyncDispatcher	ext_url_dispatcher 		= new AsyncDispatcher("Ext Urls");

	private static boolean			i2p_install_active_for_url		= false;
	private static boolean			browser_install_active_for_url	= false;

	public static void launch(
		String 		sFileOriginal,
		boolean		sync) {
		launch(sFileOriginal, sync, false);
	}

	public static void launch(
		String 		sFileOriginal,
		boolean		sync,
		boolean		force_url) {
		launch(sFileOriginal, sync, force_url, false);
	}

	public static void launch(
		String 		sFileOriginal,
		boolean		sync,
		boolean		force_url,
		boolean		force_anon) {
		String sFileModified = sFileOriginal;

		if (sFileModified == null || sFileModified.trim().length() == 0) {
			return;
		}

		if (!force_url) {
			if (!Constants.isWindows && new File(sFileModified).isDirectory()) {
				PlatformManager mgr = PlatformManagerFactory.getPlatformManager();
				if (mgr.hasCapability(PlatformManagerCapabilities.ShowFileInBrowser)) {
					try {
						PlatformManagerFactory.getPlatformManager().showFile(sFileModified);
						return;
					} catch (PlatformManagerException e) {
					}
				}
			}

			sFileModified = sFileModified.replaceAll("&vzemb=1", "");

			String exe = getExplicitLauncher(sFileModified);

			if (exe != null) {

				File	file = new File(sFileModified);

				try {
					System.out.println("Launching " + sFileModified + " with " + exe);

					if (Constants.isWindows) {

							// need to use createProcess as we want to force the process to decouple correctly (otherwise Vuze won't close until the child closes)

						try {
							PlatformManagerFactory.getPlatformManager().createProcess(exe + " \"" + sFileModified + "\"", false);

							return;

						} catch (Throwable e) {
						}
					}

					ProcessBuilder pb = GeneralUtils.createProcessBuilder(file.getParentFile(), new String[]{ exe, file.getName()}, null);

					pb.start();

					return;

				} catch (Throwable e) {

					Debug.out("Launch failed", e);
				}
			}
		}

		String lc_sFile = sFileModified.toLowerCase(Locale.US);

		if (lc_sFile.startsWith("tor:")) {

			force_anon	= true;

			lc_sFile = lc_sFile.substring(4);

			sFileModified = lc_sFile;
		}

		if (lc_sFile.startsWith("http:") || lc_sFile.startsWith("https:")) {

			String 		net_type;
			String 		eb_choice;
			boolean		use_plugins;

			if (force_anon) {

				net_type 		= AENetworkClassifier.AT_TOR;
				eb_choice 		= "plugin";
				use_plugins		= true;

			} else {

				net_type = AENetworkClassifier.AT_PUBLIC;

				try {
					net_type = AENetworkClassifier.categoriseAddress(new URL( sFileModified).getHost());

				} catch (Throwable e) {

				}

				eb_choice = COConfigurationManager.getStringParameter("browser.external.id", "system");

				use_plugins = COConfigurationManager.getBooleanParameter("browser.external.non.pub", true);

				if (net_type != AENetworkClassifier.AT_PUBLIC && use_plugins) {

					eb_choice = "plugin";	// hack to force to that code leg
				}
			}

			if (eb_choice.equals("system")) {

			} else if (eb_choice.equals("manual")) {

				String browser_exe = COConfigurationManager.getStringParameter("browser.external.prog", "");

				File bf = new File(browser_exe);

				if (bf.exists()) {

					try {
						Process proc = Runtime.getRuntime().exec(new String[]{ bf.getAbsolutePath(), sFileModified });

					} catch (Throwable e) {

						Debug.out(e);
					}
				} else {

					Debug.out("Can't launch '" + sFileModified + "' as manual browser '" + bf + " ' doesn't exist");
				}

				return;

			} else {

				handlePluginLaunch(eb_choice, net_type, use_plugins, sFileOriginal, sFileModified, sync, force_url, force_anon);

				return;
			}
		} else if (lc_sFile.startsWith("chat:")) {

			String plug_uri = "azplug:?id=azbuddy&arg=" + UrlUtils.encode(sFileModified);

			try {
				URLConnection connection = new URL(plug_uri).openConnection();

				connection.connect();

				String res = FileUtil.readInputStreamAsString(connection.getInputStream(), 2048);

				return;

			} catch (Throwable e) {
			}
		}

		boolean launched = Program.launch(sFileModified);

		if (!launched && Constants.isUnix) {

			sFileModified = sFileModified.replaceAll(" ", "\\ ");

			if (!Program.launch("xdg-open " + sFileModified)) {

				if (!Program.launch("htmlview " + sFileModified)) {

					Debug.out("Failed to launch '" + sFileModified + "'");
				}
			}
		}
	}

	private static void handlePluginLaunch(
		String				eb_choice,
		String				net_type,
		boolean				use_plugins,
		final String		sFileOriginal,
		final String		sFileModified,
		final boolean		sync,
		final boolean		force_url,
		final boolean		force_anon) {
		PluginManager pm = AzureusCoreFactory.getSingleton().getPluginManager();

		if (net_type == AENetworkClassifier.AT_I2P) {

			if (pm.getPluginInterfaceByID("azneti2phelper") == null) {

				boolean	try_it;

				synchronized(pending_ext_urls) {

					try_it = !i2p_install_active_for_url;

					i2p_install_active_for_url = true;
				}

				if (try_it) {

					ext_url_dispatcher.dispatch(
						new AERunnable() {
							public void runSupport() {
								boolean installing = false;

								try {
									final boolean[]	install_outcome = { false };

									installing =
											I2PHelpers.installI2PHelper(
											"azneti2phelper.install",
											install_outcome,
											new Runnable() {
												public void run() {
													try {
														if (install_outcome[0]) {

															Utils.launch(sFileOriginal, sync, force_url, force_anon);
														}
													} finally {

														synchronized(pending_ext_urls) {

															i2p_install_active_for_url = false;
														}
													}
												}
											});

								} finally {

									if (!installing) {

										synchronized(pending_ext_urls) {

											i2p_install_active_for_url = false;
										}

									}
								}
							}
						});
				} else {

					Debug.out("I2P installation already active");
				}

				return;
			}
		}

		java.util.List<PluginInterface> pis =
				pm.getPluginsWithMethod(
					"launchURL",
					new Class[]{ URL.class, boolean.class, Runnable.class });

		boolean found = false;

		for (final PluginInterface pi: pis) {

			String id = "plugin:" + pi.getPluginID();

			if (eb_choice.equals("plugin") || id.equals( eb_choice)) {

				found = true;

				synchronized(pending_ext_urls) {

					if (pending_ext_urls.contains( sFileModified)) {

						Debug.outNoStack("Already queued browser request for '" + sFileModified + "' - ignoring");

						return;
					}

					pending_ext_urls.add(sFileModified);
				}

				AERunnable launch =
					new AERunnable() {
						public void runSupport() {
							try {
								final AESemaphore sem = new AESemaphore("wait");

								pi.getIPC().invoke(
									"launchURL",
									new Object[]{
										new URL(sFileModified),
										false,
										new Runnable() {
											public void run() {
												sem.release();
											}
										}});

								if (!sem.reserve( 30*1000)) {

									// can happen when user is prompted to accept the launch or not and is
									// slow in replying
									// Debug.out("Timeout waiting for external url launch");
								}

							} catch (Throwable e) {

								Debug.out(e);

							} finally {

								synchronized(pending_ext_urls) {

									pending_ext_urls.remove(sFileModified);
								}
							}
						}
					};

				if (sync) {

					launch.runSupport();

				} else {

					ext_url_dispatcher.dispatch(launch);
				}
			}
		}

		if (!found) {

			if (net_type != AENetworkClassifier.AT_PUBLIC && use_plugins) {

				boolean	try_it;

				synchronized(pending_ext_urls) {

					try_it = !browser_install_active_for_url;

					browser_install_active_for_url = true;
				}

				if (try_it) {

					ext_url_dispatcher.dispatch(
						new AERunnable() {
							public void runSupport() {
								boolean installing = false;

								try {
									final boolean[]	install_outcome = { false };

									installing =
										installTorBrowser(
											"aznettorbrowser.install",
											install_outcome,
											new Runnable() {
												public void run() {
													try {
														if (install_outcome[0]) {

															Utils.launch(sFileOriginal, sync, force_url, force_anon);
														}
													} finally {

														synchronized(pending_ext_urls) {

															browser_install_active_for_url = false;
														}
													}
												}
											});

								} catch (Throwable e) {

									Debug.out(e);

								} finally {

									if (!installing) {

										synchronized(pending_ext_urls) {

											browser_install_active_for_url = false;
										}
									}
								}
							}
						});
				} else {

					Debug.out("Browser installation already active");
				}

				return;
			}
		}

		if (!found && !eb_choice.equals("plugin")) {

			Debug.out("Failed to find external URL launcher plugin with id '" + eb_choice + "'");
		}

		return;
	}



	private static boolean tb_installing = false;

	public static boolean
	isInstallingTorBrowser() {
		synchronized(pending_ext_urls) {

			return (tb_installing);
		}
	}

	private static boolean
	installTorBrowser(
		String				remember_id,
		final boolean[]		install_outcome,
		final Runnable		callback) {
		synchronized(pending_ext_urls) {

			if (tb_installing) {

				Debug.out("Tor Browser already installing");

				return (false);
			}

			tb_installing = true;
		}

		boolean	installing = false;

		try {
			UIFunctions uif = UIFunctionsManager.getUIFunctions();

			if (uif == null) {

				Debug.out("UIFunctions unavailable - can't install plugin");

				return (false);
			}

			String title = MessageText.getString("aznettorbrowser.install");

			String text = MessageText.getString("aznettorbrowser.install.text");

			UIFunctionsUserPrompter prompter = uif.getUserPrompter(title, text, new String[] {
				MessageText.getString("Button.yes"),
				MessageText.getString("Button.no")
			}, 0);

			if (remember_id != null) {

				prompter.setRemember(
					remember_id,
					false,
					MessageText.getString("MessageBoxWindow.nomoreprompting"));
			}

			prompter.setAutoCloseInMS(0);

			prompter.open(null);

			boolean	install = prompter.waitUntilClosed() == 0;

			if (install) {

				uif.installPlugin(
					"aznettorbrowser",
					"aznettorbrowser.install",
					new UIFunctions.actionListener() {
						public void actionComplete(
							Object		result) {
							try {
								if (callback != null) {

									if (result instanceof Boolean) {

										install_outcome[0] = (Boolean)result;
									}

									callback.run();
								}
							} finally {

								synchronized(pending_ext_urls) {

									tb_installing = false;
								}
							}
						}
					});

				installing = true;

			} else {

				Debug.out("Tor Browser install declined (either user reply or auto-remembered)");
			}

			return (install);

		} finally {

			if (!installing) {

				synchronized(pending_ext_urls) {

					tb_installing = false;
				}
			}
		}
	}

	private static String getExplicitLauncher(
		String	file) {
		int	pos = file.lastIndexOf(".");

		if (pos >= 0) {

			String	ext = file.substring(pos+1).toLowerCase().trim();

				// if this is a URL then hack off any possible query params

			int	q_pos = ext.indexOf("?");

			if (q_pos > 0) {

				ext = ext.substring(0, q_pos);
			}

			for ( int i=0;i<10;i++) {

				String exts = COConfigurationManager.getStringParameter("Table.lh" + i + ".exts", "").trim();
				String exe 	= COConfigurationManager.getStringParameter("Table.lh" + i + ".prog", "").trim();

				if (exts.length() > 0 && exe.length() > 0 && new File( exe).exists()) {

					exts = "," + exts.toLowerCase();

					exts = exts.replaceAll("\\.", ",");
					exts = exts.replaceAll(";", ",");
					exts = exts.replaceAll(" ", ",");

					exts = exts.replaceAll("[,]+", ",");

					if (exts.contains(","+ext)) {

						return (exe);
					}
				}
			}
		}

		return (null);
	}

	/**
	 * Sets the checkbox in a Virtual Table while inside a SWT.SetData listener
	 * trigger.  SWT 3.1 has an OSX bug that needs working around.
	 *
	 * @param item
	 * @param checked
	 */
	public static void setCheckedInSetData(final TableItem item,
			final boolean checked) {
		item.setChecked(checked);

		if (Constants.isWindowsXP || isGTK) {
			Rectangle r = item.getBounds(0);
			Table table = item.getParent();
			Rectangle rTable = table.getClientArea();

			table.redraw(0, r.y, rTable.width, r.height, true);
		}
	}

	public static boolean linkShellMetricsToConfig(final Shell shell,
			final String sConfigPrefix) {
		boolean isMaximized = COConfigurationManager.getBooleanParameter(sConfigPrefix
				+ ".maximized");

		if (!isMaximized) {
			shell.setMaximized(false);
		}

		String windowRectangle = COConfigurationManager.getStringParameter(
				sConfigPrefix + ".rectangle", null);
		boolean bDidResize = false;
		if (null != windowRectangle) {
			int i = 0;
			int[] values = new int[4];
			StringTokenizer st = new StringTokenizer(windowRectangle, ",");
			try {
				while (st.hasMoreTokens() && i < 4) {
					values[i++] = Integer.valueOf(st.nextToken()).intValue();
				}
				if (i == 4) {
					Rectangle shellBounds = new Rectangle(values[0], values[1],
							values[2], values[3]);
					if (shellBounds.width > 100 && shellBounds.height > 50) {
  					shell.setBounds(shellBounds);
  					verifyShellRect(shell, true);
  					bDidResize = true;
					}
				}
			} catch (Exception e) {
			}
		}

		if (isMaximized) {
			shell.setMaximized(isMaximized);
		}

		new ShellMetricsResizeListener(shell, sConfigPrefix);

		return bDidResize;
	}

	private static class ShellMetricsResizeListener
		implements Listener
	{
		private int state = -1;

		private String sConfigPrefix;

		private Rectangle bounds = null;

		ShellMetricsResizeListener(Shell shell, String sConfigPrefix) {
			this.sConfigPrefix = sConfigPrefix;
			state = calcState(shell);
			if (state == SWT.NONE)
				bounds = shell.getBounds();

			shell.addListener(SWT.Resize, this);
			shell.addListener(SWT.Move, this);
			shell.addListener(SWT.Dispose, this);
		}

		private int calcState(Shell shell) {
			return shell.getMinimized() ? SWT.MIN : shell.getMaximized()
					&& !isCarbon ? SWT.MAX : SWT.NONE;
		}

		private void saveMetrics() {
			COConfigurationManager.setParameter(sConfigPrefix + ".maximized",
					state == SWT.MAX);

			if (bounds == null)
				return;

			COConfigurationManager.setParameter(sConfigPrefix + ".rectangle",
					bounds.x + "," + bounds.y + "," + bounds.width + "," + bounds.height);

			COConfigurationManager.save();
		}

		public void handleEvent(Event event) {
			Shell shell = (Shell) event.widget;
			state = calcState(shell);

			if (event.type != SWT.Dispose && state == SWT.NONE)
				bounds = shell.getBounds();

			if (event.type == SWT.Dispose)
				saveMetrics();
		}
	}

	public static GridData setGridData(Composite composite, int gridStyle,
			Control ctrlBestSize, int maxHeight) {
		GridData gridData = new GridData(gridStyle);
		gridData.heightHint = ctrlBestSize.computeSize(SWT.DEFAULT, SWT.DEFAULT).y;
		if (gridData.heightHint > maxHeight && maxHeight > 0)
			gridData.heightHint = maxHeight;
		composite.setLayoutData(gridData);

		return gridData;
	}

	public static FormData getFilledFormData() {
		FormData formData = new FormData();
		formData.top = new FormAttachment(0, 0);
		formData.left = new FormAttachment(0, 0);
		formData.right = new FormAttachment(100, 0);
		formData.bottom = new FormAttachment(100, 0);
		return formData;
	}

	public static int pixelsToPoint(int pixels, int dpi) {
		int ret = (int) Math.round((pixels * 72.0) / dpi);
		return ret;
	}

    private static int pixelsToPoint(double pixels, int dpi) {
		int ret = (int) Math.round((pixels * 72.0) / dpi);
		return ret;
	}

    private static boolean drawImage(GC gc, Image image, Rectangle dstRect,
			Rectangle clipping, int hOffset, int vOffset, boolean clearArea) {
		return drawImage(gc, image, new Point(0, 0), dstRect, clipping, hOffset,
				vOffset, clearArea);
	}

    private static boolean drawImage(GC gc, Image image, Rectangle dstRect,
			Rectangle clipping, int hOffset, int vOffset) {
		return drawImage(gc, image, new Point(0, 0), dstRect, clipping, hOffset,
				vOffset, false);
	}

	public static boolean drawImage(GC gc, Image image, Point srcStart,
			Rectangle dstRect, Rectangle clipping, int hOffset, int vOffset,
			boolean clearArea) {
		Rectangle srcRect;
		Point dstAdj;

		if (clipping == null) {
			dstAdj = new Point(0, 0);
			srcRect = new Rectangle(srcStart.x, srcStart.y, dstRect.width,
					dstRect.height);
		} else {
			if (!dstRect.intersects(clipping)) {
				return false;
			}

			dstAdj = new Point(Math.max(0, clipping.x - dstRect.x), Math.max(0,
					clipping.y - dstRect.y));

			srcRect = new Rectangle(0, 0, 0, 0);
			srcRect.x = srcStart.x + dstAdj.x;
			srcRect.y = srcStart.y + dstAdj.y;
			srcRect.width = Math.min(dstRect.width - dstAdj.x, clipping.x
					+ clipping.width - dstRect.x);
			srcRect.height = Math.min(dstRect.height - dstAdj.y, clipping.y
					+ clipping.height - dstRect.y);
		}

		if (!srcRect.isEmpty()) {
			try {
				if (clearArea) {
					gc.fillRectangle(dstRect.x + dstAdj.x + hOffset, dstRect.y + dstAdj.y
							+ vOffset, srcRect.width, srcRect.height);
				}
				gc.drawImage(image, srcRect.x, srcRect.y, srcRect.width,
						srcRect.height, dstRect.x + dstAdj.x + hOffset, dstRect.y
								+ dstAdj.y + vOffset, srcRect.width, srcRect.height);
			} catch (Exception e) {
				System.out.println("drawImage: " + e.getMessage() + ": " + image + ", "
						+ srcRect + ", " + (dstRect.x + dstAdj.y + hOffset) + ","
						+ (dstRect.y + dstAdj.y + vOffset) + "," + srcRect.width + ","
						+ srcRect.height + "; imageBounds = " + image.getBounds());
			}
		}

		return true;
	}

	public static Control
	findChild(
		Composite	comp,
		int			x,
		int			y) {
		Rectangle comp_bounds = comp.getBounds();

		if (comp.isVisible() && comp_bounds.contains( x, y)) {

			x -= comp_bounds.x;
			y -= comp_bounds.y;

			Control[] children = comp.getChildren();

			for ( int i = 0; i < children.length; i++) {

				Control child = children[i];

				if (child.isVisible()) {

					if (child instanceof Composite) {

						Control res = findChild((Composite) child, x, y);

						if (res != null) {

							return (res);
						}
					} else {

						return (child);
					}
				}
			}

			return (comp);
		}

		return (null);
	}

	public static void dump(
		Control	comp) {
		PrintWriter pw = new PrintWriter(System.out);

		IndentWriter iw = new IndentWriter(pw);

		dump( iw, comp, new HashSet<Object>());

		pw.flush();
	}

	private static void dump(
		IndentWriter	iw,
		Control			comp,
		Set<Object>		done) {
		if (done.contains( comp)) {

			iw.println("<RECURSIVE!>");

			return;
		}

		done.add(comp);

		String str = comp.getClass().getName();

		int	pos = str.lastIndexOf(".");

		if (pos != -1) {

			str = str.substring(pos+1);
		}

		try {
			Field f = Widget.class.getDeclaredField("data");

			f.setAccessible(true);

			Object data = f.get(comp);

			if (data instanceof Object[]) {
				Object[] temp = (Object[])data;
				String s = "";
				for (Object t: temp) {
					s += (s==""?"":",") + t;
				}
				data = s;
			}

			String lay = "" + comp.getLayoutData();

			if (comp instanceof Composite) {

				lay += "/" + ((Composite)comp).getLayoutData();
			}

			iw.println( str + ",vis=" + comp.isVisible() + ",data=" + data + ",layout=" + lay + ",size=" + comp.getBounds());

			if (comp instanceof Composite) {

				try {
					iw.indent();

					Control[] children = ((Composite)comp).getChildren();

					for (Control kid: children) {



						dump(iw, kid, done);
					}
				} finally {

					iw.exdent();
				}
			}
		} catch (Throwable e) {

			e.printStackTrace();
		}
	}

	/**
	 * @param area
	 * @param event id
	 * @param listener
	 */
	public static void addListenerAndChildren(Composite area, int event,
			Listener listener) {
		area.addListener(event, listener);
		Control[] children = area.getChildren();
		for (int i = 0; i < children.length; i++) {
			Control child = children[i];
			if (child instanceof Composite) {
				addListenerAndChildren((Composite) child, event, listener);
			} else {
				child.addListener(event, listener);
			}
		}
	}

	public static Shell findAnyShell() {
		// Pick the main shell if we can
		UIFunctionsSWT uiFunctions = UIFunctionsManagerSWT.getUIFunctionsSWT();
		if (uiFunctions != null) {
			Shell shell = uiFunctions.getMainShell();
			if (shell != null && !shell.isDisposed()) {
				return shell;
			}
		}

		// Get active shell from current display if we can
		Display current = Display.getCurrent();
		if (current == null) {
			return null;
		}
		Shell shell = current.getActiveShell();
		if (shell != null && !shell.isDisposed()) {
			return shell;
		}

		// Get first shell of current display if we can
		Shell[] shells = current.getShells();
		if (shells.length == 0) {
			return null;
		}

		if (shells[0] != null && !shells[0].isDisposed()) {
			return shells[0];
		}

		return null;
	}

	/**
	 * @param listener
	 */
	public static boolean verifyShellRect(Shell shell, boolean bAdjustIfInvalid) {
		return verifyShellRect(shell, bAdjustIfInvalid, true);
	}

	private static boolean verifyShellRect(Shell shell, boolean bAdjustIfInvalid,
			boolean reverifyOnChange) {
		boolean bMetricsOk;
		try {
			if (shell.getMaximized()) {
				return true;
			}
			bMetricsOk = false;
			Point ptTopLeft = shell.getLocation();
			Point size = shell.getSize();
			Point ptBottomRight = shell.getLocation();
			ptBottomRight.x += size.x - 1;
			ptBottomRight.y += size.y - 1;

			Rectangle boundsMonitorTopLeft = null;
			Rectangle boundsMonitorBottomRight = null;
			Rectangle boundsMonitorContained = null;

			Monitor[] monitors = shell.getDisplay().getMonitors();
			for (int j = 0; j < monitors.length && !bMetricsOk; j++) {
				Rectangle bounds = monitors[j].getClientArea();
				boolean hasTopLeft = bounds.contains(ptTopLeft);
				boolean hasBottomRight = bounds.contains(ptBottomRight);
				bMetricsOk = hasTopLeft && hasBottomRight;
				if (hasTopLeft) {
					boundsMonitorTopLeft = bounds;
				}
				if (hasBottomRight) {
					boundsMonitorBottomRight = bounds;
				}
				if (boundsMonitorContained == null && bounds.intersects(ptTopLeft.x,
						ptTopLeft.y, ptBottomRight.x - ptTopLeft.x + 1,
						ptBottomRight.y - ptTopLeft.y + 1)) {
					boundsMonitorContained = bounds;
				}
			}
			Rectangle bounds = boundsMonitorTopLeft != null ? boundsMonitorTopLeft
					: boundsMonitorBottomRight != null ? boundsMonitorBottomRight
							: boundsMonitorContained;

			if (!bMetricsOk && bAdjustIfInvalid && bounds != null) {
				// Move up and/or left so bottom right fits
				int xDiff = ptBottomRight.x - (bounds.x + bounds.width);
				int yDiff = ptBottomRight.y - (bounds.y + bounds.height);
				boolean needsResize = false;
				boolean needsMove	= false;

				if (xDiff > 0) {
					ptTopLeft.x -= xDiff;
					needsMove = true;
				}
				if (yDiff > 0) {
					ptTopLeft.y -= yDiff;
					needsMove = true;
				}

				// move down and or right so top fits
				if (ptTopLeft.x < bounds.x) {
					ptTopLeft.x = bounds.x;
					needsMove = true;
				}
				if (ptTopLeft.y < bounds.y) {
					ptTopLeft.y = bounds.y;
					needsMove = true;
				}

				if (ptTopLeft.y < bounds.y) {
					ptBottomRight.y -= bounds.y - ptTopLeft.y;
					ptTopLeft.y = bounds.y;
					needsResize = true;
				}
				if (ptTopLeft.x < bounds.x) {
					ptBottomRight.x -= bounds.x - ptTopLeft.x;
					ptTopLeft.x = bounds.x;
					needsResize = true;
				}
				if (ptBottomRight.y >= (bounds.y + bounds.height)) {
					ptBottomRight.y = bounds.y + bounds.height - 1;
					needsResize = true;
				}
				if (ptBottomRight.x >= (bounds.x + bounds.width)) {
					ptBottomRight.x = bounds.x + bounds.width - 1;
					needsResize = true;
				}
				if (needsMove) {
					shell.setLocation(ptTopLeft);
				}

				if (needsResize) {
					shell.setSize(ptBottomRight.x - ptTopLeft.x + 1,
							ptBottomRight.y - ptTopLeft.y + 1);
				}
				if (reverifyOnChange && (needsMove || needsResize)) {

					return verifyShellRect(shell, bAdjustIfInvalid, false);

				} else {

					return (true);
				}
			}
		} catch (NoSuchMethodError e) {
			Rectangle bounds = shell.getDisplay().getClientArea();
			bMetricsOk = shell.getBounds().intersects(bounds);
		} catch (Throwable t) {
			bMetricsOk = true;
		}


		if (!bMetricsOk && bAdjustIfInvalid) {
			centreWindow(shell);
		}
		return bMetricsOk;
	}

	/**
	 * Relayout all composites up from control until there's enough room for the
	 * control to fit
	 *
	 * @param control Control that had it's sized changed and needs more room
	 */
	public static void relayout(Control control) {
		long startOn = DEBUG_SWTEXEC ? System.currentTimeMillis() : 0;
		relayout(control, false);
		if (DEBUG_SWTEXEC) {
			long diff = System.currentTimeMillis() - startOn;
			if (diff > 100) {
				String s = "Long relayout of " + diff + "ms "
						+ Debug.getCompressedStackTrace();
				if (diagLogger != null) {
					diagLogger.log(s);
				}
				System.out.println(s);
			}
		}
	}

	/**
	 * Relayout all composites up from control until there's enough room for the
	 * control to fit
	 *
	 * @param control Control that had it's sized changed and needs more room
	 */
	public static void relayout(Control control, boolean expandOnly) {
		if (control == null || control.isDisposed() || !control.isVisible()) {
			return;
		}

		if (control instanceof ScrolledComposite) {
			ScrolledComposite sc = (ScrolledComposite) control;
			Control content = sc.getContent();
			if (content != null && !content.isDisposed()) {
				Rectangle r = sc.getClientArea();
				sc.setMinSize(content.computeSize(r.width, SWT.DEFAULT ));
			}
		}


		Composite parent = control.getParent();
		Point targetSize = control.computeSize(SWT.DEFAULT, SWT.DEFAULT, true);
		Point size = control.getSize();
		if (size.y == targetSize.y && size.x == targetSize.x) {
			return;
		}

		int fixedWidth = -1;
		int fixedHeight = -1;
		Object layoutData = control.getLayoutData();
		if (layoutData instanceof FormData) {
			FormData fd = (FormData) layoutData;
			fixedHeight = fd.height;
			fixedWidth = fd.width;
			if (fd.width != SWT.DEFAULT && fd.height != SWT.DEFAULT) {
				parent.layout();
				return;
			}
		}

		if (expandOnly && size.y >= targetSize.y && size.x >= targetSize.x) {
			parent.layout();
			return;
		}
		//System.out.println("size=" + size + ";target=" + targetSize);

		Control previous = control;
		while (parent != null) {
			parent.layout(new Control[] { previous }, SWT.ALL | SWT.DEFER);
			if (parent instanceof ScrolledComposite) {
				ScrolledComposite sc = (ScrolledComposite) parent;
				Control content = sc.getContent();
				if (content != null && !content.isDisposed()) {
  				Rectangle r = sc.getClientArea();
  				sc.setMinSize(content.computeSize(r.width, SWT.DEFAULT ));
				}
			}

			Point newSize = control.getSize();

			//System.out.println("new=" + newSize + ";target=" + targetSize + ";" + control.isVisible());

			if ((fixedHeight > -1 || (newSize.y >= targetSize.y))
					&& (fixedWidth > -1 || (newSize.x >= targetSize.x))) {
				break;
			}

			previous = parent;
			parent = parent.getParent();
		}

		if (parent != null) {
			parent.layout();
		}
	}

	/**
	 *
	 */
	public static void beep() {
		execSWTThread(new AERunnable() {
			public void runSupport() {
				Display display = Display.getDefault();
				if (display != null) {
					display.beep();
				}
			}
		});
	}

	/**
	 * @deprecated Use {@link #execSWTThread(AERunnableWithCallback)} to avoid
	 *             thread locking issues
	 */
	public static Boolean execSWTThreadWithBool(String ID, AERunnableBoolean code) {
		return execSWTThreadWithBool(ID, code, 0);
	}

	/**
	 * Runs code within the SWT thread, waits for code to complete executing,
	 * (using a semaphore), and then returns a value.
	 *
	 * @note USE WITH CAUTION.  If the calling function synchronizes, and the
	 *       runnable code ends up synchronizing on the same object, an indefinite
	 *       thread lock or an unexpected timeout may occur (if one of the threads
	 *       is the SWT thread).<p>
	 *  ex - Thread1 calls c.foo(), which synchronized(this).
	 *     - Thread2 is the SWT Thread.  Thread2 calls c.foo(), which waits on
	 *       Thread1 to complete.
	 *   	 - c.foo() from Thread1 calls execSWTThreadWithBoolean(.., swtcode, ..),
	 *       which waits for the SWT Thread to return run the swtcode.
	 *     - Deadlock, or Timoeout which returns a false (and no code ran)
	 *
	 * @param ID id for debug
	 * @param code code to run
	 * @param millis ms to timeout in
	 *
	 * @return returns NULL if code never run
	 */
	public static Boolean execSWTThreadWithBool(String ID,
			AERunnableBoolean code, long millis) {
		if (code == null) {
			Logger.log(new LogEvent(LogIDs.CORE, "code null"));

			return null;
		}

		Boolean[] returnValueObject = {
			null
		};

		Display display = getDisplay();

		AESemaphore sem = null;
		if (display == null || display.getThread() != Thread.currentThread()) {
			sem = new AESemaphore(ID);
		}

		try {
			code.setupReturn(ID, returnValueObject, sem);

			if (!execSWTThread(code)) {
				// code never got run
				// XXX: throw instead?
				return null;
			}
		} catch (Throwable e) {
			if (sem != null) {
				sem.release();
			}
			Debug.out(ID, e);
		}
		if (sem != null) {
			sem.reserve(millis);
		}

		return returnValueObject[0];
	}

	/**
	 * @deprecated Use {@link #execSWTThread(AERunnableWithCallback)} to avoid
	 *             thread locking issues
	 */
	public static Object execSWTThreadWithObject(String ID, AERunnableObject code) {
		return execSWTThreadWithObject(ID, code, 0);
	}

	/**
	 * Runs code within the SWT thread, waits for code to complete executing,
	 * (using a semaphore), and then returns a value.
	 *
	 * @note USE WITH CAUTION.  If the calling function synchronizes, and the
	 *       runnable code ends up synchronizing on the same object, an indefinite
	 *       thread lock or an unexpected timeout may occur (if one of the threads
	 *       is the SWT thread).<p>
	 *  ex - Thread1 calls c.foo(), which synchronized(this).
	 *     - Thread2 is the SWT Thread.  Thread2 calls c.foo(), which waits on
	 *       Thread1 to complete.
	 *   	 - c.foo() from Thread1 calls execSWTThreadWithObject(.., swtcode, ..),
	 *       which waits for the SWT Thread to return run the swtcode.
	 *     - Deadlock, or Timoeout which returns a null (and no code ran)
	 *
	 * @param ID id for debug
	 * @param code code to run
	 * @param millis ms to timeout in
	 * @return
	 */
	public static Object execSWTThreadWithObject(String ID,
			AERunnableObject code, long millis) {
		if (code == null) {
			return null;
		}

		Object[] returnValueObject = {
			null
		};

		Display display = getDisplay();

		AESemaphore sem = null;
		if (display == null || display.getThread() != Thread.currentThread()) {
			sem = new AESemaphore(ID);
		}

		try {
			code.setupReturn(ID, returnValueObject, sem);

			if (!execSWTThread(code)) {
				// XXX: throw instead?
				return null;
			}
		} catch (Throwable e) {
			if (sem != null) {
				sem.releaseForever();
			}
			Debug.out(ID, e);
		}
		if (sem != null) {
			if (!sem.reserve(millis) && DEBUG_SWTEXEC) {
				System.out.println("Timeout in execSWTThreadWithObject(" + ID + ", "
						+ code + ", " + millis + ") via " + Debug.getCompressedStackTrace());
			}
		}

		return returnValueObject[0];
	}

	/**
	 * Waits until modal dialogs are disposed.  Assumes we are on SWT thread
	 *
	 * @since 3.0.1.3
	 */
	public static void waitForModals() {
		SWTThread swt = SWTThread.getInstance();

		Display display;
		if (swt == null) {
			display = Display.getDefault();
			if (display == null) {
				System.err.println("SWT Thread not started yet!");
				return;
			}
		} else {
			if (swt.isTerminated()) {
				return;
			}
			display = swt.getDisplay();
		}

		if (display == null || display.isDisposed()) {
			return;
		}

		Shell[] shells = display.getShells();
		Shell modalShell = null;
		for (int i = 0; i < shells.length; i++) {
			Shell shell = shells[i];
			if ((shell.getStyle() & SWT.APPLICATION_MODAL) != 0) {
				modalShell = shell;
				break;
			}
		}

		if (modalShell != null) {
			while (!modalShell.isDisposed()) {
				if (!display.readAndDispatch()) {
					display.sleep();
				}
			}
		}
	}

	public static GridData getWrappableLabelGridData(int hspan, int styles) {
		GridData gridData = new GridData(GridData.HORIZONTAL_ALIGN_FILL | styles);
		gridData.horizontalSpan = hspan;
		gridData.widthHint = 0;
		return gridData;
	}

  public static Image createAlphaImage(Device device, int width, int height) {
		return createAlphaImage(device, width, height, (byte) 0);
	}

	public static Image createAlphaImage(Device device, int width, int height,
			byte defaultAlpha) {
		byte[] alphaData = new byte[width * height];
		Arrays.fill(alphaData, 0, alphaData.length, (byte) defaultAlpha);

		ImageData imageData = new ImageData(width, height, 24, new PaletteData(
				0xFF, 0xFF00, 0xFF0000));
		Arrays.fill(imageData.data, 0, imageData.data.length, (byte) 0);
		imageData.alphaData = alphaData;
		if (device == null) {
			device = Display.getDefault();
		}
		Image image = new Image(device, imageData);
		return image;
	}

  public static Image blitImage(Device device, Image srcImage,
			Rectangle srcArea, Image dstImage, Point dstPos) {
		if (srcArea == null) {
			srcArea = srcImage.getBounds();
		}
		Rectangle dstBounds = dstImage.getBounds();
		if (dstPos == null) {
			dstPos = new Point(dstBounds.x, dstBounds.y);
		} else {
			dstBounds.x = dstPos.x;
			dstBounds.y = dstPos.y;
		}

		ImageData dstImageData = dstImage.getImageData();
		ImageData srcImageData = srcImage.getImageData();
		int yPos = dstPos.y;
		int[] pixels = new int[srcArea.width];
		byte[] alphas = new byte[srcArea.width];
		for (int y = 0; y < srcArea.height; y++) {
			srcImageData.getPixels(srcArea.x, y + srcArea.y, srcArea.width, pixels, 0);
			dstImageData.setPixels(dstPos.x, yPos, srcArea.width, pixels, 0);
			srcImageData.getAlphas(srcArea.x, y + srcArea.y, srcArea.width, alphas, 0);
			dstImageData.setAlphas(dstPos.x, yPos, srcArea.width, alphas, 0);
			yPos++;
		}

		return new Image(device, dstImageData);
	}

	/**
	 * Draws diagonal stripes onto the specified area of a GC
	 * @param lineDist spacing between the individual lines
	 * @param leftshift moves the stripes to the left, useful to shift with the background
	 * @param fallingLines true for top left to bottom-right lines, false otherwise
	 */
	public static void drawStriped(GC gcImg, int x, int y, int width, int height,
			int lineDist, int leftshift, boolean fallingLines) {
		lineDist += 2;
		final int xm = x + width;
		final int ym = y + height;
		for (int i = x; i < xm; i++) {
			for (int j = y; j < ym; j++) {
				if ((i + leftshift + (fallingLines ? -j : j)) % lineDist == 0)
					gcImg.drawPoint(i, j);
			}
		}
	}

	/**
	 *
	 * @param display
	 * @param background
	 * @param foreground
	 * @param foregroundOffsetOnBg
	 * @param modifyForegroundAlpha 0 (fully transparent) to 255 (retain current alpha)
	 * @return
	 */
	public static Image renderTransparency(Display display, Image background,
			Image foreground, Point foregroundOffsetOnBg, int modifyForegroundAlpha) {
		//Checks
		if (display == null || display.isDisposed() || background == null
				|| background.isDisposed() || foreground == null
				|| foreground.isDisposed())
			return null;
		Rectangle backgroundArea = background.getBounds();
		Rectangle foregroundDrawArea = foreground.getBounds();

		foregroundDrawArea.x += foregroundOffsetOnBg.x;
		foregroundDrawArea.y += foregroundOffsetOnBg.y;

		foregroundDrawArea.intersect(backgroundArea);

		if (foregroundDrawArea.isEmpty())
			return null;

		Image image = new Image(display, backgroundArea);

		ImageData backData = background.getImageData();
		ImageData foreData = foreground.getImageData();
		ImageData imgData = image.getImageData();

		PaletteData backPalette = backData.palette;
		ImageData backMask = backData.getTransparencyType() != SWT.TRANSPARENCY_ALPHA
				? backData.getTransparencyMask() : null;
		PaletteData forePalette = foreData.palette;
		ImageData foreMask = foreData.getTransparencyType() != SWT.TRANSPARENCY_ALPHA
				? foreData.getTransparencyMask() : null;
		PaletteData imgPalette = imgData.palette;
		image.dispose();

		for (int x = 0; x < backgroundArea.width; x++) {
			for (int y = 0; y < backgroundArea.height; y++) {
				RGB cBack = backPalette.getRGB(backData.getPixel(x, y));
				int aBack = backData.getAlpha(x, y);
				if (backMask != null && backMask.getPixel(x, y) == 0)
					aBack = 0; // special treatment for icons with transparency masks

				int aFore = 0;

				if (foregroundDrawArea.contains(x, y)) {
					final int fx = x - foregroundDrawArea.x;
					final int fy = y - foregroundDrawArea.y;
					RGB cFore = forePalette.getRGB(foreData.getPixel(fx, fy));
					aFore = foreData.getAlpha(fx, fy);
					if (foreMask != null && foreMask.getPixel(fx, fy) == 0)
						aFore = 0; // special treatment for icons with transparency masks
					aFore = aFore * modifyForegroundAlpha / 255;
					cBack.red *= aBack * (255 - aFore);
					cBack.red /= 255;
					cBack.red += aFore * cFore.red;
					cBack.red /= 255;
					cBack.green *= aBack * (255 - aFore);
					cBack.green /= 255;
					cBack.green += aFore * cFore.green;
					cBack.green /= 255;
					cBack.blue *= aBack * (255 - aFore);
					cBack.blue /= 255;
					cBack.blue += aFore * cFore.blue;
					cBack.blue /= 255;
				}
				imgData.setAlpha(x, y, aFore + aBack * (255 - aFore) / 255);
				imgData.setPixel(x, y, imgPalette.getPixel(cBack));
			}
		}
		return new Image(display, imgData);
	}

	public static Control findBackgroundImageControl(Control control) {
		Image image = control.getBackgroundImage();
		if (image == null) {
			return control;
		}

		Composite parent = control.getParent();
		Composite lastParent = parent;
		while (parent != null) {
			Image parentImage = parent.getBackgroundImage();
			if (!image.equals(parentImage)) {
				return lastParent;
			}
			lastParent = parent;
			parent = parent.getParent();
		}

		return control;
	}

	/**
	 * @return
	 *
	 * @since 3.0.3.5
	 */
	public static boolean anyShellHaveStyle(int styles) {
		Display display = Display.getCurrent();
		if (display != null) {
			Shell[] shells = display.getShells();
			for (int i = 0; i < shells.length; i++) {
				Shell shell = shells[i];
				int style = shell.getStyle();
				if ((style & styles) == styles) {
					return true;
				}
			}
		}
		return false;
	}

	public static Shell findFirstShellWithStyle(int styles) {
		Display display = Display.getCurrent();
		if (display != null) {
			Shell[] shells = display.getShells();
			for (int i = 0; i < shells.length; i++) {
				Shell shell = shells[i];
				int style = shell.getStyle();
				if ((style & styles) == styles && !shell.isDisposed()) {
					return shell;
				}
			}
		}
		return null;
	}

	public static int[] colorToIntArray(Color color) {
		if (color == null || color.isDisposed()) {
			return null;
		}
		return new int[] {
			color.getRed(),
			color.getGreen(),
			color.getBlue()
		};
	}

	/**
	 * Centers the target <code>Rectangle</code> relative to the reference Rectangle
	 * @param target
	 * @param reference
	 */
	public static void centerRelativeTo(Rectangle target, Rectangle reference) {
		target.x = reference.x + (reference.width / 2) - target.width / 2;
		target.y = reference.y + (reference.height / 2) - target.height / 2;
	}

	/**
	 * Ensure that the given <code>Rectangle</code> is fully visible on the monitor that the cursor
	 * is currently in.  This method does not resize the given Rectangle; it merely reposition it
	 * if appropriate.  If the given Rectangle is taller or wider than the current monitor then
	 * it may not fit 'fully' in the monitor.
	 * <P>
	 * We use a best-effort approach with an emphasis to have at least the top-left of the Rectangle
	 * be visible.  If the given Rectangle does not fit entirely in the monitor then portion
	 * of the right and/or left may be off-screen.
	 *
	 * <P>
	 * This method does honor global screen elements when possible.  Screen elements include the TaskBar on Windows
	 * and the Application menu on OSX, and possibly others.  The re-positioned Rectangle returned will fit on the
	 * screen without overlapping (or sliding under) these screen elements.
	 * @param rect
	 * @return
	 */
	public static void makeVisibleOnCursor(Rectangle rect) {

		if (null == rect) {
			return;
		}

		Display display = Display.getCurrent();
		if (null == display) {
			Debug.out("No current display detected.  This method [Utils.makeVisibleOnCursor()] must be called from a display thread.");
			return;
		}

		try {

			/*
			 * Get cursor location
			 */
			Point cursorLocation = display.getCursorLocation();

			/*
			 * Make visible on the monitor that the mouse cursor resides in
			 */
			makeVisibleOnMonitor(rect, getMonitor(cursorLocation));

		} catch (Throwable t) {
			//Do nothing
		}
	}

	/**
	 * Ensure that the given <code>Rectangle</code> is fully visible on the given <code>Monitor</code>.
	 * This method does not resize the given Rectangle; it merely reposition it if appropriate.
	 * If the given Rectangle is taller or wider than the current monitor then it may not fit 'fully' in the monitor.
	 * <P>
	 * We use a best-effort approach with an emphasis to have at least the top-left of the Rectangle
	 * be visible.  If the given Rectangle does not fit entirely in the monitor then portion
	 * of the right and/or left may be off-screen.
	 *
	 * <P>
	 * This method does honor global screen elements when possible.  Screen elements include the TaskBar on Windows
	 * and the Application menu on OSX, and possibly others.  The re-positioned Rectangle returned will fit on the
	 * screen without overlapping (or sliding under) these screen elements.
	 * @param rect
	 * @param monitor
	 */
	public static void makeVisibleOnMonitor(Rectangle rect, Monitor monitor) {

		if (null == rect || null == monitor) {
			return;
		}

		try {

			Rectangle monitorBounds = monitor.getClientArea();

			/*
			 * Make sure the bottom is fully visible on the monitor
			 */

			int bottomDiff = (monitorBounds.y + monitorBounds.height)
					- (rect.y + rect.height);
			if (bottomDiff < 0) {
				rect.y += bottomDiff;
			}

			/*
			 * Make sure the right is fully visible on the monitor
			 */

			int rightDiff = (monitorBounds.x + monitorBounds.width)
					- (rect.x + rect.width);
			if (rightDiff < 0) {
				rect.x += rightDiff;
			}

			/*
			 * Make sure the left is fully visible on the monitor
			 */
			if (rect.x < monitorBounds.x) {
				rect.x = monitorBounds.x;
			}

			/*
			 * Make sure the top is fully visible on the monitor
			 */
			if (rect.y < monitorBounds.y) {
				rect.y = monitorBounds.y;
			}

		} catch (Throwable t) {
			//Do nothing
		}

	}

	/**
	 * Returns the <code>Monitor</code> that the given x,y coordinates resides in
	 * @param x
	 * @param y
	 * @return the monitor if found; otherwise returns <code>null</code>
	 */
    private static Monitor getMonitor(int x, int y) {
		return getMonitor(new Point(x, y));
	}

	/**
	 * Returns the <code>Monitor</code> that the given <code>Point</code> resides in
	 * @param location
	 * @return the monitor if found; otherwise returns <code>null</code>
	 */
	public static Monitor getMonitor(Point location) {
		Display display = Display.getCurrent();

		if (null == display) {
			Debug.out("No current display detected.  This method [Utils.makeVisibleOnCursor()] must be called from a display thread.");
			return null;
		}

		try {

			/*
			 * Find the monitor that this location resides in
			 */
			Monitor[] monitors = display.getMonitors();
			Rectangle monitorBounds = null;
			for (int i = 0; i < monitors.length; i++) {
				monitorBounds = monitors[i].getClientArea();
				if (monitorBounds.contains(location)) {
					return monitors[i];
				}
			}
		} catch (Throwable t) {
			//Do nothing
		}

		return null;
	}

	public static void makeButtonsEqualWidth(
		List<Button>	buttons) {
		int width = 75;

		for (Button button: buttons) {

			width = Math.max(width, button.computeSize( SWT.DEFAULT, SWT.DEFAULT ).x);
		}

		for (Button button: buttons) {
			Object	data = button.getLayoutData();
			if (data != null) {
				if (data instanceof GridData) {
					((GridData)data).widthHint = width;
				} else if (data instanceof FormData) {
					((FormData)data).width = width;
				} else {
					Debug.out("Expected GridData/FormData");
				}
			} else {
				data = new GridData();
				((GridData) data).widthHint = width;
				button.setLayoutData(data);
			}
		}
	}

	private static boolean gotBrowserStyle = false;

	private static int browserStyle = SWT.NONE;

	/**
	 * Consistently applies the browser style obtained during the first invocation
	 * @param style the style you wish to apply
	 * @return the style, possibly ORed with <code>SWT.MOZILLA</code>
	 */
	public static int getInitialBrowserStyle(int style) {
		if (!gotBrowserStyle) {
			browserStyle = COConfigurationManager.getBooleanParameter("swt.forceMozilla")
					? SWT.MOZILLA : SWT.NONE;
			gotBrowserStyle = true;
		}
		return style | browserStyle;
	}

	private static Map truncatedTextCache = new HashMap();

	private static ThreadPool tp = new ThreadPool("GetOffSWT", 3, true);

	private static class TruncatedTextResult {
		String text;
		int maxWidth;

		public TruncatedTextResult() {
		}
	}

	public synchronized static String truncateText(GC gc,String text, int maxWidth,boolean cache) {
		if (cache) {
			TruncatedTextResult result = (TruncatedTextResult) truncatedTextCache.get(text);
			if (result != null && result.maxWidth == maxWidth) {
				return result.text;
			}
		}
		StringBuilder sb = new StringBuilder(text);
		String append = "...";
		int appendWidth = gc.textExtent(append).x;
		boolean needsAppend = false;
		while (gc.textExtent(sb.toString()).x > maxWidth) {
			//Remove characters until they fit into the maximum width
			sb.deleteCharAt(sb.length()-1);
			needsAppend = true;
			if (sb.length() == 1) {
				break;
			}
		}

		if (needsAppend) {
			while (gc.textExtent(sb.toString()).x + appendWidth > maxWidth) {
				//Remove characters until they fit into the maximum width
				sb.deleteCharAt(sb.length()-1);
				needsAppend = true;
				if (sb.length() == 1) {
					break;
				}
			}
			sb.append(append);
		}



		if (cache) {
			TruncatedTextResult ttR = new TruncatedTextResult();
			ttR.text = sb.toString();
			ttR.maxWidth = maxWidth;

			truncatedTextCache.put(text, ttR);
		}

		return sb.toString();
	}

	/**
	 * @param bg
	 * @return
	 *
	 * @since 3.1.1.1
	 */
	public static String toColorHexString(Color bg) {
		StringBuffer sb = new StringBuffer();
		twoHex(sb, bg.getRed());
		twoHex(sb, bg.getGreen());
		twoHex(sb, bg.getBlue());
		return sb.toString();
	}

	private static void twoHex(StringBuffer sb, int h) {
		if (h <= 15) {
			sb.append('0');
		}
		sb.append(Integer.toHexString(h));
	}

	public static String getWidgetBGColorURLParam() {
		Color bg = findAnyShell().getDisplay().getSystemColor(SWT.COLOR_WIDGET_BACKGROUND);

		byte[] color = new byte[3];

		color[0] = (byte) bg.getRed();
		color[1] = (byte) bg.getGreen();
		color[2] = (byte) bg.getBlue();

		return ("bg_color=" + ByteFormatter.nicePrint(color));
	}

	public static void reportError(
		Throwable e) {
		MessageBoxShell mb =
			new MessageBoxShell(
				MessageText.getString("ConfigView.section.security.op.error.title"),
				MessageText.getString("ConfigView.section.security.op.error",
						new String[] {
							Debug.getNestedExceptionMessage(e)
						}),
				new String[] {
					MessageText.getString("Button.ok"),
				},
				0);

		mb.open(null);
	}

	public static void getOffOfSWTThread(AERunnable runnable) {
		tp.run(runnable);
	}

	public static BrowserWrapper
	createSafeBrowser(
		Composite parent, int style) {
		try {
		final BrowserWrapper browser = BrowserWrapper.createBrowser(parent, Utils.getInitialBrowserStyle(style));
  		browser.addDisposeListener(new DisposeListener() {
  			public void widgetDisposed(DisposeEvent e)
  			{
  					/*
  					 * Intent here seems to be to run all pending events through the queue to ensure
  					 * that the 'setUrl' and 'setVisible' actions are complete before disposal in an
  					 * attempt to ensure memory released. This is old code and the new 4508 SWT introduced
  					 * in Dec 2014 causes the 'readAndDispatch' loop to bever exit.
  					 * So added code to queue an async event and hope that by the time this runs
  					 * any housekeeping is complete
  					 * Meh, tested and the injected code doesn't run until, say, you move the window containing
  					 * the browser. So added timeout
  					 */

  				browser.setUrl("about:blank");

  				browser.setVisible(false);

  				final boolean[]	done = {false};

  				final long start = SystemTime.getMonotonousTime();

  				execSWTThreadLater(
  					250,
  					new Runnable()
  					{
  						public void
  						run()
  						{
  							synchronized(done) {

  								done[0] = true;
  							}
  						}
  					});

  				while (!e.display.isDisposed() && e.display.readAndDispatch()) {

  					synchronized(done) {

  						if (done[0] || SystemTime.getMonotonousTime() - start > 500) {

  							break;
  						}
  					}
  				}
  			}
  		});
  		return browser ;
		} catch (Throwable e) {
		}
		return null;
	}

	public static int getUserMode() {
		return userMode;
	}

	public static Point getLocationRelativeToShell(Control control) {
		Point controlLocation = control.toDisplay(0, 0);
		Point shellLocation = control.getShell().getLocation();
		return new Point(controlLocation.x - shellLocation.x, controlLocation.y - shellLocation.y);
	}

	private static Set<String>	qv_exts = new HashSet<String>();
	private static int			qv_max_bytes;

	static{
		COConfigurationManager.addAndFireParameterListeners(
			new String[]{ "quick.view.exts", "quick.view.maxkb" },
			new ParameterListener() {
				public void parameterChanged(
					String name ) {
					String	exts_str 	= COConfigurationManager.getStringParameter("quick.view.exts");
					int		max_bytes	= COConfigurationManager.getIntParameter("quick.view.maxkb")*1024;

					String[]	bits = exts_str.split("[;, ]");

					Set<String>	exts = new HashSet<String>();

					for (String bit: bits) {

						bit = bit.trim();

						if (bit.startsWith(".")) {

							bit = bit.substring(1);
						}

						if (bit.length() > 0) {

							exts.add( bit.toLowerCase());
						}
					}

					qv_exts 		= exts;
					qv_max_bytes	= max_bytes;
				}
			});
	}

	public static boolean
	isQuickViewSupported(
		DiskManagerFileInfo	file) {
		String ext = file.getExtension().toLowerCase();

		if (ext.startsWith(".")) {

			ext = ext.substring(1);
		}

			// always support .rar

		if (ext.equals("rar")) {

			return (true);
		}

		if (qv_exts.contains( ext)) {

			if (file.getLength() <= qv_max_bytes) {

				return (true);
			}
		}

		return (false);
	}

	public static boolean
	isQuickViewActive(
		DiskManagerFileInfo	file) {
		synchronized(quick_view_active) {

			return (quick_view_active.contains( file));
		}
	}

	public static void setQuickViewActive(
		DiskManagerFileInfo	file,
		boolean				active) {
		synchronized(quick_view_active) {

			if (!active) {

				quick_view_active.remove(file);

				return;
			}

			if (quick_view_active.contains( file)) {

				return;
			}

			String ext = file.getExtension().toLowerCase();

			boolean	file_complete = file.getDownloaded() == file.getLength();

			if (ext.equals(".rar")) {

				quick_view_active.add(file);

				quickViewRAR(file);

			} else {

				if (file_complete) {

					quickView(file);

				} else {

					quick_view_active.add(file);

					if (file.isSkipped()) {

						file.setSkipped(false);
					}

					file.setPriority(1);

					DiskManagerFileInfo[] all_files = file.getDownloadManager().getDiskManagerFileInfoSet().getFiles();

					for (DiskManagerFileInfo f: all_files) {

						if (!quick_view_active.contains( f)) {

							f.setPriority(0);
						}
					}

					if (quick_view_event == null) {

						quick_view_event =
							SimpleTimer.addPeriodicEvent(
								"qv_checker",
								5*1000,
								new TimerEventPerformer() {
									public void perform(
										TimerEvent event) {
										synchronized(quick_view_active) {

											Iterator<DiskManagerFileInfo> it = quick_view_active.iterator();

											while (it.hasNext()) {

												DiskManagerFileInfo file = it.next();

												if (file.getDownloadManager().isDestroyed()) {

													it.remove();

												} else {

													if (file.getDownloaded() == file.getLength()) {

														quickView(file);

														it.remove();
													}
												}
											}

											if (quick_view_active.isEmpty()) {

												quick_view_event.cancel();

												quick_view_event = null;
											}
										}
									}
								});
					}
				}
			}

			if (!file_complete) {

				execSWTThreadLater(
					10,
					new Runnable() {
						public void run() {
							MessageBoxShell mb =
								new MessageBoxShell(
										SWT.OK,
										MessageText.getString("quick.view.scheduled.title"),
										MessageText.getString("quick.view.scheduled.text"));

							mb.setDefaultButtonUsingStyle(SWT.OK);
							mb.setRemember("quick.view.inform.activated.id", false, MessageText.getString("label.dont.show.again"));
							mb.setLeftImage(SWT.ICON_INFORMATION);
							mb.open(null);
						}
					});
			}
		}
	}

	private static void quickView(
		final DiskManagerFileInfo		file) {
		try {
			final File		target_file = file.getFile(true);

			final String contents = FileUtil.readFileAsString(target_file, qv_max_bytes);

			execSWTThread(
				new Runnable() {
					public void run() {
						if (getExplicitLauncher( target_file.getName()) != null) {

							launch( target_file.getAbsolutePath());

						} else {

							DownloadManager dm = file.getDownloadManager();

							Image	image;

							try {
								InputStream is = null;

								try {
									is = new FileInputStream(target_file);

									image = new Image( getDisplay(), is);

								} finally {

									if (is != null) {

										is.close();
									}
								}
							} catch (Throwable e) {

								image = null;
							}

							if (image != null) {

								new ImageViewerWindow(
										MessageText.getString("MainWindow.menu.quick_view") + ": " + target_file.getName(),
										MessageText.getString(
											"MainWindow.menu.quick_view.msg",
											new String[]{ target_file.getName(), dm.getDisplayName() }),
											image );
							} else {

								new TextViewerWindow(
										MessageText.getString("MainWindow.menu.quick_view") + ": " + target_file.getName(),
										MessageText.getString(
											"MainWindow.menu.quick_view.msg",
											new String[]{ target_file.getName(), dm.getDisplayName() }),
										contents, false );
							}
						}
					}
				});
		} catch (Throwable e) {

			Debug.out(e);
		}
	}

	private static void quickViewRAR(
		final DiskManagerFileInfo		file) {
		boolean went_async = false;

		try {
			final org.gudy.azureus2.plugins.disk.DiskManagerFileInfo plugin_file =  PluginCoreUtils.wrap(file);

			final RARTOCDecoder decoder =
				new RARTOCDecoder(
					new RARTOCDecoder.DataProvider() {
						private long	file_position;
						private long	file_size = file.getLength();

						public int read(
							final byte[]		buffer )

							throws IOException
						{
							long	read_from 	= file_position;
							int		read_length	= buffer.length;

							long	read_to = Math.min(file_size, read_from + read_length);

							read_length = (int)(read_to - read_from);

							if (read_length <= 0) {

								return (-1);
							}

							final int f_read_length = read_length;

							try {
								final AESemaphore sem = new AESemaphore("rarwait");

								final Object[] result = { null };

								plugin_file.createRandomReadRequest(
									read_from, read_length, false,
									new DiskManagerListener() {
										private int	buffer_pos;

										public void eventOccurred(
											DiskManagerEvent	event) {
											int	event_type = event.getType();

											if (event_type == DiskManagerEvent.EVENT_TYPE_SUCCESS) {

												PooledByteBuffer pooled_buffer = event.getBuffer();

												try {
													byte[] data = pooled_buffer.toByteArray();

													System.arraycopy(data, 0, buffer, buffer_pos, data.length);

													buffer_pos += data.length;

													if (buffer_pos == f_read_length) {

														sem.release();
													}

												} finally {

													pooled_buffer.returnToPool();
												}
											} else if (event_type == DiskManagerEvent.EVENT_TYPE_FAILED) {

												result[0] = event.getFailure();

												sem.release();
											}
										}
									});

								sem.reserve();

								if (result[0] instanceof Throwable) {

									throw ((Throwable)result[0]);
								}

								file_position += read_length;

								return (read_length);

							} catch (Throwable e) {

								throw (new IOException("read failed: " + Debug.getNestedExceptionMessage( e)));
							}
						}

						public void skip(
							long		bytes )

							throws IOException
						{
							file_position += bytes;
						}
					});

			new AEThread2("rardecoder") {
				public void run() {
					try {
						decoder.analyse(
							new RARTOCDecoder.TOCResultHandler() {
								private TextViewerWindow	viewer;
								private List<String>		lines = new ArrayList<String>();

								private int	pw_entries 	= 0;
								private int pw_text		= 0;

								private volatile boolean	abandon = false;

								public void entryRead(
									final String 		name,
									final long 			size,
									final boolean 		password )

									throws IOException
								{
									if (abandon) {

										throw (new IOException("Operation abandoned"));
									}

									String line = name + ":    " + DisplayFormatters.formatByteCountToKiBEtc(size);

									if (password) {

										line += "    **** password protected ****";

										pw_entries++;
									}

									if (password || name.toLowerCase().contains("password")) {

										line = "*\t" + line;

										pw_text++;

									} else {

										line = " \t" + line;
									}

									appendLine(line, false);
								}

								public void complete() {
									appendLine("Done", true);
								}

								public void failed(
									IOException error) {
									appendLine("Failed: " + Debug.getNestedExceptionMessage( error ), true);
								}

								private String getInfo() {
									if (pw_entries > 0) {

										return (pw_entries + " password protected file(s) found");

									} else if (pw_text > 0) {

										return (pw_text + " file(s) mentioning 'password' found");
									}

									return ("");
								}

								private void appendLine(
									final String	line,
									final boolean	complete) {
									execSWTThread(
										new Runnable() {
											public void run() {
												lines.add(line);

												StringBuilder content = new StringBuilder();

												for (String l: lines) {

													content.append(l).append("\r\n");
												}

												if (!complete) {

													content.append("processing...");

												} else {

													String info = getInfo();

													if (info.length() > 0) {

														content.append(info).append("\r\n");
													}
												}

												if (viewer == null) {

													final File		target_file = file.getFile(true);

													DownloadManager dm = file.getDownloadManager();

													viewer = new TextViewerWindow(
														MessageText.getString("MainWindow.menu.quick_view") + ": " + target_file.getName(),
														MessageText.getString(
															"MainWindow.menu.quick_view.msg",
															new String[]{ target_file.getName(), dm.getDisplayName() }),
														content.toString(), false );

												} else {

													if (viewer.isDisposed()) {

														abandon = true;

													} else {

														viewer.setText( content.toString());
													}
												}
											}
										});
								}

							});
					} catch (Throwable e) {

						Debug.out(e);

					} finally {

						synchronized(quick_view_active) {

							quick_view_active.remove(file);
						}
					}
				}
			}.start();

			went_async = true;

		} catch (Throwable e) {

			Debug.out(e);

		} finally {

			if (!went_async) {

				synchronized(quick_view_active) {

					quick_view_active.remove(file);
				}
			}
		}
	}

	public static Sash createSash(
		Composite	form,
		int			sashWidth) {
	    final Sash sash = new Sash(form, SWT.HORIZONTAL);
	    Image image = new Image(sash.getDisplay(), 9, sashWidth);
	    ImageData imageData = image.getImageData();
	    int[] row = new int[imageData.width];
	    for (int i = 0; i < row.length; i++) {
	    	if (imageData.depth == 16) {
	    		row[i] = (i % 3) != 0 ? 0x7BDEF7BD : 0xDEF7BDEB;
	    	} else {
	    		row[i] = (i % 3) != 0 ? 0xE0E0E0 : 0x808080;
	    		if (imageData.depth == 32) {
	    			row[i] = (row[i] & 255) + (row[i] << 8);
	    		}
	    	}
			}
	    for (int y = 1; y < imageData.height - 1; y++) {
	    	imageData.setPixels(0, y, row.length, row, 0);
	    }
	    Arrays.fill(row, imageData.depth == 16 ? 0x7BDEF7BD : 0xE0E0E0E0);
	  	imageData.setPixels(0, 0, row.length, row, 0);
	  	imageData.setPixels(0, imageData.height - 1, row.length, row, 0);
	    image.dispose();
	    image = new Image(sash.getDisplay(), imageData);
	    sash.setBackgroundImage(image);
	    sash.addDisposeListener(new DisposeListener() {
				public void widgetDisposed(DisposeEvent e) {
					sash.getBackgroundImage().dispose();
				}
			});

	    return (sash);
	}

	/**
	 * Sometimes, Display.getCursorControl doesn't go deep enough..
	 */
	public static Control getCursorControl() {
		Display d = Utils.getDisplay();
		Point cursorLocation = d.getCursorLocation();
		Control cursorControl = d.getCursorControl();

		if (cursorControl instanceof Composite) {
			return getCursorControl((Composite) cursorControl, cursorLocation);
		}

		return cursorControl;
	}

	public static Control getCursorControl(Composite parent, Point cursorLocation) {
		for (Control con : parent.getChildren()) {
			Rectangle bounds = con.getBounds();
			Point displayLoc = con.toDisplay(0, 0);
			bounds.x = displayLoc.x;
			bounds.y = displayLoc.y;
			boolean found = bounds.contains(cursorLocation);
			if (found) {
				if (con instanceof Composite) {
					return getCursorControl((Composite) con, cursorLocation);
				}
				return con;
			}
		}
		return parent;
	}

	public static void relayoutUp(Composite c) {
		while (c != null && !c.isDisposed()) {
			Composite newParent = c.getParent();
			if (newParent == null) {
				break;
			}
			newParent.layout(new Control[] { c });
			c = newParent;
		}
	}

	public static void updateScrolledComposite(ScrolledComposite sc) {
		Control content = sc.getContent();
		if (content != null && !content.isDisposed()) {
			Rectangle r = sc.getClientArea();
			sc.setMinSize(content.computeSize(r.width, SWT.DEFAULT ));
		}
	}

	public static void maintainSashPanelWidth(
		final SashForm		sash,
		final Composite		comp,
		final int[]			default_weights,
		final String		config_key) {
		final boolean is_lhs = comp == sash.getChildren()[0];

		String str = COConfigurationManager.getStringParameter(config_key, default_weights[0]+","+default_weights[1]);

		try {
			String[] bits = str.split(",");

			sash.setWeights(new int[]{ Integer.parseInt( bits[0] ), Integer.parseInt( bits[1])});

		} catch (Throwable e) {

			sash.setWeights(default_weights);
		}

		Listener sash_listener=
	    	new Listener()
	    	{
	    		private int	comp_weight;
	    		private int	comp_width;

		    	public void handleEvent(
					Event ev ) {
		    		if (ev.widget == comp) {

		    			int[] weights = sash.getWeights();

		    			int current_weight = weights[is_lhs?0:1];

		    			if (comp_weight != current_weight) {

		    				COConfigurationManager.setParameter(config_key, weights[0]+","+weights[1]);

		    					// sash has moved

		    				comp_weight = current_weight;

		    					// keep track of the width

		    				comp_width = comp.getBounds().width;
		    			}
		    		} else {

		    				// resize

		    			if (comp_width > 0) {

				            int width = sash.getClientArea().width;

				            if (width < 20) {

				            	width = 20;
				            }

				            double ratio = (double)comp_width/width;

				            comp_weight = (int)(ratio*1000);

				            if (comp_weight < 20) {

				            	comp_weight = 20;

				            } else if (comp_weight > 980) {

				            	comp_weight = 980;
				            }

				            if (is_lhs) {

				            	sash.setWeights(new int[]{ comp_weight, 1000 - comp_weight });

				            } else {

				            	sash.setWeights(new int[]{ 1000 - comp_weight, comp_weight });
				            }
		    			}
		    		}
			    }
		    };

		comp.addListener(SWT.Resize, sash_listener);
	    sash.addListener(SWT.Resize, sash_listener);
	}

	private static Point getDPI() {
		if (dpi == null) {
			boolean enableForceDPI = COConfigurationManager.getBooleanParameter("enable.ui.forceDPI");
			if (enableForceDPI) {
				int forceDPI = COConfigurationManager.getIntParameter("Force DPI");
				if (forceDPI > 0) {
					dpi = new Point(forceDPI, forceDPI);
					return dpi;
				}
			}
			Display display = getDisplay();
			if (display == null) {
				return new Point(0, 0);
			}
			dpi = getDPIRaw(display);
			COConfigurationManager.setIntDefault("Force DPI", dpi.x);
			if (dpi.x <= 96 || dpi.y <= 96) {
				dpi = new Point(0, 0);
			}
		}
		return dpi;
	}

	private static boolean logged_invalid_dpi = false;

	public static Point	getDPIRaw(Device device) {
		Point p = device.getDPI();
		if (p.x < 0 || p.y < 0 || p.x > 8192 || p.y > 8192) {
			if (!logged_invalid_dpi) {
				logged_invalid_dpi = true;
				Debug.outNoStack("Invalid DPI: " + p);
			}
			return (new Point(96, 96));
		}
		return (p);
	}


	public static int adjustPXForDPI(int unadjustedPX) {
		if (unadjustedPX == 0) {
			return unadjustedPX;
		}
		int xDPI = getDPI().x;
		if (xDPI == 0) {
			return unadjustedPX;
		}
		return unadjustedPX * xDPI / DEFAULT_DPI;
	}

	public static Rectangle adjustPXForDPI(Rectangle bounds) {
		Point dpi = getDPI();
		if (dpi.x == 0) {
			return bounds;
		}
		return new Rectangle(bounds.x * dpi.x / DEFAULT_DPI,
				bounds.y * dpi.y / DEFAULT_DPI, bounds.width * dpi.x / DEFAULT_DPI,
				bounds.height * dpi.y / DEFAULT_DPI);
	}

	public static Point adjustPXForDPI(Point size) {
		Point dpi = getDPI();
		if (dpi.x == 0) {
			return size;
		}
		return new Point(size.x * dpi.x / DEFAULT_DPI,
				size.y * dpi.y / DEFAULT_DPI);
	}

	public static void adjustPXForDPI(FormData fd) {
		Point dpi = getDPI();
		if (dpi.x == 0) {
			return;
		}
		adjustPXForDPI(fd.left);
		adjustPXForDPI(fd.right);
		adjustPXForDPI(fd.top);
		adjustPXForDPI(fd.bottom);
		if (fd.width > 0) {
			fd.width = adjustPXForDPI(fd.width);
		}
		if (fd.height > 0) {
			fd.height = adjustPXForDPI(fd.height);
		}
	}

	public static void adjustPXForDPI(FormAttachment fa) {
		if (fa == null) {
			return;
		}
		if (fa.offset != 0) {
			fa.offset = adjustPXForDPI(fa.offset);
		}
	}

	public static void setLayoutData(Control widget, GridData layoutData) {
		adjustPXForDPI(layoutData);
		widget.setLayoutData(layoutData);
	}

	private static void adjustPXForDPI(GridData layoutData) {
		Point dpi = getDPI();
		if (dpi.x == 0) {
			return;
		}
		if (layoutData.heightHint > 0) {
			layoutData.heightHint = adjustPXForDPI(layoutData.heightHint);
		}
		if (layoutData.horizontalIndent > 0) {
			layoutData.horizontalIndent = adjustPXForDPI(layoutData.horizontalIndent);
		}
		if (layoutData.minimumHeight > 0) {
			layoutData.minimumHeight = adjustPXForDPI(layoutData.minimumHeight);
		}
		if (layoutData.verticalIndent > 0) {
			layoutData.verticalIndent = adjustPXForDPI(layoutData.verticalIndent);
		}
		if (layoutData.minimumWidth > 0) {
			layoutData.minimumWidth = adjustPXForDPI(layoutData.minimumWidth);
		}
		if (layoutData.widthHint > 0) {
			layoutData.widthHint = adjustPXForDPI(layoutData.widthHint);
		}
	}

	public static void setLayoutData(Control widget, FormData layoutData) {
		adjustPXForDPI(layoutData);
		widget.setLayoutData(layoutData);
	}

	public static void setLayoutData(Control item, RowData rowData) {
		if (rowData.height > 0) {
			rowData.height = adjustPXForDPI(rowData.height);
		}
		if (rowData.width > 0) {
			rowData.width = adjustPXForDPI(rowData.width);
		}
		item.setLayoutData(rowData);
	}

	public static void setLayoutData(BufferedLabel label, GridData gridData) {
		adjustPXForDPI(gridData);
		label.setLayoutData(gridData);
	}

	public static void adjustPXForDPI(Object layoutData) {
		if (layoutData instanceof GridData) {
			GridData gd = (GridData) layoutData;
			adjustPXForDPI(gd);
		} else if (layoutData instanceof FormData) {
			FormData fd = (FormData) layoutData;
			adjustPXForDPI(fd);
		} else if (layoutData instanceof RowData) {
			RowData fd = (RowData) layoutData;
			adjustPXForDPI(fd);
		}
	}

	private static final WeakHashMap<Image,String>	scaled_images = new WeakHashMap<Image, String>();
	private static int	scaled_imaged_check_count = 0;

	public static boolean
	adjustPXForDPIRequired(
		Image		image) {
		Point dpi = Utils.getDPI();
		if (dpi.x > 0) {
			return (!scaled_images.containsKey( image));
		} else {
			return (false);
		}
	}

	public static Image
	adjustPXForDPI(
		Display		display,
		Image		image) {
		Point dpi = Utils.getDPI();

		if (dpi.x > 0) {

			try {
				Rectangle bounds = image.getBounds();
				Rectangle newBounds = Utils.adjustPXForDPI(bounds);

				ImageData scaledTo = image.getImageData().scaledTo(newBounds.width, newBounds.height);

				Image newImage = new Image(display, scaledTo);

				if (scaled_imaged_check_count++ % 100 == 0) {
					Iterator<Image> it = scaled_images.keySet().iterator();
					while (it.hasNext()) {
						if (it.next().isDisposed()) {
							it.remove();
						}
					}
				}

				scaled_images.put(newImage, "");

				image.dispose();

				return (newImage);

			} catch (Throwable e) {

				Debug.out("Image DPI adjustment failed: " + Debug.getNestedExceptionMessage(e));
			}
		}

		return (image);
	}

	public static void setLayout(Composite composite, GridLayout layout) {
		Point dpi = getDPI();
		if (dpi.x == 0) {
			composite.setLayout(layout);
			return;
		}

		layout.marginBottom = adjustPXForDPI(layout.marginBottom);
		layout.marginHeight = adjustPXForDPI(layout.marginHeight);
		layout.marginLeft = adjustPXForDPI(layout.marginLeft);
		layout.marginRight = adjustPXForDPI(layout.marginRight);
		layout.marginTop = adjustPXForDPI(layout.marginTop);
		layout.marginWidth = adjustPXForDPI(layout.marginWidth);
		layout.horizontalSpacing = adjustPXForDPI(layout.horizontalSpacing);
		layout.verticalSpacing = adjustPXForDPI(layout.verticalSpacing);

		composite.setLayout(layout);
	}

	public static void setLayout(Composite composite, RowLayout layout) {
		Point dpi = getDPI();
		if (dpi.x == 0) {
			composite.setLayout(layout);
			return;
		}

		layout.marginBottom = adjustPXForDPI(layout.marginBottom);
		layout.marginHeight = adjustPXForDPI(layout.marginHeight);
		layout.marginLeft = adjustPXForDPI(layout.marginLeft);
		layout.marginRight = adjustPXForDPI(layout.marginRight);
		layout.marginTop = adjustPXForDPI(layout.marginTop);
		layout.marginWidth = adjustPXForDPI(layout.marginWidth);
		layout.spacing = adjustPXForDPI(layout.spacing);


		composite.setLayout(layout);
	}

	public static void setClipping(GC gc, Rectangle r) {
		if (r == null) {
			if (isGTK3) {
				// On OSX SWT, this will cause NPE
				gc.setClipping((Path) null);
			} else {
				// On GTK3 SWT, this is specifically disabled in their code (grr!)
				gc.setClipping((Rectangle) null);
			}
			return;
		}
		gc.setClipping(r.x, r.y, r.width, r.height);
	}
}
