package com.company.gitea.api;

import com.company.gitea.model.Repository;
import com.company.gitea.model.Workflow;
import com.company.gitea.model.WorkflowJob;
import com.company.gitea.model.WorkflowRun;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Typed wrapper around Gitea 1.27 Actions endpoints.
 *
 * @see <a href="https://docs.gitea.com/api/1.27/#tag/repository">Gitea API: repository</a>
 */
public final class ActionsApi {

    public static final int DEFAULT_PAGE_SIZE = 30;
    private static final int MAX_PAGE_SIZE = 50;

    private static final Type REPOSITORY_LIST = new TypeToken<List<Repository>>() {
    }.getType();

    private final GiteaApiClient client;

    public ActionsApi(@NotNull GiteaApiClient client) {
        this.client = client;
    }

    public @NotNull GiteaApiClient getClient() {
        return client;
    }

    public @NotNull String getServerVersion() throws IOException {
        return client.getVersion();
    }

    /**
     * {@code GET /repos/{owner}/{repo}}.
     */
    public @NotNull Repository getRepository(@NotNull String fullName) throws IOException {
        return client.getJson("/repos/" + repoPath(fullName), Repository.class);
    }

    /**
     * {@code GET /user/repos?page=&limit=}. This endpoint returns a plain JSON array;
     * pagination must be driven by the caller until a short page is returned.
     */
    public @NotNull List<Repository> getRepositories(int page, int limit) throws IOException {
        int safePage = Math.max(1, page);
        int safeLimit = clampLimit(limit);
        List<Repository> repositories = client.getJson(
                "/user/repos?page=" + safePage + "&limit=" + safeLimit,
                REPOSITORY_LIST
        );
        return repositories == null ? List.of() : List.copyOf(repositories);
    }

    /**
     * {@code GET /repos/{owner}/{repo}/actions/workflows}.
     * Gitea returns all workflows without pagination.
     */
    public @NotNull List<Workflow> getWorkflows(@NotNull Repository repository) throws IOException {
        WorkflowsResponse response = client.getJson(
                "/repos/" + repoPath(repository.displayName()) + "/actions/workflows",
                WorkflowsResponse.class
        );
        return response == null || response.workflows == null ? List.of() : List.copyOf(response.workflows);
    }

    /**
     * {@code GET /repos/{owner}/{repo}/actions/workflows/{workflow_id}/runs}.
     * {@code workflowId} must be the workflow file name, for example {@code build.yml}.
     */
    public @NotNull RunPage getWorkflowRuns(@NotNull Repository repository,
                                            @NotNull String workflowId,
                                            int page,
                                            int limit) throws IOException {
        int safePage = Math.max(1, page);
        int safeLimit = clampLimit(limit);
        WorkflowRunsResponse response = client.getJson(
                "/repos/" + repoPath(repository.displayName())
                        + "/actions/workflows/" + encodePathSegment(workflowId)
                        + "/runs?page=" + safePage + "&limit=" + safeLimit,
                WorkflowRunsResponse.class
        );
        return toRunPage(response);
    }

    /**
     * {@code GET /repos/{owner}/{repo}/actions/runs/{run}}.
     */
    public @NotNull WorkflowRun getWorkflowRun(@NotNull Repository repository, long runId) throws IOException {
        return client.getJson(
                "/repos/" + repoPath(repository.displayName()) + "/actions/runs/" + runId,
                WorkflowRun.class
        );
    }

    /**
     * {@code GET /repos/{owner}/{repo}/actions/runs/{run}/jobs}.
     */
    public @NotNull JobPage getRunJobs(@NotNull Repository repository,
                                       long runId,
                                       int page,
                                       int limit) throws IOException {
        int safePage = Math.max(1, page);
        int safeLimit = clampLimit(limit);
        WorkflowJobsResponse response = client.getJson(
                "/repos/" + repoPath(repository.displayName())
                        + "/actions/runs/" + runId
                        + "/jobs?page=" + safePage + "&limit=" + safeLimit,
                WorkflowJobsResponse.class
        );
        return toJobPage(response);
    }

    /**
     * {@code GET /repos/{owner}/{repo}/actions/jobs/{job_id}}.
     */
    public @NotNull WorkflowJob getJob(@NotNull Repository repository, long jobId) throws IOException {
        return client.getJson(
                "/repos/" + repoPath(repository.displayName()) + "/actions/jobs/" + jobId,
                WorkflowJob.class
        );
    }

    /**
     * {@code GET /repos/{owner}/{repo}/actions/jobs/{job_id}/logs}.
     * <p>
     * Gitea serves this endpoint as {@code text/plain; charset=utf-8} (not JSON).
     * An empty body is valid; HTTP 404 means that the job has not started or logs
     * were cleaned up.
     */
    public @NotNull String getJobLogs(@NotNull Repository repository, long jobId) throws IOException {
        String logs = client.getText(
                "/repos/" + repoPath(repository.displayName()) + "/actions/jobs/" + jobId + "/logs"
        );
        return logs == null ? "" : logs;
    }

    public record RunPage(@NotNull List<WorkflowRun> runs, int totalCount) {
        public RunPage {
            runs = runs == null ? List.of() : List.copyOf(runs);
        }
    }

    public record JobPage(@NotNull List<WorkflowJob> jobs, int totalCount) {
        public JobPage {
            jobs = jobs == null ? List.of() : List.copyOf(jobs);
        }
    }

    private static RunPage toRunPage(WorkflowRunsResponse response) {
        if (response == null || response.workflowRuns == null) {
            return new RunPage(List.of(), 0);
        }
        return new RunPage(response.workflowRuns, Math.max(0, response.totalCount));
    }

    private static JobPage toJobPage(WorkflowJobsResponse response) {
        if (response == null || response.jobs == null) {
            return new JobPage(List.of(), 0);
        }
        return new JobPage(response.jobs, Math.max(0, response.totalCount));
    }

    private static int clampLimit(int limit) {
        return Math.max(1, Math.min(limit, MAX_PAGE_SIZE));
    }

    private static @NotNull String repoPath(@NotNull String fullName) throws IOException {
        int slash = fullName.indexOf('/');
        if (slash <= 0 || slash == fullName.length() - 1 || fullName.indexOf('/', slash + 1) >= 0) {
            throw new IOException("Invalid repository name; expected owner/repo: " + fullName);
        }
        return encodePathSegment(fullName.substring(0, slash))
                + "/" + encodePathSegment(fullName.substring(slash + 1));
    }

    private static @NotNull String encodePathSegment(@NotNull String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static final class WorkflowsResponse {
        @SerializedName("workflows")
        private List<Workflow> workflows;
        @SerializedName("total_count")
        private int totalCount;
    }

    private static final class WorkflowRunsResponse {
        @SerializedName("workflow_runs")
        private List<WorkflowRun> workflowRuns;
        @SerializedName("total_count")
        private int totalCount;
    }

    private static final class WorkflowJobsResponse {
        @SerializedName("jobs")
        private List<WorkflowJob> jobs;
        @SerializedName("total_count")
        private int totalCount;
    }
}
