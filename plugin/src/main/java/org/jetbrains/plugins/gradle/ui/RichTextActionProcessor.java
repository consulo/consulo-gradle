package org.jetbrains.plugins.gradle.ui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.MouseEvent;

import javax.swing.JComponent;
import javax.swing.JLabel;

import javax.annotation.Nonnull;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.ui.ClickListener;
import com.intellij.util.ui.UIUtil;

/**
 * @author Denis Zhdanov
 * @since 1/16/12 5:20 PM
 */
public class RichTextActionProcessor implements RichTextControlBuilder.RichTextProcessor {

  @Override
  public JComponent process(@Nonnull String s) {
    final ActionManager actionManager = ActionManager.getInstance();
    final AnAction action = actionManager.getAction(s);
    if (action == null) {
      return null;
    }
    final Presentation presentation = action.getTemplatePresentation();

    if (presentation.getIcon() != null) {
      return new ActionButton(action, presentation.clone(), GradleConstants.TOOL_WINDOW_TOOLBAR_PLACE, new Dimension(0, 0));
    }

    final String text = action.getTemplatePresentation().getText();
    JLabel result = new JLabel(text) {
      public void paint(Graphics g) {
        super.paint(g);
        final int y = g.getClipBounds().height - getFontMetrics(getFont()).getDescent() + 2;
        final int width = getFontMetrics(getFont()).stringWidth(getText());
        g.drawLine(0, y, width, y);
      }
    };
    Color color = UIUtil.isUnderDarcula() ? Color.ORANGE : Color.BLUE;
    result.setForeground(color);
    result.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    new ClickListener() {
      @Override
      public boolean onClick(MouseEvent e, int clickCount) {
        final AsyncResult<DataContext> callback = DataManager.getInstance().getDataContextFromFocus();
        final DataContext context = callback.getResult();
        if (context == null) {
          return false;
        }
        final Presentation presentation = new PresentationFactory().getPresentation(action);
        action.actionPerformed(new AnActionEvent(
          e, context, GradleConstants.TOOL_WINDOW_TOOLBAR_PLACE, presentation, ActionManager.getInstance(), e.getModifiers()
        ));
        return true;
      }
    }.installOn(result);
    return result;
  }

  @Nonnull
  @Override
  public String getKey() {
    return "action";
  }
}
