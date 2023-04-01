package consulo.gradle.impl;

import consulo.annotation.component.ExtensionImpl;
import consulo.fileTemplate.FileTemplateContributor;
import consulo.fileTemplate.FileTemplateRegistrator;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 01/04/2023
 */
@ExtensionImpl
public class GradleFileTemplateContributor implements FileTemplateContributor {
  @Override
  public void register(@Nonnull FileTemplateRegistrator fileTemplateRegistrator) {
    fileTemplateRegistrator.registerInternalTemplate("Gradle Build Script");
    fileTemplateRegistrator.registerInternalTemplate("Gradle Build Script with wrapper");
  }
}
