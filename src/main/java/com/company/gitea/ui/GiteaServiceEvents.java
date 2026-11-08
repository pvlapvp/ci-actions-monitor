package com.company.gitea.ui;

import com.intellij.execution.services.ServiceEventListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import org.jetbrains.annotations.NotNull;

/**
 * Sends Services tool window events. The Services model schedules handling on its
 * own background invoker, so these methods are safe to call from any thread.
 */
public final class GiteaServiceEvents {

    private GiteaServiceEvents() {
    }

    public static void fireChildrenChanged(@NotNull Project project, @NotNull GiteaServiceNode node) {
        if (project.isDisposed()) {
            return;
        }
        project.getMessageBus().syncPublisher(ServiceEventListener.TOPIC).handle(
                ServiceEventListener.ServiceEvent.createEvent(
                        ServiceEventListener.EventType.SERVICE_CHILDREN_CHANGED,
                        node,
                        GiteaServiceViewContributor.class
                )
        );
    }

    public static void fireServiceAdded(@NotNull Project project,
                                        @NotNull GiteaServiceNode target,
                                        @NotNull GiteaServiceNode parent) {
        if (project.isDisposed()) {
            return;
        }
        project.getMessageBus().syncPublisher(ServiceEventListener.TOPIC).handle(
                ServiceEventListener.ServiceEvent.createServiceAddedEvent(
                        target,
                        GiteaServiceViewContributor.class,
                        parent
                )
        );
    }

    public static void fireServiceRemoved(@NotNull Project project, @NotNull GiteaServiceNode target) {
        if (project.isDisposed()) {
            return;
        }
        project.getMessageBus().syncPublisher(ServiceEventListener.TOPIC).handle(
                ServiceEventListener.ServiceEvent.createEvent(
                        ServiceEventListener.EventType.SERVICE_REMOVED,
                        target,
                        GiteaServiceViewContributor.class
                )
        );
    }

    public static void fireChanged(@NotNull Project project, @NotNull GiteaServiceNode node) {
        if (project.isDisposed()) {
            return;
        }
        project.getMessageBus().syncPublisher(ServiceEventListener.TOPIC).handle(
                ServiceEventListener.ServiceEvent.createEvent(
                        ServiceEventListener.EventType.SERVICE_CHANGED,
                        node,
                        GiteaServiceViewContributor.class
                )
        );
    }

    public static void fireReset(@NotNull Project project) {
        if (project.isDisposed()) {
            return;
        }
        project.getMessageBus().syncPublisher(ServiceEventListener.TOPIC).handle(
                ServiceEventListener.ServiceEvent.createResetEvent(GiteaServiceViewContributor.class)
        );
    }

    public static void fireResetAll() {
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            fireReset(project);
        }
    }
}
