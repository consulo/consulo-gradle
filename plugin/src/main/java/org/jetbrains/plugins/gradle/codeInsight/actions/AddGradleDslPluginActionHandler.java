/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.gradle.codeInsight.actions;

import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorPopupHelper;
import consulo.colorScheme.EditorColorsManager;
import consulo.colorScheme.EditorColorsScheme;
import consulo.colorScheme.EditorFontType;
import consulo.document.Document;
import consulo.gradle.localize.GradleLocalize;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.action.CodeInsightActionHandler;
import consulo.language.editor.util.LanguageEditorUtil;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.ui.ex.awt.ColoredListCellRenderer;
import consulo.ui.ex.popup.JBPopup;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.util.lang.Pair;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;

import javax.annotation.Nonnull;
import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author Vladislav.Soroka
 * @since 10/24/13
 */
class AddGradleDslPluginActionHandler implements CodeInsightActionHandler {
    private final Pair[] myPlugins;

    public AddGradleDslPluginActionHandler(Pair[] plugins) {
        myPlugins = plugins;
    }

    @Override
    public void invoke(@Nonnull final Project project, @Nonnull final Editor editor, @Nonnull final PsiFile file) {
        if (!LanguageEditorUtil.checkModificationAllowed(editor)) {
            return;
        }

        Consumer<Pair> runnable =
            selected -> new WriteCommandAction.Simple(project, GradleLocalize.gradleCodeinsightActionApply_pluginText().get(), file) {
                @Override
                protected void run() {
                    if (selected == null) {
                        return;
                    }
                    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);
                    GrStatement grStatement = factory.createStatementFromText(
                        String.format("apply plugin: '%s'", selected.getFirst()),
                        null
                    );

                    PsiElement anchor = file.findElementAt(editor.getCaretModel().getOffset());
                    PsiElement currentElement = PsiTreeUtil.getParentOfType(anchor, GrClosableBlock.class, GroovyFile.class);
                    if (currentElement != null) {
                        currentElement.addAfter(grStatement, anchor);
                    }
                    else {
                        file.addAfter(grStatement, file.findElementAt(editor.getCaretModel().getOffset() - 1));
                    }
                    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
                    Document document = documentManager.getDocument(file);
                    if (document != null) {
                        documentManager.commitDocument(document);
                    }
                }
            }.execute();

        JBPopup popup = JBPopupFactory.getInstance().createPopupChooserBuilder(List.of(myPlugins))
            .setTitle(GradleLocalize.gradleCodeinsightActionApply_pluginPopupTitle().get())
            .setNamerForFiltering(pair -> String.valueOf(pair.getFirst()))
            .setItemChosenCallback(runnable)
            .setRenderer(new ColoredListCellRenderer<Pair<String, String>>() {

                @Override
                protected void customizeCellRenderer(
                    @Nonnull JList<? extends Pair<String, String>> list,
                    Pair<String, String> descriptor,
                    int index,
                    boolean selected,
                    boolean hasFocus
                ) {
                    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
                    Font font = scheme.getFont(EditorFontType.PLAIN);
                    setFont(font);

                    append(String.valueOf(descriptor.getFirst()));

                    String description = String.valueOf(descriptor.getSecond());
                    append(description, SimpleTextAttributes.GRAY_ATTRIBUTES);
                }
            })
            .createPopup();

        EditorPopupHelper.getInstance().showPopupInBestPositionFor(editor, popup);
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }
}
