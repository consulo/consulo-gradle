package org.jetbrains.plugins.gradle.ui;

import consulo.dataContext.DataContext;
import consulo.dataContext.DataManager;
import consulo.gradle.GradleConstants;
import consulo.ui.Size;
import consulo.ui.ex.action.*;
import consulo.ui.ex.awt.ClickListener;
import consulo.ui.ex.awt.UIUtil;
import consulo.util.concurrent.AsyncResult;

import javax.annotation.Nonnull;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

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
      return ActionButtonFactory.getInstance().create(action, presentation.clone(), GradleConstants.TOOL_WINDOW_TOOLBAR_PLACE, new Size(0, 0)).getComponent();
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
        final Presentation presentation = new BasePresentationFactory().getPresentation(action);
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
