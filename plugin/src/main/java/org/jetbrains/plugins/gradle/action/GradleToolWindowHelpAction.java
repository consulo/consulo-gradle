package org.jetbrains.plugins.gradle.action;

import consulo.dataContext.DataContext;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.ContextHelpAction;
import consulo.gradle.GradleConstants;

/**
 * @author Denis Zhdanov
 * @since 2012-03-17
 */
public class GradleToolWindowHelpAction extends ContextHelpAction {
    @Override
    @RequiredUIAccess
    public void update(AnActionEvent event) {
        final Project project = event.getData(Project.KEY);
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
