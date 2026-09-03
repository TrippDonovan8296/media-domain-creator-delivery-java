package dev.streamledger.delivery;

import dev.streamledger.media.MediaAsset;
import dev.streamledger.media.ProcessingJob;

import java.net.URI;
import java.util.UUID;

public final class CreatorDeliveryServiceTest {
    public static void main(String[] args) {
        UUID assetId = UUID.fromString("3f30f5b2-fab8-45f8-92c7-b11c3dc828d8");
        MediaAsset asset = new MediaAsset(assetId, "Quarterly creator cut",
                URI.create("https://media.example.test/watch/asset-42"));
        ProcessingJob ready = new ProcessingJob(UUID.randomUUID(), assetId, ProcessingJob.State.READY);

        FakeEmail unverified = new FakeEmail("pending");
        var held = new CreatorDeliveryService(unverified).deliver(asset, ready, "creator@example.test",
                "mail.stream.example", UUID.randomUUID());
        require(held.decision() == CreatorDeliveryService.Decision.HELD_FOR_DOMAIN_VERIFICATION,
                "pending domain must hold delivery");
        require(unverified.sendCount == 0, "held delivery must not send");

        FakeEmail verified = new FakeEmail("verified");
        var sent = new CreatorDeliveryService(verified).deliver(asset, ready, "creator@example.test",
                "mail.stream.example", UUID.randomUUID());
        require(sent.decision() == CreatorDeliveryService.Decision.SENT, "verified ready asset must send");
        require("msg_test_42".equals(sent.messageId()), "receipt must retain message_id");
        require(verified.sendCount == 1, "approved delivery sends exactly once");
        System.out.println("PASS: domain verification gates creator delivery");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class FakeEmail implements EmailGateway {
        private final String status;
        private int sendCount;

        private FakeEmail(String status) { this.status = status; }
        public String domainStatus(String domain) { return status; }
        public String send(String to, String subject, String html, String from, UUID deliveryId) {
            sendCount++;
            return "msg_test_42";
        }
    }
}
