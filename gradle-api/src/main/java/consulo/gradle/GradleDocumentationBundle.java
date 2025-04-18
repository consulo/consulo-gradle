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
package consulo.gradle;

import consulo.application.CommonBundle;
import consulo.component.util.localize.AbstractBundle;
import consulo.util.lang.ref.SoftReference;
import org.jetbrains.annotations.PropertyKey;

import jakarta.annotation.Nonnull;
import java.lang.ref.Reference;
import java.util.ResourceBundle;

/**
 * @author Vladislav.Soroka
 * @since 8/29/13
 */
public class GradleDocumentationBundle extends AbstractBundle {

  public static String message(@Nonnull @PropertyKey(resourceBundle = PATH_TO_BUNDLE) String key, @Nonnull Object... params) {
    return BUNDLE.getMessage(key, params);
  }

  public static final String PATH_TO_BUNDLE = "i18n.GradleDocumentationBundle";
  private static final GradleDocumentationBundle BUNDLE = new GradleDocumentationBundle();
  private static Reference<ResourceBundle> ourBundle;

  public GradleDocumentationBundle() {
    super(PATH_TO_BUNDLE);
  }

  public static String messageOrDefault(@Nonnull @PropertyKey(resourceBundle = PATH_TO_BUNDLE) String key,
                                        String defaultValue,
                                        @Nonnull Object... params) {
    return CommonBundle.messageOrDefault(getBundle(), key, defaultValue, params);
  }

  private static ResourceBundle getBundle() {
    ResourceBundle bundle = SoftReference.dereference(ourBundle);
    if (bundle == null) {
      bundle = ResourceBundle.getBundle(PATH_TO_BUNDLE);
      ourBundle = new SoftReference<ResourceBundle>(bundle);
    }
    return bundle;
  }
}
