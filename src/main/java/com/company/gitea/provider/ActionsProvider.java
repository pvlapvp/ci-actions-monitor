package com.company.gitea.provider;

import com.company.gitea.model.ActionsConnection;
import com.company.gitea.model.ProviderType;
import com.company.gitea.model.Repository;
import com.company.gitea.model.Workflow;
import com.company.gitea.model.WorkflowJob;
import com.company.gitea.model.WorkflowRun;
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
     * Returns the accumulated job log as text.
     * <p>
     * The streaming log contract will replace this method in a later stage.
     */
    @NotNull String getJobLogs(@NotNull Repository repository,
                               long jobId) throws IOException;

    @NotNull ConnectionInfo testConnection() throws IOException;
}
