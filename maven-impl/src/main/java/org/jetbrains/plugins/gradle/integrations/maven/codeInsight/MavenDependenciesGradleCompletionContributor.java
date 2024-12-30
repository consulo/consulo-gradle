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
package org.jetbrains.plugins.gradle.integrations.maven.codeInsight;

import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.AllIcons;
import consulo.gradle.codeInsight.AbstractGradleCompletionContributor;
import consulo.language.Language;
import consulo.language.editor.completion.*;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.pattern.ElementPattern;
import consulo.language.pattern.PatternCondition;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ProcessingContext;
import consulo.maven.rt.server.common.model.MavenArtifactInfo;
import consulo.maven.rt.server.common.model.MavenId;
import org.jetbrains.idea.maven.indices.MavenArtifactSearchResult;
import org.jetbrains.idea.maven.indices.MavenArtifactSearcher;
import org.jetbrains.idea.maven.indices.MavenProjectIndicesManager;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrNamedArgumentsOwner;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;

import static consulo.language.pattern.PlatformPatterns.psiElement;

/**
 * @author Vladislav.Soroka
 * @since 2013-10-31
 */
@ExtensionImpl
public class MavenDependenciesGradleCompletionContributor extends AbstractGradleCompletionContributor {
    private static final String GROUP_LABEL = "group";
    private static final String NAME_LABEL = "name";
    private static final String VERSION_LABEL = "version";
    private static final String DEPENDENCIES_SCRIPT_BLOCK = "dependencies";

    private static final ElementPattern<PsiElement> DEPENDENCIES_CALL_PATTERN = psiElement().inside(
        true,
        psiElement(GrMethodCallExpression.class).with(new PatternCondition<>("withInvokedExpressionText") {
            @Override
            @RequiredReadAction
            public boolean accepts(@Nonnull GrMethodCallExpression expression, ProcessingContext context) {
                return checkExpression(expression)
                    || checkExpression(PsiTreeUtil.getParentOfType(expression, GrMethodCallExpression.class));
            }

            @RequiredReadAction
            private boolean checkExpression(@Nullable GrMethodCallExpression expression) {
                if (expression == null) {
                    return false;
                }
                GrExpression grExpression = expression.getInvokedExpression();
                return grExpression != null && DEPENDENCIES_SCRIPT_BLOCK.equals(grExpression.getText());
            }
        })
    );

    private static final ElementPattern<PsiElement> IN_MAP_DEPENDENCY_NOTATION = psiElement()
        .and(AbstractGradleCompletionContributor.GRADLE_FILE_PATTERN)
        .withParent(GrLiteral.class)
        .withSuperParent(2, psiElement(GrNamedArgument.class))
        .and(DEPENDENCIES_CALL_PATTERN);

    private static final ElementPattern<PsiElement> IN_METHOD_DEPENDENCY_NOTATION = psiElement()
        .and(AbstractGradleCompletionContributor.GRADLE_FILE_PATTERN)
        .and(DEPENDENCIES_CALL_PATTERN);

    public MavenDependenciesGradleCompletionContributor() {
        // map-style notation:
        // e.g.:
        //    compile group: 'com.google.code.guice', name: 'guice', version: '1.0'
        //    runtime([group:'junit', name:'junit-dep', version:'4.7'])
        //    compile(group:'junit', name:'junit-dep', version:'4.7')
        extend(
            CompletionType.BASIC,
            IN_MAP_DEPENDENCY_NOTATION,
            (params, context, result) -> {
                result.stopHere();

                final PsiElement parent = params.getPosition().getParent().getParent();
                if (!(parent instanceof GrNamedArgument) || !(parent.getParent() instanceof GrNamedArgumentsOwner)) {
                    return;
                }

                final GrNamedArgument namedArgument = (GrNamedArgument)parent;
                if (GROUP_LABEL.equals(namedArgument.getLabelName())) {
                    MavenProjectIndicesManager m = MavenProjectIndicesManager.getInstance(namedArgument.getProject());
                    for (String groupId : m.getGroupIds()) {
                        LookupElement builder = LookupElementBuilder.create(groupId).withIcon(AllIcons.Nodes.PpLib);
                        result.addElement(builder);
                    }
                }
                else if (NAME_LABEL.equals(namedArgument.getLabelName())) {
                    String groupId = findNamedArgumentValue((GrNamedArgumentsOwner)namedArgument.getParent(), GROUP_LABEL);
                    if (groupId == null) {
                        return;
                    }

                    MavenProjectIndicesManager m = MavenProjectIndicesManager.getInstance(namedArgument.getProject());
                    for (String artifactId : m.getArtifactIds(groupId)) {
                        LookupElement builder = LookupElementBuilder.create(artifactId).withIcon(AllIcons.Nodes.PpLib);
                        result.addElement(builder);
                    }
                }
                else if (VERSION_LABEL.equals(namedArgument.getLabelName())) {
                    GrNamedArgumentsOwner namedArgumentsOwner = (GrNamedArgumentsOwner)namedArgument.getParent();

                    String groupId = findNamedArgumentValue(namedArgumentsOwner, GROUP_LABEL);
                    if (groupId == null) {
                        return;
                    }

                    String artifactId = findNamedArgumentValue(namedArgumentsOwner, NAME_LABEL);
                    if (artifactId == null) {
                        return;
                    }

                    MavenProjectIndicesManager m = MavenProjectIndicesManager.getInstance(namedArgument.getProject());
                    for (String version : m.getVersions(groupId, artifactId)) {
                        LookupElement builder = LookupElementBuilder.create(version).withIcon(AllIcons.Nodes.PpLib);
                        result.addElement(builder);
                    }
                }
            }
        );

        // group:name:version notation
        // e.g.:
        //    compile 'junit:junit:4.11'
        //    compile('junit:junit:4.11')
        extend(
            CompletionType.BASIC,
            IN_METHOD_DEPENDENCY_NOTATION,
            (params, context, result) -> {
                result.stopHere();

                final PsiElement parent = params.getPosition().getParent();
                if (!(parent instanceof GrLiteral) || !(parent.getParent() instanceof GrArgumentList)) {
                    return;
                }

                String searchText = CompletionUtilCore.findReferenceOrAlphanumericPrefix(params);
                MavenArtifactSearcher searcher = new MavenArtifactSearcher();
                List<MavenArtifactSearchResult> searchResults =
                    searcher.search(params.getPosition().getProject(), searchText, 100);
                for (MavenArtifactSearchResult searchResult : searchResults) {
                    for (MavenArtifactInfo artifactInfo : searchResult.versions) {
                        final StringBuilder buf = new StringBuilder();
                        MavenId.append(buf, artifactInfo.getGroupId());
                        MavenId.append(buf, artifactInfo.getArtifactId());
                        MavenId.append(buf, artifactInfo.getVersion());

                        LookupElement builder = LookupElementBuilder.create(buf.toString()).withIcon(AllIcons.Nodes.PpLib);
                        result.addElement(builder);
                    }
                }
            }
        );
    }

    @Nonnull
    @Override
    public Language getLanguage() {
        return GroovyLanguage.INSTANCE;
    }
}
