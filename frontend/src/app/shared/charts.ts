import { Component, computed, input } from '@angular/core';
import { DecimalPipe, NgTemplateOutlet } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatTooltipModule } from '@angular/material/tooltip';

/** Tiny inline trend line for KPI cards. */
@Component({
  selector: 'app-sparkline',
  styles: ':host { display: block; min-width: 0; } svg { display: block; }',
  template: `
    @if (points()) {
      <svg [attr.viewBox]="'0 0 ' + width() + ' ' + height()" width="100%" [attr.height]="height()" preserveAspectRatio="none" aria-hidden="true">
        <polyline [attr.points]="area()" [attr.fill]="color()" fill-opacity="0.12" stroke="none" />
        <polyline [attr.points]="points()" fill="none" [attr.stroke]="color()" stroke-width="1.8" stroke-linejoin="round" stroke-linecap="round" />
      </svg>
    }
  `,
})
export class Sparkline {
  readonly values = input<number[]>([]);
  readonly color = input('var(--eu-navy-600)');
  readonly width = input(120);
  readonly height = input(32);

  protected readonly points = computed(() => {
    const v = this.values();
    if (v.length < 2) {
      return '';
    }
    const max = Math.max(...v, 1);
    const min = Math.min(...v, 0);
    const w = this.width();
    const h = this.height() - 2;
    return v
      .map((y, i) => `${((i / (v.length - 1)) * w).toFixed(1)},${(h - ((y - min) / (max - min || 1)) * (h - 2) + 1).toFixed(1)}`)
      .join(' ');
  });
  protected readonly area = computed(() => (this.points() ? `0,${this.height()} ${this.points()} ${this.width()},${this.height()}` : ''));
}

export interface StackSeries {
  key: string;
  label: string;
  color: string;
}

export interface StackPoint {
  label: string;
  values: Record<string, number>;
}

/** Stacked daily bars with a legend and hover titles; scales to the container width. */
@Component({
  selector: 'app-stacked-bars',
  template: `
    <div class="legend">
      @for (s of series(); track s.key) {
        <span><i [style.background]="s.color"></i>{{ s.label }}</span>
      }
    </div>
    <div class="plot" role="img" [attr.aria-label]="ariaLabel()">
      <div class="axis">
        <span>{{ max() }}</span><span>{{ (max() / 2) | number: '1.0-0' }}</span><span>0</span>
      </div>
      <div class="bars">
        @for (p of points(); track p.label) {
          <div class="col" [matTooltip]="tooltip(p)">
            @for (s of series(); track s.key) {
              @if (p.values[s.key]) {
                <div class="seg" [style.height.%]="(p.values[s.key] / max()) * 100" [style.background]="s.color"></div>
              }
            }
          </div>
        }
      </div>
    </div>
    <div class="x">
      <span>{{ points()[0]?.label }}</span>
      <span>{{ points()[points().length - 1]?.label }}</span>
    </div>
  `,
  styles: `
    :host { display: block; }
    .legend { display: flex; flex-wrap: wrap; gap: 14px; font-size: 12px; color: var(--app-muted); margin-bottom: 10px; }
    .legend i { display: inline-block; width: 10px; height: 10px; border-radius: 3px; margin-right: 6px; vertical-align: -1px; }
    .plot { display: flex; gap: 8px; height: 180px; }
    .axis { display: flex; flex-direction: column; justify-content: space-between; font-size: 11px; color: var(--app-faint); font-family: var(--font-mono); text-align: right; min-width: 24px; }
    .bars { flex: 1; display: flex; align-items: flex-end; gap: 3px; border-bottom: 1px solid var(--app-line-strong);
      background: repeating-linear-gradient(to top, transparent 0, transparent calc(50% - 1px), var(--app-line) calc(50% - 1px), var(--app-line) 50%); }
    .col { flex: 1 1 0; display: flex; flex-direction: column-reverse; height: 100%; min-width: 3px; }
    .seg { width: 100%; }
    .seg:last-child { border-radius: 3px 3px 0 0; }
    .col:hover { filter: brightness(1.12); }
    .x { display: flex; justify-content: space-between; font-size: 11px; color: var(--app-faint); padding-left: 32px; margin-top: 4px; }
  `,
  imports: [DecimalPipe, MatTooltipModule],
})
export class StackedBars {
  readonly series = input.required<StackSeries[]>();
  readonly points = input.required<StackPoint[]>();
  readonly ariaLabel = input('Trend chart');

  protected readonly max = computed(() => {
    const keys = this.series().map((s) => s.key);
    return Math.max(1, ...this.points().map((p) => keys.reduce((n, k) => n + (p.values[k] ?? 0), 0)));
  });

  protected tooltip(p: StackPoint): string {
    return `${p.label}: ` + this.series().map((s) => `${s.label} ${p.values[s.key] ?? 0}`).join(', ');
  }
}

export interface Slice extends ChartLink {
  label: string;
  value: number;
  color: string;
}

