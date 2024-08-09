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
package org.jetbrains.plugins.gradle.codeInspection;

import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.collection.ArrayUtil;
import consulo.util.io.FileUtil;
import org.jetbrains.annotations.Nls;
import consulo.gradle.GradleConstants;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Vladislav.Soroka
 * @since 2013-11-21
 */
@ExtensionImpl
public class MultipleRepositoryUrlsInspection extends GradleBaseInspection {
    @Nonnull
    @Override
    protected BaseInspectionVisitor buildVisitor() {
        return new MyVisitor();
    }

    @Nls
    @Nonnull
    @Override
    public String getGroupDisplayName() {
        return PROBABLE_BUGS;
    }

    @Nonnull
    @Override
    public String[] getGroupPath() {
        return new String[]{"Gradle"};
    }

    @Override
    protected String buildErrorString(Object... args) {
        return GradleInspectionBundle.message("multiple.repository.urls", args);
    }

    @Nls
    @Nonnull
    @Override
    public String getDisplayName() {
        return GradleInspectionBundle.message("multiple.repository.urls");
    }

    private static class MyVisitor extends BaseInspectionVisitor {
        @Override
        @RequiredReadAction
        public void visitClosure(GrClosableBlock closure) {
            PsiFile file = closure.getContainingFile();
            if (file == null || !FileUtil.extensionEquals(file.getName(), GradleConstants.EXTENSION)) {
                return;
            }

            super.visitClosure(closure);
            GrMethodCall mavenMethodCall = PsiTreeUtil.getParentOfType(closure, GrMethodCall.class);
            if (mavenMethodCall == null) {
                return;
            }
            GrExpression mavenMethodExpression = mavenMethodCall.getInvokedExpression();
            if (mavenMethodExpression == null ||
                !ArrayUtil.contains(mavenMethodExpression.getText(), "maven", "ivy")) {
                return;
            }

            GrMethodCall repositoryMethodCall = PsiTreeUtil.getParentOfType(mavenMethodCall, GrMethodCall.class);
            if (repositoryMethodCall == null) {
                return;
            }
            GrExpression repositoryMethodExpression = repositoryMethodCall.getInvokedExpression();
            if (repositoryMethodExpression == null || !repositoryMethodExpression.getText().equals("repositories")) {
                return;
            }

            List<GrCallExpression> statements = findUrlCallExpressions(closure);
            if (statements.size() > 1) {
                registerError(closure);

                registerError(closure, GradleInspectionBundle.message("multiple.repository.urls"),
                    new LocalQuickFix[]{new MultipleRepositoryUrlsFix(closure, mavenMethodExpression.getText())},
                    ProblemHighlightType.GENERIC_ERROR
                );
            }
        }
    }

    @Nonnull
    @RequiredReadAction
    static List<GrCallExpression> findUrlCallExpressions(@Nonnull GrClosableBlock closure) {
        GrCallExpression[] applicationStatements = PsiTreeUtil.getChildrenOfType(closure, GrCallExpression.class);
        if (applicationStatements == null) {
            return Collections.emptyList();
        }

        List<GrCallExpression> statements = new ArrayList<>();
        for (GrCallExpression statement : applicationStatements) {
            GrReferenceExpression[] referenceExpressions = PsiTreeUtil.getChildrenOfType(statement, GrReferenceExpression.class);
            if (referenceExpressions == null) {
                continue;
            }
            for (GrReferenceExpression expression : referenceExpressions) {
                String expressionText = expression.getText();
                if ("url".equals(expressionText) || "setUrl".equals(expressionText)) {
                    statements.add(statement);
                }
            }
        }
        return statements;
    }
}
