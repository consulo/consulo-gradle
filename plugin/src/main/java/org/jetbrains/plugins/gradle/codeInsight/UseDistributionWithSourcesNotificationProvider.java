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
package org.jetbrains.plugins.gradle.codeInsight;

import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.IndexNotReadyException;
import consulo.component.ProcessCanceledException;
import consulo.externalSystem.model.DataNode;
import consulo.externalSystem.model.task.ProgressExecutionMode;
import consulo.externalSystem.service.project.ProjectData;
import consulo.externalSystem.util.DisposeAwareProjectChange;
import consulo.externalSystem.util.ExternalSystemApiUtil;
import consulo.externalSystem.util.ExternalSystemConstants;
import consulo.fileEditor.EditorNotificationBuilder;
import consulo.fileEditor.EditorNotificationProvider;
import consulo.fileEditor.EditorNotifications;
import consulo.fileEditor.FileEditor;
import consulo.gradle.GradleConstants;
import consulo.gradle.localize.GradleLocalize;
import consulo.gradle.setting.DistributionType;
import consulo.ide.ServiceManager;
import consulo.ide.impl.idea.openapi.externalSystem.service.project.ExternalProjectRefreshCallback;
import consulo.ide.impl.idea.openapi.externalSystem.service.project.manage.ProjectDataManager;
import consulo.ide.impl.idea.openapi.externalSystem.util.ExternalSystemUtil;
import consulo.language.util.ModuleUtilCore;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.inject.Inject;
import org.gradle.util.GUtil;
import org.gradle.wrapper.WrapperConfiguration;
import org.gradle.wrapper.WrapperExecutor;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Properties;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * @author Vladislav.Soroka
 * @since 9/13/13
 */
@ExtensionImpl
public class UseDistributionWithSourcesNotificationProvider implements EditorNotificationProvider {
    public static final Pattern GRADLE_SRC_DISTRIBUTION_PATTERN;
    private static final Logger LOG = Logger.getInstance(UseDistributionWithSourcesNotificationProvider.class);
    private static final String ALL_ZIP_DISTRIBUTION_URI_SUFFIX = "-all.zip";
    private final Project myProject;

    static {
        GRADLE_SRC_DISTRIBUTION_PATTERN = Pattern.compile("https?\\\\?://services\\.gradle\\.org.*" + ALL_ZIP_DISTRIBUTION_URI_SUFFIX);
    }

    @Inject
    public UseDistributionWithSourcesNotificationProvider(Project project) {
        myProject = project;
    }

    @Nonnull
    @Override
    public String getId() {
        return "gradle-use-distribution-with-source";
    }

    @RequiredReadAction
    @Nullable
    @Override
    public EditorNotificationBuilder buildNotification(
        @Nonnull VirtualFile file,
        @Nonnull FileEditor fileEditor,
        @Nonnull Supplier<EditorNotificationBuilder> supplier
    ) {
        try {
            if (GradleConstants.DEFAULT_SCRIPT_NAME.equals(file.getName()) || GradleConstants.SETTINGS_FILE_NAME.equals(file.getName())) {

                final Module module = ModuleUtilCore.findModuleForFile(file, myProject);
                if (module == null) {
                    return null;
                }
                final String rootProjectPath = getRootProjectPath(module);
                if (rootProjectPath == null) {
                    return null;
                }
                final GradleProjectSettings settings =
                    GradleSettings.getInstance(module.getProject()).getLinkedProjectSettings(rootProjectPath);
                if (settings == null || settings.getDistributionType() != DistributionType.DEFAULT_WRAPPED) {
                    return null;
                }
                if (settings.isDisableWrapperSourceDistributionNotification()) {
                    return null;
                }
                if (!showUseDistributionWithSourcesTip(rootProjectPath)) {
                    return null;
                }

                final EditorNotificationBuilder panel = supplier.get();
                panel.withText(GradleLocalize.gradleNotificationsUseDistributionWithSources());
                panel.withAction(GradleLocalize.gradleNotificationsHideTip(), (e) ->
                {
                    settings.setDisableWrapperSourceDistributionNotification(true);
                    EditorNotifications.getInstance(module.getProject()).updateAllNotifications();
                });
                panel.withAction(GradleLocalize.gradleNotificationsApplySuggestion(), (e) ->
                {
                    updateDefaultWrapperConfiguration(rootProjectPath);
                    EditorNotifications.getInstance(module.getProject()).updateAllNotifications();
                    final ProjectDataManager projectDataManager = ServiceManager.getService(ProjectDataManager.class);
                    ExternalSystemUtil.refreshProject(module.getProject(), GradleConstants.SYSTEM_ID, settings.getExternalProjectPath(), new
                        ExternalProjectRefreshCallback() {
                            @Override
                            public void onSuccess(@Nullable final DataNode<ProjectData> externalProject) {
                                if (externalProject == null) {
                                    return;
                                }
                                ExternalSystemApiUtil.executeProjectChangeAction(true, new DisposeAwareProjectChange(module.getProject()) {
                                    @RequiredUIAccess
                                    @Override
                                    public void execute() {
                                        ProjectRootManager.getInstance(module.getProject()).mergeRootsChangesDuring(
                                            () -> projectDataManager.importData(
                                                externalProject.getKey(),
                                                Collections.singleton(externalProject),
                                                module.getProject(),
                                                true
                                            )
                                        );
                                    }
                                });
                            }

                            @Override
                            public void onFailure(@Nonnull String errorMessage, @Nullable String errorDetails) {
                            }
                        }, true, ProgressExecutionMode.START_IN_FOREGROUND_ASYNC);
                });
                return panel;
            }
        }
        catch (ProcessCanceledException | IndexNotReadyException ignored) {
        }

        return null;
    }

