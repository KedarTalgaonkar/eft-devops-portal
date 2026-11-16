import { Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { apiUrl } from '../core/api';
import { Announcement, AnnouncementFeed, KIND_META, windowText } from '../core/announcements';

const DISMISSED_KEY = 'eft.dismissedAnnouncements';
const REFRESH_MS = 2 * 60_000;

function readDismissed(): string[] {
  try {
    const raw = localStorage.getItem(DISMISSED_KEY);
    return raw ? (JSON.parse(raw) as string[]) : [];
  } catch {
    return [];
  }
}

/**
 * Header banner with the announcements that are live right now (refreshed every 2 minutes). Notices, releases and
 * maintenance can be hidden per user until they change; incidents stay until they end.
 */
@Component({
  selector: 'app-announcement-bar',
  imports: [RouterLink, MatIconModule, MatTooltipModule],
  template: `
    @if (visible().length) {
      <div class="bar" role="region" aria-label="Announcements">
        @for (a of visible(); track a.id) {
          <div
            class="item"
            [class]="'item ' + a.kind.toLowerCase()"
            [attr.role]="a.kind === 'INCIDENT' ? 'alert' : 'status'"
          >
            <mat-icon class="kind-icon" aria-hidden="true">{{ meta[a.kind].icon }}</mat-icon>
            <span class="kind">{{ meta[a.kind].label }}</span>
            <span class="msg">{{ a.message }}</span>
            <span class="when">{{ window(a) }}</span>
            @if (a.linkUrl) {
              @if (a.linkUrl.startsWith('/')) {
                <a class="link" [routerLink]="a.linkUrl">{{ a.linkLabel }} →</a>
              } @else {
                <a class="link" [href]="a.linkUrl" target="_blank" rel="noopener noreferrer"
                  >{{ a.linkLabel }} ↗</a
                >
              }
            }
            @if (a.kind !== 'INCIDENT') {
              <button
                type="button"
                class="close"
                (click)="dismiss(a)"
                aria-label="Hide this announcement"
                matTooltip="Hide"
              >
                <mat-icon aria-hidden="true">close</mat-icon>
              </button>
            }
          </div>
        }
      </div>
    }
  `,
  styles: `
    .bar {
      display: flex;
      flex-direction: column;
    }
    .item {
      display: flex;
      align-items: center;
      flex-wrap: wrap;
      gap: 4px 10px;
      padding: 9px 20px;
      font-size: 13.5px;
      border-bottom: 1px solid rgba(15, 27, 51, 0.08);
    }
    .item.info {
      background: var(--info-soft);
      color: var(--eu-navy-800);
    }
    .item.release {
      background: var(--eu-teal-soft);
      color: #064e4d;
    }
    .item.maintenance {
      background: var(--warn-soft);
      color: #6b4300;
    }
    .item.incident {
      background: var(--bad);
      color: #fff;
    }
    .kind-icon {
      font-size: 19px;
      width: 19px;
      height: 19px;
    }
    .kind {
      font-weight: 700;
      text-transform: uppercase;
      font-size: 11.5px;
      letter-spacing: 0.06em;
    }
    .msg {
      flex: 1 1 320px;
      min-width: 0;
      font-weight: 500;
      overflow-wrap: anywhere;
    }
    .when {
      font-size: 12.5px;
      opacity: 0.85;
      white-space: nowrap;
    }
    .link {
      font-weight: 600;
      color: inherit;
      text-decoration: underline;
      white-space: nowrap;
    }
    .close {
      display: inline-flex;
      border: 0;
      background: transparent;
      color: inherit;
      cursor: pointer;
      padding: 3px;
      border-radius: 6px;
    }
    .close:hover {
      background: rgba(15, 27, 51, 0.1);
    }
    .close mat-icon {
      font-size: 18px;
      width: 18px;
      height: 18px;
    }
  `,
})
export class AnnouncementBar {
  protected readonly meta = KIND_META;
  private readonly dismissed = signal<string[]>(readDismissed());
  /** Hidden ones come back when an editor changes them (the key includes the last change time). */
  protected readonly visible = computed(() =>
    this.active
      .value()
      .filter((a) => a.kind === 'INCIDENT' || !this.dismissed().includes(this.key(a))),
  );
  private readonly tick = signal(0);
  private readonly feed = inject(AnnouncementFeed);
  protected readonly active = httpResource<Announcement[]>(
    () => {
      this.tick();
      this.feed.version();
      return apiUrl('announcements/active');
    },
    { defaultValue: [] },
  );

  constructor() {
    const timer = setInterval(() => this.tick.update((n) => n + 1), REFRESH_MS);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
  }

  protected window(a: Announcement): string {
    return windowText(a);
  }

  protected dismiss(a: Announcement): void {
    const next = [...this.dismissed().slice(-50), this.key(a)];
    this.dismissed.set(next);
    try {
      localStorage.setItem(DISMISSED_KEY, JSON.stringify(next));
    } catch {
      // private mode / blocked storage: hidden for this page view only
    }
  }

  private key(a: Announcement): string {
    return `${a.id}:${a.updatedAt ?? a.createdAt}`;
  }
}
