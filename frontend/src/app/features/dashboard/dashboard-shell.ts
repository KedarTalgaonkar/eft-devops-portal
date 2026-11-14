import { Component, computed, inject } from '@angular/core';
import { Ai } from '../../core/ai';
import { ModuleShell, ModuleTab } from '../../shared/module-shell';

@Component({
  selector: 'app-dashboard-shell',
  imports: [ModuleShell],
  template: `
    <app-module-shell heading="EFT command centre"
                      subtitle="Delivery, quality, security and service health of every EFT application in one place"
                      [logos]="['jenkins', 'github', 'sonarqube', 'jfrog', 'grafana']" [tabs]="tabs()" />
  `,
})
export class DashboardShell {
  private readonly ai = inject(Ai);

  /** The executive digest tab appears only when AI features are on. */
  protected readonly tabs = computed<ModuleTab[]>(() => [
    { path: 'overview', label: 'Overview', icon: 'space_dashboard', audience: 'overview' },
    ...(this.ai.enabled() ? [{ path: 'digest', label: 'Executive digest', icon: 'auto_awesome', audience: 'overview' as const }] : []),
    { path: 'issues', label: 'Issues', icon: 'report' },
    { path: 'environments', label: 'Environment status', icon: 'grid_view' },
    { path: 'services', label: 'Service health', icon: 'monitor_heart' },
    { path: 'integrations', label: 'Integrations', icon: 'hub' },
    { path: 'announcements', label: 'Announcements', icon: 'campaign' },
  ]);
}
