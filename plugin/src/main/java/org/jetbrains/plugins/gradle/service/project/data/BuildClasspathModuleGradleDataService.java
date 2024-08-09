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

import consulo.annotation.component.ExtensionImpl;
import consulo.externalSystem.ExternalSystemManager;
import consulo.externalSystem.model.DataNode;
import consulo.externalSystem.model.Key;
import consulo.externalSystem.model.ProjectKeys;
import consulo.externalSystem.model.project.ExternalModuleBuildClasspathPojo;
import consulo.externalSystem.model.project.ExternalProjectBuildClasspathPojo;
import consulo.externalSystem.model.project.ModuleData;
import consulo.externalSystem.service.project.ProjectData;
import consulo.externalSystem.service.project.manage.ProjectDataService;
import consulo.externalSystem.setting.AbstractExternalSystemLocalSettings;
import consulo.externalSystem.util.ExternalSystemApiUtil;
import consulo.externalSystem.util.ExternalSystemConstants;
import consulo.externalSystem.util.Order;
import consulo.ide.ServiceManager;
import consulo.ide.impl.idea.util.containers.ContainerUtil;
import consulo.project.Project;
import consulo.util.collection.FactoryMap;
import consulo.util.io.FileUtil;
import org.jetbrains.plugins.gradle.model.data.BuildScriptClasspathData;
import org.jetbrains.plugins.gradle.service.GradleBuildClasspathManager;
import org.jetbrains.plugins.gradle.service.GradleInstallationManager;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import consulo.gradle.GradleConstants;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.*;

/**
 * @author Vladislav.Soroka
 * @since 8/27/13
 */
@ExtensionImpl
@Order(ExternalSystemConstants.UNORDERED)
public class BuildClasspathModuleGradleDataService implements ProjectDataService<BuildScriptClasspathData, Module> {
    @Nonnull
    @Override
    public Key<BuildScriptClasspathData> getTargetDataKey() {
        return BuildScriptClasspathData.KEY;
    }

    @Override
    public void importData(
        @Nonnull final Collection<DataNode<BuildScriptClasspathData>> toImport,
        @Nonnull final Project project,
        boolean synchronous
    ) {
        if (toImport.isEmpty()) {
            return;
        }
        if (!project.isInitialized()) {
            return;
        }

        final GradleInstallationManager gradleInstallationManager = ServiceManager.getService(GradleInstallationManager.class);

        ExternalSystemManager<?, ?, ?, ?, ?> manager = ExternalSystemApiUtil.getManager(GradleConstants.SYSTEM_ID);
        assert manager != null;
        AbstractExternalSystemLocalSettings localSettings = manager.getLocalSettingsProvider().apply(project);

        Map<String/* externalProjectPath */, Set<String>> externalProjectGradleSdkLibs = FactoryMap.create(externalProjectPath ->
        {
            GradleProjectSettings settings = GradleSettings.getInstance(project).getLinkedProjectSettings(externalProjectPath);
            if (settings == null || settings.getDistributionType() == null) {
                return null;
            }

            final Set<String> gradleSdkLibraries = new LinkedHashSet<>();
            File gradleHome = gradleInstallationManager.getGradleHome(
                settings.getDistributionType(),
                externalProjectPath,
                settings.getGradleHome()
            );
            if (gradleHome != null && gradleHome.isDirectory()) {
                final Collection<File> libraries = gradleInstallationManager.getClassRoots(project, externalProjectPath);
                if (libraries != null) {
                    for (File library : libraries) {
                        gradleSdkLibraries.add(FileUtil.toCanonicalPath(library.getPath()));
                    }
                }
            }
            return gradleSdkLibraries;
        });

        for (final DataNode<BuildScriptClasspathData> node : toImport) {
            if (GradleConstants.SYSTEM_ID.equals(node.getData().getOwner())) {
                DataNode<ProjectData> projectDataNode = ExternalSystemApiUtil.findParent(node, ProjectKeys.PROJECT);
                assert projectDataNode != null;

                String linkedExternalProjectPath = projectDataNode.getData().getLinkedExternalProjectPath();
                DataNode<ModuleData> moduleDataNode = ExternalSystemApiUtil.findParent(node, ProjectKeys.MODULE);
                if (moduleDataNode == null) {
                    continue;
                }

                String externalModulePath = moduleDataNode.getData().getLinkedExternalProjectPath();
                GradleProjectSettings settings = GradleSettings.getInstance(project).getLinkedProjectSettings(linkedExternalProjectPath);
                if (settings == null || settings.getDistributionType() == null) {
                    continue;
                }

                final Set<String> buildClasspath = ContainerUtil.newLinkedHashSet();
                BuildScriptClasspathData buildScriptClasspathData = node.getData();
                for (BuildScriptClasspathData.ClasspathEntry classpathEntry : buildScriptClasspathData.getClasspathEntries()) {
                    for (String path : classpathEntry.getSourcesFile()) {
                        buildClasspath.add(FileUtil.toCanonicalPath(path));
                    }

                    for (String path : classpathEntry.getClassesFile()) {
                        buildClasspath.add(FileUtil.toCanonicalPath(path));
                    }
                }

                ExternalProjectBuildClasspathPojo projectBuildClasspathPojo =
                    localSettings.getProjectBuildClasspath().get(linkedExternalProjectPath);
                if (projectBuildClasspathPojo == null) {
                    projectBuildClasspathPojo = new ExternalProjectBuildClasspathPojo(
                        moduleDataNode.getData().getExternalName(),
                        ContainerUtil.<String>newArrayList(),
                        ContainerUtil.<String, ExternalModuleBuildClasspathPojo>newHashMap()
                    );
                    localSettings.getProjectBuildClasspath().put(linkedExternalProjectPath, projectBuildClasspathPojo);
                }

                List<String> projectBuildClasspath =
                    ContainerUtil.newArrayList(externalProjectGradleSdkLibs.get(linkedExternalProjectPath));
                // add main java root of buildSrc project
                projectBuildClasspath.add(linkedExternalProjectPath + "/buildSrc/src/main/java");
                // add main groovy root of buildSrc project
                projectBuildClasspath.add(linkedExternalProjectPath + "/buildSrc/src/main/groovy");

                projectBuildClasspathPojo.setProjectBuildClasspath(projectBuildClasspath);
                projectBuildClasspathPojo.getModulesBuildClasspath().put(
                    externalModulePath,
                    new ExternalModuleBuildClasspathPojo(externalModulePath, ContainerUtil.newArrayList(buildClasspath))
                );
            }
        }

        GradleBuildClasspathManager.getInstance(project).reload();
    }

    @Override
    public void removeData(@Nonnull Collection<? extends Module> toRemove, @Nonnull Project project, boolean synchronous) {
    }
}
