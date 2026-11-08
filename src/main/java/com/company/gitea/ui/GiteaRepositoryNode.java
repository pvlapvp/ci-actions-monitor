package com.company.gitea.ui;

import com.company.gitea.model.ActionsConnection;
import com.company.gitea.model.Repository;
import com.company.gitea.model.Workflow;
import com.company.gitea.service.ActionsMonitorService;
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
public final class GiteaRepositoryNode extends GiteaServiceNode implements ServiceViewLazyContributor {

    private final ActionsConnection connection;
    private final Repository repository;

    public GiteaRepositoryNode(@NotNull ActionsConnection connection, @NotNull Repository repository) {
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
    protected @NotNull List<GiteaServiceNode> loadChildren(@NotNull Project project) throws Exception {
        List<Workflow> workflows = project.getService(ActionsMonitorService.class)
                .loadWorkflows(connection, repository);
        List<GiteaServiceNode> children = new ArrayList<>(workflows.size() + 1);
        for (Workflow workflow : workflows) {
            children.add(new GiteaWorkflowNode(connection, repository, workflow));
        }
        if (children.isEmpty()) {
            children.add(new GiteaMessageNode(
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
        actions.add(GiteaActions.refresh(project, this));
        AnAction open = GiteaActions.openUrlOrNull(repository.webUrl(connection));
        if (open != null) {
            actions.add(open);
        }
        actions.add(GiteaActions.settings());
        return actions;
    }

    @Override
    public String browserUrl() {
        return repository.webUrl(connection);
    }
}
