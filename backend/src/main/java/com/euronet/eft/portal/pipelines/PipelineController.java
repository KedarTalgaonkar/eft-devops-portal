package com.euronet.eft.portal.pipelines;

import com.euronet.eft.portal.common.EnvironmentCodes;
import com.euronet.eft.portal.pipelines.PipelineDtos.BuildView;
import com.euronet.eft.portal.pipelines.PipelineDtos.DeploymentHistoryEntry;
import com.euronet.eft.portal.pipelines.PipelineDtos.PipelineMetric;
import com.euronet.eft.portal.pipelines.PipelineDtos.PipelineTotals;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * {@code env} is any environment code from /api/environments (DEVELOP, SIT, UAT, PERF, PROD, ...); omit it for all.
 */
@RestController
@RequestMapping("/api/pipelines")
@Tag(name = "Pipelines (Jenkins)", description = "Build counts, trends, KPIs and deployment history per project and environment")
public class PipelineController {

    private final PipelineService service;
    private final PipelineInsightsService insights;

    public PipelineController(PipelineService service, PipelineInsightsService insights) {
        this.service = service;
        this.insights = insights;
    }

    /**
     * Normalises the optional env parameter ("sit" -> SIT); rejects values that cannot be an environment code.
     */
    private static String env(String raw) {
        return raw == null || raw.isBlank() ? null : EnvironmentCodes.require(raw);
    }

    @GetMapping("/insights")
    @Operation(summary = "Executive KPIs vs the previous period, per-environment summary and problem pipelines")
    public InsightDtos.PipelineInsights insights(@RequestParam(required = false) String env,
                                                 @RequestParam(defaultValue = "30") int days) {
        return insights.insights(env(env), days);
    }

    @GetMapping("/trend")
    @Operation(summary = "Builds per day by result, plus deployments")
    public List<InsightDtos.DailyBuilds> trend(@RequestParam(required = false) String env,
                                               @RequestParam(defaultValue = "30") int days) {
        return insights.trend(env(env), days);
    }

    @GetMapping("/metrics")
    @Operation(summary = "EFT Pipeline Metrics: one row per project and environment")
    public List<PipelineMetric> metrics(@RequestParam(required = false) String env,
                                        @RequestParam(defaultValue = "30") int days) {
        return service.metrics(env(env), days);
    }

    @GetMapping("/totals")
    @Operation(summary = "Build totals across all projects")
    public PipelineTotals totals(@RequestParam(required = false) String env,
                                 @RequestParam(defaultValue = "30") int days) {
        return service.totals(env(env), days);
    }

    @GetMapping("/{applicationId}/{env}/builds")
    @Operation(summary = "Latest builds of one project in one environment")
    public List<BuildView> builds(@PathVariable long applicationId, @PathVariable String env,
                                  @RequestParam(defaultValue = "50") int limit) {
        return service.builds(applicationId, env(env), limit);
    }

    @GetMapping("/deployments")
    @Operation(summary = "Deployment history (successful deploy builds), newest first")
    public List<DeploymentHistoryEntry> deployments(@RequestParam(required = false) String env,
                                                    @RequestParam(defaultValue = "30") int days,
                                                    @RequestParam(defaultValue = "100") int limit) {
        return service.deploymentHistory(env(env), days, limit);
    }
}
