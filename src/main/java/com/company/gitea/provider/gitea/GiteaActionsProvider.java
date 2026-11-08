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
import com.company.gitea.provider.InputStreamJobLogStream;
import com.company.gitea.provider.JobLogStream;
import com.company.gitea.provider.Page;
import com.company.gitea.provider.ProviderCapabilities;
import com.company.gitea.provider.gitea.dto.GiteaRepositoryDto;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

/**
 * Gitea implementation backed by the existing {@link ActionsApi}.
 * <p>
 * No Gitea endpoint or request format is changed here: this class only adapts the
 * existing typed API to the provider-neutral contract and maps DTOs to canonical models.
 */
public final class GiteaActionsProvider implements ActionsProvider {

    private static final int MAX_PAGE_SIZE = 50;
    private static final ProviderCapabilities CAPABILITIES = new ProviderCapabilities(MAX_PAGE_SIZE);

    private final ActionsConnection connection;
    private final ActionsApi api;
    private final GiteaDtoMapper mapper;

    public GiteaActionsProvider(@NotNull ActionsConnection connection, @NotNull ActionsApi api) {
        this.connection = connection;
        this.api = api;
        this.mapper = new GiteaDtoMapper(connection);
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
        // Gitea returns all workflow definitions without pagination.
        List<Workflow> workflows = mapper.toWorkflows(api.getWorkflows(toDto(repository)), repository);
        return Page.single(workflows);
    }

    @Override
    public @NotNull Page<WorkflowRun> getWorkflowRuns(@NotNull Repository repository,
                                                       @NotNull Workflow workflow,
                                                       int page,
                                                       int pageSize) throws IOException {
        int limit = clampPageSize(pageSize);
        ActionsApi.RunPage runPage = api.getWorkflowRuns(toDto(repository), workflow.id(), page, limit);
        List<WorkflowRun> runs = mapper.toWorkflowRuns(runPage.runs());
        return new Page<>(runs, runPage.totalCount(), runs.size() >= limit);
    }

    @Override
    public @NotNull WorkflowRun getWorkflowRun(@NotNull Repository repository,
                                               long runId) throws IOException {
        return mapper.toWorkflowRun(api.getWorkflowRun(toDto(repository), runId));
    }

    @Override
    public @NotNull Page<WorkflowJob> getRunJobs(@NotNull Repository repository,
                                                  long runId,
                                                  int page,
                                                  int pageSize) throws IOException {
        int limit = clampPageSize(pageSize);
        ActionsApi.JobPage jobPage = api.getRunJobs(toDto(repository), runId, page, limit);
        List<WorkflowJob> jobs = mapper.toWorkflowJobs(jobPage.jobs());
        return new Page<>(jobs, jobPage.totalCount(), jobs.size() >= limit);
    }

    @Override
    public @NotNull WorkflowJob getJob(@NotNull Repository repository,
                                       long jobId) throws IOException {
        return mapper.toWorkflowJob(api.getJob(toDto(repository), jobId));
    }

    @Override
    public @NotNull JobLogStream openJobLogs(@NotNull Repository repository,
                                             long jobId) throws IOException {
        return new InputStreamJobLogStream(api.openJobLogStream(toDto(repository), jobId));
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

    private static @NotNull GiteaRepositoryDto toDto(@NotNull Repository repository) {
        GiteaRepositoryDto dto = new GiteaRepositoryDto();
        dto.id = repository.id();
        dto.name = repository.name();
        dto.fullName = repository.displayName();
        dto.htmlUrl = repository.htmlUrl();
        dto.defaultBranch = repository.defaultBranch();
        dto.privateRepository = repository.privateRepository();
        if (repository.owner() != null) {
            GiteaRepositoryDto.Owner owner = new GiteaRepositoryDto.Owner();
            owner.login = repository.owner().login();
            dto.owner = owner;
        }
        return dto;
    }
}
