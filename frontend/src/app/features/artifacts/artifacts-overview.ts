import { Component, computed } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { apiUrl } from '../../core/api';
import { BarList, Donut, Slice } from '../../shared/charts';
import { KpiCard } from '../../shared/kpi-card';
import { CardGroup } from '../../shared/card-group';
import { LoadState } from '../../shared/load-state';
import { ArtifactRepository } from './artifacts.models';

/** Leadership view: storage footprint and repository mix. */
@Component({
  selector: 'app-artifacts-overview',
  imports: [CardGroup, DatePipe, DecimalPipe, RouterLink, KpiCard, LoadState, BarList, Donut],
  template: `
    <app-load-state [loading]="repos.isLoading()" [error]="repos.error()" what="repositories" (retry)="repos.reload()" />
    @if (repos.value().length) {
      <app-card-group heading="Key metrics" icon="inventory_2"
                      description="Repositories, published artifacts and storage used in Artifactory.">
      <section class="grid">
        <app-kpi-card label="Repositories" icon="inventory_2" tone="info" [value]="repos.value().length" [hint]="typeSummary()" link="../browse" linkHint="Browse repositories" />
        <app-kpi-card label="Artifacts" icon="deployed_code" [value]="totalArtifacts()" [hint]="'Most in ' + (biggest()?.key ?? '—')" link="../browse" [query]="biggest() ? { repo: biggest()!.key } : null" linkHint="Browse the largest repository" />
        <app-kpi-card label="Storage" icon="storage" unit="GB" [value]="storageGb()" [hint]="(biggest()?.key ?? '—') + ' uses ' + biggestShare() + '%'" link="../browse" [query]="biggest() ? { repo: biggest()!.key } : null" linkHint="Browse the largest repository" />
        <app-kpi-card label="Last publish" icon="schedule" [value]="(lastPublish() | date: 'dd MMM, HH:mm') ?? '—'" [hint]="'In ' + (latestRepo()?.key ?? '—')" link="../browse" [query]="latestRepo() ? { repo: latestRepo()!.key } : null" linkHint="Browse the repository published to last" />
      </section>
      </app-card-group>
      <app-card-group heading="Storage" icon="storage" description="Which repositories use the most space, and which repository types exist.">
      <section class="grid-2">
        <div class="card">
          <div class="section-head"><div class="card-title">Storage by repository (MB)</div><span class="spacer"></span>
            <a class="link-more" routerLink="../browse">Browse →</a></div>
          <app-bar-list [items]="storage()" />
        </div>
        <div class="card">
          <div class="section-head"><div class="card-title">Repository types</div><span class="spacer"></span>
            <a class="link-more" routerLink="../browse">Browse →</a></div>
          <app-donut [slices]="types()" [size]="112" centreLabel="repos" ariaLabel="Repositories by type" />
          <ul class="types">
            @for (t of typeDetails(); track t.type) {
              <li>
                <div class="t-head">
                  <i [style.background]="t.color" aria-hidden="true"></i>
                  <strong>{{ t.label }}</strong>
                  <span class="t-what">{{ t.meaning }}</span>
                </div>
                <div class="t-facts">
                  <span>{{ t.repos }} repo{{ t.repos === 1 ? '' : 's' }}</span>
                  <span>{{ t.formats }}</span>
                  @if (t.type === 'VIRTUAL') {
                    <span>stores nothing itself: serves what the combined repositories hold</span>
                  } @else {
                    <span>{{ t.artifacts | number }} artifacts</span>
                    <span>{{ t.storage }}</span>
                    <span>{{ t.lastUpdated ? 'last publish ' + (t.lastUpdated | date: 'dd MMM, HH:mm') : 'nothing published yet' }}</span>
                  }
                </div>
                <div class="t-repos">
                  @for (r of t.repoKeys; track r) {
                    <a class="repo-chip" routerLink="../browse" [queryParams]="{ repo: r }">{{ r }}</a>
                  }
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
    .types { list-style: none; margin: 14px 0 0; padding: 0; display: grid; gap: 8px; }
    .types li { padding: 10px 12px; border: 1px solid var(--app-line); border-radius: 10px; display: flex; flex-direction: column; gap: 6px; }
    .t-head { display: flex; align-items: baseline; flex-wrap: wrap; gap: 4px 8px; }
    .t-head i { width: 10px; height: 10px; border-radius: 3px; align-self: center; }
    .t-what { font-size: 12.5px; color: var(--app-muted); }
    .t-facts { display: flex; flex-wrap: wrap; gap: 2px 12px; font-size: 12.5px; color: var(--app-text); }
    .t-facts span + span::before { content: "·"; margin-right: 12px; color: var(--app-faint); }
    .t-repos { display: flex; flex-wrap: wrap; gap: 6px; }
    .repo-chip { font-family: var(--font-mono); font-size: 12px; padding: 2px 8px; border-radius: 6px; background: var(--neutral-soft); color: var(--eu-navy-700); text-decoration: none; }
    .repo-chip:hover { background: var(--info-soft); text-decoration: underline; }
  `,
})
export class ArtifactsOverviewPage {
  protected readonly repos = httpResource<ArtifactRepository[]>(() => apiUrl('artifacts/repositories'), { defaultValue: [] });
  protected readonly totalArtifacts = computed(() => this.repos.value().reduce((n, r) => n + r.artifactCount, 0));
  protected readonly storageGb = computed(() => Math.round(this.repos.value().reduce((n, r) => n + r.sizeBytes, 0) / 1e8) / 10);
  protected readonly lastPublish = computed(() =>
    this.repos.value().map((r) => r.lastUpdated).filter((d): d is string => !!d).sort().at(-1) ?? null,
  );
  protected readonly biggest = computed(() => [...this.repos.value()].sort((a, b) => b.sizeBytes - a.sizeBytes)[0] ?? null);
  protected readonly biggestShare = computed(() => {
    const total = this.repos.value().reduce((n, r) => n + r.sizeBytes, 0);
    return total && this.biggest() ? Math.round((this.biggest()!.sizeBytes / total) * 100) : 0;
  });
  protected readonly latestRepo = computed(() => [...this.repos.value()].filter((r) => r.lastUpdated).sort((a, b) => (b.lastUpdated ?? '').localeCompare(a.lastUpdated ?? ''))[0] ?? null);
  protected readonly typeSummary = computed(() => this.types().map((t) => t.value + ' ' + t.label.toLowerCase()).join(' · '));
  protected readonly storage = computed(() =>
    [...this.repos.value()].sort((a, b) => b.sizeBytes - a.sizeBytes)
      .map((r) => ({ label: r.key, value: Math.round(r.sizeBytes / 1e6), hint: `${r.artifactCount} artifacts · ${r.packageType}`, link: '../browse', query: { repo: r.key } })),
  );
  protected readonly types = computed<Slice[]>(() => {
    const colors: Record<string, string> = { LOCAL: '#233f8e', REMOTE: '#00a3a1', VIRTUAL: '#f59e0b' };
    const map = new Map<string, number>();
    this.repos.value().forEach((r) => map.set(r.type, (map.get(r.type) ?? 0) + 1));
    return [...map.entries()].map(([label, value]) => ({ label, value, color: colors[label] ?? '#5b6780', link: '../browse' }));
  });

