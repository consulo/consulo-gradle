package org.jetbrains.plugins.gradle.service.project;

import com.intellij.java.impl.externalSystem.JavaProjectData;
import consulo.externalSystem.model.DataNode;
import consulo.externalSystem.model.ProjectKeys;
import consulo.externalSystem.model.project.LibraryData;
import consulo.externalSystem.model.project.ModuleData;
import consulo.externalSystem.model.task.ExternalSystemTaskId;
import consulo.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import consulo.externalSystem.model.task.TaskData;
import consulo.externalSystem.rt.model.ExternalSystemException;
import consulo.externalSystem.service.project.ExternalSystemProjectResolver;
import consulo.externalSystem.service.project.ProjectData;
import consulo.externalSystem.util.ExternalSystemApiUtil;
import consulo.externalSystem.util.ExternalSystemDebugEnvironment;
import consulo.gradle.GradleConstants;
import consulo.gradle.service.project.GradleProjectResolverExtension;
import consulo.gradle.service.project.ProjectResolverContext;
import consulo.gradle.setting.ClassHolder;
import consulo.gradle.setting.GradleExecutionSettings;
import consulo.logging.Logger;
import consulo.process.cmd.ParametersList;
import consulo.util.lang.Couple;
import consulo.util.lang.Pair;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.gradle.tooling.BuildActionExecuter;
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.UnsupportedVersionException;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.idea.BasicIdeaProject;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaProject;
import org.jetbrains.plugins.gradle.remote.impl.GradleLibraryNamesMixer;
import org.jetbrains.plugins.gradle.tooling.model.ProjectImportAction;
import org.jetbrains.plugins.gradle.util.GradleEnvironment;

import java.io.File;
import java.util.*;
import java.util.function.Function;

/**
 * @author VISTALL
 * @since 2025-04-06
 */
public class GradleProjectResolver implements ExternalSystemProjectResolver<GradleExecutionSettings> {
    private static final Logger LOG = Logger.getInstance(GradleProjectResolver.class);

    @Nonnull
    private final GradleExecutionHelper myHelper;
    private final GradleLibraryNamesMixer myLibraryNamesMixer = new GradleLibraryNamesMixer();

    public GradleProjectResolver() {
        this(new GradleExecutionHelper());
    }

    public GradleProjectResolver(@Nonnull GradleExecutionHelper helper) {
        myHelper = helper;
    }

    @Nullable
    @Override
    public DataNode<ProjectData> resolveProjectInfo(
        @Nonnull final ExternalSystemTaskId id,
        @Nonnull final String projectPath,
        final boolean isPreviewMode,
        @Nullable final GradleExecutionSettings settings,
        @Nonnull final ExternalSystemTaskNotificationListener listener
    ) throws ExternalSystemException, IllegalArgumentException, IllegalStateException {
        if (settings != null) {
            myHelper.ensureInstalledWrapper(id, projectPath, settings, listener);
        }

        final GradleProjectResolverExtension projectResolverChain = createProjectResolverChain(settings);
        final DataNode<ProjectData> resultProjectDataNode =
            myHelper.execute(
                projectPath,
                settings,
                new ProjectConnectionDataNodeFunction(
                    id,
                    projectPath,
                    settings,
                    listener,
                    isPreviewMode,
                    projectResolverChain,
                    false
                )
            );

        // auto-discover buildSrc project if needed
        final String buildSrcProjectPath = projectPath + "/buildSrc";
        handleBuildSrcProject(
            resultProjectDataNode,
            new ProjectConnectionDataNodeFunction(
                id,
                buildSrcProjectPath,
                settings,
                listener,
                isPreviewMode,
                projectResolverChain,
                true
            )
        );
        return resultProjectDataNode;
    }

    @Override
    public boolean cancelTask(@Nonnull ExternalSystemTaskId id, @Nonnull ExternalSystemTaskNotificationListener listener) {
        // TODO implement cancellation using gradle API invocation when it will be ready, see http://issues.gradle.org/browse/GRADLE-1539
        return false;
    }

