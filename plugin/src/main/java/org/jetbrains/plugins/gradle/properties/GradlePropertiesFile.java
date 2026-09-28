// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.properties;

import consulo.project.Project;
import consulo.util.io.FileUtil;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.gradle.service.execution.GradleUserHomeUtil;
import org.jetbrains.plugins.gradle.settings.GradleLocalSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static consulo.gradle.GradleConstants.GRADLE_PROPERTIES_FILE_NAME;

public final class GradlePropertiesFile {
    private GradlePropertiesFile() {
    }

    /**
     * Expects {@code buildRoot} to be a Gradle build root already,
     * for example a linked external project path.
     * <p>
     * Therefore, this function never probes the file system to locate the build root,
     * which makes it safe to call under a read action.
     */
    public static List<Path> getPropertyPathsInBuildRoot(Project project, Path buildRoot) {
        String serviceDirectory = GradleSettings.getInstance(project).getServiceDirectoryPath();
        String gradleHome = GradleLocalSettings.getInstance(project).getGradleHome(FileUtil.toCanonicalPath(buildRoot.toString()));
        return getPropertyPaths(serviceDirectory, buildRoot, gradleHome);
    }

    private static List<Path> getPropertyPaths(@Nullable String serviceDirectory, Path buildRoot, @Nullable String gradleHome) {
        List<Path> paths = new ArrayList<>(3);
        paths.add(getPropertyPathInGradleUserHome(serviceDirectory));
        paths.add(getPropertyPathInBuildRoot(buildRoot));
        Path pathInGradleHome = getPropertyPathInGradleHome(gradleHome);
        if (pathInGradleHome != null) {
            paths.add(pathInGradleHome);
        }

        List<Path> result = new ArrayList<>(paths.size());
        for (Path path : paths) {
            result.add(path.toAbsolutePath().normalize());
        }
        return result;
    }

    public static Path getPropertyPathInGradleUserHome(@Nullable String serviceDirectory) {
        Path gradleUserHome = toNioPathOrNull(serviceDirectory);
        if (gradleUserHome == null) {
            gradleUserHome = GradleUserHomeUtil.gradleUserHomeDir();
        }
        return gradleUserHome.resolve(GRADLE_PROPERTIES_FILE_NAME);
    }

    private static Path getPropertyPathInBuildRoot(Path buildRoot) {
        return buildRoot.resolve(GRADLE_PROPERTIES_FILE_NAME);
    }

    @Nullable
    private static Path getPropertyPathInGradleHome(@Nullable String gradleHome) {
        if (gradleHome != null) {
            return Paths.get(gradleHome, GRADLE_PROPERTIES_FILE_NAME);
        }
        return null;
    }

    @Nullable
    private static Path toNioPathOrNull(@Nullable String path) {
        if (path == null) {
            return null;
        }
        try {
            return Path.of(path);
        }
        catch (InvalidPathException e) {
            return null;
        }
    }
}
