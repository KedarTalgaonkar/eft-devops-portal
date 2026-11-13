package com.euronet.eft.portal.dashboard;

import com.euronet.eft.portal.dashboard.DashboardDtos.AreaStatus;
import com.euronet.eft.portal.dashboard.DashboardDtos.AttentionItem;
import com.euronet.eft.portal.dashboard.DashboardDtos.AttentionReport;
import com.euronet.eft.portal.dashboard.DashboardDtos.Rag;
import com.euronet.eft.portal.dashboard.DashboardDtos.Severity;
import com.euronet.eft.portal.environment.EnvironmentCatalog;
import com.euronet.eft.portal.health.HealthState;
import com.euronet.eft.portal.health.ServiceHealth;
import com.euronet.eft.portal.health.ServiceHealthService;
import com.euronet.eft.portal.integration.monitoring.MonitoringClient;
import com.euronet.eft.portal.integration.monitoring.MonitoringModels.AlertInfo;
import com.euronet.eft.portal.integration.sonar.SonarClient;
import com.euronet.eft.portal.integration.sonar.SonarModels.GateStatus;
import com.euronet.eft.portal.integration.sonar.SonarModels.QualityProject;
import com.euronet.eft.portal.pipelines.InsightDtos.PipelineInsights;
import com.euronet.eft.portal.pipelines.InsightDtos.ProblemPipeline;
import com.euronet.eft.portal.pipelines.PipelineInsightsService;
import com.euronet.eft.portal.sbom.SbomService;
import com.euronet.eft.portal.sbom.SbomService.ProjectRisk;
import com.euronet.eft.portal.sbom.SbomService.SbomSummary;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Builds the executive view: one RAG status per area and a ranked list of concrete issues, each with a
 * deep link into the detail tab where a support engineer can start. Thresholds are in one place below.
 */
@Service
public class AttentionService {

    private static final Logger log = LoggerFactory.getLogger(AttentionService.class);
    private static final int MAX_ITEMS = 60;

    private final PipelineInsightsService pipelines;
    private final ServiceHealthService health;
    private final SonarClient sonar;
    private final SbomService sbom;
    private final MonitoringClient monitoring;
    private final EnvironmentCatalog environments;

    public AttentionService(PipelineInsightsService pipelines, ServiceHealthService health, SonarClient sonar,
                            SbomService sbom, MonitoringClient monitoring, EnvironmentCatalog environments) {
        this.pipelines = pipelines;
        this.health = health;
        this.sonar = sonar;
        this.sbom = sbom;
        this.monitoring = monitoring;
        this.environments = environments;
    }

