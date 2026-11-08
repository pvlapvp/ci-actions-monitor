package ru.pvlapvp.ciactionsmonitor.provider.gitea;

import ru.pvlapvp.ciactionsmonitor.api.GiteaActionsApi;
import ru.pvlapvp.ciactionsmonitor.model.ActionsConnection;
import ru.pvlapvp.ciactionsmonitor.model.ProviderType;
import ru.pvlapvp.ciactionsmonitor.model.Repository;
import ru.pvlapvp.ciactionsmonitor.model.Workflow;
import ru.pvlapvp.ciactionsmonitor.model.WorkflowJob;
import ru.pvlapvp.ciactionsmonitor.model.WorkflowRun;
import ru.pvlapvp.ciactionsmonitor.provider.ActionsProvider;
import ru.pvlapvp.ciactionsmonitor.provider.ConnectionInfo;
import ru.pvlapvp.ciactionsmonitor.provider.InputStreamJobLogStream;
import ru.pvlapvp.ciactionsmonitor.provider.JobLogStream;
import ru.pvlapvp.ciactionsmonitor.provider.Page;
import ru.pvlapvp.ciactionsmonitor.provider.ProviderCapabilities;
import ru.pvlapvp.ciactionsmonitor.provider.gitea.dto.GiteaRepositoryDto;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

/**
 * Gitea implementation backed by the existing {@link GiteaActionsApi}.
 * <p>
 * No Gitea endpoint or request format is changed here: this class only adapts the
 * existing typed API to the provider-neutral contract and maps DTOs to canonical models.
 */
public final class GiteaActionsProvider implements ActionsProvider {

    private static final int MAX_PAGE_SIZE = 50;
    private static final ProviderCapabilities CAPABILITIES = new ProviderCapabilities(MAX_PAGE_SIZE);

    private final ActionsConnection connection;
    private final GiteaActionsApi api;
    private final GiteaDtoMapper mapper;

    public GiteaActionsProvider(@NotNull ActionsConnection connection, @NotNull GiteaActionsApi api) {
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
        GiteaActionsApi.RunPage runPage = api.getWorkflowRuns(toDto(repository), workflow.id(), page, limit);
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
        GiteaActionsApi.JobPage jobPage = api.getRunJobs(toDto(repository), runId, page, limit);
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
                api.getCurrentUserLogin()
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
