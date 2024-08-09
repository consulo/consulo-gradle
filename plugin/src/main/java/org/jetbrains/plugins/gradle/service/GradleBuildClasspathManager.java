// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service;

import com.intellij.java.language.psi.PsiElementFinder;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.externalSystem.ExternalSystemManager;
import consulo.externalSystem.model.project.ExternalModuleBuildClasspathPojo;
import consulo.externalSystem.model.project.ExternalProjectBuildClasspathPojo;
import consulo.externalSystem.setting.AbstractExternalSystemLocalSettings;
import consulo.externalSystem.util.ExternalSystemApiUtil;
import consulo.gradle.GradleConstants;
import consulo.ide.impl.idea.openapi.externalSystem.util.ExternalSystemUtil;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.archive.ArchiveVfsUtil;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.plugins.gradle.config.GradleClassFinder;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Vladislav.Soroka
 */
@Singleton
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public class GradleBuildClasspathManager {
    @Nonnull
    private final Project myProject;

    @Nonnull
    private volatile List<VirtualFile> allFilesCache;

    @Nonnull
    private final AtomicReference<Map<String/*module path*/, List<VirtualFile> /*module build classpath*/>> myClasspathMap =
        new AtomicReference<>(new HashMap<>());

    @Inject
    public GradleBuildClasspathManager(@Nonnull Project project) {
        myProject = project;
        allFilesCache = new ArrayList<>();
    }

    @Nonnull
    public static GradleBuildClasspathManager getInstance(@Nonnull Project project) {
        return project.getInstance(GradleBuildClasspathManager.class);
    }

    public void reload() {
        ExternalSystemManager<?, ?, ?, ?, ?> manager = ExternalSystemApiUtil.getManager(GradleConstants.SYSTEM_ID);
        assert manager != null;
        AbstractExternalSystemLocalSettings localSettings = manager.getLocalSettingsProvider().apply(myProject);

        Map<String/*module path*/, List<VirtualFile> /*module build classpath*/> map = new HashMap<>();

        for (final ExternalProjectBuildClasspathPojo projectBuildClasspathPojo : localSettings.getProjectBuildClasspath().values()) {
            final List<VirtualFile> projectBuildClasspath = new ArrayList<>();
            for (String path : projectBuildClasspathPojo.getProjectBuildClasspath()) {
                final VirtualFile virtualFile = ExternalSystemUtil.findLocalFileByPath(path);
                ContainerUtil.addIfNotNull(
                    projectBuildClasspath,
                    virtualFile == null || virtualFile.isDirectory()
                        ? virtualFile
                        : ArchiveVfsUtil.getJarRootForLocalFile(virtualFile)
                );
            }

            for (final ExternalModuleBuildClasspathPojo moduleBuildClasspathPojo : projectBuildClasspathPojo.getModulesBuildClasspath()
                .values()) {
                final List<VirtualFile> moduleBuildClasspath = ContainerUtil.newArrayList(projectBuildClasspath);
                for (String path : moduleBuildClasspathPojo.getEntries()) {
                    final VirtualFile virtualFile = ExternalSystemUtil.findLocalFileByPath(path);
                    ContainerUtil.addIfNotNull(
                        moduleBuildClasspath,
                        virtualFile == null || virtualFile.isDirectory()
                            ? virtualFile
                            : ArchiveVfsUtil.getJarRootForLocalFile(virtualFile)
                    );
                }

                map.put(moduleBuildClasspathPojo.getPath(), moduleBuildClasspath);
            }
        }

        myClasspathMap.set(map);

        Set<VirtualFile> set = new LinkedHashSet<>();
        for (List<VirtualFile> virtualFiles : myClasspathMap.get().values()) {
            set.addAll(virtualFiles);
        }
        allFilesCache = ContainerUtil.newArrayList(set);
        for (PsiElementFinder finder : PsiElementFinder.EP_NAME.getExtensions(myProject)) {
            if (finder instanceof GradleClassFinder gradleClassFinder) {
                gradleClassFinder.clearCache();
                break;
            }
        }
    }

    @Nonnull
    public List<VirtualFile> getAllClasspathEntries() {
        checkRootsValidity(allFilesCache);
        return allFilesCache;
    }

    @Nonnull
    public List<VirtualFile> getModuleClasspathEntries(@Nonnull String externalModulePath) {
        checkRootsValidity(myClasspathMap.get().get(externalModulePath));
        List<VirtualFile> virtualFiles = myClasspathMap.get().get(externalModulePath);
        return virtualFiles == null ? Collections.emptyList() : virtualFiles;
    }

    private void checkRootsValidity(@Nullable List<VirtualFile> virtualFiles) {
        if (virtualFiles == null) {
            return;
        }

        if (!virtualFiles.isEmpty()) {
            for (VirtualFile file : virtualFiles) {
                if (!file.isValid()) {
                    reload();
                    break;
                }
            }
        }
    }
}
