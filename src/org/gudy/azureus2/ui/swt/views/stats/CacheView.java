/*
 * Created on Sep 13, 2004
 * Created by Olivier Chalouhi
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
 *
 */
package org.gudy.azureus2.ui.swt.views.stats;


import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.DisplayFormatters;
import org.gudy.azureus2.ui.swt.Messages;
import org.gudy.azureus2.ui.swt.Utils;
import org.gudy.azureus2.ui.swt.components.graphics.SpeedGraphic;
import org.gudy.azureus2.ui.swt.plugins.UISWTView;
import org.gudy.azureus2.ui.swt.plugins.UISWTViewEvent;
import org.gudy.azureus2.ui.swt.pluginsimpl.UISWTViewCoreEventListener;

import com.aelitis.azureus.core.diskmanager.cache.CacheFileManagerFactory;
import com.aelitis.azureus.core.diskmanager.cache.CacheFileManagerStats;

/**
 *
 */
public class CacheView
	implements UISWTViewCoreEventListener
{

  public static final String MSGID_PREFIX = "CacheView";

	CacheFileManagerStats stats;

  Composite panel;

  Label lblInUse,lblSize,lblPercentUsed;
  ProgressBar pbInUse;

  Label lblReadsFromCache,lblNumberReadsFromCache,lblAvgSizeFromCache;
  Label lblReadsFromFile, lblNumberReadsFromFile,lblAvgSizeFromFile;
  Label lblPercentReads;
  ProgressBar pbReads;


  Label lblWritesToCache,lblNumberWritesToCache,lblAvgSizeToCache;
  Label lblWritesToFile, lblNumberWritesToFile,lblAvgSizeToFile;
  Label lblPercentWrites;
  ProgressBar pbWrites;

  Canvas  readsFromFile,readsFromCache,writesToCache,writesToFile;

  SpeedGraphic rffGraph,rfcGraph,wtcGraph,wtfGraph;

	private UISWTView swtView;

  public CacheView() {
    try {
      this.stats = CacheFileManagerFactory.getSingleton().getStats();
      rfcGraph = SpeedGraphic.getInstance();
      wtcGraph = SpeedGraphic.getInstance();
      rffGraph = SpeedGraphic.getInstance();
      wtfGraph = SpeedGraphic.getInstance();
    } catch (Exception e) {
    	Debug.printStackTrace(e);
    }
  }

  private void initialize(Composite composite) {
    panel = new Composite(composite,SWT.NULL);
    panel.setLayout(new GridLayout());

    generateGeneralGroup();
    generateReadsGroup();
    generateWritesGroup();
    generateSpeedGroup();
  }

  /**
   *
   */
  private void generateGeneralGroup() {
    GridData gridData;

    Group gCacheGeneral = new Group(panel,SWT.NULL);
    Messages.setLanguageText(gCacheGeneral,"CacheView.general.title");
    Utils.setLayoutData(gCacheGeneral, new GridData(GridData.FILL_HORIZONTAL));

    GridLayout layoutGeneral = new GridLayout();
    layoutGeneral.numColumns = 4;
    gCacheGeneral.setLayout(layoutGeneral);
    Label lbl;

    lbl = new Label(gCacheGeneral,SWT.NULL);
    gridData = new GridData();
    gridData.widthHint = 100;
    Utils.setLayoutData(lbl, gridData);
    Messages.setLanguageText(lbl,"CacheView.general.inUse");

    lblInUse = new Label(gCacheGeneral,SWT.NULL);
    gridData = new GridData();
    gridData.widthHint = 100;
    Utils.setLayoutData(lblInUse, gridData);

    pbInUse =  new ProgressBar(gCacheGeneral,SWT.HORIZONTAL);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.verticalSpan = 2;
    Utils.setLayoutData(pbInUse, gridData);
    pbInUse.setMinimum(0);
    pbInUse.setMaximum(1000);

    lblPercentUsed = new Label(gCacheGeneral,SWT.NULL);
    gridData = new GridData();
    gridData.verticalSpan = 2;
    gridData.widthHint = 100;
    Utils.setLayoutData(lblPercentUsed, gridData);

    lbl = new Label(gCacheGeneral,SWT.NULL);
    gridData = new GridData();
    gridData.widthHint = 100;
    Utils.setLayoutData(lbl, gridData);
    Messages.setLanguageText(lbl,"CacheView.general.size");

    lblSize = new Label(gCacheGeneral,SWT.NULL);
    gridData = new GridData();
    gridData.widthHint = 100;
    Utils.setLayoutData(lblSize, gridData);
  }

  private void generateReadsGroup() {
    GridData gridData;

    Group gCacheReads = new Group(panel,SWT.NULL);
    Messages.setLanguageText(gCacheReads,"CacheView.reads.title");
    Utils.setLayoutData(gCacheReads, new GridData(GridData.FILL_HORIZONTAL));

    GridLayout layoutGeneral = new GridLayout();
    layoutGeneral.numColumns = 6;
    gCacheReads.setLayout(layoutGeneral);
    Label lbl;

    lbl = new Label(gCacheReads,SWT.NULL);

    lbl = new Label(gCacheReads,SWT.NULL);
    Messages.setLanguageText(lbl,"CacheView.reads.#");

    lbl = new Label(gCacheReads,SWT.NULL);
    Messages.setLanguageText(lbl,"CacheView.reads.amount");

    lbl = new Label(gCacheReads,SWT.NULL);
    Messages.setLanguageText(lbl,"CacheView.reads.avgsize");

    lbl = new Label(gCacheReads,SWT.NULL);
    lbl = new Label(gCacheReads,SWT.NULL);


    lbl = new Label(gCacheReads,SWT.NULL);
    gridData = new GridData();
    gridData.widthHint = 100;
    Utils.setLayoutData(lbl, gridData);
    Messages.setLanguageText(lbl,"CacheView.reads.fromCache");

    lblNumberReadsFromCache = new Label(gCacheReads,SWT.NULL);
    gridData = new GridData();
    gridData.widthHint = 100;
    Utils.setLayoutData(lblNumberReadsFromCache, gridData);

    lblReadsFromCache = new Label(gCacheReads,SWT.NULL);
    gridData = new GridData();
    gridData.widthHint = 100;
    Utils.setLayoutData(lblReadsFromCache, gridData);

    lblAvgSizeFromCache = new Label(gCacheReads,SWT.NULL);
    gridData = new GridData();
    gridData.widthHint = 100;
    Utils.setLayoutData(lblAvgSizeFromCache, gridData);

    pbReads =  new ProgressBar(gCacheReads,SWT.HORIZONTAL);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.verticalSpan = 2;
    Utils.setLayoutData(pbReads, gridData);
    pbReads.setMinimum(0);
    pbReads.setMaximum(1000);

    lblPercentReads = new Label(gCacheReads,SWT.NULL);
    gridData = new GridData();
    gridData.verticalSpan = 2;
    gridData.widthHint = 100;
    Utils.setLayoutData(lblPercentReads, gridData);

    lbl = new Label(gCacheReads,SWT.NULL);
    gridData = new GridData();
    gridData.widthHint = 100;
    Utils.setLayoutData(lbl, gridData);
    Messages.setLanguageText(lbl,"CacheView.reads.fromFile");

    lblNumberReadsFromFile = new Label(gCacheReads,SWT.NULL);
    gridData = new GridData();
    gridData.widthHint = 100;
    Utils.setLayoutData(lblNumberReadsFromFile, gridData);

    lblReadsFromFile = new Label(gCacheReads,SWT.NULL);
    gridData = new GridData();
    gridData.widthHint = 100;
    Utils.setLayoutData(lblReadsFromFile, gridData);

    lblAvgSizeFromFile = new Label(gCacheReads,SWT.NULL);
    gridData = new GridData();
    gridData.widthHint = 100;
    Utils.setLayoutData(lblAvgSizeFromFile, gridData);
  }

  private void generateSpeedGroup() {
    GridData gridData;

    Group gCacheSpeeds = new Group(panel,SWT.NULL);
    Messages.setLanguageText(gCacheSpeeds,"CacheView.speeds.title");
    Utils.setLayoutData(gCacheSpeeds, new GridData(GridData.FILL_BOTH));

    GridLayout layoutGeneral = new GridLayout();
    layoutGeneral.numColumns = 3;
    gCacheSpeeds.setLayout(layoutGeneral);
    Label lbl;

    lbl = new Label(gCacheSpeeds,SWT.NULL);

    lbl = new Label(gCacheSpeeds,SWT.NULL);
    gridData = new GridData(GridData.HORIZONTAL_ALIGN_CENTER);
    Utils.setLayoutData(lbl, gridData);
    Messages.setLanguageText(lbl,"CacheView.speeds.reads");

    lbl = new Label(gCacheSpeeds,SWT.NULL);
    gridData = new GridData(GridData.HORIZONTAL_ALIGN_CENTER);
    Utils.setLayoutData(lbl, gridData);
    Messages.setLanguageText(lbl,"CacheView.speeds.writes");

    lbl = new Label(gCacheSpeeds,SWT.NULL);
    Messages.setLanguageText(lbl,"CacheView.speeds.fromCache");

    readsFromCache = new Canvas(gCacheSpeeds,SWT.NO_BACKGROUND);
    gridData = new GridData(GridData.FILL_BOTH);
    Utils.setLayoutData(readsFromCache, gridData);
    rfcGraph.initialize(readsFromCache);


    writesToCache = new Canvas(gCacheSpeeds,SWT.NO_BACKGROUND);
    gridData = new GridData(GridData.FILL_BOTH);
    Utils.setLayoutData(writesToCache, gridData);
    wtcGraph.initialize(writesToCache);

    lbl = new Label(gCacheSpeeds,SWT.NULL);
    Messages.setLanguageText(lbl,"CacheView.speeds.fromFile");

    readsFromFile = new Canvas(gCacheSpeeds,SWT.NO_BACKGROUND);
    gridData = new GridData(GridData.FILL_BOTH);
    Utils.setLayoutData(readsFromFile, gridData);
    rffGraph.initialize(readsFromFile);

    writesToFile = new Canvas(gCacheSpeeds,SWT.NO_BACKGROUND);
    gridData = new GridData(GridData.FILL_BOTH);
    Utils.setLayoutData(writesToFile, gridData);
    wtfGraph.initialize(writesToFile);
  }

  /* (non-Javadoc)
   * @see org.gudy.azureus2.ui.swt.views.stats.PeriodicViewUpdate#periodicUpdate()
   */
  public void periodicUpdate() {
    rfcGraph.addIntValue((int)stats.getAverageBytesReadFromCache());
    rffGraph.addIntValue((int)stats.getAverageBytesReadFromFile());
    wtcGraph.addIntValue((int)stats.getAverageBytesWrittenToCache());
    wtfGraph.addIntValue((int)stats.getAverageBytesWrittenToFile());
  }

  private void generateWritesGroup() {
    GridData gridData;

    Group gCacheWrites = new Group(panel,SWT.NULL);
    Messages.setLanguageText(gCacheWrites,"CacheView.writes.title");
    Utils.setLayoutData(gCacheWrites, new GridData(GridData.FILL_HORIZONTAL));

    GridLayout layoutGeneral = new GridLayout();
    layoutGeneral.numColumns = 6;
    gCacheWrites.setLayout(layoutGeneral);
    Label lbl;

    lbl = new Label(gCacheWrites,SWT.NULL);

    lbl = new Label(gCacheWrites,SWT.NULL);
    Messages.setLanguageText(lbl,"CacheView.reads.#");

    lbl = new Label(gCacheWrites,SWT.NULL);
    Messages.setLanguageText(lbl,"CacheView.reads.amount");

    lbl = new Label(gCacheWrites,SWT.NULL);
    Messages.setLanguageText(lbl,"CacheView.reads.avgsize");

    lbl = new Label(gCacheWrites,SWT.NULL);
    lbl = new Label(gCacheWrites,SWT.NULL);


    lbl = new Label(gCacheWrites,SWT.NULL);
    gridData = new GridData();
    gridData.widthHint = 100;
    Utils.setLayoutData(lbl, gridData);
    Messages.setLanguageText(lbl,"CacheView.writes.toCache");

    lblNumberWritesToCache = new Label(gCacheWrites,SWT.NULL);
    gridData = new GridData();
    gridData.widthHint = 100;
    Utils.setLayoutData(lblNumberWritesToCache, gridData);

    lblWritesToCache = new Label(gCacheWrites,SWT.NULL);
    gridData = new GridData();
    gridData.widthHint = 100;
    Utils.setLayoutData(lblWritesToCache, gridData);

    lblAvgSizeToCache = new Label(gCacheWrites,SWT.NULL);
    gridData = new GridData();
    gridData.widthHint = 100;
    Utils.setLayoutData(lblAvgSizeToCache, gridData);

    pbWrites =  new ProgressBar(gCacheWrites,SWT.HORIZONTAL);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    gridData.verticalSpan = 2;
    Utils.setLayoutData(pbWrites, gridData);
    pbWrites.setMinimum(0);
    pbWrites.setMaximum(1000);

    lblPercentWrites = new Label(gCacheWrites,SWT.NULL);
    gridData = new GridData();
    gridData.verticalSpan = 2;
    gridData.widthHint = 100;
    Utils.setLayoutData(lblPercentWrites, gridData);

    lbl = new Label(gCacheWrites,SWT.NULL);
    gridData = new GridData();
    gridData.widthHint = 100;
    Utils.setLayoutData(lbl, gridData);
    Messages.setLanguageText(lbl,"CacheView.writes.toFile");

    lblNumberWritesToFile = new Label(gCacheWrites,SWT.NULL);
    gridData = new GridData();
    gridData.widthHint = 100;
    Utils.setLayoutData(lblNumberWritesToFile, gridData);

    lblWritesToFile = new Label(gCacheWrites,SWT.NULL);
    gridData = new GridData();
    gridData.widthHint = 100;
    Utils.setLayoutData(lblWritesToFile, gridData);

    lblAvgSizeToFile = new Label(gCacheWrites,SWT.NULL);
    gridData = new GridData();
    gridData.widthHint = 100;
    Utils.setLayoutData(lblAvgSizeToFile, gridData);
  }

  private void delete() {
    Utils.disposeComposite(panel);
    rfcGraph.dispose();
    rffGraph.dispose();
    wtcGraph.dispose();
    wtfGraph.dispose();
  }

  private Composite getComposite() {
    return panel;
  }

  private void refresh() {
    //General Part
    lblSize.setText(DisplayFormatters.formatByteCountToKiBEtc(stats.getSize()));
    lblInUse.setText(DisplayFormatters.formatByteCountToKiBEtc(stats.getUsedSize()));

    int perThousands = (int) ((1000 * stats.getUsedSize()) / stats.getSize());
    lblPercentUsed.setText(DisplayFormatters.formatPercentFromThousands(perThousands));
    pbInUse.setSelection(perThousands);

    //Reads
    refrehReads();

    //Writes
    refreshWrites();

    //Graphics
    rfcGraph.refresh(false);
    rffGraph.refresh(false);
    wtcGraph.refresh(false);
    wtfGraph.refresh(false);
  }

  /**
   *
   */
  private void refrehReads() {
    int perThousands;
    long readsFromCache = stats.getBytesReadFromCache();
    long readsFromFile = stats.getBytesReadFromFile();
    long nbReadsFromCache = stats.getCacheReadCount();
    long nbReadsFromFile = stats.getFileReadCount();
    lblNumberReadsFromCache.setText("" + nbReadsFromCache);
    lblNumberReadsFromFile.setText("" + nbReadsFromFile);

    if (nbReadsFromCache != 0) {
      long avgReadFromCache = readsFromCache / nbReadsFromCache;
      lblAvgSizeFromCache.setText(DisplayFormatters.formatByteCountToKiBEtc(avgReadFromCache));
    } else {
      lblAvgSizeFromCache.setText("--");
    }

    if (nbReadsFromFile != 0) {
      long avgReadFromFile = readsFromFile / nbReadsFromFile;
      lblAvgSizeFromFile.setText(DisplayFormatters.formatByteCountToKiBEtc(avgReadFromFile));
    } else {
      lblAvgSizeFromFile.setText("--");
    }

    lblReadsFromCache.setText(DisplayFormatters.formatByteCountToKiBEtc(readsFromCache));
    lblReadsFromFile.setText(DisplayFormatters.formatByteCountToKiBEtc(readsFromFile));

    long totalRead = readsFromCache + readsFromFile;
    if (totalRead > 0) {
      perThousands = (int) ((1000l * stats.getBytesReadFromCache()) / totalRead);
      lblPercentReads.setText(DisplayFormatters.formatPercentFromThousands(perThousands) + " " + MessageText.getString("CacheView.reads.hits"));
      pbReads.setSelection(perThousands);
    }
  }

  private void refreshWrites() {
    int perThousands;
    long writesToCache = stats.getBytesWrittenToCache();
    long writesToFile = stats.getBytesWrittenToFile();
    long nbWritesToCache = stats.getCacheWriteCount();
    long nbWritesToFile = stats.getFileWriteCount();
    lblNumberWritesToCache.setText("" + nbWritesToCache);
    lblNumberWritesToFile.setText("" + nbWritesToFile);

    if (nbWritesToCache != 0) {
      long avgReadToCache = writesToCache / nbWritesToCache;
      lblAvgSizeToCache.setText(DisplayFormatters.formatByteCountToKiBEtc(avgReadToCache));
    } else {
      lblAvgSizeToCache.setText("--");
    }

    if (nbWritesToFile != 0) {
      long avgReadToFile = writesToFile / nbWritesToFile;
      lblAvgSizeToFile.setText(DisplayFormatters.formatByteCountToKiBEtc(avgReadToFile));
    } else {
      lblAvgSizeToFile.setText("--");
    }

    lblWritesToCache.setText(DisplayFormatters.formatByteCountToKiBEtc(writesToCache));
    lblWritesToFile.setText(DisplayFormatters.formatByteCountToKiBEtc(writesToFile));

    long totalNbWrites = nbWritesToCache + nbWritesToFile;
    if (totalNbWrites > 0) {
      perThousands = (int) ((1000l * nbWritesToCache) / totalNbWrites);
      lblPercentWrites.setText(DisplayFormatters.formatPercentFromThousands(perThousands) + " " + MessageText.getString("CacheView.writes.hits"));
      pbWrites.setSelection(perThousands);
    }
  }

	public boolean eventOccurred(UISWTViewEvent event) {
    switch (event.getType()) {
      case UISWTViewEvent.TYPE_CREATE:
      	swtView = (UISWTView)event.getData();
      	swtView.setTitle(MessageText.getString(MSGID_PREFIX + ".title.full"));
        break;

      case UISWTViewEvent.TYPE_DESTROY:
        delete();
        break;

      case UISWTViewEvent.TYPE_INITIALIZE:
        initialize((Composite)event.getData());
        break;

      case UISWTViewEvent.TYPE_LANGUAGEUPDATE:
      	Messages.updateLanguageForControl(getComposite());
        break;

      case UISWTViewEvent.TYPE_DATASOURCE_CHANGED:
        break;

      case UISWTViewEvent.TYPE_FOCUSGAINED:
      	break;

      case UISWTViewEvent.TYPE_REFRESH:
        refresh();
        break;

      case StatsView.EVENT_PERIODIC_UPDATE:
      	periodicUpdate();
      	break;
    }

    return true;
  }
}


