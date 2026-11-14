import { Component, computed, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { apiUrl } from '../../core/api';
import { ago } from '../../core/models';
import { Donut, Slice } from '../../shared/charts';
import { KpiCard } from '../../shared/kpi-card';
import { LoadState } from '../../shared/load-state';
import { StatusChip } from '../../shared/status-chip';
import { ToolLogo, ToolName } from '../../shared/tool-logo';
import { AreaStatus, AttentionReport, DashboardOverview, EnvironmentStatusRow } from './dashboard.models';
import { Ai } from '../../core/ai';
import { CardGroup } from '../../shared/card-group';

const AREA_LOGO: Record<AreaStatus['key'], ToolName | null> = {
  delivery: 'jenkins',
  services: 'tomcat',
  quality: 'sonarqube',
  security: null,
  operations: 'grafana',
};

const ENV_COLORS = ['#233f8e', '#00a3a1', '#4f6bb8', '#f59e0b', '#8b5cf6', '#c8312f', '#138a52', '#5b6780'];

interface FootprintRow {
  code: string;
  name: string;
  prod: boolean;
  color: string;
  applications: number;
  paths: number;
  up: number;
  down: number;
  failing: number;
  successRate: number | null;
  lastBuildAt: string | null;
}

/** Executive one-pager: overall RAG, one scorecard per area, headline KPIs and the top issues. */
@Component({
  selector: 'app-dashboard-overview',
  imports: [DatePipe, RouterLink, MatIconModule, KpiCard, LoadState, StatusChip, ToolLogo, Donut, CardGroup],
  templateUrl: './dashboard-overview.html',
  styleUrl: './dashboard-overview.scss',
})
export class DashboardOverviewPage {
  private readonly router = inject(Router);
  protected readonly ai = inject(Ai);
  protected readonly ago = ago;
  protected readonly logo = AREA_LOGO;

  protected readonly report = httpResource<AttentionReport>(() => apiUrl('dashboard/attention'));
  protected readonly overview = httpResource<DashboardOverview>(() => apiUrl('dashboard/overview'));

  protected readonly topIssues = computed(() => (this.report.value()?.items ?? []).slice(0, 6));
  protected readonly issueCounts = computed(() => {
    const items = this.report.value()?.items ?? [];
    return {
      critical: items.filter((i) => i.severity === 'CRITICAL').length,
      high: items.filter((i) => i.severity === 'HIGH').length,
      total: items.length,
    };
  });
  /** Per-application cells per environment: services up/down, last build, success rate. */
  protected readonly envStatus = httpResource<EnvironmentStatusRow[]>(() => apiUrl('dashboard/environments'), { defaultValue: [] });

  /** One line per environment for the footprint card. */
  protected readonly footprint = computed<FootprintRow[]>(() =>
    (this.overview.value()?.environments ?? []).map((e, i) => {
      const cells = this.envStatus.value().flatMap((r) => r.cells.filter((c) => c.environment === e.environment && c.deployments > 0));
      const rates = cells.map((c) => c.successRate).filter((r): r is number => r !== null);
      const last = cells.map((c) => c.lastBuildAt).filter((d): d is string => !!d).sort().at(-1) ?? null;
      return {
        code: e.environment,
        name: e.name,
        prod: e.category === 'PROD',
        color: ENV_COLORS[i % ENV_COLORS.length],
        applications: e.applications,
        paths: e.deployments,
        up: cells.reduce((n, c) => n + c.up, 0),
        down: cells.reduce((n, c) => n + c.down, 0),
        failing: cells.filter((c) => c.lastBuildResult === 'FAILURE').length,
        successRate: rates.length ? Math.round((rates.reduce((a, b) => a + b, 0) / rates.length) * 10) / 10 : null,
        lastBuildAt: last,
      };
    }),
  );

  protected readonly envSlices = computed<Slice[]>(() =>
    (this.overview.value()?.environments ?? []).map((e, i) => ({
      label: e.name,
      value: e.deployments,
      color: ENV_COLORS[i % ENV_COLORS.length],
      link: '/inventory/deployments',
      query: { env: e.environment },
    })),
  );
  protected readonly headline = computed(() => {
    const r = this.report.value();
    if (!r) {
      return '';
    }
    const red = r.areas.filter((a) => a.status === 'RED').length;
    const amber = r.areas.filter((a) => a.status === 'AMBER').length;
    if (!red && !amber) {
      return 'All areas are healthy.';
    }
    return [red ? `${red} area${red > 1 ? 's' : ''} red` : '', amber ? `${amber} amber` : ''].filter(Boolean).join(', ') +
      ` · ${this.issueCounts().critical} critical and ${this.issueCounts().high} high issues open`;
  });

  protected open(link: string): void {
    void this.router.navigateByUrl(link);
  }

  protected reload(): void {
    this.report.reload();
    this.overview.reload();
  }
}
