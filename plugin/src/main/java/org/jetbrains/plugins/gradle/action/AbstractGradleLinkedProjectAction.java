package org.jetbrains.plugins.gradle.action;

import consulo.dataContext.DataContext;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.util.lang.Pair;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Common super class for gradle actions that require {@link GradleSettings#getLinkedExternalProjectPath()}  linked project}.
 * <p/>
 * Not thread-safe.
 *
 * @author Denis Zhdanov
 * @since 2021-01-31
 */
public abstract class AbstractGradleLinkedProjectAction extends AnAction {
    @Override
    @RequiredUIAccess
    public void update(AnActionEvent e) {
        final Pair<Project, String> pair = deriveProjects(e.getDataContext());
        final boolean visible = pair != null;
        e.getPresentation().setVisible(visible);
        if (!visible) {
            return;
        }
        doUpdate(e, pair.first, pair.second);
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(AnActionEvent e) {
        final Project project = e.getData(Project.KEY);
        if (project == null) {
            return;
        }
        final Pair<Project, String> pair = deriveProjects(e.getDataContext());
        if (pair == null) {
            e.getPresentation().setVisible(false);
            return;
        }
        doActionPerformed(e, project, pair.second);
    }

    @Nullable
    private static Pair<Project, String> deriveProjects(@Nullable DataContext context) {
        if (context == null) {
            return null;
        }

        final Project project = context.getData(Project.KEY);
        if (project == null) {
            return null;
        }
        // TODO den implement
        return null;
//    final String path = GradleSettings.getInstance(project).getLinkedExternalProjectPath();
//    return path == null ? null : new Pair<Project, String>(project, path);
    }

    protected abstract void doUpdate(@Nonnull AnActionEvent event, @Nonnull Project project, @Nonnull String linkedProjectPath);

    protected abstract void doActionPerformed(@Nonnull AnActionEvent event, @Nonnull Project project, @Nonnull String linkedProjectPath);
}
