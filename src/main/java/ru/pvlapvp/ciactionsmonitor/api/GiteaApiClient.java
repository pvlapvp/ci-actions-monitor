package ru.pvlapvp.ciactionsmonitor.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import ru.pvlapvp.ciactionsmonitor.provider.ActionsApiException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * HTTP client for the Gitea 1.27 REST API.
 * <p>
 * The client is immutable and thread-safe. It never includes the token in exception
 * messages or logs. Network calls are blocking and must be executed on a background thread.
 */
public final class GiteaApiClient {

    private static final Gson GSON = new Gson();
    private static final int MAX_ERROR_BODY = 400;

    private final HttpClient httpClient;
    private final String apiBaseUrl;
    private final String token;
    private final Duration requestTimeout;

    public GiteaApiClient(@NotNull String serverUrl, @NotNull String token, int timeoutSeconds) {
        this(serverUrl, null, token, timeoutSeconds);
    }

    public GiteaApiClient(@NotNull String serverUrl,
                          @Nullable String apiUrl,
                          @NotNull String token,
                          int timeoutSeconds) {
        String normalizedUrl = normalizeServerUrl(serverUrl);
        if (normalizedUrl.isBlank()) {
            throw new IllegalArgumentException("Gitea server URL is empty");
        }

        String baseUrl = apiUrl == null || apiUrl.isBlank()
                ? normalizedUrl + "/api/v1"
                : apiUrl.trim();
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        this.apiBaseUrl = baseUrl;
        this.token = token.trim();
        this.requestTimeout = Duration.ofSeconds(Math.max(5, timeoutSeconds));
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(requestTimeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        try {
            URI.create(apiBaseUrl);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid Gitea API URL: " + apiBaseUrl, e);
        }
    }

    public @NotNull String getApiBaseUrl() {
        return apiBaseUrl;
    }

    /**
     * Performs a GET request and returns a JSON model.
     */
    public <T> T getJson(@NotNull String path, @NotNull Type type) throws IOException {
        String body = execute(path, "application/json");
        try {
            return GSON.fromJson(body, type);
        } catch (JsonSyntaxException | IllegalStateException e) {
            throw new IOException("Cannot parse JSON response from Gitea for " + path, e);
        }
    }

    public <T> T getJson(@NotNull String path, @NotNull Class<T> type) throws IOException {
        return getJson(path, (Type) type);
    }

    public JsonObject getJsonObject(@NotNull String path) throws IOException {
        String body = execute(path, "application/json");
        try {
            return JsonParser.parseString(body).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IOException("Cannot parse JSON response from Gitea for " + path, e);
        }
    }

    /**
     * Opens a text resource as a stream. The caller owns the returned stream and must
     * close it; closing the stream also releases the HTTP connection.
     */
    public @NotNull InputStream openTextStream(@NotNull String path) throws IOException {
        return executeStream(path, "text/plain, */*;q=0.8");
    }

    public @NotNull String getVersion() throws IOException {
        JsonObject json = getJsonObject("/version");
        return json.has("version") ? json.get("version").getAsString() : "unknown";
    }

    private String execute(@NotNull String path, @NotNull String accept) throws IOException {
        checkInterrupted();

        String normalizedPath = normalizePath(path);
        URI uri = createUri(normalizedPath);

        try {
            HttpResponse<String> response = httpClient.send(
                    newRequest(uri, accept),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            checkInterrupted();

            int status = response.statusCode();
            if (status >= 400) {
                throw createApiException(status, normalizedPath, response.body());
            }
            return response.body() == null ? "" : response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Gitea request was cancelled", e);
        } catch (HttpTimeoutException e) {
            throw new IOException(
                    "Gitea request timed out after " + requestTimeout.toSeconds() + " seconds: " + normalizedPath,
                    e
            );
        }
    }

    private InputStream executeStream(@NotNull String path, @NotNull String accept) throws IOException {
        checkInterrupted();

        String normalizedPath = normalizePath(path);
        URI uri = createUri(normalizedPath);

        try {
            HttpResponse<InputStream> response = httpClient.send(
                    newRequest(uri, accept),
                    HttpResponse.BodyHandlers.ofInputStream()
            );

            try {
                checkInterrupted();
            } catch (IOException e) {
                closeQuietly(response.body());
                throw e;
            }

            int status = response.statusCode();
            if (status >= 400) {
                try (InputStream body = response.body()) {
                    throw createApiException(status, normalizedPath, readErrorBody(body));
                }
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Gitea request was cancelled", e);
        } catch (HttpTimeoutException e) {
            throw new IOException(
                    "Gitea request timed out after " + requestTimeout.toSeconds() + " seconds: " + normalizedPath,
                    e
            );
        }
    }

    private HttpRequest newRequest(@NotNull URI uri, @NotNull String accept) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Accept", accept)
                .GET();
        if (!token.isBlank()) {
            builder.header("Authorization", "token " + token);
        }
        return builder.build();
    }

    private @NotNull String normalizePath(@NotNull String path) {
        return path.startsWith("/") ? path : "/" + path;
    }

    private @NotNull URI createUri(@NotNull String normalizedPath) throws IOException {
        try {
            return URI.create(apiBaseUrl + normalizedPath);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid Gitea request URL: " + normalizedPath, e);
        }
    }

    private static @NotNull String readErrorBody(@NotNull InputStream stream) {
        try {
            byte[] bytes = stream.readNBytes(MAX_ERROR_BODY + 1);
            int length = Math.min(bytes.length, MAX_ERROR_BODY);
            String text = new String(bytes, 0, length, StandardCharsets.UTF_8);
            return bytes.length > MAX_ERROR_BODY ? text + "..." : text;
        } catch (IOException e) {
            return "";
        }
    }

    private static void closeQuietly(@NotNull InputStream stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
            // Best effort: the request is already failing.
        }
    }

    private static void checkInterrupted() throws IOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new IOException("Gitea request was cancelled");
        }
    }

    private static @NotNull GiteaApiException createApiException(int status, String path, @Nullable String body) {
        if (status == 401) {
            return new GiteaApiException(
                    status,
                    ActionsApiException.Kind.AUTHENTICATION,
                    "Authentication failed (HTTP 401). Check the access token in Settings | Tools | CI Actions Monitor."
            );
        }
        if (status == 403) {
            return new GiteaApiException(
                    status,
                    ActionsApiException.Kind.AUTHENTICATION,
                    "Access denied (HTTP 403). The token may lack read:repository/read:user permissions."
            );
        }
        if (status == 404) {
            return new GiteaApiException(
                    status,
                    ActionsApiException.Kind.NOT_FOUND,
                    "Not found (HTTP 404): " + path
                            + ". The repository, workflow, run or job may not exist; job logs may not be available yet."
            );
        }

        String detail = body == null ? "" : body.strip();
        if (detail.length() > MAX_ERROR_BODY) {
            detail = detail.substring(0, MAX_ERROR_BODY) + "...";
        }
        String message = "Gitea API request failed (HTTP " + status + "): " + path;
        if (!detail.isEmpty()) {
            message += " — " + detail;
        }
        return new GiteaApiException(status, ActionsApiException.Kind.OTHER, message);
    }

    /**
     * Accepts URLs with or without a scheme and with an optional {@code /api/v1} suffix.
     */
    public static @NotNull String normalizeServerUrl(@NotNull String serverUrl) {
        String url = serverUrl.trim();
        if (url.isEmpty()) {
            return "";
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (url.endsWith("/api/v1")) {
            url = url.substring(0, url.length() - "/api/v1".length());
        }
        return url;
    }
}
