import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient, httpResource } from '@angular/common/http';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { apiUrl } from '../../core/api';
import { Ai, ExecutiveDigest, StoredInsight } from '../../core/ai';
import { Auth } from '../../core/auth';
import { AiMeta } from '../../shared/ai-meta';
import { LoadState } from '../../shared/load-state';
import { StatusChip } from '../../shared/status-chip';

type Digest = StoredInsight<ExecutiveDigest>;

/**
 * Command centre → Executive digest tab: the AI-written status report for leadership, with earlier digests to
 * look back at.
 */
@Component({
  selector: 'app-dashboard-digest',
  imports: [DatePipe, MatIconModule, MatProgressBarModule, AiMeta, LoadState, StatusChip],
  template: `
    @if (!ai.status.hasValue()) {
      <app-load-state [loading]="ai.status.isLoading()" [error]="ai.status.error()" what="AI status" (retry)="ai.status.reload()" />
    } @else if (!ai.enabled()) {
      <div class="card off">
        <mat-icon aria-hidden="true">auto_awesome</mat-icon>
        <div><strong>AI features are switched off on this portal.</strong>
          <p class="muted">An administrator can turn them on with AI_MODE (see docs/ai.md).</p></div>
      </div>
    } @else {
      <div class="layout">
        <section class="card digest" aria-labelledby="digest-title">
          <div class="top">
            <span class="badge"><mat-icon aria-hidden="true">auto_awesome</mat-icon>Executive digest</span>
            @if (shown(); as d) {
              <span class="when">{{ d.createdAt | date: 'EEEE d MMMM yyyy, HH:mm' }}</span>
              @if (!isLatest()) {
                <span class="older">Earlier digest</span>
              }
            }
            <span class="spacer"></span>
            @if (isAdmin) {
              <button class="btn" type="button" [disabled]="writing()" (click)="write()">
                <mat-icon>edit_note</mat-icon>{{ writing() ? 'Writing…' : 'Write a new digest' }}
              </button>
            }
          </div>
          @if (writing()) {
            <mat-progress-bar mode="indeterminate" aria-label="Writing the digest" />
          }

          @if (shown(); as d) {
            @let c = d.content;
            <h2 id="digest-title" class="headline">
              <app-status-chip [status]="c.overall" [text]="c.overall === 'GREEN' ? 'On track' : c.overall === 'AMBER' ? 'Needs attention' : 'Action required'" />
              {{ c.headline }}
            </h2>
            <p class="summary">{{ c.summary }}</p>

            <div class="cols">
              <div class="col good">
                <h3><mat-icon aria-hidden="true">trending_up</mat-icon>Improved</h3>
                @if (c.improvements.length) {
                  <ul>
                    @for (i of c.improvements; track $index) { <li>{{ i }}</li> }
                  </ul>
                } @else {
                  <p class="faint">No measurable improvement this period.</p>
                }
              </div>
              <div class="col risk">
                <h3><mat-icon aria-hidden="true">report</mat-icon>Top risks</h3>
                <ul>
                  @for (r of c.risks; track $index) {
                    <li><app-status-chip [status]="r.severity" /> <strong>{{ r.title }}</strong><span class="detail">{{ r.detail }}</span></li>
                  } @empty {
                    <li class="faint">No open risks.</li>
                  }
                </ul>
              </div>
              <div class="col act">
                <h3><mat-icon aria-hidden="true">task_alt</mat-icon>Next actions</h3>
                <ol>
                  @for (a of c.actions; track $index) {
                    <li>{{ a.action }} <span class="owner">{{ a.owner }}</span></li>
                  } @empty {
                    <li class="faint">Nothing to do.</li>
                  }
                </ol>
              </div>
            </div>

            @if (c.areas.length) {
              <div class="areas-block">
                <h3><mat-icon aria-hidden="true">traffic</mat-icon>By area</h3>
                <ul class="areas">
                  @for (a of c.areas; track a.area) {
                    <li><app-status-chip [status]="a.status" [text]="a.area" /> <span>{{ a.note }}</span></li>
                  }
                </ul>
              </div>
            }

            <app-ai-meta [result]="d" (changed)="replace($event)" />
          } @else if (history.isLoading()) {
            <p class="faint">Loading the digest…</p>
          } @else if (history.error()) {
            <p class="faint">The digest could not be loaded.</p>
          } @else {
            <p class="faint" id="digest-title">No digest yet. {{ isAdmin ? 'Write one now, or wait for the scheduled run.' : 'The first one is written on the next scheduled run.' }}</p>
          }
        </section>

        <aside class="card history" aria-label="Earlier digests">
          <div class="card-title">Earlier digests</div>
          <p class="faint small">Written {{ ai.status.value()?.digestEnabled ? 'every weekday morning' : 'on demand' }}. Pick one to read it.</p>
          <ul>
            @for (h of items(); track h.id) {
              <li>
                <button type="button" [class.on]="h.id === shown()?.id" (click)="selected.set(h.id)" [attr.aria-current]="h.id === shown()?.id">
                  <app-status-chip [status]="h.content.overall" [text]="h.content.overall === 'GREEN' ? 'On track' : h.content.overall === 'AMBER' ? 'Watch' : 'Act'" />
                  <span class="h-date">{{ h.createdAt | date: 'EEE d MMM, HH:mm' }}</span>
                  <span class="h-line">{{ h.content.headline }}</span>
                </button>
              </li>
            } @empty {
              <li class="faint">None yet.</li>
            }
          </ul>
        </aside>
      </div>
    }
  `,
  styles: `
    .off { display: flex; gap: 12px; align-items: flex-start; }
    .off mat-icon { color: #6d5bd0; }
    .off p { margin: 4px 0 0; }
    .layout { display: grid; grid-template-columns: minmax(0, 1fr) 300px; gap: 20px; align-items: start; }
    @media (max-width: 1100px) { .layout { grid-template-columns: 1fr; } }
    .digest { display: flex; flex-direction: column; gap: 14px; border-left: 4px solid #7c6be0;
      background: linear-gradient(135deg, #fbfaff 0%, var(--app-card) 45%); }
    .top { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; }
    .badge { display: inline-flex; align-items: center; gap: 5px; font-size: 12px; font-weight: 700; text-transform: uppercase;
      letter-spacing: .05em; color: #5b47c7; }
    .badge mat-icon { font-size: 16px; width: 16px; height: 16px; }
    .when { font-size: 12.5px; color: var(--app-muted); }
    .older { font-size: 11.5px; padding: 1px 8px; border-radius: 999px; background: var(--warn-soft); color: var(--warn); font-weight: 600; }
    .headline { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; margin: 0; font-size: 19px; line-height: 1.35; }
    .summary { margin: 0; line-height: 1.6; max-width: 110ch; }
    .cols { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }
    @media (max-width: 1300px) { .cols { grid-template-columns: 1fr; } }
    .col { border: 1px solid var(--app-line); border-radius: 12px; padding: 12px 14px; background: var(--app-card); }
    h3 { display: flex; align-items: center; gap: 6px; margin: 0 0 8px; font-size: 13px; text-transform: uppercase;
      letter-spacing: .04em; color: var(--app-muted); }
    h3 mat-icon { font-size: 18px; width: 18px; height: 18px; }
    .good h3 mat-icon { color: var(--ok); }
    .risk h3 mat-icon { color: var(--bad); }
    .act h3 mat-icon { color: var(--info); }
    .col ul, .col ol { margin: 0; padding-left: 18px; line-height: 1.5; font-size: 13.5px; }
    .col li { overflow-wrap: anywhere; }
    .col li + li { margin-top: 6px; }
    .risk ul { list-style: none; padding-left: 0; }
    .detail { display: block; color: var(--app-muted); font-size: 12.5px; margin-top: 2px; overflow-wrap: anywhere; }
    .owner { display: inline-block; margin-left: 4px; padding: 0 7px; border-radius: 999px; background: var(--neutral-soft);
      color: var(--app-muted); font-size: 11.5px; }
    .areas { list-style: none; margin: 0; padding: 0; display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 8px 16px; }
    .areas li { font-size: 13px; color: var(--app-muted); }
    .history { display: flex; flex-direction: column; gap: 6px; }
    .history .small { margin: 0 0 6px; font-size: 12.5px; }
    .history ul { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 6px; }
    .history button { width: 100%; text-align: left; display: grid; grid-template-columns: auto 1fr; gap: 2px 8px; align-items: center;
      padding: 8px 10px; border: 1px solid var(--app-line); border-radius: 10px; background: var(--app-card); cursor: pointer; font: inherit; }
    .history button:hover { border-color: var(--eu-navy-400); }
    .history button.on { border-color: #7c6be0; background: #f7f6ff; }
    .h-date { font-size: 12.5px; font-weight: 600; color: var(--app-text); }
    .h-line { grid-column: 1 / -1; font-size: 12.5px; color: var(--app-muted); line-height: 1.35; }
  `,
})
export class DashboardDigestPage {
  private readonly http = inject(HttpClient);
  protected readonly ai = inject(Ai);
  protected readonly isAdmin = inject(Auth).can('ADMIN');
  protected readonly writing = signal(false);
  protected readonly selected = signal<number | null>(null);
  /** Newly written / re-rated digests, shown without reloading the list. */
  private readonly updates = signal<Digest[]>([]);

  protected readonly history = httpResource<Digest[]>(() => (this.ai.enabled() ? apiUrl('ai/digest', { limit: 20 }) : undefined), {
    defaultValue: [],
  });

  protected readonly items = computed(() => {
    const byId = new Map<number, Digest>();
    for (const d of [...this.history.value(), ...this.updates()]) {
      byId.set(d.id, d);
    }
    return [...byId.values()].sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  });
  protected readonly shown = computed(() => {
    const list = this.items();
    return list.find((d) => d.id === this.selected()) ?? list[0] ?? null;
  });
  protected readonly isLatest = computed(() => this.shown()?.id === this.items()[0]?.id);

  protected write(): void {
    this.writing.set(true);
    this.http.post<Digest>(apiUrl('ai/digest'), null).subscribe({
      next: (d) => {
        this.replace(d);
        this.selected.set(d.id);
        this.writing.set(false);
      },
      error: () => this.writing.set(false),
    });
  }

  protected replace(d: Digest): void {
    this.updates.update((list) => [...list.filter((x) => x.id !== d.id), d]);
  }
}
