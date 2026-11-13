import { Component, computed } from '@angular/core';
import { DatePipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { apiUrl } from '../../core/api';
import { BarList, Donut, Slice } from '../../shared/charts';
import { KpiCard } from '../../shared/kpi-card';
import { LoadState } from '../../shared/load-state';
import { ArtifactRepository } from './artifacts.models';

/** Leadership view: storage footprint and repository mix. */
@Component({
  selector: 'app-artifacts-overview',
  imports: [DatePipe, RouterLink, KpiCard, LoadState, BarList, Donut],
  template: `
    <app-load-state [loading]="repos.isLoading()" [error]="repos.error()" what="repositories" (retry)="repos.reload()" />
    @if (repos.value().length) {
      <section class="grid">
        <app-kpi-card label="Repositories" icon="inventory_2" tone="info" [value]="repos.value().length" />
        <app-kpi-card label="Artifacts" icon="deployed_code" [value]="totalArtifacts()" />
        <app-kpi-card label="Storage" icon="storage" unit="GB" [value]="storageGb()" />
        <app-kpi-card label="Last publish" icon="schedule" [value]="(lastPublish() | date: 'dd MMM, HH:mm') ?? '—'" />
      </section>
      <section class="grid-2">
        <div class="card">
          <div class="section-head"><div class="card-title">Storage by repository (MB)</div><span class="spacer"></span>
            <a class="link-more" routerLink="../browse">Browse →</a></div>
          <app-bar-list [items]="storage()" />
        </div>
        <div class="card">
          <div class="card-title">Repository types</div>
          <app-donut [slices]="types()" centreLabel="repos" ariaLabel="Repositories by type" />
        </div>
      </section>
    }
  `,
})
export class ArtifactsOverviewPage {
  protected readonly repos = httpResource<ArtifactRepository[]>(() => apiUrl('artifacts/repositories'), { defaultValue: [] });
  protected readonly totalArtifacts = computed(() => this.repos.value().reduce((n, r) => n + r.artifactCount, 0));
  protected readonly storageGb = computed(() => Math.round(this.repos.value().reduce((n, r) => n + r.sizeBytes, 0) / 1e8) / 10);
  protected readonly lastPublish = computed(() =>
    this.repos.value().map((r) => r.lastUpdated).filter((d): d is string => !!d).sort().at(-1) ?? null,
  );
  protected readonly storage = computed(() =>
    [...this.repos.value()].sort((a, b) => b.sizeBytes - a.sizeBytes)
      .map((r) => ({ label: r.key, value: Math.round(r.sizeBytes / 1e6), hint: `${r.artifactCount} artifacts · ${r.packageType}` })),
  );
  protected readonly types = computed<Slice[]>(() => {
    const colors: Record<string, string> = { LOCAL: '#233f8e', REMOTE: '#00a3a1', VIRTUAL: '#f59e0b' };
    const map = new Map<string, number>();
    this.repos.value().forEach((r) => map.set(r.type, (map.get(r.type) ?? 0) + 1));
    return [...map.entries()].map(([label, value]) => ({ label, value, color: colors[label] ?? '#5b6780' }));
  });
}
