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
package org.jetbrains.plugins.gradle.codeInspection;

import consulo.annotation.DeprecationInfo;
import consulo.annotation.internal.MigratedExtensionsTo;
import consulo.component.util.localize.AbstractBundle;
import consulo.gradle.codeInspection.localize.GradleInspectionLocalize;
import org.jetbrains.annotations.PropertyKey;

/**
 * @author Vladislav.Soroka
 * @since 2013-09-13
 */
@Deprecated
@DeprecationInfo("GradleInspectionLocalize")
@MigratedExtensionsTo(GradleInspectionLocalize.class)
public class GradleInspectionBundle extends AbstractBundle {
    private static final String BUNDLE = "org.jetbrains.plugins.gradle.codeInspection.GradleInspectionBundle";

    private static final GradleInspectionBundle ourInstance = new GradleInspectionBundle();

    private GradleInspectionBundle() {
        super(BUNDLE);
    }

    public static String message(@PropertyKey(resourceBundle = BUNDLE) String key) {
        return ourInstance.getMessage(key);
    }

    public static String message(@PropertyKey(resourceBundle = BUNDLE) String key, Object... params) {
        return ourInstance.getMessage(key, params);
    }
}