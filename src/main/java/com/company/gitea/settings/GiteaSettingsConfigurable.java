package com.company.gitea.settings;

import com.company.gitea.model.GiteaServer;
import com.company.gitea.service.GiteaAutoRefreshService;
import com.company.gitea.service.GiteaConnectionService;
import com.company.gitea.service.GiteaCredentialService;
import com.company.gitea.service.GiteaTreeStateService;
import com.company.gitea.ui.GiteaServiceEvents;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Settings page for server/repository configuration.
 * <p>
 * Tokens are never shown. New tokens are written to PasswordSafe asynchronously
 * when the settings are applied.
 */
public final class GiteaSettingsConfigurable implements SearchableConfigurable {

    private final List<GiteaServer> workingServers = new ArrayList<>();
    private final Map<String, String> pendingTokens = new HashMap<>();
    private final Set<String> removedServerIds = new HashSet<>();

    private JBTable table;
    private DefaultTableModel tableModel;
    private JButton editButton;
    private JButton removeButton;
    private JPanel panel;

    @Override
    public @NotNull String getId() {
        return "com.company.gitea.settings";
    }

    @Override
    public @NotNull String getDisplayName() {
        return "Gitea Actions Monitor";
    }

    @Override
    public @Nullable JComponent createComponent() {
        tableModel = new DefaultTableModel(new Object[]{"Name", "Server URL", "Repositories"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JBTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.getSelectionModel().addListSelectionListener(event -> updateButtons());
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && table.getSelectedRow() >= 0) {
                    editSelectedServer();
                }
            }
        });

        JButton addButton = new JButton("Add...");
        addButton.addActionListener(event -> addServer());
        editButton = new JButton("Edit...");
        editButton.addActionListener(event -> editSelectedServer());
        removeButton = new JButton("Remove");
        removeButton.addActionListener(event -> removeSelectedServer());

        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.add(addButton);
        buttons.add(Box.createHorizontalStrut(6));
        buttons.add(editButton);
        buttons.add(Box.createHorizontalStrut(6));
        buttons.add(removeButton);
        buttons.add(Box.createHorizontalGlue());

        JPanel serversPanel = new JPanel(new BorderLayout(0, 6));
        serversPanel.add(new JBScrollPane(table), BorderLayout.CENTER);
        serversPanel.add(buttons, BorderLayout.SOUTH);

        JBLabel tokenNote = new JBLabel(
                "<html>Access tokens are stored in IntelliJ <b>PasswordSafe</b>, not in the plugin XML. "
                        + "A token is bound to a server and removed when the server is deleted.</html>"
        );
        tokenNote.setForeground(JBColor.GRAY);

        panel = FormBuilder.createFormBuilder()
                .addComponent(tokenNote)
                .addComponentFillVertically(serversPanel, 12)
                .getPanel();
        panel.setBorder(JBUI.Borders.empty(12));

        reset();
        return panel;
    }

    @Override
    public boolean isModified() {
        if (tableModel == null) {
            return false;
        }
        return !workingServers.equals(GiteaSettings.getInstance().getServers())
                || !pendingTokens.isEmpty()
                || !removedServerIds.isEmpty();
    }

    @Override
    public void apply() {
        GiteaSettings settings = GiteaSettings.getInstance();
        GiteaCredentialService credentials = GiteaCredentialService.getInstance();
        GiteaConnectionService connections = GiteaConnectionService.getInstance();

        settings.setServers(workingServers);

        for (String serverId : removedServerIds) {
            credentials.deleteToken(serverId);
            connections.invalidate(serverId);
        }
        for (Map.Entry<String, String> entry : pendingTokens.entrySet()) {
            credentials.setToken(entry.getKey(), entry.getValue());
            connections.invalidate(entry.getKey());
        }
        connections.invalidateAll();

        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            project.getService(GiteaTreeStateService.class).resetAll();
            project.getService(GiteaAutoRefreshService.class).clear();
        }
        GiteaServiceEvents.fireResetAll();
        GiteaSettings.saveNow();

        pendingTokens.clear();
        removedServerIds.clear();
    }

    @Override
    public void reset() {
        workingServers.clear();
        workingServers.addAll(GiteaSettings.getInstance().getServers());
        pendingTokens.clear();
        removedServerIds.clear();
        fillTable();
    }

    @Override
    public void disposeUIResources() {
        panel = null;
        table = null;
        tableModel = null;
        editButton = null;
        removeButton = null;
        workingServers.clear();
        pendingTokens.clear();
        removedServerIds.clear();
    }

    private void fillTable() {
        if (tableModel == null) {
            return;
        }
        tableModel.setRowCount(0);
        for (GiteaServer server : workingServers) {
            tableModel.addRow(new Object[]{
                    server.displayName(),
                    server.normalizedUrl(),
                    repositoriesText(server)
            });
        }
        updateButtons();
    }

    private void updateButtons() {
        if (editButton == null || removeButton == null || table == null) {
            return;
        }
        boolean selected = table.getSelectedRow() >= 0 && table.getSelectedRow() < workingServers.size();
        editButton.setEnabled(selected);
        removeButton.setEnabled(selected);
    }

    private void addServer() {
        GiteaServerDialog dialog = new GiteaServerDialog(panel, null);
        if (!dialog.showAndGet()) {
            return;
        }
        GiteaServer server = dialog.getServer();
        workingServers.add(server);
        pendingTokens.put(server.id(), dialog.getToken());
        fillTable();
        selectServer(server.id());
    }

    private void editSelectedServer() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= workingServers.size()) {
            return;
        }
        GiteaServer existing = workingServers.get(row);
        GiteaServerDialog dialog = new GiteaServerDialog(panel, existing);
        if (!dialog.showAndGet()) {
            return;
        }
        GiteaServer updated = dialog.getServer();
        workingServers.set(row, updated);
        String token = dialog.getToken();
        if (!token.isBlank()) {
            pendingTokens.put(updated.id(), token);
        }
        fillTable();
        selectServer(updated.id());
    }

    private void removeSelectedServer() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= workingServers.size()) {
            return;
        }
        GiteaServer server = workingServers.get(row);
        int answer = Messages.showYesNoDialog(
                panel,
                "Remove server \"" + server.displayName() + "\" and its stored token?",
                "Remove Gitea Server",
                Messages.getQuestionIcon()
        );
        if (answer != Messages.YES) {
            return;
        }
        workingServers.remove(row);
        pendingTokens.remove(server.id());
        removedServerIds.add(server.id());
        fillTable();
    }

    private void selectServer(@NotNull String serverId) {
        for (int row = 0; row < workingServers.size(); row++) {
            if (workingServers.get(row).id().equals(serverId)) {
                table.setRowSelectionInterval(row, row);
                return;
            }
        }
    }

    private static @NotNull String repositoriesText(@NotNull GiteaServer server) {
        if (server.repositories().isEmpty()) {
            return "All accessible";
        }
        return String.join(", ", server.repositories());
    }
}
