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
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.externalSystem.model.DataNode;
import consulo.externalSystem.model.ProjectKeys;
import consulo.externalSystem.model.project.ModuleData;
import consulo.externalSystem.model.task.TaskData;
import consulo.externalSystem.rt.model.ExternalSystemException;
import consulo.externalSystem.service.ParametersEnhancer;
import consulo.externalSystem.service.project.ProjectData;
import consulo.util.lang.Couple;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaProject;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Allows to enhance {@link GradleProjectResolver} processing.
 * <p/>
 * Every extension is expected to have a no-args constructor because they are used at external process and we need a simple way
 * to instantiate it.
 *
 * @author Denis Zhdanov, Vladislav Soroka
 * @see GradleManager#enhanceRemoteProcessing(OwnSimpleJavaParameters)   sample enhanceParameters() implementation
 * @since 4/17/13 11:24 AM
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface GradleProjectResolverExtension extends ParametersEnhancer {
  ExtensionPointName<GradleProjectResolverExtension> EP_NAME = ExtensionPointName.create(GradleProjectResolverExtension.class);

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
   * Populates {@link ProjectKeys#CONTENT_ROOT) content roots} of the given ide module on the basis of
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
  default Set<Class> getExtraProjectModelClasses() {
    return Set.of();
  }

  /**
   * add paths containing these classes to classpath of gradle tooling extension
   *
   * @return classes to be available for gradle
   */
  @Nonnull
  default Set<File> getToolingExtensionsFiles() {
    return Set.of();
  }

  @Nonnull
  List<Couple<String>> getExtraJvmArgs();

  @Nonnull
  List<String> getExtraCommandLineArgs();

  @Nonnull
  ExternalSystemException getUserFriendlyError(@Nonnull Throwable error, @Nonnull String projectPath, @Nullable String buildFilePath);

  /**
   * Performs project configuration and other checks before the actual project import (before invocation of gradle tooling API).
   */
  void preImportCheck();

  void enhanceTaskProcessing(@Nonnull List<String> taskNames, @Nullable String debuggerSetup, @Nonnull Consumer<String> initScriptConsumer);
}
