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

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.language.psi.PsiElement;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;

import jakarta.annotation.Nonnull;
import java.util.List;

/**
 * This interface narrows {@link NonCodeMembersContributor} to a closure executed inside particular method call at a gradle script.
 * <p/>
 * Example:
 * <b>build.gradle</b>
 * <pre>
 *   subprojects {
 *     repositories {
 *       mavenCentral()
 *     }
 *   }
 * </pre>
 * Here <code>'subprojects'</code> should be resolved at context of a global script; <code>'repositories'</code> in a context of
 * <code>'subprojects'</code> and <code>'mavenCentral'</code> in a context of <code>'repositories'</code>. Every such context
 * is expected to be backed by corresponding implementation of the current interface.
 *
 * @author Denis Zhdanov
 * @since 2013-07-23
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface GradleMethodContextContributor {
    ExtensionPointName<GradleMethodContextContributor> EP_NAME = ExtensionPointName.create(GradleMethodContextContributor.class);

    /**
     * Tries to resolve target element.
     *
     * @param methodCallInfo information about method call hierarchy which points to the target place. Every entry is a method name
     *                       and the deepest one is assumed to be added the head
     * @param processor      the processor receiving the declarations.
     * @param state          current resolve state
     * @param place          the original element from which the tree up walk was initiated.
     */
    void process(
        @Nonnull List<String> methodCallInfo,
        @Nonnull PsiScopeProcessor processor,
        @Nonnull ResolveState state,
        @Nonnull PsiElement place
    );
}
