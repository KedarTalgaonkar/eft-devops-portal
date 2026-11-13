import { Component, computed, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { apiUrl } from '../../core/api';
import { LoadState } from '../../shared/load-state';
import { StatusChip } from '../../shared/status-chip';
import { ArtifactInfo, ArtifactRepository } from './artifacts.models';

/** Support view: repositories and the artifacts in the selected one, with checksums. */
@Component({
  selector: 'app-artifacts-browse',
  imports: [DatePipe, DecimalPipe, LoadState, StatusChip],
  template: `
    <app-load-state [loading]="repos.isLoading()" [error]="repos.error()" what="repositories" (retry)="repos.reload()" />
    <div class="table-wrap">
      <table class="data" aria-label="Repositories">
        <thead><tr><th>Repository</th><th>Type</th><th>Package</th><th>Description</th><th class="num">Artifacts</th><th class="num">Size (MB)</th><th>Last updated</th></tr></thead>
        <tbody>
          @for (r of repos.value(); track r.key) {
            <tr class="clickable" tabindex="0" [class.selected]="r.key === selectedKey()" (click)="selected.set(r.key)" (keydown.enter)="selected.set(r.key)">
              <td><a class="mono" [href]="r.url" target="_blank" rel="noopener" (click)="$event.stopPropagation()">{{ r.key }}</a></td>
              <td><app-status-chip [text]="r.type" /></td>
              <td>{{ r.packageType }}</td>
              <td class="wrap muted">{{ r.description }}</td>
              <td class="num">{{ r.artifactCount }}</td>
              <td class="num">{{ r.sizeBytes / 1000000 | number: '1.0-0' }}</td>
              <td>{{ r.lastUpdated | date: 'dd MMM yyyy, HH:mm' }}</td>
            </tr>
          }
        </tbody>
      </table>
    </div>
    @if (selectedKey()) {
      <section class="section">
        <div class="filters">
          <strong>{{ selectedKey() }}</strong>
          <input class="search" type="search" placeholder="Search path, file or version" aria-label="Search artifacts"
                 [value]="search()" (input)="search.set($any($event.target).value)" />
        </div>
        <app-load-state [loading]="artifacts.isLoading()" [error]="artifacts.error()" [empty]="artifacts.hasValue() && rows().length === 0"
                        what="artifacts" emptyText="No artifacts (a virtual repository has none of its own)." (retry)="artifacts.reload()" />
        @if (rows().length) {
          <div class="table-wrap">
            <table class="data" aria-label="Artifacts">
              <thead><tr><th>Path</th><th>File</th><th>Version</th><th class="num">Size (MB)</th><th>Created</th><th>By</th><th class="num">Downloads</th><th>SHA-256</th></tr></thead>
              <tbody>
                @for (a of rows(); track a.path + a.name) {
                  <tr>
                    <td class="mono muted">{{ a.path }}</td>
                    <td class="mono">{{ a.name }}</td>
                    <td class="mono">{{ a.version }}</td>
                    <td class="num">{{ a.sizeBytes / 1000000 | number: '1.1-1' }}</td>
                    <td>{{ a.createdAt | date: 'dd MMM yyyy, HH:mm' }}</td>
                    <td>{{ a.createdBy }}</td>
                    <td class="num">{{ a.downloads }}</td>
                    <td class="mono faint" [title]="a.sha256">{{ a.sha256.slice(0, 12) }}…</td>
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
export class ArtifactsBrowsePage {
  protected readonly selected = signal<string | null>(null);
  protected readonly search = signal('');
  protected readonly repos = httpResource<ArtifactRepository[]>(() => apiUrl('artifacts/repositories'), { defaultValue: [] });
  protected readonly selectedKey = computed(() => this.selected() ?? this.repos.value()[0]?.key ?? null);
  protected readonly artifacts = httpResource<ArtifactInfo[]>(() => {
    const key = this.selectedKey();
    return key ? apiUrl(`artifacts/repositories/${encodeURIComponent(key)}/artifacts`) : undefined;
  });
  protected readonly rows = computed(() => {
    const q = this.search().trim().toLowerCase();
    const list = this.artifacts.value() ?? [];
    return q ? list.filter((a) => [a.path, a.name, a.version].some((v) => v.toLowerCase().includes(q))) : list;
  });
}
