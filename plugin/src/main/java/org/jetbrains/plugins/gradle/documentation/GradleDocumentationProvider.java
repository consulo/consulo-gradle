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
package org.jetbrains.plugins.gradle.documentation;

import com.intellij.java.language.impl.codeInsight.javadoc.JavaDocUtil;
import com.intellij.java.language.psi.PsiMethod;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.documentation.LanguageDocumentationProvider;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import consulo.gradle.GradleConstants;
import consulo.gradle.GradleDocumentationBundle;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.dsl.CustomMembersGenerator;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Vladislav.Soroka
 * @since 8/29/13
 */
@ExtensionImpl
public class GradleDocumentationProvider implements LanguageDocumentationProvider {

  @Nullable
  @Override
  public String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
    return null;
  }

  @Nullable
  @Override
  public List<String> getUrlFor(PsiElement element, PsiElement originalElement) {
    List<String> result = new ArrayList<String>();
    return result.isEmpty() ? null : result;
  }

  @Nullable
  @Override
  public String generateDoc(PsiElement element, PsiElement originalElement) {
    PsiFile file = element.getContainingFile();
    if (file == null || !FileUtil.extensionEquals(file.getName(), GradleConstants.EXTENSION)) return null;
    return element instanceof GrLiteral ? findDoc(element, GrLiteral.class.cast(element).getValue()) : null;
  }

  @Nullable
  @Override
  public PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object, PsiElement element) {
    final PsiFile file = element.getContainingFile();
    if (file == null || !FileUtil.extensionEquals(file.getName(), GradleConstants.EXTENSION)) return null;
    final String doc = findDoc(element, object);
    return !StringUtil.isEmpty(doc) ? new CustomMembersGenerator.GdslNamedParameter(String.valueOf(object), doc, element, null) : null;
  }

  @Nullable
  @Override
  public PsiElement getDocumentationElementForLink(PsiManager psiManager, String link, PsiElement context) {
    return JavaDocUtil.findReferenceTarget(psiManager, link, context);
  }

  @Nullable
  private static String findDoc(@Nullable PsiElement element, Object argValue) {
    String result = null;
    if (element instanceof GrLiteral) {
      GrLiteral grLiteral = (GrLiteral)element;
      PsiElement stmt = PsiTreeUtil.findFirstParent(grLiteral, psiElement -> psiElement instanceof GrCall);
      if (stmt instanceof GrCall) {
        GrCall grCall = (GrCall)stmt;
        PsiMethod psiMethod = grCall.resolveMethod();
        if (psiMethod != null && psiMethod.getContainingClass() != null) {
          //noinspection ConstantConditions
          String qualifiedName = psiMethod.getContainingClass().getQualifiedName();
          if (grLiteral.getParent() instanceof GrNamedArgument) {
            GrNamedArgument namedArgument = (GrNamedArgument)grLiteral.getParent();
            String key = StringUtil.join(new String[]{
              "gradle.documentation",
              qualifiedName,
              psiMethod.getName(),
              namedArgument.getLabelName(),
              String.valueOf(argValue),
            }, "."
            );

            result = GradleDocumentationBundle.messageOrDefault(key, "");
          }
        }
      }
    }
    return result;
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return GroovyLanguage.INSTANCE;
  }
}
