package com.euronet.eft.portal.integration.monitoring;

import com.euronet.eft.portal.integration.IntegrationStatus;
import com.euronet.eft.portal.integration.monitoring.MonitoringModels.AlertInfo;
import com.euronet.eft.portal.integration.monitoring.MonitoringModels.GrafanaDashboard;
import com.euronet.eft.portal.integration.monitoring.MonitoringModels.MetricSample;
import com.euronet.eft.portal.integration.monitoring.MonitoringModels.ScrapeTarget;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Component
@ConditionalOnProperty(prefix = "portal.integrations.monitoring", name = "mode", havingValue = "mock", matchIfMissing = true)
public class MockMonitoringClient implements MonitoringClient {

    private static final String GRAFANA = "https://grafana.example.internal";
    private static final List<String> HOSTS = List.of("sit-app-01", "sit-app-02", "uat-app-01", "uat-app-02", "dev-app-01");

    private static Random seeded() {
        return new Random(Instant.now().truncatedTo(ChronoUnit.HOURS).getEpochSecond());
    }

    @Override
    public List<GrafanaDashboard> dashboards() {
        return List.of(
            new GrafanaDashboard("eft-tomcat", "EFT Tomcat overview", "EFT", List.of("tomcat", "jvm"), GRAFANA + "/d/eft-tomcat"),
            new GrafanaDashboard("eft-jvm", "JVM memory and GC", "EFT", List.of("jvm"), GRAFANA + "/d/eft-jvm"),
            new GrafanaDashboard("node-exporter", "Linux hosts (node exporter)", "Infrastructure", List.of("linux", "node"), GRAFANA + "/d/node-exporter"),
            new GrafanaDashboard("eft-db", "Database connections", "EFT", List.of("database"), GRAFANA + "/d/eft-db"),
            new GrafanaDashboard("jenkins", "Jenkins build queue", "CI/CD", List.of("jenkins"), GRAFANA + "/d/jenkins"));
    }

    @Override
    public List<ScrapeTarget> targets() {
        Random r = seeded();
        Instant now = Instant.now();
        List<ScrapeTarget> out = new ArrayList<>();
        for (String host : HOSTS) {
            boolean down = r.nextInt(12) == 0;
            out.add(new ScrapeTarget("node", host + ":9100", down ? "down" : "up", now.minusSeconds(r.nextInt(30)),
                0.01 + r.nextDouble() * 0.2, down ? "context deadline exceeded" : null));
            out.add(new ScrapeTarget("tomcat-jmx", host + ":9404", "up", now.minusSeconds(r.nextInt(30)),
                0.05 + r.nextDouble() * 0.5, null));
        }
        return out;
    }

    @Override
    public List<AlertInfo> alerts() {
        Random r = seeded();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        List<AlertInfo> out = new ArrayList<>();
        out.add(new AlertInfo("HighHeapUsage", "warning", "firing", "JVM heap above 85% for 10m on uat-app-01",
            "Grafana", now.minus(35, ChronoUnit.MINUTES), Map.of("instance", "uat-app-01:9404", "env", "UAT")));
        if (r.nextBoolean()) {
            out.add(new AlertInfo("DiskWillFillIn24h", "critical", "firing", "/opt/tomcat logs filling on sit-app-02",
                "Prometheus", now.minus(3, ChronoUnit.HOURS), Map.of("instance", "sit-app-02:9100", "env", "SIT")));
        }
        out.add(new AlertInfo("TomcatThreadsBusy", "warning", "pending", "Busy threads above 80% on sit-app-01",
            "Prometheus", now.minus(4, ChronoUnit.MINUTES), Map.of("instance", "sit-app-01:9404", "env", "SIT")));
        return out;
    }

    @Override
    public List<MetricSample> hostMetrics() {
        Random r = seeded();
        List<MetricSample> out = new ArrayList<>();
        for (String host : HOSTS) {
            out.add(new MetricSample("cpu", host, Math.round(5 + r.nextDouble() * 70), "%"));
            out.add(new MetricSample("memory", host, Math.round(30 + r.nextDouble() * 60), "%"));
            out.add(new MetricSample("disk", host, Math.round(20 + r.nextDouble() * 70), "%"));
        }
        return out;
    }

    @Override
    public IntegrationStatus status() {
        return IntegrationStatus.mock("monitoring", "Grafana & Prometheus");
    }
}