    private static void updateDefaultWrapperConfiguration(@Nonnull String linkedProjectPath) {
        try {
            final File wrapperPropertiesFile = GradleUtil.findDefaultWrapperPropertiesFile(linkedProjectPath);
            if (wrapperPropertiesFile == null) {
                return;
            }
            final WrapperConfiguration wrapperConfiguration = GradleUtil.getWrapperConfiguration(linkedProjectPath);
            if (wrapperConfiguration == null) {
                return;
            }
            String currentDistributionUri = wrapperConfiguration.getDistribution().toString();
            if (StringUtil.endsWith(currentDistributionUri, ALL_ZIP_DISTRIBUTION_URI_SUFFIX)) {
                return;
            }

            final String distributionUri = currentDistributionUri.substring(0, currentDistributionUri.lastIndexOf('-')) +
                ALL_ZIP_DISTRIBUTION_URI_SUFFIX;

            wrapperConfiguration.setDistribution(new URI(distributionUri));
            Properties wrapperProperties = new Properties();
            wrapperProperties.setProperty(WrapperExecutor.DISTRIBUTION_URL_PROPERTY, wrapperConfiguration.getDistribution().toString());
            wrapperProperties.setProperty(WrapperExecutor.DISTRIBUTION_BASE_PROPERTY, wrapperConfiguration.getDistributionBase());
            wrapperProperties.setProperty(WrapperExecutor.DISTRIBUTION_PATH_PROPERTY, wrapperConfiguration.getDistributionPath());
            wrapperProperties.setProperty(WrapperExecutor.ZIP_STORE_BASE_PROPERTY, wrapperConfiguration.getZipBase());
            wrapperProperties.setProperty(WrapperExecutor.ZIP_STORE_PATH_PROPERTY, wrapperConfiguration.getZipPath());
            GUtil.saveProperties(wrapperProperties, new File(wrapperPropertiesFile.getPath()));
            LocalFileSystem.getInstance().refreshIoFiles(Collections.singletonList(wrapperPropertiesFile));
        }
        catch (Exception e) {
            LOG.error(e);
        }
    }

    private static boolean showUseDistributionWithSourcesTip(String linkedProjectPath) {
        WrapperConfiguration wrapperConfiguration = GradleUtil.getWrapperConfiguration(linkedProjectPath);
        // currently only wrapped distribution takes into account
        if (wrapperConfiguration == null) {
            return true;
        }
        String distributionUri = wrapperConfiguration.getDistribution().toString();
        try {
            String host = new URI(distributionUri).getHost();
            return host != null && host.endsWith("gradle.org") && !GRADLE_SRC_DISTRIBUTION_PATTERN.matcher(distributionUri).matches();
        }
        catch (URISyntaxException ignore) {
        }
        return false;
    }

    @Nullable
    private static String getRootProjectPath(@Nonnull Module module) {
        String externalSystemId = ExternalSystemApiUtil.getExtensionSystemOption(module, ExternalSystemConstants.EXTERNAL_SYSTEM_ID_KEY);
        if (externalSystemId == null || !GradleConstants.SYSTEM_ID.toString().equals(externalSystemId)) {
            return null;
        }

        String path = ExternalSystemApiUtil.getExtensionSystemOption(module, ExternalSystemConstants.ROOT_PROJECT_PATH_KEY);
        return StringUtil.isEmpty(path) ? null : path;
    }
}
