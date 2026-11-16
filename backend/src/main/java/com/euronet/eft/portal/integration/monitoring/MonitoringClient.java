package com.euronet.eft.portal.integration.monitoring;

import com.euronet.eft.portal.integration.IntegrationHealth;
import com.euronet.eft.portal.integration.monitoring.MonitoringModels.AlertInfo;
import com.euronet.eft.portal.integration.monitoring.MonitoringModels.GrafanaDashboard;
import com.euronet.eft.portal.integration.monitoring.MonitoringModels.MetricSample;
import com.euronet.eft.portal.integration.monitoring.MonitoringModels.ScrapeTarget;

import java.util.List;

/**
 * Grafana dashboards and alerts plus Prometheus targets and metrics. Live client: phase 3.
 */
public interface MonitoringClient extends IntegrationHealth {

    List<GrafanaDashboard> dashboards();

    List<ScrapeTarget> targets();

    List<AlertInfo> alerts();

    List<MetricSample> hostMetrics();
}
