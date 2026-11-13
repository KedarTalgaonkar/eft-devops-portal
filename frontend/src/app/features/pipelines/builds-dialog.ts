import { Component, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { apiUrl } from '../../core/api';
import { DurationPipe } from '../../shared/duration.pipe';
import { LoadState } from '../../shared/load-state';
import { StatusChip } from '../../shared/status-chip';
import { BuildView, PipelineMetric } from './pipelines.models';

/** Latest Jenkins builds of one project in one environment. */
@Component({
  selector: 'app-builds-dialog',
  imports: [MatDialogModule, MatButtonModule, DatePipe, DurationPipe, StatusChip, LoadState],
  template: `
    <h2 mat-dialog-title>{{ data.project }} · {{ data.environment }}</h2>
    <mat-dialog-content>
      <p class="muted">Jenkins job <code>{{ data.jenkinsJob }}</code> · latest 50 builds</p>
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
              </tr>
            </thead>
            <tbody>
              @for (b of builds.value(); track b.number) {
                <tr>
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
                </tr>
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
})
export class BuildsDialog {
  protected readonly data = inject<PipelineMetric>(MAT_DIALOG_DATA);
  protected readonly builds = httpResource<BuildView[]>(() =>
    apiUrl(`pipelines/${this.data.applicationId}/${this.data.environment}/builds`, { limit: 50 }),
  );
}
