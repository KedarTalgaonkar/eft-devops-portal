import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { filter } from 'rxjs';
import { BrandMark } from '../shared/brand-mark';
import { ToolLogo } from '../shared/tool-logo';
import { AnnouncementBar } from '../shared/announcement-bar';
import { Auth } from './auth';
import { NAV_GROUPS } from './navigation';

/**
 * Signed-in frame: navy sidebar (CSS grid on desktop, off-canvas drawer on small screens) with the user card,
 * and the routed module on the right.
 */
@Component({
  selector: 'app-portal-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatIconModule, MatTooltipModule, BrandMark, ToolLogo, AnnouncementBar],
  templateUrl: './portal-layout.html',
  styleUrl: './portal-layout.scss',
})
export class PortalLayout {
  protected readonly auth = inject(Auth);
  protected readonly navOpen = signal(false);

  protected readonly groups = computed(() => NAV_GROUPS.filter((g) => !g.requires || this.auth.can(g.requires)));
  protected readonly initials = computed(() => {
    const name = this.auth.user()?.displayName ?? '';
    return name.split(/[\s.@_-]+/).filter(Boolean).slice(0, 2).map((p) => p[0].toUpperCase()).join('') || '?';
  });
  protected readonly topRole = computed(() => {
    const roles = this.auth.user()?.roles ?? [];
    return roles.includes('ADMIN') ? 'Administrator' : roles.includes('EDITOR') ? 'Editor' : 'Viewer';
  });

  constructor() {
    inject(Router)
      .events.pipe(filter((e) => e instanceof NavigationEnd), takeUntilDestroyed())
      .subscribe(() => this.navOpen.set(false));
  }

  protected signOut(): void {
    void this.auth.logout();
  }
}
