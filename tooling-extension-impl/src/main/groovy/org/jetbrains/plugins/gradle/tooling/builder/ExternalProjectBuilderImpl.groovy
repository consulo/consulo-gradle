/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
package org.jetbrains.plugins.gradle.tooling.builder

import com.google.gson.GsonBuilder
import consulo.externalSystem.rt.model.*
import org.gradle.api.*
import org.gradle.api.file.ContentFilterable
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.util.PatternFilterable
import org.jetbrains.plugins.gradle.tooling.ErrorMessageBuilder
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService

import java.util.concurrent.ConcurrentHashMap

/**
 * @author Vladislav.Soroka
 * @since 12/20/13
 */
class ExternalProjectBuilderImpl implements ModelBuilderService {

  private final cache = new ConcurrentHashMap<String, ExternalProject>()

  @Override
  public boolean canBuild(String modelName) {
    return ExternalProject.name.equals(modelName)
  }

  @Override
  public Object buildAll(final String modelName, final Project project) {
    ExternalProject externalProject = cache[project.path]
    if (externalProject != null) return externalProject

    DefaultExternalProject defaultExternalProject = new DefaultExternalProject()
    defaultExternalProject.externalSystemId = "GRADLE"
    defaultExternalProject.name = project.name
    defaultExternalProject.QName = ":".equals(project.path) ? project.name : project.path
    defaultExternalProject.version = wrap(project.version)
    defaultExternalProject.description = project.description
    defaultExternalProject.buildDir = project.buildDir
    defaultExternalProject.buildFile = project.buildFile
    defaultExternalProject.group = wrap(project.group)
    defaultExternalProject.projectDir = project.projectDir
    defaultExternalProject.sourceSets = getSourceSets(project)
    defaultExternalProject.tasks = getTasks(project)

    defaultExternalProject.plugins = getPlugins(project)
    //defaultExternalProject.setProperties(project.getProperties())


    final Map<String, ExternalProject> childProjects = new HashMap<String, ExternalProject>(project.getChildProjects().size())
    for (Map.Entry<String, Project> projectEntry : project.getChildProjects().entrySet()) {
      final Object externalProjectChild = buildAll(modelName, projectEntry.getValue())
      if (externalProjectChild instanceof ExternalProject) {
        childProjects.put(projectEntry.getKey(), (ExternalProject)externalProjectChild)
      }
    }
    defaultExternalProject.setChildProjects(childProjects)
    cache.put(project.getPath(), defaultExternalProject)

    defaultExternalProject
  }

  static Map<String, ExternalPlugin> getPlugins(Project project) {
    def result = [:] as Map<String, ExternalPlugin>
    project.convention.plugins.each { key, value ->
      ExternalPlugin externalPlugin = new DefaultExternalPlugin()
      externalPlugin.id = key
      result.put(key, externalPlugin)
    }

    result
  }

  static Map<String, ExternalTask> getTasks(Project project) {
    def result = [:] as Map<String, ExternalTask>

    project.tasks.all { Task task ->
      ExternalTask externalTask = new DefaultExternalTask()
      externalTask.name = task.name
      externalTask.description = task.description
      externalTask.group = task.group
      externalTask.QName = task.path
      result.put(externalTask.QName, externalTask)
    }

    result
  }

