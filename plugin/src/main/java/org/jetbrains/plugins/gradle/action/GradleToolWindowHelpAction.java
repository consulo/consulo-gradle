package org.jetbrains.plugins.gradle.action;

import org.jetbrains.plugins.gradle.util.GradleConstants;
import com.intellij.ide.actions.ContextHelpAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;

/**
 * @author Denis Zhdanov
 * @since 3/17/12 2:34 PM
 */
public class GradleToolWindowHelpAction extends ContextHelpAction {

  @Override
  public void update(AnActionEvent event) {
    final Project project = event.getProject();
    if (project == null) {
      event.getPresentation().setVisible(false);
      return;
    }

    // TODO den implement
//    if (StringUtil.isEmpty(GradleSettings.getInstance(project).getLinkedExternalProjectPath())) {
//      event.getPresentation().setVisible(false);
//      return;
//    }
    event.getPresentation().setVisible(true);
    super.update(event);
  }

  @Override
  protected String getHelpId(DataContext dataContext) {
    return GradleConstants.HELP_TOPIC_TOOL_WINDOW;
  }
}
