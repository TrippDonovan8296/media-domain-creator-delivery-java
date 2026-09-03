package dev.streamledger.delivery;

import dev.streamledger.media.MediaAsset;
import dev.streamledger.media.ProcessingJob;

import java.util.UUID;

public final class CreatorDeliveryService {
    private final EmailGateway email;

    public CreatorDeliveryService(EmailGateway email) {
        this.email = email;
    }

    public DeliveryReceipt deliver(MediaAsset asset, ProcessingJob job, String creatorEmail,
                                   String sendingDomain, UUID deliveryId) {
        if (!asset.assetId().equals(job.assetId())) {
            return new DeliveryReceipt(Decision.REJECTED_ASSET_MISMATCH, null);
        }
        if (job.state() != ProcessingJob.State.READY) {
            return new DeliveryReceipt(Decision.HELD_FOR_PROCESSING, null);
        }
        if (!"verified".equalsIgnoreCase(email.domainStatus(sendingDomain))) {
            return new DeliveryReceipt(Decision.HELD_FOR_DOMAIN_VERIFICATION, null);
        }
        String html = "<p>Your media is ready: <a href=\"" + asset.playbackUrl()
                + "\">" + escape(asset.title()) + "</a></p>";
        String messageId = email.send(creatorEmail, asset.title() + " is ready", html,
                "delivery@" + sendingDomain, deliveryId);
        return new DeliveryReceipt(Decision.SENT, messageId);
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public enum Decision { SENT, HELD_FOR_PROCESSING, HELD_FOR_DOMAIN_VERIFICATION, REJECTED_ASSET_MISMATCH }
    public record DeliveryReceipt(Decision decision, String messageId) {}
}
