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

import com.intellij.java.impl.externalSystem.JavaProjectData;
import com.intellij.java.language.LanguageLevel;
import consulo.annotation.component.ExtensionImpl;
import consulo.externalSystem.model.DataNode;
import consulo.externalSystem.model.ProjectKeys;
import consulo.externalSystem.model.project.*;
import consulo.externalSystem.model.task.TaskData;
import consulo.externalSystem.rt.model.*;
import consulo.externalSystem.service.project.ProjectData;
import consulo.externalSystem.util.ExternalSystemApiUtil;
import consulo.externalSystem.util.ExternalSystemDebugEnvironment;
import consulo.gradle.GradleConstants;
import consulo.gradle.service.project.GradleProjectResolverExtension;
import consulo.gradle.service.project.ProjectResolverContext;
import consulo.http.HttpProxyManager;
import consulo.logging.Logger;
import consulo.module.content.layer.orderEntry.DependencyScope;
import consulo.platform.Platform;
import consulo.util.collection.ContainerUtil;
import consulo.util.io.FileUtil;
import consulo.util.lang.CharArrayUtil;
import consulo.util.lang.Couple;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.GradleModuleVersion;
import org.gradle.tooling.model.GradleTask;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.gradle.tooling.model.idea.*;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.gradle.model.data.BuildScriptClasspathData;
import org.jetbrains.plugins.gradle.service.project.data.ExternalProjectDataService;
import org.jetbrains.plugins.gradle.tooling.model.BuildScriptClasspathModel;
import org.jetbrains.plugins.gradle.tooling.model.ExtIdeaCompilerOutput;
import org.jetbrains.plugins.gradle.tooling.model.ModuleExtendedModel;
import org.jetbrains.plugins.gradle.tooling.model.ProjectImportAction;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

/**
 * {@link BaseGradleProjectResolverExtension} provides base implementation of Gradle project resolver.
 *
 * @author Vladislav.Soroka
 * @since 2013-10-14
 */
@ExtensionImpl(order = "last")
public class BaseGradleProjectResolverExtension implements GradleProjectResolverExtension {
    private static final Logger LOG = Logger.getInstance(BaseGradleProjectResolverExtension.class);

    @Nonnull
    @NonNls
    private static final String UNRESOLVED_DEPENDENCY_PREFIX = "unresolved dependency - ";
    private static final String MAIN_SOURCE_SET = "main";
    private static final String TEST_SOURCE_SET = "test";

    @Nonnull
    private ProjectResolverContext resolverCtx;
    @Nonnull
    private final BaseProjectImportErrorHandler myErrorHandler = new BaseProjectImportErrorHandler();

    @Override
    public void setProjectResolverContext(@Nonnull ProjectResolverContext projectResolverContext) {
        resolverCtx = projectResolverContext;
    }

    @Override
    public void setNext(@Nonnull GradleProjectResolverExtension next) {
        // should be the last extension in the chain
    }

    @Nullable
    @Override
    public GradleProjectResolverExtension getNext() {
        return null;
    }

    @Nonnull
    @Override
    public ProjectData createProject() {
        final String projectDirPath = resolverCtx.getProjectPath();
        final IdeaProject ideaProject = ((ProjectImportAction.AllModels) resolverCtx.getModels()).getIdeaProject();
        return new ProjectData(GradleConstants.SYSTEM_ID, ideaProject.getName(), projectDirPath, projectDirPath);
    }

    @Nonnull
    @Override
    public JavaProjectData createJavaProjectData() {
        final String projectDirPath = resolverCtx.getProjectPath();
        final IdeaProject ideaProject = ((ProjectImportAction.AllModels) resolverCtx.getModels()).getIdeaProject();

        // Gradle API doesn't expose gradleProject compile output path yet.
        JavaProjectData javaProjectData = new JavaProjectData(GradleConstants.SYSTEM_ID, projectDirPath + "/build/classes");
        javaProjectData.setJdkVersion(ideaProject.getJdkName());

        LanguageLevel languageLevel = consulo.ide.impl.idea.openapi.util.text.StringUtil.parseEnum(
            ideaProject.getLanguageLevel().getLevel(),
            null,
            LanguageLevel.class
        );
        if (languageLevel != null) {
            javaProjectData.setLanguageLevel(languageLevel);
        }
        else {
            javaProjectData.setLanguageLevel(ideaProject.getLanguageLevel().getLevel());
        }
        return javaProjectData;
    }

