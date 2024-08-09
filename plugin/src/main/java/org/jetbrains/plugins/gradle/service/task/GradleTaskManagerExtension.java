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
package org.jetbrains.plugins.gradle.service.task;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.externalSystem.model.task.ExternalSystemTaskId;
import consulo.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import consulo.externalSystem.rt.model.ExternalSystemException;
import consulo.gradle.setting.GradleExecutionSettings;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * @author Vladislav.Soroka
 * @since 11/5/13
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface GradleTaskManagerExtension {

    ExtensionPointName<GradleTaskManagerExtension> EP_NAME = ExtensionPointName.create(GradleTaskManagerExtension.class);

    boolean executeTasks(
        @Nonnull final ExternalSystemTaskId id,
        @Nonnull final List<String> taskNames,
        @Nonnull String projectPath,
        @Nullable final GradleExecutionSettings settings,
        @Nonnull final List<String> vmOptions,
        @Nonnull final List<String> scriptParameters,
        @Nullable final String debuggerSetup,
        @Nonnull final ExternalSystemTaskNotificationListener listener
    ) throws ExternalSystemException;

    boolean cancelTask(@Nonnull ExternalSystemTaskId id, @Nonnull ExternalSystemTaskNotificationListener listener)
        throws ExternalSystemException;
}
