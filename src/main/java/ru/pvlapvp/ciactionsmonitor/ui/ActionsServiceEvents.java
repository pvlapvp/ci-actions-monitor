package ru.pvlapvp.ciactionsmonitor.ui;

import com.intellij.execution.services.ServiceEventListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import org.jetbrains.annotations.NotNull;

/**
 * Sends Services tool window events. The Services model schedules handling on its
 * own background invoker, so these methods are safe to call from any thread.
 */
public final class ActionsServiceEvents {

    private ActionsServiceEvents() {
    }

    public static void fireChildrenChanged(@NotNull Project project, @NotNull ActionsServiceNode node) {
        if (project.isDisposed()) {
            return;
        }
        project.getMessageBus().syncPublisher(ServiceEventListener.TOPIC).handle(
                ServiceEventListener.ServiceEvent.createEvent(
                        ServiceEventListener.EventType.SERVICE_CHILDREN_CHANGED,
                        node,
                        ActionsServiceViewContributor.class
                )
        );
    }

    public static void fireServiceAdded(@NotNull Project project,
                                        @NotNull ActionsServiceNode target,
                                        @NotNull ActionsServiceNode parent) {
        if (project.isDisposed()) {
            return;
        }
        project.getMessageBus().syncPublisher(ServiceEventListener.TOPIC).handle(
                ServiceEventListener.ServiceEvent.createServiceAddedEvent(
                        target,
                        ActionsServiceViewContributor.class,
                        parent
                )
        );
    }

    public static void fireServiceRemoved(@NotNull Project project, @NotNull ActionsServiceNode target) {
        if (project.isDisposed()) {
            return;
        }
        project.getMessageBus().syncPublisher(ServiceEventListener.TOPIC).handle(
                ServiceEventListener.ServiceEvent.createEvent(
                        ServiceEventListener.EventType.SERVICE_REMOVED,
                        target,
                        ActionsServiceViewContributor.class
                )
        );
    }

    public static void fireChanged(@NotNull Project project, @NotNull ActionsServiceNode node) {
        if (project.isDisposed()) {
            return;
        }
        project.getMessageBus().syncPublisher(ServiceEventListener.TOPIC).handle(
                ServiceEventListener.ServiceEvent.createEvent(
                        ServiceEventListener.EventType.SERVICE_CHANGED,
                        node,
                        ActionsServiceViewContributor.class
                )
        );
    }

    public static void fireReset(@NotNull Project project) {
        if (project.isDisposed()) {
            return;
        }
        project.getMessageBus().syncPublisher(ServiceEventListener.TOPIC).handle(
                ServiceEventListener.ServiceEvent.createResetEvent(ActionsServiceViewContributor.class)
        );
    }

    public static void fireResetAll() {
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            fireReset(project);
        }
    }
}
