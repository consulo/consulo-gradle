package org.jetbrains.plugins.gradle.config;

import javax.annotation.Nonnull;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;

/**
 * Encapsulates functionality of patching problems from the previous gradle integration releases.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 3/19/12 3:48 PM
 */
public class GradlePatcher {

  @SuppressWarnings("MethodMayBeStatic")
  public void patch(@Nonnull Project project) {
    patchGradleHomeIfNecessary(project);
  }

  private static void patchGradleHomeIfNecessary(@Nonnull Project project) {
    // Old gradle integration didn't save gradle home at project-local settings (only default project has that information).
    
    final Project defaultProject = ProjectManager.getInstance().getDefaultProject();
    if (defaultProject.equals(project)) {
      return;
    }

    // Propagate gradle settings from the current project to the default project if necessary.
    // TODO den implement
//    final GradleSettings defaultProjectSettings = GradleSettings.getInstance(defaultProject);
//    final GradleSettings currentProjectSettings = GradleSettings.getInstance(project);
//    String projectGradleHome = currentProjectSettings.getGradleHome();
//    String defaultGradleHome = defaultProjectSettings.getGradleHome();
//    if (StringUtil.isEmpty(projectGradleHome) || !StringUtil.isEmpty(defaultGradleHome)) {
//      return;
//    }
//    GradleInstallationManager libraryManager = ServiceManager.getService(GradleInstallationManager.class);
//    File autodetectedGradleHome = libraryManager.getAutodetectedGradleHome();
//    // We don't want to store auto-detected value at the settings.
//    if (autodetectedGradleHome == null || !FileUtil.filesEqual(autodetectedGradleHome, new File(projectGradleHome))) {
//      GradleSettings.applyGradleHome(projectGradleHome, defaultProject);
//    }
  }
}
