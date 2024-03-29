/*
 * File    : SpeedGraphic.java
 * Created : 15 d�c. 2003}
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
package org.gudy.azureus2.ui.swt.components.graphics;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.config.ParameterListener;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.DisplayFormatters;
import org.gudy.azureus2.core3.util.SimpleTimer;
import org.gudy.azureus2.core3.util.TimerEvent;
import org.gudy.azureus2.core3.util.TimerEventPerformer;
import org.gudy.azureus2.core3.util.TimerEventPeriodic;
import org.gudy.azureus2.ui.swt.Utils;

/**
 * @author Olivier
 *
 */
public class
MultiPlotGraphic
	extends ScaledGraphic
	implements ParameterListener
{
	private static final int	DEFAULT_ENTRIES	= 2000;

	public static MultiPlotGraphic
	getInstance(
		ValueSource[]	sources,
		ValueFormater 	formatter) {
	    return (new MultiPlotGraphic(new Scale(), sources, formatter));
	}

	private ValueSource[]	value_sources;

	private int internalLoop;
	private int graphicsUpdate;
	private Point oldSize;

	private Image bufferImage;

	private int					nbValues		= 0;
	private int					maxEntries		= DEFAULT_ENTRIES;
	private int[][]				all_values;
	private int					currentPosition	= 0;

	private boolean				update_outstanding = false;

	private TimerEventPeriodic	update_event;

	private MultiPlotGraphic(
		Scale 			scale,
		ValueSource[]	sources,
		ValueFormater 	formater) {
		super(scale,formater);

		value_sources	= sources;

		init(null);

	    COConfigurationManager.addAndFireParameterListeners(
	    	new String[]{ "Graphics Update", "Stats Graph Dividers" }, this);
	}

	private void init(
		int[][]	history) {
		nbValues		= 0;
		maxEntries		= DEFAULT_ENTRIES;
		all_values		= new int[value_sources.length][maxEntries];
		currentPosition	= 0;

		if (history != null) {

			if (history.length != value_sources.length) {

				Debug.out("Incompatible history records, ignored");

			} else {
				if (history.length > 0) {

					int	history_entries = history[0].length;

					int	offset = Math.max(history_entries - maxEntries, 0);

					for ( int i=offset; i<history_entries;i++) {

						for ( int j=0;j<history.length;j++) {

							all_values[j][nbValues] = history[j][i];
						}

						nbValues++;
					}
				}

				currentPosition = nbValues;
			}
		}

		update_outstanding = true;
	}

	public void initialize(
		Canvas 	canvas) {
		initialize(canvas, true);
	}

	public void initialize(
		Canvas 	canvas,
		boolean	is_active) {
	  	super.initialize(canvas);

	  	drawCanvas.addPaintListener(new PaintListener() {
				public void paintControl(PaintEvent e) {
					if (bufferImage != null && !bufferImage.isDisposed()) {
						Rectangle bounds = bufferImage.getBounds();
						if (bounds.width >= e.width && bounds.height >= e.height) {

							e.gc.drawImage(bufferImage, e.x, e.y, e.width, e.height, e.x, e.y, e.width, e.height);
						}
					}
				}
			});

	  	drawCanvas.addListener(SWT.Resize, new Listener() {
				public void handleEvent(Event event) {
					drawChart(true);
				}
			});

	  	setActive(is_active);
	}

	public void setActive(
		boolean	active) {
		if (active) {

			if (update_event != null) {

				return;
			}

		  	update_event = SimpleTimer.addPeriodicEvent(
		  		"MPG:updater",
		  		1000,
		  		new TimerEventPerformer()
		  		{
		  			public void
		  			perform(
		  				TimerEvent event )
		  			{
		  				if (drawCanvas.isDisposed()) {

		  					if (update_event != null) {

		  						update_event.cancel();

		  						update_event = null;
		  					}
		  				} else {
			  				int[]	new_values = new int[value_sources.length];

			  				for ( int i=0;i<new_values.length;i++) {

			  					new_values[i] = value_sources[i].getValue();
			  				}

			  				addIntsValue(new_values);
		  				}
		  			}
		  		});
		} else {

			if (update_event != null) {

				update_event.cancel();

				update_event = null;
			}
		}
	}

	public void reset(
		int[][]		history) {
		init(history);

		Utils.execSWTThread(
			new Runnable() {
				public void run() {
					refresh(true);
				}
			});
	}

	private void addIntsValue(
		int[] new_values) {
	    try {
	    	thisMon.enter();

	    	if (all_values.length < new_values.length) {

	    		int[][]	new_all_values = new int[new_values.length][];

			    System.arraycopy(all_values, 0, new_all_values, 0, all_values.length);

	    		for (int i=all_values.length;i<new_all_values.length; i++) {

	    			new_all_values[i] = new int[maxEntries];
	    		}

	    		all_values = new_all_values;
	    	}

		    for (int i=0;i<new_values.length;i++) {

		        all_values[i][currentPosition] = new_values[i];
	    	}

		    currentPosition++;

		    if (nbValues < maxEntries) {

		      nbValues++;
		    }

		    currentPosition %= maxEntries;


	    } finally {

	    	thisMon.exit();
	    }

	    if (update_outstanding) {

	    	update_outstanding = false;

			Utils.execSWTThread(
				new Runnable() {
					public void run() {
						refresh(true);
					}
				});
	    }
	}


	public void refresh(
		boolean force ) {
		if (drawCanvas == null || drawCanvas.isDisposed()) {

			return;
		}

		Rectangle bounds = drawCanvas.getClientArea();

		if (bounds.height < 30 || bounds.width  < 100 || bounds.width > 10000 || bounds.height > 10000) {

			return;
		}

			// inflate # of values only if necessary

		if (bounds.width > maxEntries) {

			try {
				thisMon.enter();

				while (maxEntries < bounds.width) {

					maxEntries += 1000;
				}

				for (int i=0;i<all_values.length;i++) {

					int[] newValues = new int[maxEntries];

					System.arraycopy(all_values[i], 0, newValues, 0, all_values[i].length);

					all_values[i] = newValues;
				}
			} finally {

				thisMon.exit();
			}
		}

		boolean sizeChanged = (oldSize == null || oldSize.x != bounds.width || oldSize.y != bounds.height);

		oldSize = new Point(bounds.width,bounds.height);

		internalLoop++;

		if (internalLoop > graphicsUpdate) {
			internalLoop = 0;
		}

		if (internalLoop == 0 || sizeChanged || force) {

			drawChart(sizeChanged);

				// to get the scale to redraw correctly we need to force a second drawing as it
				// is the result of the initial draw that sets the scale...

			if (force) {

				drawChart(true);
			}
		}

		drawCanvas.redraw();
		drawCanvas.update();
	}

	protected void drawChart(
		boolean sizeChanged) {
		if (drawCanvas == null || drawCanvas.isDisposed() || !drawCanvas.isVisible()) {

			return;
		}

		GC gcImage = null;

		try {
			thisMon.enter();

			drawScale(sizeChanged);

			if (bufferScale == null || bufferScale.isDisposed()) {

				return;
			}

			Rectangle bounds = drawCanvas.getClientArea();

			if (bounds.isEmpty()) {
				return;
			}

				//If bufferedImage is not null, dispose it

			if (bufferImage != null && !bufferImage.isDisposed()) {

				bufferImage.dispose();
			}

			bufferImage = new Image(drawCanvas.getDisplay(), bounds);

			gcImage = new GC(bufferImage);

			gcImage.drawImage(bufferScale, 0, 0);

			gcImage.setAntialias(SWT.ON);
			gcImage.setTextAntialias(SWT.ON);

			Set<ValueSource>	invisible_sources = new HashSet<ValueSource>();

			for ( int i=0;i<value_sources.length;i++) {

				ValueSource source = value_sources[i];

				if ((source.getStyle() & ValueSource.STYLE_INVISIBLE ) != 0) {

					invisible_sources.add(source);
				}
			}

			int[] oldTargetValues = new int[all_values.length];

			int[] maxs = new int[all_values.length];

			for (int x = 0; x < bounds.width - 71; x++) {

				int position = currentPosition - x - 1;

				if (position < 0) {

					position += maxEntries;

					if (position < 0) {

						position = 0;
					}
				}

				for (int chartIdx = 0; chartIdx < all_values.length; chartIdx++) {

					ValueSource source = value_sources[chartIdx];

					if (invisible_sources.contains( source)) {

						continue;
					}

					int value = all_values[chartIdx][position];

					if (value > maxs[chartIdx]) {

						maxs[chartIdx] = value;
					}
				}
			}

			Set<ValueSource>	bold_sources = new HashSet<ValueSource>();
			Set<ValueSource>	dotted_sources = new HashSet<ValueSource>();

			int max = 0;

			for ( int i=0;i<maxs.length;i++) {

				ValueSource source = value_sources[i];

				if (invisible_sources.contains( source)) {

					continue;
				}

				if ((source.getStyle() & ValueSource.STYLE_BOLD ) != 0) {

					bold_sources.add(source);
				}

				if ((source.getStyle() & ValueSource.STYLE_DOTTED ) != 0) {

					dotted_sources.add(source);
				}

				if (!source.isTrimmable()) {

					max = Math.max(max, maxs[i]);
				}
			}

			int max_primary = max;

			for ( int i=0;i<maxs.length;i++) {

				ValueSource source = value_sources[i];

				if (invisible_sources.contains( source)) {

					continue;
				}

				if (source.isTrimmable()) {

						// trim secondary indicators so we don't loose the more important info

					int m = maxs[i];

					if (max < m) {

						if (m <= 2 * max_primary) {

							max = m;

						} else {

							max = 2 * max_primary;

							break;
						}
					}
				}
			}

			int kInB = DisplayFormatters.getKinB();

			if (max > 5*kInB) {

				max = ((max + kInB - 1)/kInB)*kInB;
			}

			scale.setMax(max);

			int[]	prev_x = new int[value_sources.length];
			int[]	prev_y = new int[value_sources.length];

			int	bounds_width_adj = bounds.width - 71;

			int	cycles = bold_sources.size()==0?2:3;


			for (int x = 0; x < bounds_width_adj; x++) {

				int position = currentPosition - x - 1;

				if (position < 0) {

					position += maxEntries;

					if (position < 0) {

						position = 0;
					}
				}

				int xDraw = bounds_width_adj - x;

				for ( int order=0;order<cycles;order++) {

					for (int chartIdx = 0; chartIdx < all_values.length; chartIdx++) {

						ValueSource source = value_sources[chartIdx];

						if (invisible_sources.contains( source)) {

							continue;
						}

						boolean is_bold = bold_sources.contains(source);

						if (is_bold && order != 2) {

							continue;
						}

						boolean is_dotted = dotted_sources.contains(source);

						if ((source.isTrimmable() == (order==0 ) && order < 2) || ( is_bold && order == 2)) {

							Color line_color = source.getLineColor();

							int targetValue = all_values[chartIdx][position];

							int oldTargetValue = oldTargetValues[chartIdx];

							if (x > 0) {

								int trimmed;

								if (is_dotted) {

									trimmed = 2;

								} else {

									trimmed = 0;

									if (targetValue > max) {
										targetValue = max;
										trimmed++;
									}

									if (oldTargetValue > max) {
										oldTargetValue = max;
										trimmed++;
									}
								}

								boolean force_draw = (trimmed == 2 && position % 4 == 0) || xDraw == 1;

								int h1 = bounds.height - scale.getScaledValue(targetValue) - 2;

								if (x == 1) {

									int h2 = bounds.height - scale.getScaledValue(oldTargetValue) - 2;

									prev_x[chartIdx] = xDraw+1;
									prev_y[chartIdx] = h2;
								}

								if (trimmed < 2 || force_draw) {

									if (h1 != prev_y[chartIdx] || force_draw) {

										gcImage.setAlpha( source.getAlpha());
										gcImage.setLineWidth(trimmed==2?3:is_bold?4:2);
										gcImage.setForeground(line_color);

										gcImage.drawLine(xDraw+1, prev_y[chartIdx], prev_x[chartIdx], prev_y[chartIdx]);
										gcImage.drawLine(xDraw, h1, xDraw + 1, prev_y[chartIdx]);

										prev_x[chartIdx] = xDraw;
										prev_y[chartIdx] = h1;
									}
								} else {

									prev_x[chartIdx] = xDraw;
									prev_y[chartIdx] = h1;
								}
							}

							oldTargetValues[chartIdx] = all_values[chartIdx][position];
						}
					}
				}
			}

			if (nbValues > 0) {

				for ( int order=0;order<cycles;order++) {

					for ( int chartIdx = 0; chartIdx < all_values.length; chartIdx++) {

						ValueSource source = value_sources[chartIdx];

						if (invisible_sources.contains( source)) {

							continue;
						}

						boolean is_bold = bold_sources.contains(source);

						if (is_bold && order != 2) {

							continue;
						}

						if ((source.isTrimmable() == (order==0 ) && order < 2) || ( is_bold && order == 2)) {

							int	style = source.getStyle();

							if ((style & ValueSource.STYLE_HIDE_LABEL ) == 0) {

								int	average_val = computeAverage(chartIdx, currentPosition - 6);

								int average_mod = average_val;

								if (average_mod > max) {

									average_mod = max;
								}

								int height = bounds.height - scale.getScaledValue( average_mod) - 2;

								gcImage.setAlpha(255);

								gcImage.setForeground( source.getLineColor());

								gcImage.drawText(formater.format(average_val), bounds.width - 65, height - 12, false);

								Color bg = gcImage.getBackground();

								if ((style & ValueSource.STYLE_DOWN ) != 0) {

									int	x = bounds.width - 72;
									int y = height - 12;

									gcImage.setBackground( source.getLineColor());

									gcImage.fillPolygon(new int[] { x, y, x+7, y, x+3, y+7 });

									gcImage.setBackground(bg);

								} else  if ((style & ValueSource.STYLE_UP ) != 0) {

									int	x = bounds.width - 72;
									int y = height - 12;

									gcImage.setBackground( source.getLineColor());

									gcImage.fillPolygon(new int[] { x, y+7, x+7, y+7, x+3, y });

									gcImage.setBackground(bg);
								}
							}
						}
					}
				}
			}
		} catch (Throwable e) {

			Debug.out(e);

		} finally {

			if (gcImage != null) {

				gcImage.dispose();
			}

			thisMon.exit();
		}
	}

	private int computeAverage(
		int	line_index,
		int position ) {
		long sum = 0;
		for (int i = -5 ; i < 6 ; i++) {
			int pos = position + i;
			pos %= maxEntries;
			if (pos < 0)
				pos += maxEntries;
			sum += all_values[line_index][pos];
		}
		return (int)(sum / 11);

	}

	public void parameterChanged(
		String parameter ) {
		graphicsUpdate = COConfigurationManager.getIntParameter("Graphics Update");

		boolean update_dividers = COConfigurationManager.getBooleanParameter("Stats Graph Dividers");

		int update_divider_width = update_dividers ? 60 : 0;

		setUpdateDividerWidth(update_divider_width);
	}

	public void dispose() {
		super.dispose();

		if (bufferImage != null && ! bufferImage.isDisposed()) {

			bufferImage.dispose();
		}

		if (update_event != null) {

			update_event.cancel();

			update_event = null;
		}

		COConfigurationManager.removeParameterListener("Graphics Update",this);
		COConfigurationManager.removeParameterListener("Stats Graph Dividers" ,this);
	}
}
