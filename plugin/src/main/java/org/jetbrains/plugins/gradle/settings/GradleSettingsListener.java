package org.jetbrains.plugins.gradle.settings;

import javax.annotation.Nonnull;

import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsListener;
import com.intellij.util.messages.Topic;

import javax.annotation.Nullable;

/**
 * Defines callback for the gradle config structure change.
 * <p/>
 * Implementations of this interface are not obliged to be thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 1/17/12 1:02 PM
 */
public interface GradleSettingsListener extends ExternalSystemSettingsListener<GradleProjectSettings> {

  Topic<GradleSettingsListener> TOPIC = Topic.create("Gradle-specific settings", GradleSettingsListener.class);

  /**
   * Is expected to be invoked when gradle home path is changed.
   * <p/>
   * <b>Note:</b> this callback is executed <b>after</b> the actual config change.
   *
   * @param oldPath            old path (if any)
   * @param newPath            new path (if any)
   * @param linkedProjectPath  target linked gradle project path
   */
  void onGradleHomeChange(@javax.annotation.Nullable String oldPath, @javax.annotation.Nullable String newPath, @Nonnull String linkedProjectPath);

  /**
   * Is expected to be invoked when 'gradle distribution type' setting is changed (generally this
   * switches tooling api to different gradle version).
   * <p/>
   * <b>Note:</b> this callback is executed <b>after</b> the actual config change.
   *
   * @param currentValue       current value
   * @param linkedProjectPath  target linked gradle project path
   */
  void onGradleDistributionTypeChange(DistributionType currentValue, @Nonnull String linkedProjectPath);

  /**
   * Is expected to be invoked when service directory path is changed.
   * <p/>
   * <b>Note:</b> this callback is executed <b>after</b> the actual config change.
   *
   * @param oldPath  old path (if any)
   * @param newPath  new path (if any)
   * @see GradleSettings#getServiceDirectoryPath() 
   */
  void onServiceDirectoryPathChange(@Nullable String oldPath, @Nullable String newPath);

  /**
   * Is expected to be called when gradle JVM options are changed by end-user.
   * 
   * @param oldOptions  old options (if any)
   * @param newOptions  new option (if any)
   */
  void onGradleVmOptionsChange(@Nullable String oldOptions, @javax.annotation.Nullable String newOptions);
}
