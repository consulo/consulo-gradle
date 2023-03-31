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

import consulo.externalSystem.model.task.ExternalSystemTaskId;
import consulo.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.idea.IdeaModule;
import org.jetbrains.plugins.gradle.tooling.model.ProjectImportAction;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;

/**
 * @author Vladislav.Soroka
 * @since 10/15/13
 */
public class ProjectResolverContext
{
	@Nonnull
	private final ExternalSystemTaskId myExternalSystemTaskId;
	@Nonnull
	private final String myProjectPath;
	@Nullable
	private final GradleExecutionSettings mySettings;
	@Nonnull
	private final ProjectConnection myConnection;
	@Nonnull
	private final ExternalSystemTaskNotificationListener myListener;
	private final boolean myIsPreviewMode;
	@Nonnull
	private ProjectImportAction.AllModels myModels;

	public ProjectResolverContext(@Nonnull final ExternalSystemTaskId externalSystemTaskId, @Nonnull final String projectPath,
			@Nullable final GradleExecutionSettings settings, @Nonnull final ProjectConnection connection,
			@Nonnull final ExternalSystemTaskNotificationListener listener, final boolean isPreviewMode)
	{
		myExternalSystemTaskId = externalSystemTaskId;
		myProjectPath = projectPath;
		mySettings = settings;
		myConnection = connection;
		myListener = listener;
		myIsPreviewMode = isPreviewMode;
	}

	@Nonnull
	public ExternalSystemTaskId getExternalSystemTaskId()
	{
		return myExternalSystemTaskId;
	}

	@Nonnull
	public String getProjectPath()
	{
		return myProjectPath;
	}

	@Nullable
	public GradleExecutionSettings getSettings()
	{
		return mySettings;
	}

	@Nonnull
	public ProjectConnection getConnection()
	{
		return myConnection;
	}

	@Nonnull
	public ExternalSystemTaskNotificationListener getListener()
	{
		return myListener;
	}

	public boolean isPreviewMode()
	{
		return myIsPreviewMode;
	}

	@Nonnull
	public ProjectImportAction.AllModels getModels()
	{
		return myModels;
	}

	public void setModels(@Nonnull ProjectImportAction.AllModels models)
	{
		myModels = models;
	}

	@Nullable
	public <T> T getExtraProject(Class<T> modelClazz)
	{
		return myModels.getExtraProject(null, modelClazz);
	}

	@Nullable
	public <T> T getExtraProject(@Nullable IdeaModule module, Class<T> modelClazz)
	{
		return myModels.getExtraProject(module, modelClazz);
	}

	@Nonnull
	public Collection<String> findModulesWithModel(@Nonnull Class modelClazz)
	{
		return myModels.findModulesWithModel(modelClazz);
	}
}
