package com.euronet.eft.portal.health;

import java.time.Instant;

/** Result of probing one deployed context (any environment, any platform). */
public record ServiceHealth(
        Long deploymentId,
        Long applicationId,
        String project,
        String client,
        String owner,
        String environment,
        String contextName,
        String url,
        HealthState state,
        Integer httpStatus,
        Long responseTimeMs,
        Instant checkedAt,
        String detail) {
}
