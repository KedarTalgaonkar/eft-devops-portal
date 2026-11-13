import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { Router } from '@angular/router';
import { apiUrl } from '../../core/api';
import { Environments } from '../../core/environments';
import { Severity, ago } from '../../core/models';
import { LoadState } from '../../shared/load-state';
import { StatusChip } from '../../shared/status-chip';
import { AttentionReport } from './dashboard.models';

/** Support view: every open issue across modules, filterable, each linking to where to start. */
@Component({
  selector: 'app-dashboard-issues',
  imports: [DatePipe, LoadState, StatusChip],
  template: `
    <div class="filters">
      <input class="search" type="search" placeholder="Search issue, owner or environment" aria-label="Search issues"
             [value]="search()" (input)="search.set($any($event.target).value)" />
      <div class="chips" role="group" aria-label="Area">
        <button type="button" class="chip-btn" [attr.aria-pressed]="area() === ''" (click)="area.set('')">All areas</button>
        @for (a of areas(); track a) {
          <button type="button" class="chip-btn" [attr.aria-pressed]="area() === a" (click)="area.set(a)">{{ a }}</button>
        }
      </div>
      <select class="select" aria-label="Minimum severity" [value]="minSeverity()" (change)="minSeverity.set($any($event.target).value)">
        <option value="LOW">All severities</option>
        <option value="MEDIUM">Medium and above</option>
        <option value="HIGH">High and above</option>
        <option value="CRITICAL">Critical only</option>
      </select>
    </div>

    <app-load-state [loading]="report.isLoading()" [error]="report.error()" [empty]="report.hasValue() && rows().length === 0"
                    what="issues" emptyText="No open issues match these filters." (retry)="report.reload()" />
    @if (rows().length) {
      <div class="table-wrap">
        <table class="data" aria-label="Open issues">
          <thead>
            <tr><th>Severity</th><th>Area</th><th>Issue</th><th>Details</th><th>Environment</th><th>Owner</th><th>Since</th><th></th></tr>
          </thead>
          <tbody>
            @for (i of rows(); track $index) {
              <tr>
                <td><app-status-chip [status]="i.severity" /></td>
                <td>{{ i.area }}</td>
                <td class="wrap"><strong>{{ i.title }}</strong></td>
                <td class="wrap muted">{{ i.detail }}</td>
                <td>{{ i.environment ? envs.name(i.environment) : '—' }}</td>
                <td>{{ i.owner ?? '—' }}</td>
                <td [title]="(i.since | date: 'dd MMM yyyy, HH:mm') ?? ''">{{ ago(i.since) }}</td>
                <td><button class="btn" type="button" (click)="open(i.link)">Open</button></td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }
  `,
})
export class DashboardIssuesPage {
  private readonly router = inject(Router);
  protected readonly envs = inject(Environments);
  protected readonly ago = ago;
  protected readonly search = signal('');
  protected readonly area = signal('');
  protected readonly minSeverity = signal<Severity>('LOW');

  protected readonly report = httpResource<AttentionReport>(() => apiUrl('dashboard/attention'));
  protected readonly areas = computed(() => [...new Set((this.report.value()?.items ?? []).map((i) => i.area))]);
  protected readonly rows = computed(() => {
    const order: Severity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];
    const max = order.indexOf(this.minSeverity());
    const q = this.search().trim().toLowerCase();
    return (this.report.value()?.items ?? [])
      .filter((i) => order.indexOf(i.severity) <= max)
      .filter((i) => !this.area() || i.area === this.area())
      .filter((i) => !q || [i.title, i.detail, i.owner ?? '', i.environment ?? ''].some((v) => v.toLowerCase().includes(q)));
  });

  protected open(link: string): void {
    void this.router.navigateByUrl(link);
  }
}
