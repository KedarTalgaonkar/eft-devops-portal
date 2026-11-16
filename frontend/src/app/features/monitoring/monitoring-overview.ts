import { Component, computed } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { apiUrl } from '../../core/api';
import { ago } from '../../core/models';
import { BarList } from '../../shared/charts';
import { KpiCard } from '../../shared/kpi-card';
import { CardGroup } from '../../shared/card-group';
import { LoadState } from '../../shared/load-state';
import { StatusChip } from '../../shared/status-chip';
import { ToolLogo } from '../../shared/tool-logo';
import { AlertInfo, GrafanaDashboard, MetricSample, ScrapeTarget, toHostRows } from './monitoring.models';

/** Leadership view: what is firing, which hosts are near capacity, and quick links to Grafana. */
@Component({
  selector: 'app-monitoring-overview',
  imports: [CardGroup, RouterLink, KpiCard, LoadState, BarList, StatusChip, ToolLogo],
  template: `
    <app-load-state
      [loading]="alerts.isLoading()"
      [error]="alerts.error()"
      what="alerts"
      (retry)="alerts.reload()"
    />
    <app-card-group
      heading="Key metrics"
      icon="monitoring"
      description="Alerts, scrape targets and host load from Grafana and Prometheus."
    >
      <section class="grid">
        <app-kpi-card
          label="Alerts firing"
          icon="notifications_active"
          [value]="firing().length"
          [tone]="criticalFiring() ? 'bad' : firing().length ? 'warn' : 'ok'"
          link="../alerts"
          [query]="{ state: 'firing' }"
          linkHint="Show firing alerts"
          [hint]="criticalFiring() + ' critical'"
        />
        <app-kpi-card
          label="Targets down"
          icon="sensors_off"
          [value]="down()"
          [tone]="down() ? 'bad' : 'ok'"
          [hint]="
            targets.value().length +
            ' scrape targets · ' +
            (targets.value().length - down()) +
            ' healthy'
          "
          link="../infrastructure"
          linkHint="Open scrape targets and hosts"
        />
        <app-kpi-card
          label="Hosts above 85%"
          icon="memory"
          [value]="hot().length"
          [tone]="hot().length ? 'warn' : 'ok'"
          [hint]="
            hot().length
              ? 'Worst: ' + hot()[0].host + ' at ' + round(hot()[0].worst) + '%'
              : 'CPU, memory and disk all below 85%'
          "
          link="../infrastructure"
          linkHint="Open host capacity"
        />
        <app-kpi-card
          label="Hosts monitored"
          icon="dns"
          tone="info"
          [value]="hosts().length"
          [hint]="dashboards.value().length + ' Grafana dashboards'"
          link="../infrastructure"
          linkHint="Open host capacity"
        />
      </section>
    </app-card-group>
    <app-card-group
      heading="Alerts and busy hosts"
      icon="notifications_active"
      description="What is firing now, and which hosts are under the most CPU, memory or disk pressure."
    >
      <section class="grid-2">
        <div class="card">
          <div class="section-head">
            <div class="card-title">Firing now</div>
            <span class="spacer"></span>
            <a class="link-more" routerLink="../alerts">All alerts →</a>
          </div>
          <ul class="alerts">
            @for (a of firing(); track a.name + a.startsAt) {
              <li>
                <a class="alert-link" routerLink="../alerts" [queryParams]="{ state: 'firing' }"
                  ><app-status-chip [status]="a.severity" />
                  <div>
                    <strong>{{ a.name }}</strong>
                    <div class="muted small">{{ a.summary }} · {{ ago(a.startsAt) }}</div>
                  </div></a
                >
              </li>
            } @empty {
              <li class="ok">
                <img src="/img/all-clear.svg" alt="" width="56" height="42" /> Nothing firing
              </li>
            }
          </ul>
        </div>
        <div class="card">
          <div class="section-head">
            <div class="card-title">Busiest hosts (worst of CPU / memory / disk)</div>
            <span class="spacer"></span>
            <a class="link-more" routerLink="../infrastructure">Infrastructure →</a>
          </div>
          <app-bar-list [items]="busiest()" unit="%" [maxValue]="100" />
        </div>
      </section>
    </app-card-group>
    <app-card-group
      heading="Grafana dashboards"
      icon="dashboard"
      description="Open a dashboard in Grafana for graphs and history."
    >
      <div class="grid">
        @for (d of dashboards.value(); track d.uid) {
          <a class="card dash" [href]="d.url" target="_blank" rel="noopener">
            <app-tool-logo name="grafana" [size]="24" />
            <div>
              <strong>{{ d.title }}</strong>
              <div class="muted small">{{ d.folder }} · {{ d.tags.join(', ') }}</div>
            </div>
          </a>
        }
      </div>
    </app-card-group>
  `,
  styles: `
    .alerts {
      list-style: none;
      margin: 0;
      padding: 0;
      display: grid;
      gap: 10px;
    }
    .alerts li {
      display: flex;
      gap: 10px;
      align-items: flex-start;
    }
    .alerts li.ok {
      align-items: center;
      font-weight: 600;
    }
    .alert-link {
      display: flex;
      gap: 10px;
      align-items: flex-start;
      flex: 1;
      margin: -4px -6px;
      padding: 4px 6px;
      border-radius: 8px;
      color: inherit;
      text-decoration: none;
    }
    .alert-link:hover {
      background: var(--neutral-soft);
    }
    .alert-link:hover strong {
      color: var(--eu-navy-600);
      text-decoration: underline;
    }
    .small {
      font-size: 12.5px;
    }
    .dash {
      display: flex;
      gap: 12px;
      align-items: center;
      text-decoration: none;
      color: inherit;
    }
    .dash:hover {
      border-color: var(--eu-navy-400);
    }
  `,
})
export class MonitoringOverviewPage {
  protected readonly ago = ago;
  protected readonly alerts = httpResource<AlertInfo[]>(() => apiUrl('monitoring/alerts'), {
    defaultValue: [],
  });
  protected readonly targets = httpResource<ScrapeTarget[]>(() => apiUrl('monitoring/targets'), {
    defaultValue: [],
  });
  protected readonly metrics = httpResource<MetricSample[]>(() => apiUrl('monitoring/metrics'), {
    defaultValue: [],
  });
  protected readonly dashboards = httpResource<GrafanaDashboard[]>(
    () => apiUrl('monitoring/dashboards'),
    { defaultValue: [] },
  );

  protected readonly firing = computed(() =>
    this.alerts.value().filter((a) => a.state === 'firing'),
  );
  protected readonly criticalFiring = computed(
    () => this.firing().filter((a) => a.severity === 'critical').length,
  );
  protected readonly down = computed(
    () => this.targets.value().filter((t) => t.health !== 'up').length,
  );
  protected readonly hosts = computed(() => toHostRows(this.metrics.value()));
  protected readonly round = Math.round;
  protected readonly hot = computed(() => this.hosts().filter((h) => h.worst >= 85));
  protected readonly busiest = computed(() =>
    this.hosts()
      .slice(0, 6)
      .map((h) => ({
        label: h.host,
        value: Math.round(h.worst),
        hint: `CPU ${Math.round(h.cpu)}% · memory ${Math.round(h.memory)}% · disk ${Math.round(h.disk)}%`,
        link: '../infrastructure',
        query: { q: h.host },
        color:
          h.worst >= 90
            ? 'var(--c-failed)'
            : h.worst >= 75
              ? 'var(--c-aborted)'
              : 'var(--eu-navy-600)',
      })),
  );
}
