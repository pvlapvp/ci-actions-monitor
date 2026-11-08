package ru.pvlapvp.ciactionsmonitor.settings;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import org.jetbrains.annotations.NotNull;

/**
 * Tools menu entry that opens the CI Actions settings page.
 */
public final class OpenSettingsAction extends AnAction {

    public OpenSettingsAction() {
        super("CI Actions Monitor Settings...");
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            project = ProjectManager.getInstance().getDefaultProject();
        }
        ShowSettingsUtil.getInstance().showSettingsDialog(project, ActionsSettingsConfigurable.class);
    }
}
