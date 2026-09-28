/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import consulo.application.util.ConcurrentFactoryMap;
import consulo.externalSystem.model.DataNode;
import consulo.externalSystem.model.Key;
import consulo.externalSystem.model.ProjectKeys;
import consulo.externalSystem.model.ProjectSystemId;
import consulo.externalSystem.rt.model.DefaultExternalProject;
import consulo.externalSystem.rt.model.ExternalProject;
import consulo.externalSystem.service.project.manage.ProjectDataManager;
import consulo.externalSystem.service.project.manage.ProjectDataService;
import consulo.externalSystem.util.ExternalSystemApiUtil;
import consulo.externalSystem.util.ExternalSystemConstants;
import consulo.externalSystem.util.Order;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.project.Project;
import consulo.util.lang.Pair;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Map;

/**
 * @author Vladislav.Soroka
 * @since 2014-07-17
 */
@Order(ExternalSystemConstants.BUILTIN_SERVICE_ORDER)
public class ExternalProjectDataService implements ProjectDataService<ExternalProject, Project> {
    private static final Logger LOG = Logger.getInstance(ExternalProjectDataService.class);

    @Nonnull
    public static final Key<ExternalProject> KEY =
        Key.create(ExternalProject.class, ProjectKeys.TASK.getProcessingWeight() + 1);

    @Nonnull
    private final Map<Pair<ProjectSystemId, File>, ExternalProject> myExternalRootProjects;

    @Nonnull
    private ProjectDataManager myProjectDataManager;

    public ExternalProjectDataService(@Nonnull ProjectDataManager projectDataManager) {
        myProjectDataManager = projectDataManager;
        myExternalRootProjects = ConcurrentFactoryMap.createMap(key -> new ExternalProjectSerializer().load(key.first, key.second));
    }

    @Nonnull
    @Override
    public Key<ExternalProject> getTargetDataKey() {
        return KEY;
    }

    @Override
    public void importData(
        @Nonnull final Collection<DataNode<ExternalProject>> toImport,
        @Nonnull final Project project,
        final boolean synchronous
    ) {
        if (toImport.size() != 1) {
            throw new IllegalArgumentException(String.format(
                "Expected to get a single external project but got %d: %s",
                toImport.size(),
                toImport
            ));
        }
        saveExternalProject(toImport.iterator().next().getData());
    }

    @Override
    public void removeData(@Nonnull final Collection<? extends Project> modules, @Nonnull Project project, boolean synchronous) {
    }

    @Nullable
    public ExternalProject getRootExternalProject(@Nonnull ProjectSystemId systemId, @Nonnull File projectRootDir) {
        return myExternalRootProjects.get(Pair.create(systemId, projectRootDir));
    }

    public void saveExternalProject(@Nonnull ExternalProject externalProject) {
        DefaultExternalProject value = new DefaultExternalProject(externalProject);

        myExternalRootProjects.put(
            Pair.create(new ProjectSystemId(externalProject.getExternalSystemId()), externalProject.getProjectDir()),
            value
        );

        new ExternalProjectSerializer().save(value);
    }

    @Nullable
    public ExternalProject findExternalProject(@Nonnull ExternalProject parentProject, @Nonnull Module module) {
        String externalProjectId = ExternalSystemApiUtil.getExternalProjectId(module);
        return externalProjectId != null ? findExternalProject(parentProject, externalProjectId) : null;
    }

    @Nullable
    private static ExternalProject findExternalProject(@Nonnull ExternalProject parentProject, @Nonnull String externalProjectId) {
        if (parentProject.getQName().equals(externalProjectId)) {
            return parentProject;
        }
        if (parentProject.getChildProjects().containsKey(externalProjectId)) {
            return parentProject.getChildProjects().get(externalProjectId);
        }
        for (ExternalProject externalProject : parentProject.getChildProjects().values()) {
            final ExternalProject project = findExternalProject(externalProject, externalProjectId);
            if (project != null) {
                return project;
            }
        }
        return null;
    }
}
