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
import consulo.ide.impl.idea.codeInsight.CodeInsightUtilBase;
import consulo.ide.impl.idea.codeInsight.lookup.impl.LookupCellRenderer;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.action.CodeInsightActionHandler;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.ui.ex.popup.JBPopup;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.util.lang.Pair;
import consulo.gradle.GradleBundle;
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
    if (!CodeInsightUtilBase.prepareEditorForWrite(editor)) return;

    Consumer<Pair> runnable =
      selected -> new WriteCommandAction.Simple(project, GradleBundle.message("gradle.codeInsight.action.apply_plugin.text"), file) {
        @Override
        protected void run() {
          if (selected == null) return;
          GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);
          GrStatement grStatement = factory.createStatementFromText(
            String.format("apply plugin: '%s'", selected.getFirst()), null);

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
                                  .setTitle(GradleBundle.message("gradle.codeInsight.action.apply_plugin.popup.title"))
                                  .setNamerForFiltering(pair -> String.valueOf(pair.getFirst()))
                                  .setItemChosenCallback(runnable)
                                  .setRenderer(new MyListCellRenderer())
                                  .createPopup();

    EditorPopupHelper.getInstance().showPopupInBestPositionFor(editor, popup);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  private static class MyListCellRenderer implements ListCellRenderer<Pair<String, String>> {
    private final JPanel myPanel;
    private final JLabel myNameLabel;
    private final JLabel myDescLabel;

    public MyListCellRenderer() {
      myPanel = new JPanel(new BorderLayout());
      myPanel.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 0));
      myNameLabel = new JLabel();

      myPanel.add(myNameLabel, BorderLayout.WEST);
      myPanel.add(new JLabel("     "));
      myDescLabel = new JLabel();
      myPanel.add(myDescLabel, BorderLayout.EAST);

      EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
      Font font = scheme.getFont(EditorFontType.PLAIN);
      myNameLabel.setFont(font);
      myDescLabel.setFont(font);
    }

    @Override
    public Component getListCellRendererComponent(JList list, Pair value, int index, boolean isSelected, boolean cellHasFocus) {

      Pair descriptor = value;
      Color backgroundColor = isSelected ? list.getSelectionBackground() : list.getBackground();

      myNameLabel.setText(String.valueOf(descriptor.getFirst()));
      myNameLabel.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
      myPanel.setBackground(backgroundColor);

      String description = String.format("<html><div WIDTH=%d>%s</div><html>", 400, String.valueOf(descriptor.getSecond()));
      myDescLabel.setText(description);
      myDescLabel.setForeground(LookupCellRenderer.getGrayedForeground(isSelected));
      myDescLabel.setBackground(backgroundColor);

      return myPanel;
    }
  }
}
