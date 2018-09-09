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
package org.jetbrains.plugins.gradle.service.project;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaProject;
import org.jetbrains.plugins.gradle.GradleManager;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.externalSystem.JavaProjectData;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.task.TaskData;
import com.intellij.openapi.externalSystem.service.ParametersEnhancer;
import com.intellij.openapi.util.KeyValue;
import com.intellij.util.Consumer;

/**
 * Allows to enhance {@link GradleProjectResolver} processing.
 * <p/>
 * Every extension is expected to have a no-args constructor because they are used at external process and we need a simple way
 * to instantiate it.
 *
 * @author Denis Zhdanov, Vladislav Soroka
 * @see GradleManager#enhanceRemoteProcessing(SimpleJavaParameters)   sample enhanceParameters() implementation
 * @since 4/17/13 11:24 AM
 */
public interface GradleProjectResolverExtension extends ParametersEnhancer
{

	ExtensionPointName<GradleProjectResolverExtension> EP_NAME = ExtensionPointName.create("org.jetbrains.plugins.gradle.projectResolve");

	void setProjectResolverContext(@Nonnull ProjectResolverContext projectResolverContext);

	void setNext(@Nonnull GradleProjectResolverExtension projectResolverExtension);

	@Nullable
	GradleProjectResolverExtension getNext();

	@Nonnull
	ProjectData createProject();

	@Nonnull
	JavaProjectData createJavaProjectData();

	void populateProjectExtraModels(@Nonnull IdeaProject gradleProject, @Nonnull DataNode<ProjectData> ideProject);

	@Nonnull
	ModuleData createModule(@Nonnull IdeaModule gradleModule, @Nonnull ProjectData projectData);

	/**
	 * Populates extra models of the given ide module on the basis of the information provided by {@link org.jetbrains.plugins.gradle.tooling
	 * .ModelBuilderService}
	 *
	 * @param ideModule corresponding module from intellij gradle plugin domain
	 */
	void populateModuleExtraModels(@Nonnull IdeaModule gradleModule, @Nonnull DataNode<ModuleData> ideModule);

	/**
	 * Populates {@link com.intellij.openapi.externalSystem.model.ProjectKeys#CONTENT_ROOT) content roots} of the given ide module on the basis of
	 * the information
	 * contained at the given gradle module.
	 *
	 * @param gradleModule holder of the module information received from the gradle tooling api
	 * @param ideModule    corresponding module from intellij gradle plugin domain
	 * @throws IllegalArgumentException if given gradle module contains invalid data
	 */
	void populateModuleContentRoots(@Nonnull IdeaModule gradleModule, @Nonnull DataNode<ModuleData> ideModule);

	void populateModuleCompileOutputSettings(@Nonnull IdeaModule gradleModule, @Nonnull DataNode<ModuleData> ideModule);

	void populateModuleDependencies(@Nonnull IdeaModule gradleModule, @Nonnull DataNode<ModuleData> ideModule,
			@Nonnull DataNode<ProjectData> ideProject);

	@Nonnull
	Collection<TaskData> populateModuleTasks(@Nonnull IdeaModule gradleModule, @Nonnull DataNode<ModuleData> ideModule,
			@Nonnull DataNode<ProjectData> ideProject);

	@Nonnull
	Collection<TaskData> filterRootProjectTasks(@Nonnull List<TaskData> allTasks);

	@Nonnull
	Set<Class> getExtraProjectModelClasses();

	/**
	 * add paths containing these classes to classpath of gradle tooling extension
	 *
	 * @return classes to be available for gradle
	 */
	@Nonnull
	Set<Class> getToolingExtensionsClasses();

	@Nonnull
	List<KeyValue<String, String>> getExtraJvmArgs();

	@Nonnull
	List<String> getExtraCommandLineArgs();

	@Nonnull
	ExternalSystemException getUserFriendlyError(@Nonnull Throwable error, @Nonnull String projectPath, @javax.annotation.Nullable String buildFilePath);

	/**
	 * Performs project configuration and other checks before the actual project import (before invocation of gradle tooling API).
	 */
	void preImportCheck();

	void enhanceTaskProcessing(@Nonnull List<String> taskNames, @Nullable String debuggerSetup, @Nonnull Consumer<String> initScriptConsumer);
}