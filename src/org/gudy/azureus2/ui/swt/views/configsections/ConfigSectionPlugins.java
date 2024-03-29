/*
 * File    : ConfigSectionPlguins.java
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

package org.gudy.azureus2.ui.swt.views.configsections;

import java.io.File;
import java.util.*;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.config.ParameterListener;
import org.gudy.azureus2.core3.internat.MessageText;
import org.gudy.azureus2.core3.util.AERunnable;
import org.gudy.azureus2.core3.util.AESemaphore;
import org.gudy.azureus2.core3.util.AEThread2;
import org.gudy.azureus2.core3.util.Debug;
import org.gudy.azureus2.core3.util.FileUtil;
import org.gudy.azureus2.ui.swt.Messages;
import org.gudy.azureus2.ui.swt.Utils;
import org.gudy.azureus2.ui.swt.config.DualChangeSelectionActionPerformer;
import org.gudy.azureus2.ui.swt.config.IAdditionalActionPerformer;
import org.gudy.azureus2.ui.swt.config.plugins.PluginParameter;
import org.gudy.azureus2.ui.swt.mainwindow.Colors;
import org.gudy.azureus2.ui.swt.plugins.UISWTConfigSection;
import org.gudy.azureus2.ui.swt.pluginsinstaller.InstallPluginWizard;
import org.gudy.azureus2.ui.swt.views.ConfigView;

import com.aelitis.azureus.core.AzureusCoreFactory;
import com.aelitis.azureus.ui.UIFunctions;
import com.aelitis.azureus.ui.UIFunctionsManager;
import com.aelitis.azureus.ui.mdi.MultipleDocumentInterface;
import com.aelitis.azureus.ui.swt.imageloader.ImageLoader;

import org.gudy.azureus2.plugins.PluginException;
import org.gudy.azureus2.plugins.PluginInterface;
import org.gudy.azureus2.plugins.installer.PluginInstallationListener;
import org.gudy.azureus2.plugins.ui.config.ConfigSection;
import org.gudy.azureus2.plugins.ui.config.Parameter;
import org.gudy.azureus2.plugins.ui.model.BasicPluginConfigModel;
import org.gudy.azureus2.plugins.ui.model.PluginConfigModel;
import org.gudy.azureus2.pluginsimpl.local.PluginInterfaceImpl;
import org.gudy.azureus2.pluginsimpl.local.ui.config.BooleanParameterImpl;
import org.gudy.azureus2.pluginsimpl.local.ui.config.ParameterRepository;

/**
 * Configuration Section that lists all the plugins and sets up
 * subsections for plugins that used the PluginConfigModel object.
 *
 * Moved from ConfigView
 *
 * @author TuxPaper
 *
 */
public class ConfigSectionPlugins implements UISWTConfigSection, ParameterListener {
	private final static String HEADER_PREFIX = "ConfigView.pluginlist.column.";

	private final static String[] COLUMN_HEADERS = { "loadAtStartup", "type",
			"name", "version", "directory", "unloadable" };

	private final static int[] COLUMN_SIZES = { 180, 70, 250, 100, 100, 50 };

	private final static int[] COLUMN_ALIGNS = { SWT.CENTER, SWT.LEFT, SWT.LEFT,
			SWT.RIGHT, SWT.LEFT, SWT.CENTER};

	private ConfigView configView;

	FilterComparator comparator;

	List pluginIFs;

	private Table table;

	private Image imgRedLed;

	private Image imgGreenLed;

	static class FilterComparator implements Comparator {
		boolean ascending = true;

		static final int FIELD_LOAD = 0;

		static final int FIELD_TYPE = 1;

		static final int FIELD_NAME = 2;

		static final int FIELD_VERSION = 3;

		static final int FIELD_DIRECTORY = 4;

		static final int FIELD_UNLOADABLE = 5;

		int field = FIELD_NAME;

		String sUserPluginDir;

		String sAppPluginDir;

