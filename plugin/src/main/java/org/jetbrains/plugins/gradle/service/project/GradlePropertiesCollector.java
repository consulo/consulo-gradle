// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project;

import consulo.annotation.component.ExtensionImpl;
import consulo.project.Project;
import jakarta.inject.Inject;
import org.jetbrains.plugins.gradle.properties.GradleDaemonJvmPropertiesFile;
import org.jetbrains.plugins.gradle.properties.GradleLocalPropertiesFile;
import org.jetbrains.plugins.gradle.properties.GradlePropertiesFile;
import org.jetbrains.plugins.gradle.service.execution.GradleUserHomeUtil;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@ExtensionImpl
public final class GradlePropertiesCollector implements GradleAutoReloadSettingsCollector {
    private final Project myProject;

    @Inject
    public GradlePropertiesCollector(Project project) {
        myProject = project;
    }

    @Override
    public List<Path> collectSettingsFiles(GradleProjectSettings projectSettings) {
        Path projectPath = Path.of(projectSettings.getExternalProjectPath());
        List<Path> paths = new ArrayList<>();
        paths.addAll(GradlePropertiesFile.getPropertyPathsInBuildRoot(myProject, projectPath));
        paths.add(GradleLocalPropertiesFile.getPropertyPath(projectPath));
        paths.add(GradleDaemonJvmPropertiesFile.getPropertyPath(projectPath));
        paths.add(GradleUserHomeUtil.gradleUserHomeDir().resolve("init.gradle"));
        return paths;
    }
}
