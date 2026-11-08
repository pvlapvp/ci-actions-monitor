package com.company.gitea.api;

import com.company.gitea.provider.gitea.dto.GiteaRepositoryDto;
import com.company.gitea.provider.gitea.dto.GiteaWorkflowDto;
import com.company.gitea.provider.gitea.dto.GiteaWorkflowJobDto;
import com.company.gitea.provider.gitea.dto.GiteaWorkflowJobsResponse;
import com.company.gitea.provider.gitea.dto.GiteaWorkflowRunDto;
import com.company.gitea.provider.gitea.dto.GiteaWorkflowRunsResponse;
import com.company.gitea.provider.gitea.dto.GiteaWorkflowsResponse;
import com.google.gson.reflect.TypeToken;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Typed wrapper around Gitea 1.27 Actions endpoints.
 * <p>
 * This class returns Gitea-specific DTOs only. Conversion to the provider-neutral
 * model is performed by {@code GiteaDtoMapper}.
 *
 * @see <a href="https://docs.gitea.com/api/1.27/#tag/repository">Gitea API: repository</a>
 */
public final class ActionsApi {

    public static final int DEFAULT_PAGE_SIZE = 30;
    private static final int MAX_PAGE_SIZE = 50;

    private static final Type REPOSITORY_LIST = new TypeToken<List<GiteaRepositoryDto>>() {
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
    public @NotNull GiteaRepositoryDto getRepository(@NotNull String fullName) throws IOException {
        return client.getJson("/repos/" + repoPath(fullName), GiteaRepositoryDto.class);
    }

    /**
     * {@code GET /user/repos?page=&limit=}. This endpoint returns a plain JSON array;
     * pagination must be driven by the caller until a short page is returned.
     */
    public @NotNull List<GiteaRepositoryDto> getRepositories(int page, int limit) throws IOException {
        int safePage = Math.max(1, page);
        int safeLimit = clampLimit(limit);
        List<GiteaRepositoryDto> repositories = client.getJson(
                "/user/repos?page=" + safePage + "&limit=" + safeLimit,
                REPOSITORY_LIST
        );
        return repositories == null ? List.of() : List.copyOf(repositories);
    }

    /**
     * {@code GET /repos/{owner}/{repo}/actions/workflows}.
     * Gitea returns all workflows without pagination.
     */
    public @NotNull List<GiteaWorkflowDto> getWorkflows(@NotNull GiteaRepositoryDto repository) throws IOException {
        GiteaWorkflowsResponse response = client.getJson(
                "/repos/" + repoPath(repository.fullName) + "/actions/workflows",
                GiteaWorkflowsResponse.class
        );
        return response == null || response.workflows == null ? List.of() : List.copyOf(response.workflows);
    }

    /**
     * {@code GET /repos/{owner}/{repo}/actions/workflows/{workflow_id}/runs}.
     * {@code workflowId} must be the workflow file name, for example {@code build.yml}.
     */
    public @NotNull RunPage getWorkflowRuns(@NotNull GiteaRepositoryDto repository,
                                            @NotNull String workflowId,
                                            int page,
                                            int limit) throws IOException {
        int safePage = Math.max(1, page);
        int safeLimit = clampLimit(limit);
        GiteaWorkflowRunsResponse response = client.getJson(
                "/repos/" + repoPath(repository.fullName)
                        + "/actions/workflows/" + encodePathSegment(workflowId)
                        + "/runs?page=" + safePage + "&limit=" + safeLimit,
                GiteaWorkflowRunsResponse.class
        );
        return toRunPage(response);
    }

    /**
     * {@code GET /repos/{owner}/{repo}/actions/runs/{run}}.
     */
    public @NotNull GiteaWorkflowRunDto getWorkflowRun(@NotNull GiteaRepositoryDto repository,
                                                       long runId) throws IOException {
        return client.getJson(
                "/repos/" + repoPath(repository.fullName) + "/actions/runs/" + runId,
                GiteaWorkflowRunDto.class
        );
    }

    /**
     * {@code GET /repos/{owner}/{repo}/actions/runs/{run}/jobs}.
     */
    public @NotNull JobPage getRunJobs(@NotNull GiteaRepositoryDto repository,
                                       long runId,
                                       int page,
                                       int limit) throws IOException {
        int safePage = Math.max(1, page);
        int safeLimit = clampLimit(limit);
        GiteaWorkflowJobsResponse response = client.getJson(
                "/repos/" + repoPath(repository.fullName)
                        + "/actions/runs/" + runId
                        + "/jobs?page=" + safePage + "&limit=" + safeLimit,
                GiteaWorkflowJobsResponse.class
        );
        return toJobPage(response);
    }

    /**
     * {@code GET /repos/{owner}/{repo}/actions/jobs/{job_id}}.
     */
    public @NotNull GiteaWorkflowJobDto getJob(@NotNull GiteaRepositoryDto repository,
                                               long jobId) throws IOException {
        return client.getJson(
                "/repos/" + repoPath(repository.fullName) + "/actions/jobs/" + jobId,
                GiteaWorkflowJobDto.class
        );
    }

    /**
     * {@code GET /repos/{owner}/{repo}/actions/jobs/{job_id}/logs}.
     * <p>
     * Gitea serves this endpoint as {@code text/plain; charset=utf-8} (not JSON).
     * An empty body is valid; HTTP 404 means that the job has not started or logs
     * were cleaned up.
     */
    public @NotNull InputStream openJobLogStream(@NotNull GiteaRepositoryDto repository,
                                                 long jobId) throws IOException {
        return client.openTextStream(
                "/repos/" + repoPath(repository.fullName) + "/actions/jobs/" + jobId + "/logs"
        );
    }

    public @NotNull String getJobLogs(@NotNull GiteaRepositoryDto repository, long jobId) throws IOException {
        String logs = client.getText(
                "/repos/" + repoPath(repository.fullName) + "/actions/jobs/" + jobId + "/logs"
        );
        return logs == null ? "" : logs;
    }

    public record RunPage(@NotNull List<GiteaWorkflowRunDto> runs, int totalCount) {
        public RunPage {
            runs = runs == null ? List.of() : List.copyOf(runs);
        }
    }

    public record JobPage(@NotNull List<GiteaWorkflowJobDto> jobs, int totalCount) {
        public JobPage {
            jobs = jobs == null ? List.of() : List.copyOf(jobs);
        }
    }

    private static RunPage toRunPage(GiteaWorkflowRunsResponse response) {
        if (response == null || response.workflowRuns == null) {
            return new RunPage(List.of(), 0);
        }
        return new RunPage(response.workflowRuns, Math.max(0, response.totalCount));
    }

    private static JobPage toJobPage(GiteaWorkflowJobsResponse response) {
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
}
