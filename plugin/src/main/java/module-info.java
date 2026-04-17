/**
 * @author VISTALL
 * @since 2023-03-31
 */
open module org.jetbrains.plugins.gradle {
    requires com.google.gson;
    requires com.intellij.properties;

    requires consulo.ide.api;
    requires consulo.application.api;
    requires consulo.build.ui.api;
    requires consulo.code.editor.api;
    requires consulo.color.scheme.api;
    requires consulo.compiler.api;
    requires consulo.component.api;
    requires consulo.configurable.api;
    requires consulo.container.api;
    requires consulo.application.content.api;
    requires consulo.disposer.api;
    requires consulo.document.api;
    requires consulo.execution.api;
    requires consulo.external.system.api;
    requires consulo.file.chooser.api;
    requires consulo.file.editor.api;
    requires consulo.file.template.api;
    requires consulo.http.api;
    requires consulo.internal.jdi;
    requires consulo.java.execution.api;
    requires consulo.java.execution.impl;
    requires consulo.language.api;
    requires consulo.language.impl;
    requires consulo.language.editor.api;
    requires consulo.localize.api;
    requires consulo.logging.api;
    requires consulo.module.api;
    requires consulo.module.content.api;
    requires consulo.module.ui.api;
    requires consulo.navigation.api;
    requires consulo.platform.api;
    requires consulo.process.api;
    requires consulo.project.api;
    requires consulo.project.ui.api;
    requires consulo.ui.api;
    requires consulo.ui.ex.api;
    requires consulo.ui.ex.awt.api;
    requires consulo.util.collection;
    requires consulo.util.concurrent;
    requires consulo.util.dataholder;
    requires consulo.util.io;
    requires consulo.util.lang;
    requires consulo.util.nodep;
    requires consulo.util.xml.serializer;
    requires consulo.virtual.file.system.api;

    requires org.apache.groovy;
    requires org.intellij.groovy;
    requires org.intellij.groovy.psi;
    requires org.jetbrains.plugins.gradle.api;
    requires org.jetbrains.plugins.gradle.tooling;

    requires java.desktop;
}
