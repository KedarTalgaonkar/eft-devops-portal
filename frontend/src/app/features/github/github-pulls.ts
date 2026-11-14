import { Component, computed, input, linkedSignal } from '@angular/core';
import { DatePipe, TitleCasePipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { MatTooltipModule } from '@angular/material/tooltip';
import { apiUrl } from '../../core/api';
import { ago } from '../../core/models';
import { LoadState } from '../../shared/load-state';
import { StatusChip } from '../../shared/status-chip';
import { AGE_BUCKETS, AgeBucket, ageDays, inBucket } from './github.models';
import { PullRequestInfo } from './github.models';

type PrState = 'open' | 'closed' | 'all';

/**
 * Support view: pull requests across team repositories, oldest waiting first. Filters can come from the URL
 * (?q=repo&age=7-14&reviewers=none&state=open), so overview charts link straight to the matching PRs.
 */
@Component({
  selector: 'app-github-pulls',
  imports: [DatePipe, TitleCasePipe, MatTooltipModule, LoadState, StatusChip],
  template: `
    <div class="filters">
      <div class="chips" role="group" aria-label="Pull request state">
        @for (s of states; track s) {
          <button type="button" class="chip-btn" [attr.aria-pressed]="prState() === s" (click)="prState.set(s)">{{ s | titlecase }}</button>
        }
      </div>
      <div class="chips" role="group" aria-label="Age">
        <button type="button" class="chip-btn" [attr.aria-pressed]="ageFilter() === ''" (click)="ageFilter.set('')">Any age</button>
        @for (b of buckets; track b.key) {
          <button type="button" class="chip-btn" [attr.aria-pressed]="ageFilter() === b.key" (click)="ageFilter.set(b.key)">{{ b.label }}</button>
        }
      </div>
      <label class="row">
        <input type="checkbox" [checked]="noReviewer()" (change)="noReviewer.set($any($event.target).checked)" /> No reviewer yet
      </label>
      <input class="search" type="search" placeholder="Search repository, title or author" aria-label="Search pull requests"
             [value]="search()" (input)="search.set($any($event.target).value)" />
    </div>
    <p class="muted summary">{{ rows().length }} pull request{{ rows().length === 1 ? '' : 's' }}{{ filtered() ? ' match these filters' : '' }}
      @if (filtered()) { · <button type="button" class="link-btn" (click)="clear()">Clear filters</button> }
    </p>
    <app-load-state [loading]="pulls.isLoading()" [error]="pulls.error()" [empty]="pulls.hasValue() && rows().length === 0"
                    what="pull requests" emptyText="No pull requests match." (retry)="pulls.reload()" />
    @if (rows().length) {
      <div class="table-wrap">
        <table class="data" aria-label="Pull requests">
          <thead><tr><th>Repository</th><th class="num">#</th><th>Title</th><th>Author</th><th>State</th><th class="num">Reviewers</th><th>Opened</th><th>Updated</th></tr></thead>
          <tbody>
            @for (p of rows(); track p.repository + p.number) {
              <tr>
                <td>{{ p.repository }}</td>
                <td class="num"><a [href]="p.url" target="_blank" rel="noopener">{{ p.number }}</a></td>
                <td class="wrap">{{ p.title }}{{ p.draft ? ' (draft)' : '' }}</td>
                <td>{{ p.author }}</td>
                <td><app-status-chip [status]="p.state" /></td>
                <td class="num" [class.warn]="p.reviewers === 0">{{ p.reviewers }}</td>
                <td [matTooltip]="(p.createdAt | date: 'dd MMM yyyy, HH:mm') ?? ''">{{ ago(p.createdAt) }}</td>
                <td>{{ ago(p.updatedAt) }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }
  `,
  styles: `
    .summary { margin: 0; font-size: 13px; }
    .link-btn { background: none; border: 0; padding: 0; font: inherit; color: var(--eu-navy-600); text-decoration: underline; cursor: pointer; }
  `,
})
export class GithubPullsPage {
  protected readonly ago = ago;
  protected readonly states = ['open', 'closed', 'all'] as const;
  protected readonly buckets = AGE_BUCKETS;

  readonly q = input<string>();
  readonly age = input<string>();
  readonly reviewers = input<string>();
  readonly state = input<string>();

  protected readonly search = linkedSignal(() => this.q() ?? '');
  protected readonly ageFilter = linkedSignal<AgeBucket | ''>(() => (['0-2', '2-7', '7-14', '14+', '7+'].includes(this.age() ?? '') ? (this.age() as AgeBucket) : ''));
  protected readonly noReviewer = linkedSignal(() => this.reviewers() === 'none');
  protected readonly prState = linkedSignal<PrState>(() => (['open', 'closed', 'all'].includes(this.state() ?? '') ? (this.state() as PrState) : 'open'));

  protected readonly pulls = httpResource<PullRequestInfo[]>(() => apiUrl('github/pulls', { state: this.prState() }), { defaultValue: [] });
  protected readonly filtered = computed(() => !!this.search().trim() || !!this.ageFilter() || this.noReviewer());
  protected readonly rows = computed(() => {
    const q = this.search().trim().toLowerCase();
    const bucket = this.ageFilter();
    return this.pulls
      .value()
      .filter((p) => !q || [p.repository, p.title, p.author].some((v) => v.toLowerCase().includes(q)))
      .filter((p) => !bucket || inBucket(ageDays(p), bucket))
      .filter((p) => !this.noReviewer() || p.reviewers === 0)
      .sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime());
  });

  protected clear(): void {
    this.search.set('');
    this.ageFilter.set('');
    this.noReviewer.set(false);
  }
}
