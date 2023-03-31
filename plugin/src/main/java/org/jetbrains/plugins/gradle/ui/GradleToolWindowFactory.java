package org.jetbrains.plugins.gradle.ui;

import consulo.annotation.component.ExtensionImpl;
import consulo.gradle.icon.GradleIconGroup;
import consulo.ide.impl.idea.openapi.externalSystem.service.task.ui.AbstractExternalSystemToolWindowFactory;
import consulo.localize.LocalizeValue;
import consulo.ui.ex.toolWindow.ToolWindowAnchor;
import consulo.ui.image.Image;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import javax.annotation.Nonnull;

@ExtensionImpl
public class GradleToolWindowFactory extends AbstractExternalSystemToolWindowFactory {
  public GradleToolWindowFactory() {
    super(GradleConstants.SYSTEM_ID);
  }

  @Nonnull
  @Override
  public String getId() {
    return "Gradle";
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

  @Nonnull
  @Override
  public LocalizeValue getDisplayName() {
    return LocalizeValue.localizeTODO("Gradle");
  }
}
