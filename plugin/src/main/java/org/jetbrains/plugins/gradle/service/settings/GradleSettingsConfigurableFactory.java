/*
 * Copyright 2013-2026 consulo.io
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

import consulo.annotation.component.ExtensionImpl;
import consulo.externalSystem.model.ProjectSystemId;
import consulo.externalSystem.service.setting.AbstractExternalProjectSettingsConfigurable;
import consulo.externalSystem.service.setting.ExternalSystemSettingsConfigurable;
import consulo.externalSystem.service.setting.ExternalSystemSettingsConfigurableFactory;
import consulo.externalSystem.service.setting.ExternalSystemSettingsPlace;
import consulo.gradle.GradleConstants;
import consulo.project.ProjectManager;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.lang.StringUtil;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleUtil;

/**
 * @author VISTALL
 */
@ExtensionImpl
public class GradleSettingsConfigurableFactory
    implements ExternalSystemSettingsConfigurableFactory<GradleProjectSettings, GradleSettings> {

    @Override
    public ProjectSystemId getSystemId() {
        return GradleConstants.SYSTEM_ID;
    }

    @Override
    public GradleProjectSettings createProjectSettings() {
        GradleProjectSettings result = new GradleProjectSettings();
        String gradleHome = GradleUtil.getLastUsedGradleHome();
        if (!StringUtil.isEmpty(gradleHome)) {
            result.setGradleHome(gradleHome);
        }
        return result;
    }

    @Override
    public GradleSettings createSystemSettings() {
        return new GradleSettings(ProjectManager.getInstance().getDefaultProject());
    }

    @Override
    @RequiredUIAccess
    public AbstractExternalProjectSettingsConfigurable<GradleProjectSettings> createProjectSettingsConfigurable(
        GradleProjectSettings settings,
        ExternalSystemSettingsPlace place
    ) {
        return new GradleProjectSettingsConfigurable(settings, place);
    }

    @Override
    @RequiredUIAccess
    public ExternalSystemSettingsConfigurable<GradleSettings> createSystemSettingsConfigurable(
        GradleSettings settings,
        ExternalSystemSettingsPlace place
    ) {
        return new GradleSystemSettingsConfigurable(settings);
    }
}
