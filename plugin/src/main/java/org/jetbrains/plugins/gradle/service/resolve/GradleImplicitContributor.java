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
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.externalSystem.model.execution.ExternalTaskPojo;
import consulo.externalSystem.util.ExternalSystemApiUtil;
import consulo.externalSystem.util.ExternalSystemConstants;
import consulo.ide.impl.idea.util.containers.ContainerUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.util.lang.Couple;
import org.jetbrains.plugins.gradle.settings.GradleLocalSettings;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.*;
import static org.jetbrains.plugins.gradle.service.resolve.GradleResolverUtil.canBeMethodOf;

/**
 * @author Vladislav.Soroka
 * @since 9/24/13
 */
@ExtensionImpl
public class GradleImplicitContributor implements GradleMethodContextContributor {
    private final static Map<String, String> BUILT_IN_TASKS = ContainerUtil.newHashMap(
        Couple.of("assemble", GRADLE_API_DEFAULT_TASK),
        Couple.of("build", GRADLE_API_DEFAULT_TASK),
        Couple.of("buildDependents", GRADLE_API_DEFAULT_TASK),
        Couple.of("buildNeeded", GRADLE_API_DEFAULT_TASK),
        Couple.of("clean", GRADLE_API_TASKS_DELETE),
        Couple.of("jar", GRADLE_API_TASKS_BUNDLING_JAR),
        Couple.of("war", GRADLE_API_TASKS_BUNDLING_WAR),
        Couple.of("classes", GRADLE_API_DEFAULT_TASK),
        Couple.of("compileJava", GRADLE_API_TASKS_COMPILE_JAVA_COMPILE),
        Couple.of("compileTestJava", GRADLE_API_DEFAULT_TASK),
        Couple.of("processTestResources", GRADLE_API_DEFAULT_TASK),
        Couple.of("testClasses", GRADLE_API_DEFAULT_TASK),
        Couple.of("processResources", GRADLE_LANGUAGE_JVM_TASKS_PROCESS_RESOURCES),
        Couple.of("setupBuild", GRADLE_BUILDSETUP_TASKS_SETUP_BUILD),
        Couple.of("wrapper", GRADLE_API_TASKS_WRAPPER_WRAPPER),
        Couple.of("javadoc", GRADLE_API_TASKS_JAVADOC_JAVADOC),
        Couple.of("dependencies", GRADLE_API_TASKS_DIAGNOSTICS_DEPENDENCY_REPORT_TASK),
        Couple.of("dependencyInsight", GRADLE_API_TASKS_DIAGNOSTICS_DEPENDENCY_INSIGHT_REPORT_TASK),
        Couple.of("projects", GRADLE_API_TASKS_DIAGNOSTICS_PROJECT_REPORT_TASK),
        Couple.of("properties", GRADLE_API_TASKS_DIAGNOSTICS_PROPERTY_REPORT_TASK),
        Couple.of("tasks", GRADLE_API_TASKS_DIAGNOSTICS_TASK_REPORT_TASK),
        Couple.of("check", GRADLE_API_DEFAULT_TASK),
        Couple.of("test", GRADLE_API_TASKS_TESTING_TEST),
        Couple.of("uploadArchives", GRADLE_API_TASKS_UPLOAD)
    );

    @Override
    @RequiredReadAction
    public void process(
        @Nonnull List<String> methodCallInfo,
        @Nonnull PsiScopeProcessor processor,
        @Nonnull ResolveState state,
        @Nonnull PsiElement place
    ) {
        if (methodCallInfo.isEmpty()) {
            checkForAvailableTasks(0, place.getText(), processor, state, place);
            return;
        }

        final String methodCall = consulo.util.collection.ContainerUtil.getLastItem(methodCallInfo);
        if (methodCall == null) {
            return;
        }

        if (!methodCall.equals("task")) {
            if (methodCallInfo.size() == 1) {
                checkForAvailableTasks(1, place.getText(), processor, state, place);
            }
            if (methodCallInfo.size() == 2) {
                processAvailableTasks(methodCallInfo, methodCall, processor, state, place);
            }
        }

        if (methodCallInfo.size() >= 3 && Arrays.equals(
            ContainerUtil.ar("dirs", "flatDir", "repositories"),
            methodCallInfo.subList(0, 3).toArray()
        )) {
            final GroovyPsiManager psiManager = GroovyPsiManager.getInstance(place.getProject());
            GradleResolverUtil.processDeclarations(
                psiManager,
                processor,
                state,
                place,
                GRADLE_API_ARTIFACTS_REPOSITORIES_FLAT_DIRECTORY_ARTIFACT_REPOSITORY
            );
        }

        if (methodCallInfo.size() == 3) {
            final GroovyPsiManager psiManager = GroovyPsiManager.getInstance(place.getProject());
            if ("manifest".equals(methodCallInfo.get(1)) && "jar".equals(methodCallInfo.get(2))) {
                GradleResolverUtil.processDeclarations(psiManager, processor, state, place, GRADLE_API_JAVA_ARCHIVES_MANIFEST);
            }
        }

        if (place instanceof GrExpression placeExpression && GradleResolverUtil.getTypeOf(placeExpression) == null) {
            GrClosableBlock closableBlock = GradleResolverUtil.findParent(place, GrClosableBlock.class);
            if (closableBlock != null && closableBlock.getParent() instanceof GrMethodCallExpression methodCallExpression) {
                PsiType psiType = GradleResolverUtil.getTypeOf(methodCallExpression);
                if (psiType != null) {
                    final GroovyPsiManager psiManager = GroovyPsiManager.getInstance(place.getProject());
                    GradleResolverUtil.processDeclarations(psiManager, processor, state, place, psiType.getCanonicalText());
                }
            }
        }
    }

