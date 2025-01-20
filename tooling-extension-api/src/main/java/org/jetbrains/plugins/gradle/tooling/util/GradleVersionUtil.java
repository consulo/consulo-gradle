// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.util;

import org.gradle.util.GradleVersion;

public final class GradleVersionUtil {

    private static final GradleVersion currentGradleVersion = GradleVersion.current().getBaseVersion();

    public static boolean isCurrentGradleAtLeast(String version) {
        return currentGradleVersion.compareTo(GradleVersion.version(version)) >= 0;
    }

    public static boolean isGradleAtLeast(GradleVersion actualVersion, String version) {
        return actualVersion.getBaseVersion().compareTo(GradleVersion.version(version)) >= 0;
    }

    public static boolean isGradleAtLeast(String actualVersion, String version) {
        return isGradleAtLeast(GradleVersion.version(actualVersion), version);
    }

    public static boolean isCurrentGradleOlderThan(String version) {
        return !isCurrentGradleAtLeast(version);
    }

    public static boolean isGradleOlderThan(GradleVersion actualVersion, String version) {
        return !isGradleAtLeast(actualVersion, version);
    }

    public static boolean isGradleOlderThan(String actualVersion, String version) {
        return !isGradleAtLeast(actualVersion, version);
    }

    /**
     * @see GradleVersionUtil#isGradleAtLeast
     * @see GradleVersionUtil#isCurrentGradleAtLeast
     * @deprecated Gradle version comparisons '>' and '<=' aren't logical.
     * Changes can be made only in the specific version and present in the future.
     * We always can identify the version where new changes were made.
     */
    @Deprecated
    @SuppressWarnings("DeprecatedIsStillUsed")
    public static boolean isCurrentGradleNewerThan(String version) {
        return currentGradleVersion.compareTo(GradleVersion.version(version)) > 0;
    }

    /**
     * @deprecated See {@link GradleVersionUtil#isCurrentGradleNewerThan} for details
     */
    @Deprecated
    @SuppressWarnings("DeprecatedIsStillUsed")
    public static boolean isGradleNewerThan(GradleVersion actualVersion, String version) {
        return actualVersion.getBaseVersion().compareTo(GradleVersion.version(version)) > 0;
    }

    /**
     * @deprecated See {@link GradleVersionUtil#isCurrentGradleNewerThan} for details
     */
    @Deprecated
    @SuppressWarnings("DeprecatedIsStillUsed")
    public static boolean isGradleNewerThan(String actualVersion, String version) {
        return isGradleNewerThan(GradleVersion.version(actualVersion), version);
    }

    /**
     * @deprecated See {@link GradleVersionUtil#isCurrentGradleNewerThan} for details
     */
    @Deprecated
    @SuppressWarnings("DeprecatedIsStillUsed")
    public static boolean isCurrentGradleOlderOrSameAs(String version) {
        return !isCurrentGradleNewerThan(version);
    }

    /**
     * @deprecated See {@link GradleVersionUtil#isCurrentGradleNewerThan} for details
     */
    @Deprecated
    @SuppressWarnings("DeprecatedIsStillUsed")
    public static boolean isGradleOlderOrSameAs(GradleVersion actualVersion, String version) {
        return !isGradleNewerThan(actualVersion, version);
    }

    /**
     * @deprecated See {@link GradleVersionUtil#isCurrentGradleNewerThan} for details
     */
    @Deprecated
    @SuppressWarnings("DeprecatedIsStillUsed")
    public static boolean isGradleOlderOrSameAs(String actualVersion, String version) {
        return !isGradleNewerThan(actualVersion, version);
    }
}
