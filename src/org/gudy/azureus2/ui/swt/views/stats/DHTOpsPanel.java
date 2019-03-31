/*
 * Created on 22 juin 2005
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
package org.gudy.azureus2.ui.swt.views.stats;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.*;

import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.util.SimpleTimer;
import org.gudy.azureus2.core3.util.SystemTime;
import org.gudy.azureus2.core3.util.TimerEvent;
import org.gudy.azureus2.core3.util.TimerEventPerformer;
import org.gudy.azureus2.core3.util.TimerEventPeriodic;
import org.gudy.azureus2.ui.swt.Utils;

import com.aelitis.azureus.core.dht.DHT;
import com.aelitis.azureus.core.dht.control.DHTControlActivity;
import com.aelitis.azureus.core.dht.control.DHTControlListener;
import com.aelitis.azureus.core.dht.control.DHTControlActivity.ActivityNode;
import com.aelitis.azureus.core.dht.control.DHTControlActivity.ActivityState;
import com.aelitis.azureus.ui.swt.utils.ColorCache;

public class DHTOpsPanel
	implements DHTControlListener
{
	private static final int ALPHA_FOCUS = 255;
	private static final int ALPHA_NOFOCUS = 150;

	private static final int	FADE_OUT	= 10*1000;

	Display display;
	Composite parent;

	Canvas canvas;
	Scale scale;

	private int		minSlots	= 8;

	private boolean	unavailable;

	private boolean mouseLeftDown = false;
	private boolean mouseRightDown = false;
	private int xDown;
	private int yDown;

	private Image img;

	private int alpha = 255;

	private boolean autoAlpha = false;

	private DHT				current_dht;
	private ActivityFilter	filter;

	private Map<DHTControlActivity,ActivityDetail>	activityMap = new HashMap<DHTControlActivity,ActivityDetail>();

	private TimerEventPeriodic timeout_timer;

	private static class Scale {
		int width;
		int height;

		float minX = -1000;
		float maxX = 1000;
		float minY = -1000;
		float maxY = 1000;
		double rotation = 0;

		float saveMinX;
		float saveMaxX;
		float saveMinY;
		float saveMaxY;
		double saveRotation;

		public int getX(float x,float y) {
			return (int) (((x * Math.cos(rotation) + y * Math.sin(rotation))-minX)/(maxX - minX) * width);
		}

		public int getY(float x,float y) {
			return (int) (((y * Math.cos(rotation) - x * Math.sin(rotation))-minY)/(maxY-minY) * height);
		}
	}

	public DHTOpsPanel(Composite parent) {
		this.parent = parent;
		this.display = parent.getDisplay();
		this.canvas = new Canvas(parent,SWT.NO_BACKGROUND);

		this.scale = new Scale();

		canvas.addPaintListener(new PaintListener() {
			public void paintControl(PaintEvent e) {
				if (img != null && !img.isDisposed()) {
					Rectangle bounds = img.getBounds();
					if (bounds.width >= e.width && bounds.height >= e.height) {
						if (alpha != 255) {
							try {
								e.gc.setAlpha(alpha);
							} catch (Exception ex) {
								// Ignore ERROR_NO_GRAPHICS_LIBRARY error or any others
							}
						}
						e.gc.drawImage(img, e.x, e.y, e.width, e.height, e.x, e.y,
								e.width, e.height);
					}
				} else {
					e.gc.setBackground(display.getSystemColor(SWT.COLOR_WIDGET_BACKGROUND));
					e.gc.fillRectangle(e.x, e.y, e.width, e.height);

					e.gc.drawText(
							MessageText.getString( unavailable?(DHTOpsView.MSGID_PREFIX + ".notAvailable"):"v3.MainWindow.view.wait"), 10,
							10, true);
				}
			}
		});

		canvas.addMouseListener(new MouseAdapter() {

			public void mouseDown(MouseEvent event) {
				if (event.button == 1) mouseLeftDown = true;
				if (event.button == 3) mouseRightDown = true;
				xDown = event.x;
				yDown = event.y;
				scale.saveMinX = scale.minX;
				scale.saveMaxX = scale.maxX;
				scale.saveMinY = scale.minY;
				scale.saveMaxY = scale.maxY;
				scale.saveRotation = scale.rotation;
			}

			public void mouseUp(MouseEvent event) {
				if (event.button == 1) mouseLeftDown = false;
				if (event.button == 3) mouseRightDown = false;
				refresh();
			}
		});

		canvas.addListener(SWT.KeyDown, new Listener() {
			public void handleEvent(Event event) {
			}
		});

		canvas.addListener(SWT.MouseWheel, new Listener() {
			public void handleEvent(Event event) {
				// System.out.println(event.count);
				scale.saveMinX = scale.minX;
				scale.saveMaxX = scale.maxX;
				scale.saveMinY = scale.minY;
				scale.saveMaxY = scale.maxY;

				int deltaY = event.count * 5;
				// scaleFactor>1 means zoom in, this happens when
				// deltaY<0 which happens when the mouse is moved up.
				float scaleFactor = 1 - (float) deltaY / 300;
				if (scaleFactor <= 0) scaleFactor = 0.01f;

				// Scalefactor of e.g. 3 makes elements 3 times larger
				float moveFactor = 1 - 1/scaleFactor;

				float centerX = (scale.saveMinX + scale.saveMaxX)/2;
				scale.minX = scale.saveMinX + moveFactor * (centerX - scale.saveMinX);
				scale.maxX = scale.saveMaxX - moveFactor * (scale.saveMaxX - centerX);

				float centerY = (scale.saveMinY + scale.saveMaxY)/2;
				scale.minY = scale.saveMinY + moveFactor * (centerY - scale.saveMinY);
				scale.maxY = scale.saveMaxY - moveFactor * (scale.saveMaxY - centerY);
				refresh();
			}
		});

		canvas.addMouseMoveListener(new MouseMoveListener() {
			private long last_refresh;
			public void mouseMove(MouseEvent event) {
				boolean	do_refresh = false;
				if (mouseLeftDown && (event.stateMask & SWT.MOD4) == 0) {
					int deltaX = event.x - xDown;
					int deltaY = event.y - yDown;
					float width = scale.width;
					float height = scale.height;
					float ratioX = (scale.saveMaxX - scale.saveMinX) / width;
					float ratioY = (scale.saveMaxY - scale.saveMinY) / height;
					float realDeltaX = deltaX * ratioX;
					float realDeltaY  = deltaY * ratioY;
					scale.minX = scale.saveMinX - realDeltaX;
					scale.maxX = scale.saveMaxX - realDeltaX;
					scale.minY = scale.saveMinY - realDeltaY;
					scale.maxY = scale.saveMaxY - realDeltaY;
					do_refresh = true;
				}
				if (mouseRightDown || (mouseLeftDown && (event.stateMask & SWT.MOD4) > 0)) {
					int deltaX = event.x - xDown;
					scale.rotation = scale.saveRotation - (float) deltaX / 100;

					int deltaY = event.y - yDown;
					// scaleFactor>1 means zoom in, this happens when
					// deltaY<0 which happens when the mouse is moved up.
					float scaleFactor = 1 - (float) deltaY / 300;
					if (scaleFactor <= 0) scaleFactor = 0.01f;

					// Scalefactor of e.g. 3 makes elements 3 times larger
					float moveFactor = 1 - 1/scaleFactor;

					float centerX = (scale.saveMinX + scale.saveMaxX)/2;
					scale.minX = scale.saveMinX + moveFactor * (centerX - scale.saveMinX);
					scale.maxX = scale.saveMaxX - moveFactor * (scale.saveMaxX - centerX);

					float centerY = (scale.saveMinY + scale.saveMaxY)/2;
					scale.minY = scale.saveMinY + moveFactor * (centerY - scale.saveMinY);
					scale.maxY = scale.saveMaxY - moveFactor * (scale.saveMaxY - centerY);
					do_refresh = true;
				}

				if (do_refresh) {

					long now = SystemTime.getMonotonousTime();

					if (now - last_refresh >= 250) {

						last_refresh = now;

						refresh();
					}
				}
			}
		});

		canvas.addMouseTrackListener(new MouseTrackListener() {
			public void mouseHover(MouseEvent e) {
			}

			public void mouseExit(MouseEvent e) {
				if (autoAlpha) {
					setAlpha(ALPHA_NOFOCUS);
				}
			}

			public void mouseEnter(MouseEvent e) {
				if (autoAlpha) {
					setAlpha(ALPHA_FOCUS);
				}
			}
		});

		timeout_timer =
			SimpleTimer.addPeriodicEvent(
				"DHTOps:timer",
				30*1000,
				new TimerEventPerformer() {
					public void perform(
						TimerEvent event) {
						if (canvas.isDisposed()) {

							timeout_timer.cancel();

							return;
						}

						synchronized(activityMap) {

							Iterator<ActivityDetail> it = activityMap.values().iterator();

							while (it.hasNext()) {

								ActivityDetail act = it.next();

								if (act.isComplete()) {

									it.remove();
								}
							}
						}
					}
				});
	}

	public void setLayoutData(Object data) {
		canvas.setLayoutData(data);
	}

	public void activityChanged(DHTControlActivity activity, int type) {
		
		if (filter != null && !filter.accept(activity)) {
			return;
		}
		
		//System.out.println( activity.getString() + "/" + type + "/" + activity.getCurrentState().getString());
		if (activity.isQueued()) {
			// ignore these until they become active
			return;
		}
		
		synchronized(activityMap) {
			ActivityDetail details = activityMap.get(activity);
			if (details == null) {
				details = new ActivityDetail(activity);
				activityMap.put(activity, details);
			}
			if (type == DHTControlListener.CT_REMOVED) {
				details.setComplete();
			}
		}
	}

	protected void setUnavailable() {
		Utils.execSWTThread(
			new Runnable() {
				public void run() {
					unavailable = true;

					if (!canvas.isDisposed()) {

						canvas.redraw();
					}
				}
			});
	}

	public void refreshView(DHT dht) {
		if (current_dht != dht) {
			if (current_dht != null) {
				current_dht.getControl().removeListener(this);
			}
			current_dht = dht;
			synchronized (activityMap) {
				activityMap.clear();
			}
			dht.getControl().addListener(this);
		}
		refresh();
	}

	public void setFilter(
		ActivityFilter		f) {
		filter = f;
	}

		/**
		 * @param min things don't work well for < 4...
		 */

	public void setMinimumSlots(
		int		min) {
		minSlots = min;
	}

	public void setScaleAndRotation(
		float		min_x,
		float		max_x,
		float		min_y,
		float		max_y,
		double		rot) {
		scale.minX 		= min_x;
		scale.maxX 		= max_x;
		scale.minY 		= min_y;
		scale.maxY 		= max_y;
		scale.rotation 	= rot;
	}

	public void refresh() {
		if (canvas.isDisposed()) {
			return;
		}
		Rectangle size = canvas.getBounds();
		if (size.width <= 0 || size.height <= 0) {
			return;
		}
		scale.width = size.width;
		scale.height = size.height;
		if (img != null && !img.isDisposed()) {
			img.dispose();
		}
		img = new Image(display,size);
		GC gc = new GC(img);
		gc.setAdvanced(true);
		gc.setAntialias(SWT.ON);
		gc.setTextAntialias(SWT.ON);
		Color white = ColorCache.getColor(display,255,255,255);
		gc.setForeground(white);
		gc.setBackground(white);
		gc.fillRectangle(size);
		List<ActivityDetail>	activities;
		List<ActivityDetail>	toRemove = new ArrayList<ActivityDetail>();
		synchronized(activityMap) {
			activities = new ArrayList<ActivityDetail>(activityMap.values());
		}
		long	now = SystemTime.getMonotonousTime();
		int	maxSlot = Math.max(activities.size(), minSlots);
		for (ActivityDetail details: activities) {
			maxSlot = Math.max(maxSlot, details.getSlot()+1);
			long completeTime = details.getCompleteTime();
			if (completeTime >= 0 && now - completeTime > FADE_OUT) {
				toRemove.add(details);
			}
		}
		boolean[]	slotsInUse = new boolean[maxSlot];
		for (ActivityDetail details: activities) {
			int	slot = details.getSlot();
			if (slot != -1) {
				slotsInUse[slot] = true;
			}
		}
		int pos = 0;
		for (ActivityDetail details: activities) {
			int	slot = details.getSlot();
			if (slot == -1) {
				while (slotsInUse[pos]) {
					pos++;
				}
				details.setSlot(pos++);
			}
		}
	 	int xOrigin = scale.getX(0, 0);
		int yOrigin = scale.getY(0, 0);
		double sliceAngle = 2*Math.PI/maxSlot;
		for (ActivityDetail details: activities) {
			details.draw(gc, xOrigin, yOrigin, sliceAngle);
		}
		gc.setForeground(ColorCache.getColor( gc.getDevice(), 0, 0, 0));
		if (activities.size() == 0) {
			gc.drawText(MessageText.getString( DHTOpsView.MSGID_PREFIX + ".idle" ), xOrigin, yOrigin);
		} else {
			gc.drawLine(xOrigin-5, yOrigin, xOrigin+5, yOrigin);
			gc.drawLine(xOrigin, yOrigin-5, xOrigin, yOrigin+5);
		}
		gc.dispose();
		canvas.redraw();
		if (toRemove.size() > 0) {
			synchronized(activityMap) {
				for (ActivityDetail detail: toRemove) {
					activityMap.remove( detail.getActivity());
				}
			}
		}
	}



	public int getAlpha() {
		return alpha;
	}

	public void setAlpha(int alpha) {
		this.alpha = alpha;
		if (canvas != null && !canvas.isDisposed()) {
			canvas.redraw();
		}
	}

	public void setAutoAlpha(boolean autoAlpha) {
		this.autoAlpha = autoAlpha;
		if (autoAlpha) {
			setAlpha(canvas.getDisplay().getCursorControl() == canvas ? ALPHA_FOCUS : ALPHA_NOFOCUS);
		}
	}

	public void delete() {
		if (img != null && !img.isDisposed()) {
			img.dispose();
		}

		if (current_dht != null) {

			current_dht.getControl().removeListener(this);

			current_dht = null;
		}

		synchronized(activityMap) {

			activityMap.clear();
		}
	}

	private class ActivityDetail {
		
		private DHTControlActivity activity;
		private long completeTime = -1;

		private int slot = -1;

		private int drawCount = 0;
		private String resultStr = "";

		private ActivityDetail(DHTControlActivity _act) {
			activity = _act;
		}

		private DHTControlActivity getActivity() {
			return (activity);
		}

		private void setComplete() {
			completeTime = SystemTime.getMonotonousTime();
		}

		private long getCompleteTime() {
			return (completeTime);
		}

		private boolean isComplete() {
			return (completeTime != -1 && SystemTime.getMonotonousTime() - completeTime > FADE_OUT);
		}

		private int getSlot() {
			return (slot);
		}

		private void setSlot(int _s) {
			slot = _s;
		}

		private void draw(GC gc, int xOrigin, int yOrigin, double sliceAngle) {
			drawCount++;
			setColour(gc);
			double angle = sliceAngle * slot;
			ActivityState stateMaybeNull = activity.getCurrentState();
			if (stateMaybeNull != null) {
				int depth = stateMaybeNull.getDepth();
				int levelDepth = 750 / depth;
				ActivityNode root = stateMaybeNull.getRootNode();
				List<Object[]> levelNodes = new ArrayList<Object[]>();
				float xStart = (float) (50 * Math.sin(angle));
				float yStart = (float) (50 * Math.cos(angle));
				levelNodes.add(new Object[] { root, xStart, yStart });
				int nodeDistance = 50;
				while (true) {
					int nodesAtNextLevel = 0;
					for (Object[] entry : levelNodes) {
						nodesAtNextLevel += ((ActivityNode) entry[0]).getChildren().size();
					}
					if (nodesAtNextLevel == 0) {
						break;
					}
					nodeDistance += levelDepth;
					double nodeSliceAngle = sliceAngle / nodesAtNextLevel;
					double currentAngle = angle;
					if (nodesAtNextLevel > 1) {
						currentAngle = currentAngle - (sliceAngle / 2);
						currentAngle += (sliceAngle - nodeSliceAngle * (nodesAtNextLevel - 1)) / 2;
					}
					List<Object[]> nextLevelNodes = new ArrayList<Object[]>();
					for (Object[] entry : levelNodes) {
						ActivityNode node = (ActivityNode) entry[0];
						float nodeX = (Float) entry[1];
						float nodeY = (Float) entry[2];
						int segStartX = scale.getX(nodeX, nodeY);
						int segStartY = scale.getY(nodeX, nodeY);
						List<ActivityNode> kids = node.getChildren();
						for (ActivityNode kid : kids) {
							float kidX = (float) (nodeDistance * Math.sin(currentAngle));
							float kidY = (float) (nodeDistance * Math.cos(currentAngle));
							nextLevelNodes.add(new Object[] { kid, kidX, kidY });
							currentAngle += nodeSliceAngle;
							int segEndX = scale.getX(kidX, kidY);
							int segEndY = scale.getY(kidX, kidY);
							gc.drawLine(segStartX, segStartY, segEndX, segEndY);
							gc.drawOval(segEndX, segEndY, 1, 1);
						}
					}
					levelNodes = nextLevelNodes;
				}
			}
			float xEnd = (float) (850 * Math.sin(angle));
			float yEnd = (float) (850 * Math.cos(angle));
			int textX = scale.getX(xEnd, yEnd);
			int textY = scale.getY(xEnd, yEnd);
			String desc = activity.getDescription();
			if (completeTime >= 0 && resultStr.length() == 0) {
				if (stateMaybeNull != null) {
					resultStr = (desc.length() == 0 ? "" : ": ") + stateMaybeNull.getResult();
				}
			}
			gc.drawText(desc + resultStr, textX, textY);
			//gc.drawLine(x_origin, y_origin, (int)x_end, (int)y_end);
			gc.setAlpha(255);
		}

		private void setColour(GC gc) {
			if (completeTime != -1 && drawCount > 1) {
				int age = (int) (SystemTime.getMonotonousTime() - completeTime);
				gc.setAlpha(Math.max(0, 200 - (255 * age / FADE_OUT)));
				gc.setForeground(ColorCache.getColor(gc.getDevice(), 0, 0, 0));
			} else {
				gc.setAlpha(255);
				int type = activity.getType();
				if (type == DHTControlActivity.AT_EXTERNAL_GET) {
					gc.setForeground(ColorCache.getColor(gc.getDevice(), 20, 200, 20));
				} else if (type == DHTControlActivity.AT_INTERNAL_GET) {
					gc.setForeground(ColorCache.getColor(gc.getDevice(), 140, 160, 40));
				} else if (type == DHTControlActivity.AT_EXTERNAL_PUT) {
					gc.setForeground(ColorCache.getColor(gc.getDevice(), 20, 20, 220));
				} else {
					gc.setForeground(ColorCache.getColor(gc.getDevice(), 40, 140, 160));
				}
			}
		}
	}

	public interface
	ActivityFilter
	{
		public boolean accept(
			DHTControlActivity		activity);
	}
}
