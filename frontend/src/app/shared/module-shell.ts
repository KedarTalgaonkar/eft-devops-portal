import { Component, inject, input } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { DataMode } from '../core/data-mode';
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
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatIconModule, MatTooltipModule, ToolLogo],
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
            <span class="eyebrow-row">
              <span class="eyebrow">{{ eyebrow() }}</span>
              @if (dataMode.label(); as m) {
                <a class="data-mode" [class.live]="m === 'Live data'" routerLink="/dashboard/integrations" [matTooltip]="dataMode.explanation()">
                  <mat-icon aria-hidden="true">{{ m === 'Live data' ? 'sensors' : 'science' }}</mat-icon>{{ m }}
                </a>
              }
            </span>
            <h1>{{ heading() }}</h1>
            @if (subtitle()) {
              <p>{{ subtitle() }}</p>
            }
          </div>
          <div class="actions"><ng-content select="[actions]" /></div>
        </div>
        <nav class="tabs" [attr.aria-label]="heading() + ' views'">
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
    .eyebrow-row { display: flex; align-items: center; flex-wrap: wrap; gap: 6px 12px; }
    .data-mode { display: inline-flex; align-items: center; gap: 4px; padding: 1px 9px; border-radius: 999px; font-size: 11px; font-weight: 600;
      background: rgba(245, 158, 11, 0.16); color: var(--eu-saffron); text-decoration: none; border: 1px solid rgba(245, 158, 11, 0.35); }
    .data-mode.live { background: rgba(0, 163, 161, 0.18); color: var(--eu-teal); border-color: rgba(0, 163, 161, 0.4); }
    .data-mode:hover { filter: brightness(1.15); }
    .data-mode mat-icon { font-size: 13px; width: 13px; height: 13px; }
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
  /** Page heading. Deliberately not called "title": that would also become a native title attribute on the whole page. */
  readonly heading = input.required<string>();
  readonly eyebrow = input('Euronet India · EFT');
  protected readonly dataMode = inject(DataMode);
  readonly subtitle = input<string>();
  readonly logos = input<ToolName[]>([]);
  readonly tabs = input<ModuleTab[]>([]);
}
