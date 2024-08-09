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
package org.jetbrains.plugins.gradle.integrations.maven;

import com.intellij.java.language.psi.PsiLiteral;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.ReadAction;
import consulo.externalSystem.util.ExternalSystemApiUtil;
import consulo.externalSystem.util.ExternalSystemConstants;
import consulo.gradle.GradleConstants;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.maven.rt.server.common.model.MavenRemoteRepository;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.idea.maven.indices.MavenIndex;
import org.jetbrains.idea.maven.indices.MavenProjectIndicesManager;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * @author Vladislav.Soroka
 * @since 10/29/13
 */
public class ImportMavenRepositoriesTask implements Runnable {
    @Nonnull
    private final static MavenRemoteRepository mavenCentralRemoteRepository;

    static {
        mavenCentralRemoteRepository =
            new MavenRemoteRepository("central", null, "https://repo1.maven.org/maven2/", null, null, null);
    }

    private final Project myProject;

    public ImportMavenRepositoriesTask(Project project) {
        myProject = project;
    }

    @Override
    @RequiredReadAction
    public void run() {
        if (myProject.isDisposed()) {
            return;
        }

        final LocalFileSystem localFileSystem = LocalFileSystem.getInstance();
        final List<PsiFile> psiFileList = new ArrayList<>();

        final ModuleManager moduleManager = ModuleManager.getInstance(myProject);
        for (Module module : moduleManager.getModules()) {
            final String externalSystemId =
                ExternalSystemApiUtil.getExtensionSystemOption(module, ExternalSystemConstants.EXTERNAL_SYSTEM_ID_KEY);
            final String modulePath =
                ExternalSystemApiUtil.getExtensionSystemOption(module, ExternalSystemConstants.LINKED_PROJECT_PATH_KEY);
            if (!GradleConstants.SYSTEM_ID.getId().equals(externalSystemId) || modulePath == null) {
                continue;
            }

            String buildScript = FileUtil.findFileInProvidedPath(modulePath, GradleConstants.DEFAULT_SCRIPT_NAME);
            if (StringUtil.isEmpty(buildScript)) {
                continue;
            }

            VirtualFile virtualFile = localFileSystem.refreshAndFindFileByPath(buildScript);
            if (virtualFile == null) {
                continue;
            }

            final PsiFile psiFile = PsiManager.getInstance(myProject).findFile(virtualFile);
            if (psiFile == null) {
                continue;
            }
            psiFileList.add(psiFile);
        }

        final PsiFile[] psiFiles = ArrayUtil.toObjectArray(psiFileList, PsiFile.class);

        final Set<MavenRemoteRepository> mavenRemoteRepositories = ReadAction.compute(() -> {
            Set<MavenRemoteRepository> remoteRepositories = new HashSet<>();
            for (PsiFile psiFile : psiFiles) {
                List<GrClosableBlock> repositoriesBlocks = new ArrayList<>();
                repositoriesBlocks.addAll(findClosableBlocks(psiFile, "repositories"));

                for (GrClosableBlock closableBlock :
                    findClosableBlocks(psiFile, "buildscript", "subprojects", "allprojects", "project", "configure")) {
                    repositoriesBlocks.addAll(findClosableBlocks(closableBlock, "repositories"));
                }

                for (GrClosableBlock repositoriesBlock : repositoriesBlocks) {
                    remoteRepositories.addAll(findMavenRemoteRepositories(repositoriesBlock));
                }
            }

            return remoteRepositories;
        });

        if (mavenRemoteRepositories == null || mavenRemoteRepositories.isEmpty()) {
            return;
        }

        MavenRepositoriesHolder repositoriesHolder = MavenRepositoriesHolder.getInstance(myProject);
        MavenProjectIndicesManager projectIndicesManager = MavenProjectIndicesManager.getInstance(myProject);

        repositoriesHolder.update(mavenRemoteRepositories);

        projectIndicesManager.scheduleUpdateIndicesList(indexes -> {
            for (MavenIndex mavenIndex : indexes) {
                if (mavenIndex.getUpdateTimestamp() == -1 && repositoriesHolder.contains(mavenIndex.getRepositoryId())) {
                    projectIndicesManager.scheduleUpdate(Collections.singletonList(mavenIndex));
                }
            }
        });
    }

    @Nonnull
    @RequiredReadAction
    private static Collection<? extends GrClosableBlock> findClosableBlocks(
        @Nonnull final PsiElement element,
        @Nonnull final String... blockNames
    ) {
        List<GrMethodCall> methodCalls = PsiTreeUtil.getChildrenOfTypeAsList(element, GrMethodCall.class);
        return ContainerUtil.mapNotNull(
            methodCalls,
            call -> {
                if (call == null || call.getClosureArguments().length != 1) {
                    return null;
                }

                GrExpression expression = call.getInvokedExpression();
                //noinspection RequiredXAction
                return expression != null && ArrayUtil.contains(expression.getText(), blockNames)
                    ? call.getClosureArguments()[0]
                    : null;
            }
        );
    }

