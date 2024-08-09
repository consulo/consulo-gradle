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

import consulo.application.ApplicationManager;
import consulo.codeEditor.Editor;
import consulo.gradle.GradleBundle;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.action.CodeInsightActionHandler;
import consulo.language.editor.util.LanguageEditorUtil;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.maven.rt.server.common.model.MavenId;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.indices.MavenArtifactSearchDialog;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * @author Vladislav.Soroka
 * @since 10/23/13
 */
class AddGradleDslDependencyActionHandler implements CodeInsightActionHandler {
    @Override
    public void invoke(@Nonnull final Project project, @Nonnull final Editor editor, @Nonnull final PsiFile file) {
        if (!LanguageEditorUtil.checkModificationAllowed(editor)) {
            return;
        }

        final List<MavenId> ids;
        if (ApplicationManager.getApplication().isUnitTestMode()) {
            ids = AddGradleDslDependencyAction.TEST_THREAD_LOCAL.get();
        }
        else {
            ids = MavenArtifactSearchDialog.searchForArtifact(project, Collections.<MavenDomDependency>emptyList());
        }

        if (ids.isEmpty()) {
            return;
        }

        new WriteCommandAction.Simple(project, GradleBundle.message("gradle.codeInsight.action.add_maven_dependency.text"), file) {
            @Override
            protected void run() {
                GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);
                List<GrMethodCall> closableBlocks = PsiTreeUtil.getChildrenOfTypeAsList(file, GrMethodCall.class);
                GrCall dependenciesBlock = ContainerUtil.find(closableBlocks, new Predicate<GrMethodCall>() {
                    @Override
                    public boolean test(GrMethodCall call) {
                        GrExpression expression = call.getInvokedExpression();
                        return expression != null && "dependencies".equals(expression.getText());
                    }
                });

                if (dependenciesBlock == null) {
                    StringBuilder buf = new StringBuilder();
                    for (MavenId mavenId : ids) {
                        buf.append(String.format("compile '%s'\n", getMavenArtifactKey(mavenId)));
                    }
                    dependenciesBlock = (GrCall)factory.createStatementFromText("dependencies{\n" + buf + "}");
                    file.add(dependenciesBlock);
                }
                else {
                    GrClosableBlock closableBlock = ArrayUtil.getFirstElement(dependenciesBlock.getClosureArguments());
                    if (closableBlock != null) {
                        for (MavenId mavenId : ids) {
                            closableBlock.addStatementBefore(
                                factory.createStatementFromText(String.format("compile '%s'\n", getMavenArtifactKey(mavenId))),
                                null
                            );
                        }
                    }
                }
            }
        }.execute();
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }


    @Nonnull
    private static String getMavenArtifactKey(MavenId mavenId) {
        StringBuilder builder = new StringBuilder();
        append(builder, mavenId.getGroupId());
        append(builder, mavenId.getArtifactId());
        append(builder, mavenId.getVersion());

        return builder.toString();
    }

    private static void append(StringBuilder builder, String part) {
        if (builder.length() != 0) {
            builder.append(':');
        }
        builder.append(part == null ? "" : part);
    }
}
