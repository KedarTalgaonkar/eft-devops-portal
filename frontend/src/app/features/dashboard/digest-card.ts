import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient, httpResource } from '@angular/common/http';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { apiUrl } from '../../core/api';
import { Ai, ExecutiveDigest, StoredInsight } from '../../core/ai';
import { Auth } from '../../core/auth';
import { AiMeta } from '../../shared/ai-meta';
import { StatusChip } from '../../shared/status-chip';

/** The latest AI-written executive digest: headline, summary, what improved, risks, next actions, one line per area. */
@Component({
  selector: 'app-digest-card',
  // no empty slot (and no page gap) when AI is off
  host: { '[style.display]': "ai.enabled() ? 'block' : 'none'" },
  imports: [DatePipe, MatIconModule, MatProgressBarModule, AiMeta, StatusChip],
  template: `
    @if (ai.enabled()) {
      <section class="card digest" aria-labelledby="digest-title">
        <div class="top">
          <span class="badge"><mat-icon aria-hidden="true">auto_awesome</mat-icon>Executive digest</span>
          @if (current(); as d) {
            <span class="when">{{ d.createdAt | date: 'EEEE d MMMM, HH:mm' }}</span>
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

        @if (current(); as d) {
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
            <ul class="areas" aria-label="By area">
              @for (a of c.areas; track a.area) {
                <li><app-status-chip [status]="a.status" [text]="a.area" /> <span>{{ a.note }}</span></li>
              }
            </ul>
          }

          <app-ai-meta [result]="d" (changed)="written.set($event)" />
        } @else if (latest.isLoading()) {
          <p class="faint">Loading the digest…</p>
        } @else if (latest.error()) {
          <p class="faint">The digest could not be loaded.</p>
        } @else {
          <p class="faint" id="digest-title">No digest yet. {{ isAdmin ? 'Write one now, or wait for the scheduled run.' : 'The first one is written on the next scheduled run.' }}</p>
        }
      </section>
    }
  `,
  styles: `
    .digest { display: flex; flex-direction: column; gap: 14px; border-left: 4px solid #7c6be0;
      background: linear-gradient(135deg, #fbfaff 0%, var(--app-card) 45%); }
    .top { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; }
    .badge { display: inline-flex; align-items: center; gap: 5px; font-size: 12px; font-weight: 700; text-transform: uppercase;
      letter-spacing: .05em; color: #5b47c7; }
    .badge mat-icon { font-size: 16px; width: 16px; height: 16px; }
    .when { font-size: 12.5px; color: var(--app-muted); }
    .headline { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; margin: 0; font-size: 19px; line-height: 1.35;
      color: var(--app-text); }
    .summary { margin: 0; line-height: 1.6; color: var(--app-text); max-width: 110ch; }
    .cols { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }
    @media (max-width: 1000px) { .cols { grid-template-columns: 1fr; } }
    .col { border: 1px solid var(--app-line); border-radius: 12px; padding: 12px 14px; background: var(--app-card); }
    .col h3 { display: flex; align-items: center; gap: 6px; margin: 0 0 8px; font-size: 13px; text-transform: uppercase;
      letter-spacing: .04em; color: var(--app-muted); }
    .col h3 mat-icon { font-size: 18px; width: 18px; height: 18px; }
    .good h3 mat-icon { color: var(--ok); }
    .risk h3 mat-icon { color: var(--bad); }
    .act h3 mat-icon { color: var(--info); }
    ul, ol { margin: 0; padding-left: 18px; line-height: 1.5; font-size: 13.5px; }
    li + li { margin-top: 6px; }
    .risk ul { list-style: none; padding-left: 0; }
    .detail { display: block; color: var(--app-muted); font-size: 12.5px; margin-top: 2px; overflow-wrap: anywhere; }
    .col li { overflow-wrap: anywhere; }
    .owner { display: inline-block; margin-left: 4px; padding: 0 7px; border-radius: 999px; background: var(--neutral-soft);
      color: var(--app-muted); font-size: 11.5px; }
    .areas { list-style: none; padding: 0; display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 8px 16px; }
    .areas li { margin: 0; font-size: 13px; color: var(--app-muted); }
  `,
})
export class DigestCard {
  private readonly http = inject(HttpClient);
  protected readonly ai = inject(Ai);
  protected readonly isAdmin = inject(Auth).can('ADMIN');
  protected readonly writing = signal(false);
  /** Set after writing or voting, so the card updates without reloading. */
  protected readonly written = signal<StoredInsight<ExecutiveDigest> | null>(null);
  protected readonly latest = httpResource<StoredInsight<ExecutiveDigest> | null>(() =>
    this.ai.enabled() ? apiUrl('ai/digest/latest') : undefined,
  );

  protected current(): StoredInsight<ExecutiveDigest> | null {
    return this.written() ?? this.latest.value() ?? null;
  }

  protected write(): void {
    this.writing.set(true);
    this.http.post<StoredInsight<ExecutiveDigest>>(apiUrl('ai/digest'), null).subscribe({
      next: (d) => {
        this.written.set(d);
        this.writing.set(false);
      },
      error: () => this.writing.set(false),
    });
  }
}
