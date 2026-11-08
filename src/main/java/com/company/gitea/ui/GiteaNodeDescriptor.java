package com.company.gitea.ui;

import com.intellij.execution.services.ServiceViewDescriptor;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Descriptor that maps a {@link GiteaServiceNode} to the Services tool window.
 */
final class GiteaNodeDescriptor implements ServiceViewDescriptor {

    private final Project project;
    private final GiteaServiceNode node;

    GiteaNodeDescriptor(@NotNull Project project, @NotNull GiteaServiceNode node) {
        this.project = project;
        this.node = node;
    }

    @Override
    public @NotNull ItemPresentation getPresentation() {
        PresentationData presentation = node.createPresentation(project);
        return presentation;
    }

    @Override
    public @Nullable JComponent getContentComponent() {
        return node.createContentComponent(project);
    }

    @Override
    public @NotNull String getId() {
        return node.id();
    }

    @Override
    public void onNodeSelected(List<Object> selectedServices) {
        node.onNodeSelected(project);
    }

    @Override
    public void onNodeUnselected() {
        node.onNodeUnselected(project);
    }

    @Override
    public @Nullable ActionGroup getToolbarActions() {
        List<AnAction> actions = node.createToolbarActions(project);
        if (actions.isEmpty()) {
            return null;
        }
        DefaultActionGroup group = new DefaultActionGroup();
        for (AnAction action : actions) {
            group.add(action);
        }
        return group;
    }

    @Override
    public boolean handleDoubleClick(@NotNull MouseEvent event) {
        return node.handleDoubleClick(project);
    }
}
