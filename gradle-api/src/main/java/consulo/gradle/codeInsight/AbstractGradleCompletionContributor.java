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
package consulo.gradle.codeInsight;

import consulo.language.editor.completion.CompletionContributor;
import consulo.language.pattern.ElementPattern;
import consulo.language.psi.PsiElement;
import consulo.gradle.GradleConstants;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrNamedArgumentsOwner;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals.GrLiteralImpl;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import static consulo.language.pattern.PlatformPatterns.psiElement;
import static consulo.language.pattern.PlatformPatterns.psiFile;
import static consulo.language.pattern.StandardPatterns.string;

/**
 * @author Vladislav.Soroka
 * @since 11/1/13
 */
public abstract class AbstractGradleCompletionContributor extends CompletionContributor {
  protected static final ElementPattern<PsiElement> GRADLE_FILE_PATTERN = psiElement()
    .inFile(psiFile().withName(string().endsWith('.' + GradleConstants.EXTENSION)));

  @Nullable
  protected String findNamedArgumentValue(@Nullable GrNamedArgumentsOwner namedArgumentsOwner, @Nonnull String label) {
    if (namedArgumentsOwner == null) return null;
    GrNamedArgument namedArgument = namedArgumentsOwner.findNamedArgument(label);
    if (namedArgument == null) return null;

    GrExpression expression = namedArgument.getExpression();
    if (!(expression instanceof GrLiteralImpl)) return null;
    Object value = GrLiteralImpl.class.cast(expression).getValue();
    return value == null ? null : String.valueOf(value);
  }
}
