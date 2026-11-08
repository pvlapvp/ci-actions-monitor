package com.company.gitea.provider.gitea;

import com.company.gitea.api.ActionsApi;
import com.company.gitea.model.ActionsConnection;
import com.company.gitea.model.ProviderType;
import com.company.gitea.model.Repository;
import com.company.gitea.model.Workflow;
import com.company.gitea.model.WorkflowJob;
import com.company.gitea.model.WorkflowRun;
import com.company.gitea.provider.ActionsProvider;
import com.company.gitea.provider.ConnectionInfo;
import com.company.gitea.provider.Page;
import com.company.gitea.provider.ProviderCapabilities;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

/**
 * Gitea implementation backed by the existing {@link ActionsApi}.
 * <p>
 * No Gitea endpoint or request format is changed here: this class only adapts the
 * existing typed API to the provider-neutral contract.
 */
public final class GiteaActionsProvider implements ActionsProvider {

    private static final int MAX_PAGE_SIZE = 50;
    private static final ProviderCapabilities CAPABILITIES = new ProviderCapabilities(MAX_PAGE_SIZE);

    private final ActionsConnection connection;
    private final ActionsApi api;

    public GiteaActionsProvider(@NotNull ActionsConnection connection, @NotNull ActionsApi api) {
        this.connection = connection;
        this.api = api;
    }

    @Override
    public @NotNull ActionsConnection connection() {
        return connection;
    }

    @Override
    public @NotNull ProviderType providerType() {
        return ProviderType.GITEA;
    }

    @Override
    public @NotNull ProviderCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public @NotNull Page<Repository> getRepositories(int page, int pageSize) throws IOException {
        int limit = clampPageSize(pageSize);
        List<Repository> repositories = api.getRepositories(page, limit);
        return new Page<>(repositories, -1, repositories.size() >= limit);
    }

    @Override
    public @NotNull Repository getRepository(@NotNull String fullName) throws IOException {
        return api.getRepository(fullName);
    }

    @Override
    public @NotNull Page<Workflow> getWorkflows(@NotNull Repository repository,
                                                int page,
                                                int pageSize) throws IOException {
        // Gitea returns all workflow definitions without pagination.
        return Page.single(api.getWorkflows(repository));
    }

    @Override
    public @NotNull Page<WorkflowRun> getWorkflowRuns(@NotNull Repository repository,
                                                       @NotNull Workflow workflow,
                                                       int page,
                                                       int pageSize) throws IOException {
        int limit = clampPageSize(pageSize);
        ActionsApi.RunPage runPage = api.getWorkflowRuns(repository, workflow.id(), page, limit);
        return new Page<>(runPage.runs(), runPage.totalCount(), runPage.runs().size() >= limit);
    }

    @Override
    public @NotNull WorkflowRun getWorkflowRun(@NotNull Repository repository,
                                               long runId) throws IOException {
        return api.getWorkflowRun(repository, runId);
    }

    @Override
    public @NotNull Page<WorkflowJob> getRunJobs(@NotNull Repository repository,
                                                  long runId,
                                                  int page,
                                                  int pageSize) throws IOException {
        int limit = clampPageSize(pageSize);
        ActionsApi.JobPage jobPage = api.getRunJobs(repository, runId, page, limit);
        return new Page<>(jobPage.jobs(), jobPage.totalCount(), jobPage.jobs().size() >= limit);
    }

    @Override
    public @NotNull WorkflowJob getJob(@NotNull Repository repository,
                                       long jobId) throws IOException {
        return api.getJob(repository, jobId);
    }

    @Override
    public @NotNull String getJobLogs(@NotNull Repository repository,
                                      long jobId) throws IOException {
        return api.getJobLogs(repository, jobId);
    }

    @Override
    public @NotNull ConnectionInfo testConnection() throws IOException {
        return new ConnectionInfo(
                ProviderType.GITEA,
                api.getServerVersion(),
                api.getClient().getApiBaseUrl(),
                null
        );
    }

    private int clampPageSize(int pageSize) {
        return Math.max(1, Math.min(pageSize, capabilities().maxPageSize()));
    }
}
