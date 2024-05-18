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

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.ide.ServiceManager;
import consulo.project.Project;
import consulo.project.ui.notification.NotificationGroup;
import consulo.project.ui.notification.NotificationType;
import consulo.project.ui.notification.event.NotificationListener;
import consulo.project.ui.util.AppUIUtil;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author Vladislav.Soroka
 * @since 12/10/13
 */
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
@Singleton
public class GradleNotification {
  public static final NotificationGroup NOTIFICATION_GROUP = NotificationGroup.balloonGroup("Gradle Notification Group");

  @Nonnull
  private final Project myProject;

  @Nonnull
  public static GradleNotification getInstance(@Nonnull Project project) {
    return ServiceManager.getService(project, GradleNotification.class);
  }

  @Inject
  public GradleNotification(@Nonnull Project project) {
    myProject = project;
  }

  public void showBalloon(@Nonnull final String title,
                          @Nonnull final String message,
                          @Nonnull final NotificationType type,
                          @Nullable final NotificationListener listener) {
    AppUIUtil.invokeLaterIfProjectAlive(myProject,
                                        () -> NOTIFICATION_GROUP.createNotification(title, message, type, listener).notify(myProject));
  }
}

