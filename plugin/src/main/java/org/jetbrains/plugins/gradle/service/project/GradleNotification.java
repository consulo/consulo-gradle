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

import javax.annotation.Nonnull;

import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.AppUIUtil;

import javax.annotation.Nullable;

/**
 * @author Vladislav.Soroka
 * @since 12/10/13
 */
public class GradleNotification {
  private static final NotificationGroup NOTIFICATION_GROUP = NotificationGroup.balloonGroup("Gradle Notification Group");

  @Nonnull
  private final Project myProject;

  @Nonnull
  public static GradleNotification getInstance(@Nonnull Project project) {
    return ServiceManager.getService(project, GradleNotification.class);
  }

  public GradleNotification(@Nonnull Project project) {
    myProject = project;
  }

  public void showBalloon(@Nonnull final String title,
                          @Nonnull final String message,
                          @Nonnull final NotificationType type,
                          @Nullable final NotificationListener listener) {
    AppUIUtil.invokeLaterIfProjectAlive(myProject, new Runnable() {
      @Override
      public void run() {
        NOTIFICATION_GROUP.createNotification(title, message, type, listener).notify(myProject);
      }
    });
  }
}

