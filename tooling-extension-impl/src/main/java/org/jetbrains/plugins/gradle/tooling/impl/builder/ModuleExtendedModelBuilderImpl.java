/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.tooling.impl.builder;

import consulo.gradle.tooling.impl.buildler.util.ReflectionMethod;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.SourceSetOutput;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.testing.Test;
import org.gradle.plugins.ide.idea.IdeaPlugin;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.jetbrains.plugins.gradle.tooling.ErrorMessageBuilder;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService;
import org.jetbrains.plugins.gradle.tooling.impl.internal.IdeaCompilerOutputImpl;
import org.jetbrains.plugins.gradle.tooling.impl.internal.IdeaContentRootImpl;
import org.jetbrains.plugins.gradle.tooling.impl.internal.IdeaSourceDirectoryImpl;
import org.jetbrains.plugins.gradle.tooling.impl.internal.ModuleExtendedModelImpl;
import org.jetbrains.plugins.gradle.tooling.model.ExtIdeaContentRoot;
import org.jetbrains.plugins.gradle.tooling.model.ModuleExtendedModel;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Vladislav.Soroka
 * @since 11/5/13
 */
public class ModuleExtendedModelBuilderImpl implements ModelBuilderService {
  private static final ReflectionMethod<File, Test> Test__getTestClassesDir = new ReflectionMethod<>(Test.class, "getTestClassesDir");
  private static final ReflectionMethod<File, SourceSetOutput> SourceSetOutput__getClassesDir =
    new ReflectionMethod<>(SourceSetOutput.class, "getClassesDir");
  private static final ReflectionMethod<Iterable<File>, SourceSetOutput> SourceSetOutput__getClassesDirs =
    new ReflectionMethod<>(SourceSetOutput.class, "getClassesDirs");

  private static final String SOURCE_SETS_PROPERTY = "sourceSets";
  private static final String TEST_SRC_DIRS_PROPERTY = "testSrcDirs";

  @Override
  public boolean canBuild(String modelName) {
    return ModuleExtendedModel.class.getName().equals(modelName);
  }

  @Nullable
  @Override
  public Object buildAll(String modelName, Project project) {
    final String moduleName = project.getName();
    final String moduleGroup = project.getGroup().toString();
    final String moduleVersion = project.getVersion().toString();
    final File buildDir = project.getBuildDir();

    final ModuleExtendedModelImpl moduleVersionModel = new ModuleExtendedModelImpl(moduleName, moduleGroup, moduleVersion, buildDir);

    final List<File> artifacts = new ArrayList<>();
    for (Task task : project.getTasks()) {
      if (task instanceof Jar) {
        Jar jar = (Jar)task;
        artifacts.add(jar.getArchivePath());
      }
    }

    moduleVersionModel.setArtifacts(artifacts);

    final Set<String> sourceDirectories = new HashSet<>();
    final Set<String> testDirectories = new HashSet<>();
    final Set<String> resourceDirectories = new HashSet<>();
    final Set<String> testResourceDirectories = new HashSet<>();

    final List<File> testClassesDirs = new ArrayList<>();
    for (Task task : project.getTasks()) {
      if (task instanceof Test) {
        Test test = (Test)task;

        File testDir = Test__getTestClassesDir.invoke(test);
        if (testDir != null) {
          testClassesDirs.add(testDir);
        }

        if (test.hasProperty(TEST_SRC_DIRS_PROPERTY)) {
          Object testSrcDirs = test.property(TEST_SRC_DIRS_PROPERTY);
          if (testSrcDirs instanceof Iterable) {
            for (Object dir : Iterable.class.cast(testSrcDirs)) {
              addFilePath(testDirectories, dir);
            }
          }
        }
      }
    }

    IdeaCompilerOutputImpl compilerOutput = new IdeaCompilerOutputImpl();

    if (project.hasProperty(SOURCE_SETS_PROPERTY)) {
      Object sourceSets = project.property(SOURCE_SETS_PROPERTY);
      if (sourceSets instanceof SourceSetContainer) {
        SourceSetContainer sourceSetContainer = (SourceSetContainer)sourceSets;
        for (SourceSet sourceSet : sourceSetContainer) {
          SourceSetOutput output = sourceSet.getOutput();
          if (SourceSet.TEST_SOURCE_SET_NAME.equals(sourceSet.getName())) {
            for (File file : getClassesDirs(output)) {
              compilerOutput.setTestClassesDir(file);
            }
            compilerOutput.setTestResourcesDir(output.getResourcesDir());
          }

          if (SourceSet.MAIN_SOURCE_SET_NAME.equals(sourceSet.getName())) {
            for (File file : getClassesDirs(output)) {
              compilerOutput.setMainClassesDir(file);
            }
            compilerOutput.setMainResourcesDir(output.getResourcesDir());
          }

          for (File javaSrcDir : sourceSet.getAllJava().getSrcDirs()) {
            boolean isTestDir = isTestDir(sourceSet, testClassesDirs);
            addFilePath(isTestDir ? testDirectories : sourceDirectories, javaSrcDir);
          }
          for (File resourcesSrcDir : sourceSet.getResources().getSrcDirs()) {
            boolean isTestDir = isTestDir(sourceSet, testClassesDirs);
            addFilePath(isTestDir ? testResourceDirectories : resourceDirectories, resourcesSrcDir);
          }
        }
      }
    }

    File projectDir = project.getProjectDir();
    IdeaContentRootImpl contentRoot = new IdeaContentRootImpl(projectDir);

    final Set<String> ideaSourceDirectories = new HashSet<>();
    final Set<String> ideaTestDirectories = new HashSet<>();
    final Set<File> excludeDirectories = new HashSet<>();

    enrichDataFromIdeaPlugin(project, excludeDirectories, ideaSourceDirectories, ideaTestDirectories);

    if (ideaSourceDirectories.isEmpty()) {
      sourceDirectories.clear();
      resourceDirectories.clear();
    }
    if (ideaTestDirectories.isEmpty()) {
      testDirectories.clear();
      testResourceDirectories.clear();
    }

    ideaSourceDirectories.removeAll(resourceDirectories);
    sourceDirectories.removeAll(ideaTestDirectories);
    sourceDirectories.addAll(ideaSourceDirectories);
    ideaTestDirectories.removeAll(testResourceDirectories);
    testDirectories.addAll(ideaTestDirectories);


    // ensure disjoint directories with different type
    resourceDirectories.removeAll(sourceDirectories);
    testDirectories.removeAll(sourceDirectories);
    testResourceDirectories.removeAll(testDirectories);

    for (String javaDir : sourceDirectories) {
      contentRoot.addSourceDirectory(new IdeaSourceDirectoryImpl(new File(javaDir), false));
    }
    for (String testDir : testDirectories) {
      contentRoot.addTestDirectory(new IdeaSourceDirectoryImpl(new File(testDir), false));
    }
    for (String resourceDir : resourceDirectories) {
      contentRoot.addResourceDirectory(new IdeaSourceDirectoryImpl(new File(resourceDir), false));
    }
    for (String testResourceDir : testResourceDirectories) {
      contentRoot.addTestResourceDirectory(new IdeaSourceDirectoryImpl(new File(testResourceDir), false));
    }
    for (File excludeDir : excludeDirectories) {
      contentRoot.addExcludeDirectory(excludeDir);
    }

    moduleVersionModel.setContentRoots(Collections.<ExtIdeaContentRoot>singleton(contentRoot));
    moduleVersionModel.setCompilerOutput(compilerOutput);
    return moduleVersionModel;
  }

