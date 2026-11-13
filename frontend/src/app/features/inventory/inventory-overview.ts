import { Component, computed, inject } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { apiUrl } from '../../core/api';
import { Environments } from '../../core/environments';
import { BarList, Donut, Slice } from '../../shared/charts';
import { KpiCard } from '../../shared/kpi-card';
import { LoadState } from '../../shared/load-state';
import { ChangeView, DeploymentView, countBy, javaLine, tomcatLine } from './inventory.models';

const PALETTE = ['#233f8e', '#00a3a1', '#f59e0b', '#8b5cf6', '#c8312f', '#4f6bb8', '#138a52', '#5b6780'];

/** Leadership view of the estate: size, platform currency (tech debt) and setup completeness. */
@Component({
  selector: 'app-inventory-overview',
  imports: [RouterLink, KpiCard, LoadState, BarList, Donut],
  template: `
    <app-load-state [loading]="deployments.isLoading()" [error]="deployments.error()" [empty]="deployments.hasValue() && !list().length"
                    what="the inventory" emptyText="No applications yet. Import the Tomcat projects workbook on the Deployment paths tab."
                    (retry)="deployments.reload()" />
    @if (list().length) {
      <section class="grid">
        <app-kpi-card label="Applications" icon="apps" tone="info" [value]="stats().apps" [hint]="stats().clients + ' clients'" />
        <app-kpi-card label="Deployment paths" icon="dns" [value]="list().length" [hint]="'across ' + stats().envs + ' environments'" />
        <app-kpi-card label="On legacy Java 8" icon="history_toggle_off" unit="%" [value]="stats().java8Pct"
                      [tone]="stats().java8Pct > 50 ? 'bad' : stats().java8Pct > 20 ? 'warn' : 'ok'"
                      [hint]="stats().java8 + ' of ' + list().length + ' paths: upgrade candidates'" />
        <app-kpi-card label="Setup complete" icon="task_alt" unit="%" [value]="stats().completePct"
                      [tone]="stats().completePct === 100 ? 'ok' : 'warn'" hint="Tomcat admin and build.xml both done" />
      </section>

      <section class="grid-3">
        <div class="card">
          <div class="card-title">Java versions</div>
          <app-donut [slices]="javaSlices()" centreLabel="paths" ariaLabel="Deployment paths by Java version" />
        </div>
        <div class="card">
          <div class="card-title">Tomcat versions</div>
          <app-donut [slices]="tomcatSlices()" centreLabel="paths" ariaLabel="Deployment paths by Tomcat version" />
        </div>
        <div class="card">
          <div class="card-title">Paths by environment</div>
          <app-bar-list [items]="byEnv()" color="var(--eu-teal)" />
        </div>
      </section>

      <section class="grid-2">
        <div class="card">
          <div class="section-head">
            <div class="card-title">Applications per owner</div>
            <span class="spacer"></span>
            <a class="link-more" routerLink="../deployments">All deployment paths →</a>
          </div>
          <app-bar-list [items]="byOwner()" />
        </div>
        <div class="card">
          <div class="section-head">
            <div class="card-title">Recent changes</div>
            <span class="spacer"></span>
            <a class="link-more" routerLink="../changes">Change log →</a>
          </div>
          <ul class="changes">
            @for (c of recent(); track c.id) {
              <li><strong>{{ c.application ?? '—' }}</strong> <span class="muted">{{ describe(c) }}</span></li>
            } @empty {
              <li class="muted">No changes recorded yet.</li>
            }
          </ul>
        </div>
      </section>
    }
  `,
  styles: `
    .changes { list-style: none; margin: 0; padding: 0; display: grid; gap: 8px; font-size: 13px; }
    .changes li { padding-bottom: 8px; border-bottom: 1px solid var(--app-line); }
    .changes li:last-child { border-bottom: 0; }
  `,
})
export class InventoryOverviewPage {
  private readonly envs = inject(Environments);
  protected readonly deployments = httpResource<DeploymentView[]>(() => apiUrl('inventory/deployments'), { defaultValue: [] });
  protected readonly changes = httpResource<ChangeView[]>(() => apiUrl('inventory/changes', { limit: 6 }), { defaultValue: [] });
  protected readonly list = computed(() => this.deployments.value());
  protected readonly recent = computed(() => this.changes.value());

  protected readonly stats = computed(() => {
    const l = this.list();
    const java8 = l.filter((d) => javaLine(d.javaVersion) === 'Java 8').length;
    const complete = l.filter((d) => d.tomcatAdminDone && d.buildXmlDone).length;
    return {
      apps: new Set(l.map((d) => d.applicationId)).size,
      clients: new Set(l.map((d) => d.client.toLowerCase())).size,
      envs: new Set(l.map((d) => d.environment)).size,
      java8,
      java8Pct: l.length ? Math.round((java8 * 100) / l.length) : 0,
      completePct: l.length ? Math.round((complete * 100) / l.length) : 0,
    };
  });

  protected readonly javaSlices = computed<Slice[]>(() => this.slices(countBy(this.list(), (d) => javaLine(d.javaVersion))));
  protected readonly tomcatSlices = computed<Slice[]>(() => this.slices(countBy(this.list(), (d) => tomcatLine(d.tomcatVersion))));
  protected readonly byEnv = computed(() =>
    countBy(this.list(), (d) => d.environment).map((x) => ({ ...x, label: this.envs.name(x.label) })),
  );
  protected readonly byOwner = computed(() =>
    countBy(
      [...new Map(this.list().map((d) => [d.applicationId, d])).values()],
      (d) => d.owner ?? 'Unassigned',
    ),
  );

  private slices(rows: { label: string; value: number }[]): Slice[] {
    return rows.map((r, i) => ({ ...r, color: PALETTE[i % PALETTE.length] }));
  }

  protected describe(c: ChangeView): string {
    switch (c.changeType) {
      case 'APPLICATION_ADDED':
        return 'added to the inventory';
      case 'DEPLOYMENT_ADDED':
        return `new deployment path ${c.newValue ?? ''}`;
      default:
        return `${c.field}: ${c.oldValue ?? '—'} → ${c.newValue ?? '—'}`;
    }
  }
}
