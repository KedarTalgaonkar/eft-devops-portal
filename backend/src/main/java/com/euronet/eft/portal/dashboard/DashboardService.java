package com.euronet.eft.portal.dashboard;

import com.euronet.eft.portal.dashboard.DashboardDtos.DashboardOverview;
import com.euronet.eft.portal.dashboard.DashboardDtos.EnvironmentCell;
import com.euronet.eft.portal.dashboard.DashboardDtos.EnvironmentCount;
import com.euronet.eft.portal.dashboard.DashboardDtos.EnvironmentStatusRow;
import com.euronet.eft.portal.dashboard.DashboardDtos.HealthSummary;
import com.euronet.eft.portal.environment.EnvironmentCatalog;
import com.euronet.eft.portal.environment.EnvironmentCatalog.EnvironmentView;
import com.euronet.eft.portal.health.HealthState;
import com.euronet.eft.portal.health.ServiceHealth;
import com.euronet.eft.portal.health.ServiceHealthService;
import com.euronet.eft.portal.integration.IntegrationHealth;
import com.euronet.eft.portal.integration.IntegrationMode;
import com.euronet.eft.portal.integration.IntegrationStatus;
import com.euronet.eft.portal.integration.monitoring.MonitoringClient;
import com.euronet.eft.portal.integration.sonar.SonarClient;
import com.euronet.eft.portal.integration.sonar.SonarModels.GateStatus;
import com.euronet.eft.portal.inventory.Application;
import com.euronet.eft.portal.inventory.Deployment;
import com.euronet.eft.portal.inventory.InventoryService;
import com.euronet.eft.portal.pipelines.PipelineDtos.PipelineMetric;
import com.euronet.eft.portal.pipelines.PipelineDtos.PipelineTotals;
import com.euronet.eft.portal.pipelines.PipelineService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Central dashboard: application overview, environment status and service health in one place. */
@Service
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);
    static final int WINDOW_DAYS = 30;

    private final InventoryService inventory;
    private final PipelineService pipelines;
    private final ServiceHealthService health;
    private final MonitoringClient monitoring;
    private final SonarClient sonar;
    private final EnvironmentCatalog environments;
    private final List<IntegrationHealth> integrations;

    public DashboardService(InventoryService inventory, PipelineService pipelines, ServiceHealthService health,
                            MonitoringClient monitoring, SonarClient sonar, EnvironmentCatalog environments,
                            List<IntegrationHealth> integrations) {
        this.inventory = inventory;
        this.pipelines = pipelines;
        this.health = health;
        this.monitoring = monitoring;
        this.sonar = sonar;
        this.environments = environments;
        this.integrations = integrations;
    }

    public DashboardOverview overview() {
        List<Deployment> deployments = inventory.allDeployments();
        Set<Long> apps = new HashSet<>();
        Set<String> owners = new HashSet<>();
        Set<String> clients = new HashSet<>();
        deployments.forEach(d -> {
            Application a = d.getApplication();
            apps.add(a.getId());
            if (a.getOwner() != null) {
                owners.add(a.getOwner().toLowerCase(Locale.ROOT));
            }
            if (!a.getClient().isBlank()) {
                clients.add(a.getClient().toLowerCase(Locale.ROOT));
            }
        });

        List<EnvironmentCount> perEnv = new ArrayList<>();
        for (EnvironmentView env : environments.list()) {
            List<Deployment> inEnv = deployments.stream().filter(d -> d.getEnvironment().equals(env.code())).toList();
            if (!env.active() && inEnv.isEmpty()) {
                continue;
            }
            int appCount = (int) inEnv.stream().map(d -> d.getApplication().getId()).distinct().count();
            perEnv.add(new EnvironmentCount(env.code(), env.name(), env.category(), appCount, inEnv.size()));
        }

        PipelineTotals totals = pipelines.totals(null, WINDOW_DAYS);
        List<ServiceHealth> probes = health.snapshot();
        HealthSummary services = new HealthSummary(
                count(probes, HealthState.UP), count(probes, HealthState.DOWN), count(probes, HealthState.UNKNOWN),
                health.lastRun());

        int alerts = safeCount(() -> (int) monitoring.alerts().stream().filter(a -> "firing".equalsIgnoreCase(a.state())).count());
        int gates = safeCount(() -> (int) sonar.projects().stream().filter(p -> p.qualityGate() == GateStatus.ERROR).count());
        int down = (int) integrations().stream().filter(i -> !i.reachable()).count();

        return new DashboardOverview(apps.size(), deployments.size(), owners.size(), clients.size(), perEnv, totals,
                services, alerts, gates, down, Instant.now());
    }

    /** Applications by environment (columns follow the environment catalogue), with probe health and last build. */
    public List<EnvironmentStatusRow> environments() {
        List<Deployment> deployments = inventory.allDeployments();
        List<String> envCodes = environments.list().stream().map(EnvironmentView::code).toList();
        Map<String, PipelineMetric> metrics = pipelines.metrics(null, WINDOW_DAYS).stream()
                .collect(Collectors.toMap(m -> m.applicationId() + "|" + m.environment(), Function.identity(), (a, b) -> a));
        Map<Long, ServiceHealth> probes = health.snapshot().stream()
                .collect(Collectors.toMap(ServiceHealth::deploymentId, Function.identity(), (a, b) -> a));

        Map<Long, List<Deployment>> byApp = new LinkedHashMap<>();
        deployments.stream()
                .sorted(Comparator.comparing((Deployment d) -> d.getApplication().getName().toLowerCase(Locale.ROOT))
                        .thenComparing(d -> d.getApplication().getClient().toLowerCase(Locale.ROOT)))
                .forEach(d -> byApp.computeIfAbsent(d.getApplication().getId(), k -> new ArrayList<>()).add(d));

        List<EnvironmentStatusRow> rows = new ArrayList<>();
        for (List<Deployment> appDeployments : byApp.values()) {
            Application app = appDeployments.getFirst().getApplication();
            List<EnvironmentCell> cells = new ArrayList<>();
            for (String env : envCodes) {
                List<Deployment> inEnv = appDeployments.stream().filter(d -> d.getEnvironment().equals(env)).toList();
                if (inEnv.isEmpty()) {
                    continue;
                }
                List<HealthState> states = inEnv.stream()
                        .map(d -> probes.get(d.getId()))
                        .map(p -> p == null ? HealthState.UNKNOWN : p.state())
                        .toList();
                int up = (int) states.stream().filter(s -> s == HealthState.UP).count();
                int downCount = (int) states.stream().filter(s -> s == HealthState.DOWN).count();
                HealthState overall = downCount > 0 ? HealthState.DOWN : up > 0 ? HealthState.UP : HealthState.UNKNOWN;
                PipelineMetric m = metrics.get(app.getId() + "|" + env);
                cells.add(new EnvironmentCell(env, inEnv.size(), up, downCount, overall,
                        m == null || m.lastBuild() == null ? null : m.lastBuild().result(),
                        m == null || m.lastBuild() == null ? null : m.lastBuild().startedAt(),
                        m == null ? null : m.successRate()));
            }
            rows.add(new EnvironmentStatusRow(app.getId(), app.getName(), app.getClient(), app.getOwner(), cells));
        }
        return rows;
    }

    public List<ServiceHealth> services() {
        return health.snapshot();
    }

    public List<IntegrationStatus> integrations() {
        return integrations.stream()
                .map(i -> {
                    try {
                        return i.status();
                    } catch (RuntimeException e) {
                        log.warn("Status check failed for {}: {}", i.getClass().getSimpleName(), e.getMessage());
                        return new IntegrationStatus(i.getClass().getSimpleName(), i.getClass().getSimpleName(),
                                IntegrationMode.LIVE, false, null, e.getMessage());
                    }
                })
                .sorted(Comparator.comparing(IntegrationStatus::name))
                .toList();
    }

    private static int count(List<ServiceHealth> probes, HealthState state) {
        return (int) probes.stream().filter(p -> p.state() == state).count();
    }

    static int safeCount(IntSupplier supplier) {
        try {
            return supplier.getAsInt();
        } catch (RuntimeException e) {
            log.warn("Dashboard counter unavailable: {}", e.getMessage());
            return 0;
        }
    }
}
