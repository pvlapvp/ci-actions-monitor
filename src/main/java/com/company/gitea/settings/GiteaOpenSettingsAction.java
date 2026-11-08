package com.company.gitea.settings;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import org.jetbrains.annotations.NotNull;

/**
 * Tools menu entry that opens the Gitea settings page.
 */
public final class GiteaOpenSettingsAction extends AnAction {

    public GiteaOpenSettingsAction() {
        super("Gitea Actions Monitor Settings...");
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            project = ProjectManager.getInstance().getDefaultProject();
        }
        ShowSettingsUtil.getInstance().showSettingsDialog(project, GiteaSettingsConfigurable.class);
    }
}