    @Nonnull
    @RequiredReadAction
    private static Collection<? extends MavenRemoteRepository> findMavenRemoteRepositories(@Nullable GrClosableBlock repositoriesBlock) {
        Set<MavenRemoteRepository> myRemoteRepositories = new HashSet<>();
        for (GrMethodCall repo : PsiTreeUtil.getChildrenOfTypeAsList(repositoriesBlock, GrMethodCall.class)) {
            if (repo.getInvokedExpression() == null) {
                continue;
            }

            final String expressionText = repo.getInvokedExpression().getText();
            if ("mavenCentral".equals(expressionText)) {
                myRemoteRepositories.add(mavenCentralRemoteRepository);
            }
            else if ("mavenRepo".equals(expressionText)) {
                for (GrNamedArgument namedArgument : repo.getNamedArguments()) {
                    if ("url".equals(namedArgument.getLabelName())) {
                        URI urlArgumentValue = resolveUriFromSimpleExpression(namedArgument.getExpression());
                        if (urlArgumentValue != null) {
                            String textUri = urlArgumentValue.toString();
                            myRemoteRepositories.add(
                                new MavenRemoteRepository(textUri, null, textUri, null, null, null)
                            );
                        }
                        break;
                    }
                }
            }
            else if ("maven".equals(expressionText) && repo.getClosureArguments().length > 0) {
                List<GrApplicationStatement> applicationStatementList =
                    PsiTreeUtil.getChildrenOfTypeAsList(repo.getClosureArguments()[0], GrApplicationStatement.class);
                if (!applicationStatementList.isEmpty()) {
                    GrApplicationStatement statement = applicationStatementList.get(0);
                    if (statement == null) {
                        continue;
                    }
                    GrExpression expression = statement.getInvokedExpression();
                    if (expression == null) {
                        continue;
                    }

                    if ("url".equals(expression.getText())) {
                        URI urlArgumentValue = resolveUriFromSimpleExpression(statement.getExpressionArguments()[0]);
                        if (urlArgumentValue != null) {
                            String textUri = urlArgumentValue.toString();
                            myRemoteRepositories.add(
                                new MavenRemoteRepository(textUri, null, textUri, null, null, null)
                            );
                        }
                    }
                }

                List<GrAssignmentExpression> assignmentExpressionList =
                    PsiTreeUtil.getChildrenOfTypeAsList(repo.getClosureArguments()[0], GrAssignmentExpression.class);
                if (!assignmentExpressionList.isEmpty()) {
                    GrAssignmentExpression statement = assignmentExpressionList.get(0);
                    if (statement == null) {
                        continue;
                    }
                    GrExpression expression = statement.getLValue();

                    if ("url".equals(expression.getText())) {
                        URI urlArgumentValue = resolveUriFromSimpleExpression(statement.getRValue());
                        if (urlArgumentValue != null) {
                            String textUri = urlArgumentValue.toString();
                            myRemoteRepositories.add(
                                new MavenRemoteRepository(textUri, null, textUri, null, null, null)
                            );
                        }
                    }
                }
            }
        }

        return myRemoteRepositories;
    }

    @Nullable
    @RequiredReadAction
    private static URI resolveUriFromSimpleExpression(@Nullable GrExpression expression) {
        if (expression == null) {
            return null;
        }

        try {
            if (expression instanceof PsiLiteral literal) {
                URI uri = new URI(String.valueOf(literal.getValue()));
                if (uri.getScheme() != null && StringUtil.startsWith(uri.getScheme(), "http")) {
                    return uri;
                }
            }
        }
        catch (URISyntaxException ignored) {
            // ignore it
        }

        try {
            PsiReference reference = expression.getReference();
            if (reference == null) {
                return null;
            }
            PsiElement element = reference.resolve();
            if (element instanceof GrVariable) {
                List<GrLiteral> grLiterals = PsiTreeUtil.getChildrenOfTypeAsList(element, GrLiteral.class);
                if (grLiterals.isEmpty()) {
                    return null;
                }
                URI uri = new URI(String.valueOf(grLiterals.get(0).getValue()));
                if (uri.getScheme() != null && StringUtil.startsWith("http", uri.getScheme())) {
                    return uri;
                }
            }
        }
        catch (URISyntaxException ignored) {
            // ignore it
        }

        return null;
    }
}
