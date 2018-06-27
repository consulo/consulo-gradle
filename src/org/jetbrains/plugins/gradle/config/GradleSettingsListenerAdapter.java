package org.jetbrains.plugins.gradle.config;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettingsListener;
import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsListenerAdapter;

/**
 * @author Denis Zhdanov
 * @since 3/13/12 3:53 PM
 */
public abstract class GradleSettingsListenerAdapter extends ExternalSystemSettingsListenerAdapter<GradleProjectSettings>
  implements GradleSettingsListener
{

  @Override
  public void onGradleHomeChange(@javax.annotation.Nullable String oldPath, @Nullable String newPath, @Nonnull String linkedProjectPath) {
  }

  @Override
  public void onGradleDistributionTypeChange(DistributionType currentValue, @Nonnull String linkedProjectPath) {
  }

  @Override
  public void onServiceDirectoryPathChange(@javax.annotation.Nullable String oldPath, @javax.annotation.Nullable String newPath) {
  }

  @Override
  public void onGradleVmOptionsChange(@javax.annotation.Nullable String oldOptions, @javax.annotation.Nullable String newOptions) {
  }
}