    @Override
    public void populateProjectExtraModels(@Nonnull IdeaProject gradleProject, @Nonnull DataNode<ProjectData> ideProject) {
        final ExternalProject externalProject = ((ProjectImportAction.AllModels) resolverCtx.getModels()).getExtraProject(ExternalProject.class);
        if (externalProject != null) {
            ideProject.createChild(ExternalProjectDataService.KEY, externalProject);
        }
    }

    @Nonnull
    @Override
    public ModuleData createModule(@Nonnull IdeaModule gradleModule, @Nonnull ProjectData projectData) {
        final String moduleName = gradleModule.getName();
        if (moduleName == null) {
            throw new IllegalStateException("Module with undefined name detected: " + gradleModule);
        }

        final String moduleConfigPath = getModuleConfigPath(gradleModule, projectData.getLinkedExternalProjectPath());

        if (ExternalSystemDebugEnvironment.DEBUG_ORPHAN_MODULES_PROCESSING) {
            LOG.info(String.format(
                "Creating module data ('%s') with the external config path: '%s'",
                gradleModule.getGradleProject().getPath(),
                moduleConfigPath
            ));
        }

        String gradlePath = gradleModule.getGradleProject().getPath();
        String moduleId = StringUtil.isEmpty(gradlePath) || ":".equals(gradlePath) ? moduleName : gradlePath;
        ModuleData moduleData = new ModuleData(moduleId, GradleConstants.SYSTEM_ID, moduleName, moduleConfigPath, moduleConfigPath);

        final ModuleExtendedModel moduleExtendedModel = ((ProjectImportAction.AllModels) resolverCtx.getModels()).getExtraProject(gradleModule, ModuleExtendedModel.class);
        if (moduleExtendedModel != null) {
            moduleData.setGroup(moduleExtendedModel.getGroup());
            moduleData.setVersion(moduleExtendedModel.getVersion());
            moduleData.setArtifacts(moduleExtendedModel.getArtifacts());
        }
        return moduleData;
    }

    @Override
    public void populateModuleExtraModels(@Nonnull IdeaModule gradleModule, @Nonnull DataNode<ModuleData> ideModule) {
        final BuildScriptClasspathModel buildScriptClasspathModel =
            ((ProjectImportAction.AllModels) resolverCtx.getModels()).getExtraProject(gradleModule, BuildScriptClasspathModel.class);
        final List<BuildScriptClasspathData.ClasspathEntry> classpathEntries;
        if (buildScriptClasspathModel != null) {
            classpathEntries = ContainerUtil.map(
                buildScriptClasspathModel.getClasspath(),
                model -> new BuildScriptClasspathData.ClasspathEntry(
                    model.getClasses(),
                    model.getSources(),
                    model.getJavadoc()
                )
            );
        }
        else {
            classpathEntries = List.of();
        }
        BuildScriptClasspathData buildScriptClasspathData = new BuildScriptClasspathData(GradleConstants.SYSTEM_ID, classpathEntries);
        ideModule.createChild(BuildScriptClasspathData.KEY, buildScriptClasspathData);
    }

