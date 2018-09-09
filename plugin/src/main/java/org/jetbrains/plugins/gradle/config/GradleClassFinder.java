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

package org.jetbrains.plugins.gradle.config;

import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import org.jetbrains.plugins.gradle.service.GradleBuildClasspathManager;
import org.jetbrains.plugins.groovy.GroovyFileType;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.externalSystem.psi.search.ExternalModuleBuildGlobalSearchScope;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.impl.PackageDirectoryCache;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.NonClasspathClassFinder;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaPackage;
import com.intellij.psi.search.EverythingGlobalScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ConcurrentFactoryMap;

/**
 * @author peter
 */
public class GradleClassFinder extends NonClasspathClassFinder
{
	@Nonnull
	private final GradleBuildClasspathManager myBuildClasspathManager;
	private final Map<String, PackageDirectoryCache> myCaches = new ConcurrentFactoryMap<String, PackageDirectoryCache>()
	{
		@javax.annotation.Nullable
		@Override
		protected PackageDirectoryCache create(String path)
		{
			return createCache(myBuildClasspathManager.getModuleClasspathEntries(path));
		}
	};

	public GradleClassFinder(@Nonnull Project project, @Nonnull GradleBuildClasspathManager buildClasspathManager)
	{
		super(project, JavaFileType.DEFAULT_EXTENSION, GroovyFileType.DEFAULT_EXTENSION);
		myBuildClasspathManager = buildClasspathManager;
	}

	@Override
	protected List<VirtualFile> calcClassRoots()
	{
		return myBuildClasspathManager.getAllClasspathEntries();
	}

	@Nonnull
	@Override
	protected PackageDirectoryCache getCache(@javax.annotation.Nullable GlobalSearchScope scope)
	{
		if(scope instanceof ExternalModuleBuildGlobalSearchScope)
		{
			return myCaches.get(((ExternalModuleBuildGlobalSearchScope) scope).getExternalModulePath());
		}
		return super.getCache(scope);
	}

	@Override
	public void clearCache()
	{
		super.clearCache();
		myCaches.clear();
	}

	@Override
	public PsiClass findClass(@Nonnull String qualifiedName, @Nonnull GlobalSearchScope scope)
	{
		PsiClass aClass = super.findClass(qualifiedName, scope);
		if(aClass == null || scope instanceof ExternalModuleBuildGlobalSearchScope || scope instanceof EverythingGlobalScope)
		{
			return aClass;
		}

		PsiFile containingFile = aClass.getContainingFile();
		VirtualFile file = containingFile != null ? containingFile.getVirtualFile() : null;
		return (file != null && !ProjectFileIndex.SERVICE.getInstance(myProject).isInContent(file) && !ProjectFileIndex.SERVICE.getInstance
				(myProject).isInLibraryClasses(file) && !ProjectFileIndex.SERVICE.getInstance(myProject).isInLibrarySource(file)) ? aClass : null;
	}

	@Nonnull
	@Override
	public PsiJavaPackage[] getSubPackages(@Nonnull PsiJavaPackage psiPackage, @Nonnull GlobalSearchScope scope)
	{
		if(scope instanceof ExternalModuleBuildGlobalSearchScope)
		{
			return super.getSubPackages(psiPackage, scope);
		}
		else
		{
			return PsiJavaPackage.EMPTY_ARRAY;
		}
	}
}