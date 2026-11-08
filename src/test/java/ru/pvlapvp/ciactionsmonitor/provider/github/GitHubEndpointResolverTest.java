package ru.pvlapvp.ciactionsmonitor.provider.github;

import ru.pvlapvp.ciactionsmonitor.model.ActionsConnection;
import ru.pvlapvp.ciactionsmonitor.model.ProviderType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubEndpointResolverTest {

    private final GitHubEndpointResolver resolver = new GitHubEndpointResolver();

    @Test
    void normalizesServerUrl() {
        assertEquals("https://ghe.example.com", resolver.normalizeServerUrl("ghe.example.com/"));
        assertEquals("https://ghe.example.com", resolver.normalizeServerUrl("https://ghe.example.com/api/v3"));
        assertEquals("https://github.com", resolver.normalizeServerUrl("https://api.github.com"));
    }

    @Test
    void resolvesGitHubCom() {
        GitHubEndpoint endpoint = resolver.resolve(connection("https://github.com", null));

        assertEquals(GitHubDeployment.GITHUB_COM, endpoint.deployment());
        assertEquals("https://github.com", endpoint.webBaseUri().toString());
        assertEquals("https://api.github.com", endpoint.apiBaseUri().toString());
        assertEquals("2022-11-28", endpoint.apiVersion());
        assertEquals("/rate_limit", endpoint.connectionProbePath());
    }

    @Test
    void resolvesGitHubEnterpriseServer() {
        GitHubEndpoint endpoint = resolver.resolve(connection("https://ghe.example.com", null));

        assertEquals(GitHubDeployment.GHES, endpoint.deployment());
        assertEquals("https://ghe.example.com/api/v3", endpoint.apiBaseUri().toString());
        assertEquals("/meta", endpoint.connectionProbePath());
    }

    @Test
    void apiUrlOverrideWins() {
        GitHubEndpoint endpoint = resolver.resolve(
                connection("https://github.com", "https://proxy.example.com/api")
        );

        assertEquals("https://proxy.example.com/api", endpoint.apiBaseUri().toString());
        assertEquals(GitHubDeployment.GITHUB_COM, endpoint.deployment());
    }

    @Test
    void gheCloudStillReportsAClearError() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolve(connection("https://acme.ghe.com", null))
        );
        assertTrue(error.getMessage().contains("GHE.com"));
        assertTrue(error.getMessage().contains("not supported"));
    }

    private static ActionsConnection connection(String serverUrl, String apiUrl) {
        return new ActionsConnection(
                "id-1",
                ProviderType.GITHUB,
                "GitHub",
                serverUrl,
                apiUrl,
                5,
                List.of()
        );
    }
}
