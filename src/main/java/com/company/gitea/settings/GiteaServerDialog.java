package com.company.gitea.settings;

import com.company.gitea.model.ActionsConnection;
import com.company.gitea.model.ProviderType;
import com.company.gitea.provider.ActionsProviderFactory;
import com.company.gitea.provider.ProviderRegistry;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.JBIntSpinner;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import java.awt.Component;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Add/edit dialog for one CI/CD connection. The token is intentionally not loaded into
 * the UI: an empty field means "keep the token stored in PasswordSafe".
 * <p>
 * Only Gitea is selectable in this stage. The provider selector is already part of the
 * dialog so GitHub can be enabled later without redesigning the settings UI.
 */
public final class GiteaServerDialog extends DialogWrapper {

    private final ActionsConnection existing;
    private final String connectionId;
    private JComboBox<ProviderType> providerTypeCombo;
    private JBTextField nameField;
    private JBTextField urlField;
    private JBTextField apiUrlField;
    private JPasswordField tokenField;
    private JBIntSpinner timeoutSpinner;
    private JBTextArea repositoriesArea;

    public GiteaServerDialog(@Nullable Component parent, @Nullable ActionsConnection existing) {
        super(parent, true);
        this.existing = existing;
        this.connectionId = existing == null ? UUID.randomUUID().toString() : existing.id();
        setTitle(existing == null ? "Add Connection" : "Edit Connection");
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        providerTypeCombo = new JComboBox<>(new ProviderType[]{ProviderType.GITEA});
        if (existing != null) {
            providerTypeCombo.setSelectedItem(existing.providerType());
        }

        nameField = new JBTextField(existing == null ? "" : existing.name());
        nameField.getEmptyText().setText("Optional display name");

        urlField = new JBTextField(existing == null ? "" : existing.serverUrl());
        urlField.getEmptyText().setText("https://gitea.example.com");

        apiUrlField = new JBTextField(existing == null || existing.apiUrl() == null ? "" : existing.apiUrl());
        apiUrlField.getEmptyText().setText("Optional; derived automatically");

        tokenField = new JPasswordField();
        timeoutSpinner = new JBIntSpinner(
                existing == null ? 30 : existing.safeTimeoutSeconds(),
                5,
                300
        );

        repositoriesArea = new JBTextArea(
                String.join("\n", existing == null ? List.of() : existing.repositories()),
                5,
                40
        );
        repositoriesArea.setLineWrap(false);
        JBScrollPane repositoriesScroll = new JBScrollPane(repositoriesArea);

        String tokenHint = existing == null
                ? "A token is required. It is stored in IntelliJ PasswordSafe, not in XML."
                : "Leave empty to keep the current token. A non-empty value replaces it.";

        JPanel panel = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("Provider:"), providerTypeCombo, 1, false)
                .addLabeledComponent(new JBLabel("Name:"), nameField, 1, false)
                .addLabeledComponent(new JBLabel("Server URL:"), urlField, 1, false)
                .addLabeledComponent(new JBLabel("API URL:"), apiUrlField, 1, false)
                .addLabeledComponent(new JBLabel("Access token:"), tokenField, 1, false)
                .addComponent(new JBLabel("<html><font color='gray'>" + tokenHint + "</font></html>"))
                .addLabeledComponent(new JBLabel("Request timeout (seconds):"), timeoutSpinner, 1, false)
                .addLabeledComponent(
                        new JBLabel("<html>Repositories:<br><font color='gray'>owner/name, one per line.<br>Empty = all accessible repositories.</font></html>"),
                        repositoriesScroll,
                        1,
                        false
                )
                .getPanel();
        panel.setBorder(JBUI.Borders.empty(10));
        return panel;
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        ProviderType providerType = getProviderType();
        ActionsProviderFactory factory;
        try {
            factory = ProviderRegistry.getInstance().factory(providerType);
        } catch (RuntimeException e) {
            return new ValidationInfo("Provider " + providerType.displayName() + " is not available", providerTypeCombo);
        }

        String url = factory.normalizeServerUrl(urlField.getText());
        if (url.isBlank()) {
            return new ValidationInfo("Server URL is required", urlField);
        }
        if (!isValidHttpUrl(url)) {
            return new ValidationInfo("Enter a valid http(s) URL", urlField);
        }

        String apiUrl = apiUrlField.getText().trim();
        if (!apiUrl.isEmpty() && !isValidHttpUrl(apiUrl)) {
            return new ValidationInfo("Enter a valid http(s) API URL", apiUrlField);
        }

        if (existing == null && getToken().isBlank()) {
            return new ValidationInfo("Access token is required for a new connection", tokenField);
        }

        List<String> repositories = parseRepositories();
        for (String repository : repositories) {
            if (!isValidRepository(repository)) {
                return new ValidationInfo("Invalid repository \"" + repository + "\"; expected owner/name", repositoriesArea);
            }
        }
        return null;
    }

    public @NotNull ActionsConnection getServer() {
        ProviderType providerType = getProviderType();
        String url = normalizeServerUrl(providerType);
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            name = hostName(providerType, url);
        }
        String apiUrl = apiUrlField.getText().trim();
        return new ActionsConnection(
                connectionId,
                providerType,
                name,
                url,
                apiUrl.isEmpty() ? null : apiUrl,
                timeoutSpinner.getNumber(),
                parseRepositories()
        );
    }

    public @NotNull String getToken() {
        return new String(tokenField.getPassword()).trim();
    }

    public boolean isNewServer() {
        return existing == null;
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return urlField;
    }

    private @NotNull ProviderType getProviderType() {
        Object selected = providerTypeCombo == null ? null : providerTypeCombo.getSelectedItem();
        return selected instanceof ProviderType type ? type : ProviderType.GITEA;
    }

    private @NotNull String normalizeServerUrl(@NotNull ProviderType providerType) {
        String raw = urlField.getText().trim();
        try {
            return ProviderRegistry.getInstance().factory(providerType).normalizeServerUrl(raw);
        } catch (RuntimeException e) {
            return raw;
        }
    }

    private @NotNull List<String> parseRepositories() {
        Set<String> result = new LinkedHashSet<>();
        for (String line : repositoriesArea.getText().split("\\R")) {
            String value = line.trim();
            if (!value.isEmpty()) {
                result.add(value);
            }
        }
        return List.copyOf(new ArrayList<>(result));
    }

    private static boolean isValidHttpUrl(@NotNull String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            return scheme != null
                    && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    && uri.getHost() != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean isValidRepository(@NotNull String value) {
        int slash = value.indexOf('/');
        return slash > 0
                && slash < value.length() - 1
                && value.indexOf('/', slash + 1) < 0
                && value.chars().noneMatch(Character::isWhitespace);
    }

    private static @NotNull String hostName(@NotNull ProviderType providerType, @NotNull String url) {
        String value = url.trim();
        if (value.isEmpty()) {
            return providerType.displayName();
        }
        String candidate = value.startsWith("http://") || value.startsWith("https://")
                ? value
                : "https://" + value;
        try {
            String host = URI.create(candidate).getHost();
            return host == null || host.isBlank() ? value : host;
        } catch (IllegalArgumentException e) {
            return value;
        }
    }
}
