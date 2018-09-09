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

import java.util.List;

import javax.annotation.Nonnull;

import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCommandArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightParameter;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveState;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import consulo.java.module.util.JavaClassNames;

/**
 * @author Vladislav.Soroka
 * @since 9/9/13
 */
public class GradleTaskContributor implements GradleMethodContextContributor {

  @Override
  public void process(@Nonnull List<String> methodCallInfo,
                      @Nonnull PsiScopeProcessor processor,
                      @Nonnull ResolveState state,
                      @Nonnull PsiElement place) {
    if (methodCallInfo.isEmpty()) return;

    if (methodCallInfo.size() == 1) {
      if(GradleResolverUtil.isLShiftElement(place.getParent())) {
        GradleResolverUtil.addImplicitVariable(processor, state, place, GradleCommonClassNames.GRADLE_API_TASK);
      }
    }
    else if (methodCallInfo.size() == 2) {
      if (place.getParent().getParent() instanceof GrCommandArgumentList) {
        // Assuming that the method call is addition of new task into the project.
        processTaskAddition(methodCallInfo.get(0), GradleCommonClassNames.GRADLE_API_TASK_CONTAINER, processor, state, place);
      }
      else {
        processTaskTypeParameter(methodCallInfo.get(0), processor, state, place);
      }

      GradleImplicitContributor.processImplicitDeclarations(processor, state, place);
    }
    else if (methodCallInfo.size() >= 3) {
      processTaskTypeParameter(methodCallInfo.get(0), processor, state, place);

      GradleImplicitContributor.processImplicitDeclarations(processor, state, place);

      if (place.getText().equals(GradleSourceSetsContributor.SOURCE_SETS) &&
          StringUtil.startsWith(methodCallInfo.get(0), GradleSourceSetsContributor.SOURCE_SETS + '.')) {
        GradleResolverUtil.addImplicitVariable(processor, state, place, GradleCommonClassNames.GRADLE_API_SOURCE_SET_CONTAINER);
      }
    }
  }

  private static void processTaskTypeParameter(@Nonnull String methodCall, @Nonnull PsiScopeProcessor processor,
                                               @Nonnull ResolveState state,
                                               @Nonnull PsiElement place) {
    final int taskTypeParameterLevel = 3;
    PsiElement psiElement = GradleResolverUtil.findParent(place, taskTypeParameterLevel);

    if (psiElement instanceof GrMethodCallExpression) {
      GrMethodCallExpression callExpression = (GrMethodCallExpression)psiElement;
      GrArgumentList argumentList = callExpression.getArgumentList();
      if (argumentList != null && argumentList.getAllArguments().length > 0) {
        for (GroovyPsiElement argument : argumentList.getAllArguments()) {
          if (argument instanceof GrNamedArgument) {
            GrNamedArgument namedArgument = (GrNamedArgument)argument;
            GrExpression grExpression = namedArgument.getExpression();
            PsiType psiType = null;
            if (grExpression != null) {
              psiType = GradleResolverUtil.getTypeOf(grExpression);
            }
            if (psiType instanceof PsiImmediateClassType) {
              PsiImmediateClassType immediateClassType = (PsiImmediateClassType)psiType;
              for (PsiType type : immediateClassType.getParameters()) {
                GroovyPsiManager psiManager = GroovyPsiManager.getInstance(place.getProject());
                GradleResolverUtil.processDeclarations(methodCall, psiManager, processor, state, place, type.getCanonicalText());
              }
            }
          }
        }
      }
      else {
        GroovyPsiManager psiManager = GroovyPsiManager.getInstance(place.getProject());
        GradleResolverUtil.processDeclarations(psiManager, processor, state, place, GradleCommonClassNames.GRADLE_API_TASK);
      }
    }
  }

  private static void processTaskAddition(@Nonnull String name,
                                          @Nonnull String handlerClass,
                                          @Nonnull PsiScopeProcessor processor,
                                          @Nonnull ResolveState state,
                                          @Nonnull PsiElement place) {
    GroovyPsiManager psiManager = GroovyPsiManager.getInstance(place.getProject());
    PsiClass psiClass = psiManager.findClassWithCache(handlerClass, place.getResolveScope());
    if (psiClass == null) {
      return;
    }

    GrLightMethodBuilder builder = new GrLightMethodBuilder(place.getManager(), name);
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(place.getManager().getProject());
    PsiType type = new PsiArrayType(factory.createTypeByFQClassName(JavaClassNames.JAVA_LANG_OBJECT, place.getResolveScope()));
    builder.addParameter(new GrLightParameter("taskInfo", type, builder));
    PsiClassType retType = factory.createTypeByFQClassName(JavaClassNames.JAVA_LANG_STRING, place.getResolveScope());
    builder.setReturnType(retType);
    processor.execute(builder, state);

    GrMethodCall call = PsiTreeUtil.getParentOfType(place, GrMethodCall.class);
    if (call == null) {
      return;
    }
    GrArgumentList args = call.getArgumentList();
    if (args == null) {
      return;
    }

    int argsCount = GradleResolverUtil.getGrMethodArumentsCount(args);
    argsCount++; // Configuration name is delivered as an argument.

    for (PsiMethod method : psiClass.findMethodsByName("create", false)) {
      if (method.getParameterList().getParametersCount() == argsCount) {
        builder.setNavigationElement(method);
      }
    }
  }
}
