package dev.streamledger.media;

import java.util.UUID;

public record ProcessingJob(UUID jobId, UUID assetId, State state) {
    public enum State { INGESTED, TRANSCODING, READY, REJECTED }
}