/** Donut with a centre figure and a legend; legend entries with a link open the matching detail view. */
@Component({
  selector: 'app-donut',
  imports: [RouterLink],
  template: `
    <div class="wrap">
      <svg viewBox="0 0 42 42" [attr.width]="size()" [attr.height]="size()" role="img" [attr.aria-label]="ariaLabel()">
        <circle cx="21" cy="21" r="15.915" fill="none" stroke="var(--app-line)" stroke-width="5" />
        @for (a of arcs(); track a.label) {
          <circle cx="21" cy="21" r="15.915" fill="none" [attr.stroke]="a.color" stroke-width="5"
                  [attr.stroke-dasharray]="a.dash" [attr.stroke-dashoffset]="a.offset" />
        }
        <text x="21" y="20.5" text-anchor="middle" class="big">{{ centre() ?? total() }}</text>
        <text x="21" y="26" text-anchor="middle" class="small">{{ centreLabel() }}</text>
      </svg>
      <ul class="legend">
        @for (s of slices(); track s.label) {
          <li>
            @if (s.link) {
              <a [routerLink]="s.link" [queryParams]="s.query ?? null" [attr.aria-label]="s.label + ': ' + s.value">
                <i [style.background]="s.color"></i><span class="lbl">{{ s.label }}</span><b class="num">{{ s.value }}</b>
              </a>
            } @else {
              <i [style.background]="s.color"></i><span>{{ s.label }}</span><b class="num">{{ s.value }}</b>
            }
          </li>
        }
      </ul>
    </div>
  `,
  styles: `
    .wrap { display: flex; align-items: center; gap: 18px; flex-wrap: wrap; }
    .big { font-size: 8px; font-weight: 700; fill: var(--app-text); font-family: 'IBM Plex Sans', sans-serif; }
    .small { font-size: 3.2px; fill: var(--app-muted); font-family: 'IBM Plex Sans', sans-serif; text-transform: uppercase; letter-spacing: .1em; }
    .legend { list-style: none; margin: 0; padding: 0; display: grid; gap: 6px; min-width: 150px; font-size: 13px; }
    .legend li, .legend a { display: grid; grid-template-columns: 12px 1fr auto; align-items: center; gap: 8px; }
    .legend li:has(> a) { display: block; }
    .legend a { margin: -3px -6px; padding: 3px 6px; border-radius: 6px; color: inherit; text-decoration: none; }
    .legend a:hover, .legend a:focus-visible { background: var(--neutral-soft); }
    .legend a:hover .lbl { color: var(--eu-navy-600); text-decoration: underline; }
    .legend i { width: 10px; height: 10px; border-radius: 3px; }
  `,
})
export class Donut {
  readonly slices = input.required<Slice[]>();
  readonly size = input(132);
  readonly centre = input<string | number | null>(null);
  readonly centreLabel = input('total');
  readonly ariaLabel = input('Distribution chart');

  protected readonly total = computed(() => this.slices().reduce((n, s) => n + s.value, 0));
  protected readonly arcs = computed(() => {
    const total = this.total() || 1;
    let acc = 0;
    return this.slices()
      .filter((s) => s.value > 0)
      .map((s) => {
        const pct = (s.value / total) * 100;
        const arc = { label: s.label, color: s.color, dash: `${pct} ${100 - pct}`, offset: `${25 - acc}` };
        acc += pct;
        return arc;
      });
  });
}

/** Router link with optional query parameters, for chart items that open a filtered detail view. */
export interface ChartLink {
  link?: string;
  query?: Record<string, string | number | boolean | null | undefined>;
}

export interface BarItem extends ChartLink {
  label: string;
  value: number;
  hint?: string;
  color?: string;
}

/** Ranked horizontal bars ("top N" lists). Items with a link open the matching detail view. */
@Component({
  selector: 'app-bar-list',
  imports: [MatTooltipModule, RouterLink, NgTemplateOutlet],
  template: `
    <ng-template #bar let-b>
      <div class="head"><span class="label" [matTooltip]="b.label" [matTooltipDisabled]="b.label.length < 28">{{ b.label }}</span>
        <b class="num">{{ b.value }}{{ unit() }}</b></div>
      <div class="track"><div class="fill" [style.width.%]="(b.value / max()) * 100" [style.background]="b.color ?? color()"></div></div>
      @if (b.hint) {
        <div class="hint">{{ b.hint }}</div>
      }
    </ng-template>
    <ul>
      @for (b of items(); track b.label) {
        <li>
          @if (b.link) {
            <a class="item" [routerLink]="b.link" [queryParams]="b.query ?? null" [attr.aria-label]="b.label + ': ' + b.value + unit()">
              <ng-container [ngTemplateOutlet]="bar" [ngTemplateOutletContext]="{ $implicit: b }" />
            </a>
          } @else {
            <ng-container [ngTemplateOutlet]="bar" [ngTemplateOutletContext]="{ $implicit: b }" />
          }
        </li>
      }
    </ul>
  `,
  styles: `
    ul { list-style: none; margin: 0; padding: 0; display: grid; gap: 6px; }
    li { padding: 3px 0; }
    a.item { display: block; margin: -4px -8px; padding: 4px 8px; border-radius: 8px; color: inherit; text-decoration: none; }
    a.item:hover, a.item:focus-visible { background: var(--neutral-soft); }
    a.item:hover .label { color: var(--eu-navy-600); text-decoration: underline; }
    .head { display: flex; justify-content: space-between; gap: 12px; font-size: 13px; }
    .label { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .track { height: 8px; border-radius: 4px; background: var(--neutral-soft); margin-top: 5px; overflow: hidden; }
    .fill { height: 100%; border-radius: 4px; }
    .hint { font-size: 12px; color: var(--app-muted); margin-top: 3px; }
  `,
})
export class BarList {
  readonly items = input.required<BarItem[]>();
  readonly color = input('var(--eu-navy-600)');
  readonly unit = input('');
  readonly maxValue = input<number | null>(null);

  protected readonly max = computed(() => this.maxValue() ?? Math.max(1, ...this.items().map((i) => i.value)));
}