    @Nonnull
    private DataNode<ProjectData> doResolveProjectInfo(
        @Nonnull final ProjectResolverContext resolverCtx,
        @Nonnull final GradleProjectResolverExtension projectResolverChain,
        boolean isBuildSrcProject
    ) throws IllegalArgumentException, IllegalStateException {
        final ProjectImportAction projectImportAction = new ProjectImportAction(resolverCtx.isPreviewMode());

        final List<Pair<String, String>> extraJvmArgs = new ArrayList<>();
        final List<String> commandLineArgs = new ArrayList<>();

        final Set<File> toolingExtensionFiles = new HashSet<>();

        for (GradleProjectResolverExtension resolverExtension = projectResolverChain; resolverExtension != null; resolverExtension =
            resolverExtension.getNext()) {
            // inject ProjectResolverContext into gradle project resolver extensions
            resolverExtension.setProjectResolverContext(resolverCtx);
            // pre-import checks
            resolverExtension.preImportCheck();
            // register classes of extra gradle project models required for extensions (e.g. com.android.builder.model.AndroidProject)
            projectImportAction.addExtraProjectModelClasses(resolverExtension.getExtraProjectModelClasses());
            // collect extra JVM arguments provided by gradle project resolver extensions
            extraJvmArgs.addAll(resolverExtension.getExtraJvmArgs());
            // collect extra command-line arguments
            commandLineArgs.addAll(resolverExtension.getExtraCommandLineArgs());
            // collect tooling extensions classes
            toolingExtensionFiles.addAll(resolverExtension.getToolingExtensionsFiles());
        }

        final ParametersList parametersList = new ParametersList();
        for (Pair<String, String> jvmArg : extraJvmArgs) {
            parametersList.addProperty(jvmArg.getFirst(), jvmArg.getSecond());
        }

        BuildActionExecuter<ProjectImportAction.AllModels> buildActionExecutor = resolverCtx.getConnection().action(projectImportAction);

        File initScript = GradleExecutionHelper.generateInitScript(isBuildSrcProject, toolingExtensionFiles);
        if (initScript != null) {
            consulo.util.collection.ContainerUtil.addAll(commandLineArgs, GradleConstants.INIT_SCRIPT_CMD_OPTION, initScript.getAbsolutePath());
        }

        GradleExecutionHelper.prepare(buildActionExecutor, resolverCtx.getExternalSystemTaskId(), resolverCtx.getSettings(),
            resolverCtx.getListener(), parametersList.getParameters(), commandLineArgs, resolverCtx.getConnection()
        );

        ProjectImportAction.AllModels allModels;
        try {
            allModels = buildActionExecutor.run();
            if (allModels == null) {
                throw new IllegalStateException("Unable to get project model for the project: " + resolverCtx.getProjectPath());
            }
        }
        catch (UnsupportedVersionException unsupportedVersionException) {
            // Old gradle distribution version used (before ver. 1.8)
            // fallback to use ModelBuilder gradle tooling API
            Class<? extends IdeaProject> aClass = resolverCtx.isPreviewMode() ? BasicIdeaProject.class : IdeaProject.class;
            ModelBuilder<? extends IdeaProject> modelBuilder = myHelper.getModelBuilder(
                aClass,
                resolverCtx.getExternalSystemTaskId(),
                resolverCtx.getSettings(),
                resolverCtx.getConnection(),
                resolverCtx.getListener(),
                parametersList.getParameters()
            );

            final IdeaProject ideaProject = modelBuilder.get();
            allModels = new ProjectImportAction.AllModels(ideaProject);
        }

        final BuildEnvironment buildEnvironment = getBuildEnvironment(resolverCtx);
        allModels.setBuildEnvironment(buildEnvironment);
        resolverCtx.setModels(allModels);

        // import project data
        ProjectData projectData = projectResolverChain.createProject();
        DataNode<ProjectData> projectDataNode = new DataNode<ProjectData>(ProjectKeys.PROJECT, projectData, null);

        // import java project data
        JavaProjectData javaProjectData = projectResolverChain.createJavaProjectData();
        projectDataNode.createChild(JavaProjectData.KEY, javaProjectData);

        IdeaProject ideaProject = ((ProjectImportAction.AllModels) resolverCtx.getModels()).getIdeaProject();

        projectResolverChain.populateProjectExtraModels(ideaProject, projectDataNode);

        DomainObjectSet<? extends IdeaModule> gradleModules = ideaProject.getModules();
        if (gradleModules == null || gradleModules.isEmpty()) {
            throw new IllegalStateException("No modules found for the target project: " + ideaProject);
        }
        final Map<String, Pair<DataNode<ModuleData>, IdeaModule>> moduleMap = new HashMap<>();

        // import modules data
        for (IdeaModule gradleModule : gradleModules) {
            if (gradleModule == null) {
                continue;
            }

            if (ExternalSystemDebugEnvironment.DEBUG_ORPHAN_MODULES_PROCESSING) {
                LOG.info(String.format("Importing module data: %s", gradleModule));
            }

            final String moduleName = gradleModule.getName();
            if (moduleName == null) {
                throw new IllegalStateException("Module with undefined name detected: " + gradleModule);
            }

            ModuleData moduleData = projectResolverChain.createModule(gradleModule, projectData);

            Pair<DataNode<ModuleData>, IdeaModule> previouslyParsedModule = moduleMap.get(moduleName);
            if (previouslyParsedModule != null) {
                throw new IllegalStateException(String.format(
                    "Modules with duplicate name (%s) detected: '%s' and '%s'",
                    moduleName,
                    moduleData,
                    previouslyParsedModule
                ));
            }
            DataNode<ModuleData> moduleDataNode = projectDataNode.createChild(ProjectKeys.MODULE, moduleData);
            moduleMap.put(moduleName, Pair.create(moduleDataNode, gradleModule));
        }

        // populate modules nodes
        final List<TaskData> allTasks = new ArrayList<>();
        for (final Pair<DataNode<ModuleData>, IdeaModule> pair : moduleMap.values()) {
            final DataNode<ModuleData> moduleDataNode = pair.first;
            final IdeaModule ideaModule = pair.second;
            projectResolverChain.populateModuleExtraModels(ideaModule, moduleDataNode);
            projectResolverChain.populateModuleContentRoots(ideaModule, moduleDataNode);
            projectResolverChain.populateModuleCompileOutputSettings(ideaModule, moduleDataNode);
            projectResolverChain.populateModuleDependencies(ideaModule, moduleDataNode, projectDataNode);
            if (!isBuildSrcProject) {
                final Collection<TaskData> moduleTasks =
                    projectResolverChain.populateModuleTasks(ideaModule, moduleDataNode, projectDataNode);
                allTasks.addAll(moduleTasks);
            }
        }

        // populate root project tasks
        final Collection<TaskData> rootProjectTaskCandidates = projectResolverChain.filterRootProjectTasks(allTasks);

        Set<Couple<String>> rootProjectTaskCandidatesMap = new HashSet<>();
        for (final TaskData taskData : rootProjectTaskCandidates) {
            rootProjectTaskCandidatesMap.add(Couple.of(taskData.getName(), taskData.getDescription()));
        }
        for (final Couple<String> p : rootProjectTaskCandidatesMap) {
            projectDataNode.createChild(
                ProjectKeys.TASK,
                new TaskData(GradleConstants.SYSTEM_ID, p.first, projectData.getLinkedExternalProjectPath(), p.second)
            );
        }

        // ensure unique library names
        Collection<DataNode<LibraryData>> libraries = ExternalSystemApiUtil.getChildren(projectDataNode, ProjectKeys.LIBRARY);
        myLibraryNamesMixer.mixNames(libraries);

        return projectDataNode;
    }

