package com.company.gitea.ui;

import com.company.gitea.model.GiteaServer;
import com.company.gitea.model.GiteaStatus;
import com.company.gitea.model.Repository;
import com.company.gitea.model.Workflow;
import com.company.gitea.model.WorkflowJob;
import com.company.gitea.model.WorkflowRun;
import com.company.gitea.service.ActionsMonitorService;
import com.company.gitea.service.GiteaAutoRefreshService;
import com.company.gitea.service.GiteaTreeStateService;
import com.intellij.execution.services.ServiceViewLazyContributor;
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
import java.util.ArrayList;
import java.util.List;

/**
 * One workflow run. Children are jobs.
 */
public final class GiteaRunNode extends GiteaServiceNode implements ServiceViewLazyContributor {

    private final GiteaServer server;
    private final Repository repository;
    private final Workflow workflow;
    private final WorkflowRun run;

    public GiteaRunNode(@NotNull GiteaServer server,
                        @NotNull Repository repository,
                        @NotNull Workflow workflow,
                        @NotNull WorkflowRun run) {
        super("run:" + server.id() + ":" + repository.displayName() + ":" + run.id());
        this.server = server;
        this.repository = repository;
        this.workflow = workflow;
        this.run = run;
    }

    public @NotNull GiteaServer server() {
        return server;
    }

    public @NotNull Repository repository() {
        return repository;
    }

    public @NotNull Workflow workflow() {
        return workflow;
    }

    public @NotNull WorkflowRun run() {
        return run;
    }

    @Override
    protected @NotNull List<GiteaServiceNode> loadChildren(@NotNull Project project) throws Exception {
        GiteaTreeStateService stateService = project.getService(GiteaTreeStateService.class);
        int loadedPages = stateService.getPageCount(id());
        ActionsMonitorService.JobList jobList = project.getService(ActionsMonitorService.class)
                .loadJobs(server, repository, run, loadedPages);

        List<GiteaServiceNode> children = new ArrayList<>(jobList.jobs().size() + 1);
        for (WorkflowJob job : jobList.jobs()) {
            children.add(new GiteaJobNode(server, repository, job));
        }
        if (jobList.totalCount() > jobList.jobs().size()) {
            children.add(new GiteaLoadMoreNode(
                    this,
                    "Load more jobs (" + jobList.jobs().size() + " of " + jobList.totalCount() + ")",
                    () -> {
                        stateService.incrementPage(id());
                        GiteaServiceEvents.fireChildrenChanged(project, this);
                    }
            ));
        }
        if (jobList.jobs().isEmpty()) {
            children.add(new GiteaMessageNode(id() + "/empty", "No jobs for this run."));
        }

        project.getService(GiteaAutoRefreshService.class).registerRun(this, jobList.jobs());
        return children;
    }

    @Override
    public @NotNull PresentationData createPresentation(@NotNull Project project) {
        GiteaStatus status = run.state();
        PresentationData data = new PresentationData();
        data.setPresentableText(run.displayName() + " [" + status.displayName() + "]");
        data.setLocationString(buildLocationText());
        data.setIcon(GiteaUi.statusIcon(status));
        data.setForcedTextForeground(GiteaUi.statusColor(status));
        data.setTooltip(buildTooltip());
        return data;
    }

    @Override
    public @Nullable JComponent createContentComponent(@NotNull Project project) {
        StringBuilder text = new StringBuilder();
        text.append("Server:      ").append(server.displayName()).append('\n');
        text.append("Repository:  ").append(repository.displayName()).append('\n');
        text.append("Workflow:    ").append(workflow.displayName()).append('\n');
        text.append("Run:         ").append(run.displayName()).append('\n');
        text.append("Status:      ").append(run.state().displayName()).append('\n');
        if (run.event() != null && !run.event().isBlank()) {
            text.append("Event:       ").append(run.event()).append('\n');
        }
        if (!run.branchText().isEmpty()) {
            text.append("Branch:      ").append(run.branchText()).append('\n');
        }
        if (run.headSha() != null && !run.headSha().isBlank()) {
            text.append("Commit:      ").append(run.headSha()).append('\n');
        }
        if (run.actorLogin() != null) {
            text.append("Actor:       ").append(run.actorLogin()).append('\n');
        }
        if (run.hasStarted()) {
            text.append("Started:     ").append(run.startedAt()).append('\n');
        }
        if (run.completedAt() != null && !run.completedAt().isBlank()
                && !run.completedAt().startsWith("0001-01-01")) {
            text.append("Completed:   ").append(run.completedAt()).append('\n');
        }
        if (run.webUrl() != null) {
            text.append("URL:         ").append(run.webUrl()).append('\n');
        }
        text.append('\n').append("Select a job to read its log.");

        JTextArea area = new JTextArea(text.toString());
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
        List<AnAction> actions = new ArrayList<>();
        actions.add(GiteaActions.refresh(project, this));
        AnAction open = GiteaActions.openUrlOrNull(run.webUrl());
        if (open != null) {
            actions.add(open);
        }
        actions.add(GiteaActions.settings());
        return actions;
    }

    @Override
    public String browserUrl() {
        return run.webUrl();
    }

    private @NotNull String buildLocationText() {
        List<String> parts = new ArrayList<>(2);
        if (!run.branchText().isEmpty()) {
            parts.add(run.branchText());
        }
        if (run.event() != null && !run.event().isBlank()) {
            parts.add(run.event());
        }
        return String.join(" • ", parts);
    }

    private @NotNull String buildTooltip() {
        StringBuilder tooltip = new StringBuilder();
        tooltip.append("Run #").append(run.runNumber()).append(", attempt ").append(run.runAttempt());
        if (run.headSha() != null && !run.headSha().isBlank()) {
            tooltip.append(", commit ").append(run.headSha());
        }
        if (run.webUrl() != null) {
            tooltip.append('\n').append(run.webUrl());
        }
        return tooltip.toString();
    }
}