    @Override
    public void populateModuleContentRoots(@Nonnull IdeaModule gradleModule, @Nonnull DataNode<ModuleData> ideModule) {
        DomainObjectSet<? extends IdeaContentRoot> contentRoots;
        ModuleExtendedModel moduleExtendedModel = ((ProjectImportAction.AllModels) resolverCtx.getModels()).getExtraProject(gradleModule, ModuleExtendedModel.class);
        if (moduleExtendedModel != null) {
            contentRoots = moduleExtendedModel.getContentRoots();
        }
        else {
            contentRoots = gradleModule.getContentRoots();
        }

        if (contentRoots == null) {
            return;
        }
        for (IdeaContentRoot gradleContentRoot : contentRoots) {
            if (gradleContentRoot == null) {
                continue;
            }

            File rootDirectory = gradleContentRoot.getRootDirectory();
            if (rootDirectory == null) {
                continue;
            }

            ContentRootData ideContentRoot = new ContentRootData(GradleConstants.SYSTEM_ID, rootDirectory.getAbsolutePath());
            ideModule.getData().setModuleDirPath(ideContentRoot.getRootPath());
            populateContentRoot(ideContentRoot, ExternalSystemSourceType.SOURCE, gradleContentRoot.getSourceDirectories());
            populateContentRoot(ideContentRoot, ExternalSystemSourceType.TEST, gradleContentRoot.getTestDirectories());

            populateContentRoot(ideContentRoot, ExternalSystemSourceType.RESOURCE, gradleContentRoot.getResourceDirectories());
            populateContentRoot(
                ideContentRoot,
                ExternalSystemSourceType.TEST_RESOURCE,
                gradleContentRoot.getTestResourceDirectories()
            );

            Set<File> excluded = gradleContentRoot.getExcludeDirectories();
            if (excluded != null) {
                for (File file : excluded) {
                    ideContentRoot.storePath(ExternalSystemSourceType.EXCLUDED, file.getAbsolutePath());
                }
            }
            ideModule.createChild(ProjectKeys.CONTENT_ROOT, ideContentRoot);
        }
    }


    @Override
    public void populateModuleCompileOutputSettings(@Nonnull IdeaModule gradleModule, @Nonnull DataNode<ModuleData> ideModule) {
        IdeaCompilerOutput moduleCompilerOutput = gradleModule.getCompilerOutput();

        File sourceCompileOutputPath = null;
        File testCompileOutputPath = null;
        File resourceCompileOutputPath = null;
        File testResourceCompileOutputPath = null;
        boolean inheritOutputDirs = false;

        ModuleData moduleData = ideModule.getData();
        if (moduleCompilerOutput != null) {
            sourceCompileOutputPath = moduleCompilerOutput.getOutputDir();
            testCompileOutputPath = moduleCompilerOutput.getTestOutputDir();
            inheritOutputDirs = moduleCompilerOutput.getInheritOutputDirs();
        }

        ModuleExtendedModel extraModel = ((ProjectImportAction.AllModels) resolverCtx.getModels()).getExtraProject(gradleModule, ModuleExtendedModel.class);
        if (extraModel != null) {
            ExtIdeaCompilerOutput compilerOutput = extraModel.getCompilerOutput();

            if (compilerOutput != null) {
                if (sourceCompileOutputPath == null) {
                    sourceCompileOutputPath = compilerOutput.getMainClassesDir();
                }

                if (testCompileOutputPath == null) {
                    testCompileOutputPath = compilerOutput.getTestClassesDir();
                }

                if (resourceCompileOutputPath == null) {
                    resourceCompileOutputPath = compilerOutput.getMainClassesDir();
                }

                if (testResourceCompileOutputPath == null) {
                    testResourceCompileOutputPath = compilerOutput.getTestClassesDir();
                }
            }
        }

        if (sourceCompileOutputPath != null
            && testCompileOutputPath != null
            && resourceCompileOutputPath != null
            && testResourceCompileOutputPath != null
        ) {
            inheritOutputDirs = false;
        }

        ExternalProject externalProject = ((ProjectImportAction.AllModels) resolverCtx.getModels()).getExtraProject(gradleModule, ExternalProject.class);
        if (externalProject != null) {
            externalProject = new DefaultExternalProject(externalProject);
        }

        if (!inheritOutputDirs && (sourceCompileOutputPath == null || testCompileOutputPath == null)) {
            sourceCompileOutputPath = getCompileOutputPath(externalProject, MAIN_SOURCE_SET, ExternalSystemSourceType.SOURCE);
            resourceCompileOutputPath = getCompileOutputPath(externalProject, MAIN_SOURCE_SET, ExternalSystemSourceType.RESOURCE);
            testCompileOutputPath = getCompileOutputPath(externalProject, TEST_SOURCE_SET, ExternalSystemSourceType.TEST);
            testResourceCompileOutputPath = getCompileOutputPath(externalProject, TEST_SOURCE_SET, ExternalSystemSourceType.TEST_RESOURCE);
        }
        else {
            resourceCompileOutputPath = sourceCompileOutputPath;
            testResourceCompileOutputPath = testCompileOutputPath;

            if (externalProject != null) {
                final ExternalSourceSet mainSourceSet = externalProject.getSourceSets().get(MAIN_SOURCE_SET);
                if (mainSourceSet != null) {
                    final ExternalSourceDirectorySet resourceDirectories =
                        mainSourceSet.getSources().get(ExternalSystemSourceType.RESOURCE);
                    if (resourceDirectories instanceof DefaultExternalSourceDirectorySet resourceDirSet) {
                        resourceDirSet.setOutputDir(sourceCompileOutputPath);
                    }
                }
                final ExternalSourceSet testSourceSet = externalProject.getSourceSets().get(TEST_SOURCE_SET);
                if (testSourceSet != null) {
                    final ExternalSourceDirectorySet testResourceDirectories = testSourceSet.getSources().get(ExternalSystemSourceType
                        .TEST_RESOURCE);
                    if (testResourceDirectories instanceof DefaultExternalSourceDirectorySet testResourceDirSet) {
                        testResourceDirSet.setOutputDir(testCompileOutputPath);
                    }
                }

                final DataNode<ProjectData> projectDataNode = ExternalSystemApiUtil.findParent(ideModule, ProjectKeys.PROJECT);
                assert projectDataNode != null;
                projectDataNode.createOrReplaceChild(ExternalProjectDataService.KEY, externalProject);
            }
        }

        if (sourceCompileOutputPath != null) {
            moduleData.setCompileOutputPath(ExternalSystemSourceType.SOURCE, sourceCompileOutputPath.getAbsolutePath());
        }
        if (resourceCompileOutputPath != null) {
            moduleData.setCompileOutputPath(ExternalSystemSourceType.RESOURCE, resourceCompileOutputPath.getAbsolutePath());
        }
        if (testCompileOutputPath != null) {
            moduleData.setCompileOutputPath(ExternalSystemSourceType.TEST, testCompileOutputPath.getAbsolutePath());
        }
        if (testResourceCompileOutputPath != null) {
            moduleData.setCompileOutputPath(ExternalSystemSourceType.TEST_RESOURCE, testResourceCompileOutputPath.getAbsolutePath());
        }

        moduleData.setInheritProjectCompileOutputPath(inheritOutputDirs || sourceCompileOutputPath == null);
    }

