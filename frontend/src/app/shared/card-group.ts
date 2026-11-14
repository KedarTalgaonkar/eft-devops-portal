import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';

/**
 * A labelled group of cards: icon, heading and one sentence saying what the cards are for, with an optional link
 * to the detail view. Wrap a `.grid` (KPI cards) or a row of chart cards in it.
 */
@Component({
  selector: 'app-card-group',
  imports: [RouterLink, MatIconModule],
  host: { role: 'region', '[attr.aria-label]': 'heading()' },
  template: `
    <header class="head">
      @if (icon()) {
        <span class="icon" aria-hidden="true"><mat-icon>{{ icon() }}</mat-icon></span>
      }
      <div class="text">
        <h2>{{ heading() }}</h2>
        @if (description()) {
          <p>{{ description() }}</p>
        }
      </div>
      @if (link()) {
        <a class="link-more" [routerLink]="link()">{{ linkLabel() }} →</a>
      }
    </header>
    <ng-content />
  `,
  styles: `
    :host { display: flex; flex-direction: column; gap: 12px; min-width: 0; }
    .head { display: flex; align-items: center; gap: 12px; padding-bottom: 10px; border-bottom: 1px solid var(--app-line); }
    .icon { flex: none; display: inline-flex; align-items: center; justify-content: center; width: 34px; height: 34px;
      border-radius: 10px; background: var(--eu-navy-600); color: #fff; }
    .icon mat-icon { font-size: 19px; width: 19px; height: 19px; }
    .text { flex: 1; min-width: 0; }
    h2 { margin: 0; font-size: 15.5px; font-weight: 600; color: var(--app-text); line-height: 1.3; }
    p { margin: 2px 0 0; font-size: 13px; color: var(--app-muted); line-height: 1.4; }
    .link-more { flex: none; align-self: center; }
  `,
})
export class CardGroup {
  readonly heading = input.required<string>();
  readonly description = input<string>();
  readonly icon = input<string>();
  readonly link = input<string>();
  readonly linkLabel = input('Details');
}
