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

import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.Task;
import consulo.application.util.ConcurrentFactoryMap;
import consulo.externalSystem.localize.ExternalSystemLocalize;
import consulo.externalSystem.model.DataNode;
import consulo.externalSystem.model.Key;
import consulo.externalSystem.model.ProjectKeys;
import consulo.externalSystem.model.ProjectSystemId;
import consulo.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import consulo.externalSystem.rt.model.DefaultExternalProject;
import consulo.externalSystem.rt.model.ExternalProject;
import consulo.externalSystem.service.notification.NotificationSource;
import consulo.externalSystem.service.project.ProjectData;
import consulo.externalSystem.service.project.manage.ProjectDataService;
import consulo.externalSystem.setting.ExternalProjectSettings;
import consulo.externalSystem.util.ExternalSystemApiUtil;
import consulo.externalSystem.util.ExternalSystemConstants;
import consulo.externalSystem.util.Order;
import consulo.ide.impl.idea.openapi.externalSystem.service.internal.ExternalSystemResolveProjectTask;
import consulo.ide.impl.idea.openapi.externalSystem.service.notification.ExternalSystemNotificationManager;
import consulo.ide.impl.idea.openapi.externalSystem.service.project.manage.ProjectDataManager;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.content.ProjectRootManager;
import consulo.platform.base.localize.CommonLocalize;
import consulo.project.Project;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.io.FileUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.ref.Ref;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.util.Collection;
import java.util.Map;

/**
 * @author Vladislav.Soroka
 * @since 7/17/2014
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
    public ExternalProject getOrImportRootExternalProject(
        @Nonnull Project project,
        @Nonnull ProjectSystemId systemId,
        @Nonnull File projectRootDir
    ) {
        final ExternalProject externalProject = getRootExternalProject(systemId, projectRootDir);
        return externalProject != null ? externalProject : importExternalProject(project, systemId, projectRootDir);
    }

    @Nullable
    private ExternalProject importExternalProject(
        @Nonnull final Project project,
        @Nonnull final ProjectSystemId projectSystemId,
        @Nonnull final File projectRootDir
    ) {
        final Boolean result = UIUtil.invokeAndWaitIfNeeded(() -> {
            final Ref<Boolean> result1 = new Ref<>(false);
            if (project.isDisposed()) {
                return false;
            }

            final String linkedProjectPath = FileUtil.toCanonicalPath(projectRootDir.getPath());
            final ExternalProjectSettings projectSettings =
                ExternalSystemApiUtil.getSettings(project, projectSystemId).getLinkedProjectSettings(linkedProjectPath);
            if (projectSettings == null) {
                LOG.warn("Unable to get project settings for project path: " + linkedProjectPath);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Available projects paths: " + ContainerUtil.map(
                        ExternalSystemApiUtil.getSettings(project, projectSystemId).getLinkedProjectsSettings(),
                        ExternalProjectSettings::getExternalProjectPath
                    ));
                }
                return false;
            }

            final File projectFile = new File(linkedProjectPath);
            final String projectName;
            if (projectFile.isFile()) {
                projectName = projectFile.getParentFile().getName();
            }
            else {
                projectName = projectFile.getName();
            }

            // ask a user for the project import if auto-import is disabled
            if (!projectSettings.isUseAutoImport()) {
                String message = String.format(
                    "Project '%s' require synchronization with %s configuration. \nImport the project?",
                    projectName,
                    projectSystemId.getDisplayName()
                );
                int returnValue = Messages.showOkCancelDialog(
                    message,
                    "Import Project",
                    CommonLocalize.buttonOk().get(),
                    CommonLocalize.buttonCancel().get(),
                    UIUtil.getQuestionIcon()
                );
                if (returnValue != Messages.OK) {
                    return false;
                }
            }

            final LocalizeValue title =
                ExternalSystemLocalize.progressImportText(linkedProjectPath, projectSystemId.getDisplayName());
            new Task.Modal(project, title.get(), false) {
                @Override
                public void run(@Nonnull ProgressIndicator indicator) {
                    if (project.isDisposed()) {
                        return;
                    }

                    ExternalSystemNotificationManager.getInstance(project)
                        .clearNotifications(null, NotificationSource.PROJECT_SYNC, projectSystemId);
                    ExternalSystemResolveProjectTask task =
                        new ExternalSystemResolveProjectTask(projectSystemId, project, linkedProjectPath, false);
                    task.execute(indicator, ExternalSystemTaskNotificationListener.EP_NAME.getExtensions());
                    if (project.isDisposed()) {
                        return;
                    }

                    final Throwable error = task.getError();
                    if (error != null) {
                        ExternalSystemNotificationManager.getInstance(project)
                            .processExternalProjectRefreshError(error, projectName, projectSystemId);
                        return;
                    }
                    final DataNode<ProjectData> projectDataDataNode = task.getExternalProject();
                    if (projectDataDataNode == null) {
                        return;
                    }

                    final Collection<DataNode<ExternalProject>> nodes = ExternalSystemApiUtil.findAll(projectDataDataNode, KEY);
                    if (nodes.size() != 1) {
                        throw new IllegalArgumentException(String.format(
                            "Expected to get a single external project but got %d: %s",
                            nodes.size(),
                            nodes
                        ));
                    }

                    ProjectRootManager.getInstance((Project)myProject)
                        .mergeRootsChangesDuring(() -> myProjectDataManager.importData(KEY, nodes, project, true));

                    result1.set(true);
                }
            }.queue();

            return result1.get();
        });

        return result ? getRootExternalProject(projectSystemId, projectRootDir) : null;
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
