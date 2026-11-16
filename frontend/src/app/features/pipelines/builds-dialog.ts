import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { apiUrl } from '../../core/api';
import { Ai } from '../../core/ai';
import { DurationPipe } from '../../shared/duration.pipe';
import { LoadState } from '../../shared/load-state';
import { StatusChip } from '../../shared/status-chip';
import { BuildExplanationPanel } from './build-explanation';
import { BuildView, PipelineMetric } from './pipelines.models';

const EXPLAINABLE = new Set(['FAILURE', 'UNSTABLE', 'ABORTED']);

/** Latest Jenkins builds of one project in one environment, with an AI explanation for failed ones. */
@Component({
  selector: 'app-builds-dialog',
  imports: [
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    DatePipe,
    DurationPipe,
    StatusChip,
    LoadState,
    BuildExplanationPanel,
  ],
  template: `
    <h2 mat-dialog-title>{{ data.project }} · {{ data.environment }}</h2>
    <mat-dialog-content>
      <p class="muted">
        Jenkins job <code>{{ data.jenkinsJob }}</code> · latest 50 builds
        @if (ai.enabled()) {
          · use <strong>Explain</strong> on a failed build for the likely cause and fix
        }
      </p>
      <app-load-state
        [loading]="builds.isLoading()"
        [error]="builds.error()"
        [empty]="builds.hasValue() && builds.value().length === 0"
        what="builds"
        emptyText="No builds found for this job."
        (retry)="builds.reload()"
      />
      @if (builds.hasValue() && builds.value().length) {
        <div class="table-wrap">
          <table class="data">
            <thead>
              <tr>
                <th class="num">#</th>
                <th>Result</th>
                <th>Started</th>
                <th class="num">Duration</th>
                <th>Version</th>
                <th>By</th>
                <th>Deployed</th>
                @if (ai.enabled()) {
                  <th><span class="sr-only">AI explanation</span></th>
                }
              </tr>
            </thead>
            <tbody>
              @for (b of builds.value(); track b.number) {
                <tr [class.open]="open() === b.number">
                  <td class="num">
                    @if (b.url) {
                      <a [href]="b.url" target="_blank" rel="noopener">{{ b.number }}</a>
                    } @else {
                      {{ b.number }}
                    }
                  </td>
                  <td><app-status-chip [status]="b.result" /></td>
                  <td>{{ b.startedAt | date: 'dd MMM yyyy, HH:mm' }}</td>
                  <td class="num">{{ b.durationSeconds | duration }}</td>
                  <td>{{ b.version ?? '—' }}</td>
                  <td>{{ b.triggeredBy ?? '—' }}</td>
                  <td>{{ b.deployment ? 'Yes' : '' }}</td>
                  @if (ai.enabled()) {
                    <td class="ai-cell">
                      @if (explainable(b)) {
                        <button
                          type="button"
                          class="explain"
                          [class.on]="open() === b.number"
                          (click)="toggle(b.number)"
                          [attr.aria-expanded]="open() === b.number"
                          [matTooltip]="
                            open() === b.number
                              ? 'Hide the explanation'
                              : 'Ask AI why this build failed'
                          "
                        >
                          <mat-icon aria-hidden="true">auto_awesome</mat-icon
                          >{{ open() === b.number ? 'Hide' : 'Explain' }}
                        </button>
                      }
                    </td>
                  }
                </tr>
                @if (open() === b.number) {
                  <tr class="explain-row">
                    <td colspan="8">
                      <app-build-explanation
                        [applicationId]="data.applicationId"
                        [environment]="data.environment"
                        [buildNumber]="b.number"
                      />
                    </td>
                  </tr>
                }
              }
            </tbody>
          </table>
        </div>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close type="button">Close</button>
    </mat-dialog-actions>
  `,
  styles: `
    .ai-cell {
      text-align: right;
    }
    .explain {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      border: 1px solid #cfc8ff;
      background: #f5f3ff;
      color: #5b47c7;
      border-radius: 999px;
      padding: 3px 10px;
      font: inherit;
      font-size: 12.5px;
      font-weight: 600;
      cursor: pointer;
      white-space: nowrap;
    }
    .explain:hover,
    .explain.on {
      background: #ebe7ff;
      border-color: #a99cf5;
    }
    .explain mat-icon {
      font-size: 15px;
      width: 15px;
      height: 15px;
    }
    tr.open > td {
      background: #faf9ff;
    }
    .explain-row > td {
      padding: 4px 8px 14px;
      background: #faf9ff;
    }
    .sr-only {
      position: absolute;
      width: 1px;
      height: 1px;
      overflow: hidden;
      clip: rect(0 0 0 0);
      white-space: nowrap;
    }
  `,
})
export class BuildsDialog {
  protected readonly data = inject<PipelineMetric>(MAT_DIALOG_DATA);
  protected readonly ai = inject(Ai);
  protected readonly open = signal<number | null>(null);
  protected readonly builds = httpResource<BuildView[]>(() =>
    apiUrl(`pipelines/${this.data.applicationId}/${this.data.environment}/builds`, { limit: 50 }),
  );

  protected explainable(b: BuildView): boolean {
    return EXPLAINABLE.has(b.result);
  }

  protected toggle(n: number): void {
    this.open.set(this.open() === n ? null : n);
  }
}
