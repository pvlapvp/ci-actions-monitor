package com.company.gitea.ui;

import com.company.gitea.model.GiteaServer;
import com.company.gitea.model.Repository;
import com.company.gitea.service.ActionsMonitorService;
import com.intellij.execution.services.ServiceViewLazyContributor;
import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Root child: one configured Gitea server.
 */
public final class GiteaServerNode extends GiteaServiceNode implements ServiceViewLazyContributor {

    private final GiteaServer server;

    public GiteaServerNode(@NotNull GiteaServer server) {
        super("server:" + server.id());
        this.server = server;
    }

    public @NotNull GiteaServer server() {
        return server;
    }

    @Override
    protected @NotNull List<GiteaServiceNode> loadChildren(@NotNull Project project) throws IOException {
        List<Repository> repositories = project.getService(ActionsMonitorService.class).loadRepositories(server);
        List<GiteaServiceNode> children = new ArrayList<>(repositories.size() + 1);
        for (Repository repository : repositories) {
            children.add(new GiteaRepositoryNode(server, repository));
        }
        if (children.isEmpty()) {
            children.add(new GiteaMessageNode(
                    id() + "/empty",
                    "No repositories found. Add one in Settings | Tools | Gitea Actions Monitor "
                            + "or make sure the token has read:repository permission."
            ));
        }
        return children;
    }

    @Override
    public @NotNull PresentationData createPresentation(@NotNull Project project) {
        PresentationData data = new PresentationData();
        data.setPresentableText(server.displayName());
        data.setLocationString(server.normalizedUrl());
        data.setIcon(AllIcons.Nodes.Services);
        data.setTooltip(server.normalizedUrl());
        return data;
    }

    @Override
    public @NotNull List<AnAction> createToolbarActions(@NotNull Project project) {
        List<AnAction> actions = new ArrayList<>();
        actions.add(GiteaActions.refresh(project, this));
        AnAction open = GiteaActions.openUrlOrNull(server.normalizedUrl());
        if (open != null) {
            actions.add(open);
        }
        actions.add(GiteaActions.settings());
        return actions;
    }

    @Override
    public String browserUrl() {
        return server.normalizedUrl();
    }
}
