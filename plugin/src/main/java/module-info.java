/**
 * @author VISTALL
 * @since 31/03/2023
 */
open module org.jetbrains.plugins.gradle {
  requires consulo.ide.api;

  requires consulo.java.execution.api;
  requires consulo.java.execution.impl;
  requires com.intellij.properties;
  requires org.intellij.groovy.psi;
  requires org.intellij.groovy;

  requires org.jetbrains.plugins.gradle.api;

  requires consulo.internal.jdi;

  requires com.google.gson;
  requires org.apache.groovy;

  requires consulo.util.nodep;

  requires gradle.all;
  requires kryo;
  requires minlog;
  requires objenesis;

  requires org.jetbrains.plugins.gradle.tooling;
  requires org.jetbrains.plugins.gradle.tooling.impl;

  // TODO remove in future
  requires consulo.ide.impl;
  requires java.desktop;

  exports consulo.gradle.codeInspection.localize;
}