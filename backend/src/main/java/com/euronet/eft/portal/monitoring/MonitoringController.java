package com.euronet.eft.portal.monitoring;

import com.euronet.eft.portal.integration.monitoring.MonitoringClient;
import com.euronet.eft.portal.integration.monitoring.MonitoringModels.AlertInfo;
import com.euronet.eft.portal.integration.monitoring.MonitoringModels.GrafanaDashboard;
import com.euronet.eft.portal.integration.monitoring.MonitoringModels.MetricSample;
import com.euronet.eft.portal.integration.monitoring.MonitoringModels.ScrapeTarget;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monitoring")
@Tag(name = "Monitoring (Grafana & Prometheus)", description = "Dashboards, scrape targets, alerts and host metrics")
public class MonitoringController {

    private final MonitoringClient monitoring;

    public MonitoringController(MonitoringClient monitoring) {
        this.monitoring = monitoring;
    }

    @GetMapping("/dashboards")
    @Operation(summary = "Grafana dashboards")
    public List<GrafanaDashboard> dashboards() {
        return monitoring.dashboards();
    }

    @GetMapping("/targets")
    @Operation(summary = "Prometheus scrape targets with up/down health")
    public List<ScrapeTarget> targets() {
        return monitoring.targets();
    }

    @GetMapping("/alerts")
    @Operation(summary = "Firing and pending alerts")
    public List<AlertInfo> alerts() {
        return monitoring.alerts();
    }

    @GetMapping("/metrics")
    @Operation(summary = "CPU, memory and disk per host")
    public List<MetricSample> metrics() {
        return monitoring.hostMetrics();
    }
}
