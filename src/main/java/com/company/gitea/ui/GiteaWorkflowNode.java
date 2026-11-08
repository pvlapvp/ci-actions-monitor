package com.company.gitea.ui;

import com.company.gitea.model.ActionsConnection;
import com.company.gitea.model.Repository;
import com.company.gitea.model.Workflow;
import com.company.gitea.model.WorkflowRun;
import com.company.gitea.service.ActionsMonitorService;
import com.company.gitea.service.GiteaAutoRefreshService;
import com.company.gitea.service.GiteaTreeStateService;
import com.intellij.execution.services.ServiceViewLazyContributor;
import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * A workflow of a repository. Children are runs, loaded lazily and paginated.
 */
public final class GiteaWorkflowNode extends GiteaServiceNode implements ServiceViewLazyContributor {

    private final ActionsConnection connection;
    private final Repository repository;
    private final Workflow workflow;

    public GiteaWorkflowNode(@NotNull ActionsConnection connection,
                             @NotNull Repository repository,
                             @NotNull Workflow workflow) {
        super("workflow:" + connection.id() + ":" + repository.displayName() + ":" + workflow.id());
        this.connection = connection;
        this.repository = repository;
        this.workflow = workflow;
    }

    public @NotNull ActionsConnection connection() {
        return connection;
    }

    public @NotNull Repository repository() {
        return repository;
    }

    public @NotNull Workflow workflow() {
        return workflow;
    }

    @Override
    protected @NotNull List<GiteaServiceNode> loadChildren(@NotNull Project project) throws Exception {
        GiteaTreeStateService stateService = project.getService(GiteaTreeStateService.class);
        int loadedPages = stateService.getPageCount(id());
        ActionsMonitorService.RunList runList = project.getService(ActionsMonitorService.class)
                .loadRuns(connection, repository, workflow, loadedPages);

        List<GiteaServiceNode> children = new ArrayList<>(runList.runs().size() + 1);
        for (WorkflowRun run : runList.runs()) {
            children.add(new GiteaRunNode(connection, repository, workflow, run));
        }
        if (runList.totalCount() > runList.runs().size()) {
            children.add(new GiteaLoadMoreNode(
                    this,
                    "Load more runs (" + runList.runs().size() + " of " + runList.totalCount() + ")",
                    () -> {
                        stateService.incrementPage(id());
                        GiteaServiceEvents.fireChildrenChanged(project, this);
                    }
            ));
        }
        if (runList.runs().isEmpty()) {
            children.add(new GiteaMessageNode(id() + "/empty", "No runs for this workflow."));
        }

        project.getService(GiteaAutoRefreshService.class).registerWorkflow(this, runList.runs());
        return children;
    }

    @Override
    public @NotNull PresentationData createPresentation(@NotNull Project project) {
        PresentationData data = new PresentationData();
        String suffix = workflow.isDisabled() ? " [disabled]" : "";
        data.setPresentableText(workflow.displayName() + suffix);
        data.setLocationString(workflow.path());
        data.setIcon(AllIcons.Actions.ListFiles);
        if (workflow.isDisabled()) {
            data.setForcedTextForeground(com.intellij.ui.JBColor.GRAY);
        }
        data.setTooltip(workflow.path());
        return data;
    }

    @Override
    public @NotNull List<AnAction> createToolbarActions(@NotNull Project project) {
        List<AnAction> actions = new ArrayList<>();
        actions.add(GiteaActions.refresh(project, this));
        AnAction open = GiteaActions.openUrlOrNull(workflow.webUrl(connection, repository));
        if (open != null) {
            actions.add(open);
        }
        actions.add(GiteaActions.settings());
        return actions;
    }

    @Override
    public String browserUrl() {
        return workflow.webUrl(connection, repository);
    }
}
