package com.company.gitea.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JTextArea;
import java.util.List;

/**
 * A non-error informational node, for example "no workflows" or "no server configured".
 */
public final class GiteaMessageNode extends GiteaServiceNode {

    private final String message;

    public GiteaMessageNode(@NotNull String id, @NotNull String message) {
        super(id);
        this.message = message;
    }

    @Override
    protected @NotNull List<GiteaServiceNode> loadChildren(@NotNull Project project) {
        return List.of();
    }

    @Override
    public @NotNull PresentationData createPresentation(@NotNull Project project) {
        PresentationData data = new PresentationData();
        data.setPresentableText(message.lines().findFirst().orElse(message));
        data.setIcon(AllIcons.General.Information);
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

    @Override
    public @NotNull List<AnAction> createToolbarActions(@NotNull Project project) {
        return List.of(GiteaActions.settings());
    }
}
