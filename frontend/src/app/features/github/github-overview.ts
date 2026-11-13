import { Component, computed } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { apiUrl } from '../../core/api';
import { BarList } from '../../shared/charts';
import { KpiCard } from '../../shared/kpi-card';
import { LoadState } from '../../shared/load-state';
import { ToolLogo } from '../../shared/tool-logo';
import { PullRequestInfo, TeamSummary } from './github.models';

const DAY = 86_400_000;

/** Leadership view: team footprint, review flow (PR age) and activity. */
@Component({
  selector: 'app-github-overview',
  imports: [RouterLink, KpiCard, LoadState, BarList, ToolLogo],
  template: `
    <app-load-state [loading]="teams.isLoading()" [error]="teams.error()" what="GitHub teams" (retry)="teams.reload()" />
    <section class="grid">
      <app-kpi-card label="Teams" icon="groups" tone="info" [value]="teams.value().length" />
      <app-kpi-card label="Repositories" icon="folder_code" [value]="repoCount()" />
      <app-kpi-card label="Open pull requests" icon="merge" [value]="open().length" [hint]="drafts() + ' drafts'" />
      <app-kpi-card label="PRs waiting > 7 days" icon="hourglass_bottom" [value]="stale().length"
                    [tone]="stale().length > 5 ? 'bad' : stale().length ? 'warn' : 'ok'" hint="Review bottlenecks" />
    </section>

    <section class="grid-2">
      <div class="card">
        <div class="section-head"><div class="card-title">Teams</div><span class="spacer"></span>
          <a class="link-more" routerLink="../repositories">Repositories →</a></div>
        <div class="teams">
          @for (t of teams.value(); track t.slug) {
            <a class="team" routerLink="../repositories" [queryParams]="{ team: t.slug }">
              <app-tool-logo name="github" [size]="22" />
              <div><strong>{{ t.name }}</strong><div class="muted">{{ t.repositoryCount }} repos · {{ t.members }} members · {{ t.openPullRequests }} open PRs</div></div>
            </a>
          }
        </div>
      </div>
      <div class="card">
        <div class="section-head"><div class="card-title">Open PRs by age</div><span class="spacer"></span>
          <a class="link-more" routerLink="../pulls">Pull requests →</a></div>
        <app-bar-list [items]="ageBuckets()" />
      </div>
    </section>

    <section class="card">
      <div class="card-title">Repositories with most open PRs</div>
      <app-bar-list [items]="byRepo()" color="var(--eu-teal)" />
    </section>
  `,
  styles: `
    .teams { display: grid; gap: 10px; }
    .team { display: flex; gap: 12px; align-items: center; padding: 10px; border: 1px solid var(--app-line); border-radius: 10px; text-decoration: none; color: inherit; }
    .team:hover { border-color: var(--eu-navy-400); background: var(--info-soft); }
  `,
})
export class GithubOverviewPage {
  protected readonly teams = httpResource<TeamSummary[]>(() => apiUrl('github/teams'), { defaultValue: [] });
  protected readonly pulls = httpResource<PullRequestInfo[]>(() => apiUrl('github/pulls', { state: 'open' }), { defaultValue: [] });

  protected readonly repoCount = computed(() => this.teams.value().reduce((n, t) => n + t.repositoryCount, 0));
  protected readonly open = computed(() => this.pulls.value());
  protected readonly drafts = computed(() => this.open().filter((p) => p.draft).length);
  protected readonly stale = computed(() => this.open().filter((p) => Date.now() - new Date(p.createdAt).getTime() > 7 * DAY));
  protected readonly ageBuckets = computed(() => {
    const age = (p: PullRequestInfo) => (Date.now() - new Date(p.createdAt).getTime()) / DAY;
    const prs = this.open();
    return [
      { label: 'Under 2 days', value: prs.filter((p) => age(p) < 2).length, color: 'var(--c-success)' },
      { label: '2–7 days', value: prs.filter((p) => age(p) >= 2 && age(p) < 7).length, color: 'var(--eu-teal)' },
      { label: '7–14 days', value: prs.filter((p) => age(p) >= 7 && age(p) < 14).length, color: 'var(--c-aborted)' },
      { label: 'Over 14 days', value: prs.filter((p) => age(p) >= 14).length, color: 'var(--c-failed)' },
    ];
  });
  protected readonly byRepo = computed(() => {
    const map = new Map<string, number>();
    this.open().forEach((p) => map.set(p.repository, (map.get(p.repository) ?? 0) + 1));
    return [...map.entries()].map(([label, value]) => ({ label, value })).sort((a, b) => b.value - a.value).slice(0, 8);
  });
}