    @RequiredReadAction
    public static void processImplicitDeclarations(
        @Nonnull PsiScopeProcessor processor,
        @Nonnull ResolveState state,
        @Nonnull PsiElement place
    ) {
        if (!place.getText().equals("resources")) {
            GroovyPsiManager psiManager = GroovyPsiManager.getInstance(place.getProject());
            GradleResolverUtil.processDeclarations(psiManager, processor, state, place, GRADLE_API_PROJECT);
        }
    }

    @RequiredReadAction
    private static void checkForAvailableTasks(
        int level,
        @Nullable String taskName,
        @Nonnull PsiScopeProcessor processor,
        @Nonnull ResolveState state,
        @Nonnull PsiElement place
    ) {
        if (taskName == null) {
            return;
        }
        final GroovyPsiManager psiManager = GroovyPsiManager.getInstance(place.getProject());
        PsiClass gradleApiProjectClass = psiManager.findClassWithCache(GRADLE_API_PROJECT, place.getResolveScope());
        if (canBeMethodOf(taskName, gradleApiProjectClass)) {
            return;
        }
        if (canBeMethodOf(GroovyPropertyUtils.getGetterNameNonBoolean(taskName), gradleApiProjectClass)) {
            return;
        }

        final String className = BUILT_IN_TASKS.get(taskName);
        if (className != null) {
            if (level <= 1) {
                GradleResolverUtil.addImplicitVariable(processor, state, place, className);
            }
            processTask(taskName, className, psiManager, processor, state, place);
            return;
        }

        Module module = ModuleUtilCore.findModuleForPsiElement(place);
        if (module == null) {
            return;
        }
        String path = ExternalSystemApiUtil.getExtensionSystemOption(module, ExternalSystemConstants.ROOT_PROJECT_PATH_KEY);
        GradleLocalSettings localSettings = GradleLocalSettings.getInstance(place.getProject());
        Collection<ExternalTaskPojo> taskPojos = localSettings.getAvailableTasks().get(path);
        if (taskPojos == null) {
            return;
        }

        for (ExternalTaskPojo taskPojo : taskPojos) {
            if (taskName.equals(taskPojo.getName())) {
                processTask(taskName, GRADLE_API_TASK, psiManager, processor, state, place);
                return;
            }
        }
    }

    @RequiredReadAction
    private static void processTask(
        @Nonnull String taskName,
        @Nonnull String fqName,
        @Nonnull GroovyPsiManager psiManager,
        @Nonnull PsiScopeProcessor processor,
        @Nonnull ResolveState state,
        @Nonnull PsiElement place
    ) {
        if (taskName.equals(place.getText())) {
            if (!(place instanceof GrClosableBlock)) {
                GrLightMethodBuilder methodBuilder =
                    GradleResolverUtil.createMethodWithClosure(taskName, fqName, null, place, psiManager);
                if (methodBuilder == null) {
                    return;
                }
                processor.execute(methodBuilder, state);
                PsiClass contributorClass = psiManager.findClassWithCache(fqName, place.getResolveScope());
                if (contributorClass == null) {
                    return;
                }
                GradleResolverUtil.processMethod(taskName, contributorClass, processor, state, place);
            }
        }
        else {
            GradleResolverUtil.processDeclarations(psiManager, processor, state, place, fqName);
        }
    }

    private static void processAvailableTasks(
        List<String> methodCallInfo,
        @Nonnull String taskName,
        @Nonnull PsiScopeProcessor processor,
        @Nonnull ResolveState state,
        @Nonnull PsiElement place
    ) {
        final GroovyPsiManager psiManager = GroovyPsiManager.getInstance(place.getProject());
        PsiClass gradleApiProjectClass = psiManager.findClassWithCache(GRADLE_API_PROJECT, place.getResolveScope());
        if (canBeMethodOf(taskName, gradleApiProjectClass)) {
            return;
        }
        if (canBeMethodOf(GroovyPropertyUtils.getGetterNameNonBoolean(taskName), gradleApiProjectClass)) {
            return;
        }
        final String className = BUILT_IN_TASKS.get(taskName);
        if (className != null) {
            String methodName = methodCallInfo.size() > 0 ? methodCallInfo.get(0) : null;
            GradleResolverUtil.processDeclarations(methodName, psiManager, processor, state, place, className);
        }
    }
}
