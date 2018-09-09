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
package org.jetbrains.plugins.gradle.service.project;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;

import org.gradle.tooling.BuildActionExecuter;
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.UnsupportedVersionException;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.idea.BasicIdeaProject;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaProject;

import javax.annotation.Nullable;
import org.jetbrains.plugins.gradle.model.ProjectImportAction;
import org.jetbrains.plugins.gradle.remote.impl.GradleLibraryNamesMixer;
import org.jetbrains.plugins.gradle.settings.ClassHolder;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleEnvironment;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.externalSystem.JavaProjectData;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.LibraryData;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.model.task.TaskData;
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemDebugEnvironment;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.KeyValue;
import com.intellij.openapi.util.Pair;
import com.intellij.util.BooleanFunction;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;

/**
 * @author Denis Zhdanov, Vladislav Soroka
 * @since 8/8/11 11:09 AM
 */
public class GradleProjectResolver implements ExternalSystemProjectResolver<GradleExecutionSettings>
{

	private static final Logger LOG = Logger.getInstance("#" + GradleProjectResolver.class.getName());

	@Nonnull
	private final GradleExecutionHelper myHelper;
	private final GradleLibraryNamesMixer myLibraryNamesMixer = new GradleLibraryNamesMixer();

	// This constructor is called by external system API, see AbstractExternalSystemFacadeImpl class constructor.
	@SuppressWarnings("UnusedDeclaration")
	public GradleProjectResolver()
	{
		this(new GradleExecutionHelper());
	}

	public GradleProjectResolver(@Nonnull GradleExecutionHelper helper)
	{
		myHelper = helper;
	}

	@Nullable
	@Override
	public DataNode<ProjectData> resolveProjectInfo(@Nonnull final ExternalSystemTaskId id, @Nonnull final String projectPath,
			final boolean isPreviewMode, @Nullable final GradleExecutionSettings settings, @Nonnull final ExternalSystemTaskNotificationListener
			listener) throws ExternalSystemException, IllegalArgumentException, IllegalStateException
	{
		if(settings != null)
		{
			myHelper.ensureInstalledWrapper(id, projectPath, settings, listener);
		}

		final GradleProjectResolverExtension projectResolverChain = createProjectResolverChain(settings);
		final DataNode<ProjectData> resultProjectDataNode = myHelper.execute(projectPath, settings, new ProjectConnectionDataNodeFunction(id,
				projectPath, settings, listener, isPreviewMode, projectResolverChain, false));

		// auto-discover buildSrc project if needed
		final String buildSrcProjectPath = projectPath + "/buildSrc";
		handleBuildSrcProject(resultProjectDataNode, new ProjectConnectionDataNodeFunction(id, buildSrcProjectPath, settings, listener,
				isPreviewMode, projectResolverChain, true));
		return resultProjectDataNode;
	}

	@Override
	public boolean cancelTask(@Nonnull ExternalSystemTaskId id, @Nonnull ExternalSystemTaskNotificationListener listener)
	{
		// TODO implement cancellation using gradle API invocation when it will be ready, see http://issues.gradle.org/browse/GRADLE-1539
		return false;
	}

