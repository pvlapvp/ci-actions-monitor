package com.company.gitea.ui;

import com.company.gitea.service.LogsService;
import com.company.gitea.settings.GiteaSettingsConfigurable;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Action factory used by Services node toolbars.
 */
final class GiteaActions {

    private GiteaActions() {
    }

    static @NotNull AnAction refresh(@NotNull Project project, @NotNull GiteaServiceNode node) {
        return new AnAction("Refresh", "Reload children from Gitea", AllIcons.Actions.Refresh) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent event) {
                GiteaServiceEvents.fireChildrenChanged(project, node);
            }
        };
    }

    static @NotNull AnAction openUrl(@NotNull String url) {
        return new AnAction("Open in Gitea", "Open in the default browser", AllIcons.General.Web) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent event) {
                com.intellij.ide.BrowserUtil.browse(url);
            }
        };
    }

    static @NotNull AnAction settings() {
        return new AnAction("Settings", "Open Gitea Actions Monitor settings", AllIcons.General.Settings) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent event) {
                Project project = event.getProject();
                if (project == null || project.isDisposed()) {
                    project = ProjectManager.getInstance().getDefaultProject();
                }
                ShowSettingsUtil.getInstance().showSettingsDialog(project, GiteaSettingsConfigurable.class);
            }
        };
    }

    static @NotNull AnAction loadMore(@NotNull Runnable action) {
        return new AnAction("Load More", "Load the next page", AllIcons.Actions.More) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent event) {
                action.run();
            }
        };
    }

    static @NotNull AnAction reloadLogs(@NotNull Project project) {
        return new AnAction("Reload Logs", "Reload the selected job log", AllIcons.Actions.Refresh) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent event) {
                project.getService(LogsService.class).reloadCurrent();
            }
        };
    }

    static @Nullable AnAction openUrlOrNull(@Nullable String url) {
        return url == null || url.isBlank() ? null : openUrl(url);
    }
}
