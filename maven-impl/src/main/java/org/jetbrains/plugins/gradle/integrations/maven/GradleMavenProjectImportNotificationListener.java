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
package org.jetbrains.plugins.gradle.integrations.maven;

import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.externalSystem.model.task.ExternalSystemTaskId;
import consulo.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import consulo.externalSystem.model.task.ExternalSystemTaskType;
import consulo.gradle.GradleConstants;
import consulo.project.Project;

import javax.annotation.Nonnull;

/**
 * {@link GradleMavenProjectImportNotificationListener} listens for Gradle project import events.
 *
 * @author Vladislav.Soroka
 * @since 2013-10-28
 */
@ExtensionImpl
public class GradleMavenProjectImportNotificationListener extends ExternalSystemTaskNotificationListenerAdapter {
    @Override
    public void onSuccess(@Nonnull ExternalSystemTaskId id) {
        if (GradleConstants.SYSTEM_ID.getId().equals(id.getProjectSystemId().getId())
            && id.getType() == ExternalSystemTaskType.RESOLVE_PROJECT) {
            final Project project = id.findProject();
            if (project == null) {
                return;
            }
            Application.get().invokeLater(new ImportMavenRepositoriesTask(project));
        }
    }
}