	@Nonnull
	private DataNode<ProjectData> doResolveProjectInfo(@Nonnull final ProjectResolverContext resolverCtx,
			@Nonnull final GradleProjectResolverExtension projectResolverChain, boolean isBuildSrcProject) throws IllegalArgumentException,
			IllegalStateException
	{

		final ProjectImportAction projectImportAction = new ProjectImportAction(resolverCtx.isPreviewMode());

		final List<KeyValue<String, String>> extraJvmArgs = new ArrayList<KeyValue<String, String>>();
		final List<String> commandLineArgs = ContainerUtil.newArrayList();
		final Set<Class> toolingExtensionClasses = ContainerUtil.newHashSet();

		for(GradleProjectResolverExtension resolverExtension = projectResolverChain; resolverExtension != null; resolverExtension =
				resolverExtension.getNext())
		{
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
			toolingExtensionClasses.addAll(resolverExtension.getToolingExtensionsClasses());
		}

		final ParametersList parametersList = new ParametersList();
		for(KeyValue<String, String> jvmArg : extraJvmArgs)
		{
			parametersList.addProperty(jvmArg.getKey(), jvmArg.getValue());
		}


		BuildActionExecuter<ProjectImportAction.AllModels> buildActionExecutor = resolverCtx.getConnection().action(projectImportAction);

		// TODO [vlad] remove the check
		if(!GradleEnvironment.DISABLE_ENHANCED_TOOLING_API)
		{
			File initScript = GradleExecutionHelper.generateInitScript(isBuildSrcProject, toolingExtensionClasses);
			if(initScript != null)
			{
				ContainerUtil.addAll(commandLineArgs, GradleConstants.INIT_SCRIPT_CMD_OPTION, initScript.getAbsolutePath());
			}
		}

		GradleExecutionHelper.prepare(buildActionExecutor, resolverCtx.getExternalSystemTaskId(), resolverCtx.getSettings(),
				resolverCtx.getListener(), parametersList.getParameters(), commandLineArgs, resolverCtx.getConnection());

		ProjectImportAction.AllModels allModels;
		try
		{
			allModels = buildActionExecutor.run();
			if(allModels == null)
			{
				throw new IllegalStateException("Unable to get project model for the project: " + resolverCtx.getProjectPath());
			}
		}
		catch(UnsupportedVersionException unsupportedVersionException)
		{
			// Old gradle distribution version used (before ver. 1.8)
			// fallback to use ModelBuilder gradle tooling API
			Class<? extends IdeaProject> aClass = resolverCtx.isPreviewMode() ? BasicIdeaProject.class : IdeaProject.class;
			ModelBuilder<? extends IdeaProject> modelBuilder = myHelper.getModelBuilder(aClass, resolverCtx.getExternalSystemTaskId(),
					resolverCtx.getSettings(), resolverCtx.getConnection(), resolverCtx.getListener(), parametersList.getParameters());

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

		IdeaProject ideaProject = resolverCtx.getModels().getIdeaProject();

		projectResolverChain.populateProjectExtraModels(ideaProject, projectDataNode);

		DomainObjectSet<? extends IdeaModule> gradleModules = ideaProject.getModules();
		if(gradleModules == null || gradleModules.isEmpty())
		{
			throw new IllegalStateException("No modules found for the target project: " + ideaProject);
		}
		final Map<String, Pair<DataNode<ModuleData>, IdeaModule>> moduleMap = ContainerUtilRt.newHashMap();

		// import modules data
		for(IdeaModule gradleModule : gradleModules)
		{
			if(gradleModule == null)
			{
				continue;
			}

			if(ExternalSystemDebugEnvironment.DEBUG_ORPHAN_MODULES_PROCESSING)
			{
				LOG.info(String.format("Importing module data: %s", gradleModule));
			}

			final String moduleName = gradleModule.getName();
			if(moduleName == null)
			{
				throw new IllegalStateException("Module with undefined name detected: " + gradleModule);
			}

			ModuleData moduleData = projectResolverChain.createModule(gradleModule, projectData);

			Pair<DataNode<ModuleData>, IdeaModule> previouslyParsedModule = moduleMap.get(moduleName);
			if(previouslyParsedModule != null)
			{
				throw new IllegalStateException(String.format("Modules with duplicate name (%s) detected: '%s' and '%s'", moduleName, moduleData,
						previouslyParsedModule));
			}
			DataNode<ModuleData> moduleDataNode = projectDataNode.createChild(ProjectKeys.MODULE, moduleData);
			moduleMap.put(moduleName, Pair.create(moduleDataNode, gradleModule));
		}

		// populate modules nodes
		final List<TaskData> allTasks = ContainerUtil.newArrayList();
		for(final Pair<DataNode<ModuleData>, IdeaModule> pair : moduleMap.values())
		{
			final DataNode<ModuleData> moduleDataNode = pair.first;
			final IdeaModule ideaModule = pair.second;
			projectResolverChain.populateModuleExtraModels(ideaModule, moduleDataNode);
			projectResolverChain.populateModuleContentRoots(ideaModule, moduleDataNode);
			projectResolverChain.populateModuleCompileOutputSettings(ideaModule, moduleDataNode);
			projectResolverChain.populateModuleDependencies(ideaModule, moduleDataNode, projectDataNode);
			if(!isBuildSrcProject)
			{
				final Collection<TaskData> moduleTasks = projectResolverChain.populateModuleTasks(ideaModule, moduleDataNode, projectDataNode);
				allTasks.addAll(moduleTasks);
			}
		}

		// populate root project tasks
		final Collection<TaskData> rootProjectTaskCandidates = projectResolverChain.filterRootProjectTasks(allTasks);

		Set<Couple<String>> rootProjectTaskCandidatesMap = ContainerUtilRt.newHashSet();
		for(final TaskData taskData : rootProjectTaskCandidates)
		{
			rootProjectTaskCandidatesMap.add(Couple.of(taskData.getName(), taskData.getDescription()));
		}
		for(final Couple<String> p : rootProjectTaskCandidatesMap)
		{
			projectDataNode.createChild(ProjectKeys.TASK, new TaskData(GradleConstants.SYSTEM_ID, p.first, projectData.getLinkedExternalProjectPath
					(), p.second));
		}

		// ensure unique library names
		Collection<DataNode<LibraryData>> libraries = ExternalSystemApiUtil.getChildren(projectDataNode, ProjectKeys.LIBRARY);
		myLibraryNamesMixer.mixNames(libraries);

		return projectDataNode;
	}

	@Nullable
	private static BuildEnvironment getBuildEnvironment(@Nonnull ProjectResolverContext resolverCtx)
	{
		try
		{
			return resolverCtx.getConnection().getModel(BuildEnvironment.class);
		}
		catch(Exception e)
		{
			return null;
		}
	}

	private void handleBuildSrcProject(@Nonnull final DataNode<ProjectData> resultProjectDataNode, @Nonnull final ProjectConnectionDataNodeFunction
			projectConnectionDataNodeFunction)
	{

		if(projectConnectionDataNodeFunction.myIsPreviewMode || GradleEnvironment.DISABLE_ENHANCED_TOOLING_API || !new File
				(projectConnectionDataNodeFunction.myProjectPath).isDirectory())
		{
			return;
		}

		final DataNode<ModuleData> buildSrcModuleDataNode = ExternalSystemApiUtil.find(resultProjectDataNode, ProjectKeys.MODULE,
				new BooleanFunction<DataNode<ModuleData>>()
		{
			@Override
			public boolean fun(DataNode<ModuleData> node)
			{
				return projectConnectionDataNodeFunction.myProjectPath.equals(node.getData().getLinkedExternalProjectPath());
			}
		});

		// check if buildSrc project was already exposed in settings.gradle file
		if(buildSrcModuleDataNode != null)
		{
			return;
		}

		final DataNode<ProjectData> buildSrcProjectDataDataNode = myHelper.execute(projectConnectionDataNodeFunction.myProjectPath,
				projectConnectionDataNodeFunction.mySettings, projectConnectionDataNodeFunction);

		if(buildSrcProjectDataDataNode != null)
		{
			final DataNode<ModuleData> moduleDataNode = ExternalSystemApiUtil.find(buildSrcProjectDataDataNode, ProjectKeys.MODULE);
			if(moduleDataNode != null)
			{
				for(DataNode<LibraryData> libraryDataNode : ExternalSystemApiUtil.findAll(buildSrcProjectDataDataNode, ProjectKeys.LIBRARY))
				{
					resultProjectDataNode.createChild(libraryDataNode.getKey(), libraryDataNode.getData());
				}

				final DataNode<ModuleData> newModuleDataNode = resultProjectDataNode.createChild(ProjectKeys.MODULE, moduleDataNode.getData());
				for(DataNode node : moduleDataNode.getChildren())
				{
					newModuleDataNode.createChild(node.getKey(), node.getData());
				}
			}
		}
	}

	private class ProjectConnectionDataNodeFunction implements Function<ProjectConnection, DataNode<ProjectData>>
	{
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

		public ProjectConnectionDataNodeFunction(@Nonnull ExternalSystemTaskId id, @Nonnull String projectPath,
				@javax.annotation.Nullable GradleExecutionSettings settings, @Nonnull ExternalSystemTaskNotificationListener listener, boolean isPreviewMode,
				@Nonnull GradleProjectResolverExtension projectResolverChain, boolean isBuildSrcProject)
		{
			myId = id;
			myProjectPath = projectPath;
			mySettings = settings;
			myListener = listener;
			myIsPreviewMode = isPreviewMode;
			myProjectResolverChain = projectResolverChain;
			myIsBuildSrcProject = isBuildSrcProject;
		}

		@Override
		public DataNode<ProjectData> fun(ProjectConnection connection)
		{
			try
			{
				return doResolveProjectInfo(new ProjectResolverContext(myId, myProjectPath, mySettings, connection, myListener, myIsPreviewMode),
						myProjectResolverChain, myIsBuildSrcProject);
			}
			catch(RuntimeException e)
			{
				LOG.info("Gradle project resolve error", e);
				throw myProjectResolverChain.getUserFriendlyError(e, myProjectPath, null);
			}
		}
	}


	@Nonnull
	public static GradleProjectResolverExtension createProjectResolverChain(@Nullable final GradleExecutionSettings settings)
	{
		GradleProjectResolverExtension projectResolverChain;
		if(settings != null)
		{
			List<ClassHolder<? extends GradleProjectResolverExtension>> extensionClasses = settings.getResolverExtensions();

			Deque<GradleProjectResolverExtension> extensions = new ArrayDeque<GradleProjectResolverExtension>();
			for(ClassHolder<? extends GradleProjectResolverExtension> holder : extensionClasses)
			{
				final GradleProjectResolverExtension extension;
				try
				{
					extension = holder.getTargetClass().newInstance();
				}
				catch(Throwable e)
				{
					throw new IllegalArgumentException(String.format("Can't instantiate project resolve extension for class '%s'",
							holder.getTargetClassName()), e);
				}
				final GradleProjectResolverExtension previous = extensions.peekLast();
				if(previous != null)
				{
					previous.setNext(extension);
					if(previous.getNext() != extension)
					{
						throw new AssertionError("Illegal next resolver got, current resolver class is " + previous.getClass().getName());
					}
				}
				extensions.add(extension);
			}
			projectResolverChain = extensions.peekFirst();

			GradleProjectResolverExtension resolverExtension = projectResolverChain;
			assert resolverExtension != null;
			while(resolverExtension.getNext() != null)
			{
				resolverExtension = resolverExtension.getNext();
			}
			if(!(resolverExtension instanceof BaseGradleProjectResolverExtension))
			{
				throw new AssertionError("Illegal last resolver got of class " + resolverExtension.getClass().getName());
			}
		}
		else
		{
			projectResolverChain = new BaseGradleProjectResolverExtension();
		}

		return projectResolverChain;
	}
}