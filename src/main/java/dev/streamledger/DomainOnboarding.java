package dev.streamledger;

import dev.streamledger.client.InfraiEmailClient;
import dev.streamledger.config.DeliveryConfig;

public final class DomainOnboarding {
    private DomainOnboarding() {}

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: DomainOnboarding <sending-domain>");
        }
        InfraiEmailClient client = new InfraiEmailClient(DeliveryConfig.fromEnvironment());
        String domain = args[0];
        System.out.println("DNS records to publish for " + domain + ":");
        System.out.println(client.verifyDomain(domain));
        System.out.println("Verification status: " + client.domainStatus(domain));
    }
}
