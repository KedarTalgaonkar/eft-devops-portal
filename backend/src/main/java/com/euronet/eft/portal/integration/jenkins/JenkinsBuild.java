package com.euronet.eft.portal.integration.jenkins;

import java.time.Duration;
import java.time.Instant;

/**
 * One Jenkins build as the portal needs it.
 *
 * @param deployment true when the build deployed to its environment (the deploy stage ran successfully)
 * @param version    artifact version that was built or deployed, when the job publishes one
 */
public record JenkinsBuild(
        String job,
        int number,
        BuildResult result,
        Instant startedAt,
        Duration duration,
        String triggeredBy,
        String version,
        boolean deployment,
        String url) {
}
