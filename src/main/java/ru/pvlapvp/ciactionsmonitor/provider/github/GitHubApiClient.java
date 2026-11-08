package ru.pvlapvp.ciactionsmonitor.provider.github;

import ru.pvlapvp.ciactionsmonitor.provider.ActionsApiException;
import ru.pvlapvp.ciactionsmonitor.provider.InputStreamJobLogStream;
import ru.pvlapvp.ciactionsmonitor.provider.JobLogStream;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * HTTP client for the GitHub REST API.
 * <p>
 * The client is immutable and thread-safe. It never includes the token in exception
 * messages or logs. Job logs are downloaded through GitHub's redirect to a signed
 * URL; the token is deliberately not forwarded to that URL.
 */
public final class GitHubApiClient {

    private static final Gson GSON = new Gson();
    private static final int MAX_ERROR_BODY = 400;
    private static final int MAX_LOG_REDIRECTS = 5;
    private static final String USER_AGENT = "IntelliJ-Gitea-Actions-Monitor";

    private final HttpClient httpClient;
    private final GitHubEndpoint endpoint;
    private final String token;
    private final Duration requestTimeout;

    public GitHubApiClient(@NotNull GitHubEndpoint endpoint,
                           @NotNull String token,
                           int timeoutSeconds) {
        this.endpoint = endpoint;
        this.token = token == null ? "" : token.trim();
        this.requestTimeout = Duration.ofSeconds(Math.max(5, timeoutSeconds));
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(requestTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public @NotNull GitHubEndpoint getEndpoint() {
        return endpoint;
    }

    public @NotNull String getApiBaseUrl() {
        return endpoint.apiBaseUri().toString();
    }

    public <T> T getJson(@NotNull String path, @NotNull Type type) throws IOException {
        String body = execute(path);
        try {
            return GSON.fromJson(body, type);
        } catch (JsonSyntaxException | IllegalStateException e) {
            throw new IOException("Cannot parse JSON response from GitHub for " + path, e);
        }
    }

    public <T> T getJson(@NotNull String path, @NotNull Class<T> type) throws IOException {
        return getJson(path, (Type) type);
    }

    public @NotNull JsonObject getJsonObject(@NotNull String path) throws IOException {
        String body = execute(path);
        try {
            return JsonParser.parseString(body).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IOException("Cannot parse JSON response from GitHub for " + path, e);
        }
    }

    /**
     * Opens a job-log download. GitHub first responds with a redirect to a signed URL;
     * the redirect is followed manually so the Authorization header is never sent to
     * the storage host and signed URLs never reach application logs.
     */
    public @NotNull JobLogStream openJobLogStream(@NotNull String path) throws IOException {
        checkInterrupted();

        URI uri = createUri(path);
        boolean includeAuth = true;

        for (int redirects = 0; ; redirects++) {
            HttpRequest request = newRequest(uri, "text/plain, */*;q=0.8", includeAuth);
            HttpResponse<InputStream> response = sendStream(request, path);
            int status = response.statusCode();

            if (isRedirect(status)) {
                String location = response.headers().firstValue("Location").orElse(null);
                closeQuietly(response.body());
                if (location == null || location.isBlank() || redirects >= MAX_LOG_REDIRECTS) {
                    throw new GitHubApiException(
                            status,
                            ActionsApiException.Kind.OTHER,
                            "GitHub log redirect failed: too many redirects or missing Location header",
                            null
                    );
                }
                uri = resolveRedirect(uri, location);
                includeAuth = false;
                continue;
            }

            if (status >= 400) {
                try (InputStream body = response.body()) {
                    throw createException(status, path, response.headers(), readErrorBody(body));
                }
            }

            return new InputStreamJobLogStream(
                    response.body(),
                    parseContentLength(response.headers()),
                    response.headers().firstValue("Content-Type").orElse(null)
            );
        }
    }

    private String execute(@NotNull String path) throws IOException {
        checkInterrupted();
        URI uri = createUri(path);
        HttpRequest request = newRequest(uri, "application/vnd.github+json", true);
        HttpResponse<String> response = sendString(request, path);

        int status = response.statusCode();
        if (status >= 400) {
            throw createException(status, path, response.headers(), response.body());
        }
        return response.body() == null ? "" : response.body();
    }

    private HttpResponse<String> sendString(@NotNull HttpRequest request,
                                            @NotNull String path) throws IOException {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("GitHub request was cancelled", e);
        } catch (HttpTimeoutException e) {
            throw new IOException(
                    "GitHub request timed out after " + requestTimeout.toSeconds() + " seconds: " + path,
                    e
            );
        }
    }

    private HttpResponse<InputStream> sendStream(@NotNull HttpRequest request,
                                                 @NotNull String path) throws IOException {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("GitHub request was cancelled", e);
        } catch (HttpTimeoutException e) {
            throw new IOException(
                    "GitHub request timed out after " + requestTimeout.toSeconds() + " seconds: " + path,
                    e
            );
        }
    }

