import { Component, inject } from '@angular/core';
import { EnvFilter } from '../../shared/env-filter';
import { ModuleShell, ModuleTab } from '../../shared/module-shell';
import { PipelineFilters } from './pipeline-filters';

@Component({
  selector: 'app-pipelines-shell',
  imports: [ModuleShell, EnvFilter],
  template: `
    <app-module-shell heading="Delivery pipelines" subtitle="Jenkins builds and deployments per project, for every environment"
                      [logos]="['jenkins', 'tomcat']" [tabs]="tabs">
      <div class="filters">
        <app-env-filter [(value)]="filters.env" />
        <span class="spacer"></span>
        <label class="muted" for="period">Period</label>
        <select id="period" class="select" [value]="filters.days()" (change)="filters.days.set(+$any($event.target).value)">
          @for (d of filters.dayOptions; track d) {
            <option [value]="d">Last {{ d }} days</option>
          }
        </select>
      </div>
    </app-module-shell>
  `,
})
export class PipelinesShell {
  protected readonly filters = inject(PipelineFilters);
  protected readonly tabs: ModuleTab[] = [
    { path: 'overview', label: 'Overview', icon: 'insights', audience: 'overview' },
    { path: 'all', label: 'Pipeline metrics', icon: 'table_rows' },
    { path: 'deployments', label: 'Deployment history', icon: 'history' },
  ];
}
