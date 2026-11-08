package com.company.gitea.settings;

import com.company.gitea.api.GiteaApiClient;
import com.company.gitea.model.GiteaServer;
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
 * Add/edit dialog for one Gitea server. The token is intentionally not loaded into
 * the UI: an empty field means "keep the token stored in PasswordSafe".
 */
public final class GiteaServerDialog extends DialogWrapper {

    private final GiteaServer existing;
    private final String serverId;
    private JBTextField nameField;
    private JBTextField urlField;
    private JPasswordField tokenField;
    private JBIntSpinner timeoutSpinner;
    private JBTextArea repositoriesArea;

    public GiteaServerDialog(@Nullable Component parent, @Nullable GiteaServer existing) {
        super(parent, true);
        this.existing = existing;
        this.serverId = existing == null ? UUID.randomUUID().toString() : existing.id();
        setTitle(existing == null ? "Add Gitea Server" : "Edit Gitea Server");
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        nameField = new JBTextField(existing == null ? "" : existing.name());
        nameField.getEmptyText().setText("Optional display name");

        urlField = new JBTextField(existing == null ? "" : existing.url());
        urlField.getEmptyText().setText("https://gitea.example.com");

        tokenField = new JPasswordField();
        timeoutSpinner = new JBIntSpinner(
                existing == null ? 30 : existing.safeTimeoutSeconds(),
                5,
                300
        );

        repositoriesArea = new JBTextArea(String.join("\n", existing == null ? List.of() : existing.repositories()), 5, 40);
        repositoriesArea.setLineWrap(false);
        JBScrollPane repositoriesScroll = new JBScrollPane(repositoriesArea);

        String tokenHint = existing == null
                ? "A token is required. It is stored in IntelliJ PasswordSafe, not in XML."
                : "Leave empty to keep the current token. A non-empty value replaces it.";

        JPanel panel = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("Name:"), nameField, 1, false)
                .addLabeledComponent(new JBLabel("Server URL:"), urlField, 1, false)
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
        String url = GiteaApiClient.normalizeServerUrl(urlField.getText());
        if (url.isBlank()) {
            return new ValidationInfo("Server URL is required", urlField);
        }
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))
                    || uri.getHost() == null) {
                return new ValidationInfo("Enter a valid http(s) URL", urlField);
            }
        } catch (IllegalArgumentException e) {
            return new ValidationInfo("Enter a valid http(s) URL", urlField);
        }

        if (existing == null && getToken().isBlank()) {
            return new ValidationInfo("Access token is required for a new server", tokenField);
        }

        List<String> repositories = parseRepositories();
        for (String repository : repositories) {
            if (!isValidRepository(repository)) {
                return new ValidationInfo("Invalid repository \"" + repository + "\"; expected owner/name", repositoriesArea);
            }
        }
        return null;
    }

    public @NotNull GiteaServer getServer() {
        String url = GiteaApiClient.normalizeServerUrl(urlField.getText());
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            name = hostName(url);
        }
        return new GiteaServer(
                serverId,
                name,
                url,
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

    private static boolean isValidRepository(@NotNull String value) {
        int slash = value.indexOf('/');
        return slash > 0
                && slash < value.length() - 1
                && value.indexOf('/', slash + 1) < 0
                && value.chars().noneMatch(Character::isWhitespace);
    }

    private static @NotNull String hostName(@NotNull String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null || host.isBlank() ? "Gitea" : host;
        } catch (IllegalArgumentException e) {
            return "Gitea";
        }
    }
}
