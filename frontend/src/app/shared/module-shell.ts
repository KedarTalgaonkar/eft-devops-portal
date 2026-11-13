import { Component, input } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { ToolLogo, ToolName } from './tool-logo';

export interface ModuleTab {
  path: string;
  label: string;
  icon: string;
  /** Who the tab is for, shown as a hint: executive overview vs support detail. */
  audience?: 'overview' | 'detail';
}

/**
 * Frame of every module: navy hero band with the tool logos, title and actions, then routable tabs
 * (Overview for leadership, detail tabs for support teams), then the active tab.
 */
@Component({
  selector: 'app-module-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatIconModule, ToolLogo],
  template: `
    <header class="hero">
      <div class="hero-inner">
        <div class="title-row">
          @if (logos().length) {
            <span class="logos">
              @for (l of logos(); track l) {
                <app-tool-logo [name]="l" [size]="40" [tile]="true" />
              }
            </span>
          }
          <div class="titles">
            <span class="eyebrow">{{ eyebrow() }}</span>
            <h1>{{ title() }}</h1>
            @if (subtitle()) {
              <p>{{ subtitle() }}</p>
            }
          </div>
          <div class="actions"><ng-content select="[actions]" /></div>
        </div>
        <nav class="tabs" [attr.aria-label]="title() + ' views'">
          @for (t of tabs(); track t.path) {
            <a [routerLink]="t.path" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: false }" class="tab"
               queryParamsHandling="preserve">
              <mat-icon aria-hidden="true">{{ t.icon }}</mat-icon>
              {{ t.label }}
              @if (t.audience === 'overview') {
                <span class="aud">Exec</span>
              }
            </a>
          }
        </nav>
      </div>
    </header>
    <div class="page">
      <ng-content />
      <router-outlet />
    </div>
  `,
  styles: `
    :host { display: block; }
    .hero { background: var(--eu-navy-800) url('/img/network-pattern.svg') right -40px center / auto 150% no-repeat; color: #fff; }
    .hero-inner { max-width: 1680px; margin: 0 auto; padding: 22px 28px 0; box-sizing: border-box; }
    .title-row { display: flex; flex-wrap: wrap; align-items: center; gap: 16px; }
    .logos { display: inline-flex; gap: 8px; }
    .titles { flex: 1 1 320px; min-width: 0; }
    .eyebrow { font-size: 11px; font-weight: 600; letter-spacing: .14em; text-transform: uppercase; color: var(--eu-teal); }
    h1 { color: #fff; font-size: 26px; font-weight: 600; margin: 2px 0 2px; }
    p { margin: 0; color: rgba(255,255,255,.75); font-size: 14px; }
    .actions { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; }
    .tabs { display: flex; gap: 4px; margin-top: 18px; overflow-x: auto; scrollbar-width: none; }
    .tabs::-webkit-scrollbar { display: none; }
    .tab { display: inline-flex; align-items: center; gap: 6px; padding: 10px 16px; color: rgba(255,255,255,.78);
      text-decoration: none; font-weight: 500; border-radius: 10px 10px 0 0; white-space: nowrap; }
    .tab mat-icon { font-size: 18px; width: 18px; height: 18px; }
    .tab:hover { color: #fff; background: rgba(255,255,255,.06); }
    .tab.active { background: var(--app-bg); color: var(--eu-navy-700); font-weight: 600; }
    .aud { font-size: 10px; font-weight: 700; letter-spacing: .08em; text-transform: uppercase; padding: 1px 6px; border-radius: 6px;
      background: rgba(0,163,161,.18); color: var(--eu-teal); }
    .tab.active .aud { background: var(--eu-teal-soft); }
  `,
})
export class ModuleShell {
  readonly title = input.required<string>();
  readonly eyebrow = input('Euronet India · EFT');
  readonly subtitle = input<string>();
  readonly logos = input<ToolName[]>([]);
  readonly tabs = input<ModuleTab[]>([]);
}
