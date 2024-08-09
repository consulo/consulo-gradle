package org.jetbrains.plugins.gradle.config;

import consulo.ide.impl.idea.openapi.externalSystem.settings.ExternalSystemSettingsListenerAdapter;
import consulo.gradle.setting.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettingsListener;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author Denis Zhdanov
 * @since 2012-03-13
 */
public abstract class GradleSettingsListenerAdapter extends ExternalSystemSettingsListenerAdapter<GradleProjectSettings>
    implements GradleSettingsListener {

    @Override
    public void onGradleHomeChange(@Nullable String oldPath, @Nullable String newPath, @Nonnull String linkedProjectPath) {
    }

    @Override
    public void onGradleDistributionTypeChange(DistributionType currentValue, @Nonnull String linkedProjectPath) {
    }

    @Override
    public void onServiceDirectoryPathChange(@Nullable String oldPath, @Nullable String newPath) {
    }

    @Override
    public void onGradleVmOptionsChange(@Nullable String oldOptions, @Nullable String newOptions) {
    }
}
