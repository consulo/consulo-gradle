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
package org.jetbrains.plugins.gradle.tooling.model;

import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.idea.IdeaContentRoot;

import java.io.File;
import java.io.Serializable;
import java.util.List;

/**
 * @author Vladislav.Soroka
 * @since 11/5/13
 */
public interface ModuleExtendedModel extends Serializable {
  /**
   * The group of the module.
   *
   * @return module group
   */
  String getGroup();

  /**
   * The name of the module.
   *
   * @return module name
   */
  String getName();

  /**
   * The version of the module
   *
   * @return module version
   */
  String getVersion();

  /**
   * The paths where the artifacts is constructed
   *
   * @return
   */
  List<File> getArtifacts();

  /**
   * All IDEA content roots.
   *
   * @return content roots
   */
  DomainObjectSet<? extends IdeaContentRoot> getContentRoots();

  /**
   * The build directory.
   *
   * @return the build directory.
   */
  File getBuildDir();

  /**
   * The compiler output directories.
   *
   * @return the compiler output directories.
   */
  ExtIdeaCompilerOutput getCompilerOutput();
}
