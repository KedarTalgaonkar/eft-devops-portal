import { Component, computed, input, linkedSignal, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { apiUrl } from '../../core/api';
import { LoadState } from '../../shared/load-state';
import { StatusChip } from '../../shared/status-chip';
import { GATE_LABEL, GateStatus, QualityProject } from './quality.models';

/** Support view: every SonarQube project with gate, issues, coverage and ratings. */
@Component({
  selector: 'app-quality-projects',
  imports: [DatePipe, DecimalPipe, LoadState, StatusChip],
  template: `
    <div class="filters">
      <div class="chips" role="group" aria-label="Quality gate">
        <button type="button" class="chip-btn" [attr.aria-pressed]="gate() === ''" (click)="gate.set('')">All</button>
        <button type="button" class="chip-btn" [attr.aria-pressed]="gate() === 'ERROR'" (click)="gate.set('ERROR')">Failed</button>
        <button type="button" class="chip-btn" [attr.aria-pressed]="gate() === 'WARN'" (click)="gate.set('WARN')">Warning</button>
        <button type="button" class="chip-btn" [attr.aria-pressed]="gate() === 'OK'" (click)="gate.set('OK')">Passed</button>
      </div>
      <input class="search" type="search" placeholder="Search project" aria-label="Search projects" [value]="search()" (input)="search.set($any($event.target).value)" />
    </div>
    <app-load-state [loading]="projects.isLoading()" [error]="projects.error()" [empty]="projects.hasValue() && rows().length === 0"
                    what="SonarQube projects" emptyText="No projects match." (retry)="projects.reload()" />
    @if (rows().length) {
      <div class="table-wrap">
        <table class="data" aria-label="SonarQube projects">
          <thead>
            <tr><th>Project</th><th>Quality gate</th><th class="num">Bugs</th><th class="num">Vulnerabilities</th><th class="num">Hotspots</th>
              <th class="num">Code smells</th><th class="num">Coverage</th><th class="num">Duplication</th><th>Ratings R / S / M</th><th class="num">Lines</th><th>Last analysis</th></tr>
          </thead>
          <tbody>
            @for (p of rows(); track p.key) {
              <tr>
                <td><a [href]="p.url" target="_blank" rel="noopener"><strong>{{ p.name }}</strong></a></td>
                <td><app-status-chip [status]="p.qualityGate" [text]="label[p.qualityGate]" /></td>
                <td class="num" [class.bad]="p.bugs > 10">{{ p.bugs }}</td>
                <td class="num" [class.bad]="p.vulnerabilities > 0">{{ p.vulnerabilities }}</td>
                <td class="num">{{ p.securityHotspots }}</td>
                <td class="num">{{ p.codeSmells }}</td>
                <td class="num" [class.warn]="p.coverage < 80">{{ p.coverage | number: '1.1-1' }}%</td>
                <td class="num" [class.warn]="p.duplication > 3">{{ p.duplication | number: '1.1-1' }}%</td>
                <td class="mono">{{ p.reliabilityRating }} / {{ p.securityRating }} / {{ p.maintainabilityRating }}</td>
                <td class="num">{{ p.linesOfCode | number }}</td>
                <td>{{ p.lastAnalysis | date: 'dd MMM, HH:mm' }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }
  `,
})
export class QualityProjectsPage {
  readonly q = input<string>();
  protected readonly search = linkedSignal(() => this.q() ?? '');
  protected readonly gate = signal<GateStatus | ''>('');
  protected readonly label = GATE_LABEL;
  protected readonly projects = httpResource<QualityProject[]>(() => apiUrl('quality/projects'), { defaultValue: [] });
  protected readonly rows = computed(() => {
    const q = this.search().trim().toLowerCase();
    const rank: Record<GateStatus, number> = { ERROR: 0, WARN: 1, NONE: 2, OK: 3 };
    return this.projects
      .value()
      .filter((p) => !this.gate() || p.qualityGate === this.gate())
      .filter((p) => !q || p.name.toLowerCase().includes(q) || p.key.toLowerCase().includes(q))
      .sort((a, b) => rank[a.qualityGate] - rank[b.qualityGate] || a.coverage - b.coverage);
  });
}
