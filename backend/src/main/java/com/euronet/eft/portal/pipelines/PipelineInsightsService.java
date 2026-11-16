package com.euronet.eft.portal.pipelines;

import com.euronet.eft.portal.environment.EnvironmentCatalog;
import com.euronet.eft.portal.integration.jenkins.BuildResult;
import com.euronet.eft.portal.integration.jenkins.JenkinsBuild;
import com.euronet.eft.portal.integration.jenkins.JenkinsBuildCache;
import com.euronet.eft.portal.pipelines.InsightDtos.DailyBuilds;
import com.euronet.eft.portal.pipelines.InsightDtos.EnvironmentSummary;
import com.euronet.eft.portal.pipelines.InsightDtos.Kpi;
import com.euronet.eft.portal.pipelines.InsightDtos.PipelineInsights;
import com.euronet.eft.portal.pipelines.InsightDtos.ProblemPipeline;
import com.euronet.eft.portal.pipelines.PipelineService.Pipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Delivery insights for the executive overview: daily trend, KPIs against the previous period and the
 * pipelines that need attention. Builds come from the same cached Jenkins data as the metrics table.
 */
@Service
public class PipelineInsightsService {

    private static final Logger log = LoggerFactory.getLogger(PipelineInsightsService.class);
    private static final int TOP_PROBLEMS = 10;

    private final PipelineService pipelines;
    private final JenkinsBuildCache builds;
    private final EnvironmentCatalog environments;

    public PipelineInsightsService(PipelineService pipelines, JenkinsBuildCache builds, EnvironmentCatalog environments) {
        this.pipelines = pipelines;
        this.builds = builds;
        this.environments = environments;
    }

    /**
     * Hours from the first failure of a red streak to the next success, for streaks that started in the window.
     */
    static void recoveryHours(List<JenkinsBuild> newestFirst, Instant from, Instant to, List<Double> out) {
        Instant redSince = null;
        for (JenkinsBuild b : newestFirst.reversed()) {
            if (b.startedAt() == null) {
                continue;
            }
            boolean failed = b.result() == BuildResult.FAILURE || b.result() == BuildResult.UNSTABLE;
            if (failed && redSince == null) {
                redSince = b.startedAt();
            } else if (b.result() == BuildResult.SUCCESS && redSince != null) {
                if (!redSince.isBefore(from) && redSince.isBefore(to)) {
                    out.add(Duration.between(redSince, b.startedAt()).toMinutes() / 60.0);
                }
                redSince = null;
            }
        }
    }

    private static List<JenkinsBuild> between(List<JenkinsBuild> list, Instant from, Instant to) {
        return list.stream()
            .filter(b -> b.startedAt() != null && !b.startedAt().isBefore(from) && b.startedAt().isBefore(to))
            .toList();
    }

    private static Double failureRate(BuildStats s) {
        int finished = s.success() + s.failed() + s.aborted() + s.unstable();
        return finished == 0 ? null : round((s.failed() + s.unstable()) * 100.0 / finished);
    }

    private static Double perDay(long count, int days) {
        return round((double) count / days);
    }

    private static Double average(List<Double> values) {
        return values.isEmpty() ? null : round(values.stream().mapToDouble(Double::doubleValue).average().orElse(0));
    }

    private static Double averageMinutes(List<JenkinsBuild> list) {
        return list.isEmpty() ? null
            : round(list.stream().filter(b -> b.duration() != null).mapToLong(b -> b.duration().toSeconds()).average().orElse(0) / 60.0);
    }

    private static double round(double v) {
        return Math.round(v * 10) / 10.0;
    }

