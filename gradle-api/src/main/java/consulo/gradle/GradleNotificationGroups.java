package consulo.gradle;

import consulo.project.ui.notification.NotificationGroup;

/**
 * Notification groups of the Gradle plugin, registered by the plugin. Other plugins may show Gradle import related
 * notifications in them.
 *
 * @author VISTALL
 * @since 2026-11-14
 */
public final class GradleNotificationGroups {
    public static final NotificationGroup GRADLE = NotificationGroup.balloonGroup("Gradle Notification Group");

    private GradleNotificationGroups() {
    }
}