    @Nullable
    private static File getCompileOutputPath(
        @Nullable ExternalProject externalProject,
        @Nonnull String sourceSetName,
        @Nonnull ExternalSystemSourceType sourceType
    ) {
        if (externalProject == null) {
            return null;
        }
        final ExternalSourceSet sourceSet = externalProject.getSourceSets().get(sourceSetName);
        if (sourceSet == null) {
            return null;
        }

        final ExternalSourceDirectorySet directorySet = sourceSet.getSources().get(sourceType);
        return directorySet != null ? directorySet.getOutputDir() : null;
    }

    @Override
    public void populateModuleDependencies(
        @Nonnull IdeaModule gradleModule,
        @Nonnull DataNode<ModuleData> ideModule,
        @Nonnull DataNode<ProjectData> ideProject
    ) {
        final List<? extends IdeaDependency> dependencies = gradleModule.getDependencies().getAll();

        if (dependencies == null) {
            return;
        }

        for (IdeaDependency dependency : dependencies) {
            if (dependency == null) {
                continue;
            }
            DependencyScope scope = parseScope(dependency.getScope());

            if (dependency instanceof IdeaModuleDependency) {
                ModuleDependencyData d = buildDependency(ideModule, (IdeaModuleDependency) dependency, ideProject);
                d.setExported(dependency.getExported());
                if (scope != null) {
                    d.setScope(scope);
                }
                ideModule.createChild(ProjectKeys.MODULE_DEPENDENCY, d);
            }
            else if (dependency instanceof IdeaSingleEntryLibraryDependency singleEntryLibraryDependency) {
                LibraryDependencyData d =
                    buildDependency(gradleModule, ideModule, singleEntryLibraryDependency, ideProject);
                d.setExported(dependency.getExported());
                if (scope != null) {
                    d.setScope(scope);
                }
                ideModule.createChild(ProjectKeys.LIBRARY_DEPENDENCY, d);
            }
        }
    }

