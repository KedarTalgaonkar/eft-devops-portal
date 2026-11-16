import { Component, computed, input, linkedSignal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { apiUrl } from '../../core/api';
import { BarList, StackedBars, StackPoint } from '../../shared/charts';
import { LoadState } from '../../shared/load-state';
import { CommitActivity, RepositoryInfo, TeamSummary } from './github.models';

/** Support view: repositories of a team, with commit activity per day and top authors. */
@Component({
  selector: 'app-github-repositories',
  imports: [DatePipe, LoadState, StackedBars, BarList],
  template: `
    <div class="filters">
      <div class="chips" role="group" aria-label="Team">
        @for (t of teams.value(); track t.slug) {
          <button
            type="button"
            class="chip-btn"
            [attr.aria-pressed]="t.slug === selectedTeam()"
            (click)="pickTeam(t.slug)"
          >
            {{ t.name }}
          </button>
        }
      </div>
    </div>
    <app-load-state
      [loading]="repos.isLoading()"
      [error]="repos.error()"
      what="repositories"
      (retry)="repos.reload()"
    />
    @if (repos.hasValue()) {
      <div class="table-wrap">
        <table class="data" aria-label="Repositories">
          <thead>
            <tr>
              <th>Repository</th>
              <th>Language</th>
              <th>Default branch</th>
              <th class="num">Commits · 30 days</th>
              <th class="num">Open PRs</th>
              <th>Last push</th>
            </tr>
          </thead>
          <tbody>
            @for (r of repos.value(); track r.fullName) {
              <tr
                class="clickable"
                tabindex="0"
                [class.selected]="r.name === selectedRepo()"
                (click)="repo.set(r.name)"
                (keydown.enter)="repo.set(r.name)"
              >
                <td>
                  <a
                    [href]="r.url"
                    target="_blank"
                    rel="noopener"
                    (click)="$event.stopPropagation()"
                    >{{ r.fullName }}</a
                  >
                </td>
                <td>{{ r.language }}</td>
                <td class="mono">{{ r.defaultBranch }}</td>
                <td class="num">{{ r.commitsLast30Days }}</td>
                <td class="num">{{ r.openPullRequests }}</td>
                <td>{{ r.lastPushAt | date: 'dd MMM, HH:mm' }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }
    @if (commits.hasValue()) {
      @let c = commits.value();
      <section class="grid-2">
        <div class="card">
          <div class="card-title">
            Commits per day · {{ c.repository }} · {{ c.totalCommits }} in {{ c.days }} days
          </div>
          <app-stacked-bars
            [series]="[{ key: 'commits', label: 'Commits', color: 'var(--eu-navy-600)' }]"
            [points]="points()"
            [ariaLabel]="'Commits per day for ' + c.repository"
          />
        </div>
        <div class="card">
          <div class="card-title">Top authors</div>
          <app-bar-list [items]="authors()" color="var(--eu-teal)" />
        </div>
      </section>
    }
  `,
})
export class GithubRepositoriesPage {
  readonly team = input<string>();
  readonly repoParam = input<string | undefined>(undefined, { alias: 'repo' });
  protected readonly chosenTeam = linkedSignal(() => this.team() ?? null);
  protected readonly repo = linkedSignal<string | null>(() => this.repoParam() ?? null);

  protected readonly teams = httpResource<TeamSummary[]>(() => apiUrl('github/teams'), {
    defaultValue: [],
  });
  protected readonly selectedTeam = computed(
    () => this.chosenTeam() ?? this.teams.value()[0]?.slug ?? null,
  );
  protected readonly repos = httpResource<RepositoryInfo[]>(() => {
    const t = this.selectedTeam();
    return t ? apiUrl(`github/teams/${encodeURIComponent(t)}/repos`) : undefined;
  });
  protected readonly selectedRepo = computed(
    () => this.repo() ?? (this.repos.hasValue() ? this.repos.value()[0]?.name : null) ?? null,
  );
  protected readonly commits = httpResource<CommitActivity>(() => {
    const r = this.selectedRepo();
    return r ? apiUrl(`github/repos/${encodeURIComponent(r)}/commits`, { days: 30 }) : undefined;
  });
  protected readonly points = computed<StackPoint[]>(() =>
    (this.commits.value()?.perDay ?? []).map((d) => ({
      label: new Date(d.date).toLocaleDateString('en-IN', { day: '2-digit', month: 'short' }),
      values: { commits: d.commits },
    })),
  );
  protected readonly authors = computed(() =>
    (this.commits.value()?.topAuthors ?? []).slice(0, 6).map((a) => ({
      label: a.author,
      value: a.commits,
      hint: 'commits in 30 days · click for their pull requests',
      link: '../pulls',
      query: { q: a.author, state: 'all' },
    })),
  );

  protected pickTeam(slug: string): void {
    this.chosenTeam.set(slug);
    this.repo.set(null);
  }
}
