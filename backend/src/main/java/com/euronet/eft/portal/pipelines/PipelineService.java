package com.euronet.eft.portal.pipelines;

import com.euronet.eft.portal.common.EnvironmentCodes;
import com.euronet.eft.portal.common.NotFoundException;
import com.euronet.eft.portal.environment.EnvironmentCatalog;
import com.euronet.eft.portal.integration.jenkins.JenkinsBuild;
import com.euronet.eft.portal.integration.jenkins.JenkinsBuildCache;
import com.euronet.eft.portal.inventory.Application;
import com.euronet.eft.portal.inventory.Deployment;
import com.euronet.eft.portal.inventory.InventoryService;
import com.euronet.eft.portal.inventory.JenkinsJobNaming;
import com.euronet.eft.portal.pipelines.PipelineDtos.BuildView;
import com.euronet.eft.portal.pipelines.PipelineDtos.DeploymentHistoryEntry;
import com.euronet.eft.portal.pipelines.PipelineDtos.PipelineMetric;
import com.euronet.eft.portal.pipelines.PipelineDtos.PipelineTotals;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Joins the inventory (which projects exist in which environment) with Jenkins build history.
 * A "pipeline" is one application in one environment, tracked by one Jenkins job. Pipelines are derived
 * from the inventory, so new applications, deployment paths and environments appear without code changes.
 */
@Service
public class PipelineService {

    private static final Logger log = LoggerFactory.getLogger(PipelineService.class);
    static final int MAX_DAYS = 365;

    private final InventoryService inventory;
    private final JenkinsBuildCache builds;
    private final JenkinsJobNaming naming;
    private final EnvironmentCatalog environments;

    public PipelineService(InventoryService inventory, JenkinsBuildCache builds, JenkinsJobNaming naming,
                           EnvironmentCatalog environments) {
        this.inventory = inventory;
        this.builds = builds;
        this.naming = naming;
        this.environments = environments;
    }

    /** One row per project and environment, counting builds started in the last {@code days} days. */
    public List<PipelineMetric> metrics(String environment, int days) {
        Instant since = since(days);
        List<PipelineMetric> rows = new ArrayList<>();
        for (Pipeline p : pipelines(environment)) {
            try {
                List<JenkinsBuild> recent = recent(p.job(), since);
                BuildStats stats = BuildStats.of(recent);
                BuildView last = recent.isEmpty() ? null : BuildView.of(recent.getFirst());
                rows.add(row(p, stats, last, null));
            } catch (RuntimeException e) {
                log.warn("Could not load builds for {}: {}", p.job(), e.getMessage());
                rows.add(row(p, BuildStats.EMPTY, null, e.getMessage()));
            }
        }
        return rows;
    }

    public PipelineTotals totals(String environment, int days) {
        List<PipelineMetric> rows = metrics(environment, days);
        BuildStats sum = rows.stream()
                .map(r -> new BuildStats(r.totalBuilds(), r.success(), r.failed(), r.aborted(), r.unstable(), r.inProgress()))
                .reduce(BuildStats.EMPTY, BuildStats::plus);
        return new PipelineTotals(rows.size(), sum.total(), sum.success(), sum.failed(), sum.aborted(),
                sum.unstable(), sum.inProgress(), sum.successRate(), clampDays(days));
    }

    /** Latest builds (any age) of one project in one environment. */
    public List<BuildView> builds(long applicationId, String environment, int limit) {
        Pipeline p = pipeline(applicationId, environment);
        return builds.builds(p.job()).stream().limit(Math.max(1, limit)).map(BuildView::of).toList();
    }

    /** Identifies one pipeline (application + environment) and its Jenkins job; used by the AI build explainer. */
    public record PipelineRef(long applicationId, String project, String client, String owner, String environment, String job) {
    }

    public PipelineRef ref(long applicationId, String environment) {
        Pipeline p = pipeline(applicationId, environment);
        Application a = p.app();
        return new PipelineRef(a.getId(), a.getName(), a.getClient(), a.getOwner(), p.environment(), p.job());
    }