    @Nonnull
    @Override
    public Collection<TaskData> populateModuleTasks(
        @Nonnull IdeaModule gradleModule,
        @Nonnull DataNode<ModuleData> ideModule,
        @Nonnull DataNode<ProjectData> ideProject
    ) throws IllegalArgumentException, IllegalStateException {
        final Collection<TaskData> tasks = new ArrayList<>();
        final String moduleConfigPath = ideModule.getData().getLinkedExternalProjectPath();

        for (GradleTask task : gradleModule.getGradleProject().getTasks()) {
            String taskName = task.getName();
            if (taskName == null || taskName.trim().isEmpty() || isIdeaTask(taskName)) {
                continue;
            }
            TaskData taskData = new TaskData(GradleConstants.SYSTEM_ID, taskName, moduleConfigPath, task.getDescription());
            ideModule.createChild(ProjectKeys.TASK, taskData);
            tasks.add(taskData);
        }

        return tasks;
    }

    @Nonnull
    @Override
    public Collection<TaskData> filterRootProjectTasks(@Nonnull List<TaskData> allTasks) {
        return allTasks;
    }

    @Nonnull
    @Override
    public Set<Class> getExtraProjectModelClasses() {
        return Set.of(
            GradleBuild.class,
            ModuleExtendedModel.class,
            BuildScriptClasspathModel.class
        );
    }

    @Nonnull
    @Override
    public List<Couple<String>> getExtraJvmArgs() {
        final List<Couple<String>> extraJvmArgs = new ArrayList<>();
        final HttpProxyManager httpConfigurable = HttpProxyManager.getInstance();

        List<String> proxyExceptions = httpConfigurable.getProxyExceptions();
        if (!proxyExceptions.isEmpty()) {
            extraJvmArgs.add(Couple.of(
                "http.nonProxyHosts",
                StringUtil.join(proxyExceptions, consulo.ide.impl.idea.openapi.util.text.StringUtil.TRIMMER, "|")
            ));
        }

        for (Pair<String, String> pair : httpConfigurable.getJvmProperties(false, null)) {
            extraJvmArgs.add(Couple.of(pair.getFirst(), pair.getSecond()));
        }

        return extraJvmArgs;
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
        return myErrorHandler.getUserFriendlyError(error, projectPath, buildFilePath);
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
        if (!StringUtil.isEmpty(debuggerSetup)) {
            final String[] lines = {
                "gradle.taskGraph.beforeTask { Task task ->",
                "    if (task instanceof JavaForkOptions) {",
                "        task.jvmArgs '" + debuggerSetup.trim() + '\'',
                "    }" + "}",
            };
            final String script = StringUtil.join(lines, Platform.current().os().lineSeparator().getSeparatorString());
            initScriptConsumer.accept(script);
        }
    }


    @Nonnull
    private String getModuleConfigPath(@Nonnull IdeaModule gradleModule, @Nonnull String rootProjectPath) {
        GradleBuild build = ((ProjectImportAction.AllModels) resolverCtx.getModels()).getExtraProject(gradleModule, GradleBuild.class);
        if (build != null) {
            String gradlePath = gradleModule.getGradleProject().getPath();
            File moduleDirPath = getModuleDirPath(build, gradlePath);
            if (moduleDirPath == null) {
                throw new IllegalStateException(String.format("Unable to find root directory for module '%s'", gradleModule.getName()));
            }
            try {
                return ExternalSystemApiUtil.toCanonicalPath(moduleDirPath.getCanonicalPath());
            }
            catch (IOException e) {
                LOG.warn("construction of the canonical path for the module fails", e);
            }
        }

        return GradleUtil.getConfigPath(gradleModule.getGradleProject(), rootProjectPath);
    }

    /**
     * Returns the physical path of the module's root directory (the path in the file system.)
     * <p>
     * It is important to note that Gradle has its own "logical" path that may or may not be equal
     * to the physical path of a Gradle project.
     * For example, the sub-project at ${projectRootDir}/apps/app will have the Gradle path :apps:app. Gradle also allows mapping physical
     * paths to a different logical path. For example, in settings.gradle:
     * <pre>
     *   include ':app'
     *   project(':app').projectDir = new File(rootDir, 'apps/app')
     * </pre>
     * In this example, sub-project at ${projectRootDir}/apps/app will have the Gradle path :app.
     * </p>
     *
     * @param build contains information about the root Gradle project and its sub-projects. Such information includes the physical path of
     *              the root Gradle project and its sub-projects.
     * @param path  the Gradle "logical" path. This path uses colon as separator, and may or may not be equal to the physical path of a
     *              Gradle project.
     * @return the physical path of the module's root directory.
     */
    @Nullable
    static File getModuleDirPath(@Nonnull GradleBuild build, @Nonnull String path) {
        for (BasicGradleProject project : build.getProjects()) {
            if (project.getPath().equals(path)) {
                return project.getProjectDirectory();
            }
        }
        return null;
    }

