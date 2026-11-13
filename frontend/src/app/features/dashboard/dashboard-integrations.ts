import { Component } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { MatIconModule } from '@angular/material/icon';
import { apiUrl } from '../../core/api';
import { IntegrationStatus } from '../../core/models';
import { LoadState } from '../../shared/load-state';
import { StatusChip } from '../../shared/status-chip';
import { ToolLogo, ToolName } from '../../shared/tool-logo';

const LOGOS: Record<string, ToolName> = {
  jenkins: 'jenkins',
  github: 'github',
  sonar: 'sonarqube',
  artifactory: 'jfrog',
  monitoring: 'grafana',
};

/** Which data sources feed the portal, live or sample, and whether they answer. */
@Component({
  selector: 'app-dashboard-integrations',
  imports: [MatIconModule, LoadState, StatusChip, ToolLogo],
  template: `
    <app-load-state [loading]="list.isLoading()" [error]="list.error()" what="integrations" (retry)="list.reload()" />
    <div class="grid-3">
      @for (i of list.value(); track i.key) {
        <div class="card integ">
          <div class="head">
            @if (logos[i.key]; as l) {
              <app-tool-logo [name]="l" [size]="34" />
            } @else {
              <mat-icon aria-hidden="true">policy</mat-icon>
            }
            <div class="name">
              <strong>{{ i.name }}</strong>
              <span class="muted">{{ i.endpoint ?? 'No endpoint configured' }}</span>
            </div>
          </div>
          <div class="row">
            <app-status-chip [status]="i.mode" [text]="i.mode === 'LIVE' ? 'Live data' : 'Sample data'" />
            <app-status-chip [status]="i.reachable ? 'UP' : 'DOWN'" [text]="i.reachable ? 'Reachable' : 'Unreachable'" />
          </div>
          <p class="muted">{{ i.message }}</p>
        </div>
      }
    </div>
    <div class="card hint">
      <strong>Switching a source to live data</strong>
      <p class="muted">Set <span class="mono">&lt;TOOL&gt;_MODE=live</span> with its URL and token in <span class="mono">.env</span>,
        then restart the backend container. See <span class="mono">docs/integrations.md</span>.</p>
    </div>
  `,
  styles: `
    .integ { display: flex; flex-direction: column; gap: 12px; }
    .head { display: flex; align-items: center; gap: 12px; }
    .head mat-icon { font-size: 34px; width: 34px; height: 34px; color: var(--eu-navy-600); }
    .name { display: flex; flex-direction: column; min-width: 0; }
    .name span { font-size: 12.5px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    p { margin: 0; font-size: 13px; }
    .hint p { margin-top: 6px; }
  `,
})
export class DashboardIntegrationsPage {
  protected readonly logos = LOGOS;
  protected readonly list = httpResource<IntegrationStatus[]>(() => apiUrl('dashboard/integrations'), { defaultValue: [] });
}
