import { Component, computed, input } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Sparkline } from './charts';

/**
 * Executive KPI: big figure with its change against the previous period on the same line (coloured by whether
 * that is good), an optional sparkline and a one-line explanation. With `link` the whole card opens the detail
 * view behind the number (optionally filtered with `query`).
 */
@Component({
  selector: 'app-kpi-card',
  imports: [NgTemplateOutlet, RouterLink, MatIconModule, MatTooltipModule, Sparkline],
  template: `
    <ng-template #body>
      <div class="top">
        <span class="label">
          @if (icon()) {
            <mat-icon aria-hidden="true">{{ icon() }}</mat-icon>
          }
          {{ label() }}
        </span>
        @if (link()) {
          <mat-icon class="go" aria-hidden="true">arrow_forward</mat-icon>
        }
      </div>
      <div class="value-row">
        <span class="figure">
          <span class="value">{{ value() ?? '—' }}<small>{{ unit() }}</small></span>
          @if (deltaText(); as d) {
            <span class="delta" [class.good]="deltaGood() === true" [class.bad]="deltaGood() === false"
                  [matTooltip]="'Previous period: ' + previous() + unit()">{{ d }}</span>
          }
        </span>
        @if (trend().length > 1) {
          <app-sparkline [values]="trend()" [color]="sparkColor()" />
        }
      </div>
      @if (hint()) {
        <div class="hint">{{ hint() }}</div>
      }
    </ng-template>

    @if (link(); as l) {
      <a class="card kpi linked" [class]="'card kpi linked tone-' + (tone() ?? 'none')" [routerLink]="l" [queryParams]="query()"
         [attr.aria-label]="label() + ': ' + (value() ?? '—') + unit() + '. ' + (linkHint() ?? 'Open details')">
        <ng-container [ngTemplateOutlet]="body" />
      </a>
    } @else {
      <div class="card kpi" [class]="'card kpi tone-' + (tone() ?? 'none')">
        <ng-container [ngTemplateOutlet]="body" />
      </div>
    }
  `,
  styles: `
    :host { display: block; min-width: 0; }
    .kpi { display: flex; flex-direction: column; gap: 6px; height: 100%; position: relative; overflow: hidden; }
    a.kpi { color: inherit; text-decoration: none; cursor: pointer; transition: border-color .15s, box-shadow .15s, transform .15s; }
    a.kpi:hover, a.kpi:focus-visible { border-color: var(--eu-navy-400); box-shadow: 0 6px 22px rgba(15, 27, 51, 0.13); transform: translateY(-1px); }
    .go { font-size: 18px; width: 18px; height: 18px; color: var(--app-faint); transition: color .15s, transform .15s; }
    a.kpi:hover .go { color: var(--eu-navy-600); transform: translateX(2px); }
    .figure { display: inline-flex; align-items: baseline; flex-wrap: nowrap; gap: 10px; min-width: 0; flex: none; }
    app-sparkline { flex: 0 1 120px; min-width: 36px; }
    .kpi::before { content: ''; position: absolute; inset: 0 auto 0 0; width: 4px; background: var(--app-line-strong); }
    .tone-ok::before { background: var(--ok); }
    .tone-bad::before { background: var(--bad); }
    .tone-warn::before { background: var(--warn); }
    .tone-info::before { background: var(--eu-teal); }
    .top { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
    .label { display: inline-flex; align-items: center; gap: 6px; color: var(--app-muted); font-size: 12.5px; font-weight: 600; text-transform: uppercase; letter-spacing: .05em; }
    .label mat-icon { font-size: 18px; width: 18px; height: 18px; }
    .delta { font-size: 12px; font-weight: 600; padding: 2px 8px; border-radius: 999px; background: var(--neutral-soft); color: var(--app-muted);
      font-family: var(--font-mono); white-space: nowrap; position: relative; top: -3px; }
    .delta.good { background: var(--ok-soft); color: var(--ok); }
    .delta.bad { background: var(--bad-soft); color: var(--bad); }
    .value-row { display: flex; align-items: flex-end; justify-content: space-between; gap: 8px; }
    .value { font-size: 30px; font-weight: 600; line-height: 1.1; font-variant-numeric: tabular-nums; letter-spacing: -0.02em; }
    .value small { font-size: 15px; font-weight: 500; color: var(--app-muted); margin-left: 3px; }
    .tone-bad .value { color: var(--bad); }
    .tone-warn .value { color: var(--warn); }
    .hint { font-size: 12.5px; color: var(--app-muted); }
  `,
})
export class KpiCard {
  readonly label = input.required<string>();
  readonly value = input<string | number | null>();
  readonly unit = input('');
  readonly hint = input<string>();
  readonly icon = input<string>();
  /** ok | bad | warn | info: accent bar and value colour. */
  readonly tone = input<string | null>();
  readonly current = input<number | null>(null);
  readonly previous = input<number | null>(null);
  /** Is a higher number better? Decides the colour of the change badge. */
  readonly higherIsBetter = input(true);
  /** 'pts' for percentages (absolute points), '%' for relative change. */
  readonly deltaMode = input<'pts' | '%'>('%');
  readonly trend = input<number[]>([]);
  /** Router link to the detail view behind the number; makes the whole card clickable. */
  readonly link = input<string | null>(null);
  readonly query = input<Record<string, string | number | boolean | null | undefined> | null>(null);
  /** Read out to screen readers, e.g. "Show failing pipelines". */
  readonly linkHint = input<string>();

  protected readonly deltaValue = computed(() => {
    const c = this.current();
    const p = this.previous();
    if (c === null || p === null || p === undefined || c === undefined) {
      return null;
    }
    if (this.deltaMode() === 'pts') {
      return Math.round((c - p) * 10) / 10;
    }
    return p === 0 ? null : Math.round(((c - p) / p) * 1000) / 10;
  });
  protected readonly deltaText = computed(() => {
    const d = this.deltaValue();
    if (d === null) {
      return null;
    }
    const arrow = d > 0 ? '▲' : d < 0 ? '▼' : '■';
    return `${arrow} ${Math.abs(d)}${this.deltaMode() === 'pts' ? ' pts' : '%'}`;
  });
  protected readonly deltaGood = computed(() => {
    const d = this.deltaValue();
    if (d === null || d === 0) {
      return null;
    }
    return d > 0 === this.higherIsBetter();
  });
  protected readonly sparkColor = computed(() =>
    this.tone() === 'bad' ? 'var(--bad)' : this.tone() === 'warn' ? 'var(--warn)' : 'var(--eu-navy-600)',
  );
}
