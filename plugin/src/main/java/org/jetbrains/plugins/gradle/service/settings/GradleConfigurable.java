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

import consulo.annotation.component.ExtensionImpl;
import consulo.configurable.ProjectConfigurable;
import consulo.configurable.StandardConfigurableIds;
import consulo.externalSystem.service.execution.ExternalSystemSettingsControl;
import consulo.ide.impl.idea.openapi.externalSystem.service.settings.AbstractExternalSystemConfigurable;
import consulo.project.Project;
import jakarta.inject.Inject;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettingsListener;
import consulo.gradle.GradleConstants;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author Denis Zhdanov
 * @since 2013-04-30
 */
@ExtensionImpl
public class GradleConfigurable extends AbstractExternalSystemConfigurable<GradleProjectSettings, GradleSettingsListener, GradleSettings> implements ProjectConfigurable {
    @Inject
    public GradleConfigurable(@Nonnull Project project) {
        super(project, GradleConstants.SYSTEM_ID);
    }

    @Nonnull
    @Override
    protected ExternalSystemSettingsControl<GradleProjectSettings> createProjectSettingsControl(@Nonnull GradleProjectSettings settings) {
        return new GradleProjectSettingsControl(settings);
    }

    @Nullable
    @Override
    protected ExternalSystemSettingsControl<GradleSettings> createSystemSettingsControl(@Nonnull GradleSettings settings) {
        return new GradleSystemSettingsControl(settings);
    }

    @Nonnull
    @Override
    protected GradleProjectSettings newProjectSettings() {
        return new GradleProjectSettings();
    }

    @Nonnull
    @Override
    public String getId() {
        return "execution.gradle";
    }

    @Nullable
    @Override
    public String getParentId() {
        return StandardConfigurableIds.EXECUTION_GROUP;
    }
}