    @Nullable
    private static BuildEnvironment getBuildEnvironment(@Nonnull ProjectResolverContext resolverCtx) {
        try {
            return resolverCtx.getConnection().getModel(BuildEnvironment.class);
        }
        catch (Exception e) {
            return null;
        }
    }

    private void handleBuildSrcProject(
        @Nonnull final DataNode<ProjectData> resultProjectDataNode,
        @Nonnull final ProjectConnectionDataNodeFunction projectConnectionDataNodeFunction
    ) {

        if (projectConnectionDataNodeFunction.myIsPreviewMode || GradleEnvironment.DISABLE_ENHANCED_TOOLING_API || !new File
            (projectConnectionDataNodeFunction.myProjectPath).isDirectory()) {
            return;
        }

        final DataNode<ModuleData> buildSrcModuleDataNode = ExternalSystemApiUtil.find(
            resultProjectDataNode,
            ProjectKeys.MODULE,
            node -> projectConnectionDataNodeFunction.myProjectPath.equals(node.getData().getLinkedExternalProjectPath())
        );

        // check if buildSrc project was already exposed in settings.gradle file
        if (buildSrcModuleDataNode != null) {
            return;
        }

        final DataNode<ProjectData> buildSrcProjectDataDataNode = myHelper.execute(projectConnectionDataNodeFunction.myProjectPath,
            projectConnectionDataNodeFunction.mySettings, projectConnectionDataNodeFunction
        );

        if (buildSrcProjectDataDataNode != null) {
            final DataNode<ModuleData> moduleDataNode = ExternalSystemApiUtil.find(buildSrcProjectDataDataNode, ProjectKeys.MODULE);
            if (moduleDataNode != null) {
                Collection<DataNode<LibraryData>> libraryDataNodes =
                    ExternalSystemApiUtil.findAll(buildSrcProjectDataDataNode, ProjectKeys.LIBRARY);
                for (DataNode<LibraryData> libraryDataNode : libraryDataNodes) {
                    resultProjectDataNode.createChild(libraryDataNode.getKey(), libraryDataNode.getData());
                }

                final DataNode<ModuleData> newModuleDataNode =
                    resultProjectDataNode.createChild(ProjectKeys.MODULE, moduleDataNode.getData());
                for (DataNode node : moduleDataNode.getChildren()) {
                    newModuleDataNode.createChild(node.getKey(), node.getData());
                }
            }
        }
    }

