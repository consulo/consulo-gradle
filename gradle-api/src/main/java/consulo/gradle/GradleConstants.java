package consulo.gradle;

import consulo.externalSystem.model.ProjectSystemId;
import consulo.gradle.localize.GradleLocalize;

import javax.annotation.Nonnull;

/**
 * Holds object representation of icons used at the <code>Gradle</code> plugin.
 *
 * @author Denis Zhdanov
 * @since 8/1/11 3:10 PM
 */
public class GradleConstants {

  @Nonnull
  public static final ProjectSystemId SYSTEM_ID = new ProjectSystemId("GRADLE", GradleLocalize.gradleName());

  @Nonnull
  public static final String EXTENSION = "gradle";
  @Nonnull
  public static final String DEFAULT_SCRIPT_NAME = "build.gradle";
  @Nonnull
  public static final String SETTINGS_FILE_NAME = "settings.gradle";

  @Nonnull
  public static final String SYSTEM_DIRECTORY_PATH_KEY = "GRADLE_USER_HOME";

  @Nonnull
  public static final String TOOL_WINDOW_TOOLBAR_PLACE = "GRADLE_SYNC_CHANGES_TOOLBAR";

  @Nonnull
  public static final String HELP_TOPIC_TOOL_WINDOW = "reference.toolwindows.gradle";

  @Nonnull
  public static final String OFFLINE_MODE_CMD_OPTION = "--offline";
  @Nonnull
  public static final String INIT_SCRIPT_CMD_OPTION = "--init-script";

  private GradleConstants() {
  }
}
