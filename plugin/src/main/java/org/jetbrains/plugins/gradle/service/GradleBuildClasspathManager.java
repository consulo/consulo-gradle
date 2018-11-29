// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.jetbrains.plugins.gradle.config.GradleClassFinder;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.model.project.ExternalModuleBuildClasspathPojo;
import com.intellij.openapi.externalSystem.model.project.ExternalProjectBuildClasspathPojo;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemLocalSettings;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElementFinder;
import com.intellij.util.containers.ContainerUtil;
import consulo.vfs.util.ArchiveVfsUtil;

/**
 * @author Vladislav.Soroka
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
		allFilesCache = ContainerUtil.newArrayList();
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

		for(final ExternalProjectBuildClasspathPojo projectBuildClasspathPojo : localSettings.getProjectBuildClasspath().values())
		{
			final List<VirtualFile> projectBuildClasspath = ContainerUtil.newArrayList();
			for(String path : projectBuildClasspathPojo.getProjectBuildClasspath())
			{
				final VirtualFile virtualFile = ExternalSystemUtil.findLocalFileByPath(path);
				ContainerUtil.addIfNotNull(projectBuildClasspath, virtualFile == null || virtualFile.isDirectory() ? virtualFile : ArchiveVfsUtil.getJarRootForLocalFile(virtualFile));
			}

			for(final ExternalModuleBuildClasspathPojo moduleBuildClasspathPojo : projectBuildClasspathPojo.getModulesBuildClasspath().values())
			{
				final List<VirtualFile> moduleBuildClasspath = ContainerUtil.newArrayList(projectBuildClasspath);
				for(String path : moduleBuildClasspathPojo.getEntries())
				{
					final VirtualFile virtualFile = ExternalSystemUtil.findLocalFileByPath(path);
					ContainerUtil.addIfNotNull(moduleBuildClasspath, virtualFile == null || virtualFile.isDirectory() ? virtualFile : ArchiveVfsUtil.getJarRootForLocalFile(virtualFile));
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
		for(PsiElementFinder finder : PsiElementFinder.EP_NAME.getExtensions(myProject))
		{
			if(finder instanceof GradleClassFinder)
			{
				((GradleClassFinder) finder).clearCache();
				break;
			}
		}
	}

	@Nonnull
	public List<VirtualFile> getAllClasspathEntries()
	{
		checkRootsValidity(allFilesCache);
		return allFilesCache;
	}

	@Nonnull
	public List<VirtualFile> getModuleClasspathEntries(@Nonnull String externalModulePath)
	{
		checkRootsValidity(myClasspathMap.get().get(externalModulePath));
		List<VirtualFile> virtualFiles = myClasspathMap.get().get(externalModulePath);
		return virtualFiles == null ? Collections.emptyList() : virtualFiles;
	}

	private void checkRootsValidity(@Nullable List<VirtualFile> virtualFiles)
	{
		if(virtualFiles == null)
		{
			return;
		}

		if(!virtualFiles.isEmpty())
		{
			for(VirtualFile file : virtualFiles)
			{
				if(!file.isValid())
				{
					reload();
					break;
				}
			}
		}
	}
}
