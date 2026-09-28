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
package org.jetbrains.plugins.gradle.service.project.data;

import consulo.annotation.component.ExtensionImpl;
import consulo.externalSystem.model.DataNode;
import consulo.externalSystem.model.Key;
import consulo.externalSystem.model.ProjectKeys;
import consulo.externalSystem.model.project.ModuleData;
import consulo.externalSystem.service.project.ProjectData;
import consulo.externalSystem.service.project.manage.ProjectDataService;
import consulo.externalSystem.util.ExternalSystemApiUtil;
import consulo.externalSystem.util.ExternalSystemConstants;
import consulo.externalSystem.util.Order;
import consulo.gradle.GradleConstants;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@ExtensionImpl
@Order(ExternalSystemConstants.UNORDERED)
public class GradleExternalModulePathsDataService implements ProjectDataService<ProjectData, Project> {
    @Nonnull
    @Override
    public Key<ProjectData> getTargetDataKey() {
        return ProjectKeys.PROJECT;
    }

    @Override
    public void importData(@Nonnull Collection<DataNode<ProjectData>> toImport, @Nonnull Project project, boolean synchronous) {
        for (DataNode<ProjectData> projectNode : toImport) {
            if (!GradleConstants.SYSTEM_ID.equals(projectNode.getData().getOwner())) {
                continue;
            }

            Set<String> externalModulePaths = new HashSet<>();
            for (DataNode<ModuleData> moduleNode : ExternalSystemApiUtil.findAll(projectNode, ProjectKeys.MODULE)) {
                externalModulePaths.add(moduleNode.getData().getLinkedExternalProjectPath());
            }
            if (externalModulePaths.isEmpty()) {
                continue;
            }

            GradleProjectSettings settings =
                GradleSettings.getInstance(project).getLinkedProjectSettings(projectNode.getData().getLinkedExternalProjectPath());
            if (settings != null) {
                settings.setModules(externalModulePaths);
            }
        }
    }

    @Override
    public void removeData(@Nonnull Collection<? extends Project> toRemove, @Nonnull Project project, boolean synchronous) {
    }
}
