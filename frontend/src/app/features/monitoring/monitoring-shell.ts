import { Component } from '@angular/core';
import { ModuleShell, ModuleTab } from '../../shared/module-shell';

@Component({
  selector: 'app-monitoring-shell',
  imports: [ModuleShell],
  template: `
    <app-module-shell
      heading="Operations monitoring"
      subtitle="Grafana alerts and dashboards, Prometheus targets and host capacity"
      [logos]="['grafana', 'prometheus']"
      [tabs]="tabs"
    />
  `,
})
export class MonitoringShell {
  protected readonly tabs: ModuleTab[] = [
    { path: 'overview', label: 'Overview', icon: 'insights', audience: 'overview' },
    { path: 'alerts', label: 'Alerts', icon: 'notifications_active' },
    { path: 'infrastructure', label: 'Infrastructure', icon: 'dns' },
  ];
}
