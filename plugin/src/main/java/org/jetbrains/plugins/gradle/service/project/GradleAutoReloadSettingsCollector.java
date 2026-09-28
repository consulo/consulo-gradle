// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;

import java.nio.file.Path;
import java.util.List;

/**
 * Allows providing Gradle scripts for auto-reload tracking.
 * For example, some Gradle plugins may define new script files which affect Gradle loading into the IDE.
 */
@ExtensionAPI(ComponentScope.PROJECT)
public interface GradleAutoReloadSettingsCollector {
    /**
     * Collects settings files which will be watched.
     * This property can be called from any thread context to reduce UI freezes and CPU usage.
     * Result will be cached, so settings files should be equals between reloads.
     *
     * @see consulo.externalSystem.autoimport.ExternalSystemProjectAware#getSettingsFiles
     * @see GradleAutoImportAware#getAffectedExternalProjectFilePaths
     */
    List<Path> collectSettingsFiles(GradleProjectSettings projectSettings);
}
