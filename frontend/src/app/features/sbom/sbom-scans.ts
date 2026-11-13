import { Component, computed, inject, input, linkedSignal, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient, httpResource } from '@angular/common/http';
import { apiUrl } from '../../core/api';
import { LoadState } from '../../shared/load-state';
import { StatusChip } from '../../shared/status-chip';
import { ScanView, VulnerabilityView } from './sbom.models';

/** Support view: run a scan, browse scan history and the findings of one scan (?scan=<id> deep link). */
@Component({
  selector: 'app-sbom-scans',
  imports: [DatePipe, LoadState, StatusChip],
  template: `
    <form class="filters" (submit)="$event.preventDefault(); run(project.value, version.value)">
      <input #project class="search" placeholder="Project, e.g. prepaid-qr-api" aria-label="Project" required />
      <input #version class="search" placeholder="Version, e.g. 1.4.2" aria-label="Version" required />
      <button class="btn primary" type="submit" [disabled]="scanning()">{{ scanning() ? 'Scanning…' : 'Generate SBOM & scan' }}</button>
    </form>

    <app-load-state [loading]="scans.isLoading()" [error]="scans.error()" [empty]="scans.hasValue() && scans.value().length === 0"
                    what="scans" emptyText="No scans yet. Enter a project and version above." (retry)="scans.reload()" />
    @if (scans.value().length) {
      <div class="table-wrap">
        <table class="data" aria-label="SBOM scans">
          <thead><tr><th>Scanned</th><th>Project</th><th>Version</th><th>Format</th><th class="num">Components</th>
            <th class="num">Critical</th><th class="num">High</th><th class="num">Medium</th><th class="num">Low</th><th>Status</th></tr></thead>
          <tbody>
            @for (s of scans.value(); track s.id) {
              <tr class="clickable" tabindex="0" [class.selected]="s.id === selected()" (click)="selected.set(s.id)" (keydown.enter)="selected.set(s.id)">
                <td>{{ s.scannedAt | date: 'dd MMM yyyy, HH:mm' }}</td>
                <td><strong>{{ s.projectName }}</strong></td>
                <td class="mono">{{ s.projectVersion }}</td>
                <td>{{ s.format }}</td>
                <td class="num">{{ s.componentCount }}</td>
                <td class="num" [class.bad]="s.critical > 0">{{ s.critical }}</td>
                <td class="num" [class.warn]="s.high > 0">{{ s.high }}</td>
                <td class="num">{{ s.medium }}</td>
                <td class="num">{{ s.low }}</td>
                <td><app-status-chip [status]="s.status" /></td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }

    @if (selectedScan(); as s) {
      <section class="section">
        <div class="section-head"><h2 class="section-title">Findings · {{ s.projectName }} {{ s.projectVersion }}</h2>
          <span class="section-sub">Most severe first · upgrade to the "Fixed in" version</span></div>
        <app-load-state [loading]="vulns.isLoading()" [error]="vulns.error()" [empty]="vulns.hasValue() && vulns.value().length === 0"
                        what="findings" emptyText="No known vulnerabilities in this SBOM." (retry)="vulns.reload()" />
        @if (vulns.hasValue() && vulns.value().length) {
          <div class="table-wrap">
            <table class="data" aria-label="Findings">
              <thead><tr><th>Severity</th><th>ID</th><th>Component</th><th>Version</th><th>Fixed in</th><th>Title</th></tr></thead>
              <tbody>
                @for (v of vulns.value(); track v.vulnId + v.component) {
                  <tr>
                    <td><app-status-chip [status]="v.severity" /></td>
                    <td><a class="mono" [href]="'https://nvd.nist.gov/vuln/detail/' + v.vulnId" target="_blank" rel="noopener">{{ v.vulnId }}</a></td>
                    <td class="mono">{{ v.component }}</td>
                    <td class="mono">{{ v.version ?? '—' }}</td>
                    <td class="mono ok">{{ v.fixedVersion ?? '—' }}</td>
                    <td class="wrap">{{ v.title }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </section>
    }
  `,
})
export class SbomScansPage {
  private readonly http = inject(HttpClient);
  readonly scan = input<string>();
  protected readonly selected = linkedSignal<number | null>(() => (this.scan() ? Number(this.scan()) : null));
  protected readonly scanning = signal(false);
  protected readonly scans = httpResource<ScanView[]>(() => apiUrl('sbom/scans'), { defaultValue: [] });
  protected readonly vulns = httpResource<VulnerabilityView[]>(() => {
    const id = this.selected();
    return id === null ? undefined : apiUrl(`sbom/scans/${id}/vulnerabilities`);
  });
  protected readonly selectedScan = computed(() => this.scans.value().find((s) => s.id === this.selected()));

  protected run(project: string, version: string): void {
    if (!project.trim() || !version.trim()) {
      return;
    }
    this.scanning.set(true);
    this.http.post<ScanView>(apiUrl('sbom/scans'), { projectName: project, projectVersion: version }).subscribe({
      next: (s) => {
        this.scanning.set(false);
        this.scans.reload();
        this.selected.set(s.id);
      },
      error: () => this.scanning.set(false),
    });
  }
}
