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
import consulo.ide.impl.idea.util.containers.ContainerUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.io.FileUtil;
import consulo.gradle.GradleConstants;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author Denis Zhdanov
 * @since 7/23/13 4:21 PM
 */
@ExtensionImpl
public class GradleScriptContributor extends NonCodeMembersContributor {

  public final static Set<String> BUILD_PROJECT_SCRIPT_BLOCKS = ContainerUtil.newHashSet(
    "project",
    "configure",
    "subprojects",
    "allprojects",
    "buildscript"
  );


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
    if (file == null || !FileUtil.extensionEquals(file.getName(), GradleConstants.EXTENSION)
      || GradleConstants.SETTINGS_FILE_NAME.equals(file.getName())) return;

    List<String> methodInfo = new ArrayList<>();
    for (GrMethodCall current = PsiTreeUtil.getParentOfType(place, GrMethodCall.class);
         current != null;
         current = PsiTreeUtil.getParentOfType(current, GrMethodCall.class)) {
      GrExpression expression = current.getInvokedExpression();
      if (expression == null) {
        continue;
      }
      String text = expression.getText();
      if (text != null) {
        methodInfo.add(text);
      }
    }

    final String methodCall = ContainerUtil.getLastItem(methodInfo);
    if (methodInfo.size() > 1 && BUILD_PROJECT_SCRIPT_BLOCKS.contains(methodCall)) {
      methodInfo.remove(methodInfo.size() - 1);
    }

    for (GradleMethodContextContributor contributor : GradleMethodContextContributor.EP_NAME.getExtensions()) {
      contributor.process(methodInfo, processor, state, place);
    }
  }
}
