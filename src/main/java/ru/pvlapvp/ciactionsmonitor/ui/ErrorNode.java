package ru.pvlapvp.ciactionsmonitor.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JTextArea;
import java.util.List;

/**
 * Visible error node. API exception messages never contain the access token.
 */
public final class ErrorNode extends ActionsServiceNode {

    private final String message;

    public ErrorNode(@NotNull String id, @NotNull String message) {
        super(id);
        this.message = message;
    }

    public @NotNull String message() {
        return message;
    }

    @Override
    protected @NotNull List<ActionsServiceNode> loadChildren(@NotNull Project project) {
        return List.of();
    }

    @Override
    public @NotNull PresentationData createPresentation(@NotNull Project project) {
        PresentationData data = new PresentationData();
        String firstLine = message.lines().findFirst().orElse("Unknown error");
        data.setPresentableText(firstLine.length() > 160 ? firstLine.substring(0, 160) + "..." : firstLine);
        data.setIcon(AllIcons.General.ErrorDialog);
        data.setForcedTextForeground(JBColor.RED);
        data.setTooltip(message);
        return data;
    }

    @Override
    public @Nullable JComponent createContentComponent(@NotNull Project project) {
        JTextArea area = new JTextArea(message);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setFont(UIUtil.getLabelFont());
        area.setBorder(JBUI.Borders.empty(8));
        return new JBScrollPane(area);
    }
}
