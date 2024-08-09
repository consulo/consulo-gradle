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
package org.jetbrains.plugins.gradle.service.execution;

import consulo.annotation.component.ExtensionImpl;
import consulo.externalSystem.service.execution.AbstractExternalSystemTaskConfigurationType;
import consulo.gradle.GradleConstants;

import javax.annotation.Nonnull;

/**
 * @author Denis Zhdanov
 * @since 23.05.13 17:56
 */
@ExtensionImpl
public class GradleExternalTaskConfigurationType extends AbstractExternalSystemTaskConfigurationType {
    @Nonnull
    public static GradleExternalTaskConfigurationType getInstance() {
        return EP_NAME.findExtensionOrFail(GradleExternalTaskConfigurationType.class);
    }

    public GradleExternalTaskConfigurationType() {
        super(GradleConstants.SYSTEM_ID);
    }
}
