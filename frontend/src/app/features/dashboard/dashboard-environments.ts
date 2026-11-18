import { Component, computed, inject, signal } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { MatTooltipModule } from '@angular/material/tooltip';
import { apiUrl } from '../../core/api';
import { Environments } from '../../core/environments';
import { Auth } from '../../core/auth';
import { LoadState } from '../../shared/load-state';
import { StatusChip } from '../../shared/status-chip';
import { EnvironmentCell, EnvironmentStatusRow } from './dashboard.models';

/** Support view: every application × environment (columns follow the environment catalogue). */
@Component({
  selector: 'app-dashboard-environments',
  imports: [RouterLink, MatTooltipModule, LoadState, StatusChip],
  template: `
    <div class="filters">
      <input
        class="search"
        type="search"
        placeholder="Search application, client or owner"
        aria-label="Search applications"
        [value]="search()"
        (input)="search.set($any($event.target).value)"
      />
      <span class="legend muted"
        ><span class="dot up">●</span> all contexts up <span class="dot down">▲</span> a context is
        down <span class="dot unk">○</span> not probed</span
      >
    </div>
    <app-load-state
      [loading]="grid.isLoading()"
      [error]="grid.error()"
      [empty]="grid.hasValue() && rows().length === 0"
      what="environment status"
      emptyText="No applications. Import the inventory first."
      (retry)="grid.reload()"
    />
    @if (rows().length) {
      <div class="table-wrap">
        <table class="data" aria-label="Environment status">
          <thead>
            <tr>
              <th>Application</th>
              <th>Owner</th>
              @for (e of envs.active(); track e.code) {
                <th>{{ e.name }}</th>
              }
            </tr>
          </thead>
          <tbody>
            @for (row of rows(); track row.applicationId) {
              <tr>
                <td>
                  <strong>{{ row.project }}</strong> <span class="muted">· {{ row.client }}</span>
                </td>
                <td>{{ row.owner ?? '—' }}</td>
                @for (e of envs.active(); track e.code) {
                  <td>
                    @if (cell(row, e.code); as c) {
                      <span class="cell">
                        <span
                          class="dot"
                          [class.up]="c.health === 'UP'"
                          [class.down]="c.health === 'DOWN'"
                          [class.unk]="c.health === 'UNKNOWN'"
                          [matTooltip]="
                            c.up + ' up, ' + c.down + ' down of ' + c.deployments + ' contexts'
                          "
                          >{{ c.health === 'DOWN' ? '▲' : c.health === 'UP' ? '●' : '○' }}</span
                        >
                        @if (seesPipelines) {
                        <a
                          [routerLink]="'/pipelines/all'"
                          [queryParams]="{ q: row.project }"
                          class="build"
                        >
                          <app-status-chip
                            [status]="c.lastBuildResult"
                            [text]="c.lastBuildResult ? undefined : 'No builds'"
                          />
                        </a>
                        @if (c.successRate !== null) {
                          <span class="muted num">{{ c.successRate }}%</span>
                        }
                        }
                      </span>
                    } @else {
                      <span class="faint">—</span>
                    }
                  </td>
                }
              </tr>
            }
          </tbody>
        </table>
      </div>
    }
  `,
  styles: `
    .cell {
      display: inline-flex;
      align-items: center;
      gap: 8px;
    }
    .build {
      text-decoration: none;
    }
    .dot {
      font-size: 13px;
    }
    .dot.up {
      color: var(--ok);
    }
    .dot.down {
      color: var(--bad);
    }
    .dot.unk {
      color: var(--app-faint);
    }
    .legend {
      font-size: 12.5px;
      display: inline-flex;
      gap: 6px;
      align-items: center;
    }
  `,
})
export class DashboardEnvironmentsPage {
  /** Build results are part of Pipelines: hidden without access to it. */
  protected readonly seesPipelines = inject(Auth).canView('PIPELINES');
  protected readonly envs = inject(Environments);
  protected readonly search = signal('');
  protected readonly grid = httpResource<EnvironmentStatusRow[]>(() =>
    apiUrl('dashboard/environments'),
  );
  protected readonly rows = computed(() => {
    const q = this.search().trim().toLowerCase();
    const list = this.grid.hasValue() ? this.grid.value() : [];
    return q
      ? list.filter((r) =>
          [r.project, r.client, r.owner ?? ''].some((v) => v.toLowerCase().includes(q)),
        )
      : list;
  });

  protected cell(row: EnvironmentStatusRow, env: string): EnvironmentCell | undefined {
    return row.cells.find((c) => c.environment === env);
  }
}
