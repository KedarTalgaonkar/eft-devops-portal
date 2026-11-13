package com.euronet.eft.portal.pipelines;

import com.euronet.eft.portal.integration.jenkins.BuildResult;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Executive view of delivery: trends and KPIs compared with the previous period of the same length. */
public final class InsightDtos {

    private InsightDtos() {
    }

    /** Builds started on one day, by result. */
    public record DailyBuilds(LocalDate date, int success, int failed, int aborted, int unstable, int deployments) {
    }

    /** A metric for the current window and the window before it (null when there is no data). */
    public record Kpi(Double current, Double previous) {
    }

    public record EnvironmentSummary(String environment, int pipelines, int totalBuilds, Double successRate,
                                     int redPipelines) {
    }

    /** A pipeline whose builds fail often or whose latest finished build failed. */
    public record ProblemPipeline(Long applicationId, String project, String client, String owner,
                                  String environment, String jenkinsJob, int failed, int totalBuilds,
                                  Double successRate, BuildResult lastResult, Instant lastFailureAt, boolean red) {
    }

    public record PipelineInsights(
            int days,
            int pipelines,
            int redPipelines,
            Kpi totalBuilds,
            Kpi successRate,
            Kpi deploymentsPerDay,
            Kpi failureRate,
            Kpi meanTimeToRecoverHours,
            Kpi averageBuildMinutes,
            List<EnvironmentSummary> environments,
            List<ProblemPipeline> problems) {
    }
}
