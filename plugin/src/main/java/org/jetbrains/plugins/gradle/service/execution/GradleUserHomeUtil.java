// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution;

import consulo.platform.Platform;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import static consulo.gradle.GradleConstants.GRADLE_CACHE_DIR_NAME;
import static consulo.gradle.GradleConstants.GRADLE_USER_HOME_ENV_KEY;
import static consulo.gradle.GradleConstants.GRADLE_USER_HOME_PROPERTY_KEY;

public final class GradleUserHomeUtil {
    private GradleUserHomeUtil() {
    }

    public static Path gradleUserHomeDir() {
        Platform platform = Platform.current();
        String gradleUserHome = platform.jvm().getRuntimeProperty(GRADLE_USER_HOME_PROPERTY_KEY);
        if (gradleUserHome == null) {
            gradleUserHome = platform.os().getEnvironmentVariable(GRADLE_USER_HOME_ENV_KEY);
        }
        if (gradleUserHome != null) {
            try {
                return Path.of(gradleUserHome).normalize();
            }
            catch (InvalidPathException ignored) {
            }
        }
        return platform.user().homePath().resolve(GRADLE_CACHE_DIR_NAME).normalize();
    }
}
