package com.company.gitea.service;

import com.company.gitea.model.ActionsConnection;
import com.company.gitea.model.Repository;
import com.company.gitea.model.Workflow;
import com.company.gitea.model.WorkflowJob;
import com.company.gitea.model.WorkflowRun;
import com.company.gitea.provider.ActionsProvider;
import com.company.gitea.provider.Page;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Project-level access to CI/CD data. All methods perform blocking network calls and
 * must be called from a background thread. The project's disposed state is checked
 * between paginated requests.
 */
@Service(Service.Level.PROJECT)
public final class ActionsMonitorService {

    public static final int REPOSITORY_PAGE_SIZE = 30;
    public static final int WORKFLOWS_PAGE_SIZE = 50;
    public static final int RUNS_PAGE_SIZE = 30;
    public static final int JOBS_PAGE_SIZE = 50;
    private static final int MAX_REPOSITORY_PAGES = 10;
    private static final int MAX_WORKFLOW_PAGES = 20;

    private final Project project;

    public ActionsMonitorService(@NotNull Project project) {
        this.project = project;
    }

    public Project getProject() {
        return project;
    }

    public @NotNull List<Repository> loadRepositories(@NotNull ActionsConnection connection) throws IOException {
        checkCancelled();
        if (!connection.repositories().isEmpty()) {
            List<Repository> result = new ArrayList<>(connection.repositories().size());
            for (String fullName : connection.repositories()) {
                result.add(Repository.fromFullName(fullName));
            }
            return result;
        }

        // No explicit list: show repositories accessible to the token, with pagination.
        ActionsProvider provider = provider(connection);
        List<Repository> result = new ArrayList<>();
        for (int page = 1; page <= MAX_REPOSITORY_PAGES; page++) {
            checkCancelled();
            Page<Repository> chunk = provider.getRepositories(page, REPOSITORY_PAGE_SIZE);
            result.addAll(chunk.items());
            if (!chunk.hasMore() || chunk.items().size() < REPOSITORY_PAGE_SIZE) {
                break;
            }
        }
        return result;
    }

    public @NotNull List<Workflow> loadWorkflows(@NotNull ActionsConnection connection,
                                                 @NotNull Repository repository) throws IOException {
        checkCancelled();
        ActionsProvider provider = provider(connection);
        // GET /repos/{owner}/{repo} validates the repository and gives a fresh metadata object.
        Repository actual = provider.getRepository(repository.displayName());
        List<Workflow> result = new ArrayList<>();
        for (int page = 1; page <= MAX_WORKFLOW_PAGES; page++) {
            checkCancelled();
            Page<Workflow> chunk = provider.getWorkflows(actual, page, WORKFLOWS_PAGE_SIZE);
            result.addAll(chunk.items());
            if (!chunk.hasMore() || chunk.items().isEmpty()) {
                break;
            }
        }
        return result;
    }

    public @NotNull RunList loadRuns(@NotNull ActionsConnection connection,
                                     @NotNull Repository repository,
                                     @NotNull Workflow workflow,
                                     int loadedPages) throws IOException {
        checkCancelled();
        String workflowId = workflow.id();
        if (workflowId == null || workflowId.isBlank()) {
            throw new IOException("Workflow \"" + workflow.displayName() + "\" has no id");
        }

        ActionsProvider provider = provider(connection);
        List<WorkflowRun> runs = new ArrayList<>();
        int totalCount = 0;
        int pages = Math.max(1, loadedPages);
        for (int page = 1; page <= pages; page++) {
            checkCancelled();
            Page<WorkflowRun> chunk = provider.getWorkflowRuns(repository, workflow, page, RUNS_PAGE_SIZE);
            totalCount = Math.max(totalCount, chunk.totalCount());
            runs.addAll(chunk.items());
            if (chunk.items().size() < RUNS_PAGE_SIZE) {
                break;
            }
        }
        return new RunList(runs, totalCount);
    }

    public @NotNull JobList loadJobs(@NotNull ActionsConnection connection,
                                     @NotNull Repository repository,
                                     @NotNull WorkflowRun run,
                                     int loadedPages) throws IOException {
        checkCancelled();
        ActionsProvider provider = provider(connection);
        List<WorkflowJob> jobs = new ArrayList<>();
        int totalCount = 0;
        int pages = Math.max(1, loadedPages);
        for (int page = 1; page <= pages; page++) {
            checkCancelled();
            Page<WorkflowJob> chunk = provider.getRunJobs(repository, run.id(), page, JOBS_PAGE_SIZE);
            totalCount = Math.max(totalCount, chunk.totalCount());
            jobs.addAll(chunk.items());
            if (chunk.items().size() < JOBS_PAGE_SIZE) {
                break;
            }
        }
        return new JobList(jobs, totalCount);
    }

    public @NotNull WorkflowRun loadRun(@NotNull ActionsConnection connection,
                                        @NotNull Repository repository,
                                        long runId) throws IOException {
        checkCancelled();
        return provider(connection).getWorkflowRun(repository, runId);
    }

    public @NotNull WorkflowJob loadJob(@NotNull ActionsConnection connection,
                                        @NotNull Repository repository,
                                        long jobId) throws IOException {
        checkCancelled();
        return provider(connection).getJob(repository, jobId);
    }

    public @NotNull String loadJobLogs(@NotNull ActionsConnection connection,
                                       @NotNull Repository repository,
                                       long jobId) throws IOException {
        checkCancelled();
        return provider(connection).getJobLogs(repository, jobId);
    }

    private @NotNull ActionsProvider provider(@NotNull ActionsConnection connection) throws IOException {
        checkCancelled();
        return GiteaConnectionService.getInstance().getProvider(connection);
    }

    private void checkCancelled() {
        if (project.isDisposed() || Thread.currentThread().isInterrupted()) {
            throw new ProcessCanceledException();
        }
    }

    public record RunList(@NotNull List<WorkflowRun> runs, int totalCount) {
        public RunList {
            runs = runs == null ? List.of() : List.copyOf(runs);
        }
    }

    public record JobList(@NotNull List<WorkflowJob> jobs, int totalCount) {
        public JobList {
            jobs = jobs == null ? List.of() : List.copyOf(jobs);
        }
    }
}
