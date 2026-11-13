package com.euronet.eft.portal.integration.artifactory;

import java.time.Instant;

public final class ArtifactoryModels {

    private ArtifactoryModels() {
    }

    public record ArtifactRepository(String key, String type, String packageType, String description,
                                     int artifactCount, long sizeBytes, Instant lastUpdated, String url) {
    }

    public record ArtifactInfo(String repository, String path, String name, String version, long sizeBytes,
                               Instant createdAt, String createdBy, int downloads, String sha256) {
    }
}
