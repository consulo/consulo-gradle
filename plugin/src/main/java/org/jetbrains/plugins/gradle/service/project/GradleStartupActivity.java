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

import consulo.annotation.component.ExtensionImpl;
import consulo.application.WriteAction;
import consulo.externalSystem.model.ExternalSystemDataKeys;
import consulo.gradle.GradleBundle;
import consulo.gradle.GradleConstants;
import consulo.gradle.impl.importProvider.GradleModuleImportProvider;
import consulo.gradle.localize.GradleLocalize;
import consulo.ide.impl.idea.ide.util.PropertiesComponent;
import consulo.ide.impl.idea.openapi.vfs.VfsUtilCore;
import consulo.ide.moduleImport.ModuleImportContext;
import consulo.ide.moduleImport.ModuleImportProcessor;
import consulo.ide.moduleImport.ModuleImportProvider;
import consulo.module.ModifiableModuleModel;
import consulo.module.ModuleManager;
import consulo.project.Project;
import consulo.project.startup.PostStartupActivity;
import consulo.project.ui.notification.Notification;
import consulo.project.ui.notification.NotificationType;
import consulo.project.ui.notification.event.NotificationListener;
import consulo.ui.UIAccess;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.concurrent.AsyncResult;
import consulo.util.io.FileUtil;
import consulo.util.lang.Pair;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.plugins.gradle.service.GradleBuildClasspathManager;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import javax.annotation.Nonnull;
import javax.swing.event.HyperlinkEvent;
import java.io.File;
import java.util.Collections;

/**
 * @author Vladislav.Soroka
 * @since 2013-12-10
 */
@ExtensionImpl
public class GradleStartupActivity implements PostStartupActivity {
    private static final String SHOW_UNLINKED_GRADLE_POPUP = "show.inlinked.gradle.project.popup";
    private static final String IMPORT_EVENT_DESCRIPTION = "import";
    private static final String DO_NOT_SHOW_EVENT_DESCRIPTION = "do.not.show";

    @Override
    public void runActivity(@Nonnull Project project, @Nonnull UIAccess uiAccess) {
        configureBuildClasspath(project);

        showNotificationForUnlinkedGradleProject(project);
    }

    private static void configureBuildClasspath(@Nonnull final Project project) {
        GradleBuildClasspathManager.getInstance(project).reload();
    }

    private static void showNotificationForUnlinkedGradleProject(@Nonnull final Project project) {
        if (!PropertiesComponent.getInstance(project).getBoolean(SHOW_UNLINKED_GRADLE_POPUP, true)
            || !GradleSettings.getInstance(project).getLinkedProjectsSettings().isEmpty()
            || project.getUserData(ExternalSystemDataKeys.NEWLY_IMPORTED_PROJECT) == Boolean.TRUE
            || project.getBaseDir() == null) {
            return;
        }

        File baseDir = VfsUtilCore.virtualToIoFile(project.getBaseDir());
        final File[] files = baseDir.listFiles((dir, name) -> FileUtil.namesEqual(GradleConstants.DEFAULT_SCRIPT_NAME, name));

        if (files != null && files.length != 0) {
            String message = String.format(
                "%s<br>\n%s",
                GradleLocalize.gradleNotificationsUnlinkedProjectFoundMsg(IMPORT_EVENT_DESCRIPTION).get(),
                GradleBundle.message("gradle.notifications.do.not.show", DO_NOT_SHOW_EVENT_DESCRIPTION)
            );

            GradleNotification.getInstance(project).showBalloon(
                GradleLocalize.gradleNotificationsUnlinkedProjectFoundTitle().get(),
                message,
                NotificationType.INFORMATION,
                new NotificationListener.Adapter() {
                    @Override
                    @RequiredUIAccess
                    protected void hyperlinkActivated(@Nonnull Notification notification, @Nonnull HyperlinkEvent e) {
                        if (IMPORT_EVENT_DESCRIPTION.equals(e.getDescription())) {
                            VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(files[0]);
                            assert vFile != null;
                            AsyncResult<Pair<ModuleImportContext, ModuleImportProvider<ModuleImportContext>>> result =
                                AsyncResult.undefined();
                            ModuleImportProcessor.showImportChooser(
                                project,
                                vFile,
                                Collections.<ModuleImportProvider>singletonList(GradleModuleImportProvider.getInstance()),
                                result
                            );

                            result.doWhenDone(pair -> {
                                ModuleImportContext context = pair.getFirst();
                                ModuleImportProvider<ModuleImportContext> provider = pair.getSecond();

                                ModifiableModuleModel modifiableModel = ModuleManager.getInstance(project).getModifiableModel();
                                provider.process(context, project, modifiableModel, module -> {});
                                WriteAction.runAndWait(modifiableModel::commit);
                            });
                        }
                        else if (DO_NOT_SHOW_EVENT_DESCRIPTION.equals(e.getDescription())) {
                            PropertiesComponent.getInstance(project).setValue(SHOW_UNLINKED_GRADLE_POPUP, Boolean.FALSE.toString());
                        }
                    }
                }
            );
        }
    }
}
