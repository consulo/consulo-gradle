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
package consulo.gradle.service.project;

import com.intellij.java.impl.externalSystem.JavaProjectData;
import consulo.externalSystem.model.DataNode;
import consulo.externalSystem.model.project.ModuleData;
import consulo.externalSystem.model.task.TaskData;
import consulo.externalSystem.rt.model.ExternalSystemException;
import consulo.externalSystem.service.project.ProjectData;
import consulo.process.ExecutionException;
import consulo.process.cmd.SimpleJavaParameters;
import consulo.util.lang.Couple;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaProject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * {@link AbstractProjectResolverExtension} provides dummy implementation of Gradle project resolver.
 *
 * @author Vladislav.Soroka
 * @since 2013-10-14
 */
public abstract class AbstractProjectResolverExtension implements GradleProjectResolverExtension {

    @Nonnull
    protected ProjectResolverContext resolverCtx;
    @Nonnull
    protected GradleProjectResolverExtension nextResolver;

    @Override
    public void setProjectResolverContext(@Nonnull ProjectResolverContext projectResolverContext) {
        resolverCtx = projectResolverContext;
    }

    @Override
    public void setNext(@Nonnull GradleProjectResolverExtension next) {
        // there always should be at least gradle basic resolver further in the chain
        //noinspection ConstantConditions
        assert next != null;
        nextResolver = next;
    }

    @Nullable
    @Override
    public GradleProjectResolverExtension getNext() {
        return nextResolver;
    }

    @Nonnull
    @Override
    public ProjectData createProject() {
        return nextResolver.createProject();
    }

    @Nonnull
    @Override
    public JavaProjectData createJavaProjectData() {
        return nextResolver.createJavaProjectData();
    }

    @Override
    public void populateProjectExtraModels(@Nonnull IdeaProject gradleProject, @Nonnull DataNode<ProjectData> ideProject) {
        nextResolver.populateProjectExtraModels(gradleProject, ideProject);
    }

    @Nonnull
    @Override
    public ModuleData createModule(@Nonnull IdeaModule gradleModule, @Nonnull ProjectData projectData) {
        return nextResolver.createModule(gradleModule, projectData);
    }

    @Override
    public void populateModuleExtraModels(@Nonnull IdeaModule gradleModule, @Nonnull DataNode<ModuleData> ideModule) {
        nextResolver.populateModuleExtraModels(gradleModule, ideModule);
    }

    @Override
    public void populateModuleContentRoots(@Nonnull IdeaModule gradleModule, @Nonnull DataNode<ModuleData> ideModule) {
        nextResolver.populateModuleContentRoots(gradleModule, ideModule);
    }


    @Override
    public void populateModuleCompileOutputSettings(@Nonnull IdeaModule gradleModule, @Nonnull DataNode<ModuleData> ideModule) {
        nextResolver.populateModuleCompileOutputSettings(gradleModule, ideModule);
    }

    @Override
    public void populateModuleDependencies(
        @Nonnull IdeaModule gradleModule, @Nonnull DataNode<ModuleData> ideModule,
        @Nonnull DataNode<ProjectData> ideProject
    ) {
        nextResolver.populateModuleDependencies(gradleModule, ideModule, ideProject);
    }

    @Nonnull
    @Override
    public Collection<TaskData> populateModuleTasks(
        @Nonnull IdeaModule gradleModule, @Nonnull DataNode<ModuleData> ideModule,
        @Nonnull DataNode<ProjectData> ideProject
    ) throws IllegalArgumentException, IllegalStateException {
        return nextResolver.populateModuleTasks(gradleModule, ideModule, ideProject);
    }

    @Nonnull
    @Override
    public Collection<TaskData> filterRootProjectTasks(@Nonnull List<TaskData> allTasks) {
        return nextResolver.filterRootProjectTasks(allTasks);
    }

    @Nonnull
    @Override
    public Set<Class> getExtraProjectModelClasses() {
        return Collections.emptySet();
    }

    @Nonnull
    @Override
    public Set<Class> getToolingExtensionsClasses() {
        return Collections.emptySet();
    }

    @Nonnull
    @Override
    public List<Couple<String>> getExtraJvmArgs() {
        return Collections.emptyList();
    }

    @Nonnull
    @Override
    public List<String> getExtraCommandLineArgs() {
        return Collections.emptyList();
    }

    @Nonnull
    @Override
    public ExternalSystemException getUserFriendlyError(
        @Nonnull Throwable error,
        @Nonnull String projectPath,
        @Nullable String buildFilePath
    ) {
        return nextResolver.getUserFriendlyError(error, projectPath, buildFilePath);
    }

    @Override
    public void enhanceRemoteProcessing(@Nonnull SimpleJavaParameters parameters) throws ExecutionException {
    }

    @Override
    public void enhanceLocalProcessing(@Nonnull List<URL> urls) {
    }

    @Override
    public void preImportCheck() {
    }

    @Override
    public void enhanceTaskProcessing(
        @Nonnull List<String> taskNames,
        @Nullable String debuggerSetup,
        @Nonnull Consumer<String> initScriptConsumer
    ) {
    }
}
