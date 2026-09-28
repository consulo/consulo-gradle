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
package org.jetbrains.plugins.gradle.service.project;

import consulo.compiler.CompilerConfiguration;
import consulo.compiler.ModuleCompilerPathsManager;
import consulo.externalSystem.ExternalSystemManager;
import consulo.externalSystem.service.project.autoimport.ExternalSystemAutoImportAware;
import consulo.externalSystem.setting.AbstractExternalSystemSettings;
import consulo.externalSystem.setting.ExternalProjectSettings;
import consulo.externalSystem.util.ExternalSystemApiUtil;
import consulo.gradle.GradleConstants;
import consulo.language.content.ProductionContentFolderTypeProvider;
import consulo.language.content.TestContentFolderTypeProvider;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.project.Project;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Denis Zhdanov
 * @since 2013-06-08
 */
public class GradleAutoImportAware implements ExternalSystemAutoImportAware {
    @Nullable
    @Override
    public String getAffectedExternalProjectPath(@Nonnull String changedFileOrDirPath, @Nonnull Project project) {
        if (!changedFileOrDirPath.endsWith("." + GradleConstants.EXTENSION) &&
            !changedFileOrDirPath.endsWith("." + GradleConstants.KOTLIN_DSL_SCRIPT_EXTENSION)) {
            return null;
        }

        if (isInsideCompileOutput(changedFileOrDirPath, project)) {
            return null;
        }

        File file = new File(changedFileOrDirPath);
        if (file.isDirectory()) {
            return null;
        }

        ExternalSystemManager<?, ?, ?, ?, ?> manager = ExternalSystemApiUtil.getManager(GradleConstants.SYSTEM_ID);
        assert manager != null;
        AbstractExternalSystemSettings<?, ?, ?> systemSettings = manager.getSettingsProvider().apply(project);
        Collection<? extends ExternalProjectSettings> projectsSettings = systemSettings.getLinkedProjectsSettings();
        if (projectsSettings.isEmpty()) {
            return null;
        }
        Map<String, String> rootPaths = new HashMap<>();
        for (ExternalProjectSettings setting : projectsSettings) {
            if (setting != null) {
                for (String path : setting.getModules()) {
                    rootPaths.put(new File(path).getPath(), setting.getExternalProjectPath());
                }
            }
        }

        for (File f = file.getParentFile(); f != null; f = f.getParentFile()) {
            String dirPath = f.getPath();
            if (rootPaths.containsKey(dirPath)) {
                return rootPaths.get(dirPath);
            }
        }
        return null;
    }

    private static boolean isInsideCompileOutput(@Nonnull String path, @Nonnull Project project) {
        String url = VirtualFileUtil.pathToUrl(path);
        String projectOutputUrl = CompilerConfiguration.getInstance(project).getCompilerOutputUrl();
        if (projectOutputUrl != null && VirtualFileUtil.isEqualOrAncestor(projectOutputUrl, url)) {
            return true;
        }

        for (Module module : ModuleManager.getInstance(project).getModules()) {
            ModuleCompilerPathsManager pathsManager = ModuleCompilerPathsManager.getInstance(module);
            String outputUrl = pathsManager.getCompilerOutputUrl(ProductionContentFolderTypeProvider.getInstance());
            if (outputUrl != null && VirtualFileUtil.isEqualOrAncestor(outputUrl, url)) {
                return true;
            }
            String testOutputUrl = pathsManager.getCompilerOutputUrl(TestContentFolderTypeProvider.getInstance());
            if (testOutputUrl != null && VirtualFileUtil.isEqualOrAncestor(testOutputUrl, url)) {
                return true;
            }
        }
        return false;
    }

    @Nonnull
    @Override
    public List<Path> getAffectedExternalProjectFilePaths(String externalProjectPath, @Nonnull Project project) {
        GradleProjectSettings projectSettings = GradleSettings.getInstance(project).getLinkedProjectSettings(externalProjectPath);
        if (projectSettings == null) {
            return Collections.emptyList();
        }

        List<Path> result = new ArrayList<>();
        project.getExtensionPoint(GradleAutoReloadSettingsCollector.class)
            .forEach(extension -> result.addAll(extension.collectSettingsFiles(projectSettings)));
        return result;
    }
}
