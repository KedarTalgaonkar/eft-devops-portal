package com.euronet.eft.portal.health;

import com.euronet.eft.portal.inventory.Application;
import com.euronet.eft.portal.inventory.Deployment;
import com.euronet.eft.portal.inventory.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Probes every deployment URL from the inventory on a schedule and keeps the latest result in memory.
 * <ul>
 *   <li>{@code HEALTH_PROBE_MODE=live}: HTTP GET of the context URL; 2xx/3xx/401/403 count as UP.</li>
 *   <li>{@code mock} (default): a stable, mostly-UP result per URL, because the real hosts are only
 *       reachable from inside the corporate network.</li>
 * </ul>
 */
@Service
public class ServiceHealthService {

    private static final Logger log = LoggerFactory.getLogger(ServiceHealthService.class);

    private final InventoryService inventory;
    private final boolean live;
    private final Duration timeout;
    private final Map<Long, ServiceHealth> latest = new ConcurrentHashMap<>();
    private volatile Instant lastRun;

    public ServiceHealthService(InventoryService inventory,
                                @Value("${portal.health.mode:mock}") String mode,
                                @Value("${portal.health.timeout:PT3S}") Duration timeout) {
        this.inventory = inventory;
        this.live = "live".equalsIgnoreCase(mode);
        this.timeout = timeout;
    }

    private static ServiceHealth result(Deployment d, String url, HealthState state, Integer status, Long ms, String detail) {
        Application a = d.getApplication();
        return new ServiceHealth(d.getId(), a.getId(), a.getName(), a.getClient(), a.getOwner(), d.getEnvironment(),
            d.getContextName(), url, state, status, ms, Instant.now(), detail);
    }

    @Scheduled(initialDelayString = "PT5S", fixedDelayString = "${portal.health.interval:PT2M}")
    public void refresh() {
        List<Deployment> deployments = inventory.allDeployments();
        Map<Long, ServiceHealth> next = new ConcurrentHashMap<>();
        if (live) {
            HttpClient http = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
            try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
                List<Future<ServiceHealth>> futures = deployments.stream()
                    .map(d -> pool.submit(() -> probeLive(http, d)))
                    .toList();
                for (Future<ServiceHealth> f : futures) {
                    try {
                        ServiceHealth h = f.get();
                        next.put(h.deploymentId(), h);
                    } catch (Exception e) {
                        log.debug("Probe task failed", e);
                    }
                }
            }
        } else {
            deployments.forEach(d -> next.put(d.getId(), probeMock(d)));
        }
        latest.clear();
        latest.putAll(next);
        lastRun = Instant.now();
        log.debug("Service health refreshed: {} deployments ({})", next.size(), live ? "live" : "mock");
    }

    /**
     * Latest probe results; runs a first probe synchronously if the scheduler has not run yet.
     */
    public List<ServiceHealth> snapshot() {
        if (lastRun == null) {
            refresh();
        }
        return latest.values().stream()
            .sorted(Comparator.comparing(ServiceHealth::project, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(ServiceHealth::environment)
                .thenComparing(ServiceHealth::contextName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    public Instant lastRun() {
        return lastRun;
    }

    private ServiceHealth probeLive(HttpClient http, Deployment d) {
        String url = d.contextUrl();
        if (url == null) {
            return result(d, null, HealthState.UNKNOWN, null, null, "No URL in inventory");
        }
        long start = System.nanoTime();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(timeout).GET().build();
            int status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            long ms = (System.nanoTime() - start) / 1_000_000;
            boolean up = (status >= 200 && status < 400) || status == 401 || status == 403;
            return result(d, url, up ? HealthState.UP : HealthState.DOWN, status, ms, "HTTP " + status);
        } catch (Exception e) {
            long ms = (System.nanoTime() - start) / 1_000_000;
            return result(d, url, HealthState.DOWN, null, ms, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private ServiceHealth probeMock(Deployment d) {
        String url = d.contextUrl();
        if (url == null) {
            return result(d, null, HealthState.UNKNOWN, null, null, "No URL in inventory");
        }
        long hour = Instant.now().truncatedTo(ChronoUnit.HOURS).getEpochSecond();
        Random random = new Random(url.hashCode() * 31L + hour);
        boolean down = random.nextInt(14) == 0;
        return result(d, url, down ? HealthState.DOWN : HealthState.UP, down ? 503 : 200,
            40L + random.nextInt(900), down ? "Mock: HTTP 503" : "Mock: HTTP 200");
    }
}
