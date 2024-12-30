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
package org.jetbrains.plugins.gradle.integrations.maven.action;

import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.application.AllIcons;
import consulo.codeEditor.Editor;
import consulo.gradle.GradleConstants;
import consulo.gradle.localize.GradleLocalize;
import consulo.language.editor.action.CodeInsightAction;
import consulo.language.editor.action.CodeInsightActionHandler;
import consulo.language.psi.PsiCompiledElement;
import consulo.language.psi.PsiFile;
import consulo.maven.rt.server.common.model.MavenId;
import consulo.project.Project;
import org.jetbrains.plugins.groovy.GroovyFileType;

import jakarta.annotation.Nonnull;
import java.util.List;

/**
 * @author Vladislav.Soroka
 * @since 2013-10-23
 */
@ActionImpl(id = "Gradle.AddGradleDslDependencyAction", parents = @ActionParentRef(value = @ActionRef(id = "GenerateGroup")))
public class AddGradleDslDependencyAction extends CodeInsightAction {
    static final ThreadLocal<List<MavenId>> TEST_THREAD_LOCAL = new ThreadLocal<>();

    public AddGradleDslDependencyAction() {
        getTemplatePresentation().setDescriptionValue(GradleLocalize.gradleCodeinsightActionAdd_maven_dependencyDescription());
        getTemplatePresentation().setTextValue(GradleLocalize.gradleCodeinsightActionAdd_maven_dependencyText());
        getTemplatePresentation().setIcon(AllIcons.Nodes.PpLib);
    }

    @Nonnull
    @Override
    protected CodeInsightActionHandler getHandler() {
        return new AddGradleDslDependencyActionHandler();
    }

    @Override
    @RequiredReadAction
    protected boolean isValidForFile(@Nonnull Project project, @Nonnull Editor editor, @Nonnull PsiFile file) {
        return !(file instanceof PsiCompiledElement)
            && GroovyFileType.INSTANCE.equals(file.getFileType())
            && !GradleConstants.SETTINGS_FILE_NAME.equals(file.getName())
            && file.getName().endsWith(GradleConstants.EXTENSION);
    }
}
