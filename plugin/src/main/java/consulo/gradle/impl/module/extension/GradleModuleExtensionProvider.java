package consulo.gradle.impl.module.extension;

import consulo.annotation.component.ExtensionImpl;
import consulo.externalSystem.service.module.extension.ExternalSystemModuleExtensionImpl;
import consulo.externalSystem.service.module.extension.ExternalSystemMutableModuleExtensionImpl;
import consulo.gradle.icon.GradleIconGroup;
import consulo.localize.LocalizeValue;
import consulo.module.content.layer.ModuleExtensionProvider;
import consulo.module.content.layer.ModuleRootLayer;
import consulo.module.extension.ModuleExtension;
import consulo.module.extension.MutableModuleExtension;
import consulo.ui.image.Image;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author VISTALL
 * @since 31/03/2023
 */
@ExtensionImpl
public class GradleModuleExtensionProvider implements ModuleExtensionProvider<ExternalSystemModuleExtensionImpl> {
  @Nonnull
  @Override
  public String getId() {
    return "GRADLE";
  }

  @Nullable
  @Override
  public String getParentId() {
    return "java";
  }

  @Override
  public boolean isSystemOnly() {
    return true;
  }

  @Nonnull
  @Override
  public LocalizeValue getName() {
    return LocalizeValue.localizeTODO("Gradle");
  }

  @Nonnull
  @Override
  public Image getIcon() {
    return GradleIconGroup.gradle();
  }

  @Nonnull
  @Override
  public ModuleExtension<ExternalSystemModuleExtensionImpl> createImmutableExtension(@Nonnull ModuleRootLayer moduleRootLayer) {
    return new ExternalSystemModuleExtensionImpl(getId(), moduleRootLayer);
  }

  @Nonnull
  @Override
  public MutableModuleExtension<ExternalSystemModuleExtensionImpl> createMutableExtension(@Nonnull ModuleRootLayer moduleRootLayer) {
    return new ExternalSystemMutableModuleExtensionImpl(getId(), moduleRootLayer);
  }
}
