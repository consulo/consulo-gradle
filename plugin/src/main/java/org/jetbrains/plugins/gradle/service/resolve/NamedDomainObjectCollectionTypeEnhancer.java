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

import com.intellij.java.language.impl.psi.impl.source.PsiClassReferenceType;
import com.intellij.java.language.psi.PsiType;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import org.jetbrains.plugins.groovy.impl.extensions.GroovyMapContentProvider;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrReferenceTypeEnhancer;

import javax.annotation.Nullable;

/**
 * @author Vladislav.Soroka
 * @since 2013-09-25
 */
@ExtensionImpl
public class NamedDomainObjectCollectionTypeEnhancer extends GrReferenceTypeEnhancer {
    @Override
    public PsiType getReferenceType(GrReferenceExpression ref, @Nullable PsiElement resolved) {
        if (resolved != null) {
            return null;
        }

        GrExpression qualifierExpression = ref.getQualifierExpression();
        if (qualifierExpression == null) {
            return null;
        }

        PsiType namedDomainCollectionType = GradleResolverUtil.getTypeOf(qualifierExpression);

        if (!GroovyPsiManager.isInheritorCached(
            namedDomainCollectionType,
            GradleCommonClassNames.GRADLE_API_NAMED_DOMAIN_OBJECT_COLLECTION
        )) {
            return null;
        }

        PsiElement qResolved;

        if (qualifierExpression instanceof GrReferenceExpression referenceExpression) {
            qResolved = referenceExpression.resolve();
        }
        else if (qualifierExpression instanceof GrMethodCall methodCall) {
            qResolved = methodCall.resolveMethod();
        }
        else {
            return null;
        }

        String key = ref.getReferenceName();
        if (key == null) {
            return null;
        }

        for (GroovyMapContentProvider provider : GroovyMapContentProvider.EP_NAME.getExtensions()) {
            PsiType type = provider.getValueType(qualifierExpression, qResolved, key);
            if (type != null) {
                return type;
            }
        }

        if (namedDomainCollectionType instanceof PsiClassReferenceType) {
            final PsiClassReferenceType referenceType = (PsiClassReferenceType)namedDomainCollectionType;
            final String fqName = referenceType.getCanonicalText();
            final GroovyPsiManager psiManager = GroovyPsiManager.getInstance(ref.getProject());
            switch (fqName) {
                case GradleCommonClassNames.GRADLE_API_SOURCE_SET_CONTAINER:
                    return psiManager.createTypeByFQClassName(GradleCommonClassNames.GRADLE_API_SOURCE_SET, ref.getResolveScope());
                case GradleCommonClassNames.GRADLE_API_CONFIGURATION_CONTAINER:
                    return psiManager.createTypeByFQClassName(GradleCommonClassNames.GRADLE_API_CONFIGURATION, ref.getResolveScope());
                case GradleCommonClassNames.GRADLE_API_TASK_CONTAINER:
                    return psiManager.createTypeByFQClassName(GradleCommonClassNames.GRADLE_API_TASK, ref.getResolveScope());
            }
        }

        return null;
    }
}
