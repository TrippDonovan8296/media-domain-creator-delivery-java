package dev.streamledger.client;

import dev.streamledger.config.DeliveryConfig;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InfraiEmailClient {
    private static final Pattern OK = Pattern.compile("\\\"ok\\\"\\s*:\\s*(true|false)");
    private static final Pattern CODE = Pattern.compile("\\\"code\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern DETAIL = Pattern.compile("\\\"(?:message|hint)\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern STATUS = Pattern.compile("\\\"verification\\\"\\s*:\\s*\\{.*?\\\"status\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"", Pattern.DOTALL);
    private static final Pattern MESSAGE_ID = Pattern.compile("\\\"message_id\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    private final DeliveryConfig config;
    private final HttpClient http;

    public InfraiEmailClient(DeliveryConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(config.requestTimeout()).build());
    }

    InfraiEmailClient(DeliveryConfig config, HttpClient http) {
        this.config = config;
        this.http = http;
    }

    // Canonical calls: infrai.email.domain.verify, infrai.email.domain.get, infrai.email.send.
    public String verifyDomain(String domain) {
        String idempotencyKey = "domain-verify:" + domain;
        return write("/v1/email/domain/verify", "{\"domain\":\"" + json(domain)
                + "\",\"idempotency_key\":\"" + json(idempotencyKey) + "\"}", idempotencyKey);
    }

    public String domainStatus(String domain) {
        String encoded = URLEncoder.encode(domain, StandardCharsets.UTF_8).replace("+", "%20");
        String data = request("GET", "/v1/email/domain/get/" + encoded, null, null);
        return capture(STATUS, data, "verification.status");
    }

    public String sendCreatorDelivery(String to, String subject, String html, String from, UUID deliveryId) {
        String idempotencyKey = "creator-delivery:" + deliveryId;
        String body = "{\"to\":\"" + json(to) + "\",\"subject\":\"" + json(subject)
                + "\",\"html\":\"" + json(html) + "\",\"from\":\"" + json(from)
                + "\",\"idempotency_key\":\"" + json(idempotencyKey) + "\"}";
        String data = write("/v1/email/send", body, idempotencyKey);
        return capture(MESSAGE_ID, data, "message_id");
    }

    private String write(String path, String body, String idempotencyKey) {
        return request("POST", path, body, idempotencyKey);
    }

    private String request(String method, String path, String body, String idempotencyKey) {
        for (int attempt = 0; attempt < config.maxAttempts(); attempt++) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(resolve(path))
                    .timeout(config.requestTimeout())
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Accept", "application/json");
            if (idempotencyKey != null) {
                builder.header("Content-Type", "application/json").header("Idempotency-Key", idempotencyKey);
            }
            builder.method(method, body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body));
            HttpResponse<String> response = send(builder.build());
            Envelope envelope = decode(response.body(), response.statusCode());
            if (response.statusCode() == 429 && attempt + 1 < config.maxAttempts()) {
                pause(retryDelay(response, attempt));
                continue;
            }
            if (!envelope.ok()) {
                throw new InfraiException(envelope.code(), envelope.detail(), response.statusCode());
            }
            if (response.statusCode() >= 500) {
                throw new IllegalStateException("Remote service response: HTTP " + response.statusCode());
            }
            return envelope.data();
        }
        throw new IllegalStateException("Retry budget exhausted");
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException("Transport error", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Request interrupted", e);
        }
    }

    private Envelope decode(String body, int status) {
        Matcher ok = OK.matcher(body);
        if (!ok.find()) {
            throw new IllegalStateException("Invalid response envelope: HTTP " + status);
        }
        String dataJson = objectField(body, "data");
        return new Envelope(Boolean.parseBoolean(ok.group(1)), dataJson,
                optional(CODE, body, "API_REJECTED"), optional(DETAIL, body, "Request rejected"));
    }

    private Duration retryDelay(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After")
                .filter(value -> value.matches("\\d+"))
                .map(value -> Duration.ofSeconds(Long.parseLong(value)))
                .orElse(Duration.ofMillis(250L << attempt));
    }

    private void pause(Duration delay) {
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Retry interrupted", e);
        }
    }

    private URI resolve(String path) {
        return URI.create(config.baseUri().toString() + path);
    }

    private static String capture(Pattern pattern, String value, String field) {
        Matcher matcher = pattern.matcher(value);
        if (!matcher.find()) {
            throw new IllegalStateException("Response omitted " + field);
        }
        return matcher.group(1);
    }

    private static String optional(Pattern pattern, String value, String fallback) {
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? matcher.group(1) : fallback;
    }

    private static String objectField(String json, String field) {
        int key = json.indexOf("\"" + field + "\"");
        int start = key < 0 ? -1 : json.indexOf('{', key);
        if (start < 0) return "{}";
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int i = start; i < json.length(); i++) {
            char current = json.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (current == '\\') escaped = true;
                else if (current == '"') quoted = false;
            } else if (current == '"') {
                quoted = true;
            } else if (current == '{') {
                depth++;
            } else if (current == '}' && --depth == 0) {
                return json.substring(start, i + 1);
            }
        }
        throw new IllegalStateException("Invalid data object");
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private record Envelope(boolean ok, String data, String code, String detail) {}
}
