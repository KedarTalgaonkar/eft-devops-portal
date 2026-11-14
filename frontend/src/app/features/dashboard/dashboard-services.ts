import { Component, computed, inject, input, linkedSignal, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient, httpResource } from '@angular/common/http';
import { apiUrl } from '../../core/api';
import { Auth } from '../../core/auth';
import { Environments } from '../../core/environments';
import { HealthState } from '../../core/models';
import { EnvFilter } from '../../shared/env-filter';
import { KpiCard } from '../../shared/kpi-card';
import { LoadState } from '../../shared/load-state';
import { StatusChip } from '../../shared/status-chip';
import { ServiceHealth } from './dashboard.models';

/** Support view: the latest probe of every deployed context, with HTTP status and response time. */
@Component({
  selector: 'app-dashboard-services',
  imports: [DatePipe, EnvFilter, KpiCard, LoadState, StatusChip],
  template: `
    <div class="filters">
      <app-env-filter [(value)]="env" />
      <select class="select" aria-label="State" [value]="state()" (change)="state.set($any($event.target).value)">
        <option value="">Any state</option><option value="DOWN">Down</option><option value="UP">Up</option><option value="UNKNOWN">Not probed</option>
      </select>
      <input class="search" type="search" placeholder="Search application, context, URL or owner" aria-label="Search services"
             [value]="search()" (input)="search.set($any($event.target).value)" />
      @if (canProbe) {
        <button class="btn primary" type="button" [disabled]="probing()" (click)="probe()">{{ probing() ? 'Probing…' : 'Probe now' }}</button>
      }
    </div>

    <div class="grid">
      <app-kpi-card label="Up" icon="check_circle" tone="ok" [value]="count('UP')" />
      <app-kpi-card label="Down" icon="error" [tone]="count('DOWN') ? 'bad' : 'ok'" [value]="count('DOWN')" />
      <app-kpi-card label="Not probed" icon="help" [value]="count('UNKNOWN')" hint="No URL in the inventory" />
      <app-kpi-card label="Slowest response" icon="speed" unit="ms" [value]="slowest()" />
    </div>

    <app-load-state [loading]="services.isLoading()" [error]="services.error()" [empty]="services.hasValue() && rows().length === 0"
                    what="service health" emptyText="No services match these filters." (retry)="services.reload()" />
    @if (rows().length) {
      <div class="table-wrap">
        <table class="data" aria-label="Service health">
          <thead>
            <tr><th>State</th><th>Application</th><th>Environment</th><th>Context</th><th>URL</th><th class="num">HTTP</th>
              <th class="num">Response (ms)</th><th>Detail</th><th>Owner</th><th>Checked</th></tr>
          </thead>
          <tbody>
            @for (s of rows(); track s.deploymentId) {
              <tr>
                <td><app-status-chip [status]="s.state" /></td>
                <td><strong>{{ s.project }}</strong> <span class="muted">· {{ s.client }}</span></td>
                <td>{{ envs.name(s.environment) }}</td>
                <td class="mono">{{ s.contextName || '—' }}</td>
                <td class="mono muted">{{ s.url ?? '—' }}</td>
                <td class="num">{{ s.httpStatus ?? '—' }}</td>
                <td class="num">{{ s.responseTimeMs ?? '—' }}</td>
                <td class="wrap" [class.bad]="s.state === 'DOWN'">{{ s.detail }}</td>
                <td>{{ s.owner ?? '—' }}</td>
                <td>{{ s.checkedAt | date: 'HH:mm:ss' }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }
  `,
})
export class DashboardServicesPage {
  private readonly http = inject(HttpClient);
  protected readonly envs = inject(Environments);
  protected readonly canProbe = inject(Auth).can('EDITOR');

  readonly q = input<string>();
  protected readonly search = linkedSignal(() => this.q() ?? '');
  protected readonly env = signal('');
  protected readonly state = signal<HealthState | ''>('');
  protected readonly probing = signal(false);
  protected readonly services = httpResource<ServiceHealth[]>(() => apiUrl('dashboard/services'), { defaultValue: [] });

  protected readonly rows = computed(() => {
    const q = this.search().trim().toLowerCase();
    const order: Record<HealthState, number> = { DOWN: 0, UNKNOWN: 1, UP: 2 };
    return this.services
      .value()
      .filter((s) => !this.env() || s.environment === this.env())
      .filter((s) => !this.state() || s.state === this.state())
      .filter((s) => !q || [s.project, s.client, s.contextName, s.url ?? '', s.owner ?? ''].some((v) => v.toLowerCase().includes(q)))
      .sort((a, b) => order[a.state] - order[b.state]);
  });
  protected readonly slowest = computed(() => Math.max(0, ...this.services.value().map((s) => s.responseTimeMs ?? 0)) || null);

  protected count(state: HealthState): number {
    return this.services.value().filter((s) => s.state === state).length;
  }

  protected probe(): void {
    this.probing.set(true);
    this.http.post<ServiceHealth[]>(apiUrl('dashboard/services/refresh'), null).subscribe({
      next: (list) => {
        this.services.set(list);
        this.probing.set(false);
      },
      error: () => this.probing.set(false),
    });
  }
}
