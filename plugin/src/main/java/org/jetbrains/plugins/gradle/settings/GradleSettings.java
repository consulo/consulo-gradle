/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.settings;

import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.jetbrains.plugins.gradle.config.DelegatingGradleSettingsListenerAdapter;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings;
import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.xmlb.annotations.AbstractCollection;

/**
 * Holds shared project-level gradle-related settings (should be kept under '.consulo').
 *
 * @author peter
 */
@State(name = "GradleSettings", storages = @Storage(file = StoragePathMacros.PROJECT_CONFIG_DIR + "/gradle.xml"))
public class GradleSettings extends AbstractExternalSystemSettings<GradleSettings, GradleProjectSettings,
		GradleSettingsListener> implements PersistentStateComponent<GradleSettings.MyState>
{
	@Nullable
	private String myServiceDirectoryPath;
	@Nullable
	private String myGradleVmOptions;
	private boolean myIsOfflineWork;

	public GradleSettings(@Nonnull Project project)
	{
		super(GradleSettingsListener.TOPIC, project);
	}

	@Nonnull
	public static GradleSettings getInstance(@Nonnull Project project)
	{
		return ServiceManager.getService(project, GradleSettings.class);
	}

	@Override
	public void subscribe(@Nonnull ExternalSystemSettingsListener<GradleProjectSettings> listener)
	{
		getProject().getMessageBus().connect(getProject()).subscribe(GradleSettingsListener.TOPIC, new DelegatingGradleSettingsListenerAdapter
				(listener));
	}

	@Override
	protected void copyExtraSettingsFrom(@Nonnull GradleSettings settings)
	{
		myServiceDirectoryPath = settings.getServiceDirectoryPath();
		myGradleVmOptions = settings.getGradleVmOptions();
		myIsOfflineWork = settings.isOfflineWork();
	}

	@SuppressWarnings("unchecked")
	@Nullable
	@Override
	public GradleSettings.MyState getState()
	{
		MyState state = new MyState();
		fillState(state);
		state.serviceDirectoryPath = myServiceDirectoryPath;
		state.gradleVmOptions = myGradleVmOptions;
		state.offlineWork = myIsOfflineWork;
		return state;
	}

	@Override
	public void loadState(MyState state)
	{
		super.loadState(state);
		myServiceDirectoryPath = state.serviceDirectoryPath;
		myGradleVmOptions = state.gradleVmOptions;
		myIsOfflineWork = state.offlineWork;
	}

	/**
	 * @return service directory path (if defined). 'Service directory' is a directory which is used internally by gradle during
	 * calls to the tooling api. E.g. it holds downloaded binaries (dependency jars). We allow to define it because there
	 * is a possible situation when a user wants to configure particular directory to be excluded from anti-virus protection
	 * in order to increase performance
	 */
	@Nullable
	public String getServiceDirectoryPath()
	{
		return myServiceDirectoryPath;
	}

	public void setServiceDirectoryPath(@Nullable String newPath)
	{
		if(!Comparing.equal(myServiceDirectoryPath, newPath))
		{
			String oldPath = myServiceDirectoryPath;
			myServiceDirectoryPath = newPath;
			getPublisher().onServiceDirectoryPathChange(oldPath, newPath);
		}
	}

	@Nullable
	public String getGradleVmOptions()
	{
		return myGradleVmOptions;
	}

	public void setGradleVmOptions(@Nullable String gradleVmOptions)
	{
		if(!Comparing.equal(myGradleVmOptions, gradleVmOptions))
		{
			String old = myGradleVmOptions;
			myGradleVmOptions = gradleVmOptions;
			getPublisher().onGradleVmOptionsChange(old, gradleVmOptions);
		}
	}

	public boolean isOfflineWork()
	{
		return myIsOfflineWork;
	}

	public void setOfflineWork(boolean isOfflineWork)
	{
		myIsOfflineWork = isOfflineWork;
	}

	@Override
	protected void checkSettings(@Nonnull GradleProjectSettings old, @Nonnull GradleProjectSettings current)
	{
		if(!Comparing.equal(old.getGradleHome(), current.getGradleHome()))
		{
			getPublisher().onGradleHomeChange(old.getGradleHome(), current.getGradleHome(), current.getExternalProjectPath());
		}
		if(old.getDistributionType() != current.getDistributionType())
		{
			getPublisher().onGradleDistributionTypeChange(current.getDistributionType(), current.getExternalProjectPath());
		}
	}

	public static class MyState implements State<GradleProjectSettings>
	{

		private Set<GradleProjectSettings> myProjectSettings = ContainerUtilRt.newTreeSet();
		public String serviceDirectoryPath;
		public String gradleVmOptions;
		public boolean offlineWork;

		@AbstractCollection(surroundWithTag = false, elementTypes = {GradleProjectSettings.class})
		public Set<GradleProjectSettings> getLinkedExternalProjectsSettings()
		{
			return myProjectSettings;
		}

		public void setLinkedExternalProjectsSettings(Set<GradleProjectSettings> settings)
		{
			if(settings != null)
			{
				myProjectSettings.addAll(settings);
			}
		}
	}
}