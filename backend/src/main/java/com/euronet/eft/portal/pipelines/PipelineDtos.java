package com.euronet.eft.portal.pipelines;

import com.euronet.eft.portal.integration.jenkins.BuildResult;
import com.euronet.eft.portal.integration.jenkins.JenkinsBuild;

import java.time.Instant;

/**
 * Response shapes of the pipelines API. Environments are codes from /api/environments.
 */
public final class PipelineDtos {

    private PipelineDtos() {
    }

    /**
     * One row of the "EFT Pipeline Metrics" table: a project in one environment.
     */
    public record PipelineMetric(
        Long applicationId,
        String project,
        String client,
        String owner,
        String environment,
        String jenkinsJob,
        int totalBuilds,
        int success,
        int failed,
        int aborted,
        int unstable,
        int inProgress,
        Double successRate,
        BuildView lastBuild,
        String error) {
    }

    public record BuildView(
        int number,
        BuildResult result,
        Instant startedAt,
        long durationSeconds,
        String triggeredBy,
        String version,
        boolean deployment,
        String url) {

        static BuildView of(JenkinsBuild b) {
            return new BuildView(b.number(), b.result(), b.startedAt(),
                b.duration() == null ? 0 : b.duration().toSeconds(),
                b.triggeredBy(), b.version(), b.deployment(), b.url());
        }
    }

    public record DeploymentHistoryEntry(
        Long applicationId,
        String project,
        String client,
        String environment,
        String jenkinsJob,
        int buildNumber,
        String version,
        BuildResult result,
        Instant deployedAt,
        String triggeredBy,
        String url) {
    }

    /**
     * Totals over all projects; used by the dashboard.
     */
    public record PipelineTotals(int projects, int totalBuilds, int success, int failed, int aborted,
                                 int unstable, int inProgress, Double successRate, int days) {
    }
}
