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

import org.gradle.tooling.model.idea.IdeaSourceDirectory;

import java.io.File;
import java.io.Serializable;

/**
 * @author Vladislav.Soroka
 * @since 11/15/13
 */
public class IdeaSourceDirectoryImpl implements IdeaSourceDirectory, Serializable {
    private static final long serialVersionUID = 7981587002081712567L;
    private final File myDirectory;
    private final boolean myGenerated;

    public IdeaSourceDirectoryImpl(File directory, boolean generated) {
        myDirectory = directory;
        myGenerated = generated;
    }

    @Override
    public File getDirectory() {
        return myDirectory;
    }

    @Override
    public boolean isGenerated() {
        return myGenerated;
    }
}