  /** What each Artifactory repository type is for, with its numbers, formats and repositories. */
  protected readonly typeDetails = computed(() => {
    const meaning: Record<string, [string, string]> = {
      LOCAL: ['Local', 'what our teams build and publish (releases, snapshots, images)'],
      REMOTE: ['Remote', 'cached copies of external sources such as Maven Central'],
      VIRTUAL: ['Virtual', 'one URL that combines several repositories for builds'],
    };
    const colors = new Map(this.types().map((s) => [s.label, s.color]));
    const byType = new Map<string, ArtifactRepository[]>();
    this.repos.value().forEach((r) => byType.set(r.type, [...(byType.get(r.type) ?? []), r]));
    return [...byType.entries()].map(([type, list]) => {
      const bytes = list.reduce((n, r) => n + r.sizeBytes, 0);
      const formats = new Map<string, number>();
      list.forEach((r) => formats.set(r.packageType, (formats.get(r.packageType) ?? 0) + 1));
      return {
        type,
        label: meaning[type]?.[0] ?? type,
        meaning: meaning[type]?.[1] ?? '',
        color: colors.get(type) ?? '#5b6780',
        repos: list.length,
        artifacts: list.reduce((n, r) => n + r.artifactCount, 0),
        storage: bytes >= 1e9 ? (bytes / 1e9).toFixed(1) + ' GB' : Math.round(bytes / 1e6) + ' MB',
        formats: [...formats.entries()].map(([f, n]) => (n > 1 ? n + ' ' : '') + f.toLowerCase()).join(', '),
        lastUpdated: list.map((r) => r.lastUpdated).filter((d): d is string => !!d).sort().at(-1) ?? null,
        repoKeys: list.map((r) => r.key),
      };
    });
  });
}
