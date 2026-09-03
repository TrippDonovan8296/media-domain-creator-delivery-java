package dev.streamledger.media;

import java.net.URI;
import java.util.UUID;

public record MediaAsset(UUID assetId, String title, URI playbackUrl) {}
