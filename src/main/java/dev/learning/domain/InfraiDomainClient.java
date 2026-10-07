package dev.learning.domain;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

public final class InfraiDomainClient {
    private final HttpClient http;
    private final DomainConfig config;

    public InfraiDomainClient(HttpClient http, DomainConfig config) {
        this.http = http;
        this.config = config;
    }

    public Map<String, String> getZone(String domain) {
        return request("GET", "/v1/dns/domain/get?domain=" + encode(domain), null, null);
    }

    public Map<String, String> addZone(String domain, String operationId) {
        return request("POST", "/v1/dns/domain/add", Json.object(Map.of("domain", domain)), operationId);
    }

    public Map<String, String> upsertRecord(String zoneId, String type, String name,
                                             String content, int ttl, String operationId) {
        return request("PUT", "/v1/dns/record/upsert", Json.object(Map.of(
                "zone_id", zoneId,
                "record_type", type,
                "name", name,
                "content", content,
                "ttl", ttl)), operationId);
    }

    public Map<String, String> verifySendingDomain(String domain, String operationId) {
        return request("POST", "/v1/email/domain/verify",
                Json.object(Map.of("domain", domain)), operationId);
    }

    private Map<String, String> request(String method, String path, String body, String operationId) {
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(config.baseUrl() + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Accept", "application/json");
            if (operationId != null) builder.header("Idempotency-Key", operationId);
            if (body == null) builder.method(method, HttpRequest.BodyPublishers.noBody());
            else builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));

            HttpResponse<String> response;
            try {
                response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            } catch (IOException e) {
                throw new IllegalStateException("Could not reach the API", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Request interrupted", e);
            }

            if (response.statusCode() == 429 && attempt < 3) {
                pause(retryDelayMillis(response, attempt));
                continue;
            }

            String envelope = response.body();
            if (!Json.booleanField(envelope, "ok")) {
                Map<String, String> error = Json.flatObject(envelope, "error");
                throw new InfraiException(error.getOrDefault("code", "API_REJECTED"),
                        error.getOrDefault("message", error.get("hint")), response.statusCode());
            }
            if (response.statusCode() >= 500) {
                throw new IllegalStateException("API transport status " + response.statusCode());
            }
            return Json.flatObject(envelope, "data");
        }
        throw new IllegalStateException("Retry budget exhausted");
    }

    private static long retryDelayMillis(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After")
                .map(value -> {
                    try { return Long.parseLong(value) * 1_000L; }
                    catch (NumberFormatException ignored) { return 500L << attempt; }
                })
                .orElse(500L << attempt);
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Backoff interrupted", e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
