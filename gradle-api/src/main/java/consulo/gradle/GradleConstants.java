package consulo.gradle;

import consulo.externalSystem.model.ProjectSystemId;
import consulo.gradle.icon.GradleIconGroup;
import consulo.gradle.localize.GradleLocalize;
import consulo.ui.image.Image;
import jakarta.annotation.Nonnull;

/**
 * Holds object representation of icons used at the <code>Gradle</code> plugin.
 *
 * @author Denis Zhdanov
 * @since 8/1/11 3:10 PM
 */
public class GradleConstants {
    @Nonnull
    public static final ProjectSystemId SYSTEM_ID = new ProjectSystemId("GRADLE", GradleLocalize.gradleName()) {
        public Image getIcon() {
            return GradleIconGroup.gradle();
        }
    };

    public static final String EXTENSION = "gradle";
    public static final String DEFAULT_SCRIPT_NAME = "build.gradle";
    public static final String KOTLIN_DSL_SCRIPT_NAME = "build.gradle.kts";
    public static final String SETTINGS_FILE_NAME = "settings.gradle";

    public static final String SYSTEM_DIRECTORY_PATH_KEY = "GRADLE_USER_HOME";

    public static final String TOOL_WINDOW_TOOLBAR_PLACE = "GRADLE_SYNC_CHANGES_TOOLBAR";

    public static final String HELP_TOPIC_TOOL_WINDOW = "reference.toolwindows.gradle";

    public static final String OFFLINE_MODE_CMD_OPTION = "--offline";
    public static final String INIT_SCRIPT_CMD_OPTION = "--init-script";

    public static final String KOTLIN_DSL_SETTINGS_FILE_NAME = "settings.gradle.kts";
    public static final String DECLARATIVE_EXTENSION = "gradle.dcl";

    public static final String KOTLIN_DSL_SCRIPT_EXTENSION = "gradle.kts";

    public static final String[] BUILD_FILE_EXTENSIONS = {
        EXTENSION, KOTLIN_DSL_SCRIPT_EXTENSION, DECLARATIVE_EXTENSION
    };

    private GradleConstants() {
    }
}
