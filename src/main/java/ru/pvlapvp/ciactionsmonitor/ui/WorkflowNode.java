package ru.pvlapvp.ciactionsmonitor.ui;

import ru.pvlapvp.ciactionsmonitor.model.ActionsConnection;
import ru.pvlapvp.ciactionsmonitor.model.Repository;
import ru.pvlapvp.ciactionsmonitor.model.Workflow;
import ru.pvlapvp.ciactionsmonitor.model.WorkflowRun;
import ru.pvlapvp.ciactionsmonitor.service.ActionsMonitorService;
import ru.pvlapvp.ciactionsmonitor.service.AutoRefreshService;
import ru.pvlapvp.ciactionsmonitor.service.TreeStateService;
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
public final class WorkflowNode extends ActionsServiceNode implements ServiceViewLazyContributor {

    private final ActionsConnection connection;
    private final Repository repository;
    private final Workflow workflow;

    public WorkflowNode(@NotNull ActionsConnection connection,
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
    protected @NotNull List<ActionsServiceNode> loadChildren(@NotNull Project project) throws Exception {
        TreeStateService stateService = project.getService(TreeStateService.class);
        int loadedPages = stateService.getPageCount(id());
        ActionsMonitorService.RunList runList = project.getService(ActionsMonitorService.class)
                .loadRuns(connection, repository, workflow, loadedPages);

        List<ActionsServiceNode> children = new ArrayList<>(runList.runs().size() + 1);
        for (WorkflowRun run : runList.runs()) {
            children.add(new RunNode(connection, repository, workflow, run));
        }
        if (runList.totalCount() > runList.runs().size()) {
            children.add(new LoadMoreNode(
                    this,
                    "Load more runs (" + runList.runs().size() + " of " + runList.totalCount() + ")",
                    () -> {
                        stateService.incrementPage(id());
                        ActionsServiceEvents.fireChildrenChanged(project, this);
                    }
            ));
        }
        if (runList.runs().isEmpty()) {
            children.add(new MessageNode(id() + "/empty", "No runs for this workflow."));
        }

        project.getService(AutoRefreshService.class).registerWorkflow(this, runList.runs());
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
        actions.add(ActionsToolbar.refresh(project, this));
        AnAction open = ActionsToolbar.openUrlOrNull(workflow.htmlUrl());
        if (open != null) {
            actions.add(open);
        }
        actions.add(ActionsToolbar.settings());
        return actions;
    }

    @Override
    public String browserUrl() {
        return workflow.htmlUrl();
    }
}