  @Nonnull
  @Override
  public ErrorMessageBuilder getErrorMessageBuilder(@Nonnull Project project, @Nonnull Exception e) {
    return ErrorMessageBuilder.create(
      project, e, "Other"
    ).withDescription("Unable to resolve all content root directories");
  }

  private static boolean isTestDir(SourceSet sourceSet, List<File> testClassesDirs) {
    if (SourceSet.TEST_SOURCE_SET_NAME.equals(sourceSet.getName())) {
      return true;
    }
    if (SourceSet.MAIN_SOURCE_SET_NAME.equals(sourceSet.getName())) {
      return false;
    }

    Iterable<File> classesDirs = getClassesDirs(sourceSet.getOutput());
    Iterator<File> iterator = classesDirs.iterator();
    File sourceSetClassesDir = iterator.hasNext() ? iterator.next() : null;

    for (File testClassesDir : testClassesDirs) {
      do {
        if (sourceSetClassesDir.getPath().equals(testClassesDir.getPath())) {
          return true;
        }
      }
      while ((testClassesDir = testClassesDir.getParentFile()) != null);
    }

    return false;
  }

  private static Iterable<File> getClassesDirs(SourceSetOutput output) {
    File file = SourceSetOutput__getClassesDir.invoke(output);
    if (file != null) {
      return Collections.singleton(file);
    }

    Iterable<File> files = SourceSetOutput__getClassesDirs.invoke(output);
    if (files != null) {
      return files;
    }
    return Collections.emptyList();
  }

  private static void addFilePath(Set<String> filePathSet, Object file) {
    if (file instanceof File) {
      try {
        filePathSet.add(((File)file).getCanonicalPath());
      }
      catch (IOException ignore) {
      }
    }
  }

  private static void enrichDataFromIdeaPlugin(Project project,
                                               Set<File> excludeDirectories,
                                               Set<String> javaDirectories,
                                               Set<String> testDirectories) {
    IdeaPlugin ideaPlugin = project.getPlugins().getPlugin(IdeaPlugin.class);
    if (ideaPlugin == null) {
      return;
    }

    IdeaModel ideaModel = ideaPlugin.getModel();
    if (ideaModel == null || ideaModel.getModule() == null) {
      return;
    }

    for (File excludeDir : ideaModel.getModule().getExcludeDirs()) {
      excludeDirectories.add(excludeDir);
    }
    for (File file : ideaModel.getModule().getSourceDirs()) {
      javaDirectories.add(file.getPath());
    }
    for (File file : ideaModel.getModule().getTestSourceDirs()) {
      testDirectories.add(file.getPath());
    }
  }
}
