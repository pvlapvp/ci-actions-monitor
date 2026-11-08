package com.company.gitea.ui;

import com.company.gitea.model.GiteaServer;
import com.company.gitea.settings.GiteaSettings;
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
 * {@code Server → Repository → Workflow → Run → Job}. Every nested node also
 * implements {@link ServiceViewContributor}, so the Services API performs lazy
 * loading and only calls {@code getServices()} when a node is expanded.
 */
public final class GiteaServiceViewContributor implements ServiceViewContributor<GiteaServiceNode> {

    @Override
    public @NotNull List<GiteaServiceNode> getServices(@NotNull Project project) {
        List<GiteaServer> servers = GiteaSettings.getInstance().getServers();
        List<GiteaServiceNode> nodes = new ArrayList<>(servers.size());
        for (GiteaServer server : servers) {
            nodes.add(new GiteaServerNode(server));
        }
        if (nodes.isEmpty()) {
            nodes.add(new GiteaMessageNode(
                    "gitea:no-servers",
                    "No Gitea servers configured.\n\n"
                            + "Open Settings | Tools | Gitea Actions Monitor and add a server URL and access token."
            ));
        }
        return List.copyOf(nodes);
    }

    @Override
    public @NotNull ServiceViewDescriptor getViewDescriptor(@NotNull Project project) {
        return new SimpleServiceViewDescriptor("Gitea Actions", AllIcons.Nodes.Services, "gitea-actions-root") {
            @Override
            public ActionGroup getToolbarActions() {
                DefaultActionGroup group = new DefaultActionGroup();
                for (AnAction action : List.of(GiteaActions.settings())) {
                    group.add(action);
                }
                return group;
            }
        };
    }

    @Override
    public @NotNull ServiceViewDescriptor getServiceDescriptor(@NotNull Project project,
                                                               @NotNull GiteaServiceNode service) {
        return service.createDescriptor(project);
    }
}
