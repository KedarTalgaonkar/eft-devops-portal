package com.euronet.eft.portal.dashboard;

import com.euronet.eft.portal.dashboard.DashboardDtos.AreaStatus;
import com.euronet.eft.portal.dashboard.DashboardDtos.AttentionItem;
import com.euronet.eft.portal.dashboard.DashboardDtos.AttentionReport;
import com.euronet.eft.portal.dashboard.DashboardDtos.DashboardOverview;
import com.euronet.eft.portal.dashboard.DashboardDtos.EnvironmentCell;
import com.euronet.eft.portal.dashboard.DashboardDtos.EnvironmentStatusRow;
import com.euronet.eft.portal.dashboard.DashboardDtos.Rag;
import com.euronet.eft.portal.health.ServiceHealth;
import com.euronet.eft.portal.health.ServiceHealthService;
import com.euronet.eft.portal.integration.IntegrationStatus;
import com.euronet.eft.portal.security.access.AccessLevel;
import com.euronet.eft.portal.security.access.ModuleAccessService;
import com.euronet.eft.portal.security.access.PortalModule;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The Command centre. It summarises other modules, so each response leaves out what the user may not see
 * (e.g. no build figures without access to Pipelines).
 */
@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard", description = "EFT application overview, environment status and service health")
public class DashboardController {

    /** Attention-report area keys and item areas that belong to another module. */
    private static final Map<String, PortalModule> AREA_MODULE = Map.of(
        "delivery", PortalModule.PIPELINES, "quality", PortalModule.QUALITY,
        "security", PortalModule.SBOM, "operations", PortalModule.MONITORING);
    private static final Map<String, PortalModule> ITEM_MODULE = Map.of(
        "Delivery", PortalModule.PIPELINES, "Code quality", PortalModule.QUALITY,
        "Security", PortalModule.SBOM, "Operations", PortalModule.MONITORING);

    private final DashboardService dashboard;
    private final ServiceHealthService health;
    private final AttentionService attention;
    private final ModuleAccessService access;

    public DashboardController(DashboardService dashboard, ServiceHealthService health, AttentionService attention,
                               ModuleAccessService access) {
        this.dashboard = dashboard;
        this.health = health;
        this.attention = attention;
        this.access = access;
    }

    @GetMapping("/overview")
    @Operation(summary = "KPIs: applications, deployments per environment, 30-day pipeline totals, health, alerts")
    public DashboardOverview overview() {
        DashboardOverview o = dashboard.overview();
        Map<PortalModule, AccessLevel> a = access.current();
        return new DashboardOverview(o.applications(), o.deployments(), o.owners(), o.clients(), o.environments(),
            sees(a, PortalModule.PIPELINES) ? o.pipelines() : null, o.services(),
            sees(a, PortalModule.MONITORING) ? o.firingAlerts() : 0,
            sees(a, PortalModule.QUALITY) ? o.failingQualityGates() : 0,
            o.integrationsDown(), o.generatedAt());
    }

    @GetMapping("/environments")
    @Operation(summary = "Environment status grid: each application in DEVELOP / SIT / UAT")
    public List<EnvironmentStatusRow> environments() {
        List<EnvironmentStatusRow> rows = dashboard.environments();
        if (sees(access.current(), PortalModule.PIPELINES)) {
            return rows;
        }
        return rows.stream().map(r -> new EnvironmentStatusRow(r.applicationId(), r.project(), r.client(), r.owner(),
            r.cells().stream().map(c -> new EnvironmentCell(c.environment(), c.deployments(), c.up(), c.down(), c.health(),
                null, null, null)).toList())).toList();
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
    public AttentionReport attention() {
        AttentionReport r = attention.report();
        Map<PortalModule, AccessLevel> a = access.current();
        List<AreaStatus> areas = r.areas().stream()
            .filter(x -> !AREA_MODULE.containsKey(x.key()) || sees(a, AREA_MODULE.get(x.key()))).toList();
        List<AttentionItem> items = r.items().stream()
            .filter(i -> !ITEM_MODULE.containsKey(i.area()) || sees(a, ITEM_MODULE.get(i.area()))).toList();
        Rag overall = areas.stream().map(AreaStatus::status).max(Comparator.naturalOrder()).orElse(Rag.GREEN);
        return new AttentionReport(overall, areas, items, r.generatedAt());
    }

    private static boolean sees(Map<PortalModule, AccessLevel> access, PortalModule module) {
        return access.get(module).atLeast(AccessLevel.VIEW);
    }
}
