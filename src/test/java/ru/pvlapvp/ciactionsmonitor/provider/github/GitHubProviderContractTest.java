package ru.pvlapvp.ciactionsmonitor.provider.github;

import ru.pvlapvp.ciactionsmonitor.model.ActionsConnection;
import ru.pvlapvp.ciactionsmonitor.model.ActionsStatus;
import ru.pvlapvp.ciactionsmonitor.model.ProviderType;
import ru.pvlapvp.ciactionsmonitor.model.Repository;
import ru.pvlapvp.ciactionsmonitor.model.Workflow;
import ru.pvlapvp.ciactionsmonitor.model.WorkflowJob;
import ru.pvlapvp.ciactionsmonitor.model.WorkflowRun;
import ru.pvlapvp.ciactionsmonitor.provider.ActionsApiException;
import ru.pvlapvp.ciactionsmonitor.provider.ConnectionInfo;
import ru.pvlapvp.ciactionsmonitor.provider.JobLogStream;
import ru.pvlapvp.ciactionsmonitor.provider.Page;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubProviderContractTest {

    private static final String TOKEN = "github-secret-token";

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> apiVersion = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            apiVersion.set(exchange.getRequestHeaders().getFirst("X-GitHub-Api-Version"));
            String path = exchange.getRequestURI().getPath();
            Response response = route(path);
            byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", response.contentType());
            exchange.sendResponseHeaders(response.status(), body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void loadsRepositoriesWorkflowsRunsJobsAndLogs() throws IOException {
        GitHubActionsProvider provider = provider();

        Page<Repository> repositories = provider.getRepositories(1, 2);
        assertEquals(2, repositories.items().size());
        assertTrue(repositories.hasMore());

        Repository repository = provider.getRepository("acme/demo");
        Page<Workflow> workflows = provider.getWorkflows(repository, 1, 50);
        assertEquals(1, workflows.items().size());
        Workflow workflow = workflows.items().get(0);
        assertEquals("123", workflow.id());

        Page<WorkflowRun> runs = provider.getWorkflowRuns(repository, workflow, 1, 2);
        assertEquals(3, runs.totalCount());
        assertEquals(2, runs.items().size());
        assertTrue(runs.hasMore());
        assertEquals(ActionsStatus.SUCCESS, runs.items().get(0).state());

        WorkflowRun run = provider.getWorkflowRun(repository, 42);
        assertEquals(42, run.id());

        Page<WorkflowJob> jobs = provider.getRunJobs(repository, 42, 1, 50);
        assertEquals(1, jobs.items().size());
        assertFalse(jobs.hasMore());

        WorkflowJob job = provider.getJob(repository, 7);
        assertEquals(7, job.id());

        try (JobLogStream logStream = provider.openJobLogs(repository, 7)) {
            String logs = new String(logStream.stream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals("hello\nworld\n", logs);
            assertNotNull(logStream.contentType());
        }
    }

    @Test
    void sendsTokenAndApiVersionAndReportsAccount() throws IOException {
        GitHubActionsProvider provider = provider();

        ConnectionInfo info = provider.testConnection();

        assertEquals(ProviderType.GITHUB, info.providerType());
        assertEquals("GitHub.com", info.version());
        assertEquals("octocat", info.account());
        assertEquals("Bearer " + TOKEN, authorization.get());
        assertEquals("2022-11-28", apiVersion.get());
    }

    @Test
    void maps403WithoutRateLimitHeadersToAuthenticationError() {
        GitHubActionsProvider provider = provider();

        ActionsApiException error = assertThrows(
                ActionsApiException.class,
                () -> provider.getRepository("acme/private")
        );

        assertTrue(error.isAuthenticationError());
        assertFalse(error.getMessage().contains(TOKEN));
    }

    @Test
    void maps404ToNotFound() {
        GitHubActionsProvider provider = provider();

        ActionsApiException error = assertThrows(
                ActionsApiException.class,
                () -> provider.getRepository("acme/missing")
        );

        assertTrue(error.isNotFound());
    }

    private GitHubActionsProvider provider() {
        ActionsConnection connection = new ActionsConnection(
                "id-1",
                ProviderType.GITHUB,
                "GitHub",
                "https://github.com",
                baseUrl,
                5,
                List.of()
        );
        return (GitHubActionsProvider) new GitHubProviderFactory().create(connection, TOKEN);
    }

    private static Response route(String path) {
        return switch (path) {
            case "/rate_limit" -> json("{\"resources\":{}}");
            case "/user" -> json("{\"login\":\"octocat\"}");
            case "/user/repos" -> json("""
                    [{
                      "id": 10,
                      "name": "demo",
                      "full_name": "acme/demo",
                      "html_url": "https://github.com/acme/demo",
                      "default_branch": "main",
                      "private": false,
                      "owner": {"login": "acme"}
                    }, {
                      "id": 11,
                      "name": "other",
                      "full_name": "acme/other",
                      "html_url": "https://github.com/acme/other",
                      "default_branch": "main",
                      "private": false,
                      "owner": {"login": "acme"}
                    }]
                    """);
            case "/repos/acme/demo" -> json("""
                    {
                      "id": 10,
                      "name": "demo",
                      "full_name": "acme/demo",
                      "html_url": "https://github.com/acme/demo",
                      "default_branch": "main",
                      "private": false,
                      "owner": {"login": "acme"}
                    }
                    """);
            case "/repos/acme/private" ->
                    new Response(403, "application/json", "{\"message\":\"Forbidden\"}");
            case "/repos/acme/missing" ->
                    new Response(404, "application/json", "{\"message\":\"Not Found\"}");
            case "/repos/acme/demo/actions/workflows" -> json("""
                    {
                      "total_count": 1,
                      "workflows": [{
                        "id": 123,
                        "name": "Build",
                        "path": ".github/workflows/build.yml",
                        "state": "active",
                        "url": "https://api.github.com/repos/acme/demo/actions/workflows/123"
                      }]
                    }
                    """);
            case "/repos/acme/demo/actions/workflows/123/runs" -> json("""
                    {
                      "total_count": 3,
                      "workflow_runs": [{
                        "id": 42,
                        "name": "Build",
                        "display_title": "Build main",
                        "path": ".github/workflows/build.yml",
                        "status": "completed",
                        "conclusion": "success",
                        "head_branch": "main",
                        "head_sha": "abc123",
                        "run_number": 5,
                        "run_attempt": 1
                      }, {
                        "id": 41,
                        "name": "Build",
                        "display_title": "Build previous",
                        "path": ".github/workflows/build.yml",
                        "status": "completed",
                        "conclusion": "failure",
                        "head_branch": "main",
                        "run_number": 4,
                        "run_attempt": 1
                      }]
                    }
                    """);
            case "/repos/acme/demo/actions/runs/42" -> json("""
                    {
                      "id": 42,
                      "name": "Build",
                      "display_title": "Build main",
                      "path": ".github/workflows/build.yml",
                      "status": "completed",
                      "conclusion": "success",
                      "head_branch": "main",
                      "run_number": 5,
                      "run_attempt": 1
                    }
                    """);
            case "/repos/acme/demo/actions/runs/42/jobs" -> json("""
                    {
                      "total_count": 1,
                      "jobs": [{
                        "id": 7,
                        "run_id": 42,
                        "name": "test",
                        "status": "completed",
                        "conclusion": "success",
                        "labels": ["ubuntu-latest"]
                      }]
                    }
                    """);
            case "/repos/acme/demo/actions/jobs/7" -> json("""
                    {
                      "id": 7,
                      "run_id": 42,
                      "name": "test",
                      "status": "completed",
                      "conclusion": "success"
                    }
                    """);
            case "/repos/acme/demo/actions/jobs/7/logs" ->
                    new Response(200, "text/plain; charset=utf-8", "hello\nworld\n");
            default -> new Response(404, "application/json", "{\"message\":\"unexpected path " + path + "\"}");
        };
    }

    private static Response json(String body) {
        return new Response(200, "application/json", body);
    }

    private record Response(int status, String contentType, String body) {
    }
}
