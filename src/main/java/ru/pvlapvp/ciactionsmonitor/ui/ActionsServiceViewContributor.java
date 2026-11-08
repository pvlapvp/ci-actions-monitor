package ru.pvlapvp.ciactionsmonitor.ui;

import ru.pvlapvp.ciactionsmonitor.model.ActionsConnection;
import ru.pvlapvp.ciactionsmonitor.settings.ActionsSettings;
import com.intellij.execution.services.ServiceViewContributor;
import com.intellij.execution.services.ServiceViewDescriptor;
import com.intellij.execution.services.SimpleServiceViewDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Root contributor for the IntelliJ Services tool window.
 * <p>
 * Registered via {@code com.intellij.serviceViewContributor}. The tree is:
 * {@code Connection → Repository → Workflow → Run → Job}. Every nested node also
 * implements {@link ServiceViewContributor}, so the Services API performs lazy
 * loading and only calls {@code getServices()} when a node is expanded.
 */
public final class ActionsServiceViewContributor implements ServiceViewContributor<ActionsServiceNode> {

    @Override
    public @NotNull List<ActionsServiceNode> getServices(@NotNull Project project) {
        List<ActionsConnection> connections = ActionsSettings.getInstance().getConnections();
        List<ActionsServiceNode> nodes = new ArrayList<>(connections.size());
        for (ActionsConnection connection : connections) {
            nodes.add(new ConnectionNode(connection));
        }
        if (nodes.isEmpty()) {
            nodes.add(new MessageNode(
                    "ci-actions:no-connections",
                    "No CI/CD connections configured.\n\n"
                            + "Open Settings | Tools | CI Actions Monitor and add a server URL and access token."
            ));
        }
        return List.copyOf(nodes);
    }

    @Override
    public @NotNull ServiceViewDescriptor getViewDescriptor(@NotNull Project project) {
        return new SimpleServiceViewDescriptor("CI Actions", AllIcons.Nodes.Services, "ci-actions-root") {
            @Override
            public ActionGroup getToolbarActions() {
                DefaultActionGroup group = new DefaultActionGroup();
                for (AnAction action : List.of(ActionsToolbar.settings())) {
                    group.add(action);
                }
                return group;
            }
        };
    }

    @Override
    public @NotNull ServiceViewDescriptor getServiceDescriptor(@NotNull Project project,
                                                               @NotNull ActionsServiceNode service) {
        return service.createDescriptor(project);
    }
}