    /**
     * Stores information about given directories at the given content root
     *
     * @param contentRoot target paths info holder
     * @param type        type of data located at the given directories
     * @param dirs        directories which paths should be stored at the given content root
     * @throws IllegalArgumentException if specified by {@link ContentRootData#storePath(ExternalSystemSourceType, String)}
     */
    private static void populateContentRoot(
        @Nonnull ContentRootData contentRoot,
        @Nonnull ExternalSystemSourceType type,
        @Nullable Iterable<? extends IdeaSourceDirectory> dirs
    ) throws IllegalArgumentException {
        if (dirs == null) {
            return;
        }
        for (IdeaSourceDirectory dir : dirs) {
            contentRoot.storePath(type, dir.getDirectory().getAbsolutePath());
        }
    }

    @Nullable
    private static DependencyScope parseScope(@Nullable IdeaDependencyScope scope) {
        if (scope == null) {
            return null;
        }
        String scopeAsString = scope.getScope();
        if (scopeAsString == null) {
            return null;
        }
        for (DependencyScope dependencyScope : DependencyScope.values()) {
            if (scopeAsString.equalsIgnoreCase(dependencyScope.toString())) {
                return dependencyScope;
            }
        }
        return null;
    }

    @Nonnull
    private static ModuleDependencyData buildDependency(
        @Nonnull DataNode<ModuleData> ownerModule,
        @Nonnull IdeaModuleDependency dependency,
        @Nonnull DataNode<ProjectData> ideProject
    ) throws IllegalStateException {
        String moduleName = dependency.getTargetModuleName();
        if (moduleName == null) {
            throw new IllegalStateException(String.format(
                "Can't parse gradle module dependency '%s'. Reason: referenced module name is undefined "
                    + "(module: '%s') ",
                dependency,
                moduleName
            ));
        }

        Set<String> registeredModuleNames = new HashSet<>();
        Collection<DataNode<ModuleData>> modulesDataNode = ExternalSystemApiUtil.getChildren(ideProject, ProjectKeys.MODULE);
        for (DataNode<ModuleData> moduleDataNode : modulesDataNode) {
            String name = moduleDataNode.getData().getExternalName();
            registeredModuleNames.add(name);
            if (name.equals(moduleName)) {
                return new ModuleDependencyData(ownerModule.getData(), moduleDataNode.getData());
            }
        }
        throw new IllegalStateException(String.format(
            "Can't parse gradle module dependency '%s'. Reason: no module with such name (%s) is found. "
                + "Registered modules: %s",
            dependency,
            moduleName,
            registeredModuleNames
        ));
    }

