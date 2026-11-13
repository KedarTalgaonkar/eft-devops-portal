import { Component, computed } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { apiUrl } from '../../core/api';
import { BarList, Donut, Slice } from '../../shared/charts';
import { KpiCard } from '../../shared/kpi-card';
import { LoadState } from '../../shared/load-state';
import { QualityProject } from './quality.models';

/** Leadership view: gate pass rate, coverage and where the risk sits. */
@Component({
  selector: 'app-quality-overview',
  imports: [RouterLink, KpiCard, LoadState, Donut, BarList],
  template: `
    <app-load-state [loading]="projects.isLoading()" [error]="projects.error()" what="SonarQube projects" (retry)="projects.reload()" />
    @if (list().length) {
      <section class="grid">
        <app-kpi-card label="Gate pass rate" icon="verified" unit="%" [value]="passRate()"
                      [tone]="passRate() >= 80 ? 'ok' : passRate() >= 60 ? 'warn' : 'bad'" [hint]="gates().ok + ' of ' + list().length + ' projects'" />
        <app-kpi-card label="Average coverage" icon="shield" unit="%" [value]="avgCoverage()" [tone]="avgCoverage() >= 80 ? 'ok' : 'warn'" hint="Target 80%" />
        <app-kpi-card label="Open vulnerabilities" icon="gpp_bad" [value]="sum('vulnerabilities')" [tone]="sum('vulnerabilities') ? 'bad' : 'ok'" />
        <app-kpi-card label="Bugs" icon="bug_report" [value]="sum('bugs')" [hint]="sum('codeSmells') + ' code smells'" />
      </section>
      <section class="grid-3">
        <div class="card">
          <div class="card-title">Quality gates</div>
          <app-donut [slices]="gateSlices()" centreLabel="projects" ariaLabel="Projects by quality gate status" />
        </div>
        <div class="card">
          <div class="section-head"><div class="card-title">Lowest coverage</div><span class="spacer"></span>
            <a class="link-more" routerLink="../projects">All projects →</a></div>
          <app-bar-list [items]="lowCoverage()" unit="%" [maxValue]="100" color="var(--c-aborted)" />
        </div>
        <div class="card">
          <div class="card-title">Most vulnerabilities</div>
          <app-bar-list [items]="mostVulns()" color="var(--c-failed)" />
        </div>
      </section>
    }
  `,
})
export class QualityOverviewPage {
  protected readonly projects = httpResource<QualityProject[]>(() => apiUrl('quality/projects'), { defaultValue: [] });
  protected readonly list = computed(() => this.projects.value());
  protected readonly gates = computed(() => ({
    ok: this.list().filter((p) => p.qualityGate === 'OK').length,
    warn: this.list().filter((p) => p.qualityGate === 'WARN').length,
    error: this.list().filter((p) => p.qualityGate === 'ERROR').length,
  }));
  protected readonly passRate = computed(() => (this.list().length ? Math.round((this.gates().ok * 100) / this.list().length) : 0));
  protected readonly avgCoverage = computed(() =>
    this.list().length ? Math.round((this.list().reduce((n, p) => n + p.coverage, 0) / this.list().length) * 10) / 10 : 0,
  );
  protected readonly gateSlices = computed<Slice[]>(() => [
    { label: 'Passed', value: this.gates().ok, color: 'var(--c-success)' },
    { label: 'Warning', value: this.gates().warn, color: 'var(--c-aborted)' },
    { label: 'Failed', value: this.gates().error, color: 'var(--c-failed)' },
  ]);
  protected readonly lowCoverage = computed(() =>
    [...this.list()].sort((a, b) => a.coverage - b.coverage).slice(0, 6).map((p) => ({ label: p.name, value: Math.round(p.coverage) })),
  );
  protected readonly mostVulns = computed(() =>
    [...this.list()].sort((a, b) => b.vulnerabilities - a.vulnerabilities).filter((p) => p.vulnerabilities > 0).slice(0, 6)
      .map((p) => ({ label: p.name, value: p.vulnerabilities, hint: `${p.securityHotspots} hotspots to review` })),
  );

  protected sum(key: 'bugs' | 'vulnerabilities' | 'codeSmells'): number {
    return this.list().reduce((n, p) => n + p[key], 0);
  }
}