    public AttentionReport report() {
        List<AreaStatus> areas = new ArrayList<>();
        List<AttentionItem> items = new ArrayList<>();
        Set<String> prodEnvironments = environments.list().stream()
                .filter(e -> "PROD".equals(e.category())).map(e -> e.code()).collect(Collectors.toSet());

        // Delivery (Jenkins)
        PipelineInsights insights = safe(() -> pipelines.insights(null, DashboardService.WINDOW_DAYS), null);
        if (insights != null) {
            Double rate = insights.successRate().current();
            Rag rag = rate == null ? Rag.AMBER : rate >= 85 && insights.redPipelines() == 0 ? Rag.GREEN : rate >= 70 ? Rag.AMBER : Rag.RED;
            areas.add(new AreaStatus("delivery", "Delivery", rag,
                    insights.redPipelines() + " of " + insights.pipelines() + " pipelines failing now",
                    rate == null ? "no builds" : rate + "% build success", "/pipelines"));
            for (ProblemPipeline p : insights.problems()) {
                Severity sev = p.red() ? (prodEnvironments.contains(p.environment()) ? Severity.CRITICAL : Severity.HIGH) : Severity.MEDIUM;
                items.add(new AttentionItem(sev, "Delivery",
                        p.project() + " · " + p.environment() + (p.red() ? " pipeline is failing" : " pipeline is unstable"),
                        p.failed() + " failed of " + p.totalBuilds() + " builds in " + insights.days() + " days · job " + p.jenkinsJob(),
                        p.owner(), p.environment(), p.lastFailureAt(), "/pipelines/all?q=" + encode(p.project())));
            }
        } else {
            areas.add(new AreaStatus("delivery", "Delivery", Rag.AMBER, "Jenkins data unavailable", "—", "/pipelines"));
        }

        // Service health (probes)
        List<ServiceHealth> probes = safe(health::snapshot, List.of());
        long down = probes.stream().filter(p -> p.state() == HealthState.DOWN).count();
        long up = probes.stream().filter(p -> p.state() == HealthState.UP).count();
        double downShare = probes.isEmpty() ? 0 : (double) down / probes.size();
        areas.add(new AreaStatus("services", "Service health",
                down == 0 ? Rag.GREEN : downShare <= 0.05 ? Rag.AMBER : Rag.RED,
                down == 0 ? "All probed services up" : plural(down, "deployment path") + " down",
                up + " / " + probes.size() + " up", "/dashboard/services"));
        probes.stream().filter(p -> p.state() == HealthState.DOWN).forEach(p -> items.add(new AttentionItem(
                prodEnvironments.contains(p.environment()) ? Severity.CRITICAL : Severity.HIGH, "Service health",
                p.project() + " · " + p.environment() + " · " + (p.contextName().isBlank() ? "root" : p.contextName()) + " is down",
                (p.detail() == null ? "" : p.detail()) + (p.url() == null ? "" : " · " + p.url()),
                p.owner(), p.environment(), p.checkedAt(), "/dashboard/services?q=" + encode(p.project()))));

        // Code quality (SonarQube)
        List<QualityProject> quality = safe(sonar::projects, List.of());
        long failingGates = quality.stream().filter(q -> q.qualityGate() == GateStatus.ERROR).count();
        double failingShare = quality.isEmpty() ? 0 : (double) failingGates / quality.size();
        areas.add(new AreaStatus("quality", "Code quality",
                failingGates == 0 ? Rag.GREEN : failingShare <= 0.2 ? Rag.AMBER : Rag.RED,
                failingGates + " of " + quality.size() + " quality gates failing",
                Math.round((quality.size() - failingGates) * 100.0 / Math.max(1, quality.size())) + "% passing", "/quality"));
        quality.stream().filter(q -> q.qualityGate() == GateStatus.ERROR).forEach(q -> items.add(new AttentionItem(
                q.vulnerabilities() > 0 ? Severity.HIGH : Severity.MEDIUM, "Code quality",
                q.name() + " fails its quality gate",
                q.bugs() + " bugs · " + q.vulnerabilities() + " vulnerabilities · coverage " + q.coverage() + "%",
                null, null, q.lastAnalysis(), "/quality/projects?q=" + encode(q.key()))));

        // Security (SBOM)
        SbomSummary risk = safe(sbom::summary, null);
        if (risk == null || risk.projects() == 0) {
            areas.add(new AreaStatus("security", "Security (SBOM)", Rag.AMBER, "No SBOM scans yet", "0 projects scanned", "/sbom"));
        } else {
            areas.add(new AreaStatus("security", "Security (SBOM)",
                    risk.critical() > 0 ? Rag.RED : risk.high() > 0 ? Rag.AMBER : Rag.GREEN,
                    risk.critical() + " critical · " + risk.high() + " high open vulnerabilities",
                    plural(risk.projects(), "project") + " scanned", "/sbom"));
            for (ProjectRisk p : risk.projectRisks()) {
                if (p.critical() > 0 || p.high() > 0) {
                    items.add(new AttentionItem(p.critical() > 0 ? Severity.CRITICAL : Severity.HIGH, "Security",
                            p.projectName() + " " + p.projectVersion() + " has " + p.critical() + " critical / " + p.high() + " high vulnerabilities",
                            p.componentCount() + " components scanned", null, null, p.scannedAt(),
                            "/sbom/scans?scan=" + p.scanId()));
                }
            }
        }

        // Operations (Grafana / Prometheus)
        List<AlertInfo> alerts = safe(monitoring::alerts, List.of());
        List<AlertInfo> firing = alerts.stream().filter(a -> "firing".equalsIgnoreCase(a.state())).toList();
        boolean criticalFiring = firing.stream().anyMatch(a -> "critical".equalsIgnoreCase(a.severity()));
        areas.add(new AreaStatus("operations", "Operations",
                criticalFiring ? Rag.RED : firing.isEmpty() ? Rag.GREEN : Rag.AMBER,
                plural(firing.size(), "alert") + " firing", plural(alerts.size(), "active alert"), "/monitoring"));
        firing.forEach(a -> items.add(new AttentionItem(
                "critical".equalsIgnoreCase(a.severity()) ? Severity.CRITICAL : Severity.MEDIUM, "Operations",
                a.name() + ": " + a.summary(), "Source " + a.source(), null,
                a.labels() == null ? null : a.labels().get("env"), a.startsAt(), "/monitoring/alerts")));

        items.sort(Comparator.comparing(AttentionItem::severity)
                .thenComparing(AttentionItem::since, Comparator.nullsLast(Comparator.reverseOrder())));
        Rag overall = areas.stream().map(AreaStatus::status).max(Comparator.naturalOrder()).orElse(Rag.GREEN);
        return new AttentionReport(overall, areas, items.stream().limit(MAX_ITEMS).toList(), Instant.now());
    }

    private static String plural(long n, String noun) {
        return n + " " + noun + (n == 1 ? "" : "s");
    }

    private static String encode(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static <T> T safe(Supplier<T> supplier, T fallback) {
        try {
            return supplier.get();
        } catch (RuntimeException e) {
            log.warn("Attention report: source unavailable: {}", e.getMessage());
            return fallback;
        }
    }
}
