/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.gradle;

import com.intellij.java.language.LanguageLevel;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.application.util.AtomicNotNullLazyValue;
import consulo.application.util.NotNullLazyValue;
import consulo.component.extension.ExtensionPointCacheKey;
import consulo.component.messagebus.MessageBusConnection;
import consulo.configurable.Configurable;
import consulo.content.bundle.Sdk;
import consulo.content.bundle.SdkTable;
import consulo.externalSystem.ExternalSystemAutoImportAware;
import consulo.externalSystem.ExternalSystemConfigurableAware;
import consulo.externalSystem.ExternalSystemManager;
import consulo.externalSystem.model.DataNode;
import consulo.externalSystem.model.ProjectSystemId;
import consulo.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import consulo.externalSystem.model.execution.ExternalTaskExecutionInfo;
import consulo.externalSystem.model.execution.ExternalTaskPojo;
import consulo.externalSystem.model.project.ExternalProjectPojo;
import consulo.externalSystem.model.task.ProgressExecutionMode;
import consulo.externalSystem.service.project.ExternalSystemProjectResolver;
import consulo.externalSystem.service.project.ProjectData;
import consulo.externalSystem.task.ExternalSystemTaskManager;
import consulo.externalSystem.ui.ExternalSystemUiAware;
import consulo.externalSystem.util.DisposeAwareProjectChange;
import consulo.externalSystem.util.ExternalSystemApiUtil;
import consulo.externalSystem.util.ExternalSystemConstants;
import consulo.fileChooser.FileChooserDescriptor;
import consulo.gradle.icon.GradleIconGroup;
import consulo.gradle.setting.ClassHolder;
import consulo.gradle.setting.DistributionType;
import consulo.gradle.setting.GradleExecutionSettings;
import consulo.ide.ServiceManager;
import consulo.ide.impl.idea.openapi.externalSystem.service.project.ExternalProjectRefreshCallback;
import consulo.ide.impl.idea.openapi.externalSystem.service.project.autoimport.CachingExternalSystemAutoImportAware;
import consulo.ide.impl.idea.openapi.externalSystem.service.project.manage.ProjectDataManager;
import consulo.ide.impl.idea.openapi.externalSystem.util.ExternalSystemUtil;
import consulo.ide.impl.idea.util.PathUtil;
import consulo.java.execution.impl.util.JreSearchUtil;
import consulo.logging.Logger;
import consulo.module.content.ProjectRootManager;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.process.ExecutionException;
import consulo.process.cmd.SimpleJavaParameters;
import consulo.project.Project;
import consulo.project.startup.StartupActivity;
import consulo.ui.UIAccess;
import consulo.ui.image.Image;
import consulo.util.collection.ContainerUtil;
import consulo.util.io.FileUtil;
import consulo.util.lang.Pair;
import consulo.virtualFileSystem.util.PathsList;
import jakarta.inject.Inject;
import org.jetbrains.plugins.gradle.config.GradleSettingsListenerAdapter;
import org.jetbrains.plugins.gradle.service.GradleInstallationManager;
import org.jetbrains.plugins.gradle.service.project.GradleAutoImportAware;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolver;
import consulo.gradle.service.project.GradleProjectResolverExtension;
import org.jetbrains.plugins.gradle.service.settings.GradleConfigurable;
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager;
import org.jetbrains.plugins.gradle.settings.*;
import consulo.gradle.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.function.Function;

/**
 * @author Denis Zhdanov
 * @since 4/10/13 1:19 PM
 */
