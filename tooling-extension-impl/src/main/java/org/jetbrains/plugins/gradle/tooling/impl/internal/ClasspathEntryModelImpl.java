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

import org.jetbrains.plugins.gradle.tooling.model.ClasspathEntryModel;

import java.io.Serializable;
import java.util.Set;

/**
 * @author Vladislav.Soroka
 * @since 12/20/13
 */
public class ClasspathEntryModelImpl implements ClasspathEntryModel, Serializable {

    private final Set<String> classes;

    private final Set<String> sources;

    private final Set<String> javadoc;

    public ClasspathEntryModelImpl(Set<String> classes, Set<String> sources, Set<String> javadoc) {
        this.classes = classes;
        this.sources = sources;
        this.javadoc = javadoc;
    }


    @Override
    public Set<String> getClasses() {
        return classes;
    }


    @Override
    public Set<String> getSources() {
        return sources;
    }


    @Override
    public Set<String> getJavadoc() {
        return javadoc;
    }
}
