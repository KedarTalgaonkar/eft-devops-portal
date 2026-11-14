import { Component, computed } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { apiUrl } from '../../core/api';
import { BarList, Donut, Slice } from '../../shared/charts';
import { KpiCard } from '../../shared/kpi-card';
import { CardGroup } from '../../shared/card-group';
import { LoadState } from '../../shared/load-state';
import { GateStatus, QualityProject } from './quality.models';

/** Leadership view: gate pass rate, coverage and where the risk sits. */
@Component({
  selector: 'app-quality-overview',
  imports: [CardGroup, RouterLink, KpiCard, LoadState, Donut, BarList],
  template: `
    <app-load-state [loading]="projects.isLoading()" [error]="projects.error()" what="SonarQube projects" (retry)="projects.reload()" />
    @if (list().length) {
      <app-card-group heading="Key metrics" icon="verified"
                      description="SonarQube quality gates, test coverage and open issues across all projects.">
      <section class="grid">
        <app-kpi-card label="Gate pass rate" icon="verified" unit="%" [value]="passRate()"
                      [tone]="passRate() >= 80 ? 'ok' : passRate() >= 60 ? 'warn' : 'bad'" [hint]="gates().ok + ' of ' + list().length + ' projects · ' + gates().error + ' failing'"
                      link="../projects" [query]="gates().error ? { gate: 'ERROR' } : null" linkHint="Show projects failing the quality gate" />
        <app-kpi-card label="Average coverage" icon="shield" unit="%" [value]="avgCoverage()" [tone]="avgCoverage() >= 80 ? 'ok' : 'warn'" [hint]="'Target 80% · ' + belowTarget() + ' projects below'"
                      link="../projects" linkHint="Open projects by coverage" />
        <app-kpi-card label="Open vulnerabilities" icon="gpp_bad" [value]="sum('vulnerabilities')" [tone]="sum('vulnerabilities') ? 'bad' : 'ok'"
                      [hint]="withVulns() + ' projects affected · ' + sum('securityHotspots') + ' hotspots to review'" link="../projects" linkHint="Open projects" />
        <app-kpi-card label="Bugs" icon="bug_report" [value]="sum('bugs')" [hint]="sum('codeSmells') + ' code smells · ' + avgDup() + '% duplicated code on average'" link="../projects" linkHint="Open projects" />
      </section>
      </app-card-group>
      <app-card-group heading="Projects to look at" icon="search"
                      description="Gate results, and the projects with the lowest coverage or the most vulnerabilities.">
      <section class="grid-3">
        <div class="card">
          <div class="section-head"><div class="card-title">Quality gates</div><span class="spacer"></span>
            <a class="link-more" routerLink="../projects">All projects →</a></div>
          <p class="muted small">A gate is SonarQube's release check on new code: coverage, bugs, vulnerabilities and reviewed hotspots.</p>
          <app-donut [slices]="gateSlices()" [size]="112" centreLabel="projects" ariaLabel="Projects by quality gate status" />
          <ul class="gates">
            @for (g of gateDetails(); track g.status) {
              <li [class]="'gate ' + g.tone">
                <div class="g-head">
                  <a class="g-name" routerLink="../projects" [queryParams]="{ gate: g.status }">{{ g.label }} · {{ g.projects.length }}</a>
                  <span class="g-what">{{ g.meaning }}</span>
                </div>
                @if (g.reasons.length) {
                  <div class="g-reasons">
                    @for (r of g.reasons; track r.label) {
                      <span>{{ r.label }}: <strong>{{ r.count }}</strong></span>
                    }
                  </div>
                }
                <div class="g-projects">
                  @for (p of g.projects.slice(0, 6); track p.key) {
                    <a class="proj-chip" routerLink="../projects" [queryParams]="{ q: p.name }">{{ p.name }}</a>
                  }
                  @if (g.projects.length > 6) {
                    <a class="proj-chip more" routerLink="../projects" [queryParams]="{ gate: g.status }">+{{ g.projects.length - 6 }} more</a>
                  }
                </div>
              </li>
            }
          </ul>
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
      </app-card-group>
    }
  `,
  styles: `
    .small { margin: -2px 0 10px; font-size: 12.5px; }
    .gates { list-style: none; margin: 14px 0 0; padding: 0; display: grid; gap: 8px; }
    .gate { padding: 10px 12px; border: 1px solid var(--app-line); border-left: 4px solid var(--app-line-strong); border-radius: 10px;
      display: flex; flex-direction: column; gap: 6px; }
    .gate.ok { border-left-color: var(--c-success); }
    .gate.warn { border-left-color: var(--c-aborted); }
    .gate.bad { border-left-color: var(--c-failed); }
    .g-head { display: flex; flex-direction: column; gap: 2px; }
    .g-name { font-weight: 600; color: var(--app-text); text-decoration: none; }
    .g-name:hover { color: var(--eu-navy-600); text-decoration: underline; }
    .g-what { font-size: 12.5px; color: var(--app-muted); }
    .g-reasons { display: flex; flex-wrap: wrap; gap: 2px 12px; font-size: 12.5px; color: var(--app-text); }
    .g-projects { display: flex; flex-wrap: wrap; gap: 6px; }
    .proj-chip { font-size: 12px; padding: 2px 8px; border-radius: 6px; background: var(--neutral-soft); color: var(--eu-navy-700); text-decoration: none; }
    .proj-chip:hover { background: var(--info-soft); text-decoration: underline; }
    .proj-chip.more { font-weight: 600; }
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
  /** Each gate status with what it means, why projects fail it, and the projects themselves. */
  protected readonly gateDetails = computed(() => {
    const groups: { status: GateStatus; label: string; tone: string; meaning: string }[] = [
      { status: 'ERROR', label: 'Failed', tone: 'bad', meaning: 'Not releasable until the failed conditions are fixed' },
      { status: 'WARN', label: 'Warning', tone: 'warn', meaning: 'Releasable, but close to a limit' },
      { status: 'OK', label: 'Passed', tone: 'ok', meaning: 'Meets every condition on new code' },
      { status: 'NONE', label: 'Not computed', tone: '', meaning: 'No analysis yet: add SonarQube to the pipeline' },
    ];
    return groups
      .map((g) => {
        const projects = this.list().filter((p) => p.qualityGate === g.status).sort((a, b) => a.coverage - b.coverage);
        const reasons = g.status === 'OK' || g.status === 'NONE' ? [] : [
          { label: 'Coverage below 80%', count: projects.filter((p) => p.coverage < 80).length },
          { label: 'Open vulnerabilities', count: projects.filter((p) => p.vulnerabilities > 0).length },
          { label: 'Hotspots to review', count: projects.filter((p) => p.securityHotspots > 0).length },
          { label: 'Bugs', count: projects.filter((p) => p.bugs > 0).length },
          { label: 'Duplication over 3%', count: projects.filter((p) => p.duplication > 3).length },
        ].filter((r) => r.count > 0);
        return { ...g, projects, reasons };
      })
      .filter((g) => g.projects.length > 0);
  });

  protected readonly gateSlices = computed<Slice[]>(() => [
    { label: 'Passed', value: this.gates().ok, color: 'var(--c-success)', link: '../projects', query: { gate: 'OK' } },
    { label: 'Warning', value: this.gates().warn, color: 'var(--c-aborted)', link: '../projects', query: { gate: 'WARN' } },
    { label: 'Failed', value: this.gates().error, color: 'var(--c-failed)', link: '../projects', query: { gate: 'ERROR' } },
  ]);
  protected readonly lowCoverage = computed(() =>
    [...this.list()].sort((a, b) => a.coverage - b.coverage).slice(0, 6)
      .map((p) => ({ label: p.name, value: Math.round(p.coverage), hint: `${p.bugs} bugs · gate ${p.qualityGate === 'OK' ? 'passed' : p.qualityGate === 'ERROR' ? 'failed' : p.qualityGate.toLowerCase()}`, link: '../projects', query: { q: p.name } })),
  );
  protected readonly mostVulns = computed(() =>
    [...this.list()].sort((a, b) => b.vulnerabilities - a.vulnerabilities).filter((p) => p.vulnerabilities > 0).slice(0, 6)
      .map((p) => ({ label: p.name, value: p.vulnerabilities, hint: `${p.securityHotspots} hotspots to review`, link: '../projects', query: { q: p.name } })),
  );

  protected readonly belowTarget = computed(() => this.list().filter((p) => p.coverage < 80).length);
  protected readonly withVulns = computed(() => this.list().filter((p) => p.vulnerabilities > 0).length);
  protected readonly avgDup = computed(() => (this.list().length ? Math.round((this.list().reduce((n, p) => n + p.duplication, 0) / this.list().length) * 10) / 10 : 0));

  protected sum(key: 'bugs' | 'vulnerabilities' | 'codeSmells' | 'securityHotspots'): number {
    return this.list().reduce((n, p) => n + p[key], 0);
  }
}
