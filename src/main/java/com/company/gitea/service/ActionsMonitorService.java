package com.company.gitea.service;

import com.company.gitea.api.ActionsApi;
import com.company.gitea.model.GiteaServer;
import com.company.gitea.model.Repository;
import com.company.gitea.model.Workflow;
import com.company.gitea.model.WorkflowJob;
import com.company.gitea.model.WorkflowRun;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Project-level access to Gitea data. All methods perform blocking HTTP calls and
 * must be called from a background thread. The project's disposed state is checked
 * between paginated requests.
 */
@Service(Service.Level.PROJECT)
public final class ActionsMonitorService {

    public static final int RUNS_PAGE_SIZE = 30;
    public static final int JOBS_PAGE_SIZE = 50;
    private static final int MAX_REPOSITORY_PAGES = 10;

    private final Project project;

    public ActionsMonitorService(@NotNull Project project) {
        this.project = project;
    }

    public Project getProject() {
        return project;
    }

    public @NotNull List<Repository> loadRepositories(@NotNull GiteaServer server) throws IOException {
        checkCancelled();
        if (!server.repositories().isEmpty()) {
            List<Repository> result = new ArrayList<>(server.repositories().size());
            for (String fullName : server.repositories()) {
                result.add(Repository.fromFullName(fullName));
            }
            return result;
        }

        // No explicit list: show repositories accessible to the token, with pagination.
        List<Repository> result = new ArrayList<>();
        for (int page = 1; page <= MAX_REPOSITORY_PAGES; page++) {
            checkCancelled();
            List<Repository> chunk = api(server).getRepositories(page, ActionsApi.DEFAULT_PAGE_SIZE);
            result.addAll(chunk);
            if (chunk.size() < ActionsApi.DEFAULT_PAGE_SIZE) {
                break;
            }
        }
        return result;
    }

    public @NotNull List<Workflow> loadWorkflows(@NotNull GiteaServer server,
                                                 @NotNull Repository repository) throws IOException {
        checkCancelled();
        ActionsApi api = api(server);
        // GET /repos/{owner}/{repo} validates the repository and gives a fresh metadata object.
        Repository actual = api.getRepository(repository.displayName());
        checkCancelled();
        return api.getWorkflows(actual);
    }

    public @NotNull RunList loadRuns(@NotNull GiteaServer server,
                                     @NotNull Repository repository,
                                     @NotNull Workflow workflow,
                                     int loadedPages) throws IOException {
        checkCancelled();
        String workflowId = workflow.id();
        if (workflowId == null || workflowId.isBlank()) {
            throw new IOException("Workflow \"" + workflow.displayName() + "\" has no id");
        }

        ActionsApi api = api(server);
        List<WorkflowRun> runs = new ArrayList<>();
        int totalCount = 0;
        int pages = Math.max(1, loadedPages);
        for (int page = 1; page <= pages; page++) {
            checkCancelled();
            ActionsApi.RunPage chunk = api.getWorkflowRuns(repository, workflowId, page, RUNS_PAGE_SIZE);
            totalCount = Math.max(totalCount, chunk.totalCount());
            runs.addAll(chunk.runs());
            if (chunk.runs().size() < RUNS_PAGE_SIZE) {
                break;
            }
        }
        return new RunList(runs, totalCount);
    }

    public @NotNull JobList loadJobs(@NotNull GiteaServer server,
                                     @NotNull Repository repository,
                                     @NotNull WorkflowRun run,
                                     int loadedPages) throws IOException {
        checkCancelled();
        ActionsApi api = api(server);
        List<WorkflowJob> jobs = new ArrayList<>();
        int totalCount = 0;
        int pages = Math.max(1, loadedPages);
        for (int page = 1; page <= pages; page++) {
            checkCancelled();
            ActionsApi.JobPage chunk = api.getRunJobs(repository, run.id(), page, JOBS_PAGE_SIZE);
            totalCount = Math.max(totalCount, chunk.totalCount());
            jobs.addAll(chunk.jobs());
            if (chunk.jobs().size() < JOBS_PAGE_SIZE) {
                break;
            }
        }
        return new JobList(jobs, totalCount);
    }

    public @NotNull WorkflowRun loadRun(@NotNull GiteaServer server,
                                        @NotNull Repository repository,
                                        long runId) throws IOException {
        checkCancelled();
        return api(server).getWorkflowRun(repository, runId);
    }

    public @NotNull WorkflowJob loadJob(@NotNull GiteaServer server,
                                        @NotNull Repository repository,
                                        long jobId) throws IOException {
        checkCancelled();
        return api(server).getJob(repository, jobId);
    }

    public @NotNull String loadJobLogs(@NotNull GiteaServer server,
                                       @NotNull Repository repository,
                                       long jobId) throws IOException {
        checkCancelled();
        return api(server).getJobLogs(repository, jobId);
    }

    private @NotNull ActionsApi api(@NotNull GiteaServer server) throws IOException {
        checkCancelled();
        return GiteaConnectionService.getInstance().getActionsApi(server);
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
