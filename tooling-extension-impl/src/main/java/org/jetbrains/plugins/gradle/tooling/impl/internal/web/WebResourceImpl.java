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
package org.jetbrains.plugins.gradle.tooling.impl.internal.web;

import org.jetbrains.plugins.gradle.tooling.web.WebConfiguration;

import java.io.File;

/**
 * @author Vladislav.Soroka
 * @since 2/10/14
 */
public class WebResourceImpl implements WebConfiguration.WebResource {
  
  private final String myWarDirectory;
  
  private final String myRelativePath;
  
  private final File file;

  public WebResourceImpl( String warDirectory,  String relativePath,  File file) {
    myWarDirectory = warDirectory;
    this.myRelativePath = relativePath;
    this.file = file;
  }

  
  @Override
  public String getWarDirectory() {
    return myWarDirectory;
  }

  
  @Override
  public String getRelativePath() {
    return myRelativePath;
  }

  
  @Override
  public File getFile() {
    return file;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof WebResourceImpl)) return false;

    WebResourceImpl resource = (WebResourceImpl)o;
    if (!file.getPath().equals(resource.file.getPath())) return false;
    if (myWarDirectory != resource.myWarDirectory) return false;
    if (!myRelativePath.equals(resource.myRelativePath)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myWarDirectory.hashCode();
    result = 31 * result + myRelativePath.hashCode();
    result = 31 * result + file.getPath().hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "WebResourceImpl{" +
           "myWarDirectory=" + myWarDirectory +
           ", warRelativePath='" + myRelativePath + '\'' +
           ", file=" + file +
           '}';
  }
}
