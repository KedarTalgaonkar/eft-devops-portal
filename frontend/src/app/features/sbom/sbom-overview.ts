import { Component, computed } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { apiUrl } from '../../core/api';
import { BarList, Donut, Slice } from '../../shared/charts';
import { KpiCard } from '../../shared/kpi-card';
import { CardGroup } from '../../shared/card-group';
import { LoadState } from '../../shared/load-state';
import { StatusChip } from '../../shared/status-chip';
import { SbomSummary } from './sbom.models';

/** Leadership view: current exposure across the latest scan of every project. */
@Component({
  selector: 'app-sbom-overview',
  imports: [CardGroup, RouterLink, KpiCard, LoadState, Donut, BarList, StatusChip],
  template: `
    <app-load-state [loading]="summary.isLoading()" [error]="summary.error()" [empty]="summary.hasValue() && summary.value().projects === 0"
                    what="the SBOM summary" emptyText="No SBOM scans yet. Run the first one on the Scans & findings tab."
                    (retry)="summary.reload()" />
    @if (summary.hasValue() && summary.value().projects) {
      @let s = summary.value();
      <app-card-group heading="Key metrics" icon="security"
                      description="From the latest SBOM scan of each project: how many findings, and how many projects are exposed.">
      <section class="grid">
        <app-kpi-card label="Critical" icon="gpp_bad" [value]="s.critical" [tone]="s.critical ? 'bad' : 'ok'"
                      [hint]="worst() ? 'Most in ' + worst()!.projectName + ' ' + worst()!.projectVersion : 'None open'"
                      link="../scans" [query]="worst() ? { scan: worst()!.scanId } : null" linkHint="Open the project with the most critical findings" />
        <app-kpi-card label="High" icon="warning" [value]="s.high" [tone]="s.high ? 'warn' : 'ok'" [hint]="s.medium + ' medium · ' + s.low + ' low'"
                      link="../scans" linkHint="Open scans and findings" />
        <app-kpi-card label="Projects scanned" icon="inventory" tone="info" [value]="s.projects" [hint]="(s.projects - atRisk()) + ' without critical or high findings'"
                      link="../scans" linkHint="Open all scans" />
        <app-kpi-card label="Projects at risk" icon="report" [value]="atRisk()" [tone]="atRisk() ? 'bad' : 'ok'" hint="With critical or high findings"
                      link="../scans" linkHint="Open scans and findings" />
      </section>
      </app-card-group>
      <app-card-group heading="Where to fix first" icon="build"
                      description="The severity mix, the riskiest projects, and the library upgrades that remove the most findings.">
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
      </app-card-group>
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
  protected readonly worst = computed(() => [...(this.summary.value()?.projectRisks ?? [])].sort((a, b) => b.critical - a.critical || b.high - a.high)[0] ?? null);
  protected readonly atRisk = computed(() => (this.summary.value()?.projectRisks ?? []).filter((p) => p.critical + p.high > 0).length);
  protected readonly severity = computed<Slice[]>(() => {
    const s = this.summary.value();
    return s
      ? [
          { label: 'Critical', value: s.critical, color: 'var(--c-failed)', link: '../scans' },
          { label: 'High', value: s.high, color: 'var(--c-aborted)', link: '../scans' },
          { label: 'Medium', value: s.medium, color: 'var(--eu-navy-400)', link: '../scans' },
          { label: 'Low', value: s.low, color: 'var(--app-faint)', link: '../scans' },
        ]
      : [];
  });
  protected readonly riskiest = computed(() =>
    (this.summary.value()?.projectRisks ?? []).slice(0, 6).map((p) => ({
      label: `${p.projectName} ${p.projectVersion}`,
      value: p.critical + p.high,
      hint: `${p.critical} critical · ${p.high} high · ${p.medium} medium · click for the findings`,
      color: p.critical ? 'var(--c-failed)' : 'var(--c-aborted)',
      link: '../scans',
      query: { scan: p.scanId },
    })),
  );
}
