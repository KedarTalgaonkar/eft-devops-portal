import { Component, computed, signal } from '@angular/core';
import { DatePipe, TitleCasePipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { MatTooltipModule } from '@angular/material/tooltip';
import { apiUrl } from '../../core/api';
import { LoadState } from '../../shared/load-state';
import { StatusChip } from '../../shared/status-chip';
import { AuditView } from './admin.models';

/** Security audit trail: sign-ins, sign-outs, lockouts, access denials and every change through the API. */
@Component({
  selector: 'app-admin-audit',
  imports: [DatePipe, TitleCasePipe, MatTooltipModule, LoadState, StatusChip],
  template: `
    <div class="filters">
      <input class="search" type="search" placeholder="Search user, action, target, IP or detail" aria-label="Search the audit log"
             [value]="search()" (input)="search.set($any($event.target).value)" />
      <div class="chips" role="group" aria-label="Event type">
        @for (f of filtersList; track f.key) {
          <button type="button" class="chip-btn" [attr.aria-pressed]="kind() === f.key" (click)="kind.set(f.key)">{{ f.label }}</button>
        }
      </div>
      <button class="btn" type="button" (click)="events.reload()">Refresh</button>
    </div>
    <app-load-state [loading]="events.isLoading()" [error]="events.error()" [empty]="events.hasValue() && rows().length === 0"
                    what="the audit log" emptyText="No events match." (retry)="events.reload()" />
    @if (rows().length) {
      <div class="table-wrap">
        <table class="data" aria-label="Audit log">
          <thead><tr><th>When</th><th>User</th><th>Action</th><th>Outcome</th><th>Target</th><th>Detail</th><th>IP</th></tr></thead>
          <tbody>
            @for (e of rows(); track e.id) {
              <tr>
                <td>{{ e.occurredAt | date: 'dd MMM yyyy, HH:mm:ss' }}</td>
                <td><strong>{{ e.username ?? '—' }}</strong> <span class="muted">{{ e.authSource ?? '' }}</span></td>
                <td class="mono">{{ e.action }}</td>
                <td><app-status-chip [status]="e.outcome === 'SUCCESS' ? 'OK' : e.outcome === 'DENIED' ? 'WARN' : 'ERROR'" [text]="e.outcome | titlecase" /></td>
                <td class="mono wrap">{{ e.target ?? '—' }}</td>
                <td class="wrap muted">{{ e.detail ?? '' }}</td>
                <td class="mono" [matTooltip]="e.userAgent ?? ''">{{ e.ipAddress ?? '—' }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }
  `,
})
export class AdminAuditPage {
  protected readonly filtersList = [
    { key: '', label: 'All' },
    { key: 'auth', label: 'Sign-in / out' },
    { key: 'security', label: 'Lockouts & denials' },
    { key: 'change', label: 'Changes' },
    { key: 'users', label: 'Account admin' },
  ];
  protected readonly search = signal('');
  protected readonly kind = signal('');
  protected readonly events = httpResource<AuditView[]>(() => apiUrl('admin/audit', { limit: 2000 }), { defaultValue: [] });
  protected readonly rows = computed(() => {
    const q = this.search().trim().toLowerCase();
    const k = this.kind();
    return this.events
      .value()
      .filter((e) =>
        !k ? true
          : k === 'auth' ? ['LOGIN', 'LOGOUT', 'PASSWORD_CHANGED'].includes(e.action)
          : k === 'security' ? ['ACCOUNT_LOCKED', 'ACCESS_DENIED'].includes(e.action) || e.outcome !== 'SUCCESS'
          : k === 'change' ? e.action.startsWith('API_')
          : e.action.startsWith('USER_'),
      )
      .filter((e) => !q || [e.username, e.action, e.target, e.ipAddress, e.detail].some((v) => (v ?? '').toLowerCase().includes(q)));
  });
}
