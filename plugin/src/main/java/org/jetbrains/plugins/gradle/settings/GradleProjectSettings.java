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
package org.jetbrains.plugins.gradle.settings;

import consulo.externalSystem.setting.ExternalProjectSettings;
import consulo.gradle.setting.DistributionType;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Denis Zhdanov
 * @since 4/24/13 11:57 AM
 */
public class GradleProjectSettings extends ExternalProjectSettings
{
	@Nullable
	private String myGradleHome;
	@Nullable
	private DistributionType distributionType;
	private boolean disableWrapperSourceDistributionNotification;
	@Nullable
	private String myJreName;

	@Nullable
	public String getGradleHome()
	{
		return myGradleHome;
	}

	public void setGradleHome(@Nullable String gradleHome)
	{
		myGradleHome = gradleHome;
	}

	@Nullable
	public DistributionType getDistributionType()
	{
		return distributionType;
	}

	public void setDistributionType(@Nullable DistributionType distributionType)
	{
		this.distributionType = distributionType;
	}

	public boolean isDisableWrapperSourceDistributionNotification()
	{
		return disableWrapperSourceDistributionNotification;
	}

	public void setDisableWrapperSourceDistributionNotification(boolean disableWrapperSourceDistributionNotification)
	{
		this.disableWrapperSourceDistributionNotification = disableWrapperSourceDistributionNotification;
	}

	@Nullable
	public String getJreName()
	{
		return myJreName;
	}

	public void setJreName(@Nullable String jreName)
	{
		myJreName = jreName;
	}

	@Nonnull
	@Override
	public ExternalProjectSettings clone()
	{
		GradleProjectSettings result = new GradleProjectSettings();
		copyTo(result);
		result.myGradleHome = myGradleHome;
		result.distributionType = distributionType;
		result.myJreName = myJreName;
		result.disableWrapperSourceDistributionNotification = disableWrapperSourceDistributionNotification;
		return result;
	}
}
