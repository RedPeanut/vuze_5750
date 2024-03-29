/*
 * File    : AbstractWizardPanel.java
 * Created : 30 sept. 2003 00:12:11
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

package org.gudy.azureus2.ui.swt.wizard;

/**
 * @author Olivier
 *
 */
public abstract class AbstractWizardPanel<W extends Wizard> implements IWizardPanel<W> {
  protected IWizardPanel<W> previousPanel;
  protected W wizard;

  public AbstractWizardPanel(W wizard, IWizardPanel<W> previousPanel) {
    this.previousPanel = previousPanel;
    this.wizard = wizard;
  }

  public boolean isPreviousEnabled() {
    return !(this.previousPanel == null);
  }

  public boolean isNextEnabled() {
    return false;
  }

  public boolean isFinishEnabled() {
    return false;
  }

  public IWizardPanel<W> getPreviousPanel() {
    return previousPanel;
  }

  public IWizardPanel<W> getNextPanel() {
    return null;
  }

  public IWizardPanel<W> getFinishPanel() {
    return null;
  }

  public boolean
  isFinishSelectionOK()
  {
  	return (true);
  }

  public void cancelled()
  {
  }

  public void finish() {}

}
