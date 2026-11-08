package ru.pvlapvp.ciactionsmonitor.provider.github;

import ru.pvlapvp.ciactionsmonitor.provider.ActionsApiException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubApiClientErrorTest {

    private static final String TOKEN = "github-secret-token";

    private HttpServer server;
    private GitHubApiClient client;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            Response response = switch (path) {
                case "/rate-limited" -> new Response(403, Map.of(
                        "X-RateLimit-Remaining", "0",
                        "Retry-After", "30"
                ), "{\"message\":\"API rate limit exceeded\"}");
                case "/too-many-requests" -> new Response(429, Map.of(
                        "Retry-After", "1"
                ), "{\"message\":\"Too many requests\"}");
                case "/forbidden" -> new Response(403, Map.of(), "{\"message\":\"Forbidden\"}");
                case "/missing" -> new Response(404, Map.of(), "{\"message\":\"Not Found\"}");
                case "/gone" -> new Response(410, Map.of(), "{\"message\":\"Gone\"}");
                case "/unauthorized" -> new Response(401, Map.of(), "{\"message\":\"Bad credentials\"}");
                default -> new Response(500, Map.of(), "{\"message\":\"Unexpected path\"}");
            };
            for (Map.Entry<String, String> header : response.headers().entrySet()) {
                exchange.getResponseHeaders().add(header.getKey(), header.getValue());
            }
            byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(response.status(), body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();

        URI apiBase = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        GitHubEndpoint endpoint = new GitHubEndpoint(
                GitHubDeployment.GITHUB_COM,
                URI.create("https://github.com"),
                apiBase,
                "2022-11-28",
                "/rate_limit"
        );
        client = new GitHubApiClient(endpoint, TOKEN, 5);
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void mapsRateLimitFromHeaders() {
        Instant before = Instant.now();

        ActionsApiException error = assertThrows(
                ActionsApiException.class,
                () -> client.getJsonObject("/rate-limited")
        );

        assertTrue(error.isRateLimited());
        assertNotNull(error.getRetryAfter());
        assertTrue(error.getRetryAfter().isAfter(before));
    }

    @Test
    void maps429ToRateLimit() {
        ActionsApiException error = assertThrows(
                ActionsApiException.class,
                () -> client.getJsonObject("/too-many-requests")
        );

        assertTrue(error.isRateLimited());
        assertNotNull(error.getRetryAfter());
    }

    @Test
    void maps403ToAuthenticationError() {
        ActionsApiException error = assertThrows(
                ActionsApiException.class,
                () -> client.getJsonObject("/forbidden")
        );

        assertTrue(error.isAuthenticationError());
    }

    @Test
    void maps404And410ToNotFound() {
        ActionsApiException notFound = assertThrows(
                ActionsApiException.class,
                () -> client.getJsonObject("/missing")
        );
        assertTrue(notFound.isNotFound());

        ActionsApiException gone = assertThrows(
                ActionsApiException.class,
                () -> client.getJsonObject("/gone")
        );
        assertTrue(gone.isNotFound());
        assertTrue(gone.getMessage().contains("410"));
    }

    @Test
    void authenticationErrorDoesNotLeakTheToken() {
        ActionsApiException error = assertThrows(
                ActionsApiException.class,
                () -> client.getJsonObject("/unauthorized")
        );

        assertTrue(error.isAuthenticationError());
        assertFalse(error.getMessage().contains(TOKEN));
    }

    private record Response(int status, Map<String, String> headers, String body) {
    }
}
