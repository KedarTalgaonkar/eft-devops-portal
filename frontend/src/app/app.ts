import { Component, computed, inject } from '@angular/core';
import { BreakpointObserver } from '@angular/cdk/layout';
import { httpResource } from '@angular/common/http';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatSidenavModule } from '@angular/material/sidenav';
import { map } from 'rxjs';
import { apiUrl } from './core/api';
import { IntegrationStatus } from './core/models';
import { NAV_GROUPS } from './core/navigation';
import { BrandMark } from './shared/brand-mark';
import { ToolLogo } from './shared/tool-logo';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatSidenavModule, MatIconModule, BrandMark, ToolLogo],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly groups = NAV_GROUPS;
  protected readonly isHandset = toSignal(
    inject(BreakpointObserver)
      .observe('(max-width: 960px)')
      .pipe(map((r) => r.matches)),
    { initialValue: false },
  );

  /** Shows leadership at a glance whether numbers are live or sample data. */
  private readonly integrations = httpResource<IntegrationStatus[]>(() => apiUrl('dashboard/integrations'), { defaultValue: [] });
  protected readonly dataMode = computed(() => {
    const list = this.integrations.value();
    const live = list.filter((i) => i.mode === 'LIVE').length;
    if (!list.length) {
      return null;
    }
    return live === 0 ? 'Sample data' : live === list.length ? 'Live data' : `${live}/${list.length} sources live`;
  });
}
