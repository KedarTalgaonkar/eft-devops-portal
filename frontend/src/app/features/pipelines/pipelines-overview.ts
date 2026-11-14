import { Component, computed, inject } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { apiUrl } from '../../core/api';
import { Environments } from '../../core/environments';
import { ago } from '../../core/models';
import { BarList, StackedBars, StackPoint, StackSeries } from '../../shared/charts';
import { KpiCard } from '../../shared/kpi-card';
import { CardGroup } from '../../shared/card-group';
import { LoadState } from '../../shared/load-state';
import { StatusChip } from '../../shared/status-chip';
import { PipelineFilters } from './pipeline-filters';
import { DailyBuilds, PipelineInsights } from './pipelines.models';

@Component({
  selector: 'app-pipelines-overview',
  imports: [CardGroup, RouterLink, KpiCard, LoadState, StackedBars, BarList, StatusChip],
  templateUrl: './pipelines-overview.html',
  styles: `
    .env-cards { display: grid; gap: 12px; grid-template-columns: repeat(auto-fill, minmax(190px, 1fr)); }
    .env { display: flex; flex-direction: column; gap: 4px; text-align: left; font: inherit; color: inherit; cursor: pointer; }
    .env:hover { border-color: var(--eu-navy-400); }
    .env .name { font-weight: 600; display: flex; justify-content: space-between; align-items: center; }
    .env .rate { font-size: 24px; font-weight: 600; font-variant-numeric: tabular-nums; }
    .problems { list-style: none; margin: 0; padding: 0; display: grid; gap: 2px; }
    .problems li { display: grid; grid-template-columns: auto 1fr auto; gap: 12px; align-items: center; padding: 10px 6px; border-bottom: 1px solid var(--app-line); }
    .problems li:last-child { border-bottom: 0; }
    .problems .title { font-weight: 600; }
    .problems .meta { font-size: 12.5px; color: var(--app-muted); }
    .clear { display: flex; align-items: center; gap: 12px; color: var(--ok); font-weight: 600; }
  `,
})
export class PipelinesOverviewPage {
  protected readonly filters = inject(PipelineFilters);
  protected readonly envs = inject(Environments);
  protected readonly ago = ago;

  protected readonly insights = httpResource<PipelineInsights>(() =>
    apiUrl('pipelines/insights', { env: this.filters.env(), days: this.filters.days() }),
  );
  protected readonly trend = httpResource<DailyBuilds[]>(
    () => apiUrl('pipelines/trend', { env: this.filters.env(), days: this.filters.days() }),
    { defaultValue: [] },
  );

  protected readonly series: StackSeries[] = [
    { key: 'success', label: 'Success', color: 'var(--c-success)' },
    { key: 'failed', label: 'Failed', color: 'var(--c-failed)' },
    { key: 'unstable', label: 'Unstable', color: 'var(--c-unstable)' },
    { key: 'aborted', label: 'Aborted', color: 'var(--c-aborted)' },
  ];
  protected readonly points = computed<StackPoint[]>(() =>
    this.trend.value().map((d) => ({
      label: new Date(d.date).toLocaleDateString('en-IN', { day: '2-digit', month: 'short' }),
      values: { success: d.success, failed: d.failed, unstable: d.unstable, aborted: d.aborted },
    })),
  );
  protected readonly buildsPerDay = computed(() => this.trend.value().map((d) => d.success + d.failed + d.aborted + d.unstable));
  protected readonly deploysPerDay = computed(() => this.trend.value().map((d) => d.deployments));
  protected readonly ratePerDay = computed(() =>
    this.trend.value().map((d) => {
      const f = d.success + d.failed + d.aborted + d.unstable;
      return f ? (d.success * 100) / f : 0;
    }),
  );
  protected readonly failingBars = computed(() =>
    (this.insights.value()?.problems ?? []).slice(0, 6).map((p) => ({
      label: `${p.project} · ${p.client} · ${this.envs.name(p.environment)}`,
      value: p.failed,
      hint: `${p.successRate ?? '—'}% success · owner ${p.owner ?? '—'} · click to investigate`,
      link: '../all',
      query: { q: p.project },
      color: p.red ? 'var(--c-failed)' : 'var(--c-aborted)',
    })),
  );

  protected rateTone(rate: number | null): string {
    return rate === null ? '' : rate >= 85 ? 'ok' : rate >= 70 ? 'warn' : 'bad';
  }
}
