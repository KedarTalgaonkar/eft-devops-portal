import { Component, computed, signal } from '@angular/core';
import { DatePipe, TitleCasePipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { apiUrl } from '../../core/api';
import { ago } from '../../core/models';
import { LoadState } from '../../shared/load-state';
import { StatusChip } from '../../shared/status-chip';
import { PullRequestInfo } from './github.models';

/** Support view: pull requests across team repositories, oldest waiting first. */
@Component({
  selector: 'app-github-pulls',
  imports: [DatePipe, TitleCasePipe, LoadState, StatusChip],
  template: `
    <div class="filters">
      <div class="chips" role="group" aria-label="Pull request state">
        @for (s of states; track s) {
          <button type="button" class="chip-btn" [attr.aria-pressed]="state() === s" (click)="state.set(s)">{{ s | titlecase }}</button>
        }
      </div>
      <input class="search" type="search" placeholder="Search repository, title or author" aria-label="Search pull requests"
             [value]="search()" (input)="search.set($any($event.target).value)" />
    </div>
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
                <td [title]="(p.createdAt | date: 'dd MMM yyyy, HH:mm') ?? ''">{{ ago(p.createdAt) }}</td>
                <td>{{ ago(p.updatedAt) }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }
  `,
})
export class GithubPullsPage {
  protected readonly ago = ago;
  protected readonly states = ['open', 'closed', 'all'] as const;
  protected readonly state = signal<'open' | 'closed' | 'all'>('open');
  protected readonly search = signal('');
  protected readonly pulls = httpResource<PullRequestInfo[]>(() => apiUrl('github/pulls', { state: this.state() }), { defaultValue: [] });
  protected readonly rows = computed(() => {
    const q = this.search().trim().toLowerCase();
    return this.pulls
      .value()
      .filter((p) => !q || [p.repository, p.title, p.author].some((v) => v.toLowerCase().includes(q)))
      .sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime());
  });
}
