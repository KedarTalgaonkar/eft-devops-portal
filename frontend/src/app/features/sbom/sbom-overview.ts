import { Component, computed } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { apiUrl } from '../../core/api';
import { BarList, Donut, Slice } from '../../shared/charts';
import { KpiCard } from '../../shared/kpi-card';
import { LoadState } from '../../shared/load-state';
import { StatusChip } from '../../shared/status-chip';
import { SbomSummary } from './sbom.models';

/** Leadership view: current exposure across the latest scan of every project. */
@Component({
  selector: 'app-sbom-overview',
  imports: [RouterLink, KpiCard, LoadState, Donut, BarList, StatusChip],
  template: `
    <app-load-state [loading]="summary.isLoading()" [error]="summary.error()" [empty]="summary.hasValue() && summary.value().projects === 0"
                    what="the SBOM summary" emptyText="No SBOM scans yet. Run the first one on the Scans & findings tab."
                    (retry)="summary.reload()" />
    @if (summary.hasValue() && summary.value().projects) {
      @let s = summary.value();
      <section class="grid">
        <app-kpi-card label="Critical" icon="gpp_bad" [value]="s.critical" [tone]="s.critical ? 'bad' : 'ok'" hint="Open in latest scans" />
        <app-kpi-card label="High" icon="warning" [value]="s.high" [tone]="s.high ? 'warn' : 'ok'" />
        <app-kpi-card label="Projects scanned" icon="inventory" tone="info" [value]="s.projects" />
        <app-kpi-card label="Projects at risk" icon="report" [value]="atRisk()" [tone]="atRisk() ? 'bad' : 'ok'" hint="With critical or high findings" />
      </section>
      <section class="grid-3">
        <div class="card">
          <div class="card-title">Findings by severity</div>
          <app-donut [slices]="severity()" centreLabel="findings" ariaLabel="Findings by severity" />
        </div>
        <div class="card">
          <div class="section-head"><div class="card-title">Riskiest projects</div><span class="spacer"></span>
            <a class="link-more" routerLink="../scans">All scans →</a></div>
          <app-bar-list [items]="riskiest()" color="var(--c-failed)" />
        </div>
        <div class="card">
          <div class="card-title">Fix first · widest impact</div>
          <ul class="fix">
            @for (c of s.topComponents.slice(0, 6); track c.vulnId + c.component) {
              <li>
                <app-status-chip [status]="c.severity" />
                <div>
                  <strong class="mono">{{ c.vulnId }}</strong> <span class="muted">{{ c.component }} {{ c.version }}</span>
                  <div class="muted small">Upgrade to {{ c.fixedVersion ?? 'a fixed release' }} · {{ c.projects.length }} project(s)</div>
                </div>
              </li>
            }
          </ul>
        </div>
      </section>
    }
  `,
  styles: `
    .fix { list-style: none; margin: 0; padding: 0; display: grid; gap: 10px; }
    .fix li { display: flex; gap: 10px; align-items: flex-start; font-size: 13px; }
    .small { font-size: 12px; }
  `,
})
export class SbomOverviewPage {
  protected readonly summary = httpResource<SbomSummary>(() => apiUrl('sbom/summary'));
  protected readonly atRisk = computed(() => (this.summary.value()?.projectRisks ?? []).filter((p) => p.critical + p.high > 0).length);
  protected readonly severity = computed<Slice[]>(() => {
    const s = this.summary.value();
    return s
      ? [
          { label: 'Critical', value: s.critical, color: 'var(--c-failed)' },
          { label: 'High', value: s.high, color: 'var(--c-aborted)' },
          { label: 'Medium', value: s.medium, color: 'var(--eu-navy-400)' },
          { label: 'Low', value: s.low, color: 'var(--app-faint)' },
        ]
      : [];
  });
  protected readonly riskiest = computed(() =>
    (this.summary.value()?.projectRisks ?? []).slice(0, 6).map((p) => ({
      label: `${p.projectName} ${p.projectVersion}`,
      value: p.critical * 10 + p.high,
      hint: `${p.critical} critical · ${p.high} high · ${p.medium} medium`,
    })),
  );
}
