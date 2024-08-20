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

import consulo.externalSystem.ExternalSystemAutoImportAware;
import consulo.externalSystem.ExternalSystemManager;
import consulo.externalSystem.setting.AbstractExternalSystemSettings;
import consulo.externalSystem.setting.ExternalProjectSettings;
import consulo.externalSystem.util.ExternalSystemApiUtil;
import consulo.project.Project;
import consulo.gradle.GradleConstants;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Denis Zhdanov
 * @since 2013-06-08
 */
public class GradleAutoImportAware implements ExternalSystemAutoImportAware {
    @Nullable
    @Override
    public String getAffectedExternalProjectPath(@Nonnull String changedFileOrDirPath, @Nonnull Project project) {
        if (!changedFileOrDirPath.endsWith(GradleConstants.EXTENSION)) {
            return null;
        }

        ExternalSystemManager<?, ?, ?, ?, ?> manager = ExternalSystemApiUtil.getManager(GradleConstants.SYSTEM_ID);
        assert manager != null;
        AbstractExternalSystemSettings<?, ?, ?> systemSettings = manager.getSettingsProvider().apply(project);
        Collection<? extends ExternalProjectSettings> projectsSettings = systemSettings.getLinkedProjectsSettings();
        if (projectsSettings.isEmpty()) {
            return null;
        }
        Map<String /* config dir path */, String /* config file path */> rootPaths = new HashMap<>();
        for (ExternalProjectSettings setting : projectsSettings) {
            if (setting != null && setting.getExternalProjectPath() != null) {
                File rootPath = new File(setting.getExternalProjectPath());
                if (rootPath.getParentFile() != null) {
                    rootPaths.put(rootPath.getParentFile().getAbsolutePath(), setting.getExternalProjectPath());
                }
            }
        }

        for (File f = new File(changedFileOrDirPath).getParentFile(); f != null; f = f.getParentFile()) {
            String dirPath = f.getAbsolutePath();
            String configFilePath = rootPaths.get(dirPath);
            if (rootPaths.containsKey(dirPath)) {
                return configFilePath;
            }
        }
        return null;
    }
}
