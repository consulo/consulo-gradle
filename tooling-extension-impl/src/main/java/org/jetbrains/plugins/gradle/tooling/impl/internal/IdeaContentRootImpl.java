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
package org.jetbrains.plugins.gradle.tooling.impl.internal;

import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.idea.IdeaContentRoot;
import org.gradle.tooling.model.idea.IdeaSourceDirectory;
import org.gradle.tooling.model.internal.ImmutableDomainObjectSet;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Vladislav.Soroka
 * @since 11/13/13
 */
public class IdeaContentRootImpl implements IdeaContentRoot, Serializable {
    private static final long serialVersionUID = 4015386851732249650L;
    
    private final File myRootDirectory;
    private final List<IdeaSourceDirectory> mySourceDirectories = new ArrayList<>();
    private final List<IdeaSourceDirectory> myTestDirectories = new ArrayList<>();
    private final List<IdeaSourceDirectory> myResourceDirectories = new ArrayList<>();
    private final List<IdeaSourceDirectory> myTestResourceDirectories = new ArrayList<>();
    private final List<IdeaSourceDirectory> myGeneratedSourceDirectories = new ArrayList<>();
    private final List<IdeaSourceDirectory> myGeneratedTestSourceDirectories = new ArrayList<>();

    private final Set<File> myExcludeDirectories = new HashSet<>();

    public IdeaContentRootImpl(File rootDirectory) {
        myRootDirectory = rootDirectory;
    }

    @Override
    public File getRootDirectory() {
        return myRootDirectory;
    }

    @Override
    public DomainObjectSet<? extends IdeaSourceDirectory> getSourceDirectories() {
        return ImmutableDomainObjectSet.of(mySourceDirectories);
    }

    @Override
    public DomainObjectSet<? extends IdeaSourceDirectory> getGeneratedSourceDirectories() {
        return ImmutableDomainObjectSet.of(myGeneratedSourceDirectories);
    }

    @Override
    public DomainObjectSet<? extends IdeaSourceDirectory> getGeneratedTestDirectories() {
        return ImmutableDomainObjectSet.of(myGeneratedTestSourceDirectories);
    }

    public void addSourceDirectory(IdeaSourceDirectory sourceDirectory) {
        mySourceDirectories.add(sourceDirectory);
    }

    public void addTestDirectory(IdeaSourceDirectory testDirectory) {
        myTestDirectories.add(testDirectory);
    }

    public void addResourceDirectory(IdeaSourceDirectory resourceDirectory) {
        myResourceDirectories.add(resourceDirectory);
    }

    public void addTestResourceDirectory(IdeaSourceDirectory resourceDirectory) {
        myTestResourceDirectories.add(resourceDirectory);
    }

    public void addExcludeDirectory(File excludeDirectory) {
        myExcludeDirectories.add(excludeDirectory);
    }

    @Override
    public DomainObjectSet<? extends IdeaSourceDirectory> getTestDirectories() {
        return ImmutableDomainObjectSet.of(myTestDirectories);
    }

    @Override
    public DomainObjectSet<? extends IdeaSourceDirectory> getResourceDirectories() {
        return ImmutableDomainObjectSet.of(myResourceDirectories);
    }

    @Override
    public DomainObjectSet<? extends IdeaSourceDirectory> getTestResourceDirectories() {
        return ImmutableDomainObjectSet.of(myTestResourceDirectories);
    }

    @Override
    public Set<File> getExcludeDirectories() {
        return myExcludeDirectories;
    }
}
