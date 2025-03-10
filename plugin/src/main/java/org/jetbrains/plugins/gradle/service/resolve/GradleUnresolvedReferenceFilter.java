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
package org.jetbrains.plugins.gradle.service.resolve;

import com.intellij.java.language.psi.PsiType;
import consulo.annotation.component.ExtensionImpl;
import org.jetbrains.plugins.groovy.impl.extensions.GroovyUnresolvedHighlightFilter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

import jakarta.annotation.Nonnull;
import java.util.Set;

/**
 * @author Vladislav.Soroka
 * @since 2013-09-25
 */
@ExtensionImpl
public class GradleUnresolvedReferenceFilter extends GroovyUnresolvedHighlightFilter {
    private final static Set<String> IGNORE_SET = Set.of(
        GradleCommonClassNames.GRADLE_API_TASK,
        GradleCommonClassNames.GRADLE_API_SOURCE_SET,
        GradleCommonClassNames.GRADLE_API_CONFIGURATION
    );

    @Override
    public boolean isReject(@Nonnull GrReferenceExpression expression) {
        final PsiType psiType = GradleResolverUtil.getTypeOf(expression);
        return psiType != null && IGNORE_SET.contains(psiType.getCanonicalText());
    }
}
