package org.jetbrains.plugins.gradle.service.project;

import consulo.annotation.component.ExtensionImpl;
import consulo.project.ui.notification.NotificationGroup;
import consulo.project.ui.notification.NotificationGroupContributor;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

/**
 * @author VISTALL
 * @since 31/03/2023
 */
@ExtensionImpl
public class GradleNotificationContributor implements NotificationGroupContributor {
  @Override
  public void contribute(@Nonnull Consumer<NotificationGroup> consumer) {
    consumer.accept(GradleNotification.NOTIFICATION_GROUP);
  }
}
