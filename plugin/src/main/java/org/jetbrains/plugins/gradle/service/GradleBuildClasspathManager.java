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
package org.jetbrains.plugins.gradle.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.jetbrains.plugins.gradle.util.GradleConstants;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.model.project.ExternalModuleBuildClasspathPojo;
import com.intellij.openapi.externalSystem.model.project.ExternalProjectBuildClasspathPojo;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemLocalSettings;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import consulo.vfs.util.ArchiveVfsUtil;

/**
 * @author Vladislav.Soroka
 * @since 12/27/13
 */
@Singleton
public class GradleBuildClasspathManager
{
	@Nonnull
	private final Project myProject;

	@Nonnull
	private volatile List<VirtualFile> allFilesCache;

	@Nonnull
	private final AtomicReference<Map<String/*module path*/, List<VirtualFile> /*module build classpath*/>> myClasspathMap = new AtomicReference<>(new HashMap<>());

	@Inject
	public GradleBuildClasspathManager(@Nonnull Project project)
	{
		myProject = project;
		reload();
	}

	@Nonnull
	public static GradleBuildClasspathManager getInstance(@Nonnull Project project)
	{
		return ServiceManager.getService(project, GradleBuildClasspathManager.class);
	}

	public void reload()
	{
		ExternalSystemManager<?, ?, ?, ?, ?> manager = ExternalSystemApiUtil.getManager(GradleConstants.SYSTEM_ID);
		assert manager != null;
		AbstractExternalSystemLocalSettings localSettings = manager.getLocalSettingsProvider().fun(myProject);

		Map<String/*module path*/, List<VirtualFile> /*module build classpath*/> map = ContainerUtil.newHashMap();

		final LocalFileSystem localFileSystem = LocalFileSystem.getInstance();
		for(ExternalProjectBuildClasspathPojo projectBuildClasspathPojo : localSettings.getProjectBuildClasspath().values())
		{
			List<VirtualFile> projectBuildClasspath = ContainerUtil.newArrayList();
			for(String path : projectBuildClasspathPojo.getProjectBuildClasspath())
			{
				final VirtualFile virtualFile = localFileSystem.refreshAndFindFileByPath(path);
				if(virtualFile != null)
				{
					ContainerUtil.addIfNotNull(projectBuildClasspath, virtualFile.isDirectory() ? virtualFile : ArchiveVfsUtil.getArchiveRootForLocalFile(virtualFile));
				}
			}

			for(ExternalModuleBuildClasspathPojo moduleBuildClasspathPojo : projectBuildClasspathPojo.getModulesBuildClasspath().values())
			{
				List<VirtualFile> moduleBuildClasspath = ContainerUtil.newArrayList(projectBuildClasspath);
				for(String path : moduleBuildClasspathPojo.getEntries())
				{
					final VirtualFile virtualFile = localFileSystem.refreshAndFindFileByPath(path);
					if(virtualFile != null)
					{
						ContainerUtil.addIfNotNull(moduleBuildClasspath, ArchiveVfsUtil.getArchiveRootForLocalFile(virtualFile));
					}
				}

				map.put(moduleBuildClasspathPojo.getPath(), moduleBuildClasspath);
			}
		}

		myClasspathMap.set(map);

		Set<VirtualFile> set = new LinkedHashSet<>();
		for(List<VirtualFile> virtualFiles : myClasspathMap.get().values())
		{
			set.addAll(virtualFiles);
		}
		allFilesCache = ContainerUtil.newArrayList(set);
	}

	@Nonnull
	public List<VirtualFile> getAllClasspathEntries()
	{
		return allFilesCache;
	}

	@Nonnull
	public List<VirtualFile> getModuleClasspathEntries(@Nonnull String externalModulePath)
	{
		List<VirtualFile> virtualFiles = myClasspathMap.get().get(externalModulePath);
		return virtualFiles == null ? Collections.<VirtualFile>emptyList() : virtualFiles;
	}
}
