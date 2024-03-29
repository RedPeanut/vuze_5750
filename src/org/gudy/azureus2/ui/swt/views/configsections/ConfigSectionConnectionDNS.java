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

package org.gudy.azureus2.ui.swt.views.configsections;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;

import org.eclipse.swt.widgets.Composite;

import org.eclipse.swt.widgets.Label;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.internat.MessageText;

import org.gudy.azureus2.plugins.ui.config.ConfigSection;
import org.gudy.azureus2.ui.swt.Messages;
import org.gudy.azureus2.ui.swt.Utils;
import org.gudy.azureus2.ui.swt.components.LinkLabel;
import org.gudy.azureus2.ui.swt.config.*;
import org.gudy.azureus2.ui.swt.plugins.UISWTConfigSection;


public class ConfigSectionConnectionDNS implements UISWTConfigSection {

	private final static String CFG_PREFIX = "ConfigView.section.dns.";

	private final static int REQUIRED_MODE = 2;

	public int maxUserMode() {
		return REQUIRED_MODE;
	}


	public String configSectionGetParentSection() {
		return ConfigSection.SECTION_CONNECTION;
	}

	public String configSectionGetName() {
		return "DNS";
	}

	public void configSectionSave() {
	}

	public void configSectionDelete() {
	}

	public Composite configSectionCreate(final Composite parent) {
		GridData gridData;
		GridLayout layout;

		Composite cSection = new Composite(parent, SWT.NULL);

		gridData = new GridData(GridData.VERTICAL_ALIGN_FILL
				| GridData.HORIZONTAL_ALIGN_FILL);
		Utils.setLayoutData(cSection, gridData);
		layout = new GridLayout();
		layout.numColumns = 2;
		cSection.setLayout(layout);

		int userMode = COConfigurationManager.getIntParameter("User Mode");
		if (userMode < REQUIRED_MODE) {
			Label label = new Label(cSection, SWT.WRAP);
			gridData = new GridData();
			gridData.horizontalSpan = 2;
			Utils.setLayoutData(label, gridData);

			final String[] modeKeys = { "ConfigView.section.mode.beginner",
					"ConfigView.section.mode.intermediate",
					"ConfigView.section.mode.advanced" };

			String param1, param2;
			if (REQUIRED_MODE < modeKeys.length)
				param1 = MessageText.getString(modeKeys[REQUIRED_MODE]);
			else
				param1 = String.valueOf(REQUIRED_MODE);

			if (userMode < modeKeys.length)
				param2 = MessageText.getString(modeKeys[userMode]);
			else
				param2 = String.valueOf(userMode);

			label.setText(MessageText.getString("ConfigView.notAvailableForMode",
					new String[] { param1, param2 } ));

			return cSection;
		}

		//////////////////////

		Label label = new Label(cSection, SWT.WRAP);
		Messages.setLanguageText(label, CFG_PREFIX + "info");
		gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.horizontalSpan = 2;
		gridData.widthHint = 200;  // needed for wrap
		Utils.setLayoutData(label, gridData);

		gridData = new GridData();
		gridData.horizontalSpan = 2;
		new LinkLabel( cSection, gridData, "ConfigView.label.please.visit.here", MessageText.getString( CFG_PREFIX + "url"));


		Label comment_label = new Label(cSection, SWT.NULL);
		Messages.setLanguageText(comment_label, CFG_PREFIX + "alts");

		gridData = new GridData(GridData.FILL_HORIZONTAL);
		StringParameter alt_servers = new StringParameter(cSection, "DNS Alt Servers");
		alt_servers.setLayoutData(gridData);

		final BooleanParameter allow_socks = new BooleanParameter(cSection,	"DNS Alt Servers SOCKS Enable", CFG_PREFIX + "allow_socks");
		gridData = new GridData();
		gridData.horizontalSpan = 2;
		allow_socks.setLayoutData(gridData);


		return cSection;

	}
}
