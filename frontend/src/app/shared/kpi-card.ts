import { Component, computed, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { Sparkline } from './charts';

/**
 * Executive KPI: big figure, change against the previous period (coloured by whether that is good),
 * an optional sparkline and a one-line explanation.
 */
@Component({
  selector: 'app-kpi-card',
  imports: [MatIconModule, Sparkline],
  template: `
    <div class="card kpi" [class]="'card kpi tone-' + (tone() ?? 'none')">
      <div class="top">
        <span class="label">
          @if (icon()) {
            <mat-icon aria-hidden="true">{{ icon() }}</mat-icon>
          }
          {{ label() }}
        </span>
        @if (deltaText(); as d) {
          <span class="delta" [class.good]="deltaGood() === true" [class.bad]="deltaGood() === false" [title]="'vs previous period: ' + previous()">
            {{ d }}
          </span>
        }
      </div>
      <div class="value-row">
        <span class="value">{{ value() ?? '—' }}<small>{{ unit() }}</small></span>
        @if (trend().length > 1) {
          <app-sparkline [values]="trend()" [color]="sparkColor()" />
        }
      </div>
      @if (hint()) {
        <div class="hint">{{ hint() }}</div>
      }
    </div>
  `,
  styles: `
    .kpi { display: flex; flex-direction: column; gap: 6px; height: 100%; position: relative; overflow: hidden; }
    .kpi::before { content: ''; position: absolute; inset: 0 auto 0 0; width: 4px; background: var(--app-line-strong); }
    .tone-ok::before { background: var(--ok); }
    .tone-bad::before { background: var(--bad); }
    .tone-warn::before { background: var(--warn); }
    .tone-info::before { background: var(--eu-teal); }
    .top { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
    .label { display: inline-flex; align-items: center; gap: 6px; color: var(--app-muted); font-size: 12.5px; font-weight: 600; text-transform: uppercase; letter-spacing: .05em; }
    .label mat-icon { font-size: 18px; width: 18px; height: 18px; }
    .delta { font-size: 12px; font-weight: 600; padding: 2px 8px; border-radius: 999px; background: var(--neutral-soft); color: var(--app-muted); font-family: var(--font-mono); }
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