    /** One build of a pipeline, from the cached build list. */
    public BuildView build(long applicationId, String environment, int number) {
        Pipeline p = pipeline(applicationId, environment);
        return builds.builds(p.job()).stream()
                .filter(b -> b.number() == number)
                .findFirst()
                .map(BuildView::of)
                .orElseThrow(() -> new NotFoundException("Build", p.job() + " #" + number));
    }

    private Pipeline pipeline(long applicationId, String environment) {
        String env = EnvironmentCodes.require(environment);
        return pipelines(env).stream()
                .filter(x -> x.app().getId() == applicationId)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Pipeline", applicationId + "/" + env));
    }

    /** Successful deployments across projects, newest first. */
    public List<DeploymentHistoryEntry> deploymentHistory(String environment, int days, int limit) {
        Instant since = since(days);
        List<DeploymentHistoryEntry> entries = new ArrayList<>();
        for (Pipeline p : pipelines(environment)) {
            try {
                for (JenkinsBuild b : recent(p.job(), since)) {
                    if (b.deployment()) {
                        entries.add(new DeploymentHistoryEntry(p.app().getId(), p.app().getName(), p.app().getClient(),
                                p.environment(), p.job(), b.number(), b.version(), b.result(), b.startedAt(),
                                b.triggeredBy(), b.url()));
                    }
                }
            } catch (RuntimeException e) {
                log.warn("Skipping deployment history of {}: {}", p.job(), e.getMessage());
            }
        }
        return entries.stream()
                .sorted(Comparator.comparing(DeploymentHistoryEntry::deployedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(Math.max(1, limit))
                .toList();
    }

    private List<JenkinsBuild> recent(String job, Instant since) {
        return builds.builds(job).stream()
                .filter(b -> b.startedAt() != null && !b.startedAt().isBefore(since))
                .toList();
    }

    /** Groups deployments by application + environment and resolves each group's Jenkins job. */
    List<Pipeline> pipelines(String environment) {
        String env = environment == null || environment.isBlank() ? null : EnvironmentCodes.require(environment);
        Map<String, List<Deployment>> groups = new LinkedHashMap<>();
        List<Deployment> all = inventory.allDeployments();
        java.util.Set<String> sharedNames = JenkinsJobNaming.sharedNames(all);
        for (Deployment d : all) {
            if (env == null || d.getEnvironment().equals(env)) {
                groups.computeIfAbsent(d.getApplication().getId() + "|" + d.getEnvironment(), k -> new ArrayList<>()).add(d);
            }
        }
        Comparator<String> envOrder = environments.order();
        return groups.values().stream()
                .map(ds -> {
                    Deployment first = ds.getFirst();
                    String job = ds.stream()
                            .map(Deployment::getJenkinsJob)
                            .filter(Objects::nonNull)
                            .filter(j -> !j.isBlank())
                            .findFirst()
                            .orElseGet(() -> naming.fromPattern(first.getApplication(), first.getEnvironment(), sharedNames));
                    return new Pipeline(first.getApplication(), first.getEnvironment(), job.strip());
                })
                .sorted(Comparator.comparing((Pipeline p) -> p.app().getName().toLowerCase(Locale.ROOT))
                        .thenComparing(p -> p.app().getClient().toLowerCase(Locale.ROOT))
                        .thenComparing(Pipeline::environment, envOrder))
                .toList();
    }

    private static PipelineMetric row(Pipeline p, BuildStats s, BuildView last, String error) {
        Application a = p.app();
        return new PipelineMetric(a.getId(), a.getName(), a.getClient(), a.getOwner(), p.environment(), p.job(),
                s.total(), s.success(), s.failed(), s.aborted(), s.unstable(), s.inProgress(), s.successRate(), last, error);
    }

    private static Instant since(int days) {
        return Instant.now().minus(Duration.ofDays(clampDays(days)));
    }

    static int clampDays(int days) {
        return Math.clamp(days, 1, MAX_DAYS);
    }

    record Pipeline(Application app, String environment, String job) {
    }
}
