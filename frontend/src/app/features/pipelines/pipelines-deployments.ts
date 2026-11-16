import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { apiUrl } from '../../core/api';
import { Environments } from '../../core/environments';
import { LoadState } from '../../shared/load-state';
import { StatusChip } from '../../shared/status-chip';
import { PipelineFilters } from './pipeline-filters';
import { DeploymentHistoryEntry } from './pipelines.models';

/** Support view: every successful deployment in the period, newest first. */
@Component({
  selector: 'app-pipelines-deployments',
  imports: [DatePipe, LoadState, StatusChip],
  template: `
    <div class="filters">
      <input
        class="search"
        type="search"
        placeholder="Search project, client, version or user"
        aria-label="Search deployments"
        [value]="search()"
        (input)="search.set($any($event.target).value)"
      />
      <span class="muted">{{ rows().length }} deployments</span>
    </div>
    <app-load-state
      [loading]="history.isLoading()"
      [error]="history.error()"
      [empty]="history.hasValue() && rows().length === 0"
      what="deployment history"
      emptyText="No deployments in this period."
      (retry)="history.reload()"
    />
    @if (rows().length) {
      <div class="table-wrap">
        <table class="data" aria-label="Deployment history">
          <thead>
            <tr>
              <th>Deployed</th>
              <th>Project</th>
              <th>Environment</th>
              <th class="num">Build</th>
              <th>Version</th>
              <th>Result</th>
              <th>By</th>
              <th>Jenkins job</th>
            </tr>
          </thead>
          <tbody>
            @for (d of rows(); track d.jenkinsJob + d.buildNumber) {
              <tr>
                <td>{{ d.deployedAt | date: 'dd MMM yyyy, HH:mm' }}</td>
                <td>
                  <strong>{{ d.project }}</strong> <span class="muted">· {{ d.client }}</span>
                </td>
                <td>{{ envs.name(d.environment) }}</td>
                <td class="num">
                  @if (d.url) {
                    <a [href]="d.url" target="_blank" rel="noopener">#{{ d.buildNumber }}</a>
                  } @else {
                    #{{ d.buildNumber }}
                  }
                </td>
                <td class="mono">{{ d.version ?? '—' }}</td>
                <td><app-status-chip [status]="d.result" /></td>
                <td>{{ d.triggeredBy ?? '—' }}</td>
                <td class="mono muted">{{ d.jenkinsJob }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }
  `,
})
export class PipelinesDeploymentsPage {
  protected readonly filters = inject(PipelineFilters);
  protected readonly envs = inject(Environments);
  protected readonly search = signal('');
  protected readonly history = httpResource<DeploymentHistoryEntry[]>(() =>
    apiUrl('pipelines/deployments', {
      env: this.filters.env(),
      days: this.filters.days(),
      limit: 500,
    }),
  );
  protected readonly rows = computed(() => {
    const list = this.history.hasValue() ? this.history.value() : [];
    const q = this.search().trim().toLowerCase();
    return q
      ? list.filter((d) =>
          [d.project, d.client, d.version ?? '', d.triggeredBy ?? ''].some((v) =>
            v.toLowerCase().includes(q),
          ),
        )
      : list;
  });
}
