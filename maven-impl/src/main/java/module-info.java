/**
 * @author VISTALL
 * @since 2023-04-01
 */
module org.jetbrains.plugins.gradle.maven.impl {
    requires consulo.ide.api;
    requires consulo.application.api;
    requires consulo.code.editor.api;
    requires consulo.component.api;
    requires consulo.external.system.api;
    requires consulo.java.language.api;
    requires consulo.language.api;
    requires consulo.language.editor.api;
    requires consulo.module.api;
    requires consulo.project.api;
    requires consulo.ui.api;
    requires consulo.util.collection;
    requires consulo.util.io;
    requires consulo.util.lang;
    requires consulo.virtual.file.system.api;

    requires org.intellij.groovy.psi;
    requires org.jetbrains.plugins.gradle.api;
    requires org.jetbrains.idea.maven;
    requires org.jetbrains.idea.maven.server.common;
}