@ExtensionImpl
public class GradleManager implements ExternalSystemConfigurableAware, ExternalSystemUiAware, ExternalSystemAutoImportAware, StartupActivity,
  ExternalSystemManager<GradleProjectSettings, GradleSettingsListener, GradleSettings, GradleLocalSettings, GradleExecutionSettings> {

  private static final Logger LOG = Logger.getInstance(GradleManager.class);

  @Nonnull
  private final ExternalSystemAutoImportAware myAutoImportDelegate = new CachingExternalSystemAutoImportAware(new GradleAutoImportAware());

  @Nonnull
  private final GradleInstallationManager myInstallationManager;

  @Inject
  public GradleManager(@Nonnull GradleInstallationManager manager) {
    myInstallationManager = manager;
  }

  @Nonnull
  @Override
  public ProjectSystemId getSystemId() {
    return GradleConstants.SYSTEM_ID;
  }

  @Nonnull
  @Override
  public Function<Project, GradleSettings> getSettingsProvider() {
    return GradleSettings::getInstance;
  }

  @Nonnull
  @Override
  public Function<Project, GradleLocalSettings> getLocalSettingsProvider() {
    return GradleLocalSettings::getInstance;
  }

  @Nonnull
  @Override
  public Function<Pair<Project, String>, GradleExecutionSettings> getExecutionSettingsProvider() {
    return pair -> {
      GradleSettings settings = GradleSettings.getInstance(pair.first);
      File gradleHome = myInstallationManager.getGradleHome(pair.first, pair.second);
      String localGradlePath = null;
      if (gradleHome != null) {
        try {
          // Try to resolve symbolic links as there were problems with them at the gradle side.
          localGradlePath = gradleHome.getCanonicalPath();
        }
        catch (IOException e) {
          localGradlePath = gradleHome.getAbsolutePath();
        }
      }

      GradleProjectSettings projectLevelSettings = settings.getLinkedProjectSettings(pair.second);
      final DistributionType distributionType;
      if (projectLevelSettings == null) {
        distributionType = GradleUtil.isGradleDefaultWrapperFilesExist(pair.second) ? DistributionType.DEFAULT_WRAPPED :
          DistributionType.LOCAL;
      }
      else {
        distributionType = projectLevelSettings.getDistributionType() == null ? DistributionType.LOCAL : projectLevelSettings
          .getDistributionType();
      }

      GradleExecutionSettings result = new GradleExecutionSettings(localGradlePath, settings.getServiceDirectoryPath(), distributionType,
                                                                   settings.getGradleVmOptions(), settings.isOfflineWork());

      for (GradleProjectResolverExtension extension : Application.get().getExtensionList(GradleProjectResolverExtension.class)) {
        result.addResolverExtensionClass(ClassHolder.from(extension.getClass()));
      }

      Sdk targetJre = JreSearchUtil.findSdkOfLevel(SdkTable.getInstance(),
                                                   LanguageLevel.JDK_1_8,
                                                   projectLevelSettings == null ? null : projectLevelSettings.getJreName());
      if (targetJre != null) {
        LOG.info("Instructing gradle to use java from " + targetJre.getHomePath());
      }
      result.setJavaHome(targetJre == null ? null : targetJre.getHomePath());
      return result;
    };
  }

  @Override
  public void enhanceRemoteProcessing(@Nonnull SimpleJavaParameters parameters) throws ExecutionException {
    final Set<String> additionalEntries = new HashSet<>();
    for (GradleProjectResolverExtension extension : Application.get().getExtensionList(GradleProjectResolverExtension.class)) {
      ContainerUtil.addIfNotNull(additionalEntries, PathUtil.getJarPathForClass(extension.getClass()));
      for (Class aClass : extension.getExtraProjectModelClasses()) {
        ContainerUtil.addIfNotNull(additionalEntries, PathUtil.getJarPathForClass(aClass));
      }
      extension.enhanceRemoteProcessing(parameters);
    }

    final PathsList classPath = parameters.getClassPath();
    for (String entry : additionalEntries) {
      classPath.add(entry);
    }

    parameters.getVMParametersList().addProperty(ExternalSystemConstants.EXTERNAL_SYSTEM_ID_KEY, GradleConstants.SYSTEM_ID.getId());
  }

  @Override
  public void enhanceLocalProcessing(@Nonnull List<URL> urls) {
  }

  @Nonnull
  @Override
  public Class<? extends ExternalSystemProjectResolver<GradleExecutionSettings>> getProjectResolverClass() {
    return GradleProjectResolver.class;
  }

  @Override
  public Class<? extends ExternalSystemTaskManager<GradleExecutionSettings>> getTaskManagerClass() {
    return GradleTaskManager.class;
  }

  @Nonnull
  @Override
  public Configurable getConfigurable(@Nonnull Project project) {
    return new GradleConfigurable(project);
  }

  @Nullable
  @Override
  public FileChooserDescriptor getExternalProjectConfigDescriptor() {
    return GradleUtil.getGradleProjectFileChooserDescriptor();
  }

  @Nullable
  @Override
  public Image getProjectIcon() {
    return GradleIconGroup.gradle();
  }

  @Nullable
  @Override
  public Image getTaskIcon() {
    return PlatformIconGroup.nodesTask();
  }

  @Nonnull
  @Override
  public String getProjectRepresentationName(@Nonnull String targetProjectPath, @Nullable String rootProjectPath) {
    return ExternalSystemApiUtil.getProjectRepresentationName(targetProjectPath, rootProjectPath);
  }

  @Nullable
  @Override
  public String getAffectedExternalProjectPath(@Nonnull String changedFileOrDirPath, @Nonnull Project project) {
    return myAutoImportDelegate.getAffectedExternalProjectPath(changedFileOrDirPath, project);
  }

  @Nonnull
  @Override
  public FileChooserDescriptor getExternalProjectDescriptor() {
    return GradleUtil.getGradleProjectFileChooserDescriptor();
  }

  @Override
  public void runActivity(@Nonnull final Project project, UIAccess uiAccess) {
    // We want to automatically refresh linked projects on gradle service directory change.
    MessageBusConnection connection = project.getMessageBus().connect(project);
    connection.subscribe(GradleSettings.getInstance(project).getChangesTopic(), new GradleSettingsListenerAdapter() {

      @Override
      public void onServiceDirectoryPathChange(@Nullable String oldPath, @Nullable String newPath) {
        ensureProjectsRefresh();
      }

      @Override
      public void onGradleHomeChange(@Nullable String oldPath,
                                     @Nullable String newPath,
                                     @Nonnull String linkedProjectPath) {
        ensureProjectsRefresh();
      }

      @Override
      public void onGradleDistributionTypeChange(DistributionType currentValue, @Nonnull String linkedProjectPath) {
        ensureProjectsRefresh();
      }

      @Override
      public void onProjectsLinked(@Nonnull Collection<GradleProjectSettings> settings) {
        final ProjectDataManager projectDataManager = ServiceManager.getService(ProjectDataManager.class);
        for (GradleProjectSettings gradleProjectSettings : settings) {
          ExternalSystemUtil.refreshProject(project,
                                            GradleConstants.SYSTEM_ID,
                                            gradleProjectSettings.getExternalProjectPath(),
                                            new ExternalProjectRefreshCallback() {
                                              @Override
                                              public void onSuccess(@Nullable final DataNode<ProjectData> externalProject) {
                                                if (externalProject == null) {
                                                  return;
                                                }
                                                ExternalSystemApiUtil.executeProjectChangeAction(true,
                                                                                                 new DisposeAwareProjectChange(project) {
                                                                                                   @Override
                                                                                                   public void execute() {
                                                                                                     ProjectRootManager.getInstance(
                                                                                                       project).mergeRootsChangesDuring(
                                                                                                       new Runnable() {
                                                                                                         @Override
                                                                                                         public void run() {
                                                                                                           projectDataManager
                                                                                                             .importData(
                                                                                                               externalProject
                                                                                                                 .getKey(),
                                                                                                               Collections
                                                                                                                 .singleton(
                                                                                                                   externalProject),
                                                                                                               project,
                                                                                                               true);
                                                                                                         }
                                                                                                       });
                                                                                                   }
                                                                                                 });
                                              }

                                              @Override
                                              public void onFailure(@Nonnull String errorMessage, @Nullable String errorDetails) {
                                              }
                                            },
                                            false,
                                            ProgressExecutionMode.MODAL_SYNC);
        }
      }

      private void ensureProjectsRefresh() {
        ExternalSystemUtil.refreshProjects(project, GradleConstants.SYSTEM_ID, true);
      }
    });

    // We used to assume that gradle scripts are always named 'build.gradle' and kept path to that build.gradle file at ide settings.
    // However, it was found out that that is incorrect assumption (IDEA-109064). Now we keep paths to gradle script's directories
    // instead. However, we don't want to force old users to re-import gradle projects because of that. That's why we check gradle
    // config and re-point it from build.gradle to the parent dir if necessary.
    Map<String, String> adjustedPaths = patchLinkedProjects(project);
    if (adjustedPaths == null) {
      return;
    }

    GradleLocalSettings localSettings = GradleLocalSettings.getInstance(project);
    patchRecentTasks(adjustedPaths, localSettings);
    patchAvailableProjects(adjustedPaths, localSettings);
    patchAvailableTasks(adjustedPaths, localSettings);
  }

  @Nullable
  private static Map<String, String> patchLinkedProjects(@Nonnull Project project) {
    GradleSettings settings = GradleSettings.getInstance(project);
    Collection<GradleProjectSettings> correctedSettings = new ArrayList<>();
    Map<String/* old path */, String/* new path */> adjustedPaths = new HashMap<>();
    for (GradleProjectSettings projectSettings : settings.getLinkedProjectsSettings()) {
      String oldPath = projectSettings.getExternalProjectPath();
      if (oldPath != null && new File(oldPath).isFile() && FileUtil.extensionEquals(oldPath, GradleConstants.EXTENSION)) {
        try {
          String newPath = new File(oldPath).getParentFile().getCanonicalPath();
          projectSettings.setExternalProjectPath(newPath);
          adjustedPaths.put(oldPath, newPath);
        }
        catch (IOException e) {
          LOG.warn(String.format("Unexpected exception occurred on attempt to re-point linked gradle project path from build.gradle to its" +
                                   " parent dir. Path: %s", oldPath), e);
        }
      }
      correctedSettings.add(projectSettings);
    }
    if (adjustedPaths.isEmpty()) {
      return null;
    }

    settings.setLinkedProjectsSettings(correctedSettings);
    return adjustedPaths;
  }

  private static void patchAvailableTasks(@Nonnull Map<String, String> adjustedPaths, @Nonnull GradleLocalSettings localSettings) {
    Map<String, Collection<ExternalTaskPojo>> adjustedAvailableTasks = new HashMap<>();
    for (Map.Entry<String, Collection<ExternalTaskPojo>> entry : localSettings.getAvailableTasks().entrySet()) {
      String newPath = adjustedPaths.get(entry.getKey());
      if (newPath == null) {
        adjustedAvailableTasks.put(entry.getKey(), entry.getValue());
      }
      else {
        for (ExternalTaskPojo task : entry.getValue()) {
          String newTaskPath = adjustedPaths.get(task.getLinkedExternalProjectPath());
          if (newTaskPath != null) {
            task.setLinkedExternalProjectPath(newTaskPath);
          }
        }
        adjustedAvailableTasks.put(newPath, entry.getValue());
      }
    }
    localSettings.setAvailableTasks(adjustedAvailableTasks);
  }

  private static void patchAvailableProjects(@Nonnull Map<String, String> adjustedPaths, @Nonnull GradleLocalSettings localSettings) {
    Map<ExternalProjectPojo, Collection<ExternalProjectPojo>> adjustedAvailableProjects = new HashMap<>();
    for (Map.Entry<ExternalProjectPojo, Collection<ExternalProjectPojo>> entry : localSettings.getAvailableProjects().entrySet()) {
      String newPath = adjustedPaths.get(entry.getKey().getPath());
      if (newPath == null) {
        adjustedAvailableProjects.put(entry.getKey(), entry.getValue());
      }
      else {
        adjustedAvailableProjects.put(new ExternalProjectPojo(entry.getKey().getName(), newPath), entry.getValue());
      }
    }
    localSettings.setAvailableProjects(adjustedAvailableProjects);
  }

  private static void patchRecentTasks(@Nonnull Map<String, String> adjustedPaths, @Nonnull GradleLocalSettings localSettings) {
    for (ExternalTaskExecutionInfo taskInfo : localSettings.getRecentTasks()) {
      ExternalSystemTaskExecutionSettings s = taskInfo.getSettings();
      String newPath = adjustedPaths.get(s.getExternalProjectPath());
      if (newPath != null) {
        s.setExternalProjectPath(newPath);
      }
    }
  }
}
