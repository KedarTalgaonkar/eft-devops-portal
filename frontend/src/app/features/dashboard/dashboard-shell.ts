import { Component } from '@angular/core';
import { ModuleShell, ModuleTab } from '../../shared/module-shell';

@Component({
  selector: 'app-dashboard-shell',
  imports: [ModuleShell],
  template: `
    <app-module-shell title="EFT command centre"
                      subtitle="Delivery, quality, security and service health of every EFT application in one place"
                      [logos]="['jenkins', 'github', 'sonarqube', 'jfrog', 'grafana']" [tabs]="tabs" />
  `,
})
export class DashboardShell {
  protected readonly tabs: ModuleTab[] = [
    { path: 'overview', label: 'Overview', icon: 'space_dashboard', audience: 'overview' },
    { path: 'issues', label: 'Issues', icon: 'report' },
    { path: 'environments', label: 'Environment status', icon: 'grid_view' },
    { path: 'services', label: 'Service health', icon: 'monitor_heart' },
    { path: 'integrations', label: 'Integrations', icon: 'hub' },
  ];
}
