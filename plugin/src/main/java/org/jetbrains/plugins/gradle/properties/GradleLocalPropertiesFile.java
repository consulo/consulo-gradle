// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.properties;

import java.nio.file.Path;
import java.nio.file.Paths;

import static consulo.gradle.GradleConstants.GRADLE_CACHE_DIR_NAME;
import static consulo.gradle.GradleConstants.GRADLE_LOCAL_PROPERTIES_FILE_NAME;

public final class GradleLocalPropertiesFile {
    private GradleLocalPropertiesFile() {
    }

    public static Path getPropertyPath(Path externalProjectPath) {
        return externalProjectPath.resolve(Paths.get(GRADLE_CACHE_DIR_NAME, GRADLE_LOCAL_PROPERTIES_FILE_NAME))
            .toAbsolutePath().normalize();
    }
}
