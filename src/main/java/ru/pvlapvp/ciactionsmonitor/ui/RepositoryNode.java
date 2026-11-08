package ru.pvlapvp.ciactionsmonitor.ui;

import ru.pvlapvp.ciactionsmonitor.model.ActionsConnection;
import ru.pvlapvp.ciactionsmonitor.model.Repository;
import ru.pvlapvp.ciactionsmonitor.model.Workflow;
import ru.pvlapvp.ciactionsmonitor.service.ActionsMonitorService;
import com.intellij.execution.services.ServiceViewLazyContributor;
import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * A repository configured for a connection.
 */
public final class RepositoryNode extends ActionsServiceNode implements ServiceViewLazyContributor {

    private final ActionsConnection connection;
    private final Repository repository;

    public RepositoryNode(@NotNull ActionsConnection connection, @NotNull Repository repository) {
        super("repo:" + connection.id() + ":" + repository.displayName());
        this.connection = connection;
        this.repository = repository;
    }

    public @NotNull ActionsConnection connection() {
        return connection;
    }

    public @NotNull Repository repository() {
        return repository;
    }

    @Override
    protected @NotNull List<ActionsServiceNode> loadChildren(@NotNull Project project) throws Exception {
        List<Workflow> workflows = project.getService(ActionsMonitorService.class)
                .loadWorkflows(connection, repository);
        List<ActionsServiceNode> children = new ArrayList<>(workflows.size() + 1);
        for (Workflow workflow : workflows) {
            children.add(new WorkflowNode(connection, repository, workflow));
        }
        if (children.isEmpty()) {
            children.add(new MessageNode(
                    id() + "/empty",
                    "No workflows found in .gitea/workflows or .github/workflows for this repository."
            ));
        }
        return children;
    }

    @Override
    public @NotNull PresentationData createPresentation(@NotNull Project project) {
        PresentationData data = new PresentationData();
        data.setPresentableText(repository.displayName());
        data.setLocationString(connection.displayName());
        data.setIcon(AllIcons.Nodes.Folder);
        String webUrl = repository.webUrl(connection);
        if (webUrl != null) {
            data.setTooltip(webUrl);
        }
        return data;
    }

    @Override
    public @NotNull List<AnAction> createToolbarActions(@NotNull Project project) {
        List<AnAction> actions = new ArrayList<>();
        actions.add(ActionsToolbar.refresh(project, this));
        AnAction open = ActionsToolbar.openUrlOrNull(repository.webUrl(connection));
        if (open != null) {
            actions.add(open);
        }
        actions.add(ActionsToolbar.settings());
        return actions;
    }

    @Override
    public String browserUrl() {
        return repository.webUrl(connection);
    }
}