  static Map<String, ExternalSourceSet> getSourceSets(Project project) {
    def result = [:] as Map<String, ExternalSourceSet>
    if (!project.hasProperty("sourceSets") || !(project.sourceSets instanceof SourceSetContainer)) {
      return result
    }
    def sourceSets = project.sourceSets as SourceSetContainer

    def (resourcesIncludes, resourcesExcludes, filterReaders) = getFilters(project, 'processResources')
    def (testResourcesIncludes, testResourcesExcludes, testFilterReaders) = getFilters(project, 'processTestResources')
    //def (javaIncludes,javaExcludes) = getFilters(project,'compileJava')

    sourceSets.all { SourceSet sourceSet ->
      ExternalSourceSet externalSourceSet = new DefaultExternalSourceSet()
      externalSourceSet.name = sourceSet.name

      def sources = [:] as Map<ExternalSystemSourceType, ExternalSourceDirectorySet>
      ExternalSourceDirectorySet resourcesDirectorySet = new DefaultExternalSourceDirectorySet()
      resourcesDirectorySet.name = sourceSet.resources.name
      resourcesDirectorySet.srcDirs = sourceSet.resources.srcDirs
      resourcesDirectorySet.outputDir = sourceSet.output.resourcesDir

      ExternalSourceDirectorySet javaDirectorySet = new DefaultExternalSourceDirectorySet()
      javaDirectorySet.name = sourceSet.allJava.name
      javaDirectorySet.srcDirs = sourceSet.allJava.srcDirs
      javaDirectorySet.outputDir = sourceSet.output.classesDir
//      javaDirectorySet.excludes = javaExcludes + sourceSet.java.excludes;
//      javaDirectorySet.includes = javaIncludes + sourceSet.java.includes;

      if (SourceSet.TEST_SOURCE_SET_NAME.equals(sourceSet.name)) {
        resourcesDirectorySet.excludes = testResourcesExcludes + sourceSet.resources.excludes;
        resourcesDirectorySet.includes = testResourcesIncludes + sourceSet.resources.includes;
        resourcesDirectorySet.filters = testFilterReaders
        sources.put(ExternalSystemSourceType.TEST, javaDirectorySet)
        sources.put(ExternalSystemSourceType.TEST_RESOURCE, resourcesDirectorySet)
      }
      else {
        resourcesDirectorySet.excludes = resourcesExcludes + sourceSet.resources.excludes;
        resourcesDirectorySet.includes = resourcesIncludes + sourceSet.resources.includes;
        resourcesDirectorySet.filters = filterReaders
        sources.put(ExternalSystemSourceType.SOURCE, javaDirectorySet)
        sources.put(ExternalSystemSourceType.RESOURCE, resourcesDirectorySet)
      }

      externalSourceSet.sources = sources
      result[sourceSet.name] = externalSourceSet
    }
    result
  }

  static getFilters(Project project, String taskName) {
    def includes = []
    def excludes = []
    def filterReaders = [] as List<ExternalFilter>
    def filterableTask = project.tasks.findByName(taskName)
    if (filterableTask instanceof PatternFilterable) {
      includes += filterableTask.includes
      excludes += filterableTask.excludes
    }

    if(System.getProperty('idea.disable.gradle.resource.filtering', 'false').toBoolean()) {
      return [includes, excludes, filterReaders]
    }

    try {
      if (filterableTask instanceof ContentFilterable && filterableTask.metaClass.respondsTo(filterableTask, "getMainSpec")) {
        def properties = filterableTask.getMainSpec().properties
        def copyActions = properties?.allCopyActions ?: properties?.copyActions

        if(copyActions) {
          copyActions.each { Action<? super FileCopyDetails> action ->
            if (action.hasProperty('val$filterType') && action.hasProperty('val$properties')) {
              def filterType = (action?.val$filterType as Class).name
              def filter = [filterType: filterType] as DefaultExternalFilter
              def props = action?.val$properties
              if (props) {
                filter.propertiesAsJsonMap = new GsonBuilder().create().toJson(props);
              }
              filterReaders << filter
            }
            else if (action.class.simpleName.equals('RenamingCopyAction') && action.hasProperty('transformer')) {
              if (action.transformer.hasProperty('matcher') && action?.transformer.hasProperty('replacement')) {
                String pattern = action?.transformer?.matcher.pattern().pattern
                String replacement = action?.transformer?.replacement
                def filter = [filterType: 'RenamingCopyFilter'] as DefaultExternalFilter
                if(pattern && replacement){
                  filter.propertiesAsJsonMap = new GsonBuilder().create().toJson([pattern: pattern, replacement: replacement]);
                  filterReaders << filter
                }
              }
            }
//          else {
//            project.logger.error(
//              ErrorMessageBuilder.create(project, "Resource configuration errors")
//                .withDescription("Unsupported copy action found: " + action.class.name).build())
//          }
          }
        }
      }
    }
    catch (Exception ignore) {
//      project.logger.error(
//        ErrorMessageBuilder.create(project, e, "Resource configuration errors")
//          .withDescription("Unable to resolve resources filtering configuration").build())
    }

    return [includes, excludes, filterReaders]
  }


  private static String wrap(Object o) {
    return o instanceof CharSequence ? o.toString() : ""
  }

  @Override
  public ErrorMessageBuilder getErrorMessageBuilder(Project project, Exception e) {
    return ErrorMessageBuilder.create(
      project, e, "Project resolve errors"
    ).withDescription("Unable to resolve additional project configuration.")
  }
}
