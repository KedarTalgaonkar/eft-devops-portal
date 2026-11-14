import { Component, computed, input, linkedSignal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { apiUrl } from '../../core/api';
import { LoadState } from '../../shared/load-state';
import { StatusChip } from '../../shared/status-chip';
import { MetricSample, ScrapeTarget, toHostRows } from './monitoring.models';

/** Support view: per-host capacity and every Prometheus scrape target. */
@Component({
  selector: 'app-monitoring-infrastructure',
  imports: [DatePipe, DecimalPipe, LoadState, StatusChip],
  template: `
    <div class="filters">
      <input class="search" type="search" placeholder="Search host" aria-label="Search hosts" [value]="search()" (input)="search.set($any($event.target).value)" />
    </div>
    <section class="section">
      <div class="section-head"><h2 class="section-title">Hosts</h2><span class="section-sub">Worst first; amber at 75%, red at 90%</span></div>
      <app-load-state [loading]="metrics.isLoading()" [error]="metrics.error()" what="host metrics" (retry)="metrics.reload()" />
      <div class="table-wrap">
        <table class="data" aria-label="Host capacity">
          <thead><tr><th>Host</th><th>CPU</th><th>Memory</th><th>Disk</th></tr></thead>
          <tbody>
            @for (h of hosts(); track h.host) {
              <tr>
                <td class="mono"><strong>{{ h.host }}</strong></td>
                @for (v of [h.cpu, h.memory, h.disk]; track $index) {
                  <td>
                    <span class="meter">
                      <span class="track"><span class="fill" [class]="'fill ' + level(v)" [style.width.%]="v"></span></span>
                      <span class="num">{{ v | number: '1.0-0' }}%</span>
                    </span>
                  </td>
                }
              </tr>
            }
          </tbody>
        </table>
      </div>
    </section>
    <section class="section">
      <div class="section-head"><h2 class="section-title">Prometheus targets</h2></div>
      <app-load-state [loading]="targets.isLoading()" [error]="targets.error()" what="targets" (retry)="targets.reload()" />
      <div class="table-wrap">
        <table class="data" aria-label="Prometheus targets">
          <thead><tr><th>Health</th><th>Job</th><th>Instance</th><th class="num">Scrape (s)</th><th>Last scrape</th><th>Error</th></tr></thead>
          <tbody>
            @for (t of sortedTargets(); track t.job + t.instance) {
              <tr>
                <td><app-status-chip [status]="t.health" /></td>
                <td>{{ t.job }}</td>
                <td class="mono">{{ t.instance }}</td>
                <td class="num">{{ t.scrapeDurationSeconds | number: '1.3-3' }}</td>
                <td>{{ t.lastScrape | date: 'HH:mm:ss' }}</td>
                <td class="bad wrap">{{ t.lastError ?? '' }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    </section>
  `,
  styles: `
    .meter { display: flex; align-items: center; gap: 8px; min-width: 170px; }
    .track { flex: 1; height: 8px; background: var(--neutral-soft); border-radius: 4px; overflow: hidden; }
    .fill { display: block; height: 100%; background: var(--eu-navy-600); border-radius: 4px; }
    .fill.warn { background: var(--c-aborted); }
    .fill.bad { background: var(--c-failed); }
  `,
})
export class MonitoringInfrastructurePage {
  protected readonly metrics = httpResource<MetricSample[]>(() => apiUrl('monitoring/metrics'), { defaultValue: [] });
  protected readonly targets = httpResource<ScrapeTarget[]>(() => apiUrl('monitoring/targets'), { defaultValue: [] });
  /** ?q=host deep link from the overview. */
  readonly q = input<string>();
  protected readonly search = linkedSignal(() => this.q() ?? '');
  protected readonly hosts = computed(() => {
    const q = this.search().trim().toLowerCase();
    return toHostRows(this.metrics.value()).filter((h) => !q || h.host.toLowerCase().includes(q));
  });
  protected readonly sortedTargets = computed(() => [...this.targets.value()].sort((a, b) => (a.health === 'up' ? 1 : 0) - (b.health === 'up' ? 1 : 0)));

  protected level(v: number): string {
    return v >= 90 ? 'bad' : v >= 75 ? 'warn' : '';
  }
}
