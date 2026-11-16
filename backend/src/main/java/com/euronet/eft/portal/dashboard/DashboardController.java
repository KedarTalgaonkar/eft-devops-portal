package com.euronet.eft.portal.dashboard;

import com.euronet.eft.portal.dashboard.DashboardDtos.DashboardOverview;
import com.euronet.eft.portal.dashboard.DashboardDtos.EnvironmentStatusRow;
import com.euronet.eft.portal.health.ServiceHealth;
import com.euronet.eft.portal.health.ServiceHealthService;
import com.euronet.eft.portal.integration.IntegrationStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard", description = "EFT application overview, environment status and service health")
public class DashboardController {

    private final DashboardService dashboard;
    private final ServiceHealthService health;
    private final AttentionService attention;

    public DashboardController(DashboardService dashboard, ServiceHealthService health, AttentionService attention) {
        this.dashboard = dashboard;
        this.health = health;
        this.attention = attention;
    }

    @GetMapping("/overview")
    @Operation(summary = "KPIs: applications, deployments per environment, 30-day pipeline totals, health, alerts")
    public DashboardOverview overview() {
        return dashboard.overview();
    }

    @GetMapping("/environments")
    @Operation(summary = "Environment status grid: each application in DEVELOP / SIT / UAT")
    public List<EnvironmentStatusRow> environments() {
        return dashboard.environments();
    }

    @GetMapping("/services")
    @Operation(summary = "Latest health probe result for every deployed context")
    public List<ServiceHealth> services() {
        return dashboard.services();
    }

    @PostMapping("/services/refresh")
    @Operation(summary = "Probe all services now instead of waiting for the schedule")
    public List<ServiceHealth> refreshServices() {
        health.refresh();
        return dashboard.services();
    }

    @GetMapping("/integrations")
    @Operation(summary = "Status and mode (mock/live) of every integration")
    public List<IntegrationStatus> integrations() {
        return dashboard.integrations();
    }

    @GetMapping("/attention")
    @Operation(summary = "Executive RAG status per area plus a ranked list of issues with deep links for support teams")
    public DashboardDtos.AttentionReport attention() {
        return attention.report();
    }
}