    private @NotNull HttpRequest newRequest(@NotNull URI uri,
                                            @NotNull String accept,
                                            boolean includeAuth) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Accept", accept)
                .header("User-Agent", USER_AGENT)
                .GET();
        if (includeAuth && endpoint.apiVersion() != null && !endpoint.apiVersion().isBlank()) {
            builder.header("X-GitHub-Api-Version", endpoint.apiVersion());
        }
        if (includeAuth && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder.build();
    }

    private @NotNull URI createUri(@NotNull String path) throws IOException {
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        try {
            return URI.create(endpoint.apiBaseUri().toString() + normalizedPath);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid GitHub request URL: " + normalizedPath, e);
        }
    }

    private static @NotNull URI resolveRedirect(@NotNull URI current, @NotNull String location) throws IOException {
        try {
            URI resolved = current.resolve(location);
            String scheme = resolved.getScheme();
            if (scheme == null
                    || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))
                    || resolved.getHost() == null) {
                throw new IOException("GitHub log redirect returned an unsupported URL");
            }
            return resolved;
        } catch (IllegalArgumentException e) {
            throw new IOException("GitHub log redirect returned an invalid URL", e);
        }
    }

    private static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private static @NotNull GitHubApiException createException(int status,
                                                               @NotNull String path,
                                                               @NotNull HttpHeaders headers,
                                                               @Nullable String body) {
        if (status == 401) {
            return new GitHubApiException(
                    status,
                    ActionsApiException.Kind.AUTHENTICATION,
                    "Authentication failed (HTTP 401). Check the GitHub token in Settings | Tools | CI Actions Monitor.",
                    null
            );
        }
        if (status == 404) {
            return new GitHubApiException(
                    status,
                    ActionsApiException.Kind.NOT_FOUND,
                    "Not found (HTTP 404): " + path
                            + ". The repository, workflow, run or job may not exist; job logs may not be available yet.",
                    null
            );
        }
        if (status == 410) {
            return new GitHubApiException(
                    status,
                    ActionsApiException.Kind.NOT_FOUND,
                    "Job log is no longer available (HTTP 410): " + path,
                    null
            );
        }
        if (status == 429 || (status == 403 && isRateLimited(headers, body))) {
            return new GitHubApiException(
                    status,
                    ActionsApiException.Kind.RATE_LIMITED,
                    "GitHub API rate limit exceeded (HTTP " + status + "): " + path,
                    parseRetryAfter(headers)
            );
        }
        if (status == 403) {
            return new GitHubApiException(
                    status,
                    ActionsApiException.Kind.AUTHENTICATION,
                    "Access denied (HTTP 403). Check that the token has Actions: Read and Metadata: Read permissions "
                            + "(Contents is not required).",
                    null
            );
        }

        String detail = body == null ? "" : body.strip();
        if (detail.length() > MAX_ERROR_BODY) {
            detail = detail.substring(0, MAX_ERROR_BODY) + "...";
        }
        String message = "GitHub API request failed (HTTP " + status + "): " + path;
        if (!detail.isEmpty()) {
            message += " — " + detail;
        }
        return new GitHubApiException(status, ActionsApiException.Kind.OTHER, message, null);
    }

    private static boolean isRateLimited(@NotNull HttpHeaders headers, @Nullable String body) {
        String remaining = headers.firstValue("X-RateLimit-Remaining").orElse("");
        if ("0".equals(remaining.trim())) {
            return true;
        }
        if (headers.firstValue("Retry-After").isPresent()) {
            return true;
        }
        return body != null && body.toLowerCase(Locale.ROOT).contains("rate limit");
    }

    private static @Nullable Instant parseRetryAfter(@NotNull HttpHeaders headers) {
        String retryAfter = headers.firstValue("Retry-After").orElse(null);
        if (retryAfter != null && !retryAfter.isBlank()) {
            try {
                return Instant.now().plusSeconds(Math.max(1, Long.parseLong(retryAfter.trim())));
            } catch (NumberFormatException ignored) {
                // Fall back to the rate-limit reset header.
            }
        }
        String reset = headers.firstValue("X-RateLimit-Reset").orElse(null);
        if (reset != null && !reset.isBlank()) {
            try {
                return Instant.ofEpochSecond(Long.parseLong(reset.trim()));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static long parseContentLength(@NotNull HttpHeaders headers) {
        String value = headers.firstValue("Content-Length").orElse(null);
        if (value == null || value.isBlank()) {
            return -1;
        }
        try {
            return Math.max(0, Long.parseLong(value.trim()));
        } catch (NumberFormatException e) {
            return -1;
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
            throw new IOException("GitHub request was cancelled");
        }
    }
}
