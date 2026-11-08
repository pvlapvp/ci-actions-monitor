package ru.pvlapvp.ciactionsmonitor.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A leaf node that loads the next page of runs/jobs.
 */
public final class LoadMoreNode extends ActionsServiceNode {

    private final ActionsServiceNode parent;
    private final String text;
    private final Runnable loadMore;
    private final AtomicBoolean triggered = new AtomicBoolean();

    public LoadMoreNode(@NotNull ActionsServiceNode parent,
                             @NotNull String text,
                             @NotNull Runnable loadMore) {
        super(parent.id() + "/more");
        this.parent = parent;
        this.text = text;
        this.loadMore = loadMore;
    }

    @Override
    protected @NotNull List<ActionsServiceNode> loadChildren(@NotNull Project project) {
        return List.of();
    }

    @Override
    public @NotNull PresentationData createPresentation(@NotNull Project project) {
        PresentationData data = new PresentationData();
        data.setPresentableText(text);
        data.setIcon(AllIcons.Actions.More);
        data.setForcedTextForeground(JBColor.GRAY);
        data.setTooltip("Double-click to load the next page");
        return data;
    }

    @Override
    public boolean handleDoubleClick(@NotNull Project project) {
        trigger();
        return true;
    }

    @Override
    public @NotNull List<AnAction> createToolbarActions(@NotNull Project project) {
        return List.of(ActionsToolbar.loadMore(this::trigger));
    }

    private void trigger() {
        if (triggered.compareAndSet(false, true)) {
            loadMore.run();
        }
    }
}
