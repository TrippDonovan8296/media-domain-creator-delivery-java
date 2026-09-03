package dev.streamledger.delivery;

import dev.streamledger.client.InfraiEmailClient;

import java.util.UUID;

public final class InfraiEmailGateway implements EmailGateway {
    private final InfraiEmailClient client;

    public InfraiEmailGateway(InfraiEmailClient client) {
        this.client = client;
    }

    @Override
    public String domainStatus(String domain) {
        return client.domainStatus(domain);
    }

    @Override
    public String send(String to, String subject, String html, String from, UUID deliveryId) {
        return client.sendCreatorDelivery(to, subject, html, from, deliveryId);
    }
}
