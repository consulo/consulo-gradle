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
package org.jetbrains.plugins.gradle.service.resolve.dsl;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.util.InheritanceUtil;
import consulo.language.editor.annotation.AnnotationHolder;
import consulo.language.editor.annotation.Annotator;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.language.psi.PsiElement;
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames;
import org.jetbrains.plugins.gradle.service.resolve.GradleResolverUtil;
import org.jetbrains.plugins.groovy.impl.highlighter.GroovySyntaxHighlighter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import javax.annotation.Nonnull;

import static org.jetbrains.plugins.gradle.service.resolve.GradleResolverUtil.canBeMethodOf;

/**
 * @author Vladislav.Soroka
 * @since 2013-09-25
 */
public class GradleDslAnnotator implements Annotator {
    @Override
    public void annotate(@Nonnull PsiElement element, @Nonnull AnnotationHolder holder) {
        if (element instanceof GrReferenceExpression referenceExpression) {
            final GrExpression qualifier = ResolveUtil.getSelfOrWithQualifier(referenceExpression);
            if (qualifier == null) {
                return;
            }
            if (qualifier instanceof GrReferenceExpression qualifierRefExpr && qualifierRefExpr.resolve() instanceof PsiClass) {
                return;
            }

            PsiType psiType = GradleResolverUtil.getTypeOf(qualifier);
            if (psiType == null) {
                return;
            }
            if (InheritanceUtil.isInheritor(psiType, GradleCommonClassNames.GRADLE_API_NAMED_DOMAIN_OBJECT_COLLECTION)) {
                final GroovyPsiManager psiManager = GroovyPsiManager.getInstance(element.getProject());
                PsiClass defaultGroovyMethodsClass =
                    psiManager.findClassWithCache(GroovyCommonClassNames.DEFAULT_GROOVY_METHODS, element.getResolveScope());
                if (canBeMethodOf(referenceExpression.getReferenceName(), defaultGroovyMethodsClass)) {
                    return;
                }

                PsiClass containerClass = psiManager.findClassWithCache(psiType.getCanonicalText(), element.getResolveScope());
                if (canBeMethodOf(referenceExpression.getReferenceName(), containerClass)) {
                    return;
                }

                PsiElement nameElement = referenceExpression.getReferenceNameElement();
                if (nameElement != null) {
                    holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                        .textAttributes(GroovySyntaxHighlighter.MAP_KEY);
                }
            }
        }
    }
}
