import { Component, computed } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { apiUrl } from '../../core/api';
import { BarItem, BarList } from '../../shared/charts';
import { KpiCard } from '../../shared/kpi-card';
import { CardGroup } from '../../shared/card-group';
import { LoadState } from '../../shared/load-state';
import { ToolLogo } from '../../shared/tool-logo';
import { AGE_BUCKETS, ageDays, inBucket, PullRequestInfo, TeamSummary } from './github.models';

/** Leadership view: team footprint, review flow (PR age) and where reviews pile up. Every number opens its detail. */
@Component({
  selector: 'app-github-overview',
  imports: [CardGroup, RouterLink, KpiCard, LoadState, BarList, ToolLogo],
  template: `
    <app-load-state
      [loading]="teams.isLoading()"
      [error]="teams.error()"
      what="GitHub teams"
      (retry)="teams.reload()"
    />
    <app-card-group
      heading="Key metrics"
      icon="code"
      description="Teams, their repositories and the pull requests waiting for review. Click a card for the list behind it."
    >
      <section class="grid">
        <app-kpi-card
          label="Teams"
          icon="groups"
          tone="info"
          [value]="teams.value().length"
          [hint]="
            members() +
            ' members · largest: ' +
            (largestTeam()?.name ?? '—') +
            ' (' +
            (largestTeam()?.repositoryCount ?? 0) +
            ' repos)'
          "
          link="../repositories"
          linkHint="Open repositories by team"
        />
        <app-kpi-card
          label="Repositories"
          icon="folder_code"
          [value]="repoCount()"
          [hint]="'Average ' + avgRepos() + ' per team · ' + reposWithOpenPrs() + ' with open PRs'"
          link="../repositories"
          linkHint="Open repositories"
        />
        <app-kpi-card
          label="Open pull requests"
          icon="merge"
          [value]="open().length"
          [hint]="
            drafts() +
            ' drafts · ' +
            noReviewer() +
            ' without a reviewer · ' +
            authors() +
            ' authors'
          "
          link="../pulls"
          linkHint="Open all open pull requests"
        />
        <app-kpi-card
          label="PRs waiting > 7 days"
          icon="hourglass_bottom"
          [value]="stale().length"
          [tone]="stale().length > 5 ? 'bad' : stale().length ? 'warn' : 'ok'"
          [hint]="
            stale().length
              ? 'Oldest: ' + oldest() + ' days in ' + (oldestRepo() ?? '—')
              : 'No review bottleneck'
          "
          link="../pulls"
          [query]="{ age: '7+' }"
          linkHint="Show pull requests older than 7 days"
        />
        <app-kpi-card
          label="PRs without reviewer"
          icon="person_off"
          [value]="noReviewer()"
          [tone]="noReviewer() ? 'warn' : 'ok'"
          hint="Nobody has been asked to review yet"
          link="../pulls"
          [query]="{ reviewers: 'none' }"
          linkHint="Show pull requests without a reviewer"
        />
      </section>
    </app-card-group>

    <app-card-group
      heading="Teams and review backlog"
      icon="groups"
      description="Who owns which repositories, how old the open pull requests are, and where they pile up. Click any row to open it."
    >
      <section class="grid-2">
        <div class="card">
          <div class="section-head">
            <div class="card-title">Teams</div>
            <span class="spacer"></span>
            <a class="link-more" routerLink="../repositories">Repositories →</a>
          </div>
          <div class="teams">
            @for (t of teams.value(); track t.slug) {
              <a class="team" routerLink="../repositories" [queryParams]="{ team: t.slug }">
                <app-tool-logo name="github" [size]="22" />
                <div>
                  <strong>{{ t.name }}</strong>
                  <div class="muted">
                    {{ t.repositoryCount }} repos · {{ t.members }} members ·
                    {{ t.openPullRequests }} open PRs
                  </div>
                </div>
              </a>
            }
          </div>
        </div>
        <div class="card">
          <div class="section-head">
            <div class="card-title">Open PRs by age</div>
            <span class="spacer"></span>
            <a class="link-more" routerLink="../pulls">Pull requests →</a>
          </div>
          <p class="muted small">Older PRs mean slower delivery. Click a bar for those PRs.</p>
          <app-bar-list [items]="ageBuckets()" />
        </div>
      </section>

      <section class="card">
        <div class="section-head">
          <div class="card-title">Repositories with most open PRs</div>
          <span class="spacer"></span>
          <a class="link-more" routerLink="../pulls">All pull requests →</a>
        </div>
        <p class="muted small">Where reviews are piling up. Click a repository for its open PRs.</p>
        <app-bar-list [items]="byRepo()" color="var(--eu-teal)" />
      </section>
    </app-card-group>
  `,
  styles: `
    .teams {
      display: grid;
      gap: 10px;
    }
    .team {
      display: flex;
      gap: 12px;
      align-items: center;
      padding: 10px;
      border: 1px solid var(--app-line);
      border-radius: 10px;
      text-decoration: none;
      color: inherit;
    }
    .team:hover {
      border-color: var(--eu-navy-400);
      background: var(--info-soft);
    }
    .small {
      margin: -4px 0 10px;
      font-size: 12.5px;
    }
  `,
})
export class GithubOverviewPage {
  protected readonly teams = httpResource<TeamSummary[]>(() => apiUrl('github/teams'), {
    defaultValue: [],
  });
  protected readonly pulls = httpResource<PullRequestInfo[]>(
    () => apiUrl('github/pulls', { state: 'open' }),
    { defaultValue: [] },
  );

