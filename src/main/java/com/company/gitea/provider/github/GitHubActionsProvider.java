package com.company.gitea.provider.github;

import com.company.gitea.model.ActionsConnection;
import com.company.gitea.model.ProviderType;
import com.company.gitea.model.Repository;
import com.company.gitea.model.Workflow;
import com.company.gitea.model.WorkflowJob;
import com.company.gitea.model.WorkflowRun;
import com.company.gitea.provider.ActionsProvider;
import com.company.gitea.provider.ConnectionInfo;
import com.company.gitea.provider.JobLogStream;
import com.company.gitea.provider.Page;
import com.company.gitea.provider.ProviderCapabilities;
import com.company.gitea.provider.github.dto.GitHubWorkflowJobsResponse;
import com.company.gitea.provider.github.dto.GitHubWorkflowRunsResponse;
import com.company.gitea.provider.github.dto.GitHubWorkflowsResponse;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

/**
 * GitHub implementation of {@link ActionsProvider}. Works with GitHub.com and GHES
 * through a resolved {@link GitHubEndpoint}.
 */
public final class GitHubActionsProvider implements ActionsProvider {

    private static final int MAX_PAGE_SIZE = 100;
    private static final ProviderCapabilities CAPABILITIES = new ProviderCapabilities(MAX_PAGE_SIZE);

    private final ActionsConnection connection;
    private final GitHubActionsApi api;
    private final GitHubDtoMapper mapper;

    public GitHubActionsProvider(@NotNull ActionsConnection connection,
                                 @NotNull GitHubActionsApi api) {
        this.connection = connection;
        this.api = api;
        this.mapper = new GitHubDtoMapper(connection);
    }

    @Override
    public @NotNull ActionsConnection connection() {
        return connection;
    }

    @Override
    public @NotNull ProviderType providerType() {
        return ProviderType.GITHUB;
    }

    @Override
    public @NotNull ProviderCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public @NotNull Page<Repository> getRepositories(int page, int pageSize) throws IOException {
        int limit = clampPageSize(pageSize);
        List<Repository> repositories = mapper.toRepositories(api.getRepositories(page, limit));
        return new Page<>(repositories, -1, repositories.size() >= limit);
    }

    @Override
    public @NotNull Repository getRepository(@NotNull String fullName) throws IOException {
        return mapper.toRepository(api.getRepository(fullName));
    }

    @Override
    public @NotNull Page<Workflow> getWorkflows(@NotNull Repository repository,
                                                int page,
                                                int pageSize) throws IOException {
        int limit = clampPageSize(pageSize);
        GitHubWorkflowsResponse response = api.getWorkflows(repository.displayName(), page, limit);
        List<Workflow> workflows = mapper.toWorkflows(response.workflows, repository);
        return new Page<>(workflows, response.totalCount, hasMore(workflows.size(), response.totalCount, page, limit));
    }

    @Override
    public @NotNull Page<WorkflowRun> getWorkflowRuns(@NotNull Repository repository,
                                                       @NotNull Workflow workflow,
                                                       int page,
                                                       int pageSize) throws IOException {
        int limit = clampPageSize(pageSize);
        GitHubWorkflowRunsResponse response = api.getWorkflowRuns(
                repository.displayName(),
                workflow.id(),
                page,
                limit
        );
        List<WorkflowRun> runs = mapper.toWorkflowRuns(response.workflowRuns);
        return new Page<>(runs, response.totalCount, hasMore(runs.size(), response.totalCount, page, limit));
    }

    @Override
    public @NotNull WorkflowRun getWorkflowRun(@NotNull Repository repository,
                                               long runId) throws IOException {
        return mapper.toWorkflowRun(api.getWorkflowRun(repository.displayName(), runId));
    }

    @Override
    public @NotNull Page<WorkflowJob> getRunJobs(@NotNull Repository repository,
                                                  long runId,
                                                  int page,
                                                  int pageSize) throws IOException {
        int limit = clampPageSize(pageSize);
        GitHubWorkflowJobsResponse response = api.getRunJobs(repository.displayName(), runId, page, limit);
        List<WorkflowJob> jobs = mapper.toWorkflowJobs(response.jobs);
        return new Page<>(jobs, response.totalCount, hasMore(jobs.size(), response.totalCount, page, limit));
    }

    @Override
    public @NotNull WorkflowJob getJob(@NotNull Repository repository,
                                       long jobId) throws IOException {
        return mapper.toWorkflowJob(api.getJob(repository.displayName(), jobId));
    }

    @Override
    public @NotNull JobLogStream openJobLogs(@NotNull Repository repository,
                                             long jobId) throws IOException {
        return api.openJobLogStream(repository.displayName(), jobId);
    }

    @Override
    public @NotNull ConnectionInfo testConnection() throws IOException {
        return api.testConnection();
    }

    private int clampPageSize(int pageSize) {
        return Math.max(1, Math.min(pageSize, capabilities().maxPageSize()));
    }

    private static boolean hasMore(int itemCount, int totalCount, int page, int pageSize) {
        if (itemCount < pageSize) {
            return false;
        }
        if (totalCount <= 0) {
            return true;
        }
        return (long) page * pageSize < totalCount;
    }
}
