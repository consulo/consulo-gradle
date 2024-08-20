/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.execution;

import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.execution.configuration.RunConfiguration;
import consulo.externalSystem.execution.ExternalSystemBeforeRunTask;
import consulo.externalSystem.execution.ExternalSystemBeforeRunTaskProvider;
import consulo.gradle.GradleConstants;
import consulo.gradle.icon.GradleIconGroup;
import consulo.project.Project;
import consulo.ui.image.Image;
import consulo.util.dataholder.Key;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

/**
 * @author Vladislav.Soroka
 */
@ExtensionImpl
public final class GradleBeforeRunTaskProvider extends ExternalSystemBeforeRunTaskProvider implements DumbAware {
    public static final Key<ExternalSystemBeforeRunTask> ID = Key.create("Gradle.BeforeRunTask");

    @Inject
    public GradleBeforeRunTaskProvider(Project project) {
        super(GradleConstants.SYSTEM_ID, project, ID);
    }

    @Override
    public Image getIcon() {
        return GradleIconGroup.gradle();
    }

    @Nullable
    @Override
    public Image getTaskIcon(ExternalSystemBeforeRunTask task) {
        return GradleIconGroup.gradle();
    }

    @Nonnull
    @Override
    public ExternalSystemBeforeRunTask createTask(@Nonnull RunConfiguration runConfiguration) {
        return new ExternalSystemBeforeRunTask(ID, GradleConstants.SYSTEM_ID);
    }
}
