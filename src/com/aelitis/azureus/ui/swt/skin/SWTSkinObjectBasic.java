/*
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

package com.aelitis.azureus.ui.swt.skin;

import java.util.*;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.widgets.*;

import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.util.*;
import org.gudy.azureus2.plugins.PluginInterface;
import org.gudy.azureus2.pluginsimpl.local.PluginInitializer;
import org.gudy.azureus2.ui.swt.Utils;
import org.gudy.azureus2.ui.swt.debug.ObfusticateImage;

import com.aelitis.azureus.ui.UIFunctionsManager;
import com.aelitis.azureus.ui.common.updater.UIUpdatable;
import com.aelitis.azureus.ui.swt.imageloader.ImageLoader;
import com.aelitis.azureus.ui.swt.utils.ColorCache;
import com.aelitis.azureus.ui.swt.views.skin.SkinView;
import com.aelitis.azureus.util.StringCompareUtils;

import hello.util.Log;

/**
 * @author TuxPaper
 * @created Jun 12, 2006
 *
 */
public class SWTSkinObjectBasic
	implements SWTSkinObject, PaintListener, ObfusticateImage {
	
	private static String TAG = SWTSkinObjectBasic.class.getSimpleName();
	
	protected static final int BORDER_ROUNDED = 1;
	protected static final int BORDER_ROUNDED_FILL = 2;
	protected static final int BORDER_GRADIENT = 3;
	protected Control control;
	protected String type;
	protected String sConfigID;
	protected SWTBGImagePainter painter;
	protected SWTSkinProperties properties;
	protected String sID;
	// XXX Might be wise to force this to SWTSkinObjectContainer
	protected SWTSkinObject parent;
	protected SWTSkin skin;
	protected String[] suffixes = null;
	protected ArrayList<SWTSkinObjectListener> listeners = new ArrayList<SWTSkinObjectListener>();
	protected AEMonitor listeners_mon = new AEMonitor(
			"SWTSkinObjectBasic::listener");
	private String sViewID;
	private int isVisible = -1;
	protected Color bgColor;
	private Color colorBorder;
	private int[] colorBorderParams = null;
	private int[] colorFillParams;
	private int colorFillType;
	boolean initialized = false;
	boolean paintListenerHooked = false;
	boolean alwaysHookPaintListener = false;
	private Map mapData = Collections.EMPTY_MAP;
	private boolean disposed = false;
	protected boolean debug = false;
	private List<GradientInfo> listGradients = new ArrayList<GradientInfo>();
	private Image bgImage;
	private String tooltipID;
	protected boolean customTooltipID = false;
	private Listener resizeGradientBGListener;
	private SkinView skinView;
	private Object datasource;
	private boolean firstVisibility;
	private boolean layoutComplete;
	private ObfusticateImage obfusticatedImageGenerator;

	/**
	 * @param properties TODO
	 *
	 */
	public SWTSkinObjectBasic(SWTSkin skin, SWTSkinProperties properties,
			Control control, String sID, String sConfigID, String type,
			SWTSkinObject parent) {
		this(skin, properties, sID, sConfigID, type, parent);
		setControl(control);
	}

	//private static int logCnt = 0;
	
	public SWTSkinObjectBasic(SWTSkin skin, SWTSkinProperties properties,
			String sID, String sConfigID, String type, SWTSkinObject parent) {
		this.skin = skin;
		this.properties = properties;
		this.sConfigID = sConfigID;
		this.sID = sID;
		this.type = type;
		this.parent = parent;
		setViewID(properties.getStringValue(sConfigID + ".view"));
		setDebug(properties.getBooleanValue(sConfigID + ".debug", false));
		
		/*//if (logCnt++ <= 3) {
			//Log.d(TAG, "ctor() is called...");
			Log.d(TAG, "sConfigID = " + sConfigID);
			if (++logCnt == 1
					|| sConfigID.equals("main.area.body")) {
				Throwable t = new Throwable();
				t.printStackTrace();
			}
		//}*/
	}

	public void setControl(final Control _control) {

		firstVisibility = properties.getBooleanValue(sConfigID + ".visible", true);

		if (!Utils.isThisThreadSWT()) {
			Debug.out("Warning: setControl not called in SWT thread for " + this);
			Utils.execSWTThread(new AERunnable() {
				public void runSupport() {
					setControl(control);
				}
			});
			return;
		}

		resizeGradientBGListener = new Listener() {
			public void handleEvent(Event event) {
				if (bgImage != null && !bgImage.isDisposed()) {
					bgImage.dispose();
				}
				Rectangle bounds = control.getBounds();
				if (bounds.height <= 0) {
					return;
				}
				bgImage = new Image(control.getDisplay(), 5, bounds.height);
				GC gc = new GC(bgImage);
				try {
					try {
						gc.setAdvanced(true);
						gc.setInterpolation(SWT.HIGH);
						gc.setAntialias(SWT.ON);
					} catch (Exception ex) {
					}

					GradientInfo lastGradInfo = new GradientInfo(bgColor, 0);
					for (GradientInfo gradInfo : listGradients) {
						if (gradInfo.startPoint != lastGradInfo.startPoint) {
							gc.setForeground(lastGradInfo.color);
							gc.setBackground(gradInfo.color);

							int y = (int) (bounds.height * lastGradInfo.startPoint);
							int height = (int) (bounds.height * gradInfo.startPoint) - y;
							gc.fillGradientRectangle(0, y, 5, height, true);
						}
						lastGradInfo = gradInfo;
					}

					if (lastGradInfo.startPoint < 1) {
						gc.setForeground(lastGradInfo.color);
						gc.setBackground(lastGradInfo.color);

						int y = (int) (bounds.height * lastGradInfo.startPoint);
						int height = bounds.height - y;
						gc.fillGradientRectangle(0, y, 5, height, true);
					}
				} finally {
					gc.dispose();
				}
				if (painter == null) {
					// Use TILE_BOTH because a gradient should never set the size of the control
					// Rather, the gradient image is made based on the control size.  If
					// we used TILE_X, SWTBGImagePainter would force the height to the
					// current image, thus preventing any auto-resizing
					painter = new SWTBGImagePainter(control, null, null,
							bgImage, SWTSkinUtils.TILE_BOTH);
				} else {
					painter.setImage(null, null, bgImage);
				}
			}
		};

		this.control = _control;
		control.setData("ConfigID", sConfigID);
		control.setData("SkinObject", this);

		SWTSkinUtils.addMouseImageChangeListeners(control);
		switchSuffix(null, 0, false);

		// setvisible is one time only
		if (!properties.getBooleanValue(sConfigID + ".visible", true)) {
			setVisible(false);
		}

		final Listener lShowHide = new Listener() {
			public void handleEvent(final Event event) {
				final boolean toBeVisible = event.type == SWT.Show;
				if (SWTSkin.DEBUG_VISIBILITIES) {
					System.out.println(">>swt.show/hide " + ((event.widget).getData("SkinObject")) + ";tobe/is=" + toBeVisible + "/" + isVisible + " via " + Debug.getCompressedStackTrace());
					//System.out.println(SWTSkinObjectBasic.this + "> swt.show/hide " + ((event.widget).getData("SkinObject")) + ";" + ((Control)event.widget).isVisible() + ";" + Debug.getCompressedStackTrace());
				}

				// wait until show or hide event is processed to guarantee
				// isVisible will be correct for listener triggers
				Utils.execSWTThreadLater(0, new AERunnable() {
					public void runSupport() {
						if (SWTSkin.DEBUG_VISIBILITIES) {
							//System.out.println(">>swt.show/hide " + ((event.widget).getData("SkinObject")) + ";" + ((Control) event.widget).isVisible());
						}
						if (control == null || control.isDisposed()) {
							setIsVisible(false, true);
							return;
						}

						if (toBeVisible == control.isVisible() && (isVisible == 1) == toBeVisible) {
							return;
						}

						if (event.widget == control) {
							setIsVisible(toBeVisible, true);
							return;
						}

						if (!toBeVisible || control.isVisible()) {
							setIsVisible(toBeVisible, true);
							return;
						}
						setIsVisible(control.isVisible(), true);
					}
				});
			}
		};

		control.addListener(SWT.Show, lShowHide);
		control.addListener(SWT.Hide, lShowHide);
		final Shell shell = control.getShell();
		shell.addListener(SWT.Show, lShowHide);
		shell.addListener(SWT.Hide, lShowHide);

		control.addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent e) {
				disposed = true;
				shell.removeListener(SWT.Show, lShowHide);
				shell.removeListener(SWT.Hide, lShowHide);

				skin.removeSkinObject(SWTSkinObjectBasic.this);
			}
		});

		control.addListener(SWT.MouseHover, new Listener() {
			public void handleEvent(Event event) {
				String id = getTooltipID(true);
				if (id == null) {
					control.setToolTipText(null);
				} else if (id.startsWith("!") && id.endsWith("!")) {
					control.setToolTipText(id.substring(1, id.length() - 1));
				} else {
					control.setToolTipText(MessageText.getString(id, (String) null));
				}
			}
		});

		if (parent instanceof SWTSkinObjectContainer) {
			((SWTSkinObjectContainer)parent).childAdded(this);
		}
	}

	/**
	 * @param visible
	 *
	 * @since 3.0.4.3
	 */
	protected boolean setIsVisible(boolean visible, boolean walkup) {
		if ((visible ? 1 : 0) == isVisible) {
			return false;
		}
		if (SWTSkin.DEBUG_VISIBILITIES) {
			System.out.println(this + " SET IS VISIBLE " + visible + " via " + Debug.getCompressedStackTrace(9));
		}
		isVisible = visible ? 1 : 0;
		switchSuffix(null, 0, false);
		triggerListeners(visible ? SWTSkinObjectListener.EVENT_SHOW
				: SWTSkinObjectListener.EVENT_HIDE);

		// only walkup when visible.. yes?
		if (walkup && visible) {
			SWTSkinObject p = parent;

  		while (p instanceof SWTSkinObjectBasic) {
  			((SWTSkinObjectBasic) p).setIsVisible(visible, false);
  			p = ((SWTSkinObjectBasic) p).getParent();
  		}
		}
		return true;
	}

	public Control getControl() {
		return control;
	}

	public String getType() {
		return type;
	}

	public String getConfigID() {
		return sConfigID;
	}

	public String getSkinObjectID() {
		return sID;
	}

	// @see com.aelitis.azureus.ui.swt.skin.SWTSkinObject#getParent()
	public SWTSkinObject getParent() {
		return parent;
	}

	public void setBackground(String sConfigID, String sSuffix) {
		Image imageBG;
		Image imageBGLeft;
		Image imageBGRight;

		if (sConfigID == null) {
			return;
		}

		ImageLoader imageLoader = skin.getImageLoader(properties);

		String id = null;
		String idLeft = null;
		String idRight = null;

		String s = properties.getStringValue(sConfigID + sSuffix, (String) null);
		if (s != null && s.length() > 0) {
			Image[] images = imageLoader.getImages(sConfigID + sSuffix);
			try {
				if (images.length == 1 && ImageLoader.isRealImage(images[0])) {
					id = sConfigID + sSuffix;
					idLeft = id + "-left";
					idRight = id + "-right";
				} else if (images.length == 3 && ImageLoader.isRealImage(images[2])) {
					id = sConfigID + sSuffix;
					idLeft = id;
					idRight = id;
				} else if (images.length == 2 && ImageLoader.isRealImage(images[1])) {
					id = sConfigID + sSuffix;
					idLeft = id;
					idRight = id + "-right";
				} else {
					id = sConfigID + sSuffix;
					//if (sSuffix.length() > 0) {
					//	setBackground(sConfigID, "");
					//}
					return;
				}
			} finally {
				imageLoader.releaseImage(sConfigID + sSuffix);
			}
		} else {
			if (s != null && painter != null) {
				painter.dispose();
				painter = null;
			}
			if (s == null) {
				//if (sSuffix.length() > 0) {
				//	setBackground(sConfigID, "");
				//}
			}
			return;
		}

		if (painter == null) {
			//control.setBackgroundImage doesn't handle transparency!
			//control.setBackgroundImage(image);

			// Workaround: create our own image with shell's background
			// for "transparent" area.  Doesn't allow control's image to show
			// through.  To do that, we'd have to walk up the tree until we
			// found a composite with an image
			//control.setBackgroundMode(SWT.INHERIT_NONE);
			//control.setBackgroundImage(imageBG);

			String sTileMode = properties.getStringValue(sConfigID + ".drawmode");
			int tileMode = SWTSkinUtils.getTileMode(sTileMode);
//			painter = new SWTBGImagePainter(control, imageBGLeft, imageBGRight,
//					imageBG, tileMode);
			painter = new SWTBGImagePainter(control, imageLoader, idLeft, idRight,
					id, tileMode);
		} else {
			//System.out.println("setImage " + sConfigID + "  " + sSuffix);
			painter.setImage(imageLoader, idLeft, idRight, id);
		}

		// XXX Is this needed?  It causes flicker and slows things down.
		//     Maybe a redraw instead (if anything at all)?
		//control.update();
	}

	// @see java.lang.Object#toString()
	public String toString() {
		String s = "SWTSkinObjectBasic {" + sID;

		if (!sID.equals(sConfigID)) {
			s += "/" + sConfigID;
		}

		if (sViewID != null) {
			s += "/v=" + sViewID;
		}

		s += ", " + type + "; parent="
				+ ((parent == null) ? null : parent.getSkinObjectID() + "}");

		return s;
	}

	// @see com.aelitis.azureus.ui.swt.skin.SWTSkinObject#getSkin()
	public SWTSkin getSkin() {
		return skin;
	}

	public int hashCode() {
		Debug.out("this should be implemented");

		return ( super.hashCode());
	}

	// @see java.lang.Object#equals(java.lang.Object)
	public boolean equals(Object obj) {
		if (obj instanceof SWTSkinObject) {
			SWTSkinObject skinObject = (SWTSkinObject) obj;
			boolean bEquals = skinObject.getSkinObjectID().equals(sID);
			if (parent != null) {
				return bEquals && parent.equals(skinObject.getParent());
			}
			return bEquals;
		}

		return super.equals(obj);
	}

	// @see com.aelitis.azureus.ui.swt.skin.SWTSkinObject#setVisible(boolean)
	public void setVisible(final boolean visible) {
		if (!layoutComplete) {
			firstVisibility = visible;
			setIsVisible(visible, true);
			return;
		}
		if (SWTSkin.DEBUG_VISIBILITIES) {
			System.out.println(this + " SET VISIBLE(" + visible + ") via "
					+ Debug.getCompressedStackTrace());
		}
		Utils.execSWTThread(new AERunnable() {
			public void runSupport() {
				if (control != null && !control.isDisposed()) {
					boolean changed = visible != control.isVisible()
							|| (visible ? 1 : 0) != isVisible;

					Object ld = control.getLayoutData();
					if (ld instanceof FormData) {
						FormData fd = (FormData) ld;
						if (!visible) {
							if (fd.width > 0 && fd.height > 0) {
								control.setData("oldSize", new Point(fd.width, fd.height));
								changed = true;
							}
							fd.width = 0;
							fd.height = 0;
						} else {
							Object oldSize = control.getData("oldSize");
							Point oldSizePoint = (oldSize instanceof Point) ? (Point) oldSize
									: new Point(SWT.DEFAULT, SWT.DEFAULT);
							if (fd.width <= 0) {
								changed = true;
								fd.width = oldSizePoint.x;
							}
							if (fd.height <= 0) {
								changed = true;
								fd.height = oldSizePoint.y;
							}
						}
						if (changed) {
  						control.setLayoutData(fd);
  						control.getParent().layout(true, true);
						}
					} else if (ld == null && !visible) {
						FormData fd = new FormData();
						fd.width = 0;
						fd.height = 0;
						control.setLayoutData(fd);
					}
					if (changed || true) { // For some reason this is required even when !changed (on Windows)
						control.setVisible(visible);
					}
					// still need to call setIsVisible to walk up/down
					setIsVisible(visible, true);
				}
			}
		});
	}

	// @see com.aelitis.azureus.ui.swt.skin.SWTSkinObject#setDefaultVisibility()
	public void setDefaultVisibility() {
		if (sConfigID == null) {
			return;
		}

		setVisible(getDefaultVisibility());
	}

	public boolean getDefaultVisibility() {
		return firstVisibility;
	}

	public boolean isVisible() {
		if (control == null || control.isDisposed()) {
			return false;
		}
		if (!layoutComplete) {
			return firstVisibility;
		}
		return isVisible == -1 ? firstVisibility : isVisible == 1;
	}

	/**
	 * Switch the suffix using the default of <code>1</code> for level and <code>false</code> for walkUp
	 */
	public String switchSuffix(String suffix) {
		return switchSuffix(suffix, 1, false);
	}

	public final String switchSuffix(String suffix, int level, boolean walkUp) {
		return switchSuffix(suffix, level, walkUp, true);
	}

	public String switchSuffix(String newSuffixEntry, int level, boolean walkUp, boolean walkDown) {
		if (walkUp) {
			SWTSkinObject parentSkinObject = parent;
			SWTSkinObject skinObject = this;

			// Move up the tree until propogation stops
			while ((parentSkinObject instanceof SWTSkinObjectContainer)
					&& ((SWTSkinObjectContainer) parentSkinObject).getPropogation()) {
				skinObject = parentSkinObject;
				parentSkinObject = parentSkinObject.getParent();
			}

			if (skinObject != this) {
				//System.out.println(sConfigID + suffix + "; walkup");

				skinObject.switchSuffix(newSuffixEntry, level, false);
				return null;
			}
		}
		String old = getSuffix();

		if (level > 0) {
  		//System.out.println(SystemTime.getCurrentTime() + ": " + this + suffix + "; switchy");
  		if (suffixes == null) {
  			old = null;
  			suffixes = new String[level];
  		} else if (suffixes.length < level) {
  			String[] newSuffixes = new String[level];
  			System.arraycopy(suffixes, 0, newSuffixes, 0, suffixes.length);
  			suffixes = newSuffixes;
  		}
  		suffixes[level - 1] = newSuffixEntry;
		}

		String fullSuffix = getSuffix();

		if (newSuffixEntry != null) {
  		if (sConfigID == null || control == null || control.isDisposed()
  				|| !isVisible() || (newSuffixEntry != null && fullSuffix.equals(old))) {
  			return fullSuffix;
  		}
		}

		final String sSuffix = fullSuffix;

		Utils.execSWTThread(new AERunnable() {

			public void runSupport() {
				if (control == null || control.isDisposed()) {
					return;
				}

				boolean needPaintHook = false;

				if (properties.hasKey(sConfigID + ".color" + sSuffix)) {
					control.removeListener(SWT.Resize, resizeGradientBGListener);

					Color color = properties.getColor(sConfigID + ".color" + sSuffix);
					bgColor = color;
					String colorStyle = properties.getStringValue(sConfigID
							+ ".color.style" + sSuffix);
					if (colorStyle != null) {
						String[] split = Constants.PAT_SPLIT_COMMA.split(colorStyle);

						if (split.length > 2) {
							try {
  							colorFillParams = new int[] {
  								Integer.parseInt(split[1]),
  								Integer.parseInt(split[2])
  							};
							} catch (NumberFormatException e) {
								//ignore
							}
						}

						if (split[0].equals("rounded")) {
							colorFillType = BORDER_ROUNDED;
							needPaintHook = true;
						} else if (split[0].equals("rounded-fill")) {
							colorFillType = BORDER_ROUNDED_FILL;
							needPaintHook = true;
						} else if (split[0].equals("gradient")) {
							colorFillType = BORDER_GRADIENT;

							Device device = Display.getDefault();
							for (int i = 1; i < split.length; i += 2) {
								Color colorStop = ColorCache.getSchemedColor(device, split[i]);
								double posStop = 1;
								if (i != split.length - 1) {
									try {
										posStop = Double.parseDouble(split[i+1]);
									} catch (Exception ignore) {
									}
								}
								listGradients.add(new GradientInfo(colorStop, posStop));
							}

							control.addListener(SWT.Resize, resizeGradientBGListener);
							resizeGradientBGListener.handleEvent(null);
						}

						control.redraw();
						control.setBackground(null);
					} else {
						control.setBackground(bgColor);
					}
				}

				Color fg = getColor_SuffixWalkback(sConfigID + ".fgcolor");
				control.setForeground(fg);

				// Color,[width]
				String sBorderStyle = properties.getStringValue(sConfigID + ".border"
						+ sSuffix);
				colorBorder = null;
				colorBorderParams = null;
				if (sBorderStyle != null) {
					String[] split = Constants.PAT_SPLIT_COMMA.split(sBorderStyle);
					colorBorder = ColorCache.getSchemedColor(control.getDisplay(), split[0]);
					needPaintHook |= colorBorder != null;

					if (split.length > 2) {
						colorBorderParams = new int[] {
							Integer.parseInt(split[1]),
							Integer.parseInt(split[2])
						};
					}
				}

				setBackground(sConfigID + ".background", sSuffix);

				String sCursor = properties.getStringValue(sConfigID + ".cursor");
				if (sCursor != null && sCursor.length() > 0) {
					if (sCursor.equalsIgnoreCase("hand")) {
						Listener handCursorListener = skin.getHandCursorListener(control.getDisplay());
						control.removeListener(SWT.MouseEnter, handCursorListener);
						control.removeListener(SWT.MouseExit, handCursorListener);

						control.addListener(SWT.MouseEnter, handCursorListener);
						control.addListener(SWT.MouseExit, handCursorListener);
					}
				}

				if (!customTooltipID) {
  				String newToolTipID = properties.getReferenceID(sConfigID + ".tooltip"
  						+ sSuffix);
  				if (newToolTipID == null && sSuffix.length() > 0) {
  					newToolTipID = properties.getReferenceID(sConfigID + ".tooltip");
  				}
  				tooltipID = newToolTipID;
				}

				if (!alwaysHookPaintListener && needPaintHook != paintListenerHooked) {
					if (needPaintHook) {
						control.addPaintListener(SWTSkinObjectBasic.this);
					} else {
						control.removePaintListener(SWTSkinObjectBasic.this);
					}
					paintListenerHooked = needPaintHook;
				}

			}

		});
		return fullSuffix;
	}

	public String getSuffix() {
		String suffix = "";
		if (suffixes == null) {
			return suffix;
		}
		for (int i = 0; i < suffixes.length; i++) {
			if (suffixes[i] != null) {
				suffix += suffixes[i];
			}
		}
		if (suffix.contains("-down-over")) {
			return suffix.replaceAll("-down-over", "-down");
		}
		return suffix;
	}

	/**
	 * @return the properties
	 */
	public SWTSkinProperties getProperties() {
		return properties;
	}

	public void setProperties(SWTSkinProperties skinProperties) {
		this.properties = skinProperties;
	}

	/* (non-Javadoc)
	 * @see com.aelitis.azureus.ui.swt.skin.SWTSkinObject#addListener(com.aelitis.azureus.ui.swt.skin.SWTSkinObjectListener)
	 */
	public void addListener(final SWTSkinObjectListener listener) {
		int visibleStateAtAdd = isVisible;
		listeners_mon.enter();
		try {
			if (listeners.contains(listener)) {
				System.err.println("Already contains listener " + Debug.getCompressedStackTrace());
				return;
			}
			listeners.add(listener);
		} finally {
			listeners_mon.exit();
		}

		if (initialized) {
			listener.eventOccured(this, SWTSkinObjectListener.EVENT_CREATED, null);
		}

		if (datasource != null) {
  		listener.eventOccured(SWTSkinObjectBasic.this,
  				SWTSkinObjectListener.EVENT_DATASOURCE_CHANGED, datasource);
		}

		if (visibleStateAtAdd == 1 && initialized) {
			Utils.execSWTThread(new AERunnable() {
				public void runSupport() {
					if (isVisible != 1) {
						return;
					}
					listener.eventOccured(SWTSkinObjectBasic.this,
							SWTSkinObjectListener.EVENT_SHOW, null);
				}
			});
		}
	}

	/* (non-Javadoc)
	 * @see com.aelitis.azureus.ui.swt.skin.SWTSkinObject#removeListener(com.aelitis.azureus.ui.swt.skin.SWTSkinObjectListener)
	 */
	public void removeListener(SWTSkinObjectListener listener) {
		listeners_mon.enter();
		try {
			listeners.remove(listener);
		} finally {
			listeners_mon.exit();
		}
	}

	public SWTSkinObjectListener[] getListeners() {
		return listeners.toArray(new SWTSkinObjectListener[0]);
	}

	public void triggerListeners(int eventType) {
		triggerListeners(eventType, null);
	}

	public void triggerListeners(final int eventType, final Object params) {
		if (SWTSkin.DEBUG_VISIBILITIES) {
			if (eventType == SWTSkinObjectListener.EVENT_SHOW) {
				System.out.println("Show " + this + " via " + Debug.getCompressedStackTrace());
			}
		}
		// delay show and hide events while not initialized
		if (eventType == SWTSkinObjectListener.EVENT_SHOW
				|| eventType == SWTSkinObjectListener.EVENT_HIDE) {
			if (!initialized) {
				//System.out.println("NOT INITIALIZED! " + SWTSkinObjectBasic.this + ";;;" + Debug.getCompressedStackTrace());
				return;
			}

			if (eventType == SWTSkinObjectListener.EVENT_SHOW && !isVisible()) {
				if (SWTSkin.DEBUG_VISIBILITIES) {
					System.out.println("Warning: Show Event when not visible " + this + " via " + Debug.getCompressedStackTrace());
				}
				return;
			} else if (eventType == SWTSkinObjectListener.EVENT_HIDE && isVisible()) {
				if (SWTSkin.DEBUG_VISIBILITIES) {
					System.out.println("Warning: Hide Event when visible " + this + " via " + Debug.getCompressedStackTrace());
				}
				return;
			}
		} else if (eventType == SWTSkinObjectListener.EVENT_CREATED) {
			//System.out.println("INITIALIZED! " + SWTSkinObjectBasic.this + ";;;" + Debug.getCompressedStackTrace());
			initialized = true;
		} else if (eventType == SWTSkinObjectListener.EVENT_DATASOURCE_CHANGED) {
			datasource = params;
		} else if (eventType == SWTSkinObjectListener.EVENT_DESTROY && isVisible == 1) {
			triggerListenersRaw(SWTSkinObjectListener.EVENT_HIDE, null);
		}

		triggerListenersRaw(eventType, params);

		if (eventType == SWTSkinObjectListener.EVENT_CREATED && isVisible >= 0) {
			triggerListeners(isVisible() ? SWTSkinObjectListener.EVENT_SHOW
					: SWTSkinObjectListener.EVENT_HIDE);
		}

		if (eventType == SWTSkinObjectListener.EVENT_SHOW && skinView == null) {
			String initClass = properties.getStringValue(sConfigID + ".onshow.skinviewclass");
			if (initClass != null) {
				try {
					String[] initClassItems = Constants.PAT_SPLIT_COMMA.split(initClass);
					ClassLoader claLoader = this.getClass().getClassLoader();
					if (initClassItems.length > 1) {
						try {
  						PluginInterface pi = PluginInitializer.getDefaultInterface().getPluginManager().getPluginInterfaceByID(initClassItems[1]);
  						if (pi != null) {
  							claLoader = pi.getPluginClassLoader();
  						}
						} catch (Exception e) {
							Debug.out(e);
						}
					}
					Class<SkinView> cla = (Class<SkinView>) Class.forName(initClassItems[0], true, claLoader);

					setSkinView(cla.newInstance());
					skinView.setMainSkinObject(this);

					// this will fire created and show for us
					addListener(skinView);
				} catch (Throwable e) {
					Debug.out(e);
				}
			}
		}
	}

	private void triggerListenersRaw(int eventType, Object params) {
		// process listeners added locally
		SWTSkinObjectListener[] listenersArray = getListeners();
		if (listenersArray.length > 0) {
			// don't use iterator as triggering code may try to remove itself
			for (SWTSkinObjectListener l : listenersArray) {
				try {
					l.eventOccured(this, eventType, params);
				} catch (Exception e) {
					Debug.out("Skin Event " + SWTSkinObjectListener.NAMES[eventType]
							+ " caused an error for listener added locally", e);
				}
			}
		}

		// process listeners added to skin
		SWTSkinObjectListener[] listeners = skin.getSkinObjectListeners(sViewID);
		if (listeners.length > 0) {
  		for (int i = 0; i < listeners.length; i++) {
  			try {
  				SWTSkinObjectListener l = listeners[i];
  				l.eventOccured(this, eventType, params);
  			} catch (Exception e) {
  				Debug.out("Skin Event " + SWTSkinObjectListener.NAMES[eventType]
  						+ " caused an error for listener added to skin", e);
  			}
  		}
		}
	}

	protected void setViewID(String viewID) {
		sViewID = viewID;
	}

	public String getViewID() {
		return sViewID;
	}

	// @see com.aelitis.azureus.ui.swt.skin.SWTSkinObject#dispose()
	public void dispose() {
		if (disposed) {
			return;
		}
		Utils.disposeSWTObjects(new Object[] {
			control
		});

		if (skinView != null) {
			removeListener(skinView);

			if (skinView instanceof UIUpdatable) {
				UIUpdatable updateable = (UIUpdatable) skinView;
				try {
					UIFunctionsManager.getUIFunctions().getUIUpdater().removeUpdater(
							updateable);
				} catch (Exception e) {
					Debug.out(e);
				}
			}
		}
	}

	public boolean isDisposed() {
		return disposed;
	}

	// @see com.aelitis.azureus.ui.swt.skin.SWTSkinObject#setTooltipID(java.lang.String)
	public void setTooltipID(final String id) {
		if (isDisposed()) {
			return;
		}
		if (StringCompareUtils.equals(id, tooltipID)) {
			return;
		}

		tooltipID = id;
		customTooltipID = true;
	}

	// @see com.aelitis.azureus.ui.swt.skin.SWTSkinObject#getTooltipID(boolean)
	public String getTooltipID(boolean walkup) {
		if (tooltipID != null || !walkup) {
			return tooltipID;
		}
		if (parent != null) {
			return parent.getTooltipID(true);
		}
		return null;
	}

	public void paintControl(GC gc) {
	}

	// @see org.eclipse.swt.events.PaintListener#paintControl(org.eclipse.swt.events.PaintEvent)
	public final void paintControl(PaintEvent e) {
		if (bgColor != null) {
			e.gc.setBackground(bgColor);
		}

		paintControl(e.gc);

		try {
			e.gc.setAdvanced(true);
			e.gc.setAntialias(SWT.ON);
		} catch (Exception ex) {
		}

		if (colorFillType > 0) {

			Rectangle bounds = (control instanceof Composite)
					? ((Composite) control).getClientArea() : control.getBounds();
			if (colorFillParams != null) {
  			if (colorFillType == BORDER_ROUNDED_FILL) {
					e.gc.fillRoundRectangle(0, 0, bounds.width - 1, bounds.height - 1,
							colorFillParams[0], colorFillParams[1]);
					e.gc.drawRoundRectangle(0, 0, bounds.width - 1, bounds.height - 1,
							colorFillParams[0], colorFillParams[1]);
  			} else if (colorFillType == BORDER_ROUNDED) {
  				Color oldFG = e.gc.getForeground();
  				e.gc.setForeground(bgColor);
  				e.gc.drawRoundRectangle(0, 0, bounds.width - 1, bounds.height - 1, colorFillParams[0],
  						colorFillParams[1]);
  				e.gc.setForeground(oldFG);
  			}
			}
//			if (colorFillType == BORDER_GRADIENT) {
//				Color oldFG = e.gc.getForeground();
//				e.gc.setForeground(bgColor2);
//				e.gc.fillGradientRectangle(0, 0, bounds.width - 1, bounds.height - 1, true);
//				e.gc.setForeground(oldFG);
//			}
		}

		if (colorBorder != null) {
			e.gc.setForeground(colorBorder);
			Rectangle bounds = (control instanceof Composite)
					? ((Composite) control).getClientArea() : control.getBounds();
			bounds.width -= 1;
			bounds.height -= 1;
			if (colorBorderParams == null) {
				e.gc.drawRectangle(bounds);
			} else {
				e.gc.drawRoundRectangle(bounds.x, bounds.y, bounds.width,
						bounds.height, colorBorderParams[0], colorBorderParams[1]);
			}
		}
	}

	public boolean isAlwaysHookPaintListener() {
		return alwaysHookPaintListener;
	}

	public void setAlwaysHookPaintListener(boolean alwaysHookPaintListener) {
		this.alwaysHookPaintListener = alwaysHookPaintListener;
		if (alwaysHookPaintListener && !paintListenerHooked) {
			control.addPaintListener(SWTSkinObjectBasic.this);
			paintListenerHooked = true;
		}
	}

	// @see com.aelitis.azureus.ui.swt.skin.SWTSkinObject#getData(java.lang.String)
	public Object getData(String id) {
		return mapData.get(id);
	}

	// @see com.aelitis.azureus.ui.swt.skin.SWTSkinObject#setData(java.lang.String, java.lang.Object)
	public void setData(String id, Object data) {
		if (mapData == Collections.EMPTY_MAP) {
			mapData = new HashMap(1);
		}
		mapData.put(id, data);
	}

	// @see org.gudy.azureus2.ui.swt.debug.ObfusticateImage#obfusticatedImage(org.eclipse.swt.graphics.Image, org.eclipse.swt.graphics.Point)
	public Image obfusticatedImage(Image image) {
		if (!isVisible()) {
			return image;
		}
		Point ourOfs = Utils.getLocationRelativeToShell(control);
		if (obfusticatedImageGenerator == null) {
			if (skinView instanceof ObfusticateImage) {
				return ((ObfusticateImage) skinView).obfusticatedImage(image);
			}
			return image;
		}
		return obfusticatedImageGenerator.obfusticatedImage(image);
	}

	public void setObfusticatedImageGenerator(ObfusticateImage obfusticatedImageGenerator) {
		this.obfusticatedImageGenerator = obfusticatedImageGenerator;
	}

	/**
	 * @param debug the debug to set
	 */
	public void setDebug(boolean debug) {
		this.debug = debug;
	}

	/**
	 * @return the debug
	 */
	public boolean isDebug() {
		return debug;
	}

	// @see com.aelitis.azureus.ui.swt.skin.SWTSkinObject#relayout()
	public void relayout() {
		if (!disposed) {
			Utils.execSWTThread(new AERunnable() {
				public void runSupport() {
					if (control.isDisposed()) {
						return;
					}
					control.getShell().layout(new Control[] {
						control
					});
				}
			});
		}
	}

	public void layoutComplete() {
		if (!layoutComplete) {
			layoutComplete = true;
			if (control != null && !control.isDisposed()) {
				control.setVisible(firstVisibility);
			}
		}
	}

	static class GradientInfo {
		public Color color;
		public double startPoint;

		public GradientInfo(Color c, double d) {
			color = c;
			startPoint = d;
		}
	}

	private Color getColor_SuffixWalkback(String id) {
		int max = suffixes == null ? 0 : suffixes.length;
		while (max >= 0) {
			String suffix = "";
  		for (int i = 0; i < max; i++) {
  			if (suffixes[i] != null) {
  				suffix += suffixes[i];
  			}
  		}
  		Color color = properties.getColor(id + suffix);
  		if (color != null) {
  			return color;
  		}
  		max--;
		}
		return null;
	}

	public SkinView getSkinView() {
		return skinView;
	}

	public void setSkinView(SkinView skinView) {
		this.skinView = skinView;
	}

}
