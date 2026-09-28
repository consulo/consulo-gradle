// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.ui;

import consulo.annotation.component.ExtensionImpl;
import consulo.externalSystem.model.ProjectSystemId;
import consulo.externalSystem.ui.ExternalSystemIconProvider;
import consulo.gradle.GradleConstants;
import consulo.gradle.icon.GradleIconGroup;
import consulo.ui.image.Image;

@ExtensionImpl
public class GradleIconProvider implements ExternalSystemIconProvider {
    @Override
    public ProjectSystemId getSystemId() {
        return GradleConstants.SYSTEM_ID;
    }

    @Override
    public Image getReloadIcon() {
        return GradleIconGroup.gradleloadchanges();
    }

    @Override
    public Image getProjectIcon() {
        return GradleIconGroup.gradle();
    }
}
