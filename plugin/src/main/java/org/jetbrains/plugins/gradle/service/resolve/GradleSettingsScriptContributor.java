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

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiType;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.gradle.GradleConstants;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;

import javax.annotation.Nonnull;

/**
 * @author Vladislav.Soroka
 * @since 10/22/13
 */
@ExtensionImpl
public class GradleSettingsScriptContributor extends NonCodeMembersContributor {

  @Override
  public void processDynamicElements(@Nonnull PsiType qualifierType,
                                     PsiClass aClass,
                                     PsiScopeProcessor processor,
                                     PsiElement place,
                                     ResolveState state) {
    if (place == null) {
      return;
    }

    if (!(aClass instanceof GroovyScriptClass)) {
      return;
    }

    PsiFile file = aClass.getContainingFile();
    if (file == null || !file.getName().equals(GradleConstants.SETTINGS_FILE_NAME)) {
      return;
    }

    GroovyPsiManager psiManager = GroovyPsiManager.getInstance(place.getProject());
    GradleResolverUtil.processDeclarations(psiManager, processor, state, place, GradleCommonClassNames.GRADLE_API_INITIALIZATION_SETTINGS);
  }
}
