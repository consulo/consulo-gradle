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

import com.intellij.ProjectTopics;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager;
import com.intellij.openapi.externalSystem.util.DisposeAwareProjectChange;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootAdapter;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import consulo.annotation.access.RequiredReadAction;
import consulo.editor.notifications.EditorNotificationProvider;
import consulo.ui.annotation.RequiredUIAccess;
import org.gradle.util.GUtil;
import org.gradle.wrapper.WrapperConfiguration;
import org.gradle.wrapper.WrapperExecutor;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import javax.annotation.Nonnull;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * @author Vladislav.Soroka
 * @since 9/13/13
 */
public class UseDistributionWithSourcesNotificationProvider implements EditorNotificationProvider<EditorNotificationPanel>
{
	public static final Pattern GRADLE_SRC_DISTRIBUTION_PATTERN;
	private static final Logger LOG = Logger.getInstance("#" + UseDistributionWithSourcesNotificationProvider.class.getName());
	private static final String ALL_ZIP_DISTRIBUTION_URI_SUFFIX = "-all.zip";
	private final Project myProject;

	static
	{
		GRADLE_SRC_DISTRIBUTION_PATTERN = Pattern.compile("https?\\\\?://services\\.gradle\\.org.*" + ALL_ZIP_DISTRIBUTION_URI_SUFFIX);
	}

	public UseDistributionWithSourcesNotificationProvider(Project project, final EditorNotifications notifications)
	{
		myProject = project;
		project.getMessageBus().connect(project).subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter()
		{
			@Override
			public void rootsChanged(ModuleRootEvent event)
			{
				notifications.updateAllNotifications();
			}
		});
	}

	@RequiredReadAction
	@Override
	public EditorNotificationPanel createNotificationPanel(@Nonnull VirtualFile file, @Nonnull FileEditor fileEditor)
	{
		try
		{
			if(GradleConstants.DEFAULT_SCRIPT_NAME.equals(file.getName()) || GradleConstants.SETTINGS_FILE_NAME.equals(file.getName()))
			{

				final Module module = ModuleUtilCore.findModuleForFile(file, myProject);
				if(module == null)
				{
					return null;
				}
				final String rootProjectPath = getRootProjectPath(module);
				if(rootProjectPath == null)
				{
					return null;
				}
				final GradleProjectSettings settings = GradleSettings.getInstance(module.getProject()).getLinkedProjectSettings(rootProjectPath);
				if(settings == null || settings.getDistributionType() != DistributionType.DEFAULT_WRAPPED)
				{
					return null;
				}
				if(settings.isDisableWrapperSourceDistributionNotification())
				{
					return null;
				}
				if(!showUseDistributionWithSourcesTip(rootProjectPath))
				{
					return null;
				}

				final EditorNotificationPanel panel = new EditorNotificationPanel();
				panel.setText(GradleBundle.message("gradle.notifications.use.distribution.with.sources"));
				panel.createActionLabel(GradleBundle.message("gradle.notifications.hide.tip"), () ->
				{
					settings.setDisableWrapperSourceDistributionNotification(true);
					EditorNotifications.getInstance(module.getProject()).updateAllNotifications();
				});
				panel.createActionLabel(GradleBundle.message("gradle.notifications.apply.suggestion"), () ->
				{
					updateDefaultWrapperConfiguration(rootProjectPath);
					EditorNotifications.getInstance(module.getProject()).updateAllNotifications();
					final ProjectDataManager projectDataManager = ServiceManager.getService(ProjectDataManager.class);
					ExternalSystemUtil.refreshProject(module.getProject(), GradleConstants.SYSTEM_ID, settings.getExternalProjectPath(), new
							ExternalProjectRefreshCallback()
					{
						@Override
						public void onSuccess(@javax.annotation.Nullable final DataNode<ProjectData> externalProject)
						{
							if(externalProject == null)
							{
								return;
							}
							ExternalSystemApiUtil.executeProjectChangeAction(true, new DisposeAwareProjectChange(module.getProject())
							{
								@RequiredUIAccess
								@Override
								public void execute()
								{
									ProjectRootManagerEx.getInstanceEx(module.getProject()).mergeRootsChangesDuring(() -> projectDataManager
											.importData(externalProject.getKey(), Collections.singleton(externalProject), module.getProject(),
													true));
								}
							});
						}

						@Override
						public void onFailure(@Nonnull String errorMessage, @javax.annotation.Nullable String errorDetails)
						{
						}
					}, true, ProgressExecutionMode.START_IN_FOREGROUND_ASYNC);
				});
				return panel;
			}
		}
		catch(ProcessCanceledException | IndexNotReadyException ignored)
		{
		}

		return null;
	}

	private static void updateDefaultWrapperConfiguration(@Nonnull String linkedProjectPath)
	{
		try
		{
			final File wrapperPropertiesFile = GradleUtil.findDefaultWrapperPropertiesFile(linkedProjectPath);
			if(wrapperPropertiesFile == null)
			{
				return;
			}
			final WrapperConfiguration wrapperConfiguration = GradleUtil.getWrapperConfiguration(linkedProjectPath);
			if(wrapperConfiguration == null)
			{
				return;
			}
			String currentDistributionUri = wrapperConfiguration.getDistribution().toString();
			if(StringUtil.endsWith(currentDistributionUri, ALL_ZIP_DISTRIBUTION_URI_SUFFIX))
			{
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
		catch(Exception e)
		{
			LOG.error(e);
		}
	}

	private static boolean showUseDistributionWithSourcesTip(String linkedProjectPath)
	{
		WrapperConfiguration wrapperConfiguration = GradleUtil.getWrapperConfiguration(linkedProjectPath);
		// currently only wrapped distribution takes into account
		if(wrapperConfiguration == null)
		{
			return true;
		}
		String distributionUri = wrapperConfiguration.getDistribution().toString();
		try
		{
			String host = new URI(distributionUri).getHost();
			return host != null && host.endsWith("gradle.org") && !GRADLE_SRC_DISTRIBUTION_PATTERN.matcher(distributionUri).matches();
		}
		catch(URISyntaxException ignore)
		{
		}
		return false;
	}

	@javax.annotation.Nullable
	private static String getRootProjectPath(@Nonnull Module module)
	{
		String externalSystemId = ExternalSystemApiUtil.getExtensionSystemOption(module, ExternalSystemConstants.EXTERNAL_SYSTEM_ID_KEY);
		if(externalSystemId == null || !GradleConstants.SYSTEM_ID.toString().equals(externalSystemId))
		{
			return null;
		}

		String path = ExternalSystemApiUtil.getExtensionSystemOption(module, ExternalSystemConstants.ROOT_PROJECT_PATH_KEY);
		return StringUtil.isEmpty(path) ? null : path;
	}
}
