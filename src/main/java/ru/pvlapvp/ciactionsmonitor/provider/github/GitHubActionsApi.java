package ru.pvlapvp.ciactionsmonitor.provider.github;

import ru.pvlapvp.ciactionsmonitor.model.ProviderType;
import ru.pvlapvp.ciactionsmonitor.provider.ConnectionInfo;
import ru.pvlapvp.ciactionsmonitor.provider.JobLogStream;
import ru.pvlapvp.ciactionsmonitor.provider.github.dto.GitHubRepositoryDto;
import ru.pvlapvp.ciactionsmonitor.provider.github.dto.GitHubWorkflowJobDto;
import ru.pvlapvp.ciactionsmonitor.provider.github.dto.GitHubWorkflowJobsResponse;
import ru.pvlapvp.ciactionsmonitor.provider.github.dto.GitHubWorkflowRunDto;
import ru.pvlapvp.ciactionsmonitor.provider.github.dto.GitHubWorkflowRunsResponse;
import ru.pvlapvp.ciactionsmonitor.provider.github.dto.GitHubWorkflowsResponse;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Typed wrapper around GitHub Actions REST endpoints.
 */
public final class GitHubActionsApi {

    private static final Type REPOSITORY_LIST = new TypeToken<List<GitHubRepositoryDto>>() {
    }.getType();

    private final GitHubApiClient client;
    private final GitHubEndpoint endpoint;

    public GitHubActionsApi(@NotNull GitHubApiClient client, @NotNull GitHubEndpoint endpoint) {
        this.client = client;
        this.endpoint = endpoint;
    }

    public @NotNull List<GitHubRepositoryDto> getRepositories(int page, int pageSize) throws IOException {
        List<GitHubRepositoryDto> repositories = client.getJson(
                "/user/repos?per_page=" + safePageSize(pageSize) + "&page=" + Math.max(1, page),
                REPOSITORY_LIST
        );
        return repositories == null ? List.of() : List.copyOf(repositories);
    }

    public @NotNull GitHubRepositoryDto getRepository(@NotNull String fullName) throws IOException {
        return client.getJson("/repos/" + repoPath(fullName), GitHubRepositoryDto.class);
    }

    public @NotNull GitHubWorkflowsResponse getWorkflows(@NotNull String fullName,
                                                         int page,
                                                         int pageSize) throws IOException {
        GitHubWorkflowsResponse response = client.getJson(
                "/repos/" + repoPath(fullName)
                        + "/actions/workflows?per_page=" + safePageSize(pageSize)
                        + "&page=" + Math.max(1, page),
                GitHubWorkflowsResponse.class
        );
        return response == null ? new GitHubWorkflowsResponse() : response;
    }

    public @NotNull GitHubWorkflowRunsResponse getWorkflowRuns(@NotNull String fullName,
                                                               @NotNull String workflowId,
                                                               int page,
                                                               int pageSize) throws IOException {
        GitHubWorkflowRunsResponse response = client.getJson(
                "/repos/" + repoPath(fullName)
                        + "/actions/workflows/" + encodePathSegment(workflowId)
                        + "/runs?per_page=" + safePageSize(pageSize)
                        + "&page=" + Math.max(1, page),
                GitHubWorkflowRunsResponse.class
        );
        return response == null ? new GitHubWorkflowRunsResponse() : response;
    }

    public @NotNull GitHubWorkflowRunDto getWorkflowRun(@NotNull String fullName,
                                                        long runId) throws IOException {
        return client.getJson(
                "/repos/" + repoPath(fullName) + "/actions/runs/" + runId,
                GitHubWorkflowRunDto.class
        );
    }

    public @NotNull GitHubWorkflowJobsResponse getRunJobs(@NotNull String fullName,
                                                          long runId,
                                                          int page,
                                                          int pageSize) throws IOException {
        GitHubWorkflowJobsResponse response = client.getJson(
                "/repos/" + repoPath(fullName)
                        + "/actions/runs/" + runId
                        + "/jobs?filter=latest"
                        + "&per_page=" + safePageSize(pageSize)
                        + "&page=" + Math.max(1, page),
                GitHubWorkflowJobsResponse.class
        );
        return response == null ? new GitHubWorkflowJobsResponse() : response;
    }

    public @NotNull GitHubWorkflowJobDto getJob(@NotNull String fullName,
                                                long jobId) throws IOException {
        return client.getJson(
                "/repos/" + repoPath(fullName) + "/actions/jobs/" + jobId,
                GitHubWorkflowJobDto.class
        );
    }

    public @NotNull JobLogStream openJobLogStream(@NotNull String fullName,
                                                  long jobId) throws IOException {
        return client.openJobLogStream(
                "/repos/" + repoPath(fullName) + "/actions/jobs/" + jobId + "/logs"
        );
    }

    public @NotNull ConnectionInfo testConnection() throws IOException {
        JsonObject meta = client.getJsonObject(endpoint.connectionProbePath());
        String version;
        if (meta.has("installed_version") && !meta.get("installed_version").isJsonNull()) {
            version = meta.get("installed_version").getAsString();
        } else if (endpoint.deployment() == GitHubDeployment.GITHUB_COM) {
            version = "GitHub.com";
        } else {
            version = "GitHub Enterprise";
        }
        return new ConnectionInfo(ProviderType.GITHUB, version, client.getApiBaseUrl(), null);
    }

    private static int safePageSize(int pageSize) {
        return Math.max(1, Math.min(pageSize, 100));
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