		public FilterComparator() {
			String sep = System.getProperty("file.separator");

			sUserPluginDir = FileUtil.getUserFile("plugins").toString();
			if (!sUserPluginDir.endsWith(sep))
				sUserPluginDir += sep;

			sAppPluginDir = FileUtil.getApplicationFile("plugins").toString();
			if (!sAppPluginDir.endsWith(sep))
				sAppPluginDir += sep;
		}

		public int compare(Object arg0, Object arg1) {
			PluginInterface if0 = (PluginInterface) arg0;
			PluginInterfaceImpl if1 = (PluginInterfaceImpl) arg1;
			int result = 0;

			switch (field) {
				case FIELD_LOAD: {
					boolean b0 = if0.getPluginState().isLoadedAtStartup();
					boolean b1 = if1.getPluginState().isLoadedAtStartup();
					result = (b0 == b1 ? 0 : (b0 ? -1 : 1));

					// Use the plugin ID name to sort by instead.
					if (result == 0) {
						result = if0.getPluginID().compareToIgnoreCase(
								if1.getPluginID());
					}
					break;
				}

				case FIELD_TYPE:
				case FIELD_DIRECTORY: {
					result = getFieldValue(field, if0).compareToIgnoreCase(
							getFieldValue(field, if1));
					break;
				}

				case FIELD_VERSION: { // XXX Not really right..
					String s0 = if0.getPluginVersion();
					String s1 = if1.getPluginVersion();
					if (s0 == null)
						s0 = "";
					if (s1 == null)
						s1 = "";
					result = s0.compareToIgnoreCase(s1);
					break;
				}

				case FIELD_UNLOADABLE: {
					boolean b0 = if0.getPluginState().isUnloadable();
					boolean b1 = if1.getPluginState().isUnloadable();
					result = (b0 == b1 ? 0 : (b0 ? -1 : 1));
					break;
				}
			}

			if (result == 0)
				result = if0.getPluginName().compareToIgnoreCase(if1.getPluginName());

			if (!ascending)
				result *= -1;

			return result;
		}

		public boolean setField(int newField) {
			if (field == newField)
				ascending = !ascending;
			else
				ascending = true;
			field = newField;
			return ascending;
		}

		public String getFieldValue(int iField, PluginInterface pluginIF) {
			switch (iField) {
				case FIELD_LOAD: {
					return pluginIF.getPluginID();
				}

				case FIELD_DIRECTORY: {
					String sDirName = pluginIF.getPluginDirectoryName();

					if (sDirName.length() > sUserPluginDir.length()
							&& sDirName.substring(0, sUserPluginDir.length()).equals(
									sUserPluginDir)) {
						return sDirName.substring(sUserPluginDir.length());

					} else if (sDirName.length() > sAppPluginDir.length()
							&& sDirName.substring(0, sAppPluginDir.length()).equals(
									sAppPluginDir)) {
						return sDirName.substring(sAppPluginDir.length());
					}
					return sDirName;
				}

				case FIELD_NAME: {
					return pluginIF.getPluginName();
				}

				case FIELD_TYPE: {
					String sDirName = pluginIF.getPluginDirectoryName();
					String sKey;

					if (sDirName.length() > sUserPluginDir.length()
							&& sDirName.substring(0, sUserPluginDir.length()).equals(
									sUserPluginDir)) {
						sKey = "perUser";

					} else if (sDirName.length() > sAppPluginDir.length()
							&& sDirName.substring(0, sAppPluginDir.length()).equals(
									sAppPluginDir)) {
						sKey = "shared";
					} else  {
						sKey = "builtIn";
					}

					return MessageText.getString(HEADER_PREFIX + "type." + sKey);
				}

				case FIELD_VERSION: {
					return pluginIF.getPluginVersion();
				}

				case FIELD_UNLOADABLE: {
					return MessageText.getString("Button."
							+ (pluginIF.getPluginState().isUnloadable() ? "yes" : "no")).replaceAll("&", "");
				}
			} // switch

			return "";
		}
	}

