package ru.pvlapvp.ciactionsmonitor.settings;

import ru.pvlapvp.ciactionsmonitor.model.ActionsConnection;
import ru.pvlapvp.ciactionsmonitor.model.ProviderType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionsSettingsMigrationTest {

    @Test
    void missingProviderTypeMeansGitea() {
        ActionsSettings settings = new ActionsSettings();
        ActionsSettings.State state = stateWithConnection("id-1", "", "https://gitea.example.com");

        settings.loadState(state);

        List<ActionsConnection> connections = settings.getConnections();
        assertEquals(1, connections.size());
        assertEquals(ProviderType.GITEA, connections.get(0).providerType());
        assertEquals("GITEA", state.connections.get(0).providerType);
    }

    @Test
    void unknownProviderTypeMeansGitea() {
        ActionsSettings settings = new ActionsSettings();
        ActionsSettings.State state = stateWithConnection("id-1", "bitbucket", "https://example.com");

        settings.loadState(state);

        assertEquals(ProviderType.GITEA, settings.getConnections().get(0).providerType());
    }

    @Test
    void legacyServerUrlCreatesGiteaConnection() {
        ActionsSettings settings = new ActionsSettings();
        ActionsSettings.State state = new ActionsSettings.State();
        state.serverUrl = "https://gitea.example.com/";
        state.requestTimeoutSeconds = 45;

        settings.loadState(state);

        List<ActionsConnection> connections = settings.getConnections();
        assertEquals(1, connections.size());
        ActionsConnection connection = connections.get(0);
        assertEquals(ProviderType.GITEA, connection.providerType());
        assertEquals("https://gitea.example.com", connection.serverUrl());
        assertEquals("gitea.example.com", connection.name());
        assertEquals(45, connection.safeTimeoutSeconds());
        assertTrue(state.serverUrl.isEmpty());
    }

    @Test
    void apiUrlAndRepositoriesAreSanitized() {
        ActionsSettings settings = new ActionsSettings();
        ActionsSettings.State state = new ActionsSettings.State();
        ActionsSettings.ConnectionState stored = new ActionsSettings.ConnectionState();
        stored.id = "id-1";
        stored.providerType = "github";
        stored.url = "https://github.com";
        stored.apiUrl = "  https://api.github.com  ";
        stored.repositories = List.of(" Acme/Demo ", "acme/demo", "", "acme/other");
        state.connections.add(stored);

        settings.loadState(state);

        ActionsConnection connection = settings.getConnections().get(0);
        assertEquals(ProviderType.GITHUB, connection.providerType());
        assertEquals("https://api.github.com", connection.apiUrl());
        assertEquals(List.of("Acme/Demo", "acme/other"), connection.repositories());
    }

    @Test
    void blankApiUrlBecomesNull() {
        ActionsSettings settings = new ActionsSettings();
        ActionsSettings.State state = stateWithConnection("id-1", "GITHUB", "https://github.com");
        state.connections.get(0).apiUrl = "   ";

        settings.loadState(state);

        assertNull(settings.getConnections().get(0).apiUrl());
    }

    private static ActionsSettings.State stateWithConnection(String id,
                                                             String providerType,
                                                             String url) {
        ActionsSettings.State state = new ActionsSettings.State();
        ActionsSettings.ConnectionState connection = new ActionsSettings.ConnectionState();
        connection.id = id;
        connection.providerType = providerType;
        connection.url = url;
        state.connections.add(connection);
        return state;
    }
}
