package ru.pvlapvp.ciactionsmonitor.ui;

import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Font;

/**
 * Shared read-only log panel for the Services tool window.
 * <p>
 * Updates replace the whole document (Gitea returns the accumulated log), so lines
 * are never duplicated. The user's scroll position is preserved unless they were
 * already at the bottom.
 */
public final class LogsPanel extends JPanel {

    private final JBLabel titleLabel = new JBLabel("Logs");
    private final JBLabel statusLabel = new JBLabel("Select a job");
    private final JTextArea textArea = new JTextArea();
    private final JBScrollPane scrollPane;

    public LogsPanel() {
        super(new BorderLayout());

        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
        titleLabel.setBorder(JBUI.Borders.empty(4, 8, 0, 8));
        statusLabel.setBorder(JBUI.Borders.empty(0, 8, 4, 8));
        statusLabel.setForeground(UIUtil.getLabelForeground());

        JPanel header = new JPanel(new BorderLayout());
        header.add(titleLabel, BorderLayout.NORTH);
        header.add(statusLabel, BorderLayout.SOUTH);

        textArea.setEditable(false);
        textArea.setLineWrap(false);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, JBUI.scale(12)));
        textArea.setBorder(JBUI.Borders.empty(4));
        scrollPane = new JBScrollPane(textArea);

        add(header, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        showMessage("Logs", "Select a workflow job to load its log.", "Ready");
    }

    public void showLoading(@NotNull String title) {
        titleLabel.setText(title);
        setStatus("Loading log...", false);
        setText("Loading...\n", true);
    }

    public void showLogs(@NotNull String title, @NotNull String text, @NotNull String status) {
        titleLabel.setText(title);
        setStatus(status, false);
        setText(text.isEmpty() ? "(empty log)\n" : text, true);
    }

    public void showMessage(@NotNull String title, @NotNull String message, @NotNull String status) {
        titleLabel.setText(title);
        setStatus(status, false);
        setText(message.endsWith("\n") ? message : message + "\n", true);
    }

    public void showError(@NotNull String title, @NotNull String message, @NotNull String status) {
        titleLabel.setText(title);
        setStatus(status, true);
        setText(message.endsWith("\n") ? message : message + "\n", true);
    }

    public void setStatus(@NotNull String status, boolean error) {
        statusLabel.setText(status);
        statusLabel.setForeground(error ? JBColor.RED : UIUtil.getLabelForeground());
    }

    public void clear() {
        titleLabel.setText("Logs");
        setStatus("Select a job", false);
        textArea.setText("");
    }

    public boolean hasText() {
        return !textArea.getText().isBlank();
    }

    private void setText(@NotNull String text, boolean preserveScroll) {
        JScrollBar bar = scrollPane.getVerticalScrollBar();
        int oldValue = bar.getValue();
        boolean wasAtBottom = oldValue + bar.getVisibleAmount() >= bar.getMaximum() - 8;

        textArea.setText(text);

        if (!preserveScroll || wasAtBottom) {
            textArea.setCaretPosition(textArea.getDocument().getLength());
        } else {
            SwingUtilities.invokeLater(() -> bar.setValue(Math.min(oldValue, Math.max(0, bar.getMaximum() - bar.getVisibleAmount()))));
        }
    }
}
