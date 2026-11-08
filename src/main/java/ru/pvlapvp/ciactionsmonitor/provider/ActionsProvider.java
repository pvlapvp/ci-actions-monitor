package ru.pvlapvp.ciactionsmonitor.provider;

import ru.pvlapvp.ciactionsmonitor.model.ActionsConnection;
import ru.pvlapvp.ciactionsmonitor.model.ProviderType;
import ru.pvlapvp.ciactionsmonitor.model.Repository;
import ru.pvlapvp.ciactionsmonitor.model.Workflow;
import ru.pvlapvp.ciactionsmonitor.model.WorkflowJob;
import ru.pvlapvp.ciactionsmonitor.model.WorkflowRun;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Provider-neutral read-only API for a CI/CD platform.
 * <p>
 * All methods perform blocking network calls and must be invoked from a background
 * thread. Implementations must never expose access tokens in messages or logs.
 */
public interface ActionsProvider {

    @NotNull ActionsConnection connection();

    @NotNull ProviderType providerType();

    @NotNull ProviderCapabilities capabilities();

    @NotNull Page<Repository> getRepositories(int page, int pageSize) throws IOException;

    @NotNull Repository getRepository(@NotNull String fullName) throws IOException;

    @NotNull Page<Workflow> getWorkflows(@NotNull Repository repository,
                                         int page,
                                         int pageSize) throws IOException;

    @NotNull Page<WorkflowRun> getWorkflowRuns(@NotNull Repository repository,
                                                @NotNull Workflow workflow,
                                                int page,
                                                int pageSize) throws IOException;

    @NotNull WorkflowRun getWorkflowRun(@NotNull Repository repository,
                                        long runId) throws IOException;

    @NotNull Page<WorkflowJob> getRunJobs(@NotNull Repository repository,
                                          long runId,
                                          int page,
                                          int pageSize) throws IOException;

    @NotNull WorkflowJob getJob(@NotNull Repository repository,
                                long jobId) throws IOException;

    /**
     * Opens the accumulated job log as a stream. The caller must close it.
     */
    @NotNull JobLogStream openJobLogs(@NotNull Repository repository,
                                      long jobId) throws IOException;

    @NotNull ConnectionInfo testConnection() throws IOException;
}
