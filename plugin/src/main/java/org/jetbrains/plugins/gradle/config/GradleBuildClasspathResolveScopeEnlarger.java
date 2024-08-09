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
package org.jetbrains.plugins.gradle.config;

import com.intellij.java.language.impl.psi.NonClasspathDirectoriesScope;
import com.intellij.java.language.psi.PsiElementFinder;
import consulo.annotation.component.ExtensionImpl;
import consulo.content.scope.SearchScope;
import consulo.ide.impl.idea.openapi.vfs.VfsUtilCore;
import consulo.language.psi.ResolveScopeEnlarger;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.plugins.groovy.GroovyFileType;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * @author Vladislav.Soroka
 * @since 5/16/2014
 */
@ExtensionImpl
public class GradleBuildClasspathResolveScopeEnlarger extends ResolveScopeEnlarger {
    @Override
    public SearchScope getAdditionalResolveScope(@Nonnull VirtualFile file, Project project) {
        String fileExtension = file.getExtension();
        if (GroovyFileType.DEFAULT_EXTENSION.equals(fileExtension)) {
            GradleClassFinder gradleClassFinder =
                project.getExtensionPoint(PsiElementFinder.class).findExtensionOrFail(GradleClassFinder.class);

            final List<VirtualFile> roots = gradleClassFinder.getClassRoots();
            for (VirtualFile root : roots) {
                if (VfsUtilCore.isAncestor(root, file, true)) {
                    return NonClasspathDirectoriesScope.compose(roots);
                }
            }
        }
        return null;
    }
}
