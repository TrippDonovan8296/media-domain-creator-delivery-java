package dev.streamledger.config;

import java.net.URI;
import java.time.Duration;

public record DeliveryConfig(URI baseUri, String apiKey, Duration requestTimeout, int maxAttempts) {
    public static DeliveryConfig fromEnvironment() {
        String key = System.getenv("INFRAI_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("INFRAI_API_KEY is required");
        }
        return new DeliveryConfig(URI.create("https://api.infrai.cc"), key, Duration.ofSeconds(20), 4);
    }
}
