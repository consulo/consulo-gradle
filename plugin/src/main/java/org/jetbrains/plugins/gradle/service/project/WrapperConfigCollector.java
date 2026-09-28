// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project;

import consulo.annotation.component.ExtensionImpl;
import consulo.gradle.setting.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

@ExtensionImpl
public final class WrapperConfigCollector implements GradleAutoReloadSettingsCollector {
    @Override
    public List<Path> collectSettingsFiles(GradleProjectSettings projectSettings) {
        if (projectSettings.getDistributionType() == DistributionType.DEFAULT_WRAPPED) {
            Path projectPath = Path.of(projectSettings.getExternalProjectPath());
            return Collections.singletonList(projectPath.resolve("gradle/wrapper/gradle-wrapper.properties"));
        }
        return Collections.emptyList();
    }
}
