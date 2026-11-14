import { Component, computed, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { MatTooltipModule } from '@angular/material/tooltip';
import { apiUrl } from '../../core/api';
import { ago } from '../../core/models';
import { LoadState } from '../../shared/load-state';
import { StatusChip } from '../../shared/status-chip';
import { AlertInfo } from './monitoring.models';

/** Support view: all firing and pending alerts with their labels. */
@Component({
  selector: 'app-monitoring-alerts',
  imports: [DatePipe, MatTooltipModule, LoadState, StatusChip],
  template: `
    <div class="filters">
      <div class="chips" role="group" aria-label="Alert state">
        @for (s of ['', 'firing', 'pending']; track s) {
          <button type="button" class="chip-btn" [attr.aria-pressed]="state() === s" (click)="state.set(s)">{{ s === '' ? 'All' : s === 'firing' ? 'Firing' : 'Pending' }}</button>
        }
      </div>
    </div>
    <app-load-state [loading]="alerts.isLoading()" [error]="alerts.error()" [empty]="alerts.hasValue() && rows().length === 0"
                    what="alerts" emptyText="No alerts." (retry)="alerts.reload()" />
    @if (rows().length) {
      <div class="table-wrap">
        <table class="data" aria-label="Alerts">
          <thead><tr><th>State</th><th>Severity</th><th>Alert</th><th>Summary</th><th>Labels</th><th>Source</th><th>Since</th></tr></thead>
          <tbody>
            @for (a of rows(); track a.name + a.startsAt) {
              <tr>
                <td><app-status-chip [status]="a.state" /></td>
                <td><app-status-chip [status]="a.severity" /></td>
                <td><strong>{{ a.name }}</strong></td>
                <td class="wrap">{{ a.summary }}</td>
                <td class="mono muted wrap">{{ labels(a) }}</td>
                <td>{{ a.source }}</td>
                <td [matTooltip]="(a.startsAt | date: 'dd MMM yyyy, HH:mm') ?? ''">{{ ago(a.startsAt) }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }
  `,
})
export class MonitoringAlertsPage {
  protected readonly ago = ago;
  protected readonly state = signal('');
  protected readonly alerts = httpResource<AlertInfo[]>(() => apiUrl('monitoring/alerts'), { defaultValue: [] });
  protected readonly rows = computed(() => this.alerts.value().filter((a) => !this.state() || a.state === this.state()));

  protected labels(a: AlertInfo): string {
    return Object.entries(a.labels ?? {}).map(([k, v]) => `${k}=${v}`).join(' ');
  }
}