    private class ProjectConnectionDataNodeFunction implements Function<ProjectConnection, DataNode<ProjectData>> {
        @Nonnull
        private final ExternalSystemTaskId myId;
        @Nonnull
        private final String myProjectPath;
        @Nullable
        private final GradleExecutionSettings mySettings;
        @Nonnull
        private final ExternalSystemTaskNotificationListener myListener;
        private final boolean myIsPreviewMode;
        @Nonnull
        private final GradleProjectResolverExtension myProjectResolverChain;
        private final boolean myIsBuildSrcProject;

        public ProjectConnectionDataNodeFunction(
            @Nonnull ExternalSystemTaskId id,
            @Nonnull String projectPath,
            @Nullable GradleExecutionSettings settings,
            @Nonnull ExternalSystemTaskNotificationListener listener,
            boolean isPreviewMode,
            @Nonnull GradleProjectResolverExtension projectResolverChain,
            boolean isBuildSrcProject
        ) {
            myId = id;
            myProjectPath = projectPath;
            mySettings = settings;
            myListener = listener;
            myIsPreviewMode = isPreviewMode;
            myProjectResolverChain = projectResolverChain;
            myIsBuildSrcProject = isBuildSrcProject;
        }

        @Override
        public DataNode<ProjectData> apply(ProjectConnection connection) {
            try {
                return doResolveProjectInfo(
                    new ProjectResolverContext(
                        myId,
                        myProjectPath,
                        mySettings,
                        connection,
                        myListener,
                        myIsPreviewMode
                    ),
                    myProjectResolverChain,
                    myIsBuildSrcProject
                );
            }
            catch (RuntimeException e) {
                LOG.info("Gradle project resolve error", e);
                throw myProjectResolverChain.getUserFriendlyError(e, myProjectPath, null);
            }
        }
    }

    @Nonnull
    public static GradleProjectResolverExtension createProjectResolverChain(@Nullable final GradleExecutionSettings settings) {
        GradleProjectResolverExtension projectResolverChain;
        if (settings != null) {
            List<ClassHolder<? extends GradleProjectResolverExtension>> extensionClasses = settings.getResolverExtensions();

            Deque<GradleProjectResolverExtension> extensions = new ArrayDeque<>();
            for (ClassHolder<? extends GradleProjectResolverExtension> holder : extensionClasses) {
                final GradleProjectResolverExtension extension;
                try {
                    extension = holder.getTargetClass().newInstance();
                }
                catch (Throwable e) {
                    throw new IllegalArgumentException(
                        String.format(
                            "Can't instantiate project resolve extension for class '%s'",
                            holder.getTargetClassName()
                        ),
                        e
                    );
                }
                final GradleProjectResolverExtension previous = extensions.peekLast();
                if (previous != null) {
                    previous.setNext(extension);
                    if (previous.getNext() != extension) {
                        throw new AssertionError(
                            "Illegal next resolver got, current resolver class is " + previous.getClass().getName()
                        );
                    }
                }
                extensions.add(extension);
            }
            projectResolverChain = extensions.peekFirst();

            GradleProjectResolverExtension resolverExtension = projectResolverChain;
            assert resolverExtension != null;
            while (resolverExtension.getNext() != null) {
                resolverExtension = resolverExtension.getNext();
            }
            if (!(resolverExtension instanceof BaseGradleProjectResolverExtension)) {
                throw new AssertionError("Illegal last resolver got of class " + resolverExtension.getClass().getName());
            }
        }
        else {
            projectResolverChain = new BaseGradleProjectResolverExtension();
        }

        return projectResolverChain;
    }
}
