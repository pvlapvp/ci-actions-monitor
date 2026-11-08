package com.company.gitea.provider;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;

/**
 * Streaming job log. The caller owns the stream and must close it, which also aborts
 * the underlying HTTP request.
 */
public interface JobLogStream extends AutoCloseable {

    @NotNull InputStream stream();

    /** Content length if known, otherwise {@code -1}. */
    long contentLength();

    @Nullable String contentType();

    @Override
    void close() throws IOException;
}
