package org.jetbrains.plugins.gradle.action;

import consulo.application.dumb.DumbAware;
import consulo.fileEditor.FileEditorManager;
import consulo.gradle.localize.GradleLocalize;
import consulo.logging.Logger;
import consulo.navigation.OpenFileDescriptor;
import consulo.navigation.OpenFileDescriptorFactory;
import consulo.project.Project;
import consulo.ui.ex.action.AnActionEvent;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import jakarta.annotation.Nonnull;

/**
 * Forces the Consulo to open {@link GradleSettings#getLinkedExternalProjectPath() linked gradle project} at the editor
 * <p/>
 * Not thread-safe.
 *
 * @author Denis Zhdanov
 * @since 2012-01-31
 */
public class GradleOpenScriptAction extends AbstractGradleLinkedProjectAction implements DumbAware {
    private static final Logger LOG = Logger.getInstance(GradleOpenScriptAction.class);

    public GradleOpenScriptAction() {
        getTemplatePresentation().setTextValue(GradleLocalize.gradleActionOpenScriptText());
        getTemplatePresentation().setDescriptionValue(GradleLocalize.gradleActionOpenScriptDescription());
    }

    @Override
    protected void doUpdate(@Nonnull AnActionEvent event, @Nonnull Project project, @Nonnull String linkedProjectPath) {
    }

    @Override
    protected void doActionPerformed(@Nonnull AnActionEvent event, @Nonnull Project project, @Nonnull String linkedProjectPath) {
        final VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(linkedProjectPath);
        if (virtualFile == null) {
            LOG.warn(String.format("Can't obtain virtual file for the target file path: '%s'", linkedProjectPath));
            return;
        }
        OpenFileDescriptor descriptor = OpenFileDescriptorFactory.getInstance(project).builder(virtualFile).build();
        FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
    }
}
