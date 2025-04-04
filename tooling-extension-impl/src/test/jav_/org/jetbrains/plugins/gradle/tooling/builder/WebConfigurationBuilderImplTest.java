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
package org.jetbrains.plugins.gradle.tooling.builder;

import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.idea.IdeaModule;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.gradle.tooling.web.WebConfiguration;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.jetbrains.plugins.gradle.tooling.web.WebConfiguration.WarModel;
import static org.jetbrains.plugins.gradle.tooling.web.WebConfiguration.WebResource;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * @author Vladislav.Soroka
 * @since 11/29/13
 */
public class WebConfigurationBuilderImplTest extends AbstractModelBuilderTest {

  public WebConfigurationBuilderImplTest(@Nonnull String gradleVersion) {
    super(gradleVersion);
  }

  @Test
  public void testDefaultWarModel() throws Exception {
    DomainObjectSet<? extends IdeaModule> ideaModules = allModels.getIdeaProject().getModules();

    List<WebConfiguration> ideaModule = ContainerUtil.mapNotNull(ideaModules, new Function<IdeaModule, WebConfiguration>() {
      @Override
      public WebConfiguration fun(IdeaModule module) {
        return allModels.getExtraProject(module, WebConfiguration.class);
      }
    });

    assertEquals(1, ideaModule.size());
    WebConfiguration webConfiguration = ideaModule.get(0);
    assertEquals(1, webConfiguration.getWarModels().size());

    final WarModel warModel = webConfiguration.getWarModels().iterator().next();
    assertEquals("src/main/webapp", warModel.getWebAppDirName());

    assertArrayEquals(
      new String[]{"MANIFEST.MF", "additionalWebInf", "rootContent"},
      ContainerUtil.map2Array(warModel.getWebResources(), new Function<WebResource, Object>() {
        @Override
        public String fun(WebResource resource) {
          return resource.getFile().getName();
        }
      }));
  }

  @Override
  protected Set<Class> getModels() {
    return ContainerUtil.<Class>set(WebConfiguration.class);
  }
}
