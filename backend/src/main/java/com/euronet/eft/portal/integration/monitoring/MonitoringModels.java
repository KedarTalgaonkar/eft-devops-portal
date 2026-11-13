package com.euronet.eft.portal.integration.monitoring;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class MonitoringModels {

    private MonitoringModels() {
    }

    /** A Grafana dashboard (from /api/search?type=dash-db). */
    public record GrafanaDashboard(String uid, String title, String folder, List<String> tags, String url) {
    }

    /** A Prometheus scrape target (from /api/v1/targets). */
    public record ScrapeTarget(String job, String instance, String health, Instant lastScrape,
                               double scrapeDurationSeconds, String lastError) {
    }

    /** A firing or pending alert (from Grafana / Alertmanager). */
    public record AlertInfo(String name, String severity, String state, String summary, String source,
                            Instant startsAt, Map<String, String> labels) {
    }

    /** One infrastructure metric for the summary cards (from a Prometheus instant query). */
    public record MetricSample(String name, String instance, double value, String unit) {
    }
}
