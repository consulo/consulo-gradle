/**
 * @author VISTALL
 * @since 01/04/2023
 */
module org.jetbrains.plugins.gradle.api {
  requires transitive consulo.ide.api;
  requires transitive org.intellij.groovy.psi;

  requires gradle.all;

  requires transitive org.jetbrains.plugins.gradle.tooling;

  exports consulo.gradle;
  exports consulo.gradle.localize;
  exports consulo.gradle.icon;
  exports org.jetbrains.plugins.gradle.model.data;
  exports consulo.gradle.service.project;
  exports consulo.gradle.setting;
  exports consulo.gradle.codeInsight;
}