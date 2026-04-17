/**
 * @author VISTALL
 * @since 01/04/2023
 */
module org.jetbrains.plugins.gradle.api {
  requires transitive consulo.ide.api;
  requires transitive org.intellij.groovy.psi;

  requires transitive gradle.tooling.api;

  requires consulo.application.api;
  requires consulo.application.content.api;
  requires consulo.component.api;
  requires consulo.external.system.api;
  requires consulo.external.system.rt;
  requires consulo.language.api;
  requires consulo.language.editor.api;
  requires consulo.localize.api;
  requires consulo.platform.api;
  requires consulo.ui.api;
  requires consulo.util.lang;
  requires consulo.virtual.file.system.api;

  exports consulo.gradle;
  exports consulo.gradle.localize;
  exports consulo.gradle.icon;
  exports org.jetbrains.plugins.gradle.model.data;
  exports consulo.gradle.service.project;
  exports consulo.gradle.setting;
  exports consulo.gradle.codeInsight;
}
