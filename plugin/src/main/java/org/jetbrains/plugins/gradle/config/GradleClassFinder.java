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

import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.impl.psi.NonClasspathClassFinder;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaPackage;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.ConcurrentFactoryMap;
import consulo.ide.impl.idea.openapi.externalSystem.psi.search.ExternalModuleBuildGlobalSearchScope;
import consulo.language.psi.PsiFile;
import consulo.language.psi.scope.EverythingGlobalScope;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.module.content.PackageDirectoryCache;
import consulo.module.content.ProjectFileIndex;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.inject.Inject;
import org.jetbrains.plugins.gradle.service.GradleBuildClasspathManager;
import org.jetbrains.plugins.groovy.GroovyFileType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * @author peter
 */
@ExtensionImpl
public class GradleClassFinder extends NonClasspathClassFinder {
    @Nonnull
    private final GradleBuildClasspathManager myBuildClasspathManager;
    private final Map<String, PackageDirectoryCache> myCaches;

    @Inject
    public GradleClassFinder(@Nonnull Project project, @Nonnull GradleBuildClasspathManager buildClasspathManager) {
        super(project, JavaFileType.DEFAULT_EXTENSION, GroovyFileType.DEFAULT_EXTENSION);
        myBuildClasspathManager = buildClasspathManager;

        myCaches = ConcurrentFactoryMap.createMap(path -> createCache(myBuildClasspathManager.getModuleClasspathEntries(path)));
    }

    @Override
    protected List<VirtualFile> calcClassRoots() {
        return myBuildClasspathManager.getAllClasspathEntries();
    }

    @Nonnull
    @Override
    protected PackageDirectoryCache getCache(@Nullable GlobalSearchScope scope) {
        if (scope instanceof ExternalModuleBuildGlobalSearchScope externalModuleBuildGlobalSearchScope) {
            return myCaches.get(externalModuleBuildGlobalSearchScope.getExternalModulePath());
        }
        return super.getCache(scope);
    }

    @Override
    public void clearCache() {
        super.clearCache();
        myCaches.clear();
    }

    @Override
    public PsiClass findClass(@Nonnull String qualifiedName, @Nonnull GlobalSearchScope scope) {
        PsiClass aClass = super.findClass(qualifiedName, scope);
        if (aClass == null || scope instanceof ExternalModuleBuildGlobalSearchScope || scope instanceof EverythingGlobalScope) {
            return aClass;
        }

        PsiFile containingFile = aClass.getContainingFile();
        VirtualFile file = containingFile != null ? containingFile.getVirtualFile() : null;
        ProjectFileIndex projectFileIndex = ProjectFileIndex.SERVICE.getInstance(myProject);
        return file != null
            && !projectFileIndex.isInContent(file)
            && !projectFileIndex.isInLibraryClasses(file)
            && !projectFileIndex.isInLibrarySource(file)
            ? aClass
            : null;
    }

    @Nonnull
    @Override
    public PsiJavaPackage[] getSubPackages(@Nonnull PsiJavaPackage psiPackage, @Nonnull GlobalSearchScope scope) {
        if (scope instanceof ExternalModuleBuildGlobalSearchScope) {
            return super.getSubPackages(psiPackage, scope);
        }
        else {
            return PsiJavaPackage.EMPTY_ARRAY;
        }
    }
}