package ru.pvlapvp.ciactionsmonitor.ui;

import ru.pvlapvp.ciactionsmonitor.model.ActionsConnection;
import ru.pvlapvp.ciactionsmonitor.model.Repository;
import ru.pvlapvp.ciactionsmonitor.service.ActionsMonitorService;
import com.intellij.execution.services.ServiceViewLazyContributor;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Root child: one configured CI/CD connection.
 */
public final class ConnectionNode extends ActionsServiceNode implements ServiceViewLazyContributor {

    private final ActionsConnection connection;

    public ConnectionNode(@NotNull ActionsConnection connection) {
        super("server:" + connection.id());
        this.connection = connection;
    }

    public @NotNull ActionsConnection connection() {
        return connection;
    }

    @Override
    protected @NotNull List<ActionsServiceNode> loadChildren(@NotNull Project project) throws IOException {
        List<Repository> repositories = project.getService(ActionsMonitorService.class).loadRepositories(connection);
        List<ActionsServiceNode> children = new ArrayList<>(repositories.size() + 1);
        for (Repository repository : repositories) {
            children.add(new RepositoryNode(connection, repository));
        }
        if (children.isEmpty()) {
            children.add(new MessageNode(
                    id() + "/empty",
                    "No repositories found. Add one in Settings | Tools | CI Actions Monitor "
                            + "or make sure the token can access repositories."
            ));
        }
        return children;
    }

    @Override
    public @NotNull PresentationData createPresentation(@NotNull Project project) {
        PresentationData data = new PresentationData();
        data.setPresentableText(connection.displayName());
        data.setLocationString(connection.serverUrl());
        data.setIcon(ActionsUi.providerIcon(connection.providerType()));
        data.setTooltip(connection.providerType().displayName() + " • " + connection.serverUrl());
        return data;
    }

    @Override
    public @NotNull List<AnAction> createToolbarActions(@NotNull Project project) {
        List<AnAction> actions = new ArrayList<>();
        actions.add(ActionsToolbar.refresh(project, this));
        AnAction open = ActionsToolbar.openUrlOrNull(connection.serverUrl());
        if (open != null) {
            actions.add(open);
        }
        actions.add(ActionsToolbar.settings());
        return actions;
    }

    @Override
    public String browserUrl() {
        return connection.serverUrl();
    }
}