    /**
     * Builds per day across all pipelines (days without builds are included as zeros).
     */
    public List<DailyBuilds> trend(String environment, int days) {
        int window = PipelineService.clampDays(days);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Map<LocalDate, int[]> perDay = new TreeMap<>();
        for (int i = window - 1; i >= 0; i--) {
            perDay.put(today.minusDays(i), new int[5]);
        }
        for (List<JenkinsBuild> list : load(environment).values()) {
            for (JenkinsBuild b : list) {
                if (b.startedAt() == null) {
                    continue;
                }
                int[] c = perDay.get(LocalDate.ofInstant(b.startedAt(), ZoneOffset.UTC));
                if (c == null) {
                    continue;
                }
                switch (b.result()) {
                    case SUCCESS -> c[0]++;
                    case FAILURE -> c[1]++;
                    case ABORTED -> c[2]++;
                    case UNSTABLE -> c[3]++;
                    default -> {
                    }
                }
                if (b.deployment()) {
                    c[4]++;
                }
            }
        }
        return perDay.entrySet().stream()
            .map(e -> new DailyBuilds(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2], e.getValue()[3], e.getValue()[4]))
            .toList();
    }

    public PipelineInsights insights(String environment, int days) {
        int window = PipelineService.clampDays(days);
        Instant now = Instant.now();
        Instant start = now.minus(Duration.ofDays(window));
        Instant previousStart = start.minus(Duration.ofDays(window));

        Map<Pipeline, List<JenkinsBuild>> all = load(environment);
        List<JenkinsBuild> current = new ArrayList<>();
        List<JenkinsBuild> previous = new ArrayList<>();
        List<Double> recoveryCurrent = new ArrayList<>();
        List<Double> recoveryPrevious = new ArrayList<>();
        Map<String, List<Object[]>> perEnv = new TreeMap<>(environments.order());
        List<ProblemPipeline> problems = new ArrayList<>();
        int red = 0;

        for (var entry : all.entrySet()) {
            Pipeline p = entry.getKey();
            List<JenkinsBuild> cur = between(entry.getValue(), start, now);
            current.addAll(cur);
            previous.addAll(between(entry.getValue(), previousStart, start));
            recoveryHours(entry.getValue(), start, now, recoveryCurrent);
            recoveryHours(entry.getValue(), previousStart, start, recoveryPrevious);

            BuildStats stats = BuildStats.of(cur);
            JenkinsBuild lastFinished = entry.getValue().stream()
                .filter(b -> b.result() != BuildResult.IN_PROGRESS && b.result() != BuildResult.NOT_BUILT)
                .findFirst().orElse(null);
            boolean isRed = lastFinished != null
                && (lastFinished.result() == BuildResult.FAILURE || lastFinished.result() == BuildResult.UNSTABLE);
            if (isRed) {
                red++;
            }
            perEnv.computeIfAbsent(p.environment(), k -> new ArrayList<>()).add(new Object[]{stats, isRed});

            if (isRed || stats.failed() > 0) {
                Instant lastFailure = cur.stream()
                    .filter(b -> b.result() == BuildResult.FAILURE || b.result() == BuildResult.UNSTABLE)
                    .map(JenkinsBuild::startedAt).findFirst().orElse(null);
                problems.add(new ProblemPipeline(p.app().getId(), p.app().getName(), p.app().getClient(),
                    p.app().getOwner(), p.environment(), p.job(), stats.failed() + stats.unstable(), stats.total(),
                    stats.successRate(), lastFinished == null ? null : lastFinished.result(), lastFailure, isRed));
            }
        }

        List<EnvironmentSummary> environments = perEnv.entrySet().stream().map(e -> {
            BuildStats sum = e.getValue().stream().map(o -> (BuildStats) o[0]).reduce(BuildStats.EMPTY, BuildStats::plus);
            int envRed = (int) e.getValue().stream().filter(o -> (Boolean) o[1]).count();
            return new EnvironmentSummary(e.getKey(), e.getValue().size(), sum.total(), sum.successRate(), envRed);
        }).toList();

        problems.sort(Comparator.comparing(ProblemPipeline::red).reversed()
            .thenComparing(ProblemPipeline::failed, Comparator.reverseOrder()));

        BuildStats cur = BuildStats.of(current);
        BuildStats prev = BuildStats.of(previous);
        return new PipelineInsights(window, all.size(), red,
            new Kpi((double) cur.total(), previous.isEmpty() ? null : (double) prev.total()),
            new Kpi(cur.successRate(), prev.successRate()),
            new Kpi(perDay(current.stream().filter(JenkinsBuild::deployment).count(), window),
                previous.isEmpty() ? null : perDay(previous.stream().filter(JenkinsBuild::deployment).count(), window)),
            new Kpi(failureRate(cur), failureRate(prev)),
            new Kpi(average(recoveryCurrent), average(recoveryPrevious)),
            new Kpi(averageMinutes(current), averageMinutes(previous)),
            environments,
            problems.stream().limit(TOP_PROBLEMS).toList());
    }

    private Map<Pipeline, List<JenkinsBuild>> load(String environment) {
        Map<Pipeline, List<JenkinsBuild>> out = new LinkedHashMap<>();
        for (Pipeline p : pipelines.pipelines(environment)) {
            try {
                out.put(p, builds.builds(p.job()));
            } catch (RuntimeException e) {
                log.warn("Insights skip {}: {}", p.job(), e.getMessage());
            }
        }
        return out;
    }
}
