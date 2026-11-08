package com.company.gitea.ui;

import com.company.gitea.model.ActionsConnection;
import com.company.gitea.model.GiteaStatus;
import com.company.gitea.model.Repository;
import com.company.gitea.model.WorkflowJob;
import com.company.gitea.service.LogsService;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.util.ArrayList;
import java.util.List;

/**
 * A job of a run. Selecting the node opens the shared log panel and starts
 * log polling while the job is active.
 */
public final class GiteaJobNode extends GiteaServiceNode {

    private final ActionsConnection connection;
    private final Repository repository;
    private final WorkflowJob job;

    public GiteaJobNode(@NotNull ActionsConnection connection,
                        @NotNull Repository repository,
                        @NotNull WorkflowJob job) {
        super("job:" + connection.id() + ":" + repository.displayName() + ":" + job.runId() + ":" + job.id());
        this.connection = connection;
        this.repository = repository;
        this.job = job;
    }

    public @NotNull WorkflowJob job() {
        return job;
    }

    @Override
    protected @NotNull List<GiteaServiceNode> loadChildren(@NotNull Project project) {
        return List.of();
    }

    @Override
    public @NotNull PresentationData createPresentation(@NotNull Project project) {
        GiteaStatus status = job.state();
        PresentationData data = new PresentationData();
        data.setPresentableText(job.displayName() + " [" + status.displayName() + "]");
        String location = job.labelsText();
        if (!location.isBlank()) {
            data.setLocationString(location);
        }
        data.setIcon(GiteaUi.statusIcon(status));
        data.setForcedTextForeground(GiteaUi.statusColor(status));
        data.setTooltip(buildTooltip());
        return data;
    }

    @Override
    public @Nullable JComponent createContentComponent(@NotNull Project project) {
        return project.getService(LogsService.class).getPanel();
    }

    @Override
    public void onNodeSelected(@NotNull Project project) {
        project.getService(LogsService.class).selectJob(connection, repository, job);
    }

    @Override
    public void onNodeUnselected(@NotNull Project project) {
        project.getService(LogsService.class).deselect(job.id());
    }

    @Override
    public @NotNull List<AnAction> createToolbarActions(@NotNull Project project) {
        List<AnAction> actions = new ArrayList<>();
        actions.add(GiteaActions.reloadLogs(project));
        AnAction open = GiteaActions.openUrlOrNull(job.webUrl());
        if (open != null) {
            actions.add(open);
        }
        actions.add(GiteaActions.settings());
        return actions;
    }

    @Override
    public String browserUrl() {
        return job.webUrl();
    }

    private @NotNull String buildTooltip() {
        StringBuilder tooltip = new StringBuilder();
        tooltip.append("Job #").append(job.id());
        if (!job.runnerText().isEmpty()) {
            tooltip.append(", runner ").append(job.runnerText());
        }
        if (job.webUrl() != null) {
            tooltip.append('\n').append(job.webUrl());
        }
        return tooltip.toString();
    }
}