    @Nonnull
    private LibraryDependencyData buildDependency(
        @Nonnull IdeaModule gradleModule,
        @Nonnull DataNode<ModuleData> ownerModule,
        @Nonnull IdeaSingleEntryLibraryDependency dependency,
        @Nonnull DataNode<ProjectData> ideProject
    ) throws IllegalStateException {
        File binaryPath = dependency.getFile();
        if (binaryPath == null) {
            throw new IllegalStateException(String.format(
                "Can't parse external library dependency '%s'. Reason: it doesn't specify path to the " +
                    "binaries",
                dependency
            ));
        }

        String libraryName;
        final GradleModuleVersion moduleVersion = dependency.getGradleModuleVersion();
        LibraryLevel level;

        // Gradle API doesn't explicitly provide information about unresolved libraries (http://issues.gradle.org/browse/GRADLE-1995).
        // That's why we use this dirty hack here.
        boolean unresolved = binaryPath.getPath().startsWith(UNRESOLVED_DEPENDENCY_PREFIX);

        if (moduleVersion == null) {
            // use module library level if the dependency does not originate from a remote repository.
            level = LibraryLevel.MODULE;

            if (binaryPath.isFile()) {
                libraryName = FileUtil.getNameWithoutExtension(binaryPath);
            }
            else {
                libraryName = FileUtil.sanitizeFileName(binaryPath.getPath());
            }

            if (unresolved) {
                // Gradle uses names like 'unresolved dependency - commons-collections commons-collections 3.2'
                // for unresolved dependencies.
                libraryName = binaryPath.getName().substring(UNRESOLVED_DEPENDENCY_PREFIX.length());
                int i = libraryName.indexOf(' ');
                if (i >= 0) {
                    i = CharArrayUtil.shiftForward(libraryName, i + 1, " ");
                }

                if (i >= 0 && i < libraryName.length()) {
                    int dependencyNameIndex = i;
                    i = libraryName.indexOf(' ', dependencyNameIndex);
                    if (i > 0) {
                        libraryName =
                            String.format("%s-%s", libraryName.substring(dependencyNameIndex, i), libraryName.substring(i + 1));
                    }
                }
            }
        }
        else {
            level = LibraryLevel.PROJECT;
            libraryName = String.format("%s:%s:%s", moduleVersion.getGroup(), moduleVersion.getName(), moduleVersion.getVersion());
        }

        final LibraryData library = new LibraryData(GradleConstants.SYSTEM_ID, libraryName, unresolved);
        if (!unresolved) {
            library.addPath(LibraryPathType.BINARY, binaryPath.getAbsolutePath());
        }

        File sourcePath = dependency.getSource();
        if (!unresolved && sourcePath != null) {
            library.addPath(LibraryPathType.SOURCE, sourcePath.getAbsolutePath());
        }

        if (!unresolved && sourcePath == null) {
            attachGradleSdkSources(gradleModule, libraryName, binaryPath, library);
        }

        File javadocPath = dependency.getJavadoc();
        if (!unresolved && javadocPath != null) {
            library.addPath(LibraryPathType.DOC, javadocPath.getAbsolutePath());
        }

        if (level == LibraryLevel.PROJECT && !GradleProjectResolverUtil.linkProjectLibrary(ideProject, library)) {
            level = LibraryLevel.MODULE;
        }

        return new LibraryDependencyData(ownerModule.getData(), library, level);
    }

    private void attachGradleSdkSources(
        @Nonnull IdeaModule gradleModule,
        @Nonnull final String libName,
        @Nullable final File libFile,
        LibraryData library
    ) {
        if (libFile == null || !libName.startsWith("gradle-")) {
            return;
        }

        final BuildScriptClasspathModel buildScriptClasspathModel =
            ((ProjectImportAction.AllModels) resolverCtx.getModels()).getExtraProject(gradleModule, BuildScriptClasspathModel.class);
        if (buildScriptClasspathModel == null) {
            return;
        }
        final File gradleHomeDir = buildScriptClasspathModel.getGradleHomeDir();
        if (gradleHomeDir == null) {
            return;
        }

        if (!FileUtil.isAncestor(gradleHomeDir, libFile, true)) {
            return;
        }

        File libOrPluginsFile = libFile.getParentFile();
        if (libOrPluginsFile != null && ("plugins".equals(libOrPluginsFile.getName()))) {
            libOrPluginsFile = libOrPluginsFile.getParentFile();
        }

        if (libOrPluginsFile != null && "lib".equals(libOrPluginsFile.getName()) && libOrPluginsFile.getParentFile() != null) {
            File srcDir = new File(libOrPluginsFile.getParentFile(), "src");

            GradleVersion current = GradleVersion.version(buildScriptClasspathModel.getGradleVersion());
            if (current.compareTo(GradleVersion.version("1.9")) >= 0) {
                int endIndex = libName.indexOf(current.getVersion());
                if (endIndex != -1) {
                    String srcDirChild = libName.substring("gradle-".length(), endIndex - 1);
                    srcDir = new File(srcDir, srcDirChild);
                }
            }

            if (srcDir.isDirectory()) {
                library.addPath(LibraryPathType.SOURCE, srcDir.getAbsolutePath());
            }
        }
    }

    private static boolean isIdeaTask(final String taskName) {
        return taskName.toLowerCase(Locale.ENGLISH).contains("idea");
    }
}
