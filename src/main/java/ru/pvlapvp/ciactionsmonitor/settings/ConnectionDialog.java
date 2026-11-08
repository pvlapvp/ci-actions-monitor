package ru.pvlapvp.ciactionsmonitor.settings;

import ru.pvlapvp.ciactionsmonitor.model.ActionsConnection;
import ru.pvlapvp.ciactionsmonitor.model.ProviderType;
import ru.pvlapvp.ciactionsmonitor.provider.ActionsProviderFactory;
import ru.pvlapvp.ciactionsmonitor.provider.ConnectionInfo;
import ru.pvlapvp.ciactionsmonitor.provider.ProviderRegistry;
import ru.pvlapvp.ciactionsmonitor.service.ConnectionService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.JBIntSpinner;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.event.DocumentEvent;
import java.awt.BorderLayout;
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
 * Both Gitea and GitHub are selectable. The dialog can test the current values in the
 * background before they are saved.
 */
public final class ConnectionDialog extends DialogWrapper {

    private final ActionsConnection existing;
    private final String connectionId;
    private JComboBox<ProviderType> providerTypeCombo;
    private JBTextField nameField;
    private JBTextField urlField;
    private JBTextField apiUrlField;
    private JPasswordField tokenField;
    private JBIntSpinner timeoutSpinner;
    private JBTextArea repositoriesArea;
    private JBLabel providerHint;
    private JButton testButton;
    private JBTextArea testResultArea;

