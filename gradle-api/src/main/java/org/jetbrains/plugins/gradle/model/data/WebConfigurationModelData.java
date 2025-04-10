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

import consulo.externalSystem.model.Key;
import consulo.externalSystem.model.ProjectKeys;
import consulo.externalSystem.model.ProjectSystemId;
import consulo.externalSystem.service.project.AbstractExternalEntityData;

import jakarta.annotation.Nonnull;
import java.util.List;

/**
 * @author Vladislav.Soroka
 * @since 11/6/13
 */
public class WebConfigurationModelData extends AbstractExternalEntityData {
  private static final long serialVersionUID = 1L;

  @Nonnull
  public static final Key<WebConfigurationModelData> KEY = Key.create(WebConfigurationModelData.class,
                                                                      ProjectKeys.LIBRARY_DEPENDENCY.getProcessingWeight() + 1);

  @Nonnull
  private final List<War> myWars;

  public WebConfigurationModelData(@Nonnull ProjectSystemId owner, @Nonnull List<War> warModels) {
    super(owner);
    myWars = warModels;
  }

  @Nonnull
  public List<War> getWars() {
    return myWars;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof WebConfigurationModelData)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }

    WebConfigurationModelData data = (WebConfigurationModelData)o;

    if (!myWars.equals(data.myWars)) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myWars.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "WebConfigurationModelData{" +
      "myWars=" + myWars +
      '}';
  }
}
