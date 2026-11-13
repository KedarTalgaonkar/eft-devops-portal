import { Component, computed, inject, input, linkedSignal, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { MatDialog } from '@angular/material/dialog';
import { apiUrl } from '../../core/api';
import { Environments } from '../../core/environments';
import { KpiCard } from '../../shared/kpi-card';
import { LoadState } from '../../shared/load-state';
import { StatusChip } from '../../shared/status-chip';
import { BuildsDialog } from './builds-dialog';
import { PipelineFilters } from './pipeline-filters';
import { SortKey, SortState, filterAndSort, totals } from './pipeline-table';
import { PipelineMetric } from './pipelines.models';

/** Support view: the "EFT Pipeline Metrics" table for every project × environment, with drill-down. */
@Component({
  selector: 'app-pipelines-all',
  imports: [DatePipe, DecimalPipe, KpiCard, LoadState, StatusChip],
  templateUrl: './pipelines-all.html',
})
export class PipelinesAllPage {
  private readonly dialog = inject(MatDialog);
  protected readonly filters = inject(PipelineFilters);
  protected readonly envs = inject(Environments);

  /** ?q= deep link from the overview / command centre. */
  readonly q = input<string>();
  protected readonly search = linkedSignal(() => this.q() ?? '');
  protected readonly onlyFailing = signal(false);
  protected readonly sort = signal<SortState>({ key: 'project', dir: 1 });

  protected readonly metrics = httpResource<PipelineMetric[]>(() =>
    apiUrl('pipelines/metrics', { env: this.filters.env(), days: this.filters.days() }),
  );

  protected readonly rows = computed(() => {
    if (!this.metrics.hasValue()) {
      return [];
    }
    const list = filterAndSort(this.metrics.value(), this.search(), this.sort(), this.envs.codes());
    return this.onlyFailing() ? list.filter((r) => r.failed > 0 || r.lastBuild?.result === 'FAILURE' || r.error) : list;
  });
  protected readonly totals = computed(() => totals(this.rows()));

  protected sortBy(key: SortKey): void {
    const current = this.sort();
    const numeric = key !== 'project' && key !== 'environment';
    this.sort.set(current.key === key ? { key, dir: current.dir === 1 ? -1 : 1 } : { key, dir: numeric ? -1 : 1 });
  }

  protected sortMark(key: SortKey): string {
    const s = this.sort();
    return s.key === key ? (s.dir === 1 ? ' ▲' : ' ▼') : '';
  }

  protected ariaSort(key: SortKey): string {
    const s = this.sort();
    return s.key !== key ? 'none' : s.dir === 1 ? 'ascending' : 'descending';
  }

  protected openBuilds(row: PipelineMetric): void {
    this.dialog.open(BuildsDialog, { data: row, width: '1000px', maxWidth: '95vw' });
  }
}
