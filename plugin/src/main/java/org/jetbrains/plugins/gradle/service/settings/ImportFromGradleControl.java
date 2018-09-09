/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.gradle.service.settings;

import com.intellij.openapi.externalSystem.service.settings.AbstractImportFromExternalSystemControl;
import com.intellij.openapi.externalSystem.util.ExternalSystemSettingsControl;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.text.StringUtil;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettingsListener;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleUtil;

/**
 * @author Denis Zhdanov
 * @since 4/30/13 4:58 PM
 */
public class ImportFromGradleControl
  extends AbstractImportFromExternalSystemControl<GradleProjectSettings, GradleSettingsListener, GradleSettings>
{
  public ImportFromGradleControl() {
    super(GradleConstants.SYSTEM_ID, new GradleSettings(ProjectManager.getInstance().getDefaultProject()), getInitialProjectSettings());
  }

  @Nonnull
  private static GradleProjectSettings getInitialProjectSettings() {
    GradleProjectSettings result = new GradleProjectSettings();
    String gradleHome = GradleUtil.getLastUsedGradleHome();
    if (!StringUtil.isEmpty(gradleHome)) {
      result.setGradleHome(gradleHome);
    }
    return result;
  }
  
  @Nonnull
  @Override
  protected ExternalSystemSettingsControl<GradleProjectSettings> createProjectSettingsControl(@Nonnull GradleProjectSettings settings) {
    GradleProjectSettingsControl settingsControl = new GradleProjectSettingsControl(settings);
    settingsControl.hideUseAutoImportBox();
    return settingsControl;
  }

  @Nullable
  @Override
  protected ExternalSystemSettingsControl<GradleSettings> createSystemSettingsControl(@Nonnull GradleSettings settings) {
    return new GradleSystemSettingsControl(settings);
  }

  @Override
  protected void onLinkedProjectPathChange(@Nonnull String path) {
    ((GradleProjectSettingsControl)getProjectSettingsControl()).updateWrapperControls(path, false);
  }
}
