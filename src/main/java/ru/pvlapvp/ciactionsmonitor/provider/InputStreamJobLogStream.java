package ru.pvlapvp.ciactionsmonitor.provider;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;

/**
 * Simple {@link JobLogStream} backed by an {@link InputStream}.
 */
public final class InputStreamJobLogStream implements JobLogStream {

    private final InputStream stream;
    private final long contentLength;
    private final String contentType;

    public InputStreamJobLogStream(@NotNull InputStream stream) {
        this(stream, -1, null);
    }

    public InputStreamJobLogStream(@NotNull InputStream stream,
                                   long contentLength,
                                   @Nullable String contentType) {
        this.stream = stream;
        this.contentLength = contentLength;
        this.contentType = contentType;
    }

    @Override
    public @NotNull InputStream stream() {
        return stream;
    }

    @Override
    public long contentLength() {
        return contentLength;
    }

    @Override
    public @Nullable String contentType() {
        return contentType;
    }

    @Override
    public void close() throws IOException {
        stream.close();
    }
}
