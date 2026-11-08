package ru.pvlapvp.ciactionsmonitor.service;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.BooleanSupplier;

/**
 * Reads a job-log stream with a hard byte limit and cooperative cancellation.
 * <p>
 * The reader is separate from {@link LogsService} so the limit and cancellation
 * behavior can be tested without starting an IntelliJ application.
 */
public final class JobLogTextReader {

    private static final int BUFFER_BYTES = 64 * 1024;

    private JobLogTextReader() {
    }

    /**
     * @return the read text and truncation flag, or {@code null} when cancelled
     */
    public static @Nullable Result read(@NotNull InputStream input,
                                        int maxBytes,
                                        @NotNull BooleanSupplier cancelled) throws IOException {
        int limit = Math.max(0, maxBytes);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[Math.min(BUFFER_BYTES, Math.max(1, limit))];
        boolean truncated = false;

        while (true) {
            if (cancelled.getAsBoolean()) {
                return null;
            }
            int read = input.read(chunk);
            if (read < 0) {
                break;
            }
            int remaining = limit - buffer.size();
            if (remaining <= 0) {
                truncated = true;
                break;
            }
            int toWrite = Math.min(read, remaining);
            buffer.write(chunk, 0, toWrite);
            if (toWrite < read) {
                truncated = true;
                break;
            }
            if (buffer.size() >= limit) {
                // The buffer is full. Probe one byte so a log of exactly maxBytes
                // is not reported as truncated.
                if (cancelled.getAsBoolean()) {
                    return null;
                }
                if (input.read() >= 0) {
                    truncated = true;
                }
                break;
            }
        }

        return new Result(buffer.toString(StandardCharsets.UTF_8), truncated);
    }

    public record Result(@NotNull String text, boolean truncated) {
    }
}
