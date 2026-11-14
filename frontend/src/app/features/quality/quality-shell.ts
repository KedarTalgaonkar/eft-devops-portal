import { Component } from '@angular/core';
import { ModuleShell, ModuleTab } from '../../shared/module-shell';

@Component({
  selector: 'app-quality-shell',
  imports: [ModuleShell],
  template: `
    <app-module-shell heading="Code quality" subtitle="SonarQube quality gates, reliability, security and maintainability"
                      [logos]="['sonarqube']" [tabs]="tabs" />
  `,
})
export class QualityShell {
  protected readonly tabs: ModuleTab[] = [
    { path: 'overview', label: 'Overview', icon: 'insights', audience: 'overview' },
    { path: 'projects', label: 'Projects', icon: 'table_rows' },
  ];
}
