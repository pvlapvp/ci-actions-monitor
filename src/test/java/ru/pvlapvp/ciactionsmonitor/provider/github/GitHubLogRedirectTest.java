package ru.pvlapvp.ciactionsmonitor.provider.github;

import ru.pvlapvp.ciactionsmonitor.model.ActionsConnection;
import ru.pvlapvp.ciactionsmonitor.model.ProviderType;
import ru.pvlapvp.ciactionsmonitor.model.Repository;
import ru.pvlapvp.ciactionsmonitor.provider.JobLogStream;
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
import static org.junit.jupiter.api.Assertions.assertNull;

class GitHubLogRedirectTest {

    private static final String TOKEN = "github-secret-token";

    private HttpServer apiServer;
    private HttpServer storageServer;
    private final AtomicReference<String> apiAuthorization = new AtomicReference<>();
    private final AtomicReference<String> storageAuthorization = new AtomicReference<>();
    private final AtomicReference<String> storageApiVersion = new AtomicReference<>();

    @BeforeEach
    void startServers() throws IOException {
        storageServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        storageServer.createContext("/signed/log", exchange -> {
            storageAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            storageApiVersion.set(exchange.getRequestHeaders().getFirst("X-GitHub-Api-Version"));
            byte[] body = "signed log body\n".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        storageServer.start();

        int storagePort = storageServer.getAddress().getPort();
        apiServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        apiServer.createContext("/repos/acme/demo/actions/jobs/7/logs", exchange -> {
            apiAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.getResponseHeaders().add(
                    "Location",
                    "http://127.0.0.1:" + storagePort + "/signed/log"
            );
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        apiServer.start();
    }

    @AfterEach
    void stopServers() {
        if (apiServer != null) {
            apiServer.stop(0);
        }
        if (storageServer != null) {
            storageServer.stop(0);
        }
    }

    @Test
    void doesNotForwardAuthorizationToTheSignedStorageHost() throws IOException {
        GitHubActionsProvider provider = provider();
        String logs;
        try (JobLogStream stream = provider.openJobLogs(Repository.fromFullName("acme/demo"), 7)) {
            logs = new String(stream.stream().readAllBytes(), StandardCharsets.UTF_8);
        }

        assertEquals("signed log body\n", logs);
        assertEquals("Bearer " + TOKEN, apiAuthorization.get());
        assertNull(storageAuthorization.get());
        assertNull(storageApiVersion.get());
    }

    private GitHubActionsProvider provider() {
        String apiBase = "http://127.0.0.1:" + apiServer.getAddress().getPort();
        ActionsConnection connection = new ActionsConnection(
                "id-1",
                ProviderType.GITHUB,
                "GitHub",
                "https://github.com",
                apiBase,
                5,
                List.of()
        );
        return (GitHubActionsProvider) new GitHubProviderFactory().create(connection, TOKEN);
    }
}
