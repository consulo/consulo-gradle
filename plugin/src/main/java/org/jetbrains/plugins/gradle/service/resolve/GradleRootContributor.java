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

import consulo.annotation.component.ExtensionImpl;
import consulo.util.collection.ContainerUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.util.lang.StringUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;

import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.Set;

import static org.jetbrains.plugins.gradle.service.resolve.GradleSourceSetsContributor.SOURCE_SETS;

/**
 * @author Denis Zhdanov
 * @since 2013-08-14
 */
@ExtensionImpl
public class GradleRootContributor implements GradleMethodContextContributor {
    private final static Set<String> BUILD_SCRIPT_BLOCKS = Set.of(
        "subprojects",
        "allprojects",
        "beforeEvaluate",
        "afterEvaluate",
        SOURCE_SETS
    );

    @Override
    public void process(
        @Nonnull List<String> methodCallInfo,
        @Nonnull PsiScopeProcessor processor,
        @Nonnull ResolveState state,
        @Nonnull PsiElement place
    ) {
        if (methodCallInfo.size() > 2) {
            return;
        }

        if (methodCallInfo.size() == 2 && !BUILD_SCRIPT_BLOCKS.contains(methodCallInfo.get(1))) {
            return;
        }
        if (methodCallInfo.size() > 0) {
            String method = ContainerUtil.getLastItem(methodCallInfo);
            if (method != null && StringUtil.startsWith(method, SOURCE_SETS)) {
                GradleSourceSetsContributor.getInstance().process(methodCallInfo, processor, state, place);
                return;
            }
        }

        GroovyPsiManager psiManager = GroovyPsiManager.getInstance(place.getProject());
        GradleResolverUtil.processDeclarations(
            methodCallInfo.size() > 0 ? methodCallInfo.get(0) : null,
            psiManager,
            processor,
            state,
            place,
            GradleCommonClassNames.GRADLE_API_PROJECT
        );
    }
}