    public ConnectionDialog(@Nullable Component parent, @Nullable ActionsConnection existing) {
        super(parent, true);
        this.existing = existing;
        this.connectionId = existing == null ? UUID.randomUUID().toString() : existing.id();
        setTitle(existing == null ? "Add Connection" : "Edit Connection");
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        providerTypeCombo = new JComboBox<>(ProviderType.values());
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
        providerHint = new JBLabel();
        providerTypeCombo.addActionListener(event -> {
            updateProviderHints();
            resetTestResult();
        });
        clearResultOnEdit(urlField);
        clearResultOnEdit(apiUrlField);
        clearResultOnEdit(tokenField);
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

        testButton = new JButton("Test Connection");
        testButton.addActionListener(event -> runConnectionTest());
        testResultArea = new JBTextArea(2, 38);
        testResultArea.setEditable(false);
        testResultArea.setLineWrap(true);
        testResultArea.setWrapStyleWord(true);
        testResultArea.setOpaque(false);
        testResultArea.setBorder(null);
        testResultArea.setFocusable(false);
        testResultArea.setForeground(JBColor.GRAY);
        JPanel testPanel = new JPanel(new BorderLayout(8, 0));
        testPanel.add(testButton, BorderLayout.WEST);
        testPanel.add(testResultArea, BorderLayout.CENTER);

        JPanel panel = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("Provider:"), providerTypeCombo, 1, false)
                .addComponent(providerHint)
                .addLabeledComponent(new JBLabel("Name:"), nameField, 1, false)
                .addLabeledComponent(new JBLabel("Server URL:"), urlField, 1, false)
                .addLabeledComponent(new JBLabel("API URL:"), apiUrlField, 1, false)
                .addLabeledComponent(new JBLabel("Access token:"), tokenField, 1, false)
                .addComponent(new JBLabel("<html><font color='gray'>" + tokenHint + "</font></html>"))
                .addComponent(testPanel)
                .addLabeledComponent(new JBLabel("Request timeout (seconds):"), timeoutSpinner, 1, false)
                .addLabeledComponent(
                        new JBLabel("<html>Repositories:<br><font color='gray'>owner/name, one per line.<br>Empty = all accessible repositories.</font></html>"),
                        repositoriesScroll,
                        1,
                        false
                )
                .getPanel();
        panel.setBorder(JBUI.Borders.empty(10));
        updateProviderHints();
        return panel;
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        ProviderType providerType = getProviderType();
        ActionsProviderFactory factory;
        try {
            factory = ProviderRegistry.getInstance().factory(providerType);
        } catch (RuntimeException e) {
            String message = e.getMessage();
            return new ValidationInfo(
                    message == null || message.isBlank()
                            ? "Provider " + providerType.displayName() + " is not available"
                            : message,
                    providerTypeCombo
            );
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

        try {
            factory.resolveApiUrl(new ActionsConnection(
                    connectionId,
                    providerType,
                    "",
                    url,
                    apiUrl.isEmpty() ? null : apiUrl,
                    30,
                    List.of()
            ));
        } catch (RuntimeException e) {
            String message = e.getMessage();
            return new ValidationInfo(
                    message == null || message.isBlank() ? "Invalid server or API URL" : message,
                    urlField
            );
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

    public @NotNull ActionsConnection getConnection() {
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

    /**
     * Tests the values currently shown in the dialog, without saving them. The token is
     * passed explicitly when the user typed a new one; otherwise the stored token is used.
     */
    private void runConnectionTest() {
        if (testButton == null || testResultArea == null) {
            return;
        }
        ProviderType providerType = getProviderType();
        String url = normalizeServerUrl(providerType);
        if (url.isBlank()) {
            showTestFailure(providerType, "Server URL is required");
            return;
        }
        if (!isValidHttpUrl(url)) {
            showTestFailure(providerType, "Enter a valid http(s) URL");
            return;
        }
        String apiUrl = apiUrlField.getText().trim();
        if (!apiUrl.isEmpty() && !isValidHttpUrl(apiUrl)) {
            showTestFailure(providerType, "Enter a valid http(s) API URL");
            return;
        }
        String token = getToken();
        if (token.isEmpty() && existing == null) {
            showTestFailure(providerType, "Enter an access token to test the connection");
            return;
        }

        ActionsConnection connection = new ActionsConnection(
                connectionId,
                providerType,
                "",
                url,
                apiUrl.isEmpty() ? null : apiUrl,
                timeoutSpinner.getNumber(),
                List.of()
        );
        testButton.setEnabled(false);
        testResultArea.setForeground(JBColor.GRAY);
        testResultArea.setText("Testing connection...");
        testResultArea.setToolTipText(null);

        ProgressManager.getInstance().run(new Task.Backgroundable(null, "Testing Connection", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                ConnectionInfo info = null;
                String error = null;
                try {
                    if (!indicator.isCanceled()) {
                        info = ConnectionService.getInstance().testConnection(
                                connection,
                                token.isEmpty() ? null : token
                        );
                    }
                } catch (Exception e) {
                    error = safeMessage(e);
                }
                ConnectionInfo finalInfo = info;
                String finalError = error;
                ApplicationManager.getApplication().invokeLater(
                        () -> showTestResult(providerType, finalInfo, finalError)
                );
            }
        });
    }

    private void showTestResult(@NotNull ProviderType providerType,
                                @Nullable ConnectionInfo info,
                                @Nullable String error) {
        if (testButton == null || testResultArea == null || !testButton.isShowing()) {
            return;
        }
        testButton.setEnabled(true);

        if (info == null && error == null) {
            resetTestResult();
            return;
        }
        if (info != null) {
            StringBuilder text = new StringBuilder("Connected: ").append(info.version());
            if (info.account() != null && !info.account().isBlank()) {
                text.append(" (").append(info.account()).append(')');
            }
            testResultArea.setForeground(JBColor.GREEN.darker());
            testResultArea.setText(text.toString());
            testResultArea.setToolTipText(info.apiUrl());
            return;
        }

        StringBuilder text = new StringBuilder("Connection failed: ")
                .append(error == null ? "unknown error" : error);
        if (providerType == ProviderType.GITHUB) {
            text.append(" Check token permissions: Actions — Read, Metadata — Read, Contents — No access.");
        }
        testResultArea.setForeground(JBColor.RED);
        testResultArea.setText(text.toString());
        testResultArea.setToolTipText(text.toString());
    }

    private void showTestFailure(@NotNull ProviderType providerType, @NotNull String message) {
        if (testButton == null) {
            return;
        }
        testButton.setEnabled(true);
        showTestResult(providerType, null, message);
    }

    private void resetTestResult() {
        if (testResultArea == null) {
            return;
        }
        testResultArea.setText("");
        testResultArea.setToolTipText(null);
        testResultArea.setForeground(JBColor.GRAY);
    }

    private void clearResultOnEdit(@NotNull javax.swing.text.JTextComponent component) {
        component.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            public void textChanged(@NotNull DocumentEvent event) {
                resetTestResult();
            }
        });
    }

    private static @NotNull String safeMessage(@NotNull Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    private @NotNull ProviderType getProviderType() {
        Object selected = providerTypeCombo == null ? null : providerTypeCombo.getSelectedItem();
        return selected instanceof ProviderType type ? type : ProviderType.GITEA;
    }

    private void updateProviderHints() {
        if (urlField == null || providerHint == null) {
            return;
        }
        ProviderType providerType = getProviderType();
        if (providerType == ProviderType.GITHUB) {
            urlField.getEmptyText().setText("https://github.com or https://ghe.example.com");
            providerHint.setText("<html><font color='gray'>Fine-grained token: Actions — Read-only, "
                    + "Metadata — Read-only, Contents — No access.</font></html>");
        } else {
            urlField.getEmptyText().setText("https://gitea.example.com");
            providerHint.setText("<html><font color='gray'>Token scopes: read:repository and read:user.</font></html>");
        }
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
