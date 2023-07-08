package org.jetbrains.plugins.gradle.ui;

import consulo.annotation.component.ExtensionImpl;
import consulo.gradle.GradleConstants;
import consulo.gradle.icon.GradleIconGroup;
import consulo.ide.impl.idea.openapi.externalSystem.service.task.ui.AbstractExternalSystemToolWindowFactory;
import consulo.ui.ex.toolWindow.ToolWindowAnchor;
import consulo.ui.image.Image;

import javax.annotation.Nonnull;

@ExtensionImpl
public class GradleToolWindowFactory extends AbstractExternalSystemToolWindowFactory {
  public GradleToolWindowFactory() {
    super(GradleConstants.SYSTEM_ID);
  }

  @Nonnull
  @Override
  public ToolWindowAnchor getAnchor() {
    return ToolWindowAnchor.RIGHT;
  }

  @Nonnull
  @Override
  public Image getIcon() {
    return GradleIconGroup.toolwindowgradle();
  }
}
