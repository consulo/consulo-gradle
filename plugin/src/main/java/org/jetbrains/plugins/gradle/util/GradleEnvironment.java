package org.jetbrains.plugins.gradle.util;

/**
 * @author Denis Zhdanov
 * @since 4/10/12 3:01 PM
 */
public class GradleEnvironment {
    public static final boolean DEBUG_GRADLE_HOME_PROCESSING = Boolean.getBoolean("gradle.debug.home.processing");
    @Deprecated
    public static final boolean DISABLE_ENHANCED_TOOLING_API = Boolean.getBoolean("gradle.disable.enhanced.tooling.api");
    public static final boolean ADJUST_USER_DIR = Boolean.getBoolean("gradle.adjust.userdir");

    private GradleEnvironment() {
    }
}
