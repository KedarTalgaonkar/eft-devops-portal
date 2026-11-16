package com.euronet.eft.portal.dashboard;

import com.euronet.eft.portal.health.HealthState;
import com.euronet.eft.portal.integration.jenkins.BuildResult;
import com.euronet.eft.portal.pipelines.PipelineDtos.PipelineTotals;

import java.time.Instant;
import java.util.List;

public final class DashboardDtos {

    private DashboardDtos() {
    }

    public enum Rag {GREEN, AMBER, RED}

    public enum Severity {CRITICAL, HIGH, MEDIUM, LOW}

    public record EnvironmentCount(String environment, String name, String category, int applications,
                                   int deployments) {
    }

    public record HealthSummary(int up, int down, int unknown, Instant checkedAt) {
    }

    public record DashboardOverview(
        int applications,
        int deployments,
        int owners,
        int clients,
        List<EnvironmentCount> environments,
        PipelineTotals pipelines,
        HealthSummary services,
        int firingAlerts,
        int failingQualityGates,
        int integrationsDown,
        Instant generatedAt) {
    }

    // ---------- executive attention report ----------

    /**
     * One cell of the environment status grid: an application in one environment.
     */
    public record EnvironmentCell(String environment, int deployments, int up, int down, HealthState health,
                                  BuildResult lastBuildResult, Instant lastBuildAt, Double successRate) {
    }

    public record EnvironmentStatusRow(Long applicationId, String project, String client, String owner,
                                       List<EnvironmentCell> cells) {
    }

    /**
     * RAG status of one area (delivery, service health, code quality, security, operations).
     */
    public record AreaStatus(String key, String label, Rag status, String headline, String metric, String link) {
    }

    /**
     * One thing a support team should look at, with a deep link into the detail view.
     */
    public record AttentionItem(Severity severity, String area, String title, String detail, String owner,
                                String environment, Instant since, String link) {
    }

    public record AttentionReport(Rag overall, List<AreaStatus> areas, List<AttentionItem> items, Instant generatedAt) {
    }
}
