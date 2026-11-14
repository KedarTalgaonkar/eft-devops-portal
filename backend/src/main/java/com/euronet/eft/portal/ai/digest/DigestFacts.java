package com.euronet.eft.portal.ai.digest;

import com.euronet.eft.portal.dashboard.DashboardDtos.AreaStatus;
import com.euronet.eft.portal.dashboard.DashboardDtos.AttentionItem;
import com.euronet.eft.portal.dashboard.DashboardDtos.DashboardOverview;
import com.euronet.eft.portal.dashboard.DashboardDtos.Rag;
import com.euronet.eft.portal.pipelines.InsightDtos.EnvironmentSummary;
import com.euronet.eft.portal.pipelines.InsightDtos.Kpi;
import java.time.LocalDate;
import java.util.List;

/**
 * The aggregated numbers the digest is written from. Only figures the portal already shows: no logs, no URLs.
 */
public record DigestFacts(
        LocalDate date,
        int windowDays,
        Rag overall,
        List<AreaStatus> areas,
        List<AttentionItem> topIssues,
        int totalIssues,
        Delivery delivery,
        Estate estate) {

    public record Delivery(int pipelines, int failingPipelines, Kpi totalBuilds, Kpi successRatePercent, Kpi failureRatePercent,
                    Kpi deploymentsPerDay, Kpi meanTimeToRecoverHours, Kpi averageBuildMinutes,
                    List<EnvironmentSummary> environments) {
    }

    public record Estate(int applications, int deploymentPaths, int servicesUp, int servicesDown, int firingAlerts,
                  int failingQualityGates, int integrationsDown) {

        static Estate of(DashboardOverview o) {
            return new Estate(o.applications(), o.deployments(), o.services().up(), o.services().down(), o.firingAlerts(),
                    o.failingQualityGates(), o.integrationsDown());
        }
    }
}
