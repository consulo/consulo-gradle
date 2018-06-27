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
package org.jetbrains.plugins.gradle.model.data;

import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.AbstractExternalEntityData;
import javax.annotation.Nonnull;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * @author Vladislav.Soroka
 * @since 11/6/13
 */
public class WarModelData extends AbstractExternalEntityData {
  private static final long serialVersionUID = 1L;

  @Nonnull
  public static final Key<WarModelData> KEY = Key.create(WarModelData.class, ProjectKeys.LIBRARY_DEPENDENCY.getProcessingWeight() + 1);
  @Nonnull
  private final String myWebAppDirName;
  @Nonnull
  private final File myWebAppDir;
  @javax.annotation.Nullable
  private File myWebXml;
  @Nonnull
  private Map<String, Set<String>> myWebRoots;
  @Nonnull
  private Set<File> myClasspath;
  @javax.annotation.Nullable
  private String myManifestContent;


  public WarModelData(@Nonnull ProjectSystemId owner, @Nonnull String webAppDirName, @Nonnull File webAppDir) {
    super(owner);
    myWebAppDirName = webAppDirName;
    myWebAppDir = webAppDir;
    myWebRoots = Collections.emptyMap();
    myClasspath = Collections.emptySet();
  }

  @Nonnull
  public String getWebAppDirName() {
    return myWebAppDirName;
  }

  @Nonnull
  public File getWebAppDir() {
    return myWebAppDir;
  }

  public void setWebXml(@javax.annotation.Nullable File webXml) {
    myWebXml = webXml;
  }

  @javax.annotation.Nullable
  public File getWebXml() {
    return myWebXml;
  }

  public void setWebRoots(@javax.annotation.Nullable Map<String, Set<String>> webRoots) {
    myWebRoots = webRoots == null ? Collections.<String, Set<String>>emptyMap() : webRoots;
  }

  @Nonnull
  public Map<String, Set<String>> getWebRoots() {
    return myWebRoots;
  }

  public void setClasspath(@javax.annotation.Nullable Set<File> classpath) {
    myClasspath = classpath == null ? Collections.<File>emptySet() : classpath;
  }

  @Nonnull
  public Set<File> getClasspath() {
    return myClasspath;
  }

  public void setManifestContent(@javax.annotation.Nullable String manifestContent) {
    myManifestContent = manifestContent;
  }

  @javax.annotation.Nullable
  public String getManifestContent() {
    return myManifestContent;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    WarModelData that = (WarModelData)o;

    if (!myWebAppDirName.equals(that.myWebAppDirName)) return false;
    if (!myWebRoots.equals(that.myWebRoots)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myWebAppDirName.hashCode();
    result = 31 * result + myWebRoots.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "WarModelData{" +
           "myWebAppDirName='" + myWebAppDirName + '\'' +
           ", myWebAppDir=" + myWebAppDir +
           ", myWebXml=" + myWebXml +
           ", myWebRoots=" + myWebRoots +
           '}';
  }
}