	/**
	 * Initialize
	 * @param _configView
	 */
	public ConfigSectionPlugins(ConfigView _configView) {
		configView = _configView;
		comparator = new FilterComparator();
	}

	public String configSectionGetParentSection() {
		return ConfigSection.SECTION_ROOT;
	}

	/* Name of section will be pulled from
	 * ConfigView.section.<i>configSectionGetName()</i>
	 */
	public String configSectionGetName() {
		return ConfigSection.SECTION_PLUGINS;
	}

	public void configSectionSave() {
	}

	public int maxUserMode() {
		return 0;
	}


	public void configSectionDelete() {
		ImageLoader imageLoader = ImageLoader.getInstance();
		imageLoader.releaseImage("redled");
		imageLoader.releaseImage("greenled");
	}

	public Composite configSectionCreate(final Composite parent) {
  	if (!AzureusCoreFactory.isCoreRunning()) {
      Composite cSection = new Composite(parent, SWT.NULL);
    	cSection.setLayout(new FillLayout());
    	Label lblNotAvail = new Label(cSection, SWT.WRAP);
    	Messages.setLanguageText(lblNotAvail, "core.not.available");
    	return cSection;
    }

		GridLayout layout;
		GridData gridData;

		Label label;

		ImageLoader imageLoader = ImageLoader.getInstance();
		imgRedLed = imageLoader.getImage("redled");
		imgGreenLed = imageLoader.getImage("greenled");

		Composite infoGroup = new Composite(parent, SWT.NULL);
		gridData = new GridData(GridData.VERTICAL_ALIGN_FILL
				| GridData.HORIZONTAL_ALIGN_FILL);
		Utils.setLayoutData(infoGroup, gridData);
		layout = new GridLayout();
		layout.numColumns = 1;
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		infoGroup.setLayout(layout);

		infoGroup.setLayout(new GridLayout());

		String sep = System.getProperty("file.separator");

		File fUserPluginDir = FileUtil.getUserFile("plugins");

		String sUserPluginDir;

		try {
			sUserPluginDir = fUserPluginDir.getCanonicalPath();
		} catch (Throwable e) {
			sUserPluginDir = fUserPluginDir.toString();
		}

		if (!sUserPluginDir.endsWith(sep)) {
			sUserPluginDir += sep;
		}

		File fAppPluginDir = FileUtil.getApplicationFile("plugins");

		String sAppPluginDir;

		try {
			sAppPluginDir = fAppPluginDir.getCanonicalPath();
		} catch (Throwable e) {
			sAppPluginDir = fAppPluginDir.toString();
		}

		if (!sAppPluginDir.endsWith(sep)) {
			sAppPluginDir += sep;
		}

		label = new Label(infoGroup, SWT.WRAP);
		Utils.setLayoutData(label, new GridData(GridData.FILL_HORIZONTAL));
		Messages.setLanguageText(label, "ConfigView.pluginlist.whereToPut");

		label = new Label(infoGroup, SWT.WRAP);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.horizontalIndent = 10;
		Utils.setLayoutData(label, gridData);
		label.setText(sUserPluginDir.replaceAll("&", "&&"));
		label.setForeground(Colors.blue);
		label.setCursor(label.getDisplay().getSystemCursor(SWT.CURSOR_HAND));

		final String _sUserPluginDir = sUserPluginDir;

		label.addMouseListener(new MouseAdapter() {
			public void mouseUp(MouseEvent arg0) {
				if (_sUserPluginDir.endsWith("/plugins/")
						|| _sUserPluginDir.endsWith("\\plugins\\")) {
					File f = new File(_sUserPluginDir);
					String dir = _sUserPluginDir;
					if (!(f.exists() && f.isDirectory())) {
						dir = _sUserPluginDir.substring(0, _sUserPluginDir
								.length() - 9);
					}
					Utils.launch(dir);
				}
			}
		});

		label = new Label(infoGroup, SWT.WRAP);
		Utils.setLayoutData(label, new GridData(GridData.FILL_HORIZONTAL));
		Messages.setLanguageText(label, "ConfigView.pluginlist.whereToPutOr");

		label = new Label(infoGroup, SWT.WRAP);
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.horizontalIndent = 10;
		Utils.setLayoutData(label, gridData);
		label.setText(sAppPluginDir.replaceAll("&", "&&"));
		label.setForeground(Colors.blue);
		label.setCursor(label.getDisplay().getSystemCursor(SWT.CURSOR_HAND));

		final String _sAppPluginDir = sAppPluginDir;

		//TODO : Fix it for windows
		label.addMouseListener(new MouseAdapter() {
			public void mouseUp(MouseEvent arg0) {
				if (_sAppPluginDir.endsWith("/plugins/")
						|| _sAppPluginDir.endsWith("\\plugins\\")) {
					File f = new File(_sAppPluginDir);
					if (f.exists() && f.isDirectory()) {
						Utils.launch(_sAppPluginDir);
					} else {
						String azureusDir = _sAppPluginDir.substring(0, _sAppPluginDir
								.length() - 9);
						System.out.println(azureusDir);
						Utils.launch(azureusDir);
					}
				}
			}
		});

		pluginIFs = rebuildPluginIFs();

		Collections.sort(pluginIFs, new Comparator() {
			public int compare(Object o1, Object o2) {
				return (((PluginInterface) o1).getPluginName()
						.compareToIgnoreCase(((PluginInterface) o2).getPluginName()));
			}
		});

		Label labelInfo = new Label(infoGroup, SWT.WRAP);
		Utils.setLayoutData(labelInfo, new GridData(GridData.FILL_HORIZONTAL));
		Messages.setLanguageText(labelInfo, "ConfigView.pluginlist.info");

		table = new Table(infoGroup, SWT.BORDER | SWT.SINGLE | SWT.CHECK
				| SWT.VIRTUAL | SWT.FULL_SELECTION);
		gridData = new GridData(GridData.FILL_BOTH);
		gridData.heightHint = 200;
		gridData.widthHint = 200;
		Utils.setLayoutData(table, gridData);
		for (int i = 0; i < COLUMN_HEADERS.length; i++) {
			final TableColumn tc = new TableColumn(table, COLUMN_ALIGNS[i]);
			tc.setWidth(Utils.adjustPXForDPI(COLUMN_SIZES[i]));
			tc.addSelectionListener(new SelectionAdapter() {
				public void widgetSelected(SelectionEvent e) {
					boolean ascending = comparator.setField(table.indexOf(tc));
					try {
						table.setSortColumn(tc);
						table.setSortDirection(ascending ? SWT.UP : SWT.DOWN);
					} catch (NoSuchMethodError ignore) {
						// Ignore Pre 3.0
					}
					Collections.sort(pluginIFs, comparator);
					table.clearAll();
				}
			});
			Messages.setLanguageText(tc, HEADER_PREFIX + COLUMN_HEADERS[i]);
		}
		table.setHeaderVisible(true);

		Composite cButtons = new Composite(infoGroup, SWT.NONE);
		layout = new GridLayout();
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		layout.numColumns = 5;
		cButtons.setLayout(layout);
		Utils.setLayoutData(cButtons, new GridData());

		final Button btnUnload = new Button(cButtons, SWT.PUSH);
		Utils.setLayoutData(btnUnload, new GridData());
		Messages.setLanguageText(btnUnload, "ConfigView.pluginlist.unloadSelected");
		btnUnload.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				final int[] items = table.getSelectionIndices();

				new AEThread2("unload") {
					public void run() {
						for (int i = 0; i < items.length; i++) {
							int index = items[i];
							if (index >= 0 && index < pluginIFs.size()) {
								PluginInterface pluginIF = (PluginInterface) pluginIFs.get(index);
								if (pluginIF.getPluginState().isOperational()) {
									if (pluginIF.getPluginState().isUnloadable()) {
										try {
											pluginIF.getPluginState().unload();
										} catch (PluginException e1) {
											// TODO Auto-generated catch block
											e1.printStackTrace();
										}
									}
								}

								Utils.execSWTThread(
									new Runnable() {
										public void run() {
											pluginIFs = rebuildPluginIFs();
											table.setItemCount(pluginIFs.size());
											Collections.sort(pluginIFs, comparator);
											table.clearAll();
										}
									});
							}
						}
					}
				}.start();
			}
		});
		btnUnload.setEnabled(false);

		final Button btnLoad = new Button(cButtons, SWT.PUSH);
		Utils.setLayoutData(btnUnload, new GridData());
		Messages.setLanguageText(btnLoad, "ConfigView.pluginlist.loadSelected");
		btnLoad.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				final int[] items = table.getSelectionIndices();

				new AEThread2("load") {
					public void run() {
						for (int i = 0; i < items.length; i++) {
							int index = items[i];
							if (index >= 0 && index < pluginIFs.size()) {

								PluginInterface pluginIF = (PluginInterface) pluginIFs.get(index);
								if (pluginIF.getPluginState().isOperational()) {continue;} // Already loaded.

								// Re-enable disabled plugins, as long as they haven't failed on
								// initialise.
								if (pluginIF.getPluginState().isDisabled()) {
									if (pluginIF.getPluginState().hasFailed()) {continue;}
									pluginIF.getPluginState().setDisabled(false);
								}

								try {
									pluginIF.getPluginState().reload();
								} catch (PluginException e1) {
									// TODO Auto-generated catch block
									Debug.printStackTrace(e1);
								}

								Utils.execSWTThread(
									new Runnable() {
										public void run() {
											if (table == null || table.isDisposed()) {
												return;
											}
											pluginIFs = rebuildPluginIFs();
											table.setItemCount(pluginIFs.size());
											Collections.sort(pluginIFs, comparator);
											table.clearAll();
										}
									});
							}
						}
					}
				}.start();
			}
		});
		btnLoad.setEnabled(false);

			// scan

		final Button btnScan = new Button(cButtons, SWT.PUSH);
		Utils.setLayoutData(btnScan, new GridData());
		Messages.setLanguageText(btnScan, "ConfigView.pluginlist.scan");
		btnScan.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				AzureusCoreFactory.getSingleton().getPluginManager().refreshPluginList(false);
				pluginIFs = rebuildPluginIFs();
				table.setItemCount(pluginIFs.size());
				Collections.sort(pluginIFs, comparator);
				table.clearAll();
			}
		});

			// uninstall

		final Button btnUninstall = new Button(cButtons, SWT.PUSH);
		Utils.setLayoutData(btnUninstall, new GridData());
		Messages.setLanguageText(btnUninstall, "ConfigView.pluginlist.uninstallSelected");
		btnUninstall.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {

				btnUninstall.setEnabled(false);

				final int[] items = table.getSelectionIndices();

				new AEThread2("uninstall") {
					public void run() {
						try {

							List<PluginInterface> pis = new ArrayList<PluginInterface>();

							for (int i = 0; i < items.length; i++) {
								int index = items[i];
								if (index >= 0 && index < pluginIFs.size()) {
									PluginInterface pluginIF = (PluginInterface) pluginIFs.get(index);

									pis.add(pluginIF);
								}
							}

							if (pis.size() > 0) {

								PluginInterface[]	ps = new PluginInterface[ pis.size()];

								pis.toArray(ps);

								try {

									final AESemaphore wait_sem = new AESemaphore("unist:wait");

									ps[0].getPluginManager().getPluginInstaller().uninstall(
										ps,
										new PluginInstallationListener() {
											public void completed() {
												wait_sem.release();
											}

											public void cancelled() {
												wait_sem.release();
											}

											public void failed(
												PluginException	e) {
												wait_sem.release();
											}
										});

									wait_sem.reserve();

								} catch (Exception e) {

									Debug.printStackTrace(e);
								}
							}
						} finally {

							Utils.execSWTThread(
								new Runnable() {
									public void run() {
										pluginIFs = rebuildPluginIFs();
										table.setItemCount(pluginIFs.size());
										Collections.sort(pluginIFs, comparator);
										table.clearAll();
										table.setSelection(new int[0]);
									}
								});
						}
					}
				}.start();
			}
		});
		btnUninstall.setEnabled(false);

			// install

		final Button btnInstall = new Button(cButtons, SWT.PUSH);
		Utils.setLayoutData(btnInstall, new GridData());
		btnInstall.setText(MessageText.getString("UpdateWindow.columns.install" ) + "...");
		btnInstall.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				new InstallPluginWizard();
			}
		});

		table.addListener(SWT.SetData, new Listener() {
			public void handleEvent(Event event) {
				TableItem item = (TableItem) event.item;
				int index = table.indexOf(item);
				PluginInterface pluginIF = (PluginInterface) pluginIFs.get(index);

				for (int i = 0; i < COLUMN_HEADERS.length; i++) {
					if (i == FilterComparator.FIELD_NAME)
						item.setImage(i, pluginIF.getPluginState().isOperational()
								? imgGreenLed : imgRedLed);

					String sText = comparator.getFieldValue(i, pluginIF);
					if (sText == null)
						sText = "";
					item.setText(i, sText);
				}

				item.setGrayed(pluginIF.getPluginState().isMandatory());
				boolean bEnabled = pluginIF.getPluginState().isLoadedAtStartup();
		    Utils.setCheckedInSetData(item, bEnabled);
				item.setData("PluginID", pluginIF.getPluginID());
				Utils.alternateRowBackground(item);
			}
		});

		table.addMouseListener(
			new MouseAdapter() {
				@Override
				public void mouseDoubleClick(MouseEvent e) {
					TableItem[] items = table.getSelection();

					if (items.length == 1) {

						int index = table.indexOf(items[0]);

						PluginInterface pluginIF = (PluginInterface) pluginIFs.get(index);

						PluginConfigModel[] models = pluginIF.getUIManager().getPluginConfigModels();

						for (PluginConfigModel model: models) {

							if (model.getPluginInterface() == pluginIF) {

								if (model instanceof BasicPluginConfigModel) {

									String id = ((BasicPluginConfigModel)model).getSection();

									UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();

									if (uiFunctions != null) {

										uiFunctions.getMDI().showEntryByID(	MultipleDocumentInterface.SIDEBAR_SECTION_CONFIG, id);
									}
								}
							}
						}
					}
				}
			});

		table.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				TableItem item = (TableItem) e.item;
				int index = table.indexOf(item);
				PluginInterface pluginIF = (PluginInterface) pluginIFs.get(index);

				if (e.detail == SWT.CHECK) {

					if (item.getGrayed()) {
						if (!item.getChecked())
							item.setChecked(true);
						return;
					}

					pluginIF.getPluginState().setDisabled(!item.getChecked());
					pluginIF.getPluginState().setLoadedAtStartup(item.getChecked());
				}

				btnUnload.setEnabled(pluginIF.getPluginState().isOperational() && pluginIF.getPluginState().isUnloadable());
				btnLoad.setEnabled(!pluginIF.getPluginState().isOperational() && !pluginIF.getPluginState().hasFailed());
				btnUninstall.setEnabled(! (pluginIF.getPluginState().isBuiltIn()||pluginIF.getPluginState().isMandatory()));
			}
		});

		table.setItemCount(pluginIFs.size());


		return infoGroup;
	}

	/**
	 * @return
	 *
	 * @since 3.0.5.3
	 */
	private List rebuildPluginIFs() {
		List pluginIFs = Arrays.asList(AzureusCoreFactory.getSingleton().getPluginManager().getPlugins());
		for (Iterator iter = pluginIFs.iterator(); iter.hasNext();) {
			PluginInterface pi = (PluginInterface) iter.next();
			// COConfigurationManager will not add the same listener twice
			COConfigurationManager.addParameterListener("PluginInfo."
					+ pi.getPluginID() + ".enabled", this);
		}
		return pluginIFs;
	}

	// @see org.gudy.azureus2.core3.config.ParameterListener#parameterChanged(java.lang.String)
	public void parameterChanged(String parameterName) {
		if (table != null) {
			Utils.execSWTThread(new AERunnable() {
				public void runSupport() {
					if (table != null && !table.isDisposed()) {
						table.clearAll();
					}
				}
			});
		}
	}

	public void initPluginSubSections() {
		// Create subsections for plugins that used the PluginConfigModel object
		// =====================================================================

		TreeItem treePlugins = configView
				.findTreeItem(ConfigSection.SECTION_PLUGINS);
		ParameterRepository repository = ParameterRepository.getInstance();

		String[] names = repository.getNames();

		Arrays.sort(names);

		for (int i = 0; i < names.length; i++) {
			String pluginName = names[i];
			Parameter[] parameters = repository.getParameterBlock(pluginName);

			// Note: 2070's plugin documentation for PluginInterface.addConfigUIParameters
			//       said to pass <"ConfigView.plugins." + displayName>.  This was
			//       never implemented in 2070.  2070 read the key <displayName> without
			//       the prefix.
			//
			//       2071+ uses <sSectionPrefix ("ConfigView.section.plugins.") + pluginName>
			//       and falls back to <displayName>.  Since
			//       <"ConfigView.plugins." + displayName> was never implemented in the
			//       first place, a check for it has not been created
			boolean bUsePrefix = MessageText.keyExists(ConfigView.sSectionPrefix
					+ "plugins." + pluginName);
			Composite pluginGroup = configView.createConfigSection(treePlugins,
					pluginName, -2, bUsePrefix);
			GridLayout pluginLayout = new GridLayout();
			pluginLayout.numColumns = 3;
			pluginGroup.setLayout(pluginLayout);

			Map parameterToPluginParameter = new HashMap();
			//Add all parameters
			for (int j = 0; j < parameters.length; j++) {
				Parameter parameter = parameters[j];
				parameterToPluginParameter.put(parameter, new PluginParameter(
						pluginGroup, parameter));
			}
			//Check for dependencies
			for (int j = 0; j < parameters.length; j++) {
				Parameter parameter = parameters[j];
				if (parameter instanceof BooleanParameterImpl) {
					List parametersToEnable = ((BooleanParameterImpl) parameter)
							.getEnabledOnSelectionParameters();
					List controlsToEnable = new ArrayList();
					Iterator iter = parametersToEnable.iterator();
					while (iter.hasNext()) {
						Parameter parameterToEnable = (Parameter) iter.next();
						PluginParameter pp = (PluginParameter) parameterToPluginParameter
								.get(parameterToEnable);
						Control[] controls = pp.getControls();
						Collections.addAll(controlsToEnable, controls);
					}

					List parametersToDisable = ((BooleanParameterImpl) parameter)
							.getDisabledOnSelectionParameters();
					List controlsToDisable = new ArrayList();
					iter = parametersToDisable.iterator();
					while (iter.hasNext()) {
						Parameter parameterToDisable = (Parameter) iter.next();
						PluginParameter pp = (PluginParameter) parameterToPluginParameter
								.get(parameterToDisable);
						Control[] controls = pp.getControls();
						Collections.addAll(controlsToDisable, controls);
					}

					Control[] ce = new Control[controlsToEnable.size()];
					Control[] cd = new Control[controlsToDisable.size()];

					if (ce.length + cd.length > 0) {
						IAdditionalActionPerformer ap = new DualChangeSelectionActionPerformer(
								(Control[]) controlsToEnable.toArray(ce),
								(Control[]) controlsToDisable.toArray(cd));
						PluginParameter pp = (PluginParameter) parameterToPluginParameter
								.get(parameter);
						pp.setAdditionalActionPerfomer(ap);
					}

				}
			}
		}

	}
}
