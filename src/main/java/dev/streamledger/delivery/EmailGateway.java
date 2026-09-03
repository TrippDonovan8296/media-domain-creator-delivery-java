package dev.streamledger.delivery;

import java.util.UUID;

public interface EmailGateway {
    String domainStatus(String domain);
    String send(String to, String subject, String html, String from, UUID deliveryId);
}