  protected readonly repoCount = computed(() =>
    this.teams.value().reduce((n, t) => n + t.repositoryCount, 0),
  );
  protected readonly members = computed(() =>
    this.teams.value().reduce((n, t) => n + t.members, 0),
  );
  protected readonly largestTeam = computed(
    () => [...this.teams.value()].sort((a, b) => b.repositoryCount - a.repositoryCount)[0],
  );
  protected readonly avgRepos = computed(() =>
    this.teams.value().length
      ? Math.round((this.repoCount() / this.teams.value().length) * 10) / 10
      : 0,
  );
  protected readonly open = computed(() => this.pulls.value());
  protected readonly drafts = computed(() => this.open().filter((p) => p.draft).length);
  protected readonly noReviewer = computed(
    () => this.open().filter((p) => p.reviewers === 0).length,
  );
  protected readonly authors = computed(() => new Set(this.open().map((p) => p.author)).size);
  protected readonly reposWithOpenPrs = computed(
    () => new Set(this.open().map((p) => p.repository)).size,
  );
  protected readonly stale = computed(() => this.open().filter((p) => ageDays(p) > 7));
  protected readonly ageBuckets = computed<BarItem[]>(() =>
    AGE_BUCKETS.filter((b) => b.key !== '7+').map((b) => ({
      label: b.label,
      value: this.open().filter((p) => inBucket(ageDays(p), b.key)).length,
      color: b.color,
      link: '../pulls',
      query: { age: b.key },
    })),
  );
  protected readonly byRepo = computed<BarItem[]>(() => {
    const map = new Map<string, PullRequestInfo[]>();
    this.open().forEach((p) => map.set(p.repository, [...(map.get(p.repository) ?? []), p]));
    return [...map.entries()]
      .map(([repo, prs]) => ({
        label: repo,
        value: prs.length,
        hint:
          'oldest ' +
          Math.floor(Math.max(...prs.map(ageDays))) +
          ' days · ' +
          prs.filter((p) => p.reviewers === 0).length +
          ' without reviewer',
        link: '../pulls',
        query: { q: repo },
      }))
      .sort((a, b) => b.value - a.value)
      .slice(0, 8);
  });
  private readonly oldestPr = computed(
    () => [...this.open()].sort((a, b) => ageDays(b) - ageDays(a))[0],
  );
  protected readonly oldest = computed(() =>
    this.oldestPr() ? Math.floor(ageDays(this.oldestPr()!)) : 0,
  );
  protected readonly oldestRepo = computed(() => this.oldestPr()?.repository);
}
