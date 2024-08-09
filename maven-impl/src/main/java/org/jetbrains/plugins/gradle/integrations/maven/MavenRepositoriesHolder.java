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
package org.jetbrains.plugins.gradle.integrations.maven;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.maven.rt.server.common.model.MavenRemoteRepository;
import consulo.project.Project;
import jakarta.inject.Singleton;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Vladislav.Soroka
 * @since 10/28/13
 */
@Singleton
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public class MavenRepositoriesHolder {
    private volatile Set<MavenRemoteRepository> myRemoteRepositories;

    public MavenRepositoriesHolder() {
        myRemoteRepositories = new HashSet<>();
    }

    public static MavenRepositoriesHolder getInstance(Project project) {
        return project.getInstance(MavenRepositoriesHolder.class);
    }

    public void update(Set<MavenRemoteRepository> remoteRepositories) {
        myRemoteRepositories = new HashSet<>(remoteRepositories);
    }

    public Set<MavenRemoteRepository> getRemoteRepositories() {
        return myRemoteRepositories;
    }

    public boolean contains(String repositoryId) {
        for (MavenRemoteRepository repository : myRemoteRepositories) {
            if (repository.getId().equals(repositoryId)) {
                return true;
            }
        }
        return false;
    }
}
