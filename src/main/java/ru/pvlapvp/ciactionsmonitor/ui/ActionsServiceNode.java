package ru.pvlapvp.ciactionsmonitor.ui;

import ru.pvlapvp.ciactionsmonitor.provider.ActionsApiException;
import com.intellij.execution.services.ServiceViewDescriptor;
import com.intellij.execution.services.ServiceViewProvidingContributor;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.progress.ProcessCanceledException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Base node of the CI/CD service tree.
 * <p>
 * A node is both a service item (shown in the Services tree) and a nested
 * {@link ServiceViewContributor} for its children. The Services API calls
 * {@link #getServices(Project)} on a background invoker, so this is the natural
 * place for blocking HTTP calls and lazy loading.
 * <p>
 * Nodes are compared by their stable id. This lets the Services model preserve
 * expansion/selection across refreshes and lets auto-refresh events target a node
 * object even if the tree created an equivalent new instance.
 */
public abstract class ActionsServiceNode
        implements ServiceViewProvidingContributor<ActionsServiceNode, ActionsServiceNode> {

    private final String id;

    protected ActionsServiceNode(@NotNull String id) {
        this.id = id;
    }

    public final @NotNull String id() {
        return id;
    }

    @Override
    public final @NotNull ActionsServiceNode asService() {
        return this;
    }

    @Override
    public final @NotNull List<ActionsServiceNode> getServices(@NotNull Project project) {
        if (project.isDisposed()) {
            return List.of();
        }
        try {
            List<ActionsServiceNode> children = loadChildren(project);
            if (children == null || children.isEmpty()) {
                return List.of();
            }
            List<ActionsServiceNode> result = new ArrayList<>(children.size());
            for (ActionsServiceNode child : children) {
                if (child != null) {
                    result.add(child);
                }
            }
            return List.copyOf(result);
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (Exception e) {
            return List.of(new ErrorNode(id + "/error", errorMessage(e)));
        }
    }

    /**
     * Loads children. Called on a background thread; implementations may perform HTTP requests.
     */
    protected abstract @NotNull List<ActionsServiceNode> loadChildren(@NotNull Project project) throws Exception;

    @Override
    public final @NotNull ServiceViewDescriptor getServiceDescriptor(@NotNull Project project,
                                                                     @NotNull ActionsServiceNode service) {
        return service.createDescriptor(project);
    }

    @Override
    public final @NotNull ServiceViewDescriptor getViewDescriptor(@NotNull Project project) {
        return createDescriptor(project);
    }

    public final @NotNull ServiceViewDescriptor createDescriptor(@NotNull Project project) {
        return new ActionsNodeDescriptor(project, this);
    }

    /**
     * Builds presentation data without performing network calls.
     */
    public abstract @NotNull PresentationData createPresentation(@NotNull Project project);

    public @Nullable JComponent createContentComponent(@NotNull Project project) {
        return null;
    }

    public void onNodeSelected(@NotNull Project project) {
    }

    public void onNodeUnselected(@NotNull Project project) {
    }

    /**
     * @return {@code true} if the double-click was handled
     */
    public boolean handleDoubleClick(@NotNull Project project) {
        return false;
    }

    public @NotNull List<AnAction> createToolbarActions(@NotNull Project project) {
        return List.of();
    }

    public @Nullable String browserUrl() {
        return null;
    }

    private static @NotNull String errorMessage(@NotNull Throwable throwable) {
        if (throwable instanceof ActionsApiException apiException) {
            return apiException.getMessage();
        }
        if (throwable instanceof IOException && throwable.getMessage() != null) {
            return throwable.getMessage();
        }
        return "Unexpected error: " + throwable.getClass().getSimpleName();
    }

    @Override
    public final boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        return id.equals(((ActionsServiceNode) object).id);
    }

    @Override
    public final int hashCode() {
        return id.hashCode();
    }

    @Override
    public final String toString() {
        return id;
    }
}
