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
package org.jetbrains.plugins.gradle.service.project;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import consulo.gradle.importProvider.GradleModuleImportProvider;
import consulo.moduleImport.ModuleImportContext;
import consulo.moduleImport.ModuleImportProvider;
import consulo.moduleImport.ui.ModuleImportProcessor;
import consulo.ui.UIAccess;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.concurrent.AsyncResult;
import consulo.util.lang.Pair;
import org.jetbrains.plugins.gradle.service.GradleBuildClasspathManager;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import javax.annotation.Nonnull;
import javax.swing.event.HyperlinkEvent;
import java.io.File;
import java.io.FilenameFilter;
import java.util.Collections;

/**
 * @author Vladislav.Soroka
 * @since 12/10/13
 */
public class GradleStartupActivity implements StartupActivity
{
	private static final String SHOW_UNLINKED_GRADLE_POPUP = "show.inlinked.gradle.project.popup";
	private static final String IMPORT_EVENT_DESCRIPTION = "import";
	private static final String DO_NOT_SHOW_EVENT_DESCRIPTION = "do.not.show";

	@Override
	public void runActivity(@Nonnull UIAccess uiAccess, @Nonnull Project project)
	{
		configureBuildClasspath(project);

		showNotificationForUnlinkedGradleProject(project);
	}

	private static void configureBuildClasspath(@Nonnull final Project project)
	{
		GradleBuildClasspathManager.getInstance(project).reload();
	}

	private static void showNotificationForUnlinkedGradleProject(@Nonnull final Project project)
	{
		if(!PropertiesComponent.getInstance(project).getBoolean(SHOW_UNLINKED_GRADLE_POPUP, true)
				|| !GradleSettings.getInstance(project).getLinkedProjectsSettings().isEmpty()
				|| project.getUserData(ExternalSystemDataKeys.NEWLY_IMPORTED_PROJECT) == Boolean.TRUE
				|| project.getBaseDir() == null)
		{
			return;
		}

		File baseDir = VfsUtilCore.virtualToIoFile(project.getBaseDir());
		final File[] files = baseDir.listFiles(new FilenameFilter()
		{
			@Override
			public boolean accept(File dir, String name)
			{
				return FileUtil.namesEqual(GradleConstants.DEFAULT_SCRIPT_NAME, name);
			}
		});

		if(files != null && files.length != 0)
		{
			String message = String.format("%s<br>\n%s",
					GradleBundle.message("gradle.notifications.unlinked.project.found.msg", IMPORT_EVENT_DESCRIPTION),
					GradleBundle.message("gradle.notifications.do.not.show", DO_NOT_SHOW_EVENT_DESCRIPTION));

			GradleNotification.getInstance(project).showBalloon(
					GradleBundle.message("gradle.notifications.unlinked.project.found.title"),
					message, NotificationType.INFORMATION, new NotificationListener.Adapter()
					{
						@Override
						@RequiredUIAccess
						protected void hyperlinkActivated(@Nonnull Notification notification, @Nonnull HyperlinkEvent e)
						{
							if(IMPORT_EVENT_DESCRIPTION.equals(e.getDescription()))
							{
								VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(files[0]);
								assert vFile != null;
								AsyncResult<Pair<ModuleImportContext, ModuleImportProvider<ModuleImportContext>>> result = AsyncResult.undefined();
								ModuleImportProcessor.showImportChooser(project, vFile, Collections.<ModuleImportProvider>singletonList(GradleModuleImportProvider.getInstance()), result);

								result.doWhenDone(pair -> {
									ModuleImportContext context = pair.getFirst();
									ModuleImportProvider<ModuleImportContext> provider = pair.getSecond();

									ModifiableModuleModel modifiableModel = ModuleManager.getInstance(project).getModifiableModel();
									provider.process(context, project, modifiableModel, module -> {
									});
									WriteAction.runAndWait(modifiableModel::commit);
								});
							}
							else if(DO_NOT_SHOW_EVENT_DESCRIPTION.equals(e.getDescription()))
							{
								PropertiesComponent.getInstance(project).setValue(SHOW_UNLINKED_GRADLE_POPUP, Boolean.FALSE.toString());
							}
						}
					}
			);
		}
	}
}
