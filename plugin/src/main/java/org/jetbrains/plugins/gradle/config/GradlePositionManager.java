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
package org.jetbrains.plugins.gradle.config;

import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.CachedValue;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.CachedValuesManager;
import consulo.application.util.ConcurrentFactoryMap;
import consulo.externalSystem.util.ExternalSystemApiUtil;
import consulo.externalSystem.util.ExternalSystemConstants;
import consulo.internal.com.sun.jdi.AbsentInformationException;
import consulo.internal.com.sun.jdi.ReferenceType;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.ModuleUtilCore;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.util.dataholder.Key;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import consulo.util.nodep.classloader.UrlClassLoader;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import org.jetbrains.plugins.gradle.service.GradleInstallationManager;
import org.jetbrains.plugins.groovy.impl.extensions.debugger.ScriptPositionManagerHelper;
import org.jetbrains.plugins.groovy.impl.runner.GroovyScriptUtil;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * @author John Murph
 */
@ExtensionImpl
public class GradlePositionManager extends ScriptPositionManagerHelper {
    private static final Logger LOG = Logger.getInstance(GradlePositionManager.class);

    private static final Pattern GRADLE_CLASS_PATTERN = Pattern.compile(".*_gradle_.*");
    private static final String SCRIPT_CLOSURE_PREFIX = "build_";
    private static final Key<CachedValue<ClassLoader>> GRADLE_CLASS_LOADER = Key.create("GRADLE_CLASS_LOADER");
    private static final Key<CachedValue<Map<File, String>>> GRADLE_CLASS_NAME = Key.create("GRADLE_CLASS_NAME");

    private final GradleInstallationManager myLibraryManager;

    @Inject
    public GradlePositionManager(@Nonnull GradleInstallationManager manager) {
        myLibraryManager = manager;
    }

    @Override
    public boolean isAppropriateRuntimeName(@Nonnull final String runtimeName) {
        return runtimeName.startsWith(SCRIPT_CLOSURE_PREFIX) || GRADLE_CLASS_PATTERN.matcher(runtimeName).matches();
    }

    @Override
    public boolean isAppropriateScriptFile(@Nonnull final GroovyFile scriptFile) {
        return GroovyScriptUtil.isSpecificScriptFile(scriptFile, GradleScriptType.INSTANCE);
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public String getRuntimeScriptName(GroovyFile groovyFile) {
        VirtualFile virtualFile = groovyFile.getVirtualFile();
        if (virtualFile == null) {
            return "";
        }

        final Module module = ModuleUtilCore.findModuleForPsiElement(groovyFile);
        if (module == null) {
            return "";
        }

        final File scriptFile = VirtualFileUtil.virtualToIoFile(virtualFile);
        final String className = CachedValuesManager.getManager(module.getProject())
            .getCachedValue(module, GRADLE_CLASS_NAME, new ScriptSourceMapCalculator(module), false).get(scriptFile);
        return className == null ? "" : className;
    }

    @Override
    @RequiredReadAction
    public PsiFile getExtraScriptIfNotFound(
        @Nonnull ReferenceType refType,
        @Nonnull String runtimeName,
        @Nonnull Project project,
        @Nonnull GlobalSearchScope scope
    ) {
        String sourceFilePath = getScriptForClassName(refType);
        if (sourceFilePath == null) {
            return null;
        }

        VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(sourceFilePath));
        if (virtualFile == null) {
            return null;
        }

        return PsiManager.getInstance(project).findFile(virtualFile);
    }

    @Nullable
    private static String getScriptForClassName(@Nonnull ReferenceType refType) {
        try {
            final List<String> data = refType.sourcePaths(null);
            if (!data.isEmpty()) {
                return data.get(0);
            }
        }
        catch (AbsentInformationException ignored) {
        }
        return null;
    }

    @Nullable
    private ClassLoader getGradleClassLoader(@Nonnull final Module module) {
        final Project project = module.getProject();
        return CachedValuesManager.getManager(project).getCachedValue(
            module,
            GRADLE_CLASS_LOADER,
            () -> CachedValueProvider.Result.create(createGradleClassLoader(module), ProjectRootManager.getInstance(project)),
            false
        );
    }

    @Nullable
    private ClassLoader createGradleClassLoader(@Nonnull Module module) {
        String rootProjectPath = ExternalSystemApiUtil.getExtensionSystemOption(module, ExternalSystemConstants.ROOT_PROJECT_PATH_KEY);
        if (StringUtil.isEmpty(rootProjectPath)) {
            return null;
        }
        final VirtualFile sdkHome = myLibraryManager.getGradleHome(module, module.getProject(), rootProjectPath);
        if (sdkHome == null) {
            return null;
        }

        List<URL> urls = new ArrayList<>();
        final VirtualFile libDir = sdkHome.findChild("lib");
        assert libDir != null;
        for (final VirtualFile child : libDir.getChildren()) {
            if ("jar".equals(child.getExtension())) {
                urls.add(VirtualFileUtil.convertToURL(child.getUrl()));
            }
        }

        return UrlClassLoader.build().urls(urls).get();
    }

    private class ScriptSourceMapCalculator implements CachedValueProvider<Map<File, String>> {
        private final Module myModule;

        public ScriptSourceMapCalculator(Module module) {
            myModule = module;
        }

        @Override
        public Result<Map<File, String>> compute() {
            final Map<File, String> result = ConcurrentFactoryMap.createMap(this::calcClassName);
            return Result.create(result, ProjectRootManager.getInstance(myModule.getProject()));
        }

        @Nullable
        private String calcClassName(File scriptFile) {
            final ClassLoader loader = getGradleClassLoader(myModule);
            if (loader != null) {
                Class<?> fileScriptSource;
                try {
                    fileScriptSource = Class.forName("org.gradle.groovy.scripts.UriScriptSource", true, loader);
                }
                catch (ClassNotFoundException e) {
                    try {
                        //before 0.9
                        fileScriptSource = Class.forName("org.gradle.groovy.scripts.FileScriptSource", true, loader);
                    }
                    catch (ClassNotFoundException e1) {
                        return null;
                    }
                }

                try {
                    final Object source = fileScriptSource.getConstructor(String.class, File.class)
                        .newInstance("script", scriptFile);
                    return (String)fileScriptSource.getMethod("getClassName").invoke(source);
                }
                catch (Exception e) {
                    LOG.error(e);
                }
            }
            return null;
        }
    }
}
