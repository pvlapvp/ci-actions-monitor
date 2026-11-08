package ru.pvlapvp.ciactionsmonitor.provider;

import ru.pvlapvp.ciactionsmonitor.model.ActionsConnection;
import ru.pvlapvp.ciactionsmonitor.model.ProviderType;
import ru.pvlapvp.ciactionsmonitor.provider.gitea.GiteaProviderFactory;
import ru.pvlapvp.ciactionsmonitor.provider.github.GitHubProviderFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.Map;

/**
 * Application-level registry of provider factories.
 * <p>
 * Factories are registered through the {@code actionsProvider} extension point, so a
 * new CI/CD platform does not require changes to the UI or the monitoring service.
 */
@Service(Service.Level.APP)
public final class ProviderRegistry {

    private static final ExtensionPointName<ActionsProviderFactory> EP =
            ExtensionPointName.create("ru.pvlapvp.ci-actions-monitor.actionsProvider");

    private final Map<ProviderType, ActionsProviderFactory> factories = new EnumMap<>(ProviderType.class);

    public ProviderRegistry() {
        // The built-in providers must stay available even if no extension is
        // contributed, for example while the plugin is being loaded or upgraded.
        factories.put(ProviderType.GITEA, new GiteaProviderFactory());
        factories.put(ProviderType.GITHUB, new GitHubProviderFactory());
        for (ActionsProviderFactory factory : EP.getExtensionList()) {
            factories.put(factory.providerType(), factory);
        }
    }

    public static @NotNull ProviderRegistry getInstance() {
        return ApplicationManager.getApplication().getService(ProviderRegistry.class);
    }

    public @NotNull ActionsProviderFactory factory(@NotNull ProviderType type) {
        ActionsProviderFactory factory = factories.get(type);
        if (factory == null) {
            throw new IllegalStateException("No ActionsProviderFactory registered for " + type);
        }
        return factory;
    }

    public @NotNull ActionsProvider create(@NotNull ActionsConnection connection,
                                           @NotNull String token) {
        return factory(connection.providerType()).create(connection, token);
    }
}
