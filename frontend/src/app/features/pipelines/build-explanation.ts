import { Component, OnInit, inject, input, signal } from '@angular/core';
import { DatePipe, TitleCasePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { apiUrl, errorMessage } from '../../core/api';
import { Auth } from '../../core/auth';
import { BuildDiagnosis, BuildExplanation, CATEGORY_LABEL, OWNER_LABEL, StoredInsight } from '../../core/ai';
import { AiMeta } from '../../shared/ai-meta';

/**
 * AI explanation of one failed build. Opening it asks the backend, which returns the stored answer when the build
 * was explained before (instant, no cost) or analyses the masked log now.
 */
@Component({
  selector: 'app-build-explanation',
  imports: [DatePipe, TitleCasePipe, MatIconModule, MatProgressBarModule, AiMeta],
  template: `
    <section class="panel" aria-live="polite" [attr.aria-busy]="loading()">
      @if (loading()) {
        <div class="loading">
          <mat-icon aria-hidden="true">auto_awesome</mat-icon>
          <span>{{ refreshing() ? 'Analysing the log again…' : 'Reading the build log and looking for the root cause…' }}</span>
        </div>
        <mat-progress-bar mode="indeterminate" aria-label="Analysing" />
      } @else if (error()) {
        <div class="error" role="alert">
          <mat-icon aria-hidden="true">error_outline</mat-icon>
          <span>{{ error() }}</span>
          <button class="btn" type="button" (click)="load(false)">Try again</button>
        </div>
      } @else if (data(); as x) {
        @let d = x.result.content;
        <header class="head">
          <span class="cat" [class]="'cat ' + d.category.toLowerCase()">{{ category[d.category] }}</span>
          <h3>{{ d.summary }}</h3>
        </header>
        <div class="facts">
          <span class="fact"><mat-icon aria-hidden="true">groups</mat-icon>Who acts: <strong>{{ owner[d.owner] }}</strong></span>
          <span class="fact"><mat-icon aria-hidden="true">insights</mat-icon>Confidence: <strong>{{ d.confidence | titlecase }}</strong></span>
          @if (d.notACodeProblem) {
            <span class="fact infra"><mat-icon aria-hidden="true">dns</mat-icon>Probably not a code problem</span>
          }
          @if (x.logTruncated) {
            <span class="fact faint">Analysed the last part of a {{ (x.logBytes / 1024 / 1024).toFixed(1) }} MB log</span>
          }
        </div>

        <div class="cols">
          <div>
            <h4>Likely cause</h4>
            <p>{{ d.likelyCause }}</p>
            @if (d.evidence.length) {
              <h4>Evidence from the log</h4>
              <div class="evidence" role="group" aria-label="Log lines">
                @for (line of d.evidence; track $index) {
                  <code>{{ line }}</code>
                }
              </div>
            }
          </div>
          <div>
            <h4>How to fix it</h4>
            <ol class="steps">
              @for (s of d.fixSteps; track $index) {
                <li>{{ s }}</li>
              }
            </ol>
            @if (x.similar.length) {
              <h4>Same problem before in this job</h4>
              <ul class="similar">
                @for (s of x.similar; track s.buildNumber) {
                  <li><strong>#{{ s.buildNumber }}</strong> · {{ s.explainedAt | date: 'dd MMM' }} · {{ s.summary }}</li>
                }
              </ul>
            }
          </div>
        </div>

        <footer class="foot">
          <app-ai-meta [result]="x.result" (changed)="voted($event)" />
          @if (canRefresh) {
            <button class="btn" type="button" (click)="load(true)"><mat-icon>refresh</mat-icon>Analyse again</button>
          }
        </footer>
      }
    </section>
  `,
  styles: `
    .panel { display: flex; flex-direction: column; gap: 12px; padding: 16px 18px; border-radius: 12px; border: 1px solid #d9d4ff;
      background: linear-gradient(180deg, #f7f6ff, var(--app-card) 70%); white-space: normal; }
    .loading, .error { display: flex; align-items: center; gap: 8px; color: var(--app-muted); }
    .loading mat-icon { color: #6d5bd0; }
    .error { color: var(--bad); }
    .error .btn { margin-left: auto; }
    .head { display: flex; flex-direction: column; gap: 6px; }
    .head h3 { margin: 0; font-size: 16px; line-height: 1.35; color: var(--app-text); }
    .cat { align-self: flex-start; font-size: 11.5px; font-weight: 700; letter-spacing: .02em; text-transform: uppercase;
      padding: 2px 9px; border-radius: 999px; background: var(--bad-soft); color: var(--bad); }
    .cat.deployment, .cat.infrastructure, .cat.timeout, .cat.dependency { background: var(--warn-soft); color: var(--warn); }
    .cat.quality_gate { background: var(--info-soft); color: var(--info); }
    .cat.unknown { background: var(--neutral-soft); color: var(--app-muted); }
    .facts { display: flex; flex-wrap: wrap; gap: 8px 18px; font-size: 13px; color: var(--app-muted); }
    .fact { display: inline-flex; align-items: center; gap: 5px; }
    .fact mat-icon { font-size: 16px; width: 16px; height: 16px; }
    .fact strong { color: var(--app-text); }
    .fact.infra { color: var(--warn); font-weight: 600; }
    .cols { display: grid; grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr); gap: 20px; }
    @media (max-width: 760px) { .cols { grid-template-columns: 1fr; } }
    h4 { margin: 0 0 6px; font-size: 12px; text-transform: uppercase; letter-spacing: .04em; color: var(--app-muted); }
    p { margin: 0 0 12px; line-height: 1.5; }
    .evidence { display: flex; flex-direction: column; gap: 2px; margin: 0; padding: 10px 12px; border-radius: 8px;
      background: #0f1b33; max-height: 220px; overflow: auto; }
    .evidence code { color: #e6ebf5; font-family: var(--font-mono); font-size: 12px; line-height: 1.55; white-space: pre-wrap;
      word-break: break-word; background: none; padding: 0; }
    .steps { margin: 0 0 12px; padding-left: 20px; line-height: 1.5; }
    .steps li + li { margin-top: 4px; }
    .similar { margin: 0; padding-left: 18px; font-size: 13px; color: var(--app-muted); }
    .foot { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; border-top: 1px solid var(--app-line); padding-top: 10px; }
    .foot app-ai-meta { flex: 1; min-width: 0; }
  `,
})
export class BuildExplanationPanel implements OnInit {
  private readonly http = inject(HttpClient);
  readonly applicationId = input.required<number>();
  readonly environment = input.required<string>();
  readonly buildNumber = input.required<number>();

  protected readonly category = CATEGORY_LABEL;
  protected readonly owner = OWNER_LABEL;
  protected readonly canRefresh = inject(Auth).can('EDITOR');
  protected readonly data = signal<BuildExplanation | null>(null);
  protected readonly loading = signal(false);
  protected readonly refreshing = signal(false);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.load(false);
  }

  protected load(refresh: boolean): void {
    this.loading.set(true);
    this.refreshing.set(refresh);
    this.error.set(null);
    const url = apiUrl(`ai/builds/${this.applicationId()}/${this.environment()}/${this.buildNumber()}/explain`, { refresh: refresh || null });
    this.http.post<BuildExplanation>(url, null).subscribe({
      next: (x) => {
        this.data.set(x);
        this.loading.set(false);
      },
      error: (e: unknown) => {
        this.error.set(errorMessage(e));
        this.loading.set(false);
      },
    });
  }

  protected voted(r: StoredInsight<BuildDiagnosis>): void {
    const x = this.data();
    if (x) {
      this.data.set({ ...x, result: r });
    }
  }
}
